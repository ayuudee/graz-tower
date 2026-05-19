# FN39 Iteration Log

Date: 2026-05-19

This log records the design changes made after the evaluation matrix, senior
reviews, and red-team pass. The goal is to turn the comparison into one final
candidate that is concrete enough to implement next.

## Iteration 1: discard SourceUnitSpec-as-runner

Decision: do not carry forward FN37's helper as the primary runner.

Reason: the helper was successful for fast exploration, but it owns too much
scenario setup and therefore pulls conformance work away from the normal
high-level scenario architecture.

Carried forward:

- source-unit identity in every check.
- author-visible domain declarations.
- witness and partition concepts.
- fuzz tiers.
- explicit non-vacuity.
- typed model-gap visibility.

Discarded:

- helper-owned sim setup.
- string parameter maps.
- per-test-only reporting.

## Iteration 2: define the primary concept as source-backed monitors

Decision: the final candidate is a source-backed conformance monitor
architecture.

Shape:

- a scenario or synthetic exchange produces a `ConformanceTrace`.
- monitors consume trace facts and source-unit contracts.
- each monitor emits an audit outcome.
- reports aggregate outcomes by source unit, monitor, scenario, domain sample,
  and gap id.

This keeps test scenarios high-level while making source-unit coverage explicit.

## Iteration 3: constrain the trace projection

Review finding addressed: monitor v2 could become a second implementation.

Revision:

- `ConformanceTrace` contains observed facts only.
- Facts may represent transmissions, instructions, pilot reports, scenario
  inputs, fixture metadata, and timing/phase observations.
- Facts must not encode conformance conclusions such as "readback is correct" or
  "clearance was lawful".
- Every fact has provenance: origin, source event id where available, timestamp
  where applicable, scenario id, and extraction path.

This makes the projection auditable and keeps monitors responsible for
source-backed interpretation.

## Iteration 4: make outcome semantics total

Review and red-team finding addressed: pass/fail alone hides vacuity and model
gaps.

Revision: every monitor run returns one of:

- `Pass`: applicability was established and the requirement was observed.
- `Fail`: applicability was established and the requirement was contradicted or
  missing.
- `Vacuous`: the monitor was compatible with the scenario, but applicability did
  not activate.
- `ExpectedGap`: the source unit is relevant, but a typed known gap prevents a
  conclusive assertion.
- `UnexpectedGap`: the trace lacks a required projection, fact, or model
  concept and no known gap is registered.

`ExpectedGap` must link to `.plan` or an equivalent tracked backlog item.

## Iteration 5: make adequacy mandatory

Review finding addressed: monitors can silently pass without exercising the
requirement.

Revision:

- monitors declare adequacy expectations.
- positive rules declare minimum applicability activations.
- negative rules declare candidate-attempt counters, so "nothing happened" is
  not automatically a pass.
- reports display activation counts and compatibility filtering decisions.

This turns vacuity into a visible result rather than hidden test success.

## Iteration 6: separate compatibility from vacuity

Review finding addressed: over-broad monitors create noisy reports.

Revision:

- scenarios and synthetic traces declare typed capabilities.
- monitors declare required capabilities.
- incompatible pairs are skipped as non-applicable by construction and do not
  count as vacuity.
- compatible-but-inactive pairs report `Vacuous`.

Initial capability vocabulary for the five cases:

- `ProtocolSynthetic`
- `ReadbackExchange`
- `TowerCircuit`
- `TaxiClearance`
- `LandingIntent`
- `AerodromeInformation`
- `CriticalPhase`

## Iteration 7: support synthetic protocol traces

Review finding addressed: pure protocol requirements should not require a full
sim run.

Revision:

- the architecture accepts traces from both sim scenarios and synthetic
  controller/pilot exchange fixtures.
- monitors do not care which adapter produced the trace.
- provenance makes synthetic traces explicit in reports.

This lets readback rules run cheaply while keeping the same source-unit report.

## Iteration 8: tier fuzzing

Red-team finding addressed: fuzzing can become another unbounded overnight run.

Revision:

- CI tier: explicit representative partitions only.
- nightly/local tier: bounded generated samples with deterministic seed records.
- exploratory tier: wider fuzzing against mature monitors, not required for
  every commit.

Reports include the partition or seed used for each generated sample.

## Iteration 9: keep semantic and phraseology checks separate

Red-team finding addressed: phraseology is not yet complete enough to assert
rendered radio text broadly.

Revision:

- semantic monitors assert required operational content.
- phraseology monitors assert cited phrase shape only when the relevant source
  unit provides enough phraseology detail.
- no semantic monitor may claim phraseology compliance by implication.

## Iteration 10: revise the five-case target set

The five disparate cases remain the acceptance probe for the next
implementation spike:

1. Readback required fields: synthetic protocol trace, expected green.
2. Taxi clearance content: high-level taxi scenario, expected green if trace
   projection exposes clearance facts.
3. Touch-and-go versus full-stop intent: high-level circuit scenario, expected
   green or `ExpectedGap` depending on available landing-intent facts.
4. Essential aerodrome information: high-level aerodrome-information scenario,
   likely `ExpectedGap` until information-broadcast modelling exists.
5. Critical-phase radio silence: high-level circuit or synthetic phase trace,
   likely `ExpectedGap` until workload/phase policy exists.

The important result is not that all five are green immediately. It is that
green, vacuous, expected-gap, and unexpected-gap outcomes are distinguishable and
auditable.

## Review Considerations

### FP / type safety

The revised design requires sealed outcome types and sealed trace fact types.
No monitor should return nullable or string-coded outcomes. Gap ids, capability
tags, source ids, and domain samples should be typed values or smart
constructors.

### Test architecture

The next spike should test the architecture at the monitor-runner/report level,
not by unit-testing individual predicates in isolation. One integration test
should run the five-case sample suite and assert the report contains the
expected mix of outcomes and source ids.

### Impact

This design adds a projection/reporting layer, which is real complexity. It
reduces coupling between source-unit tests and sim internals by making the trace
adapter the only bridge. The main failure mode is letting the projection grow
into a second model; the projection charter is therefore part of the design, not
documentation garnish.

### Operational correctness

The monitors may only encode operational claims when tied to source-unit ids
and citations already present in the source-unit corpus. Phraseology claims are
deferred unless source units provide the phraseology detail needed for a cited
assertion.
