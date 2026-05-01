#!/usr/bin/env python3
"""Promote /tmp pipeline output into the persistent Ollama-first registry.

Reads a per-document /tmp run dir produced by `ingest_document.py` (one
subdir per section, each containing `run_manifest.json`,
`source_window.json`, `judged_candidates.json`, and the per-stage
artefacts) and applies five pre-acceptance machine gates:

  * G1 — verbatim quote audit (every entry in `exactSourceQuotes` must
        appear in the section's source window after whitespace
        normalisation).
  * G2 — schema-shape validation (required fields, enum membership,
        non-empty rationale and claim).
  * G3 — authority/modality consistency: if the (modality, authorityClass)
        pair violates `_AUTHORITY_CLASS_BY_FLOOR` AND the legitimate
        bundle-gate-override path was NOT taken, this is a real authority
        error rather than a structural-concern misroute. Hard fail.
  * G4 — override audit-trail integrity: if any of the three deterministic
        overrides fired, its audit record must contain its required
        fields (originalVerdict, reason, etc.). Catches regressions in
        the override functions themselves.
  * G6 — parse-failure record: any section whose pipeline raised SystemExit
        (recorded in `failures.json` by the driver) is surfaced into
        `quality/parse_failures.json` as quality data, not just a crash.

Candidates that pass every gate AND were judged `accepted` land in
`registry/ollama_first/candidates/{documentId}/{sectionId}/{canonicalId}.json`.
Everything else (gate failure, judge-demoted, schema-shape miss) is
stashed in `pending/` for human curation. The promoter NEVER auto-rejects.

Idempotency: re-running on the same source dir produces identical
registry state. If a target file already exists with byte-identical
content the write is skipped.

Usage:

    python3 research/tools/requirements-spike/promote_to_registry.py \\
        --source-run-dir /tmp/overnight-shakedown/safetysense22 \\
        [--registry-root research/tools/requirements-spike/registry/ollama_first] \\
        [--quality-root research/tools/requirements-spike/quality]

Or, to bulk-promote a whole overnight tree:

    python3 research/tools/requirements-spike/promote_to_registry.py \\
        --source-run-root /tmp/overnight-shakedown
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import re
import sys
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


sys.path.insert(0, str(Path(__file__).resolve().parent))

from audit_quotes import normalize as normalize_for_quote_audit  # noqa: E402
from candidate_schema import (  # noqa: E402
    FORMAT_VERSION,
    SCHEMA_NAME,
    is_authority_consistent,
    validate_pipeline_candidate,
)
from canonical_id import (  # noqa: E402
    canonical_id_for,
    claim_sha256,
    looks_like_record_filename,
)


REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_REGISTRY_ROOT = Path(__file__).resolve().parent / "registry" / "ollama_first"
DEFAULT_QUALITY_ROOT = Path(__file__).resolve().parent / "quality"


JUDGEMENTS_CSV_HEADERS: tuple[str, ...] = (
    "promoterRunId",
    "promoterRunTimestampUtc",
    "documentId",
    "sectionId",
    "familyId",
    "caseId",
    "canonicalId",
    "originalCandidateId",
    "claimSha256",
    "claimTextExcerpt",
    "authorityClass",
    "modality",
    "requirementKind",
    "promotionHint",
    "challengerVerdict",
    "challengerOverridden",
    "defenderVerdict",
    "bundleGateScopeComplete",
    "siblingResolutionFired",
    "judgeDecision",
    "judgeConfidence",
    "judgeOverridden",
    "finalState",
    "gateOverallStatus",
    "quoteAuditStatus",
    "quoteAuditMisses",
    "schemaAuditStatus",
    "authorityModalityStatus",
    "authorityInvariantViolated",
    "structureModel",
    "extractionModel",
    "challengeModel",
    "defenseModel",
    "bundleGateModel",
    "judgeModel",
    "numCtx",
    "structureAttempts",
    "extractionAttempts",
    "promptVersion",
    "promptVersionSha",
    "structurePromptSha",
    "extractionPromptSha",
    "challengePromptSha",
    "defensePromptSha",
    "bundleGatePromptSha",
    "judgePromptSha",
    "eventSource",
    "curationRunId",
    "curationModel",
    "curationAction",
    "curationConfidence",
    "curationReasoning",
    "curationCorrectionsJson",
    "curationAuditPath",
)


# ── helpers ───────────────────────────────────────────────────────────────


def _utc_now() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


def _new_run_id() -> str:
    stamp = datetime.now(UTC).strftime("%Y-%m-%dT%H-%M-%SZ")
    suffix = hashlib.sha256(stamp.encode("utf-8")).hexdigest()[:4]
    return f"promote_{stamp}_{suffix}"


def _sha256_of_path(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def _parse_source_ref(source_ref: str) -> tuple[str, int, int]:
    """Parse '<path>:<startLine>-<endLine>' into a tuple."""
    match = re.match(r"^(?P<path>.+?):(?P<start>\d+)-(?P<end>\d+)$", source_ref)
    if not match:
        raise ValueError(f"unrecognised sourceRef: {source_ref!r}")
    return match["path"], int(match["start"]), int(match["end"])


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _write_json(path: Path, payload: Any) -> bool:
    """Write JSON only if the content differs. Returns True if written."""
    serialised = json.dumps(payload, indent=2, sort_keys=False) + "\n"
    if path.exists():
        existing = path.read_text(encoding="utf-8")
        if existing == serialised:
            return False
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(serialised, encoding="utf-8")
    return True


class PromotionConflict(Exception):
    """Raised when promotion would silently overwrite or contradict an
    existing registry entry. Caller decides whether to abort the run or
    skip the section. Message must include the canonicalId and both
    bucket paths so a human can resolve."""


def _relative_to_repo(path: Path | str) -> str:
    p = Path(path)
    try:
        return str(p.resolve().relative_to(REPO_ROOT))
    except ValueError:
        return str(p)


def _strip_line_prefix(window_text: str) -> str:
    """The pipeline's source_window.json embeds 'NNN: ' line prefixes for
    LLM consumption. Strip them so verbatim quotes can be matched against
    the raw source content."""
    cleaned: list[str] = []
    for line in window_text.splitlines():
        match = re.match(r"^\d+:\s?(.*)$", line)
        cleaned.append(match.group(1) if match else line)
    return "\n".join(cleaned)


# ── machine gates ─────────────────────────────────────────────────────────


def gate_g1_verbatim_quote(
    candidate: dict[str, Any],
    *,
    source_text: str,
) -> tuple[bool, dict[str, Any]]:
    """G1 — every exactSourceQuote must appear verbatim in the source
    after whitespace normalisation. Reuses `audit_quotes.normalize` so
    the audit primitive is single-source.
    """
    normalised_source = normalize_for_quote_audit(source_text)
    quotes = candidate.get("exactSourceQuotes", [])
    misses: list[dict[str, Any]] = []
    hits: list[str] = []
    if not isinstance(quotes, list) or not quotes:
        return False, {
            "status": "fail",
            "method": "windowed_substring_match",
            "totalQuotes": 0,
            "hits": 0,
            "misses": [{"reason": "no exactSourceQuotes provided"}],
        }
    for quote in quotes:
        if not isinstance(quote, str) or not quote.strip():
            misses.append({"quote": quote, "reason": "empty or non-string"})
            continue
        if normalize_for_quote_audit(quote) in normalised_source:
            hits.append(quote)
        else:
            misses.append({
                "quote": quote,
                "reason": "not found verbatim in source after whitespace normalisation",
            })
    return (
        len(misses) == 0,
        {
            "status": "pass" if not misses else "fail",
            "method": "windowed_substring_match",
            "totalQuotes": len(quotes),
            "hits": len(hits),
            "misses": misses,
        },
    )


def gate_g2_schema(candidate: dict[str, Any]) -> tuple[bool, dict[str, Any]]:
    """G2 — required fields, enum membership, non-empty content."""
    reasons = validate_pipeline_candidate(candidate)
    return (len(reasons) == 0, {"status": "pass" if not reasons else "fail", "reasons": reasons})


def gate_g3_authority_modality(
    candidate: dict[str, Any],
    *,
    challenge_override: dict[str, Any] | None,
) -> tuple[bool, dict[str, Any]]:
    """G3 — `(modality, authorityClass)` must satisfy AUTHORITY_CLASS_BY_FLOOR.

    The bundle-gate override path (when challenge_override is non-null)
    is supposed to fire only on candidates that ARE consistent — see
    `apply_bundle_gate_override` in `run_icao4444_ollama_first_prototype.py`.
    G3 does not delegate that to the override: it re-runs the consistency
    check directly. If the override fired but the candidate is in fact
    inconsistent, that is a regression in the override function itself
    and we surface it as `invariantViolated=true` and FAIL the gate.

    This means a future refactor that loosens the override's precondition
    cannot let inconsistent records into the registry silently — G3 will
    catch them at the boundary.
    """
    modality = candidate.get("modality")
    authority_class = candidate.get("authorityClass")
    consistent = is_authority_consistent(modality, authority_class)
    override_fired = challenge_override is not None
    invariant_violated = override_fired and not consistent
    passed = consistent
    if invariant_violated:
        reason = (
            f"override fired on inconsistent candidate (modality={modality!r}, "
            f"authorityClass={authority_class!r}) — apply_bundle_gate_override "
            "precondition is broken; halt the promotion until the override is fixed"
        )
    elif not consistent:
        reason = (
            f"(modality={modality!r}, authorityClass={authority_class!r}) "
            "violates AUTHORITY_CLASS_BY_FLOOR"
        )
    else:
        reason = None
    return (
        passed,
        {
            "status": "pass" if passed else "fail",
            "modality": modality,
            "authorityClass": authority_class,
            "floorConsistent": consistent,
            "overrideFired": override_fired,
            "invariantViolated": invariant_violated,
            "reason": reason,
        },
    )


_CHALLENGE_OVERRIDE_REQUIRED = (
    "originalVerdict",
    "originalConcerns",
    "candidateModality",
    "candidateAuthorityClass",
    "bundleGateScopeComplete",
    "reason",
)
_JUDGE_OVERRIDE_REQUIRED = (
    "originalDecision",
    "originalConfidence",
    "reason",
)


def gate_g4_override_audit_trail(
    *,
    challenge_override: dict[str, Any] | None,
    judge_override: dict[str, Any] | None,
) -> tuple[bool, dict[str, Any]]:
    """G4 — when an override fires, its audit record must include its
    required fields. Defends against silent regressions in the override
    functions themselves.
    """
    misses: list[str] = []
    if challenge_override is not None:
        for field in _CHALLENGE_OVERRIDE_REQUIRED:
            if field not in challenge_override:
                misses.append(f"challengeOverride missing field: {field}")
    if judge_override is not None:
        for field in _JUDGE_OVERRIDE_REQUIRED:
            if field not in judge_override:
                misses.append(f"judgeOverride missing field: {field}")
    return (len(misses) == 0, {"status": "pass" if not misses else "fail", "misses": misses})


# ── per-section promotion ─────────────────────────────────────────────────


def _build_registry_record(
    *,
    candidate: dict[str, Any],
    judge_for_record: dict[str, Any],
    challenge_for_judge: dict[str, Any],
    bundle_gate_for_judge: dict[str, Any],
    challenge_override: dict[str, Any] | None,
    judge_override: dict[str, Any] | None,
    sibling_resolution_fired: bool,
    g1_audit: dict[str, Any],
    g2_audit: dict[str, Any],
    g3_audit: dict[str, Any],
    g4_audit: dict[str, Any],
    overall_pass: bool,
    canonical_id: str,
    document_id: str,
    section_id: str,
    family_id: str | None,
    provenance: dict[str, Any],
    ingestion_run: dict[str, Any],
    lifecycle_state: str,
    promoted_from: str,
    promoted_at: str,
) -> dict[str, Any]:
    return {
        "schemaName": SCHEMA_NAME,
        "formatVersion": FORMAT_VERSION,
        "canonicalId": canonical_id,
        "documentId": document_id,
        "sectionId": section_id,
        "familyId": family_id,
        "claimText": candidate["claimText"],
        "rationale": candidate.get("rationale", ""),
        "modality": candidate.get("modality"),
        "authorityClass": candidate.get("authorityClass"),
        "requirementKind": candidate.get("requirementKind"),
        "testability": candidate.get("testability"),
        "verificationMode": candidate.get("verificationMode"),
        "promotionHint": candidate.get("promotionHint"),
        "exactSourceQuotes": candidate.get("exactSourceQuotes", []),
        "sourceItemIds": candidate.get("sourceItemIds", []),
        "claimSha256": claim_sha256(candidate["claimText"]),
        "provenance": provenance,
        "ingestionRun": ingestion_run,
        "audit": {
            "verbatimQuoteCheck": g1_audit,
            "schemaCheck": g2_audit,
            "authorityModalityCheck": g3_audit,
            "overrideAuditTrailCheck": g4_audit,
            "rationaleNonEmpty": bool(candidate.get("rationale", "").strip()),
        },
        "gate": {
            "overallStatus": "pass" if overall_pass else "fail",
            "judge": {
                "decision": judge_for_record.get("decision"),
                "confidence": judge_for_record.get("confidence"),
            },
            "challenger": {
                "verdict": challenge_for_judge.get("verdict"),
                "overridden": challenge_override is not None,
            },
            "bundleGate": {
                "scopeComplete": bundle_gate_for_judge.get("scopeComplete"),
            },
            "siblingResolution": {"fired": sibling_resolution_fired},
            "judgeOverride": {"fired": judge_override is not None},
        },
        "lifecycle": {
            "state": lifecycle_state,
            "promotedAt": promoted_at,
            "promotedFrom": promoted_from,
        },
    }


def _existing_record(registry_root: Path, document_id: str, section_id: str, canonical_id: str) -> tuple[str, dict[str, Any]] | None:
    """Search every lifecycle bucket for an existing record with this
    canonicalId. Returns (bucket_name, record) or None."""
    for bucket in ("candidates", "pending", "rejected"):
        path = registry_root / bucket / document_id / section_id / f"{canonical_id}.json"
        if path.exists():
            try:
                return bucket, _read_json(path)
            except json.JSONDecodeError:
                continue
    return None


def _section_source_text(window: dict[str, Any]) -> str:
    """Return the section's source text with line-number prefixes stripped."""
    raw = window.get("windowText") or ""
    return _strip_line_prefix(raw)


def eligible_canonical_ids_for_section(
    *,
    judged: list[dict[str, Any]],
    window: dict[str, Any],
    document_id: str,
    section_id: str,
) -> set[str]:
    """Pure function: given a section's judge output and source window,
    return the set of canonicalIds that would pass G1–G4 AND be judged
    `accepted` (i.e. would land in `candidates/`, not `pending/`).

    This is the single source of truth for "what does the gate stack
    accept?" — both `promote_section` (writes records) and
    `audit_overrides_load_bearing` (compares with-vs-without overrides)
    call this. A future gate (G5, G6, …) added to the promoter must be
    threaded through here too, otherwise the override audit silently
    reports a wrong load-bearing score.
    """
    section_source = _section_source_text(window)
    eligible: set[str] = set()
    for entry in judged:
        candidate = entry.get("candidate", {})
        judge_for_record = entry.get("judgeForRecord") or entry.get("judge") or {}
        if judge_for_record.get("decision") != "accepted":
            continue
        challenge_override = entry.get("challengeOverride")
        judge_override = entry.get("judgeOverride")
        g1_pass, _ = gate_g1_verbatim_quote(candidate, source_text=section_source)
        g2_pass, _ = gate_g2_schema(candidate)
        g3_pass, _ = gate_g3_authority_modality(
            candidate, challenge_override=challenge_override,
        )
        g4_pass, _ = gate_g4_override_audit_trail(
            challenge_override=challenge_override,
            judge_override=judge_override,
        )
        if not all((g1_pass, g2_pass, g3_pass, g4_pass)):
            continue
        try:
            canonical = canonical_id_for(
                document_id=document_id,
                section_id=section_id,
                claim_text=candidate.get("claimText", ""),
                exact_source_quotes=candidate.get("exactSourceQuotes", []),
            )
        except (ValueError, TypeError):
            continue
        eligible.add(canonical)
    return eligible


def _gather_models(run_manifest: dict[str, Any]) -> dict[str, str]:
    return {
        "structure": run_manifest.get("structureModel"),
        "structureReconcile": run_manifest.get("structureReconcileModel"),
        "extraction": run_manifest.get("extractionModel"),
        "reconcile": run_manifest.get("reconcileModel"),
        "challenge": run_manifest.get("challengeModel"),
        "defense": run_manifest.get("defenseModel"),
        "bundleGate": run_manifest.get("bundleGateModel"),
        "judge": run_manifest.get("judgeModel"),
    }


def promote_section(
    *,
    section_dir: Path,
    document_id: str,
    section_id: str,
    family_id: str | None,
    registry_root: Path,
    run_id: str,
) -> dict[str, Any]:
    """Apply gates to every candidate in a single section's /tmp output
    and write the registry records. Returns a section-level summary.
    """
    judged_path = section_dir / "judged_candidates.json"
    run_manifest_path = section_dir / "run_manifest.json"
    source_window_path = section_dir / "source_window.json"
    if not judged_path.exists() or not run_manifest_path.exists():
        return {
            "documentId": document_id,
            "sectionId": section_id,
            "status": "missing_inputs",
            "missing": [
                p.name for p in (judged_path, run_manifest_path) if not p.exists()
            ],
        }

    judged = _read_json(judged_path)
    run_manifest = _read_json(run_manifest_path)
    window = _read_json(source_window_path) if source_window_path.exists() else {}

    source_ref = run_manifest.get("sourceRef") or window.get("sourceRef") or ""
    source_path_raw = run_manifest.get("sourcePath") or window.get("sourcePath") or ""
    try:
        _, start_line, end_line = _parse_source_ref(source_ref)
    except ValueError:
        start_line, end_line = -1, -1

    source_path_obj = Path(source_path_raw) if source_path_raw else None
    source_sha256 = _sha256_of_path(source_path_obj) if source_path_obj and source_path_obj.exists() else None

    section_source_text = _section_source_text(window)

    # Per-candidate sibling-resolution lookup: the section-level audit
    # file lists which candidates were actually flipped. A candidate not
    # in that list saw no rewrite, so its `siblingResolution.fired` must
    # be false even when the section as a whole had a sibling event.
    sibling_resolution_path = section_dir / "bundle_gate_sibling_resolution.json"
    sibling_flipped_ids: set[str] = set()
    sibling_audit_records_present = 0
    if sibling_resolution_path.exists():
        try:
            audit_records = _read_json(sibling_resolution_path) or []
            sibling_audit_records_present = len(audit_records)
            for audit_record in audit_records:
                for flipped in audit_record.get("flippedCandidates") or []:
                    cid = flipped.get("candidateId") if isinstance(flipped, dict) else None
                    if cid:
                        sibling_flipped_ids.add(cid)
        except json.JSONDecodeError:
            pass
    # Defensive: if the audit file is non-empty but no candidateIds were
    # recoverable, the upstream schema has drifted. Fail loudly rather
    # than silently mark every candidate as fired=false — same class of
    # bug as the formatVersion silent-degrade we just fixed.
    if sibling_audit_records_present > 0 and not sibling_flipped_ids:
        raise ValueError(
            f"bundle_gate_sibling_resolution.json at {sibling_resolution_path} "
            f"has {sibling_audit_records_present} audit records but no recoverable "
            "candidateIds — the upstream audit shape has drifted. Update "
            "promote_to_registry to match the new shape before re-running."
        )

    candidates_landed: list[str] = []
    candidates_pending: list[str] = []
    gate_misses: list[dict[str, Any]] = []
    conflicts: list[dict[str, Any]] = []
    seen_canonicals: dict[str, str] = {}
    csv_rows: list[dict[str, Any]] = []

    for entry in judged:
        candidate = entry.get("candidate", {})
        judge_for_record = entry.get("judgeForRecord") or entry.get("judge") or {}
        challenge_for_judge = entry.get("challengeForJudge") or entry.get("challenge") or {}
        bundle_gate_for_judge = entry.get("bundleGate") or {}
        challenge_override = entry.get("challengeOverride")
        judge_override = entry.get("judgeOverride")
        defense_for_record = entry.get("defense") or {}

        candidate_sibling_fired = candidate.get("candidateId") in sibling_flipped_ids
        g1_pass, g1_audit = gate_g1_verbatim_quote(candidate, source_text=section_source_text)
        g2_pass, g2_audit = gate_g2_schema(candidate)
        g3_pass, g3_audit = gate_g3_authority_modality(
            candidate, challenge_override=challenge_override
        )
        g4_pass, g4_audit = gate_g4_override_audit_trail(
            challenge_override=challenge_override,
            judge_override=judge_override,
        )

        gate_passes = [g1_pass, g2_pass, g3_pass, g4_pass]
        overall_pass = all(gate_passes)
        judge_decision = judge_for_record.get("decision")

        # Promotion rule: gates pass AND judge accepted ⇒ candidates/.
        # Anything else ⇒ pending/. Never auto-rejects.
        target_state = (
            "accepted"
            if (overall_pass and judge_decision == "accepted")
            else "pending"
        )
        target_subdir = "candidates" if target_state == "accepted" else "pending"

        # Compute canonical id. If schema is bad enough that we cannot
        # derive an id, stash with a synthetic placeholder pointing at
        # the original candidate id.
        try:
            canonical = canonical_id_for(
                document_id=document_id,
                section_id=section_id,
                claim_text=candidate.get("claimText", ""),
                exact_source_quotes=candidate.get("exactSourceQuotes", []),
            )
        except (ValueError, TypeError) as exc:
            original = candidate.get("candidateId") or "unknown"
            canonical = f"{document_id}::{section_id}::malformed-{hashlib.sha256(original.encode()).hexdigest()[:12]}"
            g2_audit.setdefault("reasons", []).append(
                f"canonicalId derivation failed: {exc}"
            )
            target_state = "pending"
            target_subdir = "pending"
            overall_pass = False

        # Detect duplicate canonicalIds within this section's promotion
        # batch — two judged entries hashing to the same id is a real
        # signal that the LLM emitted two near-identical candidates and
        # only one will survive. Surface as a conflict rather than silently
        # overwriting.
        if canonical in seen_canonicals:
            conflicts.append({
                "canonicalId": canonical,
                "kind": "duplicate_within_run",
                "originalCandidateIds": [
                    seen_canonicals[canonical],
                    candidate.get("candidateId") or "unknown",
                ],
                "message": (
                    "Two judged candidates in this section share a canonicalId — "
                    "their (claimText, exactSourceQuotes) inputs are equivalent. "
                    "Only the first wins; the second is dropped."
                ),
            })
            continue
        seen_canonicals[canonical] = candidate.get("candidateId") or "unknown"

        # Detect cross-bucket conflicts: an existing record in a bucket
        # other than the target. Hard-fail loudly — a state flip needs
        # human curation, never silent overwrite. We also surface any
        # source-drift signal alongside cross-bucket so the curator sees
        # the full picture (both can fire at once: the source mutated
        # AND the verdict flipped).
        existing = _existing_record(registry_root, document_id, section_id, canonical)
        existing_promoted_at: str | None = None
        existing_run_id: str | None = None
        if existing is not None:
            existing_bucket, existing_record_data = existing
            if existing_bucket != target_subdir:
                conflicts.append({
                    "canonicalId": canonical,
                    "kind": "cross_bucket",
                    "existingBucket": existing_bucket,
                    "targetBucket": target_subdir,
                    "existingState": existing_record_data.get("lifecycle", {}).get("state"),
                    "newJudgeDecision": judge_decision,
                    "newGateOverall": "pass" if overall_pass else "fail",
                    "message": (
                        f"canonicalId {canonical} already lives in {existing_bucket}/ but "
                        f"this run wants to write it to {target_subdir}/. Manual curation "
                        "required: either delete the stale entry to accept the new state, "
                        "or fix the source to restore the original verdict."
                    ),
                })
                # Also check for source drift on the existing record so
                # the curator sees both signals at once.
                existing_provenance_xb = existing_record_data.get("provenance") or {}
                existing_source_sha_xb = existing_provenance_xb.get("sourceSha256")
                if (
                    source_sha256 is not None
                    and existing_source_sha_xb is not None
                    and source_sha256 != existing_source_sha_xb
                ):
                    conflicts.append({
                        "canonicalId": canonical,
                        "kind": "source_drift",
                        "existingSourceSha256": existing_source_sha_xb,
                        "newSourceSha256": source_sha256,
                        "message": (
                            "Source file has also changed since the existing record "
                            "in the other bucket was promoted — both the verdict "
                            "AND the underlying text need re-curation."
                        ),
                    })
                continue
            # Same bucket — preserve original promotedAt and runId so a
            # no-op re-promote produces byte-identical output.
            existing_lifecycle = existing_record_data.get("lifecycle") or {}
            existing_ingestion = existing_record_data.get("ingestionRun") or {}
            existing_promoted_at = existing_lifecycle.get("promotedAt")
            existing_run_id = existing_ingestion.get("runId")
            # Source-drift detection: the canonicalId is bound to claim+
            # quotes; if those still hash to the same canonicalId but
            # `provenance.sourceSha256` differs, the underlying source
            # file mutated. The verbatim-quote audit may still pass
            # (because the new source contains the same span), but the
            # line-range provenance is now untrusted. Surface and skip.
            #
            # Three sub-cases:
            #   (a) both SHAs present + differ      → source_drift
            #   (b) existing SHA missing, new known → source_drift_unknown
            #       (legacy record predates SHA capture; cannot detect
            #       drift in either direction; require human re-curate)
            #   (c) new SHA missing                 → cannot check; pass
            #       (the source file is absent on this run; the existing
            #       SHA stays authoritative)
            existing_provenance = existing_record_data.get("provenance") or {}
            existing_source_sha = existing_provenance.get("sourceSha256")
            drift_conflict: dict[str, Any] | None = None
            if source_sha256 is not None and existing_source_sha is not None and source_sha256 != existing_source_sha:
                drift_conflict = {
                    "canonicalId": canonical,
                    "kind": "source_drift",
                    "existingSourceSha256": existing_source_sha,
                    "newSourceSha256": source_sha256,
                    "message": (
                        f"source file '{source_path_raw}' has changed since the "
                        "registry record was promoted (sha256 differs). The line "
                        "range and provenance are no longer trustworthy. Re-curate "
                        "explicitly: either delete the record and re-promote against "
                        "the new source, or restore the source to its previous content."
                    ),
                }
            elif source_sha256 is not None and existing_source_sha is None:
                drift_conflict = {
                    "canonicalId": canonical,
                    "kind": "source_drift_unknown",
                    "existingSourceSha256": None,
                    "newSourceSha256": source_sha256,
                    "message": (
                        "existing registry record carries no provenance.sourceSha256 "
                        "(legacy record from before SHA capture); cannot detect drift. "
                        "Delete the record and re-promote to backfill provenance, or "
                        "manually attest the source by adding the sha256 to the existing "
                        "record."
                    ),
                }
            if drift_conflict is not None:
                conflicts.append(drift_conflict)
                continue

        provenance = {
            "sourcePath": _relative_to_repo(source_path_raw) if source_path_raw else None,
            "sourceSha256": source_sha256,
            "startLine": start_line,
            "endLine": end_line,
        }
        ingestion_run = {
            "runId": existing_run_id or run_id,
            "caseId": run_manifest.get("caseId"),
            "models": _gather_models(run_manifest),
            "promptVersion": run_manifest.get("promptVersion", "unknown"),
            "promptVersionSha": run_manifest.get("promptVersionSha", "unknown"),
            "originalCandidateId": candidate.get("candidateId"),
            "numCtx": run_manifest.get("numCtx"),
            "structureAttempts": run_manifest.get("structureAttempts"),
            "extractionAttempts": run_manifest.get("extractionAttempts"),
        }
        promoted_at = existing_promoted_at or _utc_now()
        record = _build_registry_record(
            candidate=candidate,
            judge_for_record=judge_for_record,
            challenge_for_judge=challenge_for_judge,
            bundle_gate_for_judge=bundle_gate_for_judge,
            challenge_override=challenge_override,
            judge_override=judge_override,
            sibling_resolution_fired=candidate_sibling_fired,
            g1_audit=g1_audit,
            g2_audit=g2_audit,
            g3_audit=g3_audit,
            g4_audit=g4_audit,
            overall_pass=overall_pass,
            canonical_id=canonical,
            document_id=document_id,
            section_id=section_id,
            family_id=family_id,
            provenance=provenance,
            ingestion_run=ingestion_run,
            lifecycle_state=target_state,
            promoted_from=str(judged_path),
            promoted_at=promoted_at,
        )

        target_path = (
            registry_root / target_subdir / document_id / section_id / f"{canonical}.json"
        )
        # Idempotency: if the candidate file is already present in the
        # other lifecycle bucket and content matches, that's fine; a
        # re-run that flips state is a real signal — surface it but do
        # not silently overwrite. The promoter writes to the target
        # bucket only.
        _write_json(target_path, record)

        # Collect outcome-CSV row. promoterRunId / runTimestampUtc are
        # filled in by the per-document caller so a single promotion
        # run shows up under one stable id across all sections.
        claim_text = candidate.get("claimText") or ""
        csv_rows.append({
            "documentId": document_id,
            "sectionId": section_id,
            "familyId": family_id or "",
            "caseId": run_manifest.get("caseId") or "",
            "canonicalId": canonical,
            "originalCandidateId": candidate.get("candidateId") or "",
            "claimSha256": record.get("claimSha256") or "",
            "claimTextExcerpt": claim_text[:240].replace("\n", " "),
            "authorityClass": candidate.get("authorityClass") or "",
            "modality": candidate.get("modality") or "",
            "requirementKind": candidate.get("requirementKind") or "",
            "promotionHint": candidate.get("promotionHint") or "",
            "challengerVerdict": challenge_for_judge.get("verdict") or "",
            "challengerOverridden": "true" if challenge_override is not None else "false",
            "defenderVerdict": defense_for_record.get("verdict") or "",
            "bundleGateScopeComplete": (
                "true" if bundle_gate_for_judge.get("scopeComplete") else "false"
            ),
            "siblingResolutionFired": "true" if candidate_sibling_fired else "false",
            "judgeDecision": judge_decision or "",
            "judgeConfidence": judge_for_record.get("confidence") or "",
            "judgeOverridden": "true" if judge_override is not None else "false",
            "finalState": target_state,
            "gateOverallStatus": "pass" if overall_pass else "fail",
            "quoteAuditStatus": g1_audit.get("status") or "",
            "quoteAuditMisses": str(len(g1_audit.get("misses") or [])),
            "schemaAuditStatus": g2_audit.get("status") or "",
            "authorityModalityStatus": g3_audit.get("status") or "",
            "authorityInvariantViolated": (
                "true" if g3_audit.get("invariantViolated") else "false"
            ),
            "structureModel": run_manifest.get("structureModel") or "",
            "extractionModel": run_manifest.get("extractionModel") or "",
            "challengeModel": run_manifest.get("challengeModel") or "",
            "defenseModel": run_manifest.get("defenseModel") or "",
            "bundleGateModel": run_manifest.get("bundleGateModel") or "",
            "judgeModel": run_manifest.get("judgeModel") or "",
            "numCtx": str(run_manifest.get("numCtx") or ""),
            "structureAttempts": str(run_manifest.get("structureAttempts") or ""),
            "extractionAttempts": str(run_manifest.get("extractionAttempts") or ""),
            "promptVersion": run_manifest.get("promptVersion") or "unknown",
            "promptVersionSha": run_manifest.get("promptVersionSha") or "unknown",
            **{
                f"{stage}PromptSha": (
                    (run_manifest.get("promptVersionShasByStage") or {}).get(stage)
                    or "unknown"
                )
                for stage in (
                    "structure", "extraction", "challenge",
                    "defense", "bundleGate", "judge",
                )
            },
        })

        if target_state == "accepted":
            candidates_landed.append(canonical)
        else:
            candidates_pending.append(canonical)
            gate_misses.append({
                "canonicalId": canonical,
                "originalCandidateId": candidate.get("candidateId"),
                "g1": g1_audit["status"],
                "g2": g2_audit["status"],
                "g3": g3_audit["status"],
                "g4": g4_audit["status"],
                "judgeDecision": judge_decision,
            })

    # Section sidecar — provenance for everything in this section.
    # `lastPromotedAt` and `lastPromotedRunId` are inherently drift-y
    # (every promotion touches them), so the sidecar's git diff is
    # noise-by-design; the candidate records themselves stay byte-stable
    # on no-op re-promotion.
    section_meta_path = (
        registry_root / "candidates" / document_id / section_id / "_section.json"
    )
    if candidates_landed or candidates_pending:
        _write_json(
            section_meta_path,
            {
                "documentId": document_id,
                "sectionId": section_id,
                "familyId": family_id,
                "sourcePath": _relative_to_repo(source_path_raw) if source_path_raw else None,
                "sourceSha256": source_sha256,
                "startLine": start_line,
                "endLine": end_line,
                "lastPromotedRunId": run_id,
                "lastPromotedAt": _utc_now(),
            },
        )

    return {
        "documentId": document_id,
        "sectionId": section_id,
        "status": "promoted",
        "totalJudged": len(judged),
        "landed": candidates_landed,
        "pending": candidates_pending,
        "gateMisses": gate_misses,
        "conflicts": conflicts,
        "csvRows": csv_rows,
    }


# ── per-document promotion ────────────────────────────────────────────────


def _document_id_from_run_dir(source_run_dir: Path) -> str:
    """The /tmp dir layout is {source_run_root}/{documentId}/{sectionId}/.
    The accepted_candidates aggregate (when present) carries documentId
    explicitly; fall back to the dir name.
    """
    aggregate = source_run_dir / "accepted_candidates.json"
    if aggregate.exists():
        try:
            data = _read_json(aggregate)
            if isinstance(data, dict) and data.get("documentId"):
                return data["documentId"]
        except json.JSONDecodeError:
            pass
    return source_run_dir.name


def _family_id_for_section(section_dir: Path) -> str | None:
    window = section_dir / "source_window.json"
    if window.exists():
        try:
            return _read_json(window).get("familyId")
        except json.JSONDecodeError:
            pass
    return None


def promote_document(
    *,
    source_run_dir: Path,
    registry_root: Path,
    quality_root: Path,
    run_id: str,
) -> dict[str, Any]:
    document_id = _document_id_from_run_dir(source_run_dir)
    section_results: list[dict[str, Any]] = []
    parse_failures: list[dict[str, Any]] = []

    failures_path = source_run_dir / "failures.json"
    if failures_path.exists():
        try:
            for entry in _read_json(failures_path):
                parse_failures.append({
                    "documentId": document_id,
                    "sectionId": entry.get("sectionId"),
                    "errorClass": entry.get("errorClass"),
                    "error": entry.get("error"),
                    "recordedAt": _utc_now(),
                })
        except (json.JSONDecodeError, TypeError):
            pass

    for section_dir in sorted(p for p in source_run_dir.iterdir() if p.is_dir()):
        if section_dir.name.startswith("."):
            continue
        # Skip non-section directories (e.g. cached scratch dirs).
        if not (section_dir / "judged_candidates.json").exists():
            continue
        section_id = section_dir.name
        family_id = _family_id_for_section(section_dir)
        section_results.append(
            promote_section(
                section_dir=section_dir,
                document_id=document_id,
                section_id=section_id,
                family_id=family_id,
                registry_root=registry_root,
                run_id=run_id,
            )
        )

    if parse_failures:
        existing: list[dict[str, Any]] = []
        path = quality_root / "parse_failures.json"
        if path.exists():
            try:
                existing = _read_json(path) or []
            except json.JSONDecodeError:
                existing = []
        existing.extend(parse_failures)
        _write_json(path, existing)

    csv_rows = [row for sec in section_results for row in sec.get("csvRows") or []]
    rows_appended = append_csv_rows(
        quality_root / "judgements.csv",
        csv_rows,
        promoter_run_id=run_id,
        promoter_run_timestamp_utc=_utc_now(),
    )

    return {
        "documentId": document_id,
        "runId": run_id,
        "sectionResults": section_results,
        "parseFailuresRecorded": len(parse_failures),
        "csvRowsAppended": rows_appended,
    }


# ── registry-manifest update ──────────────────────────────────────────────


def append_csv_rows(
    csv_path: Path,
    rows: list[dict[str, Any]],
    *,
    promoter_run_id: str,
    promoter_run_timestamp_utc: str,
) -> int:
    """Append outcome rows to the cumulative judgements CSV.

    The CSV is the longitudinal record. Re-runs of the promoter against
    the same /tmp source append fresh rows; the regression detector
    `check_ollama_first_regressions.py` deduplicates by `(documentId,
    sectionId, claimSha256)` taking the most recent row, so growth is
    audit-trail by design.

    Concurrency: a single CSV row with long `claimTextExcerpt` exceeds
    `PIPE_BUF` (4096 bytes), so kernel-level append atomicity does not
    apply. Wrap the append in `fcntl.flock` so two simultaneous
    promoters serialise rather than interleaving partial rows. flock is
    advisory and only honoured by writers that also take it — within
    this codebase, that's the only writer to the CSV.
    """
    if not rows:
        return 0
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    # `import fcntl` is POSIX-only. The whole tooling is Linux-only
    # already (Ollama on biggy:11434), so this is acceptable; if the
    # tooling ever runs on Windows, fall through to unsynchronised
    # append AND warn loudly so the unsafe regime is visible.
    try:
        import fcntl  # noqa: PLC0415
    except ImportError:
        fcntl = None  # type: ignore[assignment]
        print(
            "[warn] fcntl unavailable on this platform — judgements.csv "
            "appends are unsynchronised. Concurrent promoter runs may "
            "interleave rows.",
            file=sys.stderr,
        )
    with csv_path.open("a+", newline="", encoding="utf-8") as f:
        if fcntl is not None:
            fcntl.flock(f.fileno(), fcntl.LOCK_EX)
        try:
            f.seek(0)
            existing_content = f.read()
            if existing_content:
                reader = csv.DictReader(io.StringIO(existing_content))
                existing_headers = reader.fieldnames or []
                unexpected = [
                    header for header in existing_headers
                    if header not in JUDGEMENTS_CSV_HEADERS
                ]
                if unexpected:
                    raise ValueError(
                        "judgements.csv contains unknown columns: "
                        + ", ".join(sorted(unexpected))
                    )
                if existing_headers != list(JUDGEMENTS_CSV_HEADERS):
                    existing_rows = list(reader)
                    f.seek(0)
                    f.truncate()
                    migrated_writer = csv.DictWriter(
                        f,
                        fieldnames=JUDGEMENTS_CSV_HEADERS,
                    )
                    migrated_writer.writeheader()
                    for existing_row in existing_rows:
                        migrated_writer.writerow({
                            **{key: "" for key in JUDGEMENTS_CSV_HEADERS},
                            **{
                                key: existing_row.get(key, "")
                                for key in existing_headers
                            },
                        })
            else:
                f.seek(0)
                writer = csv.DictWriter(f, fieldnames=JUDGEMENTS_CSV_HEADERS)
                writer.writeheader()

            writer = csv.DictWriter(f, fieldnames=JUDGEMENTS_CSV_HEADERS)
            for row in rows:
                full_row = {
                    **{key: "" for key in JUDGEMENTS_CSV_HEADERS},
                    **row,
                    "promoterRunId": promoter_run_id,
                    "promoterRunTimestampUtc": promoter_run_timestamp_utc,
                    "eventSource": row.get("eventSource") or "promoter",
                }
                writer.writerow(full_row)
        finally:
            if fcntl is not None:
                fcntl.flock(f.fileno(), fcntl.LOCK_UN)
    return len(rows)


def _csv_bool(value: Any) -> str:
    if value is None:
        return ""
    return "true" if bool(value) else "false"


def csv_row_from_registry_record(
    record: dict[str, Any],
    *,
    event_source: str,
    curation_run_id: str = "",
    curation_model: str = "",
    curation_action: str = "",
    curation_confidence: str = "",
    curation_reasoning: str = "",
    curation_corrections: dict[str, Any] | None = None,
    curation_audit_path: str = "",
) -> dict[str, Any]:
    """Build a `judgements.csv` row from a persisted registry record.

    Promoter rows are assembled while all per-stage artefacts are still
    in memory. Curator rows are emitted later, from the registry record
    after it has moved buckets or been annotated in place. This helper
    keeps that second path on the same CSV schema rather than letting
    the curation script maintain a parallel row shape.
    """
    ingestion = record.get("ingestionRun") or {}
    models = ingestion.get("models") or {}
    gate = record.get("gate") or {}
    audit = record.get("audit") or {}
    quote_audit = audit.get("verbatimQuoteCheck") or {}
    schema_audit = audit.get("schemaCheck") or {}
    authority_audit = audit.get("authorityModalityCheck") or {}
    lifecycle = record.get("lifecycle") or {}
    prompt_shas = ingestion.get("promptVersionShasByStage") or {}
    claim_text = record.get("claimText") or ""
    return {
        "documentId": record.get("documentId") or "",
        "sectionId": record.get("sectionId") or "",
        "familyId": record.get("familyId") or "",
        "caseId": ingestion.get("caseId") or "",
        "canonicalId": record.get("canonicalId") or "",
        "originalCandidateId": ingestion.get("originalCandidateId") or "",
        "claimSha256": record.get("claimSha256") or claim_sha256(claim_text),
        "claimTextExcerpt": claim_text[:240].replace("\n", " "),
        "authorityClass": record.get("authorityClass") or "",
        "modality": record.get("modality") or "",
        "requirementKind": record.get("requirementKind") or "",
        "promotionHint": record.get("promotionHint") or "",
        "challengerVerdict": (gate.get("challenger") or {}).get("verdict") or "",
        "challengerOverridden": _csv_bool((gate.get("challenger") or {}).get("overridden")),
        "defenderVerdict": (gate.get("defender") or {}).get("verdict") or "",
        "bundleGateScopeComplete": _csv_bool((gate.get("bundleGate") or {}).get("scopeComplete")),
        "siblingResolutionFired": _csv_bool((gate.get("siblingResolution") or {}).get("fired")),
        "judgeDecision": (gate.get("judge") or {}).get("decision") or "",
        "judgeConfidence": (gate.get("judge") or {}).get("confidence") or "",
        "judgeOverridden": _csv_bool((gate.get("judgeOverride") or {}).get("fired")),
        "finalState": lifecycle.get("state") or "",
        "gateOverallStatus": gate.get("overallStatus") or "",
        "quoteAuditStatus": quote_audit.get("status") or "",
        "quoteAuditMisses": str(len(quote_audit.get("misses") or [])),
        "schemaAuditStatus": schema_audit.get("status") or "",
        "authorityModalityStatus": authority_audit.get("status") or "",
        "authorityInvariantViolated": _csv_bool(authority_audit.get("invariantViolated")),
        "structureModel": models.get("structure") or "",
        "extractionModel": models.get("extraction") or "",
        "challengeModel": models.get("challenge") or "",
        "defenseModel": models.get("defense") or "",
        "bundleGateModel": models.get("bundleGate") or "",
        "judgeModel": models.get("judge") or "",
        "numCtx": str(ingestion.get("numCtx") or ""),
        "structureAttempts": str(ingestion.get("structureAttempts") or ""),
        "extractionAttempts": str(ingestion.get("extractionAttempts") or ""),
        "promptVersion": ingestion.get("promptVersion") or "unknown",
        "promptVersionSha": ingestion.get("promptVersionSha") or "unknown",
        "structurePromptSha": prompt_shas.get("structure") or "unknown",
        "extractionPromptSha": prompt_shas.get("extraction") or "unknown",
        "challengePromptSha": prompt_shas.get("challenge") or "unknown",
        "defensePromptSha": prompt_shas.get("defense") or "unknown",
        "bundleGatePromptSha": prompt_shas.get("bundleGate") or "unknown",
        "judgePromptSha": prompt_shas.get("judge") or "unknown",
        "eventSource": event_source,
        "curationRunId": curation_run_id,
        "curationModel": curation_model,
        "curationAction": curation_action,
        "curationConfidence": curation_confidence,
        "curationReasoning": curation_reasoning,
        "curationCorrectionsJson": (
            json.dumps(curation_corrections, sort_keys=True)
            if curation_corrections
            else ""
        ),
        "curationAuditPath": curation_audit_path,
    }


def _count_files(path: Path) -> int:
    if not path.exists():
        return 0
    return sum(1 for p in path.rglob("*.json") if looks_like_record_filename(p.name))


def update_manifest(registry_root: Path) -> dict[str, Any]:
    manifest_path = registry_root / "manifest.json"
    manifest = _read_json(manifest_path) if manifest_path.exists() else {
        "schemaName": "ollama_first_registry_manifest",
        "formatVersion": FORMAT_VERSION,
        "createdAt": _utc_now(),
    }

    by_document: dict[str, int] = {}
    by_authority: dict[str, int] = {}
    by_modality: dict[str, int] = {}

    candidates_dir = registry_root / "candidates"
    candidates_count = 0
    if candidates_dir.exists():
        for record_path in candidates_dir.rglob("*.json"):
            if not looks_like_record_filename(record_path.name):
                continue
            candidates_count += 1
            try:
                record = _read_json(record_path)
            except json.JSONDecodeError:
                continue
            doc = record.get("documentId", "unknown")
            mod = record.get("modality", "unknown") or "unknown"
            ac = record.get("authorityClass", "unknown") or "unknown"
            by_document[doc] = by_document.get(doc, 0) + 1
            by_modality[mod] = by_modality.get(mod, 0) + 1
            by_authority[ac] = by_authority.get(ac, 0) + 1

    pending_count = _count_files(registry_root / "pending")
    rejected_count = _count_files(registry_root / "rejected")

    manifest["updatedAt"] = _utc_now()
    manifest["counts"] = {
        "candidates": candidates_count,
        "pending": pending_count,
        "rejected": rejected_count,
        "byDocument": dict(sorted(by_document.items())),
        "byAuthorityClass": dict(sorted(by_authority.items())),
        "byModality": dict(sorted(by_modality.items())),
    }
    manifest["documents"] = sorted(by_document.keys())
    _write_json(manifest_path, manifest)
    return manifest


# ── CLI ───────────────────────────────────────────────────────────────────


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    src_group = parser.add_mutually_exclusive_group(required=True)
    src_group.add_argument(
        "--source-run-dir",
        type=Path,
        help="Per-document /tmp run dir (one subdir per section).",
    )
    src_group.add_argument(
        "--source-run-root",
        type=Path,
        help="Parent /tmp dir containing one per-document subdir.",
    )
    parser.add_argument(
        "--registry-root",
        type=Path,
        default=DEFAULT_REGISTRY_ROOT,
        help="Where the persistent registry lives.",
    )
    parser.add_argument(
        "--quality-root",
        type=Path,
        default=DEFAULT_QUALITY_ROOT,
        help="Where parse-failure records live.",
    )
    args = parser.parse_args(argv)

    run_id = _new_run_id()
    document_results: list[dict[str, Any]] = []

    if args.source_run_dir:
        document_results.append(promote_document(
            source_run_dir=args.source_run_dir,
            registry_root=args.registry_root,
            quality_root=args.quality_root,
            run_id=run_id,
        ))
    else:
        for child in sorted(p for p in args.source_run_root.iterdir() if p.is_dir()):
            if not any(
                (sec / "judged_candidates.json").exists()
                for sec in child.iterdir() if sec.is_dir()
            ):
                continue
            document_results.append(promote_document(
                source_run_dir=child,
                registry_root=args.registry_root,
                quality_root=args.quality_root,
                run_id=run_id,
            ))

    # Strip csvRows from the runs/ log — they're already in
    # quality/judgements.csv and would bloat the per-run JSON.
    log_documents: list[dict[str, Any]] = []
    for doc in document_results:
        log_documents.append({
            **{k: v for k, v in doc.items() if k != "sectionResults"},
            "sectionResults": [
                {k: v for k, v in sec.items() if k != "csvRows"}
                for sec in doc.get("sectionResults", [])
            ],
        })
    runs_path = args.registry_root / "runs" / f"{run_id}.json"
    _write_json(
        runs_path,
        {
            "runId": run_id,
            "runStartedAt": _utc_now(),
            "documents": log_documents,
        },
    )

    manifest = update_manifest(args.registry_root)

    summary = {
        "runId": run_id,
        "documents": [
            {
                "documentId": d["documentId"],
                "sections": len(d["sectionResults"]),
                "landed": sum(len(s.get("landed", [])) for s in d["sectionResults"]),
                "pending": sum(len(s.get("pending", [])) for s in d["sectionResults"]),
                "conflicts": sum(len(s.get("conflicts", [])) for s in d["sectionResults"]),
                "parseFailures": d["parseFailuresRecorded"],
            }
            for d in document_results
        ],
        "manifestCounts": manifest["counts"],
    }
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
