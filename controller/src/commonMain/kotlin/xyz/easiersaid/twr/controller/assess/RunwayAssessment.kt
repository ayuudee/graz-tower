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
import xyz.easiersaid.twr.protocol.WakeCategory

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
    /**
     * When the last runway operation completed (holder released). Used for wake
     * turbulence time-based separation on successive departures (2/3-minute rules).
     * Null until first operation completes.
     */
    val lastOperationCompletedAt: SimTime? = null,
    /** Wake category of the last aircraft that completed a runway operation. */
    val lastOperationWakeCategory: WakeCategory? = null,
    /**
     * When the current departure holder commenced takeoff roll. ICAO Doc 4444 §5.8
     * measures time-based wake separation from commencement of roll, not from clearing
     * the runway. Set when the departure holder is first observed moving on the runway.
     */
    val takeoffRollStartedAt: SimTime? = null,
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
@Suppress("LongParameterList") // Public entry point for 3-phase transformer; bundling premature
fun updateRunwayDuty(
    prev: RunwayDutyState?,
    activeRunway: RunwayId?,
    beliefs: BeliefState,
    commitments: Map<AircraftId, Commitment>,
    events: List<ControllerEvent>,
    time: SimTime,
    worldIndex: WorldIndex? = null,
    arrivalSequence: ArrivalSequence? = null,
): RunwayDutyState? {
    val runway = activeRunway ?: return null
    val ctx = RunwayDutyCtx(beliefs, commitments, events, time, worldIndex, arrivalSequence)
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
    /** Arrival sequence to project into the duty queue. Null if no sequence state yet. */
    val arrivalSequence: ArrivalSequence?,
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
    val holderWake = ctx.beliefs.trackedAircraft[holder]?.wakeCategory
    return touched.copy(
        holder = null, operation = null, holderReachedRunway = false,
        queue = requeued + touched.queue,
        lastOperationCompletedAt = ctx.time,
        lastOperationWakeCategory = holderWake,
    )
}

/**
 * Phase 2 — Admit newly-eligible aircraft into the queue, prune stale entries, resort.
 *
 * Arrivals are projected from [ArrivalSequence] (if available), NOT self-enqueued.
 * Departures/crossings/backtracks still self-enqueue through [departureQueueEntry].
 */
private fun enqueuePhase(state: RunwayDutyState, ctx: RunwayDutyCtx): RunwayDutyState {
    val existingInQueue = state.queue.map { it.aircraft }.toSet()

    // Non-arrival entries from commitments (departures, crossings, backtracks).
    val newNonArrivalEntries = ctx.commitments.mapNotNull { (acId, commitment) ->
        if (acId == state.holder || acId in existingInQueue) null
        else departureQueueEntry(acId, commitment, ctx)
    }

    // Arrival entries projected from ArrivalSequence (closest to threshold first).
    val arrivalEntries = projectArrivalsFromSequence(ctx.arrivalSequence, state.holder, existingInQueue, ctx)

    // Prune entries for commitments that no longer exist.
    val prunedQueue = state.queue.filter { it.aircraft in ctx.commitments.keys }

    // Sort: landing > takeoff, then FIFO.
    val fullQueue = (prunedQueue + newNonArrivalEntries + arrivalEntries).sortedWith(
        compareBy<RunwayQueueEntry> { if (it.operation == RunwayOperation.ARRIVAL) 0 else 1 }
            .thenBy { it.requestedAt.millis }
    )
    return state.copy(queue = fullQueue)
}

/**
 * Non-arrival queue entries from commitments (departures only for now).
 * The TOWER_ARRIVAL branch has been removed — arrivals come from ArrivalSequence projection.
 */
private fun departureQueueEntry(
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

    else -> null
}

/**
 * Project arrivals from [ArrivalSequence] into runway queue entries.
 *
 * Only arrivals on base/final gates enter the duty queue — downwind arrivals are
 * in the sequence but not yet competing for the runway. This preserves the ICAO
 * 4444 §7.10 principle: arrivals queue when close to the runway, not from downwind.
 */
private fun projectArrivalsFromSequence(
    sequence: ArrivalSequence?,
    holder: AircraftId?,
    existingInQueue: Set<AircraftId>,
    ctx: RunwayDutyCtx,
): List<RunwayQueueEntry> {
    if (sequence == null) return emptyList()
    return sequence.slots.mapNotNull { slot ->
        if (slot.aircraft == holder || slot.aircraft in existingInQueue) return@mapNotNull null
        val commitment = ctx.commitments[slot.aircraft] ?: return@mapNotNull null
        // Commitment must be at AwaitApproach — matching the old arrivalQueueEntry semantics.
        // At AwaitDownwind the procedures haven't advanced the commitment to runway competition.
        if (commitment.kind == CommitmentKind.TOWER_ARRIVAL &&
            commitment.stage != TowerArrivalStage.AwaitApproach) return@mapNotNull null
        // Only project when on base or final (close to runway).
        val isCloseToRunway = when (slot.gate) {
            is ArrivalGate.BaseTurn, is ArrivalGate.Final, is ArrivalGate.LocaliserEstablished -> true
            is ArrivalGate.Downwind, is ArrivalGate.Inbound -> false
        }
        if (!isCloseToRunway) return@mapNotNull null
        RunwayQueueEntry(slot.aircraft, RunwayOperation.ARRIVAL, commitment.formedAt)
    }
}

/** Phase 3 — If nobody holds the runway, hand it to the head of the queue. */
private fun grantPhase(state: RunwayDutyState, ctx: RunwayDutyCtx): RunwayDutyState {
    if (state.holder != null || state.queue.isEmpty()) return state
    val granted = state.queue.first()

    // Wake timer check: if previous operation was a DEPARTURE with a known wake category,
    // ensure time-based wake separation has elapsed. Only applies when the last operation
    // was a departure (ICAO §5.8 time-based wake for successive runway operations).
    // Conservative: measures from release, not roll commencement.
    val heavyPrecedingOp = state.lastOperationCompletedAt != null &&
        state.lastOperationWakeCategory != null &&
        (state.lastOperationWakeCategory == WakeCategory.J || state.lastOperationWakeCategory == WakeCategory.H)
    if (heavyPrecedingOp) {
        val followerWake = ctx.beliefs.trackedAircraft[granted.aircraft]?.wakeCategory
        val required = requiredWakeSeparation(state.lastOperationWakeCategory, followerWake)
        // Only enforce when the required time exceeds standard (extra wake delay needed).
        if (required.timeMinutes > STANDARD_TIME_MINUTES) {
            val elapsedMs = ctx.time.millis - state.lastOperationCompletedAt.millis
            val requiredMs = (required.timeMinutes * 60_000).toLong()
            if (elapsedMs < requiredMs) return state // wait — wake separation not yet met
        }
    }

    // Departure gap analysis: if the queue head is an arrival but a departure is waiting,
    // and the inter-arrival gap is large enough for a departure roll, grant the departure
    // instead. Gated behind: lead arrival > 4nm from threshold (safe margin).
    val grantTarget = if (granted.operation == RunwayOperation.ARRIVAL) {
        val departure = state.queue.firstOrNull { it.operation == RunwayOperation.DEPARTURE }
        if (departure != null) {
            val gapOk = checkDepartureGap(granted.aircraft, state.queue, ctx)
            if (gapOk) departure else null
        } else null
    } else null

    val actualGranted = grantTarget ?: granted
    val ac = ctx.beliefs.trackedAircraft[actualGranted.aircraft]
    val alreadyOnRunway = ac != null && ac.entities.any { it is EntityRef.RunwayRef }
    return state.copy(
        holder = actualGranted.aircraft,
        operation = actualGranted.operation,
        queue = state.queue.filter { it.aircraft != actualGranted.aircraft },
        holderReachedRunway = alreadyOnRunway,
    )
}

/**
 * Check if there's a sufficient gap between the lead arrival and the next arrival
 * to slot a departure. Requires: lead arrival > 4nm from threshold AND inter-arrival
 * spacing > 60 seconds (estimated departure roll + initial climb time).
 */
private fun checkDepartureGap(
    leadArrival: AircraftId,
    queue: List<RunwayQueueEntry>,
    ctx: RunwayDutyCtx,
): Boolean {
    val sequence = ctx.arrivalSequence ?: return false
    val leadSlot = sequence.slots.firstOrNull { it.aircraft == leadArrival } ?: return false
    val leadDistM = leadSlot.distanceToThresholdM ?: return false
    val leadDistNm = metresToNm(leadDistM)

    // Lead arrival must be > 4nm from threshold (safe margin for departure roll).
    if (leadDistNm < 4.0) return false

    // Find the next arrival in the queue after the lead.
    val nextArrival = queue.drop(1).firstOrNull { it.operation == RunwayOperation.ARRIVAL }
    if (nextArrival != null) {
        val nextSlot = sequence.slots.firstOrNull { it.aircraft == nextArrival.aircraft }
        val spacing = nextSlot?.spacingAheadSeconds
        // Inter-arrival gap must be > 60 seconds (typical departure roll + initial climb).
        if (spacing != null && spacing < 60.0) return false
    }

    return true
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

/**
 * Select runway closest to into-wind. Returns null when no decision is
 * possible — empty runway set, or no wind report has been received yet.
 *
 * The caller (typically `withActiveRunway` in the controller decide loop)
 * carries the null forward as "no active runway selected"; downstream
 * logic that needs an active runway must defer instruction issuance
 * rather than substituting an arbitrary fallback.
 */
fun selectRunwayIntoWind(runways: Set<RunwayId>, wind: xyz.easiersaid.twr.controller.WindReport): RunwayId? {
    if (runways.isEmpty()) return null
    return when (wind) {
        is xyz.easiersaid.twr.controller.WindReport.NotReported -> null
        is xyz.easiersaid.twr.controller.WindReport.Available -> {
            // Parse runway heading from ID (e.g., "09" → 90°, "27" → 270°)
            runways.minByOrNull { rwy ->
                val rwyHeading = rwy.value.takeWhile { it.isDigit() }.toIntOrNull()?.times(10) ?: 0
                val windDir = wind.wind.directionDegrees
                val diff = kotlin.math.abs(rwyHeading - windDir)
                minOf(diff, 360 - diff)
            }
        }
    }
}
