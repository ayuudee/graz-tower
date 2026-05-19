# FN40 Dual Spike Review: Evidence-Mapped Tests vs Mini Monitors

Date: 2026-05-19

## Scope

This spike implemented two throwaway designs against the same representative
shape:

1. **Evidence-mapped harness**: normal Kotlin tests, a narrow `SimObservation`
   anti-corruption port, typed samples, source/golden bases, typed outcomes,
   and local reports.
2. **Mini monitor harness**: a small version of the FN39 monitor idea:
   `MiniConformanceTrace`, capabilities, provenance-bearing facts, contracts,
   monitors, and typed outcomes.

Both compile and pass focused tests.

## Files

Evidence-mapped spike:

- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceMappedSpikeHarness.kt`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceMappedHarnessSpikeTest.kt`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceMappedFacadeSpikeTest.kt`

Mini monitor spike:

- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/MiniConformanceMonitorSpikeHarness.kt`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/MiniConformanceMonitorSpikeTest.kt`

## Verification

Passed:

- `nix-shell --run "./gradlew :sim:jvmTest --tests 'xyz.easiersaid.twr.sim.EvidenceMappedHarnessSpikeTest'"`
- `nix-shell --run "./gradlew :sim:jvmTest --tests 'xyz.easiersaid.twr.sim.EvidenceMappedFacadeSpikeTest'"`
- `nix-shell --run "./gradlew :sim:jvmTest --tests 'xyz.easiersaid.twr.sim.MiniConformanceMonitorSpikeTest'"`

Direct `./gradlew` outside the Nix shell failed because no Java runtime was
visible in the ambient shell.

## What The Evidence-Mapped Harness Proved

The middle-ground idea works in code.

The core abstraction is:

- a scenario or synthetic protocol exchange produces `SimObservation`;
- assertions only see `SimObservation`, not raw `SimState`;
- each case declares a basis: source-mapped, golden, regression, or invariant;
- each case returns a typed outcome: pass, fail, vacuous, expected gap, or
  unexpected gap;
- typed samples can sit beside a case without a large fuzzing framework.

The same harness represented:

- a source-mapped synthetic readback test with typed heading representatives;
- a source-mapped LOWG taxi-order test;
- a source-mapped touch-and-go before full-stop test;
- two expected source-model gaps;
- one non-source golden assertion that the aircraft completes parked.

That last point matters: the harness is not regulation-only. It can express
golden/regression/invariant evidence with the same report discipline.

The facade test then proved the more important authoring property: the harness
can hide constructors, outcomes, report construction, and evidence ordering
behind a small Kotlin DSL:

```kotlin
sourceCase("taxi clearance before runway use", "...source ids...") {
    sample("runway", RunwayId("16C"))
    expect {
        instruction<TaxiToHoldingPoint>(aircraft) before
            report<Ready>(aircraft) before
            instruction<LineUpAndWait>(aircraft)
    }
}
```

This is the shape to optimize. Internal harness complexity is acceptable if the
public authoring surface stays this direct.

## What The Mini Monitor Harness Proved

The earlier FN39 idea also works in code at small scale.

It represented the same basic checks through:

- `MiniConformanceTrace`;
- typed capabilities;
- provenance-bearing trace facts;
- source/golden contracts;
- monitor runner;
- typed outcomes.

This gave stronger separation between scenario production and reusable monitor
logic. It also made provenance and compatibility explicit.

But it immediately became more verbose. The author has to understand contracts,
capabilities, trace projection, provenance, compatibility filtering, monitor
contexts, and fact paths before the actual test claim is legible.

## Main Finding

The user-facing criterion is decisive: **the test call site must feel simple,
legible, and not verbose**.

Harness complexity is acceptable if it stays inside the harness. The problem is
not the existence of contracts, ports, traces, provenance, or report machinery.
The problem is when a test author has to carry that vocabulary just to state a
small operational claim.

On that criterion, the evidence-mapped harness is the better next path.

The mini monitor shape is architecturally clean, but the current authoring
surface leaks the architecture. Even in a small spike, the monitor scaffolding
takes up more visual space than the behaviour under test. That is a bad smell
for the call-site API, not necessarily for the underlying implementation.

The evidence-mapped harness has enough structure to avoid one-off tests, but it
does not yet force authors into a visible framework. It keeps the test near the
scenario, keeps the expected evidence nearby, and leaves a clear road toward
reuse if patterns repeat.

## Why Evidence-Mapped Is The Better Middle Ground

### 1. It preserves test readability

The evidence-mapped facade reads like an ordinary Kotlin test with attached
evidence metadata. A reader can find the scenario, source ids, sample values,
and assertion in one place.

The monitor tests currently require following contract definitions, runner
inputs, capability filters, and fact projection before the asserted behaviour is
clear. That could be acceptable if a facade hides those pieces from normal
authoring.

### 2. It keeps the anti-corruption boundary

The spike's `SimObservation` successfully hides raw sim internals from
evidence assertions. It exposes observations: instructions, pilot reports,
pilot transmissions, and final aircraft summaries.

That gives the compiler-test quality the user wanted: scenario input, stable
observation output, evidence-mapped assertion.

### 3. It supports non-regulatory tests

The same form can express source-backed checks and golden checks. This prevents
the harness from becoming a heavy "regulation wall" and makes it generally
useful for important sim behaviours.

### 4. It leaves a path to monitors later

If evidence cases start duplicating the same predicates, those predicates can be
extracted into helpers or monitors later. That is a better direction of travel:
start legible, extract reuse under pressure.

### 5. It keeps fuzzing modest

Typed sample primitives work without a fuzzing framework. The spike used
representative heading samples in Kotlin. This can grow into example,
representative, and generated tiers without committing to a corpus-wide monitor
matrix.

## Anti-Case Against Evidence-Mapped

The evidence-mapped harness can drift into many bespoke cases if no extraction
discipline exists.

Risks:

- repeated applicability logic;
- reports that are too local rather than corpus-wide;
- evidence selectors duplicated across files;
- source-unit coverage still harder to aggregate than in a monitor bank;
- expected gaps may remain manually managed.

These risks are real, but they are manageable and visible. They are also the
right risks for the current stage. The current goal is to find a road where
tests remain pleasant to write. Prematurely solving corpus aggregation is less
important than preserving authoring clarity.

## Anti-Case Against Mini Monitors

The mini monitor architecture has the stronger long-term audit story, but the
spike exposes too much vocabulary at the authoring site.

Risks:

- authoring surface is verbose unless wrapped by a small facade;
- capability tags become a second taxonomy to maintain;
- `ConformanceTrace` can still become a shadow model;
- provenance/fact paths are useful in reports but noisy in test code;
- monitors feel detached from the scenario unless the harness does more work;
- the exposed structure encourages "framework first, test second".

The most important red-team finding is that monitor-backed tests may be correct
but uninviting if the facade is poor. If authors resist using the harness, the
architecture loses.

## What To Try Next

Proceed with the evidence-mapped direction, and evaluate it by the public DSL,
not the internal machinery. The next iteration should intentionally allow
non-trivial harness internals if they buy a terse, clear test surface.

The next spike should aim for a test shape roughly like:

```kotlin
evidenceScenario("LOWG touch-and-go then full-stop") {
    observe { lowgCircuit(outcomes = listOf(TouchAndGo, FullStop)) }

    sourceCase("taxi clearance before runway use") {
        cites("icao9432-extracted::taxi_4_4_en::417f64324f7495bf")
        sample("runway", RunwayId("16C"))

        expect {
            taxi<TaxiToHoldingPoint>() before report<Ready>() before instruction<LineUpAndWait>()
        }
    }

    goldenCase("mission completes parked") {
        expect { aircraft("OE-ABC").isParkedAndComplete() }
    }
}
```

That is the bar. The current spike is not terse enough yet, but it points in
the right direction. The next work should be API design and ergonomics at the
call site, with whatever internal harness structure is needed to support that
without lying.

## Recommended Architecture For The Next Iteration

Keep:

- `SimObservation` as the anti-corruption boundary.
- `EvidenceBasis` with `SourceMapped`, `Golden`, `Regression`, `Invariant`.
- typed outcomes.
- typed sample tiers.
- reports.
- synthetic protocol observations.

Improve:

- build a DSL that hides `EvidenceMappedCase(...)` constructors;
- provide domain query helpers: `instruction<T>()`, `report<T>()`,
  `before(...)`, `aircraft(...).parkedAndComplete`;
- make source citations terse but still explicit;
- hide monitor/contract/provenance machinery behind this facade if reuse starts
  demanding it;
- add generated-sample support only after the examples are pleasant;
- keep monitor extraction out of the first public authoring API.

Defer:

- global monitor bank;
- capability matrix;
- full trace fact taxonomy;
- corpus-wide coverage dashboard;
- phraseology checks.

## Review Considerations

### FP / type safety

Both spikes use sealed outcomes. The next evidence-mapped iteration should keep
that, but hide constructors behind a concise DSL. The observation model should
remain typed and immutable. If new observation fields are added, queries should
be total and return typed absence rather than nullable values where practical.

### Test architecture

The right test level is high-level scenario plus observation-port assertion.
The harness should not encourage low-level controller or pilot internals. Unit
tests should only appear around independent pure helpers if they earn their
keep.

### Impact

Evidence-mapped tests are less powerful than monitor banks for corpus-wide
coverage, but much more likely to remain legible. This is the better trade at
the current stage. Monitor extraction remains available once duplication proves
where it belongs.

### Operational correctness

Source-mapped cases must cite source-unit ids and should not imply phraseology
compliance unless the source unit explicitly supports that claim. Golden cases
must state their non-source basis so regulatory and project-specific claims do
not blur.

## Final Recommendation

Do not proceed directly to the FN39 monitor-first architecture.

Proceed with **evidence-mapped tests over a narrow `SimObservation` port** as
the next implementation experiment. The main work is now API ergonomics:
compress the authoring surface until the test intent is visible in a few lines,
while preserving source/golden basis, typed evidence, expected gaps, and local
reports.

If repeated evidence selectors emerge after 10-20 cases, extract those selectors
into reusable helpers or monitors. Let monitors be a refactoring result, not the
initial authoring model.
