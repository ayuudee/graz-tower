#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path


TRANCHE_EXIT_LABELS = {"4.5.7.5.1", "4.3.2.1.2", "4.3.2.1.3"}
LABEL_STUB_SAMPLE_LIMIT = 40
NOTE_SAMPLE_LIMIT = 25
HEADING_SAMPLE_LIMIT = 25


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


def excerpt(block: dict) -> str:
    return "\n".join(line for line in block["cleanTextLines"] if line).strip()


def make_item(review_item_id: str, review_kind: str, block: dict, question: str) -> dict:
    return {
        "reviewItemId": review_item_id,
        "reviewKind": review_kind,
        "documentRef": "icao4444-extracted",
        "sourceSpan": f"{block['lineStart']}-{block['lineEnd']}",
        "rawExcerpt": "\n".join(block["rawTextLines"]).strip(),
        "normalizedExcerpt": excerpt(block),
        "structuralContext": {
            "blockId": block["blockId"],
            "blockKind": block["blockKind"],
            "label": block["label"],
            "title": block["title"],
            "sectionPath": block["sectionPath"],
            "parentBlockId": block["parentBlockId"],
        },
        "emittedArtifacts": {
            "blockId": block["blockId"],
        },
        "reviewQuestion": question,
    }


def deterministic_sample(blocks: list[dict], limit: int) -> list[dict]:
    if len(blocks) <= limit:
        return blocks
    step = max(1, len(blocks) // limit)
    sampled = blocks[::step][:limit]
    if sampled[-1] != blocks[-1]:
        sampled[-1] = blocks[-1]
    return sampled


def build_review_queue(run_dir: Path) -> dict:
    block_tree = load_json(run_dir / "block_tree.json")
    bundles = load_json(run_dir / "bundle_candidates.json")
    validation = load_json(run_dir / "validation_report.json")

    block_by_id = {block["blockId"]: block for block in block_tree}
    review_items: list[dict] = []

    label_stub_blocks = [block for block in block_tree if block["blockKind"] == "label_stub"]
    note_blocks = [block for block in block_tree if block["blockKind"] == "note_block"]
    heading_blocks = [block for block in block_tree if block["blockKind"] == "section_heading"]

    for block in block_tree:
        if block["blockKind"] == "unknown_structure":
            review_items.append(
                make_item(
                    f"review::{block['blockId']}",
                    "unknown_structure",
                    block,
                    "Should this structure remain unknown_structure, or is the parser missing a recognised form?",
                )
            )

    for block in deterministic_sample(label_stub_blocks, LABEL_STUB_SAMPLE_LIMIT):
        review_items.append(
            make_item(
                f"review::{block['blockId']}",
                "label_stub",
                block,
                "Is this label-only enumerator a legitimate structural stub, or is the parser still missing attached content?",
            )
        )

    for block in deterministic_sample(note_blocks, NOTE_SAMPLE_LIMIT):
        if block["blockKind"] == "note_block":
            review_items.append(
                make_item(
                    f"review::{block['blockId']}",
                    "note_attachment_sample",
                    block,
                    "Is this note attached to the correct parent clause or list item?",
                )
            )
    for block in deterministic_sample(heading_blocks, HEADING_SAMPLE_LIMIT):
        if block["blockKind"] == "section_heading":
            review_items.append(
                make_item(
                    f"review::{block['blockId']}",
                    "heading_transition_sample",
                    block,
                    "Did this heading preserve the correct section-path transition?",
                )
            )

    for bundle in bundles:
        primary_id = bundle["primarySourceUnitId"].replace("::unit", "")
        block = block_by_id.get(primary_id)
        if not block:
            continue
        if block.get("label") in TRANCHE_EXIT_LABELS:
            review_items.append(
                make_item(
                    f"review::{block['blockId']}",
                    "tranche_exit_bundle",
                    block,
                    "Does this bundle include all structurally dependent children needed for the tranche-exit proof?",
                )
            )

    deduped: dict[str, dict] = {item["reviewItemId"]: item for item in review_items}
    review_queue = {
        "machineGate": validation["machineGate"],
        "unknownStructureCount": validation["unknownStructureCount"],
        "labelStubCount": len(label_stub_blocks),
        "reviewItemCount": len(deduped),
        "items": list(deduped.values()),
    }
    return review_queue


def render_review_pack(review_queue: dict) -> str:
    lines = [
        "# ICAO 4444 Normalization Review Pack",
        "",
        f"- machine gate: `{review_queue['machineGate']}`",
        f"- unknown-structure count: `{review_queue['unknownStructureCount']}`",
        f"- label-stub count: `{review_queue['labelStubCount']}`",
        f"- review items: `{review_queue['reviewItemCount']}`",
        "",
    ]
    for item in review_queue["items"]:
        lines.extend(
            [
                f"## {item['reviewItemId']}",
                "",
                f"- kind: `{item['reviewKind']}`",
                f"- source span: `{item['sourceSpan']}`",
                f"- block kind: `{item['structuralContext']['blockKind']}`",
                f"- label: `{item['structuralContext']['label']}`",
                f"- question: {item['reviewQuestion']}",
                "",
                "```text",
                item["normalizedExcerpt"],
                "```",
                "",
            ]
        )
    return "\n".join(lines)


def write_review_outputs(run_dir: Path, review_queue: dict, review_pack: str) -> tuple[Path, Path]:
    queue_path = run_dir / "review_queue.json"
    pack_path = run_dir / "review_pack.md"
    queue_path.write_text(json.dumps(review_queue, indent=2), encoding="utf-8")
    pack_path.write_text(review_pack, encoding="utf-8")
    return queue_path, pack_path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    args = parser.parse_args()

    review_queue = build_review_queue(args.run_dir)
    write_review_outputs(args.run_dir, review_queue, render_review_pack(review_queue))


if __name__ == "__main__":
    main()
