package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GroundTaxiTest {

    private val worldIndex = testWorldIndex()
    private val world = testWorld()

    private fun groundView(
        aircraft: Map<AircraftId, AircraftObservation>,
        time: SimTime = SimTime.ofSeconds(0),
        receivedMessages: List<ReceivedMessage> = emptyList(),
    ): ControllerView = ControllerView(
        time = time,
        controllerId = ControllerId("GND-1"),
        role = RoleName.GROUND,
        aerodromeId = TestIds.aerodrome,
        responsibilities = aircraft.keys,
        aircraft = aircraft,
        runways = mapOf(TestIds.runway09 to RunwayObservation(TestIds.runway09, RunwayStatus.CLEAR, emptySet())),
        activeClearances = emptyMap(),
        receivedMessages = receivedMessages,
        weather = null,
        pendingInboundHandoffs = emptyList(),
        worldIndex = worldIndex,
    )

    @Test
    fun `ground departure — taxi to holding point`() {
        var beliefs = BeliefState.EMPTY

        val ac = aircraftAt(TestIds.acAlpha, TestIds.standPoint, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val view = groundView(
            aircraft = mapOf(TestIds.acAlpha to ac),
            time = SimTime.ofSeconds(10),
        )
        val result = controllerDecide(view, beliefs, world)

        val instruct = result.outputs.filterIsInstance<ControllerOutput.Instruct>().firstOrNull()
        assertNotNull(instruct, "Should issue taxi instruction")
        assertTrue(instruct.instruction is TaxiTo, "Should issue TaxiTo, got ${instruct.instruction::class.simpleName}")
        assertTrue(instruct.trace.regulations.any { it.document == "ICAO_9432" },
            "Should cite ICAO 9432 for taxi phraseology")

        val taxi = instruct.instruction as TaxiTo
        assertEquals(TestIds.holdShort, taxi.destination, "Destination should be the holding point")
    }

    @Test
    fun `ground departure — handoff to tower at holding point`() {
        var beliefs = BeliefState.EMPTY

        // Cycle 1: taxi issued from stand
        val ac1 = aircraftAt(TestIds.acAlpha, TestIds.standPoint, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val view1 = groundView(aircraft = mapOf(TestIds.acAlpha to ac1), time = SimTime.ofSeconds(10))
        beliefs = controllerDecide(view1, beliefs, world).updatedBeliefs

        // Cycle 2: aircraft now at holding point
        val ac2 = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val view2 = groundView(aircraft = mapOf(TestIds.acAlpha to ac2), time = SimTime.ofSeconds(20))
        val result2 = controllerDecide(view2, beliefs, world)

        val instruct = result2.outputs.filterIsInstance<ControllerOutput.Instruct>().firstOrNull()
        assertNotNull(instruct, "Should issue handoff")
        assertTrue(instruct.instruction is ContactFrequency, "Should issue ContactFrequency, got ${instruct.instruction::class.simpleName}")
        assertTrue(instruct.trace.regulations.any { it.document == "ICAO_4444" && it.section == "§10.1" },
            "Should cite ICAO 4444 §10.1 for transfer of communications")
    }

    /**
     * The handoff guard is [NoPendingReadback], which blocks re-firing while a
     * ContactFrequency is still in flight. The pending register GC horizon doubles
     * as a retransmit timer: if no readback arrives within [MAX_READBACK_AGE] the
     * pending entry ages out and the rule fires again ("how copy?" — CAP 413 §2.7).
     */
    @Test
    fun `handoff retransmits after pending-readback GC when pilot is silent`() {
        var beliefs = BeliefState.EMPTY
        val acAtHold = aircraftAt(TestIds.acAlpha, TestIds.holdShort, worldIndex, onGround = true, goal = PilotGoal.DEPART)

        // Cycle 1 (t=10s): handoff fires.
        val r1 = controllerDecide(
            groundView(mapOf(TestIds.acAlpha to acAtHold), time = SimTime.ofSeconds(10)),
            beliefs, world,
        )
        beliefs = r1.updatedBeliefs
        assertTrue(
            r1.outputs.filterIsInstance<ControllerOutput.Instruct>()
                .any { it.instruction is ContactFrequency },
            "Cycle 1 should emit ContactFrequency",
        )

        // Cycle 2 (t=20s): pending still in flight — rule must not re-fire.
        val r2 = controllerDecide(
            groundView(mapOf(TestIds.acAlpha to acAtHold), time = SimTime.ofSeconds(20)),
            beliefs, world,
        )
        beliefs = r2.updatedBeliefs
        assertTrue(
            r2.outputs.filterIsInstance<ControllerOutput.Instruct>()
                .none { it.instruction is ContactFrequency },
            "Cycle 2 must not duplicate handoff while ContactFrequency is pending",
        )

        // Cycle 3 (t=45s): pending aged past MAX_READBACK_AGE (30s) — retransmit.
        val r3 = controllerDecide(
            groundView(mapOf(TestIds.acAlpha to acAtHold), time = SimTime.ofSeconds(45)),
            beliefs, world,
        )
        assertTrue(
            r3.outputs.filterIsInstance<ControllerOutput.Instruct>()
                .any { it.instruction is ContactFrequency },
            "Cycle 3 should retransmit handoff after pending aged out",
        )
    }

    @Test
    fun `all ground instructions carry regulation traces`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.standPoint, worldIndex, onGround = true, goal = PilotGoal.DEPART)
        val view = groundView(aircraft = mapOf(TestIds.acAlpha to ac))
        val result = controllerDecide(view, BeliefState.EMPTY, world)

        for (output in result.outputs.filterIsInstance<ControllerOutput.Instruct>()) {
            assertTrue(output.trace.regulations.isNotEmpty(),
                "Instruction ${output.instruction::class.simpleName} should have regulation trace")
        }
    }

    @Test
    fun `arriving aircraft at stand does not get departure taxi`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.standPoint, worldIndex, onGround = true, goal = PilotGoal.ARRIVE)
        val view = groundView(aircraft = mapOf(TestIds.acAlpha to ac))
        val result = controllerDecide(view, BeliefState.EMPTY, world)

        val instructs = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
        val taxiToHolding = instructs.firstOrNull {
            val instr = it.instruction
            instr is TaxiTo && instr.destination == TestIds.holdShort
        }
        assertTrue(taxiToHolding == null,
            "Arriving aircraft should not get taxi to holding point")
    }

    @Test
    fun `human pilot without taxi request does not get taxi`() {
        val ac = aircraftAt(TestIds.acAlpha, TestIds.standPoint, worldIndex, onGround = true,
            goal = PilotGoal.DEPART, humanPiloted = true)
        val view = groundView(
            aircraft = mapOf(TestIds.acAlpha to ac),
            receivedMessages = emptyList(), // no taxi request
        )
        val result = controllerDecide(view, BeliefState.EMPTY, world)

        val instructs = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
        val taxi = instructs.firstOrNull { it.instruction is TaxiTo }
        assertTrue(taxi == null,
            "Human pilot should not get taxi without request")
    }

    /**
     * Regression for Bug C (2026-04-16): when the adjacency graph has no path from the
     * aircraft to the holding point, the controller must NOT emit a bogus TaxiTo with
     * empty via. Before the fix, findRoute returned emptyList() for both "direct" and
     * "no path", so a disconnected graph produced TaxiTo(destination, via=[]).
     */
    @Test
    fun `disconnected graph — no taxi instruction emitted`() {
        // Build a world where the stand is an island: it has no adjacency, so there is
        // no route from standPoint to holdShort.
        val disconnected = WorldIndex(
            positions = mapOf(
                TestIds.standPoint to Position(-150.0, 100.0),
                TestIds.holdShort to Position(-30.0, 50.0),
                TestIds.rwyThreshold to Position(0.0, 0.0),
            ),
            adjacency = mapOf(
                // standPoint deliberately not present — island.
                TestIds.holdShort to setOf(TestIds.rwyThreshold),
                TestIds.rwyThreshold to setOf(TestIds.holdShort),
            ),
            entitiesByPoint = mapOf(
                TestIds.standPoint to setOf(EntityRef.StandRef(TestIds.stand1)),
                TestIds.holdShort to setOf(EntityRef.TaxiwayRef(TestIds.taxiwayA)),
                TestIds.rwyThreshold to setOf(EntityRef.RunwayRef(TestIds.runway09)),
            ),
            holdingPointsByRunway = mapOf(
                TestIds.runway09 to setOf(TestIds.holdShort),
            ),
            circuitLegsByPoint = emptyMap(),
        )

        val ac = aircraftAt(TestIds.acAlpha, TestIds.standPoint, disconnected,
            onGround = true, goal = PilotGoal.DEPART)
        val view = ControllerView(
            time = SimTime.ofSeconds(10),
            controllerId = ControllerId("GND-1"),
            role = RoleName.GROUND,
            aerodromeId = TestIds.aerodrome,
            responsibilities = setOf(TestIds.acAlpha),
            aircraft = mapOf(TestIds.acAlpha to ac),
            runways = mapOf(TestIds.runway09 to RunwayObservation(TestIds.runway09, RunwayStatus.CLEAR, emptySet())),
            activeClearances = emptyMap(),
            receivedMessages = emptyList(),
            weather = null,
            pendingInboundHandoffs = emptyList(),
            worldIndex = disconnected,
        )
        val result = controllerDecide(view, BeliefState.EMPTY, world)

        val taxi = result.outputs
            .filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is TaxiTo }
        assertTrue(taxi == null,
            "Disconnected graph must not produce a TaxiTo; got: ${taxi?.instruction}")
    }
}
