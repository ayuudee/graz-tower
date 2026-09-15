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
Blocked on D-PF.1 rather than implementing an evidence-only projection. The
live departure tree deliberately omits `REQUEST_STARTUP` /
`AWAIT_STARTUP_APPROVAL`, no controller procedure emits `StartupApproved` in a
real sim workflow, and `AircraftState.engineRunning` defaults true for
failure/abort physics rather than representing an engine-start lifecycle.

Decision: the eventual implementation needs explicit lifecycle/evidence for
`StartupApproved` before engine start as part of D-PF.1. Inferring engine start
from mission-step completion or default `engineRunning == true` would create a
false green for ICAO 9432 §4.2.3.

Blocked:
Blocked on D-PF.1.

There is no honest scoped implementation for ICAO 9432 source unit
`icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd`
without pulling the startup-clearance lifecycle into scope. The live departure
tree deliberately omits `REQUEST_STARTUP` / `AWAIT_STARTUP_APPROVAL`, no real
controller procedure emits `StartupApproved`, and `AircraftState.engineRunning`
defaults true for failure/abort physics rather than representing an orderable
engine-start observation.

The task is handled as an expected model gap: D-PF.1 now states that closing
startup clearance must provide orderable evidence that `StartupApproved`
occurred before an explicit engine-start observation. Mission-step completion
and default `engineRunning == true` are not valid substitutes.
## Evidence

- Commits:
- Tests:
  - `./gradlew-nix :sim:jvmTest --tests '*.Icao9432ModelGapSourceUnitSpecTest'`
- PRs:
  - `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432ModelGapSourceUnitSpecTest.kt`
  - `docs/deferments.md`
