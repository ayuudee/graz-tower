#!/usr/bin/env python3
"""Canonical-id derivation for the Ollama-first source-unit registry.

A canonical id is content-addressed: it is a deterministic function of
(documentId, sectionId, claimText, exactSourceQuotes) — the four inputs
that together pin a source unit to its operative content and verbatim
provenance. The model-emitted candidateId never participates in identity.

Properties (verified by `test_quality_gates.py`):

  * Stable under whitespace tweaks, case differences, NFC/NFD variants,
    outer-quote noise in the claim text.
  * Stable under reordering or duplication of `exactSourceQuotes` (the
    quotes are sorted and deduped before hashing).
  * Distinct on a real change to claim text or to the set of source
    quotes — those are different operative requirements.
  * Distinct across documents and across sections, even when the same
    verbatim text recurs.

The 16-hex prefix gives ~64 bits of namespace, which is safely past
collision risk for the ~10^3 candidates this registry will hold.
"""
from __future__ import annotations

import hashlib
import re
import unicodedata


_OUTER_QUOTES = "\"'“”‘’"


def normalize_claim_text(text: str) -> str:
    """Normalise a claim string for identity purposes.

    Steps: NFC unicode normalisation, lowercase, whitespace-collapse,
    outer-quote strip. Designed to be stable under the kinds of edits a
    prompt tweak introduces without changing meaning.
    """
    if not isinstance(text, str):
        raise TypeError(f"claim text must be str, got {type(text).__name__}")
    s = unicodedata.normalize("NFC", text)
    s = s.casefold()
    s = re.sub(r"\s+", " ", s).strip()
    s = s.strip(_OUTER_QUOTES).strip()
    return s


def normalize_quote(quote: str) -> str:
    """Normalise a single exactSourceQuote for identity purposes.

    Whitespace-collapse + NFC, but NOT lowercased (case is part of a
    verbatim quote's identity within the source).
    """
    if not isinstance(quote, str):
        raise TypeError(f"quote must be str, got {type(quote).__name__}")
    s = unicodedata.normalize("NFC", quote)
    s = re.sub(r"\s+", " ", s).strip()
    return s


def canonical_id_for(
    *,
    document_id: str,
    section_id: str,
    claim_text: str,
    exact_source_quotes: list[str],
) -> str:
    """Compute the canonical id for a source-unit candidate.

    Returns: '{documentId}::{sectionId}::{16-hex sha256 prefix}'.

    Raises ValueError on empty claim or empty/whitespace-only quote list,
    because both are required schema invariants — a candidate without a
    claim or without source provenance has no identity to compute.
    """
    if not document_id:
        raise ValueError("documentId is required")
    if not section_id:
        raise ValueError("sectionId is required")
    if not exact_source_quotes:
        raise ValueError("exactSourceQuotes must be non-empty")

    normalised_claim = normalize_claim_text(claim_text)
    if not normalised_claim:
        raise ValueError("claimText normalises to empty")

    normalised_quotes = sorted({normalize_quote(q) for q in exact_source_quotes if q and q.strip()})
    if not normalised_quotes:
        raise ValueError("exactSourceQuotes all normalise to empty")

    content = "\n".join([
        document_id,
        section_id,
        normalised_claim,
        "|".join(normalised_quotes),
    ])
    short_hash = hashlib.sha256(content.encode("utf-8")).hexdigest()[:16]
    return f"{document_id}::{section_id}::{short_hash}"


def claim_sha256(claim_text: str) -> str:
    """SHA-256 of the normalised claim text — the cross-run join key.

    Used by the outcome CSV to identify "morally the same claim" across
    runs even when the model assigns different originalCandidateIds.

    The normalisation uses `casefold()`, which collapses pairs like
    "Straße"/"strasse" to the same hash — desirable within a document
    but a potential collision across jurisdictions that use different
    transliteration conventions. The outcome CSV therefore joins on
    `(documentId, sectionId, claimSha256)` and never on `claimSha256`
    alone, neutralising cross-document collisions.
    """
    return hashlib.sha256(normalize_claim_text(claim_text).encode("utf-8")).hexdigest()


_CANONICAL_RECORD_RE = re.compile(r"^.+::.+::[0-9a-f]{16}\.json$")


def looks_like_record_filename(name: str) -> bool:
    """Positive predicate: True iff `name` matches the canonical-id
    filename shape `{documentId}::{sectionId}::{16-hex}.json`.

    The audit and the promoter both gate registry-walking on this
    function; a foreign file in a registry directory is surfaced as
    `unexpected_file` rather than silently audited or counted.
    """
    return bool(_CANONICAL_RECORD_RE.match(name))


def is_sidecar_filename(name: str) -> bool:
    """Convention: files prefixed with `_` are sidecars
    (e.g. `_section.json`). Future sidecars must follow this convention
    or they will be flagged as unexpected files by the audit."""
    return name.startswith("_")


__all__ = [
    "canonical_id_for",
    "claim_sha256",
    "is_sidecar_filename",
    "looks_like_record_filename",
    "normalize_claim_text",
    "normalize_quote",
]
