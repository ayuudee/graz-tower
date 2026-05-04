# Declared Registry Slice

Status date: 2026-05-04

## Scope Contract

This registry is a structured extraction of the 46 landed line-range windows
currently promoted under `candidates/`.

The live ingestion manifests in
`research/tools/requirements-spike/documents/*.json` contain those same 46
sections. The 16 clearance/communications windows that were manifest-only on
2026-05-01 have now passed ingest, promote, curation, reproducibility audit,
quote audit, regression check, and snapshotting.

It is not a full-document extraction and not a full-corpus extraction.
Downstream consumers may rely on records in this registry only as claims
about those declared source windows.

## Declared Inputs

- Document manifests: 8
- Landed promoted windows: 46
- Live manifest sections: 46
- Manifest-only additions awaiting ingest/audit: 0
- Manifested text extracts: 8
- Landed source-line records: 2,431
- Live manifest source-line records: 2,431
- Current registry records: 423 accepted candidates, 0 pending, 34 rejected
- Current regression snapshot:
  `research/tools/requirements-spike/quality/snapshots/judgements-2026-05-04-post-clearance-comms.csv`

The source inventory that establishes this boundary is:

- `research/tools/requirements-spike/quality/source_inventory/source_inventory_2026-04-29/source_document_inventory.md`

The section-level disposition ledger for future widening is:

- `research/tools/requirements-spike/quality/source_section_ledger/source_section_ledger_2026-04-30/source_section_ledger.md`

The current remaining-source queue for exact manifest windows is:

- `research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-01/source_processing_queue.md`

## Non-Claims

This registry does not claim coverage of:

- the remaining line records in the 8 manifested text extracts;
- the 4 local `research/txt/` extracts not referenced by a document manifest;
- repo PDFs outside `research/pdf/`, such as charts and OFM package metadata;
- source sections that appear in source-document tables of contents but are
  not listed in `documents/*.json`.

The RR-17 adequacy result remains valid for the older landed 22-window frame
only. A fresh 2026-05-04 adequacy pack exists for this 46-section frame at
`quality/adequacy/adequacy_2026-05-04-clearance-comms-80-20/`, but its review
CSVs still require adjudication before it can be cited as evidence of semantic
adequacy.

## How To Widen Scope

Scope widening requires a new manifest entry or a new manifest file, followed
by the standard ingest, promote, curate, audit, snapshot, and adequacy-review
flow.

Each new source section also needs a disposition in the source-section ledger
before ingestion starts, so that omissions are explicit rather than accidental.

## Review Considerations

FP / type safety: this is a registry-scope contract only; no Kotlin/domain
code changed.

Test architecture: the load-bearing checks are the registry reproducibility
audit, quote/schema/authority gates, regression snapshot, source inventory,
and source-section ledger.

Impact: consumers can depend on the registry without over-reading its scope.
Future extraction work must widen the declared slice deliberately.

Operational correctness: no new regulatory or phraseology claim is made here.
Operational claims remain source-bound to their individual registry records.
