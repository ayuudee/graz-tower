# fn-56-icao-9432-chunk-06-go-around-after.4 Close chunk 06 coverage report

## Description
Close chunk 06 with a coverage report, verification, implementation review,
and clean Flow sidecars.

## Acceptance
- [x] Produce `coverage_report.md` with one final state per source unit.
- [x] Run focused chunk 06 tests, broad `:sim:jvmTest`, detekt, Flow
      validation, and `git diff --check`.
- [x] Complete implementation review and fix any blocking findings.
- [x] Commit and push the completed epic if the user asks to proceed beyond
      planning.

## Done summary
Closed chunk 06 coverage report; focused ICAO tests, broad sim jvm test, detekt, Flow validation, and git diff --check passed; implementation review findings fixed.
## Evidence
- Commits:
- Tests: `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk06GoAroundEvidenceTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`; `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*' --tests '*.EvidenceSourceCatalogTest'`; `./gradlew-nix :sim:jvmTest`; `./gradlew-nix detekt`; `.flow/bin/flowctl validate --epic fn-56-icao-9432-chunk-06-go-around-after`; `git diff --check`
- Review: implementation review found no Kotlin/source-mapping blocker; closure-hygiene findings fixed.
- PRs:
