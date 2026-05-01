# Declared Slice And Source Section Ledger

Date: 2026-04-30

## Decision

Freeze the current Ollama-first registry as the declared 22-window extraction
slice, and make all future widening pass through a source-section disposition
ledger.

The scope contract is:

- `research/tools/requirements-spike/registry/ollama_first/DECLARED_SLICE.md`

The source-section ledger is:

- `research/tools/requirements-spike/quality/source_section_ledger/source_section_ledger_2026-04-30/source_section_ledger.md`
- `research/tools/requirements-spike/quality/source_section_ledger/source_section_ledger_2026-04-30/source_section_ledger.csv`

## Result

The registry documentation now says explicitly that the current 243 accepted
records are a structured extraction of the 22 declared manifest windows only.
They are not evidence of full-document or full-corpus coverage.

The new ledger has 457 rows:

- 22 exact manifest-window rows;
- table-of-contents / major-section rows for the local `research/txt/`
  corpus;
- duplicate/subset rows for the three aerodrome excerpt files.

Disposition counts:

- `background_only`: 13
- `defer_with_reason`: 100
- `duplicate_subset`: 3
- `extract`: 102
- `extracted_current`: 31
- `out_of_scope`: 13
- `partially_extracted`: 12
- `support_only`: 183

The ledger identifies 92 high-priority `extract` or `partially_extracted`
rows for future topic batches.

## Implication

The next extraction step should choose explicit ledger rows and add explicit
manifest line ranges. The project should not use "ingest the document" as the
planning unit.

## Review Considerations

FP / type safety: no Kotlin/domain code changed.

Test architecture: this is an audit/documentation change. Verification is
deterministic regeneration of the ledger plus row-id uniqueness and manifest
window count checks.

Impact: downstream consumers have a stable scope boundary; future work has a
section-level control surface for omissions and deferrals.

Operational correctness: no new ATC rule or phraseology claim is made. The
ledger only classifies source sections for extraction/support/defer decisions.
