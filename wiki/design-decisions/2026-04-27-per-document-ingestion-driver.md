# 2026-04-27 Per-Document Ingestion Driver

## Decision

The Ollama-first prototype now has a two-tier structure:

- **Per-section pipeline** (`run_icao4444_ollama_first_prototype.py`): one
  slice → structure / extraction / per-candidate adjudication / overrides /
  judge. Architecture established by Spike 7. Unchanged.
- **Per-document driver** (`ingest_document.py`): reads
  `documents/{id}.json` manifest, loops over sections, calls the
  per-section pipeline for each, aggregates into a per-document candidate
  registry.

The unit of source ingestion is a section. The unit of *coverage* is a
document. Adding a section to a document = manifest entry + driver re-run.

## Why

The stated programme goal is "ingest all of these relevant text, get them
clean, and get clear / accurate source units." After nine source-family
slices proved the per-section architecture stable, the next bottleneck is
section-by-section enumeration across many documents. The per-document
driver removes that bottleneck without changing the per-section pipeline:

- Each document has 5–20 useful sections.
- Sections individually need 30–200 lines of source.
- Per-document caching makes the driver re-runnable as sections are added.
- Aggregate output gives downstream consumers (deterministic tests,
  exploratory probes, suspicion-seeding) a single per-document candidate
  registry rather than scattered per-section directories.

## Consequence

- Per-section work continues to be the primary architectural unit.
  Manifests are hand-authored; section discovery is not yet automated.
- The CASES dict in `run_icao4444_ollama_first_prototype.py` keeps its
  existing entries for ad-hoc single-section spike work; manifests are
  the way to ingest a document at scale.
- Cross-section deduplication, cross-document cross-referencing, and
  parallel section execution are deferred.

## Evidence

- [icao4444-ollama-first-per-document-driver-2026-04-27.md](/home/andrew/dev/projects/twr2/docs/design/icao4444-ollama-first-per-document-driver-2026-04-27.md)
- `research/tools/requirements-spike/ingest_document.py` (driver)
- `research/tools/requirements-spike/documents/icao4444.json` (first manifest)
