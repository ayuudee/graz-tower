# FN34 synthesis: EPPLS Chapter 12 contrast

## Verdict

EPPLS Chapter 12 should not be the next executable-test source. It is valuable,
but mostly as supporting/training evidence, domain-review input, and a driver
for phraseology and abnormal-communications modeling.

This is a useful contrast with ICAO 9432:

- ICAO 9432 produced a clean executable readback slice immediately.
- EPPLS Chapter 12 produced almost no direct executable work after conservative
  classification.
- That difference is a feature of the workflow: it can say "this source matters,
  but not as direct simulator tests yet."

## Classification profile

| Status | Count |
| --- | ---: |
| `needs_domain_review` | 141 |
| `blocked_by_model_gap` | 23 |
| `duplicate_support` | 14 |

No EPPLS records remain `pending_classification`.

## What EPPLS proves

EPPLS proves that the source-unit workflow must have multiple destinations:

- **Executable tests** for typed behavior and world-backed scenarios.
- **Phraseology linting/rendering** for standard words, numbers, time,
  callsigns, radio checks, and frequency expression.
- **Scenario-model gaps** for distress, urgency, and radio failure.
- **Domain/wiki support** for explanatory service definitions and training
  notes.

Trying to force EPPLS directly into simulator tests would create low-value
tests or misleading coverage claims.

## Relationship to FN33

FN33's `FN33-MODEL-1` backlog item covers the main deferred implementation
class exposed here: phraseology/timing/communication-policy model gaps. EPPLS
does not require a separate `.plan` item tonight because it reinforces the same
gap rather than surfacing a distinct new one.

## Recommendation

The next real implementation spike should not be "do all EPPLS." It should be
one of:

1. Add direct `SourceUnitRef` evidence to runtime traces or a test-only evidence
   projection.
2. Build a phraseology-rendering/linting spike and feed it EPPLS standard-word,
   numbers, callsign, and readback wording records.
3. Build a world-backed taxi/runway scenario slice from ICAO 9432, because that
   is more likely to produce useful executable simulator coverage.

EPPLS should remain in the ledger as support/review material until one of those
facets exists.

## Review considerations

### FP / type safety

No production code changed. The ledger preserves exact canonical source-unit ids
and avoids silently collapsing review-only records into executable coverage.

### Test architecture

No tests were added for EPPLS because the conservative classification says they
would mostly test wording/modeling capabilities that do not exist yet.

### Impact

Artifacts are research-only. The result reduces risk by preventing a misleading
test-writing campaign against low-authority or explanatory material.

### Operational correctness

EPPLS is treated as training/support material. It should not override ICAO,
CAP 413, SERA, or other primary sources when conflicts arise.
