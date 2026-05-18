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
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstControllerInstructionOf
import xyz.easiersaid.twr.sim.testing.firstPilotReportOf
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

class Icao9432TaxiSourceBackedScenarioTest {
    @Test
    fun `LOWG departure taxi clearance has a holding-point limit before runway use`() {
        sourceUnitSpec("icao9432-taxi-clearance-limit-to-holding-point") {
            title("Taxi clearance limit is a runway holding point before runway use")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::taxi_4_4_en::417f64324f7495bf"),
                    SourceUnitRef("icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e"),
                ),
            )
            domain("aerodrome", setOf("LOWG"))
            domain("active-runway", setOf("16C"))
            domain("traffic", setOf("single-aircraft"))

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
                ).getOrElse { error("SimState.initial rejected the LOWG fixture: $it") }

                val activeRunway = RunwayId("16C")
                val atis = Atis(
                    letter = 'A',
                    aerodrome = lowg,
                    configuration = RunwayConfiguration(arrivals = listOf(activeRunway), departures = listOf(activeRunway)),
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
                val (finalState, records) = runUntilWithStateTrace(initialState, initialEvents, until)
                val journey = finalState.formatJourney(aircraftId, records)

                val taxiRecord = records.firstControllerInstructionOf<TaxiToHoldingPoint>(aircraftId)
                    .getOrElse { fail("Expected a departure taxi clearance to a holding point.\n$journey") }
                hit("taxi-clearance")
                val taxiInstruction =
                    ((taxiRecord.utterance as Utterance.FromController).output as ControllerOutput.Instruct)
                        .dispatch.let { dispatch -> (dispatch as Dispatch.Direct).instruction as TaxiToHoldingPoint }

                val holdingPoints = loaded.world.aerodromes
                    .getValue(lowg)
                    .taxiways.values
                    .flatMap { taxiway -> taxiway.holdingPoints }
                    .filter { holdingPoint -> holdingPoint.runway == activeRunway }
                    .map { holdingPoint -> holdingPoint.point }
                    .toSet()

                check(taxiInstruction.destination in holdingPoints) {
                    "Taxi clearance limit should be a holding point for runway ${activeRunway.value}; " +
                        "got destination=${taxiInstruction.destination}, holdingPoints=$holdingPoints.\n$journey"
                }
                check(taxiInstruction.runway == activeRunway) {
                    "Taxi clearance should explicitly name active runway ${activeRunway.value}; " +
                        "got ${taxiInstruction.runway}.\n$journey"
                }

                val readyMs = records.firstPilotReportOf<ReportEvent.Ready>(aircraftId)
                    .map { record -> record.time.millis }
                    .getOrElse { fail("Expected pilot to report ready after taxi/run-up.\n$journey") }
                hit("ready-report")
                val lineUpMs = records.firstControllerInstructionOf<LineUpAndWait>(aircraftId)
                    .map { record -> record.time.millis }
                    .getOrElse { fail("Expected runway-use permission after taxi/run-up.\n$journey") }
                hit("runway-use-instruction")
                check(taxiRecord.time.millis < readyMs && readyMs < lineUpMs) {
                    "Expected taxi clearance to precede Ready, and Ready to precede runway-use permission. " +
                        "taxi=${taxiRecord.time.millis}, ready=$readyMs, lineUp=$lineUpMs.\n$journey"
                }
                requireHits("taxi-clearance")
                requireHits("ready-report")
                requireHits("runway-use-instruction")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

}
