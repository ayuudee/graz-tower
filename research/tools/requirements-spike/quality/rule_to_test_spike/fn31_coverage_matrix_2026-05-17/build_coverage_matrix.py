#!/usr/bin/env python3
"""Build the FN31 cited rule-to-test coverage matrix.

The annotations are human-authored. This script verifies the selected source
units against the curated registry and emits stable JSON, CSV, and Markdown
artifacts for review.
"""

from __future__ import annotations

import csv
import json
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[6]
WORK_DIR = Path(__file__).resolve().parent
ANNOTATIONS_PATH = WORK_DIR / "coverage_annotations.json"
JSON_OUT = WORK_DIR / "coverage_matrix.json"
CSV_OUT = WORK_DIR / "coverage_matrix.csv"
MD_OUT = WORK_DIR / "coverage_matrix.md"

REQUIRED_RECORD_FIELDS = {
    "schemaName",
    "canonicalId",
    "documentId",
    "sectionId",
    "familyId",
    "claimText",
    "modality",
    "authorityClass",
    "requirementKind",
    "testability",
    "verificationMode",
    "exactSourceQuotes",
    "sourceItemIds",
    "provenance",
    "audit",
    "gate",
    "lifecycle",
}

REQUIRED_PROVENANCE_FIELDS = {"sourcePath", "sourceSha256", "startLine", "endLine"}
REQUIRED_ANNOTATION_FIELDS = {
    "canonicalId",
    "operationalFamily",
    "normalizedRuleClaim",
    "simulatorConcept",
    "codePaths",
    "existingTests",
    "coverageClass",
    "confidence",
    "recommendedForVerticalSlice",
    "notes",
}


def load_json(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return data


def registry_records(registry_root: Path) -> dict[str, dict[str, Any]]:
    records: dict[str, dict[str, Any]] = {}
    for path in sorted((registry_root / "candidates").glob("**/*.json")):
        data = load_json(path)
        if data.get("schemaName") != "ollama_first_candidate":
            continue
        canonical_id = data.get("canonicalId")
        if not isinstance(canonical_id, str) or not canonical_id:
            raise ValueError(f"{path} candidate missing canonicalId")
        if canonical_id in records:
            raise ValueError(f"Duplicate canonicalId in registry: {canonical_id}")
        records[canonical_id] = data | {"registryPath": str(path.relative_to(ROOT))}
    return records


def require_fields(name: str, data: dict[str, Any], fields: set[str]) -> None:
    missing = sorted(field for field in fields if field not in data)
    if missing:
        raise ValueError(f"{name} missing required fields: {', '.join(missing)}")


def validate_registry_record(record: dict[str, Any]) -> None:
    canonical_id = record["canonicalId"]
    require_fields(canonical_id, record, REQUIRED_RECORD_FIELDS)
    if record["lifecycle"].get("state") != "accepted":
        raise ValueError(f"{canonical_id} is not accepted")
    if record["gate"].get("overallStatus") != "pass":
        raise ValueError(f"{canonical_id} gate did not pass")
    quote_check = record["audit"].get("verbatimQuoteCheck", {})
    if quote_check.get("status") != "pass":
        raise ValueError(f"{canonical_id} quote check did not pass")
    if not record["exactSourceQuotes"]:
        raise ValueError(f"{canonical_id} has no exactSourceQuotes")
    if not record["sourceItemIds"]:
        raise ValueError(f"{canonical_id} has no sourceItemIds")
    require_fields(f"{canonical_id}.provenance", record["provenance"], REQUIRED_PROVENANCE_FIELDS)


def validate_annotation(annotation: dict[str, Any]) -> None:
    canonical_id = annotation.get("canonicalId", "<missing canonicalId>")
    require_fields(str(canonical_id), annotation, REQUIRED_ANNOTATION_FIELDS)
    if annotation["coverageClass"] not in {
        "executable_existing",
        "manual_translatable",
        "design_blocked",
        "review_only",
        "uncertain",
    }:
        raise ValueError(f"{canonical_id} has unknown coverageClass {annotation['coverageClass']!r}")
    if annotation["confidence"] not in {"high", "medium", "low"}:
        raise ValueError(f"{canonical_id} has unknown confidence {annotation['confidence']!r}")
    for list_field in ("codePaths", "existingTests"):
        if not isinstance(annotation[list_field], list):
            raise ValueError(f"{canonical_id}.{list_field} must be a list")


def matrix_row(annotation: dict[str, Any], record: dict[str, Any]) -> dict[str, Any]:
    provenance = record["provenance"]
    return {
        "canonicalId": record["canonicalId"],
        "documentId": record["documentId"],
        "sectionId": record["sectionId"],
        "sourceItemIds": record["sourceItemIds"],
        "lineRange": f"{provenance['startLine']}-{provenance['endLine']}",
        "sourcePath": provenance["sourcePath"],
        "claimText": record["claimText"],
        "normalizedRuleClaim": annotation["normalizedRuleClaim"],
        "operationalFamily": annotation["operationalFamily"],
        "requirementKind": record["requirementKind"],
        "modality": record["modality"],
        "authorityClass": record["authorityClass"],
        "registryTestability": record["testability"],
        "registryVerificationMode": record["verificationMode"],
        "coverageClass": annotation["coverageClass"],
        "confidence": annotation["confidence"],
        "simulatorConcept": annotation["simulatorConcept"],
        "codePaths": annotation["codePaths"],
        "existingTests": annotation["existingTests"],
        "recommendedForVerticalSlice": annotation["recommendedForVerticalSlice"],
        "exactSourceQuotes": record["exactSourceQuotes"],
        "registryPath": record["registryPath"],
        "notes": annotation["notes"],
    }


def write_csv(rows: list[dict[str, Any]]) -> None:
    fieldnames = [
        "canonicalId",
        "documentId",
        "sectionId",
        "sourceItemIds",
        "lineRange",
        "claimText",
        "normalizedRuleClaim",
        "operationalFamily",
        "coverageClass",
        "confidence",
        "simulatorConcept",
        "codePaths",
        "existingTests",
        "recommendedForVerticalSlice",
        "notes",
    ]
    with CSV_OUT.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in rows:
            writer.writerow(
                {
                    field: "; ".join(row[field]) if isinstance(row[field], list) else row[field]
                    for field in fieldnames
                }
            )


def write_markdown(payload: dict[str, Any]) -> None:
    rows = payload["records"]
    coverage_counts = payload["summary"]["coverageClassCounts"]
    lines = [
        "# FN31 Coverage Matrix",
        "",
        "## Selection",
        "",
        payload["selectionRationale"],
        "",
        "## Recommended Vertical Slice",
        "",
        f"Family: `{payload['recommendedVerticalSlice']['family']}`",
        "",
        payload["recommendedVerticalSlice"]["reason"],
        "",
        "## Coverage Counts",
        "",
    ]
    for key in sorted(coverage_counts):
        lines.append(f"- `{key}`: {coverage_counts[key]}")
    lines.extend(
        [
            "",
            "## Matrix",
            "",
            "| Source unit | Family | Coverage | Simulator concept | Existing tests | Notes |",
            "| --- | --- | --- | --- | --- | --- |",
        ]
    )
    for row in rows:
        tests = "<br>".join(f"`{test}`" for test in row["existingTests"]) if row["existingTests"] else "None identified"
        lines.append(
            "| "
            + " | ".join(
                [
                    f"`{row['canonicalId']}`",
                    row["operationalFamily"],
                    row["coverageClass"],
                    row["simulatorConcept"],
                    tests,
                    row["notes"],
                ]
            )
            + " |"
        )
    lines.extend(
        [
            "",
            "## Review Notes",
            "",
            "- FP / type safety: this task adds research artifacts only. No production rule execution shape is introduced.",
            "- Test architecture: coverage classes distinguish existing executable checks from manual translation, design-blocked claims, and review-only guidance.",
            "- Impact: simulator code remains decoupled from the registry; source units are evidence for planning only.",
            "- Operational correctness: every row retains canonical id, source item ids, exact quotes, and provenance line range in `coverage_matrix.json`.",
            "- Reversibility: artifacts are isolated under this FN31 quality directory.",
            "",
        ]
    )
    MD_OUT.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    annotations = load_json(ANNOTATIONS_PATH)
    registry_root = ROOT / annotations["registryRoot"]
    records_by_id = registry_records(registry_root)

    rows: list[dict[str, Any]] = []
    seen: set[str] = set()
    for annotation in annotations["records"]:
        validate_annotation(annotation)
        canonical_id = annotation["canonicalId"]
        if canonical_id in seen:
            raise ValueError(f"Duplicate annotation for {canonical_id}")
        seen.add(canonical_id)
        record = records_by_id.get(canonical_id)
        if record is None:
            raise ValueError(f"Selected canonicalId not found in accepted candidates: {canonical_id}")
        validate_registry_record(record)
        rows.append(matrix_row(annotation, record))

    coverage_counts: dict[str, int] = {}
    family_counts: dict[str, int] = {}
    for row in rows:
        coverage_counts[row["coverageClass"]] = coverage_counts.get(row["coverageClass"], 0) + 1
        family_counts[row["operationalFamily"]] = family_counts.get(row["operationalFamily"], 0) + 1

    payload = {
        "schema": "fn31_rule_to_test_coverage_matrix",
        "version": "2026-05-17-v1",
        "selectionRationale": annotations["selectionRationale"],
        "recommendedVerticalSlice": annotations["recommendedVerticalSlice"],
        "summary": {
            "totalRecords": len(rows),
            "coverageClassCounts": coverage_counts,
            "operationalFamilyCounts": family_counts,
        },
        "records": rows,
    }

    JSON_OUT.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write_csv(rows)
    write_markdown(payload)


if __name__ == "__main__":
    main()
