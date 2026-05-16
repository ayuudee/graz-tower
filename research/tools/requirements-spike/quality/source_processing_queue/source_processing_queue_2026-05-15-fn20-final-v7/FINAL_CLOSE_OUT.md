# FN20 Final v7 Source-Unit Ingest Close-Out

Generated: `2026-05-16`

## Outcome

The final all-source ingest completed against
`/Users/andrew/requirements-source-units-fn10-2026-05-11-v6`.

- run summary: `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6/batch_run_summary.md`
- preserved batch manifest: `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6/batch_manifest_used.json`
- raw consistency audit: `/Users/andrew/requirements-source-units-fn10-2026-05-11-v6/raw_ingest_consistency_audit.md`
- ingest log: `/Users/andrew/requirements-source-units-fn10-2026-05-15-final-v7.ingest.log`
- split classification: `SPLIT_FAILURE_CLASSIFICATION.md`

Final ingest counts:

| Source | Sections | Judged | Accepted by ingest | Advisory | Human review | Other | Failed |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `cap413-extracted` | 46/46 | 519 | 323 | 173 | 7 | 16 | 0 |
| `eppls-extracted` | 24/24 | 231 | 3 | 224 | 0 | 4 | 0 |
| `h01-extracted` | 48/48 | 613 | 475 | 89 | 13 | 36 | 0 |
| `icao4444-extracted` | 112/112 | 1335 | 924 | 289 | 27 | 95 | 0 |
| `icao9432-extracted` | 19/19 | 161 | 65 | 94 | 2 | 0 | 0 |
| `sera-923-2012-extracted` | 17/17 | 98 | 89 | 2 | 0 | 7 | 0 |
| **Total** | **266/266** | **2957** | **1879** | **871** | **49** | **158** | **0** |

After promotion, the regenerated queue has no remaining ready-to-ingest
manifest windows:

- manifest windows: `313`
- landed manifest windows: `270`
- pending-only manifest windows: `43`
- ready-to-ingest manifest windows: `0`
- pending curation records: `1356`
- failed-window retry candidates: `0`
- high-priority source-window hardening rows: `0`

## QA Evidence

Raw-root consistency audit:

- status: `pass`
- sections requested: `266`
- strict-complete sections: `266`
- unreadable JSON: `0`
- hard consistency issues: `0`
- warnings: `22`

The warnings are non-manifest stale partial directories from interrupted or
superseded broad windows. None has a `run_manifest.json`; they are retained as
visible operational history and are not part of the current-frame manifest.

Promotion and gates:

- promotion run: `research/tools/requirements-spike/registry/ollama_first/runs/promote_2026-05-16T06-47-39Z_b460.json`
- promoted registry manifest: `research/tools/requirements-spike/registry/ollama_first/manifest.json`
- registry reproducibility: `pass` (`3419` records audited, `0` mismatches)
- quote audit: `pass` (`2417` quotes, `0` misses)
- quality gates: `pass` (`94` tests)
- override contracts: `pass` (`13` tests)

Promotion produced `3` duplicate-within-run conflicts, all preserved in the
promotion run record:

- `h01-extracted/controlled_aerodromes_airborne_entry_5_3_2_1_to_5_3_2_3`
- `h01-extracted/uncontrolled_aerodromes_5_4`
- `icao4444-extracted/longitudinal_ads_b_ads_c_5_4_2_7_to_5_4_2_8`

Each conflict is an equivalent canonical ID inside the same section. The
promoter kept the first record and dropped the duplicate, with the original
candidate IDs recorded in the run artifact.

## EPPLS

EPPLS Chapter 12 was ingested as background material, not legal or phraseology
authority.

- source PDF: `research/pdf/EPPLS.pdf`
- extracted text: `research/txt/eppls-extracted.txt`
- manifest: `research/tools/requirements-spike/documents/eppls.json`
- intake artifacts: `research/tools/requirements-spike/quality/next_sources/eppls_ch12_intake_plan_2026-05-15-fn20/`
- EPPLS-only queue: `research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-15-fn20-eppls-only/`

EPPLS produced `231` judged candidates. The ingest accepted `3`; promotion
landed `1` accepted registry record and left the rest pending or non-promoted.
The useful accepted content is in
`ch12_key_readbacks_takeoff_cleared_word_limits`; it concerns readback
sequence and the restricted use of `Departure` / `Cleared` wording.

## Nolan

Nolan was inventoried, but no final ingest window was added to this run.

- inventory artifacts: `research/tools/requirements-spike/quality/next_sources/nolan_inventory_2026-05-15-fn20/`

The final non-claim is deliberate: Nolan remains background material only unless
a later task selects a specific line-stable unit and records its dependency on
primary authority.

## Non-Claims

- This is not a full-corpus completeness claim.
- Pending registry records are not treated as accepted source units.
- No cross-source precedence, deduplication, or legal hierarchy selection is
claimed by this close-out.
- The existing `build_source_unit_packages.py` tool is still fn9-frame scoped;
it should not be used as the final package validator for this fn20 frame until a
matching fn20 closure report exists.

## Review Considerations

**FP / type safety:** This close-out is data/tooling work. The final state keeps
section states explicit: landed, pending-only, stale non-manifest partial, or
out-of-scope. No failed ingest window is silently counted as complete.

**Test architecture:** The close-out evidence is the preserved manifest, strict
raw-root audit, registry reproducibility audit, quote audit, quality-gate tests,
and override-contract tests. The remaining pending records require curation,
not another ingest retry.

**Impact:** The registry is now much wider and has `1356` pending records. That
is expected from the conservative promotion gates and should be treated as the
next curation queue, not as an ingest failure.

**Operational correctness:** Operational claims remain source-bound. EPPLS and
Nolan are background sources by default; authoritative claims must continue to
come from CAP 413, ICAO, SERA, H01, or another primary source with explicit
citations.
