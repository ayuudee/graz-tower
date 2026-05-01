package xyz.easiersaid.twr.controller

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test

/**
 * Architectural enforcement test (E18) — controller commonMain must not
 * reference cross-controller responsibility state until **D-PF.8** lands a
 * proper typed projection on `ControllerView`.
 *
 * Background. Pass 7 (D-AUDIT.5 closure) introduced the sealed
 * [xyz.easiersaid.twr.protocol.ResponsibilityState] machine with three
 * leaves: `Owned`, `HandingOff`, `Watching`. Pass 8's original plan landed
 * a typed `IncomingHandoff` projection on `ControllerView` for the
 * `Watching` slice plus a demonstrative `HasIncomingHandoffFrom` guard.
 * Plan review surfaced this as scaffolding: a projection with no consuming
 * rule, a guard with no firing site. Pass 8 took **Path B** — defer the
 * projection until a real consumer exists, and delete the dead stub the
 * controller view already carried (the always-`emptyList()`
 * `pendingInboundHandoffs` field plus a `ControllerEvent.HandoffOffered`
 * leaf no event-derivation pathway emits).
 *
 * The user's directive: *"Path B, but if anything can exercise it there
 * should be an error."* This test is that error. Until Pass 9+ adds a
 * vetted projection driven by a real consuming rule, no controller-side
 * code may reference [ResponsibilityState], `Watching`, `HandingOff`, or
 * `IncomingHandoff`. The only legitimate way to learn an aircraft is
 * incoming-but-not-yet-on-frequency is the projection that doesn't exist
 * yet — anything else is the same scaffolding pattern Pass 8 rejected.
 *
 * **No-suppression rule.** When D-PF.8's real fix lands, fold this
 * test's allowlist (or delete it) as part of the same plan revision.
 * Suppression / disable / removal in lieu of a plan revision is a
 * firewall corner-cut and is forbidden.
 */
class FirewallNoWatchingReadInControllerTest {

    @Test
    fun `controller commonMain does not reference deferred Watching projection symbols`() {
        val controllerCommon = projectRoot()
            .resolve("controller/src/commonMain/kotlin")
        // Word-boundary regexes — match the symbols as bare identifiers,
        // not as substrings of larger names. KDoc / line comments are
        // stripped before scanning so prose references (e.g. discussing
        // the deferment in a doc comment) don't trip the test.
        val forbidden = listOf(
            Regex("""\bResponsibilityState\b"""),
            Regex("""\bIncomingHandoff\b"""),
            // Watching / HandingOff could collide with incidental names in
            // future code (e.g. a method called `isHandingOff` would still
            // match). Acceptable — the diagnostic names the file and the
            // reviewer can decide whether to widen the allowlist via plan
            // revision. False-positive risk is low because the controller
            // module has no other handoff vocabulary today.
            Regex("""\bWatching\b"""),
            Regex("""\bHandingOff\b"""),
        )
        val violations = mutableListOf<String>()
        Files.walk(controllerCommon).use { stream ->
            stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                val text = Files.readString(file)
                val codeOnly = text
                    .replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                    .replace(Regex("""//[^\n]*"""), "")
                for (pat in forbidden) {
                    pat.findAll(codeOnly).forEach { match ->
                        violations.add("${file.fileName}: pattern ${pat.pattern} matched `${match.value}`")
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION (D-PF.8): controller commonMain references
            cross-controller responsibility state outside an approved
            projection.

            Violations:
            ${violations.joinToString("\n            ")}

            Pass 8 took Path B and deferred the typed Watching projection
            on ControllerView. Until D-PF.8's real fix lands (a paired
            projection + consuming rule, with a corresponding firewall
            test), no controller-side code may reference
            ResponsibilityState, Watching, HandingOff, or IncomingHandoff.
            Adding such a reference is a firewall amendment that requires
            plan revision — not a @Disabled, not a @Suppress, not a test
            allowlist tweak.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
