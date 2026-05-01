#!/usr/bin/env python3
"""Falsifiability audit for the deterministic overrides.

The Ollama-first lane has three deterministic post-step overrides
(`apply_sibling_symmetry_resolution`, `apply_bundle_gate_override`,
`apply_judge_conservatism_override`) that catch challenger and judge
failure modes the prompts can't reliably handle. We periodically
falsify the assumption "the overrides are still load-bearing" by
running the pipeline twice — once with overrides on (a normal run),
once with them monkey-patched to passthroughs — and diffing the
registry-eligible candidate set.

Empty diff for a given override across multiple runs ⇒ that override
is a sunset candidate (the prompts have absorbed its logic).

Sudden growth in the diff ⇒ underlying models regressed and overrides
are doing more work than before.

This is the recurring quality probe for the override surface. Per the
plan: run weekly, or whenever the override functions change.

Usage:

    python3 research/tools/requirements-spike/audit_overrides_load_bearing.py \\
        --case safetysense22_readback_family \\
        --output-dir /tmp/override-audit-$(date +%s)

Output:

    {output_dir}/with-overrides/      — normal pipeline run
    {output_dir}/no-overrides/        — pipeline run with overrides patched off
    {output_dir}/diff.json            — promotion-set diff: only_in_with, only_in_no, etc.
    {output_dir}/summary.md           — human-readable rollup

Exits non-zero if the audit cannot complete (missing case, Ollama
unreachable). Exit 0 otherwise — this is a measurement tool, not a
gate. Decisions about override sunset are human curation based on the
diff trend across multiple runs.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


sys.path.insert(0, str(Path(__file__).resolve().parent))

import run_icao4444_ollama_first_prototype as proto  # noqa: E402
from promote_to_registry import (  # noqa: E402
    eligible_canonical_ids_for_section,
)


def _passthrough_bundle_gate_override(*, challenge_parsed: dict, **_: Any):
    return challenge_parsed, None


def _passthrough_judge_conservatism_override(*, judge_parsed: dict, **_: Any):
    return judge_parsed, None


def _passthrough_sibling_symmetry_resolution(
    *,
    candidates: list[dict[str, Any]],
    bundle_gate_results: dict[str, dict[str, Any]],
    structure_items: list[dict[str, Any]],
):
    """Pass-through: return bundle gate results unchanged, no audits."""
    return bundle_gate_results, []


def _eligible_canonical_ids(section_dir: Path, document_id: str, section_id: str) -> set[str]:
    """Delegate to the shared `eligible_canonical_ids_for_section` so
    the with/without-overrides comparison uses byte-identical gate logic
    to the promoter. If a future gate is added to the promoter, the
    override audit picks it up automatically."""
    judged_path = section_dir / "judged_candidates.json"
    window_path = section_dir / "source_window.json"
    if not judged_path.exists():
        return set()
    judged = json.loads(judged_path.read_text(encoding="utf-8"))
    window = json.loads(window_path.read_text(encoding="utf-8")) if window_path.exists() else {}
    return eligible_canonical_ids_for_section(
        judged=judged,
        window=window,
        document_id=document_id,
        section_id=section_id,
    )


def run_with_overrides(case: dict, scratch_dir: Path, base_url: str) -> Path:
    """Run the pipeline at full strength."""
    inner = proto.build_arg_parser().parse_args([])
    inner.base_url = base_url
    inner.source = proto.ROOT / case["sourceOverride"]
    section_dir = scratch_dir
    section_dir.mkdir(parents=True, exist_ok=True)
    proto.run_pipeline(case, inner, section_dir)
    return section_dir


def run_without_overrides(case: dict, scratch_dir: Path, base_url: str) -> Path:
    """Run the pipeline with all three overrides monkey-patched to
    passthroughs. The pipeline state is restored on exit so subsequent
    callers see the original behaviour.

    Caveat: this monkey-patch only affects callers that look up the
    override functions through `proto.apply_*` at call time. A helper
    that did `from run_icao4444_ollama_first_prototype import
    apply_bundle_gate_override` would bind a local reference and bypass
    the patch. The assertion below confirms `proto.apply_*` is the
    passthrough at the moment we kick off the pipeline — it does NOT
    detect a third-party module that pre-bound the override at its own
    import time. By convention, no helper should import the override
    functions by name; if you add a helper, dispatch through
    `proto.apply_*` to keep this audit honest.
    """
    saved = {
        "bundle": proto.apply_bundle_gate_override,
        "judge": proto.apply_judge_conservatism_override,
        "sibling": proto.apply_sibling_symmetry_resolution,
    }
    proto.apply_bundle_gate_override = _passthrough_bundle_gate_override
    proto.apply_judge_conservatism_override = _passthrough_judge_conservatism_override
    proto.apply_sibling_symmetry_resolution = _passthrough_sibling_symmetry_resolution
    try:
        live = sys.modules["run_icao4444_ollama_first_prototype"]
        if (
            live.apply_bundle_gate_override is not _passthrough_bundle_gate_override
            or live.apply_judge_conservatism_override is not _passthrough_judge_conservatism_override
            or live.apply_sibling_symmetry_resolution is not _passthrough_sibling_symmetry_resolution
        ):
            raise RuntimeError(
                "monkey-patch verification failed: at least one override is not "
                "the passthrough at call time. A helper has likely bound an "
                "override at import (`from ... import apply_bundle_gate_override`) "
                "and bypasses the patch — fix the helper to look up via the module."
            )
        return run_with_overrides(case, scratch_dir, base_url)
    finally:
        proto.apply_bundle_gate_override = saved["bundle"]
        proto.apply_judge_conservatism_override = saved["judge"]
        proto.apply_sibling_symmetry_resolution = saved["sibling"]


def diff_eligibility(
    *,
    with_overrides_dir: Path,
    no_overrides_dir: Path,
    document_id: str,
    section_id: str,
) -> dict[str, Any]:
    with_set = _eligible_canonical_ids(with_overrides_dir, document_id, section_id)
    no_set = _eligible_canonical_ids(no_overrides_dir, document_id, section_id)
    only_in_with = sorted(with_set - no_set)
    only_in_no = sorted(no_set - with_set)
    shared = sorted(with_set & no_set)
    union_size = len(with_set | no_set)
    # When BOTH runs produced zero eligible candidates, "score=0" looks
    # like a sunset signal but is really inconclusive — the pipeline may
    # be broken (Ollama unreachable, prompt parse failures, etc.). Mark
    # explicitly so the summary doesn't recommend retiring overrides
    # based on no-data.
    if union_size == 0:
        verdict = "inconclusive"
        score = 0.0
    else:
        verdict = "diff_computed"
        score = (len(only_in_with) + len(only_in_no)) / union_size
    return {
        "documentId": document_id,
        "sectionId": section_id,
        "verdict": verdict,
        "withOverridesEligible": sorted(with_set),
        "noOverridesEligible": sorted(no_set),
        "onlyInWithOverrides": only_in_with,
        "onlyInNoOverrides": only_in_no,
        "shared": shared,
        "withCount": len(with_set),
        "noCount": len(no_set),
        "loadBearingScore": score,
    }


def render_summary(diff: dict[str, Any]) -> str:
    lines = [
        f"# Override load-bearing audit — `{diff['documentId']}/{diff['sectionId']}`",
        "",
        f"- with overrides: `{diff['withCount']}` eligible",
        f"- without overrides: `{diff['noCount']}` eligible",
        f"- only in with-overrides: `{len(diff['onlyInWithOverrides'])}`",
        f"- only in no-overrides: `{len(diff['onlyInNoOverrides'])}`",
        f"- shared: `{len(diff['shared'])}`",
        f"- load-bearing score: `{diff['loadBearingScore']:.3f}` "
        "(0 = identical sets ⇒ overrides redundant; 1 = disjoint ⇒ overrides decisive)",
        "",
        "## Verdict",
        "",
    ]
    if diff.get("verdict") == "inconclusive":
        lines.append(
            "**Inconclusive** — both runs produced zero eligible candidates. "
            "Investigate the pipeline (Ollama reachable? prompt parse OK?) "
            "before drawing any conclusion about override load-bearing."
        )
        return "\n".join(lines) + "\n"
    score = diff["loadBearingScore"]
    if score == 0:
        lines.append(
            "**Sunset candidate** — overrides made no difference on this run. "
            "If three consecutive runs score 0, consider removing the override "
            "and updating the prompts to absorb its logic."
        )
    elif score < 0.1:
        lines.append("**Marginal** — overrides barely moved the eligibility set.")
    else:
        lines.append("**Load-bearing** — overrides are doing real work.")
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument(
        "--case",
        required=True,
        help="A key in `run_icao4444_ollama_first_prototype.CASES`.",
    )
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--base-url", default=proto.DEFAULT_BASE_URL)
    args = parser.parse_args(argv)

    if args.case not in proto.CASES:
        parser.error(
            f"unknown case {args.case!r}. Available: {sorted(proto.CASES.keys())}"
        )
    case = proto.CASES[args.case]
    args.output_dir.mkdir(parents=True, exist_ok=True)

    print(f"[run]  with-overrides: {args.case}", flush=True)
    with_dir = run_with_overrides(case, args.output_dir / "with-overrides", args.base_url)
    print(f"[run]  no-overrides:   {args.case}", flush=True)
    no_dir = run_without_overrides(case, args.output_dir / "no-overrides", args.base_url)

    diff = diff_eligibility(
        with_overrides_dir=with_dir,
        no_overrides_dir=no_dir,
        document_id=case["documentId"],
        section_id=case["caseId"],
    )

    diff_path = args.output_dir / "diff.json"
    diff_path.write_text(json.dumps(diff, indent=2) + "\n", encoding="utf-8")
    summary_path = args.output_dir / "summary.md"
    summary_path.write_text(render_summary(diff), encoding="utf-8")

    print(json.dumps({
        "case": args.case,
        "withCount": diff["withCount"],
        "noCount": diff["noCount"],
        "onlyInWithOverrides": len(diff["onlyInWithOverrides"]),
        "onlyInNoOverrides": len(diff["onlyInNoOverrides"]),
        "loadBearingScore": diff["loadBearingScore"],
        "diffPath": str(diff_path),
        "summaryPath": str(summary_path),
    }, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
