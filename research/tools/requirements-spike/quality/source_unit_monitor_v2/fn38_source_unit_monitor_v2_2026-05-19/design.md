# FN38 design: source-unit conformance monitors (v2)

## Summary

This is a deliberately separate design from FN37 `SourceUnitSpec`.

FN37 asks a source unit to own a small executable spec: domain, witness,
partitions, fuzz probes, assertions, report. FN38 flips that around:

**Scenarios produce traces. Source-backed monitors observe those traces.**

The core object is not a source-unit spec, but a reusable temporal monitor:

```kotlin
monitor("icao9432-critical-phase-radio-silence") {
    sources("icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4")

    observe<PilotBroadcast>()

    appliesWhen { event, trace ->
        event.aircraft.altitudeFt in 500..1500 &&
            event.aircraft.phase in criticalPhases &&
            event.classification == Routine
    }

    require { event, trace ->
        event.absent()
    }
}
```

That syntax is intentionally illustrative. The design point is: the rule is an
observer over a trace, with an antecedent and a consequent. Fuzzing means
generating traces that exercise the antecedent over a typed range, not
generating arbitrary events.

## Why this is meaningfully different

FN37 is author-facing and source-unit-shaped:

`SourceUnitSpec = source id + typed domain + witness + partition/fuzz probes + oracle`.

FN38 is monitor-facing and trace-shaped:

`Monitor = source ids + observed event type + applicability predicate + required trace property`.

Scenarios become separate from rules. A single scenario can feed many monitors,
and a single monitor can run over many scenarios. This better matches compiler
test suites and runtime verification: generate or hand-author programs/traces,
then run a bank of independent checks over them.

## Model

### Conformance Trace

The monitor runner needs a black-box trace, not simulator internals:

```kotlin
data class ConformanceTrace(
    val scenarioId: ScenarioId,
    val seed: Long,
    val facts: List<TraceFact>,
)
```

`TraceFact` is a normalized event/fact vocabulary, projected from current
`SimTrace` and transmission records:

- `ControllerInstructionIssued`
- `PilotReportMade`
- `PilotBroadcastMade`
- `AircraftPhaseObserved`
- `AircraftAltitudeObserved`
- `AircraftPositionObserved`
- `RunwayStateObserved`
- `InformationKnownToAircraft`
- `EssentialAerodromeInformationBroadcast`
- `TransmissionClassificationObserved`

The last three probably do not exist yet. That is good: the design should make
those gaps visible as missing trace projections, not as skipped tests.

### Monitor

```kotlin
data class ConformanceMonitor<E : TraceFact>(
    val id: MonitorId,
    val sourceUnits: Set<SourceUnitRef>,
    val observedType: KClass<E>,
    val applicability: Applicability<E>,
    val requirement: Requirement<E>,
    val adequacy: MonitorAdequacy,
)
```

The monitor runner evaluates:

1. collect all trace facts of `observedType`;
2. filter facts where `applicability` holds;
3. for each applicable fact, evaluate `requirement`;
4. record counters for observed facts, applicable facts, pass/fail/gap results;
5. fail as vacuous if adequacy says applicable facts must exist.

This makes “between 500 ft and 1500 ft” natural:

```kotlin
appliesWhen {
    altitudeFt in 500..1500
}
```

The fuzzing target is the scenario generator. The monitor stays the same.

### Scenario Bank

Scenarios are no longer embedded inside each source-unit check. They live in a
bank:

```kotlin
scenario("LOWG single-aircraft departure") { ... }
scenario("LOWG circuit touch-and-go then full-stop") { ... }
scenario("LOWG critical-phase radio workload fuzz") { ... }
```

Each scenario declares the partitions it claims to exercise:

- aerodrome;
- runway;
- aircraft count;
- mission intent;
- altitude band;
- phase window;
- hazard state;
- communication class;
- traffic state.

The runner applies all compatible monitors to each scenario trace. A monitor can
also declare a `requiresProjection` list so model gaps are explicit when a trace
cannot express the concept.

### Adequacy

Monitor adequacy is not source-unit coverage. It is monitor activation coverage.

For each monitor:

- `observed`: did the trace contain candidate facts?
- `applicable`: did the antecedent trigger?
- `checked`: did the consequent run?
- `passed`: did every applicable fact satisfy the consequent?
- `gap`: was a required projection missing?
- `vacuous`: did no applicable fact occur where at least one was required?

This is a stronger fit for temporal and range rules than FN37’s probe counters.

## The five cases under v2

### 1. Readback obligations

Monitor:

- observes `ControllerInstructionIssued`;
- applies when instruction is in readback-required families;
- requires the subsequent pilot readback to contain the required atoms.

Scenario bank:

- pure protocol generated instruction traces;
- optional full-sim traces later.

Why v2 may be better:

- one monitor can observe every emitted instruction across all scenarios;
- no need to write a separate source-unit spec per instruction family.

Risk:

- pure protocol checks need synthetic `ConformanceTrace`, which may feel
  artificial unless the projection API is clean.

### 2. Taxi clearance and runway-use boundary

Monitor A:

- observes `ControllerInstructionIssued(TaxiToHoldingPoint)`;
- applies on departure taxi clearances;
- requires destination to be a holding point for the active runway.

Monitor B:

- observes `ControllerInstructionIssued(LineUpAndWait | ClearedForTakeoff)`;
- applies to departures;
- requires a preceding `PilotReportMade(Ready)`.

Scenario bank:

- LOWG single aircraft;
- LOWG active-runway partitions;
- later two-aircraft traffic.

Why v2 may be better:

- the same monitors can run over existing G0/G1/G2 traces without rewriting
  those goldens as source-unit specs;
- scenario authors and rule authors are decoupled.

Risk:

- monitor B depends on correctly identifying “departure runway-use instruction”
  and “preceding ready for this aircraft,” so trace projection must preserve
  aircraft identity and causal order cleanly.

### 3. Touch-and-go / full-stop intent

Monitor A:

- observes `PilotIntentDeclared(TouchAndGo)` or an equivalent mission intent
  fact;
- requires a later `ClearedTouchAndGo` before touchdown/landing roll.

Monitor B:

- observes `PilotIntentDeclared(FullStop)`;
- requires no `ClearedTouchAndGo` for that approach attempt and a later
  `ClearedToLand`.

Scenario bank:

- full-stop only;
- touch-and-go then full-stop;
- go-around then full-stop if available.

Why v2 may be better:

- positive and negative behavior are separate monitors over the same trace;
- the “may” source unit becomes a capability monitor only when intent is present,
  not an unconditional requirement.

Risk:

- today’s trace likely lacks a first-class `PilotIntentDeclared` fact. If it is
  inferred from mission internals, v2 collapses back into implementation
  knowledge. The projection must expose intent as a black-box fact or mark a
  model gap.

### 4. Essential aerodrome information timing

Monitor:

- observes `AircraftPhaseObserved` entering taxi or final approach;
- applies when `EssentialAerodromeInformation` is relevant and not already known;
- requires a prior `EssentialAerodromeInformationBroadcast` to that aircraft.

Scenario bank:

- runway condition hazard;
- lighting failure;
- temporary hazard;
- already-known vs not-known partitions;
- before taxi vs before final approach.

Why v2 may be better:

- this is naturally temporal: when an aircraft crosses a phase boundary, check
  what it knew before that boundary;
- the monitor can run over all traces, not only the bespoke essential-info
  scenario.

Risk:

- this design needs three missing projections: hazard relevance, aircraft
  knowledge, and information broadcast classification. This is a model-gap
  forcing case, not a likely green test.

### 5. Critical-phase radio silence except safety necessity

Monitor:

- observes every `TransmissionStarted`;
- applies when aircraft is in critical phase and the transmission is routine;
- requires absence, or equivalently fails if such a transmission exists.

Fuzzing:

- generate traces around altitude bands, e.g. 500..1500 ft;
- sample phase windows: takeoff roll, initial climb, late final, landing roll;
- sample routine vs safety-necessary transmission attempts.

Why v2 may be better:

- this is the clearest fit for monitor semantics: “for every event matching
  predicate P, Q must hold”;
- fuzzing is just varying the antecedent domain.

Risk:

- “safety necessity” is a semantic classification, not a protocol type today.
  Without it, the monitor either over-fails or cannot run. The design must treat
  missing classification as a model gap.

## Example v2 authoring shape

```kotlin
sourceMonitor("icao9432-taxi-limit") {
    sources("icao9432-extracted::taxi_4_4_en::417f64324f7495bf")
    observes<ControllerInstructionIssued<TaxiToHoldingPoint>>()
    requiresProjection(TraceProjection.ActiveRunway)
    requiresProjection(TraceProjection.AerodromeGeometry)

    appliesWhen { fact ->
        fact.instruction.isDepartureTaxi
    }

    check("destination is holding point for active runway") { fact, trace ->
        val activeRunway = trace.activeRunwayAt(fact.time, fact.aerodrome)
        fact.instruction.destination in trace.holdingPointsFor(activeRunway)
    }

    adequacy {
        requireApplicableCountAtLeast(1)
        partition("active-runway")
    }
}
```

The equivalent fuzz example:

```kotlin
scenarioFuzz("critical-phase-radio-window") {
    altitudeFt(500..1500)
    phase(oneOf(TakeoffRoll, InitialClimb, LateFinal, LandingRoll))
    transmissionClass(oneOf(Routine, SafetyNecessary))
    samples(100)
}
```

The scenario emits traces. The monitor checks them.

## Design review

### FP / type safety

This design should be more type-safe than FN37 if `TraceFact` is a sealed
hierarchy and monitors declare the concrete fact type they observe. Invalid
checks should be unrepresentable: a taxi monitor should receive only taxi
instruction facts, not stringly typed parameters.

The risk is projection incompleteness. If projection functions return `null`
or empty lists for unsupported concepts, v2 lies. Missing projection must be a
typed `ProjectionGap`, and monitors that require missing projections must report
model gaps loudly.

### Test architecture

This fits high-level testing better than FN37. Scenario traces can come from
existing goldens, focused scenarios, or fuzzed scenario generators. Monitors are
not unit tests; they are trace oracles. Pure protocol readback remains an
exception because it has an independent formal oracle and can be represented as
synthetic traces.

### Impact

The main architectural cost is a normalized `ConformanceTrace` projection. That
is also the main value: it creates a public test interface that another team
could target. If the projection is too wide, it becomes a second simulator
model. If it is too narrow, monitors reach into internals. The design lives or
dies on that boundary.

### Operational correctness

This design handles `may`/`should` better than FN37 because applicability is
explicit. A permission source unit becomes: “when the pilot requests/declares X,
the system can produce Y,” not “Y must always occur.” Phraseology source units
still need rendered phrase facts; typed instruction facts can only provide
partial evidence.

## Red team

### Attack: monitor activation can be vacuous

If no trace fact satisfies the antecedent, every monitor passes. This is the
classic temporal-monitor trap.

Mitigation: adequacy is mandatory. Each monitor declares expected activation
counts and partition coverage. A monitor with zero applicable facts is
`vacuous`, not green, unless explicitly marked support-only.

### Attack: trace projection hides implementation assumptions

If `PilotIntentDeclared` is projected from mission internals rather than an
observable pilot communication, the monitor appears black-box while smuggling in
implementation knowledge.

Mitigation: each `TraceFact` declares its source: observed transmission,
observed world state, fixture-authored fact, mission-authored fact, or inferred
fact. Reports distinguish observed from inferred evidence.

### Attack: too many monitors over too many traces become noisy

Running every monitor against every scenario can produce a wall of irrelevant
vacuity and gap reports.

Mitigation: monitors declare scenario tags and required partitions. The runner
uses compatibility filtering, then reports unmatched monitors separately as
coverage gaps.

### Attack: this becomes a second domain model

`ConformanceTrace` might grow until it duplicates `SimState`, with worse types.

Mitigation: trace facts are event-like and audit-facing only. They should answer
“what did the black-box run show?” not “what is the whole simulator state?” If a
monitor needs internal belief state, that is a design smell.

### Attack: fuzzing full traces is slow and flaky

Generating full sim traces for every fuzz sample may be too expensive.

Mitigation: separate generator tiers:

- synthetic pure traces for protocol monitors;
- fixture-backed partition sweeps for CI;
- full scenario fuzz only for focused/nightly runs;
- always record seed and generated scenario parameters.

### Attack: negative requirements are hard to prove

“No routine radio during critical phase” requires knowing that a routine
transmission would have happened if the system were wrong. Absence alone can be
weak.

Mitigation: pair negative monitors with scenario generators that attempt to
create candidate transmissions in the forbidden window. Adequacy must count
attempted candidates, not only observed transmissions.

## Comparison to FN37

FN37 is easier to author for a single source unit. FN38 is better for scaling
across many traces and for rules phrased as “whenever condition P holds, Q must
hold.”

FN37 feels like a conformance test DSL. FN38 feels like runtime verification
over simulator transcripts.

FN37 is probably better for early exploration. FN38 may be better as the
long-term architecture if we can build a clean `ConformanceTrace` projection.

## Recommendation

Do one short v2 spike, not a replacement. Implement only:

1. a tiny `TraceFact` sealed hierarchy;
2. `ConformanceTrace` projection for existing transmission records;
3. two green monitors: readback and taxi;
4. one intent-sensitive monitor: touch-and-go/full-stop;
5. two gap monitors: essential information and critical-phase radio silence;
6. a report that distinguishes pass, fail, vacuous, and projection-gap.

The decision point after that spike is simple:

- If monitors make the five cases clearer than FN37 specs, keep the monitor
  design and rebuild cleanly.
- If monitors feel indirect or projection-heavy, keep FN37’s source-unit spec
  direction and borrow only v2’s adequacy/vacuity reporting.

The main thing to learn is whether a monitor bank over black-box traces is a
better collaboration boundary than source-unit-owned specs.
