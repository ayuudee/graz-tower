package xyz.easiersaid.twr.controller.assess

import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.bdi.TowerDepartureStage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.ControllerEvent
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.*

data class RunwayDutyState(
    val runway: RunwayId,
    val holder: AircraftId? = null,
    val operation: RunwayOperation? = null,
    val queue: List<RunwayQueueEntry> = emptyList(),
    /**
     * Set once the current holder has been observed on a runway point. Used to tell
     * "arrival still on short final" (not yet reached) from "arrival lifted off again
     * after touchdown" (T&G, late go-around without report, or normal vacate).
     * Resets when [holder] changes.
     */
    val holderReachedRunway: Boolean = false,
)

enum class RunwayOperation { DEPARTURE, ARRIVAL, CROSSING, BACKTRACK }

data class RunwayQueueEntry(
    val aircraft: AircraftId,
    val operation: RunwayOperation,
    val requestedAt: SimTime,
)

/**
 * Update runway duty state as a pipeline of three pure transformers:
 * release → enqueue → grant. Each phase is a total `RunwayDutyState -> RunwayDutyState`
 * closure over a shared read-only [RunwayDutyCtx] bundle.
 *
 * Port of TWR1's three-phase runway duty logic with entity-aware checks.
 */
fun updateRunwayDuty(
    prev: RunwayDutyState?,
    activeRunway: RunwayId?,
    beliefs: BeliefState,
    commitments: Map<AircraftId, Commitment>,
    events: List<ControllerEvent>,
    time: SimTime,
    worldIndex: WorldIndex? = null,
): RunwayDutyState? {
    val runway = activeRunway ?: return null
    val ctx = RunwayDutyCtx(beliefs, commitments, events, time, worldIndex)
    val initial = prev?.takeIf { it.runway == runway } ?: RunwayDutyState(runway)
    return initial
        .let { releasePhase(it, ctx) }
        .let { enqueuePhase(it, ctx) }
        .let { grantPhase(it, ctx) }
}

/** Read-only inputs shared across the three phases. */
private data class RunwayDutyCtx(
    val beliefs: BeliefState,
    val commitments: Map<AircraftId, Commitment>,
    val events: List<ControllerEvent>,
    val time: SimTime,
    val worldIndex: WorldIndex?,
)

/**
 * Phase 1 — Release the current holder if the operation is complete.
 *
 * Release rules per runway operation (2026-04-16 fix for Bug A):
 *   DEPARTURE: airborne = gone.
 *   ARRIVAL:   depends on whether the aircraft has reached the runway.
 *     - Not yet on the runway (still on approach): never release — arrival is inbound.
 *     - Has reached the runway and is now off it: release. This covers normal vacate
 *       (on-ground, off-runway), touch-and-go (airborne again), and late go-around
 *       without a pilot report.
 *   CROSSING / BACKTRACK: release once off the runway.
 *   A reported go-around (GoAroundDetected event) always releases, independent of operation.
 *   A holder whose commitment has been pruned releases (defensive; stops stuck-holder bugs).
 *   Priority preemption: a queued arrival on base/final preempts a departure that hasn't
 *   yet entered the runway (ICAO 4444 §7.10 — landing traffic has priority).
 */
@Suppress("CyclomaticComplexMethod")
private fun releasePhase(state: RunwayDutyState, ctx: RunwayDutyCtx): RunwayDutyState {
    val holder = state.holder ?: return state
    val ac = ctx.beliefs.trackedAircraft[holder]
    val holderCommitment = ctx.commitments[holder]
    val onRunway = ac != null && ac.entities.any { it is EntityRef.RunwayRef }
    val offRunway = ac != null && !onRunway

    // Track whether holder has touched the runway this tenure.
    val touched = if (onRunway && !state.holderReachedRunway)
        state.copy(holderReachedRunway = true) else state

    val preemptedByArrival = state.operation == RunwayOperation.DEPARTURE &&
        !onRunway && ac?.onGround == true &&
        ctx.commitments.any { (acId, c) ->
            acId != holder &&
                c.kind == CommitmentKind.TOWER_ARRIVAL &&
                c.stage == TowerArrivalStage.AwaitApproach &&
                arrivalCommittedToRunway(ctx.beliefs.trackedAircraft[acId], ctx.worldIndex)
        }

    val shouldRelease = when {
        ac == null -> true // disappeared from beliefs
        holderCommitment == null -> true // commitment terminated; don't keep the runway
        ctx.events.any { it is ControllerEvent.GoAroundDetected && it.aircraft == holder } -> true
        state.operation == RunwayOperation.DEPARTURE && !ac.onGround -> true
        state.operation == RunwayOperation.ARRIVAL -> touched.holderReachedRunway && offRunway
        state.operation == RunwayOperation.CROSSING ||
            state.operation == RunwayOperation.BACKTRACK -> offRunway
        preemptedByArrival -> true
        else -> false
    }
    if (!shouldRelease) return touched

    // On preemption, put the displaced departure back in the queue so it keeps
    // its place behind the arrival rather than getting re-enqueued from scratch.
    val requeued = if (preemptedByArrival && holderCommitment != null)
        listOf(RunwayQueueEntry(holder, RunwayOperation.DEPARTURE, holderCommitment.formedAt))
    else emptyList()
    return touched.copy(
        holder = null, operation = null, holderReachedRunway = false,
        queue = requeued + touched.queue,
    )
}

/** Phase 2 — Admit newly-eligible aircraft into the queue, prune stale entries, resort. */
private fun enqueuePhase(state: RunwayDutyState, ctx: RunwayDutyCtx): RunwayDutyState {
    val existingInQueue = state.queue.map { it.aircraft }.toSet()
    val newEntries = ctx.commitments.mapNotNull { (acId, commitment) ->
        if (acId == state.holder || acId in existingInQueue) null
        else queueEntryFor(acId, commitment, ctx)
    }
    // Prune entries for commitments that no longer exist.
    val prunedQueue = state.queue.filter { it.aircraft in ctx.commitments.keys }
    // Sort: landing > takeoff, then FIFO.
    val fullQueue = (prunedQueue + newEntries).sortedWith(
        compareBy<RunwayQueueEntry> { if (it.operation == RunwayOperation.ARRIVAL) 0 else 1 }
            .thenBy { it.requestedAt.millis }
    )
    return state.copy(queue = fullQueue)
}

private fun queueEntryFor(
    acId: AircraftId,
    commitment: Commitment,
    ctx: RunwayDutyCtx,
): RunwayQueueEntry? = when {
    commitment.kind == CommitmentKind.TOWER_DEPARTURE &&
        commitment.stage == TowerDepartureStage.AwaitReady &&
        (ctx.events.any { it is ControllerEvent.ReadyForDepartureReceived && it.aircraft == acId } ||
            ctx.beliefs.trackedAircraft[acId]?.humanPiloted == false) ->
        RunwayQueueEntry(acId, RunwayOperation.DEPARTURE, ctx.time)

    commitment.kind == CommitmentKind.TOWER_DEPARTURE &&
        (commitment.stage == TowerDepartureStage.AwaitLineUpObserved ||
            commitment.stage == TowerDepartureStage.AwaitTakeoffObserved) ->
        RunwayQueueEntry(acId, RunwayOperation.DEPARTURE, commitment.formedAt)

    commitment.kind == CommitmentKind.TOWER_ARRIVAL &&
        commitment.stage == TowerArrivalStage.AwaitApproach ->
        arrivalQueueEntry(acId, commitment, ctx)

    else -> null
}

private fun arrivalQueueEntry(
    acId: AircraftId,
    commitment: Commitment,
    ctx: RunwayDutyCtx,
): RunwayQueueEntry? {
    val ac = ctx.beliefs.trackedAircraft[acId] ?: return null
    if (ac.onGround) return null
    val onApproach = ac.entities.any { it is EntityRef.ApproachRef }
    val circuitLegs = ctx.worldIndex?.circuitLegsByPoint?.get(ac.position) ?: emptySet()
    val onBaseOrFinal = LegName.BASE in circuitLegs || LegName.FINAL in circuitLegs
    // Only enqueue when close to runway (base/final/approach), not on downwind.
    return if (onApproach || onBaseOrFinal)
        RunwayQueueEntry(acId, RunwayOperation.ARRIVAL, commitment.formedAt)
    else null
}

/** Phase 3 — If nobody holds the runway, hand it to the head of the queue. */
private fun grantPhase(state: RunwayDutyState, ctx: RunwayDutyCtx): RunwayDutyState {
    if (state.holder != null || state.queue.isEmpty()) return state
    val granted = state.queue.first()
    val ac = ctx.beliefs.trackedAircraft[granted.aircraft]
    val alreadyOnRunway = ac != null && ac.entities.any { it is EntityRef.RunwayRef }
    return state.copy(
        holder = granted.aircraft,
        operation = granted.operation,
        queue = state.queue.drop(1),
        holderReachedRunway = alreadyOnRunway,
    )
}

/** Is this arrival physically committed to the runway (on approach or base/final leg)? */
private fun arrivalCommittedToRunway(
    ac: xyz.easiersaid.twr.controller.AircraftObservation?,
    worldIndex: WorldIndex?,
): Boolean {
    if (ac == null || ac.onGround) return false
    val onApproach = ac.entities.any { it is EntityRef.ApproachRef }
    val legs = worldIndex?.circuitLegsByPoint?.get(ac.position) ?: emptySet()
    return onApproach || LegName.BASE in legs || LegName.FINAL in legs
}

/** Select runway closest to into-wind. Simple wind-direction matching. */
fun selectRunwayIntoWind(runways: Set<RunwayId>, wind: Wind?): RunwayId? {
    if (runways.isEmpty()) return null
    if (wind == null) return runways.first()
    // Parse runway heading from ID (e.g., "09" → 90°, "27" → 270°)
    return runways.minByOrNull { rwy ->
        val rwyHeading = rwy.value.takeWhile { it.isDigit() }.toIntOrNull()?.times(10) ?: 0
        val windDir = wind.directionDegrees
        val diff = kotlin.math.abs(rwyHeading - windDir)
        minOf(diff, 360 - diff)
    }
}
