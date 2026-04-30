package xyz.easiersaid.twr.migration.world

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.core.world.validate
import xyz.easiersaid.twr.protocol.AerodromeId

/**
 * Real-job integration test: load LOWG and LJMB world candidates simultaneously
 * and prove they merge into one valid [AviationWorld] with both aerodromes
 * addressable. This is the "fold LJMB into the main app" ready-gate.
 */
class MultiAerodromeLoaderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `merges LOWG and LJMB into one multi-aerodrome world`() {
        val projectRoot = resolveProjectRoot()
        val lowgPath = projectRoot.resolve("cad/airports/rendered/lowg/world-candidate.json")
        val ljmbPath = projectRoot.resolve("cad/airports/rendered/ljmb/world-candidate.json")
        assertTrue(Files.exists(lowgPath), "Missing LOWG world candidate at $lowgPath")
        assertTrue(Files.exists(ljmbPath), "Missing LJMB world candidate at $ljmbPath")

        val lowg = WorldCandidateLoader.toWorld(json.decodeFromString(lowgPath.readText()))
        val ljmb = WorldCandidateLoader.toWorld(json.decodeFromString(ljmbPath.readText()))

        val merged = WorldCandidateLoader.mergeAviationWorlds(listOf(lowg, ljmb))

        assertEquals(
            setOf(AerodromeId("LOWG"), AerodromeId("LJMB")),
            merged.aerodromes.keys,
            "Merged world should expose both LOWG and LJMB aerodromes as peers.",
        )

        val lowgAerodrome = merged.aerodromes.getValue(AerodromeId("LOWG"))
        val ljmbAerodrome = merged.aerodromes.getValue(AerodromeId("LJMB"))

        assertTrue(lowgAerodrome.runways.isNotEmpty(), "LOWG should keep its runways after merge.")
        assertTrue(ljmbAerodrome.runways.isNotEmpty(), "LJMB should keep its runways after merge.")

        // Enroute fixes (GOLVA, DIMLO, MUREG, VALLU, PETOV) appear in both airports'
        // candidate fix maps with airport-scoped pointIds. First-wins merge keeps
        // LOWG's copy of any shared fix; the merged count reflects the union of
        // unique fix identifiers.
        val sharedFixIds = lowg.fixes.keys intersect ljmb.fixes.keys
        assertEquals(
            lowg.fixes.size + ljmb.fixes.size - sharedFixIds.size,
            merged.fixes.size,
            "Merged fix count should be the union of per-airport fixes, with shared enroute fixes ${sharedFixIds.sortedBy { it.value }} deduplicated by first-wins.",
        )

        // Geometry points are also airport-prefixed in the pipeline, so no collisions.
        assertEquals(
            lowg.geometry.points.size + ljmb.geometry.points.size,
            merged.geometry.points.size,
            "Merged geometry point count should be the sum of per-airport points.",
        )

        // Running the runtime validator on the merged world must still report zero
        // structural issues for each airport's own content.
        val report = merged.validate()
        val structuralIssues = report.issues.filter { issue -> issue.code.isStructural() }
        assertTrue(
            structuralIssues.isEmpty(),
            buildString {
                appendLine("Unexpected structural issues in merged LOWG+LJMB world:")
                structuralIssues.forEach { issue -> appendLine("  - ${issue.code}: ${issue.message}") }
            }.trim(),
        )
    }

    private fun resolveProjectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val direct = cwd.resolve("settings.gradle.kts")
        return if (Files.exists(direct)) cwd else cwd.parent ?: cwd
    }

    private fun xyz.easiersaid.twr.core.world.WorldValidationCode.isStructural(): Boolean =
        this in setOf(
            xyz.easiersaid.twr.core.world.WorldValidationCode.ORPHAN_GEOMETRY_POINT,
            xyz.easiersaid.twr.core.world.WorldValidationCode.ORPHAN_GEOMETRY_SEGMENT,
            xyz.easiersaid.twr.core.world.WorldValidationCode.GEOMETRY_SEGMENT_UNKNOWN_ENDPOINT,
            xyz.easiersaid.twr.core.world.WorldValidationCode.UNKNOWN_GEOMETRY_POINT_REFERENCE,
            xyz.easiersaid.twr.core.world.WorldValidationCode.UNKNOWN_GEOMETRY_SEGMENT_REFERENCE,
            xyz.easiersaid.twr.core.world.WorldValidationCode.POINT_OUTSIDE_AIRSPACE,
            xyz.easiersaid.twr.core.world.WorldValidationCode.UNKNOWN_FIR,
            xyz.easiersaid.twr.core.world.WorldValidationCode.FIR_VOLUME_MISMATCH,
            xyz.easiersaid.twr.core.world.WorldValidationCode.UNKNOWN_AIRSPACE_VOLUME,
            xyz.easiersaid.twr.core.world.WorldValidationCode.RECIPROCAL_RUNWAYS_DO_NOT_SHARE_SEGMENT,
        )
}
