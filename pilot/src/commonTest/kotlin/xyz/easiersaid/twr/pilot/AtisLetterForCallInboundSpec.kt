package xyz.easiersaid.twr.pilot

import arrow.core.Some
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * G2 Phase C — `atisLetterForCallInbound` table.
 *
 * The helper is private; rows access via `pilotCognitiveDecide` at
 * `MissionStep.CALL_INBOUND` and observe the resulting `InitialContact.atisCode`
 * (or the loud `error()` for the multi-entry non-Transit case).
 *
 * Four contracts:
 *  1. Transit + multi-entry → goal-keyed letter.
 *  2. Non-Transit + single-entry → singleton letter (G0 regression-pin).
 *  3. Non-Transit + multi-entry → loud `error()` with goal-class + key-set in message.
 *  4. Empty map → null letter (existing semantics preserved).
 */
class AtisLetterForCallInboundSpec {

    private val LOWG = AerodromeId("LOWG")
    private val LJMB = AerodromeId("LJMB")
    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ofMillis(0)

    private fun atis(letter: Char, ad: AerodromeId, runway: String) = Atis(
        letter = letter,
        aerodrome = ad,
        configuration = RunwayConfiguration(
            arrivals = listOf(RunwayId(runway)),
            departures = listOf(RunwayId(runway)),
        ),
        wind = Wind.unsafe(160, 8),
        qnh = null,
        visibility = null,
        generatedAt = now0,
    )

    private fun aircraft(): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
        positionPoint = PointId("HOLD"),
        phase = PilotPhase.HoldingShort,
    )

    private fun missionAtCallInbound(goal: HighLevelGoal): PilotMission = PilotMission(
        goal = goal,
        root = CompoundTask(
            name = TaskName.ArrivalJoin,
            children = listOf(
                PrimitiveTask(MissionStep.CALL_INBOUND, CompletionMode.REPORTED),
            ),
        ),
        stepEnteredAt = now0,
        pendingInitialContactRole = Some(RoleName.TOWER),
    )

    @Test
    fun `Transit with multi-entry resolves goal-keyed letter`() {
        val mission = missionAtCallInbound(HighLevelGoal.Transit(destination = LJMB))
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(),
            mission = mission,
            worldIndex = WorldIndex(),
            now = now0,
            atisByAerodrome = mapOf(LOWG to atis('A', LOWG, "16C"), LJMB to atis('B', LJMB, "14")),
        )
        val ic = decision.transmissions.filterIsInstance<InitialContact>().firstOrNull()
        assertEquals('B', ic?.atisCode, "Transit goal must look up by destination aerodrome")
    }

    @Test
    fun `Transit destination missing from atis map returns null letter (no error)`() {
        // Pins the contract: when goal-derived destination is set but the
        // ATIS for that destination has not yet been published, the helper
        // returns null silently rather than firing the loud-error branch
        // (which is reserved for "destination null AND multi-entry").
        // A real cockpit pilot in this state would tune the destination
        // ATIS frequency before contact; the simulator just propagates
        // null until the ATIS publishes.
        val mission = missionAtCallInbound(HighLevelGoal.Transit(destination = LJMB))
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(),
            mission = mission,
            worldIndex = WorldIndex(),
            now = now0,
            atisByAerodrome = mapOf(LOWG to atis('A', LOWG, "16C")),  // destination LJMB absent
        )
        val ic = decision.transmissions.filterIsInstance<InitialContact>().firstOrNull()
        assertEquals(null, ic?.atisCode,
            "Destination ATIS missing from map → null letter, no error")
    }

    @Test
    fun `non-Transit with single-entry resolves the singleton letter (G0 regression pin)`() {
        // CircuitTraining at LOWG, single ATIS — the singleton fallback must
        // continue to return the letter. This is the path G0's LowgGoldenTest
        // exercises; the Phase C tightening must not break it.
        val mission = missionAtCallInbound(
            HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        )
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(),
            mission = mission,
            worldIndex = WorldIndex(),
            now = now0,
            atisByAerodrome = mapOf(LOWG to atis('A', LOWG, "16C")),
        )
        val ic = decision.transmissions.filterIsInstance<InitialContact>().firstOrNull()
        assertEquals('A', ic?.atisCode, "Singleton fallback must preserve G0 behaviour")
    }

    @Test
    fun `non-Transit with multi-entry errors loudly with goal-class and key-set in message`() {
        val mission = missionAtCallInbound(HighLevelGoal.Arrival())
        val thrown = assertFailsWith<IllegalStateException> {
            pilotCognitiveDecide(
                aircraft = aircraft(),
                mission = mission,
                worldIndex = WorldIndex(),
                now = now0,
                atisByAerodrome = mapOf(LOWG to atis('A', LOWG, "16C"), LJMB to atis('B', LJMB, "14")),
            )
        }
        assertTrue(
            thrown.message?.contains("Arrival") == true,
            "Diagnostic must name the goal class. Got: ${thrown.message}",
        )
        assertTrue(
            thrown.message?.contains("LOWG") == true && thrown.message?.contains("LJMB") == true,
            "Diagnostic must name the aerodrome key set. Got: ${thrown.message}",
        )
    }

    @Test
    fun `empty map returns null letter (preserves existing empty-ATIS semantics)`() {
        val mission = missionAtCallInbound(
            HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        )
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(),
            mission = mission,
            worldIndex = WorldIndex(),
            now = now0,
            atisByAerodrome = emptyMap(),
        )
        val ic = decision.transmissions.filterIsInstance<InitialContact>().firstOrNull()
        assertEquals(null, ic?.atisCode,
            "Empty ATIS map remains a null letter (no error, no spurious value)")
    }
}
