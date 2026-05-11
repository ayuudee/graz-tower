package xyz.easiersaid.twr.pilot.observe

import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.MissionStep
import xyz.easiersaid.twr.pilot.PilotMission
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.WindReport
import xyz.easiersaid.twr.protocol.headingDegreesMagnetic

/**
 * Sealed pilot proactive-event channel — parallel to `ControllerEvent`
 * in `:controller/observe`. Pass 16 (D-AUDIT.9 partial closure) introduced
 * the architectural shape with [DecisionAltitudeWithoutClearance];
 * fn-12.2 (G3a-obstruction) added [AtcGoAroundOnFinal] as the second leaf.
 *
 * **Current leaf set (3 leaves)**:
 *  - [DecisionAltitudeWithoutClearance] — pilot has descended to or below
 *    decision altitude without a landing clearance (self-initiated GA
 *    trigger).
 *  - [AtcGoAroundOnFinal] — ATC issued `Instruction.GoAround` and
 *    `handleGoAround` recorded the pre-rewrite on-final step on the
 *    mission flag (ATC-reactive GA trigger).
 *  - [CrosswindLimitExceeded] — world's reported wind on the active
 *    runway exceeds the aircraft type's POH-derived
 *    `maxCrosswindKnots` while on final (fn-14.1 G3a-react pilot-side
 *    reactive GA trigger). Pure derivation; recognition in
 *    [derivePilotEvent]'s crosswind branch.
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

    /**
     * fn-14.1 (G3a-react): the pilot's reactive recognition that the
     * world's reported wind on the active runway produces a crosswind
     * component exceeding the aircraft type's POH-derived
     * [xyz.easiersaid.twr.protocol.AircraftType.maxCrosswindKnots]
     * while the aircraft is on final.
     *
     * **Pure derivation** (axis 1 — self-initiated): the event is
     * constructed by [derivePilotEvent]'s crosswind branch from the
     * tuple `(aircraft, mission, weather: WindReport?)`. No mission
     * flag, no asynchronous arrival channel — distinct from
     * [AtcGoAroundOnFinal] (axis 2 — post-cognitive flag-driven).
     *
     * **Trigger predicate** (independent of DA branch — see
     * `derivePilotEvent` KDoc for guards):
     *  - `aircraft.phase is PilotPhase.Final`
     *  - `mission.currentStep ∈ {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}`
     *  - `weather is WindReport.Available`
     *  - `mission.activeRunway is Some`
     *  - `runway.headingDegreesMagnetic() != null` (fail-closed parse)
     *  - `crosswindComponentKnots(...) > aircraft.type.maxCrosswindKnots.value.toDouble()`
     *
     * **Note**: NOT clearance-gated (unlike DA branch). FAA AFH Ch 9
     * positions the pilot as having authority to GA on POH crosswind
     * regardless of clearance state.
     *
     * Carries [componentKnots] (Double — precise computed value) +
     * [limitKnots] (Int — POH value) + [runway] (RunwayId — trace
     * readability). The response stage `applyCrosswindGoAround` does
     * not consume these fields; the intent is unconditional. They are
     * load-bearing for trace coherence and future test assertions.
     *
     * **Doctrine**: FAA AFH (FAA-H-8083-3C) Chapter 9 Common Error #1;
     * 14 CFR §23.233(a); ICAO Annex 6 Part II §2.4 (PIC final
     * authority); FAA AIM §7-1-12.d.3 (wind reference frame).
     */
    data class CrosswindLimitExceeded(
        override val aircraft: AircraftId,
        val componentKnots: Double,
        val limitKnots: Int,
        val runway: RunwayId,
    ) : PilotEvent
}

/** Decision altitude threshold — at or below this without clearance triggers go-around. */
const val DECISION_ALTITUDE_M: Double = 100.0

/**
 * fn-14.1 (G3a-react): step set in which a crosswind-exceedance
 * triggers the pilot's reactive GA. Distinct from DA's onApproach
 * set: crosswind is final-only (FLY_BASE/REPORT_BASE are excluded —
 * a real PIC commits to the GA decision on final, when the
 * aerodynamic feel of crab vs slip becomes load-bearing). Includes
 * `LAND` (handleLandingClearance advances AWAIT_LANDING_CLEARANCE →
 * LAND on ClearedToLand — post-clearance crosswind exceedance is the
 * exact GA-POST-CLEAR sim scenario).
 */
private val CROSSWIND_ELIGIBLE_STEPS: Set<MissionStep> = setOf(
    MissionStep.FLY_FINAL,
    MissionStep.REPORT_FINAL,
    MissionStep.AWAIT_LANDING_CLEARANCE,
    MissionStep.LAND,
)

/**
 * Pure, total: derive any [PilotEvent] the pilot should fire this
 * tick. Returns at most one event — when both branches would fire
 * simultaneously, the lower-altitude DA branch wins (pinned by the
 * "both-trigger ordering" spec row).
 *
 * **fn-14.1 split-branch shape**: two independent branches, no shared
 * early returns. DA branch keeps its CAP 413 §4.55 gates; crosswind
 * branch (G3a-react) is a separate predicate with its own gates —
 * notably **NOT clearance-gated** (FAA AFH Ch 9: pilot has authority
 * for crosswind GA regardless of clearance state).
 *
 * The signature widening to `weather: WindReport?` is the only public-
 * shape change in fn-14.1 — single call site at `Pilot.kt:pilotDecide`
 * (verified by context-scout). Reads `aircraft.type.maxCrosswindKnots`
 * inside (NOT taken as a separate `aircraftType` parameter — that would
 * allow tests to pass mismatched type vs aircraft and produce
 * impossible runtime behavior).
 *
 * **DA branch trigger predicate** (CAP 413 §4.55):
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
 *
 * **Crosswind branch trigger predicate** (FAA AFH Ch 9, fn-14.1):
 *  - `aircraft.phase is PilotPhase.Final`
 *  - mission's current step in [CROSSWIND_ELIGIBLE_STEPS] —
 *    NOT clearance-gated (independent of `mission.hasClearance`)
 *  - `weather is WindReport.Available` — fail-closed on null /
 *    `NotReported`
 *  - `mission.activeRunway is Some` — a runway must be assigned for
 *    a crosswind component to be defined
 *  - `runway.headingDegreesMagnetic() != null` — fail-closed parse
 *  - `crosswindComponentKnots(...) > maxCrosswindKnots.toDouble()`
 */
fun derivePilotEvent(
    aircraft: AircraftState,
    mission: PilotMission,
    weather: WindReport?,
): PilotEvent? {
    // ── Branch 1: DA-without-clearance (existing fn-pre-14 behavior) ─────
    val daEvent = deriveDecisionAltitudeEvent(aircraft, mission)
    if (daEvent != null) return daEvent

    // ── Branch 2: crosswind exceedance (fn-14.1 new) ─────────────────────
    return deriveCrosswindEvent(aircraft, mission, weather)
}

/**
 * fn-14.1: DA branch extracted intact from the pre-fn-14.1 body. Pure;
 * no new behavior. Branch is mutually exclusive with crosswind in
 * practice — DA fires below 100 m with no clearance; crosswind needs
 * `phase = Final` (i.e. on the approach descent) but is not altitude-
 * or clearance-gated. When both apply (hypothetically: low + on-final
 * + uncleared + crosswind exceedance), DA wins per the split's
 * sequence — and is pinned by `PilotEventCrosswindTest`'s ordering
 * row.
 */
private fun deriveDecisionAltitudeEvent(
    aircraft: AircraftState,
    mission: PilotMission,
): PilotEvent.DecisionAltitudeWithoutClearance? {
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

/**
 * fn-14.1 (G3a-react): crosswind branch — independent gate set. All
 * inputs nullable / Optional; any null fails closed (no event). The
 * order of guards is structural-cheap-first (step in set) then
 * world-lookup (weather), then computed (crosswind component).
 *
 * `aircraft.type.maxCrosswindKnots` is read **inside** this function
 * from the live aircraft state — passing `aircraftType` as a separate
 * parameter would let a test desynchronise the two and produce
 * scenarios that cannot exist in production.
 */
@Suppress("ReturnCount") // guard-clause early returns enumerate fail-closed modes;
// folding into a single expression obscures which precondition failed.
private fun deriveCrosswindEvent(
    aircraft: AircraftState,
    mission: PilotMission,
    weather: WindReport?,
): PilotEvent.CrosswindLimitExceeded? {
    // Phase guard: only on final (during the approach descent itself).
    if (aircraft.phase !is PilotPhase.Final) return null

    // Step guard: final-eligible steps. Independent of mission.hasClearance.
    val step = mission.currentTask?.step ?: return null
    if (step !in CROSSWIND_ELIGIBLE_STEPS) return null

    // Weather guard: fail-closed on null + NotReported.
    val report = weather as? WindReport.Available ?: return null

    // Runway guard: need an assignment AND a parseable Magnetic heading.
    val assignment = mission.activeRunway.getOrNull() ?: return null
    val runway = assignment.runway
    val runwayHeading = runway.headingDegreesMagnetic() ?: return null

    // Pure crosswind component in Double.
    val component = crosswindComponentKnots(
        windFromMagnetic = report.wind.directionDegrees,
        windSpeedKnots = report.wind.speedKnots,
        runwayHeadingMagnetic = runwayHeading,
    )
    val limit = aircraft.type.maxCrosswindKnots.value
    if (component <= limit.toDouble()) return null

    return PilotEvent.CrosswindLimitExceeded(
        aircraft = aircraft.id,
        componentKnots = component,
        limitKnots = limit,
        runway = runway,
    )
}
