package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.*
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_10_1
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_6
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_9
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_9_3
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_CONDITIONAL
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_FREQUENCY_CHANGE
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_LINEUP
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_READY
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_TAKEOFF
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9870_RUNWAY_INCURSION
import xyz.easiersaid.twr.protocol.RegulationDatabase.SERA_5001
import xyz.easiersaid.twr.protocol.RegulationDatabase.SERA_5005
import xyz.easiersaid.twr.protocol.Urgency

private val DepartureTrigger = AnyOf(listOf(PilotReady, AiProactive))

fun towerDepartureProcedure(): ProcedureSpec = ProcedureSpec(
    kind = CommitmentKind.TOWER_DEPARTURE,
    stageExpectations = mapOf(
        TowerDepartureStage.AwaitReady to StageExpectation(
            ExpectedPilotAct.ReadyForDeparture,
            "Report ready for departure at the holding point so the controller can sequence you for the runway",
            regulations = listOf(ICAO9432_READY),
        ),
    ),
    stageRules = mapOf(
        TowerDepartureStage.AwaitReady to listOf(
            AtcRule(
                id = "DEP-RUNWAY-INCURSION",
                description = "Hold position — aircraft on runway without clearance",
                regulations = listOf(ICAO4444_7_6, ICAO9870_RUNWAY_INCURSION),
                guard = AllOf(listOf(OnRunway, NoRunwayClearanceIssued)),
                action = HoldPositionAction,
                urgency = Urgency.SAFETY,
            ),
            AtcRule(
                id = "DEP-HOLD-IMC",
                description = "Hold departing VFR traffic when weather below VMC minima",
                // SERA.5001 defines VMC minima but does not forbid dispatch on its own.
                // SERA.5005 is the operative rule: a VFR flight shall not be operated
                // when conditions are below those minima. That's the sole authority here.
                regulations = listOf(SERA_5005),
                guard = AllOf(listOf(DepartureTrigger, ContactEstablished, Not(WeatherPermitsVfr))),
                action = HoldPositionAction,
            ),
            AtcRule(
                id = "DEP-LUAW",
                description = "Line up and wait when pilot ready and runway available",
                regulations = listOf(ICAO4444_7_9, ICAO4444_7_9_3, ICAO9432_LINEUP),
                guard = AllOf(listOf(
                    DepartureTrigger,
                    ContactEstablished,
                    WeatherPermitsVfr,
                    RunwayAccessGranted,
                    RunwayPhysicallyClear,
                    // Never line up in front of an arrival on short final — even with
                    // runway access the approach buffer would be eroded below ICAO minima.
                    Not(OtherTrafficOnShortFinal),
                )),
                action = LineUpAction,
                nextStage = TowerDepartureStage.AwaitLineUpObserved,
                stampReadyAt = true,
            ),
            // Conditional line-up: runway occupied but access granted
            AtcRule(
                id = "DEP-LUAW-COND",
                description = "Conditional line-up behind landing/departing traffic",
                regulations = listOf(ICAO4444_7_9_3, ICAO9432_CONDITIONAL),
                guard = AllOf(listOf(
                    DepartureTrigger,
                    ContactEstablished,
                    WeatherPermitsVfr,
                    RunwayAccessGranted,
                    // Also blocked: conditional line-up in front of landing traffic
                    // would still put the departure on the runway ahead of the arrival.
                    Not(OtherTrafficOnShortFinal),
                )),
                action = ConditionalLineUpAction,
                nextStage = TowerDepartureStage.AwaitLineUpObserved,
                stampReadyAt = true,
            ),
        ),
        TowerDepartureStage.AwaitLineUpObserved to listOf(
            AtcRule(
                id = "DEP-HOLD-LINEUP-IMC",
                description = "Hold on runway when weather deteriorates below VMC",
                regulations = listOf(SERA_5001, SERA_5005),
                guard = AllOf(listOf(OnRunway, OnGround, Not(WeatherPermitsVfr))),
                action = HoldPositionAction,
            ),
            AtcRule(
                id = "DEP-TAKEOFF",
                description = "Cleared for takeoff when lined up, runway clear, and weather permits",
                regulations = listOf(ICAO4444_7_9, ICAO9432_TAKEOFF),
                guard = AllOf(listOf(
                    OnRunway, OnGround,
                    WeatherPermitsVfr,
                    RunwayAccessGranted,
                    RunwayPhysicallyClear,
                )),
                action = ClearTakeoffAction,
                nextStage = TowerDepartureStage.AwaitTakeoffObserved,
            ),
            AtcRule(
                id = "DEP-HOLD-LINEUP",
                description = "Hold position on runway when runway no longer available",
                regulations = listOf(ICAO4444_7_9),
                guard = AllOf(listOf(
                    OnRunway, OnGround,
                    NoActiveInstruction(instructionOfType<xyz.easiersaid.twr.protocol.HoldPosition>()),
                )),
                action = HoldPositionAction,
            ),
        ),
        TowerDepartureStage.AwaitTakeoffObserved to listOf(
            // Cancel takeoff if runway becomes occupied after clearance.
            // Defense-in-depth: the Lean runway kernel should prevent this scenario
            // (runway commitment coherence). If this fires, something went wrong upstream.
            AtcRule(
                id = "DEP-CANCEL-TAKEOFF",
                description = "Cancel takeoff — runway no longer clear after clearance issued",
                regulations = listOf(ICAO4444_7_9),
                guard = AllOf(listOf(OnRunway, OnGround, Not(RunwayPhysicallyClear))),
                action = CancelTakeoffAction,
                urgency = Urgency.SAFETY,
                // Stay at AWAIT_TAKEOFF_OBSERVED — re-evaluate next cycle
            ),
            AtcRule(
                id = "DEP-HANDOFF",
                description = "Hand departing traffic to approach/area control after climb-out",
                // Transfer of *communications* (§10.1), not transfer of control
                // (§6.3). Phraseology is the frequency-change instruction per Doc 9432.
                regulations = listOf(ICAO4444_10_1, ICAO9432_FREQUENCY_CHANGE),
                // Wait until the aircraft is on the upwind/crosswind climb-out rather
                // than handing off the moment the wheels leave the ground — tower needs
                // to watch the initial climb for aborts / engine failures (CAP 413 §4.46).
                // Idempotent: the ContactFrequency is in-flight for several cycles,
                // so we gate on NoPendingReadback to avoid queueing duplicates. The
                // 30 s GC horizon in the pending register doubles as the retransmit
                // timer (CAP 413 §2.7).
                guard = AllOf(listOf(
                    Airborne,
                    AnyOf(listOf(OnCircuitLeg(LegName.UPWIND), OnCircuitLeg(LegName.CROSSWIND))),
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.ContactFrequency>()),
                )),
                action = HandoffAction(xyz.easiersaid.twr.protocol.RoleName.APPROACH),
            ),
        ),
    ),
)
