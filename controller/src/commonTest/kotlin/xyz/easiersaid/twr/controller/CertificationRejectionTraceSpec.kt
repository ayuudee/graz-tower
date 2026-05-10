package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.TowerDepartureStage
import xyz.easiersaid.twr.controller.assess.RunwayDutyState
import xyz.easiersaid.twr.controller.assess.RunwayOperation
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CertificationRejectionTraceSpec {
    private val aircraft = AircraftId("OE-ABC")
    private val activeRunway = RunwayId("16C")
    private val staleCommitmentRunway = RunwayId("34C")

    @Test
    fun `procedure action rejected by certification appears in skipped trace`() {
        val view = ControllerView(
            time = SimTime.ofMillis(10_000),
            controllerId = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            responsibilities = setOf(aircraft),
            aircraft = mapOf(
                aircraft to observation(aircraft, "OEABC"),
            ),
            runways = mapOf(
                activeRunway to RunwayObservation(
                    id = activeRunway,
                    status = RunwayStatus.CLEAR,
                    occupants = emptySet(),
                ),
            ),
            activeClearances = emptyMap(),
            receivedMessages = emptyList(),
            weather = null,
            worldIndex = WorldIndex(),
            flightStripIntents = mapOf(aircraft to AircraftIntent.Departing),
        )
        val previous = BeliefState.EMPTY.copy(
            activeRunway = activeRunway,
            runwayDuty = RunwayDutyState(
                runway = activeRunway,
                holder = aircraft,
                operation = RunwayOperation.DEPARTURE,
            ),
            commitments = mapOf(
                aircraft to Commitment(
                    aircraft = aircraft,
                    kind = CommitmentKind.TOWER_DEPARTURE,
                    stage = TowerDepartureStage.AwaitLineUpObserved,
                    runway = staleCommitmentRunway,
                    formedAt = SimTime.ZERO,
                    contacted = true,
                ),
            ),
        )

        val result = controllerDecide(view, previous, AviationWorld())
        val skipped = result.trace.skippedActions.singleOrNull {
            it.aircraft == aircraft && it.reason.contains("certification rejected")
        }

        check(skipped != null) {
            "Certification rejection should be visible in skippedActions; got ${result.trace.skippedActions}"
        }
        assertTrue(
            skipped.ruleTraces.any {
                it.contains("DEP-TAKEOFF") &&
                    it.contains("Runway rejected") &&
                    it.contains("Instruction runway $staleCommitmentRunway does not match active runway $activeRunway")
            },
            "Trace should name the rule and kernel rejection; got ${skipped.ruleTraces}",
        )
        assertTrue(
            result.outputs.filterIsInstance<ControllerOutput.Instruct>().isEmpty(),
            "Rejected instruction must not be emitted; got ${result.outputs}",
        )
        val commitment = result.updatedBeliefs.commitments.getValue(aircraft)
        assertEquals(
            TowerDepartureStage.AwaitLineUpObserved,
            commitment.stage,
            "Rejected instruction must not advance the departure commitment",
        )
    }

    private fun observation(id: AircraftId, callsign: String): AircraftObservation =
        AircraftObservation(
            id = id,
            callsign = Callsign(callsign),
            position = PointId("RWY-34C"),
            entities = setOf(EntityRef.RunwayRef(staleCommitmentRunway)),
            altitude = null,
            speed = null,
            onGround = true,
        )
}
