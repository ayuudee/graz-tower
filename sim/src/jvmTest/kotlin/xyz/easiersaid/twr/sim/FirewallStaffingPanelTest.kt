package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test (E16) — staffing-panel projection content.
 *
 * Pass 6 post-impl (Impact-M.1): the `staffedRoles: Set<RoleName>` channel
 * was a direct sim→controller projection without a typed boundary. This
 * test guards [StaffingPanel] (`sim/.../StaffingPanel.kt`) the same way
 * [FirewallSensorReadingTest] guards [SensorReading].
 *
 * Forbidden patterns target sim-side fields that should NOT be projected
 * through the staffing channel:
 *  - controller identity / workload / session state would let the
 *    controller-side code key on individual controllers;
 *  - aircraft state, pilot mission, weather etc. are all out-of-channel —
 *    if a future engineer wants any of those, they must extend or replace
 *    the projection deliberately.
 *
 * **No-suppression rule**: an architectural test failure is never
 * resolved by `@Disabled` / `@Suppress` / test removal. Fix the violation
 * or amend the firewall via plan revision.
 */
class FirewallStaffingPanelTest {

    @Test
    fun `StaffingPanel reads only role-shaped data`() {
        val source = Files.readString(
            projectRoot().resolve(
                "sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/StaffingPanel.kt",
            ),
        )
        val codeOnly = source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")
        val forbiddenPatterns = listOf(
            // Controller-identity exposure: keying by ControllerId on the
            // controller side means we are tracking individual controllers
            // not just the role. Out of channel.
            """(?<![.\w])ControllerId\b""",
            // Pilot-internal state — never relevant to staffing.
            """(?<![.\w])AircraftState\b""",
            """(?<![.\w])pilotMission\b""",
            """(?<![.\w])PilotMission\b""",
            // Weather / observation / radar — separate channels (sensor /
            // weather / observation surface). Mixing them into the
            // staffing projection is a leak.
            """(?<![.\w])WeatherObservation\b""",
            """(?<![.\w])AircraftObservation\b""",
            """(?<![.\w])SensorReading\b""",
            // Workload / session state — operational meta-info that real
            // controllers can see on the strip board but isn't yet modelled.
            // Reserved here so re-introducing it forces an explicit firewall
            // amendment.
            """(?<![.\w])workload\b""",
            """(?<![.\w])sessionState\b""",
        )
        for (pat in forbiddenPatterns) {
            val regex = Regex(pat)
            check(!regex.containsMatchIn(codeOnly)) {
                """
                FIREWALL VIOLATION: StaffingPanel.kt references forbidden
                sim-side identifier matching $pat.

                The staffing projection carries only role-shaped data per
                the firewall's same-treatment rule (Pass 6 post-impl
                Impact-M.1). If you need this value on the controller side,
                either extend [StaffingPanel] deliberately (and update
                this test's allowlist to match) or add a separate typed
                projection.
                """.trimIndent()
            }
        }
    }

    @Test
    fun `StaffingPanel has exactly one production site`() {
        val simRoot = projectRoot().resolve("sim/src/commonMain/kotlin")
        val producers = mutableListOf<String>()
        Files.walk(simRoot).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val text = Files.readString(file)
                val codeOnly = text.replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                val regex = Regex("""fun\s+[^\n{]*?\)\s*:\s*StaffingPanel\b""")
                regex.findAll(codeOnly).forEach { match ->
                    producers.add("${file.fileName}: ${match.value.trim()}")
                }
            }
        }
        check(producers.size == 1) {
            """
            FIREWALL VIOLATION: StaffingPanel must have exactly one producer
            (toStaffingPanel in StaffingPanel.kt). Found ${producers.size}:
            $producers

            Adding a new producer is a firewall amendment that must be
            reviewed and documented.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
