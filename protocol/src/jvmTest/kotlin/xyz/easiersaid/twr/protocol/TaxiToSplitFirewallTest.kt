package xyz.easiersaid.twr.protocol

import kotlin.reflect.KClass
import kotlin.test.Test

/**
 * Architectural enforcement test (E14) — sealed-leaf cardinality for the
 * Pass 6 (D-PF.6) `TaxiTo` split.
 *
 * The original draft proposed a source-text regex on `data class TaxiTo`,
 * but the FP/test reviewers flagged it as scaffold-shaped: it tests
 * *naming*, not *behaviour*, and would silently miss `class TaxiTo`
 * (no `data` modifier) or a `TaxiTo` defined in another file.
 *
 * The real firewall is the type system. This test reflectively asserts:
 *  1. Both [TaxiToHoldingPoint] and [TaxiToStand] exist as sealed leaves
 *     under [GroundInstruction] (the split landed and stayed).
 *  2. No leaf named `TaxiTo` exists (the unified type is gone).
 *
 * Survives renames, lives in the same module as the type, doesn't depend
 * on text patterns.
 *
 * **No-suppression rule** — this is a structural firewall; resolve a
 * failure by fixing the violation, not by `@Disabled`/`@Suppress`/test
 * removal. Re-introducing a unified `TaxiTo` bypasses the
 * runway-explicit firewall — bring it back as a typed split if needed.
 */
class TaxiToSplitFirewallTest {

    @Test
    fun `TaxiTo is split into TaxiToHoldingPoint and TaxiToStand`() {
        val leaves = sealedLeavesOf(GroundInstruction::class)
        val names = leaves.mapNotNull { it.simpleName }.toSet()
        check("TaxiToHoldingPoint" in names && "TaxiToStand" in names) {
            "Pass 6 (D-PF.6 closure) split TaxiTo into TaxiToHoldingPoint + TaxiToStand. " +
                "GroundInstruction leaves found: $names"
        }
        check("TaxiTo" !in names) {
            "FIREWALL VIOLATION: a `TaxiTo` leaf reappeared under GroundInstruction. " +
                "The split (D-PF.6) is the structural firewall — bring back as a typed " +
                "split if needed."
        }
    }

    private fun sealedLeavesOf(root: KClass<out Any>): Set<KClass<*>> {
        val seen = mutableSetOf<KClass<*>>()
        val leaves = mutableSetOf<KClass<*>>()
        fun walk(k: KClass<*>) {
            if (!seen.add(k)) return
            val subs = k.sealedSubclasses
            if (subs.isEmpty()) {
                if (!k.isAbstract && !k.isSealed) leaves.add(k)
            } else {
                subs.forEach { walk(it) }
            }
        }
        walk(root)
        return leaves
    }
}
