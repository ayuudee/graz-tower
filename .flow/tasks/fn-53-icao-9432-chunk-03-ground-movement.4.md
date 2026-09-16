# fn-53-icao-9432-chunk-03-ground-movement.4 Close chunk 03 coverage report

## Description
Close chunk 03 with a coverage report, verification run, implementation review,
and commit.

## Acceptance
- [ ] `coverage_report.md` records final state for all 11 source units.
- [ ] Source-plan, expected-gap, and coverage-report docs agree.
- [ ] Focused tests, full sim JVM tests, detekt, diff check, and flow
      validation pass.
- [ ] Implementation review returns `SHIP`.

## Done summary
Closed chunk 03 coverage with full sim verification, detekt, Flow validation, diff check, and implementation review follow-up.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest, ./gradlew-nix :sim:jvmTest --tests chunk03/model-gap/catalog, ./gradlew-nix detekt, git diff --check, .flow/bin/flowctl validate --epic fn-53-icao-9432-chunk-03-ground-movement
- PRs: