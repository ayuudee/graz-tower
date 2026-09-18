# fn-63 review resolution

Reviewer: Harvey (`01a0b450-2e50-7ec1-92fa-7eb6006bf03b`)

## Findings addressed

1. No evidence-only path can honestly green `95034efc191fa9cd`.

Resolution:

- Kept the source unit blocked.
- Explicitly recorded that ICAO Doc 9432 §4.2.3 requires approval before
  engine start, so a `StartupApproved` instruction alone is insufficient.
- No synthetic lifecycle evidence was added.

2. D-PF.1 coupling is real.

Resolution:

- The manifest now treats startup lifecycle coverage as requiring an
  airport-conditional startup requirement, live request/approval workflow, and
  typed engine-start lifecycle event.
- `CLEARANCE_DELIVERY` remains unclaimed: the current controller
  reconciliation still treats it as unmodelled.

3. Default engine state is not engine-start evidence.

Resolution:

- The manifest explicitly rejects `AircraftState.engineRunning == true` as
  source evidence because it is a default physics/abort-failure state, not an
  orderable lifecycle transition.

4. Existing LOWG goldens should not be globally changed.

Resolution:

- Future D-PF.1 work is directed toward a dedicated startup-required
  fixture/scenario with default no-startup fixtures preserving the taxi-first
  path.

5. Tests must prove behavior rather than the gap.

Resolution:

- fn-63 adds no green test because no real lifecycle behavior was implemented.
- The current model-gap coverage remains the honest assertion for this row.

## Residual risk

The start-up lifecycle row remains uncovered until D-PF.1 is implemented.
Phraseology examples, delayed start-up timing, no-ATIS information request, and
critical-phase safety exceptions remain under their existing phraseology and
policy blockers.
