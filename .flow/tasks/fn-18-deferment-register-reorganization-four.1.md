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
  - `./AGENTS.md` — current structure for the discovery-pointer insertion site.
  - `./.flow/specs/fn-14-g3a-react-pilot-reactive-go-around-on.md` § Deferments register — current epic-spec schema (input to convention doc's bucket-3 worked example).
  - `./.flow/specs/fn-18-deferment-register-reorganization-four.md` — the epic spec being implemented (Decisions #1-#15 are the source of truth for the convention doc).
  - `./pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt` — current placeholder-test pattern (bucket 1/2 worked example).
  - `~/.claude/plans/pilot-firewall.md` § Deferments register — current canonical store (historical secondary in the new model).
  - `/Users/andrew/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/reference_audit_registers.md` — current auto-memory entry being rewritten.
  - `/Users/andrew/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/MEMORY.md` — index entry's prose may need a minor refresh.

- **CREATE**
  - `./docs/deferments.md` — the map. Header explaining the four-bucket model (1 paragraph), schema explanation (showing the locked field order), one-paragraph "How to read this file", prefix subsections at h2 depth (`## D-PF`, `## D-AUDIT`, `## D-PASS`, `## D-WORLD` — per epic Decision #7a, no parent wrapper section) all empty in .1 except for the three meta-deferments filed under `## D-PASS`, and `## Archive` section stub (empty body, one-line "Closed entries use the same `### D-...` heading discipline with minimal closed-body, see CONVENTION").
  - `./docs/deferments-CONVENTION.md` — full convention doc. Sections required: (1) Overview (1 paragraph naming the four buckets + their canonical stores); (2) Decision tree (per epic Decision #9 — interview-question form); (3) Status taxonomy (4 leaves per Decision #2, with one-sentence definition each); (4) `Pinned at:` field format (per Decision #5, with examples for tests + epics + narrative); (5) Schema for `docs/deferments.md` entries (per Decision #7 — locked field order, mandatory vs conditional fields, `Why:` cap at 3 sentences); (6) Bucket-1 vs bucket-2 worked example showing both shapes (active API vs commented-out future API); (7) Bucket transition convention (per Decision #6 — in-place status updates, no duplicates); (8) Archive policy (per Decision #3 — closed entries move to `## Archive`, lose Why/Blocked-on detail, keep one-line summary); (9) File locations (per Decision #4: `docs/deferments.md` at repo root; per-module `DeferredContractsSpec.kt` files via Decision #1). **Convention-example ID discipline** (per plan-review round 11 — codex finding "R15 regex placeholder leakage"): all illustrative IDs in worked examples MUST use non-matching forms that DO NOT begin with `D-PASS-` / `D-AUDIT.` / `D-PF.` / `D-WORLD-` / `D-WORLD.`, e.g. `<D-ID example>`, `DEFERMENT_ID_EXAMPLE`, `EXAMPLE-PASS-feature-shape`. This prevents R15's whole-repo grep from picking up convention-doc examples as real IDs that should exist in `docs/deferments.md`. Decision #14 (R15 regex tail-char restriction to `[A-Za-z0-9_]`) already excludes IDs with `<` characters, but a worked example like `EXAMPLE-PASS-feature-shape` would pass the regex and trigger a false-positive drift signal — using a non-matching example form is defence-in-depth.

- **MODIFY**
  - `./AGENTS.md` — add one bullet under `# Project Plan` section pointing at `docs/deferments.md`. Add one line under "Self-assessment before review" (item 8 or appended to list) noting deferred work surfaced during a pass must be filed in `docs/deferments.md` before commit. Existing `.plan` references stay; the new pointer **supplements**.
  - `/Users/andrew/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/reference_audit_registers.md` — rewrite to point at `docs/deferments.md` as primary discovery surface, four-bucket model with one-sentence summaries, `~/.claude/plans/pilot-firewall.md` as historical secondary. Update title/description metadata if applicable.
  - `/Users/andrew/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/MEMORY.md` — refresh the `reference_audit_registers.md` index line's prose. New summary: e.g. "Deferment register lives in `docs/deferments.md` per the four-bucket model; legacy register in `~/.claude/plans/pilot-firewall.md` § Deferments register kept as historical secondary."

## Approach (numbered Steps)

### Step 0 — Baseline capture (BEFORE any edits)

Per plan-review round 18 — codex finding "baseline capture is inside the verify step, AFTER edits, so R14-NoNewRegression can't actually prove pre-existing failures": capture the base SHA and pre-task verify output BEFORE doing any inventory mutation, file edit, or test write. The "pre-task baseline" must be pre-EVERYTHING, not pre-verify.

```bash
git rev-parse HEAD > $TMPDIR/fn-18-1-base-sha.txt
# Capture base verify state. If any failures pre-exist, R14-NoNewRegression mode is in effect
# and the post-task verify must not introduce NEW failures beyond what this baseline records.
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
  2>&1 | tee $TMPDIR/fn-18-1-base-test.log
# Module preflight (fail-loud if any required Gradle task is missing — same rule across all fn-18 tasks):
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
  --dry-run --offline --no-daemon 2>&1 | tee $TMPDIR/fn-18-1-preflight.log
```

The Verify step (later) re-runs the same command and diffs against this baseline.

### Step 1 — Write `docs/deferments-CONVENTION.md`

This is the load-bearing artifact. The convention drives every subsequent migration decision. Sections in the order above (Decision-tree, Status taxonomy, `Pinned at:` format, Schema, Bucket-1-vs-2, Transitions, Archive, File locations).

**Concrete content checkpoints**:
- The decision tree is the interview-question form from Decision #9. Phrasing precise enough that an implementer can mechanically apply it to a new deferment.
- Each bucket section names its canonical store: bucket 1/2 → `<module>/src/commonTest/.../DeferredContractsSpec.kt`; bucket 3 → flow-next epic in `todo` status; bucket 4 → `docs/deferments.md` entry only.
- The bucket-1 vs bucket-2 worked examples (per plan-review round 5, hardened per plan-review round 11): **schema-only examples are acceptable at fn-18.1 time** because no real entries exist yet (migrations happen in fn-18.2/.3). The convention doc shows the SHAPE of each bucket's `docs/deferments.md` entry + the SHAPE of each bucket's test (where applicable) using a **non-matching placeholder ID** form. **Rule (per plan-review round 11 — codex finding "R15 regex placeholder leakage")**: all illustrative IDs in CONVENTION worked examples MUST NOT begin with `D-PASS-` / `D-AUDIT.` / `D-PF.` / `D-WORLD-` / `D-WORLD.`. Use forms that the R15 grep cannot pattern-match against, e.g. `<D-ID example>`, `EXAMPLE-PASS-feature-shape`, `EXAMPLE-AUDIT.NN-FOLLOWUP`, `EXAMPLE-PF.N`, `DEFERMENT_ID_EXAMPLE`. The previous recommendation (`D-AUDIT.<example>`, `D-PASS-<example>-shape`, `D-PF.<example>`) is **superseded** — even though the R15 regex's tail-char restriction (`[A-Za-z0-9_]`) excludes the `<` character, the `D-` prefix is still grep-bait if anyone shifts punctuation in a future edit. Defence-in-depth: use a prefix that is structurally incapable of matching the regex's leading anchor. Concrete-looking IDs like `D-AUDIT.7.III-FOLLOWUP` remain **forbidden** in CONVENTION examples unless they are real entries in `docs/deferments.md` at the time of writing. After fn-18.2/.3 populate real entries, the convention doc may be updated in-place to point at real IDs — but fn-18.1's acceptance is schema-shape-only with non-matching placeholder IDs. **Bucket-1 schema example**: an entry whose status is `blocked`/`narrative` referencing today's API in its `Pinned at:` field, ID form `<D-ID example>` or `EXAMPLE-AUDIT.feature`. **Bucket-2 schema example**: same status but with `Why:` noting the missing API, ID form `EXAMPLE-PASS-missing-api`. **Bucket-3 schema example**: `Status: planned` pointing at a placeholder epic ID `fn-N-<example>-epic`. **Closed/archive schema example** (corrected per plan-review round 7): the three-field locked archive schema from epic Decision #3 — **`Status:` + `Closed by:` + `Enforcement:`**. The previous wording ("Status + Closed by only") was wrong; archive entries MUST include `Enforcement:` so the closure record is at least as informative as the deleted comment block. All four examples use schema shape with non-matching placeholder IDs; real-ID examples may be added post-fn-18.2/.3 as a documentation polish.
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
- When `/Users/andrew/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/` is reachable and writable: overwrite `reference_audit_registers.md` with the R4a text AND refresh `MEMORY.md` index entry's prose. Evidence records `R4b confirmed — memory entry written on YYYY-MM-DD.`
- When the directory is missing/unwritable (per plan-review round 4 fallback): evidence records `R4b pending — exact replacement text provided in R4a evidence; external follow-up required.` This is a non-blocking external follow-up identical in shape to R11's user-edit handling. The R4a gate is still met; R4b is logged as pending.
- Both states satisfy fn-18.1 acceptance. R4a + the R4b-confirmed-or-pending log is the dual-record format.

### Step 5 — Refresh `MEMORY.md` index

Locate the `reference_audit_registers.md` line in MEMORY.md and update the summary prose to match the new memory entry's gist.

### Step 6 — File the three meta-deferments inline in `docs/deferments.md`

Per epic R13. Each gets a full schema entry under `## D-PASS`. Field values per Decision #14.

### Step 7 — Verify

**Pre-task module preflight** (per plan-review round 11 — codex finding ":sim module preflight missing"; hardened per plan-review round 14 — codex finding "Step 7 contradicts itself"): confirm all referenced Gradle tasks exist before running the verify command. **Fail loud if any required Gradle task is missing** — same rule as fn-18.2 and fn-18.3 (no silent substitution, no trimmed verify command). Record `gradle_module_preflight_failure: <missing tasks>` in evidence and halt the task. R14 requires the full task set or an honest no-new-regression comparison against the same set; under-verification is not an option.

```bash
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
  --dry-run --offline --no-daemon 2>&1 | tee $TMPDIR/fn-18-preflight.log
# --dry-run prints the task graph that WOULD execute without actually running tests.
# Gradle exits non-zero if any task is unknown (e.g. ':sim:jvmTest' if :sim module is absent).
# This is the load-bearing preflight check (per plan-review round 13 — codex finding
# './gradlew tasks without --all does NOT list :sim:jvmTest, so the prior preflight would
# false-positive on a valid repo'). --dry-run is the canonical Gradle preflight idiom.
# **Fail loud (per plan-review round 12): if ANY required Gradle task is missing, halt the task with an explicit `gradle_module_preflight_failure: <missing tasks>` line in evidence and refuse to silently substitute a trimmed verify command. Surface the repository/module mismatch instead of under-verifying.
```

Baseline already captured in Step 0 (`$TMPDIR/fn-18-1-base-sha.txt` + `$TMPDIR/fn-18-1-base-test.log`). No need to re-run pre-task capture here — that's a defense against round-2's split-baseline anti-pattern (round 18 — codex finding 'baseline must precede edits').

**Post-task verification**: re-run. R14 acceptance is "no failures introduced relative to base log." fn-18.1 should not touch any production code or any existing test; the only test-surface change is none. detekt baseline unchanged.

**`flowctl done` invocation** (per plan-review round 11 — codex finding "no concrete done-time step"): at task close, write the done summary and evidence JSON to dedicated files, then invoke `flowctl done` with both flags:

```bash
# Write done summary — R4b state determines wording (per plan-review round 15 — codex finding "templates always claim memory writes, contradicting R4b-pending path").
# When R4b confirmed:
cat > $TMPDIR/fn-18-1-summary.md <<'EOF'
fn-18.1 shipped: docs/deferments.md scaffold + docs/deferments-CONVENTION.md + AGENTS.md pointer + reference_audit_registers.md rewrite + MEMORY.md refresh + 3 meta-deferments filed. Implementation commit: see evidence-JSON `implementation_sha` field. No production code touched; nine goldens unchanged. R4b status: confirmed.
EOF
# When R4b pending (memory dir unreachable), use this instead — DO NOT claim memory files written:
# cat > $TMPDIR/fn-18-1-summary.md <<'EOF'
# fn-18.1 shipped: docs/deferments.md scaffold + docs/deferments-CONVENTION.md + AGENTS.md pointer + 3 meta-deferments filed. Implementation commit: see evidence-JSON `implementation_sha` field. No production code touched; nine goldens unchanged. R4b status: pending — replacement text recorded in evidence as external follow-up; reference_audit_registers.md and MEMORY.md NOT written this task.
# EOF
# Write evidence JSON — conditional on R4b state
cat > $TMPDIR/fn-18-1-evidence.json <<'EOF'
{
  "task": "fn-18-deferment-register-reorganization-four.1",
  "base_sha": "<from Step 0 base-sha.txt>",
  "implementation_sha": "<SHA of the implementation commit BEFORE flowctl done",
  "gradle_module_preflight": ["<list from preflight output>"],
  "files_created": ["docs/deferments.md", "docs/deferments-CONVENTION.md"],
  "files_modified": ["AGENTS.md"],
  "r4b_status": "confirmed | pending — fill in actually-realised state at task close",
  "r4b_memory_files_written": ["/Users/andrew/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/reference_audit_registers.md", "/Users/andrew/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/MEMORY.md"],
  "_r4b_memory_files_written_note": "Populated as the array above ONLY when r4b_status == confirmed. When r4b_status == pending, set this key to empty array [] and use r4b_pending_external_followup instead.",
  "r4b_pending_external_followup": null,
  "_r4b_pending_external_followup_note": "When r4b_status == pending, set this to the exact R4a replacement text (the same text as r4a_replacement_text below) so the external follow-up has its source-of-truth handoff. When r4b_status == confirmed, leave as null.",
  "r4a_replacement_text": "<paste the exact rewritten memory entry verbatim here, as a single string with \\n for newlines>",
  "meta_deferments_filed": ["D-PASS-deferments-map-tooling-automation", "D-PASS-deferments-renumbering-discipline", "D-PASS-deferments-cross-ref-from-impl-review"],
  "verify_command": "./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt",
  "verify_outcome": "BUILD SUCCESSFUL"
}
EOF
.flow/bin/flowctl done fn-18-deferment-register-reorganization-four.1 \
  --summary-file $TMPDIR/fn-18-1-summary.md \
  --evidence-json $TMPDIR/fn-18-1-evidence.json --json
```

## Investigation targets

- Confirm the existing `AGENTS.md` § "Project Plan" structure (line 420-433) — verify the insertion site is appropriate.
- Confirm the existing `AGENTS.md` § "Self-assessment before review" numbering (current list 1-7).
- Confirm `/Users/andrew/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/MEMORY.md` index format.
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
- [ ] **R14** — Recorded as one of two outcomes per epic Decision #R14: **R14-Passed** (gradle exits 0; nine goldens GREEN; detekt unchanged; no `@Ignore` status changes) OR **R14-NoNewRegression** (baseline was already red; task evidence records base SHA + exact failing tests at start and proves no new failures).

## Done summary
fn-18.1 shipped: docs/deferments.md scaffold + docs/deferments-CONVENTION.md + AGENTS.md pointer + reference_audit_registers.md rewrite + MEMORY.md index + 3 meta-deferments filed under § D-PASS. Implementation commit 270397e; no production code touched; nine sim goldens GREEN (R14-Passed at HEAD). R4b status: confirmed (memory directory was reachable + writable; reference_audit_registers.md + MEMORY.md written). Review verdict: SHIP via flowctl trivial-diff triage (mode `triage_skip`, deterministic, reason "docs-only (3 files)") — no codex backend call needed; receipt at $TMPDIR/impl-review-receipt.json.
## Evidence
- Commits:
- Tests:
- PRs: