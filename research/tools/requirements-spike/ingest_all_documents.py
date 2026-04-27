#!/usr/bin/env python3
"""Cross-document ingestion meta-driver.

Loops `ingest_document` over all (or a `--documents` subset of) the
manifests under `documents/*.json`, aggregates a cross-document summary,
and writes:

  - {output_root}/{document_id}/         — per-document outputs
  - {output_root}/all_documents.json     — cross-document aggregate
  - {output_root}/all_documents.md       — cross-document human summary

Per-section failures are isolated by the inner driver: a single section's
failure is logged in the per-document `failures.json` and does not
abort the run. A whole-document hard error (e.g. missing manifest) is
caught here and likewise does not abort the run.

Usage:

    python3 research/tools/requirements-spike/ingest_all_documents.py \\
        --output-root /tmp/icao4444-overnight-shakedown

    python3 research/tools/requirements-spike/ingest_all_documents.py \\
        --output-root /tmp/foo \\
        --documents icao4444 sera

Per-document caching: passing the same `--output-root` again skips
already-ingested sections; only new or failed sections are re-run.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path
from typing import Any


sys.path.insert(0, str(Path(__file__).resolve().parent))

from ingest_document import (  # noqa: E402
    DEFAULT_BASE_URL,
    DOCUMENTS_DIR,
    ingest_document,
)


def discover_documents() -> list[str]:
    return sorted(p.stem for p in DOCUMENTS_DIR.glob("*.json"))


def render_cross_document_summary(aggregate: dict[str, Any]) -> str:
    lines = [
        "# Cross-Document Ingestion Summary",
        "",
        f"- run started: {aggregate['runStartedAt']}",
        f"- run finished: {aggregate['runFinishedAt']}",
        f"- elapsed seconds: {aggregate['elapsedSeconds']}",
        f"- documents attempted: `{len(aggregate['documents'])}`",
        f"- documents with errors: "
        f"`{sum(1 for d in aggregate['documents'] if d.get('error'))}`",
        f"- sections judged: `{aggregate['totals']['candidatesJudged']}`",
        f"- accepted: `{aggregate['totals']['accepted']}`",
        f"- advisory_only: `{aggregate['totals']['advisoryOnly']}`",
        f"- needs_human_review: `{aggregate['totals']['needsHumanReview']}`",
        f"- other: `{aggregate['totals']['other']}`",
        f"- overrides fired: "
        f"challenger=`{aggregate['totals']['overridesFired']['challenger']}`, "
        f"judge=`{aggregate['totals']['overridesFired']['judge']}`, "
        f"sibling_resolution=`{aggregate['totals']['overridesFired']['sibling_resolution']}`",
        "",
        "## Documents",
        "",
    ]
    for doc in aggregate["documents"]:
        if doc.get("error"):
            lines.append(f"- **{doc['documentId']}**: ERROR — {doc['error']}")
            continue
        totals = doc["totals"]
        lines.append(
            f"- **{doc['documentId']}**: "
            f"sections `{totals['ingested']}`/`{totals['sections']}`, "
            f"judged `{totals['candidatesJudged']}`, "
            f"accepted `{totals['accepted']}`, "
            f"advisory `{totals['advisoryOnly']}`, "
            f"failed `{len(doc.get('sectionsFailed') or [])}`"
        )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Cross-document Ollama-first ingestion meta-driver.",
    )
    parser.add_argument("--output-root", type=Path, required=True,
                        help="Output directory; one subdir per document.")
    parser.add_argument("--documents", nargs="*",
                        help="Subset of document ids to ingest. "
                             "Default: every manifest under documents/*.json.")
    parser.add_argument("--force", action="store_true",
                        help="Re-run sections even if already ingested.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--num-ctx", type=int, default=24576)
    parser.add_argument("--max-candidates", type=int, default=8)
    parser.add_argument("--structure-attempts", type=int, default=3)
    parser.add_argument("--extraction-attempts", type=int, default=3)
    args = parser.parse_args()

    documents = args.documents or discover_documents()
    if not documents:
        raise SystemExit("No document manifests found under documents/*.json")

    output_root: Path = args.output_root
    output_root.mkdir(parents=True, exist_ok=True)

    started_at = time.time()
    started_iso = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(started_at))
    print(f"[{started_iso}] meta-driver: ingesting {len(documents)} documents "
          f"into {output_root}: {documents}", flush=True)

    per_document: list[dict[str, Any]] = []
    grand_totals = {
        "candidatesJudged": 0,
        "accepted": 0,
        "advisoryOnly": 0,
        "needsHumanReview": 0,
        "other": 0,
        "overridesFired": {"challenger": 0, "judge": 0, "sibling_resolution": 0},
    }

    for document_id in documents:
        document_dir = output_root / document_id
        try:
            result = ingest_document(
                document_id=document_id,
                output_dir=document_dir,
                base_url=args.base_url,
                num_ctx=args.num_ctx,
                max_candidates=args.max_candidates,
                structure_attempts=args.structure_attempts,
                extraction_attempts=args.extraction_attempts,
                force=args.force,
            )
            per_document.append(result)
            t = result["totals"]
            grand_totals["candidatesJudged"] += t["candidatesJudged"]
            grand_totals["accepted"] += t["accepted"]
            grand_totals["advisoryOnly"] += t["advisoryOnly"]
            grand_totals["needsHumanReview"] += t["needsHumanReview"]
            grand_totals["other"] += t["other"]
            for k, v in t["overridesFired"].items():
                grand_totals["overridesFired"][k] += v
        except SystemExit as exc:
            err = str(exc) or "SystemExit"
            print(f"[fail] {document_id}: {err[:300]}", flush=True)
            per_document.append({
                "documentId": document_id,
                "documentDir": str(document_dir),
                "error": err,
                "errorClass": "SystemExit",
            })
        except Exception as exc:  # noqa: BLE001 — overnight robustness
            err = f"{type(exc).__name__}: {exc}"
            print(f"[fail] {document_id}: {err[:300]}", flush=True)
            per_document.append({
                "documentId": document_id,
                "documentDir": str(document_dir),
                "error": err,
                "errorClass": type(exc).__name__,
            })

    finished_at = time.time()
    finished_iso = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(finished_at))
    aggregate = {
        "runStartedAt": started_iso,
        "runFinishedAt": finished_iso,
        "elapsedSeconds": int(finished_at - started_at),
        "outputRoot": str(output_root),
        "documents": per_document,
        "totals": grand_totals,
    }

    aggregate_path = output_root / "all_documents.json"
    aggregate_path.write_text(json.dumps(aggregate, indent=2), encoding="utf-8")
    summary_path = output_root / "all_documents.md"
    summary_path.write_text(
        render_cross_document_summary(aggregate), encoding="utf-8",
    )

    print(json.dumps({
        "outputRoot": str(output_root),
        "documentsAttempted": len(documents),
        "elapsedSeconds": aggregate["elapsedSeconds"],
        "totals": grand_totals,
        "aggregatePath": str(aggregate_path),
        "summaryPath": str(summary_path),
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
