package xyz.easiersaid.twr.pilot

import arrow.core.Some
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.pilot.observe.PilotEvent
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * fn-14.1 (G3a-react R10) — `applyCrosswindGoAround` post-conditions.
 *
 * Distinct from `applySelfInitiatedGoAround` (DA path leaves
 * `phase = Climbing` and retains route) and `applyPlannedGoAround`
 * (trained-GA does NOT replace subtree). Third reactive shape:
 *  - reactive Tick A intent: `route = None`, `phase = Final` retained
 *  - subtree replacement: `TaskName.isCircuitLike()` → `CircuitAfterGoAround`
 *  - `mission.resetForGoAround(now)` called
 *  - transmits `Report(GoingAround)`
 *
 * **TouchAndGo variant** pins the `isCircuitLike` predicate: a
 * crosswind exceedance during a T&G approach must rewrite the active
 * T&G subtree the same way it rewrites Circuit. Precedent:
 * `handleGoAround` at `PilotCognitive.kt:986`.
 *
 * Doctrine: ICAO Doc 4444 §7.10.2 (missed-approach handling); FAA
 * AFH Ch 9; CAP 413 §4.67 / ICAO Doc 4444 §12.3.4.18 (pilot
 * standalone phraseology).
 */
class PilotCrosswindGoAroundTest {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO
    private val type = AircraftType.C172
    private val runway27 = RunwayId("27")

    private fun aircraft(): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = PointId("P"),
        altitudeM = 200.0,
        phase = PilotPhase.Final,
        type = type,
    )

    private fun event(): PilotEvent.CrosswindLimitExceeded =
        PilotEvent.CrosswindLimitExceeded(
            aircraft = ac,
            componentKnots = 20.0,
            limitKnots = 15,
            runway = runway27,
        )

    private fun missionWithCircuit(
        hasClearance: Boolean = true,
    ): PilotMission = PilotMission(
        goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        root = CompoundTask(
            name = TaskName.CircuitTraining,
            children = listOf(circuitTask()),
        ),
        stepEnteredAt = now0,
        hasClearance = hasClearance,
        activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
        altitudeRestrictionM = Some(80.0),
    )

    private fun missionWithTouchAndGo(): PilotMission = PilotMission(
        // CircuitTraining(TouchAndGo) — root wraps a TouchAndGo
        // compound that the crosswind GA must rewrite via the
        // isCircuitLike predicate.
        goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop)),
        root = CompoundTask(
            name = TaskName.CircuitTraining,
            children = listOf(
                CompoundTask(
                    name = TaskName.TouchAndGo,
                    children = listOf(
                        PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL),
                        PrimitiveTask(MissionStep.REPORT_FINAL, CompletionMode.REPORTED),
                        PrimitiveTask(MissionStep.AWAIT_LANDING_CLEARANCE, CompletionMode.INSTRUCTION_GATED),
                        PrimitiveTask(MissionStep.LAND, CompletionMode.PHYSICAL),
                    ),
                ),
            ),
        ),
        stepEnteredAt = now0,
        hasClearance = true,
        activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
    )

    /** Walk a TaskNode tree collecting every primitive's MissionStep. */
    private fun collectSteps(task: TaskNode): List<MissionStep> = when (task) {
        is PrimitiveTask -> listOf(task.step)
        is CompoundTask -> task.children.flatMap { collectSteps(it) }
    }

    /** Walk a TaskNode tree collecting every compound's TaskName. */
    private fun collectCompoundNames(task: TaskNode): List<TaskName> = when (task) {
        is PrimitiveTask -> emptyList()
        is CompoundTask -> listOf(task.name) + task.children.flatMap { collectCompoundNames(it) }
    }

    @Test
    fun `reactive Tick A intent — phase=Final retained, route=None, climb-speed, pattern-alt`() {
        val result = applyCrosswindGoAround(event(), missionWithCircuit(), aircraft(), now0)
        assertEquals(
            PilotPhase.Final,
            result.intent.phase,
            "reactive Tick A: phase=Final retained (NOT Climbing — distinguishes from DA path)",
        )
        assertTrue(
            result.intent.route is PilotRoute.None,
            "reactive Tick A: route=None invalidates kinematic route; got ${result.intent.route}",
        )
        assertEquals(
            type.kinematics.climbSpeedMps,
            result.intent.targetSpeedMps,
            1e-9,
            "Tick A targets climb speed (pilot is climbing out via GA path)",
        )
        assertEquals(
            type.circuitPattern.altitudeAglM,
            result.intent.targetAltitudeM,
            1e-9,
            "Tick A targets pattern altitude (climb-out target)",
        )
    }

    @Test
    fun `transmits Report(GoingAround) per ICAO Doc 4444 sec7dot10dot2`() {
        val result = applyCrosswindGoAround(event(), missionWithCircuit(), aircraft(), now0)
        assertEquals(
            listOf(Report(listOf(ReportEvent.GoingAround))),
            result.transmissions,
            "controller-side GA-POST-CLEAR fires on Report(GoingAround); transmission must be emitted",
        )
    }

    @Test
    fun `mission tree rewrites Circuit subtree to CircuitAfterGoAround`() {
        val result = applyCrosswindGoAround(event(), missionWithCircuit(), aircraft(), now0)
        val compounds = collectCompoundNames(result.mission.root)
        assertTrue(
            TaskName.CircuitAfterGoAround in compounds,
            "subtree replacement: a CircuitAfterGoAround compound is present; got $compounds",
        )
        val subtreeSteps = collectSteps(result.mission.root)
        assertTrue(
            MissionStep.GOING_AROUND in subtreeSteps,
            "VFR GA subtree contains GOING_AROUND step",
        )
    }

    @Test
    fun `resetForGoAround clears hasClearance + altitudeRestriction`() {
        val initial = missionWithCircuit(hasClearance = true)
        val result = applyCrosswindGoAround(event(), initial, aircraft(), now0)
        assertFalse(
            result.mission.hasClearance,
            "resetForGoAround clears hasClearance — pilot must request again on rejoined circuit",
        )
        assertEquals(
            null,
            result.mission.altitudeRestrictionM.getOrNull(),
            "resetForGoAround clears altitudeRestrictionM — approach-phase restrictions don't persist",
        )
    }

    @Test
    fun `TouchAndGo subtree is rewritten too — isCircuitLike predicate covers T&G`() {
        // Crosswind exceedance during a T&G approach must rewrite the
        // active T&G subtree the same way it rewrites a regular Circuit.
        // Verified at `PilotMission.kt:790`'s `isCircuitLike` predicate
        // which covers `Circuit`, `CircuitAfterGoAround`, AND `TouchAndGo`.
        val result = applyCrosswindGoAround(event(), missionWithTouchAndGo(), aircraft(), now0)
        val compounds = collectCompoundNames(result.mission.root)
        assertTrue(
            TaskName.CircuitAfterGoAround in compounds,
            "T&G crosswind GA replaces TouchAndGo subtree with CircuitAfterGoAround; got $compounds",
        )
        // The pre-rewrite TouchAndGo compound must be replaced — not
        // augmented.
        assertFalse(
            compounds.any { it is TaskName.TouchAndGo && (compounds.count { c -> c is TaskName.CircuitAfterGoAround } == 0) },
            "after rewrite, the TouchAndGo subtree has been replaced (not left alongside CircuitAfterGoAround)",
        )
        val subtreeSteps = collectSteps(result.mission.root)
        assertTrue(
            MissionStep.GOING_AROUND in subtreeSteps,
            "TouchAndGo rewrite subtree contains GOING_AROUND step",
        )
    }
}
