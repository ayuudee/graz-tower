# 2026-04-15: Controller Architecture

## Context

Evolving the controller from TWR1 (working BDI prototype with ~45 files, 1100 tests) into TWR2's richer world. TWR2 has: 100+ instruction types, domain-scoped clearance engine, entity-referenced world model, Lean certifiers (4 kernels + scheduling overlay), and a regulation database. The controller must serve a training product where regulatory grounding is visible to students.

This document captures the consolidated design after review by ATC law and operational knowledge agents.

## Decision Summary

Build a regulation-grounded, obligation-driven controller as a deep FP module. Use a hybrid event/tick simulation model. Design wide (all roles, full instruction set) but build core-out (tower/ground first, solid before expanding).

---

## 1. Regulation as First-Class Organising Principle

### Three kinds of controller knowledge

**A. Obligations** -- things the controller MUST or SHOULD do, grounded in regulations.

Every controller action traces to one or more regulatory obligations. An obligation has two orthogonal axes:

- **Strength**: MANDATORY (violation is always wrong), RECOMMENDED (departure requires justification), DISCRETIONARY (technique choice)
- **Source**: SINGLE_REGULATION (citable section), MULTI_REGULATION_SYNTHESIS (interpretation across sources), OPERATIONAL_CONVENTION (established practice without specific regulation), LOCAL_PROCEDURE (aerodrome-specific, published in AIP)

LOCAL_PROCEDURE is a distinct authority source -- "runway 27 left-hand circuit" is mandatory at that aerodrome but is not ICAO/SERA. The world model encodes these (circuit procedures, taxi routes, SIDs, STARs); the controller must acknowledge them as a regulatory source.

**B. Practices** -- specific techniques that fulfil obligations. Multiple practices may satisfy the same obligation. "Extend downwind" and "orbit" both fulfil the separation obligation; urgency determines which to use.

**C. Mechanics** -- implementation details (route finding, frequency resolution, belief tracking). No regulatory citation.

### Obligation as the root, procedure as composition

Instead of "TOWER_DEPARTURE has stages with rules", the primary structure is: "These regulations create these obligations, which apply in these situations." Procedures are pre-computed plans for fulfilling standard combinations of obligations:

```
Obligation: "Maintain prescribed separation" (ICAO 4444 s5, SERA.8005)
  Practice A: ExtendDownwind (least disruptive, Urgency.PROGRESSION)
  Practice B: Orbit (moderate, Urgency.TIME_SENSITIVE)
  Practice C: GoAround (last resort, Urgency.SAFETY)

Obligation: "Do not clear an aircraft to land onto an obstructed runway" (ICAO 4444 §7.10, §7.4.1.4.1; CAP 413 §4.55-4.56)
  Practice D: ContinueApproach (pre-clearance, obstruction-clears-in-time, Urgency.TIME_SENSITIVE)
  Practice E: GoAround-obstruction (predicate fails or post-clearance, Urgency.SAFETY)
```

The runway-obstruction case at `AwaitApproach` has three guard predicates
forming a mutually-exclusive ladder: `RunwayPhysicallyClear` (occupancy
— generic `ARR-GO-AROUND` on `Not(RunwayPhysicallyClear)`),
`RunwayObstructed` (declared obstruction in beliefs), and
`ObstructionClearsInTime` (kinematic predicate: `(clearsAt - now) +
safetyMargin ≤ ETA-to-threshold`). Priority placement at `AwaitApproach`
is `ARR-CONTINUE-APPROACH-OBSTRUCTION` (Practice D, fn-13) before
`ARR-GO-AROUND-RUNWAY-OBSTRUCTED` (Practice E, fn-12 — narrowed at this
stage with `Not(ObstructionClearsInTime)`) before the generic
`ARR-GO-AROUND`. Mutual exclusion is enforced by guard disjointness
(`ObstructionClearsInTime` vs `Not(ObstructionClearsInTime)`); priority
is defence-in-depth. Post-clearance (`LandingClearanceIssued`,
`AwaitLandedObserved`), Boundary #1 of the fn-13 epic flips the doctrine:
the post-clearance variant of `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` always
escalates to GA (CAP 413 §4.53 cancel-clearance path is a future
deferment).

A ProcedureRule fulfills one or more obligations. The procedure is the recipe; the obligations are the ingredients. This makes the controller's behaviour auditable ("why did it do X?") and composable (obligations interact naturally when multiple aircraft create overlapping demands).

### Regulatory sources

The regulation database must cover (expanding from TWR1's 25 entries):

| Source | Coverage | Role |
|--------|----------|------|
| SERA (EU 923/2012) | Rules of the air, VMC minima, flight rules | Binding law (EASA states) |
| ICAO Annex 2 | Rules of the air (global baseline) | International standard |
| ICAO Annex 11 | ATS responsibilities, service types, role definitions | Defines what ATC *is* |
| ICAO Doc 4444 | Procedures -- separation, clearances, handoff, runway ops | Primary procedural authority |
| ICAO Doc 9432 | Radiotelephony phraseology | Phraseology authority |
| ICAO Doc 8168 (PANS-OPS) | Procedure design -- approaches, SIDs, holdings | Controller must know procedure parameters |
| ICAO Doc 9870 | Runway incursion prevention | Supplementary guidance |
| CAP 413 | UK phraseology supplement | Guidance/national |
| National AIP | Aerodrome-specific procedures, circuits, noise abatement | Local authority |

Citation discipline: use procedure documents (4444) as primary authority, phraseology documents (9432) as secondary, guidance (9870) as supplementary. Several TWR1 citations need tightening (see appendix).

---

## 2. Controller Architecture

### Module structure

**protocol** (existing, extend):
- Instruction types (existing, 100+)
- ClearanceDomain, ClearanceStatus, authority model (existing)
- `RegulationRef`, `ObligationId`, `ObligationStrength`, `ObligationSource` (new)
- Controller boundary types: `ControllerView`, `ControllerDecision` (new)
- `ActionValidator`, `ActionCertifier`, `ActionScheduler` interfaces (new, pluggable)

**core** (existing, extend):
- Clearance engine (existing, solid)
- World model + resolution (existing)
- Obligation derivation: `(world state, role, situation) -> applicable obligations` (new)
- Certifier view extraction: `AviationWorld -> CertifierViews` (new, when ready)
- Runtime validation implementations (new, for runtime concerns from Lean work)

**controller** (new, deep module):
- BDI: duties, commitments, procedure specs, guard predicates, action resolution
- Pipeline: compose BDI + validator + certifier + scheduler
- Role procedures: tower, ground, approach, area, AFIS
- Reactive safety layer (obligation-driven, with intervention hierarchy)
- Decision trace (what was considered, what was committed, why)
- Single entry point: `controllerDecide(view: ControllerView): ControllerDecision`

Anti-corruption boundary: the controller receives types defined by its own module boundary, translated at the edge. Not a "view projection of world state" but full hexagonal isolation.

### Procedure model: compositional, not enumerated

Instead of a flat list of 6 procedure types (TWR1), model procedures as **(role x traffic type x direction)** combinations:

```
(GROUND, IFR, DEPARTURE) -> startup, clearance delivery, pushback, taxi
(GROUND, VFR, DEPARTURE) -> taxi
(TOWER, IFR, DEPARTURE) -> line up, takeoff, initial climb, handoff
(TOWER, VFR, DEPARTURE) -> line up, takeoff, circuit departure / VFR route
(TOWER, VFR, ARRIVAL) -> circuit join, sequencing, landing, vacate
(TOWER, IFR, ARRIVAL) -> approach sequencing, landing, vacate
(APPROACH, IFR, ARRIVAL) -> vectors/directs, approach clearance, handoff
(APPROACH, IFR, HOLDING) -> stack management, descend-through, leave hold
(APPROACH, VFR, ARRIVAL) -> circuit join instruction, handoff
(APPROACH, *, TRANSIT) -> transit service, release
(AREA, *, TRANSIT) -> handoff to approach
```

Missing from TWR1 that must be designed for:
- STARTUP / CLEARANCE_DELIVERY (distinct from taxi at larger fields)
- PUSHBACK (coordinated procedure with facing direction)
- Standalone RUNWAY_CROSSING (ground-tower coordination)
- HOLDING / STACK_MANAGEMENT (distinct from arrival sequencing)
- MISSED_APPROACH (distinct from visual go-around -- follows published procedure)
- SVFR_TRANSIT (route + level restriction through CTR)
- VFR vs IFR variants of arrival/departure

The type structure covers all of these from day one. Procedure specs are populated core-out: tower departure + arrival first, then ground, then approach.

### Stage model: progress tracker, not decision maker

Stages track per-aircraft procedure progress. Decisions operate on the **global picture** (all aircraft, runway state, separation requirements).

The real controller mental model is runway-centric and multi-aircraft: "runway occupied by departure, one lined up, one on final -- when departure airborne and separation exists, clear lined-up for takeoff, then decide about arrival." Stages are valid progress markers but transitions are gated by global state.

Arrival stages need more granularity than TWR1 -- "10 miles out" vs "4 miles final" produce very different decision contexts.

### Reactive safety: obligation-driven intervention hierarchy

Ground the reactive layer in regulation (TWR1 had no citations here):

- **Separation obligation**: ICAO 4444 s5, SERA.8005(c) -- "controller shall ensure prescribed separation between controlled flights"
- **Go-around obligation**: ICAO 4444 s7.10.2 -- "if controller considers aircraft cannot safely complete approach, instructions to go around shall be given"

Intervention hierarchy (least to most disruptive):
1. Speed control (Urgency.PROGRESSION)
2. Path extension -- extend downwind, make long approach (Urgency.PROGRESSION)
3. Orbit / hold (Urgency.TIME_SENSITIVE)
4. Go-around (Urgency.SAFETY)

The controller selects the **least-disruptive intervention** that resolves the conflict. Two aircraft on final simultaneously is a planning failure, not a reactive situation -- the proactive sequencing layer should prevent it. The reactive layer is the last line.

### AFIS: structurally distinct

AFIS must be structurally excluded from issuing `AtcInstruction`. It emits only `ControllerResponse` types (traffic information, weather, aerodrome information). If the BDI framework forces AFIS into the same procedure structure as tower, the architecture is too rigid. Enforce at the type level.

### Runway management: not a queue

Replace the TWR1 "runway duty queue" with a dynamically interleaved priority model:
- Runway state is a first-class concept (CLEAR, OCCUPIED_DEPARTURE, OCCUPIED_LANDING, OCCUPIED_CROSSING)
- Sequence is a priority-ordered list of commitments, reorderable based on: time to threshold, departure performance, Land After viability, wake turbulence categories
- The entity-referenced world model supports this: `Runway.path`, `threshold`, `exits`, `declaredDistances`, and `obstruction` (fn-12: optional `RunwayObstruction(clearsAt: SimTime)` declaration consumed by the controller's reactive `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule and the pre-clearance `Not(RunwayObstructed)` landing gate) give the information needed

### Workload model replaces one-per-tick

Replace "at most one progression action per tick" with cognitive workload:
- Each action has a cognitive cost (novel taxi route: expensive; pre-planned takeoff clearance: cheap)
- Controller has a workload budget per decision cycle
- Safety actions bypass workload
- Pre-planned actions (already-sequenced departure) have low cost
- Novel decisions (re-routing, re-sequencing) have high cost

This lets the controller issue multiple simple instructions in one cycle while preventing multiple complex decisions simultaneously.

### Pipeline with pluggable gates

```
duties -> commitments -> procedure execution -> proposed actions
    -> [validate?] -> [certify?] -> [schedule?] -> committed actions
```

Each gate is a pluggable function:
- **Validate**: runtime checks for things certifiers don't yet cover (modular, opt-in)
- **Certify**: Lean-backed certification via the 4 kernels (when extraction boundary stabilises)
- **Schedule**: sequencing/throughput optimisation (initially simple urgency-based, later analytical)

Without certifiers: BDI + default sequencing. With certifiers: BDI proposes, certifiers gate. The BDI owns protocol compliance; the certifiers own safety; the scheduler owns throughput.

---

## 3. Simulation Timing: Hybrid Event Model

### Problem with fixed-tick

TWR1's fixed-tick model caused concrete problems:
- **One tick late**: events scheduled for tick N processed at tick N+1 (tick incremented before event processing)
- **Intra-tick readback**: originally clearances activated same tick as issuance (fixed by deferring to next tick, but this was a patch)
- **Departure handoff too late**: fast aircraft left jurisdiction before controller's next tick
- **Quantisation**: realistic radio timing (3-8 second transmissions, 1-2 second processing delays) doesn't map cleanly to fixed ticks. Too coarse = collapsed causal chains. Too fine = wasted computation.

Professional ATC simulators (NARSIM, ESCAPE, ACES) use event-driven communication within real-time or fixed-step physics frameworks.

### Proposed: hybrid physics-tick + event-driven communications

**Physics layer**: fixed-step integration at a chosen dt (e.g., 1 second). Updates aircraft positions, evaluates completion conditions against CompletionView. This is necessary because flight dynamics are continuous.

**Communication/decision layer**: discrete event simulation (DES). Radio transmissions, clearance lifecycle, pilot decisions, controller actions are events with precise timestamps. Each event handler returns new state + newly scheduled events.

**The bridge**: each physics step checks if condition-pending events should fire. The communication layer reads aircraft state snapshots from the physics layer.

### Core DES pattern (FP-aligned)

```kotlin
// Pure fold -- the entire simulation is this shape
fun step(state: SimState, event: SimEvent): Pair<SimState, List<SimEvent>>
```

Event queue is a priority queue with deterministic total ordering:
1. Primary: event timestamp (SimTime)
2. Secondary: source agent ID
3. Tertiary: event sequence number (monotonic counter)

Physics integration runs as periodic self-scheduling events: a "physics tick" event processes movement and enqueues the next physics tick at `currentTime + dt`.

### Clearance lifecycle as event chain

```
T=0.000  TransmissionStart(from=TWR, instruction=ClearedForTakeoff)
T=3.200  TransmissionEnd(from=TWR)           -- 3.2s based on word count
T=4.500  PilotProcessingComplete(G-ABCD)     -- 1.3s cognitive delay
T=4.500  ReadbackStart(from=G-ABCD)
T=7.100  ReadbackEnd(from=G-ABCD)            -- 2.6s readback
T=8.200  ControllerConfirmation(ReadBackCorrect)
T=8.200  ClearanceActivated(CLR-001, ACTIVE)
```

Frequency is a shared resource: if another pilot transmits while busy, collision/stepping occurs naturally. No special flags needed (TWR1's `radioChannelResolution` flag was a workaround for the tick model's inability to represent sub-tick timing).

### Testing determinism

The FoundationDB / Antithesis approach: single-threaded event loop with seeded PRNG. All non-determinism (pilot reaction jitter, cognitive delay) uses a seeded Random. Given the same seed, identical results.

Replace `TickNumber(value: Long)` with `SimTime` (continuous). Tests specify exact event sequences with precise timestamps. No `instantKinematics` flag needed -- tests can use realistic physics with deterministic timing.

The pure-fold `(State, Event) -> (State, List<Event>)` is trivially testable: supply an event, assert state and emitted events. System tests compose event sequences. No mutable state, no timing hacks.

### Migration path

This is a significant architectural change but aligns with the project's FP principles (pure state machine in IO). The existing clearance engine (`admitClearance`, `reconcileClearances`) is already pure functions over state -- they become event handlers. `CompletionView` is already the physics-layer snapshot the clearance engine queries.

The migration would be:
1. Introduce `SimTime` value class alongside existing `TickNumber`
2. Define `SimEvent` sealed hierarchy
3. Build the event loop as a pure fold
4. Physics integration as self-scheduling events
5. Port clearance lifecycle to event handlers
6. Retire `TickNumber`, `instantKinematics`, `radioChannelResolution` flags

This need not block the controller work. The controller interface (`ControllerView -> ControllerDecision`) is timing-model-agnostic -- the orchestrator calls it regardless of whether "time to call the controller" comes from a tick or an event.

---

## 4. What To Take From TWR1

### Port wholesale
- ProcedureSpec -> stages -> rules -> guards/actions shape (adapt guards to entity-aware)
- ProcedureInterrupt concept (go-around resets arrival)
- StageExpectation / PilotExpectation (training value: what the controller expects and why)
- Commitment reconciliation logic (form/preserve/prune)
- Urgency classification (SAFETY > TIME_SENSITIVE > PROGRESSION > INFORMATIONAL)
- Decision trace structure
- Regulation database structure (evolve from 25 to full scope)

### Evolve
- Guards: phase-based -> entity-based (position + EntityRef set)
- Actions: direct instruction construction -> via resolution layer, returning Either
- Regulations: metadata on rules -> organising principle (obligations as root)
- Duties: phase-based switch -> obligation-derived
- Reactive layer: ad-hoc conflict -> obligation-driven with intervention hierarchy
- Runway duty: FIFO queue -> dynamic priority interleaving
- One-per-tick: replace with workload model
- Tick model: fixed-tick -> hybrid DES

### Do not port
- Oracle (50-tick forward simulation) -- replace with analytical layer for common patterns (time-to-threshold, required separation, runway occupancy time) plus forward simulation only for complex multi-aircraft interactions. The analytical layer is more explainable to students.
- Segment-based conflict detection -- entity-referenced world provides richer spatial reasoning
- `instantKinematics` / `radioChannelResolution` flags -- DES model eliminates the need

---

## 5. Build Order

Design wide, build core-out:

**Phase 1: Foundation** (current focus)
- Controller boundary types in protocol (ControllerView, ControllerDecision)
- Obligation / RegulationRef types in protocol
- Regulation database (evolve from TWR1, full source coverage)
- Duty derivation (entity-aware)
- BDI framework: ProcedureSpec, guards (entity-aware), actions (via resolution), commitments
- Tower departure + arrival procedures (most exercised, constrained by N0 scope)

**Phase 2: Ground and sequencing**
- Ground departure + arrival procedures (startup, taxi, pushback)
- Runway management (priority-based, not queue)
- Workload model
- Default scheduling (urgency-based)

**Phase 3: Approach and multi-controller**
- Approach arrival + transit procedures
- Holding / stack management
- Multi-controller handoff orchestration
- Area transit

**Phase 4: Formal integration**
- Certifier view extraction
- ActionCertifier implementation bridging to Lean kernels
- Runtime validation for uncovered concerns
- Analytical scheduling layer

**Deferred until needed:**
- SVFR procedures
- Missed approach (IFR, distinct from visual go-around)
- AFIS procedure framework
- Weather transition handling
- Circling approach procedures
- Oracle / forward simulation (may be replaced by analytical layer + certifiers)

---

## Appendix: TWR1 Citation Corrections

Per ATC law review:

| Rule | TWR1 Citation | Correction |
|------|--------------|------------|
| DEP-RUNWAY-INCURSION | ICAO 9870 | Primary: ICAO 4444 s7.6. Doc 9870 is guidance, not procedure |
| DEP-LUAW-COND | ICAO 9432 Ch.4 only | Add ICAO 4444 s7.9.3 (conditional clearances procedure) |
| DEP-HOLD-IMC | SERA.5001 | Tighten to SERA.5001 + SERA.5005 (VFR limitations) |
| ARR-EXTEND | ICAO 9432 + CAP 413 s4.49 | Add ICAO 4444 s5 (separation) as primary procedural basis |
| Reactive go-around | (none) | ICAO 4444 s7.10.2 (mandatory go-around instruction) |
| Reactive spacing | (none) | ICAO 4444 s5 + SERA.8005(c) (separation obligation) |

## Note (2026-05-11 — fn-14 G3a-react)

The reactive go-around surface is now **quadruple-covered** at the
pilot-side (self-initiated DA-without-clearance, pilot-trained mission,
ATC-instructed-obstruction, pilot-reactive crosswind off world weather).
**No controller behaviour change** for fn-14: the existing
`GA-PRE-CLEAR` / `GA-POST-CLEAR` interrupts (see *§2 Controller
Architecture* above) are trigger-agnostic — they fire on
`ControllerEvent.GoAroundDetected` which derives from any pilot-emitted
`Report(GoingAround)` regardless of the source path (mission-authored,
ATC-instruction-driven, or pilot's autonomous crosswind reading). The
only controller-side compile-impact was the relocation of `WindReport`
from `:controller` (`ControllerTypes.kt`) to `:protocol` so `:pilot`
can consume the wind projection through the firewall without depending
on `:controller`; `WeatherObservation` stays in `:controller`. Existing
`:controller` consumers re-import from `:protocol`. Per
`feedback_firewall_principle.md`: the pilot's new
`PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport>` widening
is a deliberate firewall change — real pilots read wind via windsock +
ASI crosscheck + instrument + ATIS (multiple channels); the world-
weather projection models the visual/instrument sensing path. The
`FirewallPilotInputTest` allowlist scan was extended as the gate.

## What this supersedes

The TWR1 controller architecture (ObligationController.kt, BDI procedures, reactive layer). The concepts are preserved and evolved; the implementation will be new.
