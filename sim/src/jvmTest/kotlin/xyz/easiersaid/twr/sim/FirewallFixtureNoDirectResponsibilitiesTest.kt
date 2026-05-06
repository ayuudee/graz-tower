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
 * **Scope (post-impl review M.1)**: scans every `.kt` file under
 * `:sim/jvmTest` and `:sim/commonTest` — not just `Fixture.kt` /
 * `Fixtures.kt`. A future test that constructs `SimState.initial(...)`
 * inline with non-empty responsibilities (or calls
 * `ControllerSpec.withOwned(... ownedAircraft = setOf(ac))` in the test
 * body) bypasses the original narrow scan.
 *
 * **Allowlisted files** — spec tests of state-machine behaviour
 * legitimately construct `Owned()` directly (Pass 7 / Pass 9):
 *  - `ResponsibilityStateMachineSpec.kt`
 *  - `ResponsibilityInvariantSpec.kt`
 *  - `RadarServiceTerminatedSpec.kt`
 *  - `MissedHandoffEventSpec.kt`
 *  - `FlightPlanFilingSpec.kt` (Pass 11's own spec — exercises the
 *    handler with hand-built states)
 *  - `ReadbackCorrectionRoundTripTest.kt` (legacy spec, not migrated)
 *  - `MultiAerodromeWorldTest.kt` (legacy spec, not migrated)
 *
 * **Allowlist shape** (negative lookahead): the only permitted assignment
 * to `responsibilities` in scanned code is `= emptyMap()`. Everything
 * else trips the regex with a D-AUDIT.10 diagnostic.
 *
 * **No-suppression rule:** an architectural test failure is never
 * resolved by `@Disabled`, `@Suppress`, or test removal. Fix the
 * violation by filing the plan via `Fixture.flightPlans` +
 * `SimEvent.FlightPlanFiled`, or amend the firewall via plan revision
 * (e.g. add a new spec-test file to the allowlist with a named reason).
 */
class FirewallFixtureNoDirectResponsibilitiesTest {

    private val allowedSpecFiles = setOf(
        "ResponsibilityStateMachineSpec.kt",
        "ResponsibilityInvariantSpec.kt",
        "RadarServiceTerminatedSpec.kt",
        "MissedHandoffEventSpec.kt",
        "FlightPlanFilingSpec.kt",
        "ReadbackCorrectionRoundTripTest.kt",
        // Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13): spec tests
        // exercising handoff and cross-aerodrome filing semantics — they
        // construct ResponsibilityState shapes hand to validate state
        // transitions, not as a fixture cheat.
        "KnownStripsHandoffTransitionSpec.kt",
    )

    @Test
    fun `fixtures and integration tests must file plans, not pre-populate responsibilities`() {
        val scanRoots = listOf(
            "sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim",
            "sim/src/commonTest/kotlin/xyz/easiersaid/twr/sim",
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
        for (rel in scanRoots) {
            val rootPath = root.resolve(rel)
            if (!Files.exists(rootPath)) continue
            Files.walk(rootPath).use { stream ->
                stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                    val name = file.fileName.toString()
                    // Skip the firewall test itself — it's allowed to mention
                    // the patterns it forbids.
                    if (name == "FirewallFixtureNoDirectResponsibilitiesTest.kt") return@forEach
                    if (name in allowedSpecFiles) return@forEach
                    val text = Files.readString(file)
                    // Strip KDoc, block comments, line comments, raw strings,
                    // regular strings — prose mentions don't trip the test.
                    val codeOnly = text
                        .replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                        .replace(Regex("""//[^\n]*"""), "")
                        .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
                        .replace(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), "")
                    for ((label, pat) in patterns) {
                        pat.findAll(codeOnly).forEach { match ->
                            val displayPath = root.relativize(file).toString()
                            violations.add("$displayPath: [$label] ${match.value.trim()}")
                        }
                    }
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
            with a `FiledPlan` value. Pass 14 (D-AUDIT.6.A-FOLLOWUP /
            .6.B-FOLLOWUP / .13): `AftnRouting.routeFiledPlan` computes
            the recipient list from the plan + world; the loader emits
            N `SimEvent.FlightPlanFiled` events per entry in
            `LoadedFixture.initialEvents`, and the test driver enqueues
            those alongside its own ticks.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
