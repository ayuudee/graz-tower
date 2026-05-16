package xyz.easiersaid.twr.pilot

import arrow.core.Some
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.Feet
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Temperature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * fn-28.1 (G3a-react-density-altitude foundation A) — `densityAltitudeInputForMission`
 * goal-by-goal resolution.
 *
 * **Round-1 codex impl-review fix (Major, 75% confidence)**: pins the
 * deliberate **divergence from [windForMission]** at every goal branch.
 * `windForMission` resolves via `g.destination` for `Transit` /
 * `Departure`; `densityAltitudeInputForMission` does NOT, because
 * DA-decline is a **departure-side, pre-taxi decision** — the relevant
 * aerodrome is the apron the pilot is AT, not the destination they are
 * going TO. Mirroring would pick the wrong source aerodrome for
 * multi-aerodrome departures (LOWG → LJMB would resolve LJMB's DA
 * inputs, masking the foundation defect).
 *
 * **Singleton fallback** matches the [windForMission] shape — single-
 * aerodrome scenarios (fn-28.3's G3aPilotReactiveDensityAltitudeTest
 * golden — LOWG-only fixture) resolve correctly through this branch.
 * Multi-aerodrome DA recognition is filed as
 * `D-PASS-g3b-react-density-altitude` (fn-28.2-or-later work).
 *
 * Round-1 fix evidence cross-reference:
 * `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt`
 * (`densityAltitudeInputForMission` KDoc).
 */
class DensityAltitudeInputForMissionTest {

    private val now0 = SimTime.ZERO
    private val rwy = RunwayId("09")

    private val lowg = AerodromeId("LOWG")
    private val ljmb = AerodromeId("LJMB")

    private val daLowg = DensityAltitudeInput(
        oat = Temperature.celsius(12.79),
        qnh = PressureSetting.QnhHpa.unsafe(1013),
        fieldElevation = Feet(1115),
    )
    private val daLjmb = DensityAltitudeInput(
        // LJMB elev ≈ 876 ft per AGENTS.md/world; ISA(876) ≈ 13.27 °C.
        oat = Temperature.celsius(13.27),
        qnh = PressureSetting.QnhHpa.unsafe(1013),
        fieldElevation = Feet(876),
    )

    private fun missionWith(
        goal: HighLevelGoal,
        filedPlan: FiledPlan? = null,
    ): PilotMission = PilotMission(
        goal = goal,
        // Minimal valid root with one primitive; the helper doesn't
        // inspect the tree.
        root = CompoundTask(
            name = TaskName.Circuit,
            children = listOf(PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL)),
        ),
        stepEnteredAt = now0,
        activeRunway = Some(RunwayAssignment(rwy, RunwayAssignmentSource.Filing)),
        filedPlan = filedPlan?.let { Some(it) } ?: arrow.core.None,
    )

    @Test
    fun `Departure with destination — does NOT resolve via destination (round-1 fix)`() {
        // Critical regression-pin test (the round-1 codex finding):
        // LOWG → LJMB departure. The pilot is at LOWG's apron, deciding
        // whether DA permits departure. `Departure.destination = LJMB`
        // MUST NOT pick LJMB's DA inputs. Multi-aerodrome map (≥2
        // entries) → singleton fallback bails → null.
        val mission = missionWith(HighLevelGoal.Departure(destination = ljmb))
        val result = densityAltitudeInputForMission(
            mission,
            mapOf(lowg to daLowg, ljmb to daLjmb),
        )
        assertNull(
            result,
            "Departure with multi-aerodrome map: fail-closed (D-PASS-g3b-react-density-altitude). " +
                "MUST NOT use g.destination — that would resolve LJMB instead of the departure aerodrome LOWG.",
        )
    }

    @Test
    fun `Transit with destination — does NOT resolve via destination (round-1 fix)`() {
        // Symmetric pin: Transit.destination is the destination, not
        // the apron. Same fail-closed behavior as Departure for the
        // multi-aerodrome case.
        val mission = missionWith(HighLevelGoal.Transit(destination = ljmb))
        val result = densityAltitudeInputForMission(
            mission,
            mapOf(lowg to daLowg, ljmb to daLjmb),
        )
        assertNull(
            result,
            "Transit with multi-aerodrome map: fail-closed; MUST NOT use g.destination",
        )
    }

    @Test
    fun `Arrival — null fallback regardless of g_from`() {
        // Arrival.from is the ORIGIN; not used here. Multi-aerodrome
        // map fails closed.
        val mission = missionWith(HighLevelGoal.Arrival(from = lowg))
        val result = densityAltitudeInputForMission(
            mission,
            mapOf(lowg to daLowg, ljmb to daLjmb),
        )
        assertNull(result, "Arrival + multi-aerodrome → null")
    }

    @Test
    fun `CircuitTraining single-aerodrome — singleton fallback returns the entry`() {
        // The fn-28.3 golden scenario: C172 at LOWG circuit training,
        // single-aerodrome map. Singleton fallback resolves correctly.
        val mission = missionWith(
            HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        )
        val result = densityAltitudeInputForMission(mission, mapOf(lowg to daLowg))
        assertEquals(
            daLowg,
            result,
            "CircuitTraining + single-entry → singleton fallback returns LOWG entry",
        )
    }

    @Test
    fun `Departure single-aerodrome — singleton fallback returns the entry`() {
        // Single-aerodrome Departure (rare but valid: local departure
        // with no filed destination). Singleton fallback resolves.
        val mission = missionWith(HighLevelGoal.Departure(destination = null))
        val result = densityAltitudeInputForMission(mission, mapOf(lowg to daLowg))
        assertEquals(
            daLowg,
            result,
            "Departure(destination=null) + single-entry → singleton",
        )
    }

    @Test
    fun `empty map returns null (no entries to fall back to)`() {
        val mission = missionWith(
            HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        )
        assertNull(
            densityAltitudeInputForMission(mission, emptyMap()),
            "empty densityAltitudeInputsByAerodrome → null",
        )
    }

    @Test
    fun `multi-aerodrome map always returns null regardless of goal — no filed plan`() {
        // Exhaustive: each goal variant + a 2-entry map MUST return
        // null when NO filed plan exists (no goal-keyed path resolves;
        // singleton fallback requires exactly 1 entry).
        val departureGoal = HighLevelGoal.Departure(destination = ljmb)
        val transitGoal = HighLevelGoal.Transit(destination = ljmb)
        val arrivalGoal = HighLevelGoal.Arrival(from = lowg)
        val circuitGoal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop))

        val map = mapOf(lowg to daLowg, ljmb to daLjmb)

        assertNull(densityAltitudeInputForMission(missionWith(departureGoal), map))
        assertNull(densityAltitudeInputForMission(missionWith(transitGoal), map))
        assertNull(densityAltitudeInputForMission(missionWith(arrivalGoal), map))
        assertNull(densityAltitudeInputForMission(missionWith(circuitGoal), map))
    }

    // ── fn-28.2 (round-9 Major 2 / round-1 codex-review fold-in):
    // filed-plan departure-aerodrome resolution.
    //
    // The acceptance contract requires THREE rows here:
    //  (a) filed plan with LOWG departure + LOWG entry → returns LOWG input.
    //  (b) no filed plan + singleton LOWG entry → returns LOWG input
    //      (covered in `Departure single-aerodrome` / `CircuitTraining
    //      single-aerodrome` above).
    //  (c) no filed plan + multi-aerodrome map → returns null + recognition
    //      skips (covered in `multi-aerodrome map always returns null`
    //      above).

    @Test
    fun `filed plan LOWG departure + LOWG entry — resolves via filed plan`() {
        // Round-9 Major 2 contract: filed-plan departure-aerodrome is
        // the canonical lookup key for DA-decline. The mission has a
        // multi-aerodrome map; without filed-plan logic this would fall
        // through to the ambiguity null. WITH filed-plan logic, the
        // helper returns LOWG's DA input.
        val filedPlan = FiledPlan.Vfr(
            departureAerodrome = lowg,
            destinationAerodrome = ljmb,
            intent = AircraftIntent.Departing,
        )
        val mission = missionWith(
            HighLevelGoal.Departure(destination = ljmb),
            filedPlan = filedPlan,
        )
        val result = densityAltitudeInputForMission(
            mission,
            mapOf(lowg to daLowg, ljmb to daLjmb),
        )
        assertEquals(
            daLowg,
            result,
            "filed plan with LOWG departure: MUST resolve LOWG's DA input even with multi-aerodrome map " +
                "(round-9 Major 2 contract — departure-aerodrome is the canonical lookup)",
        )
    }

    @Test
    fun `filed plan departure missing from map — fail-closed (no singleton-fallback rescue)`() {
        // When the filed plan names a departure aerodrome that's NOT in
        // the projection map (e.g. PilotWiring dropped LOWG due to null
        // OAT/QNH), the helper MUST return null — NOT fall back to
        // singleton when LJMB happens to be the only other entry. The
        // filed plan is the source of truth; a disagreement is a
        // recognition-fail event, not a "best effort" path.
        val filedPlan = FiledPlan.Vfr(
            departureAerodrome = lowg,
            destinationAerodrome = ljmb,
            intent = AircraftIntent.Departing,
        )
        val mission = missionWith(
            HighLevelGoal.Departure(destination = ljmb),
            filedPlan = filedPlan,
        )
        // Map has only LJMB — LOWG (filed departure) is missing.
        val result = densityAltitudeInputForMission(
            mission,
            mapOf(ljmb to daLjmb),
        )
        assertNull(
            result,
            "filed-plan departure missing from map → null (fail-closed; NO singleton-fallback rescue)",
        )
    }

    @Test
    fun `filed plan LOWG departure preempts singleton even when map has one entry`() {
        // When filed plan IS present, the helper takes the filed path
        // unconditionally — singleton fallback only fires when no filed
        // plan exists. Test: filed plan says LOWG, map has only LJMB.
        // Result MUST be null (the filed plan is the source of truth;
        // singleton would silently resolve to the wrong aerodrome's DA
        // — exactly the round-1 sibling-helper-shape mistake this helper
        // exists to prevent).
        val filedPlan = FiledPlan.Vfr(
            departureAerodrome = lowg,
            destinationAerodrome = null,
            intent = AircraftIntent.Departing,
        )
        val mission = missionWith(
            HighLevelGoal.Departure(destination = null),
            filedPlan = filedPlan,
        )
        val result = densityAltitudeInputForMission(mission, mapOf(ljmb to daLjmb))
        assertNull(
            result,
            "filed plan with LOWG departure + map containing only LJMB → null; " +
                "MUST NOT fall back to singleton (the singleton would silently resolve to the wrong aerodrome)",
        )
    }
}
