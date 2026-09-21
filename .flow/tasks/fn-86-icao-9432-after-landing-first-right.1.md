# fn-86-icao-9432-after-landing-first-right.1 Implement first-right vacating phraseology evidence

## Description
Implement the fn-86 plan for the remaining ICAO 9432 §4.9 first-right/vacated
phraseology branch:

- controller wording: `TAKE FIRST RIGHT WHEN VACATED`;
- pilot readback: canonical rendered text
  `FIRST RIGHT 118.350 FASTAIR 345` for the FASTAIR synthetic branch.

This is a rendered evidence slice only. Do not implement helicopter air-taxi,
essential-aerodrome-information phraseology, after-landing frequency-retention
policy, or taxi-timing policy in this task.

## Acceptance
- [x] Re-read the current `AfterLandingVacateVia`, `VacateReadback`,
  `ContactFrequency`, and `FrequencyReadback` code paths before editing.
- [x] Rendered phraseology/evidence either covers the complete first-right
  branch or leaves the branch explicitly blocked.
- [x] Coverage, if claimed, uses `PointId("FIRST_RIGHT")` as the exact typed
  phraseology-only synthetic witness for both `AfterLandingVacateVia.exit` and
  `VacateReadback.via`.
- [x] `AfterLandingVacateVia` renders only for
  `exit == PointId("FIRST_RIGHT")` and `whenAble == false`; all other exits or
  `whenAble` values remain unsupported.
- [x] Add only the narrow templates/tokens required for this branch:
  `AfterLandingVacateViaInstruction`, `FirstRightFrequencyReadback`, `Take`,
  `First`, `Right`, `When`, and reused existing tokens.
- [x] Evidence selectors assert the integrated ordered chain:
  `FASTAIR 345 TAKE FIRST RIGHT WHEN VACATED` ->
  `FASTAIR 345 CONTACT GROUND 118.350` ->
  `FIRST RIGHT 118.350 FASTAIR 345`.
- [x] The selected evidence chain is adjacent: no unrelated rendered fact may
  sit between the first-right instruction, contact-ground instruction, and
  composite readback in the same-aircraft rendered-controller/rendered-readback
  projection. Global evidence-fact adjacency is not required.
- [x] The pilot readback frequency is bound to the same typed `Frequency` from
  the preceding same-aircraft `ContactFrequency(role = RoleName.GROUND)`
  instruction in renderer/adapter tests over typed transmission records.
- [x] The pilot first-right readback atom is bound to the preceding
  same-aircraft `AfterLandingVacateVia(exit = PointId("FIRST_RIGHT"),
  whenAble = false)` instruction in renderer/adapter tests over typed
  transmission records.
- [x] Multi-atom pilot readback rendering is limited to the exact
  `VacateReadback(via = PointId("FIRST_RIGHT")) + FrequencyReadback` shape;
  reversed, partial, extra, wrong-`via`, and unrelated multi-atom shapes remain
  unsupported, as do mixed `SimpleElement` / non-simple readbacks.
- [x] Selector tests cover positive, absent, malformed, mismatch,
  wrong-frequency, wrong-order, and wrong-branch cases in `EvidenceDslTest` or
  an equivalent selector-focused test file.
- [x] Malformed/mismatch tests include wrong token shape, wrong exact text,
  missing obligation kinds, wrong aircraft, and wrong template branch.
- [x] Audit all `RenderedPhraseologyTemplate` consumers and update exhaustive
  branches for the new templates, including explicit rejection from
  `AuditRenderedVehicleDriverPhraseologySubject.hasValidShapeFor`.
- [x] If coverage is claimed, update `EvidenceSourceCatalog`, catalog tests,
  chunk 06 docs, `implementation_blocker_manifest.csv`, and existing fn-84
  residual sample wording together. If coverage is not claimed, leave/update
  the residual blocker visibly instead.
- [x] Existing fn-84 residual wording in catalog/test samples is removed or
  renamed when the first-right branch becomes covered.
- [x] Focused tests, broad tests, detekt, flow validation, implementation
  review, completion review, and `git diff --check` pass.

## Done summary
fn-86 implemented narrow ICAO 9432 §4.9 first-right vacating/contact-ground rendered phraseology evidence. The synthetic typed trace now covers TAKE FIRST RIGHT WHEN VACATED, CONTACT GROUND 118.350, and FIRST RIGHT 118.350 FASTAIR 345; chunk 06 residual bookkeeping is retired.
## Evidence
- Commits:
- Tests:
- PRs: