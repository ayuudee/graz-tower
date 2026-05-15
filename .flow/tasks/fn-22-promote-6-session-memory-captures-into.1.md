---
satisfies: [R1, R2, R3, R4, R5, R6, R7, R8, R9, R10]
---

## Description

Read the 9 source bug captures, synthesize each cluster into a focused durable knowledge entry, write 6 files under `.flow/memory/knowledge/{best-practices,conventions,workflow}/`, seed `.flow/memory/MEMORY.md` with a one-line index per entry. Verify via `flowctl memory search` per theme. **No bug/* captures deleted, no out-of-scope edits.**

**Size:** S (6 new files + 1 index update; ~150-300 LOC of markdown across the batch).

**Files**:
- **READ ONLY**: the 9 source bug captures listed in the epic spec under "Source captures".
- **CREATE**:
  - `.flow/memory/knowledge/best-practices/test-pin-discipline.md`
  - `.flow/memory/knowledge/best-practices/inherited-gate-semantics.md`
  - `.flow/memory/knowledge/best-practices/renumbering-grep-walk.md`
  - `.flow/memory/knowledge/best-practices/pre-existing-failure-register.md`
  - `.flow/memory/knowledge/conventions/rich-world-domain.md`
  - `.flow/memory/knowledge/workflow/flowctl-done-state-sync.md`
- **MODIFY**: `.flow/memory/MEMORY.md` (seed with 6 index entries; preserve any existing entries).

## Approach (numbered Steps)

### Step 0 — Baseline capture (BEFORE any edits)

```bash
git rev-parse HEAD > $TMPDIR/fn-22-1-base-sha.txt
find .flow/memory/bug -name "*.md" | wc -l > $TMPDIR/fn-22-1-base-bug-count.txt   # baseline (expect 17)
find .flow/memory/knowledge -name "*.md" | wc -l > $TMPDIR/fn-22-1-base-knowledge-count.txt   # baseline (expect 0)
```

### Step 1 — Read source captures, identify clusters

For each of the 9 source bug captures, extract:
- The core lesson (one-sentence "the rule")
- The reason / why it bit (one-paragraph context)
- The forward-applicable rule (when does this apply? how to recognize the situation?)

Cluster the 4 test-pin lessons (R1) into a single coherent entry; the others are 1:1.

### Step 2 — Write `.flow/memory/knowledge/best-practices/test-pin-discipline.md` (R1)

Synthesize the 4 test-pinning lessons (sim-test-pins-must-compare-against, tests-must-anchor-on-observed-post, r9-style-allowlist-guards-must-key-by, compound-predicate-test-assertions) into one entry. Cover:

- **Mint-id vs tx-start timestamps**: when ordering events on the same decision cycle, use `findEmittingCycleMs` mint-id timestamps (or note the cross-cycle architectural property that makes tx-start sufficient, as fn-15.2 documented).
- **Observe real post-state, not predicate compounds**: assertions like `count CAGA == 0 && TouchAndGo phase observed` are vacuously true if the predicate never fires; use direct absence assertions on observed-state values.
- **Allowlists key by stable identifier**: when guarding tests against unintended content (e.g. JSON files matching a path), key the allowlist by file path or stable id, not by transient JSON body content.
- **Compound-predicate failure modes**: identify the specific anti-pattern from fn-14.1 → fn-15.1 inheritance.

Reference the 4 source bug captures by file path.

### Step 3 — Write `.flow/memory/knowledge/best-practices/inherited-gate-semantics.md` (R2)

Document the fn-15.2 lesson: when copy-pasting a sim-test gate between sibling axes (e.g. crosswind → tailwind), the gate's semantic meaning may not transfer even when the syntactic pattern looks identical. The fn-14.2 "off-final" gate meant "back on downwind" by coincidence on the crosswind axis (where the GA's downwind re-entry coincided with off-final); on the tailwind axis, the same gate fired prematurely on transient phase windows during GA climbout. The fix was to watch the radio for the post-GA recovery `Report(Downwind)` directly, not the stage transition.

Forward-applicable rule: when adding a sibling sim test to an existing axis, run the gate against the new scenario's expected timeline manually (or via dry-run) and confirm the gate's semantic meaning still pins what you want.

### Step 4 — Write `.flow/memory/knowledge/conventions/rich-world-domain.md` (R3)

Document the time-varying-state-lives-on-entity convention. Cover:

- **The rule**: time-varying state (weather, runway obstructions, etc.) lives on the entity it concerns (`Aerodrome.weather`, `Runway.obstruction`), not flat `World`-root or `SimState`-root maps.
- **Precedents**: fn-12 migrated `Runway.obstruction`; fn-16 migrated `Aerodrome.weather`. Both used **atomic hard cutover** — no shim, no parallel shape, no deprecated field.
- **Forward-applicable**: when adding new time-varying state, default to the entity-field shape from day 1. If extending existing flat-map state, plan an atomic-cutover migration epic (fn-16 is the precedent for the migration shape).
- **Anti-pattern**: `SimState.weatherByAerodrome` was the long-standing pre-`rich_world_domain` shape; fn-16 closed it.

Reference: source bug capture `rich-world-domain-entity-field-needs-2026-05-13.md` + fn-12 / fn-16 spec citations.

### Step 5 — Write `.flow/memory/knowledge/best-practices/renumbering-grep-walk.md` (R4)

Document the fn-17 lesson: when reconciling renumbering across docs/code/tests (e.g. CAP 413 §4.65 → §4.64), the grep walk must span the FULL §-number range affected by the edition's shift, not just the hypothesis's focal sections. fn-17.1 round-3 codex finding missed stale `§4.55-4.56` prose at `Controller.kt:917` and `AGENTS.md:281` because the initial grep walk only targeted the focal `§4.65/§4.66/§4.67` range, missing the broader `-1` shift's other affected entries.

Forward-applicable rule: when scope is "renumbering reconciliation", the grep walk's regex must enumerate every potentially-affected section number, not just the hypothesis's focal subset. Use the verification artifact's mapping table as the authoritative range, not the prose description.

### Step 6 — Write `.flow/memory/knowledge/best-practices/pre-existing-failure-register.md` (R5)

Document the fn-16.2 → fn-19 lesson: when a test failure is known-and-pre-existing (carved out of one epic, then another, then another), promote it to a named `D-WORLD.N` (or similar prefix-family) register entry in `docs/deferments.md` after the second cross-epic occurrence. Don't let the carve-out narrative propagate across 5+ epics (the fn-5 / fn-6.2 / fn-9.2 / fn-11.2 / fn-16.1 anti-pattern that fn-16.2 finally named and fn-19 closed).

Forward-applicable rule: if a known-failing test is being skipped for the second time in a new epic's R14-NoNewRegression evidence, file a named register entry before the third occurrence.

### Step 7 — Write `.flow/memory/knowledge/workflow/flowctl-done-state-sync.md` (R6)

Document the fn-18.2 lesson from `bug/build-errors/long-spec-flow-next-tasks-review-loop-2026-05-13.md` (the actual source capture). Cover:

- **The pattern**: on long-spec flow-next tasks, `flowctl done` has side-effects on the task spec md (it replaces the `## Done summary` section with its canonical write and resets the `## Evidence` block). When the prior-worker block narrative (`## BLOCKED: EXTERNAL_BLOCKED` etc.) lingers in the same file from a pre-reset state, codex impl-review cycles repeatedly on "spec md state doesn't match implementation state."
- **The fn-18.2 manifestation**: 5 review rounds, each surfacing a different artifact mismatch — round 1 stale BLOCKED narrative, round 2 placeholder Done summary, round 3 .json `status: todo` not flipped, round 4 empty Evidence block, round 5 Set-A cardinality inconsistency between summary + evidence.
- **Forward-applicable rule**: after `flowctl done`, run a **post-done state-synchronization sweep** BEFORE re-invoking codex review:
  1. Clear any stale BLOCKED / NEEDS_WORK narrative from the task md.
  2. Confirm `## Done summary` carries the substantive description (not the flowctl placeholder).
  3. Populate `## Evidence` with actual values (commits, test results) — not the literal `<from base-sha.txt>` placeholder templates.
  4. Verify `.json status` field is `done` (not the worker's earlier `in_progress` / `todo`).
  5. Run a brief internal-consistency check: do counts in the Done summary match counts in Evidence? Do commit SHAs in Done summary appear in Evidence?
- **Anti-pattern**: re-invoking codex impl-review after `flowctl done` without the sync sweep produces 3-5 rounds of cascading artifact-mismatch findings; each fix unblocks the next.

### Step 8 — Seed `.flow/memory/MEMORY.md` index (R7)

The file is currently essentially empty (a placeholder header at most). Seed with a structured index. Format (matching the auto-memory MEMORY.md style observed in `/Users/andrew/.claude/projects/.../memory/MEMORY.md`):

```markdown
# Memory index — graz-tower (flow-next memory)

Project-scoped flow-next memory. Each entry below is a one-line summary of a
file in this directory; the file itself carries the full content. Discoverable
via `flowctl memory search <token>`.

## knowledge/best-practices

- **`knowledge/best-practices/test-pin-discipline.md`** — Sim test pins: mint-id timestamps for same-cycle ordering, observe real post-state not predicate compounds, allowlists key by stable identifier, compound-predicate failure modes.
- **`knowledge/best-practices/inherited-gate-semantics.md`** — Copy-pasted sim-test gates between sibling axes need re-validation per semantic meaning, not syntactic pattern (fn-15.2 lesson).
- **`knowledge/best-practices/renumbering-grep-walk.md`** — Grep walks for renumbering reconciliation must span the FULL affected range, not just the hypothesis's focal sections (fn-17 lesson).
- **`knowledge/best-practices/pre-existing-failure-register.md`** — Pre-existing test failures carried across 2+ epics must be promoted to named D-WORLD.N register entries (fn-16.2 → fn-19 lesson).

## knowledge/conventions

- **`knowledge/conventions/rich-world-domain.md`** — Time-varying state lives on the entity (`Aerodrome.weather`, `Runway.obstruction`); hard atomic cutover, no shims (fn-12 + fn-16 precedents).

## knowledge/workflow

- **`knowledge/workflow/flowctl-done-state-sync.md`** — After `flowctl done`, run a post-done state-sync sweep (clear BLOCKED narrative, populate Evidence, flip `.json` status) BEFORE re-invoking codex impl-review — otherwise review cycles on artifact mismatches (fn-18.2 5-round lesson).
```

Preserve any existing entries (none expected, but defensive).

### Step 9 — Verify (R8, R9, R10)

```bash
# R9 — bug captures untouched
find .flow/memory/bug -name "*.md" | wc -l   # expect: same as base (17)
diff <(sort $TMPDIR/fn-22-1-base-bug-count.txt) <(find .flow/memory/bug -name "*.md" | wc -l)

# R10 — new file count
find .flow/memory/knowledge -name "*.md" | wc -l   # expect: 6

# R8 — search smoke tests (per theme)
for theme in "mint-id" "rich-world-domain" "inherited gate" "renumbering grep" "pre-existing test failure" "flowctl done state sync"; do
  echo "=== search: $theme ==="
  .flow/bin/flowctl memory search "$theme" --limit 3 --json | jq -r '.matches[]? | "  [\(.category)] \(.title)"'
done

# R7 — index has 6 entries
grep -cE "^- \\*\\*\`knowledge/" .flow/memory/MEMORY.md   # expect: ≥6 (matches `- **\`knowledge/...\`**` bullets per Step 8 format)
```

If R8 shows any theme where the new entry isn't in the top 3, refine the entry's title/tags to include the theme tokens. Don't ship with a theme that doesn't surface its entry.

### Step 10 — `flowctl done` (eat-your-own-dogfood for R6 flowctl-done state-sync)

**CRITICAL**: this step IS the post-done state-sync sweep the R6 entry teaches. Compute concrete values BEFORE writing the evidence JSON; the literal `<placeholder>` strings must NOT appear in the final evidence (per plan-review round 2 — codex finding "Step 10 violates the workflow lesson it is promoting").

```bash
# Compute concrete values
base_sha="$(cat "$TMPDIR/fn-22-1-base-sha.txt")"
implementation_sha="$(git rev-parse HEAD)"   # capture pre-flowctl-done HEAD
base_bug_count="$(cat "$TMPDIR/fn-22-1-base-bug-count.txt")"
post_bug_count="$(find .flow/memory/bug -name "*.md" | wc -l | tr -d ' ')"
post_knowledge_count="$(find .flow/memory/knowledge -name "*.md" | wc -l | tr -d ' ')"

# Write Done summary with interpolated counts
cat > "$TMPDIR/fn-22-1-summary.md" <<EOF2
fn-22.1 shipped: ${post_knowledge_count} durable knowledge entries promoted from 9 session bug/* captures into .flow/memory/knowledge/{best-practices,conventions,workflow}/. MEMORY.md index seeded. ${post_bug_count} bug/* captures (baseline ${base_bug_count}) kept symptom-only. flowctl memory search verified each entry surfaces as top hit for its theme. Implementation commit: ${implementation_sha}.
EOF2

# Write Evidence JSON with interpolated values
cat > "$TMPDIR/fn-22-1-evidence.json" <<EOF2
{
  "task": "fn-22-promote-6-session-memory-captures-into.1",
  "base_sha": "${base_sha}",
  "implementation_sha": "${implementation_sha}",
  "files_created": 6,
  "files_modified": 1,
  "bug_captures_baseline": ${base_bug_count},
  "bug_captures_post": ${post_bug_count},
  "knowledge_entries_count_post": ${post_knowledge_count},
  "knowledge_entries_created": [
    "knowledge/best-practices/test-pin-discipline.md",
    "knowledge/best-practices/inherited-gate-semantics.md",
    "knowledge/best-practices/renumbering-grep-walk.md",
    "knowledge/best-practices/pre-existing-failure-register.md",
    "knowledge/conventions/rich-world-domain.md",
    "knowledge/workflow/flowctl-done-state-sync.md"
  ],
  "memory_search_smoke_test": "all 6 themes surface their entry in flowctl memory search top-3 (verified per Step 9)",
  "diff_file_count": 7
}
EOF2

# Run flowctl done with the interpolated artifacts
.flow/bin/flowctl done fn-22-promote-6-session-memory-captures-into.1 \
  --summary-file "$TMPDIR/fn-22-1-summary.md" \
  --evidence-json "$TMPDIR/fn-22-1-evidence.json" --json

# Post-done state-sync sweep (eat-your-own-dogfood):
# 1. Confirm task md `## Done summary` carries the interpolated text (not a flowctl placeholder)
# 2. Confirm task md `## Evidence` carries concrete SHAs (not literal `<placeholder>` strings)
# 3. Confirm `flowctl show <task> --json | jq .status` == "done"
# (No re-review needed for this task since the impl-review flow runs separately, but the sweep is the discipline the R6 entry promotes — practice it here.)
```

## Acceptance

- [ ] **R1-R6** — Six knowledge entries written under `.flow/memory/knowledge/{best-practices,conventions,workflow}/` per Steps 2-7; each cites ≥2 source bug captures (where the cluster shape allows) and offers a forward-applicable rule.
- [ ] **R7** — `.flow/memory/MEMORY.md` indexes the 6 entries; format mirrors the auto-memory MEMORY.md style (bullets of form `- **\`knowledge/...\`**`).
- [ ] **R8** — `flowctl memory search` returns each new entry as top-3 hit for its theme tokens.
- [ ] **R9** — `find .flow/memory/bug -name "*.md" | wc -l` unchanged from baseline (no bug/* captures deleted).
- [ ] **R10** — Diff is 6 new files + 1 MEMORY.md update = 7 files; no out-of-scope edits.

## Key context

- Knowledge entries are durable "rule + reason + when-to-apply" — not symptom records. If an entry would just paste one bug capture's body, it failed the consolidation gate (per epic Early proof point).
- `flowctl memory search` is token-based. Title and body tokens both contribute; pick titles that include the theme's load-bearing terms.
- The auto-memory at `/Users/andrew/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/` is a SEPARATE system (used by the main Claude conversation, not flow-next subagents). Do not write to it from this task.
- Pre-existing dirty state (research/tools/requirements-spike/, fn-20 untracked files, research/pdf+txt) MUST NOT be staged.

## Done summary

_(filled by `flowctl done` at task close — see Step 10)_

## Evidence

_(filled by `flowctl done` at task close — see Step 10)_
