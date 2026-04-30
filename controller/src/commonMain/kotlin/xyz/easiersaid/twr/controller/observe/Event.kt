package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.controller.ReceivedMessage
import xyz.easiersaid.twr.protocol.Acknowledge
import xyz.easiersaid.twr.protocol.AcknowledgeType
import xyz.easiersaid.twr.protocol.AcknowledgeWithInfo
import xyz.easiersaid.twr.protocol.Affirm
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.CancelEmergency
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.Confirm
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Emergency
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.Negative
import xyz.easiersaid.twr.protocol.NegativeContact
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.Request
import xyz.easiersaid.twr.protocol.RequestOrbit
import xyz.easiersaid.twr.protocol.RequestRightBase
import xyz.easiersaid.twr.protocol.RequestShortApproach
import xyz.easiersaid.twr.protocol.RequestStartup
import xyz.easiersaid.twr.protocol.RequestTaxi
import xyz.easiersaid.twr.protocol.RequestType
import xyz.easiersaid.twr.protocol.RequestVisualApproach
import xyz.easiersaid.twr.protocol.Roger
import xyz.easiersaid.twr.protocol.SayAgain
import xyz.easiersaid.twr.protocol.StandbyAck
import xyz.easiersaid.twr.protocol.TrafficInSight
import xyz.easiersaid.twr.protocol.TrafficRef
import xyz.easiersaid.twr.protocol.Unable
import xyz.easiersaid.twr.protocol.Wilco

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

    /**
     * Pilot reported circuit-end intent on a Downwind position report
     * (CAP 413 §4.45-4.49 / ICAO Doc 4444 §12.3.4 phraseology:
     * "downwind, full stop" / "downwind, touch and go").
     *
     * Emitted alongside [PositionReported] when [ReportEvent.Downwind]
     * carries a non-null intent. The belief-update fold writes
     * [BeliefState.circuitIntent] from this event; the procedure rules
     * read it via [xyz.easiersaid.twr.controller.bdi.CircuitIntentIs].
     */
    data class CircuitIntentReported(val aircraft: AircraftId, val intent: CircuitIntent) : ControllerEvent

    /**
     * Pilot has reported runway vacated after a full-stop landing — the
     * arrival flow has committed. Real-world parallel: the controller hears
     * "OE-ABC, runway vacated" and annotates the strip from departure-flow
     * to arrival-flow.
     *
     * Emitted alongside [PositionReported] when the pilot transmits
     * `Report(RunwayVacated)`. The belief-update fold writes
     * [BeliefState.aircraftIntent] = `Arriving` from this event; the
     * controller's `determineServiceKind` then forms a `GROUND_ARRIVAL`
     * commitment instead of `GROUND_DEPARTURE` for the post-landing taxi.
     *
     * Touch-and-go landings do NOT produce `Report(RunwayVacated)` (the
     * aircraft never vacates; it lifts off again). So this event fires
     * only on full-stop landings — the correct trigger for the
     * Departing → Arriving transition.
     */
    data class AircraftArrivalCommitted(val aircraft: AircraftId) : ControllerEvent
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
        is Report -> tx.events.flatMap { event -> deriveFromReport(aircraft, event) }
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

private fun deriveFromReport(aircraft: AircraftId, event: ReportEvent): List<ControllerEvent> =
    when (event) {
        is ReportEvent.Ready -> listOf(ControllerEvent.ReadyForDepartureReceived(aircraft))
        is ReportEvent.GoingAround -> listOf(ControllerEvent.GoAroundDetected(aircraft))
        is ReportEvent.Downwind -> {
            // Downwind always emits PositionReported. If the pilot's report
            // qualified the call with circuit-end intent (CAP 413 §4.45-4.49,
            // "downwind, full stop" / "downwind, touch and go"), additionally
            // emit CircuitIntentReported so the belief-update fold can record
            // the intent on BeliefState.circuitIntent. This is the only path
            // the controller has to learn circuit intent — there is no
            // back-channel.
            val position = ControllerEvent.PositionReported(aircraft, event)
            val intent = event.circuitIntent
            if (intent != null) listOf(position, ControllerEvent.CircuitIntentReported(aircraft, intent))
            else listOf(position)
        }
        is ReportEvent.Base, is ReportEvent.Final,
        is ReportEvent.LongFinal, is ReportEvent.Airborne,
        is ReportEvent.Established, is ReportEvent.EstablishedLocaliser,
        is ReportEvent.EstablishedGlidepath ->
            listOf(ControllerEvent.PositionReported(aircraft, event))
        // Runway vacated after a full-stop landing. Emits PositionReported
        // (it IS a position report — "the aircraft is now off the runway")
        // AND AircraftArrivalCommitted (which the belief fold uses to flip
        // aircraftIntent from Departing → Arriving). T&G landings don't
        // produce this report (the aircraft lifts off, never vacates), so
        // this trigger is full-stop-only by construction.
        is ReportEvent.RunwayVacated -> listOf(
            ControllerEvent.PositionReported(aircraft, event),
            ControllerEvent.AircraftArrivalCommitted(aircraft),
        )
        // Explicitly listed so the compiler forces a decision when new variants are added.
        is ReportEvent.VisualWithField, is ReportEvent.EstablishedInHold,
        is ReportEvent.TcasRa, is ReportEvent.MinimumFuel,
        is ReportEvent.PassingLevel, is ReportEvent.LeavingLevel,
        is ReportEvent.DistanceDme, is ReportEvent.OverFix -> emptyList()
    }
