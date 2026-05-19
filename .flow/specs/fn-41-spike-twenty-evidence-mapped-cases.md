# Spike Twenty Evidence-Mapped Cases

## Goal & Context
Scale the FN40 evidence-mapped test facade to roughly twenty authored cases and tighten the test authoring surface until the call sites feel simple, legible, and low-ceremony. Harness complexity is allowed internally; the public test should express scenario intent, evidence basis, typed samples, and expected evidence as directly as possible.

## Architecture & Data Models
Build on the FN40 throwaway harness in `sim/src/jvmTest`. Keep `SimObservation` as the anti-corruption port. Add only the DSL/helpers needed to make 20 cases readable. Include source-mapped, golden, regression, invariant, pass, expected-gap, and at least one negative/fail-shaped report case if useful without making the suite red.

## API Contracts
The public test authoring surface should be concise enough that a source case can usually be read as: source id(s), sample(s), expectation. Reusable protocol assertions may be helper functions if they improve legibility.

## Edge Cases & Constraints
Do not turn this into production architecture. Do not expose `SimState` to evidence cases. Do not silently skip gaps. Avoid broad monitor framework vocabulary at the call site. Keep any fuzz/generation bounded and deterministic.

## Acceptance Criteria
- [ ] Around 20 evidence-mapped cases compile and pass.
- [ ] Cases include synthetic protocol/readback, real LOWG scenario evidence, golden/non-source cases, and expected gaps.
- [ ] Call-site DSL is tightened from FN40.
- [ ] Focused tests and detekt pass.
- [ ] Review analyzes whether the design still feels simple at 20 cases.
- [ ] `.plan` is updated with the next recommended step.

## Boundaries
This remains a spike. It may add or revise test-only helper APIs but should not replace existing golden/source-unit tests. It should not attempt complete phraseology coverage.

## Decision Context
FN40 selected evidence-mapped tests over a narrow `SimObservation` port as the best middle ground. The open question is whether that feeling survives when scaled beyond a handful of examples.

## Review considerations

### FP / type safety
Preserve sealed outcomes and typed samples. Avoid string-coded result states. If helpers abstract evidence ordering or required readback atoms, keep them total and typed.

### Test architecture
Tests must remain high-level or synthetic-protocol at the boundary. The spike should judge authoring ergonomics as a first-class test architecture concern.

### Impact
The risk is DSL growth. The review must identify which helpers are worth keeping and which are ceremony.

### Operational correctness
Source-mapped cases cite source-unit ids. Semantic checks must not imply phraseology compliance. Golden/regression/invariant cases must state their non-source basis.
