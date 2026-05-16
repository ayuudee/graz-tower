package xyz.easiersaid.twr.pilot.observe

import arrow.core.Some
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CompletionMode
import xyz.easiersaid.twr.pilot.CompoundTask
import xyz.easiersaid.twr.pilot.DensityAltitudeInput
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.MissionStep
import xyz.easiersaid.twr.pilot.PilotMission
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.PrimitiveTask
import xyz.easiersaid.twr.pilot.TaskName
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.Feet
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Temperature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * fn-28.2 (G3a-react-density-altitude): `deriveDensityAltitudeEvent`
 * recognition contract.
 *
 * **R16 recognition+apply agreement**: the derivation gates on
 * `isDensityAltitudeDeclineEligible` (pre-taxi mission shape); the apply
 * stage `applyDensityAltitudeDecline` uses the same predicate. The tests
 * below pin the derivation behaviour for the common cases — the
 * agreement contract is enforced by re-using the named guard in both
 * sites.
 *
 * **R21 branch order**: tested at the function-composition level — when
 * multiple branches' predicates hold simultaneously, the documented order
 * wins. The DA-decline branch's pre-taxi gate is structurally disjoint
 * from the on-final gates of DA-without-clearance / wind branches, so
 * co-occurrence is impossible by construction.
 */
class PilotEventDensityAltitudeTest {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO
    private val rwy = RunwayId("27")

    // LOWG ISA+35°C scenario from the fn-28.3 sim golden — DA ≈ 5323 ft
    // (> 5000 ft C172 threshold). See DensityAltitudeFormulaTest for
    // boundary numerics.
    private val daHighLowg = DensityAltitudeInput(
        oat = Temperature.celsius(47.79),
        qnh = PressureSetting.QnhHpa.unsafe(1013),
        fieldElevation = Feet.unsafe(1115),
    )

    // ISA at LOWG — DA ≈ 1123 ft (< 5000 ft threshold). Negative-row
    // benchmark.
    private val daLowLowg = DensityAltitudeInput(
        oat = Temperature.celsius(12.79),
        qnh = PressureSetting.QnhHpa.unsafe(1013),
        fieldElevation = Feet.unsafe(1115),
    )

    private fun aircraft(type: AircraftType = AircraftType.C172): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = PointId("APRON"),
        altitudeM = 0.0,
        phase = PilotPhase.AtStand,
        type = type,
    )

    private fun missionWithStep(step: MissionStep): PilotMission = PilotMission(
        goal = HighLevelGoal.Departure(),
        root = CompoundTask(
            name = TaskName.Depart,
            children = listOf(PrimitiveTask(step, CompletionMode.INSTRUCTION_GATED)),
        ),
        stepEnteredAt = now0,
        activeRunway = Some(RunwayAssignment(rwy, RunwayAssignmentSource.Filing)),
    )

    @Test
    fun `fires when C172 + REQUEST_TAXI + DA exceeds 5000 ft — golden positive row`() {
        val event = derivePilotEvent(
            aircraft = aircraft(type = AircraftType.C172),
            mission = missionWithStep(MissionStep.REQUEST_TAXI),
            weather = null,
            densityAltitudeInput = daHighLowg,
        )
        assertTrue(
            event is PilotEvent.DensityAltitudeDecline,
            "C172 + pre-taxi + DA > threshold MUST fire DensityAltitudeDecline; got $event",
        )
        assertTrue(
            event.computedDaFeet > 5000,
            "fired event carries the computed DA (> threshold by construction); got ${event.computedDaFeet}",
        )
        assertEquals(
            5000,
            event.limitFeet,
            "fired event carries the C172 threshold for trace readability",
        )
    }

    @Test
    fun `fires on TAXI_TO_HOLDING — pilot may re-compute DA during taxi`() {
        val event = derivePilotEvent(
            aircraft = aircraft(),
            mission = missionWithStep(MissionStep.TAXI_TO_HOLDING),
            weather = null,
            densityAltitudeInput = daHighLowg,
        )
        assertTrue(
            event is PilotEvent.DensityAltitudeDecline,
            "TAXI_TO_HOLDING is eligible per R16; got $event",
        )
    }

    @Test
    fun `does NOT fire when DA at or below threshold — strict inequality`() {
        val event = derivePilotEvent(
            aircraft = aircraft(),
            mission = missionWithStep(MissionStep.REQUEST_TAXI),
            weather = null,
            densityAltitudeInput = daLowLowg,
        )
        assertNull(
            event,
            "DA ≈ 1123 ft does NOT exceed C172's 5000 ft threshold; no event",
        )
    }

    @Test
    fun `does NOT fire when densityAltitudeInput is null — fail-closed projection`() {
        val event = derivePilotEvent(
            aircraft = aircraft(),
            mission = missionWithStep(MissionStep.REQUEST_TAXI),
            weather = null,
            densityAltitudeInput = null,
        )
        assertNull(
            event,
            "null densityAltitudeInput (PilotWiring fail-closed projection) → no event",
        )
    }

    @Test
    fun `B738 fallthrough — null maxDensityAltitudeFt means trigger never fires (round-5 Major 2)`() {
        // CRITICAL: the nullable applicability semantic. Even with a
        // high-DA input, B738 has `maxDensityAltitudeFt = null` so the
        // elvis-default fail-closes; no DensityAltitudeDecline event.
        val event = derivePilotEvent(
            aircraft = aircraft(type = AircraftType.B738),
            mission = missionWithStep(MissionStep.REQUEST_TAXI),
            weather = null,
            densityAltitudeInput = daHighLowg,
        )
        assertNull(
            event,
            "B738 has null maxDensityAltitudeFt (jet-class fallthrough); DA branch must NOT fire",
        )
    }

    @Test
    fun `does NOT fire on airborne steps — mission-shape guard rejects`() {
        listOf(
            MissionStep.FLY_DEPARTURE,
            MissionStep.FLY_DOWNWIND,
            MissionStep.FLY_FINAL,
            MissionStep.AWAIT_LANDING_CLEARANCE,
        ).forEach { step ->
            val event = derivePilotEvent(
                aircraft = aircraft(),
                mission = missionWithStep(step),
                weather = null,
                densityAltitudeInput = daHighLowg,
            )
            assertNull(
                event,
                "airborne step $step is not pre-taxi-eligible per R16; no event",
            )
        }
    }

    @Test
    fun `does NOT fire on post-taxi pre-airborne steps — RUN_UP_CHECKS et al`() {
        // R16 split: post-taxi states fall under abort eligibility (fn-28.9),
        // not DA decline. Pin the disjoint contract.
        listOf(
            MissionStep.RUN_UP_CHECKS,
            MissionStep.REPORT_READY,
            MissionStep.AWAIT_LINE_UP,
            MissionStep.AWAIT_TAKEOFF_CLEARANCE,
        ).forEach { step ->
            val event = derivePilotEvent(
                aircraft = aircraft(),
                mission = missionWithStep(step),
                weather = null,
                densityAltitudeInput = daHighLowg,
            )
            assertNull(
                event,
                "post-taxi step $step is NOT pre-taxi-eligible; DA decline must not fire (abort path covers this regime)",
            )
        }
    }
}
