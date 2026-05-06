package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13) — `knownStrips`
 * read+write firewall.
 *
 * The `knownStrips` field on [ControllerSpec] holds AFTN-distributed
 * strips for which the controller does NOT hold responsibility. Two
 * structural rules govern its access:
 *
 * 1. **Read firewall**: the rule layer (`:controller/commonMain`) must
 *    NOT read `knownStrips` directly. The only legitimate consumer is
 *    the projection layer in `ControllerWiring.kt` (`:sim`) which
 *    composes it into `flightStripIntents`. Direct reads from rules
 *    would create dual-source-of-truth ("am I responsible?" vs "do I
 *    know about this strip?") and silently bypass the projection
 *    boundary.
 *
 * 2. **Write firewall**: only `Step.handleFlightPlanFiled` (arrival-
 *    side branch) and `Step.applyContactFrequency` (cleanup on
 *    handoff) may write `knownStrips`. Any other write site is a
 *    firewall regression.
 *
 * **No-suppression rule**: an architectural test failure is never
 * resolved by `@Disabled`, `@Suppress`, or test removal. Resolve by
 * fixing the violation, or by formally amending the firewall via plan
 * revision.
 */
class FirewallKnownStripsAccessTest {

    @Test
    fun `controller commonMain does not read knownStrips`() {
        val controllerCommon = projectRoot().resolve("controller/src/commonMain/kotlin")
        val violations = mutableListOf<String>()
        // Strip comments + strings before scanning; otherwise any KDoc that
        // mentions `knownStrips` would false-positive.
        Files.walk(controllerCommon).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val text = Files.readString(file)
                val codeOnly = text
                    .replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""//[^\n]*"""), "")
                    .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
                    .replace(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), "")
                if (codeOnly.contains("knownStrips")) {
                    violations.add(file.fileName.toString())
                }
            }
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION (Pass 14 D-AUDIT.6.A-FOLLOWUP read-firewall):
            controller commonMain reads `knownStrips` directly. The rule
            layer must NOT consult this field — strips on the board and
            responsibility for the aircraft are separate concerns.

            The only legitimate consumer is the projection layer in
            `ControllerWiring.kt` (sim/commonMain), which composes
            knownStrips into `flightStripIntents` for ControllerView.

            Files violating the firewall:
              ${violations.joinToString("\n              ")}

            Fix: route the read through `flightStripIntents` (or a future
            typed projection), or amend the firewall via plan revision.
            """.trimIndent()
        }
    }

    @Test
    fun `sim commonMain writes knownStrips only from allowlisted call sites`() {
        val simCommon = projectRoot().resolve("sim/src/commonMain/kotlin")
        // Allowed write sites: Step.kt's handleFlightPlanFiled (arrival
        // branch) and applyContactFrequency (cleanup on handoff).
        // ControllerSpec.kt is the declaration site (data-class accessors
        // and `copy`), implicitly allowed.
        val allowedFiles = setOf("Step.kt", "ControllerSpec.kt")
        val violations = mutableListOf<String>()
        // Forbidden: `knownStrips =` (assignment in copy()) outside allowed files.
        val pattern = Regex("""\bknownStrips\s*=[^=]""")
        Files.walk(simCommon).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val name = file.fileName.toString()
                if (name in allowedFiles) return@forEach
                val text = Files.readString(file)
                val codeOnly = text
                    .replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""//[^\n]*"""), "")
                    .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
                    .replace(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), "")
                if (pattern.containsMatchIn(codeOnly)) {
                    violations.add(name)
                }
            }
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION (Pass 14 D-AUDIT.6.A-FOLLOWUP write-firewall):
            sim commonMain writes `knownStrips` outside the allowlisted
            handler sites.

            Allowed write sites: $allowedFiles
            Violating files: $violations

            Fix: route the write through `Step.handleFlightPlanFiled` or
            `Step.applyContactFrequency`, or amend the firewall via plan
            revision.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
