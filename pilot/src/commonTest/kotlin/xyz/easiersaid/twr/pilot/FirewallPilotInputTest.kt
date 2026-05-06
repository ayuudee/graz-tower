package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test

/**
 * Architectural enforcement test (E2) — `PilotInput` construction-time field shape.
 *
 * The named-argument constructor below is the FIREWALL ALLOWLIST for [PilotInput].
 * If a non-cockpit field is added (e.g. `controllerBeliefs`, `targetRunway`,
 * `controllerCommitments`, anything reachable from `BeliefState` /
 * `ControllerSpec` / `ControllerView`), the constructor signature changes and
 * this test fails to compile — the compile error directs the reviewer here.
 *
 * If a new cockpit-input field is legitimately needed (e.g. an ATIS broadcast
 * once D-AUDIT.8 lands), add it to the data class AND name it explicitly in
 * this constructor. The intent is to force every change to [PilotInput]
 * through visible review against the firewall principle.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Fix the violation or amend
 * the firewall via plan revision.
 */
class FirewallPilotInputTest {

    @Test
    fun `PilotInput has only cockpit-input fields`() {
        // FIREWALL ALLOWLIST. Every field below must map to a real-world cockpit
        // input: own kinematic state, own filed plan, own visual observation,
        // chart data. See `pilot/PilotInput.kt` and the pilot-firewall plan.
        val canonical = PilotInput(
            aircraft = AircraftState(
                id = AircraftId("X"),
                callsign = Callsign("X"),
                position = xyz.easiersaid.twr.core.world.Position(xMeters = 0.0, yMeters = 0.0),
                positionPoint = PointId("P"),
            ),
            worldIndex = WorldIndex(),
            world = AviationWorld(),
            now = SimTime.ZERO,
            // Pass 15 (D-AUDIT.8 closure): per-aerodrome ATIS — a real-world
            // cockpit input (the pilot tunes the ATIS frequency before first
            // contact and reads the letter into the cockpit). Adding non-
            // cockpit data to this map is a firewall regression.
            atisByAerodrome = emptyMap(),
        )
        @Suppress("UNUSED_VARIABLE")
        val _check = canonical.aircraft
    }
}
