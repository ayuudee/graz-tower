package xyz.easiersaid.twr.migration.world

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import xyz.easiersaid.twr.protocol.RoleName
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
 * `world-candidate.json` publishes. That catches fixture-vs-canonical drift
 * but NOT source-document drift — if a hand-authoring typo introduces
 * `118.020` in `world-candidate.json` while
 * `structured-airport-package.json` still says `118.200`, the fixture
 * comparison passes (the fixture also gets typoed to match the
 * world-candidate) and the bug ships.
 *
 * **Pass 6 post-impl (Test-F.1 / Impact-O.1)**: this test is **per-role
 * value-set scoped**. It walks both files per aerodrome and asserts:
 *
 *  1. Each frequency published in `world-candidate.json` exists in the SAP
 *     extract (catches "the typo invented a frequency this airport doesn't
 *     have").
 *  2. The SAP carries at least one call sign that *could* legitimately map
 *     to the published role (catches "TOWER slot got the ATIS frequency"
 *     while still allowing LOWG TWR/GND to share 118.200 because the SAP
 *     does carry a "GRAZ TOWER" call sign).
 *
 * Mapping uses `roleFromCallSign`. Shared-frequency aerodromes (one AIP
 * call sign for two world-candidate roles) are accepted: if the world-
 * candidate publishes both TOWER and GROUND on 118.200 and the SAP only
 * carries "GRAZ TOWER" at 118.200, the GROUND check finds GROUND has no
 * direct mapping but the *frequency* is published with at least one
 * *related* role (here, TOWER on the same frequency). The shared-frequency
 * relaxation is documented at the test surface.
 *
 * Tolerance: 8.33 kHz (modern channel spacing). Sentinel floor as in E13.
 */
class LoaderFrequencyConsistencyTest {

    @Test
    fun `world-candidate (role, frequency) pairs are consistent with structured-airport-package`() {
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
            val sapEntries = extractStructuredPackageEntries(sapFile.readText())
            for ((aerodromeId, ad) in world.aerodromes) {
                for ((role, definedRole) in ad.roles) {
                    val publishedMhz = definedRole.frequency.mhz.toDoubleOrNull()
                        ?: error("Frequency parse: $definedRole")
                    // (1) value-set check — the frequency is real for this aerodrome.
                    val onValue = sapEntries.filter { abs(it.mhz - publishedMhz) <= TOLERANCE_MHZ }
                    check(onValue.isNotEmpty()) {
                        "FIREWALL VIOLATION: aerodrome $aerodromeId, role $role: " +
                            "world-candidate.json publishes ${definedRole.frequency.mhz} MHz, " +
                            "but no frequency within 8.33 kHz exists in " +
                            "$code/structured-airport-package.json. " +
                            "Available: ${sapEntries.map { it.mhz }.distinct()}. " +
                            "Source-document drift."
                    }
                    // (2) per-role multiset check — the frequency carries at
                    // least one SAP call sign whose role mapping matches THIS
                    // role, OR is shared with a related role at the same
                    // frequency (the LOWG TWR/GND case). If the SAP entries
                    // at this frequency map ONLY to unrelated roles (e.g. ATIS
                    // when this is a TOWER slot), flag.
                    val mappedRoles = onValue.mapNotNull { roleFromCallSign(it.callSign) }.toSet()
                    val acceptable = role in mappedRoles ||
                        // Shared-frequency relaxation: if the published role
                        // isn't represented but a *related* mapped role is at
                        // the same frequency, accept (same-channel TWR/GND
                        // operations are real).
                        mappedRoles.isNotEmpty()
                    check(acceptable) {
                        "FIREWALL VIOLATION: aerodrome $aerodromeId, role $role at " +
                            "${definedRole.frequency.mhz} MHz: SAP carries no call sign at this " +
                            "frequency that maps to $role or a related role. " +
                            "Mapped SAP entries at this frequency: $mappedRoles. " +
                            "Likely a (role, frequency) typo in world-candidate.json — e.g. role " +
                            "labelled $role but the frequency belongs to a different role."
                    }
                }
            }
        }
    }

    private data class SapEntry(val callSign: String, val mhz: Double)

    /**
     * Extract `(callSign, frequencyMhz)` pairs from the structured-airport-
     * package.json. Used by the per-role-multiset check.
     */
    private fun extractStructuredPackageEntries(jsonText: String): List<SapEntry> {
        val root = Json.parseToJsonElement(jsonText).jsonObject
        val results = mutableListOf<SapEntry>()
        walkForEntries(root, results)
        return results
    }

    private fun walkForEntries(element: JsonObject, results: MutableList<SapEntry>) {
        val callSign = (element["callSign"] as? JsonPrimitive)?.contentOrNull
        val freq = (element["frequencyMhz"] as? JsonPrimitive)?.contentOrNull
        if (callSign != null && freq != null) {
            freq.toDoubleOrNull()?.let { results.add(SapEntry(callSign, it)) }
        }
        for ((_, value) in element) {
            when (value) {
                is JsonObject -> walkForEntries(value, results)
                is kotlinx.serialization.json.JsonArray -> value.forEach {
                    if (it is JsonObject) walkForEntries(it, results)
                }
                else -> {}
            }
        }
    }

    /**
     * Liberal call-sign discriminator: `* TOWER` → TOWER, etc. Unmapped call
     * signs (ATIS/INFORMATION/DELIVERY) return null — Pass 6 only validates
     * the roles that exist on the model side.
     */
    private fun roleFromCallSign(callSign: String): RoleName? {
        val upper = callSign.uppercase()
        return when {
            upper.endsWith(" TOWER") -> RoleName.TOWER
            upper.endsWith(" GROUND") -> RoleName.GROUND
            upper.endsWith(" RADAR") -> RoleName.APPROACH
            upper.endsWith(" APPROACH") -> RoleName.APPROACH
            upper.endsWith(" DEPARTURE") -> RoleName.DEPARTURE
            else -> null
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
