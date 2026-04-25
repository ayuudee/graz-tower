package xyz.easiersaid.twr.sim

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import arrow.core.NonEmptyList
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.CircuitProcedure
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.core.world.InstrumentApproach
import xyz.easiersaid.twr.core.world.Sid
import xyz.easiersaid.twr.core.world.Star
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.ApproachType
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.ClearanceState
import xyz.easiersaid.twr.protocol.FlightPlan
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SidId

// ── Navigation modes ────────────────────────────────────────────────
//
// Named planning modes — each determines how the pilot derives their route.
// Not a fundamental taxonomy; just the set of strategies the sim currently
// models. Adding a mode = adding a variant + handling every TaskName for
// it (compiler-enforced via sealed when).
//
// Known limitations (deferred, not broken):
//   - Visual transit uses the departure route as an approximation. FLY_DEPARTURE stays open
//     until zone-exit detection is implemented — step completion is not on PilotPhase.Climbing
//     (see A6 in .plan).
//   - Step.kt's buildDepartureRoute/buildCircuitDepartureRoute delegate via getOrNull(),
//     discarding RoutingError information. The instruction-effect layer predates Either-based
//     routing; propagating errors through it is a separate refactor.
//
// IFR wiring gaps (route planner is complete, sim wiring is not):
//   - ClearedTo from Uncleaned fails — IFR clearance delivery happens before runway
//     assignment in real ops. The ClearanceState.Uncleaned → EnRouteClearance transition
//     needs a runway, but the first ClearedTo doesn't carry one. Needs a pre-clearance
//     runway assignment step or accepting runway from a prior instruction.
//   - IFR arrival HTN (planMission ifr=true, Arrival) has no arrivalJoinTask() — the
//     pilot starts at FLY_STAR with no initial contact or inbound call.
//   - createMission never passes ifr=true — IFR missions can't be constructed through
//     that path yet. Needs the NavigationMode to drive the ifr flag.
//   - applyFplAmendment in processInstruction silently discards AmendmentError via
//     getOrNull(). Genuine errors (FixNotOnRoute, InvalidTransition) are lost.
//   - StopClimbAt/StopDescentAt don't update clearedLevel — intentional simplification
//     (these are intermediate restrictions, not new cleared levels).

sealed interface NavigationMode {
    /** Visual navigation via VFR routes and visual references. */
    data class Visual(val runway: RunwayId, val destination: AerodromeId?) : NavigationMode

    /** Circuit pattern at the local aerodrome. */
    data class Circuit(val runway: RunwayId, val procedure: CircuitProcedureId) : NavigationMode

    /** Following a cleared instrument route from a filed flight plan. */
    data class Instrument(val fpl: FlightPlan) : NavigationMode

    /** Emergency — direct to nearest suitable or assigned runway. */
    data class Emergency(val targetRunway: RunwayId?) : NavigationMode
}

// ── Circuit lookup ──────────────────────────────────────────────────

/**
 * Discriminated lookup strategy for [findRunwayAndCircuit].
 *
 * [ById] is used from circuit mode ([NavigationMode.Circuit]) — the procedure ID is
 * authoritative and failure is a data error (no silent fallback).
 * [ByRunway] is used from visual mode — picks the first circuit for the runway.
 */
sealed interface CircuitLookup {
    /** Look up circuit by procedure ID. Returns an error if the ID is not found. */
    data class ById(val id: CircuitProcedureId) : CircuitLookup
    /** Look up circuit by runway ID. Returns the first matching circuit. */
    data class ByRunway(val runwayId: RunwayId) : CircuitLookup
}

// ── Routing errors ──────────────────────────────────────────────────

sealed interface RoutingError {
    /** The mode × taskName combination is structurally invalid (e.g., Circuit × Transit). */
    data class InvalidCombination(val mode: NavigationMode, val taskName: TaskName) : RoutingError

    /** Valid combination, but the route-building logic is not yet implemented. */
    data class NotYetImplemented(val mode: NavigationMode, val taskName: TaskName) : RoutingError

    data class RunwayNotFound(val runway: RunwayId) : RoutingError
    data class CircuitNotFound(val runway: RunwayId) : RoutingError
    data class ProcedureNotFound(val id: String) : RoutingError
    data class InsufficientGeometry(val detail: String) : RoutingError
    /**
     * A waypoint referenced by a route is absent from [WorldIndex.positions].
     *
     * This is a world-data integrity defect, not a routing-logic error. The world
     * author must ensure every waypoint used in a route is present in the index.
     */
    data class WaypointNotInIndex(val point: xyz.easiersaid.twr.protocol.PointId) : RoutingError

    /**
     * A cross-aerodrome route refers to a transit waypoint (TMA entry,
     * CTR entry) that doesn't resolve to a [Fix] in the world.
     */
    data class MissingTransitWaypoint(
        val ident: xyz.easiersaid.twr.protocol.FixId,
    ) : RoutingError

    /**
     * The destination aerodrome has no usable runway — no weather-driven
     * runway selection is possible (e.g. wind data missing, or the aerodrome
     * has no runways at all).
     */
    data class NoArrivalRunway(
        val aerodrome: xyz.easiersaid.twr.protocol.AerodromeId,
    ) : RoutingError

    /**
     * No published entry/join sequence is available for the (aerodrome,
     * runway) combination — circuit projection or join-leg authoring
     * incomplete.
     */
    data class NoEntrySequenceForRunway(
        val aerodrome: xyz.easiersaid.twr.protocol.AerodromeId,
        val runway: xyz.easiersaid.twr.protocol.RunwayId,
    ) : RoutingError
}

// ── Route builder ───────────────────────────────────────────────────

/**
 * Build an airborne route for the given navigation mode and active task.
 *
 * Dispatches on [mode] × [taskName] — every combination is an explicit
 * decision. Invalid combinations return [RoutingError.InvalidCombination];
 * not-yet-implemented combinations return [RoutingError.NotYetImplemented].
 *
 * Ground tasks (GroundDeparture, GroundArrival) and structural tasks
 * (CircuitTraining) are always [InvalidCombination] — they don't produce
 * airborne routes.
 */
fun buildAirborneRoute(
    mode: NavigationMode,
    taskName: TaskName,
    world: AviationWorld,
    worldIndex: WorldIndex,
): Either<RoutingError, PilotRoute.Airborne> = when (mode) {
    is NavigationMode.Circuit -> buildCircuitModeRoute(mode, taskName, world, worldIndex)
    is NavigationMode.Visual -> buildVisualModeRoute(mode, taskName, world, worldIndex)
    is NavigationMode.Instrument -> buildInstrumentModeRoute(mode, taskName, world, worldIndex)
    is NavigationMode.Emergency -> buildEmergencyModeRoute(mode, taskName)
}

// ── Circuit mode ────────────────────────────────────────────────────

private fun buildCircuitModeRoute(
    mode: NavigationMode.Circuit,
    taskName: TaskName,
    world: AviationWorld,
    worldIndex: WorldIndex,
): Either<RoutingError, PilotRoute.Airborne> = when (taskName) {
    // Circuit pattern: full loop dep end → upwind → ... → threshold.
    is TaskName.Circuit,
    is TaskName.CircuitAfterGoAround,
    is TaskName.TouchAndGo -> buildCircuitPatternRoute(mode.runway, world, worldIndex, CircuitLookup.ById(mode.procedure))

    // Go-around: published go-around path → rejoin circuit.
    is TaskName.GoAround -> buildGoAroundRoute(mode.runway, world, worldIndex, CircuitLookup.ById(mode.procedure))

    // Ground tasks — no airborne route.
    is TaskName.GroundDeparture,
    is TaskName.GroundArrival -> RoutingError.InvalidCombination(mode, taskName).left()

    // Invalid for circuit mode — circuits don't depart, arrive, or transit.
    is TaskName.Depart,
    is TaskName.Arrive,
    is TaskName.Transit -> RoutingError.InvalidCombination(mode, taskName).left()

    // Structural — never the active compound.
    is TaskName.CircuitTraining -> RoutingError.InvalidCombination(mode, taskName).left()

    // Arrival join is not used in circuit training (pilot is already at the aerodrome).
    is TaskName.ArrivalJoin -> RoutingError.InvalidCombination(mode, taskName).left()

    // Cross-aerodrome transit doesn't compose with Circuit mode — circuit mode
    // is for circuit-training missions at a single aerodrome.
    is TaskName.CrossAerodromeTransit -> RoutingError.InvalidCombination(mode, taskName).left()
}

// ── Visual mode ─────────────────────────────────────────────────────

/**
 * Cross-aerodrome route context: the published TMA/CTR entry waypoints
 * for a [HighLevelGoal.VfrCrossAerodromeTransit] mission. Carried into
 * [buildVisualModeRoute] so the per-phase route builders can produce
 * cross-aerodrome routes (Transit phase: head to TMA entry; ArrivalJoin
 * phase: route from TMA entry through CTR REPs to the circuit join).
 *
 * [joinLeg] is supplied by the goal (not derived); the route builder
 * uses it directly rather than falling back to a silent default.
 */
internal data class CrossAerodromeContext(
    val tmaEntry: xyz.easiersaid.twr.protocol.FixId,
    val ctrEntry: xyz.easiersaid.twr.protocol.FixId,
    val ctrCorridorWaypoints: List<xyz.easiersaid.twr.protocol.FixId>,
    val joinLeg: LegName,
    val enRouteAltitudeM: Double,
)

/**
 * Build an airborne route for Visual navigation mode. Package-private so
 * [planRouteIfNeeded] in [UnifiedPilot] can pass the mission-level [joinLeg]
 * directly for Visual × circuit tasks without threading it through
 * [buildAirborneRoute] (which would pollute the other 3 navigation modes).
 */
internal fun buildVisualModeRoute(
    mode: NavigationMode.Visual,
    taskName: TaskName,
    world: AviationWorld,
    worldIndex: WorldIndex,
    joinLeg: Option<LegName> = None,
    crossAerodrome: Option<CrossAerodromeContext> = None,
): Either<RoutingError, PilotRoute.Airborne> = when (taskName) {
    // Departure climb-out. For a single-aerodrome Departure mission this is
    // the short circuit climb-out ending at crosswind. For a cross-aerodrome
    // mission the *long* transit route is built here — at this phase
    // [mode.runway] is the source-airport runway (the source tower's
    // commitment is still alive), so the route runs `source-rwy dep-end →
    // upwind → crosswind → TMA-entry waypoint`. By the time TaskName.Transit
    // (FLY_EN_ROUTE) becomes active, the source tower has released the
    // aircraft and no controller has a runway-bearing commitment, so
    // [planRoute] returns null and route planning is suspended — the
    // existing kinematic route from this phase carries the aircraft
    // through the FIS segment until the destination tower picks up.
    is TaskName.Depart -> when (crossAerodrome) {
        is None -> buildVisualDepartureRoute(mode.runway, world, worldIndex)
        is Some -> buildCrossAerodromeTransitRoute(
            runwayId = mode.runway,
            tmaEntry = crossAerodrome.value.tmaEntry,
            enRouteAltitudeM = crossAerodrome.value.enRouteAltitudeM,
            world = world,
            worldIndex = worldIndex,
        )
    }

    // Visual transit (single-aerodrome): zone-exit pattern; cross-aerodrome
    // transit's route is built at the Depart phase (above) when the source
    // controller's commitment is alive. Reaching here in cross-aerodrome
    // mode is structurally invalid.
    is TaskName.Transit -> when (crossAerodrome) {
        is None -> buildVisualDepartureRoute(mode.runway, world, worldIndex)
        is Some -> RoutingError.InvalidCombination(mode, taskName).left()
    }

    // Circuit pattern at destination. Join leg comes from the JoinCircuit instruction
    // stored on the mission (A12); defaults to DOWNWIND if not yet instructed.
    is TaskName.Circuit,
    is TaskName.CircuitAfterGoAround,
    is TaskName.TouchAndGo -> buildCircuitFromLeg(
        mode.runway, joinLeg.getOrElse { LegName.DOWNWIND }, world, worldIndex,
    )

    // Arrival join: route from current position through the published CTR
    // entry sequence to the circuit join leg. Cross-aerodrome only — single-
    // aerodrome arrivals don't have a published CTR-entry sequence to follow.
    is TaskName.ArrivalJoin -> when (crossAerodrome) {
        is None -> RoutingError.NotYetImplemented(mode, taskName).left()
        is Some -> buildCrossAerodromeArrivalJoinRoute(
            runwayId = mode.runway,
            tmaEntry = crossAerodrome.value.tmaEntry,
            ctrEntry = crossAerodrome.value.ctrEntry,
            corridorWaypoints = crossAerodrome.value.ctrCorridorWaypoints,
            // Goal-supplied joinLeg is authoritative; ATC override (mission.joinLeg)
            // takes priority if present. No silent default — a cross-aerodrome
            // mission *must* declare a join leg in its goal (`HighLevelGoal.
            // VfrCrossAerodromeTransit.joinLeg`).
            joinLeg = joinLeg.getOrElse { crossAerodrome.value.joinLeg },
            world = world,
            worldIndex = worldIndex,
        )
    }

    // Arrive compound: dispatches to sub-tasks, not directly routable.
    is TaskName.Arrive -> RoutingError.InvalidCombination(mode, taskName).left()

    // Go-around: rejoin circuit from go-around path.
    is TaskName.GoAround -> buildGoAroundRoute(mode.runway, world, worldIndex)

    // Ground tasks — no airborne route.
    is TaskName.GroundDeparture,
    is TaskName.GroundArrival -> RoutingError.InvalidCombination(mode, taskName).left()

    // Structural — never the active compound.
    is TaskName.CircuitTraining -> RoutingError.InvalidCombination(mode, taskName).left()

    // CrossAerodromeTransit is the root compound and never the active
    // compound during route construction — phase children (Depart,
    // Transit, ArrivalJoin, Circuit, GroundDeparture, GroundArrival)
    // dispatch through the branches above. Reaching here is structural.
    is TaskName.CrossAerodromeTransit -> RoutingError.InvalidCombination(mode, taskName).left()
}

// ── Instrument mode ─────────────────────────────────────────────────

private fun buildInstrumentModeRoute(
    mode: NavigationMode.Instrument,
    taskName: TaskName,
    world: AviationWorld,
    worldIndex: WorldIndex,
): Either<RoutingError, PilotRoute.Airborne> = when (taskName) {
    // SID departure: follow published SID waypoints with constraints.
    is TaskName.Depart,
    is TaskName.Transit -> buildSidDepartureRoute(mode.fpl, world, worldIndex)

    // STAR + approach: follow published STAR then instrument approach.
    is TaskName.Arrive -> buildStarApproachRoute(mode.fpl, world, worldIndex)

    // Arrival join: route to the first STAR waypoint (or IAF if no STAR).
    is TaskName.ArrivalJoin -> buildArrivalJoinRoute(mode.fpl, world)

    // Missed approach: follow published missed approach procedure.
    is TaskName.GoAround -> buildMissedApproachRoute(mode.fpl, world)

    // IFR does not fly VFR circuits.
    is TaskName.Circuit,
    is TaskName.CircuitAfterGoAround,
    is TaskName.TouchAndGo -> RoutingError.InvalidCombination(mode, taskName).left()

    is TaskName.GroundDeparture,
    is TaskName.GroundArrival -> RoutingError.InvalidCombination(mode, taskName).left()

    is TaskName.CircuitTraining -> RoutingError.InvalidCombination(mode, taskName).left()

    // CrossAerodromeTransit is the root compound and never the active
    // compound during route construction. Reaching here is structural.
    is TaskName.CrossAerodromeTransit -> RoutingError.InvalidCombination(mode, taskName).left()
}

// ── Emergency mode (deferred) ───────────────────────────────────────

private fun buildEmergencyModeRoute(
    mode: NavigationMode.Emergency,
    taskName: TaskName,
): Either<RoutingError, PilotRoute.Airborne> = when (taskName) {
    is TaskName.Depart,
    is TaskName.Transit,
    is TaskName.Arrive,
    is TaskName.ArrivalJoin,
    is TaskName.Circuit,
    is TaskName.CircuitAfterGoAround,
    is TaskName.TouchAndGo,
    is TaskName.GoAround -> RoutingError.NotYetImplemented(mode, taskName).left()

    is TaskName.GroundDeparture,
    is TaskName.GroundArrival -> RoutingError.InvalidCombination(mode, taskName).left()

    is TaskName.CircuitTraining -> RoutingError.InvalidCombination(mode, taskName).left()

    is TaskName.CrossAerodromeTransit -> RoutingError.InvalidCombination(mode, taskName).left()
}

// ── Route construction helpers ──────────────────────────────────────

/**
 * Build a full circuit pattern route: departure end → upwind → crosswind →
 * downwind → base → final → threshold.
 *
 * Uses [CircuitProcedure.legs] path ordering for deterministic waypoint
 * sequencing. Filters the threshold from intermediate segments (it sits on
 * the UPWIND/FINAL boundary; .distinct() would consume it early).
 */
internal fun buildCircuitPatternRoute(
    runwayId: RunwayId,
    world: AviationWorld,
    worldIndex: WorldIndex,
    lookup: CircuitLookup = CircuitLookup.ByRunway(runwayId),
): Either<RoutingError, PilotRoute.Airborne> {
    val (runway, circuit) = findRunwayAndCircuit(world, lookup)
        .fold({ return it.left() }, { it })
    val runwayPath = runway.path.points
    if (runwayPath.size < 2) return RoutingError.InsufficientGeometry("Runway $runwayId has < 2 path points").left()
    val departureEnd = runwayPath.last()
    val threshold = runway.threshold

    val segments = buildList {
        add(departureEnd)
        for (legName in CIRCUIT_LEG_ORDER) {
            val points = legPoints(circuit, legName, excludeThreshold = threshold)
            if (points.isEmpty()) return RoutingError.InsufficientGeometry("Circuit ${circuit.id} has no points for leg $legName").left()
            addAll(points)
        }
        add(threshold)
    }.distinct()

    val waypoints = NonEmptyList(segments.first(), segments.drop(1))
    return PilotRoute.Airborne(
        waypoints = waypoints,
        targetAltitudeM = CIRCUIT_ALTITUDE_M,
        arrivalPhase = PilotPhase.LandingRoll,
    ).right()
}

/**
 * Build a visual departure route: departure end → upwind → crosswind.
 *
 * For VFR departures, the pilot climbs out through the circuit departure
 * legs and continues straight out. The route terminates at crosswind;
 * the pilot's phase transitions to [PilotPhase.Crosswind] on arrival.
 *
 * Filters threshold from the UPWIND leg (threshold is tagged UPWIND+FINAL;
 * without filtering, the pilot would route back toward the threshold).
 */
internal fun buildVisualDepartureRoute(
    runwayId: RunwayId,
    world: AviationWorld,
    worldIndex: WorldIndex,
): Either<RoutingError, PilotRoute.Airborne> {
    val (runway, circuit) = findRunwayAndCircuit(world, CircuitLookup.ByRunway(runwayId))
        .fold({ return it.left() }, { it })
    val runwayPath = runway.path.points
    if (runwayPath.size < 2) return RoutingError.InsufficientGeometry("Runway $runwayId has < 2 path points").left()
    val departureEnd = runwayPath.last()
    val threshold = runway.threshold

    val upwind = legPoints(circuit, LegName.UPWIND, excludeThreshold = threshold)
    val crosswind = legPoints(circuit, LegName.CROSSWIND, excludeThreshold = threshold)
    if (upwind.isEmpty() && crosswind.isEmpty()) {
        return RoutingError.InsufficientGeometry("No UPWIND or CROSSWIND points for runway $runwayId").left()
    }

    val segments = buildList {
        add(departureEnd)
        addAll(upwind)
        addAll(crosswind)
    }.distinct()

    val waypoints = NonEmptyList(segments.first(), segments.drop(1))
    return PilotRoute.Airborne(
        waypoints = waypoints,
        targetAltitudeM = CIRCUIT_ALTITUDE_M,
        arrivalPhase = PilotPhase.Crosswind,
    ).right()
}

/**
 * Build a partial circuit route starting from [startLeg] through to landing.
 *
 * Used for arrivals joining mid-circuit. The route includes all circuit leg
 * points from [startLeg] onward, ending at the threshold.
 *
 * Leg ordering: UPWIND → CROSSWIND → DOWNWIND → BASE → FINAL.
 * Starting from DOWNWIND gives: downwind → base → final → threshold.
 */
internal fun buildCircuitFromLeg(
    runwayId: RunwayId,
    startLeg: LegName,
    world: AviationWorld,
    worldIndex: WorldIndex,
): Either<RoutingError, PilotRoute.Airborne> {
    val (runway, circuit) = findRunwayAndCircuit(world, CircuitLookup.ByRunway(runwayId))
        .fold({ return it.left() }, { it })
    val threshold = runway.threshold

    val startIndex = CIRCUIT_LEG_ORDER.indexOf(startLeg)
    if (startIndex < 0) return RoutingError.InsufficientGeometry("Unknown circuit leg: $startLeg").left()

    val segments = buildList {
        for (i in startIndex until CIRCUIT_LEG_ORDER.size) {
            val points = legPoints(circuit, CIRCUIT_LEG_ORDER[i], excludeThreshold = threshold)
            if (points.isEmpty()) return RoutingError.InsufficientGeometry("Circuit ${circuit.id} has no points for leg ${CIRCUIT_LEG_ORDER[i]}").left()
            // Drop the first point of each leg (it's the junction from the previous
            // leg) unless this is the first leg in our slice — then we need it as
            // the route's entry point. BUT: for the first leg in the slice, the first
            // point is the junction from the PRIOR (not-included) leg, so skip it too.
            // The pilot is joining mid-circuit AT the start of this leg, not at the
            // tail of the previous one.
            if (i == startIndex) addAll(points.drop(1)) else addAll(points)
        }
        add(threshold)
    }.distinct()

    if (segments.size < 2) return RoutingError.InsufficientGeometry("Circuit from $startLeg has < 2 waypoints").left()

    val waypoints = NonEmptyList(segments.first(), segments.drop(1))
    return PilotRoute.Airborne(
        waypoints = waypoints,
        targetAltitudeM = CIRCUIT_ALTITUDE_M,
        arrivalPhase = PilotPhase.LandingRoll,
    ).right()
}

/**
 * Cross-aerodrome transit route: from the source runway's departure end,
 * out through the climb-out leg, to the destination's TMA-entry waypoint.
 *
 * The route terminates at [tmaEntry] — when the pilot reaches it, the
 * Transit phase completes and the ArrivalJoin phase begins.
 *
 * Note (G1-DEF-11): the source-airport climb-out points and the
 * destination's [tmaEntry] live in different airport-local Cartesian
 * frames in the merged world; the geometric distance between them is
 * meaningless until the merge reprojects to a shared frame. The
 * waypoint *identity* sequence is correct here; flight tracking will
 * succeed only after G1-DEF-11 lands.
 */
internal fun buildCrossAerodromeTransitRoute(
    runwayId: RunwayId,
    tmaEntry: xyz.easiersaid.twr.protocol.FixId,
    enRouteAltitudeM: Double,
    world: AviationWorld,
    worldIndex: WorldIndex,
): Either<RoutingError, PilotRoute.Airborne> {
    val (runway, circuit) = findRunwayAndCircuit(world, CircuitLookup.ByRunway(runwayId))
        .fold({ return it.left() }, { it })
    val runwayPath = runway.path.points
    if (runwayPath.size < 2) return RoutingError.InsufficientGeometry("Runway $runwayId has < 2 path points").left()
    val departureEnd = runwayPath.last()
    val threshold = runway.threshold
    val tmaEntryFix = world.fixes[tmaEntry] ?: return RoutingError.MissingTransitWaypoint(tmaEntry).left()

    val upwind = legPoints(circuit, LegName.UPWIND, excludeThreshold = threshold)
    val crosswind = legPoints(circuit, LegName.CROSSWIND, excludeThreshold = threshold)

    val segments = buildList {
        add(departureEnd)
        addAll(upwind)
        addAll(crosswind)
        add(tmaEntryFix.point)
    }.distinct()

    // arrivalPhase = Crosswind: at the TMA entry the pilot is in stable cruise,
    // not climbing. Matches the buildVisualDepartureRoute idiom (Crosswind as
    // "stable, no specific stage"). En-route altitude is supplied by the goal
    // — for LOWG → LJMB this clears the Pohorje massif (~5000 ft). Note
    // (atc-general round-3): the upwind/crosswind dogleg here is a circuit-leg
    // approximation pending VFR-exit-lane authoring (G1-DEF-12).
    val waypoints = NonEmptyList(segments.first(), segments.drop(1))
    return PilotRoute.Airborne(
        waypoints = waypoints,
        targetAltitudeM = enRouteAltitudeM,
        arrivalPhase = PilotPhase.Crosswind,
    ).right()
}

/**
 * Cross-aerodrome arrival-join route: from the destination's TMA-entry
 * waypoint, through the CTR-entry waypoint, into the destination circuit
 * at [joinLeg].
 *
 * The pilot is already at or near [tmaEntry] when this route is built
 * (the Transit phase has completed). The route runs:
 * `tmaEntry → ctrEntry → [circuit-leg points from joinLeg onward] →
 *  threshold`.
 */
internal fun buildCrossAerodromeArrivalJoinRoute(
    runwayId: RunwayId,
    tmaEntry: xyz.easiersaid.twr.protocol.FixId,
    ctrEntry: xyz.easiersaid.twr.protocol.FixId,
    corridorWaypoints: List<xyz.easiersaid.twr.protocol.FixId>,
    joinLeg: LegName,
    world: AviationWorld,
    worldIndex: WorldIndex,
): Either<RoutingError, PilotRoute.Airborne> {
    val (runway, circuit) = findRunwayAndCircuit(world, CircuitLookup.ByRunway(runwayId))
        .fold({ return it.left() }, { it })
    val threshold = runway.threshold
    val tmaEntryFix = world.fixes[tmaEntry] ?: return RoutingError.MissingTransitWaypoint(tmaEntry).left()
    val ctrEntryFix = world.fixes[ctrEntry] ?: return RoutingError.MissingTransitWaypoint(ctrEntry).left()
    // Resolve every corridor waypoint up front; report the first missing one.
    val corridorFixes = corridorWaypoints.map { ident ->
        world.fixes[ident] ?: return RoutingError.MissingTransitWaypoint(ident).left()
    }
    val arrivalAerodrome = world.aerodromes.entries
        .firstOrNull { (_, ad) -> runwayId in ad.runways.keys }?.key
        ?: return RoutingError.RunwayNotFound(runwayId).left()

    val startIndex = CIRCUIT_LEG_ORDER.indexOf(joinLeg)
    if (startIndex < 0) {
        return RoutingError.NoEntrySequenceForRunway(
            aerodrome = arrivalAerodrome,
            runway = runwayId,
        ).left()
    }

    val segments = buildList {
        add(tmaEntryFix.point)
        add(ctrEntryFix.point)
        // Corridor REPs between the CTR entry and the circuit join (e.g. MN2
        // between MN1 and the right-base join for LJMB RWY 14). Per atc-
        // general round-3 finding: skipping these amounts to skipping a
        // published reporting point.
        for (fix in corridorFixes) add(fix.point)
        for (i in startIndex until CIRCUIT_LEG_ORDER.size) {
            val points = legPoints(circuit, CIRCUIT_LEG_ORDER[i], excludeThreshold = threshold)
            if (points.isEmpty()) {
                return RoutingError.InsufficientGeometry(
                    "Circuit ${circuit.id} has no points for leg ${CIRCUIT_LEG_ORDER[i]}",
                ).left()
            }
            if (i == startIndex) addAll(points.drop(1)) else addAll(points)
        }
        add(threshold)
    }.distinct()

    val waypoints = NonEmptyList(segments.first(), segments.drop(1))
    return PilotRoute.Airborne(
        waypoints = waypoints,
        targetAltitudeM = CIRCUIT_ALTITUDE_M,
        arrivalPhase = PilotPhase.LandingRoll,
    ).right()
}

/**
 * Build a go-around route: published go-around path → rejoin full circuit.
 *
 * Uses [CircuitProcedure.goAroundPath] for the initial climb-out, then
 * continues through the circuit from UPWIND onward (crosswind → downwind →
 * base → final → threshold).
 *
 * Filters threshold from both the go-around path and intermediate leg points
 * to prevent [List.distinct] from consuming it before the route's end.
 */
internal fun buildGoAroundRoute(
    runwayId: RunwayId,
    world: AviationWorld,
    worldIndex: WorldIndex,
    lookup: CircuitLookup = CircuitLookup.ByRunway(runwayId),
): Either<RoutingError, PilotRoute.Airborne> {
    val (runway, circuit) = findRunwayAndCircuit(world, lookup)
        .fold({ return it.left() }, { it })
    val threshold = runway.threshold

    // Go-around path → full circuit from UPWIND onward → threshold.
    // Filter threshold from go-around path (the pilot is AT the threshold,
    // it's not a target). Include UPWIND to capture any intermediate points
    // between the go-around endpoint and crosswind.
    val goAroundPoints = circuit.goAroundPath.points.filter { it != threshold }

    val segments = buildList {
        addAll(goAroundPoints)
        for (legName in CIRCUIT_LEG_ORDER) {
            addAll(legPoints(circuit, legName, excludeThreshold = threshold))
        }
        add(threshold)
    }.distinct()

    val waypoints = NonEmptyList(segments.first(), segments.drop(1))
    return PilotRoute.Airborne(
        waypoints = waypoints,
        targetAltitudeM = CIRCUIT_ALTITUDE_M,
        arrivalPhase = PilotPhase.LandingRoll,
    ).right()
}

/**
 * Build a SID departure route from the flight plan's assigned SID.
 *
 * Extracts waypoints from the published [Sid] for the departure runway,
 * populating [WaypointConstraints] from each waypoint's altitude and speed
 * constraints. The route starts at the departure end of the runway and
 * continues through the SID waypoints.
 *
 * Falls back to [buildVisualDepartureRoute] if no SID is assigned (the
 * pilot departs visually — common for VFR-in-IFR or SID not available).
 */
internal fun buildSidDepartureRoute(
    fpl: FlightPlan,
    world: AviationWorld,
    worldIndex: WorldIndex,
): Either<RoutingError, PilotRoute.Airborne> {
    val clearance = fpl.clearance
    val depRunway = when (clearance) {
        is ClearanceState.Uncleaned -> return RoutingError.InsufficientGeometry("No clearance — cannot build SID route").left()
        is ClearanceState.EnRouteClearance -> clearance.departureRunway
        is ClearanceState.ApproachClearance -> clearance.departureRunway
    }
    val sidId = when (clearance) {
        is ClearanceState.Uncleaned -> null
        is ClearanceState.EnRouteClearance -> clearance.sid
        is ClearanceState.ApproachClearance -> clearance.sid
    }

    // No SID assigned — fall back to visual departure.
    if (sidId == null) return buildVisualDepartureRoute(depRunway, world, worldIndex)

    // Look up the SID procedure.
    val sid = world.aerodromes.values
        .firstNotNullOfOrNull { it.sids[sidId] }
        ?: return RoutingError.ProcedureNotFound(sidId.value).left()

    // SID must be for the assigned runway.
    if (sid.runway != depRunway) {
        return RoutingError.InsufficientGeometry("SID ${sidId.value} is for runway ${sid.runway}, not $depRunway").left()
    }

    // Look up the runway for the departure end point.
    val runway = world.aerodromes.values
        .firstNotNullOfOrNull { it.runways[depRunway] }
        ?: return RoutingError.RunwayNotFound(depRunway).left()
    val runwayPath = runway.path.points
    if (runwayPath.size < 2) return RoutingError.InsufficientGeometry("Runway $depRunway has < 2 path points").left()
    val departureEnd = runwayPath.last()

    // Build the waypoint sequence: departure end → SID waypoints.
    val sidPoints = sid.waypoints.map { it.point }
    val segments = (listOf(departureEnd) + sidPoints).distinct()
    if (segments.size < 2) return RoutingError.InsufficientGeometry("SID route has < 2 distinct waypoints").left()

    // Extract constraints from the SID waypoints.
    val constraints = sid.waypoints
        .filter { it.altitudeConstraint != null || it.speedConstraint != null }
        .associate { it.point to WaypointConstraints(it.altitudeConstraint, it.speedConstraint) }

    // Target altitude: use the last SID waypoint's altitude constraint if available,
    // otherwise use circuit altitude as a reasonable default.
    val lastAltitude = sid.waypoints.lastOrNull()?.altitudeConstraint
    val targetAltitudeM = when (lastAltitude) {
        is xyz.easiersaid.twr.core.world.AltitudeConstraint.At -> levelToMeters(lastAltitude.level)
        is xyz.easiersaid.twr.core.world.AltitudeConstraint.AtOrAbove -> levelToMeters(lastAltitude.minimum)
        is xyz.easiersaid.twr.core.world.AltitudeConstraint.AtOrBelow -> levelToMeters(lastAltitude.maximum)
        is xyz.easiersaid.twr.core.world.AltitudeConstraint.Between -> levelToMeters(lastAltitude.minimum)
        null -> CIRCUIT_ALTITUDE_M
    }

    val waypoints = NonEmptyList(segments.first(), segments.drop(1))
    return PilotRoute.Airborne(
        waypoints = waypoints,
        targetAltitudeM = targetAltitudeM,
        arrivalPhase = PilotPhase.Climbing,
        waypointConstraints = constraints,
    ).right()
}

/**
 * Build a STAR + approach route from the flight plan.
 *
 * Concatenates STAR waypoints with instrument approach waypoints, handling
 * the STAR-to-approach transition gap: if the last STAR waypoint differs
 * from the first approach waypoint, both are included (the pilot flies
 * directly between them, typically on ATC vectors or "proceed direct IAF").
 *
 * Requires [ClearanceState.ApproachClearance] — the pilot must have been
 * cleared for an approach to build this route.
 */
internal fun buildStarApproachRoute(
    fpl: FlightPlan,
    world: AviationWorld,
    worldIndex: WorldIndex,
): Either<RoutingError, PilotRoute.Airborne> {
    val clearance = fpl.clearance as? ClearanceState.ApproachClearance
        ?: return RoutingError.InsufficientGeometry("No approach clearance — cannot build STAR+approach route").left()

    // Look up STAR (optional — some arrivals go direct to IAF).
    val star: Star? = clearance.star?.let { starId ->
        world.aerodromes.values.firstNotNullOfOrNull { it.stars[starId] }
            ?: return RoutingError.ProcedureNotFound(starId.value).left()
    }

    // Look up approach by type + runway.
    val approach: InstrumentApproach = world.aerodromes.values
        .flatMap { it.approaches.values }
        .firstOrNull { it.type == clearance.approachType && it.runway == clearance.arrivalRunway }
        ?: return RoutingError.ProcedureNotFound("${clearance.approachType} for ${clearance.arrivalRunway}").left()

    // Build waypoint sequence: STAR → approach.
    val starPoints = star?.waypoints?.map { it.point } ?: emptyList()
    val approachPoints = approach.waypoints.map { it.point }
    val allPoints = (starPoints + approachPoints).distinct()

    if (allPoints.size < 2) return RoutingError.InsufficientGeometry("STAR+approach has < 2 distinct waypoints").left()

    // Collect constraints from both procedures.
    val constraints = mutableMapOf<PointId, WaypointConstraints>()
    star?.waypoints?.forEach { wp ->
        if (wp.altitudeConstraint != null || wp.speedConstraint != null) {
            constraints[wp.point] = WaypointConstraints(wp.altitudeConstraint, wp.speedConstraint)
        }
    }
    approach.waypoints.forEach { wp ->
        if (wp.altitudeConstraint != null || wp.speedConstraint != null) {
            // Approach constraints override STAR constraints for shared waypoints (e.g., IAF).
            constraints[wp.point] = WaypointConstraints(wp.altitudeConstraint, wp.speedConstraint)
        }
    }

    // S6: target altitude should be the first waypoint's constraint, not zero.
    // The kinematic layer refines altitude from per-waypoint constraints.
    val firstAltitude = (star?.waypoints ?: approach.waypoints).firstOrNull()
        ?.altitudeConstraint?.let { resolveConstraintAltitude(it) }
        ?: CIRCUIT_ALTITUDE_M

    val waypoints = NonEmptyList(allPoints.first(), allPoints.drop(1))
    return PilotRoute.Airborne(
        waypoints = waypoints,
        targetAltitudeM = firstAltitude,
        arrivalPhase = PilotPhase.LandingRoll,
        waypointConstraints = constraints,
    ).right()
}

/**
 * Build an arrival join route: route to the first STAR waypoint or IAF.
 *
 * The pilot is inbound and needs to reach the start of the published
 * arrival procedure. If a STAR is assigned, the first STAR waypoint is
 * the target. Otherwise, the first approach waypoint (IAF).
 */
internal fun buildArrivalJoinRoute(
    fpl: FlightPlan,
    world: AviationWorld,
): Either<RoutingError, PilotRoute.Airborne> {
    val clearance = fpl.clearance
    val starId = when (clearance) {
        is ClearanceState.Uncleaned -> null
        is ClearanceState.EnRouteClearance -> clearance.star
        is ClearanceState.ApproachClearance -> clearance.star
    }
    val approachType = (clearance as? ClearanceState.ApproachClearance)?.approachType
    val arrivalRunway = (clearance as? ClearanceState.ApproachClearance)?.arrivalRunway

    // Find the first waypoint of the assigned STAR or approach.
    val targetPoint: PointId = if (starId != null) {
        val star = world.aerodromes.values.firstNotNullOfOrNull { it.stars[starId] }
            ?: return RoutingError.ProcedureNotFound(starId.value).left()
        star.waypoints.first().point
    } else if (approachType != null && arrivalRunway != null) {
        val approach = world.aerodromes.values
            .flatMap { it.approaches.values }
            .firstOrNull { it.type == approachType && it.runway == arrivalRunway }
            ?: return RoutingError.ProcedureNotFound("$approachType for $arrivalRunway").left()
        approach.waypoints.first().point
    } else {
        return RoutingError.InsufficientGeometry("No STAR or approach assigned — cannot build arrival join route").left()
    }

    val waypoints = NonEmptyList(targetPoint, emptyList())
    return PilotRoute.Airborne(
        waypoints = waypoints,
        targetAltitudeM = CIRCUIT_ALTITUDE_M, // initial descent target; refined by constraints
        arrivalPhase = PilotPhase.Climbing, // en-route to STAR/IAF, not yet on approach
    ).right()
}

/**
 * Build a missed approach route from the published missed approach procedure.
 *
 * The pilot climbs out on the missed approach, following the published
 * waypoints, and terminates at the missed approach hold fix. The hold
 * itself (repeated loop flying) is handled by the kinematic layer and
 * [AWAITING_ATC_INSTRUCTION] mission step.
 *
 * Requires [ClearanceState.ApproachClearance] — the missed approach
 * procedure is part of the assigned instrument approach.
 */
internal fun buildMissedApproachRoute(
    fpl: FlightPlan,
    world: AviationWorld,
): Either<RoutingError, PilotRoute.Airborne> {
    val clearance = fpl.clearance as? ClearanceState.ApproachClearance
        ?: return RoutingError.InsufficientGeometry("No approach clearance — cannot build missed approach route").left()

    // Look up the approach to get the missed approach procedure.
    val approach = world.aerodromes.values
        .flatMap { it.approaches.values }
        .firstOrNull { it.type == clearance.approachType && it.runway == clearance.arrivalRunway }
        ?: return RoutingError.ProcedureNotFound("${clearance.approachType} for ${clearance.arrivalRunway}").left()

    val missedWaypoints = approach.missedApproach.waypoints.map { it.point }
    if (missedWaypoints.isEmpty()) {
        return RoutingError.InsufficientGeometry("Missed approach has no waypoints").left()
    }

    // Collect constraints from missed approach waypoints.
    val constraints = approach.missedApproach.waypoints
        .filter { it.altitudeConstraint != null || it.speedConstraint != null }
        .associate { it.point to WaypointConstraints(it.altitudeConstraint, it.speedConstraint) }

    val segments = missedWaypoints.distinct()
    val waypoints = NonEmptyList(segments.first(), segments.drop(1))
    return PilotRoute.Airborne(
        waypoints = waypoints,
        // Target altitude from the last missed approach waypoint's constraint, or
        // the holding pattern altitude. Falls back to circuit altitude.
        targetAltitudeM = approach.missedApproach.waypoints.lastOrNull()
            ?.altitudeConstraint?.let { resolveConstraintAltitude(it) }
            ?: CIRCUIT_ALTITUDE_M,
        arrivalPhase = PilotPhase.Climbing, // pilot is climbing on missed approach
        waypointConstraints = constraints,
    ).right()
}

/** Convert a [Level] to altitude in meters. Approximate: 1 FL = 100 ft ≈ 30.48 m. */
internal fun levelToMeters(level: xyz.easiersaid.twr.protocol.Level): Double = when (level) {
    is xyz.easiersaid.twr.protocol.Level.FlightLevel -> level.fl * 30.48
    is xyz.easiersaid.twr.protocol.Level.AltitudeFeet -> level.feet * 0.3048
    is xyz.easiersaid.twr.protocol.Level.HeightFeet -> level.feet * 0.3048
}

/** Resolve an altitude constraint to a target altitude in meters. */
private fun resolveConstraintAltitude(constraint: xyz.easiersaid.twr.core.world.AltitudeConstraint): Double =
    when (constraint) {
        is xyz.easiersaid.twr.core.world.AltitudeConstraint.At -> levelToMeters(constraint.level)
        is xyz.easiersaid.twr.core.world.AltitudeConstraint.AtOrAbove -> levelToMeters(constraint.minimum)
        is xyz.easiersaid.twr.core.world.AltitudeConstraint.AtOrBelow -> levelToMeters(constraint.maximum)
        is xyz.easiersaid.twr.core.world.AltitudeConstraint.Between -> levelToMeters(constraint.minimum)
    }

// ── Navigation mode derivation ──────────────────────────────────────

/**
 * Derive the navigation mode from the pilot's goal and assigned runway.
 *
 * Exhaustive over [HighLevelGoal]. Returns [RoutingError] if the world
 * lacks a circuit procedure for a circuit-training goal.
 */
fun deriveNavigationMode(
    goal: HighLevelGoal,
    runway: RunwayId,
    world: AviationWorld,
): Either<RoutingError, NavigationMode> = when (goal) {
    is HighLevelGoal.CircuitTraining -> {
        val procedureId = world.aerodromes.values
            .flatMap { it.circuits.values }
            .firstOrNull { it.runway == runway }
            ?.id
        if (procedureId != null) NavigationMode.Circuit(runway, procedureId).right()
        else RoutingError.CircuitNotFound(runway).left()
    }
    is HighLevelGoal.Departure -> NavigationMode.Visual(runway, goal.destination).right()
    is HighLevelGoal.Arrival -> NavigationMode.Visual(runway, destination = null).right()
    is HighLevelGoal.Transit -> NavigationMode.Visual(runway, goal.destination).right()
    is HighLevelGoal.VfrCrossAerodromeTransit ->
        // Visual mode at the *destination* — the runway parameter here is the
        // active runway at goal.to, not goal.from. Cross-aerodrome route
        // construction lands in G1.4.
        NavigationMode.Visual(runway, goal.to).right()
}

// ── Shared helpers ──────────────────────────────────────────────────

/** Circuit altitude — ~1000 ft. Per-aircraft-type values land later. */
internal const val CIRCUIT_ALTITUDE_M: Double = 300.0

/** Canonical circuit leg order. */
private val CIRCUIT_LEG_ORDER = listOf(LegName.UPWIND, LegName.CROSSWIND, LegName.DOWNWIND, LegName.BASE, LegName.FINAL)

/**
 * Look up the runway and its circuit procedure from the world.
 *
 * Dispatch on [CircuitLookup]:
 * - [CircuitLookup.ById]: looks up circuit by ID, then derives runway from the circuit.
 *   Failure is a data-integrity error — no silent fallback to runway-first.
 * - [CircuitLookup.ByRunway]: runway-first search, returns the first matching circuit.
 *   Used from visual mode where no procedure ID is known.
 */
private fun findRunwayAndCircuit(
    world: AviationWorld,
    lookup: CircuitLookup,
): Either<RoutingError, Pair<Runway, CircuitProcedure>> = when (lookup) {
    is CircuitLookup.ById -> {
        val aerodrome = world.aerodromes.values
            .firstOrNull { it.circuits.containsKey(lookup.id) }
            ?: return RoutingError.ProcedureNotFound(lookup.id.value).left()
        val circuit = aerodrome.circuits.getValue(lookup.id)
        val runway = aerodrome.runways[circuit.runway]
            ?: return RoutingError.RunwayNotFound(circuit.runway).left()
        (runway to circuit).right()
    }
    is CircuitLookup.ByRunway -> {
        val aerodrome = world.aerodromes.values
            .firstOrNull { it.runways.containsKey(lookup.runwayId) }
            ?: return RoutingError.RunwayNotFound(lookup.runwayId).left()
        val runway = aerodrome.runways.getValue(lookup.runwayId)
        val circuit = aerodrome.circuits.values
            .firstOrNull { it.runway == lookup.runwayId }
            ?: return RoutingError.CircuitNotFound(lookup.runwayId).left()
        (runway to circuit).right()
    }
}

/**
 * Points for a circuit leg in path order, optionally excluding threshold.
 *
 * Uses [CircuitProcedure.legs] directly for deterministic spatial ordering
 * (not the WorldIndex, whose map iteration order is unspecified).
 */
private fun legPoints(
    circuit: CircuitProcedure,
    leg: LegName,
    excludeThreshold: PointId? = null,
): List<PointId> {
    val circuitLeg = circuit.legs.firstOrNull { it.name == leg } ?: return emptyList()
    return if (excludeThreshold != null) circuitLeg.path.points.filter { it != excludeThreshold }
    else circuitLeg.path.points
}
