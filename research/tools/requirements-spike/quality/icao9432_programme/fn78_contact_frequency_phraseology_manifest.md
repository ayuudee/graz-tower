# fn-78 contact-frequency phraseology manifest

## Source movement

Closed source unit:

- `icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc`

Movement:

- from: `phraseology-later` / `PHRASE-1`
- to: `covered-green` rendered phraseology

Evidence:

- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432PhraseologyEvidenceTest.kt`
- `EvidenceFactPayload.RenderedPhraseology`
- `EvidenceFactPayload.RenderedPilotReadbackPhraseology`
- `EvidenceAuditScope.RenderedPhraseologyTrace`

Covered phraseology surface:

- controller instruction: `CONTACT [unit] [frequency]`
- pilot readback: `[frequency] [callsign]`

## Residual PHRASE-1 scope

fn-78 does not close the wider phraseology blocker. These remain deliberately
outside this slice:

- `MONITOR [unit] [frequency]`
- unit-only frequency-transfer phraseology where no explicit frequency is present
- conditional forms such as `WHEN PASSING [level] CONTACT [unit] [frequency]`
- all-stations broadcast prefix rows
- initial-contact full-callsign rows
- readback-call-sign termination rows outside the concrete frequency readback

## Review considerations

- FP / type safety: unsupported contact-frequency renderings remain explicit
  typed evidence outcomes, not thrown exceptions. The renderer only produces
  the covered template when a frequency is present.
- Test architecture: the test is source-mapped and checks the rendered
  controller text and the rendered pilot readback text, rather than relying on
  typed `ContactFrequency` emission alone.
- Impact: the change adds rendered phraseology evidence for one already
  modelled transfer instruction without changing controller selection or pilot
  operational behaviour.
- Operational correctness: the cited source unit is ICAO Doc 9432 §2.8.2.1;
  broader local station-name policy is not asserted here.
