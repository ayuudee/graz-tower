# FN42 Synthesis: Evidence-Mapped Testing Initiative

Date: 2026-05-19

## Bottom Line

The initiative should continue, but the recommendation needs correction.

The right conclusion is not:

> Build evidence-mapped tests over `SimObservation` as the conformance
> architecture.

The better conclusion is:

> Build a source/evidence-mapped public DSL whose call sites stay terse, and
> back it with a real audit/reporting core: typed source catalog, typed domains,
> provenance-bearing observations, adequacy/activation accounting, durable
> reports, and governed expected gaps.

FN40/FN41 proved the authoring surface can feel good. They did not prove the
audit architecture is sufficient.

## What Survives

### The public DSL direction survives

The strongest positive evidence is the 20-case call site. Protocol cases such
as:

```kotlin
readbackCase("takeoff clearance", ClearedForTakeoff(aircraft, runway)) {
    requires(ClearedForTakeoffReadback(runway))
}
```

and scenario evidence such as:

```kotlin
instruction<TaxiToHoldingPoint>(aircraft) before
    report<Ready>(aircraft) before
    instruction<LineUpAndWait>(aircraft)
```

are close to the desired feel. The test author sees intent, not harness
machinery.

### The anti-corruption boundary survives

Assertions should not consume raw `SimState`. An observation layer is still the
right port/adaptor boundary.

But `SimObservation` as currently spiked is too thin. A permanent observation
record needs stable fact identity and provenance.

### Evidence bases survive

The shared form for source, golden, regression, and invariant cases is a good
idea. It prevents the harness from becoming a regulation-only wall while still
keeping regulatory claims marked as source-backed.

### Fuzzing belongs behind the facade

The user formulation is correct: the public test says what domain matters; the
harness decides whether to run examples, representatives, or generated samples.

Fuzzing should be a typed domain/sample capability, not public test ceremony.

## What Does Not Survive

### `SimObservation`-only conformance does not survive

The current observation model has no:

- source event id;
- fact id;
- provenance path;
- applicability;
- activation count;
- compatibility/capability result;
- adequacy;
- durable report record.

That is enough for a spike, not enough for source-backed conformance.

### Raw string source ids do not survive

Raw source ids at call sites are both noisy and unsafe. The next path needs a
typed/generated source catalog or mechanically validated source references.

The catalog should make call sites read like:

```kotlin
cites(ICAO9432.Readback.RequiredItems)
cites(ICAO9432.Taxi.HoldingPointLimit)
```

The names must be traceable to source-unit registry records.

### First-match selectors do not survive

`instruction<T>()` / `report<T>()` selecting the first matching event is not
enough. It will break or false-pass under repeated events, multi-aircraft runs,
go-arounds, touch-and-go repeats, and absence/range assertions.

The next query layer needs typed selector semantics:

- `first`;
- `nth`;
- `exactly(n)`;
- `none`;
- `between`;
- `after`;
- scoped by aircraft, runway, circuit, attempt, phase, or sample.

### Broad expected gaps do not survive

Expected gaps cannot point at broad initiative items. They need typed gap ids
with:

- affected source units;
- missing observation/projection/model concept;
- closure trigger;
- backlog/deferment link.

## Reconciled Architecture

The reconciled architecture is:

1. **Public DSL**
   - `protocolEvidence { ... }`
   - `simEvidence { ... }`
   - `source { cites(...); sample(...); expect { ... } }`
   - `golden`, `regression`, `invariant` variants.

2. **Source catalog**
   - typed source refs generated or validated from the source-unit registry;
   - no raw source strings at ordinary call sites.

3. **Typed domains and samples**
   - examples;
   - representatives;
   - generated samples with seed/count/partition;
   - reproducible failure reports.

4. **Observation/fact layer**
   - observation records remain anti-corruption data, not raw sim state;
   - every observed fact has id, provenance, stable sequence, time, and origin;
   - synthetic protocol facts and sim facts share report shape.

5. **Evidence query algebra**
   - typed selectors and ordering/window/count/absence helpers;
   - no ad hoc first-match assumptions for permanent cases.

6. **Audit/reporting core**
   - durable Markdown/JSON reports;
   - source id, case id, scenario id, sample, seed, partition, outcome, fact ids;
   - applicability/activation/adequacy where source conformance claims need it.

7. **Internal monitors only if they earn their keep**
   - monitors may appear behind the facade for repeated selectors or corpus
     aggregation;
   - monitor vocabulary must not leak into the public test authoring surface.

## Fuzzing Position

Fuzzing should be implemented as typed domain expansion:

```kotlin
sample("heading", headings.boundariesAndInterior())
generated("heading", headings, seed = 9432L, count = 25)
```

For the first permanent-facing slice, start with pure protocol domains:

- heading;
- level;
- speed;
- pressure;
- squawk;
- runway identifiers.

For sim scenarios, stay with examples and representatives until reports can
reproduce failures with source unit, scenario, seed, sample value, partition,
and supporting fact ids.

## What To Do Next

Create a build spike, not another conceptual spike:

1. Implement `protocolEvidence` and `simEvidence` public entry points.
2. Introduce a small typed source catalog for the source units already used in
   FN41.
3. Promote `readbackCase` as a first-class helper, but name it honestly:
   `structuralReadbackRequirement` unless it drives an actual exchange.
4. Add provenance-bearing observation facts with stable ids and sequence.
5. Add a small query algebra: `first`, `exactly`, `none`, `between`.
6. Add typed domain samples and one generated protocol domain.
7. Emit durable Markdown/JSON reports for the suite.
8. Re-port the 20 cases through the new facade.

## What Would Falsify The Direction

Stop or rethink if:

- the permanent facade cannot keep the 20 cases as readable as the spike;
- source refs cannot be validated mechanically;
- expected gaps cannot be governed with typed gap ids;
- repeated-event selectors become ambiguous;
- generated sample failures are not reproducible;
- reports are not useful enough to answer "what source units are covered and
  what evidence supports them";
- authors repeatedly need to escape to raw `SimState` or manual trace scans.

## Final Recommendation

Proceed with evidence-mapped testing as the **authoring model**, not as the
entire architecture.

Implement the next iteration as:

> terse evidence DSL over a provenance-bearing audit/reporting core.

This reconciles FN39 and FN41: FN39 was right about auditability; FN41 was right
about authoring ergonomics. The next design must satisfy both.
