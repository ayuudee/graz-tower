package xyz.easiersaid.twr.pilot.observe

import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.MissionStep
import xyz.easiersaid.twr.pilot.PilotMission
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AircraftId

/**
 * Sealed pilot proactive-event channel — parallel to `ControllerEvent`
 * in `:controller/observe`. Pass 16 (D-AUDIT.9 partial closure) introduced
 * the architectural shape with [DecisionAltitudeWithoutClearance];
 * fn-12.2 (G3a-obstruction) added [AtcGoAroundOnFinal] as the second leaf.
 *
 * **Current leaf set (2 leaves)**:
 *  - [DecisionAltitudeWithoutClearance] — pilot has descended to or below
 *    decision altitude without a landing clearance (self-initiated GA
 *    trigger).
 *  - [AtcGoAroundOnFinal] — ATC issued `Instruction.GoAround` and
 *    `handleGoAround` recorded the pre-rewrite on-final step on the
 *    mission flag (ATC-reactive GA trigger).
 *
 * Future leaves land with their consumers (filed as
 * D-AUDIT.9.II–V-FOLLOWUP) — the sealed shape is open to extension via
 * additional leaves, each with its own recognition site and response
 * function.
 *
 * **Two recognition axes** (per fn-12.2 G3a-obstruction):
 *  - **Self-initiated events** are derived purely from `(AircraftState,
 *    PilotMission)` by [derivePilotEvent] — observation-to-event derivation
 *    with no time dependency today. [DecisionAltitudeWithoutClearance] is
 *    derived here.
 *  - **Post-cognitive flag-driven events** are constructed at decision time
 *    in `pilotDecide` from transient mission flags written by the cognitive
 *    layer (`pilotCognitiveDecide` / `processInstruction`). The recognition
 *    site is `pilotDecide`, NOT [derivePilotEvent], because the trigger
 *    state — e.g. [PilotMission.pendingAtcGoAroundFrom] — is set by the
 *    cognitive cycle that just ran. [AtcGoAroundOnFinal] is constructed
 *    here: ATC issued `Instruction.GoAround`, `processInstruction`
 *    rewrote the mission tree, and `handleGoAround` stamped the
 *    pre-rewrite step onto the mission for the recognition arm in
 *    `pilotDecide` to consume.
 *
 * **Recognition vs response**: response (event → mission update +
 * transmission/intent) is `Pilot.applySelfInitiatedGoAround` for the
 * self-initiated path and `Pilot.applyAtcInitiatedGoAround` for the
 * ATC-initiated reactive path. Spec tests pin both stages independently.
 *
 * **Doctrine**:
 *  - [DecisionAltitudeWithoutClearance]: CAP 413 §4.55 (continue approach
 *    vs go-around decision-altitude discipline). Response transmission
 *    cites ICAO Doc 4444 §7.10.2 (missed approach / go-around).
 *  - [AtcGoAroundOnFinal]: CAP 413 §4.65 (pilot compliance with ATC
 *    go-around instruction); ICAO Doc 4444 §7.4.1.4.1(c) (controller's
 *    runway-incursion / obstruction-driven GA).
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

    /**
     * fn-12.2 (G3a-obstruction): the pilot's reactive recognition that ATC
     * has issued a `GoAround` instruction during one of the on-final
     * eligible steps `{FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE,
     * LAND}` while the aircraft is in Circuit-mode and `phase = Final`.
     * Constructed at the recognition site in `pilotDecide` (NOT in
     * [derivePilotEvent]) from the transient mission flag
     * [PilotMission.pendingAtcGoAroundFrom] written by `handleGoAround`
     * BEFORE the mission-tree rewrite.
     *
     * Carries `originalStep` for diagnostic clarity — the step the aircraft
     * was on when the GA instruction arrived. The Tick A response
     * (`applyAtcInitiatedGoAround`) does not consume this field; the
     * intent override (`route = None`, `phase = Final` retained) is
     * unconditional.
     */
    data class AtcGoAroundOnFinal(
        override val aircraft: AircraftId,
        val originalStep: MissionStep,
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
