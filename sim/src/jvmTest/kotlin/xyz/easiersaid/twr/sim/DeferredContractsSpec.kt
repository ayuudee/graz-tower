package xyz.easiersaid.twr.sim

import kotlin.test.Ignore
import kotlin.test.Test

/**
 * `@Ignore`d placeholder tests pinning eventual contracts for deferred work
 * in `:sim`. Mirrors the `:pilot` / `:controller` / `:protocol` patterns;
 * see `docs/deferments.md` for the canonical register and
 * `docs/deferments-CONVENTION.md` for the four-bucket model.
 *
 * When a deferment is picked up, the implementer flips `@Ignore` off,
 * uncomments / extends the body, and the test becomes a real verification
 * of the contract.
 */
class DeferredContractsSpec {

    /**
     * **D-AUDIT.2.C-FOLLOWUP** — sim-level integration test for the full
     * comms tail (Pass 9 lost-comms lifecycle).
     *
     * When implemented:
     *  - The sim runs an end-to-end scenario where an aircraft fails to
     *    read back, the controller queries (`ConfirmInstruction`), reissues
     *    (`AtcInstruction` replay-as-original per Doc 4444 §12.3.1.2),
     *    and finally transmits blind under `LostCommsDeclared` (§15.1.4).
     *  - Assertions: query at 10 s; reissue at 30 s, 50 s, 70 s (3 attempts);
     *    `LostCommsDeclared` at 5 min; `TransmittingBlind` emission gated
     *    by `emittedBlindAt`.
     *  - Needs per-message cognitive-delay knob on `PilotInput` so a
     *    deterministic test can stage the readback miss without injecting
     *    sim-internal time skew.
     *
     * Bucket 2 — the per-message cognitive-delay knob does not exist; today
     * tests run with a single global cadence.
     */
    @Ignore
    @Test
    fun `D-AUDIT2-C full comms tail integration test (query, reissue, blind)`() {
        // Bucket 2: needs `PilotInput.perMessageCognitiveDelay` (or similar
        // knob) on the pilot input surface before the scenario is testable
        // deterministically.
        // TODO when D-AUDIT.2.C-FOLLOWUP lands:
        //   val fixture = lowgGoldenFixture(
        //       pilotCognitiveDelayPerMessage = mapOf(missedInstr to SimDuration.minutes(6)),
        //   )
        //   val trace = runTo(fixture, t = SimDuration.minutes(7))
        //   val states = trace.coordinationStateTransitions(aircraft, missedInstr)
        //   assertEquals(
        //       listOf("Issued", "Querying", "Reissued(1)", "Reissued(2)", "Reissued(3)", "LostCommsDeclared"),
        //       states,
        //   )
    }

    /**
     * **D-AUDIT.2.F-FOLLOWUP** — G0 negative-escalation assertion
     * (instruction-vs-completion semantics).
     *
     * When implemented:
     *  - G0 asserts "no `LostCommsDeclared` on instructions the aircraft
     *    has not physically complied with" — distinguishes "instruction
     *    issued, not yet read back" from "instruction issued, aircraft
     *    ignoring it."
     *  - Pass 12 dropped the prior "no `LostCommsDeclared` at end of G0"
     *    assertion: it passed due to the destroyed-on-readback bug
     *    (D-AUDIT.2.E follow-on), not because of correctness.
     *  - The right shape lands when scenario-level coverage of the
     *    instruction-vs-completion semantics is in place.
     *
     * Bucket 2 — `Commitment.physicallyComplied` (or equivalent typed
     * "the aircraft has done the thing") accessor doesn't exist yet.
     */
    @Ignore
    @Test
    fun `D-AUDIT2-F G0 no LostCommsDeclared on un-complied-with instructions`() {
        // Bucket 2: requires an "instruction physically complied with"
        // accessor on `Commitment` or `BeliefState` that distinguishes
        // "readback received but stage not reached" from "no readback".
        // TODO when D-AUDIT.2.F-FOLLOWUP lands:
        //   val trace = runG0()
        //   val incompleteInstr = trace.uncompletedInstructions()
        //   for (instr in incompleteInstr) {
        //       assertNull(
        //           trace.coordinationState(aircraft, instr).lostCommsDeclaredAt,
        //           "no LostCommsDeclared on un-complied-with instr $instr",
        //       )
        //   }
    }
}
