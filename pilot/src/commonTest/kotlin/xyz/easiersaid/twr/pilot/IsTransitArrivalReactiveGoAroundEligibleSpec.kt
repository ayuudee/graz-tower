package xyz.easiersaid.twr.pilot

import arrow.core.Some
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * fn-28.6 (R18 round-4 Major 1 + round-11 Major 1):
 * [isTransitArrivalReactiveGoAroundEligible] named-guard mission-shape
 * enumeration. Sibling of [IsDensityAltitudeDeclineEligibleSpec] — same
 * "every-step-row" test discipline, but for the Transit-arrival reactive-GA
 * dispatch guard rather than the DA-decline eligibility.
 *
 * **Predicate parts** (verified row-by-row below):
 *  1. `mission.goal is HighLevelGoal.Transit`
 *  2. Active primitive's step in `{FLY_FINAL, REPORT_FINAL,
 *      AWAIT_LANDING_CLEARANCE, LAND}` AND that primitive is a direct
 *     child of the Transit compound (no nested Circuit / GoAround wrapper)
 *  3. `mission.activeRunway is Some`
 *  4. `aircraft.phase == PilotPhase.Final`
 */
class IsTransitArrivalReactiveGoAroundEligibleSpec {

    private val now0 = SimTime.ZERO
    private val ac = AircraftId("OE-ABC")
    private val runway27 = RunwayId("27")
    private val ljmb = AerodromeId("LJMB")

    private fun aircraftOnFinal(phase: PilotPhase = PilotPhase.Final): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = PointId("P"),
        altitudeM = 200.0,
        phase = phase,
        type = AircraftType.C172,
    )

    /**
     * Transit mission with the canonical "arrival-primitives as direct
     * children of the Transit compound" shape — mirrors
     * `planMission(HighLevelGoal.Transit(...), ifr = false)` layout.
     *
     * The leading primitive of `step` is positioned as the first incomplete
     * child of the Transit compound (so it's the active primitive). Preceding
     * primitives are marked `completed = true` so `currentTask?.step` picks
     * up the requested step.
     */
    private fun transitMission(
        step: MissionStep,
        activeRunway: RunwayId? = runway27,
    ): PilotMission {
        // Build a Transit compound with the active-step primitive at the
        // front (all preceding ones marked complete via the
        // arrival-primitive subset). For the eligibility guard the only
        // structural signal is `currentTask?.step` + the absence of a
        // nested compound at activeCompound() — so a minimal "single
        // active primitive directly under Transit" tree is sufficient.
        return PilotMission(
            goal = HighLevelGoal.Transit(destination = ljmb),
            root = CompoundTask(
                name = TaskName.Transit,
                children = listOf(
                    PrimitiveTask(step, CompletionMode.PHYSICAL),
                ),
            ),
            stepEnteredAt = now0,
            activeRunway = activeRunway?.let {
                Some(RunwayAssignment(it, RunwayAssignmentSource.Filing))
            } ?: arrow.core.None,
        )
    }

    @Test
    fun `Transit + FLY_FINAL + Final-phase + runway-assigned is eligible`() {
        assertTrue(
            isTransitArrivalReactiveGoAroundEligible(
                aircraft = aircraftOnFinal(),
                mission = transitMission(MissionStep.FLY_FINAL),
            ),
            "canonical Transit-arrival reactive-GA shape: Transit + FLY_FINAL + Final + runway",
        )
    }

    @Test
    fun `REPORT_FINAL AWAIT_LANDING_CLEARANCE LAND are all eligible`() {
        listOf(
            MissionStep.REPORT_FINAL,
            MissionStep.AWAIT_LANDING_CLEARANCE,
            MissionStep.LAND,
        ).forEach { step ->
            assertTrue(
                isTransitArrivalReactiveGoAroundEligible(
                    aircraft = aircraftOnFinal(),
                    mission = transitMission(step),
                ),
                "wind-reactive eligible step $step is eligible for Transit-arrival GA",
            )
        }
    }

    @Test
    fun `non-Transit goal is NOT eligible — Departure Arrival CircuitTraining fail`() {
        val arrivalMission = PilotMission(
            goal = HighLevelGoal.Arrival(),
            root = CompoundTask(
                name = TaskName.Arrive,
                children = listOf(PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL)),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
        )
        assertFalse(
            isTransitArrivalReactiveGoAroundEligible(aircraftOnFinal(), arrivalMission),
            "Arrival mission shape is covered by `isReactiveGoAroundEligible`, NOT this Transit-only guard",
        )

        val circuitTrainingMission = PilotMission(
            goal = HighLevelGoal.CircuitTraining(
                outcomes = listOf(CircuitOutcome.FullStop),
            ),
            root = CompoundTask(
                name = TaskName.CircuitTraining,
                children = listOf(
                    CompoundTask(
                        name = TaskName.Circuit,
                        children = listOf(PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL)),
                    ),
                ),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
        )
        assertFalse(
            isTransitArrivalReactiveGoAroundEligible(aircraftOnFinal(), circuitTrainingMission),
            "CircuitTraining is covered by `isReactiveGoAroundEligible`, NOT this guard",
        )
    }

    @Test
    fun `non-Final phase is NOT eligible — fails phase guard`() {
        listOf(
            PilotPhase.AtStand,
            PilotPhase.TakeoffRoll,
            PilotPhase.Climbing,
            PilotPhase.LandingRoll,
        ).forEach { phase ->
            assertFalse(
                isTransitArrivalReactiveGoAroundEligible(
                    aircraft = aircraftOnFinal(phase),
                    mission = transitMission(MissionStep.FLY_FINAL),
                ),
                "phase $phase is NOT Final; Transit-arrival reactive-GA gates on Final descent",
            )
        }
    }

    @Test
    fun `no active runway is NOT eligible — filed plan must resolve destinationRunway`() {
        assertFalse(
            isTransitArrivalReactiveGoAroundEligible(
                aircraft = aircraftOnFinal(),
                mission = transitMission(MissionStep.FLY_FINAL, activeRunway = null),
            ),
            "without activeRunway, Transit-arrival reactive-GA fails closed (no runway → no wind component)",
        )
    }

    @Test
    fun `non-arrival steps are NOT eligible — REQUEST_TAXI FLY_DEPARTURE etc fail`() {
        listOf(
            MissionStep.REQUEST_TAXI,
            MissionStep.TAXI_TO_HOLDING,
            MissionStep.RUN_UP_CHECKS,
            MissionStep.AWAIT_TAKEOFF_CLEARANCE,
            MissionStep.FLY_DEPARTURE,
            MissionStep.FLY_DOWNWIND,
            MissionStep.REPORT_DOWNWIND,
            MissionStep.FLY_BASE,
            MissionStep.REPORT_BASE,
            MissionStep.AWAIT_SEQUENCING,
            MissionStep.GOING_AROUND,
        ).forEach { step ->
            assertFalse(
                isTransitArrivalReactiveGoAroundEligible(
                    aircraft = aircraftOnFinal(),
                    mission = transitMission(step),
                ),
                "step $step is NOT in the Transit-arrival wind-reactive eligible step set",
            )
        }
    }

    @Test
    fun `post-suffix-replace shape is NOT eligible — no-refire invariant`() {
        // After a Transit-arrival reactive GA, the suffix replacement
        // produces `[goAroundTask(), circuitTask(), groundArrivalTask()]`
        // inside the Transit compound. The active primitive is now inside
        // a nested compound (GoAround → GOING_AROUND first), so
        // `activeCompound()` returns non-null → guard fails → recognition
        // does NOT re-fire.
        val postGaMission = PilotMission(
            goal = HighLevelGoal.Transit(destination = ljmb),
            root = CompoundTask(
                name = TaskName.Transit,
                children = listOf(
                    goAroundTask(),
                    circuitTask(),
                    groundArrivalTask(),
                ),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
        )
        assertFalse(
            isTransitArrivalReactiveGoAroundEligible(aircraftOnFinal(), postGaMission),
            "post-suffix-replace shape has activeCompound() non-null — guard fails → no re-fire",
        )
    }

    @Test
    fun `goal=Transit + root name != Transit is NOT eligible — codex round-1 fix`() {
        // The goal/root invariant is type-system-permitted but planner-
        // broken: a `PilotMission(goal = Transit, root = CompoundTask(
        // TaskName.Arrive, ...))` is malformed. Pre-codex-round-1 the
        // guard checked only `goal is Transit` + `activeCompound() == null`
        // — a malformed mission would PASS, and the Transit GA dispatch
        // fork would run a Transit-shape rewrite on an Arrive tree.
        // Codex round-1 caught this Major / 100% confidence; the fix
        // adds an explicit root-name gate. This row pins the fix so a
        // regression that drops the root-name check fails here.
        val malformedMission = PilotMission(
            goal = HighLevelGoal.Transit(destination = ljmb),
            root = CompoundTask(
                name = TaskName.Arrive, // wrong root name for Transit goal
                children = listOf(PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL)),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
        )
        assertFalse(
            isTransitArrivalReactiveGoAroundEligible(aircraftOnFinal(), malformedMission),
            "goal=Transit + root=Arrive is malformed; guard refuses — codex round-1 fix",
        )

        // Also pin the same row with other non-Transit root names — the
        // gate is on TaskName.Transit specifically, not "anything except
        // Arrive."
        listOf(
            TaskName.Depart,
            TaskName.Circuit,
            TaskName.CircuitTraining,
            TaskName.CircuitAfterGoAround,
            TaskName.TouchAndGo,
        ).forEach { rootName ->
            val mission = PilotMission(
                goal = HighLevelGoal.Transit(destination = ljmb),
                root = CompoundTask(
                    name = rootName,
                    children = listOf(PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL)),
                ),
                stepEnteredAt = now0,
                activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
            )
            assertFalse(
                isTransitArrivalReactiveGoAroundEligible(aircraftOnFinal(), mission),
                "goal=Transit + root=$rootName must NOT pass the Transit-arrival guard",
            )
        }
    }

    @Test
    fun `null currentTask returns false — fail-closed on no active primitive`() {
        val completedMission = PilotMission(
            goal = HighLevelGoal.Transit(destination = ljmb),
            root = CompoundTask(
                name = TaskName.Transit,
                children = listOf(
                    PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL, completed = true),
                ),
            ),
            stepEnteredAt = now0,
            activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
        )
        assertFalse(
            isTransitArrivalReactiveGoAroundEligible(aircraftOnFinal(), completedMission),
            "fail-closed when no active primitive (currentTask is null)",
        )
    }
}
