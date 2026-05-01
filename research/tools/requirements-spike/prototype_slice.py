#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parents[3]


@dataclass(frozen=True)
class SliceSpec:
    slice_id: str
    path: Path
    start_line: int
    end_line: int
    method: str
    notes: str


@dataclass(frozen=True)
class SourceUnit:
    source_unit_id: str
    slice_id: str
    document_id: str
    method: str
    unit_kind: str
    section_path: list[str]
    source_ref: str
    text: str


SLICE_SPECS = [
    SliceSpec(
        slice_id="icao4444_readback",
        path=ROOT / "research/txt/icao4444-extracted.txt",
        start_line=3403,
        end_line=3427,
        method="numeric_clause_with_lists",
        notes="Normative clause family with subordinate list items and note.",
    ),
    SliceSpec(
        slice_id="icao4444_transfer",
        path=ROOT / "research/txt/icao4444-extracted.txt",
        start_line=3104,
        end_line=3130,
        method="numeric_clause_with_lists",
        notes="Normative transfer clause with timing guidance and note.",
    ),
    SliceSpec(
        slice_id="sera_responsibilities_control",
        path=ROOT / "research/txt/sera-923-2012-extracted.txt",
        start_line=1196,
        end_line=1228,
        method="sera_clause",
        notes="Control sample for a clean regulation structure outside the main radio slice.",
    ),
    SliceSpec(
        slice_id="cap413_frequency_and_readback",
        path=ROOT / "research/txt/cap413-extracted.txt",
        start_line=6300,
        end_line=6505,
        method="paragraphs_with_examples",
        notes="Manual paragraphs with embedded example dialogues and list blocks.",
    ),
    SliceSpec(
        slice_id="h01_readback",
        path=ROOT / "research/txt/h01-extracted.txt",
        start_line=4200,
        end_line=4315,
        method="bilingual_lettered_pairs",
        notes="Bilingual readback obligations with repeated letter labels and support notes.",
    ),
    SliceSpec(
        slice_id="h01_transfer",
        path=ROOT / "research/txt/h01-extracted.txt",
        start_line=4660,
        end_line=4732,
        method="bilingual_lettered_pairs",
        notes="Bilingual transfer/initial-contact slice with example dialogue.",
    ),
    SliceSpec(
        slice_id="icao9432_transfer_and_readback",
        path=ROOT / "research/txt/icao9432-extracted.txt",
        start_line=3830,
        end_line=4035,
        method="manual_numbered_with_examples",
        notes="Manual paragraphs with uppercase phraseology examples.",
    ),
    SliceSpec(
        slice_id="egast_vfr_readback",
        path=ROOT / "research/txt/egast-vfr-extracted.txt",
        start_line=539,
        end_line=629,
        method="advisory_bullets_and_paragraphs",
        notes="Advisory prose with bullets, guidance paragraphs, and conditional-clearance advice.",
    ),
]


NUMERIC_CLAUSE_RE = re.compile(r"^(?P<label>\d+(?:\.\d+)+)\s+(?P<body>.+)$")
SERA_CLAUSE_RE = re.compile(r"^(?P<label>SERA\.\d+)\s+(?P<body>.+)$")
LETTERED_ITEM_RE = re.compile(r"^(?P<label>[a-z]\))\s*(?P<body>.*)$")
H01_ENGLISH_HINT_RE = re.compile(r"\b(When|The|Other|Voice|It is|If both|An aircraft|The flight crew)\b")
CAP413_PARAGRAPH_RE = re.compile(r"^\d+\.\d+$")
ICAO9432_SECTION_RE = re.compile(r"^(?P<label>\d+\.\d+\.\d+)\s*(?P<body>.*)$")
PAGE_NOISE_RE = re.compile(
    r"^(Page \d+|CAP 413|Chapter \d+[.:].*|Manual of Radiotelephony|Podręcznik radiotelefonicznej frazeologii lotniczej|May \d{4}|\d{2}/\d{2}/\d{2}|2-\d+|4-\d+)$"
)
SEPARATOR_RE = re.compile(r"^[\-\u2014\u2015\u2500\u2501\uf0be]+$")
CALLSIGN_EXAMPLE_RE = re.compile(r"^(?:[GALB]:|[A-Z0-9-]{2,}\b)")


def read_slice_lines(spec: SliceSpec) -> list[tuple[int, str]]:
    # `splitlines()` treats form-feed page breaks as line boundaries, which drifts
    # away from editor and `nl -ba` line numbers for these extracted documents.
    lines = spec.path.read_text(encoding="utf-8").split("\n")
    indexed = []
    for line_no in range(spec.start_line, spec.end_line + 1):
        if 1 <= line_no <= len(lines):
            indexed.append((line_no, lines[line_no - 1].rstrip()))
    return indexed


def normalized_text(lines: list[str]) -> str:
    return "\n".join(line for line in lines if line.strip()).strip()


def clean_layout_text(line: str) -> str:
    return line.replace("\x0c", "").replace("\x07", "").strip()


def is_layout_noise(line: str) -> bool:
    cleaned = clean_layout_text(line)
    return (
        not cleaned
        or bool(PAGE_NOISE_RE.match(cleaned))
        or bool(SEPARATOR_RE.match(cleaned))
    )


def extract_numeric_clause_with_lists(spec: SliceSpec, indexed: list[tuple[int, str]]) -> list[SourceUnit]:
    heading_path: list[str] = []
    units: list[SourceUnit] = []
    active_clause: tuple[str, int, list[str]] | None = None
    active_list: tuple[str, int, list[str]] | None = None
    active_note: tuple[int, list[str]] | None = None

    def flush_clause() -> None:
        nonlocal active_clause
        if active_clause is None:
            return
        label, start_line, body_lines = active_clause
        units.append(
            SourceUnit(
                source_unit_id=f"{spec.slice_id}::clause::{label}",
                slice_id=spec.slice_id,
                document_id=spec.path.stem,
                method=spec.method,
                unit_kind="normative_clause",
                section_path=heading_path + [label],
                source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                text=normalized_text(body_lines),
            )
        )
        active_clause = None

    def flush_list() -> None:
        nonlocal active_list
        if active_list is None:
            return
        label, start_line, body_lines = active_list
        parent = active_clause[0] if active_clause else "root"
        units.append(
            SourceUnit(
                source_unit_id=f"{spec.slice_id}::list_item::{parent}::{label}",
                slice_id=spec.slice_id,
                document_id=spec.path.stem,
                method=spec.method,
                unit_kind="list_item",
                section_path=heading_path + ([parent] if parent != "root" else []) + [label],
                source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                text=normalized_text(body_lines),
            )
        )
        active_list = None

    def flush_note() -> None:
        nonlocal active_note
        if active_note is None:
            return
        start_line, body_lines = active_note
        parent = active_clause[0] if active_clause else "root"
        units.append(
            SourceUnit(
                source_unit_id=f"{spec.slice_id}::note::{parent}::{start_line}",
                slice_id=spec.slice_id,
                document_id=spec.path.stem,
                method=spec.method,
                unit_kind="background_explanation",
                section_path=heading_path + ([parent] if parent != "root" else []) + ["note"],
                source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                text=normalized_text(body_lines),
            )
        )
        active_note = None

    for line_no, raw in indexed:
        line = clean_layout_text(raw)
        if not line:
            continue
        if is_layout_noise(line):
            continue
        if line.isupper() and line == "READBACK OF CLEARANCES":
            heading_path = [line]
            continue
        if line.startswith("Note.—"):
            flush_list()
            flush_note()
            active_note = (line_no, [line])
            continue
        if active_note is not None:
            if NUMERIC_CLAUSE_RE.match(line) or LETTERED_ITEM_RE.match(line):
                flush_note()
            else:
                active_note[1].append(line)
                continue
        numeric = NUMERIC_CLAUSE_RE.match(line)
        if numeric:
            flush_list()
            flush_clause()
            active_clause = (numeric.group("label"), line_no, [numeric.group("body")])
            continue
        lettered = LETTERED_ITEM_RE.match(line)
        if lettered:
            flush_list()
            active_list = (lettered.group("label"), line_no, [lettered.group("body")])
            continue
        if active_list is not None:
            active_list[2].append(line)
            continue
        if active_clause is not None:
            active_clause[2].append(line)

    flush_note()
    flush_list()
    flush_clause()
    return units


def extract_sera_clause(spec: SliceSpec, indexed: list[tuple[int, str]]) -> list[SourceUnit]:
    section_path: list[str] = []
    units: list[SourceUnit] = []
    active_clause: tuple[str, int, list[str]] | None = None
    active_list: tuple[str, int, list[str]] | None = None

    def flush_list() -> None:
        nonlocal active_list
        if active_list is None:
            return
        label, start_line, body_lines = active_list
        parent = active_clause[0] if active_clause else "root"
        units.append(
            SourceUnit(
                source_unit_id=f"{spec.slice_id}::list_item::{parent}::{label}",
                slice_id=spec.slice_id,
                document_id=spec.path.stem,
                method=spec.method,
                unit_kind="list_item",
                section_path=section_path + ([parent] if parent != "root" else []) + [label],
                source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                text=normalized_text(body_lines),
            )
        )
        active_list = None

    def flush_clause() -> None:
        nonlocal active_clause
        if active_clause is None:
            return
        label, start_line, body_lines = active_clause
        units.append(
            SourceUnit(
                source_unit_id=f"{spec.slice_id}::clause::{label}",
                slice_id=spec.slice_id,
                document_id=spec.path.stem,
                method=spec.method,
                unit_kind="normative_clause",
                section_path=section_path + [label],
                source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                text=normalized_text(body_lines),
            )
        )
        active_clause = None

    for line_no, raw in indexed:
        line = clean_layout_text(raw)
        if not line:
            continue
        if line.startswith("SECTION "):
            flush_list()
            flush_clause()
            section_path = [line]
            continue
        if line.startswith("CHAPTER "):
            flush_list()
            flush_clause()
            section_path = section_path[:1] + [line]
            continue
        sera = SERA_CLAUSE_RE.match(line)
        if sera:
            flush_list()
            flush_clause()
            active_clause = (sera.group("label"), line_no, [sera.group("body")])
            continue
        lettered = re.match(r"^\((?P<label>[a-z])\)\s*(?P<body>.*)$", line)
        if lettered:
            flush_list()
            active_list = (lettered.group("label"), line_no, [lettered.group("body")])
            continue
        if active_list is not None:
            active_list[2].append(line)
            continue
        if active_clause is not None:
            active_clause[2].append(line)

    flush_list()
    flush_clause()
    return units


def looks_like_example_line(line: str) -> bool:
    stripped = clean_layout_text(line)
    return (
        stripped.startswith("---------------")
        or stripped.startswith("…")
        or stripped.startswith("...")
        or bool(re.match(r"^[GALB]:", stripped))
        or (
            bool(CALLSIGN_EXAMPLE_RE.match(stripped))
            and stripped.upper() == stripped
            and not PAGE_NOISE_RE.match(stripped)
        )
    )


def extract_paragraphs_with_examples(spec: SliceSpec, indexed: list[tuple[int, str]]) -> list[SourceUnit]:
    heading_path: list[str] = []
    units: list[SourceUnit] = []
    active_paragraph: tuple[str, int, list[str]] | None = None
    active_example: tuple[int, list[str]] | None = None

    def flush_example() -> None:
        nonlocal active_example
        if active_example is None:
            return
        start_line, body_lines = active_example
        parent = active_paragraph[0] if active_paragraph else "context"
        units.append(
            SourceUnit(
                source_unit_id=f"{spec.slice_id}::dialogue::{parent}::{start_line}",
                slice_id=spec.slice_id,
                document_id=spec.path.stem,
                method=spec.method,
                unit_kind="dialogue_example",
                section_path=heading_path + [parent, "dialogue"],
                source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                text=normalized_text(body_lines),
            )
        )
        active_example = None

    def flush_paragraph() -> None:
        nonlocal active_paragraph
        if active_paragraph is None:
            return
        label, start_line, body_lines = active_paragraph
        text = normalized_text(body_lines)
        if not text:
            active_paragraph = None
            return
        units.append(
            SourceUnit(
                source_unit_id=f"{spec.slice_id}::paragraph::{label}",
                slice_id=spec.slice_id,
                document_id=spec.path.stem,
                method=spec.method,
                unit_kind="operational_guidance",
                section_path=heading_path + [label],
                source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                text=text,
            )
        )
        active_paragraph = None

    for line_no, raw in indexed:
        line = raw.rstrip()
        stripped = clean_layout_text(line)
        if not stripped:
            continue
        if is_layout_noise(stripped):
            continue
        if stripped.startswith("Chapter ") or stripped == "Clearance Issue and Read-back Requirements":
            heading_path = [stripped]
            continue
        if CAP413_PARAGRAPH_RE.match(stripped):
            flush_example()
            flush_paragraph()
            active_paragraph = (stripped, line_no, [])
            continue
        if active_paragraph is not None and looks_like_example_line(stripped):
            if active_example is None:
                active_example = (line_no, [stripped.lstrip("\x07")])
            else:
                active_example[1].append(stripped.lstrip("\x07"))
            continue
        if active_example is not None and not looks_like_example_line(stripped):
            flush_example()
        if active_paragraph is not None:
            active_paragraph[2].append(stripped)

    flush_example()
    flush_paragraph()
    return units


def extract_bilingual_lettered_pairs(spec: SliceSpec, indexed: list[tuple[int, str]]) -> list[SourceUnit]:
    units: list[SourceUnit] = []
    grouped: dict[str, list[tuple[int, list[str]]]] = {}
    current_label: str | None = None
    current_lines: list[str] = []
    current_start: int | None = None

    def flush_current() -> None:
        nonlocal current_label, current_lines, current_start
        if current_label is None or current_start is None:
            return
        grouped.setdefault(current_label, []).append((current_start, current_lines[:]))
        current_label = None
        current_lines = []
        current_start = None

    for line_no, raw in indexed:
        line = clean_layout_text(raw)
        if not line:
            continue
        if is_layout_noise(line):
            continue
        lettered = LETTERED_ITEM_RE.match(line)
        if lettered:
            flush_current()
            current_label = lettered.group("label")
            current_start = line_no
            body = lettered.group("body")
            current_lines = [body] if body else []
            continue
        if current_label is not None:
            current_lines.append(line)

    flush_current()

    for label, entries in grouped.items():
        if not entries:
            continue
        english_entry = next(
            ((start, body) for start, body in entries if H01_ENGLISH_HINT_RE.search(normalized_text(body))),
            entries[-1],
        )
        start_line, body_lines = english_entry
        guidance_lines: list[str] = []
        example_lines: list[str] = []
        in_example = False
        for body_line in body_lines:
            cleaned = clean_layout_text(body_line)
            if not cleaned:
                continue
            if cleaned.lower() in {"after frequency change:", "nach dem frequenzwechsel:"}:
                in_example = True
                continue
            if re.match(r"^[GALB]:", cleaned):
                in_example = True
            if in_example:
                example_lines.append(cleaned)
            else:
                guidance_lines.append(cleaned)
        if guidance_lines:
            units.append(
                SourceUnit(
                    source_unit_id=f"{spec.slice_id}::lettered::{label.rstrip(')')}",
                    slice_id=spec.slice_id,
                    document_id=spec.path.stem,
                    method=spec.method,
                    unit_kind="operational_guidance",
                    section_path=[label],
                    source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                    text=normalized_text(guidance_lines),
                )
            )
        if example_lines:
            units.append(
                SourceUnit(
                    source_unit_id=f"{spec.slice_id}::dialogue::{label.rstrip(')')}::{start_line}",
                    slice_id=spec.slice_id,
                    document_id=spec.path.stem,
                    method=spec.method,
                    unit_kind="dialogue_example",
                    section_path=[label, "dialogue"],
                    source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                    text=normalized_text(example_lines),
                )
            )
    return units


def extract_manual_numbered_with_examples(spec: SliceSpec, indexed: list[tuple[int, str]]) -> list[SourceUnit]:
    section_path: list[str] = []
    units: list[SourceUnit] = []
    active_section: tuple[str, int, list[str]] | None = None
    active_example: tuple[int, list[str]] | None = None

    def flush_example(parent_label: str | None) -> None:
        nonlocal active_example
        if active_example is None:
            return
        start_line, body_lines = active_example
        parent = parent_label or "context"
        units.append(
            SourceUnit(
                source_unit_id=f"{spec.slice_id}::dialogue::{parent}::{start_line}",
                slice_id=spec.slice_id,
                document_id=spec.path.stem,
                method=spec.method,
                unit_kind="dialogue_example",
                section_path=section_path + [parent, "dialogue"],
                source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                text=normalized_text(body_lines),
            )
        )
        active_example = None

    def flush_section() -> None:
        nonlocal active_section
        if active_section is None:
            return
        label, start_line, body_lines = active_section
        text = normalized_text(body_lines)
        if not text:
            active_section = None
            return
        units.append(
            SourceUnit(
                source_unit_id=f"{spec.slice_id}::clause::{label}",
                slice_id=spec.slice_id,
                document_id=spec.path.stem,
                method=spec.method,
                unit_kind="operational_guidance",
                section_path=section_path + [label],
                source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                text=text,
            )
        )
        active_section = None

    for line_no, raw in indexed:
        line = clean_layout_text(raw)
        if not line:
            continue
        if is_layout_noise(line):
            continue
        if line.startswith("2.8.2 Transfer of communications") or line.startswith("2.8.3 Issue of clearance"):
            flush_example(active_section[0] if active_section else None)
            flush_section()
            section_path = [line]
            continue
        numeric = ICAO9432_SECTION_RE.match(line)
        if numeric:
            flush_example(active_section[0] if active_section else None)
            flush_section()
            body = [numeric.group("body")] if numeric.group("body") else []
            active_section = (numeric.group("label"), line_no, body)
            continue
        if active_section is not None and (line.isupper() or line.startswith("---------------")):
            if active_example is None:
                active_example = (line_no, [line])
            else:
                active_example[1].append(line)
            continue
        if active_example is not None and not (line.isupper() or line.startswith("---------------")):
            flush_example(active_section[0] if active_section else None)
        if active_section is not None:
            active_section[2].append(line)

    flush_example(active_section[0] if active_section else None)
    flush_section()
    return units


def extract_advisory_bullets_and_paragraphs(
    spec: SliceSpec, indexed: list[tuple[int, str]]
) -> list[SourceUnit]:
    units: list[SourceUnit] = []
    active_block: tuple[str, int, list[str]] | None = None
    active_bullet: tuple[int, list[str]] | None = None

    def flush_bullet() -> None:
        nonlocal active_bullet
        if active_bullet is None:
            return
        start_line, body_lines = active_bullet
        text = normalized_text(body_lines)
        if text:
            units.append(
                SourceUnit(
                    source_unit_id=f"{spec.slice_id}::bullet::{start_line}",
                    slice_id=spec.slice_id,
                    document_id=spec.path.stem,
                    method=spec.method,
                    unit_kind="best_practice_note",
                    section_path=["bullet"],
                    source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                    text=text,
                )
            )
        active_bullet = None

    def flush_block(kind: str) -> None:
        nonlocal active_block
        if active_block is None:
            return
        label, start_line, body_lines = active_block
        text = normalized_text(body_lines)
        if text:
            units.append(
                SourceUnit(
                    source_unit_id=f"{spec.slice_id}::{kind}::{label}",
                    slice_id=spec.slice_id,
                    document_id=spec.path.stem,
                    method=spec.method,
                    unit_kind=kind,
                    section_path=[label],
                    source_ref=f"{spec.path}:{start_line}-{start_line + max(0, len(body_lines) - 1)}",
                    text=text,
                )
            )
        active_block = None

    for line_no, raw in indexed:
        line = clean_layout_text(raw)
        if not line:
            flush_bullet()
            continue
        if is_layout_noise(line):
            continue
        if line in {
            "Messages containing the following must be read back:",
            "Acknowledgement by Callsign",
            "Items to be Read back",
            "Read back",
            "Condition",
            "Clearance",
            "Brief reiteration of the condition",
            "General",
            "General Phraseology",
        }:
            flush_bullet()
            flush_block("background_explanation" if line in {"General", "General Phraseology"} else "best_practice_note")
            active_block = (line, line_no, [line])
            continue
        if line == "•":
            flush_bullet()
            active_bullet = (line_no, [])
            continue
        if active_bullet is not None:
            active_bullet[1].append(line)
            continue
        if active_block is None:
            active_block = ("context", line_no, [line])
        else:
            active_block[2].append(line)

    flush_bullet()
    flush_block("best_practice_note")
    return units


METHODS: dict[str, Callable[[SliceSpec, list[tuple[int, str]]], list[SourceUnit]]] = {
    "numeric_clause_with_lists": extract_numeric_clause_with_lists,
    "sera_clause": extract_sera_clause,
    "paragraphs_with_examples": extract_paragraphs_with_examples,
    "bilingual_lettered_pairs": extract_bilingual_lettered_pairs,
    "manual_numbered_with_examples": extract_manual_numbered_with_examples,
    "advisory_bullets_and_paragraphs": extract_advisory_bullets_and_paragraphs,
}


def build_slice_payload(spec: SliceSpec) -> dict:
    indexed = read_slice_lines(spec)
    units = METHODS[spec.method](spec, indexed)
    return {
        "sliceId": spec.slice_id,
        "document": str(spec.path),
        "method": spec.method,
        "notes": spec.notes,
        "lineRange": {"start": spec.start_line, "end": spec.end_line},
        "unitCount": len(units),
        "unitKinds": dict(sorted(Counter(unit.unit_kind for unit in units).items())),
        "units": [asdict(unit) for unit in units],
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    args.output_dir.mkdir(parents=True, exist_ok=True)
    summary = []
    for spec in SLICE_SPECS:
        payload = build_slice_payload(spec)
        out = args.output_dir / f"{spec.slice_id}.json"
        out.write_text(json.dumps(payload, indent=2), encoding="utf-8")
        summary.append(
            {
                "sliceId": spec.slice_id,
                "method": spec.method,
                "unitCount": payload["unitCount"],
                "unitKinds": payload["unitKinds"],
                "output": str(out),
            }
        )

    (args.output_dir / "summary.json").write_text(
        json.dumps({"slices": summary}, indent=2),
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
