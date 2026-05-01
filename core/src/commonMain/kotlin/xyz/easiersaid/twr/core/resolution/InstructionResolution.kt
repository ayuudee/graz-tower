@file:Suppress("TooManyFunctions") // file groups all per-instruction resolution logic; splitting
// fragments the instruction → resolved-instruction surface that callers depend on.

package xyz.easiersaid.twr.core.resolution

import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AerodromeRole
import xyz.easiersaid.twr.core.world.AirspaceVolume
import xyz.easiersaid.twr.core.world.Airway
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.CircuitProcedure
import xyz.easiersaid.twr.core.world.Fix
import xyz.easiersaid.twr.core.world.GeometrySegmentId
import xyz.easiersaid.twr.core.world.HoldingPattern
import xyz.easiersaid.twr.core.world.HoldingPoint
import xyz.easiersaid.twr.core.world.HandoffStep
import xyz.easiersaid.twr.core.world.InstrumentApproach
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.PilotHandoffAction
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.Sid
import xyz.easiersaid.twr.core.world.Star
import xyz.easiersaid.twr.core.world.SurfaceType
import xyz.easiersaid.twr.core.world.Taxiway
import xyz.easiersaid.twr.core.world.VfrRoute
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.HoldAt
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.HoldSpec
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.Orbit
import xyz.easiersaid.twr.protocol.OrbitDirection
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.ApproachId
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SpecialVfrClearance
import xyz.easiersaid.twr.protocol.TaxiClearance
import xyz.easiersaid.twr.protocol.TurnDirection
import xyz.easiersaid.twr.protocol.ExtendDownwind

data class AerodromeResolutionContext(
    val aerodromeId: AerodromeId,
    val currentRole: RoleName? = null,
    val currentPoint: PointId? = null,
    val currentFix: FixId? = null,
    val currentRunway: RunwayId? = null,
    val currentApproach: ApproachId? = null,
    val currentCircuit: CircuitProcedureId? = null,
    val onGround: Boolean? = null
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
    MISSING_CURRENT_HEADING,
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
    UNKNOWN_AIRSPACE_VOLUME,
    AIRSPACE_ROUTE_DOES_NOT_INTERACT,
    AIRWAY_EXIT_FIX_NOT_ON_AIRWAY,
    AIRWAY_JOIN_FIX_NOT_ON_AIRWAY,
    AMBIGUOUS_PUBLISHED_HANDOFF,
    CONDITIONAL_STEP_NOT_SUPPORTED,
    CONDITIONAL_INSTRUCTION_NOT_ALLOWED,
    MULTIPLE_CONDITIONS_NOT_SUPPORTED,
    UNKNOWN_CIRCUIT_PROCEDURE,
    AMBIGUOUS_CIRCUIT_PROCEDURE,
    MISSING_CURRENT_APPROACH,
    MISSING_CURRENT_CIRCUIT,
    NO_EXTENDED_DOWNWIND,
    NO_ORBIT_POINT,
    AMBIGUOUS_ORBIT_POINT,
    GROUND_STEP_NOT_ON_ACTIVE_TAXI_ROUTE,
    MISSING_CURRENT_RUNWAY
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
    val circlingRunway: Runway? = null,
    val waypointPoints: List<PointId>,
    val thresholdPoint: PointId,
    val missedApproachPoints: List<PointId>,
    val missedApproachHoldingPattern: HoldingPattern
)

data class ResolvedRunwayOperation(
    val aerodrome: Aerodrome,
    val runway: Runway,
    val thresholdPoint: PointId,
    val pathPoints: List<PointId>
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
    val route: ResolvedRouteSpec?,
    val routePoints: List<PointId>,
    val clearanceLimitHoldingPattern: HoldingPattern? = null
)

data class ResolvedHoldingInstruction(
    val aerodrome: Aerodrome,
    val fix: Fix,
    val holdingPattern: HoldingPattern,
    val fixPoint: PointId,
    val loopPoints: List<PointId>,
    val hold: HoldSpec,
    val expectFurtherClearanceAt: String? = null
)

data class ResolvedCircuitJoinInstruction(
    val aerodrome: Aerodrome,
    val circuit: CircuitProcedure,
    val join: xyz.easiersaid.twr.core.world.CircuitJoin,
    val joinEntryPoint: PointId,
    val joinPathPoints: List<PointId>,
    val circuitPoints: List<PointId>
)

data class ResolvedContinueApproachInstruction(
    val aerodrome: Aerodrome,
    val approach: InstrumentApproach,
    val waypointPoints: List<PointId>,
    val thresholdPoint: PointId
)

data class ResolvedExtendedDownwindInstruction(
    val aerodrome: Aerodrome,
    val circuit: CircuitProcedure,
    val extendedPathPoints: List<PointId>,
    val offRampPoints: List<List<PointId>>
)

data class ResolvedOrbitInstruction(
    val aerodrome: Aerodrome,
    val circuit: CircuitProcedure,
    val orbitPoint: PointId,
    val direction: OrbitDirection,
    val loopPoints: List<PointId>
)

data class ResolvedAirspaceInstruction(
    val aerodrome: Aerodrome,
    val airspace: AirspaceVolume,
    val route: ResolvedRouteSpec? = null,
    val levelRestriction: Level? = null,
    val routeInteraction: ResolvedAirspaceRouteInteraction? = null
)

data class AirspaceBoundaryTransition(
    val from: PointId,
    val to: PointId
)

enum class AirspaceRouteInteractionType {
    CONTAINED,
    ENTRY_ONLY,
    EXIT_ONLY,
    TRANSIT
}

data class ResolvedAirspaceRouteInteraction(
    val routePoints: List<PointId>,
    val insidePoints: List<PointId>,
    val entryTransitions: List<AirspaceBoundaryTransition>,
    val exitTransitions: List<AirspaceBoundaryTransition>
) {
    private val insidePointSet = insidePoints.toSet()

    val startsInside: Boolean =
        routePoints.firstOrNull() in insidePointSet

    val endsInside: Boolean =
        routePoints.lastOrNull() in insidePointSet

    val touchesAirspace: Boolean =
        insidePoints.isNotEmpty() || entryTransitions.isNotEmpty() || exitTransitions.isNotEmpty()

    val interactionType: AirspaceRouteInteractionType? =
        when {
            !touchesAirspace -> null
            entryTransitions.isNotEmpty() && exitTransitions.isNotEmpty() -> AirspaceRouteInteractionType.TRANSIT
            entryTransitions.isNotEmpty() -> AirspaceRouteInteractionType.ENTRY_ONLY
            exitTransitions.isNotEmpty() -> AirspaceRouteInteractionType.EXIT_ONLY
            else -> AirspaceRouteInteractionType.CONTAINED
        }
}

data class ResolvedRoleFrequency(
    val aerodrome: Aerodrome,
    val roleName: RoleName,
    val role: AerodromeRole,
    val publishedFrequency: Frequency,
    val instructedFrequency: Frequency,
    val publishedHandoff: HandoffStep? = null
)

enum class ResolvedVectorKind {
    FLY_HEADING,
    TURN_HEADING,
    CONTINUE_PRESENT_HEADING,
    TURN_BY_DEGREES
}

data class ResolvedVectorInstruction(
    val kind: ResolvedVectorKind,
    val targetHeading: Heading? = null,
    val turnDirection: TurnDirection? = null,
    val turnDegrees: Int? = null,
    val capturedHeading: Heading? = null
)

fun AviationWorld.resolveContinueApproach(
    context: AerodromeResolutionContext,
): ResolutionResult<ResolvedContinueApproachInstruction> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val approachId = context.currentApproach ?: return unresolved(
        ResolutionFailureCode.MISSING_CURRENT_APPROACH,
        "Continue-approach resolution requires a current approach at aerodrome ${aerodrome.icao.value}"
    )
    val approach = aerodrome.approaches[approachId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_APPROACH,
        "Current approach ${approachId.value} is not defined at aerodrome ${aerodrome.icao.value}"
    )
    val runway = aerodrome.runways[approach.runway] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_RUNWAY,
        "Approach ${approach.id.value} references unknown runway ${approach.runway.value} at aerodrome ${aerodrome.icao.value}"
    )

    return resolved(
        ResolvedContinueApproachInstruction(
            aerodrome = aerodrome,
            approach = approach,
            waypointPoints = approach.waypoints.map { waypoint -> waypoint.point },
            thresholdPoint = runway.threshold
        )
    )
}

fun AviationWorld.resolveExtendDownwind(
    context: AerodromeResolutionContext,
): ResolutionResult<ResolvedExtendedDownwindInstruction> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val circuitId = context.currentCircuit ?: return unresolved(
        ResolutionFailureCode.MISSING_CURRENT_CIRCUIT,
        "Extend-downwind resolution requires a current circuit at aerodrome ${aerodrome.icao.value}"
    )
    val circuit = aerodrome.circuits[circuitId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_CIRCUIT_PROCEDURE,
        "Current circuit ${circuitId.value} is not defined at aerodrome ${aerodrome.icao.value}"
    )
    val extension = circuit.extendedDownwind ?: return unresolved(
        ResolutionFailureCode.NO_EXTENDED_DOWNWIND,
        "Circuit ${circuit.id.value} at aerodrome ${aerodrome.icao.value} has no published extended downwind"
    )

    return resolved(
        ResolvedExtendedDownwindInstruction(
            aerodrome = aerodrome,
            circuit = circuit,
            extendedPathPoints = extension.extendedPath.points,
            offRampPoints = extension.offRamps.map { offRamp -> offRamp.path.points }
        )
    )
}

@Suppress("ReturnCount") // guard-clause early returns enumerate the resolution preconditions;
// each named failure is more diagnostic than a folded single-expression alternative.
fun AviationWorld.resolveOrbit(
    context: AerodromeResolutionContext,
    instruction: Orbit
): ResolutionResult<ResolvedOrbitInstruction> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val circuitId = context.currentCircuit ?: return unresolved(
        ResolutionFailureCode.MISSING_CURRENT_CIRCUIT,
        "Orbit resolution requires a current circuit at aerodrome ${aerodrome.icao.value}"
    )
    val circuit = aerodrome.circuits[circuitId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_CIRCUIT_PROCEDURE,
        "Current circuit ${circuitId.value} is not defined at aerodrome ${aerodrome.icao.value}"
    )
    val currentPoint = context.currentPoint ?: return unresolved(
        ResolutionFailureCode.MISSING_CURRENT_POINT,
        "Orbit resolution requires a current point at aerodrome ${aerodrome.icao.value}"
    )
    val matches = circuit.orbitPoints.filter { orbitPoint ->
        orbitPoint.point == currentPoint && orbitPoint.direction == instruction.direction
    }
    val orbitPoint = when (matches.size) {
        0 -> return unresolved(
            ResolutionFailureCode.NO_ORBIT_POINT,
            "Circuit ${circuit.id.value} has no ${instruction.direction.name.lowercase()} orbit point at ${currentPoint.value}"
        )

        1 -> matches.single()
        else -> return unresolved(
            ResolutionFailureCode.AMBIGUOUS_ORBIT_POINT,
            "Circuit ${circuit.id.value} has multiple ${instruction.direction.name.lowercase()} orbit points at ${currentPoint.value}"
        )
    }

    return resolved(
        ResolvedOrbitInstruction(
            aerodrome = aerodrome,
            circuit = circuit,
            orbitPoint = orbitPoint.point,
            direction = orbitPoint.direction,
            loopPoints = orbitPoint.loop.points
        )
    )
}

fun AviationWorld.resolveRunwayOperation(
    aerodromeId: AerodromeId,
    runwayId: RunwayId
): ResolutionResult<ResolvedRunwayOperation> {
    val aerodrome = aerodrome(aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${aerodromeId.value}"
    )
    val runway = aerodrome.runways[runwayId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_RUNWAY,
        "Unknown runway ${runwayId.value} at aerodrome ${aerodrome.icao.value}"
    )
    return resolved(
        ResolvedRunwayOperation(
            aerodrome = aerodrome,
            runway = runway,
            thresholdPoint = runway.threshold,
            pathPoints = runway.path.points
        )
    )
}

fun AviationWorld.resolveTaxiTo(
    context: GroundResolutionContext,
    instruction: TaxiClearance
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
                taxiwayPathDistance(taxiway.path, context.currentPoint, point) ?: Double.POSITIVE_INFINITY
            } ?: return@mapNotNull null

        val distance = taxiwayPathDistance(taxiway.path, context.currentPoint, crossingPoint)
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

@Suppress("ReturnCount") // guard-clause early returns enumerate the approach-resolution
// preconditions (aerodrome / approach / runway / waypoints / threshold).
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
            "Multiple ${instruction.approachType} approaches exist for runway " +
                "${instruction.runway.value} at aerodrome ${aerodrome.icao.value}"
        )
    }

    val runway = aerodrome.runways[approach.runway] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_RUNWAY,
        "Unknown runway ${approach.runway.value} at aerodrome ${aerodrome.icao.value}"
    )
    val circlingRunway = instruction.circlingRunway?.let { runwayId ->
        aerodrome.runways[runwayId] ?: return unresolved(
            ResolutionFailureCode.UNKNOWN_CIRCLING_RUNWAY,
            "Unknown circling runway ${runwayId.value} at aerodrome ${aerodrome.icao.value}"
        )
    }
    val missedApproachHoldingPattern = aerodrome.holdingPatterns[approach.missedApproach.holdAt]
        ?: return unresolved(
            ResolutionFailureCode.UNKNOWN_HOLDING_PATTERN,
            "Missed-approach holding pattern ${approach.missedApproach.holdAt.value} is not defined at aerodrome ${aerodrome.icao.value}"
        )

    return resolved(
        ResolvedApproachClearance(
            aerodrome = aerodrome,
            approach = approach,
            circlingRunway = circlingRunway,
            waypointPoints = approach.waypoints.map { waypoint -> waypoint.point },
            thresholdPoint = runway.threshold,
            missedApproachPoints = approach.missedApproach.waypoints.map { waypoint -> waypoint.point },
            missedApproachHoldingPattern = missedApproachHoldingPattern
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
    val routePoints = when (resolvedRoute) {
        is arrow.core.Either.Left -> return resolvedRoute
        is arrow.core.Either.Right -> routePointsForClearance(
            route = resolvedRoute.value,
            clearanceLimit = clearanceLimit
        )

        null -> listOf(clearanceLimit.point)
    }
    val clearanceLimitHoldingPattern = aerodrome.holdingPatterns.values
        .singleOrNull { holdingPattern -> holdingPattern.fix == clearanceLimit.id }

    return when (resolvedRoute) {
        is arrow.core.Either.Right -> resolved(
            ResolvedRouteClearance(
                aerodrome = aerodrome,
                clearanceLimit = clearanceLimit,
                route = resolvedRoute.value,
                routePoints = routePoints,
                clearanceLimitHoldingPattern = clearanceLimitHoldingPattern
            )
        )

        null -> resolved(
            ResolvedRouteClearance(
                aerodrome = aerodrome,
                clearanceLimit = clearanceLimit,
                route = null,
                routePoints = routePoints,
                clearanceLimitHoldingPattern = clearanceLimitHoldingPattern
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
            fixPoint = fix.point,
            loopPoints = holdingPattern.loop.points,
            hold = instruction.hold,
            expectFurtherClearanceAt = instruction.expectFurtherClearanceAt
        )
    )
}

fun AviationWorld.resolveContactFrequency(
    context: AerodromeResolutionContext,
    instruction: ContactFrequency
): ResolutionResult<ResolvedRoleFrequency> =
    resolveRoleFrequency(context, instruction.role, instruction.frequency, PilotHandoffAction.CONTACT)

fun AviationWorld.resolveMonitorFrequency(
    context: AerodromeResolutionContext,
    instruction: MonitorFrequency
): ResolutionResult<ResolvedRoleFrequency> =
    resolveRoleFrequency(context, instruction.role, instruction.frequency, PilotHandoffAction.MONITOR)

fun AviationWorld.resolveRemainOutsideControlledAirspace(
    context: AerodromeResolutionContext,
    instruction: RemainOutsideControlledAirspace
): ResolutionResult<ResolvedAirspaceInstruction> =
    resolveAirspaceInstruction(
        context = context,
        airspaceId = instruction.airspace,
        route = null,
        levelRestriction = null
    )

fun AviationWorld.resolveClearedToEnterControlZone(
    context: AerodromeResolutionContext,
    instruction: ClearedToEnterControlZone
): ResolutionResult<ResolvedAirspaceInstruction> =
    resolveAirspaceInstruction(
        context = context,
        airspaceId = instruction.airspace,
        route = instruction.route,
        levelRestriction = instruction.levelRestriction
    )

fun AviationWorld.resolveSpecialVfrClearance(
    context: AerodromeResolutionContext,
    instruction: SpecialVfrClearance
): ResolutionResult<ResolvedAirspaceInstruction> =
    resolveAirspaceInstruction(
        context = context,
        airspaceId = instruction.airspace,
        route = instruction.route,
        levelRestriction = instruction.levelRestriction
    )

private fun AviationWorld.resolveRoleFrequency(
    context: AerodromeResolutionContext,
    roleName: RoleName,
    explicitFrequency: Frequency?,
    handoffAction: PilotHandoffAction
): ResolutionResult<ResolvedRoleFrequency> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val role = aerodrome.roles[roleName] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_ROLE,
        "Aerodrome ${aerodrome.icao.value} does not declare role ${roleName.name}"
    )
    val publishedHandoff = when (
        val result = resolvePublishedHandoff(
            aerodrome = aerodrome,
            context = context,
            targetRole = roleName,
            handoffAction = handoffAction
        )
    ) {
        is arrow.core.Either.Left -> return result
        is arrow.core.Either.Right -> result.value
    }

    return resolved(
        ResolvedRoleFrequency(
            aerodrome = aerodrome,
            roleName = roleName,
            role = role,
            publishedFrequency = role.frequency,
            instructedFrequency = explicitFrequency ?: role.frequency,
            publishedHandoff = publishedHandoff
        )
    )
}

private fun resolvePublishedHandoff(
    aerodrome: Aerodrome,
    context: AerodromeResolutionContext,
    targetRole: RoleName,
    handoffAction: PilotHandoffAction
): ResolutionResult<HandoffStep?> {
    val currentRole = context.currentRole ?: return resolved(null)
    val candidates = aerodrome.aip.handoffSequence.filter { step ->
        step.from == currentRole &&
            step.to == targetRole &&
            step.pilotAction == handoffAction
    }
    if (candidates.isEmpty()) {
        return resolved(null)
    }
    if (candidates.size == 1) {
        return resolved(candidates.single())
    }

    val matchingContext = candidates.filter { step -> handoffMatchesContext(step, context) }
    return when (matchingContext.size) {
        1 -> resolved(matchingContext.single())
        else -> unresolved(
            ResolutionFailureCode.AMBIGUOUS_PUBLISHED_HANDOFF,
            "Aerodrome ${aerodrome.icao.value} declares multiple " +
                "${handoffAction.name.lowercase()} handoffs from " +
                "${currentRole.name} to ${targetRole.name} without enough context to disambiguate"
        )
    }
}

private fun handoffMatchesContext(
    handoff: HandoffStep,
    context: AerodromeResolutionContext
): Boolean =
    when (handoff.at.kind) {
        xyz.easiersaid.twr.core.world.HandoffPointKind.HOLDING_POINT ->
            handoff.at.point != null && handoff.at.point == context.currentPoint

        xyz.easiersaid.twr.core.world.HandoffPointKind.BOUNDARY_FIX ->
            handoff.at.fix != null && handoff.at.fix == context.currentFix

        xyz.easiersaid.twr.core.world.HandoffPointKind.AIRBORNE ->
            context.onGround == false
    }

private fun AviationWorld.resolveAirspaceInstruction(
    context: AerodromeResolutionContext,
    airspaceId: xyz.easiersaid.twr.protocol.AirspaceVolumeId,
    route: RouteSpec?,
    levelRestriction: Level?
): ResolutionResult<ResolvedAirspaceInstruction> {
    val aerodrome = aerodrome(context.aerodromeId) ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val airspaceVolume = airspace[airspaceId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AIRSPACE_VOLUME,
        "Unknown airspace volume ${airspaceId.value}"
    )
    val resolvedRoute = route?.let { resolveRouteSpec(aerodrome, it) }

    return when (resolvedRoute) {
        is arrow.core.Either.Left -> resolvedRoute
        is arrow.core.Either.Right -> {
            val routeInteraction = resolveAirspaceRouteInteraction(
                airspace = airspaceVolume,
                route = resolvedRoute.value
            )
            if (!routeInteraction.touchesAirspace) {
                return unresolved(
                    ResolutionFailureCode.AIRSPACE_ROUTE_DOES_NOT_INTERACT,
                    "Route for airspace instruction ${airspaceId.value} does not touch airspace volume ${airspaceVolume.id.value}"
                )
            }
            resolved(
                ResolvedAirspaceInstruction(
                    aerodrome = aerodrome,
                    airspace = airspaceVolume,
                    route = resolvedRoute.value,
                    levelRestriction = levelRestriction,
                    routeInteraction = routeInteraction
                )
            )
        }

        null -> resolved(
            ResolvedAirspaceInstruction(
                aerodrome = aerodrome,
                airspace = airspaceVolume,
                route = null,
                levelRestriction = levelRestriction,
                routeInteraction = null
            )
        )
    }
}

private fun resolveAirspaceRouteInteraction(
    airspace: AirspaceVolume,
    route: ResolvedRouteSpec
): ResolvedAirspaceRouteInteraction {
    val routePoints = route.routePoints()
    // Route/airspace interaction still follows explicit memberPoints, not geometric
    // point-in-polygon inference from the published boundary.
    val insidePoints = routePoints.filter { point -> point in airspace.memberPoints }
    val transitions = routePoints.zipWithNext()

    val entryTransitions = transitions.mapNotNull { (from, to) ->
        if (from !in airspace.memberPoints && to in airspace.memberPoints) {
            AirspaceBoundaryTransition(from, to)
        } else {
            null
        }
    }
    val exitTransitions = transitions.mapNotNull { (from, to) ->
        if (from in airspace.memberPoints && to !in airspace.memberPoints) {
            AirspaceBoundaryTransition(from, to)
        } else {
            null
        }
    }

    return ResolvedAirspaceRouteInteraction(
        routePoints = routePoints,
        insidePoints = insidePoints,
        entryTransitions = entryTransitions,
        exitTransitions = exitTransitions
    )
}

private fun AviationWorld.routePointsForClearance(
    route: ResolvedRouteSpec,
    clearanceLimit: Fix
): List<PointId> =
    when (route) {
        is ResolvedRouteSpec.Direct -> listOf(route.fix.point)
        is ResolvedRouteSpec.Via -> route.fixes.map { fix -> fix.point }
        is ResolvedRouteSpec.AirwaySegment -> route.routePoints()
        is ResolvedRouteSpec.SidProcedure ->
            route.sid.publishedPointsTo(clearanceLimit.point)
        is ResolvedRouteSpec.StarProcedure ->
            route.star.publishedPointsTo(clearanceLimit.point)
        is ResolvedRouteSpec.VfrRouteProcedure -> route.route.waypoints.map { waypoint -> waypoint.point }
    }

private fun ResolvedRouteSpec.routePoints(): List<PointId> =
    when (this) {
        is ResolvedRouteSpec.Direct -> listOf(fix.point)
        is ResolvedRouteSpec.Via -> fixes.map { fix -> fix.point }
        is ResolvedRouteSpec.AirwaySegment -> {
            val points = airway.waypoints.map { waypoint -> waypoint.point }
            points.take(points.indexOf(exitFix.point) + 1)
        }
        is ResolvedRouteSpec.SidProcedure -> sid.waypoints.map { waypoint -> waypoint.point }
        is ResolvedRouteSpec.StarProcedure -> star.waypoints.map { waypoint -> waypoint.point }
        is ResolvedRouteSpec.VfrRouteProcedure -> route.waypoints.map { waypoint -> waypoint.point }
    }

private fun Sid.publishedPointsTo(limitPoint: PointId): List<PointId> {
    val trunkPoints = waypoints.map { waypoint -> waypoint.point }
    if (limitPoint in trunkPoints) {
        return trunkPoints.take(trunkPoints.indexOf(limitPoint) + 1)
    }

    val transition = transitions.values.firstOrNull { transitionWaypoints ->
        transitionWaypoints.any { waypoint -> waypoint.point == limitPoint }
    } ?: return trunkPoints

    return trunkPoints.connectedWithTransition(transition.map { waypoint -> waypoint.point }, limitPoint)
}

private fun Star.publishedPointsTo(limitPoint: PointId): List<PointId> {
    val trunkPoints = waypoints.map { waypoint -> waypoint.point }
    if (limitPoint in trunkPoints) {
        return trunkPoints.take(trunkPoints.indexOf(limitPoint) + 1)
    }

    val transition = transitions.values.firstOrNull { transitionWaypoints ->
        transitionWaypoints.any { waypoint -> waypoint.point == limitPoint }
    } ?: return trunkPoints

    return trunkPoints.connectedWithTransition(transition.map { waypoint -> waypoint.point }, limitPoint)
}

private fun List<PointId>.connectedWithTransition(
    transition: List<PointId>,
    limitPoint: PointId
): List<PointId> {
    if (transition.isEmpty()) return this
    val connectionIndex = indexOf(transition.first())
    if (connectionIndex == -1) {
        return this
    }
    val truncatedTransition = if (limitPoint in transition) {
        transition.take(transition.indexOf(limitPoint) + 1)
    } else {
        transition
    }
    return take(connectionIndex + 1) + truncatedTransition.drop(1)
}

private fun AviationWorld.resolveRouteSpec(
    aerodrome: Aerodrome,
    route: RouteSpec
): ResolutionResult<ResolvedRouteSpec> = arrow.core.raise.either {
    when (route) {
        is RouteSpec.Direct -> {
            val fix = fixes[route.fix] ?: raise(ResolutionFailure(
                ResolutionFailureCode.UNKNOWN_FIX,
                "Unknown route fix ${route.fix.value}"
            ))
            ResolvedRouteSpec.Direct(fix)
        }

        is RouteSpec.Via -> {
            val routeFixes = route.fixes.map { fixId ->
                fixes[fixId] ?: raise(ResolutionFailure(
                    ResolutionFailureCode.UNKNOWN_FIX,
                    "Unknown route fix ${fixId.value}"
                ))
            }
            ResolvedRouteSpec.Via(routeFixes)
        }

        is RouteSpec.Airway -> {
            val airway = airways[route.airway] ?: raise(ResolutionFailure(
                ResolutionFailureCode.UNKNOWN_AIRWAY,
                "Unknown airway ${route.airway.value}"
            ))
            val exitFix = fixes[route.exitFix] ?: raise(ResolutionFailure(
                ResolutionFailureCode.UNKNOWN_FIX,
                "Unknown airway exit fix ${route.exitFix.value}"
            ))
            if (airway.waypoints.none { waypoint -> waypoint.point == exitFix.point }) {
                raise(ResolutionFailure(
                    ResolutionFailureCode.AIRWAY_EXIT_FIX_NOT_ON_AIRWAY,
                    "Fix ${route.exitFix.value} is not on airway ${airway.id.value}"
                ))
            }
            ResolvedRouteSpec.AirwaySegment(airway, exitFix)
        }

        is RouteSpec.ViaSid -> {
            val sid = aerodrome.sids[route.sid] ?: raise(ResolutionFailure(
                ResolutionFailureCode.UNKNOWN_SID,
                "Unknown SID ${route.sid.value} at aerodrome ${aerodrome.icao.value}"
            ))
            ResolvedRouteSpec.SidProcedure(sid)
        }

        is RouteSpec.ViaStar -> {
            val star = aerodrome.stars[route.star] ?: raise(ResolutionFailure(
                ResolutionFailureCode.UNKNOWN_STAR,
                "Unknown STAR ${route.star.value} at aerodrome ${aerodrome.icao.value}"
            ))
            ResolvedRouteSpec.StarProcedure(star)
        }

        is RouteSpec.ViaRoute -> {
            val vfrRoute = vfrRoutes[route.route] ?: raise(ResolutionFailure(
                ResolutionFailureCode.UNKNOWN_VFR_ROUTE,
                "Unknown VFR route ${route.route.value}"
            ))
            ResolvedRouteSpec.VfrRouteProcedure(vfrRoute)
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
                taxiwayPathDistance(taxiway.path, currentPoint, point.point) ?: Double.POSITIVE_INFINITY
            } ?: return@mapNotNull null

        val distance = taxiwayPathDistance(taxiway.path, currentPoint, holdingPoint.point)
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

private fun AviationWorld.taxiwayPathDistance(
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
