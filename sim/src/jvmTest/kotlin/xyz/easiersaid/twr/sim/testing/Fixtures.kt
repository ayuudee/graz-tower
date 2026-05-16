package xyz.easiersaid.twr.sim.testing

import arrow.core.nonEmptyListOf
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import xyz.easiersaid.twr.core.world.WeatherObservation
import xyz.easiersaid.twr.protocol.WindReport
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.Temperature
import xyz.easiersaid.twr.protocol.Wind

/**
 * Pre-built per-aerodrome [Fixture]s for sim integration tests. Constants
 * carry only [Path]s and primitives — no I/O happens at class load. I/O
 * fires when [Fixture.load] is called.
 *
 * Pass 10 evolution: per Pass 4 plan R2, the manifest-driven loader will
 * remove the need for `weather` and `controllerRoles` injection; those
 * fields disappear from [Fixture]. The `Fixtures` constants survive with
 * shrunk shapes.
 *
 * **Per-fixture provenance** (G0 / G1 / G2 anchors):
 *  - [LOWG] — single-aircraft circuit training. Stand point
 *    `LOWG_STAND_1_POINT` from the LOWG world-candidate authoring.
 *    Drives G0 ([xyz.easiersaid.twr.sim.LowgGoldenTest]), G3a
 *    ([xyz.easiersaid.twr.sim.G3aPilotTrainedGoAroundTest] —
 *    single-aircraft trained go-around as circuit-training outcome;
 *    same fixture, distinguishing surface is the goal authorship
 *    `HighLevelGoal.CircuitTraining(outcomes = [GoAround, FullStop])`
 *    per `feedback_world_only_test_triggers.md`), G3a-obstruction
 *    ([xyz.easiersaid.twr.sim.G3aRunwayObstructionTest] — single-
 *    aircraft ATC-instructed reactive go-around on a world-authored
 *    runway obstruction; same fixture, distinguishing surface is the
 *    one-shot `runway.obstruction = RunwayObstruction(clearsAt = ...)`
 *    mutation via `runUntilWithStateTrace`'s `onAfterEvent` hook per
 *    `feedback_world_only_test_triggers.md`), G3a-obstruction-
 *    continue-approach
 *    ([xyz.easiersaid.twr.sim.G3aRunwayObstructionContinueApproachTest]
 *    — single-aircraft pre-clearance CONTINUE APPROACH on a short-TTL
 *    world-authored runway obstruction that clears in time per fn-13's
 *    `ObstructionClearsInTime` predicate; same fixture, distinguishing
 *    surface is the 5-second `clearsAt` TTL + pre-clearance authorship
 *    stage versus G3a-obstruction's 60-second post-clearance variant —
 *    the two tests together cover the three-state pre-clearance ladder
 *    of CAP 413 §4.55-4.56 / ICAO 4444 §12.3.4.16(d)), **G3a-react**
 *    ([xyz.easiersaid.twr.sim.G3aPilotReactiveCrosswindTest] — single-
 *    aircraft pilot-reactive go-around triggered by a world-authored
 *    wind shift whose crosswind component on the active runway exceeds
 *    the aircraft type's POH-derived `AircraftType.maxCrosswindKnots`
 *    (C172 = 15 kt); same fixture, distinguishing surface is the two-
 *    transition `world.aerodromes[LOWG].weather` mutation via
 *    `runUntilWithStateTrace`'s `onAfterEvent` hook per
 *    `feedback_world_only_test_triggers.md` — first one-shot authors
 *    20 kt direct crosswind to trigger the recognition, second one-shot
 *    returns wind to within limits once `Report(GoingAround)` has been
 *    transmitted and the aircraft is off final; closes the G3a trilogy
 *    by adding the fourth reactive-GA path — the first pilot-side
 *    reactive recognition driven by world weather), and
 *    **G3a-react-tailwind**
 *    ([xyz.easiersaid.twr.sim.G3aPilotReactiveTailwindTest] — single-
 *    aircraft pilot-reactive go-around triggered by a world-authored
 *    wind shift whose **tailwind component** on the active runway
 *    exceeds the aircraft type's `AircraftType.maxTailwindKnots`
 *    (C172 = 10 kt **FAA AFH-advisory** — Cessna 172R/172S POH §2 does
 *    NOT publish a hard tailwind limitation, so 10 kt is the FAA AFH
 *    Ch 9 industry-standard advisory for light singles; B738 = 15 kt
 *    FCOM Limitations §1 hard operational limitation — the per-type
 *    doctrinal severity asymmetry surfaces only on the tailwind axis,
 *    not on the crosswind axis); same fixture, same two-transition
 *    `world.aerodromes[LOWG].weather` discipline as G3a-react-crosswind
 *    via `onAfterEvent`; first one-shot authors 15 kt direct tailwind
 *    `((runwayHeading + 180) % 360 clamped 0→360)` to trigger
 *    recognition (5 kt margin above C172's 10 kt advisory), second
 *    one-shot returns wind to the initial 10 kt headwind once the
 *    post-GA **recovery-circuit
 *    `Report(events=[Downwind(...)])`** transmission is observed —
 *    the load-bearing recovery observable; strictly tighter than the
 *    crosswind sibling's `off-final` gate per fn-15.2 codex round-2/3
 *    review since the recovery downwind report only fires when the
 *    aircraft has physically re-entered the recovery pattern, not
 *    merely climbed out from the GA; closes the second pilot-reactive
 *    POH/AFH recognition axis as the fifth reactive-GA path).
 *  - [LOWG_HIGH_DA] — fn-28.3 (G3a-react-density-altitude sim golden)
 *    LOWG variant with a hot-day OAT (50.0°C) chosen so
 *    `computeDensityAltitudeFeet` returns 5594 ft, comfortably above
 *    C172's 5000 ft `maxDensityAltitudeFt` advisory (FAA AC 61-107B
 *    §3-1). Same world/stand/controllers/flight plan as [LOWG]; the
 *    sole distinguishing surface is the [WeatherObservation.oat] slot.
 *    Drives [xyz.easiersaid.twr.sim.G3aPilotReactiveDensityAltitudeTest]
 *    — apron-stay decline triggered by the static OAT (no mid-run
 *    weather-mutation hook, unlike the crosswind/tailwind axes; DA
 *    decline is recognised on the first pilot decision tick before the
 *    pilot transmits `Request(RequestTaxi)`). Closes the third
 *    pilot-reactive POH/AFH recognition axis as the sixth reactive-GA
 *    path (after the wind axes), and the first apron-side reactive
 *    branch — no go-around envelope, just decline.
 *  - [LOWG_TWO_AIRCRAFT] — fn-8.1 G1 foundation. Two-aircraft VFR
 *    circuit-training fixture at LOWG. Stand pair: `LOWG_STAND_1_POINT`
 *    + `LOWG_STAND_2_POINT` — adjacent GA gates authored in the LOWG
 *    `world-candidate.json` (chosen from world-candidate authoring; no
 *    speculative AIP claim — both stands are
 *    `direct_authored_geometry_with_reference_attrs` from the
 *    `NEW_Parking_Points` authoring pass). Drives G1
 *    ([xyz.easiersaid.twr.sim.G1TwoAircraftCircuitsTest]) and
 *    **G3a-react-multi-aircraft**
 *    ([xyz.easiersaid.twr.sim.G3aPilotReactiveMultiAircraftTest] —
 *    fn-28.5 multi-aircraft pilot-reactive go-around + controller-side
 *    `ARR-EXTEND-FOR-GA` + `ARR-TURN-BASE` sequencing of the trailing
 *    aircraft; three scenarios — crosswind GA + extend-downwind to B,
 *    tailwind GA + extend-downwind to B, GA-recovery via A's
 *    `Report(Downwind)` pattern-rejoin clearing belief → `TurnBase` to
 *    B fires same-cycle per .4's concrete cancel-output contract
 *    (round-10 Major 2) with the existing
 *    `SupersessionRelation(TurnBase, ExtendDownwind, ABANDON)` row
 *    dropping B's prior `ExtendDownwind` coordination — NO runway-
 *    vacate clause per round-8 Major 3; same fixture as G1 with the
 *    two-transition `world.aerodromes[LOWG].weather` authorship pattern
 *    layered over A; both aircraft C172 / Light wake category; B's
 *    `PilotDecisionTick` delayed by 2 sim-minutes via the G1 mission-
 *    start-offset recipe so B reaches downwind while A is on final
 *    under landing clearance — the conflict authoring that makes the
 *    multi-aircraft scenario reachable). Wake category lives on
 *    `AircraftState.type` (set at aircraft construction in the test);
 *    the fixture itself only carries filed plans + start points.
 *  - [LJMB] — single-aerodrome reference fixture (not a golden anchor;
 *    used for cross-aerodrome composition).
 *  - [LOWG_LJMB_VFR] — multi-aerodrome G2 anchor (cross-aerodrome
 *    transit). Drives G2
 *    ([xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest]).
 *  - [LOWG_LJMB_VFR_REACTIVE] — fn-28.7 G3b sim-golden variant of
 *    [LOWG_LJMB_VFR] with concrete OAT + QNH at BOTH aerodromes so the
 *    Transit-arrival reactive-GA recognition path can read LJMB weather
 *    without `mapNotNull`-projection drops. Wind initialised to a pure-
 *    headwind on LJMB runway 14 (140°M @ 10 kt) so the two-transition
 *    `world.aerodromes[LJMB].weather` mutation pattern is the sole
 *    driver of the LJMB-side recognition. Drives the seventh reactive-
 *    GA path (the first **cross-aerodrome** reactive-GA path; closes
 *    `D-PASS-g3b-react-cross-aerodrome-{crosswind,tailwind}`) via
 *    [xyz.easiersaid.twr.sim.G3bCrossAerodromeReactiveTest].
 */
object Fixtures {

    val LOWG: Fixture = Fixture(
        aerodromeId = AerodromeId("LOWG"),
        candidatePath = projectRoot().resolve("cad/airports/rendered/lowg/world-candidate.json"),
        standPointId = PointId("LOWG_STAND_1_POINT"),
        frequency = Frequency.unsafe("118.200"),
        // fn-28.1 (G3a-react-density-altitude foundation A, round-3 Major 4):
        // populate LOWG with concrete OAT + QNH so DA-touching scenarios
        // (fn-28.3's G3aPilotReactiveDensityAltitudeTest, future apron-DA
        // sim coverage) produce a non-null `DensityAltitudeInput` entry
        // through the `PilotWiring.buildPilotInput` projection. Pre-
        // fn-28.1 the fixture set `qnh = null` (no consumer needed it);
        // a `null` qnh causes the projection to OMIT the LOWG entry
        // (fail-closed), and downstream DA recognition fails to fire with
        // an unhelpful "no map entry" trace — making DA-touching sim
        // failures obscure. Concrete values surface DA recognition
        // failures with diagnostic-friendly inputs in the trace.
        //
        // Numeric provenance:
        //  - OAT = ISA(LOWG_elev) ≈ 12.79 °C
        //    LOWG elevation per AGENTS.md / world data ≈ 1115 ft;
        //    ISA(h_ft) = 15.0 - (h_ft / 1000) * 1.98 = 15.0 - 1.115 * 1.98
        //              = 12.7923 °C; rounded to 12.79 °C for the fixture
        //    literal. ISA value (not a "hot day") so circuit-training
        //    tests (G0 / G3a-trained / G3a-obstruction / G3a-react-
        //    crosswind / G3a-react-tailwind) running on this fixture are
        //    NOT spuriously DA-tripped. fn-28.3's hot-DA scenario will
        //    author ISA+35 °C ≈ 47.79 °C via the world-mutation hook
        //    pattern (mirrors fn-14.2's crosswind wind-shift authoring).
        //  - QNH = 1013 hPa (standard ISA QNH). Smart constructor
        //    `PressureSetting.QnhHpa.unsafe(1013)` is the Int-typed
        //    canonical hPa surface. fn-28's spec text mentions
        //    `PressureSetting.hPa(1013.25)` — the codebase's
        //    `QnhHpa(Int)` smart-constructor pins the 1013 hPa integer
        //    literal (the spec's `.25` decimal is below the smart-
        //    constructor's resolution; floor-rounded as the
        //    standard-pressure convention).
        weather = WeatherObservation(
            wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
            qnh = PressureSetting.QnhHpa.unsafe(1013),
            visibility = null,
            oat = Temperature.celsius(12.79),
        ),
        // LOWG (per manifest): tower handles ground duties on the same RT;
        // we model the operational reality with both roles on the same freq.
        controllerRoles = setOf(RoleName.GROUND, RoleName.TOWER),
        // Pass 11 (D-AUDIT.6 / D-AUDIT.10): file the plan via AFTN-style
        // event distribution. Pre-Pass-11 this was a `groundResponsibilities`
        // direct-injection cheat; the strip now arrives via
        // `SimEvent.FlightPlanFiled` at sim-start.
        flightPlans = mapOf(
            AircraftId("OE-ABC") to FiledPlan.Vfr(
                departureAerodrome = AerodromeId("LOWG"),
                destinationAerodrome = null, // local circuit training
                intent = AircraftIntent.Departing,
            ),
        ),
    )

    /**
     * fn-28.3 (G3a-react-density-altitude sim golden): LOWG variant with a
     * hot-day OAT that produces a density altitude **above** the C172's
     * 5000 ft `maxDensityAltitudeFt` advisory (FAA AC 61-107B §3-1). Same
     * world / stand / controllers / flight plan as [LOWG]; the **sole**
     * distinguishing surface is the OAT slot on
     * [WeatherObservation.oat] — every other weather field is identical
     * to the baseline [LOWG] fixture (wind 160°@8, QNH 1013 hPa, visibility
     * null). Keeping the surface-area diff to a single field pins the
     * test's "the DA recognition fired because of the OAT slot" causal
     * claim — a regression that produced DA decline off some other
     * weather field would not satisfy any other test that uses [LOWG]
     * (those tests run on `oat = 12.79°C` which yields DA below threshold).
     *
     * **Numeric provenance** (`computeDensityAltitudeFeet` reverse-derived;
     * pinned by the fn-28.3 test's R17 numerical assertion):
     *
     * Field elevation per LOWG world-candidate JSON: **1120 ft**
     * (`cad/airports/rendered/lowg/world-candidate.json:elevationFeet`).
     * The fn-28 round-3 Major 4 narrative quoted "1115 ft" from
     * AGENTS.md; the world-data authoritative value is 1120 ft. Both
     * round to the same ISA(elev) ≈ 12.78°C; either value yields a DA
     * comfortably above 5000 ft at OAT = 50°C.
     *
     * **OAT = 50.0°C** (concrete hot-day value chosen to comfortably
     * exceed C172's 5000 ft advisory by ≥ 500 ft, per fn-28.3 spec
     * "fixture's OAT is chosen so the function returns ≥ 5500 ft").
     * Derivation (formula in [xyz.easiersaid.twr.pilot.DensityAltitudeFormula]):
     *
     * ```
     * pressure_altitude_ft = 1120 + (1013.25 - 1013) * 30 = 1127.5
     * isa_temperature_c    = 15.0 - (1120 / 1000) * 1.98 = 12.7824
     * density_altitude_ft  = 1127.5 + 120 * (50.0 - 12.7824) = 5593.6
     * rounded → 5594 ft (≥ 5500, ≥ 5000 + 500 ft comfort margin)
     * ```
     *
     * The narrative anchor in fn-28's spec round-3 Major 4 ("47.8°C =
     * ISA+35°C at LOWG") yields ~5330 ft — above the 5000 ft threshold
     * but BELOW the spec's ≥5500 comfort floor. The spec explicitly
     * permits "OR use a computed-from-fixture value" so we lift the
     * OAT to 50°C; this is a concrete numeric (not prose) per round-3
     * Major 4 / R17 — the fn-28.3 test asserts on `computeDensityAltitudeFeet`'s
     * output (5594 ft), NOT on the prose ISA+35 framing.
     *
     * **QNH = 1013 hPa** (standard ISA QNH, same as baseline [LOWG]).
     * `PressureSetting.QnhHpa.unsafe(1013)`; the formula collapses
     * `(1013.25 - 1013) * 30 = 7.5 ft` so the QNH term is small but
     * non-zero — preserves the QNH path through the formula.
     *
     * **Wind = 160°@8** (light SE wind aligned with runway 16C; same as
     * baseline [LOWG]). DA decline is an apron-side decision; wind is
     * irrelevant to the recognition predicate, but a non-trivial wind
     * preserves the rest of the fixture's runtime behaviour (controller
     * runway selection, ATIS path) for downstream consumers.
     *
     * **No mutation hook**: unlike fn-14.2 / fn-15.2's two-transition
     * weather authorship pattern (where wind shifts in mid-run), DA
     * decline is recognised on the FIRST pilot decision tick — before
     * the pilot transmits `Request(RequestTaxi)`. The fixture's static
     * OAT is the sole driver; no per-tick world-hook is required.
     *
     * **Sibling tests** (DA recognition surface):
     *  - fn-28.3's `G3aPilotReactiveDensityAltitudeTest` is the **sole**
     *    consumer at fn-28 close. Future apron-DA scenarios (post-fn-28
     *    DA recovery flows, multi-aircraft DA cascades) reuse this
     *    fixture and override [WeatherObservation.oat] as needed via the
     *    same provenance shape.
     */
    val LOWG_HIGH_DA: Fixture = Fixture(
        aerodromeId = AerodromeId("LOWG"),
        candidatePath = projectRoot().resolve("cad/airports/rendered/lowg/world-candidate.json"),
        standPointId = PointId("LOWG_STAND_1_POINT"),
        frequency = Frequency.unsafe("118.200"),
        weather = WeatherObservation(
            wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
            qnh = PressureSetting.QnhHpa.unsafe(1013),
            visibility = null,
            // fn-28.3: hot-day OAT chosen so `computeDensityAltitudeFeet`
            // returns 5594 ft (> C172's 5000 ft `maxDensityAltitudeFt`
            // advisory by ≥ 500 ft). See KDoc above for the full
            // derivation; concrete numeric per round-3 Major 4 / R17.
            oat = Temperature.celsius(50.0),
        ),
        controllerRoles = setOf(RoleName.GROUND, RoleName.TOWER),
        flightPlans = mapOf(
            AircraftId("OE-ABC") to FiledPlan.Vfr(
                departureAerodrome = AerodromeId("LOWG"),
                destinationAerodrome = null, // local circuit training
                intent = AircraftIntent.Departing,
            ),
        ),
    )

    /**
     * fn-28.9 (G0 abort-takeoff sim golden — positive scenario,
     * pre-rotation): aliases [LOWG] verbatim (base scenario data ONLY).
     *
     * **Fixture surface** vs. [LOWG]:
     *  - **Identical** — same world / stand / controllers / flight plan /
     *    weather / runway selection. No `EngineFailureAt` is authored at
     *    fixture-build time (round-7 Minor 3 / round-11 Major 3): the
     *    `EngineFailureAt(t)` event is INJECTED DYNAMICALLY during the
     *    test's setup phase. The test observes the trace until
     *    `ClearedForTakeoff` is processed, then injects
     *    `EngineFailureAt(t = ClearedForTakeoff_time + 1ms)` via the
     *    `runUntilWithStateTraceAndInjection` post-step hook (which
     *    routes through `SimState.emit` for monotonic seq stamping; see
     *    `RunUntil.kt`'s [EventInjection] KDoc). Fixture is static-data-
     *    only; test setup is the dynamic-trace-observer.
     *
     * **Named for grep-ability** (vs. raw [LOWG] reuse): the two
     * abort-takeoff fixture aliases (`PRE_VR` + `POST_VR`) name the
     * SCENARIO INTENT, not a data difference. A future scenario that
     * needs a distinct world (e.g. different runway / aerodrome / weather
     * for abort) extends one of these aliases with the per-scenario
     * override field; today the aliases collapse to [LOWG] because the
     * scenario distinguisher lives in TEST METHOD setup (injected
     * `EngineFailureAt` timing), not in fixture data.
     *
     * **Sibling tests** (G0 abort-takeoff surface):
     *  - fn-28.9's `G0AbortTakeoffEngineFailureTest`'s POSITIVE method
     *    (pre-rotation engine failure → abort recognition fires →
     *    instant-stop on the runway) is the SOLE consumer at fn-28
     *    close. Three-layer pin: instant-stop same tick; never airborne;
     *    zero cognitive transmissions same tick.
     */
    val LOWG_ABORT_TAKEOFF_PRE_VR: Fixture = LOWG

    /**
     * fn-28.9 (G0 abort-takeoff sim golden — negative scenario,
     * post-rotation): aliases [LOWG] verbatim (base scenario data ONLY).
     *
     * **Fixture surface** vs. [LOWG] / [LOWG_ABORT_TAKEOFF_PRE_VR]:
     *  - **Identical** — same as the `PRE_VR` alias above. The
     *    distinguishing surface is the test method's injection timing:
     *    the negative scenario authors `EngineFailureAt(t)` AFTER the
     *    physics tick that crosses rotation speed, so the 4-check gate
     *    fails on the speed predicate (`speedMps >= rotationSpeedMps`)
     *    and abort recognition does NOT fire.
     *
     * **Negative scenario contract** (round-2 Major 7): the test ENDS
     * after asserting the abort gate did NOT fire — no further ticks
     * required, no recovery flow modelled. The fixture supports both
     * positive and negative scenarios because the scenario divergence
     * lives in the test method, not the fixture.
     *
     * **Sibling tests**:
     *  - fn-28.9's `G0AbortTakeoffEngineFailureTest`'s NEGATIVE method
     *    (post-rotation engine failure → abort recognition does NOT
     *    fire → test ends after gate-assertion).
     */
    val LOWG_ABORT_TAKEOFF_POST_VR: Fixture = LOWG

    /**
     * fn-8.1 (G1 foundation): two-aircraft VFR circuit-training fixture at LOWG.
     *
     * Both aircraft are local circuit traffic (VFR LOWG → LOWG) at adjacent GA
     * stands. Stand pair: `LOWG_STAND_1_POINT` and `LOWG_STAND_2_POINT` —
     * authored adjacently in the LOWG `world-candidate.json` (both gates,
     * `aircraftTypes = jets|turboprops`, both `direct_authored_geometry_with_reference_attrs`
     * from the NEW_Parking_Points authoring pass). Choice is from the
     * world-candidate authoring; not a speculative AIP claim.
     *
     * **Wake category lives on `AircraftState` / aircraft observation, not
     * on `FiledPlan`** — `FiledPlan.Vfr` carries departure/destination/runway/
     * intent only. The two-Light-category requirement (e.g. C172 / PA-28) is
     * enforced at aircraft construction in fn-8.2, where the
     * `AircraftState.type.wakeCategory` is set. This fixture only carries the
     * filed plans; its KDoc records the intended wake-category pairing.
     *
     * Frequencies, weather, controller roles mirror the single-aircraft G0
     * [LOWG] fixture (GROUND + TOWER on 118.200, light SE wind).
     */
    val LOWG_TWO_AIRCRAFT: Fixture = Fixture(
        aerodromeId = AerodromeId("LOWG"),
        candidatePath = projectRoot().resolve("cad/airports/rendered/lowg/world-candidate.json"),
        // standPointId is the legacy single-aircraft anchor; tests using this
        // multi-aircraft fixture should reach for requiredStartPoints() and
        // ignore standPointId. The field remains non-null because the Fixture
        // shape preserves the single-aircraft default for G0/G2 fixtures.
        standPointId = PointId("LOWG_STAND_1_POINT"),
        frequency = Frequency.unsafe("118.200"),
        weather = WeatherObservation(
            wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
            qnh = null,
            visibility = null,
        ),
        controllerRoles = setOf(RoleName.GROUND, RoleName.TOWER),
        // Two VFR circuit-training plans, both LOWG → LOWG. AircraftId values
        // sort lexicographically as ABC < DEF, so the loader's
        // `flightPlans.entries.sortedBy { it.key.value }` pass produces
        // `OE-ABC` first, then `OE-DEF` — deterministic seq-assignment.
        // The intended wake-category pairing for fn-8.2 is two Lights
        // (C172 / PA-28) — not enforced here because FiledPlan doesn't
        // carry wake category; enforced at AircraftState construction.
        flightPlans = mapOf(
            AircraftId("OE-ABC") to FiledPlan.Vfr(
                departureAerodrome = AerodromeId("LOWG"),
                destinationAerodrome = null,
                intent = AircraftIntent.Departing,
            ),
            AircraftId("OE-DEF") to FiledPlan.Vfr(
                departureAerodrome = AerodromeId("LOWG"),
                destinationAerodrome = null,
                intent = AircraftIntent.Departing,
            ),
        ),
        // Distinct, adjacent GA stands authored in the LOWG world-candidate.
        // Validation in `LoadedFixture.validate` enforces:
        // - both points exist in worldIndex.positions
        // - no flightPlan entry without a startPoints entry (and vice versa)
        // - no two aircraft sharing a start point.
        startPoints = mapOf(
            AircraftId("OE-ABC") to PointId("LOWG_STAND_1_POINT"),
            AircraftId("OE-DEF") to PointId("LOWG_STAND_2_POINT"),
        ),
    )

    val LJMB: Fixture = Fixture(
        aerodromeId = AerodromeId("LJMB"),
        candidatePath = projectRoot().resolve("cad/airports/rendered/ljmb/world-candidate.json"),
        // LJMB candidate stands reference taxiway points; the GA-1 start 1
        // stand's pointId is LJMB_TWY_A_17_02. Future tests may copy this
        // Fixture and override standPointId for their stand of choice.
        standPointId = PointId("LJMB_TWY_A_17_02"),
        frequency = Frequency.unsafe("119.205"),
        weather = WeatherObservation(
            wind = WindReport.Available(Wind.unsafe(directionDegrees = 140, speedKnots = 6)),
            qnh = null,
            visibility = null,
        ),
        // LJMB is controlled (TOWER + APP) during published hours per Slovenia
        // AIP AD 2.LJMB. G2 Phase A landed the roles block in the LJMB world-
        // candidate so this fixture loads cleanly.
        controllerRoles = setOf(RoleName.TOWER),
    )

    /**
     * G2 Phase A: cross-aerodrome VFR transit fixture. Both LOWG and LJMB are
     * staged; controllers staffed at LOWG_GROUND, LOWG_TOWER, LOWG_APPROACH,
     * LJMB_TOWER. A single VFR FiledPlan distributes via Pass 14
     * `AftnRouting.routeFiledPlan` to LOWG_GROUND (Owned) and LJMB_TOWER
     * (knownStrips).
     *
     * Per-aerodrome frequencies (multi-aerodrome cannot use a single-frequency
     * field): LOWG GND/TWR on 118.200, LOWG APP on 119.300, LJMB TOWER on 119.205.
     *
     * `standPointId` is the aircraft's start point at LOWG; `destinationStandPointId`
     * is the expected end point at LJMB. Both are taxiway-stand points per
     * the existing LOWG / LJMB authoring conventions.
     */
    val LOWG_LJMB_VFR: MultiAerodromeFixture = MultiAerodromeFixture(
        staffing = nonEmptyListOf(
            AerodromeStaffing(
                aerodromeId = AerodromeId("LOWG"),
                candidatePath = projectRoot().resolve("cad/airports/rendered/lowg/world-candidate.json"),
                frequencyByRole = mapOf(
                    RoleName.GROUND to Frequency.unsafe("118.200"),
                    RoleName.TOWER to Frequency.unsafe("118.200"),
                    RoleName.APPROACH to Frequency.unsafe("119.300"),
                ),
                weather = WeatherObservation(
                    wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
                    qnh = null,
                    visibility = null,
                ),
            ),
            AerodromeStaffing(
                aerodromeId = AerodromeId("LJMB"),
                candidatePath = projectRoot().resolve("cad/airports/rendered/ljmb/world-candidate.json"),
                frequencyByRole = mapOf(
                    RoleName.TOWER to Frequency.unsafe("119.205"),
                ),
                weather = WeatherObservation(
                    wind = WindReport.Available(Wind.unsafe(directionDegrees = 140, speedKnots = 6)),
                    qnh = null,
                    visibility = null,
                ),
            ),
        ),
        standPointId = PointId("LOWG_STAND_1_POINT"),
        destinationStandPointId = PointId("LJMB_TWY_A_17_02"),
        weatherByAerodrome = mapOf(
            AerodromeId("LOWG") to WeatherObservation(
                wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
                qnh = null,
                visibility = null,
            ),
            AerodromeId("LJMB") to WeatherObservation(
                wind = WindReport.Available(Wind.unsafe(directionDegrees = 140, speedKnots = 6)),
                qnh = null,
                visibility = null,
            ),
        ),
        flightPlans = mapOf(
            AircraftId("OE-XYZ") to FiledPlan.Vfr(
                departureAerodrome = AerodromeId("LOWG"),
                destinationAerodrome = AerodromeId("LJMB"),
                destinationRunway = xyz.easiersaid.twr.protocol.RunwayId("14"),
                intent = AircraftIntent.Transit,
            ),
        ),
    )

    /**
     * fn-28.7 (G3b sim golden — Transit-arrival reactive-GA): cross-
     * aerodrome variant of [LOWG_LJMB_VFR] with **concrete OAT + QNH**
     * at both LOWG and LJMB so the pilot's Transit-arrival reactive-GA
     * recognition path can read **LJMB**'s weather on the next
     * `PilotDecisionTick` without the `PilotWiring.buildPilotInput`
     * `mapNotNull` projection silently dropping the entry (fail-closed
     * on missing `wind`). LJMB's initial wind is a pure-headwind on
     * runway 14 (140°M @ 10 kt) so the two-transition
     * `world.aerodromes[LJMB].weather` mutation pattern is the sole
     * driver of the LJMB-side recognition.
     *
     * **Distinguishing surface vs [LOWG_LJMB_VFR]** (mirrors the
     * [LOWG_HIGH_DA] / [LOWG] discipline — keep the diff surface to the
     * single field that matters for the recognition under test):
     *  - **LJMB initial wind is `140°@10`** (pure headwind on runway 14),
     *    NOT `140°@6` — the test's two-transition hook authors a
     *    crosswind or tailwind shift past the C172 limits and then
     *    resets to this initial headwind once the post-GA recovery
     *    `Report(Downwind)` is on the wire. The 10 kt headwind value
     *    matches the G3a-react LOWG fixture's initial wind so the
     *    recovery-pattern arithmetic is identical across the G3a/G3b
     *    cousin tests (10 kt headwind = 0 kt crosswind component and
     *    AT the C172 10 kt tailwind advisory boundary — strict `>`
     *    recognition does not fire).
     *  - **LJMB QNH = 1013 hPa, LJMB OAT = ISA(elev) ≈ 13.27 °C** —
     *    same numerical-provenance discipline as [LOWG] (fn-28.1 round-3
     *    Major 4). LJMB elevation per world-candidate JSON is 876 ft;
     *    `ISA(876 ft) = 15.0 - 0.876 * 1.98 = 13.2655 °C`, rounded to
     *    13.27 °C for the fixture literal. ISA value so this fixture is
     *    NOT spuriously DA-tripped during the LJMB approach. The
     *    `PilotWiring` projection requires non-null `wind` AND non-null
     *    `qnh` for the DA-input projection to surface a non-null
     *    `DensityAltitudeInput` entry (fn-28.1's
     *    `densityAltitudeInputForMission` then narrows by mission
     *    destination); for fn-28.7 G3b we don't exercise the DA-decline
     *    recognition at LJMB but we keep the projection non-null so a
     *    future cross-aerodrome DA scenario could reuse this fixture
     *    without re-authoring weather.
     *  - **LOWG weather identical to [LOWG]** — the LOWG side of this
     *    fixture is the canonical [LOWG] weather (`160°@8`, QNH 1013,
     *    OAT 12.79). The G3b test does NOT exercise any LOWG-side
     *    wind-reactive recognition (the departure half is a normal
     *    Transit cruise); copying [LOWG]'s shape keeps the LOWG half of
     *    the cross-aerodrome run behaviourally identical to G2.
     *  - **`AircraftId("OE-XYZ")`** is reused (same as [LOWG_LJMB_VFR])
     *    so the routing recipients + `standPointId` shapes survive the
     *    fixture-load path unchanged.
     *
     * **Sibling tests** (Transit-arrival reactive-GA surface):
     *  - fn-28.7's `G3bCrossAerodromeReactiveTest` is the SOLE consumer
     *    at fn-28 close. Two `@Test` methods — crosswind axis (pure
     *    crosswind on LJMB runway 14, 20 kt > C172's 15 kt POH
     *    crosswind limit) + tailwind axis (pure tailwind on LJMB runway
     *    14, 15 kt > C172's 10 kt AFH-advisory). Both reuse this
     *    fixture; the recognition axis lives in the per-test hook, not
     *    the fixture.
     *  - The fn-28.6 unit-level `PilotTransitArrivalReactiveGoAroundTest`
     *    is the pilot-side composition pin; this sim fixture is the
     *    end-to-end sibling.
     */
    val LOWG_LJMB_VFR_REACTIVE: MultiAerodromeFixture = MultiAerodromeFixture(
        staffing = nonEmptyListOf(
            AerodromeStaffing(
                aerodromeId = AerodromeId("LOWG"),
                candidatePath = projectRoot().resolve("cad/airports/rendered/lowg/world-candidate.json"),
                frequencyByRole = mapOf(
                    RoleName.GROUND to Frequency.unsafe("118.200"),
                    RoleName.TOWER to Frequency.unsafe("118.200"),
                    RoleName.APPROACH to Frequency.unsafe("119.300"),
                ),
                // LOWG side — copy of [LOWG] (fn-28.1 round-3 Major 4
                // concrete OAT + QNH). G3b does not exercise any LOWG-
                // side wind-reactive recognition; this slot exists so
                // multi-aerodrome `staffing` is well-formed.
                weather = WeatherObservation(
                    wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
                    qnh = PressureSetting.QnhHpa.unsafe(1013),
                    visibility = null,
                    oat = Temperature.celsius(12.79),
                ),
            ),
            AerodromeStaffing(
                aerodromeId = AerodromeId("LJMB"),
                candidatePath = projectRoot().resolve("cad/airports/rendered/ljmb/world-candidate.json"),
                frequencyByRole = mapOf(
                    RoleName.TOWER to Frequency.unsafe("119.205"),
                ),
                // LJMB side — pure-headwind on runway 14 (140°M) at 10 kt.
                // 0 kt crosswind + 0 kt tailwind initially; the two-
                // transition hook is the sole driver of the LJMB-side
                // recognition. QNH = 1013 hPa (standard ISA). OAT =
                // ISA(876 ft elev) = 15.0 - 0.876 * 1.98 = 13.2655 °C →
                // 13.27 °C; ISA value so DA decline is NOT spuriously
                // tripped on LJMB approach.
                weather = WeatherObservation(
                    wind = WindReport.Available(Wind.unsafe(directionDegrees = 140, speedKnots = 10)),
                    qnh = PressureSetting.QnhHpa.unsafe(1013),
                    visibility = null,
                    oat = Temperature.celsius(13.27),
                ),
            ),
        ),
        standPointId = PointId("LOWG_STAND_1_POINT"),
        destinationStandPointId = PointId("LJMB_TWY_A_17_02"),
        weatherByAerodrome = mapOf(
            AerodromeId("LOWG") to WeatherObservation(
                wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
                qnh = PressureSetting.QnhHpa.unsafe(1013),
                visibility = null,
                oat = Temperature.celsius(12.79),
            ),
            AerodromeId("LJMB") to WeatherObservation(
                wind = WindReport.Available(Wind.unsafe(directionDegrees = 140, speedKnots = 10)),
                qnh = PressureSetting.QnhHpa.unsafe(1013),
                visibility = null,
                oat = Temperature.celsius(13.27),
            ),
        ),
        flightPlans = mapOf(
            AircraftId("OE-XYZ") to FiledPlan.Vfr(
                departureAerodrome = AerodromeId("LOWG"),
                destinationAerodrome = AerodromeId("LJMB"),
                destinationRunway = xyz.easiersaid.twr.protocol.RunwayId("14"),
                intent = AircraftIntent.Transit,
            ),
        ),
    )

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
