package xyz.easiersaid.twr.sim

import arrow.core.Option
import arrow.core.getOrElse
import kotlin.test.Test
import xyz.easiersaid.twr.controller.ControllerOutput
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
import xyz.easiersaid.twr.sim.testing.SimTrace
import xyz.easiersaid.twr.sim.testing.commitmentStageTransitions
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.requiredStartPoints
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

/**
 * fn-8.3 Phase 3 round 1 — assertive regression pin for the C1-C4
 * fixes (B4 closure family).
 *
 * G1TwoAircraftCircuitsTest is still red on a separate downstream
 * wedge (B5 — pilot-side mid-T&G→full-stop recovery). That test's
 * full-mission-completion assertions cannot be the regression
 * witness for the C1-C4 invariants — those need their own pin so
 * the fix surface is protected even while G1 itself stays red.
 *
 * The pins here are focused on **observable B4 invariants** that
 * the C1-C4 fixes directly establish:
 *
 *  1. **Same-aircraft pilot transmissions never collide** (C1):
 *     consecutive pilot ticks for the same aircraft on the same
 *     frequency must produce non-overlapping `startedAt` /
 *     `endsAt` windows. Pre-C1, two B-tick transmissions could
 *     both schedule at the same instant when the prior tick's
 *     `TransmissionStart` was queued but not yet processed.
 *
 *  2. **B's TOWER_DEPARTURE commitment advances out of
 *     AwaitTakeoffObserved within the run** (C2 + C3): with
 *     `IsCircuitTrafficByStrip` recognising B as local-flight
 *     circuit traffic from the AFTN-distributed strip alone,
 *     `DEP-CIRCUIT-COMPLETE` fires regardless of whether the
 *     radio-derived `circuitIntent[B]` was delivered. Pre-fix, a
 *     stepped-on Downwind permanently wedged B at
 *     `AwaitTakeoffObserved`.
 *
 *  3. **The controller does not issue `ClearedTouchAndGo` to an
 *     aircraft whose `circuitIntent` is empty** (C4): with the
 *     default-flip, ARR-LAND-TNG requires an explicit
 *     `CircuitIntentIs(TOUCH_AND_GO)`. Empty-intent aircraft on
 *     final get cleared to land, not to T&G.
 *
 * If any of these invariants regresses, this spec catches it
 * without depending on G1's full closure.
 */
class G1B4ClosurePinSpec {

    private fun runFixture(): SimTrace {
        val fixture = Fixtures.LOWG_TWO_AIRCRAFT
        val loaded = fixture.load().getOrElse { error("LOWG_TWO_AIRCRAFT load failed: $it") }
        val lowg = AerodromeId("LOWG")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND))
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER))

        val aId = AircraftId("OE-ABC")
        val bId = AircraftId("OE-DEF")
        val now = SimTime.ZERO
        val starts = fixture.requiredStartPoints()

        val missionA = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 2, fullStopOnLast = true),
            startPhase = PilotPhase.AtStand, time = now,
            filedPlan = fixture.flightPlans.getValue(aId),
        )
        val missionB = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 2, fullStopOnLast = true),
            startPhase = PilotPhase.AtStand, time = now,
            filedPlan = fixture.flightPlans.getValue(bId),
        )

        val acA = AircraftState(
            id = aId, callsign = Callsign("OEABC"),
            position = loaded.world.geometry.points.getValue(starts.getValue(aId)),
            positionPoint = starts.getValue(aId), phase = PilotPhase.AtStand,
            type = AircraftType.C172, pilotMission = missionA,
        )
        val acB = AircraftState(
            id = bId, callsign = Callsign("OEDEF"),
            position = loaded.world.geometry.points.getValue(starts.getValue(bId)),
            positionPoint = starts.getValue(bId), phase = PilotPhase.AtStand,
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
        return runUntilWithStateTrace(state, initialEvents, until).trace
    }

    /**
     * **C1 invariant**: same-aircraft pilot transmissions never overlap.
     *
     * Walk every `SimEvent.TransmissionStart` whose speaker is a pilot.
     * Group by aircraft id. Within each group, sort by `startedAt` and
     * verify that adjacent pairs are non-overlapping
     * (`prev.endsAt <= next.startedAt`).
     *
     * Pre-C1, B's same-aircraft consecutive Downwind+Base could both
     * land at `startedAt = 1560940ms` (both ticks computed against the
     * stale view that didn't yet include the prior tick's queued ift).
     * The post-C1 pin asserts that no two transmissions from the same
     * aircraft on the same frequency overlap.
     */
    @Test
    fun `C1 — same-aircraft pilot transmissions never overlap`() {
        val trace = runFixture()
        data class PilotTx(val ac: AircraftId, val startedAt: SimTime, val endsAt: SimTime, val freq: String)
        val pilotTxs = mutableListOf<PilotTx>()
        for (s in trace.steps) {
            val ev = s.event as? SimEvent.TransmissionStart ?: continue
            val tx = ev.transmission
            val sp = tx.speaker as? SpeakerRef.Pilot ?: continue
            pilotTxs += PilotTx(sp.aircraftId, tx.startedAt, tx.endsAt, tx.frequency.mhz)
        }
        val byAircraft = pilotTxs.groupBy { it.ac }
        byAircraft.forEach { (ac, txs) ->
            val sorted = txs.sortedBy { it.startedAt.millis }
            for (i in 1 until sorted.size) {
                val prev = sorted[i - 1]
                val cur = sorted[i]
                check(prev.endsAt.millis <= cur.startedAt.millis) {
                    "C1 regression: aircraft ${ac.value} has overlapping pilot transmissions on freq " +
                        "${prev.freq} — prev=[${prev.startedAt.millis}..${prev.endsAt.millis}] " +
                        "cur=[${cur.startedAt.millis}..${cur.endsAt.millis}]"
                }
            }
        }
    }

    /**
     * **C2 + C3 invariant**: B's TOWER_DEPARTURE commitment advances
     * out of `AwaitTakeoffObserved` within the run.
     *
     * Pre-C2/C3, a stepped-on Downwind transmission from B left
     * `circuitIntent[B]` empty. `DEP-CIRCUIT-COMPLETE`'s gate
     * (`Airborne && OnCircuitLeg(DOWNWIND) && IsCircuitTraffic`) was
     * always false because `IsCircuitTraffic` required the
     * radio-derived signal. B's commitment wedged at
     * `AwaitTakeoffObserved` for the entire run.
     *
     * Post-C2/C3, the gate accepts `IsCircuitTrafficByStrip` as an
     * alternative — the AFTN-distributed strip's "VFR LCL" tag is
     * sufficient. The commitment advances; B's TOWER_ARRIVAL forms
     * downstream.
     *
     * The pin asserts that B's commitment-stage chain at LOWG_TOWER
     * leaves `AwaitTakeoffObserved` at some point before run end.
     */
    @Test
    fun `C2+C3 — B commitment leaves AwaitTakeoffObserved within run`() {
        val trace = runFixture()
        val tower = trace.initial.controllers.values
            .firstOrNull { it.role == RoleName.TOWER && it.aerodromeId == AerodromeId("LOWG") }
            ?: error("LOWG_TOWER not in initial state")
        val bId = AircraftId("OE-DEF")
        val transitions = trace.commitmentStageTransitions(bId, tower.id)
        val leftAwaitTakeoffObserved = transitions.any { t ->
            t.from.fold({ false }, { it.name == "AwaitTakeoffObserved" })
        }
        check(leftAwaitTakeoffObserved) {
            "C2+C3 regression: B's TOWER_DEPARTURE commitment never advanced out of " +
                "AwaitTakeoffObserved. Transitions: ${transitions.map {
                    "${it.from.fold({ "absent" }, { it.name })}→${it.to.fold({ "absent" }, { it.name })}"
                }}"
        }
    }

    /**
     * **C4 invariant**: the controller does not issue `ClearedTouchAndGo`
     * to an aircraft whose `circuitIntent` is empty at the moment of
     * issuance.
     *
     * Pre-C4, `ARR-LAND-TNG`'s gate was `Not(CircuitIntentIs(FULL_STOP))`
     * — i.e., default to T&G when intent is empty. Combined with the
     * C2/C3 strip-based DEP-CIRCUIT-COMPLETE, an aircraft whose
     * Downwind never delivered would get a T&G clearance against a
     * pilot who never declared T&G.
     *
     * Post-C4, `ARR-LAND-TNG` requires explicit
     * `CircuitIntentIs(TOUCH_AND_GO)`. The pin asserts that every
     * `ClearedTouchAndGo` issuance in the trace is preceded by a
     * non-empty `circuitIntent` for the target aircraft.
     */
    @Test
    fun `C4 — ClearedTouchAndGo never issued without circuitIntent declared`() {
        val trace = runFixture()
        val tower = trace.initial.controllers.values
            .firstOrNull { it.role == RoleName.TOWER && it.aerodromeId == AerodromeId("LOWG") }
            ?: error("LOWG_TOWER not in initial state")
        for (s in trace.steps) {
            val ev = s.event as? SimEvent.TransmissionStart ?: continue
            val tx = ev.transmission
            val u = tx.utterance as? Utterance.FromController ?: continue
            val instr = u.output as? ControllerOutput.Instruct ?: continue
            val payload = (instr.dispatch as? xyz.easiersaid.twr.controller.bdi.Dispatch.Direct)?.instruction
                ?: (instr.dispatch as? xyz.easiersaid.twr.controller.bdi.Dispatch.Conditional)?.instruction
            if (payload !is xyz.easiersaid.twr.protocol.ClearedTouchAndGo) continue
            val target = instr.target
            // Look up circuitIntent at the cursor immediately before this
            // transmission was emitted — i.e., the controller's state at
            // the moment of issuance.
            val intentAtCursor = s.state.beliefs[tower.id]?.circuitIntent?.get(target)
            check(intentAtCursor != null) {
                "C4 regression: ClearedTouchAndGo issued to ${target.value} at " +
                    "${tx.startedAt.millis}ms with empty circuitIntent — pre-fix default-T&G semantics."
            }
        }
    }
}
