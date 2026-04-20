package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.applySupersessionCleanup
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.PendingReadback
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Disregard
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TurnBase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [applySupersessionCleanup] — the post-arbitration pending-readback
 * cleanup when a committed instruction supersedes an active one.
 */
class SupersessionTest {

    private val aircraft = AircraftId("G-TEST")
    private val t0 = SimTime.ofSeconds(10)

    @Test
    fun `TurnBase supersedes ExtendDownwind — pending readback abandoned`() {
        val beliefs = BeliefState.EMPTY.copy(
            pendingReadbacks = mapOf(
                aircraft to listOf(PendingReadback(ExtendDownwind(aircraft), t0))
            ),
        )

        val result = applySupersessionCleanup(
            beliefs,
            committedInstructions = listOf(aircraft to TurnBase(aircraft)),
        )

        assertTrue(
            result.pendingReadbacks[aircraft].isNullOrEmpty(),
            "ExtendDownwind pending should be abandoned when TurnBase is committed",
        )
    }

    @Test
    fun `non-superseding instruction leaves pending readback intact`() {
        val beliefs = BeliefState.EMPTY.copy(
            pendingReadbacks = mapOf(
                aircraft to listOf(PendingReadback(ExtendDownwind(aircraft), t0))
            ),
        )

        // HoldPosition does not supersede ExtendDownwind — no relation registered.
        val result = applySupersessionCleanup(
            beliefs,
            committedInstructions = listOf(aircraft to HoldPosition(aircraft)),
        )

        assertEquals(
            1, result.pendingReadbacks[aircraft]?.size,
            "ExtendDownwind pending should survive when a non-superseding instruction commits",
        )
    }

    @Test
    fun `supersession only affects the targeted aircraft`() {
        val other = AircraftId("G-OTHER")
        val beliefs = BeliefState.EMPTY.copy(
            pendingReadbacks = mapOf(
                aircraft to listOf(PendingReadback(ExtendDownwind(aircraft), t0)),
                other to listOf(PendingReadback(ExtendDownwind(other), t0)),
            ),
        )

        val result = applySupersessionCleanup(
            beliefs,
            committedInstructions = listOf(aircraft to TurnBase(aircraft)),
        )

        assertTrue(
            result.pendingReadbacks[aircraft].isNullOrEmpty(),
            "targeted aircraft's pending should be abandoned",
        )
        assertEquals(
            1, result.pendingReadbacks[other]?.size,
            "other aircraft's pending must not be affected",
        )
    }

    @Test
    fun `empty committed instructions returns beliefs unchanged`() {
        val beliefs = BeliefState.EMPTY.copy(
            pendingReadbacks = mapOf(
                aircraft to listOf(PendingReadback(ExtendDownwind(aircraft), t0))
            ),
        )

        val result = applySupersessionCleanup(beliefs, committedInstructions = emptyList())

        assertTrue(
            result.pendingReadbacks === beliefs.pendingReadbacks,
            "should return same reference when no instructions committed",
        )
    }

    @Test
    fun `Disregard universally abandons ALL pending readbacks for aircraft`() {
        val beliefs = BeliefState.EMPTY.copy(
            pendingReadbacks = mapOf(
                aircraft to listOf(
                    PendingReadback(ExtendDownwind(aircraft), t0),
                    PendingReadback(MaintainSpeed(aircraft, xyz.easiersaid.twr.protocol.Speed.InKnots(
                        xyz.easiersaid.twr.protocol.Knots.unsafe(160),
                    )), t0),
                ),
            ),
        )

        val result = applySupersessionCleanup(
            beliefs,
            committedInstructions = listOf(aircraft to Disregard(aircraft)),
        )

        assertTrue(
            result.pendingReadbacks[aircraft].isNullOrEmpty(),
            "Disregard should abandon ALL pending readbacks, not just specific types",
        )
    }
}
