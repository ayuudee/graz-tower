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
 * fn-28.2 (G3a-react-density-altitude R13 + R14 + R20):
 * `applyDensityAltitudeDecline` post-conditions.
 *
 * Sibling of fn-14.1's `PilotCrosswindGoAroundTest` / fn-15.1's
 * `PilotTailwindGoAroundTest`, but the response shape is distinct:
 *  - apron-side TERMINAL decision (NOT a go-around).
 *  - mission tree rewritten via `replaceFromActivePrimitive` (R13 sole
 *    rewrite primitive), NOT `replaceChild`.
 *  - intent: `targetSpeedMps = 0` (at-rest on the apron), NOT the
 *    `climbSpeedMps` of the GA path; `phase = AtStand`.
 *  - `suppressSameTickCognitive = true` flag (R14 / round-3 fix carried).
 *  - no `transmissions` slot (v1).
 *
 * **R20 NON_COMPLETING contract**: the rewritten primitive carries
 * `(DECLINE_DEPARTURE, NON_COMPLETING)`. The pin: a downstream tick that
 * re-runs `isStepComplete` on the new primitive returns `false` (the
 * primitive never advances).
 */
class PilotDensityAltitudeDeclineTest {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO
    private val type = AircraftType.C172
    private val runway27 = RunwayId("27")

    private fun aircraft(phase: PilotPhase = PilotPhase.AtStand): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = PointId("APRON"),
        altitudeM = 0.0,
        phase = phase,
        type = type,
    )

    private fun event(): PilotEvent.DensityAltitudeDecline = PilotEvent.DensityAltitudeDecline(
        aircraft = ac,
        computedDaFeet = 5323,
        limitFeet = 5000,
    )

    private fun departureMission(step: MissionStep = MissionStep.REQUEST_TAXI): PilotMission = PilotMission(
        goal = HighLevelGoal.Departure(),
        root = CompoundTask(
            name = TaskName.Depart,
            children = listOf(
                groundDepartureTask().let { gd ->
                    // Walk gd.children, set every primitive before `step` as
                    // completed so `step` is the active leaf.
                    val targetIdx = gd.children.indexOfFirst {
                        it is PrimitiveTask && it.step == step
                    }
                    require(targetIdx >= 0) { "Mission helper: step $step not in groundDepartureTask" }
                    gd.copy(
                        children = gd.children.mapIndexed { idx, child ->
                            when {
                                child is PrimitiveTask && idx < targetIdx -> child.copy(completed = true)
                                else -> child
                            }
                        },
                    )
                },
                PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
                PrimitiveTask(MissionStep.SHUTDOWN, CompletionMode.INSTANT),
            ),
        ),
        stepEnteredAt = now0,
        activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
    )

    private fun collectSteps(task: TaskNode): List<MissionStep> = when (task) {
        is PrimitiveTask -> listOf(task.step)
        is CompoundTask -> task.children.flatMap { collectSteps(it) }
    }

    @Test
    fun `apron-terminal intent — targetSpeedMps=0, phase=AtStand, route=None, altitude=0`() {
        val result = applyDensityAltitudeDecline(event(), departureMission(), aircraft())
        assertEquals(
            0.0,
            result.intent.targetSpeedMps,
            1e-9,
            "apron-terminal: pilot decides NOT to taxi; physics at-rest",
        )
        assertEquals(
            PilotPhase.AtStand,
            result.intent.phase,
            "apron-terminal: phase=AtStand reflects the apron-static reality",
        )
        assertTrue(
            result.intent.route is PilotRoute.None,
            "apron-terminal: no airborne or ground route; got ${result.intent.route}",
        )
        assertEquals(
            0.0,
            result.intent.targetAltitudeM,
            1e-9,
            "apron-terminal: surface elevation; no climb intent",
        )
    }

    @Test
    fun `cognitive-suppression flag is set — R14 round-13 Major 1 contract`() {
        val result = applyDensityAltitudeDecline(event(), departureMission(), aircraft())
        assertTrue(
            result.suppressSameTickCognitive,
            "R14: DA decline returns suppressSameTickCognitive = true; pilotDecide " +
                "zeroes same-tick cognitive transmissions on every PilotOutput return path",
        )
    }

    @Test
    fun `mission tree replaced via replaceFromActivePrimitive — terminal DECLINE_DEPARTURE primitive`() {
        val result = applyDensityAltitudeDecline(event(), departureMission(), aircraft())
        val steps = collectSteps(result.mission.root)
        assertTrue(
            MissionStep.DECLINE_DEPARTURE in steps,
            "mission tree contains DECLINE_DEPARTURE primitive after rewrite; got $steps",
        )
        // The active primitive (REQUEST_TAXI) was at the start of
        // groundDepartureTask; replaceFromActivePrimitive drops it + every
        // following sibling (TAXI_TO_HOLDING / RUN_UP_CHECKS / etc.) inside
        // the GroundDeparture compound, AND every later top-level sibling
        // (FLY_DEPARTURE / SHUTDOWN) — that's R13's "leave outer parents
        // intact" contract: the rewrite happens at the INNER compound's
        // level (GroundDeparture), and outer-level later siblings are
        // preserved.
        //
        // Actually wait: per R13 contract for fn-28's call sites, DA-decline
        // SHOULD leave outer-level siblings (FLY_DEPARTURE, SHUTDOWN) in
        // place — those are the rest of the Departure plan that DA-decline
        // doesn't preclude structurally, but operationally `currentTask`
        // will stop at the NON_COMPLETING DECLINE_DEPARTURE forever. Pin:
        assertTrue(
            MissionStep.FLY_DEPARTURE in steps,
            "outer-level FLY_DEPARTURE sibling preserved (R13 'leave outer parents intact')",
        )
    }

    @Test
    fun `DECLINE_DEPARTURE primitive carries NON_COMPLETING completion mode — R20 invariant`() {
        val result = applyDensityAltitudeDecline(event(), departureMission(), aircraft())
        // Walk the tree to find the DECLINE_DEPARTURE primitive and verify
        // its completion mode.
        fun findDecline(task: TaskNode): PrimitiveTask? = when (task) {
            is PrimitiveTask -> if (task.step == MissionStep.DECLINE_DEPARTURE) task else null
            is CompoundTask -> task.children.firstNotNullOfOrNull { findDecline(it) }
        }
        val declinePrimitive = findDecline(result.mission.root)
        assertTrue(
            declinePrimitive != null,
            "DECLINE_DEPARTURE primitive must be present in the rewritten tree",
        )
        assertEquals(
            CompletionMode.NON_COMPLETING,
            declinePrimitive.completionMode,
            "R20: DECLINE_DEPARTURE primitives MUST pair with NON_COMPLETING",
        )
        assertFalse(
            declinePrimitive.completed,
            "DECLINE_DEPARTURE primitive starts uncompleted (NON_COMPLETING means it stays so)",
        )
    }

    @Test
    fun `currentTask after rewrite is DECLINE_DEPARTURE — pilot is at terminal state`() {
        val result = applyDensityAltitudeDecline(event(), departureMission(), aircraft())
        assertEquals(
            MissionStep.DECLINE_DEPARTURE,
            result.mission.currentTask?.step,
            "after rewrite, pilot's active primitive is DECLINE_DEPARTURE (terminal apron state)",
        )
    }

    @Test
    fun `applies to TAXI_TO_HOLDING active step too — pilot re-computes DA during taxi`() {
        val result = applyDensityAltitudeDecline(
            event(),
            departureMission(step = MissionStep.TAXI_TO_HOLDING),
            aircraft(phase = PilotPhase.Taxiing),
        )
        assertEquals(
            MissionStep.DECLINE_DEPARTURE,
            result.mission.currentTask?.step,
            "TAXI_TO_HOLDING is eligible per R16; rewrite proceeds + active step becomes DECLINE_DEPARTURE",
        )
    }

    @Test
    fun `defensive fail-closed when mission no longer eligible — returns input mission`() {
        // Recognition+apply agreement defensive guard: if a caller invokes
        // the apply with a mission whose active step is NOT pre-taxi-
        // eligible (a recognition+apply divergence regression), return the
        // input mission unchanged + an at-rest intent. The recognition
        // gate at `derivePilotEvent` should already have rejected such a
        // mission; this is belt-and-braces.
        val ineligibleMission = PilotMission(
            goal = HighLevelGoal.Departure(),
            root = CompoundTask(
                name = TaskName.Depart,
                children = listOf(
                    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
                ),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
        )
        val result = applyDensityAltitudeDecline(event(), ineligibleMission, aircraft())
        assertSame(
            ineligibleMission,
            result.mission,
            "ineligible mission: defensive guard returns input unchanged (no rewrite)",
        )
        // Suppression flag still true (the trigger source was still a DA
        // decline event — same tick suppression of cognitive transmissions
        // is the conservative choice).
        assertTrue(
            result.suppressSameTickCognitive,
            "defensive fail-closed: still suppress same-tick cognitive (no harm; consistent contract)",
        )
    }
}
