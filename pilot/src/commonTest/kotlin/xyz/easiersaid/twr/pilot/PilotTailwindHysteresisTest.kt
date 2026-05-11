package xyz.easiersaid.twr.pilot

import arrow.core.Some
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.pilot.observe.PilotEvent
import xyz.easiersaid.twr.pilot.observe.derivePilotEvent
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * fn-15.1 (G3a-react-tailwind R10) — hysteresis via mission-tree rewrite.
 *
 * Sibling of fn-14.1's `PilotCrosswindHysteresisTest`. Distinct from
 * fn-12.2 (where `pendingAtcGoAroundFrom` flag was needed because the ATC
 * instruction arrived asynchronously) and fn-13.1 (where
 * `continueApproachIssuedThisAttempt` witness was needed because CONTINUE
 * APPROACH does NOT rewrite the mission tree). G3a-react-tailwind's
 * hysteresis is **free**: the rewrite to `CircuitAfterGoAround` takes
 * `currentStep` out of the `WIND_REACTIVE_ELIGIBLE_STEPS` set, so the
 * second decision cycle with the same wind state produces zero events.
 *
 * Two-cycle scenario:
 *  1. Cycle 1: aircraft on FLY_FINAL, wind 15 kt dead tailwind, AFH 10 kt.
 *     derivePilotEvent → TailwindLimitExceeded fires.
 *     applyTailwindGoAround → mission rewritten to CircuitAfterGoAround,
 *     active step in the rewrite is GOING_AROUND.
 *  2. Cycle 2: aircraft still on Final (next pilot tick, before kinematic
 *     advance), wind unchanged. derivePilotEvent → null because
 *     `currentStep == GOING_AROUND` is not in
 *     {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}.
 *
 * The pin protects against a regression that (a) widens the
 * `WIND_REACTIVE_ELIGIBLE_STEPS` set to include GOING_AROUND or (b)
 * breaks the subtree rewrite so that `currentStep` stays in the eligible
 * set.
 */
class PilotTailwindHysteresisTest {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO
    private val type = AircraftType.C172
    private val runway27 = RunwayId("27")

    private fun aircraft(): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = PointId("P"),
        altitudeM = 200.0,
        phase = PilotPhase.Final,
        type = type,
    )

    private fun startingMission(): PilotMission = PilotMission(
        goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        root = CompoundTask(
            name = TaskName.CircuitTraining,
            children = listOf(circuitTask()),
        ),
        stepEnteredAt = now0,
        hasClearance = false,
        activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
    )

    /**
     * Advance the mission's currentTask (the leftmost incomplete primitive)
     * to a target step by marking everything up to and including the prior
     * primitives complete. Used in the cycle-1 setup to land the mission
     * on FLY_FINAL.
     */
    private fun advanceTo(mission: PilotMission, target: MissionStep): PilotMission {
        var m = mission
        // Cap at ~20 advances to avoid infinite loops on a malformed tree.
        repeat(20) {
            val step = m.currentTask?.step
            if (step == target) return m
            if (step == null) return m
            m = m.copy(root = m.root.markComplete(step))
        }
        return m
    }

    private fun availableWind(directionDegrees: Int, speedKnots: Int): WindReport =
        WindReport.Available(Wind.unsafe(directionDegrees, speedKnots))

    @Test
    fun `cycle 1 fires event + rewrites tree, cycle 2 returns null because currentStep is outside the recognition set`() {
        val initial = advanceTo(startingMission(), MissionStep.FLY_FINAL)
        check(initial.currentTask?.step == MissionStep.FLY_FINAL) {
            "test setup precondition: mission advanced to FLY_FINAL; got ${initial.currentTask?.step}"
        }
        // Wind 15 kt dead tailwind vs C172's 10 kt AFH advisory.
        // Runway 27 (heading 270°) + wind 090°M = relative 180° = dead tailwind.
        val wind = availableWind(directionDegrees = 90, speedKnots = 15)

        // Cycle 1: event fires.
        val cycle1Event = derivePilotEvent(aircraft(), initial, wind)
        assertTrue(
            cycle1Event is PilotEvent.TailwindLimitExceeded,
            "cycle 1: 15 kt tailwind > 10 kt AFH advisory must fire TailwindLimitExceeded; got $cycle1Event",
        )
        // Apply the GA — mission tree rewrite + reset.
        val gaResult = applyTailwindGoAround(cycle1Event, initial, aircraft(), now0)
        val postRewriteMission = gaResult.mission

        // The active step after rewrite must be GOING_AROUND (the
        // leftmost incomplete leaf in CircuitAfterGoAround's GoAround
        // subtree).
        val postRewriteStep = postRewriteMission.currentTask?.step
        check(postRewriteStep == MissionStep.GOING_AROUND) {
            "post-rewrite active step expected GOING_AROUND; got $postRewriteStep"
        }

        // Cycle 2: same wind, post-rewrite mission. derivePilotEvent
        // must return null — GOING_AROUND is not in the
        // WIND_REACTIVE_ELIGIBLE_STEPS set.
        val cycle2Event = derivePilotEvent(aircraft(), postRewriteMission, wind)
        assertNull(
            cycle2Event,
            "cycle 2: hysteresis via tree-rewrite — currentStep=GOING_AROUND is outside the eligible " +
                "set so no event fires even though wind still exceeds the advisory; got $cycle2Event",
        )
    }
}
