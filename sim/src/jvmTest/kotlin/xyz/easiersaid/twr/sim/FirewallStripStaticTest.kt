package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test (D-PF.5 closure) — `FlightStrip.kt` reads only
 * filed-plan attributes from `pilotMission`.
 *
 * The strip is a pre-briefing artefact. It carries the filed plan's nature
 * (departure / arrival / circuit-training / transit) plus identity. It must
 * never carry runtime mission state — the dynamic Departing → Arriving
 * transition for circuit-training aircraft post-landing is driven by the
 * controller's radio observation
 * ([xyz.easiersaid.twr.controller.observe.ControllerEvent.AircraftArrivalCommitted]
 * folded into [xyz.easiersaid.twr.controller.observe.BeliefState.aircraftIntent]),
 * not by re-reading the pilot's mission tree.
 *
 * **Allowlist regex** (category-based, not name-based): any access of the
 * form `(pilotMission|mission)\??\.<x>` where `<x>` is not in the filed-plan
 * set `{goal, navigationMode}` is forbidden. The category is "post-filing
 * runtime state" — fields that change as the flight progresses (active
 * compound, current task, last reported leg, route override, contacted-on-
 * frequency, has-clearance, etc.). Forbidding the category structurally,
 * not by enumerating names, catches future drift (renames, new fields)
 * the same way.
 *
 * Structural defence: the function `inferIntentFromGoal(goal: HighLevelGoal?)`
 * takes only the goal — it cannot read mission tree because it has no
 * `mission` argument. This source-text test is belt-and-braces (matching
 * the precedent of [FirewallSensorReadingTest]); the structural defence
 * is `inferIntentFromGoal`'s signature.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Fix the violation or amend
 * the firewall via plan revision.
 */
class FirewallStripStaticTest {

    @Test
    fun `FlightStrip reads only filed-plan attributes from pilotMission`() {
        val source = projectRoot()
            .resolve("sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/FlightStrip.kt")
            .let { Files.readString(it) }
        val codeOnly = source
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

        val accessRe = Regex("""\b(?:pilotMission|mission)\??\.([\w]+)\b""")
        val allowedFields = setOf("goal", "navigationMode")
        val violations = mutableListOf<String>()
        for (match in accessRe.findAll(codeOnly)) {
            val field = match.groupValues[1]
            if (field !in allowedFields) {
                violations.add("${match.value} (field: $field)")
            }
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION: FlightStrip.kt reads non-filed-plan pilotMission
            fields: $violations.

            The strip carries filed-plan attributes only. Allowed: goal,
            navigationMode. Forbidden: every other field on PilotMission
            (post-filing runtime state — root, currentTask, activeRunway,
            activeConstraints, routeOverride, contactedOnFrequency,
            lastReportedLeg, hasClearance, joinLeg, altitudeRestrictionM,
            lastTransmittedStep, stepEnteredAt, reportedVacated).

            The dynamic Departing → Arriving transition is driven by
            ControllerEvent.AircraftArrivalCommitted from radio observation
            (`Report(RunwayVacated)`), not by reading the pilot's mission tree.
            See deferment D-PF.5 in /home/andrew/.claude/plans/pilot-firewall.md
            and the closure in /home/andrew/.claude/plans/fragility-and-strip-dynamism.md.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
