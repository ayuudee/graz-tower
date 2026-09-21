# fn-89-fn-89-icao-9432-chunk-01-communications fn-89 ICAO 9432 chunk 01 communications phraseology examples

## Overview
Close three remaining chunk-01 `phraseology-later` source units from ICAO
Doc 9432 §2.8.1 as honest synthetic rendered-example evidence:

- `icao9432-extracted::communications_2_8_1_en::a685cef087951878`:
  when establishing communications, an aircraft should use the full call sign
  of both the aircraft and the aeronautical station.
- `icao9432-extracted::communications_2_8_1_en::b7acdc88125f1510`:
  a ground-station broadcast should be prefaced by `ALL STATIONS`.
- `icao9432-extracted::communications_2_8_1_en::5efac97fddfd54ca`:
  an aircraft vicinity broadcast should be prefaced by `ALL STATIONS`.

The source examples in `research/txt/icao9432-extracted.txt` lines 3644-3663
are:

- `STEPHENVILLE TOWER G-ABCD`
- `G-ABCD STEPHENVILLE TOWER`
- `ALL STATIONS ALEXANDER CONTROL, FUEL DUMPING COMPLETED`
- `ALL STATIONS G-CDAB WESTBOUND MARLO VOR TO STEPHENVILLE LEAVING FL 260 DESCENDING FL 150`

This epic does not implement live broadcast/controller/pilot behaviour. It
adds source-mapped phraseology evidence vocabulary for accepted examples only,
similar in honesty to fn-88's essential-aerodrome-information synthetic
rendered examples.

## Scope
- In scope:
  - Add typed `EvidenceSourceCatalog` refs for the three source units.
  - Use a claim scope that is honest for synthetic rendered examples, not
    trace-backed `RenderedPhraseologyTrace`.
  - Add a closed communication-phraseology example payload/template/token
    surface for:
    - station-called/aircraft-callsign initial-contact ordering,
    - aircraft-callsign/station-called reverse example,
    - ground-station `ALL STATIONS` broadcast prefix,
    - aircraft `ALL STATIONS` broadcast prefix.
  - Add an evidence selector that validates the complete example block for
    exact templates, exact tokens, exact text, exact source order, and no extra
    same-kind payloads.
  - Add source-mapped synthetic tests using explicit payload literals via
    `EvidenceFactAdapters.fromProjectedPayloads`.
  - Add selector tests covering positive and negative behavior.
  - Update `EvidenceSourceCatalogTest`, chunk-01 docs, and
    `implementation_blocker_manifest.csv`.
- Out of scope:
  - No-reply behavior for general calls (`8b0487b183cd02cf`) because that is
    policy-blocked.
  - ATC route-clearance-is-not-runway-entry semantics
    (`fe3b04ca9c3384d9`) because it is a separate row and likely not the same
    evidence surface.
  - Live controller/pilot generation, radio broadcast routing, acknowledgement
    policy, receiver sets, or station topology.
  - General free-text phraseology rendering beyond these source examples.

## Approach
1. Re-read the three candidate JSON files and §2.8.1 source excerpt before
   implementation.
2. Reuse `EvidenceClaimKind.SyntheticRenderedPhraseologyExample` and
   `SimEvidenceBuilder.sourceRenderedExample(...)`; do not add a second claim
   kind unless implementation pressure proves the existing one ambiguous.
3. Add a distinct payload surface, not `RenderedPhraseologyTemplate`:
   - `EvidenceFactPayload.RenderedCommunicationPhraseologyExample`
   - `RenderedCommunicationPhraseologyTemplate`
   - `CommunicationPhraseologyToken`
   The token type should be separate from both controller `PhraseologyToken`
   and fn-88's essential-aerodrome-information token type.
4. Add helper constructors for the selector's canonical expected block. The
   source-coverage test must not use those helpers for its inputs; it should
   construct explicit literal payloads so source fidelity is pinned
   independently.
5. Add selector tests:
   - positive block with unrelated payload ignored,
   - absent evidence,
   - missing example,
   - wrong order,
   - wrong template,
   - wrong text,
   - malformed tokens,
   - extra duplicate same-kind payload.
6. Add source coverage in a new or existing chunk-01 communications evidence
   test using explicit synthetic payloads, citing the three typed refs and
   sampling ICAO Doc 9432, Fourth Edition, 2007, §2.8.1.1-§2.8.1.3.
7. Update catalog/docs/manifest:
   - add the three source refs with synthetic rendered-example scope,
   - update catalog expected IDs / scope separation tests,
   - move the three source units out of `phraseology-later` into a synthetic
     rendered-example state,
   - update chunk-01 summary counts and expected-gaps records.
8. Validate with focused tests, detekt, broad tests, flow validation,
   implementation review, completion review, and `git diff --check`.

## Quick Commands
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk01CommunicationsPhraseologyEvidenceTest' --tests '*.EvidenceDslTest' --tests '*.EvidenceSourceCatalogTest' --tests '*.EvidenceReportWriterTest'`
- `./gradlew-nix detekt`
- `./gradlew-nix :protocol:allTests :core:allTests :sim:jvmTest`
- `scripts/ralph/flowctl validate --epic fn-89-fn-89-icao-9432-chunk-01-communications --json`

## Acceptance
- [ ] The three chunk-01 communication phraseology source units have typed
  source refs.
- [ ] The source refs use synthetic rendered-example claim scope, not
  `RenderedPhraseologyTrace`.
- [ ] The source cases use `SyntheticRenderedPhraseologyExample` via
  `sourceRenderedExample(...)`.
- [ ] A closed rendered communication phraseology example payload/template/token
  surface represents the four §2.8.1 examples exactly.
- [ ] The token type is distinct from existing controller/pilot phraseology
  tokens and from essential-aerodrome-information tokens.
- [ ] The selector checks exact template, text, token list, source order, and
  same-kind count.
- [ ] The selector ignores unrelated payload kinds and fails on absent, missing,
  wrong-order, wrong-template, wrong-text, malformed-token, and extra duplicate
  same-kind cases.
- [ ] Source coverage uses explicit literal `fromProjectedPayloads` payloads,
  not the selector helper constructors.
- [ ] Existing trace-backed phraseology invariant surfaces are not widened.
- [ ] Chunk-01 docs and the implementation blocker manifest are updated
  consistently.
- [ ] Focused tests, broad tests, detekt, flow validation, implementation
  review, completion review, and `git diff --check` pass.

## Review Considerations
- FP / type safety: Use closed template and token types. Any new `when` over
  `EvidenceFactKind`, `EvidenceClaimKind`, or report classification must stay
  exhaustive; no catch-all `else`.
- Test architecture: Tests must prove source fidelity independently. Selector
  expected-helper constructors may exist, but the source test input must use
  explicit literals copied from the source examples.
- Impact: Keep this as synthetic example evidence. Do not route it through live
  controller/pilot `RenderedPhraseologyTemplate`, because these broadcasts and
  initial-contact examples are not currently generated by the sim.
- Operational correctness: Source claims are ICAO Doc 9432, Manual of
  Radiotelephony, Fourth Edition, 2007, §2.8.1.1-§2.8.1.3. The claims are
  advisory phraseology examples (`should`), not universal runtime policy.

## References
- `research/txt/icao9432-extracted.txt` lines 3644-3663.
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/communications_2_8_1_en/icao9432-extracted::communications_2_8_1_en::a685cef087951878.json`
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/communications_2_8_1_en/icao9432-extracted::communications_2_8_1_en::b7acdc88125f1510.json`
- `research/tools/requirements-spike/registry/ollama_first/candidates/icao9432-extracted/communications_2_8_1_en/icao9432-extracted::communications_2_8_1_en::5efac97fddfd54ca.json`
