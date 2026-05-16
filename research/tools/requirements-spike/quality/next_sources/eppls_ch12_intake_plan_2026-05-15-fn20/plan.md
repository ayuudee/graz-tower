# EPPLS Chapter 12 Intake Plan

Generated: `2026-05-15`

Flow task: `fn-20-fn10-source-unit-ingest-close-out-and.5`

## Goal

Determine whether `research/pdf/EPPLS.pdf` contains Chapter 12 material worth
adding to the FN10 source-unit close-out, and if so produce source-window
manifests with exact provenance for ingestion. If the PDF cannot be identified
or text cannot be extracted reliably, record a loud blocker instead of creating
speculative windows.

## Current Evidence

Local file:

- path: `research/pdf/EPPLS.pdf`
- size: `73,286,341` bytes (`70M`)
- SHA-256: `dc50992422b288e8934799c31fe64c0ae2f17929c1c7c55b49078ddb0236af7b`
- PDF version: `1.6`
- macOS metadata page count: `782`
- macOS metadata title: `Front.fm`
- creator/application metadata: `FrameMaker 12.0.4`, `Acrobat Distiller 15.0 (Windows)`
- macOS metadata security: `Password Encrypted`

Local blockers observed during intake:

- `pdfinfo` and `pdftotext` are not available in the current shell.
- No `pypdf`, `PyPDF2`, `pdfminer`, or `fitz` module is available in the Nix
  Python used for the requirements-spike pipeline.
- `strings` does not expose useful title/chapter text, likely because the PDF
  streams are compressed.

## Plan

### 1. Establish PDF Identity

1. Extract or inspect the first pages using a reproducible local command.
   Preferred command path is Poppler (`pdfinfo`, `pdftotext -layout`). If the
   current shell lacks it, run through the project Nix environment or a
   one-shot Nix shell with Poppler tools.
2. Record title, edition/date, publisher/issuer, page count, security flags,
   and extraction command in an intake record.
3. Verify that this file is actually the EPPLS source intended by the user.
   Do not infer identity from filename alone; the current metadata title is
   only `Front.fm`.

Exit gate:

- If title/edition cannot be verified, mark EPPLS `intake_blocked` with the
  checksum and metadata above.

### 2. Produce a Text Extract

1. Produce `research/txt/eppls-extracted.txt` from `research/pdf/EPPLS.pdf`
   using a deterministic command, preserving layout where possible.
2. If Poppler reports encryption or text extraction failure, try an explicit
   non-owner-password extraction if permitted by the file, then OCR only if the
   text layer is absent and OCR can be made reproducible enough for line-number
   provenance.
3. Count lines, capture extraction command, and store a short first-page
   evidence excerpt in the intake record.

Exit gate:

- If text extraction cannot produce stable line numbers, stop and mark EPPLS
  `text_extraction_blocked`. Do not create manifest windows from page images
  without a repeatable text/line mapping.

### 3. Locate Chapter 12

1. Search the text extract for Chapter 12 headings, table-of-contents entries,
   and page headers.
2. Determine exact line range for Chapter 12 and its sub-sections.
3. Build a Chapter 12 section inventory with:
   - section id
   - title
   - source line range
   - source page range if available
   - likely content type: phraseology, pilot procedure, explanatory text,
     table, note, or non-ATC metadata
   - initial relevance disposition

Exit gate:

- If no Chapter 12 is found, record a no-Chapter-12 blocker with search terms
  and evidence.

### 4. Relevance Review

Review Chapter 12 for material relevant to FN10 source units:

- pilot/controller radiotelephony phraseology
- clearance, readback, transfer, frequency-change, emergency, urgency, and
  aerodrome communication procedures
- pilot-side phraseology that complements, but does not supersede, ICAO/SERA/
  CAP/H01 sources
- explicit references to primary regulation or procedure sources

Exclude:

- general language-learning content with no ATC operational claim
- examples that are not reusable source units
- purely pedagogical advice without operational or phraseology content
- content whose provenance or text extraction is unstable

### 5. Manifest Construction

For accepted Chapter 12 windows:

1. Add a new document manifest, likely `documents/eppls.json`, only after the
   source identity and line ranges are verified.
2. Use narrow windows. Prefer sub-section windows over full-chapter windows.
3. Set authority ceiling conservatively until document identity is known:
   - `background_support` or `best_practice` for training-language content
   - stronger authority only if the source itself is confirmed as an official
     procedure/phraseology source and the exact section supports that ceiling
4. Notes must state EPPLS is source-local and not integrated into precedence
   decisions in this close-out stream.

### 6. Ingestion and QA

1. Generate a source-processing queue that includes only accepted EPPLS windows.
2. Dry-run the queue before model calls.
3. Run ingestion in a dedicated durable root, not the v6 current-frame root.
4. Run raw-root consistency audit for the EPPLS run.
5. Promote/package only strict-complete sections that pass quote/schema/
   reproducibility gates.
6. If any EPPLS windows fail, classify failures and keep them visible.

## Deliverables

- `intake_record.json` with file identity, checksum, metadata, extraction
  command, and blocker status if applicable.
- `chapter12_inventory.md` and `.json` with line-ranged Chapter 12 sections.
- `documents/eppls.json` only if at least one stable, relevant window exists.
- EPPLS-specific queue/run artifacts if windows are ingested.
- Final close-out note stating whether EPPLS contributed source units or was
  blocked/out-of-scope.

## Review Considerations

**FP / type safety:** EPPLS source state is explicit and total:
`intake_blocked`, `text_extraction_blocked`, `chapter_not_found`,
`inventory_ready`, `manifest_ready`, `ingested`, or `blocked_after_ingest`.
No downstream step may treat an unknown state as success.

**Test architecture:** The key tests are reproducibility checks: checksum,
deterministic text extraction, stable line ranges, dry-run queue validation,
raw-root consistency audit, quote audit, schema gates, and registry
reproducibility. A manifest without stable line provenance fails the plan.

**Impact:** Keeping EPPLS in a dedicated source package avoids mixing
pilot-side or training-oriented material into the current ICAO/SERA/CAP/H01
authority frame. This makes the final FN10 output clearer but requires explicit
non-claims about cross-source precedence.

**Operational correctness:** No ATC law or phraseology claim may be made from
EPPLS without exact document identity, edition/date, chapter/section, and line
range. EPPLS content must not weaken or supersede primary regulatory sources in
this close-out stream.
