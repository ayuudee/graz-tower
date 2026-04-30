package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test (A5 Test 1) — build-graph firewall.
 *
 * The pilot/ATC firewall is most strongly enforced at the Gradle dependency
 * level: the `controller` module's `commonMain` source set must not declare
 * a compile dependency on `:sim`. If absent, no `xyz.easiersaid.twr.sim.*`
 * type can be imported into controller commonMain at all, regardless of
 * how it's spelled (typealias, FQN, star import). The leak is structurally
 * impossible.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Resolve by fixing the
 * violation, or by formally amending the firewall via plan revision. This
 * rule is the user's no-corners principle applied to architectural
 * enforcement.
 */
class FirewallBuildGraphTest {

    @Test
    fun `controller commonMain does not declare a sim dependency`() {
        val gradleFile = projectRoot().resolve("controller/build.gradle.kts")
        check(Files.exists(gradleFile)) {
            "controller/build.gradle.kts not found at $gradleFile"
        }
        val text = Files.readString(gradleFile)
        check(!text.contains("project(\":sim\")")) {
            """
            FIREWALL VIOLATION: controller/build.gradle.kts declares a sim dependency.
            Controller must not have compile-time access to sim types — the firewall
            is structural at the dependency-graph level.

            Fix: remove the implementation(project(":sim")) declaration. If you need
            to share data between sim and controller, route through:
              - protocol module (wire-format types like CircuitIntent, ReportEvent),
              - core module (shared world model),
              - the typed boundary projections (SensorReading, FlightStrip).
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
