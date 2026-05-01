package xyz.easiersaid.twr.pilot

import arrow.core.None
import arrow.core.Some
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.Disregard
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiViaRunway
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Spec test (E7) for the runway-from-radio derivation in [processInstruction].
 *
 * Pure-function spec. The pilot's runway comes from radio alone — there is
 * no controller-state read. Each row pins one (instruction → activeRunway)
 * cell of the contract.
 *
 * Pass 6 (D-PF.6 closure): the multi-runway holding-point disambiguator is
 * gone — the runway is now an explicit field on [TaxiToHoldingPoint] (and
 * already was on [TaxiViaRunway]). The two earlier "multi-runway TaxiTo
 * with prior activeRunway / sorted-first" rows pinned a fallback that no
 * longer exists; they retire with the disambiguator. Replaced by a
 * **twin-row** (Test review M.3) — the same destination, two different
 * explicit runways — to pin "runway from field" rather than
 * "runway from destination's first candidate."
 */
class ProcessInstructionRunwayDerivationSpec {

    private val aircraftId = AircraftId("OE-ABC")
    private val rwy16C = RunwayId("16C")
    private val rwy16L = RunwayId("16L")
    private val holdingA4 = PointId("HOLD_A4")
    private val holdingPoint16C = PointId("HOLD_16C")

    private val worldIndex = WorldIndex(
        holdingPointsByRunway = mapOf(
            rwy16C to setOf(holdingPoint16C, holdingA4),
            rwy16L to setOf(holdingA4),
        ),
    )

    private val freshMission: PilotMission = createMission(
        goal = HighLevelGoal.CircuitTraining(circuits = 1, fullStopOnLast = true),
        startPhase = PilotPhase.AtStand,
        time = SimTime.ZERO,
    )

    /**
     * Pass 6 contract row: TaxiToHoldingPoint sets activeRunway from the
     * field. Single source-of-truth row pinning the field-flow path; the
     * earlier per-runway "TaxiTo to a holding point of runway 16C sets
     * activeRunway to 16C" rows pruned (Test review S.5) — they tested
     * "did the pilot copy a field" once the disambiguator went away.
     */
    @Test
    fun `TaxiToHoldingPoint propagates the explicit runway to activeRunway`() {
        val instr = TaxiToHoldingPoint(
            target = aircraftId, destination = holdingPoint16C, runway = rwy16C, via = emptyList(),
        )
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
        assertEquals(RunwayAssignmentSource.TaxiClearance, updated.activeRunway.getOrNull()?.source)
    }

    /**
     * Twin-row (Test review M.3): same destination [holdingA4] (which serves
     * both 16C and 16L), two different explicit runways. Without the twin,
     * "runway from field" would be consistent with "runway from destination's
     * first candidate" on a fixture where A4 sorts 16C-first.
     */
    @Test
    fun `multi-runway holding point with explicit 16C in field sets activeRunway = 16C`() {
        val instr = TaxiToHoldingPoint(
            target = aircraftId, destination = holdingA4, runway = rwy16C, via = emptyList(),
        )
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
    }

    @Test
    fun `multi-runway holding point with explicit 16L in field sets activeRunway = 16L`() {
        val instr = TaxiToHoldingPoint(
            target = aircraftId, destination = holdingA4, runway = rwy16L, via = emptyList(),
        )
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16L, updated.activeRunway.getOrNull()?.runway)
    }

    /**
     * Impact review M.1 / B.iii: TaxiViaRunway carries an explicit runway and
     * propagates it to activeRunway with source TaxiClearance — symmetric
     * with TaxiToHoldingPoint.
     */
    @Test
    fun `TaxiViaRunway propagates the explicit runway to activeRunway with TaxiClearance source`() {
        val instr = TaxiViaRunway(target = aircraftId, runway = rwy16C, destination = holdingPoint16C)
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
        assertEquals(RunwayAssignmentSource.TaxiClearance, updated.activeRunway.getOrNull()?.source)
    }

    @Test
    fun `LineUpAndWait sets activeRunway from the instruction's runway field`() {
        val instr = LineUpAndWait(target = aircraftId, runway = rwy16C)
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
    }

    @Test
    fun `ClearedForTakeoff sets activeRunway from the instruction's runway field`() {
        val instr = ClearedForTakeoff(target = aircraftId, runway = rwy16C)
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
    }

    @Test
    fun `Disregard does not change activeRunway`() {
        val priorMission = freshMission.copy(activeRunway = Some(RunwayAssignment(rwy16C, RunwayAssignmentSource.Land)))
        val instr = Disregard(target = aircraftId)
        val updated = processInstruction(instr, priorMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
    }

    @Test
    fun `processInstruction on a fresh mission with no prior leaves activeRunway None`() {
        val instr = Disregard(target = aircraftId)
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(None, updated.activeRunway)
    }

    @Test
    fun `ClearedToLand sets activeRunway from the instruction's runway field`() {
        val instr = ClearedToLand(target = aircraftId, runway = rwy16C)
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
    }

    @Test
    fun `ClearedTouchAndGo sets activeRunway from the instruction's runway field`() {
        val instr = ClearedTouchAndGo(target = aircraftId, runway = rwy16C)
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
    }

    @Test
    fun `AfterLandingVacateVia preserves the prior activeRunway (instruction has no runway field)`() {
        // AfterLandingVacateVia carries the exit point, not the runway — the
        // runway is implicit (the one just landed on, set by ClearedToLand).
        val priorMission = freshMission.copy(activeRunway = Some(RunwayAssignment(rwy16C, RunwayAssignmentSource.Land)))
        val instr = AfterLandingVacateVia(target = aircraftId, exit = PointId("EXIT_W2"))
        val updated = processInstruction(instr, priorMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
    }

    @Test
    fun `BacktrackRunway sets activeRunway from the instruction's runway field`() {
        val instr = BacktrackRunway(target = aircraftId, runway = rwy16C)
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
    }
}
