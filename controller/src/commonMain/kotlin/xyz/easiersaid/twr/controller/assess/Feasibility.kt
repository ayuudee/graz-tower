package xyz.easiersaid.twr.controller.assess

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.IncreaseSpeedTo
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.ReduceSpeedTo
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.TurnBase

/**
 * Property of an instruction: is it coherent for this aircraft's state?
 *
 * Distinct from [xyz.easiersaid.twr.controller.bdi.RuleGuard] which gates
 * whether a *rule* should fire. Feasibility gates whether an *instruction*
 * is physically/procedurally valid once selected. Checked by the arbitrator
 * post-selection, pre-emission.
 *
 * See design doc §1.5 (2026-04-19-approach-sequencing.md).
 */
sealed interface Feasibility {
    data object Feasible : Feasibility
    data class Infeasible(val reason: String) : Feasibility
}

/**
 * Evaluate whether [instruction] is feasible given the current view and beliefs.
 *
 * Specific predicates per instruction type. Returns [Feasibility.Feasible] for
 * all types without an explicit predicate.
 */
@Suppress("UnusedParameter") // beliefs consumed by future sequence-aware predicates
fun checkFeasibility(
    instruction: AtcInstruction,
    aircraft: AircraftObservation,
    view: ControllerView,
    beliefs: BeliefState,
): Feasibility = when (instruction) {
    is ClearedToLand -> checkLandingFeasibility(aircraft, view)
    is TurnBase -> checkTurnBaseFeasibility(aircraft, view)
    is xyz.easiersaid.twr.protocol.GoAround, is xyz.easiersaid.twr.protocol.BreakOff ->
        if (aircraft.onGround) Feasibility.Infeasible("Cannot go around / break off — aircraft is on the ground")
        else Feasibility.Feasible
    is MaintainSpeed -> checkSpeedFeasibility(instruction.speed, aircraft)
    is ReduceSpeedTo -> checkSpeedFeasibility(instruction.speed, aircraft)
    is IncreaseSpeedTo -> checkSpeedFeasibility(instruction.speed, aircraft)
    else -> Feasibility.Feasible
}

/**
 * Landing clearance feasibility: aircraft must be on final approach or inside FAF.
 *
 * Tracker #38: no minimum final distance for landing clearance. Landing clearance
 * is only coherent when the aircraft is on the final approach leg (or an approach
 * procedure entity). Issuing ClearedToLand to an aircraft on downwind or base is
 * physically impossible (they can't comply from there).
 */
private fun checkLandingFeasibility(
    aircraft: AircraftObservation,
    view: ControllerView,
): Feasibility {
    val legs = view.worldIndex.circuitLegsByPoint[aircraft.position] ?: emptySet()
    val onFinal = LegName.FINAL in legs
    val onApproach = aircraft.entities.any {
        it is xyz.easiersaid.twr.core.world.EntityRef.ApproachRef
    }
    return if (onFinal || onApproach) Feasibility.Feasible
    else Feasibility.Infeasible("Aircraft not on final approach (position: ${aircraft.position})")
}

/**
 * TurnBase feasibility: aircraft must be on the downwind leg.
 *
 * Tracker #37: ARR-TURN-BASE fires from base leg — should only fire from downwind.
 * The existing rule guard checks `OnCircuitLeg(BASE)` which is the *trigger* position
 * (aircraft has reached base). But the *instruction* TurnBase only makes sense if
 * the aircraft is still on downwind (telling an aircraft already on base to turn base
 * is redundant/confusing). This feasibility check catches the case where the guard
 * fires late.
 */
/**
 * Speed instruction feasibility: target speed must be within a reasonable airspeed
 * envelope. Aircraft-type-specific speed bands are a future refinement (P5-D11 in
 * tracker); for now we use a general VFR/IFR envelope.
 *
 * The APP speed ladder (250kt/25nm → 210/15 → 180/8 → 160/4) is enforced by the
 * APP procedure rules, not by feasibility — feasibility checks whether the aircraft
 * *can* comply, not whether the instruction is operationally appropriate at this range.
 */
@Suppress("UnusedParameter") // aircraft consumed when type-specific speed bands are available
private fun checkSpeedFeasibility(
    speed: Speed,
    aircraft: AircraftObservation,
): Feasibility {
    val knots = when (speed) {
        is xyz.easiersaid.twr.protocol.Speed.InKnots -> speed.knots.value
        is xyz.easiersaid.twr.protocol.Speed.InMach -> return Feasibility.Feasible // Mach = high-altitude, always feasible
    }
    // General envelope: 60kt (stall margin for light singles) to 250kt (below FL100 limit).
    // Aircraft-type bands refine this when type data is available.
    return if (knots in 60..250) Feasibility.Feasible
    else Feasibility.Infeasible("Speed ${knots}kt outside feasible envelope (60–250kt)")
}

private fun checkTurnBaseFeasibility(
    aircraft: AircraftObservation,
    view: ControllerView,
): Feasibility {
    val legs = view.worldIndex.circuitLegsByPoint[aircraft.position] ?: emptySet()
    return if (LegName.DOWNWIND in legs || LegName.BASE in legs) Feasibility.Feasible
    else Feasibility.Infeasible("Aircraft not on downwind or base leg (position: ${aircraft.position})")
}
