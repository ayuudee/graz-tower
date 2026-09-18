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
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.testing.SimTrace
import xyz.easiersaid.twr.sim.testing.TraceStep
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

class Icao9432VehicleMovementSourceBackedScenarioTest {
    @Test
    fun `vehicle movement permission lifecycle covers standby hold limit and first-call content`() {
        sourceUnitSpec("icao9432-vehicle-movement-permission-lifecycle") {
            title("Vehicle driver does not proceed after standby or hold until permission, and stops at limits")
            sourceUnits(
                listOf(
                    SourceUnitRef(
                        "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::6061311019d039c7",
                    ),
                    SourceUnitRef(
                        "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::ed59ee805ff7fe77",
                    ),
                    SourceUnitRef(
                        "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::84c61b2ef1f12ad5",
                    ),
                    SourceUnitRef(
                        "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140",
                    ),
                ),
            )
            domain("vehicle-event-surface", setOf("vehicle-specific-radio-and-state"))
            domain("permission-gate", setOf("standby", "hold-position", "clearance-limit"))
            domain("phraseology-boundary", setOf("structured-content-not-rendered-wording"))

            witness("standby blocks movement until later proceed permission") {
                val scenario = vehicleScenario(
                    listOf(
                        VehicleRadio.Driver(
                            at = 0,
                            transmission = initialCall(route = listOf(PointId("KILO"))),
                        ),
                        VehicleRadio.Controller(
                            at = 5,
                            transmission = VehicleControllerTransmission.Standby(VEHICLE),
                        ),
                        VehicleRadio.Controller(
                            at = 20,
                            transmission = VehicleControllerTransmission.ProceedTo(
                                vehicle = VEHICLE,
                                clearanceLimit = DESTINATION,
                                route = listOf(PointId("KILO")),
                            ),
                        ),
                    ),
                )
                val (_, records, trace) = runUntilWithStateTrace(
                    scenario.initialState,
                    scenario.events,
                    SimTime.ZERO + SimDuration.ofSeconds(40),
                )

                val firstCall = trace.vehicleDriverTransmission<VehicleDriverTransmission.InitialCall>()
                check(firstCall.callsign == CALLSIGN) { "Vehicle first-call did not carry call sign: $firstCall" }
                check(firstCall.position == START) { "Vehicle first-call did not carry position: $firstCall" }
                check(firstCall.destination == DESTINATION) { "Vehicle first-call did not carry destination: $firstCall" }
                check(firstCall.route == listOf(PointId("KILO"))) {
                    "Vehicle first-call did not carry route when possible: $firstCall"
                }
                hit("first-call-structured-content")

                val report = simEvidence("icao9432-vehicle-first-call-rendered-wording") {
                    observe {
                        EvidenceFactAdapters.fromTransmissionRecords(
                            scenarioId = "icao9432-vehicle-first-call-rendered-wording",
                            records = records,
                            diagnostic = "Vehicle first-call rendered phraseology",
                        )
                    }
                    source("vehicle first-call rendered wording") {
                        cites(ICAO9432.VehiclesAndTowing.FirstCallIdentifiesVehicleRoute)
                        sample("vehicle", VEHICLE.value)
                        sample("position", START.value)
                        sample("destination", DESTINATION.value)
                        sample("route", "KILO")
                        expect {
                            renderedVehicleDriverPhraseology(VEHICLE).initialCall(
                                callsign = CALLSIGN,
                                position = START,
                                destination = DESTINATION,
                                route = listOf(PointId("KILO")),
                            )
                        }
                    }
                }
                report.assertNoFailures()
                hit("first-call-rendered-wording")

                val standbyStep = trace.afterVehicleInstruction<VehicleControllerTransmission.Standby>()
                standbyStep.assertVehicleAt(START)
                standbyStep.assertVehiclePhase<VehicleMovementPhase.Standby>()
                hit("standby-received-no-move")

                val final = trace.finalState.vehicles.getValue(VEHICLE)
                check(final.position == DESTINATION && final.phase is VehicleMovementPhase.Complete) {
                    "Vehicle did not proceed only after later permission. Final=$final"
                }
                hit("permission-after-standby")
                requireHits("first-call-structured-content")
                requireHits("first-call-rendered-wording")
                requireHits("standby-received-no-move")
                requireHits("permission-after-standby")
            }

            witness("hold position blocks movement until callback permission") {
                val scenario = vehicleScenario(
                    listOf(
                        VehicleRadio.Driver(at = 0, transmission = initialCall(route = listOf(PointId("ALPHA")))),
                        VehicleRadio.Controller(
                            at = 5,
                            transmission = VehicleControllerTransmission.HoldPosition(VEHICLE),
                        ),
                        VehicleRadio.Controller(
                            at = 18,
                            transmission = VehicleControllerTransmission.ProceedTo(
                                vehicle = VEHICLE,
                                clearanceLimit = DESTINATION,
                                route = listOf(PointId("ALPHA")),
                            ),
                        ),
                    ),
                )
                val (_, _, trace) = runUntilWithStateTrace(
                    scenario.initialState,
                    scenario.events,
                    SimTime.ZERO + SimDuration.ofSeconds(40),
                )

                val holdStep = trace.afterVehicleInstruction<VehicleControllerTransmission.HoldPosition>()
                holdStep.assertVehicleAt(START)
                holdStep.assertVehiclePhase<VehicleMovementPhase.HoldingPosition>()
                hit("hold-position-received-no-move")

                val final = trace.finalState.vehicles.getValue(VEHICLE)
                check(final.position == DESTINATION && final.phase is VehicleMovementPhase.Complete) {
                    "Vehicle did not wait for callback permission after HOLD POSITION. Final=$final"
                }
                hit("callback-permission-after-hold")
                requireHits("hold-position-received-no-move")
                requireHits("callback-permission-after-hold")
            }

            witness("hold position supersedes stale proceed permission before movement completes") {
                val scenario = vehicleScenario(
                    listOf(
                        VehicleRadio.Driver(at = 0, transmission = initialCall(route = listOf(PointId("ALPHA")))),
                        VehicleRadio.Controller(
                            at = 5,
                            transmission = VehicleControllerTransmission.ProceedTo(
                                vehicle = VEHICLE,
                                clearanceLimit = DESTINATION,
                                route = listOf(PointId("ALPHA")),
                            ),
                        ),
                        VehicleRadio.Controller(
                            at = 9,
                            transmission = VehicleControllerTransmission.HoldPosition(VEHICLE),
                        ),
                    ),
                )
                val (_, _, trace) = runUntilWithStateTrace(
                    scenario.initialState,
                    scenario.events,
                    SimTime.ZERO + SimDuration.ofSeconds(25),
                )

                val holdStep = trace.afterVehicleInstruction<VehicleControllerTransmission.HoldPosition>()
                holdStep.assertVehicleAt(START)
                holdStep.assertVehiclePhase<VehicleMovementPhase.HoldingPosition>()

                val final = trace.finalState.vehicles.getValue(VEHICLE)
                check(final.position == START && final.phase is VehicleMovementPhase.HoldingPosition) {
                    "Stale pre-hold proceed permission moved the vehicle after HOLD POSITION. Final=$final"
                }
                hit("hold-supersedes-stale-permission")
                requireHits("hold-supersedes-stale-permission")
            }

            witness("vehicle stops at intermediate clearance limit and requests onward permission") {
                val limit = PointId("HOLD-14")
                val scenario = vehicleScenario(
                    listOf(
                        VehicleRadio.Driver(
                            at = 0,
                            transmission = initialCall(route = listOf(PointId("KILO"), PointId("ALPHA"))),
                        ),
                        VehicleRadio.Controller(
                            at = 5,
                            transmission = VehicleControllerTransmission.ProceedTo(
                                vehicle = VEHICLE,
                                clearanceLimit = limit,
                                route = listOf(PointId("KILO"), PointId("ALPHA")),
                            ),
                        ),
                        VehicleRadio.Driver(
                            at = 18,
                            transmission = VehicleDriverTransmission.RequestFurtherPermission(
                                vehicle = VEHICLE,
                                from = limit,
                                destination = DESTINATION,
                            ),
                        ),
                        VehicleRadio.Controller(
                            at = 25,
                            transmission = VehicleControllerTransmission.ProceedTo(
                                vehicle = VEHICLE,
                                clearanceLimit = DESTINATION,
                                route = listOf(PointId("FOXTROT")),
                            ),
                        ),
                    ),
                )
                val (_, _, trace) = runUntilWithStateTrace(
                    scenario.initialState,
                    scenario.events,
                    SimTime.ZERO + SimDuration.ofSeconds(45),
                )

                val stopped = trace.steps.firstOrNull { step ->
                    step.state.vehicles[VEHICLE]?.phase is VehicleMovementPhase.StoppedAtLimit
                } ?: fail("Vehicle never stopped at intermediate clearance limit.")
                stopped.assertVehicleAt(limit)
                hit("stopped-at-clearance-limit")

                val onwardRequest = trace.vehicleDriverTransmission<VehicleDriverTransmission.RequestFurtherPermission>()
                check(onwardRequest.from == limit && onwardRequest.destination == DESTINATION) {
                    "Vehicle did not request onward permission from the clearance limit: $onwardRequest"
                }
                hit("requested-further-permission")

                val final = trace.finalState.vehicles.getValue(VEHICLE)
                check(final.position == DESTINATION && final.phase is VehicleMovementPhase.Complete) {
                    "Vehicle did not complete after further permission. Final=$final"
                }
                hit("proceeded-after-further-permission")
                requireHits("stopped-at-clearance-limit")
                requireHits("requested-further-permission")
                requireHits("proceeded-after-further-permission")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `vehicle radio payload cannot mutate a different vehicle`() {
        val other = VehicleId("WORKER-99")
        val ground = ControllerSpec(
            id = CONTROLLER,
            role = RoleName.GROUND,
            aerodromeId = AerodromeId("LOWG"),
            frequency = FREQUENCY,
            responsibilities = emptyMap(),
        )
        val state = SimState(
            now = SimTime.ZERO,
            seq = 0L,
            rng = SimRandom(42L),
            rngByAircraft = emptyMap(),
            aircraft = LinkedHashMap(),
            vehicles = mapOf(
                VEHICLE to VehicleState(VEHICLE, CALLSIGN, START, DESTINATION, emptyList()),
                other to VehicleState(other, Callsign("WORKER 99"), START, DESTINATION, emptyList()),
            ),
            controllers = mapOf(CONTROLLER to ground),
            beliefs = mapOf(CONTROLLER to BeliefState.EMPTY),
            world = AviationWorld(),
            worldIndex = WorldIndex(),
        )
        val utterance = Utterance.FromVehicleDriver(
            VehicleDriverTransmission.RequestFurtherPermission(
                vehicle = other,
                from = START,
                destination = DESTINATION,
            ),
        )
        val tx = InFlightTransmission(
            id = TransmissionId(0),
            speaker = SpeakerRef.VehicleDriver(VEHICLE),
            receiver = ReceiverRef.Controller(CONTROLLER),
            frequency = FREQUENCY,
            utterance = utterance,
            startedAt = SimTime.ZERO,
            endsAt = SimTime.ZERO + utteranceDuration(utterance),
        )

        val withInFlight = state.copy(inFlightTransmissions = mapOf(tx.id to tx))
        assertFailsWith<IllegalStateException> {
            step(withInFlight, SimEvent.TransmissionEnd(time = tx.endsAt, transmissionId = tx.id))
        }

        val controllerUtterance = Utterance.FromVehicleController(
            VehicleControllerTransmission.HoldPosition(vehicle = other),
        )
        assertFailsWith<IllegalStateException> {
            step(
                state,
                SimEvent.VehicleDriverProcessingComplete(
                    time = SimTime.ZERO,
                    vehicleId = VEHICLE,
                    utterance = controllerUtterance,
                ),
            )
        }
    }

    private fun vehicleScenario(radios: List<VehicleRadio>): VehicleScenario {
        val ground = ControllerSpec(
            id = CONTROLLER,
            role = RoleName.GROUND,
            aerodromeId = AerodromeId("LOWG"),
            frequency = FREQUENCY,
            responsibilities = emptyMap(),
        )
        var state = SimState(
            now = SimTime.ZERO,
            seq = 0L,
            rng = SimRandom(42L),
            rngByAircraft = emptyMap(),
            aircraft = LinkedHashMap(),
            vehicles = mapOf(
                VEHICLE to VehicleState(
                    id = VEHICLE,
                    callsign = CALLSIGN,
                    position = START,
                    destination = DESTINATION,
                    route = emptyList(),
                ),
            ),
            controllers = mapOf(CONTROLLER to ground),
            beliefs = mapOf(CONTROLLER to BeliefState.EMPTY),
            world = AviationWorld(),
            worldIndex = WorldIndex(),
        )
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

    private fun initialCall(route: List<PointId>): VehicleDriverTransmission.InitialCall =
        VehicleDriverTransmission.InitialCall(
            vehicle = VEHICLE,
            callsign = CALLSIGN,
            position = START,
            destination = DESTINATION,
            route = route,
        )

    private inline fun <reified T : VehicleDriverTransmission> SimTrace.vehicleDriverTransmission(): T =
        steps.asSequence()
            .mapNotNull { (it.event as? SimEvent.TransmissionStart)?.transmission?.utterance }
            .mapNotNull { (it as? Utterance.FromVehicleDriver)?.transmission as? T }
            .firstOrNull()
            ?: fail("Expected vehicle driver transmission ${T::class.simpleName}.")

    private inline fun <reified T : VehicleControllerTransmission> SimTrace.afterVehicleInstruction(): TraceStep =
        steps.firstOrNull { step ->
            step.event is SimEvent.VehicleDriverProcessingComplete &&
                ((step.event as SimEvent.VehicleDriverProcessingComplete).utterance as? Utterance.FromVehicleController)
                    ?.transmission is T
        } ?: fail("Expected delivered vehicle instruction ${T::class.simpleName}.")

    private fun TraceStep.assertVehicleAt(point: PointId) {
        val vehicle = state.vehicles.getValue(VEHICLE)
        check(vehicle.position == point) {
            "Expected vehicle at ${point.value} after ${event::class.simpleName}, got $vehicle"
        }
    }

    private inline fun <reified T : VehicleMovementPhase> TraceStep.assertVehiclePhase() {
        val vehicle = state.vehicles.getValue(VEHICLE)
        check(vehicle.phase is T) {
            "Expected vehicle phase ${T::class.simpleName} after ${event::class.simpleName}, got $vehicle"
        }
    }

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
        val START = PointId("GATE-27")
        val DESTINATION = PointId("WORK-IN-PROGRESS-HOTEL")
    }
}
