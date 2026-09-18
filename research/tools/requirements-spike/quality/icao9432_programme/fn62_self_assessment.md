# fn-62 self-assessment

Epic: `fn-62-icao-9432-fn43-gap-1-essential`

Scope: close the ICAO 9432 §4.10 essential-aerodrome-information
definition/category rows as structural evidence vocabulary. Live sim
projection, timing, receipt, omission, open pertinence, and phraseology remain
explicit gaps.

## Principal-agent checks

Totality:

- No production sealed transition was changed.
- The new evidence payload and kind are closed enum/data-class additions on
  the JVM test evidence surface.
- The new DSL selector fails on empty evidence and on missing categories or
  domains; it does not return a vacuous pass.

Reversal completeness:

- No reversible sim/controller/pilot state transition was introduced.
- No runtime state field was added.

Interaction coverage:

- The new source-mapped test exercises `simEvidence -> sourceVocabulary ->
  EvidenceFactSet -> AuditEssentialAerodromeInformationSubject ->
  EvidenceAuditReport`.
- `EvidenceDslTest` covers both the pass path and the missing-category failure
  path, including activation of observed category facts.
- `Icao9432ModelGapSourceUnitSpecTest` keeps policy-sensitive §4.10 rows red as
  model gaps.

Test coverage for known features:

- `Icao9432EssentialAerodromeInformationEvidenceTest` covers the one domain
  definition row and seven category rows targeted by fn-62 as structural
  vocabulary. It asserts category/domain/facet/safety-relevance shape and
  `StructuralEvidenceVocabulary` claim kind, not live scenario observation.
- `EvidenceSourceCatalogTest` was included in validation to check typed source
  catalog consistency.

New-field audit:

- No production state data class fields were added.
- Added evidence payload type: no `.copy(` migration is required because
  `EvidenceFactPayload` leaves are immutable values and existing adapters only
  consume explicit payload instances.

Operational correctness:

- The fn-62 claims are deliberately narrow: ICAO 9432 §4.10 structural
  vocabulary for category, domain, source facets, and safety relevance only.
- The tests do not assert that information was passed to a pilot, received by a
  pilot, rendered with correct phraseology, omitted because known, or judged
  pertinent under local policy.
- Those policy/receipt/phraseology claims remain blocked in the manifests and
  source-unit specs.

Error handling honesty:

- No `error()` paths were added.
- Missing evidence is represented as `EvidenceAuditOutcome.Fail` with the
  observed evidence listed for diagnostics.

Deferment honesty:

- No new unrelated deferment was discovered.
- The existing fn-62 blocked rows remain visible in programme manifests:
  omission when known from other sources, timing "whenever possible", open
  pertinence, and phraseology.
