# fn-87-icao-9432-after-landing-helicopter-air.1 Implement helicopter air-taxi rendered phraseology evidence

## Description
Implement the fn-87 plan for the remaining ICAO 9432 §4.9 helicopter
air-taxi phraseology source unit
`icao9432-extracted::after_landing_4_9_en::203b53733da22603`.

This is a rendered evidence slice only. Do not implement helicopter movement,
helicopter mission behavior, essential-aerodrome-information phraseology, or
after-landing policy rows.

## Acceptance
- [ ] Re-read `AirTaxiTo`, `TaxiRouteReadback`, `InstructionReadback`, and
  current taxi rendered phraseology before editing.
- [ ] `AirTaxiTo` renders only for
  `destination == PointId("HELICOPTER_STAND")` and `via.isEmpty()`.
- [ ] Controller rendering uses a distinct `AirTaxiToInstruction` template,
  not `TaxiToStandInstruction`.
- [ ] `PointId("HELICOPTER_STAND")` displays as `HELICOPTER STAND` in rendered
  phraseology text; the typed point id remains unchanged, and source coverage
  still requires the paired air-taxi selector.
- [ ] Unsupported `AirTaxiTo` destinations and non-empty `via` routes remain
  explicit unsupported rendered phraseology evidence.
- [ ] Rendered controller phraseology proves exact text
  `G-HELI AIR-TAXI TO HELICOPTER STAND` for the synthetic source branch.
- [ ] Rendered pilot readback phraseology proves source-equivalent text
  `AIR-TAXI TO HELICOPTER STAND G-HELI` for
  `TaxiRouteReadback(destination = PointId("HELICOPTER_STAND"), via = emptyList())`,
  using the distinct `AirTaxiRouteReadback` template.
- [ ] `HELICOPTER_STAND` is intentionally treated as the narrow air-taxi
  rendered-readback sentinel because the typed readback atom has no
  controller-instruction context; ordinary readbacks to other destinations
  continue to use `TaxiRouteReadback`.
- [ ] Evidence selector proves the same-aircraft ordered pair and rejects
  ordinary taxi-to-stand evidence.
- [ ] Evidence selector requires adjacency in the same-aircraft rendered
  controller/readback projection and rejects an otherwise valid pair with an
  intervening rendered controller/readback fact.
- [ ] Evidence selector rejects a standalone rendered
  `AirTaxiRouteReadback(HELICOPTER_STAND)` when there is no preceding
  same-aircraft `AirTaxiToInstruction`.
- [ ] Evidence selector checks exact template, obligation policy, tokens, and
  rendered text for both the air-taxi controller fact and the readback fact.
- [ ] Route projection helpers used by the selector carry and validate
  `RenderedPhraseText` for both facts; route structure/template/token matches
  alone must not satisfy this source unit.
- [ ] Renderer/adapter tests cover positive typed output plus wrong
  `AirTaxiTo` destination and non-empty `via` unsupported controller variants.
- [ ] Renderer/adapter tests prove ordinary readback destinations other than
  `HELICOPTER_STAND` still render with `TaxiRouteReadback`.
- [ ] Selector tests cover correct `AirTaxiToInstruction` followed by a
  mismatched-destination readback and require failure; the mismatched readback
  is selector-rejected, not treated as a renderer-level unsupported variant.
- [ ] Selector tests using synthetic facts cover absent evidence, wrong text,
  missing obligations, wrong template, wrong order, wrong aircraft, and
  ordinary taxi branch rejection.
- [ ] Selector tests cover token-only malformed controller and readback facts:
  same template, text, aircraft, order, and obligations, but wrong token lists.
- [ ] Selector tests use a small explicit local factory or an existing helper
  whose callsite shows sequence, aircraft, template, obligations, tokens, and
  text.
- [ ] The synthetic source trace uses `OutstandingCoordination.expectedReadback`
  with `TaxiRouteReadback(PointId("HELICOPTER_STAND"), emptyList())`.
- [ ] The synthetic source trace orders controller before pilot readback using
  distinct transmission ids and monotonically later readback timing.
- [ ] All `RenderedPhraseologyTemplate` consumers are audited and updated if a
  template is added.
- [ ] `AuditRenderedPhraseologySubject.supportedControllerPhraseologyTemplates`
  includes `AirTaxiToInstruction`.
- [ ] The TAKE OFF word-use source-mapped fixture includes the synthetic
  `airTaxiToHelicopterStandRecord(aircraft)` controller record so the
  supported-template completeness check observes `AirTaxiToInstruction`.
- [ ] Exhaustive template consumers such as vehicle phraseology shape
  validation explicitly reject `AirTaxiToInstruction`.
- [ ] Route extraction helpers and after-landing selector projections handle
  `AirTaxiToInstruction` + `AirTaxiRouteReadback` separately from ordinary
  taxi-to-stand evidence, while unrelated selector helpers deliberately reject
  the new templates.
- [ ] `EvidenceSourceCatalog`, catalog tests, chunk 06 docs, and the central
  implementation blocker manifest are updated consistently.
- [ ] Update the `203b53733da22603` row in
  `chunk_06_go_around_after_landing_aerodrome_info/source_plan.md`,
  `coverage_report.md`, `expected_gaps.md`, and
  `implementation_blocker_manifest.csv`.
- [ ] Focused tests, broad tests, detekt, flow validation, implementation
  review, completion review, and `git diff --check` pass.
- [ ] Task evidence records `git diff --check` alongside the Gradle and
  flow-validation commands.
- [ ] `Done summary` and `Evidence` are filled with the shipped commit,
  validation commands, and review receipts before task close.

## Done summary
Implemented the ICAO 9432 §4.9 helicopter air-taxi rendered phraseology slice
for source unit `icao9432-extracted::after_landing_4_9_en::203b53733da22603`.
`AirTaxiTo(G-HELI, HELICOPTER_STAND)` now renders as
`G-HELI AIR-TAXI TO HELICOPTER STAND`, the matching structural
`TaxiRouteReadback(HELICOPTER_STAND)` renders as
`AIR-TAXI TO HELICOPTER STAND G-HELI`, and source coverage requires an
adjacent same-aircraft controller/readback pair. Unsupported controller shapes
remain unsupported; ordinary non-helicopter-stand route readbacks continue to
use the ordinary taxi-route template.

## Evidence
- Commits:
  - `fn-87 cover helicopter air-taxi phraseology`
- Tests:
  - `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest'`
  - `./gradlew-nix detekt`
  - `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
  - `scripts/ralph/flowctl validate --epic fn-87-icao-9432-after-landing-helicopter-air --json`
  - `git diff --check`
- Reviews:
  - Plan review: `.flow/.plan-review-receipt-fn87-r8.json` (`SHIP`)
  - Implementation review: `.flow/.impl-review-receipt-fn87-r3.json` (`SHIP`)
  - Completion review: `.flow/.completion-review-receipt-fn87-r3.json` (`SHIP`)
- PRs:
