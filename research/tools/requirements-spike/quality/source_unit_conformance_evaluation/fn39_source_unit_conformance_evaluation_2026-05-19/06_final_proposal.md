# FN39 Final Proposal: Source-Backed Conformance Monitors

Date: 2026-05-19

## Recommendation

Build the permanent source-unit conformance strategy around source-backed
conformance monitors over a typed black-box trace.

Do not promote the FN37 `SourceUnitSpec` helper as the final runner. Keep its
useful authoring ideas, but move execution and audit responsibility into a
monitor runner that observes high-level scenarios and synthetic protocol
exchanges through a narrow `ConformanceTrace`.

The proposed shape is:

1. normal high-level scenarios or synthetic protocol fixtures produce trace
   facts;
2. source-unit contracts declare applicability, domain samples, required facts,
   adequacy expectations, and expected model gaps;
3. monitors consume only trace facts and contract metadata;
4. the runner emits durable Markdown and JSON reports grouped by source unit,
   scenario, monitor, generated domain sample, and outcome.

This gives the project a regulatory foundation that can grow across the source
unit corpus without turning every source unit into a bespoke scenario test.

## Why This Direction

### It matches the test standards

The main tests remain believable high-level scenarios. Source-unit checks become
an audit layer over the scenario output, rather than a second scenario harness
or a low-level predicate test suite.

### It makes coverage measurable

One-off tests can show that a case works. They do not naturally show whether a
source unit was applicable, vacuous, blocked by a known model gap, or missing a
projection. The monitor runner can report those states explicitly.

### It supports independent development

A team should be able to write a source-unit contract against the trace
interface without knowing how the sim internally models clearances, pilot
intent, or controller state. That only works if the trace interface is small,
typed, documented, and observation-only.

### It separates semantic and phraseology claims

Semantic operational content can be monitored before the full phraseology layer
is ready. Phraseology monitors require explicit cited source-unit support and
should not be inferred from semantic checks.

## Rejected Alternatives

### Promote FN37 SourceUnitSpec directly

Rejected. FN37 proved that code-authored source-unit checks feel good, but the
helper currently owns scenario setup and uses stringly fuzz parameters. That
couples source-unit conformance to sim internals and leaves audit reporting too
thin.

### Write one bespoke scenario per source unit

Rejected as the primary strategy. It is simple for a handful of cases, but it
does not scale to corpus progress tracking, vacuity detection, expected gap
management, or fuzzed domain coverage.

### Build monitor v2 without an authoring shell

Rejected. Pure monitors are too opaque for source-unit authors. The final design
needs contracts that make source ids, applicability, domain samples, and
adequacy expectations explicit.

## Proposed Architecture

### `ConformanceTrace`

A typed sequence of observed facts produced by either the sim or a synthetic
protocol fixture.

Rules:

- facts are observations, not conclusions;
- facts carry provenance;
- facts are sealed and typed;
- missing projection support yields `UnexpectedGap` unless registered as an
  expected gap.

Initial fact families:

- `TransmissionFact`
- `InstructionFact`
- `PilotReportFact`
- `ScenarioInputFact`
- `AircraftPhaseFact`
- `TimingFact`
- `FixtureMetadataFact`

### `TraceFactProvenance`

Every trace fact records:

- scenario or fixture id;
- origin adapter;
- source event id where available;
- timestamp where applicable;
- extraction path;
- whether the fact is sim-observed or synthetic.

### `SourceUnitContract`

Code-authored metadata for a source unit or small group of source units.

Fields:

- source unit id and citations;
- source-unit text summary;
- applicability predicate;
- required monitor capability tags;
- typed domain partitions;
- bounded generator definitions;
- adequacy expectations;
- expected gap ids;
- monitor bindings.

This is the retained, cleaned-up version of the useful FN37 authoring style.

### `ConformanceMonitor`

A pure checker over `ConformanceTrace` plus a compatible
`SourceUnitContract`.

Monitors return a sealed outcome:

- `Pass`
- `Fail`
- `Vacuous`
- `ExpectedGap`
- `UnexpectedGap`

No monitor may return nullable results or silently skip a compatible scenario.

### `MonitorAdequacy`

An explicit declaration of what counts as a meaningful run:

- positive activation count;
- negative candidate-attempt count;
- domain partition coverage;
- accepted vacuity reasons.

### `ScenarioBank`

The existing high-level scenario style remains the source of sim traces. The
bank provides stable scenario ids and capability tags, but does not know source
unit semantics.

Synthetic protocol fixtures sit beside it for pure exchange rules such as
readback field requirements.

### Report Artifacts

The runner emits:

- a Markdown summary for humans;
- JSON records for follow-up tooling;
- source-unit coverage table;
- scenario/monitor compatibility table;
- outcome records with supporting fact ids;
- typed gap references linked to `.plan` or the relevant Flow epic.

## Five-Case Acceptance Probe

The first implementation spike should prove the architecture against five
disparate cases.

1. Readback required fields
   - Source: ICAO 9432 readback source unit already used by FN37.
   - Input: synthetic protocol trace.
   - Expected: green `Pass`, with omitted-field variants producing `Fail`.

2. Taxi clearance content
   - Input: high-level LOWG taxi scenario.
   - Expected: `Pass` if instruction and transmission facts expose route,
     runway, hold-short, and callsign content.

3. Touch-and-go versus full-stop intent
   - Input: high-level circuit scenario.
   - Expected: `Pass` if pilot intent is observable; otherwise `ExpectedGap`
     against a tracked landing-intent projection item.

4. Essential aerodrome information
   - Input: scenario or synthetic trace that establishes weather/runway
     information applicability.
   - Expected: likely `ExpectedGap` until aerodrome-information modelling is
     implemented.

5. Critical-phase radio silence
   - Input: circuit or synthetic phase trace with critical-phase intervals.
   - Expected: likely `ExpectedGap` until workload/phase communication policy is
     modelled.

The suite succeeds if each outcome is honest and auditable. It does not require
every source unit to be green on the first implementation pass.

## Implementation Plan

### Phase 1: minimal trace and runner

- Define sealed `TraceFact` families needed for the five cases.
- Define `TraceFactProvenance`.
- Define sealed monitor outcomes.
- Build a runner that executes contracts over traces and emits Markdown/JSON.
- Add a synthetic protocol trace adapter.

### Phase 2: first monitors

- Implement readback required-field monitor.
- Implement taxi-clearance content monitor.
- Implement monitor adequacy accounting.
- Add outcome report tests over the synthetic and taxi examples.

### Phase 3: model-gap monitors

- Add landing-intent, essential-information, and critical-phase contracts.
- Register expected gaps in `.plan` where the current model cannot expose the
  needed fact honestly.
- Ensure reports distinguish `ExpectedGap`, `UnexpectedGap`, and `Vacuous`.

### Phase 4: high-level five-case integration

- Run the five-case probe through one integration test.
- Assert source-unit ids, scenario ids, outcome types, activation counts, and
  gap ids.
- Keep assertions at the report level rather than re-testing each predicate in
  isolation.

### Phase 5: decide permanent rebuild

- Review ergonomics after the five-case probe.
- If the design still feels right, replace the throwaway FN37 helper with the
  permanent contract/monitor API.
- If it does not, keep only the report and trace lessons and restart from the
  source-unit contract boundary.

## Verification Gates

For the next implementation spike:

- focused monitor-runner integration test over the five-case probe;
- source citation validation remains green;
- `./gradlew :sim:jvmTest --tests '*SourceUnit*'` or the equivalent focused
  suite after naming settles;
- `./gradlew detekt`;
- `git diff --check`;
- update `.plan` for every expected gap that remains.

## Review Considerations

### FP / type safety

The core API should use sealed outcomes, sealed trace facts, typed source ids,
typed capability tags, and typed gap ids. No `else` branch should swallow new
fact or outcome types. If a trace adapter cannot project a required fact, it
must emit a typed gap outcome through the monitor/report path rather than
inventing a default.

### Test architecture

The canonical test should be report-level and high-level: run a small scenario
bank plus synthetic protocol fixture, then assert the conformance report. Unit
tests are appropriate only for pure parsing or generator boundaries with an
independent oracle. The five-case probe is the first acceptance wall.

### Impact

This adds an explicit conformance layer, which is additional architecture. The
payoff is that source-unit coverage, vacuity, expected gaps, and generated
domain partitions become visible. The main risk is projection creep; the
projection charter and provenance requirement are the safeguards.

### Operational correctness

Operational claims must come from source-unit ids and their citations. Monitors
must not invent ATC doctrine. Phraseology checks stay separate until the
phraseology source units are specific enough to support rendered transmission
assertions.

## Final Position

Proceed with a monitor-first implementation spike, using the five-case probe as
the acceptance target. Treat FN37 and FN38 as disposable research artifacts whose
lessons have been folded into this proposal. The next permanent-looking code
should start from `ConformanceTrace`, `SourceUnitContract`, monitor outcomes,
and report generation, not from the existing `SourceUnitSpec` runner.
