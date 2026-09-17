# fn-52-implement-icao-9432-chunk-02.1 Project critical-phase controller transmissions with safety-necessity classification

## Description

Emit `EvidenceFactPayload.CriticalPhaseTransmission` from real sim traces for
controller transmissions that occur inside observed critical-phase windows.
Do not rely on `fromProjectedPayloads`.

## Acceptance

- [x] Projection is derived from real `TransmissionRecord` / `SimTrace` data.
- [x] Critical-phase windows cover all four ICAO 9432 §4.1.2 protected phases:
  take-off, initial climb, late final, and landing roll; any approximation is
  documented as an over-approximation, not a silent omission.
- [x] Routine vs safety-necessary classification is typed.
- [x] All projected transmissions are classified as `Routine` until a
  reason-bearing safety-necessity policy type exists.
- [x] No default marks transmissions safety-necessary.
- [x] A test creates or observes a real controller transmission inside a
  critical-phase window and proves it becomes a
  `CriticalPhaseTransmission(Routine)` fact.
- [x] A source-mapped audit test proves routine in-window transmission fails
  `criticalPhase(...).routineControllerTransmissions().none()`.
- [x] Target-aircraft scoping is tested: a controller transmission to a
  different aircraft is not attributed to the aircraft in the critical phase.
- [x] Source-mapped test cites `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4`.

## Done summary
Projected `CriticalPhaseTransmission` facts from real `SimTrace`
transmission data and kept all safety-necessity classification `Routine` until
a reason-bearing policy type exists. The source-mapped result remains
covered-red/policy-blocked rather than green.

## Evidence
- Commit: `195fae36 fn-52.1 project critical-phase transmissions`
- Tests: `./gradlew-nix :sim:jvmTest`
- Tests: `./gradlew-nix detekt`
- Tests: `git diff --check`
- Files: `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt`
- Files: `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceProjectionPressureTest.kt`
- Files: `research/tools/requirements-spike/quality/icao9432_programme/chunk_02_radio_procedures_policy/coverage_report.md`
