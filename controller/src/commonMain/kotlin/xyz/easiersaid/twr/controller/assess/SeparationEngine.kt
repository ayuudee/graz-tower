package xyz.easiersaid.twr.controller.assess

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.ObservationSnapshot
import xyz.easiersaid.twr.controller.observe.SeparationAssessment
import xyz.easiersaid.twr.controller.observe.SeparationConcern
import xyz.easiersaid.twr.controller.observe.isSeverityAtLeast
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.WakeCategory

/**
 * Phase A — Early separation assessment (belief source).
 *
 * Runs after `updateArrivalSequence`, before `executeProcedures`.
 * Computes [SeparationAssessment] for every relevant pair and writes
 * them into [BeliefState.separationAssessments]. Procedure guards read
 * these assessments instead of computing their own distances.
 */
@Suppress("UnusedParameter") // worldIndex consumed when position-based separation supplements distance
fun assessSeparation(
    beliefs: BeliefState,
    worldIndex: WorldIndex?,
): List<SeparationAssessment> {
    val arrivals = beliefs.arrivalSequence?.slots ?: emptyList()
    if (arrivals.size < 2) return emptyList()

    // All pairs, not just consecutive — a go-around can remove an aircraft mid-sequence,
    // making previously non-adjacent pairs suddenly adjacent. Lower index = leader (closer
    // to threshold, since the sequence is sorted by distance).
    val assessments = mutableListOf<SeparationAssessment>()
    for (i in arrivals.indices) {
        for (j in i + 1 until arrivals.size) {
            val assessment = assessPair(
                arrivals[i].aircraft, arrivals[j].aircraft,
                beliefs, worldIndex,
            )
            if (assessment != null) assessments.add(assessment)
        }
    }
    // Apply hysteresis: concern can only drop severity after cooldown.
    return assessments.map { applyHysteresis(it, beliefs) }
}

/**
 * Assess separation between two aircraft.
 *
 * Returns null when insufficient data (either aircraft not tracked).
 * Uses absolute-margin comfort computation with closure-rate adjustment.
 */
private fun assessPair(
    leaderId: AircraftId,
    followerId: AircraftId,
    beliefs: BeliefState,
    @Suppress("UnusedParameter") worldIndex: WorldIndex?, // reserved for position-based supplemental checks
): SeparationAssessment? {
    val leaderAc = beliefs.trackedAircraft[leaderId] ?: return null
    val followerAc = beliefs.trackedAircraft[followerId] ?: return null

    // Required separation: wake-based if applicable, otherwise radar minimum.
    val wake = requiredWakeSeparation(leaderAc.wakeCategory, followerAc.wakeCategory)
    val requiredNm = wake.distanceNm

    // Current separation: from arrival sequence distances.
    val leaderDist = beliefs.arrivalSequence?.slots?.firstOrNull { it.aircraft == leaderId }?.distanceToThresholdM
    val followerDist = beliefs.arrivalSequence?.slots?.firstOrNull { it.aircraft == followerId }?.distanceToThresholdM
    val currentSeparationNm = if (leaderDist != null && followerDist != null) {
        metresToNm(followerDist - leaderDist)
    } else null

    // Closure rate from observation history.
    val closureRateKt = computeClosureRate(leaderId, followerId, beliefs)

    // Comfort computation.
    // Derive positional + visual-sep inputs for the comfort gradient.
    val followerSlot = beliefs.arrivalSequence?.slots?.firstOrNull { it.aircraft == followerId }
    val followerDistNm = followerSlot?.distanceToThresholdM?.let { metresToNm(it) }
    val visualSepApplied = followerSlot?.followTarget?.acquisitionState == AcquisitionState.VISUAL_SEPARATION_APPLIED

    val concern = computeConcern(currentSeparationNm, requiredNm, closureRateKt, followerDistNm, visualSepApplied)

    // Time to minimum (if converging).
    val timeToMin = if (currentSeparationNm != null && closureRateKt != null && closureRateKt > 0) {
        val excessNm = currentSeparationNm - requiredNm
        if (excessNm > 0) (excessNm / closureRateKt) * 3600.0 else 0.0 // seconds
    } else null

    return SeparationAssessment(
        aircraft = leaderId,
        other = followerId,
        currentSeparationNm = currentSeparationNm,
        requiredSeparationNm = requiredNm,
        closureRateKt = closureRateKt,
        timeToMinimumSeconds = timeToMin,
        concern = concern,
    )
}

// ── Comfort computation ──────────────────────────────────────────────

/**
 * Closure-rate factor: NM of additional margin required per knot of closure.
 * 40kt closure → 0.8 NM additional buffer required.
 */
private const val CLOSURE_FACTOR = 0.02

/** Thresholds in NM of effective margin. Parameterised for tuning. */
/**
 * Apply hysteresis: if the new concern is less severe than the recent concern and
 * the cooldown hasn't elapsed, keep the old (higher) severity. Prevents oscillation.
 */
private fun applyHysteresis(
    assessment: SeparationAssessment,
    beliefs: BeliefState,
): SeparationAssessment {
    val recent = beliefs.recentConcerns[assessment.other] ?: return assessment
    val newConcern = assessment.concern
    val oldConcern = recent.concern

    // Only apply hysteresis between Severity levels (not Delegated).
    if (newConcern !is SeparationConcern.Severity || oldConcern !is SeparationConcern.Severity) return assessment

    // If new is less severe and cooldown hasn't elapsed, keep old.
    if (newConcern.level < oldConcern.level) {
        // Keep the higher concern — cooldown is enforced by Controller.kt's updateRecentConcerns
        // which only allows de-escalation after CONCERN_COOLDOWN_MS has elapsed.
        return assessment.copy(concern = oldConcern)
    }
    return assessment
}

/** Base thresholds in NM of effective margin. Tightened by positional factor when close to threshold. */
internal const val COMFORTABLE_THRESHOLD = 2.0
internal const val MONITORING_THRESHOLD = 1.0
internal const val VIOLATION_THRESHOLD = 0.0

/** Factor for tightening thresholds when close to runway threshold. At 2nm final, thresholds halve. */
private const val POSITIONAL_TIGHTENING_FACTOR = 0.5
private const val POSITIONAL_TIGHTENING_RANGE_NM = 4.0

/**
 * Compute the controller's separation concern for a pair.
 *
 * This is the most safety-critical function in the system. `internal` for direct testability.
 * Uses absolute margin (NM) with closure-rate adjustment and positional tightening.
 *
 * Positional context: thresholds tighten when the follower is close to the runway threshold,
 * because there is less room to correct. At 2nm final, thresholds are halved.
 */
internal fun computeConcern(
    currentNm: Double?,
    requiredNm: Double,
    closureRateKt: Double?,
    distanceToThresholdNm: Double?,
    visualSeparationApplied: Boolean,
): SeparationConcern {
    if (visualSeparationApplied) return SeparationConcern.Delegated
    if (currentNm == null) return SeparationConcern.Severity.MONITORING // insufficient data = be cautious

    val margin = currentNm - requiredNm
    val closureAdjustment = if (closureRateKt != null && closureRateKt > 0) closureRateKt * CLOSURE_FACTOR else 0.0
    val effectiveMargin = margin - closureAdjustment

    // Positional tightening: closer to threshold = tighter thresholds.
    val positionalFactor = if (distanceToThresholdNm != null && distanceToThresholdNm < POSITIONAL_TIGHTENING_RANGE_NM) {
        POSITIONAL_TIGHTENING_FACTOR + (1.0 - POSITIONAL_TIGHTENING_FACTOR) * (distanceToThresholdNm / POSITIONAL_TIGHTENING_RANGE_NM)
    } else 1.0

    return when {
        effectiveMargin > COMFORTABLE_THRESHOLD * positionalFactor -> SeparationConcern.Severity.COMFORTABLE
        effectiveMargin > MONITORING_THRESHOLD * positionalFactor -> SeparationConcern.Severity.MONITORING
        effectiveMargin > VIOLATION_THRESHOLD -> SeparationConcern.Severity.INTERVENTION
        else -> SeparationConcern.Severity.VIOLATION
    }
}

// ── Closure rate ─────────────────────────────────────────────────────

/**
 * Compute closure rate between two aircraft from observation history.
 * Positive = converging (follower catching up to leader).
 * Returns null when insufficient history.
 */
private fun computeClosureRate(
    leaderId: AircraftId,
    followerId: AircraftId,
    beliefs: BeliefState,
): Double? {
    val leaderHistory = beliefs.previousPositions[leaderId] ?: return null
    val followerHistory = beliefs.previousPositions[followerId] ?: return null
    if (leaderHistory.size < 2 || followerHistory.size < 2) return null

    // Use the two most recent snapshots for each.
    val leaderSpeed = estimateGroundSpeedKt(leaderHistory)
    val followerSpeed = estimateGroundSpeedKt(followerHistory)
    if (leaderSpeed == null || followerSpeed == null) return null

    // Closure = follower speed minus leader speed (positive = converging).
    return followerSpeed - leaderSpeed
}

/** Estimate ground speed in knots from the latest observation snapshot. */
private fun estimateGroundSpeedKt(history: List<ObservationSnapshot>): Double? {
    if (history.size < 2) return null
    // Use only the latest snapshot's groundSpeed — stale fallback would bias toward false precision.
    return history.last().groundSpeed?.value?.toDouble()
}

// ── Phase B — Reactive safety net ────────────────────────────────────

/**
 * Phase B — Post-arbitration reactive safety net.
 *
 * Inspects separation assessments and committed outputs. If any pair is at
 * INTERVENTION or VIOLATION level and no committed output addresses it,
 * returns reactive interventions to inject. SAFETY interventions bypass
 * the arbitrator budget.
 *
 * At most one SAFETY action per aircraft per cycle (dedup with procedure outputs).
 */
/**
 * Returns assessment + selected intervention pairs for unaddressed concerns.
 * Atomic: detection and action selection happen together so callers can't drop a VIOLATION.
 */
fun reactiveInterventions(
    beliefs: BeliefState,
    committedOutputs: List<ControllerOutput.Instruct>,
): List<Pair<SeparationAssessment, Intervention>> {
    val safetyAircraft = committedOutputs
        .filter { it.urgency == xyz.easiersaid.twr.protocol.Urgency.SAFETY }
        .map { it.target }.toSet()

    return beliefs.separationAssessments.mapNotNull { assessment ->
        if (!assessment.concern.isSeverityAtLeast(SeparationConcern.Severity.INTERVENTION)) return@mapNotNull null
        if (assessment.other in safetyAircraft) return@mapNotNull null // dedup
        val follower = beliefs.trackedAircraft[assessment.other] ?: return@mapNotNull null
        val intervention = selectIntervention(assessment, follower, beliefs) ?: return@mapNotNull null
        assessment to intervention
    }
}

/**
 * Emit concrete [ControllerOutput] for reactive interventions.
 *
 * Maps [Intervention] → instruction:
 *   - GoAround → [BreakOff] if runway clear (phraseology: ATC-initiated discontinue),
 *                 [GoAround] if runway occupied.
 *   - SpeedControl/PathExtension/OrbitHold → not emitted reactively (procedure rules handle).
 *
 * Each reactive output is paired with [TrafficInformation] per Doc 4444 §5.10.1.1.
 */
fun emitReactiveOutputs(
    interventions: List<Pair<SeparationAssessment, Intervention>>,
    beliefs: BeliefState,
): List<ControllerOutput> {
    return interventions.flatMap { (assessment, intervention) ->
        val target = assessment.other
        val instruction: xyz.easiersaid.twr.protocol.AtcInstruction = when (intervention) {
            is Intervention.GoAround -> {
                val runwayClear = beliefs.runwayBeliefs.values.all {
                    it.status == xyz.easiersaid.twr.controller.RunwayStatus.CLEAR
                }
                if (runwayClear) xyz.easiersaid.twr.protocol.BreakOff(
                    target,
                    missedApproachInstructions = listOf(
                        xyz.easiersaid.twr.protocol.ClimbTo(target, xyz.easiersaid.twr.protocol.Level.AltitudeFeet.unsafe(2000)),
                    ),
                )
                else xyz.easiersaid.twr.protocol.GoAround(target)
            }
            // Non-SAFETY interventions are handled by procedure rules, not emitted reactively.
            is Intervention.SpeedControl, is Intervention.PathExtension, is Intervention.OrbitHold ->
                return@flatMap emptyList()
        }

        val instruct = ControllerOutput.Instruct(
            target = target,
            dispatch = xyz.easiersaid.twr.controller.bdi.Dispatch.Direct(instruction),
            urgency = intervention.urgency,
            trace = xyz.easiersaid.twr.controller.DecisionTrace(
                ruleId = "REACTIVE-SEPARATION",
                description = "Reactive ${intervention::class.simpleName} — separation ${assessment.concern}",
                regulations = listOf(
                    xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_5,
                    xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_10_2,
                ),
            ),
        )

        // Companion traffic information (Doc 4444 §5.10.1.1).
        val trafficRef = xyz.easiersaid.twr.controller.bdi.resolveTrafficRef(assessment.aircraft, beliefs)
        val trafficInfo = ControllerOutput.Respond(
            target = target,
            response = xyz.easiersaid.twr.protocol.TrafficInformation(
                target = target,
                traffic = trafficRef,
                distanceNm = assessment.currentSeparationNm,
            ),
            trace = xyz.easiersaid.twr.controller.DecisionTrace(
                ruleId = "REACTIVE-TRAFFIC-INFO",
                description = "Traffic information accompanying separation intervention",
                regulations = listOf(xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_5),
            ),
        )

        listOf(instruct, trafficInfo)
    }
}
