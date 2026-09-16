# fn-58-icao-9432-chunk-08-distress-urgency.4 Close chunk 08 coverage report

## Description
Close chunk 08 with a coverage report, verification, implementation review, and
clean Flow sidecars.

## Acceptance
- [x] Produce `coverage_report.md` with one final state per source unit.
- [x] Run focused chunk 08 tests, broad `:sim:jvmTest`, detekt, Flow
      validation, and `git diff --check`.
- [x] Complete implementation review and fix any blocking findings.
- [x] Commit and push the completed epic.

## Done summary
Closed chunk 08 coverage with zero green rows, full verification, and
implementation review remediation. Final review pending after sidecar closure.
## Evidence
- Docs: `research/tools/requirements-spike/quality/icao9432_programme/chunk_08_distress_urgency_comms_failure/coverage_report.md`
- Tests: `./gradlew-nix :sim:jvmTest`
- Tests: `./gradlew-nix detekt`
- Tests: `.flow/bin/flowctl validate --epic fn-58-icao-9432-chunk-08-distress-urgency`
- Tests: `git diff --check`
- Review: first implementation review NO-SHIP; findings remediated
