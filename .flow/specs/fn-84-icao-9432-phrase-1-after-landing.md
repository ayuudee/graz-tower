# fn-84-icao-9432-phrase-1-after-landing ICAO 9432 PHRASE-1 after-landing phraseology split

## Overview
Close the ICAO 9432 §4.9 after-landing phraseology surface that the current
typed protocol can honestly represent, without claiming coverage for the parts
that still need richer movement/essential-information modelling.

The chunk 06 programme still has four `phraseology-later` rows. This epic is a
narrow PHRASE-1 split:

- `after_landing_4_9_en::e30350fdecad45a1`: cover the rendered wording for the
  already-modelled `RUNWAY VACATED` pilot report, `TAXI TO STAND ... VIA ...`
  controller instruction, and stand/via readback.
- `after_landing_4_9_en::df25159c1e7b94a3`: cover only the `CONTACT GROUND
  118.350` / frequency readback branch via the existing `ContactFrequency`
  renderer; keep `TAKE FIRST RIGHT WHEN VACATED` residual because there is no
  current rendered vacating-runway/readback surface.
- `after_landing_4_9_en::203b53733da22603`: remain phraseology-later because
  helicopter air-taxi phraseology is not currently rendered.
- `essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75`: remain
  phraseology-later because essential aerodrome information text is not
  currently rendered.

## Scope
In scope:

- Add typed `EvidenceSourceRef`s for the two after-landing example rows, using
  `RenderedPhraseologyTrace` claim scope and titles that make split residuals
  explicit.
- Extend the phraseology renderer only for already-modelled protocol leaves:
  `TaxiToStand`, `ReportEvent.RunwayVacated`, and generic single-atom
  `TaxiRouteReadback` records. Do not project the readback as stand-bound by
  itself; the source-mapped §4.9 claim must pair it with a matching
  `TaxiToStand` rendered instruction in the same evidence case. Do not add
  `AirTaxiTo` rendered phraseology in this task.
- Add evidence DSL selectors for the new rendered templates.
- Add source-mapped tests against a real LOWG full-stop trace where possible.
  Synthetic records are acceptable only if the production trace does not emit
  the exact after-landing phrase branch needed; the test must say so.
- Update chunk 06 programme docs so the row states reflect covered/split
  residuals honestly.

Out of scope:

- No new taxi policy, tower-frequency-retention policy, landing-roll timing
  policy, or absolute-necessity judgement.
- No helicopter air-taxi phraseology unless the existing typed protocol and
  readback renderer already support it without broad new semantics. This task
  deliberately does not render `AirTaxiTo`, so
  `after_landing_4_9_en::203b53733da22603` remains phraseology-later.
- No rendered essential-aerodrome-information phraseology.
- No claim that ICAO example sequencing is fully modelled; this is wording
  evidence over supported typed transmissions.

## Approach
1. Add after-landing phraseology templates/tokens for:
   - controller `TaxiToStand`: callsign, `TAXI`, `TO`, stand point, optional
     `VIA` route points;
   - pilot report `RunwayVacated`: `RUNWAY VACATED`;
   - pilot readback `TaxiRouteReadback`: destination plus optional `VIA` route
     points and callsign, as a generic route readback template.
2. Token contract:
   - `TaxiToStandInstruction`: `AircraftCallsign`, `Taxi`, `To`,
     `PointName(destination)`, optional `Via`, then zero or more
     `PointName(routePoint)` tokens.
   - `TaxiRouteReadback`: `PointName(destination)`, optional `Via`, route
     points, and final `AircraftCallsign`.
   - `RunwayVacatedReport`: `Runway`, `Vacated`.
   Aircraft identity for report facts is bound by transmission metadata
   (`SpeakerRef.Pilot(aircraft)` / fact payload aircraft id), not by the report
   wording tokens. Tokens prove the phraseology words; selectors bind the
   aircraft.
3. Readback correlation rule: the generic `TaxiRouteReadback` fact is not
   enough to satisfy `e30350...` alone. The source evidence must require
   matching rendered facts for `RunwayVacatedReport(aircraft)`,
   `TaxiToStandInstruction(aircraft, destination, via)`, and
   `TaxiRouteReadback(aircraft, destination, via)` with the same destination and
   the same ordered `via` route point list. If the actual trace cannot provide
   that shape and a synthetic record is used, the synthetic fixture must contain
   both the `TaxiToStand` instruction and corresponding readback, and the test
   name/sample metadata must state which branch is synthetic, why the LOWG trace
   cannot supply it, and which production renderer path the synthetic record
   still exercises. The task done summary may repeat this.
4. Keep renderer totality honest: unsupported instructions/readbacks/reports
   continue returning unsupported payloads rather than fake strings.
5. Add DSL selectors that match tokens, not plain text.
6. Add tests in the existing phraseology evidence fixture:
   - `e30350...` cites the source row and requires runway-vacated report,
     taxi-to-stand instruction, and taxi route readback phraseology.
   - `df251...` cites the source row and requires the contact-ground branch
     only, with samples naming the first-right residual.
7. Update source catalog and chunk 06 docs/manifests to move only the covered
   branches out of `phraseology-later`.
8. Validate with focused tests, detekt, broad test suite, flow validation, and
   implementation/completion review.

Documentation convention for mixed rows: a source unit with only a covered
branch must use a `split: ...` final state in chunk docs rather than
`covered-green`. For fn-84 that means `df251...` should become
`split: CONTACT GROUND wording covered; TAKE FIRST RIGHT WHEN VACATED remains
blocked`, and summary counts must move one row from `phraseology-later` to a
split/residual bucket. `e30350...` can become covered phraseology if all listed
example utterances are represented by supported rendered facts. Expected final
chunk 06 summary, assuming `e30350...` is fully covered: rendered
phraseology/covered-green increases by 1, split/residual increases by 1,
`phraseology-later` decreases from 4 to 2, and the model/policy counts remain
unchanged.

Artifact representation:

- `EvidenceSourceCatalog.kt`: no lifecycle state is stored there; add typed
  source refs only. Titles must name whether the ref is full rendered wording or
  split branch wording.
- `coverage_report.md`: use the display string `covered-green` for
  `e30350...`; use the display string
  `split: CONTACT GROUND wording covered; TAKE FIRST RIGHT WHEN VACATED remains blocked`
  for `df251...`; add a summary row for split residuals.
- `expected_gaps.md`: remove fully covered `e30350...` from
  `Phraseology-Later`; move `df251...` into a `Split Residuals` section with
  the blocked first-right/vacating wording called out; leave helicopter
  air-taxi and essential-aerodrome-information phraseology under
  `Phraseology-Later`.
- There is no separate machine-readable chunk 06 manifest in this programme
  directory at planning time; if implementation finds one, update it consistently
  with the same display-state convention.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432PhraseologyEvidenceTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceFactsTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `scripts/ralph/flowctl validate --epic fn-84-icao-9432-phrase-1-after-landing --json` (canonical local command; `.flow/bin/flowctl` may appear in older chunk docs)

## Acceptance
- [ ] The production phraseology renderer emits typed rendered facts for
  `TaxiToStand`, `ReportEvent.RunwayVacated`, and stand-route readbacks.
- [ ] The evidence DSL can assert those phraseology facts by template and typed
  tokens.
- [ ] `after_landing_4_9_en::e30350fdecad45a1` has source-mapped rendered
  phraseology evidence for runway-vacated / taxi-to-stand wording.
- [ ] `after_landing_4_9_en::df25159c1e7b94a3` has source-mapped rendered
  phraseology evidence only for the contact-ground branch, with first-right /
  vacating wording still visible as a residual.
- [ ] Chunk 06 programme docs no longer list covered branches as generic
  `phraseology-later`, represent `df251...` as an explicit `split:` state, and
  still keep helicopter air-taxi and essential aerodrome information phraseology
  blocked.
- [ ] Focused tests, detekt, broad tests, flow validation, implementation
  review, and completion review pass.

## Review considerations

- FP / type safety: Renderer `when` expressions must stay exhaustive over the
  explicitly handled leaves and return unsupported payloads for type-valid
  states outside this proof surface. No `else` branch may swallow future
  instruction/readback/report cases.
- Test architecture: This remains high-level source-mapped evidence. Tests
  should exercise production phraseology projection from transmission records
  and assert typed tokens. Unit-style selector tests are only for the DSL
  matching surface. If synthetic records are used, they must still go through
  the production phraseology renderers and document why a real LOWG trace could
  not supply that branch.
- Impact: This extends the PHRASE-1 rendering proof set only. It should not
  change controller, pilot, movement, clearance, or readback behaviour. The main
  failure mode is overclaiming full ICAO example dialogue; docs and source titles
  must make residuals explicit. In particular, adding `AirTaxiTo` rendering or
  treating a generic route readback as stand-bound without the matching
  `TaxiToStand` source assertion would undermine the intended residual state and
  is out of scope.
- Operational correctness: Claims are limited to ICAO Doc 9432 Fourth Edition
  2007 §4.9 examples in `research/txt/icao9432-extracted.txt` lines 5868-5932.
  Timing/policy rows for after landing remain blocked.

## References
- `research/txt/icao9432-extracted.txt` §4.9, lines 5868-5932.
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/after_landing_4_9_en/icao9432-extracted::after_landing_4_9_en::df25159c1e7b94a3.json`
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/after_landing_4_9_en/icao9432-extracted::after_landing_4_9_en::e30350fdecad45a1.json`
