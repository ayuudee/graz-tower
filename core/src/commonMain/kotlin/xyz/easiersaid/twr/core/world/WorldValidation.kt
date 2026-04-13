package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.AerodromeId
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
    DUPLICATE_STAR_NAME
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

fun AviationWorld.validate(): WorldValidationReport {
    val issues = mutableListOf<WorldValidationIssue>()

    validateGeometryReferencesAndClaims(issues)
    validateAirspaceCoverage(issues)
    validateFirMembership(issues)
    validateGlobalNames(issues)

    aerodromes.values.forEach { aerodrome ->
        validateAerodrome(aerodrome, issues)
    }

    return WorldValidationReport(issues.toList())
}

private fun AviationWorld.validateAirspaceCoverage(issues: MutableList<WorldValidationIssue>) {
    val coveredPoints = airspace.values
        .flatMap { volume -> volume.points }
        .toSet()

    geometry.points.keys
        .filter { point -> point !in coveredPoints }
        .sortedBy(PointId::value)
        .forEach { point ->
            issues += WorldValidationIssue(
                WorldValidationCode.POINT_OUTSIDE_AIRSPACE,
                "Point ${point.value} is not contained in any airspace volume"
            )
        }
}

private fun AviationWorld.validateGeometryReferencesAndClaims(
    issues: MutableList<WorldValidationIssue>
) {
    val claimedPoints = deriveEntitiesByPoint().keys
    val claimedSegments = collectClaimedSegments()

    geometry.segments.keys.forEach { segment ->
        if (segment.first !in geometry.points || segment.second !in geometry.points) {
            issues += WorldValidationIssue(
                WorldValidationCode.GEOMETRY_SEGMENT_UNKNOWN_ENDPOINT,
                "Geometry segment ${segment.describe()} references a point missing from the geometry point map"
            )
        }
    }

    claimedPoints
        .filter { point -> point !in geometry.points }
        .sortedBy(PointId::value)
        .forEach { point ->
            issues += WorldValidationIssue(
                WorldValidationCode.UNKNOWN_GEOMETRY_POINT_REFERENCE,
                "Entity reference point ${point.value} is missing from physical geometry"
            )
        }

    claimedSegments
        .filter { segment -> segment !in geometry.segments }
        .sortedBy(GeometrySegmentId::describe)
        .forEach { segment ->
            issues += WorldValidationIssue(
                WorldValidationCode.UNKNOWN_GEOMETRY_SEGMENT_REFERENCE,
                "Entity reference segment ${segment.describe()} is missing from physical geometry"
            )
        }

    geometry.points.keys
        .filter { point -> point !in claimedPoints }
        .sortedBy(PointId::value)
        .forEach { point ->
            issues += WorldValidationIssue(
                WorldValidationCode.ORPHAN_GEOMETRY_POINT,
                "Geometry point ${point.value} is not claimed by any entity"
            )
        }

    geometry.segments.keys
        .filter { segment -> segment !in claimedSegments }
        .sortedBy(GeometrySegmentId::describe)
        .forEach { segment ->
            issues += WorldValidationIssue(
                WorldValidationCode.ORPHAN_GEOMETRY_SEGMENT,
                "Geometry segment ${segment.describe()} is not claimed by any entity"
            )
        }
}

private fun AviationWorld.validateFirMembership(issues: MutableList<WorldValidationIssue>) {
    airspace.values.forEach { volume ->
        val fir = firs[volume.fir]
        when {
            fir == null -> issues += WorldValidationIssue(
                WorldValidationCode.UNKNOWN_FIR,
                "Airspace volume ${volume.id.value} references unknown FIR ${volume.fir.value}"
            )

            volume.id !in fir.volumes -> issues += WorldValidationIssue(
                WorldValidationCode.FIR_VOLUME_MISMATCH,
                "Airspace volume ${volume.id.value} is not listed in FIR ${fir.id.value}"
            )
        }
    }

    firs.values.forEach { fir ->
        fir.volumes
            .filter { volumeId -> volumeId !in airspace }
            .sortedBy { volumeId -> volumeId.value }
            .forEach { volumeId ->
                issues += WorldValidationIssue(
                    WorldValidationCode.UNKNOWN_AIRSPACE_VOLUME,
                    "FIR ${fir.id.value} references unknown airspace volume ${volumeId.value}"
                )
            }
    }
}

private fun AviationWorld.validateGlobalNames(issues: MutableList<WorldValidationIssue>) {
    airways.values
        .groupBy { airway -> airway.name }
        .filterValues { duplicates -> duplicates.size > 1 }
        .forEach { (name, duplicates) ->
            issues += WorldValidationIssue(
                WorldValidationCode.DUPLICATE_AIRWAY_NAME,
                "Airway name $name is duplicated across ${duplicates.map { airway -> airway.id.value }.sorted()}"
            )
        }
}

private fun AviationWorld.validateAerodrome(
    aerodrome: Aerodrome,
    issues: MutableList<WorldValidationIssue>
) {
    validateRunwayHoldingPoints(aerodrome, issues)
    validateStandReachability(aerodrome, issues)
    validateRunwayExits(aerodrome, issues)
    validateProcedureAnchoring(aerodrome, issues)
    validateSegmentOwnership(aerodrome, issues)
    validateHoldingPatterns(aerodrome, issues)
    validateRoleStaffing(aerodrome, issues)
    validateReciprocalRunways(aerodrome, issues)
    validateAerodromeNames(aerodrome, issues)
}

private fun validateRunwayHoldingPoints(
    aerodrome: Aerodrome,
    issues: MutableList<WorldValidationIssue>
) {
    val runwayProtectedByHoldingPoint = aerodrome.taxiways.values
        .flatMap { taxiway -> taxiway.holdingPoints }
        .mapNotNull { holdingPoint -> holdingPoint.runway }
        .toSet()

    aerodrome.runways.values
        .filter { runway -> runway.id !in runwayProtectedByHoldingPoint }
        .sortedBy { runway -> runway.id.value }
        .forEach { runway ->
            issues += WorldValidationIssue(
                WorldValidationCode.MISSING_RUNWAY_HOLDING_POINT,
                "Aerodrome ${aerodrome.icao.value} has no holding point protecting runway ${runway.id.value}"
            )
        }
}

private fun validateStandReachability(
    aerodrome: Aerodrome,
    issues: MutableList<WorldValidationIssue>
) {
    val adjacency = groundAdjacency(aerodrome)
    val holdingPoints = aerodrome.taxiways.values.flatMap { taxiway -> taxiway.holdingPoints }
    val stands = aerodrome.stands.values

    holdingPoints.forEach { holdingPoint ->
        val reachable = reachablePointsFrom(holdingPoint.point, adjacency)
        stands.filter { stand -> stand.point !in reachable }
            .sortedBy { stand -> stand.id.value }
            .forEach { stand ->
                issues += WorldValidationIssue(
                    WorldValidationCode.UNREACHABLE_STAND_FROM_HOLDING_POINT,
                    "Stand ${stand.id.value} is not reachable from holding point ${holdingPoint.point.value} at aerodrome ${aerodrome.icao.value}"
                )
            }
    }
}

private fun validateRunwayExits(
    aerodrome: Aerodrome,
    issues: MutableList<WorldValidationIssue>
) {
    aerodrome.runways.values.forEach { runway ->
        runway.exits.forEach { exit ->
            val taxiway = aerodrome.taxiways[exit.taxiway]
            if (taxiway == null) {
                issues += WorldValidationIssue(
                    WorldValidationCode.UNKNOWN_RUNWAY_EXIT_TAXIWAY,
                    "Runway ${runway.id.value} references unknown exit taxiway ${exit.taxiway.value}"
                )
                return@forEach
            }

            if (exit.point !in runway.path.points) {
                issues += WorldValidationIssue(
                    WorldValidationCode.RUNWAY_EXIT_NOT_ON_RUNWAY,
                    "Runway exit point ${exit.point.value} is not on runway ${runway.id.value}"
                )
            }

            if (exit.point !in taxiway.path.points) {
                issues += WorldValidationIssue(
                    WorldValidationCode.RUNWAY_EXIT_NOT_ON_TAXIWAY,
                    "Runway exit point ${exit.point.value} is not on taxiway ${taxiway.id.value}"
                )
            }
        }
    }
}

private fun AviationWorld.validateProcedureAnchoring(
    aerodrome: Aerodrome,
    issues: MutableList<WorldValidationIssue>
) {
    val holdingFixPoints = aerodrome.holdingPatterns.values
        .mapNotNull { holdingPattern -> fixes[holdingPattern.fix]?.point }
        .toSet()
    val approachEntryPoints = aerodrome.approaches.values
        .mapNotNull { approach -> approach.waypoints.firstOrNull()?.point }
        .toSet()

    aerodrome.sids.values.forEach { sid ->
        val runway = aerodrome.runways[sid.runway]
        val firstPoint = sid.waypoints.firstOrNull()?.point
        when {
            runway == null -> issues += WorldValidationIssue(
                WorldValidationCode.SID_UNKNOWN_RUNWAY,
                "SID ${sid.id.value} references unknown runway ${sid.runway.value} at aerodrome ${aerodrome.icao.value}"
            )

            firstPoint != runway.threshold -> issues += WorldValidationIssue(
                WorldValidationCode.SID_NOT_AT_RUNWAY_THRESHOLD,
                "SID ${sid.id.value} does not start at runway ${runway.id.value} threshold ${runway.threshold.value}"
            )
        }
    }

    aerodrome.stars.values.forEach { star ->
        val terminalPoint = star.waypoints.lastOrNull()?.point ?: return@forEach
        if (terminalPoint !in approachEntryPoints && terminalPoint !in holdingFixPoints) {
            issues += WorldValidationIssue(
                WorldValidationCode.STAR_TERMINAL_POINT_UNSHARED,
                "STAR ${star.id.value} ends at point ${terminalPoint.value}, " +
                    "not shared with an approach/holding fix at ${aerodrome.icao.value}"
            )
        }
    }

    aerodrome.approaches.values.forEach { approach ->
        val runway = aerodrome.runways[approach.runway]
        val lastPoint = approach.waypoints.lastOrNull()?.point
        when {
            runway == null -> issues += WorldValidationIssue(
                WorldValidationCode.APPROACH_UNKNOWN_RUNWAY,
                "Approach ${approach.id.value} references unknown runway ${approach.runway.value} at aerodrome ${aerodrome.icao.value}"
            )

            lastPoint != runway.threshold -> issues += WorldValidationIssue(
                WorldValidationCode.APPROACH_NOT_AT_RUNWAY_THRESHOLD,
                "Approach ${approach.id.value} does not end at runway ${runway.id.value} threshold ${runway.threshold.value}"
            )
        }

        if (approach.missedApproach.holdAt !in aerodrome.holdingPatterns) {
            issues += WorldValidationIssue(
                WorldValidationCode.APPROACH_UNKNOWN_MISSED_HOLD,
                "Approach ${approach.id.value} references unknown missed-approach hold ${approach.missedApproach.holdAt.value}"
            )
        }
    }
}

private fun validateSegmentOwnership(
    aerodrome: Aerodrome,
    issues: MutableList<WorldValidationIssue>
) {
    aerodrome.taxiways.values
        .flatMap { taxiway ->
            taxiway.path.geometrySegmentIds().map { segment -> segment to taxiway.id.value }
        }
        .groupBy(
            keySelector = { (segment, _) -> segment },
            valueTransform = { (_, taxiwayId) -> taxiwayId }
        )
        .filterValues { owners -> owners.distinct().size > 1 }
        .forEach { (segment, owners) ->
            issues += WorldValidationIssue(
                WorldValidationCode.TAXIWAY_SEGMENT_OVERLAP,
                "Taxiway segment ${segment.describe()} is claimed by multiple taxiways ${owners.distinct().sorted()}"
            )
        }

    aerodrome.aprons.values
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
        .forEach { (segment, owners) ->
            issues += WorldValidationIssue(
                WorldValidationCode.APRON_SEGMENT_OVERLAP,
                "Apron segment ${segment.describe()} is claimed by multiple aprons ${owners.distinct().sorted()}"
            )
        }
}

private fun AviationWorld.validateHoldingPatterns(
    aerodrome: Aerodrome,
    issues: MutableList<WorldValidationIssue>
) {
    aerodrome.holdingPatterns.values.forEach { holdingPattern ->
        if (holdingPattern.fix !in fixes) {
            issues += WorldValidationIssue(
                WorldValidationCode.HOLDING_PATTERN_UNKNOWN_FIX,
                "Holding pattern ${holdingPattern.id.value} references unknown fix ${holdingPattern.fix.value}"
            )
        }
        if (holdingPattern.loop.points.first() != holdingPattern.loop.points.last()) {
            issues += WorldValidationIssue(
                WorldValidationCode.HOLDING_PATTERN_NOT_CLOSED,
                "Holding pattern ${holdingPattern.id.value} is not a closed loop"
            )
        }
    }
}

private fun validateRoleStaffing(
    aerodrome: Aerodrome,
    issues: MutableList<WorldValidationIssue>
) {
    val staffedRoles = aerodrome.controllers.values
        .flatten()
        .toSet()

    aerodrome.roles.keys
        .filter { role -> role !in staffedRoles }
        .sortedBy(RoleName::name)
        .forEach { role ->
            issues += WorldValidationIssue(
                WorldValidationCode.UNSTAFFED_ROLE,
                "Aerodrome ${aerodrome.icao.value} declares role ${role.name} without an assigned controller"
            )
        }
}

private fun validateReciprocalRunways(
    aerodrome: Aerodrome,
    issues: MutableList<WorldValidationIssue>
) {
    val checkedPairs = mutableSetOf<Pair<RunwayId, RunwayId>>()

    aerodrome.runways.values.forEach { runway ->
        val reciprocalId = runway.id.reciprocal() ?: return@forEach
        val reciprocal = aerodrome.runways[reciprocalId] ?: return@forEach
        val pair = listOf(runway.id, reciprocal.id)
            .sortedBy(RunwayId::value)
            .let { ids -> ids[0] to ids[1] }

        if (!checkedPairs.add(pair)) {
            return@forEach
        }

        val sharedSegments = runway.path.segmentIds()
            .map { segment -> GeometrySegmentId.between(segment.from, segment.to) }
            .toSet()
            .intersect(
                reciprocal.path.segmentIds()
                    .map { segment -> GeometrySegmentId.between(segment.from, segment.to) }
                    .toSet()
            )

        if (sharedSegments.isEmpty()) {
            issues += WorldValidationIssue(
                WorldValidationCode.RECIPROCAL_RUNWAYS_DO_NOT_SHARE_SEGMENT,
                "Reciprocal runways ${runway.id.value} and ${reciprocal.id.value} at aerodrome ${aerodrome.icao.value} do not share any segment"
            )
        }
    }
}

private fun validateAerodromeNames(
    aerodrome: Aerodrome,
    issues: MutableList<WorldValidationIssue>
) {
    aerodrome.sids.values
        .groupBy { sid -> sid.name }
        .filterValues { duplicates -> duplicates.size > 1 }
        .forEach { (name, duplicates) ->
            issues += WorldValidationIssue(
                WorldValidationCode.DUPLICATE_SID_NAME,
                "Aerodrome ${aerodrome.icao.value} has duplicate SID name $name across ${duplicates.map { sid -> sid.id.value }.sorted()}"
            )
        }

    aerodrome.stars.values
        .groupBy { star -> star.name }
        .filterValues { duplicates -> duplicates.size > 1 }
        .forEach { (name, duplicates) ->
            issues += WorldValidationIssue(
                WorldValidationCode.DUPLICATE_STAR_NAME,
                "Aerodrome ${aerodrome.icao.value} has duplicate STAR name $name across ${duplicates.map { star -> star.id.value }.sorted()}"
            )
        }
}

private fun groundAdjacency(aerodrome: Aerodrome): Map<PointId, Set<PointId>> {
    val adjacency = linkedMapOf<PointId, MutableSet<PointId>>()

    fun addPath(path: Path) {
        path.segmentIds().forEach { segment ->
            adjacency.getOrPut(segment.from) { linkedSetOf() }.add(segment.to)
            adjacency.getOrPut(segment.to) { linkedSetOf() }.add(segment.from)
        }
    }

    aerodrome.taxiways.values.forEach { taxiway -> addPath(taxiway.path) }
    aerodrome.aprons.values.forEach { apron -> apron.paths.forEach(::addPath) }

    return adjacency.mapValues { (_, points) -> points.toSet() }
}

private fun reachablePointsFrom(
    origin: PointId,
    adjacency: Map<PointId, Set<PointId>>
): Set<PointId> {
    if (origin !in adjacency) {
        return setOf(origin)
    }

    val visited = linkedSetOf<PointId>()
    val queue = ArrayDeque<PointId>()

    visited += origin
    queue += origin

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        adjacency[current].orEmpty().forEach { next ->
            if (visited.add(next)) {
                queue += next
            }
        }
    }

    return visited
}

private fun AviationWorld.collectClaimedSegments(): Set<GeometrySegmentId> {
    val claimedSegments = linkedSetOf<GeometrySegmentId>()

    fun addPath(path: Path) {
        claimedSegments += path.geometrySegmentIds()
    }

    aerodromes.values.forEach { aerodrome ->
        aerodrome.runways.values.forEach { runway -> addPath(runway.path) }
        aerodrome.taxiways.values.forEach { taxiway -> addPath(taxiway.path) }
        aerodrome.aprons.values.forEach { apron ->
            apron.paths.forEach(::addPath)
        }
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
            sid.transitions.values.forEach { transition ->
                transition.asPathOrNull()?.let(::addPath)
            }
        }
        aerodrome.stars.values.forEach { star ->
            star.waypoints.asPathOrNull()?.let(::addPath)
            star.transitions.values.forEach { transition ->
                transition.asPathOrNull()?.let(::addPath)
            }
        }
        aerodrome.approaches.values.forEach { approach ->
            approach.waypoints.asPathOrNull()?.let(::addPath)
            approach.missedApproach.waypoints.asPathOrNull()?.let(::addPath)
        }
        aerodrome.holdingPatterns.values.forEach { holdingPattern -> addPath(holdingPattern.loop) }
    }

    airways.values.forEach { airway ->
        airway.waypoints.asPathOrNull()?.let(::addPath)
    }
    vfrRoutes.values.forEach { route ->
        route.waypoints.asPathOrNull()?.let(::addPath)
    }

    return claimedSegments
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
