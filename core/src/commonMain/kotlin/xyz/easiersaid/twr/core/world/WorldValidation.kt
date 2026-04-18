package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId

enum class WorldValidationCode {
    ORPHAN_GEOMETRY_POINT,
    ORPHAN_GEOMETRY_SEGMENT,
    GEOMETRY_SEGMENT_UNKNOWN_ENDPOINT,
    UNKNOWN_GEOMETRY_POINT_REFERENCE,
    UNKNOWN_GEOMETRY_SEGMENT_REFERENCE,
    POINT_OUTSIDE_AIRSPACE,
    UNKNOWN_FIR,
    FIR_VOLUME_MISMATCH,
    UNKNOWN_AIRSPACE_VOLUME,
    AIRSPACE_BOUNDARY_VERTEX_NOT_IN_MEMBERSHIP,
    VFR_ROUTE_UNKNOWN_VOLUME,
    VFR_ROUTE_POINT_NOT_IN_VOLUME,
    VFR_ROUTE_SEGMENT_SEQUENCE_MISMATCH,
    VFR_ROUTE_SEGMENT_UNKNOWN_VOLUME,
    VFR_ROUTE_SEGMENT_ENDPOINT_NOT_IN_VOLUME,
    UNIFORM_VFR_ROUTE_CONTROLLED_CLASS_WITHOUT_VOLUME,
    MISSING_RUNWAY_HOLDING_POINT,
    UNREACHABLE_STAND_FROM_HOLDING_POINT,
    UNKNOWN_RUNWAY_EXIT_TAXIWAY,
    RUNWAY_EXIT_NOT_ON_RUNWAY,
    RUNWAY_EXIT_NOT_ON_TAXIWAY,
    SID_UNKNOWN_RUNWAY,
    SID_NOT_AT_RUNWAY_THRESHOLD,
    STAR_TERMINAL_POINT_UNSHARED,
    APPROACH_UNKNOWN_RUNWAY,
    APPROACH_NOT_AT_RUNWAY_THRESHOLD,
    APPROACH_UNKNOWN_MISSED_HOLD,
    TAXIWAY_SEGMENT_OVERLAP,
    APRON_SEGMENT_OVERLAP,
    HOLDING_PATTERN_UNKNOWN_FIX,
    HOLDING_PATTERN_NOT_CLOSED,
    UNSTAFFED_ROLE,
    RECIPROCAL_RUNWAYS_DO_NOT_SHARE_SEGMENT,
    DUPLICATE_AIRWAY_NAME,
    DUPLICATE_SID_NAME,
    DUPLICATE_STAR_NAME,
    OPERATIONAL_SECTOR_UNKNOWN_PROCEDURE,
    PUBLISHED_VFR_PROCEDURE_UNKNOWN_ROUTE,
    PUBLISHED_VFR_PROCEDURE_UNKNOWN_SECTOR,
    PUBLISHED_VFR_PROCEDURE_UNKNOWN_CIRCUIT
}

data class WorldValidationIssue(
    val code: WorldValidationCode,
    val message: String
)

data class WorldValidationReport(
    val issues: List<WorldValidationIssue>
) {
    val isValid: Boolean
        get() = issues.isEmpty()
}

fun AviationWorld.validate(): WorldValidationReport =
    WorldValidationReport(
        validateGeometryReferencesAndClaims() +
            validateAirspaceCoverage() +
            validateVfrRouteAirspaceProfiles() +
            validateFirMembership() +
            validateGlobalNames() +
            aerodromes.values.flatMap(::validateAerodrome)
    )

private fun AviationWorld.validateGeometryReferencesAndClaims(): List<WorldValidationIssue> {
    val claimedPoints = deriveEntitiesByPoint().keys
    val claimedSegments = collectClaimedSegments()

    val endpointIssues = geometry.segments.keys
        .filter { segment -> segment.first !in geometry.points || segment.second !in geometry.points }
        .map { segment ->
            WorldValidationIssue(
                WorldValidationCode.GEOMETRY_SEGMENT_UNKNOWN_ENDPOINT,
                "Geometry segment ${segment.describe()} references a point missing from the geometry point map"
            )
        }

    val unknownPointIssues = claimedPoints
        .filter { point -> point !in geometry.points }
        .sortedBy(PointId::value)
        .map { point ->
            WorldValidationIssue(
                WorldValidationCode.UNKNOWN_GEOMETRY_POINT_REFERENCE,
                "Entity reference point ${point.value} is missing from physical geometry"
            )
        }

    val unknownSegmentIssues = claimedSegments
        .filter { segment -> segment !in geometry.segments }
        .sortedBy(GeometrySegmentId::describe)
        .map { segment ->
            WorldValidationIssue(
                WorldValidationCode.UNKNOWN_GEOMETRY_SEGMENT_REFERENCE,
                "Entity reference segment ${segment.describe()} is missing from physical geometry"
            )
        }

    val orphanPointIssues = geometry.points.keys
        .filter { point -> point !in claimedPoints }
        .sortedBy(PointId::value)
        .map { point ->
            WorldValidationIssue(
                WorldValidationCode.ORPHAN_GEOMETRY_POINT,
                "Geometry point ${point.value} is not claimed by any entity"
            )
        }

    val orphanSegmentIssues = geometry.segments.keys
        .filter { segment -> segment !in claimedSegments }
        .sortedBy(GeometrySegmentId::describe)
        .map { segment ->
            WorldValidationIssue(
                WorldValidationCode.ORPHAN_GEOMETRY_SEGMENT,
                "Geometry segment ${segment.describe()} is not claimed by any entity"
            )
        }

    return endpointIssues + unknownPointIssues + unknownSegmentIssues + orphanPointIssues + orphanSegmentIssues
}

private fun AviationWorld.validateFirMembership(): List<WorldValidationIssue> {
    val volumeIssues = airspace.values.mapNotNull { volume ->
        val fir = firs[volume.fir]
        when {
            fir == null -> WorldValidationIssue(
                WorldValidationCode.UNKNOWN_FIR,
                "Airspace volume ${volume.id.value} references unknown FIR ${volume.fir.value}"
            )

            volume.id !in fir.volumes -> WorldValidationIssue(
                WorldValidationCode.FIR_VOLUME_MISMATCH,
                "Airspace volume ${volume.id.value} is not listed in FIR ${fir.id.value}"
            )

            else -> null
        }
    }

    val firIssues = firs.values.flatMap { fir ->
        fir.volumes
            .filter { volumeId -> volumeId !in airspace }
            .sortedBy { volumeId -> volumeId.value }
            .map { volumeId ->
                WorldValidationIssue(
                    WorldValidationCode.UNKNOWN_AIRSPACE_VOLUME,
                    "FIR ${fir.id.value} references unknown airspace volume ${volumeId.value}"
                )
            }
    }

    return volumeIssues + firIssues
}

private fun AviationWorld.validateGlobalNames(): List<WorldValidationIssue> =
    airways.values
        .groupBy { airway -> airway.name }
        .filterValues { duplicates -> duplicates.size > 1 }
        .map { (name, duplicates) ->
            WorldValidationIssue(
                WorldValidationCode.DUPLICATE_AIRWAY_NAME,
                "Airway name $name is duplicated across ${duplicates.map { airway -> airway.id.value }.sorted()}"
            )
        }

private fun AviationWorld.validateAerodrome(aerodrome: Aerodrome): List<WorldValidationIssue> =
    validateRunwayHoldingPoints(aerodrome) +
        validateStandReachability(aerodrome) +
        validateRunwayExits(aerodrome) +
        validateProcedureAnchoring(aerodrome) +
        validateSegmentOwnership(aerodrome) +
        validateHoldingPatterns(aerodrome) +
        validateAipReferences(aerodrome) +
        validateRoleStaffing(aerodrome) +
        validateReciprocalRunways(aerodrome) +
        validateAerodromeNames(aerodrome)

private fun validateRunwayHoldingPoints(aerodrome: Aerodrome): List<WorldValidationIssue> {
    val runwayProtectedByHoldingPoint = aerodrome.taxiways.values
        .flatMap { taxiway -> taxiway.holdingPoints }
        .mapNotNull { holdingPoint -> holdingPoint.runway }
        .toSet()

    return aerodrome.runways.values
        .filter { runway -> runway.id !in runwayProtectedByHoldingPoint }
        .sortedBy { runway -> runway.id.value }
        .map { runway ->
            WorldValidationIssue(
                WorldValidationCode.MISSING_RUNWAY_HOLDING_POINT,
                "Aerodrome ${aerodrome.icao.value} has no holding point protecting runway ${runway.id.value}"
            )
        }
}

private fun validateStandReachability(aerodrome: Aerodrome): List<WorldValidationIssue> {
    val adjacency = groundAdjacency(aerodrome)
    val holdingPoints = aerodrome.taxiways.values.flatMap { taxiway -> taxiway.holdingPoints }
    val stands = aerodrome.stands.values

    return holdingPoints.flatMap { holdingPoint ->
        val reachable = reachablePointsFrom(holdingPoint.point, adjacency)
        stands
            .filter { stand -> stand.point !in reachable }
            .sortedBy { stand -> stand.id.value }
            .map { stand ->
                WorldValidationIssue(
                    WorldValidationCode.UNREACHABLE_STAND_FROM_HOLDING_POINT,
                    "Stand ${stand.id.value} is not reachable from holding point " +
                        "${holdingPoint.point.value} at aerodrome ${aerodrome.icao.value}"
                )
            }
    }
}

private fun validateRunwayExits(aerodrome: Aerodrome): List<WorldValidationIssue> =
    aerodrome.runways.values.flatMap { runway ->
        runway.exits.flatMap { exit ->
            val taxiway = aerodrome.taxiways[exit.taxiway]
            if (taxiway == null) {
                return@flatMap listOf(
                    WorldValidationIssue(
                        WorldValidationCode.UNKNOWN_RUNWAY_EXIT_TAXIWAY,
                        "Runway ${runway.id.value} references unknown exit taxiway ${exit.taxiway.value}"
                    )
                )
            }
            listOfNotNull(
                if (exit.point !in runway.path.points) WorldValidationIssue(
                    WorldValidationCode.RUNWAY_EXIT_NOT_ON_RUNWAY,
                    "Runway exit point ${exit.point.value} is not on runway ${runway.id.value}"
                ) else null,
                if (exit.point !in taxiway.path.points) WorldValidationIssue(
                    WorldValidationCode.RUNWAY_EXIT_NOT_ON_TAXIWAY,
                    "Runway exit point ${exit.point.value} is not on taxiway ${taxiway.id.value}"
                ) else null
            )
        }
    }

private fun AviationWorld.validateProcedureAnchoring(
    aerodrome: Aerodrome
): List<WorldValidationIssue> {
    val holdingFixPoints = aerodrome.holdingPatterns.values
        .mapNotNull { holdingPattern -> fixes[holdingPattern.fix]?.point }
        .toSet()
    val approachEntryPoints = aerodrome.approaches.values
        .mapNotNull { approach -> approach.waypoints.firstOrNull()?.point }
        .toSet()

    val sidIssues = aerodrome.sids.values.mapNotNull { sid ->
        val runway = aerodrome.runways[sid.runway]
        val firstPoint = sid.waypoints.firstOrNull()?.point
        when {
            runway == null -> WorldValidationIssue(
                WorldValidationCode.SID_UNKNOWN_RUNWAY,
                "SID ${sid.id.value} references unknown runway ${sid.runway.value} " +
                    "at aerodrome ${aerodrome.icao.value}"
            )

            firstPoint != runway.threshold -> WorldValidationIssue(
                WorldValidationCode.SID_NOT_AT_RUNWAY_THRESHOLD,
                "SID ${sid.id.value} does not start at runway ${runway.id.value} " +
                    "threshold ${runway.threshold.value}"
            )

            else -> null
        }
    }

    val starIssues = aerodrome.stars.values.mapNotNull { star ->
        val terminalPoint = star.waypoints.lastOrNull()?.point ?: return@mapNotNull null
        if (terminalPoint !in approachEntryPoints && terminalPoint !in holdingFixPoints) {
            WorldValidationIssue(
                WorldValidationCode.STAR_TERMINAL_POINT_UNSHARED,
                "STAR ${star.id.value} ends at point ${terminalPoint.value}, " +
                    "not shared with an approach/holding fix at ${aerodrome.icao.value}"
            )
        } else null
    }

    val approachIssues = aerodrome.approaches.values.flatMap { approach ->
        val runway = aerodrome.runways[approach.runway]
        val lastPoint = approach.waypoints.lastOrNull()?.point
        listOfNotNull(
            when {
                runway == null -> WorldValidationIssue(
                    WorldValidationCode.APPROACH_UNKNOWN_RUNWAY,
                    "Approach ${approach.id.value} references unknown runway " +
                        "${approach.runway.value} at aerodrome ${aerodrome.icao.value}"
                )

                lastPoint != runway.threshold -> WorldValidationIssue(
                    WorldValidationCode.APPROACH_NOT_AT_RUNWAY_THRESHOLD,
                    "Approach ${approach.id.value} does not end at runway " +
                        "${runway.id.value} threshold ${runway.threshold.value}"
                )

                else -> null
            },
            if (approach.missedApproach.holdAt !in aerodrome.holdingPatterns) WorldValidationIssue(
                WorldValidationCode.APPROACH_UNKNOWN_MISSED_HOLD,
                "Approach ${approach.id.value} references unknown missed-approach " +
                    "hold ${approach.missedApproach.holdAt.value}"
            ) else null
        )
    }

    return sidIssues + starIssues + approachIssues
}

private fun validateSegmentOwnership(aerodrome: Aerodrome): List<WorldValidationIssue> {
    val taxiwayOverlaps = aerodrome.taxiways.values
        .flatMap { taxiway ->
            taxiway.path.geometrySegmentIds().map { segment -> segment to taxiway.id.value }
        }
        .groupBy(
            keySelector = { (segment, _) -> segment },
            valueTransform = { (_, taxiwayId) -> taxiwayId }
        )
        .filterValues { owners -> owners.distinct().size > 1 }
        .map { (segment, owners) ->
            WorldValidationIssue(
                WorldValidationCode.TAXIWAY_SEGMENT_OVERLAP,
                "Taxiway segment ${segment.describe()} is claimed by " +
                    "multiple taxiways ${owners.distinct().sorted()}"
            )
        }

    val apronOverlaps = aerodrome.aprons.values
        .flatMap { apron ->
            apron.paths.flatMap { path ->
                path.geometrySegmentIds().map { segment -> segment to apron.id.value }
            }
        }
        .groupBy(
            keySelector = { (segment, _) -> segment },
            valueTransform = { (_, apronId) -> apronId }
        )
        .filterValues { owners -> owners.distinct().size > 1 }
        .map { (segment, owners) ->
            WorldValidationIssue(
                WorldValidationCode.APRON_SEGMENT_OVERLAP,
                "Apron segment ${segment.describe()} is claimed by " +
                    "multiple aprons ${owners.distinct().sorted()}"
            )
        }

    return taxiwayOverlaps + apronOverlaps
}

private fun AviationWorld.validateHoldingPatterns(
    aerodrome: Aerodrome
): List<WorldValidationIssue> =
    aerodrome.holdingPatterns.values.flatMap { holdingPattern ->
        listOfNotNull(
            if (holdingPattern.fix !in fixes) WorldValidationIssue(
                WorldValidationCode.HOLDING_PATTERN_UNKNOWN_FIX,
                "Holding pattern ${holdingPattern.id.value} references unknown fix ${holdingPattern.fix.value}"
            ) else null,
            if (holdingPattern.loop.points.first() != holdingPattern.loop.points.last()) WorldValidationIssue(
                WorldValidationCode.HOLDING_PATTERN_NOT_CLOSED,
                "Holding pattern ${holdingPattern.id.value} is not a closed loop"
            ) else null
        )
    }

private fun AviationWorld.validateAipReferences(
    aerodrome: Aerodrome
): List<WorldValidationIssue> {
    val sectorIssues = aerodrome.aip.operationalSectors.values.flatMap { sector ->
        sector.associatedProcedures
            .filter { procedureId -> procedureId !in aerodrome.aip.publishedVfrProcedures }
            .sortedBy { procedureId -> procedureId.value }
            .map { procedureId ->
                WorldValidationIssue(
                    WorldValidationCode.OPERATIONAL_SECTOR_UNKNOWN_PROCEDURE,
                    "Operational sector ${sector.id.value} at aerodrome ${aerodrome.icao.value} " +
                        "references unknown published VFR procedure ${procedureId.value}"
                )
            }
    }

    val procedureIssues = aerodrome.aip.publishedVfrProcedures.values.flatMap { procedure ->
        val unknownRoutes = procedure.associatedVfrRoutes
            .filter { routeId -> routeId !in vfrRoutes }
            .sortedBy { routeId -> routeId.value }
            .map { routeId ->
                WorldValidationIssue(
                    WorldValidationCode.PUBLISHED_VFR_PROCEDURE_UNKNOWN_ROUTE,
                    "Published VFR procedure ${procedure.id.value} at aerodrome ${aerodrome.icao.value} " +
                        "references unknown VFR route ${routeId.value}"
                )
            }
        val unknownSectors = procedure.associatedOperationalSectors
            .filter { sectorId -> sectorId !in aerodrome.aip.operationalSectors }
            .sortedBy { sectorId -> sectorId.value }
            .map { sectorId ->
                WorldValidationIssue(
                    WorldValidationCode.PUBLISHED_VFR_PROCEDURE_UNKNOWN_SECTOR,
                    "Published VFR procedure ${procedure.id.value} at aerodrome ${aerodrome.icao.value} " +
                        "references unknown operational sector ${sectorId.value}"
                )
            }
        val unknownCircuits = procedure.associatedCircuits
            .filter { circuitId -> circuitId !in aerodrome.circuits }
            .sortedBy { circuitId -> circuitId.value }
            .map { circuitId ->
                WorldValidationIssue(
                    WorldValidationCode.PUBLISHED_VFR_PROCEDURE_UNKNOWN_CIRCUIT,
                    "Published VFR procedure ${procedure.id.value} at aerodrome ${aerodrome.icao.value} " +
                        "references unknown circuit procedure ${circuitId.value}"
                )
            }
        unknownRoutes + unknownSectors + unknownCircuits
    }

    return sectorIssues + procedureIssues
}

private fun validateRoleStaffing(aerodrome: Aerodrome): List<WorldValidationIssue> {
    val staffedRoles = aerodrome.controllers.values.flatten().toSet()

    return aerodrome.roles.keys
        .filter { role -> role !in staffedRoles }
        .sortedBy(RoleName::name)
        .map { role ->
            WorldValidationIssue(
                WorldValidationCode.UNSTAFFED_ROLE,
                "Aerodrome ${aerodrome.icao.value} declares role ${role.name} without an assigned controller"
            )
        }
}

private fun validateReciprocalRunways(aerodrome: Aerodrome): List<WorldValidationIssue> {
    val reciprocalPairs = aerodrome.runways.values.mapNotNull { runway ->
        val reciprocalId = runway.id.reciprocal() ?: return@mapNotNull null
        val reciprocal = aerodrome.runways[reciprocalId] ?: return@mapNotNull null
        val pair = listOf(runway.id, reciprocal.id).sortedBy(RunwayId::value)
        Triple(pair[0], pair[1], runway to reciprocal)
    }.distinctBy { (first, second, _) -> first to second }

    return reciprocalPairs.mapNotNull { (_, _, runways) ->
        val (runway, reciprocal) = runways
        val sharedSegments = runway.path.geometrySegmentIds().toSet()
            .intersect(reciprocal.path.geometrySegmentIds().toSet())

        if (sharedSegments.isEmpty()) {
            WorldValidationIssue(
                WorldValidationCode.RECIPROCAL_RUNWAYS_DO_NOT_SHARE_SEGMENT,
                "Reciprocal runways ${runway.id.value} and ${reciprocal.id.value} " +
                    "at aerodrome ${aerodrome.icao.value} do not share any segment"
            )
        } else null
    }
}

private fun validateAerodromeNames(aerodrome: Aerodrome): List<WorldValidationIssue> {
    val sidDuplicates = aerodrome.sids.values
        .groupBy { sid -> sid.name }
        .filterValues { duplicates -> duplicates.size > 1 }
        .map { (name, duplicates) ->
            WorldValidationIssue(
                WorldValidationCode.DUPLICATE_SID_NAME,
                "Aerodrome ${aerodrome.icao.value} has duplicate SID name $name " +
                    "across ${duplicates.map { sid -> sid.id.value }.sorted()}"
            )
        }

    val starDuplicates = aerodrome.stars.values
        .groupBy { star -> star.name }
        .filterValues { duplicates -> duplicates.size > 1 }
        .map { (name, duplicates) ->
            WorldValidationIssue(
                WorldValidationCode.DUPLICATE_STAR_NAME,
                "Aerodrome ${aerodrome.icao.value} has duplicate STAR name $name " +
                    "across ${duplicates.map { star -> star.id.value }.sorted()}"
            )
        }

    return sidDuplicates + starDuplicates
}

private fun groundAdjacency(aerodrome: Aerodrome): Map<PointId, Set<PointId>> {
    val allPaths = aerodrome.taxiways.values.map { it.path } +
        aerodrome.aprons.values.flatMap { it.paths }
    val edges = allPaths.flatMap { path ->
        path.segmentIds().flatMap { segment ->
            listOf(segment.from to segment.to, segment.to to segment.from)
        }
    }
    return edges.groupBy(
        keySelector = { (from, _) -> from },
        valueTransform = { (_, to) -> to }
    ).mapValues { (_, neighbors) -> neighbors.toSet() }
}

private tailrec fun reachablePointsFrom(
    frontier: List<PointId>,
    adjacency: Map<PointId, Set<PointId>>,
    visited: Set<PointId> = emptySet()
): Set<PointId> {
    if (frontier.isEmpty()) return visited
    val nextVisited = visited + frontier
    val nextFrontier = frontier.flatMap { point ->
        adjacency[point].orEmpty().filter { it !in nextVisited }
    }.distinct()
    return reachablePointsFrom(nextFrontier, adjacency, nextVisited)
}

private fun reachablePointsFrom(
    origin: PointId,
    adjacency: Map<PointId, Set<PointId>>
): Set<PointId> =
    reachablePointsFrom(listOf(origin), adjacency, emptySet())

private fun AviationWorld.collectClaimedSegments(): Set<GeometrySegmentId> =
    buildSet {
        fun addPath(path: Path) { addAll(path.geometrySegmentIds()) }

        aerodromes.values.forEach { aerodrome ->
            aerodrome.runways.values.forEach { runway -> addPath(runway.path) }
            aerodrome.taxiways.values.forEach { taxiway -> addPath(taxiway.path) }
            aerodrome.aprons.values.forEach { apron -> apron.paths.forEach(::addPath) }
            aerodrome.circuits.values.forEach { circuit ->
                circuit.legs.forEach { leg -> addPath(leg.path) }
                circuit.joinProcedures.mapNotNull { join -> join.entryPath }.forEach(::addPath)
                circuit.extendedDownwind?.let { extension ->
                    addPath(extension.extendedPath)
                    extension.offRamps.forEach { offRamp -> addPath(offRamp.path) }
                }
                circuit.orbitPoints.forEach { orbit -> addPath(orbit.loop) }
                addPath(circuit.goAroundPath)
            }
            aerodrome.sids.values.forEach { sid ->
                sid.waypoints.asPathOrNull()?.let(::addPath)
                sid.transitions.values.forEach { transition -> transition.asPathOrNull()?.let(::addPath) }
            }
            aerodrome.stars.values.forEach { star ->
                star.waypoints.asPathOrNull()?.let(::addPath)
                star.transitions.values.forEach { transition -> transition.asPathOrNull()?.let(::addPath) }
            }
            aerodrome.approaches.values.forEach { approach ->
                approach.waypoints.asPathOrNull()?.let(::addPath)
                approach.missedApproach.waypoints.asPathOrNull()?.let(::addPath)
            }
            aerodrome.holdingPatterns.values.forEach { addPath(it.loop) }
        }

        airways.values.forEach { airway -> airway.waypoints.asPathOrNull()?.let(::addPath) }
        vfrRoutes.values.forEach { route -> route.waypoints.asPathOrNull()?.let(::addPath) }
        airspace.values.forEach { volume ->
            volume.boundary?.rings.orEmpty().forEach { ring -> addPath(ring.asClosedPath()) }
        }
        aerodromes.values.forEach { aerodrome ->
            aerodrome.aip.operationalSectors.values.forEach { sector ->
                sector.boundary.rings.forEach { ring -> addPath(ring.asClosedPath()) }
            }
        }
    }

private fun RunwayId.reciprocal(): RunwayId? {
    val match = RUNWAY_DESIGNATOR.matchEntire(value) ?: return null
    val numeric = match.groupValues[1].toInt()
    val suffix = match.groupValues[2]
    val reciprocalNumeric = ((numeric + 17) % 36) + 1
    val reciprocalSuffix = when (suffix) {
        "L" -> "R"
        "R" -> "L"
        else -> suffix
    }
    return RunwayId(reciprocalNumeric.toString().padStart(2, '0') + reciprocalSuffix)
}

private val RUNWAY_DESIGNATOR = Regex("""^(\d{2})([LCR]?)$""")
