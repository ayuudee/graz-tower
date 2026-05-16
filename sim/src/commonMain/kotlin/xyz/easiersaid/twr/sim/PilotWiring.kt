package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.pilot.DensityAltitudeInput
import xyz.easiersaid.twr.pilot.PilotInput
import xyz.easiersaid.twr.pilot.world.toPilotView
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
        // fn-24: project `AviationWorld` into the typed
        // `PilotAviationWorld` view at the firewall boundary. The
        // projection omits dynamic entity fields
        // (`Aerodrome.weather`, `Runway.obstruction`) so pilot code
        // cannot reach them by chart-read — closes
        // `D-PASS-pilot-world-strip-dynamic-state`.
        world = state.world.toPilotView(),
        now = state.now,
        // Pass 15 (D-AUDIT.8 closure): the pilot reads ATIS at the
        // moment of first contact (lazy). The full per-aerodrome ATIS
        // map crosses the boundary so multi-aerodrome scenarios can
        // resolve via aerodrome lookup later (D-AUDIT.8.IV-FOLLOWUP).
        atisByAerodrome = state.atisByAerodrome,
        // fn-14.1 (G3a-react R4): project just the WindReport slice from
        // each WeatherObservation. The full triple (wind, qnh, visibility)
        // stays on the entity — only the wind crosses the pilot firewall.
        // Real pilots sense wind via windsock + ASI + instrument scan;
        // the projection models that channel.
        //
        // fn-16 (R7a): source migrated from the deleted
        // `state.weatherByAerodrome` to `state.world.aerodromes[*].weather`.
        // **Pinned `mapNotNull` form** — preserves the pre-migration
        // absent-key semantics exactly. An aerodrome with `weather ==
        // null` produces NO entry (matching pre-migration behaviour
        // where the key was simply absent). Critical for
        // `windForMission`'s `map.size == 1` singleton-fallback path
        // in multi-aerodrome scenarios. The `mapValues { weather?.wind
        // ?: NotReported }` alternative was considered and rejected
        // because it would synthesise spurious `NotReported` entries
        // for unweathered aerodromes.
        weatherByAerodrome = state.world.aerodromes
            .mapNotNull { (id, a) -> a.weather?.wind?.let { id to it } }
            .toMap(),
        // fn-28.1 (G3a-react-density-altitude foundation A): project
        // the typed `DensityAltitudeInput` for every aerodrome whose
        // weather has BOTH oat AND qnh non-null. Aerodrome elevation
        // (`Aerodrome.elevation: Feet`) is non-null today by core schema;
        // the projection STILL gates the elevation read (future schema
        // changes that admit a nullable or wider type must preserve this
        // fail-closed shape — see `DensityAltitudeInput` KDoc + the
        // fn-28.1 projection-fail-closed test in
        // `ProjectionDensityAltitudeInputSpec`).
        //
        // **Fail-closed**: any aerodrome missing oat OR qnh produces NO
        // map entry (no `null`-valued map entry; no synthesised
        // placeholder). Downstream DA recognition in fn-28.2 reads the
        // map via `densityAltitudeInputForMission` and treats a missing
        // entry as no-event. Same `mapNotNull` shape as `weatherByAerodrome`
        // above — chosen for the identical "absent-key vs explicit-null"
        // semantic the pre-migration fixture sites used.
        densityAltitudeInputsByAerodrome = state.world.aerodromes
            .mapNotNull { (id, a) ->
                val weather = a.weather ?: return@mapNotNull null
                val oat = weather.oat ?: return@mapNotNull null
                val qnh = weather.qnh ?: return@mapNotNull null
                // Field-elevation sourcing (round-14 Minor 1 acceptance):
                // `Aerodrome.elevation: Feet` is non-null today, so the
                // explicit binding below is mechanically a passthrough.
                // The fail-closed shape is documented in
                // `DensityAltitudeInput` KDoc — a future schema regression
                // that admits a nullable or wider type MUST update this
                // construction site to preserve the fail-closed contract
                // (omit the entry rather than synthesise a default).
                val elevation = a.elevation
                id to DensityAltitudeInput(
                    oat = oat,
                    qnh = qnh,
                    fieldElevation = elevation,
                )
            }
            .toMap(),
    )
}
