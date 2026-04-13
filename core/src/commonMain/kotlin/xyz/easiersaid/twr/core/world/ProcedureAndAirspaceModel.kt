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
import xyz.easiersaid.twr.protocol.OrbitDirection
import xyz.easiersaid.twr.protocol.PointId
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

data class VfrRoute(
    val id: VfrRouteId,
    val name: String,
    val waypoints: List<Waypoint>,
    val airspaceClass: AirspaceClass
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

data class AirspaceVolume(
    val id: AirspaceVolumeId,
    val name: String,
    val type: AirspaceVolumeType,
    val airspaceClass: AirspaceClass,
    val altitudeBand: AltitudeBand,
    val points: Set<PointId>,
    val fir: FirId
) {
    init {
        require(points.isNotEmpty()) { "Airspace volume must reference at least one point" }
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
