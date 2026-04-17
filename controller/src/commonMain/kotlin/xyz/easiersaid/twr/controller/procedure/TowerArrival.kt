package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.controller.bdi.*
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.protocol.RegulationDatabase.CAP413_4_55
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_5
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_CIRCUIT_REPORTS
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
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
import xyz.easiersaid.twr.protocol.Urgency

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
            fromStages = setOf(TowerArrivalStage.AwaitLandedObserved),
            guard = GoAroundEvent,
            targetStage = TowerArrivalStage.AwaitDownwind,
        ),
    ),
    stageRules = mapOf(
        // ── AwaitDownwind: acknowledge position or wait ──────────────
        TowerArrivalStage.AwaitDownwind to listOf(
            AtcRule(
                id = "ARR-DOWNWIND-ACK",
                description = "Acknowledge downwind report and advance to approach sequencing",
                // Position-report acknowledgement is phraseology-only — no clearance or
                // sequencing action is taken here. §7.10 (arriving aircraft) is reserved
                // for rules that actually dispose of the approach.
                regulations = listOf(ICAO9432_CIRCUIT_REPORTS),
                guard = AllOf(listOf(InCircuit, PositionReported)),
                nextStage = TowerArrivalStage.AwaitApproach,
            ),
            AtcRule(
                id = "ARR-ADVANCE-APPROACH",
                description = "Aircraft already on approach — advance to approach sequencing",
                regulations = listOf(ICAO4444_7_10),
                guard = OnApproach,
                nextStage = TowerArrivalStage.AwaitApproach,
            ),
            // Fallback for AI aircraft in circuit without position report
            AtcRule(
                id = "ARR-AI-ADVANCE",
                description = "AI aircraft in circuit — advance to approach sequencing without position report",
                regulations = listOf(ICAO4444_7_10),
                guard = AllOf(listOf(InCircuit, AiProactive)),
                nextStage = TowerArrivalStage.AwaitApproach,
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
                    // Retransmit via pending-readback horizon — ExtendDownwind has no
                    // required-atom readback, so the pending entry ages out after 30 s
                    // (MAX_READBACK_AGE), keeping re-issues to the CAP 413 §2.7 cadence.
                    NoPendingReadback(instructionOfType<ExtendDownwind>()),
                )),
                action = ExtendDownwindAction,
                urgency = Urgency.TIME_SENSITIVE,
            ),
            // Turn base — resolves persistent ExtendDownwind when aircraft reaches base/final
            // with runway access. A ClearedToLand also resolves via domain supersession.
            AtcRule(
                id = "ARR-TURN-BASE",
                description = "Turn base when runway access granted after extend downwind",
                regulations = listOf(ICAO4444_7_10),
                guard = AllOf(listOf(
                    OnCircuitLeg(LegName.BASE),
                    RunwayAccessGranted,
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.TurnBase>()),
                )),
                action = TurnBaseAction,
                urgency = Urgency.PROGRESSION,
            ),
            // Clear to land — VFR, not touch-and-go
            AtcRule(
                id = "ARR-LAND",
                description = "Clear to land when on final and runway available",
                regulations = listOf(ICAO4444_7_10, ICAO9432_LANDING),
                guard = AllOf(listOf(
                    AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
                    WeatherPermitsVfr,
                    RunwayAccessGranted,
                    RunwayPhysicallyClear,
                    Not(PilotGoalIs(PilotGoal.TOUCH_AND_GO)),
                )),
                action = ClearLandAction,
                nextStage = TowerArrivalStage.AwaitLandedObserved,
                urgency = Urgency.TIME_SENSITIVE,
            ),
            // Clear touch-and-go
            AtcRule(
                id = "ARR-LAND-TNG",
                description = "Clear touch-and-go when on final and runway available",
                regulations = listOf(ICAO4444_7_10, ICAO9432_LANDING),
                guard = AllOf(listOf(
                    AnyOf(listOf(OnApproach, OnCircuitLeg(LegName.FINAL))),
                    WeatherPermitsVfr,
                    RunwayAccessGranted,
                    RunwayPhysicallyClear,
                    PilotGoalIs(PilotGoal.TOUCH_AND_GO),
                )),
                action = ClearTouchAndGoAction,
                nextStage = TowerArrivalStage.AwaitLandedObserved,
                urgency = Urgency.TIME_SENSITIVE,
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
                regulations = listOf(ICAO4444_7_10),
                guard = AllOf(listOf(PilotGoalIs(PilotGoal.TOUCH_AND_GO), Airborne)),
                nextStage = TowerArrivalStage.Complete,
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
                guard = AllOf(listOf(
                    OnRunway, OnGround,
                    Not(PilotGoalIs(PilotGoal.TOUCH_AND_GO)),
                    NoPendingReadback(InstructionMatcher.AnyOf(listOf(
                        instructionOfType<AfterLandingVacateVia>(),
                        instructionOfType<xyz.easiersaid.twr.protocol.BacktrackRunway>(),
                    ))),
                )),
                action = VacateAction,
                nextStage = TowerArrivalStage.AwaitVacating,
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
                regulations = listOf(ICAO4444_10_1, ICAO9432_FREQUENCY_CHANGE),
                guard = AllOf(listOf(
                    OnGround, Not(OnRunway),
                    Not(PilotGoalIs(PilotGoal.TOUCH_AND_GO)),
                    NoPendingReadback(instructionOfType<ContactFrequency>()),
                )),
                action = HandoffAction(xyz.easiersaid.twr.protocol.RoleName.GROUND),
            ),
        ),
    ),
)
