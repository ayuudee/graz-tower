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

class LjmbWorldCandidateValidationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    @Test
    fun writesLjmbCurrentCoreValidationReport() {
        val projectRoot = resolveProjectRoot()
        val candidatePath = projectRoot.resolve("cad/airports/rendered/ljmb/world-candidate.json")
        assertTrue(Files.exists(candidatePath), "Missing LJMB world candidate at $candidatePath")

        val document = json.decodeFromString<WorldCandidateDocument>(candidatePath.readText())
        assertExpectedLjmbAirspaceVolumes(document)
        assertExpectedLjmbPublishedVfrProcedures(document)
        assertExpectedLjmbCurrentCoreSubset(document)

        val world = WorldCandidateLoader.toWorld(document)
        val report = world.validate()
        val structuralIssues = report.issues.filter { it.code in structuralCodes }

        val reportDocument = LjmbValidationReportDocument(
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
                LjmbValidationIssueRecord(
                    code = issue.code.name,
                    message = issue.message,
                )
            },
        )

        val outputPath = projectRoot.resolve("cad/airports/rendered/ljmb/world-validation-report.json")
        Files.createDirectories(outputPath.parent)
        outputPath.writeText(json.encodeToString(reportDocument))

        val summary = buildString {
            appendLine("LJMB world candidate validation issues: ${report.issues.size}")
            reportDocument.issueCountsByCode.forEach { (code, count) ->
                appendLine("  $code: $count")
            }
        }
        println(summary.trim())

        assertEquals(
            0,
            report.issues.size,
            buildString {
                appendLine("Unexpected validation issues in LJMB world candidate:")
                report.issues.forEach { issue -> appendLine("- ${issue.code}: ${issue.message}") }
            }.trim(),
        )

        assertTrue(
            structuralIssues.isEmpty(),
            buildString {
                appendLine("Unexpected structural projection issues in LJMB world candidate:")
                structuralIssues.forEach { issue -> appendLine("- ${issue.code}: ${issue.message}") }
            }.trim(),
        )
    }

    private fun assertExpectedLjmbAirspaceVolumes(document: WorldCandidateDocument) {
        val expectedVolumeIds = setOf(
            "LJLA_OPEN_FIR_G",
            "LJMB_OPENAIR_CTR_MARIBOR",
            "LJMB_OPENAIR_TMA_DOLSKO_1",
            "LJMB_OPENAIR_TMA_DOLSKO_1_2",
            "LJMB_OPENAIR_TMA_MARIBOR_1",
            "LJMB_OPENAIR_TMA_MARIBOR_2",
            "LJMB_OPENAIR_TMA_MARIBOR_2_2",
            "LJMB_OPENAIR_TMA_MURA",
        )
        assertEquals(
            expectedVolumeIds,
            document.world.airspaceVolumes.keys,
            "LJMB current-core candidate should project the worked OpenAir-backed runtime low-level airspace subset plus the explicit open-FIR fallback volume.",
        )
    }

    private fun assertExpectedLjmbPublishedVfrProcedures(document: WorldCandidateDocument) {
        val publishedProcedures = document.world.aerodrome.aip.publishedVfrProcedures
        assertEquals(
            setOf("ljmb_ctr_entry_general", "ljmb_tma_entry_general"),
            publishedProcedures.keys,
            "LJMB current-core candidate should currently expose only the first Jepp-derived VFR publication layer.",
        )

        val tmaEntry = publishedProcedures.getValue("ljmb_tma_entry_general")
        val petovLabel = tmaEntry.mapLabels.firstOrNull { label -> label.label == "PETOV" }
        assertTrue(
            petovLabel != null,
            "LJMB TMA entry publication should list PETOV among its map waypoint labels.",
        )
        assertEquals(
            "FIX",
            petovLabel?.location?.kind,
            "PETOV should now resolve to a FIX reference via the X-Plane earth_fix.dat cache rather than remain a LITERAL placeholder.",
        )
    }

    private fun assertExpectedLjmbCurrentCoreSubset(document: WorldCandidateDocument) {
        assertEquals(
            setOf("ljmb_mn_corridor_inbound", "ljmb_mw_corridor_inbound"),
            document.world.vfrRoutes.keys,
            "LJMB current-core candidate should project the two Jepp 19-1 corridor routes (MN1-MN2, MW1-LAPNA). " +
                "Further VFR route authoring remains deferred until explicit circuit-join anchors are added.",
        )
        assertExpectedLjmbCircuits(document)
        assertExpectedLjmbIfrSids(document)
        assertEquals(
            emptySet(),
            document.world.aerodrome.stars.keys,
            "LJMB runtime STAR projection is deferred until STAR terminal fixes are shared with projected approach entry points.",
        )
        assertEquals(
            emptySet(),
            document.world.aerodrome.approaches.keys,
            "LJMB runtime approach projection is deferred pending missed-approach hold-loop compilation (LOWG-specific today).",
        )
        assertEquals(
            emptySet(),
            document.world.aerodrome.holdingPatterns.keys,
            "LJMB runtime holding-pattern projection is deferred pending the same hold-loop compilation.",
        )
        assertEquals(39, document.world.aerodrome.taxiways.size)
        assertEquals(7, document.world.aerodrome.stands.size)
        val petov = document.world.fixes["PETOV"]
        assertTrue(
            petov != null,
            "PETOV should now resolve from the X-Plane earth_fix.dat cache rather than remain a literal unresolved VFR point",
        )
    }

    /**
     * Validates the LJMB runtime SID subset against the current CIFP source of truth.
     *
     * Decomposed into two semantically distinct checks (per dbt-style accepted-values + not-null
     * split):
     *  - [assertSidsAreStructurallyValid] — every actual SID validates structurally
     *    (key matches `sid.id`; a `_PATH` entry exists in `document.world.geometry.paths`).
     *    No hard-coded set. New SIDs appearing here are NOT a failure — they may come from
     *    a CIFP cycle upgrade or `.plan` M1 unblocking fixless-leg SIDs.
     *  - [assertSidsCoverPromisedSet] — asserts the subset of SIDs we promise to keep
     *    supporting is present (`missing = expectedCovered - actual` is empty).
     *    Does NOT assert `unexpected = actual - expectedCovered` is empty — coverage is a
     *    subset relation, not an inventory snapshot.
     *
     * Cycle id source: `data/cifp/LJMB.dat` carries no AIRAC/CYCLE/HDR header marker, so the
     * cycle id falls back to the file's git blob SHA. Failure messages hard-code the result so
     * the next reconciler immediately knows what cycle the test was authored against.
     */
    private fun assertExpectedLjmbIfrSids(document: WorldCandidateDocument) {
        assertSidsAreStructurallyValid(document)
        assertSidsCoverPromisedSet(document)
    }

    private fun assertSidsAreStructurallyValid(document: WorldCandidateDocument) {
        val sids = document.world.aerodrome.sids
        val paths = document.world.geometry.paths
        for ((key, sid) in sids) {
            assertEquals(
                key,
                sid.id,
                "LJMB SID structural validation: map key '$key' must equal the value's `sid.id` " +
                    "field ('${sid.id}'). A mismatch indicates upstream serialization or " +
                    "renaming drift in `bin/airport_world_candidate.py`.",
            )
            val expectedPathId = "${sid.id}_PATH"
            assertTrue(
                paths.containsKey(expectedPathId),
                "LJMB SID structural validation: SID '${sid.id}' must have a matching " +
                    "'$expectedPathId' entry in `document.world.geometry.paths`. " +
                    "Missing path indicates the candidate generator's SID projection " +
                    "(bin/airport_world_candidate.py:808-857) failed to emit a geometry " +
                    "path for this SID.",
            )
        }
    }

    private fun assertSidsCoverPromisedSet(document: WorldCandidateDocument) {
        val expectedCovered = setOf(
            "LJMB_SID_GOLV2G_14",
            "LJMB_SID_PETO2B_14",
            "LJMB_SID_PETO5D_32",
            "LJMB_SID_VALU1S_14",
            "LJMB_SID_VALU4L_32",
        )
        val actual = document.world.aerodrome.sids.keys
        val missing = expectedCovered - actual
        assertTrue(
            missing.isEmpty(),
            buildString {
                appendLine(
                    "LJMB SID coverage drift against CIFP cycle " +
                        "'cycle unknown; source data/cifp/LJMB.dat at git-sha " +
                        "a28fb1eed6a0ff80aedd0a3f3336a35f50d66a97':",
                )
                appendLine("  missing (expected but not present): $missing")
                appendLine(
                    "If non-empty, a SID we promised to support disappeared from the " +
                        "candidate.",
                )
                appendLine(
                    "Investigate the candidate generator " +
                        "(bin/airport_world_candidate.py:811-812 filter) or whether the " +
                        "CIFP cycle has shifted. Do NOT assert `actual - expectedCovered` " +
                        "is empty — new SIDs are a structural-validation surface, not a " +
                        "coverage regression.",
                )
            }.trim(),
        )
    }

    private fun assertExpectedLjmbCircuits(document: WorldCandidateDocument) {
        val circuits = document.world.aerodrome.circuits
        val expectedIds = setOf(
            "LJMB_CIRCUIT_14_MAIN_14_32",
            "LJMB_CIRCUIT_32_MAIN_14_32",
            "LJMB_CIRCUIT_14L_GLIDER_14L_32R",
            "LJMB_CIRCUIT_32R_GLIDER_14L_32R",
        )
        assertEquals(
            expectedIds,
            circuits.keys,
            "LJMB current-core candidate should project one circuit procedure per runway direction for the main 14/32 loop and the 14L/32R glider loop.",
        )

        val expectedLegNames = listOf("UPWIND", "CROSSWIND", "DOWNWIND", "BASE", "FINAL")
        val expectations = mapOf(
            "LJMB_CIRCUIT_14_MAIN_14_32" to Pair("14", "RIGHT_HAND"),
            "LJMB_CIRCUIT_32_MAIN_14_32" to Pair("32", "LEFT_HAND"),
            "LJMB_CIRCUIT_14L_GLIDER_14L_32R" to Pair("14L", "LEFT_HAND"),
            "LJMB_CIRCUIT_32R_GLIDER_14L_32R" to Pair("32R", "RIGHT_HAND"),
        )
        val paths = document.world.geometry.paths
        for ((circuitId, pair) in expectations) {
            val (expectedRunway, expectedDirection) = pair
            val circuit = circuits.getValue(circuitId)
            assertEquals(expectedRunway, circuit.runwayId, "Circuit $circuitId runway")
            assertEquals(expectedDirection, circuit.direction, "Circuit $circuitId direction")
            assertEquals(1876, circuit.altitudeFeet, "Circuit $circuitId altitude (1000 ft AGL over elevation 876 ft MSL)")
            assertEquals(
                expectedLegNames,
                circuit.legs.map { leg -> leg.name },
                "Circuit $circuitId leg ordering",
            )
            circuit.legs.forEach { leg ->
                assertTrue(
                    paths.containsKey(leg.pathId),
                    "Circuit $circuitId leg ${leg.name} pathId ${leg.pathId} must resolve",
                )
            }
            assertTrue(
                paths.containsKey(circuit.goAroundPathId),
                "Circuit $circuitId goAroundPathId ${circuit.goAroundPathId} must resolve",
            )
        }
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
private data class LjmbValidationReportDocument(
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
    val issues: List<LjmbValidationIssueRecord>,
)

@Serializable
private data class LjmbValidationIssueRecord(
    val code: String,
    val message: String,
)
