# FN43-GAP-2 Evidence Scout

Task: `fn-51-icao-9432-chunk-02-radio-procedures-and.3`

Scope:

- `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4`
- `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd`
- Duration sub-obligation of
  `icao9432-extracted::test_procedures_2_8_4_en::d67d1f63cbbecd7d`

## Findings

### Critical-Phase Radio Discipline

Existing surfaces:

- `EvidenceFactPayload.CriticalPhaseWindow`
- `EvidenceFactPayload.CriticalPhaseTransmission`
- `EvidenceExpectContext.criticalPhase(aircraftId)`
- `AuditCriticalPhaseRoutineTransmissions.none()`
- `ICAO9432.CriticalPhase.CriticalPhaseRadioSilence`
- `EvidenceGaps.CriticalPhaseRadioSilenceProjection`

Important limitation:

- Original scout finding: `fromLowgCircuitTrace(...)` added
  `CriticalPhaseWindow` facts but did not project
  `EvidenceFactPayload.CriticalPhaseTransmission` from real
  `TransmissionRecord` / `SimTrace` controller transmissions. A selector could
  therefore pass with observed windows and zero projected routine
  transmissions.
- fn-52.1 follow-up: the adapter now projects
  `CriticalPhaseTransmission(Routine)` for controller transmissions whose
  target aircraft is in a protected phase. `PilotPhase.Climbing` and
  `PilotPhase.Final` are documented conservative over-approximations for
  initial climb and late final. The LOWG pressure trace now reports this source
  unit as covered-red rather than false-green.

Classification:

- `covered-red` for current LOWG routine critical-phase transmission evidence
  under fn-52.1's conservative classification.
- `policy-blocked` for the "unless necessary for safety reasons" exception.

Repair direction:

- Separate epic: project controller transmissions that occur inside
  `CriticalPhaseWindow`s, carrying `TransmissionNecessity.Routine` vs a typed
  safety-necessary classification.
- Do not green chunk 02 from window-only evidence.
- Impact review addendum: the existing window projection omitted
  `InitialClimb` and `LateFinal` because `PilotPhase.Climbing` and
  `PilotPhase.Final` mapped to `null`. fn-52.1 must fix that as a conservative
  observation surface or leave the unobservable parts explicit.
- Impact review addendum: `TransmissionNecessity.SafetyNecessary` is too
  coarse to emit without a reason-bearing policy type. fn-52.1 should classify
  projected transmissions as `Routine` only.

### Engine-Start After Approval

Existing surfaces:

- `protocol.StartupApproved`
- `protocol.RequestStartup`
- `MissionStep.REQUEST_STARTUP`
- `MissionStep.AWAIT_STARTUP_APPROVAL`
- `PilotCognitive` handles `StartupApproved` by completing the mission step.
- `SimEvent.Spawn` exists, but it is aircraft creation, not an engine-start
  event following approval.

Important limitation:

- No source-mapped evidence fact was found for "ATC approval received" followed
  by "pilot starts engines".
- `AircraftState.engineRunning` exists, but currently serves the abort /
  engine-failure path; it starts true by default and is not an engine-start
  lifecycle signal.

Classification:

- `expected-gap`, blocked on D-PF.1 after fn-52.2 impact review.

Repair direction:

- Introduce an observable start-up workflow fact only when D-PF.1 lands the
  real startup-clearance lifecycle: airport-conditional startup requirement,
  live startup mission steps, and a clearance-delivery procedure that emits
  `StartupApproved`.
- Do not infer engine start from mission-step completion or default
  `AircraftState.engineRunning == true`.

### Ground-Station Test-Signal Duration

Existing surfaces:

- `TransmissionRecord.time` exists.
- fn-52.3 adds mandatory `TransmissionRecord.endedAt`, derived from the typed
  `InFlightTransmission.endsAt`; matching `SimEvent.TransmissionEnd` records
  are consistency-checked when present.
- fn-52.3 adds typed `Utterance.GroundStationTestSignal` with
  `TestSignalPurpose`, plus `EvidenceFactPayload.GroundStationTestSignal`.

Important limitation:

- Spoken-number content and transmitting-station callsign content remain
  unmodelled until rendered phraseology evidence exists.

Classification:

- Duration: `covered-green` after fn-52.3 for the ICAO 9432 §2.8.4.4
  10-second duration sub-obligation.
- Spoken-number/callsign content: `phraseology-later` (`PHRASE-1`).

## Impact Assessment

- Adding critical-phase transmission projection is reusable across later chunks
  and should not be hidden inside chunk 02. It affects source-mapped evidence
  semantics and should get its own reviewed repair epic.
- Adding engine-start workflow evidence is a domain decision. If engine start
  becomes lifecycle state, every mission authoring path and reversal path must
  be audited.
- Adding radio-test-signal duration evidence requires a typed signal kind or
  phraseology rendering. A duration-only projection without signal identity
  would be under-specified and easy to falsely green.

## Conclusion

No fn-51 chunk-02 row should be moved to covered-green in the current coverage
epic without a separate implementation repair. Task .4 should author
expected-gap records and, if useful, a source-mapped expected-gap test for the
critical-phase unit to pin the missing projection honestly.
