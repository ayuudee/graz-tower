# fn-52-implement-icao-9432-chunk-02.2 Model start-up approval to engine-start evidence

## Description

Model or expose evidence for ATC start-up approval followed by pilot engine
start, without inferring engine start from mission-step completion alone.

## Acceptance

- [x] The design decides whether engine start is explicit lifecycle state or an evidence-only projection.
- [x] Approval and start observations are orderable in sim time/evidence sequence.
- [x] Reversal and initial-state implications of any `engineRunning` change are audited.
- [x] Source-mapped test cites `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd`.

## Done summary
Assessed the start-up approval to engine-start source unit and deliberately
left it as an expected model gap blocked on D-PF.1. No evidence-only shortcut
was implemented: mission-step completion and default `engineRunning == true`
are not valid substitutes for orderable `StartupApproved` before explicit
engine-start evidence.

## Evidence
- Commit: `cc88274a fn-52.2 block startup engine-start evidence`
- Tests: `./gradlew-nix :sim:jvmTest --tests '*.Icao9432ModelGapSourceUnitSpecTest'`
- Files: `docs/deferments.md`
- Files: `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432ModelGapSourceUnitSpecTest.kt`
- Files: `research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/expected_gaps.md`
- Files: `research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/coverage_report.md`
