# fn-72-icao-9432-emergency-1e-assistance-relay.1 Build assistance relay movement manifest and impact review

## Description
Build the exact movement manifest for fn-72 and run impact review before any
implementation. The manifest must separate emergency assistance, emergency
relay, frequency-policy, and interference-suppression branches, and must
explicitly keep message payload/addressing, controller lost-contact workflow,
Annex 10, and rendered phraseology out of scope.

## Acceptance
- [ ] Manifest lists every targeted source unit, current state, intended
  target state, evidence required, wrong-path negatives, and residual blocker.
- [ ] Rows that remain blocked are explicitly listed and unchanged.
- [ ] Policy rows bind named policy concepts rather than hidden defaults.
- [ ] Message payload/addressing rows and controller lost-contact rows are
  deferred as explicit non-targets.
- [ ] Reset/reversal expectations are listed before forward-path evidence.
- [ ] Impact review is complete before implementation starts.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## Done summary
Built narrowed fn-72 movement manifest and completed plan/impact review. Scope is eight emergency assistance/frequency/interference rows; message payload/addressing, controller lost-contact workflow, Annex 10, and phraseology remain non-target residuals.
## Evidence
- Commits:
- Tests:
- PRs: