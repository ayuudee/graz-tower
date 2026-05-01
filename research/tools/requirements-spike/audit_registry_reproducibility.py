#!/usr/bin/env python3
"""Reproducibility audit for the Ollama-first registry.

Two modes:

  * `--dry-run-only` (default, fast, no Ollama): walks the registry and
    asserts that every record's recorded `canonicalId` equals
    (a) the value re-derived from `(documentId, sectionId, claimText,
        exactSourceQuotes)` via `canonical_id.canonical_id_for`,
    (b) the file's stem (filename), and
    (c) the directory path's `documentId/sectionId` segments.

    This catches manual edits to claim text or quotes that forgot to
    update canonicalId, file renames that drift the filename out of
    sync, and `canonical_id_for` algorithm changes that would
    retroactively invalidate prior IDs. It is the cheap correctness
    check that should run on every CI commit touching the registry.

  * `--full` (slow, requires Ollama at the configured base URL):
    re-runs the pipeline for one or more sections to a scratch
    directory and compares the derived canonicalId set against the
    registry's set for that section. claimText byte-equality is a
    hard fail. Rationale drift is informational by default; pass
    `--strict` to make it fail.

Usage:

    python3 research/tools/requirements-spike/audit_registry_reproducibility.py
        # default: dry-run audit of the whole registry

    python3 research/tools/requirements-spike/audit_registry_reproducibility.py \\
        --full --document icao4444-extracted --section readback_4_5_7_5

Exits non-zero on any mismatch and writes a structured JSON report to
`--report` (default `quality/reproducibility_report.json`).
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


sys.path.insert(0, str(Path(__file__).resolve().parent))

from canonical_id import (  # noqa: E402
    canonical_id_for,
    is_sidecar_filename,
    looks_like_record_filename,
)


REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_REGISTRY_ROOT = Path(__file__).resolve().parent / "registry" / "ollama_first"
DEFAULT_QUALITY_ROOT = Path(__file__).resolve().parent / "quality"


# ── dry-run audit ─────────────────────────────────────────────────────────


def _classify_paths(registry_root: Path) -> tuple[list[Path], list[Path]]:
    """Walk the registry; return (record_paths, unexpected_paths).
    A `*.json` file is a record iff its name matches the canonical-id
    shape; a sidecar iff its name starts with `_`; otherwise it's
    unexpected and the audit must surface it (per 'no silent drops')."""
    records: list[Path] = []
    unexpected: list[Path] = []
    for bucket in ("candidates", "pending", "rejected"):
        bucket_dir = registry_root / bucket
        if not bucket_dir.exists():
            continue
        for path in sorted(bucket_dir.rglob("*.json")):
            if looks_like_record_filename(path.name):
                records.append(path)
            elif is_sidecar_filename(path.name):
                continue
            else:
                unexpected.append(path)
    return records, unexpected


def _check_record(record_path: Path, registry_root: Path) -> list[dict[str, Any]]:
    """Apply the four round-trip checks to a single record. Returns a
    (possibly empty) list of mismatch records."""
    mismatches: list[dict[str, Any]] = []
    try:
        record = json.loads(record_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError) as exc:
        return [{
            "kind": "unreadable",
            "path": str(record_path.relative_to(registry_root)),
            "error": str(exc),
        }]

    recorded_id = record.get("canonicalId")
    document_id = record.get("documentId")
    section_id = record.get("sectionId")
    claim_text = record.get("claimText")
    exact_quotes = record.get("exactSourceQuotes")

    # Check 1: canonical id round-trip.
    try:
        recomputed = canonical_id_for(
            document_id=document_id or "",
            section_id=section_id or "",
            claim_text=claim_text or "",
            exact_source_quotes=exact_quotes or [],
        )
    except (ValueError, TypeError) as exc:
        mismatches.append({
            "kind": "cannot_derive_canonical_id",
            "path": str(record_path.relative_to(registry_root)),
            "recordedCanonicalId": recorded_id,
            "error": str(exc),
        })
        return mismatches

    if recomputed != recorded_id:
        mismatches.append({
            "kind": "canonical_id_recompute_mismatch",
            "path": str(record_path.relative_to(registry_root)),
            "recordedCanonicalId": recorded_id,
            "recomputedCanonicalId": recomputed,
            "message": (
                "claim_text or exactSourceQuotes was edited without re-deriving "
                "canonicalId, OR canonical_id_for's algorithm changed. The "
                "recorded id no longer matches the content's hash."
            ),
        })

    # Check 2: filename stem equals recorded canonicalId.
    stem_id = record_path.stem
    if stem_id != recorded_id:
        mismatches.append({
            "kind": "filename_canonical_id_mismatch",
            "path": str(record_path.relative_to(registry_root)),
            "recordedCanonicalId": recorded_id,
            "filenameStem": stem_id,
            "message": (
                "the registry record's filename does not match its canonicalId. "
                "A manual rename or directory move drifted the file out of sync."
            ),
        })

    # Check 3: directory path's (documentId, sectionId) match record fields.
    # Layout is `{registry_root}/{bucket}/{documentId}/{sectionId}/{canonicalId}.json`.
    sec_dir = record_path.parent
    doc_dir = sec_dir.parent
    if doc_dir.name != document_id:
        mismatches.append({
            "kind": "directory_documentId_mismatch",
            "path": str(record_path.relative_to(registry_root)),
            "recordedDocumentId": document_id,
            "directoryDocumentId": doc_dir.name,
        })
    if sec_dir.name != section_id:
        mismatches.append({
            "kind": "directory_sectionId_mismatch",
            "path": str(record_path.relative_to(registry_root)),
            "recordedSectionId": section_id,
            "directorySectionId": sec_dir.name,
        })

    return mismatches


def audit_dry_run(registry_root: Path) -> dict[str, Any]:
    """Walk the registry; recompute canonicalIds; return a structured
    report. The report is the falsifiable artefact: empty `mismatches`
    means the registry round-trips cleanly. Files with unexpected names
    (not canonical-id-shaped and not sidecars prefixed `_`) surface as
    `unexpected_file` so a misnamed or accidentally-renamed real record
    cannot slip through silently."""
    record_paths, unexpected_paths = _classify_paths(registry_root)
    all_mismatches: list[dict[str, Any]] = []
    for path in record_paths:
        all_mismatches.extend(_check_record(path, registry_root))
    for path in unexpected_paths:
        all_mismatches.append({
            "kind": "unexpected_file",
            "path": str(path.relative_to(registry_root)),
            "message": (
                "file in a record directory is not canonical-id-shaped and "
                "not a `_`-prefixed sidecar. If this is a real record, its "
                "filename was edited; restore to "
                "`{documentId}::{sectionId}::{16-hex}.json`. If it's an "
                "intentional sidecar, prefix the name with `_`."
            ),
        })
    return {
        "mode": "dry_run",
        "registryRoot": str(registry_root),
        "totalRecordsAudited": len(record_paths),
        "totalUnexpectedFiles": len(unexpected_paths),
        "totalMismatches": len(all_mismatches),
        "mismatches": all_mismatches,
        "status": "pass" if not all_mismatches else "fail",
    }


# ── full audit (Ollama-dependent) ─────────────────────────────────────────


def audit_full(
    *,
    registry_root: Path,
    document_id: str,
    section_id: str,
    base_url: str,
    strict: bool,
) -> dict[str, Any]:
    """Re-run the pipeline for one section against a scratch dir and
    compare the derived canonicalId set against the registry. Requires
    Ollama at `base_url`. The actual pipeline run is delegated to
    `run_icao4444_ollama_first_prototype.run_pipeline` and the in-memory
    promotion mirrors `promote_to_registry.promote_section`.

    Implementation note: this function is the integration-test seam for
    real reproducibility. It is NOT exercised by the standard unit-test
    suite (which is offline). Run it manually when curating the
    registry, or as a nightly CI job with Ollama available.
    """
    # Importing the pipeline lazily so the dry-run path stays free of
    # Ollama-related imports.
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from run_icao4444_ollama_first_prototype import (  # noqa: PLC0415
        ROOT,
        build_arg_parser,
        run_pipeline,
    )
    from promote_to_registry import (  # noqa: PLC0415
        DEFAULT_QUALITY_ROOT as _QR,  # type: ignore[attr-defined]  # noqa: F401
        promote_section,
    )

    section_dir_in_registry = registry_root / "candidates" / document_id / section_id
    section_meta_path = section_dir_in_registry / "_section.json"
    if not section_meta_path.exists():
        return {
            "mode": "full",
            "documentId": document_id,
            "sectionId": section_id,
            "status": "skipped",
            "reason": f"no _section.json at {section_meta_path}",
        }
    section_meta = json.loads(section_meta_path.read_text(encoding="utf-8"))

    # Build a synthetic case dict matching what `ingest_document` would
    # have used when the section was first ingested. We require that the
    # registry's recorded `provenance.{sourcePath, startLine, endLine}`
    # are present so we can reconstruct the source window.
    sample_record = next(
        (json.loads(p.read_text(encoding="utf-8"))
         for p in section_dir_in_registry.rglob("*.json")
         if looks_like_record_filename(p.name)),
        None,
    )
    if sample_record is None:
        return {
            "mode": "full",
            "documentId": document_id,
            "sectionId": section_id,
            "status": "skipped",
            "reason": "no candidate records to anchor the case dict",
        }
    provenance = sample_record.get("provenance") or {}
    source_path = provenance.get("sourcePath")
    start_line = provenance.get("startLine")
    end_line = provenance.get("endLine")
    if not source_path or start_line is None or end_line is None:
        return {
            "mode": "full",
            "documentId": document_id,
            "sectionId": section_id,
            "status": "skipped",
            "reason": "registry record missing provenance fields needed to re-run",
        }

    # Pull caseId / familyId from a registry record so the re-run sees
    # exactly the same prompt context the original ingestion did. The
    # fallback chain (ingestionRun.caseId → familyId → sectionId) is
    # surfaced explicitly in the result via `caseIdSource` so a curator
    # auditing a legacy registry sees the degraded mode rather than
    # treating a noisy disjoint diff as truth.
    family_id = sample_record.get("familyId") or section_id
    ingestion_case_id = (sample_record.get("ingestionRun") or {}).get("caseId")
    if ingestion_case_id:
        case_id = ingestion_case_id
        case_id_source = "ingestionRun"
    elif sample_record.get("familyId"):
        case_id = sample_record["familyId"]
        case_id_source = "familyId"
    else:
        case_id = section_id
        case_id_source = "sectionId"
    case = {
        "caseId": case_id,
        "documentId": document_id,
        "familyId": family_id,
        "authorityCeiling": sample_record.get("authorityClass"),
        "startLine": start_line,
        "endLine": end_line,
        "notes": "reproducibility audit re-run",
        "sourceOverride": source_path,
    }

    # Drive the pipeline into a scratch dir.
    import tempfile  # noqa: PLC0415
    with tempfile.TemporaryDirectory(prefix="repro-audit-") as scratch:
        scratch_path = Path(scratch)
        inner = build_arg_parser().parse_args([])
        inner.base_url = base_url
        inner.source = ROOT / source_path
        run_pipeline(case, inner, scratch_path)

        # Promote in-memory to a scratch registry dir and compare.
        scratch_registry = scratch_path / "scratch_registry"
        promote_section(
            section_dir=scratch_path,
            document_id=document_id,
            section_id=section_id,
            family_id=sample_record.get("familyId"),
            registry_root=scratch_registry,
            run_id="repro_audit_run",
        )

        # Compare canonicalId sets — only files that are actual records.
        registry_ids = {
            p.stem for p in section_dir_in_registry.rglob("*.json")
            if looks_like_record_filename(p.name)
        }
        scratch_section_dir = scratch_registry / "candidates" / document_id / section_id
        scratch_ids = {
            p.stem for p in scratch_section_dir.rglob("*.json")
            if looks_like_record_filename(p.name)
        } if scratch_section_dir.exists() else set()
        scratch_pending_dir = scratch_registry / "pending" / document_id / section_id
        scratch_pending_ids = {
            p.stem for p in scratch_pending_dir.rglob("*.json")
            if looks_like_record_filename(p.name)
        } if scratch_pending_dir.exists() else set()
        scratch_all_ids = scratch_ids | scratch_pending_ids

        only_in_registry = sorted(registry_ids - scratch_all_ids)
        only_in_scratch = sorted(scratch_all_ids - registry_ids)
        shared = registry_ids & scratch_all_ids

        # claimText byte-equality on shared ids. Pin the lookup to the
        # specific bucket+document+section path so non-deterministic
        # `rglob` ordering can never pick up a stray match elsewhere.
        claim_drifts: list[dict[str, Any]] = []
        rationale_drifts: list[dict[str, Any]] = []
        for cid in sorted(shared):
            registry_path = section_dir_in_registry / f"{cid}.json"
            scratch_path_match = next(
                (
                    p for p in (
                        scratch_registry / "candidates" / document_id / section_id / f"{cid}.json",
                        scratch_registry / "pending" / document_id / section_id / f"{cid}.json",
                    )
                    if p.exists()
                ),
                None,
            )
            if not registry_path.exists() or not scratch_path_match:
                continue
            registry_record = json.loads(registry_path.read_text(encoding="utf-8"))
            scratch_record = json.loads(scratch_path_match.read_text(encoding="utf-8"))
            if registry_record.get("claimText") != scratch_record.get("claimText"):
                claim_drifts.append({
                    "canonicalId": cid,
                    "registryClaimExcerpt": (registry_record.get("claimText") or "")[:120],
                    "scratchClaimExcerpt": (scratch_record.get("claimText") or "")[:120],
                })
            if registry_record.get("rationale") != scratch_record.get("rationale"):
                rationale_drifts.append({"canonicalId": cid})

        hard_fail = bool(only_in_registry or only_in_scratch or claim_drifts)
        if strict and rationale_drifts:
            hard_fail = True
        return {
            "mode": "full",
            "documentId": document_id,
            "sectionId": section_id,
            "caseIdUsed": case_id,
            "caseIdSource": case_id_source,
            "status": "fail" if hard_fail else "pass",
            "registryCanonicalIdCount": len(registry_ids),
            "scratchCanonicalIdCount": len(scratch_all_ids),
            "onlyInRegistry": only_in_registry,
            "onlyInScratch": only_in_scratch,
            "claimTextDrifts": claim_drifts,
            "rationaleDrifts": rationale_drifts,
            "rationaleStrict": strict,
        }


# ── CLI ───────────────────────────────────────────────────────────────────


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument(
        "--registry-root",
        type=Path,
        default=DEFAULT_REGISTRY_ROOT,
        help="Path to registry/ollama_first/.",
    )
    parser.add_argument(
        "--full",
        action="store_true",
        help="Run the full audit (re-runs the pipeline; needs Ollama).",
    )
    parser.add_argument(
        "--dry-run-only",
        action="store_true",
        help="Force dry-run mode even if --full is implied elsewhere (default behaviour).",
    )
    parser.add_argument("--document", help="Document id (required with --full).")
    parser.add_argument("--section", help="Section id (required with --full).")
    parser.add_argument(
        "--base-url",
        default="http://biggy:11434",
        help="Ollama base URL for --full mode.",
    )
    parser.add_argument(
        "--strict",
        action="store_true",
        help="In --full mode, treat rationale drift as a hard fail.",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=DEFAULT_QUALITY_ROOT / "reproducibility_report.json",
        help="Where to write the structured JSON report.",
    )
    args = parser.parse_args(argv)

    if args.full:
        if not args.document or not args.section:
            parser.error("--full requires --document and --section")
        result = audit_full(
            registry_root=args.registry_root,
            document_id=args.document,
            section_id=args.section,
            base_url=args.base_url,
            strict=args.strict,
        )
    else:
        result = audit_dry_run(args.registry_root)

    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")

    print(json.dumps(
        {k: v for k, v in result.items() if k != "mismatches"},
        indent=2,
    ))
    if result.get("status") == "fail":
        if "mismatches" in result:
            for m in result["mismatches"][:10]:
                print(f"  MISMATCH {m['kind']}: {m.get('path') or m.get('canonicalId')}")
            if len(result["mismatches"]) > 10:
                print(f"  ... ({len(result['mismatches']) - 10} more)")
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
