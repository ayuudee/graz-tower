#!/usr/bin/env python3
"""Tests for the Ollama-first quality-gate primitives.

Run as:

  python3 research/tools/requirements-spike/test_quality_gates.py

Exits non-zero on any assertion failure.

Phase A covers:
  * canonical_id stability under cosmetic edits and across reorders.
  * canonical_id distinctness on real claim/quote changes.
  * candidate_schema validators on happy-path and missing-field cases.

Phase B will extend this file with falsification probes per machine gate.
"""
from __future__ import annotations

import csv
import sys
from collections import Counter
from pathlib import Path


sys.path.insert(0, str(Path(__file__).resolve().parent))

from canonical_id import (  # noqa: E402
    canonical_id_for,
    claim_sha256,
    normalize_claim_text,
    normalize_quote,
)
from candidate_schema import (  # noqa: E402
    AUTHORITY_CLASS_BY_FLOOR,
    AUTHORITY_CLASS_VALUES,
    FORMAT_VERSION,
    LIFECYCLE_STATES,
    MODALITY_VALUES,
    SCHEMA_NAME,
    is_authority_consistent,
    validate_pipeline_candidate,
    validate_registry_record,
)
from promote_to_registry import (  # noqa: E402
    JUDGEMENTS_CSV_HEADERS,
    append_csv_rows,
    csv_row_from_registry_record,
    gate_g1_verbatim_quote,
    gate_g2_schema,
    gate_g3_authority_modality,
    gate_g4_override_audit_trail,
    promote_section,
)
from curate_pending_with_gpt import (  # noqa: E402
    append_curator_judgement_row,
    apply_curator_action,
)
from check_ollama_first_regressions import detect_regressions  # noqa: E402
from audit_registry_reproducibility import audit_dry_run  # noqa: E402
from audit_quotes import normalize as normalize_for_quote_audit  # noqa: E402
from audit_overrides_load_bearing import diff_eligibility  # noqa: E402
from build_registry_adequacy_review import (  # noqa: E402
    _source_window,
    choose_record_sample,
    choose_section_sample,
    record_risk_tags,
    section_risk_tags,
)


# ── canonical_id stability ────────────────────────────────────────────────


def _id(claim: str, quotes: list[str], *, document_id: str = "doc-x", section_id: str = "sec-y") -> str:
    return canonical_id_for(
        document_id=document_id,
        section_id=section_id,
        claim_text=claim,
        exact_source_quotes=quotes,
    )


def test_canonical_id_format() -> None:
    canonical = _id("The pilot shall read back.", ["The pilot shall read back."])
    assert canonical.startswith("doc-x::sec-y::"), canonical
    short_hash = canonical.rsplit("::", 1)[1]
    assert len(short_hash) == 16, short_hash
    assert all(c in "0123456789abcdef" for c in short_hash), short_hash


def test_canonical_id_stable_under_whitespace() -> None:
    a = _id("The pilot shall read back.", ["The pilot shall read back."])
    b = _id("  The pilot   shall\nread back.  ", ["The pilot shall read back."])
    assert a == b, (a, b)


def test_canonical_id_stable_under_case() -> None:
    a = _id("The pilot shall read back.", ["The pilot shall read back."])
    b = _id("THE PILOT SHALL READ BACK.", ["The pilot shall read back."])
    assert a == b, (a, b)


def test_canonical_id_stable_under_outer_quote_strip() -> None:
    a = _id("The pilot shall read back.", ["The pilot shall read back."])
    b = _id('"The pilot shall read back."', ["The pilot shall read back."])
    assert a == b, (a, b)


def test_canonical_id_stable_under_quote_reorder() -> None:
    a = _id("X", ["alpha", "beta", "gamma"])
    b = _id("X", ["gamma", "alpha", "beta"])
    assert a == b, (a, b)


def test_canonical_id_stable_under_quote_dup() -> None:
    a = _id("X", ["alpha", "beta"])
    b = _id("X", ["alpha", "beta", "alpha"])
    assert a == b, (a, b)


def test_canonical_id_distinct_on_claim_change() -> None:
    a = _id("The pilot shall read back.", ["q"])
    b = _id("The pilot may read back.", ["q"])
    assert a != b, (a, b)


def test_canonical_id_distinct_on_quote_change() -> None:
    a = _id("X", ["alpha"])
    b = _id("X", ["beta"])
    assert a != b, (a, b)


def test_canonical_id_distinct_across_documents() -> None:
    a = _id("X", ["q"], document_id="doc-a")
    b = _id("X", ["q"], document_id="doc-b")
    assert a != b, (a, b)


def test_canonical_id_distinct_across_sections() -> None:
    a = _id("X", ["q"], section_id="sec-a")
    b = _id("X", ["q"], section_id="sec-b")
    assert a != b, (a, b)


def test_canonical_id_rejects_empty_claim() -> None:
    try:
        _id("", ["q"])
    except ValueError:
        return
    raise AssertionError("expected ValueError on empty claim")


def test_canonical_id_rejects_empty_quote_list() -> None:
    try:
        _id("X", [])
    except ValueError:
        return
    raise AssertionError("expected ValueError on empty quote list")


def test_canonical_id_rejects_whitespace_only_quotes() -> None:
    try:
        _id("X", ["  ", "\t"])
    except ValueError:
        return
    raise AssertionError("expected ValueError when all quotes normalise empty")


def test_claim_sha256_join_key() -> None:
    a = claim_sha256("The pilot shall read back.")
    b = claim_sha256("  THE pilot SHALL  read back.  ")
    assert a == b, (a, b)
    c = claim_sha256("The pilot may read back.")
    assert a != c, (a, c)


def test_normalize_claim_strips_outer_unicode_quotes() -> None:
    assert normalize_claim_text("“abc”") == "abc"
    assert normalize_claim_text("‘abc’") == "abc"


def test_normalize_quote_preserves_case() -> None:
    assert normalize_quote("Pilot Shall") == "Pilot Shall"


# ── candidate_schema validators ───────────────────────────────────────────


_HAPPY_PIPELINE_CANDIDATE = {
    "candidateId": "x",
    "sourceItemIds": ["item_1"],
    "claimText": "Pilots shall read back.",
    "authorityClass": "best_practice",
    "modality": "shall",
    "requirementKind": "phraseology_rule",
    "testability": "sim_executable",
    "verificationMode": "deterministic_test",
    "promotionHint": "promote",
    "exactSourceQuotes": ["Pilots shall read back."],
    "supportingAttempts": ["attempt_1"],
    "consistencyClass": "consistent",
    "rationale": "Direct restatement.",
}


def test_validate_pipeline_candidate_happy_path() -> None:
    reasons = validate_pipeline_candidate(_HAPPY_PIPELINE_CANDIDATE)
    assert reasons == [], reasons


def test_validate_pipeline_candidate_missing_field() -> None:
    bad = {**_HAPPY_PIPELINE_CANDIDATE}
    del bad["rationale"]
    reasons = validate_pipeline_candidate(bad)
    assert any("missing required field: rationale" in r for r in reasons), reasons


def test_validate_pipeline_candidate_empty_rationale() -> None:
    bad = {**_HAPPY_PIPELINE_CANDIDATE, "rationale": "   "}
    reasons = validate_pipeline_candidate(bad)
    assert any("rationale is empty" in r for r in reasons), reasons


def test_validate_pipeline_candidate_bad_modality() -> None:
    bad = {**_HAPPY_PIPELINE_CANDIDATE, "modality": "ought"}
    reasons = validate_pipeline_candidate(bad)
    assert any("modality 'ought' not in" in r for r in reasons), reasons


def test_validate_pipeline_candidate_must_is_alias_for_shall() -> None:
    """Phase E finding: UK CAA SafetySense docs say 'must' for the same
    operative meaning as ICAO Annex 'shall'. The schema accepts both.
    Falsification probe: a `must`-modality candidate must validate clean
    AND must satisfy G3 against `authoritative_requirement` ceiling."""
    must_candidate = {**_HAPPY_PIPELINE_CANDIDATE, "modality": "must"}
    reasons = validate_pipeline_candidate(must_candidate)
    assert reasons == [], reasons
    assert is_authority_consistent("must", "authoritative_requirement"), (
        "`must` must satisfy AUTHORITY_CLASS_BY_FLOOR for authoritative_requirement"
    )


def test_validate_pipeline_candidate_bad_authority_class() -> None:
    bad = {**_HAPPY_PIPELINE_CANDIDATE, "authorityClass": "law"}
    reasons = validate_pipeline_candidate(bad)
    assert any("authorityClass 'law' not in" in r for r in reasons), reasons


def test_validate_pipeline_candidate_no_quotes() -> None:
    bad = {**_HAPPY_PIPELINE_CANDIDATE, "exactSourceQuotes": ["", "  "]}
    reasons = validate_pipeline_candidate(bad)
    assert any("exactSourceQuotes has no non-empty entries" in r for r in reasons), reasons


_HAPPY_REGISTRY_RECORD = {
    "schemaName": SCHEMA_NAME,
    "formatVersion": FORMAT_VERSION,
    "canonicalId": "doc::sec::abc1234567890def",
    "documentId": "doc",
    "sectionId": "sec",
    "claimText": "Pilots shall read back.",
    "rationale": "Direct restatement.",
    "modality": "shall",
    "authorityClass": "best_practice",
    "exactSourceQuotes": ["Pilots shall read back."],
    "provenance": {
        "sourcePath": "research/txt/x.txt",
        "sourceSha256": "0" * 64,
        "startLine": 1,
        "endLine": 2,
    },
    "ingestionRun": {
        "runId": "run_x",
        "runStartedAt": "2026-04-25T00:00:00Z",
        "models": {"structure": "qwen3.6:35b-a3b"},
        "promptVersion": "2026-04-28-v1",
        "originalCandidateId": "x",
    },
    "audit": {
        "verbatimQuoteCheck": {"status": "pass", "method": "windowed_substring_match"},
        "rationaleNonEmpty": True,
    },
    "gate": {
        "overallStatus": "pass",
        "judge": {"decision": "accepted", "confidence": "high"},
    },
    "lifecycle": {"state": "accepted", "promotedAt": "2026-04-25T00:00:00Z"},
}


def test_validate_registry_record_happy_path() -> None:
    reasons = validate_registry_record(_HAPPY_REGISTRY_RECORD)
    assert reasons == [], reasons


def test_validate_registry_record_missing_provenance_field() -> None:
    bad = {
        **_HAPPY_REGISTRY_RECORD,
        "provenance": {k: v for k, v in _HAPPY_REGISTRY_RECORD["provenance"].items() if k != "sourceSha256"},
    }
    reasons = validate_registry_record(bad)
    assert any("provenance missing required field: sourceSha256" in r for r in reasons), reasons


def test_validate_registry_record_bad_lifecycle_state() -> None:
    bad = {**_HAPPY_REGISTRY_RECORD, "lifecycle": {"state": "limbo"}}
    reasons = validate_registry_record(bad)
    assert any("lifecycle.state 'limbo' not in" in r for r in reasons), reasons


def test_validate_registry_record_accepted_requires_gate_pass() -> None:
    bad = {
        **_HAPPY_REGISTRY_RECORD,
        "gate": {"overallStatus": "fail", "judge": {"decision": "accepted"}},
    }
    reasons = validate_registry_record(bad)
    assert any(
        "lifecycle.state=accepted requires gate.overallStatus=pass" in r for r in reasons
    ), reasons


def test_validate_registry_record_bad_quote_audit_status() -> None:
    bad = {
        **_HAPPY_REGISTRY_RECORD,
        "audit": {"verbatimQuoteCheck": {"status": "maybe"}, "rationaleNonEmpty": True},
    }
    reasons = validate_registry_record(bad)
    assert any("audit.verbatimQuoteCheck.status 'maybe' not in" in r for r in reasons), reasons


# ── authority/modality consistency ────────────────────────────────────────


def test_authority_consistency_authoritative_only_shall_or_mixed() -> None:
    assert is_authority_consistent("shall", "authoritative_requirement")
    assert is_authority_consistent("mixed", "authoritative_requirement")
    assert not is_authority_consistent("should", "authoritative_requirement")
    assert not is_authority_consistent("note", "authoritative_requirement")


def test_authority_consistency_lower_floors_admit_all_modalities() -> None:
    for ac in ("operational_guidance", "best_practice", "background_support"):
        for m in MODALITY_VALUES:
            assert is_authority_consistent(m, ac), (m, ac)


def test_authority_consistency_unknown_authority_class_treated_consistent() -> None:
    assert is_authority_consistent("shall", "totally_made_up")


def test_authority_consistency_missing_authority_class_treated_consistent() -> None:
    assert is_authority_consistent("shall", None)
    assert is_authority_consistent("shall", "")


def test_authority_class_floor_table_complete() -> None:
    """Every authority class in the enum has a floor entry."""
    assert set(AUTHORITY_CLASS_BY_FLOOR.keys()) == set(AUTHORITY_CLASS_VALUES)


# ── G1 verbatim quote audit ───────────────────────────────────────────────


_TINY_SOURCE = (
    "The pilot shall read back the clearance.\n"
    "Speed and altitude are part of the readback.\n"
)


def test_g1_passes_when_quote_present_verbatim() -> None:
    candidate = {"exactSourceQuotes": ["The pilot shall read back the clearance."]}
    passed, audit = gate_g1_verbatim_quote(candidate, source_text=_TINY_SOURCE)
    assert passed, audit
    assert audit["status"] == "pass"
    assert audit["misses"] == []


def test_g1_passes_under_whitespace_drift() -> None:
    candidate = {"exactSourceQuotes": ["The pilot   shall\nread back\tthe clearance."]}
    passed, audit = gate_g1_verbatim_quote(candidate, source_text=_TINY_SOURCE)
    assert passed, audit


def test_g1_fires_on_invented_quote() -> None:
    """Falsification probe: a synthetic quote that is NOT in the source
    must make G1 fail. If this test starts passing, G1 is dead."""
    candidate = {
        "exactSourceQuotes": ["The controller shall recite the alphabet backwards."],
    }
    passed, audit = gate_g1_verbatim_quote(candidate, source_text=_TINY_SOURCE)
    assert not passed, audit
    assert audit["status"] == "fail"
    assert audit["misses"], audit


def test_g1_fires_on_empty_quote_list() -> None:
    passed, audit = gate_g1_verbatim_quote(
        {"exactSourceQuotes": []}, source_text=_TINY_SOURCE
    )
    assert not passed
    assert audit["status"] == "fail"


def test_g1_fires_on_whitespace_only_quote() -> None:
    passed, audit = gate_g1_verbatim_quote(
        {"exactSourceQuotes": ["   "]}, source_text=_TINY_SOURCE
    )
    assert not passed
    assert any(m.get("reason") == "empty or non-string" for m in audit["misses"]), audit


# ── G2 schema-shape ───────────────────────────────────────────────────────


def test_g2_passes_on_complete_candidate() -> None:
    passed, audit = gate_g2_schema(_HAPPY_PIPELINE_CANDIDATE)
    assert passed, audit
    assert audit["status"] == "pass"


def test_g2_fires_on_missing_rationale() -> None:
    """Falsification probe: a candidate with rationale missing entirely
    must make G2 fail."""
    bad = {k: v for k, v in _HAPPY_PIPELINE_CANDIDATE.items() if k != "rationale"}
    passed, audit = gate_g2_schema(bad)
    assert not passed, audit
    assert any("missing required field: rationale" in r for r in audit["reasons"])


def test_g2_fires_on_bad_authority_class() -> None:
    bad = {**_HAPPY_PIPELINE_CANDIDATE, "authorityClass": "law_of_the_land"}
    passed, audit = gate_g2_schema(bad)
    assert not passed
    assert any("authorityClass 'law_of_the_land' not in" in r for r in audit["reasons"])


# ── G3 authority/modality consistency ─────────────────────────────────────


def test_g3_passes_when_consistent() -> None:
    candidate = {"modality": "shall", "authorityClass": "authoritative_requirement"}
    passed, audit = gate_g3_authority_modality(candidate, challenge_override=None)
    assert passed
    assert audit["floorConsistent"] is True


def test_g3_passes_when_consistent_with_override_fired() -> None:
    """When override fires AND candidate is genuinely consistent, G3
    passes (the normal case). overrideFired is informational."""
    candidate = {"modality": "shall", "authorityClass": "authoritative_requirement"}
    override_audit = {
        "originalVerdict": "authority_too_high",
        "originalConcerns": [],
        "candidateModality": "shall",
        "candidateAuthorityClass": "authoritative_requirement",
        "bundleGateScopeComplete": True,
        "reason": "overridden",
        "originalSourceQuotes": [],
    }
    passed, audit = gate_g3_authority_modality(
        candidate, challenge_override=override_audit
    )
    assert passed, audit
    assert audit["overrideFired"] is True
    assert audit["floorConsistent"] is True
    assert audit["invariantViolated"] is False


def test_g3_fires_when_override_fires_on_inconsistent_candidate() -> None:
    """Falsification probe: if a future regression in
    `apply_bundle_gate_override` lets the override fire on an
    inconsistent candidate, G3 must catch that — invariantViolated=true,
    gate fails. This is the load-bearing assertion that prevents an
    override refactor from silently admitting bad records.

    The audit dict must echo (modality, authorityClass) so a debugger
    can recover which fields are at odds without re-reading the
    original candidate.
    """
    candidate = {"modality": "should", "authorityClass": "authoritative_requirement"}
    override_audit = {
        "originalVerdict": "authority_too_high",
        "originalConcerns": [],
        "candidateModality": "should",
        "candidateAuthorityClass": "authoritative_requirement",
        "bundleGateScopeComplete": True,
        "reason": "overridden",
        "originalSourceQuotes": [],
    }
    passed, audit = gate_g3_authority_modality(
        candidate, challenge_override=override_audit
    )
    assert not passed, audit
    assert audit["invariantViolated"] is True
    assert "override fired on inconsistent candidate" in audit["reason"]
    # Pin debug-info: a future refactor that drops these fields must regress this test.
    assert audit["modality"] == "should"
    assert audit["authorityClass"] == "authoritative_requirement"
    assert audit["floorConsistent"] is False
    assert audit["overrideFired"] is True


def test_g3_fires_on_inconsistent_without_override() -> None:
    """Falsification probe: authority_class=authoritative_requirement with
    modality=note is a real inconsistency. With no override audit record,
    G3 must hard-fail."""
    candidate = {"modality": "note", "authorityClass": "authoritative_requirement"}
    passed, audit = gate_g3_authority_modality(candidate, challenge_override=None)
    assert not passed
    assert audit["status"] == "fail"
    assert audit["invariantViolated"] is False
    assert "violates AUTHORITY_CLASS_BY_FLOOR" in audit["reason"]


def test_format_version_mismatch_is_hard_fail() -> None:
    """Pre-1.0 policy: a record carrying a non-current formatVersion
    must fail validation. Forces explicit migration scripts when the
    version bumps."""
    bad = {**_HAPPY_REGISTRY_RECORD, "formatVersion": "1999-01-01-v0"}
    reasons = validate_registry_record(bad)
    assert any("formatVersion must be" in r for r in reasons), reasons


def test_canonical_id_casefold_handles_german_eszett() -> None:
    """`.casefold()` lowers ß → ss; `.lower()` does not. Locale-safe
    normalisation matters for any future German source content (H01)."""
    # Same logical text, one with eszett, one with ss equivalent.
    a = canonical_id_for(
        document_id="d", section_id="s",
        claim_text="Straße",
        exact_source_quotes=["q"],
    )
    b = canonical_id_for(
        document_id="d", section_id="s",
        claim_text="strasse",
        exact_source_quotes=["q"],
    )
    assert a == b, (a, b)


# ── G4 override audit-trail integrity ─────────────────────────────────────


_HAPPY_CHALLENGE_OVERRIDE = {
    "originalVerdict": "authority_too_high",
    "originalConcerns": ["bla"],
    "originalSourceQuotes": ["q"],
    "candidateModality": "shall",
    "candidateAuthorityClass": "best_practice",
    "bundleGateScopeComplete": True,
    "reason": "overridden",
}


_HAPPY_JUDGE_OVERRIDE = {
    "originalDecision": "advisory_only",
    "originalConfidence": "medium",
    "reason": "advisory upgrade",
}


def test_g4_passes_when_no_overrides_fired() -> None:
    passed, audit = gate_g4_override_audit_trail(
        challenge_override=None, judge_override=None,
    )
    assert passed
    assert audit["status"] == "pass"


def test_g4_passes_on_complete_audit_records() -> None:
    passed, audit = gate_g4_override_audit_trail(
        challenge_override=_HAPPY_CHALLENGE_OVERRIDE,
        judge_override=_HAPPY_JUDGE_OVERRIDE,
    )
    assert passed, audit


def test_g4_fires_when_challenge_override_missing_reason() -> None:
    """Falsification probe: if a future refactor of
    apply_bundle_gate_override drops the `reason` field, G4 must fire."""
    bad = {k: v for k, v in _HAPPY_CHALLENGE_OVERRIDE.items() if k != "reason"}
    passed, audit = gate_g4_override_audit_trail(
        challenge_override=bad, judge_override=None,
    )
    assert not passed
    assert any("challengeOverride missing field: reason" in m for m in audit["misses"])


def test_g4_fires_when_judge_override_missing_original_decision() -> None:
    bad = {k: v for k, v in _HAPPY_JUDGE_OVERRIDE.items() if k != "originalDecision"}
    passed, audit = gate_g4_override_audit_trail(
        challenge_override=None, judge_override=bad,
    )
    assert not passed
    assert any("judgeOverride missing field: originalDecision" in m for m in audit["misses"])


# ── promote_section idempotency + conflict detection ─────────────────────


import json  # noqa: E402
import shutil  # noqa: E402
import tempfile  # noqa: E402


_SAMPLE_TMP_DIR = Path("/tmp/overnight-shakedown/safetysense22/readbacks")


def _has_sample_run() -> bool:
    return (_SAMPLE_TMP_DIR / "judged_candidates.json").exists()


def test_promote_section_idempotent_byte_equal_on_no_op_rerun() -> None:
    """Running promote_section twice on the same /tmp source must produce
    byte-identical files in the registry. This is what makes registry
    diffs commit-ready instead of churn."""
    if not _has_sample_run():
        return  # sample run absent — skip
    with tempfile.TemporaryDirectory() as scratch:
        registry = Path(scratch) / "ollama_first"
        result_a = promote_section(
            section_dir=_SAMPLE_TMP_DIR,
            document_id="safetysense22-extracted",
            section_id="readbacks",
            family_id="safetysense22_readback_family",
            registry_root=registry,
            run_id="test_run_A",
        )
        snapshot_a: dict[str, str] = {}
        for path in sorted(registry.rglob("*.json")):
            if path.name == "_section.json":
                continue
            snapshot_a[str(path.relative_to(registry))] = path.read_text(encoding="utf-8")

        result_b = promote_section(
            section_dir=_SAMPLE_TMP_DIR,
            document_id="safetysense22-extracted",
            section_id="readbacks",
            family_id="safetysense22_readback_family",
            registry_root=registry,
            run_id="test_run_B",
        )
        snapshot_b: dict[str, str] = {}
        for path in sorted(registry.rglob("*.json")):
            if path.name == "_section.json":
                continue
            snapshot_b[str(path.relative_to(registry))] = path.read_text(encoding="utf-8")

        assert result_a["landed"] == result_b["landed"], (result_a, result_b)
        assert result_a["pending"] == result_b["pending"], (result_a, result_b)
        assert snapshot_a.keys() == snapshot_b.keys(), (
            sorted(snapshot_a.keys()), sorted(snapshot_b.keys())
        )
        for rel in snapshot_a:
            assert snapshot_a[rel] == snapshot_b[rel], (
                f"file {rel} drifted between runs — re-promotion is not idempotent"
            )


def test_promote_section_detects_source_sha_drift() -> None:
    """If the source file mutates between the original promotion and a
    re-promote (different sourceSha256 in the existing record), the
    promoter must surface a `source_drift` conflict and skip the write
    rather than silently re-stamping the record with the new SHA."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = Path(scratch) / "ollama_first"
        first = promote_section(
            section_dir=_SAMPLE_TMP_DIR,
            document_id="safetysense22-extracted",
            section_id="readbacks",
            family_id="safetysense22_readback_family",
            registry_root=registry,
            run_id="test_run_first",
        )
        landed = list(first["landed"])
        if not landed:
            return
        # Mutate the recorded sourceSha256 in the existing registry file
        # to simulate the source content having drifted.
        cid = landed[0]
        record_path = registry / "candidates" / "safetysense22-extracted" / "readbacks" / f"{cid}.json"
        record = json.loads(record_path.read_text(encoding="utf-8"))
        record["provenance"]["sourceSha256"] = "0" * 64
        record_path.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")

        second = promote_section(
            section_dir=_SAMPLE_TMP_DIR,
            document_id="safetysense22-extracted",
            section_id="readbacks",
            family_id="safetysense22_readback_family",
            registry_root=registry,
            run_id="test_run_second",
        )
        assert any(c["kind"] == "source_drift" for c in second["conflicts"]), second["conflicts"]


def test_promote_section_detects_legacy_record_without_source_sha() -> None:
    """If the existing record predates sourceSha256 capture (legacy: the
    field is missing or null), the promoter must NOT silently backfill
    the new SHA — that would let a mutated source file slide in undetected.
    Surface as `source_drift_unknown` and skip."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = Path(scratch) / "ollama_first"
        first = promote_section(
            section_dir=_SAMPLE_TMP_DIR,
            document_id="safetysense22-extracted",
            section_id="readbacks",
            family_id="safetysense22_readback_family",
            registry_root=registry,
            run_id="test_run_first",
        )
        landed = list(first["landed"])
        if not landed:
            return
        # Strip sourceSha256 from the existing record to simulate a
        # legacy record that pre-dates SHA capture.
        cid = landed[0]
        record_path = registry / "candidates" / "safetysense22-extracted" / "readbacks" / f"{cid}.json"
        record = json.loads(record_path.read_text(encoding="utf-8"))
        record["provenance"].pop("sourceSha256", None)
        record_path.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")

        second = promote_section(
            section_dir=_SAMPLE_TMP_DIR,
            document_id="safetysense22-extracted",
            section_id="readbacks",
            family_id="safetysense22_readback_family",
            registry_root=registry,
            run_id="test_run_second",
        )
        kinds = {c["kind"] for c in second["conflicts"]}
        assert "source_drift_unknown" in kinds, second["conflicts"]


def test_promote_section_collects_both_cross_bucket_and_drift() -> None:
    """When the same canonicalId is in the wrong bucket AND the source
    has drifted, the curator needs to see both signals — fixing only
    one would let the other slide silently."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = Path(scratch) / "ollama_first"
        first = promote_section(
            section_dir=_SAMPLE_TMP_DIR,
            document_id="safetysense22-extracted",
            section_id="readbacks",
            family_id="safetysense22_readback_family",
            registry_root=registry,
            run_id="test_run_first",
        )
        landed = list(first["landed"])
        if not landed:
            return
        cid = landed[0]
        # Move to pending/ to set up cross-bucket, AND mutate sourceSha256
        # to set up source-drift.
        src = registry / "candidates" / "safetysense22-extracted" / "readbacks" / f"{cid}.json"
        dst = registry / "pending" / "safetysense22-extracted" / "readbacks" / f"{cid}.json"
        dst.parent.mkdir(parents=True, exist_ok=True)
        record = json.loads(src.read_text(encoding="utf-8"))
        record["provenance"]["sourceSha256"] = "f" * 64
        dst.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")
        src.unlink()

        second = promote_section(
            section_dir=_SAMPLE_TMP_DIR,
            document_id="safetysense22-extracted",
            section_id="readbacks",
            family_id="safetysense22_readback_family",
            registry_root=registry,
            run_id="test_run_second",
        )
        kinds = {c["kind"] for c in second["conflicts"]}
        assert "cross_bucket" in kinds, second["conflicts"]
        assert "source_drift" in kinds, second["conflicts"]


def test_promote_section_raises_on_flipped_candidates_schema_drift() -> None:
    """If `bundle_gate_sibling_resolution.json` is non-empty but no
    candidateIds are recoverable (upstream schema renamed a field), the
    promoter must raise rather than silently mark every candidate as
    sibling-resolution-not-fired. Same class as the formatVersion bug
    we just fixed."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        # Copy the sample section into a scratch dir so we can mutate
        # the audit file without disturbing the real data.
        scratch_section = Path(scratch) / "readbacks_drift"
        scratch_section.mkdir()
        for child in _SAMPLE_TMP_DIR.iterdir():
            if child.is_file():
                target = scratch_section / child.name
                target.write_bytes(child.read_bytes())
            elif child.is_dir():
                shutil.copytree(child, scratch_section / child.name)
        # Inject a malformed sibling-resolution audit: non-empty but with
        # the candidateId field renamed to a key the promoter doesn't know.
        bad_audit = [{
            "siblingParentItemId": "P",
            "groupMembers": ["a", "b"],
            "scopeBefore": {"a": False, "b": True},
            "flippedTo": "scopeComplete=true",
            "flippedCandidates": [{"renamedFromCandidateId": "a"}],
            "reason": "synthetic",
        }]
        (scratch_section / "bundle_gate_sibling_resolution.json").write_text(
            json.dumps(bad_audit, indent=2), encoding="utf-8",
        )

        registry = Path(scratch) / "ollama_first"
        try:
            promote_section(
                section_dir=scratch_section,
                document_id="safetysense22-extracted",
                section_id="readbacks_drift",
                family_id="safetysense22_readback_family",
                registry_root=registry,
                run_id="test_run_drift",
            )
        except ValueError as exc:
            assert "audit shape has drifted" in str(exc), exc
            return
        raise AssertionError(
            "expected ValueError on flippedCandidates schema drift; promoter "
            "silently passed instead"
        )


def test_promote_section_detects_cross_bucket_conflict() -> None:
    """If the same canonicalId already lives in candidates/ but a re-run
    wants to write it to pending/ (or vice-versa), the promoter must NOT
    silently leave a stale copy. It must record the conflict and skip
    the write."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = Path(scratch) / "ollama_first"
        first = promote_section(
            section_dir=_SAMPLE_TMP_DIR,
            document_id="safetysense22-extracted",
            section_id="readbacks",
            family_id="safetysense22_readback_family",
            registry_root=registry,
            run_id="test_run_first",
        )
        landed = list(first["landed"])
        if not landed:
            return  # nothing accepted — skip
        # Manually move the landed file into pending/ to simulate a
        # state-flip scenario (e.g. judge regressed to advisory_only on
        # a later /tmp run). Now re-promoting from the same /tmp data
        # must surface a cross-bucket conflict because the source still
        # claims accepted.
        for cid in landed:
            src = registry / "candidates" / "safetysense22-extracted" / "readbacks" / f"{cid}.json"
            dst = registry / "pending" / "safetysense22-extracted" / "readbacks" / f"{cid}.json"
            dst.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(src), str(dst))

        second = promote_section(
            section_dir=_SAMPLE_TMP_DIR,
            document_id="safetysense22-extracted",
            section_id="readbacks",
            family_id="safetysense22_readback_family",
            registry_root=registry,
            run_id="test_run_second",
        )
        assert second["conflicts"], (
            "expected cross_bucket conflict to be surfaced after manually moving "
            "an accepted record into pending/"
        )
        assert any(c["kind"] == "cross_bucket" for c in second["conflicts"]), second["conflicts"]


# ── regression detector ──────────────────────────────────────────────────


def _csv_row(**overrides: str) -> dict[str, str]:
    base = {
        "promoterRunId": "r1",
        "promoterRunTimestampUtc": "2026-04-28T00:00:00Z",
        "documentId": "d1", "sectionId": "s1",
        "claimSha256": "claim-A",
        "finalState": "accepted",
        "judgeDecision": "accepted",
        "challengerOverridden": "false", "judgeOverridden": "false",
        "structureModel": "M1", "extractionModel": "M1", "challengeModel": "M1",
        "defenseModel": "M1", "bundleGateModel": "M1", "judgeModel": "M1",
        "promptVersionSha": "p1",
        "claimTextExcerpt": "X",
        "quoteAuditStatus": "pass", "quoteAuditMisses": "0",
    }
    base.update(overrides)
    return base


def test_regression_detector_flags_stochastic_flip() -> None:
    """Same models, same promptVersionSha, accepted → pending must
    hard-fail. This is the falsification probe that proves the detector
    is alive."""
    base = [_csv_row()]
    cur = [_csv_row(finalState="pending", judgeDecision="advisory_only",
                    promoterRunTimestampUtc="2026-04-28T01:00:00Z")]
    result = detect_regressions(base, cur)
    assert any(h["kind"] == "stochastic_accepted_to_pending" for h in result["hardFails"]), result


def test_regression_detector_ignores_flip_when_prompt_changed() -> None:
    """If promptVersionSha changed between baseline and current, an
    accept→pending flip is expected (we changed the contract). Soft
    warning at most, not a hard fail."""
    base = [_csv_row(promptVersionSha="p1")]
    cur = [_csv_row(finalState="pending", judgeDecision="advisory_only",
                    promoterRunTimestampUtc="2026-04-28T01:00:00Z",
                    promptVersionSha="p2")]
    result = detect_regressions(base, cur)
    assert not any(h["kind"] == "stochastic_accepted_to_pending" for h in result["hardFails"]), result


def test_regression_detector_flags_section_drop() -> None:
    """Section accepted-count dropping more than the threshold must
    hard-fail."""
    base = [
        _csv_row(claimSha256=f"c{i}") for i in range(10)
    ]
    cur = [_csv_row(claimSha256="c0")]
    result = detect_regressions(base, cur, accepted_drop_percent=20.0)
    assert any(h["kind"] == "section_accepted_drop" for h in result["hardFails"]), result


def test_regression_detector_flags_quote_audit_fail() -> None:
    """An accepted current row with quoteAuditStatus=fail is a hard fail."""
    base = [_csv_row()]
    cur = [_csv_row(quoteAuditStatus="fail", quoteAuditMisses="1",
                    promoterRunTimestampUtc="2026-04-28T01:00:00Z")]
    result = detect_regressions(base, cur)
    assert any(h["kind"] == "quote_audit_fail" for h in result["hardFails"]), result


def test_regression_detector_allows_rejected_quote_audit_fail() -> None:
    """Rejected rows may preserve the failed quote audit as the reason
    for rejection. Once curator rows are visible, that evidence must not
    make the current registry state fail."""
    base = [_csv_row()]
    cur = [_csv_row(
        finalState="rejected",
        quoteAuditStatus="fail",
        quoteAuditMisses="1",
        promoterRunTimestampUtc="2026-04-28T01:00:00Z",
        eventSource="curator",
        curationAction="reject",
    )]
    result = detect_regressions(base, cur)
    assert not any(h["kind"] == "quote_audit_fail" for h in result["hardFails"]), result


def test_regression_detector_clean_when_no_change() -> None:
    base = [_csv_row()]
    cur = [_csv_row(promoterRunTimestampUtc="2026-04-28T01:00:00Z")]
    result = detect_regressions(base, cur)
    assert result["hardFails"] == [], result
    assert result["softWarnings"] == [], result


def test_regression_detector_takes_latest_per_key() -> None:
    """The CSV is append-only; multiple rows per key may exist. The
    detector must look at only the latest row per (doc, section,
    claimSha256)."""
    cur = [
        _csv_row(finalState="pending",
                 promoterRunTimestampUtc="2026-04-28T00:00:00Z"),
        _csv_row(finalState="accepted",
                 promoterRunTimestampUtc="2026-04-28T01:00:00Z"),
    ]
    base = [_csv_row()]
    result = detect_regressions(base, cur)
    # Latest is accepted → no flip → no hard fail.
    assert result["hardFails"] == [], result


def test_regression_detector_iso_z_vs_offset_compare() -> None:
    """`Z` and `+00:00` represent the same instant but lex-compare
    differently. Latest-per-key must treat them as equal under string
    chronology and tiebreak by promoterRunId."""
    cur = [
        _csv_row(finalState="accepted",
                 promoterRunTimestampUtc="2026-04-28T01:00:00Z",
                 promoterRunId="run_A"),
        _csv_row(finalState="pending",
                 judgeDecision="advisory_only",
                 promoterRunTimestampUtc="2026-04-28T01:00:00+00:00",
                 promoterRunId="run_B"),
    ]
    base = [_csv_row(promoterRunId="run_0")]
    result = detect_regressions(base, cur)
    # Same instant; tiebreak picks run_B (lex on promoterRunId), which
    # is pending. With same models + promptVersionSha, that's a
    # stochastic flip → hard fail. Without the Z-normalisation fix, the
    # OLD behaviour would lex-pick run_A and miss the flip.
    assert any(h["kind"] == "stochastic_accepted_to_pending" for h in result["hardFails"]), result


def test_regression_detector_soft_warning_requires_min_sample_size() -> None:
    """A 1-of-3 → 0-of-3 override-fire shift looks like a 33% delta but
    n=3 is noise. The detector must require min sample size before
    surfacing a soft warning."""
    base = [_csv_row(claimSha256=f"c{i}", challengerOverridden="true" if i == 0 else "false")
            for i in range(3)]
    cur = [_csv_row(claimSha256=f"c{i}", challengerOverridden="false",
                    promoterRunTimestampUtc="2026-04-28T01:00:00Z")
           for i in range(3)]
    result = detect_regressions(base, cur, min_sample_size=10)
    assert result["softWarnings"] == [], result


def test_regression_detector_soft_warning_fires_on_large_section_shift() -> None:
    """With sufficient sample size and both abs+rel delta exceeded, the
    soft warning fires."""
    base = [_csv_row(claimSha256=f"c{i}", challengerOverridden="true")
            for i in range(20)]
    cur = [_csv_row(claimSha256=f"c{i}", challengerOverridden="false",
                    promoterRunTimestampUtc="2026-04-28T01:00:00Z")
           for i in range(20)]
    result = detect_regressions(base, cur, min_sample_size=10,
                                override_change_percent=25.0)
    assert any(s["kind"] == "override_fire_rate_shift" for s in result["softWarnings"]), result


def test_regression_detector_ignores_flip_when_judge_prompt_changed() -> None:
    """If the judge prompt changed (per-stage SHA differs), a flip is
    expected and must NOT be flagged stochastic. Falsifies the case
    where the all-stages SHA is sufficient — judge-only edits would
    have been masked under Phase C if not for the per-stage path."""
    base = [_csv_row(
        challengePromptSha="C1", bundleGatePromptSha="B1", judgePromptSha="J1",
        structurePromptSha="S1", extractionPromptSha="E1", defensePromptSha="D1",
    )]
    cur = [_csv_row(
        finalState="pending", judgeDecision="advisory_only",
        promoterRunTimestampUtc="2026-04-28T01:00:00Z",
        challengePromptSha="C1", bundleGatePromptSha="B1", judgePromptSha="J2",
        structurePromptSha="S1", extractionPromptSha="E1", defensePromptSha="D1",
    )]
    result = detect_regressions(base, cur)
    assert not any(h["kind"] == "stochastic_accepted_to_pending" for h in result["hardFails"]), result


def test_regression_detector_flags_flip_when_only_defense_prompt_changed() -> None:
    """Defense is informational, not gate-acted-on. A flip with same
    challenge/bundleGate/judge SHAs and only the defense SHA changed is
    still a stochastic regression — the verdict-affecting prompts didn't
    move. This is the precision improvement Phase C' delivered: under
    the all-stages-only logic this would NOT have been flagged."""
    base = [_csv_row(
        challengePromptSha="C1", bundleGatePromptSha="B1", judgePromptSha="J1",
        structurePromptSha="S1", extractionPromptSha="E1", defensePromptSha="D1",
    )]
    cur = [_csv_row(
        finalState="pending", judgeDecision="advisory_only",
        promoterRunTimestampUtc="2026-04-28T01:00:00Z",
        challengePromptSha="C1", bundleGatePromptSha="B1", judgePromptSha="J1",
        structurePromptSha="S1", extractionPromptSha="E1", defensePromptSha="D2",
    )]
    result = detect_regressions(base, cur)
    assert any(h["kind"] == "stochastic_accepted_to_pending" for h in result["hardFails"]), result


def test_regression_detector_legacy_csv_uses_all_stages_sha() -> None:
    """Legacy rows (no per-stage SHAs) fall back to all-stages comparison.
    Mixed rows (one side has per-stage, other doesn't) is treated as
    'cannot anchor stable' and the flip is NOT flagged stochastic —
    safer default."""
    base = [_csv_row(promptVersionSha="P1")]
    cur = [_csv_row(
        finalState="pending", judgeDecision="advisory_only",
        promoterRunTimestampUtc="2026-04-28T01:00:00Z",
        promptVersionSha="P1",
    )]
    result = detect_regressions(base, cur)
    # Both sides legacy, all-stages SHA equal → flip is stochastic.
    assert any(h["kind"] == "stochastic_accepted_to_pending" for h in result["hardFails"]), result


def test_append_csv_rows_migrates_legacy_header_and_defaults_event_source() -> None:
    """A live pre-curation CSV has the old promoter-only header. The
    first append after RR-12 must migrate that header in place, preserve
    old rows, and default newly appended promoter rows to
    eventSource=promoter."""
    legacy_headers = [
        header for header in JUDGEMENTS_CSV_HEADERS
        if not header.startswith("curation") and header != "eventSource"
    ]
    with tempfile.TemporaryDirectory() as scratch:
        csv_path = Path(scratch) / "judgements.csv"
        with csv_path.open("w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=legacy_headers)
            writer.writeheader()
            writer.writerow({
                **{key: "" for key in legacy_headers},
                "documentId": "legacy-doc",
                "sectionId": "legacy-sec",
                "claimSha256": "legacy-claim",
                "finalState": "pending",
            })

        appended = append_csv_rows(
            csv_path,
            [{
                "documentId": "new-doc",
                "sectionId": "new-sec",
                "claimSha256": "new-claim",
                "finalState": "accepted",
            }],
            promoter_run_id="run_new",
            promoter_run_timestamp_utc="2026-04-29T00:00:00Z",
        )
        assert appended == 1
        with csv_path.open(newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            rows = list(reader)
        assert reader.fieldnames == list(JUDGEMENTS_CSV_HEADERS), reader.fieldnames
        assert len(rows) == 2, rows
        assert rows[0]["documentId"] == "legacy-doc"
        assert rows[0]["eventSource"] == ""
        assert rows[1]["documentId"] == "new-doc"
        assert rows[1]["eventSource"] == "promoter"


def test_curator_judgement_row_uses_final_registry_state() -> None:
    """After a curation action moves a record, the CSV row must be built
    from the final bucket record rather than the stale pending object.
    This is the RR-12 load-bearing path."""
    with tempfile.TemporaryDirectory() as scratch:
        root = Path(scratch)
        registry = root / "registry"
        quality = root / "quality"
        record = json.loads(json.dumps({
            **_HAPPY_REGISTRY_RECORD,
            "familyId": "family",
            "canonicalId": "doc::sec::abc1234567890def",
            "lifecycle": {
                "state": "pending",
                "promotedAt": "2026-04-28T00:00:00Z",
            },
            "gate": {
                "overallStatus": "pass",
                "judge": {"decision": "needs_human_review", "confidence": "medium"},
                "challenger": {"verdict": "supported", "overridden": False},
                "bundleGate": {"scopeComplete": True},
                "siblingResolution": {"fired": False},
                "judgeOverride": {"fired": False},
            },
        }))
        pending_path = registry / "pending" / "doc" / "sec" / f"{record['canonicalId']}.json"
        pending_path.parent.mkdir(parents=True)
        pending_path.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")

        parsed = {
            "action": "reject",
            "reasoning": "The candidate is unsupported by the source.",
            "confidence": "high",
        }
        action_audit = apply_curator_action(
            pending_path=pending_path,
            pending_record=record,
            parsed=parsed,
            registry_root=registry,
            dry_run=False,
        )
        audit_path = quality / "curation" / "curate_test" / f"{record['canonicalId']}.json"
        audit_path.parent.mkdir(parents=True)
        appended = append_curator_judgement_row(
            quality_root=quality,
            registry_root=registry,
            pending_path=pending_path,
            pending_record=record,
            parsed=parsed,
            action_audit=action_audit,
            run_id="curate_test",
            model="gpt-test",
            audit_path=audit_path,
            timestamp_utc="2026-04-29T00:00:00Z",
        )
        assert appended == 1
        with (quality / "judgements.csv").open(newline="", encoding="utf-8") as f:
            rows = list(csv.DictReader(f))
        assert len(rows) == 1, rows
        row = rows[0]
        assert row["eventSource"] == "curator", row
        assert row["curationAction"] == "reject", row
        assert row["curationModel"] == "gpt-test", row
        assert row["finalState"] == "rejected", row
        assert row["curationAuditPath"] == (
            f"curation/curate_test/{record['canonicalId']}.json"
        )


def test_regression_detector_takes_latest_curator_row() -> None:
    """A curator row with a later timestamp must supersede an earlier
    promoter row for the same claim key. Otherwise RR-12 remains
    unfixed from the detector's point of view."""
    base = [_csv_row()]
    current = [
        _csv_row(
            finalState="pending",
            judgeDecision="advisory_only",
            promoterRunTimestampUtc="2026-04-29T00:00:00Z",
            eventSource="promoter",
        ),
        _csv_row(
            finalState="accepted",
            judgeDecision="advisory_only",
            promoterRunTimestampUtc="2026-04-29T01:00:00Z",
            eventSource="curator",
            curationAction="promote",
        ),
    ]
    result = detect_regressions(base, current)
    assert result["hardFails"] == [], result


# ── RR-13 adequacy review sampling ────────────────────────────────────────


def test_adequacy_record_risk_tags_cover_curated_and_gate_risks() -> None:
    record = {
        **_HAPPY_REGISTRY_RECORD,
        "authorityClass": "background_support",
        "promotionHint": "support_only",
        "testability": "partially_executable",
        "audit": {
            "curationApplied": {"action": "manual_split_accept"},
            "verbatimQuoteCheck": {"status": "fail"},
            "authorityModalityCheck": {"invariantViolated": True},
        },
        "gate": {
            "challenger": {"overridden": True},
            "judgeOverride": {"fired": True},
            "judge": {"decision": "advisory_only"},
        },
        "lifecycle": {"state": "rejected"},
    }
    tags = set(record_risk_tags(record, "rejected", ["dialogue_examples"]))
    expected = {
        "authority_invariant_violation",
        "challenger_overridden",
        "curated",
        "dialogue_examples",
        "executable_or_partial",
        "judge_demoted",
        "judge_overridden",
        "manual_split",
        "non_accepted",
        "quote_audit_fail",
        "support_only",
    }
    assert expected <= tags, tags


def _adequacy_record_item(
    *,
    canonical_id: str,
    bucket: str,
    document_id: str,
    section_id: str,
    authority_class: str,
    modality: str,
    risk_tags: list[str],
) -> dict[str, object]:
    lifecycle_state = {
        "candidates": "accepted",
        "pending": "pending",
        "rejected": "rejected",
    }[bucket]
    return {
        "bucket": bucket,
        "path": f"registry/ollama_first/{bucket}/{canonical_id}.json",
        "riskTags": risk_tags,
        "record": {
            "canonicalId": canonical_id,
            "documentId": document_id,
            "sectionId": section_id,
            "authorityClass": authority_class,
            "modality": modality,
            "requirementKind": "phraseology_rule",
            "testability": "sim_executable",
            "lifecycle": {"state": lifecycle_state},
        },
    }


def test_adequacy_record_sample_is_deterministic_and_stratifies_lifecycle() -> None:
    records = [
        _adequacy_record_item(
            canonical_id=f"doc-a::sec-{index}::000000000000000{index}",
            bucket="candidates",
            document_id="doc-a",
            section_id=f"sec-{index}",
            authority_class="authoritative_requirement",
            modality="shall",
            risk_tags=["nested_conditions"] if index == 0 else [],
        )
        for index in range(4)
    ] + [
        _adequacy_record_item(
            canonical_id="doc-b::sec-0::1000000000000000",
            bucket="candidates",
            document_id="doc-b",
            section_id="sec-0",
            authority_class="best_practice",
            modality="should",
            risk_tags=["dialogue_examples"],
        ),
        _adequacy_record_item(
            canonical_id="doc-c::sec-0::2000000000000000",
            bucket="rejected",
            document_id="doc-c",
            section_id="sec-0",
            authority_class="operational_guidance",
            modality="may",
            risk_tags=["quote_audit_fail"],
        ),
        _adequacy_record_item(
            canonical_id="doc-d::sec-0::3000000000000000",
            bucket="pending",
            document_id="doc-d",
            section_id="sec-0",
            authority_class="background_support",
            modality="note",
            risk_tags=["support_only"],
        ),
    ]
    sample_a = choose_record_sample(records, 6, "rr13-test-seed")
    sample_b = choose_record_sample(records, 6, "rr13-test-seed")
    assert [item["record"]["canonicalId"] for item in sample_a] == [
        item["record"]["canonicalId"] for item in sample_b
    ]
    assert len(sample_a) == 6, sample_a
    assert {"candidates", "pending", "rejected"} <= {item["bucket"] for item in sample_a}
    assert len({item["record"]["documentId"] for item in sample_a}) >= 3, sample_a


def test_adequacy_section_sample_covers_source_shape_risks() -> None:
    assert {"bilingual", "dialogue_examples", "table_or_figure"} <= set(
        section_risk_tags({"notes": "Bilingual German/English dialogue example with table."})
    )
    sections = [
        {
            "documentId": "doc-a",
            "sectionId": "readback",
            "defaultAuthorityCeiling": "authoritative_requirement",
            "riskTags": ["nested_conditions", "parent_child"],
        },
        {
            "documentId": "doc-b",
            "sectionId": "dialogue",
            "defaultAuthorityCeiling": "operational_guidance",
            "riskTags": ["dialogue_examples", "bilingual"],
        },
        {
            "documentId": "doc-c",
            "sectionId": "table",
            "defaultAuthorityCeiling": "best_practice",
            "riskTags": ["table_or_figure"],
        },
        {
            "documentId": "doc-d",
            "sectionId": "plain",
            "defaultAuthorityCeiling": "background_support",
            "riskTags": [],
        },
    ]
    sample = choose_section_sample(
        sections,
        4,
        "rr13-section-test-seed",
        Counter({
            ("doc-a", "readback"): 12,
            ("doc-b", "dialogue"): 1,
            ("doc-c", "table"): 0,
            ("doc-d", "plain"): 3,
        }),
    )
    covered_docs = {section["documentId"] for section in sample}
    covered_risks = {
        risk
        for section in sample
        for risk in section.get("riskTags", [])
    }
    assert covered_docs == {"doc-a", "doc-b", "doc-c", "doc-d"}, sample
    assert {"bilingual", "dialogue_examples", "nested_conditions", "table_or_figure"} <= covered_risks
    assert any(section.get("recordCount", 0) >= 10 for section in sample), sample


def test_adequacy_source_window_uses_newline_line_numbers() -> None:
    """PDF extracts contain form-feed page breaks. Manifest line numbers
    are newline-based, so source windows must not let form-feed
    characters shift later line numbers."""
    with tempfile.TemporaryDirectory() as scratch:
        source = Path(scratch) / "source.txt"
        source.write_text("one\ntwo\fstill line two\nthree\n", encoding="utf-8")
        window = _source_window(source, 2, 3)
    assert window == "2: two\fstill line two\n3: three", repr(window)


def test_prompt_version_shas_per_stage_are_distinct() -> None:
    """Each prompt-builder stage gets its own SHA. Editing one stage's
    prompt must change only that stage's SHA, leaving the others stable."""
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import run_icao4444_ollama_first_prototype as proto  # noqa: PLC0415
    shas = proto.PROMPT_VERSION_SHAS_BY_STAGE
    expected_stages = {"structure", "extraction", "challenge", "defense", "bundleGate", "judge"}
    assert set(shas.keys()) == expected_stages, shas.keys()
    # Each SHA is a 64-char hex string (sha256 hexdigest).
    for stage, sha in shas.items():
        assert len(sha) == 64, (stage, sha)
        assert all(c in "0123456789abcdef" for c in sha), (stage, sha)
    # Stage SHAs must differ — the prompts are different texts.
    assert len(set(shas.values())) == len(expected_stages), shas
    # All-stages SHA must differ from any individual stage's SHA.
    assert proto.PROMPT_VERSION_SHA not in shas.values()


def test_llm_stage_schema_error_is_stage_specific() -> None:
    """Valid JSON with the wrong top-level shape must fail as an LLM
    schema problem, not as an unhelpful Python TypeError downstream."""
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import run_icao4444_ollama_first_prototype as proto  # noqa: PLC0415

    original_post = proto.post_ollama_chat

    def fake_post(**_: object) -> dict[str, object]:
        return {"model": "fake", "content": '["not", "an", "object"]'}

    proto.post_ollama_chat = fake_post  # type: ignore[assignment]
    try:
        try:
            proto.call_ollama_chat(
                base_url="http://example.invalid",
                model="fake-model",
                system_prompt="system",
                user_prompt="user",
                temperature=0.0,
                num_predict=16,
                num_ctx=128,
                timeout_seconds=1,
                stage="judge:cand_x",
                json_repair_attempts=0,
                required_fields=["caseId", "candidateId"],
            )
        except proto.OllamaSchemaError as exc:
            assert "judge:cand_x" in str(exc)
            assert "top-level JSON value is list" in str(exc)
        else:
            raise AssertionError("expected OllamaSchemaError")
    finally:
        proto.post_ollama_chat = original_post  # type: ignore[assignment]


def test_llm_stage_schema_normalizes_single_object_array() -> None:
    """A model sometimes wraps the requested object in a one-item array.
    That normalization is deterministic and audited rather than silent."""
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import run_icao4444_ollama_first_prototype as proto  # noqa: PLC0415

    original_post = proto.post_ollama_chat

    def fake_post(**_: object) -> dict[str, object]:
        return {
            "model": "fake",
            "content": json.dumps([{"caseId": "c", "candidateId": "x"}]),
        }

    proto.post_ollama_chat = fake_post  # type: ignore[assignment]
    try:
        result = proto.call_ollama_chat(
            base_url="http://example.invalid",
            model="fake-model",
            system_prompt="system",
            user_prompt="user",
            temperature=0.0,
            num_predict=16,
            num_ctx=128,
            timeout_seconds=1,
            stage="judge:cand_x",
            json_repair_attempts=0,
            required_fields=["caseId", "candidateId"],
        )
    finally:
        proto.post_ollama_chat = original_post  # type: ignore[assignment]

    assert result["parsed"] == {"caseId": "c", "candidateId": "x"}
    assert result["schemaNormalization"]["kind"] == "singleObjectArray"
    assert result["schemaRepairApplied"] is False


# ── reproducibility audit (dry-run) ───────────────────────────────────────


def _build_one_section_registry(scratch: Path) -> Path:
    """Build a small synthetic registry rooted at `scratch/ollama_first`
    with one valid record under candidates/ for testing dry-run audit."""
    if not _has_sample_run():
        return Path()
    registry = scratch / "ollama_first"
    promote_section(
        section_dir=_SAMPLE_TMP_DIR,
        document_id="safetysense22-extracted",
        section_id="readbacks",
        family_id="safetysense22_readback_family",
        registry_root=registry,
        run_id="repro_test_setup",
    )
    return registry


def test_audit_dry_run_passes_on_clean_registry() -> None:
    """A freshly-promoted registry must round-trip cleanly: every
    canonicalId re-derives to itself, every filename matches, every
    directory segment matches."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = _build_one_section_registry(Path(scratch))
        report = audit_dry_run(registry)
        assert report["status"] == "pass", report
        assert report["totalMismatches"] == 0, report
        assert report["totalRecordsAudited"] >= 1, report


def test_audit_dry_run_catches_edited_claim_text() -> None:
    """Falsification probe: editing claim text without re-deriving the
    canonicalId must surface as `canonical_id_recompute_mismatch`. This
    is the core round-trip invariant — without it, the registry is just
    a directory of opaque blobs."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = _build_one_section_registry(Path(scratch))
        # Find the one landed record and rewrite its claim text.
        candidate = next(
            p for p in (registry / "candidates").rglob("*.json")
            if p.name != "_section.json"
        )
        record = json.loads(candidate.read_text(encoding="utf-8"))
        record["claimText"] = "FORGED CLAIM that was never in the source"
        candidate.write_text(json.dumps(record, indent=2) + "\n", encoding="utf-8")

        report = audit_dry_run(registry)
        assert report["status"] == "fail", report
        kinds = {m["kind"] for m in report["mismatches"]}
        assert "canonical_id_recompute_mismatch" in kinds, report


def test_audit_dry_run_catches_renamed_file_as_unexpected() -> None:
    """Falsification probe: a file rename that drifts the filename
    out of canonical-id shape must be caught as `unexpected_file`. The
    audit must NOT silently ignore renamed real records — that would be
    a 'no corners cut' violation."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = _build_one_section_registry(Path(scratch))
        candidate = next(
            p for p in (registry / "candidates").rglob("*.json")
            if p.name != "_section.json"
        )
        renamed = candidate.with_name("renamed-by-mistake.json")
        candidate.rename(renamed)
        report = audit_dry_run(registry)
        assert report["status"] == "fail", report
        kinds = {m["kind"] for m in report["mismatches"]}
        assert "unexpected_file" in kinds, report


def test_audit_dry_run_catches_filename_drift_within_canonical_shape() -> None:
    """Falsification probe: a rename that KEEPS canonical-id shape but
    flips the hex suffix must trip filename_canonical_id_mismatch (the
    file LOOKS like a record by shape, but its filename disagrees with
    its recorded canonicalId)."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = _build_one_section_registry(Path(scratch))
        candidate = next(
            p for p in (registry / "candidates").rglob("*.json")
            if p.name != "_section.json"
        )
        # Build a new canonical-shaped name with a different hex suffix.
        # The recorded canonicalId inside the file stays the same.
        original_stem = candidate.stem
        prefix, _old_hex = original_stem.rsplit("::", 1)
        forged_name = f"{prefix}::deadbeefcafef00d.json"
        renamed = candidate.with_name(forged_name)
        candidate.rename(renamed)
        report = audit_dry_run(registry)
        assert report["status"] == "fail", report
        kinds = {m["kind"] for m in report["mismatches"]}
        assert "filename_canonical_id_mismatch" in kinds, report


def test_audit_dry_run_catches_directory_drift() -> None:
    """Falsification probe: moving a record into the wrong section
    directory must surface a directory_sectionId_mismatch."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = _build_one_section_registry(Path(scratch))
        candidate = next(
            p for p in (registry / "candidates").rglob("*.json")
            if p.name != "_section.json"
        )
        wrong_section = registry / "candidates" / "safetysense22-extracted" / "wrong-section"
        wrong_section.mkdir(parents=True, exist_ok=True)
        moved = wrong_section / candidate.name
        shutil.move(str(candidate), str(moved))
        report = audit_dry_run(registry)
        assert report["status"] == "fail", report
        kinds = {m["kind"] for m in report["mismatches"]}
        assert "directory_sectionId_mismatch" in kinds, report


def test_audit_dry_run_handles_unreadable_file() -> None:
    """A malformed JSON file with canonical-id shape must surface as
    `unreadable` rather than crash the audit. The audit must continue
    on the rest of the registry."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = _build_one_section_registry(Path(scratch))
        bad_dir = registry / "candidates" / "safetysense22-extracted" / "readbacks"
        # Use canonical-id shape so the audit treats it as a record
        # candidate and exercises the JSON-parse path.
        bad_path = bad_dir / "safetysense22-extracted::readbacks::deadbeefcafef00d.json"
        bad_path.write_text("{ invalid json {", encoding="utf-8")
        report = audit_dry_run(registry)
        assert report["status"] == "fail", report
        assert any(m["kind"] == "unreadable" for m in report["mismatches"]), report


def test_audit_dry_run_flags_non_record_non_sidecar_as_unexpected() -> None:
    """A file that's neither canonical-id-shaped nor `_`-prefixed must
    surface as `unexpected_file` — no silent drops on registry contents
    that don't follow the conventions."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = _build_one_section_registry(Path(scratch))
        sec_dir = registry / "candidates" / "safetysense22-extracted" / "readbacks"
        (sec_dir / "stray-debug.json").write_text("{}", encoding="utf-8")
        report = audit_dry_run(registry)
        kinds = {m["kind"] for m in report["mismatches"]}
        assert "unexpected_file" in kinds, report


def test_quote_audit_normalize_removes_pdf_layout_controls() -> None:
    """PDF extraction sometimes leaves non-whitespace C0 controls adjacent
    to visible text. Quote matching should ignore that layout artifact while
    preserving ordinary whitespace normalization."""
    source = "\x07Stourton Ground,\nBIGJET 347"
    quote = "Stourton Ground, BIGJET 347"
    assert quote in normalize_for_quote_audit(source)


def test_audit_dry_run_returns_empty_for_empty_registry() -> None:
    """A registry with no records yet (e.g. the freshly-committed empty
    skeleton) audits clean — nothing to round-trip, so vacuously pass."""
    with tempfile.TemporaryDirectory() as scratch:
        empty_registry = Path(scratch) / "empty"
        empty_registry.mkdir()
        report = audit_dry_run(empty_registry)
        assert report["status"] == "pass", report
        assert report["totalRecordsAudited"] == 0, report


def test_audit_full_imports_resolve() -> None:
    """`audit_full` lazy-imports `run_pipeline`, `build_arg_parser`, and
    `promote_section`. Tests don't run the full Ollama path, so an
    import rename in a callee module would silently rot the wedge.
    Force the imports to resolve here so a rename surfaces in CI."""
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from run_icao4444_ollama_first_prototype import (  # noqa: PLC0415,F401
        ROOT,
        build_arg_parser,
        run_pipeline,
    )
    from promote_to_registry import (  # noqa: PLC0415,F401
        promote_section as _ps,
    )


def test_audit_dry_run_ignores_foreign_sidecars() -> None:
    """Positive-predicate filter must reject anything not shaped like
    a canonical-id record. Future sidecars (`_metadata.json`,
    `_index.json`, …) and editor noise (`.swp`, `.bak`) must NOT be
    audited as records — that would crash the audit with a misleading
    `cannot_derive_canonical_id`."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        registry = _build_one_section_registry(Path(scratch))
        sec_dir = registry / "candidates" / "safetysense22-extracted" / "readbacks"
        # Drop a stray sidecar — different name than _section.json so
        # the OLD skip-list would have failed; the new positive predicate
        # ignores it because it doesn't match the canonical-id shape.
        (sec_dir / "_future_metadata.json").write_text(
            '{"hello": "world"}', encoding="utf-8",
        )
        (sec_dir / "stray.swp").write_text("noise", encoding="utf-8")
        report = audit_dry_run(registry)
        assert report["status"] == "pass", report
        # Audit count must equal the real records, NOT include the sidecar.
        from canonical_id import canonical_id_for as _c  # noqa: PLC0415,F401
        real_records = sum(
            1 for p in (registry / "candidates").rglob("*.json")
            if "::" in p.stem and p.stem.split("::")[-1] != ""
        )
        assert report["totalRecordsAudited"] == real_records, (
            report, real_records,
        )


# ── override load-bearing audit (offline) ─────────────────────────────────


def test_override_audit_diff_is_empty_on_identical_inputs() -> None:
    """Sanity: two pointers to the same /tmp section produce identical
    eligibility sets, so the diff is empty. If this test fails, the
    audit's diff logic is asymmetric or non-deterministic — both would
    invalidate Phase F's whole purpose."""
    if not _has_sample_run():
        return
    diff = diff_eligibility(
        with_overrides_dir=_SAMPLE_TMP_DIR,
        no_overrides_dir=_SAMPLE_TMP_DIR,
        document_id="safetysense22-extracted",
        section_id="readbacks",
    )
    assert diff["onlyInWithOverrides"] == [], diff
    assert diff["onlyInNoOverrides"] == [], diff
    assert diff["loadBearingScore"] == 0.0, diff


def test_override_audit_diff_inconclusive_when_both_empty() -> None:
    """If both runs produced zero eligible candidates, the verdict must
    be `inconclusive`, not `sunset_candidate`. Otherwise an Ollama
    outage masquerades as 'overrides aren't load-bearing'."""
    with tempfile.TemporaryDirectory() as scratch:
        empty_a = Path(scratch) / "a"
        empty_b = Path(scratch) / "b"
        empty_a.mkdir()
        empty_b.mkdir()
        diff = diff_eligibility(
            with_overrides_dir=empty_a,
            no_overrides_dir=empty_b,
            document_id="d", section_id="s",
        )
        assert diff["verdict"] == "inconclusive", diff
        assert diff["loadBearingScore"] == 0.0, diff


def test_override_audit_diff_detects_disjoint_eligibility() -> None:
    """Falsification probe: if the two scratch dirs produce disjoint
    eligibility sets (e.g. one has accepted candidates, the other has
    none), the diff must flag overrides as fully load-bearing
    (loadBearingScore close to 1.0)."""
    if not _has_sample_run():
        return
    with tempfile.TemporaryDirectory() as scratch:
        # Build a synthetic "no-overrides" section by copying the real
        # one but rewriting judged_candidates.json so every judge
        # decision is a non-accepted demotion. Eligibility set is empty;
        # the with-overrides set still contains the originally accepted
        # candidate(s); diff should be entirely "only in with".
        empty_section = Path(scratch) / "empty"
        empty_section.mkdir()
        for child in _SAMPLE_TMP_DIR.iterdir():
            if child.is_file():
                target = empty_section / child.name
                target.write_bytes(child.read_bytes())
            elif child.is_dir():
                shutil.copytree(child, empty_section / child.name)
        judged_path = empty_section / "judged_candidates.json"
        judged = json.loads(judged_path.read_text(encoding="utf-8"))
        for entry in judged:
            entry["judgeForRecord"] = {"decision": "advisory_only", "confidence": "low"}
            entry["judge"] = entry["judgeForRecord"]
        judged_path.write_text(json.dumps(judged, indent=2), encoding="utf-8")

        diff = diff_eligibility(
            with_overrides_dir=_SAMPLE_TMP_DIR,
            no_overrides_dir=empty_section,
            document_id="safetysense22-extracted",
            section_id="readbacks",
        )
        # The with-overrides set is non-empty; the no-overrides set is
        # empty. Diff is fully one-sided.
        if diff["withCount"] > 0:
            assert len(diff["onlyInWithOverrides"]) == diff["withCount"], diff
            assert diff["noCount"] == 0, diff
            assert diff["loadBearingScore"] == 1.0, diff


# ── runner ────────────────────────────────────────────────────────────────


TESTS = [
    test_canonical_id_format,
    test_canonical_id_stable_under_whitespace,
    test_canonical_id_stable_under_case,
    test_canonical_id_stable_under_outer_quote_strip,
    test_canonical_id_stable_under_quote_reorder,
    test_canonical_id_stable_under_quote_dup,
    test_canonical_id_distinct_on_claim_change,
    test_canonical_id_distinct_on_quote_change,
    test_canonical_id_distinct_across_documents,
    test_canonical_id_distinct_across_sections,
    test_canonical_id_rejects_empty_claim,
    test_canonical_id_rejects_empty_quote_list,
    test_canonical_id_rejects_whitespace_only_quotes,
    test_claim_sha256_join_key,
    test_normalize_claim_strips_outer_unicode_quotes,
    test_normalize_quote_preserves_case,
    test_validate_pipeline_candidate_happy_path,
    test_validate_pipeline_candidate_missing_field,
    test_validate_pipeline_candidate_empty_rationale,
    test_validate_pipeline_candidate_bad_modality,
    test_validate_pipeline_candidate_must_is_alias_for_shall,
    test_validate_pipeline_candidate_bad_authority_class,
    test_validate_pipeline_candidate_no_quotes,
    test_validate_registry_record_happy_path,
    test_validate_registry_record_missing_provenance_field,
    test_validate_registry_record_bad_lifecycle_state,
    test_validate_registry_record_accepted_requires_gate_pass,
    test_validate_registry_record_bad_quote_audit_status,
    test_authority_consistency_authoritative_only_shall_or_mixed,
    test_authority_consistency_lower_floors_admit_all_modalities,
    test_authority_consistency_unknown_authority_class_treated_consistent,
    test_authority_consistency_missing_authority_class_treated_consistent,
    test_authority_class_floor_table_complete,
    test_g1_passes_when_quote_present_verbatim,
    test_g1_passes_under_whitespace_drift,
    test_g1_fires_on_invented_quote,
    test_g1_fires_on_empty_quote_list,
    test_g1_fires_on_whitespace_only_quote,
    test_g2_passes_on_complete_candidate,
    test_g2_fires_on_missing_rationale,
    test_g2_fires_on_bad_authority_class,
    test_g3_passes_when_consistent,
    test_g3_passes_when_consistent_with_override_fired,
    test_g3_fires_when_override_fires_on_inconsistent_candidate,
    test_g3_fires_on_inconsistent_without_override,
    test_format_version_mismatch_is_hard_fail,
    test_canonical_id_casefold_handles_german_eszett,
    test_g4_passes_when_no_overrides_fired,
    test_g4_passes_on_complete_audit_records,
    test_g4_fires_when_challenge_override_missing_reason,
    test_g4_fires_when_judge_override_missing_original_decision,
    test_promote_section_idempotent_byte_equal_on_no_op_rerun,
    test_promote_section_detects_source_sha_drift,
    test_promote_section_detects_legacy_record_without_source_sha,
    test_promote_section_collects_both_cross_bucket_and_drift,
    test_promote_section_raises_on_flipped_candidates_schema_drift,
    test_promote_section_detects_cross_bucket_conflict,
    test_regression_detector_flags_stochastic_flip,
    test_regression_detector_ignores_flip_when_prompt_changed,
    test_regression_detector_flags_section_drop,
    test_regression_detector_flags_quote_audit_fail,
    test_regression_detector_allows_rejected_quote_audit_fail,
    test_regression_detector_clean_when_no_change,
    test_regression_detector_takes_latest_per_key,
    test_regression_detector_iso_z_vs_offset_compare,
    test_regression_detector_soft_warning_requires_min_sample_size,
    test_regression_detector_soft_warning_fires_on_large_section_shift,
    test_regression_detector_ignores_flip_when_judge_prompt_changed,
    test_regression_detector_flags_flip_when_only_defense_prompt_changed,
    test_regression_detector_legacy_csv_uses_all_stages_sha,
    test_append_csv_rows_migrates_legacy_header_and_defaults_event_source,
    test_curator_judgement_row_uses_final_registry_state,
    test_regression_detector_takes_latest_curator_row,
    test_adequacy_record_risk_tags_cover_curated_and_gate_risks,
    test_adequacy_record_sample_is_deterministic_and_stratifies_lifecycle,
    test_adequacy_section_sample_covers_source_shape_risks,
    test_adequacy_source_window_uses_newline_line_numbers,
    test_prompt_version_shas_per_stage_are_distinct,
    test_llm_stage_schema_error_is_stage_specific,
    test_llm_stage_schema_normalizes_single_object_array,
    test_audit_dry_run_passes_on_clean_registry,
    test_audit_dry_run_catches_edited_claim_text,
    test_audit_dry_run_catches_renamed_file_as_unexpected,
    test_audit_dry_run_catches_filename_drift_within_canonical_shape,
    test_audit_dry_run_catches_directory_drift,
    test_audit_dry_run_handles_unreadable_file,
    test_audit_dry_run_flags_non_record_non_sidecar_as_unexpected,
    test_quote_audit_normalize_removes_pdf_layout_controls,
    test_audit_dry_run_returns_empty_for_empty_registry,
    test_audit_full_imports_resolve,
    test_audit_dry_run_ignores_foreign_sidecars,
    test_override_audit_diff_is_empty_on_identical_inputs,
    test_override_audit_diff_inconclusive_when_both_empty,
    test_override_audit_diff_detects_disjoint_eligibility,
]


def main() -> int:
    failures: list[tuple[str, BaseException]] = []
    for test in TESTS:
        try:
            test()
        except AssertionError as exc:
            failures.append((test.__name__, exc))
        except Exception as exc:
            failures.append((test.__name__, exc))
    if failures:
        print(f"{len(failures)} of {len(TESTS)} quality-gate tests FAILED:")
        for name, exc in failures:
            print(f"  - {name}: {type(exc).__name__}: {exc}")
        return 1
    print(f"all {len(TESTS)} quality-gate tests passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
