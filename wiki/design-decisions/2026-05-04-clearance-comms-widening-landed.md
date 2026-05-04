# Clearance/Comms Widening Landed

Date: 2026-05-04

## Decision

Treat the clearance/communications exact-window queue from
`research/tools/requirements-spike/quality/source_processing_queue/source_processing_queue_2026-05-01/`
as landed in the Ollama-first registry. This supersedes the 2026-05-01 state
where 16 manifest windows were ready for processing but not yet promoted.

The live declared slice is now the 46 exact windows listed in
`research/tools/requirements-spike/documents/*.json`; every one of those
manifest windows has accepted registry output.

## Result

The remote Ollama-backed run produced a clean 16-section batch for:

- CAP 413: 8 sections.
- ICAO Doc 4444: 7 sections.
- ICAO Doc 9432: 1 section.

Promotion and curation landed the result in
`research/tools/requirements-spike/registry/ollama_first/`:

- 423 accepted records.
- 0 pending records.
- 34 rejected records.
- 46 / 46 manifest windows landed.

The current status snapshot is
`research/tools/requirements-spike/registry/ollama_first/STATUS-2026-05-04.md`.
The current regression baseline is
`research/tools/requirements-spike/quality/snapshots/judgements-2026-05-04-post-clearance-comms.csv`.

## Validation

The landed registry passed the deterministic checks that protect mechanical
integrity:

- Reproducibility audit: 457 records, 0 mismatches.
- Accepted quote audit: 534 quotes, 0 misses.
- Regression check against the partial clearance/comms baseline: 0 hard and 0
  soft warnings.
- Offline tool tests: 91 quality-gate tests and 13 override-contract tests.

A new adequacy review pack exists at
`research/tools/requirements-spike/quality/adequacy/adequacy_2026-05-04-clearance-comms-80-20/`.
It is intentionally not counted as completed adequacy evidence until the sampled
records and source sections are adjudicated against the source text.

## Implication

Downstream consumers may treat the registry as the complete landed translation
of the current declared 46-window slice. They must not treat it as a full-source
or full-corpus extraction, and they must not treat semantic adequacy as
independently accepted until the 2026-05-04 adequacy pack is reviewed and any
material findings are repaired.

## Review Considerations

FP / type safety: no Kotlin/domain model changed. The only tool change was path
normalisation in `promote_to_registry.py`, preserving deterministic promotion
while allowing source paths produced on the remote machine to resolve in the
local repo.

Test architecture: confidence is currently mechanical and traceability-focused:
promotion conflict checks, reproducibility audit, quote-presence audit,
regression comparison, and offline Python tests. The remaining required test of
fitness is semantic adequacy adjudication of the sampled records and sampled
sections.

Impact: this removes the ambiguity between "queued" and "landed" for the
clearance/comms seam. The main failure mode now is over-claiming completeness:
the registry is complete for declared manifest windows, not for all source text
outside those manifests.

Operational correctness: no new ATC rule claim is made by this decision. The
source-unit records themselves retain their document/section provenance; future
use in controller or pilot behaviour must cite the exact source record and
underlying document section.
