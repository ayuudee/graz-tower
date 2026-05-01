#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import re
from dataclasses import asdict, dataclass
from datetime import UTC, datetime
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_SOURCE = ROOT / "research/txt/icao4444-extracted.txt"

NUMERIC_LABEL_ONLY_RE = re.compile(r"^(?P<label>\d+(?:\.\d+)+)\s*$")
NUMERIC_CLAUSE_RE = re.compile(r"^(?P<label>\d+(?:\.\d+)+)\s+(?P<body>.+)$")
BODY_CHAPTER_HEADING_RE = re.compile(r"^Chapter\s+(?P<label>\d+)\s*$")
PAGE_CHAPTER_RE = re.compile(r"^Chapter\s+\d+\.")
TOC_CHAPTER_RE = re.compile(r"^CHAPTER\s+\d+\.")
LETTERED_ITEM_RE = re.compile(r"^(?P<label>[a-z]\))\s*(?P<body>.*)$")
NUMBERED_ITEM_RE = re.compile(r"^(?P<label>\d+\))\s*(?P<body>.*)$")
NUMBER_DOT_LABEL_RE = re.compile(r"^(?P<label>\d+\.)\s*(?P<body>.*)$")
NOTE_RE = re.compile(r"^(?P<label>Note(?:\s+\d+)?\.\u2014)\s*(?P<body>.*)$")
DEFINITION_ENTRY_RE = re.compile(r"^(?P<term>[A-Z][A-Za-z0-9 /(),\-\u2014]+?)\.\s+(?P<body>.+)$")
INLINE_SECTION_RE = re.compile(r"^Section\s+(?P<label>\d+)\.\u2014\s*(?P<body>.+)$")
ROMAN_PAGE_RE = re.compile(r"^\(([ivxlcdm]+)\)$", re.IGNORECASE)
PAGE_NUMBER_RE = re.compile(r"^\d+-\d+$")
DATE_STAMP_RE = re.compile(r"^\d{1,2}/\d{1,2}/+\d{2,4}$")
NO_NUMBER_RE = re.compile(r"^No\.\s+\d+$")
APPENDIX_HEADING_RE = re.compile(r"^Appendix\s+[A-Z0-9]+\.$")
UPPERCASE_HEADING_RE = re.compile(r"^[A-Z0-9 ,/\-\u2014()]+$")
SEPARATOR_RE = re.compile(r"^[\-_]{5,}$")
TABLE_FRAGMENT_RE = re.compile(
    r"^(?:"
    r"\(|\)|/|\+|\u2014|\u2013|\uf0a3|"
    r"\d{1,6}°?|"
    r"[\u2013\u2014]{2,}|"
    r"\([A-Za-z0-9/]+\)\]?,?\*{0,2}|"
    r"[A-Z]{2,}\*{0,3}|"
    r"[A-Za-z0-9]+/[A-Za-z0-9]+|"
    r"[A-Za-z]+(?:-[A-Za-z]+)+"
    r")$"
)

PAGE_FURNITURE_LINES = {
    "Doc 4444",
    "PROCEDURES FOR AIR NAVIGATION SERVICES",
    "Procedures for",
    "Air Navigation Services",
    "Air Traffic Management",
    "Air Traffic Management (PANS-ATM)",
    "INTERNATIONAL CIVIL AVIATION ORGANIZATION",
    "International Civil Aviation Organization",
    "AMENDMENTS",
    "RECORD OF AMENDMENTS AND CORRIGENDA",
    "AMENDMENTS",
    "CORRIGENDA",
    "TABLE OF CONTENTS",
    "Page",
}


@dataclass(frozen=True)
class DocumentTapeLine:
    lineNo: int
    rawText: str
    cleanText: str
    pageBreakBefore: bool
    layoutClass: str
    keepForStructure: bool


@dataclass(frozen=True)
class BlockNode:
    blockId: str
    blockKind: str
    label: str | None
    title: str | None
    parentBlockId: str | None
    lineStart: int
    lineEnd: int
    sectionPath: list[str]
    rawTextLines: list[str]
    cleanTextLines: list[str]
    droppedLayoutLines: list[int]


@dataclass(frozen=True)
class SourceUnit:
    sourceUnitId: str
    unitKind: str
    sectionPath: list[str]
    parentSourceUnitId: str | None
    blockId: str
    sourceRef: str
    sourceText: str
    normalizedText: str
    layoutEvidence: dict[str, Any]


@dataclass(frozen=True)
class BundleCandidate:
    bundleId: str
    bundleKind: str
    recommendation: str
    primarySourceUnitId: str
    memberIds: list[str]
    sourceRef: str
    justification: str


def utc_now_iso() -> str:
    return datetime.now(UTC).replace(microsecond=0).isoformat()


def source_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    digest.update(path.read_bytes())
    return digest.hexdigest()


def clean_line(raw: str) -> tuple[str, bool]:
    page_break_before = "\x0c" in raw
    clean = raw.replace("\x0c", "").replace("\x07", "").strip()
    return clean, page_break_before


def classify_layout(clean_text: str) -> str:
    if not clean_text:
        return "blank"
    if clean_text.count(".") >= 20:
        return "page_furniture"
    if clean_text in PAGE_FURNITURE_LINES:
        return "page_furniture"
    if TOC_CHAPTER_RE.match(clean_text):
        return "page_furniture"
    if PAGE_CHAPTER_RE.match(clean_text):
        return "page_furniture"
    if ROMAN_PAGE_RE.match(clean_text):
        return "page_furniture"
    if PAGE_NUMBER_RE.match(clean_text):
        return "page_furniture"
    if DATE_STAMP_RE.match(clean_text):
        return "page_furniture"
    if NO_NUMBER_RE.match(clean_text):
        return "page_furniture"
    if SEPARATOR_RE.match(clean_text):
        return "page_furniture"
    if clean_text.startswith("Figure "):
        return "page_furniture"
    if clean_text.startswith("Published in separate English"):
        return "page_furniture"
    if clean_text.startswith("For ordering information"):
        return "page_furniture"
    if clean_text.startswith("This edition supersedes"):
        return "page_furniture"
    if clean_text.startswith("Order Number:"):
        return "page_furniture"
    if clean_text.startswith("ISBN "):
        return "page_furniture"
    if clean_text.startswith("© ICAO"):
        return "page_furniture"
    if clean_text.startswith("Fifteenth edition"):
        return "page_furniture"
    if clean_text.startswith("Sixteenth edition"):
        return "page_furniture"
    if clean_text.startswith("Amendments "):
        return "page_furniture"
    if clean_text.startswith("To be incorporated"):
        return "page_furniture"
    return "content"


def build_document_tape(source_path: Path) -> list[DocumentTapeLine]:
    lines = source_path.read_text(encoding="utf-8").split("\n")
    return [
        DocumentTapeLine(
            lineNo=line_no,
            rawText=raw,
            cleanText=clean,
            pageBreakBefore=page_break_before,
            layoutClass=classify_layout(clean),
            keepForStructure=classify_layout(clean) == "content",
        )
        for line_no, raw in enumerate(lines, start=1)
        for clean, page_break_before in [clean_line(raw)]
    ]


def text_from_lines(lines: list[str]) -> str:
    return "\n".join(line for line in lines if line).strip()


def append_line(node: dict[str, Any], tape_line: DocumentTapeLine) -> None:
    node["lineEnd"] = tape_line.lineNo
    node["rawTextLines"].append(tape_line.rawText)
    node["cleanTextLines"].append(tape_line.cleanText)


def build_block_tree(tape: list[DocumentTapeLine]) -> list[BlockNode]:
    nodes: list[dict[str, Any]] = []
    node_counter = 0

    current_chapter_id: str | None = None
    current_chapter_label: str | None = None
    current_chapter_title: str | None = None
    current_heading_id: str | None = None
    current_heading_title: str | None = None
    current_inline_section_id: str | None = None
    current_clause_id: str | None = None
    current_definition_id: str | None = None
    current_list_item_id: str | None = None
    heading_by_label: dict[str, dict[str, Any]] = {}
    active_node: dict[str, Any] | None = None

    def next_id(kind: str) -> str:
        nonlocal node_counter
        node_counter += 1
        return f"icao4444::{kind}::{node_counter:05d}"

    def current_section_path(extra: list[str] | None = None) -> list[str]:
        path: list[str] = []
        if current_chapter_title:
            path.append(current_chapter_title)
        if current_heading_title:
            path.append(current_heading_title)
        if extra:
            path.extend(extra)
        return path

    def flush_active() -> None:
        nonlocal active_node
        if active_node is None:
            return
        active_node["sectionPath"] = current_section_path(
            [active_node["label"]] if active_node["label"] else [],
        )
        nodes.append(active_node)
        active_node = None

    def is_heading_title_candidate(line: str) -> bool:
        if not re.search(r"[A-Za-z]", line):
            return False
        if not line or line.endswith(".") or line.endswith(";") or line.endswith(":"):
            return False
        if len(line) > 100:
            return False
        return line == line.title() or UPPERCASE_HEADING_RE.match(line) is not None

    def next_kept_clean_text(from_index: int) -> str | None:
        probe = from_index + 1
        while probe < len(tape):
            candidate = tape[probe]
            if candidate.keepForStructure:
                return candidate.cleanText
            probe += 1
        return None

    def is_clause_like_label(label: str) -> bool:
        if current_chapter_label is None:
            return True
        if label.startswith(f"{current_chapter_label}."):
            return True
        if current_list_item_id is None and current_clause_id is None and current_definition_id is None:
            return True
        return False

    def should_break_note(line: str) -> bool:
        return (
            line.startswith("Figure ")
            or classify_layout(line) == "page_furniture"
        )

    index = 0
    while index < len(tape):
        tape_line = tape[index]
        if not tape_line.keepForStructure:
            if active_node is not None and tape_line.layoutClass == "page_furniture":
                active_node["droppedLayoutLines"].append(tape_line.lineNo)
            index += 1
            continue

        line = tape_line.cleanText

        chapter = BODY_CHAPTER_HEADING_RE.match(line)
        if chapter:
            flush_active()
            chapter_id = next_id("chapter_heading")
            current_chapter_id = chapter_id
            current_chapter_label = chapter.group("label")
            current_chapter_title = None
            current_heading_id = None
            current_heading_title = None
            current_inline_section_id = None
            current_clause_id = None
            current_definition_id = None
            current_list_item_id = None
            chapter_node = {
                "blockId": chapter_id,
                "blockKind": "chapter_heading",
                "label": chapter.group("label"),
                "title": current_chapter_title,
                "parentBlockId": None,
                "lineStart": tape_line.lineNo,
                "lineEnd": tape_line.lineNo,
                "sectionPath": [],
                "rawTextLines": [tape_line.rawText],
                "cleanTextLines": [line],
                "droppedLayoutLines": [],
            }
            nodes.append(chapter_node)
            index += 1
            continue

        if current_chapter_id and current_chapter_title is None and is_heading_title_candidate(line):
            current_chapter_title = line
            for node in reversed(nodes):
                if node["blockId"] == current_chapter_id:
                    node["title"] = line
                    break
            index += 1
            continue

        heading_label = NUMERIC_LABEL_ONLY_RE.match(line)
        if heading_label and is_clause_like_label(heading_label.group("label")):
            flush_active()
            heading_id = next_id("section_heading")
            current_heading_id = heading_id
            current_heading_title = None
            current_inline_section_id = None
            current_clause_id = None
            current_definition_id = None
            current_list_item_id = None
            heading_node = {
                "blockId": heading_id,
                "blockKind": "section_heading",
                "label": heading_label.group("label"),
                "title": None,
                "parentBlockId": current_chapter_id,
                "lineStart": tape_line.lineNo,
                "lineEnd": tape_line.lineNo,
                "sectionPath": current_section_path(),
                "rawTextLines": [tape_line.rawText],
                "cleanTextLines": [line],
                "droppedLayoutLines": [],
            }
            heading_by_label[heading_label.group("label")] = heading_node
            nodes.append(heading_node)
            index += 1
            continue

        if heading_label:
            flush_active()
            nodes.append(
                {
                    "blockId": next_id("label_stub"),
                    "blockKind": "label_stub",
                    "label": heading_label.group("label"),
                    "title": None,
                    "parentBlockId": current_inline_section_id or current_heading_id or current_chapter_id,
                    "lineStart": tape_line.lineNo,
                    "lineEnd": tape_line.lineNo,
                    "sectionPath": current_section_path(),
                    "rawTextLines": [tape_line.rawText],
                    "cleanTextLines": [line],
                    "droppedLayoutLines": [],
                }
            )
            index += 1
            continue

        if current_heading_id and current_heading_title is None and is_heading_title_candidate(line):
            current_heading_title = line
            for node in reversed(nodes):
                if node["blockId"] == current_heading_id:
                    node["title"] = line
                    break
            index += 1
            continue

        inline_section = INLINE_SECTION_RE.match(line)
        if inline_section:
            flush_active()
            inline_id = next_id("section_heading")
            current_inline_section_id = inline_id
            nodes.append(
                {
                    "blockId": inline_id,
                    "blockKind": "section_heading",
                    "label": f"Section {inline_section.group('label')}",
                    "title": inline_section.group("body"),
                    "parentBlockId": current_clause_id or current_heading_id or current_chapter_id,
                    "lineStart": tape_line.lineNo,
                    "lineEnd": tape_line.lineNo,
                    "sectionPath": current_section_path(),
                    "rawTextLines": [tape_line.rawText],
                    "cleanTextLines": [line],
                    "droppedLayoutLines": [],
                }
            )
            index += 1
            continue

        numeric_clause = NUMERIC_CLAUSE_RE.match(line)
        if numeric_clause and is_clause_like_label(numeric_clause.group("label")):
            flush_active()
            label = numeric_clause.group("label")
            body = numeric_clause.group("body")
            parent_label = ".".join(label.split(".")[:-1])
            clause_id = next_id("numeric_clause")
            parent_block_id = heading_by_label.get(parent_label, {}).get("blockId", current_heading_id)
            current_inline_section_id = None
            current_clause_id = clause_id
            current_definition_id = None
            current_list_item_id = None
            active_node = {
                "blockId": clause_id,
                "blockKind": "numeric_clause",
                "label": label,
                "title": None,
                "parentBlockId": parent_block_id,
                "lineStart": tape_line.lineNo,
                "lineEnd": tape_line.lineNo,
                "sectionPath": [],
                "rawTextLines": [tape_line.rawText],
                "cleanTextLines": [body],
                "droppedLayoutLines": [],
            }
            index += 1
            continue

        if current_chapter_title == "DEFINITIONS":
            definition = DEFINITION_ENTRY_RE.match(line)
            if definition and current_list_item_id is None:
                flush_active()
                definition_id = next_id("definition_entry")
                current_definition_id = definition_id
                current_clause_id = None
                current_list_item_id = None
                active_node = {
                    "blockId": definition_id,
                    "blockKind": "definition_entry",
                    "label": definition.group("term"),
                    "title": None,
                    "parentBlockId": current_inline_section_id or current_heading_id or current_chapter_id,
                    "lineStart": tape_line.lineNo,
                    "lineEnd": tape_line.lineNo,
                    "sectionPath": [],
                    "rawTextLines": [tape_line.rawText],
                    "cleanTextLines": [definition.group("body")],
                    "droppedLayoutLines": [],
                }
                index += 1
                continue

        note = NOTE_RE.match(line)
        if note:
            flush_active()
            note_parent_id = (
                current_list_item_id
                or current_definition_id
                or current_inline_section_id
                or current_clause_id
                or current_heading_id
                or current_chapter_id
            )
            active_node = {
                "blockId": next_id("note_block"),
                "blockKind": "note_block",
                "label": note.group("label"),
                "title": None,
                "parentBlockId": note_parent_id,
                "lineStart": tape_line.lineNo,
                "lineEnd": tape_line.lineNo,
                "sectionPath": [],
                "rawTextLines": [tape_line.rawText],
                "cleanTextLines": [note.group("body") or line],
                "droppedLayoutLines": [],
            }
            current_list_item_id = None
            index += 1
            continue

        if line == "—":
            flush_active()
            nodes.append(
                {
                    "blockId": next_id("separator_line"),
                    "blockKind": "separator_line",
                    "label": None,
                    "title": None,
                    "parentBlockId": current_inline_section_id or current_heading_id or current_chapter_id,
                    "lineStart": tape_line.lineNo,
                    "lineEnd": tape_line.lineNo,
                    "sectionPath": current_section_path(),
                    "rawTextLines": [tape_line.rawText],
                    "cleanTextLines": [line],
                    "droppedLayoutLines": [],
                }
            )
            index += 1
            continue

        if TABLE_FRAGMENT_RE.match(line):
            flush_active()
            nodes.append(
                {
                    "blockId": next_id("table_fragment"),
                    "blockKind": "table_fragment",
                    "label": None,
                    "title": None,
                    "parentBlockId": current_inline_section_id or current_heading_id or current_chapter_id,
                    "lineStart": tape_line.lineNo,
                    "lineEnd": tape_line.lineNo,
                    "sectionPath": current_section_path(),
                    "rawTextLines": [tape_line.rawText],
                    "cleanTextLines": [line],
                    "droppedLayoutLines": [],
                }
            )
            index += 1
            continue

        lettered = LETTERED_ITEM_RE.match(line)
        if lettered:
            next_clean = next_kept_clean_text(index) or ""
            if not lettered.group("body") and (
                NUMBERED_ITEM_RE.match(next_clean)
                or LETTERED_ITEM_RE.match(next_clean)
                or NUMBER_DOT_LABEL_RE.match(next_clean)
                or TABLE_FRAGMENT_RE.match(next_clean)
                or INLINE_SECTION_RE.match(next_clean)
                or BODY_CHAPTER_HEADING_RE.match(next_clean)
                or APPENDIX_HEADING_RE.match(next_clean)
                or (
                    NUMERIC_CLAUSE_RE.match(next_clean)
                    and is_clause_like_label(NUMERIC_CLAUSE_RE.match(next_clean).group("label"))
                )
            ):
                flush_active()
                nodes.append(
                    {
                        "blockId": next_id("label_stub"),
                        "blockKind": "label_stub",
                        "label": lettered.group("label"),
                        "title": None,
                        "parentBlockId": current_list_item_id or current_definition_id or current_inline_section_id or current_clause_id or current_heading_id or current_chapter_id,
                        "lineStart": tape_line.lineNo,
                        "lineEnd": tape_line.lineNo,
                        "sectionPath": current_section_path(),
                        "rawTextLines": [tape_line.rawText],
                        "cleanTextLines": [line],
                        "droppedLayoutLines": [],
                    }
                )
                index += 1
                continue
            flush_active()
            list_item_id = next_id("list_item")
            parent_id = current_definition_id or current_inline_section_id or current_clause_id or current_heading_id or current_chapter_id
            current_list_item_id = list_item_id
            active_node = {
                "blockId": list_item_id,
                "blockKind": "list_item",
                "label": lettered.group("label"),
                "title": None,
                "parentBlockId": parent_id,
                "lineStart": tape_line.lineNo,
                "lineEnd": tape_line.lineNo,
                "sectionPath": [],
                "rawTextLines": [tape_line.rawText],
                "cleanTextLines": [lettered.group("body")],
                "droppedLayoutLines": [],
            }
            index += 1
            continue

        numbered = NUMBERED_ITEM_RE.match(line)
        if numbered:
            next_clean = next_kept_clean_text(index) or ""
            if not numbered.group("body") and (
                NUMBERED_ITEM_RE.match(next_clean)
                or LETTERED_ITEM_RE.match(next_clean)
                or NUMBER_DOT_LABEL_RE.match(next_clean)
                or TABLE_FRAGMENT_RE.match(next_clean)
                or INLINE_SECTION_RE.match(next_clean)
                or BODY_CHAPTER_HEADING_RE.match(next_clean)
                or APPENDIX_HEADING_RE.match(next_clean)
                or (
                    NUMERIC_CLAUSE_RE.match(next_clean)
                    and is_clause_like_label(NUMERIC_CLAUSE_RE.match(next_clean).group("label"))
                )
            ):
                flush_active()
                nodes.append(
                    {
                        "blockId": next_id("label_stub"),
                        "blockKind": "label_stub",
                        "label": numbered.group("label"),
                        "title": None,
                        "parentBlockId": current_list_item_id or current_definition_id or current_inline_section_id or current_clause_id or current_heading_id or current_chapter_id,
                        "lineStart": tape_line.lineNo,
                        "lineEnd": tape_line.lineNo,
                        "sectionPath": current_section_path(),
                        "rawTextLines": [tape_line.rawText],
                        "cleanTextLines": [line],
                        "droppedLayoutLines": [],
                    }
                )
                index += 1
                continue
            flush_active()
            parent_id = current_list_item_id or current_definition_id or current_inline_section_id or current_clause_id or current_heading_id or current_chapter_id
            active_node = {
                "blockId": next_id("sub_list_item"),
                "blockKind": "sub_list_item",
                "label": numbered.group("label"),
                "title": None,
                "parentBlockId": parent_id,
                "lineStart": tape_line.lineNo,
                "lineEnd": tape_line.lineNo,
                "sectionPath": [],
                "rawTextLines": [tape_line.rawText],
                "cleanTextLines": [numbered.group("body")],
                "droppedLayoutLines": [],
            }
            index += 1
            continue

        number_dot = NUMBER_DOT_LABEL_RE.match(line)
        if number_dot and not number_dot.group("body"):
            flush_active()
            nodes.append(
                {
                    "blockId": next_id("label_stub"),
                    "blockKind": "label_stub",
                    "label": number_dot.group("label"),
                    "title": None,
                    "parentBlockId": current_list_item_id or current_definition_id or current_inline_section_id or current_clause_id or current_heading_id or current_chapter_id,
                    "lineStart": tape_line.lineNo,
                    "lineEnd": tape_line.lineNo,
                    "sectionPath": current_section_path(),
                    "rawTextLines": [tape_line.rawText],
                    "cleanTextLines": [line],
                    "droppedLayoutLines": [],
                }
            )
            index += 1
            continue

        if active_node is not None:
            if active_node["blockKind"] == "note_block" and should_break_note(line):
                flush_active()
                continue
            append_line(active_node, tape_line)
            index += 1
            continue

        if current_chapter_id is None:
            nodes.append(
                {
                    "blockId": next_id("front_matter"),
                    "blockKind": "front_matter",
                    "label": None,
                    "title": None,
                    "parentBlockId": None,
                    "lineStart": tape_line.lineNo,
                    "lineEnd": tape_line.lineNo,
                    "sectionPath": [],
                    "rawTextLines": [tape_line.rawText],
                    "cleanTextLines": [line],
                    "droppedLayoutLines": [],
                }
            )
            index += 1
            continue

        if is_heading_title_candidate(line):
            heading_id = next_id("section_heading")
            nodes.append(
                {
                    "blockId": heading_id,
                    "blockKind": "section_heading",
                    "label": None,
                    "title": line,
                    "parentBlockId": current_inline_section_id or current_heading_id or current_chapter_id,
                    "lineStart": tape_line.lineNo,
                    "lineEnd": tape_line.lineNo,
                    "sectionPath": current_section_path(),
                    "rawTextLines": [tape_line.rawText],
                    "cleanTextLines": [line],
                    "droppedLayoutLines": [],
                }
            )
            current_inline_section_id = heading_id
            index += 1
            continue

        if APPENDIX_HEADING_RE.match(line):
            heading_id = next_id("section_heading")
            nodes.append(
                {
                    "blockId": heading_id,
                    "blockKind": "section_heading",
                    "label": None,
                    "title": line,
                    "parentBlockId": current_inline_section_id or current_heading_id or current_chapter_id,
                    "lineStart": tape_line.lineNo,
                    "lineEnd": tape_line.lineNo,
                    "sectionPath": current_section_path(),
                    "rawTextLines": [tape_line.rawText],
                    "cleanTextLines": [line],
                    "droppedLayoutLines": [],
                }
            )
            current_inline_section_id = heading_id
            index += 1
            continue

        if len(line) > 20 or ":" in line or " " in line or line[:1].islower() or line.endswith(".") or line.endswith(";"):
            active_node = {
                "blockId": next_id("free_paragraph"),
                "blockKind": "free_paragraph",
                "label": None,
                "title": None,
                "parentBlockId": current_inline_section_id or current_heading_id or current_chapter_id,
                "lineStart": tape_line.lineNo,
                "lineEnd": tape_line.lineNo,
                "sectionPath": [],
                "rawTextLines": [tape_line.rawText],
                "cleanTextLines": [line],
                "droppedLayoutLines": [],
            }
            index += 1
            continue

        nodes.append(
            {
                "blockId": next_id("unknown_structure"),
                "blockKind": "unknown_structure",
                "label": None,
                "title": None,
                "parentBlockId": current_inline_section_id or current_heading_id or current_chapter_id,
                "lineStart": tape_line.lineNo,
                "lineEnd": tape_line.lineNo,
                "sectionPath": current_section_path(),
                "rawTextLines": [tape_line.rawText],
                "cleanTextLines": [line],
                "droppedLayoutLines": [],
            }
        )
        index += 1

    flush_active()

    return [BlockNode(**node) for node in nodes]


def source_ref(source_path: Path, line_start: int, line_end: int) -> str:
    return f"{source_path}:{line_start}-{line_end}"


def emit_source_units(block_tree: list[BlockNode], source_path: Path) -> list[SourceUnit]:
    block_index = {node.blockId: node for node in block_tree}
    units: list[SourceUnit] = []
    for node in block_tree:
        if node.blockKind not in {"numeric_clause", "definition_entry", "note_block", "list_item", "sub_list_item", "section_heading"}:
            continue
        unit_kind = {
            "numeric_clause": "normative_clause",
            "definition_entry": "definition",
            "note_block": "background_explanation",
            "list_item": "list_item",
            "sub_list_item": "sub_list_item",
            "section_heading": "heading_context",
        }[node.blockKind]
        parent_unit_id = None
        if node.parentBlockId and node.parentBlockId in block_index:
            parent_node = block_index[node.parentBlockId]
            if parent_node.blockKind in {"numeric_clause", "definition_entry", "list_item", "section_heading"}:
                parent_unit_id = f"{parent_node.blockId}::unit"
        normalized_text = text_from_lines(node.cleanTextLines)
        source_text = text_from_lines([line.replace("\x0c", "").replace("\x07", "") for line in node.rawTextLines])
        units.append(
            SourceUnit(
                sourceUnitId=f"{node.blockId}::unit",
                unitKind=unit_kind,
                sectionPath=node.sectionPath,
                parentSourceUnitId=parent_unit_id,
                blockId=node.blockId,
                sourceRef=source_ref(source_path, node.lineStart, node.lineEnd),
                sourceText=source_text,
                normalizedText=normalized_text,
                layoutEvidence={
                    "droppedLayoutLines": node.droppedLayoutLines,
                    "lineStart": node.lineStart,
                    "lineEnd": node.lineEnd,
                },
            )
        )
    return units


def emit_bundle_candidates(source_units: list[SourceUnit], source_path: Path) -> list[BundleCandidate]:
    units_by_id = {unit.sourceUnitId: unit for unit in source_units}
    children_by_parent: dict[str, list[SourceUnit]] = {}
    for unit in source_units:
        if unit.parentSourceUnitId is None:
            continue
        children_by_parent.setdefault(unit.parentSourceUnitId, []).append(unit)

    bundles: list[BundleCandidate] = []
    for unit in source_units:
        children = sorted(children_by_parent.get(unit.sourceUnitId, []), key=lambda child: child.sourceRef)
        if unit.unitKind not in {"normative_clause", "definition", "list_item"}:
            continue
        if not children and not unit.normalizedText.rstrip().endswith(":"):
            if unit.unitKind != "normative_clause":
                continue
            bundles.append(
                BundleCandidate(
                    bundleId=f"icao4444:{unit.blockId}",
                    bundleKind="standalone_clause",
                    recommendation="standalone",
                    primarySourceUnitId=unit.sourceUnitId,
                    memberIds=[unit.sourceUnitId],
                    sourceRef=unit.sourceRef,
                    justification="standalone normative clause without structural dependants",
                )
            )
            continue
        child_kinds = {child.unitKind for child in children}
        if "list_item" in child_kinds and "background_explanation" in child_kinds:
            bundle_kind = "clause_with_list_and_note"
        elif "list_item" in child_kinds:
            bundle_kind = "clause_with_list"
        elif "sub_list_item" in child_kinds:
            bundle_kind = "list_item_with_subitems"
        elif "background_explanation" in child_kinds:
            bundle_kind = "clause_with_support_note"
        else:
            bundle_kind = "manual_review_required"
        recommendation = (
            "bundle_required"
            if (children and any(child.unitKind in {"list_item", "sub_list_item"} for child in children))
            or unit.normalizedText.rstrip().endswith(":")
            else "standalone_with_support"
        )
        member_ids = [unit.sourceUnitId, *[child.sourceUnitId for child in children]]
        bundles.append(
            BundleCandidate(
                bundleId=f"icao4444:{unit.blockId}",
                bundleKind=bundle_kind,
                recommendation=recommendation,
                primarySourceUnitId=unit.sourceUnitId,
                memberIds=member_ids,
                sourceRef=unit.sourceRef,
                justification=(
                    "parent has structural dependants"
                    if children
                    else "parent clause ends with colon and needs contextual review"
                ),
            )
        )
    return bundles


def summarize_counts(block_tree: list[BlockNode], source_units: list[SourceUnit], bundles: list[BundleCandidate]) -> dict[str, Any]:
    return {
        "blockKinds": count_by_key([node.blockKind for node in block_tree]),
        "unitKinds": count_by_key([unit.unitKind for unit in source_units]),
        "bundleKinds": count_by_key([bundle.bundleKind for bundle in bundles]),
        "bundleRecommendations": count_by_key([bundle.recommendation for bundle in bundles]),
    }


def count_by_key(items: list[str]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for item in items:
        counts[item] = counts.get(item, 0) + 1
    return dict(sorted(counts.items()))


def build_normalization_run(source_path: Path = DEFAULT_SOURCE) -> dict[str, Any]:
    tape = build_document_tape(source_path)
    block_tree = build_block_tree(tape)
    source_units = emit_source_units(block_tree, source_path)
    bundles = emit_bundle_candidates(source_units, source_path)
    return {
        "metadata": {
            "sourceDocumentId": "icao4444-extracted",
            "sourcePath": str(source_path),
            "sourceSha256": source_sha256(source_path),
            "normalizerVersion": "2026-04-24-v2",
            "generatedAt": utc_now_iso(),
        },
        "documentTape": [asdict(line) for line in tape],
        "blockTree": [asdict(node) for node in block_tree],
        "sourceUnits": [asdict(unit) for unit in source_units],
        "bundleCandidates": [asdict(bundle) for bundle in bundles],
        "counts": summarize_counts(block_tree, source_units, bundles),
    }


def write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
