# Implement ICAO 9432 chunk 02 observation evidence gaps

## Goal & Context

Repair the chunk-specific observation/model gaps discovered by
`fn-51-icao-9432-chunk-02-radio-procedures-and`. This is deliberately
separate from the fn-51 coverage epic so source-mapped test planning does not
smuggle implementation work into coverage classification.

In scope:

- Critical-phase controller-transmission projection for
  `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4`.
- Start-up approval to engine-start workflow evidence for
  `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::95034efc191fa9cd`.
- Ground-station radio-test-signal identity and duration evidence for the
  duration sub-obligation of
  `icao9432-extracted::test_procedures_2_8_4_en::d67d1f63cbbecd7d`.

Out of scope:

- Rendered phraseology validation (`PHRASE-1`).
- Universal operational policy concepts (`POLICY-1`), except where a typed
  safety-necessity field is required to avoid false green critical-phase
  claims.
- Marking chunk 02 covered-green. fn-51 owns coverage artifacts; this epic
  owns implementation surfaces.

## Acceptance

- [x] Critical-phase projection emits real `CriticalPhaseTransmission` facts
  from sim traces, not test-injected payloads.
- [x] Any safety-necessary exception classification is typed and review-aware;
  no catch-all "necessary" default is introduced. Until a reason-bearing
  policy type exists, projected critical-phase transmissions are classified as
  `Routine`.
- [x] Start-up approval/start evidence is based on real lifecycle state or an
  explicit model decision; mission-step completion alone is not treated as
  engine start.
- [x] Radio-test signal duration evidence can identify a typed test-signal
  transmission without rendered phraseology string matching.
- [x] Source-mapped fn-51 tests can be flipped from expected-gap only after the
  relevant evidence is real.

## Review Considerations

- FP / type safety: new evidence payloads and policy classifications must be
  sealed/closed and exhaustive. No nullable pseudo-state or string tag should
  decide regulatory meaning.
- Test architecture: repair tests should use real sim traces and then the
  source-mapped evidence DSL. Unit tests are acceptable only for formal mapping
  functions with independent oracles.
- Impact: these evidence surfaces are reusable beyond chunk 02. Keep them
  generic enough for later chunks, but do not build phraseology or policy
  infrastructure opportunistically.
- Operational correctness: cite ICAO 9432 §4.1.2, §4.2.3, and §2.8.4.4
  source-unit ids in tests/docs. Safety-exception handling must not erase the
  "should not transmit" critical-phase discipline. The §4.1.2 phase set is
  take-off, initial climb, late final, and landing roll; fn-52.1 must either
  observe each phase or leave the unobservable part explicit.
