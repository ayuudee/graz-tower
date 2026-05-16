package xyz.easiersaid.twr.pilot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * fn-28.2 (G3a-react-density-altitude R20): `CompletionMode.NON_COMPLETING`
 * consumer-audit pin.
 *
 * **Reflection-exhaustiveness contract** (round-6 Major 3 / round-7 Major 1
 * corrected sites): every site that dispatches on `CompletionMode` MUST
 * have a `NON_COMPLETING` arm. The audit at impl time enumerates the
 * consumer sites via grep (`grep -rE 'when.*CompletionMode|CompletionMode\.'`).
 * This test pins the structural contract:
 *  1. The enum has all six values (PHYSICAL / REPORTED / INSTRUCTION_GATED
 *     / TIMED / INSTANT / NON_COMPLETING).
 *  2. The new NON_COMPLETING value carries the "terminal primitive — no
 *     completion event ever flips its status" semantic; downstream
 *     consumers (PilotCognitive.isStepComplete) return `false` for it
 *     (covered by the PilotDensityAltitudeDeclineTest's "stays active
 *     forever" assertion).
 *
 * A regression that drops the enum value or fails to add the dispatch
 * arm at `isStepComplete` (the actual CompletionMode dispatch site —
 * NOT `isPhysicallyComplete`, which consumes MissionStep) fails the
 * downstream behavioural test rather than this structural pin; this test
 * keeps the enum's shape stable as a documentation anchor.
 *
 * **Audit log** (round-6 Major 3 grep, sites visited at fn-28.2 impl):
 *  - `PilotCognitive.isStepComplete` — CompletionMode dispatch site;
 *    `NON_COMPLETING -> false` arm added.
 *  - `PilotMission.kt` — every `PrimitiveTask` constructor call
 *    enumerated by the `CompletionMode.` grep; NON_COMPLETING uses
 *    are limited to `applyDensityAltitudeDecline` (fn-28.2) and
 *    `applyAbortTakeoff` (fn-28.8/.9) — both via
 *    `replaceFromActivePrimitive`.
 *  - No `when (completionMode)` reflection-exhaustiveness test sites
 *    existed at fn-28.2 land; this file IS that pin.
 */
class CompletionModeNonCompletingAuditSpec {

    @Test
    fun `CompletionMode has six values — PHYSICAL REPORTED INSTRUCTION_GATED TIMED INSTANT NON_COMPLETING`() {
        val values = CompletionMode.values().toSet()
        assertEquals(
            setOf(
                CompletionMode.PHYSICAL,
                CompletionMode.REPORTED,
                CompletionMode.INSTRUCTION_GATED,
                CompletionMode.TIMED,
                CompletionMode.INSTANT,
                CompletionMode.NON_COMPLETING,
            ),
            values,
            "CompletionMode enum must include NON_COMPLETING (fn-28.2 R20) + all pre-fn-28 values",
        )
    }

    @Test
    fun `NON_COMPLETING is enumerated in the CompletionMode value set`() {
        assertTrue(
            CompletionMode.NON_COMPLETING in CompletionMode.values(),
            "NON_COMPLETING must be a CompletionMode enum value (R20)",
        )
    }

    @Test
    fun `NON_COMPLETING is constructible on PrimitiveTask — wired for DECLINE_DEPARTURE + ABORTED`() {
        // fn-28.2: DECLINE_DEPARTURE uses NON_COMPLETING.
        val decline = PrimitiveTask(MissionStep.DECLINE_DEPARTURE, CompletionMode.NON_COMPLETING)
        assertEquals(MissionStep.DECLINE_DEPARTURE, decline.step)
        assertEquals(CompletionMode.NON_COMPLETING, decline.completionMode)
        assertEquals(false, decline.completed, "PrimitiveTask defaults `completed = false`")
        // fn-28.8 will pair NON_COMPLETING with the new ABORTED MissionStep;
        // it is NOT added at fn-28.2 (per task spec — .8 adds ABORTED).
    }
}
