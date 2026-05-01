package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test (E6) — `:sim`'s pilot-tick path does not
 * read controller beliefs.
 *
 * The build graph forbids `:pilot` from reading `:controller` types — but
 * `:sim` legitimately depends on both, and `Step.kt:handlePilotTick`
 * *could* synthesise [PilotInput] fields from [BeliefState] (the very leak
 * the pilot-firewall plan deletes). The defence at the integration layer:
 * any function in `Step.kt` that calls `pilotDecide(` or `buildPilotInput(`
 * must not also reference `state.beliefs` — that combination is the leak
 * pattern.
 *
 * This is the regression guard for the deleted `Step.kt:125-127` lookup.
 * The test is intentionally narrow: it doesn't forbid `state.beliefs`
 * everywhere in `:sim` (that would be wrong — `ControllerWiring` reads
 * beliefs legitimately), only in functions on the pilot's path.
 *
 * **Implementation note**: Kotlin function bodies are extracted by a
 * brace-matching scan of the file. A Kotlin parser would be more robust;
 * the brace counter here is a pragmatic approximation that catches the
 * pattern of interest (single-pass over `Step.kt`). False positives are
 * preferred over false negatives — if the pilot path ever needs to read
 * beliefs *for a non-leak reason*, the test fires and forces a deliberate
 * justification.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal.
 */
class FirewallSimPilotTickIsolationTest {

    @Test
    fun `Step pilot-tick path does not read controller beliefs`() {
        val stepFile = projectRoot()
            .resolve("sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/Step.kt")
        val text = Files.readString(stepFile)
        val codeOnly = text
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

        val pilotPathFunctions = extractFunctionBodiesContaining(
            codeOnly,
            triggers = listOf("pilotDecide(", "buildPilotInput("),
        )
        val violations = pilotPathFunctions.filter { (_, body) ->
            beliefsReadPattern.containsMatchIn(body)
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION: Step.kt has pilot-path function(s) that read
            controller beliefs. The pilot must decide only on PilotInput;
            synthesising input fields from `state.beliefs` is the leak pattern
            that pilot-firewall Phase C deleted (the previous Step.kt:125-127
            lookup of `state.beliefs.values…commitments…runway`).

            Offending functions:
            ${violations.joinToString("\n") { "  - ${it.first}" }}

            Fix: move the belief read out of the pilot-path function. If the
            data is genuinely needed by the pilot, route it through radio
            (processInstruction populates mission state) or through FlightStrip
            (filed-plan pre-briefing). If it is genuinely needed by the sim
            integration layer for a non-pilot purpose, factor it into a
            separate function that does not call pilotDecide / buildPilotInput.
            """.trimIndent()
        }
    }

    private val beliefsReadPattern = Regex("""\bstate\.beliefs\b""")

    /**
     * Scan [code] for top-level `private fun` / `fun` / `internal fun` blocks
     * whose body contains any of [triggers]. Returns pairs of (function-name,
     * body-text) so the test can run a secondary check on the body.
     *
     * The brace-matcher is a single-pass approximation; it handles balanced
     * braces but not strings/regex literals containing braces (rare in this
     * file). False-positive risk: if a function body happens to contain a
     * brace-imbalance in a string, the matcher may include too much. False-
     * negative risk: minimal — the leak pattern is `state.beliefs` next to
     * `pilotDecide(`, both being plain identifiers with no embedded braces.
     */
    private fun extractFunctionBodiesContaining(
        code: String,
        triggers: List<String>,
    ): List<Pair<String, String>> {
        // Function-declaration regex permits any combination of modifiers
        // (`private`, `internal`, `public`, `suspend`, `inline`, `operator`,
        // `tailrec`, etc.), an optional generic-parameter block (`<T>`), and
        // an optional receiver type for extension functions
        // (`fun SimState.foo(…)`). The captured group is the function name
        // — that's what we report on a violation. The previous narrow form
        // missed `suspend fun` and extension functions, which would let a
        // future leak slip through silently. False-positive risk: the
        // regex matches identifiers that happen to look like Kotlin function
        // declarations inside string literals — vanishingly rare in our
        // Step.kt and acceptable per the test's "false positives preferred
        // over silent false negatives" stance.
        val funStart = Regex(
            """^\s*(?:(?:private|internal|public|inline|suspend|operator|tailrec|infix|external)\s+)*""" +
                """fun\s*(?:<[^>]*>\s*)?(?:\w+\.)?(\w+)\b""",
            RegexOption.MULTILINE,
        )
        val results = mutableListOf<Pair<String, String>>()
        for (match in funStart.findAll(code)) {
            val nameStart = match.range.last + 1
            val openBrace = code.indexOf('{', nameStart)
            if (openBrace < 0) continue
            // Brace-match to find the function body's closing brace.
            var depth = 1
            var i = openBrace + 1
            while (i < code.length && depth > 0) {
                when (code[i]) {
                    '{' -> depth++
                    '}' -> depth--
                }
                i++
            }
            if (depth != 0) continue
            val body = code.substring(openBrace, i)
            if (triggers.any { it in body }) {
                results.add(match.groupValues[1] to body)
            }
        }
        return results
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
