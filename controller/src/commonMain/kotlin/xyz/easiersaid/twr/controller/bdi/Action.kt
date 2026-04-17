package xyz.easiersaid.twr.controller.bdi

import arrow.core.Either
import arrow.core.right
import arrow.core.left
import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.assess.RunwayOperation
import xyz.easiersaid.twr.protocol.*

/** Failure when an action cannot resolve against the world. */
data class ActionResolutionFailure(val reason: String)

/** Sequence info to emit alongside the instruction. */
data class SequenceInfo(
    val number: Int,
    val behindTraffic: TrafficRef? = null,
)

/** Traffic info to emit alongside the instruction. */
data class TrafficInfo(
    val traffic: TrafficRef,
    val description: String,
    /**
     * Structured hints used by the Controller to populate [TrafficInformation]. Carried
     * on the proposed action so that rule/action code (which has the belief context)
     * can hand them to the companion-output builder without a second lookup.
     */
    val level: Level? = null,
    val movement: String? = null,
    val distanceNm: Double? = null,
    val clockPosition: Int? = null,
)

/**
 * How the controller wants the instruction dispatched — directly, or gated on a
 * visible surface condition ("behind the landing 737, line up runway 27").
 *
 * Replaces a nullable `condition: ConditionalPredicate?` flag with an explicit
 * algebra so callers reckon with the variant at construction time and exhaustive
 * `when` is available at every downstream boundary.
 */
sealed interface Dispatch {
    val instruction: AtcInstruction

    data class Direct(override val instruction: AtcInstruction) : Dispatch

    data class Conditional(
        override val instruction: AtcInstruction,
        val condition: ConditionalPredicate,
    ) : Dispatch
}

/** A proposed instruction before arbitration. Urgency comes from the rule, not the action. */
data class ProposedAction(
    val aircraft: AircraftId,
    val dispatch: Dispatch,
    val sequenceInfo: SequenceInfo? = null,
    val trafficInfo: TrafficInfo? = null,
) {
    val instruction: AtcInstruction get() = dispatch.instruction
}

/** Convenience constructor for the common direct-dispatch case. */
fun ProposedAction(
    aircraft: AircraftId,
    instruction: AtcInstruction,
    sequenceInfo: SequenceInfo? = null,
    trafficInfo: TrafficInfo? = null,
): ProposedAction = ProposedAction(
    aircraft = aircraft,
    dispatch = Dispatch.Direct(instruction),
    sequenceInfo = sequenceInfo,
    trafficInfo = trafficInfo,
)

/**
 * An abstract action resolved to a concrete instruction at execution time.
 * Returns [Either] — failed resolutions surface in the trace, not silently swallowed.
 */
sealed interface RuleAction {
    fun resolve(
        ac: AircraftObservation,
        commitment: Commitment,
        ctx: OperatorContext,
    ): Either<ActionResolutionFailure, ProposedAction>
}

// ── Concrete actions ─────────────────────────────────────────────────

data object LineUpAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        when (val rwy = commitment.runway ?: ctx.beliefs.activeRunway) {
            null -> ActionResolutionFailure("No runway in commitment or beliefs").left()
            else -> ProposedAction(ac.id, LineUpAndWait(ac.id, rwy)).right()
        }
}

data object ConditionalLineUpAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val rwy = commitment.runway ?: ctx.beliefs.activeRunway
            ?: return ActionResolutionFailure("No runway").left()
        val rwyObs = ctx.beliefs.runwayBeliefs[rwy]
            ?: return ActionResolutionFailure("No runway observation for $rwy").left()
        val occupant = rwyObs.occupants.firstOrNull { it != ac.id }
            ?: return ActionResolutionFailure("No occupant to condition on").left()
        val occupantObs = ctx.beliefs.trackedAircraft[occupant]
        val trafficRef = resolveTrafficRef(occupant, ctx.beliefs)
        val action = if (occupantObs?.pilotGoal == xyz.easiersaid.twr.controller.PilotGoal.ARRIVE ||
            occupantObs?.pilotGoal == xyz.easiersaid.twr.controller.PilotGoal.TOUCH_AND_GO)
            TrafficAction.LANDING else TrafficAction.DEPARTING
        return ProposedAction(
            aircraft = ac.id,
            dispatch = Dispatch.Conditional(
                instruction = LineUpAndWait(ac.id, rwy),
                condition = ConditionalPredicate.AfterTraffic(trafficRef, action),
            ),
        ).right()
    }
}

data object ClearTakeoffAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        when (val rwy = commitment.runway ?: ctx.beliefs.activeRunway) {
            null -> ActionResolutionFailure("No runway").left()
            else -> ProposedAction(ac.id, ClearedForTakeoff(ac.id, rwy)).right()
        }
}

data object ClearLandAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val rwy = commitment.runway ?: ctx.beliefs.activeRunway
            ?: return ActionResolutionFailure("No runway").left()
        val traffic = findRelevantTraffic(ac.id, ctx)
        return ProposedAction(ac.id, ClearedToLand(ac.id, rwy), trafficInfo = traffic).right()
    }
}

data object ClearTouchAndGoAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        when (val rwy = commitment.runway ?: ctx.beliefs.activeRunway) {
            null -> ActionResolutionFailure("No runway").left()
            else -> ProposedAction(ac.id, ClearedTouchAndGo(ac.id, rwy)).right()
        }
}

data object HoldPositionAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(ac.id, HoldPosition(ac.id)).right()
}

data object CancelTakeoffAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(ac.id, HoldPositionCancelTakeoff(ac.id)).right()
}

data object GoAroundAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(ac.id, GoAround(ac.id)).right()
}

data object VacateAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway
            ?: return ActionResolutionFailure("No runway for vacate").left()
        val aerodrome = ctx.world.aerodromes[ctx.view.aerodromeId]
            ?: return ActionResolutionFailure("Aerodrome not found").left()
        val rwy = aerodrome.runways[runway]
            ?: return ActionResolutionFailure("Runway $runway not found").left()

        // Pick nearest exit, or backtrack if no exits
        val exit = rwy.exits.firstOrNull()
        return if (exit != null) {
            ProposedAction(ac.id, AfterLandingVacateVia(ac.id, exit.point)).right()
        } else {
            ProposedAction(ac.id, BacktrackRunway(ac.id, runway)).right()
        }
    }
}

data object TurnBaseAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(ac.id, TurnBase(ac.id)).right()
}

data object ExtendDownwindAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val seqInfo = deriveSequenceInfo(ac.id, ctx)
        return ProposedAction(ac.id, ExtendDownwind(ac.id), sequenceInfo = seqInfo).right()
    }
}

data object ContinueApproachAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(ac.id, ContinueApproach(ac.id, inferContinueApproachReason(ac, ctx))).right()
}

/**
 * Pick the best reason phrase for a delayed landing clearance. Prefers the most
 * specific observation about runway state, falling back to "runway access pending"
 * when we can see nothing more useful.
 */
private fun inferContinueApproachReason(
    ac: AircraftObservation,
    ctx: OperatorContext,
): ContinueApproachReason {
    val duty = ctx.beliefs.runwayDuty
    val holder = duty?.holder
    if (holder != null && holder != ac.id) {
        when (duty.operation) {
            RunwayOperation.ARRIVAL -> return ContinueApproachReason.TRAFFIC_LANDING
            RunwayOperation.DEPARTURE -> return ContinueApproachReason.TRAFFIC_DEPARTING
            RunwayOperation.CROSSING, RunwayOperation.BACKTRACK ->
                return ContinueApproachReason.TRAFFIC_CROSSING
            null -> Unit
        }
    }
    val recentGoAround = ctx.events.any { it is xyz.easiersaid.twr.controller.observe.ControllerEvent.GoAroundDetected && it.aircraft != ac.id }
    if (recentGoAround) return ContinueApproachReason.PRECEDING_GO_AROUND
    return ContinueApproachReason.RUNWAY_ACCESS_PENDING
}

data class HandoffAction(val toRole: RoleName) : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val aerodrome = ctx.world.aerodromes[ctx.view.aerodromeId]
            ?: return ActionResolutionFailure("Aerodrome ${ctx.view.aerodromeId} not found").left()
        val role = aerodrome.roles[toRole]
            ?: return ActionResolutionFailure("No ${toRole.name} role at ${ctx.view.aerodromeId}").left()
        return ProposedAction(ac.id, ContactFrequency(ac.id, toRole, frequency = role.frequency)).right()
    }
}

data object TaxiToHoldingAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway
            ?: return ActionResolutionFailure("No runway for holding point lookup").left()
        val holdingPoints = ctx.worldIndex.holdingPointsByRunway[runway]
            ?: return ActionResolutionFailure("No holding points for runway $runway").left()
        val destination = nearestPoint(ac.position, holdingPoints, ctx.worldIndex)
            ?: return ActionResolutionFailure("Empty holding points set").left()
        val via = findRoute(ac.position, destination, ctx.worldIndex)
            ?: return ActionResolutionFailure("No taxi route from ${ac.position} to $destination").left()
        return ProposedAction(ac.id, TaxiTo(ac.id, destination, via)).right()
    }
}

data object TaxiToStandAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val aerodrome = ctx.world.aerodromes[ctx.view.aerodromeId]
            ?: return ActionResolutionFailure("Aerodrome not found").left()
        val standPoints = aerodrome.stands.values.map { it.point }.toSet()
        val destination = nearestPoint(ac.position, standPoints, ctx.worldIndex)
            ?: return ActionResolutionFailure("No stands at aerodrome").left()
        val via = findRoute(ac.position, destination, ctx.worldIndex)
            ?: return ActionResolutionFailure("No taxi route from ${ac.position} to $destination").left()
        return ProposedAction(ac.id, TaxiTo(ac.id, destination, via)).right()
    }
}

/** Construct a TrafficRef for an aircraft from beliefs. Prefers callsign. */
fun resolveTrafficRef(acId: AircraftId, beliefs: xyz.easiersaid.twr.controller.observe.BeliefState): TrafficRef {
    val ac = beliefs.trackedAircraft[acId]
    return TrafficRef.ByCallsign(ac?.callsign ?: Callsign(acId.value))
}

/** Describe traffic for phraseology: "Cessna on final" or just callsign if no type known. */
fun describeTraffic(acId: AircraftId, beliefs: xyz.easiersaid.twr.controller.observe.BeliefState, worldIndex: xyz.easiersaid.twr.core.world.WorldIndex): String {
    val ac = beliefs.trackedAircraft[acId] ?: return acId.value
    val type = ac.typeDescription ?: ""
    val leg = worldIndex.circuitLegsByPoint[ac.position]?.firstOrNull()?.name?.lowercase() ?: ""
    return listOf(type, leg).filter { it.isNotEmpty() }.joinToString(" on ").ifEmpty { ac.callsign.value }
}

/** Pick the point from candidates nearest to the reference point. Deterministic by position. */
private fun nearestPoint(from: PointId, candidates: Set<PointId>, worldIndex: xyz.easiersaid.twr.core.world.WorldIndex): PointId? {
    if (candidates.isEmpty()) return null
    if (candidates.size == 1) return candidates.first()
    val fromPos = worldIndex.positions[from] ?: return candidates.minByOrNull { it.value }
    return candidates.minByOrNull { point ->
        val pos = worldIndex.positions[point] ?: return@minByOrNull Double.MAX_VALUE
        val dx = pos.xMeters - fromPos.xMeters
        val dy = pos.yMeters - fromPos.yMeters
        dx * dx + dy * dy // squared distance, no need for sqrt
    }
}

/**
 * BFS on adjacency graph to find route between two points.
 *
 * Returns:
 * - `emptyList()` when `from == to` — direct route, no intermediate via points needed.
 * - A non-empty list of intermediate via points (excluding `from` and `to`) when a path exists.
 * - `null` when no path exists — callers must handle this distinctly from the empty-via case.
 */
private fun findRoute(from: PointId, to: PointId, worldIndex: xyz.easiersaid.twr.core.world.WorldIndex): List<PointId>? {
    if (from == to) return emptyList()
    val adjacency = worldIndex.adjacency
    val visited = mutableSetOf(from)
    val parent = mutableMapOf<PointId, PointId>()
    val queue = ArrayDeque<PointId>()
    queue.add(from)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        if (current == to) {
            // Reconstruct path, return intermediate points (excluding from and to)
            val path = mutableListOf<PointId>()
            var node = to
            while (node != from) {
                path.add(node)
                node = parent[node] ?: break
            }
            path.reverse()
            // via = intermediate points between from and destination (exclude destination itself)
            return path.dropLast(1)
        }
        for (neighbor in adjacency[current] ?: emptySet()) {
            if (neighbor !in visited) {
                visited.add(neighbor)
                parent[neighbor] = current
                queue.add(neighbor)
            }
        }
    }
    return null // no path found
}

/** Derive sequence info from runway duty queue for this aircraft. */
private fun deriveSequenceInfo(acId: AircraftId, ctx: OperatorContext): SequenceInfo? {
    val duty = ctx.beliefs.runwayDuty ?: return null
    // Position = 1 if holder, 2+ if in queue
    val pos = if (duty.holder == acId) 1
    else {
        val queueIdx = duty.queue.indexOfFirst { it.aircraft == acId }
        if (queueIdx < 0) return null
        queueIdx + 2 // +1 for 0-indexed, +1 for holder ahead
    }
    if (pos <= 1) return null // first = no sequence info needed

    // Find who's ahead
    val ahead = if (duty.holder != null && duty.holder != acId) duty.holder
    else duty.queue.getOrNull(0)?.aircraft
    val behindTraffic = ahead?.let { resolveTrafficRef(it, ctx.beliefs) }
    return SequenceInfo(pos, behindTraffic)
}

/** Find relevant traffic for landing/takeoff clearances (preceding or conflicting). */
private fun findRelevantTraffic(acId: AircraftId, ctx: OperatorContext): TrafficInfo? {
    val duty = ctx.beliefs.runwayDuty ?: return null
    // If there's a holder and it's not us, that's relevant traffic
    val relevant = if (duty.holder != null && duty.holder != acId) duty.holder
    else duty.queue.firstOrNull { it.aircraft != acId }?.aircraft
    relevant ?: return null
    val ref = resolveTrafficRef(relevant, ctx.beliefs)
    val desc = describeTraffic(relevant, ctx.beliefs, ctx.worldIndex)
    val other = ctx.beliefs.trackedAircraft[relevant]
    val movement = other?.let { describeMovement(it, ctx) }
    return TrafficInfo(
        traffic = ref,
        description = desc,
        level = other?.altitude,
        movement = movement,
    )
}

/** Short "what the traffic is doing" phrase used in traffic information companions. */
private fun describeMovement(
    other: AircraftObservation,
    ctx: OperatorContext,
): String {
    val onRunway = other.entities.any { it is xyz.easiersaid.twr.core.world.EntityRef.RunwayRef }
    val legs = ctx.worldIndex.circuitLegsByPoint[other.position] ?: emptySet()
    val legPhrase = legs.firstOrNull()?.name?.lowercase()
    return when {
        onRunway && other.onGround && other.pilotGoal == xyz.easiersaid.twr.controller.PilotGoal.DEPART -> "rolling"
        onRunway && other.onGround -> "on the runway"
        !other.onGround && legPhrase != null -> legPhrase
        !other.onGround -> "airborne"
        else -> "taxiing"
    }
}
