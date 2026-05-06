package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Pass 11 (D-AUDIT.6) — stale-import firewall.
 *
 * `AircraftIntent` lifted from `:controller/observe` to `:protocol`.
 * A partial revert (or an IDE auto-import that resurrects the old FQN)
 * would re-introduce the controller-side type without compiling against
 * the protocol-side enum, masking source-of-truth drift.
 *
 * This test scans all `.kt` files under `:protocol`, `:controller`,
 * `:pilot`, and `:sim` for the historical FQN. The post-Pass-11 type
 * lives at `xyz.easiersaid.twr.protocol.AircraftIntent` — anything else
 * trips the firewall with a D-AUDIT.6 diagnostic.
 *
 * **No-suppression rule:** an architectural test failure is never
 * resolved by `@Disabled`, `@Suppress`, or test removal. Fix the
 * violation by importing `xyz.easiersaid.twr.protocol.AircraftIntent`,
 * or amend the firewall via plan revision.
 */
class FirewallNoStaleAircraftIntentTest {

    @Test
    fun `no references to controller dot observe dot AircraftIntent remain`() {
        val scanRoots = listOf(
            "protocol/src",
            "controller/src",
            "pilot/src",
            "sim/src",
        )
        val pattern = Regex("""\bcontroller\.observe\.AircraftIntent\b""")
        val violations = mutableListOf<String>()
        val root = projectRoot()
        for (rel in scanRoots) {
            val rootPath = root.resolve(rel)
            if (!Files.exists(rootPath)) continue
            Files.walk(rootPath).use { stream ->
                stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                    val name = file.fileName.toString()
                    // Skip this firewall test itself.
                    if (name == "FirewallNoStaleAircraftIntentTest.kt") return@forEach
                    val text = Files.readString(file)
                    // Strip comments + string literals so historical
                    // documentation mentions don't trip the test.
                    val codeOnly = text
                        .replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                        .replace(Regex("""//[^\n]*"""), "")
                        .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
                        .replace(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), "")
                    pattern.findAll(codeOnly).forEach { match ->
                        val displayPath = root.relativize(file).toString()
                        violations.add("$displayPath: ${match.value}")
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION (D-AUDIT.6): stale `controller.observe.AircraftIntent`
            reference detected. The type was lifted to
            `xyz.easiersaid.twr.protocol.AircraftIntent` in Pass 11 — every
            consumer must import from `:protocol`.

            Violations:
            ${violations.joinToString("\n            ")}

            Fix: change `import xyz.easiersaid.twr.controller.observe.AircraftIntent`
            to `import xyz.easiersaid.twr.protocol.AircraftIntent`. The type
            shape is identical (sealed interface with three data-objects).
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
