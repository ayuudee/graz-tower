package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.reflect.KClass
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
 * Two assertions guard the doctrine:
 *  - **Reflection**: walk the `sealedSubclasses` tree to its concrete
 *    leaves and assert the set is exactly `{Peer, Released}`. Recurses
 *    so an intermediate sealed layer (e.g.
 *    `sealed interface IntraAerodrome : HandoffTarget` with `Peer`
 *    inside) doesn't unground the assertion.
 *  - **Source-text scan**: belt-and-braces against the partial-refactor
 *    window where a procedure rule references a new variant before the
 *    sealed leaf lands. Allow-list-based regex (any `HandoffTarget.X`
 *    where `X` is not `Peer`/`Released` trips) so it generalizes to any
 *    new leaf name (`Foreign`, `Cross`, `Remote`, …).
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
    fun `HandoffTarget transitive concrete leaves are exactly Peer plus Released`() {
        // R5 reflection assertion (per epic spec
        // .flow/specs/fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.md). Walks
        // the `sealedSubclasses` tree to concrete (non-sealed) classes so an
        // intermediate sealed layer doesn't unground the assertion. Source-
        // text approaches (see the sibling assertion below) are belt-and-braces.
        val concreteLeaves = transitiveConcreteSealedLeaves(HandoffTarget::class)
        val leafNames = concreteLeaves
            .map { it.simpleName ?: error("anonymous HandoffTarget concrete leaf: $it") }
            .toSet()
        val expected = setOf("Peer", "Released")
        assertEquals(
            expected,
            leafNames,
            """
            FIREWALL VIOLATION (G2 doctrine): HandoffTarget concrete-leaf set
            is wrong at runtime.

            Found:    $leafNames
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
    fun `no source-text references to HandoffTarget dot leaves outside the allow-list`() {
        // Belt-and-braces sibling to the reflection assertion. Allow-list-
        // based regex catches any new variant name (Foreign, Cross, Remote,
        // External, …) — fixed-symbol "Foreign" alone would not catch a
        // refactor that named the variant differently. Includes the partial-
        // refactor window where a procedure rule references a new variant
        // before the sealed leaf lands.
        val scanRoots = listOf(
            "protocol/src",
            "core/src",
            "controller/src",
            "pilot/src",
            "sim/src",
            "migration/src",
        )
        val allowList = setOf("Peer", "Released")
        // `HandoffTarget.<UpperName>` — captures a leaf reference. The
        // `[A-Z]` first-letter constraint excludes property-style references
        // like `HandoffTarget.kt` (none in source, but defensive).
        val pattern = Regex("""\bHandoffTarget\.([A-Z]\w*)\b""")
        val root = projectRoot()
        val violations: List<String> = scanRoots
            .map { root.resolve(it) }
            .filter { Files.exists(it) }
            .flatMap { rootPath ->
                Files.walk(rootPath).use { stream ->
                    stream
                        .filter { it.toString().endsWith(".kt") }
                        .filter { it.fileName.toString() != "FirewallNoCrossAerodromeHandoffTest.kt" }
                        .map { file ->
                            val codeOnly = stripCommentsAndStrings(Files.readString(file))
                            pattern.findAll(codeOnly)
                                .map { match -> match.groupValues[1] to file }
                                .filter { (leaf, _) -> leaf !in allowList }
                                .map { (leaf, file) ->
                                    "${root.relativize(file)}: HandoffTarget.$leaf"
                                }
                                .toList()
                        }
                        .toList()
                }
            }
            .flatten()
        check(violations.isEmpty()) {
            """
            FIREWALL VIOLATION (G2 doctrine): a `HandoffTarget.X` reference
            outside the allow-list ${allowList} was found in the codebase.
            Cross-aerodrome handoff is modelled as release + procedure-
            following + autonomous initial contact, not as a peer handoff
            to a foreign-aerodrome controller.

            Violations:
            ${violations.joinToString("\n            ")}

            Fix: replace the new leaf reference with `HandoffTarget.Released`
            and route the destination ownership through
            `applyTwoWayCommsEstablished`'s knownStrips arm (Pass 14 + G2
            Phase E). See sim/Step.kt for the post-pilot-initial-contact flip.
            """.trimIndent()
        }
    }

    /**
     * Recursively collect all concrete (non-sealed) sealed-subclass leaves of
     * [root]. A sealed-subclass that is itself sealed is treated as an
     * intermediate node, not a leaf.
     */
    private fun transitiveConcreteSealedLeaves(root: KClass<*>): Set<KClass<*>> {
        val frontier = ArrayDeque<KClass<*>>().apply { add(root) }
        val concrete = mutableSetOf<KClass<*>>()
        while (frontier.isNotEmpty()) {
            val cls = frontier.removeFirst()
            val children = cls.sealedSubclasses
            if (children.isEmpty()) {
                // Concrete leaf (no further sealed children). The root itself
                // is added only if it has no sealed children — which would
                // mean the sealed interface has no implementations, an
                // independently broken state caught by the assertEquals.
                if (cls != root) concrete.add(cls)
            } else {
                frontier.addAll(children)
            }
        }
        return concrete
    }

    private fun stripCommentsAndStrings(text: String): String = text
        .replace(Regex("""/\*\*[\s\S]*?\*/"""), "")
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")
        .replace(Regex("\"\"\"[\\s\\S]*?\"\"\""), "")
        .replace(Regex("\"(?:\\\\.|[^\"\\\\\\n])*\""), "")

    private fun projectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        return generateSequence(cwd) { it.parent }
            .firstOrNull { Files.exists(it.resolve("settings.gradle.kts")) }
            ?: error(
                "could not locate project root (no settings.gradle.kts ancestor of $cwd) — " +
                    "test runner is operating outside the expected layout; this would silently " +
                    "skip the firewall scan, which the no-suppression doctrine forbids.",
            )
    }
}
