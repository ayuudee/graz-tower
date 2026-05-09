package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId

/**
 * fn-8.1: per-aircraft RNG determinism contract.
 *
 * Acceptance evidence for the determinism property: **swapping the within-tick
 * dispatch order of aircraft A and B leaves each aircraft's draws unchanged**
 * (same aircraft IDs throughout). Each id has its own independent stream
 * seeded once via `SimRandom.split(id.value)` in `SimState.initial`; future
 * advances of one aircraft's stream do not affect another's.
 *
 * The test does **not** simulate actual within-tick dispatch reordering —
 * `EventQueue` already totally orders by `(time, source, seq)` (see
 * `EVENT_ORDER` and `AgentId.Pilot.sortKey = "2-pilot-${id.value}"`), so
 * dispatch order at equal `time` is deterministic by construction.
 * Instead, it demonstrates the underlying invariant: input list order does
 * not affect per-aircraft seeding (because `initial` sorts by id), and each
 * aircraft's child stream is independent of any draws on the other's.
 */
class PerAircraftRngSpec {

    private val acA = AircraftId("OE-ABC")
    private val acB = AircraftId("OE-DEF")
    private val pointA = PointId("STAND_A")
    private val pointB = PointId("STAND_B")

    /** Minimal world index containing just the two aircraft start points. */
    private val worldIndex = WorldIndex(
        positions = mapOf(
            pointA to Position(xMeters = 0.0, yMeters = 0.0),
            pointB to Position(xMeters = 50.0, yMeters = 0.0),
        ),
    )

    private fun aircraft(id: AircraftId, point: PointId): AircraftState = AircraftState(
        id = id,
        callsign = Callsign(id.value.replace("-", "")),
        position = worldIndex.positions.getValue(point),
        positionPoint = point,
    )

    @Test
    fun `per-aircraft RNG is independent of input list order`() {
        // Same seed, same aircraft, different list orders.
        val ab = SimState.initial(
            seed = 42L,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft(acA, pointA), aircraft(acB, pointB)),
            weatherByAerodrome = emptyMap<AerodromeId, xyz.easiersaid.twr.controller.WeatherObservation>(),
        ).getOrElse { error("init AB failed: $it") }

        val ba = SimState.initial(
            seed = 42L,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft(acB, pointB), aircraft(acA, pointA)),
            weatherByAerodrome = emptyMap<AerodromeId, xyz.easiersaid.twr.controller.WeatherObservation>(),
        ).getOrElse { error("init BA failed: $it") }

        // Same id → same RNG state regardless of input list order.
        assertEquals(ab.aircraftRng(acA), ba.aircraftRng(acA), "aircraft A RNG must be order-invariant")
        assertEquals(ab.aircraftRng(acB), ba.aircraftRng(acB), "aircraft B RNG must be order-invariant")
        // Different ids → different streams (the keying is on the id).
        assertNotEquals(ab.aircraftRng(acA), ab.aircraftRng(acB), "different ids must produce different streams")
    }

    @Test
    fun `per-aircraft draws are independent across aircraft`() {
        val state = SimState.initial(
            seed = 42L,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft(acA, pointA), aircraft(acB, pointB)),
            weatherByAerodrome = emptyMap<AerodromeId, xyz.easiersaid.twr.controller.WeatherObservation>(),
        ).getOrElse { error("init failed: $it") }

        // Draw on A first, then B.
        val (aDraw1ThenB, _) = run {
            val (aDraw, _) = state.aircraftRng(acA).nextLong()
            val (bDraw, _) = state.aircraftRng(acB).nextLong()
            aDraw to bDraw
        }

        // Draw on B first, then A — A's draw should be unchanged.
        val (bDraw2, _) = state.aircraftRng(acB).nextLong()
        val (aDraw2, _) = state.aircraftRng(acA).nextLong()

        assertEquals(aDraw1ThenB, aDraw2, "aircraft A's first draw must not depend on B drawing first")
        // Sanity: B's draw is the same in both orders too.
        val (bDraw1, _) = state.aircraftRng(acB).nextLong()
        assertEquals(bDraw1, bDraw2, "aircraft B's first draw must not depend on A drawing first")
    }

    @Test
    fun `withAircraftRng updates only the named aircraft's stream`() {
        val state = SimState.initial(
            seed = 42L,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft(acA, pointA), aircraft(acB, pointB)),
            weatherByAerodrome = emptyMap<AerodromeId, xyz.easiersaid.twr.controller.WeatherObservation>(),
        ).getOrElse { error("init failed: $it") }

        val originalA = state.aircraftRng(acA)
        val originalB = state.aircraftRng(acB)
        val (_, advancedA) = originalA.nextLong()

        val updated = state.withAircraftRng(acA, advancedA)
        assertEquals(advancedA, updated.aircraftRng(acA), "A's stream must reflect the advance")
        assertEquals(originalB, updated.aircraftRng(acB), "B's stream must be untouched by A's advance")
    }
}
