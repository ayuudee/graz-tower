package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the pilot cognitive layer — pure functions on
 * (AircraftState, PilotMission, WorldIndex, SimTime).
 *
 * No sim engine needed. These test the HTN step advancement,
 * transmission generation, and instruction processing.
 */
class PilotCognitiveTest {

    private val t0 = SimTime.ofSeconds(0)
    private val t10 = SimTime.ofSeconds(10)

    // Minimal world index for cognitive tests — just circuit leg mappings.
    private val worldIndex = WorldIndex(
        positions = mapOf(
            PointId("DW") to Position(0.0, 300.0),
            PointId("BASE") to Position(-200.0, 200.0),
            PointId("FINAL") to Position(-200.0, 0.0),
            PointId("THR") to Position(0.0, 0.0),
        ),
        circuitLegsByPoint = mapOf(
            PointId("DW") to setOf(LegName.DOWNWIND),
            PointId("BASE") to setOf(LegName.BASE),
            PointId("FINAL") to setOf(LegName.FINAL),
            PointId("THR") to setOf(LegName.FINAL, LegName.UPWIND),
        ),
        thresholdByRunway = mapOf(RunwayId("09") to PointId("THR")),
    )

    private fun aircraft(point: String, phase: PilotPhase, alt: Double = 300.0) = AircraftState(
        id = AircraftId("TEST"), callsign = Callsign("TEST"),
        position = worldIndex.positions[PointId(point)]!!,
        positionPoint = PointId(point),
        altitudeM = alt, targetAltitudeM = alt,
        speedMps = 33.0, targetSpeedMps = 33.0,
        phase = phase, pilotGoal = PilotGoal.ARRIVE,
    )

    private fun arriveMission(startPhase: PilotPhase = PilotPhase.Final) =
        createMission(PilotGoal.ARRIVE, startPhase, t0)

    // ── Step advancement ─────────────────────────────────────────────

    @Test
    fun `REPORT_FINAL generates final report and advances`() {
        val ac = aircraft("FINAL", PilotPhase.Final)
        val mission = arriveMission(PilotPhase.Final)

        // First tick: should generate Report(Final).
        val decision1 = pilotCognitiveDecide(ac, mission, worldIndex, t0)
        val report = decision1.transmissions.filterIsInstance<Report>()
        assertTrue(report.isNotEmpty(), "Should generate a final report")
        assertTrue(report.any { it.events.contains(ReportEvent.Final) })

        // After updating lastReportedLeg, the step should complete on next tick.
        var updated = decision1.updatedMission
        for (tx in decision1.transmissions) { updated = updateAfterTransmission(updated, tx) }
        val decision2 = pilotCognitiveDecide(ac, updated, worldIndex, t10)
        assertEquals(MissionStep.AWAIT_LANDING_CLEARANCE, decision2.updatedMission.currentTask?.step,
            "Should advance past REPORT_FINAL to AWAIT_LANDING_CLEARANCE")
    }

    // ── Instruction processing ───────────────────────────────────────

    @Test
    fun `ClearedToLand batch-completes through AWAIT_LANDING_CLEARANCE`() {
        // Start at Final (past REPORT_DOWNWIND/AWAIT_SEQUENCING/FLY_BASE/REPORT_BASE/FLY_FINAL).
        val mission = arriveMission(PilotPhase.Final)
        // Simulate: report final already done.
        val withReport = mission.copy(
            root = mission.root.markComplete(MissionStep.REPORT_FINAL),
            lastReportedLeg = LegName.FINAL,
        )
        val result = processInstruction(ClearedToLand(AircraftId("TEST"), RunwayId("09")), withReport, t10)
        assertEquals(MissionStep.LAND, result.currentTask?.step,
            "ClearedToLand should batch-complete through AWAIT_LANDING_CLEARANCE to LAND")
    }

    @Test
    fun `ExtendDownwind adds constraint`() {
        val mission = arriveMission(PilotPhase.Downwind)
        val result = processInstruction(ExtendDownwind(AircraftId("TEST")), mission, t10)
        assertTrue(ActiveConstraint.ExtendingDownwind in result.activeConstraints)
    }

    @Test
    fun `TurnBase removes ExtendingDownwind constraint`() {
        val mission = arriveMission(PilotPhase.Downwind)
            .copy(activeConstraints = setOf(ActiveConstraint.ExtendingDownwind))
        val result = processInstruction(TurnBase(AircraftId("TEST")), mission, t10)
        assertTrue(ActiveConstraint.ExtendingDownwind !in result.activeConstraints)
    }

    @Test
    fun `GoAround replaces circuit subtree`() {
        val mission = arriveMission(PilotPhase.Final)
        val result = processInstruction(GoAround(AircraftId("TEST")), mission, t10)
        assertEquals(MissionStep.GOING_AROUND, result.currentTask?.step,
            "Go-around should transition to GOING_AROUND step")
    }

    @Test
    fun `processInstruction for unknown instruction returns mission unchanged`() {
        val mission = arriveMission(PilotPhase.Final)
        val result = processInstruction(HoldPosition(AircraftId("TEST")), mission, t10)
        assertEquals(mission.currentTask?.step, result.currentTask?.step)
    }

    // ── Transmission generation ──────────────────────────────────────

    @Test
    fun `AWAIT_LANDING_CLEARANCE generates no transmission initially`() {
        val ac = aircraft("FINAL", PilotPhase.Final)
        // Create mission already past REPORT_FINAL.
        var mission = arriveMission(PilotPhase.Final)
        mission = mission.copy(
            root = mission.root.markComplete(MissionStep.REPORT_FINAL),
            lastReportedLeg = LegName.FINAL,
        )
        val decision = pilotCognitiveDecide(ac, mission, worldIndex, t0)
        assertTrue(decision.transmissions.isEmpty(),
            "AWAIT_LANDING_CLEARANCE should not transmit immediately")
    }

    @Test
    fun `AWAIT_LANDING_CLEARANCE escalation fires after 15 seconds`() {
        val ac = aircraft("FINAL", PilotPhase.Final)
        var mission = arriveMission(PilotPhase.Final)
        mission = mission.copy(
            root = mission.root.markComplete(MissionStep.REPORT_FINAL),
            lastReportedLeg = LegName.FINAL,
            stepEnteredAt = t0,
        )
        // At t=16s, escalation should fire.
        val decision = pilotCognitiveDecide(ac, mission, worldIndex, SimTime.ofSeconds(16))
        assertTrue(decision.transmissions.isNotEmpty(), "Should fire escalation prompt after 15s")
    }

    // ── Mission decomposition ────────────────────────────────────────

    @Test
    fun `ARRIVE decomposition starts at correct step for Final phase`() {
        val mission = arriveMission(PilotPhase.Final)
        assertEquals(MissionStep.REPORT_FINAL, mission.currentTask?.step,
            "ARRIVE from Final should start at REPORT_FINAL")
    }

    @Test
    fun `ARRIVE decomposition starts at correct step for Downwind phase`() {
        val mission = arriveMission(PilotPhase.Downwind)
        assertEquals(MissionStep.REPORT_DOWNWIND, mission.currentTask?.step,
            "ARRIVE from Downwind should start at REPORT_DOWNWIND")
    }

    @Test
    fun `TOUCH_AND_GO decomposition starts at REQUEST_STARTUP for AtStand`() {
        val mission = createMission(PilotGoal.TOUCH_AND_GO, PilotPhase.AtStand, t0)
        assertEquals(MissionStep.REQUEST_STARTUP, mission.currentTask?.step)
    }

    @Test
    fun `completed mission has no current task`() {
        val mission = arriveMission(PilotPhase.Final)
        // Mark everything complete.
        var root = mission.root
        for (step in MissionStep.entries) { root = root.markComplete(step) }
        val completed = mission.copy(root = root)
        assertTrue(completed.isComplete)
        assertNull(completed.currentTask)
    }

    // ── HTN tree operations ──────────────────────────────────────────

    @Test
    fun `markComplete marks only the first incomplete instance`() {
        // Create a tree with FLY_DEPARTURE in two places (like go-around re-sequence).
        val tree = CompoundTask("ROOT", listOf(
            PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL, completed = true),
            CompoundTask("CIRCUIT", listOf(
                PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL), // incomplete
                PrimitiveTask(MissionStep.FLY_DOWNWIND, CompletionMode.PHYSICAL),
            )),
        ))
        val marked = tree.markComplete(MissionStep.FLY_DEPARTURE)
        // The first FLY_DEPARTURE is already complete. The second should now be complete.
        val circuit = marked.children[1] as CompoundTask
        val dep = circuit.children[0] as PrimitiveTask
        assertTrue(dep.completed, "Second FLY_DEPARTURE should be marked complete")
    }

    @Test
    fun `markComplete traverses past unchanged compound siblings`() {
        // This is the exact bug shape: ARRIVAL_JOIN (compound, all complete, structurally unchanged
        // after markComplete) followed by CIRCUIT (compound, containing the target step).
        // The old reference-identity check (!==) would set found=true after ARRIVAL_JOIN
        // because copy() creates a new object, and skip CIRCUIT entirely.
        val tree = CompoundTask("ARRIVE", listOf(
            CompoundTask("ARRIVAL_JOIN", listOf(
                PrimitiveTask(MissionStep.CALL_INBOUND, CompletionMode.REPORTED, completed = true),
                PrimitiveTask(MissionStep.AWAIT_JOINING_INSTRUCTIONS, CompletionMode.INSTRUCTION_GATED, completed = true),
            )),
            CompoundTask("CIRCUIT", listOf(
                PrimitiveTask(MissionStep.FLY_DOWNWIND, CompletionMode.PHYSICAL, completed = true),
                PrimitiveTask(MissionStep.REPORT_FINAL, CompletionMode.REPORTED), // target — incomplete
                PrimitiveTask(MissionStep.AWAIT_LANDING_CLEARANCE, CompletionMode.INSTRUCTION_GATED),
            )),
        ))
        val marked = tree.markComplete(MissionStep.REPORT_FINAL)
        val circuit = marked.children[1] as CompoundTask
        val reportFinal = circuit.children[1] as PrimitiveTask
        assertTrue(reportFinal.completed, "REPORT_FINAL in CIRCUIT should be marked despite ARRIVAL_JOIN being unchanged")
    }

    @Test
    fun `markComplete does not mark already-complete instances`() {
        val tree = CompoundTask("ROOT", listOf(
            PrimitiveTask(MissionStep.FLY_DOWNWIND, CompletionMode.PHYSICAL, completed = true),
            PrimitiveTask(MissionStep.REPORT_DOWNWIND, CompletionMode.REPORTED),
        ))
        val marked = tree.markComplete(MissionStep.FLY_DOWNWIND)
        // FLY_DOWNWIND was already complete, REPORT_DOWNWIND should still be incomplete.
        val report = marked.children[1] as PrimitiveTask
        assertTrue(!report.completed)
    }
}
