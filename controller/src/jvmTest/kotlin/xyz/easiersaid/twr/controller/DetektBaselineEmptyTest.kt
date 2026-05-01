package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Pass 1 verification gate (per the Pass 1 plan, Item "Detekt baseline-empty
 * literal assertion"): the detekt baseline file `detekt-baseline.xml` must
 * have an empty `<CurrentIssues>` element.
 *
 * Why a separate test rather than relying on `./gradlew detekt` to fail:
 * detekt passes if the baseline *contains* the findings (suppressing them).
 * Without this assertion, a future "regenerate the baseline" commit silently
 * undoes Pass 1's no-carry-forward gate. Detekt build passing is necessary
 * but insufficient.
 *
 * **No-suppression rule:** an architectural test failure is never resolved
 * by `@Disabled`, `@Suppress`, or test removal. Resolve by addressing the
 * findings in the baseline (real fix or `@Suppress` with rationale at the
 * call site). Adding an entry to baseline is a corners-cut.
 */
class DetektBaselineEmptyTest {

    @Test
    fun `detekt-baseline xml CurrentIssues element is empty`() {
        val baseline = projectRoot().resolve("detekt-baseline.xml")
        check(Files.exists(baseline)) {
            "detekt-baseline.xml not found at ${baseline.toAbsolutePath()}. " +
                "Detekt's baseline file must exist (even if empty)."
        }
        val text = Files.readString(baseline)

        // Match either `<CurrentIssues></CurrentIssues>` or `<CurrentIssues/>` or
        // an absent element. Anything else (a non-empty body) is a violation.
        val emptyTagRegex = Regex("""<CurrentIssues\s*/>""")
        val emptyOpenCloseRegex = Regex("""<CurrentIssues>\s*</CurrentIssues>""")

        if (emptyTagRegex.containsMatchIn(text) || emptyOpenCloseRegex.containsMatchIn(text)) {
            return // empty — pass
        }

        // If we reach here, the element has children. Extract them for the failure message.
        val nonEmptyRegex = Regex("""<CurrentIssues>([\s\S]*?)</CurrentIssues>""")
        val match = nonEmptyRegex.find(text)
        val body = match?.groupValues?.getOrNull(1)?.trim() ?: "(unparseable)"
        throw AssertionError(
            """
            BASELINE NON-EMPTY: detekt-baseline.xml's <CurrentIssues> contains entries.
            Per the Pass 1 plan's no-carry-forward rule, the baseline must be empty.
            Resolve each entry by either fixing the code or adding @Suppress with
            rationale at the call site, then remove the entry from the baseline.

            Baseline contents:
            $body
            """.trimIndent()
        )
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
