package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.protocol.HandoffTarget

/**
 * G2 Phase G — cross-aerodrome handoff syntactic firewall.
 *
 * The cross-aerodrome doctrine (G2 design memo, project memory
 * "transmission_architecture") models inter-aerodrome flows as
 * **release + procedure-following + autonomous initial contact** at the
 * destination's published contact REP. There is no peer handoff between
 * controllers at different aerodromes. The transferring controller's
 * options at the boundary are:
 *  - `HandoffTarget.Peer(controllerId)` — handoff to a peer at the SAME
 *    aerodrome (intra-aerodrome flow, e.g., GROUND → TOWER, TOWER →
 *    APPROACH);
 *  - `HandoffTarget.Released` — boundary release per ICAO Doc 4444
 *    §10.1.4; the aircraft proceeds without a successor controller.
 *
 * Adding a third leaf such as `HandoffTarget.Foreign(aerodromeId)` would
 * make cross-aerodrome handoff syntactically expressible — and the
 * receiving aerodrome's controllers would then need a handoff-acceptance
 * codepath that doesn't exist (the cross-aerodrome `Watching` flip is
 * driven by Pass 14's `knownStrips` + `applyTwoWayCommsEstablished`, not
 * by a peer handoff). Such a variant could not be implemented without
 * collapsing the firewall.
 *
 * This test scans `HandoffTarget.kt` for the sealed interface's leaves
 * and asserts the set is **exactly** `{Peer, Released}`. A regression
 * (someone adds a third variant) trips the firewall before the change
 * lands.
 *
 * **No-suppression rule:** an architectural test failure is never
 * resolved by `@Disabled`, `@Suppress`, or test removal. Fix the
 * violation by removing the new leaf, or amend the firewall via
 * doctrine revision (which requires updating the G2 design memo,
 * project memory `transmission_architecture`, and Pass 14's
 * `applyTwoWayCommsEstablished` knownStrips arm in lockstep).
 */
class FirewallNoCrossAerodromeHandoffTest {

    @Test
    fun `HandoffTarget sealed interface has exactly Peer plus Released leaves`() {
        // R5 reflection assertion (per epic spec
        // .flow/specs/fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.md): the
        // Kotlin reflection API gives the runtime-authoritative leaf set,
        // catching any new variant regardless of which file defines it.
        // Source-text approaches (see the sibling assertion below) are
        // belt-and-braces.
        val leaves = HandoffTarget::class.sealedSubclasses
            .mapNotNull { it.simpleName }
            .toSet()
        val expected = setOf("Peer", "Released")
        assertEquals(
            expected,
            leaves,
            """
            FIREWALL VIOLATION (G2 doctrine): HandoffTarget sealed interface
            has the wrong leaf set at runtime.

            Found:    $leaves
            Expected: $expected

            Cross-aerodrome handoff is modelled as release + procedure-
            following + autonomous initial contact at the destination's
            published contact REP — NOT as a peer handoff. The
            `applyTwoWayCommsEstablished` flow's knownStrips arm (Pass 14)
            takes ownership at the destination via a separate codepath
            from peer handoff, and there is no implementation for any
            other handoff target.

            If a third leaf is genuinely needed, the doctrine change
            requires updating, in lockstep:
              - the G2 design memo (`docs/design/g2-*.md`)
              - project memory `transmission_architecture`
              - `applyTwoWayCommsEstablished`'s knownStrips arm (sim/Step.kt)
              - `routeFiledPlan` distribution (sim/AftnRouting.kt)
              - this test's expected set
            """.trimIndent(),
        )
    }

    @Test
    fun `no source-text references to HandoffTarget dot Foreign across modules`() {
        // Belt-and-braces sibling to the leaf-set assertion: scan all
        // modules for the literal `HandoffTarget.Foreign` symbol. A
        // partially-applied refactor that adds `Foreign` and references
        // it from a procedure rule (without yet adding it to the sealed
        // interface) would still trip this test.
        val scanRoots = listOf(
            "protocol/src",
            "controller/src",
            "pilot/src",
            "sim/src",
        )
        val pattern = Regex("""\bHandoffTarget\.Foreign\b""")
        val violations = mutableListOf<String>()
        val root = projectRoot()
        for (rel in scanRoots) {
            val rootPath = root.resolve(rel)
            if (!Files.exists(rootPath)) continue
            Files.walk(rootPath).use { stream ->
                stream.filter { it.toString().endsWith(".kt") }.forEach { file ->
                    val name = file.fileName.toString()
                    if (name == "FirewallNoCrossAerodromeHandoffTest.kt") return@forEach
                    val text = Files.readString(file)
                    val codeOnly = text
                        .replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
                        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                        .replace(Regex("""//[^\n]*"""), "")
                        .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
                        .replace(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), "")
                    pattern.findAll(codeOnly).forEach { match ->
                        val displayPath = root.relativize(file).toString()
                        violations.add("$displayPath: ${match.value}")
                    }
                }
            }
        }
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION (G2 doctrine): a `HandoffTarget.Foreign`
            reference was found in the codebase. Cross-aerodrome handoff
            is modelled as release + procedure-following + autonomous
            initial contact, not as a peer handoff to a foreign-aerodrome
            controller.

            Violations:
            ${violations.joinToString("\n            ")}

            Fix: replace `HandoffTarget.Foreign(...)` with
            `HandoffTarget.Released` and route the destination ownership
            through `applyTwoWayCommsEstablished`'s knownStrips arm
            (Pass 14 + G2 Phase E). See sim/Step.kt for the post-pilot-
            initial-contact flip.
            """.trimIndent()
        }
    }

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return if (Files.exists(cwd.resolve("settings.gradle.kts"))) cwd
            else cwd.parent ?: cwd
    }
}
