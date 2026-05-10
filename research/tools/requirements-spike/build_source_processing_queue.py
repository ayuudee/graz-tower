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
RESEARCH_TXT = ROOT / "research/txt"
RESEARCH_PDF = ROOT / "research/pdf"
LEDGER_CSV = (
    SPIKE
    / "quality/source_section_ledger/source_section_ledger_2026-04-30/source_section_ledger.csv"
)
DEFAULT_OUTPUT_DIR = SPIKE / "quality/source_processing_queue/source_processing_queue_2026-05-01"


SOURCE_SCOPE_NOTES: dict[str, list[str]] = {
    "h01-extracted": [
        (
            "H01 records are English-side scoped for manifested AIC A 21/23 "
            "sections; they do not certify German/English translation equivalence."
        ),
        (
            "The H01 package should claim completeness only for manifested English-side "
            "windows that have landed and been curated."
        ),
    ],
}


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


def registry_document_counts(bucket: str) -> dict[str, int]:
    by_section = registry_counts(bucket)
    counts: Counter[str] = Counter()
    for document_id, _section_id in by_section:
        counts[document_id] += by_section[(document_id, _section_id)]
    return dict(sorted(counts.items()))


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


def _source_paths_for_keywords(keywords: tuple[str, ...]) -> list[str]:
    paths: list[Path] = []
    for root in (RESEARCH_TXT, RESEARCH_PDF):
        if not root.exists():
            continue
        for path in sorted(root.iterdir()):
            if path.is_file() and any(keyword in path.name.casefold() for keyword in keywords):
                paths.append(path)
    return [str(path.relative_to(ROOT)) for path in paths]


def build_current_source_frame(queue: dict[str, Any]) -> dict[str, Any]:
    manifests, _stems_by_document_id = load_manifests()
    candidate_counts = registry_document_counts("candidates")
    pending_counts = registry_document_counts("pending")
    rejected_counts = registry_document_counts("rejected")
    registry_documents = set(candidate_counts) | set(pending_counts) | set(rejected_counts)
    manifest_rows_by_doc: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in queue["manifestStatus"]:
        manifest_rows_by_doc[row["documentId"]].append(row)
    hardening_by_doc: Counter[str] = Counter(
        row["documentId"] for row in queue["needsSourceWindowHardening"]
    )

    included: list[dict[str, Any]] = []
    for document_id, wrapper in sorted(manifests.items()):
        manifest = wrapper["manifest"]
        rows = manifest_rows_by_doc.get(document_id, [])
        included.append(
            {
                "documentId": document_id,
                "classification": "included_manifest",
                "documentStem": wrapper["stem"],
                "documentTitle": manifest.get("documentTitle", document_id),
                "manifestPath": wrapper["path"],
                "sourcePath": manifest["sourcePath"],
                "manifestSections": len(manifest.get("sections") or []),
                "registryRecords": {
                    "candidates": candidate_counts.get(document_id, 0),
                    "pending": pending_counts.get(document_id, 0),
                    "rejected": rejected_counts.get(document_id, 0),
                },
                "manifestWindowState": {
                    "landed": sum(1 for row in rows if row["state"] == "landed"),
                    "pendingOnly": sum(1 for row in rows if row["state"] == "pending_only"),
                    "readyToIngest": sum(1 for row in rows if row["state"] == "ready_to_ingest"),
                },
                "highPriorityRowsNeedingSourceWindowHardening": hardening_by_doc.get(document_id, 0),
            }
        )

    for document_id in sorted(registry_documents - set(manifests)):
        included.append(
            {
                "documentId": document_id,
                "classification": "included_registry_only",
                "documentStem": None,
                "documentTitle": document_id,
                "manifestPath": None,
                "sourcePath": None,
                "manifestSections": 0,
                "registryRecords": {
                    "candidates": candidate_counts.get(document_id, 0),
                    "pending": pending_counts.get(document_id, 0),
                    "rejected": rejected_counts.get(document_id, 0),
                },
                "manifestWindowState": {
                    "landed": 0,
                    "pendingOnly": 0,
                    "readyToIngest": 0,
                },
                "highPriorityRowsNeedingSourceWindowHardening": hardening_by_doc.get(document_id, 0),
                "blocker": "registry_records_without_document_manifest",
            }
        )

    excluded_future = [
        {
            "sourceKey": "nolan",
            "classification": "excluded_future",
            "reason": "not in current document-manifest/registry control set",
            "evidencePaths": _source_paths_for_keywords(("nolan", "fundamentals")),
        },
        {
            "sourceKey": "eppls",
            "classification": "excluded_future",
            "reason": "not in current document-manifest/registry control set",
            "evidencePaths": _source_paths_for_keywords(("eppls",)),
        },
    ]
    return {
        "schemaName": "requirements_current_source_frame",
        "formatVersion": "2026-05-09-v1",
        "generatedAt": queue["generatedAt"],
        "controlRule": (
            "include all requirements-spike document manifests plus any live registry "
            "document not represented by those manifests; defer Nolan/EPPLS unless present "
            "in that control set"
        ),
        "includedSources": included,
        "excludedFutureSources": excluded_future,
        "blockedAmbiguousSources": [
            source for source in included if source["classification"] == "included_registry_only"
        ],
        "counts": {
            "includedSources": len(included),
            "excludedFutureSources": len(excluded_future),
            "blockedAmbiguousSources": sum(
                1 for source in included if source["classification"] == "included_registry_only"
            ),
        },
    }


def source_status_rows(queue: dict[str, Any], frame: dict[str, Any]) -> list[dict[str, Any]]:
    rows_by_doc: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in queue["manifestStatus"]:
        rows_by_doc[row["documentId"]].append(row)
    hardening_by_doc: Counter[str] = Counter(
        row["documentId"] for row in queue["needsSourceWindowHardening"]
    )
    status_rows: list[dict[str, Any]] = []
    for source in frame["includedSources"]:
        document_id = source["documentId"]
        rows = rows_by_doc.get(document_id, [])
        scope_notes = SOURCE_SCOPE_NOTES.get(document_id, [])
        landed = sum(1 for row in rows if row["state"] == "landed")
        pending_only = sum(1 for row in rows if row["state"] == "pending_only")
        ready = sum(1 for row in rows if row["state"] == "ready_to_ingest")
        hardening = hardening_by_doc.get(document_id, 0)
        if source["classification"] == "included_registry_only":
            state = "blocked_ambiguous"
            next_action = "add document manifest or remove unsupported registry-only source claim"
        elif ready:
            state = "needs_ingest"
            next_action = "run ready-to-ingest batch for remaining manifest windows"
        elif pending_only:
            state = "needs_curation"
            next_action = "curate pending-only manifest windows"
        elif hardening:
            state = "manifest_complete_with_hardening_backlog"
            if scope_notes:
                next_action = (
                    "carry scoped non-claims into package; harden remaining rows before "
                    "claiming full source completeness"
                )
            else:
                next_action = "decide whether hardening rows are current package blockers or scoped non-claims"
        else:
            state = "manifest_complete"
            next_action = "eligible for H01/source-specific review and closure review"
        status_rows.append(
            {
                "documentId": document_id,
                "documentTitle": source["documentTitle"],
                "classification": source["classification"],
                "sourceState": state,
                "manifestSections": len(rows),
                "landedManifestSections": landed,
                "pendingOnlyManifestSections": pending_only,
                "readyToIngestManifestSections": ready,
                "acceptedRecords": source["registryRecords"]["candidates"],
                "pendingRecords": source["registryRecords"]["pending"],
                "rejectedRecords": source["registryRecords"]["rejected"],
                "highPriorityRowsNeedingSourceWindowHardening": hardening,
                "scopeNotes": scope_notes,
                "nextAction": next_action,
            }
        )
    return status_rows


def build_source_progress_report(queue: dict[str, Any], frame: dict[str, Any]) -> dict[str, Any]:
    source_rows = source_status_rows(queue, frame)
    failed_windows: list[dict[str, Any]] = []
    return {
        "schemaName": "requirements_source_progress_report",
        "formatVersion": "2026-05-09-v1",
        "generatedAt": queue["generatedAt"],
        "levels": {
            "source": source_rows,
            "section": queue["manifestStatus"],
            "tactical": {
                "readyToIngest": queue["readyToIngest"],
                "failedWindowRetryCandidates": failed_windows,
                "pendingCuration": queue["pendingCuration"],
                "needsSourceWindowHardening": queue["needsSourceWindowHardening"],
                "excludedFutureSources": frame["excludedFutureSources"],
                "blockedAmbiguousSources": frame["blockedAmbiguousSources"],
                "sourceScopeNotes": [
                    {
                        "documentId": row["documentId"],
                        "scopeNotes": row["scopeNotes"],
                    }
                    for row in source_rows
                    if row["scopeNotes"]
                ],
                "failedWindowExpectation": {
                    "expectedFromPlan": "none",
                    "currentComputedCount": len(failed_windows),
                    "reconciliation": (
                        "No external failure ledger is wired into this queue; ready-to-ingest "
                        "windows are tracked separately and must not be counted as failed retries."
                    ),
                },
            },
        },
        "counts": {
            **queue["counts"],
            "includedSources": frame["counts"]["includedSources"],
            "excludedFutureSources": frame["counts"]["excludedFutureSources"],
            "failedWindowRetryCandidates": len(failed_windows),
        },
    }


def render_progress_report(report: dict[str, Any]) -> str:
    lines = [
        "# Source Progress Report",
        "",
        f"Generated: `{report['generatedAt']}`",
        "",
        "## Overall Counts",
        "",
        f"- Included current-frame sources: `{report['counts']['includedSources']}`",
        f"- Excluded future sources: `{report['counts']['excludedFutureSources']}`",
        f"- Manifest windows: `{report['counts']['manifestWindows']}`",
        f"- Landed manifest windows: `{report['counts']['landedManifestWindows']}`",
        f"- Ready-to-ingest manifest windows: `{report['counts']['readyToIngestManifestWindows']}`",
        f"- Pending curation records: `{report['counts']['pendingCurationRecords']}`",
        f"- Failed-window retry candidates: `{report['counts']['failedWindowRetryCandidates']}`",
        f"- High-priority rows needing source-window hardening: `{report['counts']['highPriorityRowsNeedingSourceWindowHardening']}`",
        "",
        "## Source Level",
        "",
        "| Source | State | Sections | Accepted | Pending | Rejected | Hardening | Next action |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | --- |",
    ]
    for row in report["levels"]["source"]:
        lines.append(
            "| {documentId} | {sourceState} | {landedManifestSections}/{manifestSections} | "
            "{acceptedRecords} | {pendingRecords} | {rejectedRecords} | "
            "{highPriorityRowsNeedingSourceWindowHardening} | {nextAction} |".format(**row)
        )
    scope_notes = report["levels"]["tactical"]["sourceScopeNotes"]
    if scope_notes:
        lines.extend(
            [
                "",
                "## Scope / Non-Claims",
                "",
            ]
        )
        for item in scope_notes:
            lines.append(f"### {item['documentId']}")
            lines.extend(f"- {note}" for note in item["scopeNotes"])
    lines.extend(
        [
            "",
            "## Section Level",
            "",
            "| Source | Section | State | Accepted | Pending | Rejected | Lines |",
            "| --- | --- | --- | ---: | ---: | ---: | --- |",
        ]
    )
    for row in report["levels"]["section"]:
        lines.append(
            "| {documentId} | {sectionId} | {state} | {acceptedRecords} | "
            "{pendingRecords} | {rejectedRecords} | {startLine}-{endLine} |".format(**row)
        )
    tactical = report["levels"]["tactical"]
    lines.extend(
        [
            "",
            "## Tactical Level",
            "",
            f"- Ready-to-ingest windows: `{len(tactical['readyToIngest'])}`",
            f"- Failed-window retry candidates: `{len(tactical['failedWindowRetryCandidates'])}`",
            f"- Pending curation sections: `{len(tactical['pendingCuration'])}`",
            f"- Source-window hardening rows: `{len(tactical['needsSourceWindowHardening'])}`",
            f"- Blocked ambiguous sources: `{len(tactical['blockedAmbiguousSources'])}`",
            "",
            "### Failed Window Reconciliation",
            "",
            f"- Expected from plan: `{tactical['failedWindowExpectation']['expectedFromPlan']}`",
            f"- Current computed count: `{tactical['failedWindowExpectation']['currentComputedCount']}`",
            f"- Reconciliation: {tactical['failedWindowExpectation']['reconciliation']}",
            "",
            "### Excluded Future Sources",
            "",
            "| Source | Evidence paths |",
            "| --- | --- |",
        ]
    )
    for row in tactical["excludedFutureSources"]:
        evidence = ", ".join(row["evidencePaths"]) if row["evidencePaths"] else "not present in repo artifacts"
        lines.append(f"| {row['sourceKey']} | {evidence} |")
    return "\n".join(lines) + "\n"


def write_csv(path: Path, rows: list[dict[str, Any]], fieldnames: list[str]) -> None:
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames, lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow({name: row.get(name, "") for name in fieldnames})


def render_summary(queue: dict[str, Any], output_dir: Path) -> str:
    batch_path = output_dir / "ready_to_ingest_batch.json"
    try:
        batch_arg = str(batch_path.relative_to(ROOT))
    except ValueError:
        batch_arg = str(batch_path)
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
            f"  --batch-manifest {batch_arg} \\",
            "  --dry-run\"",
            "```",
            "",
            "Run the exact manifest-only batch with:",
            "",
            "```bash",
            "export RUN_ROOT=\"$HOME/requirements-source-units-$(date +%Y-%m-%d)\"",
            "nix-shell -p python3 --run \"python3 research/tools/requirements-spike/ingest_section_batch.py \\",
            f"  --batch-manifest {batch_arg} \\",
            "  --output-root \\\"$RUN_ROOT\\\" \\",
            "  --heartbeat-seconds 60\"",
            "```",
            "",
            "Then promote and audit:",
            "",
            "```bash",
            "nix-shell -p python3 --run \"python3 research/tools/requirements-spike/promote_to_registry.py \\",
            "  --source-run-root \\\"$RUN_ROOT\\\"\"",
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
    if not output_dir.is_absolute():
        output_dir = ROOT / output_dir
    output_dir = output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    queue = build_queue()
    frame = build_current_source_frame(queue)
    progress_report = build_source_progress_report(queue, frame)

    (output_dir / "source_processing_queue.json").write_text(
        json.dumps(queue, indent=2) + "\n",
        encoding="utf-8",
    )
    (output_dir / "current_source_frame.json").write_text(
        json.dumps(frame, indent=2) + "\n",
        encoding="utf-8",
    )
    (output_dir / "source_progress_report.json").write_text(
        json.dumps(progress_report, indent=2) + "\n",
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
    (output_dir / "source_progress_report.md").write_text(
        render_progress_report(progress_report),
        encoding="utf-8",
    )
    print(
        json.dumps(
            {
                "outputDir": str(output_dir),
                "counts": progress_report["counts"],
                "currentSourceFrame": str((output_dir / "current_source_frame.json").relative_to(ROOT)),
                "sourceProgressReport": str((output_dir / "source_progress_report.md").relative_to(ROOT)),
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
