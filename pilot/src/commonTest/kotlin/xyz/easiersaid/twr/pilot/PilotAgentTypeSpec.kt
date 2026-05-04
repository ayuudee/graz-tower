package xyz.easiersaid.twr.pilot

import arrow.core.NonEmptyList
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Pass 10 (D-AUDIT.4) — per-type behaviour wiring.
 *
 * `AircraftTypeSpec` pins doctrine values; this spec proves the values
 * actually flow through `PilotAgent` to per-type `targetSpeedMps`. Without
 * this row, a typo like `targetSpeedMps = AircraftType.C172.kinematics.climbSpeedMps`
 * (hardcoded receiver instead of `ac.type.kinematics.climbSpeedMps`) ships
 * green — every aircraft would still get C172's climb speed.
 *
 * Real-job: each row exercises a distinct per-type kinematic value at the
 * decision boundary.
 */
class PilotAgentTypeSpec {

    private fun spawn(type: AircraftType, phase: PilotPhase, speedMps: Double): AircraftState =
        AircraftState(
            id = AircraftId("OE-ABC"),
            callsign = Callsign("OEABC"),
            position = Position(xMeters = 0.0, yMeters = 0.0),
            positionPoint = PointId("P"),
            speedMps = speedMps,
            phase = phase,
            type = type,
        )

    @Test
    fun `C172 takeoff roll past rotation speed commands C172 climb speed`() {
        val ac = spawn(
            AircraftType.C172,
            PilotPhase.TakeoffRoll,
            // Past C172 rotation (28 m/s) — should transition to climb.
            speedMps = 30.0,
        ).copy(
            // Need an airborne route to transition out of takeoff roll.
            route = PilotRoute.Airborne(
                waypoints = NonEmptyList(PointId("WP1"), emptyList()),
                arrivalPhase = PilotPhase.Climbing,
                targetAltitudeM = 305.0,
            ),
        )
        val intent = DefaultPilot.decide(
            PilotInput(aircraft = ac, worldIndex = WorldIndex(), world = AviationWorld(), now = SimTime.ZERO),
        ).fold({ fail("pilotDecide failed: $it") }, { it })
        assertEquals(40.0, intent.targetSpeedMps, "C172 climb speed = POH Vy = 40 m/s")
    }

    @Test
    fun `B738 takeoff roll past rotation speed commands B738 climb speed`() {
        val ac = spawn(
            AircraftType.B738,
            PilotPhase.TakeoffRoll,
            // Past B738 rotation (75 m/s).
            speedMps = 80.0,
        ).copy(
            route = PilotRoute.Airborne(
                waypoints = NonEmptyList(PointId("WP1"), emptyList()),
                arrivalPhase = PilotPhase.Climbing,
                targetAltitudeM = 457.0,
            ),
        )
        val intent = DefaultPilot.decide(
            PilotInput(aircraft = ac, worldIndex = WorldIndex(), world = AviationWorld(), now = SimTime.ZERO),
        ).fold({ fail("pilotDecide failed: $it") }, { it })
        assertEquals(
            130.0,
            intent.targetSpeedMps,
            "B738 climb speed = FCOM 250 KIAS below FL100 = 130 m/s — proves per-type wiring (not hardcoded C172)",
        )
    }

    @Test
    fun `B738 at stand with ground route commands taxi target speed from B738 kinematics`() {
        // Pass 10 post-impl test-review Test-Add-1: pin the taxi read site
        // (PilotAgent.kt onAtStand). C172 and B738 share taxiSpeedMps=10.0
        // today, so a typo `aircraft.type.kinematics.climbSpeedMps` instead
        // of `taxiSpeedMps` at this read site would return 130 (B738 climb)
        // instead of 10, failing this row.
        val ac = spawn(
            AircraftType.B738,
            PilotPhase.AtStand,
            speedMps = 0.0,
        ).copy(
            route = PilotRoute.Ground(
                waypoints = NonEmptyList(PointId("WP1"), emptyList()),
                arrivalPhase = PilotPhase.HoldingShort,
            ),
        )
        val intent = DefaultPilot.decide(
            PilotInput(aircraft = ac, worldIndex = WorldIndex(), world = AviationWorld(), now = SimTime.ZERO),
        ).fold({ fail("pilotDecide failed: $it") }, { it })
        assertEquals(
            10.0,
            intent.targetSpeedMps,
            "B738 taxi target speed = 10 m/s (FCOM operationally similar to GA on taxiways)",
        )
        assertEquals(PilotPhase.Taxiing, intent.phase, "AtStand + Ground route → start taxiing")
    }
}
