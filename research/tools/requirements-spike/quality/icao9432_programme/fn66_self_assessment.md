# fn-66 Self Assessment

Epic: `fn-66-icao-9432-vehicle-1a-vehicle-movement`

## Scope Completed

- Added a minimal sim-level vehicle actor and movement lifecycle:
  `VehicleId`, `VehicleState`, vehicle driver/controller utterances,
  standby/hold/permission phases, and permission-id-gated movement to a
  clearance limit.
- Added source-backed scenario coverage for ICAO Doc 9432 §5.2.1, §5.2.2,
  and §5.2.3 movement-permission obligations.
- Updated chunk-07 coverage docs and blocker manifest.

## Principal Checklist

- Totality: new sealed leaves were added to sim radio/event types. Exhaustive
  test helper `when` expressions were updated with explicit vehicle branches;
  no catch-all `else` was added.
- Reversal / cleanup: standby and hold clear any active permission. Arrival at
  a clearance limit is keyed by `VehiclePermissionId`; stale arrival events from
  superseded permissions are defined no-ops.
- Interaction coverage: tests use the real `TransmissionStart` /
  `TransmissionEnd` radio path and `runUntilWithStateTrace`, not direct state
  mutation. Vehicle driver requests and controller permissions are represented
  as radio utterances.
- Test coverage: `Icao9432VehicleMovementSourceBackedScenarioTest` covers
  structured first-call content, standby non-movement, hold-position
  non-movement, intermediate clearance-limit stop, onward request, and
  permission-after-blocked-state progression.
- New-field audit: `SimState.vehicles` and `nextVehiclePermissionId` have
  defaults and are written only by vehicle lifecycle handlers in this pass.
  Existing aircraft-state mutation sites are not coupled to vehicle movement.
- Operational correctness: source claims are cited to ICAO Doc 9432 §5.2.1
  first-call content, §5.2.2 standby, and §5.2.3 hold-position /
  clearance-limit workflow. §5.1.2 vigilance/local procedures, §5.2.4 apron
  traffic, §5.3 runway crossing, and §5.4 towing remain blocked or later.
- Error handling honesty: unknown vehicle ids throw loudly when processing a
  vehicle transmission or instruction. Stale permission-arrival events are not
  errors because they are reachable after a valid superseding hold/standby.
- Deferment honesty: remaining work is already represented in the chunk-07
  source plan, coverage report, blocker manifest, and open fn-65/fn-67 epics.
  No new deferment bucket is needed for this pass.

## Residual Risks For Review

- Vehicle support is intentionally sim-level and not yet integrated with
  controller BDI/procedure selection. That is acceptable for fn-66 only if the
  source-mapped evidence is described as vehicle lifecycle evidence, not full
  controller policy coverage.
- The first-call source unit is a split claim: structured content is covered;
  rendered wording remains `PHRASE-1`.
- Runway crossing, runway occupancy, vehicle/tow extent geometry, towing
  metadata, and apron-traffic policy must remain blocked.
