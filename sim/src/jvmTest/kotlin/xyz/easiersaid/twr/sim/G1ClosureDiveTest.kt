package xyz.easiersaid.twr.sim

import arrow.core.Option
import arrow.core.getOrElse
import kotlin.test.Test
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.commitmentStageTransitions
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.requiredStartPoints
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace
import xyz.easiersaid.twr.sim.testing.transitionsOf

/**
 * fn-8.3 Phase 1 diagnostic dive — prints focused trace data for the
 * G1 multi-aircraft circuit-pattern deadlock. Diagnostic-only; not part
 * of the closure proof. Remove or evolve once root-cause is known.
 *
 * Mirrors `G1TwoAircraftCircuitsTest` setup verbatim, then walks:
 *  - tower circuitIntent[A] transitions over the run.
 *  - commitment stage transitions for A at LOWG_TOWER.
 *  - "TnG/Land" transmissions and the controller's circuitIntent[A] at the
 *    moment each fired (best-effort cursor lookup).
 */
class G1ClosureDiveTest {

    @Test
    fun `dive — circuit intent + commitment stage`() {
        val fixture = Fixtures.LOWG_TWO_AIRCRAFT
        val loaded = fixture.load().getOrElse { error("LOWG_TWO_AIRCRAFT load failed: $it") }
        val lowg = AerodromeId("LOWG")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND))
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER))

        val aId = AircraftId("OE-ABC")
        val bId = AircraftId("OE-DEF")
        val now = SimTime.ZERO
        val starts = fixture.requiredStartPoints()
        val standA = starts.getValue(aId)
        val standB = starts.getValue(bId)

        val missionA = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 2, fullStopOnLast = true),
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans.getValue(aId),
        )
        val missionB = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 2, fullStopOnLast = true),
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans.getValue(bId),
        )

        val acA = AircraftState(
            id = aId, callsign = Callsign("OEABC"),
            position = loaded.world.geometry.points.getValue(standA),
            positionPoint = standA, phase = PilotPhase.AtStand,
            type = AircraftType.C172, pilotMission = missionA,
        )
        val acB = AircraftState(
            id = bId, callsign = Callsign("OEDEF"),
            position = loaded.world.geometry.points.getValue(standB),
            positionPoint = standB, phase = PilotPhase.AtStand,
            type = AircraftType.C172, pilotMission = missionB,
        )

        val state = SimState.initial(
            seed = 42L, world = loaded.world, worldIndex = loaded.worldIndex,
            aircraft = listOf(acA, acB), controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(lowg to fixture.weather),
        ).getOrElse { error("SimState.initial failed: $it") }

        val until = SimTime.ZERO + SimDuration.ofMillis(90 * 60 * 1000L)
        val atis = Atis(
            letter = 'A', aerodrome = lowg,
            configuration = RunwayConfiguration(
                arrivals = listOf(RunwayId("16C")), departures = listOf(RunwayId("16C")),
            ),
            wind = Wind.unsafe(160, 8), qnh = null, visibility = null, generatedAt = now,
        )
        val bOffset = SimDuration.ofMillis(2 * 60 * 1000L)
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = atis),
            SimEvent.PilotDecisionTick(time = now, aircraftId = aId),
            SimEvent.PilotDecisionTick(time = now + bOffset, aircraftId = bId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )
        val (_, records, trace) = runUntilWithStateTrace(state, initialEvents, until)

        // 1. circuitIntent[A] transitions at TOWER
        println("\n=== circuitIntent[$aId] at $tower ===")
        val intentTransitions = trace.transitionsOf { st ->
            Option.fromNullable(st.beliefs[tower.id]?.circuitIntent?.get(aId))
        }
        intentTransitions.forEach { t ->
            val from = t.from.fold({ "absent" }, { it.name })
            val to = t.to.fold({ "absent" }, { it.name })
            println("  [${t.after.time.millis}ms] $from → $to")
        }

        // 2. commitment stage transitions for A at TOWER
        println("\n=== commitment.stage[$aId] at $tower ===")
        trace.commitmentStageTransitions(aId, tower.id).forEach { t ->
            val from = t.from.fold({ "absent" }, { it.name })
            val to = t.to.fold({ "absent" }, { it.name })
            println("  [${t.after.time.millis}ms] $from → $to")
        }

        // 3. ARR-LAND* transmissions for A — show controller's intent at the
        //    cursor immediately preceding the transmission record.
        println("\n=== ARR-LAND* transmissions for A (intent at controller view) ===")
        records.forEach { rec ->
            val out = (rec.utterance as? Utterance.FromController)?.output as? ControllerOutput.Instruct
                ?: return@forEach
            if (out.target != aId) return@forEach
            val ruleId = out.trace.ruleId
            if (!ruleId.startsWith("ARR-LAND") &&
                !ruleId.startsWith("ARR-VACATE") &&
                !ruleId.startsWith("ARR-TNG")) return@forEach
            val instr = (out.dispatch as? Dispatch.Direct)?.instruction
                ?: (out.dispatch as? Dispatch.Conditional)?.instruction
            // best-effort intent at-or-before this transmission
            var intent: String? = null
            var stage: String? = null
            val initial = trace.initial
            intent = initial.beliefs[tower.id]?.circuitIntent?.get(aId)?.name
            stage = initial.beliefs[tower.id]?.commitments?.get(aId)?.stage?.name
            for (s in trace.steps) {
                if (s.time > rec.time) break
                intent = s.state.beliefs[tower.id]?.circuitIntent?.get(aId)?.name
                stage = s.state.beliefs[tower.id]?.commitments?.get(aId)?.stage?.name
            }
            val instrName = instr?.let { it::class.simpleName } ?: "?"
            println("  [${rec.time.millis}ms] $ruleId → $instrName | intent=$intent stage=$stage")
        }

        // 4. Pilot Downwind reports for A — show what intent the pilot transmitted
        //    and the controller's belief immediately before/after.
        println("\n=== Pilot Downwind reports for A ===")
        records.forEach { rec ->
            val pilotTx = (rec.utterance as? Utterance.FromPilot)?.transmission ?: return@forEach
            val report = pilotTx as? xyz.easiersaid.twr.protocol.Report ?: return@forEach
            val downwind = report.events.filterIsInstance<xyz.easiersaid.twr.protocol.ReportEvent.Downwind>()
                .firstOrNull() ?: return@forEach
            val speaker = rec.speaker as? SpeakerRef.Pilot ?: return@forEach
            if (speaker.aircraftId != aId) return@forEach
            // intent BEFORE
            var beforeIntent: String? = trace.initial.beliefs[tower.id]?.circuitIntent?.get(aId)?.name
            for (s in trace.steps) {
                if (s.time > rec.time) break
                if (s.time < rec.time) {
                    beforeIntent = s.state.beliefs[tower.id]?.circuitIntent?.get(aId)?.name
                }
            }
            // intent AFTER all steps in trace at-or-after this time
            var afterIntent: String? = beforeIntent
            for (s in trace.steps) {
                if (s.time >= rec.time) {
                    afterIntent = s.state.beliefs[tower.id]?.circuitIntent?.get(aId)?.name
                    if (s.time > rec.time + xyz.easiersaid.twr.protocol.SimDuration.ofMillis(20000)) break
                }
            }
            println("  [${rec.time.millis}ms] PILOT Downwind(intent=${downwind.circuitIntent}) | tower-before=$beforeIntent | tower-after-20s=$afterIntent")
        }
    }
}
