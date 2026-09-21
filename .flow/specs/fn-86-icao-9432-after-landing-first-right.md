# fn-86-icao-9432-after-landing-first-right ICAO 9432 after-landing first-right vacating phraseology

## Overview
Close the remaining ICAO 9432 chunk 06 rendered-phraseology residual for
`icao9432-extracted::after_landing_4_9_en::df25159c1e7b94a3`.

fn-84 covered the `CONTACT GROUND` / frequency-readback branch of the §4.9
example. The residual branch is the vacating-runway instruction and readback:
`TAKE FIRST RIGHT WHEN VACATED` followed by `FIRST RIGHT, [frequency]
[callsign]`.

This epic is an evidence/phraseology slice. It should only mark the source unit
green if the renderer and evidence selectors prove the first-right/vacated
wording honestly. If the current protocol cannot represent the instruction or
readback without inventing semantics, the work must leave the residual visibly
blocked with an updated blocker note rather than claiming coverage.

## Scope
In scope:

- Re-read the existing `AfterLandingVacateVia`, `VacateReadback`,
  `ContactFrequency`, and `FrequencyReadback` protocol and renderer surfaces.
- Treat `PointId("FIRST_RIGHT")` as the only accepted phraseology-only
  synthetic witness for the Doc 9432 example's "FIRST RIGHT" branch. If
  `AfterLandingVacateVia.exit` or `VacateReadback.via` cannot carry that exact
  typed value through the renderer/evidence surface, leave the branch blocked.
- Render `AfterLandingVacateVia` only when
  `exit == PointId("FIRST_RIGHT")` and `whenAble == false`; every other
  `AfterLandingVacateVia` remains an explicit unsupported instruction.
- Add rendered phraseology for the narrow first-right/vacated branch when it can
  be represented with existing typed protocol leaves.
- Add source-mapped evidence selectors/tests that assert ordered rendered text,
  not just token presence.
- Update `EvidenceSourceCatalog`, catalog tests, chunk 06 docs, and
  `implementation_blocker_manifest.csv` if the residual branch is fully
  covered.

Out of scope:

- Helicopter `AIR-TAXI TO HELICOPTER STAND` phraseology
  (`203b53733da22603`).
- Essential aerodrome information phraseology (`7b81f87f5c2b4d75`).
- After-landing tower-frequency retention and taxi-timing policy rows.
- Changing aircraft movement, controller policy, or clearance sequencing.

## Approach
1. Inspect the existing after-landing protocol leaves and current unsupported
   renderer branches for `AfterLandingVacateVia` and `VacateReadback`.
2. If the protocol already carries enough typed data, add renderer templates and
   tokens for:
- `TAKE FIRST RIGHT WHEN VACATED`;
- `CONTACT GROUND [frequency]`;
- pilot composite readback containing `FIRST RIGHT`, the same frequency, and
  callsign.
3. Extend evidence selectors so the source-backed/synthetic record asserts the
   integrated ordered chain, including exact rendered text:
   - controller `FASTAIR 345 TAKE FIRST RIGHT WHEN VACATED`;
   - controller `FASTAIR 345 CONTACT GROUND 118.350`;
   - pilot `FIRST RIGHT 118.350 FASTAIR 345`.
   The chain must be for the same aircraft. The contact instruction must be
   `ContactFrequency(role = RoleName.GROUND, frequency = 118.350)`, and the
   readback frequency must be the same typed `Frequency`. For the synthetic
   source example, adjacency means adjacent within the evidence projection
   filtered to successful rendered controller phraseology and successful
   rendered pilot-readback phraseology facts for that aircraft; unsupported
   phraseology facts are not adjacency members, but the absence of the expected
   successful rendered facts must fail the selector. Do not require global
   `EvidenceFact` sequence adjacency because one transmission can emit multiple
   non-rendered facts.
   The composite pilot readback must bind both atoms: `VacateReadback` matches
   the preceding same-aircraft `AfterLandingVacateVia(exit = FIRST_RIGHT,
   whenAble = false)`, and `FrequencyReadback` matches the preceding
   same-aircraft `ContactFrequency(role = GROUND, frequency = 118.350)`.
   This typed binding is proven in renderer/adapter tests over typed
   `TransmissionRecord` values; the source selector itself proves the rendered
   text/order chain because rendered evidence payloads intentionally carry text,
   tokens, and `transmissionRef`, not the full originating protocol object.
4. Add selector tests for positive evidence and malformed/mismatched cases,
   including wrong exit side, missing `WHEN VACATED`, wrong frequency, and
   aircraft phraseology not being confused with taxi-to-stand evidence. Include
   a wrong-order test where all three rendered facts exist but contact-ground or
   readback appears before first-right. Put selector-algebra tests in
   `EvidenceDslTest` or an equivalent selector-focused test file, separate from
   the source-mapped ICAO scenario/test.
   Malformed/mismatch tests must include correct template with wrong token
   shape, correct tokens with wrong text, missing obligation kinds, and wrong
   aircraft/template branch.
   Expected selector outcomes:
   - absent expected chain: `Fail` with missing/empty evidence;
   - malformed expected-template facts: `Fail` with diagnostic evidence;
   - well-shaped text/token/frequency mismatch: `Fail` with diagnostic evidence;
   - irrelevant branch facts: do not satisfy the chain and must not activate as
     a pass.
5. Add only the narrow multi-atom pilot readback renderer needed for
   `VacateReadback(via = PointId("FIRST_RIGHT")) + FrequencyReadback`.
   Reversed, partial, extra, unrelated, wrong-`via`, or mixed
   `SimpleElement` / non-simple readback elements must remain typed unsupported
   evidence unless a separate source-backed plan justifies them.
6. Expected new rendered phraseology vocabulary is narrow:
   - templates: `AfterLandingVacateViaInstruction`,
     `FirstRightFrequencyReadback`;
   - tokens: `Take`, `First`, `Right`, `When`, and reuse existing `Vacated`,
     `FrequencyValue`, `AircraftCallsign`.
7. Audit every `RenderedPhraseologyTemplate` consumer after adding templates,
   including selector shape validation and any exhaustive `when` expressions.
   New templates must be supported only in the fn-86 selector path or
   explicitly rejected where irrelevant. In particular,
   `AuditRenderedVehicleDriverPhraseologySubject.hasValidShapeFor` must reject
   the new templates.
8. If coverage is claimed, update exactly these documentation/catalog surfaces
   together: `EvidenceSourceCatalog`, catalog tests, chunk 06 coverage/source
   docs, `implementation_blocker_manifest.csv`, and the existing fn-84
   residual sample wording in `Icao9432PhraseologyEvidenceTest`. If coverage is
   not claimed, update blocker wording to keep the residual visible instead.
   Preserve unrelated residuals either way.
9. Validate with focused tests, broad sim/core/protocol tests, detekt, flow
   validation, implementation review, completion review, and diff checks.

## Quick commands
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `./gradlew-nix detekt`
- `scripts/ralph/flowctl validate --epic fn-86-icao-9432-after-landing-first-right --json`
- `git diff --check`
- `scripts/ralph/flowctl codex impl-review fn-86-icao-9432-after-landing-first-right.1 --base 197c1947 --receipt .flow/.impl-review-receipt-fn86.json --json --sandbox read-only`
- `scripts/ralph/flowctl codex completion-review fn-86-icao-9432-after-landing-first-right --base 197c1947 --receipt .flow/.completion-review-receipt-fn86.json --json --sandbox read-only`

## Acceptance
- [ ] First-right/vacated controller phraseology is rendered from typed protocol
  evidence or remains explicitly blocked with no false coverage claim.
- [ ] The first-right branch uses `PointId("FIRST_RIGHT")` as the typed
  phraseology-only synthetic witness for both controller instruction and pilot
  readback; any other point value is a mismatch or remains blocked.
- [ ] `AfterLandingVacateVia` rendering succeeds only for
  `exit == PointId("FIRST_RIGHT")` and `whenAble == false`; all other
  `AfterLandingVacateVia` leaves remain unsupported.
- [ ] New template/token vocabulary is limited to
  `AfterLandingVacateViaInstruction`, `FirstRightFrequencyReadback`, and the
  needed first-right tokens (`Take`, `First`, `Right`, `When`) plus reused
  existing tokens.
- [ ] Pilot first-right/frequency readback phraseology is rendered and asserted
  when the controller branch is claimed covered.
- [ ] Source-mapped evidence for `df25159c1e7b94a3` proves ordered rendered text
  for the residual branch, not just tokens:
  `FASTAIR 345 TAKE FIRST RIGHT WHEN VACATED`;
  `FASTAIR 345 CONTACT GROUND 118.350`;
  `FIRST RIGHT 118.350 FASTAIR 345`.
- [ ] Pilot composite readback rendering supports exactly
  `VacateReadback + FrequencyReadback` for this branch and rejects reversed,
  partial, extra, and unrelated multi-atom readbacks.
- [ ] The readback frequency is bound to the same typed `Frequency` rendered in
  the preceding same-aircraft `ContactFrequency(role = RoleName.GROUND)`
  instruction in renderer/adapter tests over typed transmission records.
- [ ] The readback first-right atom is bound to the preceding same-aircraft
  `AfterLandingVacateVia(exit = PointId("FIRST_RIGHT"), whenAble = false)`
  instruction in renderer/adapter tests over typed transmission records.
- [ ] The source-mapped selector requires adjacent facts in the
  same-aircraft successful rendered-controller/rendered-readback projection for
  the first-right instruction, contact-ground instruction, and composite
  readback in that order.
- [ ] Selector tests distinguish absent evidence, malformed evidence,
  well-shaped mismatch, wrong frequency, wrong order, and wrong branch evidence.
- [ ] Malformed/mismatch tests include wrong token shape, wrong exact text,
  missing obligation kinds, wrong aircraft, and wrong template branch.
- [ ] All `RenderedPhraseologyTemplate` consumers are audited and updated for
  the new templates with exhaustive handling, including explicit rejection from
  `AuditRenderedVehicleDriverPhraseologySubject.hasValidShapeFor`.
- [ ] If coverage is claimed, update `EvidenceSourceCatalog`, catalog tests,
  chunk 06 coverage/source docs, `implementation_blocker_manifest.csv`, and
  existing fn-84 residual sample wording together. If coverage is not claimed,
  keep/update explicit blocker wording instead.
- [ ] Focused tests, broad relevant tests, detekt, flow validation,
  implementation review, completion review, and `git diff --check` pass.

## Review Considerations

- FP / type safety: Renderer support must be over existing typed protocol
  leaves or new typed leaves with exhaustive dispatch. Unsupported phraseology
  stays explicit; no catch-all string success.
- Test architecture: Selector tests prove the matching algebra. Source-mapped
  tests prove the actual residual branch and exact rendered text in order.
- Impact: This should only affect rendered phraseology/evidence. It must not
  change after-landing sequencing, controller policy, aircraft movement, or
  handoff behaviour.
- Operational correctness: ICAO Doc 9432 §4.9 is an example phraseology row.
  Coverage is for rendered wording only; it does not create a universal policy
  that every arrival must be instructed to take the first right.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_06_go_around_after_landing_aerodrome_info/coverage_report.md`
- `research/tools/requirements-spike/quality/icao9432_programme/chunk_06_go_around_after_landing_aerodrome_info/source_plan.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/RadioPhraseology.kt`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432PhraseologyEvidenceTest.kt`
