package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pass 13 (D-AUDIT.3 closure) — per-type RUN_UP_CHECKS duration wiring.
 *
 * `AircraftTypeSpec` pins doctrine values:
 *  - C172 run-up = 60 s (POH §4 mag-check / carb-heat / instrument scan).
 *  - B738 run-up = 600 s (FCOM NP cold-start: engine warmup, FMC entry,
 *    before-takeoff checklist).
 *
 * This spec proves the values flow through `PilotCognitive.isStepComplete`'s
 * `CompletionMode.TIMED` branch into `pilotCognitiveDecide`'s step
 * advancement. Without this row, a typo like
 * `aircraft.type.kinematics.climbSpeedMps` (wrong field) ships green —
 * `isStepComplete` is `private`, so the consumer-path is only observable
 * through `pilotCognitiveDecide`'s `updatedMission`.
 *
 * Real-job: each row exercises the per-type duration boundary at the
 * cognitive-decision layer.
 */
class RunUpDurationSpec {

    private fun aircraftAt(type: AircraftType): AircraftState = AircraftState(
        id = AircraftId("OE-ABC"),
        callsign = Callsign("OEABC"),
        position = Position(xMeters = 0.0, yMeters = 0.0),
        positionPoint = PointId("HOLD"),
        phase = PilotPhase.HoldingShort,
        type = type,
    )

    private fun missionAtRunUp(time: SimTime): PilotMission =
        // HoldingShort skips REQUEST_TAXI + TAXI_TO_HOLDING; next active step
        // is RUN_UP_CHECKS (CompletionMode.TIMED).
        createMission(
            goal = HighLevelGoal.Departure(),
            startPhase = PilotPhase.HoldingShort,
            time = time,
        )

    @Test
    fun `C172 RUN_UP_CHECKS does not complete before POH 60s duration`() {
        val ac = aircraftAt(AircraftType.C172)
        val mission = missionAtRunUp(SimTime.ZERO)
        // At 59 s — below the C172 POH 60 s threshold. Step should NOT advance.
        val now = SimTime.ofMillis(59_000)
        val decision = pilotCognitiveDecide(ac, mission, WorldIndex(), now)
        assertEquals(
            MissionStep.RUN_UP_CHECKS,
            decision.updatedMission.currentTask?.step,
            "C172 RUN_UP_CHECKS at t=59s must remain active (POH §4 = 60 s)",
        )
    }

    @Test
    fun `C172 RUN_UP_CHECKS completes after POH 60s duration`() {
        val ac = aircraftAt(AircraftType.C172)
        val mission = missionAtRunUp(SimTime.ZERO)
        // At 61 s — above the C172 POH 60 s threshold. Step should advance.
        val now = SimTime.ofMillis(61_000)
        val decision = pilotCognitiveDecide(ac, mission, WorldIndex(), now)
        assertNotEquals(
            MissionStep.RUN_UP_CHECKS,
            decision.updatedMission.currentTask?.step,
            "C172 RUN_UP_CHECKS at t=61s must advance (POH §4 = 60 s)",
        )
    }

    @Test
    fun `B738 RUN_UP_CHECKS does not complete at the C172 60s mark (FCOM 600s)`() {
        val ac = aircraftAt(AircraftType.B738)
        val mission = missionAtRunUp(SimTime.ZERO)
        // At 61 s — past C172 threshold but well below B738 FCOM 600 s.
        // Step must REMAIN active. This row proves per-type wiring: a
        // hardcoded 60_000L would advance B738 here too.
        val now = SimTime.ofMillis(61_000)
        val decision = pilotCognitiveDecide(ac, mission, WorldIndex(), now)
        assertEquals(
            MissionStep.RUN_UP_CHECKS,
            decision.updatedMission.currentTask?.step,
            "B738 RUN_UP_CHECKS at t=61s must remain active (FCOM NP = 600 s) — " +
                "proves per-type wiring (not hardcoded C172 60s)",
        )
    }

    @Test
    fun `B738 RUN_UP_CHECKS completes after FCOM 600s duration`() {
        val ac = aircraftAt(AircraftType.B738)
        val mission = missionAtRunUp(SimTime.ZERO)
        // At 601 s — above the B738 FCOM 600 s threshold. Step should advance.
        val now = SimTime.ofMillis(601_000)
        val decision = pilotCognitiveDecide(ac, mission, WorldIndex(), now)
        assertNotEquals(
            MissionStep.RUN_UP_CHECKS,
            decision.updatedMission.currentTask?.step,
            "B738 RUN_UP_CHECKS at t=601s must advance (FCOM NP = 600 s)",
        )
    }
}
