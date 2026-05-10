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
import xyz.easiersaid.twr.core.world.RunwayObstruction
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
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SayAgain
import xyz.easiersaid.twr.protocol.StandbyAck
import xyz.easiersaid.twr.protocol.TrafficInSight
import xyz.easiersaid.twr.protocol.TrafficRef
import xyz.easiersaid.twr.protocol.Unable
import xyz.easiersaid.twr.protocol.Wilco

/**
 * Semantic events derived from received messages, belief deltas, and
 * world-state changes.
 *
 * **Three source classes** (post fn-12):
 *  1. **Radio-derived, aircraft-scoped** — derived from a
 *     [PilotTransmission] after physics + interpretation resolves it.
 *     Examples: [ReadyForDepartureReceived], [InitialContactReceived],
 *     [PositionReported], [ReadbackReceived], [GoAroundDetected],
 *     [UnableReceived], [TrafficInSightReceived], [PilotRequestReceived],
 *     [CircuitIntentReported], [AircraftArrivalCommitted]. All emitted by
 *     `deriveFromTransmission` in this module; all carry `aircraft:
 *     AircraftId`.
 *  2. **Controller-state-derived, aircraft-scoped** — emitted directly
 *     by controller-side state transitions (not from radio). The current
 *     instance is [ResponsibilityTaken], emitted when responsibility for
 *     an aircraft is acquired (e.g. cross-aerodrome pickup); it carries
 *     `aircraft: AircraftId` and `aircraftIdOf` returns it normally.
 *  3. **World-state-derived, no-aircraft** — emitted directly by the
 *     sim's per-cycle world-diff producer when a world-model field
 *     changes value. The [RunwayObstructionDetected] and
 *     [RunwayObstructionCleared] leaves are the first instances. They
 *     carry no `AircraftId` payload (per-controller-scoped — see those
 *     KDocs) and are therefore **exempt from `aircraftIdOf`** aircraft-
 *     extraction. Helpers that walk all events must either filter to
 *     aircraft-scoped events or handle the null-aircraft case explicitly.
 *
 * Filtering by "has an `aircraft` field" is NOT a reliable proxy for
 * "radio-derived" — class 2 also carries `aircraft` without being radio-
 * derived. Use `deriveFromTransmission` as the canonical radio-derivation
 * site; everything else is non-radio.
 *
 * All three source classes flow through the same [BeliefState] fold
 * pipeline. The firewall contract is "typed events from world or radio
 * or controller-side state, never raw audio, never blocking calls" — see
 * `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md`
 * § Unified Event Taxonomy.
 */
sealed interface ControllerEvent {
    data class ReadyForDepartureReceived(val aircraft: AircraftId) : ControllerEvent
    data class InitialContactReceived(val aircraft: AircraftId, val intentions: RequestType?) : ControllerEvent
    data class PositionReported(val aircraft: AircraftId, val event: ReportEvent) : ControllerEvent
    data class ReadbackReceived(val aircraft: AircraftId, val readback: Readback) : ControllerEvent
    data class StartupRequested(val aircraft: AircraftId) : ControllerEvent
    data class TaxiRequested(val aircraft: AircraftId) : ControllerEvent
    data class GoAroundDetected(val aircraft: AircraftId) : ControllerEvent
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

    /**
     * fn-12 (R2): the world has declared the active runway obstructed —
     * sim-side detection (modality-agnostic; tower-visual / surface sensor /
     * ground inspection are all valid sources). Emitted by the per-cycle
     * world-diff producer in `sim/.../ControllerWiring.kt` on the
     * `runway.obstruction` field's `None → Some(new)` transition.
     *
     * No `AerodromeId` payload — the producer is per-controller-scoped:
     * events in `ControllerView.worldEvents` reference only `RunwayId`s
     * within `view.aerodromeId`'s runway set. Cross-aerodrome routing is
     * filed as `D-PASS-g3a-obstruction-aerodrome-payload`.
     *
     * Folded into [BeliefState.runwayObstructions] via
     * `withRunwayObstructionEvents`. Read by the [RunwayObstructed] guard
     * and the `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule.
     */
    data class RunwayObstructionDetected(
        val runway: RunwayId,
        val obstruction: RunwayObstruction,
    ) : ControllerEvent

    /**
     * fn-12 (R2): the world has cleared the runway obstruction — sim-side
     * expiry. Emitted by the world-diff producer on the
     * `runway.obstruction` field's `Some → None` transition (from the
     * per-cycle expiry pass nulling expired obstructions where
     * `clearsAt <= now`).
     *
     * Folded into [BeliefState.runwayObstructions] (drops the entry).
     */
    data class RunwayObstructionCleared(val runway: RunwayId) : ControllerEvent
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
