package xyz.easiersaid.twr.core.resolution

import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AerodromeRole
import xyz.easiersaid.twr.core.world.Airway
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.CircuitProcedure
import xyz.easiersaid.twr.core.world.Fix
import xyz.easiersaid.twr.core.world.GeometrySegmentId
import xyz.easiersaid.twr.core.world.HoldingPattern
import xyz.easiersaid.twr.core.world.HoldingPoint
import xyz.easiersaid.twr.core.world.InstrumentApproach
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.Sid
import xyz.easiersaid.twr.core.world.Star
import xyz.easiersaid.twr.core.world.SurfaceType
import xyz.easiersaid.twr.core.world.Taxiway
import xyz.easiersaid.twr.core.world.VfrRoute
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.HoldAt
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.HoldSpec
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.TaxiTo

data class AerodromeResolutionContext(
    val aerodromeId: AerodromeId
)

data class GroundResolutionContext(
    val aerodromeId: AerodromeId,
    val currentPoint: PointId
)

enum class ResolutionFailureCode {
    UNKNOWN_AERODROME,
    MISSING_CURRENT_POINT,
    UNKNOWN_POINT,
    UNKNOWN_ROUTE_POINT,
    UNKNOWN_RUNWAY,
    UNKNOWN_FIX,
    UNKNOWN_ROLE,
    UNKNOWN_SID,
    UNKNOWN_STAR,
    UNKNOWN_VFR_ROUTE,
    UNKNOWN_AIRWAY,
    UNKNOWN_CIRCLING_RUNWAY,
    PATH_NOT_FOUND,
    NO_CURRENT_TAXIWAY,
    AMBIGUOUS_CURRENT_TAXIWAY,
    NO_HOLDING_POINT_FOR_RUNWAY,
    NO_RUNWAY_CROSSING,
    UNKNOWN_APPROACH,
    AMBIGUOUS_APPROACH,
    UNKNOWN_HOLDING_PATTERN,
    AMBIGUOUS_HOLDING_PATTERN,
    AIRWAY_EXIT_FIX_NOT_ON_AIRWAY,
    AIRWAY_JOIN_FIX_NOT_ON_AIRWAY,
    CONDITIONAL_STEP_NOT_SUPPORTED,
    CONDITIONAL_INSTRUCTION_NOT_ALLOWED,
    MULTIPLE_CONDITIONS_NOT_SUPPORTED,
    UNKNOWN_CIRCUIT_PROCEDURE,
    AMBIGUOUS_CIRCUIT_PROCEDURE,
    GROUND_STEP_NOT_ON_ACTIVE_TAXI_ROUTE
}

data class ResolutionFailure(
    val code: ResolutionFailureCode,
    val message: String
)

typealias ResolutionResult<T> = arrow.core.Either<ResolutionFailure, T>

data class ResolvedTaxiRoute(
    val aerodrome: Aerodrome,
    val points: List<PointId>,
    val destination: PointId,
    val via: List<PointId>
)

data class ResolvedHoldingPoint(
    val aerodrome: Aerodrome,
    val runway: Runway,
    val taxiway: Taxiway,
    val holdingPoint: HoldingPoint
)

data class ResolvedRunwayCrossing(
    val aerodrome: Aerodrome,
    val runway: Runway,
    val taxiway: Taxiway,
    val crossingPoint: PointId
)

data class ResolvedApproachClearance(
    val aerodrome: Aerodrome,
    val approach: InstrumentApproach,
    val circlingRunway: Runway? = null
)

sealed interface ResolvedRouteSpec {
    data class Direct(val fix: Fix) : ResolvedRouteSpec
    data class Via(val fixes: List<Fix>) : ResolvedRouteSpec
    data class AirwaySegment(
        val airway: Airway,
        val exitFix: Fix
    ) : ResolvedRouteSpec

    data class SidProcedure(val sid: Sid) : ResolvedRouteSpec
    data class StarProcedure(val star: Star) : ResolvedRouteSpec
    data class VfrRouteProcedure(val route: VfrRoute) : ResolvedRouteSpec
}

data class ResolvedRouteClearance(
    val aerodrome: Aerodrome,
    val clearanceLimit: Fix,
    val route: ResolvedRouteSpec?
)

data class ResolvedHoldingInstruction(
    val aerodrome: Aerodrome,
    val fix: Fix,
    val holdingPattern: HoldingPattern,
    val hold: HoldSpec,
    val expectFurtherClearanceAt: String? = null
)

data class ResolvedRoleFrequency(
    val aerodrome: Aerodrome,
    val roleName: RoleName,
    val role: AerodromeRole,
    val publishedFrequency: Frequency,
    val instructedFrequency: Frequency
)

fun AviationWorld.resolveTaxiTo(
    context: GroundResolutionContext,
    instruction: TaxiTo
): ResolutionResult<ResolvedTaxiRoute> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )

    val checkpoints = instruction.via + instruction.destination
    val unknownPoint = checkpoints.firstOrNull { point -> point !in geometry.points }
    if (context.currentPoint !in geometry.points) {
        return unresolved(
            ResolutionFailureCode.UNKNOWN_POINT,
            "Unknown current point ${context.currentPoint.value}"
        )
    }
    if (unknownPoint != null) {
        return unresolved(
            ResolutionFailureCode.UNKNOWN_ROUTE_POINT,
            "Unknown taxi route point ${unknownPoint.value}"
        )
    }

    val fullRoute = arrow.core.raise.either<ResolutionFailure, List<PointId>> {
        checkpoints.fold(listOf(context.currentPoint)) { routeSoFar, checkpoint ->
            val legStart = routeSoFar.last()
            val leg = shortestPath(
                start = legStart,
                destination = checkpoint,
                allowedSurfaces = setOf(SurfaceType.GROUND)
            ) ?: raise(ResolutionFailure(
                ResolutionFailureCode.PATH_NOT_FOUND,
                "No ground path from ${legStart.value} to ${checkpoint.value} at aerodrome ${aerodrome.icao.value}"
            ))
            routeSoFar + leg.drop(1)
        }
    }
    val resolvedRoute = when (fullRoute) {
        is arrow.core.Either.Left -> return fullRoute
        is arrow.core.Either.Right -> fullRoute.value
    }

    return resolved(
        ResolvedTaxiRoute(
            aerodrome = aerodrome,
            points = resolvedRoute,
            destination = instruction.destination,
            via = instruction.via
        )
    )
}

fun AviationWorld.resolveHoldShortOf(
    context: GroundResolutionContext,
    instruction: HoldShortOf
): ResolutionResult<ResolvedHoldingPoint> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val runway = aerodrome.runways[instruction.runway] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_RUNWAY,
        "Unknown runway ${instruction.runway.value} at aerodrome ${aerodrome.icao.value}"
    )

    return resolveHoldingPointOnCurrentTaxiway(aerodrome, context.currentPoint, runway)
}

fun AviationWorld.resolveCrossRunway(
    context: GroundResolutionContext,
    instruction: CrossRunway
): ResolutionResult<ResolvedRunwayCrossing> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val runway = aerodrome.runways[instruction.runway] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_RUNWAY,
        "Unknown runway ${instruction.runway.value} at aerodrome ${aerodrome.icao.value}"
    )

    val candidateTaxiways = aerodrome.taxiways.values
        .filter { taxiway -> context.currentPoint in taxiway.path.points }

    if (candidateTaxiways.isEmpty()) {
        return unresolved(
            ResolutionFailureCode.NO_CURRENT_TAXIWAY,
            "Point ${context.currentPoint.value} is not on any taxiway at aerodrome ${aerodrome.icao.value}"
        )
    }

    val candidates = candidateTaxiways.mapNotNull { taxiway ->
        val crossingPoint = taxiway.path.points
            .intersect(runway.path.points.toSet())
            .minByOrNull { point ->
                taxiwayPathDistance(aerodrome, taxiway.path, context.currentPoint, point) ?: Double.POSITIVE_INFINITY
            } ?: return@mapNotNull null

        val distance = taxiwayPathDistance(aerodrome, taxiway.path, context.currentPoint, crossingPoint)
            ?: return@mapNotNull null

        RunwayCrossingCandidate(taxiway, crossingPoint, distance)
    }

    if (candidates.isEmpty()) {
        return unresolved(
            ResolutionFailureCode.NO_RUNWAY_CROSSING,
            "No shared point exists between the current taxiway and runway ${runway.id.value} at aerodrome ${aerodrome.icao.value}"
        )
    }

    val bestCandidate = candidates.minByOrNull { candidate -> candidate.distance }!!
    val ambiguousCandidate = candidates.any { candidate ->
        candidate !== bestCandidate && candidate.distance == bestCandidate.distance
    }
    if (ambiguousCandidate) {
        return unresolved(
            ResolutionFailureCode.AMBIGUOUS_CURRENT_TAXIWAY,
            "Point ${context.currentPoint.value} lies on multiple taxiways with an equally near crossing for runway ${runway.id.value}"
        )
    }

    return resolved(
        ResolvedRunwayCrossing(
            aerodrome = aerodrome,
            runway = runway,
            taxiway = bestCandidate.taxiway,
            crossingPoint = bestCandidate.crossingPoint
        )
    )
}

fun AviationWorld.resolveClearedApproach(
    context: AerodromeResolutionContext,
    instruction: ClearedApproach
): ResolutionResult<ResolvedApproachClearance> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val matches = aerodrome.approaches.values.filter { approach ->
        approach.type == instruction.approachType && approach.runway == instruction.runway
    }

    val approach = when (matches.size) {
        0 -> return unresolved(
            ResolutionFailureCode.UNKNOWN_APPROACH,
            "No ${instruction.approachType} approach exists for runway ${instruction.runway.value} at aerodrome ${aerodrome.icao.value}"
        )

        1 -> matches.single()
        else -> return unresolved(
            ResolutionFailureCode.AMBIGUOUS_APPROACH,
            "Multiple ${instruction.approachType} approaches exist for runway ${instruction.runway.value} at aerodrome ${aerodrome.icao.value}"
        )
    }

    val circlingRunway = instruction.circlingRunway?.let { runwayId ->
        aerodrome.runways[runwayId] ?: return unresolved(
            ResolutionFailureCode.UNKNOWN_CIRCLING_RUNWAY,
            "Unknown circling runway ${runwayId.value} at aerodrome ${aerodrome.icao.value}"
        )
    }

    return resolved(
        ResolvedApproachClearance(
            aerodrome = aerodrome,
            approach = approach,
            circlingRunway = circlingRunway
        )
    )
}

fun AviationWorld.resolveClearedTo(
    context: AerodromeResolutionContext,
    instruction: ClearedTo
): ResolutionResult<ResolvedRouteClearance> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val clearanceLimit = fixes[instruction.clearanceLimit] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_FIX,
        "Unknown clearance-limit fix ${instruction.clearanceLimit.value}"
    )
    val resolvedRoute = instruction.route?.let { route ->
        resolveRouteSpec(aerodrome, route)
    }

    return when (resolvedRoute) {
        is arrow.core.Either.Left -> resolvedRoute
        is arrow.core.Either.Right -> resolved(
            ResolvedRouteClearance(
                aerodrome = aerodrome,
                clearanceLimit = clearanceLimit,
                route = resolvedRoute.value
            )
        )

        null -> resolved(
            ResolvedRouteClearance(
                aerodrome = aerodrome,
                clearanceLimit = clearanceLimit,
                route = null
            )
        )
    }
}

fun AviationWorld.resolveHoldAt(
    context: AerodromeResolutionContext,
    instruction: HoldAt
): ResolutionResult<ResolvedHoldingInstruction> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val fixId = when (val hold = instruction.hold) {
        is HoldSpec.Published -> hold.fix
        is HoldSpec.InboundTrack -> hold.fix
    }
    val fix = fixes[fixId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_FIX,
        "Unknown hold fix ${fixId.value}"
    )
    val matchingPatterns = aerodrome.holdingPatterns.values.filter { holdingPattern ->
        holdingPattern.fix == fixId
    }

    val holdingPattern = when (matchingPatterns.size) {
        0 -> return unresolved(
            ResolutionFailureCode.UNKNOWN_HOLDING_PATTERN,
            "No holding pattern exists for fix ${fixId.value} at aerodrome ${aerodrome.icao.value}"
        )

        1 -> matchingPatterns.single()
        else -> return unresolved(
            ResolutionFailureCode.AMBIGUOUS_HOLDING_PATTERN,
            "Multiple holding patterns exist for fix ${fixId.value} at aerodrome ${aerodrome.icao.value}"
        )
    }

    return resolved(
        ResolvedHoldingInstruction(
            aerodrome = aerodrome,
            fix = fix,
            holdingPattern = holdingPattern,
            hold = instruction.hold,
            expectFurtherClearanceAt = instruction.expectFurtherClearanceAt
        )
    )
}

fun AviationWorld.resolveContactFrequency(
    context: AerodromeResolutionContext,
    instruction: ContactFrequency
): ResolutionResult<ResolvedRoleFrequency> =
    resolveRoleFrequency(context, instruction.role, instruction.frequency)

fun AviationWorld.resolveMonitorFrequency(
    context: AerodromeResolutionContext,
    instruction: MonitorFrequency
): ResolutionResult<ResolvedRoleFrequency> =
    resolveRoleFrequency(context, instruction.role, instruction.frequency)

private fun AviationWorld.resolveRoleFrequency(
    context: AerodromeResolutionContext,
    roleName: RoleName,
    explicitFrequency: Frequency?
): ResolutionResult<ResolvedRoleFrequency> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val role = aerodrome.roles[roleName] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_ROLE,
        "Aerodrome ${aerodrome.icao.value} does not declare role ${roleName.name}"
    )

    return resolved(
        ResolvedRoleFrequency(
            aerodrome = aerodrome,
            roleName = roleName,
            role = role,
            publishedFrequency = role.frequency,
            instructedFrequency = explicitFrequency ?: role.frequency
        )
    )
}

private fun AviationWorld.resolveRouteSpec(
    aerodrome: Aerodrome,
    route: RouteSpec
): ResolutionResult<ResolvedRouteSpec> {
    return when (route) {
        is RouteSpec.Direct -> {
            val fix = fixes[route.fix] ?: return unresolved(
                ResolutionFailureCode.UNKNOWN_FIX,
                "Unknown route fix ${route.fix.value}"
            )
            resolved(ResolvedRouteSpec.Direct(fix))
        }

        is RouteSpec.Via -> {
            val routeFixes = route.fixes.map { fixId ->
                fixes[fixId] ?: return unresolved(
                    ResolutionFailureCode.UNKNOWN_FIX,
                    "Unknown route fix ${fixId.value}"
                )
            }
            resolved(ResolvedRouteSpec.Via(routeFixes))
        }

        is RouteSpec.Airway -> {
            val airway = airways[route.airway] ?: return unresolved(
                ResolutionFailureCode.UNKNOWN_AIRWAY,
                "Unknown airway ${route.airway.value}"
            )
            val exitFix = fixes[route.exitFix] ?: return unresolved(
                ResolutionFailureCode.UNKNOWN_FIX,
                "Unknown airway exit fix ${route.exitFix.value}"
            )
            if (airway.waypoints.none { waypoint -> waypoint.point == exitFix.point }) {
                return unresolved(
                    ResolutionFailureCode.AIRWAY_EXIT_FIX_NOT_ON_AIRWAY,
                    "Fix ${route.exitFix.value} is not on airway ${airway.id.value}"
                )
            }
            resolved(ResolvedRouteSpec.AirwaySegment(airway, exitFix))
        }

        is RouteSpec.ViaSid -> {
            val sid = aerodrome.sids[route.sid] ?: return unresolved(
                ResolutionFailureCode.UNKNOWN_SID,
                "Unknown SID ${route.sid.value} at aerodrome ${aerodrome.icao.value}"
            )
            resolved(ResolvedRouteSpec.SidProcedure(sid))
        }

        is RouteSpec.ViaStar -> {
            val star = aerodrome.stars[route.star] ?: return unresolved(
                ResolutionFailureCode.UNKNOWN_STAR,
                "Unknown STAR ${route.star.value} at aerodrome ${aerodrome.icao.value}"
            )
            resolved(ResolvedRouteSpec.StarProcedure(star))
        }

        is RouteSpec.ViaRoute -> {
            val vfrRoute = vfrRoutes[route.route] ?: return unresolved(
                ResolutionFailureCode.UNKNOWN_VFR_ROUTE,
                "Unknown VFR route ${route.route.value}"
            )
            resolved(ResolvedRouteSpec.VfrRouteProcedure(vfrRoute))
        }
    }
}

private fun AviationWorld.resolveHoldingPointOnCurrentTaxiway(
    aerodrome: Aerodrome,
    currentPoint: PointId,
    runway: Runway
): ResolutionResult<ResolvedHoldingPoint> {
    val candidateTaxiways = aerodrome.taxiways.values
        .filter { taxiway -> currentPoint in taxiway.path.points }

    if (candidateTaxiways.isEmpty()) {
        return unresolved(
            ResolutionFailureCode.NO_CURRENT_TAXIWAY,
            "Point ${currentPoint.value} is not on any taxiway at aerodrome ${aerodrome.icao.value}"
        )
    }

    val candidates = candidateTaxiways.mapNotNull { taxiway ->
        val holdingPoint = taxiway.holdingPoints
            .filter { point -> point.runway == runway.id }
            .minByOrNull { point ->
                taxiwayPathDistance(aerodrome, taxiway.path, currentPoint, point.point) ?: Double.POSITIVE_INFINITY
            } ?: return@mapNotNull null

        val distance = taxiwayPathDistance(aerodrome, taxiway.path, currentPoint, holdingPoint.point)
            ?: return@mapNotNull null

        HoldingPointCandidate(taxiway, holdingPoint, distance)
    }

    if (candidates.isEmpty()) {
        return unresolved(
            ResolutionFailureCode.NO_HOLDING_POINT_FOR_RUNWAY,
            "No holding point exists on the current taxiway for runway ${runway.id.value} at aerodrome ${aerodrome.icao.value}"
        )
    }

    val bestCandidate = candidates.minByOrNull { candidate -> candidate.distance }!!
    val ambiguousCandidate = candidates.any { candidate ->
        candidate !== bestCandidate && candidate.distance == bestCandidate.distance
    }
    if (ambiguousCandidate) {
        return unresolved(
            ResolutionFailureCode.AMBIGUOUS_CURRENT_TAXIWAY,
            "Point ${currentPoint.value} lies on multiple taxiways with an equally near holding point for runway ${runway.id.value}"
        )
    }

    return resolved(
        ResolvedHoldingPoint(
            aerodrome = aerodrome,
            runway = runway,
            taxiway = bestCandidate.taxiway,
            holdingPoint = bestCandidate.holdingPoint
        )
    )
}

@Suppress("LoopWithTooManyJumpStatements")
private fun AviationWorld.shortestPath(
    start: PointId,
    destination: PointId,
    allowedSurfaces: Set<SurfaceType>
): List<PointId>? {
    if (start == destination) return listOf(start)

    val adjacency = buildAdjacency(allowedSurfaces)
    if (start !in adjacency || destination !in adjacency) return null

    val initialDistances = adjacency.keys.associateWith { point ->
        if (point == start) 0.0 else Double.POSITIVE_INFINITY
    }
    val initialPrevious = mapOf<PointId, PointId?>(start to null)

    val result = dijkstra(
        adjacency = adjacency,
        destination = destination,
        unvisited = adjacency.keys,
        distances = initialDistances,
        previous = initialPrevious
    )

    return result[destination]?.let { reconstructPath(destination, result) }
}

private tailrec fun AviationWorld.dijkstra(
    adjacency: Map<PointId, Set<PointId>>,
    destination: PointId,
    unvisited: Set<PointId>,
    distances: Map<PointId, Double>,
    previous: Map<PointId, PointId?>
): Map<PointId, PointId?> {
    if (unvisited.isEmpty()) return previous
    val current = unvisited.minByOrNull { distances.getValue(it) } ?: return previous
    if (distances.getValue(current) == Double.POSITIVE_INFINITY) return previous
    if (current == destination) return previous

    val neighbors = adjacency.getValue(current).filter { it in unvisited }
    val currentDist = distances.getValue(current)

    val updates = neighbors.mapNotNull { neighbor ->
        val segmentLength = geometry.segments[GeometrySegmentId.between(current, neighbor)]
            ?.length?.value ?: return@mapNotNull null
        val candidateDist = currentDist + segmentLength
        if (candidateDist < distances.getValue(neighbor)) {
            neighbor to candidateDist
        } else null
    }

    val newDistances = distances + updates.map { (point, dist) -> point to dist }
    val newPrevious = previous + updates.map { (point, _) -> point to (current as? PointId) }

    return dijkstra(adjacency, destination, unvisited - current, newDistances, newPrevious)
}

private fun reconstructPath(
    destination: PointId,
    previous: Map<PointId, PointId?>
): List<PointId> = generateSequence(destination) { previous[it] }.toList().asReversed()

private fun AviationWorld.buildAdjacency(
    allowedSurfaces: Set<SurfaceType>
): Map<PointId, Set<PointId>> {
    val edges = geometry.segments
        .filter { (_, segment) -> segment.surface in allowedSurfaces }
        .keys
        .flatMap { segmentId ->
            listOf(segmentId.first to segmentId.second, segmentId.second to segmentId.first)
        }
    return edges.groupBy(
        keySelector = { (from, _) -> from },
        valueTransform = { (_, to) -> to }
    ).mapValues { (_, neighbors) -> neighbors.toSet() }
}

@Suppress("UnusedParameter")
private fun AviationWorld.taxiwayPathDistance(
    aerodrome: Aerodrome,
    path: Path,
    from: PointId,
    to: PointId
): Double? {
    val fromIndex = path.points.indexOf(from)
    val toIndex = path.points.indexOf(to)
    if (fromIndex == -1 || toIndex == -1) {
        return null
    }
    if (fromIndex == toIndex) {
        return 0.0
    }

    val orderedPoints = if (fromIndex < toIndex) {
        path.points.subList(fromIndex, toIndex + 1)
    } else {
        path.points.subList(toIndex, fromIndex + 1)
    }

    return orderedPoints.zipWithNext()
        .sumOf { (left, right) ->
            geometry.segments[GeometrySegmentId.between(left, right)]
                ?.length
                ?.value
                ?: return null
        }
}

private fun AviationWorld.aerodrome(id: AerodromeId): Aerodrome? =
    aerodromes[id]

private fun <T> resolved(value: T): ResolutionResult<T> =
    arrow.core.Either.Right(value)

private fun unresolved(
    code: ResolutionFailureCode,
    message: String
): ResolutionResult<Nothing> =
    arrow.core.Either.Left(ResolutionFailure(code, message))

private data class HoldingPointCandidate(
    val taxiway: Taxiway,
    val holdingPoint: HoldingPoint,
    val distance: Double
)

private data class RunwayCrossingCandidate(
    val taxiway: Taxiway,
    val crossingPoint: PointId,
    val distance: Double
)
