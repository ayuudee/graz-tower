package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.world.toPilotView
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals

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
            world = AviationWorld().toPilotView(),
            now = SimTime.ZERO,
            // Pass 15 (D-AUDIT.8 closure): per-aerodrome ATIS — a real-world
            // cockpit input (the pilot tunes the ATIS frequency before first
            // contact and reads the letter into the cockpit). Adding non-
            // cockpit data to this map is a firewall regression.
            atisByAerodrome = emptyMap(),
            // fn-14.1 (G3a-react R3): per-aerodrome wind state — a real-world
            // cockpit input (windsock / ASI / instrument scan). The pilot
            // consumes only the WindReport projection from each
            // WeatherObservation; QNH/visibility stay controller-side.
            weatherByAerodrome = emptyMap(),
            // fn-28.1 (G3a-react-density-altitude foundation A): per-aerodrome
            // typed DA inputs (OAT + QNH + field elevation) — a real-world
            // cockpit input (ATIS-read OAT/QNH + published chart elevation).
            // `DensityAltitudeInput` is structurally constrained to
            // typed-units + scalars; no entity reference reachable. Adding
            // non-cockpit data via this map is a firewall regression.
            densityAltitudeInputsByAerodrome = emptyMap(),
        )
        @Suppress("UNUSED_VARIABLE")
        val _check = canonical.aircraft
    }

    /**
     * fn-14.1 (G3a-react R3) — reflection-based property scan. The
     * canonical-constructor block above is necessary but not sufficient:
     * a future field added with a default value would slip past the
     * canonical call (call sites don't have to name defaulted args).
     *
     * This test enumerates every public property of `PilotInput` via
     * Kotlin reflection and compares against a hard-coded allowlist of
     * cockpit-input field names. A new field added without updating
     * `allowedFields` fails this test — forcing a deliberate review
     * against the firewall principle (real-world cockpit input?
     * justified in PilotInput KDoc? added to the canonical-constructor
     * test?).
     *
     * **No-suppression rule** applies as to the canonical block.
     */
    @Test
    fun `PilotInput property names match the firewall allowlist`() {
        val allowedFields = setOf(
            "aircraft",
            "worldIndex",
            "world",
            "now",
            "atisByAerodrome",
            "weatherByAerodrome",
            // fn-28.1: per-aerodrome typed DensityAltitudeInput projection
            // (ATIS-read OAT/QNH + published chart elevation). Firewall-clean
            // by construction — `DensityAltitudeInput` carries only
            // typed-units (Temperature, PressureSetting, Feet) and no
            // entity references.
            "densityAltitudeInputsByAerodrome",
        )
        val actualFields = PilotInput::class.memberProperties.map { it.name }.toSet()
        assertEquals(
            allowedFields,
            actualFields,
            """
            FIREWALL VIOLATION: PilotInput field set does not match the
            allowlist. Every PilotInput field must map to a real-world
            cockpit input (own kinematic state, filed plan, visual
            observation, chart data).

            Expected (allowlist): $allowedFields
            Actual (PilotInput):  $actualFields

            Resolution paths:
              - If the new field IS a cockpit input: add it to
                `allowedFields` here AND add a named-argument entry to
                the canonical-constructor block above AND justify in
                the field's KDoc.
              - If the new field IS NOT a cockpit input (e.g. anything
                reachable from BeliefState / ControllerSpec /
                ControllerView): remove it. Smuggling controller-side
                state through PilotInput is the firewall regression
                Phase C deleted.

            No `@Suppress`, no `@Disabled`, no test removal.
            """.trimIndent(),
        )
    }
}
