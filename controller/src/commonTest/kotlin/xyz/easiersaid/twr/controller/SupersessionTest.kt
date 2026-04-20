package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.applySupersessionCleanup
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
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

class SupersessionTest {

    private val aircraft = AircraftId("G-TEST")
    private val t0 = SimTime.ofSeconds(10)

    private fun coord(ac: AircraftId, instr: xyz.easiersaid.twr.protocol.AtcInstruction) =
        OutstandingCoordination(ac, instr, emptySet(), t0)

    @Test
    fun `TurnBase supersedes ExtendDownwind — coordination abandoned`() {
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(aircraft to listOf(coord(aircraft, ExtendDownwind(aircraft)))),
        )
        val result = applySupersessionCleanup(beliefs, listOf(aircraft to TurnBase(aircraft)))
        assertTrue(result.pendingReadbacks[aircraft].isNullOrEmpty())
    }

    @Test
    fun `non-superseding instruction leaves coordination intact`() {
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(aircraft to listOf(coord(aircraft, ExtendDownwind(aircraft)))),
        )
        val result = applySupersessionCleanup(beliefs, listOf(aircraft to HoldPosition(aircraft)))
        assertEquals(1, result.pendingReadbacks[aircraft]?.size)
    }

    @Test
    fun `supersession only affects the targeted aircraft`() {
        val other = AircraftId("G-OTHER")
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(
                aircraft to listOf(coord(aircraft, ExtendDownwind(aircraft))),
                other to listOf(coord(other, ExtendDownwind(other))),
            ),
        )
        val result = applySupersessionCleanup(beliefs, listOf(aircraft to TurnBase(aircraft)))
        assertTrue(result.pendingReadbacks[aircraft].isNullOrEmpty())
        assertEquals(1, result.pendingReadbacks[other]?.size)
    }

    @Test
    fun `empty committed instructions returns beliefs unchanged`() {
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(aircraft to listOf(coord(aircraft, ExtendDownwind(aircraft)))),
        )
        val result = applySupersessionCleanup(beliefs, emptyList())
        assertTrue(result.coordinations === beliefs.coordinations)
    }

    @Test
    fun `Disregard universally abandons ALL coordinations for aircraft`() {
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(aircraft to listOf(
                coord(aircraft, ExtendDownwind(aircraft)),
                coord(aircraft, MaintainSpeed(aircraft, xyz.easiersaid.twr.protocol.Speed.InKnots(
                    xyz.easiersaid.twr.protocol.Knots.unsafe(160),
                ))),
            )),
        )
        val result = applySupersessionCleanup(beliefs, listOf(aircraft to Disregard(aircraft)))
        assertTrue(result.pendingReadbacks[aircraft].isNullOrEmpty())
    }
}
