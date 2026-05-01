package xyz.easiersaid.twr.pilot

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test (E1) — pilot module build-graph firewall.
 *
 * The pilot/ATC firewall is most strongly enforced at the Gradle dependency
 * level: the `pilot` module's `commonMain` source set must not declare a
 * compile dependency on `:controller` or `:sim`. If absent, no
 * `xyz.easiersaid.twr.controller.*` or `xyz.easiersaid.twr.sim.*` type can
 * be imported into pilot commonMain at all, regardless of how it's spelled
 * (typealias, FQN, star import). The leak is structurally impossible.
 *
 * The mirror direction is enforced by `FirewallControllerDependencyTest`
 * (in `:controller`) — `:controller` must not depend on `:pilot`. Both
 * directions together establish inviolable separation; `:sim` is the
 * integration layer that depends on both, and neither sees the other.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Resolve by fixing the
 * violation, or by formally amending the firewall via plan revision. This
 * rule is the user's no-corners principle applied to architectural
 * enforcement.
 */
class FirewallPilotDependencyTest {

    @Test
    fun `pilot commonMain does not declare a controller dependency`() {
        val gradleFile = projectRoot().resolve("pilot/build.gradle.kts")
        check(Files.exists(gradleFile)) { "pilot/build.gradle.kts not found at $gradleFile" }
        val text = Files.readString(gradleFile)
        check(!text.contains("project(\":controller\")")) {
            """
            FIREWALL VIOLATION: pilot/build.gradle.kts declares a controller dependency.
            Pilot must not have compile-time access to controller types — the firewall
            is structural at the dependency-graph level.

            Fix: remove the implementation(project(":controller")) declaration. If you
            need to share data between pilot and controller, route through:
              - protocol module (wire-format types like AtcInstruction, PilotTransmission),
              - core module (shared world model),
              - the typed boundary (PilotInput on the pilot side; SensorReading + FlightStrip
                on the controller side; both populated by :sim wiring).
            """.trimIndent()
        }
    }

    @Test
    fun `pilot commonMain does not declare a sim dependency`() {
        val gradleFile = projectRoot().resolve("pilot/build.gradle.kts")
        val text = Files.readString(gradleFile)
        check(!text.contains("project(\":sim\")")) {
            """
            FIREWALL VIOLATION: pilot/build.gradle.kts declares a sim dependency.
            Pilot is the agent; sim is the integration layer that drives agents.
            Sim depends on pilot (and controller); pilot does not depend on sim.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
