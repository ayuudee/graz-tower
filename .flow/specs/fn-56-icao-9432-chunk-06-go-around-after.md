# fn-56-icao-9432-chunk-06-go-around-after ICAO 9432 chunk 06 go-around after-landing aerodrome-info source-mapped tests

## Overview
Author source-mapped coverage for ICAO 9432 chunk 06:
`chunk-06-go-around-after-landing-aerodrome-info`.

Scope sections:

- `go_around_4_8_en`
- `after_landing_4_9_en`
- `essential_aerodrome_information_4_10_en`

This chunk contains 20 accepted source units covering VFR and IFR go-around
defaults, radio discipline during go-around, post-landing tower-frequency and
taxi timing guidance, and essential aerodrome information categories and
timing. The epic is an audit and source-mapped test-authoring pass, not a
mandate to implement every ICAO 9432 sentence as simulator law.

## Scope
In scope:

- Verify all 20 accepted source-unit quote excerpts against
  `research/txt/icao9432-extracted.txt`.
- Produce `source_plan.md`, `expected_gaps.md`, and `coverage_report.md` under
  `research/tools/requirements-spike/quality/icao9432_programme/chunk_06_go_around_after_landing_aerodrome_info/`.
- Reuse existing go-around golden traces where they honestly prove VFR traffic
  circuit continuation after go-around.
- Add narrow source-mapped evidence tests only where current observations
  prove the source claim or expose a real source-backed failure.
- Record phraseology, policy, and missing-world-model blockers loudly.

Out of scope:

- Implementing generic essential-aerodrome-information modelling.
- Implementing IFR missed-approach procedures unless a current scenario and
  evidence surface already exists.
- Treating typed instructions or reports as rendered phraseology compliance.
- Promoting `should`, `may`, `whenever possible`, or operational-guidance rows
  into universal simulator law.
- Repairing controller/pilot/sim behaviour beyond the smallest evidence
  projection needed to make an existing current behaviour honestly auditable.

## Approach
1. Build a chunk-local source plan from the accepted registry records and
   mechanically check normalized quotes against `research/txt/icao9432-extracted.txt`.
2. Classify every row into one of: covered-green candidate, covered-red
   candidate, model-gap, policy-blocked, phraseology-later, or not-applicable.
3. Give special scrutiny to the two go-around default rows:
   - VFR go-around should be covered only if the trace proves re-entry into the
     normal traffic circuit after go-around.
   - IFR missed approach should remain a model gap unless the sim has an
     instrument-approach/missed-approach procedure surface.
4. Treat after-landing rows as mostly policy/phraseology unless the trace can
   prove runway-vacated tower-frequency retention or taxi-instruction timing
   without relying on rendered wording.
5. Treat essential-aerodrome-information category rows as model gaps unless
   there is a typed movement-area hazard/facility information model.
6. Run focused chunk tests, broad `:sim:jvmTest`, detekt, Flow validation, and
   `git diff --check`.
7. Run independent implementation review before closing and committing.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :sim:jvmTest`
- `./gradlew-nix detekt`
- `.flow/bin/flowctl validate --epic fn-56-icao-9432-chunk-06-go-around-after`

## Acceptance
- [ ] All 20 chunk 06 accepted source units have a reviewed final planned state.
- [ ] Every coverage claim cites the accepted source-unit id.
- [ ] VFR go-around coverage, if claimed, proves circuit continuation after
      go-around, not merely a `GoAround` transmission.
- [ ] IFR missed-approach and essential-aerodrome-information claims remain
      gaps unless a current typed model can prove them.
- [ ] Phraseology rows are not marked covered-green from typed transmission
      existence alone.
- [ ] Policy-sensitive rows are marked with the missing policy concept.
- [ ] Flow tasks and checked-in sidecars match runtime state before commit.

## Review Considerations

### FP / Type Safety

No production ADT or state field is planned. If the pass discovers a missing
evidence projection, it must be typed and closed. No new `else` branch,
stringly source matching, or `error()` for type-valid states belongs in this
epic.

### Test Architecture

Prefer high-level source-mapped scenario tests that prove behaviour through a
real trace. Do not add structural tests for compiler-guaranteed properties.
Expected gaps must be source-specific and visible in `expected_gaps.md`.

### Impact

The main risk is false-green coverage: go-around goldens are already rich, but
not every go-around source unit is proven by seeing a go-around transmission.
Essential aerodrome information could become a large world-modelling project;
this epic must not smuggle that into a coverage pass.

### Operational Correctness

All claims are grounded in ICAO Doc 9432 §4.8, §4.9, and §4.10 source-unit ids.
The source contains default procedures, phraseology examples, and
`should`/`whenever possible` guidance; final dispositions must preserve those
modalities.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/README.md`
- `research/tools/requirements-spike/quality/icao9432_programme/classification.json`
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/`
- `research/txt/icao9432-extracted.txt`
