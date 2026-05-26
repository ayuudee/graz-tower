# Sim emits pilot-notified frequency change (FN44-GAP-2 closure)

## Goal & Context

ICAO 9432 §2.8.2.1 requires that, absent controller advice, "an aircraft
will, except for reasons of safety, notify the appropriate aeronautical
station before such a [frequency] change takes place." The protocol layer
already models this notification as `Request(RequestFrequencyChange(...))`,
and the evidence DSL now projects matching pilot transmissions into
`FrequencyTransfer(mode = PilotNotifiedAbsentAdvice, …)` facts via the
adapter added in `fn-48-icao-9432-chunk-01-drive-expected-gap.2`.

However the sim does NOT currently emit
`Request(RequestFrequencyChange(...))` from the pilot side under any
scenario. As a result the chunk-01 evidence test
`Icao9432Chunk01FrequencyTransferEvidenceTest::pilot-notified frequency
change source unit is covered red against current LOWG trace` lands
**covered-red**: the audit honestly reports a `Fail` outcome for the cited
source ref, and the test asserts that outcome directly on
`report.results` rather than via `assertNoFailures()`. JUnit passes (build
green) while the audit's red outcome stands.

This repair epic closes that gap by teaching the sim's pilot agent to
emit `Request(RequestFrequencyChange(...))` in the appropriate scenario
contexts — at minimum where the pilot decides to leave a frequency
without a controller release/advice (cross-aerodrome, departure boundary,
etc.). When the sim emits the relevant transmission, the same chunk-01
test will flip from `covered-red` to `covered-green`; the assertion shape
in the test method should then be updated to call
`report.assertNoFailures()` and the `.plan` pointer (FN44-GAP-2 →
fn-49-sim-emits-pilot-notified-frequency) deleted.

## Closure Signal

`./gradlew-nix :sim:jvmTest --tests
"*.Icao9432Chunk01FrequencyTransferEvidenceTest"` runs to green with the
pilot-notified test method calling `report.assertNoFailures()` (instead
of asserting on `report.results` for a `Fail` outcome).

Equivalently: in any sim scenario, at least one
`Request(RequestFrequencyChange(...))` `PilotTransmission` is emitted
under conditions where ICAO 9432 §2.8.2.1's fallback applies (pilot
changing frequency absent controller advice).

## Acceptance Criteria

- [ ] **R1** — Pilot agent emits `Request(RequestFrequencyChange(...))`
  in at least one realistic sim scenario (e.g., cross-aerodrome
  departure-boundary frequency change, AFIS-to-uncontrolled-airspace
  transition).
- [ ] **R2** — The chunk-01 evidence test for the pilot-notified branch
  re-runs `covered-green`: the test method asserts via
  `report.assertNoFailures()` (the `report.results` `Fail` assertion
  introduced in fn-48-icao-9432-chunk-01-drive-expected-gap.2 is replaced).
- [ ] **R3** — `.plan` pointer paragraph for FN44-GAP-2 is deleted
  (since the unit is now `covered-green`).
- [ ] **R4** — `./gradlew-nix build` and `./gradlew-nix detekt` both
  green.
- [ ] **R5** — No regression in existing sim golden tests
  (`./gradlew-nix :sim:jvmTest`).

## Investigation hints

- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/PilotTransmission.kt:235`
  — `RequestFrequencyChange` already exists.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:444+`
  — adapter projection `pilotNotifiedFrequencyChangeFact` consumes the
  transmission and emits the typed fact.
- `pilot/src/commonMain/kotlin/.../decision` — pilot decision sites that
  pick communications transmissions.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt`
  — closure test; its `pilotNotified` method shape changes once green.

## Boundaries

In scope:
- Pilot-side decision logic that selects `RequestFrequencyChange` in at
  least one realistic scenario.
- Updating the chunk-01 evidence test method to call
  `report.assertNoFailures()`.
- Deleting the FN44-GAP-2 pointer paragraph from `.plan`.

Out of scope:
- Controller-advised frequency change (already covered-green via
  ContactFrequency; FN44-GAP-1 closed).
- Restructuring `EvidenceFactPayload.FrequencyTransfer` or
  `FrequencyTransferTarget`.
- New ICAO 9432 source units beyond §2.8.2.1.

## Notes

Drafted as part of `fn-48-icao-9432-chunk-01-drive-expected-gap.2`'s
honest covered-red landing. Per the "no deferment, no surprises" doctrine:
the FN44-GAP-2 `.plan` entry is REPLACED (not deleted) with a one-line
pointer to this repair epic, preserving visible debt until production
closes the gap.
