package xyz.easiersaid.twr.controller.assess

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.SeparationAssessment
import xyz.easiersaid.twr.controller.observe.SeparationConcern
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.protocol.Urgency

/**
 * Intervention type selected by the separation engine.
 *
 * Hierarchy from least to most disruptive:
 *   1. Speed control (PROGRESSION)
 *   2. Path extension — extend downwind / make long approach (PROGRESSION)
 *   3. Orbit (TIME_SENSITIVE)
 *   4. Go-around (SAFETY)
 */
sealed interface Intervention {
    val urgency: Urgency

    /** Reduce follower speed to increase spacing. */
    data object SpeedControl : Intervention {
        override val urgency = Urgency.PROGRESSION
    }

    /** Extend downwind or make long approach. */
    data object PathExtension : Intervention {
        override val urgency = Urgency.PROGRESSION
    }

    /** Hold / orbit. Sequence has broken down beyond linear delay. */
    data object OrbitHold : Intervention {
        override val urgency = Urgency.TIME_SENSITIVE
    }

    /** Go-around. Mandatory at VIOLATION level. */
    data object GoAround : Intervention {
        override val urgency = Urgency.SAFETY
    }
}

/**
 * Select the least-disruptive intervention for a separation concern.
 *
 * Pure function. Checks skip predicates first (go straight to go-around when
 * no lesser intervention is feasible), then tries each level in order.
 *
 * Returns null for COMFORTABLE, MONITORING, and DELEGATED concerns.
 */
@Suppress("ReturnCount") // guard-clause pattern — each check is an early return
fun selectIntervention(
    assessment: SeparationAssessment,
    follower: AircraftObservation,
    beliefs: BeliefState,
): Intervention? {
    if (assessment.concern == SeparationConcern.Severity.COMFORTABLE ||
        assessment.concern == SeparationConcern.Severity.MONITORING ||
        assessment.concern == SeparationConcern.Delegated) return null

    // ── Skip predicates: go directly to go-around ──────────────────
    if (assessment.concern == SeparationConcern.Severity.VIOLATION) return Intervention.GoAround

    val legs = beliefs.trackedAircraft[follower.id]?.let { ac ->
        // We need worldIndex for leg check but don't have it here — use the
        // arrivalSequence gate as a proxy for position.
        beliefs.arrivalSequence?.slots?.firstOrNull { it.aircraft == ac.id }?.gate
    }

    // Inside final approach (FAF or closer) — no room for speed/extend/orbit.
    val insideFinal = legs is ArrivalGate.Final &&
        (legs.phase == FinalPhase.FAF || legs.phase == FinalPhase.INSIDE_FAF)
    if (insideFinal) return Intervention.GoAround

    // Insufficient margin with high closure — can't decelerate in time.
    val highClosureNoMargin = assessment.closureRateKt != null &&
        assessment.closureRateKt > 30.0 &&
        assessment.currentSeparationNm != null &&
        (assessment.currentSeparationNm - assessment.requiredSeparationNm) < 1.0
    if (highClosureNoMargin) return Intervention.GoAround

    // ── Normal hierarchy: try least disruptive first ───────────────

    // Speed control: aircraft must be airborne and not already at minimum speed.
    val speedFeasible = !follower.onGround && follower.groundSpeed != null &&
        follower.groundSpeed.value > 70 // above minimum approach speed
    if (speedFeasible) return Intervention.SpeedControl

    // Path extension: aircraft must be on downwind or early approach.
    val onDownwind = legs is ArrivalGate.Downwind
    val onEarlyApproach = legs is ArrivalGate.BaseTurn || legs is ArrivalGate.Inbound
    if (onDownwind || onEarlyApproach) return Intervention.PathExtension

    // Orbit: available when not on final.
    val notOnFinal = legs !is ArrivalGate.Final && legs !is ArrivalGate.LocaliserEstablished
    if (notOnFinal) return Intervention.OrbitHold

    // Final fallback: go-around.
    return Intervention.GoAround
}
