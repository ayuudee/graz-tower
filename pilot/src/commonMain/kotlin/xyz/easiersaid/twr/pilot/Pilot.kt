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
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
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

    // Pass 16 (D-AUDIT.9 partial closure): typed PilotEvent channel.
    // Recognition (event derivation) is pure and lives in
    // `:pilot/observe`; response (mission update + intent override)
    // lives here as `applySelfInitiatedGoAround`.
    //
    // Today the channel has one leaf — `as? Cast` resolves it. When
    // the second leaf lands (D-AUDIT.9.II–V-FOLLOWUP), this shifts to
    // a sealed `when`-fold and `derivePilotEvent` returns `List<PilotEvent>`.
    val pilotEvent = xyz.easiersaid.twr.pilot.observe.derivePilotEvent(aircraft, cognitive.updatedMission)
    val goAround = (pilotEvent as? xyz.easiersaid.twr.pilot.observe.PilotEvent.DecisionAltitudeWithoutClearance)
        ?.let { applySelfInitiatedGoAround(it, cognitive.updatedMission, aircraft, input.now) }

    // fn-11.1 (Tick A): trained-GA fork-point. When the cognitive layer
    // just advanced past `FLY_FINAL_TO_SHORT_FINAL` to `GOING_AROUND`,
    // apply the planned-GA response: clear the kinematic route (so Tick B
    // hits the Circuit-mode go-around route special-case in `planRoute`)
    // and reset phase-local mission state via `resetForGoAround`. This is
    // structurally distinct from `applySelfInitiatedGoAround` (the
    // reactive flow): trigger is the static mission tree, not a sensor
    // event; no subtree replacement is needed (the GA was compiled into
    // the tree at `createMission`). Per `feedback_world_only_test_triggers.md`.
    val plannedGoAround = if (
        goAround == null && preStep == MissionStep.FLY_FINAL_TO_SHORT_FINAL &&
        cognitive.updatedMission.currentTask?.step == MissionStep.GOING_AROUND
    ) {
        applyPlannedGoAround(cognitive.updatedMission, aircraft, input.now)
    } else null

    val effectiveMission = goAround?.mission ?: plannedGoAround?.mission ?: cognitive.updatedMission
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
    return when (planOutcome) {
        is PlanRouteOutcome.Failed -> planOutcome.error.left()
        is PlanRouteOutcome.Plan -> PilotOutput(
            intent = planOutcome.intent,
            transmissions = cognitive.transmissions + goAroundTransmissions,
            updatedMission = planOutcome.mission,
        ).right()
        // fn-11.1: trained-GA Tick A — `plannedGoAround.intent` clears the
        // route (route=PilotRoute.None) and pins phase=Final so Tick B's
        // Circuit-mode special-case fires. Reactive `goAround?.intent`
        // takes precedence (separate code paths per
        // `feedback_world_only_test_triggers.md` / practice-scout
        // anti-pattern #6, but reactive is structurally exclusive to
        // trained-GA via `derivePilotEvent`'s step-set).
        is PlanRouteOutcome.Skip -> PilotOutput(
            intent = goAround?.intent
                ?: plannedGoAround?.intent
                ?: applyCognitiveOverrides(kinematicIntent, effectiveMission),
            transmissions = cognitive.transmissions + goAroundTransmissions,
            updatedMission = effectiveMission,
        ).right()
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
    world: AviationWorld,
    worldIndex: WorldIndex,
): PlanRouteOutcome {
    val step = mission.currentTask?.step ?: return PlanRouteOutcome.Skip

    // G2 Phase C: cross-aerodrome Transit cruise. Fires BEFORE the activeRunway
    // gate below — Transit cruise doesn't depend on a local runway assignment.
    // See [planTransitCruise] for the cache + resolve logic.
    if (step == MissionStep.FLY_DEPARTURE && mission.goal is HighLevelGoal.Transit) {
        return planTransitCruise(mission, mission.goal, aircraft, world)
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
    world: AviationWorld,
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
    world: AviationWorld,
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
    world: AviationWorld,
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
    world: AviationWorld,
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
 * Doctrine: CAP 413 §4.66/§4.67 — pilot-initiated go-around announcement
 * at the decision-altitude gate, climb runway-heading, re-enter the
 * normal traffic circuit.
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
 * Apply cognitive overrides to kinematic intent.
 *
 * The cognitive layer IS the pilot. When the mission state conflicts with
 * what the kinematics want to do, the cognitive decision wins.
 */
private fun applyCognitiveOverrides(
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
