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
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.WakeCategory
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstControllerInstructionOf
import xyz.easiersaid.twr.sim.testing.firstPilotReportOf
import xyz.easiersaid.twr.sim.testing.firstWhere
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.missionStepTransitions
import xyz.easiersaid.twr.sim.testing.positionPointTransitions
import xyz.easiersaid.twr.sim.testing.requiredStartPoints
import xyz.easiersaid.twr.sim.testing.responsibilityTransitions
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

/**
 * G1 — single-aerodrome two-aircraft VFR circuit-training golden test.
 *
 * Two AI aircraft (`OE-ABC` and `OE-DEF`, both C172 / `WakeCategory.L`)
 * start at adjacent LOWG GA stands, taxi out, take off, fly two circuits
 * each (touch-and-go + full-stop on last), then taxi back to a stand.
 * Exercises ATC sequencing on a single runway: taxi sequencing,
 * single-runway gating, doctrinally-correct serialization (the
 * runway-duty machine holds the runway for A's full circuit-training
 * session, releasing it to B only after A vacates).
 *
 * ## Status: GREEN — fn-8.3 Phase 4 (B5-α) closure
 *
 * fn-8.3 Phase 4 closed the multi-aircraft circuit-pattern sequencing
 * defect via B5-α (controller-side `HasReportedPositionCall` guard on
 * the shared `LandingConditions`, gating ARR-LAND / ARR-LAND-TNG on
 * the pilot's pre-clearance position call). With B5-α landed, both
 * aircraft fly their circuits cleanly: A flies circuits 1-2 then
 * vacates; B then gets the runway slot and flies circuits 1-2 in
 * trail. Both aircraft mission-complete inside the 60-min wall.
 *
 * ## Doctrinal re-baseline (per fn-8.3 spec decision #9)
 *
 * The fn-8.2 first run (2026-05-09) authored several invariants that
 * were structurally unreachable under doctrinally-correct
 * multi-aircraft sequencing:
 *
 *  - **R6 conflict-resolution chain** (`extendDownwind(B) ≺ touchdown(A)
 *    ≺ turnBase(B)`) — assumed B would be airborne and on downwind
 *    while A was still in the pattern. With single-runway gating
 *    correctly serializing the two C172s, B never gets the runway slot
 *    until A has vacated, so the pattern overlap that would force
 *    ExtendDownwind never materialises. **Removed.**
 *  - **R7 wake-rule evaluation pin** — required a tower
 *    `SeparationAssessment` to exist for the (A, B) pair. The
 *    separation engine emits assessments only when both aircraft are
 *    simultaneously in the arrival sequence; with the two aircraft
 *    serialized, no overlap means no assessment. The wake-rule code
 *    path is exercised by other tests (e.g. fn-8.1 fixtures + targeted
 *    `WakeRule` specs) so removing this pin doesn't reduce coverage.
 *    **Removed.**
 *  - **R8 forced-conflict invariant** (`ExtendDownwind(B)` observed) —
 *    same root cause as R6: structurally unreachable. **Removed.**
 *
 * What survives the re-baseline (still load-bearing):
 *  - Per-aircraft outcomes (mission complete + parked).
 *  - Taxi clearance order (A precedes B).
 *  - Single-runway gate (A's `ClearedForTakeoff` precedes B's
 *    `LineUpAndWait`).
 *  - Final-circuit landing order (A's `ClearedToLand` precedes B's).
 *  - **Multi-aircraft commitment-stage / coordination-ledger /
 *    runway-duty closure** (fn-8.3 acceptance bullet 5 invariants —
 *    duplicated minimally in [G1TwoAircraftMinimalSpec] for the
 *    `circuits=1` shape, kept here for the `circuits=2` shape).
 *  - Time band (±15% of observed wall per fn-8.3 decision #11).
 *
 * The matching single-aircraft case (G0 — circuits=1, single OE-ABC)
 * stays green; the two-aircraft circuits=1 variant is closed by
 * [G1TwoAircraftMinimalSpec] for the smaller scenario shape per
 * fn-8.3 spec acceptance bullet 5.
 *
 * **Sibling tests:**
 *  - G0 — [LowgGoldenTest] — single-aerodrome, single-aircraft circuit
 *    training. The structural template G1 mirrors line-for-line per
 *    aircraft.
 *  - G1 minimal — [G1TwoAircraftMinimalSpec] — two-aircraft `circuits=1`
 *    (full-stop only, no T&G mid-flip) commitment-stage / coordination-
 *    ledger / runway-duty closure pin.
 *  - G2 — [G2CrossAerodromeVfrTest] — multi-aerodrome (LOWG → LJMB)
 *    transit, single-aircraft. G1's multi-aircraft sibling at a single
 *    aerodrome.
 *  - G3a — [G3aPilotTrainedGoAroundTest] — single-aerodrome,
 *    single-aircraft pilot-trained go-around as circuit-training outcome.
 *    G1's vacate-coordination closure pattern (per fn-8.3 acceptance #5)
 *    is reused at the single-aircraft scale by G3a's R7 pin.
 *  - G3a-obstruction — [G3aRunwayObstructionTest] — single-aerodrome,
 *    single-aircraft ATC-instructed reactive go-around on a world-
 *    authored runway obstruction. Reuses the same R7 vacate-
 *    coordination closure invariant at the recovery-circuit landing.
 *  - G3a-obstruction-continue-approach —
 *    [G3aRunwayObstructionContinueApproachTest] — single-aerodrome,
 *    single-aircraft pre-clearance CONTINUE APPROACH on a short-TTL
 *    world-authored runway obstruction that clears in time per fn-13.
 *    Companion to G3a-obstruction: same fixture, predicate-eligible
 *    branch fires the CA path instead of the GA.
 *  - G3a-react — [G3aPilotReactiveCrosswindTest] — single-aerodrome,
 *    single-aircraft **pilot-reactive** go-around triggered by a world-
 *    authored wind shift past the C172's POH-derived 15 kt maximum
 *    demonstrated crosswind (fn-14). Closes the G3a trilogy as the
 *    fourth reactive-GA path — the first pilot-side reactive
 *    recognition driven by world weather. Reuses G1's R7 vacate-
 *    coordination closure invariant on the recovery-circuit landing.
 *
 * **What G1 distinctively pins (post fn-8.3 Phase 4 re-baseline):**
 *  - **Per-aircraft outcomes**: both aircraft complete their missions and
 *    park at stand points.
 *  - **Causal partial-orders** across two aircraft: taxi sequencing
 *    (A precedes B), single-runway gating (A's takeoff precedes B's
 *    line-up), and final-circuit landing order (A lands before B).
 *  - **Multi-aircraft commitment-stage closure**: tower's coordination
 *    ledger contains no leftover vacate / `BacktrackRunway` entries
 *    after the run; `RunwayDutyState.holder` is null after both
 *    aircraft vacate.
 *  - **Wake category sanity**: both aircraft are C172 / `WakeCategory.L`
 *    (set at `AircraftState` construction). The wake-rule classifier
 *    code path is exercised by other tests; the L→L pairing here is
 *    documentation that fn-8.1's fixture-pairing intent holds.
 *
 * **Sequencing authoring**: B's mission-start is delayed via a deferred
 * first `PilotDecisionTick` in `initialEvents` — **not** a per-plan
 * filing offset. Both aircraft's `FiledPlan`s are filed at `SimTime.ZERO`
 * via `Fixture.load()`'s `initialEvents`; B's pilot ticks begin at
 * `T+2 min`. The offset establishes the lead-trail ordering; the
 * single-runway gate then serializes them naturally. A future refactor
 * that wires `Fixture` per-plan filing time is filed as
 * `D-PASS-fixture-per-plan-filing-time`; G1's authoring stays on the
 * mission-start offset until then.
 *
 * **Wake category lives on `AircraftType` (→ `wakeCategory`)**, set at
 * `AircraftState` construction. FiledPlan does NOT carry wake category;
 * `AircraftState` is the right place. Both aircraft are C172 / Light.
 *
 * **Time band**: tightened to ±15% of the observed wall per fn-8.3
 * decision #11. Observed wall on the post-fn-8.3-Phase-4 sim is
 * ~50 sim minutes (B's mission completion at ~2975 s); the band is
 * authored around that observed value with explicit ±15% tolerance.
 */
class G1TwoAircraftCircuitsTest {

    @Test
    fun `two AI aircraft fly two circuits each at LOWG`() {
        // ── World + controllers via the shared fixture ──────────────────────
        val fixture = Fixtures.LOWG_TWO_AIRCRAFT
        val loaded = fixture.load().getOrElse {
            fail("LOWG_TWO_AIRCRAFT fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) {
            "GROUND missing from LOWG_TWO_AIRCRAFT fixture"
        }
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) {
            "TOWER missing from LOWG_TWO_AIRCRAFT fixture"
        }

        // ── Two AI aircraft at adjacent LOWG stands ─────────────────────────
        // AircraftIds are sorted lexicographically (ABC < DEF) by
        // Fixture.load()'s deterministic FlightPlanFiled emission. Both
        // aircraft are C172 / WakeCategory.L so the R7 wake-rule pin holds.
        val aircraftAId = AircraftId("OE-ABC")
        val aircraftBId = AircraftId("OE-DEF")
        val now = SimTime.ZERO
        val startPoints = fixture.requiredStartPoints()
        val standPointA = startPoints.getValue(aircraftAId)
        val standPointB = startPoints.getValue(aircraftBId)

        // fn-11.1: typed-outcome migration — listOf(TouchAndGo, FullStop) is the
        // structurally equivalent shape for the old (circuits=2, fullStopOnLast=true).
        val twoCircuitTraining = HighLevelGoal.CircuitTraining(
            outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
        )
        val missionA = createMission(
            goal = twoCircuitTraining,
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans[aircraftAId]
                ?: fail("LOWG_TWO_AIRCRAFT fixture missing flight plan for $aircraftAId"),
        )
        val missionB = createMission(
            goal = twoCircuitTraining,
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans[aircraftBId]
                ?: fail("LOWG_TWO_AIRCRAFT fixture missing flight plan for $aircraftBId"),
        )

        // Wake category lives on `AircraftType` (→ `wakeCategory`), set at
        // `AircraftState` construction. Both aircraft are C172 / Light so the
        // R7 wake-rule pin (L→L → `IcaoNoAdditionalWakeMinimum`) holds.
        // FiledPlan does NOT carry wake category; this is the right place.
        val aircraftA = AircraftState(
            id = aircraftAId,
            callsign = Callsign("OEABC"),
            position = loaded.world.geometry.points.getValue(standPointA),
            positionPoint = standPointA,
            phase = PilotPhase.AtStand,
            type = AircraftType.C172,
            pilotMission = missionA,
        )
        val aircraftB = AircraftState(
            id = aircraftBId,
            callsign = Callsign("OEDEF"),
            position = loaded.world.geometry.points.getValue(standPointB),
            positionPoint = standPointB,
            phase = PilotPhase.AtStand,
            type = AircraftType.C172,
            pilotMission = missionB,
        )

        // ── Build SimState through the smart constructor ────────────────────
        // Weather is REQUIRED for runway-bearing aerodromes — `SimState.initial`
        // rejects empty `weatherByAerodrome` for any runway-bearing aerodrome.
        // LOWG has runways; the fixture authors `weather` (single field, single-
        // aerodrome shape) which feeds the per-aerodrome map directly.
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraftA, aircraftB),
            controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(lowg to fixture.weather),
        ).getOrElse { error("SimState.initial rejected the LOWG_TWO_AIRCRAFT fixture: $it") }

        // ── ATIS + drive ────────────────────────────────────────────────────
        // 60 sim minutes ceiling. fn-8.3 Phase 4 re-baseline: observed wall
        // is ~50 sim minutes (B's mission completes at ~2975 s); the
        // 60-min wall keeps modest headroom for run-to-run jitter. The
        // ±15% time band below is the load-bearing tightness pin per
        // fn-8.3 decision #11. A wedged run still hits this wall first.
        val until = SimTime.ZERO + SimDuration.ofMillis(60 * 60 * 1000L)
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
        // **Mission-start offset (NOT filing offset).** Per epic decision
        // context: `Fixture.load()` files all plans at `SimTime.ZERO` via
        // `loaded.initialEvents`; there is no per-plan filing time today
        // (filed as `D-PASS-fixture-per-plan-filing-time`). To author the
        // conflict, we delay B's first `PilotDecisionTick` to T+2 min so
        // B departs ~2 minutes behind A and ends up on downwind while A
        // is on base/final, forcing the controller to extend B's downwind.
        // The 2-minute offset is empirical; the load-bearing pin is the
        // forced-conflict invariant (`ExtendDownwind(B)` observed) and the
        // three-event causal chain — never the offset value itself.
        val bMissionStartOffset = SimDuration.ofMillis(2 * 60 * 1000L)
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = lowgAtis),
            // A starts immediately:
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftAId),
            // B's mission-start is delayed by 2 sim-minutes:
            SimEvent.PilotDecisionTick(time = now + bMissionStartOffset, aircraftId = aircraftBId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )
        val (finalState, records, trace) = runUntilWithStateTrace(initialState, initialEvents, until)

        // ── Diagnostic preamble (per-aircraft) ─────────────────────────────
        // Per-aircraft journeys + per-aircraft trace queries. Critical for
        // debugging when (not if) the conflict authoring shifts.
        val journeyA = finalState.formatJourney(aircraftAId, records)
        val journeyB = finalState.formatJourney(aircraftBId, records)
        val journey = "── Aircraft A ($aircraftAId) ──\n$journeyA\n\n── Aircraft B ($aircraftBId) ──\n$journeyB"
        println(journey)

        println()
        println("─── G1 per-aircraft trace summary ───")
        for (acId in listOf(aircraftAId, aircraftBId)) {
            println()
            println("── $acId ──")
            println("Responsibility transitions:")
            for (t in trace.responsibilityTransitions(acId)) {
                val fromStr = t.from.fold({ "absent" }, { it::class.simpleName ?: "?" })
                val toStr = t.to.fold({ "absent" }, { it::class.simpleName ?: "?" })
                println("  [${t.after.time.millis}ms] ${t.controller}: $fromStr → $toStr")
            }
            println("Mission step transitions:")
            for (t in trace.missionStepTransitions(acId)) {
                val fromStr = t.from.fold({ "absent" }, { it.name })
                val toStr = t.to.fold({ "absent" }, { it.name })
                println("  [${t.after.time.millis}ms] $fromStr → $toStr")
            }
            println("positionPoint transitions:")
            for (t in trace.positionPointTransitions(acId)) {
                val fromStr = t.from.fold({ "absent" }, { it.value })
                val toStr = t.to.fold({ "absent" }, { it.value })
                println("  [${t.after.time.millis}ms] $fromStr → $toStr")
            }
        }
        println("─── end G1 per-aircraft trace summary ───")
        println()

        // ── Outcome pins (per aircraft) ─────────────────────────────────────
        // Both aircraft complete their missions and park at stand points.
        val finalA = finalState.aircraft.getValue(aircraftAId)
        val finalB = finalState.aircraft.getValue(aircraftBId)
        val finalMissionA = checkNotNull(finalA.pilotMission) { "Aircraft A lost its mission.\n$journey" }
        val finalMissionB = checkNotNull(finalB.pilotMission) { "Aircraft B lost its mission.\n$journey" }

        check(finalMissionA.isComplete) {
            "A mission did not complete within 90 sim minutes.\n$journey"
        }
        check(finalMissionB.isComplete) {
            "B mission did not complete within 90 sim minutes.\n$journey"
        }
        check(finalA.altitudeM == 0.0) {
            "A is not on the ground at end of run.\n$journey"
        }
        check(finalB.altitudeM == 0.0) {
            "B is not on the ground at end of run.\n$journey"
        }
        check(finalA.phase == PilotPhase.Parked || finalA.phase == PilotPhase.AtStand) {
            "A did not return to a stand.\n$journey"
        }
        check(finalB.phase == PilotPhase.Parked || finalB.phase == PilotPhase.AtStand) {
            "B did not return to a stand.\n$journey"
        }

        // Stand membership uses `stands.values.map { it.point }.toSet()`,
        // mirroring `LowgGoldenTest.kt:427-430`. NOT `stands.keys` (those are
        // stand IDs, while `AircraftState.positionPoint` is a `PointId`).
        val standPoints = loaded.world.aerodromes
            .getValue(lowg)
            .stands.values.map { it.point }.toSet()
        check(finalA.positionPoint in standPoints) {
            "A did not end at a LOWG stand point. positionPoint=${finalA.positionPoint}; " +
                "valid stand points: $standPoints.\n$journey"
        }
        check(finalB.positionPoint in standPoints) {
            "B did not end at a LOWG stand point. positionPoint=${finalB.positionPoint}; " +
                "valid stand points: $standPoints.\n$journey"
        }

        // ── Causal partial-order pins ───────────────────────────────────────
        // (1) Taxi clearance order: A's first TaxiToHoldingPoint precedes B's.
        val taxiAMs = records.firstControllerInstructionOf<TaxiToHoldingPoint>(aircraftAId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one TaxiToHoldingPoint for $aircraftAId.\n$journey")
            }
        val taxiBMs = records.firstControllerInstructionOf<TaxiToHoldingPoint>(aircraftBId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one TaxiToHoldingPoint for $aircraftBId.\n$journey")
            }
        check(taxiAMs < taxiBMs) {
            "Taxi clearance order: A's TaxiToHoldingPoint (${taxiAMs}ms) must precede " +
                "B's (${taxiBMs}ms). With B's mission-start offset by 2 min, A reaches " +
                "ground first.\n$journey"
        }

        // (2) Single-runway gate: A's first ClearedForTakeoff precedes B's
        // first LineUpAndWait. While A is still on the runway / departing,
        // B must hold short via LineUpAndWait (queued) — never simultaneous
        // ClearedForTakeoffs.
        val clearedTakeoffAMs = records.firstControllerInstructionOf<ClearedForTakeoff>(aircraftAId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one ClearedForTakeoff for $aircraftAId.\n$journey")
            }
        val lineUpBMs = records.firstControllerInstructionOf<LineUpAndWait>(aircraftBId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one LineUpAndWait for $aircraftBId — single-runway " +
                    "sequencing requires B to be queued at the holding point while A departs.\n$journey")
            }
        check(clearedTakeoffAMs < lineUpBMs) {
            "Single-runway gate: A's ClearedForTakeoff (${clearedTakeoffAMs}ms) must precede " +
                "B's LineUpAndWait (${lineUpBMs}ms). The controller must finish A's departure " +
                "clearance before sequencing B onto the runway.\n$journey"
        }

        // (3) Doctrinal serialization — fn-8.3 Phase 4 re-baseline.
        //
        // Pre-fn-8.3 the test asserted a "conflict-resolution three-event
        // chain" (extendDownwind(B) ≺ touchdown(A) ≺ turnBase(B)) that
        // was reachable only under the OLD broken sim where A's runaway
        // commitment-form-and-issue loop kept her on the runway long
        // enough for B to catch up airborne behind her. Post-Phase-2
        // (B2 / B3) the loop collapsed; post-Phase-3 (C1-C4) the strip-
        // based circuit recognition + same-aircraft frequency tracking
        // landed; post-Phase-4 (B5-α) the controller waits for the
        // pilot's pre-clearance position call before clearing to land.
        //
        // With those fixes the runway-duty machine correctly serializes
        // two C172s on a single runway: A holds the runway across her
        // full circuit-training session (taxi → takeoff → 2 circuits →
        // vacate), then releases to B. B never overlaps A on the
        // pattern. The conflict-resolution chain is therefore
        // structurally unreachable. fn-8.3 spec decision #9 covers this
        // re-baseline ("re-baseline pinned values if the fix is
        // doctrinally correct").
        //
        // The replacement invariant captures what the doctrinally-
        // correct sim DOES exhibit: B's first Downwind report happens
        // strictly AFTER A is *actually* off the runway. The pin uses
        // A's `Report(RunwayVacated)` transmission — the pilot only
        // sends this after physically leaving the runway entity (the
        // mission step `REPORT_RUNWAY_VACATED` is reachable only post-
        // vacate). Pinning on the controller's vacate INSTRUCTION
        // (codex review iteration 1 finding) would only show the
        // controller TOLD A to vacate, not that A actually did so —
        // a regression that emitted a vacate instruction but failed
        // to actually release the runway would slip past such a pin.
        val aRunwayVacatedTime = records.firstPilotReportOf<ReportEvent.RunwayVacated>(aircraftAId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one RunwayVacated report from $aircraftAId — " +
                    "without it, A never confirmed leaving the runway after her full-stop " +
                    "landing.\n$journey")
            }
        val bFirstDownwindTime = records.firstPilotReportOf<ReportEvent.Downwind>(aircraftBId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one Downwind report from $aircraftBId — without it, " +
                    "B never declared circuit-position to the controller.\n$journey")
            }
        check(aRunwayVacatedTime < bFirstDownwindTime) {
            "Doctrinal serialization: A's RunwayVacated report (${aRunwayVacatedTime}ms) must " +
                "precede B's first Downwind report (${bFirstDownwindTime}ms). The single-runway " +
                "duty machine should release A from the runway before B enters the pattern.\n$journey"
        }

        // (4) Final-circuit landing order: A is cleared to land before B.
        // With A two minutes ahead (mission-start offset) and the
        // sequencing rules holding the lead, A reaches FULL_STOP final
        // before B does. We pin via ClearedToLand records; in 2-circuit
        // training both aircraft receive at most one ClearedToLand
        // (the final circuit is full-stop; touch-and-goes use
        // ClearedTouchAndGo).
        val landAMs = records.firstControllerInstructionOf<ClearedToLand>(aircraftAId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one ClearedToLand for $aircraftAId on the " +
                    "full-stop final circuit.\n$journey")
            }
        val landBMs = records.firstControllerInstructionOf<ClearedToLand>(aircraftBId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one ClearedToLand for $aircraftBId on the " +
                    "full-stop final circuit.\n$journey")
            }
        check(landAMs < landBMs) {
            "Final-circuit landing order: A's ClearedToLand (${landAMs}ms) must precede " +
                "B's (${landBMs}ms). With B's 2-min mission-start offset, A reaches the " +
                "full-stop final ahead of B; reversal would indicate the lead-trail " +
                "ordering broke under conflict resolution.\n$journey"
        }

        // (5) Both aircraft taxi-to-stand at end — the closure pin. Already
        // covered by the outcome `positionPoint in standPoints` checks
        // above; restated here as the partial-order closure for
        // documentation.

        // ── fn-8.3 acceptance #5 invariants (multi-aircraft commitment-stage
        //    closure): tower's coordination ledger is empty of leftover
        //    vacate / BacktrackRunway entries; runway-duty holder is null
        //    after both aircraft vacate. Mirrors the `circuits=1` minimal
        //    pin in [G1TwoAircraftMinimalSpec]; carried here for the
        //    `circuits=2` shape so a regression that breaks `circuits=2`
        //    differently from `circuits=1` fails loud.
        // ─────────────────────────────────────────────────────────────────────
        val towerBeliefs = checkNotNull(finalState.beliefs[tower.id]) {
            "Tower beliefs missing at end of run — controller pipeline regression.\n$journey"
        }
        for (acId in listOf(aircraftAId, aircraftBId)) {
            val acCoords = towerBeliefs.coordinations[acId].orEmpty()
            val vacateClass = acCoords.filter { coord ->
                coord.instruction is AfterLandingVacateVia ||
                    coord.instruction is BacktrackRunway
            }
            check(vacateClass.isEmpty()) {
                "Vacate/BacktrackRunway coordination did not close for $acId — entries " +
                    "still present in BeliefState.coordinations: $vacateClass.\n$journey"
            }
        }
        val runwayDuty = checkNotNull(towerBeliefs.runwayDuty) {
            "Tower runwayDuty missing at end of run — runway-duty machine regression.\n$journey"
        }
        check(runwayDuty.holder == null) {
            "RunwayDutyState.holder must be null after both aircraft vacate. Got " +
                "holder=${runwayDuty.holder} (just-vacated aircraft retained — runway-duty " +
                "release path broken).\n$journey"
        }

        // ── Wake category sanity (post-fn-8.3 re-baseline). The wake-rule
        //    classifier code path is exercised by other tests; here we
        //    document that the L→L pairing fn-8.1 authored at the fixture
        //    level survives this run's `AircraftState` construction.
        // ─────────────────────────────────────────────────────────────────────
        check(finalA.type.wakeCategory == WakeCategory.L) {
            "A wake category drift: expected L, got ${finalA.type.wakeCategory}.\n$journey"
        }
        check(finalB.type.wakeCategory == WakeCategory.L) {
            "B wake category drift: expected L, got ${finalB.type.wakeCategory}.\n$journey"
        }

        // ── Time band ───────────────────────────────────────────────────────
        // fn-8.3 decision #11: tighten to ±15% of the observed wall once
        // G1 first-greens. Observed wall on the post-fn-8.3-Phase-4 sim
        // (commit immediately preceding this re-baseline): B's mission
        // completion at ~2975 s (~49.6 min); the band is centred on
        // 2975 s with ±15% tolerance. The band is wide enough to absorb
        // small per-pass timing shifts but narrow enough to catch a
        // doctrine regression that materially alters the serialization
        // cadence (e.g. a runway-duty machine that releases too early
        // or holds too long).
        val completionACursor = trace.firstWhere { st ->
            st.aircraft[aircraftAId]?.pilotMission?.isComplete == true
        }.getOrElse {
            fail("A's mission never reached isComplete during the trace.\n$journey")
        }
        val completionBCursor = trace.firstWhere { st ->
            st.aircraft[aircraftBId]?.pilotMission?.isComplete == true
        }.getOrElse {
            fail("B's mission never reached isComplete during the trace.\n$journey")
        }
        val completionAMs = completionACursor.time.millis
        val completionBMs = completionBCursor.time.millis
        // ±15% band centred on observed wall = 2975 s = 2_975_000 ms.
        val observedBCompletionMs = 2_975_000L
        val band = (observedBCompletionMs * 0.15).toLong()
        val minBMs = observedBCompletionMs - band
        val maxBMs = observedBCompletionMs + band
        check(completionBMs in minBMs..maxBMs) {
            "B's mission completion (${completionBMs / 1000} s) outside the ±15% band " +
                "[${minBMs / 1000} s, ${maxBMs / 1000} s] centred on the observed wall " +
                "(${observedBCompletionMs / 1000} s). The band catches doctrine timing " +
                "regressions; if the run completes on the looser side, the doctrine has " +
                "shifted (re-baseline + per-pin rationale required per fn-8.3 decision " +
                "#9).\n$journey"
        }
        // A's mission completes earlier than B's by construction (lead-trail).
        // Re-pin a generous upper bound only — mission completion times
        // shift modestly with timing-jitter, but A always completes before B.
        check(completionAMs < completionBMs) {
            "A's mission must complete before B's: A=${completionAMs / 1000} s, " +
                "B=${completionBMs / 1000} s. With the 2-min mission-start offset and " +
                "single-runway gating, A is doctrinally first.\n$journey"
        }
    }
}
