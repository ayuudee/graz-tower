#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any

from prototype_slice import SLICE_SPECS, build_slice_payload


TARGET_SLICES = {"icao4444_readback", "icao4444_transfer"}


def clause_label(unit: dict[str, Any]) -> str | None:
    source_unit_id = unit["source_unit_id"]
    parts = source_unit_id.split("::")
    if len(parts) >= 3 and parts[1] == "clause":
        return parts[2]
    return None


def child_parent_label(unit: dict[str, Any]) -> str | None:
    source_unit_id = unit["source_unit_id"]
    parts = source_unit_id.split("::")
    if len(parts) >= 4 and parts[1] in {"list_item", "note"}:
        return parts[2]
    return None


def build_target_payloads() -> dict[str, dict[str, Any]]:
    payloads: dict[str, dict[str, Any]] = {}
    for spec in SLICE_SPECS:
        if spec.slice_id in TARGET_SLICES:
            payloads[spec.slice_id] = build_slice_payload(spec)
    return payloads


def units_by_parent(payloads: dict[str, dict[str, Any]]) -> dict[tuple[str, str], list[dict[str, Any]]]:
    grouped: dict[tuple[str, str], list[dict[str, Any]]] = defaultdict(list)
    for slice_id, payload in payloads.items():
        for unit in payload["units"]:
            parent = child_parent_label(unit)
            if parent is not None:
                grouped[(slice_id, parent)].append(unit)
    return grouped


def recommendation_for_clause(clause: dict[str, Any], children: list[dict[str, Any]]) -> str:
    if clause["text"].rstrip().endswith(":"):
        return "bundle_required"
    if any(child["unit_kind"] == "list_item" for child in children):
        return "bundle_required"
    if any(child["unit_kind"] == "background_explanation" for child in children):
        return "standalone_with_support"
    return "standalone_candidate"


def bundle_kind_for_children(children: list[dict[str, Any]]) -> str:
    kinds = {child["unit_kind"] for child in children}
    if "list_item" in kinds and "background_explanation" in kinds:
        return "clause_with_list_and_note"
    if "list_item" in kinds:
        return "clause_with_list"
    if "background_explanation" in kinds:
        return "clause_with_support_note"
    return "standalone_clause"


def member_role(unit: dict[str, Any], *, primary_clause_id: str) -> str:
    if unit["source_unit_id"] == primary_clause_id:
        return "primary_clause"
    if unit["unit_kind"] == "list_item":
        return "subordinate_item"
    if unit["unit_kind"] == "background_explanation":
        return "supporting_note"
    return "supporting_context"


def build_bundles(payloads: dict[str, dict[str, Any]]) -> dict[str, Any]:
    children = units_by_parent(payloads)
    bundles: list[dict[str, Any]] = []
    for slice_id, payload in sorted(payloads.items()):
        for unit in payload["units"]:
            label = clause_label(unit)
            if label is None:
                continue
            subordinate_units = sorted(
                children.get((slice_id, label), []),
                key=lambda child: child["source_ref"],
            )
            recommendation = recommendation_for_clause(unit, subordinate_units)
            if recommendation == "standalone_candidate" and not subordinate_units:
                continue
            bundle_id = f"{payload['document'].split('/')[-1].replace('.txt', '')}:{label}"
            bundles.append(
                {
                    "bundleId": bundle_id,
                    "sliceId": slice_id,
                    "primaryClauseId": unit["source_unit_id"],
                    "sectionPath": unit["section_path"],
                    "sourceRef": unit["source_ref"],
                    "bundleKind": bundle_kind_for_children(subordinate_units),
                    "recommendation": recommendation,
                    "members": [
                        {
                            "sourceUnitId": member["source_unit_id"],
                            "role": member_role(member, primary_clause_id=unit["source_unit_id"]),
                            "unitKind": member["unit_kind"],
                            "sectionPath": member["section_path"],
                            "sourceRef": member["source_ref"],
                            "text": member["text"],
                        }
                        for member in [unit, *subordinate_units]
                    ],
                }
            )
    return {
        "generator": "build_icao4444_bundles.py",
        "sliceIds": sorted(payloads.keys()),
        "bundleCount": len(bundles),
        "bundles": bundles,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    payloads = build_target_payloads()
    result = build_bundles(payloads)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
