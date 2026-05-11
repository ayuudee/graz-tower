package xyz.easiersaid.twr.pilot

import arrow.core.None
import arrow.core.Some
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * fn-14.1 (G3a-react) — `windForMission` goal-by-goal resolution + the
 * singleton-fallback shape mirrored from `atisLetterForCallInbound`.
 *
 * Pins the deliberate **divergence from the ATIS helper** at the multi-
 * aerodrome ambiguity branch: this helper returns `null` (fail-closed)
 * rather than `error()` because the lookup runs every pilot decision
 * cycle and a crash would propagate to unrelated multi-aerodrome
 * scenarios. Multi-aerodrome G3b-react is `D-PASS-g3b-react-cross-
 * aerodrome-crosswind`.
 */
class WindForMissionTest {

    private val now0 = SimTime.ZERO
    private val rwy = RunwayId("09")

    private val lowg = AerodromeId("LOWG")
    private val ljmb = AerodromeId("LJMB")
    private val windLowg = WindReport.Available(Wind.unsafe(360, 12))
    private val windLjmb = WindReport.Available(Wind.unsafe(180, 8))

    private fun missionWith(goal: HighLevelGoal): PilotMission = PilotMission(
        goal = goal,
        // Minimal valid root with one primitive; the helper doesn't
        // inspect the tree.
        root = CompoundTask(
            name = TaskName.Circuit,
            children = listOf(PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL)),
        ),
        stepEnteredAt = now0,
        activeRunway = Some(RunwayAssignment(rwy, RunwayAssignmentSource.Filing)),
    )

    @Test
    fun `Transit destination — key lookup picks the destination aerodrome's wind`() {
        // Transit LOWG→LJMB; destination is LJMB. The helper must
        // resolve `weatherByAerodrome[ljmb]`, NOT singleton-fallback
        // (which would pick LOWG by lex order in a 2-entry map).
        val mission = missionWith(HighLevelGoal.Transit(destination = ljmb))
        val result = windForMission(
            mission,
            mapOf(lowg to windLowg, ljmb to windLjmb),
        )
        assertEquals(windLjmb, result, "Transit.destination → key lookup")
    }

    @Test
    fun `Departure destination — key lookup picks the destination aerodrome's wind`() {
        // Mirrors the ATIS helper's shape; rare in practice (the pilot
        // is unlikely to be on final approach during Departure) but
        // pin for symmetry.
        val mission = missionWith(HighLevelGoal.Departure(destination = ljmb))
        val result = windForMission(
            mission,
            mapOf(lowg to windLowg, ljmb to windLjmb),
        )
        assertEquals(windLjmb, result, "Departure.destination → key lookup")
    }

    @Test
    fun `Arrival — null fallback, never reads Arrival from (origin)`() {
        // Arrival.from is the ORIGIN aerodrome, not the landing one.
        // Using it would silently return the wrong wind. Mirrors the
        // ATIS helper.
        val mission = missionWith(HighLevelGoal.Arrival(from = lowg))
        // Single-entry map: singleton-fallback returns the singleton.
        val result = windForMission(mission, mapOf(ljmb to windLjmb))
        assertEquals(
            windLjmb,
            result,
            "Arrival → singleton-fallback; Arrival.from is NOT used as a key",
        )
    }

    @Test
    fun `CircuitTraining — singleton fallback (single-aerodrome case)`() {
        val mission = missionWith(
            HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        )
        val result = windForMission(mission, mapOf(lowg to windLowg))
        assertEquals(windLowg, result, "CircuitTraining + single-entry map → singleton")
    }

    @Test
    fun `CircuitTraining — empty map returns null (no wind report yet)`() {
        val mission = missionWith(
            HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        )
        assertNull(
            windForMission(mission, emptyMap()),
            "empty weatherByAerodrome → null fallback (no report yet)",
        )
    }

    @Test
    fun `CircuitTraining — multi-aerodrome map returns null (fail-closed, G3b-react deferment)`() {
        // Deliberate divergence from atisLetterForCallInbound: this
        // helper returns null instead of error()-ing. The lookup runs
        // per-cycle; a crash would propagate to unrelated multi-
        // aerodrome scenarios. Cross-aerodrome crosswind recognition
        // is `D-PASS-g3b-react-cross-aerodrome-crosswind`.
        val mission = missionWith(
            HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        )
        assertNull(
            windForMission(mission, mapOf(lowg to windLowg, ljmb to windLjmb)),
            "multi-aerodrome ambiguity in CircuitTraining → null (fail-closed; G3b-react deferred)",
        )
    }

    @Test
    fun `Transit with destination=null — singleton fallback (multi-aerodrome → null)`() {
        // Transit.destination is nullable in the protocol — a goal
        // constructed without a destination falls back to singleton-
        // fallback (mirrors the ATIS helper).
        val mission = missionWith(HighLevelGoal.Transit(destination = null))
        assertEquals(
            windLowg,
            windForMission(mission, mapOf(lowg to windLowg)),
            "Transit(destination=null) + single-entry → singleton",
        )
        assertNull(
            windForMission(mission, mapOf(lowg to windLowg, ljmb to windLjmb)),
            "Transit(destination=null) + multi-aerodrome → null",
        )
    }
}
