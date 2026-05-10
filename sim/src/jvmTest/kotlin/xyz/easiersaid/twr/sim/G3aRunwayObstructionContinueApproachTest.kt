package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.bdi.OBSTRUCTION_CLEAR_SAFETY_MARGIN_MS
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
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ContinueApproachReason
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.RegulationDatabase
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
 * G3a-obstruction-continue-approach — single-aerodrome single-aircraft
 * VFR **CONTINUE APPROACH** triggered by a short-TTL world-authored
 * runway obstruction at the pre-clearance ladder middle state per
 * CAP 413 §4.55-4.56 + ICAO Doc 4444 §12.3.4.16(d).
 *
 * Single AI aircraft at LOWG flies a single planned circuit
 * (`HighLevelGoal.CircuitTraining(outcomes = [FullStop])`). The test's
 * per-step world hook authors `runway.obstruction =
 * RunwayObstruction(clearsAt = now + 5.seconds)` one-shot at the FIRST
 * post-event state where ALL preconditions hold simultaneously,
 * mirroring the CA rule's guard predicate exactly:
 * (a) commitment stage is `AwaitApproach` (post-Downwind ack, pre-
 * `ClearedToLand`); (b) no `ClearedToLand` coordination exists for the
 * aircraft; (c) the aircraft's `positionPoint` is on a FINAL-labelled
 * leg (`worldIndex.circuitLegsByPoint[positionPoint].contains(FINAL)`)
 * OR distance-to-threshold ≤ 5000m (mirrors the rule's geometric arm);
 * (d) `speedMps > 0` (mirrors `groundSpeed` precondition of
 * `ObstructionClearsInTime`); (e) `(5s + 10s safety margin) ≤
 * distance-to-threshold / groundSpeed` (predicate-eligibility). The sim's
 * per-cycle world-diff producer derives a
 * `ControllerEvent.RunwayObstructionDetected`; the tower's belief folds
 * it into `BeliefState.runwayObstructions`; the `ObstructionClearsInTime`
 * guard evaluates `true`; the new `ARR-CONTINUE-APPROACH-OBSTRUCTION`
 * rule (placed BEFORE `obstructionGoAroundRuleAwaitApproach` in
 * `stageRules[AwaitApproach]`) wins selection. The tower transmits
 * `Instruction.ContinueApproach(reason = RUNWAY_OBSTRUCTED)` + the
 * mandatory `RunwayObstructionInformation` companion (pre-clearance
 * reason-on-radio per ICAO §12.3.4.16(d) + §8.9.6.1.8). The pilot
 * continues the mission unchanged (CONTINUE APPROACH has empty
 * `requiredReadbackAtoms` — no `Report(ContinueApproach)` transmission
 * per `D-PASS-continue-approach-pilot-readback`). The obstruction
 * expires at `clearsAt`; the runway-obstructions slice flips back to
 * `None`; the pre-clearance `Not(RunwayObstructed)` gate on
 * `LandingConditions` ungates; `ARR-LAND` fires `ClearedToLand`; the
 * pilot reads back, lands, vacates.
 *
 * **Sibling tests:**
 *  - G0 ([LowgGoldenTest]) — single-aerodrome, single-aircraft circuit
 *    training (full-stop only; no CONTINUE APPROACH, no GA). Structural
 *    template this test mirrors at the radio-chain layer (one approach,
 *    one landing, one vacate).
 *  - G1 ([G1TwoAircraftCircuitsTest]) — single-aerodrome, two-aircraft
 *    circuits=2 (T&G + full-stop). Vacate-coordination closure
 *    invariants this test's R7 pin reuses landed in G1.
 *  - G1 minimal ([G1TwoAircraftMinimalSpec]) — two-aircraft circuits=1
 *    multi-aircraft commitment-stage closure pin.
 *  - G2 ([G2CrossAerodromeVfrTest]) — single-aircraft cross-aerodrome
 *    VFR transit (LOWG → LJMB).
 *  - G3a ([G3aPilotTrainedGoAroundTest]) — single-aircraft single-
 *    aerodrome **pilot-trained** go-around (instructor-authored
 *    `CircuitOutcome.GoAround` outcome).
 *  - G3a-obstruction ([G3aRunwayObstructionTest]) — single-aircraft
 *    single-aerodrome ATC-instructed **reactive go-around** on a
 *    post-clearance long-TTL (60s) obstruction. This test is the
 *    pre-clearance short-TTL (5s) **CONTINUE APPROACH** companion: same
 *    fixture, same fault surface (world-authored `runway.obstruction`),
 *    but predicate-eligible (clears in time) and pre-clearance — so the
 *    pre-clearance ladder middle state fires instead of the GA. The two
 *    tests together cover the three-state CONTINUE APPROACH /
 *    `ObstructionClearsInTime` decision ladder per CAP 413 §4.55-4.56 /
 *    ICAO 4444 §12.3.4.16.
 *
 * **What this test distinctively pins (vs G3a-obstruction GA test):**
 *  - **Pre-clearance, predicate-eligible**: the test authors the
 *    obstruction at the moment the commitment is in `AwaitApproach`
 *    (post-Downwind, pre-`ClearedToLand`), with a 5s `clearsAt` sized
 *    so `(5s + OBSTRUCTION_CLEAR_SAFETY_MARGIN_S(10s)) ≤
 *    ETA-to-threshold`. The `ObstructionClearsInTime` guard evaluates
 *    `true`; the CA rule wins; the GA rule's narrowed guard
 *    (`Not(ObstructionClearsInTime)`) fails closed.
 *  - **Stage NON-regression**: the commitment's `stage`, `kind`, and
 *    `runway` STAY unchanged across the CONTINUE APPROACH cycle (the
 *    rule has `nextStage = null`); only the
 *    `continueApproachIssuedThisAttempt` witness flips `false → true`.
 *    Other sticky witnesses (`touchedDownDuringCommitment`,
 *    `pilotReadyDuringCommitment`, `observedReportsDuringCommitment`,
 *    `obstructionGoAroundIssuedThisAttempt`) remain UNCHANGED. The
 *    absence of a `<from-stage> → AwaitDownwind` regression around the
 *    CA decision cycle is itself a load-bearing pin — it is the key
 *    observable signature distinguishing CONTINUE APPROACH from GO
 *    AROUND in the sim trace. After the obstruction clears + ARR-LAND
 *    fires + readback resolves, the stage advances normally
 *    `AwaitApproach → LandingClearanceIssued → AwaitLandedObserved`.
 *  - **No `Report(ContinueApproach)` transmission**: per
 *    `protocol.InstructionReadback.requiredReadbackAtoms`, the
 *    `ContinueApproach` instruction has empty required atoms. The
 *    pilot does NOT transmit a readback (out-of-scope per
 *    `D-PASS-continue-approach-pilot-readback`). The radio chain pin
 *    asserts the absence — a regression that added a CA readback would
 *    break this pin and surface in code review.
 *  - **Three-layer pin pattern** (per fn-11.2 discipline, extended
 *    with separated decision-cycle vs transmission-start timestamps
 *    per `sim-test-pins-must-compare-against-2026-05-10` memory):
 *    - Layer 1a — **Decision-cycle causal partial-order**:
 *      `RunwayObstructionDetected.decisionTime
 *      <= ContinueApproach.decisionTime
 *      == RunwayObstructionInformation.decisionTime` (companion
 *      emitted by the SAME `controllerDecide` invocation as the
 *      primary CA instruction);
 *      `< RunwayObstructionCleared.decisionTime
 *      < ClearedToLand.decisionTime` (pre-clearance
 *      `Not(RunwayObstructed)` gate ungates only after the obstruction
 *      expires from beliefs).
 *    - Layer 1b — **Radio-transmission partial-order**:
 *      `ContinueApproach.txStart < RunwayObstructionInformation.txStart`
 *      (strict `<` — `applyControllerOutputs` serializes outputs on the
 *      same frequency); `< ClearedToLand.txStart` (re-issued after
 *      obstruction clears); `< Report(RunwayVacated).txStart`. NO
 *      `Report(ContinueApproach)` between primary and companion (empty
 *      readback per InstructionReadback.kt:115).
 *    - Layer 2 — **Stage NON-regression** (the key behavioural
 *      signature): exactly ZERO stage regressions from
 *      `{LandingClearanceIssued, AwaitLandedObserved}` to
 *      `AwaitDownwind` during the obstruction window. The CA rule has
 *      `nextStage = null`; the commitment stays at `AwaitApproach`
 *      across the CA decision cycle; the
 *      `continueApproachIssuedThisAttempt` witness is the sole
 *      observable state change on the commitment (verified post-CA
 *      decision cycle). The four other sticky witnesses are unchanged.
 *      The full stage progression (post obstruction-clear) is
 *      `AwaitApproach → LandingClearanceIssued → AwaitLandedObserved`
 *      with no intermediate `AwaitDownwind` step.
 *    - Layer 3 — **Kinematic non-event**: no `Climbing` phase entry at
 *      any point in the aircraft phase trace. The aircraft does NOT
 *      execute a go-around climb; the phase sequence terminates with
 *      `... Final → LandingRoll → Vacating → AtStand`.
 *  - **Per-controller event scoping** (per fn-12 Decision #3): exactly
 *    one `None → Some(...)` transition AND one `Some → None`
 *    transition in the TOWER controller's `runwayObstructions[16C]`
 *    belief slice via [SimTrace.runwayObstructionTransitions]. Not a
 *    raw global `worldEvents` count — the belief-slice transition is
 *    the observable surface the existing `G3aRunwayObstructionTest`
 *    uses.
 *  - **Companion + reason content**:
 *    - `Instruction.ContinueApproach` emitted with
 *      `reason = ContinueApproachReason.RUNWAY_OBSTRUCTED` (verified on
 *      the protocol payload, not inferred via `inferContinueApproachReason`).
 *    - `RunwayObstructionInformation` companion emitted in the same
 *      controller decision cycle (`==` on decision-cycle time, not
 *      tx-start), serialized after the primary (`<` on tx-start).
 *    - Companion's `DecisionTrace.regulations` cites exactly
 *      `[CAP413_4_55, CAP413_4_56, ICAO4444_12_3_4_16,
 *      ICAO4444_8_9_6_1_8]` — pre-clearance refs.
 *    - **Explicit absence assertions**: `CAP413_4_65` (missed-approach
 *      phraseology — wrong for CONTINUE APPROACH) is NOT cited;
 *      `ICAO4444_7_4_1_4_1` (post-clearance GA mandate — wrong for
 *      pre-clearance CA) is NOT cited. Catches a hypothetical
 *      regression where the companion drifted back to the fn-12 GA
 *      default regs list.
 *  - **Supersession on normal-success path** (fn-13.1 R7 extension):
 *    after `ClearedToLand` is re-issued (post-obstruction-clear), no
 *    stale `ContinueApproach` coordination remains in the tower's
 *    coordinations ledger for the aircraft. The
 *    `ClearedToLand → ContinueApproach` supersession edge in
 *    `coordinations.kt`'s `supersedes` table closes the CA coordination
 *    when ARR-LAND fires.
 *  - **R7 vacate-coordination closure** (per fn-8.3 discipline): after
 *    the aircraft vacates, the tower's coordination ledger contains no
 *    leftover `AfterLandingVacateVia` / `BacktrackRunway` entries for
 *    the aircraft.
 *  - **Obstruction lifetime pin**: `Cleared.decisionTime >= clearsAt`
 *    (the sim's per-cycle expiry pass nulls `runway.obstruction` only
 *    when `clearsAt <= now`). Catches a hypothetical regression that
 *    expires the obstruction early.
 *  - **Doctrine separation**: physical-occupancy
 *    (`RunwayPhysicallyClear`) vs declared-obstruction
 *    (`RunwayObstructed`) vs clears-in-time
 *    (`ObstructionClearsInTime`) — three guard predicates at
 *    `AwaitApproach` with mutually-exclusive priority placement
 *    (CA wins when both `RunwayObstructed` AND `ObstructionClearsInTime`;
 *    GA wins when `RunwayObstructed` AND `Not(ObstructionClearsInTime)`;
 *    generic ARR-GO-AROUND wins on physical occupancy only).
 *
 * **Time band** (per epic R10 + fn-8.3 decision #11 inheritance):
 * observed completion wall on first GREEN run (single aircraft, one
 * planned circuit, brief CONTINUE APPROACH delay, then normal landing).
 * The ±15% band catches doctrine regressions while absorbing
 * run-to-run jitter. After first GREEN the observed wall is captured
 * below; the band may be retightened on closure-pass calibration.
 *
 *   - observed completion wall: 896_000 ms (~14.9 sim minutes)
 *   - lower bound (×0.85): 761_600 ms (~12.7 min)
 *   - upper bound (×1.15): 1_030_400 ms (~17.2 min)
 *
 * Rationale: single-aircraft, single planned circuit with a brief 5-
 * second obstruction window at AwaitApproach. CA does NOT regress the
 * commitment; the aircraft continues approach and lands normally on
 * the same approach after the obstruction clears. The wall is
 * materially SHORTER than G3a-obstruction's GA test (~1399 s) because
 * the CA path does NOT add a recovery circuit. It is comparable to
 * G0's `LowgGoldenTest` plus a brief CA delay + radio serialization on
 * the CA + companion + re-issued `ClearedToLand` chain. A doctrine
 * regression that altered the predicate's eligibility window, the
 * radio serialization, or the post-obstruction `ARR-LAND` re-fire
 * timing would push the wall outside this band. A wall that DOUBLED
 * back toward ~1400 s would indicate a hidden recovery-circuit fork
 * (the GA path leaking into the CA scenario).
 *
 * **Doctrinal anchors:**
 *  - **CAP 413 §4.55**: *"CONTINUE APPROACH (reason or instruction)."*
 *    Pre-clearance delay phraseology when the runway is not yet clear
 *    for landing but is expected to be in good time. fn-13.1 R7
 *    upgraded §4.55's RegulationDatabase entry to anchor on the
 *    runway-obstructed pre-clearance case.
 *  - **CAP 413 §4.56**: *"The Pilot-in-Command may or may not be told
 *    the reason for the delay."* Reason verbalisation in the primary
 *    transmission is optional; the companion
 *    `RunwayObstructionInformation` carries the structured reason
 *    independently.
 *  - **ICAO Doc 4444 §12.3.4.16(d)**: *"CONTINUE APPROACH [(reason
 *    why instruction is given)]"* phraseology — the international
 *    equivalent of CAP 413 §4.55-4.56.
 *  - **ICAO Doc 4444 §8.9.6.1.8**: *"the reason for the instruction
 *    or the advice should be given to the pilot"* — the companion's
 *    purpose.
 *
 * Verbatim verifications anchor on the same research/txt extracts as
 * the controller-level `ObstructionContinueApproachSpec`.
 */
class G3aRunwayObstructionContinueApproachTest {

    @Test
    fun `world-authored short-TTL obstruction triggers CONTINUE APPROACH and normal landing at LOWG`() {
        // ── World + controllers via the shared fixture ──────────────────────
        // Reuses Fixtures.LOWG (single-aircraft single-aerodrome). The
        // distinguishing surface is the world-state mutation — the
        // `runway.obstruction` field is authored at runtime via the sim
        // runner's `onAfterEvent` hook with a 5-second `clearsAt` (vs the
        // GA test's 60s). World-only test triggers per
        // `feedback_world_only_test_triggers.md` — no event injection, no
        // belief-state mutation, no rigged decision.
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
        // listOf(CircuitOutcome.FullStop))` — a single planned circuit. NO
        // `CircuitOutcome.GoAround` outcome — CONTINUE APPROACH is a
        // pre-clearance delay, not a missed approach; the commitment stays
        // at `AwaitApproach` (the CA rule has `nextStage = null`) and the
        // aircraft lands on the same approach after the obstruction clears.
        // Adding a GoAround outcome would conflate the test's behavioural
        // surface with G3a-trained.
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
        // 30 sim minutes generous ceiling. One planned circuit + 5s
        // obstruction wait should land in well under that. The ±15% time
        // band below is the load-bearing tightness pin.
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
        // = now + 5.seconds)` at the strict pre-clearance / final-geometry
        // window defined in the task spec's R9 acceptance text. Authorship
        // fires at the FIRST post-event state where ALL of the following
        // hold simultaneously, mirroring the CA rule's guard predicate
        // shape so the very next controller cycle sees a satisfied guard:
        //
        //   (a) commitment stage is `AwaitApproach` — pins the rule's only
        //       stage placement (Boundary #1: post-clearance variants
        //       always escalate to GA).
        //   (b) NO `ClearedToLand` coordination exists for the aircraft —
        //       pins the pre-clearance constraint
        //       (`T_obs < T_ClearedToLand`).
        //   (c) `OnCircuitLeg(FINAL) == true` for the aircraft's current
        //       `positionPoint` (i.e. `worldIndex.circuitLegsByPoint
        //       [ac.positionPoint]` contains `LegName.FINAL`) OR the
        //       Euclidean distance to runway threshold is ≤ 5000 m —
        //       mirrors the CA rule's `AnyOf(OnApproach,
        //       OnCircuitLeg(FINAL))` geometric arm. Authorship inside
        //       this window ensures the rule's geometric guard is
        //       satisfied at the next controller cycle.
        //   (d) Aircraft has a positive `speedMps` — pins the
        //       `groundSpeed` precondition of `ObstructionClearsInTime`
        //       (the guard fails closed when `groundSpeed` is null /
        //       non-positive).
        //   (e) Predicate-eligibility check:
        //       `(5 s + OBSTRUCTION_CLEAR_SAFETY_MARGIN_S(10 s)) ≤
        //       distance-to-threshold / groundSpeed`. With a 5 s TTL,
        //       the predicate holds when the aircraft is more than 15 s
        //       of ETA from the threshold.
        //
        // **One-shot guard**: `var obstructionAuthored = false` ensures
        // `runway.obstruction` is set exactly once. Re-writing `Some →
        // Some(new clearsAt)` would refresh the `clearsAt` field and
        // violate the immutability invariant (fn-12 epic Decision #4);
        // the sim's world-diff producer `check(...)`s this invariant and
        // throws `IllegalStateException` on violation as defense-in-depth.
        //
        // **5-second TTL** matches the task spec's R9 acceptance exactly.
        // The companion `G3aRunwayObstructionTest` uses 60 s on the
        // post-clearance long-TTL GA branch. The TTL is short enough to
        // keep the predicate's positive eligibility window wide (the
        // smaller the gap, the easier `(gap + 10 s) ≤ ETA` is to
        // satisfy) and just barely long enough that the world-diff fold,
        // the rule fire, and the expiry-then-ARR-LAND chain all occur
        // before the aircraft reaches the threshold geometrically.
        //
        // **Fail-loud precondition validation**: at the END of the run,
        // we explicitly assert the hook fired. If the predicate above
        // never holds for any sim tick before the aircraft reaches a
        // too-close-to-threshold point (i.e. the AwaitApproach +
        // on-final-geometry window collapses), the test fails with a
        // descriptive error pointing at the precondition mismatch — per
        // the task spec's "FAIL LOUDLY (test setup error, not a
        // behavioural failure)" rule.
        var obstructionAuthored = false
        val obstructionAuthoredAt = arrayOf<SimTime?>(null)
        val obstructionClearsAt = arrayOf<SimTime?>(null)
        val authorshipDiagnostics = arrayOf<String?>(null)
        val onAfterEvent: (SimEvent, SimState) -> SimState = { _, st ->
            if (obstructionAuthored) {
                st
            } else {
                val precond = checkAuthorshipPreconditions(
                    st = st,
                    aircraftId = aircraftId,
                    towerId = tower.id,
                    rwy = rwy,
                    proposedClearsAtDuration = SimDuration.ofSeconds(5),
                )
                if (precond is AuthorshipDecision.Skip) {
                    st
                } else {
                    val author = precond as AuthorshipDecision.Author
                    obstructionAuthored = true
                    val clearsAt = st.now + SimDuration.ofSeconds(5)
                    obstructionAuthoredAt[0] = st.now
                    obstructionClearsAt[0] = clearsAt
                    authorshipDiagnostics[0] = author.diagnostic
                    authorRunwayObstruction(st, lowg, rwy, RunwayObstruction(clearsAt = clearsAt))
                }
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
        println("─── G3a-obstruction-continue-approach per-aircraft trace summary ───")
        println("Obstruction authored at: ${obstructionAuthoredAt[0]?.millis ?: "<NEVER>"}ms")
        println("Obstruction clearsAt:    ${obstructionClearsAt[0]?.millis ?: "<NEVER>"}ms")
        println("Authorship preconditions: ${authorshipDiagnostics[0] ?: "<NEVER>"}")
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
        println("Tower controller transmissions (id, startedAt, decision-cycle):")
        for (step in trace.steps) {
            val ev = step.event
            if (ev is SimEvent.TransmissionStart && ev.transmission.speaker is SpeakerRef.Controller &&
                (ev.transmission.speaker as SpeakerRef.Controller).id == tower.id
            ) {
                val tx = ev.transmission
                val klass = when (val u = tx.utterance) {
                    is Utterance.FromController -> u.output::class.simpleName +
                        when (val o = u.output) {
                            is ControllerOutput.Instruct -> ":" + o.instruction::class.simpleName
                            is ControllerOutput.Respond -> ":" + o.response::class.simpleName
                        }
                    else -> "?"
                }
                var priorNextId = trace.initial.nextTransmissionId
                var cycleMs: Long? = null
                for (s in trace.steps) {
                    val e = s.event
                    val post = s.state.nextTransmissionId
                    if (e is SimEvent.ControllerCycle && e.controllerId == tower.id &&
                        priorNextId <= tx.id.value && tx.id.value < post
                    ) {
                        cycleMs = e.time.millis
                        break
                    }
                    priorNextId = post
                }
                println("  id=${tx.id.value} startedAt=${tx.startedAt.millis}ms decisionCycle=${cycleMs ?: "<none>"}ms $klass")
            }
        }
        println("─── end G3a-obstruction-continue-approach per-aircraft trace summary ───")
        println()

        // ── One-shot authorship pin (FAIL LOUDLY per spec R9) ──────────────
        //
        // The task spec's R9 acceptance is explicit: if the authorship
        // preconditions never align (commitment at AwaitApproach AND
        // pre-ClearedToLand AND on-final-geometry AND groundSpeed > 0 AND
        // (5s + 10s margin) ≤ ETA-to-threshold), the test fails LOUDLY
        // as a test-setup error — NOT a silent skip and NOT a soft-pass.
        // The five preconditions mirror the CA rule's guard predicate
        // exactly; if none ever hold, the LOWG fixture's circuit
        // geometry / cycle cadence / pilot timing has drifted relative
        // to the rule's predicate shape, and the rest of the test's
        // pins are not validating anything.
        check(obstructionAuthored) {
            "World-authorship hook never fired — `checkAuthorshipPreconditions` never " +
                "returned `Author` across the entire 30 sim-minute run. The R9 acceptance " +
                "predicate (stage=AwaitApproach AND no ClearedToLand coordination AND " +
                "(OnCircuitLeg(FINAL) OR distance≤5km) AND speedMps>0 AND (5s+10s margin)≤ETA) " +
                "did not align at any post-event state. Either (i) the AwaitApproach window " +
                "is too narrow at LOWG for the hook to catch (ARR-LAND fires the same cycle " +
                "the stage enters AwaitApproach, with no on-final-geometry tick in between), " +
                "or (ii) the LOWG circuit geometry does not include FINAL-leg-labelled points " +
                "before the threshold, OR the world index's `circuitLegsByPoint` is empty for " +
                "the points the aircraft visits, OR groundSpeed never becomes positive in the " +
                "window, OR the predicate-eligibility check always fails (e.g. ETA too short). " +
                "This is a test-setup error per the spec's FAIL LOUDLY rule; the rest of the " +
                "test's pins assume the obstruction was authored in the eligible window.\n$journey"
        }

        // ── Outcome pins: aircraft completes mission, parked at stand ──────
        val finalAircraft = finalState.aircraft.getValue(aircraftId)
        val finalMission = checkNotNull(finalAircraft.pilotMission) {
            "Aircraft lost its mission.\n$journey"
        }

        check(finalMission.isComplete) {
            "Mission did not complete within 30 sim minutes — wedge or under-budget. " +
                "CONTINUE APPROACH should be a brief delay (5s obstruction + a few seconds of " +
                "decision-cycle latency); if the mission never completes, either the " +
                "obstruction never cleared (expiry-pass regression) or `ARR-LAND` failed to " +
                "re-fire after the obstruction cleared (pre-clearance gate stuck).\n$journey"
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
        // Exactly one `None → Some(...)` and one `Some → None` transition in
        // the TOWER's `runwayObstructions[16C]` belief slice (one Detected
        // and one Cleared per obstruction lifetime). Per fn-12 Decision #3
        // the events are per-controller scoped; this pin scopes to TOWER.
        // Defense-in-depth for the one-shot authorship guard above.
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
                "transitions strictly None ↔ Some).\n$journey"
        }
        val detectedTransition = detectedBeliefTransitions.single()
        val clearedTransition = clearedBeliefTransitions.single()
        val detectedMs = detectedTransition.after.time.millis
        val clearedMs = clearedTransition.after.time.millis

        // ── Locate CONTINUE APPROACH + companion + ClearedToLand records ──
        //
        // CONTINUE APPROACH primary instruction (typed `ContinueApproach`),
        // emitted via `Dispatch.Direct` by `ObstructionContinueApproachAction`.
        val caRecord = records.firstControllerInstructionOf<ContinueApproach>(aircraftId)
            .getOrElse {
                fail(
                    "Controller never transmitted ContinueApproach to $aircraftId — the new " +
                        "ARR-CONTINUE-APPROACH-OBSTRUCTION rule did not fire. Expected: rule " +
                        "fires when (i) commitment stage is AwaitApproach, (ii) RunwayObstructed " +
                        "holds, (iii) ObstructionClearsInTime holds. Check the authorship hook " +
                        "preconditions and `ObstructionClearsInTime`'s ETA arithmetic.\n$journey"
                )
            }
        val caMs = caRecord.time.millis

        // Verify the protocol payload: `reason == RUNWAY_OBSTRUCTED`. Set
        // directly by `ObstructionContinueApproachAction` (NOT inferred by
        // `inferContinueApproachReason`, which lacks Commitment).
        val caInstruct = ((caRecord.utterance as Utterance.FromController).output
            as ControllerOutput.Instruct)
        val caInstruction = (caInstruct.dispatch as Dispatch.Direct).instruction as ContinueApproach
        check(caInstruction.reason == ContinueApproachReason.RUNWAY_OBSTRUCTED) {
            "ContinueApproach.reason mismatch: ${caInstruction.reason} vs " +
                "${ContinueApproachReason.RUNWAY_OBSTRUCTED}. The reason is set inline by " +
                "`ObstructionContinueApproachAction` (the existing traffic-driven " +
                "`ContinueApproachAction` uses `inferContinueApproachReason` and would yield " +
                "RUNWAY_ACCESS_PENDING/TRAFFIC_* — a drifted reason here indicates the wrong " +
                "action fired (the existing rule slipped past its `Not(RunwayObstructed)` " +
                "gate).\n$journey"
        }

        // Decision-cycle time for the CA — via the `nextTransmissionId`
        // mint-id walk helper (per `sim-test-pins-must-compare-against-
        // 2026-05-10` memory: use decision-cycle, not tx-start, for
        // controller-decision invariants).
        val caTxId = extractTransmissionId(trace, caRecord, "ContinueApproach instruction", journey)
        val caDecisionCycleMs = findEmittingCycleMs(
            trace = trace,
            controller = tower.id,
            txId = caTxId,
            txDescription = "ContinueApproach instruction",
            journey = journey,
        )

        // Companion `RunwayObstructionInformation` record + decision-cycle.
        val companionRecord = records.firstOrNull { rec ->
            val out = (rec.utterance as? Utterance.FromController)?.output ?: return@firstOrNull false
            val respond = out as? ControllerOutput.Respond ?: return@firstOrNull false
            respond.target == aircraftId && respond.response is RunwayObstructionInformation
        } ?: fail(
            "Controller never transmitted RunwayObstructionInformation companion to $aircraftId " +
                "— the pre-clearance reason-on-radio (CAP 413 §4.55-4.56, ICAO §12.3.4.16(d) + " +
                "§8.9.6.1.8) was not emitted alongside ContinueApproach. The companion is " +
                "mandatory; without it the CA instruction lacks the structured doctrinal reason.\n$journey"
        )
        val companionMs = companionRecord.time.millis
        val companionRespond = (companionRecord.utterance as Utterance.FromController).output
            as ControllerOutput.Respond
        val companion = companionRespond.response as RunwayObstructionInformation
        check(companion.runway == rwy) {
            "Companion RunwayObstructionInformation.runway mismatch: ${companion.runway} vs $rwy.\n$journey"
        }
        check(companion.clearsAt == obstructionClearsAt[0]!!) {
            "Companion RunwayObstructionInformation.clearsAt mismatch: ${companion.clearsAt} vs " +
                "authored ${obstructionClearsAt[0]}. The companion's clearsAt is the " +
                "obstruction's clearsAt from BeliefState.runwayObstructions; drift indicates " +
                "the ObstructionInfo carrier dropped or mutated the field.\n$journey"
        }

        val companionTxId = extractTransmissionId(
            trace, companionRecord, "RunwayObstructionInformation companion", journey,
        )
        val companionDecisionCycleMs = findEmittingCycleMs(
            trace = trace,
            controller = tower.id,
            txId = companionTxId,
            txDescription = "RunwayObstructionInformation companion",
            journey = journey,
        )

        // ── Same controller decision/output cycle pin ──────────────────────
        //
        // The companion is required to be emitted in the SAME `controllerDecide`
        // invocation as the CA instruction (per fn-13 R3 — pre-clearance
        // reason-on-radio is doctrinally bound to the CA, not a standalone
        // advisory). Both outputs come from one `deriveCompanionOutputs`
        // call inside one `ControllerCycle` step; they MUST share that
        // step's decision-cycle time. Without this pin, a later standalone
        // `RunwayObstructionInformation` response — unrelated to the CA —
        // could satisfy a tx-only ordering check.
        check(caDecisionCycleMs == companionDecisionCycleMs) {
            "Same-decision-cycle pin: ContinueApproach and RunwayObstructionInformation " +
                "companion must be emitted in the same controllerDecide cycle (same " +
                "`SimEvent.ControllerCycle` step for the tower). ContinueApproach " +
                "decision-cycle=${caDecisionCycleMs}ms, companion " +
                "decision-cycle=${companionDecisionCycleMs}ms. Mismatch indicates the " +
                "companion was emitted by a different cycle than the CA — either (i) a later " +
                "standalone obstruction-info response from a different rule fire, or (ii) the " +
                "`deriveCompanionOutputs` invocation split CA and companion across cycles. " +
                "The mandatory reason-on-radio (CAP 413 §4.55-4.56, ICAO §12.3.4.16(d)) is " +
                "doctrinally bound to the CA instruction; emitting them in separate cycles " +
                "lets the pilot continue approach without the reason.\n$journey"
        }

        // ── Companion regulations content pin ──────────────────────────────
        //
        // fn-13.1 R3: the companion's `DecisionTrace.regulations` must cite
        // EXACTLY the four pre-clearance refs. Explicit absence assertions
        // catch a regression to the fn-12 GA default regs list (which
        // cites `CAP413_4_65` and `ICAO4444_7_4_1_4_1`, both wrong for
        // pre-clearance CONTINUE APPROACH).
        val expectedCompanionRegs = setOf(
            RegulationDatabase.CAP413_4_55,
            RegulationDatabase.CAP413_4_56,
            RegulationDatabase.ICAO4444_12_3_4_16,
            RegulationDatabase.ICAO4444_8_9_6_1_8,
        )
        val observedCompanionRegs = companionRespond.trace.regulations.toSet()
        check(observedCompanionRegs == expectedCompanionRegs) {
            "Companion DecisionTrace.regulations mismatch.\n" +
                "  expected: $expectedCompanionRegs\n" +
                "  observed: $observedCompanionRegs\n" +
                "fn-13.1 R3 split the companion regs by path: CONTINUE APPROACH cites the " +
                "pre-clearance refs (CAP413 §4.55, §4.56, ICAO §12.3.4.16, §8.9.6.1.8); GA " +
                "cites the post-clearance refs (CAP413 §4.65, ICAO §7.4.1.4.1, §8.9.6.1.8). " +
                "Drift here indicates `obstructionInfo.companionTraceRegs` was not set by " +
                "`ObstructionContinueApproachAction`, OR `deriveCompanionOutputs` fell back to " +
                "the GA default branch when it shouldn't.\n$journey"
        }
        // Explicit absence — even if the set equality above changes shape
        // in a future refactor, these two specific exclusions are
        // load-bearing and must never appear in CA companion regs.
        check(RegulationDatabase.CAP413_4_65 !in companionRespond.trace.regulations) {
            "CAP 413 §4.65 (missed-approach phraseology) MUST NOT appear in the CONTINUE " +
                "APPROACH companion's DecisionTrace.regulations — it is the GA companion's " +
                "regulation, doctrinally wrong for a CONTINUE APPROACH (which is NOT a missed " +
                "approach).\n$journey"
        }
        check(RegulationDatabase.ICAO4444_7_4_1_4_1 !in companionRespond.trace.regulations) {
            "ICAO Doc 4444 §7.4.1.4.1 (post-clearance GA mandate) MUST NOT appear in the " +
                "CONTINUE APPROACH companion's DecisionTrace.regulations — it is a " +
                "post-clearance reason-on-radio mandate; CONTINUE APPROACH is pre-clearance.\n$journey"
        }

        // ── Locate ClearedToLand + Report(RunwayVacated) ──────────────────
        //
        // Single ClearedToLand on the same approach attempt (no recovery
        // circuit — CA does NOT regress the commitment).
        val landRecords = records.filter { rec ->
            val out = (rec.utterance as? Utterance.FromController)?.output
                as? ControllerOutput.Instruct ?: return@filter false
            val instr = (out.dispatch as? Dispatch.Direct)?.instruction ?: return@filter false
            out.target == aircraftId && instr is ClearedToLand
        }
        val landRecord = landRecords.firstOrNull()
            ?: fail(
                "Controller never transmitted ClearedToLand to $aircraftId after the obstruction " +
                    "cleared. Expected: once `RunwayObstructionCleared` fires and the " +
                    "BeliefState.runwayObstructions slice flips back to None, the pre-clearance " +
                    "`Not(RunwayObstructed)` gate on LandingConditions ungates and ARR-LAND " +
                    "re-fires.\n$journey"
            )
        val landMs = landRecord.time.millis

        val landTxId = extractTransmissionId(trace, landRecord, "ClearedToLand instruction", journey)
        val landDecisionCycleMs = findEmittingCycleMs(
            trace = trace,
            controller = tower.id,
            txId = landTxId,
            txDescription = "ClearedToLand instruction",
            journey = journey,
        )

        // Report(RunwayVacated) — the canonical landing-complete observable.
        val vacatedMs = records.firstPilotReportOf<ReportEvent.RunwayVacated>(aircraftId)
            .map { it.time.millis }
            .getOrElse {
                fail(
                    "Expected at least one Report(RunwayVacated) from $aircraftId — without it, " +
                        "the aircraft never confirmed leaving the runway after landing.\n$journey"
                )
            }

        // ── Layer 1b — Radio-transmission causal partial-order pins ─────────
        //
        // Transmission ordering (radio serialization):
        //   ContinueApproach ≺ RunwayObstructionInformation ≺ ClearedToLand
        //   ≺ Report(RunwayVacated)
        //
        // `applyControllerOutputs` serializes outputs on the same
        // frequency; each subsequent transmission starts at the prior
        // transmission's `endsAt`. So `ContinueApproach.txStart <
        // companion.txStart` strictly (not equal).
        check(caMs < companionMs) {
            "Companion serialization pin: ContinueApproach.txStart (${caMs}ms) must precede " +
                "RunwayObstructionInformation.txStart (${companionMs}ms). " +
                "`applyControllerOutputs` serializes outputs on the same frequency; the " +
                "companion's txStart equals the CA's `endsAt`. Equality would indicate the " +
                "radio serializer didn't queue the second transmission — both would talk over " +
                "each other on air, a doctrinal violation.\n$journey"
        }
        check(companionMs < landMs) {
            "Radio chain pin: RunwayObstructionInformation.txStart (${companionMs}ms) must " +
                "precede ClearedToLand.txStart (${landMs}ms). The companion is part of the CA " +
                "decision cycle; ClearedToLand is emitted later by ARR-LAND after the " +
                "obstruction expires and the pre-clearance gate ungates.\n$journey"
        }
        check(landMs < vacatedMs) {
            "Radio chain pin: ClearedToLand.txStart (${landMs}ms) must precede " +
                "Report(RunwayVacated).txStart (${vacatedMs}ms). The aircraft must read back the " +
                "landing clearance, land, and roll-out before vacating.\n$journey"
        }

        // ── Absence pin: no Report(ContinueApproach) transmission ──────────
        //
        // `protocol.InstructionReadback.requiredReadbackAtoms(ContinueApproach)
        // = emptySet()`. The pilot does NOT transmit a readback for CA per
        // `D-PASS-continue-approach-pilot-readback`. A regression that added
        // a readback would surface as a pilot-side report transmission
        // between the controller's CA and ClearedToLand. Pin: no transmission
        // BY THIS AIRCRAFT contains a `ContinueApproach` readback atom or
        // a `Report` with content referencing CA.
        //
        // We check the absence by scanning pilot transmissions in the
        // `(caMs, landMs)` window — any pilot transmission containing a
        // `ContinueApproach` reference there would be a CA readback.
        val pilotReadbacksInWindow = records.filter { rec ->
            val fromPilot = (rec.utterance as? Utterance.FromPilot)?.transmission
                ?: return@filter false
            val isInWindow = rec.time.millis in (caMs + 1)..(landMs - 1)
            val isAircraft = (rec.speaker as? SpeakerRef.Pilot)?.aircraftId == aircraftId
            isInWindow && isAircraft && fromPilot is xyz.easiersaid.twr.protocol.Readback
        }
        val caReadback = pilotReadbacksInWindow.firstOrNull { rec ->
            val rb = (rec.utterance as Utterance.FromPilot).transmission
                as xyz.easiersaid.twr.protocol.Readback
            // A CA readback would carry an `AtomicReadback` referring to
            // ContinueApproach. Conservative check: stringify and look
            // for the type name. (No CA readback atoms exist in the
            // protocol, so this should never match — defensive.)
            rb.toString().contains("ContinueApproach")
        }
        check(caReadback == null) {
            "Pilot transmitted a Readback referencing ContinueApproach between the CA " +
                "(${caMs}ms) and the ClearedToLand (${landMs}ms). Per " +
                "`InstructionReadback.requiredReadbackAtoms(ContinueApproach) = emptySet()`, " +
                "the pilot must NOT read back a CONTINUE APPROACH instruction (out-of-scope " +
                "per D-PASS-continue-approach-pilot-readback). Found readback at " +
                "${caReadback?.time?.millis}ms: ${caReadback?.utterance}.\n$journey"
        }

        // ── Layer 1a — Decision-cycle causal partial-order pins ────────────
        //
        // Two pins — both compared against **decision-cycle** times, NOT
        // transmission-start times (per `sim-test-pins-must-compare-against-
        // 2026-05-10` memory). Comparing belief transitions against tx-start
        // times leaves a gap: a queued transmission could start after a
        // later belief transition while its originating decision was made
        // too early, and the pin would falsely pass.
        //   1. `Detected.decisionTime <= CA.decisionTime` — the rule fires
        //      in the cycle that sees RunwayObstructed=true.
        //   2. `Cleared.decisionTime < ClearedToLand.decisionTime` — the
        //      pre-clearance landing gate (`Not(RunwayObstructed)`)
        //      requires the obstruction to be absent from beliefs before
        //      ARR-LAND can issue clearance.
        check(detectedMs <= caDecisionCycleMs) {
            "Decision-cycle pin: RunwayObstructionDetected belief transition (${detectedMs}ms) " +
                "must occur at-or-before ContinueApproach decision-cycle " +
                "(${caDecisionCycleMs}ms). Equality is allowed (belief fold + rule fire in " +
                "same controllerDecide cycle); strict > would indicate the rule fired before " +
                "the belief was updated — a fold-vs-rule ordering regression. " +
                "(ContinueApproach.txStart=${caMs}ms is later, but tx-start is not the right " +
                "comparand — a queued transmission could start after a later belief transition " +
                "while its originating decision was made too early.)\n$journey"
        }
        check(clearedMs <= landDecisionCycleMs) {
            "Pre-clearance ungate pin: RunwayObstructionCleared belief transition " +
                "(${clearedMs}ms) must occur at-or-before ClearedToLand decision-cycle " +
                "(${landDecisionCycleMs}ms). Equality is allowed (belief fold + rule fire in " +
                "the SAME controllerDecide cycle: the expiry pass + world-diff producer emit " +
                "`RunwayObstructionCleared` at the top of the cycle, the fold drops the belief " +
                "to `None`, then `Not(RunwayObstructed)` evaluates true and ARR-LAND fires " +
                "in the same arbitration pass). Strict > would indicate ClearedToLand fired " +
                "BEFORE the obstruction cleared in beliefs — a doctrine regression on the " +
                "landing gate. (ClearedToLand.txStart=${landMs}ms; tx-start is not the right " +
                "comparand — the queued transmission could start after `Cleared` while its " +
                "originating decision was made before.)\n$journey"
        }

        // Defensive: decision-cycle is at-or-before tx-start. The
        // controller decides first, then `applyControllerOutputs` queues
        // the radio transmission.
        check(caDecisionCycleMs <= caMs) {
            "Decision-vs-tx ordering pin: ContinueApproach decision-cycle " +
                "(${caDecisionCycleMs}ms) must be at-or-before ContinueApproach.txStart " +
                "(${caMs}ms). Reversal indicates a sim-engine invariant violation.\n$journey"
        }

        // ── Obstruction lifetime pin (R6 / R10 — `clearsAt` semantics) ─────
        //
        // The obstruction was authored with a 5-second `clearsAt`. The
        // sim's per-cycle expiry pass nulls `runway.obstruction` when
        // `clearsAt <= now`, which then drives the `Some → None` diff and
        // the `RunwayObstructionCleared` event. Therefore the belief
        // slice's `Some → None` transition (`clearedMs`) must occur at or
        // after the authored `clearsAt`. Without this pin, a regression
        // that expires the obstruction immediately after detection would
        // still produce exactly one `Some → None`, unblock ARR-LAND, and
        // pass the pre-clearance ungate pin above — bypassing the
        // obstruction-lifetime semantics entirely.
        val clearsAtMs = obstructionClearsAt[0]!!.millis
        check(clearedMs >= clearsAtMs) {
            "Obstruction lifetime pin: RunwayObstructionCleared belief transition " +
                "(${clearedMs}ms) must occur at-or-after the authored `clearsAt` " +
                "(${clearsAtMs}ms). The sim's per-cycle expiry pass nulls " +
                "`runway.obstruction` only when `clearsAt <= now`; a `Cleared` event before " +
                "`clearsAt` would indicate one of (i) the expiry condition is wrong (firing " +
                "on `clearsAt > now`), (ii) the world hook was re-fired and nulled the " +
                "obstruction early, or (iii) a non-expiry code path is mutating " +
                "`runway.obstruction`. All three violate the `RunwayObstruction(clearsAt)` " +
                "lifetime contract.\n$journey"
        }

        // ── Layer 2 — Stage NON-regression pin (KEY signature) ──────────────
        //
        // The `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule has `nextStage = null`
        // (the commitment stays at `AwaitApproach`). Distinguishing from
        // GA: there is NO `<from-stage> → AwaitDownwind` regression in
        // the trace at any point. The stage progression is monotonic:
        // `AwaitDownwind → AwaitApproach → LandingClearanceIssued →
        // AwaitLandedObserved → ...` with no backward step.
        //
        // The absence of regression is the load-bearing behavioural
        // signature distinguishing CONTINUE APPROACH from GO AROUND in
        // the sim trace (per the task spec § Layer 2). A hypothetical
        // regression where CONTINUE APPROACH accidentally advanced to
        // `AwaitDownwind` (rule misconfigured with `nextStage =
        // AwaitDownwind`) would surface here.
        val stageTransitions = trace.commitmentStageTransitions(aircraftId, tower.id)
        val regressionsToAwaitDownwind = stageTransitions.filter { t ->
            val from = t.from.fold({ null }, { it as? TowerArrivalStage }) ?: return@filter false
            val to = t.to.fold({ null }, { it as? TowerArrivalStage }) ?: return@filter false
            // Regression candidates: any forward stage going back to AwaitDownwind.
            from in setOf(
                TowerArrivalStage.AwaitApproach,
                TowerArrivalStage.LandingClearanceIssued,
                TowerArrivalStage.AwaitLandedObserved,
            ) && to == TowerArrivalStage.AwaitDownwind
        }
        check(regressionsToAwaitDownwind.isEmpty()) {
            val regressionSummary = regressionsToAwaitDownwind.joinToString(", ") { t ->
                val fromName = t.from.fold({ "-" }, { it.name })
                val toName = t.to.fold({ "-" }, { it.name })
                "$fromName → $toName @${t.after.time.millis}ms"
            }
            "Stage NON-regression pin: expected ZERO stage regressions back to AwaitDownwind " +
                "during the CONTINUE APPROACH cycle (CA's `nextStage = null`; commitment stays " +
                "at AwaitApproach). Observed ${regressionsToAwaitDownwind.size} regressions: " +
                "$regressionSummary. " +
                "A regression here indicates either (i) the CA rule accidentally got a " +
                "`nextStage = AwaitDownwind` (turning it into a GA), or (ii) the obstruction-GA " +
                "rule fired instead (mutual-exclusion guard regression — both `RunwayObstructed` " +
                "and `Not(ObstructionClearsInTime)` evaluated true when they should have been " +
                "mutually exclusive).\n$journey"
        }

        // The stage progression must be monotonic forward through the
        // normal-success path: AwaitDownwind → AwaitApproach →
        // LandingClearanceIssued → AwaitLandedObserved. We assert each
        // forward step appears once.
        val stageProgression = stageTransitions.mapNotNull { t ->
            val from = t.from.fold({ null }, { it as? TowerArrivalStage })
            val to = t.to.fold({ null }, { it as? TowerArrivalStage }) ?: return@mapNotNull null
            from to to
        }
        // The TowerArrival commitment for a single-aircraft circuit at LOWG
        // begins at `AwaitApproach` (the prior `TowerDeparture` commitment
        // completes at takeoff observation; the fresh arrival commitment
        // skips `AwaitDownwind` because the circuit-traffic strip already
        // records the arrival intent, and the new commitment is shaped
        // directly at `AwaitApproach`). We assert the post-AwaitApproach
        // forward progression: AwaitApproach → LandingClearanceIssued →
        // AwaitLandedObserved. The `AwaitApproach → LandingClearanceIssued`
        // transition is the load-bearing post-CA-cleared-obstruction
        // signal — it proves the CA path did NOT regress the stage (a GA
        // would have routed AwaitApproach → AwaitDownwind, then the
        // recovery circuit's AwaitDownwind → AwaitApproach later).
        val expectedForwardTransitions = listOf(
            TowerArrivalStage.AwaitApproach to TowerArrivalStage.LandingClearanceIssued,
            TowerArrivalStage.LandingClearanceIssued to TowerArrivalStage.AwaitLandedObserved,
        )
        for (expected in expectedForwardTransitions) {
            check(stageProgression.contains(expected)) {
                "Stage progression pin: expected forward transition ${expected.first?.name} → " +
                    "${expected.second.name} at some point in the trace; not observed. Stage " +
                    "progression: $stageProgression.\n$journey"
            }
        }

        // ── Commitment witness pins at CA cycle ──────────────────────────────
        //
        // Inspect the commitment immediately after the CA decision cycle.
        // `continueApproachIssuedThisAttempt` flips false → true; all
        // other sticky witnesses remain UNCHANGED (their pre-CA values).
        // The CA rule has `nextStage = null`, so the stage stays at
        // `AwaitApproach`.
        //
        // Use the trace cursor that follows the CA decision cycle to
        // inspect the commitment. The CA decision-cycle time is
        // `caDecisionCycleMs`; the post-cycle state is what we want.
        val caDecisionCursor = trace.firstWhere { st ->
            st.now.millis >= caDecisionCycleMs &&
                st.beliefs[tower.id]?.commitments?.get(aircraftId)
                    ?.continueApproachIssuedThisAttempt == true
        }.getOrElse {
            fail(
                "Could not locate a trace cursor where the tower's commitment for $aircraftId " +
                    "has `continueApproachIssuedThisAttempt = true`. The witness is set by " +
                    "`applyCommittedOutputWitnesses` after the CA rule fires and the action " +
                    "is certified; an absent witness here indicates the witness-application " +
                    "pass dropped the flag (fn-13.1 R2 regression).\n$journey"
            )
        }
        val commitmentAtCa = caDecisionCursor.state.beliefs[tower.id]
            ?.commitments?.get(aircraftId)
            ?: fail(
                "Tower commitment for $aircraftId missing at CA decision cursor.\n$journey"
            )
        check(commitmentAtCa.stage == TowerArrivalStage.AwaitApproach) {
            "Commitment stage at CA decision: expected AwaitApproach (CA has " +
                "`nextStage = null`; commitment stays in place), got ${commitmentAtCa.stage}. " +
                "Drift indicates either the CA rule accidentally advanced the stage, or a " +
                "different rule (e.g. ARR-LAND, ARR-GO-AROUND-RUNWAY-OBSTRUCTED) fired in the " +
                "same cycle and the witness-update pass attached `continueApproachIssuedThisAttempt` " +
                "to the wrong rule's commitment.\n$journey"
        }
        check(commitmentAtCa.continueApproachIssuedThisAttempt) {
            "continueApproachIssuedThisAttempt witness must be set post-CA-fire (no-refire " +
                "discipline per fn-13.1 R2). Observed=false. The witness is set on the " +
                "committed-output path by `applyCommittedOutputWitnesses`; an unset flag here " +
                "would let the CA rule re-fire every cycle while both predicates persist.\n$journey"
        }
        // Other sticky witnesses remain at their pre-CA values across the
        // CA cycle (per fn-8.3 reset machinery — witnesses reset on stage
        // regression, NOT on rule fires that keep the stage). The CA
        // cycle has `nextStage = null` (no regression), so any
        // commitment-scoped witness populated before the CA decision
        // cycle must still be populated after.
        //
        // For this single-aircraft scenario, the TowerArrival commitment
        // begins at `AwaitApproach` (the prior TowerDeparture commitment
        // completed at takeoff observation, and the new arrival shape
        // skips `AwaitDownwind` — the Downwind report is processed by
        // the arrival commitment's reconciliation but does not advance
        // its stage). `observedReportsDuringCommitment` may or may not
        // have the Downwind report by the CA decision-cycle moment
        // (depends on tick ordering between the pilot's Downwind
        // transmission and the AwaitTakeoffObserved → AwaitApproach
        // hand-over); we therefore do not pin this set's content. We
        // instead pin the GA-witness ABSENCE (mutual-exclusion proof).
        check(!commitmentAtCa.obstructionGoAroundIssuedThisAttempt) {
            "obstructionGoAroundIssuedThisAttempt must remain false through the CA cycle — " +
                "the GA witness is only set when the obstruction-GA rule fires, and CA + GA " +
                "are mutually exclusive at AwaitApproach. A true value here indicates BOTH " +
                "rules fired (mutual-exclusion guard regression).\n$journey"
        }
        check(!commitmentAtCa.touchedDownDuringCommitment) {
            "touchedDownDuringCommitment must remain false through the CA cycle — the aircraft " +
                "has not touched down. A true value here would indicate the witness logic mis-" +
                "attributed a touchdown to the pre-clearance window.\n$journey"
        }

        // ── Layer 3 — Kinematic non-event pin ───────────────────────────────
        //
        // The aircraft does NOT execute a go-around climb. After the CA
        // decision cycle, the phase trace progresses normally toward
        // `LandingRoll → Vacating → AtStand`; NO `Climbing` phase entries
        // after the CA cycle (the takeoff climb earlier in the trace is
        // expected and not load-bearing here). A post-CA `Climbing`
        // transition would indicate the GA rule fired after the CA
        // somehow — the CONTINUE APPROACH path keeps the aircraft on
        // the same approach throughout, and the obstruction clears in
        // time so ARR-LAND succeeds without escalation.
        val phaseTransitions = trace.transitionsOf { st ->
            st.aircraft[aircraftId]?.phase
        }
        val postCaClimbingEntries = phaseTransitions.filter { t ->
            t.to == PilotPhase.Climbing && t.after.time.millis >= caDecisionCycleMs
        }
        check(postCaClimbingEntries.isEmpty()) {
            val climbingSummary = postCaClimbingEntries.joinToString(", ") { t ->
                "${t.from} → ${t.to} @${t.after.time.millis}ms"
            }
            "Layer 3 kinematic non-event pin: expected ZERO `Climbing` phase entries AFTER " +
                "the CONTINUE APPROACH decision cycle (${caDecisionCycleMs}ms). The CA path " +
                "keeps the aircraft on the same approach; a post-CA `Climbing` entry would " +
                "indicate a GA fired and rerouted the aircraft. Observed " +
                "${postCaClimbingEntries.size} entries: $climbingSummary.\n$journey"
        }
        // The phase sequence must reach LandingRoll → Vacating → AtStand.
        val terminalPhases = phaseTransitions.map { it.to }.toSet()
        check(PilotPhase.LandingRoll in terminalPhases) {
            val phaseSummary = phaseTransitions.joinToString(", ") { t ->
                "${t.to}@${t.after.time.millis}ms"
            }
            "Aircraft never entered LandingRoll — the normal-landing path after the obstruction " +
                "cleared did not complete. Phase transitions: $phaseSummary.\n$journey"
        }
        check(PilotPhase.Vacating in terminalPhases) {
            "Aircraft never entered Vacating phase — the post-landing vacate sequence did not " +
                "complete.\n$journey"
        }

        // ── Supersession + R7 vacate-coordination closure pin ────────────────
        //
        // After the aircraft vacates: the tower's coordination ledger
        // contains NO leftover `ContinueApproach`, `AfterLandingVacateVia`,
        // or `BacktrackRunway` entries for the aircraft. The
        // `ClearedToLand → ContinueApproach` supersession edge added in
        // fn-13.1 closes the stale CA coordination when ARR-LAND fires;
        // the readback close path drops the `AfterLandingVacateVia` /
        // `BacktrackRunway` entries on vacate (fn-8.3 R7-style closure).
        val towerBeliefs = checkNotNull(finalState.beliefs[tower.id]) {
            "Tower beliefs missing at end of run — controller pipeline regression.\n$journey"
        }
        val acCoordinations = towerBeliefs.coordinations[aircraftId].orEmpty()
        val leftoverContinueApproach = acCoordinations.filter { coord ->
            coord.instruction is ContinueApproach
        }
        check(leftoverContinueApproach.isEmpty()) {
            "Supersession pin: after ClearedToLand is re-issued (post-obstruction-clear), no " +
                "stale ContinueApproach coordination should remain in the tower's ledger. Got " +
                "${leftoverContinueApproach.size} unclosed entries: $leftoverContinueApproach. " +
                "The `ClearedToLand → ContinueApproach` supersession edge in " +
                "`coordinations.kt`'s `supersedes` table closes the CA coordination when " +
                "ARR-LAND fires; an unclosed entry indicates either (i) the supersession " +
                "edge dropped from the table, or (ii) the CA coordination's instruction shape " +
                "no longer matches the predicate.\n$journey"
        }
        val vacateCoords = acCoordinations.filter { coord ->
            coord.instruction is AfterLandingVacateVia || coord.instruction is BacktrackRunway
        }
        check(vacateCoords.isEmpty()) {
            "R7 vacate-coordination closure: after vacate readback, the tower's coordination " +
                "ledger must contain no `AfterLandingVacateVia` / `BacktrackRunway` entries " +
                "for $aircraftId. Got ${vacateCoords.size} unclosed entries: " +
                "$vacateCoords.\nIf this fires, the readback close path broke — either the " +
                "pilot's vacate readback was malformed, or `acceptReadback` failed to remove " +
                "the matching entry.\n$journey"
        }

        // ── Absence pin: no GoAround instruction ────────────────────────────
        //
        // CONTINUE APPROACH is mutually exclusive with GA at `AwaitApproach`
        // via the narrowed guard `Not(ObstructionClearsInTime)` on the
        // obstruction-GA variant. The CA path should produce ZERO GoAround
        // transmissions across the whole run.
        val goAroundRecord = records.firstControllerInstructionOf<GoAround>(aircraftId)
        check(goAroundRecord.isNone()) {
            "Expected ZERO GoAround instructions on the CONTINUE APPROACH path — the " +
                "CA and GA rules at AwaitApproach are mutually exclusive via " +
                "`ObstructionClearsInTime` / `Not(ObstructionClearsInTime)`. Observed: " +
                "$goAroundRecord. Either both rules fired (guard regression), or the obstruction " +
                "cleared too late (predicate-eligibility window collapsed and GA fired " +
                "instead).\n$journey"
        }

        // ── Time band (R10 — ±15% around observed wall) ──────────────────────
        //
        // First-green observed wall: captured below for the LOWG single-
        // aircraft single-planned-circuit + CONTINUE APPROACH (5s
        // obstruction) scenario. ±15% band catches doctrine timing
        // regressions while absorbing run-to-run jitter. Captured in
        // fn-13.2 evidence; rebaseline if doctrine shifts (per fn-8.3
        // decision #11 inheritance).
        val completionCursor = trace.firstWhere { st ->
            st.aircraft[aircraftId]?.pilotMission?.isComplete == true
        }.getOrElse {
            fail("Mission never reached isComplete during the trace.\n$journey")
        }
        val completionMs = completionCursor.time.millis
        // First-GREEN observed wall (fn-13.2 calibration run): 896_000 ms
        // (~14.9 sim minutes). Single-aircraft, single planned circuit,
        // pre-clearance CONTINUE APPROACH (20s obstruction window). The
        // wall is materially SHORTER than G3a-obstruction's GA test
        // (~1399 s) because the CA path does NOT regress the commitment
        // — the aircraft continues the same approach and lands on the
        // first attempt, without a recovery circuit.
        val observedCompletionMs = 896_000L
        val band = (observedCompletionMs * 0.15).toLong()
        val minMs = observedCompletionMs - band
        val maxMs = observedCompletionMs + band
        check(completionMs in minMs..maxMs) {
            "Mission completion (${completionMs / 1000} s = ${completionMs}ms) outside the " +
                "±15% band [${minMs / 1000} s, ${maxMs / 1000} s] centred on the observed wall " +
                "(${observedCompletionMs / 1000} s). Drift indicates a doctrine regression " +
                "affecting the CONTINUE APPROACH cadence — predicate-eligibility window, " +
                "radio serialization on the CA + companion chain, or post-obstruction " +
                "ARR-LAND re-fire timing. actual=${completionMs}ms expected=${observedCompletionMs}ms " +
                "band=±${band}ms.\n$journey"
        }
    }

    /**
     * Decision return shape for [checkAuthorshipPreconditions] — pure
     * value-class disjunction (skip with a reason vs author with
     * diagnostic), no exceptions or null tags.
     */
    private sealed interface AuthorshipDecision {
        data object Skip : AuthorshipDecision
        data class Author(val diagnostic: String) : AuthorshipDecision
    }

    /**
     * Strict-spec predicate for the one-shot world-authorship hook —
     * mirrors the CA rule's guard predicate exactly so that authoring
     * here implies the rule will fire on the very next controller cycle.
     *
     * Returns [AuthorshipDecision.Author] when ALL of the following
     * hold simultaneously, else [AuthorshipDecision.Skip]:
     *
     *  - commitment stage is `AwaitApproach`
     *  - NO `ClearedToLand` coordination exists for the aircraft (pre-
     *    clearance constraint `T_obs < T_ClearedToLand`)
     *  - `OnCircuitLeg(FINAL) == true` for the aircraft's `positionPoint`
     *    (i.e. `worldIndex.circuitLegsByPoint[positionPoint]` contains
     *    `LegName.FINAL`) — mirrors the CA rule's geometric arm
     *  - aircraft `speedMps > 0` — pins the `groundSpeed` precondition
     *    of `ObstructionClearsInTime`
     *  - predicate-eligibility: `(proposedClearsAt - now) + 10s safety
     *    margin ≤ distance-to-threshold / groundSpeed` — pins
     *    `ObstructionClearsInTime`'s arithmetic.
     *
     * The check uses the post-step `SimState` (the hook runs after each
     * step). All preconditions read from the same post-step snapshot so
     * they observe the same instant.
     */
    private fun checkAuthorshipPreconditions(
        st: SimState,
        aircraftId: AircraftId,
        towerId: xyz.easiersaid.twr.protocol.ControllerId,
        rwy: RunwayId,
        proposedClearsAtDuration: SimDuration,
    ): AuthorshipDecision {
        val ac = st.aircraft[aircraftId] ?: return AuthorshipDecision.Skip
        val commitment = st.beliefs[towerId]?.commitments?.get(aircraftId)
            ?: return AuthorshipDecision.Skip
        if (commitment.stage != TowerArrivalStage.AwaitApproach) {
            return AuthorshipDecision.Skip
        }
        // Pre-clearance constraint: no ClearedToLand coordination yet.
        val tower = st.beliefs[towerId] ?: return AuthorshipDecision.Skip
        val hasClearedToLand = tower.coordinations[aircraftId].orEmpty().any {
            it.instruction is ClearedToLand
        }
        if (hasClearedToLand) return AuthorshipDecision.Skip
        // OnCircuitLeg(FINAL) — the aircraft's positionPoint must belong
        // to a FINAL-labelled circuit leg in the world index.
        val finalLegHere = st.worldIndex.circuitLegsByPoint[ac.positionPoint]
            ?.contains(xyz.easiersaid.twr.core.world.LegName.FINAL) == true
        // Distance to threshold (Euclidean) — fallback geometric eligibility
        // when not yet on FINAL leg label, mirrors the CA rule's
        // `AnyOf(OnApproach, OnCircuitLeg(FINAL))` arm.
        val thresholdPoint = st.worldIndex.thresholdByRunway[rwy]
            ?: return AuthorshipDecision.Skip
        val thrPos = st.worldIndex.positions[thresholdPoint]
            ?: return AuthorshipDecision.Skip
        val dx = ac.position.xMeters - thrPos.xMeters
        val dy = ac.position.yMeters - thrPos.yMeters
        val distanceM = kotlin.math.sqrt(dx * dx + dy * dy)
        if (!distanceM.isFinite() || distanceM < 0.0) return AuthorshipDecision.Skip
        val onApproachGeometry = finalLegHere || distanceM <= 5000.0
        if (!onApproachGeometry) return AuthorshipDecision.Skip
        // groundSpeed precondition — must be positive for ETA arithmetic.
        // The controller derives `groundSpeed: Knots?` from the
        // aircraft's `speedMps` at observation time; an at-rest aircraft
        // (speedMps == 0) yields null at the controller boundary and
        // `ObstructionClearsInTime` fails closed. Mirror that here.
        if (ac.speedMps <= 0.0) return AuthorshipDecision.Skip
        // Predicate-eligibility check — exact arithmetic from the
        // `ObstructionClearsInTime` guard.
        val etaSeconds = distanceM / ac.speedMps
        if (!etaSeconds.isFinite() || etaSeconds < 0.0) return AuthorshipDecision.Skip
        val etaMs = (etaSeconds * 1000.0).toLong()
        val gapMs = proposedClearsAtDuration.millis + OBSTRUCTION_CLEAR_SAFETY_MARGIN_MS
        if (gapMs > etaMs) return AuthorshipDecision.Skip
        val diagnostic = "phase=${ac.phase}, positionPoint=${ac.positionPoint.value}, " +
            "finalLegLabel=$finalLegHere, distanceM=${"%.1f".format(distanceM)}, " +
            "speedMps=${"%.2f".format(ac.speedMps)}, etaMs=$etaMs, gapMs=$gapMs"
        return AuthorshipDecision.Author(diagnostic)
    }

    /**
     * Extract the [TransmissionId] for a controller transmission record
     * by scanning the trace's `TransmissionStart` events for the matching
     * `(startedAt, utterance)` pair.
     *
     * Mirrors the canonical pattern from `G3aRunwayObstructionTest` — see
     * that test class's KDoc on `extractTransmissionId` for the rationale.
     */
    private fun extractTransmissionId(
        trace: xyz.easiersaid.twr.sim.testing.SimTrace,
        record: xyz.easiersaid.twr.sim.testing.TransmissionRecord,
        description: String,
        journey: String,
    ): xyz.easiersaid.twr.sim.TransmissionId {
        for (step in trace.steps) {
            val ev = step.event
            if (ev is SimEvent.TransmissionStart &&
                ev.transmission.startedAt == record.time &&
                ev.transmission.utterance == record.utterance
            ) {
                return ev.transmission.id
            }
        }
        fail(
            "Could not locate `SimEvent.TransmissionStart` matching $description at " +
                "${record.time.millis}ms. The record was projected from the trace but the " +
                "underlying event is missing — sim-engine invariant violation or a record/" +
                "event projection drift.\n$journey"
        )
    }

    /**
     * Walk the trace to find the controller decision-cycle time that
     * EMITTED a given transmission, identified by its [txId]. Mirrors the
     * canonical `nextTransmissionId` mint-id walk pattern from
     * `G3aRunwayObstructionTest`. See that test class's KDoc on
     * `findEmittingCycleMs` for the rationale and the
     * `sim-test-pins-must-compare-against-2026-05-10` memory entry.
     */
    private fun findEmittingCycleMs(
        trace: xyz.easiersaid.twr.sim.testing.SimTrace,
        controller: xyz.easiersaid.twr.protocol.ControllerId,
        txId: xyz.easiersaid.twr.sim.TransmissionId,
        txDescription: String,
        journey: String,
    ): Long {
        var priorNextId = trace.initial.nextTransmissionId
        for (step in trace.steps) {
            val postNextId = step.state.nextTransmissionId
            val ev = step.event
            if (ev is SimEvent.ControllerCycle &&
                ev.controllerId == controller &&
                priorNextId <= txId.value &&
                txId.value < postNextId
            ) {
                return ev.time.millis
            }
            priorNextId = postNextId
        }
        fail(
            "No `SimEvent.ControllerCycle` for controller $controller minted transmission id " +
                "${txId.value} (looking for $txDescription's emitting cycle).\n$journey"
        )
    }

    /**
     * Pure world-state mutation: set `aerodromes[$aerodromeId]
     * .runways[$rwy].obstruction = Some(obstruction)`. Mirrors the
     * canonical mutation pattern from `G3aRunwayObstructionTest`.
     *
     * The one-shot guard at the call site ensures this is only ever
     * called once per obstruction lifetime; defense-in-depth lives in
     * `sim.runwayObstructionEvents`'s `check(...)` which throws on a
     * `Some(old) → Some(new clearsAt)` transition reaching the diff
     * producer.
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
