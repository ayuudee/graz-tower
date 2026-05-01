#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

from icao4444_normalizer_lib import classify_layout

def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def check(status: str, check_id: str, severity: str, details: str) -> dict:
    return {
        "checkId": check_id,
        "severity": severity,
        "status": status,
        "details": details,
    }


def build_validation_report(run_dir: Path, *, unknown_structure_threshold: int = 0) -> dict:
    manifest = load_json(run_dir / "run_manifest.json")
    tape = load_json(run_dir / "document_tape.json")
    block_tree = load_json(run_dir / "block_tree.json")
    source_units = load_json(run_dir / "source_units.json")
    bundles = load_json(run_dir / "bundle_candidates.json")

    checks: list[dict] = []

    line_count = len(tape)
    max_line = max((line["lineNo"] for line in tape), default=0)
    checks.append(
        check(
            "pass" if line_count == max_line else "fail",
            "source_line_accounting",
            "hard",
            f"document tape lines={line_count}, max_line={max_line}",
        )
    )

    block_ids = [node["blockId"] for node in block_tree]
    checks.append(
        check(
            "pass" if len(block_ids) == len(set(block_ids)) else "fail",
            "unique_block_ids",
            "hard",
            f"block_count={len(block_ids)} unique={len(set(block_ids))}",
        )
    )

    source_unit_ids = [unit["sourceUnitId"] for unit in source_units]
    checks.append(
        check(
            "pass" if len(source_unit_ids) == len(set(source_unit_ids)) else "fail",
            "unique_source_unit_ids",
            "hard",
            f"source_units={len(source_unit_ids)} unique={len(set(source_unit_ids))}",
        )
    )

    block_kind_by_id = {node["blockId"]: node["blockKind"] for node in block_tree}
    children_by_parent: dict[str, list[dict]] = {}
    for node in block_tree:
        if node["parentBlockId"] is None:
            continue
        children_by_parent.setdefault(node["parentBlockId"], []).append(node)
    orphan_children = [
        node["blockId"]
        for node in block_tree
        if node["blockKind"] in {"list_item", "sub_list_item", "note_block"}
        and not node["parentBlockId"]
    ]
    checks.append(
        check(
            "pass" if not orphan_children else "fail",
            "orphan_structural_children",
            "hard",
            "no orphan list_item/sub_list_item/note_block nodes"
            if not orphan_children
            else f"orphans={orphan_children[:10]}",
        )
    )

    empty_units = [
        unit["sourceUnitId"]
        for unit in source_units
        if not unit["normalizedText"].strip()
        and not children_by_parent.get(unit["blockId"])
    ]
    checks.append(
        check(
            "pass" if not empty_units else "fail",
            "empty_source_units",
            "hard",
            "no empty normalized source units" if not empty_units else f"empty={empty_units[:10]}",
        )
    )

    page_furniture_leak = [
        unit["sourceUnitId"]
        for unit in source_units
        if any(classify_layout(line.strip()) == "page_furniture" for line in unit["sourceText"].splitlines() if line.strip())
    ]
    checks.append(
        check(
            "pass" if not page_furniture_leak else "fail",
            "page_furniture_leak",
            "hard",
            "no obvious page furniture inside source units"
            if not page_furniture_leak
            else f"leaks={page_furniture_leak[:10]}",
        )
    )

    unknown_structures = [node["blockId"] for node in block_tree if node["blockKind"] == "unknown_structure"]
    checks.append(
        check(
            "pass" if len(unknown_structures) <= unknown_structure_threshold else "fail",
            "unknown_structure_threshold",
            "hard",
            f"unknown_structure_count={len(unknown_structures)} threshold={unknown_structure_threshold}",
        )
    )

    bundle_primary_ids = [bundle["primarySourceUnitId"] for bundle in bundles]
    checks.append(
        check(
            "pass" if len(bundle_primary_ids) == len(set(bundle_primary_ids)) else "fail",
            "unique_bundle_primaries",
            "hard",
            f"bundles={len(bundle_primary_ids)} unique_primaries={len(set(bundle_primary_ids))}",
        )
    )

    failures = [item for item in checks if item["status"] == "fail" and item["severity"] == "hard"]
    warnings = [item for item in checks if item["status"] == "fail" and item["severity"] != "hard"]
    return {
        "document": manifest["sourceDocumentId"],
        "sourceSha256": manifest["sourceSha256"],
        "normalizerVersion": manifest["normalizerVersion"],
        "checks": checks,
        "failures": failures,
        "warnings": warnings,
        "unknownStructureCount": len(unknown_structures),
        "machineGate": "pass" if not failures else "fail",
        "stageCounts": manifest["stageCounts"],
    }


def write_validation_report(run_dir: Path, report: dict) -> Path:
    report_path = run_dir / "validation_report.json"
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report_path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--unknown-structure-threshold", type=int, default=0)
    args = parser.parse_args()

    write_validation_report(
        args.run_dir,
        build_validation_report(
            args.run_dir,
            unknown_structure_threshold=args.unknown_structure_threshold,
        ),
    )


if __name__ == "__main__":
    main()
