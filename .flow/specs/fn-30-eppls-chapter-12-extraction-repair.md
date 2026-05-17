# EPPLS Chapter 12 extraction repair

## Goal & Context
Repair EPPLS Chapter 12 source extraction so registry quote audits validate against readable, chapter-scoped source text rather than the prior whole-book two-column layout dump.

## Architecture & Data Models
Use a deterministic Poppler plain-text extraction script to regenerate `research/txt/eppls-extracted.txt` as Chapter 12 only. Update the EPPLS document manifest and registry provenance metadata to the repaired source SHA and chapter-local line ranges. Promote only previously demoted EPPLS records whose quotes now pass the registry quote audit.

## API Contracts
No runtime API changes. Registry records retain existing canonical IDs unless claim text or exact quotes change; this repair does not edit claim text or quotes.

## Edge Cases & Constraints
Records that still fail exact quote verification remain pending. EPPLS remains background-support authority only and does not supersede primary regulatory sources.

## Acceptance Criteria
- [x] EPPLS source text is Chapter 12 only and reproducibly generated.
- [x] Accepted registry quote audit passes with zero misses.
- [x] Reproducibility audit passes.
- [x] Residual pending records are explicitly documented.

## Boundaries
No full-book EPPLS extraction. No manual quote rewriting. No claim that EPPLS is authoritative regulation.

## Decision Context
Option 1 was selected because the primary failure was source extraction quality, not semantic extraction.

## Review considerations
FP / type safety: not applicable; data/tooling-only repair.
Test architecture: quote audit, reproducibility audit, and existing quality/override contract tests cover the registry invariants.
Impact: changes EPPLS source SHA, manifest line ranges, and EPPLS registry provenance; residual quote failures remain loud in pending.
Operational correctness: EPPLS is background-support only; regulatory claims remain sourced to primary documents.
