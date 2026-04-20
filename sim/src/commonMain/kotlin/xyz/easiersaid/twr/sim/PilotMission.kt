package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.SimTime

/**
 * Goal-driven pilot mission plan.
 *
 * A mission decomposes a [PilotGoal] into an ordered sequence of [MissionStep]s.
 * Each step knows when it's active, what to transmit, and when it's complete.
 * Instructions from ATC modify constraints within the current step (e.g., "extend
 * downwind" suppresses base turn), not rewrite the plan — except for explicit
 * replan triggers (go-around → awaiting ATC instruction).
 *
 * The pilot cognitive layer advances through steps; the physical layer (DefaultPilot)
 * handles kinematics (speed, altitude, waypoint following).
 */
data class PilotMission(
    val goal: xyz.easiersaid.twr.controller.PilotGoal,
    val steps: List<MissionStep>,
    val currentStepIndex: Int = 0,
    /** Instructions received from ATC that modify current step behaviour. */
    val activeConstraints: Set<ActiveConstraint> = emptySet(),
    /** Last circuit leg where a position report was made (suppress duplicates). */
    val lastReportedLeg: xyz.easiersaid.twr.core.world.LegName? = null,
    /** Whether initial contact has been made on the current frequency. */
    val contactedOnFrequency: Boolean = false,
    /** Timer for missing-clearance escalation (millis since step entered). */
    val stepEnteredAt: SimTime = SimTime.ZERO,
) {
    val currentStep: MissionStep? get() = steps.getOrNull(currentStepIndex)
    val isComplete: Boolean get() = currentStepIndex >= steps.size
}

/** An ATC instruction that modifies pilot behaviour without changing the mission plan. */
sealed interface ActiveConstraint {
    /** Extend downwind — suppress base turn until TurnBase received. */
    data object ExtendingDownwind : ActiveConstraint
    /** Orbiting as instructed. */
    data object Orbiting : ActiveConstraint
    /** Speed restriction in effect. */
    data class SpeedRestriction(val targetKnots: Int) : ActiveConstraint
}

/**
 * A single step in the mission plan.
 *
 * Steps are checked in order. Each step stays active until its completion condition
 * is met, then the mission advances to the next step. Some steps produce a
 * transmission when entered or when a condition is met.
 */
enum class MissionStep {
    // ── Ground (pre-departure) ──────────────────────────────────
    REQUEST_STARTUP,
    AWAIT_STARTUP_APPROVAL,
    REQUEST_TAXI,
    TAXI_TO_HOLDING,
    RUN_UP_CHECKS,
    REPORT_READY,
    AWAIT_LINE_UP,
    AWAIT_TAKEOFF_CLEARANCE,

    // ── Airborne (circuit) ──────────────────────────────────────
    FLY_DEPARTURE,           // upwind + crosswind
    FLY_DOWNWIND,
    REPORT_DOWNWIND,         // abeam threshold
    AWAIT_SEQUENCING,        // handle extend/orbit, escalation timer
    FLY_BASE,
    FLY_FINAL,
    AWAIT_LANDING_CLEARANCE, // escalation: query at 1nm, go-around at 0.5nm

    // ── Landing + post-landing ──────────────────────────────────
    LAND,
    REPORT_RUNWAY_VACATED,
    AWAIT_VACATE_INSTRUCTION,
    TAXI_TO_STAND,
    SHUTDOWN,

    // ── Special states ──────────────────────────────────────────
    GOING_AROUND,            // event-driven: notify tower, await instruction
    AWAITING_ATC_INSTRUCTION,// after go-around: wait for tower to re-sequence
}

/** Decompose a pilot goal into a mission step sequence. */
fun decomposeMission(goal: xyz.easiersaid.twr.controller.PilotGoal): List<MissionStep> = when (goal) {
    xyz.easiersaid.twr.controller.PilotGoal.DEPART -> listOf(
        MissionStep.REQUEST_STARTUP,
        MissionStep.AWAIT_STARTUP_APPROVAL,
        MissionStep.REQUEST_TAXI,
        MissionStep.TAXI_TO_HOLDING,
        MissionStep.RUN_UP_CHECKS,
        MissionStep.REPORT_READY,
        MissionStep.AWAIT_LINE_UP,
        MissionStep.AWAIT_TAKEOFF_CLEARANCE,
        MissionStep.FLY_DEPARTURE,
        // For DEPART, aircraft leaves the circuit after departure — no circuit steps.
        MissionStep.SHUTDOWN,
    )
    xyz.easiersaid.twr.controller.PilotGoal.ARRIVE -> listOf(
        // Arriving aircraft starts airborne — no ground pre-departure steps.
        MissionStep.FLY_DOWNWIND,
        MissionStep.REPORT_DOWNWIND,
        MissionStep.AWAIT_SEQUENCING,
        MissionStep.FLY_BASE,
        MissionStep.FLY_FINAL,
        MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,
        MissionStep.REPORT_RUNWAY_VACATED,
        MissionStep.AWAIT_VACATE_INSTRUCTION,
        MissionStep.TAXI_TO_STAND,
        MissionStep.SHUTDOWN,
    )
    xyz.easiersaid.twr.controller.PilotGoal.TOUCH_AND_GO -> listOf(
        // Full circuit from startup.
        MissionStep.REQUEST_STARTUP,
        MissionStep.AWAIT_STARTUP_APPROVAL,
        MissionStep.REQUEST_TAXI,
        MissionStep.TAXI_TO_HOLDING,
        MissionStep.RUN_UP_CHECKS,
        MissionStep.REPORT_READY,
        MissionStep.AWAIT_LINE_UP,
        MissionStep.AWAIT_TAKEOFF_CLEARANCE,
        MissionStep.FLY_DEPARTURE,
        MissionStep.FLY_DOWNWIND,
        MissionStep.REPORT_DOWNWIND,
        MissionStep.AWAIT_SEQUENCING,
        MissionStep.FLY_BASE,
        MissionStep.FLY_FINAL,
        MissionStep.AWAIT_LANDING_CLEARANCE,
        MissionStep.LAND,
        // Touch-and-go: after landing roll, back to circuit (loop handled externally).
        MissionStep.REPORT_RUNWAY_VACATED,
        MissionStep.AWAIT_VACATE_INSTRUCTION,
        MissionStep.TAXI_TO_STAND,
        MissionStep.SHUTDOWN,
    )
    xyz.easiersaid.twr.controller.PilotGoal.TRANSIT -> listOf(
        // Transit: simplified for now.
        MissionStep.FLY_DEPARTURE,
        MissionStep.SHUTDOWN,
    )
}

/** Create a fresh mission for an aircraft at a given starting phase. */
fun createMission(
    goal: xyz.easiersaid.twr.controller.PilotGoal,
    startPhase: PilotPhase,
    time: SimTime,
): PilotMission {
    val allSteps = decomposeMission(goal)
    // Skip steps that are already completed based on starting phase.
    val startIndex = when (startPhase) {
        is PilotPhase.AtStand -> 0
        is PilotPhase.Taxiing -> allSteps.indexOf(MissionStep.TAXI_TO_HOLDING).coerceAtLeast(0)
        is PilotPhase.HoldingShort -> allSteps.indexOf(MissionStep.REPORT_READY).coerceAtLeast(0)
        is PilotPhase.LinedUp -> allSteps.indexOf(MissionStep.AWAIT_TAKEOFF_CLEARANCE).coerceAtLeast(0)
        is PilotPhase.TakeoffRoll -> allSteps.indexOf(MissionStep.FLY_DEPARTURE).coerceAtLeast(0)
        is PilotPhase.Climbing -> allSteps.indexOf(MissionStep.FLY_DEPARTURE).coerceAtLeast(0)
        is PilotPhase.Crosswind -> allSteps.indexOf(MissionStep.FLY_DOWNWIND).coerceAtLeast(0)
        is PilotPhase.Downwind -> allSteps.indexOf(MissionStep.FLY_DOWNWIND).coerceAtLeast(0)
        is PilotPhase.Base -> allSteps.indexOf(MissionStep.FLY_BASE).coerceAtLeast(0)
        is PilotPhase.Final -> allSteps.indexOf(MissionStep.FLY_FINAL).coerceAtLeast(0)
        is PilotPhase.LandingRoll -> allSteps.indexOf(MissionStep.LAND).coerceAtLeast(0)
        is PilotPhase.Vacating -> allSteps.indexOf(MissionStep.REPORT_RUNWAY_VACATED).coerceAtLeast(0)
        is PilotPhase.ClearOfRunway -> allSteps.indexOf(MissionStep.TAXI_TO_STAND).coerceAtLeast(0)
        is PilotPhase.Parked -> allSteps.size // complete
    }
    return PilotMission(
        goal = goal,
        steps = allSteps,
        currentStepIndex = startIndex,
        stepEnteredAt = time,
    )
}
