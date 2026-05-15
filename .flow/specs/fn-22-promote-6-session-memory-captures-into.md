# fn-22 — Promote session bug/* captures to durable `.flow/memory/knowledge/` entries

## Overview

The 5-epic session (fn-15/16/17/18/19) plus fn-21 (strategy refresh) shipped 17 memory captures into `.flow/memory/bug/{test-failures,build-errors,integration,runtime-errors,data}/`. Each is a real lesson, but they're symptom-records ("this broke and how it broke") rather than durable conventions. The cross-cutting lessons need promotion into `.flow/memory/knowledge/{best-practices,conventions}/` so flow-next reviewer subagents (impl-review codex backend, plan-sync, workers) discover them via `flowctl memory search` keyed on the right categories rather than wading through symptom-by-symptom records.

The bug/* captures stay in place (they're authoritative event records). The promotion is **additive**: extract 6 durable lessons (5 technical + 1 process) that generalize beyond their source bug captures, write each as a focused knowledge entry under `.flow/memory/knowledge/{best-practices,conventions,workflow}/`, and update `.flow/memory/MEMORY.md` to index them. **No bug/* entries deleted.**

**Scope note on naming**: the prospect originally said "feedback_*" but `.flow/memory/` has no `feedback_` convention (no existing entries; flow-next doesn't key on the prefix). The target taxonomy is `knowledge/best-practices/` (durable how-to lessons), `knowledge/conventions/` (project-specific patterns), and `knowledge/workflow/` (flow-next process lessons). This task uses those three subtrees.

## Boundaries / non-goals

- **Out: deleting or rewriting bug/* captures.** They stay as authoritative event records. Promotion is additive.
- **Out: the auto-memory location** at `/Users/andrew/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/`. That's a separate memory system (read by the main Claude conversation, not flow-next subagents). Out of scope.
- **Out: any production code, tests, or docs outside `.flow/memory/`.** Pure memory consolidation.
- **Out: comprehensive coverage of all 17 bug entries.** Only promote the ones that span multiple captures with a clear durable lesson — single-symptom records stay as bug/* only.

## Strategy Alignment

Active tracks served by this plan:
- **Reviewer / agent infrastructure** — the strategy track explicitly anchors "plans review-aware by construction" and "AI-generated code is locally correct and globally blind; this project hardens against that". Consolidating cross-cutting lessons into `knowledge/best-practices/` is exactly this: making the corpus of "what we've learned" discoverable to subagents before they make the same mistakes.

## Decision context

**Why now**: 6 epics shipped in this session. Each captured 1-3 memory entries on the way. The lessons cluster around 6 themes: 5 technical (test pinning, inherited-gate semantics, rich-world-domain, renumbering grep walks, pre-existing-failure register discipline — the first one is a 4-capture synthesis, the rest are 1:1 with their source bug capture) plus 1 process lesson (flowctl-done state-synchronization). Without promotion, each future session re-discovers them the hard way. Tight window for consolidation while the source captures are still fresh.

**Cluster shape from inventory**:
- 4× test-pin-discipline (sim-test-pins-must-compare-against, tests-must-anchor-on-observed-post, r9-style-allowlist-guards-must-key-by, compound-predicate-test-assertions)
- 1× inherited-gate-semantics (inherited-sim-test-gate-semantics)
- 1× rich-world-domain (rich-world-domain-entity-field-needs)
- 1× renumbering-grep-walk (renumbering-grep-walk-must-span-full)
- 1× pre-existing-failure-register (pre-existing-test-failures-need-named)
- 1× flowctl-done-state-sync (long-spec-flow-next-tasks-review-loop — flowctl-done writes canonical summary; prior worker-block narrative leaks; review cycles on artifact mismatches)
- Other 8 captures (sticky-witness-guard-swap, state-threading-specs, ga-path-precedence, stop-and-report-deferment, mark-complete-tree-walk, fn-131-ca-witness, specialized-rules-witness, recognition/apply-pipelines) are domain-specific symptom records; leave in bug/* only.

**Target subtree per cluster**:
- `knowledge/best-practices/` — how-to lessons that apply to future code (`test-pin-discipline`, `inherited-gate-semantics`, `renumbering-grep-walk`, `pre-existing-failure-register`)
- `knowledge/conventions/` — project-specific patterns (`rich-world-domain`)
- `knowledge/workflow/` — flow-next-process lessons (`flowctl-done-state-sync`)

## Acceptance

- **R1:** New entry `.flow/memory/knowledge/best-practices/test-pin-discipline.md` — synthesizes the 4 test-pinning lessons (mint-id vs tx-start timestamps, observe-real-post-state not predicate compounds, allowlists key by stable identifier, compound-predicate failure modes). Cross-references the 4 source bug captures.
- **R2:** New entry `.flow/memory/knowledge/best-practices/inherited-gate-semantics.md` — copy-pasted sim-test gates between sibling axes need re-validation per semantic meaning, not syntactic pattern (the fn-15.2 lesson where the off-final gate semantically meant "back on downwind" by coincidence on crosswind axis but didn't transfer to tailwind).
- **R3:** New entry `.flow/memory/knowledge/conventions/rich-world-domain.md` — time-varying state lives on the entity it concerns (`Aerodrome.weather`, `Runway.obstruction`); hard atomic cutover, no shims; no `SimState.*-by-aerodrome` flat maps when an entity exists. Cross-references fn-12 and fn-16 as precedents.
- **R4:** New entry `.flow/memory/knowledge/best-practices/renumbering-grep-walk.md` — when reconciling renumbering across docs, the grep walk must span the FULL §-number range affected by the edition's shift, not just the hypothesis's focal sections.
- **R5:** New entry `.flow/memory/knowledge/best-practices/pre-existing-failure-register.md` — pre-existing test failures carried across epics must be promoted to named `D-WORLD.N` (or similar prefix-family) register entries in `docs/deferments.md`; carve-out narrative across multiple epic specs is a smell, not a pattern.
- **R6:** New entry `.flow/memory/knowledge/workflow/flowctl-done-state-sync.md` — long-spec flow-next tasks where `flowctl done` writes its own canonical summary into the task spec md AND prior worker-block narrative leaks into the same file cause codex impl-review to cycle repeatedly on "spec md state doesn't match implementation state." Each round surfaces a different artifact mismatch (BLOCKED narrative, empty Evidence block, `.json` status=todo, set-cardinality inconsistency). Forward-applicable rule: after `flowctl done`, run a post-done state-synchronization sweep — clear stale narrative, populate Evidence, flip `.json` status — before re-invoking codex impl-review. Source: `.flow/memory/bug/build-errors/long-spec-flow-next-tasks-review-loop-2026-05-13.md` (verified per plan-review round 1).
- **R7:** `.flow/memory/MEMORY.md` index updated to list the 6 new entries with one-line descriptions. The index is currently empty; this task seeds it.
- **R8:** `flowctl memory search "<theme>" --limit 5 --json` returns the new knowledge entry as the top hit for each theme (e.g. `search "mint-id"` → test-pin-discipline; `search "rich-world-domain"` → rich-world-domain; etc.). Verified per-entry at task close.
- **R9:** No bug/* captures deleted. `find .flow/memory/bug -name "*.md" | wc -l` stays at 17 (or grows if the worker captures new lessons during this task — but no decrease).
- **R10:** Diff scope: 6 new files + 1 modified MEMORY.md = 7 files. No production code, no tests, no docs outside `.flow/memory/`.

## Early proof point

Task `fn-22-promote-6-session-memory-captures-into.1` validates the consolidation discipline (clarified per plan-review round 1 — the gate has two shapes, one for synthesis and one for 1:1 promotion):
- **Synthesis entries** (R1 test-pin-discipline, the 4-capture synthesis): MUST cite ≥2 source bug captures by file path AND demonstrate cross-cluster generalization (the synthesis is more than the sum of its sources).
- **1:1 promotion entries** (R2-R6): MUST cite their single source bug capture by file path AND offer a forward-applicable rule that generalizes beyond the symptom record (the "durable lesson" gate — if the entry just pastes the bug capture's body, it failed).
If any entry fails its gate, drop it from the batch and re-cluster.

## Quick commands

```bash
# Pre-task inventory
find .flow/memory/bug -name "*.md" | wc -l   # baseline: 17 (or whatever the count is at task time)
find .flow/memory/knowledge -name "*.md" | wc -l   # baseline: 0
ls .flow/memory/knowledge/best-practices/ .flow/memory/knowledge/conventions/ .flow/memory/knowledge/workflow/

# Post-task verification
find .flow/memory/knowledge -name "*.md" | wc -l   # expect: 6
find .flow/memory/bug -name "*.md" | wc -l   # expect: 17 (unchanged) per R9

# Memory search smoke test (R8)
.flow/bin/flowctl memory search "mint-id" --limit 3 --json | jq '.matches[] | {category, title}'
.flow/bin/flowctl memory search "rich-world-domain" --limit 3 --json | jq '.matches[] | {category, title}'
.flow/bin/flowctl memory search "inherited gate semantics" --limit 3 --json | jq '.matches[] | {category, title}'

# Index check
grep -c "^- " .flow/memory/MEMORY.md   # expect: ≥6 (the 6 new entries + any existing)
```

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | test-pin-discipline.md (best-practices) | fn-22-promote-6-session-memory-captures-into.1 | — |
| R2  | inherited-gate-semantics.md (best-practices) | fn-22-promote-6-session-memory-captures-into.1 | — |
| R3  | rich-world-domain.md (conventions) | fn-22-promote-6-session-memory-captures-into.1 | — |
| R4  | renumbering-grep-walk.md (best-practices) | fn-22-promote-6-session-memory-captures-into.1 | — |
| R5  | pre-existing-failure-register.md (best-practices) | fn-22-promote-6-session-memory-captures-into.1 | — |
| R6  | flowctl-done-state-sync.md (workflow) | fn-22-promote-6-session-memory-captures-into.1 | — |
| R7  | .flow/memory/MEMORY.md indexes the 6 entries | fn-22-promote-6-session-memory-captures-into.1 | — |
| R8  | flowctl memory search returns each entry as top hit per theme | fn-22-promote-6-session-memory-captures-into.1 | — |
| R9  | No bug/* captures deleted | fn-22-promote-6-session-memory-captures-into.1 | — |
| R10 | Diff is 6+1 = 7 files; no out-of-scope edits | fn-22-promote-6-session-memory-captures-into.1 | — |

## Review considerations

- **FP / type safety**: not applicable — markdown only.
- **Test architecture**: not applicable — no tests added.
- **Impact**: scoped to `.flow/memory/`. Downstream impact: every `flow-next:memory-scout` invocation surfaces these durable lessons, and reviewer subagents (codex impl-review backend, plan-sync) can search against them. **Reviewer focus**: confirm each entry generalizes (R7 R-ID quality gate); confirm cross-references to source bug captures are accurate file paths.
- **Operational ATC correctness / applicability**: not applicable — no runtime behavior changes.

## References

- Source captures (the 9 entries that get consolidated):
  - `.flow/memory/bug/test-failures/sim-test-pins-must-compare-against-2026-05-10.md`
  - `.flow/memory/bug/test-failures/tests-must-anchor-on-observed-post-2026-05-09.md`
  - `.flow/memory/bug/test-failures/r9-style-allowlist-guards-must-key-by-2026-05-09.md`
  - `.flow/memory/bug/test-failures/compound-predicate-test-assertions-2026-05-11.md`
  - `.flow/memory/bug/test-failures/inherited-sim-test-gate-semantics-may-2026-05-11.md`
  - `.flow/memory/bug/integration/rich-world-domain-entity-field-needs-2026-05-13.md`
  - `.flow/memory/bug/build-errors/renumbering-grep-walk-must-span-full-2026-05-11.md`
  - `.flow/memory/bug/test-failures/pre-existing-test-failures-need-named-2026-05-13.md`
  - `.flow/memory/bug/build-errors/long-spec-flow-next-tasks-review-loop-2026-05-13.md` (source for R6 flowctl-done-state-sync entry)
- Source captures that stay symptom-only (8 entries; not promoted):
  - `bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11.md`
  - `bug/build-errors/sticky-witness-guard-swap-can-regress-2026-05-09.md`
  - `bug/build-errors/state-threading-specs-need-consumption-2026-05-09.md`
  - `bug/build-errors/ga-path-precedence-reorder-when-adding-2026-05-10.md`
  - `bug/build-errors/stop-and-report-deferment-contracts-2026-05-09.md`
  - `bug/runtime-errors/fn-131-ca-witness-re-arm-fires-on-late-2026-05-10.md`
  - `bug/runtime-errors/specialized-rules-witness-does-not-gate-2026-05-10.md`
  - `bug/data/markcomplete-tree-walk-corrupts-future-2026-05-10.md`
- Index file:
  - `.flow/memory/MEMORY.md` — currently essentially empty; this task seeds it
- Tooling:
  - `.flow/bin/flowctl memory search` — the consumer surface
