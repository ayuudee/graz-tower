package xyz.easiersaid.twr.pilot

import arrow.core.Either
import arrow.core.None
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
private sealed interface PlanRouteOutcome {
    data object Skip : PlanRouteOutcome
    data class Plan(val intent: PilotIntent) : PlanRouteOutcome
    data class Failed(val error: RoutingError) : PlanRouteOutcome
}

/** Decision altitude threshold — below this without clearance → go around. */
private const val DECISION_ALTITUDE_M = 100.0

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

    // Cognitive layer: advance mission, generate transmissions.
    val cognitive = pilotCognitiveDecide(aircraft, mission, input.worldIndex, input.now)

    // Self-initiated go-around: if the pilot is at decision altitude without
    // clearance, trigger a full go-around (mission update + transmission + climb).
    // This must run BEFORE route planning and kinematic overrides.
    val goAround = checkSelfInitiatedGoAround(cognitive.updatedMission, aircraft, input.now)
    val effectiveMission = goAround?.mission ?: cognitive.updatedMission
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
            updatedMission = effectiveMission,
        ).right()
        is PlanRouteOutcome.Skip -> PilotOutput(
            intent = goAround?.intent
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
private fun planRoute(
    mission: PilotMission,
    aircraft: AircraftState,
    kinematicRoute: PilotRoute,
    world: AviationWorld,
    worldIndex: WorldIndex,
): PlanRouteOutcome {
    val step = mission.currentTask?.step ?: return PlanRouteOutcome.Skip
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

    val airborneSteps = setOf(
        // Flying steps
        MissionStep.FLY_DOWNWIND, MissionStep.FLY_BASE, MissionStep.FLY_FINAL,
        MissionStep.FLY_DEPARTURE, MissionStep.FLY_SID, MissionStep.FLY_EN_ROUTE,
        MissionStep.FLY_STAR, MissionStep.FLY_APPROACH, MissionStep.FLY_MISSED_APPROACH,
        // Airborne report/sequencing steps — pilot needs a route while transmitting
        MissionStep.REPORT_DOWNWIND, MissionStep.REPORT_BASE, MissionStep.REPORT_FINAL,
        MissionStep.AWAIT_SEQUENCING, MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,
    )
    if (step !in airborneSteps) return PlanRouteOutcome.Skip

    val taskName = mission.root.activeCompound()?.name ?: return PlanRouteOutcome.Skip

    return when (mode) {
        // Visual mode: continuous re-derivation from mission state + kinematic position.
        is NavigationMode.Visual -> planVisualRoute(mode, taskName, mission, aircraft, kinematicRoute, w, worldIndex)

        // Non-Visual: one-shot bootstrap. Route is built once and not rebuilt mid-flight.
        // IFR continuous planning is deferred until IFR missions land.
        else -> {
            if (aircraft.route !is PilotRoute.None) return PlanRouteOutcome.Skip
            buildAirborneRoute(mode, taskName, w).fold(
                ifLeft = { PlanRouteOutcome.Failed(it) },
                ifRight = { route ->
                    PlanRouteOutcome.Plan(PilotIntent(
                        targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) aircraft.type.kinematics.climbSpeedMps
                            else aircraft.type.kinematics.approachSpeedMps,
                        phase = aircraft.phase,
                        route = route,
                        targetAltitudeM = route.targetAltitudeM,
                    ))
                },
            )
        }
    }
}

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
            return buildGoAroundRoute(mode.runway, world).fold(
                ifLeft = { PlanRouteOutcome.Failed(it) },
                ifRight = { gaRoute ->
                    val gaAlt = mission.altitudeRestrictionM.map { minOf(CIRCUIT_ALTITUDE_M, it) }
                        .getOrElse { CIRCUIT_ALTITUDE_M }
                    PlanRouteOutcome.Plan(PilotIntent(
                        targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
                        phase = PilotPhase.Climbing,
                        route = gaRoute.copy(targetAltitudeM = gaAlt),
                        targetAltitudeM = gaAlt,
                    ))
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

    val derivedRoute = buildVisualModeRoute(mode, taskName, world, joinLeg)
        .getOrElse { err -> return PlanRouteOutcome.Failed(err) }

    // Apply ATC altitude restriction (E3): StopClimbAt caps targetAltitudeM so the
    // pilot levels off at the restricted altitude rather than climbing to circuit altitude.
    val targetAlt = mission.altitudeRestrictionM
        .map { minOf(derivedRoute.targetAltitudeM, it) }
        .getOrElse { derivedRoute.targetAltitudeM }

    // ── Bootstrap completion (no prior kinematic route) ────────────────────────
    if (cur == null) {
        return PlanRouteOutcome.Plan(PilotIntent(
            targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) aircraft.type.kinematics.climbSpeedMps
                else aircraft.type.kinematics.approachSpeedMps,
            phase = aircraft.phase,
            route = derivedRoute.copy(targetAltitudeM = targetAlt),
            targetAltitudeM = targetAlt,
        ))
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
        return PlanRouteOutcome.Plan(PilotIntent(
            targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) aircraft.type.kinematics.climbSpeedMps
                else aircraft.type.kinematics.approachSpeedMps,
            phase = aircraft.phase,
            route = cur.copy(targetAltitudeM = targetAlt),
            targetAltitudeM = targetAlt,
        ))
    }

    return PlanRouteOutcome.Plan(PilotIntent(
        targetSpeedMps = if (aircraft.phase is PilotPhase.Climbing) aircraft.type.kinematics.climbSpeedMps
            else aircraft.type.kinematics.approachSpeedMps,
        phase = aircraft.phase,
        route = derivedRoute.copy(targetAltitudeM = targetAlt),
        targetAltitudeM = targetAlt,
    ))
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
    return buildAirborneRoute(mode, taskName, world).fold(
        ifLeft = { PlanRouteOutcome.Failed(it) },
        ifRight = { route ->
            PlanRouteOutcome.Plan(PilotIntent(
                targetSpeedMps = aircraft.type.kinematics.climbSpeedMps,
                phase = PilotPhase.TakeoffRoll,
                route = route,
                targetAltitudeM = CIRCUIT_ALTITUDE_M,
            ))
        },
    )
}

/** Result of a self-initiated go-around check. */
private data class GoAroundResult(
    val intent: PilotIntent,
    val mission: PilotMission,
    val transmissions: List<PilotTransmission>,
)

/**
 * Check if the pilot should self-initiate a go-around (decision altitude
 * without clearance). If so, returns the full go-around effect: mission
 * update (subtree replacement + resetForGoAround), GoingAround transmission,
 * and climbing intent. Returns null if no go-around is needed.
 *
 * This is the pilot's DECISION to go around — distinct from an ATC-instructed
 * go-around (which arrives via processInstruction). Both produce the same
 * mission-level effect (subtree replacement + state reset).
 */
private fun checkSelfInitiatedGoAround(
    mission: PilotMission,
    aircraft: AircraftState,
    now: SimTime,
): GoAroundResult? {
    val currentStep = mission.currentTask?.step ?: return null

    // Only fire on approach steps without clearance, at or below decision altitude.
    val onApproach = currentStep == MissionStep.AWAIT_LANDING_CLEARANCE ||
        currentStep == MissionStep.REPORT_FINAL || currentStep == MissionStep.FLY_FINAL ||
        currentStep == MissionStep.FLY_BASE || currentStep == MissionStep.REPORT_BASE
    if (!onApproach || mission.hasClearance) return null
    if (aircraft.altitudeM !in 0.01..DECISION_ALTITUDE_M) return null
    if (aircraft.phase is PilotPhase.LandingRoll || aircraft.phase is PilotPhase.Vacating) return null
    // Don't re-fire if already going around (step advanced to GOING_AROUND or beyond).
    if (currentStep == MissionStep.GOING_AROUND || currentStep == MissionStep.AWAITING_ATC_INSTRUCTION) return null

    // Full go-around: mission update + transmission + climbing intent.
    // Self-initiated go-around: pilot reports going around and re-enters circuit
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
            targetAltitudeM = CIRCUIT_ALTITUDE_M,
        ),
        mission = updatedMission,
        transmissions = listOf(Report(listOf(ReportEvent.GoingAround))),
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
