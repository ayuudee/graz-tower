package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Pass 9 (D-AUDIT.2 / Phase 9.B) — single-producer enforcement for
 * [SimEvent.MissedHandoffDetected].
 *
 * The sweep is the only legitimate producer of `MissedHandoffDetected`
 * events. A second producer would re-introduce the dampening problem
 * `handoffEscalations` is designed to solve (multiple events for the
 * same handoff in the same cycle from different sites).
 *
 * Detection: scan `:sim/commonMain` for `SimEvent.MissedHandoffDetected(`
 * (constructor invocation). Strip KDoc / block / line comments and
 * string literals before scanning so prose references don't trip the
 * test. Exactly one match must remain — in `Step.kt`'s
 * `sweepHandoffTimeouts` function.
 *
 * **No-suppression rule:** an architectural test failure is never
 * resolved by `@Disabled`, `@Suppress`, or test removal. Resolve by
 * routing the new producer through `sweepHandoffTimeouts` (or amend the
 * firewall via plan revision).
 */
class FirewallMissedHandoffSweepProducerTest {

    @Test
    fun `MissedHandoffDetected has exactly one production site in sim commonMain`() {
        val simCommon = projectRoot().resolve("sim/src/commonMain/kotlin")
        val pattern = Regex("""\bSimEvent\.MissedHandoffDetected\s*\(""")
        val matches = mutableListOf<String>()
        Files.walk(simCommon).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val text = Files.readString(file)
                val codeOnly = text
                    .replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""//[^\n]*"""), "")
                    .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
                    .replace(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), "")
                pattern.findAll(codeOnly).forEach {
                    matches.add("${file.fileName}: ${it.value}")
                }
            }
        }
        check(matches.size == 1) {
            """
            FIREWALL VIOLATION (Pass 9 D-AUDIT.2 / Phase 9.B): MissedHandoffDetected
            must have exactly one production site in :sim/commonMain.

            Found ${matches.size} site(s):
            ${matches.joinToString("\n            ")}

            All emissions must route through sweepHandoffTimeouts so the
            handoffEscalations re-fire dampening applies. A second producer
            would emit duplicate events for the same handoff.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
