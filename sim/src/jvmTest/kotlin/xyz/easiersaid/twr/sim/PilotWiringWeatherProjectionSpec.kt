package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WeatherObservation
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport

/**
 * fn-16 (R7a-test): focused test for [buildPilotInput]'s
 * `mapNotNull` absent-key-preserving projection.
 *
 * **Three semantic cases — pinning the `mapNotNull` contract:**
 *  - **Available wind** → projected as `WindReport.Available`.
 *  - **NotReported wind** → projected as `WindReport.NotReported`
 *    (`mapNotNull` projects every aerodrome with non-null `weather`,
 *    even when `weather.wind` is `NotReported`).
 *  - **`weather == null`** → **NOT projected** (the projection skips
 *    aerodromes whose entity has no weather). This is the load-bearing
 *    semantic: a wrong `mapValues { weather?.wind ?: NotReported }`
 *    implementation would synthesise a spurious `NotReported` entry
 *    for the unweathered aerodrome and break the
 *    `windForMission`'s `map.size == 1` singleton-fallback path.
 *
 * Setup uses [SimState.initial] — the canonical construction path that
 * exercises both new invariants (R5a, R5b) plus the production wiring.
 * All three aerodromes have empty `runways` so R5a is vacuously
 * satisfied without authoring runway geometry; LJMB and LOWG carry
 * weather entries that pass R5b.
 */
class PilotWiringWeatherProjectionSpec {

    private val lowg = AerodromeId("LOWG")
    private val ljmb = AerodromeId("LJMB")
    private val eddm = AerodromeId("EDDM")

    private val spawnPoint = PointId("SPAWN")
    private val acId = AircraftId("OE-TST")

    /** Empty-runways aerodrome — R5a vacuous. */
    private fun emptyAerodrome(id: AerodromeId): Aerodrome =
        Aerodrome(
            icao = id,
            elevation = Feet(0),
            magneticVariation = Degrees(0.0),
            transitionAltitude = Level.AltitudeFeet.unsafe(5000),
        )

    private val lowgWind = Wind.unsafe(directionDegrees = 90, speedKnots = 10)
    private val lowgObservation = WeatherObservation(
        wind = WindReport.Available(lowgWind),
        qnh = null,
        visibility = null,
    )
    private val ljmbNotReportedObservation = WeatherObservation(
        wind = WindReport.NotReported,
        qnh = null,
        visibility = null,
    )

    private fun aircraft(): AircraftState = AircraftState(
        id = acId,
        callsign = Callsign("OE-TST"),
        position = Position(0.0, 0.0),
        positionPoint = spawnPoint,
    )

    @Test
    fun mapNotNullProjectionPreservesAbsentKeySemantics() {
        val world = AviationWorld(
            aerodromes = mapOf(
                lowg to emptyAerodrome(lowg),
                ljmb to emptyAerodrome(ljmb),
                eddm to emptyAerodrome(eddm),
            ),
        )
        val worldIndex = WorldIndex(positions = mapOf(spawnPoint to Position(0.0, 0.0)))
        val state = SimState.initial(
            seed = 42L,
            world = world,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft()),
            weatherByAerodrome = mapOf(
                lowg to lowgObservation,
                ljmb to ljmbNotReportedObservation,
                // EDDM intentionally absent — its weather stays null
                // after the fold.
            ),
        ).getOrNull() ?: error("SimState.initial failed unexpectedly")

        val input = buildPilotInput(state, acId)
            ?: error("buildPilotInput returned null — aircraft not in state")

        // R7a case 1: Available wind → projected as Available.
        assertEquals(
            WindReport.Available(lowgWind),
            input.weatherByAerodrome[lowg],
            "Available wind must lift through the mapNotNull projection",
        )
        // R7a case 2: NotReported wind → projected as NotReported
        // (mapNotNull keys on `weather?.wind != null`, not on
        // `weather.wind is Available`).
        assertEquals(
            WindReport.NotReported,
            input.weatherByAerodrome[ljmb],
            "NotReported wind must also project — mapNotNull skips only when weather is null",
        )
        // R7a case 3: weather == null → NOT projected. The wrong
        // `mapValues { weather?.wind ?: NotReported }` implementation
        // would synthesise a spurious NotReported here.
        assertNull(
            input.weatherByAerodrome[eddm],
            "aerodrome with weather == null must NOT appear in the projection",
        )
        assertEquals(
            2,
            input.weatherByAerodrome.size,
            "projection must contain exactly LOWG + LJMB (EDDM has weather == null)",
        )
    }
}
