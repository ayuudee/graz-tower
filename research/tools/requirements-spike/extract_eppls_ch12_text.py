#!/usr/bin/env python3
"""Extract EPPLS Chapter 12 with Poppler reading-order text.

Run with Poppler available, for example:

    nix-shell -p poppler-utils --run \
      "python3 research/tools/requirements-spike/extract_eppls_ch12_text.py"

The previous EPPLS text dump used layout-preserving extraction over the whole
book. Chapter 12 is two-column, and that layout interleaved left/right column
text on the same line. Poppler's plain text mode gives a better reading order
for the Chapter 12 registry contract: exact quotes must be discoverable as
contiguous text after whitespace normalisation.
"""
from __future__ import annotations

import argparse
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
DEFAULT_PDF = ROOT / "research/pdf/EPPLS.pdf"
DEFAULT_OUTPUT = ROOT / "research/txt/eppls-extracted.txt"


def extract_plain_text(pdf: Path) -> str:
    proc = subprocess.run(
        ["pdftotext", str(pdf), "-"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return proc.stdout


def slice_chapter_12(full_text: str) -> str:
    lines = full_text.splitlines()
    try:
        start = next(i for i, line in enumerate(lines) if line.strip() == "C OMMUNICATIONS")
    except StopIteration as exc:
        raise SystemExit("Could not find EPPLS Chapter 12 start marker") from exc

    # Chapter 12 ends at the 12-22 footer; retain the footer block but no
    # following chapter text. This keeps the source explicitly chapter-scoped.
    end = None
    for i in range(start, len(lines)):
        if lines[i].strip() == "12-22":
            end = i + 1
            break
    if end is None:
        raise SystemExit("Could not find EPPLS Chapter 12 end marker")

    return "\n".join(lines[start:end]).rstrip() + "\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pdf", type=Path, default=DEFAULT_PDF)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()

    chapter_text = slice_chapter_12(extract_plain_text(args.pdf))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(chapter_text, encoding="utf-8")
    print(args.output)
    print(f"lines={len(chapter_text.splitlines())}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
