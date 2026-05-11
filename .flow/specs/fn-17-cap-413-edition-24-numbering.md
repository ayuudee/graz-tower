# CAP 413 Edition 24 numbering reconciliation

## Overview

**Effective-date policy** (codex round-28 finding #5): this project
intentionally cites next-effective CAP 413 once primary-source
verified, even before 2026-07-01. The codebase becomes Ed 24-
coherent from fn-17's merge onward in Branch A / A-retire / B (Ed
24 positively verified); Branch C keeps Ed 23 Corr metadata until
Ed 24 PDF is retrievable.

Doctrinal-citation cleanup pass. CAP 413 (UK CAA *Radiotelephony Manual*) Edition 24
(republished 2026-04-21, effective 2026-07-01) is the next-effective edition;
the current effective edition as of this epic's planning date (2026-05-11) is
**Edition 23 / Edition 23 Corr** (effective 2021-01-21, with the
2024-03-28 Edition 24 first-release withdrawn for errors). The flow-next-14
docs-scout finding — "Edition 24 renumbered §4.66 (VFR-continue) → §4.65 and
§4.67 (pilot-initiated GA) → §4.66" — could not be independently verified
against publicly served CAA PDFs during planning (the
`caa.co.uk/publication/download/27609` link the CAA's CAP 413 landing page
advertises as "current" still serves Edition 23 as of planning capture). The
docs-scout claim is therefore treated as a **hypothesis to confirm or refute
against the primary Ed 24 PDF** before any rename, not as a settled fact. This
epic's Task .1 includes primary-source verification as its **Step 1**, and the
mechanical rename only fires if verification confirms the renumbering.

Scope at planning time: doctrinal-citation cleanup. No code behaviour change.
Pure rename + KDoc principle-text refresh + edition-string correction. Closes
the `D-PASS-cap413-edition-24-reconciliation` deferment filed in fn-14.

**Secondary scope** discovered during planning: every existing CAP 413 entry in
`RegulationDatabase.kt` carries `edition = "27th ed. (2023)"` — a stale string
that does not match any real CAP 413 edition (CAP 413 numbering is Ed 1..24,
not 27). This is **independent** of any §4.x renumbering and is an outright
factual error in the regdb. The epic fixes that string in-place via a new
`RegulationRef.CAP_413_EDITION` named constant whose value is **coupled to
the Step 1 branch verdict** (Decision #7 — codex round-3/4 critical
finding):

- Branch A (Ed 24 renumbering confirmed): constant = `"Edition 24
  (effective 1 July 2026)"` because `section` fields now cite Ed 24
  numbering. **Citation triple must be coherent**: Ed 24 numbers with Ed
  24 metadata.
- Branch A-retire (Ed 24 retires ATC-initiated-GA phraseology
  entirely — codex round-9/13/14 finding cascade): constant =
  `"Edition 24 (effective 1 July 2026)"`. **Exception** (codex
  round-14 critical finding — citation-triple coherence):
  `CAP413_4_65` (whose principle describes Ed-23 §4.65 ATC-initiated
  GA, a topic Ed 24 has retired) keeps its **inline literal**
  `edition = "Edition 23 Corr (effective 21 January 2021)"` —
  bypassing `CAP_413_EDITION` — because applying Ed 24 metadata to
  Ed-23-only content would produce a false triple. All other
  CAP413_* entries (which Ed 24 verified retains) use the constant.
  This partial-shift is the bridge state; the doctrine-anchor-
  removal epic (`D-PASS-cap413-edition-24-retired-atc-ga-phraseology`)
  completes the migration by either retiring or replacing
  `CAP413_4_65`.
- Branch B (Ed 24 verified to retain Ed 23 numbering): constant = `"Edition
  24 (effective 1 July 2026)"` because Ed 24 has been positively verified.
- Branch C (Ed 24 PDF unavailable at task time): constant = `"Edition 23
  Corr (effective 21 January 2021)"`; §-numbers stay Ed 23. File
  `D-PASS-cap413-edition-24-rename-pending-pdf` to revisit on/after
  2026-07-01 when the Ed 24 PDF becomes retrievable.

**Deferment naming convention** (codex round-22 finding #5):
- `D-PASS-cap413-edition-24-retired-atc-ga-phraseology` — Branch
  A-retire's canonical deferment for §4.65 ATC-initiated-GA
  retirement (most-likely RETIRED row).
- `D-PASS-cap413-edition-24-retired-<section>` (e.g. `-retired-4-46`)
  — generalised hard-gate deferment for any other CAP413_* entry
  Table 2 classifies as `RETIRED` (rare).
- `D-PASS-cap413-edition-24-refined-<section>` — `REFINED-not-
  updated` rows.
- `D-PASS-cap413-edition-24-unreviewed-<section>` — `UNREVIEWED`
  rows.
- `D-PASS-cap413-edition-23-comparison-unavailable` — Branch B-
  unverified-comparison sub-branch (codex round-22 finding #4).
- `D-PASS-cap413-edition-24-rename-pending-pdf` — Branch C primary
  consolidated deferment.

All naming patterns appear in `.plan` per R10 as branch-specific
new deferments.

In Branch A or Branch B the codebase cites Ed 24 doctrine starting from
the fn-17 merge — acceptable per `feedback_reality_anchored.md` (doctrine
moves; cite next-current). No TODO comment in source: the Branch-C
future-switch is a tracked deferment, not a `// TODO` (per project rule:
no silent deferred-action markers in source).

**Scenario:** Task .1 Step 1 pulls the Edition 24 primary PDF from the CAA
(or, if unavailable at task time, defers the rename to a follow-up). If the
PDF confirms the renumbering hypothesis, every `CAP413_4_66`-equivalent /
`CAP413_4_67`-equivalent / §4.66 / §4.67 prose mention in
`RegulationDatabase.kt` + KDoc + tests + wiki + flow specs is updated to the
new numbering. If the PDF refutes the hypothesis, the epic closes as a no-op
on the renumbering (Ed 24 keeps Ed 23 numbering) and only the
verification artifact (R1) + edition-string correction (R9) + deferment closure (R10) + full verify (R11) ship (Branch-A-only criteria R3-R8 skipped). **No deprecation shim** is
added — project rule (per user instructions): no backwards-compat hacks;
entry IDs get renamed and every consumer re-imports.

## Boundaries / non-goals

- **Out: code behaviour changes.** Pure citation rename + edition-string
  refresh. No new rules, no new actions, no new tests beyond what the
  rename touches.
- **Out: rewriting closed-epic flow specs body prose.** Per planning
  decision (see Decision #5 below), closed-epic specs (`fn-11`, `fn-12`,
  `fn-13`) get a one-line **errata footer** appended **only in Branch A**.
  In Branch B / Branch C, closed-epic specs are NOT touched (the
  verification artifact alone records the verdict). Active-epic spec
  (fn-14 — recently completed but still relevant) gets inline updates in
  Branch A only.
- **Out: other CAP 413 Edition 24 changes.** Only §4.65 / §4.66 / §4.67 /
  §4.68 renumbering and edition-string correction. If primary-source
  review reveals other Edition 24 changes affecting cited sections (e.g.
  §4.46, §4.49, §4.51, §4.53, §4.55, §4.56, §2.7), file each as a sibling
  deferment (`D-PASS-cap413-edition-24-<section>`); do NOT auto-rename.
- **Out: ICAO Doc 4444 / SERA / Annex 11 / etc.** edition reconciliation.
  Each non-CAP-413 source has its own edition history; this epic is CAP
  413 only. Filed as `D-PASS-doctrinal-edition-reconciliation-non-cap413`
  for a potential future epic.
- **Out: principle-text upgrades that change semantic meaning.** Per
  Decision #3 below, principle strings are **faithfully summarised in the
  existing one-line style**, NOT verbatim long quotes (which would risk
  licence violation and don't fit the model). Meaningful reinterpretation
  (e.g. moving a clause from `GUIDANCE` to `PHRASEOLOGY` category) is
  out-of-scope and filed as a follow-up.
- **Out: verbatim PDF redistribution.** The verification artifact
  `wiki/data-sources/cap413-edition-24-capture.md` captures SHA-256 +
  source URL + section titles + short identifying excerpts (≤ 1 sentence
  per section, sufficient to disambiguate the renumbering), NOT
  paragraph-length verbatim manual text (per Decision #1's licence
  gate — Crown copyright with CAA-specific reproduction terms,
  verified at task time per the PDF's front-matter notice; codex
  round-25 finding #6 — do not bake in an OGL v3 assumption).
- **Out: post-implementation re-pull of Ed 24 on/after 2026-07-01.** Ed
  23 Corr → Ed 24 effective-date switch handled by the separate
  `D-PASS-cap413-edition-24-rename-pending-pdf` deferment, NOT a TODO
  comment in `RegulationRef.CAP_413_EDITION`.
- **Out: changes to `.claude/agents/atc-phraseology.md`'s reference paths.**
  That file points to `agents/research/phraseology/cap413.txt` (an offline
  cache, not in this repo); leave the agent-doc reference alone.

## Strategy Alignment

Active tracks served by this plan:

- **Runtime simulator** — citation hygiene. The four CAP 413 sections at the
  centre of the doctrinal hot-path for fn-11 / fn-12 / fn-13 / fn-14
  (pilot-initiated GA, VFR-continue, controller-initiated missed approach)
  must cite the current effective edition correctly. Stale cites in the
  doctrinal anchors of the runtime simulator surface erode trust in the
  reality-anchored modelling principle.

## Decision context

### 1. Primary-source verification as Task .1 Step 1 — abort-if-different gate, with licence-bounded quoting

**Decided.** Before any rename, Task .1 Step 1 downloads the Edition 24 PDF
from the CAA (effective 2026-07-01 — `caa.co.uk/publication/download/27609`
if/when it serves Ed 24, or the next-published equivalent URL discovered at
task time), records the SHA-256 of the captured PDF in the commit message
**and** in a new `wiki/data-sources/cap413-edition-24-capture.md` provenance
note, then runs the verification checklist:

- §4.65 in Ed 24 — section **title + ≤ 1-sentence identifying excerpt**.
  (Ed 23: ATC-initiated missed approach.)
- §4.66 in Ed 24 — section **title + ≤ 1-sentence identifying excerpt**.
  (Ed 23: VFR aircraft to continue into normal traffic circuit.)
- §4.67 in Ed 24 — section **title + ≤ 1-sentence identifying excerpt**.
  (Ed 23: pilot-initiated GOING AROUND.)
- §4.68 in Ed 24 — section **title + ≤ 1-sentence identifying excerpt**.
  (Ed 23: Military missed approach reference to Ch 10.)
- §4.46, §4.49, §4.51, §4.53, §4.55, §4.56, §2.7 — confirm "section
  identical to Ed 23 wording" or note shift (≤ 1-sentence excerpt only).

**Licence gate** (codex round-1/22 finding cascade): CAP 413 is Crown
copyright with the CAA's specific reproduction terms (the CAP 413 PDF
includes its own reproduction notice; per codex round-22 finding #3,
do NOT assume OGL v3 grant — verify the front-matter at task time).
**Capture policy**: section titles + page/line locators + paraphrases
+ **only tiny identifying excerpts when necessary** (capped at **≤
25 words per excerpt**, codex round-22 finding #3). Paraphrase is
the default for `UNCHANGED` rows; verbatim excerpts only when needed
to disambiguate `RENUMBERED` / `REFINED` / `RETIRED` classification.
Local-machine extraction procedure (`pdftotext` + grep) documented
in the artifact so future reviewers can re-derive without
re-downloading or redistributing.

**Decision tree**:
- **Branch A (renumbering confirmed; ATC-initiated-GA section
  preserved in Ed 24 at some §-number)**: docs-scout finding is right
  — proceed with full rename per R3-R8 below.
- **Branch A-retire** (sub-branch — Ed 24 RETIRES the verbatim ATC-
  initiated-GA phraseology section entirely; codex round-9 finding
  #3): doctrine-anchor removal is **out of fn-17 scope**. Stop and
  split: file `D-PASS-cap413-edition-24-retired-atc-ga-phraseology`
  with the Ed 24 evidence; do NOT retire the `val CAP413_4_65` in
  this task. Ship only verification artifact (R1) + edition-string
  correction (R9) + deferment closure (R10) + Branch-B-style
  one-line closure annotation on fn-14:404 (R7 Branch-B/C arm) +
  full verify (R11). Skip R3 / R4 / R5 / R6 / R8 / R12. R2 verdict
  records "Branch A-retire: doctrine-anchor removal deferred."
- **Branch B (renumbering refuted; Ed 24 keeps Ed 23 numbering)**:
  selectable when Ed 24 has been positively verified to retain Ed
  23 §-numbering for §4.65-§4.68 (codex round-25/29 finding cascade
  — refined). **Per-entry handling under R9 hard gate**: if any
  CAP413_* Table 2 row is `RETIRED` / `REFINED-not-updated` /
  `UNREVIEWED`, that entry keeps an inline Ed 23 literal per R9's
  hard gate (just like Branch A); Branch B is still selectable for
  the overall rename verdict. Branch B's verdict is about §-number
  preservation, not about every entry being UNCHANGED. The
  edition-string-correction
  sub-scope (R9) ships. The one-line closure annotation at fn-14:404
  ships (R7 Branch-B/C arm). **Stale docs-scout-hypothesis prose
  outside fn-14** (codex round-7 finding #5): grep for the hypothesis
  phrasing — `rg -n 'docs-scout caught Edition 24
  renumbered|Ed 24 renumbered §4\.6' .flow/specs/ wiki/` — and at each
  active occurrence outside fn-14, **update the prose to reflect
  Branch-B's verified verdict** (single-line edit per occurrence) OR
  record in the verification artifact's § Retained historical cites
  why it remains unchanged. Default: edit. Leaving the stale
  hypothesis prose misleads future readers when Branch B has positively
  refuted it. Closed-epic flow specs (fn-11/12/13) NOT touched — they
  don't carry the docs-scout-claim phrasing per planning grep.
  `D-PASS-cap413-edition-24-reconciliation` closure record cites
  "verified Ed 24 retains Ed 23 §4.x numbering" + PDF SHA.
- **Branch C (Ed 24 PDF unavailable at task time)**: abort the rename
  sub-scope; ship only R9. Re-file the rename as
  `D-PASS-cap413-edition-24-rename-pending-pdf`. **Closed-epic flow
  specs NOT touched.**

**Why abort-if-different**: the docs-scout finding from fn-14 was not
verified against a current CAA-served PDF. The user-supplied brief
explicitly pinned "verify primary source as Step 1 of implementation,
abort if numbering is different" as a load-bearing decision. Planning-time
verification surfaced that the CAA download endpoint still serves Ed 23,
so the abort gate is **load-bearing not optional**.

### 2. Entry ID renames — narrow scope (only `CAP413_4_65` is typed), no deprecation shim

**Decided** (refined per codex round-3 major finding). Per planning grep,
the **only typed CAP413 entry in `{4.65, 4.66, 4.67, 4.68}` is
`CAP413_4_65`**. §4.66, §4.67, and §4.68 appear only as **prose KDoc
citations** and **test-message strings** — they are NOT
`val CAP413_4_<n> = RegulationRef(...)` declarations. This significantly
narrows the typed-rename scope:

- **Typed-entry rename scope (Branch A)**: only `CAP413_4_65` (ATC-
  initiated missed-approach phraseology). Per R1 mapping table: if Ed 24
  keeps ATC-initiated GA at §4.65, no rename needed (principle string
  stays the same; the symbol stays). If Ed 24 moves ATC-initiated GA to
  a different §, **rename `CAP413_4_65` to `CAP413_4_<new>`**; the
  principle stays the same. If Ed 24 retires verbatim ATC-initiated-GA
  phraseology entirely (very unlikely; verify at Step 1), retire the val
  with deletion + downstream re-routing.
- **Prose-only §4.66/§4.67/§4.68 scope**: handled in Step 4's
  grep-driven prose sweep. KDoc text + test message strings get updated
  per the R1 mapping table; no `val` declarations to manipulate. The
  earlier draft assumption that `CAP413_4_66/67/68` exist as typed
  entries was wrong (codex round-3 finding).
- **New typed entries**: this epic does NOT add new `CAP413_4_<n>`
  symbols. If Ed 24's renumbering happens to map old §4.66 to new §4.65
  (the hypothesis), the prose-only citations update in Step 4 and no
  new `val` is added (because the codebase never carried a typed
  `CAP413_4_66`). If a future epic wants typed entries for VFR-continue
  / pilot-GA phraseology, file separately — fn-17 is rename + prose
  hygiene, not surface expansion.

**No deprecation shim** (project rule per user feedback): no
`@Deprecated` typealias, no parallel symbols. Commit message annotates
the rename mechanically; Kotlin compiler catches every consumer.

**Naming policy** for renamed IDs (if Branch A renames `CAP413_4_65`):
keep the `CAP413_4_<digits>` pattern; if Ed 24 introduces sub-numbered
sections (e.g. §4.65.1), use `CAP413_4_65_1` (underscore-separator,
mirrors existing `ICAO4444_7_4_1_4_1`). Verify at task time.

### 3. Principle-text comparison against Ed 24 — faithful summary, not verbatim

**Decided** (codex round-1 finding). `RegulationRef.principle` is a
one-line model principle, **not a quotation field**. Verbatim manual text
is too long, awkward, and licence-sensitive. The rule is: **faithfully
summarise the Ed 24 section in the existing one-line principle style**,
capturing the exact section number + title in the verification artifact
(Decision #1) so any future cross-check has the precise authoritative
text without redistributing it in source.

If Ed 24's content materially changes meaning vs Ed 23, the principle
summary is rewritten to reflect the new meaning. If Ed 24 only changes
wording (modulo whitespace / hyphenation) without changing meaning,
leave the principle string alone.

**Anti-decision**: do NOT "improve" the principle text during this pass
beyond the minimum required to track Ed 24's meaning. Stylistic
refinement is a separate pass with its own review surface.

**Canonical classification vocabulary** (codex round-27/28/29 finding
cascade — single source of truth for Table 1 / Table 2 / R9 hard
gate; all earlier scattered labels normalise to this enum):

Each row carries TWO fields:
- `classification`: one of `UNCHANGED` / `RENUMBERED` / `REFINED` /
  `RETIRED` / `UNREVIEWED`.
- `updatedInTask`: `true` / `false` (whether fn-17.1 updated the
  corresponding `RegulationDatabase.kt` entry in-task).

R9 hard gate rule: an entry uses `edition = RegulationRef.CAP_413_
EDITION` (Ed 24 metadata) IF its row has `(classification ∈
{UNCHANGED, RENUMBERED, REFINED}) AND (updatedInTask = true OR
classification = UNCHANGED)`. Otherwise — `RETIRED`,
`REFINED + updatedInTask=false`, or `UNREVIEWED` — keeps an inline
Ed 23 literal with KDoc exception.

Older drafts used composite labels like `RENUMBERED_UPDATED` or
`REFINED-not-updated`; those are deprecated; use the two-field shape.

**Ed 23 comparison source — single normalised rule** (codex round-24/
25/27 finding cascade — replaces all earlier scattered Ed-23-
unavailability handling):
- The **planning-time Ed 23 PDF SHA**
  `f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21e746e7`
  recorded in § Captured research is a **valid comparison source for
  all branches**.
- Branch A / A-retire / B selection requires **either** a fresh-pull
  Ed 23 PDF at task time **OR** the recorded planning-time SHA. No
  codebase-string fallback. If both fresh-pull AND planning-time
  reference are unavailable (rare double-failure — implementer
  cannot access the planning artifact in their environment): task
  halts; file `D-PASS-cap413-edition-23-comparison-unavailable`.
- Branch C uses the same Ed 23 source rule as its **active citation
  source** (since Branch C pins `CAP_413_EDITION = Ed 23 Corr`).
  Branch C proceeds with planning-time SHA when no fresh-pull is
  available; Branch C does NOT halt when only the planning-time
  reference is usable.

### 4. Older-edition references — audit for intentional historical cites

**Decided.** Some cites might intentionally point to an older edition (e.g.
"per CAP 413 Edition 23 because that was the doctrine effective when fn-12
shipped"). Audit at task time: grep `Edition 23\|Edition 22\|Ed 23\|Ed 22`
across the codebase. **Per planning check**, no such intentional
older-edition cite exists (the only match is `fn-12-g3a-obstruction-single-
aircraft-atc.md:389` which says `"CAP 413 Edition 23 (or 24, effective
2024-03-28) §4.53"` — that's hedged uncertainty, not an intentional pin).

**Rule**: if Task .1 finds an intentional older-edition cite (e.g. in
`wiki/design-decisions/`), leave that cite alone **and** classify
explicitly. **Recording preference** (codex round-5 finding #7):
- **Prose-heavy Markdown narrative files** (`wiki/design-decisions/`,
  `wiki/domain/`, closed-epic `.flow/specs/`, STRATEGY.md narrative
  paragraphs, etc.): prefer **artifact recording** in
  `wiki/data-sources/cap413-edition-24-capture.md` § Retained
  historical cites (file path + line + cite + reason). Inline `<!--
  comment -->` distracts from narrative; keep prose clean and the
  audit trail in the artifact.
- **Code KDoc / Kotlin comments / Test KDoc**: inline comment marker
  `// intentional Ed 23 cite: historical record (reason)` or
  `/* ... */` is appropriate — the code reader benefits from
  inline context.
- **Either way**, every retained cite is explicitly classified — no
  silent "left alone" outcomes (codex round-2 finding #4). The rename
  touches **current-doctrine cites**, not historical ones, but the
  classification record is unambiguous.

### 5. `.flow/specs/` closed-epic specs — Branch-A-only errata footer, NOT body rewrite

**Decided** (codex round-1 finding). The closed epic specs (`fn-11`,
`fn-12`, `fn-13`) cite §4.65 / §4.66 / §4.67 in their `## References` /
`## Approach` / `## Done summary` sections. **Do not in-place-rewrite**
the body of those specs: they're historical records of what the epic
shipped under the then-current doctrine. Per `feedback_reality_anchored.md`
— the historical record stays unflinching.

**Branch A only**: append a one-line **errata footer** to each closed-epic
spec. **Wording uses the exact verified mapping from R1 Table 1**
(codex round-18 finding #6 — avoid generic "§4.6x renumbers"
placeholder; cite exact old-topic → new-section mappings):

```
## Errata
- 2026-05-11 (fn-17): CAP 413 §-cites in this spec were authored
  against the then-current Edition 23 numbering. Per fn-17.1's
  primary-source verification (artifact:
  `wiki/data-sources/cap413-edition-24-capture.md`), Ed 24
  (effective 2026-07-01) maps as follows: <exact old §-number →
  new §-number list from Table 1, e.g. "§4.66 (VFR-continue) →
  §4.65; §4.67 (pilot-initiated GA) → §4.66; §4.65 (ATC-initiated
  GA) → §4.67">. Current-doctrine citations live in
  `protocol/.../RegulationDatabase.kt`; this spec's prose is
  preserved as-is for historical fidelity.
```

**Branch B and Branch C**: closed-epic spec files are **NOT touched**.
Branch B's verdict ("Ed 24 retains Ed 23 numbering") means the closed
specs are already current; Branch C's verdict ("PDF unavailable") means
nothing has been verified to update against. In both branches, the
verification artifact (Decision #1) is the sole record of the verdict.

**Active-epic specs** (fn-14 — recently closed but still relevant to G3a
discussion):
- **Branch A**: inline-update the §4.66/§4.67 prose mentions per R7.
  fn-14's `## Deferments register` line for `D-PASS-cap413-edition-24-
  reconciliation` updates to closure status with the verified
  renumberings.
- **Branch A-retire / Branch B / Branch C** (codex round-6/12
  findings): apply a **single one-line closure annotation** to fn-14's
  deferment-register entry (line 404). Wording per branch:
  - Branch A-retire (codex round-12 finding #3): `**D-PASS-cap413-
    edition-24-reconciliation** — CLOSED by fn-17 (2026-05-11): Ed 24
    verified; ATC-initiated GA phraseology no longer has a direct
    mechanical citation target; doctrine-anchor removal deferred to
    `D-PASS-cap413-edition-24-retired-atc-ga-phraseology`.`
  - Branch B: `**D-PASS-cap413-edition-24-reconciliation** — CLOSED by
    fn-17 (2026-05-11): Ed 24 verified to retain Ed 23 §-numbering; no
    rename needed.`
  - Branch C: `**D-PASS-cap413-edition-24-reconciliation** — CLOSED by
    fn-17 (2026-05-11): Ed 24 PDF unverifiable at task time;
    edition-string corrected; rename deferred to
    `D-PASS-cap413-edition-24-rename-pending-pdf`.`
  No other lines in fn-14 are touched in any of these three branches.

### 6. Test fixture impact — string-match audit

**Decided.** Tests pin regdb entry IDs in several places (per grep):

- `controller/.../ObstructionGoAroundSpec.kt:424-425` — asserts
  `regs.containsAll(listOf("§7.4.1.4.1", "§8.9.6.1.8", "§4.65"))` —
  **string-literal §-number pin**, updates to whatever Ed 24 says ATC-
  initiated GA's §-number is.
- `controller/.../ObstructionContinueApproachSpec.kt:924-925` — asserts
  `"§4.65" !in regs` — **string-literal absence pin**, updates to whatever
  Ed 24's ATC-initiated-GA section number becomes.
- `sim/.../G3aRunwayObstructionContinueApproachTest.kt:797` — asserts
  `RegulationDatabase.CAP413_4_65 !in companionRespond.trace.regulations`
  — **typed-reference pin**, updates only if Decision #2 renames the ID.

Audit grep at task time (codex round-28 finding #7 — KMP module paths,
not bare `src/`): `rg -n '"§4\.6[5-8]"|CAP413_4_6[5-8]' protocol/src pilot/src controller/src sim/src --glob '!**/build/**'`.
Every match is enumerated as an acceptance-criterion file path (R6 below).

**No new tests** are added in this epic. The existing pins are mechanically
updated.

### 7. Edition-string correction — coupled to numbering verdict (NO Ed 24 numbers with Ed 23 metadata)

**Decided** (refined per codex round-3 critical finding). Every CAP 413
entry in `RegulationDatabase.kt` currently carries
`edition = "27th ed. (2023)"`. This is wrong regardless of any
renumbering — CAP 413's edition numbering is 1..24, not 27.

**Citation-triple invariant** (load-bearing per codex round-3 critical
finding): a `RegulationRef` carries `(document, edition, section)` as a
**single coherent citation triple**. The `section` and `edition` fields
**must agree** — i.e., we cannot ship "§4.65 / Edition 23" labelled
content while §4.65 in Edition 23 means ATC-initiated GA but the
principle text describes VFR-continue (which is Edition 24's §4.65).

**Fix in-place via named constant** (mirrors
`RegulationRef.ICAO_4444_EDITION` / `SERA_EDITION` / `ICAO_9432_EDITION`
pattern). Introduce `RegulationRef.CAP_413_EDITION`. Value is
**coupled to the numbering verdict**:

- **Branch A (Ed 24 renumbering confirmed)**: set
  `CAP_413_EDITION = "Edition 24 (effective 1 July 2026)"` **at the same
  commit** that renumbers `section` fields. The triple stays coherent:
  every renamed entry is Ed 24 numbering with Ed 24 metadata. This means
  the codebase cites next-effective doctrine starting from the
  fn-17 merge; Ed 23 cited content (e.g. fn-12 ship-time KDoc) is either
  updated in Step 4 prose sweep OR explicitly retained as historical
  with comment marker per Decision #4. **The codebase becomes Ed 24-
  coherent**, ahead of Ed 24's 2026-07-01 effective date. Acceptable per
  `feedback_reality_anchored.md`: doctrine moves; cite what's
  next-current; mark historical artifacts as historical.
- **Branch A-retire** (Ed 24 retires ATC-initiated-GA phraseology):
  set `CAP_413_EDITION = "Edition 24 (effective 1 July 2026)"`
  applied to **most** CAP413_* entries (whose Ed 24 content is
  positively verified — every CAP413_* entry **except**
  `CAP413_4_65`). **Exception** (codex round-14 critical finding —
  citation-triple coherence): `CAP413_4_65` keeps an **inline
  literal** `edition = "Edition 23 Corr (effective 21 January
  2021)"` — bypassing the `CAP_413_EDITION` constant — because Ed 24
  has retired the ATC-initiated-GA phraseology section this `val`
  describes. Applying Ed 24 metadata to Ed-23-only content would
  produce a false triple. KDoc on `CAP413_4_65` notes this
  intentional inline-literal exception and points at
  `D-PASS-cap413-edition-24-retired-atc-ga-phraseology` for the
  doctrine-anchor-removal epic that will complete the migration.
- **Branch B (Ed 24 retains Ed 23 numbering)**: set
  `CAP_413_EDITION = "Edition 24 (effective 1 July 2026)"` because
  Branch B explicitly verified Ed 24's content. `section` fields stay
  as-is (Ed 24's §-numbers == Ed 23's §-numbers). Triple coherent: Ed
  24 numbering + Ed 24 metadata.
- **Branch C (Ed 24 PDF unavailable)**: set
  `CAP_413_EDITION = "Edition 23 Corr (effective 21 January 2021)"`.
  `section` fields stay as-is (Ed 23 numbering, never touched in this
  branch). Triple coherent: Ed 23 numbering + Ed 23 metadata. File
  `D-PASS-cap413-edition-24-rename-pending-pdf` to revisit on/after
  2026-07-01.

The previous plan-draft proposed pinning to Ed 23 metadata even when
Ed 24 numbering applies (Branch A). That is rejected per codex round-3:
producing `"§4.65 / Edition 23"` triples for content that actually means
"§4.65 in Edition 24" silently corrupts the citation surface. **No
mixing**.

**No TODO comment in source** (codex round-1 standing rule). The
Ed-23-Corr → Ed-24 effective-date switch from Branch C is tracked solely
as `D-PASS-cap413-edition-24-rename-pending-pdf`.

This sub-scope **ships in all branches** — the edition string is
factually wrong today regardless of any renumbering verification.

### 8. Scope discipline — what touches code vs what stays prose

**Decided.** The work splits along three surfaces:

- **Code surface** (RegulationDatabase IDs, KDoc cites, test string pins,
  Kotlin imports): mechanical sweep, type-checked by the compiler + grep.
- **Wiki + design-decisions + STRATEGY.md + AGENTS.md prose**: replace
  §-number references in **current-doctrine descriptive prose**; leave
  alone in **historical-record paragraphs** (e.g. "fn-12 shipped citing
  §4.65" stays — that was the doctrinally-correct ID at fn-12 ship time).
- **`.flow/specs/` closed-epic specs**: errata footer only **in Branch A**
  (Decision #5). Not touched in Branch B/C.

**Audit at task time** finds the boundary; planning enumerates the file
list in Acceptance R5-R8.

### 9. Plan as 1 task — single mechanical sweep, with stop-gate

**Decided.** Baseline is one task `fn-17.1`. Could split if Task .1 Step 1
reveals depth (e.g. Ed 24 renumbers >2 sections), but baseline is single-
task per user brief.

If Branch A from Decision #1 fires and Task .1 Step 1 reveals **>3 unrelated
section renumberings**, **stop and split**: file Task .2 for the additional
sections rather than expanding .1. Hard scope-creep gate.

## Acceptance

- **R1 (branch-aware):** Task .1 Step 1 produces a primary-source
  verification artifact `wiki/data-sources/cap413-edition-24-capture.md`
  whose required fields depend on which branch fires:
  - **Branch A, Branch A-retire, or Branch B** (Ed 24 PDF retrievable):
    (a) source URL, (b) SHA-256 of captured PDF, (c) section
    titles + ≤ 1-sentence identifying excerpts for §4.65, §4.66, §4.67,
    §4.68 (NOT paragraph-length quotes — Decision #1 licence gate),
    (d) **content-review record** for §4.46, §4.49, §4.51, §4.53,
    §4.55, §4.56, §2.7 (codex round-10 finding #3 — title-only is
    insufficient when edition metadata changes; the existing
    `RegulationDatabase.kt` `principle` summary for each entry must
    be checked against the Ed 24 section's content). Each row: section
    title + classification (`UNCHANGED — principle remains valid` /
    `REFINED — principle must be rewritten` / `RETIRED — deferment
    filed`) + ≤ 1-sentence identifying excerpt only when REFINED or
    RETIRED. Each section listed in (c) and (d) carries one of three
    classifications — `RENUMBERED` (cite new §-number), `UNCHANGED`
    (verbatim section-title + topic + principle-summary match Ed 23
    within meaning-preserving tolerance), or `REFINED` (meaning
    differs from Ed 23; principle string must be rewritten in
    summary form per Decision #3). **Ed 23 PDF is mandatory for
    Branch A/B/A-retire selection** (codex round-22 finding #4 —
    Ed 23 comparison cannot be `UNREVIEWED` if branch verdict is
    `UNCHANGED`-claiming): the Ed 23 PDF served by `caa.co.uk/
    publication/download/27609` at planning capture time
    (SHA `f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21
    e746e7`) is the comparison source; either re-pull at task time
    OR reference the planning-time SHA. **If Ed 23 PDF is
    unavailable AND planning-time SHA reference cannot be used**
    (rare double-failure; codex round-22/23 finding cascade): task
    halts — no Branch A / A-retire / B can be selected without an
    Ed 23 comparison source (the citation-triple gate becomes
    unverifiable). File `D-PASS-cap413-edition-23-comparison-
    unavailable` and stop fn-17.1 pending Ed 23 source recovery.
    **No codebase-string fallback** (codex round-12/15/22 finding
    cascade — codebase strings are NOT an authoritative proxy). (e) Ed 24
    edition-masthead + effective-date strings, (f) docs-scout
    hypothesis reproduced + Verdict (confirmed / refuted), (g) local
    extraction procedure (`pdftotext -layout ... | grep ...` one-
    liner) for re-derivation, (h) **mapping table** from "old Ed 23
    §-number + topic" → "new Ed 24 §-number + topic" covering §4.65 /
    §4.66 / §4.67 / §4.68 (drives R3-R6 acceptance per the mapping —
    codex round-2 finding #3).
  - **Branch C** (Ed 24 PDF unavailable / endpoint serves Ed 23 still):
    (a) every URL attempted, (b) observed returned edition masthead at
    each URL, (c) SHA-256 of the **Ed 23 PDF served as the active
    citation source** (codex round-4 finding #4 — Branch C pins
    `CAP_413_EDITION = "Edition 23 Corr ..."` so the artifact must
    document the Ed 23 PDF as a first-class citation source, NOT just
    "whatever the endpoint served"). Capture: source URL, SHA-256,
    masthead string, effective-date string. (d) capture date, (e) the
    reason Ed 24 section captures could not be produced (e.g. "CAA
    endpoint `download/27609` returned Ed 23 effective 2021-01-21;
    CAP 413 landing page still advertises Ed 24 effective 2026-07-01
    but download link unchanged"), (f) docs-scout hypothesis reproduced
    + Verdict (unverifiable), (g) the deferment ID filed for re-attempt
    (`D-PASS-cap413-edition-24-rename-pending-pdf`), (h) Branch-C
    mapping table: `RegulationDatabase` `section` fields stay as
    current values; only `edition` metadata is touched. **No content
    classification rows needed for Branch C** (no Ed 24 source to
    compare against).
- **R2:** Decision-tree branch from Decision #1 captured in
  `wiki/data-sources/cap413-edition-24-capture.md` § Verdict, with
  the selected branch (A / A-retire / B / C) named and rationale.
  **Authoritative R-firing-by-branch table** (codex round-9/15/17/19
  finding cascade — single source of truth):

  | Req | A | A-retire | B | C |
  |-----|---|----------|---|---|
  | R1  | ✓ | ✓        | ✓ | ✓ |
  | R2  | ✓ | ✓        | ✓ | ✓ |
  | R3  | ✓ | no-op    | no-op | no-op |
  | R4  | ✓ | no-op    | no-op | no-op |
  | R5  | ✓ | no-op    | no-op | no-op |
  | R6  | ✓ | no-op    | no-op | no-op |
  | R7  | ✓ (inline rewrite) | ✓ (B/C-arm annotation) | ✓ (B/C-arm) | ✓ (B/C-arm) |
  | R8  | ✓ | no-op    | no-op | no-op |
  | R9  | ✓ | ✓        | ✓ | ✓ |
  | R10 | ✓ | ✓        | ✓ | ✓ |
  | R11 | ✓ | ✓        | ✓ | ✓ |
  | R12 | no-op | no-op | ✓ | no-op |
  | R13 | no-op | ✓     | no-op | no-op |

  **Branch B-unverified-comparison sub-case** (codex round-22/23
  finding cascade — Ed 23 PDF unretrievable AND planning-time SHA
  unusable AT TASK TIME, AND Ed 24 PDF is retrievable): branch
  table does NOT include this as a first-class branch — instead,
  Step 1 fails to select any of A/A-retire/B until Ed 23
  comparison is established. File `D-PASS-cap413-edition-23-
  comparison-unavailable` and **task halts** until Ed 23 source is
  recovered. Without Ed 23 comparison, no branch claiming
  "UNCHANGED" can be made; the citation triple gate becomes
  unverifiable. The previous draft's "Branch B-unverified-
  comparison" sub-branch is rejected per round-23 finding #1 — it
  would silently demote citation discipline.
- **R3 (Branch A only):** `protocol/.../RegulationDatabase.kt` — **only
  typed entry** in scope is `CAP413_4_65` (Decision #2 — codex round-3
  finding). Per R1 (h)'s mapping table: rename `CAP413_4_65` to
  `CAP413_4_<new>` only if Ed 24 moves ATC-initiated GA phraseology to
  a different §-number; retire only if Ed 24 retires the section. If
  the §-number stays unchanged (Branch A may still confirm renumbering
  of §4.66/§4.67 prose-only sections without moving §4.65 itself), the
  `val CAP413_4_65` symbol stays as-is. Principle string refreshed as a
  faithful one-line summary (Decision #3) only if Ed 24's meaning
  differs from Ed 23. Title field refreshed to match if needed. KDoc
  retained with section-content cross-reference. **§4.66, §4.67, §4.68
  prose-only references are NOT touched by R3** — they are handled in
  R5 (source prose sweep) and R6 (test prose sweep).
- **R4 (Branch A only):** All `import` lines for the renamed `CAP413_4_X`
  symbols updated. Files touched: `controller/.../TowerArrival.kt`,
  `controller/.../Controller.kt`. Compile-clean (verified by R11 full
  verify, NOT separate compile target invocations).
- **R5 (Branch A only — full prose sweep; Branch B handles stale
  hypothesis prose separately per R12):** All prose `§4.65` / `§4.66`
  / `§4.67` / `§4.68` mentions in **current-doctrine descriptive
  contexts** classified against R1 (h)'s mapping table and updated to
  their new §-numbers per the mapping. **NOT just §4.66/§4.67** —
  every `§4.6[5-8]` match is classified (codex round-2 finding #3).
  Old §4.65 references (ATC-initiated GA topic) map to whatever Ed
  24's new ATC-initiated-GA §-number is; old §4.68 references
  (military) map similarly. Files (verified at planning grep — Task
  .1 Step 4 re-runs grep across `§4\.6[5-8]` pattern):
  - `pilot/.../Pilot.kt` (lines 850, 958, 1017)
  - `pilot/.../PilotMission.kt` (lines 62, 740, 758)
  - `pilot/.../PilotCognitive.kt` (line 606)
  - `pilot/.../observe/PilotEvent.kt` (line 75)
  - `controller/.../procedure/TowerArrival.kt` (line 243, 340)
  - `controller/.../bdi/Action.kt` (lines 84, 86, 303, 350)
  - `controller/.../Controller.kt` (lines 923, 926)
  - `wiki/domain/aviation-world.md` — search and update
  - `wiki/design-decisions/2026-04-22-root-cause-go-around-and-totality.md`
    — search and update (current-doctrine paragraphs only)
  - `wiki/design-decisions/2026-04-15-controller-architecture.md` —
    search and update
  - `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md`
    — search and update
  - `STRATEGY.md` — search and update
  - `AGENTS.md` — search and update (lines 217, 270, 325, 391)
- **R6 (Branch A only):** All `§4.65` / `§4.66` / `§4.67` / `§4.68`
  mentions in **test KDoc / Test message strings / string-literal pins**
  classified against R1 (h)'s mapping table and updated per the mapping.
  Files:
  - `pilot/src/commonTest/.../PilotCrosswindTickATickBTest.kt`
  - `pilot/src/commonTest/.../PilotAtcInitiatedGoAroundSpec.kt`
  - `pilot/src/commonTest/.../PilotCrosswindGoAroundTest.kt`
  - `pilot/src/commonTest/.../PlannedGoAroundSpec.kt`
  - `controller/src/commonTest/.../ObstructionGoAroundSpec.kt` (incl
    string-literal pin at line 424-425)
  - `controller/src/commonTest/.../ObstructionContinueApproachSpec.kt`
    (incl string-literal pin at line 924-925)
  - `sim/src/jvmTest/.../G3aRunwayObstructionTest.kt` (lines 239, 673,
    732)
  - `sim/src/jvmTest/.../G3aPilotTrainedGoAroundTest.kt` (lines 105, 166,
    168)
  - `sim/src/jvmTest/.../G3aRunwayObstructionContinueApproachTest.kt`
    (lines 205, 774, 789, 797-798)
  - `sim/src/jvmTest/.../G3aPilotReactiveCrosswindTest.kt` (line 193)
- **R7:** `.flow/specs/fn-14-g3a-react-pilot-reactive-go-around-on.md`
  treatment per branch:
  - **Branch A**: inline-update §4.66/§4.67 prose mentions at lines
    22 / 154 / 356 / 404 to new numbering; update its
    `D-PASS-cap413-edition-24-reconciliation` deferment entry at
    line 404 to reflect closure by this epic.
  - **Branch B** (codex round-6/20/21 finding cascade): single
    line-404 closure annotation per Decision #5 PLUS any additional
    inline edits at fn-14 lines that R12's grep actually matches
    (hypothesis/deferment phrasing only — `'docs-scout caught
    Edition 24 renumbered|Ed 24 renumbered §4\.6|D-PASS-cap413-
    edition-24-reconciliation'`). Per planning grep, line 22 of
    fn-14 carries the docs-scout-claim hypothesis prose and is in
    scope; line 356 carries the "verify against Edition 23.1 vs 24
    numbering at task time" hedge and is in scope; **line 154
    carries normal `per CAP 413 §4.67` doctrinal phrasing and is
    NOT touched in Branch B** (codex round-21 finding #4) — current-
    doctrine numbering is Branch B's confirmed status quo. R7 + R12
    apply only where the grep matches; otherwise no edit.
  - **Branch A-retire / Branch C** (codex round-6 finding #4):
    apply only the single line-404 closure annotation. No other
    fn-14 edits.
- **R8 (Branch A only):** Errata footers appended (NOT in-place rewrites,
  per Decision #5) to:
  - `.flow/specs/fn-11-g3a-single-aircraft-pilot-trained-vfr.md`
  - `.flow/specs/fn-12-g3a-obstruction-single-aircraft-atc.md`
  - `.flow/specs/fn-13-g3a-obstruction-continue-approach-three.md`
  **Branch B and Branch C**: these files are NOT touched; the
  verification artifact alone records the verdict.
- **R9 (all branches — Branch A/B/C):** `RegulationRef.CAP_413_EDITION`
  named constant introduced at `RegulationRef`'s declaration site
  (mirrors `ICAO_4444_EDITION` / `SERA_EDITION` / `ICAO_9432_EDITION`
  pattern). Every existing CAP 413 entry's
  `edition = "27th ed. (2023)"` literal replaced with
  `edition = RegulationRef.CAP_413_EDITION`. **Constant value coupled
  to branch verdict** (Decision #7 — codex round-3/4/14 critical
  finding cascade):
  - **Universal hard gate** (codex round-15/16/17/18 finding cascade —
    applies in Branch A, A-retire, and B; Branch C never moves to Ed
    24 metadata so the gate is vacuous there): every CAP413_* typed
    entry's `edition` field uses `RegulationRef.CAP_413_EDITION`
    (Ed 24 value) **only if its Table 2 row is one of**:
    `UNCHANGED` / `RENUMBERED` (section field updated in-task) /
    `REFINED` (principle string updated in-task). Any other Table 2
    classification — `RETIRED` (Ed 24 retires the section) or
    `REFINED` (not updated in-task) or `UNREVIEWED` (Ed 23
    comparison source unavailable) — **keeps an inline literal**
    `edition = "Edition 23 Corr (effective 21 January 2021)"` with
    explanatory KDoc, and files a per-entry deferment
    (`D-PASS-cap413-edition-24-retired-<n>` or `-refined-<n>` or
    `-unreviewed-<n>`). This invariant holds across all branches:
    no Ed 24 metadata on unverified or stale-principle content.
  - Branch A or Branch B: per the universal hard gate above —
    `"Edition 24 (effective 1 July 2026)"` applied where the gate
    permits, inline-literal exception elsewhere.
  - Branch A-retire: same universal hard gate, with the most-likely
    RETIRED row being `CAP413_4_65` (ATC-initiated GA).
  - Branch C: `"Edition 23 Corr (effective 21 January 2021)"`,
    applied to every CAP413_* entry (Ed 23 numbering + Ed 23
    metadata — triple coherent without invoking the hard gate).
  - **Audit completeness check** (codex round-16 finding #5):
    Table 2 must include a row for every CAP413_* `val` declared in
    `RegulationDatabase.kt` (verified by
    `grep -n "val CAP413_" protocol/.../RegulationDatabase.kt`
    against Table 2's row list). If Table 2 misses an entry, the
    artifact is incomplete and R1 fails.
  **No Ed 24 numbering with Ed 23 metadata; no Ed 23 numbering with
  Ed 24 metadata.** No TODO comment in source. Branch-C future-
  switch tracked as `D-PASS-cap413-edition-24-rename-pending-pdf`;
  Branch-A-retire doctrine-anchor-removal tracked as
  `D-PASS-cap413-edition-24-retired-atc-ga-phraseology`.
- **R10 (all branches):** **Repo-root `.plan` is canonical** per
  `AGENTS.md:422-450` + codex round-4 finding #3. Update `.plan`
  directly: close `D-PASS-cap413-edition-24-reconciliation` (mark
  `DONE` per `.plan` maintenance rules) AND file any new deferments
  surfaced by this epic into `.plan` (`D-PASS-cap413-edition-24-rename-
  pending-pdf` if Branch C — single consolidated Branch-C deferment;
  `D-PASS-cap413-edition-24-<section>` if Step 1 surfaces additional Ed
  24 changes; `D-PASS-cap413-principle-text-deep-refresh` if Step 1
  surfaces semantic shifts). **Sister register** `~/.claude/plans/pilot-
  firewall.md` (per `.plan:484` register-split convention — off-repo
  long-running pilot-firewall design doc; pre-dates `.plan` but
  coexists; carries an identical copy for D-PF/D-AUDIT/D-PASS items)
  is updated when reachable. `.plan` is the load-bearing record; the
  sister register is best-effort. Closure / new-entry payload — **one
  clean rule** (codex round-10 finding #4):
  - **Primary commit** (the implementation commit) records: branch
    verdict + PDF SHA in the commit message body. `.plan` is updated
    in the same primary commit with branch verdict + PDF SHA + the
    pointer string `"see .flow/tasks/fn-17-cap-413-edition-24-
    numbering.1.md ## Evidence"`. **Task `## Evidence` records
    everything except the final SHA at this point** (gradle output,
    grep audit, register-touch list).
  - **Follow-up metadata commit** (codex round-13 finding #2 —
    coherent mechanism, breaks the chicken-and-egg cleanly): after
    the primary commit lands, populate `## Done summary` +
    `## Evidence`'s `Commits:` line with the primary SHA. **Preferred
    mechanism** (codex round-18/25 finding cascade — preferred, with
    fallback): `flowctl done fn-17-cap-413-edition-24-numbering.1
    --summary-file <md> --evidence-json <json>`. **Fallback** if
    `flowctl done` is unavailable in the implementer's environment
    (Step 0 preflight detects this): directly Edit
    `.flow/tasks/fn-17-cap-413-edition-24-numbering.1.md` (the
    repo-state outcome — populated summary + evidence — is what
    R10b acceptance gates on; the mechanism is operationally
    interchangeable, codex round-25 finding #3). Create a small
    follow-up commit titled `fn-17.1: record commit SHA in task
    evidence`. NOT a `git commit --amend` cycle. The
    primary commit stays unmodified; the follow-up commit is a
    one-line metadata addition.
  - `## Evidence` also records which registers were touched (always
    `.plan`; `~/.claude/plans/pilot-firewall.md` if reachable,
    unreachable-fallback note otherwise).
- **R13 (Branch A-retire only — codex round-17/19 finding cascade —
  binding criterion for Step 4a):** Narrow `§4.65` / `CAP413_4_65`
  prose audit across protocol, pilot, controller, sim, wiki,
  AGENTS.md, STRATEGY.md, .flow/specs/ (excluding closed-epic
  fn-11/12/13 specs). Run `rg -n '§4\.65|CAP413_4_65' protocol
  pilot controller sim wiki AGENTS.md STRATEGY.md .flow/specs
  --glob '!**/build/**'`. For each active occurrence: annotate the
  cite with `(Ed 23 anchor retained pending
  D-PASS-cap413-edition-24-retired-atc-ga-phraseology)`, OR record
  in artifact's § Retained historical cites with reason. Acceptance
  shape: every grep hit classified (annotated inline or recorded in
  artifact); zero unclassified hits. Branch A / Branch B / Branch C:
  R13 no-op (Branch A handles via R5; B/C don't retire §4.65).
- **R12 (Branch B only — codex round-9/19 finding cascade):** Stale
  docs-scout-hypothesis prose **across all active surfaces, including
  fn-14** (per Decision #1 Branch B handling; codex round-19 finding
  #1 — fn-14 not excluded). Run `rg -n 'docs-scout caught Edition
  24 renumbered|Ed 24 renumbered §4\.6|D-PASS-cap413-edition-24-
  reconciliation' .flow/specs/ wiki/ AGENTS.md STRATEGY.md`. For
  each active occurrence: edit the prose single-line to reflect the
  verified Branch-B verdict (Ed 24 retains Ed 23 numbering), OR
  record in the verification artifact's § Retained historical cites
  why the prose should remain unchanged. Default: edit. **fn-14
  receives both the line-404 closure annotation (R7) AND inline
  edits to lines 22/154/356 if those carry the stale hypothesis
  prose** — they're the same physical edit pass. Closed-epic flow
  specs (fn-11/12/13) not touched (no docs-scout phrasing per
  planning grep). **Branch A and Branch C**: R12 is a no-op (Branch
  A rewrites hypothesis prose to verified verdict in R5/R7; Branch C
  cannot verify so the hypothesis prose stays as historical record
  in the artifact's § Retained historical cites).
- **R11:** `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest
  :core:allTests :protocol:allTests detekt` exits 0. **All eight golden
  tests** (G0 / G1 / G1-min / G2 / G3a-trained / G3a-obstruction /
  G3a-obstruction-continue-approach / G3a-react) GREEN. detekt baseline
  unchanged. No new failures from rename or edition-string churn. This
  is the **authoritative verification command** — do not invent
  intermediate per-module compile targets (codex round-1 finding).
  **Step 0 preflight** (codex round-18/23 finding cascade): if
  `:sim` is missing at Step 0, R11 is **blocked by repository/
  module mismatch** — NOT a fn-17.1 implementation failure (codex
  round-23 finding #5 — accountability classification matters for
  review). Per `feedback_no_corners.md`, no silent substitution.
  The implementer either: (i) recovers the `:sim` module if it was
  removed in error, OR (ii) records the missing module as an
  unrelated build-break repo-state blocker, stops fn-17.1, and
  surfaces the issue separately. Fixed fail-loud behaviour; no
  substitution path.
  **Eight-golden evidence requirement** (codex round-5 finding #6):
  `## Evidence` records either the Gradle test-report path
  (`<module>/build/reports/tests/jvmTest/index.html`) confirming the
  eight named test classes ran AND passed, OR the per-test
  `./gradlew ... --tests "*LowgGoldenTest*" --tests "...*G3a*..."` etc.
  command output enumerating each golden by name. The "8 goldens
  GREEN" claim must be auditable from `## Evidence` without
  re-running the suite.
  **Failure handling (codex round-2 finding #5)**: any test or task
  failure must be recorded in `## Evidence` with exact failing
  test/task output, the failing-test name, and full stderr. Failures
  cannot be waived as "pre-existing flakiness" unless: (a) the failure
  reproduces on master at the merge-base SHA without this epic's
  changes (record the bisect evidence in `## Evidence`), AND (b) the
  pre-existing flakiness is filed as a new deferment (e.g.
  `D-PASS-flaky-test-<name>`). No silent skip / silent retry / silent
  test exclusion — per `feedback_no_corners.md`.

## Strategy drift flagged for review

_(none — citation hygiene aligns with the Runtime simulator track and the
reality-anchored modelling principle.)_

## Quick commands

```bash
./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
# Repo-wide audit (codex round-3 finding #4 — broader than initial scope):
rg -n '§4\.6[5-8]|CAP413_4_6[5-8]|27th ed\.|Edition 23' --glob '!**/build/**' --glob '!**/node_modules/**' --glob '!**/.gradle/**'
# Or with find+grep equivalent if rg unavailable; covers docs/ core/ migration/ .flow/tasks/ etc.
pdftotext -layout cap413-ed24.pdf cap413-ed24.txt && grep -nE '^[[:space:]]*4\.(46|49|51|53|55|56|65|66|67|68)|^[[:space:]]*2\.7[[:space:]]' cap413-ed24.txt
sha256sum cap413-ed24.pdf
```

## Approach

### Single-task plan

`fn-17.1 — primary-source verification + conditional rename + edition-string
correction`. Steps:

1. **Step 1 (load-bearing gate)**: pull Ed 24 PDF from CAA, capture SHA-256
   in `wiki/data-sources/cap413-edition-24-capture.md` (with licence-
   bounded short excerpts per Decision #1), verify the docs-scout
   hypothesis, select branch A/B/C. If Branch C (PDF unavailable),
   commit only the verification artifact + R9 sub-scope, open follow-up
   deferment, close.
2. **Step 2 (if Branch A)**: rename entry IDs in `RegulationDatabase.kt`
   per Decision #2. Compiler catches every consumer; `git grep`
   enumerates.
3. **Step 3 (if Branch A)**: refresh title / principle strings per
   Decision #3 (faithful one-line summaries, not verbatim quotes).
4. **Step 4 (if Branch A)**: prose-fix every current-doctrine §-number
   reference across source / wiki / AGENTS.md / STRATEGY.md (R5, R6).
   Active flow spec (fn-14) inline-update (R7). Closed flow specs (fn-11,
   fn-12, fn-13) errata footer (R8). **Branch B/C**: skip Step 4
   entirely.
5. **Step 5 (if Branch A)**: update test string-literal pins per R6.
6. **Step 6 (all branches)**: introduce `RegulationRef.CAP_413_EDITION`
   constant + rewrite all entries (R9). **Constant value coupled to
   branch verdict** per Decision #7 (Branch A/B → Ed 24; Branch C →
   Ed 23 Corr; codex round-3/4/6 critical-finding cascade — citation
   triple coherence). Branch-C future-effective-date switch filed as
   `D-PASS-cap413-edition-24-rename-pending-pdf`.
7. **Step 7**: full verify per R11.
8. **Step 8**: deferment closure note in `## Done summary` (R10) with
   commit-SHA placeholder.

### Reuse points

| Surface | Reuse | New code |
|---------|-------|----------|
| `RegulationRef` data class | `protocol/.../RegulationModel.kt` (exists; verify exact file at task time) | New `CAP_413_EDITION` companion constant |
| `ICAO_4444_EDITION` / `SERA_EDITION` / `ICAO_9432_EDITION` constants | Existing in `RegulationModel.kt` companion | Mirror for CAP 413 |
| Compiler-driven rename | Kotlin compiler catches every consumer of renamed `val` | No new tooling |
| `wiki/data-sources/` provenance pattern | Existing dir; planning grep contains `lowg.md`, `ljmb.md`, `identifier-reconciliation.md`, `overview.md`, `requirements-source-units.md` | New `cap413-edition-24-capture.md` |
| Errata-footer pattern | NEW for this epic | Single 3-line footer per closed-epic spec (Branch A only) |
| Edition-constant pattern | `ICAO_4444_EDITION = ...` etc. | `CAP_413_EDITION` constant |

## Test notes

**No new tests.** The existing pins are mechanically updated. Branch A
acceptance gates on existing test suite GREEN post-rename. Branch B/C
acceptance gates on existing test suite GREEN post-edition-string churn
(stricter than typical no-op — edition-string change touches every CAP 413
entry's `RegulationRef` instance equality, so any test that compares full
`RegulationRef` objects rather than just `.section` will see a diff).

**Existing test pins audited for the edition-string change**:
- `controller/.../ObstructionGoAroundSpec.kt:424` compares `.section` only —
  unaffected by edition-string change.
- `controller/.../ObstructionContinueApproachSpec.kt:924` compares `.section`
  only — unaffected.
- `sim/.../G3aRunwayObstructionContinueApproachTest.kt:797` compares
  reference equality via `!in` over `List<RegulationRef>` — affected only
  if a stale `RegulationRef` snapshot is compared; verify at task time.

If any test compares full `RegulationRef` object equality (rather than just
the section field), that's an existing test design issue surfaced — not a
defect introduced by this epic — but the rename still needs to land green.
Acceptance gate: R11 full verify GREEN.

## Review considerations

### FP / type-safety axis

- Pure rename — no signature changes, no new sealed cases.
- `CAP_413_EDITION` constant pattern matches existing FP convention (named
  literals over inline strings; single point of change).
- Compiler-driven rename is type-safe by construction; no string-match
  rewrites at the import surface.
- No new partial functions, no new total-by-type-shape concerns.
- No silent deferred-action TODOs in source — the Ed-24-effective-date
  switch is a deferment artifact, not a code comment.

### Test architecture axis

- No new tests; existing pins mechanically updated.
- Branch-A test impact: 3 test files touched at string-literal pin sites
  + 7 test files touched at KDoc/message-string sites (R6).
- Branch-B/C test impact: 0 test code touched (only RegulationDatabase
  edition-string churn).
- Eight golden tests gate every branch via R11.

### Impact axis

- **Branch A worst case**: ~25 files touched across protocol / pilot /
  controller / sim / wiki / AGENTS.md / STRATEGY.md / 4 flow specs.
- **Branch B**: ~3 files touched (RegulationDatabase + RegulationModel +
  wiki provenance artifact).
- **Branch C**: ~3 files touched (RegulationDatabase + RegulationModel +
  wiki provenance artifact).
- No code behaviour change in any branch. Test green is guaranteed
  modulo the test-design audit in `## Test notes`.
- Migration cost: zero runtime impact. Compile-time only.
- Anti-corruption layer: stays intact — no protocol shape change.
- **Licence**: capture artifact uses ≤ 25-word excerpts plus
  section titles plus paraphrases — per CAP 413 reproduction terms
  verified at task time (codex round-25 finding #6 — no OGL v3
  assumption). No paragraph-length redistribution.

### Operational axis

- Determinism: unaffected.
- Replay / observability: `DecisionTrace.regulations` payloads still emit
  with the same `RegulationRef.section` strings (post-rename), so log
  consumers / trace dumps still parse correctly.
- Performance: zero impact.
- Migration / rollback: trivial — revert is one commit.

## Early proof point

**Task .1 Step 1** is the load-bearing gate. If the Ed 24 PDF fetches clean
and verifies the docs-scout hypothesis, the rest of the epic is mechanical
sweep. If it doesn't, the abort gate fires and the epic closes as
limited-scope (edition-string correction only) with a follow-up
deferment. Both branches close cleanly within one task; no
implementation-time surprises possible because Step 1 is the
disambiguating step.

## References

### Doctrinal

- **CAP 413 Edition 24** (UK CAA *Radiotelephony Manual*) — effective
  2026-07-01, republished 2026-04-21. The 2024-03-28 first-release Ed 24
  was withdrawn for errors. Primary source pulled in Task .1 Step 1.
- **CAP 413 Edition 23 / Edition 23 Corr** — effective 2021-01-21
  through 2026-06-30. Source-of-truth for the current codebase numbering
  as captured at planning time:
  - §4.65 = ATC-initiated missed approach (`BIGJET 347, go around I say
    again go around, acknowledge` — controller; pilot replies `Going
    around, BIGJET 347`)
  - §4.66 = VFR aircraft to continue into the normal traffic circuit
  - §4.67 = Pilot-initiated GA (`G-CD, going around` / ATC: `G-CD, Roger`)
  - §4.68 = Military missed approach (cross-reference to Chapter 10)
- **CAA CAP 413 landing page**: `caa.co.uk/our-work/publications/
  documents/content/cap-413/`
- **Withdrawn Ed 24 (2024-03-28) context**: AOPA UK announcement +
  Andrews' Aviation report — confirms Ed 24 first-release withdrawal
  rationale.
- **CAP 413 reproduction terms** (verified at task time per the PDF's
  front-matter notice — codex round-25 finding #6; do NOT assume OGL
  v3) — basis for
  ≤ 1-sentence-excerpt + section-title capture pattern in the
  verification artifact.

### Codebase prior art

- **fn-14** — filed `D-PASS-cap413-edition-24-reconciliation` deferment
  at its `## Deferments register` line 404; this epic closes that
  deferment.
- **fn-13.1** — established the `CAP_413` edition-string pattern via the
  existing (incorrect) `"27th ed. (2023)"` literal at the new
  `CAP413_4_53` / `CAP413_4_55` / `CAP413_4_56` entries.
- **fn-12.1** — established the `CAP413_4_65` companion-trace-regs
  pattern via the `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` rule's `regulations`
  list. Rename target if Branch A.
- **fn-11** — first uses CAP 413 §4.66/§4.67 cites in prose KDoc; main
  Branch-A target for prose updates.

### Memory

- `feedback_reality_anchored.md` — model real CAP 413 doctrine; deferments
  unflinching; **errata footer over body rewrite** for historical specs.
- `feedback_no_corners.md` — no deprecation shim, no silent compat layer;
  rename + every consumer re-imports. Also: no TODO comments as
  silent-deferred-action markers — use tracked deferments.
- `feedback_world_only_test_triggers.md` — no test-rigging from this
  rename; existing test pins mechanically updated.
- `feedback_plans_review_aware.md` — Review considerations addressed
  inline.
- `feedback_pass_scope.md` — fold edition-string correction into the
  closing pass (independent sub-scope per Decision #7 ships in all
  branches; no follow-up filed for it).
- `feedback_impact_assessment.md` — plan enumerates impact inline per
  Branch A/B/C.

### External (primary-source pull)

- [CAP 413 download endpoint](https://www.caa.co.uk/publication/download/27609) — pulled at task time
- [CAP 413 landing page](https://www.caa.co.uk/our-work/publications/documents/content/cap-413/)
- [AOPA UK CAP 413 Edition 24 announcement](https://aopa.co.uk/news-articles/radiotelephony-manual-cap-413-edition-24-effective-from-28-march-2024) — context for the withdrawn first-release Ed 24

### Captured research (planning-time)

Planning pulled `caa.co.uk/publication/download/27609` on 2026-05-11. The
endpoint served **Edition 23, effective 21 January 2021** (page 1
masthead). Edition 23 §4.65-§4.68 content captured from that PDF
(page 26 of Chapter 4):

- §4.65 — Missed Approach (ATC-initiated GA): `BIGJET 347, go around I
  say again go around, acknowledge` / pilot: `Going around, BIGJET 347`.
- §4.66 — VFR aircraft to continue into the normal traffic circuit.
- §4.67 — Pilot-initiated missed approach: `G-CD, going around` /
  ATC: `G-CD, Roger`.
- §4.68 — Military missed approach phraseology (cross-reference to
  Chapter 10).

This **planning-time capture serves as the Ed 23 comparison source** for
Task .1 Step 1's `UNCHANGED` / `REFINED` classifications, supporting R1
(c)(d). **Planning-time SHA-256** (codex round-19 finding #5 —
recorded so Branch C fallback is executable):
`f3b4839e885cd554740f664a55d3732cd7284789e0b5f808970cfdbc21e746e7`
(captured 2026-05-11 from `caa.co.uk/publication/download/27609`).
Task .1 either re-pulls the PDF and re-derives this SHA, or — only
if both Ed 24 AND Ed 23 paths fail at task time — references this
recorded planning-time SHA as the active citation source for Branch
C's `CAP_413_EDITION` constant.

## Deferments register

Deferments from this epic file in `~/.claude/plans/pilot-firewall.md`
§ Deferments register:

- **`D-PASS-cap413-edition-24-rename-pending-pdf`** — **single
  consolidated Branch-C deferment** (codex round-7 finding #1 —
  previously split into two overlapping deferments; consolidated to
  preserve citation-triple coherence). Filed only when Branch C fires
  (Ed 24 PDF unavailable at task time). Trigger: CAA endpoint serves
  Ed 24 PDF. Action: re-pull, verify renumbering hypothesis, **update
  numbering AND edition metadata together in a single commit** so the
  citation triple stays coherent (no intermediate state with Ed 24
  numbering on Ed 23 metadata or vice versa). Single consolidated
  Branch-C deferment per codex round-7 finding #1 (earlier drafts
  split the work into two parallel deferments; consolidation removes
  ordering ambiguity).
- **`D-PASS-cap413-edition-24-<section>`** (filed if Task .1 Step 1
  finds other Ed 24 changes affecting §4.46 / §4.49 / §4.51 / §4.53 /
  §4.55 / §4.56 / §2.7 — one deferment per section, sized at task time).
- **`D-PASS-doctrinal-edition-reconciliation-non-cap413`** — ICAO Doc
  4444, ICAO Annex 11, SERA, ICAO 9432 edition reconciliation. Each
  source has its own edition history. CAP 413 is the only edition
  reconciled in fn-17.
- **`D-PASS-cap413-principle-text-deep-refresh`** (filed if Task .1 Step
  1's title + excerpt capture reveals semantic-level Ed 24 refinements
  beyond mechanical one-line summary update) — deeper principle-string
  rewrites separated to their own pass with full review.

## Closures

- **`D-PASS-cap413-edition-24-reconciliation`** closed (fn-14 epic
  deferment line 404).
- **CAP 413 edition-string factual error** closed in all branches via R9
  — no longer carries the incorrect `"27th ed. (2023)"` literal.
- **Primary-source verification discipline** established for future
  doctrine-edition reconciliation passes — the Task .1 Step 1 verification
  artifact pattern (PDF SHA + section-title + ≤ 1-sentence-excerpt + local
  extraction procedure + branch verdict) is reusable for the deferred
  ICAO / SERA / Annex passes (and observes OGL v3 / equivalent
  licence terms by not redistributing paragraph-length text).

## Requirement coverage

| Req | Description | Task(s) |
|-----|-------------|---------|
| R1  | Primary-source verification artifact (PDF SHA, titles + ≤ 1-sentence excerpts, extraction procedure, hypothesis verdict) | fn-17.1 |
| R2  | Branch A/B/C verdict captured + downstream criteria gated | fn-17.1 |
| R3  | (Branch A) `RegulationDatabase` entries renamed / retired / refreshed | fn-17.1 |
| R4  | (Branch A) Import lines updated; R11 confirms compile-clean | fn-17.1 |
| R5  | (Branch A) Source + wiki + AGENTS.md + STRATEGY.md prose updates | fn-17.1 |
| R6  | (Branch A) Test KDoc + string-literal pin updates | fn-17.1 |
| R7  | (Branch A) Active flow-spec (fn-14) inline update | fn-17.1 |
| R8  | (Branch A only) Closed flow-spec (fn-11/fn-12/fn-13) errata footer append. Branch B/C: not touched. | fn-17.1 |
| R9  | (All branches) `CAP_413_EDITION` named constant + all entries refactored. **Value coupled to branch verdict** (Branch A/B → Ed 24; Branch C → Ed 23 Corr). | fn-17.1 |
| R10 | (All branches) `.plan` (canonical, repo-root) closure for `D-PASS-cap413-edition-24-reconciliation` + branch-specific new deferments. `## Evidence` carries final commit SHA. | fn-17.1 |
| R11 | Full verify GREEN (8 goldens) — authoritative verification command | fn-17.1 |
| R12 | (Branch B only) Stale docs-scout-hypothesis prose grep + classify across all active surfaces including fn-14 | fn-17.1 |
| R13 | (Branch A-retire only) Narrow §4.65 / CAP413_4_65 prose audit + per-hit classification | fn-17.1 |

## Done summary

_(filled per task during implementation)_

## Evidence

_(filled per task during implementation)_
