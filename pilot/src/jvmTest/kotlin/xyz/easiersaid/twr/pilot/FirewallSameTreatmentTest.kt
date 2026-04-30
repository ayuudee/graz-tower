package xyz.easiersaid.twr.pilot

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test (E4) — historical-name tripwire for
 * AI-vs-human discriminators.
 *
 * The pilot's behaviour does not branch on whether the cockpit is crewed
 * by AI or human. The **load-bearing** structural enforcement is
 * [FirewallAircraftStateTest] (in commonTest), which compile-fails if any
 * such field is added to [AircraftState] under any name.
 *
 * This test is a **tripwire for the specific historical names** that were
 * used in the codebase before the pilot firewall: `humanPiloted` and
 * `pilotGoal`. Both are gone after Phase D; this test catches them coming
 * back. New AI-vs-human field names (`isAi`, `pilotKind`, etc.) are caught
 * by E5, not here — keep that scope distinction in mind when amending.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal.
 */
class FirewallSameTreatmentTest {

    @Test
    fun `pilot module does not reference historical AI-vs-human discriminators`() {
        val pilotRoot = projectRoot().resolve("pilot/src/commonMain/kotlin")
        val violations = mutableListOf<String>()
        val patterns = listOf(
            Regex("""\bhumanPiloted\b"""),
            Regex("""\bpilotGoal\b"""),
        )
        Files.walk(pilotRoot).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val text = Files.readString(file)
                // Strip both KDoc/block comments and `//` line comments first.
                // Comments are the natural place to document the firewall by
                // example ("the previous `humanPiloted` field…"); a strict
                // text scan of comments would force every doc reference into
                // round-trip evasion. Stripping makes the test resilient.
                val codeOnly = text
                    .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""//[^\n]*"""), "")
                patterns.forEach { pat ->
                    pat.findAll(codeOnly).forEach { match ->
                        violations.add("${file.fileName}: ${match.value}")
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION: pilot module references historical AI-vs-human
            discriminators (humanPiloted / pilotGoal): $violations.

            The pilot's decision logic must not branch on whether the cockpit
            is crewed by AI or human. Same mission tree, same timing, same
            inputs. Real-world differences in crewing are observed (radio-
            derived), not assumed (boolean-discriminated).

            Note: this test catches the *historical names* only. The structural
            defence is FirewallAircraftStateTest (E5), which fails to compile
            if any new AI-vs-human field is added under any name. Both tests
            are needed: this one catches the specific names coming back, E5
            catches new variants.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
