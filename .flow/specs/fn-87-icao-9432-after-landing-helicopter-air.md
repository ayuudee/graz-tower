# fn-87-icao-9432-after-landing-helicopter-air ICAO 9432 after-landing helicopter air-taxi phraseology

## Overview
Close the ICAO 9432 §4.9 helicopter air-taxi phraseology source unit
`icao9432-extracted::after_landing_4_9_en::203b53733da22603`.

The accepted source quotes are:

- `G-HELI AIR-TAXI TO HELICOPTER STAND`
- `AIR-TAXI TO HELICOPTER STAND, G-HELI`

This is a rendered phraseology/evidence slice. It must not create a general
helicopter ground-movement model, helicopter mission flow, or operational
policy. The only claimed source-mapped behavior is the rendered wording for a
synthetic typed `AirTaxiTo` instruction and matching pilot readback.

## Scope
- In scope:
  - Render `AirTaxiTo(target = G-HELI, destination = PointId("HELICOPTER_STAND"), via = emptyList())`
    as `G-HELI AIR-TAXI TO HELICOPTER STAND`.
  - Add a narrow `AirTaxiToInstruction` controller template so air-taxi
    evidence cannot be confused with ordinary `TaxiToStandInstruction`.
  - Add only the tokens needed for the controller phrase, e.g. `AirTaxi` or
    `Air` + `Taxi`, while reusing `To`, `PointName`, and aircraft callsign
    tokens where appropriate.
  - Add a narrow `AirTaxiRouteReadback` rendered readback template for
    `TaxiRouteReadback(destination = PointId("HELICOPTER_STAND"), via = emptyList())`.
    That typed atom renders with template `AirTaxiRouteReadback`, tokens for
    `AIR-TAXI TO`, `PointName(HELICOPTER_STAND)`, and aircraft callsign, and
    text `AIR-TAXI TO HELICOPTER STAND G-HELI`. Ordinary non-air-taxi route
    readbacks to other destinations continue to use `TaxiRouteReadback`.
    `HELICOPTER_STAND` is intentionally treated as this narrow air-taxi
    rendered-readback sentinel because the typed readback atom has no
    controller-instruction context.
    Source coverage still depends on a selector that pairs it with the
    preceding `AirTaxiToInstruction`; a standalone air-taxi readback fact must
    not green the source.
  - Extend rendered point display so `PointId("HELICOPTER_STAND")` displays
    as `HELICOPTER STAND` in rendered phraseology text. This may affect any
    phraseology renderer that intentionally renders that point as text; it must
    not change protocol ids, route matching, movement geometry, or
    non-phraseology point identity.
  - Add source-mapped synthetic transmission-record evidence through
    `EvidenceFactAdapters.fromTransmissionRecords`.
  - Update source catalog/tests, chunk 06 docs, and the central blocker
    manifest if coverage is claimed.
- Out of scope:
  - Helicopter actor/mission behavior, real air-taxi movement, or Step.kt
    semantics.
  - General arbitrary-destination `AirTaxiTo` rendered phraseology.
  - Essential-aerodrome-information phraseology, after-landing policy rows, and
    helicopter stand geometry.

## Approach
1. Re-read `AirTaxiTo`, `TaxiRouteReadback`, `InstructionReadback`, and the
   existing rendered taxi-to-stand/taxi-route phraseology.
2. Add a narrow `AirTaxiTo` controller renderer:
   - It succeeds only for `destination == PointId("HELICOPTER_STAND")` and
     `via.isEmpty()`.
   - Any other destination or non-empty `via` remains
     `UnsupportedInstruction`.
   - The text is exactly `G-HELI AIR-TAXI TO HELICOPTER STAND` for the
     source-mapped synthetic branch.
3. Add `RenderedPhraseologyTemplate.AirTaxiToInstruction` and a narrow
   controller phraseology helper.
   - Do not reuse `TaxiToStandInstruction` for the controller side.
   - Add only the minimum token vocabulary needed to represent `AIR-TAXI TO`.
4. Add a rendered-phraseology display helper for the helicopter-stand point:
   `PointId("HELICOPTER_STAND") -> "HELICOPTER STAND"`.
   - The helper may be used by route readback rendering generally, because
     that renderer is context-free.
   - It must not change protocol ids, route matching, movement geometry, or
     general point display outside this slice.
5. Add an after-landing/helicopter-air-taxi selector that requires the
   same-aircraft ordered rendered controller/readback pair:
   `AIR-TAXI TO HELICOPTER STAND` -> `AIR-TAXI TO HELICOPTER STAND G-HELI`.
   Adjacency is within the same-aircraft successful rendered
   controller/readback projection, not global fact adjacency.
   A same-aircraft intervening rendered controller/readback fact in that
   filtered projection must break the match.
   A match requires exact template, exact/contains-all obligation policy as
   used by existing phraseology selectors, exact token list, and exact rendered
   text for both the controller instruction and pilot readback.
   The air-taxi route projection helpers must carry `RenderedPhraseText` and
   validate it for both facts; template/tokens/route structure alone are not
   sufficient for this source unit.
   A standalone rendered `AirTaxiRouteReadback(HELICOPTER_STAND)` with no
   preceding same-aircraft `AirTaxiToInstruction` must fail.
6. Add focused tests in two layers:
   - Renderer/adapter tests using typed transmission records cover the real
     `AirTaxiTo` and `TaxiRouteReadback` output plus unsupported typed
     controller variants such as wrong `AirTaxiTo` destination and non-empty
     `via`. A mismatched readback destination remains renderable as ordinary
     context-free readback phraseology where the existing renderer supports it;
     selector tests, not renderer unsupported tests, prove that it does not
     satisfy this source unit.
   - Selector tests using synthetic `EvidenceFact` payloads cover malformed
     wrong text, missing obligation kinds, wrong template, wrong order, wrong
     aircraft, absent evidence, and ordinary taxi-to-stand branch rejection.
     Introduce or reuse a small local test factory that builds the required
     `EvidenceFactSet` with explicit sequence, aircraft, template,
     obligations, tokens, and rendered text. Do not hide those fields behind
     ad hoc opaque fact construction.
     Include token-only malformed cases for both controller and readback:
     template, text, aircraft, order, and obligations otherwise valid, but the
     token list is wrong.
   - Route extraction/projection helpers used by after-landing selectors must
     handle `AirTaxiToInstruction` + `AirTaxiRouteReadback` as a separate
     branch from ordinary `TaxiToStandInstruction` + `TaxiRouteReadback`.
     Helpers outside that selector's domain must deliberately reject the new
     templates rather than accidentally decoding them as ordinary taxi.
7. Add a source-mapped `Icao9432PhraseologyEvidenceTest` case using synthetic
   typed transmission records:
   - `ControllerOutput.Instruct.fromCoordinationReissue` carrying
     `AirTaxiTo(G-HELI, HELICOPTER_STAND)`.
   - `OutstandingCoordination.expectedReadback =
     setOf(TaxiRouteReadback(PointId("HELICOPTER_STAND"), emptyList()))`.
   - Pilot `Readback(SimpleElement(TaxiRouteReadback(HELICOPTER_STAND)))`
     rendered as `AIR-TAXI TO HELICOPTER STAND G-HELI`.
   - The controller record must precede the pilot readback record, with
     distinct transmission ids and monotonically later pilot `time`/`endedAt`.
8. Update docs/catalog/manifest after tests prove coverage.
9. Audit the `AuditRenderedPhraseologySubject.supportedControllerPhraseologyTemplates`
   TAKE OFF word-use invariant explicitly:
   - Include `AirTaxiToInstruction` in the supported controller template set.
   - Add the synthetic `airTaxiToHelicopterStandRecord(aircraft)` controller
     record to the source-mapped TAKE OFF word-use combined evidence fixture so
     the supported-template completeness check observes the new template.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `scripts/ralph/flowctl validate --epic fn-87-icao-9432-after-landing-helicopter-air --json`

## Acceptance
- [ ] `AirTaxiTo` rendering is narrow: only `PointId("HELICOPTER_STAND")`
  with an empty `via` route renders; all other typed-valid `AirTaxiTo` leaves
  remain unsupported evidence.
- [ ] Source-mapped evidence proves exact controller text
  `G-HELI AIR-TAXI TO HELICOPTER STAND`.
- [ ] Source-mapped evidence proves exact pilot readback text equivalent to
  `AIR-TAXI TO HELICOPTER STAND, G-HELI` as
  `AIR-TAXI TO HELICOPTER STAND G-HELI` using the repository's no-punctuation
  rendered phrase style.
- [ ] The selector proves the ordered same-aircraft controller/readback pair
  with adjacency in the same-aircraft rendered controller/readback projection,
  and does not accept ordinary taxi-to-stand evidence.
- [ ] The selector rejects an otherwise valid same-aircraft controller/readback
  pair when an intervening rendered controller/readback fact appears between
  them in the filtered projection.
- [ ] The selector validates exact `RenderedPhraseText` on both the
  `AirTaxiToInstruction` and `AirTaxiRouteReadback` facts; route tokens,
  templates, and obligations alone are insufficient.
- [ ] A standalone `AirTaxiRouteReadback(HELICOPTER_STAND)` without a preceding
  same-aircraft `AirTaxiToInstruction` fails the source selector.
- [ ] `PointId("HELICOPTER_STAND")` displays as `HELICOPTER STAND` only in
  rendered phraseology text; typed point identity remains `HELICOPTER_STAND`,
  and source coverage still requires the air-taxi paired selector.
- [ ] Unsupported controller shapes are tested: wrong `AirTaxiTo` destination
  and non-empty `via`.
- [ ] Selector rejection shapes are tested: mismatched readback destination,
  wrong text, malformed controller tokens, malformed readback tokens, missing
  obligations, wrong template, wrong aircraft, absent evidence, wrong order,
  intervening same-aircraft rendered phraseology, and ordinary taxi-to-stand
  evidence.
- [ ] Renderer tests prove ordinary readback destinations other than
  `HELICOPTER_STAND` still use the ordinary `TaxiRouteReadback` template.
- [ ] All `RenderedPhraseologyTemplate` consumers are audited and updated for
  any new template, including explicit rejection where the template is outside
  a selector's domain.
- [ ] `AirTaxiToInstruction` is considered explicitly in the TAKE OFF word-use
  supported-template invariant and its source-mapped fixture.
- [ ] Exhaustive template consumers are updated, including vehicle phraseology
  shape rejection, supported controller template sets, and any tests that
  enumerate rendered templates.
- [ ] Route extraction helpers and after-landing selector projections handle
  `AirTaxiToInstruction` + `AirTaxiRouteReadback` separately from ordinary taxi
  evidence, while unrelated selector helpers deliberately reject the new
  templates.
- [ ] `EvidenceSourceCatalog`, catalog tests, chunk 06 docs, and
  `implementation_blocker_manifest.csv` are updated consistently.
- [ ] Update these exact rows/paths for
  `icao9432-extracted::after_landing_4_9_en::203b53733da22603`:
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_06_go_around_after_landing_aerodrome_info/source_plan.md`,
  `coverage_report.md`, `expected_gaps.md`, and
  `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`.
- [ ] Focused tests, broad tests, detekt, flow validation, implementation
  review, completion review, and `git diff --check` pass.

## Review considerations
- FP / type safety: Keep all unsupported `AirTaxiTo` variants as explicit
  unsupported evidence, not broad string rendering. Avoid catch-all `else`
  branches in sealed `when` expressions.
- Test architecture: Use a synthetic typed transmission trace because current
  LOWG scenarios do not exercise helicopter air taxi. Selector tests must
  independently protect text, token/template, obligation, aircraft, and order;
  renderer tests protect typed output and unsupported typed variants.
- Impact: This should not change controller selection, pilot behavior,
  movement, or helicopter simulation. It is evidence/renderer-only.
- Operational correctness: Cite ICAO Doc 9432, Manual of Radiotelephony,
  Fourth Edition, 2007, §4.9. Treat the example as rendered phraseology
  support, not universal operational law.

## References
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/after_landing_4_9_en/icao9432-extracted::after_landing_4_9_en::203b53733da22603.json`
- `research/txt/icao9432-extracted.txt` lines around §4.9.
