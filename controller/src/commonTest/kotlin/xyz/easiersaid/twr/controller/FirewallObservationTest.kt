package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import kotlin.test.Test

/**
 * Architectural enforcement test (A5 Test 2) — construction-time field shape.
 *
 * The named-argument constructor below is the FIREWALL ALLOWLIST for
 * [AircraftObservation]. If a pilot-internal field is added (e.g.
 * `pilotGoal`, `humanPiloted`, mission state, internal phase enum), the
 * constructor signature changes and this test fails to compile — the
 * compile error directs the reviewer here.
 *
 * If a new sensor-derived field is legitimately needed, add it to the
 * data class AND name it explicitly in this constructor below. The intent
 * is to force every change to [AircraftObservation] through visible
 * review against the firewall principle.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Fix the violation or amend
 * the firewall via plan revision.
 */
class FirewallObservationTest {

    @Test
    fun `AircraftObservation has only sensor-observable fields`() {
        // FIREWALL ALLOWLIST. Every field below must map to a real-world
        // sensor or visual cue. See `controller/ControllerTypes.kt` and
        // /home/andrew/.claude/plans/deep-mixing-prism.md for the
        // architectural principle.
        val canonical = AircraftObservation(
            id = AircraftId("X"),
            callsign = Callsign("X"),
            position = PointId("P"),
            entities = emptySet(),
            altitude = null,
            speed = null,
            heading = null,
            groundSpeed = null,
            onGround = false,
            wakeCategory = null,
            icaoTypeDesignator = null,
        )
        // Touching the value to ensure the compiler doesn't elide it.
        @Suppress("UNUSED_VARIABLE")
        val _check = canonical.id
    }
}
