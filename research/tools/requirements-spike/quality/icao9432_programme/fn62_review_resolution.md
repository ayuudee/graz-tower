# fn-62 review resolution

Reviewer: Goodall (`01a0b443-60d8-7a42-9ac3-5dcfa5eef6cc`)

## Findings addressed

1. Structural vocabulary was mislabeled as sim-observed behaviour.

Resolution:

- Added `EvidenceClaimKind.StructuralEvidenceVocabulary`.
- Added `SimEvidenceBuilder.sourceVocabulary(...)`.
- Added `EvidenceSourceClaimScope.StructuralEvidenceVocabulary`.
- Marked only the eight §4.10 definition/category vocabulary rows with the
  structural scope.
- `Icao9432EssentialAerodromeInformationEvidenceTest` now uses
  `sourceVocabulary(...)`, synthetic-projection evidence origin, and asserts
  that every result has `StructuralEvidenceVocabulary` claim kind.
- Programme manifests now use `covered-structural`, not `covered-green`, for
  those rows.

2. Category checks were too coarse.

Resolution:

- `EvidenceFactPayload.EssentialAerodromeInformation` now carries typed source
  facets and explicit safety relevance.
- The selector requires category, requested facets, and
  `NecessaryForSafeOperation`.
- The source-mapped test requires the source-specific facets:
  runway/taxiway/apron; on/adjacent movement area; parked aircraft; birds on
  ground or in air; and lighting system.

3. Definition row did not represent safety relevance.

Resolution:

- Essential-information payloads now carry
  `EssentialAerodromeInformationSafetyRelevance.NecessaryForSafeOperation`.
- The domain selector only considers safety-relevant facts.

4. Programme/task status ambiguity.

Resolution:

- Programme CSV, JSON, blocker manifest, coverage report, source plan,
  expected gaps, manifest, and self-assessment now distinguish
  `covered-structural` from live scenario coverage.
- Flow task JSON files are static task definitions; runtime state lives under
  `.git/flow-state`. `flowctl show fn-62-icao-9432-fn43-gap-1-essential`
  reports tasks 1 and 2 done and task 3 in progress before closeout.

## Residual risk

fn-62 still does not prove live sim projection of essential aerodrome
information, transmission/receipt timing, aircraft-known-information omission,
open pertinence policy, or rendered phraseology. Those are deliberately left as
visible gaps.
