#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


EXPECTED_ARTIFACTS = [
    "generation_manifest.json",
    "generated_cases.json",
    "blocked_residuals.json",
    "generation_review_queue.json",
    "generation_review_pack.md",
    "generation_summary.md",
]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--fixture-dir", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()

    checks: list[dict[str, object]] = []
    overall_pass = True

    for artifact in EXPECTED_ARTIFACTS:
        run_path = args.run_dir / artifact
        fixture_path = args.fixture_dir / artifact
        exists = run_path.exists() and fixture_path.exists()
        match = exists and sha256(run_path) == sha256(fixture_path)
        checks.append(
            {
                "artifact": artifact,
                "runExists": run_path.exists(),
                "fixtureExists": fixture_path.exists(),
                "matches": match,
            }
        )
        overall_pass = overall_pass and match

    payload = {
        "pass": overall_pass,
        "runDir": str(args.run_dir),
        "fixtureDir": str(args.fixture_dir),
        "checks": checks,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(payload, indent=2), encoding="utf-8")

    if not overall_pass:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
