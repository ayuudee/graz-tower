# fn-78: ICAO 9432 PHRASE-1 contact-frequency phraseology evidence

## Objective

Close the narrow ICAO Doc 9432 §2.8.2.1 phraseology source unit
`icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc` by proving rendered controller `CONTACT [unit] [frequency]` phraseology and rendered pilot `[frequency] [callsign]` readback over a real LOWG trace.

Source text checked in `research/txt/icao9432-extracted.txt` lines 3830-3837: `FASTAIR 345 CONTACT ALEXANDER CONTROL 129.1` and `129.1 FASTAIR 345`.

## Scope

- Add typed controller phraseology support for `ContactFrequency` when an explicit frequency is present.
- Add narrow pilot-readback phraseology support for single-atom `FrequencyReadback(frequency, role)`.
- Add DSL selectors for contact-frequency controller phraseology and frequency readback phraseology.
- Add `ICAO9432.TransferCommunications.ContactFrequencyPhrase` with `RenderedPhraseologyTrace` scope and catalog coverage.
- Add a LOWG source-backed sim evidence test asserting `ContactFrequency(role=TOWER, frequency=118.200)` rendered phraseology and pilot frequency readback for OE-ABC.
- Update chunk 01 docs/manifests and central implementation manifest, plus fn-78 manifest/self-assessment.

## Non-scope

- Do not close MONITOR phraseology, unit-only contact-frequency instructions without explicit frequency, conditional `WHEN PASSING` transfer phraseology, or all-stations/full-callsign communications rows.
- Do not alter frequency-transfer behaviour; this is rendered evidence only.

## Acceptance

- Source-backed test fails if controller `CONTACT` + unit + frequency tokens are missing/wrong, or if pilot frequency/callsign readback tokens are missing/wrong.
- Unsupported `ContactFrequency` without explicit frequency and unsupported readback shapes remain typed unsupported evidence.
- Chunk 01 phraseology count/manifest move only `96720...` to `covered-green` rendered phraseology.
- Plan review, focused tests, detekt, broad tests, impl review, completion review, flow validation, and `git diff --check` pass before push.

## Review considerations

### FP / type safety

Use typed templates/tokens/results. No string parsing should be authoritative. Unsupported variants must not silently render.

### Test architecture

The source movement depends on a real sim trace and evidence DSL assertions. Unit-level projection/selector tests may support the shape but do not replace the source-backed test.

### Impact

This work touches phraseology rendering/projection/catalog/docs only. It must not alter controller handoff, pilot radio switching, or frequency-transfer semantics.

### Operational correctness

Citation is ICAO Doc 9432 §2.8.2.1. The unit name is the sim role name (`TOWER`) rather than a real-world station name; the test proves the source's structural phraseology slots (`CONTACT`, unit, frequency, readback frequency, callsign), not local callsign naming policy.
