package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test — belief-write provenance.
 *
 * The protected slices on [xyz.easiersaid.twr.controller.observe.BeliefState]
 * must be written only by typed fold/extension functions in their canonical
 * homes. Pass 9 (D-AUDIT.2) extends the protection to the `coordinations`
 * slice, which is the lifecycle ledger for issued instructions.
 *
 * Protected slices and their allowed write sites:
 *  - `recentRadio` — `withRecentRadio` in `Observe.kt` (Pass 5).
 *  - `circuitIntent` — `withCircuitIntentEvents` in `Observe.kt` (Pass 5).
 *  - `coordinations` (Pass 9 D-AUDIT.2) — written by `recordCoordinations`
 *    and `escalateOverdueCoordinations` in `Readback.kt`,
 *    `markCoordinationEscalationsEmitted` in `CoordinationEscalation.kt`,
 *    `applySupersessionCleanup` in `Supersession.kt`, the `acceptReadback`
 *    fold in `Controller.kt`, and the structural-preserve in `Observe.kt`.
 *
 * The `coordinations` allowlist is broader than the other slices because
 * the lifecycle has more legitimate write paths (issuance, escalation,
 * confirmation, supersession). Each is named in source and bounded.
 *
 * Any other write site is a firewall regression.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Fix the violation or amend
 * the firewall via plan revision.
 */
class FirewallBeliefWriteTest {

    @Test
    fun `protected belief slices are written only by allowlisted files`() {
        val controllerCommon = projectRoot()
            .resolve("controller/src/commonMain/kotlin")
        val violations = mutableListOf<String>()
        // Per-slice allowlist (filename → allowed slices written from there).
        // BeliefState.kt is implicitly allowlisted for all slices (declaration
        // site + projection getters).
        val sliceAllowlist: Map<String, Set<String>> = mapOf(
            "Observe.kt" to setOf("recentRadio", "circuitIntent", "coordinations"),
            "Readback.kt" to setOf("coordinations"),
            "CoordinationEscalation.kt" to setOf("coordinations"),
            "Supersession.kt" to setOf("coordinations"),
            "Controller.kt" to setOf("coordinations"), // acceptReadback fold (Pass 9: bounded)
        )
        val sliceNames = listOf("recentRadio", "circuitIntent", "coordinations")
        val mutationPattern = Regex(
            """\.copy\s*\([^)]*\b(${sliceNames.joinToString("|")})\s*=""",
            RegexOption.DOT_MATCHES_ALL,
        )
        // Trailing `[^=]` excludes `==` (used in guards/comparisons).
        val mapWritePattern = Regex("""\b(${sliceNames.joinToString("|")})\s*\[[^]]*]\s*=[^=]""")
        Files.walk(controllerCommon).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val name = file.fileName.toString()
                if (name == "BeliefState.kt") return@forEach // declaration / projections
                val text = Files.readString(file)
                val codeOnly = text.replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""//[^\n]*"""), "")
                val allowed = sliceAllowlist[name].orEmpty()
                mutationPattern.findAll(codeOnly).forEach { match ->
                    val slice = match.groupValues[1]
                    if (slice !in allowed) {
                        violations.add("$name: copy() mutation: ${match.value.trim()}")
                    }
                }
                mapWritePattern.findAll(codeOnly).forEach { match ->
                    val slice = match.groupValues[1]
                    if (slice !in allowed) {
                        violations.add("$name: map-index write: ${match.value.trim()}")
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION: protected belief slice mutated outside its
            allowlisted files:
            $violations

            Allowlist (file → permitted slices):
            ${sliceAllowlist.entries.joinToString("\n            ") { (f, s) -> "$f → ${s.joinToString(", ")}" }}

            These belief slices must be populated only by the named typed
            extensions. Adding a new write site is a firewall amendment that
            requires plan revision.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
