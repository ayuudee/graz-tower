package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.RoleName

/**
 * Registered controller in the simulation.
 *
 * A [ControllerSpec] is the ground-truth side of the boundary the controller
 * module observes through a [xyz.easiersaid.twr.controller.ControllerView]:
 * who the controller is ([id]), what they staff ([role]) at which aerodrome
 * ([aerodromeId]), and which aircraft they are currently responsible for
 * ([responsibilities]).
 *
 * The spec is the minimum the [handleControllerTick] in [step] needs to
 * project a view; enrichments (weather, pending handoffs) come from other
 * parts of [SimState] or later slices.
 *
 * Responsibility transfer is modelled as a [ControllerSpec.copy] on handoff
 * completion — kept in the sim so controllers are not the source of truth
 * for who belongs to whom.
 */
data class ControllerSpec(
    val id: ControllerId,
    val role: RoleName,
    val aerodromeId: AerodromeId,
    val frequency: Frequency,
    val responsibilities: Set<AircraftId>,
)
