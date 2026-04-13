package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.ApronId
import xyz.easiersaid.twr.protocol.AuthorityEntityType
import xyz.easiersaid.twr.protocol.AuthorityOperation
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.StandId
import xyz.easiersaid.twr.protocol.TaxiwayId

data class Meters(val value: Double) {
    init {
        require(value >= 0.0) { "Meters must be >= 0" }
    }
}

data class Feet(val value: Int) {
    init {
        require(value >= 0) { "Feet must be >= 0" }
    }
}

data class Degrees(val value: Double) {
    init {
        require(value >= 0.0 && value < 360.0) { "Degrees must be in [0, 360)" }
    }
}

data class Position(
    val xMeters: Double,
    val yMeters: Double,
    val altitudeFeet: Int? = null
) {
    init {
        require(xMeters.isFinite()) { "xMeters must be finite" }
        require(yMeters.isFinite()) { "yMeters must be finite" }
        require(altitudeFeet == null || altitudeFeet >= 0) { "altitudeFeet must be >= 0 when present" }
    }
}

data class SegmentId(
    val from: PointId,
    val to: PointId
)

sealed interface SegmentShape {
    data object Straight : SegmentShape
    data class Arc(val radius: Meters) : SegmentShape
}

enum class SurfaceType {
    GROUND,
    RUNWAY,
    SKY
}

data class Path(
    val points: List<PointId>
) {
    init {
        require(points.size >= 2) { "Path must contain at least two points" }
    }
}

data class DeclaredDistances(
    val tora: Meters,
    val toda: Meters,
    val asda: Meters,
    val lda: Meters,
    val clearway: Meters? = null
)

data class RunwayExit(
    val point: PointId,
    val taxiway: TaxiwayId
)

data class Runway(
    val id: RunwayId,
    val path: Path,
    val threshold: PointId,
    val exits: List<RunwayExit> = emptyList(),
    val declaredDistances: DeclaredDistances? = null
) {
    init {
        require(path.points.first() == threshold) { "Runway threshold must be the first path point" }
    }
}

enum class HoldingPointType {
    CAT_A,
    CAT_B,
    INTERMEDIATE
}

data class HoldingPoint(
    val point: PointId,
    val name: String? = null,
    val type: HoldingPointType,
    val runway: RunwayId? = null
)

data class Taxiway(
    val id: TaxiwayId,
    val name: String,
    val path: Path,
    val holdingPoints: List<HoldingPoint> = emptyList(),
    val bidirectional: Boolean = true
)

data class Stand(
    val id: StandId,
    val name: String,
    val point: PointId
)

data class Apron(
    val id: ApronId,
    val name: String,
    val paths: List<Path>,
    val stands: Set<StandId> = emptySet(),
    val capacity: Int? = null
) {
    init {
        require(paths.isNotEmpty()) { "Apron must contain at least one path" }
        require(capacity == null || capacity >= 0) { "Apron capacity must be >= 0 when present" }
    }
}

data class Fix(
    val id: FixId,
    val point: PointId,
    val name: String
)

data class AuthorityGrant(
    val entityType: AuthorityEntityType,
    val operations: Set<AuthorityOperation>
) {
    init {
        require(operations.isNotEmpty()) { "Authority grant must contain at least one operation" }
    }
}

data class AerodromeRole(
    val name: RoleName,
    val authorities: Set<AuthorityGrant>,
    val frequency: Frequency
)

enum class HandoffPointKind {
    HOLDING_POINT,
    AIRBORNE,
    BOUNDARY_FIX
}

data class HandoffPoint(
    val kind: HandoffPointKind,
    val point: PointId? = null,
    val fix: FixId? = null
)

enum class PilotHandoffAction {
    CONTACT,
    MONITOR
}

data class HandoffStep(
    val from: RoleName,
    val to: RoleName,
    val at: HandoffPoint,
    val pilotAction: PilotHandoffAction
)

sealed interface ActiveRunwayRule {
    data object PreferIntoWind : ActiveRunwayRule
    data class Fixed(
        val departures: RunwayId,
        val arrivals: RunwayId? = null
    ) : ActiveRunwayRule
}

data class NoiseRule(
    val description: String
)

data class AerodromeAip(
    val atisFrequency: Frequency? = null,
    val handoffSequence: List<HandoffStep> = emptyList(),
    val activeRunwaySelection: ActiveRunwayRule = ActiveRunwayRule.PreferIntoWind,
    val noiseAbatement: List<NoiseRule> = emptyList(),
    val specialInstructions: List<String> = emptyList()
)

data class Aerodrome(
    val icao: AerodromeId,
    val elevation: Feet,
    val magneticVariation: Degrees,
    val transitionAltitude: Level,
    val transitionLevel: Level? = null,
    val aip: AerodromeAip = AerodromeAip(),
    val roles: Map<RoleName, AerodromeRole> = emptyMap(),
    val controllers: Map<ControllerId, Set<RoleName>> = emptyMap(),
    val runways: Map<RunwayId, Runway> = emptyMap(),
    val taxiways: Map<TaxiwayId, Taxiway> = emptyMap(),
    val stands: Map<StandId, Stand> = emptyMap(),
    val aprons: Map<ApronId, Apron> = emptyMap()
)

data class AviationWorld(
    val fixes: Map<FixId, Fix> = emptyMap(),
    val aerodromes: Map<AerodromeId, Aerodrome> = emptyMap()
)

sealed interface EntityRef {
    data class RunwayRef(val id: RunwayId) : EntityRef
    data class TaxiwayRef(val id: TaxiwayId) : EntityRef
    data class StandRef(val id: StandId) : EntityRef
    data class ApronRef(val id: ApronId) : EntityRef
    data class FixRef(val id: FixId) : EntityRef
}

data class WorldIndex(
    val positions: Map<PointId, Position> = emptyMap(),
    val adjacency: Map<PointId, Set<PointId>> = emptyMap(),
    val surfaceBySegment: Map<SegmentId, SurfaceType> = emptyMap(),
    val lengthBySegment: Map<SegmentId, Meters> = emptyMap(),
    val widthBySegment: Map<SegmentId, Meters> = emptyMap(),
    val entitiesByPoint: Map<PointId, Set<EntityRef>> = emptyMap()
)
