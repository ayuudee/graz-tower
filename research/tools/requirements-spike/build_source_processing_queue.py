#!/usr/bin/env python3
"""Build the queue for remaining Ollama-backed source-unit processing.

This is the bridge between the source-section ledger and the actual batch
runner. It separates three states that must not be conflated:

* ready_to_ingest: exact document-manifest windows with no accepted records yet;
* pending_curation: records already produced but not accepted/rejected; and
* needs_source_window_hardening: ledger rows that still need exact line windows
  before the Ollama section processor can run.
"""
from __future__ import annotations

import csv
import json
from collections import Counter, defaultdict
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[3]
SPIKE = ROOT / "research/tools/requirements-spike"
DOCUMENTS_DIR = SPIKE / "documents"
REGISTRY_ROOT = SPIKE / "registry/ollama_first"
LEDGER_CSV = (
    SPIKE
    / "quality/source_section_ledger/source_section_ledger_2026-04-30/source_section_ledger.csv"
)
DEFAULT_OUTPUT_DIR = SPIKE / "quality/source_processing_queue/source_processing_queue_2026-05-01"


def utc_now() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def load_manifests() -> tuple[dict[str, dict[str, Any]], dict[str, str]]:
    manifests: dict[str, dict[str, Any]] = {}
    stems_by_document_id: dict[str, str] = {}
    for path in sorted(DOCUMENTS_DIR.glob("*.json")):
        manifest = read_json(path)
        manifests[manifest["documentId"]] = {
            "stem": path.stem,
            "manifest": manifest,
            "path": str(path.relative_to(ROOT)),
        }
        stems_by_document_id[manifest["documentId"]] = path.stem
    return manifests, stems_by_document_id


def registry_counts(bucket: str) -> dict[tuple[str, str], int]:
    counts: dict[tuple[str, str], int] = {}
    bucket_root = REGISTRY_ROOT / bucket
    if not bucket_root.exists():
        return counts
    for document_dir in bucket_root.iterdir():
        if not document_dir.is_dir():
            continue
        for section_dir in document_dir.iterdir():
            if not section_dir.is_dir():
                continue
            files = [
                path
                for path in section_dir.glob("*.json")
                if path.name != "_section.json"
            ]
            if files:
                counts[(document_dir.name, section_dir.name)] = len(files)
    return counts


def manifest_windows() -> list[dict[str, Any]]:
    manifests, _ = load_manifests()
    rows: list[dict[str, Any]] = []
    for document_id, wrapper in sorted(manifests.items()):
        manifest = wrapper["manifest"]
        for section in manifest["sections"]:
            rows.append(
                {
                    "documentStem": wrapper["stem"],
                    "documentId": document_id,
                    "documentTitle": manifest.get("documentTitle", document_id),
                    "sourcePath": manifest["sourcePath"],
                    "sectionId": section["sectionId"],
                    "familyId": section.get("familyId", section["sectionId"]),
                    "startLine": section["startLine"],
                    "endLine": section["endLine"],
                    "authorityCeiling": section.get(
                        "authorityCeiling",
                        manifest.get("defaultAuthorityCeiling"),
                    ),
                    "notes": section.get("notes", ""),
                }
            )
    return rows


def load_ledger_rows() -> list[dict[str, str]]:
    with LEDGER_CSV.open(encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def build_queue() -> dict[str, Any]:
    accepted_counts = registry_counts("candidates")
    pending_counts = registry_counts("pending")
    rejected_counts = registry_counts("rejected")

    ready_to_ingest: list[dict[str, Any]] = []
    manifest_status: list[dict[str, Any]] = []
    for row in manifest_windows():
        key = (row["documentId"], row["sectionId"])
        accepted = accepted_counts.get(key, 0)
        pending = pending_counts.get(key, 0)
        rejected = rejected_counts.get(key, 0)
        if accepted:
            state = "landed"
        elif pending:
            state = "pending_only"
        else:
            state = "ready_to_ingest"
        enriched = {
            **row,
            "acceptedRecords": accepted,
            "pendingRecords": pending,
            "rejectedRecords": rejected,
            "state": state,
        }
        manifest_status.append(enriched)
        if state == "ready_to_ingest":
            ready_to_ingest.append(enriched)

    pending_curation = [
        {
            "documentId": document_id,
            "sectionId": section_id,
            "pendingRecords": count,
            "acceptedRecords": accepted_counts.get((document_id, section_id), 0),
        }
        for (document_id, section_id), count in sorted(pending_counts.items())
    ]

    needs_hardening: list[dict[str, Any]] = []
    for row in load_ledger_rows():
        if row["granularity"] == "manifest_window":
            continue
        if row["priority"] != "high":
            continue
        if row["disposition"] not in {"extract", "partially_extracted"}:
            continue
        has_exact_window = bool(row.get("manifest_section_id"))
        has_start_end = bool(row.get("start_line") and row.get("end_line"))
        needs_hardening.append(
            {
                "rowId": row["row_id"],
                "documentId": row["document_id"],
                "sourcePath": row["source_path"],
                "sectionRef": row["section_ref"],
                "title": row["title"],
                "disposition": row["disposition"],
                "priority": row["priority"],
                "startLine": row["start_line"] or None,
                "endLine": row["end_line"] or None,
                "rationale": row["rationale"],
                "nextAction": row["next_action"],
                "queueState": (
                    "has_exact_window"
                    if has_exact_window
                    else ("needs_manifesting" if has_start_end else "needs_exact_line_range")
                ),
            }
        )

    ready_by_doc = Counter(row["documentId"] for row in ready_to_ingest)
    hardening_by_doc = Counter(row["documentId"] for row in needs_hardening)
    return {
        "schemaName": "requirements_source_processing_queue",
        "formatVersion": "2026-05-01-v1",
        "generatedAt": utc_now(),
        "registryRoot": str(REGISTRY_ROOT.relative_to(ROOT)),
        "sourceLedger": str(LEDGER_CSV.relative_to(ROOT)),
        "counts": {
            "manifestWindows": len(manifest_status),
            "landedManifestWindows": sum(1 for row in manifest_status if row["state"] == "landed"),
            "pendingOnlyManifestWindows": sum(1 for row in manifest_status if row["state"] == "pending_only"),
            "readyToIngestManifestWindows": len(ready_to_ingest),
            "pendingCurationSections": len(pending_curation),
            "pendingCurationRecords": sum(row["pendingRecords"] for row in pending_curation),
            "highPriorityRowsNeedingSourceWindowHardening": len(needs_hardening),
        },
        "readyToIngestByDocument": dict(sorted(ready_by_doc.items())),
        "hardeningNeededByDocument": dict(sorted(hardening_by_doc.items())),
        "manifestStatus": manifest_status,
        "readyToIngest": ready_to_ingest,
        "pendingCuration": pending_curation,
        "needsSourceWindowHardening": needs_hardening,
    }


def write_csv(path: Path, rows: list[dict[str, Any]], fieldnames: list[str]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow({name: row.get(name, "") for name in fieldnames})


def render_summary(queue: dict[str, Any], output_dir: Path) -> str:
    batch_path = output_dir / "ready_to_ingest_batch.json"
    lines = [
        "# Source Processing Queue",
        "",
        f"Generated: {queue['generatedAt']}",
        "",
        "## Counts",
        "",
        f"- Manifest windows: `{queue['counts']['manifestWindows']}`",
        f"- Landed manifest windows: `{queue['counts']['landedManifestWindows']}`",
        f"- Manifest windows ready for Ollama ingestion: `{queue['counts']['readyToIngestManifestWindows']}`",
        f"- Pending curation records: `{queue['counts']['pendingCurationRecords']}`",
        f"- High-priority ledger rows needing exact source-window hardening: `{queue['counts']['highPriorityRowsNeedingSourceWindowHardening']}`",
        "",
        "## Ready To Ingest By Document",
        "",
        "| Document | Windows |",
        "| --- | ---: |",
    ]
    for document_id, count in queue["readyToIngestByDocument"].items():
        lines.append(f"| {document_id} | {count} |")
    lines.extend(
        [
            "",
            "## Next Command",
            "",
            "Validate the batch without contacting Ollama:",
            "",
            "```bash",
            "nix-shell -p python3 --run \"python3 research/tools/requirements-spike/ingest_section_batch.py \\",
            f"  --batch-manifest {batch_path} \\",
            "  --dry-run\"",
            "```",
            "",
            "Run the exact manifest-only batch with:",
            "",
            "```bash",
            "nix-shell -p python3 --run \"python3 research/tools/requirements-spike/ingest_section_batch.py \\",
            f"  --batch-manifest {batch_path} \\",
            "  --output-root /tmp/requirements-source-units-$(date +%Y-%m-%d)\"",
            "```",
            "",
            "Then promote and audit:",
            "",
            "```bash",
            "nix-shell -p python3 --run \"python3 research/tools/requirements-spike/promote_to_registry.py \\",
            "  --source-run-root /tmp/requirements-source-units-$(date +%Y-%m-%d)\"",
            "nix-shell -p python3 --run \"python3 research/tools/requirements-spike/audit_registry_reproducibility.py\"",
            "```",
            "",
            "Then follow Phase G in `research/tools/requirements-spike/RUNBOOK.md` for any `pending/` records and snapshot `quality/judgements.csv`.",
            "",
            "Do not queue `needsSourceWindowHardening` rows directly; they still need exact line windows in `documents/*.json` first.",
        ]
    )
    return "\n".join(lines) + "\n"


def main() -> int:
    import argparse

    parser = argparse.ArgumentParser(description="Build remaining source processing queue.")
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    args = parser.parse_args()

    output_dir: Path = args.output_dir
    output_dir.mkdir(parents=True, exist_ok=True)
    queue = build_queue()

    (output_dir / "source_processing_queue.json").write_text(
        json.dumps(queue, indent=2) + "\n",
        encoding="utf-8",
    )
    batch = {
        "schemaName": "ollama_section_batch",
        "formatVersion": "2026-05-01-v1",
        "generatedAt": queue["generatedAt"],
        "sourceQueue": str((output_dir / "source_processing_queue.json").relative_to(ROOT)),
        "sections": queue["readyToIngest"],
    }
    (output_dir / "ready_to_ingest_batch.json").write_text(
        json.dumps(batch, indent=2) + "\n",
        encoding="utf-8",
    )
    write_csv(
        output_dir / "ready_to_ingest.csv",
        queue["readyToIngest"],
        [
            "documentStem",
            "documentId",
            "sectionId",
            "familyId",
            "startLine",
            "endLine",
            "authorityCeiling",
            "acceptedRecords",
            "pendingRecords",
            "rejectedRecords",
            "state",
            "notes",
        ],
    )
    write_csv(
        output_dir / "pending_curation.csv",
        queue["pendingCuration"],
        ["documentId", "sectionId", "acceptedRecords", "pendingRecords"],
    )
    write_csv(
        output_dir / "needs_source_window_hardening.csv",
        queue["needsSourceWindowHardening"],
        [
            "rowId",
            "documentId",
            "sectionRef",
            "title",
            "disposition",
            "priority",
            "startLine",
            "endLine",
            "queueState",
            "rationale",
            "nextAction",
        ],
    )
    (output_dir / "source_processing_queue.md").write_text(
        render_summary(queue, output_dir),
        encoding="utf-8",
    )
    print(json.dumps({"outputDir": str(output_dir), "counts": queue["counts"]}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
