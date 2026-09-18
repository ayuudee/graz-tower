## Description

Close only `icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc` by adding rendered phraseology evidence for ICAO 9432 §2.8.2.1 contact-frequency transfer: controller `CONTACT [unit] [frequency]`, pilot `[frequency] [callsign]`.

Required work:

- Add typed controller phraseology support for `ContactFrequency` with explicit frequency.
- Add narrow typed pilot-readback phraseology support for single-atom `FrequencyReadback`.
- Add selectors for controller contact-frequency phraseology and pilot frequency readback phraseology.
- Add `ICAO9432.TransferCommunications.ContactFrequencyPhrase` with `RenderedPhraseologyTrace` scope and catalog coverage.
- Add a LOWG source-backed sim evidence test for OE-ABC contact to TOWER 118.200 and matching pilot readback.
- Update chunk 01 docs, central manifest, fn-78 manifest, and self-assessment.

Review considerations:

- FP / type safety: typed templates/tokens/results; unsupported variants stay explicit.
- Test architecture: source movement relies on real sim evidence.
- Impact: no behaviour changes to handoff/frequency transfer.
- Operational correctness: cite ICAO Doc 9432 §2.8.2.1 and avoid local station-name overclaim.

## Acceptance

- Test fails if `CONTACT`, unit, frequency, readback frequency, or callsign rendered tokens are missing/wrong.
- No adjacent PHRASE-1 rows move.
- Focused tests, detekt, broad tests, flow validation, impl review, completion review, and `git diff --check` pass before push.

## Done summary
Closed the ICAO 9432 §2.8.2.1 contact-frequency phraseology row with source-mapped rendered phraseology evidence. Added controller CONTACT [unit] [frequency] rendering, pilot [frequency] [callsign] readback rendering, DSL selectors, catalog coverage, and programme manifest updates while leaving residual PHRASE-1 scope explicit.
## Evidence
- Commits:
- Tests:
- PRs: