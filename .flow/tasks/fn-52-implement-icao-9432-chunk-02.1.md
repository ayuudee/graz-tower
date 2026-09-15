# fn-52-implement-icao-9432-chunk-02.1 Project critical-phase controller transmissions with safety-necessity classification

## Description

Emit `EvidenceFactPayload.CriticalPhaseTransmission` from real sim traces for
controller transmissions that occur inside observed critical-phase windows.
Do not rely on `fromProjectedPayloads`.

## Acceptance

- [ ] Projection is derived from real `TransmissionRecord` / `SimTrace` data.
- [ ] Routine vs safety-necessary classification is typed.
- [ ] No default marks transmissions safety-necessary.
- [ ] Source-mapped test cites `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4`.

## Done summary

## Evidence

- Commits:
- Tests:
- PRs:
