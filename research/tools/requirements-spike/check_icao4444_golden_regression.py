#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def excerpt(block: dict[str, Any]) -> str:
    return "\n".join(line for line in block["cleanTextLines"] if line).strip()


def summarize_block(block: dict[str, Any]) -> dict[str, Any]:
    return {
        "blockId": block["blockId"],
        "blockKind": block["blockKind"],
        "label": block["label"],
        "title": block["title"],
        "parentBlockId": block["parentBlockId"],
        "lineStart": block["lineStart"],
        "lineEnd": block["lineEnd"],
        "sectionPath": block["sectionPath"],
        "normalizedText": excerpt(block),
    }


def summarize_unit(unit: dict[str, Any]) -> dict[str, Any]:
    return {
        "sourceUnitId": unit["sourceUnitId"],
        "unitKind": unit["unitKind"],
        "parentSourceUnitId": unit["parentSourceUnitId"],
        "sourceRef": unit["sourceRef"],
        "sectionPath": unit["sectionPath"],
        "normalizedText": unit["normalizedText"],
    }


def actual_for_fixture(
    fixture: dict[str, Any],
    *,
    blocks: list[dict[str, Any]],
    units: list[dict[str, Any]],
    bundles: list[dict[str, Any]],
) -> dict[str, Any]:
    if fixture["fixtureKind"] == "bundle_anchor":
        label = fixture["selector"]["value"]
        block = next(item for item in blocks if item.get("label") == label)
        unit = next(item for item in units if item["blockId"] == block["blockId"])
        bundle = next(item for item in bundles if item["primarySourceUnitId"] == unit["sourceUnitId"])
        units_by_id = {item["sourceUnitId"]: item for item in units}
        return {
            "primaryBlock": summarize_block(block),
            "primaryUnit": summarize_unit(unit),
            "bundle": {
                "bundleId": bundle["bundleId"],
                "bundleKind": bundle["bundleKind"],
                "recommendation": bundle["recommendation"],
                "memberIds": bundle["memberIds"],
            },
            "memberUnits": [
                summarize_unit(units_by_id[member_id])
                for member_id in bundle["memberIds"]
            ],
        }
    if fixture["fixtureKind"] == "structure_window":
        start = fixture["selector"]["start"]
        end = fixture["selector"]["end"]
        selected_blocks = [
            summarize_block(block)
            for block in blocks
            if block["lineStart"] >= start and block["lineEnd"] <= end
        ]
        selected_block_ids = {block["blockId"] for block in selected_blocks}
        selected_units = [
            summarize_unit(unit)
            for unit in units
            if unit["blockId"] in selected_block_ids
        ]
        return {
            "blocks": selected_blocks,
            "units": selected_units,
        }
    raise ValueError(f"Unsupported fixture kind: {fixture['fixtureKind']}")


def build_golden_regression_report(run_dir: Path, fixture_dir: Path) -> dict[str, Any]:
    blocks = load_json(run_dir / "block_tree.json")
    units = load_json(run_dir / "source_units.json")
    bundles = load_json(run_dir / "bundle_candidates.json")
    run_manifest = load_json(run_dir / "run_manifest.json")
    manifest = load_json(fixture_dir / "fixture_manifest.json")

    failures = 0
    results: list[dict[str, Any]] = []
    if run_manifest["sourceSha256"] != manifest["sourceSha256"]:
        failures += 1
        results.append(
            {
                "fixtureId": "source_sha256",
                "fixtureKind": "run_metadata",
                "status": "fail",
            }
        )

    for fixture_path_str in manifest["fixtures"]:
        fixture = load_json(fixture_dir / fixture_path_str)
        actual = actual_for_fixture(fixture, blocks=blocks, units=units, bundles=bundles)
        status = "pass" if actual == fixture["expected"] else "fail"
        if status == "fail":
            failures += 1
        results.append(
            {
                "fixtureId": fixture["fixtureId"],
                "fixtureKind": fixture["fixtureKind"],
                "status": status,
            }
        )

    return {
        "document": manifest["document"],
        "sourceSha256": manifest["sourceSha256"],
        "fixtureCount": len(results),
        "failures": failures,
        "status": "pass" if failures == 0 else "fail",
        "results": results,
    }


def write_golden_regression_report(report_path: Path, report: dict[str, Any]) -> Path:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    return report_path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--fixture-dir", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    report = build_golden_regression_report(args.run_dir, args.fixture_dir)
    write_golden_regression_report(args.report, report)
    if report["status"] != "pass":
        raise SystemExit(1)


if __name__ == "__main__":
    main()
