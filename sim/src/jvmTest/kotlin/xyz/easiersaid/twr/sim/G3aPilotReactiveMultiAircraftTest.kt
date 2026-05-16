package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.core.world.WeatherObservation
import xyz.easiersaid.twr.core.world.updateAerodrome
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TurnBase
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport
import xyz.easiersaid.twr.protocol.headingDegreesMagnetic
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.commitmentStageTransitions
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstWhere
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.missionStepTransitions
import xyz.easiersaid.twr.sim.testing.positionPointTransitions
import xyz.easiersaid.twr.sim.testing.requiredStartPoints
import xyz.easiersaid.twr.sim.testing.responsibilityTransitions
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace
import xyz.easiersaid.twr.sim.testing.transitionsOf
import xyz.easiersaid.twr.sim.testing.weatherTransitions

/**
 * G3a-react-multi-aircraft — single-aerodrome **two**-aircraft VFR
 * pilot-reactive go-around triggered by a world-authored wind shift,
 * combined with **controller-side sequencing** of the trailing aircraft
 * (fn-28.4's `ARR-EXTEND-FOR-GA` + `ARR-TURN-BASE` machinery).
 *
 * **Reframed model** (per fn-28 plan-review round 1 Major 1+2+3): pilot-side
 * recognition is independent of ATC; the controller cannot prevent a
 * pilot-side GA. Aircraft A on final declares pilot-reactive GA via the
 * existing crosswind / tailwind branch (`derivePilotEvent`'s final-phase
 * guard fires). Aircraft B on **downwind** is NOT eligible for wind-GA
 * (the final-phase guard rejects the downwind-phase aircraft). The
 * controller observes `Report(GoingAround)` from A and emits
 * `ExtendDownwind` to B so B does not turn base into the GA-active
 * runway. When A's GA state ends per R23 lifecycle (pattern-rejoin
 * report — `Report(Downwind)` / `Report(Final)` / `Report(Base)` — OR
 * a 60s timeout; **NOT** runway-vacate per round-7 Major 4 / round-8
 * Major 3), the controller's `BeliefState.goAroundInProgressByRunway`
 * belief clears, `ARR-TURN-BASE`'s `Not(GoAroundInProgressOnRunway)`
 * guard passes again, and `TurnBase` to B fires in the **same cycle**
 * (per .4's concrete cancel-output contract, round-10 Major 2). The
 * existing `SupersessionRelation(TurnBase, ExtendDownwind, ABANDON)` row
 * (Supersession.kt:69) drops B's prior `ExtendDownwind` coordination —
 * concrete cancel via supersession.
 *
 * Both aircraft are C172s at LOWG. A starts immediately and ends up on
 * final under landing clearance; B's `PilotDecisionTick` is delayed by
 * 2 sim-minutes (mirroring fn-8's `G1TwoAircraftCircuitsTest` mission-
 * start offset) so B reaches downwind while A is still committed to
 * land — the conflict authoring that makes the cancel-via-supersession
 * chain reachable.
 *
 * Three scenarios in one file, mirroring the .4 spec's task scope (M-L,
 * ~1100-1400 LOC budget per `## Description`):
 *
 *  1. **Crosswind GA + extend-downwind**: wind shifts past C172's
 *     POH-demonstrated 15 kt crosswind limit on the active runway. A's
 *     existing crosswind branch fires + transmits `Report(GoingAround)`;
 *     controller writes `goAroundInProgressByRunway[RWY_16C]` and emits
 *     `ExtendDownwind(B)` on the next/same cycle.
 *  2. **Tailwind GA + extend-downwind**: identical shape on the tailwind
 *     axis (15 kt tailwind > C172's 10 kt FAA AFH advisory). Distinct
 *     `applyTailwindGoAround` Tick A pathway; same controller-side
 *     extend-downwind outcome.
 *  3. **GA-recovery / belief-clear**: wind-shift-back recovery scenario.
 *     The crosswind shape fires + B is `ExtendDownwind`-extended; A
 *     completes the go-around pattern and issues a recovery-circuit
 *     `Report(Downwind)` (pattern-rejoin per R23 lifecycle — round-8
 *     Major 3 fix; NOT runway-vacate). The controller's GA belief
 *     clears, `ARR-TURN-BASE` re-enables for B, and B's next
 *     sequencing instruction (`TurnBase`) fires in the same cycle the
 *     belief cleared — `SupersessionRelation(TurnBase, ExtendDownwind,
 *     ABANDON)` drops the prior ExtendDownwind coordination. **NO
 *     runway-vacate clause** anywhere in this scenario's assertions
 *     (round-8 Major 3).
 *
 * **Sibling tests** (multi-aircraft + pilot-reactive taxonomy):
 *  - G0 ([LowgGoldenTest]) — single-aircraft single-aerodrome circuit
 *    training. Structural template.
 *  - G1 ([G1TwoAircraftCircuitsTest]) — single-aerodrome two-aircraft
 *    circuits=2 baseline. The `bMissionStartOffset = 2 min` shape used
 *    by this test (and the `requiredStartPoints` API used to resolve
 *    A's + B's stands) was landed in G1.
 *  - G3a-react-crosswind ([G3aPilotReactiveCrosswindTest]) — single-
 *    aircraft pilot-reactive GA, crosswind axis. **A in this test's
 *    Scenario 1 and Scenario 3 mirrors that test's single-aircraft
 *    shape verbatim** — same fixture (`Fixtures.LOWG_TWO_AIRCRAFT`
 *    instead of `LOWG`, but same wind authorship pattern), same
 *    two-transition world-state authorship discipline. The
 *    distinguishing surface is the addition of aircraft B + the
 *    controller's `ARR-EXTEND-FOR-GA` + `ARR-TURN-BASE` flow on B.
 *  - G3a-react-tailwind ([G3aPilotReactiveTailwindTest]) — pilot-
 *    reactive GA tailwind sibling. Scenario 2 mirrors that test's
 *    A-side shape; same recovery-Downwind-report gate (codex round-2
 *    radio-observable discipline; per
 *    `knowledge/best-practices/inherited-gate-semantics-2026-05-15`).
 *  - G3a-react-density-altitude ([G3aPilotReactiveDensityAltitudeTest])
 *    — apron-side reactive decline (different mission shape; no
 *    controller-side sequencing component).
 *
 * **What G3a-react-multi-aircraft distinctively pins** (per scenario):
 *
 *  - **Scenario 1 + 2 — crosswind / tailwind GA-then-extend**:
 *    - **Layer 1 (causal partial-order)**: exactly one
 *      `Report(GoingAround)` from A between the wind-shift cycle and
 *      the world-recovery cycle; at least one `ExtendDownwind(B)`
 *      strictly AFTER A's `Report(GoingAround)`; B has NO
 *      `Report(GoingAround)` of its own across the entire run (the
 *      controller-side sequencing does NOT cause B to declare a
 *      pilot-side GA — final-phase guard rejects downwind-phase
 *      aircraft).
 *    - **Layer 2 (sticky-witness regression — commitment-stage)**: A's
 *      commitment regresses from `{LandingClearanceIssued,
 *      AwaitLandedObserved}` to `AwaitDownwind` via `GA-POST-CLEAR`
 *      strictly AFTER the GoingAround transmission. Mirrors single-
 *      aircraft G3a-react-crosswind / -tailwind's regression pin
 *      shape verbatim — the pilot-side GA recognition + applier-side
 *      mission tree rewrite + controller-side commitment regression
 *      are unchanged by adding B.
 *    - **Layer 3 (kinematic non-event for B)**: B never enters base
 *      while the GA belief is active. Asserted as the absence of a
 *      `positionPoint` transition into a base-leg point for B between
 *      the GoAround transmission cycle and the belief-clear cycle.
 *      Catches a regression where `ARR-TURN-BASE`'s
 *      `Not(GoAroundInProgressOnRunway)` guard misfires (B turns base
 *      into the GA-active runway). The KDoc on `commitmentStageTransitions`
 *      / commitment-stage gates from fn-15.2's inherited-gate-semantics
 *      capture applies — radio observable (`firstControllerInstructionOf<TurnBase>`)
 *      paired with the position-point absence pin keeps the assertion
 *      anchored on the property under test.
 *
 *  - **Scenario 3 — GA-recovery / belief-clear**:
 *    - **Pattern-rejoin clear** (R23, NOT runway-vacate per round-8 Major
 *      3): A completes the go-around pattern and emits `Report(Downwind)`
 *      on the recovery circuit. The post-GoingAround pattern-rejoin
 *      `Report(Downwind)` from A clears the controller's
 *      `goAroundInProgressByRunway` belief in the same cycle the report
 *      is processed (round-13 Major 3 — `receivedAt > setAtTime`
 *      strict-inequality satisfied by the wall-clock gap between the
 *      initial GoAround set + the recovery Downwind clear).
 *    - **Concrete cancel-output, same-cycle TurnBase** (round-10 Major
 *      2): in the SAME cycle the belief clears,
 *      `ARR-TURN-BASE`'s guard re-enables for B (`Not(GoAroundInProgressOnRunway)`
 *      passes) and `TurnBase(B)` fires. The existing
 *      `SupersessionRelation(TurnBase, ExtendDownwind, ABANDON)` row
 *      drops B's prior ExtendDownwind coordination — concrete cancel
 *      via supersession.
 *    - **No runway-vacate clause** (round-8 Major 3): the test asserts
 *      that the belief-clear and TurnBase-to-B both happen BEFORE any
 *      `Report(RunwayVacated)` from A. Pinning the ordering this way
 *      proves the R23 lifecycle is pattern-rejoin-driven, not
 *      runway-vacate-driven.
 *
 * **Scenario setup precondition** (acceptance bullet from .5's spec
 * citing round-10 Major 3): A's ARR-LAND commitment exists in
 * `BeliefState` BEFORE the wind shift triggers A's GA. The `Transition-1`
 * authorship hook gate (`aircraftIsOnFinalWithLandingClearance` —
 * commitment.stage ∈ {`LandingClearanceIssued`, `AwaitLandedObserved`})
 * is itself a witness that A has an active `TOWER_ARRIVAL` commitment
 * (the stages live on `Commitment.stage`; without a commitment the gate
 * would never return true). Each scenario surfaces this explicitly by
 * checking `commitments[A].kind == TOWER_ARRIVAL && commitments[A].runway
 * == RWY_16C` at the trace cursor immediately preceding the wind-shift
 * authorship. Pins the round-10 Major 3 round-trip that ensures
 * `resolveGoAroundRunway` succeeds via the primary commitment path (not
 * the `activeRunway` fallback).
 *
 * **Inherited-gate-semantics audit** (per
 * `knowledge/best-practices/inherited-gate-semantics-2026-05-15.md` —
 * load-bearing for this test because it mirrors a sibling axis):
 *  - **Scenarios 1 + 2 inherit A's reactive-GA gates** from the single-
 *    aircraft crosswind / tailwind goldens verbatim. The
 *    `aircraftIsOnFinalWithLandingClearance` hook predicate, the
 *    `recoveryDownwindReportedFlag` radio observable (Scenario 3, used
 *    for the wind-clear hook), and the three-layer pin shape on A are
 *    structurally identical. Re-validation per the inherited-gate-
 *    semantics best-practice: the gates' semantic intent ("A is on
 *    final and post-clearance" / "A's recovery circuit reached
 *    downwind") holds on this test's axis because the **A-side flow is
 *    the same single-aircraft flow** as the siblings, just with B
 *    running alongside. No accidental axis-mismatch (B does NOT
 *    participate in the wind-GA recognition — the final-phase guard
 *    rejects downwind-phase aircraft per .4's reframing).
 *  - **B's downwind-phase + post-extend-downwind gates are NEW**. The
 *    controller-side `ARR-EXTEND-FOR-GA` rule + `ARR-TURN-BASE`'s
 *    `Not(GoAroundInProgressOnRunway)` gating + the
 *    `SupersessionRelation(TurnBase, ExtendDownwind, ABANDON)` row are
 *    all introduced by .4. The pins below (B's ExtendDownwind receipt,
 *    B's TurnBase same-cycle as belief-clear, B's no-base position-
 *    point pin during the GA-active window) are NEW gates with no
 *    sibling precedent — the radio observable
 *    (`firstControllerInstructionOf<ExtendDownwind>`,
 *    `firstControllerInstructionOf<TurnBase>`) keeps them anchored on
 *    the controller's output surface (post-state observation per
 *    `knowledge/best-practices/test-pin-discipline-2026-05-15.md`).
 *
 * **Total-order assertion** (per .5's spec, round-8 Minor 1 fix): with
 * both aircraft in the same tick, the simulation must resolve in
 * deterministic order per the engine's existing `EVENT_ORDER` contract:
 * `(time, source, seq)` via `SimEvent.seq` (NOT `(tick, aircraftId,
 * eventKind ordinal)` — that was a misstatement). The test asserts that
 * the run is fully deterministic: re-running the scenario from the same
 * seed produces the same final state (`state.aircraft[A]` + `state
 * .aircraft[B]` + `state.beliefs` are equal). This is the cheapest
 * proxy for the `EVENT_ORDER` contract holding — a regression in event
 * ordering would surface as non-determinism between two identical
 * runs.
 *
 * **Doctrinal anchors**:
 *  - **ICAO Doc 4444 17th ed. Ch 12 §12.3.4** (Aerodrome Control
 *    Phraseologies — sequencing). The controller's `ExtendDownwind`
 *    response to a same-runway GA is the doctrinal sequencing
 *    instruction. Cited via the
 *    `RegulationDatabase.ICAO4444_12_3_4` constant added in fn-28.4.
 *  - **ICAO Doc 9432 Ch.4** (EXTEND DOWNWIND phraseology).
 *  - **FAA AFH (FAA-H-8083-3C) Chapter 9** (carried from the crosswind /
 *    tailwind siblings): POH-demonstrated crosswind / AFH-advisory
 *    tailwind exceedance triggers go-around for the PIC.
 *  - **ICAO Annex 6 Part II §2.4** (PIC final authority — A declares GA
 *    without ATC permission; carried from the siblings).
 *
 * **Snapshot-bloat avoidance** (per practice-scout #1 in .5's spec): the
 * test asserts on targeted projections (`responsibilityTransitions`,
 * `commitmentStageTransitions`, `firstControllerInstructionOf<…>`,
 * `firstPilotReportOf<…>`, `positionPointTransitions`,
 * `weatherTransitions`), NOT full state-tree comparison. The total-order
 * determinism pin reads the final aircraft + beliefs subset, not the
 * entire `SimState`.
 *
 * @see G3aPilotReactiveCrosswindTest the single-aircraft crosswind
 *      sibling — Scenarios 1 + 3 reproduce its A-side wind authorship +
 *      three-layer pin shape with B added.
 * @see G3aPilotReactiveTailwindTest the single-aircraft tailwind sibling
 *      — Scenario 2 reproduces its A-side shape with B added.
 * @see G1TwoAircraftCircuitsTest the two-aircraft circuit baseline —
 *      provides the `bMissionStartOffset = 2 min` recipe + the
 *      `requiredStartPoints` API + the `LOWG_TWO_AIRCRAFT` fixture
 *      shape this test reuses.
 * @see xyz.easiersaid.twr.controller.GoAroundSequencingSpec the
 *      controller-side unit-level coverage for `ARR-EXTEND-FOR-GA`
 *      + `ARR-TURN-BASE` + the `withGoAroundInProgress` fold;
 *      this sim test is the integration-level companion that
 *      composes the pilot-side + controller-side foundations.
 */
class G3aPilotReactiveMultiAircraftTest {

    // ─────────────────────────────────────────────────────────────────
    // Scenario 1 — A's crosswind GA + controller emits ExtendDownwind(B)
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `crosswind GA on A triggers ExtendDownwind to B and B holds downwind through GA-active window`() {
        runMultiAircraftReactiveScenario(
            label = "crosswind GA + extend-downwind",
            axisShape = AxisShape.Crosswind,
            assertBeliefClearsAndTurnBaseFires = false,
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Scenario 2 — A's tailwind GA + controller emits ExtendDownwind(B)
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `tailwind GA on A triggers ExtendDownwind to B and B holds downwind through GA-active window`() {
        runMultiAircraftReactiveScenario(
            label = "tailwind GA + extend-downwind",
            axisShape = AxisShape.Tailwind,
            assertBeliefClearsAndTurnBaseFires = false,
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Scenario 3 — GA-recovery / belief-clear → TurnBase(B) same-cycle
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `crosswind GA on A then pattern-rejoin Report Downwind clears belief and TurnBase to B fires same-cycle`() {
        runMultiAircraftReactiveScenario(
            label = "crosswind GA + belief-clear + TurnBase(B) cancel",
            axisShape = AxisShape.Crosswind,
            assertBeliefClearsAndTurnBaseFires = true,
        )
    }

    // ─────────────────────────────────────────────────────────────────
    // Shared scenario runner
    // ─────────────────────────────────────────────────────────────────

    /**
     * Recognition axis discriminator. Selects the wind authorship shape
     * (direction + speed + advisory threshold reference). The applier-
     * level path (`applyCrosswindGoAround` vs `applyTailwindGoAround`)
     * is dispatched by `derivePilotEvent`'s branch order; the test's
     * three-layer A-side pins are axis-agnostic (the regression / GA
     * transmission / recovery shapes are the same).
     */
    private enum class AxisShape {
        Crosswind, Tailwind,
    }

    /**
     * Runs one of the three scenarios. Axis selects the wind authorship;
     * `assertBeliefClearsAndTurnBaseFires` toggles Scenario 3's recovery
     * pins (belief-clear via pattern-rejoin + same-cycle TurnBase to B
     * + no-runway-vacate ordering). Scenarios 1 + 2 share the GA + extend-
     * downwind pins; Scenario 3 adds the recovery-side pins on top.
     *
     * The two-aircraft fixture, the 2-minute mission-start offset on B,
     * the three-layer A-side discipline, and the wind authorship hook are
     * all common. Only the wind direction + speed values change between
     * axes.
     */
    private fun runMultiAircraftReactiveScenario(
        label: String,
        axisShape: AxisShape,
        assertBeliefClearsAndTurnBaseFires: Boolean,
    ) {
        // ── World + controllers via the shared multi-aircraft fixture ───────
        val fixture = Fixtures.LOWG_TWO_AIRCRAFT
        val loaded = fixture.load().getOrElse {
            fail("LOWG_TWO_AIRCRAFT fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val rwy = RunwayId("16C")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) {
            "GROUND missing from LOWG_TWO_AIRCRAFT fixture"
        }
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) {
            "TOWER missing from LOWG_TWO_AIRCRAFT fixture"
        }

        val runwayHeading = checkNotNull(rwy.headingDegreesMagnetic()) {
            "Runway $rwy did not parse to a magnetic heading — fixture/test mismatch"
        }
        // Per the single-aircraft siblings: pure-crosswind direction is
        // (runwayHeading + 90) % 360 mod-clamped to [1..360]; pure-tailwind
        // direction is (runwayHeading + 180) % 360 mod-clamped to [1..360].
        // For 16C (160°M) the values are 250°M (crosswind) and 340°M
        // (tailwind). The clamp is defensive — neither 250 nor 340 sits
        // on the 0/360 boundary, but the helper handles future runway
        // headings that would land on 0.
        val pureCrosswindDirection: Int = ((runwayHeading + 90) % 360)
            .let { if (it == 0) 360 else it }
        val pureTailwindDirection: Int = ((runwayHeading + 180) % 360)
            .let { if (it == 0) 360 else it }

        // ── Two C172s at LOWG: A on circuit (will GA), B on circuit ─────────
        // A flies a single full-stop circuit so the recognition predicate
        // fires on circuit 1's final — same shape as the single-aircraft
        // siblings. B flies a single full-stop circuit too; B's mission
        // does NOT need a GA outcome — B is purely sequenced by ATC. The
        // 2-minute `bMissionStartOffset` (from G1's recipe) makes B reach
        // downwind while A is on final under landing clearance.
        val aircraftAId = AircraftId("OE-ABC")
        val aircraftBId = AircraftId("OE-DEF")
        val now = SimTime.ZERO
        val startPoints = fixture.requiredStartPoints()
        val standPointA = startPoints.getValue(aircraftAId)
        val standPointB = startPoints.getValue(aircraftBId)

        val singleFullStop = HighLevelGoal.CircuitTraining(
            outcomes = listOf(CircuitOutcome.FullStop),
        )
        val missionA = createMission(
            goal = singleFullStop,
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans[aircraftAId]
                ?: fail("LOWG_TWO_AIRCRAFT fixture missing flight plan for $aircraftAId"),
        )
        val missionB = createMission(
            goal = singleFullStop,
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans[aircraftBId]
                ?: fail("LOWG_TWO_AIRCRAFT fixture missing flight plan for $aircraftBId"),
        )
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

        // ── Initial weather = 10 kt headwind from runway heading ─────────────
        // Zero crosswind + zero tailwind component initially — recognition
        // satisfied only after the world hook authors the shift. Override
        // the fixture's default 160°@8 to a precise 16C headwind (160°@10)
        // so the initial wind components are exactly zero (sibling
        // discipline).
        val initialWeather = WeatherObservation(
            wind = WindReport.Available(
                Wind.unsafe(directionDegrees = runwayHeading, speedKnots = 10),
            ),
            qnh = null,
            visibility = null,
        )

        // ── Build SimState through the smart constructor ────────────────────
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraftA, aircraftB),
            controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(lowg to initialWeather),
        ).getOrElse { error("SimState.initial rejected the LOWG_TWO_AIRCRAFT fixture: $it") }

        // ── ATIS + drive ────────────────────────────────────────────────────
        // 60 sim minutes ceiling. Scenarios 1 + 2 finish A's GA + B's
        // extend-downwind quickly (well under 30 sim min). Scenario 3
        // additionally requires A to complete the recovery pattern and
        // emit its recovery `Report(Downwind)` before the test can pin
        // the belief-clear / TurnBase-same-cycle / no-runway-vacate
        // ordering — that adds ~10-15 sim min on top.
        val until = SimTime.ZERO + SimDuration.ofMillis(60 * 60 * 1000L)
        val lowgAtis = Atis(
            letter = 'A',
            aerodrome = lowg,
            configuration = RunwayConfiguration(
                arrivals = listOf(rwy),
                departures = listOf(rwy),
            ),
            wind = Wind.unsafe(runwayHeading, 10),
            qnh = null,
            visibility = null,
            generatedAt = now,
        )
        // B's mission-start offset (NOT filing offset, per G1's recipe):
        // delay B's first PilotDecisionTick by 2 sim-minutes so B departs
        // behind A and reaches downwind while A is on final under
        // clearance. The 2-minute value is empirical from G1; the load-
        // bearing pin is the forced-conflict invariant (`ExtendDownwind(B)`
        // observed after `Report(GoingAround)` from A), not the offset
        // value itself.
        val bMissionStartOffset = SimDuration.ofMillis(2 * 60 * 1000L)
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = lowgAtis),
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftAId),
            SimEvent.PilotDecisionTick(time = now + bMissionStartOffset, aircraftId = aircraftBId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )

        // ── Wind authorship hook ────────────────────────────────────────────
        //
        // Same two-transition shape as the single-aircraft siblings:
        //  - Transition 1 fires when A is on final under landing clearance
        //    (post-clearance window per the single-aircraft spec).
        //  - Transition 2 fires when A's recovery circuit has emitted
        //    `Report(Downwind)` after the GA — the load-bearing radio
        //    observable per `inherited-gate-semantics-2026-05-15` (the
        //    fn-15.2 codex round-2/3 closure on the off-final-vs-downwind-
        //    report semantic mismatch). Pure radio observation; no peek
        //    into pilot state or commitment stage.
        var windAuthored = false
        var windClearedToLimit = false
        val windAuthoredAt = arrayOf<SimTime?>(null)
        val windClearedAt = arrayOf<SimTime?>(null)
        val goingAroundTransmittedFlag = arrayOf(false)
        val recoveryDownwindReportedFlag = arrayOf(false)
        val onAfterEvent: (SimEvent, SimState) -> SimState = { ev, st ->
            // Track A's `Report(GoingAround)` + the FIRST post-GA
            // `Report(Downwind)` from A via the event stream. The radio
            // surface is the load-bearing observable per fn-15.2's
            // codex round-2/3 fix — commitment-stage gates fire too
            // early on the GA climbout (round-13 Major 3 documents
            // this for the controller-side belief lifecycle; the same
            // hazard applies to test-side gates).
            if (ev is SimEvent.TransmissionStart) {
                val tx = ev.transmission
                val speakerAc = (tx.speaker as? SpeakerRef.Pilot)?.aircraftId
                val pilotTransmission =
                    (tx.utterance as? Utterance.FromPilot)?.transmission
                val report = pilotTransmission as? xyz.easiersaid.twr.protocol.Report
                if (speakerAc == aircraftAId && report != null) {
                    if (!goingAroundTransmittedFlag[0] &&
                        report.events.any { it is ReportEvent.GoingAround }
                    ) {
                        goingAroundTransmittedFlag[0] = true
                    }
                    // The FIRST post-GoingAround Downwind report from A is
                    // the recovery-circuit pattern-rejoin. Per fn-15.2:
                    // strictly tighter than any commitment-stage proxy,
                    // because the controller's
                    // `reconcileAwaitDownwind` advances stage on
                    // transient OnBase/OnFinal observations during the GA
                    // climbout. The recovery Downwind transmission only
                    // fires when A has physically re-entered the recovery
                    // pattern.
                    if (goingAroundTransmittedFlag[0] &&
                        !recoveryDownwindReportedFlag[0] &&
                        report.events.any { it is ReportEvent.Downwind }
                    ) {
                        recoveryDownwindReportedFlag[0] = true
                    }
                }
            }

            when {
                // Transition 2 — wind returns within limits (one-shot).
                // Gated on the recovery-circuit Downwind report from A
                // per fn-15.2's gate-semantics fix. Without this gate, a
                // phase-only gate (`A is off-final`) fires immediately
                // post-GA-climbout and the exceedance window is
                // vacuously narrow.
                !windClearedToLimit && recoveryDownwindReportedFlag[0] -> {
                    windClearedToLimit = true
                    windClearedAt[0] = st.now
                    authorWeather(st, lowg, initialWeather)
                }
                // Transition 1 — wind crosses past limit (one-shot).
                // Gated on A being on final under landing clearance —
                // mirrors the single-aircraft siblings' post-clearance
                // window pin (`T_obs > T_ClearedToLand`).
                !windAuthored &&
                    aircraftIsOnFinalWithLandingClearance(st, aircraftAId, tower.id) -> {
                    windAuthored = true
                    windAuthoredAt[0] = st.now
                    val (windDir, windSpeed) = when (axisShape) {
                        AxisShape.Crosswind -> pureCrosswindDirection to 20
                        AxisShape.Tailwind -> pureTailwindDirection to 15
                    }
                    val perturbedWeather = WeatherObservation(
                        wind = WindReport.Available(
                            Wind.unsafe(directionDegrees = windDir, speedKnots = windSpeed),
                        ),
                        qnh = null,
                        visibility = null,
                    )
                    authorWeather(st, lowg, perturbedWeather)
                }
                else -> st
            }
        }

        val (finalState, records, trace) = runUntilWithStateTrace(
            initialState = initialState,
            initialEvents = initialEvents,
            untilTime = until,
            onAfterEvent = onAfterEvent,
        )

        // ── Diagnostic preamble ─────────────────────────────────────────────
        val journeyA = finalState.formatJourney(aircraftAId, records)
        val journeyB = finalState.formatJourney(aircraftBId, records)
        val journey = "[$label]\n" +
            "── Aircraft A ($aircraftAId) ──\n$journeyA\n\n" +
            "── Aircraft B ($aircraftBId) ──\n$journeyB"
        println(journey)

        println()
        println("─── G3a-react-multi [$label] trace summary ───")
        println("Runway heading (16C):       ${runwayHeading}°M")
        println("Crosswind direction:        ${pureCrosswindDirection}°M")
        println("Tailwind direction:         ${pureTailwindDirection}°M")
        println("Axis under test:            $axisShape")
        println("Wind authored at:           ${windAuthoredAt[0]?.millis ?: "<NEVER>"}ms")
        println("Wind cleared at:            ${windClearedAt[0]?.millis ?: "<NEVER>"}ms")
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
            println("Commitment stage transitions (tower):")
            for (t in trace.commitmentStageTransitions(acId, tower.id)) {
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
            println("Phase transitions:")
            for (t in trace.transitionsOf { st -> st.aircraft[acId]?.phase }) {
                println("  [${t.after.time.millis}ms] ${t.from} → ${t.to}")
            }
        }
        println("─── end G3a-react-multi [$label] trace summary ───")
        println()

        // ── One-shot authorship pins (defensive) ────────────────────────────
        // Mirrors the single-aircraft siblings: if the hook never fired,
        // the rest of the test's pins are non-meaningful.
        check(windAuthored) {
            "[$label] World-authorship hook never fired transition 1 — " +
                "`aircraftIsOnFinalWithLandingClearance` never returned true for $aircraftAId. " +
                "Either A never reached phase=Final, or ClearedToLand was never issued for " +
                "circuit 1.\n$journey"
        }
        // Scenarios 1+2 do NOT require the wind to clear (their pins are
        // about A's GA + B's ExtendDownwind, both pre-recovery). Scenario
        // 3 requires the recovery `Report(Downwind)` from A which is also
        // what triggers wind-clear, so `windClearedToLimit` is a free
        // necessary condition there.
        if (assertBeliefClearsAndTurnBaseFires) {
            check(windClearedToLimit) {
                "[$label] World-authorship hook never fired transition 2 — A never " +
                    "transmitted a post-GA `Report(Downwind)`. Without this transmission, " +
                    "the controller's GA belief cannot clear via the pattern-rejoin path " +
                    "(60s timeout would still clear it, but the radio observable is the " +
                    "primary clear path per R23 lifecycle).\n$journey"
            }
        }

        // ── World-weather transition pin ────────────────────────────────────
        //
        // Scenarios 1 + 2: exactly ONE transition (the perturbation). The
        // wind-clear transition is conditional on A's recovery Downwind
        // report which may or may not happen in the test's time window
        // for these scenarios (we only assert on the GA + extend-downwind
        // mechanics).
        //
        // Scenario 3: exactly TWO transitions (perturbation + clear).
        val weatherTrans = trace.weatherTransitions(lowg)
        if (assertBeliefClearsAndTurnBaseFires) {
            check(weatherTrans.size == 2) {
                "[$label] Expected exactly two transitions in world.aerodromes[$lowg].weather " +
                    "(wind authored + cleared), observed ${weatherTrans.size}.\n$journey"
            }
        } else {
            check(weatherTrans.size >= 1) {
                "[$label] Expected at least one transition in world.aerodromes[$lowg].weather " +
                    "(wind authored), observed ${weatherTrans.size}.\n$journey"
            }
        }
        val weatherShiftMs = weatherTrans[0].after.time.millis

        // ── Layer 1 — Report(GoingAround) from A; B has none ────────────────
        //
        // Exactly one `Report(GoingAround)` from A (matching the single-
        // aircraft siblings' hysteresis pin) AND zero `Report(GoingAround)`
        // from B across the run (the multi-aircraft reframing's load-
        // bearing claim — B's downwind-phase aircraft never recognises
        // wind GA because the final-phase guard rejects).
        val aGoingAroundRecords = records.filter { rec ->
            val speakerAc = (rec.speaker as? SpeakerRef.Pilot)?.aircraftId
            if (speakerAc != aircraftAId) return@filter false
            val pilotTransmission = (rec.utterance as? Utterance.FromPilot)?.transmission
            val report = pilotTransmission as? xyz.easiersaid.twr.protocol.Report
                ?: return@filter false
            report.events.any { it is ReportEvent.GoingAround }
        }
        check(aGoingAroundRecords.size == 1) {
            "[$label] Expected exactly one Report(GoingAround) for $aircraftAId, observed " +
                "${aGoingAroundRecords.size}. More than one indicates hysteresis regression; " +
                "zero indicates the recognition didn't fire.\n$journey"
        }
        val aGoingAroundMs = aGoingAroundRecords.single().time.millis

        val bGoingAroundRecords = records.filter { rec ->
            val speakerAc = (rec.speaker as? SpeakerRef.Pilot)?.aircraftId
            if (speakerAc != aircraftBId) return@filter false
            val pilotTransmission = (rec.utterance as? Utterance.FromPilot)?.transmission
            val report = pilotTransmission as? xyz.easiersaid.twr.protocol.Report
                ?: return@filter false
            report.events.any { it is ReportEvent.GoingAround }
        }
        check(bGoingAroundRecords.isEmpty()) {
            "[$label] Multi-aircraft reframing pin: B ($aircraftBId) must NEVER declare a " +
                "pilot-side GA — B is on downwind during the wind-shift cycle, and " +
                "`derivePilotEvent`'s final-phase guard rejects downwind-phase aircraft. " +
                "Got ${bGoingAroundRecords.size} Report(GoingAround) from B at: " +
                bGoingAroundRecords.joinToString { "${it.time.millis}ms" } +
                ".\n$journey"
        }

        // ── Scenario setup precondition (round-10 Major 3) ──────────────────
        //
        // A's TOWER_ARRIVAL commitment with non-null `runway` exists in
        // `BeliefState` at the trace cursor immediately preceding the
        // wind-shift authorship. This ensures `resolveGoAroundRunway`
        // succeeds via the primary commitment path (not the
        // `activeRunway` fallback). The wind-shift hook itself gates on
        // commitment.stage ∈ {LandingClearanceIssued, AwaitLandedObserved}
        // — both TOWER_ARRIVAL post-clearance stages — so the commitment
        // is structurally present. We additionally surface it here for
        // round-10 Major 3 documentation.
        val preShiftCursor = trace.firstWhere { st ->
            st.now.millis >= weatherShiftMs
        }.getOrElse {
            fail("[$label] Trace has no cursor at-or-after weatherShiftMs ${weatherShiftMs}ms — " +
                "the run terminated before the wind-shift authorship took effect.\n$journey")
        }
        val preShiftCommitment = preShiftCursor.state.beliefs[tower.id]
            ?.commitments?.get(aircraftAId)
            ?: fail(
                "[$label] Round-10 Major 3 precondition: A's commitment must exist in " +
                    "BeliefState at the wind-shift cursor (${weatherShiftMs}ms) so " +
                    "`resolveGoAroundRunway` succeeds via the primary commitment path. " +
                    "BeliefState.commitments[$aircraftAId] = null.\n$journey",
            )
        check(preShiftCommitment.kind == CommitmentKind.TOWER_ARRIVAL) {
            "[$label] Round-10 Major 3 precondition: A's commitment kind at wind-shift " +
                "must be TOWER_ARRIVAL (the runway the controller committed A to land on " +
                "is the one `resolveGoAroundRunway` reads via commitment.runway). Got " +
                "kind=${preShiftCommitment.kind}.\n$journey"
        }
        check(preShiftCommitment.runway == rwy) {
            "[$label] Round-10 Major 3 precondition: A's TOWER_ARRIVAL commitment must " +
                "have runway = $rwy at the wind-shift cursor. Got runway=" +
                "${preShiftCommitment.runway}.\n$journey"
        }

        // ── Layer 2 — Sticky-witness regression on A (GA-POST-CLEAR) ────────
        //
        // Mirrors the single-aircraft siblings: A's commitment regresses
        // from one of {LandingClearanceIssued, AwaitLandedObserved} to
        // AwaitDownwind via `GA-POST-CLEAR`. The interrupt fires STRICTLY
        // AFTER A's `Report(GoingAround)` (radio-delivery prerequisite —
        // the tower receives the GA event via `GoAroundEvent`, and the
        // interrupt consumes that event on a subsequent cycle).
        val aStageTransitions = trace.commitmentStageTransitions(aircraftAId, tower.id)
        val postClearStages = setOf<TowerArrivalStage>(
            TowerArrivalStage.LandingClearanceIssued,
            TowerArrivalStage.AwaitLandedObserved,
        )
        val aRegressions = aStageTransitions.filter { t ->
            val from = t.from.fold({ null }, { it as? TowerArrivalStage }) ?: return@filter false
            val to = t.to.fold({ null }, { it as? TowerArrivalStage }) ?: return@filter false
            from in postClearStages && to == TowerArrivalStage.AwaitDownwind
        }
        check(aRegressions.size == 1) {
            "[$label] Expected exactly one stage regression " +
                "{LandingClearanceIssued | AwaitLandedObserved} → AwaitDownwind for A on the " +
                "pilot-reactive GA, observed ${aRegressions.size}.\n$journey"
        }
        val aRegression = aRegressions.single()
        check(aRegression.after.time.millis > aGoingAroundMs) {
            "[$label] Radio-delivery prerequisite for A: stage regression at " +
                "${aRegression.after.time.millis}ms must fire strictly AFTER " +
                "Report(GoingAround) at ${aGoingAroundMs}ms.\n$journey"
        }

        // ── Controller-side: ExtendDownwind(B) fires after A's GA ────────────
        //
        // The `ARR-EXTEND-FOR-GA` rule emits `ExtendDownwind` to B (the
        // trailing downwind aircraft on the GA-active runway). At least
        // one `ExtendDownwind(B)` must exist strictly AFTER A's
        // `Report(GoingAround)` — the rule's guard requires
        // `GoAroundInProgressOnRunway`, which is set only after the GA
        // report has been received and folded into BeliefState. A
        // pre-existing `ExtendDownwind(B)` from the standard spacing-
        // driven `ARR-EXTEND` rule is allowed, but a fresh
        // `ExtendDownwind(B)` AFTER the GA must appear for the cancel-
        // via-supersession chain in Scenario 3 to work.
        val bExtendDownwindRecords = records.filter { rec ->
            val out = (rec.utterance as? Utterance.FromController)?.output
                as? ControllerOutput.Instruct ?: return@filter false
            val instr = (out.dispatch as? Dispatch.Direct)?.instruction
                ?: (out.dispatch as? Dispatch.Conditional)?.instruction
                ?: return@filter false
            out.target == aircraftBId && instr is ExtendDownwind
        }
        val bExtendDownwindAfterGa = bExtendDownwindRecords.filter {
            it.time.millis > aGoingAroundMs
        }
        check(bExtendDownwindAfterGa.isNotEmpty()) {
            "[$label] Expected at least one ExtendDownwind(B) strictly AFTER A's " +
                "Report(GoingAround) at ${aGoingAroundMs}ms — `ARR-EXTEND-FOR-GA` fires " +
                "when (a) GA belief active on B's runway and (b) B observed on downwind. " +
                "All ExtendDownwind(B) records: " +
                bExtendDownwindRecords.joinToString { "${it.time.millis}ms" } +
                ".\n$journey"
        }
        val bExtendDownwindAfterGaMs = bExtendDownwindAfterGa.first().time.millis

        // ── Layer 3 — Kinematic non-event for B during GA-active window ─────
        //
        // While the GA belief is active (between A's `Report(GoingAround)`
        // and either A's recovery Downwind report or the 60s timeout), B
        // MUST NOT enter a base-leg position-point. `ARR-TURN-BASE`'s
        // `Not(GoAroundInProgressOnRunway)` guard prevents the TurnBase
        // instruction from firing for B in this window. A regression that
        // misfires the guard would let B turn base into the GA-active
        // runway — a doctrinal violation.
        //
        // We pin this on the radio observable instead of a position-point
        // walk: no `TurnBase(B)` instruction emitted between
        // `Report(GoingAround)` and either the recovery Downwind report
        // (Scenario 3) or end-of-window (Scenarios 1+2 use the
        // bExtendDownwindAfterGaMs cursor as a conservative inner bound —
        // the rule fires once-per-cycle while the belief is active, so
        // the cursor sits inside the GA window). The radio observable is
        // the load-bearing post-state per
        // `knowledge/best-practices/test-pin-discipline-2026-05-15` (post-
        // state-vs-intent + radio-observable preference).
        val bTurnBaseRecords = records.filter { rec ->
            val out = (rec.utterance as? Utterance.FromController)?.output
                as? ControllerOutput.Instruct ?: return@filter false
            val instr = (out.dispatch as? Dispatch.Direct)?.instruction
                ?: (out.dispatch as? Dispatch.Conditional)?.instruction
                ?: return@filter false
            out.target == aircraftBId && instr is TurnBase
        }
        val gaWindowUpper = if (assertBeliefClearsAndTurnBaseFires) {
            // Scenario 3 — strictly before the recovery Downwind report
            // (which is what clears the belief).
            checkNotNull(windClearedAt[0]) {
                "[$label] Scenario 3 requires windClearedAt to be set (set by recovery " +
                    "Downwind report).\n$journey"
            }.millis
        } else {
            // Scenarios 1+2 — use the ExtendDownwind(B) emission as the
            // inner cursor; the GA belief is still active at that point
            // (the rule's guard is `GoAroundInProgressOnRunway`).
            bExtendDownwindAfterGaMs
        }
        val bTurnBaseInWindow = bTurnBaseRecords.filter { rec ->
            val t = rec.time.millis
            t > aGoingAroundMs && t < gaWindowUpper
        }
        check(bTurnBaseInWindow.isEmpty()) {
            "[$label] Layer 3 kinematic non-event for B: expected NO TurnBase(B) " +
                "instructions between A's Report(GoingAround) at ${aGoingAroundMs}ms and " +
                "the GA-window upper bound at ${gaWindowUpper}ms — `ARR-TURN-BASE`'s " +
                "`Not(GoAroundInProgressOnRunway)` guard must suppress TurnBase while the " +
                "belief is active. Got ${bTurnBaseInWindow.size} TurnBase(B) emissions in " +
                "the GA window at: " + bTurnBaseInWindow.joinToString { "${it.time.millis}ms" } +
                ".\n$journey"
        }

        // ── Scenario 3 — recovery-side pins ─────────────────────────────────
        //
        // Belief-clear via pattern-rejoin Report(Downwind) from A; same-
        // cycle TurnBase(B) emission; no-runway-vacate precedence
        // (round-8 Major 3 — the lifecycle is pattern-rejoin-driven, NOT
        // runway-vacate-driven).
        if (assertBeliefClearsAndTurnBaseFires) {
            // Recovery Downwind report from A (the pattern-rejoin clear).
            val aRecoveryDownwindRecord = records.firstOrNull { rec ->
                val speakerAc = (rec.speaker as? SpeakerRef.Pilot)?.aircraftId
                if (speakerAc != aircraftAId) return@firstOrNull false
                if (rec.time.millis <= aGoingAroundMs) return@firstOrNull false
                val pilotTransmission = (rec.utterance as? Utterance.FromPilot)?.transmission
                val report = pilotTransmission as? xyz.easiersaid.twr.protocol.Report
                    ?: return@firstOrNull false
                report.events.any { it is ReportEvent.Downwind }
            } ?: fail(
                "[$label] Scenario 3 requires A to emit a post-GoingAround Report(Downwind) " +
                    "(the recovery-circuit pattern-rejoin per R23 lifecycle). None observed " +
                    "after ${aGoingAroundMs}ms.\n$journey"
            )
            val aRecoveryDownwindMs = aRecoveryDownwindRecord.time.millis

            // First TurnBase(B) strictly AFTER A's recovery Downwind. The
            // controller's GA belief clears in the cycle that processes
            // the recovery Downwind report (round-13 Major 3 — `receivedAt
            // > setAtTime` strict-inequality is satisfied by the wall-
            // clock gap). `ARR-TURN-BASE` re-enables for B in that same
            // cycle (the rule's guard set drops the GA-active block) and
            // emits `TurnBase(B)`. The existing
            // `SupersessionRelation(TurnBase, ExtendDownwind, ABANDON)`
            // row drops B's prior ExtendDownwind coordination.
            val bTurnBaseAfterRecovery = bTurnBaseRecords.firstOrNull {
                it.time.millis >= aRecoveryDownwindMs
            } ?: fail(
                "[$label] Scenario 3 (round-10 Major 2 concrete cancel-output): expected at " +
                    "least one TurnBase(B) AT-OR-AFTER A's recovery Report(Downwind) at " +
                    "${aRecoveryDownwindMs}ms — `ARR-TURN-BASE` should re-enable for B in the " +
                    "same cycle the GA belief clears. All TurnBase(B) records: " +
                    bTurnBaseRecords.joinToString { "${it.time.millis}ms" } + ".\n$journey",
            )
            val bTurnBaseAfterRecoveryMs = bTurnBaseAfterRecovery.time.millis

            // No-runway-vacate precedence (round-8 Major 3): the belief-
            // clear + TurnBase(B) chain happens BEFORE any
            // `Report(RunwayVacated)` from A. The R23 lifecycle is
            // pattern-rejoin-driven; runway-vacate is NOT a clear path.
            // If A vacates first (e.g. on a recovery full-stop landing),
            // that ordering would still be consistent with this pin —
            // the load-bearing claim is "belief-clear happens via
            // Report(Downwind), not via vacate". To pin that claim
            // observably, we assert the TurnBase(B) precedes any
            // RunwayVacated(A) — if a hypothetical regression made the
            // belief clear on vacate instead of pattern-rejoin, the
            // TurnBase(B) would slip past the vacate and this assertion
            // would catch it.
            val aRunwayVacatedRecords = records.filter { rec ->
                val speakerAc = (rec.speaker as? SpeakerRef.Pilot)?.aircraftId
                if (speakerAc != aircraftAId) return@filter false
                val pilotTransmission = (rec.utterance as? Utterance.FromPilot)?.transmission
                val report = pilotTransmission as? xyz.easiersaid.twr.protocol.Report
                    ?: return@filter false
                report.events.any { it is ReportEvent.RunwayVacated }
            }
            val aFirstRunwayVacatedMs = aRunwayVacatedRecords.firstOrNull()?.time?.millis
            if (aFirstRunwayVacatedMs != null) {
                check(bTurnBaseAfterRecoveryMs < aFirstRunwayVacatedMs) {
                    "[$label] Round-8 Major 3 (no runway-vacate clause): TurnBase(B) at " +
                        "${bTurnBaseAfterRecoveryMs}ms must fire BEFORE A's first " +
                        "Report(RunwayVacated) at ${aFirstRunwayVacatedMs}ms. The R23 " +
                        "lifecycle clears the GA belief on pattern-rejoin (`Report(Downwind)`), " +
                        "NOT on runway-vacate. If TurnBase(B) only fires after A's vacate, " +
                        "the clear path is the wrong one.\n$journey"
                }
            }

            // Sticky-witness pin on the belief itself: after A's recovery
            // Downwind report has been processed, `BeliefState
            // .goAroundInProgressByRunway[rwy]` must be absent for the
            // tower. Reads the trace cursor at-or-after the recovery
            // Downwind transmission record. (We use `aRecoveryDownwindMs
            // + 1ms` to ensure the cursor sits strictly AFTER the cycle
            // that consumed the message — the same-cycle clear is
            // satisfied by `receivedAt > setAtTime`, which strictly
            // postdates the GA's setAtTime by tens of seconds.)
            val postRecoveryCursor = trace.firstWhere { st ->
                st.now.millis > aRecoveryDownwindMs
            }.getOrElse {
                fail("[$label] Trace has no cursor strictly after recovery Downwind at " +
                    "${aRecoveryDownwindMs}ms — the run terminated before the belief-clear " +
                    "cycle.\n$journey")
            }
            val postRecoveryBeliefs = postRecoveryCursor.state.beliefs[tower.id]
                ?: fail(
                    "[$label] Tower beliefs missing at post-recovery cursor — controller " +
                        "pipeline regression.\n$journey",
                )
            check(rwy !in postRecoveryBeliefs.goAroundInProgressByRunway) {
                "[$label] Post-recovery belief-clear pin: tower's " +
                    "BeliefState.goAroundInProgressByRunway must NOT contain an entry for " +
                    "runway $rwy after A's recovery Report(Downwind) at " +
                    "${aRecoveryDownwindMs}ms (cycle observed at " +
                    "${postRecoveryCursor.time.millis}ms). The pattern-rejoin transmission " +
                    "should have cleared the belief via `withGoAroundInProgress`. Got " +
                    "entries: ${postRecoveryBeliefs.goAroundInProgressByRunway}.\n$journey"
            }
        }

        // ── Total-order determinism (round-8 Minor 1 — EVENT_ORDER) ──────────
        //
        // Cheapest proxy for the engine's `(time, source, seq)`
        // `EVENT_ORDER` contract: re-run the scenario from the same seed
        // and assert the final A-and-B aircraft state + tower belief
        // state are equal. Any non-deterministic event ordering would
        // surface as a difference between the two runs. The pin compares
        // narrow projections (post-state-vs-intent + targeted-projection
        // discipline) — NOT full state-tree equality (which the snapshot-
        // bloat-avoidance note in .5's spec warns against).
        val (finalStateRerun, _, _) = runUntilWithStateTrace(
            initialState = initialState,
            initialEvents = initialEvents,
            untilTime = until,
            onAfterEvent = onAfterEventDeterminismRerun(
                lowg = lowg,
                aircraftAId = aircraftAId,
                towerId = tower.id,
                pureCrosswindDirection = pureCrosswindDirection,
                pureTailwindDirection = pureTailwindDirection,
                axisShape = axisShape,
                initialWeather = initialWeather,
            ),
        )
        check(finalState.aircraft[aircraftAId] == finalStateRerun.aircraft[aircraftAId]) {
            "[$label] Determinism pin (EVENT_ORDER round-8 Minor 1): final aircraft state " +
                "for A differs between two runs from the same seed. Non-deterministic event " +
                "ordering regression.\n$journey"
        }
        check(finalState.aircraft[aircraftBId] == finalStateRerun.aircraft[aircraftBId]) {
            "[$label] Determinism pin (EVENT_ORDER round-8 Minor 1): final aircraft state " +
                "for B differs between two runs from the same seed.\n$journey"
        }
        check(finalState.beliefs[tower.id]?.goAroundInProgressByRunway ==
            finalStateRerun.beliefs[tower.id]?.goAroundInProgressByRunway) {
            "[$label] Determinism pin (EVENT_ORDER round-8 Minor 1): final " +
                "goAroundInProgressByRunway slice differs between two runs from the same " +
                "seed — non-deterministic fold ordering.\n$journey"
        }
    }

    /**
     * Build a wind-authorship hook identical in shape to the primary
     * scenario's hook, used for the determinism re-run. The hook's state
     * (var flags, arrayOf-of-flag slots) is local to this builder so the
     * re-run does not share mutable state with the primary run.
     *
     * Identity of authorship logic across the two runs is the load-bearing
     * property the determinism pin asserts against: same seed + same
     * initial events + same authorship hook ⇒ same final state.
     */
    private fun onAfterEventDeterminismRerun(
        lowg: AerodromeId,
        aircraftAId: AircraftId,
        towerId: ControllerId,
        pureCrosswindDirection: Int,
        pureTailwindDirection: Int,
        axisShape: AxisShape,
        initialWeather: WeatherObservation,
    ): (SimEvent, SimState) -> SimState {
        var windAuthored = false
        var windClearedToLimit = false
        val goingAroundTransmittedFlag = arrayOf(false)
        val recoveryDownwindReportedFlag = arrayOf(false)
        return { ev, st ->
            if (ev is SimEvent.TransmissionStart) {
                val tx = ev.transmission
                val speakerAc = (tx.speaker as? SpeakerRef.Pilot)?.aircraftId
                val pilotTransmission =
                    (tx.utterance as? Utterance.FromPilot)?.transmission
                val report = pilotTransmission as? xyz.easiersaid.twr.protocol.Report
                if (speakerAc == aircraftAId && report != null) {
                    if (!goingAroundTransmittedFlag[0] &&
                        report.events.any { it is ReportEvent.GoingAround }
                    ) {
                        goingAroundTransmittedFlag[0] = true
                    }
                    if (goingAroundTransmittedFlag[0] &&
                        !recoveryDownwindReportedFlag[0] &&
                        report.events.any { it is ReportEvent.Downwind }
                    ) {
                        recoveryDownwindReportedFlag[0] = true
                    }
                }
            }
            when {
                !windClearedToLimit && recoveryDownwindReportedFlag[0] -> {
                    windClearedToLimit = true
                    authorWeather(st, lowg, initialWeather)
                }
                !windAuthored &&
                    aircraftIsOnFinalWithLandingClearance(st, aircraftAId, towerId) -> {
                    windAuthored = true
                    val (windDir, windSpeed) = when (axisShape) {
                        AxisShape.Crosswind -> pureCrosswindDirection to 20
                        AxisShape.Tailwind -> pureTailwindDirection to 15
                    }
                    val perturbedWeather = WeatherObservation(
                        wind = WindReport.Available(
                            Wind.unsafe(directionDegrees = windDir, speedKnots = windSpeed),
                        ),
                        qnh = null,
                        visibility = null,
                    )
                    authorWeather(st, lowg, perturbedWeather)
                }
                else -> st
            }
        }
    }

    /**
     * Predicate for transition-1 authorship (mirrors single-aircraft
     * siblings): the aircraft is on `phase=Final` AND the tower's
     * commitment for the aircraft sits in a post-clearance stage
     * (`LandingClearanceIssued` or `AwaitLandedObserved`). Same shape
     * as G3aPilotReactiveCrosswindTest's hook predicate.
     */
    private fun aircraftIsOnFinalWithLandingClearance(
        st: SimState,
        aircraft: AircraftId,
        towerId: ControllerId,
    ): Boolean {
        val ac = st.aircraft[aircraft] ?: return false
        if (ac.phase != PilotPhase.Final) return false
        val commitment = st.beliefs[towerId]?.commitments?.get(aircraft) ?: return false
        val stage = commitment.stage
        return stage == TowerArrivalStage.LandingClearanceIssued ||
            stage == TowerArrivalStage.AwaitLandedObserved
    }

    /**
     * Pure world-state mutation: replace
     * `state.world.aerodromes[aerodromeId].weather` with [weather]. Per
     * fn-14.2 R12 the world-only test trigger discipline writes
     * directly to the world-state entity (NOT to controller beliefs
     * and NOT to `PilotInput`); the sim's per-cycle `buildPilotInput`
     * projection picks up the new wind on the next pilot decision tick.
     *
     * fn-16 (R8): migrated from the deleted `state.weatherByAerodrome`
     * flat map to [xyz.easiersaid.twr.core.world.Aerodrome.weather] via
     * the [xyz.easiersaid.twr.core.world.updateAerodrome] lens helper.
     */
    private fun authorWeather(
        st: SimState,
        aerodromeId: AerodromeId,
        weather: WeatherObservation,
    ): SimState =
        st.copy(world = st.world.updateAerodrome(aerodromeId) { it.copy(weather = weather) })
}
