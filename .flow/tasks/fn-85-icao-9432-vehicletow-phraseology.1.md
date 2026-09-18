# fn-85-icao-9432-vehicletow-phraseology.1 Render vehicle and tow phraseology evidence

## Description
Implement the fn-85 manifest:
`research/tools/requirements-spike/quality/icao9432_programme/fn85_vehicle_tow_phraseology_manifest.md`.

The work is limited to rendered evidence for two already-structured chunk 07
source units. Extend the vehicle/tow phraseology and evidence surfaces, then
prove the real source-backed vehicle first-call and tow-request payloads render
the expected source-unit wording.

Do not implement the remaining chunk 07 model/policy blockers and do not reuse
aircraft taxi/runway phraseology as vehicle evidence.

## Acceptance
# Acceptance Criteria

- [x] Rendered vehicle first-call evidence covers source unit
  `7759017903acf140` with call sign, position, destination, and ordered route.
- [x] Rendered tow-request metadata evidence covers source unit
  `4b103081585bfb71` with addressee/receiving station, aircraft under tow,
  aircraft type, and operator.
- [x] Vehicle/tow phraseology matching is typed and rejects mismatched or malformed
  evidence loudly in selector tests.
- [x] Selector tests distinguish absent rendered evidence, metadata mismatch,
  malformed token sequences, renderer unsupported payloads, and aircraft
  phraseology being rejected as vehicle evidence.
- [x] Selector tests cover the concrete cases from the epic: missing call sign,
  reordered route, wrong destination, missing addressee, wrong aircraft under
  tow, missing aircraft type/operator, and aircraft taxi/runway phraseology
  being rejected as vehicle evidence.
- [x] Chunk 07 docs and the central blocker manifest no longer list these two
  rendered wording branches as open `PHRASE-1` residuals.
- [x] The central blocker manifest path is
  `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`.
- [x] Remaining chunk 07 policy/model blockers stay explicit and unchanged.
- [x] Focused tests, broad relevant tests, detekt, flow validation, implementation
  review, completion review, and `git diff --check` pass.


## Done summary
Added typed vehicle-driver rendered phraseology for vehicle initial calls and
tow requests, with explicit unsupported results for other vehicle-driver
transmissions. Added evidence facts/selectors and source-backed assertions for
the two chunk 07 rendered wording branches, then updated the chunk 07 docs and
central blocker manifest so the remaining blockers are only the unchanged
model/policy gaps.

## Evidence
- Commits: `7267646e`
- Tests:
  - `./gradlew-nix :sim:jvmTest --tests '*.EvidenceDslTest' --tests '*.Icao9432VehicleMovementSourceBackedScenarioTest' --tests '*.Icao9432VehicleTowingSourceBackedScenarioTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
  - `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
  - `./gradlew-nix detekt`
  - `scripts/ralph/flowctl validate --epic fn-85-icao-9432-vehicletow-phraseology --json`
  - `git diff --check`
- Reviews:
  - Plan review: `.flow/.plan-review-receipt-fn85-r2.json` (`SHIP`)
  - Implementation review: `.flow/.impl-review-receipt-fn85-r5.json` (`SHIP`)
  - Completion review: `.flow/.completion-review-receipt-fn85.json` (`SHIP`)
- PRs:
