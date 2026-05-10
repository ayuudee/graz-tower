@file:Suppress("TooManyFunctions") // loader groups all world-candidate→AviationWorld translation
// into one object; splitting fragments the surface and obscures the manifest→world mapping.

package xyz.easiersaid.twr.migration.world

import kotlin.math.hypot
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AerodromeAip
import xyz.easiersaid.twr.core.world.AerodromeRole
import xyz.easiersaid.twr.core.world.AirspaceBoundary
import xyz.easiersaid.twr.core.world.AirspaceClass
import xyz.easiersaid.twr.core.world.AirspaceVolume
import xyz.easiersaid.twr.core.world.AirspaceVolumeType
import xyz.easiersaid.twr.core.world.AltitudeBand
import xyz.easiersaid.twr.core.world.AltitudeBoundary
import xyz.easiersaid.twr.core.world.AltitudeConstraint
import xyz.easiersaid.twr.core.world.ApproachMinimum
import xyz.easiersaid.twr.core.world.Apron
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.CircuitJoin
import xyz.easiersaid.twr.core.world.CircuitLeg
import xyz.easiersaid.twr.core.world.CircuitProcedure
import xyz.easiersaid.twr.core.world.ContactRequirement
import xyz.easiersaid.twr.core.world.ContactTiming
import xyz.easiersaid.twr.core.world.DeclaredDistances
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Doctrine
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Fix
import xyz.easiersaid.twr.core.world.FixType
import xyz.easiersaid.twr.core.world.FlightInformationRegion
import xyz.easiersaid.twr.core.world.GeometrySegmentId
import xyz.easiersaid.twr.core.world.HoldingPattern
import xyz.easiersaid.twr.core.world.HoldingPoint
import xyz.easiersaid.twr.core.world.HoldingPointType
import xyz.easiersaid.twr.core.world.InstrumentApproach
import xyz.easiersaid.twr.core.world.LatLon
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Meters
import xyz.easiersaid.twr.core.world.MinimumType
import xyz.easiersaid.twr.core.world.MissedApproachProcedure
import xyz.easiersaid.twr.core.world.OperationalSector
import xyz.easiersaid.twr.core.world.OperationalSectorAnchor
import xyz.easiersaid.twr.core.world.OperationalSectorCtrRelation
import xyz.easiersaid.twr.core.world.OperationalSectorKind
import xyz.easiersaid.twr.core.world.Path as WorldPath
import xyz.easiersaid.twr.core.world.PhysicalGeometry
import xyz.easiersaid.twr.core.world.PlateId
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.PublishedMapLabel
import xyz.easiersaid.twr.core.world.PublishedPointReference
import xyz.easiersaid.twr.core.world.PublishedProcedureAdvisories
import xyz.easiersaid.twr.core.world.PublishedProcedureCommunicationFailure
import xyz.easiersaid.twr.core.world.PublishedVfrProcedure
import xyz.easiersaid.twr.core.world.PublishedVfrProcedureKind
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.SegmentGeometry
import xyz.easiersaid.twr.core.world.Sid
import xyz.easiersaid.twr.core.world.SpeedConstraint
import xyz.easiersaid.twr.core.world.Stand
import xyz.easiersaid.twr.core.world.Star
import xyz.easiersaid.twr.core.world.SurfaceType
import xyz.easiersaid.twr.core.world.Taxiway
import xyz.easiersaid.twr.core.world.VfrRoute
import xyz.easiersaid.twr.core.world.VfrRouteAirspaceProfile
import xyz.easiersaid.twr.core.world.VfrRouteAirspaceSegment
import xyz.easiersaid.twr.core.world.Waypoint
import xyz.easiersaid.twr.core.world.asBoundaryRing
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.ApproachId
import xyz.easiersaid.twr.protocol.ApronId
import xyz.easiersaid.twr.protocol.ApproachType
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.DmeDistanceNm
import xyz.easiersaid.twr.protocol.FirId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.HoldingPatternId
import xyz.easiersaid.twr.protocol.JoinType
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.Minutes
import xyz.easiersaid.twr.protocol.OperationalSectorId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PublishedVfrProcedureId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SidId
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.StandId
import xyz.easiersaid.twr.protocol.StarId
import xyz.easiersaid.twr.protocol.TaxiwayId
import xyz.easiersaid.twr.protocol.TurnDirection
import xyz.easiersaid.twr.protocol.VfrRouteId

/**
 * Pure converter from a serialized [WorldCandidateDocument] into the runtime
 * [AviationWorld] data structures. Single-aerodrome-per-document; compose
 * multiple worlds with [mergeAviationWorlds] for multi-aerodrome runtime use.
 */
object WorldCandidateLoader {

    @Suppress("LongMethod") // top-level loader composes ~10 concern-specific subdomains
    // (aerodromes, runways, taxiways, holding-points, circuits, SIDs/STARs, airspaces, etc.)
    // into one immutable AviationWorld. The composition is intrinsically wide; splitting moves
    // wiring into helpers without simplifying the dependency graph.
    fun toWorld(document: WorldCandidateDocument): AviationWorld {
        val world = document.world

        val positions = world.geometry.points.mapValues { (_, point) ->
            Position(
                xMeters = point.xMeters,
                yMeters = point.yMeters,
            )
        }.mapKeys { (id, _) -> PointId(id) }

        val paths = world.geometry.paths.mapValues { (_, path) ->
            WorldPath(path.pointIds.map(::PointId))
        }

        val segments = world.geometry.paths.values
            .flatMap { path ->
                val pointIds = path.pointIds.map(::PointId)
                pointIds.zipWithNext().map { (from, to) ->
                    val fromPosition = positions.getValue(from)
                    val toPosition = positions.getValue(to)
                    GeometrySegmentId.between(from, to) to SegmentGeometry(
                        length = Meters(hypot(toPosition.xMeters - fromPosition.xMeters, toPosition.yMeters - fromPosition.yMeters)),
                        width = Meters(path.widthMeters),
                        surface = path.surface.toSurfaceType(),
                    )
                }
            }
            .groupBy(
                keySelector = { (segmentId, _) -> segmentId },
                valueTransform = { (_, geometry) -> geometry },
            )
            .mapValues { (segmentId, geometries) ->
                val first = geometries.first()
                require(geometries.all { geometry -> geometry == first }) {
                    "Conflicting segment geometry projection for ${segmentId.describe()}: $geometries"
                }
                first
            }

        val runways = world.aerodrome.runways.mapValues { (_, runway) ->
            Runway(
                id = RunwayId(runway.id),
                path = paths.getValue(runway.pathId),
                threshold = PointId(runway.thresholdPointId),
                declaredDistances = DeclaredDistances(
                    tora = Meters(runway.declaredDistances.toraMeters.toDouble()),
                    toda = Meters(runway.declaredDistances.todaMeters.toDouble()),
                    asda = Meters(runway.declaredDistances.asdaMeters.toDouble()),
                    lda = Meters(runway.declaredDistances.ldaMeters.toDouble()),
                ),
            )
        }.mapKeys { (id, _) -> RunwayId(id) }

        val taxiways = world.aerodrome.taxiways.mapValues { (_, taxiway) ->
            Taxiway(
                id = TaxiwayId(taxiway.id),
                name = taxiway.name,
                path = paths.getValue(taxiway.pathId),
                holdingPoints = taxiway.holdingPoints.map { holdingPoint ->
                    HoldingPoint(
                        point = PointId(holdingPoint.pointId),
                        name = holdingPoint.name,
                        type = holdingPoint.type.toHoldingPointType(),
                        runway = holdingPoint.runwayId?.let(::RunwayId),
                    )
                },
                bidirectional = taxiway.bidirectional,
            )
        }.mapKeys { (id, _) -> TaxiwayId(id) }

        val stands = world.aerodrome.stands.mapValues { (_, stand) ->
            Stand(
                id = StandId(stand.id),
                name = stand.name,
                point = PointId(stand.pointId),
            )
        }.mapKeys { (id, _) -> StandId(id) }

        val aprons = world.aerodrome.aprons.mapValues { (_, apron) ->
            Apron(
                id = ApronId(apron.id),
                name = apron.name,
                paths = apron.pathIds.map(paths::getValue),
                stands = apron.standIds.map(::StandId).toSet(),
            )
        }.mapKeys { (id, _) -> ApronId(id) }

        val fixes = world.fixes.mapValues { (_, fix) ->
            Fix(
                id = FixId(fix.id),
                point = PointId(fix.pointId),
                name = fix.name,
                type = fix.type.toFixType(),
            )
        }.mapKeys { (id, _) -> FixId(id) }

        val vfrRoutes = world.vfrRoutes.mapValues { (_, route) ->
            VfrRoute(
                id = VfrRouteId(route.id),
                name = route.name,
                waypoints = route.pointIds.map { pointId -> Waypoint(PointId(pointId)) },
                airspaceProfile = route.airspaceProfile.toVfrRouteAirspaceProfile(),
            )
        }.mapKeys { (id, _) -> VfrRouteId(id) }

        val operationalSectors = world.aerodrome.aip.operationalSectors.mapValues { (_, sector) ->
            OperationalSector(
                id = OperationalSectorId(sector.id),
                name = sector.name,
                kind = sector.kind.toOperationalSectorKind(),
                boundary = AirspaceBoundary(
                    sector.boundaryPathIds.map { pathId -> paths.getValue(pathId).asBoundaryRing() },
                ),
                anchor = sector.anchor?.toOperationalSectorAnchor(),
                entryExitPoints = sector.entryExitPointIds.map(::PointId).toSet(),
                altitudeBand = sector.altitudeBand?.toAltitudeBand(),
                contactRequirement = sector.contactRequirement?.toContactRequirement(),
                relationToCtr = sector.relationToCtr?.toOperationalSectorCtrRelation(),
                associatedProcedures = sector.associatedProcedureIds.map(::PublishedVfrProcedureId).toSet(),
                note = sector.note,
                specialProcedureNote = sector.specialProcedureNote,
            )
        }.mapKeys { (id, _) -> OperationalSectorId(id) }

        val publishedVfrProcedures = world.aerodrome.aip.publishedVfrProcedures.mapValues { (_, procedure) ->
            procedure.toPublishedVfrProcedure()
        }.mapKeys { (id, _) -> PublishedVfrProcedureId(id) }

        val circuits = world.aerodrome.circuits.mapValues { (_, circuit) ->
            CircuitProcedure(
                id = CircuitProcedureId(circuit.id),
                runway = RunwayId(circuit.runwayId),
                direction = circuit.direction.toCircuitDirection(),
                legs = circuit.legs.map { leg ->
                    CircuitLeg(
                        name = leg.name.toLegName(),
                        path = paths.getValue(leg.pathId),
                    )
                },
                altitude = Level.AltitudeFeet.unsafe(circuit.altitudeFeet),
                reportingPoints = circuit.reportingPoints.mapKeys { (name, _) -> name.toLegName() }
                    .mapValues { (_, pointId) -> PointId(pointId) },
                joinProcedures = circuit.joinProcedures.map { join ->
                    CircuitJoin(
                        type = join.type.toJoinType(),
                        entryPoint = PointId(join.entryPointId),
                        entryPath = join.entryPathId?.let(paths::getValue),
                    )
                },
                goAroundPath = paths.getValue(circuit.goAroundPathId),
            )
        }.mapKeys { (id, _) -> CircuitProcedureId(id) }

        val airspace = world.airspaceVolumes.mapValues { (_, volume) ->
            AirspaceVolume(
                id = AirspaceVolumeId(volume.id),
                name = volume.name,
                type = AirspaceVolumeType.valueOf(volume.type),
                airspaceClass = AirspaceClass.valueOf(volume.airspaceClass),
                altitudeBand = volume.altitudeBand.toAltitudeBand(),
                memberPoints = volume.memberPointIds.map(::PointId).toSet(),
                fir = FirId(volume.firId),
                boundary = volume.boundaryPathIds
                    .takeIf { it.isNotEmpty() }
                    ?.map { pathId -> paths.getValue(pathId).asBoundaryRing() }
                    ?.let(::AirspaceBoundary),
            )
        }.mapKeys { (id, _) -> AirspaceVolumeId(id) }

        val firs = world.firs.mapValues { (_, fir) ->
            FlightInformationRegion(
                id = FirId(fir.id),
                name = fir.name,
                volumes = fir.volumeIds.map(::AirspaceVolumeId).toSet(),
            )
        }.mapKeys { (id, _) -> FirId(id) }

        // R8: per-aerodrome ctrApproximationRadius — sub-floor authoring is
        // rejected at load time against the ICAO Annex 11 §2.11 5 NM floor.
        // Match the loader's existing throwing-validation convention
        // (`require(n >= ...) { msg }`); not Either.Left.
        val authoredCtrRadiusNm = world.aerodrome.ctrApproximationRadiusNauticalMiles
        if (authoredCtrRadiusNm != null) {
            require(authoredCtrRadiusNm >= Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES) {
                "ctrApproximationRadiusNauticalMiles must be >= " +
                    "${Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES} NM " +
                    "(ICAO Annex 11 §2.11 control-zone-lateral-limits floor): " +
                    "got $authoredCtrRadiusNm"
            }
        }

        val aerodrome = Aerodrome(
            icao = AerodromeId(world.aerodrome.icao),
            elevation = Feet(world.aerodrome.elevationFeet),
            magneticVariation = Degrees(world.aerodrome.magneticVariationDegrees.toDouble()),
            transitionAltitude = Level.AltitudeFeet.unsafe(world.aerodrome.transitionAltitudeFeet),
            ctrApproximationRadius = authoredCtrRadiusNm
                ?.let(Meters::fromNauticalMiles)
                ?: Doctrine.IcaoAnnex11.CTR_FLOOR_5NM,
            aip = AerodromeAip(
                operationalSectors = operationalSectors,
                publishedVfrProcedures = publishedVfrProcedures,
            ),
            runways = runways,
            circuits = circuits,
            taxiways = taxiways,
            stands = stands,
            aprons = aprons,
            sids = world.aerodrome.sids.mapValues { (_, sid) -> sid.toSid() }
                .mapKeys { (id, _) -> SidId(id) },
            stars = world.aerodrome.stars.mapValues { (_, star) -> star.toStar() }
                .mapKeys { (id, _) -> StarId(id) },
            approaches = world.aerodrome.approaches.mapValues { (_, approach) -> approach.toInstrumentApproach() }
                .mapKeys { (id, _) -> ApproachId(id) },
            holdingPatterns = world.aerodrome.holdingPatterns.mapValues { (_, holdingPattern) ->
                holdingPattern.toHoldingPattern(paths)
            }.mapKeys { (id, _) -> HoldingPatternId(id) },
            // Pass 6 (D-AUDIT.12 closure): roles populated from the manifest
            // (parse-time-typed RoleName + Frequency). Authorities bridged
            // through LoaderDefaults — Pass 6 only recognises Placeholder;
            // D-AUDIT.11 will extend the sealed dispatch.
            roles = world.aerodrome.roles.mapValues { (_, role) ->
                AerodromeRole(
                    name = role.name,
                    authorities = LoaderDefaults.toAuthorityGrants(role.authorities),
                    frequency = role.frequencyMhz,
                )
            },
            // Aerodrome's modelled controllers — the published roles, each
            // assigned to a synthetic 1-1 controller id. The validator at
            // `validateRoleStaffing` (WorldValidation.kt) requires every
            // published role to have a corresponding controller; this is
            // the world-model alignment. The *sim runtime* separately
            // decides which subset to staff in any given test (via
            // ControllerView.staffedRoles populated from state.controllers).
            controllers = world.aerodrome.roles.keys.associate { role ->
                xyz.easiersaid.twr.protocol.ControllerId(
                    "${world.aerodrome.icao}_${role.name}",
                ) to setOf(role)
            },
            // Reference point looked up from a hardcoded table for now
            // (G1-DEF-11). Until the migration pipeline propagates lat/lon
            // through world-candidate.json, the loader recognises the
            // ICAO codes it knows about. Single-airport tests with
            // synthetic codes (e.g. "TEST") get null and bypass the
            // multi-aerodrome reprojection (no merge happens for them).
            referencePoint = REFERENCE_POINTS[AerodromeId(world.aerodrome.icao)],
        )

        return AviationWorld(
            geometry = PhysicalGeometry(points = positions, segments = segments),
            fixes = fixes,
            vfrRoutes = vfrRoutes,
            aerodromes = mapOf(AerodromeId(world.aerodrome.icao) to aerodrome),
            airspace = airspace,
            firs = firs,
        )
    }

    /**
     * Merge N single-aerodrome worlds into one multi-aerodrome world.
     *
     * Most entity keys are airport-prefixed by the upstream pipeline (points,
     * airspace volumes, VFR routes), so they merge cleanly by union. A few
     * namespaces are intentionally shared across airports — enroute fixes
     * (e.g. `GOLVA`, `DIMLO`) and FIRs — so duplicate keys are expected. For
     * those, the merge keeps the first occurrence; a cleaner cross-aerodrome
     * enroute-fix reconciliation step is a later phase, not a v1 blocker.
     *
     * Aerodrome IDs must be unique per world; a duplicate aerodrome is a hard
     * error.
     */
    fun mergeAviationWorlds(worlds: List<AviationWorld>): AviationWorld {
        if (worlds.isEmpty()) error("mergeAviationWorlds: at least one world required")
        if (worlds.size == 1) return worlds.single()

        fun <K, V> mergeStrict(maps: List<Map<K, V>>, label: String): Map<K, V> {
            val out = mutableMapOf<K, V>()
            for (map in maps) {
                for ((k, v) in map) {
                    val existing = out.put(k, v)
                    require(existing == null || existing == v) {
                        "Duplicate $label key '$k' across merged worlds"
                    }
                }
            }
            return out
        }

        fun <K, V> mergeFirstWins(maps: List<Map<K, V>>): Map<K, V> {
            val out = mutableMapOf<K, V>()
            for (map in maps) {
                for ((k, v) in map) {
                    out.putIfAbsent(k, v)
                }
            }
            return out
        }

        // G1-DEF-11: reproject each airport's airport-local Cartesian
        // geometry into a single shared frame before merging. Each
        // airport's xMeters/yMeters is relative to its own
        // [Aerodrome.referencePoint] (lat/lon). To produce a coherent
        // merged frame:
        //  1. Pick a global origin = arithmetic mean of all reference
        //     points present (deterministic, not order-dependent).
        //  2. For each airport, compute the displacement (Δx, Δy) from
        //     the global origin to the airport's reference point in
        //     metres (flat-earth ENU; accurate to <0.1% within 100 km).
        //  3. Translate every Position in that airport's geometry by
        //     this displacement.
        // Worlds without [referencePoint] are merged as-is (legacy /
        // synthetic worlds keep the previous behaviour).
        val reprojected = reprojectToSharedFrame(worlds)

        val geometry = PhysicalGeometry(
            points = mergeStrict(reprojected.map { it.geometry.points }, "point"),
            segments = mergeStrict(reprojected.map { it.geometry.segments }, "segment"),
        )
        // Fixes and FIRs are shared namespaces across airports; first-wins.
        val fixes = mergeFirstWins(reprojected.map { it.fixes })
        val firs = mergeFirstWins(reprojected.map { it.firs })
        val vfrRoutes = mergeStrict(reprojected.map { it.vfrRoutes }, "vfr route")
        val aerodromes = mergeStrict(reprojected.map { it.aerodromes }, "aerodrome")
        val airspace = mergeStrict(reprojected.map { it.airspace }, "airspace volume")

        return AviationWorld(
            geometry = geometry,
            fixes = fixes,
            vfrRoutes = vfrRoutes,
            aerodromes = aerodromes,
            airspace = airspace,
            firs = firs,
        )
    }
}

/**
 * Reproject each airport's geometry into a single shared frame, with
 * the global origin at the arithmetic mean of all
 * [Aerodrome.referencePoint]s.
 *
 * Strict per round-3 review: when reprojecting, *every* aerodrome
 * across all input worlds must have a reference point, else the
 * merge fails loudly. Silent partial reprojection (some airports in
 * shared frame, others in airport-local frame) is the foot-gun
 * G1-DEF-11 was meant to close.
 *
 * Loud fail also when any airport's ENU offset from the global
 * origin exceeds [MAX_ENU_OFFSET_M] — flat-earth approximation
 * breaks beyond ~150 km, and the right structural fix at that
 * scale is a spherical projection (deferred).
 */
private fun reprojectToSharedFrame(worlds: List<AviationWorld>): List<AviationWorld> {
        val allRefs = worlds.flatMap { world ->
            world.aerodromes.values.map { it.referencePoint }
        }
        // Round-3 fix (FP must-fix #1): require every aerodrome to have a
        // reference point when merging. The previous "< 2 → no reprojection"
        // shortcut produced silently-broken multi-aerodrome geometry the
        // moment any airport was missing from the loader's hardcoded table.
        require(allRefs.all { it != null }) {
            val missing = worlds.flatMap { world ->
                world.aerodromes.entries
                    .filter { (_, ad) -> ad.referencePoint == null }
                    .map { (id, _) -> id }
            }
            "mergeAviationWorlds: cannot reproject — aerodromes missing referencePoint: $missing. " +
                "Add them to WorldCandidateLoader.REFERENCE_POINTS or wait for G1-DEF-17."
        }
        val refs = allRefs.filterNotNull()
        if (refs.size < 2) return worlds  // single airport or all empty — nothing to reproject

        val originLat = refs.map { it.latitude }.average()
        val originLon = refs.map { it.longitude }.average()

        return worlds.map { world ->
            // Each AviationWorld carries one airport's geometry in this
            // pipeline (toWorld produces single-airport worlds). Pick
            // that airport's reference point.
            val ref = world.aerodromes.values
                .firstOrNull { it.referencePoint != null }?.referencePoint
                ?: return@map world  // unreachable after the require above for size>=2
            val (dx, dy) = enuOffsetMeters(originLat, originLon, ref.latitude, ref.longitude)
            // Round-3 fix (atc-general): flat-earth ENU is accurate to <0.1%
            // within ~100 km; beyond ~150 km the small-angle approximation
            // breaks (Earth curvature ~95 km sagitta at 1100 km). Fail loud
            // rather than produce silently-wrong geometry.
            require(kotlin.math.hypot(dx, dy) <= MAX_ENU_OFFSET_M) {
                "mergeAviationWorlds: ENU offset for aerodrome at $ref " +
                    "exceeds $MAX_ENU_OFFSET_M m (flat-earth approximation breaks). " +
                    "Use a spherical projection for global-scale scenarios."
            }
            val translated = world.geometry.points.mapValues { (_, p) ->
                Position(xMeters = p.xMeters + dx, yMeters = p.yMeters + dy, altitudeFeet = p.altitudeFeet)
            }
            world.copy(geometry = world.geometry.copy(points = translated))
        }
    }

    /** Maximum ENU offset for which the flat-earth projection remains valid. */
    private const val MAX_ENU_OFFSET_M: Double = 150_000.0

    /**
     * Flat-earth ENU offset (in metres) from the global origin
     * (originLat, originLon) to the airport reference (lat, lon).
     * Accurate to <0.1% for distances under ~100 km — within the
     * accuracy budget for VFR cross-aerodrome flight in G1.
     */
    private fun enuOffsetMeters(
        originLat: Double,
        originLon: Double,
        lat: Double,
        lon: Double,
    ): Pair<Double, Double> {
        val degToRad = kotlin.math.PI / 180.0
        val earthRadiusM = 6_378_137.0
        val cosOriginLat = kotlin.math.cos(originLat * degToRad)
        val dx = earthRadiusM * cosOriginLat * (lon - originLon) * degToRad
        val dy = earthRadiusM * (lat - originLat) * degToRad
        return dx to dy
    }

    /**
     * Hardcoded reference points for the airports the runtime currently
     * supports. Until the migration pipeline propagates lat/lon through
     * `world-candidate.json`, the loader recognises ICAO codes it knows
     * about. Sourced from X-Plane apt.dat.
     *
     * Adding a new airport: source from AIP / X-Plane apt.dat / Jepp.
     */
    private val REFERENCE_POINTS: Map<AerodromeId, LatLon> = mapOf(
        AerodromeId("LOWG") to LatLon.unsafe(latitude = 46.993056, longitude = 15.439167),
        AerodromeId("LJMB") to LatLon.unsafe(latitude = 46.480000, longitude = 15.686111),
    )

    private fun String.toSurfaceType(): SurfaceType =
        when (this) {
            "GROUND" -> SurfaceType.GROUND
            "RUNWAY" -> SurfaceType.RUNWAY
            "SKY" -> SurfaceType.SKY
            else -> error("Unsupported surface type: $this")
        }

    private fun CandidateVfrRouteAirspaceProfile?.toVfrRouteAirspaceProfile(): VfrRouteAirspaceProfile? =
        when (this?.kind) {
            "IN_VOLUME" -> airspaceVolumeId?.let(::AirspaceVolumeId)?.let(VfrRouteAirspaceProfile::InVolume)
            "IN_CLASS" -> airspaceClass?.let(AirspaceClass::valueOf)?.let(VfrRouteAirspaceProfile::InClass)
            "SEGMENTED" -> VfrRouteAirspaceProfile.Segmented(
                segments.map { segment ->
                    VfrRouteAirspaceSegment(
                        from = PointId(segment.fromPointId),
                        to = PointId(segment.toPointId),
                        airspaceVolume = AirspaceVolumeId(segment.airspaceVolumeId),
                    )
                },
            )
            else -> null
        }

    private fun CandidatePublishedVfrProcedure.toPublishedVfrProcedure(): PublishedVfrProcedure =
        PublishedVfrProcedure(
            id = PublishedVfrProcedureId(id),
            plateId = PlateId(plateId),
            kind = kind.toPublishedVfrProcedureKind(),
            publishedSequence = publishedSequence.map { reference -> reference.toPublishedPointReference() },
            associatedVfrRoutes = associatedVfrRouteIds.map(::VfrRouteId).toSet(),
            associatedOperationalSectors = associatedOperationalSectorIds.map(::OperationalSectorId).toSet(),
            associatedCircuits = associatedCircuitIds.map(::CircuitProcedureId).toSet(),
            contactRequirement = contactRequirement?.toContactRequirement(),
            advisories = advisories?.toPublishedProcedureAdvisories(),
            mapLabels = mapLabels.map { label ->
                PublishedMapLabel(
                    label = label.label,
                    location = label.location.toPublishedPointReference(),
                )
            },
            terminatesAt = terminatesAt?.toPublishedPointReference(),
            holdAt = holdAt?.toPublishedPointReference(),
            communicationFailure = communicationFailure?.toPublishedProcedureCommunicationFailure(),
            departureRunways = departureRunwayIds.map(::RunwayId).toSet(),
            applicableRunways = applicableRunwayIds.map(::RunwayId).toSet(),
        )

    private fun CandidateInstrumentApproach.toInstrumentApproach(): InstrumentApproach =
        InstrumentApproach(
            id = ApproachId(id),
            name = name,
            type = type.toApproachType(),
            runway = RunwayId(runwayId),
            waypoints = waypoints.map { waypoint -> waypoint.toWaypoint() },
            minimumAltitude = minimumAltitude.toApproachMinimum(),
            missedApproach = MissedApproachProcedure(
                waypoints = missedApproach.waypoints.map { waypoint -> waypoint.toWaypoint() },
                holdAt = HoldingPatternId(missedApproach.holdAtId),
            ),
        )

    private fun CandidateSid.toSid(): Sid =
        Sid(
            id = SidId(id),
            name = name,
            runway = RunwayId(runwayId),
            waypoints = waypoints.map { waypoint -> waypoint.toWaypoint() },
            transitions = transitions.mapValues { (_, transitionWaypoints) ->
                transitionWaypoints.map { waypoint -> waypoint.toWaypoint() }
            },
        )

    private fun CandidateStar.toStar(): Star =
        Star(
            id = StarId(id),
            name = name,
            waypoints = waypoints.map { waypoint -> waypoint.toWaypoint() },
            transitions = transitions.mapValues { (_, transitionWaypoints) ->
                transitionWaypoints.map { waypoint -> waypoint.toWaypoint() }
            },
        )

    private fun CandidateHoldingPattern.toHoldingPattern(
        paths: Map<String, WorldPath>,
    ): HoldingPattern =
        HoldingPattern(
            id = HoldingPatternId(id),
            fix = FixId(fixId),
            inboundCourse = Degrees(inboundCourseDegrees),
            turnDirection = turnDirection.toTurnDirection(),
            loop = paths.getValue(loopPathId),
            legTime = legTimeMinutes?.let(Minutes::unsafe),
            legDistance = legDistanceNm?.let(DmeDistanceNm::unsafe),
            maxSpeed = maxSpeedKnots?.let(Knots::unsafe),
            altitude = Level.AltitudeFeet.unsafe(altitudeFeet),
            stackSeparation = stackSeparationFeet?.let(::Feet),
        )

    private fun CandidateWaypoint.toWaypoint(): Waypoint =
        Waypoint(
            point = PointId(pointId),
            name = name,
            altitudeConstraint = altitudeConstraint?.toAltitudeConstraint(),
            speedConstraint = speedConstraint?.toSpeedConstraint(),
        )

    private fun CandidateApproachMinimum.toApproachMinimum(): ApproachMinimum =
        ApproachMinimum(
            type = type.toMinimumType(),
            altitude = Level.AltitudeFeet.unsafe(altitudeFeet),
            height = heightFeet?.let(Level.HeightFeet::unsafe),
        )

    private fun CandidateWaypointAltitudeConstraint.toAltitudeConstraint(): AltitudeConstraint =
        when (kind) {
            "AT" -> AltitudeConstraint.At(Level.AltitudeFeet.unsafe(requireNotNull(valueFeet)))
            "AT_OR_ABOVE" -> AltitudeConstraint.AtOrAbove(Level.AltitudeFeet.unsafe(requireNotNull(minimumFeet)))
            "AT_OR_BELOW" -> AltitudeConstraint.AtOrBelow(Level.AltitudeFeet.unsafe(requireNotNull(maximumFeet)))
            "BETWEEN" -> AltitudeConstraint.Between(
                minimum = Level.AltitudeFeet.unsafe(requireNotNull(minimumFeet)),
                maximum = Level.AltitudeFeet.unsafe(requireNotNull(maximumFeet)),
            )
            else -> error("Unsupported waypoint altitude constraint kind: $kind")
        }

    private fun CandidateWaypointSpeedConstraint.toSpeedConstraint(): SpeedConstraint =
        when (kind) {
            "AT" -> SpeedConstraint.At(Speed.InKnots(Knots.unsafe(requireNotNull(valueKnots))))
            "AT_OR_ABOVE" -> SpeedConstraint.AtOrAbove(Speed.InKnots(Knots.unsafe(requireNotNull(minimumKnots))))
            "AT_OR_BELOW" -> SpeedConstraint.AtOrBelow(Speed.InKnots(Knots.unsafe(requireNotNull(maximumKnots))))
            "BETWEEN" -> SpeedConstraint.Between(
                minimum = Speed.InKnots(Knots.unsafe(requireNotNull(minimumKnots))),
                maximum = Speed.InKnots(Knots.unsafe(requireNotNull(maximumKnots))),
            )
            else -> error("Unsupported waypoint speed constraint kind: $kind")
        }

    private fun String.toPublishedVfrProcedureKind(): PublishedVfrProcedureKind =
        when (this) {
            "ARRIVAL" -> PublishedVfrProcedureKind.ARRIVAL
            "DEPARTURE" -> PublishedVfrProcedureKind.DEPARTURE
            "TRANSIT" -> PublishedVfrProcedureKind.TRANSIT
            "CIRCUIT_PUBLICATION" -> PublishedVfrProcedureKind.CIRCUIT_PUBLICATION
            "CIRCUIT_ATTACHED_HOLD" -> PublishedVfrProcedureKind.CIRCUIT_ATTACHED_HOLD
            else -> error("Unsupported published VFR procedure kind: $this")
        }

    private fun String.toCircuitDirection(): CircuitDirection =
        when (this) {
            "LEFT_HAND" -> CircuitDirection.LEFT_HAND
            "RIGHT_HAND" -> CircuitDirection.RIGHT_HAND
            else -> error("Unsupported circuit direction: $this")
        }

    private fun String.toLegName(): LegName =
        when (this) {
            "UPWIND" -> LegName.UPWIND
            "CROSSWIND" -> LegName.CROSSWIND
            "DOWNWIND" -> LegName.DOWNWIND
            "BASE" -> LegName.BASE
            "FINAL" -> LegName.FINAL
            else -> error("Unsupported circuit leg name: $this")
        }

    private fun String.toJoinType(): JoinType =
        when (this) {
            "STRAIGHT_IN" -> JoinType.STRAIGHT_IN
            "BASE" -> JoinType.BASE
            "DOWNWIND" -> JoinType.DOWNWIND
            "CROSSWIND" -> JoinType.CROSSWIND
            "MID_DOWNWIND" -> JoinType.MID_DOWNWIND
            "OVERHEAD" -> JoinType.OVERHEAD
            "LONG_FINAL" -> JoinType.LONG_FINAL
            else -> error("Unsupported join type: $this")
        }

    private fun String.toApproachType(): ApproachType =
        when (this) {
            "ILS" -> ApproachType.ILS
            "LOC" -> ApproachType.LOC
            "RNAV" -> ApproachType.RNAV
            "RNP" -> ApproachType.RNP
            "VOR" -> ApproachType.VOR
            "NDB" -> ApproachType.NDB
            "SRA" -> ApproachType.SRA
            "PAR" -> ApproachType.PAR
            else -> error("Unsupported approach type: $this")
        }

    private fun String.toMinimumType(): MinimumType =
        when (this) {
            "DECISION_ALTITUDE" -> MinimumType.DECISION_ALTITUDE
            "MINIMUM_DESCENT_ALTITUDE" -> MinimumType.MINIMUM_DESCENT_ALTITUDE
            else -> error("Unsupported minimum type: $this")
        }

    private fun String.toTurnDirection(): TurnDirection =
        when (this) {
            "LEFT" -> TurnDirection.LEFT
            "RIGHT" -> TurnDirection.RIGHT
            else -> error("Unsupported turn direction: $this")
        }

    private fun CandidatePublishedPointReference.toPublishedPointReference(): PublishedPointReference =
        when (kind) {
            "FIX" -> PublishedPointReference.Fix(reference = reference, point = PointId(requireNotNull(pointId)))
            "NAMED_POINT" -> PublishedPointReference.NamedPoint(reference = reference, point = PointId(requireNotNull(pointId)))
            "OPERATIONAL_SECTOR_ANCHOR" ->
                PublishedPointReference.SectorAnchor(reference = reference, point = PointId(requireNotNull(pointId)))
            "LITERAL" -> PublishedPointReference.Literal(reference = reference)
            else -> error("Unsupported published point reference type: $kind")
        }

    private fun String.toOperationalSectorKind(): OperationalSectorKind =
        when (this) {
            "VFR_OPERATIONAL" -> OperationalSectorKind.VFR_OPERATIONAL
            "IFR_HOLDING_SECTOR" -> OperationalSectorKind.IFR_HOLDING_SECTOR
            "VFR_TRAINING_AREA" -> OperationalSectorKind.VFR_TRAINING_AREA
            "GLIDER_SECTOR" -> OperationalSectorKind.GLIDER_SECTOR
            "NIGHT_VFR_SECTOR" -> OperationalSectorKind.NIGHT_VFR_SECTOR
            "HELICOPTER_OPERATIONAL" -> OperationalSectorKind.HELICOPTER_OPERATIONAL
            else -> error("Unsupported operational sector kind: $this")
        }

    private fun CandidateOperationalSectorAnchor.toOperationalSectorAnchor(): OperationalSectorAnchor =
        when (kind) {
            "CTR_BOUNDARY_REPORTING_POINT" -> OperationalSectorAnchor.CtrBoundaryReportingPoint(PointId(requireNotNull(pointId)))
            "REPORTING_POINT" -> OperationalSectorAnchor.ReportingPoint(PointId(requireNotNull(pointId)))
            "NAVAID" -> OperationalSectorAnchor.Navaid(PointId(requireNotNull(pointId)))
            else -> error("Unsupported operational sector anchor role: $kind")
        }

    private fun String.toOperationalSectorCtrRelation(): OperationalSectorCtrRelation =
        when (this) {
            "WITHIN_CTR" -> OperationalSectorCtrRelation.WITHIN_CTR
            "OVERLAPS_CTR" -> OperationalSectorCtrRelation.OVERLAPS_CTR
            "BOUNDARY_OR_OVERLAP" -> OperationalSectorCtrRelation.BOUNDARY_OR_OVERLAP
            "ADJACENT_TO_CTR" -> OperationalSectorCtrRelation.ADJACENT_TO_CTR
            "INSIDE_TMA_OUTSIDE_CTR" -> OperationalSectorCtrRelation.INSIDE_TMA_OUTSIDE_CTR
            else -> error("Unsupported operational sector CTR relation: $this")
        }

    private fun CandidateAltitudeBand.toAltitudeBand(): AltitudeBand =
        AltitudeBand(
            lower = lower.toAltitudeBoundary(),
            upper = upper?.toAltitudeBoundary() ?: AltitudeBoundary.Unlimited,
        )

    private fun CandidateAltitudeBoundary.toAltitudeBoundary(): AltitudeBoundary =
        when (kind) {
            "SURFACE" -> AltitudeBoundary.Surface
            "AT_LEVEL" -> AltitudeBoundary.AtLevel(
                when (levelType) {
                    "ALTITUDE_FEET" -> Level.AltitudeFeet.unsafe(value ?: 0)
                    "FLIGHT_LEVEL" -> Level.FlightLevel.unsafe(value ?: 0)
                    "HEIGHT_FEET" -> Level.HeightFeet.unsafe(value ?: 0)
                    else -> error("Unsupported altitude boundary level type: $levelType")
                },
            )
            "UNLIMITED" -> AltitudeBoundary.Unlimited
            else -> error("Unsupported altitude boundary kind: $kind")
        }

    private fun CandidateContactRequirement.toContactRequirement(): ContactRequirement =
        ContactRequirement(
            role = RoleName.valueOf(role),
            timing = timing.toContactTiming(),
        )

    private fun CandidateContactTiming.toContactTiming(): ContactTiming =
        when (kind) {
            "BEFORE_ENTRY" -> ContactTiming.BeforeEntry
            "BEFORE_POINT" -> ContactTiming.BeforePoint(PointId(requireNotNull(pointId)))
            "AT_POINT" -> ContactTiming.AtPoint(PointId(requireNotNull(pointId)))
            "DISTANCE_BEFORE" -> ContactTiming.DistanceBefore(
                point = PointId(requireNotNull(pointId)),
                distance = DmeDistanceNm.unsafe(requireNotNull(distanceNm)),
            )
            "BY_ALTITUDE" -> ContactTiming.ByAltitude(
                when (levelType) {
                    "ALTITUDE_FEET" -> Level.AltitudeFeet.unsafe(requireNotNull(value))
                    "FLIGHT_LEVEL" -> Level.FlightLevel.unsafe(requireNotNull(value))
                    "HEIGHT_FEET" -> Level.HeightFeet.unsafe(requireNotNull(value))
                    else -> error("Unsupported contact timing altitude level type: $levelType")
                },
            )
            else -> error("Unsupported contact timing kind: $kind")
        }

    private fun CandidatePublishedProcedureAdvisories.toPublishedProcedureAdvisories(): PublishedProcedureAdvisories =
        PublishedProcedureAdvisories(
            contact = contact,
            altitude = altitude,
            route = route,
            reporting = reporting,
            availability = availability,
            specialProcedure = specialProcedure,
            noiseAbatement = noiseAbatement,
            speedCap = speedCap,
            squawkConvention = squawkConvention,
            activationHours = activationHours,
            equipmentMinimum = equipmentMinimum,
            language = language,
            general = general,
        )

    private fun CandidatePublishedProcedureCommunicationFailure.toPublishedProcedureCommunicationFailure():
        PublishedProcedureCommunicationFailure =
        PublishedProcedureCommunicationFailure(
            beforeContactEstablished = beforeContactEstablished,
            afterContactEstablishedExitSequence = afterContactEstablishedExitSequence.map { reference ->
                reference.toPublishedPointReference()
            },
            note = note,
        )

    private fun String.toFixType(): FixType =
        when (this) {
            "WAYPOINT" -> FixType.WAYPOINT
            "VOR" -> FixType.VOR
            "NDB" -> FixType.NDB
            "MARKER" -> FixType.MARKER
            else -> error("Unsupported fix type: $this")
        }

    private fun String?.toHoldingPointType(): HoldingPointType =
        when (this) {
            null -> HoldingPointType.CAT_A
            "CAT_A" -> HoldingPointType.CAT_A
            "CAT_B" -> HoldingPointType.CAT_B
            "INTERMEDIATE" -> HoldingPointType.INTERMEDIATE
            else -> error("Unsupported holding point type: $this")
        }
