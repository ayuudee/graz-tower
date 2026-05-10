@file:Suppress("MaxLineLength") // override fun resolve(ac, commitment, ctx): Either<...> signature
// repeats across every RuleAction; tabular form keeps them grep-able and visually aligned.
// Wrapping each signature would add 4 lines × ~12 actions of noise without changing comprehension.

package xyz.easiersaid.twr.controller.bdi

import arrow.core.Either
import arrow.core.right
import arrow.core.left
import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.assess.RunwayOperation
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ConditionalPredicate
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ContinueApproachReason
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldPositionCancelTakeoff
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.JoinType
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.ReportWhen
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiToStand
import xyz.easiersaid.twr.protocol.TrafficAction
import xyz.easiersaid.twr.protocol.TrafficRef
import xyz.easiersaid.twr.protocol.TurnBase

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
    val dispatch: Dispatch,
    val sequenceInfo: SequenceInfo? = null,
    val trafficInfo: TrafficInfo? = null,
) {
    val instruction: AtcInstruction get() = dispatch.instruction
    val aircraft: AircraftId get() = instruction.target
}

/** Convenience constructor for the common direct-dispatch case. */
fun ProposedAction(
    instruction: AtcInstruction,
    sequenceInfo: SequenceInfo? = null,
    trafficInfo: TrafficInfo? = null,
): ProposedAction = ProposedAction(
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
            else -> ProposedAction(LineUpAndWait(ac.id, rwy)).right()
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
        val trafficRef = resolveTrafficRef(occupant, ctx.beliefs)
        // Classify the runway occupant by the controller's belief about its
        // service intent, not by reading the pilot's internal goal.
        // Arriving aircraft (or circuit traffic that has declared T&G — they
        // will land then lift off again) appear as LANDING traffic to the
        // conditional clearance. Defaults to DEPARTING when intent is unknown.
        val occupantIntent = ctx.intentOf(occupant)
        val occupantIsCircuit = occupant in ctx.beliefs.circuitIntent
        val action = if (occupantIntent == xyz.easiersaid.twr.protocol.AircraftIntent.Arriving ||
            occupantIsCircuit)
            TrafficAction.LANDING else TrafficAction.DEPARTING
        return ProposedAction(
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
            else -> ProposedAction(ClearedForTakeoff(ac.id, rwy)).right()
        }
}

data object ClearLandAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val rwy = commitment.runway ?: ctx.beliefs.activeRunway
            ?: return ActionResolutionFailure("No runway").left()
        val traffic = findRelevantTraffic(ac.id, ctx)
        return ProposedAction(ClearedToLand(ac.id, rwy), trafficInfo = traffic).right()
    }
}

data object ClearTouchAndGoAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        when (val rwy = commitment.runway ?: ctx.beliefs.activeRunway) {
            null -> ActionResolutionFailure("No runway").left()
            else -> ProposedAction(ClearedTouchAndGo(ac.id, rwy)).right()
        }
}

data object HoldPositionAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(HoldPosition(ac.id)).right()
}

data object CancelTakeoffAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(HoldPositionCancelTakeoff(ac.id)).right()
}

data object GoAroundAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(GoAround(ac.id)).right()
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
            ProposedAction(AfterLandingVacateVia(ac.id, exit.point)).right()
        } else {
            ProposedAction(BacktrackRunway(ac.id, runway)).right()
        }
    }
}

data object TurnBaseAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(TurnBase(ac.id)).right()
}

data object ReportFinalAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(ReportWhen(ac.id, ReportEvent.Final)).right()
}

data object ExtendDownwindAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val seqInfo = deriveSequenceInfo(ac.id, ctx)
        return ProposedAction(ExtendDownwind(ac.id), sequenceInfo = seqInfo).right()
    }
}

data object ContinueApproachAction : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ProposedAction(ContinueApproach(ac.id, inferContinueApproachReason(ac, ctx))).right()
}

/**
 * G2 Phase I: tower issues join-circuit instructions for off-pattern arriving
 * traffic (cross-aerodrome arrivals from a published REP, or unsequenced
 * inbounds outside the local pattern).
 *
 * The [joinType] is supplied explicitly at the rule site — there is no
 * doctrine-defaulted ICAO/EU "default join position" (per atc-law review:
 * Doc 9432 Ch.4 / SERA.3225 leave the position to controller discretion;
 * CAP 413's OVERHEAD JOIN default is UK-specific). Phase I rule sites
 * pass `JoinType.DOWNWIND` for the LJMB scenario; future variations
 * (STRAIGHT_IN, BASE, OVERHEAD) require new rule sites with their own
 * doctrine commentary.
 *
 * The action reads the destination's published circuit data
 * (`world.aerodromes[ctx.view.aerodromeId].circuits`) and selects the
 * circuit matching the commitment's runway. The circuit's
 * `direction` (LEFT_HAND / RIGHT_HAND) is propagated to the issued
 * `JoinCircuit` instruction so the pilot navigates the correct pattern
 * geometry. Two failure paths are surfaced as
 * `Either.Left(ActionResolutionFailure)`:
 *  - no runway derivable from commitment + beliefs (defensive — every
 *    AwaitDownwind commitment is constructed with a runway today);
 *  - no published circuit for the runway (a world-data defect — the
 *    aerodrome publishes the runway but lacks circuit geometry).
 */
data class JoinCircuitAction(val joinType: JoinType) : RuleAction {
    override fun resolve(
        ac: AircraftObservation,
        commitment: Commitment,
        ctx: OperatorContext,
    ): Either<ActionResolutionFailure, ProposedAction> {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway
            ?: return ActionResolutionFailure(
                "JoinCircuitAction: no runway derivable for ${ac.id} (commitment=${commitment.runway}, " +
                    "beliefs.activeRunway=${ctx.beliefs.activeRunway})",
            ).left()
        val circuit = ctx.world.aerodromes[ctx.view.aerodromeId]
            ?.circuits
            ?.values
            ?.firstOrNull { it.runway == runway }
            ?: return ActionResolutionFailure(
                "JoinCircuitAction: no circuit published for runway $runway at ${ctx.view.aerodromeId}; " +
                    "world-data defect — the aerodrome publishes the runway but no circuit geometry.",
            ).left()
        return ProposedAction(
            JoinCircuit(
                target = ac.id,
                circuitDirection = circuit.direction,
                joinType = joinType,
                runway = runway,
            ),
        ).right()
    }
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

/**
 * Hand the aircraft off to another role at the same aerodrome.
 *
 * Pass 6 (D-AUDIT.12): introduces the published-vs-staffed distinction.
 * Pass 6 post-impl (Impact-M.2): the staffing check moved to the rule-level
 * `IsTransferTargetStaffed` guard so the rule itself doesn't fire when
 * the target is unstaffed. This action is then total over its inputs —
 * the rule guards have done the gating. The action's only failure modes
 * left are world-config defects (aerodrome missing or role unpublished),
 * which would never reach a runtime sim that has been validated.
 */
data class HandoffAction(val toRole: RoleName) : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> {
        val aerodrome = ctx.world.aerodromes[ctx.view.aerodromeId]
            ?: return ActionResolutionFailure("Aerodrome ${ctx.view.aerodromeId} not found").left()
        val role = aerodrome.roles[toRole]
            ?: return ActionResolutionFailure("No ${toRole.name} role at ${ctx.view.aerodromeId}").left()
        return ProposedAction(ContactFrequency(ac.id, toRole, frequency = role.frequency)).right()
    }
}

/**
 * Pass 7 (D-PF.7 closure): emit `RadarServiceTerminated` when the next
 * controller is unstaffed and the aircraft has reached the CTR boundary.
 * Mirrors [HandoffAction]'s shape but produces no peer transition — the
 * sim drops the aircraft from the sender's responsibilities once the
 * pilot reads back.
 *
 * [forRole] names the role this action releases — i.e. the role that
 * *would have* received the handoff. Used by `BoundaryReleaseFirewallTest`
 * (E17) to verify every `HandoffAction(role)` rule has a sibling
 * `TerminateRadarServiceAction(forRole=role)` rule.
 */
data class TerminateRadarServiceAction(
    val forRole: RoleName,
    val suggestedFrequency: arrow.core.Option<xyz.easiersaid.twr.protocol.Frequency> = arrow.core.None,
    val squawk: arrow.core.Option<xyz.easiersaid.twr.protocol.Squawk> = arrow.core.None,
) : RuleAction {
    override fun resolve(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Either<ActionResolutionFailure, ProposedAction> =
        ProposedAction(
            xyz.easiersaid.twr.protocol.RadarServiceTerminated(
                target = ac.id,
                suggestedFrequency = suggestedFrequency,
                squawk = squawk,
            ),
        ).right()
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
        return ProposedAction(
            TaxiToHoldingPoint(target = ac.id, destination = destination, runway = runway, via = via),
        ).right()
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
        return ProposedAction(
            TaxiToStand(target = ac.id, destination = destination, via = via),
        ).right()
    }
}

/** Construct a TrafficRef for an aircraft from beliefs. Prefers callsign. */
fun resolveTrafficRef(acId: AircraftId, beliefs: xyz.easiersaid.twr.controller.observe.BeliefState): TrafficRef {
    val ac = beliefs.trackedAircraft[acId]
    return TrafficRef.ByCallsign(ac?.callsign ?: Callsign(acId.value))
}

/**
 * Describe traffic for phraseology: e.g. "on final" or just callsign.
 *
 * Previously included aircraft type ("Cessna on final"), pulled from
 * [AircraftObservation.typeDescription] which was always null and was a
 * firewall-leak shape. Type description must come from a typed channel
 * (radio "we are a Cessna 172" or registration database) when authored.
 */
fun describeTraffic(acId: AircraftId, beliefs: xyz.easiersaid.twr.controller.observe.BeliefState, worldIndex: xyz.easiersaid.twr.core.world.WorldIndex): String {
    val ac = beliefs.trackedAircraft[acId] ?: return acId.value
    val leg = worldIndex.circuitLegsByPoint[ac.position]?.firstOrNull()?.name?.lowercase() ?: ""
    return if (leg.isNotEmpty()) "on $leg" else ac.callsign.value
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
    val intent = ctx.intentOf(other.id)
    val isDeparting = intent == xyz.easiersaid.twr.protocol.AircraftIntent.Departing
    return when {
        onRunway && other.onGround && isDeparting -> "rolling"
        onRunway && other.onGround -> "on the runway"
        !other.onGround && legPhrase != null -> legPhrase
        !other.onGround -> "airborne"
        else -> "taxiing"
    }
}
