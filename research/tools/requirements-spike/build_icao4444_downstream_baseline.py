#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from pathlib import Path
from typing import Any


DEFAULT_POLICY = (
    Path(__file__).resolve().parent / "downstream/icao4444_downstream_policy.json"
)


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def descendants_by_parent(blocks: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    children: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for block in blocks:
        parent = block["parentBlockId"]
        if parent is not None:
            children[parent].append(block)
    return children


def collect_subtree_block_ids(root_id: str, children: dict[str, list[dict[str, Any]]]) -> set[str]:
    pending = [root_id]
    seen: set[str] = set()
    while pending:
        current = pending.pop()
        if current in seen:
            continue
        seen.add(current)
        pending.extend(child["blockId"] for child in children.get(current, []))
    return seen


def build_downstream_baseline(run_dir: Path, policy_path: Path) -> dict[str, Any]:
    policy = load_json(policy_path)
    manifest = load_json(run_dir / "run_manifest.json")
    blocks = load_json(run_dir / "block_tree.json")
    units = load_json(run_dir / "source_units.json")
    bundles = load_json(run_dir / "bundle_candidates.json")

    blocks_by_id = {block["blockId"]: block for block in blocks}
    units_by_id = {unit["sourceUnitId"]: unit for unit in units}
    children = descendants_by_parent(blocks)

    eligible: list[dict[str, Any]] = []
    blocked: list[dict[str, Any]] = []

    disallowed = set(policy["disallowedDescendantBlockKinds"])
    explicit_allow = set(policy["explicitlyAllowedPrimaryLabels"])
    allowed_recommendations = set(policy["eligibleBundleRecommendations"])
    disallowed_bundle_kinds = set(policy.get("disallowedBundleKinds", []))

    for bundle in bundles:
        primary = units_by_id[bundle["primarySourceUnitId"]]
        primary_block = blocks_by_id[primary["blockId"]]
        subtree_ids = collect_subtree_block_ids(primary_block["blockId"], children)
        subtree_blocks = [blocks_by_id[block_id] for block_id in sorted(subtree_ids)]
        violating_blocks = [
            {
                "blockId": block["blockId"],
                "blockKind": block["blockKind"],
                "lineStart": block["lineStart"],
                "lineEnd": block["lineEnd"],
                "label": block["label"],
            }
            for block in subtree_blocks
            if block["blockKind"] in disallowed
        ]

        reasons: list[str] = []
        if bundle["bundleKind"] in disallowed_bundle_kinds:
            reasons.append(f"bundle_kind:{bundle['bundleKind']}")
        if bundle["recommendation"] not in allowed_recommendations:
            reasons.append(f"recommendation:{bundle['recommendation']}")
        if violating_blocks and primary_block.get("label") not in explicit_allow:
            reasons.append(
                "disallowed_descendants:"
                + ",".join(sorted({item["blockKind"] for item in violating_blocks}))
            )

        bundle_summary = {
            "bundleId": bundle["bundleId"],
            "primaryLabel": primary_block.get("label"),
            "sourceRef": primary["sourceRef"],
            "bundleKind": bundle["bundleKind"],
            "recommendation": bundle["recommendation"],
            "memberIds": bundle["memberIds"],
        }

        if reasons:
            blocked.append(
                {
                    **bundle_summary,
                    "reasons": reasons,
                    "violatingBlocks": violating_blocks,
                }
            )
        else:
            eligible.append(bundle_summary)

    return {
        "baselineId": f"icao4444-downstream-baseline:{manifest['sourceSha256'][:12]}",
        "sourceDocumentId": manifest["sourceDocumentId"],
        "sourceSha256": manifest["sourceSha256"],
        "policyId": policy["policyId"],
        "policyVersion": policy["policyVersion"],
        "eligibleBundleCount": len(eligible),
        "blockedBundleCount": len(blocked),
        "seedEligibleBundleCount": len(
            [bundle for bundle in eligible if bundle["primaryLabel"] in explicit_allow]
        ),
        "eligibleBundles": eligible,
        "seedEligibleBundles": [
            bundle for bundle in eligible if bundle["primaryLabel"] in explicit_allow
        ],
        "blockedBundles": blocked,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--policy", type=Path, default=DEFAULT_POLICY)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    payload = build_downstream_baseline(args.run_dir, args.policy)
    write_json(args.output, payload)


if __name__ == "__main__":
    main()
