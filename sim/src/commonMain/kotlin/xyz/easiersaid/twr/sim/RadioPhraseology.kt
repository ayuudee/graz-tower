package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AfterPassingLevelClimbTo
import xyz.easiersaid.twr.protocol.AfterPassingLevelDescendTo
import xyz.easiersaid.twr.protocol.AirTaxiTo
import xyz.easiersaid.twr.protocol.AerodromeInstruction
import xyz.easiersaid.twr.protocol.ApproachInstruction
import xyz.easiersaid.twr.protocol.AvoidArea
import xyz.easiersaid.twr.protocol.AvoidLevel
import xyz.easiersaid.twr.protocol.BacktrackReadback
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.BreakOff
import xyz.easiersaid.twr.protocol.BreakOffReadback
import xyz.easiersaid.twr.protocol.CancelClearance
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.Clearance
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedForTakeoffReadback
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedApproachReadback
import xyz.easiersaid.twr.protocol.ClearedLowApproach
import xyz.easiersaid.twr.protocol.ClearedLowApproachReadback
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedToLandReadback
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ClearedTouchAndGoReadback
import xyz.easiersaid.twr.protocol.ClearedVisualApproach
import xyz.easiersaid.twr.protocol.ClimbTo
import xyz.easiersaid.twr.protocol.CommenceApproachAt
import xyz.easiersaid.twr.protocol.ConditionalClearance
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ContinuePresentHeading
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ConfirmSquawk
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.CrossRunwayReadback
import xyz.easiersaid.twr.protocol.DescendTo
import xyz.easiersaid.twr.protocol.DescendWhenReady
import xyz.easiersaid.twr.protocol.Disregard
import xyz.easiersaid.twr.protocol.DisregardAcknowledgementReadback
import xyz.easiersaid.twr.protocol.DivertTo
import xyz.easiersaid.twr.protocol.EmergencyInstruction
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.ExtendDownwindReadback
import xyz.easiersaid.twr.protocol.ExpediteClimb
import xyz.easiersaid.twr.protocol.ExpediteDescend
import xyz.easiersaid.twr.protocol.ExpediteTaxi
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.FollowTraffic
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.FrequencyInstruction
import xyz.easiersaid.twr.protocol.FrequencyReadback
import xyz.easiersaid.twr.protocol.FreeTextReadback
import xyz.easiersaid.twr.protocol.GiveWayToTraffic
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.GoAroundReadback
import xyz.easiersaid.twr.protocol.GroundInstruction
import xyz.easiersaid.twr.protocol.HeadingReadback
import xyz.easiersaid.twr.protocol.HoldAt
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldPositionCancelTakeoff
import xyz.easiersaid.twr.protocol.HoldReadback
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.HoldShortReadback
import xyz.easiersaid.twr.protocol.HoldingAcknowledgementReadback
import xyz.easiersaid.twr.protocol.IncreaseSpeedTo
import xyz.easiersaid.twr.protocol.InterceptLocaliser
import xyz.easiersaid.twr.protocol.JoinAirway
import xyz.easiersaid.twr.protocol.JoinAirwayReadback
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.LeaveHoldProceedDirect
import xyz.easiersaid.twr.protocol.LeaveHoldProceedDirectReadback
import xyz.easiersaid.twr.protocol.LevelInstruction
import xyz.easiersaid.twr.protocol.LevelReadback
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.LineUpReadback
import xyz.easiersaid.twr.protocol.MaintainAltitudeUntilEstablished
import xyz.easiersaid.twr.protocol.MaintainAtOrAbove
import xyz.easiersaid.twr.protocol.MaintainAtOrBelow
import xyz.easiersaid.twr.protocol.MaintainLevel
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.MaintainVisualSeparation
import xyz.easiersaid.twr.protocol.MakeAnotherCircuit
import xyz.easiersaid.twr.protocol.MakeLongApproach
import xyz.easiersaid.twr.protocol.MakeShortApproach
import xyz.easiersaid.twr.protocol.MinimumCleanSpeed
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.NumberInSequence
import xyz.easiersaid.twr.protocol.Orbit
import xyz.easiersaid.twr.protocol.OrbitReadback
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PressureSettingReadback
import xyz.easiersaid.twr.protocol.ProceedDirect
import xyz.easiersaid.twr.protocol.PushbackApproved
import xyz.easiersaid.twr.protocol.PushbackFace
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.RadarServiceTerminated
import xyz.easiersaid.twr.protocol.ReduceSpeedTo
import xyz.easiersaid.twr.protocol.ReduceTaxiSpeed
import xyz.easiersaid.twr.protocol.RejoinSidAt
import xyz.easiersaid.twr.protocol.RejoinSidAtReadback
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.ReportIntentions
import xyz.easiersaid.twr.protocol.ReportInstruction
import xyz.easiersaid.twr.protocol.ReportTrafficInSight
import xyz.easiersaid.twr.protocol.ReportWhen
import xyz.easiersaid.twr.protocol.ResumeNormalSpeed
import xyz.easiersaid.twr.protocol.ResumeOwnNavigation
import xyz.easiersaid.twr.protocol.ResumeOwnNavigationReadback
import xyz.easiersaid.twr.protocol.RouteInstruction
import xyz.easiersaid.twr.protocol.RouteAsFiled
import xyz.easiersaid.twr.protocol.RouteAsFiledReadback
import xyz.easiersaid.twr.protocol.RouteReadback
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.RunwayInUseAdvisory
import xyz.easiersaid.twr.protocol.RunwayInUseReadback
import xyz.easiersaid.twr.protocol.RunwayInstruction
import xyz.easiersaid.twr.protocol.RunwayReadback
import xyz.easiersaid.twr.protocol.SetPressure
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.SequenceAcknowledgementReadback
import xyz.easiersaid.twr.protocol.SequencingInstruction
import xyz.easiersaid.twr.protocol.SimpleElement
import xyz.easiersaid.twr.protocol.SpecialVfrClearance
import xyz.easiersaid.twr.protocol.SpecialVfrReadback
import xyz.easiersaid.twr.protocol.SpeedInstruction
import xyz.easiersaid.twr.protocol.SpeedReadback
import xyz.easiersaid.twr.protocol.SquawkIdent
import xyz.easiersaid.twr.protocol.SquawkNormal
import xyz.easiersaid.twr.protocol.SquawkReadback
import xyz.easiersaid.twr.protocol.SquawkStandby
import xyz.easiersaid.twr.protocol.StopImmediately
import xyz.easiersaid.twr.protocol.StopImmediatelyReadback
import xyz.easiersaid.twr.protocol.StopClimbAt
import xyz.easiersaid.twr.protocol.StopDescentAt
import xyz.easiersaid.twr.protocol.StopSquawk
import xyz.easiersaid.twr.protocol.StopTurn
import xyz.easiersaid.twr.protocol.StartupApproved
import xyz.easiersaid.twr.protocol.SurveillanceInstruction
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrHoldShort
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrHoldShortReadback
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrVacateReadback
import xyz.easiersaid.twr.protocol.TakeoffImmediatelyOrVacateRunway
import xyz.easiersaid.twr.protocol.TaxiRouteReadback
import xyz.easiersaid.twr.protocol.TaxiIntoHoldingBay
import xyz.easiersaid.twr.protocol.TaxiToStand
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiViaRunway
import xyz.easiersaid.twr.protocol.TaxiViaRunwayReadback
import xyz.easiersaid.twr.protocol.TaxiWithCaution
import xyz.easiersaid.twr.protocol.TransitionLevelIssuance
import xyz.easiersaid.twr.protocol.TransitionLevelReadback
import xyz.easiersaid.twr.protocol.TurnBase
import xyz.easiersaid.twr.protocol.TurnByDegrees
import xyz.easiersaid.twr.protocol.TurnHeading
import xyz.easiersaid.twr.protocol.VacateReadback
import xyz.easiersaid.twr.protocol.VacateRunway
import xyz.easiersaid.twr.protocol.VectorInstruction
import xyz.easiersaid.twr.protocol.VisualApproachReadback
import xyz.easiersaid.twr.protocol.WhenAbleProceedDirect

enum class PhraseologyObligationKind {
    MandatoryWords,
    OrderedPhrase,
    SemanticSlot,
    ExampleDialogue,
    Readback,
    AmbiguityAbsence,
    ForbiddenMeaning,
}

enum class RenderedPhraseologyTemplate {
    TakeoffClearance,
    TouchAndGoClearance,
    LineUpAndWaitInstruction,
    LineUpReadback,
    ContactFrequencyInstruction,
    FrequencyReadback,
    StopImmediatelyInstruction,
    FinalReport,
    LongFinalReport,
    TaxiToStandInstruction,
    TaxiRouteReadback,
    RunwayVacatedReport,
    VehicleInitialCall,
    VehicleTowRequest,
}

@JvmInline
value class RenderedPhraseText(val value: String) {
    init {
        require(value.isNotBlank()) { "rendered phraseology text must not be blank" }
    }
}

sealed interface PhraseologyToken {
    data class AircraftCallsign(val aircraftId: AircraftId) : PhraseologyToken
    data class VehicleCallsign(val callsign: Callsign) : PhraseologyToken
    data class StationName(val station: ControllerId) : PhraseologyToken
    data class AircraftTypeName(val aircraftType: AircraftType) : PhraseologyToken
    data class OperatorName(val operator: AircraftOperator) : PhraseologyToken
    data class RunwayDesignator(val runway: RunwayId) : PhraseologyToken
    data object Runway : PhraseologyToken
    data object Cleared : PhraseologyToken
    data object For : PhraseologyToken
    data object TakeOff : PhraseologyToken
    data object Line : PhraseologyToken
    data object Up : PhraseologyToken
    data object Lining : PhraseologyToken
    data object Wait : PhraseologyToken
    data object Contact : PhraseologyToken
    data class UnitName(val value: String) : PhraseologyToken
    data class FrequencyValue(val frequency: Frequency) : PhraseologyToken
    data object Stop : PhraseologyToken
    data object Immediately : PhraseologyToken
    data object Touch : PhraseologyToken
    data object And : PhraseologyToken
    data object Go : PhraseologyToken
    data object Final : PhraseologyToken
    data object Long : PhraseologyToken
    data object Taxi : PhraseologyToken
    data object To : PhraseologyToken
    data object Via : PhraseologyToken
    data object Request : PhraseologyToken
    data object Tow : PhraseologyToken
    data object Vacated : PhraseologyToken
    data class PointName(val point: PointId) : PhraseologyToken
}

data class RenderedControllerPhraseology(
    val template: RenderedPhraseologyTemplate,
    val obligationKinds: Set<PhraseologyObligationKind>,
    val tokens: List<PhraseologyToken>,
    val text: RenderedPhraseText,
) {
    init {
        require(obligationKinds.isNotEmpty()) { "rendered phraseology must name obligation kinds" }
        require(tokens.isNotEmpty()) { "rendered phraseology must carry tokens" }
    }
}

sealed interface ControllerPhraseologyRenderResult {
    data class Rendered(val phraseology: RenderedControllerPhraseology) : ControllerPhraseologyRenderResult
    data class UnsupportedInstruction(val instruction: AtcInstruction) : ControllerPhraseologyRenderResult
}

data class RenderedPilotReadbackPhraseology(
    val template: RenderedPhraseologyTemplate,
    val obligationKinds: Set<PhraseologyObligationKind>,
    val tokens: List<PhraseologyToken>,
    val text: RenderedPhraseText,
) {
    init {
        require(obligationKinds.isNotEmpty()) { "rendered pilot readback phraseology must name obligation kinds" }
        require(tokens.isNotEmpty()) { "rendered pilot readback phraseology must carry tokens" }
    }
}

sealed interface PilotReadbackPhraseologyRenderResult {
    data class Rendered(val phraseology: RenderedPilotReadbackPhraseology) : PilotReadbackPhraseologyRenderResult
    data class UnsupportedReadback(val readback: Readback) : PilotReadbackPhraseologyRenderResult
}

data class RenderedPilotReportPhraseology(
    val template: RenderedPhraseologyTemplate,
    val obligationKinds: Set<PhraseologyObligationKind>,
    val tokens: List<PhraseologyToken>,
    val text: RenderedPhraseText,
) {
    init {
        require(obligationKinds.isNotEmpty()) { "rendered pilot report phraseology must name obligation kinds" }
        require(tokens.isNotEmpty()) { "rendered pilot report phraseology must carry tokens" }
    }
}

sealed interface PilotReportPhraseologyRenderResult {
    data class Rendered(val phraseology: RenderedPilotReportPhraseology) : PilotReportPhraseologyRenderResult
    data class UnsupportedReport(val report: Report) : PilotReportPhraseologyRenderResult
}

data class RenderedVehicleDriverPhraseology(
    val template: RenderedPhraseologyTemplate,
    val obligationKinds: Set<PhraseologyObligationKind>,
    val tokens: List<PhraseologyToken>,
    val text: RenderedPhraseText,
) {
    init {
        require(obligationKinds.isNotEmpty()) { "rendered vehicle phraseology must name obligation kinds" }
        require(tokens.isNotEmpty()) { "rendered vehicle phraseology must carry tokens" }
    }
}

sealed interface VehicleDriverPhraseologyRenderResult {
    data class Rendered(val phraseology: RenderedVehicleDriverPhraseology) : VehicleDriverPhraseologyRenderResult
    data class UnsupportedTransmission(
        val transmission: VehicleDriverTransmission,
    ) : VehicleDriverPhraseologyRenderResult
}

fun renderControllerPhraseology(output: ControllerOutput.Instruct): ControllerPhraseologyRenderResult {
    return when (val instruction = output.instruction) {
        is GroundInstruction -> renderGroundInstructionPhraseology(output.target, instruction)
        is RunwayInstruction -> renderRunwayInstructionPhraseology(output.target, instruction)
        is RouteInstruction -> unsupportedInstruction(instruction)
        is VectorInstruction -> unsupportedInstruction(instruction)
        is LevelInstruction -> unsupportedInstruction(instruction)
        is SpeedInstruction -> unsupportedInstruction(instruction)
        is ApproachInstruction -> renderApproachInstructionPhraseology(instruction)
        is ReportInstruction -> unsupportedInstruction(instruction)
        is FrequencyInstruction -> renderFrequencyInstructionPhraseology(output.target, instruction)
        is SurveillanceInstruction -> unsupportedInstruction(instruction)
        is SequencingInstruction -> unsupportedInstruction(instruction)
        is AerodromeInstruction -> unsupportedInstruction(instruction)
        is EmergencyInstruction -> unsupportedInstruction(instruction)
        is Clearance -> renderClearancePhraseology(instruction)
        is SetPressure,
        is RunwayInUseAdvisory,
        is TransitionLevelIssuance,
        is RemainOutsideControlledAirspace,
        is CancelClearance,
        is Disregard,
        is AvoidArea,
        -> unsupportedInstruction(instruction)
    }
}

private fun renderGroundInstructionPhraseology(
    aircraftId: AircraftId,
    instruction: GroundInstruction,
): ControllerPhraseologyRenderResult {
    val phraseology = when (instruction) {
        is StopImmediately -> stopImmediatelyPhraseology(aircraftId)
        is TaxiToStand -> taxiToStandPhraseology(aircraftId, instruction.destination, instruction.via)
        is StartupApproved,
        is PushbackApproved,
        is PushbackFace,
        is TaxiToHoldingPoint,
        is TaxiViaRunway,
        is AirTaxiTo,
        is HoldPosition,
        is HoldShortOf,
        is CrossRunway,
        is BacktrackRunway,
        is VacateRunway,
        is TaxiIntoHoldingBay,
        is TaxiWithCaution,
        is ExpediteTaxi,
        is ReduceTaxiSpeed,
        is GiveWayToTraffic,
        -> return unsupportedInstruction(instruction)
    }
    return ControllerPhraseologyRenderResult.Rendered(phraseology)
}

private fun renderRunwayInstructionPhraseology(
    aircraftId: AircraftId,
    instruction: RunwayInstruction,
): ControllerPhraseologyRenderResult {
    val phraseology = when (instruction) {
        is LineUpAndWait -> lineUpAndWaitPhraseology(aircraftId, instruction.runway)
        is ClearedForTakeoff -> takeoffClearancePhraseology(aircraftId, instruction.runway)
        is ClearedTouchAndGo -> touchAndGoClearancePhraseology(aircraftId)
        is StopImmediately -> stopImmediatelyPhraseology(aircraftId)
        is ClearedToLand,
        is ClearedLowApproach,
        is GoAround,
        is HoldPositionCancelTakeoff,
        is BreakOff,
        is TakeoffImmediatelyOrVacateRunway,
        is TakeoffImmediatelyOrHoldShort,
        is AfterLandingVacateVia,
        -> return unsupportedInstruction(instruction)
    }
    return ControllerPhraseologyRenderResult.Rendered(phraseology)
}

private fun renderFrequencyInstructionPhraseology(
    aircraftId: AircraftId,
    instruction: FrequencyInstruction,
): ControllerPhraseologyRenderResult {
    val phraseology = when (instruction) {
        is ContactFrequency -> {
            val frequency = instruction.frequency ?: return unsupportedInstruction(instruction)
            contactFrequencyPhraseology(
                aircraftId = aircraftId,
                unitName = instruction.role.name,
                frequency = frequency,
            )
        }
        is MonitorFrequency -> return unsupportedInstruction(instruction)
    }
    return ControllerPhraseologyRenderResult.Rendered(phraseology)
}

private fun renderApproachInstructionPhraseology(
    instruction: ApproachInstruction,
): ControllerPhraseologyRenderResult =
    when (instruction) {
        is GoAround,
        is BreakOff,
        is InterceptLocaliser,
        is ClearedApproach,
        is ClearedVisualApproach,
        is ContinueApproach,
        is JoinCircuit,
        is MakeShortApproach,
        is MakeLongApproach,
        is ExtendDownwind,
        is TurnBase,
        is CommenceApproachAt,
        is MaintainAltitudeUntilEstablished,
        -> unsupportedInstruction(instruction)
    }

private fun renderClearancePhraseology(instruction: Clearance): ControllerPhraseologyRenderResult =
    when (instruction) {
        is ConditionalClearance,
        is ClearedTo,
        is ClearedToEnterControlZone,
        is SpecialVfrClearance,
        is HoldShortOf,
        is CrossRunway,
        is BacktrackRunway,
        is LineUpAndWait,
        is ClearedForTakeoff,
        is ClearedToLand,
        is ClearedTouchAndGo,
        is ClearedLowApproach,
        is ClearedApproach,
        is ClearedVisualApproach,
        is StartupApproved,
        is PushbackApproved,
        -> unsupportedInstruction(instruction)
    }

private fun unsupportedInstruction(instruction: AtcInstruction): ControllerPhraseologyRenderResult.UnsupportedInstruction =
    ControllerPhraseologyRenderResult.UnsupportedInstruction(instruction)

fun renderPilotReadbackPhraseology(
    aircraftId: AircraftId,
    readback: Readback,
): PilotReadbackPhraseologyRenderResult {
    val atoms = readback.elements.mapNotNull { element ->
        (element as? SimpleElement)?.value
    }
    if (atoms.size != readback.elements.size) {
        return PilotReadbackPhraseologyRenderResult.UnsupportedReadback(readback)
    }
    val phraseology = when (val atom = atoms.singleOrNull()) {
        is LineUpReadback -> lineUpReadbackPhraseology(aircraftId = aircraftId)
        is FrequencyReadback -> frequencyReadbackPhraseology(aircraftId = aircraftId, frequency = atom.frequency)
        is TaxiRouteReadback -> taxiRouteReadbackPhraseology(
            aircraftId = aircraftId,
            destination = atom.destination,
            via = atom.via,
        )
        is BacktrackReadback,
        is BreakOffReadback,
        is ClearedApproachReadback,
        is ClearedForTakeoffReadback,
        is ClearedLowApproachReadback,
        is ClearedToLandReadback,
        is ClearedTouchAndGoReadback,
        is CrossRunwayReadback,
        DisregardAcknowledgementReadback,
        is ExtendDownwindReadback,
        is FreeTextReadback,
        is GoAroundReadback,
        is HeadingReadback,
        is HoldingAcknowledgementReadback,
        is HoldReadback,
        is HoldShortReadback,
        is JoinAirwayReadback,
        is LeaveHoldProceedDirectReadback,
        is LevelReadback,
        is OrbitReadback,
        is PressureSettingReadback,
        is RejoinSidAtReadback,
        ResumeOwnNavigationReadback,
        RouteAsFiledReadback,
        is RouteReadback,
        is RunwayInUseReadback,
        is RunwayReadback,
        is SequenceAcknowledgementReadback,
        is SpecialVfrReadback,
        is SpeedReadback,
        is SquawkReadback,
        StopImmediatelyReadback,
        is TakeoffImmediatelyOrHoldShortReadback,
        is TakeoffImmediatelyOrVacateReadback,
        is TaxiViaRunwayReadback,
        is TransitionLevelReadback,
        is VacateReadback,
        is VisualApproachReadback,
        null,
        -> return PilotReadbackPhraseologyRenderResult.UnsupportedReadback(readback)
    }
    return PilotReadbackPhraseologyRenderResult.Rendered(phraseology)
}

fun renderPilotReportPhraseology(report: Report): PilotReportPhraseologyRenderResult {
    val phraseology = when (report.events.singleOrNull()) {
        ReportEvent.Final -> finalReportPhraseology()
        ReportEvent.LongFinal -> longFinalReportPhraseology()
        ReportEvent.RunwayVacated -> runwayVacatedReportPhraseology()
        null,
        is ReportEvent.Downwind,
        ReportEvent.Base,
        ReportEvent.Airborne,
        ReportEvent.Established,
        ReportEvent.EstablishedLocaliser,
        ReportEvent.EstablishedGlidepath,
        ReportEvent.Ready,
        ReportEvent.GoingAround,
        ReportEvent.VisualWithField,
        ReportEvent.EstablishedInHold,
        ReportEvent.TcasRa,
        ReportEvent.MinimumFuel,
        is ReportEvent.PassingLevel,
        is ReportEvent.LeavingLevel,
        is ReportEvent.DistanceDme,
        is ReportEvent.OverFix,
        -> return PilotReportPhraseologyRenderResult.UnsupportedReport(report)
    }
    return PilotReportPhraseologyRenderResult.Rendered(phraseology)
}

private fun lineUpAndWaitPhraseology(
    aircraftId: AircraftId,
    runway: RunwayId,
): RenderedControllerPhraseology {
    val tokens = listOf(
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.Runway,
        PhraseologyToken.RunwayDesignator(runway),
        PhraseologyToken.Line,
        PhraseologyToken.Up,
        PhraseologyToken.And,
        PhraseologyToken.Wait,
    )
    return RenderedControllerPhraseology(
        template = RenderedPhraseologyTemplate.LineUpAndWaitInstruction,
        obligationKinds = renderedClearanceObligations,
        tokens = tokens,
        text = RenderedPhraseText("${aircraftId.value} RUNWAY ${runway.value} LINE UP AND WAIT"),
    )
}

private fun contactFrequencyPhraseology(
    aircraftId: AircraftId,
    unitName: String,
    frequency: Frequency,
): RenderedControllerPhraseology {
    val tokens = listOf(
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.Contact,
        PhraseologyToken.UnitName(unitName),
        PhraseologyToken.FrequencyValue(frequency),
    )
    return RenderedControllerPhraseology(
        template = RenderedPhraseologyTemplate.ContactFrequencyInstruction,
        obligationKinds = setOf(
            PhraseologyObligationKind.OrderedPhrase,
            PhraseologyObligationKind.SemanticSlot,
            PhraseologyObligationKind.Readback,
        ),
        tokens = tokens,
        text = RenderedPhraseText("${aircraftId.value} CONTACT $unitName ${frequency.mhz}"),
    )
}

private fun takeoffClearancePhraseology(
    aircraftId: AircraftId,
    runway: RunwayId,
): RenderedControllerPhraseology {
    val tokens = listOf(
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.Runway,
        PhraseologyToken.RunwayDesignator(runway),
        PhraseologyToken.Cleared,
        PhraseologyToken.For,
        PhraseologyToken.TakeOff,
    )
    return RenderedControllerPhraseology(
        template = RenderedPhraseologyTemplate.TakeoffClearance,
        obligationKinds = renderedClearanceObligations,
        tokens = tokens,
        text = RenderedPhraseText("${aircraftId.value} RUNWAY ${runway.value} CLEARED FOR TAKE-OFF"),
    )
}

private fun touchAndGoClearancePhraseology(
    aircraftId: AircraftId,
): RenderedControllerPhraseology {
    val tokens = listOf(
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.Cleared,
        PhraseologyToken.Touch,
        PhraseologyToken.And,
        PhraseologyToken.Go,
    )
    return RenderedControllerPhraseology(
        template = RenderedPhraseologyTemplate.TouchAndGoClearance,
        obligationKinds = renderedClearanceObligations,
        tokens = tokens,
        text = RenderedPhraseText("${aircraftId.value} CLEARED TOUCH AND GO"),
    )
}

private fun stopImmediatelyPhraseology(
    aircraftId: AircraftId,
): RenderedControllerPhraseology {
    val tokens = listOf(
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.Stop,
        PhraseologyToken.Immediately,
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.Stop,
        PhraseologyToken.Immediately,
    )
    return RenderedControllerPhraseology(
        template = RenderedPhraseologyTemplate.StopImmediatelyInstruction,
        obligationKinds = setOf(
            PhraseologyObligationKind.OrderedPhrase,
            PhraseologyObligationKind.SemanticSlot,
        ),
        tokens = tokens,
        text = RenderedPhraseText(
            "${aircraftId.value} STOP IMMEDIATELY ${aircraftId.value} STOP IMMEDIATELY",
        ),
    )
}

private fun taxiToStandPhraseology(
    aircraftId: AircraftId,
    destination: PointId,
    via: List<PointId>,
): RenderedControllerPhraseology {
    val routeTokens = routeTokens(destination = destination, via = via)
    val tokens = listOf(
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.Taxi,
        PhraseologyToken.To,
    ) + routeTokens
    return RenderedControllerPhraseology(
        template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
        obligationKinds = setOf(
            PhraseologyObligationKind.OrderedPhrase,
            PhraseologyObligationKind.SemanticSlot,
            PhraseologyObligationKind.Readback,
        ),
        tokens = tokens,
        text = RenderedPhraseText(
            "${aircraftId.value} TAXI TO ${routeText(destination = destination, via = via)}",
        ),
    )
}

private fun lineUpReadbackPhraseology(
    aircraftId: AircraftId,
): RenderedPilotReadbackPhraseology {
    val tokens = listOf(
        PhraseologyToken.Lining,
        PhraseologyToken.Up,
        PhraseologyToken.AircraftCallsign(aircraftId),
    )
    return RenderedPilotReadbackPhraseology(
        template = RenderedPhraseologyTemplate.LineUpReadback,
        obligationKinds = setOf(
            PhraseologyObligationKind.OrderedPhrase,
            PhraseologyObligationKind.Readback,
            PhraseologyObligationKind.SemanticSlot,
        ),
        tokens = tokens,
        text = RenderedPhraseText("LINING UP ${aircraftId.value}"),
    )
}

private fun frequencyReadbackPhraseology(
    aircraftId: AircraftId,
    frequency: Frequency,
): RenderedPilotReadbackPhraseology {
    val tokens = listOf(
        PhraseologyToken.FrequencyValue(frequency),
        PhraseologyToken.AircraftCallsign(aircraftId),
    )
    return RenderedPilotReadbackPhraseology(
        template = RenderedPhraseologyTemplate.FrequencyReadback,
        obligationKinds = setOf(
            PhraseologyObligationKind.OrderedPhrase,
            PhraseologyObligationKind.Readback,
            PhraseologyObligationKind.SemanticSlot,
        ),
        tokens = tokens,
        text = RenderedPhraseText("${frequency.mhz} ${aircraftId.value}"),
    )
}

private fun taxiRouteReadbackPhraseology(
    aircraftId: AircraftId,
    destination: PointId,
    via: List<PointId>,
): RenderedPilotReadbackPhraseology {
    val tokens = routeTokens(destination = destination, via = via) +
        PhraseologyToken.AircraftCallsign(aircraftId)
    return RenderedPilotReadbackPhraseology(
        template = RenderedPhraseologyTemplate.TaxiRouteReadback,
        obligationKinds = readbackObligations,
        tokens = tokens,
        text = RenderedPhraseText("${routeText(destination = destination, via = via)} ${aircraftId.value}"),
    )
}

private fun finalReportPhraseology(): RenderedPilotReportPhraseology =
    RenderedPilotReportPhraseology(
        template = RenderedPhraseologyTemplate.FinalReport,
        obligationKinds = reportObligations,
        tokens = listOf(PhraseologyToken.Final),
        text = RenderedPhraseText("FINAL"),
    )

private fun longFinalReportPhraseology(): RenderedPilotReportPhraseology =
    RenderedPilotReportPhraseology(
        template = RenderedPhraseologyTemplate.LongFinalReport,
        obligationKinds = reportObligations,
        tokens = listOf(PhraseologyToken.Long, PhraseologyToken.Final),
        text = RenderedPhraseText("LONG FINAL"),
    )

private fun runwayVacatedReportPhraseology(): RenderedPilotReportPhraseology =
    RenderedPilotReportPhraseology(
        template = RenderedPhraseologyTemplate.RunwayVacatedReport,
        obligationKinds = reportObligations,
        tokens = listOf(PhraseologyToken.Runway, PhraseologyToken.Vacated),
        text = RenderedPhraseText("RUNWAY VACATED"),
    )

private fun routeTokens(
    destination: PointId,
    via: List<PointId>,
): List<PhraseologyToken> {
    val destinationToken = PhraseologyToken.PointName(destination)
    return if (via.isEmpty()) {
        listOf(destinationToken)
    } else {
        listOf(destinationToken, PhraseologyToken.Via) + via.map(PhraseologyToken::PointName)
    }
}

private fun routeText(
    destination: PointId,
    via: List<PointId>,
): String =
    if (via.isEmpty()) {
        destination.value
    } else {
        "${destination.value} VIA ${via.joinToString(separator = " ") { point -> point.value }}"
    }

private val renderedClearanceObligations: Set<PhraseologyObligationKind> =
    setOf(
        PhraseologyObligationKind.OrderedPhrase,
        PhraseologyObligationKind.SemanticSlot,
        PhraseologyObligationKind.ForbiddenMeaning,
    )

private val reportObligations: Set<PhraseologyObligationKind> =
    setOf(
        PhraseologyObligationKind.OrderedPhrase,
        PhraseologyObligationKind.SemanticSlot,
    )

private val readbackObligations: Set<PhraseologyObligationKind> =
    setOf(
        PhraseologyObligationKind.OrderedPhrase,
        PhraseologyObligationKind.Readback,
        PhraseologyObligationKind.SemanticSlot,
    )
