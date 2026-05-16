package xyz.easiersaid.twr.pilot

import arrow.core.Some
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.pilot.observe.PilotEvent
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * fn-28.9 (G0 abort-takeoff R13 + R14 + R20): `applyAbortTakeoff`
 * post-conditions.
 *
 * Sibling of fn-28.2's `PilotDensityAltitudeDeclineTest` — the response
 * shape mirrors DA decline but with abort-specific surfaces:
 *  - runway-side TERMINAL decision (NOT a go-around).
 *  - mission tree rewritten via `replaceFromActivePrimitive` (R13 sole
 *    rewrite primitive), NOT `replaceChild`.
 *  - intent: `targetSpeedMps = 0` (at-rest on the runway, combined with
 *    R12 engine-off clamp from .8 to produce instant-stop).
 *  - `suppressSameTickCognitive = true` flag (R14 / round-15 Major 2
 *    contract — covers ALL pilotDecide return paths).
 *  - no `transmissions` slot (v1 — no Mayday / PanPan emitted at fn-28).
 *
 * **R20 NON_COMPLETING contract**: the rewritten primitive carries
 * `(ABORTED, NON_COMPLETING)`. A downstream tick that re-runs
 * `isStepComplete` returns `false` (the primitive never advances).
 */
class PilotAbortTakeoffTest {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO
    private val type = AircraftType.C172
    private val runway16c = RunwayId("16C")

    private fun aircraft(
        phase: PilotPhase = PilotPhase.TakeoffRoll,
        speedMps: Double = 15.0,
        engineRunning: Boolean = false,
    ): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = PointId("LOWG_RWY_16C_THR"),
        altitudeM = 0.0,
        phase = phase,
        type = type,
        speedMps = speedMps,
        engineRunning = engineRunning,
    )

    private fun event(speedAtFailure: Double = 15.0): PilotEvent.AbortTakeoff =
        PilotEvent.AbortTakeoff(aircraft = ac, speedAtFailure = speedAtFailure)

    private fun departureMission(
        activeStep: MissionStep = MissionStep.FLY_DEPARTURE,
    ): PilotMission {
        // Build a flat Departure root with groundDepartureTask as compound +
        // FLY_DEPARTURE / SHUTDOWN siblings. Mark every step before
        // `activeStep` as completed so the active leaf is `activeStep`.
        val gd = groundDepartureTask()
        val gdCompleted = gd.copy(
            children = gd.children.map { child ->
                when (child) {
                    is PrimitiveTask -> child.copy(completed = true)
                    is CompoundTask -> child
                }
            },
        )
        val root = CompoundTask(
            name = TaskName.Depart,
            children = when (activeStep) {
                MissionStep.AWAIT_TAKEOFF_CLEARANCE -> {
                    // Active leaf inside groundDepartureTask — mark all
                    // children before AWAIT_TAKEOFF_CLEARANCE as completed
                    // and leave it active.
                    val gdWithActive = gd.copy(
                        children = gd.children.map { child ->
                            when {
                                child is PrimitiveTask && child.step == activeStep -> child
                                child is PrimitiveTask -> {
                                    val priorTo = listOf(
                                        MissionStep.REQUEST_TAXI,
                                        MissionStep.TAXI_TO_HOLDING,
                                        MissionStep.RUN_UP_CHECKS,
                                        MissionStep.REPORT_READY,
                                        MissionStep.AWAIT_LINE_UP,
                                    )
                                    if (child.step in priorTo) child.copy(completed = true) else child
                                }
                                else -> child
                            }
                        },
                    )
                    listOf(
                        gdWithActive,
                        PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
                        PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
                    )
                }
                MissionStep.FLY_DEPARTURE -> listOf(
                    gdCompleted,
                    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
                    PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
                )
                else -> error("departureMission helper only supports AWAIT_TAKEOFF_CLEARANCE / FLY_DEPARTURE; got $activeStep")
            },
        )
        return PilotMission(
            goal = HighLevelGoal.Departure(),
            root = root,
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(runway16c, RunwayAssignmentSource.Radio.LineUp)),
        )
    }

    private fun collectSteps(task: TaskNode): List<MissionStep> = when (task) {
        is PrimitiveTask -> listOf(task.step)
        is CompoundTask -> task.children.flatMap { collectSteps(it) }
    }

    @Test
    fun `runway-terminal intent — targetSpeedMps=0, phase preserved, route=None, altitude=0`() {
        val result = applyAbortTakeoff(event(), departureMission(), aircraft())
        assertEquals(
            0.0,
            result.intent.targetSpeedMps,
            1e-9,
            "runway-terminal: pilot decides NOT to continue takeoff; targetSpeedMps=0 + " +
                "R12 engine-off clamp produces instant stop on the same physics tick",
        )
        assertEquals(
            PilotPhase.TakeoffRoll,
            result.intent.phase,
            "phase preserved at TakeoffRoll — physics decelerates naturally; v1 does not " +
                "introduce a 'Stopped' phase. Mission tree's NON_COMPLETING ABORTED is the " +
                "load-bearing terminal signal, not the phase.",
        )
        assertTrue(
            result.intent.route is PilotRoute.None,
            "runway-terminal: no airborne or ground route; got ${result.intent.route}",
        )
        assertEquals(
            0.0,
            result.intent.targetAltitudeM,
            1e-9,
            "runway-terminal: surface elevation; no climb intent",
        )
    }

    @Test
    fun `cognitive-suppression flag is set — R14 round-15 Major 2 contract`() {
        val result = applyAbortTakeoff(event(), departureMission(), aircraft())
        assertTrue(
            result.suppressSameTickCognitive,
            "R14: abort returns suppressSameTickCognitive = true; pilotDecide zeroes " +
                "same-tick cognitive transmissions on every PilotOutput return path",
        )
    }

    @Test
    fun `mission tree replaced via replaceFromActivePrimitive — terminal ABORTED primitive`() {
        val result = applyAbortTakeoff(event(), departureMission(), aircraft())
        val steps = collectSteps(result.mission.root)
        assertTrue(
            MissionStep.ABORTED in steps,
            "mission tree contains ABORTED primitive after rewrite; got $steps",
        )
        // FLY_DEPARTURE was the active primitive at the root level (outer
        // sibling of the GroundDeparture compound). `replaceFromActivePrimitive`
        // drops it + every following same-level sibling (SHUTDOWN); the
        // GroundDeparture compound (already complete) is preserved as a
        // left-side sibling.
        assertFalse(
            MissionStep.FLY_DEPARTURE in steps,
            "FLY_DEPARTURE active primitive was dropped by replaceFromActivePrimitive; got $steps",
        )
        assertFalse(
            MissionStep.SHUTDOWN in steps,
            "SHUTDOWN was a later same-level sibling; dropped by suffix-replace; got $steps",
        )
    }

    @Test
    fun `ABORTED primitive carries NON_COMPLETING completion mode — R20 invariant`() {
        val result = applyAbortTakeoff(event(), departureMission(), aircraft())
        fun findAborted(task: TaskNode): PrimitiveTask? = when (task) {
            is PrimitiveTask -> if (task.step == MissionStep.ABORTED) task else null
            is CompoundTask -> task.children.firstNotNullOfOrNull { findAborted(it) }
        }
        val abortedPrimitive = findAborted(result.mission.root)
        assertTrue(
            abortedPrimitive != null,
            "ABORTED primitive must be present in the rewritten tree",
        )
        assertEquals(
            CompletionMode.NON_COMPLETING,
            abortedPrimitive.completionMode,
            "R20: ABORTED primitives MUST pair with NON_COMPLETING",
        )
        assertFalse(
            abortedPrimitive.completed,
            "ABORTED primitive starts uncompleted (NON_COMPLETING means it stays so)",
        )
    }

    @Test
    fun `currentTask after rewrite is ABORTED — pilot is at terminal state`() {
        val result = applyAbortTakeoff(event(), departureMission(), aircraft())
        assertEquals(
            MissionStep.ABORTED,
            result.mission.currentTask?.step,
            "after rewrite, pilot's active primitive is ABORTED (terminal runway state)",
        )
    }

    @Test
    fun `applies to AWAIT_TAKEOFF_CLEARANCE active step too — abort while waiting at holding point`() {
        val result = applyAbortTakeoff(
            event(speedAtFailure = 0.0),
            departureMission(activeStep = MissionStep.AWAIT_TAKEOFF_CLEARANCE),
            aircraft(speedMps = 0.0),
        )
        assertEquals(
            MissionStep.ABORTED,
            result.mission.currentTask?.step,
            "AWAIT_TAKEOFF_CLEARANCE is eligible per R16; rewrite proceeds + active step becomes ABORTED",
        )
    }

    @Test
    fun `defensive fail-closed when mission no longer eligible — returns input mission`() {
        // R16 recognition+apply agreement defensive guard. If a caller
        // invokes the apply with a mission whose active step is NOT
        // takeoff-roll-eligible (a recognition+apply divergence regression),
        // return the input mission unchanged + an at-rest intent.
        val ineligibleMission = PilotMission(
            goal = HighLevelGoal.Departure(),
            root = CompoundTask(
                name = TaskName.Depart,
                children = listOf(
                    PrimitiveTask(MissionStep.LAND, CompletionMode.PHYSICAL),
                ),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(runway16c, RunwayAssignmentSource.Filing)),
        )
        val result = applyAbortTakeoff(event(), ineligibleMission, aircraft())
        assertSame(
            ineligibleMission,
            result.mission,
            "ineligible mission: defensive guard returns input unchanged (no rewrite)",
        )
        assertTrue(
            result.suppressSameTickCognitive,
            "defensive fail-closed: still suppress same-tick cognitive (consistent contract)",
        )
        // Intent is at-rest with current phase preserved (NOT a no-op);
        // the apply still asserts pilot intent to STOP even if the
        // mission rewrite couldn't proceed.
        assertEquals(0.0, result.intent.targetSpeedMps, 1e-9)
    }
}
