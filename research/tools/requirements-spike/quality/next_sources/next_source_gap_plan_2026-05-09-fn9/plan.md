# Nolan / EPPLS Gap-Analysis Plan

Generated: `2026-05-09`

## Goal

Process the next candidate sources discretely, source-by-source, without
integrating them with CAP 413, ICAO, SERA, H01, EGAST, SafetySense, or Slovenia
packages. The output for each source is the same product shape as the current
frame: a per-source manifest, accepted source units with exact provenance,
validation evidence, and explicit non-claims.

## Current Intake State

| Source | Current repo evidence | Intake state | Initial authority ceiling |
| --- | --- | --- | --- |
| Nolan, *Fundamentals of Air Traffic Control* | `research/pdf/fundies.pdf`; `research/txt/nolan-fundamentals-extracted.txt` | Available for inventory | `background_support` unless a clause is independently tied to a cited regulation |
| EPPLS | No `EPPLS.pdf` or EPPLS text extract found under `research/pdf/` or `research/txt/` on 2026-05-09 | Blocked at intake | Unknown until document identity and edition are verified |

## Source-First Procedure

1. **Intake and identity**
   - Record source path, title, edition/date, page count, checksum, text-extract path, and extraction command.
   - Fail the source intake if the PDF/text extract is absent or title/edition cannot be identified.

2. **Whole-source inventory**
   - Build a deterministic table of contents from the text extract.
   - Run one bounded LLM relevance pass over the whole-source ToC only, asking for candidate sections relevant to ATC communication, clearance, readback, transfer, emergency communication, pilot-side phraseology, and source-specific conceptual support.
   - Compare deterministic ToC selection and LLM selection; disagreements become review rows, not silent inclusions or exclusions.

3. **Manifest construction**
   - Create `documents/<source>.json` only for sections accepted into the source-specific scope.
   - Every manifest window must carry exact line ranges and authority ceiling.
   - Nolan windows default to support/background status; EPPLS ceiling is decided after document identity review.

4. **Sequential Ollama processing**
   - Queue only manifest windows for one source at a time.
   - Preserve immutable run roots and batch manifests.
   - Promote through the existing registry gates; no direct package edits for model output.

5. **Curation and validation**
   - Curate pending records only with verified run-root provenance.
   - Run quote audit, registry reproducibility, quality gates, override contracts, and a fresh adequacy sample for that source.
   - Package the source independently as `ready`, `scoped_ready`, or `blocked`.

6. **No integration in this phase**
   - Do not deduplicate against current packages.
   - Do not choose regulatory precedence.
   - Do not use Nolan or EPPLS to weaken or supersede an ICAO/SERA/CAP source unit.

## Source-Specific Notes

### Nolan

Nolan is likely useful as explanatory and conceptual support, not as a legal or
phraseology authority. Candidate sections should be selected for concepts such
as ATC service purpose, separation, communication coordination, controller/pilot
responsibilities, and training context. Any source unit extracted from Nolan
must avoid `authoritative_requirement` unless the exact text itself cites a
primary regulation and the package records that dependency.

### EPPLS

EPPLS cannot enter processing until the file is present and identified. Once
present, treat pilot-side radio-communication content as source-local evidence.
Pilot-side phraseology may be valuable, but it must remain per-source until a
later integration pass compares it with ICAO/SERA/CAP controller-side sources.

## Review Considerations

**FP / type safety:** Source status is total: `intake_blocked`,
`inventory_ready`, `manifest_ready`, `processing`, `curation`, `package_ready`,
`scoped_ready`, or `blocked`. Missing file identity or missing line windows must
fail the source state rather than emit partial package output.

**Test architecture:** Each source requires intake evidence, manifest/status
report, registry reproducibility, quote audit, quality gates, adequacy sample,
and package validation. The package validator must reconcile source units
against live registry counts.

**Impact:** Keeping sources discrete prevents early precedence mistakes and
allows later integration to choose the best authority source. The tradeoff is
more packages and explicit non-claims.

**Operational correctness:** Regulatory or phraseology claims require exact
document/section/line provenance. Nolan may explain concepts but is not a
primary ATC-law source. EPPLS authority cannot be assessed before intake.

## Exit Criteria

- Nolan has an intake record, relevance inventory, manifest, processed source
  units, validation report, and package, or a loud blocker explaining why not.
- EPPLS has the same, but only after the PDF/text extract is actually present.
- Both sources remain unintegrated with the current packages.
