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
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.BreakOff
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.LevelInstruction
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RouteInstruction
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.VectorInstruction
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstPilotReportOf
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace
import xyz.easiersaid.twr.sim.testing.TransmissionRecord

/**
 * ICAO 9432 chunk-06 source-mapped scenario evidence for §4.8.2.
 *
 * This test covers only the VFR branch: unless instructed otherwise, a VFR
 * aircraft continues in the normal traffic circuit after go-around. It does
 * not claim coverage for the IFR missed-approach-procedure branch or for
 * go-around radio brevity.
 */
class Icao9432Chunk06GoAroundEvidenceTest {
    @Test
    fun `VFR go-around continues into the normal traffic circuit`() {
        sourceUnitSpec("icao9432-vfr-go-around-continues-normal-traffic-circuit") {
            title("VFR aircraft continues in normal traffic circuit after go-around")
            sourceUnit(ICAO9432.GoAroundProcedures.VfrContinuesTrafficCircuit.toSourceUnitRef())
            domain("flight-rules", setOf("VFR"))
            domain("mission", setOf("circuit-training-go-around-then-full-stop"))
            domain("contrary-instruction", setOf("none-observed-before-post-ga-downwind"))

            witness("LOWG VFR circuit go-around then recovery circuit") {
                val loaded = Fixtures.LOWG.load().getOrElse {
                    fail("LOWG fixture failed to load: $it")
                }
                val lowg = AerodromeId("LOWG")
                val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) { "GROUND missing from fixture" }
                val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) { "TOWER missing from fixture" }
                val aircraftId = AircraftId("OE-ABC")
                val now = SimTime.ZERO
                val mission = createMission(
                    goal = HighLevelGoal.CircuitTraining(
                        outcomes = listOf(CircuitOutcome.GoAround, CircuitOutcome.FullStop),
                    ),
                    startPhase = PilotPhase.AtStand,
                    time = now,
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
                ).getOrElse { fail("SimState.initial rejected the LOWG fixture: $it") }

                val activeRunway = RunwayId("16C")
                val atis = Atis(
                    letter = 'A',
                    aerodrome = lowg,
                    configuration = RunwayConfiguration(
                        arrivals = listOf(activeRunway),
                        departures = listOf(activeRunway),
                    ),
                    wind = Wind.unsafe(160, 8),
                    qnh = null,
                    visibility = null,
                    generatedAt = now,
                )
                val initialEvents = loaded.initialEvents + listOf(
                    SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = atis),
                    SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
                    SimEvent.PhysicsTick(time = now),
                    SimEvent.ControllerCycle(time = now, controllerId = ground.id),
                    SimEvent.ControllerCycle(time = now, controllerId = tower.id),
                )
                val until = now + SimDuration.ofMillis(45 * 60 * 1000L)
                val (finalState, records) = runUntilWithStateTrace(initialState, initialEvents, until)
                val journey = finalState.formatJourney(aircraftId, records)

                val goingAroundRecord = records.firstPilotReportOf<ReportEvent.GoingAround>(aircraftId)
                    .getOrElse { fail("Expected pilot to report GoingAround in VFR circuit scenario.\n$journey") }
                val goingAroundMs = goingAroundRecord.time.millis
                hit("going-around-report")

                val postGoAroundDownwindRecord = records.firstOrNull { record ->
                    record.time.millis > goingAroundMs &&
                        record.isPilotReportFrom<ReportEvent.Downwind>(aircraftId)
                } ?: fail(
                    "Expected a post-go-around Downwind report from $aircraftId, proving normal " +
                        "traffic-circuit continuation after Report(GoingAround) at ${goingAroundMs}ms.\n$journey",
                )
                val postGoAroundDownwindMs = postGoAroundDownwindRecord.time.millis
                hit("post-go-around-downwind")

                val contraryInstructions = records.filter { record ->
                    record.time.millis > goingAroundMs &&
                        record.time.millis < postGoAroundDownwindMs &&
                        record.isContraryRoutingInstructionTo(aircraftId)
                }
                check(contraryInstructions.isEmpty()) {
                    "Expected no contrary route/vector/level/missed-approach instruction between " +
                        "GoingAround (${goingAroundMs}ms) and post-GA Downwind " +
                        "(${postGoAroundDownwindMs}ms); got $contraryInstructions.\n$journey"
                }
                hit("no-contrary-instruction-before-downwind")

                val recoveryLandingClearanceMs = records.firstOrNull { record ->
                    record.time.millis > postGoAroundDownwindMs &&
                        record.isControllerInstructionTo<ClearedToLand>(aircraftId)
                }?.time?.millis ?: fail(
                    "Expected recovery ClearedToLand after post-GA Downwind " +
                        "(${postGoAroundDownwindMs}ms).\n$journey",
                )
                hit("recovery-landing-clearance")

                val vacatedMs = records.firstPilotReportOf<ReportEvent.RunwayVacated>(aircraftId)
                    .map { record -> record.time.millis }
                    .filter { ms -> ms > recoveryLandingClearanceMs }
                    .getOrElse {
                        fail(
                            "Expected RunwayVacated after recovery ClearedToLand " +
                                "(${recoveryLandingClearanceMs}ms).\n$journey",
                        )
                    }
                hit("recovery-runway-vacated")

                check(goingAroundMs < postGoAroundDownwindMs && postGoAroundDownwindMs < recoveryLandingClearanceMs) {
                    "Expected GoingAround < post-GA Downwind < recovery ClearedToLand; got " +
                        "goingAround=${goingAroundMs}ms, downwind=${postGoAroundDownwindMs}ms, " +
                        "clearedToLand=${recoveryLandingClearanceMs}ms.\n$journey"
                }
                check(recoveryLandingClearanceMs < vacatedMs) {
                    "Expected recovery ClearedToLand before RunwayVacated; " +
                        "clearedToLand=${recoveryLandingClearanceMs}ms, vacated=${vacatedMs}ms.\n$journey"
                }

                requireHits("going-around-report")
                requireHits("post-go-around-downwind")
                requireHits("no-contrary-instruction-before-downwind")
                requireHits("recovery-landing-clearance")
                requireHits("recovery-runway-vacated")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    private inline fun <reified E : ReportEvent> TransmissionRecord.isPilotReportFrom(
        aircraftId: AircraftId,
    ): Boolean {
        val speaker = speaker as? SpeakerRef.Pilot ?: return false
        val transmission = (utterance as? Utterance.FromPilot)?.transmission as? Report ?: return false
        return speaker.aircraftId == aircraftId && transmission.events.any { event -> event is E }
    }

    private fun TransmissionRecord.isContraryRoutingInstructionTo(aircraftId: AircraftId): Boolean {
        val output = (utterance as? Utterance.FromController)?.output as? ControllerOutput.Instruct ?: return false
        val dispatch = output.dispatch as? Dispatch.Direct ?: return false
        if (output.target != aircraftId) return false
        val instruction = dispatch.instruction
        return instruction is RouteInstruction ||
            instruction is VectorInstruction ||
            instruction is LevelInstruction ||
            instruction is BreakOff
    }

    private inline fun <reified I> TransmissionRecord.isControllerInstructionTo(
        aircraftId: AircraftId,
    ): Boolean {
        val output = (utterance as? Utterance.FromController)?.output as? ControllerOutput.Instruct ?: return false
        val dispatch = output.dispatch as? Dispatch.Direct ?: return false
        return output.target == aircraftId && dispatch.instruction is I
    }
}
