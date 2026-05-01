#!/usr/bin/env python3
"""Regression detector for the Ollama-first lane.

Compares two snapshots of `quality/judgements.csv` and hard-fails on:

  * Any `(documentId, sectionId, claimSha256)` whose `finalState` flipped
    from `accepted` to non-accepted, when models AND `promptVersionSha`
    are unchanged. This is a pure stochastic regression on a known-stable
    candidate — the model spat out a different verdict for the same
    inputs, and overrides did not save us.
  * Section-level `acceptedCount` drop greater than the policy threshold
    (default 20%, configurable via `policy.json`).
  * Any row whose `quoteAuditStatus` is `fail`.

Soft warnings (do not exit non-zero):

  * Override-fire rate jumped > 25% on a section that previously did not
    rely on overrides — the model regressed and the overrides are
    saving us.
  * Override-fire rate dropped to 0% on a section that historically
    needed them — could be a genuine prompt absorption (good) or a
    masked regression (bad). Surface for human review.

Each snapshot is a CSV in the shape produced by `promote_to_registry.py`.
The "current" snapshot is by default the live `quality/judgements.csv`;
the baseline is whichever snapshot you point at via `--baseline`.

Usage:

    python3 research/tools/requirements-spike/check_ollama_first_regressions.py \\
        --baseline research/tools/requirements-spike/quality/snapshots/judgements-2026-04-28.csv \\
        --current  research/tools/requirements-spike/quality/judgements.csv

Exits non-zero on any hard-fail. Soft warnings are printed but do not
affect exit code; pipe through `--fail-on-soft` if you want them to.
"""
from __future__ import annotations

import argparse
import csv
import json
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path
from typing import Any


REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_QUALITY_ROOT = Path(__file__).resolve().parent / "quality"
DEFAULT_REGISTRY_POLICY = Path(__file__).resolve().parent / "registry" / "ollama_first" / "policy.json"


_MODEL_FIELDS: tuple[str, ...] = (
    "structureModel",
    "extractionModel",
    "challengeModel",
    "defenseModel",
    "bundleGateModel",
    "judgeModel",
)

# Prompt stages whose edits can flip a verdict for an already-extracted
# claim. Structure and extraction edits change *which* claims are
# extracted (different `claimSha256` → different join key → claim does
# not survive into a same-key comparison), so they do not need to match
# for a same-key flip to count as stochastic. Defense is informational
# (not gate-acted-on). Therefore only challenge / bundleGate / judge
# edits disqualify a same-key flip from being labeled stochastic.
_VERDICT_AFFECTING_PROMPT_STAGES: tuple[str, ...] = (
    "challengePromptSha",
    "bundleGatePromptSha",
    "judgePromptSha",
)


def load_csv(path: Path) -> list[dict[str, str]]:
    if not path.exists():
        raise SystemExit(f"snapshot CSV not found: {path}")
    with path.open(newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def _key(row: dict[str, str]) -> tuple[str, str, str]:
    return (row.get("documentId", ""), row.get("sectionId", ""), row.get("claimSha256", ""))


def _parse_iso_utc(s: str) -> datetime | None:
    """Parse an ISO-8601 UTC stamp tolerant of `Z` vs `+00:00`. Returns
    None on parse failure rather than raising — the caller falls back to
    string compare in that case so a malformed row doesn't crash the
    detector."""
    if not s:
        return None
    try:
        return datetime.fromisoformat(s.replace("Z", "+00:00"))
    except ValueError:
        return None


def _row_sort_key(row: dict[str, str]) -> tuple[datetime | str, str]:
    """Sort key: parsed UTC timestamp (chronological, suffix-form-agnostic),
    with `promoterRunId` as deterministic tiebreak when two rows share the
    same instant. Without the secondary key, same-second double-promotes
    produce non-deterministic latest-per-key under append order."""
    parsed = _parse_iso_utc(row.get("promoterRunTimestampUtc", ""))
    primary: datetime | str = parsed if parsed is not None else (row.get("promoterRunTimestampUtc") or "")
    return (primary, row.get("promoterRunId") or "")


def _latest_per_key(rows: list[dict[str, str]]) -> dict[tuple[str, str, str], dict[str, str]]:
    """The CSV is append-only and may carry many promotion-runs of the
    same logical candidate. Take the most recent row per key, breaking
    same-instant ties deterministically by `promoterRunId`."""
    by_key: dict[tuple[str, str, str], dict[str, str]] = {}
    for row in rows:
        if not row.get("claimSha256"):
            continue
        k = _key(row)
        existing = by_key.get(k)
        if existing is None or _row_sort_key(row) >= _row_sort_key(existing):
            by_key[k] = row
    return by_key


def _models_match(a: dict[str, str], b: dict[str, str]) -> bool:
    return all(a.get(f, "") == b.get(f, "") for f in _MODEL_FIELDS)


def _prompt_match(a: dict[str, str], b: dict[str, str]) -> bool:
    """The detector treats a flip as 'stochastic' only when the prompts
    that determine the verdict are unchanged. We require equality on
    every verdict-affecting per-stage SHA, AND require that all such
    SHAs are known on both sides (an empty/unknown SHA cannot anchor a
    stable-context claim).

    Falls back to the all-stages `promptVersionSha` only when ALL
    per-stage SHAs are missing on both sides (legacy CSV rows). This is
    a no-precision-loss fallback: legacy data gets the older coarser
    behaviour; current data gets the precise one.
    """
    a_stage_shas = [a.get(col, "") for col in _VERDICT_AFFECTING_PROMPT_STAGES]
    b_stage_shas = [b.get(col, "") for col in _VERDICT_AFFECTING_PROMPT_STAGES]
    a_have_per_stage = any(s and s != "unknown" for s in a_stage_shas)
    b_have_per_stage = any(s and s != "unknown" for s in b_stage_shas)
    if a_have_per_stage and b_have_per_stage:
        for a_sha, b_sha in zip(a_stage_shas, b_stage_shas, strict=True):
            if not a_sha or a_sha == "unknown":
                return False
            if a_sha != b_sha:
                return False
        return True
    # Legacy CSV path: neither side has per-stage SHAs; fall back to the
    # all-stages digest exactly as Phase C did.
    a_all = a.get("promptVersionSha", "")
    return (
        a_all == b.get("promptVersionSha", "")
        and a_all not in ("", "unknown")
    )


def _section_counts(rows_by_key: dict[tuple[str, str, str], dict[str, str]]) -> dict[tuple[str, str], dict[str, int]]:
    counts: dict[tuple[str, str], dict[str, int]] = defaultdict(lambda: {
        "accepted": 0, "pending": 0, "overrideFires": 0, "total": 0,
    })
    for (doc, sec, _), row in rows_by_key.items():
        c = counts[(doc, sec)]
        c["total"] += 1
        if row.get("finalState") == "accepted":
            c["accepted"] += 1
        else:
            c["pending"] += 1
        if row.get("challengerOverridden") == "true" or row.get("judgeOverridden") == "true":
            c["overrideFires"] += 1
    return dict(counts)


def _load_thresholds() -> dict[str, float | int]:
    if not DEFAULT_REGISTRY_POLICY.exists():
        return {"sectionAcceptedDropPercent": 20, "overrideFireRateChangePercent": 25}
    try:
        policy = json.loads(DEFAULT_REGISTRY_POLICY.read_text(encoding="utf-8"))
        regression = policy.get("regression") or {}
        return {
            "sectionAcceptedDropPercent": regression.get(
                "sectionAcceptedCountDropPercentThreshold", 20,
            ),
            "overrideFireRateChangePercent": regression.get(
                "softWarnOverrideFireRateChangePercent", 25,
            ),
        }
    except (json.JSONDecodeError, OSError):
        return {"sectionAcceptedDropPercent": 20, "overrideFireRateChangePercent": 25}


def detect_regressions(
    baseline_rows: list[dict[str, str]],
    current_rows: list[dict[str, str]],
    *,
    accepted_drop_percent: float = 20.0,
    override_change_percent: float = 25.0,
    min_sample_size: int = 10,
) -> dict[str, Any]:
    baseline = _latest_per_key(baseline_rows)
    current = _latest_per_key(current_rows)

    hard_fails: list[dict[str, Any]] = []
    soft_warnings: list[dict[str, Any]] = []

    # Per-candidate flips.
    for key, base_row in baseline.items():
        cur_row = current.get(key)
        if cur_row is None:
            continue
        if base_row.get("finalState") == "accepted" and cur_row.get("finalState") != "accepted":
            stable_context = _models_match(base_row, cur_row) and _prompt_match(base_row, cur_row)
            if stable_context:
                hard_fails.append({
                    "kind": "stochastic_accepted_to_pending",
                    "documentId": key[0],
                    "sectionId": key[1],
                    "claimSha256": key[2],
                    "baselineState": base_row.get("finalState"),
                    "currentState": cur_row.get("finalState"),
                    "currentJudgeDecision": cur_row.get("judgeDecision"),
                    "claimExcerpt": cur_row.get("claimTextExcerpt", "")[:120],
                    "message": (
                        "previously-accepted candidate flipped to non-accepted with "
                        "identical models and promptVersionSha — pure stochastic regression"
                    ),
                })

    # Section-level accepted-count drops.
    base_sec = _section_counts(baseline)
    cur_sec = _section_counts(current)
    for sec_key, base_counts in base_sec.items():
        cur_counts = cur_sec.get(sec_key)
        if cur_counts is None or base_counts["accepted"] == 0:
            continue
        drop = base_counts["accepted"] - cur_counts["accepted"]
        drop_pct = (drop / base_counts["accepted"]) * 100
        if drop_pct > accepted_drop_percent:
            hard_fails.append({
                "kind": "section_accepted_drop",
                "documentId": sec_key[0],
                "sectionId": sec_key[1],
                "baselineAccepted": base_counts["accepted"],
                "currentAccepted": cur_counts["accepted"],
                "dropPercent": round(drop_pct, 1),
                "message": (
                    f"section accepted count dropped {drop_pct:.1f}% (threshold "
                    f"{accepted_drop_percent}%)"
                ),
            })

    # Quote-audit fails are hard-fail only for latest accepted records.
    # Pending/rejected rows may preserve the failed audit as the reason
    # the candidate did not enter the accepted registry; that evidence
    # must remain visible without making the current curated state fail.
    for key, cur_row in current.items():
        if cur_row.get("finalState") == "accepted" and cur_row.get("quoteAuditStatus") == "fail":
            hard_fails.append({
                "kind": "quote_audit_fail",
                "documentId": key[0],
                "sectionId": key[1],
                "claimSha256": key[2],
                "misses": cur_row.get("quoteAuditMisses", "?"),
                "message": "verbatim quote audit failed for a current candidate",
            })

    # Soft warnings on override-fire-rate shifts. A flat absolute-delta
    # threshold misfires on low-volume sections (a 1-of-3 → 0-of-3 shift
    # is 33% absolute but n=3 is noise). Require: minimum sample size
    # AND meaningful absolute delta AND meaningful relative delta. All
    # three reduces false-positive spam on small sections without
    # silencing real regime changes on larger ones.
    for sec_key, base_counts in base_sec.items():
        cur_counts = cur_sec.get(sec_key)
        if cur_counts is None or base_counts["total"] == 0 or cur_counts["total"] == 0:
            continue
        if base_counts["total"] < min_sample_size or cur_counts["total"] < min_sample_size:
            continue
        base_rate = base_counts["overrideFires"] / base_counts["total"]
        cur_rate = cur_counts["overrideFires"] / cur_counts["total"]
        abs_delta_pct = (cur_rate - base_rate) * 100
        # Relative delta uses max(base, cur) as denominator so it is
        # symmetric and finite when one side is zero.
        denominator = max(base_rate, cur_rate)
        rel_delta = abs(cur_rate - base_rate) / denominator if denominator > 0 else 0.0
        if abs(abs_delta_pct) > override_change_percent and rel_delta > 0.5:
            soft_warnings.append({
                "kind": "override_fire_rate_shift",
                "documentId": sec_key[0],
                "sectionId": sec_key[1],
                "baselineRatePct": round(base_rate * 100, 1),
                "currentRatePct": round(cur_rate * 100, 1),
                "deltaPct": round(abs_delta_pct, 1),
                "relativeDelta": round(rel_delta, 2),
                "baselineN": base_counts["total"],
                "currentN": cur_counts["total"],
            })

    return {
        "hardFails": hard_fails,
        "softWarnings": soft_warnings,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument(
        "--baseline",
        type=Path,
        required=True,
        help="Baseline snapshot CSV (typically under quality/snapshots/).",
    )
    parser.add_argument(
        "--current",
        type=Path,
        default=DEFAULT_QUALITY_ROOT / "judgements.csv",
        help="Current judgements CSV (default: live quality/judgements.csv).",
    )
    parser.add_argument(
        "--fail-on-soft",
        action="store_true",
        help="Exit non-zero on soft warnings as well as hard fails.",
    )
    parser.add_argument(
        "--report",
        type=Path,
        default=None,
        help="Write a structured JSON report to this path.",
    )
    args = parser.parse_args(argv)

    thresholds = _load_thresholds()
    baseline_rows = load_csv(args.baseline)
    current_rows = load_csv(args.current)

    result = detect_regressions(
        baseline_rows,
        current_rows,
        accepted_drop_percent=float(thresholds["sectionAcceptedDropPercent"]),
        override_change_percent=float(thresholds["overrideFireRateChangePercent"]),
    )

    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")

    print(f"baseline rows: {len(baseline_rows)}, current rows: {len(current_rows)}")
    print(f"hard fails: {len(result['hardFails'])}, soft warnings: {len(result['softWarnings'])}")
    for hf in result["hardFails"]:
        print(f"  HARD {hf['kind']}: {hf.get('message', '')} | {hf.get('documentId', '')}::{hf.get('sectionId', '')}")
    for sw in result["softWarnings"]:
        print(f"  SOFT {sw['kind']}: {sw['documentId']}::{sw['sectionId']} ({sw.get('baselineRatePct')}% → {sw.get('currentRatePct')}%)")

    exit_code = 1 if result["hardFails"] else 0
    if args.fail_on_soft and result["softWarnings"]:
        exit_code = 1
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
