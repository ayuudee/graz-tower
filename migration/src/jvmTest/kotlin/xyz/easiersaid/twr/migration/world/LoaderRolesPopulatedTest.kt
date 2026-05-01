package xyz.easiersaid.twr.migration.world

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test (E13) — every rendered world-candidate
 * JSON publishes at least one controller role.
 *
 * Pass 6 (D-AUDIT.12 closure): the loader now reads `roles` from the
 * canonical hand-authored `world-candidate.json`. The fixture's old
 * post-load `ad.copy(roles = ...)` patch is gone. If a future engineer
 * adds a new aerodrome JSON without `roles`, the schema accepts it
 * silently (default `emptyMap()`) but this test fails — surfacing the
 * gap loudly at PR-review time.
 *
 * **Sentinel floor (Test review F.1)**: assert the known-published
 * aerodromes (`lowg`, `ljmb`) appear in the rendered tree before
 * walking. Without this, an empty `cad/airports/rendered` tree (a
 * stripped CI checkout) would pass vacuously — the for-loop iterates
 * zero times and finds zero violations.
 *
 * **No-suppression rule** — resolve a failure by hand-authoring the
 * `roles` block in the JSON, not by `@Disabled`/`@Suppress`/test removal.
 */
class LoaderRolesPopulatedTest {

    @Test
    fun `every rendered world-candidate JSON publishes at least one role`() {
        val rendered = projectRoot().resolve("cad/airports/rendered").toFile()
        val worldCandidates = rendered.listFiles { f -> f.isDirectory }
            ?.mapNotNull { dir ->
                dir.resolve("world-candidate.json")
                    .takeIf { it.exists() }
                    ?.let { dir.name to it }
            }.orEmpty()

        // Sentinel floor: the known canonical aerodromes are LOWG and LJMB.
        // If they are missing, the test is running against a stripped
        // checkout — fail loudly, don't pass vacuously.
        val codes = worldCandidates.map { it.first }.toSet()
        check("lowg" in codes && "ljmb" in codes) {
            "LoaderRolesPopulatedTest sentinel: expected lowg+ljmb subdirectories under " +
                "cad/airports/rendered. Got: $codes. The test cannot run against an empty " +
                "rendered tree."
        }

        val json = Json { ignoreUnknownKeys = true }
        for ((code, file) in worldCandidates) {
            val world = WorldCandidateLoader.toWorld(json.decodeFromString(file.readText()))
            for ((id, ad) in world.aerodromes) {
                check(ad.roles.isNotEmpty()) {
                    "FIREWALL VIOLATION: aerodrome $id (in $code/world-candidate.json) " +
                        "publishes no roles. Pass 6 (D-AUDIT.12) requires every rendered " +
                        "aerodrome to hand-author a `roles` block sourced from its " +
                        "structured-airport-package.json."
                }
            }
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
