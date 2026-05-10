package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.Airborne
import xyz.easiersaid.twr.controller.bdi.AircraftIntentIs
import xyz.easiersaid.twr.controller.bdi.instructionOfType
import xyz.easiersaid.twr.controller.bdi.AllOf
import xyz.easiersaid.twr.controller.bdi.AnyOf
import xyz.easiersaid.twr.controller.bdi.AtcRule
import xyz.easiersaid.twr.controller.bdi.CircuitIntentIs
import xyz.easiersaid.twr.controller.bdi.ClearLandAction
import xyz.easiersaid.twr.controller.bdi.ClearTouchAndGoAction
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.ContinueApproachAction
import xyz.easiersaid.twr.controller.bdi.ExtendDownwindAction
import xyz.easiersaid.twr.controller.bdi.GoAroundAction
import xyz.easiersaid.twr.controller.bdi.GoAroundEvent
import xyz.easiersaid.twr.controller.bdi.HandoffAction
import xyz.easiersaid.twr.controller.bdi.HasReportedPositionCall
import xyz.easiersaid.twr.controller.bdi.IsTransferTargetStaffed
import xyz.easiersaid.twr.controller.bdi.JoinCircuitAction
import xyz.easiersaid.twr.controller.bdi.PositionReportKind
import xyz.easiersaid.twr.controller.bdi.TaxiToStandAction
import xyz.easiersaid.twr.controller.bdi.TerminateRadarServiceAction
import xyz.easiersaid.twr.controller.bdi.InCircuit
import xyz.easiersaid.twr.controller.bdi.InstructionMatcher
import xyz.easiersaid.twr.controller.bdi.IsCircuitTraffic
import xyz.easiersaid.twr.controller.bdi.NoActiveInstruction
import xyz.easiersaid.twr.controller.bdi.CoordinationIssued
import xyz.easiersaid.twr.controller.bdi.NoPendingReadback
import xyz.easiersaid.twr.controller.bdi.Not
import xyz.easiersaid.twr.controller.bdi.ContinueApproachAlreadyIssuedThisAttempt
import xyz.easiersaid.twr.controller.bdi.ObstructionClearsInTime
import xyz.easiersaid.twr.controller.bdi.ObstructionContinueApproachAction
import xyz.easiersaid.twr.controller.bdi.ObstructionGoAroundAction
import xyz.easiersaid.twr.controller.bdi.ObstructionGoAroundAlreadyIssuedThisAttempt
import xyz.easiersaid.twr.controller.bdi.OnApproach
import xyz.easiersaid.twr.controller.bdi.OnCircuitLeg
import xyz.easiersaid.twr.controller.bdi.OnGround
import xyz.easiersaid.twr.controller.bdi.OnRunway
import xyz.easiersaid.twr.controller.bdi.TouchedDownDuringCommitment
import xyz.easiersaid.twr.controller.bdi.PositionReported
import xyz.easiersaid.twr.controller.bdi.ProcedureInterrupt
import xyz.easiersaid.twr.controller.bdi.ProcedureSpec
import xyz.easiersaid.twr.controller.bdi.ReportFinalAction
import xyz.easiersaid.twr.controller.bdi.RunwayAccessGranted
import xyz.easiersaid.twr.controller.bdi.RunwayLengthOperation
import xyz.easiersaid.twr.controller.bdi.RunwayLengthSufficient
import xyz.easiersaid.twr.controller.bdi.RunwayObstructed
import xyz.easiersaid.twr.controller.bdi.RunwayPhysicallyClear
import xyz.easiersaid.twr.controller.bdi.SeparationConcernAbove
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.bdi.TurnBaseAction
import xyz.easiersaid.twr.controller.bdi.VacateAction
import xyz.easiersaid.twr.controller.bdi.WeatherPermitsVfr
import xyz.easiersaid.twr.controller.bdi.WithinDistanceOfThreshold
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Meters
import xyz.easiersaid.twr.protocol.RegulationDatabase.CAP413_4_55
import xyz.easiersaid.twr.protocol.RegulationDatabase.CAP413_4_56
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_12_3_4_16
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_5
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_CIRCUIT_JOIN
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_CIRCUIT_REPORTS
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_TAXI
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_6
import xyz.easiersaid.twr.protocol.RegulationDatabase.SERA_3225
import xyz.easiersaid.twr.protocol.RegulationDatabase.SERA_5005
import xyz.easiersaid.twr.protocol.RegulationDatabase.SERA_8005_C
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.RegulationDatabase.CAP413_4_51
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_10_1
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_11
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_10
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_10_2
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_CONTINUE_APPROACH
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_FREQUENCY_CHANGE
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_GO_AROUND
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_EXTEND_DOWNWIND
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_LANDING
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_4_1_4_1
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_8_9_6_1_8
import xyz.easiersaid.twr.protocol.RegulationDatabase.CAP413_4_65
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ReportWhen
import xyz.easiersaid.twr.protocol.Urgency
import xyz.easiersaid.twr.controller.observe.AdvancementPolicy

/**
 * Tower arrival procedure — the controller-side arrival flow's rule pipeline.
 *
 * **Three doctrinally distinct guard predicates at `AwaitApproach`** (fn-13
 * extends fn-12's two-predicate model):
 *  - `RunwayPhysicallyClear` — used by the generic `ARR-GO-AROUND` /
 *    `ARR-GO-AROUND-CLEARANCE-ISSUED` rules (negated). Reads
 *    `BeliefState.runwayBeliefs[runway].status` for **physical occupancy** by
 *    another aircraft (landing rolls in progress, lined-up departure, etc.).
 *    Trigger source: aircraft-position observation + the runway-duty state.
 *  - `RunwayObstructed` — used by the `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule
 *    (and as `Not(RunwayObstructed)` on `LandingConditions`). Reads
 *    `BeliefState.runwayObstructions` for a **declared obstruction**
 *    (vehicle, debris, wildlife, surface contamination — modality-agnostic in
 *    the v1 model). Trigger source: world-state-derived events
 *    (`RunwayObstructionDetected` / `Cleared`) from the sim's per-cycle
 *    world-diff producer; see
 *    `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md`
 *    § Unified Event Taxonomy on the three `ControllerEvent` source classes.
 *  - `ObstructionClearsInTime` (fn-13) — kinematic predicate over a declared
 *    obstruction: `(clearsAt - now) + OBSTRUCTION_CLEAR_SAFETY_MARGIN_S(10s)
 *    ≤ ETA-to-threshold`. Fail-closed (any missing input → false → GA wins).
 *    Reads `BeliefState.runwayObstructions[runway]`, `ac.coords`,
 *    `ac.groundSpeed`, and `ctx.worldIndex.thresholdByRunway[runway]`.
 *
 * **Three-way priority ordering at `AwaitApproach`** (fn-13):
 *  1. `ARR-CONTINUE-APPROACH-OBSTRUCTION` — gated on `RunwayObstructed AND
 *     ObstructionClearsInTime`. Pre-clearance ladder middle state per CAP
 *     413 §4.55-4.56 + ICAO 4444 §12.3.4.16(d). Emits
 *     `Instruction.ContinueApproach(RUNWAY_OBSTRUCTED)` + companion
 *     `RunwayObstructionInformation`. `nextStage = null` (commitment stays
 *     at AwaitApproach); witness `continueApproachIssuedThisAttempt`
 *     suppresses re-fire.
 *  2. `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` (AwaitApproach variant, narrowed
 *     in fn-13) — gated on `RunwayObstructed AND
 *     Not(ObstructionClearsInTime)`. Mutually exclusive with the rule
 *     above via guard disjointness; priority placement is defence-in-depth.
 *  3. `ARR-GO-AROUND` (generic, fn-10 era) — gated on `RunwayAccessGranted
 *     AND Not(RunwayPhysicallyClear)`. Physical-occupancy path; no
 *     obstruction companion.
 *
 * Post-clearance (`LandingClearanceIssued`, `AwaitLandedObserved`), the
 * obstruction-GA variant is UNCHANGED from fn-12 (Boundary #1 of fn-13:
 * once landing clearance is issued, the doctrine flips to GA-on-obstruction;
 * the CONTINUE APPROACH surface is pre-clearance-only — CAP 413 §4.53
 * cancel-clearance path is a future deferment).
 *
 * The `RunwayPhysicallyClear` and `RunwayObstructed` predicates may both
 * be true simultaneously (e.g. an aircraft on the runway AND a declared
 * debris obstruction). The obstruction-specific rule wins by priority
 * placement (per fn-12 R7) so the companion `RunwayObstructionInformation`
 * transmission is emitted (reason on radio per ICAO §7.4.1.4.1(c) for GA
 * / §12.3.4.16(d) for CA — mandatory in both cases). When only physical
 * occupancy holds, the generic GA rule fires without the obstruction-info
 * companion.
 */

/**
 * Maximum distance from threshold at which landing clearance may be issued.
 *
 * VFR circuit finals typically begin 1.5–2.5 nm out. A 5000m (~2.7 nm) outer gate
 * prevents clearance being issued immediately on turning final after a very long
 * extended downwind, while leaving ample time for readback and a go-around if needed.
 * No ICAO regulatory minimum exists for issuance distance; this is an operational
 * safety margin on top of the RunwayPhysicallyClear requirement (ICAO 4444 §7.10).
 */
private val MAX_LANDING_CLEARANCE_DISTANCE = Meters(5000.0)

/** Shared guard: conditions for issuing or re-issuing a landing clearance (non-T&G). */
private val LandingConditions = AllOf(listOf(
    AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
    WithinDistanceOfThreshold(MAX_LANDING_CLEARANCE_DISTANCE),
    WeatherPermitsVfr,
    RunwayAccessGranted,
    RunwayPhysicallyClear,
    // fn-12 (R6): typed runway-obstruction gate. Doctrinally distinct from
    // RunwayPhysicallyClear (which reads runwayBeliefs[runway].status for
    // physical occupancy by another aircraft) — RunwayObstructed reads the
    // BeliefState.runwayObstructions slice populated by the world-diff
    // producer's typed declarations (vehicle, debris, wildlife, etc.).
    // Defensive belt-and-suspenders alongside the post-clearance reactive
    // rule: even if the rule fires in the same cycle as the obstruction
    // first appears, this gate prevents `ClearedToLand` from being issued
    // onto a known-obstructed runway. Transitively gates ARR-LAND,
    // ARR-LAND-TNG, ARR-LAND-REISSUE, and ARR-LAND-TNG-REISSUE.
    Not(RunwayObstructed),
    // Pass 13 (D-AUDIT.4.A-FOLLOWUP closure): runway must be long enough
    // for the aircraft's landing LDA. Shared by ARR-LAND, ARR-LAND-TNG,
    // and their re-issue rules. Fails closed for unknown designator or
    // absent declared distances.
    RunwayLengthSufficient(RunwayLengthOperation.LANDING),
    // fn-8.3 Phase 4 (B5-α): the controller has observed at least one
    // circuit-position pilot call (Downwind / Base / Final / LongFinal)
    // during the **current** commitment lifetime. Doctrine: CAP 413
    // §4.45-4.49 / ICAO Doc 4444 §7.10 — landing clearance follows the
    // pilot's position call.
    //
    // Pre-fix, ARR-LAND / ARR-LAND-TNG fired purely on observed geometry
    // + strip-derived `IsCircuitTrafficByStrip` (C2/C3) — a stepped-on
    // Downwind didn't block landing-clearance issuance, so the controller
    // could clear the aircraft to land BEFORE the pilot's position call
    // had been delivered. The pilot's mission tree (T&G shape) then
    // mismatched the controller's clearance disposition (full-stop), and
    // M3/M4 surfaced (BacktrackRunway dropped, aircraft lifts off again).
    // See fn-8.3 spec § Evidence § Phase 3 round 2 for the empirical
    // four-mechanism trace (M1 — same-tick race).
    //
    // Reset points (mirrors B2 / B3 patterns): commitment formation
    // (`createCommitment` → default empty), stage regression (e.g.
    // go-around `LandingClearanceIssued`/`AwaitLandedObserved` →
    // `AwaitDownwind` per `GA-POST-CLEAR` — handled by the regression
    // detection in `advanceCommittedStages`).
    HasReportedPositionCall(setOf(
        PositionReportKind.DOWNWIND,
        PositionReportKind.BASE,
        PositionReportKind.FINAL,
        PositionReportKind.LONG_FINAL,
        PositionReportKind.ESTABLISHED,
        PositionReportKind.ESTABLISHED_LOCALISER,
        PositionReportKind.ESTABLISHED_GLIDEPATH,
    )),
))

/**
 * fn-12 (R7): single AtcRule reused across all three on-final stages
 * (`AwaitApproach`, `LandingClearanceIssued`, `AwaitLandedObserved`).
 * Together they cover the entire on-final window: pre-clearance,
 * post-clearance pre-readback, post-readback pre-touchdown.
 *
 * **Priority placement** (per fn-12 epic R7 acceptance): inserted BEFORE
 * broader generic GA rules (`ARR-GO-AROUND`, `ARR-CONTINUE`,
 * `ARR-GO-AROUND-CLEARANCE-ISSUED`) so that when a runway is BOTH
 * obstructed AND physically-not-clear (or access-not-granted), the
 * obstruction-specific rule wins — the obstruction-info companion is
 * emitted (per ICAO §7.4.1.4.1(c) — reason-on-radio is mandatory).
 *
 * **Regression at issuance** (`Immediate` advancement): the commitment
 * moves to `AwaitDownwind` in the same tick the rule fires. The existing
 * `GA-POST-CLEAR` interrupt does NOT re-fire for this path (its
 * `fromStages` no longer matches by the time `Report(GoingAround)`
 * arrives). The witness `obstructionGoAroundIssuedThisAttempt` (set in
 * `advanceCommittedStages` when the rule fires from a committed-output
 * path) is the actual no-refire mechanism — stage-progression alone is
 * insufficient because reconciliation may re-advance the aircraft back
 * through eligible stages while the obstruction persists.
 *
 * **Re-arm**: the witness clears on the next `Report(Downwind)` arrival
 * in `reconcileTowerArrival`, OR on commitment replacement (fresh
 * `Commitment` takes the default `false`).
 *
 * **Regulatory grounding** (R7 acceptance): explicit refs, not
 * placeholders — ICAO Doc 4444 §7.4.1.4.1 (runway obstruction GA mandate)
 * + §8.9.6.1.8 (reason on radio) + CAP 413 §4.65 (missed approach
 * phraseology).
 */
/**
 * fn-13.1 (R5): split out for the `AwaitApproach` stage placement.
 *
 * **Why two rule objects**: fn-13.1 narrows the AwaitApproach-stage GA
 * placement with `Not(ObstructionClearsInTime)` for mutual exclusion with
 * `ARR-CONTINUE-APPROACH-OBSTRUCTION` (which fires when the predicate is
 * `true`). Post-clearance placements (`LandingClearanceIssued`,
 * `AwaitLandedObserved`) are UNCHANGED per fn-13 epic Boundary #1 — once
 * landing clearance is issued, the doctrine flips to GA-on-obstruction
 * (CAP 413 §4.53 cancel-clearance path is a future deferment).
 *
 * **Shared rule id** (`ARR-GO-AROUND-RUNWAY-OBSTRUCTED`): both variants
 * share the id because the [applyCommittedOutputWitnesses] pass in
 * Controller.kt pattern-matches on `result.trace.ruleId` to set the
 * obstruction-GA witness. Two ids would require duplicating the witness
 * branch. Stage scope disambiguates which variant fires at runtime —
 * `executeProcedure` uses the stage's rule list.
 */
private val obstructionGoAroundRuleAwaitApproach: AtcRule = AtcRule(
    id = "ARR-GO-AROUND-RUNWAY-OBSTRUCTED",
    description = "Instruct go-around — runway obstructed during approach (clears too late)",
    regulations = listOf(ICAO4444_7_4_1_4_1, ICAO4444_8_9_6_1_8, CAP413_4_65),
    guard = AllOf(listOf(
        AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
        RunwayObstructed,
        // fn-13.1 (R5): AwaitApproach-stage narrowing. When the
        // obstruction is expected to clear in time, the new
        // ARR-CONTINUE-APPROACH-OBSTRUCTION rule wins (placed BEFORE
        // this rule in `stageRules[AwaitApproach]`); this rule fires
        // only when the predicate is false (clears too late, missing
        // groundSpeed, unknown threshold, etc. — fail-closed false → GA).
        Not(ObstructionClearsInTime),
        // No-refire guard. Witness is set ONLY on the committed-output
        // path (`applyCommittedOutputWitnesses`), NOT at candidate-emit
        // time — see fn-12 task spec § R7-no-refire.
        Not(ObstructionGoAroundAlreadyIssuedThisAttempt),
    )),
    action = ObstructionGoAroundAction,
    nextStage = TowerArrivalStage.AwaitDownwind,
    urgency = Urgency.SAFETY,
    advancementPolicy = AdvancementPolicy.Immediate,
)

/**
 * fn-13.1 (R5): post-clearance placement — `LandingClearanceIssued` and
 * `AwaitLandedObserved`. Original fn-12 shape, UNCHANGED per Boundary #1.
 * Post-clearance obstructions always escalate to GA; the CONTINUE APPROACH
 * surface is pre-clearance-only.
 */
private val obstructionGoAroundRulePostClearance: AtcRule = AtcRule(
    id = "ARR-GO-AROUND-RUNWAY-OBSTRUCTED",
    description = "Instruct go-around — runway obstructed during approach (post-clearance)",
    regulations = listOf(ICAO4444_7_4_1_4_1, ICAO4444_8_9_6_1_8, CAP413_4_65),
    guard = AllOf(listOf(
        AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
        RunwayObstructed,
        // No-refire guard. Witness is set ONLY on the committed-output
        // path (`applyCommittedOutputWitnesses`), NOT at candidate-emit
        // time — see fn-12 task spec § R7-no-refire.
        Not(ObstructionGoAroundAlreadyIssuedThisAttempt),
    )),
    action = ObstructionGoAroundAction,
    nextStage = TowerArrivalStage.AwaitDownwind,
    urgency = Urgency.SAFETY,
    advancementPolicy = AdvancementPolicy.Immediate,
)

/**
 * fn-13.1 (R4): obstruction-driven CONTINUE APPROACH — pre-clearance ladder
 * middle state per CAP 413 §4.55-4.56 + ICAO Doc 4444 §12.3.4.16(d).
 *
 * Fires from `AwaitApproach` ONLY (Boundary #1: post-clearance always
 * escalates to GA). Priority-placed BEFORE `obstructionGoAroundRuleAwaitApproach`
 * in the stage rule list so that when the predicate holds, the CA rule
 * wins selection-order. The GA rule's narrowed guard
 * (`Not(ObstructionClearsInTime)`) provides defence-in-depth mutual
 * exclusion: even if rule-selection priority changed, both rules would
 * not pass their guards simultaneously.
 *
 * **No `nextStage`** (stays at `AwaitApproach`): once the obstruction
 * clears or the predicate flips, the existing `ARR-LAND` / `ARR-LAND-TNG`
 * rules (or the GA rule on escalation) take over without commitment
 * re-formation. The `continueApproachIssuedThisAttempt` witness is the
 * only suppression mechanism — set by the
 * [applyCommittedOutputWitnesses] pass post-arbitration + certification.
 * Re-armed on the next `Report(Downwind)` arrival in
 * [reconcileTowerArrival] (shared lifecycle with the GA witness).
 *
 * **Urgency = TIME_SENSITIVE**: matches the existing traffic-driven
 * `ARR-CONTINUE` rule. `PROGRESSION` would let arbitration's
 * one-per-urgency budget delay the CA behind routine progression work,
 * which is wrong on final. `SAFETY` is wrong doctrinally — CONTINUE
 * APPROACH is not an emergency; it is the pre-clearance delay surface.
 *
 * **Regulations**: pre-clearance refs only. Excludes `CAP413_4_65`
 * (missed-approach phraseology) and `ICAO4444_7_4_1_4_1` (post-clearance
 * GA mandate). Same exclusion list as the companion-trace regs the action
 * populates.
 */
private val obstructionContinueApproachRule: AtcRule = AtcRule(
    id = "ARR-CONTINUE-APPROACH-OBSTRUCTION",
    description = "Delay landing clearance via CONTINUE APPROACH — runway obstructed but expected to clear in time",
    regulations = listOf(CAP413_4_55, CAP413_4_56, ICAO4444_12_3_4_16, ICAO4444_8_9_6_1_8),
    guard = AllOf(listOf(
        AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
        RunwayObstructed,
        ObstructionClearsInTime,
        // GA witness check: if the GA fired earlier in this approach
        // attempt, we don't issue a CA on top (the GA already regressed
        // the commitment; a fresh CA on an aircraft mid-recovery would
        // be doctrinally wrong).
        Not(ObstructionGoAroundAlreadyIssuedThisAttempt),
        // CA witness check: the rule has `nextStage = null` (stays at
        // AwaitApproach), so without this gate the rule would re-fire
        // every cycle while both predicates persist.
        Not(ContinueApproachAlreadyIssuedThisAttempt),
    )),
    action = ObstructionContinueApproachAction,
    // nextStage = null: stay at AwaitApproach. The witness is the
    // suppression mechanism; downstream rules (ARR-LAND when the
    // obstruction clears, the GA when it doesn't) re-fire from the
    // same stage.
    urgency = Urgency.TIME_SENSITIVE,
    advancementPolicy = AdvancementPolicy.Immediate,
)

@Suppress("LongMethod") // procedure spec is a flat list of rules — splitting into smaller
// procedures is a behavioural decision (separate APPROACH/AFIS/etc. flows), not a stylistic one.
fun towerArrivalProcedure(): ProcedureSpec = ProcedureSpec(
    kind = CommitmentKind.TOWER_ARRIVAL,
    interrupts = listOf(
        ProcedureInterrupt(
            id = "GA-PRE-CLEAR",
            description = "Go-around detected before landing clearance",
            regulations = listOf(ICAO4444_7_10_2),
            fromStages = setOf(TowerArrivalStage.AwaitApproach),
            guard = GoAroundEvent,
            targetStage = TowerArrivalStage.AwaitDownwind,
        ),
        ProcedureInterrupt(
            id = "GA-POST-CLEAR",
            description = "Go-around detected after landing clearance",
            regulations = listOf(ICAO4444_7_10_2),
            fromStages = setOf(TowerArrivalStage.LandingClearanceIssued, TowerArrivalStage.AwaitLandedObserved),
            guard = GoAroundEvent,
            targetStage = TowerArrivalStage.AwaitDownwind,
        ),
    ),
    stageRules = mapOf(
        // ── AwaitDownwind: acknowledge position or wait ──────────────
        TowerArrivalStage.AwaitDownwind to listOf(
            // G2 Phase I: cross-aerodrome / off-pattern arrival — issue
            // join-downwind so the pilot navigates from the inbound REP into
            // the local pattern. Existing AwaitDownwind rules (ARR-DOWNWIND-
            // ACK, ARR-EXTEND, ARR-TURN-BASE, ARR-LAND) all gate on
            // `OnCircuitLeg(...)` — they assume the aircraft is already in
            // the pattern. None fires for an aircraft at OSMOT (LJMB's
            // first VFR contact REP, ~25 NM out).
            //
            // Doctrine: ICAO/EU has no prescribed default join position
            // (controller discretion per atc-law review). For the LJMB
            // scenario (single VFR arrival from OSMOT, RWY 14 right-hand
            // pattern), `JoinType.DOWNWIND` is the conservative minimum-
            // intervention choice. STRAIGHT_IN, BASE, OVERHEAD variations
            // require new rule sites with their own doctrine commentary.
            //
            // Re-fire prevention: `NoActiveInstruction` (not just
            // `NoPendingReadback`) because after the readback resolves,
            // the pending-readback gate would otherwise allow re-fire while
            // the aircraft is still off-pattern flying toward downwind.
            // `NoActiveInstruction` reads the issued-clearance ledger and
            // gates on any non-terminal JoinCircuit clearance.
            //
            // Self-deactivation: once the aircraft enters the downwind leg,
            // `OnCircuitLeg(DOWNWIND)` becomes true and the `Not(AnyOf(...))`
            // guard turns false — rule sleeps and the existing pattern rules
            // (ARR-EXTEND, ARR-TURN-BASE) take over.
            AtcRule(
                id = "ARR-JOIN-CIRCUIT",
                description = "Issue join-downwind for off-pattern arriving traffic",
                regulations = listOf(SERA_3225, SERA_5005, ICAO4444_7_10, ICAO9432_CIRCUIT_JOIN),
                guard = AllOf(listOf(
                    Airborne,
                    Not(AnyOf(listOf(
                        OnCircuitLeg(LegName.UPWIND),
                        OnCircuitLeg(LegName.CROSSWIND),
                        OnCircuitLeg(LegName.DOWNWIND),
                        OnCircuitLeg(LegName.BASE),
                        OnCircuitLeg(LegName.FINAL),
                    ))),
                    AircraftIntentIs(xyz.easiersaid.twr.protocol.AircraftIntent.Arriving),
                    RunwayLengthSufficient(RunwayLengthOperation.LANDING),
                )),
                action = JoinCircuitAction(joinType = xyz.easiersaid.twr.protocol.JoinType.DOWNWIND),
                // Re-fire prevention via stage advancement: same pattern as
                // `DEP-CROSS-AERODROME-RELEASE` (Phase H). JoinCircuit has no
                // required readback atoms (PilotCognitive's
                // `requiredReadbackAtoms` returns None for JoinCircuit), so
                // `NoPendingReadback(JoinCircuit)` would always evaluate true
                // and the rule would re-fire every cycle. `NoActiveInstruction`
                // would help if the issued-clearances ledger were populated,
                // but `ControllerView.activeClearances` is currently always
                // empty (sim doesn't propagate it). Stage advancement is the
                // load-bearing gate. Downstream rules at AwaitApproach all
                // gate on `OnCircuitLeg(...)`, so they sleep while the
                // aircraft is still off-pattern flying toward downwind; once
                // the pattern is reached, `ARR-TURN-BASE` / `ARR-LAND` etc.
                // take over.
                nextStage = TowerArrivalStage.AwaitApproach,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            AtcRule(
                id = "ARR-DOWNWIND-ACK",
                description = "Acknowledge downwind report and advance to approach sequencing",
                // Position-report acknowledgement is phraseology-only — no clearance or
                // sequencing action is taken here. §7.10 (arriving aircraft) is reserved
                // for rules that actually dispose of the approach.
                regulations = listOf(ICAO9432_CIRCUIT_REPORTS),
                guard = AllOf(listOf(InCircuit, PositionReported)),
                nextStage = TowerArrivalStage.AwaitApproach,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            AtcRule(
                id = "ARR-ADVANCE-APPROACH",
                description = "Aircraft already on approach — advance to approach sequencing",
                regulations = listOf(ICAO4444_7_10),
                guard = OnApproach,
                nextStage = TowerArrivalStage.AwaitApproach,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        // ── AwaitApproach: sequence, delay, or clear to land ─────────
        TowerArrivalStage.AwaitApproach to listOf(
            // fn-13.1 (R4): obstruction-driven CONTINUE APPROACH — fires
            // FIRST when the obstruction is expected to clear in time
            // (per CAP 413 §4.55-4.56 / ICAO 4444 §12.3.4.16(d)). When
            // the predicate fails (clears too late / missing inputs),
            // `obstructionGoAroundRuleAwaitApproach`'s narrowed guard
            // (`Not(ObstructionClearsInTime)`) lets the GA fire instead.
            // Priority placement matters: rule-selection is list-order
            // (per `executeProcedure` first-match-wins).
            obstructionContinueApproachRule,
            // fn-12 (R7): obstruction-driven GA — fires BEFORE the broader
            // generic GA rule so when the runway is BOTH obstructed AND
            // physically-not-clear, the obstruction-specific rule wins
            // and the obstruction-info companion is emitted.
            //
            // fn-13.1 (R5): AwaitApproach-stage variant. Narrowed with
            // `Not(ObstructionClearsInTime)` so the GA fires only when
            // the CONTINUE APPROACH predicate fails — mutual exclusion
            // with the new rule above.
            obstructionGoAroundRuleAwaitApproach,
            // Controller-initiated go-around: runway was granted but is no longer clear
            AtcRule(
                id = "ARR-GO-AROUND",
                description = "Instruct go-around — runway not clear for landing",
                regulations = listOf(ICAO4444_7_10_2, ICAO9432_GO_AROUND),
                guard = AllOf(listOf(
                    AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
                    RunwayAccessGranted,
                    Not(RunwayPhysicallyClear),
                )),
                action = GoAroundAction,
                nextStage = TowerArrivalStage.AwaitDownwind,
                urgency = Urgency.SAFETY,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Extend downwind for spacing when no runway access yet
            AtcRule(
                id = "ARR-EXTEND",
                description = "Extend downwind for in-trail spacing — no runway access yet",
                // §7.10 is the correct authority (controller sequencing of arriving traffic
                // in the circuit); §5 is generic separation methods and was broader than
                // needed for a circuit-spacing delay.
                regulations = listOf(ICAO4444_7_10, ICAO9432_EXTEND_DOWNWIND),
                guard = AllOf(listOf(
                    OnCircuitLeg(LegName.DOWNWIND),
                    Not(RunwayAccessGranted),
                    // Stop re-issuing once the controller judges spacing is adequate.
                    // Uses the separation engine's comfort gradient from beliefs
                    // (Phase 6b Phase A). Fires when concern is INTERVENTION or above.
                    SeparationConcernAbove(xyz.easiersaid.twr.controller.observe.SeparationConcern.Severity.INTERVENTION),
                    // Retransmit via the coordination lifecycle (Pass 9 D-AUDIT.2):
                    // ExtendDownwind has no required-atom readback, so the entry
                    // escalates Issued → Querying → Reissued, keeping re-issues to
                    // the CAP 413 §2.7 cadence.
                    NoPendingReadback(instructionOfType<ExtendDownwind>()),
                )),
                action = ExtendDownwindAction,
                urgency = Urgency.TIME_SENSITIVE,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Turn base — sequencing decision, NOT a runway-access decision. The controller
            // tells the aircraft to turn base when spacing is adequate and the runway is
            // physically clear. Decoupled from RunwayAccessGranted to avoid the deadlock
            // where extended-downwind aircraft can't reach base gate for duty queue entry.
            // Guard fires only from DOWNWIND — once established on base, the sequencing
            // decision has already been made and re-issuing TurnBase would be non-standard
            // (CAP 413 §4.49 / ICAO Doc 9432 Ch.4: TurnBase is a downwind sequencing tool).
            AtcRule(
                id = "ARR-TURN-BASE",
                description = "Turn base when spacing adequate and runway clear",
                regulations = listOf(ICAO4444_7_10),
                guard = AllOf(listOf(
                    OnCircuitLeg(LegName.DOWNWIND),
                    Not(SeparationConcernAbove(xyz.easiersaid.twr.controller.observe.SeparationConcern.Severity.INTERVENTION)),
                    RunwayPhysicallyClear,
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.TurnBase>()),
                )),
                action = TurnBaseAction,
                urgency = Urgency.PROGRESSION,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Request final position report when aircraft is on base.
            // Issued on base so the pilot has notice to call when they turn; the final call
            // is then used to time the landing clearance and release departing traffic.
            // Simple version: ARR-LAND still gates on physical position (OnCircuitLeg FINAL
            // + WithinDistanceOfThreshold), not on the observed final report. The stronger
            // gating (clearance only after controller observes the final call) requires a
            // receipt mechanism on OutstandingReport that does not yet exist — see .plan OR-1.
            AtcRule(
                id = "ARR-REPORT-FINAL",
                description = "Request final position report when aircraft is on base",
                regulations = listOf(ICAO9432_CIRCUIT_REPORTS, CAP413_4_51),
                guard = AllOf(listOf(
                    OnCircuitLeg(LegName.BASE),
                    NoPendingReadback(instructionOfType<ReportWhen>()),
                )),
                action = ReportFinalAction,
                urgency = Urgency.PROGRESSION,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Clear to land — VFR, full-stop intent OR no circuit intent
            // signal received (radio default is "full-stop unless explicit
            // T&G heard"). fn-8.3 Phase 3 (B4 closure follow-on): the
            // strip-based DEP-CIRCUIT-COMPLETE broadening can advance the
            // commitment to TOWER_ARRIVAL before any Downwind transmission
            // is delivered (e.g. cross-aircraft step-on lost the radio
            // call). Without this default-to-full-stop semantic, ARR-LAND-TNG
            // would fire with `Not(CircuitIntentIs(FULL_STOP))=true` when
            // intent is empty, issuing a T&G clearance against a pilot who
            // never declared T&G. Reality-anchored: a real controller
            // hearing no Downwind call but seeing the aircraft on final
            // would clear to land (safe default), not offer T&G.
            AtcRule(
                id = "ARR-LAND",
                description = "Clear to land when on final and runway available — full-stop or unknown intent",
                regulations = listOf(ICAO4444_7_10, ICAO9432_LANDING),
                guard = AllOf(listOf(
                    LandingConditions,
                    AnyOf(listOf(
                        CircuitIntentIs(CircuitIntent.FULL_STOP),
                        Not(IsCircuitTraffic),
                    )),
                )),
                action = ClearLandAction,
                nextStage = TowerArrivalStage.LandingClearanceIssued,
                readbackAdvancesToStage = TowerArrivalStage.AwaitLandedObserved,
                urgency = Urgency.TIME_SENSITIVE,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Clear touch-and-go — only on explicit T&G intent declaration
            AtcRule(
                id = "ARR-LAND-TNG",
                description = "Clear touch-and-go when on final and runway available — explicit T&G intent",
                regulations = listOf(ICAO4444_7_10, ICAO9432_LANDING),
                guard = AllOf(listOf(LandingConditions, CircuitIntentIs(CircuitIntent.TOUCH_AND_GO))),
                action = ClearTouchAndGoAction,
                nextStage = TowerArrivalStage.LandingClearanceIssued,
                readbackAdvancesToStage = TowerArrivalStage.AwaitLandedObserved,
                urgency = Urgency.TIME_SENSITIVE,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Continue approach when runway not yet clear
            //
            // fn-13.1 (codex round-2): the existing traffic-driven CONTINUE
            // APPROACH rule MUST NOT fire when a runway obstruction is in
            // play — the new `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule
            // (placed earlier in this list) owns the obstruction case.
            //
            // Without `Not(RunwayObstructed)`: after the obstruction-
            // specific CA fires and its coordination escalates past
            // `Issued` (Querying/Reissued/LostCommsDeclared), the
            // `NoPendingReadback(ContinueApproach)` matcher might stop
            // blocking (depending on coordination state), and this rule
            // could emit a SECOND ContinueApproach with the wrong reason
            // (RUNWAY_ACCESS_PENDING / TRAFFIC_*) and NO companion. That
            // violates the no-refire intent for the obstruction window
            // and emits doctrinally-wrong phraseology.
            //
            // The new `Not(RunwayObstructed)` gate is structurally
            // simpler than checking `ContinueApproachAlreadyIssuedThisAttempt`:
            // when an obstruction is in beliefs, the obstruction-specific
            // path is the only correct CA path. Once the obstruction
            // clears, the runway-access/physically-clear arms might still
            // delay landing clearance (preceding traffic) and this rule
            // fires correctly with `inferContinueApproachReason`.
            AtcRule(
                id = "ARR-CONTINUE",
                description = "Continue approach when on final but runway not yet clear",
                // fn-13.1 (codex round-3): dropped `CAP413_4_55` from this
                // rule's regulations list. CAP 413 §4.55 was upgraded by
                // fn-13.1 R7 to specifically describe the runway-obstructed
                // pre-clearance case (the new rule above). The traffic-
                // driven CONTINUE APPROACH path is grounded in ICAO Doc
                // 9432 Ch.4 (generic phraseology) + ICAO Doc 4444 §7.10
                // (arrival sequencing — the controller delays landing
                // clearance to maintain spacing). Citing §4.55 here would
                // attach the obstruction-specific principle to outputs
                // that have nothing to do with obstructions.
                regulations = listOf(ICAO9432_CONTINUE_APPROACH, ICAO4444_7_10),
                guard = AllOf(listOf(
                    AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
                    AnyOf(listOf(Not(RunwayAccessGranted), Not(RunwayPhysicallyClear))),
                    Not(RunwayObstructed),
                    NoPendingReadback(instructionOfType<ContinueApproach>()),
                )),
                action = ContinueApproachAction,
                urgency = Urgency.TIME_SENSITIVE,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        // ── LandingClearanceIssued: clearance transmitted, awaiting readback ──
        // The aircraft is on final/approach. The controller watches for the
        // readback (handled by coordination ledger) or for the aircraft to
        // touch down (handled by observation reconciliation).
        // Go-around from this stage is handled by the GA-POST-CLEAR interrupt.
        TowerArrivalStage.LandingClearanceIssued to listOf(
            // fn-12 (R7): obstruction-driven GA — placed BEFORE the generic
            // GA rule so the obstruction-specific reason-on-radio companion
            // wins when both rules' guards would pass.
            //
            // fn-13.1 (R5): post-clearance variant — UNCHANGED from fn-12.
            // Boundary #1: once landing clearance is issued, the doctrine
            // flips to GA-on-obstruction; the CONTINUE APPROACH surface
            // is pre-clearance-only (CAP 413 §4.53 cancel-clearance path
            // is a future deferment).
            obstructionGoAroundRulePostClearance,
            // Controller-initiated go-around: runway was cleared but is no longer safe
            AtcRule(
                id = "ARR-GO-AROUND-CLEARANCE-ISSUED",
                description = "Instruct go-around — runway not clear after clearance issued",
                regulations = listOf(ICAO4444_7_10_2, ICAO9432_GO_AROUND),
                guard = AllOf(listOf(
                    AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
                    Not(RunwayPhysicallyClear),
                )),
                action = GoAroundAction,
                nextStage = TowerArrivalStage.AwaitDownwind,
                urgency = Urgency.SAFETY,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Re-issue: ClearedToLand was stepped on → coordination GC'd → re-issue.
            // Gate matches ARR-LAND: explicit FULL_STOP OR no circuit-intent
            // signal received (default-to-full-stop for unknown intent).
            //
            // fn-8.3 Phase 3 round 1 (codex review iteration 3): the
            // `NoPendingReadback` matcher widens to BOTH landing-clearance
            // types so a fresh land-reissue cannot land on top of a pilot
            // who is currently reading back a `ClearedTouchAndGo` (and
            // vice versa for the T&G reissue). Limiting the cross-type
            // block to the narrow `Issued` state preserves liveness — if
            // the opposite-type coordination escalates past `Issued`
            // (Querying / Reissued / LostCommsDeclared), the COORD-REISSUE
            // / lost-comms flows have effectively superseded the prior
            // clearance in the eyes of the lifecycle, and a fresh
            // intent-aligned clearance is the doctrinally correct next
            // step. Iteration-2's wider `NoOpenCoordination` gate caused
            // a deadlock when intent flipped post-issuance and the prior
            // coordination escalated — neither rule could fire.
            //
            // Net invariant: at most one *fresh-issued* (state=Issued)
            // landing-class coordination at a time. Multiple coordinations
            // in escalated states can coexist in the ledger; the
            // escalation flow + supersession-by-readback handle resolution.
            AtcRule(
                id = "ARR-LAND-REISSUE",
                description = "Re-issue landing clearance after readback timeout",
                regulations = listOf(ICAO4444_7_10, ICAO9432_LANDING),
                guard = AllOf(listOf(
                    LandingConditions,
                    AnyOf(listOf(
                        CircuitIntentIs(CircuitIntent.FULL_STOP),
                        Not(IsCircuitTraffic),
                    )),
                    NoPendingReadback(InstructionMatcher.AnyOf(listOf(
                        instructionOfType<xyz.easiersaid.twr.protocol.ClearedToLand>(),
                        instructionOfType<xyz.easiersaid.twr.protocol.ClearedTouchAndGo>(),
                    ))),
                )),
                action = ClearLandAction,
                nextStage = TowerArrivalStage.LandingClearanceIssued,
                readbackAdvancesToStage = TowerArrivalStage.AwaitLandedObserved,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Re-issue T&G variant — gate matches ARR-LAND-TNG (explicit T&G only).
            //
            // fn-8.3 Phase 3 round 1 (codex review iteration 3): symmetric
            // to ARR-LAND-REISSUE. The `NoPendingReadback` matcher blocks
            // on either landing-clearance type in `Issued` state only;
            // escalated states allow the rule to fire and supersede the
            // prior unresolved clearance via the standard escalation
            // lifecycle. See ARR-LAND-REISSUE doc above for liveness vs
            // safety reasoning.
            AtcRule(
                id = "ARR-LAND-TNG-REISSUE",
                description = "Re-issue touch-and-go clearance after readback timeout",
                regulations = listOf(ICAO4444_7_10, ICAO9432_LANDING),
                guard = AllOf(listOf(
                    LandingConditions,
                    CircuitIntentIs(CircuitIntent.TOUCH_AND_GO),
                    NoPendingReadback(InstructionMatcher.AnyOf(listOf(
                        instructionOfType<xyz.easiersaid.twr.protocol.ClearedTouchAndGo>(),
                        instructionOfType<xyz.easiersaid.twr.protocol.ClearedToLand>(),
                    ))),
                )),
                action = ClearTouchAndGoAction,
                nextStage = TowerArrivalStage.LandingClearanceIssued,
                readbackAdvancesToStage = TowerArrivalStage.AwaitLandedObserved,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        // ── AwaitLandedObserved: aircraft on runway → handoff ────────
        TowerArrivalStage.AwaitLandedObserved to listOf(
            // fn-12 (R7): obstruction-driven GA — covers the post-readback,
            // pre-touchdown window. Fires only while the aircraft is still
            // airborne (rule's guard is `OnApproach OR OnCircuitLeg(FINAL)`,
            // both of which are airborne predicates). Once the aircraft is
            // on-runway-on-ground, the existing `ARR-TNG-AIRBORNE` /
            // `ARR-VACATE` paths take over and this rule's guard fails.
            //
            // fn-13.1 (R5): post-clearance variant — UNCHANGED from fn-12.
            obstructionGoAroundRulePostClearance,
            // Touch-and-go: aircraft rolled, lifted off again — commitment completes
            // so reconciliation forms a fresh arrival for the next circuit. No vacate,
            // no handoff: the aircraft stays with Tower.
            AtcRule(
                id = "ARR-TNG-AIRBORNE",
                description = "Touch-and-go aircraft is airborne again — complete this arrival",
                // Completing the arrival commitment so the circuit can re-form is an
                // internal state transition. §7.10 (arriving aircraft / circuit
                // sequencing) is the applicable authority; §7.10.2 is specifically the
                // *go-around* instruction and doesn't belong here.
                //
                // Gates on declared circuit traffic (IsCircuitTraffic) plus
                // not-FULL_STOP — i.e. the pilot has reported a Downwind with
                // T&G intent (or no intent → defaults to T&G). Without
                // IsCircuitTraffic, a non-circuit airborne arrival could
                // trigger spurious completion.
                //
                // fn-8.3 Phase 2 (B2): also requires that the aircraft was
                // actually observed on the runway on-ground during this
                // commitment lifetime ([TouchedDownDuringCommitment]).
                // Pre-fix, this rule fired on bare `Airborne` and produced
                // a runaway commitment ping-pong: each `ARR-LAND-TNG`
                // readback advanced the stage to `AwaitLandedObserved`,
                // `ARR-TNG-AIRBORNE` fired immediately because the aircraft
                // was airborne (even though it had never touched the
                // runway), the commitment completed, a fresh one re-formed,
                // and the cycle repeated every ~10s — saturating the
                // frequency and stepping on the pilot's FULL_STOP downwind
                // (fn-8.3 spec § Evidence § Phase 1).
                //
                // The gate matches the doctrinal definition of "touch-and-
                // go": the aircraft must have actually touched down on the
                // runway. Without the witness, the controller cannot in
                // good faith claim the arrival commitment is fulfilled by
                // the aircraft being airborne again.
                regulations = listOf(ICAO4444_7_10),
                guard = AllOf(listOf(
                    IsCircuitTraffic,
                    Not(CircuitIntentIs(CircuitIntent.FULL_STOP)),
                    Airborne,
                    TouchedDownDuringCommitment,
                )),
                nextStage = TowerArrivalStage.Complete,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Vacate instruction — direct aircraft off the runway. Skipped for
            // touch-and-go: the pilot's plan is to roll and lift off again.
            //
            // Stage advances to [AwaitVacating] on fire so the rule can't
            // retransmit while the pilot is still executing the vacate. If the
            // transmission is stepped on, the pending-readback horizon (30 s)
            // doubles as the retransmit timer via [NoPendingReadback] — the
            // rule stays in AwaitLandedObserved until a pending entry exists,
            // so a lost first shot re-fires, but a successful first shot won't
            // re-fire even after the pilot reads back (stage has advanced).
            AtcRule(
                id = "ARR-VACATE",
                description = "Vacate the runway via assigned exit or backtrack",
                regulations = listOf(ICAO4444_7_11),
                // Vacate fires for full-stop arrivals (declared FULL_STOP),
                // for non-circuit arrivals (no circuit intent declared at all —
                // a one-shot Arrival mission), and for aircraft the
                // controller has already committed to a full-stop landing
                // for via `ClearedToLand`. T&G traffic that has declared
                // touch-and-go and that the controller has not yet
                // committed to a full-stop is excluded.
                //
                // fn-8.3 Phase 3 round 1 (codex review iteration 4): the
                // third disjunct (`CoordinationIssued(ClearedToLand)`)
                // closes a wedge where a delayed Downwind delivers
                // `TOUCH_AND_GO` *after* the controller has already
                // committed to full-stop via the C4 default-flip. Without
                // it, the aircraft is on the runway with no firing rule:
                // `ARR-TNG-AIRBORNE` is false (on ground, not airborne)
                // and the original two disjuncts of `ARR-VACATE` flip to
                // false when `circuitIntent` updates to T&G. The
                // disposition-locking semantic (real ATC: "I cleared this
                // pilot to land; their disposition is now full-stop
                // regardless of any late report") is encoded by checking
                // the controller's own issued-coordination ledger rather
                // than the mutable circuit-intent belief.
                guard = AllOf(listOf(
                    OnRunway, OnGround,
                    AnyOf(listOf(
                        CircuitIntentIs(CircuitIntent.FULL_STOP),
                        Not(IsCircuitTraffic),
                        CoordinationIssued(instructionOfType<xyz.easiersaid.twr.protocol.ClearedToLand>()),
                    )),
                    NoPendingReadback(InstructionMatcher.AnyOf(listOf(
                        instructionOfType<AfterLandingVacateVia>(),
                        instructionOfType<xyz.easiersaid.twr.protocol.BacktrackRunway>(),
                    ))),
                )),
                action = VacateAction,
                nextStage = TowerArrivalStage.AwaitVacating,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        // ── AwaitVacating: aircraft leaving runway, hand off to ground ─
        TowerArrivalStage.AwaitVacating to listOf(
            // Handoff after vacating (non-T&G arrivals only).
            //
            // The stage stays in AwaitVacating after firing — the successful
            // effect is that [applyContactFrequency] transfers responsibility
            // to GND, at which point reconcile orphan-prunes the commitment.
            // If the ContactFrequency transmission is stepped on,
            // [NoPendingReadback] blocks retransmit until the pending entry
            // GCs (30 s) and the rule re-fires (CAP 413 §2.7).
            AtcRule(
                id = "ARR-VACATE-HANDOFF",
                description = "Hand off to ground control after leaving runway",
                // Transfer of *communications* (§10.1), not transfer of control
                // (§6.3). Phraseology is the frequency-change instruction per Doc 9432.
                //
                // Same intent gating as ARR-VACATE: full-stop arrivals plus
                // non-circuit arrivals get handed to ground after vacate.
                // T&G traffic continues to fly with tower.
                regulations = listOf(ICAO4444_10_1, ICAO9432_FREQUENCY_CHANGE),
                guard = AllOf(listOf(
                    OnGround, Not(OnRunway),
                    // fn-8.3 Phase 3 round 1 (codex review iteration 4):
                    // sibling of ARR-VACATE — `CoordinationIssued(ClearedToLand)`
                    // closes the late-T&G-Downwind wedge symmetrically.
                    AnyOf(listOf(
                        CircuitIntentIs(CircuitIntent.FULL_STOP),
                        Not(IsCircuitTraffic),
                        CoordinationIssued(instructionOfType<xyz.easiersaid.twr.protocol.ClearedToLand>()),
                    )),
                    NoPendingReadback(instructionOfType<ContactFrequency>()),
                    IsTransferTargetStaffed(xyz.easiersaid.twr.protocol.RoleName.GROUND),
                )),
                action = HandoffAction(xyz.easiersaid.twr.protocol.RoleName.GROUND),
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // G2 Phase I: post-landing taxi at unstaffed-GROUND. Per Doc
            // 4444 §7.6 and SERA.8005, ATC authorisation is required for
            // movement on the manoeuvring area; the prior plan's
            // "ARR-RADAR-SERVICE-TERMINATED + pilot self-taxis" violated
            // this. Doc 4444 §7.11 + §7.6 give the TWR taxi authority in
            // single-controller ops. The TWR issues TaxiToStand directly
            // when GROUND is unstaffed; ARR-RADAR-SERVICE-TERMINATED then
            // fires AFTER the taxi clearance lifecycle resolves
            // (NoActiveInstruction(TaxiToStand) gates the RST).
            AtcRule(
                id = "ARR-TAXI-TO-STAND-AT-TOWER",
                description = "Tower issues taxi-to-stand for arriving traffic when GROUND unstaffed (single-controller op)",
                regulations = listOf(ICAO4444_7_11, ICAO4444_7_6, SERA_8005_C, ICAO9432_TAXI),
                guard = AllOf(listOf(
                    OnGround, Not(OnRunway),
                    // fn-8.3 Phase 3 round 1 (codex review iteration 4):
                    // sibling of ARR-VACATE — `CoordinationIssued(ClearedToLand)`
                    // closes the late-T&G-Downwind wedge symmetrically.
                    AnyOf(listOf(
                        CircuitIntentIs(CircuitIntent.FULL_STOP),
                        Not(IsCircuitTraffic),
                        CoordinationIssued(instructionOfType<xyz.easiersaid.twr.protocol.ClearedToLand>()),
                    )),
                    Not(IsTransferTargetStaffed(xyz.easiersaid.twr.protocol.RoleName.GROUND)),
                    NoActiveInstruction(instructionOfType<xyz.easiersaid.twr.protocol.TaxiToStand>()),
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.TaxiToStand>()),
                )),
                action = TaxiToStandAction,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Pass 7 (D-PF.7 closure): boundary-release sibling for the
            // unstaffed-GROUND case. If a tower has no peer ground (small
            // field, single-controller op), release the aircraft per
            // §10.1.4. The aircraft is already on the ground here so the
            // CTR-radius gate is moot — but kept for E17 sibling-pairing.
            //
            // G2 Phase I: this rule still fires for arrivals at unstaffed-
            // GROUND aerodromes (e.g., LJMB cross-aerodrome arrival), but
            // is now a sibling to `ARR-TAXI-TO-STAND-AT-TOWER` rather
            // than a substitute. The TWR issues both: taxi-to-stand FIRST
            // (per Doc 4444 §7.6 / SERA.8005, the manoeuvring-area taxi
            // requires ATC authorisation), then radar service terminated.
            // The pilot processes both: the TaxiToStand drives mission
            // progression through TAXI_TO_STAND PHYSICAL completion; the
            // RST releases the responsibility entry. Both can fire on the
            // same controller cycle (Immediate advancement) without
            // ordering hazard since they target different mission slices.
            AtcRule(
                id = "ARR-RADAR-SERVICE-TERMINATED",
                description = "Terminate radar service when GROUND unstaffed (small-field single-controller op)",
                regulations = listOf(ICAO4444_10_1, ICAO9432_FREQUENCY_CHANGE),
                guard = AllOf(listOf(
                    OnGround, Not(OnRunway),
                    // fn-8.3 Phase 3 round 1 (codex review iteration 4):
                    // sibling of ARR-VACATE — `CoordinationIssued(ClearedToLand)`
                    // closes the late-T&G-Downwind wedge symmetrically.
                    AnyOf(listOf(
                        CircuitIntentIs(CircuitIntent.FULL_STOP),
                        Not(IsCircuitTraffic),
                        CoordinationIssued(instructionOfType<xyz.easiersaid.twr.protocol.ClearedToLand>()),
                    )),
                    Not(IsTransferTargetStaffed(xyz.easiersaid.twr.protocol.RoleName.GROUND)),
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.RadarServiceTerminated>()),
                )),
                action = TerminateRadarServiceAction(forRole = xyz.easiersaid.twr.protocol.RoleName.GROUND),
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
    ),
)
