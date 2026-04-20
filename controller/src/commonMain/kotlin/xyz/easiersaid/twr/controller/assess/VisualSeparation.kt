package xyz.easiersaid.twr.controller.assess

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Level

/**
 * Visual separation decision — can the controller apply visual separation?
 *
 * Conditions per Doc 4444 §5.11 and SERA.8005(b):
 * - Both aircraft visible (or one reports other in sight — checked via FollowTarget.TRAFFIC_IN_SIGHT)
 * - Geometry suitable: not converging, adequate lateral offset
 * - Not during LVP (SERA.5025)
 * - Below FL100
 *
 * Returns true if the controller may accept the reduced standard.
 * The actual transition to VISUAL_SEPARATION_APPLIED is a controller decision
 * that fires in the FollowTarget lifecycle, not automatically.
 */
@Suppress("ReturnCount") // guard-clause pattern — each condition is an early return
fun canApplyVisualSeparation(
    aircraft: AircraftId,
    other: AircraftId,
    view: ControllerView,
    beliefs: BeliefState,
): Boolean {
    // LVP: visual separation not applicable (SERA.5025).
    if (view.lvpMode) return false

    // Both aircraft must be tracked.
    val ac = beliefs.trackedAircraft[aircraft] ?: return false
    val otherAc = beliefs.trackedAircraft[other] ?: return false

    // Below FL100 (10,000 ft) — SERA.8005(b).
    if (isAboveFl100(ac) || isAboveFl100(otherAc)) return false

    // Pilot must have traffic in sight (checked via FollowTarget in ArrivalSequence).
    val slot = beliefs.arrivalSequence?.slots?.firstOrNull { it.aircraft == aircraft }
    val hasTrafficInSight = slot?.followTarget?.let {
        it.aircraft == other && (
            it.acquisitionState == AcquisitionState.TRAFFIC_IN_SIGHT ||
                it.acquisitionState == AcquisitionState.VISUAL_SEPARATION_APPLIED
            )
    } ?: false
    if (!hasTrafficInSight) return false

    // Geometry: not converging fast. The 20kt threshold is a sim heuristic (tuning parameter),
    // not an ICAO numeric condition. Doc 4444 §5.11 requires "satisfied that separation will be maintained."
    val assessment = beliefs.separationAssessments.firstOrNull { a ->
        (a.aircraft == aircraft && a.other == other) || (a.aircraft == other && a.other == aircraft)
    }
    val isConvergingFast = assessment?.closureRateKt?.let { it > 20.0 } ?: false
    if (isConvergingFast) return false

    return true
}

private fun isAboveFl100(ac: AircraftObservation): Boolean = when (val alt = ac.altitude) {
    is Level.FlightLevel -> alt.fl >= 100
    is Level.AltitudeFeet -> alt.feet >= 10000
    is Level.HeightFeet -> alt.feet >= 10000
    null -> false
}
