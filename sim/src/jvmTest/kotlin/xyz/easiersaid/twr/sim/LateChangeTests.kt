package xyz.easiersaid.twr.sim

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * Phase C placeholder tests for circuit-intent late-change scenarios.
 *
 * These tests pin the *contract* the firewall + circuit-intent design
 * accommodates. They are deliberately `@Ignore`d — implementing them is
 * deferred work. The point is to make Phase C's "this is accommodated"
 * claim auditable: when Phase C lands, these become real tests; if the
 * design were wrong, the tests would not be writable in their current
 * shape.
 *
 * See `/home/andrew/.claude/plans/deep-mixing-prism.md` Phase C for the
 * design context. Each placeholder names its detection mechanism so the
 * implementer knows what they're targeting.
 */
class LateChangeTests {

    /**
     * Phase C1: pilot was cleared touch-and-go and stops on the runway anyway
     * (per Annex 2 §2.3.1 PIC authority — benign deviation, must inform ATC).
     *
     * Detection mechanism: sensor-only. Aircraft on runway entity, ground
     * speed below taxi threshold, time elapsed since holderReachedRunway
     * exceeds expected T&G roll-out window. Requires a new
     * `holderTouchdownAt: SimTime?` field on `RunwayDutyState`.
     *
     * No `circuitIntent` read on the controller side — detection works
     * regardless of declared intent because the deviation is visible from
     * the runway-occupancy belief alone.
     */
    @Ignore
    @Test
    fun `pilot stops on T&G clearance — controller observes via runway occupancy and issues vacate`() {
        // TODO: Phase C1.
        // 1. Spawn AI aircraft, fly one circuit with CircuitIntent.TOUCH_AND_GO.
        // 2. Touch down on runway. Pilot fails to lift off again (simulate by
        //    keeping target speed at 0 after touchdown).
        // 3. Wait > T_ROLLOUT seconds.
        // 4. Assert: controller emitted AfterLandingVacateVia (ARR-VACATE-AFTER-DEVIATION).
        // 5. Assert: belief.runwayDuty?.holderTouchdownAt was set.
    }

    /**
     * Phase C2: pilot was cleared to land and tries a touch-and-go without
     * fresh take-off clearance. ICAO 4444 §7.9 — unauthorised takeoff.
     *
     * Detection mechanism: runway-clear belief + airborne observation
     * combined with the active coordination ledger entry showing the most
     * recent runway-use clearance was `ClearedToLand` (no `ClearedForTakeoff`
     * issued). New ARR-UNAUTHORISED-DEP rule fires.
     *
     * Like C1, this is a sensor + ledger detection — no pilot internal
     * state read.
     */
    @Ignore
    @Test
    fun `pilot does T&G under cleared-to-land — controller flags unauthorised takeoff`() {
        // TODO: Phase C2.
        // 1. Spawn AI aircraft inbound; pilot reports "downwind, full stop".
        // 2. Controller issues ClearedToLand.
        // 3. Pilot touches down then lifts off again (sim modifies pilot to
        //    re-engage circuit instead of stopping).
        // 4. Assert: controller emitted Disregard or queued an investigation event.
        // 5. Assert: no ClearedForTakeoff was in the coordination ledger.
    }

    /**
     * Phase C4: multi-circuit (N≥2) with terminal `CircuitOutcome.FullStop`
     * (fn-11.1 typed shape) exercises both rule polarities in the same run.
     *
     * - Circuits 1..N-1: pilot reports "downwind, touch and go" → CircuitIntent
     *   = TOUCH_AND_GO → ARR-LAND-TNG fires → ClearedTouchAndGo issued.
     * - Circuit N: pilot reports "downwind, full stop" → CircuitIntent overwrites
     *   to FULL_STOP → ARR-LAND fires → ClearedToLand issued.
     *
     * Verifies the belief overwrite-cycle-to-cycle property: each downwind
     * report supersedes the previous intent without a stale clearance hanging
     * in the coordination ledger (within Phase B scope: each clearance is
     * issued *after* the corresponding downwind report).
     */
    @Ignore
    @Test
    fun `multi-circuit T&G then full-stop — belief overwrites cycle to cycle`() {
        // TODO: Phase C4.
        // 1. Spawn AI aircraft with CircuitTraining(outcomes = listOf(TouchAndGo, FullStop)).
        // 2. Run until first circuit complete. Assert: ClearedTouchAndGo issued for
        //    circuit 1; circuitIntent[ac] cleared after T&G touchdown so the next
        //    circuit's downwind can re-declare.
        // 3. Run until second circuit complete. Assert: ClearedToLand issued for
        //    circuit 2; AfterLandingVacateVia issued on touchdown.
        // 4. Assert: instruction stream contains both ClearedTouchAndGo and
        //    ClearedToLand for the same aircraft, in that order.
    }
}
