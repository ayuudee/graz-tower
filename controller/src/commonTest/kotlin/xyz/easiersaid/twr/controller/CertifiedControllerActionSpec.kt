package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.bdi.TowerDepartureStage
import xyz.easiersaid.twr.controller.certify.CertificationEvidence
import xyz.easiersaid.twr.controller.certify.KernelRequirement
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.RunwayExit
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.HoldPositionCancelTakeoff
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TaxiwayId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CertifiedControllerActionSpec {
    private val aerodrome = AerodromeId("LOWG")
    private val aircraft = AircraftId("OE-ABC")
    private val conflict = AircraftId("OE-XYZ")
    private val runway = RunwayId("16C")
    private val exit = PointId("E1")

    @Test
    fun `cancel takeoff rule emits certified runway-surface instruction`() {
        val view = baseView(
            aircraft = mapOf(
                aircraft to runwayObservation(aircraft, "OEABC"),
                conflict to runwayObservation(conflict, "OEXYZ"),
            ),
            runways = mapOf(
                runway to RunwayObservation(
                    id = runway,
                    status = RunwayStatus.OCCUPIED_CROSSING,
                    occupants = setOf(aircraft, conflict),
                ),
            ),
        )
        val previous = BeliefState.EMPTY.copy(
            activeRunway = runway,
            commitments = mapOf(
                aircraft to Commitment(
                    aircraft = aircraft,
                    kind = CommitmentKind.TOWER_DEPARTURE,
                    stage = TowerDepartureStage.AwaitTakeoffObserved,
                    runway = runway,
                    formedAt = SimTime.ZERO,
                    contacted = true,
                ),
            ),
        )

        val result = controllerDecide(view, previous, AviationWorld())
        val instruction = singleInstruction(result)

        assertIs<HoldPositionCancelTakeoff>(instruction.instruction)
        assertEquals(setOf(KernelRequirement.Runway, KernelRequirement.Surface), instruction.kernelRequirements())
        assertEquals(
            instruction.certificationEvidence,
            result.updatedBeliefs.coordinations.getValue(aircraft).single().certificationEvidence,
            "coordination ledger must preserve the emitted certification evidence",
        )
    }

    @Test
    fun `vacate rule emits certified runway-surface instruction`() {
        val view = baseView(
            aircraft = mapOf(aircraft to runwayObservation(aircraft, "OEABC")),
            runways = mapOf(
                runway to RunwayObservation(
                    id = runway,
                    status = RunwayStatus.OCCUPIED_LANDING,
                    occupants = setOf(aircraft),
                ),
            ),
        )
        val previous = BeliefState.EMPTY.copy(
            activeRunway = runway,
            commitments = mapOf(
                aircraft to Commitment(
                    aircraft = aircraft,
                    kind = CommitmentKind.TOWER_ARRIVAL,
                    stage = TowerArrivalStage.AwaitLandedObserved,
                    runway = runway,
                    formedAt = SimTime.ZERO,
                    contacted = true,
                ),
            ),
        )

        val result = controllerDecide(view, previous, worldWithExit())
        val instruction = singleInstruction(result)

        val vacate = assertIs<AfterLandingVacateVia>(instruction.instruction)
        assertEquals(exit, vacate.exit)
        assertEquals(setOf(KernelRequirement.Runway, KernelRequirement.Surface), instruction.kernelRequirements())
        assertEquals(
            instruction.certificationEvidence,
            result.updatedBeliefs.coordinations.getValue(aircraft).single().certificationEvidence,
            "coordination ledger must preserve the emitted certification evidence",
        )
    }

    private fun singleInstruction(result: ControllerDecisionResult): ControllerOutput.Instruct {
        val instructs = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
        assertEquals(1, instructs.size, "Expected one instruction; got ${result.outputs}")
        return instructs.single()
    }

    private fun ControllerOutput.Instruct.kernelRequirements(): Set<KernelRequirement> =
        certificationEvidence.all.mapNotNull {
            (it as? CertificationEvidence.KernelBacked)?.requirement
        }.toSet()

    private fun baseView(
        aircraft: Map<AircraftId, AircraftObservation>,
        runways: Map<RunwayId, RunwayObservation>,
    ): ControllerView = ControllerView(
        time = SimTime.ofMillis(10_000),
        controllerId = ControllerId("LOWG_TWR"),
        role = RoleName.TOWER,
        aerodromeId = aerodrome,
        responsibilities = setOf(this.aircraft),
        aircraft = aircraft,
        runways = runways,
        activeClearances = emptyMap(),
        receivedMessages = emptyList(),
        weather = null,
        worldIndex = WorldIndex(),
        flightStripIntents = mapOf(this.aircraft to AircraftIntent.Arriving),
    )

    private fun runwayObservation(id: AircraftId, callsign: String): AircraftObservation =
        AircraftObservation(
            id = id,
            callsign = Callsign(callsign),
            position = PointId("RWY-16C"),
            entities = setOf(EntityRef.RunwayRef(runway)),
            altitude = null,
            speed = null,
            onGround = true,
        )

    private fun worldWithExit(): AviationWorld {
        val runwayModel = Runway(
            id = runway,
            path = Path(listOf(PointId("T"), PointId("D"))),
            threshold = PointId("T"),
            exits = listOf(RunwayExit(point = exit, taxiway = TaxiwayId("A"))),
        )
        val aerodromeModel = Aerodrome(
            icao = aerodrome,
            elevation = Feet(0),
            magneticVariation = Degrees(0.0),
            transitionAltitude = Level.AltitudeFeet.unsafe(5000),
            runways = mapOf(runway to runwayModel),
        )
        return AviationWorld(aerodromes = mapOf(aerodrome to aerodromeModel))
    }
}
