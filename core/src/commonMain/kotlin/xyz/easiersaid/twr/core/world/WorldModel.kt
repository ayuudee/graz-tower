package xyz.easiersaid.twr.core.world

import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.ApronId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.AirwayId
import xyz.easiersaid.twr.protocol.ApproachId
import xyz.easiersaid.twr.protocol.AuthorityEntityType
import xyz.easiersaid.twr.protocol.AuthorityOperation
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FirId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.HoldingPatternId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.OperationalSectorId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PublishedVfrProcedureId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SidId
import xyz.easiersaid.twr.protocol.StandId
import xyz.easiersaid.twr.protocol.StarId
import xyz.easiersaid.twr.protocol.TaxiwayId
import xyz.easiersaid.twr.protocol.VfrRouteId

data class Meters(val value: Double) {
    init {
        require(value >= 0.0) { "Meters must be >= 0" }
    }

    companion object {
        /**
         * Convert nautical miles to metres using the international NM
         * (1 NM = 1852 m exactly). Self-documents doctrine numbers like
         * `Meters.fromNauticalMiles(12)` (= 22 224 m) at the call site.
         */
        fun fromNauticalMiles(nm: Int): Meters = Meters(nm * 1852.0)
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

@ConsistentCopyVisibility
data class GeometrySegmentId private constructor(
    val first: PointId,
    val second: PointId
) {
    init {
        require(first != second) { "Geometry segments must connect two distinct points" }
    }

    companion object {
        fun between(a: PointId, b: PointId): GeometrySegmentId {
            require(a != b) { "Geometry segments must connect two distinct points" }
            return if (a.value <= b.value) {
                GeometrySegmentId(a, b)
            } else {
                GeometrySegmentId(b, a)
            }
        }
    }

    fun directedIds(): Set<SegmentId> =
        setOf(
            SegmentId(first, second),
            SegmentId(second, first)
        )

    fun describe(): String =
        "${first.value}<->${second.value}"
}

sealed interface SegmentShape {
    data object Straight : SegmentShape
    data class Arc(val radius: Meters) : SegmentShape
}

enum class SurfaceType {
    GROUND,
    RUNWAY,
    SKY
}

data class SegmentGeometry(
    val length: Meters,
    val width: Meters,
    val shape: SegmentShape = SegmentShape.Straight,
    val surface: SurfaceType
) {
    init {
        require(length.value > 0.0) { "Segment length must be > 0" }
        require(width.value > 0.0) { "Segment width must be > 0" }
    }
}

data class PhysicalGeometry(
    val points: Map<PointId, Position> = emptyMap(),
    val segments: Map<GeometrySegmentId, SegmentGeometry> = emptyMap()
)

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

enum class FixType {
    WAYPOINT,
    VOR,
    NDB,
    MARKER
}

data class Fix(
    val id: FixId,
    val point: PointId,
    val name: String,
    val type: FixType = FixType.WAYPOINT
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
    val specialInstructions: List<String> = emptyList(),
    val operationalSectors: Map<OperationalSectorId, OperationalSector> = emptyMap(),
    val publishedVfrProcedures: Map<PublishedVfrProcedureId, PublishedVfrProcedure> = emptyMap()
)

/**
 * Geographic reference point in WGS-84 lat/lon, in degrees.
 *
 * Used by `WorldCandidateLoader.mergeAviationWorlds` to reproject each
 * airport's local Cartesian frame into a single shared frame at parse
 * time. Without this, multi-aerodrome merges produce overlapping
 * coordinate spaces (each airport's xMeters/yMeters is relative to
 * its own reference). G1-DEF-11.
 */
@ConsistentCopyVisibility
data class LatLon private constructor(val latitude: Double, val longitude: Double) {
    init {
        // Defensive backstop — every construction goes via [invoke] or
        // [unsafe], both of which range-check.
        require(latitude in -90.0..90.0) { "Latitude must be in [-90, 90]: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be in [-180, 180]: $longitude" }
    }

    companion object {
        /**
         * Smart constructor — returns a typed error rather than throwing
         * when lat/lon is out of range. Use this at deserialization
         * boundaries (G1-DEF-17 will plumb lat/lon through the JSON
         * `world-candidate` schema; throwing from a deserializer is the
         * wrong shape).
         */
        operator fun invoke(latitude: Double, longitude: Double): arrow.core.Either<String, LatLon> =
            when {
                latitude !in -90.0..90.0 -> arrow.core.Either.Left("Latitude must be in [-90, 90]: $latitude")
                longitude !in -180.0..180.0 -> arrow.core.Either.Left("Longitude must be in [-180, 180]: $longitude")
                else -> arrow.core.Either.Right(LatLon(latitude, longitude))
            }

        /**
         * Throwing variant — for trusted call sites (compile-time literals).
         */
        fun unsafe(latitude: Double, longitude: Double): LatLon =
            invoke(latitude, longitude).fold({ error(it) }, { it })
    }
}

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
    val aprons: Map<ApronId, Apron> = emptyMap(),
    val circuits: Map<CircuitProcedureId, CircuitProcedure> = emptyMap(),
    val sids: Map<SidId, Sid> = emptyMap(),
    val stars: Map<StarId, Star> = emptyMap(),
    val approaches: Map<ApproachId, InstrumentApproach> = emptyMap(),
    val holdingPatterns: Map<HoldingPatternId, HoldingPattern> = emptyMap(),
    /**
     * Geographic origin of this aerodrome's local Cartesian frame
     * (xMeters/yMeters in [PhysicalGeometry.points]). Null if the
     * runtime hasn't been told where this airport is in the world —
     * which is the legacy single-airport mode where the question
     * doesn't arise.
     *
     * Required for [WorldCandidateLoader.mergeAviationWorlds] to
     * reproject airport-local geometries into a shared frame
     * (G1-DEF-11).
     */
    val referencePoint: LatLon? = null,
    /**
     * Circular approximation of this aerodrome's CTR boundary, used by
     * the controller's `OutsideAerodromeRadius` rule to decide when an
     * outbound aircraft has crossed the boundary and is eligible for
     * radar-service termination / cross-aerodrome release.
     *
     * **This is a single-radius stand-in for a polygonal boundary.**
     * Real CTR shapes are published as polygons in AIP AD 2.17 and
     * routinely vary several NM in extent between approach axes (LOWG's
     * polygon ranges 6.7–16.25 NM from ARP, for example). The circular
     * approximation is anisotropic-wrong: short on the approach axis,
     * generous abeam. Polygon containment is the planned replacement —
     * see `D-AUDIT-polygon-ctr` (FM/Lean territory, fn-4 lineage).
     *
     * **Per-aerodrome authoring.** Loaded from the JSON schema field
     * `ctrApproximationRadiusNauticalMiles` when present (with a
     * sub-floor `require(n >= CTR_FLOOR_NAUTICAL_MILES)` rejection in
     * the loader), otherwise defaulted to
     * [Doctrine.IcaoAnnex11.CTR_FLOOR_5NM]. The default is a
     * regulatory floor — at most controlled aerodromes the actual
     * polygon extends further on the approach axis, so authoring a
     * tighter per-aerodrome value from AIP polygon data (rounded up,
     * with proxy-offset margin) is the doctrinally-correct shape.
     */
    val ctrApproximationRadius: Meters = Doctrine.IcaoAnnex11.CTR_FLOOR_5NM,
)

data class AviationWorld(
    val geometry: PhysicalGeometry = PhysicalGeometry(),
    val fixes: Map<FixId, Fix> = emptyMap(),
    val aerodromes: Map<AerodromeId, Aerodrome> = emptyMap(),
    val airways: Map<AirwayId, Airway> = emptyMap(),
    val vfrRoutes: Map<VfrRouteId, VfrRoute> = emptyMap(),
    val airspace: Map<AirspaceVolumeId, AirspaceVolume> = emptyMap(),
    val firs: Map<FirId, FlightInformationRegion> = emptyMap()
)

sealed interface EntityRef {
    data class RunwayRef(val id: RunwayId) : EntityRef
    data class TaxiwayRef(val id: TaxiwayId) : EntityRef
    data class StandRef(val id: StandId) : EntityRef
    data class ApronRef(val id: ApronId) : EntityRef
    data class FixRef(val id: FixId) : EntityRef
    data class CircuitProcedureRef(val id: CircuitProcedureId) : EntityRef
    data class HoldingPatternRef(val id: HoldingPatternId) : EntityRef
    data class SidRef(val id: SidId) : EntityRef
    data class StarRef(val id: StarId) : EntityRef
    data class ApproachRef(val id: ApproachId) : EntityRef
    data class AirwayRef(val id: AirwayId) : EntityRef
    data class VfrRouteRef(val id: VfrRouteId) : EntityRef
    data class AirspaceVolumeRef(val id: AirspaceVolumeId) : EntityRef
    data class OperationalSectorRef(val id: OperationalSectorId) : EntityRef
    data class PublishedVfrProcedureRef(val id: PublishedVfrProcedureId) : EntityRef
}

data class WorldIndex(
    val positions: Map<PointId, Position> = emptyMap(),
    val adjacency: Map<PointId, Set<PointId>> = emptyMap(),
    val surfaceBySegment: Map<SegmentId, SurfaceType> = emptyMap(),
    val lengthBySegment: Map<SegmentId, Meters> = emptyMap(),
    val widthBySegment: Map<SegmentId, Meters> = emptyMap(),
    val entitiesByPoint: Map<PointId, Set<EntityRef>> = emptyMap(),
    val holdingPointsByRunway: Map<RunwayId, Set<PointId>> = emptyMap(),
    val circuitLegsByPoint: Map<PointId, Set<LegName>> = emptyMap(),
    /** Runway threshold point, keyed by runway. Used for arrival distance computation. */
    val thresholdByRunway: Map<RunwayId, PointId> = emptyMap(),
) {
    /**
     * Precomputed reverse of [holdingPointsByRunway]. O(1) lookup, computed
     * once per index. Co-located with the forward map so the pair stays
     * consistent.
     */
    private val runwaysByHoldingPoint: Map<PointId, Set<RunwayId>> =
        holdingPointsByRunway.entries
            .flatMap { (rwy, points) -> points.map { it to rwy } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, runways) -> runways.toSet() }

    /**
     * Reverse lookup: given a holding-point [PointId], which runways does it
     * serve? Returns the empty set for non-holding-point points.
     *
     * Multi-runway holding points are real (intersecting taxiways at parallel-
     * runway airports). The reverse map is `Set<RunwayId>` rather than a
     * single runway so the call site can disambiguate explicitly. The pilot
     * (post pilot-firewall) reads this when extracting the runway from a
     * `TaxiTo` instruction's destination; the precedence rule is documented
     * in `PilotMission.processInstruction`.
     *
     * The cleaner long-term shape is for `TaxiTo` to carry an explicit
     * `runway: RunwayId` field, removing the inference entirely. Recorded
     * as deferment **D-PF.6**.
     */
    fun runwaysForHoldingPoint(point: PointId): Set<RunwayId> =
        runwaysByHoldingPoint[point].orEmpty()
}
