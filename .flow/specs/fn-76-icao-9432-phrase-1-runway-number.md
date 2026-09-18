# fn-76: ICAO 9432 PHRASE-1 runway-number takeoff clearance evidence

## Objective

Close the narrow rendered-phraseology facet of ICAO Doc 9432 §4.5.8 source unit
`icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef` as a **declared sampled-branch renderer evidence closure**.

Source text checked in `research/txt/icao9432-extracted.txt` lines 5329-5333: when several runways are in use and the pilot may be confused, the runway number should be stated in the take-off clearance.

## Scope

- Add permanent `ICAO9432.TakeoffProcedures.RunwayNumberInTakeoffClearance` with `RenderedPhraseologyTrace` scope.
- Include that ref in the catalog aggregate through the takeoff-procedures set so `EvidenceSourceCatalogTest` validates it.
- Add a source-backed sim evidence test using the existing LOWG departure/circuit trace and existing `renderedPhraseology(...).takeoffClearance(RunwayId("16C"))` selector.
- Make the activation limitation explicit:
  - `sample("several-runways-in-use", true)` and `sample("confusion-risk", true)` are branch declarations, not sim-observed activation facts.
  - The test proves rendered wording for that declared branch; it does not prove that the sim detects runway-confusion risk.
- Update chunk 04 programme docs and create `research/tools/requirements-spike/quality/icao9432_programme/fn76_phraseology_runway_number_manifest.md`.
- Move only `8af22...` from `phraseology-later` to `covered-green`.
- Do not move `13264a6ac6d529c3`; it remains support-only / review-only even though renderer support exists.

## Acceptance

- The test fails if no rendered takeoff clearance phraseology exists, if the runway token is absent, or if it names the wrong runway.
- The test and docs state that fn-76 does **not** add typed operational activation evidence for several-runway/confusion-risk detection.
- Chunk 04 counts become: `covered-green` 2, `policy-blocked` 9, `model-gap` 1, `phraseology-later` 7.
- Flow validation, focused tests, detekt, broad protocol/core/sim tests, and git whitespace checks pass before commit.

## Review considerations

### FP / type safety

No production state or sealed hierarchy is expected. The source ref must be typed and catalogued. Assertions must use `RenderedPhraseologyTemplate` and `PhraseologyToken`; no string-only assertion should become the source of truth.

### Test architecture

This is a high-level source-backed evidence test over a real sim run, not a low-level renderer unit. It is deliberately a declared-branch renderer proof, because there is not yet a typed fact for runway-confusion-risk activation.

### Impact

The work should not alter controller or pilot behaviour. The operational limitation is documented loudly instead of hidden: if future work requires real confusion-risk policy, that is a separate policy/model epic, not fn-76.

### Operational correctness

Regulatory claim: ICAO Doc 9432 §4.5.8 requires the runway number in take-off clearance where several runways are in use and possible pilot confusion exists. fn-76 proves the rendered `RUNWAY` + runway-designator phraseology for an explicitly declared branch only; it does not assert universal behaviour for every takeoff clearance or every aerodrome.
