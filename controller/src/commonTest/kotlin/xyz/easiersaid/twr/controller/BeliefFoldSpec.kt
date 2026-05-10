package xyz.easiersaid.twr.controller

import arrow.core.None
import arrow.core.Some
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.ControllerEvent
import xyz.easiersaid.twr.controller.observe.RecentRadio
import xyz.easiersaid.twr.controller.observe.deriveCurrentIntent
import xyz.easiersaid.twr.controller.observe.withCircuitIntentEvents
import xyz.easiersaid.twr.controller.observe.withRecentRadio
import xyz.easiersaid.twr.controller.observe.withRunwayObstructionEvents
import xyz.easiersaid.twr.core.world.RunwayObstruction
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.RequestTaxi
import xyz.easiersaid.twr.protocol.RequestVisualApproach
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test

/**
 * Spec test for the belief-update folds.
 *
 * Two folds remain after the Pass 5 D-AUDIT.14 closure:
 *  - [withCircuitIntentEvents] — sole writer of [BeliefState.circuitIntent].
 *  - [withRecentRadio] — sole writer of [BeliefState.recentRadio].
 *
 * The previous `withAircraftIntentEvents` and `seedAircraftIntentFromStrips`
 * folds are gone; intent is derived on demand from primary sources via
 * [deriveCurrentIntent]. The strip-vs-radio precedence quotient is exercised
 * end-to-end in [DetermineServiceKindSpec]; this file pins the smaller
 * primitive — *the derive function itself* — so a regression at the fold
 * level isolates from a regression in `determineServiceKind`'s branching.
 *
 * Both folds are the *only* write site for their respective slices, enforced
 * architecturally by [xyz.easiersaid.twr.controller.FirewallBeliefWriteTest].
 */
class BeliefFoldSpec {

    private val aircraft = AircraftId("OE-ABC")
    private val now = SimTime.ofMillis(1_000)

    @Test
    fun `CircuitIntentReported writes intent into belief`() {
        val updated = BeliefState.EMPTY.withCircuitIntentEvents(listOf(
            ControllerEvent.CircuitIntentReported(aircraft, CircuitIntent.FULL_STOP),
        ))
        check(updated.circuitIntent[aircraft] == CircuitIntent.FULL_STOP) {
            "Expected circuitIntent[$aircraft] = FULL_STOP; got ${updated.circuitIntent}"
        }
    }

    @Test
    fun `GoAroundDetected clears circuit intent for the aircraft`() {
        // Pilot must re-declare on the rejoined circuit per ICAO 4444 §7.10.2.
        val seeded = BeliefState.EMPTY.copy(
            circuitIntent = mapOf(aircraft to CircuitIntent.FULL_STOP),
        )
        val cleared = seeded.withCircuitIntentEvents(listOf(
            ControllerEvent.GoAroundDetected(aircraft),
        ))
        check(aircraft !in cleared.circuitIntent) {
            "Expected circuitIntent to drop $aircraft after GoAroundDetected; got ${cleared.circuitIntent}"
        }
    }

    @Test
    fun `withRecentRadio appends an event for the addressed aircraft`() {
        val updated = BeliefState.EMPTY.withRecentRadio(
            listOf(ControllerEvent.InitialContactReceived(aircraft, RequestTaxi())),
            now,
        )
        val radio = updated.recentRadio[aircraft]
        check(radio != null && radio.entries.size == 1) {
            "Expected one entry for $aircraft after InitialContactReceived; got ${updated.recentRadio}"
        }
    }

    @Test
    fun `withRecentRadio prunes entries older than the configured window`() {
        // First append at t=0; second append past the window. The first entry
        // must be evicted. Without this, a regression that silently widens the
        // window (e.g. Long.MAX_VALUE, or the cutoff computed with the wrong
        // sign) would pass every other test in the suite — the cache would
        // grow unbounded but the intent-derivation rows would still see the
        // most-recent event win. This row is the only fold-level guard.
        val window = BeliefState.RECENT_RADIO_WINDOW
        val t0 = SimTime.ofMillis(0)
        val tPastWindow = SimTime.ofMillis(window.millis + 1)
        val afterFirst = BeliefState.EMPTY.withRecentRadio(
            listOf(ControllerEvent.InitialContactReceived(aircraft, RequestTaxi())),
            t0,
        )
        val afterSecond = afterFirst.withRecentRadio(
            listOf(ControllerEvent.AircraftArrivalCommitted(aircraft)),
            tPastWindow,
        )
        val radio = afterSecond.recentRadio[aircraft]
        check(radio != null && radio.entries.size == 1) {
            "Expected the first (t=0) entry to be evicted by the window-prune; got ${radio?.entries}"
        }
        check(radio.entries.single().event is ControllerEvent.AircraftArrivalCommitted) {
            "Expected the surviving entry to be AircraftArrivalCommitted; got ${radio.entries}"
        }
    }

    @Test
    fun `deriveCurrentIntent returns radio intent when present (radio overrides strip)`() {
        // Strip says Departing (from filed plan); radio says Arriving (RequestVisualApproach).
        // Radio wins.
        val radio = RecentRadio.EMPTY.append(
            ControllerEvent.InitialContactReceived(aircraft, RequestVisualApproach),
            now,
            BeliefState.RECENT_RADIO_WINDOW,
        )
        val derived = deriveCurrentIntent(Some(AircraftIntent.Departing), radio)
        check(derived == AircraftIntent.Arriving) {
            "Expected radio override (Arriving); got $derived"
        }
    }

    @Test
    fun `deriveCurrentIntent falls back to strip when radio has no intent`() {
        val derived = deriveCurrentIntent(Some(AircraftIntent.Departing), RecentRadio.EMPTY)
        check(derived == AircraftIntent.Departing) {
            "Expected strip fallback (Departing); got $derived"
        }
    }

    @Test
    fun `deriveCurrentIntent falls back to Transit when neither radio nor strip provides intent`() {
        val derived = deriveCurrentIntent(None, RecentRadio.EMPTY)
        check(derived == AircraftIntent.Transit) {
            "Expected Transit default; got $derived"
        }
    }

    // ── fn-12 (R4): runwayObstructions fold ──────────────────────────

    @Test
    fun `RunwayObstructionDetected writes the obstruction into the slice`() {
        val rwy = RunwayId("16C")
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(60))
        val updated = BeliefState.EMPTY.withRunwayObstructionEvents(listOf(
            ControllerEvent.RunwayObstructionDetected(rwy, obs),
        ))
        check(updated.runwayObstructions[rwy] == obs) {
            "Expected runwayObstructions[$rwy] = $obs; got ${updated.runwayObstructions}"
        }
    }

    @Test
    fun `RunwayObstructionCleared drops the entry from the slice`() {
        val rwy = RunwayId("16C")
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(60))
        val seeded = BeliefState.EMPTY.copy(runwayObstructions = mapOf(rwy to obs))
        val cleared = seeded.withRunwayObstructionEvents(listOf(
            ControllerEvent.RunwayObstructionCleared(rwy),
        ))
        check(rwy !in cleared.runwayObstructions) {
            "Expected runwayObstructions to drop $rwy after Cleared; got ${cleared.runwayObstructions}"
        }
    }

    @Test
    fun `non-obstruction events leave the runwayObstructions slice unchanged (identity)`() {
        // The fold short-circuits on identity equality when no event-leaf
        // touched the slice. A regression that returned a new map for
        // every event would still be functionally correct but waste an
        // allocation per cycle.
        val rwy = RunwayId("16C")
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(60))
        val seeded = BeliefState.EMPTY.copy(runwayObstructions = mapOf(rwy to obs))
        val updated = seeded.withRunwayObstructionEvents(listOf(
            ControllerEvent.InitialContactReceived(aircraft, RequestTaxi()),
            ControllerEvent.AircraftArrivalCommitted(aircraft),
        ))
        check(updated === seeded) {
            "Expected identity short-circuit when no obstruction-event modified the slice"
        }
    }

    @Test
    fun `deriveCurrentIntent uses most-recent intent-bearing radio event`() {
        // Earlier RequestTaxi (Departing); later AircraftArrivalCommitted (Arriving).
        // The most-recent intent-bearing event wins.
        val radio = RecentRadio.EMPTY
            .append(ControllerEvent.InitialContactReceived(aircraft, RequestTaxi()), now, BeliefState.RECENT_RADIO_WINDOW)
            .append(ControllerEvent.AircraftArrivalCommitted(aircraft), SimTime.ofMillis(now.millis + 1_000), BeliefState.RECENT_RADIO_WINDOW)
        val derived = deriveCurrentIntent(None, radio)
        check(derived == AircraftIntent.Arriving) {
            "Expected most-recent radio intent (Arriving); got $derived"
        }
    }
}
