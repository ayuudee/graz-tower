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
import xyz.easiersaid.twr.protocol.RoleName
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
 *    transition `state.weatherByAerodrome[LOWG]` mutation via
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
 *    `state.weatherByAerodrome[LOWG]` discipline as G3a-react-crosswind
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
 *  - [LOWG_TWO_AIRCRAFT] — fn-8.1 G1 foundation. Two-aircraft VFR
 *    circuit-training fixture at LOWG. Stand pair: `LOWG_STAND_1_POINT`
 *    + `LOWG_STAND_2_POINT` — adjacent GA gates authored in the LOWG
 *    `world-candidate.json` (chosen from world-candidate authoring; no
 *    speculative AIP claim — both stands are
 *    `direct_authored_geometry_with_reference_attrs` from the
 *    `NEW_Parking_Points` authoring pass). Drives G1
 *    ([xyz.easiersaid.twr.sim.G1TwoAircraftCircuitsTest]). Wake category
 *    lives on `AircraftState.type` (set at aircraft construction in the
 *    test); the fixture itself only carries filed plans + start points.
 *  - [LJMB] — single-aerodrome reference fixture (not a golden anchor;
 *    used for cross-aerodrome composition).
 *  - [LOWG_LJMB_VFR] — multi-aerodrome G2 anchor (cross-aerodrome
 *    transit). Drives G2
 *    ([xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest]).
 */
object Fixtures {

    val LOWG: Fixture = Fixture(
        aerodromeId = AerodromeId("LOWG"),
        candidatePath = projectRoot().resolve("cad/airports/rendered/lowg/world-candidate.json"),
        standPointId = PointId("LOWG_STAND_1_POINT"),
        frequency = Frequency.unsafe("118.200"),
        weather = WeatherObservation(
            wind = WindReport.Available(Wind.unsafe(directionDegrees = 160, speedKnots = 8)),
            qnh = null,
            visibility = null,
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
    // G2 Phase A scope note: `LjmbWorldCandidateValidationTest` (LJMB IFR SID
    // inventory mismatch — expects 9 SIDs, world-candidate publishes 5) is
    // pre-existing on master and explicitly out of G2 scope (G2 is VFR transit;
    // SIDs are IFR procedures). A future LJMB IFR pass closes that test.
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

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
