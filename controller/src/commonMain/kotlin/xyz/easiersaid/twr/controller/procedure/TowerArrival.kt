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
import xyz.easiersaid.twr.controller.bdi.IsTransferTargetStaffed
import xyz.easiersaid.twr.controller.bdi.JoinCircuitAction
import xyz.easiersaid.twr.controller.bdi.TaxiToStandAction
import xyz.easiersaid.twr.controller.bdi.TerminateRadarServiceAction
import xyz.easiersaid.twr.controller.bdi.InCircuit
import xyz.easiersaid.twr.controller.bdi.InstructionMatcher
import xyz.easiersaid.twr.controller.bdi.IsCircuitTraffic
import xyz.easiersaid.twr.controller.bdi.NoActiveInstruction
import xyz.easiersaid.twr.controller.bdi.NoPendingReadback
import xyz.easiersaid.twr.controller.bdi.Not
import xyz.easiersaid.twr.controller.bdi.OnApproach
import xyz.easiersaid.twr.controller.bdi.OnCircuitLeg
import xyz.easiersaid.twr.controller.bdi.OnGround
import xyz.easiersaid.twr.controller.bdi.OnRunway
import xyz.easiersaid.twr.controller.bdi.PositionReported
import xyz.easiersaid.twr.controller.bdi.ProcedureInterrupt
import xyz.easiersaid.twr.controller.bdi.ProcedureSpec
import xyz.easiersaid.twr.controller.bdi.ReportFinalAction
import xyz.easiersaid.twr.controller.bdi.RunwayAccessGranted
import xyz.easiersaid.twr.controller.bdi.RunwayLengthOperation
import xyz.easiersaid.twr.controller.bdi.RunwayLengthSufficient
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
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ReportWhen
import xyz.easiersaid.twr.protocol.Urgency
import xyz.easiersaid.twr.controller.observe.AdvancementPolicy

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
    // Pass 13 (D-AUDIT.4.A-FOLLOWUP closure): runway must be long enough
    // for the aircraft's landing LDA. Shared by ARR-LAND, ARR-LAND-TNG,
    // and their re-issue rules. Fails closed for unknown designator or
    // absent declared distances.
    RunwayLengthSufficient(RunwayLengthOperation.LANDING),
))

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
            // Clear to land — VFR, full-stop intent declared
            AtcRule(
                id = "ARR-LAND",
                description = "Clear to land when on final and runway available, pilot declared full-stop",
                regulations = listOf(ICAO4444_7_10, ICAO9432_LANDING),
                guard = AllOf(listOf(LandingConditions, CircuitIntentIs(CircuitIntent.FULL_STOP))),
                action = ClearLandAction,
                nextStage = TowerArrivalStage.LandingClearanceIssued,
                readbackAdvancesToStage = TowerArrivalStage.AwaitLandedObserved,
                urgency = Urgency.TIME_SENSITIVE,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Clear touch-and-go — default for circuit traffic that has not declared full-stop
            AtcRule(
                id = "ARR-LAND-TNG",
                description = "Clear touch-and-go when on final and runway available (default for circuit traffic)",
                regulations = listOf(ICAO4444_7_10, ICAO9432_LANDING),
                guard = AllOf(listOf(LandingConditions, Not(CircuitIntentIs(CircuitIntent.FULL_STOP)))),
                action = ClearTouchAndGoAction,
                nextStage = TowerArrivalStage.LandingClearanceIssued,
                readbackAdvancesToStage = TowerArrivalStage.AwaitLandedObserved,
                urgency = Urgency.TIME_SENSITIVE,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Continue approach when runway not yet clear
            AtcRule(
                id = "ARR-CONTINUE",
                description = "Continue approach when on final but runway not yet clear",
                regulations = listOf(ICAO9432_CONTINUE_APPROACH, CAP413_4_55),
                guard = AllOf(listOf(
                    AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
                    AnyOf(listOf(Not(RunwayAccessGranted), Not(RunwayPhysicallyClear))),
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
            AtcRule(
                id = "ARR-LAND-REISSUE",
                description = "Re-issue landing clearance after readback timeout",
                regulations = listOf(ICAO4444_7_10, ICAO9432_LANDING),
                guard = AllOf(listOf(
                    LandingConditions,
                    CircuitIntentIs(CircuitIntent.FULL_STOP),
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
            // Re-issue T&G variant
            AtcRule(
                id = "ARR-LAND-TNG-REISSUE",
                description = "Re-issue touch-and-go clearance after readback timeout",
                regulations = listOf(ICAO4444_7_10, ICAO9432_LANDING),
                guard = AllOf(listOf(
                    LandingConditions,
                    Not(CircuitIntentIs(CircuitIntent.FULL_STOP)),
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.ClearedTouchAndGo>()),
                )),
                action = ClearTouchAndGoAction,
                nextStage = TowerArrivalStage.LandingClearanceIssued,
                readbackAdvancesToStage = TowerArrivalStage.AwaitLandedObserved,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        // ── AwaitLandedObserved: aircraft on runway → handoff ────────
        TowerArrivalStage.AwaitLandedObserved to listOf(
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
                regulations = listOf(ICAO4444_7_10),
                guard = AllOf(listOf(IsCircuitTraffic, Not(CircuitIntentIs(CircuitIntent.FULL_STOP)), Airborne)),
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
                // Vacate fires for full-stop arrivals (declared FULL_STOP) and
                // for non-circuit arrivals (no circuit intent declared at all —
                // a one-shot Arrival mission). T&G traffic that has declared
                // touch-and-go is excluded.
                guard = AllOf(listOf(
                    OnRunway, OnGround,
                    AnyOf(listOf(CircuitIntentIs(CircuitIntent.FULL_STOP), Not(IsCircuitTraffic))),
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
                    AnyOf(listOf(CircuitIntentIs(CircuitIntent.FULL_STOP), Not(IsCircuitTraffic))),
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
                    AnyOf(listOf(CircuitIntentIs(CircuitIntent.FULL_STOP), Not(IsCircuitTraffic))),
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
                    AnyOf(listOf(CircuitIntentIs(CircuitIntent.FULL_STOP), Not(IsCircuitTraffic))),
                    Not(IsTransferTargetStaffed(xyz.easiersaid.twr.protocol.RoleName.GROUND)),
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.RadarServiceTerminated>()),
                )),
                action = TerminateRadarServiceAction(forRole = xyz.easiersaid.twr.protocol.RoleName.GROUND),
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
    ),
)
