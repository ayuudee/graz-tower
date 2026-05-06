package xyz.easiersaid.twr.sim

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AftnAddress
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.RoleName

/**
 * AFTN routing topology — pure function from a [FiledPlan] to the set of
 * controller bays that should receive a copy of the strip.
 *
 * Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13): closes the
 * fan-out gap. Pass 11's `FlightPlanFiled` event carried a single
 * `recipient: RoleName` implicitly at `plan.departureAerodrome`. This
 * function expands routing to the multi-recipient cross-aerodrome
 * model: one departure-side recipient + one destination-side recipient
 * (when the plan crosses aerodromes).
 *
 * **Sources**:
 *  - ICAO Doc 4444 §11 — flight plan filing.
 *  - ICAO Annex 10 Vol II — AFTN addressing.
 *  - Doc 4444 §10.1 — strip distribution to handoff-receiving controllers.
 *
 * **Routing topology (Pass 14 only)**:
 *  - **Departure side, always one recipient.** Prefers `GROUND`;
 *    falls back to `TOWER`. Neither published →
 *    `Left(NoDepartureRoleStaffed)`.
 *  - **Destination side, conditional one recipient.** Only added if
 *    the plan crosses aerodromes (`destinationAerodrome != null &&
 *    destinationAerodrome != departureAerodrome`). Prefers `TOWER`;
 *    falls back to `APPROACH`. Neither published →
 *    `Left(NoDestinationRoleStaffed)`.
 *
 * **Out of scope** (filed deferments):
 *  - `CLEARANCE_DELIVERY` routing (D-PF.1 / Pass 17).
 *  - En-route ACC sectors (FIR sector model).
 *  - Strip-update-on-amendment (D-AUDIT.6.C-FOLLOWUP).
 */
object AftnRouting {

    /**
     * Compute the set of AFTN addresses that should receive [plan].
     *
     * The function is parameterised on a *projection* — `(AerodromeId)
     * -> Set<RoleName>` — rather than the full `AviationWorld`, so it
     * stays decoupled from `:core` and remains trivially testable.
     * Sim's call site is `{ world.aerodromes[it]?.roles?.keys.orEmpty() }`.
     */
    fun routeFiledPlan(
        plan: FiledPlan,
        publishedRolesAt: (AerodromeId) -> Set<RoleName>,
    ): Either<RoutingFailure, NonEmptyList<AftnAddress>> {
        val depAerodrome = plan.departureAerodrome
        val depRole = preferredDepartureRole(publishedRolesAt(depAerodrome))
            ?: return RoutingFailure.NoDepartureRoleStaffed(depAerodrome).left()
        val depAddress = AftnAddress(depAerodrome, depRole)

        // Single-aerodrome plans (null destination OR destination==departure)
        // short-circuit here. The cross-aerodrome predicate inlines the null
        // handling so the destination-side code path doesn't need `!!`.
        val destAerodrome = plan.destinationAerodrome
            ?: return NonEmptyList(depAddress, emptyList()).right()
        if (destAerodrome == depAerodrome) {
            return NonEmptyList(depAddress, emptyList()).right()
        }

        val destRole = preferredDestinationRole(publishedRolesAt(destAerodrome))
            ?: return RoutingFailure.NoDestinationRoleStaffed(destAerodrome).left()
        return NonEmptyList(depAddress, listOf(AftnAddress(destAerodrome, destRole))).right()
    }

    /** Departure side: prefer GROUND, fall back to TOWER. */
    private fun preferredDepartureRole(published: Set<RoleName>): RoleName? = when {
        RoleName.GROUND in published -> RoleName.GROUND
        RoleName.TOWER in published -> RoleName.TOWER
        else -> null
    }

    /** Destination side: prefer TOWER, fall back to APPROACH. */
    private fun preferredDestinationRole(published: Set<RoleName>): RoleName? = when {
        RoleName.TOWER in published -> RoleName.TOWER
        RoleName.APPROACH in published -> RoleName.APPROACH
        else -> null
    }
}

/**
 * Sealed routing-failure reasons. Pass 14 surfaces "no role staffed
 * for this AFTN address side" as typed errors rather than partial-
 * function silent drops.
 */
sealed interface RoutingFailure {
    /** Departure aerodrome publishes neither GROUND nor TOWER. */
    data class NoDepartureRoleStaffed(val aerodrome: AerodromeId) : RoutingFailure

    /** Destination aerodrome publishes neither TOWER nor APPROACH. */
    data class NoDestinationRoleStaffed(val aerodrome: AerodromeId) : RoutingFailure
}
