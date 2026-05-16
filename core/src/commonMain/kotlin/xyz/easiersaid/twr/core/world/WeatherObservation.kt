package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.Temperature
import xyz.easiersaid.twr.protocol.WindReport

/**
 * Observed weather at a single aerodrome. The [wind] field is a sealed
 * [WindReport] (not nullable) so every consumer must handle the
 * "no report" case explicitly.
 *
 * fn-16: relocated from `:controller` to `:core/world` (sibling of
 * [Aerodrome]) per `project_rich_world_domain.md` — time-varying state
 * lives on the entity. With [Aerodrome.weather] now the single source of
 * truth, [WeatherObservation] is structurally the value carried by that
 * field, and `:core` is the natural module home so every reader
 * (`:controller`, `:sim`, `:pilot` tests) imports from one place.
 *
 * fn-14.1 (G3a-react) prior: the [WindReport] sealed type was lifted to
 * `:protocol` so the pilot can consume the wind projection through the
 * firewall without depending on `:controller`. `WeatherObservation`
 * (the full `(WindReport, qnh, visibility)` triple) was retained at
 * `:controller` then; fn-16 moves it to `:core/world` alongside
 * [Aerodrome] where it logically lives.
 *
 * fn-28.1 (G3a-react-density-altitude foundation A) — appended [oat] (OAT,
 * outside air temperature) AFTER [visibility]. Placement-after-visibility
 * is deliberate (round-8 Minor 2 of the fn-28 spec): the default-`null`
 * positional argument preserves every existing
 * `WeatherObservation(wind, qnh, visibility)` constructor call site at
 * fn-28.1 land time. Per ICAO Annex 11 §4.3.6.h (ATIS broadcast content),
 * surface air temperature is part of the published ATIS — modelling OAT
 * here puts the rich-world entity-field anchor for the same datum the
 * controller broadcasts. DA recognition (fn-28.2) reads through the
 * `:pilot/DensityAltitudeInput` projection — direct reads from
 * `aerodrome.weather.oat` in pilot code fail to compile via the
 * `PilotAviationWorld` strip (fn-24).
 *
 * **Pilot-firewall discipline** (fn-16 / fn-24 carried + fn-28.1
 * extension): pilot rules MUST consume the typed DA projection through
 * `xyz.easiersaid.twr.pilot.PilotInput.densityAltitudeInputsByAerodrome`
 * (`Map<AerodromeId, DensityAltitudeInput>`). Direct reads of
 * `world.aerodromes[id].weather.oat` from pilot code are structurally
 * unreachable — [xyz.easiersaid.twr.pilot.world.PilotAviationWorld]'s
 * projection strips `Aerodrome.weather`, including the new [oat] field.
 * Convention precedent + structural enforcement documented at
 * `core/src/commonMain/.../Aerodrome.weather` KDoc.
 */
data class WeatherObservation(
    val wind: WindReport,
    val qnh: PressureSetting?,
    val visibility: Int?,
    /**
     * Outside air temperature (OAT), fn-28.1 foundation A. Default
     * `null` preserves all existing positional constructor sites; the
     * new field appears AFTER [visibility] so no prior call site needs
     * adjustment (round-8 Minor 2). Concrete OAT is required by
     * DA-touching scenarios — the projection at
     * `xyz.easiersaid.twr.sim.PilotWiring.buildPilotInput` is
     * fail-closed when either [oat] or [qnh] is null on a given
     * aerodrome's weather (no `DensityAltitudeInput` entry produced;
     * downstream DA recognition skips that aerodrome).
     *
     * **Doctrine**: ICAO Annex 11 §4.3.6.h (ATIS content — air
     * temperature); ICAO Doc 4444 §4.5.5.h (equivalent ATIS content
     * list); FAA AC 61-107B §3-1 (density altitude operating
     * considerations — consumed by fn-28.2's
     * `AircraftType.maxDensityAltitudeFt`).
     */
    val oat: Temperature? = null,
)
