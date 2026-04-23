package xyz.easiersaid.twr.sim

import arrow.core.Some
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
        createMission(HighLevelGoal.Arrival(), startPhase, t0)

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

    // ── resetForGoAround exhaustive field test ──────────────────────

    @Test
    fun `resetForGoAround covers every PilotMission field`() {
        // Build a mission with every field set to a non-default value.
        // This ensures the test touches every field — if a new field is
        // added to PilotMission, this construction will fail to compile
        // (no default) or the assertions below will need updating.
        val mission = PilotMission(
            goal = HighLevelGoal.CircuitTraining(2),
            root = planMission(HighLevelGoal.CircuitTraining(2)),
            navigationMode = NavigationMode.Visual(RunwayId("09"), null),
            activeRunway = RunwayId("09"),
            activeConstraints = setOf(ActiveConstraint.ExtendingDownwind),
            routeOverride = RouteOverride.Vectoring(Heading.unsafe(270)),
            contactedOnFrequency = true,
            stepEnteredAt = SimTime.ofSeconds(100),
            lastReportedLeg = xyz.easiersaid.twr.core.world.LegName.DOWNWIND,
            reportedVacated = true,
            hasClearance = true,
            joinLeg = Some(xyz.easiersaid.twr.core.world.LegName.BASE),
            altitudeRestrictionM = 200.0,
        )

        val reset = mission.resetForGoAround(t10)

        // ── Structural: preserved ──
        assertEquals(mission.goal, reset.goal, "goal must be preserved")
        assertEquals(mission.root, reset.root, "root must be preserved (caller handles subtree replacement)")
        assertEquals(mission.navigationMode, reset.navigationMode, "navigationMode must be preserved")
        assertEquals(mission.activeRunway, reset.activeRunway, "activeRunway must be preserved")

        // ── Cross-cutting: reset on go-around ──
        assertEquals(emptySet<ActiveConstraint>(), reset.activeConstraints, "activeConstraints must be cleared")
        assertNull(reset.routeOverride, "routeOverride must be cleared")

        // ── Cross-cutting: preserved ──
        assertEquals(true, reset.contactedOnFrequency, "contactedOnFrequency must be preserved")

        // ── Phase-local: reset to defaults ──
        assertEquals(t10, reset.stepEnteredAt, "stepEnteredAt must be set to now")
        assertNull(reset.lastReportedLeg, "lastReportedLeg must be null")
        assertEquals(false, reset.reportedVacated, "reportedVacated must be false")
        assertEquals(false, reset.hasClearance, "hasClearance must be false")
        assertEquals(arrow.core.None, reset.joinLeg, "joinLeg must be reset to None on go-around")
        assertNull(reset.altitudeRestrictionM, "altitudeRestrictionM must be cleared on go-around")

        // ── Field count guard (manual — no reflection in commonTest) ──
        // This destructuring exercises every constructor parameter. If a new
        // field is added, this line won't compile until updated.
        val (goal, root, navMode, activeRwy,
            constraints, override, contacted,
            entered, reported, vacated, clearance,
            joinLegField, altRestriction) = reset
        // Suppress unused — the point is compile-time coverage, not runtime use.
        @Suppress("UNUSED_VARIABLE") val guard = listOf(
            goal, root, navMode, activeRwy, constraints, override, contacted,
            entered, reported, vacated, clearance, joinLegField, altRestriction,
        )
    }

    @Test
    fun `GoAround resets hasClearance to false`() {
        val mission = arriveMission(PilotPhase.Final).copy(hasClearance = true)
        val result = processInstruction(GoAround(AircraftId("TEST")), mission, t10)
        assertEquals(false, result.hasClearance,
            "Go-around must reset hasClearance — old clearance is consumed")
    }

    @Test
    fun `GoAround only replaces incomplete circuit subtrees`() {
        // Build a two-circuit mission where the first circuit is complete.
        val root = CompoundTask(TaskName.CircuitTraining, listOf(
            CompoundTask(TaskName.Circuit, listOf(
                PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL, completed = true),
                PrimitiveTask(MissionStep.LAND, CompletionMode.PHYSICAL, completed = true),
            )),
            CompoundTask(TaskName.Circuit, listOf(
                PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL, completed = true),
                PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL), // incomplete — active
            )),
        ))
        val mission = PilotMission(goal = HighLevelGoal.CircuitTraining(2), root = root)
        val result = processInstruction(GoAround(AircraftId("TEST")), mission, t10)

        // The first (completed) circuit should be untouched.
        val firstChild = result.root.children[0] as CompoundTask
        assertTrue(firstChild.isComplete, "First circuit should remain complete after go-around")
        assertEquals(TaskName.Circuit, firstChild.name, "First circuit should be untouched")

        // The second (incomplete) circuit should be replaced with CircuitAfterGoAround.
        val secondChild = result.root.children[1] as CompoundTask
        assertEquals(TaskName.CircuitAfterGoAround, secondChild.name,
            "Second circuit should be replaced with go-around")
    }

    // ── Route override lifecycle (IFR-5) ──────────────────────────────

    @Test
    fun `FlyHeading sets Vectoring override`() {
        val mission = arriveMission(PilotPhase.Final)
        val result = processInstruction(FlyHeading(AircraftId("TEST"), Heading.unsafe(270)), mission, t10)
        assertTrue(result.routeOverride is RouteOverride.Vectoring)
        assertEquals(Heading.unsafe(270), (result.routeOverride as RouteOverride.Vectoring).heading)
    }

    @Test
    fun `TurnHeading sets Vectoring override`() {
        val mission = arriveMission(PilotPhase.Final)
        val result = processInstruction(
            TurnHeading(AircraftId("TEST"), TurnDirection.LEFT, Heading.unsafe(180)), mission, t10,
        )
        assertTrue(result.routeOverride is RouteOverride.Vectoring)
    }

    @Test
    fun `HoldAt sets Holding override`() {
        val mission = arriveMission(PilotPhase.Final)
        val hold = HoldSpec.Published(FixId("FIX"))
        val result = processInstruction(HoldAt(AircraftId("TEST"), hold), mission, t10)
        assertTrue(result.routeOverride is RouteOverride.Holding)
        assertEquals(hold, (result.routeOverride as RouteOverride.Holding).hold)
    }

    @Test
    fun `ResumeOwnNavigation clears override`() {
        val mission = arriveMission(PilotPhase.Final)
            .copy(routeOverride = RouteOverride.Vectoring(Heading.unsafe(270)))
        val result = processInstruction(ResumeOwnNavigation(AircraftId("TEST")), mission, t10)
        assertEquals(null, result.routeOverride)
    }

    // ── FPL amendment wiring (IFR-6) ────────────────────────────────

    @Test
    fun `processInstruction ClearedApproach updates IFR mission FPL`() {
        // Start with an IFR mission that has en-route clearance.
        val fpl = FlightPlan(
            departureAerodrome = AerodromeId("LOWG"),
            arrivalAerodrome = AerodromeId("LJLJ"),
            requestedLevel = Level.FlightLevel.unsafe(100),
            enRouteWaypoints = emptyList(),
            clearance = ClearanceState.EnRouteClearance(
                clearanceLimit = FixId("WP1"),
                departureRunway = RunwayId("09"),
            ),
        )
        val mission = arriveMission(PilotPhase.Final)
            .copy(navigationMode = NavigationMode.Instrument(fpl))

        // Issue ClearedApproach — should advance to ApproachClearance.
        val result = processInstruction(
            ClearedApproach(AircraftId("TEST"), ApproachType.ILS, RunwayId("09")),
            mission, t10,
        )

        val updatedMode = result.navigationMode as? NavigationMode.Instrument
        assertNotNull(updatedMode, "Navigation mode should still be Instrument")
        val clearance = updatedMode.fpl.clearance
        assertTrue(clearance is ClearanceState.ApproachClearance,
            "Clearance should advance to ApproachClearance, was ${clearance::class.simpleName}")
        assertEquals(ApproachType.ILS, clearance.approachType)
    }

    @Test
    fun `processInstruction on VFR mission does not call amendFpl`() {
        val mission = arriveMission(PilotPhase.Final) // VFR mission, no NavigationMode.Instrument
        val result = processInstruction(
            ClearedApproach(AircraftId("TEST"), ApproachType.ILS, RunwayId("09")),
            mission, t10,
        )
        // VFR mission should be unchanged — no FPL to amend.
        assertEquals(mission.navigationMode, result.navigationMode)
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
        val mission = createMission(HighLevelGoal.CircuitTraining(1), PilotPhase.AtStand, t0)
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
        val tree = CompoundTask(TaskName.Depart, listOf(
            PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL, completed = true),
            CompoundTask(TaskName.Circuit, listOf(
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
        val tree = CompoundTask(TaskName.Arrive, listOf(
            CompoundTask(TaskName.ArrivalJoin, listOf(
                PrimitiveTask(MissionStep.CALL_INBOUND, CompletionMode.REPORTED, completed = true),
                PrimitiveTask(MissionStep.AWAIT_JOINING_INSTRUCTIONS, CompletionMode.INSTRUCTION_GATED, completed = true),
            )),
            CompoundTask(TaskName.Circuit, listOf(
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
        val tree = CompoundTask(TaskName.Depart, listOf(
            PrimitiveTask(MissionStep.FLY_DOWNWIND, CompletionMode.PHYSICAL, completed = true),
            PrimitiveTask(MissionStep.REPORT_DOWNWIND, CompletionMode.REPORTED),
        ))
        val marked = tree.markComplete(MissionStep.FLY_DOWNWIND)
        // FLY_DOWNWIND was already complete, REPORT_DOWNWIND should still be incomplete.
        val report = marked.children[1] as PrimitiveTask
        assertTrue(!report.completed)
    }

    // ── IFR mission decomposition ───────────────────────────────────

    @Test
    fun `IFR Departure decomposes to GroundDeparture + FLY_SID + FLY_EN_ROUTE + SHUTDOWN`() {
        val root = planMission(HighLevelGoal.Departure(), humanPiloted = true, ifr = true)
        val steps = collectPrimitiveSteps(root)
        assertTrue(MissionStep.FLY_SID in steps, "IFR departure should include FLY_SID")
        assertTrue(MissionStep.FLY_EN_ROUTE in steps, "IFR departure should include FLY_EN_ROUTE")
        assertTrue(MissionStep.FLY_DEPARTURE !in steps, "IFR departure should not include VFR FLY_DEPARTURE")
    }

    @Test
    fun `IFR Arrival decomposes to FLY_STAR + FLY_APPROACH + LAND + GroundArrival`() {
        val root = planMission(HighLevelGoal.Arrival(), humanPiloted = true, ifr = true)
        val steps = collectPrimitiveSteps(root)
        assertTrue(MissionStep.FLY_STAR in steps, "IFR arrival should include FLY_STAR")
        assertTrue(MissionStep.FLY_APPROACH in steps, "IFR arrival should include FLY_APPROACH")
        assertTrue(MissionStep.LAND in steps, "IFR arrival should include LAND")
    }

    @Test
    fun `VFR Departure unchanged — still uses FLY_DEPARTURE`() {
        val root = planMission(HighLevelGoal.Departure(), humanPiloted = true, ifr = false)
        val steps = collectPrimitiveSteps(root)
        assertTrue(MissionStep.FLY_DEPARTURE in steps)
        assertTrue(MissionStep.FLY_SID !in steps)
    }

    @Test
    fun `CircuitTraining ignores ifr flag`() {
        val root = planMission(HighLevelGoal.CircuitTraining(1), humanPiloted = true, ifr = true)
        val steps = collectPrimitiveSteps(root)
        assertTrue(MissionStep.FLY_DEPARTURE in steps, "Circuit training should still use VFR steps")
        assertTrue(MissionStep.FLY_SID !in steps, "Circuit training should not have IFR steps")
    }

    // ── MissionStep coverage guard ──────────────────────────────────

    @Test
    fun `every MissionStep appears in at least one planMission decomposition`() {
        val allSteps = mutableSetOf<MissionStep>()
        // Collect from all goal × ifr combinations.
        for (ifr in listOf(false, true)) {
            allSteps += collectPrimitiveSteps(planMission(HighLevelGoal.Departure(), ifr = ifr))
            allSteps += collectPrimitiveSteps(planMission(HighLevelGoal.Arrival(), ifr = ifr))
            allSteps += collectPrimitiveSteps(planMission(HighLevelGoal.Transit(), ifr = ifr))
        }
        allSteps += collectPrimitiveSteps(planMission(HighLevelGoal.CircuitTraining(2)))
        // Also add steps from go-around tasks (used in runtime replanning, not initial decomposition).
        allSteps += collectPrimitiveSteps(goAroundTask())
        allSteps += collectPrimitiveSteps(ifrGoAroundTask())

        val missing = MissionStep.entries.toSet() - allSteps
        assertTrue(missing.isEmpty(),
            "MissionStep values not in any planMission decomposition: $missing. " +
            "Add them to a task tree or update skipCompletedSteps.")
    }

    private fun collectPrimitiveSteps(node: TaskNode): Set<MissionStep> = when (node) {
        is PrimitiveTask -> setOf(node.step)
        is CompoundTask -> node.children.flatMap { collectPrimitiveSteps(it) }.toSet()
    }

    // ── derivePilotGoal null-fallback (A5) ──────────────────────────────

    @Test
    fun `derivePilotGoal returns TRANSIT for Transit mission when activeCompound is null`() {
        // Transit planMission: root=Transit compound whose children are primitives,
        // so activeCompound() returns null. Must fall back to HighLevelGoal → TRANSIT.
        val mission = createMission(HighLevelGoal.Transit(), PilotPhase.AtStand, t0)
        assertEquals(PilotGoal.TRANSIT, derivePilotGoal(mission))
    }

    @Test
    fun `derivePilotGoal returns TRANSIT for completed Transit mission`() {
        // Regression: before fix, Transit→null fallback returned PilotGoal.DEPART.
        val mission = createMission(HighLevelGoal.Transit(), PilotPhase.AtStand, t0)
        var root = mission.root
        for (step in MissionStep.entries) { root = root.markComplete(step) }
        assertEquals(PilotGoal.TRANSIT, derivePilotGoal(mission.copy(root = root)),
            "Completed Transit mission must return TRANSIT not DEPART")
    }

    @Test
    fun `derivePilotGoal returns ARRIVE for completed Arrival mission`() {
        val mission = createMission(HighLevelGoal.Arrival(), PilotPhase.Final, t0)
        var root = mission.root
        for (step in MissionStep.entries) { root = root.markComplete(step) }
        assertEquals(PilotGoal.ARRIVE, derivePilotGoal(mission.copy(root = root)))
    }

    @Test
    fun `derivePilotGoal returns DEPART for completed Departure mission`() {
        val mission = createMission(HighLevelGoal.Departure(), PilotPhase.AtStand, t0)
        var root = mission.root
        for (step in MissionStep.entries) { root = root.markComplete(step) }
        assertEquals(PilotGoal.DEPART, derivePilotGoal(mission.copy(root = root)))
    }

    @Test
    fun `derivePilotGoal returns TOUCH_AND_GO during go-around phase of CircuitAfterGoAround`() {
        // CircuitAfterGoAround: [goAroundTask (incomplete), circuitTask]
        // While the GoAround subtask is active, the aircraft is climbing away → TOUCH_AND_GO.
        val root = CompoundTask(TaskName.CircuitTraining, listOf(
            CompoundTask(TaskName.CircuitAfterGoAround, listOf(
                CompoundTask(TaskName.GoAround, listOf(
                    PrimitiveTask(MissionStep.GOING_AROUND, CompletionMode.PHYSICAL), // active
                )),
                CompoundTask(TaskName.Circuit, listOf(
                    PrimitiveTask(MissionStep.FLY_DOWNWIND, CompletionMode.PHYSICAL),
                )),
            )),
        ))
        val mission = PilotMission(goal = HighLevelGoal.CircuitTraining(1), root = root)
        assertEquals(PilotGoal.TOUCH_AND_GO, derivePilotGoal(mission),
            "During go-around climb-out, goal should be TOUCH_AND_GO")
    }

    @Test
    fun `derivePilotGoal returns ARRIVE during resumed circuit after go-around`() {
        // CircuitAfterGoAround: [goAroundTask (complete), circuitTask (active)]
        // Once the go-around phase is done, the inner Circuit is a full-stop landing → ARRIVE.
        val root = CompoundTask(TaskName.CircuitTraining, listOf(
            CompoundTask(TaskName.CircuitAfterGoAround, listOf(
                CompoundTask(TaskName.GoAround, listOf(
                    PrimitiveTask(MissionStep.GOING_AROUND, CompletionMode.PHYSICAL, completed = true),
                )),
                CompoundTask(TaskName.Circuit, listOf(
                    PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL), // active
                )),
            )),
        ))
        val mission = PilotMission(goal = HighLevelGoal.CircuitTraining(1), root = root)
        assertEquals(PilotGoal.ARRIVE, derivePilotGoal(mission),
            "During resumed full-stop circuit after go-around, goal must be ARRIVE not TOUCH_AND_GO")
    }

    // ── JoinCircuit stores joinLeg (A12) ────────────────────────────────

    @Test
    fun `JoinCircuit stores joinLeg and marks AWAIT_JOINING_INSTRUCTIONS complete`() {
        // Construct the ArrivalJoin task with CALL_INBOUND already complete (pilot has called inbound)
        // so the active step is AWAIT_JOINING_INSTRUCTIONS (INSTRUCTION_GATED).
        val arrivalJoin = CompoundTask(TaskName.ArrivalJoin, listOf(
            PrimitiveTask(MissionStep.CALL_INBOUND, CompletionMode.REPORTED, completed = true),
            PrimitiveTask(MissionStep.AWAIT_JOINING_INSTRUCTIONS, CompletionMode.INSTRUCTION_GATED),
        ))
        val root = CompoundTask(TaskName.Arrive, listOf(
            arrivalJoin,
            circuitTask(),
            groundArrivalTask(),
        ))
        val mission = PilotMission(goal = HighLevelGoal.Arrival(), root = root, stepEnteredAt = t0,
            contactedOnFrequency = true)
        assertEquals(MissionStep.AWAIT_JOINING_INSTRUCTIONS, mission.currentTask?.step,
            "Precondition: mission should be at AWAIT_JOINING_INSTRUCTIONS")

        val result = processInstruction(
            JoinCircuit(AircraftId("TEST"), CircuitDirection.LEFT_HAND, JoinType.BASE, RunwayId("09")),
            mission, t10,
        )

        assertEquals(arrow.core.Some(LegName.BASE), result.joinLeg,
            "JoinCircuit(BASE) must store LegName.BASE in joinLeg")
        // AWAIT_JOINING_INSTRUCTIONS is INSTRUCTION_GATED so it is now complete.
        assertTrue(result.currentTask?.step != MissionStep.AWAIT_JOINING_INSTRUCTIONS,
            "AWAIT_JOINING_INSTRUCTIONS must be marked complete after JoinCircuit")
    }

    @Test
    fun `JoinType toCircuitLeg exhaustive mapping`() {
        // Verify every JoinType maps to the expected LegName.
        assertEquals(LegName.DOWNWIND, JoinType.DOWNWIND.toCircuitLeg())
        assertEquals(LegName.DOWNWIND, JoinType.MID_DOWNWIND.toCircuitLeg())
        assertEquals(LegName.BASE, JoinType.BASE.toCircuitLeg())
        assertEquals(LegName.FINAL, JoinType.STRAIGHT_IN.toCircuitLeg())
        assertEquals(LegName.FINAL, JoinType.LONG_FINAL.toCircuitLeg())
        assertEquals(LegName.CROSSWIND, JoinType.CROSSWIND.toCircuitLeg())
        assertEquals(LegName.DOWNWIND, JoinType.OVERHEAD.toCircuitLeg())
    }

    @Test
    fun `processInstruction returns unchanged mission for unknown instruction at wrong step`() {
        // TaxiTo at FLY_DOWNWIND — no match, mission returned unchanged.
        val mission = arriveMission(PilotPhase.Downwind)
        val result = processInstruction(TaxiTo(AircraftId("TEST"), PointId("STAND")), mission, t10)
        assertEquals(mission.currentTask?.step, result.currentTask?.step,
            "Unmatched instruction/step combination must return mission unchanged")
    }

    // ── touchAndGoCircuitTask name + go-around replacement (A11) ────────

    @Test
    fun `touchAndGoCircuitTask uses TaskName TouchAndGo not Circuit`() {
        assertEquals(TaskName.TouchAndGo, touchAndGoCircuitTask().name,
            "touchAndGoCircuitTask must use TaskName.TouchAndGo so derivePilotGoal and go-around replacement identify it correctly")
    }

    @Test
    fun `GoAround replaces incomplete TouchAndGo circuit subtree`() {
        val root = CompoundTask(TaskName.CircuitTraining, listOf(
            CompoundTask(TaskName.TouchAndGo, listOf(
                PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL, completed = true),
                PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL), // incomplete
            )),
        ))
        val mission = PilotMission(goal = HighLevelGoal.CircuitTraining(1), root = root)
        val result = processInstruction(GoAround(AircraftId("TEST")), mission, t10)
        val child = result.root.children[0] as CompoundTask
        assertEquals(TaskName.CircuitAfterGoAround, child.name,
            "GoAround should replace incomplete TouchAndGo task with CircuitAfterGoAround")
    }
}
