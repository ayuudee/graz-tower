# fn-63 self-assessment

Epic: `fn-63-icao-9432-fn43-gap-2-start-up-lifecycle`

Scope: audit the remaining ICAO 9432 §4.2 start-up lifecycle gap and decide
whether any source unit can move green without implementing D-PF.1.

## Principal-agent checks

Totality:

- No production code was changed.
- No sealed transition, `when`, or runtime state field was introduced.

Reversal completeness:

- No reversible pilot/controller/sim transition was introduced.
- The required future engine-start lifecycle remains unimplemented rather than
  represented by an incomplete reset path.

Interaction coverage:

- The audit traced the existing protocol, pilot mission, controller event,
  commitment reconciliation, sim instruction application, and evidence surfaces.
- Existing pieces (`RequestStartup`, `StartupApproved`,
  `REQUEST_STARTUP`, `AWAIT_STARTUP_APPROVAL`) do not form a live end-to-end
  path because the normal departure tree omits startup and controller
  clearance-delivery commitments are unmodelled.

Test coverage for known features:

- No green coverage was added because the required behavior does not exist.
- Existing expected-gap coverage remains the correct test posture for
  `95034efc191fa9cd`.

New-field audit:

- No production fields were added.
- The future D-PF.1 implementation must audit all mutation sites if it adds
  airport-procedure flags, engine lifecycle state, or controller commitment
  state.

Operational correctness:

- ICAO Doc 9432 §4.2.3 requires ATC approval before engine start.
- A universal startup-clearance requirement would be operationally wrong;
  future implementation must be aerodrome/procedure conditional.
- Rendered request/approval wording remains phraseology work, not lifecycle
  evidence.

Error handling honesty:

- No new `error()` paths were added.
- The gap remains explicit in programme docs rather than hidden behind default
  values or synthetic evidence.

Deferment honesty:

- D-PF.1 already exists in `docs/deferments.md` and exactly covers the missing
  startup-clearance workflow.
- fn-63 did not discover a smaller honest implementation that avoids D-PF.1.
