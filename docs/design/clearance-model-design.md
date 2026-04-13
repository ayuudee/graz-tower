# Clearance Model Design

## What carries forward

The existing clearance lifecycle is formally verified and correct. The following are retained without structural change:

- **State machine**: Issued, ReadbackPending, ConditionPending, Active, Completed, Superseded, Cancelled. Terminal states are absorbing. Verified in `ClearanceLifecycle.tla` and `ClearanceActivation.tla`. Future formalisation in Lean.
- **Transition function**: `transition(current, event, hasCondition)` — pure, deterministic, no world dependencies.
- **ClearanceEvent**: Deliver, PilotReadback, ConditionMet, Complete, Supersede, Cancel.
- **ClearanceCondition**: Subject aircraft + condition predicate. Polling-based evaluation each tick. Conditions reference specific traffic and actions (AfterTraffic, BehindTraffic) as defined in `ConditionalPredicate` in Instruction.kt.
- **Supersession rules**: One instruction making another obsolete (GoAround supersedes ClearedToLand, etc.). Scoped by clearance domain — see below.
- **Conflict detection**: Preventing incompatible concurrent clearances.
- **Radio-driven lifecycle**: Clearances exist because of radio transmissions. Issue at tick N, readback at tick N+1.
- **Reconciliation phase ordering**: Complete → prune → update runway → evaluate conditions → activate. Prevents race conditions.

**Conditional clearance restriction**: Per Doc 4444 §7.9.3.3 / SERA.8015(d)(2), conditional clearances are restricted to surface movement instructions. Conditions must not be applied to ClearedForTakeoff, ClearedToLand, or other non-surface instructions. This is enforced at clearance construction time.

## What changes

### 1. Instructions reference entities, not nodes

Today, `TaxiTo` carries `route: List<NodeId>, destination: NodeId` — a raw path through the graph. The controller resolves the path before issuing the clearance, and the instruction is a bag of node IDs with no operational structure.

In the new model, instructions reference **entities** from the path network. The controller issues clearances in the language of the AIP and phraseology. The pilot resolves entity references to physical paths using the WorldIndex.

This is not a cosmetic rename. Entity references carry operational meaning that node lists don't:

- "Taxi via Alpha" means the aircraft follows Taxiway Alpha's path. If Alpha has a holding point for Runway 16C, the pilot knows to expect a hold instruction there.
- "Cross runway 34C" means the aircraft crosses at the shared point between its current taxiway and Runway 34C. The crossing point is derived from entity relationships, not encoded in the instruction.
- "Hold short runway 16C" means stop at the holding point on the current taxiway for Runway 16C. The point is a property of the taxiway entity.

### 2. Compound clearances

Real ATC issues compound clearances: "Taxi via Alpha, Bravo, cross runway 34C, hold short runway 16C." One transmission, one readback, multiple sub-instructions with implicit sequencing.

Today, each instruction is a separate clearance with its own lifecycle. This forces artificial sequencing — the controller issues TaxiTo, waits for completion, then issues CrossRunway, then HoldShortOf. The pilot manages them independently. This is both unrealistic and mechanically fragile.

The new model introduces a **clearance envelope**: a single clearance containing an ordered sequence of instructions. The envelope has one lifecycle (one readback, one Active state). The instructions within it execute in order as the aircraft progresses.

### 3. Clearance limits

Every IFR clearance has a clearance limit [Doc 4444 §4.5.7.1]. The clearance limit is mandatory — it is typically the destination aerodrome, but may be an intermediate fix for partial clearances, re-routes, and arrival sequencing.

"Cleared to BALSI, hold as published." The aircraft flies the cleared route up to the limit fix, then holds. If no further clearance is received, the pilot holds indefinitely.

**Construction-time invariant**: Every fix that may serve as a clearance limit must have an associated holding pattern in the path network. A clearance with `limit = FixId("BALSI")` and no holding pattern at BALSI is unflyable and must be rejected at construction time.

### 4. Completion is entity-aware

Today, `isInstructionComplete` switches on `AircraftPhase` — a monolithic enum that encodes what the aircraft is doing. In the new model, operational context is derived from entities. Completion conditions should be expressible in terms the entity model provides.

`AircraftPhase` can remain as a derived convenience (computed from entity context each tick), but completion rules should not require it as the primary input.

### 5. Activation sets intent, not world state

Today, `applyActivationEffects` directly modifies world state when a clearance becomes Active — setting speed bands, phases, and runway references. In the new model, activation sets **pilot intent**: target waypoint, target speed, target altitude. The pilot agent and physics translate intent into movement along entity paths.

This separates "what the pilot was told to do" from "what the world looks like as a result." The clearance model owns the former; the pilot and physics own the latter.

## Clearance structure

```
Clearance(
  id: ClearanceId,
  aircraft: AircraftId,
  content: ClearanceContent,              // what the pilot must do
  domain: ClearanceDomain,               // for supersession scoping
  issuedBy: ControllerId,
  issuedAt: TickNumber,
  status: ClearanceStatus,                // lifecycle state (unchanged)
  condition: ConditionalPredicate?,      // conditional clearance (surface movement only)
)
```

### ClearanceContent

A clearance contains either a single instruction or a compound sequence.

```
sealed interface ClearanceContent {

  data class Single(
    val instruction: AtcInstruction,
  ) : ClearanceContent

  data class Compound(
    val steps: List<AtcInstruction>,      // ordered, executed per timing rules
    val completedSteps: Set<Int> = emptySet(),  // indices of completed steps
  ) : ClearanceContent
}
```

A compound clearance is an ordered list of instructions with a shared lifecycle (one readback, one Active state). Steps complete independently based on geographic position or aircraft state — not by sequential advancement through an index.

**Sequential steps** (e.g. TaxiTo, CrossRunway) complete when the aircraft reaches the relevant geographic point. The path network's geometry naturally handles ordering — the aircraft reaches the crossing point before the hold-short point because that is the physical layout. A validation assertion enforces that sequential steps complete in order (catches physics bugs), but ordering is not used for control flow.

**Immediate steps** (e.g. SetSquawk) complete on activation. **Persistent steps** (e.g. HoldShortOf) never self-complete.

The compound completes when all non-persistent steps are in `completedSteps`. Supersession and cancellation apply to the entire envelope, scoped by domain.

### Instruction timing

Each instruction has an inherent timing category that determines when it becomes effective within a compound clearance:

```
enum class InstructionTiming {
  Sequential,       // executes when prior sequential step completes (ground movement)
  Immediate,        // in effect from clearance activation (squawk, pressure, climb, speed)
  Persistent,       // never self-completes; terminated by a subsequent clearance
}
```

| Timing | Instructions |
|--------|-------------|
| Sequential | TaxiTo, CrossRunway, BacktrackRunway |
| Immediate | SetSquawk, SetPressure, ClimbTo, DescendTo, MaintainSpeed, ReduceSpeedTo, IncreaseSpeedTo, ContactFrequency, MonitorFrequency |
| Persistent | HoldPosition, HoldShortOf, LineUpAndWait, Orbit, ExtendDownwind, HoldAt |

**Sequential** steps execute in order as the aircraft progresses. **Immediate** steps take effect the moment the clearance becomes Active. **Persistent** steps create an ongoing state — the pilot holds position, holds short, orbits — until a subsequent clearance terminates them.

Phase-dependent activation (e.g. "climb to 5000" not actioned until airborne, "contact approach" not actioned until leaving tower airspace) is a **pilot-layer concern**, not a clearance model concern. The clearance marks the instruction as Immediate; the pilot agent determines when to begin executing based on its current flight phase and the instruction's operational context.

Instructions that are typically issued as Single clearances (ClearedForTakeoff, ClearedToLand, etc.) are not assigned a compound timing because they are not compound steps.

### Clearance domains

Each clearance belongs to a domain. Supersession primarily occurs within the same domain — a new squawk does not destroy an active taxi clearance.

```
enum class ClearanceDomain {
  GROUND,           // taxi, cross, hold short, backtrack, pushback, startup
  RUNWAY,           // line up, takeoff, land, touch and go, go around
  ROUTE,            // cleared to, proceed direct, hold at, join airway
  LEVEL,            // climb, descend, maintain level
  SPEED,            // speed instructions
  SQUAWK,           // transponder
  FREQUENCY,        // contact, monitor
}
```

A compound clearance's domain is determined by its primary instruction (typically the first sequential step). If a controller issues a new squawk while a taxi clearance is active, only the SQUAWK domain is affected — the taxi clearance continues.

**Cross-domain supersession.** Some instructions supersede across multiple domains because they activate procedures with multi-domain implications:

| Instruction | Primary domain | Also supersedes in |
|---|---|---|
| GoAround | RUNWAY | LEVEL, ROUTE |
| ClearedApproach | ROUTE | LEVEL |
| HoldAt (with level) | ROUTE | LEVEL |

Instructions with cross-domain effects carry a `supersedesIn: Set<ClearanceDomain>` property. GoAround cancels the landing clearance (RUNWAY), replaces the approach path with the missed approach route (ROUTE), and replaces any assigned altitude with the missed approach climb (LEVEL).

**Compound domain resolution.** A compound clearance may contain steps whose natural domain differs from the compound's primary domain (e.g. a ROUTE-domain departure compound containing a ClimbTo step, which is naturally LEVEL-domain). When a standalone clearance arrives in a secondary domain (e.g. a new LEVEL instruction), it takes precedence for pilot behaviour in that domain, but the compound's lifecycle is untouched. The pilot mentally annotates the amendment — the compound is not superseded, but the pilot sources LEVEL intent from the newer standalone clearance.

This matches real-world practice: "maintain five thousand" overrides the altitude element of the departure clearance without destroying the route, squawk, or frequency components.

**Example — taxi clearance:**
```
Compound(steps = [
  TaxiTo(target, destination = holdingPoint16C, via = [nodeA3, nodeB1]),
  CrossRunway(target, runway = RunwayId("34C")),
  HoldShortOf(target, runway = RunwayId("16C")),
])
domain = GROUND
```

One readback. The pilot follows the TaxiTo route. When they reach the crossing point for 34C, they cross. When they reach the holding point for 16C, they hold. TaxiTo's destination is the final holding point — CrossRunway and HoldShortOf are action points along the route, not separate destinations.

**Example — IFR departure:**
```
Compound(steps = [
  ClearedTo(target, clearanceLimit = NodeId("KEMIK"), route = ViaSid("KEMIK 1A")),
  ClimbTo(target, level = AltitudeFeet(5000)),
  SetSquawk(target, squawk = Squawk(4521)),
])
domain = ROUTE
```

One readback. SetSquawk is Immediate (set on activation). ClimbTo is Immediate (pilot-layer determines when to begin climbing — on becoming airborne). ClearedTo is the primary route instruction. If the controller later says "maintain five thousand," a standalone LEVEL-domain clearance overrides the ClimbTo for pilot intent without destroying this compound.

## Instruction types

Instructions are defined in `Instruction.kt` in the `protocol` module. The clearance model references these types directly — it does not define its own instruction hierarchy.

See `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt` for the full type definitions. Key categories relevant to the clearance model:

- **Ground movement**: TaxiTo, HoldPosition, HoldShortOf, CrossRunway, BacktrackRunway, StartupApproved, PushbackApproved, VacateRunway, etc.
- **Runway operations**: LineUpAndWait, ClearedForTakeoff, ClearedToLand, ClearedTouchAndGo, GoAround, TurnBase, etc.
- **Route/IFR**: ClearedTo, ClearedApproach, HoldAt, ProceedDirect, etc.
- **Level**: ClimbTo, DescendTo, ExpediteClimb, ExpediteDescend, MaintainLevel, etc.
- **Speed**: MaintainSpeed, ReduceSpeedTo, IncreaseSpeedTo, ResumeNormalSpeed, etc.
- **Surveillance**: SetSquawk, SquawkIdent, SetPressure, etc.
- **Frequency**: ContactFrequency, MonitorFrequency
- **Approach/circuit**: ClearedApproach, JoinCircuit, ExtendDownwind, TurnBase, Orbit, etc.
- **Conditional**: ConditionalClearance wraps any instruction with a ConditionalPredicate
- **Controller responses** (not instructions): ReadBackCorrect, Standby, Identified, TrafficInformation, etc.

## Completion conditions

### Completion categories

Instructions fall into two categories for completion:

**Self-completing** — have an intrinsic completion condition based on aircraft state:

| Instruction | Completion condition |
|-------------|---------------------|
| TaxiTo | Aircraft at destination point |
| ClearedToLand | Aircraft has vacated the runway (crossed runway holding position onto a taxiway). Go-around supersedes, not completes. |
| ClearedForTakeoff | Aircraft airborne (altitude > runway elevation + threshold) |
| CrossRunway | Transition-based: aircraft entered runway entity segments AND subsequently left them |
| ClearedTo | Aircraft at clearance limit fix (or destination if no intermediate limit) |
| ClimbTo | Altitude >= target level |
| DescendTo | Altitude <= target level |
| ReduceSpeedTo / IncreaseSpeedTo | Speed at or past target |
| BacktrackRunway | Aircraft at far end of runway |
| JoinCircuit | Aircraft established on the specified circuit leg at circuit altitude |

**Persistent** — never self-complete; terminated by a subsequent clearance:

| Instruction | Terminated by |
|-------------|---------------|
| HoldPosition | Any movement instruction (TaxiTo, CrossRunway, etc.) |
| HoldShortOf | CrossRunway, LineUpAndWait, or new taxi clearance |
| LineUpAndWait | ClearedForTakeoff |
| Orbit | TurnBase, ContinueApproach, or any approach/route instruction |
| ExtendDownwind | TurnBase |
| HoldAt | LeaveHoldProceedDirect, ClearedApproach, or new route instruction |
| SetSquawk | New SetSquawk (domain supersession) |
| ContactFrequency | Completed when pilot checks in on new frequency (radio event) |

### Completion input

Completion is evaluated against a **view over WorldState** — a projection containing everything needed to evaluate any completion condition:

```
data class CompletionView(
  val position: PointId,                  // current point on the path network
  val entities: Set<EntityRef>,           // all entities the aircraft is currently on (plural — intersections)
  val altitude: Level?,
  val speed: Speed?,
  val onGround: Boolean,
  val transitionHistory: Set<EntityRef>,  // entities the aircraft has been on this tick (for transition detection)
  val radioState: RadioState,             // current frequency, last contact
  val transponderCode: Squawk?,
)

fun isInstructionComplete(
  instruction: AtcInstruction,
  view: CompletionView,
): CompletionResult                       // Complete, NotComplete, or NotApplicable (persistent)
```

`entities` is a `Set<EntityRef>` — an aircraft at a taxiway/runway intersection is on both entities simultaneously. `transitionHistory` supports transition-based completion (CrossRunway: was on runway, now isn't). The view is derived from WorldState each tick; it is not stored on the clearance.

### Compound clearance completion

A compound clearance completes when all non-persistent steps are in `completedSteps`:

```
fun isCompoundComplete(content: ClearanceContent.Compound, view: CompletionView): Boolean {
  return content.steps.withIndex()
    .filter { (_, step) -> step.timing != Persistent }
    .all { (index, _) -> index in content.completedSteps }
}
```

Each tick, the system evaluates every step in the compound against the `CompletionView`. Steps that are complete are added to `completedSteps`. Sequential steps must complete in order — this is validated by assertion, but completion is driven by geographic/state conditions, not by index advancement.

Persistent steps (HoldShortOf, HoldPosition, etc.) are excluded from compound completion. They remain in effect until externally terminated by a subsequent clearance.

## Activation effects

When a clearance becomes Active, the system sets **pilot intent** — not world state.

The pilot agent reads its active clearances directly and derives its behaviour from them. The clearance content *is* the pilot's intent — there is no separate PilotIntent data structure that lossy-projects clearance content.

The pilot resolves entity references to physical paths using the WorldIndex:

1. `TaxiTo(destination, via = [...])` → walk the path from current position through the via points to the destination
2. `CrossRunway(34C)` → find shared point between current taxiway and Runway 34C, cross through it
3. `HoldShortOf(16C)` → find holding point on current taxiway for Runway 16C, stop there
4. `ClearedTo(limit, route = ViaSid("KEMIK 1A"))` → resolve SID entity to waypoint sequence, follow it

Physics moves the aircraft along entity paths. The world state reflects physical reality (position, speed, altitude). Clearances reflect operational intent.

**Runway references** (landingRef, takeoffRef, crossingRef) are derived from "aircraft is on a runway entity with an active landing/takeoff/crossing clearance," not set by activation effects.

### Compound step execution

The pilot reads all steps in a compound and executes them as encountered geographically. There is no step-advancement mechanism — the pilot follows the route defined by the primary instruction (e.g. TaxiTo) and performs action-point steps (CrossRunway, HoldShortOf) when reaching the relevant geographic point.

This matches how real pilots process compound taxi clearances: "I am taxiing to holding point 16C. Along the way, I cross 34C and hold short of 16C." The pilot does not think in terms of "step 1, step 2" — they follow the route and act at waypoints.

For non-taxi compounds (IFR departures), the execution semantics differ by timing category: Immediate steps activate on clearance activation, and the pilot determines when to begin executing based on flight phase (pilot-layer concern). The compound structure is a shared lifecycle envelope, not a sequential execution plan.

## Supersession and conflict

Supersession is scoped by **clearance domain**. A new clearance in domain X supersedes any existing active clearance in domain X for the same aircraft. Clearances in different domains coexist independently.

Within a domain, the existing supersession rules apply: GoAround supersedes ClearedToLand (both RUNWAY domain). A new TaxiTo supersedes an existing TaxiTo (both GROUND domain). A new SetSquawk supersedes an existing SetSquawk (both SQUAWK domain).

**Cross-domain supersession** applies to instructions with the `supersedesIn` property. GoAround supersedes in RUNWAY, LEVEL, and ROUTE simultaneously — cancelling the landing clearance, any assigned altitude, and the approach routing.

**Compound domain resolution** ensures that a standalone clearance in a secondary domain overrides the corresponding aspect of an active compound without destroying the compound. The pilot sources intent from the newest clearance in each domain.

The conflict rules (`conflictsWithExisting`) also apply within domains — preventing incompatible concurrent clearances in the same domain.

## Interaction with the path network

### Controller issues clearances using entities

The controller's decision process composes clearances from entity references:

1. Aircraft at stand on Apron → determine taxi route using taxiway/apron path entities
2. Route is expressed as via points through the path network to a holding point
3. If route crosses a runway, insert CrossRunway step
4. Terminal step is HoldShortOf at the holding point
5. Package as a Compound clearance (domain = GROUND), transmit via radio

The controller never constructs node lists. It works with the entities that its role has authority over.

### Pilot resolves entities to physical paths

The pilot receives a clearance with entity references. To execute:

1. `TaxiTo(destination, via = [nodeA3, nodeB1])` → follow the path through the via points to the destination
2. `CrossRunway(34C)` → find shared point between current taxiway and Runway 34C, cross through it
3. `HoldShortOf(16C)` → find holding point on current taxiway for Runway 16C, stop there

The pilot uses the WorldIndex to resolve entity references to physical points and segments. The pilot's physical movement is along segments; the clearance's operational meaning is in entities.

### Phase transitions are entity-derived

"Aircraft entered a segment belonging to Runway 16C with an active ClearedToLand(16C)" → aircraft is landing on 16C. The combination of entity context and active clearance determines the operational phase. No need for the tick loop to set phases imperatively.

## Clearance lifecycle in the tick loop

The tick loop phases remain structurally the same. Changes are in how clearance content is interpreted:

```
1. Process external events
2. Reconcile:
   a. Complete fulfilled clearances (entity-based completion checks)
   b. Advance compound clearance currentStep where sequential step completed
   c. Prune terminal clearances
   d. Update runway state (derived from aircraft on runway entity segments + active clearances)
   e. Evaluate conditions (unchanged)
3. Build PilotView → pilot reads active clearances, derives behaviour → transmissions
4. Build ControllerView → controller decides (composes entity-based clearances) → transmissions
5. processAllRadio: creates/advances clearances (unchanged lifecycle logic)
6. Physics: move aircraft along entity paths toward clearance targets
7. Auto-readback fallback (unchanged)
```

The lifecycle logic (state machine, events, transitions) is unchanged. What changes is the content flowing through it and how completion is evaluated.

## Gaps and future formalisation

### Lean formalisation targets

The existing TLA+ specs (`ClearanceLifecycle.tla`, `ClearanceActivation.tla`) verify the state machine and activation boundary. The new model introduces properties that need formal verification:

1. **Compound clearance monotonicity**: `completedSteps` only grows, never shrinks. Sequential steps complete in order (assertion).

2. **Clearance-entity consistency**: If a clearance references entity E, E must exist in AviationWorld for the clearance's entire lifecycle.

3. **Authority scope**: A controller can only issue instructions over entities within their role's authority. Ground cannot issue ClearedForTakeoff; Tower cannot issue TaxiTo.

4. **Clearance limit termination**: An aircraft with `ClearedTo(limit = F)` must either receive a further clearance before reaching F, or enter a hold at F. No state where the aircraft passes the limit without clearance.

5. **Compound envelope atomicity**: Supersession or cancellation of a compound clearance affects all steps within the same domain. No partial supersession within a compound.

6. **Domain isolation**: Supersession in domain X does not affect clearances in domain Y.

7. **Conditional restriction**: Conditional predicates may only be attached to surface movement instructions (GROUND domain).

### Gaps not yet addressed

- **Amendment**: Real ATC amends clearances ("Amend taxi clearance — after Bravo, continue via Charlie"). The current model has no amendment concept; the controller must supersede the entire clearance and re-issue. Domain-scoped supersession reduces the blast radius but does not solve intra-compound amendment. This is acceptable for now but lossy for compound clearances where only one step changes.

- **Void time**: Doc 4444 §4.5.7.5 requires void times for IFR clearances at uncontrolled aerodromes — a clearance that self-cancels if the aircraft is not airborne by a deadline. Not modelled. The workaround is controller-initiated cancellation.

- **Conditional compound steps**: "Cross runway 34C when the departing 737 is airborne." Today, conditions apply to the clearance envelope. Applying a condition to a specific step within a compound clearance is not modelled. The workaround is to split the clearance.

- **Pilot-initiated requests**: "Request startup", "Ready for departure." These are not clearances but they interact with the clearance model — a request may trigger the controller to issue a clearance. The request model is defined in `PilotTransmission.kt` and is separate from the clearance model.

- **Frequency changes imply transfer of control**: When a pilot completes a ContactFrequency instruction, the controlling authority changes. The simulation must track which controller currently has authority over each aircraft. This is modelled as a handoff: when ContactFrequency completes (pilot checks in on new frequency), authority transfers to the controller filling the target role. The handoff is automatic ("software coordination") — controllers do not explicitly call each other. The AIP's handoff sequence defines the expected progression (Ground → Tower → Approach etc.).

- **CPDLC / datalink**: Non-radio clearance delivery. The lifecycle is the same (issue, acknowledge, active) but the delivery mechanism differs. Deferred.

- **Speed and altitude as constraints vs. targets**: `ClimbTo(5000)` is a target. "Maintain 5000" is a constraint. "Not above 3000 until BALSI" is a waypoint-scoped constraint. The current model treats all altitude/speed instructions as targets. Waypoint-scoped constraints would need to reference the path network's waypoint altitude constraints.
