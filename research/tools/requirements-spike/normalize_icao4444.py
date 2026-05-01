#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path

from icao4444_normalizer_lib import DEFAULT_SOURCE, build_normalization_run, write_json


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()

    run = build_normalization_run(args.source)
    output_dir = args.output_dir
    write_json(output_dir / "document_tape.json", run["documentTape"])
    write_json(output_dir / "block_tree.json", run["blockTree"])
    write_json(output_dir / "source_units.json", run["sourceUnits"])
    write_json(output_dir / "bundle_candidates.json", run["bundleCandidates"])
    write_json(
        output_dir / "run_manifest.json",
        {
            **run["metadata"],
            "artifactPaths": {
                "documentTape": str(output_dir / "document_tape.json"),
                "blockTree": str(output_dir / "block_tree.json"),
                "sourceUnits": str(output_dir / "source_units.json"),
                "bundleCandidates": str(output_dir / "bundle_candidates.json"),
            },
            "stageCounts": {
                "documentTapeLines": len(run["documentTape"]),
                "blockNodes": len(run["blockTree"]),
                "sourceUnits": len(run["sourceUnits"]),
                "bundleCandidates": len(run["bundleCandidates"]),
            },
            "validationSummary": None,
            "counts": run["counts"],
        },
    )


if __name__ == "__main__":
    main()
