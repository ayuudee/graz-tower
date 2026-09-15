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
- [ ] `Icao9432Chunk01ReceptionDoubtEvidenceTest` calls
  `report.assertNoFailures()` and asserts the cited COMMS-1 source ref
  produces `EvidenceAuditOutcome.Pass`.
- [ ] `.plan` COMMS-1 pointer paragraph is deleted.
- [ ] `STRATEGY.md` requirements-registry paragraph says chunk-01 COMMS-1
  is covered-green, while PHRASE-1 and POLICY-1 remain as open
  non-COMMS-1 work.
- [ ] `research/tools/requirements-spike/quality/icao9432_programme/`
  live reports and structured artifacts no longer contain stale
  `COMMS-1` covered-red or expected-gap framing.
- [ ] `fn-50-sim-models-reception-quality-comms-1` is closed in flow.
- [ ] Verification is green:
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
TBD

## Evidence
- Commits:
- Tests:
- PRs:
