package xyz.easiersaid.twr.pilot

import arrow.core.None
import arrow.core.Some
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * G2 Phase C — `isPhysicallyComplete`'s FLY_DEPARTURE arm.
 *
 * The arm is private; rows exercise it via `pilotCognitiveDecide` and observe
 * step advancement. Six contracts:
 *  1. Transit + at REP + Climbing → step completes (Phase C's new arm).
 *  2. Transit + not at REP → step does NOT complete.
 *  3. Transit + transitContactRep == None → does NOT complete (proves
 *     `Option == Some(_)` evaluates false on None regardless of positionPoint).
 *  4. Departure + at REP-equal point but not Climbing → does NOT complete via
 *     the new arm (the `transitAtRep` guard requires `goal is Transit`).
 *  5. Departure + Climbing + no DOWNWIND mapping → DOES complete via
 *     existing `(isDeparting && Climbing)` arm. Regression-pin against any
 *     refactor that changes the goal-class predicate (e.g. accidentally
 *     widening to `goal is Transit` would break Departure lift-off).
 *  6. CircuitTraining + DOWNWIND-leg waypoint → DOES complete via existing
 *     `LegName.DOWNWIND in legs` arm. Regression-pin against accidental
 *     subsumption of the existing leg-membership arm.
 */
class IsPhysicallyCompleteFlyDepartureSpec {

    private val LJMB = AerodromeId("LJMB")
    private val OSMOT = PointId("LJMB_FIX_OSMOT")
    private val OTHER = PointId("LJMB_FIX_LAPNA")
    private val ac = AircraftId("OE-XYZ")
    private val now0 = SimTime.ofMillis(0)

    /** Mission whose currentTask is FLY_DEPARTURE. */
    private fun transitMissionAtFlyDeparture(
        transitContactRep: arrow.core.Option<PointId>,
    ): PilotMission = PilotMission(
        goal = HighLevelGoal.Transit(destination = LJMB),
        root = CompoundTask(
            name = TaskName.Transit,
            children = listOf(
                PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
            ),
        ),
        stepEnteredAt = now0,
        transitContactRep = transitContactRep,
    )

    private fun aircraft(positionPoint: PointId, phase: PilotPhase): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEXYZ"),
        position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
        positionPoint = positionPoint,
        phase = phase,
    )

    @Test
    fun `Transit at REP advances FLY_DEPARTURE`() {
        val mission = transitMissionAtFlyDeparture(Some(OSMOT))
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(OSMOT, PilotPhase.Climbing),
            mission = mission,
            worldIndex = WorldIndex(),
            now = now0,
        )
        assertEquals(true, decision.updatedMission.isComplete,
            "Aircraft at the REP must complete FLY_DEPARTURE for a Transit mission")
    }

    @Test
    fun `Transit not at REP does not advance FLY_DEPARTURE`() {
        val mission = transitMissionAtFlyDeparture(Some(OSMOT))
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(OTHER, PilotPhase.Climbing),
            mission = mission,
            worldIndex = WorldIndex(),
            now = now0,
        )
        assertEquals(false, decision.updatedMission.isComplete,
            "Aircraft at a different point must not complete FLY_DEPARTURE")
    }

    @Test
    fun `Transit with None transitContactRep does not advance FLY_DEPARTURE even at any point`() {
        // Pre-resolution tick: the planner has not yet written the slice.
        // Option == Some(_) returns false on None, so the cognitive layer
        // does not prematurely complete the step regardless of where the
        // aircraft happens to be. Order-of-operations correctness pin.
        val mission = transitMissionAtFlyDeparture(None)
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(OSMOT, PilotPhase.Climbing),
            mission = mission,
            worldIndex = WorldIndex(),
            now = now0,
        )
        assertEquals(false, decision.updatedMission.isComplete,
            "transitContactRep = None must NOT match any aircraft.positionPoint")
    }

    @Test
    fun `Departure does not subsume Transit's at-REP arm`() {
        // Departure mission with transitContactRep populated (shouldn't
        // happen in production, but pins type-level isolation): the
        // `transitAtRep` guard requires `goal is Transit`. Departure's
        // own `isDeparting && Climbing` arm is the only way it can
        // complete here — and we deliberately put aircraft in Final phase
        // so neither arm fires.
        val mission = PilotMission(
            goal = HighLevelGoal.Departure(destination = LJMB),
            root = CompoundTask(
                name = TaskName.Depart,
                children = listOf(
                    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
                ),
            ),
            stepEnteredAt = now0,
            transitContactRep = Some(OSMOT),
        )
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(OSMOT, PilotPhase.Final),
            mission = mission,
            worldIndex = WorldIndex(),
            now = now0,
        )
        assertEquals(false, decision.updatedMission.isComplete,
            "transitAtRep guard must not fire for a Departure goal")
    }

    @Test
    fun `Departure + Climbing + no DOWNWIND mapping advances FLY_DEPARTURE (existing isDeparting arm)`() {
        // Pins the existing `(isDeparting && Climbing)` arm — a regression
        // that changes `mission.goal is HighLevelGoal.Departure` to e.g.
        // `goal is HighLevelGoal.Transit` would break Departure lift-off
        // and pass every other Phase C test silently.
        val mission = PilotMission(
            goal = HighLevelGoal.Departure(destination = LJMB),
            root = CompoundTask(
                name = TaskName.Depart,
                children = listOf(
                    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
                ),
            ),
            stepEnteredAt = now0,
        )
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(PointId("AWAY_FROM_CIRCUIT"), PilotPhase.Climbing),
            mission = mission,
            worldIndex = WorldIndex(),  // no DOWNWIND mapping
            now = now0,
        )
        assertEquals(true, decision.updatedMission.isComplete,
            "Departure goal climbing-out from a non-circuit point must complete via " +
                "the existing isDeparting && Climbing arm")
    }

    @Test
    fun `CircuitTraining advances FLY_DEPARTURE on DOWNWIND-leg waypoint (existing arm)`() {
        // Regression-pin: the existing `LegName.DOWNWIND in legs` arm must
        // continue to fire for circuit-training missions. A WorldIndex with
        // the aircraft's positionPoint mapped to DOWNWIND triggers it.
        val downwindPoint = PointId("CIRCUIT_DOWNWIND_1")
        val mission = PilotMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 1, fullStopOnLast = true),
            root = CompoundTask(
                name = TaskName.Circuit,
                children = listOf(
                    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
                ),
            ),
            stepEnteredAt = now0,
        )
        val worldIndex = WorldIndex(
            circuitLegsByPoint = mapOf(downwindPoint to setOf(LegName.DOWNWIND)),
        )
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(downwindPoint, PilotPhase.Climbing),
            mission = mission,
            worldIndex = worldIndex,
            now = now0,
        )
        assertEquals(true, decision.updatedMission.isComplete,
            "Existing DOWNWIND-leg arm must continue to fire for CircuitTraining")
        assertNotEquals(decision.updatedMission, mission,
            "Mission must advance — proves the existing arm wasn't broken by Phase C")
    }
}
