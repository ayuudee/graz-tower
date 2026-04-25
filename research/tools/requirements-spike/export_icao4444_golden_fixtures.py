#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


FIXTURE_SPECS = [
    {
        "fixtureId": "tranche_exit_4_3_2_1_1",
        "fixtureKind": "bundle_anchor",
        "selector": {"type": "label", "value": "4.3.2.1.1"},
    },
    {
        "fixtureId": "tranche_exit_4_3_2_1_2",
        "fixtureKind": "bundle_anchor",
        "selector": {"type": "label", "value": "4.3.2.1.2"},
    },
    {
        "fixtureId": "tranche_exit_4_3_2_1_3",
        "fixtureKind": "bundle_anchor",
        "selector": {"type": "label", "value": "4.3.2.1.3"},
    },
    {
        "fixtureId": "tranche_exit_4_5_7_5_1",
        "fixtureKind": "bundle_anchor",
        "selector": {"type": "label", "value": "4.5.7.5.1"},
    },
    {
        "fixtureId": "tranche_exit_4_5_7_5_1_1",
        "fixtureKind": "bundle_anchor",
        "selector": {"type": "label", "value": "4.5.7.5.1.1"},
    },
    {
        "fixtureId": "tranche_exit_4_5_7_5_2",
        "fixtureKind": "bundle_anchor",
        "selector": {"type": "label", "value": "4.5.7.5.2"},
    },
    {
        "fixtureId": "tranche_exit_4_5_7_5_2_1",
        "fixtureKind": "bundle_anchor",
        "selector": {"type": "label", "value": "4.5.7.5.2.1"},
    },
    {
        "fixtureId": "appendix_var_form_stub_window",
        "fixtureKind": "structure_window",
        "selector": {"type": "line_window", "start": 19466, "end": 19474},
    },
    {
        "fixtureId": "appendix_route_examples_table_window",
        "fixtureKind": "structure_window",
        "selector": {"type": "line_window", "start": 20429, "end": 20435},
    },
    {
        "fixtureId": "appendix_supplementary_data_table_window",
        "fixtureKind": "structure_window",
        "selector": {"type": "line_window", "start": 21197, "end": 21209},
    },
]


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


def bundle_fixture(
    spec: dict[str, Any],
    *,
    manifest: dict[str, Any],
    blocks: list[dict[str, Any]],
    units: list[dict[str, Any]],
    bundles: list[dict[str, Any]],
) -> dict[str, Any]:
    label = spec["selector"]["value"]
    block = next(item for item in blocks if item.get("label") == label)
    unit = next(item for item in units if item["blockId"] == block["blockId"])
    bundle = next(item for item in bundles if item["primarySourceUnitId"] == unit["sourceUnitId"])
    units_by_id = {item["sourceUnitId"]: item for item in units}
    return {
        "fixtureId": spec["fixtureId"],
        "fixtureKind": spec["fixtureKind"],
        "selector": spec["selector"],
        "sourceDocumentId": manifest["sourceDocumentId"],
        "sourceSha256": manifest["sourceSha256"],
        "expected": {
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
        },
    }


def window_fixture(
    spec: dict[str, Any],
    *,
    manifest: dict[str, Any],
    blocks: list[dict[str, Any]],
    units: list[dict[str, Any]],
) -> dict[str, Any]:
    start = spec["selector"]["start"]
    end = spec["selector"]["end"]
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
        "fixtureId": spec["fixtureId"],
        "fixtureKind": spec["fixtureKind"],
        "selector": spec["selector"],
        "sourceDocumentId": manifest["sourceDocumentId"],
        "sourceSha256": manifest["sourceSha256"],
        "expected": {
            "blocks": selected_blocks,
            "units": selected_units,
        },
    }


def build_fixture(
    spec: dict[str, Any],
    *,
    manifest: dict[str, Any],
    blocks: list[dict[str, Any]],
    units: list[dict[str, Any]],
    bundles: list[dict[str, Any]],
) -> dict[str, Any]:
    if spec["fixtureKind"] == "bundle_anchor":
        return bundle_fixture(spec, manifest=manifest, blocks=blocks, units=units, bundles=bundles)
    if spec["fixtureKind"] == "structure_window":
        return window_fixture(spec, manifest=manifest, blocks=blocks, units=units)
    raise ValueError(f"Unsupported fixture kind: {spec['fixtureKind']}")


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    run_dir = args.run_dir
    output_dir = args.output_dir

    manifest = load_json(run_dir / "run_manifest.json")
    blocks = load_json(run_dir / "block_tree.json")
    units = load_json(run_dir / "source_units.json")
    bundles = load_json(run_dir / "bundle_candidates.json")

    fixtures = [
        build_fixture(spec, manifest=manifest, blocks=blocks, units=units, bundles=bundles)
        for spec in FIXTURE_SPECS
    ]
    fixture_files: list[str] = []
    for fixture in fixtures:
        fixture_path = output_dir / f"{fixture['fixtureId']}.json"
        write_json(fixture_path, fixture)
        fixture_files.append(fixture_path.name)

    write_json(
        output_dir / "fixture_manifest.json",
        {
            "document": manifest["sourceDocumentId"],
            "sourceSha256": manifest["sourceSha256"],
            "fixtureCount": len(fixtures),
            "fixtures": fixture_files,
        },
    )


if __name__ == "__main__":
    main()
