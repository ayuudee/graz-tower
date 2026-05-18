# High-level source-backed scenario strategy spike

## Goal & Context

Find a strategy for source-unit-backed regulatory tests that matches the project testing standards: high-level, believable, minimal scenarios that exercise the relevant system path. The goal is a regulatory wall to build against, not a low-level unit-test matrix. This remains exploratory and disposable; if the strategy proves itself, throw away the spike scaffolding and rebuild cleanly.

The immediate lesson from FN33/FN34 is that protocol-function assertions are too low-level to become the main approach. They were useful probes, but the real target is scenario-shaped evidence: given a believable aircraft/controller situation, the system reaches the expected behavior and the scenario declares the source units that justify why that behavior matters.

## Strategy Decision: Source Evidence Shape

Use a test-only source evidence projection first.

Do not put `SourceUnitRef` into production `DecisionTrace` yet. That would prematurely commit runtime trace design before we know the scenario shape, evidence granularity, and assertion ergonomics. A test-only projection lets the spike compare approaches without coupling production code to a likely-to-be-rewritten experiment.

The projection should still be strict:

- cited source-unit ids must exist in the accepted registry;
- every scenario case must cite at least one source unit;
- every ledger row marked `covered` must be backed by a scenario/test citation;
- partial coverage must be represented explicitly, not hidden as `covered`.

## Architecture & Data Models

Spike-only concepts:

- `SourceUnitRef`: canonical accepted registry id.
- `SourceBackedScenario`: high-level test case metadata plus executable scenario/assertion.
- `ScenarioEvidence`: test-only record of source units asserted by the scenario.
- `CoverageLedgerCheck`: mechanical validation between registry, ledger, and source-backed tests.

No production runtime trace changes unless a later task explicitly compares that option and explains why the test-only projection is insufficient.

## Workflow

1. Repair spike hygiene from FN33: fix stale second-source notes and add/handle `partially_covered` where the previous ledger overstated coverage.
2. Add source citation validation: Kotlin tests or lightweight scripts must fail if source ids cited in tests do not exist in the accepted registry.
3. Build one high-level, minimal taxi/runway scenario from `icao9432-extracted::taxi_4_4_en`. Prefer a believable existing harness path over a narrow protocol function.
4. Compare the scenario shape against at least one alternative: direct golden annotation, standalone conformance DSL, or production trace extension. The comparison should focus on ergonomics, evidence honesty, failure messages, and how hard it is to throw away.
5. Synthesize what to keep, what to discard, and what to try next.

## Acceptance Criteria

- [ ] FN33 artifact inconsistency is corrected.
- [ ] The ledger vocabulary can express partial coverage explicitly.
- [ ] Source-unit citations used by spike tests are mechanically validated against accepted registry records.
- [ ] At least one high-level, believable, minimal source-backed scenario is implemented or the attempt produces a loud blocker with evidence.
- [ ] The scenario asserts system behavior, not merely a protocol helper function.
- [ ] The synthesis recommends whether to pursue test-only projection, production trace evidence, golden annotation, or another approach.

## Boundaries

In scope:

- Spike/test/research code.
- One or two representative high-level scenarios.
- Citation and ledger validation.

Out of scope:

- Full corpus coverage.
- Phraseology rendering/linting.
- Adversarial model-generated breaking attempts.
- Production `DecisionTrace` migration unless the experiment clearly demonstrates it is required.

## Decision Context

The project testing standards prefer system-level and integration tests. Therefore, the main question is not "can each source unit become a unit test?" The question is "can a small set of believable scenarios form a regulatory wall, with explicit source-unit evidence, without turning into brittle theater?"

## Review considerations

### FP / type safety

Keep evidence structures total and explicit. Avoid catch-all status mappings. Do not use `error()` for type-valid but unhandled classification states; represent them as explicit statuses in the ledger.

### Test architecture

The primary test must exercise a realistic path through the simulator/controller stack. Low-level assertions are allowed only as comparison/control evidence, not as the recommended strategy.

### Impact

This is intentionally disposable. Keep production changes out unless they are the explicit subject of a comparison task. The desired output is a strategy decision, not permanent scaffolding.

### Operational correctness

Every source-backed scenario must cite accepted source units. EPPLS remains support/training evidence and must not override ICAO/CAP/SERA primary sources.
