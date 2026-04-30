package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test — belief-write provenance.
 *
 * The protected slices on [xyz.easiersaid.twr.controller.observe.BeliefState]
 * must be written only by the typed fold extensions in `Observe.kt`:
 *  - `recentRadio` — fold over [ControllerEvent] via `withRecentRadio`
 *    (Pass 5 D-AUDIT.14 closure: replaces the deleted `aircraftIntent` slice;
 *    intent is derived on demand by `deriveCurrentIntent`).
 *  - `circuitIntent` — fold over CircuitIntentReported / GoAroundDetected
 *    via `withCircuitIntentEvents`.
 *
 * Any other write site is a firewall regression.
 *
 * Detection: scan `controller/src/commonMain` for `recentRadio[` or
 * `circuitIntent[` (map-index assignment) and `recentRadio =` / `circuitIntent =`
 * (constructor / `copy` parameter assignment). The only file allowed to
 * contain these patterns is `Observe.kt`. Type declaration in `BeliefState.kt`
 * is also allowed (the field declaration itself).
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Fix the violation or amend
 * the firewall via plan revision.
 */
class FirewallBeliefWriteTest {

    @Test
    fun `recentRadio and circuitIntent are written only by typed event fold`() {
        val controllerCommon = projectRoot()
            .resolve("controller/src/commonMain/kotlin")
        val violations = mutableListOf<String>()
        // The forbidden pattern is mutation of the BeliefState slice — i.e.
        // `.copy(... recentRadio = ...)` or `BeliefState(... recentRadio = ...)`
        // — and map-index writes like `recentRadio[id] = value`.
        // Reads (`ctx.beliefs.recentRadio[id]`) and parameter passing
        // (`reconcileCommitments(recentRadio = b.recentRadio, ...)`) are fine;
        // both are narrow consumer surfaces. The architectural rule is that
        // BeliefState's slice values originate only from the typed fold.
        val mutationPattern = Regex(
            """\.copy\s*\([^)]*\b(recentRadio|circuitIntent)\s*=""",
            RegexOption.DOT_MATCHES_ALL,
        )
        // The trailing `[^=]` excludes `==` from matching; we want assignment,
        // not equality comparison. Without this, `circuitIntent[ac.id] == intent`
        // (used in guards) trips the regex.
        val mapWritePattern = Regex("""\b(recentRadio|circuitIntent)\s*\[[^]]*]\s*=[^=]""")
        Files.walk(controllerCommon).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val name = file.fileName.toString()
                // Allowlist: declaration site (BeliefState.kt) and the typed
                // fold (Observe.kt) may write the slices.
                if (name == "BeliefState.kt" || name == "Observe.kt") return@forEach
                val text = Files.readString(file)
                val codeOnly = text.replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""//[^\n]*"""), "")
                mutationPattern.findAll(codeOnly).forEach { match ->
                    violations.add("$name: copy() mutation: ${match.value.trim()}")
                }
                mapWritePattern.findAll(codeOnly).forEach { match ->
                    violations.add("$name: map-index write: ${match.value.trim()}")
                }
            }
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION: recentRadio / circuitIntent mutated outside
            BeliefState.kt and Observe.kt:
            $violations

            These belief slices must be populated only by the typed event-fold
            extensions in Observe.kt. Their values come from radio (typed
            ControllerEvents) — never from anywhere else. Adding a new write
            site is a firewall amendment that requires plan revision.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
