# fn-79 take-off clearance phraseology manifest

## Source movement

Closed source unit:

- `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3`

Movement:

- from: `phraseology-later` / `PHRASE-1`; support-only / review-only
- to: `covered-green` rendered phraseology

Source verification:

- `research/txt/icao9432-extracted.txt` contains the §4.5 example line
  `G-CD RUNWAY 06 CLEARED FOR TAKE-OFF`.
- The accepted programme row maps that quote to source unit
  `13264a6ac6d529c3`.

Evidence:

- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432PhraseologyEvidenceTest.kt`
- `EvidenceFactPayload.RenderedPhraseology`
- `EvidenceAuditScope.RenderedPhraseologyTrace`

Covered phraseology surface:

- controller instruction: `RUNWAY [designator] CLEARED FOR TAKE-OFF`

## Residual PHRASE-1 scope

fn-79 does not close these chunk-04 rows:

- immediate-departure line-up phraseology
- immediate-departure readiness query phraseology
- taxi phraseology ambiguity against runway-entry/take-off clearance
- conditional-clearance order
- stop-immediately repetition and callsign

fn-79 also does not change fn-76's separate declared-branch closure for stating
the runway number where confusion is possible.

## Review considerations

- FP / type safety: no new production state or renderer branch is introduced.
  The existing typed rendered phraseology selector is cited by a new source ref.
- Test architecture: the source-mapped test asserts rendered token structure
  through the DSL, not only `ClearedForTakeoff` emission.
- Impact: this is catalog/test/docs movement over existing rendering behaviour.
  No operational simulator behaviour changes.
- Operational correctness: the cited source is ICAO Doc 9432 §4.5 example
  phraseology; the closure is phraseology-only.
