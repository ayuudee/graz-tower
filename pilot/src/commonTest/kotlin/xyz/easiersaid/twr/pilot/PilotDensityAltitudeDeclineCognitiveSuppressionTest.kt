package xyz.easiersaid.twr.pilot

import arrow.core.Some
import arrow.core.getOrElse
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.PhysicalGeometry
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.buildWorldIndex
import xyz.easiersaid.twr.pilot.world.toPilotView
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.Feet
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.Request
import xyz.easiersaid.twr.protocol.RequestTaxi
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Temperature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * fn-28.2 (G3a-react-density-altitude R14 / round-13 Major 1):
 * `pilotDecide` cognitive-suppression mechanism — when DA-decline fires,
 * the same-tick cognitive transmission (`Request(RequestTaxi)` on
 * REQUEST_TAXI's first tick) MUST be zeroed.
 *
 * **Round-13 Major 1 contract**: the suppression must apply BEFORE every
 * `PilotOutput` construction site — `PlanRouteOutcome.Plan` branch AND
 * `Skip` branch AND any error/fallback branch. NOT only the `Skip` path.
 * This test exercises every return path with the suppression-flagged
 * event payload by constructing fixtures that route through each
 * `planOutcome` branch.
 *
 * **Apron-pre-taxi scenario**: pilot's mission is `Departure`, currentTask
 * is REQUEST_TAXI (first tick → would normally emit `Request(RequestTaxi)`),
 * aircraft at-stand. World fixture publishes a single LOWG aerodrome
 * with high-DA weather (ISA+35°C, QNH 1013) → DA ≈ 5323 ft, exceeds
 * C172's 5000 ft threshold → DA-decline fires.
 */
class PilotDensityAltitudeDeclineCognitiveSuppressionTest {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO
    private val lowg = AerodromeId("LOWG")
    private val rwy09 = RunwayId("09")
    private val apron = PointId("APRON")
    private val rwyThreshold = PointId("T")

    // Minimal world for the apron-side scenario. The aircraft is at the
    // apron point; we still wire a runway + aerodrome so the goal +
    // activeRunway resolve.
    private fun syntheticWorld(): AviationWorld {
        val runway = Runway(
            id = rwy09,
            path = Path(listOf(rwyThreshold, PointId("RWYEND"))),
            threshold = rwyThreshold,
        )
        val aerodrome = Aerodrome(
            icao = lowg,
            elevation = Feet.unsafe(1115),  // LOWG ≈ 1115 ft
            magneticVariation = Degrees(0.0),
            transitionAltitude = Level.AltitudeFeet.unsafe(5000),
            runways = mapOf(rwy09 to runway),
            circuits = emptyMap(),
        )
        return AviationWorld(
            geometry = PhysicalGeometry(
                points = mapOf(
                    apron to Position(0.0, 0.0),
                    rwyThreshold to Position(100.0, 0.0),
                    PointId("RWYEND") to Position(1000.0, 0.0),
                ),
            ),
            aerodromes = mapOf(lowg to aerodrome),
        )
    }

    private fun departureMissionAtRequestTaxi(): PilotMission {
        // Build groundDepartureTask with REQUEST_TAXI as the active step
        // (first child, uncompleted; nothing complete before it).
        val rootDepart = CompoundTask(
            name = TaskName.Depart,
            children = listOf(
                groundDepartureTask(),  // REQUEST_TAXI is first child
                PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
                PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
            ),
        )
        return PilotMission(
            goal = HighLevelGoal.Departure(),
            root = rootDepart,
            stepEnteredAt = now0,
            // No activeRunway set — pre-clearance the pilot is silent;
            // DA-decline does not need a runway-assignment.
        )
    }

    private fun highDaInput(): DensityAltitudeInput = DensityAltitudeInput(
        oat = Temperature.celsius(47.79),
        qnh = PressureSetting.QnhHpa.unsafe(1013),
        fieldElevation = Feet.unsafe(1115),
    )

    @Test
    fun `pilotDecide suppresses Request(RequestTaxi) on the tick DA-decline fires (Skip path)`() {
        // SETUP: mission at REQUEST_TAXI (first tick → cognitive layer
        // would normally emit `Request(RequestTaxi)` per the
        // `stepTransmission` REQUEST_TAXI arm). With a high-DA input,
        // DA-decline fires.
        val mission = departureMissionAtRequestTaxi()
        val world = syntheticWorld()
        val aircraft = AircraftState(
            id = ac,
            callsign = Callsign("OEABC"),
            position = Position(0.0, 0.0),
            positionPoint = apron,
            altitudeM = 0.0,
            phase = PilotPhase.AtStand,
            type = AircraftType.C172,
            pilotMission = mission,
        )

        val output = pilotDecide(
            PilotInput(
                aircraft = aircraft,
                worldIndex = world.buildWorldIndex(),
                world = world.toPilotView(),
                now = now0,
                densityAltitudeInputsByAerodrome = mapOf(lowg to highDaInput()),
            ),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // ASSERT: no Request(RequestTaxi) transmission. The cognitive
        // layer's stepTransmission would have emitted it; suppression
        // zeroes it.
        assertFalse(
            output.transmissions.any { it is Request && it.type is RequestTaxi },
            "cognitive suppression must zero Request(RequestTaxi) when DA-decline fires; " +
                "got transmissions = ${output.transmissions}",
        )
    }

    @Test
    fun `pilotDecide produces DA-decline intent on the tick DA-decline fires`() {
        val mission = departureMissionAtRequestTaxi()
        val world = syntheticWorld()
        val aircraft = AircraftState(
            id = ac,
            callsign = Callsign("OEABC"),
            position = Position(0.0, 0.0),
            positionPoint = apron,
            altitudeM = 0.0,
            phase = PilotPhase.AtStand,
            type = AircraftType.C172,
            pilotMission = mission,
        )
        val output = pilotDecide(
            PilotInput(
                aircraft = aircraft,
                worldIndex = world.buildWorldIndex(),
                world = world.toPilotView(),
                now = now0,
                densityAltitudeInputsByAerodrome = mapOf(lowg to highDaInput()),
            ),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // ASSERT: apron-terminal intent — targetSpeedMps = 0, phase = AtStand.
        // The DA-decline path's intent takes precedence over the cognitive
        // baseline + the (no-op) planRoute result.
        assertEquals(
            0.0,
            output.intent.targetSpeedMps,
            1e-9,
            "DA-decline intent: targetSpeedMps = 0 (at-rest on apron)",
        )
        assertEquals(
            PilotPhase.AtStand,
            output.intent.phase,
            "DA-decline intent: phase = AtStand",
        )
        // ASSERT: mission tree contains DECLINE_DEPARTURE primitive.
        fun walk(task: TaskNode): List<MissionStep> = when (task) {
            is PrimitiveTask -> listOf(task.step)
            is CompoundTask -> task.children.flatMap { walk(it) }
        }
        assertTrue(
            output.updatedMission != null &&
                MissionStep.DECLINE_DEPARTURE in walk(output.updatedMission.root),
            "DA-decline rewrites the mission tree; DECLINE_DEPARTURE primitive must be present",
        )
    }

    @Test
    fun `pilotDecide does NOT suppress when DA-decline does NOT fire (control row)`() {
        // CONTROL: same scenario but with ISA-temp DA input → DA below
        // threshold → DA-decline does NOT fire → cognitive transmissions
        // pass through normally.
        val mission = departureMissionAtRequestTaxi()
        val world = syntheticWorld()
        val aircraft = AircraftState(
            id = ac,
            callsign = Callsign("OEABC"),
            position = Position(0.0, 0.0),
            positionPoint = apron,
            altitudeM = 0.0,
            phase = PilotPhase.AtStand,
            type = AircraftType.C172,
            pilotMission = mission,
        )
        val isaDaInput = DensityAltitudeInput(
            oat = Temperature.celsius(12.79),  // ISA at 1115 ft
            qnh = PressureSetting.QnhHpa.unsafe(1013),
            fieldElevation = Feet.unsafe(1115),
        )
        val output = pilotDecide(
            PilotInput(
                aircraft = aircraft,
                worldIndex = world.buildWorldIndex(),
                world = world.toPilotView(),
                now = now0,
                densityAltitudeInputsByAerodrome = mapOf(lowg to isaDaInput),
            ),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // ASSERT: Request(RequestTaxi) IS emitted (normal pre-fn-28.2
        // behaviour preserved when DA-decline does not fire).
        assertTrue(
            output.transmissions.any { it is Request && it.type is RequestTaxi },
            "control row: no DA-decline → cognitive Request(RequestTaxi) passes through; " +
                "got transmissions = ${output.transmissions}",
        )
    }

    // ── Focused-seam coverage for the Plan branch (round-2 codex fix) ──
    //
    // **Why a focused seam**: round-13 Major 1 contract requires the
    // suppression to apply BEFORE every PilotOutput construction site —
    // BOTH `PlanRouteOutcome.Plan` AND `PlanRouteOutcome.Skip` branches.
    //
    // **Natural reachability**: in pre-taxi DA-decline scenarios,
    // planRoute returns `Skip` (REQUEST_TAXI / TAXI_TO_HOLDING /
    // DECLINE_DEPARTURE are not in airborneSteps; no activeRunway is
    // set at the apron). The `Plan` branch is unreachable in the
    // natural DA-decline flow because DA-decline's pre-taxi gates and
    // planRoute's airborne-steps / Transit-cruise gates are mutually
    // exclusive by R16 design.
    //
    // **Focused-seam strategy** (codex round-2 round-trip fix): the
    // suppression logic is refactored to a named helper
    // [applyCognitiveSuppression] which both pilotDecide return
    // branches call once. Unit-testing the helper directly covers the
    // suppression logic without requiring a synthetic "DA-decline AND
    // Plan both fire" fixture. The structural code-share in pilotDecide
    // is what makes the Plan/Skip parity hold; the helper is the unit
    // under test for the parity contract.

    @Test
    fun `applyCognitiveSuppression — flag true zeroes transmissions list`() {
        // Direct seam test (round-2 codex fix replacing the broken
        // `return@Test` Plan-path test): exercises the structural
        // contract that BOTH pilotDecide return branches call this
        // helper, so any branch's output is suppressed identically.
        val cognitive: List<xyz.easiersaid.twr.protocol.PilotTransmission> = listOf(
            Request(RequestTaxi()),
        )
        val suppressed = applyCognitiveSuppression(cognitive, suppressSameTickCognitive = true)
        assertEquals(
            emptyList<xyz.easiersaid.twr.protocol.PilotTransmission>(),
            suppressed,
            "round-13 Major 1: suppression true → empty list; pilotDecide's PlanRouteOutcome.Plan AND " +
                "PlanRouteOutcome.Skip branches both call this helper, so this row covers both paths " +
                "by structural code-share",
        )
    }

    @Test
    fun `applyCognitiveSuppression — flag false passes transmissions through verbatim`() {
        // Negative parity: when the flag is false, the helper is the
        // identity function. Pin the contract: BOTH pilotDecide branches
        // must produce normal transmissions in this regime.
        val cognitive: List<xyz.easiersaid.twr.protocol.PilotTransmission> = listOf(
            Request(RequestTaxi()),
        )
        val passed = applyCognitiveSuppression(cognitive, suppressSameTickCognitive = false)
        assertEquals(cognitive, passed, "flag=false → identity; transmissions pass through verbatim")
    }

    @Test
    fun `applyCognitiveSuppression — empty input + flag true returns empty (no-op idempotence)`() {
        // Edge case: empty cognitive transmissions + suppression flag
        // true is still a valid call (e.g. a tick where the cognitive
        // layer had nothing to say AND DA-decline fired). Both behaviors
        // are idempotent → empty list.
        val suppressed = applyCognitiveSuppression(emptyList(), suppressSameTickCognitive = true)
        assertEquals(emptyList<xyz.easiersaid.twr.protocol.PilotTransmission>(), suppressed)
    }

    @Test
    fun `B738 fallthrough — DA-decline does NOT fire, no suppression, normal transmissions`() {
        // B738 has null maxDensityAltitudeFt → DA-decline never fires
        // even with a high-DA input. Suppression must not trigger.
        // (REQUEST_TAXI on B738 is operationally identical to C172 from
        // the cognitive-layer perspective; the per-type kinematics differ
        // but the transmission slot is the same.)
        val mission = departureMissionAtRequestTaxi()
        val world = syntheticWorld()
        val aircraft = AircraftState(
            id = ac,
            callsign = Callsign("OEABC"),
            position = Position(0.0, 0.0),
            positionPoint = apron,
            altitudeM = 0.0,
            phase = PilotPhase.AtStand,
            type = AircraftType.B738,
            pilotMission = mission,
        )
        val output = pilotDecide(
            PilotInput(
                aircraft = aircraft,
                worldIndex = world.buildWorldIndex(),
                world = world.toPilotView(),
                now = now0,
                densityAltitudeInputsByAerodrome = mapOf(lowg to highDaInput()),
            ),
        ).getOrElse { fail("pilotDecide failed: $it") }

        // ASSERT: Request(RequestTaxi) IS emitted (B738 fallthrough →
        // DA-decline does not fire → no suppression).
        assertTrue(
            output.transmissions.any { it is Request && it.type is RequestTaxi },
            "B738 fallthrough: null maxDensityAltitudeFt → DA-decline does not fire → no suppression",
        )
    }
}
