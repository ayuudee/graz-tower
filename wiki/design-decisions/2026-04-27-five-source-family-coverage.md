# 2026-04-27 Five-Source-Family Coverage

## Decision

The Ollama-first prototype now has working source ingestion on five
families across three authority ceilings:

- ICAO 4444 readback (authoritative_requirement, numbered clause)
- ICAO 4444 transfer (authoritative_requirement, parent + siblings)
- EGAST VFR (best_practice, unnumbered prose)
- H01 §3.8.1 (operational_guidance, bilingual)
- CAP 413 §2.68–2.71 (operational_guidance, dialogue-interleaved manual)

Adding a new family is now "add a `CASES` entry, run, possibly bump a
budget" — the architecture established by Spike 7 (challenger →
defender → bundle gate → judge with three deterministic post-steps)
holds without modification.

This completes the platform document's three-spike proving sequence
(ICAO 4444 / H01 / EGAST) plus two additional families.

## Why

Two reasons for landing the broader coverage now:

1. The user's stated goal is "ingest all of these relevant text, get
   them clean, and get clear / accurate source units" as the first
   step of the broader programme. Five-family coverage is concrete
   progress toward that.

2. The RR-5 deterministic override pattern was load-bearing on
   ICAO 4444 and EGAST. Both H01 and CAP 413 confirm it as a
   cross-family phenomenon, not an ICAO-4444 quirk. The override
   fired exactly once on H01 (parent + nested list, mixed modality)
   and four times on CAP 413 (one mixed-modality bundle + three
   reflexive authority verdicts on `modality=none` candidates). The
   pattern is robust.

A third observation: the override does **not** mask judge
source-grounding checks. CAP 413's `req_controller_ask_readback`
went through the override (challenger → supported) but the judge
independently ruled `unsupported_by_source`. The override only
neutralises one specific class of challenger noise.

## Consequence

- Five families covered.
- Mechanical scaling pattern recurring: bigger window → denser
  structure → larger extraction → longer inference. Each new family
  may need budget bumps. New defaults: structure `num_predict`
  5000→8000 (attempts), 6000→10000 (reconciliation); all timeouts
  doubled. Inert for smaller cases.
- The `background_support` authority ceiling is not yet probed.
  Candidates: SafetySense22 best-practice notes, ICAO 9432 example
  dialogues.
- Suggested next probes: ICAO 9432, SERA 923/2012, SafetySense22,
  Slovenia VFR.

## Evidence

- [icao4444-ollama-first-h01-cap413-multi-family-2026-04-27.md](docs/design/icao4444-ollama-first-h01-cap413-multi-family-2026-04-27.md)
- [H01 readback summary](/tmp/icao4444-ollama-first-prototype-h01-readback/summary.md) — 8 candidates, 6 accepted
- [CAP 413 readback summary](/tmp/icao4444-ollama-first-prototype-cap413-readback/summary.md) — 8 candidates, 6 accepted
