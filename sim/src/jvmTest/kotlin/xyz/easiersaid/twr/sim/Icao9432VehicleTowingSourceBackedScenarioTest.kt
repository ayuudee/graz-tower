package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.fail
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.testing.SimTrace
import xyz.easiersaid.twr.sim.testing.TraceStep
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

class Icao9432VehicleTowingSourceBackedScenarioTest {
    @Test
    fun `tow request identifies aircraft under tow and states type and operator`() {
        sourceUnitSpec("icao9432-vehicle-tow-request-structured-metadata") {
            title("Tow request identifies aircraft under tow and carries type and operator metadata")
            sourceUnits(
                listOf(
                    SourceUnitRef(
                        "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605",
                    ),
                    SourceUnitRef(
                        "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71",
                    ),
                ),
            )
            domain("tow-awareness", setOf("aircraft-under-tow-stated-to-receiving-station"))
            domain("tow-metadata", setOf("aircraft-type-and-operator-present"))
            domain("phraseology-boundary", setOf("structured-content-not-rendered-wording"))

            witness("driver request to receiving station carries tow metadata") {
                val scenario = vehicleTowingScenario(
                    initialVehicle = baseVehicle(),
                    radios = listOf(
                        VehicleRadio.Driver(
                            at = 0,
                            receiver = RECEIVING_STATION,
                            transmission = VehicleDriverTransmission.RequestTow(
                                vehicle = VEHICLE,
                                callsign = CALLSIGN,
                                receivingStation = RECEIVING_STATION,
                                tow = TOW,
                            ),
                        ),
                    ),
                )
                val (_, records, trace) = runUntilWithStateTrace(
                    scenario.initialState,
                    scenario.events,
                    SimTime.ZERO + SimDuration.ofSeconds(10),
                )

                val requestStep = trace.afterDriverTransmission<VehicleDriverTransmission.RequestTow>()
                val request = requestStep.driverTransmission<VehicleDriverTransmission.RequestTow>()
                check(request.receivingStation == RECEIVING_STATION) {
                    "Tow request did not identify receiving station: $request"
                }
                check(
                    (requestStep.event as SimEvent.TransmissionReceptionObserved).transmission.receiver ==
                        ReceiverRef.Controller(RECEIVING_STATION),
                ) {
                    "Tow request was not addressed to the receiving station"
                }
                check(request.tow.aircraft == TOWED_AIRCRAFT) {
                    "Tow request did not identify aircraft under tow: $request"
                }
                hit("aircraft-under-tow-stated-to-receiving-station")

                check(request.tow.aircraftType == AircraftType.B738) {
                    "Tow request did not carry aircraft type: $request"
                }
                check(request.tow.operator == OPERATOR) {
                    "Tow request did not carry operator where applicable: $request"
                }
                val vehicle = requestStep.state.vehicles.getValue(VEHICLE)
                check(vehicle.activeTow?.metadata == TOW) {
                    "Tow metadata was not recorded in active vehicle state: $vehicle"
                }
                hit("type-and-operator-structured-metadata")

                val report = simEvidence("icao9432-vehicle-tow-request-rendered-wording") {
                    observe {
                        EvidenceFactAdapters.fromTransmissionRecords(
                            scenarioId = "icao9432-vehicle-tow-request-rendered-wording",
                            records = records,
                            diagnostic = "Vehicle tow-request rendered phraseology",
                        )
                    }
                    source("vehicle tow request rendered wording") {
                        cites(ICAO9432.VehiclesAndTowing.TowRequestStatesAircraftTypeAndOperator)
                        sample("vehicle", VEHICLE.value)
                        sample("receiving-station", RECEIVING_STATION.value)
                        sample("aircraft", TOWED_AIRCRAFT.value)
                        sample("aircraft-type", AircraftType.B738.icaoDesignator.raw)
                        sample("operator", OPERATOR.value)
                        expect {
                            renderedVehicleDriverPhraseology(VEHICLE).towRequest(
                                callsign = CALLSIGN,
                                receivingStation = RECEIVING_STATION,
                                aircraft = TOWED_AIRCRAFT,
                                aircraftType = AircraftType.B738,
                                operator = OPERATOR,
                            )
                        }
                    }
                }
                report.assertNoFailures()
                hit("type-and-operator-rendered-wording")

                requireHits("aircraft-under-tow-stated-to-receiving-station")
                requireHits("type-and-operator-structured-metadata")
                requireHits("type-and-operator-rendered-wording")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `vehicle and tow runway vacated report waits until both are clear beyond holding point`() {
        sourceUnitSpec("icao9432-vehicle-tow-runway-vacated-after-combined-clear") {
            title("Vehicle and tow runway-vacated report waits until both are clear beyond holding point")
            sourceUnit(
                SourceUnitRef(
                    "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b",
                ),
            )
            domain("extent-scope", setOf("vehicle-and-tow"))
            domain("vacated-report-timing", setOf("after-vehicle-and-tow-clear-beyond-holding-point"))

            witness("active tow reports runway vacated only after vehicle and tow clear evidence") {
                val scenario = vehicleTowingScenario(
                    initialVehicle = baseVehicle().copy(
                        runwayState = VehicleRunwayState.OnRunway(RUNWAY),
                        activeTow = activeTow(),
                    ),
                    radios = listOf(
                        VehicleRadio.Controller(
                            at = 0,
                            transmission = VehicleControllerTransmission.VacateRunway(VEHICLE, RUNWAY),
                        ),
                        VehicleRadio.Driver(
                            at = 10,
                            receiver = GROUND,
                            transmission = VehicleDriverTransmission.RunwayVacated(VEHICLE, RUNWAY),
                        ),
                    ),
                )
                val (_, _, trace) = runUntilWithStateTrace(
                    scenario.initialState,
                    scenario.events,
                    SimTime.ZERO + SimDuration.ofSeconds(30),
                )

                val vehicleClear = trace.steps.firstOrNull { step ->
                    step.event is SimEvent.VehicleClearBeyondHoldingPoint &&
                        (step.event as SimEvent.VehicleClearBeyondHoldingPoint).vehicleId == VEHICLE
                } ?: fail("Vehicle never reached clear-beyond-holding-point state.")
                hit("vehicle-clear-beyond-holding-point")

                val towClear = trace.steps.firstOrNull { step ->
                    step.event is SimEvent.TowClearBeyondHoldingPoint &&
                        (step.event as SimEvent.TowClearBeyondHoldingPoint).vehicleId == VEHICLE
                } ?: fail("Tow never reached clear-beyond-holding-point state.")
                val towClearVehicle = towClear.state.vehicles.getValue(VEHICLE)
                check(towClearVehicle.activeTow?.clearBeyondHoldingPoint?.runway == RUNWAY) {
                    "Tow clear event did not produce tow clear evidence: $towClearVehicle"
                }
                hit("tow-clear-beyond-holding-point")

                val vacated = trace.afterDriverTransmission<VehicleDriverTransmission.RunwayVacated>()
                check(vehicleClear.time < vacated.time && towClear.time < vacated.time) {
                    "Runway vacated report occurred before vehicle and tow clear evidence"
                }
                check(vacated.state.vehicles.getValue(VEHICLE).runwayState == VehicleRunwayState.OffRunway) {
                    "Runway vacated report did not clear runway state"
                }
                hit("runway-vacated-after-vehicle-and-tow-clear")

                requireHits("vehicle-clear-beyond-holding-point")
                requireHits("tow-clear-beyond-holding-point")
                requireHits("runway-vacated-after-vehicle-and-tow-clear")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `active tow runway vacated report before tow clear evidence fails loudly`() {
        val state = baseState(
            baseVehicle().copy(
                runwayState = VehicleRunwayState.ClearBeyondHoldingPoint(RUNWAY),
                activeTow = activeTow(clearBeyondHoldingPoint = null),
            ),
        )
        val tx = vehicleDriverTransmission(
            VehicleDriverTransmission.RunwayVacated(VEHICLE, RUNWAY),
            receiver = GROUND,
        )

        assertFailsWith<IllegalStateException> {
            step(
                state.copy(inFlightTransmissions = mapOf(tx.id to tx)),
                SimEvent.TransmissionEnd(time = tx.endsAt, transmissionId = tx.id),
            )
        }
    }

    @Test
    fun `stale tow clear beyond holding point event does not clear active tow`() {
        val activeVacate = ActiveRunwayVacate(
            id = VehiclePermissionId(1),
            runway = RUNWAY,
            issuedAt = SimTime.ZERO,
        )
        val state = baseState(
            baseVehicle().copy(
                runwayState = VehicleRunwayState.ClearBeyondHoldingPoint(RUNWAY),
                activeRunwayVacate = activeVacate,
                activeTow = activeTow(clearBeyondHoldingPoint = null),
            ),
        )

        val (next, _) = step(
            state,
            SimEvent.TowClearBeyondHoldingPoint(
                time = SimTime.ZERO,
                vehicleId = VEHICLE,
                runway = RUNWAY,
                permissionId = VehiclePermissionId(99),
            ),
        )

        check(next.vehicles.getValue(VEHICLE).activeTow?.clearBeyondHoldingPoint == null) {
            "Stale tow clear-beyond-holding-point event cleared active tow"
        }
        check(next.vehicles.getValue(VEHICLE).activeRunwayVacate == activeVacate) {
            "Stale tow clear-beyond-holding-point event consumed active vacate token"
        }
    }

    @Test
    fun `tow request radio payload cannot mutate a different vehicle`() {
        val other = VehicleId("WORKER-99")
        val state = baseState(
            baseVehicle(),
            other to VehicleState(other, Callsign("WORKER 99"), START, DESTINATION, emptyList()),
        )
        val tx = vehicleDriverTransmission(
            VehicleDriverTransmission.RequestTow(
                vehicle = other,
                callsign = Callsign("WORKER 99"),
                receivingStation = RECEIVING_STATION,
                tow = TOW,
            ),
            speaker = VEHICLE,
            receiver = RECEIVING_STATION,
        )

        assertFailsWith<IllegalStateException> {
            step(
                state.copy(inFlightTransmissions = mapOf(tx.id to tx)),
                SimEvent.TransmissionEnd(time = tx.endsAt, transmissionId = tx.id),
            )
        }
    }

    @Test
    fun `fresh vehicle initial call clears stale active tow state`() {
        val state = baseState(
            baseVehicle().copy(
                activeTow = activeTow(
                    clearBeyondHoldingPoint = TowClearBeyondHoldingPoint(RUNWAY, VehiclePermissionId(1)),
                ),
            ),
        )
        val tx = vehicleDriverTransmission(
            VehicleDriverTransmission.InitialCall(
                vehicle = VEHICLE,
                callsign = CALLSIGN,
                position = START,
                destination = DESTINATION,
                route = listOf(START, DESTINATION),
            ),
            receiver = GROUND,
        )

        val (next, _) = step(
            state.copy(inFlightTransmissions = mapOf(tx.id to tx)),
            SimEvent.TransmissionEnd(time = tx.endsAt, transmissionId = tx.id),
        )

        check(next.vehicles.getValue(VEHICLE).activeTow == null) {
            "Fresh initial call retained stale active tow state"
        }
    }

    private fun vehicleTowingScenario(
        initialVehicle: VehicleState,
        radios: List<VehicleRadio>,
    ): VehicleScenario {
        var state = baseState(initialVehicle)
        val events = radios.map { radio ->
            val (next, txId) = state.mintTransmissionId()
            state = next
            val utterance = radio.utterance()
            val tx = InFlightTransmission(
                id = txId,
                speaker = radio.speaker(),
                receiver = radio.receiver(),
                frequency = FREQUENCY,
                utterance = utterance,
                startedAt = SimTime.ZERO + SimDuration.ofSeconds(radio.at),
                endsAt = SimTime.ZERO + SimDuration.ofSeconds(radio.at) + utteranceDuration(utterance),
            )
            SimEvent.TransmissionStart(time = tx.startedAt, transmission = tx)
        }
        return VehicleScenario(state, events)
    }

    private fun baseState(
        vehicle: VehicleState,
        vararg additionalVehicles: Pair<VehicleId, VehicleState>,
    ): SimState =
        SimState(
            now = SimTime.ZERO,
            seq = 0L,
            rng = SimRandom(42L),
            rngByAircraft = emptyMap(),
            aircraft = LinkedHashMap(),
            vehicles = mapOf(vehicle.id to vehicle) + additionalVehicles,
            controllers = mapOf(
                GROUND to controller(GROUND, RoleName.GROUND),
                RECEIVING_STATION to controller(RECEIVING_STATION, RoleName.TOWER),
            ),
            beliefs = mapOf(GROUND to BeliefState.EMPTY, RECEIVING_STATION to BeliefState.EMPTY),
            world = AviationWorld(),
            worldIndex = WorldIndex(),
        )

    private fun controller(id: ControllerId, role: RoleName): ControllerSpec =
        ControllerSpec(
            id = id,
            role = role,
            aerodromeId = AerodromeId("LOWG"),
            frequency = FREQUENCY,
            responsibilities = emptyMap(),
        )

    private fun baseVehicle(): VehicleState =
        VehicleState(
            id = VEHICLE,
            callsign = CALLSIGN,
            position = START,
            destination = DESTINATION,
            route = listOf(START, DESTINATION),
        )

    private fun activeTow(
        clearBeyondHoldingPoint: TowClearBeyondHoldingPoint? = null,
    ): ActiveTow =
        ActiveTow(
            metadata = TOW,
            receivingStation = RECEIVING_STATION,
            startedAt = SimTime.ZERO,
            clearBeyondHoldingPoint = clearBeyondHoldingPoint,
        )

    private fun vehicleDriverTransmission(
        transmission: VehicleDriverTransmission,
        speaker: VehicleId = VEHICLE,
        receiver: ControllerId,
    ): InFlightTransmission {
        val utterance = Utterance.FromVehicleDriver(transmission)
        return InFlightTransmission(
            id = TransmissionId(0),
            speaker = SpeakerRef.VehicleDriver(speaker),
            receiver = ReceiverRef.Controller(receiver),
            frequency = FREQUENCY,
            utterance = utterance,
            startedAt = SimTime.ZERO,
            endsAt = SimTime.ZERO + utteranceDuration(utterance),
        )
    }

    private inline fun <reified T : VehicleDriverTransmission> SimTrace.afterDriverTransmission() =
        steps.firstOrNull { step ->
            step.event is SimEvent.TransmissionReceptionObserved &&
                ((step.event as SimEvent.TransmissionReceptionObserved).transmission.utterance
                    as? Utterance.FromVehicleDriver)?.transmission is T
        } ?: fail("Expected delivered vehicle driver transmission ${T::class.simpleName}.")

    private inline fun <reified T : VehicleDriverTransmission> TraceStep.driverTransmission(): T =
        (((event as SimEvent.TransmissionReceptionObserved).transmission.utterance as Utterance.FromVehicleDriver)
            .transmission as T)

    private sealed interface VehicleRadio {
        val at: Long
        fun utterance(): Utterance
        fun speaker(): SpeakerRef
        fun receiver(): ReceiverRef

        data class Driver(
            override val at: Long,
            val receiver: ControllerId,
            val transmission: VehicleDriverTransmission,
        ) : VehicleRadio {
            override fun utterance(): Utterance = Utterance.FromVehicleDriver(transmission)
            override fun speaker(): SpeakerRef = SpeakerRef.VehicleDriver(VEHICLE)
            override fun receiver(): ReceiverRef = ReceiverRef.Controller(receiver)
        }

        data class Controller(
            override val at: Long,
            val transmission: VehicleControllerTransmission,
        ) : VehicleRadio {
            override fun utterance(): Utterance = Utterance.FromVehicleController(transmission)
            override fun speaker(): SpeakerRef = SpeakerRef.Controller(GROUND)
            override fun receiver(): ReceiverRef = ReceiverRef.VehicleDriver(VEHICLE)
        }
    }

    private data class VehicleScenario(
        val initialState: SimState,
        val events: List<SimEvent>,
    )

    private companion object {
        val VEHICLE = VehicleId("TUG-5")
        val CALLSIGN = Callsign("TUG 5")
        val GROUND = ControllerId("LOWG_GROUND")
        val RECEIVING_STATION = ControllerId("LOWG_TOWER")
        val FREQUENCY = Frequency.unsafe("121.700")
        val RUNWAY = RunwayId("27")
        val START = PointId("TOW-BAY")
        val DESTINATION = PointId("MAINTENANCE-RAMP")
        val TOWED_AIRCRAFT = AircraftId("OE-TOW")
        val OPERATOR = AircraftOperator("Austrian")
        val TOW = TowMetadata(
            aircraft = TOWED_AIRCRAFT,
            aircraftType = AircraftType.B738,
            operator = OPERATOR,
        )
    }
}
