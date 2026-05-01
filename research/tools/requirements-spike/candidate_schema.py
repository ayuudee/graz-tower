#!/usr/bin/env python3
"""Single source of truth for the Ollama-first candidate schema.

The pipeline's prompts, the in-flight `require_fields` checks, and the
post-acceptance machine gates must all agree on which fields are
required and which enum values are legal. Historically those constants
were duplicated across prompt strings and validator calls in
`run_icao4444_ollama_first_prototype.py`; lifting them here gives a
single point of update.

This module is pure: no I/O, no globals beyond the constants below.
"""
from __future__ import annotations

from typing import Any


SCHEMA_NAME = "ollama_first_candidate"
FORMAT_VERSION = "2026-04-28-v1"


CANDIDATE_REQUIRED_FIELDS: tuple[str, ...] = (
    "candidateId",
    "sourceItemIds",
    "claimText",
    "authorityClass",
    "modality",
    "requirementKind",
    "testability",
    "verificationMode",
    "promotionHint",
    "exactSourceQuotes",
    "supportingAttempts",
    "consistencyClass",
    "rationale",
)


REGISTRY_REQUIRED_FIELDS: tuple[str, ...] = (
    "schemaName",
    "formatVersion",
    "canonicalId",
    "documentId",
    "sectionId",
    "claimText",
    "rationale",
    "modality",
    "authorityClass",
    "exactSourceQuotes",
    "provenance",
    "ingestionRun",
    "audit",
    "gate",
    "lifecycle",
)


PROVENANCE_REQUIRED_FIELDS: tuple[str, ...] = (
    "sourcePath",
    "sourceSha256",
    "startLine",
    "endLine",
)


INGESTION_RUN_REQUIRED_FIELDS: tuple[str, ...] = (
    "runId",
    "runStartedAt",
    "models",
    "promptVersion",
    "originalCandidateId",
)


# Accepted enum values, from `run_icao4444_ollama_first_prototype.py`
# CASES authority ceilings + the modality vocabulary used in prompts.
#
# `must` is accepted as an alias for `shall` — the two are
# semantically equivalent in regulatory English (UK CAA SafetySense
# documents and many EASA texts prefer `must`; ICAO Annexes prefer
# `shall`). Phase E surfaced this when 16 atomic SafetySense22
# readback items mirrored the source's "items must be read back in
# their entirety" wording and tripped the original strict enum.
MODALITY_VALUES: frozenset[str] = frozenset(
    {"shall", "must", "should", "may", "note", "example", "none", "mixed"}
)

AUTHORITY_CLASS_VALUES: frozenset[str] = frozenset(
    {
        "authoritative_requirement",
        "operational_guidance",
        "best_practice",
        "background_support",
    }
)

LIFECYCLE_STATES: frozenset[str] = frozenset(
    {"proposed", "pending", "accepted", "rejected"}
)

GATE_OVERALL_STATUSES: frozenset[str] = frozenset({"pass", "fail"})

QUOTE_AUDIT_STATUSES: frozenset[str] = frozenset({"pass", "fail"})


# Modality floors per authority class. Mirrors `_AUTHORITY_CLASS_BY_FLOOR`
# in `run_icao4444_ollama_first_prototype.py`. Kept here as the canonical
# table; the prototype script imports this constant in Phase B.
AUTHORITY_CLASS_BY_FLOOR: dict[str, frozenset[str]] = {
    "authoritative_requirement": frozenset({"shall", "must", "mixed"}),
    "operational_guidance": frozenset(MODALITY_VALUES),
    "best_practice": frozenset(MODALITY_VALUES),
    "background_support": frozenset(MODALITY_VALUES),
}


def is_authority_consistent(modality: str | None, authority_class: str | None) -> bool:
    """Return True iff (modality, authorityClass) satisfies the floor table.

    Unknown authority_class is treated as consistent (gate G3 raises
    schema-shape errors elsewhere); a missing modality is treated as
    `none`. Mirrors the prototype's existing behaviour bit-for-bit so
    the override safety condition does not shift under foot.
    """
    if not authority_class:
        return True
    allowed = AUTHORITY_CLASS_BY_FLOOR.get(authority_class)
    if allowed is None:
        return True
    return (modality or "none") in allowed


class SchemaError(Exception):
    """Raised by validate_* helpers when a candidate fails schema-shape checks."""


def _require(payload: dict[str, Any], fields: tuple[str, ...], *, label: str) -> list[str]:
    return [field for field in fields if field not in payload]


def validate_pipeline_candidate(candidate: dict[str, Any]) -> list[str]:
    """Validate a candidate as it leaves the pipeline (pre-promotion).

    Returns a list of human-readable failure reasons; empty list ⇒ pass.
    Does NOT raise — the caller decides whether to hard-fail or stash
    in `pending/`.
    """
    reasons: list[str] = []
    missing = _require(candidate, CANDIDATE_REQUIRED_FIELDS, label="candidate")
    for field in missing:
        reasons.append(f"missing required field: {field}")

    if isinstance(candidate.get("rationale"), str) and not candidate["rationale"].strip():
        reasons.append("rationale is empty or whitespace-only")
    if isinstance(candidate.get("claimText"), str) and not candidate["claimText"].strip():
        reasons.append("claimText is empty or whitespace-only")

    quotes = candidate.get("exactSourceQuotes")
    if isinstance(quotes, list) and not [q for q in quotes if isinstance(q, str) and q.strip()]:
        reasons.append("exactSourceQuotes has no non-empty entries")

    modality = candidate.get("modality")
    if isinstance(modality, str) and modality not in MODALITY_VALUES:
        reasons.append(f"modality '{modality}' not in {sorted(MODALITY_VALUES)}")

    authority_class = candidate.get("authorityClass")
    if isinstance(authority_class, str) and authority_class not in AUTHORITY_CLASS_VALUES:
        reasons.append(
            f"authorityClass '{authority_class}' not in {sorted(AUTHORITY_CLASS_VALUES)}"
        )

    return reasons


def validate_registry_record(record: dict[str, Any]) -> list[str]:
    """Validate a fully-promoted registry record.

    Returns a list of failure reasons; empty list ⇒ pass.
    """
    reasons: list[str] = []
    for field in _require(record, REGISTRY_REQUIRED_FIELDS, label="registry record"):
        reasons.append(f"missing required field: {field}")

    if record.get("schemaName") not in (SCHEMA_NAME, None):
        reasons.append(f"schemaName must be '{SCHEMA_NAME}', got {record.get('schemaName')!r}")

    # Pre-1.0 policy: strict equality. There is no migration tooling yet,
    # so a record carrying a stale or future formatVersion is either
    # mis-shaped or addressed by code that doesn't exist. The cure is to
    # write the migration script, not to silently accept the drift.
    if "formatVersion" in record and record.get("formatVersion") != FORMAT_VERSION:
        reasons.append(
            f"formatVersion must be {FORMAT_VERSION!r}, got {record.get('formatVersion')!r} "
            "— migrate the record explicitly before promoting"
        )

    provenance = record.get("provenance")
    if isinstance(provenance, dict):
        for field in _require(provenance, PROVENANCE_REQUIRED_FIELDS, label="provenance"):
            reasons.append(f"provenance missing required field: {field}")

    ingestion_run = record.get("ingestionRun")
    if isinstance(ingestion_run, dict):
        for field in _require(ingestion_run, INGESTION_RUN_REQUIRED_FIELDS, label="ingestionRun"):
            reasons.append(f"ingestionRun missing required field: {field}")

    lifecycle = record.get("lifecycle")
    if isinstance(lifecycle, dict):
        state = lifecycle.get("state")
        if state is not None and state not in LIFECYCLE_STATES:
            reasons.append(f"lifecycle.state '{state}' not in {sorted(LIFECYCLE_STATES)}")

    gate = record.get("gate")
    if isinstance(gate, dict):
        overall = gate.get("overallStatus")
        if overall is not None and overall not in GATE_OVERALL_STATUSES:
            reasons.append(
                f"gate.overallStatus '{overall}' not in {sorted(GATE_OVERALL_STATUSES)}"
            )

    audit = record.get("audit")
    if isinstance(audit, dict):
        quote_audit = audit.get("verbatimQuoteCheck")
        if isinstance(quote_audit, dict):
            status = quote_audit.get("status")
            if status is not None and status not in QUOTE_AUDIT_STATUSES:
                reasons.append(
                    f"audit.verbatimQuoteCheck.status '{status}' not in "
                    f"{sorted(QUOTE_AUDIT_STATUSES)}"
                )

    # Cross-field: an accepted record must have gate.overallStatus == 'pass'.
    if isinstance(record.get("lifecycle"), dict) and isinstance(record.get("gate"), dict):
        if record["lifecycle"].get("state") == "accepted" and record["gate"].get("overallStatus") != "pass":
            reasons.append("lifecycle.state=accepted requires gate.overallStatus=pass")

    return reasons


__all__ = [
    "AUTHORITY_CLASS_BY_FLOOR",
    "AUTHORITY_CLASS_VALUES",
    "CANDIDATE_REQUIRED_FIELDS",
    "FORMAT_VERSION",
    "GATE_OVERALL_STATUSES",
    "INGESTION_RUN_REQUIRED_FIELDS",
    "LIFECYCLE_STATES",
    "MODALITY_VALUES",
    "PROVENANCE_REQUIRED_FIELDS",
    "QUOTE_AUDIT_STATUSES",
    "REGISTRY_REQUIRED_FIELDS",
    "SCHEMA_NAME",
    "SchemaError",
    "is_authority_consistent",
    "validate_pipeline_candidate",
    "validate_registry_record",
]
