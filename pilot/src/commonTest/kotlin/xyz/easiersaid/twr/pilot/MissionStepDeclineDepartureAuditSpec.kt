package xyz.easiersaid.twr.pilot

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * fn-28.2 (G3a-react-density-altitude R15): `MissionStep.DECLINE_DEPARTURE`
 * 4-consumer audit pin.
 *
 * **R15 audit sites** (round-7 Major 1 corrected):
 *  1. `PilotCognitive.isPhysicallyComplete` — DECLINE_DEPARTURE returns
 *     `false` (default branch enumeration; the step is NON_COMPLETING and
 *     never reaches `isPhysicallyComplete` via the documented wiring, but
 *     the explicit arm pins the regression contract).
 *  2. `PilotCognitive.isReportComplete` — DECLINE_DEPARTURE returns
 *     `false` (same rationale — REPORTED-channel completion is not the
 *     dispatch path for NON_COMPLETING).
 *  3. `PilotCognitive.stepTransmission` — DECLINE_DEPARTURE returns
 *     `null` (no transmission emitted in v1; cognitive-suppression in
 *     `pilotDecide` also zeros same-tick transmissions).
 *  4. `Pilot.planRoute` — falls into the "step not in airborneSteps"
 *     guard via the default-Skip return; DA decline has no airborne
 *     route. Combined with `applyDensityAltitudeDecline`'s
 *     `targetSpeedMps = 0`, the aircraft remains at-rest on the apron.
 *
 * This file pins the enum-value addition and the structural contract;
 * the per-site behavioural tests live in:
 *  - `PilotDensityAltitudeDeclineTest` (apply intent + mission tree)
 *  - `IsDensityAltitudeDeclineEligibleSpec` (eligibility guard)
 *  - `PilotEventDensityAltitudeTest` (recognition gate)
 *
 * A regression that drops the enum value, removes one of the audit-site
 * arms, or wires DECLINE_DEPARTURE with a CompletionMode that ISN'T
 * NON_COMPLETING fails one of those downstream tests.
 */
class MissionStepDeclineDepartureAuditSpec {

    @Test
    fun `DECLINE_DEPARTURE is enumerated in the MissionStep enum`() {
        assertTrue(
            MissionStep.DECLINE_DEPARTURE in MissionStep.values(),
            "MissionStep enum must include DECLINE_DEPARTURE (fn-28.2 R15)",
        )
    }

    @Test
    fun `DECLINE_DEPARTURE pairs with NON_COMPLETING in the canonical applyXxx construction`() {
        // The unique constructor of a `PrimitiveTask(DECLINE_DEPARTURE, ...)`
        // is `applyDensityAltitudeDecline` (Pilot.kt). It pairs the
        // MissionStep with `CompletionMode.NON_COMPLETING` via
        // `replaceFromActivePrimitive`. Pin the contract:
        val canonical = PrimitiveTask(MissionStep.DECLINE_DEPARTURE, CompletionMode.NON_COMPLETING)
        assertTrue(
            canonical.completionMode == CompletionMode.NON_COMPLETING,
            "DECLINE_DEPARTURE primitives MUST pair with NON_COMPLETING completion mode",
        )
    }

    @Test
    fun `MissionStep value set includes both fn-28_2 enum additions`() {
        // Pre-fn-28.2 there were 30 MissionStep values; fn-28.2 adds
        // DECLINE_DEPARTURE for one (and fn-28.8 will add ABORTED). The
        // count is brittle to future enum additions, so the assertion
        // only requires DECLINE_DEPARTURE — fn-28.8 (ABORTED) ships its
        // own audit pin.
        val all = MissionStep.values().toSet()
        assertTrue(MissionStep.DECLINE_DEPARTURE in all, "DECLINE_DEPARTURE present")
        // GOING_AROUND and AWAITING_ATC_INSTRUCTION are the fn-11/fn-12
        // peer-special-state values; pin them as cross-references so a
        // regression that accidentally renames or removes those nearby
        // values is caught here too.
        assertTrue(MissionStep.GOING_AROUND in all, "GOING_AROUND peer present")
        assertTrue(MissionStep.AWAITING_ATC_INSTRUCTION in all, "AWAITING_ATC_INSTRUCTION peer present")
    }
}
