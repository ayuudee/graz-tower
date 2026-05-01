#!/usr/bin/env python3

from __future__ import annotations

import argparse
import sys
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

import airport_entity_projection as projection
import airport_authoring_report as report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Project the current airport authoring package into a core-entity bundle plus publication supplement.",
    )
    parser.add_argument("manifest", type=Path, help="Path to the airport manifest JSON file")
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Output JSON file. Defaults to cad/airports/rendered/<airport>/entity-bundle.json.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    bundle = projection.build_projection_bundle(args.manifest)
    root = report.repo_root()
    default_output = root / "cad" / "airports" / "rendered" / bundle["airportCode"].lower() / "entity-bundle.json"
    output_path = args.output if args.output is not None else default_output
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(projection.projection_json(bundle))
    print(output_path)


if __name__ == "__main__":
    main()
