# Formal Methods Brief v4

## Proof-First Design of a Certified ATC Control System for a Simulator

## 1. Mandate

Develop a formal model of an ATC control system for a simulator and establish its correctness before implementation. Only after the formal semantics, invariants, and proof obligations are stable should the system be refined into executable functions and data structures in code, most likely in Rust or Kotlin.

The proof target is not “the full ATC agent is always strategically correct.” The proof target is smaller and stronger:

* every issued safety-critical command must pass through an explicit certification path,
* certification must preserve explicit invariant families under explicit assumptions,
* assumption failures and emergencies must be first-class,
* and implementation must refine the formal model rather than reintroduce informal reasoning.

The central claim is:

> Any command actually issued by the simulator must have passed through the relevant certification path, and under the active assumptions that certification preserves the applicable safety invariants. When those assumptions cease to hold, the system must detect this, withdraw the affected guarantees, and fall back to the strongest justified degraded or emergency regime.

---

## 2. Design Envelope

This system is for **synthetic simulator airports**, not unconstrained real-world ATC.

The intended design envelope is:

* one or two runways maximum,
* small surface graphs, roughly 10–20 taxiway segments,
* small airborne path graphs, roughly 10–30 path segments with altitude bands,
* low traffic density, roughly 5–10 simultaneous controlled entities,
* simple procedures: standard patterns, a small number of arrivals and departures, limited merges, limited holdings.

The formal abstractions remain parameterized by environment, but the proof techniques are chosen for this envelope. They are not assumed to scale unchanged to large or highly irregular airports.

---

## 3. Operational Model

### 3.1 Surface model

The airport surface is a directed graph of:

* taxiway segments,
* runway entry and exit segments,
* holding points,
* protected runway-adjacent zones,
* intersections and crossings.

Surface control is graph movement plus resource reservation.

### 3.2 Airspace model

The airspace is **not free-flight 3D geometry**.

It is a directed graph of fixed airborne path segments, together with altitude bands and altitude transitions.

Each airborne entity is modeled by:

* current path edge,
* longitudinal position along that edge,
* speed interval,
* altitude state,
* current phase,
* possible next branch or merge choices.

Altitude state is explicit:

* `AtBand(b)`
* `Transitioning(b_from, b_to, z_interval, t_remaining)`

During a transition, the entity occupies the full swept altitude interval, not merely the source or target band.

### 3.3 Consequence for separation

Airborne separation is defined over:

* longitudinal spacing on shared edges,
* merge and junction reservation,
* altitude-band or swept-interval overlap,
* phase-specific conflict rules.

This makes the airborne certifier structurally similar to the taxiway certifier: both are graph-occupancy and reservation problems with bounded motion.

---

## 4. Phase 0: Foundational Commitments

These commitments are fixed before proof development begins.

### 4.1 Proof-authoritative semantics

There must be one proof-authoritative semantics.

Recommended default:

* **Lean 4** is the authoritative semantics and proof environment.

Other tools may be used for exploration or model checking, but nothing counts as established unless stated and proved in the authoritative theorem-prover model.

### 4.2 Time model

The system uses a discrete global decision clock with:

* **tick size `Δt = 1 second`**

All certification decisions occur on tick boundaries.

### 4.3 Within-tick motion and edge-transition rule

Between ticks, motion is conservatively over-approximated.

For an entity on edge `e` at longitudinal position `s` with speed interval `[v_min, v_max]`, the within-tick reachable set includes:

* an interval along `e` derived from worst-case motion over `Δt`,
* and, if permitted, a prefix of a successor edge.

This is not left implicit. The environment must define, for every edge-to-edge transition `e -> e'`, a **guard point** `g(e,e')` before the end of `e`.

Rule:

* if the upper bound of the entity’s within-tick reachable interval can cross `g(e,e')` during the next tick, then the entity must already hold the required reservation for the junction and the chosen successor edge `e'` at the current tick;
* otherwise the act is uncertifiable.

So the reachable set may span:

* a suffix of the current edge,
* the junction token,
* and a prefix of the reserved successor edge.

Conflict checking must therefore consider occupancy on both the current edge and the successor edge whenever the reachable interval can cross the guard point.

This is the airborne analogue of holding a hold-short clearance before runway entry.

### 4.4 Certification horizon and viability

Airborne certification is rolling-horizon with:

* **`H_sep = 120 seconds`**

The load-bearing property is not merely safety over the next 120 seconds. It is:

* safety over the next `H_sep`,
* plus a formal **viability predicate** at the horizon boundary.

Define:

`Viable_sep(E, W_H)`

to mean:

> from end-of-horizon state `W_H`, there exists at least one certifiable continuation command from an approved continuation set.

The continuation set must include command classes such as:

* continue current path under current reservation,
* hold on current path where legal,
* speed reduction where legal,
* branch choice where already reserved and legal,
* go-around or recovery-path activation where applicable.

Then airborne certification of command `c` from state `W` must prove:

* all reachable states over `H_sep` preserve the separation invariants,
* and every reachable end-of-horizon state `W_H` satisfies `Viable_sep(E, W_H)`.

This prevents commands that are safe for 120 seconds but leave no certifiable continuation at second 121.

### 4.5 Uncertainty model

`W` is a **belief state**, not a point estimate.

It contains:

* nominal observed state,
* bounded uncertainty intervals,
* bounded observation latency,
* bounded timestamp skew,
* conformance metadata.

Its semantics is a set of concrete states `γ(W)`.

All certification judgements quantify over `γ(W)`. A command is certifiable only if the relevant invariants hold for all concrete states consistent with the belief state.

### 4.6 Observation model

Phase 1 uses a **single authoritative world state**.

All agents and certifiers consume the same authoritative `W` at each tick. The baseline proof model does not allow divergent internal state estimates for certification.

The observation model must provide:

* bounded state error,
* bounded latency,
* update rules for exogenous events,
* a consistent fusion rule.

### 4.7 Composition and global compatibility

Certificates do **not** compose by default.

The rule for a new active clearance is:

1. local certification succeeds in its own domain,
2. global compatibility succeeds against the current active set.

No new active clearance enters the world unless both pass.

### 4.8 Compatibility decomposition and implementation constraint

The global compatibility predicate must be structured to admit efficient incremental evaluation.

Each active clearance has a **footprint**, consisting of the resources and state components it can affect, such as:

* runway resources,
* taxi segments,
* airborne path edges,
* merge or junction tokens,
* altitude bands or intervals,
* ownership relations,
* dependency predecessors.

The proof structure must show:

> if an existing active clearance has disjoint footprint from a candidate clearance, and no dependency or ownership interaction exists, then it is irrelevant to the compatibility outcome.

So `Compat(new, ActiveSet)` must decompose into checks over:

* intersecting footprints,
* direct dependency relations,
* ownership and phase consistency.

This is a semantic requirement, not merely a performance wish. It ensures that runtime evaluation can be incremental rather than a full recomputation over all active items.

### 4.9 Simulator assumption set `N₀`

Nominal theorems are relative to an explicit simulator assumption set:

* ordered and correct radio delivery unless explicitly modeled otherwise,
* nominal pilot compliance unless scenario-injected nonconformance occurs,
* known bounded aircraft performance envelopes,
* known bounded sensor error and latency,
* no malicious actors,
* weather within the modeled envelope unless disturbance mode is entered,
* well-formed environment data.

---

## 5. BDI Agent Boundary and Client Contract

Each operational domain is driven by an autonomous agent, BDI-like or similar, responsible for:

* intent formation,
* goal prioritization,
* sequencing strategy,
* proposal generation,
* replanning after rejection or disturbance.

The formal certification layer does **not** verify agent reasoning. The agent is a client of the certification API.

The formal layer’s obligation is only:

> Given the authoritative world state and a proposed semantic command, determine whether that command is certifiable under the active invariants and mode.

### 5.1 Minimal agent contract

The agent must satisfy the following interface contract:

* proposals must be well-typed and carry required metadata,
* the agent must consume the authoritative `W` at each tick and must not certify against stale cached state,
* the agent must respect rejection and must not resubmit the identical proposal against unchanged world state without changed circumstances or explicit backoff logic,
* the agent must respect mode changes and only propose command classes permitted in the current mode.

These are interface obligations, enforced by runtime validation if not formally proved.

---

## 6. Architectural Decomposition

There are three operational agent domains plus one certification overlay.

### 6.1 Runway agent + runway-ground kernel

The runway agent proposes runway-related acts.

The runway-ground kernel is authoritative for:

* hold short,
* line up and wait,
* runway entry,
* runway crossing,
* runway reservation and protection,
* takeoff occupancy compatibility,
* landing occupancy compatibility.

### 6.2 Taxiway agent + taxiway certifier

The taxiway agent proposes surface movements outside protected runway usage.

The taxiway certifier handles:

* taxi segment authority,
* intersection compatibility,
* hold-point logic,
* protected-surface exclusions,
* requests to the runway kernel where runway interaction is involved.

### 6.3 Air agent + separation certifier

The air agent proposes airborne operational acts:

* pattern movement,
* sequencing,
* route following,
* departure progression,
* approach progression,
* speed or altitude changes,
* go-around and resequencing.

The separation certifier is an overlay that certifies separation-relevant airborne acts over the path graph.

### 6.4 Phraseology layer

Typed semantic commands are transformed into utterances. Phraseology has no power to change operational meaning.

---

## 7. Full Command API and Incremental Delivery Strategy

The full typed command API must be defined upfront, before proofs are complete. This stabilizes integration while assurance improves over time.

### 7.1 Assurance tiers

**Tier 1 — Fully verified**
Mechanized proof in Lean.

**Tier 2 — Conservatively checked**
Same API, but justified by runtime assertions, strong invariant checks, property-based testing, and conservative rejection.

**Tier 3 — Unchecked but constrained**
No mechanized certification yet, but architectural non-bypass still prevents direct issuance.

### 7.2 Initial assurance-tier matrix

| Component                       | Initial Tier | Target Tier |
| ------------------------------- | -----------: | ----------: |
| Runway-ground kernel            |            1 |           1 |
| Joint transition certification  |            1 |           1 |
| Non-bypass / lifecycle          |            1 |           1 |
| Taxiway certifier               |            2 |           1 |
| Path-graph separation certifier |            2 |           1 |
| Mode-switch / emergency logic   |            2 |           1 |
| Agent proposal validation       |            2 |           2 |
| Phraseology rendering           |            2 |           2 |
| Agent strategic reasoning       |            3 |           3 |

The API remains stable while components are upgraded from lower assurance to higher assurance.

---

## 8. Ownership and Responsibility

Each entity has a formal ownership state:

* `TaxiOwned`
* `RunwayOwned`
* `AirOwned`
* `Joint(d1,d2)`
* `PendingTransfer(from,to)`

No certifier may authorize an act unless ownership rules permit it.

Responsibility transfer is a certified transition. During `PendingTransfer`, authority remains well-defined until completion, expiry, or rejection.

---

## 9. Commands

Commands are typed semantic acts with explicit preconditions.

### 9.1 Ground-only acts

Examples:

* taxi segment authority,
* hold short,
* continue taxi,
* stop,
* route amendment,
* non-runway crossing.

### 9.2 Air-only acts

Examples:

* assign or maintain path edge,
* choose branch in path graph,
* climb or descend altitude band,
* continue altitude transition,
* speed restriction,
* continue approach,
* extend pattern leg,
* go around,
* route amendment.

### 9.3 Joint acts

These require multiple certifications:

* line up and wait,
* cleared for takeoff,
* cleared to land,
* protected runway crossing,
* go-around transition.

Takeoff and landing are joint acts even if the air agent is the usual initiator.

---

## 10. Environment `E` and World `W`

### 10.1 Environment

`E` contains:

* surface graph,
* runway set,
* hold points,
* protected zones,
* airborne path graph,
* altitude bands,
* junction and merge structure,
* guard points for edge transitions,
* procedure rules,
* conflict rules,
* resource rules,
* ownership rules,
* observation model,
* local sequencing constraints.

### 10.2 World state

`W` contains:

* all controlled entities,
* nominal state and uncertainty intervals,
* current edge and longitudinal position,
* speed interval,
* altitude state `AtBand` or `Transitioning`,
* current phase,
* ownership state,
* active clearances and reservations,
* active dependency graph,
* current mode,
* conformance state,
* pending proposals,
* exogenous events.

---

## 11. Environment Well-Formedness

`WellFormed(E)` must be concrete. At minimum it includes:

* the surface graph is connected or explicitly partitioned,
* every surface path intersecting a runway has a hold point before protected entry,
* protected zones cover runway-adjacent conflict points,
* every merge and branch is explicit in the airborne path graph,
* guard points exist for all certified edge transitions,
* altitude-band rules are total for all relevant phase and traffic pairs,
* separation rules are total over aircraft-class × phase × phase,
* conflict rules and resource rules are mutually consistent,
* procedure graphs contain no mandatory deadlocks,
* ownership-transfer paths are total where required,
* no state is simultaneously required and forbidden.

---

## 12. Concrete Validation Instances

Concrete instances are not part of the initial proof foundation.

Instead:

* the runway kernel is proved generically over commitment kinds and conflict
  relations,
* the surface kernel is instantiated later against a concrete simulator surface
  graph,
* the air-path kernel is instantiated later against a concrete simulator airborne
  path graph.

If a minimal validation airport is introduced, it is a later validation artifact
for the graph-dependent kernels, not a foundational prerequisite for the early
proof programme.

---

## 13. Decision Certificates, Active Clearances, and Dependencies

### 13.1 Decision certificates

A decision certificate is a proof object authorizing issuance at the current tick.

It is:

* single-shot,
* current-step only,
* consumed on issuance,
* never reused.

### 13.2 Active clearances and commitments

Issuing a certified command creates a persistent active object.

Lifecycle states include:

* `Active`
* `Satisfied`
* `Expired`
* `Superseded`
* `CancelPending`
* `Cancelled`
* `Violated`

### 13.3 Dependency graph and acyclicity obligation

Active clearances form a finite dependency graph.

Acyclicity is not assumed. It is a certification obligation.

Rule:

* a newly issued active clearance may depend only on already-active prerequisites,
* dependency edges are introduced only from the new clearance to pre-existing active items.

Therefore every dependency edge points backward in issuance order, and acyclicity follows by construction.

Certification of a new active clearance must prove:

* all introduced dependencies are to already-active prerequisites,
* the resulting dependency graph remains backward-pointing in issuance order.

This guarantees termination of revocation propagation.

### 13.4 Restatement and duplicate transmission

A repeated radio transmission of a still-active clearance is a restatement of the same active clearance, not a new issuance.

If the prior clearance is:

* `CancelPending`
* `Expired`
* `Cancelled`
* `Violated`

then any further use requires a new proposal and new certification. Treating it as an existing active clearance is an error.

### 13.5 Monotonicity

Existing clearances are never strengthened in place. Any strengthening or semantic change requires new certification.

---

## 14. Preconditions and Sequencing Constraints

Sequencing constraints are formal preconditions, not agent conventions.

Examples:

* landing clearance requires correct approach phase,
* takeoff clearance requires correct runway and departure-ready phase,
* path changes require legal branch availability and, where needed, junction reservation,
* altitude-band changes require legality under current phase and route,
* runway crossing requires correct approach to the hold point,
* ownership must be correct before any domain can certify the act.

---

## 15. Invariant Families

The system uses a family of invariants, not one monolith.

### 15.1 Runway invariants

* no incompatible runway commitments coexist,
* runway occupancy and reservation remain coherent,
* crossing, landing, line-up, and takeoff commitments do not conflict.

### 15.2 Taxiway invariants

* no incompatible segment authorities coexist,
* protected segments are entered only under authorization,
* taxi movement remains graph-consistent,
* runway entry never occurs without runway authorization.

### 15.3 Air/separation invariants

Defined over the path graph and altitude state:

* minimum longitudinal spacing on shared edges,
* no illegal simultaneous occupancy at merge or junction points,
* no prohibited altitude overlap between `AtBand` or `Transitioning` states,
* phase and path commitments remain consistent with separation rules over `H_sep`.

### 15.4 Interface invariants

* joint acts require all relevant certifications,
* no agent bypasses certification,
* ownership and active clearances remain mutually consistent.

### 15.5 Mode-specific invariants

There are different invariant families for:

* `Normal`
* `Degraded(reason)`
* `Emergency(kind)`

---

## 16. Separation Certifier and Boundary Sufficiency

### 16.1 Separation model

Airborne separation is defined over:

* path-segment occupancy,
* longitudinal spacing on shared edges,
* junction and merge reservation,
* altitude-band or swept-interval overlap,
* phase-specific conflict rules.

### 16.2 Separation-relevant commands

In this model, separation relevance is defined syntactically over command type plus transition annotation.

A command is separation-relevant if it changes any of:

* path assignment,
* branch choice,
* speed bound,
* altitude band or transition state,
* merge or junction reservation,
* missed-approach or departure corridor commitment,
* phase **where that phase change alters the applicable separation rule, conflict neighborhood, or reservation requirement within `H_sep`**.

This is intentionally broader than pure geometry change, but narrower than “every phase change.”

### 16.3 Neutrality of non-certified commands

Commands that are not separation-relevant must satisfy a neutrality property:

* they do not change which entities can conflict within `H_sep`,
* they do not reduce certified separation margins,
* they do not change the applicable separation rule.

### 16.4 Boundary sufficiency theorem

Prove:

> If every separation-relevant airborne act is certified, and all other airborne acts are separation-neutral over `H_sep`, then uncertified airborne acts cannot cause a separation violation within the modeled horizon under nominal assumptions.

This theorem is mandatory.

---

## 17. Liveness and Progress

The system must not satisfy safety by refusing to act forever.

### 17.1 Decision fairness

Every pending proposal is examined within bounded time.

### 17.2 Runway liveness

If a valid runway-related request remains admissible and unblocked, the system must eventually:

* certify it, or
* produce a formal blocker state.

### 17.3 Taxi liveness

If a taxi request remains conflict-free and ownership is valid, it must eventually be granted or formally blocked.

### 17.4 No starvation

No entity may be indefinitely deferred absent explicit priority justification.

Baseline policy:

* priority classes,
* ageing within each class.

### 17.5 Deadlock freedom

The reservation scheme uses an acyclic resource ordering.

This ordering must cover:

* taxiway segments,
* protected surface resources,
* runway reservations,
* and, in two-runway configurations, cross-runway dependencies as well.

Deadlock freedom must be proved against the full reservation scheme.

---

## 18. Nonconformance and Simulator Exceptions

This is a simulator, so nonconformance has two roles.

### 18.1 Scenario-injected nonconformance

The simulator deliberately injects deviation to test the system.

Examples:

* wrong branch choice,
* missed hold short,
* altitude-band bust,
* unexpected speed deviation.

The obligation is that detection, containment, and mode-switch operate correctly in response.

### 18.2 Model-internal nonconformance

The system’s own state becomes inconsistent due to implementation bug or edge case.

This should not occur in the proved model, but runtime monitors should still detect it defensively.

### 18.3 Conformance bands

For active clearances:

* `Conforming`
* `Warning`
* `Violation`

### 18.4 Detection latency

Baseline bound:

* **`δ_det = 2 ticks`**

In the simulator, an injected deviation may be known immediately by the simulator engine, but the formal bound concerns how quickly the certification layer learns of it through the observation model.

### 18.5 Multiple failures

Baseline recovery guarantees are strongest for one simultaneous nonconforming entity or one emergency.

For multiple simultaneous failures, the system must still enter a defined emergency mode and preserve the strongest remaining justified invariant.

---

## 19. Modes and Emergency Command Vocabulary

Modes are explicit:

* `Normal`
* `Degraded(reason)`
* `Emergency(kind)`
* `Emergency(MultiFailure)`
* optional `Recovery`

### 19.1 Emergency command set

The permitted command vocabulary in emergency modes must be concrete.

At minimum, emergency-mode command classes are:

**Surface / runway**

* `FreezeSurface`
* `HoldPosition`
* `StopTaxi`
* `VacateProtectedSegment`
* `CancelSurfaceClearance`
* `ReserveRunwayExclusive`

**Air**

* `MaintainCurrentPath`
* `AssignRecoveryPath`
* `AssignRecoveryBand`
* `HoldOnPathSegment`
* `GoAround`
* `EmergencyLand`
* `CancelAirClearance`

**Joint**

* `EmergencyRunwayReservation`
* `EmergencyGoAroundTransition`
* `ExclusiveRecoveryCorridorActivation`

The degraded/emergency preservation theorems are scoped over this command vocabulary.

---

## 20. Go-Around as a First-Class Joint Transition

Go-around is a primary case study.

`GoAround(a)`:

* terminates or supersedes landing commitment,
* activates the missed-approach path commitment,
* updates runway protection state,
* may block incompatible departures or crossings,
* triggers air-agent resequencing.

Landing certification must prove that before the landing commitment gate, either:

* landing remains certifiable, or
* go-around remains safely enabled.

The go-around transition must preserve the joint invariant family.

---

## 21. Proof Programme

### Phase 0

Lock foundational commitments and define authoritative semantics.

### Phase 1

Runway kernel soundness over commitments and a conflict table.

### Phase 2

Orchestration non-bypass for takeoff, landing, and go-around.

### Phase 3

Taxiway certification and reservation proofs over concrete simulator surface graphs.

### Phase 4

Air-path certification over concrete simulator airborne graphs, plus separation
coverage and boundary sufficiency.

### Phase 5

Modes, nonconformance, disturbance, emergency.

### Phase 6

Phraseology rendering with Tier 2 semantic property checks for the supported subset.

Generic runway and orchestration proofs come first. Concrete graph instantiation
starts only when the surface and air kernels are developed.

---

## 22. Refinement to Code

### 22.1 Refinement relation

Use forward simulation with trace inclusion from implementation to formal model.

### 22.2 Preserved properties

Refinement must preserve:

* safety invariants,
* lifecycle correctness,
* non-bypass properties,
* stated liveness properties under scheduler assumptions.

### 22.3 Finite representation

Most safety-critical quantities here are discrete or bounded numeric values:

* graph edge identity,
* altitude-band index,
* longitudinal position along edge,
* speed bounds,
* reservation state.

Use bounded integers, rationals, or fixed-point values with rounding toward danger where needed. Conservative rational arithmetic is likely sufficient and simpler to verify than general interval arithmetic for most of this model.

Implementation error must be absorbed into the belief-state uncertainty semantics.

### 22.4 Incremental compatibility evaluation

Runtime compatibility checking must be implemented using the same footprint-based decomposition used in the proofs:

* index active clearances by resource footprint,
* check only intersecting footprints plus dependency and ownership interactions,
* avoid full active-set recomputation on each tick.

This is part of the implementation contract, not a later optimization.

### 22.5 Language target

The proof model is language-agnostic.

Rust is preferred for the smallest trusted kernels. Kotlin is acceptable if the same explicit transition and non-bypass discipline is maintained.

---

## 23. Deliverables

The formal developer should produce:

1. Phase 0 commitments document,
2. authoritative formal semantics,
3. `WellFormed(E)` and `Valid(E,W)`,
4. concrete validation instances for the surface and air kernels,
5. typed command API,
6. decision-certificate and active-clearance lifecycle,
7. ownership and transfer rules,
8. runway kernel proofs,
9. joint takeoff, landing, and go-around proofs,
10. taxiway and reservation proofs,
11. path-graph separation proofs,
12. separation-boundary sufficiency theorem,
13. liveness and deadlock results,
14. nonconformance and mode-switch proofs,
15. phraseology Tier 2 property-checking plan,
16. refinement plan to Rust or Kotlin,
17. assurance-tier matrix maintained per delivery milestone.

---

## 24. Final Principle

The system is not intended to prove that the agents are globally intelligent. It is intended to prove something smaller and more useful:

> Agents may propose whatever they like.
> They cannot issue safety-critical commands without certification.
> Certification is explicit about assumptions.
> Assumptions are monitored.
> When assumptions fail, the system withdraws the old guarantee and falls back to the strongest justified degraded or emergency regime.

That is the formal target.

---

## 25. Compendium: Split-Kernel Architecture

This compendium records a refined understanding that supersedes the earlier
implicit assumption of one large shared semantic space.

### 25.1 Architectural revision

The certification core is split into **four independent pure kernels** plus one
orchestration layer.

The kernels are:

* runway kernel,
* surface kernel,
* air-path kernel,
* separation checker.

Each kernel:

* has its own state,
* has its own proposal type,
* has its own approval or rejection type,
* has its own local invariants,
* does not call the other kernels,
* does not know the other kernels exist.

They are libraries, not services.

### 25.2 Runway kernel

The runway kernel is a state machine over runway commitment types and their
incompatibility table.

It is authoritative only for runway commitment conflicts.

Examples of runway commitments include:

* clear,
* line up and wait,
* reserved for landing,
* occupied takeoff roll,
* occupied landing roll,
* protected for crossing,
* vacating.

The runway kernel does **not** require airport topology. It only requires:

* that a runway identifier exists,
* the current active commitments,
* the proposed new commitment,
* the conflict relation between commitment kinds.

### 25.3 Surface kernel

The surface kernel is a graph-reservation checker over the taxiway and protected
surface graph.

It is authoritative for:

* graph-legality of taxi movement,
* segment reservations,
* hold-point use,
* protected-segment entry under explicit authorization tokens.

It does **not** reason about runway conflict policy. If protected entry depends on
runway authority, that authority must arrive as explicit input data.

### 25.4 Air-path kernel

The air-path kernel is a graph-and-altitude reservation checker over the airborne
path graph.

It is authoritative for:

* edge and junction legality,
* branch legality,
* guard-point rules,
* altitude-band legality,
* path and junction reservation conflicts.

It does **not** reason about pairwise separation minima. Those belong to the
separation checker.

### 25.5 Separation checker

The separation checker is a pairwise horizon checker.

It is authoritative for:

* longitudinal spacing,
* vertical overlap,
* phase-conditioned pairwise conflict rules over a finite horizon.

It does **not** discover peers, route aircraft, reserve graph objects, or reason
about airport topology as a planner. It consumes:

* the pair of entity states,
* the candidate subject change,
* the applicable separation rule,
* and the horizon.

Peer selection is therefore an orchestration responsibility, not a separation
responsibility.

### 25.6 Orchestration layer

The orchestration layer is the only component allowed to issue commands.

Its responsibilities are:

1. map a command class to a static certification-plan shape,
2. instantiate the concrete local proposals and separation scenarios from the
   current world,
3. call each required kernel,
4. collect the approvals,
5. run the narrow compatibility check,
6. create the active clearance and update the world,
7. issue if and only if all required steps succeed.

The orchestration layer is therefore the exclusive home of:

* non-bypass,
* routing completeness,
* peer-selection coverage,
* approval bundling,
* compatibility,
* lifecycle integration,
* ownership and mode integration.

### 25.7 Static plan shape versus plan instantiation

The architecture distinguishes two compilation stages.

First, a static routing function:

`compile_command : CommandClass -> PlanTemplate`

This is finite and exhaustive over command classes.

Second, a world-dependent plan-instantiation function:

`instantiate_plan : World -> Command -> CertificationPlan | CompileError`

This constructs the concrete:

* runway proposals,
* surface proposals,
* air proposals,
* separation scenarios.

The routing theorem is about `compile_command`.

The plan-instantiation theorem is about `instantiate_plan`.

These are different proof obligations and must not be conflated.

### 25.8 Compatibility is narrow

The global compatibility check remains necessary, but it is intentionally narrow.

It may reason about:

* footprint overlap,
* dependency consistency,
* ownership consistency,
* mode consistency,
* active-set admission.

It must **not** re-evaluate:

* runway conflict logic,
* surface graph legality,
* air-path graph legality,
* guard-point legality,
* spacing or altitude separation.

If it does, it becomes a hidden fifth model and destroys the decomposition.

### 25.9 Top-level proof split

The proof programme is split into:

* runway-kernel soundness,
* surface-kernel soundness,
* air-kernel soundness,
* separation-checker soundness,
* orchestration routing completeness,
* plan-instantiation correctness,
* separation-peer coverage,
* compatibility narrowness,
* non-bypass.

The global theorem is therefore an orchestration-composition theorem, not a
single monolithic preservation theorem over a giant shared semantic state.

### 25.10 Joint acts

Joint acts remain real, but they are now expressed by plan shape rather than by a
shared internal model.

Examples:

* `ClearedTakeoff`
  * runway + air + separation + compatibility
* `ClearedToLand`
  * runway + air + separation + compatibility
* `GoAround`
  * runway + air + separation + compatibility
* `RunwayCrossing`
  * surface + runway + compatibility

The local kernels do not know they are participating in a joint act. The
orchestration layer knows this from the plan template.

---

## 26. Compendium: Revised Role of Concrete Airport Instances

### 26.1 The earlier `M₀` idea was overloaded

The earlier `M₀` idea bundled together:

* runway commitment logic,
* surface topology,
* airborne path topology.

That was a mistake.

These are different dependencies for different kernels.

### 26.2 What needs a concrete airport instance

The surface kernel needs a concrete surface graph.

The air-path kernel needs a concrete airborne path graph.

Those concrete graphs should come from the simulator airport definitions, not from
an artificial early proof artifact.

### 26.3 What does not need a concrete airport instance

The runway kernel does not need a concrete airport graph.

The separation checker does not need a concrete airport graph as part of its own
logic. It only needs conflict scenarios and rules as parameters.

### 26.4 Consequence for the proof programme

There is no longer a Phase 0.5 devoted to a foundational `M₀`.

Instead:

* Phase 1 proves the runway kernel generically.
* Phase 2 proves the orchestration structure for the first joint acts.
* Phase 3 introduces concrete surface graphs when the surface kernel is developed.
* Phase 4 introduces concrete airborne path graphs when the air-path kernel is developed.

If a minimal airport instance is still useful later, it is a validation instance
for the graph-dependent kernels, not part of the architectural foundation.
