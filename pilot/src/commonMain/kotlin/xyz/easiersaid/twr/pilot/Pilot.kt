package xyz.easiersaid.twr.pilot

import arrow.core.Either
import arrow.core.None
import arrow.core.NonEmptyList
import arrow.core.Option
import arrow.core.Some
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.toOption
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.world.PilotAviationWorld
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.SimTime

/**
 * Unified pilot decision: one brain, one output.
 *
 * Combines kinematic intent (speed, altitude, phase, route) with cognitive
 * decisions (transmissions, mission advancement). The cognitive layer can
 * override kinematic intent when the mission requires it (e.g., go around
 * at decision altitude without clearance).
 *
 * The physical layer ([DefaultPilot]) computes what the aircraft would do
 * kinematically. The cognitive layer ([pilotCognitiveDecide]) advances the
 * mission and generates transmissions. [pilotDecide] merges both, with
 * cognitive overrides taking precedence, returning
 * `Either<RoutingError, PilotOutput>`.
 */

/**
 * Outcome of route planning for the current tick.
 *
 *  - [Skip] — planner has nothing new to say; caller falls back to kinematic
 *    intent + cognitive overrides. This is the normal outcome on most ticks
 *    (mission step doesn't need a route, kinematic route is still good, etc.).
 *  - [Plan] — planner produced a new intent that supersedes kinematic.
 *  - [Failed] — planner raised a [RoutingError]. The top-level [pilotDecide]
 *    bubbles this up as `Either.Left(error)`; the simulator boundary
 *    (`Step.handlePilotTick`) handles the freeze.
 */
internal sealed interface PlanRouteOutcome {
    data object Skip : PlanRouteOutcome
    data class Plan(
        val intent: PilotIntent,
        /**
         * The mission state after this planning tick. Most arms pass the
         * input mission through unchanged (`mission = mission`); the
         * G2 Phase C Transit arm at `planRoute` writes its resolved
         * `transitContactRep` into the mission via
         * `mission = mission.copy(transitContactRep = Some(rep))`.
         *
         * The call site at `pilotDecide` uses this directly (no fold)
         * as `PilotOutput.updatedMission`.
         */
        val mission: PilotMission,
    ) : PlanRouteOutcome
    data class Failed(val error: RoutingError) : PlanRouteOutcome
}

// Pass 16 (D-AUDIT.9 partial closure): DECISION_ALTITUDE_M relocated to
// `:pilot/observe/PilotEvent.kt` alongside the typed event channel.

/**
 * One pilot, one brain, one decision.
 *
 * If the aircraft has no mission, falls back to pure kinematic pilot (legacy).
 * If the aircraft has a mission, the cognitive layer drives everything:
 * - Kinematics are computed by DefaultPilot as a baseline
 * - Cognitive layer can override (go-around when no clearance, hold position when extending)
 * - Transmissions come from the cognitive layer only
 */
fun pilotDecide(input: PilotInput): Either<RoutingError, PilotOutput> {
    val aircraft = input.aircraft
    val kinematicIntent = DefaultPilot.decide(input).getOrElse { err -> return err.left() }

    val mission = aircraft.pilotMission
    if (mission == null || mission.isComplete) {
        return PilotOutput(kinematicIntent, emptyList(), mission).right()
    }

    // fn-11.1: capture the pre-cognitive step to detect the trained-GA
    // Tick A transition (FLY_FINAL_TO_SHORT_FINAL → GOING_AROUND). The
    // detection happens after `pilotCognitiveDecide` advances steps; see
    // `applyPlannedGoAround` below for the response stage.
    val preStep = mission.currentTask?.step

    // Cognitive layer: advance mission, generate transmissions.
    val cognitive = pilotCognitiveDecide(aircraft, mission, input.worldIndex, input.now, input.atisByAerodrome)

    // ── GA-path precedence (deterministic, additive) ─────────────────────
    //
    // FIVE GA paths share `pilotDecide`'s fork point (post fn-15.1 —
    // pilot-reactive tailwind closes the G3a-react trilogy). Order is
    // essential: trained-GA wins on the static-tree transition;
    // ATC-reactive wins on the flag-on-mission signal; self-initiated
    // runs only when neither fired AND the pure-derivation arm produces
    // an event (one of three leaves — DA-without-clearance,
    // tailwind-exceeded, or crosswind-exceeded). Per task spec
    // §"Exact intended control flow" (extended by fn-14.1 + fn-15.1):
    //
    //  1. Trained-GA (fn-11.1, plan-driven): preStep ==
    //     FLY_FINAL_TO_SHORT_FINAL && currentStep == GOING_AROUND.
    //  2. ATC-reactive (fn-12.2, post-cognitive flag): see
    //     [recognizeAtcInitiatedGoAround]. Always inspects the flag (so
    //     it can clear it defensively) regardless of whether trained-GA
    //     fired; trained-GA's natural flow leaves the flag None.
    //  3. Self-initiated — DA-without-clearance (Pass 16, kinematic
    //     pure derivation): pilot has descended to decision altitude
    //     with no clearance. Only invoked when neither trained-GA nor
    //     ATC-reactive fired (per spec R9c — preserves
    //     `SelfInitiatedGoAroundResponseSpec`'s trigger tick + emission
    //     contract unchanged).
    //  4. Self-initiated — pilot-reactive tailwind (fn-15.1, world-
    //     weather pure derivation): pilot reads world wind via
    //     `PilotInput.weatherByAerodrome`; `derivePilotEvent`'s
    //     tailwind branch fires `TailwindLimitExceeded` when the
    //     tailwind component exceeds the aircraft type's POH/AFH-derived
    //     `maxTailwindKnots` while on final. Per-type doctrinal severity
    //     asymmetry (C172 AFH advisory / B738 FCOM hard limit) documented
    //     in the type's `maxTailwindKnots` KDoc; recognition predicate
    //     is uniform.
    //  5. Self-initiated — pilot-reactive crosswind (fn-14.1, world-
    //     weather pure derivation): pilot reads world wind via
    //     `PilotInput.weatherByAerodrome`; `derivePilotEvent`'s
    //     crosswind branch fires `CrosswindLimitExceeded` when the
    //     crosswind component exceeds the aircraft type's POH-derived
    //     `maxCrosswindKnots` while on final.
    //
    // Self-initiated paths 3 / 4 / 5 share this arm — within
    // `derivePilotEvent` branch ordering is DA → tailwind → crosswind
    // (pinned by ordering tests in `PilotEventTailwindTest` and
    // `PilotEventCrosswindTest`; doctrinal rationale per fn-15
    // Decision #5: DA = lowest-altitude / hardest-stop trigger;
    // tailwind = physically stronger constraint, doctrinally hard limit
    // on jet-class types; crosswind = control-authority constraint per
    // AC 23-8B).

    // 1. Trained-GA (fn-11.1).
    val plannedGoAround = if (
        preStep == MissionStep.FLY_FINAL_TO_SHORT_FINAL &&
        cognitive.updatedMission.currentTask?.step == MissionStep.GOING_AROUND
    ) {
        applyPlannedGoAround(cognitive.updatedMission, aircraft, input.now)
    } else null

    // 2. ATC-reactive (fn-12.2). Inspects [PilotMission.pendingAtcGoAroundFrom]
    //    set by `handleGoAround` BEFORE its tree rewrite. Only runs when
    //    trained-GA did NOT fire — trained-GA short-circuits per spec
    //    R9c. When trained-GA wins, the flag is still defensively cleared
    //    via the post-fold reconciliation below (single-cycle invariant)
    //    WITHOUT constructing the typed event leaf or invoking the apply
    //    function — that would produce dead writes masked by trained-GA's
    //    precedence.
    //
    //    Two-layer flag-clear defense: layer 1 — `handleGoAround` only
    //    sets the flag when on-final-eligible; layer 2 — both this
    //    recognition arm (when invoked) and the post-fold reconciliation
    //    below clear the flag on every cycle, so the single-cycle
    //    lifetime invariant holds across all three GA paths' precedence.
    //
    //    Constructs [PilotEvent.AtcGoAroundOnFinal] at the recognition
    //    site (NOT in `derivePilotEvent`) for trace coherence; the apply
    //    function does not consume the event payload but the leaf
    //    formalises the typed channel parallel to fn-11.1.
    val atcGoAroundOutcome: RecognizedAtcGoAround? = if (plannedGoAround == null) {
        recognizeAtcInitiatedGoAround(
            aircraft = aircraft,
            mission = cognitive.updatedMission,
            world = input.world,
        )
    } else null

    // 3. Self-initiated (Pass 16 + fn-14.1 + fn-15.1). Only when neither
    //    trained-GA nor ATC-reactive fired. Self-init's trigger predicate
    //    is independent of trained-GA / ATC-reactive flags, so guard
    //    explicitly.
    //
    //    fn-15.1 (G3a-react-tailwind): `derivePilotEvent` now returns one
    //    of three leaves — `DecisionAltitudeWithoutClearance` (DA path),
    //    `TailwindLimitExceeded` (POH/AFH tailwind path), or
    //    `CrosswindLimitExceeded` (POH crosswind path). All three
    //    dispatch through the self-initiated arm; ordering (DA wins, then
    //    tailwind wins, then crosswind) is enforced inside
    //    `derivePilotEvent` (DA → tailwind → crosswind branch order;
    //    pinned by the ordering test rows in
    //    `PilotEventTailwindTest` + `PilotEventCrosswindTest`).
    //
    //    The `when` arms below are written in the **same dispatch order**
    //    (DA → tailwind → crosswind) so the visual ordering aligns with
    //    the documented branch order. Functionally the dispatch is
    //    order-independent — only one event surfaces per call — but the
    //    visual alignment aids reader clarity.
    // fn-28 round-detekt: self-init dispatch extracted to
    // [dispatchSelfInitiatedEvent] to keep `pilotDecide` under the LongMethod
    // + CyclomaticComplexity thresholds. The helper returns a typed bundle
    // carrying the three possible apply-path outcomes; co-occurrence is
    // structurally impossible (PilotEvent leaves are disjoint) but the
    // bundle shape mirrors the elvis-chain precedence below.
    val selfInit: SelfInitiatedDispatchResult =
        if (plannedGoAround == null && atcGoAroundOutcome?.intent == null) {
            dispatchSelfInitiatedEvent(aircraft, cognitive.updatedMission, input)
        } else {
            SelfInitiatedDispatchResult(goAround = null, densityAltitudeDecline = null, abortTakeoff = null)
        }
    val goAround: GoAroundResult? = selfInit.goAround
    val densityAltitudeDecline: DensityAltitudeDeclineResult? = selfInit.densityAltitudeDecline
    val abortTakeoff: AbortTakeoffResult? = selfInit.abortTakeoff

    // Effective mission: trained-GA → ATC-reactive (only when it fired
    // intent) → DA-decline → self-init → cognitive baseline. The post-fold
    // flag re-clear below restores the single-cycle invariant for whichever
    // path won.
    //
    // fn-28.2 (R13 / R14): DA-decline slots BETWEEN ATC-reactive and
    // self-init paths in the mission-precedence chain. DA-decline is a
    // pre-taxi terminal decision; if the cognitive layer were to advance
    // past REQUEST_TAXI in the same tick the decline fired, the rewritten
    // tree (DECLINE_DEPARTURE NON_COMPLETING leaf) takes precedence.
    //
    // fn-28.9: AbortTakeoff slots BETWEEN DA-decline and self-init in
    // the mission-precedence chain. Abort is a takeoff-roll terminal
    // decision (mission shape disjoint from DA-decline's pre-taxi shapes
    // by construction; co-occurrence is structurally impossible — but
    // the chain is enumerated for reader clarity + future symmetry).
    // The rewritten tree (ABORTED NON_COMPLETING leaf) takes precedence
    // over the cognitive-baseline mission for the same reasoning as
    // DA-decline.
    val effectiveMission = (
        plannedGoAround?.mission
            ?: (atcGoAroundOutcome?.mission?.takeIf { atcGoAroundOutcome.intent != null })
            ?: densityAltitudeDecline?.mission
            ?: abortTakeoff?.mission
            ?: goAround?.mission
            ?: atcGoAroundOutcome?.mission  // discriminator-fail path: flag-cleared mission
            ?: cognitive.updatedMission
        )
        .copy(pendingAtcGoAroundFrom = None)  // single-cycle invariant: flag NEVER persists

    // fn-28.2 (R14 / round-13 Major 1): cognitive-suppression mechanism.
    // When DA-decline fires with `suppressSameTickCognitive = true`, the
    // per-step cognitive transmission (`Request(RequestTaxi)` etc.) that
    // would otherwise fire on the same tick is zeroed. The suppression
    // applies BEFORE every PilotOutput construction site (both
    // `PlanRouteOutcome.Plan` and `PlanRouteOutcome.Skip`) — covers ALL
    // pilotDecide return paths, NOT only the Skip path. The
    // `PlanRouteOutcome.Failed` branch returns `Either.Left` (no
    // PilotOutput); the suppression flag has no transmission slot to
    // affect on the error path.
    //
    // Refactored to the [applyCognitiveSuppression] focused seam (codex
    // round-2 fix): the suppression logic is now a named helper, unit-
    // tested directly. Both pilotDecide return branches call the same
    // helper output (`effectiveCognitiveTransmissions`), guaranteeing
    // structural code-share without requiring a "DA-decline AND Plan
    // route both fire" test scenario (which is structurally impossible
    // by R16 design — DA-decline gates pre-taxi, planRoute Plan branch
    // requires airborne / Transit-cruise context).
    //
    // fn-28.9 (R14 round-15 Major 2 contract): abort-takeoff's
    // `suppressSameTickCognitive = true` ORs into the suppression flag
    // alongside DA-decline. Same structural code-share via the shared
    // [applyCognitiveSuppression] helper — the helper runs once and the
    // resulting `effectiveCognitiveTransmissions` flows into BOTH the
    // `PlanRouteOutcome.Plan` and `PlanRouteOutcome.Skip` branches. The
    // contract: abort apply's flag must filter `cognitive.transmissions`
    // BEFORE every `PilotOutput` construction site, including any
    // error/fallback paths. The `Failed` branch returns `Either.Left`
    // (no PilotOutput); the flag has no transmission slot to affect on
    // the error path, but the structural code-share covers the Plan +
    // Skip return-path universe.
    val suppressSameTickCognitive: Boolean =
        densityAltitudeDecline?.suppressSameTickCognitive == true ||
            abortTakeoff?.suppressSameTickCognitive == true
    val effectiveCognitiveTransmissions: List<PilotTransmission> =
        applyCognitiveSuppression(cognitive.transmissions, suppressSameTickCognitive)
    val goAroundTransmissions = goAround?.transmissions ?: emptyList()

    // Plan execution: if the current task needs an airborne route the pilot
    // doesn't have yet, the route planner builds one. Pass the kinematic route
    // so the Visual-mode planner can compare against the post-pop state and avoid
    // regressing waypoints already consumed by DefaultPilot.
    //
    // No `activeRunway` parameter: the pilot's runway is `mission.activeRunway`,
    // populated only by `processInstruction` from radio-derived sources (Phase C
    // of the pilot-firewall plan). The previous Step.kt:125-127 lookup that
    // peeked at `state.beliefs[…].commitments[…].runway` is structurally
    // unreachable here — `:pilot` cannot import `:controller`.
    val planOutcome = planRoute(
        effectiveMission, aircraft, kinematicIntent.route, input.world, input.worldIndex,
    )
    return buildPilotOutput(
        planOutcome = planOutcome,
        kinematicIntent = kinematicIntent,
        effectiveMission = effectiveMission,
        plannedGoAround = plannedGoAround,
        atcGoAroundOutcome = atcGoAroundOutcome,
        densityAltitudeDecline = densityAltitudeDecline,
        abortTakeoff = abortTakeoff,
        goAround = goAround,
        effectiveCognitiveTransmissions = effectiveCognitiveTransmissions,
        goAroundTransmissions = goAroundTransmissions,
    )
}

// dispatchSelfInitiatedEvent + buildPilotOutput moved to PilotDispatch.kt
// (round-detekt: keeps Pilot.kt under the TooManyFunctions threshold).

/**
 * fn-14.1 (G3a-react): resolve the [xyz.easiersaid.twr.protocol.WindReport]
 * relevant to this mission's active aerodrome. Lives in `Pilot.kt`
 * (NOT private in `PilotCognitive.kt`) because [pilotDecide] above
 * calls it — top-level `private` in Kotlin is file-private.
 *
 * **Goal treatment** (mirrors `atisLetterForCallInbound`'s actual shape
 * at `PilotCognitive.kt:478-500`):
 *  - `Transit.destination` → key lookup
 *  - `Departure.destination` → key lookup (rarely relevant — pilot
 *    is unlikely to be on final approach during Departure, but the
 *    shape is symmetric with ATIS)
 *  - `Arrival` → null fallback (DO NOT use `Arrival.from` — that
 *    field is the origin aerodrome, not the landing one; same
 *    treatment as the ATIS helper)
 *  - `CircuitTraining` → null fallback (single-aerodrome circuits
 *    resolve via the singleton-fallback below)
 *
 * **Singleton fallback — fail-closed on multi-aerodrome ambiguity**:
 * when the goal does not carry a destination key (Arrival /
 * CircuitTraining), pick the singleton entry if there is exactly one,
 * else return `null`. **Differs from `atisLetterForCallInbound` here**:
 * that helper `error()`s on multi-aerodrome ambiguity because the
 * ATIS lookup is sender-aware and a crash is the right loud-failure
 * mode for a wiring defect. The wind lookup runs **every pilot
 * decision cycle**; erroring would crash unrelated multi-aerodrome
 * scenarios that have nothing to do with crosswind recognition. Fail-
 * closed (`null` → crosswind recognition treats as no-event) is the
 * safer choice. Multi-aerodrome crosswind recognition (G3b-react) is
 * the deferred sibling — `D-PASS-g3b-react-cross-aerodrome-crosswind`.
 *
 * `internal` so unit tests can pin the goal-by-goal mapping
 * independently of `pilotDecide`.
 */
internal fun windForMission(
    mission: PilotMission,
    weatherByAerodrome: Map<xyz.easiersaid.twr.protocol.AerodromeId, xyz.easiersaid.twr.protocol.WindReport>,
): xyz.easiersaid.twr.protocol.WindReport? {
    val targetAerodrome: xyz.easiersaid.twr.protocol.AerodromeId? = when (val g = mission.goal) {
        is HighLevelGoal.Transit -> g.destination
        is HighLevelGoal.Departure -> g.destination
        is HighLevelGoal.Arrival, is HighLevelGoal.CircuitTraining -> null
    }
    if (targetAerodrome != null) return weatherByAerodrome[targetAerodrome]
    return when (weatherByAerodrome.size) {
        0 -> null
        1 -> weatherByAerodrome.values.single()
        else -> null // fail-closed on multi-aerodrome ambiguity (G3b-react deferment).
    }
}

/**
 * fn-28.1 (G3a-react-density-altitude foundation A): resolve the
 * [DensityAltitudeInput] relevant to this mission's aerodrome.
 *
 * **NOT a mirror of [windForMission]**: codex impl-review round 1 fix
 * (Major, 75% confidence) — DA-decline is a **departure-side, pre-taxi
 * decision** (the pilot computes DA at the apron BEFORE requesting taxi
 * for departure). The relevant aerodrome is the **departure aerodrome**,
 * not the destination. [HighLevelGoal.Departure] and
 * [HighLevelGoal.Transit] both carry `destination` (the aerodrome the
 * pilot is going TO), not `from` (the apron the pilot is AT). Mirroring
 * `windForMission`'s `g.destination` lookup would bake the wrong source
 * aerodrome into the DA recognition pipeline — for a multi-aerodrome
 * LOWG → LJMB departure it would either pick LJMB's DA inputs (the
 * wrong aerodrome — the pilot is still at LOWG's apron, deciding
 * whether to fly the departure at all) or fail closed if only LOWG had
 * weather populated, masking the foundation defect.
 *
 * **Goal treatment** (corrected per round-1 fix):
 *  - `Departure` / `Transit` / `Arrival` / `CircuitTraining` → null
 *    explicit-key lookup (none of the [HighLevelGoal] variants carry a
 *    typed *departure* aerodrome; `from` on `Arrival` is the origin of
 *    an arrival mission shape, not the apron the pilot is at for a
 *    decline-departure decision).
 *  - **Singleton fallback** below covers the single-aerodrome scenario
 *    fn-28.3's G3aPilotReactiveDensityAltitudeTest depends on (LOWG-only
 *    fixture → the map carries exactly one entry → singleton fallback
 *    returns it).
 *
 * **Multi-aerodrome DA recognition is out of fn-28.1 scope.** Once
 * fn-28.2 lands the DA-decline branch, multi-aerodrome scenarios will
 * need an explicit departure-aerodrome source — either:
 *   (a) a new `Departure.departureAerodrome` field on the goal type;
 *   (b) derive from `aircraft.positionPoint` → `worldIndex` → aerodrome
 *       containing the point;
 *   (c) thread from `FiledPlan.Vfr.departureAerodrome` via mission
 *       state.
 * The decision is deferred to fn-28.2 plan-review per the worker-time
 * "Resolved during implementation" discipline. fn-28.1 fails closed for
 * the multi-aerodrome case (singleton fallback fires only when the map
 * has exactly one entry; ≥2 → null). Filed as the conventional sibling
 * deferment `D-PASS-g3b-react-density-altitude` per fn-14.1 /
 * `D-PASS-g3b-react-cross-aerodrome-crosswind` pattern.
 *
 * **Singleton fallback — fail-closed on multi-aerodrome ambiguity**:
 * when the goal does not carry a usable key (i.e. always, post-
 * round-1-fix), pick the singleton entry if there is exactly one,
 * else `null`. fn-28.2's DA recognition treats `null` as no-event.
 *
 * `internal` so fn-28.2's pilot-side unit tests can pin the goal-by-
 * goal mapping independently of `pilotDecide`.
 */
internal fun densityAltitudeInputForMission(
    mission: PilotMission,
    densityAltitudeInputsByAerodrome: Map<xyz.easiersaid.twr.protocol.AerodromeId, DensityAltitudeInput>,
): DensityAltitudeInput? {
    // fn-28.2 (round-9 Major 2 / acceptance contract): aerodrome-
    // resolution policy.
    //
    // **Order** (fail-closed at every step):
    //  1. **Filed-plan departure aerodrome first**: when
    //     `mission.filedPlan` carries a `departureAerodrome`, use it as
    //     the lookup key. This is the doctrinally-correct source for
    //     "the aerodrome the pilot is AT" in a pre-taxi DA-decline
    //     decision — pilots file a plan with their departure aerodrome
    //     before requesting taxi.
    //  2. **Singleton fallback**: when no filed plan exists and the
    //     map has exactly one entry, use it (the single-aerodrome
    //     scenario fn-28.3's G3a golden depends on).
    //  3. **Fail-closed ambiguity**: when no filed plan exists AND the
    //     map has 2+ entries → null. Multi-aerodrome DA without filed
    //     plan is filed as `D-PASS-g3b-react-density-altitude`.
    //
    // **NOT a mirror of [windForMission]** (codex round-1 memory anchor):
    // wind GA recognition resolves via `g.destination` (the runway being
    // approached on final); DA-decline resolves via the apron the pilot
    // is AT. The two helpers represent opposite decision-side concerns
    // even though they share a similar Map shape — see memory entry
    // `bug/build-errors/ga-path-precedence-reorder-when-adding-2026-05-10`
    // (2026-05-16 update) for the full sibling-helper-shape rationale.
    val filedDeparture = mission.filedPlan.getOrNull()?.departureAerodrome
    if (filedDeparture != null) {
        // Filed plan present — fail-closed strict map lookup. If the
        // filed departure has no entry in the projection map (PilotWiring
        // dropped it due to null OAT/QNH or invalid elevation), DA
        // recognition skips this mission. NOT a fallback to singleton —
        // a filed plan that disagrees with the projection is the
        // recognition-fail case, not a "best effort" path.
        return densityAltitudeInputsByAerodrome[filedDeparture]
    }
    // No filed plan — fall back to singleton-only resolution. Multi-
    // aerodrome maps with no filed plan are out-of-scope for v1 DA
    // recognition (filed as `D-PASS-g3b-react-density-altitude`).
    return when (densityAltitudeInputsByAerodrome.size) {
        0 -> null
        1 -> densityAltitudeInputsByAerodrome.values.single()
        else -> null // fail-closed on multi-aerodrome ambiguity (G3b-DA deferment).
    }
}

/**
 * Derive the pilot's airborne route from mission state and current position.
 *
 * **Aviate, navigate, communicate**: the pilot re-derives their route every tick.
 * For Visual mode this is continuous — any change to mission state (restriction,
 * join leg, step advancement) is reflected immediately without explicit route
 * invalidation. A stability check suppresses intent updates when the derived route
 * is identical to the one currently being flown.
 *
 * For non-Visual modes (Circuit, Instrument) the one-shot behaviour is preserved
 * until IFR wiring lands — routes are built once and not rebuilt mid-flight.
 *
 * Fires for:
 * - **Circuit mode + FLY_DEPARTURE**: T&G lift-off or route upgrade (always).
 * - **Visual mode, any airborne step**: continuous re-derivation from position.
 * - **Other modes, airborne step, no route**: one-shot bootstrap.
 *
 * Returns a [PlanRouteOutcome]. [PlanRouteOutcome.Failed] surfaces routing
 * errors back to [pilotDecide], which bubbles them up as `Either.Left`;
 * the simulator boundary handles the freeze. Previously a swallowed
 * `getOrElse { return null }` made non-Visual routing failures invisible.
 */
@Suppress("ReturnCount") // multi-stage invariant validation; early-return is the FP-correct shape
internal fun planRoute(
    mission: PilotMission,
    aircraft: AircraftState,
    kinematicRoute: PilotRoute,
    world: PilotAviationWorld,
    worldIndex: WorldIndex,
): PlanRouteOutcome {
    val step = mission.currentTask?.step ?: return PlanRouteOutcome.Skip

    // G2 Phase C: cross-aerodrome Transit cruise. Fires BEFORE the activeRunway
    // gate below — Transit cruise doesn't depend on a local runway assignment.
    // See [planTransitCruise] for the cache + resolve logic.
    //
    // fn-28.6 (R14 round-14 Major 1): Transit-cruise discriminator.
    // After a Transit-arrival reactive GA suffix replacement (R22), the
    // active leaf advances into the recovery `circuitTask()` subtree —
    // FLY_DEPARTURE becomes active AGAIN as the first primitive of the
    // recovery circuit, but the mission goal is still
    // `HighLevelGoal.Transit`. The pre-discriminator code would route this
    // FLY_DEPARTURE through `planTransitCruise` (sending the aircraft toward
    // the destination's first published REP at cruise altitude) — WRONG
    // for the post-GA recovery, which needs the Circuit-mode GA climb
    // path.
    //
    // **Discriminator**: treat Transit+FLY_DEPARTURE as cruise ONLY when
    // the active primitive is the ORIGINAL flat Transit departure primitive
    // (i.e. a direct child of the Transit compound). When the active
    // primitive lives inside a nested `Circuit` / `CircuitAfterGoAround`
    // / `GoAround` compound (post-suffix-replacement recovery), fall
    // through to the regular planRoute logic — Tick B's Circuit-mode
    // GA-route special case (`planCircuitTrainedGoAround`) and Visual-mode
    // GA-route special case (in `planVisualRoute`) handle the recovery
    // climb.
    if (step == MissionStep.FLY_DEPARTURE && mission.goal is HighLevelGoal.Transit) {
        val activeInner = mission.root.activeCompound()?.name
        val isRecoveryShape = activeInner is TaskName.Circuit ||
            activeInner is TaskName.CircuitAfterGoAround ||
            activeInner is TaskName.GoAround
        if (!isRecoveryShape) {
            return planTransitCruise(mission, mission.goal, aircraft, world)
        }
        // else: fall through — Transit post-GA recovery FLY_DEPARTURE goes
        // through the same path as a CircuitTraining FLY_DEPARTURE.
    }

    // The pilot's runway is on the mission, populated by `processInstruction`
    // from each radio-derived runway source (TaxiTo→holding-point lookup,
    // LineUpAndWait/ClearedForTakeoff/Land/Vacate explicit). No fallback to
    // a controller-state read — the pilot is genuinely silent until the
    // controller speaks. Phase C of the pilot-firewall plan.
    val rwy = mission.activeRunway.getOrNull()?.runway ?: return PlanRouteOutcome.Skip
    val w = world
    val mode = mission.navigationMode.getOrNull()
        ?: deriveNavigationMode(mission.goal, rwy, w)
            .fold({ return PlanRouteOutcome.Failed(it) }, { it })

    // FLY_DEPARTURE in Circuit mode: T&G lift-off or short-route upgrade.
    if (step == MissionStep.FLY_DEPARTURE && mode is NavigationMode.Circuit) {
        when (val dep = planCircuitDeparture(mission, aircraft, mode, w)) {
            is PlanRouteOutcome.Skip -> Unit  // fall through to airborne-step check below
            is PlanRouteOutcome.Plan, is PlanRouteOutcome.Failed -> return dep
        }
    }

    // fn-11.1 (G3a-trained Tick B): Circuit-mode go-around route special-case.
    // See [planCircuitTrainedGoAround] for the predicate + rationale.
    if (isCircuitTrainedGoAroundTickB(step, mode, aircraft, kinematicRoute)) {
        return planCircuitTrainedGoAround(mode as NavigationMode.Circuit, mission, aircraft, w)
    }

    val airborneSteps = setOf(
        // Flying steps
        MissionStep.FLY_DOWNWIND, MissionStep.FLY_BASE, MissionStep.FLY_FINAL,
        // fn-11.1 (G3a-trained): trained-GA short-final descent leg uses the
        // same airborne-route requirement as FLY_FINAL — the route planner
        // aliases it to the standard circuit pattern below.
        MissionStep.FLY_FINAL_TO_SHORT_FINAL,
        MissionStep.FLY_DEPARTURE, MissionStep.FLY_SID, MissionStep.FLY_EN_ROUTE,
        MissionStep.FLY_STAR, MissionStep.FLY_APPROACH, MissionStep.FLY_MISSED_APPROACH,
        // Airborne report/sequencing steps — pilot needs a route while transmitting
        MissionStep.REPORT_DOWNWIND, MissionStep.REPORT_BASE, MissionStep.REPORT_FINAL,
        MissionStep.AWAIT_SEQUENCING, MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,
        // fn-28.2 (R15): `DECLINE_DEPARTURE` is DELIBERATELY ABSENT — DA
        // decline is an apron-terminal state with no airborne route. The
        // `step !in airborneSteps` guard returns `PlanRouteOutcome.Skip`
        // → no planning fires.
        // fn-28.8 (R15): `ABORTED` is DELIBERATELY ABSENT — abort-takeoff
        // is a runway-terminal state with no airborne route. The
        // `step !in airborneSteps` guard returns `PlanRouteOutcome.Skip`
        // → no planning fires. Combined with the fn-28.9 abort apply's
        // `targetSpeedMps = 0` Tick A intent, the aircraft remains
        // at-rest on the runway.
    )
    if (step !in airborneSteps) return PlanRouteOutcome.Skip

    // G2 Phase I: cross-aerodrome arrival-pattern routing. The Transit
    // mission tree has FLY_DOWNWIND..LAND as direct primitive children of
    // the Transit compound, so `mission.root.activeCompound()` returns null
    // at those steps (no inner CompoundTask to return). For Transit-arrival
    // airborne steps with a joinLeg set (post-ARR-JOIN-CIRCUIT), use
    // `TaskName.Circuit` semantics directly — `buildCircuitFromLeg` produces
    // the route from current position into the destination's pattern.
    //
    // Fires BEFORE the activeCompound check below — for non-Transit missions
    // and for Transit missions still in cruise (FLY_DEPARTURE handled above
    // by the Transit-cruise arm) this branch is inert.
    val transitArrivalAirborneSteps = setOf(
        MissionStep.FLY_DOWNWIND, MissionStep.FLY_BASE, MissionStep.FLY_FINAL,
        MissionStep.REPORT_DOWNWIND, MissionStep.REPORT_BASE, MissionStep.REPORT_FINAL,
        MissionStep.AWAIT_SEQUENCING, MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,
    )
    if (mission.goal is HighLevelGoal.Transit && step in transitArrivalAirborneSteps) {
        return planVisualRoute(
            mode as NavigationMode.Visual,
            TaskName.Circuit,
            mission,
            aircraft,
            kinematicRoute,
            w,
            worldIndex,
        )
    }

    val taskName = mission.root.activeCompound()?.name ?: return PlanRouteOutcome.Skip

    return when (mode) {
        // Visual mode: continuous re-derivation from mission state + kinematic position.
        is NavigationMode.Visual -> planVisualRoute(mode, taskName, mission, aircraft, kinematicRoute, w, worldIndex)

        // Non-Visual: one-shot bootstrap. Route is built once and not rebuilt mid-flight.
        // IFR continuous planning is deferred until IFR missions land.
        else -> {
            if (aircraft.route !is PilotRoute.None) return PlanRouteOutcome.Skip
            buildAirborneRoute(mode, taskName, w, aircraft.type).fold(
                ifLeft = { PlanRouteOutcome.Failed(it) },
                ifRight = { route ->
                    PlanRouteOutcome.Plan(
                        intent = PilotIntent(
                            targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) aircraft.type.kinematics.climbSpeedMps
                                else aircraft.type.kinematics.approachSpeedMps,
                            phase = aircraft.phase,
                            route = route,
                            targetAltitudeM = route.targetAltitudeM,
                        ),
                        mission = mission,
                    )
                },
            )
        }
    }
}

/**
 * G2 Phase C: cross-aerodrome Transit cruise route — runs from current
 * position to the destination's first published contact REP (resolved
 * from `world.aerodromes[destination].aip.publishedVfrProcedures`). Caches
 * the resolved REP on `mission.transitContactRep` (write-once per D-G2.4).
 *
 * D-G2.4 tripwire (set-once invariant): the cached REP is valid only for
 * the goal-destination it was resolved for. Today `HighLevelGoal.Transit`
 * is a data class with `val destination`, so a goal-destination change is
 * unrepresentable in current code paths (every "change" produces a new
 * PilotMission). Future fluid-replanning (D-G2.4 real-fix) MUST clear
 * `mission.transitContactRep` whenever the destination changes — see the
 * deferment register.
 */
@Suppress("ReturnCount")
private fun planTransitCruise(
    mission: PilotMission,
    goal: HighLevelGoal.Transit,
    aircraft: AircraftState,
    world: PilotAviationWorld,
): PlanRouteOutcome {
    val destination = goal.destination ?: return PlanRouteOutcome.Skip
    val cachedRep = mission.transitContactRep.getOrNull()
    val rep: PointId
    val updatedMission: PilotMission
    if (cachedRep != null) {
        rep = cachedRep
        updatedMission = mission
    } else {
        when (val resolved = resolveTransitContactRep(world, destination)) {
            is Either.Left -> return PlanRouteOutcome.Failed(resolved.value)
            is Either.Right -> {
                rep = resolved.value
                updatedMission = mission.copy(transitContactRep = Some(rep))
            }
        }
    }
    val airborne = PilotRoute.Airborne(
        waypoints = NonEmptyList(rep, emptyList()),
        targetAltitudeM = aircraft.type.cruiseAltitudeM,
        arrivalPhase = PilotPhase.Climbing,
    )
    return PlanRouteOutcome.Plan(
        intent = PilotIntent(
            targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
            phase = if (aircraft.phase is PilotPhase.AtStand) aircraft.phase else PilotPhase.Climbing,
            route = airborne,
            targetAltitudeM = aircraft.type.cruiseAltitudeM,
        ),
        mission = updatedMission,
    )
}

/**
 * fn-11.1 (G3a-trained Tick B) predicate: detect "we're sitting on Final
 * with no airborne route at FLY_DEPARTURE in Circuit mode" — the signature
 * of the trained-GA Tick A's route invalidation.
 *
 * **Discriminator**: ordinary `CircuitTraining` `FLY_DEPARTURE` happens
 * on-ground (`PilotPhase.LandingRoll` for T&G between circuits, or
 * `AtStand`/`HoldingShort`/`TakeoffRoll` for the first circuit's lift-off
 * from the stand). Phase is never `Final` at that point. The conjunction
 * `Final + no airborne route + FLY_DEPARTURE + Circuit` therefore matches
 * only the post-trained-GA-Tick-A state in practice.
 */
private fun isCircuitTrainedGoAroundTickB(
    step: MissionStep,
    mode: NavigationMode,
    aircraft: AircraftState,
    kinematicRoute: PilotRoute,
): Boolean = step == MissionStep.FLY_DEPARTURE && mode is NavigationMode.Circuit &&
    aircraft.phase is PilotPhase.Final && kinematicRoute !is PilotRoute.Airborne

/**
 * fn-11.1 (G3a-trained Tick B): build the published go-around route after
 * the trained-GA Tick A's route invalidation. Mirrors the Visual-mode
 * special-case at `planVisualRoute` lines 339-370 (reactive flow). Without
 * this, `buildAirborneRoute` for Circuit × FLY_DEPARTURE would build a
 * NORMAL circuit pattern (via `buildCircuitPatternRoute`), causing the
 * aircraft to fly a fresh pattern from the runway threshold instead of
 * climbing out via the GA path.
 */
private fun planCircuitTrainedGoAround(
    mode: NavigationMode.Circuit,
    mission: PilotMission,
    aircraft: AircraftState,
    world: PilotAviationWorld,
): PlanRouteOutcome = buildGoAroundRoute(mode.runway, world, aircraft.type, CircuitLookup.ById(mode.procedure)).fold(
    ifLeft = { PlanRouteOutcome.Failed(it) },
    ifRight = { gaRoute ->
        val patternAlt = aircraft.type.circuitPattern.altitudeAglM
        val gaAlt = mission.altitudeRestrictionM.map { minOf(patternAlt, it) }
            .getOrElse { patternAlt }
        PlanRouteOutcome.Plan(
            intent = PilotIntent(
                targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
                phase = PilotPhase.Climbing,
                route = gaRoute.copy(targetAltitudeM = gaAlt),
                targetAltitudeM = gaAlt,
            ),
            mission = mission,
        )
    },
)

/**
 * Continuous route derivation for Visual mode.
 *
 * **Aviate, navigate, communicate**: the pilot re-derives their route every tick.
 * Any change to mission state (ATC join instruction, altitude restriction) is reflected
 * immediately without explicit route invalidation.
 *
 * **Stability**: we compare against [kinematicRoute] (the DefaultPilot's post-pop route),
 * not [AircraftState.route] (the pre-pop route). This prevents two failure modes:
 * - Regression: after DefaultPilot pops DOWNWIND-1, aircraft.route = [BASE-1, ...]. If we
 *   compare against aircraft.route, the position-derived [DOWNWIND-1, ...] looks different
 *   and we'd steer the aircraft back to DOWNWIND-1.
 * - Over-planning: kinematic is already past the ATC-instructed join point — suffix check
 *   suppresses the re-plan and lets the kinematic pilot continue forward.
 *
 * **Join leg priority**: ATC instruction ([PilotMission.joinLeg]) takes precedence. When
 * the instruction is not set, leg is derived from [kinematicRoute]'s head waypoint (where
 * the pilot is heading next), falling back to [AircraftState.positionPoint] for bootstrap
 * (no route yet).
 */
@Suppress("ReturnCount") // guard-clause early returns enumerate failure modes per task type;
// folding into a single expression obscures which precondition failed.
private fun planVisualRoute(
    mode: NavigationMode.Visual,
    taskName: TaskName,
    mission: PilotMission,
    aircraft: AircraftState,
    kinematicRoute: PilotRoute,
    world: PilotAviationWorld,
    worldIndex: WorldIndex,
): PlanRouteOutcome {
    val step = mission.currentTask?.step
    val cur = kinematicRoute as? PilotRoute.Airborne

    // ── No kinematic airborne route: special-case Final-phase transitions ─────
    // A Final-phase aircraft with no airborne route is in one of two transitions:
    //   - LAND step: kinematic just committed to landing (last waypoint consumed,
    //     LandingRoll intent issued). Don't plan — would interrupt the flare.
    //   - FLY_DEPARTURE step: GOING_AROUND just reported, step advanced but the
    //     aircraft is still on final. Build the go-around climb route so the
    //     pilot immediately climbs out.
    // Other airborne-step + no-route cases (initial spawn into a flying phase,
    // mid-circuit waypoint exhaustion) fall through to the normal derivation
    // below, which will produce a fresh circuit route from mission state.
    if (cur == null && aircraft.phase is PilotPhase.Final) {
        if (step == MissionStep.LAND) return PlanRouteOutcome.Skip
        if (step == MissionStep.FLY_DEPARTURE) {
            return buildGoAroundRoute(mode.runway, world, aircraft.type).fold(
                ifLeft = { PlanRouteOutcome.Failed(it) },
                ifRight = { gaRoute ->
                    val patternAlt = aircraft.type.circuitPattern.altitudeAglM
                    val gaAlt = mission.altitudeRestrictionM.map { minOf(patternAlt, it) }
                        .getOrElse { patternAlt }
                    PlanRouteOutcome.Plan(
                        intent = PilotIntent(
                            targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
                            phase = PilotPhase.Climbing,
                            route = gaRoute.copy(targetAltitudeM = gaAlt),
                            targetAltitudeM = gaAlt,
                        ),
                        mission = mission,
                    )
                },
            )
        }
    }

    // ── Derive the route from mission state + kinematic position ───────────────
    // Use kinematic route head (where we're heading) rather than positionPoint (where we've
    // been) to avoid re-inserting waypoints the DefaultPilot already consumed.
    val lookupPoint = cur?.waypoints?.head ?: aircraft.positionPoint
    val derivedLeg = deriveCurrentCircuitLeg(lookupPoint, worldIndex).toOption()
    // ATC join instruction takes priority over position-derived leg.
    // Exhaustive when — Option is sealed (Some | None); no silent else branch.
    val joinLeg = when (val jl = mission.joinLeg) {
        is Some -> jl
        is None -> derivedLeg
    }

    val derivedRoute = buildVisualModeRoute(mode, taskName, world, aircraft.type, joinLeg)
        .getOrElse { err -> return PlanRouteOutcome.Failed(err) }

    // Apply ATC altitude restriction (E3): StopClimbAt caps targetAltitudeM so the
    // pilot levels off at the restricted altitude rather than climbing to circuit altitude.
    val targetAlt = mission.altitudeRestrictionM
        .map { minOf(derivedRoute.targetAltitudeM, it) }
        .getOrElse { derivedRoute.targetAltitudeM }

    // ── Bootstrap completion (no prior kinematic route) ────────────────────────
    if (cur == null) {
        return PlanRouteOutcome.Plan(
            intent = PilotIntent(
                targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) aircraft.type.kinematics.climbSpeedMps
                    else aircraft.type.kinematics.approachSpeedMps,
                phase = aircraft.phase,
                route = derivedRoute.copy(targetAltitudeM = targetAlt),
                targetAltitudeM = targetAlt,
            ),
            mission = mission,
        )
    }

    // ── Stability: compare against kinematic route (post-pop), not aircraft.route ──
    // Exact match: nothing has changed.
    if (cur.waypoints == derivedRoute.waypoints &&
        cur.targetAltitudeM == targetAlt &&
        cur.arrivalPhase == derivedRoute.arrivalPhase) {
        return PlanRouteOutcome.Skip
    }

    // Kinematic is already a suffix of the derived route: the aircraft has progressed
    // past the derived join point. Don't regress waypoints — but altitude may still change.
    val newWps = derivedRoute.waypoints.toList()
    val curWps = cur.waypoints.toList()
    val kinematicAlreadyAhead = newWps.size > curWps.size && newWps.takeLast(curWps.size) == curWps
    if (kinematicAlreadyAhead) {
        // On the last waypoint (committed to landing or final waypoint): always suppress.
        // Prevents rebuilding from the threshold point (which maps to both FINAL and UPWIND)
        // and disrupting the kinematic pilot's final descent commitment.
        if (cur.waypoints.tail.isEmpty()) return PlanRouteOutcome.Skip
        // Earlier waypoints: suppress if nothing changed; update altitude in-place if it did.
        if (cur.targetAltitudeM == targetAlt && cur.arrivalPhase == derivedRoute.arrivalPhase) {
            return PlanRouteOutcome.Skip
        }
        return PlanRouteOutcome.Plan(
            intent = PilotIntent(
                targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) aircraft.type.kinematics.climbSpeedMps
                    else aircraft.type.kinematics.approachSpeedMps,
                phase = aircraft.phase,
                route = cur.copy(targetAltitudeM = targetAlt),
                targetAltitudeM = targetAlt,
            ),
            mission = mission,
        )
    }

    return PlanRouteOutcome.Plan(
        intent = PilotIntent(
            targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) aircraft.type.kinematics.climbSpeedMps
                else aircraft.type.kinematics.approachSpeedMps,
            phase = aircraft.phase,
            route = derivedRoute.copy(targetAltitudeM = targetAlt),
            targetAltitudeM = targetAlt,
        ),
        mission = mission,
    )
}

/**
 * Derive the circuit leg the aircraft is currently on from its WorldIndex position.
 *
 * Returns null when the aircraft is not at a mapped circuit waypoint (e.g., on the
 * apron, inbound from outside the circuit). The caller falls back to [PilotMission.joinLeg].
 *
 * Priority: FINAL > BASE > DOWNWIND > CROSSWIND > UPWIND.
 * FINAL takes priority because the threshold PointId maps to both FINAL and UPWIND;
 * when on approach to land, FINAL is the correct interpretation. Aircraft departing
 * from threshold are handled by [planCircuitDeparture] before this function is called.
 */
private fun deriveCurrentCircuitLeg(position: PointId, worldIndex: WorldIndex): LegName? {
    val legs = worldIndex.circuitLegsByPoint[position]
    if (legs.isNullOrEmpty()) return null
    val priority = listOf(LegName.FINAL, LegName.BASE, LegName.DOWNWIND, LegName.CROSSWIND, LegName.UPWIND)
    return priority.firstOrNull { it in legs }
}

/** Circuit-mode FLY_DEPARTURE: T&G lift-off or short-route upgrade to full circuit. */
private fun planCircuitDeparture(
    mission: PilotMission,
    aircraft: AircraftState,
    mode: NavigationMode.Circuit,
    world: PilotAviationWorld,
): PlanRouteOutcome {
    when (aircraft.phase) {
        is PilotPhase.LandingRoll -> Unit
        is PilotPhase.TakeoffRoll -> {
            val current = aircraft.route as? PilotRoute.Airborne ?: return PlanRouteOutcome.Skip
            // Already upgraded — nothing to do.
            if (current.arrivalPhase is PilotPhase.LandingRoll) return PlanRouteOutcome.Skip
        }
        else -> return PlanRouteOutcome.Skip
    }

    val taskName = mission.root.activeCompound()?.name ?: return PlanRouteOutcome.Skip
    return buildAirborneRoute(mode, taskName, world, aircraft.type).fold(
        ifLeft = { PlanRouteOutcome.Failed(it) },
        ifRight = { route ->
            PlanRouteOutcome.Plan(
                intent = PilotIntent(
                    targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
                    phase = PilotPhase.TakeoffRoll,
                    route = route,
                    targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM,
                ),
                mission = mission,
            )
        },
    )
}

/**
 * Result of a self-initiated go-around. Pass 16 (D-AUDIT.9 partial)
 * exposes as `internal` for direct testing of `applySelfInitiatedGoAround`.
 */
internal data class GoAroundResult(
    val intent: PilotIntent,
    val mission: PilotMission,
    val transmissions: List<PilotTransmission>,
)

/**
 * fn-28.6 (G3b cross-aerodrome reactive-GA R18 / R22 / round-4 Major 1 +
 * round-11 Major 1): Transit-arrival reactive-GA eligibility guard.
 *
 * **What this gates**: a pilot-reactive wind-exceedance go-around during the
 * arrival pattern of a `HighLevelGoal.Transit` mission, where the arrival
 * primitives (FLY_FINAL / REPORT_FINAL / AWAIT_LANDING_CLEARANCE / LAND) are
 * direct primitive children of the `Transit` compound — NOT wrapped in a
 * `Circuit` / `CircuitAfterGoAround` / `TouchAndGo` subtree the way
 * `CircuitTraining` or `Arrive` shapes are. The existing
 * `isReactiveGoAroundEligible` guard (in `:pilot/observe/PilotEvent.kt`) is
 * scoped to circuit-like compounds — it returns `false` for Transit-arrival
 * because `mission.root.activeCompound()` is `null` (the active primitive is
 * a direct child of `Transit`, not a nested `Circuit`).
 *
 * **R18 dispatch path** (round-4 Major 1): the existing
 * [applyCrosswindGoAround] / [applyTailwindGoAround] appliers carry a Transit
 * dispatch fork that consults this guard. When the guard holds, the apply
 * dispatches to the `replaceFromActivePrimitive(listOf(goAroundTask(),
 * circuitTask(), groundArrivalTask()))` (R22) path instead of the circuit-
 * only `replaceChild { isCircuitLike }` rewrite. NO new event leaves; NO new
 * pilotDecide branches; NO new applier function — single dispatch fork in
 * the existing appliers.
 *
 * **Recognition+apply pipeline agreement** (round-12 Major 1): BOTH sides
 * (`deriveCrosswindEvent` + `deriveTailwindEvent` in PilotEvent.kt AND the
 * appliers here) consult the same disjunctive eligibility
 * `isReactiveGoAroundEligible(mission) || isTransitArrivalReactiveGoAroundEligible(aircraft, mission)`.
 * Recognition firing without apply matching (or vice versa) is the canonical
 * failure mode — pinned by the memory entry
 * `bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11`.
 *
 * **Predicate parts** (round-11 Major 1 — data-honest with available fields;
 * codex round-1 fix adds (1a) root-name gate):
 *  1. `mission.goal is HighLevelGoal.Transit` — mission-shape carries the
 *     Transit-arrival arrival-pattern continuation. (`HighLevelGoal.Arrival`
 *     missions also have arrival primitives, but they wrap them in a
 *     `Circuit` subtree via `planMission` — the existing
 *     `isReactiveGoAroundEligible` covers that path.)
 *  1a. `mission.root.name is TaskName.Transit` — root compound name pin.
 *     The goal/root invariant is type-system-permitted but planner-broken
 *     when violated; a well-typed `PilotMission(goal = Transit,
 *     root = CompoundTask(TaskName.Arrive, ...))` is malformed but
 *     would otherwise pass (1). This part explicitly refuses that shape
 *     so the apply doesn't run a Transit-shape rewrite on a non-Transit
 *     tree. NOT a structural redundancy — the goal type vs the
 *     planner-produced tree-name carry independent invariants. Codex
 *     round-1 review caught this as a Major / 100% confidence finding.
 *  2. **Active arrival primitive** in the wind-reactive eligible step set
 *     (`FLY_FINAL` / `REPORT_FINAL` / `AWAIT_LANDING_CLEARANCE` / `LAND`)
 *     AND that primitive is a direct child of the `Transit` compound (not
 *     inside a nested `Circuit` subtree). The flat-shape check is
 *     `mission.root.activeCompound() == null` — i.e. the active primitive
 *     bubbles up to the root level rather than being inside a nested
 *     compound. Pre-fn-28.6 Transit-arrival has no inner compounds for the
 *     arrival primitives; post-fn-28.6 Transit-arrival GA, the suffix
 *     replacement puts `GoAround` / `Circuit` compounds in, and
 *     `activeCompound()` returns those — the guard no longer matches, so
 *     recognition does NOT re-fire (no-refire invariant).
 *  3. `mission.activeRunway` is set (the filed plan's `destinationRunway`
 *     resolution per the Transit-arrival arrival-runway pairing).
 *  4. `aircraft.phase == PilotPhase.Final` — the pilot is on the approach
 *     descent. NO geometric "at destination aerodrome" check — Transit +
 *     active-arrival-primitive + Final-phase is the v1 proxy. A future
 *     ARP-circular gate would be a finer geographic check; v1 fails closed
 *     to the mission-shape contract.
 *
 * **Home** (round-10 Minor 2): this file (`Pilot.kt`), not
 * `:pilot/observe/PilotEvent.kt`. The guard is consumed by the apply path
 * (`applyCrosswindGoAround` / `applyTailwindGoAround`), so its home is
 * adjacent to the appliers. PilotEvent.kt imports it for the recognition-
 * site disjunctive check.
 *
 * **Doctrine**: FAA AFH (FAA-H-8083-3C) Chapter 9 (crosswind/tailwind GAs);
 * ICAO Annex 6 Part II §2.4 (PIC final authority); ICAO Doc 4444 §7.10.2
 * (missed-approach handling); CAP 413 §4.66 (Ed 24 — pilot standalone
 * phraseology).
 */
@Suppress("ReturnCount") // guard-clause early returns enumerate fail-closed modes
internal fun isTransitArrivalReactiveGoAroundEligible(
    aircraft: AircraftState,
    mission: PilotMission,
): Boolean {
    // (1) Mission-shape: Transit goal.
    if (mission.goal !is HighLevelGoal.Transit) return false
    // (1a) Root compound is the Transit task tree (codex round-1 fix):
    // a well-typed `PilotMission(goal = Transit, root = CompoundTask(
    // TaskName.Arrive, ...))` is malformed but type-system-permitted —
    // gate on the root name too so a malformed mission doesn't bypass the
    // intended Transit-arrival shape contract. Pre-fn-28.6 Transit
    // missions from `planMission(Transit)` always have
    // `root.name == TaskName.Transit`; post-suffix-replace shapes keep
    // the outer name (the rewrite only replaces children). NOT a
    // structural redundancy with the Transit-goal check — the two carry
    // independent invariants (the goal type vs the planner-produced
    // tree-name), and a mismatch is a separate bug class worth refusing
    // explicitly.
    if (mission.root.name !is TaskName.Transit) return false
    // (4) Aircraft on final approach.
    if (aircraft.phase !is PilotPhase.Final) return false
    // (3) Active-runway resolved.
    if (mission.activeRunway.getOrNull() == null) return false
    // (2a) Active primitive's step is in the wind-reactive eligible step set
    // (the same final-side step set the recognition derives use). Note: this
    // set is locally enumerated rather than imported from PilotEvent.kt to
    // avoid a circular dependency — PilotEvent.kt imports this function.
    val step = mission.currentTask?.step ?: return false
    val transitArrivalWindReactiveSteps = setOf(
        MissionStep.FLY_FINAL,
        MissionStep.REPORT_FINAL,
        MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,
    )
    if (step !in transitArrivalWindReactiveSteps) return false
    // (2b) Flat-shape check: the active primitive must be a direct child of
    // the Transit compound (no nested Circuit / GoAround wrapper). After a
    // Transit-arrival GA suffix replacement, the active primitive moves
    // inside a nested compound (GoAround → Circuit → ...), so
    // `activeCompound()` returns non-null and this guard returns false —
    // the no-refire invariant (recognition does NOT fire on post-GA
    // mission shapes).
    if (mission.root.activeCompound() != null) return false
    return true
}

/**
 * Pass 16 (D-AUDIT.9 partial closure) — response stage for the
 * pilot's self-initiated go-around. Consumes a typed
 * [xyz.easiersaid.twr.pilot.observe.PilotEvent.DecisionAltitudeWithoutClearance]
 * (recognition lives in `:pilot/observe/PilotEvent.kt`'s
 * `derivePilotEvent`); produces the full go-around effect: mission
 * update (subtree replacement + `resetForGoAround`), `Report(GoingAround)`
 * transmission, and a `Climbing` intent.
 *
 * **Renamed from `checkSelfInitiatedGoAround`** (Pass 16): the trigger
 * checks now live in `derivePilotEvent`; this function applies the
 * already-recognized event.
 *
 * **Doctrine**: ICAO Doc 4444 §7.10.2 — the pilot's `Report(GoingAround)`
 * triggers controller-side missed-approach handling.
 */
@Suppress("UnusedParameter") // event field names available to future leaves; keeps the typed shape explicit.
internal fun applySelfInitiatedGoAround(
    event: xyz.easiersaid.twr.pilot.observe.PilotEvent.DecisionAltitudeWithoutClearance,
    mission: PilotMission,
    aircraft: AircraftState,
    now: SimTime,
): GoAroundResult {
    // Self-initiated go-around: pilot reports going around and re-enters circuit.
    // VFR go-arounds are autonomous — pilot re-enters circuit after GOING_AROUND.
    // IFR uses a separate task that includes FLY_MISSED_APPROACH + AWAITING_ATC_INSTRUCTION.
    val gaTask = if (mission.navigationMode.getOrNull() is NavigationMode.Instrument) ifrGoAroundTask()
        else goAroundTask()
    val newRoot = mission.root.replaceChild(
        predicate = { it is CompoundTask && !it.isComplete &&
            (it.name is TaskName.Circuit || it.name is TaskName.CircuitAfterGoAround) },
        replacement = CompoundTask(TaskName.CircuitAfterGoAround, listOf(
            gaTask,
            circuitTask(),
        )),
    )
    val updatedMission = mission.resetForGoAround(now).copy(root = newRoot)

    return GoAroundResult(
        intent = PilotIntent(
            targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
            phase = PilotPhase.Climbing,
            route = aircraft.route, // keep current route until planner builds new one
            targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM,
        ),
        mission = updatedMission,
        transmissions = listOf(Report(listOf(ReportEvent.GoingAround))),
    )
}

/**
 * fn-14.1 (G3a-react): apply the pilot-reactive crosswind-exceedance
 * GA. Distinct from [applySelfInitiatedGoAround] (DA path) and
 * [applyPlannedGoAround] (trained-GA static tree) — third reactive
 * shape: **reactive Tick A intent** (mirrors trained / ATC-reactive:
 * `route = None`, `phase = Final` retained) **combined with inline
 * subtree replacement** (mirrors self-initiated DA: rewrites the
 * active Circuit/CircuitAfterGoAround/TouchAndGo subtree to
 * `CircuitAfterGoAround`). The mission-tree rewrite IS the hysteresis
 * — after this fires, `currentStep` leaves the crosswind-eligible
 * step set, so subsequent ticks return null even if the wind still
 * exceeds the limit.
 *
 * **Subtree predicate**: [TaskName.isCircuitLike] (defined at
 * `PilotMission.kt:790`) — covers `Circuit`, `CircuitAfterGoAround`,
 * AND `TouchAndGo`. A crosswind exceedance during a Touch-and-Go
 * circuit must rewrite the active T&G subtree the same way it
 * rewrites a regular `Circuit` (precedent: `handleGoAround` at
 * `PilotCognitive.kt:986`).
 *
 * **`now: SimTime` explicit parameter** is consumed by
 * [PilotMission.resetForGoAround] which clears `hasClearance`,
 * `activeConstraints`, `routeOverride`, `altitudeRestrictionM`,
 * `joinLeg`, etc. — every approach-phase mutation invalidated by the
 * GA decision.
 *
 * **Tick A intent** mirrors [applyPlannedGoAround] (the trained-GA
 * Tick A) and [applyAtcInitiatedGoAround] (the ATC-reactive Tick A)
 * — NOT [applySelfInitiatedGoAround]:
 *  - `phase = PilotPhase.Final` retained (NOT `Climbing`). Tick B's
 *    [isCircuitTrainedGoAroundTickB] predicate requires it. If Tick
 *    A set `Climbing`, Tick B's special-case would not fire and the
 *    planner would build a normal pattern instead of the GA path.
 *  - `route = PilotRoute.None` invalidates the kinematic route so the
 *    pilot does not continue toward the threshold of the runway that
 *    is no longer landable.
 *  - `targetAltitudeM = circuitPattern.altitudeAglM` — the climb-out
 *    pattern altitude target.
 *
 * Transmits `Report(GoingAround)` so the controller-side
 * `GA-POST-CLEAR` (or `GA-PRE-CLEAR`) interrupt fires; the controller
 * has no awareness of the trigger source (POH crosswind vs DA-
 * without-clearance vs ATC-issued vs trained) — only that the pilot
 * is going around. CAP 413 §4.66 / ICAO Doc 4444 §12.3.4.18: pilot
 * has standalone phraseology authority (no ATC permission needed).
 * (CAP 413 §4.66 in Ed 24 = §4.67 in Ed 23, renumbered per fn-17.1.)
 *
 * **Do NOT modify [applySelfInitiatedGoAround]** — its DA-trigger
 * `phase = Climbing` / retained-route semantics are unchanged. The
 * pre-fn-14 spec `SelfInitiatedGoAroundResponseSpec` is the regression
 * check.
 *
 * KDoc cross-references the three siblings; reviewer guidance: any
 * future change in one path that wants to be matched in another must
 * be explicit, not inherited via a refactored shared core helper —
 * the over-abstraction was considered and rejected during plan review
 * (the three paths differ in their Tick A intent and reset
 * semantics).
 *
 * **Doctrine**: FAA AFH (FAA-H-8083-3C) Chapter 9 (crosswind common
 * errors); 14 CFR §23.233(a); ICAO Annex 6 Part II §2.4 (PIC final
 * authority); ICAO Doc 4444 §7.10.2 (missed-approach handling).
 */
// fn-28.6 (R18 dispatch fork): function now has an early-return for the
// Transit-arrival dispatch path + the existing circuit-only return. Both are
// guard-clause patterns; ReturnCount suppression mirrors `planRoute`'s
// discipline at the top of this file.
@Suppress("UnusedParameter", "ReturnCount") // event field names available to future leaves; keeps the typed shape explicit.
internal fun applyCrosswindGoAround(
    event: xyz.easiersaid.twr.pilot.observe.PilotEvent.CrosswindLimitExceeded,
    mission: PilotMission,
    aircraft: AircraftState,
    now: SimTime,
): GoAroundResult {
    // fn-28.6 (R18 round-4 Major 1): Transit-arrival dispatch fork. When the
    // mission is a Transit-arrival reactive-GA shape (FLY_FINAL et al. as
    // direct primitives of the Transit compound, Final-phase, active runway),
    // dispatch to the suffix-replace path via
    // `replaceFromActivePrimitive([goAroundTask(), circuitTask(),
    // groundArrivalTask()])` (R22). Pre-rewrite `resetForGoAround(now)` is
    // called BEFORE the rewrite (round-16 Major 2) to clear stale approach
    // state — same discipline as the circuit-only branch below.
    //
    // The recognition site (`deriveCrosswindEvent` in PilotEvent.kt) is
    // widened to a disjunctive eligibility check
    // `isReactiveGoAroundEligible(mission) || isTransitArrivalReactiveGoAroundEligible(aircraft, mission)`
    // so both Transit-arrival and circuit-shape missions can fire the
    // recognition; the apply path's dispatch fork here decides which rewrite
    // mechanism runs.
    if (isTransitArrivalReactiveGoAroundEligible(aircraft, mission)) {
        return applyTransitArrivalReactiveGoAround(mission, aircraft, now)
    }
    val gaTask = if (mission.navigationMode.getOrNull() is NavigationMode.Instrument) ifrGoAroundTask()
        else goAroundTask()
    // Subtree predicate mirrors `handleGoAround` (PilotCognitive.kt:986)
    // and `applySelfInitiatedGoAround` — use `isCircuitLike()` so the
    // rewrite covers `Circuit`, `CircuitAfterGoAround`, AND `TouchAndGo`.
    // There is no `planCircuitAfterGoAround` helper in the codebase;
    // the inline pattern is the precedent.
    val newRoot = mission.root.replaceChild(
        predicate = { it is CompoundTask && !it.isComplete && it.name.isCircuitLike() },
        replacement = CompoundTask(TaskName.CircuitAfterGoAround, listOf(
            gaTask,
            circuitTask(),
        )),
    )
    // resetForGoAround clears hasClearance + every approach-phase
    // mutation. Distinct from applyAtcInitiatedGoAround which does NOT
    // re-call reset (handleGoAround already did it pre-rewrite for the
    // ATC path); the crosswind path's rewrite is the unique apply site,
    // so we own the reset.
    val updatedMission = mission.resetForGoAround(now).copy(root = newRoot)

    return GoAroundResult(
        intent = PilotIntent(
            targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
            // Reactive Tick A intent (mirrors trained / ATC-reactive):
            // phase = Final retained; route = None. Tick B's planRoute
            // Circuit-mode FLY_DEPARTURE + Final + no-route special case
            // (planCircuitTrainedGoAround) builds the GA route on the
            // next tick — load-bearing reuse pinned by
            // PilotCrosswindTickATickBTest.
            phase = PilotPhase.Final,
            route = PilotRoute.None,
            targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM,
        ),
        mission = updatedMission,
        transmissions = listOf(Report(listOf(ReportEvent.GoingAround))),
    )
}

/**
 * fn-28.6 (R13 + R18 + R19 + R22 / round-4 Major 1, Major 2, Critical 2;
 * round-16 Major 2): Transit-arrival reactive-GA apply.
 *
 * Shared by [applyCrosswindGoAround] and [applyTailwindGoAround] — both wind-
 * axis appliers dispatch here when [isTransitArrivalReactiveGoAroundEligible]
 * holds. Single dispatch fork; no new event leaves; no new applier per axis.
 *
 * **Mission delta** (R13 / R22):
 *   `mission.resetForGoAround(now)`            // round-16 Major 2 — clear
 *                                              // approach-side state BEFORE
 *                                              // the rewrite
 *     .copy(root = mission.root.replaceFromActivePrimitive(
 *         listOf(goAroundTask(), circuitTask(), groundArrivalTask()),
 *     ))
 *
 * The suffix-replace uses the R13 primitive [CompoundTask.replaceFromActivePrimitive]
 * with the R22 contract: `[goAroundTask(), circuitTask(), groundArrivalTask()]`
 * — the post-recovery `groundArrivalTask()` preserves the Transit mission's
 * full recovery-landing-and-taxi continuation (the eventual landing after
 * the circuit pattern completes). Pre-fn-28.6 Transit-arrival has its
 * arrival primitives as direct children of the `Transit` compound; the
 * suffix-replace drops the active-and-onward arrival primitives, replacing
 * them with the GA + circuit + ground-arrival continuation.
 *
 * **resetForGoAround placement** (round-16 Major 2): called BEFORE the
 * rewrite so the cleared mission state is the receiver of `.copy(root = ...)`.
 * Mirrors `handleGoAround`'s sequencing (`mission.resetForGoAround(now).copy
 * (root = newRoot, ...)`) — every approach-phase mutation (`hasClearance`,
 * `activeConstraints`, `routeOverride`, `altitudeRestrictionM`, `joinLeg`,
 * `lastReportedLeg`, `lastTransmittedStep`) is cleared. The circuit-only
 * branches (`applyCrosswindGoAround` / `applyTailwindGoAround`'s
 * post-fork body, `applySelfInitiatedGoAround`, `handleGoAround`) call
 * `resetForGoAround(now).copy(root = newRoot)` in the same order — the
 * Transit branch matches their discipline.
 *
 * **Tick A intent** (R19 / round-4 Major 2): aligns with the existing
 * reactive-GA Tick A used by [applyCrosswindGoAround] / [applyTailwindGoAround]
 * / [applyPlannedGoAround] / [applyAtcInitiatedGoAround]:
 *  - `targetSpeedMps = aircraft.type.kinematics.climbSpeedMps` — NOT 0
 *    (DA/abort regime, not Transit GA).
 *  - `phase = PilotPhase.Final` retained — Tick B's planRoute special-cases
 *    (Visual-mode + Circuit-mode) detect "Final + no airborne route +
 *    FLY_DEPARTURE" and build the GA climb route.
 *  - `route = PilotRoute.None` — invalidates the kinematic route toward the
 *    threshold of the runway that is no longer landable.
 *  - `targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM` — the
 *    pattern altitude target for the climb-out leg.
 *
 * Transmits `Report(GoingAround)` — same as the circuit-only path. CAP 413
 * §4.66 (Ed 24 — formerly §4.67 in Ed 23) / ICAO Doc 4444 §12.3.4.18: pilot
 * has standalone phraseology authority.
 *
 * **No-refire invariant** (R18): after the suffix replacement, the active
 * primitive moves inside the nested `GoAround` compound, so
 * `mission.root.activeCompound()` returns non-null —
 * [isTransitArrivalReactiveGoAroundEligible]'s flat-shape check fails and
 * recognition does NOT re-fire on subsequent ticks.
 */
private fun applyTransitArrivalReactiveGoAround(
    mission: PilotMission,
    aircraft: AircraftState,
    now: SimTime,
): GoAroundResult {
    // R13 / R22 suffix-replace: `[goAroundTask(), circuitTask(),
    // groundArrivalTask()]`. The first compound's first primitive
    // (GOING_AROUND, REPORTED) becomes the active leaf; cognitive layer
    // emits the GA report. After GOING_AROUND completes, the active leaf
    // advances into the `circuitTask()` subtree (FLY_DEPARTURE first),
    // which the planRoute Transit-cruise discriminator (below) correctly
    // routes via the circuit-shape recovery path.
    val rewrittenRoot = mission.root.replaceFromActivePrimitive(
        listOf(
            goAroundTask(),
            circuitTask(),
            groundArrivalTask(),
        ),
    )
    // round-16 Major 2: resetForGoAround BEFORE rewrite — clear stale approach
    // state (hasClearance, constraints, route overrides, joinLeg,
    // lastReportedLeg, lastTransmittedStep) so the post-GA circuit pattern
    // starts fresh.
    val updatedMission = mission.resetForGoAround(now).copy(root = rewrittenRoot)
    return GoAroundResult(
        intent = PilotIntent(
            targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
            // R19: Transit GA Tick A mirrors circuit-only Tick A —
            // climbSpeedMps + Final + None + patternAltitude. NOT
            // targetSpeedMps = 0 (DA/abort regime).
            phase = PilotPhase.Final,
            route = PilotRoute.None,
            targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM,
        ),
        mission = updatedMission,
        transmissions = listOf(Report(listOf(ReportEvent.GoingAround))),
    )
}

/**
 * fn-15.1 (G3a-react-tailwind): apply the pilot-reactive tailwind-
 * exceedance GA. Sibling of [applyCrosswindGoAround]; **body identical**
 * to the crosswind applier (same Tick A intent shape: `route = None`,
 * `phase = Final` retained; same subtree replacement via
 * `replaceChild { isCircuitLike }`; same `resetForGoAround(now)` call;
 * same `Report(GoingAround)` transmission).
 *
 * **Distinct function, not a shared core helper** (anti-decision pinned
 * in KDoc — mirrors fn-14.1's anti-decision):
 *  - **Trace readability** — stack traces / log breadcrumbs show
 *    `applyTailwindGoAround` distinctly. A single shared applier (e.g.
 *    `applyReactiveWindGoAround(event: PilotEvent, ...)`) would lose the
 *    at-a-glance distinction.
 *  - **Future-proofing for doctrine divergence** — if tailwind doctrine
 *    diverges per-type (e.g. distinct climb-gradient target for jet-class
 *    hard-limit tailwind GAs per FCOM, vs the AFH-advisory regime for
 *    light singles), the body of `applyTailwindGoAround` can move without
 *    touching the crosswind path. Shared body would either silently
 *    change crosswind (rejected) or require parameterising the
 *    divergence (over-abstraction).
 *  - **Per-type doctrinal severity asymmetry** lives in
 *    [xyz.easiersaid.twr.protocol.AircraftType.maxTailwindKnots] KDoc
 *    (C172 = FAA AFH Ch 9 industry-standard advisory; B738 = Boeing
 *    737-800 FCOM Limitations §1 hard limit). The applier is uniform;
 *    the doctrinal anchor cited per-leaf differs.
 *
 * **Subtree predicate**: [TaskName.isCircuitLike] (defined at
 * `PilotMission.kt:790`) — covers `Circuit`, `CircuitAfterGoAround`,
 * AND `TouchAndGo`. A tailwind exceedance during a Touch-and-Go circuit
 * must rewrite the active T&G subtree the same way it rewrites a regular
 * `Circuit` (precedent: `handleGoAround` at `PilotCognitive.kt:986`).
 *
 * **`now: SimTime` explicit parameter** is consumed by
 * [PilotMission.resetForGoAround] which clears `hasClearance`,
 * `activeConstraints`, `routeOverride`, `altitudeRestrictionM`,
 * `joinLeg`, etc. — every approach-phase mutation invalidated by the
 * GA decision.
 *
 * **Tick A intent** mirrors [applyCrosswindGoAround] / [applyPlannedGoAround]
 * (the trained-GA Tick A) / [applyAtcInitiatedGoAround] (the ATC-reactive
 * Tick A) — NOT [applySelfInitiatedGoAround]:
 *  - `phase = PilotPhase.Final` retained (NOT `Climbing`). Tick B's
 *    [isCircuitTrainedGoAroundTickB] predicate requires it.
 *  - `route = PilotRoute.None` invalidates the kinematic route so the
 *    pilot does not continue toward the threshold of the runway that
 *    is no longer landable.
 *  - `targetAltitudeM = circuitPattern.altitudeAglM` — the climb-out
 *    pattern altitude target.
 *
 * Transmits `Report(GoingAround)` so the controller-side
 * `GA-POST-CLEAR` (or `GA-PRE-CLEAR`) interrupt fires; the controller
 * has no awareness of the trigger source (POH/AFH tailwind vs POH
 * crosswind vs DA-without-clearance vs ATC-issued vs trained) — only
 * that the pilot is going around. CAP 413 §4.66 / ICAO Doc 4444
 * §12.3.4.18: pilot has standalone phraseology authority (no ATC
 * permission needed). (CAP 413 §4.66 in Ed 24 = §4.67 in Ed 23,
 * renumbered per fn-17.1.)
 *
 * **Do NOT modify [applyCrosswindGoAround] or [applySelfInitiatedGoAround]**
 * — fn-14.1's crosswind body shape and the DA path's
 * `phase = Climbing` / retained-route semantics are unchanged. The
 * fn-14.1 `PilotCrosswindGoAroundTest` and the pre-fn-14 spec
 * `SelfInitiatedGoAroundResponseSpec` are the regression checks.
 *
 * **Doctrine**: FAA AFH (FAA-H-8083-3C) Chapter 9 (tailwind landings as
 * high-risk operations — modelling anchor for the C172 advisory regime);
 * Boeing 737-800 FCOM Limitations §1 (hard limit anchor for the B738
 * leaf); 14 CFR §23.233(a) (sibling certification framing for the
 * crosswind axis only — no FAR/CS tailwind cert clause is verifiable at
 * v1 scope per fn-15 Decision #8); ICAO Annex 6 Part II §2.4 (PIC final
 * authority); ICAO Doc 4444 §7.10.2 (missed-approach handling); ICAO Doc
 * 4444 §7.11.6 (peer doctrinal anchor — 5 kt tailwind for reduced runway
 * separation minima, scope distinct from POH performance).
 */
// fn-28.6 (R18 dispatch fork): mirrors `applyCrosswindGoAround` — early-return
// for Transit-arrival + existing circuit-only return; ReturnCount suppression
// matches.
@Suppress("UnusedParameter", "ReturnCount") // event field names available to future leaves; keeps the typed shape explicit.
internal fun applyTailwindGoAround(
    event: xyz.easiersaid.twr.pilot.observe.PilotEvent.TailwindLimitExceeded,
    mission: PilotMission,
    aircraft: AircraftState,
    now: SimTime,
): GoAroundResult {
    // fn-28.6 (R18 round-4 Major 1): Transit-arrival dispatch fork — mirrors
    // [applyCrosswindGoAround]'s dispatch. Single-applier dispatch fork; no
    // new event leaves; no new pilotDecide branches. See
    // [applyTransitArrivalReactiveGoAround] KDoc for the suffix-replace
    // contract (R13 + R22) + Tick A intent (R19) + reset placement (round-16
    // Major 2) + no-refire invariant.
    if (isTransitArrivalReactiveGoAroundEligible(aircraft, mission)) {
        return applyTransitArrivalReactiveGoAround(mission, aircraft, now)
    }
    val gaTask = if (mission.navigationMode.getOrNull() is NavigationMode.Instrument) ifrGoAroundTask()
        else goAroundTask()
    // Subtree predicate mirrors `applyCrosswindGoAround` /
    // `handleGoAround` (PilotCognitive.kt:986) — use `isCircuitLike()`
    // so the rewrite covers `Circuit`, `CircuitAfterGoAround`, AND
    // `TouchAndGo`. There is no `planCircuitAfterGoAround` helper in the
    // codebase; the inline pattern is the precedent.
    val newRoot = mission.root.replaceChild(
        predicate = { it is CompoundTask && !it.isComplete && it.name.isCircuitLike() },
        replacement = CompoundTask(TaskName.CircuitAfterGoAround, listOf(
            gaTask,
            circuitTask(),
        )),
    )
    // resetForGoAround clears hasClearance + every approach-phase
    // mutation. Distinct from applyAtcInitiatedGoAround which does NOT
    // re-call reset (handleGoAround already did it pre-rewrite for the
    // ATC path); the tailwind path's rewrite is the unique apply site,
    // so we own the reset — same as the crosswind path.
    val updatedMission = mission.resetForGoAround(now).copy(root = newRoot)

    return GoAroundResult(
        intent = PilotIntent(
            targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
            // Reactive Tick A intent (mirrors crosswind / trained / ATC-
            // reactive): phase = Final retained; route = None. Tick B's
            // planRoute Circuit-mode FLY_DEPARTURE + Final + no-route
            // special case (planCircuitTrainedGoAround) builds the GA
            // route on the next tick — load-bearing reuse pinned by
            // PilotTailwindTickATickBTest.
            phase = PilotPhase.Final,
            route = PilotRoute.None,
            targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM,
        ),
        mission = updatedMission,
        transmissions = listOf(Report(listOf(ReportEvent.GoingAround))),
    )
}

/**
 * fn-28.2 (G3a-react-density-altitude R14 / R20): result of the pilot's
 * reactive DA-decline. Distinct from [GoAroundResult] / [PlannedGoAroundResult]
 * / [AtcGoAroundResult] — DA decline is an apron-side terminal decision,
 * NOT a go-around. The mission tree is rewritten to a NON_COMPLETING
 * `DECLINE_DEPARTURE` primitive via [CompoundTask.replaceFromActivePrimitive],
 * and physics is at-rest (`targetSpeedMps = 0`).
 *
 * **Cognitive-suppression** (R14): the [suppressSameTickCognitive] flag
 * tells `pilotDecide` to zero any same-tick cognitive transmissions
 * (e.g. the per-step `Request(RequestTaxi)` that would otherwise fire on
 * REQUEST_TAXI's first tick). The DA decline preempts the request — the
 * pilot has decided NOT to taxi. Round-13 Major 1: the suppression must
 * apply BEFORE every `PilotOutput` construction site (`PlanRouteOutcome
 * .Plan` AND `Skip` AND any error/fallback branch). The flag is the typed
 * signal; the application loop in `pilotDecide` is the consumer.
 *
 * **No `transmissions` field**: v1 emits no transmission on DA decline.
 * The mission-tree rewrite + at-rest intent + cognitive-suppression are
 * the complete pilot-side response. Future fn-28 work may add a CAP 413
 * courtesy-phrase slot; the result type adds the field at that point.
 */
internal data class DensityAltitudeDeclineResult(
    val intent: PilotIntent,
    val mission: PilotMission,
    val suppressSameTickCognitive: Boolean = true,
)

/**
 * fn-28.2 (G3a-react-density-altitude R13 + R14 + R20): apply the pilot's
 * reactive DA-decline. Recognition fires from
 * `derivePilotEvent`'s `deriveDensityAltitudeEvent` branch; this function
 * applies the already-recognised event.
 *
 * **Recognition+apply agreement** (R16): the function calls
 * [isDensityAltitudeDeclineEligible] internally — the same guard the
 * derivation site uses. If for any reason the apply is invoked with a
 * mission no longer in the eligible shape (cognitive layer advanced past
 * REQUEST_TAXI between derive and apply, etc.), the apply fails closed
 * (returns the input mission unchanged + an at-rest intent). This is the
 * "recognition+apply pipelines need mission-shape agreement" pattern
 * pinned in the memory entry
 * `bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11`.
 *
 * **Mission delta** (R13 sole rewrite primitive):
 * `mission.root.replaceFromActivePrimitive(listOf(
 *     PrimitiveTask(MissionStep.DECLINE_DEPARTURE, CompletionMode.NON_COMPLETING)
 * ))` — the suffix from the active primitive (REQUEST_TAXI or
 * TAXI_TO_HOLDING) is replaced with the terminal NON_COMPLETING
 * DECLINE_DEPARTURE primitive. No `resetForGoAround` — DA decline is not
 * a go-around; phase-local state (joinLeg, altitudeRestrictionM) is
 * irrelevant on the apron.
 *
 * **Tick A intent**:
 *  - `targetSpeedMps = 0` — at-rest on the apron. The kinematic layer
 *    must not advance taxiing speed.
 *  - `phase = AtStand` — the pilot is at-rest; the phase reflects the
 *    apron-static reality. (If the aircraft was mid-taxi when DA decline
 *    fired, the pilot decides to stop — `aircraft.phase` at the moment
 *    of decline could be `Taxiing`; the intent overrides to `AtStand` to
 *    signal stopped. Future fn-28 work may add a `Stopped` phase or
 *    refine the semantic; v1 reuses `AtStand`.)
 *  - `route = PilotRoute.None` — no airborne / ground route.
 *  - `targetAltitudeM = 0.0` — surface elevation; no climb intent.
 *
 * **Cognitive-suppression** (R14 / round-3 fix): returns
 * `suppressSameTickCognitive = true`. `pilotDecide` zeroes any same-tick
 * cognitive transmissions when the flag is set — preventing the
 * REQUEST_TAXI's per-step transmission from firing on the tick the
 * mission tree is being rewritten to DECLINE_DEPARTURE.
 *
 * **Doctrine**: FAA AC 61-107B §3-1; ICAO Annex 6 Part II §2.4 (PIC
 * authority); FAA AFH Ch 11 (high-DA decline as a pilot decision).
 */
@Suppress("UnusedParameter") // event field names available to future leaves; keeps the typed shape explicit.
internal fun applyDensityAltitudeDecline(
    event: xyz.easiersaid.twr.pilot.observe.PilotEvent.DensityAltitudeDecline,
    mission: PilotMission,
    aircraft: AircraftState,
): DensityAltitudeDeclineResult {
    // Recognition+apply agreement: guard against mission shape mismatch.
    // Fails closed to an at-rest intent + unchanged mission; the event
    // is from `derivePilotEvent` which already gated on the same predicate,
    // so this is defensive against future regressions where derive and
    // apply diverge.
    if (!isDensityAltitudeDeclineEligible(mission)) {
        return DensityAltitudeDeclineResult(
            intent = PilotIntent(
                targetSpeedMps = 0.0,
                phase = aircraft.phase,
                route = PilotRoute.None,
                targetAltitudeM = aircraft.altitudeM,
            ),
            mission = mission,
            suppressSameTickCognitive = true,
        )
    }

    val rewrittenRoot = mission.root.replaceFromActivePrimitive(
        listOf(
            PrimitiveTask(MissionStep.DECLINE_DEPARTURE, CompletionMode.NON_COMPLETING),
        ),
    )

    return DensityAltitudeDeclineResult(
        intent = PilotIntent(
            targetSpeedMps = 0.0,
            phase = PilotPhase.AtStand,
            route = PilotRoute.None,
            targetAltitudeM = 0.0,
        ),
        mission = mission.copy(root = rewrittenRoot),
        suppressSameTickCognitive = true,
    )
}

/**
 * fn-28.9 (G0 abort-takeoff R14 + R20): result of the pilot's reactive
 * abort-takeoff. Distinct from [GoAroundResult] / [PlannedGoAroundResult]
 * / [AtcGoAroundResult] / [DensityAltitudeDeclineResult] — abort is a
 * runway-side terminal decision (engine failed pre-rotation; aircraft
 * stays on the runway), NOT a go-around. The mission tree is rewritten
 * to a NON_COMPLETING `ABORTED` primitive via
 * [CompoundTask.replaceFromActivePrimitive], and physics is at-rest
 * (`targetSpeedMps = 0`).
 *
 * **Cognitive-suppression** (R14): the [suppressSameTickCognitive] flag
 * tells `pilotDecide` to zero any same-tick cognitive transmissions
 * (e.g. the per-step `Report(LinedUp)`-style emission that would
 * otherwise fire if the mission was at `AWAIT_TAKEOFF_CLEARANCE` /
 * `FLY_DEPARTURE`). The abort preempts whatever the cognitive layer was
 * about to transmit — the pilot has decided NOT to continue the takeoff.
 * Mirrors fn-28.2's DA-decline R14 contract; round-15 Major 2 contract
 * requires the suppression filter to apply BEFORE every `PilotOutput`
 * construction site, covered structurally by the shared
 * [applyCognitiveSuppression] helper.
 *
 * **No `transmissions` field**: v1 emits no transmission on abort. The
 * mission-tree rewrite + at-rest intent + cognitive-suppression are the
 * complete pilot-side response. Future fn-28 work may add an emergency
 * declaration (`Mayday` / `PanPan` per CAP 413 §8 / ICAO Doc 4444 §15);
 * the result type adds the field at that point.
 */
internal data class AbortTakeoffResult(
    val intent: PilotIntent,
    val mission: PilotMission,
    val suppressSameTickCognitive: Boolean = true,
)

/**
 * fn-28.9 (G0 abort-takeoff R13 + R14 + R20): apply the pilot's reactive
 * abort-takeoff. Recognition fires from
 * `derivePilotEvent`'s `deriveAbortTakeoffEvent` branch; this function
 * applies the already-recognised event.
 *
 * **Recognition+apply agreement** (R16): the function calls
 * [isAbortTakeoffEligible] internally — the same guard the derivation
 * site uses. If for any reason the apply is invoked with a mission no
 * longer in the eligible shape (e.g. cognitive layer advanced past
 * `FLY_DEPARTURE` between derive and apply somehow), the apply fails
 * closed (returns the input mission unchanged + an at-rest intent).
 * This is the "recognition+apply pipelines need mission-shape agreement"
 * pattern pinned in the memory entry
 * `bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11`,
 * mirroring fn-28.2's [applyDensityAltitudeDecline].
 *
 * **Mission delta** (R13 sole rewrite primitive):
 * `mission.root.replaceFromActivePrimitive(listOf(
 *     PrimitiveTask(MissionStep.ABORTED, CompletionMode.NON_COMPLETING)
 * ))` — the suffix from the active primitive (`AWAIT_TAKEOFF_CLEARANCE`
 * or `FLY_DEPARTURE`) is replaced with the terminal NON_COMPLETING
 * ABORTED primitive. No `resetForGoAround` — abort is not a go-around;
 * the aircraft never gets airborne so circuit / approach-phase mutations
 * (joinLeg, altitudeRestrictionM) were never set.
 *
 * **Tick A intent**:
 *  - `targetSpeedMps = 0.0` — at-rest on the runway. The kinematic
 *    layer's engine-off clamp (`advanceKinematics` per R12, in fn-28.8)
 *    already bounds the working speed by `min(targetSpeedMps,
 *    currentSpeedMps)` when `engineRunning == false`; the pilot intent
 *    pins the floor at 0, so the aircraft decelerates each PhysicsTick
 *    until it comes to rest. Combined with the clamp, the same-tick
 *    visible behaviour is "instant stop" (no integrator advancement
 *    past `min(0, currentSpeed)` once the apply runs).
 *  - `phase = aircraft.phase` — preserve the current phase (TakeoffRoll
 *    in the eligible scenarios). The phase will transition naturally as
 *    physics decelerates. v1 doesn't introduce a "Stopped" phase; the
 *    mission tree's NON_COMPLETING ABORTED is the load-bearing signal,
 *    not the phase.
 *  - `route = PilotRoute.None` — no airborne / ground route. The
 *    aircraft stays on the runway.
 *  - `targetAltitudeM = 0.0` — surface elevation; no climb intent.
 *
 * **Cognitive-suppression** (R14 / round-3 fix carried): returns
 * `suppressSameTickCognitive = true`. `pilotDecide` zeroes any same-tick
 * cognitive transmissions when the flag is set — preventing the per-step
 * cognitive emission from firing on the tick the mission tree is being
 * rewritten to ABORTED. Round-15 Major 2: the cognitive-suppression
 * filter applies BEFORE every PilotOutput construction site (both
 * `PlanRouteOutcome.Plan` and `PlanRouteOutcome.Skip` branches) — covered
 * structurally via the shared [applyCognitiveSuppression] helper which
 * `pilotDecide` already calls.
 *
 * **Doctrine**: FAA AIM §5-2 (rejected-takeoff decision); POH §3.3
 * (engine-failure-on-takeoff); ICAO Annex 6 Part II §2.4 (PIC final
 * authority).
 */
@Suppress("UnusedParameter") // event field names available to future leaves; keeps the typed shape explicit.
internal fun applyAbortTakeoff(
    event: xyz.easiersaid.twr.pilot.observe.PilotEvent.AbortTakeoff,
    mission: PilotMission,
    aircraft: AircraftState,
): AbortTakeoffResult {
    // Recognition+apply agreement (R16). Fails closed to an at-rest
    // intent + unchanged mission. The event is from `derivePilotEvent`
    // which already gated on the same predicate, so this is defensive
    // against future regressions where derive and apply diverge.
    if (!isAbortTakeoffEligible(mission)) {
        return AbortTakeoffResult(
            intent = PilotIntent(
                targetSpeedMps = 0.0,
                phase = aircraft.phase,
                route = PilotRoute.None,
                targetAltitudeM = aircraft.altitudeM,
            ),
            mission = mission,
            suppressSameTickCognitive = true,
        )
    }

    val rewrittenRoot = mission.root.replaceFromActivePrimitive(
        listOf(
            PrimitiveTask(MissionStep.ABORTED, CompletionMode.NON_COMPLETING),
        ),
    )

    return AbortTakeoffResult(
        intent = PilotIntent(
            targetSpeedMps = 0.0,
            phase = aircraft.phase,
            route = PilotRoute.None,
            targetAltitudeM = 0.0,
        ),
        mission = mission.copy(root = rewrittenRoot),
        suppressSameTickCognitive = true,
    )
}

/**
 * Result of a planned (trained) go-around — fn-11.1's response stage for
 * the static mission-tree fork. Sibling to [GoAroundResult] (the reactive
 * flow's response). Both produce intent + mission deltas; trained-GA omits
 * the `transmissions` field because the cognitive layer's `stepTransmission`
 * already emits `Report(GoingAround)` for the GOING_AROUND step (the trained
 * tree's compile-time arrangement makes that step the active leaf on Tick A).
 */
internal data class PlannedGoAroundResult(
    val intent: PilotIntent,
    val mission: PilotMission,
)

/**
 * fn-11.1 (G3a-trained Tick A): apply the planned-GA response when the
 * cognitive layer has just advanced past `FLY_FINAL_TO_SHORT_FINAL`.
 *
 * Distinct from [applySelfInitiatedGoAround]: the trained-GA path is plan-
 * driven from the [HighLevelGoal.CircuitTraining.outcomes] list compiled at
 * `createMission` time. No subtree replacement is needed — the GA primitive
 * is already the active leaf within [plannedGoAroundCircuitTask]. This
 * helper is responsible only for the **route invalidation** + **phase-
 * local state reset** that the static tree cannot encode.
 *
 * Tick A's intent emits:
 *  - `phase = Final` (NOT `Climbing`). Tick B's Circuit-mode `planRoute`
 *    special-case predicates on `aircraft.phase is Final && route !is
 *    Airborne`. If Tick A set `Climbing`, Tick B's `aircraft.phase` would
 *    be `Climbing` and the special-case would not fire — the planner
 *    would build a normal circuit route instead of the GA path.
 *  - `route = PilotRoute.None`. Without this, the kinematic engine would
 *    keep flying the old final route toward the threshold.
 *  - `targetAltitudeM = patternAlt`. The pilot's intended climb-out
 *    altitude (matched in Tick B's GA-route intent for stability).
 *
 * Mission delta:
 *  - `resetForGoAround(now)` — clears `hasClearance`, `activeConstraints`,
 *    `routeOverride`, `altitudeRestrictionM`, `joinLeg`, etc. Preserves
 *    structural fields (goal, root, navigationMode, activeRunway) per the
 *    function's named-field convention.
 *  - Static mission root unchanged. The trained tree was compiled with the
 *    GA at the right place; no subtree replacement is appropriate.
 *
 * Doctrine: CAP 413 §4.65/§4.66 — pilot-initiated go-around announcement
 * at the decision-altitude gate, climb runway-heading, re-enter the
 * normal traffic circuit. (Ed 24 numbering — formerly §4.66/§4.67 in
 * Ed 23, renumbered per fn-17.1.)
 */
internal fun applyPlannedGoAround(
    mission: PilotMission,
    aircraft: AircraftState,
    now: SimTime,
): PlannedGoAroundResult {
    val updatedMission = mission.resetForGoAround(now)
    return PlannedGoAroundResult(
        intent = PilotIntent(
            targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
            phase = PilotPhase.Final,
            route = PilotRoute.None,
            targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM,
        ),
        mission = updatedMission,
    )
}

/**
 * fn-12.2 (G3a-obstruction Tick A) result. Sibling to [PlannedGoAroundResult]
 * — both produce intent + mission deltas with no transmission slot. The
 * cognitive layer's `stepTransmission` already emits the appropriate radio
 * for the GOING_AROUND step (mission-tree rewrite by `processInstruction(GoAround)`
 * makes that step the active leaf on Tick A); the apply function's job is
 * the route-invalidation intent + flag-clear.
 */
internal data class AtcGoAroundResult(
    val intent: PilotIntent,
    val mission: PilotMission,
)

/**
 * fn-12.2 (G3a-obstruction Tick A): apply the ATC-issued reactive
 * go-around response when `pilotDecide`'s recognition arm has confirmed
 * the discriminator (flag set + on-final eligible step + Circuit-mode
 * effective + phase=Final).
 *
 * **Intent-only — does NOT call `mission.resetForGoAround(now)`.**
 * `handleGoAround` (in `pilotCognitiveDecide`'s `processInstruction(GoAround)`
 * path) already called `resetForGoAround` on this mission. Calling it
 * again would either be idempotent (safe) or wipe the flag set by
 * `handleGoAround` (unsafe — see [PilotMission.pendingAtcGoAroundFrom]
 * KDoc). Avoiding the second call is the conservative choice.
 *
 * Tick A's intent mirrors [applyPlannedGoAround] (the trained-GA Tick A):
 *  - `phase = PilotPhase.Final` retained — Tick B's
 *    [isCircuitTrainedGoAroundTickB] predicate requires it.
 *  - `route = PilotRoute.None` invalidates the kinematic route so the
 *    pilot does not continue toward the (now-obstructed) threshold.
 *  - `targetAltitudeM = patternAlt` — climb-out altitude target.
 *
 * **Mission delta**: clears the flag (`pendingAtcGoAroundFrom = None`).
 * No other field is touched — `handleGoAround`'s prior
 * `resetForGoAround + .copy(root = ...)` already established the
 * post-rewrite mission state.
 *
 * Doctrine: ICAO Doc 4444 §7.4.1.4.1(c), CAP 413 §4.64 — pilot complies
 * with ATC go-around instruction. (Ed 24 numbering — formerly §4.65 in
 * Ed 23, renumbered per fn-17.1.)
 */
internal fun applyAtcInitiatedGoAround(
    mission: PilotMission,
    aircraft: AircraftState,
): AtcGoAroundResult {
    val updatedMission = mission.copy(pendingAtcGoAroundFrom = None)
    return AtcGoAroundResult(
        intent = PilotIntent(
            targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
            phase = PilotPhase.Final,
            route = PilotRoute.None,
            targetAltitudeM = aircraft.type.circuitPattern.altitudeAglM,
        ),
        mission = updatedMission,
    )
}

/**
 * fn-12.2 (G3a-obstruction): recognize and dispatch the ATC-issued reactive
 * GA path. Reads the post-cognitive `pendingAtcGoAroundFrom` flag and
 * applies the **two-layer flag-clear defense**:
 *  1. If the flag is [None], return `null` — nothing to do.
 *  2. If the flag is [Some] and the discriminator passes (flag value in
 *     the on-final eligible set, effective navigation mode is Circuit,
 *     `aircraft.phase is Final`), construct
 *     [xyz.easiersaid.twr.pilot.observe.PilotEvent.AtcGoAroundOnFinal] (the
 *     typed event leaf for trace coherence) and fire
 *     [applyAtcInitiatedGoAround] which clears the flag and emits the Tick
 *     A intent.
 *  3. If the flag is [Some] but the discriminator fails, return an
 *     [RecognizedAtcGoAround] whose `intent` is `null` (caller falls
 *     through to normal route-planning) but whose `mission` has the flag
 *     CLEARED anyway. Without this defensive clear, a stale flag could
 *     fire the apply on a later cycle when the aircraft happens to be in
 *     phase=Final via some other path.
 *
 * **Effective Circuit-mode discriminator**: `mission.navigationMode` is
 * often [None] for normal circuit-training missions because
 * [planRoute] derives `Circuit` locally from `mission.activeRunway + world`
 * via [deriveNavigationMode] without writing back. Gating on the stored
 * field alone would silently fail. The recognition uses the same
 * derivation `planRoute` uses (Option (a) from the task spec); the helper
 * signature takes `world` because [deriveNavigationMode] needs it.
 *
 * Returns:
 *  - `null` when the flag is [None] (no signal — the common case).
 *  - [RecognizedAtcGoAround] with non-null `intent` when the predicate
 *    fires (Tick A apply).
 *  - [RecognizedAtcGoAround] with `null` `intent` when the flag is [Some]
 *    but the discriminator fails — `mission` carries the cleared flag.
 *    Caller treats this as "use the cleared mission, no intent override."
 */
internal fun recognizeAtcInitiatedGoAround(
    aircraft: AircraftState,
    mission: PilotMission,
    world: PilotAviationWorld,
): RecognizedAtcGoAround? {
    val flag = mission.pendingAtcGoAroundFrom.getOrNull() ?: return null
    val flagValid = flag in setOf(
        MissionStep.FLY_FINAL,
        MissionStep.REPORT_FINAL,
        MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,
    )
    val phaseFinal = aircraft.phase is PilotPhase.Final
    val effectiveCircuit = isEffectiveCircuitMode(mission, world)
    return if (flagValid && phaseFinal && effectiveCircuit) {
        // Construct the typed event leaf at the recognition site (NOT in
        // `derivePilotEvent`) — this is the post-cognitive flag-driven
        // axis of `PilotEvent`, parallel to but distinct from
        // `derivePilotEvent`'s pure-derivation axis. Returned to the
        // caller as a typed witness of recognition; future trace
        // consumers (peer to fn-11.1's `DecisionAltitudeWithoutClearance`)
        // will read it from `RecognizedAtcGoAround.event`.
        val event = xyz.easiersaid.twr.pilot.observe.PilotEvent.AtcGoAroundOnFinal(
            aircraft = aircraft.id,
            originalStep = flag,
        )
        val applied = applyAtcInitiatedGoAround(mission, aircraft)
        RecognizedAtcGoAround(intent = applied.intent, mission = applied.mission, event = event)
    } else {
        // Defensive flag-clear on discriminator-fail. No event constructed
        // — the recognition predicate explicitly rejected the trigger; an
        // event leaf would mislead future trace consumers.
        RecognizedAtcGoAround(
            intent = null,
            mission = mission.copy(pendingAtcGoAroundFrom = None),
            event = null,
        )
    }
}

/**
 * fn-12.2: `recognizeAtcInitiatedGoAround` return shape.
 *
 *  - [intent] non-null: Tick A apply fired; caller uses this intent.
 *  - [intent] null: discriminator failed, but the flag was Some and has
 *    been defensively cleared in [mission]; caller uses the cleared
 *    mission (no intent override).
 *  - [event] non-null when (and only when) [intent] is non-null — the
 *    typed witness of recognition for trace consumers. Future telemetry
 *    will read this without needing to re-derive the trigger.
 */
internal data class RecognizedAtcGoAround(
    val intent: PilotIntent?,
    val mission: PilotMission,
    val event: xyz.easiersaid.twr.pilot.observe.PilotEvent.AtcGoAroundOnFinal?,
)

/**
 * fn-12.2: effective Circuit-mode discriminator for the ATC-issued reactive
 * GA recognition predicate.
 *
 * `mission.navigationMode` is [None] for normal circuit-training missions
 * because `createMission` defaults the field to [None] and `planRoute`
 * derives `NavigationMode.Circuit` locally from
 * `mission.activeRunway + world` via [deriveNavigationMode] without writing
 * back. Gating on `mission.navigationMode.getOrNull()` alone would
 * silently fail for the normal LOWG circuit-training case.
 *
 * This helper reuses the same derivation path `planRoute` uses (option (a)
 * from the task spec): try the stored `mission.navigationMode` first; if
 * [None], call [deriveNavigationMode] with the mission's active runway
 * and the world. Returns `false` if no runway is set (recognition fails
 * conservatively) or if derivation fails (e.g. `CircuitNotFound`).
 *
 * Signature includes `world` because [deriveNavigationMode] needs it.
 */
internal fun isEffectiveCircuitMode(mission: PilotMission, world: PilotAviationWorld): Boolean {
    mission.navigationMode.getOrNull()?.let { return it is NavigationMode.Circuit }
    val rwy = mission.activeRunway.getOrNull()?.runway ?: return false
    return deriveNavigationMode(mission.goal, rwy, world)
        .fold({ false }, { it is NavigationMode.Circuit })
}

/**
 * fn-28.2 (R14 / round-13 Major 1 / round-2 codex fold-in): cognitive-
 * suppression focused seam. When [suppressSameTickCognitive] is true,
 * returns an empty transmission list; otherwise returns the original
 * cognitive transmissions verbatim.
 *
 * **Why a named helper, not inline at the call site**: codex round-2
 * fix. The round-13 Major 1 contract requires "the filter must apply
 * BEFORE every `PilotOutput` construction site" — both pilotDecide's
 * `PlanRouteOutcome.Plan` and `PlanRouteOutcome.Skip` branches call
 * this helper once (via `effectiveCognitiveTransmissions`), so the
 * code-share is structural. Unit tests on this helper directly cover
 * the suppression logic without requiring an end-to-end fixture that
 * exercises Plan-path while DA-decline fires (which is structurally
 * impossible by R16 design — DA-decline gates pre-taxi, planRoute Plan
 * requires airborne / Transit-cruise).
 *
 * **Pure**: no side effects, no time dependency. Same inputs → same
 * output, every call.
 *
 * **`internal` visibility**: enables direct unit-testing from
 * `commonTest` without exposing the helper to non-test consumers of
 * the `:pilot` module's public API.
 */
internal fun applyCognitiveSuppression(
    transmissions: List<PilotTransmission>,
    suppressSameTickCognitive: Boolean,
): List<PilotTransmission> =
    if (suppressSameTickCognitive) emptyList() else transmissions

/**
 * Apply cognitive overrides to kinematic intent.
 *
 * The cognitive layer IS the pilot. When the mission state conflicts with
 * what the kinematics want to do, the cognitive decision wins.
 */
internal fun applyCognitiveOverrides(
    kinematic: PilotIntent,
    mission: PilotMission,
): PilotIntent {
    if (mission.currentTask?.step == null) return kinematic

    // Go-around override moved to checkSelfInitiatedGoAround (updates mission + transmits).

    // Override: ExtendingDownwind constraint → maintain downwind heading, don't turn base.
    if (ActiveConstraint.ExtendingDownwind in mission.activeConstraints) {
        if (kinematic.phase is PilotPhase.Base) {
            // Don't turn base — stay on downwind.
            return kinematic.copy(phase = PilotPhase.Downwind)
        }
    }

    return kinematic
}
