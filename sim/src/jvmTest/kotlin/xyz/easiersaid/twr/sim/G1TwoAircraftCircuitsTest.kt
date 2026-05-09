package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.assess.WakeRule
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
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.LineUpAndWait
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
import xyz.easiersaid.twr.sim.testing.firstWhere
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.missionStepTransitions
import xyz.easiersaid.twr.sim.testing.positionPointTransitions
import xyz.easiersaid.twr.sim.testing.requiredStartPoints
import xyz.easiersaid.twr.sim.testing.responsibilityTransitions
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace
import xyz.easiersaid.twr.sim.testing.stateAtOrBefore

/**
 * G1 — single-aerodrome two-aircraft VFR circuit-training golden test.
 *
 * Two AI aircraft (`OE-ABC` and `OE-DEF`, both C172 / `WakeCategory.L`)
 * start at adjacent LOWG GA stands, taxi out, take off, fly two circuits
 * each (touch-and-go + full-stop on last), then taxi back to a stand.
 * Exercises ATC sequencing on a single runway with two aircraft in the
 * pattern simultaneously: taxi sequencing, single-runway gating,
 * extend-downwind spacing, wake-rule evaluation, and conflict-resolution
 * three-event chain.
 *
 * ## Status: FAILING — G1 closure pending (fn-8.2 first-pass surface)
 *
 * fn-8.2 first run (2026-05-09) surfaces a multi-aircraft circuit-pattern
 * sequencing defect. Mirrors the G2 closure pattern (G2 was closed over
 * four interlocking fixes after first run revealed the cross-aerodrome
 * sequencing gaps). The test ships **loudly failing** per the codebase's
 * no-corners-cut convention (`AGENTS.md` § Golden tests: "a failing
 * golden test is documented in its KDoc with the specific blocker and
 * stays loudly failing. No `@Disabled`, skip-list, or exclusion set.").
 *
 * **Failure mode (observed 2026-05-09, run wall = 90 sim minutes):**
 * Aircraft A (OE-ABC) executes the first circuit's touch-and-go
 * correctly. On the second circuit she reports Downwind FULL_STOP at
 * ~T+20:48; the controller responds with repeated `ClearedTouchAndGo`
 * re-issues (`ARR-LAND-TNG-REISSUE`) on the CAP 413 §2.7 cadence. A
 * physically reaches the runway threshold (`LOWG_RWY_16C_THR`) and
 * her mission step transitions to `REPORT_RUNWAY_VACATED`, but **no
 * `ClearedToLand` and no `AfterLandingVacateVia` instruction** is ever
 * transmitted; she stays on the runway in `LandingRoll` phase
 * indefinitely. The tower's commitment for A remains at
 * `AwaitLandedObserved` with the runway-duty `holder` still set to A.
 * B is queued for departure (`RunwayQueueEntry(B, DEPARTURE)`) and
 * never receives a takeoff slot. Final transmission count saturates
 * at ~418 — the simulator effectively deadlocks despite event-queue
 * activity continuing.
 *
 * The matching single-aircraft case (G0 — circuits=1, single OE-ABC)
 * stays green; the two-aircraft circuits=1 variant exhibits the same
 * deadlock with `BacktrackRunway` issued (the rule fires) but the
 * pilot never advances. This narrows the failure to the multi-aircraft
 * coordination / commitment-stage path, **not** the FULL_STOP intent
 * flip itself or the runway-exit selection.
 *
 * **Suspect zones (next-pass investigation, not yet root-caused):**
 *  - Coordination ledger interaction with two-aircraft pendingReadback
 *    matching — first-pass evidence shows the T&G coordination on A
 *    keeps escalating (Issued → Querying) even after A reads back,
 *    consistent with a same-aircraft / cross-aircraft readback-match
 *    misattribution under multi-aircraft load.
 *  - Commitment-stage advancement on the touch-and-go → full-stop
 *    transition while the previous-circuit T&G coordination is still
 *    live; G2's `acceptReadback` "close every Correct coord" fix may
 *    have a sibling on the arrival-side T&G commitment lifecycle.
 *  - Runway-duty `lastOperationCompletedAt` doesn't appear to advance
 *    past A's first-circuit T&G touchdown, so the duty-state machine
 *    may still hold A as the in-flight ARRIVAL holder when the second
 *    circuit's FULL_STOP path tries to fire.
 *
 * This is a **closure-pass blocker** (analogous to G2's pre-closure
 * state). G1 cannot ship green until the multi-aircraft circuit-pattern
 * sequencing path is fixed. The fix is out of fn-8.2's scope (the test
 * itself is correctly authored per the spec — investigation, plan
 * review, and codex sign-off all clean over 5 iterations); it belongs
 * to a follow-up closure pass parallel to fn-8.
 *
 * **Sibling tests:**
 *  - G0 — [LowgGoldenTest] — single-aerodrome, single-aircraft circuit
 *    training. The structural template G1 mirrors line-for-line per
 *    aircraft.
 *  - G2 — [G2CrossAerodromeVfrTest] — multi-aerodrome (LOWG → LJMB)
 *    transit, single-aircraft. G1's multi-aircraft sibling at a single
 *    aerodrome.
 *
 * **What G1 distinctively pins:**
 *  - **Per-aircraft outcomes**: both aircraft complete their missions and
 *    park at stand points.
 *  - **Causal partial-orders** across two aircraft: taxi sequencing
 *    (A precedes B), single-runway gating (A's takeoff precedes B's
 *    line-up), conflict-resolution chain (extendDownwind(B) ≺ touchdown(A)
 *    ≺ turnBase(B)) — the load-bearing G1 invariant per epic R6 — and
 *    final-circuit landing order (A lands before B).
 *  - **Wake-rule evaluation** (R7): the controller's `SeparationEngine`
 *    classifies the L→L pair into [WakeRule.IcaoNoAdditionalWakeMinimum]
 *    via the fallback path (no L→L row in `ICAO_WAKE_TABLE`). The pin
 *    asserts the rule was evaluated, not just the absence of extra
 *    spacing.
 *  - **Forced-conflict invariant** (R8): `ExtendDownwind(B)` is observed
 *    during the run. If absent, the test fails loud — circuit timing
 *    shifted and the conflict authoring went dull, masking the
 *    conflict-resolution code path.
 *
 * **Conflict authoring**: B's mission-start is delayed via a deferred
 * first `PilotDecisionTick` in `initialEvents` — **not** a per-plan
 * filing offset. Both aircraft's `FiledPlan`s are filed at `SimTime.ZERO`
 * via `Fixture.load()`'s `initialEvents`; B's pilot ticks begin at
 * `T+2 min`. The exact offset is empirical; the load-bearing pin is the
 * causal three-event chain, not the offset value. A future refactor that
 * wires `Fixture` per-plan filing time is filed as
 * `D-PASS-fixture-per-plan-filing-time`; G1's authoring stays on the
 * mission-start offset until then.
 *
 * **Wake category lives on `AircraftState`, not `FiledPlan`.** Both
 * aircraft are constructed with `type = AircraftType.C172` (→
 * `WakeCategory.L`) so the R7 wake-rule pin holds. The fixture's
 * KDoc records the intended pairing; this test enforces it at
 * `AircraftState` construction.
 *
 * **Time band**: first-implementation uses the 90-min generous ceiling
 * (mirrors G2). Post-first-green captures the observed wall in
 * `## Evidence` and tightens to a ±15% band — both iterations belong to
 * fn-8.2 per spec acceptance R6.
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

        val missionA = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 2, fullStopOnLast = true),
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans[aircraftAId]
                ?: fail("LOWG_TWO_AIRCRAFT fixture missing flight plan for $aircraftAId"),
        )
        val missionB = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 2, fullStopOnLast = true),
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
        // 90 sim minutes ceiling (mirrors G2 — generous first-implementation
        // bound; tighten to ±15% band post-first-green per fn-8.2 acceptance
        // §8). A wedged run hits the wall.
        val until = SimTime.ZERO + SimDuration.ofMillis(90 * 60 * 1000L)
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

        // (3) Conflict-resolution three-event chain (the load-bearing G1
        // invariant per epic R6):
        //   extendDownwind(B).time < touchdown(A).time < turnBase(B).time
        //
        // The chain captures the sequencing intent: B is told to extend
        // downwind because A is ahead (still landing); then A touches down
        // (lands); then with A clear, B turns base to land in trail.
        //
        // Touchdown is observed via `PilotPhase.LandingRoll` first
        // appearance for A — a stable post-touchdown observable in the
        // trace. The transient `Final → Land` transition is brief; the
        // first cursor where A's phase enters `LandingRoll` is the
        // doctrinally-correct pin (A is on the runway, decelerating after
        // touchdown).
        //
        // turnBase(B) is observed via `PilotPhase.Base` first appearance
        // for B, which is the observable consequence of any TurnBase
        // instruction (rule firing → pilot apply → phase transition). We
        // pin against the phase transition, not the instruction record,
        // because phase is the stable end-state observable; instruction
        // records may be re-issued or superseded.
        val extendBRecord = records.firstControllerInstructionOf<ExtendDownwind>(aircraftBId)
            .getOrElse {
                fail("Forced-conflict invariant violated (R8): B never had to extend downwind. " +
                    "Either circuit timing shifted (refactor needed?), or the offset is wrong. " +
                    "Adjust B's mission-start offset until extend-downwind fires.\n$journey")
            }
        val extendBMs = extendBRecord.time.millis

        // touchdown(A): first cursor where A is in LandingRoll, AT-OR-AFTER
        // the extendDownwind(B) record time. The "at-or-after" anchor
        // distinguishes A's first circuit's touch-and-go landing roll from
        // her second, post-extend touchdown — a regression that fired
        // ExtendDownwind earlier (e.g. before A's first landing) would
        // otherwise mask the chain.
        val extendBSimTime = extendBRecord.time
        val touchdownACursor = trace.firstWhere { st ->
            val a = st.aircraft[aircraftAId] ?: return@firstWhere false
            st.now.millis >= extendBSimTime.millis && a.phase == PilotPhase.LandingRoll
        }.getOrElse {
            fail("A never reached LandingRoll after B's ExtendDownwind (${extendBMs}ms). " +
                "The conflict-resolution chain requires A's touchdown to follow B's " +
                "extension.\n$journey")
        }
        val touchdownAMs = touchdownACursor.time.millis

        // turnBase(B): first cursor where B is in Base, AT-OR-AFTER A's
        // touchdown. B's first downwind → base transition (pre-extend) is
        // not the chain's anchor; the post-extend turnBase is.
        val turnBaseBCursor = trace.firstWhere { st ->
            val b = st.aircraft[aircraftBId] ?: return@firstWhere false
            st.now.millis >= touchdownAMs && b.phase == PilotPhase.Base
        }.getOrElse {
            fail("B never reached Base after A's touchdown (${touchdownAMs}ms). " +
                "The conflict-resolution chain requires B to turn base after A lands.\n$journey")
        }
        val turnBaseBMs = turnBaseBCursor.time.millis

        check(extendBMs < touchdownAMs && touchdownAMs < turnBaseBMs) {
            "Conflict-resolution three-event chain violated. Expected: " +
                "extendDownwind(B).time ($extendBMs ms) < touchdown(A).time " +
                "($touchdownAMs ms) < turnBase(B).time ($turnBaseBMs ms). " +
                "If extend < turnBase < touchdown the controller turned B base before A " +
                "had landed (separation regression); if touchdown < extend < turnBase the " +
                "extend was decorative (no conflict actually resolved).\n$journey"
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

        // ── Wake-rule pin (R7) ──────────────────────────────────────────────
        // Two C172s are both `WakeCategory.L`. L→L is not in
        // `ICAO_WAKE_TABLE` (no row at `WakeSeparation.kt`); the classifier
        // hits the fallback path → `WakeRule.IcaoNoAdditionalWakeMinimum(L,
        // L)`. The pin asserts the rule was *evaluated* (wake category
        // present, classifier ran), not just the absence of extra spacing.
        //
        // SeparationAssessment fields are `aircraft` / `other` (not `leader`
        // / `follower` — those names live INSIDE the `WakeRule` cases).
        // Locate the first state where the tower's `separationAssessments`
        // includes the (A, B) pair, then assert on that assessment's
        // `wakeRule`.
        val wakeAssessmentCursor = trace.firstWhere { st ->
            val tb = st.beliefs[tower.id] ?: return@firstWhere false
            tb.separationAssessments.any { it.aircraft == aircraftAId && it.other == aircraftBId }
        }.getOrElse {
            fail("Tower never produced a SeparationAssessment for the (A, B) pair. " +
                "With both aircraft in the arrival sequence simultaneously, the " +
                "separation engine must classify the wake rule.\n$journey")
        }
        val firstAbAssessment = wakeAssessmentCursor.state.beliefs.getValue(tower.id)
            .separationAssessments
            .first { it.aircraft == aircraftAId && it.other == aircraftBId }
        val rule = firstAbAssessment.wakeRule
        check(
            rule is WakeRule.IcaoNoAdditionalWakeMinimum &&
                rule.leader == WakeCategory.L &&
                rule.follower == WakeCategory.L,
        ) {
            "Wake-rule pin (R7): expected first (A, B) SeparationAssessment.wakeRule = " +
                "IcaoNoAdditionalWakeMinimum(leader=L, follower=L) (L→L pair has no row " +
                "in ICAO_WAKE_TABLE, so the classifier must hit the fallback path). " +
                "Got: $rule.\n$journey"
        }

        // ── Forced-conflict invariant pin (R8) ──────────────────────────────
        // The three-event chain above already requires `ExtendDownwind(B)`
        // to be present (its `getOrElse` block fails loud). Restate the
        // contract explicitly here so a future refactor of the chain pin
        // (e.g. structural changes to record discovery) can never silently
        // remove the invariant.
        val extendInstructions = records.filter { rec ->
            val out = (rec.utterance as? Utterance.FromController)?.output as? ControllerOutput.Instruct
                ?: return@filter false
            val instr = (out.dispatch as? Dispatch.Direct)?.instruction
            instr is ExtendDownwind && out.target == aircraftBId
        }
        check(extendInstructions.isNotEmpty()) {
            "Forced-conflict invariant (R8) violated: B never had to extend downwind. " +
                "Either circuit timing shifted (refactor needed?), or the offset is wrong. " +
                "Adjust B's mission-start offset until extend-downwind fires.\n$journey"
        }

        // ── Time band ───────────────────────────────────────────────────────
        // First-implementation acceptance: 90-min generous ceiling matches
        // G2's wall budget. The run completes when the later mission
        // (mission B) reaches `isComplete`; cap with the wall above.
        // Post-first-green: capture observed wall in `## Evidence`; pin a
        // ±15% band per fn-8.2 acceptance §8.
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
        // Generous ceiling: 90 min — same wall as G2.
        val maxMs = 90 * 60 * 1000L
        check(completionAMs <= maxMs) {
            "A's mission completion (${completionAMs / 1000} s) exceeds the 90-min " +
                "wall budget; the run wedged or the doctrine timing shifted.\n$journey"
        }
        check(completionBMs <= maxMs) {
            "B's mission completion (${completionBMs / 1000} s) exceeds the 90-min " +
                "wall budget; the run wedged or the doctrine timing shifted.\n$journey"
        }

        // ── stateAtOrBefore sanity check ────────────────────────────────────
        // The `stateAtOrBefore(time)` SimTrace helper is added in fn-8.2
        // for the conflict-resolution pin's record→state cursor bridge.
        // Sanity-check: the cursor at extendDownwind(B)'s record time
        // returns a state whose sim-time is <= extendBMs.
        val extendBStateCursor = trace.stateAtOrBefore(extendBSimTime).getOrElse {
            fail("stateAtOrBefore(${extendBMs}ms) returned None despite the trace " +
                "covering this time. Helper regression?\n$journey")
        }
        check(extendBStateCursor.time.millis <= extendBMs) {
            "stateAtOrBefore returned a cursor at ${extendBStateCursor.time.millis}ms " +
                "for query time ${extendBMs}ms — must be at-or-before.\n$journey"
        }
    }
}
