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
        // Pass 15 (D-AUDIT.8 closure): the pilot reads ATIS at the
        // moment of first contact (lazy). The full per-aerodrome ATIS
        // map crosses the boundary so multi-aerodrome scenarios can
        // resolve via aerodrome lookup later (D-AUDIT.8.IV-FOLLOWUP).
        atisByAerodrome = state.atisByAerodrome,
        // fn-14.1 (G3a-react R4): project just the WindReport slice from
        // each WeatherObservation. The full triple (wind, qnh, visibility)
        // stays on the controller side — only the wind crosses the pilot
        // firewall. Real pilots sense wind via windsock + ASI + instrument
        // scan; the projection models that channel.
        weatherByAerodrome = state.weatherByAerodrome.mapValues { (_, obs) -> obs.wind },
    )
}
