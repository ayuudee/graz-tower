# 2026-04-29 Curator Ledger And Pending Closure

## Decision

The Ollama-first registry now treats curation as a first-class
`quality/judgements.csv` event. `curate_pending_with_gpt.py` appends
post-curation rows with `eventSource=curator`, and the historical
2026-04-29 curation audit directories were backfilled into the CSV so
the regression detector's latest-row view matches registry lifecycle
state.

The three residual RR-11 pending records were resolved manually:

- H01 AIC A 21/23 section 3.8.1(c): rejected the broad parent readback
  claim as a redundant umbrella over accepted item-level obligations.
- ICAO Doc 4444 section 4.3.2.1: rejected the non-standalone parent
  clause and split the operative arriving/departing branches into
  accepted records for sections 4.3.2.1.1 and 4.3.2.1.3.
- ICAO Doc 4444 section 4.5.7.5.2.1 Note: accepted as
  `background_support` / `review_only`, not as an executable rule.

Live registry after closure: 212 accepted candidates, 0 pending, 31
rejected, 243 total records. Reproducibility audit passes across all
243 records.

## Why

RR-12 meant the registry files and per-record curation audit trail were
truthful, but the longitudinal CSV used by regression checks still saw
pre-curation states. That split made the detector unable to reason about
curator-driven lifecycle changes.

Appending curator rows keeps one ledger: promotion rows record what the
local pipeline produced, curator rows record the final adjudication, and
the detector continues to select the latest row by
`(documentId, sectionId, claimSha256)`.

The RR-11 manual decisions are intentionally conservative. Parent
clauses that depend on subordinate operative lists are not accepted as
standalone executable rules. Where the missing subordinate content was
clear and source-supported, it was split into explicit records; where
accepted siblings already covered the content, the umbrella was rejected.

## Consequence

- `registry/ollama_first/pending/` is empty.
- Historical curation decisions are visible in `quality/judgements.csv`.
- Quote-audit failures on rejected rows no longer fail the regression
  detector; they are preserved as evidence for rejection. Quote-audit
  failures on accepted rows still hard-fail.
- The registry is internally consistent and auditable, but this does not
  prove global fitness for purpose. A separate independent statistical
  adequacy assessment is now tracked in `.plan` as RR-13.

## Review considerations

- **FP / type safety**: CSV rows remain total over the declared header.
  Old headers are migrated under the existing file lock and unknown
  columns fail loudly. No identity field (`canonicalId`, `claimText`,
  `exactSourceQuotes`) is mutated in place.
- **Test architecture**: Added focused tests for legacy header migration,
  final-record curator row emission, latest curator-row regression
  behavior, and rejected-row quote-audit semantics. Offline verification:
  87 quality-gate tests, 13 override-contract tests, reproducibility
  audit, and baseline-vs-current regression check.
- **Impact**: Downstream consumers can now read the CSV as the lifecycle
  ledger. The tradeoff is that curator rows often lack per-stage prompt
  SHAs because registry records did not previously persist them; those
  rows are human/frontier-curator events, not stochastic local-judge
  events.
- **Operational correctness**: The manual source decisions are anchored
  to AIC A 21/23 H01 section 3.8.1(c), ICAO Doc 4444 sections 4.3.2.1,
  4.3.2.1.1, 4.3.2.1.3, and ICAO Doc 4444 section 4.5.7.5.2.1. The
  split records preserve condition sets as bundled family-test candidates
  rather than flattening them into false single-condition atoms.
