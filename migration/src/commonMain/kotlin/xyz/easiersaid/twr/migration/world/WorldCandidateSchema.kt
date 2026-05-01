package xyz.easiersaid.twr.migration.world

import kotlinx.serialization.Serializable
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.RoleName

/**
 * Shared JSON schema for the projected current-core world candidate.
 *
 * The LOWG validator harness consumes these types directly, so the candidate
 * boundary no longer lives as a private DTO tree inside one JVM test.
 */
@Serializable
data class WorldCandidateDocument(
    val airportCode: String,
    val airportName: String,
    val projectionStatus: String,
    val sourceManifest: String,
    val sourceEntityBundle: String,
    val forcedAssumptions: List<String> = emptyList(),
    val omittedFeatures: List<String> = emptyList(),
    val projectionGaps: List<String> = emptyList(),
    val world: CandidateWorld,
)

@Serializable
data class CandidateWorld(
    val geometry: CandidateGeometry,
    val fixes: Map<String, CandidateFix>,
    val vfrRoutes: Map<String, CandidateVfrRoute> = emptyMap(),
    val aerodrome: CandidateAerodrome,
    val airspaceVolumes: Map<String, CandidateAirspaceVolume> = emptyMap(),
    val firs: Map<String, CandidateFir> = emptyMap(),
)

@Serializable
data class CandidateGeometry(
    val points: Map<String, CandidatePoint>,
    val paths: Map<String, CandidatePath>,
)

@Serializable
data class CandidatePoint(
    val id: String,
    val xMeters: Double,
    val yMeters: Double,
)

@Serializable
data class CandidatePath(
    val id: String,
    val pointIds: List<String>,
    val surface: String,
    val widthMeters: Double,
)

@Serializable
data class CandidateFix(
    val id: String,
    val name: String,
    val pointId: String,
    val type: String,
)

@Serializable
data class CandidateVfrRoute(
    val id: String,
    val name: String,
    val pointIds: List<String>,
    val airspaceProfile: CandidateVfrRouteAirspaceProfile? = null,
)

@Serializable
data class CandidateVfrRouteAirspaceProfile(
    val kind: String,
    val airspaceClass: String? = null,
    val airspaceVolumeId: String? = null,
    val segments: List<CandidateVfrRouteAirspaceSegment> = emptyList(),
)

@Serializable
data class CandidateVfrRouteAirspaceSegment(
    val fromPointId: String,
    val toPointId: String,
    val airspaceVolumeId: String,
)

@Serializable
data class CandidateAerodrome(
    val icao: String,
    val name: String,
    val elevationFeet: Int,
    val magneticVariationDegrees: Int,
    val transitionAltitudeFeet: Int,
    val aip: CandidateAerodromeAip = CandidateAerodromeAip(),
    val runways: Map<String, CandidateRunway>,
    val circuits: Map<String, CandidateCircuitProcedure> = emptyMap(),
    val taxiways: Map<String, CandidateTaxiway>,
    val stands: Map<String, CandidateStand>,
    val aprons: Map<String, CandidateApron>,
    val sids: Map<String, CandidateSid> = emptyMap(),
    val stars: Map<String, CandidateStar> = emptyMap(),
    val approaches: Map<String, CandidateInstrumentApproach> = emptyMap(),
    val holdingPatterns: Map<String, CandidateHoldingPattern> = emptyMap(),
    /**
     * Controller roles published by this aerodrome (TOWER, GROUND, APPROACH,
     * AREA_CONTROL, AFIS, CLEARANCE_DELIVERY, DEPARTURE).
     *
     * Pass 6 (D-AUDIT.12 closure): hand-authored from
     * `structured-airport-package.json`'s frequency block. Defaults to
     * `emptyMap()` so existing JSONs parse, but [LoaderRolesPopulatedTest]
     * asserts every rendered aerodrome publishes ≥1 role.
     */
    val roles: Map<RoleName, CandidateAerodromeRole> = emptyMap(),
)

/**
 * One controller role published by a [CandidateAerodrome].
 *
 * Per Pass 6 plan §A.iii, [name] and [frequencyMhz] are typed at parse time
 * via [RoleNameSerializer] and [FrequencySerializer] — invalid role tokens or
 * out-of-range frequencies fail with [kotlinx.serialization.SerializationException]
 * at JSON decode, never reaching the loader.
 */
@Serializable
data class CandidateAerodromeRole(
    @Serializable(with = RoleNameSerializer::class) val name: RoleName,
    @Serializable(with = FrequencySerializer::class) val frequencyMhz: Frequency,
    /**
     * Authority tokens. Pass 6 only recognises `"PLACEHOLDER"`
     * (see [LoaderAuthority.PLACEHOLDER_TOKEN]); D-AUDIT.11 will add real
     * authority strings. The field is added now (defaulted to
     * `["PLACEHOLDER"]`) so D-AUDIT.11 is field POPULATION, not schema
     * migration.
     */
    val authorities: List<String> = listOf(LoaderAuthority.PLACEHOLDER_TOKEN),
)

@Serializable
data class CandidateAerodromeAip(
    val operationalSectors: Map<String, CandidateOperationalSector> = emptyMap(),
    val publishedVfrProcedures: Map<String, CandidatePublishedVfrProcedure> = emptyMap(),
)

@Serializable
data class CandidateRunway(
    val id: String,
    val pathId: String,
    val thresholdPointId: String,
    val declaredDistances: CandidateDeclaredDistances,
)

@Serializable
data class CandidateDeclaredDistances(
    val toraMeters: Int,
    val todaMeters: Int,
    val asdaMeters: Int,
    val ldaMeters: Int,
)

@Serializable
data class CandidateCircuitProcedure(
    val id: String,
    val runwayId: String,
    val direction: String,
    val legs: List<CandidateCircuitLeg>,
    val altitudeFeet: Int,
    val reportingPoints: Map<String, String> = emptyMap(),
    val joinProcedures: List<CandidateCircuitJoin> = emptyList(),
    val goAroundPathId: String,
    val sourceLoop: String? = null,
)

@Serializable
data class CandidateCircuitLeg(
    val name: String,
    val pathId: String,
)

@Serializable
data class CandidateCircuitJoin(
    val type: String,
    val entryPointId: String,
    val entryPathId: String? = null,
)

@Serializable
data class CandidateTaxiway(
    val id: String,
    val name: String,
    val pathId: String,
    val bidirectional: Boolean = true,
    val holdingPoints: List<CandidateHoldingPoint> = emptyList(),
)

@Serializable
data class CandidateHoldingPoint(
    val pointId: String,
    val name: String? = null,
    val type: String? = null,
    val runwayId: String? = null,
)

@Serializable
data class CandidateStand(
    val id: String,
    val name: String,
    val pointId: String,
)

@Serializable
data class CandidateApron(
    val id: String,
    val name: String,
    val pathIds: List<String>,
    val standIds: List<String> = emptyList(),
)

@Serializable
data class CandidateSid(
    val id: String,
    val name: String,
    val runwayId: String,
    val waypoints: List<CandidateWaypoint>,
    val transitions: Map<String, List<CandidateWaypoint>> = emptyMap(),
)

@Serializable
data class CandidateStar(
    val id: String,
    val name: String,
    val waypoints: List<CandidateWaypoint>,
    val transitions: Map<String, List<CandidateWaypoint>> = emptyMap(),
)

@Serializable
data class CandidateInstrumentApproach(
    val id: String,
    val name: String,
    val type: String,
    val runwayId: String,
    val waypoints: List<CandidateWaypoint>,
    val minimumAltitude: CandidateApproachMinimum,
    val missedApproach: CandidateMissedApproachProcedure,
)

@Serializable
data class CandidateApproachMinimum(
    val type: String,
    val altitudeFeet: Int,
    val heightFeet: Int? = null,
)

@Serializable
data class CandidateMissedApproachProcedure(
    val waypoints: List<CandidateWaypoint>,
    val holdAtId: String,
)

@Serializable
data class CandidateHoldingPattern(
    val id: String,
    val fixId: String,
    val inboundCourseDegrees: Double,
    val turnDirection: String,
    val loopPathId: String,
    val legTimeMinutes: Int? = null,
    val legDistanceNm: Double? = null,
    val maxSpeedKnots: Int? = null,
    val altitudeFeet: Int,
    val stackSeparationFeet: Int? = null,
)

@Serializable
data class CandidateWaypoint(
    val pointId: String,
    val name: String? = null,
    val altitudeConstraint: CandidateWaypointAltitudeConstraint? = null,
    val speedConstraint: CandidateWaypointSpeedConstraint? = null,
)

@Serializable
data class CandidateWaypointAltitudeConstraint(
    val kind: String,
    val valueFeet: Int? = null,
    val minimumFeet: Int? = null,
    val maximumFeet: Int? = null,
)

@Serializable
data class CandidateWaypointSpeedConstraint(
    val kind: String,
    val valueKnots: Int? = null,
    val minimumKnots: Int? = null,
    val maximumKnots: Int? = null,
)

@Serializable
data class CandidateOperationalSector(
    val id: String,
    val name: String,
    val kind: String,
    val boundaryPathIds: List<String>,
    val anchor: CandidateOperationalSectorAnchor? = null,
    val entryExitPointIds: List<String> = emptyList(),
    val altitudeBand: CandidateAltitudeBand? = null,
    val contactRequirement: CandidateContactRequirement? = null,
    val relationToCtr: String? = null,
    val associatedProcedureIds: List<String> = emptyList(),
    val note: String? = null,
    val specialProcedureNote: String? = null,
)

@Serializable
data class CandidatePublishedVfrProcedure(
    val id: String,
    val plateId: String,
    val kind: String,
    val publishedSequence: List<CandidatePublishedPointReference> = emptyList(),
    val associatedVfrRouteIds: List<String> = emptyList(),
    val associatedOperationalSectorIds: List<String> = emptyList(),
    val associatedCircuitIds: List<String> = emptyList(),
    val contactRequirement: CandidateContactRequirement? = null,
    val advisories: CandidatePublishedProcedureAdvisories? = null,
    val mapLabels: List<CandidatePublishedMapLabel> = emptyList(),
    val terminatesAt: CandidatePublishedPointReference? = null,
    val holdAt: CandidatePublishedPointReference? = null,
    val communicationFailure: CandidatePublishedProcedureCommunicationFailure? = null,
    val departureRunwayIds: List<String> = emptyList(),
    val applicableRunwayIds: List<String> = emptyList(),
)

@Serializable
data class CandidatePublishedPointReference(
    val kind: String,
    val reference: String,
    val pointId: String? = null,
)

@Serializable
data class CandidatePublishedMapLabel(
    val label: String,
    val location: CandidatePublishedPointReference,
)

@Serializable
data class CandidateAltitudeBand(
    val lower: CandidateAltitudeBoundary,
    val upper: CandidateAltitudeBoundary? = null,
)

@Serializable
data class CandidateAltitudeBoundary(
    val kind: String,
    val levelType: String? = null,
    val value: Int? = null,
)

@Serializable
data class CandidateContactRequirement(
    val role: String,
    val timing: CandidateContactTiming,
)

@Serializable
data class CandidateContactTiming(
    val kind: String,
    val pointId: String? = null,
    val distanceNm: Double? = null,
    val levelType: String? = null,
    val value: Int? = null,
)

@Serializable
data class CandidateOperationalSectorAnchor(
    val kind: String,
    val pointId: String? = null,
)

@Serializable
data class CandidatePublishedProcedureAdvisories(
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
    val general: String? = null,
)

@Serializable
data class CandidatePublishedProcedureCommunicationFailure(
    val beforeContactEstablished: String? = null,
    val afterContactEstablishedExitSequence: List<CandidatePublishedPointReference> = emptyList(),
    val note: String? = null,
)

@Serializable
data class CandidateAirspaceVolume(
    val id: String,
    val name: String,
    val type: String,
    val airspaceClass: String,
    val altitudeBand: CandidateAltitudeBand,
    val memberPointIds: List<String> = emptyList(),
    val firId: String,
    val boundaryPathIds: List<String> = emptyList(),
    val projectionStatus: String? = null,
    val note: String? = null,
)

@Serializable
data class CandidateFir(
    val id: String,
    val name: String,
    val volumeIds: List<String> = emptyList(),
    val projectionStatus: String? = null,
)
