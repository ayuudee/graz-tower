#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
from collections import Counter
from pathlib import Path


def load_rows(path: Path) -> list[dict[str, str]]:
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def print_counter(title: str, rows: list[dict[str, str]], key_fields: list[str]) -> None:
    counter = Counter(tuple(row[field] for field in key_fields) for row in rows)
    print(f"\n{title}")
    for key, count in sorted(counter.items()):
        label = " | ".join(key)
        print(f"- {label}: {count}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", type=Path, required=True)
    args = parser.parse_args()

    rows = load_rows(args.csv)

    print(f"rows: {len(rows)}")
    print_counter("By outcome", rows, ["outcome"])
    print_counter("By model and outcome", rows, ["model", "outcome"])
    print_counter("By family and outcome", rows, ["familyId", "outcome"])


if __name__ == "__main__":
    main()
