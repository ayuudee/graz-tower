package xyz.easiersaid.twr.pilot.observe

import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.DensityAltitudeInput
import xyz.easiersaid.twr.pilot.MissionStep
import xyz.easiersaid.twr.pilot.PilotMission
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.activeCompound
import xyz.easiersaid.twr.pilot.isCircuitLike
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.WindReport
import xyz.easiersaid.twr.protocol.headingDegreesMagnetic

/**
 * Sealed pilot proactive-event channel — parallel to `ControllerEvent`
 * in `:controller/observe`. Pass 16 (D-AUDIT.9 partial closure) introduced
 * the architectural shape with [DecisionAltitudeWithoutClearance];
 * fn-12.2 (G3a-obstruction) added [AtcGoAroundOnFinal] as the second leaf;
 * fn-14.1 (G3a-react crosswind) added [CrosswindLimitExceeded] as the third
 * leaf; fn-15.1 (G3a-react-tailwind) adds [TailwindLimitExceeded] as the
 * fourth leaf — the second pilot-side reactive recognition axis driven by
 * world weather.
 *
 * **Current leaf set (4 leaves)**:
 *  - [DecisionAltitudeWithoutClearance] — pilot has descended to or below
 *    decision altitude without a landing clearance (self-initiated GA
 *    trigger).
 *  - [AtcGoAroundOnFinal] — ATC issued `Instruction.GoAround` and
 *    `handleGoAround` recorded the pre-rewrite on-final step on the
 *    mission flag (ATC-reactive GA trigger).
 *  - [CrosswindLimitExceeded] — world's reported wind on the active
 *    runway produces a crosswind component exceeding the aircraft
 *    type's POH-derived
 *    [xyz.easiersaid.twr.protocol.AircraftType.maxCrosswindKnots] while
 *    on final (fn-14.1 G3a-react pilot-side reactive GA trigger). Pure
 *    derivation; recognition in [derivePilotEvent]'s crosswind branch.
 *    POH source: C172 = 15 kt per Cessna 172S NAV III POH §2
 *    ("Maximum demonstrated crosswind velocity is 15 knots — not a
 *    limitation"); B738 = 33 kt per Boeing 737-800 FCOM Limitations §1
 *    (steady crosswind on dry/grooved runway). Modelling note: POH
 *    "demonstrated crosswind" is performance information per 14 CFR
 *    §23.233(a) (pre-Amd 64) / FAA AC 23-8B, NOT a limitation in the
 *    certification sense; the sim treats the demonstrated value as the
 *    trigger per FAA AFH Ch 9 Common Error #1, with personal-minimums
 *    judgement layer deferred (`D-PASS-g3a-react-personal-minimums`).
 *    End-to-end sim coverage:
 *    [xyz.easiersaid.twr.sim.G3aPilotReactiveCrosswindTest] (fn-14.2).
 *  - [TailwindLimitExceeded] — world's reported wind on the active runway
 *    produces a tailwind component exceeding the aircraft type's
 *    [xyz.easiersaid.twr.protocol.AircraftType.maxTailwindKnots] while on
 *    final (fn-15.1 G3a-react-tailwind pilot-side reactive GA trigger).
 *    Pure derivation; recognition in [derivePilotEvent]'s tailwind branch.
 *    **Per-type doctrinal severity asymmetry**: C172 = 10 kt FAA AFH Ch 9
 *    industry-standard advisory (POH §2 does NOT publish a hard limit);
 *    B738 = 15 kt FCOM Limitations §1 hard operational limitation
 *    (dry runway). The recognition fires identically across types — the
 *    asymmetry is documented at
 *    [xyz.easiersaid.twr.protocol.AircraftType.maxTailwindKnots] KDoc
 *    only; the typed predicate `component > limit` is uniform. End-to-end
 *    sim coverage: [xyz.easiersaid.twr.sim.G3aPilotReactiveTailwindTest]
 *    (fn-15.2).
 *
 * Future leaves land with their consumers (filed as
 * D-AUDIT.9.II–V-FOLLOWUP) — the sealed shape is open to extension via
 * additional leaves, each with its own recognition site and response
 * function.
 *
 * **Two recognition axes** (per fn-12.2 G3a-obstruction):
 *  - **Self-initiated events** are derived purely from `(AircraftState,
 *    PilotMission)` (+ optional `WindReport` for the wind-axis branches)
 *    by [derivePilotEvent] — observation-to-event derivation with no time
 *    dependency today. [DecisionAltitudeWithoutClearance],
 *    [CrosswindLimitExceeded], and [TailwindLimitExceeded] are derived
 *    here.
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
 * transmission/intent) is `Pilot.applySelfInitiatedGoAround` for the DA
 * self-initiated path, `Pilot.applyAtcInitiatedGoAround` for the
 * ATC-initiated reactive path, `Pilot.applyCrosswindGoAround` for the
 * fn-14.1 crosswind-reactive path, and `Pilot.applyTailwindGoAround` for
 * the fn-15.1 tailwind-reactive path. Spec tests pin each stage
 * independently.
 *
 * **Doctrine**:
 *  - [DecisionAltitudeWithoutClearance]: CAP 413 §4.55 (continue approach
 *    vs go-around decision-altitude discipline). Response transmission
 *    cites ICAO Doc 4444 §7.10.2 (missed approach / go-around).
 *  - [AtcGoAroundOnFinal]: CAP 413 §4.64 (pilot compliance with ATC
 *    go-around instruction — Ed 24 numbering; formerly §4.65 in Ed 23,
 *    renumbered per fn-17.1); ICAO Doc 4444 §7.4.1.4.1(c) (controller's
 *    runway-incursion / obstruction-driven GA).
 *  - [CrosswindLimitExceeded]: FAA AFH Ch 9 Common Error #1; 14 CFR
 *    §23.233(a); ICAO Annex 6 Part II §2.4 (PIC final authority); FAA
 *    AIM §7-1-12.d.3 (wind reference frame).
 *  - [TailwindLimitExceeded]: FAA AFH Ch 9 (tailwind landings as high-risk
 *    operations — modelling anchor for the C172 advisory regime); ICAO
 *    Annex 6 Part II §2.4 (PIC final authority); FAA AIM §7-1-12.d.3
 *    (wind reference frame). Per-leaf source: C172 = FAA AFH Ch 9
 *    advisory; B738 = Boeing 737-800 FCOM Limitations §1 hard limit.
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

    /**
     * fn-15.1 (G3a-react-tailwind): the pilot's reactive recognition that
     * the world's reported wind on the active runway produces a tailwind
     * component exceeding the aircraft type's
     * [xyz.easiersaid.twr.protocol.AircraftType.maxTailwindKnots] while
     * the aircraft is on final. Second axis of pilot-reactive POH/AFH-
     * derived recognition; sibling of [CrosswindLimitExceeded].
     *
     * **Per-type doctrinal severity asymmetry** (load-bearing for KDoc;
     * recognition predicate is uniform):
     *  - C172: 10 kt is the **FAA AFH Ch 9 industry-standard advisory**
     *    (POH §2 does NOT publish a hard tailwind limitation); modelling
     *    rationale mirrors fn-14.1 crosswind (AC 23-8B-style "competent
     *    pilot goes around when the advisory is exceeded").
     *  - B738: 15 kt is the **Boeing 737-800 FCOM Limitations §1 hard
     *    operational limitation** (dry runway).
     *
     * **Pure derivation** (axis 1 — self-initiated): the event is
     * constructed by [derivePilotEvent]'s tailwind branch from the same
     * tuple `(aircraft, mission, weather: WindReport?)` consumed by the
     * crosswind branch. Branches are independent — no shared early
     * returns, no shared state. Trigger predicate is independent of the
     * crosswind branch (both can hold simultaneously; ordering pin in
     * `derivePilotEvent` makes tailwind fire first when both apply same
     * tick).
     *
     * **Trigger predicate** (independent of DA branch and of the
     * crosswind branch — see `derivePilotEvent` KDoc for guards):
     *  - `aircraft.phase is PilotPhase.Final`
     *  - `mission.currentStep ∈ {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}`
     *    (shared `WIND_REACTIVE_ELIGIBLE_STEPS` symbol with crosswind branch)
     *  - `mission.root.activeCompound()?.name.isCircuitLike()` —
     *    fail-closed mission-shape guard via `isReactiveGoAroundEligible`
     *    (shared symbol with crosswind branch).
     *  - `weather is WindReport.Available`
     *  - `mission.activeRunway is Some`
     *  - `runway.headingDegreesMagnetic() != null` (fail-closed parse)
     *  - `tailwindComponentKnots(...) > aircraft.type.maxTailwindKnots.value.toDouble()`
     *
     * **Note**: NOT clearance-gated (mirrors crosswind branch). FAA AFH
     * Ch 9 positions the pilot as having authority to GA on POH/AFH
     * tailwind regardless of clearance state — the touchdown-energy /
     * runway-remaining / go-around-margin physics do not depend on
     * whether a clearance has been issued.
     *
     * Carries [componentKnots] (Double — precise computed value) +
     * [limitKnots] (Int — POH/AFH value) + [runway] (RunwayId — trace
     * readability). The response stage `applyTailwindGoAround` does not
     * consume these fields; the intent is unconditional. They are
     * load-bearing for trace coherence and future test assertions
     * (telemetry / log parsing distinguishing tailwind from crosswind
     * triggers without re-deriving from `(componentKnots, limitKnots)`).
     *
     * **Doctrine**: FAA AFH (FAA-H-8083-3C) Chapter 9 (tailwind landings
     * as high-risk operations — modelling anchor for the C172 advisory
     * regime); Boeing 737-800 FCOM Limitations §1 (hard limit anchor
     * for the B738 leaf); ICAO Annex 6 Part II §2.4 (PIC final
     * authority); FAA AIM §7-1-12.d.3 (wind reference frame); ICAO Doc
     * 4444 §7.11.6 (peer doctrinal anchor — 5 kt tailwind for reduced
     * runway separation minima, scope distinct from POH performance).
     */
    data class TailwindLimitExceeded(
        override val aircraft: AircraftId,
        val componentKnots: Double,
        val limitKnots: Int,
        val runway: RunwayId,
    ) : PilotEvent
}

/** Decision altitude threshold — at or below this without clearance triggers go-around. */
const val DECISION_ALTITUDE_M: Double = 100.0

/**
 * fn-14.1 introduced this as `CROSSWIND_ELIGIBLE_STEPS`; fn-15.1
 * **renamed** to `WIND_REACTIVE_ELIGIBLE_STEPS` because the second
 * consumer landed (tailwind branch). Shared between the crosswind and
 * tailwind branches of [derivePilotEvent] — both pilot-reactive wind-
 * axis recognitions evaluate the same on-final step set.
 *
 * Distinct from DA's `onApproach` set: wind-reactive recognition is
 * final-only (FLY_BASE / REPORT_BASE are excluded — a real PIC commits
 * to the wind GA decision on final, when the aerodynamic feel of crab
 * vs slip (crosswind) and the touchdown-energy / runway-remaining /
 * go-around-margin physics (tailwind) become load-bearing). Includes
 * `LAND` (handleLandingClearance advances AWAIT_LANDING_CLEARANCE →
 * LAND on ClearedToLand — post-clearance wind exceedance is the exact
 * GA-POST-CLEAR sim scenario).
 *
 * If future doctrine diverges (e.g. tailwind eligible from base for
 * runway-remaining margin), the rename can split back into two sets;
 * sharing the symbol now (when the second consumer lands) is the right
 * "second-consumer landed, time to share" moment per
 * `feedback_pass_scope.md` discipline.
 */
private val WIND_REACTIVE_ELIGIBLE_STEPS: Set<MissionStep> = setOf(
    MissionStep.FLY_FINAL,
    MissionStep.REPORT_FINAL,
    MissionStep.AWAIT_LANDING_CLEARANCE,
    MissionStep.LAND,
)

/**
 * Pure, total: derive any [PilotEvent] the pilot should fire this
 * tick. Returns at most one event — when multiple branches would fire
 * simultaneously, the ordering below pins which surfaces.
 *
 * **fn-15.1 three-branch shape** (post fn-14.1 split): three independent
 * branches, no shared early returns. The DA branch keeps its CAP 413
 * §4.55 gates; the tailwind branch (fn-15.1 G3a-react-tailwind) and
 * the crosswind branch (fn-14.1 G3a-react) are wind-axis predicates
 * with their own gates — notably **NOT clearance-gated** (FAA AFH Ch 9:
 * pilot has authority for wind-reactive GA regardless of clearance
 * state).
 *
 * **Branch ordering** (doctrinally motivated per fn-15 Decision #5;
 * pinned by ordering tests):
 *
 *  1. **DA (lowest-altitude / hardest-stop trigger)** — CAP 413 §4.55
 *     decision-altitude discipline. When all three predicates
 *     simultaneously hold (low + on-final + uncleared + tailwind exceeded
 *     + crosswind exceeded), DA wins.
 *  2. **Tailwind (physically stronger constraint)** — tailwind affects
 *     touchdown energy, runway remaining, and go-around margin; on
 *     jet-class types like B738 it is doctrinally a hard limitation per
 *     FCOM Limitations §1. When only tailwind + crosswind hold (on-final
 *     + cleared + both winds exceeded), tailwind wins.
 *  3. **Crosswind (control-authority constraint)** — demonstrated
 *     performance per AC 23-8B (judgement-zone). When only crosswind
 *     holds, crosswind fires.
 *
 * The signature stays `derivePilotEvent(aircraft, mission, weather: WindReport?)`
 * — same as fn-14.1; the tailwind branch reuses the same `WindReport`
 * channel. Reads `aircraft.type.maxTailwindKnots` / `maxCrosswindKnots`
 * inside (NOT taken as separate parameters — that would allow tests to
 * pass mismatched type vs aircraft and produce impossible runtime
 * behavior).
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
 * **Tailwind branch trigger predicate** (FAA AFH Ch 9 / FCOM §1, fn-15.1):
 *  - `aircraft.phase is PilotPhase.Final`
 *  - mission's current step in [WIND_REACTIVE_ELIGIBLE_STEPS] —
 *    NOT clearance-gated (independent of `mission.hasClearance`)
 *  - mission-shape guard via [isReactiveGoAroundEligible] —
 *    `activeCompoundName.isCircuitLike()`
 *  - `weather is WindReport.Available` — fail-closed on null /
 *    `NotReported`
 *  - `mission.activeRunway is Some`
 *  - `runway.headingDegreesMagnetic() != null` — fail-closed parse
 *  - `tailwindComponentKnots(...) > maxTailwindKnots.toDouble()`
 *
 * **Crosswind branch trigger predicate** (FAA AFH Ch 9, fn-14.1):
 *  - `aircraft.phase is PilotPhase.Final`
 *  - mission's current step in [WIND_REACTIVE_ELIGIBLE_STEPS] —
 *    NOT clearance-gated (independent of `mission.hasClearance`)
 *  - mission-shape guard via [isReactiveGoAroundEligible] —
 *    `activeCompoundName.isCircuitLike()`
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
    /**
     * fn-28.1 (G3a-react-density-altitude foundation A): typed
     * density-altitude input for the aerodrome the pilot's mission
     * concerns. **Signature-only at fn-28.1** — the DA-decline branch
     * lands in fn-28.2 (along with
     * `AircraftType.maxDensityAltitudeFt`, the recognition gate, and
     * the `replaceFromActivePrimitive([PrimitiveTask(DECLINE_DEPARTURE,
     * NON_COMPLETING)])` apply path). Default `null` preserves all
     * existing call sites; fn-28.1's `pilotDecide` call-site update
     * passes the projected entry via `mission.goal` → aerodrome
     * resolution (see Pilot.kt).
     *
     * Per R21 (round-6 branch order): the DA-decline branch slots
     * BETWEEN `DecisionAltitudeWithoutClearance` (existing) and the
     * fn-28.4-or-later `AbortTakeoff` branch (not present today).
     * That branch is intentionally NOT wired here — landing a no-op
     * branch with no recognition predicate would create a
     * compile-clean dead arm. The placeholder is the parameter
     * threading only.
     */
    densityAltitudeInput: DensityAltitudeInput? = null,
): PilotEvent? {
    // fn-28.1: `densityAltitudeInput` is signature-threaded but
    // intentionally unread in this version of the function body — wiring
    // a no-op branch with no recognition predicate would create a
    // compile-clean dead arm. fn-28.2 lands the
    // `deriveDensityAltitudeDeclineEvent(aircraft, mission, densityAltitudeInput)`
    // branch and slots it between the DA-without-clearance and tailwind
    // branches per R21's branch order:
    //   DecisionAltitudeWithoutClearance → DensityAltitudeDecline →
    //   AbortTakeoff → TailwindLimitExceeded → CrosswindLimitExceeded.
    // The parameter's default `null` preserves every pre-fn-28.1 call
    // site; the firewall-clean type ([DensityAltitudeInput]) records the
    // typed contract at the public API. The `_pinTypeContract` reference
    // below is structural — it pins the type at the recognition site so
    // a future regression that loses the parameter (e.g. an accidental
    // signature revert) surfaces as a compile error, not a silent drop.
    @Suppress("UNUSED_VARIABLE")
    val _pinTypeContract: DensityAltitudeInput? = densityAltitudeInput

    return deriveDecisionAltitudeEvent(aircraft, mission)
        // ── Branch 2: tailwind exceedance (fn-15.1 new — physically stronger
        // constraint, fires before crosswind when both apply per Decision #5)
        ?: deriveTailwindEvent(aircraft, mission, weather)
        // ── Branch 3: crosswind exceedance (fn-14.1, control-authority
        // constraint; demoted one position by fn-15.1's tailwind branch)
        ?: deriveCrosswindEvent(aircraft, mission, weather)
}

/**
 * fn-14.1: DA branch extracted intact from the pre-fn-14.1 body. Pure;
 * no new behavior. Branch is mutually exclusive with the wind branches
 * in practice — DA fires below 100 m with no clearance; wind branches
 * need `phase = Final` (i.e. on the approach descent) but are not
 * altitude- or clearance-gated. When all three apply (hypothetically:
 * low + on-final + uncleared + both winds exceeded), DA wins per the
 * split's sequence — pinned by `PilotEventTailwindTest`'s ordering row
 * and `PilotEventCrosswindTest`'s legacy "DA + crosswind" ordering row.
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
 * fn-15.1: shared mission-shape guard for the reactive-GA recognition
 * branches (crosswind, tailwind). Fail-closed if the active compound
 * is not rewritable by `applyCrosswindGoAround` / `applyTailwindGoAround`'s
 * `replaceChild { isCircuitLike }` predicate. The Transit-arrival mission
 * shape (FINAL primitive directly under Transit compound) cannot be
 * rewritten by `isCircuitLike`-keyed replacement; firing recognition
 * without rewrite would emit `Report(GoingAround)` while leaving the
 * step in the eligible set, causing re-fire every tick.
 *
 * Multi-aerodrome / Transit-arrival reactive recognition is filed as
 * `D-PASS-g3b-react-cross-aerodrome-crosswind` (fn-14) and
 * `D-PASS-g3b-react-cross-aerodrome-tailwind` (fn-15).
 *
 * Extracted from fn-14.1's inline `isCircuitLike` guard at codex
 * round-1 fix. Pinned in
 * `bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11`
 * memory entry — recognition must agree with the apply's subtree-rewrite
 * predicate or the recognition fires into a no-op apply.
 */
private fun isReactiveGoAroundEligible(mission: PilotMission): Boolean {
    val activeCompoundName = mission.root.activeCompound()?.name ?: return false
    return activeCompoundName.isCircuitLike()
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
 *
 * fn-15.1: step-set symbol renamed to [WIND_REACTIVE_ELIGIBLE_STEPS]
 * (shared with tailwind branch); mission-shape guard extracted to
 * [isReactiveGoAroundEligible] (shared with tailwind branch). Behaviour
 * unchanged from fn-14.1 — only the symbol names move.
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
    if (step !in WIND_REACTIVE_ELIGIBLE_STEPS) return null

    // Mission-shape guard (fn-14.1 codex review fix, lifted to a shared
    // helper in fn-15.1): only fire when the pilot's active compound is
    // circuit-like, i.e. a tree the response applier
    // (`applyCrosswindGoAround` → `replaceChild { isCircuitLike }`) can
    // rewrite. See [isReactiveGoAroundEligible] KDoc for the
    // recognition+apply pipeline rationale (Transit-arrival mission
    // shape fails closed; multi-aerodrome reactive recognition is filed
    // as `D-PASS-g3b-react-cross-aerodrome-crosswind`).
    if (!isReactiveGoAroundEligible(mission)) return null

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

/**
 * fn-15.1 (G3a-react-tailwind): tailwind branch — independent gate set,
 * mirrors [deriveCrosswindEvent]'s shape exactly except for:
 *  - helper: [tailwindComponentKnots] instead of [crosswindComponentKnots];
 *  - limit: `aircraft.type.maxTailwindKnots` instead of `maxCrosswindKnots`;
 *  - event: [PilotEvent.TailwindLimitExceeded] instead of `CrosswindLimitExceeded`.
 *
 * Branches are independent — no shared early returns, no shared state.
 * Both can hold simultaneously on the same tick (e.g. quartering tail-
 * crosswind at 20 kt against C172 produces both `tailwind = 14.14 > 10`
 * AND `crosswind = 14.14 < 15`, but a 20 kt pure quartering-tailwind
 * scenario can trip both predicates). When both hold, this branch
 * (positioned ahead of crosswind in [derivePilotEvent]) wins per fn-15
 * Decision #5: tailwind is the physically stronger constraint
 * (touchdown energy, runway remaining, go-around margin) and is
 * doctrinally a hard limitation on jet-class types (FCOM Limitations §1).
 *
 * **Per-type doctrinal severity** lives in the
 * [xyz.easiersaid.twr.protocol.AircraftType.maxTailwindKnots] KDoc and
 * in [PilotEvent.TailwindLimitExceeded] KDoc — not in this function. The
 * recognition predicate is uniform across types; the doctrinal anchor
 * cited per-leaf differs (C172 = FAA AFH Ch 9 advisory; B738 = Boeing
 * 737-800 FCOM Limitations §1 hard limit).
 *
 * `aircraft.type.maxTailwindKnots` is read **inside** this function
 * from the live aircraft state — mirrors fn-14.1's discipline; passing
 * `aircraftType` as a separate parameter would let a test desynchronise
 * the two and produce scenarios that cannot exist in production.
 */
@Suppress("ReturnCount") // guard-clause early returns enumerate fail-closed modes;
// folding into a single expression obscures which precondition failed.
private fun deriveTailwindEvent(
    aircraft: AircraftState,
    mission: PilotMission,
    weather: WindReport?,
): PilotEvent.TailwindLimitExceeded? {
    // Phase guard: only on final (during the approach descent itself).
    if (aircraft.phase !is PilotPhase.Final) return null

    // Step guard: final-eligible steps. Independent of mission.hasClearance.
    val step = mission.currentTask?.step ?: return null
    if (step !in WIND_REACTIVE_ELIGIBLE_STEPS) return null

    // Mission-shape guard (shared with crosswind branch): only fire when
    // the pilot's active compound is circuit-like, i.e. a tree the
    // response applier (`applyTailwindGoAround` → `replaceChild
    // { isCircuitLike }`) can rewrite. See [isReactiveGoAroundEligible]
    // KDoc for the recognition+apply pipeline rationale.
    if (!isReactiveGoAroundEligible(mission)) return null

    // Weather guard: fail-closed on null + NotReported.
    val report = weather as? WindReport.Available ?: return null

    // Runway guard: need an assignment AND a parseable Magnetic heading.
    val assignment = mission.activeRunway.getOrNull() ?: return null
    val runway = assignment.runway
    val runwayHeading = runway.headingDegreesMagnetic() ?: return null

    // Pure tailwind component in Double.
    val component = tailwindComponentKnots(
        windFromMagnetic = report.wind.directionDegrees,
        windSpeedKnots = report.wind.speedKnots,
        runwayHeadingMagnetic = runwayHeading,
    )
    val limit = aircraft.type.maxTailwindKnots.value
    if (component <= limit.toDouble()) return null

    return PilotEvent.TailwindLimitExceeded(
        aircraft = aircraft.id,
        componentKnots = component,
        limitKnots = limit,
        runway = runway,
    )
}
