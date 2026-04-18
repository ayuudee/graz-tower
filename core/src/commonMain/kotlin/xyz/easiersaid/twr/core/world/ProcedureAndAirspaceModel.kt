package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.AirwayId
import xyz.easiersaid.twr.protocol.ApproachId
import xyz.easiersaid.twr.protocol.ApproachType
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.DmeDistanceNm
import xyz.easiersaid.twr.protocol.FirId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.HoldingPatternId
import xyz.easiersaid.twr.protocol.JoinType
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.Minutes
import xyz.easiersaid.twr.protocol.OperationalSectorId
import xyz.easiersaid.twr.protocol.OrbitDirection
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PublishedVfrProcedureId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SidId
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.StarId
import xyz.easiersaid.twr.protocol.TurnDirection
import xyz.easiersaid.twr.protocol.VfrRouteId

sealed interface AltitudeBoundary {
    data object Surface : AltitudeBoundary
    data class AtLevel(val level: Level) : AltitudeBoundary
    data object Unlimited : AltitudeBoundary
}

data class AltitudeBand(
    val lower: AltitudeBoundary,
    val upper: AltitudeBoundary
)

sealed interface AltitudeConstraint {
    data class At(val level: Level) : AltitudeConstraint
    data class AtOrAbove(val minimum: Level) : AltitudeConstraint
    data class AtOrBelow(val maximum: Level) : AltitudeConstraint
    data class Between(
        val minimum: Level,
        val maximum: Level
    ) : AltitudeConstraint
}

sealed interface SpeedConstraint {
    data class At(val speed: Speed) : SpeedConstraint
    data class AtOrAbove(val minimum: Speed) : SpeedConstraint
    data class AtOrBelow(val maximum: Speed) : SpeedConstraint
    data class Between(
        val minimum: Speed,
        val maximum: Speed
    ) : SpeedConstraint
}

data class Waypoint(
    val point: PointId,
    val name: String? = null,
    val altitudeConstraint: AltitudeConstraint? = null,
    val speedConstraint: SpeedConstraint? = null
)

data class VfrRouteAirspaceSegment(
    val from: PointId,
    val to: PointId,
    val airspaceVolume: AirspaceVolumeId
) {
    init {
        require(from != to) { "VFR route airspace segments must connect two distinct points" }
    }
}

sealed interface VfrRouteAirspaceProfile {
    data class InVolume(val airspaceVolume: AirspaceVolumeId) : VfrRouteAirspaceProfile
    data class InClass(val airspaceClass: AirspaceClass) : VfrRouteAirspaceProfile
    data class Segmented(val segments: List<VfrRouteAirspaceSegment>) : VfrRouteAirspaceProfile {
        init {
            require(segments.isNotEmpty()) { "Segmented VFR route airspace profile must contain at least one segment" }
        }
    }
}

data class VfrRoute(
    val id: VfrRouteId,
    val name: String,
    val waypoints: List<Waypoint>,
    val airspaceProfile: VfrRouteAirspaceProfile? = null
) {
    init {
        require(waypoints.size >= 2) { "VFR route must contain at least two waypoints" }
    }
}

data class Airway(
    val id: AirwayId,
    val name: String,
    val waypoints: List<Waypoint>,
    val altitudeBand: AltitudeBand,
    val bidirectional: Boolean = true
) {
    init {
        require(waypoints.size >= 2) { "Airway must contain at least two waypoints" }
    }
}

data class Sid(
    val id: SidId,
    val name: String,
    val runway: RunwayId,
    val waypoints: List<Waypoint>,
    val transitions: Map<String, List<Waypoint>> = emptyMap()
) {
    init {
        require(waypoints.isNotEmpty()) { "SID trunk must contain at least one waypoint" }
    }
}

data class Star(
    val id: StarId,
    val name: String,
    val waypoints: List<Waypoint>,
    val transitions: Map<String, List<Waypoint>> = emptyMap()
) {
    init {
        require(waypoints.isNotEmpty()) { "STAR trunk must contain at least one waypoint" }
    }
}

enum class MinimumType {
    DECISION_ALTITUDE,
    MINIMUM_DESCENT_ALTITUDE
}

data class ApproachMinimum(
    val type: MinimumType,
    val altitude: Level,
    val height: Level? = null
)

data class MissedApproachProcedure(
    val waypoints: List<Waypoint>,
    val holdAt: HoldingPatternId
) {
    init {
        require(waypoints.isNotEmpty()) { "Missed approach must contain at least one waypoint" }
    }
}

data class InstrumentApproach(
    val id: ApproachId,
    val name: String,
    val type: ApproachType,
    val runway: RunwayId,
    val waypoints: List<Waypoint>,
    val minimumAltitude: ApproachMinimum,
    val missedApproach: MissedApproachProcedure
) {
    init {
        require(waypoints.isNotEmpty()) { "Instrument approach must contain at least one waypoint" }
    }
}

enum class LegName {
    UPWIND,
    CROSSWIND,
    DOWNWIND,
    BASE,
    FINAL
}

data class CircuitLeg(
    val name: LegName,
    val path: Path
)

data class CircuitJoin(
    val type: JoinType,
    val entryPoint: PointId,
    val entryPath: Path? = null
)

data class OffRamp(
    val path: Path
)

data class ExtendedDownwind(
    val extendedPath: Path,
    val offRamps: List<OffRamp> = emptyList()
)

data class OrbitPoint(
    val point: PointId,
    val loop: Path,
    val direction: OrbitDirection
) {
    init {
        require(loop.points.first() == point) { "Orbit loop must start at its orbit point" }
        require(loop.points.last() == point) { "Orbit loop must end at its orbit point" }
    }
}

data class CircuitProcedure(
    val id: CircuitProcedureId,
    val runway: RunwayId,
    val direction: CircuitDirection,
    val legs: List<CircuitLeg>,
    val altitude: Level,
    val reportingPoints: Map<LegName, PointId> = emptyMap(),
    val joinProcedures: List<CircuitJoin> = emptyList(),
    val extendedDownwind: ExtendedDownwind? = null,
    val orbitPoints: List<OrbitPoint> = emptyList(),
    val goAroundPath: Path
) {
    init {
        require(legs.isNotEmpty()) { "Circuit procedure must contain at least one leg" }
        require(
            legs.zipWithNext().all { (left, right) ->
                left.path.points.last() == right.path.points.first()
            }
        ) { "Circuit legs must connect in sequence" }
        require(
            legs.last().path.points.last() == legs.first().path.points.first()
        ) { "Circuit procedure must form a closed loop" }
    }
}

data class HoldingPattern(
    val id: HoldingPatternId,
    val fix: FixId,
    val inboundCourse: Degrees,
    val turnDirection: TurnDirection,
    val loop: Path,
    val legTime: Minutes? = null,
    val legDistance: DmeDistanceNm? = null,
    val maxSpeed: Knots? = null,
    val altitude: Level,
    val stackSeparation: Feet? = null
) {
    init {
        require(loop.points.first() == loop.points.last()) { "Holding pattern loop must be closed" }
    }
}

enum class AirspaceClass {
    A,
    B,
    C,
    D,
    E,
    F,
    G
}

enum class AirspaceVolumeType {
    CTR,
    TMA,
    ATZ,
    FIR,
    UIR,
    OCA
}

data class BoundaryRing(
    val points: List<PointId>
) {
    init {
        require(points.distinct().size >= 3) { "Boundary ring must contain at least three distinct points" }
    }
}

data class AirspaceBoundary(
    val rings: List<BoundaryRing>
) {
    init {
        require(rings.isNotEmpty()) { "Airspace boundary must contain at least one ring" }
    }
}

data class ContactRequirement(
    val role: RoleName,
    val timing: ContactTiming
)

sealed interface ContactTiming {
    data object BeforeEntry : ContactTiming
    data class BeforePoint(val point: PointId) : ContactTiming
    data class AtPoint(val point: PointId) : ContactTiming
    data class DistanceBefore(
        val point: PointId,
        val distance: DmeDistanceNm
    ) : ContactTiming
    /** Contact must be established by the time the aircraft reaches this altitude. */
    data class ByAltitude(val level: Level) : ContactTiming
}

enum class OperationalSectorKind {
    VFR_OPERATIONAL,
    IFR_HOLDING_SECTOR,
    VFR_TRAINING_AREA,
    GLIDER_SECTOR,
    NIGHT_VFR_SECTOR,
    HELICOPTER_OPERATIONAL
}

sealed interface OperationalSectorAnchor {
    data class CtrBoundaryReportingPoint(val point: PointId) : OperationalSectorAnchor
    data class ReportingPoint(val point: PointId) : OperationalSectorAnchor
    data class Navaid(val point: PointId) : OperationalSectorAnchor
}

enum class OperationalSectorCtrRelation {
    WITHIN_CTR,
    OVERLAPS_CTR,
    BOUNDARY_OR_OVERLAP,
    ADJACENT_TO_CTR,
    INSIDE_TMA_OUTSIDE_CTR
}

data class OperationalSector(
    val id: OperationalSectorId,
    val name: String,
    val kind: OperationalSectorKind,
    val boundary: AirspaceBoundary,
    val anchor: OperationalSectorAnchor? = null,
    val entryExitPoints: Set<PointId> = emptySet(),
    val altitudeBand: AltitudeBand? = null,
    val contactRequirement: ContactRequirement? = null,
    val relationToCtr: OperationalSectorCtrRelation? = null,
    val associatedProcedures: Set<PublishedVfrProcedureId> = emptySet(),
    val note: String? = null,
    val specialProcedureNote: String? = null
)

sealed interface PublishedPointReference {
    val reference: String

    data class Fix(
        override val reference: String,
        val point: PointId
    ) : PublishedPointReference

    data class NamedPoint(
        override val reference: String,
        val point: PointId
    ) : PublishedPointReference

    data class SectorAnchor(
        override val reference: String,
        val point: PointId
    ) : PublishedPointReference

    data class Literal(
        override val reference: String
    ) : PublishedPointReference
}

data class PublishedMapLabel(
    val label: String,
    val location: PublishedPointReference
)

enum class PublishedVfrProcedureKind {
    ARRIVAL,
    DEPARTURE,
    TRANSIT,
    CIRCUIT_PUBLICATION,
    CIRCUIT_ATTACHED_HOLD
}

data class PublishedProcedureAdvisories(
    val contact: String? = null,
    val altitude: String? = null,
    val route: String? = null,
    val reporting: String? = null,
    val availability: String? = null,
    val specialProcedure: String? = null,
    val noiseAbatement: String? = null,
    val speedCap: String? = null,
    val squawkConvention: String? = null,
    val activationHours: String? = null,
    val equipmentMinimum: String? = null,
    val language: String? = null,
    val general: String? = null
)

data class PublishedProcedureCommunicationFailure(
    val beforeContactEstablished: String? = null,
    val afterContactEstablishedExitSequence: List<PublishedPointReference> = emptyList(),
    val note: String? = null
)

data class PublishedVfrProcedure(
    val id: PublishedVfrProcedureId,
    val plateId: PlateId,
    val kind: PublishedVfrProcedureKind,
    val publishedSequence: List<PublishedPointReference> = emptyList(),
    val associatedVfrRoutes: Set<VfrRouteId> = emptySet(),
    val associatedOperationalSectors: Set<OperationalSectorId> = emptySet(),
    val associatedCircuits: Set<CircuitProcedureId> = emptySet(),
    val contactRequirement: ContactRequirement? = null,
    val advisories: PublishedProcedureAdvisories? = null,
    val mapLabels: List<PublishedMapLabel> = emptyList(),
    val terminatesAt: PublishedPointReference? = null,
    val holdAt: PublishedPointReference? = null,
    val communicationFailure: PublishedProcedureCommunicationFailure? = null,
    val departureRunways: Set<RunwayId> = emptySet(),
    val applicableRunways: Set<RunwayId> = emptySet()
)

data class AirspaceVolume(
    val id: AirspaceVolumeId,
    val name: String,
    val type: AirspaceVolumeType,
    val airspaceClass: AirspaceClass,
    val altitudeBand: AltitudeBand,
    val memberPoints: Set<PointId>,
    val fir: FirId,
    val boundary: AirspaceBoundary? = null
) {
    init {
        require(memberPoints.isNotEmpty()) { "Airspace volume must reference at least one member point" }
    }
}

data class FlightInformationRegion(
    val id: FirId,
    val name: String,
    val volumes: Set<AirspaceVolumeId>
) {
    init {
        require(volumes.isNotEmpty()) { "FIR must contain at least one airspace volume" }
    }
}

internal fun List<Waypoint>.asPathOrNull(): Path? =
    takeIf { it.size >= 2 }?.let { waypoints ->
        Path(waypoints.map { waypoint -> waypoint.point })
    }

/** Materializes the implicit closing edge used by boundary validation/indexing code. */
internal fun BoundaryRing.asClosedPath(): Path =
    Path(points + points.first())

/** Accepts both explicitly closed and implicit-open paths when normalizing external boundary data. */
fun Path.asBoundaryRing(): BoundaryRing =
    BoundaryRing(
        if (points.size >= 2 && points.first() == points.last()) {
            points.dropLast(1)
        } else {
            points
        }
    )

internal fun ContactTiming.pointOrNull(): PointId? =
    when (this) {
        ContactTiming.BeforeEntry,
        is ContactTiming.ByAltitude -> null

        is ContactTiming.BeforePoint -> point
        is ContactTiming.AtPoint -> point
        is ContactTiming.DistanceBefore -> point
    }

internal fun OperationalSectorAnchor?.pointOrNull(): PointId? =
    when (this) {
        null -> null
        is OperationalSectorAnchor.CtrBoundaryReportingPoint -> point
        is OperationalSectorAnchor.ReportingPoint -> point
        is OperationalSectorAnchor.Navaid -> point
    }

internal fun PublishedPointReference.pointOrNull(): PointId? =
    when (this) {
        is PublishedPointReference.Fix -> point
        is PublishedPointReference.NamedPoint -> point
        is PublishedPointReference.SectorAnchor -> point
        is PublishedPointReference.Literal -> null
    }
