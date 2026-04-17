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
        is Acknowledge, is TrafficInSight, is NegativeContact,
        is SayAgain, is Confirm, is Emergency, is CancelEmergency -> emptyList()
    }

private fun deriveFromRequest(aircraft: AircraftId, type: RequestType): List<ControllerEvent> =
    when (type) {
        is RequestStartup -> listOf(ControllerEvent.StartupRequested(aircraft))
        is RequestTaxi -> listOf(ControllerEvent.TaxiRequested(aircraft))
        else -> emptyList()
    }

private fun deriveFromReport(aircraft: AircraftId, event: ReportEvent): ControllerEvent? =
    when (event) {
        is ReportEvent.Ready -> ControllerEvent.ReadyForDepartureReceived(aircraft)
        is ReportEvent.GoingAround -> ControllerEvent.GoAroundDetected(aircraft)
        is ReportEvent.Downwind, is ReportEvent.Base, is ReportEvent.Final,
        is ReportEvent.LongFinal, is ReportEvent.Airborne ->
            ControllerEvent.PositionReported(aircraft, event)
        else -> null
    }
