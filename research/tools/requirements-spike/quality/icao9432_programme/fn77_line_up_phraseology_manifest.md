# fn-77 Line-Up Phraseology Movement Manifest

Scope: ICAO 9432 §4.5.3 line-up instruction and acknowledgement phraseology.

## Moved Source Unit

| Source unit | Previous state | New state | Evidence |
|---|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03` | `phraseology-later` | `covered-green` rendered phraseology | `Icao9432PhraseologyEvidenceTest`; `RenderedPhraseologyTrace` proves rendered controller `RUNWAY [designator] LINE UP AND WAIT` and pilot `LINING UP [callsign]` acknowledgement in a LOWG trace. |

## Residual Limitation

fn-77 adds a deliberately narrow rendered pilot-readback proof surface for
single-atom `LineUpReadback` only. It does not introduce a general rendered
readback system for every `AtomicReadback`.

## Explicit Non-Movements

- `icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3`
  remains `phraseology-later` / support-only / review-only.
- Immediate-departure line-up (`152f0ffb84869af5`), taxi-ambiguity
  (`db8a2c3dcd586b0e`), conditional-clearance order (`9b30810984e06a35`),
  and stop-immediately repetition (`6b5a0d8b27525cbd`) remain `PHRASE-1`.
