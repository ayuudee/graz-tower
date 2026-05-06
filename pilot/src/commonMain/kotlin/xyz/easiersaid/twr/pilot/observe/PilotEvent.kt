package xyz.easiersaid.twr.pilot.observe

import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.MissionStep
import xyz.easiersaid.twr.pilot.PilotMission
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AircraftId

/**
 * Sealed pilot proactive-event channel — parallel to `ControllerEvent`
 * in `:controller/observe`. Pass 16 (D-AUDIT.9 partial closure) lands
 * the architectural shape with one leaf:
 * [DecisionAltitudeWithoutClearance]. Future leaves land with their
 * consumers (filed as D-AUDIT.9.II–V-FOLLOWUP).
 *
 * **Recognition vs response**: derivation (observation → event) is
 * pure and total over `(AircraftState, PilotMission)` — no time
 * dependency today; the next time-dependent leaf adds the parameter.
 * Response (event → mission update + transmission) is
 * `Pilot.applySelfInitiatedGoAround` (renamed from
 * `checkSelfInitiatedGoAround`). Spec tests pin both stages
 * independently.
 *
 * **Doctrine**: CAP 413 §4.55 (continue approach vs go-around
 * decision-altitude discipline — the pilot's recognition predicate).
 * The transmission produced by the response stage cites
 * ICAO Doc 4444 §7.10.2 (missed approach / go-around — the
 * controller-side response).
 */
sealed interface PilotEvent {
    val aircraft: AircraftId

    /**
     * Pilot has descended to or below decision altitude without a
     * landing clearance. The proactive response is a self-initiated
     * go-around (subtree replacement + GOING_AROUND transmission).
     * `currentStep` is non-null — the derivation function narrows
     * `mission.currentTask?.step` at entry.
     */
    data class DecisionAltitudeWithoutClearance(
        override val aircraft: AircraftId,
        val altitudeM: Double,
        val currentStep: MissionStep,
    ) : PilotEvent
}

/** Decision altitude threshold — at or below this without clearance triggers go-around. */
const val DECISION_ALTITUDE_M: Double = 100.0

/**
 * Pure, total: derive any [PilotEvent] the pilot should fire this
 * tick. Today returns at most one
 * [PilotEvent.DecisionAltitudeWithoutClearance]; when more leaves
 * land, this becomes `List<PilotEvent>` and consumers shift to a
 * sealed `when`-fold.
 *
 * **Trigger predicate** (CAP 413 §4.55):
 *  - mission's current step is one of {AWAIT_LANDING_CLEARANCE,
 *    REPORT_FINAL, FLY_FINAL, FLY_BASE, REPORT_BASE};
 *  - mission has no landing clearance;
 *  - aircraft altitude is at or below [DECISION_ALTITUDE_M] (closed
 *    inclusive — a regression to half-open would silently drop edge
 *    triggers; pinned by the boundary spec row);
 *  - aircraft phase is not `LandingRoll` or `Vacating` (post-touchdown
 *    states do not trigger); the explicit phase guard makes the
 *    pre-Pass-16 `0.01` lower altitude bound redundant — dropped
 *    (post-impl Impact S2 fold);
 *  - current step is not already `GOING_AROUND` or
 *    `AWAITING_ATC_INSTRUCTION` (re-fire prevention; both halves of
 *    the IFR/VFR variants pinned by the re-fire spec row).
 */
fun derivePilotEvent(
    aircraft: AircraftState,
    mission: PilotMission,
): PilotEvent? {
    val step = mission.currentTask?.step ?: return null
    val onApproach = step == MissionStep.AWAIT_LANDING_CLEARANCE ||
        step == MissionStep.REPORT_FINAL || step == MissionStep.FLY_FINAL ||
        step == MissionStep.FLY_BASE || step == MissionStep.REPORT_BASE
    if (!onApproach || mission.hasClearance) return null
    if (aircraft.altitudeM > DECISION_ALTITUDE_M) return null
    if (aircraft.phase is PilotPhase.LandingRoll || aircraft.phase is PilotPhase.Vacating) return null
    if (step == MissionStep.GOING_AROUND || step == MissionStep.AWAITING_ATC_INSTRUCTION) return null
    return PilotEvent.DecisionAltitudeWithoutClearance(
        aircraft = aircraft.id,
        altitudeM = aircraft.altitudeM,
        currentStep = step,
    )
}
