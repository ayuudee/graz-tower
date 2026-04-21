package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.procedure.*
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.protocol.Knots
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the position classification functions — the bridge between
 * raw [AircraftObservation] and the sealed position types consumed by
 * the reconciliation functions.
 *
 * These classifications contain non-trivial logic: rolling threshold,
 * circuit leg name matching, entity ref type checks, and priority-ordered
 * when chains. Testing in isolation ensures the reconciliation tests
 * exercise the right positions.
 */
class PositionClassificationTest {

    private val worldIndex = testWorldIndex()

    // ── Departure classification ────────────────────────────────────

    @Test
    fun `departure — ground at holding point`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true)
        assertEquals(DeparturePosition.AtHolding, classifyDeparturePosition(ac, worldIndex))
    }

    @Test
    fun `departure — ground on runway, not rolling`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex, onGround = true)
        assertEquals(DeparturePosition.OnRunway, classifyDeparturePosition(ac, worldIndex))
    }

    @Test
    fun `departure — ground on runway, rolling`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex,
            onGround = true, groundSpeed = Knots.unsafe(50))
        assertEquals(DeparturePosition.OnRunwayRolling, classifyDeparturePosition(ac, worldIndex))
    }

    @Test
    fun `departure — ground on runway, below rolling threshold`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex,
            onGround = true, groundSpeed = Knots.unsafe(20))
        assertEquals(DeparturePosition.OnRunway, classifyDeparturePosition(ac, worldIndex))
    }

    @Test
    fun `departure — airborne over runway`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex, onGround = false)
        assertEquals(DeparturePosition.AirborneOverRunway, classifyDeparturePosition(ac, worldIndex))
    }

    @Test
    fun `departure — airborne on upwind`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.upwind, worldIndex, onGround = false)
        assertEquals(DeparturePosition.OnClimbout, classifyDeparturePosition(ac, worldIndex))
    }

    @Test
    fun `departure — airborne on crosswind`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.crosswind, worldIndex, onGround = false)
        assertEquals(DeparturePosition.OnClimbout, classifyDeparturePosition(ac, worldIndex))
    }

    @Test
    fun `departure — airborne not on any known position treated as climbout`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.downwind, worldIndex, onGround = false)
        assertEquals(DeparturePosition.OnClimbout, classifyDeparturePosition(ac, worldIndex))
    }

    // ── Arrival classification ──────────────────────────────────────

    @Test
    fun `arrival — airborne on downwind`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.downwind, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        assertEquals(ArrivalPosition.OnDownwind, classifyArrivalPosition(ac, worldIndex))
    }

    @Test
    fun `arrival — airborne on base`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.base, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        assertEquals(ArrivalPosition.OnBase, classifyArrivalPosition(ac, worldIndex))
    }

    @Test
    fun `arrival — airborne on final`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.finalApproach, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        assertEquals(ArrivalPosition.OnFinal, classifyArrivalPosition(ac, worldIndex))
    }

    @Test
    fun `arrival — airborne over runway (short final)`() {
        // Runway threshold has both FINAL and UPWIND legs — FINAL takes priority.
        val ac = aircraftAt(TestIds.acAlpha, TestIds.rwyThreshold, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        assertEquals(ArrivalPosition.OnFinal, classifyArrivalPosition(ac, worldIndex))
    }

    @Test
    fun `arrival — ground on runway`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex,
            onGround = true, goal = PilotGoal.ARRIVE)
        assertEquals(ArrivalPosition.OnRunway, classifyArrivalPosition(ac, worldIndex))
    }

    @Test
    fun `arrival — ground clear of runway`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex,
            onGround = true, goal = PilotGoal.ARRIVE)
        assertEquals(ArrivalPosition.ClearOfRunway, classifyArrivalPosition(ac, worldIndex))
    }

    @Test
    fun `arrival — airborne, not on any circuit leg or approach`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.apron, worldIndex,
            onGround = false, goal = PilotGoal.ARRIVE)
        assertEquals(ArrivalPosition.AirborneElsewhere, classifyArrivalPosition(ac, worldIndex))
    }

    // ── Ground classification ───────────────────────────────────────

    @Test
    fun `ground — at stand`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.standPoint, worldIndex, onGround = true)
        assertEquals(GroundPosition.AtStand, classifyGroundPosition(ac, TestIds.runway09, worldIndex))
    }

    @Test
    fun `ground — at holding point`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true)
        assertEquals(GroundPosition.AtHoldingPoint, classifyGroundPosition(ac, TestIds.runway09, worldIndex))
    }

    @Test
    fun `ground — on runway (incursion)`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.rwyMid, worldIndex, onGround = true)
        assertEquals(GroundPosition.OnRunway, classifyGroundPosition(ac, TestIds.runway09, worldIndex))
    }

    @Test
    fun `ground — taxiing (not at stand, not at holding, not on runway)`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.apron, worldIndex, onGround = true)
        assertEquals(GroundPosition.Taxiing, classifyGroundPosition(ac, TestIds.runway09, worldIndex))
    }

    @Test
    fun `ground — airborne is Elsewhere`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.upwind, worldIndex, onGround = false)
        assertEquals(GroundPosition.Elsewhere, classifyGroundPosition(ac, TestIds.runway09, worldIndex))
    }
}
