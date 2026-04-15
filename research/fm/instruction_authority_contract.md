# Instruction Authority Contract

This note narrows the greenfield authority story into the smaller instruction
subset that `research/fm` should currently treat as authority-resolved.

It builds on
[aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr2/research/fm/aviation_world_extraction_contract.md).
That note freezes the **shape** of the authority payload. This note freezes the
current **instruction-to-grant mapping** that is justified strongly enough to
drive Lean work now.

## Scope

This is not a final controller-jurisdiction matrix for every instruction in the
future project.

It is a conservative proof contract:

- resolve only the instruction families whose role/entity ownership is already
  stable in the greenfield docs
- leave the rest explicitly unresolved
- do not guess authority semantics just to make the theorem surface look bigger

## Resolved Instruction Families

The following instruction families are now treated as authority-resolved:

- `TaxiTo` -> `(taxiway, taxi)`
- `HoldPosition` -> `(taxiway, taxi)` on the delivered current-shape ground
  boundary
- `HoldShortOf` -> `(runway, cross)` on the delivered current graph-backed
  ground boundary
- `CrossRunway` -> `(runway, cross)`
- `BacktrackRunway` -> `(runway, backtrack)` on the current-shape backtrack
  boundary
- `LineUpAndWait` -> `(runway, lineUp)`
- `ClearedForTakeoff` -> `(runway, takeoff)`
- `ClearedToLand` -> `(runway, land)`
- `GoAround` -> `(runway, goAround)` on the current-shape runway boundary
- `ClearedLowApproach` -> `(runway, lowApproach)`
- `ClearedTouchAndGo` -> `(runway, touchAndGo)`
- `ClearedTo` -> `(fix, routeClearance)` on the delivered world-backed
  route-bearing boundary
- `JoinCircuit` -> `(circuitProcedure, circuit)`
- `ExtendDownwind` -> `(circuitProcedure, circuit)` on the delivered
  world-backed route-adjacent boundary
- `Orbit` -> `(circuitProcedure, circuit)` on the delivered world-backed
  route-adjacent boundary
- `ContinueApproach` -> `(instrumentApproach, sequence)` on the delivered
  world-backed route-adjacent boundary
- `RemainOutsideControlledAirspace` -> `(airspaceVolume, airspaceTransit)` on
  the delivered world-backed airspace boundary
- `ClearedToEnterControlZone` -> `(airspaceVolume, airspaceTransit)` on the
  delivered world-backed airspace boundary
- `SpecialVfrClearance` -> `(airspaceVolume, airspaceTransit)` on the
  delivered world-backed airspace boundary
- `ContactFrequency` -> `(radioRole, contact)`
- `MonitorFrequency` -> `(radioRole, monitor)`
- `ProceedDirect`, `LeaveHoldProceedDirect`, `WhenAbleProceedDirect`, and
  `RejoinSidAt` -> `(fix, routeClearance)` on the delivered world-backed
  route/vector-control boundary
- `JoinAirway` -> `(airway, routeClearance)` on that same delivered
  route/vector-control boundary
- `ResumeOwnNavigation`, `RouteAsFiled`, `FlyHeading`, `TurnHeading`,
  `TurnByDegrees`, `ContinuePresentHeading`, `StopTurn`, and
  `InterceptLocaliser` ->
  `(airspaceVolume, routeClearance)` on the delivered current-shape
  route/vector-control boundary, now widened through its first narrow
  immediate-adjunct compound layer and with `TurnByDegrees` closed on the
  explicit observed-turn-progress model
- `ClimbTo`, `DescendTo`, `DescendWhenReady`, `ExpediteClimb`,
  `ExpediteDescend`, `MaintainLevel`, `StopClimbAt`, `StopDescentAt`,
  `MaintainAtOrAbove`, `MaintainAtOrBelow`, `AfterPassingLevelClimbTo`,
  `AfterPassingLevelDescendTo`, `MaintainAltitudeUntilEstablished`, and
  `AvoidLevel` -> `(airspaceVolume, altitude)` on the delivered current-shape
  air-modifier boundary
- `MaintainSpeed`, `ReduceSpeedTo`, `IncreaseSpeedTo`, `MinimumCleanSpeed`,
  and `ResumeNormalSpeed` -> `(airspaceVolume, speed)` on that same delivered
  air-modifier boundary
- `SetPressure` and `CancelClearance` -> `(airspaceVolume, information)` on
  that same delivered current-shape air-modifier boundary
- `SetSquawk` -> `(radioRole, squawk)` on the delivered current-shape
  communications/surveillance boundary
- `ConfirmSquawk` -> `(radioRole, squawk)` on the delivered current-shape
  communications/surveillance boundary
- `SquawkIdent` -> `(radioRole, squawk)` on the delivered current-shape
  communications/surveillance boundary
- `SquawkStandby` -> `(radioRole, squawk)` on the delivered current-shape
  communications/surveillance boundary
- `SquawkNormal` -> `(radioRole, squawk)` on the delivered current-shape
  communications/surveillance boundary
- `StopSquawk` -> `(radioRole, squawk)` on the delivered current-shape
  communications/surveillance boundary
- `ClearedApproach` -> `(instrumentApproach, approachClearance)`
- `HoldAt` -> `(holdingPattern, hold)`

The longstanding envelope-facing subset is mirrored in
[ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
through `instructionRequiredAuthorityGrant?`, and the delivered world-backed
Phase B route-adjacent mappings are mirrored in
[GreenfieldRouteAdjacentWorldBackedAuthority.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldRouteAdjacentWorldBackedAuthority.lean)
for the delivered world-backed Phase B route-adjacent surface.

On the published-handoff jurisdiction branch, `ContactFrequency` and
`MonitorFrequency` keep those same `(radioRole, contact|monitor)` grants.
Published handoffs add world-backed readiness and completion facts, not a new
authority family.
The delivered broader ground/surface movement mappings are now mirrored in
[GreenfieldGroundMovementCurrentShape.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/GreenfieldGroundMovementCurrentShape.lean)
for the current graph-backed ground branch.

## Why These Are Safe To Freeze

These instruction families line up directly with the greenfield role model:

- taxi movement on taxiways
- current-shape hold-position control as a conservative taxiway/taxi action on
  the current engine
- runway crossing and runway-use operations
- current graph-backed hold-short resolution on the current runway-adjacent
  protection boundary
- current graph-backed route-bearing progression to a published clearance limit
- circuit-procedure control
- world-backed circuit-sequencing instructions that stay on the tower-side
  circuit family
- world-backed `ContinueApproach` as approach sequencing on the present
  approach family, still at the same type-level authority granularity used
  elsewhere in the greenfield model
- world-backed controlled-airspace entry / restriction instructions over the
  existing type-level `airspaceVolume / airspaceTransit` authority family
- approach clearance over approach entities
- holding-pattern control
- radio role / handoff style instructions
- route/vector-control instructions on the current explicit published-fix /
  airway + vector-state boundary
- level/speed/pressure/admin modifiers on the delivered current-shape
  air-modifier boundary

They do not require the project to settle the harder open questions around
clearance limits, mixed supersession, or derived pilot-intent semantics first.

## Explicitly Unresolved Families

The following instruction families remain intentionally unresolved at the
authority-mapping layer:

- `StartupApproved`
- `ReportDownwind`
- `ReportFinal`
- `Proceed`
- `CrossControlledAirspace`

The reason is not "these instructions have no authority semantics."
The reason is "the right authority family is still entangled with other open
semantic questions."

Example:

- `CrossControlledAirspace` likely belongs to a broader coordination/airspace
  authority layer that is not yet narrowed enough here

## Conservative Lean Rule

The current Lean rule should remain conservative:

- if an instruction family is mapped, issuer authorization may be checked
  against extracted role grants
- if an instruction family is not mapped yet, it is treated as not authorized
  by the current proof-side checker

That is not a product claim that the instruction is forbidden forever.
It is a proof claim that `research/fm` has not justified the mapping yet.

## Current Lean Surface

The current authority scaffold in
[ClearanceEnvelope.lean](/home/andrew/dev/projects/twr2/research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean)
now provides:

- `instructionRequiredAuthorityGrant?`
- `instructionIssuerAuthorized`
- `instructionsIssuerAuthorized`
- `compoundClearanceIssuerAuthorized`
- `structuredClearanceIssuerAuthorized`
- `compoundClearanceFrontierIssuerAuthorized`
- `structuredClearanceFrontierIssuerAuthorized`
- `compile_clearance_instruction_as_issuer`
- `compile_frontier_as_issuer`
- `compile_clearance_content_frontier_as_issuer`
- `compile_structured_clearance_frontier_as_issuer`

This is enough to support future theorem work around:

- issuer authorization for single instructions
- envelope-wide authorization for compound clearances
- authorization-aware compilation at the current frontier boundary
- the separation between authority checks and local kernel legality checks

The current Lean lemmas now also establish the bridge from whole-content
authorization to frontier authorization for compound and structured clearances.
For the currently resolved subset, they also establish both directions needed
for a conservative checked compile seam:

- if the frontier is authorized, the checked frontier/content/structured
  compilers reduce to the existing unchecked compiler surface
- if checked frontier/content/structured compilation succeeds, that success
  itself proves frontier-level issuer authorization
- successful checked frontier/content/structured compilation also preserves the
  selected frontier instruction set

## Next Step

The next authority increment should not be "map every remaining instruction."

It should be:

1. keep the delivered world-backed Phase B authority mappings stable and aligned
   to the Kotlin model
2. keep the delivered world-backed route/vector-control and route-bearing
   mappings aligned to the Kotlin model and parity inventory
3. decide the right authority families for the still-unresolved coordination
   instructions
4. only then widen the mapping surface again
