package xyz.easiersaid.twr.pilot.observe

import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.CompletionMode
import xyz.easiersaid.twr.pilot.CompoundTask
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.MissionStep
import xyz.easiersaid.twr.pilot.PilotMission
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.PrimitiveTask
import xyz.easiersaid.twr.pilot.TaskName
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pass 16 (D-AUDIT.9 partial closure) — recognition stage for
 * `PilotEvent.DecisionAltitudeWithoutClearance`.
 *
 * The existing `Pilot.checkSelfInitiatedGoAround` body was untested
 * (referenced only in source comments — G0 doesn't trigger it). Pass
 * 16 splits recognition from response, types the event, and pins
 * every guard branch with a covering row.
 *
 * Doctrine: CAP 413 §4.55 (continue approach vs go-around at decision
 * altitude — pilot's recognition predicate).
 *
 * 8 rows pin the guard-by-guard predicate:
 *  1. Happy FINAL — REPORT_FINAL @ 50m, no clearance → emits.
 *  2. Happy FLY_BASE — pins that inclusion-set extends beyond a
 *     single member.
 *  3. Above decision altitude → null.
 *  4. Boundary at 100m (closed inclusive) → emits.
 *  5. Has clearance → null.
 *  6. Wrong step (REPORT_DOWNWIND) → null.
 *  7. Phase guard (LandingRoll) → null.
 *  8. Re-fire prevention — both VFR (GOING_AROUND) and IFR
 *     (AWAITING_ATC_INSTRUCTION) → null.
 */
class PilotEventDerivationSpec {

    private val ac = AircraftId("OE-ABC")

    private fun aircraftAt(altitudeM: Double, phase: PilotPhase = PilotPhase.Final): AircraftState =
        AircraftState(
            id = ac,
            callsign = Callsign("OEABC"),
            position = Position(0.0, 0.0),
            positionPoint = PointId("P"),
            altitudeM = altitudeM,
            phase = phase,
        )

    private fun missionAtStep(
        step: MissionStep,
        hasClearance: Boolean = false,
    ): PilotMission = PilotMission(
        goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        root = CompoundTask(
            name = TaskName.Circuit,
            children = listOf(PrimitiveTask(step, CompletionMode.PHYSICAL)),
        ),
        stepEnteredAt = SimTime.ZERO,
        hasClearance = hasClearance,
    )

    @Test
    fun `aircraft at 50m on REPORT_FINAL with no clearance emits go-around event per CAP 413 sec4dot55`() {
        val event = derivePilotEvent(aircraftAt(50.0), missionAtStep(MissionStep.REPORT_FINAL), weather = null)
        assertEquals(
            PilotEvent.DecisionAltitudeWithoutClearance(
                aircraft = ac, altitudeM = 50.0, currentStep = MissionStep.REPORT_FINAL,
            ),
            event,
            "CAP 413 §4.55: at decision altitude without clearance, pilot self-initiates go-around",
        )
    }

    @Test
    fun `aircraft at 50m on FLY_BASE with no clearance emits event — inclusion set covers approach steps beyond FINAL`() {
        // Pinning the inclusion set extends to FLY_BASE; a regression
        // narrowing to only FINAL would silently drop go-arounds during
        // base-leg low-altitude scenarios.
        val event = derivePilotEvent(aircraftAt(50.0), missionAtStep(MissionStep.FLY_BASE), weather = null)
        assertEquals(
            MissionStep.FLY_BASE,
            (event as? PilotEvent.DecisionAltitudeWithoutClearance)?.currentStep,
            "CAP 413 §4.55: FLY_BASE is in the approach-step inclusion set",
        )
    }

    @Test
    fun `aircraft at 200m above decision altitude emits no event`() {
        assertNull(
            derivePilotEvent(aircraftAt(200.0), missionAtStep(MissionStep.REPORT_FINAL), weather = null),
            "CAP 413 §4.55: above decision altitude (100m) the pilot continues approach",
        )
    }

    @Test
    fun `aircraft at exactly 100m emits event — boundary inclusive`() {
        // Boundary case: a regression to half-open `< 100.0` would
        // silently drop edge triggers. Pin the closed-inclusive
        // specification of `aircraft.altitudeM <= DECISION_ALTITUDE_M`.
        assertEquals(
            100.0,
            (derivePilotEvent(aircraftAt(100.0), missionAtStep(MissionStep.REPORT_FINAL), weather = null)
                as? PilotEvent.DecisionAltitudeWithoutClearance)?.altitudeM,
            "altitude == DECISION_ALTITUDE_M (100m) is at the boundary; closed-inclusive specification fires",
        )
    }

    @Test
    fun `aircraft at 50m with landing clearance emits no event`() {
        assertNull(
            derivePilotEvent(aircraftAt(50.0), missionAtStep(MissionStep.REPORT_FINAL, hasClearance = true), weather = null),
            "CAP 413 §4.55: with clearance the pilot lands; go-around not triggered",
        )
    }

    @Test
    fun `aircraft at 50m on REPORT_DOWNWIND emits no event — wrong approach step`() {
        assertNull(
            derivePilotEvent(aircraftAt(50.0), missionAtStep(MissionStep.REPORT_DOWNWIND), weather = null),
            "REPORT_DOWNWIND is not in the approach-step inclusion set",
        )
    }

    @Test
    fun `aircraft on LandingRoll emits no event — phase guard`() {
        // After touchdown the aircraft is rolling; a regression that
        // dropped the phase guard would fire spurious go-arounds during
        // landing roll. The altitude+phase combination is contrived —
        // it pins the predicate, not a realistic flight path.
        assertNull(
            derivePilotEvent(
                aircraftAt(50.0, phase = PilotPhase.LandingRoll),
                missionAtStep(MissionStep.REPORT_FINAL),
                weather = null,
            ),
            "Phase guard: LandingRoll/Vacating excluded — no go-around after touchdown",
        )
    }

    @Test
    fun `re-fire prevention covers both GOING_AROUND (VFR) and AWAITING_ATC_INSTRUCTION (IFR)`() {
        // The transition has already entered — a second fire would
        // double-replace the subtree.
        assertNull(
            derivePilotEvent(aircraftAt(50.0), missionAtStep(MissionStep.GOING_AROUND), weather = null),
            "VFR re-fire prevention: GOING_AROUND step → already going around",
        )
        assertNull(
            derivePilotEvent(aircraftAt(50.0), missionAtStep(MissionStep.AWAITING_ATC_INSTRUCTION), weather = null),
            "IFR re-fire prevention: AWAITING_ATC_INSTRUCTION step → already on missed approach",
        )
    }
}
