package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PushbackApproved
import xyz.easiersaid.twr.protocol.Request
import xyz.easiersaid.twr.protocol.RequestPushback
import xyz.easiersaid.twr.protocol.RequestTaxi
import xyz.easiersaid.twr.protocol.RequestType
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.sim.testing.EventInjection
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.LoadedFixture
import xyz.easiersaid.twr.sim.testing.TransmissionRecord
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstControllerInstructionOf
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTraceAndInjection
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

class Icao9432PushbackSourceBackedScenarioTest {
    @Test
    fun `pushback required departure receives approval and ground crew signal before taxi request`() {
        sourceUnitSpec("icao9432-pushback-atc-branch-ground-crew-signal") {
            title("Pushback approval and ground-crew signal precede taxi request")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::pushback_powerback_4_3_en::5980a8f786170b01"),
                    SourceUnitRef("icao9432-extracted::pushback_powerback_4_3_en::b3652213a568f55f"),
                ),
            )
            domain("local-procedure-branch", setOf("atc-ground"))
            domain("unsupported-branch", setOf("apron-management"))
            domain("manoeuvre", setOf("pushback-not-powerback"))

            witness("LOWG pushback-required departure using GROUND local-procedure branch") {
                val loaded = Fixtures.LOWG.load().getOrElse {
                    fail("LOWG fixture failed to load: $it")
                }
                val lowg = AerodromeId("LOWG")
                val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) { "GROUND missing from fixture" }
                val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) { "TOWER missing from fixture" }
                val aircraftId = AircraftId("OE-ABC")
                val now = SimTime.ZERO
                val mission = createMission(
                    goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
                    startPhase = PilotPhase.AtStand,
                    time = now,
                    requiresPushback = true,
                )
                val aircraft = AircraftState(
                    id = aircraftId,
                    callsign = Callsign("OEABC"),
                    position = loaded.world.geometry.points.getValue(Fixtures.LOWG.standPointId),
                    positionPoint = Fixtures.LOWG.standPointId,
                    phase = PilotPhase.AtStand,
                    pilotMission = mission,
                )
                val initialState = SimState.initial(
                    seed = 42L,
                    world = loaded.world,
                    worldIndex = loaded.worldIndex,
                    aircraft = listOf(aircraft),
                    controllers = listOf(ground, tower),
                    weatherByAerodrome = mapOf(lowg to Fixtures.LOWG.weather),
                ).getOrElse { error("SimState.initial rejected the LOWG fixture: $it") }

                val initialEvents = loaded.initialEvents + listOf(
                    SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
                    SimEvent.PhysicsTick(time = now),
                    SimEvent.ControllerCycle(time = now, controllerId = ground.id),
                    SimEvent.ControllerCycle(time = now, controllerId = tower.id),
                )
                val until = now + SimDuration.ofMillis(12 * 60 * 1000L)
                val (finalState, records, trace) = runUntilWithStateTrace(initialState, initialEvents, until)
                val journey = finalState.formatJourney(aircraftId, records)

                val requestPushback = records.firstPilotRequest<RequestPushback>(aircraftId)
                    ?: fail("Expected pilot to request pushback.\n$journey")
                hit("pushback-requested")
                val pushbackApproved = records.firstControllerInstructionOf<PushbackApproved>(aircraftId)
                    .getOrElse { fail("Expected GROUND to approve pushback.\n$journey") }
                hit("pushback-approved")
                val groundCrewSignal = trace.steps.firstOrNull { step ->
                    step.event is SimEvent.GroundCrewPushbackComplete &&
                        (step.event as SimEvent.GroundCrewPushbackComplete).aircraftId == aircraftId
                } ?: fail("Expected ground crew pushback completion signal.\n$journey")
                hit("ground-crew-signal")
                val taxiRequest = records.firstPilotRequest<RequestTaxi>(aircraftId)
                    ?: fail("Expected taxi request after pushback completion.\n$journey")
                hit("taxi-after-signal")

                check(requestPushback.time < pushbackApproved.time) {
                    "Expected pushback request before approval. request=${requestPushback.time.millis}, " +
                        "approval=${pushbackApproved.time.millis}.\n$journey"
                }
                check(pushbackApproved.time < groundCrewSignal.time) {
                    "Expected approval before ground-crew visual signal. approval=${pushbackApproved.time.millis}, " +
                        "signal=${groundCrewSignal.time.millis}.\n$journey"
                }
                check(groundCrewSignal.time < taxiRequest.time) {
                    "Expected taxi request after ground-crew visual signal. signal=${groundCrewSignal.time.millis}, " +
                        "taxi=${taxiRequest.time.millis}.\n$journey"
                }

                val approvalInstruction =
                    ((pushbackApproved.utterance as Utterance.FromController).output as ControllerOutput.Instruct)
                        .dispatch.let { dispatch -> (dispatch as Dispatch.Direct).instruction }
                check(approvalInstruction is PushbackApproved) {
                    "Expected direct PushbackApproved instruction, got $approvalInstruction.\n$journey"
                }

                requireHits("pushback-requested")
                requireHits("pushback-approved")
                requireHits("ground-crew-signal")
                requireHits("taxi-after-signal")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `premature taxi request after pushback approval does not clear taxi before ground crew signal`() {
        val loaded = Fixtures.LOWG.load().getOrElse {
            fail("LOWG fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) { "GROUND missing from fixture" }
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) { "TOWER missing from fixture" }
        val aircraftId = AircraftId("OE-ABC")
        val now = SimTime.ZERO
        val initialState = pushbackInitialState(loaded, lowg, ground, tower, aircraftId, now)
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )
        var injectedPrematureTaxi = false
        val until = now + SimDuration.ofMillis(12 * 60 * 1000L)
        val (finalState, records, trace) = runUntilWithStateTraceAndInjection(
            initialState = initialState,
            initialEvents = initialEvents,
            untilTime = until,
            onAfterEvent = { ev, st ->
                if (injectedPrematureTaxi || !isPushbackApprovedProcessing(ev)) {
                    EventInjection(state = st, inject = emptyList())
                } else {
                    injectedPrematureTaxi = true
                    val start = ev.time + SimDuration.ofSeconds(1)
                    val utterance = Utterance.FromPilot(Request(RequestTaxi()))
                    val (withTxId, txId) = st.mintTransmissionId()
                    val tx = InFlightTransmission(
                        id = txId,
                        speaker = SpeakerRef.Pilot(aircraftId),
                        receiver = ReceiverRef.Controller(ground.id),
                        frequency = ground.frequency,
                        utterance = utterance,
                        startedAt = start,
                        endsAt = start + utteranceDuration(utterance),
                    )
                    EventInjection(
                        state = withTxId,
                        inject = listOf(SimEvent.TransmissionStart(time = tx.startedAt, transmission = tx)),
                    )
                }
            },
        )
        val journey = finalState.formatJourney(aircraftId, records)
        check(injectedPrematureTaxi) {
            "Expected test hook to inject a premature taxi request after PushbackApproved.\n$journey"
        }

        val groundCrewSignal = trace.steps.firstOrNull { step ->
            step.event is SimEvent.GroundCrewPushbackComplete &&
                (step.event as SimEvent.GroundCrewPushbackComplete).aircraftId == aircraftId
        } ?: fail("Expected ground crew pushback completion signal.\n$journey")
        val taxiClearance = records.firstControllerInstructionOf<TaxiToHoldingPoint>(aircraftId)
            .getOrElse { fail("Expected eventual taxi clearance after ground-crew signal.\n$journey") }
        check(groundCrewSignal.time < taxiClearance.time) {
            "Expected no taxi clearance before ground-crew signal. signal=${groundCrewSignal.time.millis}, " +
                "taxiClearance=${taxiClearance.time.millis}.\n$journey"
        }
    }
}

private fun pushbackInitialState(
    loaded: LoadedFixture,
    lowg: AerodromeId,
    ground: ControllerSpec,
    tower: ControllerSpec,
    aircraftId: AircraftId,
    now: SimTime,
): SimState {
    val mission = createMission(
        goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        startPhase = PilotPhase.AtStand,
        time = now,
        requiresPushback = true,
    )
    val aircraft = AircraftState(
        id = aircraftId,
        callsign = Callsign("OEABC"),
        position = loaded.world.geometry.points.getValue(Fixtures.LOWG.standPointId),
        positionPoint = Fixtures.LOWG.standPointId,
        phase = PilotPhase.AtStand,
        pilotMission = mission,
    )
    return SimState.initial(
        seed = 42L,
        world = loaded.world,
        worldIndex = loaded.worldIndex,
        aircraft = listOf(aircraft),
        controllers = listOf(ground, tower),
        weatherByAerodrome = mapOf(lowg to Fixtures.LOWG.weather),
    ).getOrElse { error("SimState.initial rejected the LOWG fixture: $it") }
}

private fun isPushbackApprovedProcessing(event: SimEvent): Boolean =
    event is SimEvent.PilotProcessingComplete &&
        (event.utterance as? Utterance.FromController)
            ?.output
            ?.let { output -> (output as? ControllerOutput.Instruct)?.instruction is PushbackApproved } == true

private inline fun <reified R : RequestType> List<TransmissionRecord>.firstPilotRequest(
    aircraftId: AircraftId,
): TransmissionRecord? =
    firstOrNull { record ->
        val speaker = record.speaker as? SpeakerRef.Pilot ?: return@firstOrNull false
        val tx = (record.utterance as? Utterance.FromPilot)?.transmission as? Request
            ?: return@firstOrNull false
        speaker.aircraftId == aircraftId && tx.type is R
    }
