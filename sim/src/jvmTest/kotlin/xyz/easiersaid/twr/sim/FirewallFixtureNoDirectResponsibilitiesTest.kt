package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Pass 11 (D-AUDIT.10) — fixture-cheat firewall (E20).
 *
 * Pre-Pass-11 the LOWG `Fixture` injected `groundResponsibilities =
 * setOf(AircraftId("OE-ABC"))` directly into `ControllerSpec.withOwned(...)`,
 * collapsing AFTN strip-arrival into test-setup. Pass 11 closes the cheat
 * by routing every initial responsibility through `SimEvent.FlightPlanFiled`.
 *
 * This test scans `Fixture.kt` and `Fixtures.kt` for the forbidden patterns.
 * `ControllerSpec.withOwned(...)` itself stays — spec tests
 * (`ResponsibilityStateMachineSpec` etc.) still need it for hand-built
 * state-machine rows. The firewall scopes to **fixture-load code**, not
 * all `withOwned` callers.
 *
 * **Allowlist shape** (negative lookahead): the only permitted assignment
 * to `responsibilities` in fixture code is `= emptyMap()`. Everything else
 * trips the regex with a D-AUDIT.10 diagnostic.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Fix the violation by
 * filing the plan via `Fixture.flightPlans` + `SimEvent.FlightPlanFiled`,
 * or amend the firewall via plan revision.
 */
class FirewallFixtureNoDirectResponsibilitiesTest {

    @Test
    fun `fixtures must file plans, not pre-populate responsibilities`() {
        val files = listOf(
            "sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixture.kt",
            "sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt",
        )
        // Negative-lookahead allowlist: `responsibilities = emptyMap()` only.
        // `responsibilities = mapOf(...)`, `LinkedHashMap()`, `expected.responsibilities`
        // all trip the regex. **`\s*` is INSIDE the lookahead** so that
        // backtrack-greedy whitespace shrinkage can't bypass the check.
        val responsibilitiesAssign = Regex("""\bresponsibilities\s*=(?!\s*emptyMap\(\))""")
        // `withOwned(... ownedAircraft = <non-empty>)` is the precise cheat
        // shape. Allow `ownedAircraft = emptySet()` (the new fixture-load
        // shape after Pass 11).
        val withOwnedNonEmpty = Regex(
            """\bwithOwned\s*\([\s\S]*?\bownedAircraft\s*=(?!\s*emptySet\(\))""",
        )
        // Direct `ResponsibilityState.Owned(...)` construction — fixtures
        // should never reach for this. Spec tests in other files may.
        val ownedConstruct = Regex("""\bResponsibilityState\.Owned\s*\(""")

        val patterns = listOf(
            "responsibilities-assign" to responsibilitiesAssign,
            "withOwned-non-empty" to withOwnedNonEmpty,
            "Owned-construct" to ownedConstruct,
        )
        val violations = mutableListOf<String>()
        val root = projectRoot()
        for (rel in files) {
            val path = root.resolve(rel)
            check(Files.exists(path)) { "fixture file missing: $rel" }
            val text = Files.readString(path)
            // Strip KDoc, block comments, line comments, and string literals
            // before scanning so prose mentions don't trip the test.
            val codeOnly = text
                .replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                .replace(Regex("""//[^\n]*"""), "")
                .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
                .replace(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), "")
            for ((label, pat) in patterns) {
                pat.findAll(codeOnly).forEach { match ->
                    violations.add("$rel: [$label] ${match.value.trim()}")
                }
            }
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION (D-AUDIT.10): fixture-load code is pre-populating
            controller responsibilities directly. Use SimEvent.FlightPlanFiled
            via Fixture.flightPlans instead.

            Violations:
            ${violations.joinToString("\n            ")}

            Fix: declare the aircraft in `Fixture.flightPlans = mapOf(...)`
            with a `FiledPlanForFixture(plan, recipient)`. The loader emits
            a `SimEvent.FlightPlanFiled` per entry in `LoadedFixture.initialEvents`,
            and the test driver enqueues those alongside its own ticks.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
