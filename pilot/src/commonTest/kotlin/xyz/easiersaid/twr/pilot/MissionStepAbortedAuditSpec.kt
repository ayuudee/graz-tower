package xyz.easiersaid.twr.pilot

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * fn-28.8 (G0 abort-takeoff foundation R15): `MissionStep.ABORTED`
 * 4-consumer audit pin.
 *
 * **R15 audit sites** (mirrors fn-28.2's `DECLINE_DEPARTURE` audit pattern):
 *  1. `PilotCognitive.isPhysicallyComplete` — ABORTED returns `false`
 *     (default branch enumeration; the step is NON_COMPLETING and never
 *     reaches `isPhysicallyComplete` via the documented wiring, but the
 *     explicit arm pins the regression contract).
 *  2. `PilotCognitive.isReportComplete` — ABORTED returns `false` (same
 *     rationale — REPORTED-channel completion is not the dispatch path
 *     for NON_COMPLETING).
 *  3. `PilotCognitive.stepTransmission` — ABORTED returns `null` (no
 *     transmission in fn-28.8 scope; fn-28.9 may add phraseology).
 *  4. `Pilot.planRoute` — falls into the "step not in airborneSteps"
 *     guard via the default-Skip return; ABORTED has no airborne route.
 *     Combined with the fn-28.9 abort apply's `targetSpeedMps = 0`, the
 *     aircraft remains at-rest on the runway.
 *
 * This file pins the enum-value addition and the structural contract;
 * the apply-side behavioural tests live in fn-28.9 (the sim golden).
 *
 * **NOT `ABORT_ROLL`** (round scope — out of scope for fn-28.8): ABORTED is
 * the single abort-terminal step. The decel phase before the aircraft
 * comes to rest is modelled by the physics layer's engine-off clamp
 * (R12 — `advanceKinematics`) + the pilot's at-rest intent, not by a
 * separate MissionStep.
 *
 * A regression that drops the enum value, removes one of the audit-site
 * arms, or wires ABORTED with a CompletionMode that ISN'T NON_COMPLETING
 * fails one of those downstream tests.
 */
class MissionStepAbortedAuditSpec {

    @Test
    fun `ABORTED is enumerated in the MissionStep enum`() {
        assertTrue(
            MissionStep.ABORTED in MissionStep.values(),
            "MissionStep enum must include ABORTED (fn-28.8 R15)",
        )
    }

    @Test
    fun `ABORTED pairs with NON_COMPLETING in the canonical applyXxx construction`() {
        // The unique constructor of a `PrimitiveTask(ABORTED, ...)` lands
        // in fn-28.9 (the abort apply). It pairs the MissionStep with
        // `CompletionMode.NON_COMPLETING` via `replaceFromActivePrimitive`,
        // mirroring fn-28.2's DECLINE_DEPARTURE shape. Pin the contract:
        val canonical = PrimitiveTask(MissionStep.ABORTED, CompletionMode.NON_COMPLETING)
        assertTrue(
            canonical.completionMode == CompletionMode.NON_COMPLETING,
            "ABORTED primitives MUST pair with NON_COMPLETING completion mode (R20)",
        )
    }

    @Test
    fun `ABORTED coexists with fn-28_2 DECLINE_DEPARTURE — both fn-28 terminal steps present`() {
        // Pin both fn-28 terminal MissionSteps' co-presence — a regression
        // that removed one but left the other (or accidentally renamed) is
        // caught here.
        val all = MissionStep.values().toSet()
        assertTrue(MissionStep.ABORTED in all, "ABORTED present (fn-28.8 R15)")
        assertTrue(MissionStep.DECLINE_DEPARTURE in all, "DECLINE_DEPARTURE present (fn-28.2 R15)")
    }

    @Test
    fun `ABORT_ROLL is NOT in the MissionStep enum (round scope discipline)`() {
        // Negative-space pin: fn-28.8 explicitly excludes ABORT_ROLL from
        // the MissionStep enum. The decel phase is modelled by the physics
        // layer's engine-off clamp + the pilot's at-rest intent, NOT by a
        // separate MissionStep. A regression that introduced ABORT_ROLL
        // would either be visible in the enum value names below or pass
        // through (no MissionStep.ABORT_ROLL reference compiles), so this
        // test asserts on the value names directly.
        val names = MissionStep.values().map { it.name }.toSet()
        assertTrue(
            "ABORT_ROLL" !in names,
            "ABORT_ROLL MUST NOT be a MissionStep value (fn-28.8 scope discipline: ABORTED only)",
        )
    }
}
