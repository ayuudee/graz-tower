# FN41 Review: Twenty Evidence-Mapped Cases

Date: 2026-05-19

## Scope

FN41 scaled the FN40 evidence-mapped harness to 20 authored cases and judged
whether the test call site still felt simple and legible.

The spike added:

- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceMappedTwentyCaseSpikeTest.kt`
- facade additions in
  `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceMappedSpikeHarness.kt`

The 20 cases are:

- 12 source-mapped synthetic protocol/readback cases;
- 4 source-mapped LOWG scenario ordering cases;
- 1 golden LOWG completion case;
- 1 invariant LOWG runway-use ordering case;
- 2 explicit expected gaps.

## Verification

Passed:

- `nix-shell --run "./gradlew :sim:jvmTest --tests 'xyz.easiersaid.twr.sim.EvidenceMappedTwentyCaseSpikeTest'"`

## Main Result

The evidence-mapped direction still feels viable at 20 cases, but only after
adding domain-specific facade helpers.

The strongest positive result is `readbackCase`. Once that helper exists, the
protocol cases are tight:

```kotlin
readbackCase("takeoff clearance", ClearedForTakeoff(aircraft, runway)) {
    requires(ClearedForTakeoffReadback(runway))
}
```

That is close to the target feeling. The source mapping, report outcome, and
readback comparison are all harness concerns. The case author sees the
instruction and the expected atom.

The LOWG scenario cases also remain readable:

```kotlin
sourceCase("taxi clearance before runway use", TAXI_SOURCE_A, TAXI_SOURCE_B) {
    sample("runway", runway)
    expect {
        instruction<TaxiToHoldingPoint>(aircraft) before
            report<Ready>(aircraft) before
            instruction<LineUpAndWait>(aircraft)
    }
}
```

This is not as compact as the protocol cases, but it is legible. The assertion
looks like the intended temporal evidence.

## What Worked

### 1. Domain helpers matter more than generic harness purity

The generic `sourceCase { expect { ... } }` shape is acceptable, but the suite
became pleasant only when repeated domains got named helpers:

- `readbackCase(...) { requires(...) }`
- `instruction<T>(aircraft) before report<E>(aircraft)`
- `aircraft(aircraft).isParkedAndComplete()`
- `expectedGap(planId, reason)`

This points to the right design rule: keep the core harness small, then add
thin domain helpers where they remove visible ceremony.

### 2. The observation port held

The scaled cases did not need raw `SimState`. The authored assertions used
`SimObservation` evidence:

- observed instructions;
- observed reports;
- final aircraft summary;
- synthetic protocol boundary.

That is the key anti-corruption property.

### 3. Source, golden, invariant, and gap cases can share one form

The suite did not feel regulation-only. Source-backed cases and non-source
golden/invariant cases fit in the same report model without blurring their
basis.

### 4. Expected gaps stayed honest

The expected gaps are visible cases, not skipped tests. That is better than
turning unsupported source units into comments or exclusions.

## What Still Feels Noisy

### 1. Source ids dominate visual space

Long source-unit ids are necessary evidence, but they make call sites noisy.
The constants helped, but only because this is a spike. A real API probably
needs a small source catalog:

```kotlin
sources(ICAO9432.Readback.RequiredItems)
sources(ICAO9432.Taxi.HoldingPointLimit)
```

That catalog must remain generated or mechanically traceable to the source-unit
registry, not hand-wavy names.

### 2. The suite still mixes protocol and scenario concerns

The protocol readback cases and LOWG scenario cases both use
`evidenceScenario`, but they are different modes. The harness should probably
have two entry points:

- `protocolEvidence("...") { ... }`
- `simEvidence("...") { observe { ... } ... }`

That would remove the dummy synthetic observation for protocol-only checks.

### 3. Generic temporal ordering is good but limited

`A before B before C` is excellent for many golden/source assertions. It will
not be enough for ranges, absence, repeated events, or "between 500 ft and
1500 ft" style fuzz domains. The next API needs similarly terse helpers for:

- absence: `no instruction<T>() after ...`;
- ranges: `altitude in Feet(500)..Feet(1500)`;
- counts: `exactly(1) { instruction<T>() }`;
- generated samples: `forEachSample(altitudes.boundariesAndInterior())`.

The rule should be: add helpers only when a real case demands them.

### 4. `readbackCase` belongs in the harness, not the test file

In the spike it is local to the 20-case test. That made experimentation fast,
but it proves the helper is real enough to promote into the harness if this path
continues.

## Anti-Case

The approach can still become a bespoke-test garden.

At 20 cases, the pressure to create domain helpers is already clear. If those
helpers are added casually, the harness may become a bag of ad hoc assertion
functions. That would preserve low ceremony in the short term but lose
coherence.

The guardrail should be:

- generic harness primitives stay very small;
- domain helpers live in named modules;
- every helper is justified by repeated call-site simplification;
- helpers return the same typed outcomes and reports;
- no helper may inspect raw sim internals.

Another risk: source coverage remains local. The report can say what these
20 cases did, but it is not yet a corpus-wide coverage dashboard. That is
acceptable for now. Corpus reporting should be built after the authoring
surface is stable.

## Comparison To Monitor-First

The monitor-first idea still has a place, but not as the public authoring model.

FN41 strengthens that conclusion. With domain helpers, evidence-mapped tests
can read like compact executable examples. The equivalent monitor version would
need contracts, capabilities, facts, and monitor binding unless hidden behind a
facade. If hidden behind the same facade, the difference becomes internal
implementation strategy rather than authoring model.

So the recommendation is not "never monitors." It is:

> Make the evidence-mapped facade the public API. Let monitors emerge behind it
> if repeated selectors or corpus reporting require them.

## Recommended Next Shape

Move toward a permanent-facing spike with this call-site goal:

```kotlin
protocolEvidence("ICAO 9432 readbacks") {
    readback("takeoff clearance", ClearedForTakeoff(aircraft, runway)) {
        cites(ICAO9432.Readback.RequiredItems)
        requires(ClearedForTakeoffReadback(runway))
    }
}

simEvidence("LOWG touch-and-go then full-stop") {
    observe { lowgCircuit(TouchAndGo, FullStop) }

    source("taxi clearance before runway use") {
        cites(ICAO9432.Taxi.HoldingPointLimit)
        sample("runway", runway)
        expect {
            instruction<TaxiToHoldingPoint>(aircraft) before
                report<Ready>(aircraft) before
                instruction<LineUpAndWait>(aircraft)
        }
    }
}
```

The main missing piece is a source catalog. Without it, long ids will keep
making tests look heavier than they are.

## Review Considerations

### FP / type safety

The outcome model remains sealed and typed. The next iteration should avoid
string source IDs at call sites by introducing typed source references generated
from the source-unit registry or declared in one audited catalog. Ordering and
readback helpers should continue returning typed outcomes.

### Test architecture

The architecture still fits the testing standards: high-level scenarios for sim
behaviour, synthetic protocol boundaries for pure protocol rules, and
report-level assertions rather than low-level implementation checks.

### Impact

The public API is trending in the right direction. The risk is helper sprawl.
The next spike should constrain helper placement and naming so the DSL does not
become local folklore.

### Operational correctness

The spike uses existing source-unit ids from prior ICAO 9432 work and explicitly
marks unsupported aerodrome-information / critical-phase facts as expected gaps.
It does not claim phraseology compliance beyond structural readback atoms.

## Final Recommendation

Continue with evidence-mapped tests.

The next work should be a tighter permanent-facing facade, not more conceptual
architecture:

1. split protocol and sim evidence entry points;
2. promote `readbackCase` into the harness as a first-class domain helper;
3. create a typed source catalog for the source-unit ids used in tests;
4. add only one or two new evidence primitives driven by real cases;
5. keep monitor/corpus aggregation as internal follow-up, not public test
   authoring vocabulary.

The 20-case spike supports the path. The tests can stay simple if the harness is
allowed to absorb complexity and if the call-site API is treated as the primary
design artifact.
