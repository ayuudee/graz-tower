# fn-73-icao-9432-emergency-1f-message.1 Build message policy movement manifest and plan review

## Description
Build the exact source-unit movement manifest for the fn-73 message-addressing
and payload-policy slice, then run plan and impact review before implementation.

## Acceptance
- [ ] Movement manifest lists only the targeted message policy rows:
  `27a450fa3bfcbc0a`, `94e94be0c800c982`, `1de475a788206cc8`, and
  `b95d7bb1cb409bca`.
- [ ] Manifest states why rendered phraseology/order, Annex 10, any-means
  communication, emergency-descent follow-up instructions, and controller
  communications-failure relay remain non-targets.
- [ ] Plan review is completed and recorded on the epic.
- [ ] Impact review is completed before implementation.
- [ ] Review concerns are addressed in the plan itself, especially policy
  honesty, phraseology non-claiming, and executable residual gaps.
- [ ] Plan requires explicitly stated relay circumstances for `94e94...`,
  non-vacuous urgency payload policy tests, and a residual gap rewrite limited
  to rendered phraseology/order.

## Done summary
Created fn-73 message-policy movement manifest, ran impact review and plan review, patched plan-review findings, and received ship re-review.
## Evidence
- Commits:
- Tests:
- PRs: