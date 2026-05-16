package xyz.easiersaid.twr.pilot.world

import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AerodromeAip
import xyz.easiersaid.twr.core.world.AerodromeRole
import xyz.easiersaid.twr.core.world.Airway
import xyz.easiersaid.twr.core.world.AirspaceVolume
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.CircuitProcedure
import xyz.easiersaid.twr.core.world.DeclaredDistances
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Doctrine
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Fix
import xyz.easiersaid.twr.core.world.FlightInformationRegion
import xyz.easiersaid.twr.core.world.HoldingPattern
import xyz.easiersaid.twr.core.world.InstrumentApproach
import xyz.easiersaid.twr.core.world.LatLon
import xyz.easiersaid.twr.core.world.Meters
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.PhysicalGeometry
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.RunwayExit
import xyz.easiersaid.twr.core.world.Sid
import xyz.easiersaid.twr.core.world.Star
import xyz.easiersaid.twr.core.world.Stand
import xyz.easiersaid.twr.core.world.Apron
import xyz.easiersaid.twr.core.world.Taxiway
import xyz.easiersaid.twr.core.world.VfrRoute
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.AirwayId
import xyz.easiersaid.twr.protocol.ApproachId
import xyz.easiersaid.twr.protocol.ApronId
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FirId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.HoldingPatternId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SidId
import xyz.easiersaid.twr.protocol.StandId
import xyz.easiersaid.twr.protocol.StarId
import xyz.easiersaid.twr.protocol.TaxiwayId
import xyz.easiersaid.twr.protocol.VfrRouteId

/**
 * fn-24 (closes `D-PASS-pilot-world-strip-dynamic-state`) — structural
 * enforcement of the pilot firewall against entity-level dynamic state.
 *
 * Post-fn-12 (`Runway.obstruction`) and fn-16 (`Aerodrome.weather`), the
 * rich-world-domain principle puts time-varying state ON the entity it
 * concerns. The same [AviationWorld] instance flows to both the
 * controller-side wiring AND `PilotInput.world`. Convention + per-field
 * KDoc enforced the discipline that pilot rules read these dynamic
 * fields only through their typed projections (e.g.
 * [xyz.easiersaid.twr.pilot.PilotInput.weatherByAerodrome] for wind);
 * direct reads of `world.aerodromes[id].weather` or
 * `world.aerodromes[id].runways[id].obstruction` from pilot code were
 * convention-violations but compiled.
 *
 * This file introduces parallel typed projection data classes
 * ([PilotAviationWorld], [PilotAerodrome], [PilotRunway]) that omit the
 * dynamic entity fields. With [PilotInput.world: PilotAviationWorld],
 * those direct reads now fail to compile — structural enforcement
 * replaces convention. `D-PASS-pilot-world-strip-dynamic-state` (filed
 * by fn-16.1's codex round-2 review) archives at this fn-24.1 landing.
 *
 * **Construction discipline (R4).** [AviationWorld.toPilotView],
 * [Aerodrome.toPilotView], and [Runway.toPilotView] use exhaustive
 * **named-argument constructor wiring** (no `copy()`, no spread, no
 * reflection). When a future field is added to [Aerodrome] or [Runway]
 * in `:core`, every projection-constructor call site in this file
 * fails to compile until the implementer either (a) adds the field to
 * the projection (chart-equivalent / static reference data), or (b)
 * justifies why the field belongs only on the core entity (dynamic
 * state — model it as a typed projection field on `PilotInput` instead,
 * the way [xyz.easiersaid.twr.pilot.PilotInput.weatherByAerodrome] does
 * for `Aerodrome.weather.wind`). Named-arg wiring is the construction-
 * site gate; the property-set parity assertion in
 * `FirewallPilotAviationWorldTest` is the complementary future-field
 * gate that catches the same drift from the other side.
 *
 * **Build-graph one-wayness.** The projection lives in `:pilot`, not in
 * `:core` as a `Pilot`-typed view. Pilot-firewall enforcement belongs
 * structurally INSIDE `:pilot`; widening `:core` with a pilot-specific
 * projection concept would invert the build-graph dependency. The
 * deferment's "open question" (`:pilot`-side vs `:core`-side) resolves
 * this way.
 *
 * @see xyz.easiersaid.twr.core.world.Aerodrome.weather KDoc (fn-16 dynamic-state precedent)
 * @see xyz.easiersaid.twr.core.world.Runway.obstruction KDoc (fn-12 dynamic-state precedent)
 */
public data class PilotAviationWorld(
    val geometry: PhysicalGeometry = PhysicalGeometry(),
    val fixes: Map<FixId, Fix> = emptyMap(),
    val aerodromes: Map<AerodromeId, PilotAerodrome> = emptyMap(),
    val airways: Map<AirwayId, Airway> = emptyMap(),
    val vfrRoutes: Map<VfrRouteId, VfrRoute> = emptyMap(),
    val airspace: Map<AirspaceVolumeId, AirspaceVolume> = emptyMap(),
    val firs: Map<FirId, FlightInformationRegion> = emptyMap(),
)

/**
 * Pilot-side projection of [Aerodrome]. Mirrors every static / chart-
 * equivalent field on the core entity; **omits [Aerodrome.weather]**
 * (the dynamic field — pilots consume wind via the typed
 * [xyz.easiersaid.twr.pilot.PilotInput.weatherByAerodrome] projection).
 *
 * `runways` substitutes the value type to [PilotRunway] (which itself
 * omits [Runway.obstruction]).
 */
public data class PilotAerodrome(
    val icao: AerodromeId,
    val elevation: Feet,
    val magneticVariation: Degrees,
    val transitionAltitude: Level,
    val transitionLevel: Level? = null,
    val aip: AerodromeAip = AerodromeAip(),
    val roles: Map<RoleName, AerodromeRole> = emptyMap(),
    val controllers: Map<ControllerId, Set<RoleName>> = emptyMap(),
    val runways: Map<RunwayId, PilotRunway> = emptyMap(),
    val taxiways: Map<TaxiwayId, Taxiway> = emptyMap(),
    val stands: Map<StandId, Stand> = emptyMap(),
    val aprons: Map<ApronId, Apron> = emptyMap(),
    val circuits: Map<CircuitProcedureId, CircuitProcedure> = emptyMap(),
    val sids: Map<SidId, Sid> = emptyMap(),
    val stars: Map<StarId, Star> = emptyMap(),
    val approaches: Map<ApproachId, InstrumentApproach> = emptyMap(),
    val holdingPatterns: Map<HoldingPatternId, HoldingPattern> = emptyMap(),
    val referencePoint: LatLon? = null,
    val ctrApproximationRadius: Meters = Doctrine.IcaoAnnex11.CTR_FLOOR_5NM,
    // NO `val weather: WeatherObservation?` — the fn-24 structural
    // omission. Pilots read wind via the typed projection at
    // `PilotInput.weatherByAerodrome`; QNH/visibility have no
    // pilot-firewall projection in v1.
)

/**
 * Pilot-side projection of [Runway]. Mirrors every static field on the
 * core entity; **omits [Runway.obstruction]** (the dynamic field —
 * the controller's reactive obstruction-GA rule fires on the controller
 * channel; pilots learn of an obstruction via radio, never by chart
 * read).
 */
public data class PilotRunway(
    val id: RunwayId,
    val path: Path,
    val threshold: xyz.easiersaid.twr.protocol.PointId,
    val exits: List<RunwayExit> = emptyList(),
    val declaredDistances: DeclaredDistances? = null,
    // NO `val obstruction: RunwayObstruction?` — the fn-12-precedent
    // structural omission. Obstruction state reaches the pilot via
    // controller transmission, not via chart-read.
)

/**
 * Project an [AviationWorld] into its pilot-side view by stripping
 * entity-level dynamic fields. Pure function; uses **exhaustive named-
 * argument constructor wiring** so a future field added to the core
 * types surfaces at the projection's construction site (R4).
 */
public fun AviationWorld.toPilotView(): PilotAviationWorld = PilotAviationWorld(
    geometry = geometry,
    fixes = fixes,
    aerodromes = aerodromes.mapValues { (_, a) -> a.toPilotView() },
    airways = airways,
    vfrRoutes = vfrRoutes,
    airspace = airspace,
    firs = firs,
)

/**
 * Project an [Aerodrome] into its pilot-side view by dropping the
 * dynamic [Aerodrome.weather] field. Static fields flow through
 * verbatim; `runways` recurses via [Runway.toPilotView].
 */
private fun Aerodrome.toPilotView(): PilotAerodrome = PilotAerodrome(
    icao = icao,
    elevation = elevation,
    magneticVariation = magneticVariation,
    transitionAltitude = transitionAltitude,
    transitionLevel = transitionLevel,
    aip = aip,
    roles = roles,
    controllers = controllers,
    runways = runways.mapValues { (_, r) -> r.toPilotView() },
    taxiways = taxiways,
    stands = stands,
    aprons = aprons,
    circuits = circuits,
    sids = sids,
    stars = stars,
    approaches = approaches,
    holdingPatterns = holdingPatterns,
    referencePoint = referencePoint,
    ctrApproximationRadius = ctrApproximationRadius,
    // `weather` deliberately not wired — see KDoc on [PilotAerodrome].
)

/**
 * Project a [Runway] into its pilot-side view by dropping the dynamic
 * [Runway.obstruction] field. Static fields flow through verbatim.
 */
private fun Runway.toPilotView(): PilotRunway = PilotRunway(
    id = id,
    path = path,
    threshold = threshold,
    exits = exits,
    declaredDistances = declaredDistances,
    // `obstruction` deliberately not wired — see KDoc on [PilotRunway].
)
