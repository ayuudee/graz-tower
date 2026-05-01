package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.RoleName

/**
 * Sim-side projection of "which roles are staffed at this aerodrome right
 * now," delivered to the controller as part of [ControllerView].
 *
 * Pass 6 (D-AUDIT.12) introduced the published-vs-staffed distinction
 * `staffedRoles: Set<RoleName>` directly on `ControllerView`. The
 * post-impl review (Impact-M.1) flagged that as an unprotected sim→controller
 * channel — it didn't pass through a typed boundary like [SensorReading]
 * or [FlightStrip]. This is the boundary projection: the controller reads
 * `view.staffingPanel`, which encapsulates "what controllers are working
 * on the strip board / coordination panel right now." Real ATC sees this
 * via the strip board (paper or electronic) and the inter-controller
 * coordination calls before the shift starts.
 *
 * The projection deliberately carries **only role names** — not controller
 * identities, not workload, not session state. That is the firewall: a
 * future regression that wanted to expose "the controller of TOWER right
 * now is ID = X" would either (a) extend this type explicitly or (b)
 * bypass it. The architectural test [FirewallStaffingPanelTest] guards
 * that the type carries only [RoleName]-shaped data.
 */
data class StaffingPanel(val roles: Set<RoleName>)

/**
 * The single sim-side `SimState → StaffingPanel` projection. Reads only
 * the role-membership of `state.controllers` filtered by aerodrome — no
 * controller-identity, no workload, no session state. The architectural
 * test [FirewallStaffingPanelTest] asserts this file references only
 * [SimState.controllers] and that it does not import any pilot-internal
 * type.
 */
internal fun SimState.toStaffingPanel(aerodromeId: xyz.easiersaid.twr.protocol.AerodromeId): StaffingPanel {
    val roles = controllers.values
        .filter { it.aerodromeId == aerodromeId }
        .map { it.role }
        .toSet()
    return StaffingPanel(roles)
}
