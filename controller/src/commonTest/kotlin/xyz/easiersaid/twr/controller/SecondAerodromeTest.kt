package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Smoke test for the second (right-hand circuit) test aerodrome.
 *
 * Validates that world-building and the controller pipeline work with a
 * non-default circuit direction, catching any baked-in LEFT_HAND assumptions.
 */
class SecondAerodromeTest {

    @Test
    fun `right-hand circuit world builds and controller runs without error`() {
        val world = testWorldB()
        val aerodrome = world.aerodromes[TestIdsB.aerodrome]
        assertNotNull(aerodrome, "LOWI aerodrome should exist")

        val circuit = aerodrome.circuits[TestIdsB.circuit]
        assertNotNull(circuit, "08-RH circuit should exist")
        assertEquals(CircuitDirection.RIGHT_HAND, circuit.direction)

        // Run a single empty cycle — no aircraft, just verify the pipeline doesn't crash.
        val worldIndex = world.aerodromes.values.first().let { ad ->
            // Build a minimal WorldIndex for the second aerodrome
            val positions = world.geometry.points
            val entitiesByPoint = mapOf(
                TestIdsB.rwyThreshold to setOf(
                    xyz.easiersaid.twr.core.world.EntityRef.RunwayRef(TestIdsB.runway08),
                    xyz.easiersaid.twr.core.world.EntityRef.CircuitProcedureRef(TestIdsB.circuit),
                ),
                TestIdsB.holdShort to setOf(
                    xyz.easiersaid.twr.core.world.EntityRef.TaxiwayRef(TestIdsB.taxiwayB),
                ),
            )
            xyz.easiersaid.twr.core.world.WorldIndex(
                positions = positions,
                adjacency = emptyMap(),
                entitiesByPoint = entitiesByPoint,
                holdingPointsByRunway = mapOf(TestIdsB.runway08 to setOf(TestIdsB.holdShort)),
                circuitLegsByPoint = emptyMap(),
            )
        }

        val view = ControllerView(
            time = SimTime.ofSeconds(0),
            controllerId = TestIdsB.controller,
            role = RoleName.TOWER,
            aerodromeId = TestIdsB.aerodrome,
            responsibilities = emptySet(),
            aircraft = emptyMap(),
            runways = mapOf(
                TestIdsB.runway08 to RunwayObservation(TestIdsB.runway08, RunwayStatus.CLEAR, emptySet()),
            ),
            activeClearances = emptyMap(),
            receivedMessages = emptyList(),
            weather = null,
            pendingInboundHandoffs = emptyList(),
            worldIndex = worldIndex,
        )

        val result = controllerDecide(view, BeliefState.EMPTY, world)
        assertNotNull(result, "controller should produce a result for the second aerodrome")
    }
}
