# fn-80-icao-9432-phrase-1-stop-immediately.1 Implement source-mapped stop-immediately phraseology evidence

## Description
Implement source-mapped rendered phraseology evidence for the existing
`StopImmediately` instruction while preserving the operational trigger as
blocked policy/scenario work.

## Acceptance
- [ ] `RadioPhraseology` renders `StopImmediately` as a full controller phrase,
  not a fragment: callsign, STOP, IMMEDIATELY, callsign, STOP, IMMEDIATELY.
- [ ] The rendered phrase has exactly two callsign tokens and exactly two
  STOP/IMMEDIATELY pairs.
- [ ] `ICAO9432.TakeoffProcedures.StopImmediatelyPhrase` cites
  `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd`
  with `RenderedPhraseologyTrace` scope.
- [ ] A source-mapped test uses `EvidenceFactAdapters.fromTransmissionRecords`
  with a synthetic `ControllerOutput.Instruct.fromEmergencyPolicy(StopImmediately(...))`
  record and asserts through the rendered phraseology DSL selector.
- [ ] Chunk-04 `source_plan.md`, `coverage_report.md`, `expected_gaps.md`, and
  `implementation_blocker_manifest.csv` record
  `split: rendered phraseology covered; takeoff-roll/dangerous-traffic trigger policy blocked`.
- [ ] No controller selection, pilot behaviour, runway-duty, clearance
  lifecycle, or emergency-trigger behaviour changes are introduced.
- [ ] Focused tests, detekt, broad regression, flow validation, implementation
  review, and completion review are recorded.

## Done summary
Implemented source-mapped rendered phraseology evidence for ICAO 9432 §4.5.11 stop-immediately wording. Added full-phrase rendering for StopImmediately, exact token DSL assertion, source catalog entry, synthetic transmission evidence through the normal fact adapter, and chunk-04 split documentation preserving the operational trigger/policy gap.
## Evidence
- Commits:
- Tests:
- PRs: