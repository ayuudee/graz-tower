package xyz.easiersaid.twr.pilot

import arrow.core.None
import arrow.core.Some
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.Disregard
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.HoldAt
import xyz.easiersaid.twr.protocol.HoldSpec
import xyz.easiersaid.twr.protocol.InterceptLocaliser
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.ResumeOwnNavigation
import xyz.easiersaid.twr.protocol.StopClimbAt
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.JoinType
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.StartupApproved
import xyz.easiersaid.twr.protocol.TaxiTo
import xyz.easiersaid.twr.protocol.TurnBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pass 1, Item 6 — pair-equivalence permanent test for [processInstruction]'s
 * mission-state contract.
 *
 * The Pass 1 restructure converted [processInstruction] from a condition-based
 * `when {}` to an outer `when (instruction)` exhaustive over [AtcInstruction]'s
 * leaves. Per-arm semantics must be preserved; the existing spec
 * ([ProcessInstructionRunwayDerivationSpec]) covers the runway-derivation slice
 * but not the mission-state slice (step completion, hasClearance, joinLeg,
 * routeOverride, contactedOnFrequency, etc.).
 *
 * **These rows pin the contract that future passes (Pass 5/8/11) must keep
 * green.** A regression in any leaf's mission-state effect names itself rather
 * than failing G0 with a 30-minute timeout diagnostic.
 *
 * Each test is a single (instruction, step) → mission-state assertion. Rows
 * cover the leaves with non-trivial mission-state effects; identity rows
 * (instruction has no effect for the given step) are covered structurally by
 * [ExhaustivenessTest] and not enumerated here.
 */
class ProcessInstructionMissionStateSpec {

    private val aircraftId = AircraftId("OE-TST")
    private val rwy = RunwayId("16")
    private val holdingPoint = PointId("HOLD_16")
    private val worldIndex = WorldIndex(
        holdingPointsByRunway = mapOf(rwy to setOf(holdingPoint)),
    )

    private fun missionAt(step: MissionStep): PilotMission {
        val base = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 1, fullStopOnLast = true),
            startPhase = PilotPhase.AtStand,
            time = SimTime.ZERO,
        )
        // Walk the mission tree forward by completing prior steps until [step] is current.
        var m = base
        var safety = 0
        while (m.currentTask?.step != step && safety < 50) {
            val cur = m.currentTask?.step ?: break
            m = m.copy(root = m.root.markComplete(cur), stepEnteredAt = SimTime.ZERO)
            safety++
        }
        return m
    }

    // ── Step-completion arms ────────────────────────────────────────────

    @Test
    fun `StartupApproved at AWAIT_STARTUP_APPROVAL completes the step`() {
        val m = missionAt(MissionStep.AWAIT_STARTUP_APPROVAL)
        val instr = StartupApproved(target = aircraftId)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertTrue(
            updated.currentTask?.step != MissionStep.AWAIT_STARTUP_APPROVAL,
            "Expected step to advance past AWAIT_STARTUP_APPROVAL, got ${updated.currentTask?.step}"
        )
    }

    @Test
    fun `TaxiTo at REQUEST_TAXI completes the step`() {
        val m = missionAt(MissionStep.REQUEST_TAXI)
        val instr = TaxiTo(target = aircraftId, destination = holdingPoint, via = emptyList())
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertTrue(
            updated.currentTask?.step != MissionStep.REQUEST_TAXI,
            "Expected step to advance past REQUEST_TAXI, got ${updated.currentTask?.step}"
        )
    }

    @Test
    fun `LineUpAndWait at AWAIT_LINE_UP completes the step`() {
        val m = missionAt(MissionStep.AWAIT_LINE_UP)
        val instr = LineUpAndWait(target = aircraftId, runway = rwy)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertTrue(
            updated.currentTask?.step != MissionStep.AWAIT_LINE_UP,
            "Expected step to advance past AWAIT_LINE_UP, got ${updated.currentTask?.step}"
        )
    }

    @Test
    fun `ClearedForTakeoff at AWAIT_LINE_UP completes the step`() {
        val m = missionAt(MissionStep.AWAIT_LINE_UP)
        val instr = ClearedForTakeoff(target = aircraftId, runway = rwy)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertTrue(
            updated.currentTask?.step != MissionStep.AWAIT_LINE_UP,
            "Expected step to advance past AWAIT_LINE_UP, got ${updated.currentTask?.step}"
        )
    }

    @Test
    fun `AfterLandingVacateVia at AWAIT_VACATE_INSTRUCTION completes the step`() {
        val m = missionAt(MissionStep.AWAIT_VACATE_INSTRUCTION)
        val instr = AfterLandingVacateVia(target = aircraftId, exit = PointId("EXIT_W"))
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertTrue(
            updated.currentTask?.step != MissionStep.AWAIT_VACATE_INSTRUCTION,
            "Expected step to advance past AWAIT_VACATE_INSTRUCTION, got ${updated.currentTask?.step}"
        )
    }

    // ── Step-completion irrelevance: arm at wrong step is no-op ────────

    @Test
    fun `StartupApproved at AWAIT_TAKEOFF_CLEARANCE is no-op`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE)
        val instr = StartupApproved(target = aircraftId)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertEquals(MissionStep.AWAIT_TAKEOFF_CLEARANCE, updated.currentTask?.step)
    }

    // ── Constraint manipulation (step-agnostic) ────────────────────────

    @Test
    fun `ExtendDownwind adds the ExtendingDownwind constraint`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE)
        val instr = ExtendDownwind(target = aircraftId)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertTrue(ActiveConstraint.ExtendingDownwind in updated.activeConstraints)
    }

    @Test
    fun `TurnBase removes the ExtendingDownwind constraint`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE)
            .copy(activeConstraints = setOf(ActiveConstraint.ExtendingDownwind))
        val instr = TurnBase(target = aircraftId)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertFalse(ActiveConstraint.ExtendingDownwind in updated.activeConstraints)
    }

    // ── Landing clearance: hasClearance flip ───────────────────────────

    @Test
    fun `ClearedToLand sets hasClearance true`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE)
        val instr = ClearedToLand(target = aircraftId, runway = rwy)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertTrue(updated.hasClearance)
    }

    @Test
    fun `ClearedTouchAndGo sets hasClearance true`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE)
        val instr = ClearedTouchAndGo(target = aircraftId, runway = rwy)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertTrue(updated.hasClearance)
    }

    // ── ContactFrequency: contactedOnFrequency reset + first-tick gate ──

    @Test
    fun `ContactFrequency resets contactedOnFrequency and lastTransmittedStep`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE).copy(
            contactedOnFrequency = true,
            lastTransmittedStep = Some(MissionStep.REPORT_READY),
        )
        val instr = ContactFrequency(target = aircraftId, role = RoleName.TOWER, frequency = Frequency.unsafe("118.250"))
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertFalse(updated.contactedOnFrequency)
        assertEquals(None, updated.lastTransmittedStep)
    }

    // ── routeOverride sets/clears (Pass 2 Item 7 — symmetric coverage) ──

    @Test
    fun `FlyHeading sets routeOverride to Vectoring`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE)
        val instr = FlyHeading(target = aircraftId, heading = Heading.unsafe(90))
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        when (val r = updated.routeOverride) {
            is Some -> assertTrue(r.value is RouteOverride.Vectoring, "Expected Vectoring, got ${r.value}")
            is None -> fail("Expected Some(Vectoring), got None")
        }
    }

    @Test
    fun `HoldAt sets routeOverride to Holding`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE)
        val instr = HoldAt(target = aircraftId, hold = HoldSpec.Published(fix = FixId("FOX01")))
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        when (val r = updated.routeOverride) {
            is Some -> assertTrue(r.value is RouteOverride.Holding, "Expected Holding, got ${r.value}")
            is None -> fail("Expected Some(Holding), got None")
        }
    }

    @Test
    fun `InterceptLocaliser clears routeOverride from prior Vectoring`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE).copy(
            routeOverride = Some(RouteOverride.Vectoring(Heading.unsafe(180))),
        )
        val instr = InterceptLocaliser(target = aircraftId, runway = rwy)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertEquals(None, updated.routeOverride)
    }

    @Test
    fun `ResumeOwnNavigation clears routeOverride from prior Holding`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE).copy(
            routeOverride = Some(RouteOverride.Holding(HoldSpec.Published(fix = FixId("FOX02")))),
        )
        val instr = ResumeOwnNavigation(target = aircraftId)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertEquals(None, updated.routeOverride)
    }

    @Test
    fun `Disregard clears routeOverride and ExtendingDownwind constraint`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE).copy(
            routeOverride = Some(RouteOverride.Vectoring(Heading.unsafe(90))),
            activeConstraints = setOf(ActiveConstraint.ExtendingDownwind),
        )
        val instr = Disregard(target = aircraftId)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertEquals(None, updated.routeOverride)
        assertFalse(ActiveConstraint.ExtendingDownwind in updated.activeConstraints)
    }

    // ── altitudeRestrictionM (Pass 2 Item 7 — symmetric coverage) ──────

    @Test
    fun `StopClimbAt sets altitudeRestrictionM in metres`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE)
        val instr = StopClimbAt(target = aircraftId, level = Level.AltitudeFeet.unsafe(3000))
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertTrue(updated.altitudeRestrictionM is Some, "Expected altitudeRestrictionM to be Some, got ${updated.altitudeRestrictionM}")
    }

    @Test
    fun `fresh mission has altitudeRestrictionM equal to None`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE)
        assertEquals(None, m.altitudeRestrictionM)
    }

    // ── IFR navigationMode regression net (Pass 2 post-impl review) ─────
    //
    // The Pass 2 migration changed `navigationMode: NavigationMode?` to
    // `Option<NavigationMode>`. A subtle bug regressed `handleGoAround` and
    // `Pilot.kt:checkSelfInitiatedGoAround` to use
    // `mission.navigationMode is NavigationMode.Instrument` — which is
    // structurally always false against an `Option<NavigationMode>` receiver
    // (the discriminator never matches). The IFR go-around branch became
    // dead code. Caught by the post-impl review (Pass 2 Item 9 spirit:
    // "expand tests, not throw"). This row pins the contract that an IFR
    // mission's GoAround selects [ifrGoAroundTask] (3 steps), not [goAroundTask]
    // (1 step).

    @Test
    fun `GoAround on IFR mission picks ifrGoAroundTask, not VFR goAroundTask`() {
        val m = missionAt(MissionStep.AWAIT_TAKEOFF_CLEARANCE).copy(
            navigationMode = Some(NavigationMode.Instrument(
                fpl = xyz.easiersaid.twr.protocol.FlightPlan(
                    departureAerodrome = xyz.easiersaid.twr.protocol.AerodromeId("LOWG"),
                    arrivalAerodrome = xyz.easiersaid.twr.protocol.AerodromeId("LOWW"),
                    requestedLevel = xyz.easiersaid.twr.protocol.Level.AltitudeFeet.unsafe(8000),
                    enRouteWaypoints = emptyList(),
                ),
            )),
        )
        val instr = xyz.easiersaid.twr.protocol.GoAround(target = aircraftId)
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        // The IFR go-around tree contains FLY_MISSED_APPROACH; the VFR shape does not.
        // Walk the new tree and confirm.
        val gaTask = updated.root.children.firstOrNull {
            it is CompoundTask && it.name is TaskName.CircuitAfterGoAround
        } as? CompoundTask
        check(gaTask != null) { "Expected CircuitAfterGoAround compound after GoAround instruction; got ${updated.root}" }
        val gaSubtree = gaTask.children.firstOrNull {
            it is CompoundTask && it.name is TaskName.GoAround
        } as? CompoundTask
        check(gaSubtree != null) { "Expected GoAround sub-compound; got ${gaTask.children}" }
        val steps = gaSubtree.children.filterIsInstance<PrimitiveTask>().map { it.step }
        assertTrue(
            MissionStep.FLY_MISSED_APPROACH in steps,
            "IFR go-around tree must include FLY_MISSED_APPROACH; got $steps. " +
                "If this fails, handleGoAround regressed to picking VFR shape for IFR missions."
        )
    }

    // ── JoinCircuit: stores joinLeg ─────────────────────────────────────

    @Test
    fun `JoinCircuit at AWAIT_JOINING_INSTRUCTIONS sets joinLeg from joinType`() {
        // Arrival mission carries an AWAIT_JOINING_INSTRUCTIONS step; CircuitTraining
        // (used elsewhere here) does not.
        val arrivalBase = createMission(
            goal = HighLevelGoal.Arrival(),
            startPhase = PilotPhase.Climbing,
            time = SimTime.ZERO,
        )
        var m = arrivalBase
        var safety = 0
        while (m.currentTask?.step != MissionStep.AWAIT_JOINING_INSTRUCTIONS && safety < 50) {
            val cur = m.currentTask?.step ?: break
            m = m.copy(root = m.root.markComplete(cur), stepEnteredAt = SimTime.ZERO)
            safety++
        }
        check(m.currentTask?.step == MissionStep.AWAIT_JOINING_INSTRUCTIONS) {
            "Could not advance arrival mission to AWAIT_JOINING_INSTRUCTIONS; " +
                "got ${m.currentTask?.step}. Test fixture may need updating."
        }
        val instr = JoinCircuit(
            target = aircraftId,
            circuitDirection = CircuitDirection.LEFT_HAND,
            joinType = JoinType.DOWNWIND,
        )
        val updated = processInstruction(instr, m, SimTime.ZERO, worldIndex)
        assertTrue(
            updated.joinLeg.isSome(),
            "Expected joinLeg to be set after JoinCircuit at AWAIT_JOINING_INSTRUCTIONS"
        )
    }
}
