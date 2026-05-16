package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.protocol.Feet
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.Temperature

/**
 * fn-28.1 (G3a-react-density-altitude foundation A) — typed projection of
 * the three inputs the density-altitude formula requires. Crosses the
 * pilot firewall via [PilotInput.densityAltitudeInputsByAerodrome]
 * (`Map<AerodromeId, DensityAltitudeInput>`); `:sim`'s
 * [xyz.easiersaid.twr.sim.PilotWiring.buildPilotInput] constructs the
 * map at the firewall boundary from the world's per-aerodrome weather +
 * elevation. Direct reads of the underlying
 * [xyz.easiersaid.twr.core.world.WeatherObservation.oat] /
 * [xyz.easiersaid.twr.core.world.WeatherObservation.qnh] /
 * [xyz.easiersaid.twr.core.world.Aerodrome.elevation] from pilot code
 * are structurally unreachable —
 * [xyz.easiersaid.twr.pilot.world.PilotAviationWorld] strips
 * [xyz.easiersaid.twr.core.world.Aerodrome.weather], and
 * `Aerodrome.elevation` is reachable via the projection but the typed
 * DA projection is the doctrinally-correct read path (same shape as
 * `weatherByAerodrome` carrying only [WindReport] not the full
 * `WeatherObservation` triple — fn-14.1 / fn-16 precedent).
 *
 * **Firewall-clean** (R24 + R13 / fn-28 spec): all three fields are
 * typed-units ([Temperature] / [PressureSetting] / [Feet]) or scalars
 * only — no `Aerodrome`, `Runway`, `BeliefState`, `ControllerView`, or
 * other entity reference smuggled through. `FirewallPilotInputTest`
 * enumerates the allowlist via the canonical-constructor block AND a
 * reflection-based property scan; adding a non-cockpit field via this
 * map is forbidden by the firewall test's allowlist match.
 *
 * **Construction-site contract — fail-closed** (fn-28.1 acceptance):
 * `PilotWiring.buildPilotInput` constructs a `DensityAltitudeInput`
 * entry for an aerodrome ONLY when ALL of:
 *  - `aerodrome.weather?.oat != null`,
 *  - `aerodrome.weather?.qnh != null`,
 *  - `aerodrome.elevation` is convertible to [Feet] (always true today
 *    — `Aerodrome.elevation: Feet` is already typed-units, BUT future
 *    schema changes that admit a nullable or wider type must preserve
 *    the fail-closed contract here).
 *
 * When any precondition fails, the projection OMITS the aerodrome
 * entry (no `null`-valued map entry, no synthesised
 * placeholder). The downstream DA recognition (fn-28.2's
 * `derivePilotEvent` density-altitude branch) skips aerodromes with no
 * map entry — fail-closed all the way down.
 *
 * **Doctrine**: ICAO Annex 11 §4.3.6.h (ATIS OAT + QNH source); FAA
 * AC 61-107B §3-1 (DA operating considerations — anchored in
 * fn-28.2's `AircraftType.maxDensityAltitudeFt`).
 */
data class DensityAltitudeInput(
    /**
     * Outside air temperature observed at the aerodrome — typed [Temperature]
     * (degrees Celsius). Non-nullable: fail-closed projection at
     * [xyz.easiersaid.twr.sim.PilotWiring.buildPilotInput] guarantees this
     * field is concrete by the time a `DensityAltitudeInput` exists.
     * Source per fn-28.1: `Aerodrome.weather.oat`.
     */
    val oat: Temperature,
    /**
     * QNH (altimeter setting) at the aerodrome — typed [PressureSetting]
     * (sealed: `QnhHpa` / `QfeHpa` / `Standard`). Non-nullable: fail-closed
     * projection at the firewall boundary. Source per fn-28.1:
     * `Aerodrome.weather.qnh`.
     *
     * The DA formula's pressure-altitude term uses `(1013.25 - qnh_hPa) * 30`
     * — only the `QnhHpa` leaf carries the numerator. The fn-28.2
     * `computeDensityAltitudeFeet` formula will narrow the sealed type
     * (`Standard` / `QfeHpa` are unsupported in v1; fail-closed at the
     * formula site, not here — the projection records the typed datum
     * the controller broadcasts).
     */
    val qnh: PressureSetting,
    /**
     * Aerodrome elevation in feet — typed [Feet] (positive-int smart
     * constructor; fn-28.1 lifts residency from `:core/world` to
     * `:protocol` per R24 so this `:pilot`-resident type can depend
     * on it). Source per fn-28.1: `Aerodrome.elevation`.
     *
     * Future schema changes that admit a nullable elevation (none planned
     * at fn-28.1 — `Aerodrome.elevation: Feet` is non-null today) MUST
     * preserve the fail-closed projection contract at
     * `PilotWiring.buildPilotInput` (omit the entry rather than
     * synthesise a default).
     */
    val fieldElevation: Feet,
)
