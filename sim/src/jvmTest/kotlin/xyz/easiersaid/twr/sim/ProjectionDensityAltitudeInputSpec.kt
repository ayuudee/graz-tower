package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.Temperature
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport

/**
 * fn-28.1 (G3a-react-density-altitude foundation A): projection-wiring
 * test for [buildPilotInput]'s
 * [xyz.easiersaid.twr.pilot.PilotInput.densityAltitudeInputsByAerodrome]
 * construction.
 *
 * **Semantic cases — pinning the fail-closed contract**:
 *  - **Both OAT + QNH present** → typed
 *    [xyz.easiersaid.twr.pilot.DensityAltitudeInput] entry produced;
 *    fields carry-through verbatim.
 *  - **OAT present, QNH null** → NO entry (fail-closed).
 *  - **OAT null, QNH present** → NO entry (fail-closed).
 *  - **`weather == null`** → NO entry (fail-closed; same shape as
 *    `weatherByAerodrome`'s mapNotNull semantic).
 *  - **Field-elevation source** (round-14 Minor 1 acceptance): the
 *    valid-elevation case is covered by the all-positive case;
 *    `Aerodrome.elevation: Feet` is non-null today so no "missing
 *    elevation" row is constructible without a schema change. The
 *    `DensityAltitudeInput.fieldElevation` KDoc + the projection-site
 *    comment in `PilotWiring.buildPilotInput` document the
 *    forward-applicable fail-closed contract.
 *
 * Mirrors the shape of [PilotWiringWeatherProjectionSpec] for the
 * sibling `weatherByAerodrome` projection (fn-16's R7a).
 */
class ProjectionDensityAltitudeInputSpec {

    private val lowg = AerodromeId("LOWG")
    private val ljmb = AerodromeId("LJMB")
    private val eddm = AerodromeId("EDDM")
    private val lszh = AerodromeId("LSZH")

    private val spawnPoint = PointId("SPAWN")
    private val acId = AircraftId("OE-TST")

    /** Empty-runways aerodrome — R5a vacuous. Distinct elevations per ICAO. */
    private fun emptyAerodrome(id: AerodromeId, elevationFt: Int): Aerodrome =
        Aerodrome(
            icao = id,
            elevation = Feet(elevationFt),
            magneticVariation = Degrees(0.0),
            transitionAltitude = Level.AltitudeFeet.unsafe(5000),
        )

    private val sampleWind = Wind.unsafe(directionDegrees = 90, speedKnots = 10)
    private val sampleOat = Temperature.celsius(12.79)
    private val sampleQnh = PressureSetting.QnhHpa.unsafe(1013)

    private fun aircraft(): AircraftState = AircraftState(
        id = acId,
        callsign = Callsign("OE-TST"),
        position = Position(0.0, 0.0),
        positionPoint = spawnPoint,
    )

    @Test
    fun bothOatAndQnhPresentProducesTypedEntry() {
        val world = AviationWorld(aerodromes = mapOf(lowg to emptyAerodrome(lowg, 1115)))
        val worldIndex = WorldIndex(positions = mapOf(spawnPoint to Position(0.0, 0.0)))
        val state = SimState.initial(
            seed = 42L,
            world = world,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft()),
            weatherByAerodrome = mapOf(
                lowg to WeatherObservation(
                    wind = WindReport.Available(sampleWind),
                    qnh = sampleQnh,
                    visibility = null,
                    oat = sampleOat,
                ),
            ),
        ).getOrNull() ?: error("SimState.initial failed unexpectedly")

        val input = buildPilotInput(state, acId)
            ?: error("buildPilotInput returned null — aircraft not in state")

        val entry = input.densityAltitudeInputsByAerodrome[lowg]
        assertNotNull(entry, "concrete OAT+QNH must produce a DensityAltitudeInput entry")
        assertEquals(sampleOat, entry.oat)
        assertEquals(sampleQnh, entry.qnh)
        assertEquals(Feet(1115), entry.fieldElevation)
        assertEquals(1, input.densityAltitudeInputsByAerodrome.size)
    }

    @Test
    fun nullQnhFailsClosedNoEntry() {
        val world = AviationWorld(aerodromes = mapOf(lowg to emptyAerodrome(lowg, 1115)))
        val worldIndex = WorldIndex(positions = mapOf(spawnPoint to Position(0.0, 0.0)))
        val state = SimState.initial(
            seed = 42L,
            world = world,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft()),
            weatherByAerodrome = mapOf(
                lowg to WeatherObservation(
                    wind = WindReport.Available(sampleWind),
                    qnh = null,
                    visibility = null,
                    oat = sampleOat,
                ),
            ),
        ).getOrNull() ?: error("SimState.initial failed unexpectedly")

        val input = buildPilotInput(state, acId)
            ?: error("buildPilotInput returned null — aircraft not in state")

        assertNull(
            input.densityAltitudeInputsByAerodrome[lowg],
            "OAT-only (qnh=null) must fail closed — no DensityAltitudeInput entry",
        )
        assertEquals(0, input.densityAltitudeInputsByAerodrome.size)
    }

    @Test
    fun nullOatFailsClosedNoEntry() {
        val world = AviationWorld(aerodromes = mapOf(lowg to emptyAerodrome(lowg, 1115)))
        val worldIndex = WorldIndex(positions = mapOf(spawnPoint to Position(0.0, 0.0)))
        val state = SimState.initial(
            seed = 42L,
            world = world,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft()),
            weatherByAerodrome = mapOf(
                lowg to WeatherObservation(
                    wind = WindReport.Available(sampleWind),
                    qnh = sampleQnh,
                    visibility = null,
                    oat = null,
                ),
            ),
        ).getOrNull() ?: error("SimState.initial failed unexpectedly")

        val input = buildPilotInput(state, acId)
            ?: error("buildPilotInput returned null — aircraft not in state")

        assertNull(
            input.densityAltitudeInputsByAerodrome[lowg],
            "QNH-only (oat=null) must fail closed — no DensityAltitudeInput entry",
        )
        assertEquals(0, input.densityAltitudeInputsByAerodrome.size)
    }

    @Test
    fun absentWeatherFailsClosedNoEntry() {
        val world = AviationWorld(aerodromes = mapOf(eddm to emptyAerodrome(eddm, 1487)))
        val worldIndex = WorldIndex(positions = mapOf(spawnPoint to Position(0.0, 0.0)))
        val state = SimState.initial(
            seed = 42L,
            world = world,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft()),
            weatherByAerodrome = emptyMap(),
        ).getOrNull() ?: error("SimState.initial failed unexpectedly")

        val input = buildPilotInput(state, acId)
            ?: error("buildPilotInput returned null — aircraft not in state")

        assertNull(
            input.densityAltitudeInputsByAerodrome[eddm],
            "weather=null on aerodrome must fail closed — no DensityAltitudeInput entry",
        )
        assertEquals(0, input.densityAltitudeInputsByAerodrome.size)
    }

    @Test
    fun mixedFleetPartialProjection() {
        // Three aerodromes, three weather shapes — projection lifts only
        // the one with BOTH oat and qnh non-null.
        val world = AviationWorld(
            aerodromes = mapOf(
                lowg to emptyAerodrome(lowg, 1115),    // both present → projected
                ljmb to emptyAerodrome(ljmb, 876),     // qnh null → skipped
                lszh to emptyAerodrome(lszh, 1416),    // oat null → skipped
                eddm to emptyAerodrome(eddm, 1487),    // weather absent → skipped
            ),
        )
        val worldIndex = WorldIndex(positions = mapOf(spawnPoint to Position(0.0, 0.0)))
        val state = SimState.initial(
            seed = 42L,
            world = world,
            worldIndex = worldIndex,
            aircraft = listOf(aircraft()),
            weatherByAerodrome = mapOf(
                lowg to WeatherObservation(
                    wind = WindReport.Available(sampleWind),
                    qnh = sampleQnh,
                    visibility = null,
                    oat = sampleOat,
                ),
                ljmb to WeatherObservation(
                    wind = WindReport.Available(sampleWind),
                    qnh = null, // ← fails closed
                    visibility = null,
                    oat = sampleOat,
                ),
                lszh to WeatherObservation(
                    wind = WindReport.Available(sampleWind),
                    qnh = sampleQnh,
                    visibility = null,
                    oat = null, // ← fails closed
                ),
                // EDDM intentionally absent — its weather stays null.
            ),
        ).getOrNull() ?: error("SimState.initial failed unexpectedly")

        val input = buildPilotInput(state, acId)
            ?: error("buildPilotInput returned null — aircraft not in state")

        assertNotNull(input.densityAltitudeInputsByAerodrome[lowg])
        assertNull(input.densityAltitudeInputsByAerodrome[ljmb], "qnh null → no entry")
        assertNull(input.densityAltitudeInputsByAerodrome[lszh], "oat null → no entry")
        assertNull(input.densityAltitudeInputsByAerodrome[eddm], "weather null → no entry")
        assertEquals(1, input.densityAltitudeInputsByAerodrome.size, "only LOWG passes the all-three gate")
    }
}
