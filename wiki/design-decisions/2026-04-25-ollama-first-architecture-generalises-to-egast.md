# 2026-04-25 Ollama-First Architecture Generalises To EGAST

## Decision

Keep the Spike 3 four-stage adjudication architecture as the standard
Ollama-first prototype. It demonstrably handles a structurally very
different source family (`EGAST VFR` advisory prose with `best_practice`
ceiling) without modification to challenger / defender / bundle-gate /
judge logic.

The only changes needed for advisory source families are mechanical
budget bumps:

- default `num_ctx` 12288 → 16384
- extraction-stage `num_predict` 3500 → 8000

These are now defaults, transparent to `ICAO 4444` runs.

A new case `egast_readback_family` and a `sourceOverride` mechanism were
added so a single prototype script can probe multiple source documents
without per-case CLI plumbing.

## Why

The platform document committed the architecture to a three-spike
proving sequence over `ICAO 4444`, `H01`, and `EGAST VFR`. The
question was whether the four-stage architecture established for
`ICAO 4444` would survive on advisory prose where the family ceiling
is `best_practice` rather than authoritative.

The Spike 4 EGAST run produced 6 candidates, 3 accepted, 3 advisory_only,
all classified at `best_practice` or `background_support`. The structure
stage handled unnumbered prose with inline page-layout noise; the bundle
gate sensibly returned `scopeComplete=true` everywhere; the family
ceiling held.

This is the architecture's first real evidence of generality.

## Consequence

`RR-5` (challenger uses `authority_too_high` for mixed-modality
structural concerns) is upgraded from L to M impact. On `ICAO 4444` the
judge filters that bug because good candidates have `shall` anchors. On
EGAST the judge cannot lean on `shall`, and 2 of 5 promotable candidates
were demoted from `accepted` to `advisory_only` as a result. The bug now
damages registry-surface outcomes on advisory source families.

Future generality probes (`H01`, `CAP 413`, `Doc 9432`) should be
straightforward additions to the existing prototype, using the same
case-plus-sourceOverride pattern.

## Evidence

- [icao4444-ollama-first-egast-generality-2026-04-25.md](docs/design/icao4444-ollama-first-egast-generality-2026-04-25.md)
- [Spike 4 readback summary](/tmp/icao4444-ollama-first-prototype-spike-4-egast-readback/summary.md)
