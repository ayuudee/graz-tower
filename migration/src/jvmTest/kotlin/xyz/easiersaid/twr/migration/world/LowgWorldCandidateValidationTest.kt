package xyz.easiersaid.twr.migration.world

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AirspaceClass
import xyz.easiersaid.twr.core.world.AirspaceBoundary
import xyz.easiersaid.twr.core.world.AirspaceVolume
import xyz.easiersaid.twr.core.world.AirspaceVolumeType
import xyz.easiersaid.twr.core.world.AltitudeBand
import xyz.easiersaid.twr.core.world.AltitudeBoundary
import xyz.easiersaid.twr.core.world.AltitudeConstraint
import xyz.easiersaid.twr.core.world.Apron
import xyz.easiersaid.twr.core.world.AerodromeAip
import xyz.easiersaid.twr.core.world.ApproachMinimum
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.BoundaryRing
import xyz.easiersaid.twr.core.world.CircuitJoin
import xyz.easiersaid.twr.core.world.CircuitLeg
import xyz.easiersaid.twr.core.world.CircuitProcedure
import xyz.easiersaid.twr.core.world.ContactRequirement
import xyz.easiersaid.twr.core.world.ContactTiming
import xyz.easiersaid.twr.core.world.DeclaredDistances
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Fix
import xyz.easiersaid.twr.core.world.FixType
import xyz.easiersaid.twr.core.world.FlightInformationRegion
import xyz.easiersaid.twr.core.world.GeometrySegmentId
import xyz.easiersaid.twr.core.world.HoldingPoint
import xyz.easiersaid.twr.core.world.HoldingPointType
import xyz.easiersaid.twr.core.world.HoldingPattern
import xyz.easiersaid.twr.core.world.InstrumentApproach
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Meters
import xyz.easiersaid.twr.core.world.MinimumType
import xyz.easiersaid.twr.core.world.MissedApproachProcedure
import xyz.easiersaid.twr.core.world.OperationalSector
import xyz.easiersaid.twr.core.world.OperationalSectorAnchor
import xyz.easiersaid.twr.core.world.OperationalSectorCtrRelation
import xyz.easiersaid.twr.core.world.OperationalSectorKind
import xyz.easiersaid.twr.core.world.Path as WorldPath
import xyz.easiersaid.twr.core.world.PhysicalGeometry
import xyz.easiersaid.twr.core.world.PlateId
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.PublishedMapLabel
import xyz.easiersaid.twr.core.world.PublishedPointReference
import xyz.easiersaid.twr.core.world.PublishedProcedureAdvisories
import xyz.easiersaid.twr.core.world.PublishedProcedureCommunicationFailure
import xyz.easiersaid.twr.core.world.PublishedVfrProcedure
import xyz.easiersaid.twr.core.world.PublishedVfrProcedureKind
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.SegmentGeometry
import xyz.easiersaid.twr.core.world.SpeedConstraint
import xyz.easiersaid.twr.core.world.Sid
import xyz.easiersaid.twr.core.world.Stand
import xyz.easiersaid.twr.core.world.Star
import xyz.easiersaid.twr.core.world.SurfaceType
import xyz.easiersaid.twr.core.world.Taxiway
import xyz.easiersaid.twr.core.world.VfrRoute
import xyz.easiersaid.twr.core.world.VfrRouteAirspaceProfile
import xyz.easiersaid.twr.core.world.VfrRouteAirspaceSegment
import xyz.easiersaid.twr.core.world.Waypoint
import xyz.easiersaid.twr.core.world.WorldValidationCode
import xyz.easiersaid.twr.core.world.asBoundaryRing
import xyz.easiersaid.twr.core.world.validate
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.ApproachId
import xyz.easiersaid.twr.protocol.ApronId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.CircuitDirection
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.DmeDistanceNm
import xyz.easiersaid.twr.protocol.FirId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.HoldingPatternId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Minutes
import xyz.easiersaid.twr.protocol.OperationalSectorId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PublishedVfrProcedureId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SidId
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.StandId
import xyz.easiersaid.twr.protocol.StarId
import xyz.easiersaid.twr.protocol.TaxiwayId
import xyz.easiersaid.twr.protocol.JoinType
import xyz.easiersaid.twr.protocol.TurnDirection
import xyz.easiersaid.twr.protocol.VfrRouteId
import xyz.easiersaid.twr.protocol.ApproachType

class LowgWorldCandidateValidationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Test
    fun writesLowgCurrentCoreValidationReport() {
        val projectRoot = resolveProjectRoot()
        val candidatePath = projectRoot.resolve("cad/airports/rendered/lowg/world-candidate.json")
        assertTrue(Files.exists(candidatePath), "Missing LOWG world candidate at $candidatePath")

        val document = json.decodeFromString<WorldCandidateDocument>(candidatePath.readText())
        assertExpectedLowgAirspaceVolumes(document)
        assertExpectedLowgVfrRouteProfiles(document)
        assertExpectedLowgIfrSubset(document)

        val world = WorldCandidateLoader.toWorld(document)
        val report = world.validate()
        val structuralIssues = report.issues.filter { it.code in structuralCodes }

        val reportDocument = ValidationReportDocument(
            airportCode = document.airportCode,
            airportName = document.airportName,
            projectionStatus = document.projectionStatus,
            sourceManifest = document.sourceManifest,
            sourceEntityBundle = document.sourceEntityBundle,
            forcedAssumptions = document.forcedAssumptions,
            omittedFeatures = document.omittedFeatures,
            projectionGaps = document.projectionGaps,
            issueCount = report.issues.size,
            issueCountsByCode = report.issues.groupingBy { issue -> issue.code.name }.eachCount().toSortedMap(),
            structuralIssueCount = structuralIssues.size,
            structuralIssueCodes = structuralIssues.map { issue -> issue.code.name }.distinct().sorted(),
            issues = report.issues.map { issue ->
                ValidationIssueRecord(
                    code = issue.code.name,
                    message = issue.message,
                )
            },
        )

        val outputPath = projectRoot.resolve("cad/airports/rendered/lowg/world-validation-report.json")
        Files.createDirectories(outputPath.parent)
        outputPath.writeText(json.encodeToString(reportDocument))

        val summary = buildString {
            appendLine("LOWG world candidate validation issues: ${report.issues.size}")
            reportDocument.issueCountsByCode.forEach { (code, count) ->
                appendLine("  $code: $count")
            }
        }
        println(summary.trim())

        assertEquals(
            0,
            report.issues.size,
            buildString {
                appendLine("Unexpected validation issues in LOWG world candidate:")
                report.issues.forEach { issue -> appendLine("- ${issue.code}: ${issue.message}") }
            }.trim(),
        )

        assertTrue(
            structuralIssues.isEmpty(),
            buildString {
                appendLine("Unexpected structural projection issues in LOWG world candidate:")
                structuralIssues.forEach { issue -> appendLine("- ${issue.code}: ${issue.message}") }
            }.trim(),
        )
    }

    private fun assertExpectedLowgAirspaceVolumes(document: WorldCandidateDocument) {
        val expectedVolumeIds = setOf(
            "LO0EF_E",
            "LO585",
            "LO59D_E",
            "LO80C_D",
            "LOCB1_E",
            "LODDA_E",
        )
        assertEquals(
            expectedVolumeIds,
            document.world.airspaceVolumes.keys,
            "LOWG current-core candidate should project the worked low-level runtime airspace subset only.",
        )
    }

    private fun assertExpectedLowgVfrRouteProfiles(document: WorldCandidateDocument) {
        val routes = document.world.vfrRoutes

        val southeast = routes.getValue("vfr_southeast_entry_path")
        assertEquals("IN_VOLUME", southeast.airspaceProfile?.kind)
        assertEquals("LO585", southeast.airspaceProfile?.airspaceVolumeId)

        val southwest = routes.getValue("vfr_southwest_entry_path")
        assertEquals("IN_VOLUME", southwest.airspaceProfile?.kind)
        assertEquals("LO585", southwest.airspaceProfile?.airspaceVolumeId)

        val western = routes.getValue("vfr_western_corridor_path")
        assertEquals("SEGMENTED", western.airspaceProfile?.kind)
        assertEquals(
            listOf("LO585", "LO585", "LO585", "LO0EF_E"),
            western.airspaceProfile?.segments?.map { segment -> segment.airspaceVolumeId },
        )
        assertEquals(5, western.pointIds.size)

        val northeast = routes.getValue("vfr_northeast_entry_path")
        // Since the M5 airport-agnostic airspace projector landed (2026-04-25), the
        // northeast arrival route's profile is computed by point-in-polygon over the
        // worked candidate volumes. GLEISDORF starts in TMA LO59D_E, the route then
        // enters TMA LO80C_D and finally CTR LO585. Two TRANSITION points are inserted
        // automatically.
        assertEquals("SEGMENTED", northeast.airspaceProfile?.kind)
        assertEquals(
            listOf("LO59D_E", "LO80C_D", "LO80C_D", "LO585", "LO585"),
            northeast.airspaceProfile?.segments?.map { segment -> segment.airspaceVolumeId },
        )
        assertEquals(6, northeast.pointIds.size)
    }

    private fun assertExpectedLowgIfrSubset(document: WorldCandidateDocument) {
        val sids = document.world.aerodrome.sids
        assertEquals(21, sids.size, "LOWG current-core candidate should project the full LOWG SID set.")
        assertTrue(sids.containsKey("LOWG_SID_GOTA5G_16C"))
        assertTrue(sids.containsKey("LOWG_SID_ABIR3V_34C"))

        val stars = document.world.aerodrome.stars
        assertEquals(
            8,
            stars.size,
            "LOWG current-core candidate should project the full LOWG STAR set once PIBIP and XIBAR are shared with the selected approach entry-point set.",
        )
        assertTrue(stars.containsKey("LOWG_STAR_ABIR1M"))
        assertTrue(stars.containsKey("LOWG_STAR_GBG1M"))
        assertEquals("XIBAR", stars.getValue("LOWG_STAR_ABIR1M").waypoints.last().name)
        assertEquals("PIBIP", stars.getValue("LOWG_STAR_GBG1M").waypoints.last().name)

        val holdingPatterns = document.world.aerodrome.holdingPatterns
        assertEquals(
            setOf("LOWG_GBG_MISSED_HOLD"),
            holdingPatterns.keys,
            "LOWG current-core candidate should project exactly the shared GBG missed-approach hold.",
        )

        val approaches = document.world.aerodrome.approaches
        assertEquals(
            setOf("LOWG_ILS_34C", "LOWG_RNP_16C", "LOWG_RNP_34C", "LOWG_VOR_16C", "LOWG_VOR_34C"),
            approaches.keys,
            "LOWG current-core candidate should project the first IFR runtime subset only.",
        )
        assertEquals("ILS", approaches.getValue("LOWG_ILS_34C").type)
        assertEquals("RNP", approaches.getValue("LOWG_RNP_16C").type)
        assertEquals("RNP", approaches.getValue("LOWG_RNP_34C").type)
        assertEquals("VOR", approaches.getValue("LOWG_VOR_16C").type)
        assertEquals("VOR", approaches.getValue("LOWG_VOR_34C").type)
        assertEquals("PIBIP", approaches.getValue("LOWG_VOR_34C").waypoints.first().name)
        assertEquals("XIBAR", approaches.getValue("LOWG_ILS_34C").waypoints.first().name)
    }

    private fun resolveProjectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val direct = cwd.resolve("settings.gradle.kts")
        return if (Files.exists(direct)) {
            cwd
        } else {
            cwd.parent ?: cwd
        }
    }


    private companion object {
        val structuralCodes = setOf(
            WorldValidationCode.ORPHAN_GEOMETRY_POINT,
            WorldValidationCode.ORPHAN_GEOMETRY_SEGMENT,
            WorldValidationCode.GEOMETRY_SEGMENT_UNKNOWN_ENDPOINT,
            WorldValidationCode.UNKNOWN_GEOMETRY_POINT_REFERENCE,
            WorldValidationCode.UNKNOWN_GEOMETRY_SEGMENT_REFERENCE,
            WorldValidationCode.POINT_OUTSIDE_AIRSPACE,
            WorldValidationCode.UNKNOWN_FIR,
            WorldValidationCode.FIR_VOLUME_MISMATCH,
            WorldValidationCode.UNKNOWN_AIRSPACE_VOLUME,
            WorldValidationCode.RECIPROCAL_RUNWAYS_DO_NOT_SHARE_SEGMENT,
        )
    }
}

@Serializable
private data class ValidationReportDocument(
    val airportCode: String,
    val airportName: String,
    val projectionStatus: String,
    val sourceManifest: String,
    val sourceEntityBundle: String,
    val forcedAssumptions: List<String>,
    val omittedFeatures: List<String>,
    val projectionGaps: List<String>,
    val issueCount: Int,
    val issueCountsByCode: Map<String, Int>,
    val structuralIssueCount: Int,
    val structuralIssueCodes: List<String>,
    val issues: List<ValidationIssueRecord>,
)

@Serializable
private data class ValidationIssueRecord(
    val code: String,
    val message: String,
)
