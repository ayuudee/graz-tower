package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.CoordinationState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ConfirmInstruction
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pass 9 (D-AUDIT.2 / Phase 9.A.9) — integration test for the readback
 * escalation lifecycle through `controllerDecide`.
 *
 * **Why this test exists**: the spec rows in `CoordinationLifecycleSpec`
 * test the fold (`escalateOverdueCoordinations`) in isolation. They cannot
 * prove the controller actually *emits* `ConfirmInstruction` through the
 * full `controllerDecide` pipeline. Without this test, a regression that
 * advances state correctly but forgets to wire the emission would pass
 * every spec row and fail silently in production.
 *
 * **Plan deviation**: the plan named this `LowgReadbackTimeoutTest` and
 * placed it in `:sim/jvmTest`, seeded from G0's fixture with extended
 * pilot cognitive delay. The real-job version is simpler — the new
 * behaviour to integration-test is the controller-side emission, not the
 * pilot-side delay knob (which doesn't exist as a per-message dial today).
 * Driving the controller-decide pipeline directly with a hand-built
 * BeliefState + ControllerView covers the new code paths
 * (escalateOverdueCoordinations + coordinationEscalationOutputs +
 * markCoordinationEscalationsEmitted) end-to-end without scaffolding a
 * pilot-cognition fixture.
 */
class ReadbackQueryEscalationIntegrationTest {

    private val ac = AircraftId("OE-ABC")
    private val rwy = RunwayId("16C")
    private val aerodromeId = AerodromeId("LOWG")
    private val controllerId = ControllerId("LOWG_TOWER")
    private val instruction = HoldShortOf(target = ac, runway = rwy)
    private val issuedAt = SimTime.ZERO
    // Past queryAfter (10 s in Default) — escalation should advance to Querying.
    private val now = issuedAt + SimDuration.ofSeconds(11)

    private val acObservation = AircraftObservation.from(
        id = ac,
        callsign = Callsign("OEABC"),
        position = PointId("P"),
        altitude = null,
        groundSpeed = null,
        onGround = true,
        worldIndex = WorldIndex(),
    )

    private fun viewWith(time: SimTime): ControllerView = ControllerView(
        time = time,
        controllerId = controllerId,
        role = RoleName.TOWER,
        aerodromeId = aerodromeId,
        responsibilities = setOf(ac),
        aircraft = mapOf(ac to acObservation),
        runways = emptyMap(),
        activeClearances = emptyMap(),
        receivedMessages = emptyList(),
        weather = null,
        worldIndex = WorldIndex(),
    )

    private fun beliefsWithIssuedCoordination(): BeliefState {
        val coord = OutstandingCoordination(
            aircraft = ac,
            instruction = instruction,
            expectedReadback = emptySet(),
            issuedAt = issuedAt,
            state = CoordinationState.Issued,
        )
        return BeliefState.EMPTY.copy(coordinations = mapOf(ac to listOf(coord)))
    }

    @Test
    fun `controllerDecide emits ConfirmInstruction when readback is overdue and advances coordination to Querying`() {
        val beliefs = beliefsWithIssuedCoordination()
        val view = viewWith(now)

        val result = controllerDecide(view, beliefs, AviationWorld())

        // Assertion 1: the controller emitted a ConfirmInstruction for the
        // overdue coordination.
        val confirms = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .map { it.response }
            .filterIsInstance<ConfirmInstruction>()
        assertEquals(1, confirms.size, "expected exactly one ConfirmInstruction; got ${result.outputs}")
        assertEquals(instruction, confirms.single().instruction, "Confirm must reference the overdue instruction")
        assertEquals(ac, confirms.single().target)

        // Assertion 2: the coordination is now in Querying state with
        // emittedAt set (dampening the next cycle).
        val coord = result.updatedBeliefs.coordinations.getValue(ac).single()
        val state = coord.state
        assertTrue(state is CoordinationState.Querying, "expected Querying, got $state")
        assertEquals(now, state.emittedAt, "emittedAt must be set after emission to dampen next cycle")
    }

    @Test
    fun `next decide cycle does not re-emit Confirm when coordination is still in Querying`() {
        // First cycle: emit + advance to Querying.
        val beliefs0 = beliefsWithIssuedCoordination()
        val view0 = viewWith(now)
        val result0 = controllerDecide(view0, beliefs0, AviationWorld())
        val beliefs1 = result0.updatedBeliefs

        // Second cycle, slightly later but still inside the Querying window.
        val nextNow = now + SimDuration.ofSeconds(1)
        val view1 = viewWith(nextNow)
        val result1 = controllerDecide(view1, beliefs1, AviationWorld())

        val confirms = result1.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .map { it.response }
            .filterIsInstance<ConfirmInstruction>()
        assertEquals(0, confirms.size, "Confirm must NOT re-emit while coordination is still in Querying with emittedAt set")
    }
}
