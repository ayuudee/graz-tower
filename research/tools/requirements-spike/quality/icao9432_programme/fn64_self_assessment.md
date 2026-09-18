# fn-64 Self-Assessment

Epic: `fn-64-icao-9432-pushback-1-pushback-and`

## Commandment Audit

- Totality: updated exhaustive `MissionStep`, `AtcInstruction`,
  `ControllerEvent`, and `GroundDepartureStage` dispatches. No catch-all
  `else` was added to hide the new pushback states.
- Reversal completeness: no reversible state transition pair was added. The
  new lifecycle is forward-only: request, approval, ground-crew completion,
  taxi request. The existing GROUND arrival/departure reconciliation remains
  total over typed positions.
- Interaction coverage: the source-backed sim tests exercise pilot mission
  authoring, radio request derivation, controller rule selection,
  certification, sim event scheduling, mission advancement, controller belief
  projection of the ground-crew signal, and the subsequent taxi clearance.
- Test coverage for known features: `Icao9432PushbackSourceBackedScenarioTest`
  covers the new source-mapped behavior; `GroundDepartureTaskShapeSpec` pins
  the opt-in mission-tree shape; existing model-gap coverage now only keeps
  powerback blocked.
- New-field audit: no state data-class field was added. New sealed leaves and
  events were audited through compile/detekt and focused tests.
- Operational correctness: pushback behavior is cited to ICAO Doc 9432 §4.3.
  The greened branch is a scenario-authored ATC/GROUND branch and does not
  claim apron-management policy coverage. Powerback remains blocked because tug
  pushback does not satisfy reverse engine-powered movement.
- Error handling honesty: `GroundCrewPushbackComplete` throws only when the sim
  reaches an internally scheduled event for an aircraft/mission shape that the
  scheduler should have made impossible. Unsupported apron-management and
  powerback branches remain represented as explicit gaps rather than silent
  defaults.
- Deferment honesty: apron-management, powerback, and rendered phraseology
  remain visible in the programme blocker/gap docs; no new silent deferment was
  introduced.

## Review Considerations

- FP / type safety: the pushback route uses sealed mission steps, sealed
  controller events, a typed GROUND stage, and typed sim event evidence. The
  certification plan adds only `PushbackApproved` to surface authorization;
  startup remains unsupported.
- Test architecture: the primary proof is a high-level source-backed sim
  scenario with ordering assertions. The unit pin is limited to mission-tree
  shape because that is a small independent contract the scenario relies on.
- Impact: touched pilot mission/cognition, controller observation/procedure,
  certification, sim event handling, source-backed tests, and programme
  reports. Existing non-pushback departures remain default because pushback is
  opt-in via `requiresPushback`. Runtime local-procedure policy is still future
  work, not a shipped claim.
- Operational correctness: ICAO Doc 9432 §4.3 supports the branch-scoped
  request authority and the post-manoeuvre ground-crew signal. The work does
  not assert phraseology strings or apron-management behavior.
