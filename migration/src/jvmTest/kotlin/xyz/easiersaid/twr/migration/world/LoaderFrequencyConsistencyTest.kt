package xyz.easiersaid.twr.migration.world

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.math.abs
import kotlin.test.Test

/**
 * Architectural enforcement test (E15) — cross-document drift catcher.
 *
 * Pass 6 (Impact review O3): `FrequencyMismatch` (in `Fixture.validate`)
 * compares the test fixture's expected frequency against what
 * `world-candidate.json` publishes. That catches fixture-vs-canonical
 * drift but NOT source-document drift — if a hand-authoring typo
 * introduces `118.020` in `world-candidate.json` while
 * `structured-airport-package.json` still says `118.200`, the fixture
 * comparison passes (the fixture also gets typoed to match the
 * world-candidate) and the bug ships.
 *
 * This test walks both files per aerodrome and asserts every frequency
 * published by a role in `world-candidate.json` *exists* in the
 * `structured-airport-package.json` extract — match by frequency value,
 * not by role-to-callsign mapping. The role↔callsign mapping is too
 * lossy to enforce: real shared-frequency aerodromes (LOWG TWR/GND on
 * 118.200) publish ONE call sign in the AIP for both roles, while the
 * canonical model splits them. The reality-anchored check is "is this
 * frequency really an AIP-published value at this airport?"
 *
 * Tolerance: 8.33 kHz (modern channel spacing). Sentinel floor as in E13.
 */
class LoaderFrequencyConsistencyTest {

    @Test
    fun `world-candidate frequencies appear in the structured-airport-package source`() {
        val rendered = projectRoot().resolve("cad/airports/rendered").toFile()
        val pairs = rendered.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir ->
                val wc = dir.resolve("world-candidate.json").takeIf { it.exists() }
                val sap = dir.resolve("structured-airport-package.json").takeIf { it.exists() }
                if (wc != null && sap != null) Triple(dir.name, wc, sap) else null
            }.orEmpty()

        // Sentinel floor.
        val codes = pairs.map { it.first }.toSet()
        check("lowg" in codes && "ljmb" in codes) {
            "LoaderFrequencyConsistencyTest sentinel: expected lowg+ljmb subdirectories. " +
                "Got: $codes."
        }

        val json = Json { ignoreUnknownKeys = true }
        for ((code, wcFile, sapFile) in pairs) {
            val world = WorldCandidateLoader.toWorld(json.decodeFromString(wcFile.readText()))
            val sapMhzValues = extractStructuredPackageFrequencyValues(sapFile.readText())
            for ((aerodromeId, ad) in world.aerodromes) {
                for ((role, definedRole) in ad.roles) {
                    val publishedMhz = definedRole.frequency.mhz.toDoubleOrNull()
                        ?: error("Frequency parse: $definedRole")
                    val matches = sapMhzValues.any { abs(it - publishedMhz) <= TOLERANCE_MHZ }
                    check(matches) {
                        "FIREWALL VIOLATION: aerodrome $aerodromeId, role $role: " +
                            "world-candidate.json publishes ${definedRole.frequency.mhz} MHz, " +
                            "but no frequency within 8.33 kHz exists in " +
                            "$code/structured-airport-package.json. Available: $sapMhzValues. " +
                            "Source-document drift."
                    }
                }
            }
        }
    }

    /**
     * Extract every numeric `frequencyMhz` value from a structured-airport-
     * package.json (regardless of associated call sign — see class KDoc for
     * why role-from-callsign mapping was rejected).
     */
    private fun extractStructuredPackageFrequencyValues(jsonText: String): List<Double> {
        val root = Json.parseToJsonElement(jsonText).jsonObject
        val results = mutableListOf<Double>()
        walkForFrequencies(root, results)
        return results
    }

    private fun walkForFrequencies(element: JsonObject, results: MutableList<Double>) {
        val freq = (element["frequencyMhz"] as? JsonPrimitive)?.contentOrNull
        if (freq != null) {
            freq.toDoubleOrNull()?.let { results.add(it) }
        }
        // Recurse into nested objects and arrays — the structured-airport-
        // package layout is nested.
        for ((_, value) in element) {
            when (value) {
                is JsonObject -> walkForFrequencies(value, results)
                is kotlinx.serialization.json.JsonArray -> value.forEach {
                    if (it is JsonObject) walkForFrequencies(it, results)
                }
                else -> {}
            }
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }

    companion object {
        /** 8.33 kHz channel spacing — the modern aviation tolerance. */
        const val TOLERANCE_MHZ: Double = 0.00833
    }
}
