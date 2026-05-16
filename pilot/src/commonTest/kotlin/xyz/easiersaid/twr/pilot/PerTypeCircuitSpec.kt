package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.CircuitLeg
import xyz.easiersaid.twr.pilot.world.PilotAviationWorld
import xyz.easiersaid.twr.pilot.world.toPilotView
import xyz.easiersaid.twr.core.world.CircuitProcedure
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Pass 13 (D-AUDIT.4.D-FOLLOWUP) — per-type circuit altitude wiring.
 *
 * `AircraftTypeSpec` pins doctrine values:
 *  - C172 pattern altitude = 305 m AGL (POH §4 / FAA AIM 4-3-3 — 1000 ft).
 *  - B738 pattern altitude = 457 m AGL (FCOM Supplementary — 1500 ft).
 *
 * This spec proves the values flow through `buildVisualDepartureRoute`
 * (and by uniformity through every other route-builder helper, which all
 * read the same `aircraftType.circuitPattern.altitudeAglM` field) into
 * the returned `PilotRoute.Airborne.targetAltitudeM`. Without this row,
 * a typo like `targetAltitudeM = AircraftType.C172.circuitPattern.altitudeAglM`
 * (hardcoded receiver instead of `aircraftType.circuitPattern.altitudeAglM`)
 * ships green — every aircraft would still get C172's 305 m.
 *
 * Real-job: each row exercises a distinct per-type circuit-altitude
 * value at the route-planner boundary.
 */
class PerTypeCircuitSpec {

    @Test
    fun `C172 visual departure route targets 305m circuit altitude`() {
        val route = buildVisualDepartureRoute(
            runwayId = RWY_ID,
            world = synthetic1RunwayWorld(),
            aircraftType = AircraftType.C172,
        ).fold({ fail("buildVisualDepartureRoute failed: $it") }, { it })
        assertEquals(
            305.0,
            route.targetAltitudeM,
            "C172 pattern altitude POH §4 / FAA AIM 4-3-3 = 1000 ft AGL = 305 m",
        )
    }

    @Test
    fun `B738 visual departure route targets 457m jet circuit altitude`() {
        val route = buildVisualDepartureRoute(
            runwayId = RWY_ID,
            world = synthetic1RunwayWorld(),
            aircraftType = AircraftType.B738,
        ).fold({ fail("buildVisualDepartureRoute failed: $it") }, { it })
        assertEquals(
            457.0,
            route.targetAltitudeM,
            "B738 jet visual-circuit pattern altitude FCOM = 1500 ft AGL = 457 m " +
                "— proves per-type wiring (not hardcoded C172)",
        )
    }

    companion object {
        private val ADRM_ID = AerodromeId("XXXX")
        private val RWY_ID = RunwayId("09")
        private val CKT_ID = CircuitProcedureId("RWY09-LH")

        // Minimal synthetic world: one aerodrome, one runway, one left-hand
        // circuit. Path constraints are satisfied (legs sequenced + closed
        // loop). Geometry is irrelevant to the per-type altitude assertion;
        // the test exercises only the value-flow through the helper.
        private val THRESHOLD = PointId("T")
        private val DEP_END = PointId("DEP")
        private val UPWIND_END = PointId("UE")
        private val CROSSWIND_END = PointId("CE")
        private val DOWNWIND_END = PointId("DE")
        private val BASE_TURN = PointId("BT")

        private fun synthetic1RunwayWorld(): PilotAviationWorld {
            val runway = Runway(
                id = RWY_ID,
                path = Path(listOf(THRESHOLD, DEP_END)),
                threshold = THRESHOLD,
            )
            val circuit = CircuitProcedure(
                id = CKT_ID,
                runway = RWY_ID,
                direction = CircuitDirection.LEFT_HAND,
                legs = listOf(
                    CircuitLeg(LegName.UPWIND, Path(listOf(THRESHOLD, UPWIND_END))),
                    CircuitLeg(LegName.CROSSWIND, Path(listOf(UPWIND_END, CROSSWIND_END))),
                    CircuitLeg(LegName.DOWNWIND, Path(listOf(CROSSWIND_END, DOWNWIND_END))),
                    CircuitLeg(LegName.BASE, Path(listOf(DOWNWIND_END, BASE_TURN))),
                    CircuitLeg(LegName.FINAL, Path(listOf(BASE_TURN, THRESHOLD))),
                ),
                altitude = Level.AltitudeFeet.unsafe(1000),
                goAroundPath = Path(listOf(THRESHOLD, UPWIND_END)),
            )
            val aerodrome = Aerodrome(
                icao = ADRM_ID,
                elevation = Feet(0),
                magneticVariation = Degrees(0.0),
                transitionAltitude = Level.AltitudeFeet.unsafe(5000),
                runways = mapOf(RWY_ID to runway),
                circuits = mapOf(CKT_ID to circuit),
            )
            return AviationWorld(aerodromes = mapOf(ADRM_ID to aerodrome)).toPilotView()
        }
    }
}
