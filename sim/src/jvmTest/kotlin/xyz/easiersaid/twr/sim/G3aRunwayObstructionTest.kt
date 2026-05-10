package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.core.world.RunwayObstruction
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
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.RunwayObstructionInformation
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
import xyz.easiersaid.twr.sim.testing.runwayObstructionTransitions
import xyz.easiersaid.twr.sim.testing.transitionsOf

/**
 * G3a-obstruction — single-aerodrome single-aircraft VFR ATC-instructed
 * go-around triggered by a world-authored runway obstruction.
 *
 * Single AI aircraft at LOWG flies a single planned circuit
 * (`HighLevelGoal.CircuitTraining(outcomes = [FullStop])`). After the
 * tower issues `ClearedToLand` for circuit 1, the test's per-tick world
 * hook authors `runway.obstruction = RunwayObstruction(clearsAt = now +
 * 60.seconds)` one-shot. The sim's per-cycle world-diff producer derives
 * a `ControllerEvent.RunwayObstructionDetected`, the tower's belief
 * folds it into `BeliefState.runwayObstructions`, the reactive
 * `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule fires (with `Immediate`
 * advancement back to `AwaitDownwind`), the tower transmits
 * `GoAround` + the mandatory `RunwayObstructionInformation` companion
 * (ICAO Doc 4444 §7.4.1.4.1(c) — reason on radio), the pilot's
 * `AtcGoAroundOnFinal` recognition + `applyAtcInitiatedGoAround` Tick A
 * + reused Tick B planner execute, the obstruction expires at
 * `clearsAt`, the recovery circuit is re-cleared to land, the aircraft
 * full-stops and vacates.
 *
 * **Sibling tests:**
 *  - G0 ([LowgGoldenTest]) — single-aerodrome, single-aircraft circuit
 *    training (full-stop only; no GA). Structural template G3a-
 *    obstruction mirrors.
 *  - G1 ([G1TwoAircraftCircuitsTest]) — single-aerodrome, two-aircraft
 *    circuits=2 (T&G + full-stop). Vacate-coordination closure
 *    invariants G3a-obstruction's R7 pin reuses landed in G1.
 *  - G1 minimal ([G1TwoAircraftMinimalSpec]) — two-aircraft circuits=1
 *    multi-aircraft commitment-stage closure pin.
 *  - G2 ([G2CrossAerodromeVfrTest]) — single-aircraft cross-aerodrome
 *    VFR transit (LOWG → LJMB).
 *  - G3a ([G3aPilotTrainedGoAroundTest]) — single-aircraft single-
 *    aerodrome **pilot-trained** go-around (instructor-authored
 *    `CircuitOutcome.GoAround` outcome). G3a-obstruction is the
 *    reactive-ATC sibling: the controller initiates the GA in response
 *    to a world-state change rather than the pilot following a planned
 *    GA from the mission tree.
 *
 * **What G3a-obstruction distinctively pins:**
 *  - **World-only test trigger:** the test authors `runway.obstruction`
 *    via the `onAfterEvent` hook in `runUntilWithStateTrace`. Sim's
 *    expiry/diff/event/fold pipeline does the rest — no direct
 *    `ControllerEvent` injection, no `BeliefState` mutation. Per
 *    `feedback_world_only_test_triggers.md`. **One-shot authorship**:
 *    a `var obstructionAuthored` guard ensures `Some → Some(new
 *    clearsAt)` never happens, preserving the `clearsAt` immutability
 *    invariant (fn-12 epic Decision #4). Defense-in-depth: the sim's
 *    world-diff producer `check(...)`s the invariant and throws on
 *    violation.
 *  - **End-to-end stack:** the entire pipeline — world author → sim
 *    expiry → per-controller world-diff → `RunwayObstructionDetected`
 *    event → controller belief fold → `RunwayObstructed` guard +
 *    `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule + `Immediate` advancement
 *    + `obstructionGoAroundIssuedThisAttempt` witness — exercises in
 *    one run. Controller-level unit tests
 *    ([xyz.easiersaid.twr.controller.ObstructionGoAroundSpec]) pin
 *    supersession and no-refire; pilot-level unit tests
 *    ([xyz.easiersaid.twr.pilot.PilotAtcInitiatedGoAroundSpec]) pin
 *    Tick A intent + recognition discriminator. This test pins the
 *    composition.
 *  - **Three-layer pin pattern** (per fn-11.2 discipline, extended
 *    with separated decision-cycle vs transmission-start timestamps):
 *    - Layer 1a — **Decision-cycle causal partial-order**:
 *      `RunwayObstructionDetected.decisionTime <= GoAround.decisionTime
 *      == Stage_regression(<from-stage> → AwaitDownwind).time` (same
 *      tick via `Immediate` advancement);
 *      `RunwayObstructionCleared.decisionTime <
 *      ClearedToLand_recovery.decisionTime` (pre-clearance gate
 *      ungates). The transitions of `BeliefState.runwayObstructions`
 *      give the decision-cycle observability surface; the
 *      commitment-stage transitions give the regression time.
 *    - Layer 1b — **Radio-transmission partial-order**:
 *      `GoAround.txStart < RunwayObstructionInformation.txStart <
 *      Report(GoingAround).txStart < Report(RunwayVacated).txStart`.
 *      `applyControllerOutputs` serializes outputs on the same
 *      frequency (each subsequent transmission starts at the prior
 *      transmission's `endsAt`), so `GoAround.txStart` and the
 *      companion's `txStart` are **NOT equal** — strict `<`. They DO
 *      share a controller decision/output cycle (same
 *      `deriveCompanionOutputs` invocation), but transmission-start
 *      times diverge.
 *    - Layer 2 — **Sticky-witness regression at GoAround decision-
 *      cycle time** via `Immediate` advancement (NOT via the
 *      `GA-POST-CLEAR` interrupt — by the time `Report(GoingAround)`
 *      arrives, the stage is already `AwaitDownwind` and the
 *      interrupt's `fromStages` no longer matches): exactly one
 *      `<from-stage> → AwaitDownwind` transition observed, where
 *      `<from-stage> ∈ {LandingClearanceIssued, AwaitLandedObserved}`
 *      (post-clearance because `T_obs > T_ClearedToLand`; which
 *      specific post-clearance stage depends on radio queue / tick
 *      cadence — both are valid per Decision Context #3a). Post-
 *      regression sticky witnesses
 *      (`touchedDownDuringCommitment`,
 *      `observedReportsDuringCommitment`) are reset via fn-8.3's
 *      reset machinery.
 *    - Layer 3 — **Kinematic non-event**: the aircraft does NOT enter
 *      `LandingRoll` or `Vacating` phase before
 *      `Report(GoingAround).time` — the obstruction-driven GA
 *      prevents touchdown on circuit 1.
 *  - **Per-controller event scoping:** exactly one
 *    `RunwayObstructionDetected` belief-transition is observed in the
 *    TOWER controller's `runwayObstructions` slice for runway 16C (one
 *    Detected per obstruction lifetime). Per fn-12 Decision #3 the
 *    events are per-controller-scoped; this pin scopes to the TOWER's
 *    belief slice rather than a global trace count.
 *  - **Companion transmission pin:** alongside the `GoAround`
 *    instruction, a `RunwayObstructionInformation` companion is emitted
 *    in the same controller-output cycle (both come from the same
 *    `deriveCompanionOutputs` invocation), with `GoAround.txStart <
 *    RunwayObstructionInformation.txStart` after radio serialization.
 *    Per ICAO Doc 4444 §7.4.1.4.1(c) and §8.9.6.1.8, the reason for the
 *    GA is mandatory.
 *  - **R7 vacate-coordination closure** (per fn-8.3 discipline): after
 *    the recovery circuit's full-stop landing, the tower's coordination
 *    ledger contains no leftover `AfterLandingVacateVia` /
 *    `BacktrackRunway` entries for the aircraft.
 *  - **Doctrine separation:** physical-occupancy (`RunwayPhysicallyClear`)
 *    vs declared-obstruction (`RunwayObstructed`) — the
 *    `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule reads the
 *    `BeliefState.runwayObstructions` slice (world-state-derived events)
 *    rather than the runway-occupancy slice. The companion-info
 *    transmission distinguishes the cause on the radio.
 *
 * **Time band** (per epic R10 + fn-8.3 decision #11 inheritance):
 * observed completion wall on first GREEN run (single aircraft, one
 * planned circuit, ATC-instructed GA + recovery circuit + 60s
 * obstruction wait). The ±15% band catches doctrine regressions while
 * absorbing run-to-run jitter. After first GREEN the observed wall is
 * captured in fn-12.3's `## Evidence` section; the band may be
 * retightened on closure-pass calibration. The constants below are
 * the load-bearing tightness pin.
 *
 *   - observed completion wall: 1_399_000 ms (~23.3 sim minutes)
 *   - lower bound (×0.85): 1_189_150 ms (~19.8 min)
 *   - upper bound (×1.15): 1_608_850 ms (~26.8 min)
 *
 * Rationale: single-aircraft, single planned circuit + recovery circuit
 * (provided by `handleGoAround`'s tree rewrite — `CircuitAfterGoAround
 * = [goAroundTask, circuitTask]`) + 60s obstruction wait. The wall is
 * comparable to G3a-trained's ~1393 s — both run two equivalent
 * circuits with one go-around. A doctrine regression that materially
 * alters the reactive-GA cadence, the obstruction-info radio
 * serialization, or the recovery-circuit re-entry geometry would push
 * the wall outside this band.
 *
 * **Doctrinal anchors:**
 *  - **ICAO Doc 4444 §7.4.1.4.1**: *"In the event of a runway becoming
 *    obstructed during the approach phase of an arriving aircraft, ATC
 *    shall ... in all cases inform the aircraft of the runway
 *    incursion or obstruction."* The obstruction-driven GA + companion-
 *    info transmission realise this.
 *  - **ICAO Doc 4444 §8.9.6.1.8**: *"in all such cases, the reason for
 *    the instruction or the advice should be given to the pilot."* The
 *    `RunwayObstructionInformation` companion carries the reason.
 *  - **CAP 413 §4.65**: missed-approach / go-around phraseology.
 *
 * Verbatim verifications anchor on the same research/txt extracts as
 * the controller-level `ObstructionGoAroundSpec`.
 */
class G3aRunwayObstructionTest {

    @Test
    fun `world-authored runway obstruction triggers ATC GA and recovery landing at LOWG`() {
        // ── World + controllers via the shared fixture ──────────────────────
        // G3a-obstruction reuses Fixtures.LOWG (single-aircraft single-
        // aerodrome). The distinguishing surface is the world-state mutation
        // — the `runway.obstruction` field is authored at runtime via the
        // sim runner's `onAfterEvent` hook. No new fixture authoring required;
        // world-only test triggers per `feedback_world_only_test_triggers.md`
        // (the trigger is the world-state mutation, not an injected event
        // or rigged decision).
        val fixture = Fixtures.LOWG
        val loaded = fixture.load().getOrElse {
            fail("LOWG fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val rwy = RunwayId("16C")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) {
            "GROUND missing from LOWG fixture"
        }
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) {
            "TOWER missing from LOWG fixture"
        }

        // ── One AI aircraft, mission = single full-stop circuit ─────────────
        // Mission goal: `HighLevelGoal.CircuitTraining(outcomes =
        // listOf(CircuitOutcome.FullStop))` — a single planned circuit. The
        // recovery circuit (after the ATC-instructed GA) is provided
        // automatically by `handleGoAround`'s tree rewrite
        // (`CircuitAfterGoAround = [goAroundTask, circuitTask]`); no
        // second outcome is needed and adding one would produce three
        // landing attempts (GA + recovery-FullStop + remaining-FullStop)
        // which is not the intended scenario.
        //
        // NO `CircuitOutcome.GoAround` in the list — this is reactive ATC
        // GA, not pilot-trained. The pilot's recognition arm
        // (`recognizeAtcInitiatedGoAround` + `applyAtcInitiatedGoAround`)
        // fires off the `pendingAtcGoAroundFrom` flag set in
        // `handleGoAround` when the controller's `Instruction.GoAround`
        // arrives.
        val aircraftId = AircraftId("OE-ABC")
        val now = SimTime.ZERO
        val singleFullStop = HighLevelGoal.CircuitTraining(
            outcomes = listOf(CircuitOutcome.FullStop),
        )
        val mission = createMission(
            goal = singleFullStop,
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
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(lowg to fixture.weather),
        ).getOrElse { error("SimState.initial rejected the LOWG fixture: $it") }

        // ── ATIS + drive ────────────────────────────────────────────────────
        // 30 sim minutes generous ceiling. One planned circuit + 60s
        // obstruction wait + recovery circuit should land in well under
        // that. The ±15% time band below is the load-bearing tightness pin.
        val until = SimTime.ZERO + SimDuration.ofMillis(30 * 60 * 1000L)
        val lowgAtis = Atis(
            letter = 'A',
            aerodrome = lowg,
            configuration = RunwayConfiguration(
                arrivals = listOf(rwy),
                departures = listOf(rwy),
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

        // ── One-shot world-state authorship via `onAfterEvent` ──────────────
        //
        // The test authors `runway.obstruction = RunwayObstruction(clearsAt
        // = now + 60.seconds)` AFTER the aircraft is on phase=Final AND
        // a `ClearedToLand` has been issued for circuit 1 (the
        // `T_obs > T_ClearedToLand` constraint in the task spec). The
        // hook checks **both** conditions on every step's post-step state;
        // the first state that satisfies them triggers the one-shot
        // authorship.
        //
        // Why both conditions? `phase=Final` alone fires too early (the
        // GA-PRE-CLEAR path would activate); `ClearedToLand` alone fires
        // too late (the readback may complete before phase=Final). The
        // intersection pins the rule's `LandingClearanceIssued` /
        // `AwaitLandedObserved` from-stages.
        //
        // **One-shot guard**: `var obstructionAuthored = false` ensures
        // `runway.obstruction` is set exactly once. Re-writing `Some →
        // Some(new clearsAt)` would refresh the `clearsAt` field and
        // violate the immutability invariant (fn-12 epic Decision #4).
        // The sim's world-diff producer `check(...)`s this invariant and
        // throws an `IllegalStateException` on violation — defense-in-
        // depth, but the hook guard is the first line of defense.
        //
        // The 60-second window is short enough to keep the run time
        // bounded (the recovery circuit re-enters downwind quickly) and
        // long enough for the obstruction to persist through the
        // `GoAround` instruction issuance + readback + the pilot's
        // climb-out phase. By the time the recovery circuit reaches
        // final, the obstruction has expired and the pre-clearance gate
        // re-opens.
        var obstructionAuthored = false
        val obstructionAuthoredAt = arrayOf<SimTime?>(null)
        val obstructionClearsAt = arrayOf<SimTime?>(null)
        val onAfterEvent: (SimEvent, SimState) -> SimState = { _, st ->
            if (obstructionAuthored) {
                st
            } else if (!aircraftIsOnFinalWithLandingClearance(st, aircraftId, tower.id, rwy)) {
                st
            } else {
                obstructionAuthored = true
                val clearsAt = st.now + SimDuration.ofSeconds(60)
                obstructionAuthoredAt[0] = st.now
                obstructionClearsAt[0] = clearsAt
                authorRunwayObstruction(st, lowg, rwy, RunwayObstruction(clearsAt = clearsAt))
            }
        }

        val (finalState, records, trace) = runUntilWithStateTrace(
            initialState = initialState,
            initialEvents = initialEvents,
            untilTime = until,
            onAfterEvent = onAfterEvent,
        )

        // ── Diagnostic preamble ─────────────────────────────────────────────
        val journey = finalState.formatJourney(aircraftId, records)
        println(journey)

        println()
        println("─── G3a-obstruction per-aircraft trace summary ───")
        println("Obstruction authored at: ${obstructionAuthoredAt[0]?.millis ?: "<NEVER>"}ms")
        println("Obstruction clearsAt:    ${obstructionClearsAt[0]?.millis ?: "<NEVER>"}ms")
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
        println("Runway-obstructions belief transitions (tower, 16C):")
        for (t in trace.runwayObstructionTransitions(tower.id, rwy)) {
            val fromStr = t.from.fold({ "absent" }, { "Some(clearsAt=${it.clearsAt.millis}ms)" })
            val toStr = t.to.fold({ "absent" }, { "Some(clearsAt=${it.clearsAt.millis}ms)" })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("positionPoint transitions:")
        for (t in trace.positionPointTransitions(aircraftId)) {
            val fromStr = t.from.fold({ "absent" }, { it.value })
            val toStr = t.to.fold({ "absent" }, { it.value })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("Phase transitions:")
        for (t in trace.transitionsOf { st -> st.aircraft[aircraftId]?.phase }) {
            println("  [${t.after.time.millis}ms] ${t.from} → ${t.to}")
        }
        println("─── end G3a-obstruction per-aircraft trace summary ───")
        println()

        // ── One-shot authorship pin (defensive) ─────────────────────────────
        check(obstructionAuthored) {
            "World-authorship hook never fired — `aircraftIsOnFinalWithLandingClearance` " +
                "never returned true. Either the aircraft never reached phase=Final, or " +
                "ClearedToLand was never issued for circuit 1. This is a pre-condition " +
                "regression — the rest of the test's pins assume the obstruction was authored.\n$journey"
        }

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

        val standPoints = loaded.world.aerodromes
            .getValue(lowg)
            .stands.values.map { it.point }.toSet()
        check(finalAircraft.positionPoint in standPoints) {
            "Aircraft did not end at a LOWG stand point. positionPoint=${finalAircraft.positionPoint}; " +
                "valid stand points: $standPoints.\n$journey"
        }

        // ── Per-controller event scoping pin ───────────────────────────────
        //
        // Exactly one `None → Some(...)` transition in the TOWER's
        // `runwayObstructions[16C]` belief slice (one Detected per
        // obstruction lifetime), and exactly one `Some → None` transition
        // (one Cleared). Per fn-12 Decision #3 the events are per-controller
        // scoped; this pin scopes to TOWER's belief slice. Defense-in-depth
        // for the one-shot authorship guard above.
        val obstructionBeliefTransitions = trace.runwayObstructionTransitions(tower.id, rwy)
        val detectedBeliefTransitions = obstructionBeliefTransitions.filter { t ->
            t.from.fold({ true }, { false }) && t.to.fold({ false }, { true })
        }
        val clearedBeliefTransitions = obstructionBeliefTransitions.filter { t ->
            t.from.fold({ false }, { true }) && t.to.fold({ true }, { false })
        }
        check(detectedBeliefTransitions.size == 1) {
            "Expected exactly one None → Some(...) transition in TOWER's runwayObstructions[$rwy] " +
                "belief slice (one Detected per obstruction lifetime), observed " +
                "${detectedBeliefTransitions.size}. More than one would indicate the world hook " +
                "fired multiple times (one-shot guard regression); zero would indicate the diff " +
                "producer didn't observe the world-state change.\n$journey"
        }
        check(clearedBeliefTransitions.size == 1) {
            "Expected exactly one Some → None transition in TOWER's runwayObstructions[$rwy] " +
                "belief slice (one Cleared per obstruction lifetime via expiry), observed " +
                "${clearedBeliefTransitions.size}. Zero would indicate the expiry pass didn't " +
                "null the obstruction at `clearsAt`; more than one is impossible (the slice " +
                "transitions strictly `None ↔ Some`).\n$journey"
        }
        val detectedTransition = detectedBeliefTransitions.single()
        val clearedTransition = clearedBeliefTransitions.single()
        val detectedMs = detectedTransition.after.time.millis
        val clearedMs = clearedTransition.after.time.millis

        // ── Layer 1b — Radio-transmission causal partial-order pins ─────────
        //
        // Transmission ordering (radio serialization):
        //   ClearedToLand(circuit 1) ≺ GoAround ≺
        //   RunwayObstructionInformation (companion) ≺ Report(GoingAround)
        //   ≺ ClearedToLand(recovery) ≺ Report(RunwayVacated).
        //
        // `applyControllerOutputs` serializes outputs on the same
        // frequency; each subsequent transmission starts at the prior
        // transmission's `endsAt`. So GoAround.txStart < companion.txStart
        // (strict <), not equal.

        // Find all ClearedToLand records targeting this aircraft via
        // `Dispatch.Direct.instruction` (mirroring G3a-trained's pattern).
        val landRecords = records.filter { rec ->
            val out = (rec.utterance as? Utterance.FromController)?.output
                as? ControllerOutput.Instruct ?: return@filter false
            val instr = (out.dispatch as? Dispatch.Direct)?.instruction ?: return@filter false
            out.target == aircraftId && instr is ClearedToLand
        }

        val landCircuit1Record = landRecords.firstOrNull()
            ?: fail(
                "Expected at least one ClearedToLand for $aircraftId on circuit 1 — " +
                    "the obstruction GA is post-clearance by hook construction; the controller's " +
                    "ARR-LAND rule must clear the aircraft to land BEFORE the world hook authors " +
                    "the obstruction.\n$journey"
            )
        val landCircuit1Ms = landCircuit1Record.time.millis

        // Find the GoAround instruction record.
        val goAroundInstrRecord = records.firstControllerInstructionOf<GoAround>(aircraftId)
            .getOrElse {
                fail(
                    "Controller never transmitted GoAround to $aircraftId — the reactive " +
                        "obstruction-GA rule did not fire. Expected: " +
                        "ARR-GO-AROUND-RUNWAY-OBSTRUCTED fires when RunwayObstructed becomes true " +
                        "on a post-clearance commitment.\n$journey"
                )
            }
        val goAroundMs = goAroundInstrRecord.time.millis

        // Find the RunwayObstructionInformation companion record.
        val companionRecord = records.firstOrNull { rec ->
            val out = (rec.utterance as? Utterance.FromController)?.output ?: return@firstOrNull false
            val respond = out as? ControllerOutput.Respond ?: return@firstOrNull false
            respond.target == aircraftId && respond.response is RunwayObstructionInformation
        } ?: fail(
            "Controller never transmitted RunwayObstructionInformation companion to $aircraftId " +
                "— the obstruction-info reason-on-radio (ICAO §7.4.1.4.1(c) + §8.9.6.1.8) was not " +
                "emitted alongside GoAround. The companion is mandatory; without it the GA " +
                "instruction lacks the doctrinal reason.\n$journey"
        )
        val companionMs = companionRecord.time.millis
        val companion = ((companionRecord.utterance as Utterance.FromController).output
            as ControllerOutput.Respond).response as RunwayObstructionInformation
        check(companion.runway == rwy) {
            "Companion RunwayObstructionInformation.runway mismatch: ${companion.runway} vs $rwy.\n$journey"
        }
        check(companion.clearsAt == obstructionClearsAt[0]!!) {
            "Companion RunwayObstructionInformation.clearsAt mismatch: ${companion.clearsAt} vs " +
                "authored ${obstructionClearsAt[0]}. The companion's clearsAt is the obstruction's " +
                "clearsAt from the BeliefState.runwayObstructions slice; drift here indicates the " +
                "ObstructionInfo carrier on the ProposedAction dropped or mutated the field.\n$journey"
        }

        // Pilot's GoingAround report.
        val goingAroundRecord = records.firstPilotReportOf<ReportEvent.GoingAround>(aircraftId)
            .getOrElse {
                fail(
                    "Pilot never transmitted Report(GoingAround) — the ATC-reactive GA " +
                        "recognition did not fire. Expected: `applyAtcInitiatedGoAround` Tick A " +
                        "produces phase=Final + route=None; the existing GOING_AROUND step then " +
                        "emits Report(GoingAround) per CAP 413 §4.67 phraseology.\n$journey"
                )
            }
        val goingAroundMs = goingAroundRecord.time.millis

        // First ClearedToLand AFTER GoingAround — recovery circuit's clearance.
        val landRecoveryRecord = landRecords.firstOrNull { it.time.millis > goingAroundMs }
            ?: fail(
                "Expected at least one ClearedToLand for $aircraftId AFTER Report(GoingAround) — " +
                    "the recovery circuit re-enters the pattern and is re-cleared to land after " +
                    "the obstruction expires.\n$journey"
            )
        val landRecoveryMs = landRecoveryRecord.time.millis

        // Report(RunwayVacated) — the canonical landing-complete observable.
        val vacatedMs = records.firstPilotReportOf<ReportEvent.RunwayVacated>(aircraftId)
            .map { it.time.millis }
            .getOrElse {
                fail(
                    "Expected at least one Report(RunwayVacated) from $aircraftId — without it, " +
                        "the aircraft never confirmed leaving the runway after the recovery " +
                        "circuit's landing.\n$journey"
                )
            }

        // Layer 1b chain assertions: full transmission-start partial order.
        check(landCircuit1Ms < goAroundMs) {
            "Pre-clearance pin: ClearedToLand(circuit 1) (${landCircuit1Ms}ms) must precede " +
                "GoAround (${goAroundMs}ms). Reversal would indicate either (i) the obstruction " +
                "was authored before ClearedToLand (one-shot hook condition regression), or " +
                "(ii) the reactive rule fired from a pre-clearance stage (the test's `T_obs > " +
                "T_ClearedToLand` constraint was violated).\n$journey"
        }
        check(goAroundMs < companionMs) {
            "Companion serialization pin: GoAround.txStart (${goAroundMs}ms) must precede " +
                "RunwayObstructionInformation.txStart (${companionMs}ms). `applyControllerOutputs` " +
                "serializes outputs on the same frequency; the companion's txStart equals the " +
                "GoAround's `endsAt`. Equality (txStart == txStart) would indicate the radio " +
                "serializer didn't queue the second transmission — both would talk over each " +
                "other on air, a doctrinal violation.\n$journey"
        }
        check(companionMs < goingAroundMs) {
            "Radio chain pin: RunwayObstructionInformation.txStart (${companionMs}ms) must " +
                "precede Report(GoingAround).txStart (${goingAroundMs}ms). The pilot reads the " +
                "GA instruction (after both controller transmissions arrive), executes Tick A, " +
                "and then transmits the GoingAround report per CAP 413 §4.67.\n$journey"
        }
        check(goingAroundMs < landRecoveryMs && landRecoveryMs < vacatedMs) {
            "Recovery chain pin: expected " +
                "Report(GoingAround) (${goingAroundMs}ms) < " +
                "ClearedToLand(recovery) (${landRecoveryMs}ms) < " +
                "Report(RunwayVacated) (${vacatedMs}ms). If ClearedToLand(recovery) is at-or-" +
                "before GoingAround, the filter picked up circuit 1's clearance; if " +
                "RunwayVacated is at-or-before recovery clearance, the aircraft landed without " +
                "ATC having re-cleared it.\n$journey"
        }

        // ── Layer 1a — Decision-cycle causal partial-order pins ────────────
        //
        // The decision-cycle timestamps live on `BeliefState.runwayObstructions`
        // transitions (the belief slice flips on the controller-cycle event
        // that folds the world-diff event into beliefs) and on the commitment-
        // stage transitions (the rule fires within the same cycle).
        //
        // Two pins here:
        //   1. `Detected.decisionTime <= GoAround.decisionTime` — the rule
        //      fires in the cycle that sees RunwayObstructed=true. The
        //      controller's decision happens at the same tick as the belief
        //      fold (same `controllerDecide` invocation), and the radio
        //      transmission queue starts at that tick. So
        //      `Detected.decisionTime <= GoAround.txStart`.
        //   2. `Cleared.decisionTime < ClearedToLand(recovery).decisionTime`
        //      — the pre-clearance landing gate (`Not(RunwayObstructed)`)
        //      requires the obstruction to be absent from beliefs before
        //      `ARR-LAND` can re-issue clearance.
        check(detectedMs <= goAroundMs) {
            "Decision-cycle pin: RunwayObstructionDetected belief transition " +
                "(${detectedMs}ms) must occur at-or-before GoAround.txStart (${goAroundMs}ms). " +
                "Equality is allowed (belief fold + rule fire in same controllerDecide cycle); " +
                "strict > would indicate the rule fired before the belief was updated — a " +
                "fold-vs-rule ordering regression.\n$journey"
        }
        check(clearedMs < landRecoveryMs) {
            "Pre-clearance ungate pin: RunwayObstructionCleared belief transition " +
                "(${clearedMs}ms) must precede ClearedToLand(recovery).txStart (${landRecoveryMs}ms). " +
                "If recovery clearance fires before the obstruction clears in beliefs, the " +
                "Not(RunwayObstructed) gate on LandingConditions did not block — a doctrine " +
                "regression on the landing gate.\n$journey"
        }

        // ── Layer 2 — Sticky-witness regression pin ─────────────────────────
        //
        // The `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule fires with `Immediate`
        // advancement to `AwaitDownwind`. The commitment regresses from one
        // of `{LandingClearanceIssued, AwaitLandedObserved}` (the test's
        // `T_obs > T_ClearedToLand` constraint pins post-clearance; which
        // specific post-clearance stage depends on radio queue + tick
        // cadence — both are valid per Decision Context #3a). The existing
        // `GA-POST-CLEAR` interrupt does NOT fire for this path: by the time
        // `Report(GoingAround)` arrives, the stage is already `AwaitDownwind`
        // and the interrupt's `fromStages` no longer matches.
        //
        // Pin assertion: exactly one regression transition with from-stage
        // in `{LandingClearanceIssued, AwaitLandedObserved}` to
        // `AwaitDownwind`. The regression time equals the GoAround's
        // controller-output-cycle time (`Stage_regression.time ==
        // GoAround_decision.time`). We assert `Stage_regression.time <=
        // GoAround.txStart` (decision-cycle is at-or-before transmission-
        // start; the controller decides first, then the radio queue
        // begins serializing).

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
                "AwaitDownwind for the obstruction-driven GA, observed ${regressions.size}. More " +
                "than one would indicate the rule fired multiple times (no-refire witness " +
                "regression); zero would indicate the rule didn't fire (guard regression, " +
                "rule-id drift, or belief-fold regression).\n$journey"
        }
        val regression = regressions.single()

        check(regression.after.time.millis <= goAroundMs) {
            "Decision-cycle pin: stage regression at ${regression.after.time.millis}ms must " +
                "be at-or-before GoAround.txStart (${goAroundMs}ms). `Immediate` advancement " +
                "happens in the same controllerDecide cycle as the rule fire; the radio " +
                "transmission queue then starts. Regression strictly AFTER GoAround.txStart " +
                "would indicate the stage advanced via the `GA-POST-CLEAR` interrupt instead " +
                "of `Immediate` advancement — a different code path.\n$journey"
        }

        // Post-regression sticky witnesses are reset. Same shape as
        // G3a-trained's Layer 2 reset pin.
        val commitmentAfter = regression.after.state.beliefs[tower.id]
            ?.commitments?.get(aircraftId)
            ?: fail(
                "Tower commitment for $aircraftId missing AT regression cursor — the regression " +
                    "should preserve the commitment (stage drops, commitment lives), not delete " +
                    "it. If absent, the rule's Option A model has degenerated to close-and-new " +
                    "(Option B) silently.\n$journey"
            )
        check(!commitmentAfter.touchedDownDuringCommitment) {
            "touchedDownDuringCommitment must be reset post-regression (stage transition at " +
                "${regression.after.time.millis}ms); got " +
                "touchedDownDuringCommitment=${commitmentAfter.touchedDownDuringCommitment}. " +
                "Without the reset, ARR-TNG-AIRBORNE could fire a stale touchdown decision on " +
                "the recovery circuit.\n$journey"
        }
        check(commitmentAfter.observedReportsDuringCommitment.isEmpty()) {
            "observedReportsDuringCommitment must be reset post-regression (stage transition at " +
                "${regression.after.time.millis}ms); got " +
                "${commitmentAfter.observedReportsDuringCommitment.size} entries: " +
                "${commitmentAfter.observedReportsDuringCommitment}. " +
                "Without the reset, HasReportedPositionCall could be satisfied by stale " +
                "circuit-1 reports, allowing ARR-LAND to re-issue ClearedToLand prematurely on " +
                "the recovery circuit (the pre-clearance position-call discipline from " +
                "fn-8.3 B5-α).\n$journey"
        }
        check(commitmentAfter.obstructionGoAroundIssuedThisAttempt) {
            "Witness obstructionGoAroundIssuedThisAttempt must be set post-fire (no-refire " +
                "discipline per fn-12 R7-no-refire). The witness lives on the Commitment and is " +
                "cleared on the next Report(Downwind) (fresh commitment lifecycle) or commitment " +
                "replacement.\n$journey"
        }

        // ── Layer 3 — Kinematic non-event pin ───────────────────────────────
        //
        // The obstruction-driven GA prevents touchdown on circuit 1: no
        // `LandingRoll` or `Vacating` phase entered before Report(GoingAround)
        // is recorded. Any state where `phase in {LandingRoll, Vacating}`
        // BEFORE the GoingAround time would indicate the aircraft physically
        // touched down despite the ATC-issued GA — the reactive recognition
        // failed to prevent landing.
        val phaseTransitions = trace.transitionsOf { st ->
            st.aircraft[aircraftId]?.phase
        }
        val touchdownBeforeGA = phaseTransitions.any { t ->
            val landed = t.to == PilotPhase.LandingRoll || t.to == PilotPhase.Vacating
            landed && t.after.time.millis < goingAroundMs
        }
        check(!touchdownBeforeGA) {
            "Aircraft entered LandingRoll or Vacating BEFORE Report(GoingAround) (${goingAroundMs}ms) — " +
                "circuit 1 should NOT have touched down. The ATC-reactive GA must fire in time " +
                "to prevent landing on the obstructed runway. Touchdown before GA indicates " +
                "either (i) the pilot's recognition arm missed the GoAround instruction, " +
                "(ii) the discriminator (Circuit-mode + phase=Final + flag set) failed to " +
                "match, or (iii) the radio-delivery latency exceeded the time-to-touchdown " +
                "window.\nPhase transitions: " +
                phaseTransitions.joinToString { "${it.to}@${it.after.time.millis}ms" } +
                "\n$journey"
        }

        // ── R7 — Vacate-coordination closure pin ────────────────────────────
        //
        // After the recovery circuit's full-stop landing, the tower's
        // coordination ledger contains NO pending `AfterLandingVacateVia` /
        // `BacktrackRunway` entries for the aircraft — the vacate readback
        // closed the coordination per fn-8.3's discipline. Mirrors the
        // closure pattern from G3a-trained's R7 pin at the obstruction
        // scenario scale.
        val towerBeliefs = checkNotNull(finalState.beliefs[tower.id]) {
            "Tower beliefs missing at end of run — controller pipeline regression.\n$journey"
        }
        val acCoordinations = towerBeliefs.coordinations[aircraftId].orEmpty()
        val vacateCoords = acCoordinations.filter { coord ->
            coord.instruction is AfterLandingVacateVia ||
                coord.instruction is BacktrackRunway
        }
        check(vacateCoords.isEmpty()) {
            "R7 vacate-coordination closure: after the recovery circuit's vacate readback, " +
                "the tower's coordination ledger must contain no `AfterLandingVacateVia` / " +
                "`BacktrackRunway` entries for $aircraftId. Got ${vacateCoords.size} unclosed " +
                "entries: $vacateCoords.\nIf this fires, the readback close path broke — either " +
                "the pilot's vacate readback was malformed, or `acceptReadback` failed to " +
                "remove the matching entry.\n$journey"
        }

        // ── Time band (R10 — ±15% around observed wall) ──────────────────────
        //
        // First-green observed wall (fn-12.3): captured below for the LOWG
        // single-aircraft single-planned-circuit + obstruction-GA + recovery
        // circuit + 60 s obstruction wait scenario. ±15% band catches doctrine
        // timing regressions while absorbing run-to-run jitter. Captured in
        // fn-12.3 evidence; rebaseline if doctrine shifts (per fn-8.3
        // decision #11 inheritance).
        val completionCursor = trace.firstWhere { st ->
            st.aircraft[aircraftId]?.pilotMission?.isComplete == true
        }.getOrElse {
            fail("Mission never reached isComplete during the trace.\n$journey")
        }
        val completionMs = completionCursor.time.millis
        val observedCompletionMs = 1_399_000L
        val band = (observedCompletionMs * 0.15).toLong()
        val minMs = observedCompletionMs - band
        val maxMs = observedCompletionMs + band
        check(completionMs in minMs..maxMs) {
            "Mission completion (${completionMs / 1000} s = ${completionMs}ms) outside the ±15% " +
                "band [${minMs / 1000} s, ${maxMs / 1000} s] centred on the observed wall " +
                "(${observedCompletionMs / 1000} s). Drift indicates a doctrine regression " +
                "affecting the obstruction-GA cadence — reactive-rule fire latency, recovery-" +
                "circuit re-entry geometry, or radio serialization on the GA + companion + " +
                "readback chain. actual=${completionMs}ms expected=${observedCompletionMs}ms " +
                "band=±${band}ms.\n$journey"
        }
    }

    /**
     * Predicate for the one-shot world-authorship hook: the aircraft is on
     * `phase=Final` AND the tower's commitment for the aircraft sits in
     * a **post-clearance** stage (`LandingClearanceIssued` or
     * `AwaitLandedObserved`).
     *
     * Uses post-step belief state (the hook runs after the step that
     * produced this state). Both conditions read from the post-step
     * snapshot, so they are observed against the same instant.
     *
     * Stage-based rather than coordination-based: the `LandingClearanceIssued`
     * stage entry is the load-bearing "ClearedToLand issued" signal in the
     * BDI machine; coordinations close quickly on readback and may already
     * be empty by the time `phase=Final` is reached. Reading the stage gives
     * a stable post-clearance window spanning both pre-readback
     * (`LandingClearanceIssued`) and post-readback pre-touchdown
     * (`AwaitLandedObserved`) — exactly the `T_obs > T_ClearedToLand`
     * window the task spec calls for. Both stages are also the rule's
     * eligible from-stages.
     */
    private fun aircraftIsOnFinalWithLandingClearance(
        st: SimState,
        aircraft: AircraftId,
        towerId: xyz.easiersaid.twr.protocol.ControllerId,
        @Suppress("UNUSED_PARAMETER") rwy: RunwayId,
    ): Boolean {
        val ac = st.aircraft[aircraft] ?: return false
        if (ac.phase != PilotPhase.Final) return false
        val commitment = st.beliefs[towerId]?.commitments?.get(aircraft) ?: return false
        val stage = commitment.stage
        return stage == TowerArrivalStage.LandingClearanceIssued ||
            stage == TowerArrivalStage.AwaitLandedObserved
    }

    /**
     * Pure world-state mutation: set `aerodromes[$aerodromeId].runways[$rwy]
     * .obstruction = Some(obstruction)`. The mutation chain rebuilds the
     * nested data classes via `copy` so the existing `state.world` reference
     * survives unchanged structurally elsewhere (immutable persistence).
     *
     * Fails loudly if the aerodrome or runway is absent — those are
     * fixture-load preconditions and a missing key here indicates a
     * mismatched fixture/test pairing.
     *
     * The one-shot guard at the call site ensures this is only ever
     * called once per obstruction lifetime; defense-in-depth lives in
     * `sim.runwayObstructionEvents`'s `check(...)` which throws an
     * `IllegalStateException` if a `Some(old) → Some(new clearsAt)`
     * transition reaches the diff producer.
     */
    private fun authorRunwayObstruction(
        st: SimState,
        aerodromeId: AerodromeId,
        rwy: RunwayId,
        obstruction: RunwayObstruction,
    ): SimState {
        val aerodrome = checkNotNull(st.world.aerodromes[aerodromeId]) {
            "Aerodrome $aerodromeId missing from world — fixture/test mismatch"
        }
        val runway = checkNotNull(aerodrome.runways[rwy]) {
            "Runway $rwy missing from aerodrome $aerodromeId — fixture/test mismatch"
        }
        val updatedRunway = runway.copy(obstruction = obstruction)
        val updatedAerodrome = aerodrome.copy(runways = aerodrome.runways + (rwy to updatedRunway))
        val updatedWorld = st.world.copy(
            aerodromes = st.world.aerodromes + (aerodromeId to updatedAerodrome),
        )
        return st.copy(world = updatedWorld)
    }
}
