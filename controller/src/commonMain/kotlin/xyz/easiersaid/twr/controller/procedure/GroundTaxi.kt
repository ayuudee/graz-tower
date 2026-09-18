package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.instructionOfType
import xyz.easiersaid.twr.controller.bdi.AllOf
import xyz.easiersaid.twr.controller.bdi.AtHoldingPoint
import xyz.easiersaid.twr.controller.bdi.AtStand
import xyz.easiersaid.twr.controller.bdi.AtcRule
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.ExpectedPilotAct
import xyz.easiersaid.twr.controller.bdi.GroundArrivalStage
import xyz.easiersaid.twr.controller.bdi.GroundDepartureStage
import xyz.easiersaid.twr.controller.bdi.HandoffAction
import xyz.easiersaid.twr.controller.bdi.IsTransferTargetStaffed
import xyz.easiersaid.twr.controller.bdi.NoActiveInstruction
import xyz.easiersaid.twr.controller.bdi.NoPendingReadback
import xyz.easiersaid.twr.controller.bdi.Not
import xyz.easiersaid.twr.controller.bdi.TerminateRadarServiceAction
import xyz.easiersaid.twr.controller.bdi.ProcedureSpec
import xyz.easiersaid.twr.controller.bdi.PushbackApprovalAction
import xyz.easiersaid.twr.controller.bdi.PushbackCompleted
import xyz.easiersaid.twr.controller.bdi.PushbackRequested
import xyz.easiersaid.twr.controller.bdi.Stage
import xyz.easiersaid.twr.controller.bdi.StageExpectation
import xyz.easiersaid.twr.controller.bdi.TaxiRequested
import xyz.easiersaid.twr.controller.bdi.TaxiToHoldingAction
import xyz.easiersaid.twr.controller.bdi.TaxiToStandAction
import xyz.easiersaid.twr.controller.observe.AdvancementPolicy
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_10_1
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_6
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_FREQUENCY_CHANGE
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_PUSHBACK_POWERBACK
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_TAXI
import xyz.easiersaid.twr.protocol.RadarServiceTerminated
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.TaxiClearance

/**
 * Ground taxi procedure — handles both departure and arrival flows.
 *
 * Departure: AwaitTaxiRequest → AwaitAtHolding → Complete
 * Arrival: TaxiToStand → AwaitParked → Complete
 *
 * Entry stage determined by CommitmentReconciliation based on pilot goal.
 */
fun groundTaxiProcedure(): ProcedureSpec = ProcedureSpec(
    kind = CommitmentKind.GROUND_TAXI,
    stageExpectations = groundTaxiStageExpectations(),
    stageRules = groundTaxiStageRules(),
)

private fun groundTaxiStageExpectations(): Map<Stage, StageExpectation> =
    mapOf(
        GroundDepartureStage.AwaitTaxiRequest to StageExpectation(
            ExpectedPilotAct.RequestTaxi,
            "Request taxi to the holding point — the controller needs to know you're ready to move",
            regulations = listOf(ICAO9432_TAXI),
        ),
        GroundDepartureStage.AwaitPushbackCompletion to StageExpectation(
            ExpectedPilotAct.RequestTaxi,
            "Wait for pushback completion and ground-crew signal before requesting taxi",
            regulations = listOf(ICAO9432_PUSHBACK_POWERBACK),
        ),
    )

private fun groundTaxiStageRules(): Map<Stage, List<AtcRule>> =
    mapOf(
        GroundDepartureStage.AwaitTaxiRequest to awaitTaxiRequestRules(),
        GroundDepartureStage.AwaitPushbackCompletion to awaitPushbackCompletionRules(),
        GroundDepartureStage.AwaitAtHolding to awaitAtHoldingRules(),
        GroundArrivalStage.TaxiToStand to taxiToStandRules(),
        GroundArrivalStage.AwaitParked to awaitParkedRules(),
    )

private fun awaitTaxiRequestRules(): List<AtcRule> = listOf(
    AtcRule(
        id = "GND-PUSHBACK-APPROVE",
        description = "Approve pushback before the aircraft requests taxi",
        regulations = listOf(ICAO9432_PUSHBACK_POWERBACK),
        guard = PushbackRequested,
        action = PushbackApprovalAction,
        nextStage = GroundDepartureStage.AwaitPushbackCompletion,
        advancementPolicy = AdvancementPolicy.Immediate,
    ),
    AtcRule(
        id = "GND-TAXI",
        description = "Taxi to holding point for departure",
        regulations = listOf(ICAO4444_7_6, ICAO9432_TAXI),
        guard = TaxiRequested,
        action = TaxiToHoldingAction,
        nextStage = GroundDepartureStage.AwaitAtHolding,
        advancementPolicy = AdvancementPolicy.Immediate,
    ),
)

private fun awaitPushbackCompletionRules(): List<AtcRule> = listOf(
    AtcRule(
        id = "GND-TAXI-AFTER-PUSHBACK",
        description = "Taxi to holding point after pushback completion signal",
        regulations = listOf(ICAO4444_7_6, ICAO9432_TAXI),
        guard = AllOf(listOf(TaxiRequested, PushbackCompleted)),
        action = TaxiToHoldingAction,
        nextStage = GroundDepartureStage.AwaitAtHolding,
        advancementPolicy = AdvancementPolicy.Immediate,
    ),
)

private fun awaitAtHoldingRules(): List<AtcRule> = listOf(
    AtcRule(
        id = "GND-HANDOFF",
        description = "Hand departing traffic to tower at holding point",
        regulations = listOf(ICAO4444_10_1, ICAO9432_FREQUENCY_CHANGE),
        guard = AllOf(listOf(
            AtHoldingPoint,
            NoPendingReadback(instructionOfType<ContactFrequency>()),
            IsTransferTargetStaffed(RoleName.TOWER),
        )),
        action = HandoffAction(RoleName.TOWER),
        advancementPolicy = AdvancementPolicy.Immediate,
    ),
    AtcRule(
        id = "GND-RADAR-SERVICE-TERMINATED",
        description = "Terminate service when TOWER unstaffed and aircraft at holding point",
        regulations = listOf(ICAO4444_7_6, ICAO9432_TAXI),
        guard = AllOf(listOf(
            AtHoldingPoint,
            Not(IsTransferTargetStaffed(RoleName.TOWER)),
            NoPendingReadback(instructionOfType<RadarServiceTerminated>()),
        )),
        action = TerminateRadarServiceAction(forRole = RoleName.TOWER),
        advancementPolicy = AdvancementPolicy.Immediate,
    ),
)

private fun taxiToStandRules(): List<AtcRule> = listOf(
    AtcRule(
        id = "GND-TAXI-STAND",
        description = "Taxi arriving traffic to parking stand",
        regulations = listOf(ICAO4444_7_6, ICAO9432_TAXI),
        guard = NoActiveInstruction(instructionOfType<TaxiClearance>()),
        action = TaxiToStandAction,
        nextStage = GroundArrivalStage.AwaitParked,
        advancementPolicy = AdvancementPolicy.Immediate,
    ),
)

private fun awaitParkedRules(): List<AtcRule> = listOf(
    AtcRule(
        id = "GND-PARKED",
        description = "Ground arrival complete when aircraft at stand",
        regulations = listOf(ICAO9432_TAXI),
        guard = AtStand,
        nextStage = GroundArrivalStage.Complete,
        advancementPolicy = AdvancementPolicy.Immediate,
    ),
    AtcRule(
        id = "GND-TAXI-STAND-REISSUE",
        description = "Re-issue taxi-to-stand after readback timeout",
        regulations = listOf(ICAO4444_7_6, ICAO9432_TAXI),
        guard = AllOf(listOf(
            Not(AtStand),
            NoPendingReadback(instructionOfType<TaxiClearance>()),
        )),
        action = TaxiToStandAction,
        advancementPolicy = AdvancementPolicy.Immediate,
    ),
)
