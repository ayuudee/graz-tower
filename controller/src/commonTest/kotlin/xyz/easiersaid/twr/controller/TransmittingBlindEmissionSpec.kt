package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.CoordinationState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.controller.observe.coordinationEscalationOutputs
import xyz.easiersaid.twr.controller.observe.markCoordinationEscalationsEmitted
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TransmittingBlind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pass 12 (D-AUDIT.2.A) — TransmittingBlind emission on entry to
 * LostCommsDeclared.
 *
 * Two rows:
 *  1. Just-transitioned LostCommsDeclared (`emittedBlindAt == null`)
 *     produces one TransmittingBlind output; markCoordinationEscalationsEmitted
 *     bumps `emittedBlindAt` to `now`.
 *  2. Already-marked LostCommsDeclared (`emittedBlindAt != null`) does
 *     NOT re-emit on subsequent cycles — the marker is the gate.
 */
class TransmittingBlindEmissionSpec {

    private val ac = AircraftId("OE-ABC")
    private val rwy = RunwayId("16C")
    private val instruction = HoldShortOf(target = ac, runway = rwy)
    private val issuedAt = SimTime.ZERO
    private val now = SimTime.ofMillis(310_000)

    private fun beliefsWith(state: CoordinationState): BeliefState {
        val coord = OutstandingCoordination(
            aircraft = ac,
            instruction = instruction,
            expectedReadback = emptySet(),
            issuedAt = issuedAt,
            state = state,
        )
        return BeliefState.EMPTY.copy(coordinations = mapOf(ac to listOf(coord)))
    }

    @Test
    fun `just-transitioned LostCommsDeclared emits TransmittingBlind once and marks emittedBlindAt`() {
        val beliefs = beliefsWith(CoordinationState.LostCommsDeclared(declaredAt = now, emittedBlindAt = null))
        val outputs = coordinationEscalationOutputs(beliefs, now)
        val blinds = outputs.filterIsInstance<ControllerOutput.Respond>()
            .map { it.response }
            .filterIsInstance<TransmittingBlind>()
        assertEquals(1, blinds.size, "expected exactly one TransmittingBlind, got ${outputs.size} outputs total")
        assertEquals(instruction, blinds.single().instruction)
        assertEquals(ac, blinds.single().target)

        // markCoordinationEscalationsEmitted bumps emittedBlindAt to `now`.
        val marked = beliefs.markCoordinationEscalationsEmitted(now)
        val markedCoord = marked.coordinations.getValue(ac).single()
        val markedState = markedCoord.state
        assertTrue(markedState is CoordinationState.LostCommsDeclared)
        assertEquals(now, markedState.emittedBlindAt, "emittedBlindAt should be set after the mark pass")
    }

    @Test
    fun `already-marked LostCommsDeclared does NOT re-emit`() {
        val emittedAt = SimTime.ofMillis(now.millis - 1_000)  // earlier
        val beliefs = beliefsWith(
            CoordinationState.LostCommsDeclared(declaredAt = emittedAt, emittedBlindAt = emittedAt),
        )
        val outputs = coordinationEscalationOutputs(beliefs, now)
        val blinds = outputs.filterIsInstance<ControllerOutput.Respond>()
            .map { it.response }
            .filterIsInstance<TransmittingBlind>()
        assertTrue(blinds.isEmpty(), "TransmittingBlind must not re-emit once emittedBlindAt is set; got $blinds")
    }
}

