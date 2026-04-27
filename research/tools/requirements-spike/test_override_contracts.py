#!/usr/bin/env python3
"""Contract tests for the three deterministic post-step override functions.

These exercise each safety condition's fire / decline branches with synthetic
inputs. They do not call any LLM. Run as:

  python3 research/tools/requirements-spike/test_override_contracts.py

Exits non-zero on any assertion failure.
"""
from __future__ import annotations

import sys
from pathlib import Path


# Allow `from run_icao4444_ollama_first_prototype import ...`
sys.path.insert(0, str(Path(__file__).resolve().parent))

from run_icao4444_ollama_first_prototype import (  # noqa: E402
    apply_bundle_gate_override,
    apply_judge_conservatism_override,
    apply_sibling_symmetry_resolution,
    is_authority_consistent,
)


# ── apply_sibling_symmetry_resolution ────────────────────────────────────


def test_sibling_resolution_fires_on_disagreement() -> None:
    """Two candidates citing distinct child branches of the same parent
    with disagreeing scope verdicts get flipped to scope=true."""
    structure = [
        {"itemId": "P", "parentItemId": None},
        {"itemId": "P.a", "parentItemId": "P"},
        {"itemId": "P.b", "parentItemId": "P"},
    ]
    candidates = [
        {"candidateId": "vmc", "sourceItemIds": ["P", "P.a"]},
        {"candidateId": "imc", "sourceItemIds": ["P", "P.b"]},
    ]
    bg = {
        "vmc": {"caseId": "c", "candidateId": "vmc", "scopeComplete": False, "missingDependencies": ["P.b"], "rationale": "..."},
        "imc": {"caseId": "c", "candidateId": "imc", "scopeComplete": True, "missingDependencies": [], "rationale": "..."},
    }
    resolved, audits = apply_sibling_symmetry_resolution(
        candidates=candidates, bundle_gate_results=bg, structure_items=structure,
    )
    assert resolved["vmc"]["scopeComplete"] is True, "VMC should be flipped to true"
    assert resolved["imc"]["scopeComplete"] is True, "IMC should remain true"
    assert resolved["vmc"]["missingDependencies"] == [], "missingDependencies should clear"
    assert len(audits) == 1, "exactly one resolution audit"
    assert audits[0]["siblingParentItemId"] == "P"
    assert {a["candidateId"] for a in audits[0]["flippedCandidates"]} == {"vmc"}


def test_sibling_resolution_declines_on_agreement() -> None:
    """Sibling candidates whose scope verdicts already agree are left alone."""
    structure = [
        {"itemId": "P", "parentItemId": None},
        {"itemId": "P.a", "parentItemId": "P"},
        {"itemId": "P.b", "parentItemId": "P"},
    ]
    candidates = [
        {"candidateId": "vmc", "sourceItemIds": ["P", "P.a"]},
        {"candidateId": "imc", "sourceItemIds": ["P", "P.b"]},
    ]
    bg = {
        "vmc": {"caseId": "c", "candidateId": "vmc", "scopeComplete": True, "missingDependencies": [], "rationale": "..."},
        "imc": {"caseId": "c", "candidateId": "imc", "scopeComplete": True, "missingDependencies": [], "rationale": "..."},
    }
    resolved, audits = apply_sibling_symmetry_resolution(
        candidates=candidates, bundle_gate_results=bg, structure_items=structure,
    )
    assert resolved == bg, "no-op when verdicts agree"
    assert audits == []


def test_sibling_resolution_declines_on_non_siblings() -> None:
    """Two candidates that share no parent are not treated as siblings even
    if they happen to disagree on scope."""
    structure = [
        {"itemId": "P1", "parentItemId": None},
        {"itemId": "P2", "parentItemId": None},
    ]
    candidates = [
        {"candidateId": "a", "sourceItemIds": ["P1"]},
        {"candidateId": "b", "sourceItemIds": ["P2"]},
    ]
    bg = {
        "a": {"caseId": "c", "candidateId": "a", "scopeComplete": False, "missingDependencies": [], "rationale": "..."},
        "b": {"caseId": "c", "candidateId": "b", "scopeComplete": True, "missingDependencies": [], "rationale": "..."},
    }
    resolved, audits = apply_sibling_symmetry_resolution(
        candidates=candidates, bundle_gate_results=bg, structure_items=structure,
    )
    assert resolved["a"]["scopeComplete"] is False, "non-siblings should not be touched"
    assert audits == []


# ── apply_bundle_gate_override (RR-5) ────────────────────────────────────


def _challenge(verdict: str, **extra: object) -> dict[str, object]:
    base: dict[str, object] = {
        "caseId": "c", "candidateId": "x", "verdict": verdict,
        "concerns": ["..."], "sourceQuotes": ["..."],
    }
    base.update(extra)
    return base


def test_bundle_override_fires_on_authority_too_high_with_consistent_modality() -> None:
    """Mixed-modality bundle on authoritative_requirement is consistent
    (mixed includes shall) → override fires."""
    candidate = {"candidateId": "x", "modality": "mixed", "authorityClass": "authoritative_requirement"}
    challenge = _challenge("authority_too_high")
    bg = {"scopeComplete": True}
    out, audit = apply_bundle_gate_override(
        challenge_parsed=challenge, bundle_gate_parsed=bg, candidate=candidate,
    )
    assert out["verdict"] == "supported"
    assert out["concerns"] == []
    assert audit is not None
    assert audit["originalVerdict"] == "authority_too_high"


def test_bundle_override_declines_on_inconsistent_modality() -> None:
    """should clause classified as authoritative_requirement is genuinely
    too high → override stands aside; the challenger's verdict survives."""
    candidate = {"candidateId": "x", "modality": "should", "authorityClass": "authoritative_requirement"}
    challenge = _challenge("authority_too_high")
    bg = {"scopeComplete": True}
    out, audit = apply_bundle_gate_override(
        challenge_parsed=challenge, bundle_gate_parsed=bg, candidate=candidate,
    )
    assert out["verdict"] == "authority_too_high", "real authority discrepancy must survive"
    assert audit is None


def test_bundle_override_declines_on_scope_incomplete() -> None:
    """Scope incomplete → bundle gate has flagged a real issue → override
    stands aside."""
    candidate = {"candidateId": "x", "modality": "mixed", "authorityClass": "authoritative_requirement"}
    challenge = _challenge("authority_too_high")
    bg = {"scopeComplete": False}
    out, audit = apply_bundle_gate_override(
        challenge_parsed=challenge, bundle_gate_parsed=bg, candidate=candidate,
    )
    assert out["verdict"] == "authority_too_high"
    assert audit is None


def test_bundle_override_declines_on_non_authority_verdict() -> None:
    """Override only fires on authority_too_* verdicts — supported,
    overbroad, wrong_split, etc. survive untouched."""
    candidate = {"candidateId": "x", "modality": "mixed", "authorityClass": "authoritative_requirement"}
    bg = {"scopeComplete": True}
    for v in ["supported", "overbroad", "underspecified", "wrong_split", "unsupported_by_source"]:
        out, audit = apply_bundle_gate_override(
            challenge_parsed=_challenge(v), bundle_gate_parsed=bg, candidate=candidate,
        )
        assert out["verdict"] == v, f"verdict {v} must not be overridden"
        assert audit is None, f"verdict {v} must not produce an audit"


def test_authority_consistency_modality_floor() -> None:
    """authoritative_requirement requires shall or mixed; weaker classes
    accept any modality."""
    assert is_authority_consistent("shall", "authoritative_requirement") is True
    assert is_authority_consistent("mixed", "authoritative_requirement") is True
    assert is_authority_consistent("should", "authoritative_requirement") is False
    assert is_authority_consistent("note", "authoritative_requirement") is False
    assert is_authority_consistent("note", "best_practice") is True
    assert is_authority_consistent("note", "operational_guidance") is True
    assert is_authority_consistent("note", "background_support") is True
    assert is_authority_consistent(None, None) is True, "missing fields → safe default"


# ── apply_judge_conservatism_override (RR-8) ─────────────────────────────


def _judge(decision: str, rationale: str = "...") -> dict[str, object]:
    return {
        "caseId": "c", "candidateId": "x", "decision": decision,
        "confidence": "high", "rationale": rationale, "notes": [],
    }


def test_judge_override_fires_on_clean_demotion() -> None:
    """promote candidate + supported challenger + scope=true demoted to
    advisory_only has no basis → override restores accepted."""
    judge = _judge("advisory_only")
    candidate = {"promotionHint": "promote"}
    challenge_for_judge = {"verdict": "supported"}
    bg = {"scopeComplete": True}
    out, audit = apply_judge_conservatism_override(
        judge_parsed=judge, candidate=candidate,
        challenge_for_judge=challenge_for_judge, bundle_gate_parsed=bg,
    )
    assert out["decision"] == "accepted"
    assert audit is not None
    assert audit["originalDecision"] == "advisory_only"


def test_judge_override_declines_on_advisory_hint() -> None:
    """promotionHint=advisory_only → demotion is correct → override stands aside."""
    judge = _judge("advisory_only")
    candidate = {"promotionHint": "advisory_only"}
    challenge_for_judge = {"verdict": "supported"}
    bg = {"scopeComplete": True}
    out, audit = apply_judge_conservatism_override(
        judge_parsed=judge, candidate=candidate,
        challenge_for_judge=challenge_for_judge, bundle_gate_parsed=bg,
    )
    assert out["decision"] == "advisory_only"
    assert audit is None


def test_judge_override_declines_on_unsupported_challenger() -> None:
    """Challenger raised a real concern → judge has basis to demote →
    override stands aside."""
    judge = _judge("advisory_only")
    candidate = {"promotionHint": "promote"}
    challenge_for_judge = {"verdict": "overbroad"}
    bg = {"scopeComplete": True}
    out, audit = apply_judge_conservatism_override(
        judge_parsed=judge, candidate=candidate,
        challenge_for_judge=challenge_for_judge, bundle_gate_parsed=bg,
    )
    assert out["decision"] == "advisory_only"
    assert audit is None


def test_judge_override_declines_on_scope_incomplete() -> None:
    """Bundle gate flagged scope incomplete → judge has basis to demote
    (or bundle) → override stands aside."""
    judge = _judge("advisory_only")
    candidate = {"promotionHint": "promote"}
    challenge_for_judge = {"verdict": "supported"}
    bg = {"scopeComplete": False}
    out, audit = apply_judge_conservatism_override(
        judge_parsed=judge, candidate=candidate,
        challenge_for_judge=challenge_for_judge, bundle_gate_parsed=bg,
    )
    assert out["decision"] == "advisory_only"
    assert audit is None


def test_judge_override_declines_on_non_advisory_decision() -> None:
    """Override only fires on advisory_only demotions. Other decisions
    (accepted, needs_split, needs_bundle, ...) pass through."""
    candidate = {"promotionHint": "promote"}
    challenge_for_judge = {"verdict": "supported"}
    bg = {"scopeComplete": True}
    for d in ["accepted", "needs_split", "needs_bundle", "ambiguous", "unsupported_by_source", "needs_human_review"]:
        out, audit = apply_judge_conservatism_override(
            judge_parsed=_judge(d), candidate=candidate,
            challenge_for_judge=challenge_for_judge, bundle_gate_parsed=bg,
        )
        assert out["decision"] == d, f"decision {d} must not be overridden"
        assert audit is None, f"decision {d} must not produce an audit"


# ── runner ───────────────────────────────────────────────────────────────


TESTS = [
    test_sibling_resolution_fires_on_disagreement,
    test_sibling_resolution_declines_on_agreement,
    test_sibling_resolution_declines_on_non_siblings,
    test_bundle_override_fires_on_authority_too_high_with_consistent_modality,
    test_bundle_override_declines_on_inconsistent_modality,
    test_bundle_override_declines_on_scope_incomplete,
    test_bundle_override_declines_on_non_authority_verdict,
    test_authority_consistency_modality_floor,
    test_judge_override_fires_on_clean_demotion,
    test_judge_override_declines_on_advisory_hint,
    test_judge_override_declines_on_unsupported_challenger,
    test_judge_override_declines_on_scope_incomplete,
    test_judge_override_declines_on_non_advisory_decision,
]


def main() -> int:
    failures: list[tuple[str, BaseException]] = []
    for test in TESTS:
        try:
            test()
        except AssertionError as exc:
            failures.append((test.__name__, exc))
    if failures:
        print(f"{len(failures)} of {len(TESTS)} contract tests FAILED:")
        for name, exc in failures:
            print(f"  - {name}: {exc}")
        return 1
    print(f"all {len(TESTS)} contract tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
