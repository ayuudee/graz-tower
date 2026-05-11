---
satisfies: [R1, R2, R3, R4, R5, R6, R7, R8, R9, R10, R11, R12, R13]
---

# fn-17.1 — Primary-source Ed 24 verification + conditional rename + CAP_413_EDITION constant

**Execution mode** (codex round-26/27 finding cascade): this task is
intended to run via `flow-next:work`. Commits are required as part
of task completion (per `flow-next:work`'s execution contract). If
run outside `flow-next:work` (e.g., a direct exploration/review
turn), the implementer must explicitly authorise commits or stop at
working-tree state with a clear handoff to the user/pilot.

## Description

Doctrinal-citation cleanup: pull CAP 413 Edition 24 from the CAA primary
source, capture verification (PDF SHA + section titles + ≤ 1-sentence
identifying excerpts + local extraction procedure — licence-bounded per
epic Decision #1), select branch A (renumbering confirmed) / **A-retire
(Ed 24 retires ATC-initiated-GA phraseology — codex round-10 finding
#2)** / B (renumbering refuted) / C (PDF unavailable), then
conditionally execute the rename per the selected branch. Edition-string correction ships in **all branches** via
the new `RegulationRef.CAP_413_EDITION` constant whose value is
**coupled to the branch verdict, NOT to calendar date** (epic Decision
#7 — citation triple coherence):
- Branch A or Branch B: `"Edition 24 (effective 1 July 2026)"` — Ed 24
  was positively verified; the codebase cites Ed 24 doctrine from
  fn-17 merge onward.
- Branch A-retire: `"Edition 24 (effective 1 July 2026)"` for most
  CAP413_* entries; **inline literal `"Edition 23 Corr (effective 21
  January 2021)"` exception** for any CAP413_* entry whose R1 row is
  `RETIRED` and that's NOT updated in-task (most-likely
  `CAP413_4_65`) — citation-triple coherence (codex round-14/15
  findings).
- Branch C: `"Edition 23 Corr (effective 21 January 2021)"` — Ed 24
  could not be verified; the codebase stays on Ed 23 doctrine and
  a deferment is filed for re-attempt.

Closes the `D-PASS-cap413-edition-24-reconciliation` deferment filed in
fn-14. Covers R1-R13, branch-gated per the authoritative R-firing-by-
branch table in epic R2.

## Problem

The codebase cites CAP 413 §4.65 / §4.66 / §4.67 across protocol, pilot,
controller, sim, wiki, AGENTS.md, STRATEGY.md, and four flow specs. The
fn-14 docs-scout claim — "Ed 24 renumbered §4.66 → §4.65 and §4.67 →
§4.66" — was never verified against a CAA-served PDF and is therefore a
**hypothesis**, not a fact. Separately, every existing
`RegulationDatabase.kt` CAP 413 entry carries `edition = "27th ed.
(2023)"` — a factually wrong literal (CAP 413's edition numbering is
1..24, not 27). This task fixes both: verifies the hypothesis against
the Ed 24 primary source (with licence-bounded capture), conditionally
renames per the verdict, and corrects the edition string in all
branches.

## Files (read or modify)

### READ

- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt`
  — current CAP413_* entries (lines 359-480) + edition-string companion
  constants pattern (`SERA_EDITION`, `ICAO_4444_EDITION`,
  `ICAO_9432_EDITION` — verify exact declaration file via `grep -n
  "_EDITION =" protocol/.../*.kt`). Verify per-entry `edition = "27th
  ed. (2023)"` literal at every CAP413_* entry.
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationModel.kt`
  — `RegulationRef` data class shape + companion-object constants
  declaration site (most likely location; confirm via grep above).
- `wiki/data-sources/` (current contents: `identifier-reconciliation.md`,
  `ljmb.md`, `lowg.md`, `overview.md`, `requirements-source-units.md`)
  — provenance file naming convention.
- All current Branch-A target files per epic R5/R6 — pre-rename grep at
  Step 4 enumerates them.

### MODIFY (Branch A — typed-entry rename + KDoc/prose sweep)

- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt`
  — **only one typed `CAP413_4_<n>` entry is in scope: `CAP413_4_65`**
  (codex round-3/4 finding). Per R1 mapping table: rename / retire /
  leave-alone per Step 2's branching rule. **§4.66 / §4.67 / §4.68 are
  prose-only** (NOT typed entries) — handled by Step 4 grep-driven
  sweep (KDoc + prose) and Step 6 (test strings), NOT by this typed
  rename step. Adjacent KDoc on `CAP413_4_65` updated to reflect any
  new section number / refreshed principle if Ed 24 meaning shifted.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt`
  — `import` line at 83 + `regulations =` list entries at 267 / 298;
  KDoc cite at 243 / 340.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/Controller.kt`
  — `RegulationDatabase.CAP413_4_65` reference at 931 + KDoc at 923-926.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt`
  — KDoc prose at 84 / 86 / 303 / 350.
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt` —
  KDoc at 850 / 958 / 1017.
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt`
  — KDoc at 62 / 740 / 758.
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt`
  — KDoc at 606.
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/observe/PilotEvent.kt`
  — KDoc at 75.

### MODIFY (Branch A — test strings)

- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotCrosswindTickATickBTest.kt`
  — KDoc at 63 + message string at 227.
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotAtcInitiatedGoAroundSpec.kt`
  — KDoc at 69-70.
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotCrosswindGoAroundTest.kt`
  — KDoc at 38.
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PlannedGoAroundSpec.kt`
  — KDoc at 58 / 61 + message strings at 248 / 340 + KDoc at 610 / 635.
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/ObstructionGoAroundSpec.kt`
  — KDoc at 64 + string-literal pin at 424-425 (`listOf("§7.4.1.4.1",
  "§8.9.6.1.8", "§4.65")`).
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/ObstructionContinueApproachSpec.kt`
  — KDoc at 74-75 + string-literal absence pin at 924-925 (`"§4.65" !in
  regs`).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionTest.kt`
  — KDoc at 239 + message strings at 673 / 732.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotTrainedGoAroundTest.kt`
  — KDoc at 105 / 166 / 168.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionContinueApproachTest.kt`
  — KDoc at 205 + comment at 774 + KDoc / message strings at 789 + check
  at 797-798.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveCrosswindTest.kt`
  — KDoc at 193.

### MODIFY (Branch A — wiki + agents + strategy + active flow spec)

- `wiki/domain/aviation-world.md` — grep first; update current-doctrine
  prose only.
- `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md`
  — current-doctrine prose at 170 / 186-187 / 229-230.
- `wiki/design-decisions/2026-04-15-controller-architecture.md` — grep
  for §4.6x mentions; update current-doctrine prose only.
- `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md`
  — line 96 (`Does NOT cite CAP413_4_65`).
- `STRATEGY.md` — grep at task time.
- `AGENTS.md` — lines 217 / 270 / 325 / 391.
- `.flow/specs/fn-14-g3a-react-pilot-reactive-go-around-on.md` —
  in-place inline-update §4.66 / §4.67 mentions at line 22 / 154 / 356 /
  404. **Active-epic spec** per Decision #5; inline rewrite allowed.

### MODIFY (Branch A only — closed-epic spec errata footers; Branch B/C not touched)

- `.flow/specs/fn-11-g3a-single-aircraft-pilot-trained-vfr.md` — append
  `## Errata` section per epic Decision #5 wording.
- `.flow/specs/fn-12-g3a-obstruction-single-aircraft-atc.md` — append
  `## Errata`.
- `.flow/specs/fn-13-g3a-obstruction-continue-approach-three.md` —
  append `## Errata`.
- **Branch B and Branch C**: these files are NOT touched. The
  verification artifact (Step 1) is the sole record of the verdict.

### MODIFY (all branches — edition-string correction; value coupled to branch)

- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationModel.kt`
  (or the file where `RegulationRef.companion` lives — verify via grep
  at task time) — add `const val CAP_413_EDITION = "<value>"` where
  value is **coupled to the branch verdict** (codex round-3/4/10
  critical-finding cascade — citation triple coherence):
  - **Branch A, Branch A-retire, or Branch B**: `"Edition 24
    (effective 1 July 2026)"` (all three positively verified Ed 24
    content).
  - **Branch C**: `"Edition 23 Corr (effective 21 January 2021)"` (Ed
    24 unverified).
  Mirror existing `ICAO_4444_EDITION` / `SERA_EDITION` /
  `ICAO_9432_EDITION` declarations.
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt`
  — every CAP413_* entry's `edition = "27th ed. (2023)"` literal
  replaced **per the universal hard gate** (codex round-21 finding
  #3 — not unconditional): `edition = RegulationRef.CAP_413_EDITION`
  when the Table 2 row is `UNCHANGED` / `RENUMBERED-updated` /
  `REFINED-updated`; inline `edition = "Edition 23 Corr (effective
  21 January 2021)"` literal with KDoc exception otherwise
  (`RETIRED` / `(REFINED, updatedInTask=false)` / `UNREVIEWED`). **Sites
  verified by planning grep**: lines 362 (CAP413_2_7), 371
  (CAP413_4_46), 380 (CAP413_4_49), 387 (CAP413_4_51), 405
  (CAP413_4_55), 426 (CAP413_4_53), 443 (CAP413_4_56), 475
  (CAP413_4_65 — symbol renamed in Branch A; inline-literal
  exception in Branch A-retire).

### NEW (all branches — verification artifact)

- `wiki/data-sources/cap413-edition-24-capture.md` — new file. Contents
  per Step 1 below.

## Approach (numbered Steps)

### Step 0 — Preflight (codex round-12/13/15 findings)

**Commit workflow** (codex round-15/18/20/26 finding cascade —
unified contract; commits required when run via `flow-next:work`):
- fn-17.1 implementation **requires the primary implementation
  commit** when executed via `flow-next:work` (the documented
  flow-next execution mode; codex round-26 finding #2). Citation
  hygiene work that doesn't commit ships nothing. The `flow-next:
  work` skill explicitly authorises commits. **If run outside
  `flow-next:work`** (e.g., a direct review/exploration turn that
  is NOT flow-next-work mode): R10b commits move out of scope —
  the user/pilot finalises the task via their own commit workflow.
  The acceptance criteria in this spec assume `flow-next:work`
  execution mode.
- The follow-up metadata via `flowctl done` (single canonical
  mechanism — NOT `flowctl task set-spec`) is also required to
  complete R10 acceptance fully, but failure to land it
  post-primary-commit leaves the task in a recoverable state (the
  primary commit + working-tree state is sufficient evidence of
  R10a; R10b is the bookkeeping completion).

Verify the `:sim` Gradle module exists at task start. Run `./gradlew
projects` and confirm `:sim` is listed (planning verified per
`settings.gradle.kts:include(":sim")`). Record the output excerpt in
`## Evidence`. **If `:sim` is absent** (codex round-18 finding #4 —
fail-loud, no silent substitution): stop fn-17.1 work, surface as an
unrelated build-break issue, and resolve `:sim` recovery before
resuming. R11's command does not have a substitution path; per
`feedback_no_corners.md`, no silent module-name substitution.

Additionally verify `flowctl done --help` exits 0 (codex round-17
finding #2 — `flowctl done` is the canonical command for `## Done
summary` + `## Evidence` updates in Step 9; planning verified at
v0.41.1). If `flowctl done` is unavailable, fall back to direct
Edit on `.flow/tasks/fn-17-cap-413-edition-24-numbering.1.md`;
record the substitution in `## Evidence`.

### Step 1 — Primary-source verification (load-bearing gate)

1. Pull CAP 413 Ed 24 PDF from the CAA. **Exhaust ALL four CAA
   discovery paths below before falling through to Branch C** (codex
   round-10/11/13 finding cascade — Branch C is the absence-of-
   evidence verdict, not the first-failure verdict). Try, in order,
   and record each attempt + outcome in the artifact's § Attempted
   Ed 24 sources:
   1. Path 1: `https://www.caa.co.uk/publication/download/27609`
   2. Path 2: `https://www.caa.co.uk/our-work/publications/documents/content/cap-413/`
      — follow the "Download" link (often a different URL from
      `/publication/download/27609` at any given time).
   3. Path 3: `https://www.caa.co.uk/CAP413` (short-link redirect).
   4. Path 4 (codex round-23 finding #2 — operationally precise):
      if a web-search tool is available to the implementer, run
      `WebSearch "CAP 413 Edition 24 site:caa.co.uk"`. Record the
      search query AND the chosen CAA-hosted result URL in the
      artifact. Then fetch that URL as a regular HTTP request and
      record metadata (final URL, status, Content-Type, edition
      string, SHA-256 if PDF) per Step 1.1's recording shape. If
      no web-search tool is available, record "Path 4: no web-
      search tool available in this environment" and continue
      with Paths 1-3 only — single-path-4 unavailability does NOT
      relax the "exhaust all CAA paths" rule because Paths 1-3
      provide independent direct-fetch coverage.

   **CAA-hosted only for Branch A/A-retire/B selection** (codex
   round-12/26 finding cascade — primary-source gate; non-CAA
   mirrors like SKYbrary or AOPA may be stale or unauthenticated
   and cannot satisfy the verification gate). **Non-CAA Ed 24
   candidates discovered during Step 1 MUST be recorded** under §
   Attempted Ed 24 sources / non-CAA mirrors with explicit note
   "not accepted for branch selection because not CAA-hosted"
   (codex round-26 finding #3 — preserves honest evidence trail
   without weakening the primary-source gate). They never select
   Branch A / A-retire / B. If all four CAA paths above fail or
   serve Ed 23, **select Branch C** — even if a non-CAA mirror PDF
   purports to be Ed 24.

   Branch C selection requires **all four CAA paths attempted, each
   recorded as** (codex round-15 finding #6 — per-attempt precision):
   final URL after redirects, HTTP status code, `Content-Type`
   header, edition string if the response is extractable
   (`pdftotext` on a PDF payload; HTML title/heading on an HTML
   response), **SHA-256 only for PDF payloads** (HTML / error
   responses recorded as `not hashed: <reason>`). Single-path failure
   is not sufficient — all four paths must be recorded.
2. Verify the PDF is **Edition 24** (not Ed 23). Check:
   - The masthead / title page says "Edition 24" (not "Edition 23"
     or "Edition 23 Corr").
   - The effective date reads "1 July 2026" (or whatever Ed 24's exact
     effective-date masthead reads at task time).
   - Compute SHA-256 of the captured PDF: `sha256sum cap413-ed24.pdf`.
   - **If a single discovery path returns Ed 23**: do NOT select
     Branch C yet — continue exhausting the remaining paths from Step
     1.1 (codex round-11 finding: Step 1.1 / Step 1.2 must agree on
     "exhaust ALL paths before Branch C"). **Select Branch C only
     after** all four CAA discovery paths (Paths 1-4) in Step 1.1
     have been attempted — where "attempted" for Path 4 means
     either a completed CAA-hosted web search OR an explicit
     "web-search tool unavailable in this environment" record in
     the artifact (codex round-23/24 finding cascade) — AND none
     returned an Ed 24 PDF AND every retrievable CAA source served
     Ed 23 or a 4xx/5xx error. **Branch C step
     ordering** (codex round-22 finding #1 — Step 6b is all-branch
     and MUST fire even though it sits in the 2-7 numbering range):
     skip Branch-A-only Steps 2, 3, 4, 5, 7 (rename + prose sweep);
     STILL run Step 6b (fn-14 line-404 closure annotation —
     all-branch); proceed with Step 6b → Step 8 (edition-string
     correction) → Step 9 (`.plan` update + deferments) → Step 10
     (full verify). All-branch steps (0, 1, 6b, 8, 9, 10) always
     run.
3. Run `pdftotext -layout cap413-ed24.pdf cap413-ed24.txt`. Grep
   `grep -nE '^[[:space:]]*4\.(46|49|51|53|55|56|65|66|67|68)|^[[:space:]]*2\.7[[:space:]]'
   cap413-ed24.txt` (corrected ERE per codex round-1 finding).
   **Fallback if `pdftotext` fragility loses headings** (codex round-3
   finding #5): grep cap413-ed24.txt for `missed approach`,
   `going around`, `continue approach`, plus the table of contents
   block to locate sections by topic. If extraction requires manual
   confirmation of section boundaries, record that in the artifact's
   § Extraction notes subsection.
4. Write `wiki/data-sources/cap413-edition-24-capture.md`. **Two
   distinct templates depending on the branch selected at Step 1.2**
   (codex round-5 finding #3):

   **Branch A / A-retire / B template** (Ed 24 PDF retrievable;
   codex round-22/27 finding cascade — A-retire uses the same
   artifact structure as A/B since Ed 24 is positively verified in
   all three): licence-bounded per epic Decision #1; paraphrase by
   default; verbatim excerpts capped at **≤ 25 words per excerpt**
   when needed for disambiguation; do NOT commit paragraph-length
   verbatim manual text; do NOT assume any specific licence — verify
   the CAP 413 PDF's reproduction notice at task time:
   - **§ Source**: download URL + capture date.
   - **§ Ed 24 PDF metadata**: SHA-256 (full hex), edition masthead
     string, effective-date masthead string, publication date.
   - **§ Ed 23 comparison source** (codex round-12 finding #2 —
     primary-source comparison required; codebase strings are not
     authoritative since they're being corrected): URL + SHA-256 of
     Ed 23 / Ed 23 Corr PDF used for `UNCHANGED` / `REFINED`
     classification. The planning-pulled Ed 23 PDF (planning-time
     capture from `caa.co.uk/publication/download/27609` returning
     Edition 23 effective 2021-01-21,
     SHA `f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21
     e746e7`) is the default; record the SHA-256 locally. **If Ed
     23 PDF cannot be captured at task time AND planning-time SHA
     cannot be referenced** (rare double-failure; codex round-24
     finding #1 — task halts, no "UNREVIEWED-rows-continue"
     fallback because it would break the citation-triple gate):
     file `D-PASS-cap413-edition-23-comparison-unavailable` and
     stop fn-17.1 pending Ed 23 source recovery. Do NOT use the
     codebase's existing `RegulationDatabase.kt` strings as a proxy
     for content comparison. The `UNREVIEWED` classification only
     applies on a **per-row** basis when comparing specific
     sections (e.g. one section in the Ed 23 PDF has a damaged
     page), NOT as a global fallback when the Ed 23 PDF as a whole
     is unavailable.
   - **§ Section captures — two tables** (codex round-7/10/17
     findings):

     **Table 1: §4.65-§4.68 renumbering map** — focal sections of
     the renumbering hypothesis. Each row: section number, title,
     **≤ 25-word identifying excerpt**, classification (codex
     round-27 finding #1 — explicit enum aligned with task R9):
     uses the canonical two-field shape (codex round-29/30 finding —
     `classification` + `updatedInTask`): `classification ∈
     {UNCHANGED, RENUMBERED, REFINED, RETIRED, UNREVIEWED}` (RETIRED
     for Branch A-retire's focal §4.65 row); `updatedInTask ∈ {true,
     false}` indicates whether fn-17.1 updated the corresponding
     `RegulationDatabase.kt` entry in-task. The "mapping table" referenced by R3/R5/R6.
     **Table 2: CAP413 typed-entry audit** — covers **every existing
     `CAP413_*` `val` in `RegulationDatabase.kt`** (codex round-16/17
     finding — the typed-entry audit is the load-bearing table for
     R9's hard gate; broader than focal renumbering). Each row
     carries (codex round-20 finding #3 — content-basis-per-row for
     auditability):
     - symbol name
     - current §-number, current title (as in `RegulationDatabase.kt`)
     - Ed 24 §-number (post-renumbering verdict)
     - **content-review verdict** classification: `UNCHANGED`
       (principle remains valid against Ed 24 content) / `REFINED`
       (Ed 24 shifts meaning; principle must be rewritten) /
       `RETIRED` (Ed 24 drops section; deferment filed) /
       `UNREVIEWED` (Ed 23 comparison source unavailable)
     - **page/line locator in Ed 24 PDF** (where the verifier read
       the section content; e.g. "Ch 4 page 26 around line 5055").
     - **basis-of-check paraphrase** (codex round-20 finding #3 —
       short content basis, not just a locator): one-line paraphrase
       of what was checked, e.g. "Ed 24 §4.65 still describes ATC
       'GO AROUND I SAY AGAIN' phraseology — matches current
       principle." Required for ALL rows including UNCHANGED, so the
       audit is independently meaningful without re-reading the PDF.
     - ≤ 1-sentence verbatim excerpt — only for REFINED / RETIRED
       rows (paraphrase plus excerpt for those; UNCHANGED rows use
       paraphrase only, minimising Crown-copyright capture).
     **Audit completeness**: `grep -n "val CAP413_"
     protocol/.../RegulationDatabase.kt` (planning result: CAP413_2_7,
     CAP413_4_46, CAP413_4_49, CAP413_4_51, CAP413_4_53, CAP413_4_55,
     CAP413_4_56, CAP413_4_65) — Table 2 must cover every `val`
     listed.
   - **§ Mapping table**: explicit old-Ed-23 §-number → new-Ed-24
     §-number for §4.65 / §4.66 / §4.67 / §4.68 (drives R3/R5/R6).
   - **§ Retained historical cites** (new — codex round-2/5
     findings): file path + line + cite text + reason for any cite
     intentionally left as Ed 23 numbering. **Prefer artifact
     recording over inline comments for Markdown narrative files** to
     avoid polluting prose (codex round-5 finding #7); inline
     comments OK in code / KDoc.
   - **§ Hypothesis**: "fn-14 docs-scout claimed Ed 24 renumbers §4.66
     → §4.65 (VFR-continue) and §4.67 → §4.66 (pilot-initiated GA)."
   - **§ Verdict** (one of; codex round-29 finding #2 — A-retire
     explicit):
     - **Branch A — Confirmed**: §4.65 in Ed 24 is what §4.66 was in
       Ed 23; §4.66 in Ed 24 is what §4.67 was in Ed 23. Note the
       new location of Ed 23's §4.65 content (ATC-initiated GA) in
       Ed 24.
     - **Branch A-retire — ATC-initiated GA retired**: Ed 24
       retires the §4.65 ATC-initiated-GA phraseology section
       entirely. Record: which Ed 24 section (if any) inherits the
       phraseology; required deferment ID
       `D-PASS-cap413-edition-24-retired-atc-ga-phraseology`;
       `CAP413_4_65` keeps Ed 23 inline literal exception per R9
       hard gate.
     - **Branch B — Refuted**: §4.65 / §4.66 / §4.67 / §4.68 in
       Ed 24 say the same things as Ed 23. Codebase numbering is
       correct. R3-R8 collapse to no-op; R9+R10+R11+R12 fire.
   - **§ Local extraction procedure**: one-liner
     `pdftotext -layout cap413-ed24.pdf - | grep -nE '<...>'` so
     future reviewers can re-derive section content against the
     captured SHA without re-downloading or redistributing.

   **Branch C template** (Ed 24 PDF unavailable):
   - **§ Attempted Ed 24 sources**: every URL tried + observed
     returned edition masthead at each + outcome (e.g. "endpoint
     `download/27609` returned Ed 23 effective 2021-01-21").
   - **§ Ed 23 PDF as active citation source** (codex round-4/18/25
     finding cascade — Branch C pins `CAP_413_EDITION = Ed 23 Corr`
     so the Ed 23 PDF is the active doctrine source): URL + SHA-256
     of the Ed 23 PDF served + masthead string + effective-date
     string + capture date. **If no Ed 23 PDF is retrievable from
     any CAA discovery path either** (rare double-failure — both
     Ed 24 AND Ed 23 unavailable at task time): fall back to the
     **planning-time Ed 23 PDF capture** documented in the epic's
     § Captured research section (SHA
     `f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21
     e746e7`). Use that SHA-256 as the citation source for Branch
     C's edition string — Branch C proceeds with R9 edition-string
     correction (codex round-25 finding #2 — the planning-time SHA
     IS the active citation source; Branch C does NOT halt; it
     proceeds with the planning-time SHA AND files
     `D-PASS-cap413-edition-23-pdf-unreachable-at-task-time`
     deferment).
   - **§ Hypothesis**: as above.
   - **§ Verdict — Branch C — Unverifiable**: Ed 24 PDF unavailable /
     serves Ed 23 at task time. Re-file rename as
     `D-PASS-cap413-edition-24-rename-pending-pdf`. §-numbers stay Ed
     23; only edition-metadata correction ships.
   - **§ Branch-C disposition**: `section` fields in
     `RegulationDatabase.kt` left unchanged (Ed 23 numbering retained).
     Only `edition` metadata is touched in this branch.
   - **§ Local extraction procedure**: (codex round-28 finding #4 —
     local extraction requires a local PDF, not just a SHA): if the
     Branch C citation source is a fresh-pull Ed 23 PDF, document
     `pdftotext -layout <local.pdf> | grep ...`. **If the citation
     source is the planning-time SHA only (no local Ed 23 PDF
     available)**: state explicitly "no local extraction possible;
     planning-time captured research at the epic's § Captured
     research section is the source of section content; this Branch
     C artifact references that planning capture by SHA". Do NOT
     fabricate a local-extraction command against a missing file.

   Both templates: do NOT commit any captured PDF. The artifact records
   URL + SHA + extraction procedure only (per CAP 413 PDF
   reproduction terms verified at task time; Crown copyright
   redistribution out of scope).
5. **Create / update** the verification artifact in the working tree.
   **Do NOT make a separate commit at this step** (codex round-2
   finding #2). The artifact is committed alongside the rest of the
   task's changes in a single final commit at the end of Step 10. The
   final commit message includes the PDF SHA-256 and the branch
   verdict. Do NOT commit the captured PDF (Crown copyright; CAP 413
   PDF reproduction terms — commit URL + SHA + extraction procedure
   only).

### Step 2 (Branch A only) — Rename `CAP413_4_65` only if Ed 24 moves it

**Narrow scope** per epic Decision #2 + codex round-3 finding #1: the
**only typed `CAP413_4_<n>` symbol** for §-numbers in {4.65, 4.66, 4.67,
4.68} is `CAP413_4_65`. `CAP413_4_66`, `CAP413_4_67`, `CAP413_4_68` do
**NOT** exist as typed entries — they appear only in prose KDoc + test
message strings (handled in Step 4 / Step 6).

**Action depends on the R1 mapping table verdict for the "ATC-initiated
missed approach phraseology" topic** (codex round-6/24 finding cascade
— drop "very likely" framing; mapping is the source of truth; A-retire
explicitly updates `CAP413_4_65`'s KDoc even though R3 is no-op):

Look up the row in R1's mapping table where `old Ed 23 §-number = 4.65
(ATC-initiated GA phraseology)`. The right-hand column dictates the
action:

- **Row says new §-number ≠ 4.65** (e.g. Ed 24 moves the topic to
  §4.67 or wherever): **rename** `val CAP413_4_65` → `val
  CAP413_4_<new>`. Update the `section = "§..."` field to the new
  §-number. Principle / title stay the same (ATC-initiated GA topic
  unchanged). Every consumer `import` + reference updates in Step 3.
- **Row says new §-number == 4.65** (Ed 24 keeps ATC-initiated GA at
  §4.65; the renumbering of old §4.66/§4.67 shifts surrounding
  sections but not §4.65 itself): no `val` rename. The `CAP413_4_65`
  symbol stays. Principle string + title refreshed only if Ed 24's
  wording materially differs from Ed 23 (Decision #3).
- **Row says the topic is retired** (Ed 24 drops verbatim ATC-
  initiated-GA phraseology entirely — improbable; verify thoroughly
  via the artifact's Ed 24 captures): **SELECT BRANCH A-RETIRE**
  (codex round-7/10/24 finding cascade). Do NOT retire the `val` in
  this task. Doctrine-anchor removal is no longer mechanical citation
  cleanup — it has substantive impact on the obstruction-GA
  regulation list and the controller's `DecisionTrace` payload, which
  is downstream test-visible. **In Branch A-retire**: explicitly
  update `CAP413_4_65`'s KDoc to mark it as an intentional Ed 23
  bridge citing `D-PASS-cap413-edition-24-retired-atc-ga-
  phraseology` (codex round-24 finding #6 — KDoc update needed even
  though R3 is no-op for the rename action). **Branch A-retire
  deliverables** (per epic Decision #1 A-retire sub-branch):
    - R1: full verification artifact (Branch A/A-retire/B template).
    - R2: verdict line names "Branch A-retire: doctrine-anchor
      removal deferred".
    - R3, R4, R5, R6, R8, R12: no-op, documented in artifact verdict.
    - R7: one-line Branch-B/C-arm closure annotation on fn-14:404.
    - R9: edition-string correction with `CAP413_4_65` keeping
      inline Ed 23 literal (universal hard gate; constant value
      otherwise `"Edition 24 (effective 1 July 2026)"`).
    - R10: `.plan` closure + new deferment
      `D-PASS-cap413-edition-24-retired-atc-ga-phraseology` filed
      with the Ed 24 evidence.
    - R11: full verify GREEN.
    - **R13** (codex round-28 finding #1 — MANDATORY in A-retire,
      not just listed in the gating table): Step 4a narrow §4.65 /
      CAP413_4_65 prose audit runs before Step 8/9/10.
  Doctrine-anchor removal is a separate future epic with its own
  review surface.

**Citation-triple coherence** (critical — codex round-3/4/6 cascade):
the `(document, edition, section)` triple stays coherent throughout.
Renaming `CAP413_4_65` from §4.65 (ATC-initiated GA) to §4.65 (Ed 24
VFR-continue topic, if that's what the mapping table claims) without
also relabelling the principle would silently corrupt the
obstruction-GA regulation list. **The `principle` and `title` follow
the topic, not the §-number**; the §-number is the address, the
content is the doctrine. The rename above renames the address while
keeping the doctrine intact.

**Symbols that do NOT exist** (no rename action — they were never
typed):
- `CAP413_4_66` — VFR-continue. Prose only.
- `CAP413_4_67` — pilot-initiated GOING AROUND. Prose only.
- `CAP413_4_68` — military missed approach reference. Prose only.

**This epic does NOT add new typed entries.** Step 4's prose sweep
updates §4.66 / §4.67 / §4.68 KDoc citations to their new Ed 24
§-numbers; no `val` declarations are introduced.

**Edition #1 quirk**: if the hypothesis is wrong in detail (e.g. Ed 24
renumbers §4.66 → §4.64 instead of §4.65), follow Ed 24's actual
numbering; don't force the hypothesis. The hypothesis is a starting
heuristic; Step 1 is the source of truth.

### Step 3 (Branch A only — if Step 2 renamed `CAP413_4_65`) — Compiler-driven import sweep

If Step 2 renamed the typed `CAP413_4_65` symbol: update every consumer's
`import` line + every call-site reference to the new symbol. The planning
grep enumerates the call sites under `## Files`:
- `controller/.../TowerArrival.kt:83` (import) + `:267` + `:298` (call
  sites).
- `controller/.../Controller.kt:931` (call site).
- All KDoc cross-references update via prose sweep in Step 4.

If Step 2 left `CAP413_4_65` unchanged (Ed 24 keeps ATC-initiated GA at
§4.65): no import-sweep needed; Step 3 is a no-op.

**Step 10 (R11) is the sole authoritative verification gate** (codex
round-1 + round-4 findings). Do not invoke per-module compile targets
mid-task; rely on Step 10 to catch any unresolved-reference or
type-error fallout.

### Step 4 (Branch A only) — Prose update grep walk

Repo-wide grep (codex round-3/22 finding cascade — broader than
initial draft scope; **exclude fn-17 own artifacts** to avoid
self-rewriting the implementation plan):

```
rg -n '§4\.6[5-8]|CAP413_4_6[5-8]' \
   --glob '!**/build/**' \
   --glob '!**/node_modules/**' \
   --glob '!**/.gradle/**' \
   --glob '!.flow/specs/fn-17-cap-413-edition-24-numbering.md' \
   --glob '!.flow/tasks/fn-17-cap-413-edition-24-numbering.1.*' \
   --glob '!wiki/data-sources/cap413-edition-24-capture.md'
```

The fn-17 own artifacts (epic spec, task spec, verification artifact)
are exempt from stale-cite cleanup because they ARE the verification
record (codex round-22/25 finding cascade); their §4.6x / CAP413_4_6x
references are classified collectively in R1's verification artifact
(Tables 1+2) once, NOT rewritten line-by-line.

Covers `docs/`, `core/`, `migration/` (if present), `.flow/tasks/`,
`research/` (if any code-doc surfaces match), in addition to the planning
grep's `protocol pilot controller sim wiki AGENTS.md STRATEGY.md
.flow/specs`.

**Record the initial grep output in `## Evidence`** (codex round-12
finding #6 — auditability of the cleanup). Walk every match (not just
§4.66/§4.67) — codex round-2 finding #3. For each match:

1. Classify against R1's mapping table: which Ed 23 §-number meaning
   does the cite intend? (e.g. "ATC-initiated GA" → Ed 23 §4.65;
   "VFR-continue" → Ed 23 §4.66; "pilot-initiated GA" → Ed 23 §4.67;
   "Military missed approach" → Ed 23 §4.68.)
2. Per mapping, update to the new Ed 24 §-number. **Every classified
   match touched** — silent omissions fail R5.
3. For historical-record paragraphs intentionally retaining the old
   numbering: **explicit classification required** (epic Decision #4,
   codex round-2 finding #4):
   - If the file format supports comments: add the marker
     `<!-- intentional Ed 23 cite: historical record (reason) -->`.
   - Otherwise: record file path + line + cite text + reason in the
     verification artifact's new § Retained historical cites
     subsection. NO silent "left alone".

- **Code KDoc / Test KDoc / Test message string**: update §-number
  in-place per the mapping.
- **Wiki / design-decisions / AGENTS.md / STRATEGY.md prose**: update
  current-doctrine descriptive paragraphs; explicitly classify any
  intentionally-retained historical paragraphs.
- **`.flow/specs/fn-14-*.md`** (active-epic): inline update lines 22 /
  154 / 356 / 404 per the mapping.
- **`.flow/specs/fn-11-*.md`, `fn-12-*.md`, `fn-13-*.md`** (closed-epic,
  Branch A only): do NOT in-place rewrite. Append errata footer per
  Step 5.

**Post-edit grep audit** (codex round-12/18 finding cascade —
auditability with explicit-classification shape): after all Step 4 /
Step 5 / Step 6 edits land, re-run the repo-wide grep. **Expected
result**: every remaining hit must be accounted for in one of:
(a) the verification artifact's § Retained historical cites
(file+line+reason listed), OR (b) one of the three closed-epic
errata footers (fn-11/12/13). Record the post-edit grep output AND
the per-hit classification table in `## Evidence`. **Acceptance
shape** (not "zero hits"): "all remaining `§4.6[5-8]` /
`CAP413_4_6[5-8]` hits classified under Retained historical cites
or Errata; no unclassified current-doctrine stale cites." If any
hit is unclassified, Step 4 is incomplete — return and finish.

### Step 4a (Branch A-retire only — codex round-17 finding #3) — Narrow §4.65 prose audit

Branch A-retire keeps `CAP413_4_65` and its associated current-
doctrine KDoc/prose as Ed-23 bridge content (until the doctrine-
anchor-removal epic ships). But every active KDoc / wiki / spec
paragraph that describes "CAP 413 §4.65: ATC-initiated missed
approach phraseology" is now describing **retired Ed 24 content**.
Run `rg -n '§4\.65|CAP413_4_65' protocol pilot controller sim wiki
AGENTS.md STRATEGY.md .flow/specs --glob '!**/build/**'
--glob '!.flow/specs/fn-17-cap-413-edition-24-numbering.md'
--glob '!.flow/tasks/fn-17-cap-413-edition-24-numbering.1.*'
--glob '!wiki/data-sources/cap413-edition-24-capture.md'
--glob '!.flow/specs/fn-11-*.md'
--glob '!.flow/specs/fn-12-*.md'
--glob '!.flow/specs/fn-13-*.md'`
(codex round-22/23/29 finding cascade — exclude fn-17 own
artifacts, the capture artifact, AND closed-epic flow specs which
must remain historical and are only touched via errata footers in
Branch A; never in Branch A-retire's R13 audit). For each active
occurrence (excluding intentional historical-record
paragraphs):
- Annotate the cite to make the Ed-23-bridge status explicit:
  e.g., add ` (Ed 23 anchor retained pending
  D-PASS-cap413-edition-24-retired-atc-ga-phraseology)` to the
  existing cite text.
- Inline edit OR record in the verification artifact's § Retained
  historical cites with reason "Ed 23 anchor retained as bridge
  during A-retire branch; pending doctrine-anchor-removal epic".
- This is **NOT** the full R5 prose sweep (R5 is no-op in A-retire);
  it's a **narrow audit limited to §4.65 / CAP413_4_65 cites only**
  to prevent silently misleading current-doctrine documentation
  after Ed 24 retires the topic.

Branch A / Branch B / Branch C: this step is a no-op (Branch A
rewrites all §4.6x cites in R5; Branch B/C don't touch §4.65 since
Ed 24 either preserves it or is unverified).

### Step 4b (Branch B only — codex round-9 finding #1) — Stale-hypothesis prose grep

Run `rg -n 'docs-scout caught Edition 24 renumbered|Ed 24 renumbered
§4\.6|D-PASS-cap413-edition-24-reconciliation' .flow/specs/ wiki/
AGENTS.md STRATEGY.md
--glob '!.flow/specs/fn-17-cap-413-edition-24-numbering.md'
--glob '!.flow/tasks/fn-17-cap-413-edition-24-numbering.1.*'
--glob '!wiki/data-sources/cap413-edition-24-capture.md'`
(codex round-20/22/23 finding cascade — scope aligned with R12;
exclude fn-17 own artifacts AND the capture artifact). For each
active occurrence outside fn-14:
- Edit the prose to reflect Branch-B's verified verdict (Ed 24 retains
  Ed 23 §-numbering; the hypothesis was wrong). One-line edits.
- OR record in the verification artifact's § Retained historical cites
  why the cite should remain unchanged (rare; default is edit since
  Branch B has positively refuted the hypothesis).
- Closed-epic flow specs (fn-11/12/13) NOT touched (per planning grep
  they don't carry the docs-scout phrasing).

**Branch A**: this step is folded into R5/R7's full prose sweep (Branch
A rewrites hypothesis prose to the verified Branch-A verdict).

**Branch C**: this step is a no-op (Branch C cannot verify the
hypothesis; stale prose stays as historical record, captured in the
verification artifact's § Retained historical cites with a Branch-C
note).

### Step 5 (Branch A only) — Errata footers on closed flow specs

Append to each of `fn-11`, `fn-12`, `fn-13` spec files:

```
## Errata
- 2026-05-11 (fn-17): CAP 413 §-cites in this spec were authored
  against the then-current Edition 23 numbering. Per fn-17.1's
  primary-source verification (artifact:
  `wiki/data-sources/cap413-edition-24-capture.md`), Ed 24
  (effective 2026-07-01) maps as follows: <exact old §-number →
  new §-number list from Table 1 of the verification artifact,
  e.g. "§4.66 (VFR-continue) → §4.65; §4.67 (pilot-initiated GA)
  → §4.66; §4.65 (ATC-initiated GA) → §4.67">. Current-doctrine
  citations live in `protocol/.../RegulationDatabase.kt`; this
  spec's prose is preserved as-is for historical fidelity.
```

Use the **exact mapping** from R1 Table 1 (codex round-18/20
findings — exact mappings, not generic "§4.6x renumbers"
placeholder).

Each spec gets one tailored entry; the list of renumberings reflects the
exact verdict. Do not edit any other line in these files.

**Branch B and Branch C**: this step is skipped entirely — closed-epic
spec files are not touched.

### Step 6 (Branch A only) — Update test string-literal pins

Per `## Files / MODIFY (Branch A — test strings)`:
- `ObstructionGoAroundSpec.kt:424` — change `listOf("§7.4.1.4.1",
  "§8.9.6.1.8", "§4.65")` to the new §-number for ATC-initiated GA per
  Step 1.
- `ObstructionContinueApproachSpec.kt:924-925` — change `"§4.65"` to the
  new §-number that contains ATC-initiated GA per Step 1.
- `G3aRunwayObstructionContinueApproachTest.kt:797` — change
  `RegulationDatabase.CAP413_4_65` reference to the new symbol per
  Step 2 rename. Note this is **typed-reference equality** so it
  auto-tracks Step 2; no string-literal change needed.

### Step 6b (all branches — codex round-21 finding #1) — fn-14 line-404 closure annotation

**Unconditional, all-branch step**: apply the one-line closure
annotation to `.flow/specs/fn-14-g3a-react-pilot-reactive-go-around-
on.md` line 404 (deferment-register entry) per epic Decision #5
branch-specific wording. This step runs in all four branches
(A / A-retire / B / C); R7 then layers branch-specific additional
edits on top:
- Branch A: Step 7 below adds inline updates at lines 22/154/356.
- Branch B: R12's grep walk in Step 4b may add inline edits at
  lines 22/154/356 if hypothesis prose is present at those lines.
- Branch A-retire / Branch C: line-404 annotation only; no other
  fn-14 edits.

### Step 7 (Branch A only) — Update fn-14 spec inline per verified mapping

Edit `.flow/specs/fn-14-g3a-react-pilot-reactive-go-around-on.md` per
the R1 verified mapping (codex round-5 finding #4 — replace the
hypothesis prose with the verified outcome, do NOT leave stale
"docs-scout claimed" hypothesis in an active spec):
- **Line 22** — currently reads `Out: CAP 413 Edition 24 §4.65/§4.66
  renumbering. docs-scout caught Edition 24 renumbered §4.66→§4.65
  (VFR-continue) and §4.67→§4.66 (pilot-initiated GA)...`. **Replace
  the hypothesis prose with the verified outcome**: `Out: CAP 413
  Edition 24 §-renumbering (reconciled by fn-17, 2026-05-11): <exact
  renumberings per R1 mapping table>.`
- **Line 154** — `per CAP 413 §4.67 / ICAO Doc 4444 §12.3.4.18` —
  update §4.67 to the new §-number per Step 1 mapping.
- **Line 356** — `**CAP 413 §4.66** / §4.67 (existing fn-11/fn-12
  cite; verify against Edition 23.1 vs 24 numbering at task time —
  see `D-PASS-cap413-edition-24-reconciliation`)` — replace with the
  verified Ed 24 §-numbers + closure pointer `(reconciled by fn-17)`.
- **Line 404** — `**D-PASS-cap413-edition-24-reconciliation** —
  Edition 24 renumbered §4.66→§4.65...` — mark CLOSED with fn-17
  epic ID + commit-SHA placeholder + the verified renumberings.

All four edits replace stale hypothesis with verified verdict; no
"keep as historical context" of unverified prose in active specs.

### Step 8 (all branches) — Edition-string correction (citation-triple coherent)

**Citation-triple invariant** (codex round-3 critical finding): the
`(document, edition, section)` triple must be coherent. The
`CAP_413_EDITION` constant value is **coupled to the branch verdict**:

- **Branch A** (Ed 24 renumbering confirmed, rename applied):
  `CAP_413_EDITION = "Edition 24 (effective 1 July 2026)"`. The
  codebase cites Ed 24 doctrine post-merge. Acceptable per
  `feedback_reality_anchored.md` — doctrine moves; cite what's
  next-current.
- **Branch B** (Ed 24 retains Ed 23 numbering, verified):
  `CAP_413_EDITION = "Edition 24 (effective 1 July 2026)"`. §-numbers
  unchanged (Ed 24 == Ed 23 numbering); metadata Ed 24.
- **Branch C** (Ed 24 PDF unavailable):
  `CAP_413_EDITION = "Edition 23 Corr (effective 21 January 2021)"`.
  §-numbers unchanged. Metadata Ed 23. File
  `D-PASS-cap413-edition-24-rename-pending-pdf` for revisit.

**NO Ed 24 numbering with Ed 23 metadata** (codex round-3 critical). The
previous draft proposed pinning to Ed 23 metadata even in Branch A;
that's rejected — it produces invalid citation triples.

**NO TODO comment in source**. The Ed-23-Corr → Ed-24 effective-date
switch (Branch C only) is tracked solely as
`D-PASS-cap413-edition-24-rename-pending-pdf` (epic Decision #7 +
Deferments register).

Implementation:

1. Add to `RegulationRef` companion object (in `RegulationModel.kt` —
   verify exact location at task time via `grep -n "ICAO_4444_EDITION
   =" protocol/.../*.kt`): `const val CAP_413_EDITION = "<value>"` per
   the branch table above.

2. In `RegulationDatabase.kt`, every CAP413_* entry: replace
   `edition = "27th ed. (2023)"` with `edition =
   RegulationRef.CAP_413_EDITION`. **Verified planning sites**: lines
   362, 371, 380, 387, 405, 426, 443, 475 (post-rename line numbers may
   shift if Branch A renames `CAP413_4_65`).

3. **Universal hard gate** (codex round-14/15/18 finding cascade —
   citation-triple coherence applied across all branches): in
   Branch A, A-retire, and B, an entry uses `edition =
   RegulationRef.CAP_413_EDITION` (Ed 24) **only if** its R1 Table 2
   row classifies as `UNCHANGED` / `RENUMBERED` (section updated in
   this task) / `REFINED` (principle updated in this task). For any
   other Table 2 classification — `RETIRED`, `(REFINED, updatedInTask=false)`,
   or `UNREVIEWED` — the entry keeps `edition = "Edition 23 Corr
   (effective 21 January 2021)"` as an inline literal (NOT the
   constant), with KDoc noting:
   - `RETIRED`: "Ed 24 retired this section; Ed 24 metadata would
     produce false triple. Doctrine-anchor removal deferred to
     `D-PASS-cap413-edition-24-retired-<n>`."
   - `(REFINED, updatedInTask=false)`: "Ed 24 refines this section's meaning;
     principle update deferred to `D-PASS-cap413-edition-24-
     refined-<n>` (out of fn-17 scope)."
   - `UNREVIEWED`: "Ed 23 comparison source unavailable; cannot
     confirm Ed 24 principle validity. Deferred to
     `D-PASS-cap413-edition-24-unreviewed-<n>`."
   In Branch C, this gate is vacuous (constant value is Ed 23
   already; Ed 23 numbers + Ed 23 metadata are universally
   coherent).

### Step 9 (all branches) — Deferment closure in `.plan` (canonical)

**Primary path** (codex round-4/16 findings — working-tree-blocking,
commit-optional): update repo-root `.plan` directly in the working
tree — `D-PASS-cap413-edition-24-reconciliation` marked `DONE` with:
- Branch verdict (A/A-retire/B/C)
- PDF SHA-256 from Step 1
- Reference to the fn-17 epic ID + the literal pointer string
  `"see .flow/tasks/fn-17-cap-413-edition-24-numbering.1.md ##
  Evidence"`.

**Commit workflow** (codex round-13/16 findings — explicit, commit-
optional):
**Commit workflow (required per Step 0 commit contract; codex round-
18/22 findings — single canonical mechanism `flowctl done`)**:
1. **Primary implementation commit**: contains all source/wiki/test
   edits + `.plan` closure entry (without SHA placeholder) + the
   verification artifact + task `## Evidence` populated with
   everything except the final SHA + task `## Done summary` drafted.
   Commit-message body records branch verdict + PDF SHA.
2. **Follow-up metadata via `flowctl done`**: after the primary
   commit lands, run `flowctl done
   fn-17-cap-413-edition-24-numbering.1 --summary-file <md>
   --evidence-json <json>` (canonical command; performs both the
   state transition to `done` AND patches `## Done summary` /
   `## Evidence`). The `<json>` for `--evidence-json` includes the
   primary commit SHA. Create a small follow-up commit titled e.g.
   `fn-17.1: record primary commit SHA via flowctl done`. **The
   `flowctl done` state transition is intentional — the task IS
   done at this point** (the chicken-and-egg only existed because
   `## Evidence` carries the SHA of the commit that landed it, not
   because `done` is premature).
   **Fallback** if `flowctl done` is unavailable (Step 0 preflight
   detects this): edit `.flow/tasks/fn-17-cap-413-edition-24-
   numbering.1.md` directly via Edit and commit (no state
   transition; track separately).
   `.plan` itself is NOT touched in the follow-up — `.plan` already
   points to `## Evidence` (which now resolves to the SHA).

**If commits are blocked at task time** (rare — e.g. permissions
issue mid-task): record the block in `## Evidence`, leave working-
tree state intact, and surface as a separate task-blocker issue.
R10 acceptance is NOT satisfied until commits land — per codex
round-20/21 finding: fn-17.1 is commit-required, NOT commit-
optional; "blocked at commit time" is a task-stop condition, not a
silent fallback.

Add to `.plan` any new deferments surfaced by this epic:
- `D-PASS-cap413-edition-24-rename-pending-pdf` — **Branch C only**
  (codex round-7 finding #1 — single consolidated deferment, replacing
  the previously-split `effective-date-switch` + `rename-pending-pdf`
  pair). Trigger: CAA endpoint serves Ed 24 PDF. Action: re-pull,
  verify, update numbering + edition metadata together in one commit
  for citation-triple coherence.
- `D-PASS-cap413-edition-24-<section>` — if Step 1 surfaces other Ed
  24 changes (one per section).
- `D-PASS-cap413-principle-text-deep-refresh` — if Step 1 surfaces
  semantic shifts beyond mechanical one-line summary.

**Sister register update** (codex round-21 finding #6 — explicitly
NOT part of repo commit acceptance; `.claude/` lives outside the
repo): update `~/.claude/plans/pilot-firewall.md` if reachable. This
is **off-commit bookkeeping evidence**, never part of the primary
implementation commit. `## Evidence` records "sister register
touched" / "sister register unreachable from this environment;
deferred to next workstation visit". **No silent skip** —
`## Evidence` always names what was touched.

### Step 10 — Full verify (authoritative) + eight-golden evidence collection

Preflight ran in Step 0 (codex round-13 finding #5 — preflight
sequenced before source edits). Run the full verify command:

```
./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
```

Exits 0. detekt baseline unchanged. This is the **authoritative
verification command** — no intermediate per-module compile targets
per codex round-1 finding.

**Eight-golden evidence collection** (codex round-7 finding #3 —
concrete commands, not ad-hoc inference):

After the full verify passes, run one of the following to capture
auditable evidence of the eight goldens:

```bash
# Option A (robust per-class checks — codex round-13/14/16/17/21/22/24
# finding cascade — primary detection via Gradle's default JUnit XML
# filename convention `TEST-<fqcn>.xml`, fallback to content grep;
# attribute-order-independent; script exits non-zero on any failure):
set -e
fail=0
for cls in LowgGoldenTest G1TwoAircraftCircuitsTest G1TwoAircraftMinimalSpec G2CrossAerodromeVfrTest G3aPilotTrainedGoAroundTest G3aRunwayObstructionTest G3aRunwayObstructionContinueApproachTest G3aPilotReactiveCrosswindTest; do
  # Primary: Gradle's default JUnit XML filename
  xml="sim/build/test-results/jvmTest/TEST-xyz.easiersaid.twr.sim.$cls.xml"
  if [ ! -f "$xml" ]; then
    # Fallback: content-grep across all XMLs (covers JUnit-emitter variations)
    xml=$(rg -l "<testsuite[^>]+name=\"xyz\.easiersaid\.twr\.sim\.$cls\"" sim/build/test-results/jvmTest/ 2>/dev/null | head -1 || true)
  fi
  if [ -z "$xml" ] || [ ! -f "$xml" ]; then
    echo "MISSING: $cls"; fail=1; continue
  fi
  # Verify owning <testsuite> reports failures="0" AND errors="0"
  # AND skipped="0" AND tests > 0 (codex round-22/24/28 findings —
  # positive test count required; skipped checked since project
  # explicitly forbids disabled goldens; attribute-order-independent
  # via four separate greps).
  if rg -q '<testsuite[^>]+failures="0"' "$xml" \
     && rg -q '<testsuite[^>]+errors="0"' "$xml" \
     && rg -q '<testsuite[^>]+skipped="0"' "$xml" \
     && rg -q '<testsuite[^>]+tests="[1-9][0-9]*"' "$xml"; then
    echo "OK: $cls @ $xml"
  else
    echo "FAILED: $cls @ $xml"; fail=1
  fi
done
[ $fail -eq 0 ] || { echo "Eight-golden evidence FAILED"; exit 1; }
# Combined with the prior step's `./gradlew :sim:jvmTest` exit 0, this
# proves the eight testsuites ran AND passed (Gradle exits non-zero on
# any test failure).
# Option B: re-run with --tests filter (slower; only if Option A's
# XML files are missing — that itself is anomalous, since `:sim:jvmTest`
# exiting 0 should have written results. Investigate where Gradle wrote
# reports before falling back.)
./gradlew :sim:jvmTest --tests "*LowgGoldenTest" --tests "*G1TwoAircraftCircuitsTest" --tests "*G1TwoAircraftMinimalSpec" --tests "*G2CrossAerodromeVfrTest" --tests "*G3aPilotTrainedGoAroundTest" --tests "*G3aRunwayObstructionTest" --tests "*G3aRunwayObstructionContinueApproachTest" --tests "*G3aPilotReactiveCrosswindTest"
```

Record in `## Evidence` (codex round-10/14 findings — self-
describing, not inference-required): (a) the Gradle full-verify
command's exit status `0` literally quoted, (b) the `rg -l` output
listing the eight matched testsuite XML paths, (c) a note like
"Gradle `:sim:jvmTest` exited 0 (any failing test fails the task);
the eight testsuite XMLs above confirm the eight golden classes
ran." If the XML files are missing despite Gradle exiting 0 (codex
round-13 finding #6 — anomalous; not normal fallback path):
investigate where Gradle wrote reports (perhaps
`sim/build/reports/tests/` vs `sim/build/test-results/jvmTest/`,
or a non-default reports directory). Record the investigation +
substituted command in `## Evidence`. Do NOT silently swallow
missing-XML output; record loudly.

## Investigation targets

- Verify `RegulationRef` companion-object constant location. ✅ Per
  planning grep: constants live as `RegulationRef.SERA_EDITION`,
  `RegulationRef.ICAO_4444_EDITION`, `RegulationRef.ICAO_9432_EDITION`.
  New `RegulationRef.CAP_413_EDITION` goes next to those.
- Verify which file declares the constants (`RegulationModel.kt` vs
  `RegulationDatabase.kt`). Grep at task time: `grep -n
  "ICAO_4444_EDITION =" protocol/.../*.kt`.
- Verify the CAA Ed 24 PDF URL is still
  `caa.co.uk/publication/download/27609` at task time. If it 404s or
  serves Ed 23 still, fall through to WebSearch (Branch C path).
- Confirm `RegulationDatabase.CAP413_4_65 !in companionRespond.trace
  .regulations` at `G3aRunwayObstructionContinueApproachTest.kt:797` is
  typed-reference equality, not string equality (it is — `!in` on a
  `List<RegulationRef>` invokes `RegulationRef.equals`). If Branch A's
  Step 2 rename retires the old `CAP413_4_65` and replaces with a new
  `CAP413_4_67` (or whatever), this `!in` check still works correctly
  because the symbol resolves to the new entry, but the absence-pin
  intent might shift — verify the test's intent matches Ed 24's
  ATC-initiated-GA section number.

## Key context

- **Compiler-driven rename**: Kotlin's `import xyz.easiersaid.twr.protocol.
  RegulationDatabase.CAP413_4_65` resolves to the renamed symbol after
  Step 2; rebuild surfaces unresolved-references at every consumer. No
  silent failures possible.
- **The codebase's current `"27th ed. (2023)"` edition string is wrong
  regardless of any rename**. CAP 413's edition numbering is 1..24. Step
  8 fixes this in all branches.
- **Errata footers on closed flow specs (Branch A only)** — do NOT
  in-place rewrite fn-11 / fn-12 / fn-13 specs per
  `feedback_reality_anchored.md` (the historical record stays
  unflinching). Append only, three-line footer per Step 5.
- **Branch B and Branch C do not touch closed-epic spec files** — the
  verification artifact (Step 1) is the sole record of the verdict.
- **Active-epic spec (fn-14) gets inline update in Branch A only** — fn-
  14 is recent and still in-context; inline-rewrite is acceptable
  there.
- **`G3aRunwayObstructionContinueApproachTest.kt:797`** uses typed
  reference equality. After Branch A's Step 2 rename, the symbol points
  to the new entry; the absence-pin intent should track the renamed
  ATC-initiated-GA section. **Verify intent** at Step 6: the test
  asserts "CAP 413's ATC-initiated missed-approach section is NOT in
  the CONTINUE APPROACH companion's regs list" — that intent persists
  after rename, but the specific §-number changes.
- **Branch C must still ship Step 8**. The edition-string error is
  factual, independent of any renumbering verification.
- **No new tests**. The existing suite gates this epic.
- **No TODO comment in source** for the Ed-24-effective-date switch —
  filed as deferment `D-PASS-cap413-edition-24-rename-pending-pdf`
  (per codex round-1 finding).
- **Verbatim PDF text is NOT committed** — Step 1 captures section
  titles + ≤ 1-sentence excerpts + SHA + extraction procedure (OGL v3
  fair-use bounds; Crown copyright redistribution out of scope).

## Acceptance

- [ ] **R1 (branch-aware)** — `wiki/data-sources/cap413-edition-24-
  capture.md` exists. **Branch A or B** (codex round-8 finding #2 —
  capture scope aligned with epic Decision #1 + Step 1 template):
  source URL (CAA-hosted only — codex round-12 finding #1), SHA-256
  of Ed 24 PDF, edition + effective-date masthead, **section titles +
  ≤ 1-sentence identifying excerpts** for the focal sections §4.65 /
  §4.66 / §4.67 / §4.68; **section titles + content-review verdict**
  (codex round-10 finding #3) for §2.7 / §4.46 / §4.49 / §4.51 /
  §4.53 / §4.55 / §4.56. **Ed 23 primary-source PDF mandatory for
  Branch A/A-retire/B selection** (codex round-12/22/26 finding
  cascade — codebase strings are NOT an authoritative proxy, AND
  task halts if Ed 23 PDF and planning-time SHA both unavailable
  rather than proceeding with `UNREVIEWED` rows). Each row
  classified `RENUMBERED` / `UNCHANGED` (principle remains valid) /
  `REFINED` (Ed 24 shifts meaning) / `RETIRED` (deferment filed) /
  `UNREVIEWED` (codex round-25/26 finding cascade — `UNREVIEWED`
  applies ONLY per-row when a specific section in the Ed 23 PDF is
  unreadable AT THE PAGE LEVEL while the rest of the PDF is
  available, NOT as a global fallback when the Ed 23 PDF is
  unavailable in toto; global Ed 23 unavailability halts the task). Excerpts attached
  for focal sections (always) and non-focal REFINED/RETIRED rows.
  **Ed 23 comparison source: planning-pulled Ed 23 PDF SHA
  `f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21e746e7`
  recorded in § Ed 23 comparison source** (codex round-12/15/16/22
  finding cascade — NO codebase-string fallback). If Ed 23 PDF
  cannot be captured AND planning-time SHA cannot be referenced:
  task halts, file `D-PASS-cap413-edition-23-comparison-
  unavailable` (codex round-24/25 finding cascade — no
  `UNREVIEWED`-rows-continue fallback at branch-selection scope).
  Hypothesis reproduced. Local extraction procedure + fallback note
  (per Step 1 if `pdftotext` failed heading-extraction). **Mapping
  table old §-number+topic → new §-number+topic** (drives R3-R6).
  **Branch C**:
  every URL attempted, observed returned edition masthead at each,
  SHA-256 of returned Ed 23 PDF (as active citation source), capture
  date, reason Ed 24 captures could not be produced, hypothesis
  reproduced, deferment ID filed (`D-PASS-cap413-edition-24-rename-
  pending-pdf`).
- [ ] **R2** — Verification artifact's § Verdict section names the
  selected branch (A / A-retire / B / C) and gates downstream
  criteria per the **authoritative R-firing-by-branch table** in
  epic R2 (codex round-19 finding — single source of truth). Quick
  reference: R1 / R2 / R9 / R10 / R11 fire in all branches; R7
  fires in all branches (mode differs by branch); R3-R6 / R8 fire
  in A only; R12 fires in B only; R13 fires in A-retire only.
- [ ] **R3** (Branch A only) — **Typed-entry scope** (codex round-3
  finding #1): only `val CAP413_4_65` is in scope. Per R1 mapping
  table: rename to new symbol if Ed 24 moves ATC-initiated GA; leave
  alone if Ed 24 keeps §4.65 unchanged. **If Ed 24 retires the ATC-
  initiated-GA section entirely**: branch verdict becomes Branch A-
  retire (not Branch A); fn-17.1 ships per the Branch A-retire R2
  gating (R1/R2/R7/R9/R10/R11 only; R3 itself is no-op in A-retire).
  Doctrine-anchor removal handled by `D-PASS-cap413-edition-24-
  retired-atc-ga-phraseology` in a separate epic. §4.66/§4.67/§4.68
  are **prose-only** (NOT typed entries) and are handled in R5/R6,
  not R3. Title + principle (faithful one-line summary if Ed 24
  meaning differs from Ed 23, else unchanged) + section field match
  Step 1 captures for the affected symbol.
- [ ] **R4** (Branch A only) — `import` lines and call sites updated:
  `controller/.../TowerArrival.kt:83/267/298`,
  `controller/.../Controller.kt:931`. R11 confirms compile-clean (no
  separate compile-target invocation).
- [ ] **R5** (Branch A only) — Source-tree + wiki + AGENTS.md +
  STRATEGY.md prose updated for current-doctrine paragraphs. **Every
  `§4.6[5-8]` match classified** against the R1 mapping table (codex
  round-2 finding #3) — old §4.65 (ATC-initiated GA), §4.66
  (VFR-continue), §4.67 (pilot GA), §4.68 (military) all covered, not
  just §4.66/§4.67. **Every retained historical cite** explicitly
  classified (codex round-2/5 findings). **Recording preference**
  (codex round-5 finding #7): for prose-heavy Markdown narrative
  files, prefer artifact recording in
  `wiki/data-sources/cap413-edition-24-capture.md` § Retained
  historical cites (avoids polluting narrative); for code KDoc /
  Kotlin / Test KDoc, inline comment marker is appropriate. No silent
  "left alone" outcomes.
- [ ] **R6** (Branch A only) — Test KDoc + message strings + string-
  literal pins updated per epic R6 list (10 test files). **Every
  `§4.6[5-8]` match in test code classified** against R1 mapping table
  — old §4.65 references in tests (e.g. ObstructionGoAroundSpec.kt:424
  pin) map to whatever Ed 24 calls ATC-initiated GA. String-literal
  pins at `ObstructionGoAroundSpec.kt:424-425` and
  `ObstructionContinueApproachSpec.kt:924-925` updated per mapping.
  Typed-reference pin at `G3aRunwayObstructionContinueApproachTest.kt:
  797` re-verified for intent fidelity post-rename.
- [ ] **R7** — `.flow/specs/fn-14-*.md` per branch (codex round-6/10/
  20 finding cascade):
  - **Branch A**: inline-updated at lines 22 / 154 / 356 / 404 per
    Step 7. Deferment record at line 404 marked CLOSED.
  - **Branch B**: line-404 closure annotation PLUS any additional
    inline edits at fn-14 lines that R12's grep actually matches
    (codex round-20/21 finding cascade — hypothesis/deferment
    phrasing only). Per planning grep: line 22 and line 356 are in
    scope (carry hypothesis prose); **line 154 is NOT touched in
    Branch B** (carries normal `per CAP 413 §4.67` doctrinal
    phrasing — current-doctrine numbering is Branch B's confirmed
    status quo).
  - **Branch A-retire / Branch C**: line-404 closure annotation
    only; no other fn-14 edits.
- [ ] **R8** (Branch A only) — Errata footers appended (3-line block per
  Step 5) to `.flow/specs/fn-11-*.md`, `fn-12-*.md`, `fn-13-*.md`. No
  in-place rewrites to those files. **Branch B and Branch C**: closed-
  epic spec files NOT touched.
- [ ] **R9** (all branches) — `RegulationRef.CAP_413_EDITION` constant
  introduced; every CAP413_* entry's `edition = "27th ed. (2023)"`
  literal replaced. **Universal hard gate** (codex round-3/10/14/18
  finding cascade — applies in Branch A, A-retire, B):
  - `CAP_413_EDITION = "Edition 24 (effective 1 July 2026)"` set
    once in the companion.
  - **Per-entry application gate**: an entry uses `edition =
    RegulationRef.CAP_413_EDITION` (Ed 24) **only if** its Table 2
    row is `UNCHANGED` / `RENUMBERED` (section updated in-task) /
    `REFINED` (principle updated in-task). Otherwise — `RETIRED`,
    `(REFINED, updatedInTask=false)`, or `UNREVIEWED` — the entry keeps an
    **inline literal** `edition = "Edition 23 Corr (effective 21
    January 2021)"` with KDoc noting the intentional exception, and
    files a per-entry deferment (`D-PASS-cap413-edition-24-retired-
    <n>` / `-refined-<n>` / `-unreviewed-<n>`). No Ed 24 metadata on
    unverified or stale-principle content.
  - **Branch C**: `RegulationRef.CAP_413_EDITION = "Edition 23 Corr
    (effective 21 January 2021)"`, applied to every CAP413_* entry
    uniformly (no hard gate needed — Ed 23 metadata + Ed 23 numbers
    are universally coherent).
  **Invariant (rephrased per codex round-23 finding #4 — verification
  basis, not number form): no section number whose meaning is
  unverified or whose principle belongs only to Ed 23 may carry Ed
  24 metadata.** Branch B intentionally leaves §-numbers unchanged
  while switching to Ed 24 metadata — that's coherent because Branch
  B verified Ed 24 retains those §-numbers. Branch A's hard-gate
  exceptions cover unverified / Ed-23-only-content cases. No TODO
  comment in source. Verified at every site:
  362, 371, 380, 387, 405, 426, 443, 475 (post-rename line numbers
  may shift in Branch A).
- [ ] **R10** (all branches) — **Two-phase; both required for task
  completion** (codex round-15/18/20/23/24 finding cascade —
  unified contract; commits are part of task completion, not
  optional):
  - **R10a (working-tree state)**: in the working tree (pre-
    commit), `.plan` closure entry written (branch verdict + PDF
    SHA + pointer `"see .flow/tasks/fn-17-cap-413-edition-24-
    numbering.1.md ## Evidence"`) AND new branch-specific
    deferments added to `.plan` AND task `## Evidence` populated
    with branch verdict / PDF SHA / register-touch list / gradle
    output / grep audit.
  - **R10b (commit + `flowctl done`)**: implementation commit
    lands; then run `flowctl done fn-17-cap-413-edition-24-
    numbering.1 --summary-file <md> --evidence-json <json>` to
    populate `## Done summary` + `## Evidence`'s `Commits:` line
    with the primary SHA; create a small follow-up metadata commit.
  Both R10a and R10b are **required for fn-17.1 task completion**.
  If commits are blocked at task time, fn-17.1 is **incomplete and
  blocked**, NOT silently passed (codex round-24 finding #3 — no
  ambiguity).
  New deferments added to `.plan` per branch:
  - `D-PASS-cap413-edition-24-rename-pending-pdf` — **Branch C only**
    (consolidated single deferment per codex round-7 finding #1;
    covers both future rename + edition-metadata switch as one
    coherent action when Ed 24 PDF becomes retrievable).
  - `D-PASS-cap413-edition-23-pdf-unreachable-at-task-time` — Branch
    C if the Ed 23 PDF cannot be fresh-pulled AND only the planning-
    time SHA fallback was used (codex round-28/29 finding cascade).
  - `D-PASS-cap413-edition-24-<section>` — if Step 1 surfaces other
    Ed 24 changes.
  - `D-PASS-cap413-principle-text-deep-refresh` — if Step 1 surfaces
    semantic shifts.

  **Sister register** `~/.claude/plans/pilot-firewall.md` updated when
  reachable (best-effort per `.plan:484` convention; off-repo). When
  unreachable, `## Evidence` records the gap.
- [ ] **R12** (Branch B only — codex round-9/19 finding cascade) —
  `rg -n 'docs-scout caught Edition 24 renumbered|Ed 24 renumbered
  §4\.6|D-PASS-cap413-edition-24-reconciliation' .flow/specs/
  wiki/ AGENTS.md STRATEGY.md` walked; every active occurrence
  (**including fn-14 inline edits at lines 22/154/356 if those carry
  the hypothesis prose, in addition to the line-404 closure
  annotation from R7**) edited to verified Branch-B verdict OR
  recorded in verification artifact's § Retained historical cites.
  Branch A: no-op (folded into R5/R7). Branch C: no-op (recorded in
  artifact as historical record).
- [ ] **R13** (Branch A-retire only — codex round-17/19 finding
  cascade — binding criterion for Step 4a) — `rg -n '§4\.65|
  CAP413_4_65' protocol pilot controller sim wiki AGENTS.md
  STRATEGY.md .flow/specs --glob '!**/build/**'` walked; every
  active occurrence classified (annotated inline with `(Ed 23
  anchor retained pending D-PASS-cap413-edition-24-retired-atc-
  ga-phraseology)`, OR recorded in artifact's § Retained
  historical cites). Acceptance shape: zero unclassified hits.
  Branch A: no-op (R5 covers). Branch B: no-op (§4.65 retained
  unchanged). Branch C: no-op (§-numbers not touched).
- [ ] **R11** — `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest
  :core:allTests :protocol:allTests detekt` exits 0. Eight goldens
  GREEN (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction /
  G3a-obstruction-continue-approach / G3a-react). detekt baseline
  unchanged. **Authoritative verification command** — no intermediate
  per-module targets. **Eight-golden evidence** (codex round-5
  finding #6): `## Evidence` records either the Gradle test-report
  paths confirming the eight test classes ran AND passed, OR explicit
  per-test `--tests "*<GoldenClass>*"` enumerations — auditable
  without re-running. **Failure handling**: any failure recorded in
  `## Evidence` with full failing test name + stderr. No
  "pre-existing flakiness" waiver without master-merge-base bisect
  evidence AND a deferment filing (`D-PASS-flaky-test-<name>`). No
  silent skip / retry / exclusion.

## Done summary

**Branch verdict**: Branch A — Confirmed (with Edition #1 quirk per Step 2 / epic R2).

**Primary-source verification**:
- Ed 24 PDF URL: `https://www.caa.co.uk/publication/download/18165` (the CAA landing page's "CAP 413 Future" link at `/our-work/publications/documents/content/cap-413/`; Path 1's `/27609` advertised endpoint still served Ed 23 Corr at task time, so the load-bearing primary-source had to come from Path 2's landing page).
- Ed 24 PDF SHA-256: `c620cda9b6bdbe8e9ed51b258e4df2f6e3edc839226e53ee2b591cb696a966ac` (Edition 24, "Incorporating amendments to 31 March 2026", Effective date 1 July 2026; captured 2026-05-11).
- Ed 23 comparison source SHA-256: `f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21e746e7` (matches planning-time capture exactly).

**Mapping** (uniform `-1` shift; deeper than the docs-scout hypothesis caught — Ed 23 §4.65 ATC-initiated GA also moves backward):
- Ed 23 §4.49 → Ed 24 §4.48 (circuit sequencing; RENUMBERED — `CAP413_4_49.section` updated)
- Ed 23 §4.53 → Ed 24 §4.52 (cancellation of issued landing clearance; RENUMBERED — `CAP413_4_53.section` updated)
- Ed 23 §4.55 → Ed 24 §4.54 (continue approach — runway obstructed; RENUMBERED — `CAP413_4_55.section` updated)
- Ed 23 §4.56 → Ed 24 §4.55 (CA is not landing clearance; RENUMBERED — `CAP413_4_56.section` updated)
- Ed 23 §4.65 → Ed 24 §4.64 (ATC-initiated GA; RENUMBERED — `val CAP413_4_65` renamed to `val CAP413_4_64`; consumer call sites updated in `Controller.kt`, `TowerArrival.kt`)
- Ed 23 §4.66/§4.67/§4.68 → Ed 24 §4.65/§4.66/§4.67 (VFR-continue / pilot GA / military; prose-only — no typed entries)
- Ed 23 §2.7 (SAFETYCOM) → Ed 24 §2.7 (UNCHANGED — but codebase principle pre-existing miscite; flagged UNREVIEWED, inline Ed 23 Corr literal retained, deferment `D-PASS-cap413-2_7-principle-cite-audit` filed)
- Ed 23 §4.46 → Ed 24 §4.46 with shifted content (codebase principle pre-existing miscite; UNREVIEWED, inline Ed 23 Corr literal, deferment `D-PASS-cap413-4_46-principle-cite-audit` filed)
- Ed 23 §4.51 → Ed 24 §4.51 (UNCHANGED in Ed 24 — codebase principle "REPORT FINAL RUNWAY" coincidentally matches Ed 24 §4.51 content; in Ed 23 this content lived at §4.52)

**Files touched** (29 in the primary commit + 2 in the R11-verify-evidence follow-up = 31 total; the research/tools/requirements-spike modifications from a separate spike are intentionally excluded per parent agent instruction):
- Protocol model + database (2): `RegulationModel.kt` (added `CAP_413_EDITION` const), `RegulationDatabase.kt` (rename + section field + KDoc updates per Table 2 verdicts).
- Controller production (3): `Controller.kt`, `bdi/Action.kt`, `procedure/TowerArrival.kt`.
- Controller tests (2): `ObstructionGoAroundSpec.kt`, `ObstructionContinueApproachSpec.kt`.
- Pilot production (4): `Pilot.kt`, `PilotMission.kt`, `PilotCognitive.kt`, `observe/PilotEvent.kt`.
- Pilot tests (4): `PilotCrosswindTickATickBTest.kt`, `PilotAtcInitiatedGoAroundSpec.kt`, `PilotCrosswindGoAroundTest.kt`, `PlannedGoAroundSpec.kt`.
- Sim tests (4): `G3aRunwayObstructionTest.kt`, `G3aPilotTrainedGoAroundTest.kt`, `G3aRunwayObstructionContinueApproachTest.kt`, `G3aPilotReactiveCrosswindTest.kt`.
- Wiki / data-sources (3): NEW `cap413-edition-24-capture.md`, plus `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md` and `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md`.
- AGENTS / .plan (2): `AGENTS.md`, `.plan` (closure entry + 3 new deferments — 2 still open for principle-cite audit, 1 CLOSED for the R11-verify workaround).
- Closed-epic spec errata (3): `fn-11-...md`, `fn-12-...md`, `fn-13-...md` — `## Errata` footers appended.
- Active-epic spec inline (1): `fn-14-...md` lines 22 / 154 / 356 / 404 rewritten with verified verdict.
- Next-session spec inline (1): `fn-15-...md` line 416.

**Eight goldens stayed GREEN** (R11 satisfied — Gradle ran in the sandbox via `GRADLE_USER_HOME=$TMPDIR/gradle-user-home` workaround, see `## Evidence` below for the exact command + per-class XML verification).

**Deferments closed**:
- `D-PASS-cap413-edition-24-reconciliation` (filed in fn-14) — CLOSED by this task per the verification artifact at `wiki/data-sources/cap413-edition-24-capture.md`.
- `D-PASS-cap413-edition-24-r11-verify-sandbox-block` (filed during this task at codex round 1 NEEDS_WORK; closed during round 2 via the cloned-Gradle-user-home workaround documented in `.plan`).

**Deferments opened**:
- `D-PASS-cap413-2_7-principle-cite-audit` — `CAP413_2_7.principle` references "frequency change / two-way communication" but cited §2.7 content is SAFETYCOM in both Ed 23 and Ed 24. Pre-existing principle-vs-cite drift; out of fn-17 renumbering scope.
- `D-PASS-cap413-4_46-principle-cite-audit` — `CAP413_4_46.principle` references "hold-short readback" but cited §4.46 content is traffic info (Ed 23) / routine reports (Ed 24). Same pre-existing drift shape.

## Evidence

- Commits (chronological, all on `main`):
  - Primary implementation: `68dc375a02b3b2fde95d44bd74238377e648cfbb` — "fn-17.1: CAP 413 Edition 24 numbering reconciliation (Branch A)" — the load-bearing rename + KDoc/prose sweep + edition-string correction + verification artifact + closed-spec errata footers.
  - R11-verify-evidence follow-up: `3bfe9ce` — "fn-17.1: R11 verify completed — eight goldens GREEN, close R11-verify-sandbox-block deferment".
  - Task-evidence population: HEAD-1 (this file) — "fn-17.1: populate Done summary + Evidence sections (R10b)".
  - Final `flowctl done` state-transition commit follows immediately after the last review SHIP per fn-17.1 Step 9.2 ("Follow-up metadata via `flowctl done`"). The `flowctl done` invocation patches the task spec's status field and produces the small follow-up commit recording the primary SHA; the chicken-and-egg recurrence ends there per the spec's explicit "the task IS done at this point" note.
- Tests run: `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt --offline --no-daemon` exited **0** (BUILD SUCCESSFUL in 40s, 25 actionable tasks executed). Eight-golden evidence per the robust per-class XML check in Step 10:
  - `sim/build/test-results/jvmTest/TEST-xyz.easiersaid.twr.sim.LowgGoldenTest.xml` — failures=0 errors=0 skipped=0 tests>0
  - `sim/build/test-results/jvmTest/TEST-xyz.easiersaid.twr.sim.G1TwoAircraftCircuitsTest.xml` — failures=0 errors=0 skipped=0 tests>0
  - `sim/build/test-results/jvmTest/TEST-xyz.easiersaid.twr.sim.G1TwoAircraftMinimalSpec.xml` — failures=0 errors=0 skipped=0 tests>0
  - `sim/build/test-results/jvmTest/TEST-xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest.xml` — failures=0 errors=0 skipped=0 tests>0
  - `sim/build/test-results/jvmTest/TEST-xyz.easiersaid.twr.sim.G3aPilotTrainedGoAroundTest.xml` — failures=0 errors=0 skipped=0 tests>0
  - `sim/build/test-results/jvmTest/TEST-xyz.easiersaid.twr.sim.G3aRunwayObstructionTest.xml` — failures=0 errors=0 skipped=0 tests>0
  - `sim/build/test-results/jvmTest/TEST-xyz.easiersaid.twr.sim.G3aRunwayObstructionContinueApproachTest.xml` — failures=0 errors=0 skipped=0 tests>0
  - `sim/build/test-results/jvmTest/TEST-xyz.easiersaid.twr.sim.G3aPilotReactiveCrosswindTest.xml` — failures=0 errors=0 skipped=0 tests>0
- Verification artifact: `wiki/data-sources/cap413-edition-24-capture.md` (contains Ed 24 PDF SHA-256 + section titles + ≤ 1-sentence excerpts + Table 1 focal renumbering map + Table 2 typed-entry audit + retained historical cites + local extraction procedure).
- Branch: `main` (per parent agent context — branch choice was current branch; user's epic spec listed `master` as `branch_name` but the actual repository branch is `main`).
- Sandbox-workaround pattern (R11 enablement): `GRADLE_USER_HOME=$TMPDIR/gradle-user-home` (clone of `/Users/andrew/.gradle/{caches,native,wrapper}` with lock files stripped) + `_JAVA_OPTIONS=-Djava.io.tmpdir=$TMPDIR` (Kotlin compiler intermediate files) + `--offline --no-daemon`. Recorded in `.plan` deferment closure for future implementers in similarly-restricted environments.
- Sister register: `~/.claude/plans/pilot-firewall.md` not touched in this run (off-repo bookkeeping; sandbox-allowed but no deferment-register section discovered there relevant to CAP 413).
- PRs: _(none — task closure does not require PR per fn-17 epic configuration; subsequent epic close will roll up via the parent agent's `/flow-next:make-pr` if desired)._
