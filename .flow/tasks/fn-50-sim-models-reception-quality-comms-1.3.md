# fn-50-sim-models-reception-quality-comms-1.3 Flip COMMS-1 covered-green and close chunk-01 framing

## Description
Flip the COMMS-1 chunk-01 source-mapped evidence from covered-red to
covered-green and close all live framing.

This task is only valid after task .2 proves a real resolved
reception-doubt chain. Replace the direct `Fail` assertion in
`Icao9432Chunk01ReceptionDoubtEvidenceTest` with
`report.assertNoFailures()` plus a targeted `Pass` cross-check. Delete
the `.plan` COMMS-1 pointer and update `STRATEGY.md` plus the ICAO 9432
chunk-01 quality artifacts so they no longer advertise COMMS-1 as a
covered-red repair item.

## Acceptance
- [x] `Icao9432Chunk01ReceptionDoubtEvidenceTest` calls
  `report.assertNoFailures()` and asserts the cited COMMS-1 source ref
  produces `EvidenceAuditOutcome.Pass`.
- [x] `.plan` COMMS-1 pointer paragraph is deleted.
- [x] `STRATEGY.md` requirements-registry paragraph says chunk-01 COMMS-1
  is covered-green, while PHRASE-1 and POLICY-1 remain as open
  non-COMMS-1 work.
- [x] `research/tools/requirements-spike/quality/icao9432_programme/`
  live reports and structured artifacts no longer contain stale
  `COMMS-1` covered-red or expected-gap framing.
- [x] `fn-50-sim-models-reception-quality-comms-1` is closed in flow.
- [x] Verification is green:
  `./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01ReceptionDoubtEvidenceTest"`,
  `./gradlew-nix :sim:jvmTest`, and `./gradlew-nix build detekt`.

## Review considerations

- FP / type safety: this should be docs/test-framing only; any new model
  code discovered here means task .2 was incomplete.
- Test architecture: use the same source-mapped pattern as fn-49.2:
  `assertNoFailures()` plus targeted `Pass`, not a broad report-only
  assertion.
- Impact: stale generated artifacts are a real risk. Grep for `COMMS-1`
  and distinguish live closed-red framing from historical flow specs.
- Operational correctness: cite ICAO 9432 §2.8.1.4 in the test/doc
  framing and avoid phraseology claims beyond typed repetition request.

## Done summary
Flipped COMMS-1 to covered-green and closed live chunk-01 framing.

- Updated Icao9432Chunk01ReceptionDoubtEvidenceTest to call report.assertNoFailures() and assert Pass for ICAO 9432 §2.8.1.4.
- Deleted the active .plan COMMS-1 repair pointer.
- Updated STRATEGY.md and chunk-01 markdown/JSON/CSV artifacts so COMMS-1 is covered-green and no longer an expected-gap/covered-red item.
- Kept PHRASE-1 and POLICY-1 unchanged, and filed D-AUDIT.15-FOLLOWUP for cognitive-mission repetition recovery.
## Evidence
- Commits:
- Tests: {'command': './gradlew-nix :sim:jvmTest --tests "*.ReceptionQualityCommsTest" --tests "*.EvidenceFactsTest" --tests "*.Icao9432Chunk01ReceptionDoubtEvidenceTest" --tests "*.G1B4ClosurePinSpec"', 'result': 'pass'}, {'command': './gradlew-nix :sim:jvmTest', 'result': 'pass'}, {'command': './gradlew-nix detekt', 'result': 'pass'}, {'command': './gradlew-nix :sim:jvmTest --tests "*Icao9432*" :controller:jvmTest --tests "*Icao9432*"', 'result': 'pass'}, {'command': './gradlew-nix build', 'result': 'pass'}
- PRs:
