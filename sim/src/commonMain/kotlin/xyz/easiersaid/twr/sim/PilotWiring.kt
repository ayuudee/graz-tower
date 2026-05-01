package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.pilot.PilotInput
import xyz.easiersaid.twr.protocol.AircraftId

/**
 * Build a [PilotInput] from a [SimState] for a specific aircraft.
 *
 * Symmetric counterpart of [buildControllerView] in `ControllerWiring.kt`.
 * `:sim` is the integration layer that depends on both `:pilot` and
 * `:controller`; the wiring is where sim-level state crosses each agent's
 * typed boundary.
 *
 * Returns null when the aircraft is missing — `handlePilotTick`'s null path.
 *
 * **Firewall posture**: this function reads only fields of [SimState] that
 * the pilot is legitimately allowed to know — own [AircraftState],
 * [WorldIndex], [AviationWorld], current [SimTime]. It does NOT read
 * `state.beliefs` or `state.controllers`. The compile-time enforcement is
 * `:pilot`'s build graph (no `:controller` dependency); the runtime
 * enforcement is `FirewallSimPilotTickIsolationTest` which scans this
 * file's source.
 */
internal fun buildPilotInput(state: SimState, aircraftId: AircraftId): PilotInput? {
    val aircraft = state.aircraft[aircraftId] ?: return null
    return PilotInput(
        aircraft = aircraft,
        worldIndex = state.worldIndex,
        world = state.world,
        now = state.now,
    )
}
