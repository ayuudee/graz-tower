package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.RunwayId

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
    data object Touch : PhraseologyToken
    data object And : PhraseologyToken
    data object Go : PhraseologyToken
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

fun renderControllerPhraseology(output: ControllerOutput.Instruct): ControllerPhraseologyRenderResult {
    val phraseology = when (val instruction = output.instruction) {
        is ClearedForTakeoff -> takeoffClearancePhraseology(output.target, instruction.runway)
        is ClearedTouchAndGo -> touchAndGoClearancePhraseology(output.target)
        else -> return ControllerPhraseologyRenderResult.UnsupportedInstruction(instruction)
    }
    return ControllerPhraseologyRenderResult.Rendered(phraseology)
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

private val renderedClearanceObligations: Set<PhraseologyObligationKind> =
    setOf(
        PhraseologyObligationKind.OrderedPhrase,
        PhraseologyObligationKind.SemanticSlot,
        PhraseologyObligationKind.ForbiddenMeaning,
    )
