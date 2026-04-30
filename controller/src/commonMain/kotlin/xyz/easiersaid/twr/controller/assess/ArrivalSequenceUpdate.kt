package xyz.easiersaid.twr.controller.assess

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayId
import kotlin.math.sqrt

/**
 * Update the arrival sequence as a pipeline step.
 *
 * Runs between reconcileCommitments and updateRunwayDuty so that the
 * duty queue can project from a freshly-updated sequence. Pure:
 * (ArrivalSequence?, BeliefState) -> ArrivalSequence?.
 */
fun updateArrivalSequence(
    prev: ArrivalSequence?,
    activeRunway: RunwayId?,
    beliefs: BeliefState,
    worldIndex: WorldIndex?,
): ArrivalSequence? {
    val runway = activeRunway ?: return null
    val sequence = prev?.takeIf { it.runway == runway } ?: ArrivalSequence.empty(runway)
    return deriveSequence(sequence, beliefs, worldIndex)
}

/**
 * Derive arrival slots from committed arrivals.
 *
 * Distance-only first increment: orders by distance to threshold along the
 * approach path. No speed/ETA/closure-rate yet — those are Phase 5b.3 refinements.
 *
 * Preserves stable numbers for aircraft already in the sequence.
 * New arrivals get the next available number at their natural position.
 */
private fun deriveSequence(
    current: ArrivalSequence,
    beliefs: BeliefState,
    worldIndex: WorldIndex?,
): ArrivalSequence {
    // 1. Find all committed arrivals (TOWER or APPROACH) that are airborne.
    val arrivalAircraft = beliefs.commitments.filter { (acId, c) ->
        (c.kind == CommitmentKind.TOWER_ARRIVAL || c.kind == CommitmentKind.APPROACH_ARRIVAL) &&
            !c.isComplete &&
            beliefs.trackedAircraft[acId]?.onGround != true
    }

    // 2. For each, compute distance and gate.
    val candidates = arrivalAircraft.mapNotNull { (acId, commitment) ->
        val ac = beliefs.trackedAircraft[acId] ?: return@mapNotNull null
        val gate = deriveGate(ac, worldIndex, beliefs)
        val distance = distanceToThreshold(ac, worldIndex, current.runway)
        CandidateSlot(acId, commitment, ac, gate, distance)
    }

    // 3. Sort by time-to-threshold when speed is available, distance when not.
    // Time = distance / groundSpeed. Aircraft with speed data sort by time; without, by distance.
    val sorted = candidates.sortedBy { candidate ->
        val distM = candidate.distanceM ?: Double.MAX_VALUE
        val speedKt = candidate.ac.groundSpeed?.value?.toDouble()
        if (speedKt != null && speedKt > 0) {
            val distNm = distM / METRES_PER_NM
            (distNm / speedKt) * 3600.0 // seconds to threshold
        } else distM // fallback: distance ordering (meters — puts no-speed aircraft after timed ones)
    }

    // 4. Assign stable numbers. Numbers increase monotonically in distance order but may
    //    have gaps when aircraft are removed (gaps are acceptable — the number is a contract).
    //    Existing numbers are preserved when the aircraft's relative position hasn't
    //    changed. When an aircraft is removed (go-around, unable) or inserted, trailing
    //    numbers shift — triggering re-emission of NumberInSequence.
    val existingNumbers = current.slots.associate { it.aircraft to it.stableNumber }
    val existingFollowTargets = current.slots.associate { it.aircraft to it.followTarget }

    // Previous ordering (by stable number) for detecting relative-order changes.
    val prevOrder = current.slots.sortedBy { it.stableNumber }.map { it.aircraft }
    val newOrder = sorted.map { it.acId }

    // Build slots with fold: thread nextNumber through each candidate.
    data class SlotAcc(val slots: List<ArrivalSlot>, val nextNum: Int)
    val result = sorted.fold(SlotAcc(emptyList(), 1)) { acc, candidate ->
        val number = if (candidate.acId in existingNumbers && relativeOrderPreserved(candidate.acId, prevOrder, newOrder))
            existingNumbers[candidate.acId]!! else acc.nextNum
        val slot = ArrivalSlot(
            aircraft = candidate.acId,
            stableNumber = number,
            followTarget = resolveFollowTarget(existingFollowTargets[candidate.acId]),
            distanceToThresholdM = candidate.distanceM,
            spacingAheadSeconds = computeSpacingAhead(candidate, acc.slots),
            gate = candidate.gate,
            approachMode = deriveApproachMode(candidate.ac, beliefs),
        )
        SlotAcc(acc.slots + slot, maxOf(acc.nextNum, number) + 1)
    }

    // 5. Detect re-sequenced aircraft (number changed) and new arrivals.
    val resequenced = result.slots.filter { slot ->
        val prev = existingNumbers[slot.aircraft]
        prev != null && prev != slot.stableNumber
    }.map { it.aircraft }.toSet()
    val newArrivals = result.slots.filter { it.aircraft !in existingNumbers }.map { it.aircraft }.toSet()

    return current.copy(
        slots = result.slots,
        resequencedAircraft = resequenced + newArrivals,
    )
}

/**
 * True if [aircraft] has the same relative position in both orderings.
 * An aircraft that was 2nd of 3 and is still 2nd of 3 (or 2nd of 2 after removal) is preserved.
 * An aircraft whose predecessor or successor changed gets a new number.
 */
/**
 * Clear a follow target when the pilot has reported unable.
 * Operationally: aircraft retains its sequence position but gets a new follow
 * target (or reverts to procedural separation). Clearing forces the controller
 * to re-assign on the next cycle.
 */
/** Compute seconds of spacing between this candidate and the preceding slot (if any). */
private fun computeSpacingAhead(candidate: CandidateSlot, precedingSlots: List<ArrivalSlot>): Double? {
    if (precedingSlots.isEmpty()) return null
    val ahead = precedingSlots.last()
    val myDistM = candidate.distanceM ?: return null
    val aheadDistM = ahead.distanceToThresholdM ?: return null
    val gapM = myDistM - aheadDistM
    if (gapM <= 0) return 0.0
    val speedKt = candidate.ac.groundSpeed?.value?.toDouble()
    return if (speedKt != null && speedKt > 0) {
        val gapNm = gapM / METRES_PER_NM
        (gapNm / speedKt) * 3600.0 // seconds
    } else null
}

private fun resolveFollowTarget(existing: FollowTarget?): FollowTarget? =
    if (existing?.acquisitionState == AcquisitionState.UNABLE) null else existing

private fun relativeOrderPreserved(aircraft: AircraftId, prevOrder: List<AircraftId>, newOrder: List<AircraftId>): Boolean {
    val prevIdx = prevOrder.indexOf(aircraft)
    val newIdx = newOrder.indexOf(aircraft)
    if (prevIdx < 0 || newIdx < 0) return false
    // Check predecessor is the same (or both are first).
    val prevPred = prevOrder.getOrNull(prevIdx - 1)
    val newPred = newOrder.getOrNull(newIdx - 1)
    return prevPred == newPred
}

private data class CandidateSlot(
    val acId: AircraftId,
    val commitment: Commitment,
    val ac: AircraftObservation,
    val gate: ArrivalGate,
    val distanceM: Double?,
)

// ── Gate derivation ──────────────────────────────────────────────────

/**
 * Map the aircraft's current circuit leg to an [ArrivalGate].
 *
 * Uses [WorldIndex.circuitLegsByPoint] to determine which leg the aircraft is on.
 * Falls back to [ArrivalGate.Inbound] when position is not on a known circuit leg.
 */
private fun deriveGate(ac: AircraftObservation, worldIndex: WorldIndex?, beliefs: BeliefState): ArrivalGate {
    // Localiser established takes precedence — it's orthogonal to distance/leg.
    if (ac.id in beliefs.establishedLocaliser) return ArrivalGate.LocaliserEstablished

    val legs = worldIndex?.circuitLegsByPoint?.get(ac.position) ?: emptySet()
    return when {
        LegName.FINAL in legs -> ArrivalGate.Final(FinalPhase.INTERCEPT)
        LegName.BASE in legs -> ArrivalGate.BaseTurn(BaseTurnPhase.INITIATED)
        LegName.DOWNWIND in legs -> ArrivalGate.Downwind(DownwindPhase.ABEAM)
        LegName.CROSSWIND in legs -> ArrivalGate.Inbound
        LegName.UPWIND in legs -> ArrivalGate.Inbound
        else -> ArrivalGate.Inbound
    }
}

// ── Distance computation ─────────────────────────────────────────────

/**
 * Compute path-remaining distance to threshold for an arrival.
 *
 * BFS from the aircraft's position to the threshold along the circuit adjacency
 * graph, summing segment distances point-to-point. Falls back to Euclidean
 * straight-line when no path is found (aircraft off the circuit graph).
 */
private fun distanceToThreshold(
    ac: AircraftObservation,
    worldIndex: WorldIndex?,
    runway: RunwayId,
): Double? {
    val positions = worldIndex?.positions ?: return null
    val acPos = positions[ac.position] ?: return null
    val threshold = worldIndex.thresholdByRunway[runway] ?: return null
    val thresholdPos = positions[threshold] ?: return null

    // Try path-following distance via adjacency BFS.
    val pathDistance = pathDistanceBfs(ac.position, threshold, worldIndex)
    return pathDistance ?: euclideanDistance(acPos, thresholdPos)
}

/**
 * Shortest-path distance from [from] to [to] along the adjacency graph (SPFA variant).
 * Returns null when no path exists. Returns dist[to] only after full queue exhaustion
 * to guarantee optimality — early return on dequeue is unsound for SPFA.
 */
@Suppress("LoopWithTooManyJumpStatements") // SPFA relaxation naturally has skip + re-enqueue
private fun pathDistanceBfs(from: PointId, to: PointId, worldIndex: WorldIndex): Double? {
    if (from == to) return 0.0
    val positions = worldIndex.positions
    val adjacency = worldIndex.adjacency
    val dist = mutableMapOf(from to 0.0)
    val queue = ArrayDeque<PointId>()
    queue.add(from)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val currentDist = dist[current] ?: continue
        val currentPos = positions[current] ?: continue

        for (neighbor in adjacency[current] ?: emptySet()) {
            val neighborPos = positions[neighbor] ?: continue
            val segmentDist = currentDist + euclideanDistance(currentPos, neighborPos)
            if (neighbor !in dist || segmentDist < dist[neighbor]!!) {
                dist[neighbor] = segmentDist
                queue.add(neighbor)
            }
        }
    }
    return dist[to] // null if unreachable
}

private fun euclideanDistance(a: Position, b: Position): Double {
    val dx = a.xMeters - b.xMeters
    val dy = a.yMeters - b.yMeters
    return sqrt(dx * dx + dy * dy)
}

// ── Approach mode derivation ─────────────────────────────────────────

/**
 * Derive approach mode from cleared approach type if available, else default
 * to VISUAL.
 *
 * Previously also checked `ac.flightRules == FlightRules.IFR` as a fallback,
 * but that field was always null (never populated) and was a firewall-leak
 * shape (would have to come from the pilot's filed flight plan via a typed
 * radio event, not from observation). When IFR support lands, restore the
 * fallback by reading a belief slice populated from a typed event.
 */
private fun deriveApproachMode(ac: AircraftObservation, beliefs: BeliefState): ApproachMode {
    // Check issuedClearances for an active approach clearance for this aircraft.
    val approachClearance = beliefs.issuedClearances.values.firstOrNull { clr ->
        clr.aircraft == ac.id && !clr.status.isTerminal &&
            (clr.instruction is xyz.easiersaid.twr.protocol.ClearedApproach ||
                clr.instruction is xyz.easiersaid.twr.protocol.ClearedVisualApproach)
    }
    return when {
        approachClearance?.instruction is xyz.easiersaid.twr.protocol.ClearedVisualApproach -> ApproachMode.VISUAL
        approachClearance?.instruction is xyz.easiersaid.twr.protocol.ClearedApproach -> {
            val typed = approachClearance.instruction as xyz.easiersaid.twr.protocol.ClearedApproach
            when (typed.approachType) {
                xyz.easiersaid.twr.protocol.ApproachType.ILS -> ApproachMode.ILS
                xyz.easiersaid.twr.protocol.ApproachType.LOC -> ApproachMode.LOC
                xyz.easiersaid.twr.protocol.ApproachType.RNAV, xyz.easiersaid.twr.protocol.ApproachType.RNP -> ApproachMode.RNAV
                else -> ApproachMode.ILS // VOR, NDB, SRA, PAR default to ILS separation for now
            }
        }
        else -> ApproachMode.VISUAL
    }
}
