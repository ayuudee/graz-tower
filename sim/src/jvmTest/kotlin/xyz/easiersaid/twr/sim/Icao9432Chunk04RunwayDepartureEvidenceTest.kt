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
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstControllerInstructionOf
import xyz.easiersaid.twr.sim.testing.firstPilotReportOf
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

/**
 * ICAO 9432 chunk-04 source-mapped scenario evidence for §4.5.1.
 *
 * §4.5.1 says that, at busy aerodromes with separate GROUND and TOWER
 * functions, aircraft are **usually** transferred to TOWER at or when
 * approaching the runway-holding position. This test proves only the LOWG
 * scenario instance; final chunk coverage remains policy-blocked rather than
 * universal covered-green because "usually" is not an unconditional rule.
 */
class Icao9432Chunk04RunwayDepartureEvidenceTest {
    @Test
    fun `LOWG transfer to tower occurs at runway holding point before runway use`() {
        sourceUnitSpec("icao9432-runway-departure-tower-transfer-at-holding-position") {
            title("LOWG departure is transferred from GROUND to TOWER at the runway holding point")
            sourceUnit(ICAO9432.TakeoffProcedures.TowerTransferAtHoldingPosition.toSourceUnitRef())
            domain("aerodrome", setOf("LOWG"))
            domain("active-runway", setOf("16C"))
            domain("service-shape", setOf("separate-ground-and-tower"))

            witness("LOWG 16C GA stand departure") {
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
                val until = now + SimDuration.ofMillis(12 * 60 * 1000L)
                val (finalState, records, trace) = runUntilWithStateTrace(initialState, initialEvents, until)
                val journey = finalState.formatJourney(aircraftId, records)

                val taxiRecord = records.firstControllerInstructionOf<TaxiToHoldingPoint>(aircraftId)
                    .getOrElse { fail("Expected a taxi clearance to a runway holding point.\n$journey") }
                hit("taxi-clearance")

                val contactRecord = records.firstOrNull { record ->
                    val speaker = record.speaker as? SpeakerRef.Controller ?: return@firstOrNull false
                    val output = (record.utterance as? Utterance.FromController)?.output
                        as? ControllerOutput.Instruct ?: return@firstOrNull false
                    val dispatch = output.dispatch as? Dispatch.Direct ?: return@firstOrNull false
                    val instruction = dispatch.instruction as? ContactFrequency ?: return@firstOrNull false
                    speaker.id == ground.id && instruction.target == aircraftId && instruction.role == RoleName.TOWER
                } ?: fail("Expected GROUND to issue ContactFrequency(role=TOWER) for $aircraftId.\n$journey")
                hit("tower-transfer")

                val readyMs = records.firstPilotReportOf<ReportEvent.Ready>(aircraftId)
                    .map { record -> record.time.millis }
                    .getOrElse { fail("Expected pilot Ready report after tower transfer.\n$journey") }
                hit("ready-report")
                val lineUpMs = records.firstControllerInstructionOf<LineUpAndWait>(aircraftId)
                    .map { record -> record.time.millis }
                    .getOrElse { fail("Expected LineUpAndWait after tower transfer.\n$journey") }
                hit("runway-use-instruction")

                check(taxiRecord.time.millis < contactRecord.time.millis) {
                    "Expected taxi clearance before tower transfer. taxi=${taxiRecord.time.millis}, " +
                        "transfer=${contactRecord.time.millis}.\n$journey"
                }
                check(contactRecord.time.millis < readyMs && readyMs < lineUpMs) {
                    "Expected tower transfer before Ready, and Ready before runway-use permission. " +
                        "transfer=${contactRecord.time.millis}, ready=$readyMs, lineUp=$lineUpMs.\n$journey"
                }

                val transferStep = trace.steps.singleOrNull { step ->
                    val event = step.event as? SimEvent.TransmissionStart ?: return@singleOrNull false
                    event.transmission.id == contactRecord.transmissionId
                } ?: fail(
                    "Could not uniquely locate TransmissionStart for ContactFrequency transmission " +
                        "${contactRecord.transmissionId.value}.\n$journey",
                )
                val aircraftAtTransfer = checkNotNull(transferStep.state.aircraft[aircraftId]) {
                    "Aircraft $aircraftId missing from state at tower-transfer transmission"
                }
                val holdingPoints = loaded.world.aerodromes
                    .getValue(lowg)
                    .taxiways.values
                    .flatMap { taxiway -> taxiway.holdingPoints }
                    .filter { holdingPoint -> holdingPoint.runway == activeRunway }
                    .map { holdingPoint -> holdingPoint.point }
                    .toSet()
                check(aircraftAtTransfer.positionPoint in holdingPoints) {
                    "Expected tower transfer at a runway ${activeRunway.value} holding point; " +
                        "positionPoint=${aircraftAtTransfer.positionPoint}, holdingPoints=$holdingPoints.\n$journey"
                }
                check(aircraftAtTransfer.phase == PilotPhase.HoldingShort) {
                    "Expected aircraft to be holding short at tower transfer; phase=${aircraftAtTransfer.phase}.\n$journey"
                }
                requireHits("taxi-clearance")
                requireHits("tower-transfer")
                requireHits("ready-report")
                requireHits("runway-use-instruction")
            }
        }.assertSatisfied().assertNoModelGaps()
    }
}
