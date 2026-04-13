# Instruction Authority Contract

This note narrows the greenfield authority story into the smaller instruction
subset that `research2` should currently treat as authority-resolved.

It builds on
[aviation_world_extraction_contract.md](/home/andrew/dev/projects/twr/research2/aviation_world_extraction_contract.md).
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

- `TaxiVia` -> `(taxiway, taxi)`
- `CrossRunway` -> `(runway, cross)`
- `BacktrackRunway` -> `(runway, backtrack)`
- `LineUpAndWait` -> `(runway, lineUp)`
- `ClearedForTakeoff` -> `(runway, takeoff)`
- `ClearedToLand` -> `(runway, land)`
- `ClearedTouchAndGo` -> `(runway, touchAndGo)`
- `JoinCircuit` -> `(circuitProcedure, circuit)`
- `ContactFrequency` -> `(radioRole, contact)`
- `MonitorFrequency` -> `(radioRole, monitor)`
- `ClearedApproach` -> `(instrumentApproach, approachClearance)`
- `HoldAt` -> `(holdingPattern, hold)`

These mappings are now mirrored in
[ClearanceEnvelope.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/ClearanceEnvelope.lean)
through `instructionRequiredAuthorityGrant?`.

## Why These Are Safe To Freeze

These instruction families line up directly with the greenfield role model:

- taxi movement on taxiways
- runway crossing and runway-use operations
- circuit-procedure control
- approach clearance over approach entities
- holding-pattern control
- radio role / handoff style instructions

They do not require the project to settle the harder open questions around
clearance limits, mixed supersession, or derived pilot-intent semantics first.

## Explicitly Unresolved Families

The following instruction families remain intentionally unresolved at the
authority-mapping layer:

- `StartupApproved`
- `HoldPosition`
- `HoldShortOf`
- `GoAround`
- `Orbit`
- `ExtendDownwind`
- `ReportDownwind`
- `ReportFinal`
- `ContinueApproach`
- `Proceed`
- `ClearedTo`
- `ClimbTo`
- `DescendTo`
- `ReduceSpeedTo`
- `SquawkCode`
- `CrossControlledAirspace`

The reason is not "these instructions have no authority semantics."
The reason is "the right authority family is still entangled with other open
semantic questions."

Examples:

- `ClearedTo` depends on the still-open route/intention/limit theorem surface
- `ClimbTo`, `DescendTo`, and `ReduceSpeedTo` may belong to a higher airspace or
  sequencing authority contract rather than to one route entity family
- `HoldShortOf` is operationally simple but its authority story still sits at an
  awkward boundary between taxiway movement and runway-adjacent protection
- `SquawkCode` and `CrossControlledAirspace` likely belong to a broader
  coordination/airspace authority layer that is not yet narrowed enough here

## Conservative Lean Rule

The current Lean rule should remain conservative:

- if an instruction family is mapped, issuer authorization may be checked
  against extracted role grants
- if an instruction family is not mapped yet, it is treated as not authorized
  by the current proof-side checker

That is not a product claim that the instruction is forbidden forever.
It is a proof claim that `research2` has not justified the mapping yet.

## Current Lean Surface

The current authority scaffold in
[ClearanceEnvelope.lean](/home/andrew/dev/projects/twr/research2/lean/CertifiedAtc/ClearanceEnvelope.lean)
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

1. prove small issuer-authorization lemmas above the current resolved subset
2. decide the right authority families for the unresolved airspace / route /
   coordination instructions
3. only then widen the mapping surface
