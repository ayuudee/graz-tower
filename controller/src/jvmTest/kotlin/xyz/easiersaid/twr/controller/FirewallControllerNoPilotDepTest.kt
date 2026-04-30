package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test (E1b) — controller module must not depend on `:pilot`.
 *
 * Symmetric counterpart of `FirewallPilotDependencyTest` (in `:pilot`). The
 * pilot firewall establishes inviolable separation in **both** directions:
 *
 *   - `:pilot` ↛ `:controller` (no pilot peeks at controller state)
 *   - `:controller` ↛ `:pilot` (no controller peeks at pilot state)
 *
 * `:sim` is the integration layer that depends on both; neither sees the
 * other. The existing `FirewallBuildGraphTest` already guards
 * `:controller` ↛ `:sim`. This test extends parity to `:pilot`.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal.
 */
class FirewallControllerNoPilotDepTest {

    @Test
    fun `controller commonMain does not declare a pilot dependency`() {
        val gradleFile = projectRoot().resolve("controller/build.gradle.kts")
        check(Files.exists(gradleFile)) {
            "controller/build.gradle.kts not found at $gradleFile"
        }
        val text = Files.readString(gradleFile)
        check(!text.contains("project(\":pilot\")")) {
            """
            FIREWALL VIOLATION: controller/build.gradle.kts declares a pilot dependency.
            Inviolable separation requires both directions: pilot ↛ controller AND
            controller ↛ pilot. Sim is the integration layer that depends on both;
            neither agent module sees the other.

            Fix: remove the implementation(project(":pilot")) declaration. If the
            controller needs to consume pilot-derived data, route through:
              - protocol module (wire-format types — radio is the legitimate channel),
              - the typed boundary (SensorReading + FlightStrip, populated by :sim),
              - the typed event fold (ControllerEvent, derived from radio messages).
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
