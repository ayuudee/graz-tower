package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtomicReadback
import xyz.easiersaid.twr.protocol.FrequencyReadback
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.VacateReadback

private val FirstRightPoint = PointId("FIRST_RIGHT")

private val firstRightReadbackObligations: Set<PhraseologyObligationKind> =
    setOf(
        PhraseologyObligationKind.OrderedPhrase,
        PhraseologyObligationKind.Readback,
        PhraseologyObligationKind.SemanticSlot,
    )

internal fun afterLandingVacateViaPhraseology(
    aircraftId: AircraftId,
    instruction: AfterLandingVacateVia,
): RenderedControllerPhraseology? {
    if (instruction.exit != FirstRightPoint || instruction.whenAble) return null
    val tokens = listOf(
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.Take,
        PhraseologyToken.First,
        PhraseologyToken.Right,
        PhraseologyToken.When,
        PhraseologyToken.Vacated,
    )
    return RenderedControllerPhraseology(
        template = RenderedPhraseologyTemplate.AfterLandingVacateViaInstruction,
        obligationKinds = firstRightReadbackObligations,
        tokens = tokens,
        text = RenderedPhraseText("${aircraftId.value} TAKE FIRST RIGHT WHEN VACATED"),
    )
}

internal fun firstRightFrequencyReadbackPhraseology(
    aircraftId: AircraftId,
    atoms: List<AtomicReadback>,
): RenderedPilotReadbackPhraseology? {
    val vacate = atoms.getOrNull(0) as? VacateReadback ?: return null
    val frequency = atoms.getOrNull(1) as? FrequencyReadback ?: return null
    if (atoms.size != 2 || vacate.via != FirstRightPoint || vacate.direction != null) return null
    val tokens = listOf(
        PhraseologyToken.First,
        PhraseologyToken.Right,
        PhraseologyToken.FrequencyValue(frequency.frequency),
        PhraseologyToken.AircraftCallsign(aircraftId),
    )
    return RenderedPilotReadbackPhraseology(
        template = RenderedPhraseologyTemplate.FirstRightFrequencyReadback,
        obligationKinds = firstRightReadbackObligations,
        tokens = tokens,
        text = RenderedPhraseText("FIRST RIGHT ${frequency.frequency.mhz} ${aircraftId.value}"),
    )
}
