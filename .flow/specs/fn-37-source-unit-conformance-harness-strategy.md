# Source-Unit Conformance Harness Strategy

## Goal & Context
Find a test strategy that can scale from source units to a durable regulatory wall without requiring test authors to understand the implementation internals of the current simulator. The goal is not to preserve the FN35/FN36 spike code. The goal is to learn what interface, vocabulary, and workflow would let another team express conformance tests from the source units and have those tests drive the system from the outside.

The current evidence says that source-unit citations are useful, but one-off source-backed scenarios are too weak. A better shape is: source unit as the audit atom, scenario as the executable context, and a generated condition space as the proof that the rule is not only true for the author's one happy path.

External research anchors this direction: property-based testing feeds many generated values into the same invariant rather than relying on hand-picked examples, and Kotest already supports that model in Kotlin. QuickCheck-style state-machine testing adds preconditions, postconditions, and an abstract model for stateful APIs. Model-based testing treats tests as derived from an abstract model and explicitly calls out the mapping problem from abstract tests to executable system calls; that is exactly the problem here.

## Proposed Shape
Introduce a disposable `SourceUnitLaw` test harness in `sim/jvmTest` or a sibling test-support module. It should be code-first and strongly typed, but declarative enough that source-unit test authors do not need to know controller internals.

Conceptually:

```kotlin
sourceUnitLaw("icao9432-extracted::taxi_4_4_en::417f64324f7495bf") {
    title("Taxi clearance limit is a runway holding point before runway use")
    source("ICAO 9432 4.4")

    domain(TaxiDepartureDomain) {
        aerodromes(LOWG)
        runways(activeRunways())
        startPositions(gaStands())
        traffic(singleAircraft(), lightTwoAircraft())
        weather(vfr())
    }

    witness("LOWG 16C GA stand A") { scenario ->
        scenario.assertTrace { trace ->
            taxiClearance.destination.isHoldingPointFor(activeRunway)
            taxiClearance precedes pilotReport<Ready>()
            pilotReport<Ready>() precedes instruction<LineUpAndWait>()
        }
    }

    property("all generated valid taxi departures") { generatedScenario ->
        generatedScenario.assertTrace { trace ->
            every<TaxiToHoldingPoint>().destination.isHoldingPointFor(activeRunway)
            no<LineUpAndWait>().before(pilotReport<Ready>())
        }
    }
}
```

This is intentionally not the final API. The important design commitments are:

- A source unit owns a suite, not necessarily a single test.
- A suite has one minimal witness scenario plus one or more generated/property probes.
- Assertions are over black-box trace vocabulary: transmissions, typed instructions, pilot reports, aircraft phases, responsibilities, and source/evidence markers if present.
- The domain is explicit: the test says the finite or generated space it claims to cover.
- The runner records seeds, generated parameters, non-vacuity counts, and shrunk counterexamples.

## Architecture & Data Models

### 1. Black-box conformance interface

Define the test-facing system boundary as an interface, not as direct access to controller internals:

```kotlin
interface TowerConformanceTarget {
    fun run(input: ScenarioInput, seed: Long): ScenarioTrace
}
```

`ScenarioInput` is the public language for a test author: aerodrome fixture, aircraft, mission intent, weather, runway configuration, injected world events, optional pilot/controller communication events, and time budget.

`ScenarioTrace` is the public oracle language: ordered transmissions, typed instructions, typed pilot reports, aircraft phase timeline, runway/traffic state observations, and optional decision evidence. It can be backed by today’s `SimTrace` and transmission records, but the law author should not depend on `SimState`, `BeliefState`, controller rules, or internal BDI objects.

### 2. Source-unit law model

A `SourceUnitLaw` should contain:

- `sourceUnit`: canonical id.
- `claim`: short human text copied or paraphrased from the accepted source unit.
- `kind`: obligation, prohibition, permission/capability, definition, phraseology, timing, emergency/abnormal.
- `applicability`: preconditions under which the claim applies.
- `domain`: generated dimensions and finite partitions.
- `oracles`: trace predicates, temporal predicates, phraseology predicates, evidence predicates.
- `adequacy`: required domain-cell coverage and non-vacuity gates.
- `knownGap`: optional loud classification when the model cannot express the source yet.

This makes the test suite per source unit, but avoids one brittle `@Test` per source unit. A single source unit may have several assertions or generated probes. Several related source units may share one scenario domain and runner.

### 3. Domain language and fuzzing

The domain should be a typed generator, not random event soup. It should generate valid scenarios only, with named dimensions:

- aerodrome: LOWG now, later LOWG/LJMB where relevant;
- runway: active runway, approach/departure runway pairing;
- aircraft count and ordering;
- aircraft mission intent: full stop, touch-and-go, go-around, transit;
- phase windows: taxi, line-up, takeoff roll, initial climb, downwind, final, landing roll;
- weather/hazard: normal VFR, crosswind/tailwind, runway obstruction, essential aerodrome info;
- communications: ATIS present/absent, frequency in use, readback required, emergency/urgency flag;
- traffic: none, same-runway serialised, pattern conflict, runway occupied.

Use three tiers:

1. `Witness`: one deterministic, minimal scenario for readability and debugging.
2. `Partition sweep`: finite generated examples over named equivalence classes. This is the compiler-test-inspired part: the suite knows which cells exist and which have passed.
3. `Random fuzz`: Kotest generator over the same typed domain, with seeds and shrinking. This is not for proving every case; it is for discovering missing cells, accidental coupling, and brittle assumptions.

### 4. Adequacy and progress

The harness should produce a `SourceUnitLawReport` artifact:

- source unit id;
- law id;
- generated domain dimensions;
- cells covered;
- cells intentionally out of scope;
- witness pass/fail;
- property iterations and seed;
- non-vacuity counters;
- linked trace snippets/counterexamples;
- current ledger status recommendation.

This report becomes the bridge from source units to progress tracking. It is better than simply marking ledger rows because it explains what was actually exercised.

## Five-Case Trial Plan

The next spike should implement just enough harness to exercise five disparate cases. These are deliberately chosen to pressure different facets.

### Case 1: Readback obligations, pure protocol

Source area: `icao9432-extracted::readback_2_8_3_en`.

Purpose: prove the harness supports source-unit suites that do not need the full sim. The target can be a pure adapter over `requiredReadbackAtoms`.

Domain: generated typed ATC instructions grouped by runway operations, taxi/route clearances, levels/headings/speeds/squawks/pressure.

Oracle: required readback atoms contain exactly the operational atoms named by the instruction family.

Adequacy: every readback instruction family has at least one generated example; family counters are non-zero.

Expected learning: whether one source-unit law can contain multiple generated instruction families without becoming unreadable.

### Case 2: Taxi clearance and runway-use boundary

Source area: `taxi_4_4_en`.

Purpose: prove a full black-box sim scenario can be expressed without referencing controller internals.

Domain: LOWG departure from valid stands; active runway choices; one or two light aircraft; optional ATIS already issued.

Oracle: taxi clearance limit is a holding point for the active runway; no line-up/takeoff clearance before the aircraft reports ready; generated variants preserve this.

Adequacy: every active runway in the fixture that has a valid departure holding point is hit; each generated run has at least one taxi clearance and one runway-use instruction, otherwise fail as vacuous.

Expected learning: whether black-box trace assertions are expressive enough for ground movement rules.

### Case 3: Touch-and-go / full-stop intent

Source area: `final_approach_landing_4_7_en`.

Purpose: split a permissive source unit into capability plus intent-sensitive behavior.

Domain: circuit-training mission outcomes: full stop only, touch-and-go then full stop, go-around then full stop where already modeled; one/two aircraft if cheap enough.

Oracle: touch-and-go intent leads to `ClearedTouchAndGo` on the relevant circuit; full-stop intent does not; later full-stop landing clears and vacates; if phraseology rendering exists, rendered text must include the phraseology atom, otherwise the phraseology claim remains partial/model-gap.

Adequacy: all mission-outcome partitions pass and the negative full-stop-only partition observes no touch-and-go clearance.

Expected learning: how to handle `may` source units without turning them into unconditional requirements.

### Case 4: Essential aerodrome information timing

Source area: `essential_aerodrome_information_4_10_en`.

Purpose: test a rule whose domain is not just aircraft intent, but world information availability and timing.

Domain: runway/taxiway hazards or lighting/serviceability conditions; aircraft before taxi, before final approach, and already informed vs not informed.

Oracle: when essential information is relevant and not already known, it appears before taxi or final approach; when already known, omission is allowed. This may initially produce model gaps if essential information is not first-class enough.

Adequacy: each hazard/information partition either has a passing trace or a loud `model_gap` report; no silent skip.

Expected learning: whether the harness can express “known to aircraft” and information-timing domains independently of current implementation details.

### Case 5: Radio silence during critical phases except safety necessity

Source area: `aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4`.

Purpose: pressure the framework with a temporal prohibition and an exception condition.

Domain: aircraft phases takeoff roll, initial climb, last part of final, landing roll; candidate transmissions safety-critical vs non-safety; normal and abnormal world events.

Oracle: non-safety transmissions are absent during critical windows; safety transmissions are allowed and traceable to a safety event. If the current simulator lacks communication workload/necessity classification, this law should compile/run as an explicit model-gap probe, not pass silently.

Adequacy: every critical phase window is exercised; both allowed and forbidden message classes are generated. Non-vacuity gates require at least one attempted non-safety transmission candidate in each critical phase partition.

Expected learning: whether fuzzing the domain language can expose missing model concepts before implementation starts.

## Workflow

1. Build a tiny harness skeleton around `TowerConformanceTarget`, `ScenarioInput`, `ScenarioTrace`, `SourceUnitLaw`, and `LawReport`.
2. Port the existing readback cases into Case 1 using generated instruction families.
3. Port the taxi and touch-and-go scenarios into Cases 2 and 3 using the black-box target.
4. Add Case 4 as a mixed pass/gap trial for essential aerodrome information.
5. Add Case 5 as a deliberate red-team law for communication timing. It may not pass; success is a loud and useful model-gap report.
6. Generate a single summary artifact showing five source-unit laws, their domains, partitions, vacuity counters, pass/fail/gap state, and counterexample seeds where applicable.
7. Decide whether the harness feels good enough to rebuild cleanly or whether to discard it and keep only lessons.

## Acceptance Criteria

- [ ] Five source-unit law suites exist across the cases above.
- [ ] At least three run as passing executable tests against the current system.
- [ ] At least one uses generated/property input over a named domain rather than a single example.
- [ ] At least one intentionally reports a model gap without passing silently.
- [ ] Every law emits or can emit a `LawReport` with source unit, domain dimensions, coverage cells, non-vacuity counters, and seed/counterexample data.
- [ ] A test author can read a law without needing to know controller BDI/rule internals.
- [ ] A review document records what felt good, what was awkward, what was impossible, and what should be thrown away.

## Boundaries

Do not attempt all ICAO 9432 source units. Do not make this a permanent framework yet. Do not require phraseology rendering to exist before the trial, but do make phraseology gaps explicit. Do not let random generation create impossible aviation situations; generators must encode valid scenario domains. Do not mark source units covered merely because a related golden exists.

## Decision Context

This combines three testing styles already relevant to the repo:

- compiler-style condition-space coverage: named partitions and progress through them;
- property-based testing: generated inputs over invariants and seeds for counterexamples;
- model-based testing: abstract scenario/test requirements mapped to black-box executable traces.

The important bet is that the source-unit law layer becomes the shared language between a regulatory/test team and the implementation team. The test team writes source-unit laws against `TowerConformanceTarget`; the implementation team makes any tower engine satisfy that interface.

## Plan Review

### FP / type safety

The domain language must be typed. Invalid scenarios should be unrepresentable where practical: e.g. active runway ids come from the fixture, mission outcomes from sealed `CircuitOutcome`, phase windows from explicit domain values. Where validity depends on fixture data, generators return typed errors or shrink away through explicit preconditions with non-vacuity counters. Avoid `else` catch-alls in oracle dispatch; each source-unit law kind should be handled explicitly.

### Test architecture

This respects the project standard of high-level/integration tests first. The pure readback case is acceptable because it has a formal independent oracle in protocol structure. The sim cases assert trace behavior through a public-ish trace vocabulary. Unit tests are not the default. Generated tests must have witness examples so failures are understandable.

### Impact

The main coupling risk is creating a second simulator DSL. Keep the first harness intentionally thin: a black-box input, a black-box trace, source law metadata, and reports. Do not expose controller beliefs or BDI structures. If the trace vocabulary is insufficient, that is a useful finding and should drive a trace projection, not law authors reaching into internals.

### Operational correctness

Every law must cite source units and keep the source claim visible. `may` and `should` claims are not treated as unconditional obligations. Phraseology-only claims cannot be fully covered by typed instruction traces. Emergency/critical-phase laws must cite the relevant source unit and classify missing safety-necessity semantics as model gaps.

## Red Team

### Attack: This becomes ceremonial traceability

Risk: authors attach source ids to broad goldens and call them covered.

Countermeasure: each law must list its domain dimensions, oracle predicates, and non-vacuity counters. The report should fail if a cited source unit has no assertion that mentions its law id or predicate.

### Attack: Fuzzing produces aviation nonsense

Risk: random generation creates impossible starts, invalid runway use, or incoherent traffic, then failures are noise.

Countermeasure: fuzz only through typed domain generators seeded from validated fixtures. No arbitrary event streams for source laws. Use partitions first, randomization second.

### Attack: Tests require implementation knowledge anyway

Risk: to make assertions pass, authors inspect BDI/rules and encode implementation assumptions.

Countermeasure: source-law code may depend only on `TowerConformanceTarget`, `ScenarioInput`, `ScenarioTrace`, and protocol types. Ban imports from controller internals in the law package with a test or detekt rule if this becomes permanent.

### Attack: Property tests become slow/flaky

Risk: generated full-sim runs are too expensive or nondeterministic.

Countermeasure: two tiers. CI uses witness plus small partition sweeps. Nightly/local uses higher iteration fuzzing. Every failure logs seed and generated scenario parameters. Determinism is itself a harness invariant.

### Attack: Source units are not formal requirements

Risk: extracted source units mix examples, permissions, definitions, and operational obligations.

Countermeasure: law `kind` controls semantics. Definitions can support domains; permissions become capability laws under explicit requested preconditions; obligations/prohibitions become stronger oracles; examples remain support unless phraseology rendering exists.

### Attack: Five cases are still too close to current goldens

Risk: readback/taxi/touch-and-go are too easy and do not prove breadth.

Countermeasure: include Case 4 and Case 5 specifically because they likely expose missing model concepts. A good plan must produce at least one explicit gap, not only green tests.

## Revised Recommendation After Review

Proceed with a short spike, but do not start by generalising the FN35/FN36 helper. Start with the law/report vocabulary and implement five thin vertical slices. The success criterion is not coverage count; it is whether the law authoring experience feels like writing regulatory behavior against a black-box tower.

The strongest final shape is:

`SourceUnitLaw = source id + typed domain + witness + generated partitions + trace oracle + adequacy report`.

That is the language worth trying next.
