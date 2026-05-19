# Senior review: FN37 SourceUnitSpec candidate

## Verdict

`SourceUnitSpec` is a successful exploration tool, but not the best final
architecture as currently shaped.

It is readable, source-centered, and directly expresses the author’s intent. It
proved the five-case suite can exist. However, its current implementation keeps
too much scenario construction inside the source-unit-owned test body. That
weakens the independent-team objective and makes large-scale reuse harder.

The right move is not to discard it. The right move is to demote it from
architecture to authoring vocabulary: keep its source identity, domain language,
witness/partition/fuzz tiers, non-vacuity counters, and model-gap discipline,
but do not make each source unit own bespoke simulation setup.

## Architecture review

### Strengths

- Direct source-unit traceability is excellent. A reviewer can see the source ids
  at the top of each test.
- The concept is small. It does not require a broad trace projection layer before
  authors can express useful checks.
- It supports multiple probe kinds under one source-unit umbrella: witness,
  partition, fuzz, and explicit gap.
- The failure report is source-aware and preserves enough structure to diagnose
  which probe failed.

### Weaknesses

- The current sim examples still construct `SimState`, load fixtures, schedule
  ATIS, and start controller cycles inside the spec body. That is too much
  implementation knowledge for the desired collaboration boundary.
- Source-unit ownership encourages duplicated scenario setup. Taxi, touch, takeoff,
  circuit, and after-landing source units will all want similar LOWG scenario
  scaffolding.
- There is no central scenario bank. A single trace cannot naturally be reused
  across many source units.
- It risks one file per source unit or one test per section, which can become a
  maintenance tax rather than a regulatory wall.

### Required architectural correction if chosen

If `SourceUnitSpec` remains primary, it must be rebuilt around:

```kotlin
sourceUnitSpec("...") {
    domain(TaxiDepartureDomain)
    witness(ScenarioRef.LowgSingleDeparture)
    assertTrace { trace -> ... }
}
```

The spec body should never build `SimState` directly.

## Test architecture review

The candidate aligns with the testing standards in spirit:

- it favors full scenarios for taxi/touch;
- it keeps pure protocol readback only where there is an independent oracle;
- it makes model gaps visible;
- it treats generated examples as domain probes, not random event soup.

The weakness is that the test scope is source-unit-local. High-level tests become
less high-level when each source unit owns a tiny custom mini-runner. The more
source units we add, the harder it becomes to know which system behaviors are
really covered versus merely repeated.

## FP / type-safety review

Current problems:

- `parameters: Map<String, String>` is too weak for fuzzed domains.
- `modelGap(...)` is an exception used for control flow in test support. That is
  acceptable for the spike but should become a typed outcome in a permanent
  runner.
- `SourceUnitSpecReport.assertHasModelGap()` lets expected gaps pass. That is
  fine for a design spike; permanent behavior needs separate expected-gap
  registration and backlog linkage.

Required correction:

- Replace string maps with typed domain values, e.g. `TaxiDomainCase`,
  `CriticalPhaseCase`, `ReadbackInstructionCase`.
- Replace exception-based model gaps with `Either<ProjectionGap, CheckResult>`
  or equivalent typed result at the runner boundary.
- Make probe kind and outcome exhaustive sealed hierarchies.

## Operational correctness review

The candidate handles source-unit identity well, but it can overclaim if the
assertion does not match the material source claim. The touch-and-go example
already showed this: a source unit about pilot request/intent can be partially
exercised by a typed clearance trace, but full coverage requires an intent or
request fact.

To be operationally honest, a permanent `SourceUnitSpec` must distinguish:

- source claim text;
- applicability;
- asserted behavior;
- missing phraseology/rendering evidence;
- missing intent/request evidence;
- source-unit status recommendation.

## Maintainability review

Maintainability is good for the first 5-20 source units and weak beyond that
unless scenario construction is centralized.

Likely failure mode: the repo accumulates readable but bespoke tests that all
load LOWG slightly differently, set up ATIS slightly differently, and assert
similar trace facts in different local styles.

The candidate needs:

- shared scenario references;
- shared typed domains;
- generated report artifacts;
- lint/validation for allowed imports;
- a ledger/report integration path.

## Senior findings

1. **Major: source-unit specs need a black-box scenario boundary.** Without this,
   test authors still need simulator internals.
2. **Major: stringly fuzz parameters are not acceptable beyond spike code.**
   Typed-domain values are required.
3. **Major: expected model gaps must be typed, registered, and reportable.**
   They cannot simply be passing tests.
4. **Moderate: source-unit-local scenario setup will duplicate heavily.**
5. **Moderate: reports need to be durable artifacts, not only failure strings.**

## Recommendation

Do not choose `SourceUnitSpec` as the core final architecture in its current
form. Keep it as an authoring-facing shell or manifest format if the final
approach uses monitors underneath.

Best retained ideas:

- source-unit identity at the top of the authored object;
- domain dimensions;
- witness / partition / fuzz tiers;
- non-vacuity counters;
- explicit model-gap outcome;
- readable source-centered report.
