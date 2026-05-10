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
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.requiredStartPoints

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
    fun `step handlePilotTick advances the per-aircraft RNG and leaves others byte-stable`() {
        // Integration test: drive a single PilotDecisionTick for aircraft A
        // through step() and assert that:
        //   - A's RNG stream has advanced (the production tick handler
        //     consumed one draw and persisted it via withAircraftRng)
        //   - B's RNG stream is byte-identical to its initial seeded value
        //
        // This is the load-bearing R2 assertion: that the production pilot
        // tick path actually reads/writes per-aircraft RNG, not just the
        // helper map in isolation. A regression that re-uses `state.rng`
        // instead of `state.aircraftRng(id)` would break this row.
        //
        // We use Fixtures.LOWG_TWO_AIRCRAFT to get a real world (aircraft
        // need a valid positionPoint in worldIndex.positions, and the pilot
        // tick must be able to call buildPilotInput / pilotDecide without
        // hitting the null-aircraft early-return).
        val loaded = Fixtures.LOWG_TWO_AIRCRAFT.load().getOrElse {
            error("LOWG_TWO_AIRCRAFT failed to load: $it")
        }
        val starts = Fixtures.LOWG_TWO_AIRCRAFT.requiredStartPoints()
        val now = xyz.easiersaid.twr.protocol.SimTime.ZERO
        // Build minimal AircraftStates for both aircraft at their start
        // points. Pilot mission left null → DefaultPilot path; a single
        // pilot tick is enough to exercise the RNG threading regardless
        // of mission cognition.
        val aircraftA = AircraftState(
            id = acA,
            callsign = Callsign(acA.value.replace("-", "")),
            position = loaded.world.geometry.points.getValue(starts.getValue(acA)),
            positionPoint = starts.getValue(acA),
        )
        val aircraftB = AircraftState(
            id = acB,
            callsign = Callsign(acB.value.replace("-", "")),
            position = loaded.world.geometry.points.getValue(starts.getValue(acB)),
            positionPoint = starts.getValue(acB),
        )
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraftA, aircraftB),
            controllers = loaded.controllers.values.toList(),
            weatherByAerodrome = mapOf(
                xyz.easiersaid.twr.protocol.AerodromeId("LOWG") to Fixtures.LOWG_TWO_AIRCRAFT.weather,
            ),
        ).getOrElse { error("SimState.initial rejected fixture: $it") }

        val acARngBefore = initialState.aircraftRng(acA)
        val acBRngBefore = initialState.aircraftRng(acB)

        // Drive one PilotDecisionTick for A only. step() may emit follow-up
        // events; we only care about the resulting state's per-aircraft RNG.
        val (afterTickA, _) = step(initialState, SimEvent.PilotDecisionTick(time = now, aircraftId = acA))

        assertNotEquals(
            acARngBefore,
            afterTickA.aircraftRng(acA),
            "A's per-aircraft RNG must advance after handlePilotTick",
        )
        assertEquals(
            acBRngBefore,
            afterTickA.aircraftRng(acB),
            "B's per-aircraft RNG must be byte-stable when only A's tick fires",
        )
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
