#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path


TRANCHE_EXIT_LABELS = ["4.5.7.5.1", "4.3.2.1.2", "4.3.2.1.3"]


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def render_summary(run_dir: Path) -> str:
    manifest = load_json(run_dir / "run_manifest.json")
    validation = load_json(run_dir / "validation_report.json")
    block_tree = load_json(run_dir / "block_tree.json")
    source_units = load_json(run_dir / "source_units.json")
    bundles = load_json(run_dir / "bundle_candidates.json")
    label_stub_count = sum(1 for node in block_tree if node["blockKind"] == "label_stub")

    tranche_blocks = [
        node for node in block_tree if node.get("label") in TRANCHE_EXIT_LABELS
    ]
    lines = [
        "# ICAO 4444 Normalization Summary",
        "",
        f"- source: `{manifest['sourcePath']}`",
        f"- source sha256: `{manifest['sourceSha256']}`",
        f"- generated at: `{manifest['generatedAt']}`",
        f"- machine gate: `{validation['machineGate']}`",
        f"- block nodes: `{len(block_tree)}`",
        f"- source units: `{len(source_units)}`",
        f"- bundle candidates: `{len(bundles)}`",
        f"- unknown-structure count: `{validation['unknownStructureCount']}`",
        f"- label-stub count: `{label_stub_count}`",
        "",
        "## Hard Checks",
        "",
    ]
    for item in validation["checks"]:
        lines.append(f"- `{item['checkId']}`: `{item['status']}` — {item['details']}")
    lines.extend(
        [
            "",
            "## Tranche Exit Bundle Anchors",
            "",
        ]
    )
    for node in tranche_blocks:
        lines.append(
            f"- `{node['label']}` [{node['blockKind']}] {node['lineStart']}-{node['lineEnd']}"
        )
    return "\n".join(lines) + "\n"


def write_summary(run_dir: Path, summary: str) -> Path:
    output = run_dir / "normalization_summary.md"
    output.write_text(summary, encoding="utf-8")
    return output


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args()

    write_summary(args.run_dir, render_summary(args.run_dir))


if __name__ == "__main__":
    main()
