#!/usr/bin/env python3
"""Build a small independent review sample for registry adequacy.

This is intentionally an 80/20 tool. It does not try to re-judge every
candidate or build a second registry. It produces a deterministic,
stratified review pack that lets a reviewer estimate whether the current
registry is a faithful enough translation of the ingested source corpus.
"""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
from collections import Counter, defaultdict
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_TOOL_ROOT = Path(__file__).resolve().parent
DEFAULT_REGISTRY_ROOT = DEFAULT_TOOL_ROOT / "registry" / "ollama_first"
DEFAULT_DOCUMENTS_DIR = DEFAULT_TOOL_ROOT / "documents"
DEFAULT_OUTPUT_ROOT = DEFAULT_TOOL_ROOT / "quality" / "adequacy"


def _utc_stamp() -> str:
    return datetime.now(UTC).strftime("%Y-%m-%dT%H-%M-%SZ")


def _read_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def _stable_digest(seed: str, key: str) -> str:
    return hashlib.sha256(f"{seed}|{key}".encode("utf-8")).hexdigest()


def _record_filename(path: Path) -> bool:
    parts = path.stem.split("::")
    return (
        path.suffix == ".json"
        and len(parts) == 3
        and len(parts[2]) == 16
        and all(c in "0123456789abcdef" for c in parts[2])
    )


def _source_window(source_path: Path, start_line: int, end_line: int) -> str:
    # Manifest line numbers are newline-based (`grep -n` / editor-style).
    # PDF extracts contain form-feed page breaks; `splitlines()` would treat
    # those as extra line breaks and drift every source window after page one.
    lines = source_path.read_text(encoding="utf-8").split("\n")
    start = max(start_line, 1)
    end = min(end_line, len(lines))
    return "\n".join(
        f"{line_no}: {lines[line_no - 1]}"
        for line_no in range(start, end + 1)
    )


def section_risk_tags(section: dict[str, Any]) -> list[str]:
    notes = (section.get("notes") or "").casefold()
    tags: set[str] = set()
    keywords = {
        "bilingual": ("bilingual", "german", "english half"),
        "dialogue_examples": ("dialogue", "rtf", "example"),
        "nested_conditions": ("nested", "sub-numbered", "list", "a/b", "a-f"),
        "table_or_figure": ("table", "figure", "layout artifact"),
        "parent_child": ("parent", "siblings", "as follows"),
        "local_or_best_practice": ("best-practice", "local", "advisory"),
    }
    for tag, needles in keywords.items():
        if any(needle in notes for needle in needles):
            tags.add(tag)
    return sorted(tags)


def record_risk_tags(record: dict[str, Any], bucket: str, section_tags: list[str]) -> list[str]:
    tags = set(section_tags)
    audit = record.get("audit") or {}
    gate = record.get("gate") or {}
    lifecycle = record.get("lifecycle") or {}
    curation = audit.get("curationApplied") or {}
    quote_audit = audit.get("verbatimQuoteCheck") or {}
    authority_audit = audit.get("authorityModalityCheck") or {}

    if bucket != "candidates" or lifecycle.get("state") != "accepted":
        tags.add("non_accepted")
    if record.get("testability") in {"sim_executable", "partially_executable"}:
        tags.add("executable_or_partial")
    if record.get("promotionHint") == "support_only" or record.get("authorityClass") == "background_support":
        tags.add("support_only")
    if curation:
        tags.add("curated")
    if curation.get("action") == "manual_split_accept":
        tags.add("manual_split")
    if curation.get("action") in {"reject", "keep_pending"}:
        tags.add(f"curation_{curation.get('action')}")
    if quote_audit.get("status") == "fail":
        tags.add("quote_audit_fail")
    if authority_audit.get("invariantViolated"):
        tags.add("authority_invariant_violation")
    if (gate.get("challenger") or {}).get("overridden"):
        tags.add("challenger_overridden")
    if (gate.get("judgeOverride") or {}).get("fired"):
        tags.add("judge_overridden")
    if (gate.get("judge") or {}).get("decision") not in {None, "accepted"}:
        tags.add("judge_demoted")
    return sorted(tags)


def load_sections(documents_dir: Path) -> list[dict[str, Any]]:
    sections: list[dict[str, Any]] = []
    for manifest_path in sorted(documents_dir.glob("*.json")):
        manifest = _read_json(manifest_path)
        for section in manifest.get("sections") or []:
            source_path = REPO_ROOT / manifest["sourcePath"]
            enriched = {
                "documentId": manifest["documentId"],
                "documentTitle": manifest.get("documentTitle", ""),
                "sourcePath": manifest["sourcePath"],
                "defaultAuthorityCeiling": manifest.get("defaultAuthorityCeiling", ""),
                "sectionId": section["sectionId"],
                "familyId": section.get("familyId", ""),
                "startLine": section["startLine"],
                "endLine": section["endLine"],
                "notes": section.get("notes", ""),
                "riskTags": section_risk_tags(section),
                "lineCount": max(0, section["endLine"] - section["startLine"] + 1),
                "sourceWindow": _source_window(source_path, section["startLine"], section["endLine"]),
            }
            sections.append(enriched)
    return sections


def load_records(registry_root: Path, sections_by_key: dict[tuple[str, str], dict[str, Any]]) -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for bucket in ("candidates", "pending", "rejected"):
        bucket_dir = registry_root / bucket
        if not bucket_dir.exists():
            continue
        for path in sorted(bucket_dir.rglob("*.json")):
            if not _record_filename(path):
                continue
            record = _read_json(path)
            key = (record.get("documentId", ""), record.get("sectionId", ""))
            section_tags = sections_by_key.get(key, {}).get("riskTags", [])
            records.append({
                "bucket": bucket,
                "path": str(path.relative_to(REPO_ROOT)),
                "record": record,
                "riskTags": record_risk_tags(record, bucket, section_tags),
            })
    return records


def _coverage_units_for_record(item: dict[str, Any]) -> set[str]:
    record = item["record"]
    return {
        f"bucket:{item['bucket']}",
        f"doc:{record.get('documentId', '')}",
        f"section:{record.get('documentId', '')}/{record.get('sectionId', '')}",
        f"authority:{record.get('authorityClass', '')}",
        f"modality:{record.get('modality', '')}",
        f"kind:{record.get('requirementKind', '')}",
        f"testability:{record.get('testability', '')}",
        *(f"risk:{tag}" for tag in item["riskTags"]),
    }


def _coverage_units_for_section(section: dict[str, Any]) -> set[str]:
    return {
        f"doc:{section['documentId']}",
        f"ceiling:{section.get('defaultAuthorityCeiling', '')}",
        *(f"risk:{tag}" for tag in section.get("riskTags", [])),
    }


def greedy_sample(
    items: list[dict[str, Any]],
    target: int,
    *,
    seed: str,
    key_fn,
    coverage_fn,
) -> list[dict[str, Any]]:
    remaining = list(items)
    selected: list[dict[str, Any]] = []
    covered: set[str] = set()
    while remaining and len(selected) < target:
        def score(item: dict[str, Any]) -> tuple[int, int, str]:
            units = coverage_fn(item)
            new_units = len(units - covered)
            risk_count = len(item.get("riskTags", []))
            return (new_units, risk_count, _stable_digest(seed, key_fn(item)))

        choice = max(remaining, key=score)
        selected.append(choice)
        covered |= coverage_fn(choice)
        remaining = [item for item in remaining if key_fn(item) != key_fn(choice)]
    return selected


def choose_record_sample(records: list[dict[str, Any]], target: int, seed: str) -> list[dict[str, Any]]:
    by_bucket: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for item in records:
        by_bucket[item["bucket"]].append(item)

    pending_quota = min(len(by_bucket["pending"]), max(1, target // 10)) if by_bucket["pending"] else 0
    rejected_quota = min(len(by_bucket["rejected"]), max(1, target // 4)) if by_bucket["rejected"] else 0
    accepted_quota = min(len(by_bucket["candidates"]), target - pending_quota - rejected_quota)
    quotas = {
        "pending": pending_quota,
        "rejected": rejected_quota,
        "candidates": accepted_quota,
    }
    selected: list[dict[str, Any]] = []
    for bucket, quota in quotas.items():
        selected.extend(greedy_sample(
            by_bucket[bucket],
            quota,
            seed=f"{seed}|records|{bucket}",
            key_fn=lambda item: item["record"].get("canonicalId", ""),
            coverage_fn=_coverage_units_for_record,
        ))

    if len(selected) < target:
        selected_ids = {item["record"].get("canonicalId", "") for item in selected}
        rest = [
            item for item in records
            if item["record"].get("canonicalId", "") not in selected_ids
        ]
        selected.extend(greedy_sample(
            rest,
            target - len(selected),
            seed=f"{seed}|records|fill",
            key_fn=lambda item: item["record"].get("canonicalId", ""),
            coverage_fn=_coverage_units_for_record,
        ))
    return sorted(selected, key=lambda item: item["record"].get("canonicalId", ""))


def choose_section_sample(
    sections: list[dict[str, Any]],
    target: int,
    seed: str,
    records_by_section: Counter[tuple[str, str]],
) -> list[dict[str, Any]]:
    enriched = [
        {
            **section,
            "recordCount": records_by_section[(section["documentId"], section["sectionId"])],
        }
        for section in sections
    ]
    return sorted(greedy_sample(
        enriched,
        min(target, len(enriched)),
        seed=f"{seed}|sections",
        key_fn=lambda section: f"{section['documentId']}::{section['sectionId']}",
        coverage_fn=lambda section: _coverage_units_for_section(section) | {
            "volume:high_records" if section.get("recordCount", 0) >= 10 else "volume:low_records",
        },
    ), key=lambda section: (section["documentId"], section["sectionId"]))


def _record_csv_row(sample_id: str, item: dict[str, Any]) -> dict[str, str]:
    record = item["record"]
    audit = record.get("audit") or {}
    lifecycle = record.get("lifecycle") or {}
    return {
        "sampleId": sample_id,
        "sampleKind": "record",
        "bucket": item["bucket"],
        "documentId": record.get("documentId", ""),
        "sectionId": record.get("sectionId", ""),
        "canonicalId": record.get("canonicalId", ""),
        "lifecycleState": lifecycle.get("state", ""),
        "authorityClass": record.get("authorityClass", ""),
        "modality": record.get("modality", ""),
        "requirementKind": record.get("requirementKind", ""),
        "testability": record.get("testability", ""),
        "verificationMode": record.get("verificationMode", ""),
        "riskTags": ";".join(item["riskTags"]),
        "claimText": record.get("claimText", ""),
        "exactSourceQuotes": "\n---\n".join(record.get("exactSourceQuotes") or []),
        "curationAction": (audit.get("curationApplied") or {}).get("action", ""),
        "reviewVerdict": "",
        "errorType": "",
        "severity": "",
        "reviewerNotes": "",
    }


def _section_csv_row(sample_id: str, section: dict[str, Any]) -> dict[str, str]:
    return {
        "sampleId": sample_id,
        "sampleKind": "section",
        "documentId": section["documentId"],
        "sectionId": section["sectionId"],
        "familyId": section.get("familyId", ""),
        "sourcePath": section["sourcePath"],
        "startLine": str(section["startLine"]),
        "endLine": str(section["endLine"]),
        "lineCount": str(section["lineCount"]),
        "recordCount": str(section.get("recordCount", 0)),
        "riskTags": ";".join(section.get("riskTags", [])),
        "notes": section.get("notes", ""),
        "omissionVerdict": "",
        "missingRequirementCount": "",
        "missingRequirementNotes": "",
        "reviewerNotes": "",
    }


def write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    if not rows:
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    headers = list(rows[0].keys())
    with path.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=headers)
        writer.writeheader()
        writer.writerows(rows)


def render_pack(
    *,
    record_sample: list[dict[str, Any]],
    section_sample: list[dict[str, Any]],
    output_dir: Path,
    seed: str,
) -> str:
    lines: list[str] = [
        "# Registry Adequacy Review Pack",
        "",
        f"- seed: `{seed}`",
        f"- record sample: `{len(record_sample)}`",
        f"- section omission sample: `{len(section_sample)}`",
        "",
        "## How To Review",
        "",
        "For each sampled record, compare `claimText` and `exactSourceQuotes` with the source. Use verdicts:",
        "",
        "- `correct`",
        "- `minor_metadata_issue`",
        "- `major_error`",
        "- `wrong_lifecycle`",
        "- `needs_split_or_bundle`",
        "- `cannot_assess`",
        "",
        "For each sampled section, scan the source window and the section's accepted/rejected record summary. Use omission verdicts:",
        "",
        "- `no_material_omission`",
        "- `possible_omission`",
        "- `material_omission`",
        "- `cannot_assess`",
        "",
        "Rule-of-three approximation: if 0 major record errors are found in 48 records, the 95% upper bound on major record-error rate is about 6.3% for the sampled frame. If 0 material omissions are found in 12 sections, the corresponding upper bound is about 25%; this is a smoke check for systemic omissions, not a proof of completeness.",
        "",
        "## Sampled Records",
        "",
    ]
    for index, item in enumerate(record_sample, 1):
        record = item["record"]
        lines.extend([
            f"### R{index:03d} `{record.get('canonicalId', '')}`",
            "",
            f"- bucket: `{item['bucket']}`",
            f"- document/section: `{record.get('documentId', '')}/{record.get('sectionId', '')}`",
            f"- authority/modality: `{record.get('authorityClass', '')}` / `{record.get('modality', '')}`",
            f"- kind/testability: `{record.get('requirementKind', '')}` / `{record.get('testability', '')}`",
            f"- risk tags: `{';'.join(item['riskTags'])}`",
            "",
            "**Claim**",
            "",
            record.get("claimText", ""),
            "",
            "**Exact Source Quotes**",
            "",
        ])
        for quote in record.get("exactSourceQuotes") or []:
            lines.extend(["```text", quote, "```", ""])

    lines.extend(["## Sampled Source Sections", ""])
    for index, section in enumerate(section_sample, 1):
        section_file = output_dir / "source_windows" / f"S{index:03d}_{section['documentId']}__{section['sectionId']}.txt"
        lines.extend([
            f"### S{index:03d} `{section['documentId']}/{section['sectionId']}`",
            "",
            f"- source: `{section['sourcePath']}:{section['startLine']}-{section['endLine']}`",
            f"- records in section: `{section.get('recordCount', 0)}`",
            f"- risk tags: `{';'.join(section.get('riskTags', []))}`",
            f"- source window file: `{section_file.relative_to(output_dir)}`",
            "",
            section.get("notes", ""),
            "",
        ])
    return "\n".join(lines) + "\n"


def build_review_pack(
    *,
    registry_root: Path,
    documents_dir: Path,
    output_dir: Path,
    record_sample_size: int,
    section_sample_size: int,
    seed: str,
) -> dict[str, Any]:
    sections = load_sections(documents_dir)
    sections_by_key = {
        (section["documentId"], section["sectionId"]): section
        for section in sections
    }
    records = load_records(registry_root, sections_by_key)
    records_by_section: Counter[tuple[str, str]] = Counter(
        (item["record"].get("documentId", ""), item["record"].get("sectionId", ""))
        for item in records
    )
    record_sample = choose_record_sample(records, record_sample_size, seed)
    section_sample = choose_section_sample(sections, section_sample_size, seed, records_by_section)

    output_dir.mkdir(parents=True, exist_ok=True)
    source_dir = output_dir / "source_windows"
    source_dir.mkdir(parents=True, exist_ok=True)
    for index, section in enumerate(section_sample, 1):
        source_file = source_dir / f"S{index:03d}_{section['documentId']}__{section['sectionId']}.txt"
        source_file.write_text(section["sourceWindow"] + "\n", encoding="utf-8")

    record_rows = [
        _record_csv_row(f"R{index:03d}", item)
        for index, item in enumerate(record_sample, 1)
    ]
    section_rows = [
        _section_csv_row(f"S{index:03d}", section)
        for index, section in enumerate(section_sample, 1)
    ]
    write_csv(output_dir / "record_review.csv", record_rows)
    write_csv(output_dir / "section_omission_review.csv", section_rows)
    (output_dir / "review_pack.md").write_text(
        render_pack(
            record_sample=record_sample,
            section_sample=section_sample,
            output_dir=output_dir,
            seed=seed,
        ),
        encoding="utf-8",
    )

    manifest = {
        "schemaName": "registry_adequacy_review_pack",
        "createdAt": datetime.now(UTC).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "seed": seed,
        "registryRoot": str(registry_root),
        "recordSampleSize": len(record_sample),
        "sectionSampleSize": len(section_sample),
        "population": {
            "records": len(records),
            "sections": len(sections),
            "recordsByBucket": dict(Counter(item["bucket"] for item in records)),
            "recordsByDocument": dict(Counter(item["record"].get("documentId", "") for item in records)),
        },
        "outputs": {
            "recordReviewCsv": "record_review.csv",
            "sectionOmissionReviewCsv": "section_omission_review.csv",
            "reviewPackMarkdown": "review_pack.md",
            "sourceWindowsDir": "source_windows/",
        },
        "sampledRecordIds": [item["record"].get("canonicalId", "") for item in record_sample],
        "sampledSections": [
            {"documentId": section["documentId"], "sectionId": section["sectionId"]}
            for section in section_sample
        ],
    }
    (output_dir / "sample_manifest.json").write_text(
        json.dumps(manifest, indent=2) + "\n",
        encoding="utf-8",
    )
    return manifest


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--registry-root", type=Path, default=DEFAULT_REGISTRY_ROOT)
    parser.add_argument("--documents-dir", type=Path, default=DEFAULT_DOCUMENTS_DIR)
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--record-sample-size", type=int, default=48)
    parser.add_argument("--section-sample-size", type=int, default=12)
    parser.add_argument("--seed", default="rr13-80-20-2026-04-29")
    args = parser.parse_args(argv)

    output_dir = args.output_dir or (DEFAULT_OUTPUT_ROOT / f"adequacy_{_utc_stamp()}")
    manifest = build_review_pack(
        registry_root=args.registry_root,
        documents_dir=args.documents_dir,
        output_dir=output_dir,
        record_sample_size=args.record_sample_size,
        section_sample_size=args.section_sample_size,
        seed=args.seed,
    )
    print(json.dumps({
        "outputDir": str(output_dir),
        "recordSampleSize": manifest["recordSampleSize"],
        "sectionSampleSize": manifest["sectionSampleSize"],
        "population": manifest["population"],
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
