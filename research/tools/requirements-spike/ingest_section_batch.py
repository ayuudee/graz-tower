#!/usr/bin/env python3
"""Run the Ollama-first pipeline over an explicit section batch.

This is the durable replacement for one-off repo-root runners. It accepts the
`ready_to_ingest_batch.json` produced by `build_source_processing_queue.py`,
processes only those exact manifest sections, and leaves a promotable
per-document tree under the chosen output root.
"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
import threading
import time
from collections import defaultdict
from pathlib import Path
from typing import Any


sys.path.insert(0, str(Path(__file__).resolve().parent))

from ingest_document import (  # noqa: E402
    DEFAULT_BASE_URL,
    aggregate_document,
    load_manifest,
    render_document_summary,
    section_to_case,
)
from run_icao4444_ollama_first_prototype import (  # noqa: E402
    build_arg_parser,
    run_pipeline,
)


def read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def strict_already_ingested(section_dir: Path) -> bool:
    run_manifest_path = section_dir / "run_manifest.json"
    judged_path = section_dir / "judged_candidates.json"
    if not run_manifest_path.exists() or not judged_path.exists():
        return False
    try:
        run_manifest = read_json(run_manifest_path)
    except json.JSONDecodeError:
        return False
    return (
        run_manifest.get("candidateCount") == run_manifest.get("judgedCandidateCount")
        and run_manifest.get("judgedCandidateCount", 0) > 0
    )


def load_batch_sections(path: Path) -> list[dict[str, Any]]:
    payload = read_json(path)
    sections = payload.get("sections")
    if not isinstance(sections, list):
        raise SystemExit(f"{path} does not contain a sections list")
    return sections


def validate_batch_sections(
    sections: list[dict[str, Any]],
) -> tuple[dict[str, set[str]], dict[str, str]]:
    selected_by_doc: dict[str, set[str]] = defaultdict(set)
    stem_by_doc: dict[str, str] = {}
    rows_by_key: dict[tuple[str, str], dict[str, Any]] = {}
    errors: list[str] = []

    required = {"documentId", "documentStem", "sectionId", "sourcePath", "startLine", "endLine"}
    for index, section in enumerate(sections):
        missing_keys = sorted(required - set(section))
        if missing_keys:
            errors.append(f"section row {index} missing keys: {missing_keys}")
            continue
        document_id = section["documentId"]
        section_id = section["sectionId"]
        key = (document_id, section_id)
        if key in rows_by_key:
            errors.append(f"duplicate section in batch: {document_id}/{section_id}")
            continue
        rows_by_key[key] = section
        selected_by_doc[document_id].add(section_id)
        previous_stem = stem_by_doc.get(document_id)
        if previous_stem and previous_stem != section["documentStem"]:
            errors.append(
                f"{document_id}: inconsistent documentStem values "
                f"{previous_stem!r} and {section['documentStem']!r}"
            )
        stem_by_doc[document_id] = section["documentStem"]

    for document_id in sorted(selected_by_doc):
        stem = stem_by_doc[document_id]
        manifest = load_manifest(stem)
        if manifest["documentId"] != document_id:
            errors.append(
                f"{document_id}: manifest stem {stem!r} has documentId {manifest['documentId']!r}"
            )
            continue
        by_section = {section["sectionId"]: section for section in manifest["sections"]}
        for section_id in sorted(selected_by_doc[document_id]):
            row = rows_by_key[(document_id, section_id)]
            manifest_section = by_section.get(section_id)
            if manifest_section is None:
                errors.append(f"{document_id}: section {section_id!r} is not in documents/{stem}.json")
                continue
            expected = {
                "sourcePath": manifest["sourcePath"],
                "startLine": manifest_section["startLine"],
                "endLine": manifest_section["endLine"],
            }
            actual = {
                "sourcePath": row["sourcePath"],
                "startLine": row["startLine"],
                "endLine": row["endLine"],
            }
            if actual != expected:
                errors.append(
                    f"{document_id}/{section_id}: batch source window {actual} "
                    f"does not match manifest {expected}"
                )

    if errors:
        raise SystemExit("batch manifest validation failed:\n- " + "\n- ".join(errors))
    return selected_by_doc, stem_by_doc


def render_batch_summary(summary: dict[str, Any]) -> str:
    lines = [
        "# Section Batch Ingestion Summary",
        "",
        f"- started: {summary['startedAt']}",
        f"- finished: {summary['finishedAt']}",
        f"- elapsed seconds: `{summary['elapsedSeconds']}`",
        f"- output root: `{summary['outputRoot']}`",
        f"- sections requested: `{summary['totals']['sectionsRequested']}`",
        f"- sections processed: `{summary['totals']['sectionsProcessed']}`",
        f"- sections skipped: `{summary['totals']['sectionsSkipped']}`",
        f"- sections failed: `{summary['totals']['sectionsFailed']}`",
        f"- candidates judged: `{summary['totals']['candidatesJudged']}`",
        f"- accepted: `{summary['totals']['accepted']}`",
        f"- advisory_only: `{summary['totals']['advisoryOnly']}`",
        f"- other: `{summary['totals']['other']}`",
        "",
        "## Documents",
        "",
    ]
    for document in summary["documents"]:
        totals = document["totals"]
        lines.append(
            f"- `{document['documentId']}`: sections "
            f"`{totals['ingested']}`/`{totals['sections']}`, "
            f"judged `{totals['candidatesJudged']}`, accepted `{totals['accepted']}`, "
            f"failed `{len(document.get('sectionsFailed', []))}`"
        )
    if summary["failures"]:
        lines.extend(["", "## Failures", ""])
        for failure in summary["failures"]:
            lines.append(
                f"- `{failure['documentId']}/{failure['sectionId']}`: "
                f"{failure['errorClass']} - {failure['error']}"
            )
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Run an explicit Ollama section batch.")
    parser.add_argument("--batch-manifest", type=Path, required=True)
    parser.add_argument("--output-root", type=Path)
    parser.add_argument("--dry-run", action="store_true", help="validate the batch without contacting Ollama")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--stop-on-failure", action="store_true")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--num-ctx", type=int, default=24576)
    parser.add_argument("--max-candidates", type=int, default=0, help="maximum candidates to judge per section; 0 judges all")
    parser.add_argument("--structure-attempts", type=int, default=3)
    parser.add_argument("--extraction-attempts", type=int, default=3)
    parser.add_argument("--json-repair-attempts", type=int, default=1)
    parser.add_argument("--heartbeat-seconds", type=int, default=30)
    args = parser.parse_args()

    sections = load_batch_sections(args.batch_manifest)
    if not sections:
        raise SystemExit("batch manifest contains no sections")

    selected_by_doc, stem_by_doc = validate_batch_sections(sections)

    if args.dry_run:
        print(
            json.dumps(
                {
                    "status": "dry_run_pass",
                    "batchManifest": str(args.batch_manifest),
                    "sectionsRequested": len(sections),
                    "documents": {
                        document_id: len(section_ids)
                        for document_id, section_ids in sorted(selected_by_doc.items())
                    },
                },
                indent=2,
            )
        )
        return 0

    if args.output_root is None:
        raise SystemExit("--output-root is required unless --dry-run is set")

    output_root: Path = args.output_root
    output_root.mkdir(parents=True, exist_ok=True)
    (output_root / "batch_manifest_used.json").write_text(
        json.dumps(read_json(args.batch_manifest), indent=2) + "\n",
        encoding="utf-8",
    )

    stop_heartbeat = threading.Event()

    def heartbeat() -> None:
        while not stop_heartbeat.wait(args.heartbeat_seconds):
            print("[heartbeat] section batch ingest still running", flush=True)

    threading.Thread(target=heartbeat, daemon=True).start()

    started = time.time()
    started_iso = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(started))
    print(
        f"[start] section batch: {len(sections)} sections -> {output_root}",
        flush=True,
    )

    inner_args = build_arg_parser().parse_args([])
    inner_args.base_url = args.base_url
    inner_args.output_dir = None
    inner_args.num_ctx = args.num_ctx
    inner_args.max_candidates = args.max_candidates
    inner_args.structure_attempts = args.structure_attempts
    inner_args.extraction_attempts = args.extraction_attempts
    inner_args.json_repair_attempts = args.json_repair_attempts

    documents_summary: list[dict[str, Any]] = []
    failures: list[dict[str, str]] = []
    sections_processed = 0
    sections_skipped = 0
    stop_requested = False

    try:
        for document_id in sorted(selected_by_doc):
            stem = stem_by_doc[document_id]
            manifest = load_manifest(stem)
            wanted = selected_by_doc[document_id]
            selected = [section for section in manifest["sections"] if section["sectionId"] in wanted]
            missing = sorted(wanted - {section["sectionId"] for section in selected})
            if missing:
                raise SystemExit(f"{document_id}: missing manifest sections: {missing}")

            partial_manifest = {**manifest, "sections": selected}
            document_dir = output_root / document_id
            document_dir.mkdir(parents=True, exist_ok=True)
            document_failures: list[dict[str, str]] = []
            print(f"[doc] {document_id}: {len(selected)} sections", flush=True)

            for section in selected:
                section_dir = document_dir / section["sectionId"]
                if not args.force and strict_already_ingested(section_dir):
                    print(f"[skip] {document_id}/{section['sectionId']} already ingested", flush=True)
                    sections_skipped += 1
                    continue
                if section_dir.exists():
                    shutil.rmtree(section_dir)
                print(
                    f"[run] {document_id}/{section['sectionId']} "
                    f"lines {section['startLine']}-{section['endLine']}",
                    flush=True,
                )
                try:
                    run_manifest = run_pipeline(section_to_case(manifest, section), inner_args, section_dir)
                    if run_manifest.get("candidateCount") != run_manifest.get("judgedCandidateCount"):
                        raise RuntimeError(
                            "candidate cap truncation: "
                            f"candidateCount={run_manifest.get('candidateCount')} "
                            f"judgedCandidateCount={run_manifest.get('judgedCandidateCount')}"
                        )
                    sections_processed += 1
                except Exception as exc:  # noqa: BLE001 - batch must record per-section failures
                    failure = {
                        "documentId": document_id,
                        "sectionId": section["sectionId"],
                        "errorClass": type(exc).__name__,
                        "error": str(exc) or type(exc).__name__,
                    }
                    print(
                        f"[fail] {document_id}/{section['sectionId']}: "
                        f"{failure['errorClass']}: {failure['error'][:300]}",
                        flush=True,
                    )
                    failures.append(failure)
                    document_failures.append(failure)
                    if args.stop_on_failure or "candidate cap truncation" in failure["error"]:
                        stop_requested = True
                        break
            if document_failures:
                (document_dir / "failures.json").write_text(
                    json.dumps(document_failures, indent=2) + "\n",
                    encoding="utf-8",
                )
            aggregate = aggregate_document(document_dir, partial_manifest)
            aggregate["sectionsFailed"] = [failure["sectionId"] for failure in document_failures]
            (document_dir / "accepted_candidates.json").write_text(
                json.dumps(aggregate, indent=2) + "\n",
                encoding="utf-8",
            )
            (document_dir / "summary.md").write_text(
                render_document_summary(aggregate),
                encoding="utf-8",
            )
            documents_summary.append(
                {
                    "documentId": document_id,
                    "documentDir": str(document_dir),
                    "sectionsFailed": aggregate["sectionsFailed"],
                    "totals": aggregate["totals"],
                }
            )
            print(f"[doc-done] {document_id}: {aggregate['totals']}", flush=True)
            if stop_requested:
                break
    finally:
        stop_heartbeat.set()

    finished = time.time()
    grand_totals = {
        "sectionsRequested": len(sections),
        "sectionsProcessed": sections_processed,
        "sectionsSkipped": sections_skipped,
        "sectionsFailed": len(failures),
        "candidatesJudged": sum(document["totals"]["candidatesJudged"] for document in documents_summary),
        "accepted": sum(document["totals"]["accepted"] for document in documents_summary),
        "advisoryOnly": sum(document["totals"]["advisoryOnly"] for document in documents_summary),
        "needsHumanReview": sum(document["totals"]["needsHumanReview"] for document in documents_summary),
        "other": sum(document["totals"]["other"] for document in documents_summary),
    }
    summary = {
        "startedAt": started_iso,
        "finishedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(finished)),
        "elapsedSeconds": int(finished - started),
        "outputRoot": str(output_root),
        "batchManifest": str(args.batch_manifest),
        "documents": documents_summary,
        "failures": failures,
        "totals": grand_totals,
    }
    (output_root / "batch_run_summary.json").write_text(
        json.dumps(summary, indent=2) + "\n",
        encoding="utf-8",
    )
    (output_root / "batch_run_summary.md").write_text(
        render_batch_summary(summary),
        encoding="utf-8",
    )
    print(json.dumps(summary, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
