#!/usr/bin/env python3
"""Falsifiability experiment: do the deterministic post-step overrides
materially change the registry surface, or would the judge ignore the
bad challenger verdicts anyway?

This script monkey-patches `apply_bundle_gate_override` and
`apply_judge_conservatism_override` to passthroughs (no-ops), then
runs the SafetySense22 readback section. Compare the resulting
summary.md against a normal run. Identical = overrides are theatre;
different = overrides are load-bearing.

Usage:

    python3 research/tools/requirements-spike/no_override_trial.py \\
        --output-dir /tmp/no-override-trial
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path
from typing import Any


sys.path.insert(0, str(Path(__file__).resolve().parent))

import run_icao4444_ollama_first_prototype as proto  # noqa: E402


def passthrough_bundle_gate_override(*, challenge_parsed: dict, **_: Any):
    """No-op replacement for apply_bundle_gate_override: never overrides."""
    return challenge_parsed, None


def passthrough_judge_conservatism_override(*, judge_parsed: dict, **_: Any):
    """No-op replacement for apply_judge_conservatism_override: never overrides."""
    return judge_parsed, None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--case", default="safetysense22_readback_family")
    args_extra = parser.parse_args()

    proto.apply_bundle_gate_override = passthrough_bundle_gate_override
    proto.apply_judge_conservatism_override = passthrough_judge_conservatism_override

    inner = proto.build_arg_parser().parse_args([])
    inner.source = proto.ROOT / proto.CASES[args_extra.case]["sourceOverride"]
    case = proto.CASES[args_extra.case]
    args_extra.output_dir.mkdir(parents=True, exist_ok=True)
    proto.run_pipeline(case, inner, args_extra.output_dir)
    print(f"runDir: {args_extra.output_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
