package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.AirTaxiTo
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.PointId

internal val HelicopterStandPoint: PointId = PointId("HELICOPTER_STAND")

internal fun airTaxiToPhraseology(
    aircraftId: AircraftId,
    instruction: AirTaxiTo,
): RenderedControllerPhraseology? {
    if (instruction.destination != HelicopterStandPoint || instruction.via.isNotEmpty()) return null
    val tokens = listOf(
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.AirTaxi,
        PhraseologyToken.To,
        PhraseologyToken.PointName(HelicopterStandPoint),
    )
    return RenderedControllerPhraseology(
        template = RenderedPhraseologyTemplate.AirTaxiToInstruction,
        obligationKinds = setOf(
            PhraseologyObligationKind.OrderedPhrase,
            PhraseologyObligationKind.SemanticSlot,
            PhraseologyObligationKind.Readback,
        ),
        tokens = tokens,
        text = RenderedPhraseText("${aircraftId.value} AIR-TAXI TO ${phraseologyPointText(HelicopterStandPoint)}"),
    )
}

internal fun airTaxiRouteReadbackPhraseology(
    aircraftId: AircraftId,
    destination: PointId,
    via: List<PointId>,
): RenderedPilotReadbackPhraseology? {
    if (destination != HelicopterStandPoint || via.isNotEmpty()) return null
    val tokens = listOf(
        PhraseologyToken.AirTaxi,
        PhraseologyToken.To,
        PhraseologyToken.PointName(HelicopterStandPoint),
        PhraseologyToken.AircraftCallsign(aircraftId),
    )
    return RenderedPilotReadbackPhraseology(
        template = RenderedPhraseologyTemplate.AirTaxiRouteReadback,
        obligationKinds = readbackObligations,
        tokens = tokens,
        text = RenderedPhraseText("AIR-TAXI TO ${phraseologyPointText(HelicopterStandPoint)} ${aircraftId.value}"),
    )
}

internal fun phraseologyPointText(point: PointId): String =
    when (point) {
        HelicopterStandPoint -> "HELICOPTER STAND"
        else -> point.value
    }
