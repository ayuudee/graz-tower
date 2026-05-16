package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.core.world.WeatherObservation
import xyz.easiersaid.twr.core.world.updateAerodrome
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AftnAddress
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport
import xyz.easiersaid.twr.protocol.headingDegreesMagnetic
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.commitmentStageTransitions
import xyz.easiersaid.twr.sim.testing.controllerAt
import xyz.easiersaid.twr.sim.testing.firstWhere
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.missionStepTransitions
import xyz.easiersaid.twr.sim.testing.positionPointTransitions
import xyz.easiersaid.twr.sim.testing.responsibilityTransitions
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace
import xyz.easiersaid.twr.sim.testing.transitionsOf
import xyz.easiersaid.twr.sim.testing.weatherTransitions

/**
 * G3b — **cross-aerodrome** Transit-arrival reactive-GA golden test
 * (fn-28.7). Single AI aircraft files a VFR LOWG → LJMB transit; on
 * arrival approach at LJMB, the destination aerodrome's runway 14
 * wind exceeds C172 POH crosswind (scenario 1) or AFH-advisory
 * tailwind (scenario 2). The pilot's
 * `isTransitArrivalReactiveGoAroundEligible(aircraft, mission)` guard
 * (fn-28.6 R18 — Transit goal + arrival primitive direct child of the
 * Transit compound + Final phase + active runway) is satisfied AND the
 * widened `deriveCrosswindEvent` / `deriveTailwindEvent` disjunctive
 * eligibility (round-12 Major 1: `isReactiveGoAroundEligible(mission)
 * || isTransitArrivalReactiveGoAroundEligible(aircraft, mission)`)
 * fires the recognition. The existing `applyCrosswindGoAround` /
 * `applyTailwindGoAround` Transit-dispatch fork (R18) routes through
 * the shared `applyTransitArrivalReactiveGoAround` helper which
 * rewrites the mission via
 * `replaceFromActivePrimitive(listOf(goAroundTask(), circuitTask(),
 * groundArrivalTask()))` (R13 + R22) and transmits
 * `Report(GoingAround)` with `climbSpeedMps + Final + None +
 * patternAltitude` Tick A intent (R19).
 *
 * **Closes the G3b cross-aerodrome reactive-GA pair** (the seventh +
 * eighth reactive-GA paths overall, the **first** cross-aerodrome
 * reactive-GA paths). Two `@Test` methods — `crosswind` and `tailwind`
 * — both reuse [Fixtures.LOWG_LJMB_VFR_REACTIVE] and only differ in
 * the recognition axis exercised by the per-test
 * `world.aerodromes[LJMB].weather` mutation hook.
 *
 * **Sibling tests:**
 *  - **G2** ([G2CrossAerodromeVfrTest]) — single-aircraft cross-
 *    aerodrome VFR transit (LOWG → LJMB), no GA. Structural template
 *    for the cross-aerodrome flow (filing distribution, autonomous
 *    InitialContact at the destination REP, post-contact ownership
 *    flip, recovery landing at LJMB). G3b adds the **wind-reactive
 *    GA branch on arrival approach at LJMB** atop G2's shape.
 *  - **G3a-react-crosswind** ([G3aPilotReactiveCrosswindTest]) — the
 *    single-aerodrome pilot-reactive crosswind GA at LOWG. Same
 *    recognition axis (`deriveCrosswindEvent`'s widened eligibility),
 *    same two-transition world-weather authorship pattern, same
 *    three-layer pin shape; distinguishing surfaces are (a) the
 *    Transit-arrival mission shape vs CircuitTraining single-circuit,
 *    (b) the suffix-replace rewrite primitive
 *    (`replaceFromActivePrimitive([goAroundTask(), circuitTask(),
 *    groundArrivalTask()])`) vs the circuit-only `replaceChild { it
 *    .isCircuitLike() }` rewrite, and (c) recognition is happening
 *    on a destination aerodrome other than the departure airport.
 *  - **G3a-react-tailwind** ([G3aPilotReactiveTailwindTest]) — the
 *    single-aerodrome pilot-reactive tailwind GA at LOWG. Sibling to
 *    G3a-react-crosswind on the tailwind axis; this test's tailwind
 *    method mirrors the tailwind sibling's recovery-`Report(Downwind)`
 *    transition-2 gate (codex round-2 strengthening — pure radio
 *    observable, no peek into pilot state).
 *  - **`PilotTransitArrivalReactiveGoAroundTest`** (pilot-side unit
 *    test) — composition pin on the dispatch fork + suffix-replace
 *    rewrite + Tick A intent + `resetForGoAround` cleared fields +
 *    no-refire invariant. This sim test is the end-to-end sibling
 *    that exercises the same code path against world weather +
 *    cross-aerodrome routing + radio + controller lifecycle.
 *
 * **What G3b distinctively pins:**
 *  - **Cross-aerodrome reactive recognition at LJMB**: the wind
 *    mutation targets the destination's weather slot
 *    (`world.aerodromes[LJMB].weather` via the
 *    [xyz.easiersaid.twr.core.world.updateAerodrome] lens), NOT the
 *    departure's. The pilot's `windForMission` projection resolves
 *    the away aerodrome key via the mission's goal-destination per
 *    fn-28.6's audit (`PilotWiring.buildPilotInput` carries every
 *    `world.aerodromes[*].weather?.wind` entry — fail-closed only on
 *    null wind — and `windForMission` narrows by mission shape).
 *    Recognition fires on a destination aerodrome OTHER than the
 *    departure airport — the first reactive-GA path with that
 *    property. NO new wiring required for the cross-aerodrome
 *    surface (fn-28.6 round 6 weather-projection audit confirmed
 *    the existing `mapNotNull`-shaped projection is correct).
 *  - **Transit-arrival mission shape via dispatch fork**: the
 *    recognition fires through the widened `deriveCrosswindEvent` /
 *    `deriveTailwindEvent` disjunctive eligibility (round-12 Major 1
 *    — recognition is in `derive*Event`, NOT inside the appliers;
 *    round-16 Major 1 reviewer focus). Apply dispatches through
 *    `applyCrosswindGoAround` / `applyTailwindGoAround`'s
 *    Transit-arrival fork via the same
 *    `isTransitArrivalReactiveGoAroundEligible(aircraft, mission)`
 *    guard. Single-applier dispatch fork; the existing
 *    pilot-decision pipeline is unchanged.
 *  - **R22 suffix-replace via existing GA TaskNodes — NO
 *    destination-GA placeholder enum/string** (round-5 Critical 2):
 *    the suffix-replacement at the active arrival primitive uses
 *    `replaceFromActivePrimitive(listOf(goAroundTask(),
 *    circuitTask(), groundArrivalTask()))` — the same existing
 *    TaskNode helpers used by every other GA path. No new
 *    destination-GA placeholder `MissionStep` value or placeholder
 *    string is introduced anywhere in this test or its spec. The
 *    mission-tree shape pin references the resolved-by-.6 TaskNode
 *    types directly.
 *  - **R19 Transit GA Tick A intent**: `climbSpeedMps + Final +
 *    None + patternAltitude` — NOT zero-target (DA/abort regime),
 *    NOT `Climbing` phase (self-initiated DA regime). Aligns with
 *    every other reactive-GA Tick A (`applyCrosswindGoAround`'s
 *    circuit-only branch, `applyTailwindGoAround`'s circuit-only
 *    branch, `applyPlannedGoAround`, `applyAtcInitiatedGoAround`).
 *    The applier's intent is exercised end-to-end through this
 *    test's recovery-circuit kinematics; the unit-level Tick A pin
 *    lives in `PilotTransitArrivalReactiveGoAroundTest`.
 *  - **Bounded-window kinematic non-event** (round-10 Minor 3): the
 *    test runs until the GA's named witness is observed —
 *    `Report(GoingAround)` transmission (the `goAroundTask()`
 *    primitive's emitted radio transmission) — and the kinematic
 *    non-event "aircraft never lands at LJMB" is pinned **WITHIN
 *    the bounded window** between the LJMB-side wind-shift hook fire
 *    and the wind-recovery hook fire. **Recovery landing is out-of-
 *    window** — in production R22 has a `groundArrivalTask()` after
 *    the recovery circuit so the aircraft eventually lands and
 *    taxis to a LJMB stand; the test's window stops at the named
 *    witness (per the task spec) and downstream recovery landing is
 *    NOT pinned here. NOT a contradiction with R22's full
 *    continuation — in production the recovery lands; the test's
 *    window is bounded by the named witness.
 *  - **VFR; no SID**: the Transit-arrival reactive-GA path is VFR-
 *    only at fn-28's scope. The fixture's filed plan
 *    (`FiledPlan.Vfr(..., destinationRunway = RunwayId("14"),
 *    intent = AircraftIntent.Transit)`) carries no SID; the cruise
 *    is a single `FLY_DEPARTURE` primitive whose terminal waypoint
 *    is the LJMB-side procedure REP (per `planMission`'s Transit
 *    arm at `PilotMission.kt:855`).
 *  - **LJMB runway 14 geometry** (140°M heading). Pure-crosswind
 *    direction is therefore `(140 + 90) % 360 = 230°M`; pure-
 *    tailwind direction is `(140 + 180) % 360 = 320°M`. Both
 *    directions are computed via [headingDegreesMagnetic] +
 *    arithmetic (NOT hardcoded literals) so a future re-numbering
 *    of LJMB runway 14 (e.g. magnetic-variation update,
 *    re-designation) propagates through the test.
 *
 * **Doctrine** (carried from G3a-react siblings):
 *  - **FAA AFH (FAA-H-8083-3C) Chapter 9**: crosswind landing
 *    (Common Error #1 — exceeding the demonstrated crosswind
 *    component) + tailwind landing (touchdown-energy / go-around-
 *    margin axis). Same chapter, two physical mechanisms.
 *  - **14 CFR §23.233(a)** (pre-Amendment 64) + **FAA AC 23-8B**:
 *    POH "demonstrated crosswind" is performance information
 *    (0.2 V_SO floor), not a limitation. C172 = 15 kt POH-
 *    demonstrated.
 *  - **Cessna 172R/172S NAV III POH §2 (Limitations)**: explicit
 *    **absence** of a published tailwind limitation; 10 kt is the
 *    FAA AFH Ch 9 advisory adopted as the modelling anchor.
 *  - **ICAO Annex 6 Part II §2.4**: PIC final authority (GA
 *    without ATC permission per CAP 413 §4.66 (Ed 24 — formerly
 *    §4.67 in Ed 23, renumbered per fn-17.1) / ICAO Doc 4444
 *    §12.3.4.18 — home/away agnostic; the controller-side `GA-
 *    POST-CLEAR` interrupt fires off any pilot-emitted
 *    `Report(GoingAround)` regardless of which aerodrome's airspace
 *    the aircraft is in).
 *  - **ICAO Doc 4444 §7.10.2**: controller-side missed-approach
 *    handling triggered by `Report(GoingAround)` — same as the
 *    home-aerodrome G3a-react siblings.
 *
 * **Time band** (R12 — ±15% per fn-8.3 decision #11 inheritance):
 * unlike the G3a-react siblings which run a single-airport
 * full-stop circuit, G3b composes G2's cross-aerodrome run (LOWG →
 * LJMB, ~50-75 min nominal per G2's pin) with a single reactive-GA
 * detour + recovery circuit at LJMB (~10-15 min additional per the
 * G3a-react sibling shape). Combined nominal: 60-90 sim minutes.
 * The wall in this test is 120 sim minutes; the assertion is
 * **completion within the wall** (no rigid lower/upper centred-on-
 * observed-wall pin yet — first GREEN to anchor the band lives in
 * the user's local Gradle run, captured in the task's evidence).
 *
 * **Inherited-gate-semantics audit comments**: the
 * `aircraftIsOnFinalWithLandingClearance` hook predicate, the
 * radio-tracking GoingAround / recovery-Downwind one-shots, the
 * commitment-stage regression pin, and the kinematic-non-event pin
 * are **inherited unchanged** from the G3a-react-tailwind sibling
 * (which carries the codex round-2 strengthening to a radio-only
 * recovery gate). The inheritance is load-bearing: the controller-
 * side `GA-POST-CLEAR` machinery is trigger-agnostic and the
 * commitment lifecycle does NOT vary by aerodrome (home vs away).
 * A regression that broke any of these gate semantics would surface
 * in BOTH G3a-react-tailwind AND G3b — keeping the predicates
 * shape-identical to G3a-react-tailwind preserves the cross-test
 * regression signal.
 *
 * @see G2CrossAerodromeVfrTest the no-GA cross-aerodrome anchor
 * @see G3aPilotReactiveCrosswindTest the same-aerodrome crosswind
 *      sibling
 * @see G3aPilotReactiveTailwindTest the same-aerodrome tailwind
 *      sibling (gate semantics inherited here)
 * @see xyz.easiersaid.twr.pilot.PilotTransitArrivalReactiveGoAroundTest
 *      the unit-level composition pin on the dispatch fork
 */
class G3bCrossAerodromeReactiveTest {

    /**
     * Scenario 1 — crosswind axis. LJMB runway 14 (140°M heading);
     * pure-crosswind direction `(140 + 90) % 360 = 230°M`. Once the
     * tower has issued `ClearedToLand` and the aircraft is on
     * `phase = Final` at LJMB, the per-tick hook authors
     * `world.aerodromes[LJMB].weather = WeatherObservation(wind =
     * Available(Wind(directionDegrees = 230, speedKnots = 20)))`
     * one-shot — pure direct crosswind, 20 kt > C172's 15 kt POH
     * crosswind limit. The pilot's widened `deriveCrosswindEvent`
     * fires off the Transit-arrival shape; the apply dispatches to
     * `applyTransitArrivalReactiveGoAround`; `Report(GoingAround)`
     * is transmitted; the controller's `GA-POST-CLEAR` interrupt
     * regresses the commitment. Within the bounded window between
     * crosswind authored and crosswind cleared, the aircraft does
     * NOT enter `LandingRoll` or `Vacating`.
     */
    @Test
    fun `world-authored crosswind exceedance at LJMB triggers Transit-arrival reactive GA in cross-aerodrome flight`() {
        runCrossAerodromeReactiveScenario(
            scenario = ReactiveScenario.CROSSWIND,
        )
    }

    /**
     * Scenario 2 — tailwind axis. LJMB runway 14 (140°M heading);
     * pure-tailwind direction `(140 + 180) % 360 = 320°M`. Once the
     * tower has issued `ClearedToLand` and the aircraft is on
     * `phase = Final` at LJMB, the per-tick hook authors
     * `world.aerodromes[LJMB].weather = WeatherObservation(wind =
     * Available(Wind(directionDegrees = 320, speedKnots = 15)))`
     * one-shot — pure direct tailwind, 15 kt > C172's 10 kt AFH-
     * advisory tailwind value (5 kt margin above the advisory).
     * The pilot's widened `deriveTailwindEvent` fires off the
     * Transit-arrival shape; the apply dispatches to
     * `applyTransitArrivalReactiveGoAround`; same downstream chain
     * as the crosswind scenario.
     *
     * The transition-2 gate is the post-GA recovery
     * `Report(Downwind)` (codex round-2 strengthening inherited from
     * the G3a-react-tailwind sibling — pure radio observable, no
     * peek into pilot state or controller belief).
     */
    @Test
    fun `world-authored tailwind exceedance at LJMB triggers Transit-arrival reactive GA in cross-aerodrome flight`() {
        runCrossAerodromeReactiveScenario(
            scenario = ReactiveScenario.TAILWIND,
        )
    }

    /** Recognition axis exercised by [runCrossAerodromeReactiveScenario]. */
    private enum class ReactiveScenario { CROSSWIND, TAILWIND }

    /**
     * Shared body of the crosswind + tailwind `@Test` methods. The
     * recognition axis is the **only** difference between the two
     * scenarios — same fixture, same routing, same hook-predicate
     * shapes, same three-layer pin layout. The axis chooses (a) the
     * wind direction the hook authors at transition-1 (pure-crosswind
     * vs pure-tailwind on runway 14), (b) the wind speed (20 kt
     * crosswind = 5 kt above the 15 kt POH limit; 15 kt tailwind = 5
     * kt above the 10 kt AFH advisory — both with the same 5 kt
     * margin above the limit), and (c) the transition-2 gate
     * (the crosswind scenario uses "aircraft off final" — the
     * G3a-react-crosswind sibling's shape; the tailwind scenario
     * uses the post-GA recovery `Report(Downwind)` transmission —
     * the G3a-react-tailwind sibling's codex round-2 strengthening).
     */
    @Suppress("LongMethod") // intentional single-method-per-scenario shape; mirrors G2/G3a sibling discipline
    private fun runCrossAerodromeReactiveScenario(scenario: ReactiveScenario) {
        // ── World + 4 controllers via the cross-aerodrome reactive fixture ───
        // Mirrors G2's `Fixtures.LOWG_LJMB_VFR.load()` pattern; the only
        // distinguishing surface is the fn-28.7 fixture variant with
        // concrete OAT + QNH at both aerodromes (required for the
        // PilotWiring projection to surface LJMB weather to the pilot's
        // `windForMission` resolver — see KDoc on
        // `Fixtures.LOWG_LJMB_VFR_REACTIVE`).
        val fixture = Fixtures.LOWG_LJMB_VFR_REACTIVE
        val loaded = fixture.load().getOrElse {
            fail("LOWG_LJMB_VFR_REACTIVE fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val ljmb = AerodromeId("LJMB")
        val ljmbRwy = RunwayId("14")
        val lowgGround = checkNotNull(loaded.controllerAt(lowg, RoleName.GROUND)) {
            "LOWG_GROUND missing from fixture"
        }
        val lowgTower = checkNotNull(loaded.controllerAt(lowg, RoleName.TOWER)) {
            "LOWG_TOWER missing from fixture"
        }
        val lowgApproach = checkNotNull(loaded.controllerAt(lowg, RoleName.APPROACH)) {
            "LOWG_APPROACH missing from fixture"
        }
        val ljmbTower = checkNotNull(loaded.controllerAt(ljmb, RoleName.TOWER)) {
            "LJMB_TOWER missing from fixture"
        }

        // Resolve LJMB runway 14 heading via the typed helper (fn-14.1):
        // `RunwayId("14").headingDegreesMagnetic() == 140`. Computing the
        // pure-crosswind / pure-tailwind directions arithmetically (rather
        // than hardcoding 230 / 320) lets a future re-designation of LJMB
        // runway 14 (e.g. magnetic-variation update) propagate through this
        // test. `% 360` prevents overflow when (heading + offset) >= 360;
        // map `0` back to `360` per the `Wind` smart constructor's `0..360`
        // domain (the constructor accepts both endpoints; we use 360 to
        // preserve the aviation-display "360 = North" convention).
        val runwayHeading = checkNotNull(ljmbRwy.headingDegreesMagnetic()) {
            "Runway $ljmbRwy did not parse to a magnetic heading — fixture/test mismatch"
        }
        val pureCrosswindDirection: Int = (((runwayHeading + 90) % 360))
            .let { if (it == 0) 360 else it }
        val pureTailwindDirection: Int = (((runwayHeading + 180) % 360))
            .let { if (it == 0) 360 else it }

        // ── Filing-cardinality pin (R4) — same as G2 ─────────────────────────
        // The fixture's single FiledPlan(LOWG → LJMB) distributes via Pass 14
        // AftnRouting.routeFiledPlan to LOWG_GROUND (Owned) + LJMB_TOWER
        // (knownStrips). A regression that emitted 1 or 3 events would
        // surface here without needing to walk the full ~60-minute run.
        val filings = loaded.initialEvents.filterIsInstance<SimEvent.FlightPlanFiled>()
        check(filings.size == 2) {
            "G3b routing-cardinality regression: expected exactly 2 FlightPlanFiled " +
                "events for the cross-aerodrome reactive fixture, got ${filings.size}: " +
                "${filings.map { it.recipient }}"
        }

        // ── One AI aircraft at LOWG stand, mission = Transit to LJMB ────────
        // VFR; no SID. Mission shape identical to G2 — the recognition axis
        // doesn't affect mission construction; the fn-28.6 widened
        // `derive*Event` eligibility + applier dispatch fork is what
        // surfaces the Transit-arrival GA path on this mission shape when
        // wind conditions at LJMB exceed limits AND the aircraft is on
        // `phase=Final` (with runway assigned).
        val aircraftId = AircraftId("OE-XYZ")
        val now = SimTime.ZERO
        val filedPlan = FiledPlan.Vfr(
            departureAerodrome = lowg,
            destinationAerodrome = ljmb,
            destinationRunway = ljmbRwy,
            intent = AircraftIntent.Transit,
        )
        val mission = createMission(
            goal = HighLevelGoal.Transit(destination = ljmb),
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = filedPlan,
        )
        val standPointId = Fixtures.LOWG_LJMB_VFR_REACTIVE.standPointId
        val aircraft = AircraftState(
            id = aircraftId,
            callsign = Callsign("OEXYZ"),
            position = loaded.world.geometry.points.getValue(standPointId),
            positionPoint = standPointId,
            phase = PilotPhase.AtStand,
            type = AircraftType.C172,
            pilotMission = mission,
        )

        // ── Initial per-aerodrome weather (matches fixture; pinned here) ────
        // The fixture sets both, but we pin the LJMB-side initial wind
        // explicitly here so the recovery-wind authorship can compare
        // against this exact `WeatherObservation` after transition-2 fires.
        // Pure-headwind on LJMB runway 14 (140°M @ 10 kt) — zero crosswind
        // component AND boundary-value tailwind (10 kt = C172's
        // AFH-advisory; strict-`>` recognition is non-firing per fn-15.1
        // boundary semantics). The LJMB OAT + QNH are carried via the
        // fixture; we don't re-pin them here.
        val ljmbInitialWeather = WeatherObservation(
            wind = WindReport.Available(
                Wind.unsafe(directionDegrees = runwayHeading, speedKnots = 10),
            ),
            qnh = fixture.weatherByAerodrome[ljmb]?.qnh,
            visibility = null,
            oat = fixture.weatherByAerodrome[ljmb]?.oat,
        )

        // ── Build SimState through the smart constructor ────────────────────
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(lowgGround, lowgTower, lowgApproach, ljmbTower),
            weatherByAerodrome = Fixtures.LOWG_LJMB_VFR_REACTIVE.weatherByAerodrome,
        ).getOrElse { error("SimState.initial rejected the LOWG_LJMB_VFR_REACTIVE fixture: $it") }

        // ── ATIS + drive ────────────────────────────────────────────────────
        // 120 sim minutes wall — block-time budget is ~50-75 min for the
        // G2-shape cross-aerodrome cruise + a single reactive-GA detour +
        // recovery circuit at LJMB (~10-15 min additional per G3a-react
        // sibling shape). Combined nominal: 60-90 min. Hitting the 120-min
        // wall means the run wedged.
        val until = SimTime.ZERO + SimDuration.ofMillis(120 * 60 * 1000L)
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
        val ljmbAtis = Atis(
            letter = 'B',
            aerodrome = ljmb,
            configuration = RunwayConfiguration(
                arrivals = listOf(ljmbRwy),
                departures = listOf(ljmbRwy),
            ),
            wind = Wind.unsafe(runwayHeading, 10),
            qnh = null,
            visibility = null,
            generatedAt = now,
        )
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = lowgAtis),
            SimEvent.AtisIssued(time = now, aerodrome = ljmb, atis = ljmbAtis),
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = lowgGround.id),
            SimEvent.ControllerCycle(time = now, controllerId = lowgTower.id),
            SimEvent.ControllerCycle(time = now, controllerId = lowgApproach.id),
            SimEvent.ControllerCycle(time = now, controllerId = ljmbTower.id),
        )

        // ── Two-transition LJMB-weather authorship via `onAfterEvent` ───────
        //
        // Transition 1 — wind crosses past C172's limit at LJMB. Fires when
        // the aircraft is on `phase=Final` AND LJMB_TWR's commitment for
        // the aircraft sits in a post-clearance stage (`LandingClearanceIssued`
        // or `AwaitLandedObserved`). Same shape as G3a-react-{crosswind,
        // tailwind}'s hook predicate — the controller-side `GA-POST-CLEAR`
        // interrupt is trigger-agnostic and home/away-agnostic; the
        // commitment lifecycle does NOT vary across aerodromes (the
        // inherited-gate-semantics audit). The wind direction + speed at
        // transition-1 depends on the recognition axis (see [scenario]).
        //
        // Transition 2 — wind returns within limits. Two gate shapes by axis:
        //  - CROSSWIND: `aircraftIsOffFinal` (G3a-react-crosswind sibling's
        //    shape). The Transit-arrival GA path retains the
        //    `phase=Final + route=None` Tick A intent (R19) and Tick B's
        //    planRoute special case will rebuild the GA route on the next
        //    tick; the aircraft then transitions out of Final as it
        //    climbs into the GA pattern, and the off-final gate fires.
        //  - TAILWIND: the post-GA recovery `Report(events=[Downwind(...)])`
        //    transmission (G3a-react-tailwind sibling's codex round-2
        //    strengthening). The pilot transmits the downwind position only
        //    when physically re-entering downwind on the recovery circuit;
        //    pure radio observable, no peek into pilot state. Strictly
        //    tighter than off-final since the recovery-pattern circuit is
        //    NOT on final the moment the GA fires.
        //
        // Each transition is one-shot guarded; defense-in-depth against
        // multi-fire which would either retrigger recognition (transition
        // 1) or thrash the wind (transition 2).
        var windAuthored = false
        var windClearedToLimit = false
        val windAuthoredAt = arrayOf<SimTime?>(null)
        val windClearedAt = arrayOf<SimTime?>(null)
        val goingAroundTransmittedFlag = arrayOf(false)
        val recoveryDownwindReportedFlag = arrayOf(false)
        val goingAroundTransmittedAt = arrayOf<SimTime?>(null)
        val onAfterEvent: (SimEvent, SimState) -> SimState = { ev, st ->
            // Track `Report(GoingAround)` emission via the event stream so
            // the recovery-wind transition's gate is consistent with the
            // sim's actual radio surface (not an indirect mission-step
            // proxy). The named witness for the test's bounded window is
            // the `goAroundTask()` primitive's emitted transmission —
            // `SpeakerRef.Pilot.aircraftId == aircraftId` AND the
            // transmission's utterance carries `ReportEvent.GoingAround`.
            if (ev is SimEvent.TransmissionStart) {
                val tx = ev.transmission
                val speakerAc = (tx.speaker as? SpeakerRef.Pilot)?.aircraftId
                val pilotTransmission =
                    (tx.utterance as? Utterance.FromPilot)?.transmission
                val report = pilotTransmission as? xyz.easiersaid.twr.protocol.Report
                if (speakerAc == aircraftId && report != null) {
                    if (!goingAroundTransmittedFlag[0] &&
                        report.events.any { it is ReportEvent.GoingAround }
                    ) {
                        goingAroundTransmittedFlag[0] = true
                        goingAroundTransmittedAt[0] = st.now
                    }
                    // Tailwind-axis post-GA Downwind tracker (codex round-2
                    // strengthening inherited from G3a-react-tailwind). The
                    // pre-GA `Report(Downwind)` from the original arrival is
                    // BEFORE the GA transmission; the `goingAroundTransmittedFlag`
                    // gate prevents this tracker from latching on the wrong
                    // Downwind. The load-bearing observable is the **second**
                    // (recovery-circuit) Downwind transmission.
                    if (goingAroundTransmittedFlag[0] &&
                        !recoveryDownwindReportedFlag[0] &&
                        report.events.any { it is ReportEvent.Downwind }
                    ) {
                        recoveryDownwindReportedFlag[0] = true
                    }
                }
            }

            when {
                // Transition 2 — wind returns within limit (one-shot).
                // Axis-dependent gate (see [scenario] handling).
                !windClearedToLimit &&
                    transition2GateFires(scenario, st, aircraftId,
                        goingAroundTransmittedFlag[0],
                        recoveryDownwindReportedFlag[0],
                    ) -> {
                    windClearedToLimit = true
                    windClearedAt[0] = st.now
                    authorWeather(st, ljmb, ljmbInitialWeather)
                }
                // Transition 1 — wind crosses past limit at LJMB (one-shot).
                // Gated on the LJMB-tower commitment + aircraft-final
                // post-clearance window per the inherited G3a-react gate
                // semantics (audit comment block at the top of this hook).
                !windAuthored &&
                    aircraftIsOnFinalWithLandingClearance(st, aircraftId, ljmbTower.id) -> {
                    windAuthored = true
                    windAuthoredAt[0] = st.now
                    val shiftedWeather = weatherForAxis(
                        scenario = scenario,
                        crosswindDirection = pureCrosswindDirection,
                        tailwindDirection = pureTailwindDirection,
                        baseline = ljmbInitialWeather,
                    )
                    authorWeather(st, ljmb, shiftedWeather)
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
        val journey = finalState.formatJourney(aircraftId, records)
        println(journey)

        println()
        println("─── G3b cross-aerodrome reactive ($scenario) per-aircraft trace summary ───")
        println("LJMB runway heading (14):   ${runwayHeading}°M")
        println("Pure-crosswind direction:   ${pureCrosswindDirection}°M")
        println("Pure-tailwind direction:    ${pureTailwindDirection}°M")
        println("Wind authored at:           ${windAuthoredAt[0]?.millis ?: "<NEVER>"}ms")
        println("Wind cleared at:            ${windClearedAt[0]?.millis ?: "<NEVER>"}ms")
        println("Report(GoingAround) at:     ${goingAroundTransmittedAt[0]?.millis ?: "<NEVER>"}ms")
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
        println("Commitment stage transitions (LJMB tower):")
        for (t in trace.commitmentStageTransitions(aircraftId, ljmbTower.id)) {
            val fromStr = t.from.fold({ "absent" }, { it.name })
            val toStr = t.to.fold({ "absent" }, { it.name })
            println("  [${t.after.time.millis}ms] $fromStr → $toStr")
        }
        println("Weather (LJMB) transitions:")
        for (t in trace.weatherTransitions(ljmb)) {
            val fmt: (arrow.core.Option<WeatherObservation>) -> String = { opt ->
                opt.fold({ "absent" }) { obs ->
                    when (val w = obs.wind) {
                        is WindReport.Available ->
                            "wind=${w.wind.directionDegrees}°@${w.wind.speedKnots}kt"
                        WindReport.NotReported -> "NotReported"
                    }
                }
            }
            println("  [${t.after.time.millis}ms] ${fmt(t.from)} → ${fmt(t.to)}")
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
        println("─── end G3b cross-aerodrome reactive ($scenario) per-aircraft trace summary ───")
        println()

        // ── Named-witness pin (bounded window) ──────────────────────────────
        //
        // The test's bounded time window is anchored on the GA's named
        // witness: the `Report(GoingAround)` transmission. Per the task
        // spec's round-10 Minor 3, the within-window kinematic non-event
        // "aircraft never lands at LJMB" stands; the recovery landing is
        // out-of-window. The defensive pin ensures the named witness was
        // emitted (otherwise the rest of the assertions are vacuously
        // true / spuriously false).
        check(windAuthored) {
            "World-authorship hook never fired transition 1 — " +
                "`aircraftIsOnFinalWithLandingClearance` (LJMB_TWR) never returned true. " +
                "Either the aircraft never reached phase=Final at LJMB, or ClearedToLand was " +
                "never issued by LJMB_TWR. Pre-condition for the named-witness pin and the " +
                "rest of the assertions.\n$journey"
        }
        check(goingAroundTransmittedFlag[0]) {
            "Named witness never fired: `Report(GoingAround)` was never transmitted by " +
                "$aircraftId. The widened `derive${scenario.name.lowercase().replaceFirstChar { it.uppercase() }}Event` " +
                "did not fire on the Transit-arrival mission shape, OR `applyCrosswindGoAround` " +
                "/ `applyTailwindGoAround`'s Transit-dispatch fork did not route through " +
                "`applyTransitArrivalReactiveGoAround`, OR the cognitive layer did not emit " +
                "the goAroundTask() primitive's REPORTED transmission. fn-28.6 R18 dispatch " +
                "regression.\n$journey"
        }
        check(windClearedToLimit) {
            "World-authorship hook never fired transition 2 — the recovery gate (axis-specific) " +
                "never satisfied: scenario=$scenario, goingAroundTransmitted=${goingAroundTransmittedFlag[0]}, " +
                "recoveryDownwindReported=${recoveryDownwindReportedFlag[0]}. The bounded-window " +
                "pin needs both endpoints; without transition 2 the wind-clear timestamp " +
                "(window upper bound) is undefined.\n$journey"
        }

        // ── World-weather transition pin (exactly two LJMB transitions) ─────
        //
        // The aerodrome-keyed `world.aerodromes[LJMB].weather` slice
        // transitions exactly twice during the run: (1) initial 140°@10 →
        // axis-dependent shift (crosswind/tailwind authored), (2) shifted
        // → 140°@10 (cleared). NO controller-belief slice expansion —
        // weather is world-state per [weatherTransitions]'s KDoc.
        val weatherTrans = trace.weatherTransitions(ljmb)
        check(weatherTrans.size == 2) {
            "Expected exactly two transitions in world.aerodromes[$ljmb].weather " +
                "(authored + cleared), observed ${weatherTrans.size}. More than two would " +
                "indicate the one-shot guards regressed; fewer than two indicates either " +
                "the authorship hook didn't fire (covered by the defensive pins above) or " +
                "the trace doesn't see the world-state mutation (sim-engine invariant " +
                "violation).\n$journey"
        }
        val weatherShiftMs = weatherTrans[0].after.time.millis
        val weatherClearMs = weatherTrans[1].after.time.millis
        check(weatherShiftMs < weatherClearMs) {
            "Weather-transition ordering pin: shift ($weatherShiftMs ms) must precede clear " +
                "($weatherClearMs ms). Equal/reversed indicates the one-shot guards fired in " +
                "the wrong order.\n$journey"
        }

        // Defense-in-depth: confirm the shift is the high-{crosswind,tailwind}
        // state and the clear is the headwind state — pins the wind values
        // against the authorship parameters above.
        val shiftedWind = (weatherTrans[0].to.getOrElse {
            fail("Weather-shift transition has absent `to` — invariant violation.\n$journey")
        }.wind as? WindReport.Available)?.wind
            ?: fail("Weather-shift transition `to.wind` is not WindReport.Available.\n$journey")
        val expectedShift = when (scenario) {
            ReactiveScenario.CROSSWIND -> pureCrosswindDirection to 20
            ReactiveScenario.TAILWIND -> pureTailwindDirection to 15
        }
        check(shiftedWind.directionDegrees == expectedShift.first &&
            shiftedWind.speedKnots == expectedShift.second) {
            "Weather-shift wind mismatch ($scenario): got " +
                "${shiftedWind.directionDegrees}°@${shiftedWind.speedKnots} expected " +
                "${expectedShift.first}°@${expectedShift.second}.\n$journey"
        }
        val clearedWind = (weatherTrans[1].to.getOrElse {
            fail("Weather-clear transition has absent `to` — invariant violation.\n$journey")
        }.wind as? WindReport.Available)?.wind
            ?: fail("Weather-clear transition `to.wind` is not WindReport.Available.\n$journey")
        check(clearedWind.directionDegrees == runwayHeading && clearedWind.speedKnots == 10) {
            "Weather-clear wind mismatch: got ${clearedWind.directionDegrees}°@${clearedWind.speedKnots} " +
                "expected ${runwayHeading}°@10.\n$journey"
        }

        // ── Layer 1 — Causal partial-order pin (exactly one GoingAround) ────
        //
        // Same shape as G3a-react-{crosswind,tailwind}: exactly ONE
        // `Report(GoingAround)` between the wind-shift and wind-recovery
        // cycles. More than one would indicate hysteresis regression
        // (recognition re-fired — would mean
        // `isTransitArrivalReactiveGoAroundEligible`'s no-refire invariant
        // — `activeCompound() != null` after suffix-replace — failed);
        // zero would indicate the widened recognition didn't fire on the
        // Transit-arrival shape (R18 dispatch regression).
        val goingAroundRecords = records.filter { rec ->
            val speakerAc = (rec.speaker as? SpeakerRef.Pilot)?.aircraftId
            if (speakerAc != aircraftId) return@filter false
            val pilotTransmission = (rec.utterance as? Utterance.FromPilot)?.transmission
            val report = pilotTransmission as? xyz.easiersaid.twr.protocol.Report
                ?: return@filter false
            report.events.any { it is ReportEvent.GoingAround }
        }
        check(goingAroundRecords.size == 1) {
            "Expected exactly one Report(GoingAround) for $aircraftId between the LJMB " +
                "wind-shift (${weatherShiftMs}ms) and wind-recovery (${weatherClearMs}ms) " +
                "cycles, observed ${goingAroundRecords.size}. More than one indicates the " +
                "fn-28.6 no-refire invariant regressed (post-suffix-replace " +
                "`activeCompound() != null` check failed); zero indicates the widened " +
                "`derive*Event` did not fire on the Transit-arrival mission shape.\n$journey"
        }
        val goingAroundRecord = goingAroundRecords.single()
        val goingAroundMs = goingAroundRecord.time.millis
        check(goingAroundMs in weatherShiftMs..weatherClearMs) {
            "Report(GoingAround) (${goingAroundMs}ms) must occur between the LJMB wind-shift " +
                "(${weatherShiftMs}ms) and wind-recovery (${weatherClearMs}ms) cycles.\n$journey"
        }

        // ── Layer 2 — Sticky-witness regression pin (LJMB_TWR commitment) ──
        //
        // Same shape as G3a-react-{crosswind,tailwind}: the pilot's
        // `Report(GoingAround)` is received by **LJMB_TWR** (the
        // home-aerodrome-agnostic `GA-POST-CLEAR` interrupt), and the
        // tower's commitment regresses from `{LandingClearanceIssued,
        // AwaitLandedObserved}` to `AwaitDownwind`. Distinct from
        // G3a-react only in WHICH controller's commitment we read
        // (LJMB_TWR, not LOWG_TWR); the gate semantics are inherited
        // unchanged (the controller-side machinery is home/away-agnostic).
        val stageTransitions = trace.commitmentStageTransitions(aircraftId, ljmbTower.id)
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
            "Expected exactly one LJMB_TWR stage regression {LandingClearanceIssued | " +
                "AwaitLandedObserved} → AwaitDownwind on the Transit-arrival reactive GA, " +
                "observed ${regressions.size}. Same inherited gate semantics as the G3a-react " +
                "siblings — controller-side `GA-POST-CLEAR` is home/away-agnostic; a deviation " +
                "would indicate that the cross-aerodrome `Report(GoingAround)` reception path " +
                "diverges from the home-aerodrome path (it must NOT).\n$journey"
        }
        val regression = regressions.single()
        check(regression.after.time.millis > goingAroundMs) {
            "Radio-delivery prerequisite: LJMB_TWR stage regression at " +
                "${regression.after.time.millis}ms must fire strictly AFTER Report(GoingAround) " +
                "at ${goingAroundMs}ms. `GA-POST-CLEAR` gates on `GoAroundEvent` delivered " +
                "from the radio; a regression AT-OR-BEFORE the GoingAround transmission would " +
                "indicate the regression fired off some other channel.\n$journey"
        }

        // Post-regression sticky witnesses are reset (fn-8.3 R7-style).
        val commitmentAfter = regression.after.state.beliefs[ljmbTower.id]
            ?.commitments?.get(aircraftId)
            ?: fail(
                "LJMB_TWR commitment for $aircraftId missing AT regression cursor — the " +
                    "regression should preserve the commitment (stage drops, commitment lives), " +
                    "not delete it.\n$journey"
            )
        check(!commitmentAfter.touchedDownDuringCommitment) {
            "touchedDownDuringCommitment must be reset post-regression; got " +
                "${commitmentAfter.touchedDownDuringCommitment}.\n$journey"
        }
        check(!commitmentAfter.pilotReadyDuringCommitment) {
            "pilotReadyDuringCommitment must be reset post-regression; got " +
                "${commitmentAfter.pilotReadyDuringCommitment}.\n$journey"
        }
        check(commitmentAfter.observedReportsDuringCommitment.isEmpty()) {
            "observedReportsDuringCommitment must be reset post-regression; got " +
                "${commitmentAfter.observedReportsDuringCommitment.size} entries: " +
                "${commitmentAfter.observedReportsDuringCommitment}.\n$journey"
        }

        // ── Layer 3 — Kinematic non-event pin (BOUNDED WINDOW) ──────────────
        //
        // **Bounded-window pin** (round-10 Minor 3): the within-window
        // kinematic non-event "aircraft never lands at LJMB" is pinned
        // BETWEEN the wind-shift and wind-recovery cycles. Downstream
        // recovery landing is OUT-OF-WINDOW — in production R22 has the
        // `groundArrivalTask()` after the recovery circuit so the
        // aircraft eventually lands; the test's window stops at the named
        // witness (per the task spec). NOT a contradiction with R22's
        // full continuation. A LandingRoll/Vacating phase entry inside
        // the window would indicate either (i) the widened recognition
        // fired too late, (ii) the suffix-replace via R13 primitive
        // failed to invalidate the original arrival's threshold-bound
        // route, or (iii) the LJMB_TWR's interrupt didn't fire and the
        // aircraft landed on the runway 14 in the exceedance condition.
        val phaseTransitions = trace.transitionsOf { st ->
            st.aircraft[aircraftId]?.phase
        }
        val touchdownInWindow = phaseTransitions.any { t ->
            val landed = t.to == PilotPhase.LandingRoll || t.to == PilotPhase.Vacating
            val inWindow = t.after.time.millis in weatherShiftMs..weatherClearMs
            landed && inWindow
        }
        check(!touchdownInWindow) {
            "Aircraft entered LandingRoll or Vacating between the LJMB wind-shift " +
                "(${weatherShiftMs}ms) and wind-recovery (${weatherClearMs}ms) cycles — the " +
                "Transit-arrival reactive GA must fire in time to prevent landing at LJMB " +
                "in the exceedance window.\nPhase transitions: " +
                phaseTransitions.joinToString { "${it.to}@${it.after.time.millis}ms" } +
                "\n$journey"
        }

        // ── Mission-tree shape pin (R13 + R22 — TaskNode type assertion) ────
        //
        // After the suffix-replace, the active leaf is inside the
        // `goAroundTask()` compound; the immediate ancestor in the
        // mission tree's flat task-list view at the active position is
        // the same `GoAround` TaskName the existing GA flows use. We
        // pin the **post-GA mission step set** appearing in the
        // mission-step transitions after the wind-shift cycle —
        // specifically that a `GOING_AROUND` step is visited (the
        // `goAroundTask()`'s first primitive) before the recovery
        // circuit's `FLY_DEPARTURE` (the `circuitTask()`'s first
        // primitive). NO destination-GA placeholder enum value or
        // placeholder string appears in this assertion (round-5
        // Critical 2) — the assertion references the resolved-by-.6
        // TaskNode types directly.
        val missionStepTrans = trace.missionStepTransitions(aircraftId)
        val postShiftSteps = missionStepTrans
            .filter { it.after.time.millis > weatherShiftMs }
            .mapNotNull { t -> t.to.fold({ null }, { it }) }
        val postShiftStepNames = postShiftSteps.map { it.name }
        check(postShiftStepNames.contains("GOING_AROUND")) {
            "R22 suffix-replace shape pin: after the LJMB wind-shift, the mission step " +
                "sequence must visit `GOING_AROUND` (the `goAroundTask()`'s first primitive). " +
                "Observed post-shift step sequence: $postShiftStepNames. A missing " +
                "GOING_AROUND would indicate the apply path did NOT route through " +
                "`applyTransitArrivalReactiveGoAround` (R18 dispatch fork regression) or the " +
                "`replaceFromActivePrimitive(listOf(goAroundTask(), ...))` rewrite did not " +
                "land the goAroundTask() compound at the active position.\n$journey"
        }
        // The recovery circuit's `FLY_DEPARTURE` (first primitive of
        // `circuitTask()`) must appear AFTER the `GOING_AROUND` step in
        // the post-shift sequence — this pins the R22 contract's
        // suffix order `[goAroundTask(), circuitTask(), groundArrivalTask()]`.
        val goingAroundIdx = postShiftStepNames.indexOf("GOING_AROUND")
        val flyDepartureIdx = postShiftStepNames.indexOf("FLY_DEPARTURE")
        if (flyDepartureIdx >= 0) {
            check(goingAroundIdx < flyDepartureIdx) {
                "R22 suffix-order pin: `GOING_AROUND` (index $goingAroundIdx) must precede " +
                    "the recovery `FLY_DEPARTURE` (index $flyDepartureIdx, first primitive of " +
                    "`circuitTask()`) in the post-shift step sequence. A reversed order would " +
                    "indicate the R22 contract `[goAroundTask(), circuitTask(), " +
                    "groundArrivalTask()]` was authored with the GA after the recovery " +
                    "circuit.\n$journey"
            }
        }

        // ── Recovery + bounded-window stop-condition compliance pin ─────────
        //
        // The named witness for the test's bounded window is the
        // `Report(GoingAround)` transmission (per task spec). The window
        // close is the wind-recovery (transition-2) cursor. Downstream
        // recovery landing is **out-of-window** by design — the test
        // does NOT pin recovery-touchdown / vacate-coordination /
        // run-up dwell / final-stand parking. Those pins live in G2
        // (same cross-aerodrome shape, no GA) and in the G3a-react
        // siblings (same recovery-circuit shape, home aerodrome). G3b's
        // unique surface is "the Transit-arrival GA recognised + applied
        // correctly in a cross-aerodrome flight"; recovery semantics
        // are inherited.
        //
        // The defensive lower-bound on the named witness's window
        // ordering: the GoingAround transmission must fall strictly
        // BETWEEN wind-shift and wind-recovery cycles (already pinned
        // above on Layer 1's `goingAroundMs in weatherShiftMs..
        // weatherClearMs`). The defensive upper-bound: the run wall
        // (120 sim minutes) must not be hit — i.e. the wind-recovery
        // cycle fires strictly before the wall.
        check(weatherClearMs < until.millis) {
            "Bounded-window upper-bound pin: the wind-recovery cycle at ${weatherClearMs}ms " +
                "must fire strictly before the run's 120-min wall (${until.millis}ms). " +
                "Hitting the wall before transition-2 fires means the recovery gate (axis " +
                "$scenario) never satisfied — the GA may have fired but the recovery circuit " +
                "did not re-enter downwind (tailwind axis) / leave final (crosswind axis).\n" +
                "$journey"
        }

        // ── G2-shape filing-distribution pin (R4 inherited from G2) ─────────
        //
        // The fixture's flightPlans payload is one VFR plan with
        // destinationAerodrome=LJMB, intent=Transit. AftnRouting
        // .routeFiledPlan produces 2 events (LOWG_GROUND first, LJMB_TOWER
        // second). Filing-cardinality already pinned at the top; this row
        // pins the recipient identities and ordering survive routing.
        val recipients = filings.map { it.recipient }
        check(recipients == listOf(
            AftnAddress(lowg, RoleName.GROUND),
            AftnAddress(ljmb, RoleName.TOWER),
        )) {
            "G3b filing-distribution: expected [LOWG/GROUND, LJMB/TOWER] in order; got " +
                "$recipients.\n$journey"
        }

        // ── Sanity: mission did not crash before the GA fired ───────────────
        //
        // We do NOT pin `finalMission.isComplete` because the test's bounded
        // window is the named-witness stop condition (transition-2
        // wind-recovery cycle); the recovery landing is out-of-window per
        // the task spec. We DO check that the mission still has a
        // non-null pilotMission and the aircraft has not entered a
        // crashed state — defensive only, to surface engine-side
        // regressions that would cascade into the assertions above.
        val finalAircraft = finalState.aircraft.getValue(aircraftId)
        checkNotNull(finalAircraft.pilotMission) {
            "Aircraft $aircraftId lost its mission during the run — engine regression.\n$journey"
        }

        // Trace exists in final state — guards against the no-trace path
        // (which would also fail above on `trace.firstWhere` calls).
        trace.firstWhere { st -> st.aircraft[aircraftId]?.pilotMission != null }
            .getOrElse {
                fail("Mission never constructed in trace.\n$journey")
            }
    }

    /**
     * Axis-dispatched recovery-gate predicate for transition-2 of the
     * world-weather authorship hook. Same shape as the G3a-react sibling
     * helpers; the crosswind scenario uses `aircraftIsOffFinal` (the
     * G3a-react-crosswind sibling's gate), the tailwind scenario uses
     * the post-GA recovery `Report(Downwind)` transmission flag (the
     * G3a-react-tailwind sibling's codex round-2 strengthening).
     */
    private fun transition2GateFires(
        scenario: ReactiveScenario,
        st: SimState,
        aircraft: AircraftId,
        goingAroundTransmitted: Boolean,
        recoveryDownwindReported: Boolean,
    ): Boolean = when (scenario) {
        ReactiveScenario.CROSSWIND ->
            goingAroundTransmitted && aircraftIsOffFinal(st, aircraft)
        ReactiveScenario.TAILWIND ->
            recoveryDownwindReported
    }

    /**
     * Compute the LJMB-side `WeatherObservation` for the axis-specific
     * exceedance shift (transition-1). Crosswind scenario: pure-crosswind
     * direction at 20 kt (5 kt above C172's 15 kt POH crosswind limit).
     * Tailwind scenario: pure-tailwind direction at 15 kt (5 kt above
     * C172's 10 kt AFH-advisory tailwind value).
     *
     * QNH + OAT are inherited from the [baseline] (the fixture's LJMB
     * initial weather) — only the wind slot varies across transition-1.
     */
    private fun weatherForAxis(
        scenario: ReactiveScenario,
        crosswindDirection: Int,
        tailwindDirection: Int,
        baseline: WeatherObservation,
    ): WeatherObservation = when (scenario) {
        ReactiveScenario.CROSSWIND -> baseline.copy(
            wind = WindReport.Available(
                Wind.unsafe(directionDegrees = crosswindDirection, speedKnots = 20),
            ),
        )
        ReactiveScenario.TAILWIND -> baseline.copy(
            wind = WindReport.Available(
                Wind.unsafe(directionDegrees = tailwindDirection, speedKnots = 15),
            ),
        )
    }

    /**
     * Predicate for transition-1 authorship at LJMB: the aircraft is on
     * `phase=Final` AND **LJMB_TWR**'s commitment for the aircraft sits
     * in a **post-clearance** stage (`LandingClearanceIssued` or
     * `AwaitLandedObserved`). Same shape as the G3a-react sibling hooks;
     * the only difference is the controller-id parameter (LJMB_TWR here,
     * LOWG_TWR in the same-aerodrome siblings) — the gate semantics are
     * home/away-agnostic (inherited-gate-semantics audit).
     */
    private fun aircraftIsOnFinalWithLandingClearance(
        st: SimState,
        aircraft: AircraftId,
        towerId: xyz.easiersaid.twr.protocol.ControllerId,
    ): Boolean {
        val ac = st.aircraft[aircraft] ?: return false
        if (ac.phase != PilotPhase.Final) return false
        val commitment = st.beliefs[towerId]?.commitments?.get(aircraft) ?: return false
        val stage = commitment.stage
        return stage == TowerArrivalStage.LandingClearanceIssued ||
            stage == TowerArrivalStage.AwaitLandedObserved
    }

    /**
     * Predicate for transition-2 authorship (crosswind axis): the
     * aircraft is NOT on final (has climbed out / re-entered the
     * pattern). Used together with the `goingAroundTransmitted` gate
     * to fire the wind-recovery transition only after the GA path has
     * actually started executing. Inherited unchanged from
     * G3a-react-crosswind.
     */
    private fun aircraftIsOffFinal(st: SimState, aircraft: AircraftId): Boolean {
        val ac = st.aircraft[aircraft] ?: return false
        return ac.phase != PilotPhase.Final
    }

    /**
     * Pure world-state mutation: replace
     * `state.world.aerodromes[aerodromeId].weather` with [weather]. Per
     * fn-14.2 R12 the world-only test trigger discipline writes
     * directly to the world-state entity (NOT to controller beliefs
     * and NOT to `PilotInput`); the sim's per-cycle `buildPilotInput`
     * projection picks up the new wind on the next pilot decision
     * tick. fn-16 R8: aerodrome-keyed via the
     * [xyz.easiersaid.twr.core.world.updateAerodrome] lens helper
     * (replaces the deleted flat `state.weatherByAerodrome` map).
     */
    private fun authorWeather(
        st: SimState,
        aerodromeId: AerodromeId,
        weather: WeatherObservation,
    ): SimState =
        st.copy(world = st.world.updateAerodrome(aerodromeId) { it.copy(weather = weather) })
}
