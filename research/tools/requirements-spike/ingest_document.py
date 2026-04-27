#!/usr/bin/env python3
"""Per-document Ollama-first ingestion driver.

Reads a per-document manifest under `documents/{document_id}.json` and runs
the per-section pipeline (`run_icao4444_ollama_first_prototype.run_pipeline`)
for each section in turn. Aggregates per-section outputs into a per-document
output directory and emits an aggregate `accepted_candidates.json` plus a
per-document `summary.md`.

Usage:

    python3 research/tools/requirements-spike/ingest_document.py \\
        --document icao4444 \\
        --output-dir /tmp/icao4444-document-ingest

Per-section caching: if the per-section output directory already contains a
non-empty `run_manifest.json`, the section is treated as already ingested
and is not re-run unless `--force` is passed.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


sys.path.insert(0, str(Path(__file__).resolve().parent))

from run_icao4444_ollama_first_prototype import (  # noqa: E402
    DEFAULT_BASE_URL,
    DEFAULT_SOURCE,
    ROOT,
    build_arg_parser,
    run_pipeline,
)


DOCUMENTS_DIR = Path(__file__).resolve().parent / "documents"


def load_manifest(document_id: str) -> dict[str, Any]:
    path = DOCUMENTS_DIR / f"{document_id}.json"
    if not path.exists():
        raise SystemExit(
            f"No document manifest at {path}. "
            f"Available: {sorted(p.stem for p in DOCUMENTS_DIR.glob('*.json'))}"
        )
    return json.loads(path.read_text(encoding="utf-8"))


def section_to_case(manifest: dict[str, Any], section: dict[str, Any]) -> dict[str, Any]:
    """Build a `CASES`-shaped case dict from a manifest section.

    The pipeline's `run_pipeline` consumes this shape.
    """
    return {
        "caseId": section.get("familyId") or section["sectionId"],
        "documentId": manifest["documentId"],
        "familyId": section.get("familyId") or section["sectionId"],
        "authorityCeiling": section.get(
            "authorityCeiling", manifest.get("defaultAuthorityCeiling"),
        ),
        "startLine": section["startLine"],
        "endLine": section["endLine"],
        "notes": section.get("notes", ""),
        "sourceOverride": manifest["sourcePath"],
    }


def already_ingested(section_dir: Path) -> bool:
    manifest_path = section_dir / "run_manifest.json"
    if not manifest_path.exists():
        return False
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return False
    return bool(manifest.get("judgedCandidateCount"))


def aggregate_document(document_dir: Path, manifest: dict[str, Any]) -> dict[str, Any]:
    """Produce per-document aggregate output: list of accepted candidates with
    section provenance, plus the parallel summary table.
    """
    sections_summary: list[dict[str, Any]] = []
    accepted: list[dict[str, Any]] = []
    advisory: list[dict[str, Any]] = []
    needs_human: list[dict[str, Any]] = []
    other: list[dict[str, Any]] = []
    total_judged = 0
    total_overrides = {"challenger": 0, "judge": 0, "sibling_resolution": 0}

    for section in manifest["sections"]:
        section_dir = document_dir / section["sectionId"]
        run_manifest_path = section_dir / "run_manifest.json"
        judged_path = section_dir / "judged_candidates.json"
        if not run_manifest_path.exists() or not judged_path.exists():
            sections_summary.append({
                "sectionId": section["sectionId"],
                "status": "missing",
            })
            continue
        run_manifest = json.loads(run_manifest_path.read_text(encoding="utf-8"))
        judged = json.loads(judged_path.read_text(encoding="utf-8"))
        section_accepted: list[str] = []
        section_advisory: list[str] = []
        section_other: list[str] = []
        for item in judged:
            decision = (item.get("judgeForRecord") or item["judge"])["decision"]
            row = {
                "sectionId": section["sectionId"],
                "candidateId": item["candidate"]["candidateId"],
                "claimText": item["candidate"]["claimText"],
                "authorityClass": item["candidate"]["authorityClass"],
                "modality": item["candidate"]["modality"],
                "promotionHint": item["candidate"]["promotionHint"],
                "decision": decision,
                "challengerOverridden": item.get("challengeOverride") is not None,
                "judgeOverridden": item.get("judgeOverride") is not None,
                "sourceItemIds": item["candidate"]["sourceItemIds"],
                "exactSourceQuotes": item["candidate"]["exactSourceQuotes"],
            }
            if decision == "accepted":
                accepted.append(row)
                section_accepted.append(row["candidateId"])
            elif decision == "advisory_only":
                advisory.append(row)
                section_advisory.append(row["candidateId"])
            elif decision == "needs_human_review":
                needs_human.append(row)
                section_other.append(row["candidateId"])
            else:
                other.append(row)
                section_other.append(row["candidateId"])
            total_judged += 1
            if row["challengerOverridden"]:
                total_overrides["challenger"] += 1
            if row["judgeOverridden"]:
                total_overrides["judge"] += 1
        if (section_dir / "bundle_gate_sibling_resolution.json").exists():
            total_overrides["sibling_resolution"] += 1
        sections_summary.append({
            "sectionId": section["sectionId"],
            "status": "ingested",
            "candidateCount": run_manifest.get("candidateCount"),
            "judgedCandidateCount": run_manifest.get("judgedCandidateCount"),
            "acceptedCount": len(section_accepted),
            "advisoryCount": len(section_advisory),
            "otherCount": len(section_other),
        })

    aggregate = {
        "documentId": manifest["documentId"],
        "documentTitle": manifest.get("documentTitle"),
        "sourcePath": manifest["sourcePath"],
        "totals": {
            "sections": len(manifest["sections"]),
            "ingested": sum(1 for s in sections_summary if s["status"] == "ingested"),
            "candidatesJudged": total_judged,
            "accepted": len(accepted),
            "advisoryOnly": len(advisory),
            "needsHumanReview": len(needs_human),
            "other": len(other),
            "overridesFired": total_overrides,
        },
        "sections": sections_summary,
        "acceptedCandidates": accepted,
        "advisoryCandidates": advisory,
        "needsHumanReviewCandidates": needs_human,
        "otherCandidates": other,
    }
    return aggregate


def render_document_summary(aggregate: dict[str, Any]) -> str:
    lines = [
        f"# Per-Document Ingestion Summary: `{aggregate['documentId']}`",
        "",
        f"- title: {aggregate.get('documentTitle') or aggregate['documentId']}",
        f"- source: `{aggregate['sourcePath']}`",
        f"- sections: `{aggregate['totals']['sections']}` "
        f"(ingested: `{aggregate['totals']['ingested']}`)",
        f"- candidates judged: `{aggregate['totals']['candidatesJudged']}`",
        f"- accepted: `{aggregate['totals']['accepted']}`",
        f"- advisory_only: `{aggregate['totals']['advisoryOnly']}`",
        f"- needs_human_review: `{aggregate['totals']['needsHumanReview']}`",
        f"- other (needs_split / needs_bundle / unsupported / ambiguous): "
        f"`{aggregate['totals']['other']}`",
        f"- overrides fired: challenger=`{aggregate['totals']['overridesFired']['challenger']}`, "
        f"judge=`{aggregate['totals']['overridesFired']['judge']}`, "
        f"sibling_resolution=`{aggregate['totals']['overridesFired']['sibling_resolution']}`",
        "",
        "## Sections",
        "",
    ]
    for section in aggregate["sections"]:
        if section["status"] == "ingested":
            lines.append(
                f"- `{section['sectionId']}`: judged "
                f"`{section['judgedCandidateCount']}` of "
                f"`{section['candidateCount']}` — "
                f"accepted `{section['acceptedCount']}`, "
                f"advisory `{section['advisoryCount']}`, "
                f"other `{section['otherCount']}`"
            )
        else:
            lines.append(f"- `{section['sectionId']}`: **{section['status']}**")
    if aggregate["acceptedCandidates"]:
        lines.append("")
        lines.append("## Accepted Candidates")
        lines.append("")
        for candidate in aggregate["acceptedCandidates"]:
            override_marker = ""
            if candidate["challengerOverridden"] or candidate["judgeOverridden"]:
                pieces = []
                if candidate["challengerOverridden"]:
                    pieces.append("challenger-override")
                if candidate["judgeOverridden"]:
                    pieces.append("judge-override")
                override_marker = f" _({', '.join(pieces)})_"
            lines.append(
                f"- `{candidate['sectionId']}::{candidate['candidateId']}` "
                f"({candidate['authorityClass']}, {candidate['modality']}){override_marker}"
            )
            lines.append(f"  - {candidate['claimText']}")
    return "\n".join(lines) + "\n"


def ingest_document(
    *,
    document_id: str,
    output_dir: Path,
    base_url: str = DEFAULT_BASE_URL,
    num_ctx: int = 24576,
    max_candidates: int = 8,
    structure_attempts: int = 3,
    extraction_attempts: int = 3,
    force: bool = False,
) -> dict[str, Any]:
    """Run the per-section pipeline across every section in the document
    manifest. Per-section failures are caught, logged, recorded in
    `failures.json`, and do not abort the run — important for unattended
    multi-document ingestion.

    Returns the per-document aggregate result.
    """
    manifest = load_manifest(document_id)
    output_dir.mkdir(parents=True, exist_ok=True)

    inner_defaults = build_arg_parser().parse_args([])
    inner_defaults.base_url = base_url
    inner_defaults.num_ctx = num_ctx
    inner_defaults.max_candidates = max_candidates
    inner_defaults.structure_attempts = structure_attempts
    inner_defaults.extraction_attempts = extraction_attempts
    inner_defaults.source = ROOT / manifest["sourcePath"]

    sections_processed: list[str] = []
    sections_skipped: list[str] = []
    sections_failed: list[dict[str, Any]] = []
    for section in manifest["sections"]:
        section_dir = output_dir / section["sectionId"]
        if not force and already_ingested(section_dir):
            print(f"[skip] {document_id}/{section['sectionId']}: already ingested")
            sections_skipped.append(section["sectionId"])
            continue
        case = section_to_case(manifest, section)
        print(
            f"[run]  {document_id}/{section['sectionId']}: "
            f"lines {case['startLine']}-{case['endLine']}",
            flush=True,
        )
        try:
            run_pipeline(case, inner_defaults, section_dir)
            sections_processed.append(section["sectionId"])
        except SystemExit as exc:
            err = str(exc) or "SystemExit"
            print(f"[fail] {document_id}/{section['sectionId']}: {err[:300]}",
                  flush=True)
            sections_failed.append({
                "sectionId": section["sectionId"], "error": err,
                "errorClass": "SystemExit",
            })
        except Exception as exc:  # noqa: BLE001 — overnight robustness
            err = f"{type(exc).__name__}: {exc}"
            print(f"[fail] {document_id}/{section['sectionId']}: {err[:300]}",
                  flush=True)
            sections_failed.append({
                "sectionId": section["sectionId"], "error": err,
                "errorClass": type(exc).__name__,
            })

    if sections_failed:
        (output_dir / "failures.json").write_text(
            json.dumps(sections_failed, indent=2), encoding="utf-8",
        )
    elif (output_dir / "failures.json").exists():
        (output_dir / "failures.json").unlink()

    aggregate = aggregate_document(output_dir, manifest)
    aggregate["sectionsFailed"] = sections_failed
    aggregate_path = output_dir / "accepted_candidates.json"
    aggregate_path.write_text(json.dumps(aggregate, indent=2), encoding="utf-8")
    summary_md = output_dir / "summary.md"
    summary_md.write_text(render_document_summary(aggregate), encoding="utf-8")

    return {
        "documentId": manifest["documentId"],
        "documentDir": str(output_dir),
        "sectionsProcessed": sections_processed,
        "sectionsSkipped": sections_skipped,
        "sectionsFailed": [s["sectionId"] for s in sections_failed],
        "totals": aggregate["totals"],
        "aggregatePath": str(aggregate_path),
        "summaryPath": str(summary_md),
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Per-document Ollama-first ingestion driver.",
    )
    parser.add_argument("--document", required=True,
                        help="Document id matching documents/{id}.json")
    parser.add_argument("--output-dir", type=Path, required=True,
                        help="Per-document output directory; one subdir per section.")
    parser.add_argument("--force", action="store_true",
                        help="Re-run sections even if their output already exists.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--num-ctx", type=int, default=24576)
    parser.add_argument("--max-candidates", type=int, default=8)
    parser.add_argument("--structure-attempts", type=int, default=3)
    parser.add_argument("--extraction-attempts", type=int, default=3)
    args = parser.parse_args()

    result = ingest_document(
        document_id=args.document,
        output_dir=args.output_dir,
        base_url=args.base_url,
        num_ctx=args.num_ctx,
        max_candidates=args.max_candidates,
        structure_attempts=args.structure_attempts,
        extraction_attempts=args.extraction_attempts,
        force=args.force,
    )
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
