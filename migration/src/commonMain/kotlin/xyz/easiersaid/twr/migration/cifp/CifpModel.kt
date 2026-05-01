package xyz.easiersaid.twr.migration.cifp

/**
 * Domain model faithful to X-Plane's CIFP (Coded Instrument Flight Procedures) format.
 * One file per airport, containing SIDs, STARs, approaches, runway data, and precision approach data.
 */
data class CifpAirport(
    val sids: List<CifpProcedure>,
    val stars: List<CifpProcedure>,
    val approaches: List<CifpProcedure>,
    val runways: List<CifpRunway>,
    val precisionApproachData: List<CifpPrecisionData>,
)

/**
 * A named procedure (SID, STAR, or approach) consisting of ordered legs,
 * optionally grouped by transition.
 */
data class CifpProcedure(
    val sectionType: SectionType,
    val routeType: String,
    val name: String,
    val legs: List<CifpLeg>,
)

enum class SectionType(val prefix: String) {
    SID("SID"),
    STAR("STAR"),
    APPROACH("APPCH");

    companion object {
        private val byPrefix = entries.associateBy { it.prefix }
        fun fromPrefix(prefix: String): SectionType? = byPrefix[prefix]
    }
}

/**
 * A single procedure leg — an ARINC 424 path-terminator with fix, constraints, and navaid info.
 */
data class CifpLeg(
    val sequenceNumber: Int,
    val routeType: String,
    val procedureName: String,
    val transition: String,
    val fix: CifpFix?,
    val waypointDescription: String,
    val turnDirection: TurnDirection?,
    val rnp: String,
    val pathTerminator: PathTerminator,
    val recommendedNavaid: CifpFix?,
    val arcRadius: String,
    val theta: String,
    val rho: String,
    val outboundMagneticCourse: String,
    val routeDistance: String,
    val altitudeConstraint: AltitudeConstraint?,
    val speedLimit: String,
    val verticalAngle: String,
    val centerFix: CifpFix?,
    val gnssFmsIndicator: String,
    val speedConstraintType: String,
)

data class CifpFix(
    val id: String,
    val region: String,
    val section: String,
    val subsection: String,
)

enum class TurnDirection(val code: String) {
    LEFT("L"),
    RIGHT("R");

    companion object {
        fun fromCode(code: String): TurnDirection? = when (code.trim()) {
            "L" -> LEFT
            "R" -> RIGHT
            else -> null
        }
    }
}

enum class PathTerminator(val code: String) {
    IF("IF"),   // Initial fix
    TF("TF"),   // Track to fix
    CF("CF"),   // Course to fix
    DF("DF"),   // Direct to fix
    CA("CA"),   // Course to altitude
    FA("FA"),   // Fix to altitude
    FD("FD"),   // Fix to distance
    HA("HA"),   // Holding (racetrack to altitude)
    HF("HF"),   // Holding (racetrack to fix)
    HM("HM"),   // Holding (racetrack to manual)
    RF("RF"),   // Radius to fix (constant radius arc)
    AF("AF"),   // Arc to fix
    VA("VA"),   // Heading to altitude
    VD("VD"),   // Heading to distance
    VI("VI"),   // Heading to intercept
    VM("VM"),   // Heading to manual
    CI("CI"),   // Course to intercept
    CR("CR"),   // Course to radial
    CD("CD"),   // Course to DME distance
    FC("FC"),   // From fix to track/course
    FM("FM"),   // From fix to manual
    PI("PI");   // Procedure turn

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): PathTerminator? = byCode[code.trim()]
    }
}

data class AltitudeConstraint(
    val type: AltitudeConstraintType,
    val altitude1: Int?,
    val altitude2: Int?,
    val transitionAltitude: Int?,
)

enum class AltitudeConstraintType(val code: String) {
    AT(""),
    AT_OR_ABOVE("+"),
    AT_OR_BELOW("-"),
    BETWEEN("B"),
    AT_BY_ATC("J"),
    AT_OR_ABOVE_BY_ATC("H");

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun fromCode(code: String): AltitudeConstraintType? = byCode[code.trim()]
    }
}

/**
 * Runway threshold data from RWY records.
 */
data class CifpRunway(
    val designator: String,
    val thresholdLatitude: String?,
    val thresholdLongitude: String?,
    val elevation: Int?,
    val landingThresholdElevation: Int?,
    val ilsIdentifier: String?,
    val ilsCategory: Int?,
)

/**
 * Precision approach data from PRDAT records.
 */
data class CifpPrecisionData(
    val minimums: List<CifpMinimum>,
)

data class CifpMinimum(
    val type: String,
    val label: String,
)
