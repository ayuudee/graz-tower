package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test (A5 Test 3) — sensor projection content.
 *
 * The `toSensorReading` extension in
 * `sim/src/commonMain/kotlin/.../SensorReading.kt` is the only allowed
 * `AircraftState → SensorReading` projection. It must read only
 * sensor-observable members of [AircraftState] (id, callsign, position,
 * altitude, speed, position-derived entities). Reading any pilot-internal
 * member is a firewall leak.
 *
 * Patterns match a forbidden field name as a *bare identifier* on the
 * extension's implicit receiver (e.g. `pilotGoal`, not `this.pilotGoal` —
 * Kotlin extension functions habitually omit `this.`). The negative
 * lookbehind `(?<![.\w])` rejects dot-access through other receivers and
 * partial-word matches (e.g. `myPilotGoal`); KDoc and `//` comments are
 * stripped before scanning so explanatory references don't trip the regex.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Fix the violation or amend
 * the firewall via plan revision.
 */
class FirewallSensorReadingTest {

    @Test
    fun `toSensorReading reads only sensor-observable AircraftState members`() {
        val source = Files.readString(
            projectRoot().resolve(
                "sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SensorReading.kt",
            ),
        )
        // Strip both KDoc/block comments and `//` line comments before scanning,
        // so explanatory mentions of the forbidden field names don't trip the
        // regex. Comments are the natural place to document the firewall
        // constraint by example.
        val codeOnly = source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")
        val forbiddenPatterns = listOf(
            // `pilotGoal` and `humanPiloted` were structural firewall-leak
            // names; both are now compile-time forbidden by the pilot-side
            // FirewallAircraftStateTest (E5), so a SensorReading-side text
            // scan is redundant with that structural defence. The patterns
            // below remain because they target *legitimate* fields of
            // `AircraftState` whose presence in `SensorReading.kt` would
            // mean the sensor projection is reading pilot-internal state
            // (pilotMission tree, targets, phase, route) — that is the
            // subtle leak class the structural test cannot catch.
            """(?<![.\w])pilotMission\b""",
            """(?<![.\w])targetSpeedMps\b""",
            """(?<![.\w])targetAltitudeM\b""",
            """(?<![.\w])phase\b""",
            """(?<![.\w])route\b""",
            // Pass 5 (D-AUDIT.1 closure): the sensor projection must not
            // derive position-attached entities. A real radar returns a
            // position; the *controller* looks up what's at that position
            // via its own world model. Re-introducing an EntityRef field
            // or an entitiesByPoint lookup here is a regression — the
            // entity derivation moved to AircraftObservation.from() on the
            // controller side. The pattern matches both the type name and
            // the lookup table; KDoc references are stripped before scan.
            """(?<![.\w])EntityRef\b""",
            """(?<![.\w])entitiesByPoint\b""",
        )
        for (pat in forbiddenPatterns) {
            val regex = Regex(pat)
            check(!regex.containsMatchIn(codeOnly)) {
                """
                FIREWALL VIOLATION: SensorReading.kt reads forbidden AircraftState
                member matching $pat.

                Sensor projection must not peek at pilot-internal state. If you
                need this value on the controller side, add a typed
                ControllerEvent populated from a radio transmission and a
                BeliefState slice — see /home/andrew/.claude/plans/deep-mixing-prism.md.
                """.trimIndent()
            }
        }
    }

    @Test
    fun `coords assigns from kinematic state-position only`() {
        // fn-6.2 (R6): pin that `toSensorReading` populates `coords` from
        // the bare-identifier read of `state.position` (the kinematic
        // Cartesian field on AircraftState) — not from a re-derived snap
        // value or a placeholder. The negative half rejects a local-var
        // shadow inside the function body that would silently bypass the
        // bare-identifier firewall (e.g. `val position = positionPoint`
        // followed by `coords = position` would defeat the positive scan
        // by re-purposing the identifier).
        val source = Files.readString(
            projectRoot().resolve(
                "sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SensorReading.kt",
            ),
        )
        // Strip both KDoc/block comments and `//` line comments before
        // scanning, mirroring the existing forbidden-name pipeline above
        // so explanatory references in comments don't trip either regex.
        val codeOnly = source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

        // Positive: somewhere in the file (post-comment-strip) the
        // expression `coords = <ident>` appears, and the captured RHS is
        // exactly `position`. The single production site is
        // `toSensorReading`; if a future refactor splits the projection
        // the `SensorReading has exactly one production site` test
        // catches the duplication.
        val coordsAssign = Regex("""coords\s*=\s*(\w+)""").find(codeOnly)
            ?: error(
                "FIREWALL VIOLATION: SensorReading.kt has no `coords = <ident>` assignment. " +
                    "Expected exactly one, populated from the bare identifier `position`."
            )
        val rhs = coordsAssign.groupValues[1]
        check(rhs == "position") {
            """
            FIREWALL VIOLATION: SensorReading.kt assigns `coords = $rhs`, expected
            `coords = position` (bare identifier on the AircraftState receiver, the
            kinematic Cartesian field). Re-deriving coords from the snap field or
            a recomputed value defeats the kinematic-radar doctrine — fn-6's whole
            reason for existing.
            """.trimIndent()
        }

        // Negative: inside the `toSensorReading` function body there must
        // be no local declaration `val position = …` that would shadow
        // the receiver field. A shadow renders the positive scan
        // meaningless (the captured `position` could resolve to anything).
        val toSensorReadingBody = Regex(
            """fun\s+AircraftState\.toSensorReading\b[\s\S]*?\{([\s\S]*?)\n\}"""
        ).find(codeOnly)?.groupValues?.get(1)
            ?: error(
                "FIREWALL VIOLATION: could not locate `fun AircraftState.toSensorReading` " +
                    "in SensorReading.kt — has the projection moved or been split? See the " +
                    "`SensorReading has exactly one production site` test."
            )
        check(!Regex("""val\s+position\b""").containsMatchIn(toSensorReadingBody)) {
            """
            FIREWALL VIOLATION: `toSensorReading` declares a local `val position`
            that shadows the AircraftState receiver field. The `coords = position`
            firewall pin relies on the bare identifier resolving to the receiver's
            kinematic field; a shadow lets `coords` silently take a different
            value. Remove the shadow or rename it (e.g. `snapPoint`).
            """.trimIndent()
        }
    }

    @Test
    fun `SensorReading has exactly one production site`() {
        // Walk sim/commonMain looking for declarations that return SensorReading.
        // The single production function is `toSensorReading`. Adding another
        // producer is a firewall regression — every new boundary projection
        // must be reviewed.
        val simRoot = projectRoot().resolve("sim/src/commonMain/kotlin")
        val producers = mutableListOf<String>()
        Files.walk(simRoot).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val text = Files.readString(file)
                val codeOnly = text.replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                // Match a function declaration whose RETURN type is SensorReading.
                // The pattern requires the closing paren of the parameter list
                // to precede the colon, which avoids false-positive matches on
                // parameter types like `fun toObservation(reading: SensorReading)`.
                val regex = Regex("""fun\s+[^\n{]*?\)\s*:\s*SensorReading\b""")
                regex.findAll(codeOnly).forEach { match ->
                    producers.add("${file.fileName}: ${match.value.trim()}")
                }
            }
        }
        check(producers.size == 1) {
            """
            FIREWALL VIOLATION: SensorReading must have exactly one producer
            (toSensorReading in SensorReading.kt). Found ${producers.size}:
            $producers

            Adding a new producer is a firewall amendment that must be
            reviewed and documented. See deep-mixing-prism plan.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
