package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.CoordinationState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.controller.observe.updateBeliefs
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pass 12 (D-AUDIT.2.B) — coordinations cleanup on aircraft departure.
 *
 * Three rows pin the narrow-prune contract:
 *  1. LostCommsDeclared on a fully-departed aircraft is pruned.
 *  2. LostCommsDeclared on an aircraft still in `view.responsibilities`
 *     is preserved (mid-handoff windows kept clean).
 *  3. Issued / Querying / Reissued on a departed aircraft are NOT pruned
 *     (a late readback could still resolve them).
 */
class CoordinationsCleanupSpec {

    private val ac = AircraftId("OE-ABC")
    private val rwy = RunwayId("16C")
    private val now = SimTime.ZERO
    private val instruction = HoldShortOf(target = ac, runway = rwy)

    private fun beliefsWith(state: CoordinationState): BeliefState {
        val coord = OutstandingCoordination(
            aircraft = ac,
            dispatch = xyz.easiersaid.twr.controller.bdi.Dispatch.Direct(instruction),
            certificationEvidence = testCertificationEvidence(),
            expectedReadback = emptySet(),
            issuedAt = now,
            state = state,
        )
        return BeliefState.EMPTY.copy(coordinations = mapOf(ac to listOf(coord)))
    }

    /** Build a ControllerView with the named aircraft slices. */
    private fun viewWith(
        observed: Map<AircraftId, AircraftObservation>,
        responsibilities: Set<AircraftId>,
    ): ControllerView = ControllerView(
        time = now,
        controllerId = ControllerId("LOWG_GROUND"),
        role = RoleName.GROUND,
        aerodromeId = AerodromeId("LOWG"),
        responsibilities = responsibilities,
        aircraft = observed,
        runways = emptyMap(),
        activeClearances = emptyMap(),
        receivedMessages = emptyList(),
        weather = null,
        worldIndex = WorldIndex(),
    )

    private fun observation(): AircraftObservation = AircraftObservation.from(
        id = ac,
        callsign = Callsign("OEABC"),
        position = PointId("P"),
        altitude = null,
        groundSpeed = null,
        onGround = true,
        wakeCategory = null,
        icaoTypeDesignator = null,
        worldIndex = WorldIndex(),
    )

    @Test
    fun `LostCommsDeclared on fully-departed aircraft is pruned`() {
        val priorBeliefs = beliefsWith(CoordinationState.LostCommsDeclared(declaredAt = now, emittedBlindAt = now))
        val view = viewWith(observed = emptyMap(), responsibilities = emptySet())
        val updated = updateBeliefs(priorBeliefs, view)
        assertTrue(
            ac !in updated.coordinations,
            "LostCommsDeclared for fully-departed aircraft must be pruned; got ${updated.coordinations}",
        )
    }

    @Test
    fun `LostCommsDeclared on aircraft still in responsibilities is preserved`() {
        val priorBeliefs = beliefsWith(CoordinationState.LostCommsDeclared(declaredAt = now, emittedBlindAt = now))
        val view = viewWith(observed = emptyMap(), responsibilities = setOf(ac))
        val updated = updateBeliefs(priorBeliefs, view)
        assertEquals(
            1,
            updated.coordinations[ac]?.size,
            "LostCommsDeclared must be preserved while ac is still in responsibilities",
        )
    }

    @Test
    fun `Issued or Reissued on departed aircraft is NOT pruned (late readback may resolve)`() {
        val states = listOf(
            CoordinationState.Issued,
            CoordinationState.Querying(queriedAt = now, emittedAt = now),
            CoordinationState.Reissued(reissuedAt = now, attemptCount = 1, emittedAt = now),
        )
        for (s in states) {
            val priorBeliefs = beliefsWith(s)
            val view = viewWith(observed = emptyMap(), responsibilities = emptySet())
            val updated = updateBeliefs(priorBeliefs, view)
            assertEquals(
                1,
                updated.coordinations[ac]?.size,
                "$s must NOT be pruned on departure — late readback may still resolve it",
            )
        }
    }
}
