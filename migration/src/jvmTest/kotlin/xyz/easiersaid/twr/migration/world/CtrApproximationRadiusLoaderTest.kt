package xyz.easiersaid.twr.migration.world

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.core.world.Doctrine
import xyz.easiersaid.twr.core.world.Meters

/**
 * fn-7 R8 + R9 — per-aerodrome `ctrApproximationRadius` loader contract.
 *
 * Two pins:
 *
 *  - **R8 sub-floor rejection**: the loader rejects an authored
 *    `ctrApproximationRadiusNauticalMiles` value below
 *    [Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES] (= 5 NM, the ICAO
 *    Annex 11 §2.11 floor). Authored 4 must throw at load time, not be
 *    silently coerced. The error message must cite the constant value
 *    and the §2.11 source.
 *
 *  - **R9 real-airport authoring guardrail**: every rendered airport
 *    under `cad/airports/rendered/<icao>/world-candidate.json` must
 *    author `ctrApproximationRadiusNauticalMiles` exactly per the
 *    [expected] allowlist. Catches:
 *      - missing field (silent 5 NM ICAO-floor fallback at a real
 *        controlled aerodrome — permissive-wrong, releases inside CTR)
 *      - wrong / stale value (e.g. typo `180`, regression to `5`)
 *      - new rendered airport without a deliberate review of its
 *        AIP-derived radius (forces a test-update + plan-review)
 *
 * **No-suppression rule.** Resolve a failure by hand-authoring the
 * radius from AIP AD 2.17 polygon data (rounded UP to NM, with proxy-
 * offset margin) and updating the [expected] allowlist with a citation
 * comment, not by `@Disabled`/`@Suppress`/test removal. The 5 NM ICAO
 * floor is the loader's null-fallback, not a per-airport authoring
 * value at controlled aerodromes.
 */
class CtrApproximationRadiusLoaderTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Per-airport authored radius allowlist. Every rendered airport must
     * appear here with its exact authored value; new airports without an
     * entry fail R9 with a directive pointing to the fn-7 epic spec's
     * "Decision context" section.
     *
     * Citations:
     *  - **LOWG 18 NM**: AIP Austria, AD 2 LOWG §2.17 (control zone
     *    lateral limits), effective 2026-04-01 (AIRAC 2604). Polygon
     *    max-edge 16.25 NM rounded UP + ~1 NM ARP-proxy-offset margin.
     *  - **LJMB 18 NM**: conservative placeholder (= same as LOWG).
     *    Slovenia eAIP not bot-fetchable; real-polygon transcription
     *    deferred as `D-AUDIT-ljmb-polygon`. The 5 NM ICAO Annex 11
     *    §2.11 floor would be permissive-wrong (real LJMB CTR almost
     *    certainly extends past 5 NM); 18 NM under-fires the release
     *    rule, which is regulatorily-safe under uncertainty.
     */
    private val expected: Map<String, Int> = mapOf(
        "LOWG" to 18,
        "LJMB" to 18,
    )

    @Test
    fun `loader rejects a sub-floor authored ctrApproximationRadiusNauticalMiles`() {
        // R8: synthesise a sub-floor authored value by patching the LOWG
        // world-candidate JSON (the canonical valid candidate is the
        // smallest delta from the green-baseline path; constructing a
        // synthetic candidate from scratch would risk false negatives).
        val candidatePath = projectRoot().resolve("cad/airports/rendered/lowg/world-candidate.json")
        assertTrue(Files.exists(candidatePath), "Missing LOWG world candidate at $candidatePath")
        val original = json.decodeFromString<WorldCandidateDocument>(candidatePath.readText())
        // Sanity: the on-disk value is in-floor (R9 pins this exactly elsewhere).
        check((original.world.aerodrome.ctrApproximationRadiusNauticalMiles ?: 0) >=
            Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES) {
            "Test pin assumption: LOWG on-disk value must be >= " +
                "${Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES} NM (got " +
                "${original.world.aerodrome.ctrApproximationRadiusNauticalMiles})"
        }
        val subFloorValue = Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES - 1  // = 4
        val patched = original.copy(
            world = original.world.copy(
                aerodrome = original.world.aerodrome.copy(
                    ctrApproximationRadiusNauticalMiles = subFloorValue,
                ),
            ),
        )

        val ex = assertFailsWith<IllegalArgumentException> {
            WorldCandidateLoader.toWorld(patched)
        }
        val msg = ex.message ?: ""
        assertTrue(
            msg.contains("${Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES} NM"),
            "Sub-floor rejection message must cite the floor constant " +
                "(\"${Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES} NM\"); got: \"$msg\"",
        )
        assertTrue(
            msg.contains("§2.11"),
            "Sub-floor rejection message must cite ICAO Annex 11 §2.11; got: \"$msg\"",
        )
        assertTrue(
            msg.contains("$subFloorValue"),
            "Sub-floor rejection message must include the offending value " +
                "($subFloorValue); got: \"$msg\"",
        )
    }

    @Test
    fun `every rendered airport authors ctrApproximationRadiusNauticalMiles per allowlist`() {
        // R9: scan every rendered world-candidate.json and assert exact-
        // value match against the [expected] allowlist. Catches missing
        // field, wrong value, and untracked new airports in one pass.
        val rendered = projectRoot().resolve("cad/airports/rendered").toFile()
        val worldCandidates = rendered.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir ->
                dir.resolve("world-candidate.json")
                    .takeIf { it.exists() }
                    ?.let { dir.name to it }
            }.orEmpty()

        // Sentinel floor (mirrors LoaderRolesPopulatedTest): the canonical
        // aerodromes must be present on disk. A stripped CI checkout would
        // otherwise vacuously pass.
        val codes = worldCandidates.map { it.first }.toSet()
        check("lowg" in codes && "ljmb" in codes) {
            "CtrApproximationRadiusLoaderTest sentinel: expected lowg+ljmb subdirectories " +
                "under cad/airports/rendered. Got: $codes. The R9 guardrail cannot run " +
                "against an empty rendered tree."
        }

        for ((dir, file) in worldCandidates) {
            // R9 hardening (impl-review fn-7-1 finding): key the allowlist by
            // the rendered-directory name (uppercased), NOT by the JSON's
            // self-declared `aerodrome.icao`. Otherwise a new directory like
            // `cad/airports/rendered/abcd/world-candidate.json` could ship a
            // copy-pasted JSON that still says `"icao": "LOWG"` and silently
            // satisfy the allowlist — bypassing the "every new rendered
            // airport requires deliberate review" guard. The directory name
            // is the contract; the JSON content is verified against it
            // before any allowlist lookup.
            val dirIcao = dir.uppercase()
            val document = json.decodeFromString<WorldCandidateDocument>(file.readText())
            val documentIcao = document.world.aerodrome.icao
            assertEquals(
                dirIcao,
                documentIcao,
                "R9 GUARDRAIL: rendered directory `cad/airports/rendered/$dir/" +
                    "world-candidate.json` declares `aerodrome.icao = " +
                    "\"$documentIcao\"` — must match the directory name " +
                    "(`$dirIcao`). A copy-pasted JSON with a stale ICAO would " +
                    "let a new rendered airport bypass the allowlist guard " +
                    "below by impersonating an allowlisted airport.",
            )
            val authored = document.world.aerodrome.ctrApproximationRadiusNauticalMiles
            val expectedNm = expected[dirIcao]
            check(expectedNm != null) {
                "R9 GUARDRAIL: rendered airport `$dirIcao` (in $dir/world-candidate.json) " +
                    "is not in the CtrApproximationRadiusLoaderTest.expected allowlist. " +
                    "Add an entry with the deliberate per-airport value derived from " +
                    "AIP AD 2.17 polygon data (rounded UP to NM, with proxy-offset margin). " +
                    "Review the fn-7 epic spec's Decision context section for the LOWG / " +
                    "LJMB authoring precedent."
            }
            check(authored != null) {
                "R9 GUARDRAIL: rendered airport `$dirIcao` (in $dir/world-candidate.json) " +
                    "has no `ctrApproximationRadiusNauticalMiles` field — would silently " +
                    "fall back to the 5 NM ICAO Annex 11 §2.11 floor, which is permissive-" +
                    "wrong at almost every controlled aerodrome (releases inside the real " +
                    "CTR polygon on the approach axis). Author the AIP-derived radius."
            }
            assertEquals(
                expectedNm,
                authored,
                "R9 GUARDRAIL: rendered airport `$dirIcao` (in $dir/world-candidate.json) " +
                    "authors $authored NM; expected $expectedNm NM per allowlist. A " +
                    "deliberate change requires updating both the JSON and the " +
                    "CtrApproximationRadiusLoaderTest.expected map (with citation).",
            )

            // Deeper pin: load the candidate and verify the runtime
            // Aerodrome.ctrApproximationRadius matches the authored value
            // converted via Meters.fromNauticalMiles. Catches a regression
            // where the loader threading is silently broken (loader picks
            // up the schema field but fails to wire it through).
            val world = WorldCandidateLoader.toWorld(document)
            val aerodrome = world.aerodromes.values.single()
            assertEquals(
                Meters.fromNauticalMiles(expectedNm),
                aerodrome.ctrApproximationRadius,
                "Loader threading regression: $dirIcao authored $expectedNm NM but " +
                    "Aerodrome.ctrApproximationRadius came out as " +
                    "${aerodrome.ctrApproximationRadius.value} m " +
                    "(expected ${Meters.fromNauticalMiles(expectedNm).value} m).",
            )
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
