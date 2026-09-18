package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.PointId

fun renderVehicleDriverPhraseology(
    transmission: VehicleDriverTransmission,
): VehicleDriverPhraseologyRenderResult {
    val phraseology = when (transmission) {
        is VehicleDriverTransmission.InitialCall -> vehicleInitialCallPhraseology(transmission)
        is VehicleDriverTransmission.RequestTow -> vehicleTowRequestPhraseology(transmission)
        is VehicleDriverTransmission.RequestFurtherPermission,
        is VehicleDriverTransmission.AcknowledgeRunwayCrossing,
        is VehicleDriverTransmission.RunwayVacated,
        is VehicleDriverTransmission.Acknowledge,
        -> return VehicleDriverPhraseologyRenderResult.UnsupportedTransmission(transmission)
    }
    return VehicleDriverPhraseologyRenderResult.Rendered(phraseology)
}

private fun vehicleInitialCallPhraseology(
    transmission: VehicleDriverTransmission.InitialCall,
): RenderedVehicleDriverPhraseology {
    val tokens = listOf(
        PhraseologyToken.VehicleCallsign(transmission.callsign),
        PhraseologyToken.PointName(transmission.position),
        PhraseologyToken.To,
        PhraseologyToken.PointName(transmission.destination),
        PhraseologyToken.Via,
    ) + transmission.route.map(PhraseologyToken::PointName)
    return RenderedVehicleDriverPhraseology(
        template = RenderedPhraseologyTemplate.VehicleInitialCall,
        obligationKinds = vehiclePhraseologyObligations,
        tokens = tokens,
        text = RenderedPhraseText(
            "${transmission.callsign.value} ${transmission.position.value} TO " +
                "${transmission.destination.value} VIA ${pointText(transmission.route)}",
        ),
    )
}

private fun vehicleTowRequestPhraseology(
    transmission: VehicleDriverTransmission.RequestTow,
): RenderedVehicleDriverPhraseology {
    val tokens = listOf(
        PhraseologyToken.StationName(transmission.receivingStation),
        PhraseologyToken.VehicleCallsign(transmission.callsign),
        PhraseologyToken.Request,
        PhraseologyToken.Tow,
        PhraseologyToken.AircraftCallsign(transmission.tow.aircraft),
        PhraseologyToken.AircraftTypeName(transmission.tow.aircraftType),
    ) + listOfNotNull(transmission.tow.operator?.let(PhraseologyToken::OperatorName))
    return RenderedVehicleDriverPhraseology(
        template = RenderedPhraseologyTemplate.VehicleTowRequest,
        obligationKinds = vehiclePhraseologyObligations,
        tokens = tokens,
        text = RenderedPhraseText(
            listOfNotNull(
                transmission.receivingStation.value,
                transmission.callsign.value,
                "REQUEST TOW",
                transmission.tow.aircraft.value,
                transmission.tow.aircraftType.icaoDesignator.raw,
                transmission.tow.operator?.value,
            ).joinToString(separator = " "),
        ),
    )
}

private fun pointText(points: List<PointId>): String =
    points.joinToString(separator = " ") { point -> point.value }

private val vehiclePhraseologyObligations: Set<PhraseologyObligationKind> =
    setOf(
        PhraseologyObligationKind.OrderedPhrase,
        PhraseologyObligationKind.SemanticSlot,
    )
