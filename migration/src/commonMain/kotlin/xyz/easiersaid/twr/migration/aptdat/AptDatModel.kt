package xyz.easiersaid.twr.migration.aptdat

import xyz.easiersaid.twr.migration.common.GeoCoordinate

/**
 * Domain model faithful to the X-Plane apt.dat file format (WorldEditor 2.x).
 * Models what is in the file, not our ATC domain.
 */
data class AptDatAirport(
    val icao: String,
    val name: String,
    val elevationFeet: Int,
    val hasTower: Boolean,
    val metadata: Map<String, String>,
    val landRunways: List<LandRunway>,
    val waterRunways: List<WaterRunway>,
    val helipads: List<Helipad>,
    val pavements: List<Pavement>,
    val linearFeatures: List<LinearFeature>,
    val boundary: Boundary?,
    val taxiNetwork: TaxiNetwork,
    val stands: List<Stand>,
    val startupLocations: List<StartupLocation>,
    val taxiSigns: List<TaxiSign>,
    val lightingObjects: List<LightingObject>,
    val towerViewpoint: TowerViewpoint?,
    val atcFlows: List<AtcFlow>,
    val frequencies: List<AtcFrequency>,
    val serviceVehicleLocations: List<ServiceVehicleLocation>,
    val serviceVehicleDestinations: List<ServiceVehicleDestination>,
)

// -- Record 100: Land runway --

data class LandRunway(
    val width: Double,
    val surface: SurfaceType,
    val shoulderSurface: Int,
    val smoothness: Double,
    val centreLights: Int,
    val edgeLights: Int,
    val distanceRemainingSigns: Int,
    val end1: RunwayEnd,
    val end2: RunwayEnd,
)

data class RunwayEnd(
    val designator: String,
    val position: GeoCoordinate,
    val displacedThresholdMeters: Double,
    val overrunMeters: Double,
    val markings: Int,
    val approachLighting: Int,
    val touchdownZoneLighting: Int,
    val reilLighting: Int,
)

enum class SurfaceType(val code: Int) {
    ASPHALT(1),
    CONCRETE(2),
    TURF(3),
    DIRT(4),
    GRAVEL(5),
    DRY_LAKEBED(12),
    WATER(13),
    SNOW(14),
    TRANSPARENT(15);

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: Int): SurfaceType? = byCode[code]
    }
}

// -- Record 101: Water runway --

data class WaterRunway(
    val width: Double,
    val end1Designator: String,
    val end1Position: GeoCoordinate,
    val end2Designator: String,
    val end2Position: GeoCoordinate,
)

// -- Record 102: Helipad --

data class Helipad(
    val designator: String,
    val position: GeoCoordinate,
    val heading: Double,
    val length: Double,
    val width: Double,
    val surface: SurfaceType?,
    val markings: Int,
    val shoulderSurface: Int,
    val smoothness: Double,
    val edgeLights: Int,
)

// -- Records 110-116: Pavement polygons --

sealed interface BezierNode {
    val position: GeoCoordinate

    /** Record 111: plain vertex */
    data class Plain(override val position: GeoCoordinate) : BezierNode

    /** Record 112: bezier vertex with control point */
    data class Bezier(
        override val position: GeoCoordinate,
        val controlPoint: GeoCoordinate,
    ) : BezierNode

    /** Record 113: plain close (ring closure) */
    data class ClosePlain(override val position: GeoCoordinate) : BezierNode

    /** Record 114: bezier close */
    data class CloseBezier(
        override val position: GeoCoordinate,
        val controlPoint: GeoCoordinate,
    ) : BezierNode

    /** Record 115: plain end (last vertex of a winding) */
    data class EndPlain(override val position: GeoCoordinate) : BezierNode

    /** Record 116: bezier end */
    data class EndBezier(
        override val position: GeoCoordinate,
        val controlPoint: GeoCoordinate,
    ) : BezierNode
}

data class Pavement(
    val surface: SurfaceType?,
    val smoothness: Double,
    val heading: Double,
    val name: String,
    val nodes: List<BezierNode>,
)

data class LinearFeature(
    val name: String,
    val nodes: List<BezierNode>,
)

data class Boundary(
    val name: String,
    val nodes: List<BezierNode>,
)

// -- Records 1201/1202/1204/1206: Taxi network --

data class TaxiNetwork(
    val nodes: List<TaxiNode>,
    val edges: List<TaxiEdge>,
    val vehicleEdges: List<VehicleEdge>,
)

data class TaxiNode(
    val id: Int,
    val position: GeoCoordinate,
    val usage: TaxiNodeUsage,
    val name: String,
)

enum class TaxiNodeUsage(val code: String) {
    INIT("init"),
    DEST("dest"),
    BOTH("both"),
    JUNC("junc");

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): TaxiNodeUsage? = byCode[code]
    }
}

data class TaxiEdge(
    val node1Id: Int,
    val node2Id: Int,
    val direction: EdgeDirection,
    val type: EdgeType,
    val name: String,
    val activeZones: List<ActiveZone>,
)

enum class EdgeDirection(val code: String) {
    TWOWAY("twoway"),
    ONEWAY("oneway");

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): EdgeDirection? = byCode[code]
    }
}

enum class EdgeType(val code: String) {
    TAXIWAY("taxiway"),
    RUNWAY("runway");

    companion object {
        fun fromCode(code: String): EdgeType? = when {
            code == "runway" -> RUNWAY
            code.startsWith("taxiway") -> TAXIWAY
            else -> null
        }
    }
}

data class ActiveZone(
    val type: ActiveZoneType,
    val runwayDesignators: List<String>,
)

enum class ActiveZoneType(val code: String) {
    DEPARTURE("departure"),
    ARRIVAL("arrival"),
    ILS("ils");

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): ActiveZoneType? = byCode[code]
    }
}

data class VehicleEdge(
    val node1Id: Int,
    val node2Id: Int,
    val direction: EdgeDirection,
    val name: String,
)

// -- Record 1300: Stand/parking --

data class Stand(
    val position: GeoCoordinate,
    val heading: Double,
    val type: StandType,
    val aircraftTypes: String,
    val name: String,
)

enum class StandType(val code: String) {
    GATE("gate"),
    TIE_DOWN("tie_down"),
    HANGAR("hangar"),
    MISC("misc");

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): StandType? = byCode[code]
    }
}

// -- Record 1301: Startup location metadata --

data class StartupLocation(
    val name: String,
    val operationType: String,
)

// -- Record 20: Taxi signs --

data class TaxiSign(
    val position: GeoCoordinate,
    val heading: Double,
    val reserved: Int,
    val size: Int,
    val text: String,
)

// -- Record 21: Lighting objects --

data class LightingObject(
    val position: GeoCoordinate,
    val lightType: Int,
    val heading: Double,
    val angle: Double,
    val associatedRunway: String,
    val description: String,
)

// -- Record 14: Tower viewpoint --

data class TowerViewpoint(
    val position: GeoCoordinate,
    val heightFeetAgl: Double,
    val drawBuilding: Int,
    val name: String,
)

// -- Records 1050/1054/1055: Frequencies --

data class AtcFrequency(
    val type: AtcFrequencyType,
    val frequencyKhz: Int,
    val name: String,
)

enum class AtcFrequencyType(val recordCode: String) {
    ATIS("1050"),
    UNICOM("1051"),
    CLEARANCE("1052"),
    GROUND("1053"),
    TOWER("1054"),
    APPROACH("1055"),
    DEPARTURE("1056");

    companion object {
        private val byRecordCode = entries.associateBy { it.recordCode }
        fun fromRecordCode(code: String): AtcFrequencyType? = byRecordCode[code]
    }
}

// -- Records 1000-1003, 1101, 1110: ATC flow --

data class AtcFlow(
    val name: String,
    val windRule: WindRule?,
    val ceilingRule: CeilingRule?,
    val visibilityRule: VisibilityRule?,
    val patternRunway: PatternRunway?,
    val runwayAssignments: List<RunwayAssignment>,
)

data class WindRule(
    val icao: String,
    val minHeading: Int,
    val maxHeading: Int,
    val maxSpeedKnots: Int,
)

data class CeilingRule(
    val icao: String,
    val ceilingFeetAgl: Int,
)

data class VisibilityRule(
    val icao: String,
    val visibilityStatuteMiles: Double,
)

data class PatternRunway(
    val runwayDesignator: String,
    val direction: String,
)

data class RunwayAssignment(
    val runwayDesignator: String,
    val frequencyKhz: Int,
    val operations: String,
    val aircraftTypes: String,
    val name: String,
)

// -- Records 1400/1401: Service vehicles --

data class ServiceVehicleLocation(
    val position: GeoCoordinate,
    val heading: Double,
    val vehicleType: String,
    val reserved: Int,
    val name: String,
)

data class ServiceVehicleDestination(
    val position: GeoCoordinate,
    val heading: Double,
    val vehicleType: String,
    val name: String,
)
