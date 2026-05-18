package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstControllerInstructionOf
import xyz.easiersaid.twr.sim.testing.firstPilotReportOf
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

class Icao9432TouchAndGoSourceBackedScenarioTest {
    @Test
    fun `LOWG circuit training receives touch-and-go clearance before full-stop landing`() {
        sourceUnitSpec("icao9432-touch-and-go-circuit-training") {
            title("Touch-and-go intent produces touch-and-go clearance before full-stop landing")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::0ece166e11d7728e"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4"),
                ),
            )
            domain("aerodrome", setOf("LOWG"))
            domain("mission-outcome", setOf("touch-and-go-then-full-stop"))
            domain("traffic", setOf("single-aircraft"))

            witness("LOWG 16C touch-and-go then full-stop") {
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
                        outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
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
                val until = now + SimDuration.ofMillis(45 * 60 * 1000L)
                val (finalState, records) = runUntilWithStateTrace(initialState, initialEvents, until)
                val journey = finalState.formatJourney(aircraftId, records)

                val touchAndGoMs = records.firstControllerInstructionOf<ClearedTouchAndGo>(aircraftId)
                    .map { record -> record.time.millis }
                    .getOrElse { fail("Expected first circuit to receive ClearedTouchAndGo.\n$journey") }
                hit("touch-and-go-clearance")
                val landMs = records.firstControllerInstructionOf<ClearedToLand>(aircraftId)
                    .map { record -> record.time.millis }
                    .getOrElse { fail("Expected final circuit to receive ClearedToLand.\n$journey") }
                hit("full-stop-clearance")
                check(touchAndGoMs < landMs) {
                    "Expected ClearedTouchAndGo to precede final full-stop ClearedToLand; " +
                        "touchAndGo=${touchAndGoMs}ms, land=${landMs}ms.\n$journey"
                }

                val vacatedMs = records.firstPilotReportOf<ReportEvent.RunwayVacated>(aircraftId)
                    .map { record -> record.time.millis }
                    .getOrElse { fail("Expected pilot to vacate after final full-stop landing.\n$journey") }
                hit("runway-vacated")
                check(landMs < vacatedMs) {
                    "Expected final ClearedToLand to precede RunwayVacated; land=${landMs}ms, " +
                        "vacated=${vacatedMs}ms.\n$journey"
                }

                val finalAircraft = finalState.aircraft.getValue(aircraftId)
                check(finalAircraft.pilotMission?.isComplete == true && finalAircraft.phase == PilotPhase.Parked) {
                    "Expected touch-and-go plus full-stop circuit-training mission to complete and park.\n$journey"
                }
                requireHits("touch-and-go-clearance")
                requireHits("full-stop-clearance")
                requireHits("runway-vacated")
            }
        }.assertSatisfied().assertNoModelGaps()
    }
}
