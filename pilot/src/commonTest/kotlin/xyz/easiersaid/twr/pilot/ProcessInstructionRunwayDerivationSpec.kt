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
import xyz.easiersaid.twr.protocol.TaxiTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spec test (E7) for the Phase C runway-from-radio derivation in
 * [processInstruction].
 *
 * Pure-function spec. The pilot's runway comes from radio alone — there
 * is no controller-state read. Five rows pin the contract so a future
 * regression names itself rather than failing G0 with "mission did not
 * complete in 30 minutes".
 *
 * The five rows correspond to:
 *  1. TaxiTo(holding-point-of-runway-X) → activeRunway = X
 *  2. LineUpAndWait(X) → activeRunway = X
 *  3. ClearedForTakeoff(X) → activeRunway = X
 *  4. Disregard → activeRunway unchanged
 *  5. fresh mission, no prior radio → activeRunway null
 *
 * Multi-runway holding-point ambiguity (D-PF.6) is NOT covered here — the
 * test airport has each holding point serving a single runway, matching
 * G0/LOWG. When D-PF.6 lands and TaxiTo carries an explicit runway field,
 * the test expands.
 */
class ProcessInstructionRunwayDerivationSpec {

    private val aircraftId = AircraftId("OE-ABC")
    private val rwy16C = RunwayId("16C")
    private val holdingPoint = PointId("HOLD_16C")

    /** Minimal world index: one runway, one holding point. */
    private val worldIndex = WorldIndex(
        holdingPointsByRunway = mapOf(rwy16C to setOf(holdingPoint)),
    )

    private val freshMission: PilotMission = createMission(
        goal = HighLevelGoal.CircuitTraining(circuits = 1, fullStopOnLast = true),
        startPhase = PilotPhase.AtStand,
        time = SimTime.ZERO,
    )

    @Test
    fun `TaxiTo to a holding point of runway 16C sets activeRunway to 16C`() {
        val instr = TaxiTo(target = aircraftId, destination = holdingPoint, via = emptyList())
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, worldIndex)
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
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
        // A non-runway-bearing instruction (Disregard) on a fresh mission.
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
        // The pilot's `activeRunway` is preserved across the instruction.
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

    // ── Multi-runway holding-point disambiguator ──────────────────────────────
    //
    // The reverse-lookup `runwaysForHoldingPoint` returns Set<RunwayId>; when
    // a single holding point serves multiple runways (intersecting taxiways
    // at parallel-runway airports), the disambiguator picks: (i) the prior
    // `activeRunway` if it remains in the candidate set, (ii) otherwise the
    // sorted-first by RunwayId.value. The clean fix (D-PF.6) is for TaxiTo
    // to carry an explicit runway field and remove the inference entirely.

    private val rwy16L = RunwayId("16L")
    private val sharedHolding = PointId("HOLD_A4")
    private val multiRunwayWorldIndex = WorldIndex(
        holdingPointsByRunway = mapOf(
            rwy16C to setOf(sharedHolding),
            rwy16L to setOf(sharedHolding),
        ),
    )

    @Test
    fun `multi-runway TaxiTo with prior activeRunway in candidates keeps the prior`() {
        val priorMission = freshMission.copy(
            activeRunway = Some(RunwayAssignment(rwy16L, RunwayAssignmentSource.TaxiClearance)),
        )
        val instr = TaxiTo(target = aircraftId, destination = sharedHolding, via = emptyList())
        val updated = processInstruction(instr, priorMission, SimTime.ZERO, multiRunwayWorldIndex)
        assertEquals(rwy16L, updated.activeRunway.getOrNull()?.runway)
    }

    @Test
    fun `multi-runway TaxiTo with no prior picks sorted-first deterministically`() {
        val instr = TaxiTo(target = aircraftId, destination = sharedHolding, via = emptyList())
        val updated = processInstruction(instr, freshMission, SimTime.ZERO, multiRunwayWorldIndex)
        // 16C sorts before 16L lexicographically.
        assertEquals(rwy16C, updated.activeRunway.getOrNull()?.runway)
        // Re-run with 100 fresh passes; result must be invariant (no Set-iteration
        // dependency). If the disambiguator regressed to `candidates.first()`
        // without the .sortedBy, this would fail probabilistically across JVM
        // versions or hash-perturbation seeds.
        repeat(100) {
            val r = processInstruction(instr, freshMission, SimTime.ZERO, multiRunwayWorldIndex)
            assertTrue(
                r.activeRunway.getOrNull()?.runway == rwy16C,
                "Disambiguator must be deterministic; got ${r.activeRunway}",
            )
        }
    }
}
