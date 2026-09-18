package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.fail
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.testing.SimTrace
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

class Icao9432VehicleRunwaySourceBackedScenarioTest {
    @Test
    fun `vehicle crosses runway only after positive permission and acknowledgement`() {
        sourceUnitSpec("icao9432-vehicle-runway-crossing-permission-acknowledgement") {
            title("Vehicle runway crossing requires positive permission and acknowledgement")
            sourceUnit(
                SourceUnitRef(
                    "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::24f7b6a86407ef6f",
                ),
            )
            domain("runway-crossing-state", setOf("holding-short", "permission-issued", "acknowledged-crossing"))

            witness("hold short then cross only after acknowledgement") {
                val scenario = vehicleRunwayScenario(
                    initialVehicle = baseVehicle(),
                    radios = listOf(
                        VehicleRadio.Controller(
                            at = 0,
                            transmission = VehicleControllerTransmission.HoldShortRunway(VEHICLE, RUNWAY),
                        ),
                        VehicleRadio.Controller(
                            at = 5,
                            transmission = VehicleControllerTransmission.CrossRunway(
                                vehicle = VEHICLE,
                                runway = RUNWAY,
                                crossingTo = AFTER_RUNWAY,
                            ),
                        ),
                        VehicleRadio.Driver(
                            at = 10,
                            transmission = VehicleDriverTransmission.AcknowledgeRunwayCrossing(VEHICLE, RUNWAY),
                        ),
                    ),
                )
                val (_, _, trace) = runUntilWithStateTrace(
                    scenario.initialState,
                    scenario.events,
                    SimTime.ZERO + SimDuration.ofSeconds(30),
                )

                val afterPermission = trace.afterControllerTransmission<VehicleControllerTransmission.CrossRunway>()
                val permissionVehicle = afterPermission.state.vehicles.getValue(VEHICLE)
                check(permissionVehicle.runwayState == VehicleRunwayState.HoldingShort(RUNWAY)) {
                    "Vehicle crossed before acknowledgement: $permissionVehicle"
                }
                check(permissionVehicle.activeRunwayCrossing?.runway == RUNWAY) {
                    "Positive crossing permission was not recorded: $permissionVehicle"
                }
                hit("positive-permission-before-crossing")

                val afterAck = trace.afterDriverTransmission<VehicleDriverTransmission.AcknowledgeRunwayCrossing>()
                val acknowledgedVehicle = afterAck.state.vehicles.getValue(VEHICLE)
                check(acknowledgedVehicle.runwayState == VehicleRunwayState.Crossing(RUNWAY)) {
                    "Vehicle did not begin crossing after acknowledgement: $acknowledgedVehicle"
                }
                check(acknowledgedVehicle.activeRunwayCrossing == null) {
                    "Crossing permission should be consumed after acknowledgement: $acknowledgedVehicle"
                }
                hit("acknowledgement-before-crossing")

                requireHits("positive-permission-before-crossing")
                requireHits("acknowledgement-before-crossing")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `vehicle runway crossing acknowledgement without positive permission fails loudly`() {
        val state = baseState(
            baseVehicle().copy(runwayState = VehicleRunwayState.HoldingShort(RUNWAY)),
        )
        val tx = vehicleDriverTransmission(
            VehicleDriverTransmission.AcknowledgeRunwayCrossing(VEHICLE, RUNWAY),
        )

        assertFailsWith<IllegalStateException> {
            step(
                state.copy(inFlightTransmissions = mapOf(tx.id to tx)),
                SimEvent.TransmissionEnd(time = tx.endsAt, transmissionId = tx.id),
            )
        }
    }

    @Test
    fun `vehicle-only runway vacated report waits until clear beyond holding point`() {
        sourceUnitSpec("icao9432-vehicle-runway-vacated-after-clear-beyond-holding-point") {
            title("Vehicle-only runway-vacated report waits until clear beyond holding point")
            sourceUnit(
                SourceUnitRef(
                    "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b",
                ),
            )
            domain("extent-scope", setOf("vehicle-only-not-tow"))
            domain("vacated-report-timing", setOf("after-clear-beyond-holding-point"))

            witness("vehicle reports runway vacated only after clear-beyond-holding-point evidence") {
                val scenario = vehicleRunwayScenario(
                    initialVehicle = baseVehicle().copy(runwayState = VehicleRunwayState.OnRunway(RUNWAY)),
                    radios = listOf(
                        VehicleRadio.Controller(
                            at = 0,
                            transmission = VehicleControllerTransmission.VacateRunway(VEHICLE, RUNWAY),
                        ),
                        VehicleRadio.Driver(
                            at = 10,
                            transmission = VehicleDriverTransmission.RunwayVacated(VEHICLE, RUNWAY),
                        ),
                    ),
                )
                val (_, _, trace) = runUntilWithStateTrace(
                    scenario.initialState,
                    scenario.events,
                    SimTime.ZERO + SimDuration.ofSeconds(30),
                )

                val clearStep = trace.steps.firstOrNull { step ->
                    step.event is SimEvent.VehicleClearBeyondHoldingPoint &&
                        (step.event as SimEvent.VehicleClearBeyondHoldingPoint).vehicleId == VEHICLE
                } ?: fail("Vehicle never reached clear-beyond-holding-point state.")
                val clearVehicle = clearStep.state.vehicles.getValue(VEHICLE)
                check(clearVehicle.runwayState == VehicleRunwayState.ClearBeyondHoldingPoint(RUNWAY)) {
                    "Clear-beyond-holding-point event did not produce clear evidence: $clearVehicle"
                }
                hit("clear-beyond-holding-point-before-report")

                val vacatedStep = trace.afterDriverTransmission<VehicleDriverTransmission.RunwayVacated>()
                check(clearStep.time < vacatedStep.time) {
                    "Runway vacated report occurred before clear-beyond-holding-point evidence"
                }
                val finalVehicle = vacatedStep.state.vehicles.getValue(VEHICLE)
                check(finalVehicle.runwayState == VehicleRunwayState.OffRunway) {
                    "Runway vacated report did not clear vehicle runway state: $finalVehicle"
                }
                hit("runway-vacated-report-after-clear")

                requireHits("clear-beyond-holding-point-before-report")
                requireHits("runway-vacated-report-after-clear")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `stale vehicle clear beyond holding point event does not clear runway state`() {
        val activeVacate = ActiveRunwayVacate(
            id = VehiclePermissionId(1),
            runway = RUNWAY,
            issuedAt = SimTime.ZERO,
        )
        val state = baseState(
            baseVehicle().copy(
                runwayState = VehicleRunwayState.Crossing(RUNWAY),
                activeRunwayVacate = activeVacate,
            ),
        )

        val (next, _) = step(
            state,
            SimEvent.VehicleClearBeyondHoldingPoint(
                time = SimTime.ZERO,
                vehicleId = VEHICLE,
                runway = RUNWAY,
                permissionId = VehiclePermissionId(99),
            ),
        )

        check(next.vehicles.getValue(VEHICLE).runwayState == VehicleRunwayState.Crossing(RUNWAY)) {
            "Stale clear-beyond-holding-point event cleared runway state"
        }
        check(next.vehicles.getValue(VEHICLE).activeRunwayVacate == activeVacate) {
            "Stale clear-beyond-holding-point event consumed active vacate token"
        }
    }

    @Test
    fun `premature vehicle runway vacated report fails loudly`() {
        val state = baseState(
            baseVehicle().copy(runwayState = VehicleRunwayState.Crossing(RUNWAY)),
        )
        val tx = vehicleDriverTransmission(
            VehicleDriverTransmission.RunwayVacated(VEHICLE, RUNWAY),
        )

        assertFailsWith<IllegalStateException> {
            step(
                state.copy(inFlightTransmissions = mapOf(tx.id to tx)),
                SimEvent.TransmissionEnd(time = tx.endsAt, transmissionId = tx.id),
            )
        }
    }

    private fun vehicleRunwayScenario(
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

    private fun baseState(vehicle: VehicleState): SimState {
        val ground = ControllerSpec(
            id = CONTROLLER,
            role = RoleName.GROUND,
            aerodromeId = AerodromeId("LOWG"),
            frequency = FREQUENCY,
            responsibilities = emptyMap(),
        )
        return SimState(
            now = SimTime.ZERO,
            seq = 0L,
            rng = SimRandom(42L),
            rngByAircraft = emptyMap(),
            aircraft = LinkedHashMap(),
            vehicles = mapOf(vehicle.id to vehicle),
            controllers = mapOf(CONTROLLER to ground),
            beliefs = mapOf(CONTROLLER to BeliefState.EMPTY),
            world = AviationWorld(),
            worldIndex = WorldIndex(),
        )
    }

    private fun baseVehicle(): VehicleState =
        VehicleState(
            id = VEHICLE,
            callsign = CALLSIGN,
            position = HOLDING_POINT,
            destination = AFTER_RUNWAY,
            route = listOf(HOLDING_POINT, AFTER_RUNWAY),
        )

    private fun vehicleDriverTransmission(transmission: VehicleDriverTransmission): InFlightTransmission {
        val utterance = Utterance.FromVehicleDriver(transmission)
        return InFlightTransmission(
            id = TransmissionId(0),
            speaker = SpeakerRef.VehicleDriver(VEHICLE),
            receiver = ReceiverRef.Controller(CONTROLLER),
            frequency = FREQUENCY,
            utterance = utterance,
            startedAt = SimTime.ZERO,
            endsAt = SimTime.ZERO + utteranceDuration(utterance),
        )
    }

    private inline fun <reified T : VehicleControllerTransmission> SimTrace.afterControllerTransmission() =
        steps.firstOrNull { step ->
            step.event is SimEvent.VehicleDriverProcessingComplete &&
                ((step.event as SimEvent.VehicleDriverProcessingComplete).utterance as? Utterance.FromVehicleController)
                    ?.transmission is T
        } ?: fail("Expected delivered vehicle controller transmission ${T::class.simpleName}.")

    private inline fun <reified T : VehicleDriverTransmission> SimTrace.afterDriverTransmission() =
        steps.firstOrNull { step ->
            step.event is SimEvent.TransmissionReceptionObserved &&
                ((step.event as SimEvent.TransmissionReceptionObserved).transmission.utterance
                    as? Utterance.FromVehicleDriver)?.transmission is T
        } ?: fail("Expected delivered vehicle driver transmission ${T::class.simpleName}.")

    private sealed interface VehicleRadio {
        val at: Long
        fun utterance(): Utterance
        fun speaker(): SpeakerRef
        fun receiver(): ReceiverRef

        data class Driver(
            override val at: Long,
            val transmission: VehicleDriverTransmission,
        ) : VehicleRadio {
            override fun utterance(): Utterance = Utterance.FromVehicleDriver(transmission)
            override fun speaker(): SpeakerRef = SpeakerRef.VehicleDriver(VEHICLE)
            override fun receiver(): ReceiverRef = ReceiverRef.Controller(CONTROLLER)
        }

        data class Controller(
            override val at: Long,
            val transmission: VehicleControllerTransmission,
        ) : VehicleRadio {
            override fun utterance(): Utterance = Utterance.FromVehicleController(transmission)
            override fun speaker(): SpeakerRef = SpeakerRef.Controller(CONTROLLER)
            override fun receiver(): ReceiverRef = ReceiverRef.VehicleDriver(VEHICLE)
        }
    }

    private data class VehicleScenario(
        val initialState: SimState,
        val events: List<SimEvent>,
    )

    private companion object {
        val VEHICLE = VehicleId("WORKER-21")
        val CALLSIGN = Callsign("WORKER 21")
        val CONTROLLER = ControllerId("LOWG_GROUND")
        val FREQUENCY = Frequency.unsafe("121.700")
        val RUNWAY = RunwayId("27")
        val HOLDING_POINT = PointId("HOLD-SHORT-27")
        val AFTER_RUNWAY = PointId("MIKE")
    }
}
