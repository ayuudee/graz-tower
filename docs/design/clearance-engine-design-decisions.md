# Clearance Engine — Design Decisions

Decisions made during ATC agent review (2026-04-13), with rationale for why certain
behaviours are correct for this simulation even when they differ from how a real-world
ATC system might be structured.

## Vectors and route supersession

Vector instructions (`FlyHeading`, `TurnHeading`, `ContinuePresentHeading`) are in
`ClearanceDomain.ROUTE`. A vector fully supersedes an active route clearance (`ClearedTo`).

ICAO Doc 4444 8.6.5.2 describes vectors as "suspending" rather than "cancelling" the route
clearance. In practice, this distinction is conceptual — the controller always explicitly
reinstates routing via `ResumeOwnNavigation`, `ProceedDirect`, or an approach clearance.
The pilot never silently reverts to the old route.

For this simulation, full supersession is correct: the old route clearance is terminal, and
any resumption of routing is a new clearance. Introducing a separate `VECTOR` domain would
add complexity without changing observable simulation behaviour.

## Approach clearances do not complete

`ClearedApproach` and `ClearedVisualApproach` have `completionCategory = null`. They
intentionally never "complete."

An approach clearance has no single completion criterion. The aircraft is "on the approach"
from clearance until either landing (a separate `ClearedToLand` in the RUNWAY domain) or
go-around (which supersedes ROUTE + LEVEL + SPEED + RUNWAY). The approach clearance stays
active until superseded — it does not need its own completion event.

## Approach clearances supersede LEVEL

`ClearedApproach` supersedes `{ROUTE, LEVEL, SPEED}`. This is correct in the general case:
the approach procedure defines its own vertical and speed profile.

If a controller wants a level restriction to survive the approach clearance (e.g., "maintain
FL100 until established on the localiser"), they should issue `MaintainAltitudeUntilEstablished`
in a compound clearance *with* the approach clearance, so both are part of the same envelope
and supersession does not apply between them.

## Hold-short release requires a new compound clearance

A standalone `CrossRunway` clearance will fully supersede an existing compound taxi clearance
(both are `GROUND` domain). This means the remaining taxi steps in the compound clearance
are lost.

This matches real-world practice: after an aircraft crosses a runway, the controller issues
a new clearance for the remaining taxi ("cross runway 09, taxi to stand 42 via Alpha") rather
than expecting the pilot to resume an earlier clearance. The clearance authoring layer should
follow this pattern.

## CancelClearance is handled by the authoring layer

`CancelClearance` carries an optional `ClearanceDomain` field, but the supersession engine
does not inspect instruction fields — it only uses metadata. Rather than adding special-case
logic to the engine, the authoring layer should identify the target clearance and set its
status to `CANCELLED` directly. The `CancelClearance` instruction type exists in the protocol
for phraseology representation (controller-to-pilot transmission), not as an engine primitive.

## Surface wind is nullable in takeoff/landing clearances

`ClearedForTakeoff.surfaceWind` and `ClearedToLand.surfaceWind` are `Wind? = null`. ICAO
requires surface wind in these clearances, but this is a protocol layer concern — it models
what *can* be expressed, not what *must* be expressed. Enforcement of mandatory fields belongs
in the clearance authoring/validation layer, not in the type definition. Making wind non-nullable
would force every test fixture and non-wind-relevant code path to construct a Wind object.

## Runway clearance null timing

`ClearedForTakeoff`, `ClearedToLand`, `ClearedTouchAndGo`, `ClearedLowApproach` have
`timing = null`. Their execution depends on pilot readiness and external conditions rather
than fitting into the sequential/immediate/persistent taxonomy used for compound clearance
step ordering. This is intentional — these clearances are typically standalone and are not
sequenced within compound clearances.

## Conditional taxi clearances

`TaxiTo`, `TaxiViaRunway`, and `AirTaxiTo` have `mayBeConditional = true`. While ICAO 4444
7.9 specifically mandates the conditional clearance format for runway-related movements,
"behind the [traffic], taxi to..." is common real-world European phraseology and is not
prohibited by ICAO. This matches operational practice.

## BehindTraffic predicate has no TrafficAction

`ConditionalPredicate.BehindTraffic` carries only a `TrafficRef`, while `AfterTraffic`
carries both `TrafficRef` and `TrafficAction`. These are distinct predicates with different
semantics: "behind" means follow (the traffic's action is implicit — moving in the same
direction), while "after" requires specifying what the traffic is doing (landing, departing,
etc.) per ICAO 4444 7.9.3.
