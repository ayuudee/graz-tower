# fn-76 Phraseology Runway-Number Movement Manifest

Scope: ICAO 9432 §4.5.8 rendered runway-number phraseology for take-off
clearance.

## Moved Source Unit

| Source unit | Previous state | New state | Evidence |
|---|---|---|---|
| `icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef` | `phraseology-later` | `covered-green` declared-branch rendered phraseology | `Icao9432PhraseologyEvidenceTest`; `RenderedPhraseologyTrace` proves the rendered take-off clearance contains `RUNWAY` plus the active runway designator token for a declared several-runways / confusion-risk branch. |

## Residual Limitation

fn-76 does not add a typed operational activation model for detecting when
several runways are in use or when a pilot may be confused. The source-backed
test declares those branch conditions as samples and proves the rendered
phraseology for that branch. A future policy/model epic would be required if the
system must decide the confusion-risk condition dynamically.

## Explicit Non-Movement

`icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3`
remains `phraseology-later` / support-only / review-only. Existing take-off
clearance renderer support continues to serve as evidence infrastructure rather
than a standalone green movement for that support-only row.
