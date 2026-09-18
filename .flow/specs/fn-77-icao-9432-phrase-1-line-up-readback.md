# fn-77: ICAO 9432 PHRASE-1 line-up/readback phraseology evidence

## Objective

Close the narrow ICAO Doc 9432 §4.5.3 source unit
`icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03` by proving rendered controller line-up phraseology (`LINE UP AND WAIT`) and rendered pilot acknowledgement/readback (`LINING UP [callsign]`) over a real LOWG trace.

Source text checked in `research/txt/icao9432-extracted.txt` lines 5150-5161: after the pilot reports ready, the controller says `G-CD LINE UP AND WAIT` and the pilot acknowledges `LINING UP G-CD`.

## Scope

- Add `LineUpAndWait` support to the controller phraseology renderer with typed tokens for callsign, runway designator, `LINE`, `UP`, `AND`, `WAIT`.
- Add a deliberately tiny pilot-readback phraseology renderer for `Readback` transmissions whose sole atom is `LineUpReadback(runway)`, producing tokens for `LINING`, `UP`, and aircraft callsign.
- Add evidence payload/projection/DSL selectors for rendered pilot readback phraseology, emitted only for clear pilot transmissions.
- Add a permanent `ICAO9432.TakeoffProcedures.LineUpAndWaitPhrase` source ref with `RenderedPhraseologyTrace` scope and include it in the catalog aggregate.
- Add a source-backed sim test using a LOWG departure/circuit trace that asserts both rendered controller `LineUpAndWait` and pilot `LineUpReadback` phraseology for OE-ABC / runway 16C.
- Update chunk 04 docs, central manifest, and add an fn-77 movement manifest/self-assessment.

## Non-scope

- Do not create a broad rendered readback system for every `AtomicReadback`.
- Do not close immediate-departure line-up (`152f0...`), taxi-ambiguity (`db8...`), conditional-clearance ordering (`9b30...`), or stop-immediately repetition (`6b5...`). Those require additional templates/conditions.
- Do not move support-only `13264a6ac6d529c3`.

## Acceptance

- The source-backed test fails if the controller `LINE UP AND WAIT` rendered template is missing/wrong, or if the pilot `LINING UP` readback rendered template is missing/wrong.
- Unsupported controller instructions and unsupported pilot readbacks remain loud typed unsupported evidence or absent by documented scope; no silent generic phraseology default is introduced.
- Chunk 04 counts become: `covered-green` 3, `policy-blocked` 9, `model-gap` 1, `phraseology-later` 6.
- Plan review, focused tests, detekt, broad `:protocol:allTests :core:allTests :sim:jvmTest`, impl review, completion review, flow validation, and `git diff --check` pass before push.

## Review considerations

### FP / type safety

Token templates must be typed; renderer `when` branches must stay exhaustive over the explicit supported scope and return typed unsupported results for anything not implemented. No string parsing should be authoritative when token lists can express the obligation.

### Test architecture

This remains a high-level source-backed evidence test over a real sim trace. Unit tests may support renderer/projection edges, but the source movement depends on the sim evidence DSL.

### Impact

The work touches evidence rendering/projection only. It must not alter controller, pilot, mission, or radio timing behaviour. The readback renderer is intentionally narrow to avoid inventing a phraseology layer wider than the source unit needs.

### Operational correctness

Citation is ICAO Doc 9432 §4.5.3. The test proves the example phraseology/acknowledgement pair for line-up, not every possible local variant or immediate-departure phraseology.
