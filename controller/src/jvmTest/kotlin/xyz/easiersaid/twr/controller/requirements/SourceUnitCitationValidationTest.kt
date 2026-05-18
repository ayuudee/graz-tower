package xyz.easiersaid.twr.controller.requirements

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class SourceUnitCitationValidationTest {
    @Test
    fun `Kotlin source-unit citations exist in accepted registry`() {
        val accepted = acceptedRegistrySourceUnits()
        val citations = kotlinSourceUnitCitations()

        val missing = citations - accepted

        assertTrue(
            missing.isEmpty(),
            "Kotlin SourceUnitRef citations missing from accepted registry:\n${missing.sorted().joinToString("\n")}",
        )
    }

    @Test
    fun `covered FN33 ledger rows are backed by Kotlin source-unit citations`() {
        val citations = kotlinSourceUnitCitations()
        val coveredRows = fn33LedgerRows()
            .filter { row -> row.status == "covered" || row.status == "partially_covered" }

        val uncited = coveredRows
            .map { row -> row.sourceUnitId }
            .filterNot { sourceUnit -> sourceUnit in citations }

        assertTrue(
            uncited.isEmpty(),
            "FN33 covered/partially-covered ledger rows without Kotlin SourceUnitRef citation:\n" +
                uncited.sorted().joinToString("\n"),
        )
    }

    private fun acceptedRegistrySourceUnits(): Set<String> =
        Files.walk(repoRoot().resolve("research/tools/requirements-spike/registry/ollama_first/candidates")).use { paths ->
            paths
                .filter { path -> path.isRegularFile() && path.extension == "json" && path.name != "_section.json" }
                .toList()
                .map { path -> path.readText() }
                .filter { text -> lifecycleStateRegex.find(text)?.groupValues?.get(1) == "accepted" }
                .mapNotNull { text -> canonicalIdRegex.find(text)?.groupValues?.get(1) }
                .toSet()
        }

    private fun kotlinSourceUnitCitations(): Set<String> =
        Files.walk(repoRoot()).use { paths ->
            paths
                .filter { path -> path.isRegularFile() && path.extension == "kt" }
                .toList()
                .flatMap { path -> sourceUnitRefRegex.findAll(path.readText()).map { match -> match.groupValues[1] } }
                .toSet()
        }

    private fun fn33LedgerRows(): List<LedgerRow> =
        repoRoot()
            .resolve("research/tools/requirements-spike/quality/rule_to_test_spike/fn33_icao9432_section_workflow_2026-05-17/icao9432_ledger.jsonl")
            .readLines()
            .mapNotNull { line ->
                val sourceUnit = sourceUnitIdRegex.find(line)?.groupValues?.get(1)
                val status = statusRegex.find(line)?.groupValues?.get(1)
                if (sourceUnit == null || status == null) null else LedgerRow(sourceUnit, status)
            }

    private data class LedgerRow(
        val sourceUnitId: String,
        val status: String,
    )

    private companion object {
        val canonicalIdRegex = Regex(""""canonicalId"\s*:\s*"([^"]+)"""")
        val lifecycleStateRegex = Regex(""""state"\s*:\s*"([^"]+)"""")
        val sourceUnitIdRegex = Regex(""""source_unit_id"\s*:\s*"([^"]+)"""")
        val sourceUnitRefRegex = Regex("""SourceUnitRef\("([^"]+)"\)""")
        val statusRegex = Regex(""""status"\s*:\s*"([^"]+)"""")

        fun repoRoot(): Path =
            generateSequence(Path.of("").toAbsolutePath().normalize()) { path -> path.parent }
                .firstOrNull { path -> Files.exists(path.resolve("settings.gradle.kts")) && Files.exists(path.resolve(".flow")) }
                ?: error("Could not locate repo root from ${Path.of("").toAbsolutePath().normalize()}")
    }
}
