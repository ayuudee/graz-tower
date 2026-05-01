# Source Document Inventory Scope

Date: 2026-04-29

## Decision

Treat the RR-10/RR-17 registry as a high-quality extraction of the declared
22 line-range windows, not as a full-document or full-corpus extraction.

The checked inventory artifact is:

- `research/tools/requirements-spike/quality/source_inventory/source_inventory_2026-04-29/source_document_inventory.md`
- `research/tools/requirements-spike/quality/source_inventory/source_inventory_2026-04-29/text_extract_inventory.csv`
- `research/tools/requirements-spike/quality/source_inventory/source_inventory_2026-04-29/manifest_sections.csv`
- `research/tools/requirements-spike/quality/source_inventory/source_inventory_2026-04-29/pdf_source_map.csv`
- `research/tools/requirements-spike/quality/source_inventory/source_inventory_2026-04-29/repo_pdf_supplement.csv`

## Findings

The current requirements-spike manifests contain:

- 8 document manifests in `research/tools/requirements-spike/documents/`;
- 22 selected line-range sections;
- 1,538 unique covered source-line records.

Those 1,538 lines are:

- 1.69% of the 90,910 line records in the 8 manifested `research/txt/` extracts;
- 1.08% of the 141,767 line records across all 12 local `research/txt/` extracts.

Four local text extracts are outside the requirements-spike manifests:

- `cap413-aerodrome-chapter.txt`;
- `h01-aerodrome-chapter.txt`;
- `icao9432-aerodrome-chapter.txt`;
- `nolan-fundamentals-extracted.txt`.

The repo-wide PDF scan found 25 PDFs:

- 9 requirements-source PDFs under `research/pdf/`;
- 16 chart/OFM metadata PDFs outside `research/pdf/`, listed separately in
  `repo_pdf_supplement.csv`.

The all-`research/txt/` denominator is a file-inventory metric rather than a
deduplicated semantic-corpus metric because the CAP 413, H01, and ICAO 9432
aerodrome excerpt files overlap their full-source extracts.

## Implication

The RR-17 adequacy result remains valid only for the declared 22-section frame.
It should not be used as evidence that all available source documents have been
translated into actionable structured facts.

Any downstream consumer that needs broader coverage must request a new manifest
widening pass over explicit source documents and sections.

## Review Considerations

FP / type safety: no Kotlin/domain code changed.

Test architecture: this is an audit artifact. Verification is deterministic
filesystem, manifest, PDF identity, and line-coverage checking, not Gradle tests.

Impact: this narrows the confidence claim. The registry can still be relied on
for the 22 declared windows, but not for full-corpus coverage.

Operational correctness: no new ATC rule or phraseology claim is made here.
The artifact is about source scope and coverage boundaries.
