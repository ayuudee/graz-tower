package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.FrequencyReadback
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.LineUpReadback
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimpleElement
import xyz.easiersaid.twr.protocol.StopImmediately
import xyz.easiersaid.twr.protocol.TaxiRouteReadback
import xyz.easiersaid.twr.protocol.TaxiToStand

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
}

@JvmInline
value class RenderedPhraseText(val value: String) {
    init {
        require(value.isNotBlank()) { "rendered phraseology text must not be blank" }
    }
}

sealed interface PhraseologyToken {
    data class AircraftCallsign(val aircraftId: AircraftId) : PhraseologyToken
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

fun renderControllerPhraseology(output: ControllerOutput.Instruct): ControllerPhraseologyRenderResult {
    val phraseology = when (val instruction = output.instruction) {
        is LineUpAndWait -> lineUpAndWaitPhraseology(output.target, instruction.runway)
        is ContactFrequency -> {
            val frequency = instruction.frequency
                ?: return ControllerPhraseologyRenderResult.UnsupportedInstruction(instruction)
            contactFrequencyPhraseology(
                aircraftId = output.target,
                unitName = instruction.role.name,
                frequency = frequency,
            )
        }
        is ClearedForTakeoff -> takeoffClearancePhraseology(output.target, instruction.runway)
        is ClearedTouchAndGo -> touchAndGoClearancePhraseology(output.target)
        is StopImmediately -> stopImmediatelyPhraseology(output.target)
        is TaxiToStand -> taxiToStandPhraseology(output.target, instruction.destination, instruction.via)
        else -> return ControllerPhraseologyRenderResult.UnsupportedInstruction(instruction)
    }
    return ControllerPhraseologyRenderResult.Rendered(phraseology)
}

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
        else -> return PilotReadbackPhraseologyRenderResult.UnsupportedReadback(readback)
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
