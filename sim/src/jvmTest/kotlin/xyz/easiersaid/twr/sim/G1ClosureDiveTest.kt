package xyz.easiersaid.twr.sim

import arrow.core.Option
import arrow.core.getOrElse
import kotlin.test.Test
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.core.world.EntityRef
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

    /**
     * fn-8.3 Phase 2 — OnRunway-flicker / commitment ping-pong investigation.
     *
     * Phase 1 root-causes the wedge to a runaway commitment ping-pong
     * `Complete → AwaitApproach → LandingClearanceIssued → AwaitLandedObserved
     * → Complete` during the **airborne** portion of circuit 1 (before A
     * has touched down at all). Phase 2's first thread asks WHY the cycle
     * reaches `AwaitLandedObserved` and re-completes via `ARR-TNG-AIRBORNE`
     * over and over while the aircraft is genuinely airborne.
     *
     * This test prints, around every commitment-stage transition for
     * OE-ABC at LOWG_TOWER:
     *  - the SimEvent that produced the transition (event-tagged trace);
     *  - the controller's view of the aircraft (`positionPoint`, entity
     *    set including any RunwayRef, controller-side `onGround`, kinematic
     *    altitude from the ground-truth `AircraftState`);
     *  - the active commitment kind + circuitIntent slice at that cursor.
     *
     * Goal: surface whether the loop is driven by:
     *  (a) ArrivalPosition.OnRunway flickering (controller seeing the
     *      airborne aircraft as on-ground over the runway threshold —
     *      false-positive touchdown), OR
     *  (b) `readbackAdvancesToStage = AwaitLandedObserved` advancing
     *      stage on every fresh `ClearedTouchAndGo` readback (eager
     *      stage-advance from coordination ledger), then
     *      `ARR-TNG-AIRBORNE`'s airborne-only gate firing immediately
     *      because the aircraft never actually touched down, OR
     *  (c) something else entirely.
     */
    @Test
    fun `dive — OnRunway flicker + commitment ping-pong cause`() {
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
        val (_, _, trace) = runUntilWithStateTrace(state, initialEvents, until)

        // 1. Event-tagged commitment-stage transitions for A — same as the
        //    Phase 1 dive but each transition carries the SimEvent that
        //    produced the post-state. Crucially: when stage transitions
        //    (Complete) → (Complete) under the same `Stage.isComplete`
        //    flag, the underlying Stage object identity differs, so this
        //    surfaces every fresh AwaitDownwind etc.
        println("\n=== Event-tagged commitment.stage[$aId] @ $tower ===")
        // We walk all steps and re-extract stage at each step from the
        // post-state, recording (event, stage) tuples. Then print only
        // the rows where stage changed from prior, plus the first 30
        // post-airborne rows for context.
        data class StageRow(
            val time: SimTime,
            val stage: String,
            val eventName: String,
            val onGroundCtrl: Boolean,
            val onRunwayEntity: Boolean,
            val phase: String,
            val altM: Double,
            val ppoint: String,
        )

        val rows = mutableListOf<StageRow>()
        var prevStage: String? = trace.initial.beliefs[tower.id]
            ?.commitments?.get(aId)?.stage?.name
        rows += StageRow(
            time = trace.initial.now,
            stage = prevStage ?: "absent",
            eventName = "(initial)",
            onGroundCtrl = trace.initial.aircraft[aId]?.let { (it.altitudeM <= 0.5) } ?: true,
            onRunwayEntity = false,
            phase = trace.initial.aircraft[aId]?.phase?.let { it::class.simpleName ?: "?" } ?: "?",
            altM = trace.initial.aircraft[aId]?.altitudeM ?: 0.0,
            ppoint = trace.initial.aircraft[aId]?.positionPoint?.value ?: "?",
        )
        for (s in trace.steps) {
            val stageName = s.state.beliefs[tower.id]
                ?.commitments?.get(aId)?.stage?.name ?: "absent"
            val ac = s.state.aircraft[aId]
            val pp = ac?.positionPoint
            val entitiesAtPp = pp?.let { loaded.worldIndex.entitiesByPoint[it] }.orEmpty()
            val onRunwayEntity = entitiesAtPp.any { it is EntityRef.RunwayRef }
            rows += StageRow(
                time = s.time,
                stage = stageName,
                eventName = s.event::class.simpleName ?: "?",
                onGroundCtrl = ac?.let { it.altitudeM <= 0.5 } ?: true,
                onRunwayEntity = onRunwayEntity,
                phase = ac?.phase?.let { it::class.simpleName ?: "?" } ?: "?",
                altM = ac?.altitudeM ?: 0.0,
                ppoint = pp?.value ?: "?",
            )
        }

        // Print only stage-change rows (with surrounding 1 row of context).
        val changeIndices = rows.indices.filter { i ->
            i > 0 && rows[i].stage != rows[i - 1].stage
        }
        println("Stage transitions (with triggering event + aircraft snapshot):")
        for (idx in changeIndices) {
            val r = rows[idx]
            println(
                "  [${r.time.millis}ms] ${rows[idx - 1].stage} → ${r.stage} " +
                    "via ${r.eventName} | phase=${r.phase} alt=${"%.1f".format(r.altM)}m " +
                    "pp=${r.ppoint} onRwyEntity=${r.onRunwayEntity} " +
                    "ctrlOnGround=${r.onGroundCtrl}",
            )
        }

        // 2. Histogram: how many transitions land in each stage during the
        //    "airborne portion of circuit 1" window (Phase 1 noted
        //    569000-1155000ms in the prior dive).
        println("\n=== Stage-transition histogram (airborne window 569000-1155000ms) ===")
        val window = 569_000L..1_155_000L
        val winChanges = changeIndices
            .filter { rows[it].time.millis in window }
        val byStage = winChanges.groupingBy { rows[it].stage }.eachCount()
        byStage.entries.sortedByDescending { it.value }.forEach { (stage, n) ->
            println("  $stage: $n transitions")
        }
        println("  total transitions in window: ${winChanges.size}")

        // 3. Stage-transition cycle pattern in the window: what's the
        //    most common previous→current transition?
        println("\n=== Stage-transition pair frequency (airborne window) ===")
        val pairs = winChanges.groupingBy { idx ->
            "${rows[idx - 1].stage} → ${rows[idx].stage}"
        }.eachCount()
        pairs.entries.sortedByDescending { it.value }.forEach { (pair, n) ->
            println("  $pair: $n")
        }

        // 4. Entity audit at the points the ping-pong walks through. The
        //    BDI guard atoms `OnApproach`, `OnCircuitLeg(FINAL)`, `OnRunway`
        //    are entity-set-derived (worldIndex.entitiesByPoint[point]).
        //    If `LandingConditions` (AnyOf(OnApproach, OnCircuitLeg(FINAL)))
        //    evaluates true at LOWG_ANCHOR_CIRCUIT_SE_ENTRY (or any
        //    base-leg point), the ping-pong's airborne-fire bug isn't an
        //    OnRunway flicker — it's a premature ARR-LAND-TNG firing on
        //    base/anchor points.
        println("\n=== Entity audit for points seen during airborne window ===")
        val seenPoints = changeIndices
            .filter { rows[it].time.millis in window }
            .map { rows[it].ppoint }
            .distinct()
        seenPoints.forEach { ppName ->
            val ppId = xyz.easiersaid.twr.protocol.PointId(ppName)
            val ents = loaded.worldIndex.entitiesByPoint[ppId].orEmpty()
            val onApproach = ents.any { it is EntityRef.ApproachRef }
            val onRunway = ents.any { it is EntityRef.RunwayRef }
            val circuitLegs = loaded.worldIndex.circuitLegsByPoint[ppId].orEmpty()
            val onFinalLeg = xyz.easiersaid.twr.core.world.LegName.FINAL in circuitLegs
            val onBaseLeg = xyz.easiersaid.twr.core.world.LegName.BASE in circuitLegs
            val onDownwindLeg = xyz.easiersaid.twr.core.world.LegName.DOWNWIND in circuitLegs
            val tag = buildString {
                if (onApproach) append("Approach ")
                if (onRunway) append("Runway ")
                if (onFinalLeg) append("FINAL ")
                if (onBaseLeg) append("BASE ")
                if (onDownwindLeg) append("DOWNWIND ")
            }.trim().ifEmpty { "(no relevant entities)" }
            println("  $ppName → $tag")
        }

        // 5. Aircraft snapshot during a representative ping-pong cycle —
        //    pick the first 3 cycles in the window, print every step with
        //    aircraft + stage info.
        println("\n=== First 3 ping-pong cycles — full step dump ===")
        // A "cycle" starts at a Complete→<not Complete> transition.
        val cycleStartIndices = changeIndices
            .filter { rows[it - 1].stage == "Complete" && rows[it].time.millis in window }
            .take(3)
        for ((cycleIdx, startIdx) in cycleStartIndices.withIndex()) {
            // End: next time stage returns to Complete OR +20s window cap.
            val endIdx = (startIdx until rows.size)
                .firstOrNull { i ->
                    i > startIdx && rows[i].stage == "Complete" ||
                        rows[i].time.millis - rows[startIdx].time.millis > 20_000
                } ?: rows.lastIndex
            println("\n  --- cycle ${cycleIdx + 1} (start=${rows[startIdx].time.millis}ms) ---")
            for (i in startIdx..endIdx) {
                val r = rows[i]
                val pre = if (i == startIdx) "*" else " "
                println(
                    "  $pre [${r.time.millis}ms] ${r.stage} " +
                        "via ${r.eventName} | phase=${r.phase} " +
                        "alt=${"%.1f".format(r.altM)}m onRwyEntity=${r.onRunwayEntity}",
                )
            }
        }
    }
}
