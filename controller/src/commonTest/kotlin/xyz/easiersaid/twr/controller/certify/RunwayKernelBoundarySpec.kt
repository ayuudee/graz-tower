package xyz.easiersaid.twr.controller.certify

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.RunwayObservation
import xyz.easiersaid.twr.controller.RunwayStatus
import xyz.easiersaid.twr.controller.assess.RunwayOperation
import xyz.easiersaid.twr.controller.assess.RunwayDutyState
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RunwayKernelBoundarySpec {
    private val aircraft = AircraftId("OEABC")
    private val other = AircraftId("OEXYZ")
    private val runway = RunwayId("16C")

    @Test
    fun `runway kernel operations declare their required duty operation`() {
        val operations = listOf(
            RunwayKernelOperation.LineUpAndWait(aircraft, runway) to RunwayOperation.DEPARTURE,
            RunwayKernelOperation.ClearedForTakeoff(aircraft, runway) to RunwayOperation.DEPARTURE,
            RunwayKernelOperation.ClearedToLand(aircraft, runway) to RunwayOperation.ARRIVAL,
        )

        operations.forEach { (operation, expectedDuty) ->
            assertEquals(aircraft, operation.aircraft)
            assertEquals(runway, operation.runway)
            assertEquals(expectedDuty, operation.requiredDutyOperation)
        }
    }

    @Test
    fun `runway kernel accepts line-up with all primitive evidence`() {
        val decision = KotlinRunwayKernel.evaluate(input())

        val accepted = assertIs<RunwayKernelDecision.Accepted>(decision)
        assertEquals(
            setOf(
                RunwayKernelEvidence.ActiveRunwayMatched::class,
                RunwayKernelEvidence.ControllerHeldRunwayAuthority::class,
                RunwayKernelEvidence.RunwayOccupancyCompatible::class,
                RunwayKernelEvidence.AircraftPhaseCompatible::class,
            ),
            accepted.evidence.all.map { it::class }.toSet(),
        )
    }

    @Test
    fun `runway kernel rejects missing active runway`() {
        val decision = KotlinRunwayKernel.evaluate(input(activeRunway = null))
        val rejected = assertIs<RunwayKernelDecision.Rejected>(decision)
        assertIs<RunwayKernelFailure.MissingActiveRunway>(rejected.failure)
    }

    @Test
    fun `runway kernel rejects active runway mismatch`() {
        val decision = KotlinRunwayKernel.evaluate(input(activeRunway = RunwayId("34C")))
        val rejected = assertIs<RunwayKernelDecision.Rejected>(decision)
        assertIs<RunwayKernelFailure.ActiveRunwayMismatch>(rejected.failure)
    }

    @Test
    fun `runway kernel rejects missing runway authority`() {
        val decision = KotlinRunwayKernel.evaluate(input(runwayDuty = null))
        val rejected = assertIs<RunwayKernelDecision.Rejected>(decision)
        assertIs<RunwayKernelFailure.MissingRunwayDuty>(rejected.failure)
    }

    @Test
    fun `runway kernel rejects authority held for another aircraft`() {
        val decision = KotlinRunwayKernel.evaluate(
            input(
                runwayDuty = RunwayDutyState(
                    runway = runway,
                    holder = other,
                    operation = RunwayOperation.DEPARTURE,
                ),
            ),
        )
        val rejected = assertIs<RunwayKernelDecision.Rejected>(decision)
        assertIs<RunwayKernelFailure.RunwayAuthorityMismatch>(rejected.failure)
    }

    @Test
    fun `runway kernel rejects missing runway observation`() {
        val decision = KotlinRunwayKernel.evaluate(input(runwayObservation = null))
        val rejected = assertIs<RunwayKernelDecision.Rejected>(decision)
        assertIs<RunwayKernelFailure.MissingRunwayObservation>(rejected.failure)
    }

    @Test
    fun `runway kernel rejects occupancy by other traffic`() {
        val decision = KotlinRunwayKernel.evaluate(
            input(
                runwayObservation = RunwayObservation(
                    id = runway,
                    status = RunwayStatus.OCCUPIED_LANDING,
                    occupants = setOf(other),
                ),
            ),
        )
        val rejected = assertIs<RunwayKernelDecision.Rejected>(decision)
        assertIs<RunwayKernelFailure.IncompatibleRunwayOccupancy>(rejected.failure)
    }

    @Test
    fun `runway kernel rejects aircraft phase mismatch`() {
        val decision = KotlinRunwayKernel.evaluate(input(onGround = false))
        val rejected = assertIs<RunwayKernelDecision.Rejected>(decision)
        assertIs<RunwayKernelFailure.IncompatibleAircraftPhase>(rejected.failure)
    }

    private fun input(
        operation: RunwayKernelOperation = RunwayKernelOperation.LineUpAndWait(aircraft, runway),
        activeRunway: RunwayId? = runway,
        runwayDuty: RunwayDutyState? = RunwayDutyState(
            runway = runway,
            holder = aircraft,
            operation = operation.requiredDutyOperation,
        ),
        runwayObservation: RunwayObservation? = RunwayObservation(
            id = runway,
            status = RunwayStatus.CLEAR,
            occupants = emptySet(),
        ),
        onGround: Boolean = true,
    ): RunwayKernelInput {
        val observation = AircraftObservation(
            id = aircraft,
            callsign = Callsign("OEABC"),
            position = PointId("P"),
            coords = Position(xMeters = 0.0, yMeters = 0.0),
            entities = emptySet(),
            altitude = null,
            speed = null,
            onGround = onGround,
        )
        val runwayBeliefs = if (runwayObservation == null) emptyMap() else mapOf(runway to runwayObservation)
        val view = ControllerView(
            time = SimTime.ZERO,
            controllerId = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            responsibilities = setOf(aircraft),
            aircraft = mapOf(aircraft to observation),
            runways = runwayBeliefs,
            activeClearances = emptyMap(),
            receivedMessages = emptyList(),
            weather = null,
            worldIndex = WorldIndex(),
        )
        return RunwayKernelInput(
            operation = operation,
            context = CertificationContext(
                view = view,
                beliefs = BeliefState.EMPTY.copy(
                    trackedAircraft = mapOf(aircraft to observation),
                    activeRunway = activeRunway,
                    runwayDuty = runwayDuty,
                    runwayBeliefs = runwayBeliefs,
                ),
                world = AviationWorld(),
                decisionTime = SimTime.ZERO,
            ),
        )
    }
}
