package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.SimTime

/**
 * The complete and exclusive input to the pilot's decision function.
 *
 * Every field of [PilotInput] must map to a real-world cockpit input: own
 * kinematic state, own filed plan, own visual observation, world geometry
 * (chart). The named-argument constructor below is the FIREWALL ALLOWLIST.
 * Adding a field requires it to map to a real-world cockpit input AND a
 * deliberate update of `FirewallPilotInputTest`. A widening that smuggles
 * in controller-side state (BeliefState, ControllerSpec, ControllerView, or
 * anything reachable from them) is a firewall regression and is forbidden
 * by the no-suppression rule.
 *
 * **Note on radio reception**: received [xyz.easiersaid.twr.protocol.AtcInstruction]s
 * are folded into [PilotMission] via `processInstruction` *outside* the
 * pilot-decision tick, at the moment of delivery (`Step.handlePilotProcessingComplete`).
 * By the time `pilotDecide` runs, every radio fact the pilot needs has
 * already been recorded on `PilotMission` (activeRunway, contactedOnFrequency,
 * routeOverride, hasClearance, etc.). [PilotInput] therefore carries no
 * radio inbox — there is nothing for the tick to read that is not already
 * on `aircraft.pilotMission`.
 *
 * **No-suppression rule:** an architectural test failure that prevents
 * widening this type is never resolved by `@Suppress`, `@Disabled`, or
 * test removal. Resolve by either justifying the widening as a real-world
 * cockpit input (and updating the firewall test deliberately) or by
 * routing the data through `processInstruction` if it is radio-derived.
 */
data class PilotInput(
    val aircraft: AircraftState,
    val worldIndex: WorldIndex,
    val world: AviationWorld,
    val now: SimTime,
)

/**
 * The pilot's decision for one tick. Total — every successful decision
 * carries kinematic intent, any transmissions to issue, and the updated
 * mission state.
 *
 * Routing errors are carried in `Either<RoutingError, PilotOutput>` at
 * the call site (`pilotDecide`), not as a side field on this type. The
 * freeze-on-routing-error policy lives at the sim boundary
 * (`Step.handlePilotTick`), not inside the success type — the FP-correct
 * separation of "the pilot decided" from "the simulator handles a routing
 * defect."
 */
data class PilotOutput(
    val intent: PilotIntent,
    val transmissions: List<PilotTransmission>,
    val updatedMission: PilotMission?,
)
