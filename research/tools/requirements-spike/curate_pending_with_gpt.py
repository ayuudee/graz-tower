#!/usr/bin/env python3
"""LLM curation pass over `registry/ollama_first/pending/`.

Phase G — second-pass curator.

The local pipeline's qwen3.6 judge demotes candidates conservatively
because it sees only the candidate plus its own source window. Many of
those demotions are resolvable when you also see (a) the sibling
candidates already accepted in `candidates/`, (b) the override audit
trail, (c) the judge's own rationale, and (d) the verbatim source.
This script gives that whole bundle to a frontier LLM (OpenAI gpt-5.x
or Anthropic claude-opus / claude-sonnet) and asks for a structured
action via a constrained schema (OpenAI `response_format`, Anthropic
tool use).

Allowed actions:

  * `promote` — move the pending record to `candidates/` unchanged.
    The judge was over-cautious; the gate audits already pass; nothing
    needs editing.
  * `promote_with_corrections` — change ONLY non-identity fields
    (`modality`, `authorityClass`, `requirementKind`), then move to
    `candidates/`. Identity fields (`claimText`, `exactSourceQuotes`)
    are NEVER corrected in this pass — those would change the
    canonicalId and demand a fresh ingestion. The corrected record is
    re-validated against G2 + G3 in-process; if it still fails, action
    is silently demoted to `keep_pending`.
  * `reject` — move to `rejected/`. The candidate is wrong (claim not
    in source, or duplicates another record's content).
  * `keep_pending` — the curator could not decide; record stays in
    pending/ with a curation annotation.

Per record, an audit artefact is written to
`quality/curation/{runId}/{canonicalId}.json` containing the GPT-5.5
prompt, response, and applied action.

Usage:

    set -a; source /path/to/.env; set +a
    python3 research/tools/requirements-spike/curate_pending_with_gpt.py \\
        --source-run-root /tmp/ollama-first-phase-e-2026-04-28 \\
        [--limit 3]   # smoke test first; omit for full pass
        [--model gpt-5.5]
        [--dry-run]   # call GPT, report actions, do not modify registry

Exits 0 if the script completed (regardless of curation outcomes).
Exit 1 on hard errors (API key missing, manifest malformed, etc.).
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
import time
import urllib.error
import urllib.request
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


sys.path.insert(0, str(Path(__file__).resolve().parent))

from candidate_schema import (  # noqa: E402
    AUTHORITY_CLASS_VALUES,
    MODALITY_VALUES,
    is_authority_consistent,
    validate_pipeline_candidate,
)
from canonical_id import looks_like_record_filename  # noqa: E402
from promote_to_registry import (  # noqa: E402
    append_csv_rows,
    csv_row_from_registry_record,
)


REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_REGISTRY_ROOT = Path(__file__).resolve().parent / "registry" / "ollama_first"
DEFAULT_QUALITY_ROOT = Path(__file__).resolve().parent / "quality"
DEFAULT_OPENAI_MODEL = "gpt-5.5"
DEFAULT_ANTHROPIC_MODEL = "claude-opus-4-7"


# ── context bundle ────────────────────────────────────────────────────────


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _utc_now() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


def _new_run_id() -> str:
    stamp = datetime.now(UTC).strftime("%Y-%m-%dT%H-%M-%SZ")
    return f"curate_{stamp}"


def _section_source_text(section_dir: Path) -> str:
    window_path = section_dir / "source_window.json"
    if not window_path.exists():
        return ""
    window = _read_json(window_path)
    raw = window.get("windowText") or ""
    cleaned: list[str] = []
    import re
    for line in raw.splitlines():
        match = re.match(r"^\d+:\s?(.*)$", line)
        cleaned.append(match.group(1) if match else line)
    return "\n".join(cleaned)


def _judge_rationale(section_dir: Path, original_candidate_id: str) -> str | None:
    judge_path = section_dir / "judge" / f"{original_candidate_id}.json"
    if not judge_path.exists():
        return None
    try:
        judge = _read_json(judge_path)
        return judge.get("parsed", {}).get("rationale")
    except (json.JSONDecodeError, OSError):
        return None


def _challenge_concerns(section_dir: Path, original_candidate_id: str) -> list[str]:
    challenge_path = section_dir / "challenge" / f"{original_candidate_id}.json"
    if not challenge_path.exists():
        return []
    try:
        ch = _read_json(challenge_path)
        return ch.get("parsed", {}).get("concerns", [])
    except (json.JSONDecodeError, OSError):
        return []


def _bundle_gate_rationale(section_dir: Path, original_candidate_id: str) -> str | None:
    bg_path = section_dir / "bundle_gate" / f"{original_candidate_id}.json"
    if not bg_path.exists():
        return None
    try:
        bg = _read_json(bg_path)
        return bg.get("parsed", {}).get("rationale")
    except (json.JSONDecodeError, OSError):
        return None


def _sibling_accepted(registry_root: Path, document_id: str, section_id: str) -> list[dict[str, Any]]:
    """Return a compact summary of already-accepted candidates in this
    section so the curator can judge umbrella-vs-atomic relationships."""
    sec_dir = registry_root / "candidates" / document_id / section_id
    if not sec_dir.exists():
        return []
    out: list[dict[str, Any]] = []
    for path in sorted(sec_dir.iterdir()):
        if not looks_like_record_filename(path.name):
            continue
        rec = _read_json(path)
        out.append({
            "canonicalId": rec.get("canonicalId"),
            "claimText": rec.get("claimText"),
            "modality": rec.get("modality"),
            "authorityClass": rec.get("authorityClass"),
            "exactSourceQuoteCount": len(rec.get("exactSourceQuotes") or []),
        })
    return out


def build_context_bundle(
    *,
    pending_record: dict[str, Any],
    registry_root: Path,
    source_run_root: Path,
) -> dict[str, Any]:
    document_id = pending_record["documentId"]
    section_id = pending_record["sectionId"]
    original_candidate_id = pending_record["ingestionRun"].get("originalCandidateId") or ""
    section_dir = source_run_root / document_id / section_id
    return {
        "candidate": {
            "canonicalId": pending_record.get("canonicalId"),
            "claimText": pending_record.get("claimText"),
            "rationale": pending_record.get("rationale"),
            "modality": pending_record.get("modality"),
            "authorityClass": pending_record.get("authorityClass"),
            "requirementKind": pending_record.get("requirementKind"),
            "exactSourceQuotes": pending_record.get("exactSourceQuotes", []),
        },
        "judgeForRecord": {
            "decision": pending_record.get("gate", {}).get("judge", {}).get("decision"),
            "confidence": pending_record.get("gate", {}).get("judge", {}).get("confidence"),
            "rationale": _judge_rationale(section_dir, original_candidate_id),
        },
        "challengerVerdict": pending_record.get("gate", {}).get("challenger", {}).get("verdict"),
        "challengerOverridden": pending_record.get("gate", {}).get("challenger", {}).get("overridden"),
        "challengerConcerns": _challenge_concerns(section_dir, original_candidate_id),
        "bundleGate": {
            "scopeComplete": pending_record.get("gate", {}).get("bundleGate", {}).get("scopeComplete"),
            "rationale": _bundle_gate_rationale(section_dir, original_candidate_id),
        },
        "g1QuoteAudit": pending_record.get("audit", {}).get("verbatimQuoteCheck", {}),
        "g2SchemaAudit": pending_record.get("audit", {}).get("schemaCheck", {}),
        "g3AuthorityModalityAudit": pending_record.get("audit", {}).get("authorityModalityCheck", {}),
        "g4OverrideAudit": pending_record.get("audit", {}).get("overrideAuditTrailCheck", {}),
        "siblingAccepted": _sibling_accepted(registry_root, document_id, section_id),
        "sourceWindowText": _section_source_text(section_dir),
    }


# ── prompt + LLM call ─────────────────────────────────────────────────────


_SYSTEM_PROMPT = """You are an aviation regulatory-text curator. Your job is to decide what to do with one candidate "source unit" that the local pipeline's judge demoted to `pending`.

You will receive a structured context bundle: the candidate's claimText + modality + authorityClass + verbatim source quotes; the gate audit results (G1 verbatim quote, G2 schema, G3 authority/modality, G4 override audit-trail); the judge's, challenger's, and bundle-gate's rationales; the source window text the candidate was extracted from; and the sibling candidates already accepted in `candidates/` for this section.

Return ONLY a JSON object matching this schema:

{
  "action": "promote" | "promote_with_corrections" | "reject" | "keep_pending",
  "reasoning": "1–3 sentences explaining the decision",
  "corrections": {
    "modality": "<one of shall|must|should|may|note|example|none|mixed>",
    "authorityClass": "<one of authoritative_requirement|operational_guidance|best_practice|background_support>",
    "requirementKind": "<optional new value>"
  },
  "confidence": "high" | "medium" | "low"
}

Rules — read these carefully:

1. `claimText` and `exactSourceQuotes` are IDENTITY fields and MUST NOT be edited in this curation pass. If the claim text is wrong or the quotes don't exist verbatim in the source, the action is `reject`, not `promote_with_corrections`.

2. The modality vocabulary is exactly {shall, must, should, may, note, example, none, mixed}. `must` is a synonym for `shall`. Pick the modality that matches the source's wording for THIS candidate's quotes.

3. The authority floor table is:
   - `authoritative_requirement` → only `shall`, `must`, `mixed`
   - `operational_guidance` / `best_practice` / `background_support` → any modality
   If the candidate's modality is `may`/`should`/`note` etc. and the authorityClass is `authoritative_requirement`, the correct fix is to LOWER the authorityClass to `operational_guidance` (one rung down). Do not change the modality just to pass G3.

4. `promote` is correct when: G1–G4 all passed AND the judge accepted; the candidate just got stuck because of a sibling-symmetry quirk or other prompt artefact. (You will rarely see this — most pending records have at least one issue.)

5. `promote_with_corrections` is correct when: only `modality`, `authorityClass`, or `requirementKind` need changing (e.g. the may×authoritative_requirement case in rule 3) AND no identity field is wrong. Provide ONLY the fields you want to change in `corrections`.

6. `reject` is correct when: the candidate's claim is not supported by the source (G1 fail with a real verbatim miss), OR the candidate is a redundant umbrella whose atomic items already appear in `siblingAccepted`. Always favour atomic items over umbrellas — if `siblingAccepted` already covers the umbrella's content, the umbrella is `reject`.

7. `keep_pending` is correct when: you genuinely cannot decide, OR the issue requires re-extraction (changing claim text or quotes). Be specific about what a human curator would need.

8. Be conservative on best_practice / operational_guidance documents (CAP413, EGAST, SafetySense, Slovenia VFR, ICAO 9432, H01): prefer `advisory_only` modality framing rather than promoting to authoritative.

9. NEVER invent corrections. If you propose `modality: "shall"`, the source must support `shall`-strength obligation for THIS specific quote.

If unsure, prefer `keep_pending` over a wrong promotion or rejection."""


_CURATOR_TOOL_SCHEMA = {
    "type": "object",
    "properties": {
        "action": {
            "type": "string",
            "enum": ["promote", "promote_with_corrections", "reject", "keep_pending"],
        },
        "reasoning": {"type": "string", "minLength": 1},
        "corrections": {
            "type": "object",
            "properties": {
                "modality": {
                    "type": "string",
                    "enum": ["shall", "must", "should", "may", "note", "example", "none", "mixed"],
                },
                "authorityClass": {
                    "type": "string",
                    "enum": [
                        "authoritative_requirement", "operational_guidance",
                        "best_practice", "background_support",
                    ],
                },
                "requirementKind": {"type": "string"},
            },
            "additionalProperties": False,
        },
        "confidence": {"type": "string", "enum": ["high", "medium", "low"]},
    },
    "required": ["action", "reasoning", "confidence"],
    "additionalProperties": False,
}


def call_gpt(
    *,
    api_key: str,
    model: str,
    bundle: dict[str, Any],
    timeout_seconds: int = 60,
) -> dict[str, Any]:
    """POST to OpenAI chat completions with strict JSON response. Returns
    the parsed JSON plus the raw response for audit."""
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": _SYSTEM_PROMPT},
            {"role": "user", "content": json.dumps(bundle, indent=2, ensure_ascii=False)},
        ],
        "response_format": {"type": "json_object"},
    }
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        "https://api.openai.com/v1/chat/completions",
        data=body,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
    )
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout_seconds) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        err_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenAI HTTP {exc.code}: {err_body[:500]}") from exc
    elapsed = time.time() - started
    response = json.loads(raw)
    if "error" in response:
        raise RuntimeError(f"OpenAI returned error: {response['error']}")
    content = response["choices"][0]["message"]["content"]
    parsed = json.loads(content)
    return {
        "parsed": parsed,
        "raw": response,
        "elapsedSeconds": round(elapsed, 2),
        "model": model,
    }


def call_anthropic(
    *,
    api_key: str,
    model: str,
    bundle: dict[str, Any],
    timeout_seconds: int = 90,
) -> dict[str, Any]:
    """POST to Anthropic Messages API with tool-use for guaranteed
    structure. Claude is forced to call the `record_curation_decision`
    tool, whose schema mirrors the JSON we want; we read the tool's
    `input` block as the parsed response.
    """
    payload = {
        "model": model,
        "max_tokens": 1024,
        "system": _SYSTEM_PROMPT,
        "messages": [
            {
                "role": "user",
                "content": json.dumps(bundle, indent=2, ensure_ascii=False),
            },
        ],
        "tools": [
            {
                "name": "record_curation_decision",
                "description": (
                    "Record the curator's decision on one pending source-unit "
                    "candidate. Call exactly once with the action + reasoning."
                ),
                "input_schema": _CURATOR_TOOL_SCHEMA,
            },
        ],
        "tool_choice": {"type": "tool", "name": "record_curation_decision"},
    }
    body = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        "https://api.anthropic.com/v1/messages",
        data=body,
        headers={
            "x-api-key": api_key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        },
    )
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout_seconds) as resp:
            raw = resp.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        err_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Anthropic HTTP {exc.code}: {err_body[:500]}") from exc
    elapsed = time.time() - started
    response = json.loads(raw)
    if response.get("type") == "error":
        raise RuntimeError(f"Anthropic returned error: {response.get('error')}")
    tool_use_block = next(
        (b for b in response.get("content", []) if b.get("type") == "tool_use"),
        None,
    )
    if tool_use_block is None:
        raise RuntimeError(
            f"Anthropic returned no tool_use block; content was: "
            f"{response.get('content')!r}"
        )
    parsed = tool_use_block["input"]
    return {
        "parsed": parsed,
        "raw": response,
        "elapsedSeconds": round(elapsed, 2),
        "model": model,
    }


# ── apply curator decision ────────────────────────────────────────────────


_VALID_ACTIONS = {"promote", "promote_with_corrections", "reject", "keep_pending"}


def _validate_curator_response(parsed: dict[str, Any]) -> str | None:
    """Return None if valid, else a short error string."""
    action = parsed.get("action")
    if action not in _VALID_ACTIONS:
        return f"action must be one of {_VALID_ACTIONS}, got {action!r}"
    if not isinstance(parsed.get("reasoning"), str) or not parsed["reasoning"].strip():
        return "reasoning must be a non-empty string"
    if action == "promote_with_corrections":
        corr = parsed.get("corrections")
        if not isinstance(corr, dict) or not corr:
            return "promote_with_corrections requires non-empty corrections dict"
        for key in corr:
            if key not in {"modality", "authorityClass", "requirementKind"}:
                return f"corrections may only contain modality/authorityClass/requirementKind; got {key}"
        if "modality" in corr and corr["modality"] not in MODALITY_VALUES:
            return f"corrections.modality not in vocabulary: {corr['modality']!r}"
        if "authorityClass" in corr and corr["authorityClass"] not in AUTHORITY_CLASS_VALUES:
            return f"corrections.authorityClass not in vocabulary: {corr['authorityClass']!r}"
    return None


def _apply_corrections(record: dict[str, Any], corrections: dict[str, Any]) -> dict[str, Any]:
    out = dict(record)
    for key, value in corrections.items():
        out[key] = value
    return out


def _gate_check_after_corrections(record: dict[str, Any]) -> tuple[bool, list[str]]:
    """Re-run G2 + G3 against the corrected record. The other gates (G1
    verbatim quote, G4 override audit) cannot be invalidated by
    modality/authorityClass changes, so we don't re-check them.
    """
    g2_reasons = validate_pipeline_candidate({
        "candidateId": record.get("ingestionRun", {}).get("originalCandidateId") or "x",
        "sourceItemIds": record.get("sourceItemIds", []),
        "claimText": record.get("claimText"),
        "authorityClass": record.get("authorityClass"),
        "modality": record.get("modality"),
        "requirementKind": record.get("requirementKind"),
        "testability": record.get("testability"),
        "verificationMode": record.get("verificationMode"),
        "promotionHint": record.get("promotionHint"),
        "exactSourceQuotes": record.get("exactSourceQuotes", []),
        "supportingAttempts": ["curated"],
        "consistencyClass": "curated",
        "rationale": record.get("rationale"),
    })
    g3_consistent = is_authority_consistent(record.get("modality"), record.get("authorityClass"))
    failures: list[str] = []
    if g2_reasons:
        failures.extend(g2_reasons)
    if not g3_consistent:
        failures.append(
            f"G3 still fails after corrections: ({record.get('modality')!r}, "
            f"{record.get('authorityClass')!r}) violates AUTHORITY_CLASS_BY_FLOOR"
        )
    return (len(failures) == 0, failures)


def apply_curator_action(
    *,
    pending_path: Path,
    pending_record: dict[str, Any],
    parsed: dict[str, Any],
    registry_root: Path,
    dry_run: bool,
) -> dict[str, Any]:
    action = parsed["action"]
    document_id = pending_record["documentId"]
    section_id = pending_record["sectionId"]
    canonical_id = pending_record["canonicalId"]
    target_audit: dict[str, Any] = {
        "appliedAction": action,
        "dryRun": dry_run,
        "fromPath": str(pending_path.relative_to(registry_root)),
    }

    if action == "promote":
        target_path = registry_root / "candidates" / document_id / section_id / pending_path.name
        record_to_write = dict(pending_record)
        record_to_write.setdefault("audit", {})["curationApplied"] = {
            "action": "promote",
            "reasoning": parsed.get("reasoning"),
            "confidence": parsed.get("confidence"),
            "curatedAt": _utc_now(),
        }
        record_to_write.setdefault("lifecycle", {}).update({
            "state": "accepted",
            "curatedAt": _utc_now(),
        })
        record_to_write.setdefault("gate", {})["overallStatus"] = "pass"
        target_audit["toPath"] = str(target_path.relative_to(registry_root))
        if not dry_run:
            target_path.parent.mkdir(parents=True, exist_ok=True)
            target_path.write_text(json.dumps(record_to_write, indent=2) + "\n", encoding="utf-8")
            pending_path.unlink()
        return target_audit

    if action == "promote_with_corrections":
        corrections = parsed["corrections"]
        corrected = _apply_corrections(pending_record, corrections)
        passes, fail_reasons = _gate_check_after_corrections(corrected)
        if not passes:
            target_audit["downgraded"] = "keep_pending"
            target_audit["postCorrectionGateFailures"] = fail_reasons
            return target_audit
        # G3 audit needs to reflect the new consistency.
        corrected.setdefault("audit", {})["authorityModalityCheck"] = {
            "status": "pass",
            "modality": corrected.get("modality"),
            "authorityClass": corrected.get("authorityClass"),
            "floorConsistent": True,
            "overrideFired": pending_record.get("gate", {})
                .get("challenger", {})
                .get("overridden", False),
            "invariantViolated": False,
            "reason": None,
        }
        corrected["audit"]["schemaCheck"] = {"status": "pass", "reasons": []}
        corrected["audit"]["curationApplied"] = {
            "action": "promote_with_corrections",
            "reasoning": parsed.get("reasoning"),
            "corrections": corrections,
            "confidence": parsed.get("confidence"),
            "curatedAt": _utc_now(),
        }
        corrected.setdefault("gate", {})["overallStatus"] = "pass"
        corrected.setdefault("lifecycle", {}).update({
            "state": "accepted",
            "curatedAt": _utc_now(),
        })
        target_path = registry_root / "candidates" / document_id / section_id / pending_path.name
        target_audit["toPath"] = str(target_path.relative_to(registry_root))
        target_audit["correctionsApplied"] = corrections
        if not dry_run:
            target_path.parent.mkdir(parents=True, exist_ok=True)
            target_path.write_text(json.dumps(corrected, indent=2) + "\n", encoding="utf-8")
            pending_path.unlink()
        return target_audit

    if action == "reject":
        target_path = registry_root / "rejected" / document_id / section_id / pending_path.name
        record_to_write = dict(pending_record)
        record_to_write.setdefault("audit", {})["curationApplied"] = {
            "action": "reject",
            "reasoning": parsed.get("reasoning"),
            "confidence": parsed.get("confidence"),
            "curatedAt": _utc_now(),
        }
        record_to_write.setdefault("lifecycle", {}).update({
            "state": "rejected",
            "curatedAt": _utc_now(),
        })
        target_audit["toPath"] = str(target_path.relative_to(registry_root))
        if not dry_run:
            target_path.parent.mkdir(parents=True, exist_ok=True)
            target_path.write_text(json.dumps(record_to_write, indent=2) + "\n", encoding="utf-8")
            pending_path.unlink()
        return target_audit

    # keep_pending: annotate in place but do not move.
    annotated = dict(pending_record)
    annotated.setdefault("audit", {})["curationApplied"] = {
        "action": "keep_pending",
        "reasoning": parsed.get("reasoning"),
        "confidence": parsed.get("confidence"),
        "curatedAt": _utc_now(),
    }
    if not dry_run:
        pending_path.write_text(json.dumps(annotated, indent=2) + "\n", encoding="utf-8")
    return target_audit


def _final_record_path_after_action(
    *,
    pending_path: Path,
    action_audit: dict[str, Any],
    registry_root: Path,
) -> Path:
    to_path = action_audit.get("toPath")
    if isinstance(to_path, str) and to_path:
        return registry_root / to_path
    return pending_path


def append_curator_judgement_row(
    *,
    quality_root: Path,
    registry_root: Path,
    pending_path: Path,
    pending_record: dict[str, Any],
    parsed: dict[str, Any],
    action_audit: dict[str, Any],
    run_id: str,
    model: str,
    audit_path: Path,
    timestamp_utc: str,
) -> int:
    """Append one post-curation outcome row to `judgements.csv`.

    The row is derived from the final registry record after the curator
    action has been applied. That makes the regression detector's
    latest-row view match the actual bucket/lifecycle state.
    """
    final_path = _final_record_path_after_action(
        pending_path=pending_path,
        action_audit=action_audit,
        registry_root=registry_root,
    )
    final_record = (
        _read_json(final_path)
        if final_path.exists()
        else pending_record
    )
    applied = action_audit.get("downgraded") or action_audit.get("appliedAction") or ""
    row = csv_row_from_registry_record(
        final_record,
        event_source="curator",
        curation_run_id=run_id,
        curation_model=model,
        curation_action=str(applied),
        curation_confidence=str(parsed.get("confidence") or ""),
        curation_reasoning=str(parsed.get("reasoning") or action_audit.get("error") or ""),
        curation_corrections=parsed.get("corrections") if isinstance(parsed.get("corrections"), dict) else None,
        curation_audit_path=str(audit_path.relative_to(quality_root)),
    )
    return append_csv_rows(
        quality_root / "judgements.csv",
        [row],
        promoter_run_id=run_id,
        promoter_run_timestamp_utc=timestamp_utc,
    )


# ── driver ────────────────────────────────────────────────────────────────


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument(
        "--source-run-root",
        type=Path,
        required=True,
        help="The /tmp run root that originally produced the registry "
             "(needed for source_window.json and per-stage rationales).",
    )
    parser.add_argument(
        "--registry-root",
        type=Path,
        default=DEFAULT_REGISTRY_ROOT,
    )
    parser.add_argument(
        "--quality-root",
        type=Path,
        default=DEFAULT_QUALITY_ROOT,
    )
    parser.add_argument("--limit", type=int, default=None,
                        help="Curate at most N records; useful for smoke tests.")
    parser.add_argument("--model", default=DEFAULT_OPENAI_MODEL)
    parser.add_argument("--dry-run", action="store_true",
                        help="Call GPT but do not modify the registry.")
    parser.add_argument(
        "--filter-document",
        default=None,
        help="Only curate records whose documentId equals this string.",
    )
    args = parser.parse_args(argv)

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        print("OPENAI_API_KEY not set in env", file=sys.stderr)
        return 1

    pending_paths = []
    pending_dir = args.registry_root / "pending"
    if pending_dir.exists():
        for path in sorted(pending_dir.rglob("*.json")):
            if not looks_like_record_filename(path.name):
                continue
            pending_paths.append(path)

    if args.filter_document:
        pending_paths = [
            p for p in pending_paths
            if json.loads(p.read_text(encoding="utf-8")).get("documentId") == args.filter_document
        ]

    if args.limit:
        pending_paths = pending_paths[: args.limit]

    if not pending_paths:
        print("no pending records to curate")
        return 0

    run_id = _new_run_id()
    audit_dir = args.quality_root / "curation" / run_id
    audit_dir.mkdir(parents=True, exist_ok=True)

    counts: dict[str, int] = {}
    total = len(pending_paths)
    print(f"[curate] runId={run_id} | model={args.model} | dry-run={args.dry_run} | records={total}")

    for idx, path in enumerate(pending_paths, 1):
        try:
            pending_record = _read_json(path)
            bundle = build_context_bundle(
                pending_record=pending_record,
                registry_root=args.registry_root,
                source_run_root=args.source_run_root,
            )
            response = call_gpt(api_key=api_key, model=args.model, bundle=bundle)
            err = _validate_curator_response(response["parsed"])
            if err:
                print(f"[{idx}/{total}] {path.name}: INVALID GPT response — {err}")
                action_audit = {
                    "appliedAction": "keep_pending",
                    "downgraded": "invalid_response",
                    "error": err,
                }
            else:
                action_audit = apply_curator_action(
                    pending_path=path,
                    pending_record=pending_record,
                    parsed=response["parsed"],
                    registry_root=args.registry_root,
                    dry_run=args.dry_run,
                )

            applied = action_audit.get("downgraded") or action_audit.get("appliedAction")
            counts[applied] = counts.get(applied, 0) + 1
            print(
                f"[{idx}/{total}] {pending_record.get('canonicalId')[-16:]} "
                f"({pending_record.get('documentId')}) -> {applied}  "
                f"({response.get('elapsedSeconds')}s, conf={response['parsed'].get('confidence')})"
            )

            audit_path = audit_dir / f"{path.stem}.json"
            curated_at = _utc_now()
            ledger_rows_appended = 0
            if not args.dry_run:
                ledger_rows_appended = append_curator_judgement_row(
                    quality_root=args.quality_root,
                    registry_root=args.registry_root,
                    pending_path=path,
                    pending_record=pending_record,
                    parsed=response["parsed"],
                    action_audit=action_audit,
                    run_id=run_id,
                    model=args.model,
                    audit_path=audit_path,
                    timestamp_utc=curated_at,
                )
            action_audit["judgementRowsAppended"] = ledger_rows_appended

            audit_path.write_text(
                json.dumps({
                    "canonicalId": pending_record.get("canonicalId"),
                    "documentId": pending_record.get("documentId"),
                    "sectionId": pending_record.get("sectionId"),
                    "model": args.model,
                    "elapsedSeconds": response.get("elapsedSeconds"),
                    "gptResponse": response["parsed"],
                    "actionAudit": action_audit,
                    "curatedAt": curated_at,
                }, indent=2) + "\n",
                encoding="utf-8",
            )
        except Exception as exc:  # noqa: BLE001
            counts["error"] = counts.get("error", 0) + 1
            print(f"[{idx}/{total}] {path.name}: ERROR — {type(exc).__name__}: {exc}")
            (audit_dir / f"{path.stem}.error.json").write_text(
                json.dumps({
                    "path": str(path),
                    "error": f"{type(exc).__name__}: {exc}",
                    "curatedAt": _utc_now(),
                }, indent=2) + "\n",
                encoding="utf-8",
            )

    print()
    print("Curation summary:")
    for action, n in sorted(counts.items(), key=lambda kv: -kv[1]):
        print(f"  {action}: {n}")
    print()
    print(f"Audit trail: {audit_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
