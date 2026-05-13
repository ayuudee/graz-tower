# fn-19-fix-d-world2-pre-existing Fix D-WORLD.2 pre-existing :migration:jvmTest failure (LjmbWorldCandidateValidationTest 9-vs-5 SIDs)

## Overview
Reconcile the LJMB candidate to the 9-SID expectation OR document the 5-SID acceptance and update the test; unblocks `./gradlew build`.

## Leverage
Small-diff lever because the failure is one test's assertion against one regenerated artifact; impact lands on every commit's broad build (currently red on a side path that 5+ epics have carved out).

## Suggested size
S (from prospect ranking)

## Affected areas
migration/, .plan (D-WORLD.2 archive flip), docs/deferments.md

## Risk notes
If the 5-SID acceptance is the right answer, the test+report must agree; if the 9-SID restoration is right, the LJMB candidate generator may need a bug fix.

## Source
- Prospect: `.flow/prospects/post-fn-15-16-17-18-next-moves-refactor-2026-05-13.md#idea-1`
- Focus hint: refactor for obvious gain / new territory (IFR, multi-aircraft, stress test) / working through .plan
- Prospected: 2026-05-13

## Acceptance
_(to be defined — run `/flow-next:interview <epic-id>` or `/flow-next:plan <epic-id>` next)_

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `# e.g., npm test, bun test, make test`
