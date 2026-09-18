# fn-80 stop-immediately phraseology manifest

## Source movement

Split source unit:

- `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd`

Movement:

- from: `phraseology-later` / `PHRASE-1`
- to: `split: rendered phraseology covered; takeoff-roll/dangerous-traffic trigger policy blocked`

Source verification:

- `research/txt/icao9432-extracted.txt` §4.5.11 contains the example
  `FASTAIR 345 STOP IMMEDIATELY FASTAIR 345 STOP IMMEDIATELY`.
- The accepted programme row maps the quote "the aircraft should be instructed
  to stop immediately and the instruction and call sign repeated" to source
  unit `6b5a0d8b27525cbd`.

Evidence:

- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432PhraseologyEvidenceTest.kt`
- `EvidenceFactPayload.RenderedPhraseology`
- `EvidenceAuditScope.RenderedPhraseologyTrace`

Covered phraseology surface:

- controller instruction: `[callsign] STOP IMMEDIATELY [callsign] STOP IMMEDIATELY`
- exact structural token order: callsign, STOP, IMMEDIATELY, callsign, STOP,
  IMMEDIATELY

## Remaining blocked scope

fn-80 does not prove:

- an aircraft has commenced take-off roll
- dangerous traffic exists
- the controller's policy decision to select `StopImmediately`
- pilot compliance, runway-duty lifecycle, or live emergency scenario behaviour

## Review considerations

- FP / type safety: `StopImmediately` uses a typed rendered phraseology template
  and explicit tokens. Unsupported instruction paths remain typed unsupported.
- Test architecture: the source-mapped test feeds a typed synthetic transmission
  through `EvidenceFactAdapters.fromTransmissionRecords`, exercising the same
  projection path used by real transmissions.
- Impact: production change is limited to pure phraseology rendering plus a
  public emergency-policy construction helper for typed controller output
  fixtures.
- Operational correctness: the source row is ICAO Doc 9432 §4.5.11; operational
  trigger semantics remain blocked rather than falsely green.
