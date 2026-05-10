package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.bdi.ProposedAction
import xyz.easiersaid.twr.controller.certify.ActionCertifier
import xyz.easiersaid.twr.controller.certify.CertificationContext
import xyz.easiersaid.twr.controller.certify.KotlinRuntimeKernelCertifiers
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.CoordinationState
import xyz.easiersaid.twr.controller.observe.coordinationEscalationOutputs
import xyz.easiersaid.twr.controller.observe.recordCoordinations
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ConditionalClearance
import xyz.easiersaid.twr.protocol.ConditionalPredicate
import xyz.easiersaid.twr.protocol.ConfirmInstruction
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TrafficAction
import xyz.easiersaid.twr.protocol.TrafficRef
import xyz.easiersaid.twr.protocol.Urgency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ConditionalDispatchCoordinationSpec {
    private val aircraft = AircraftId("OE-ABC")
    private val runway = RunwayId("16C")
    private val condition = ConditionalPredicate.AfterTraffic(
        traffic = TrafficRef.ByCallsign(Callsign("OEXYZ")),
        action = TrafficAction.LANDING,
    )

    @Test
    fun `coordination ledger and reissue preserve conditional dispatch`() {
        val certified = ActionCertifier(KotlinRuntimeKernelCertifiers).certify(
            action = ProposedAction(
                dispatch = Dispatch.Conditional(LineUpAndWait(aircraft, runway), condition),
            ),
            context = certificationContext(),
        ).getOrNull() ?: error("conditional line-up should certify in normal visibility")
        val instruct = ControllerOutput.Instruct.fromCertified(
            certified = certified,
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("TEST-COND", "conditional test", emptyList()),
        )

        val recorded = BeliefState.EMPTY.recordCoordinations(listOf(instruct), SimTime.ZERO)
        val coord = recorded.coordinations.getValue(aircraft).single()
        assertIs<Dispatch.Conditional>(coord.dispatch)
        val readbackInstruction = assertIs<ConditionalClearance>(coord.readbackInstruction)
        assertEquals(condition, readbackInstruction.condition)

        val querying = recorded.copy(
            coordinations = mapOf(
                aircraft to listOf(
                    coord.copy(
                        state = CoordinationState.Querying(
                            queriedAt = SimTime.ZERO,
                            emittedAt = null,
                        ),
                    ),
                ),
            ),
        )
        val query = coordinationEscalationOutputs(querying, SimTime.ZERO)
            .filterIsInstance<ControllerOutput.Respond>()
            .map { it.response }
            .filterIsInstance<ConfirmInstruction>()
            .single()
        val queriedInstruction = assertIs<ConditionalClearance>(query.instruction)
        assertEquals(condition, queriedInstruction.condition)

        val reissued = recorded.copy(
            coordinations = mapOf(
                aircraft to listOf(
                    coord.copy(
                        state = CoordinationState.Reissued(
                            reissuedAt = SimTime.ZERO,
                            attemptCount = 1,
                            emittedAt = null,
                        ),
                    ),
                ),
            ),
        )
        val output = coordinationEscalationOutputs(reissued, SimTime.ZERO)
            .filterIsInstance<ControllerOutput.Instruct>()
            .single()
        val reissueDispatch = assertIs<Dispatch.Conditional>(output.dispatch)
        assertEquals(condition, reissueDispatch.condition)
    }

    private fun certificationContext(): CertificationContext {
        val observation = AircraftObservation(
            id = aircraft,
            callsign = Callsign("OEABC"),
            position = PointId("P"),
            entities = emptySet(),
            altitude = null,
            speed = null,
            onGround = true,
        )
        val view = ControllerView(
            time = SimTime.ZERO,
            controllerId = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            responsibilities = setOf(aircraft),
            aircraft = mapOf(aircraft to observation),
            runways = emptyMap(),
            activeClearances = emptyMap(),
            receivedMessages = emptyList(),
            weather = null,
            worldIndex = WorldIndex(),
        )
        return CertificationContext(
            view = view,
            beliefs = BeliefState.EMPTY.copy(
                trackedAircraft = mapOf(aircraft to observation),
                activeRunway = runway,
            ),
            world = AviationWorld(),
            decisionTime = SimTime.ZERO,
        )
    }
}
