package xyz.easiersaid.twr.protocol

import arrow.core.Either
import arrow.core.left
import arrow.core.right

/**
 * AFTN routing address — the pair `(aerodromeId, role)` that identifies a
 * controller bay receiving a flight strip via AFTN distribution.
 *
 * Real AFTN addresses are 8-character codes (`LOWGZTZX` = LOWG + ZTZX
 * (tower routing function)); this is the structural equivalent in
 * TWR2's typed model.
 *
 * Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13): replaces the
 * pre-Pass-14 `recipient: RoleName` on `SimEvent.FlightPlanFiled`. The
 * old shape implicitly meant "role at `plan.departureAerodrome`," which
 * could not represent cross-aerodrome destination strips.
 */
data class AftnAddress(
    val aerodromeId: AerodromeId,
    val role: RoleName,
)

/**
 * Sealed discriminator for an [AftnAddress] relative to a [FiledPlan]:
 * is the address on the departure side or the arrival side?
 *
 * Pass 14: drives `handleFlightPlanFiled`'s sealed-when dispatch
 * (departure → `Owned` flow; arrival → `knownStrips` flow). Future
 * en-route ACC routing adds a third leaf and the compiler-exhaustive
 * `when` surfaces every consumer that needs updating.
 */
sealed interface AftnDestination {
    /** Recipient is at the plan's departure aerodrome. */
    data object Departure : AftnDestination

    /** Recipient is at the plan's destination aerodrome (cross-aerodrome). */
    data object Arrival : AftnDestination

    companion object {
        /**
         * Classify an [AftnAddress] relative to a [FiledPlan]. Returns
         * `Left(UnreachableDestination)` when the recipient's aerodrome
         * matches neither the departure nor the destination — a routing-
         * table defect (the aircraft would never reach this controller's
         * airspace via this filed plan).
         */
        fun classify(
            recipient: AftnAddress,
            plan: FiledPlan,
        ): Either<UnreachableDestination, AftnDestination> = when (recipient.aerodromeId) {
            plan.departureAerodrome -> Departure.right()
            plan.destinationAerodrome -> Arrival.right()
            else -> UnreachableDestination(recipient, plan).left()
        }
    }
}

/**
 * Failure of [AftnDestination.classify]: the recipient's aerodrome
 * matches neither the plan's departure nor its destination. Indicates
 * a routing-table defect (typically: emitter computed a recipient list
 * incorrectly, or the plan's destination was amended after filing).
 */
data class UnreachableDestination(
    val recipient: AftnAddress,
    val plan: FiledPlan,
)
