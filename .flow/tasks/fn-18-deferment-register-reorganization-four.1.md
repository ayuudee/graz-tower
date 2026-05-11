---
satisfies: [R1, R2, R3, R4a, R4b, R13, R14]
---

# fn-18.1 — Convention + scaffolding: docs/deferments.md + CONVENTION + AGENTS.md pointer + memory rewrite

## Description

Lays the foundation surface for the four-bucket deferment register reorganization. Writes the empty (header + schema + Archive stub) `docs/deferments.md` map, the full `docs/deferments-CONVENTION.md` decision-tree doc, updates `AGENTS.md` with a discovery pointer, rewrites the `reference_audit_registers.md` auto-memory entry, and files the three meta-deferments (`D-PASS-deferments-map-tooling-automation`, `D-PASS-deferments-renumbering-discipline`, `D-PASS-deferments-cross-ref-from-impl-review`). No migrations from existing sources yet — fn-18.2 / .3 handle those.

The success criterion is: a reader who reads `docs/deferments-CONVENTION.md` and the empty-bodied `docs/deferments.md` can predict the **shape** of what fn-18.2 will produce when populating the map. If they can't, the convention is unclear and this task fails review.

## Problem

Today the convention for filing a deferment is implicit — embedded in `~/.claude/plans/pilot-firewall.md`'s schema (4 fields: What today / Why wrong / Real-fix contract / Trigger) and in the ad-hoc `## Deferments register` sections of fn-14 / fn-15 / fn-17 epic specs (different schema, different fields). The `DeferredContractsSpec.kt` pattern exists in `:pilot` only and isn't documented as a project doctrine. No in-repo doc exists explaining "where do new deferments go." This task establishes that doctrine before any migration moves IDs around.

## Files (read or modify)

- **READ**
  - `/home/andrew/dev/projects/twr2/AGENTS.md` — current structure for the discovery-pointer insertion site.
  - `/home/andrew/dev/projects/twr2/.flow/specs/fn-14-g3a-react-pilot-reactive-go-around-on.md` § Deferments register — current epic-spec schema (input to convention doc's bucket-3 worked example).
  - `/home/andrew/dev/projects/twr2/.flow/specs/fn-18-deferment-register-reorganization-four.md` — the epic spec being implemented (Decisions #1-#15 are the source of truth for the convention doc).
  - `/home/andrew/dev/projects/twr2/pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt` — current placeholder-test pattern (bucket 1/2 worked example).
  - `~/.claude/plans/pilot-firewall.md` § Deferments register — current canonical store (historical secondary in the new model).
  - `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/reference_audit_registers.md` — current auto-memory entry being rewritten.
  - `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/MEMORY.md` — index entry's prose may need a minor refresh.

- **CREATE**
  - `/home/andrew/dev/projects/twr2/docs/deferments.md` — the map. Header explaining the four-bucket model (1 paragraph), schema explanation (showing the locked field order), one-paragraph "How to read this file", prefix subsections at h2 depth (`## D-PF`, `## D-AUDIT`, `## D-PASS`, `## D-WORLD` — per epic Decision #7a, no parent wrapper section) all empty in .1 except for the three meta-deferments filed under `## D-PASS`, and `## Archive` section stub (empty body, one-line "Closed entries use the same `### D-...` heading discipline with minimal closed-body, see CONVENTION").
  - `/home/andrew/dev/projects/twr2/docs/deferments-CONVENTION.md` — full convention doc. Sections required: (1) Overview (1 paragraph naming the four buckets + their canonical stores); (2) Decision tree (per epic Decision #9 — interview-question form); (3) Status taxonomy (4 leaves per Decision #2, with one-sentence definition each); (4) `Pinned at:` field format (per Decision #5, with examples for tests + epics + narrative); (5) Schema for `docs/deferments.md` entries (per Decision #7 — locked field order, mandatory vs conditional fields, `Why:` cap at 3 sentences); (6) Bucket-1 vs bucket-2 worked example showing both shapes (active API vs commented-out future API); (7) Bucket transition convention (per Decision #6 — in-place status updates, no duplicates); (8) Archive policy (per Decision #3 — closed entries move to `## Archive`, lose Why/Blocked-on detail, keep one-line summary); (9) File locations (per Decision #4: `docs/deferments.md` at repo root; per-module `DeferredContractsSpec.kt` files via Decision #1).

- **MODIFY**
  - `/home/andrew/dev/projects/twr2/AGENTS.md` — add one bullet under `# Project Plan` section pointing at `docs/deferments.md`. Add one line under "Self-assessment before review" (item 8 or appended to list) noting deferred work surfaced during a pass must be filed in `docs/deferments.md` before commit. Existing `.plan` references stay; the new pointer **supplements**.
  - `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/reference_audit_registers.md` — rewrite to point at `docs/deferments.md` as primary discovery surface, four-bucket model with one-sentence summaries, `~/.claude/plans/pilot-firewall.md` as historical secondary. Update title/description metadata if applicable.
  - `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/MEMORY.md` — refresh the `reference_audit_registers.md` index line's prose. New summary: e.g. "Deferment register lives in `docs/deferments.md` per the four-bucket model; legacy register in `~/.claude/plans/pilot-firewall.md` § Deferments register kept as historical secondary."

## Approach (numbered Steps)

### Step 1 — Write `docs/deferments-CONVENTION.md`

This is the load-bearing artifact. The convention drives every subsequent migration decision. Sections in the order above (Decision-tree, Status taxonomy, `Pinned at:` format, Schema, Bucket-1-vs-2, Transitions, Archive, File locations).

**Concrete content checkpoints**:
- The decision tree is the interview-question form from Decision #9. Phrasing precise enough that an implementer can mechanically apply it to a new deferment.
- Each bucket section names its canonical store: bucket 1/2 → `<module>/src/commonTest/.../DeferredContractsSpec.kt`; bucket 3 → flow-next epic in `todo` status; bucket 4 → `docs/deferments.md` entry only.
- The bucket-1 vs bucket-2 worked examples (per plan-review round 5): **schema-only examples are acceptable at fn-18.1 time** because no real entries exist yet (migrations happen in fn-18.2/.3). The convention doc shows the SHAPE of each bucket's `docs/deferments.md` entry + the SHAPE of each bucket's test (where applicable) using a **placeholder-form ID** (per plan-review round 8 — hardened to avoid R15 inventory leakage). All schema-example IDs in the CONVENTION doc MUST use a placeholder marker that the R15 regex `[A-Za-z0-9_]` tail-char excludes — recommended forms: `D-AUDIT.<example>`, `D-PASS-<example>-shape`, `D-PF.<example>`. Concrete-looking IDs like `D-AUDIT.7.III-FOLLOWUP` are **forbidden** in CONVENTION examples unless they are real entries in `docs/deferments.md` at the time of writing (which they cannot be at fn-18.1 time because migrations haven't run). After fn-18.2/.3 populate real entries, the convention doc may be updated in-place to point at real IDs — but fn-18.1's acceptance is schema-shape-only with placeholder IDs. **Bucket-1 schema example**: an entry whose status is `blocked`/`narrative` referencing today's API in its `Pinned at:` field, ID form `D-AUDIT.<example>`. **Bucket-2 schema example**: same status but with `Why:` noting the missing API, ID form `D-PASS-<example>-missing-api`. **Bucket-3 schema example**: `Status: planned` pointing at a placeholder epic ID `fn-N-<example>-epic`. **Closed/archive schema example** (corrected per plan-review round 7): the three-field locked archive schema from epic Decision #3 — **`Status:` + `Closed by:` + `Enforcement:`**. The previous wording ("Status + Closed by only") was wrong; archive entries MUST include `Enforcement:` so the closure record is at least as informative as the deleted comment block. All four examples use schema shape with placeholder-form IDs; real-ID examples may be added post-fn-18.2/.3 as a documentation polish.
- **Compile-check guarantee** (per epic Decision #1, hardened per plan-review round 5): bucket 1 tests **MUST** contain at least one real current-API reference (import + assertion) so a rename of the referenced API breaks compile loudly. If an entry cannot satisfy this MUST, it is bucket 2 by definition (commented-out future API, narrative-in-code only). The MUST-vs-SHOULD distinction is what makes bucket 1 "code-as-record" — without compile-check it collapses into bucket 2.
- **Heading discipline** (per epic Decision #7): only `### D-...` headings are entries; section-organising headings use `##` depth. Spell this out in the schema section so a future writer doesn't accidentally produce a header that grep-counts as an entry.
- The schema example for a docs entry uses the locked field order (Decision #7). Show one fully-worked entry in each status (`blocked`, `planned`, `narrative`, `closed`) so an implementer sees the field cadence.

### Step 2 — Write `docs/deferments.md` (empty body except meta-deferments)

**Heading discipline** (per epic Decision #7): ONLY `### D-...` headings denote a deferment entry. Prefix-organising section headings use `##` depth; non-entry placeholder text uses prose. The meta-deferment entries themselves use `### D-PASS-deferments-...` headings (entries, by definition). This makes the file mechanically scannable: `grep -c '^### D-' docs/deferments.md` counts entries.

**Structure** locked per epic Decision #7a — prefix subsections at h2 depth directly, no parent wrapper:

```markdown
# Deferments Register

<one paragraph: four-bucket model, why this file exists, where to file new
items, link to docs/deferments-CONVENTION.md>

<one paragraph: how to read; status taxonomy in one sentence; heading-discipline
note that only ### D-... rows are entries>

## D-PF

_(populated by fn-18.2 from `~/.claude/plans/pilot-firewall.md`)_

## D-AUDIT

_(populated by fn-18.2 from `~/.claude/plans/pilot-firewall.md`)_

## D-PASS

### D-PASS-deferments-map-tooling-automation — Tooling automation over deferments map
**Status:** narrative
**Pinned at:** narrative only
**Why:** <1-3 sentences per Decision #14>
**Closes by:** new epic when CI tooling lift becomes worthwhile.

### D-PASS-deferments-renumbering-discipline — Mixed ID-scheme cleanup
**Status:** narrative
**Pinned at:** narrative only
**Why:** <1-3 sentences>
**Closes by:** new epic when settling on a single ID convention.

### D-PASS-deferments-cross-ref-from-impl-review — Defer flow for review findings
**Status:** narrative
**Pinned at:** narrative only
**Why:** <1-3 sentences>
**Closes by:** new epic when a /flow-next:defer skill is justified.

_(remaining D-PASS-* entries populated by fn-18.2 / fn-18.3.)_

## D-WORLD

_(populated by fn-18.2)_

## Archive

_(closed entries land here per the convention's archive policy.)_
```

The italicised prose placeholders are deliberate — make the empty state self-explanatory. **No `### D-...` header for those placeholders** — they're prose-only markers.

### Step 3 — Update `AGENTS.md`

Two insertion points:

1. Under `# Project Plan` (around line 420-433), add one short paragraph BEFORE the `.plan` description containing both the discovery pointer AND the boundary rule (per epic Decision #8 clarified per plan-review round 3):

   ```
   `docs/deferments.md` is the project-wide deferments register — the canonical
   discovery point for named `D-*` deferments (`D-PF.*`, `D-AUDIT.*`, `D-PASS-*`,
   `D-WORLD.*`) organised by four-bucket model (test contract / API gap /
   multi-task epic / narrative). See `docs/deferments-CONVENTION.md` for the
   decision tree.

   `.plan` remains the canonical backlog for ordinary known issues with
   `Impact: H/M/L` ratings (short-ID format: `B3`, `IFR-1`, `RR-*`, `M*`).
   Boundary: if the item has a named `D-*` prefix with a real-fix contract
   (eventual API shape, blocked-on prerequisite), it lives in
   `docs/deferments.md`. Otherwise it lives in `.plan`.
   ```

2. Under `## Self-assessment before review` (around line 67-79), append one bullet to the numbered self-assessment list:
   ```
   8. **Deferment honesty**: Any work surfaced during this pass that won't ship in this pass is filed in `docs/deferments.md` (one of the four buckets) before commit.
   ```

### Step 4 — Rewrite `reference_audit_registers.md` memory entry (R4a + R4b)

Per epic R4 split (clarified per plan-review round 7), this step has TWO distinct outcomes:

**R4a — produce the exact replacement text (acceptance-bound, in-evidence)**:
- Title/description: still "Audit / deferments registers location".
- New body: ~150 words covering: (a) primary register `docs/deferments.md` at repo root with four-bucket model; (b) bucket overview in 4 short bullets; (c) `~/.claude/plans/pilot-firewall.md § Deferments register` kept as historical secondary for pre-Pass-17 narrative; (d) convention doc at `docs/deferments-CONVENTION.md`; (e) the four bucket-canonical stores (per-module `DeferredContractsSpec.kt`, flow-next epic, docs entry, inline code comment).
- The exact text MUST be pasted into fn-18.1's evidence block at task close — this is the R4a gate. R4a is acceptance-bound regardless of whether the memory directory is reachable.

**R4b — perform the actual write (acceptance-bound IF memory directory is writable)**:
- When `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/` is reachable and writable: overwrite `reference_audit_registers.md` with the R4a text AND refresh `MEMORY.md` index entry's prose. Evidence records `R4b confirmed — memory entry written on YYYY-MM-DD.`
- When the directory is missing/unwritable (per plan-review round 4 fallback): evidence records `R4b pending — exact replacement text provided in R4a evidence; external follow-up required.` This is a non-blocking external follow-up identical in shape to R11's user-edit handling. The R4a gate is still met; R4b is logged as pending.
- Both states satisfy fn-18.1 acceptance. R4a + the R4b-confirmed-or-pending log is the dual-record format.

### Step 5 — Refresh `MEMORY.md` index

Locate the `reference_audit_registers.md` line in MEMORY.md and update the summary prose to match the new memory entry's gist.

### Step 6 — File the three meta-deferments inline in `docs/deferments.md`

Per epic R13. Each gets a full schema entry under `## D-PASS`. Field values per Decision #14.

### Step 7 — Verify

**Pre-task baseline capture** (per plan-review round 2): at task start, capture base SHA + pre-existing failure state.

```bash
git rev-parse HEAD > /tmp/fn-18-1-base-sha.txt
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt 2>&1 | tee /tmp/fn-18-1-base-test.log
```

**Post-task verification**: re-run. R14 acceptance is "no failures introduced relative to base log." fn-18.1 should not touch any production code or any existing test; the only test-surface change is none. detekt baseline unchanged.

## Investigation targets

- Confirm the existing `AGENTS.md` § "Project Plan" structure (line 420-433) — verify the insertion site is appropriate.
- Confirm the existing `AGENTS.md` § "Self-assessment before review" numbering (current list 1-7).
- Confirm `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/MEMORY.md` index format.
- Verify the convention doc renders cleanly in markdown viewers (headers, lists, code blocks).

## Key context

- **No production code touched.** This task ships docs, a memory entry, and a markdown index update.
- **`docs/deferments.md` body is empty at .1 close** except for the three meta-deferments. Migration of existing sources happens in .2 and .3.
- **The convention doc is the load-bearing artifact** — if it's unclear, .2/.3 produce inconsistent records.
- **`AGENTS.md` change is minimal** — one bullet + one line. Do NOT rewrite or restructure the existing AGENTS.md.
- **Memory entry rewrite is acceptance-level** — agents reading the old entry get the new pointer for future sessions.
- **No corners cut on the meta-deferments** — they're documented per the schema, not as a "TODO file these later" comment.

## Acceptance

- [ ] **R1** (partial — scaffold portion) — `docs/deferments.md` exists at repo root with: header + schema-explanation paragraph + prefix subsections at h2 depth (`## D-PF`, `## D-AUDIT`, `## D-PASS`, `## D-WORLD`) with prose placeholders for empty ones + the three meta-deferments fully-schema'd under `## D-PASS` + `## Archive` stub.
- [ ] **R2** — `docs/deferments-CONVENTION.md` exists at repo root covering all 9 required sections (overview, decision tree, status taxonomy, `Pinned at:` format, entry schema, bucket-1-vs-2 worked example, transition convention, archive policy, file locations).
- [ ] **R3** — `AGENTS.md` updated: one new bullet under "Project Plan" pointing at `docs/deferments.md`; one new line under "Self-assessment before review" requiring deferred work to be filed before commit. Existing `.plan` references intact.
- [ ] **R4a** — Exact replacement text for `reference_audit_registers.md` pasted into fn-18.1 evidence block. Acceptance-bound regardless of memory-directory reachability.
- [ ] **R4b** — Recorded as one of two outcomes per epic R4 split: **R4b-confirmed** (memory directory reachable; `reference_audit_registers.md` rewritten in place + `MEMORY.md` index prose refreshed; date logged in evidence) OR **R4b-pending** (memory directory unreachable; pending external follow-up logged in evidence with the R4a text as the source-of-truth handoff).
- [ ] **R13** — Three meta-deferments (`D-PASS-deferments-map-tooling-automation`, `D-PASS-deferments-renumbering-discipline`, `D-PASS-deferments-cross-ref-from-impl-review`) filed in `docs/deferments.md` per Decision #14 as bucket 4 (`narrative`).
- [ ] **R14** — Recorded as one of two outcomes per epic Decision #R14: **R14-Passed** (gradle exits 0; eight goldens GREEN; detekt unchanged; no `@Ignore` status changes) OR **R14-NoNewRegression** (baseline was already red; task evidence records base SHA + exact failing tests at start and proves no new failures).

## Done summary

_(filled at done-time)_

## Evidence

_(filled at done-time)_
