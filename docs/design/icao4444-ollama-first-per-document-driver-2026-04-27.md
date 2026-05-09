# Per-Document Ingestion Driver 2026-04-27

This pass adds the second tier of the Ollama-first ingestion pipeline:
a per-document driver that loops over the sections of one document,
runs the per-section pipeline for each, and aggregates results into a
per-document candidate registry.

The unit shift: per-section to per-document. **The architecture established
through Spike 7 stays unchanged at the per-section level** — the driver
is a thin wrapper that calls `run_pipeline()` once per section and
aggregates outputs.

## Architecture

```
                       +--------------------------------+
                       |  documents/{id}.json manifest  |
                       |    (sections, source path,     |
                       |     default authority ceiling) |
                       +--------------------------------+
                                     |
                                     v
                       +--------------------------------+
                       |  ingest_document.py            |
                       |  --document {id}               |
                       |  --output-dir {path}           |
                       +--------------------------------+
                                     |
                                     v
                       (per-section loop, sequential)
                                     |
                                     v
                       +--------------------------------+
                       |  run_pipeline(case, args, dir) |
                       |  (existing per-section pipeline:
                       |   structure -> extraction ->   |
                       |   per-candidate adjudication)  |
                       +--------------------------------+
                                     |
                                     v
                       +--------------------------------+
                       |  per-section output:           |
                       |    {output_dir}/{sectionId}/   |
                       |       summary.md, run_manifest,|
                       |       judge/, override audits  |
                       +--------------------------------+
                                     |
                                     v
                       +--------------------------------+
                       |  aggregate_document()          |
                       |    walks all per-section dirs, |
                       |    produces:                   |
                       |    - accepted_candidates.json  |
                       |    - summary.md (doc level)    |
                       +--------------------------------+
```

## Manifest schema

A document manifest lives at
`research/tools/requirements-spike/documents/{document_id}.json`:

```json
{
  "documentId": "icao4444-extracted",
  "documentTitle": "ICAO Doc 4444 PANS-ATM",
  "sourcePath": "research/txt/icao4444-extracted.txt",
  "defaultAuthorityCeiling": "authoritative_requirement",
  "sections": [
    {
      "sectionId": "transfer_4_3_2_1",
      "familyId": "icao4444_transfer_family",
      "startLine": 3086,
      "endLine": 3131,
      "notes": "..."
    }
  ]
}
```

A section may override the document-default authority ceiling with its
own `authorityCeiling`.

The `sectionId` becomes the per-section output directory name and is the
primary key for the section in aggregate output. The `familyId` carries
through to the case dict consumed by `run_pipeline`.

## Caching by section

`already_ingested(section_dir)` checks for a non-empty
`run_manifest.json`. If present, the section is skipped on subsequent
runs unless `--force` is passed. This makes the driver re-runnable as
sections are added incrementally; only the new sections trigger live
Ollama work.

## Aggregate output

Per-document `accepted_candidates.json` schema:

```json
{
  "documentId": "...",
  "totals": {
    "sections": 5,
    "ingested": 5,
    "candidatesJudged": 30,
    "accepted": 18,
    "advisoryOnly": 8,
    "needsHumanReview": 0,
    "other": 4,
    "overridesFired": {
      "challenger": 11,
      "judge": 2,
      "sibling_resolution": 0
    }
  },
  "sections": [...],
  "acceptedCandidates": [
    {
      "sectionId": "...",
      "candidateId": "...",
      "claimText": "...",
      "authorityClass": "...",
      "modality": "...",
      "promotionHint": "...",
      "decision": "accepted",
      "challengerOverridden": true,
      "judgeOverridden": false,
      "sourceItemIds": [...],
      "exactSourceQuotes": [...]
    }
  ],
  "advisoryCandidates": [...],
  "needsHumanReviewCandidates": [...],
  "otherCandidates": [...]
}
```

A parallel human-readable `summary.md` shows section-by-section counts
and lists every accepted candidate with its claim text and any
override markers.

## Why this is the right scale

Section size has been the right granularity throughout the spike work
(typically 30–200 lines). Documents are too coarse to feed to a single
prompt — the extraction model ran into token budgets even on
70-line slices, and would hit ICAO 4444's 21956 lines as a hard wall
before useful output.

So the per-section unit stays. The document layer adds two things on
top:

1. **Coverage tracking**: explicit list of which sections of a document
   have been ingested, what's left.
2. **Aggregation**: a single per-document candidate registry that
   downstream consumers (deterministic test compilation, exploratory
   probes, suspicion-seeding) can read without traversing per-section
   directories.

## What's deferred

These are out of scope for this commit:

- **Cross-section deduplication**: two sections may produce candidates
  with overlapping claims. The aggregate currently collects everything;
  dedup is a downstream consumer concern.
- **Cross-document cross-referencing**: e.g., the ICAO 4444 readback
  rule and the H01 readback rule both express §4.5.7.5.1; surfacing
  this overlap is a later step.
- **Section discovery**: manifests are hand-authored. Auto-enumerating
  sections (e.g., by clause number regex) is a future extension.
- **Parallel section execution**: sections run sequentially. Each takes
  3–10 min on biggy. Parallelisation is a later optimisation.

## Review Considerations

- **FP / type safety**: not applicable to domain code. The driver is a
  pure-Python loop over manifest sections; failures in a single
  section propagate as the existing pipeline's `SystemExit` and stop
  the run. That is the right behaviour for the prototype — silent
  failures would mask budget/timeout issues that need to be addressed.
- **Test architecture**: the per-section contract tests in
  `test_override_contracts.py` already cover the post-step safety
  conditions; the driver itself is thin orchestration that doesn't
  benefit from unit-level tests beyond the sanity check that aggregate
  output has the expected shape.
- **Impact**: this is the natural next step toward the broader source
  ingestion goal. Adding a section is now a manifest entry plus
  re-running the driver. The per-section quality (RR-1..RR-8 pattern,
  three deterministic post-steps) carries through unchanged.
- **Operational correctness**: source claims grounded in
  [icao4444-extracted.txt](research/txt/icao4444-extracted.txt)
  via the existing per-section pipeline.

## Roadmap

The current source corpus has these probed-and-unprobed states:

| Document | Sections covered | Sections likely useful |
|---|---|---|
| ICAO 4444 | 2 (readback, transfer) | many — taxi (§7.6), separation (§5), takeoff (§7.9), landing (§7.10), holding (§4.6), emergencies (§15) |
| EGAST | 1 (readback) | a few — circuit, clearances, weather-decision |
| H01 | 1 (§3.8.1) | several — §3.8.2 end of conversation, §3.8.3 corrections, taxiway/runway phraseology, frequency change |
| CAP 413 | 1 (§2.68–2.71) | many — full RTF chapters per phase of flight |
| ICAO 9432 | 1 (§2.8.3 EN) | many — full manual mirrors CAP 413's structure |
| SERA | 1 (8015(e)) | several — 8005 (compliance), 8010 (flight rules), 8015 other paragraphs |
| SafetySense22 | 1 (readbacks) | a few — circuit calls, frequency change, emergencies |
| Slovenia VFR | 1 (readback) | few — corridor procedures |

Adding a section = manifest entry + driver re-run. Each is small. Total
section count across the corpus is probably 80–150; running them all
takes hours of biggy time but is the path to the stated goal.
