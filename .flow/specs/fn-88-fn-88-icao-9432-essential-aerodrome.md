# fn-88-fn-88-icao-9432-essential-aerodrome fn-88 ICAO 9432 essential aerodrome information example phraseology

## Overview
Close the remaining chunk-06 phraseology-later source unit
`icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75`.

This source unit is an ICAO Doc 9432 §4.10 example block, not a live sim
behavior requirement. The accepted source quotes are:

- `FASTAIR 345 CAUTION CONSTRUCTION WORK ADJACENT TO GATE 37`
- `CENTRE LINE TAXIWAY LIGHTING UNSERVICEABLE`
- `RUNWAY CONDITIONS 09: AVAILABLE WIDTH 32 METRES, COVERED WITH THIN PATCHES OF ICE, BRAKING ACTION POOR`

The goal is to make the evidence surface able to represent and source-map
these rendered examples honestly. It must not claim timing, omission policy,
open pertinence, live controller projection, or general essential-aerodrome-
information generation.

## Scope
- In scope:
  - Add a typed evidence payload for rendered essential-aerodrome-information
    example phraseology.
  - Add narrow templates/tokens for the three source examples:
    construction caution, centre-line taxiway-lighting unserviceability, and
    runway-condition report.
  - Add an evidence selector that validates the source example block by exact
    template, exact rendered text, exact tokens/slots, and source-example
    ordering.
  - Add source-mapped synthetic evidence using `EvidenceFactAdapters.fromProjectedPayloads`
    with explicit payloads. This is appropriate because no production
    controller transmission type currently represents these information-only
    examples.
  - Update `EvidenceSourceCatalog`, catalog tests, chunk 06 docs, and
    `implementation_blocker_manifest.csv`.
- Out of scope:
  - Controller behavior or phrase generation for operational aerodrome
    information.
  - Aircraft receipt/timing evidence, known-from-other-sources policy,
    pertinence policy, runway-condition modelling, braking-action modelling,
    lighting-serviceability state, construction geometry, or live sim
    projection.
  - General arbitrary free-text ATIS/aerodrome-information rendering.

## Approach
1. Re-read the existing structural essential-aerodrome-information evidence and
   the §4.10 source text/candidate.
2. Add a small jvmTest evidence vocabulary:
   - `EvidenceClaimKind.SyntheticRenderedPhraseologyExample` and a
     `SimEvidenceBuilder` method such as `sourceRenderedExample(...)` so audit
     reports do not mislabel this as live sim behavior or structural vocabulary.
   - `RenderedEssentialAerodromeInformationPhraseologyTemplate` with exactly
     the three source-example leaves.
   - A distinct `EssentialAerodromeInformationPhraseologyToken` type. Do not
     reuse `PhraseologyToken`; these examples are not controller/pilot/vehicle
     rendered phraseology and must not widen those invariant surfaces.
   - `EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology`
     carrying template, tokens, and `RenderedPhraseText`.
   - Minimal token leaves or structured token data needed to prove the three
     examples without using opaque strings only.
3. Add an evidence selector, likely `essentialAerodromeInformationPhraseology()`,
   that requires all three examples in source order and validates:
   - exact template,
   - exact source-equivalent text,
   - exact token list,
   - no extra or missing same-kind rendered essential-aerodrome-information
     phraseology payloads for the source case.
   The selector must ignore unrelated evidence payload kinds, including the
   existing structural `EssentialAerodromeInformation` facts, but fail if the
   same fact set contains an extra rendered essential-aerodrome-information
   phraseology payload.
4. Add selector tests for:
   - positive three-example block,
   - absent evidence,
   - missing one example,
   - wrong order,
   - wrong template,
   - wrong text,
   - malformed tokens,
   - extra duplicate same-kind payload.
5. Add source coverage in `Icao9432EssentialAerodromeInformationEvidenceTest`
   using explicit `fromProjectedPayloads` payloads and cite
   `ICAO9432.AerodromeInformation.ExamplePhraseology`.
6. Add catalog/docs/manifest updates:
   - add an explicit catalog claim scope such as
     `SyntheticRenderedPhraseologyExample`,
   - add `ExamplePhraseology` source ref with that synthetic rendered-example
     claim scope, not `RenderedPhraseologyTrace`,
   - update catalog expected IDs/rendered-phraseology scope test,
   - move `7b81f87f5c2b4d75` from `phraseology-later` to covered,
   - update chunk summary counts and expected-gaps phraseology-later table.
7. Do not add this payload to controller rendered phraseology template
   invariants such as TAKE OFF word-use; this is a separate evidence payload,
   not `RenderedPhraseologyTemplate`.
8. Record review receipts using the standard flowctl review process:
   - `scripts/ralph/flowctl codex impl-review ... --base origin/main`
   - `scripts/ralph/flowctl codex completion-review ... --base origin/main`

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432EssentialAerodromeInformationEvidenceTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `scripts/ralph/flowctl validate --epic fn-88-fn-88-icao-9432-essential-aerodrome --json`

## Acceptance
- [ ] The source unit `icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75`
  has a typed source ref in `EvidenceSourceCatalog`.
- [ ] The source ref uses a synthetic rendered-example claim scope, not
  `RenderedPhraseologyTrace`.
- [ ] The source case uses a dedicated synthetic rendered-phraseology example
  `EvidenceClaimKind` / builder method, not `source` or `sourceVocabulary`.
- [ ] The evidence surface can represent exactly the three accepted §4.10
  example phraseology strings.
- [ ] The selector validates exact text, template, tokens, and source order for
  all three examples.
- [ ] Negative selector tests cover absent, missing example, wrong order, wrong
  template, wrong text, malformed tokens, and extra duplicate same-kind payload
  behavior.
- [ ] Coverage is explicitly synthetic rendered-example evidence and does not
  claim live controller projection, timing, omission, pertinence, or policy.
- [ ] The selector ignores unrelated evidence payload kinds but fails on extra
  same-kind rendered essential-aerodrome-information phraseology payloads.
- [ ] The new payload/templates remain separate from `RenderedPhraseologyTemplate`
  and are excluded from controller/pilot/vehicle rendered phraseology invariant
  sets.
- [ ] Existing structural essential-aerodrome-information tests remain
  structural evidence vocabulary only.
- [ ] `EvidenceSourceCatalog`, catalog tests, chunk 06 docs, and
  `implementation_blocker_manifest.csv` are updated consistently.
- [ ] Focused tests, broad tests, detekt, flow validation, implementation
  review, completion review, and `git diff --check` pass.

## Review considerations
- FP / type safety: Use a closed template enum and a distinct essential-
  aerodrome-information phraseology token type over arbitrary strings. Extra
  same-kind payloads are type-valid but must fail this exact source block.
- Test architecture: Use projected synthetic payloads, not live sim traces,
  because the current model has no controller output for these information-only
  examples. Tests should prove selector behavior, not merely source catalog
  membership.
- Impact: Keep this separate from `RenderedPhraseologyTemplate` so controller
  instruction invariants and TAKE OFF word-use coverage are not widened by an
  information-example evidence payload.
- Operational correctness: Cite ICAO Doc 9432, Manual of Radiotelephony,
  Fourth Edition, 2007, §4.10. Treat the phrases as examples of essential
  aerodrome information wording only.

## References
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/essential_aerodrome_information_4_10_en/icao9432-extracted::essential_aerodrome_information_4_10_en::7b81f87f5c2b4d75.json`
- `research/txt/icao9432-extracted.txt` lines 5933-5965.
