# EPPLS Chapter 12 contrasting source-unit workflow pass

## Goal & Context

Run a second source through the same ledger/classification workflow after FN33, but choose a source that proves a different facet. EPPLS Chapter 12 is larger than the tiny EGAST/SafetySense readback-only sources, but it is the best contrast because it is textbook/training material with radio discipline, standard words, radar-service definitions, distress/urgency, radio failure, operating numbers, and relay/circuit material.

This pass is not expected to produce many executable tests. Its purpose is to test whether the workflow remains useful when the source is mostly review-heavy, explanatory, or phraseology/language-policy oriented.

## Architecture & Data Models

Use the same artifact shape as FN33:

- generated ledger from accepted `eppls-extracted` records;
- section inventory;
- classification summary;
- synthesis report.

Statuses remain: `covered`, `new_case_needed`, `blocked_by_existing_red`, `blocked_by_model_gap`, `not_sim_scope`, `duplicate_support`, and `needs_domain_review`.

## Workflow

1. Generate a complete EPPLS Chapter 12 ledger from accepted registry records.
2. Classify every record conservatively.
3. Compare the classification profile against ICAO 9432.
4. Decide whether EPPLS should feed executable tests, wiki/domain notes, phraseology linting, or remain review support.

## Acceptance Criteria

- [ ] Every accepted `eppls-extracted` source unit appears exactly once in the ledger.
- [ ] Every EPPLS Chapter 12 section has a section-level status.
- [ ] No record remains `pending_classification`.
- [ ] The synthesis explains what EPPLS proves that ICAO 9432 did not.
- [ ] Any new deferred implementation gap is added to `.plan` if it is not already covered by FN33-MODEL-1.

## Boundaries

In scope: EPPLS Chapter 12 accepted registry records only.

Out of scope: new production behavior, full EPPLS book coverage, and executable tests unless a tiny obvious one already exists in current code.

## Decision Context

EGAST, SafetySense, and Slovenia VFR are smaller, but they mostly repeat the readback facet already tested by FN33. EPPLS Chapter 12 gives a better overnight contrast: less directly executable, more explanatory, and more likely to force classification into domain review, phraseology linting, or wiki/support buckets.

## Review considerations

### FP / type safety

No production types should change in this pass. Generated ledger rows must retain source-unit ids exactly.

### Test architecture

This pass should not invent tests for explanatory textbook material. If the correct outcome is `needs_domain_review` or `blocked_by_model_gap`, classify it that way.

### Impact

The impact should stay in research artifacts and Flow state. Findings may inform future phraseology/scenario-builder work.

### Operational correctness

EPPLS is training/textbook material, not the highest authority source. Do not use it to override ICAO/CAP/SERA behavior; classify it as supporting/training evidence where appropriate.
