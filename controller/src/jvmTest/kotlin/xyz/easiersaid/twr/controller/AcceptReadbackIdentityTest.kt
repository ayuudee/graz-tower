package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Pass 12 (D-AUDIT.2.E follow-on F.3) — identity-preservation invariant
 * for the `processReadback` → `acceptReadback` path.
 *
 * Pre-Pass-12, `acceptReadback` did `coords.filterIndexed { i, _ -> i != correctIdx }`
 * to compute `remaining`. This was a latent bug: when `processReadback`
 * filtered `coords` (Issued-only pre-Pass-12), the index was into the
 * filtered list, and the filtered-list-minus-matched then OVERWROTE
 * `beliefs.coordinations[ac]`, silently destroying unfiltered entries.
 *
 * Pass 12 fixes the bug: `acceptReadback` now removes by *identity*
 * (`it !== confirmed`) against the original list. This invariant
 * depends on the same `OutstandingCoordination` reference flowing
 * through the read path. A future refactor that does
 * `coords.map { it.copy() }` (defensive copy) anywhere on the path
 * silently breaks identity matching — a duplicate-instruction confirm
 * could appear to work but remove the wrong entry.
 *
 * This source-text scan asserts that no defensive-copy pattern exists
 * on the `coords`-flow inside `processReadback` / `acceptReadback`.
 * Specifically: no `.map { ... .copy(` pattern on a `coords` or
 * `coordinations` receiver inside `Controller.kt`'s readback fold.
 *
 * **No-suppression rule**: an architectural test failure is never
 * resolved by `@Disabled`, `@Suppress`, or test removal. Resolve by
 * either avoiding the defensive-copy or migrating the path to a
 * stable per-coordination identifier (`OutstandingCoordination.coordinationId`,
 * mooted in plan-review F.3).
 */
class AcceptReadbackIdentityTest {

    @Test
    fun `processReadback path uses identity-preserving filter — no defensive copy`() {
        val controllerKt = projectRoot()
            .resolve("controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/Controller.kt")
        val text = Files.readString(controllerKt)
        // Strip comments + strings.
        val codeOnly = text
            .replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")
            .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
            .replace(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), "")

        // Forbidden: defensive copy on coords/coordinations receiver.
        // Pattern: `<receiver>.map { ... .copy(` where receiver is one
        // of `coords`, `coordinations`, `originalCoords`.
        val forbidden = Regex(
            """\b(coords|coordinations|originalCoords)\b\s*\.\s*map\s*\{[\s\S]*?\.copy\s*\(""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val matches = forbidden.findAll(codeOnly).toList()
        check(matches.isEmpty()) {
            """
            FIREWALL VIOLATION (Pass 12 D-AUDIT.2.E follow-on F.3):
            defensive-copy pattern detected on the readback fold's coords
            receiver. acceptReadback removes by identity (`it !== confirmed`);
            a `coords.map { ... copy(...) }` upstream silently breaks the
            identity match.

            Matches:
            ${matches.joinToString("\n            ") { it.value.trim().take(120) }}

            Fix: avoid the defensive copy, OR migrate to a stable
            per-coordination identifier. The latter is a larger refactor
            (introduce `OutstandingCoordination.coordinationId`); plan-review
            mooted it as a follow-up.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
