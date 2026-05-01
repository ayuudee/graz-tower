package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.ControllerEvent
import xyz.easiersaid.twr.controller.observe.deriveEventsFromMessages
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import kotlin.test.Test

/**
 * Spec test for the pure event-derivation function (Phase B6).
 *
 * Pinned because G0 exercises seven downstream mechanisms behind a single
 * outcome assertion ("aircraft got back to the stand"). When G0 fails, this
 * test localises whether the radio→event translation is the broken link.
 */
class EventDerivationSpec {

    private val aircraft = AircraftId("OE-ABC")

    @Test
    fun `Downwind report with FULL_STOP intent yields PositionReported and CircuitIntentReported`() {
        val tx = Report(events = listOf(ReportEvent.Downwind(circuitIntent = CircuitIntent.FULL_STOP)))
        val msgs = listOf(ReceivedMessage.Clear(aircraft = aircraft, transmission = tx))

        val events = deriveEventsFromMessages(msgs)

        check(events.any { it is ControllerEvent.PositionReported && it.aircraft == aircraft }) {
            "Expected PositionReported in: $events"
        }
        check(events.any { it is ControllerEvent.CircuitIntentReported &&
            it.aircraft == aircraft && it.intent == CircuitIntent.FULL_STOP }) {
            "Expected CircuitIntentReported(FULL_STOP) in: $events"
        }
    }

    @Test
    fun `Downwind report with TOUCH_AND_GO intent yields CircuitIntentReported with TOUCH_AND_GO`() {
        val tx = Report(events = listOf(ReportEvent.Downwind(circuitIntent = CircuitIntent.TOUCH_AND_GO)))
        val events = deriveEventsFromMessages(listOf(ReceivedMessage.Clear(aircraft, tx)))

        val intent = events.filterIsInstance<ControllerEvent.CircuitIntentReported>().singleOrNull()
        check(intent != null && intent.intent == CircuitIntent.TOUCH_AND_GO) {
            "Expected exactly one CircuitIntentReported(TOUCH_AND_GO); got events=$events"
        }
    }

    @Test
    fun `Downwind report without intent emits PositionReported only`() {
        val tx = Report(events = listOf(ReportEvent.Downwind(circuitIntent = null)))
        val events = deriveEventsFromMessages(listOf(ReceivedMessage.Clear(aircraft, tx)))

        check(events.size == 1 && events.single() is ControllerEvent.PositionReported) {
            "Expected only PositionReported when intent is null; got: $events"
        }
    }

    @Test
    fun `Report(RunwayVacated) emits PositionReported and AircraftArrivalCommitted`() {
        // Post-landing radio observation: the controller hears "runway
        // vacated" and annotates the strip from departure-flow to
        // arrival-flow. Both events fire; the belief fold consumes
        // AircraftArrivalCommitted to flip aircraftIntent → Arriving.
        // (D-PF.5 closure — replaces the dynamic-strip mind-reading.)
        val tx = Report(events = listOf(ReportEvent.RunwayVacated))
        val events = deriveEventsFromMessages(listOf(ReceivedMessage.Clear(aircraft, tx)))

        check(events.any { it is ControllerEvent.PositionReported && it.aircraft == aircraft }) {
            "Expected PositionReported(aircraft=$aircraft, RunwayVacated) in: $events"
        }
        check(events.any { it is ControllerEvent.AircraftArrivalCommitted && it.aircraft == aircraft }) {
            "Expected AircraftArrivalCommitted($aircraft) in: $events"
        }
    }

    @Test
    fun `Downwind report does NOT emit AircraftArrivalCommitted`() {
        // Inverse coverage. AircraftArrivalCommitted fires ONLY on
        // RunwayVacated (post-landing observation), never on Downwind. A
        // regression that wired it to the wrong dispatch arm would not
        // fail the positive test above; this catches it directly.
        val tx = Report(events = listOf(ReportEvent.Downwind(circuitIntent = CircuitIntent.FULL_STOP)))
        val events = deriveEventsFromMessages(listOf(ReceivedMessage.Clear(aircraft, tx)))

        check(events.none { it is ControllerEvent.AircraftArrivalCommitted }) {
            "Expected NO AircraftArrivalCommitted from Downwind; got: $events"
        }
    }
}
