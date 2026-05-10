# Source Section Ledger - 2026-04-30

## Scope

This ledger is the control surface for future source widening. It covers the local `research/txt/` corpus at table-of-contents / major-section granularity and includes exact manifest-window rows. As of this ledger, `documents/*.json` contains 232 exact windows: 47 have accepted registry records and 185 are manifest-only windows awaiting clean ingestion/promotion.

It is not paragraph-level atomization. A row with `extract` means the section should be considered for a future topic batch; it does not mean the section has already been translated.

## Summary

- Ledger rows: 667
- Exact manifest-window rows: 232
- Landed manifest-window rows: 47
- Manifest-only rows ready for Ollama ingestion: 185
- High-priority `extract` or `partially_extracted` rows: 0
- High-priority non-manifest rows needing exact source windows: 0

The disposition table counts all rows. The Ollama-ready count above is restricted to exact `manifest_window` rows, so it excludes high-level TOC rollups that also carry `manifest_only` disposition.

### By Disposition

| Disposition | Rows |
| --- | ---: |
| background_only | 13 |
| defer_with_reason | 100 |
| duplicate_subset | 3 |
| extract | 20 |
| extracted_current | 62 |
| manifest_only | 188 |
| out_of_scope | 13 |
| partially_extracted | 2 |
| support_only | 182 |
| window_hardened | 84 |

### By Document

| Document | Rows |
| --- | ---: |
| cap413-aerodrome-chapter | 1 |
| cap413-extracted | 88 |
| egast-vfr-extracted | 9 |
| h01-aerodrome-chapter | 1 |
| h01-extracted | 96 |
| icao4444-extracted | 223 |
| icao9432-aerodrome-chapter | 1 |
| icao9432-extracted | 113 |
| nolan-fundamentals-extracted | 13 |
| safetysense22-extracted | 30 |
| sera-923-2012-extracted | 79 |
| slovenia-vfr-extracted | 13 |

## High-Priority Future Extraction Rows

| Document | Section | Disposition | Rationale |
| --- | --- | --- | --- |

## Checks

1. Exact manifest-window rows are read from `documents/*.json`; their disposition is computed from whether accepted registry records exist for the same document/section.
2. SERA section rows are generated from the source text's `SERA.xxxx` headings before the appendices/differences material.
3. CAP 413, EGAST, H01, ICAO 4444, ICAO 9432, SafetySense, Slovenia, and Nolan rows are keyed from their table-of-contents / major-section structure.
4. The three aerodrome excerpt files are marked `duplicate_subset` rather than queued for separate extraction.

## Review Considerations

FP / type safety: no Kotlin/domain code changed.

Test architecture: this is an audit ledger. The check is deterministic regeneration plus row-id uniqueness and manifest cross-checks.

Impact: future widening can now be tracked by section disposition instead of vague document-level intent.

Operational correctness: no new ATC rule claim is made. Dispositions only rank source sections for future extraction/support/defer decisions.
