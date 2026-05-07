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
            updatedMission = planOutcome.mission,
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
internal fun planRoute(
    mission: PilotMission,
    aircraft: AircraftState,
    kinematicRoute: PilotRoute,
    world: AviationWorld,
    worldIndex: WorldIndex,
): PlanRouteOutcome {
    val step = mission.currentTask?.step ?: return PlanRouteOutcome.Skip

    // G2 Phase C: cross-aerodrome Transit cruise. The cruise route runs from
    // current position to the destination's first published contact REP
    // (resolved from world.aerodromes[destination].aip.publishedVfrProcedures).
    // Fires BEFORE the activeRunway gate below — Transit cruise doesn't depend
    // on a local runway assignment. Gated by `goal is HighLevelGoal.Transit`,
    // so non-Transit goals fall through to the existing logic unchanged.
    if (step == MissionStep.FLY_DEPARTURE && mission.goal is HighLevelGoal.Transit) {
        val destination = mission.goal.destination ?: return PlanRouteOutcome.Skip
        // Resolve once and cache on mission. The cached value is the
        // canonical source on subsequent ticks (write-once per D-G2.4).
        //
        // D-G2.4 tripwire (set-once invariant): the cached REP is valid only
        // for the goal-destination it was resolved for. Today
        // `HighLevelGoal.Transit` is a data class with `val destination`, so
        // a goal-destination change is unrepresentable in current code paths
        // (every "change" produces a new PilotMission). Future fluid-replanning
        // (D-G2.4 real-fix) MUST clear `mission.transitContactRep` whenever
        // the destination changes — see the deferment register. The
        // `mission.copy(transitContactRep = None)` clear-on-change is the
        // contract that future code enforces; this code-site assumes it.
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
