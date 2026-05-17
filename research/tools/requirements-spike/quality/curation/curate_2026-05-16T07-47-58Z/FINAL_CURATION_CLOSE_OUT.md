# FN20 Pending Registry Curation Close-Out

Date: 2026-05-16

## Scope

This close-out covers the one-off FN20 pending-registry curation pass over the
registry state produced by the final v7 ingest.

- Source run root: `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6`
- Curation run: `research/tools/requirements-spike/quality/curation/curate_2026-05-16T07-47-58Z/`
- Runtime log: `/Users/andrew/requirements-source-units-fn10-2026-05-16-curation.log`
- Registry root: `research/tools/requirements-spike/registry/ollama_first/`

## Curation Result

The full GPT curation pass completed all 1,353 records that remained after the
three-record smoke run.

Raw GPT actions:

| Action | Count |
| --- | ---: |
| `promote_with_corrections` | 478 |
| `promote` | 425 |
| `reject` | 357 |
| `keep_pending` | 93 |

Per-document raw actions:

| Document | Promote | Promote with corrections | Reject | Keep pending |
| --- | ---: | ---: | ---: | ---: |
| `cap413-extracted` | 116 | 47 | 37 | 13 |
| `eppls-extracted` | 48 | 133 | 37 | 12 |
| `h01-extracted` | 52 | 53 | 114 | 18 |
| `icao4444-extracted` | 152 | 216 | 147 | 47 |
| `icao9432-extracted` | 53 | 28 | 15 | 2 |
| `sera-923-2012-extracted` | 4 | 1 | 7 | 1 |

## Final QA Repair

The first post-curation quote audit failed. The failures were accepted records
whose quoted support was not a contiguous verbatim substring of the extracted
source text:

- `eppls-extracted`: 101 accepted records, 107 quote misses.
- `h01-extracted`: 9 accepted records, 11 quote misses.

The EPPLS failures are mostly caused by the PDF extraction interleaving
two-column text. The resulting claims may be semantically plausible, but they do
not satisfy the registry's exact-source-quote contract. The H01 failures were
also treated as hard quote-contract failures. All 110 records were demoted back
to `pending` with explicit `audit.postCurationDemotion` metadata.

Demotion report:
`research/tools/requirements-spike/quality/curation/curate_2026-05-16T07-47-58Z/post_curation_quote_demotions.json`

Final registry counts:

| Bucket | Count |
| --- | ---: |
| Accepted candidates | 2,921 |
| Pending | 106 |
| Rejected | 392 |

Final pending records:

| Document | Keep pending | Post-quote-audit demotion | Total pending |
| --- | ---: | ---: | ---: |
| `cap413-extracted` | 13 | 0 | 13 |
| `eppls-extracted` | 12 | 4 | 16 |
| `h01-extracted` | 18 | 9 | 27 |
| `icao4444-extracted` | 47 | 0 | 47 |
| `icao9432-extracted` | 2 | 0 | 2 |
| `sera-923-2012-extracted` | 1 | 0 | 1 |

## EPPLS Chapter 12 Extraction Repair

On 2026-05-17 the EPPLS source text was repaired from a whole-book,
layout-preserving text dump to a Chapter 12-only Poppler plain-text extraction.
This keeps the user-requested scope to Chapter 12 and avoids the two-column
line interleaving that caused most EPPLS quote failures.

- Extractor: `research/tools/requirements-spike/extract_eppls_ch12_text.py`
- Repaired source: `research/txt/eppls-extracted.txt`
- Source SHA-256:
  `be3a51ff19cd9c7f746b12a5d177fb482d47f751814dbcc818e7c39f1b405dc2`
- Repair report:
  `research/tools/requirements-spike/quality/curation/curate_2026-05-16T07-47-58Z/eppls_ch12_extraction_repair.json`

The repair promoted 97 of the 101 EPPLS records previously demoted for quote
misses. Four EPPLS post-quote-audit demotions still fail exact quote
verification and remain pending.

## Final Gates

The manifest was refreshed after the QA demotion repair.

Final checks:

| Check | Result |
| --- | --- |
| `audit_registry_reproducibility.py` | pass; 3,419 records audited, 0 unexpected files, 0 mismatches |
| `audit_quotes.py --registry-root registry/ollama_first` | pass; 3,482 quotes, 0 misses |
| `test_quality_gates.py` | pass; 94 tests |
| `test_override_contracts.py` | pass; 13 tests |
| `build_source_processing_queue.py` | pass; 106 pending curation records, 0 ready-to-ingest windows |

## Non-Claims

- The 203 residual pending records are not accepted source units.
- EPPLS Chapter 12 remains background-support only; the registry now accepts
  only the EPPLS records whose exact quotes pass against the repaired
  Chapter 12-only extracted text.
- The post-curation registry is mechanically consistent, but it is not a claim
  that every semantically useful EPPLS/H01 passage has been recovered. The
  demoted records require source-text repair or human re-extraction before they
  can be accepted.
