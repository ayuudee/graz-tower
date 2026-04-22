package xyz.easiersaid.twr.protocol

import arrow.core.Either
import arrow.core.left
import arrow.core.right

// ── Flight plan ─────────────────────────────────────────────────────
//
// An IFR pilot's filed flight plan, as progressively amended by ATC
// clearance. The FPL lists WHERE the pilot wants to go; the ClearanceState
// tracks HOW MUCH of the route ATC has cleared.
//
// The pilot files the FPL; ATC clears it (possibly amended); the pilot
// follows the clearance. Amendments are applied via [amendFpl], a total
// pure function over the AtcInstruction sealed hierarchy.

data class FlightPlan(
    /** Departure aerodrome (always known at filing time). */
    val departureAerodrome: AerodromeId,
    /** Destination aerodrome (always known at filing time). */
    val arrivalAerodrome: AerodromeId,
    /** IFR alternate — drives hold-vs-divert after missed approach. */
    val alternateAerodrome: AerodromeId? = null,
    /** Requested cruising level as filed. ATC may clear a different level. */
    val requestedLevel: Level,
    /** Filed en-route fixes (the route between SID exit and STAR entry). */
    val enRouteWaypoints: List<FixId>,
    /** Progressive clearance state — advances as ATC issues clearances. */
    val clearance: ClearanceState = ClearanceState.Uncleaned,
)

// ── Clearance state progression ─────────────────────────────────────
//
// Sealed hierarchy makes illegal states unrepresentable:
//   Uncleaned → EnRouteClearance → ApproachClearance
// Cannot get approach clearance without en-route clearance first.

sealed interface ClearanceState {
    /** Filed but not yet cleared by ATC. */
    data object Uncleaned : ClearanceState

    /** Cleared with a route and clearance limit. */
    data class EnRouteClearance(
        val clearanceLimit: FixId,
        val departureRunway: RunwayId,
        val sid: SidId? = null,
        val star: StarId? = null,
        val clearedLevel: Level? = null,
    ) : ClearanceState

    /** Cleared for approach — approach type and arrival runway are non-null. */
    data class ApproachClearance(
        val clearanceLimit: FixId,
        val departureRunway: RunwayId,
        val sid: SidId? = null,
        val star: StarId? = null,
        val clearedLevel: Level? = null,
        val approachType: ApproachType,
        val arrivalRunway: RunwayId,
    ) : ClearanceState
}

// ── FPL amendment errors ────────────────────────────────────────────

sealed interface AmendmentError {
    /** Cannot apply this clearance in the current state (e.g., approach before en-route). */
    data class InvalidTransition(val from: ClearanceState, val instruction: AtcInstruction) : AmendmentError

    /** ProceedDirect to a fix not on the filed route. */
    data class FixNotOnRoute(val fix: FixId) : AmendmentError
}

// ── FPL amendment function ──────────────────────────────────────────

/**
 * Amend a flight plan based on an ATC instruction.
 *
 * Total over [AtcInstruction]: every instruction type is explicitly handled.
 * Most instructions have no FPL effect (readbacks, reports, frequency changes,
 * speed, etc.) and return the FPL unchanged. The interesting cases are route
 * clearances, approach clearances, and direct-to amendments.
 *
 * FPL amendments are distinct from [RouteOverride] (vectors/holds, which
 * temporarily suspend FPL-based routing) and [ActiveConstraint] (speed
 * restrictions, which modify how the pilot follows the route).
 */
fun amendFpl(
    fpl: FlightPlan,
    instruction: AtcInstruction,
): Either<AmendmentError, FlightPlan> {
    return when (instruction) {

    // ── Route clearance: advance to EnRouteClearance ────────────────
    is ClearedTo -> {
        val sid = when (val r = instruction.route) {
            is RouteSpec.ViaSid -> r.sid
            else -> (fpl.clearance as? ClearanceState.EnRouteClearance)?.sid
                ?: (fpl.clearance as? ClearanceState.ApproachClearance)?.sid
        }
        val star = when (val r = instruction.route) {
            is RouteSpec.ViaStar -> r.star
            else -> (fpl.clearance as? ClearanceState.EnRouteClearance)?.star
                ?: (fpl.clearance as? ClearanceState.ApproachClearance)?.star
        }
        val depRwy = when (val c = fpl.clearance) {
            is ClearanceState.Uncleaned -> null // runway not yet assigned — will come with LUAW/CTOT
            is ClearanceState.EnRouteClearance -> c.departureRunway
            is ClearanceState.ApproachClearance -> c.departureRunway
        }
        // ClearedTo can be issued at any clearance state (initial or re-clear).
        // If no departure runway yet (Uncleaned), leave it unset — the runway
        // assignment comes from LineUpAndWait/ClearedForTakeoff, not ClearedTo.
        // For now, require a departure runway on the clearance if uncleaned.
        if (depRwy == null) {
            return AmendmentError.InvalidTransition(fpl.clearance, instruction).left()
        }
        val clearedLevel = (fpl.clearance as? ClearanceState.EnRouteClearance)?.clearedLevel
            ?: (fpl.clearance as? ClearanceState.ApproachClearance)?.clearedLevel
        fpl.copy(clearance = ClearanceState.EnRouteClearance(
            clearanceLimit = instruction.clearanceLimit,
            departureRunway = depRwy,
            sid = sid,
            star = star,
            clearedLevel = clearedLevel,
        )).right()
    }

    // ── Approach clearance: advance to ApproachClearance ────────────
    is ClearedApproach -> when (val c = fpl.clearance) {
        is ClearanceState.Uncleaned ->
            AmendmentError.InvalidTransition(c, instruction).left()
        is ClearanceState.EnRouteClearance -> fpl.copy(clearance = ClearanceState.ApproachClearance(
            clearanceLimit = c.clearanceLimit,
            departureRunway = c.departureRunway,
            sid = c.sid,
            star = c.star,
            clearedLevel = c.clearedLevel,
            approachType = instruction.approachType,
            arrivalRunway = instruction.runway,
        )).right()
        is ClearanceState.ApproachClearance -> fpl.copy(clearance = c.copy(
            approachType = instruction.approachType,
            arrivalRunway = instruction.runway,
        )).right()
    }

    // ── Direct-to: truncate en-route waypoints ──────────────────────
    is ProceedDirect -> {
        val idx = fpl.enRouteWaypoints.indexOf(instruction.fix)
        if (idx < 0) return AmendmentError.FixNotOnRoute(instruction.fix).left()
        fpl.copy(enRouteWaypoints = fpl.enRouteWaypoints.subList(idx, fpl.enRouteWaypoints.size)).right()
    }

    // ── Level amendments: update cleared level on clearance state ───
    is ClimbTo -> fpl.withClearedLevel(instruction.level)
    is DescendTo -> fpl.withClearedLevel(instruction.level)
    is MaintainLevel -> fpl.withClearedLevel(instruction.level)
    is ExpediteClimb -> fpl.withClearedLevel(instruction.level)
    is ExpediteDescend -> fpl.withClearedLevel(instruction.level)

    // ── Level instructions that update cleared level ──────────────
    is DescendWhenReady -> fpl.withClearedLevel(instruction.level)

    // ── Instructions with no FPL effect ─────────────────────────────
    // Category interfaces — leaf types with FPL effects are matched above.
    is Clearance -> fpl.right()
    is GroundInstruction -> fpl.right()
    is RunwayInstruction -> fpl.right()
    is RouteInstruction -> fpl.right()
    is VectorInstruction -> fpl.right()
    is LevelInstruction -> fpl.right()
    is SpeedInstruction -> fpl.right()
    is ApproachInstruction -> fpl.right()
    is ReportInstruction -> fpl.right()
    is FrequencyInstruction -> fpl.right()
    is SurveillanceInstruction -> fpl.right()
    is SequencingInstruction -> fpl.right()
    is AerodromeInstruction -> fpl.right()
    is EmergencyInstruction -> fpl.right()
    // Cancel clearance: regress clearance state.
    is CancelClearance -> when (val c = fpl.clearance) {
        is ClearanceState.Uncleaned -> fpl.right() // nothing to cancel
        is ClearanceState.ApproachClearance -> fpl.copy(
            clearance = ClearanceState.EnRouteClearance(
                clearanceLimit = c.clearanceLimit,
                departureRunway = c.departureRunway,
                sid = c.sid, star = c.star, clearedLevel = c.clearedLevel,
            ),
        ).right()
        is ClearanceState.EnRouteClearance -> fpl.copy(
            clearance = ClearanceState.Uncleaned,
        ).right()
    }

    // Uncategorised instructions (SetPressure, Disregard, etc.)
    is SetPressure -> fpl.right()
    is RemainOutsideControlledAirspace -> fpl.right()
    is Disregard -> fpl.right()
    is AvoidArea -> fpl.right()
    is AvoidLevel -> fpl.right()
}
}

/** Helper: update the cleared level on the current clearance state. */
private fun FlightPlan.withClearedLevel(level: Level): Either<AmendmentError, FlightPlan> =
    when (val c = clearance) {
        is ClearanceState.Uncleaned -> this.right() // no clearance yet — level instruction has no FPL effect
        is ClearanceState.EnRouteClearance -> copy(clearance = c.copy(clearedLevel = level)).right()
        is ClearanceState.ApproachClearance -> copy(clearance = c.copy(clearedLevel = level)).right()
    }
