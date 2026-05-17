# Requirements Source Units

Last updated: 2026-05-16

## FN20 Final v7 Ingest Close-Out

The one-off FN20 all-source ingest completed against:

- Raw root: `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6`
- Close-out report: `research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-15-fn20-final-v7/FINAL_CLOSE_OUT.md`
- Post-promotion queue/status: `research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-15-fn20-final-v7/source_progress_report.md`

Final ingest status:

- Ingested sections: `266/266`
- Ingest failures: `0`
- Raw-root consistency audit: `pass` (`0` unreadable JSON, `0` hard consistency issues)
- Registry reproducibility: `pass`
- Quote audit: `pass` (`2417` quotes, `0` misses)
- Ready-to-ingest manifest windows after promotion: `0`
- Pending curation records after promotion: `1356`

## FN20 Pending Curation Close-Out

The FN20 pending-registry curation pass completed on 2026-05-16:

- Curation report: `research/tools/requirements-spike/quality/curation/curate_2026-05-16T07-47-58Z/FINAL_CURATION_CLOSE_OUT.md`
- Curation log: `/Users/andrew/requirements-source-units-fn10-2026-05-16-curation.log`
- Raw GPT actions: `425` promote, `478` promote with corrections, `357`
  reject, `93` keep pending.
- EPPLS Chapter 12 extraction repair:
  `research/tools/requirements-spike/quality/curation/curate_2026-05-16T07-47-58Z/eppls_ch12_extraction_repair.json`
- Final accepted registry records: `2921`
- Final pending records: `106`
- Final rejected records: `392`
- Registry reproducibility: `pass` (`3419` records audited, `0` mismatches)
- Quote audit: `pass` (`3482` quotes, `0` misses)
- Source processing queue: `0` ready-to-ingest windows, `106` pending
  curation records.

Final QA originally demoted `110` accepted records back to pending because their
exact quotes could not be verified as contiguous verbatim substrings of the
extracted source text. EPPLS was then repaired by replacing the whole-book,
layout-preserving text dump with a Chapter 12-only Poppler plain-text
extraction (`research/tools/requirements-spike/extract_eppls_ch12_text.py`,
`research/txt/eppls-extracted.txt`). That repair promoted `97` EPPLS records
whose quotes became mechanically verifiable. The residual EPPLS pending set is
`16` records: `12` explicit `keep_pending` outcomes and `4` remaining
post-quote-audit demotions. H01 still has `9` post-quote-audit demotions.

EPPLS Chapter 12 is now present and ingested as background-support material
(`research/pdf/EPPLS.pdf`, `research/txt/eppls-extracted.txt`,
`research/tools/requirements-spike/documents/eppls.json`). Nolan has an
inventory artifact but no ingested window in this close-out:
`research/tools/requirements-spike/quality/next_sources/nolan_inventory_2026-05-15-fn20/`.

FN20 does not supersede the fn9 package frame below. The package builder remains
fn9-frame scoped until a matching fn20 closure report exists. Pending records
are not accepted source units.

## FN9 Package Frame

The current source-unit package frame is:

- Package root: `research/tools/requirements-spike/quality/source_packages/source_packages_2026-05-09-fn9-current-frame/`
- Closure review: `research/tools/requirements-spike/quality/closure/closure_2026-05-09-fn9/closure_report.md`
- Final three-level status report: `research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-09-fn9-final/source_progress_report.md`

Current package status:

| Source | Status | Accepted units | Scope note |
| --- | --- | ---: | --- |
| CAP 413 | `scoped_ready` | 79 | Manifested windows only; source-window hardening backlog remains. |
| EGAST VFR guide | `ready` | 17 | Current manifest complete. |
| H01 AIC A 21/23 | `scoped_ready` | 24 | English-side scoped; no German/English translation-equivalence claim; not full-document complete. |
| ICAO Doc 4444 | `scoped_ready` | 186 | Manifested windows only; source-window hardening backlog remains. |
| ICAO Doc 9432 | `scoped_ready` | 22 | Manifested windows only; source-window hardening backlog remains. |
| SafetySense 22 | `ready` | 15 | Current manifest complete. |
| SERA 923/2012 | `scoped_ready` | 73 | Manifested windows only; source-window hardening backlog remains. |
| Slovenia VFR phraseology guide | `ready` | 15 | Current manifest complete. |

No current-frame source is blocked by pending records, failed manifest windows,
quote/source misses, or registry reproducibility mismatch. Nolan and EPPLS are
not part of this package frame; Nolan is available for later inventory as
conceptual support, while EPPLS is intake-blocked until the file is present and
identified.
