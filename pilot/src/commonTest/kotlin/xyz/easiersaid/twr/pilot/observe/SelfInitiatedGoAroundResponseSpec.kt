package xyz.easiersaid.twr.pilot.observe

import arrow.core.None
import arrow.core.Some
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CompletionMode
import xyz.easiersaid.twr.pilot.CompoundTask
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.MissionStep
import xyz.easiersaid.twr.pilot.NavigationMode
import xyz.easiersaid.twr.pilot.PilotMission
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.PrimitiveTask
import xyz.easiersaid.twr.pilot.TaskNode
import xyz.easiersaid.twr.pilot.TaskName
import xyz.easiersaid.twr.pilot.applySelfInitiatedGoAround
import xyz.easiersaid.twr.pilot.circuitTask
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearanceState
import xyz.easiersaid.twr.protocol.FlightPlan
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pass 16 (D-AUDIT.9 partial closure) — response stage for the typed
 * `PilotEvent.DecisionAltitudeWithoutClearance` event.
 * `applySelfInitiatedGoAround` is the renamed (and trigger-stripped)
 * former `checkSelfInitiatedGoAround` body.
 *
 * Doctrine: ICAO Doc 4444 §7.10.2 — the pilot's `Report(GoingAround)`
 * triggers the controller-side missed-approach handling.
 *
 * 3 rows pin the response contract:
 *  1. VFR: `goAroundTask()` + `circuitTask()` subtree, Climbing intent,
 *     `Report(GoingAround)` transmission.
 *  2. IFR: `ifrGoAroundTask()` subtree (FLY_MISSED_APPROACH +
 *     AWAITING_ATC_INSTRUCTION).
 *  3. Mission invariants reset: `hasClearance` and `altitudeRestrictionM`
 *     both cleared by `resetForGoAround`.
 */
class SelfInitiatedGoAroundResponseSpec {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO

    private fun aircraft(): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = PointId("P"),
        altitudeM = 50.0,
        phase = PilotPhase.Final,
    )

    private fun event(): PilotEvent.DecisionAltitudeWithoutClearance =
        PilotEvent.DecisionAltitudeWithoutClearance(
            aircraft = ac,
            altitudeM = 50.0,
            currentStep = MissionStep.REPORT_FINAL,
        )

    /**
     * Mission root must wrap a Circuit-named child for `replaceChild`'s
     * walk to find it. Use the production `circuitTask()` as the child
     * so the test exercises real subtree shapes.
     */
    private fun vfrMission(
        hasClearance: Boolean = false,
        altitudeRestrictionM: arrow.core.Option<Double> = None,
    ): PilotMission = PilotMission(
        goal = HighLevelGoal.CircuitTraining(circuits = 1, fullStopOnLast = true),
        root = CompoundTask(
            name = TaskName.CircuitTraining,
            children = listOf(circuitTask()),
        ),
        stepEnteredAt = now0,
        hasClearance = hasClearance,
        altitudeRestrictionM = altitudeRestrictionM,
    )

    private fun ifrMission(): PilotMission {
        val fpl = FlightPlan(
            departureAerodrome = AerodromeId("LOWG"),
            arrivalAerodrome = AerodromeId("LJMB"),
            requestedLevel = Level.AltitudeFeet.unsafe(8000),
            enRouteWaypoints = emptyList(),
            clearance = ClearanceState.Uncleaned,
        )
        return PilotMission(
            goal = HighLevelGoal.Arrival(),
            root = CompoundTask(
                name = TaskName.Arrive,
                children = listOf(circuitTask()),
            ),
            stepEnteredAt = now0,
            navigationMode = Some(NavigationMode.Instrument(fpl)),
        )
    }

    /** Walk a TaskNode tree collecting every primitive's MissionStep. */
    private fun collectSteps(task: TaskNode): List<MissionStep> = when (task) {
        is PrimitiveTask -> listOf(task.step)
        is CompoundTask -> task.children.flatMap { collectSteps(it) }
    }

    @Test
    fun `VFR self-go-around produces Climbing intent + GoingAround transmission + CircuitAfterGoAround subtree per Doc 4444 sec7dot10dot2`() {
        val result = applySelfInitiatedGoAround(event(), vfrMission(), aircraft(), now0)
        assertEquals(PilotPhase.Climbing, result.intent.phase, "Doc 4444 §7.10.2: pilot climbs on go-around")
        assertEquals(
            listOf(Report(listOf(ReportEvent.GoingAround))),
            result.transmissions,
            "Doc 4444 §7.10.2: pilot transmits Report(GoingAround) to trigger controller-side missed-approach",
        )
        // Subtree shape: a CircuitAfterGoAround compound containing
        // VFR goAroundTask (GOING_AROUND step) + circuitTask (no
        // FLY_MISSED_APPROACH or AWAITING_ATC_INSTRUCTION).
        val subtreeSteps = collectSteps(result.mission.root)
        assertTrue(
            MissionStep.GOING_AROUND in subtreeSteps,
            "VFR go-around subtree must include GOING_AROUND step",
        )
        assertTrue(
            MissionStep.FLY_MISSED_APPROACH !in subtreeSteps,
            "VFR go-around must NOT use IFR's FLY_MISSED_APPROACH",
        )
    }

    @Test
    fun `IFR self-go-around produces ifrGoAroundTask subtree (FLY_MISSED_APPROACH + AWAITING_ATC_INSTRUCTION)`() {
        val result = applySelfInitiatedGoAround(event(), ifrMission(), aircraft(), now0)
        val subtreeSteps = collectSteps(result.mission.root)
        assertTrue(
            MissionStep.FLY_MISSED_APPROACH in subtreeSteps,
            "IFR go-around subtree must include FLY_MISSED_APPROACH per ICAO Doc 4444 §7.10.2",
        )
        assertTrue(
            MissionStep.AWAITING_ATC_INSTRUCTION in subtreeSteps,
            "IFR go-around must wait for ATC missed-approach instruction (AWAITING_ATC_INSTRUCTION)",
        )
    }

    @Test
    fun `mission invariants reset — hasClearance cleared and altitudeRestrictionM cleared by resetForGoAround`() {
        val initialMission = vfrMission(
            hasClearance = true,
            altitudeRestrictionM = Some(80.0),
        )
        val result = applySelfInitiatedGoAround(event(), initialMission, aircraft(), now0)
        assertEquals(
            false,
            result.mission.hasClearance,
            "resetForGoAround clears hasClearance — pilot must request clearance again on rejoined circuit",
        )
        assertNull(
            result.mission.altitudeRestrictionM.getOrNull(),
            "resetForGoAround clears altitudeRestrictionM — prior StopClimbAt does not persist across go-around",
        )
    }
}
