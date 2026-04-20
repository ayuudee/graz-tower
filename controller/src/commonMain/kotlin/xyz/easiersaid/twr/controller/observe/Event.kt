package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.controller.ReceivedMessage
import xyz.easiersaid.twr.protocol.*

/** Semantic events derived from received messages and belief deltas. */
sealed interface ControllerEvent {
    data class ReadyForDepartureReceived(val aircraft: AircraftId) : ControllerEvent
    data class InitialContactReceived(val aircraft: AircraftId, val intentions: RequestType?) : ControllerEvent
    data class PositionReported(val aircraft: AircraftId, val event: ReportEvent) : ControllerEvent
    data class ReadbackReceived(val aircraft: AircraftId, val readback: Readback) : ControllerEvent
    data class StartupRequested(val aircraft: AircraftId) : ControllerEvent
    data class TaxiRequested(val aircraft: AircraftId) : ControllerEvent
    data class GoAroundDetected(val aircraft: AircraftId) : ControllerEvent
    data class HandoffOffered(val aircraft: AircraftId, val from: ControllerId) : ControllerEvent
    data class ResponsibilityTaken(val aircraft: AircraftId) : ControllerEvent

    /**
     * Pilot refused an instruction ("unable [reason]"). Routes to re-sequencing:
     * the refusing aircraft's commitment enters a NeedsReplan state.
     * Not a readback defect — a goal-state change.
     */
    data class UnableReceived(val aircraft: AircraftId, val reason: String?) : ControllerEvent

    /** Pilot reports traffic in sight. Advances FollowTarget acquisition state. */
    data class TrafficInSightReceived(val aircraft: AircraftId, val traffic: TrafficRef?) : ControllerEvent

    /** Pilot request requiring controller decision (visual approach, short approach, orbit, etc.) */
    data class PilotRequestReceived(val aircraft: AircraftId, val request: RequestType) : ControllerEvent
}

/** Derive semantic events from channel-resolved received messages. */
fun deriveEventsFromMessages(messages: List<ReceivedMessage>): List<ControllerEvent> =
    messages.flatMap { msg -> deriveFromTransmission(msg.aircraft, msg.transmission) }

private fun deriveFromTransmission(aircraft: AircraftId, tx: PilotTransmission): List<ControllerEvent> =
    when (tx) {
        is InitialContact -> listOf(
            ControllerEvent.InitialContactReceived(aircraft, tx.intention)
        )
        is Request -> deriveFromRequest(aircraft, tx.type)
        is Report -> tx.events.mapNotNull { event -> deriveFromReport(aircraft, event) }
        is Readback -> listOf(ControllerEvent.ReadbackReceived(aircraft, tx))
        is Acknowledge -> deriveFromAcknowledge(aircraft, tx.type)
        is TrafficInSight -> listOf(ControllerEvent.TrafficInSightReceived(aircraft, tx.traffic))
        is NegativeContact,
        is SayAgain, is Confirm, is Emergency, is CancelEmergency -> emptyList()
    }

private fun deriveFromRequest(aircraft: AircraftId, type: RequestType): List<ControllerEvent> =
    when (type) {
        is RequestStartup -> listOf(ControllerEvent.StartupRequested(aircraft))
        is RequestTaxi -> listOf(ControllerEvent.TaxiRequested(aircraft))
        is RequestVisualApproach, is RequestShortApproach, is RequestRightBase, is RequestOrbit ->
            listOf(ControllerEvent.PilotRequestReceived(aircraft, type))
        else -> emptyList()
    }

private fun deriveFromAcknowledge(aircraft: AircraftId, type: AcknowledgeType): List<ControllerEvent> =
    when (type) {
        is Unable -> listOf(ControllerEvent.UnableReceived(aircraft, type.reason))
        is Wilco, is Roger, is Affirm, is Negative, is StandbyAck, is AcknowledgeWithInfo -> emptyList()
    }

private fun deriveFromReport(aircraft: AircraftId, event: ReportEvent): ControllerEvent? =
    when (event) {
        is ReportEvent.Ready -> ControllerEvent.ReadyForDepartureReceived(aircraft)
        is ReportEvent.GoingAround -> ControllerEvent.GoAroundDetected(aircraft)
        is ReportEvent.Downwind, is ReportEvent.Base, is ReportEvent.Final,
        is ReportEvent.LongFinal, is ReportEvent.Airborne,
        is ReportEvent.Established, is ReportEvent.EstablishedLocaliser,
        is ReportEvent.EstablishedGlidepath ->
            ControllerEvent.PositionReported(aircraft, event)
        // Explicitly listed so the compiler forces a decision when new variants are added.
        is ReportEvent.RunwayVacated,
        is ReportEvent.VisualWithField, is ReportEvent.EstablishedInHold,
        is ReportEvent.TcasRa, is ReportEvent.MinimumFuel,
        is ReportEvent.PassingLevel, is ReportEvent.LeavingLevel,
        is ReportEvent.DistanceDme, is ReportEvent.OverFix -> null
    }
