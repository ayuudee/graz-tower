package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Pass 12 (D-PF.9 / E21) — single-producer + content firewall for the
 * `outgoingMissedHandoffs` projection on `ControllerView`.
 *
 * Two contracts pinned:
 *
 * 1. **Single producer**: exactly one site in `:sim/commonMain` constructs
 *    a `MissedHandoffNotice`. Anything else is a firewall regression
 *    (the projection's purpose is "AFTN-strip-shaped surface from the
 *    sim's authoritative `handoffEscalations` slice"; multiple producers
 *    would re-introduce the dampening collisions Pass 9's
 *    `handoffEscalations` was designed to solve).
 *
 * 2. **Content firewall**: the construction call's named arguments must
 *    map exactly to the strip-shaped allowlist
 *    `(targetRole, targetFrequency, since)`. Adding a pilot-internal
 *    field at the production site (e.g. `pilotMission` peek) is a
 *    firewall regression. Same shape as `FirewallObservationTest` —
 *    canonical-constructor allowlist enforced.
 *
 * **No-suppression rule**: an architectural test failure is never
 * resolved by `@Disabled`, `@Suppress`, or test removal. Resolve by
 * routing through the existing `buildControllerView` projection or
 * amend the firewall via plan revision.
 */
class FirewallOutgoingMissedHandoffsProjectionTest {

    @Test
    fun `MissedHandoffNotice has exactly one production site in sim commonMain`() {
        val simCommon = projectRoot().resolve("sim/src/commonMain/kotlin")
        val pattern = Regex("""\bMissedHandoffNotice\s*\(""")
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
            FIREWALL VIOLATION (Pass 12 D-PF.9 / E21): MissedHandoffNotice
            must have exactly one production site in :sim/commonMain.

            Found ${matches.size} site(s):
            ${matches.joinToString("\n            ")}

            All construction must route through buildControllerView's
            projection of `state.handoffEscalations`. A second producer
            would re-introduce the dampening collisions Pass 9's
            handoffEscalations was designed to solve.
            """.trimIndent()
        }
    }

    @Test
    fun `MissedHandoffNotice content firewall — canonical-constructor allowlist`() {
        // Compile-time gate (mirror of FirewallObservationTest pattern):
        // every named argument here is the firewall allowlist. Adding a
        // pilot-internal field to MissedHandoffNotice fails to compile
        // against this canonical call until the test is updated
        // deliberately (forcing plan-revision review).
        val canonical = xyz.easiersaid.twr.controller.MissedHandoffNotice(
            targetRole = xyz.easiersaid.twr.protocol.RoleName.TOWER,
            targetFrequency = xyz.easiersaid.twr.protocol.Frequency.unsafe("118.200"),
            since = xyz.easiersaid.twr.protocol.SimTime.ZERO,
        )
        @Suppress("UNUSED_VARIABLE")
        val _check = canonical.targetRole
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
