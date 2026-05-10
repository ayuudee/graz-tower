package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.commitmentStageTransitions
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstControllerInstructionOf
import xyz.easiersaid.twr.sim.testing.firstPilotReportOf
import xyz.easiersaid.twr.sim.testing.firstWhere
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.missionStepTransitions
import xyz.easiersaid.twr.sim.testing.positionPointTransitions
import xyz.easiersaid.twr.sim.testing.responsibilityTransitions
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace
import xyz.easiersaid.twr.sim.testing.transitionsOf

/**
 * G3a — single-aerodrome single-aircraft VFR pilot-trained go-around as
 * circuit-training outcome.
 *
 * Single AI aircraft at LOWG flies a two-circuit training mission where
 * **circuit 1 is explicitly authored as a go-around** (the instructor's
 * training plan dictates a planned GA at short-final) and circuit 2 is
 * a full-stop landing. The mission tree is forked at compile time
 * (`HighLevelGoal.CircuitTraining(outcomes = listOf(GoAround, FullStop))`):
 * the pilot follows the static tree autonomously; no sensor-driven
 * recognition path fires. Closes the gap left by
 * `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md`'s
 * open ask: "Any mission type that supports go-around must have a
 * go-around integration test before merge."
 *
 * **Sibling tests:**
 *  - G0 ([LowgGoldenTest]) — single-aerodrome, single-aircraft circuit
 *    training (full-stop only; no GA). Structural template G3a mirrors.
 *  - G1 ([G1TwoAircraftCircuitsTest]) — single-aerodrome, two-aircraft
 *    circuits=2 (T&G + full-stop). The vacate-coordination closure
 *    pattern G3a's R7 pin reuses landed in G1.
 *  - G1 minimal ([G1TwoAircraftMinimalSpec]) — two-aircraft circuits=1
 *    multi-aircraft commitment-stage closure pin.
 *  - G2 ([G2CrossAerodromeVfrTest]) — single-aircraft cross-aerodrome
 *    VFR transit (LOWG → LJMB).
 *
 * **What G3a distinctively pins:**
 *  - **Trained-GA fork at compile time:** the `outcomes` list authors
 *    a planned go-around as a circuit-training outcome. The pilot's
 *    mission compiler reads the goal at `createMission` and forks the
 *    tree statically; from there decisions are autonomous. The world-
 *    only trigger is the goal authorship — no event injection, no
 *    rigged decisions.
 *  - **CAP 413 §4.66/§4.67/§4.68 phraseology:** pilot transmits
 *    `Report(GoingAround)` at short-final on circuit 1; the
 *    sticky-witness regression path fires off the controller's
 *    `GoAroundEvent` on receipt.
 *  - **Three-layer pin pattern** (per fn-11.2 plan-review:
 *    causal partial-order + sticky-witness regression + kinematic
 *    non-event). Each layer catches a different failure shape:
 *    - Layer 1 — **Causal partial-order**: `ClearedToLand(circuit 1) ≺
 *      Report(GoingAround) ≺ ClearedToLand(circuit 2) ≺
 *      Report(RunwayVacated)`. The pre-clearance pin
 *      (`ClearedToLand(c1) ≺ GoingAround`) distinguishes trained-GA-
 *      post-clearance from a hypothetical pre-clearance variant.
 *    - Layer 2 — **Sticky-witness regression** (Option A — codebase-
 *      aligned): tower's commitment for the aircraft regresses
 *      through GA-POST-CLEAR — exactly one transition `from in
 *      {LandingClearanceIssued, AwaitLandedObserved} → AwaitDownwind`
 *      observed during circuit 1; post-regression sticky witnesses
 *      (`touchedDownDuringCommitment`,
 *      `observedReportsDuringCommitment`) are reset.
 *    - Layer 3 — **Kinematic non-event**: aircraft does NOT enter
 *      `LandingRoll` phase before `Report(GoingAround).time` — the
 *      trained GA prevents touchdown on circuit 1.
 *  - **R7 vacate-coordination closure:** after circuit 2's full-stop
 *    landing, the tower's coordination ledger contains no pending
 *    `AfterLandingVacateVia` / `BacktrackRunway` entry for the
 *    aircraft (the vacate readback closed the entry per fn-8.3's
 *    discipline). Mirrors the multi-aircraft closure invariant from
 *    [G1TwoAircraftCircuitsTest] / [G1TwoAircraftMinimalSpec] at the
 *    single-aircraft scale.
 *  - **Radio-delivery prerequisite for `GA-POST-CLEAR`:** Layer 2's
 *    regression-pin is asserted **after** the pilot's
 *    `Report(GoingAround)` is observed in the records (the
 *    transmission must reach the tower before the regression rule
 *    fires). Pin shape: regression-time strictly greater than
 *    GoingAround-record time.
 *
 * **Time band** (per epic R8 + pass-7 plan-review finding #4):
 * observed completion wall ~1393 s (~23.2 sim minutes) for a single
 * aircraft flying two circuits with one trained GA at LOWG. The
 * ±15% band is centred on the observed value:
 *
 *   - observed completion wall: 1_393_000 ms (~23.2 sim minutes)
 *   - lower bound (×0.85): 1_184_050 ms (~19.7 min)
 *   - upper bound (×1.15): 1_601_950 ms (~26.7 min)
 *
 * Rationale: the mission is single-aircraft (no inter-aircraft
 * sequencing latency), comprises two pattern circuits plus the
 * GA detour with re-join-downwind + full-stop-on-circuit-2 cadence.
 * Observed cadence is approximately 2.3× G0's single-circuit wall
 * — the multiplier is driven by the GA climb-out + re-entry geometry
 * + the doubled REPORT_DOWNWIND / REPORT_BASE / FLY_FINAL legs across
 * two circuits, plus the per-step run-up / line-up dwell.
 *
 * The band is wide enough to absorb run-to-run jitter but narrow
 * enough to catch a doctrine regression that materially alters the
 * GA cadence (e.g. a climb-rate regression on the goAroundPath or a
 * stage-regression that fires too early/late). After first green the
 * band is captured in fn-11.2's `## Evidence` section and may be
 * retightened on closure-pass calibration.
 *
 * **Doctrinal anchors:**
 *  - **CAP 413 §4.67**: *"In the event of missed approach being
 *    initiated by the pilot, the phrase 'going around' shall be used."*
 *  - **CAP 413 §4.66**: VFR aircraft is to continue into the normal
 *    traffic circuit unless instructions are issued to the contrary.
 *  - **ICAO Doc 4444 §12.3.4.18**: pilot transmission `*GOING AROUND.`,
 *    controller acknowledgement `<callsign>, Roger`.
 *
 * Verbatim verifications live in `research/txt/cap413-aerodrome-chapter.txt`
 * lines 1075-1090 + `research/txt/icao4444-extracted.txt` lines 15233-15244.
 */
class G3aPilotTrainedGoAroundTest {

    @Test
    fun `pilot trains a go-around on circuit 1 and lands on circuit 2 at LOWG`() {
        // ── World + controllers via the shared fixture ──────────────────────
        // G3a reuses Fixtures.LOWG (single-aircraft single-aerodrome). The
        // distinguishing surface is the goal — the trained-GA outcome is
        // expressed entirely through `HighLevelGoal.CircuitTraining(outcomes
        // = [GoAround, FullStop])`. No new fixture authoring required;
        // world-only test triggers per `feedback_world_only_test_triggers.md`
        // (the trigger is the goal, not an injected event).
        val fixture = Fixtures.LOWG
        val loaded = fixture.load().getOrElse {
            fail("LOWG fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) {
            "GROUND missing from LOWG fixture"
        }
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) {
            "TOWER missing from LOWG fixture"
        }

        // ── One AI aircraft, mission = trained GA on circuit 1 + full-stop on circuit 2 ──
        // The terminal-FullStop invariant on `CircuitTraining` (init block)
        // requires the last outcome be FullStop, so the mission terminates
        // on the ground without wedging `groundArrivalTask`.
        val aircraftId = AircraftId("OE-ABC")
        val now = SimTime.ZERO
        val trainedGoAroundThenFullStop = HighLevelGoal.CircuitTraining(
            outcomes = listOf(CircuitOutcome.GoAround, CircuitOutcome.FullStop),
        )
        val mission = createMission(
            goal = trainedGoAroundThenFullStop,
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans[aircraftId]
                ?: fail("LOWG fixture missing flight plan for $aircraftId"),
        )

        // Wake category lives on `AircraftType` (per fn-8.3 spec convention).
        // Single-aircraft so wake-rule pin isn't load-bearing here, but
        // maintain the firewall-doctrine pattern (C172 → WakeCategory.L).
        val aircraft = AircraftState(
            id = aircraftId,
            callsign = Callsign("OEABC"),
            position = loaded.world.geometry.points.getValue(fixture.standPointId),
            positionPoint = fixture.standPointId,
            phase = PilotPhase.AtStand,
            type = AircraftType.C172,
            pilotMission = mission,
        )

        // ── Build SimState through the smart constructor ────────────────────
        // Weather REQUIRED for runway-bearing aerodromes — `SimState.initial`
        // rejects empty `weatherByAerodrome` for LOWG.
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(lowg to fixture.weather),
        ).getOrElse { error("SimState.initial rejected the LOWG fixture: $it") }

        // ── ATIS + drive ────────────────────────────────────────────────────
        // 30 sim minutes generous ceiling. Two circuits with a GA detour
        // should land in well under that. The ±15% time band below is the
        // load-bearing tightness pin per fn-8.3 decision #11. A wedged run
        // hits this wall first.
        val until = SimTime.ZERO + SimDuration.ofMillis(30 * 60 * 1000L)
        val lowgAtis = Atis(
            letter = 'A',
            aerodrome = lowg,
            configuration = RunwayConfiguration(
                arrivals = listOf(RunwayId("16C")),
                departures = listOf(RunwayId("16C")),
            ),
            wind = Wind.unsafe(160, 8),
            qnh = null,
            visibility = null,
            generatedAt = now,
        )
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = lowgAtis),
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )
        val (finalState, records, trace) = runUntilWithStateTrace(initialState, initialEvents, until)

        // ── Diagnostic preamble ─────────────────────────────────────────────
        // Critical for debugging when (not if) the trained-GA fork-point shifts.
        val journey = finalState.formatJourney(aircraftId, records)
        println(journey)

        println()
        println("─── G3a per-aircraft trace summary ───")
        println("Responsibility transitions:")
        for (t in trace.responsibilityTransitions(aircraftId)) {
            val fromStr = t.from.fold({ "absent" }, { it::class.simpleName ?: "?" })
            val toStr = t.to.fold({ "absent" }, { it::class.simpleName ?: "?" })
            println("  [${t.after.time.millis}ms] ${t.controller}: $fromStr → $toStr")
        }
        println("Mission step transitions:")
        for (t in trace.missionStepTransitions(aircraftId)) {
            val fromStr = t.from.fold({ "absent" }, { it.name })
            val toStr = t.to.fold({ "absent" }, { it.name })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("Commitment stage transitions (tower):")
        for (t in trace.commitmentStageTransitions(aircraftId, tower.id)) {
            val fromStr = t.from.fold({ "absent" }, { it.name })
            val toStr = t.to.fold({ "absent" }, { it.name })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("positionPoint transitions:")
        for (t in trace.positionPointTransitions(aircraftId)) {
            val fromStr = t.from.fold({ "absent" }, { it.value })
            val toStr = t.to.fold({ "absent" }, { it.value })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("─── end G3a per-aircraft trace summary ───")
        println()

        // ── Outcome pins: aircraft completes mission, parked at stand ──────
        val finalAircraft = finalState.aircraft.getValue(aircraftId)
        val finalMission = checkNotNull(finalAircraft.pilotMission) {
            "Aircraft lost its mission.\n$journey"
        }

        check(finalMission.isComplete) {
            "Mission did not complete within 30 sim minutes — wedge or under-budget.\n$journey"
        }
        check(finalAircraft.altitudeM == 0.0) {
            "Aircraft is not on the ground at end of run.\n$journey"
        }
        check(finalAircraft.phase == PilotPhase.Parked || finalAircraft.phase == PilotPhase.AtStand) {
            "Aircraft did not return to a stand. Final phase: ${finalAircraft.phase}.\n$journey"
        }

        // Stand membership uses `stands.values.map { it.point }.toSet()`,
        // mirroring `LowgGoldenTest.kt:444-446`.
        val standPoints = loaded.world.aerodromes
            .getValue(lowg)
            .stands.values.map { it.point }.toSet()
        check(finalAircraft.positionPoint in standPoints) {
            "Aircraft did not end at a LOWG stand point. positionPoint=${finalAircraft.positionPoint}; " +
                "valid stand points: $standPoints.\n$journey"
        }

        // ── Layer 1 — Causal partial-order pins ─────────────────────────────
        //
        // The chain pinned end-to-end:
        //   ClearedToLand(circuit 1) ≺ Report(GoingAround) ≺
        //   ClearedToLand(circuit 2) ≺ Report(RunwayVacated)
        //
        // The first ClearedToLand is for circuit 1 (the GA circuit). The
        // pilot reports `GoingAround` at short-final on circuit 1. The
        // controller then re-clears for circuit 2 after the trained GA
        // re-enters downwind. RunwayVacated comes after the circuit-2
        // landing.
        //
        // First-vs-lastOrNull: per fn-11.2 plan-review pass-9 finding #6,
        // we use `firstOrNull` over filtered records ordered by time
        // (`records` are append-ordered by time in the runner) to avoid
        // accidentally picking up a reissue under coordination escalation.
        // The first ClearedToLand AFTER GoingAround is the load-bearing
        // circuit-2 clearance.

        // Find the trained-GA transmission record — the foundational
        // observable. If absent, the trained-GA didn't fire at all.
        val goingAroundRecord = records.firstPilotReportOf<ReportEvent.GoingAround>(aircraftId)
            .getOrElse {
                fail(
                    "Pilot never transmitted Report(GoingAround) — the trained GA did not fire. " +
                        "Expected: pilot's mission tree compiles `GoAround` outcome into a " +
                        "FLY_FINAL_TO_SHORT_FINAL → GOING_AROUND step pair on circuit 1.\n$journey"
                )
            }
        val goingAroundMs = goingAroundRecord.time.millis

        // Find ALL ClearedToLand records targeting this aircraft (in time
        // order — `records` is monotonically time-ordered).
        val landRecords = records.filter { rec ->
            val out = (rec.utterance as? Utterance.FromController)?.output
                as? xyz.easiersaid.twr.controller.ControllerOutput.Instruct ?: return@filter false
            out.target == aircraftId && out.instruction is ClearedToLand
        }

        // First ClearedToLand precedes the trained-GA — this is circuit 1's
        // clearance, satisfying the `GA-POST-CLEAR` regression-source pin
        // per epic R6 (the trained GA is post-clearance because the pilot
        // reports Downwind/Base which satisfies HasReportedPositionCall;
        // the controller then issues ClearedToLand BEFORE the pilot's
        // GoingAround call at short-final).
        val landCircuit1Ms = landRecords.firstOrNull()?.time?.millis
            ?: fail(
                "Expected at least one ClearedToLand for $aircraftId on circuit 1 — " +
                    "the trained GA is post-clearance (per epic R6 pass-3 finding #2): the " +
                    "controller's ARR-LAND rule clears the aircraft to land during downwind/base " +
                    "BEFORE the pilot announces going-around at short-final.\n$journey"
            )

        // ClearedToLand(circuit 1) ≺ Report(GoingAround) — the pre-clearance
        // pin per epic R6 + pass-3 plan-review finding #2. Distinguishes
        // trained-GA-post-clearance from a hypothetical pre-clearance variant.
        check(landCircuit1Ms < goingAroundMs) {
            "Pre-clearance pin: ClearedToLand(circuit 1) (${landCircuit1Ms}ms) must precede " +
                "Report(GoingAround) (${goingAroundMs}ms). For a trained-GA post-clearance " +
                "scenario, the controller clears to land during downwind/base; the pilot then " +
                "announces going-around at short-final. Reversal would indicate either (i) the " +
                "ARR-LAND rule didn't fire pre-GA (the trained-GA scenario degenerated to " +
                "pre-clearance), or (ii) the FLY_FINAL_TO_SHORT_FINAL step completed too early " +
                "(altitude gate fired before the controller had a chance to clear).\n$journey"
        }

        // First ClearedToLand AFTER GoingAround — circuit 2's clearance.
        val landCircuit2Record = landRecords.firstOrNull { it.time.millis > goingAroundMs }
            ?: fail(
                "Expected at least one ClearedToLand for $aircraftId AFTER Report(GoingAround) — " +
                    "the trained pilot rejoins the circuit and is re-cleared to land on circuit 2.\n$journey"
            )
        val landCircuit2Ms = landCircuit2Record.time.millis

        // Report(RunwayVacated) — the canonical landing-complete observable,
        // mirroring G0's pattern.
        val vacatedMs = records.firstPilotReportOf<ReportEvent.RunwayVacated>(aircraftId)
            .map { it.time.millis }
            .getOrElse {
                fail(
                    "Expected at least one Report(RunwayVacated) from $aircraftId — without it, " +
                        "the aircraft never confirmed leaving the runway after the circuit-2 landing.\n$journey"
                )
            }

        // Causal chain: GoingAround ≺ ClearedToLand(c2) ≺ RunwayVacated.
        check(goingAroundMs < landCircuit2Ms && landCircuit2Ms < vacatedMs) {
            "Causal chain violated: expected " +
                "Report(GoingAround) (${goingAroundMs}ms) < " +
                "ClearedToLand(c2) (${landCircuit2Ms}ms) < " +
                "Report(RunwayVacated) (${vacatedMs}ms).\n" +
                "If the ClearedToLand(c2) time is at-or-before the GoingAround time, the " +
                "filter accidentally picked up circuit 1's clearance reissue. If RunwayVacated " +
                "is at-or-before the c2 clearance, the aircraft landed without ATC having " +
                "re-cleared it — a doctrinal regression.\n$journey"
        }

        // ── Layer 2 — Sticky-witness regression pins (Option A) ─────────────
        //
        // Per Decision Context #3 (Option A — codebase-aligned post fn-8.3):
        // the commitment lifecycle SURVIVES the GA; the stage REGRESSES from
        // a post-clearance stage (LandingClearanceIssued or AwaitLandedObserved)
        // back to AwaitDownwind via `GA-POST-CLEAR` (TowerArrival.kt:148-156).
        // The same commitment lifetime spans circuit-1 fork → rejoin downwind
        // → circuit-2 land. Sticky witnesses (`touchedDownDuringCommitment`,
        // `observedReportsDuringCommitment`) are RESET on regression by
        // `Controller.advanceCommittedStages` + `isStageRegression`.
        //
        // Radio-delivery prerequisite (per epic R6 + pass-9 plan-review
        // finding #3): `GA-POST-CLEAR` regression depends on `GoAroundEvent`
        // which fires on received `Report(GoingAround)`. The records-collection
        // membership of the GoingAround transmission (above) is the
        // delivery-witness; the regression-pin's stage-transition time being
        // strictly AFTER the GoingAround record's time is the second witness.

        val stageTransitions = trace.commitmentStageTransitions(aircraftId, tower.id)
        val postClearStages = setOf<TowerArrivalStage>(
            TowerArrivalStage.LandingClearanceIssued,
            TowerArrivalStage.AwaitLandedObserved,
        )
        val regressions = stageTransitions.filter { t ->
            val from = t.from.fold({ null }, { it as? TowerArrivalStage }) ?: return@filter false
            val to = t.to.fold({ null }, { it as? TowerArrivalStage }) ?: return@filter false
            from in postClearStages && to == TowerArrivalStage.AwaitDownwind
        }

        check(regressions.size == 1) {
            "Expected exactly one stage regression {LandingClearanceIssued | AwaitLandedObserved} → " +
                "AwaitDownwind on the trained-GA circuit, observed ${regressions.size}. " +
                "More than one would indicate the GA-POST-CLEAR rule fired multiple times " +
                "(unexpected for a single trained-GA scenario); zero would indicate the " +
                "GoAroundEvent was not delivered to the controller (radio-delivery failure) " +
                "or the regression rule didn't fire (rule-id drift).\n$journey"
        }
        val regression = regressions.single()

        // Radio-delivery prerequisite: regression fires AFTER the
        // GoingAround transmission was emitted. Without delivery the
        // GoAroundEvent doesn't fire → no regression.
        check(regression.after.time.millis > goingAroundMs) {
            "Radio-delivery prerequisite: stage regression at ${regression.after.time.millis}ms " +
                "must fire AFTER Report(GoingAround) at ${goingAroundMs}ms. A regression fired " +
                "AT-OR-BEFORE the GoingAround record would indicate either (i) the records' " +
                "GoingAround was a duplicate (delivery happened earlier on a different record) or " +
                "(ii) the regression fired off some other channel — neither acceptable for " +
                "GA-POST-CLEAR which gates on `GoAroundEvent` delivered from the radio.\n$journey"
        }

        // Post-regression sticky witnesses are reset. The fresh commitment
        // (after regression) holds the default values.
        val commitmentAfter = regression.after.state.beliefs[tower.id]
            ?.commitments?.get(aircraftId)
            ?: fail(
                "Tower commitment for $aircraftId missing AT regression cursor — the " +
                    "regression should preserve the commitment (stage drops, commitment lives), " +
                    "not delete it. If absent, Option A's stage-regression model has degenerated " +
                    "to close-and-new (Option B) silently.\n$journey"
            )
        check(!commitmentAfter.touchedDownDuringCommitment) {
            "touchedDownDuringCommitment must be reset post-regression (stage transition at " +
                "${regression.after.time.millis}ms); got " +
                "touchedDownDuringCommitment=${commitmentAfter.touchedDownDuringCommitment}. " +
                "Without the reset, ARR-TNG-AIRBORNE could fire a stale touchdown decision on " +
                "the next circuit (the runaway commitment shape fn-8.3 closed via B2).\n$journey"
        }
        check(commitmentAfter.observedReportsDuringCommitment.isEmpty()) {
            "observedReportsDuringCommitment must be reset post-regression (stage transition at " +
                "${regression.after.time.millis}ms); got " +
                "${commitmentAfter.observedReportsDuringCommitment.size} entries: " +
                "${commitmentAfter.observedReportsDuringCommitment}. " +
                "Without the reset, HasReportedCircuitPosition could be satisfied by stale " +
                "circuit-1 reports, allowing ARR-LAND to re-issue ClearedToLand prematurely on " +
                "circuit 2 (the pre-clearance position-call discipline fn-8.3 closed via B5-α).\n$journey"
        }

        // ── Layer 3 — Kinematic non-event pins ──────────────────────────────
        //
        // The trained GA prevents touchdown on circuit 1: no `LandingRoll`
        // phase entered between Final and Climbing on the GA circuit.
        // Pinning by phase-transition cursor: any state where
        // `phase == LandingRoll` BEFORE the GoingAround time would indicate
        // the aircraft physically touched down despite the planned GA —
        // the trained GA failed to prevent landing.
        //
        // Per pass-9 plan-review finding #7, we use `transition.after.time`
        // (when the new phase is observed), not `before.time`.
        val phaseTransitions = trace.transitionsOf { st ->
            st.aircraft[aircraftId]?.phase
        }
        val landingRollBeforeGA = phaseTransitions.any { t ->
            t.to == PilotPhase.LandingRoll && t.after.time.millis < goingAroundMs
        }
        check(!landingRollBeforeGA) {
            "Aircraft entered LandingRoll BEFORE Report(GoingAround) (${goingAroundMs}ms) — " +
                "circuit 1 should NOT have touched down. The trained-GA fork should fire at " +
                "short-final altitude (DECISION_ALTITUDE_M ~100m AGL) and divert via the " +
                "go-around path; entering LandingRoll means the FLY_FINAL_TO_SHORT_FINAL step " +
                "fell through to a landing.\nPhase transitions: " +
                phaseTransitions.joinToString { "${it.to}@${it.after.time.millis}ms" } +
                "\n$journey"
        }

        // ── R7 — Vacate-coordination closure pin ────────────────────────────
        //
        // After circuit 2's full-stop landing, the tower's coordination
        // ledger contains NO pending `AfterLandingVacateVia` /
        // `BacktrackRunway` entries for the aircraft — the vacate readback
        // closed the coordination per fn-8.3's discipline. Mirrors the
        // multi-aircraft closure invariant from G1/G1-minimal at the
        // single-aircraft scale per epic R7.
        //
        // Mission-completed pin alone doesn't catch a lingering
        // coordination — the ledger entry could persist past mission
        // completion if the close path broke.
        val towerBeliefs = checkNotNull(finalState.beliefs[tower.id]) {
            "Tower beliefs missing at end of run — controller pipeline regression.\n$journey"
        }
        val acCoordinations = towerBeliefs.coordinations[aircraftId].orEmpty()
        val vacateCoords = acCoordinations.filter { coord ->
            coord.instruction is AfterLandingVacateVia ||
                coord.instruction is BacktrackRunway
        }
        check(vacateCoords.isEmpty()) {
            "R7 vacate-coordination closure: after circuit 2's vacate readback, the tower's " +
                "coordination ledger must contain no `AfterLandingVacateVia` / `BacktrackRunway` " +
                "entries for $aircraftId. Got ${vacateCoords.size} unclosed entries: " +
                "$vacateCoords.\nIf this fires, the readback close path broke — either the " +
                "pilot's vacate readback was malformed, or `acceptReadback` failed to remove " +
                "the matching entry.\n$journey"
        }

        // ── Time band (R8 — ±15% around observed wall) ──────────────────────
        //
        // Observed completion wall: ~600 s (10 sim minutes) for the
        // single-aircraft trained-GA-then-full-stop scenario at LOWG.
        // ±15% band centred on observed value. The band catches doctrine
        // timing regressions; if the run completes outside, either
        // (i) the GA detour cadence has shifted (climb-rate regression on
        // the GA path, route-planner regression for goAroundPath), or
        // (ii) the underlying single-circuit cadence changed (shared with
        // G0's pin). After-first-green calibration in fn-11.2 evidence.
        val completionCursor = trace.firstWhere { st ->
            st.aircraft[aircraftId]?.pilotMission?.isComplete == true
        }.getOrElse {
            fail("Mission never reached isComplete during the trace.\n$journey")
        }
        val completionMs = completionCursor.time.millis
        // First-green observed wall (fn-11.2): 1_393_000 ms (~23.2 min) on
        // the LOWG single-aircraft trained-GA-then-FullStop scenario.
        // ±15% band catches doctrine regressions while absorbing run-to-run
        // jitter. Captured in fn-11.2 evidence; rebaseline if doctrine
        // shifts (per fn-8.3 decision #11 inheritance).
        val observedCompletionMs = 1_393_000L
        val band = (observedCompletionMs * 0.15).toLong()
        val minMs = observedCompletionMs - band
        val maxMs = observedCompletionMs + band
        check(completionMs in minMs..maxMs) {
            "Mission completion (${completionMs / 1000} s = ${completionMs}ms) outside the ±15% " +
                "band [${minMs / 1000} s, ${maxMs / 1000} s] centred on the observed wall " +
                "(${observedCompletionMs / 1000} s). Drift indicates a doctrine regression " +
                "affecting the trained-GA cadence — most likely a climb-rate / route-planner " +
                "shift on the goAroundPath, OR an upstream shift in single-circuit cadence " +
                "(also covered by G0's band). actual=${completionMs}ms expected=${observedCompletionMs}ms " +
                "band=±${band}ms.\n$journey"
        }
    }
}
