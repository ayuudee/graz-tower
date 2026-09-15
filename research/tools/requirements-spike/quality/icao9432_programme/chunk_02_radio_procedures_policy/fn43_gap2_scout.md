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

- `fromLowgCircuitTrace(...)` currently adds `CriticalPhaseWindow` facts.
- No production projection of `EvidenceFactPayload.CriticalPhaseTransmission`
  from real `TransmissionRecord` / `SimTrace` controller transmissions was
  found. `rg "CriticalPhaseTransmission("` finds the payload declaration and
  test-injected instances only.
- Therefore a selector can pass with observed windows and zero projected
  routine transmissions, but that is not enough to prove ICAO 9432 §4.1.2.

Classification:

- `expected-gap` for missing routine/safety-necessity transmission projection.
- `policy-blocked` for the "unless necessary for safety reasons" exception.

Repair direction:

- Separate epic: project controller transmissions that occur inside
  `CriticalPhaseWindow`s, carrying `TransmissionNecessity.Routine` vs a typed
  safety-necessary classification.
- Do not green chunk 02 from window-only evidence.

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

- `expected-gap` (`FN43-GAP-2` or a narrower start-up-workflow repair epic).

Repair direction:

- Separate epic: introduce an observable start-up workflow fact only if the
  sim chooses to model engine-start approval/start as a real lifecycle.
- Do not infer engine start from mission-step completion alone.

### Ground-Station Test-Signal Duration

Existing surfaces:

- `TransmissionRecord.time` exists.
- Transmission duration can be inferred from `SimEvent.TransmissionStart` /
  `SimEvent.TransmissionEnd` in the raw event trace, but
  `TransmissionRecord` stores start time only.

Important limitation:

- No typed protocol/sim concept for a ground-station test signal was found.
- Without a typed test-signal utterance or rendered phraseology layer, the
  duration obligation cannot be identified without string-matching speech.

Classification:

- Duration: `expected-gap` under a narrower radio-test-signal modelling gap.
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
