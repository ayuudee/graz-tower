# Deferment register reorganization — four-bucket hybrid with code-as-record

## Overview

Today the project's deferment tracking is scattered across four uncoordinated surfaces:

1. **`~/.claude/plans/pilot-firewall.md § Deferments register`** (repo-external) — canonical store for ~21 OPEN D-AUDIT.*/D-PF.*/D-PASS-*/D-WORLD.* items from Passes 1-17 and fn-7/fn-8. Not CI-visible. Agents without `Read` on the user's home directory miss the entire register.
2. **fn-14 epic spec § Deferments register** (in-repo, epic-scoped) — 11 `D-PASS-g3a-react-*` siblings + `D-PASS-wind-state-migrate-to-aerodrome` + `D-PASS-cap413-edition-24-reconciliation`. Lives in one epic's spec; visible only when reading that epic.
3. **`DeferredContractsSpec.kt`** at `pilot/src/commonTest/kotlin/.../DeferredContractsSpec.kt` — 6 `@Ignore`d placeholder tests pinning D-PF.1/2/3 + D-AUDIT.5/6/10 with compile-checked acceptance shapes. Pattern is under-used (only `:pilot` carries it) AND **four of the six placeholders** pin deferments **already closed** (D-PF.2 / D-AUDIT.5 / D-AUDIT.6 / D-AUDIT.10) — orphan rot. (D-PF.1 and D-PF.3 are the two still-active placeholders.)
4. **Inline `D-PASS-* / D-AUDIT-*` code comments** — breadcrumbs in KDoc + tests. Six of these reference deferment IDs that exist **only as code comments** and were never filed in pilot-firewall.md: `D-PASS-fn6-snap-derived`, `D-PASS-g3a-obstruction-aerodrome-payload`, `D-PASS-continue-approach-pilot-readback`, `D-PASS-g3a-obstruction-kind-variants`, `D-PASS-g3a-obstruction-clearsAt-update`, `D-PASS-fixture-per-plan-filing-time`. No canonical store exists for these.

This epic establishes a **four-bucket hybrid** model. `docs/deferments.md` becomes a narrative **map** pointing at canonical stores; existing patterns (test files, epic stubs, inline breadcrumbs) become first-class canonical stores. Migration is comprehensive: every existing deferment gets a canonical-store record, not just a docs listing.

**The novelty** is making `docs/deferments.md` CI-visible (in-repo, greppable) and turning the existing scattered patterns into a coherent system. The four buckets pin where each deferment lives; the map's `Pinned at:` field is the single source of truth for navigation.

## Boundaries / non-goals

- **Out: closing any deferment.** This epic migrates and triages only. If an item appears closable mid-task (e.g. its blocker landed), file as a sibling deferment for follow-up; do not close inline.
- **Out: adding new deferments.** Only triaging what exists today. (Exception: meta-deferments about the new system itself — e.g. tooling automation over `docs/deferments.md` — explicitly filed at task time.)
- **Out: changing the `@Ignore` mechanism.** `kotlin.test.@Ignore` is the existing pattern; keep it.
- **Out: redesigning the inline code-comment convention.** Keep `// D-PASS-* / D-AUDIT-*` breadcrumbs as the third leg.
- **Out: tooling automation.** A script that parses `docs/deferments.md` and asserts every `Pinned at:` exists, or a detekt rule that requires every inline deferment ID to appear in the map, is a follow-up sibling. v1 ships the human-readable map only. Filed as `D-PASS-deferments-map-tooling-automation`.
- **Out: GitHub Issues integration.** Solo+AI workflow; flow-next is the project-management discipline.
- **Out: editing `~/.claude/plans/pilot-firewall.md` directly as a CI-validated step.** The file is repo-external; the user owns it. fn-18.2's flowctl-acceptance criterion is that fn-18.2 **provides the exact MIGRATED header text and logs external-follow-up status** (`pending` / `confirmed-by-user`) in evidence. The actual user-edit to apply the header is outside flowctl acceptance; the legacy register is only declared migrated after the user performs and reports that edit. fn-18.2 never writes `confirmed` unless the user has independently reported the edit.

## Strategy Alignment

Active tracks served by this plan:

- **Runtime simulator** — indirectly. fn-18 doesn't add simulator behaviour. It removes a class of meta-rot ("known deficiency invisible to CI / cross-agent / future-self because it lives on the user's laptop only") that has been compounding since Pass 1. Every future runtime-sim epic benefits because deferments become greppable in-repo and pinned to code or tests that break loudly when the underlying API shifts.
- **Tooling / agent infrastructure** (cross-cutting, no formal track). The four-bucket model + decision tree + `docs/deferments.md` map is doctrine for how agents — main, subagent, and review — discover known deficiencies. Replaces the `~/.claude/plans/pilot-firewall.md` pointer in `reference_audit_registers.md` memory.

## Decision context

### 1. `*DeferredContractsSpec.kt` lives per-module (high confidence per brief recommendation)

**Decided.** Each Kotlin module that has deferments anchored to its API surface gets its own `*DeferredContractsSpec.kt` in `commonTest/kotlin/xyz/easiersaid/twr/<module>/`. Today only `:pilot` has one. Modules likely to grow one at migration time: `:controller` (for D-PASS-fn6-snap-derived, D-PASS-g3a-obstruction-aerodrome-payload, multiple D-AUDIT.* items pinning controller-side shapes), `:protocol` (for D-PASS-g3a-obstruction-kind-variants, D-PASS-g3a-obstruction-clearsAt-update — these pin `protocol/.../RunwayObstruction.kt`'s shape), `:sim` (for D-PASS-fixture-per-plan-filing-time — pins `Fixture.kt`'s shape), and `:core` (likely empty in v1 — most `:core` deferments are FM/Lean-territory ones owned by `D-AUDIT-polygon-ctr` which goes to bucket 4).

**Compile-check guarantee per bucket** (hardened per plan-review round 5-6):
- **Bucket 1** — when the deferred API exists today, the `@Ignore`d test body **MUST** include at least one real compilable reference to the current API. **What counts** (per plan-review round 6): an assertion using a real type, OR a type construction of a real domain class, OR a function call returning a real value — i.e. a reference that participates in the test body's value flow. **Import-only references do NOT count** (IDE auto-cleanup can remove them; detekt unused-import rules can flag them). The minimum is one statement like `val x: RealType = constructor(...)` or `assertEquals(realFunction(...), expectedValue)` inside the test body, even though `@Ignore` skips runtime execution.
- **Bucket 2** — when the API is missing, the test body is **commented-out pseudo-code only**; no compile-check is possible until the API lands. The KDoc names what API is missing so an implementer knows what to add when uncommenting.

The blanket claim "bucket 1/2 = compile-checked acceptance shape" applies only to bucket 1 in its strong form. Bucket 2 is narrative-in-code that becomes compile-checked when the implementer flips `@Ignore` off + uncomments + the new API exists. The convention doc spells out this distinction with concrete code examples.

**Why per-module not central `:protocol`-test:** proximity wins. The whole point of bucket 1 is that the test references the API that exists today; when that API changes (rename, signature change, new sealed leaf), the deferment record breaks loudly via test compile failure. Centralising in `:protocol` would force cross-module imports the test runner doesn't have; per-module keeps the dependency direction sane.

**File naming convention**: `<Module><Capitalised>DeferredContractsSpec.kt` (e.g. `ControllerDeferredContractsSpec.kt`) OR keep `DeferredContractsSpec.kt` namespaced by package (current pilot pattern). **Recommend the latter** — the package is the discriminator; no need to repeat "Pilot" in both filename and package. The class itself is `class DeferredContractsSpec` in each.

Alternative considered + rejected: a single `:protocol/commonTest/DeferredContractsSpec.kt` aggregator. Rejected because `:protocol` cannot import from `:pilot` / `:controller` / `:sim` — the tests need access to the deferred-API call shapes that live in the consumer modules.

### 2. Status taxonomy: four leaves (decided per brief)

**Decided.** Four statuses, exhaustive, no extension without spec amendment:

- **`blocked`** — waiting on prerequisite work that does not yet exist (model field, scenario, third aerodrome). Has a clear unblock trigger. Most D-AUDIT.9.*-FOLLOWUP items are blocked.
- **`planned`** — has a flow-next epic stub (`todo` status). Bucket 3 entries. fn-15/fn-16/fn-17 are the existing examples.
- **`narrative`** — bucket 4 entry with no clear pin today. May graduate to `blocked` or `planned` later.
- **`closed`** — kept in `## Archive` section for history. Struck through or marked CLOSED in the active section.

**Anti-decision**: do NOT add `active`, `in-progress`, `partial`, `paused`, or `accepted-as-by-design` statuses. Those distinctions are properly the epic-status surface (`open`/`done`) or the inline code comment ("By design / accepted" pattern in `.plan`). The four-leaf taxonomy is exhaustive for the migration scope.

### 3. Archive policy: closed entries stay in `docs/deferments.md § Archive` (decided per brief; format clarified per plan-review round 3 + 6)

**Decided.** Closed entries are moved to a `## Archive` section at the bottom of `docs/deferments.md`. They do NOT get deleted. Rationale: grep-discoverability beats git-archaeology; "this was deferred and then closed by X" is information future agents will want.

**Format (clarified per plan-review round 3 + 6)**: archive entries use `### D-...` headings just like active entries, so `grep '^### D-' docs/deferments.md` counts both active and closed entries cleanly. Archive entries carry a minimal body — three fields:

```markdown
### D-PF.2 — RunwayAssignmentSource sealed discriminator
**Status:** closed
**Closed by:** Pass 5 (commit b8b099a) — see ~/.claude/plans/pass-5-entities-and-aircraft-intent.md
**Enforcement:** `RunwayAssignmentSource` sealed type in `protocol/RunwayAssignment.kt`; `applyPrecedence` invariant test in `ProcessInstructionRunwayDerivationSpec.kt`
```

Three fields: `Status: closed` + `Closed by:` + `Enforcement:`. The `Enforcement:` field (per plan-review round 6) preserves the enforcement-surface references from the now-deleted code comment blocks (e.g. test files that pin the contract). Without it, archive entries lose the informativeness of the deleted comments. Rationale: closure history must be at least as informative as the state being removed.

NO `Why:` / `Blocked on:` / `Pinned at:` fields on archived entries. The active register's job is forward-looking; the archive's job is "what closed this, what code enforces the contract now."

### 4. `docs/deferments.md` lives at repo root `docs/`, not under `.flow/` (decided per brief)

**Decided.** `docs/deferments.md` is general project documentation, not flow-specific runtime state. It lives at `docs/deferments.md` (alongside `docs/design/`, `docs/test-standards.md`). `.flow/` is for epic/task state managed by flowctl.

### 5. `Pinned at:` field is plain text, greppable, not markdown link (decided per brief)

**Decided.** Format: `path/to/File.kt::test_name_or_section` for tests; `fn-N-epic-id` for epics; `narrative only` for bucket 4. Plain text. Markdown links break when files move; plain text survives `grep -rn` and IDE jump-to-line.

**Examples**:
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt::D-PF1 airport requiring startup clearance has REQUEST_STARTUP and AWAIT_STARTUP_APPROVAL`
- `fn-15-g3a-react-tailwind-pilot-reactive-go`
- `narrative only`

### 6. Bucket transition convention: in-place status update, history-preserving (decided per brief)

**Decided.** When a deferment moves from bucket-4 (`narrative`) to bucket-1 (now has a test) or bucket-3 (now an epic), the docs entry updates **in place** — `Status:` field flips (`narrative → blocked` or `narrative → planned`), `Pinned at:` updates to point at the new test or epic ID, and a parenthetical "(promoted to bucket N on YYYY-MM-DD)" gets appended to the `Why:` field's last sentence. The old `Pinned at: narrative only` value moves into a `Previously:` line if useful for history; otherwise dropped (git history preserves the diff).

Anti-pattern: do NOT add a new entry and mark the old "obsolete". One entry per deferment ID throughout its life.

### 7. Schema for `docs/deferments.md` entries (locked)

**Decided.** Every entry uses this exact shape:

```markdown
### <ID> — <One-line title>
**Status:** <blocked | planned | narrative | closed>
**Pinned at:** <path/to/file.kt::test_name | fn-N-epic-id | narrative only>
**Blocked on:** <free text; only present when status=blocked>
**Why:** <1-3 sentences. Why this is deferred, what the world looks like when it lands. Reality-anchored, no "operationally acceptable" softening.>
**Closes by:** <new epic when activated | inline in epic fn-N | archived when prerequisite lands | epic fn-N (planned)>
```

Field order is fixed. `Blocked on:` is omitted entirely (not blank, not "n/a") when `Status ≠ blocked`. Other fields are always present. The 1-3 sentence cap on `Why:` is enforced by review.

Headers (`### <ID>`) use the existing dash-separated IDs from pilot-firewall.md (e.g. `D-PF.1`, `D-AUDIT.9.II-FOLLOWUP`, `D-PASS-g3a-react-tailwind-limit`). No renaming during migration.

**Heading discipline** (locked per plan-review round 1): in `docs/deferments.md`, ONLY `### D-...` headings denote a deferment entry. Section-organising headings use other depths (`##`, `####`) or prose-only text. This makes the file mechanically scannable — a future tooling pass (`D-PASS-deferments-map-tooling-automation`) can count entries by grepping `^### D-`. Placeholder text in the empty body MUST NOT use `### D-...` for non-entry prose. The convention doc spells this out.

**Decision #7a — Locked structure for `docs/deferments.md`** (clarified per plan-review round 2):

```
# Deferments Register
<header paragraph>
<status taxonomy paragraph + heading-discipline note>

## D-PF
<entries: ### D-PF.1, ### D-PF.3, ...>

## D-AUDIT
<entries: ### D-AUDIT.2.C-FOLLOWUP, ### D-AUDIT.3.II-FOLLOWUP, ...>

## D-PASS
<entries: ### D-PASS-deferments-map-tooling-automation, ### D-PASS-g3a-react-tailwind-limit, ...>

## D-WORLD
<entries: ### D-WORLD.1>

## Archive
<closed-entry one-liners>
```

No `## Active` wrapper. Prefix subsections at `##` depth directly. The four `## D-*` sections hold all active entries grouped by ID prefix; `## Archive` holds closed history. Empty prefix subsections (during fn-18.1's empty-body scaffold state) carry a one-line prose placeholder, NOT a `### D-...` header — e.g. `_(populated by fn-18.2 from pilot-firewall.md)_`.

### 8. Convention doc lives at `docs/deferments-CONVENTION.md`, NOT inside AGENTS.md (decided)

**Decided.** Convention lives in its own file (`docs/deferments-CONVENTION.md`) and is **referenced** from `AGENTS.md § Project Plan` and from the principal section of `AGENTS.md`. Rationale: AGENTS.md is the commandments + plumbing; deferment convention is doctrine. Keeping them in separate files lets each evolve independently, and the convention is naturally co-located with `docs/deferments.md` itself.

`AGENTS.md` gets a single bullet under "Project Plan" pointing at `docs/deferments.md` as the discovery entry point, and one line under "Self-assessment before review" noting that any deferred work surfaced during a pass must land in `docs/deferments.md` (one of the four buckets) before commit.

**Boundary rule for `.plan` vs `docs/deferments.md`** (added per plan-review round 3):

- **`.plan`** stays the canonical backlog for: ordinary known issues with `Impact: H/M/L` ratings, in-progress workstream notes, IFR wiring gaps, controller backlog items, "by-design / accepted" rationale entries. Short-ID format: `B3`, `IFR-1`, `RR-*`, `M*`. These are operational backlog items — what's wrong now, what we'd fix if we had time.

- **`docs/deferments.md`** stays the canonical register for: named `D-*` deferments (`D-PF.*`, `D-AUDIT.*`, `D-PASS-*`, `D-WORLD.*`) — work consciously parked with a real-fix contract (the eventual API shape, the missing prerequisite, the test that lands when it's closed). These are deferred contract/spec commitments — what we promised about the future when we shipped something incomplete.

- **Cross-reference**: if a `.plan` item warrants a real-fix contract (eventual API shape, blocked-on prerequisite, named closure trigger), it becomes a `D-*` deferment in `docs/deferments.md` and the `.plan` entry is closed with a one-line pointer. The reverse — a deferment lacking the contract shape — does not exist by Decision #2 (`narrative` status covers cross-cutting items, but they're still named D-* entries).

- **When in doubt**: if the item has a named ID with a `D-*` prefix → `docs/deferments.md`. Otherwise → `.plan`.

`AGENTS.md § Project Plan` carries this boundary rule explicitly (one short paragraph), so future agents don't have to derive it.

### 9. Bucket decision tree (locked)

**Decided.** Interview-question convention doc:

> **Does this deferment have a clear test shape I could write today** (even as `@Ignore`d)?
> - **Yes, the API exists today** → bucket 1 (`@Ignore`d test in module's `DeferredContractsSpec.kt`).
> - **Yes, but the API is missing** (e.g. "needs `PilotInput.nearbyTraffic`") → bucket 2 (same file, with commented-out call next to the assertion).
> - **No, but it's multi-task scope** → bucket 3 (flow-next epic stub, status `todo`).
> - **No, and it's doctrinal / cross-cutting / blocked-on-real-world** → bucket 4 (`docs/deferments.md` entry only).

Buckets 1 and 2 are mechanically identical (same file, same `@Ignore` annotation). Bucket 2 has a commented-out reference that becomes the uncomment-and-fill site once the API lands. The split is **decision-tree clarity** — the implementer needs to know whether the work also requires API extension or just contract pinning.

### 10. Migration scope: comprehensive, no carveouts (locked per `feedback_no_corners.md`)

**Decided.** Every deferment ID across the four sources gets a record. No "we'll get the rest in fn-19" carve-out. The scope is naming, triaging, and pinning — not closing — so the work is bounded and finite.

**Planning-time counts are explicitly non-authoritative.** The numbers below are from greps at planning time, included as a rough size sanity-check. fn-18.2 and fn-18.3 each begin by running the same greps and writing the resulting **locked inventory artifact** into their done summary BEFORE any migration work begins. Acceptance is exhaustive against the locked list: "every ID in the locked list appears exactly once in `docs/deferments.md`." The numbers below are reference-only and may differ from the locked count by ±10 once the greps run at task time.

**Placeholder IDs in epic specs**: some sources (e.g. fn-17 spec) file conditional deferments like `D-PASS-cap413-edition-24-<section>` whose `<section>` is a meta-placeholder, not a stable ID. **Decision (per plan-review round 2)**: such placeholder IDs are NOT entries in `docs/deferments.md`. The migration only files entries with concrete, stable IDs. The conditional language in the source spec stays as-is; the migration captures it under whatever concrete ID lands when (or if) the conditional fires. Document this in the done summary if any placeholder IDs are observed.

**Inventoried IDs to migrate** (from research at planning time; verify and lock the exact list at task time):

**From `~/.claude/plans/pilot-firewall.md` OPEN items** (21):
- D-PF.1, D-PF.3, D-PF.8
- D-AUDIT.2.C-FOLLOWUP, D-AUDIT.2.F-FOLLOWUP
- D-AUDIT.3.II-FOLLOWUP
- D-AUDIT.4.A.II-FOLLOWUP, D-AUDIT.4.B-FOLLOWUP, D-AUDIT.4.D.II-FOLLOWUP
- D-AUDIT.6.C-FOLLOWUP
- D-AUDIT.7.II-FOLLOWUP, D-AUDIT.7.III-FOLLOWUP
- D-AUDIT.8.II-FOLLOWUP, D-AUDIT.8.III-FOLLOWUP, D-AUDIT.8.IV-FOLLOWUP
- D-AUDIT.9.II-FOLLOWUP, D-AUDIT.9.III-FOLLOWUP, D-AUDIT.9.IV-FOLLOWUP, D-AUDIT.9.V-FOLLOWUP
- D-AUDIT.11
- D-PASS-13.3-II-FOLLOWUP, D-PASS-17.1, D-PASS-17.2, D-PASS-17.3-FOLLOWUP
- D-WORLD.1
- D-AUDIT-arp-proxy-runtime, D-AUDIT-polygon-ctr, D-AUDIT-airac-cycle-tracking, D-AUDIT-ljmb-polygon
- D-PASS-g1-diagnostics-typed-events
- D-PASS-cross-aircraft-step-on
- D-PASS-pilot-mid-tng-fullstop-recovery

(Audit shows above list = ~32 OPEN IDs once D-AUDIT.4.* / .6.* / .7.* / .8.* / .9.* FOLLOWUPs are fully enumerated; the "~22" in the brief was a quick scan. Plan-review must verify at task time and update the count.)

**From `fn-14` epic spec § Deferments register** (11 listed in spec; brief said 13. Reconciliation: spec lists 9 g3a-react siblings + wind-state + cap413 = 11. Brief's "13" was probably a miscount. Task-time inventory grep is authoritative.):
- D-PASS-g3a-react-tailwind-limit (→ fn-15)
- D-PASS-g3a-react-gust-evaluation
- D-PASS-g3a-react-wind-variability-dynamics
- D-PASS-g3a-react-multi-aircraft-crosswind
- D-PASS-g3b-react-cross-aerodrome-crosswind
- D-PASS-g3a-react-other-poh-triggers
- D-PASS-g3a-react-personal-minimums
- D-PASS-g3a-react-atis-cadence-sensing
- D-PASS-g3a-react-vrb-handling
- D-PASS-wind-state-migrate-to-aerodrome (→ fn-16)
- D-PASS-cap413-edition-24-reconciliation (→ fn-17)

**From `fn-15` epic spec** (6 new siblings):
- D-PASS-g3a-react-tailwind-gust-evaluation
- D-PASS-g3a-react-multi-aircraft-tailwind
- D-PASS-g3a-react-combined-wind-vector
- D-PASS-g3a-react-tailwind-atis-cadence
- D-PASS-g3a-react-tailwind-condition-corrections
- D-PASS-g3a-react-tailwind-personal-minimums

**From `fn-17` epic spec** (2 concrete + 1 conditional-placeholder):
- D-PASS-cap413-edition-24-rename-pending-pdf
- D-PASS-cap413-edition-24-<section> — **conditional placeholder, NOT a stable ID**. No `docs/deferments.md` entry unless a concrete ID lands. Done summary records the conditional language.
- D-PASS-cap413-principle-text-deep-refresh

**From `DeferredContractsSpec.kt` orphan tests** (4 placeholder tests where the deferment is CLOSED but the `@Ignore` test remains; count corrected from "3" per plan-review round 8 — the four IDs listed below are the correct cardinality):
- D-PF.2 (CLOSED Pass 5 — test orphaned; either delete test or convert to active regression)
- D-AUDIT.5 (CLOSED Pass 7 — same)
- D-AUDIT.6 (CLOSED Pass 11 — same)
- D-AUDIT.10 (CLOSED Pass 11 — same)

**From inline code comments only** (~6 IDs never filed in pilot-firewall.md):
- D-PASS-fn6-snap-derived
- D-PASS-g3a-obstruction-aerodrome-payload
- D-PASS-continue-approach-pilot-readback
- D-PASS-g3a-obstruction-kind-variants
- D-PASS-g3a-obstruction-clearsAt-update
- D-PASS-fixture-per-plan-filing-time

**From `.plan` (in-repo operational backlog)** (added per plan-review round 10 — `.plan` contains concrete `D-*` IDs that fn-18.3's R15 grep would discover; explicit migration source so they're triaged before the gate, not surprised at it):

Planning-time scan (non-authoritative):
- `D-PASS-g1-diagnostics` (DONE / CLOSED-PARTIAL — fn-18.3 files as Archive)
- `D-PASS-g1-diagnostics-typed-events` (OPEN — already in pilot-firewall.md OPEN list, dedup at task time)
- `D-PASS-cross-aircraft-step-on` (OPEN — already in pilot-firewall.md OPEN list, dedup at task time)
- `D-PASS-pilot-mid-tng-fullstop-recovery` (OPEN — already in pilot-firewall.md OPEN list, dedup at task time)

**Handling** (per plan-review round 10): fn-18.3 greps `.plan` for `D-*` IDs explicitly at task start. For each ID:
- If status in `.plan` is OPEN and the ID is also in pilot-firewall.md OPEN list (handled by fn-18.2): no new docs entry — fn-18.2 already filed it. fn-18.3 verifies the `.plan` line doesn't duplicate the canonical record (boundary rule from Decision #8 — `.plan` keeps short-ID format `B*/IFR-*/RR-*/M*` items; D-* items live in `docs/deferments.md`).
- If status in `.plan` is OPEN and the ID is NOT in pilot-firewall.md or epic specs: bucket-triage and file an active entry in `docs/deferments.md`.
- If status in `.plan` is DONE / CLOSED-PARTIAL: file as an Archive entry (Status + Closed-by + Enforcement per Decision #3).
- The `.plan` line itself is left intact — fn-18 does NOT edit `.plan` (separate boundary; `.plan` is the user's operational backlog). The cross-reference is one-way: `docs/deferments.md` is the canonical for D-* IDs; the `.plan` mention is a working-note byproduct that R15's grep tolerates by virtue of the entry already existing in docs.

**Approximate total**: 60-67 IDs from the five sources combined (~32 pilot-firewall.md + ~19 fn-14/15/17 specs + ~6 inline-only + 4 orphan archives + 1-4 .plan-only IDs, dependent on dedup). **The locked inventory artifact in fn-18.2/.3 done summaries is the source of truth.** Acceptance R7 is "every ID in the locked list is mapped to a bucket and has exactly one `docs/deferments.md` entry."

### 11. Memory update is acceptance-level (locked per brief)

**Decided.** `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/reference_audit_registers.md` becomes stale after fn-18 ships. The acceptance criterion (R10) is that this memory entry is rewritten to:

- Point at `docs/deferments.md` as the **primary** discovery entry point (in-repo, CI-visible, greppable).
- Explain the four-bucket model with one-sentence summaries.
- Keep `~/.claude/plans/pilot-firewall.md § Deferments register` as a **secondary** historical reference for closed items pre-Pass-17 (because the user's pass notes there are richer than the `## Archive` lines will be).
- Update the `MEMORY.md` index entry's prose if needed.

### 12. `pilot-firewall.md` "MIGRATED" header + commit-SHA timing (locked per brief; sequencing clarified per plan-review rounds 2-6)

**Decided.** Two distinct acceptance criteria (split per plan-review round 6 — original conflation was semantically muddy):

- **R11 (task-internal acceptance)**: fn-18.2 provides the exact MIGRATED header text and records it in the task's evidence block as an external follow-up for the user. This is testable at task-close and is acceptance-bound for fn-18.2.
- **External post-task follow-up (NOT a flowctl acceptance criterion)**: the user pastes the header into `~/.claude/plans/pilot-firewall.md § Deferments register`. This happens outside flowctl's acceptance gate because pilot-firewall.md is repo-external. fn-18.2 records the follow-up in evidence; it is NOT marked as "confirmed migrated" until the user actually edits the file.

Header **template** (commit SHA filled at user-edit time):

```
**MIGRATED to docs/deferments.md per fn-18 on 2026-MM-DD (commit <pending>).**
The entries below are preserved for historical context (pass-by-pass narrative).
For the active deferment register, see `docs/deferments.md` in the repo.
```

**Why the split**: a single acceptance criterion that says "header is present" is unenforceable from CI because pilot-firewall.md is outside the repo; a single criterion that says "agent provided text" is too weak because it leaves the legacy register potentially unmarked indefinitely. The split is honest: fn-18.2 owns "I provided the exact text and logged it"; the user owns "I applied the edit." Both happen; only the agent-side is gated by flowctl acceptance.

**R12 (the fn-14/15/17 epic-spec redirect lines)** is purely in-repo and IS flowctl-acceptance-bound. fn-18.3 performs the redirects via `flowctl epic set-plan`; the SHA may be `<pending>` for back-fill, but the redirect line existence is checked at done-time.

### 13. Orphan-test handling for closed deferments (decided; clarified per plan-review round 2)

**Decided.** The four `DeferredContractsSpec.kt` placeholder tests for closed deferments (D-PF.2, D-AUDIT.5, D-AUDIT.6, D-AUDIT.10) are handled in fn-18.2 as follows:

- Each test that references a CLOSED deferment is **either** (a) **deleted outright**, OR (b) **converted** to a non-`@Ignore`d regression test if the closing pass left an unverified shape.
- **No per-file closure comments left behind.** Per plan-review round 2: leaving a "// Closed by Pass N" comment in the file is neither compile-checked nor strongly discoverable. The authoritative closure record lives in `docs/deferments.md § Archive` (one line per closed entry pointing at the closing pass / commit / epic). If the orphan test gets deleted, it disappears cleanly; the closure record in Archive is the discoverable trail.
- Existing **closed-deferment block-comments** that already live in `DeferredContractsSpec.kt` (e.g. the D-PF.5 / D-PF.6 / D-AUDIT.3 comments at lines 92-129) are themselves orphan rot — they were comment-blocks-pretending-to-be-test-history. fn-18.2 deletes those comment blocks too; their content is captured authoritatively in `docs/deferments.md § Archive`.
- Per `feedback_no_corners.md`: no silent `@Suppress`, no "keep the placeholder for now," no "keep the comment for historical narrative." Each gets a decision.
- D-PF.2 conversion-vs-delete is decided at task time after reading Pass 5's closure record; D-AUDIT.5 / .6 / .10 similarly read Pass 7 / Pass 11. Fallback (per plan-review round 1): if the per-pass plan file is missing, resolve from in-repo tests + git log + the closure summary in pilot-firewall.md.
- The archive entries in `docs/deferments.md` reference whatever the test became (deleted → no `Pinned at:` field; converted → file:test path in `Pinned at:`).

### 14. Meta-deferments surfaced by this epic (decided)

**Decided.** fn-18 itself spawns the following meta-deferments:

- **`D-PASS-deferments-map-tooling-automation`** — bucket 4 narrative. Future tooling: detekt rule or script that (a) parses `docs/deferments.md`, (b) verifies every `Pinned at: <test>` actually exists, (c) verifies every inline `D-PASS-*` / `D-AUDIT-*` / `D-PF.*` code comment appears as an ID in the map. Triggers automatic CI fail on drift. Filed in fn-18.1's `docs/deferments.md` initial scaffold.
- **`D-PASS-deferments-renumbering-discipline`** — bucket 4 narrative. The current ID scheme is inherited from the pre-flow-next pass-N tracking (`D-AUDIT.N`, `D-PASS-N.x`) and fn-7+ epic-derived dash-suffixed names (`D-AUDIT-lowg-ctr-radius`). The mix produces inconsistent grep results. Future cleanup: a single convention (probably the dash-suffixed form, since it survives renumbering). v1 keeps existing IDs as-is to bound scope.
- **`D-PASS-deferments-cross-ref-from-impl-review`** — bucket 4 narrative. When a code-review agent surfaces a finding that the principal agent defers, the convention for "this becomes a deferment" isn't yet automated. Today it's a manual "file it as a sibling deferment" step. Future tooling: a `/flow-next:defer` skill or similar that prompts for bucket assignment + writes the record.

### 15. Per-task scope split (locked baseline; plan-review may reshape)

**Decided.** Three tasks:

- **fn-18.1 — Convention + scaffolding.** Write `docs/deferments.md` (empty body except schema explanation + four-bucket header + `## Archive` stub). Write `docs/deferments-CONVENTION.md` (decision tree, examples, bucket-1 vs bucket-2 distinction). Update `AGENTS.md` (single bullet pointing at `docs/deferments.md`). Provide `reference_audit_registers.md` auto-memory entry replacement text (R4a) and perform the write when memory directory is reachable (R4b — fallback to evidence-block follow-up if not). File the three meta-deferments in this epic's spec for migration via fn-18.2. No deferments migrated yet — just scaffold.
- **fn-18.2 — Migrate pilot-firewall.md OPEN items + handle orphan tests.** ~32 items triaged. New `@Ignore`d tests written for bucket 1/2 items where contract has clear shape and no test exists today (per-module `DeferredContractsSpec.kt` created or extended). New epic stubs created for bucket 3 items where multi-task scope is clear. The four orphan tests (D-PF.2 / D-AUDIT.5 / .6 / .10) are deleted-or-converted. fn-18.2 provides the exact pilot-firewall.md "MIGRATED" header text and records external-follow-up status (`pending` / `confirmed-by-user`) in evidence — fn-18.2 NEVER declares the external edit complete; the user's edit to pilot-firewall.md is outside flowctl acceptance (per R11 normalization in plan-review rounds 7-8).
- **fn-18.3 — Migrate fn-14/15/17 epic-spec siblings + inline-only IDs.** 13 fn-14 siblings + 6 fn-15 siblings + 3 fn-17 siblings + ~6 inline-only IDs. fn-15/fn-16/fn-17 (already epic stubs in `todo`) get bucket-3 docs entries pointing at them. fn-14 epic spec's `## Deferments register` section gets updated to point at `docs/deferments.md` as the single source of truth (fn-15 / fn-17 specs similarly).

Plan-review may propose 2 tasks or 4. The 3-task split is concrete and locked unless reshape is justified.

## Acceptance

- **R1** — `docs/deferments.md` exists at repo root with: (a) header explaining the four-bucket model and pointing readers at `docs/deferments-CONVENTION.md`; (b) Active body organised by **prefix subsections** (`## D-PF`, `## D-AUDIT`, `## D-PASS`, `## D-WORLD`) under a `## Active` parent OR (chosen variant locked in Decision #7a) prefix subsections at h2 depth directly; (c) `## Archive` section for closed entries; (d) every active OPEN deferment from the inventory (R7) appears as an `### <ID>` block matching the schema locked in Decision #7.
- **R2** — `docs/deferments-CONVENTION.md` exists at repo root with: (a) the four-bucket model and decision tree from Decision #9; (b) bucket-1 vs bucket-2 distinction (commented-out call); (c) `Pinned at:` field format with examples; (d) bucket-transition convention (Decision #6); (e) archive policy (Decision #3); (f) status taxonomy with definitions (Decision #2); (g) at least one **schema-shape example** per bucket (per plan-review round 5: at fn-18.1 time real entries don't exist yet; schema examples suffice). Examples may be updated to point at real entries post-fn-18.2/.3 as a documentation polish, but fn-18.1's acceptance is schema-shape-only.
- **R3** — `AGENTS.md` updated with: (a) one bullet under "Project Plan" pointing at `docs/deferments.md` as the discovery entry point for known deficiencies; (b) one line under "Self-assessment before review" noting deferred work landed during a pass must be filed in `docs/deferments.md`. The existing `.plan` reference stays; the new pointer **supplements**, does not replace.
- **R4** — `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/reference_audit_registers.md` rewritten per Decision #11. Two distinct sub-criteria (split per plan-review round 7 — original criterion conflated repo-side and external-side actions, same shape as R11):
  - **R4a (task-internal acceptance)**: fn-18.1 produces the exact replacement text and records it in the task's evidence block. This is testable at task-close and is acceptance-bound for fn-18.1. New text references `docs/deferments.md` as primary and `~/.claude/plans/pilot-firewall.md` as historical secondary. MEMORY.md index entry's prose updated if needed (also acceptance-bound when the memory directory IS writable — the prose update is in-repo-adjacent and lives in the same external location).
  - **R4b (external follow-up, NOT a flowctl acceptance criterion)**: the actual write to `reference_audit_registers.md` and `MEMORY.md` happens at the file location. When the memory directory is reachable and writable from the agent's environment, fn-18.1 performs the write and R4b is confirmed in evidence. When unreachable/unwritable (the plan-review-round-4 fallback case), R4b is logged as a pending non-blocking external follow-up. Both states satisfy fn-18.1 acceptance; R4a is the gate.
- **R5** — Per-module `DeferredContractsSpec.kt` test files exist in every module that has at least one bucket-1 or bucket-2 deferment after migration. Today only `:pilot` has one; at minimum `:controller`, `:protocol`, `:sim` will gain one if any of their inventoried deferments fits bucket 1/2. Empty `DeferredContractsSpec.kt` files are NOT created — only modules with actual contract-shape deferments get the file.
- **R6** — Every existing `@Ignore`d test in `pilot/src/commonTest/.../DeferredContractsSpec.kt` either (a) maps to an active deferment in `docs/deferments.md` with `Pinned at:` matching the file::test path, OR (b) is deleted/converted per Decision #13. No orphan `@Ignore` test remains.
- **R7** — Comprehensive migration: every deferment ID enumerated in Decision #10 (planning-time counts: pilot-firewall.md OPEN ~32 + fn-14 spec 11 + fn-15 spec 6 + fn-17 spec 2 concrete + DeferredContractsSpec orphans 4 + inline-only ~6 ≈ ~61 IDs) appears as an entry in `docs/deferments.md` (either active body or `## Archive`). **All counts here are planning-time estimates and non-authoritative**; the locked inventory artifacts produced in fn-18.2 and fn-18.3 done summaries are the source of truth. Acceptance is **non-empty AND exhaustive**: every ID in the locked inventory has exactly one entry.
- **R8** — Bucket distribution recorded in fn-18.3's done summary: counts per bucket (1/2/3/4) and per-source (pilot-firewall.md / fn-14 / fn-15 / fn-17 / inline-only). Helps future audit see the shape of what landed.
- **R9** — Bucket-3 epic stubs (fn-15, fn-16, fn-17 — already existing) confirmed cross-referenced. Any new bucket-3 epic stubs created during migration (if a pilot-firewall.md item turns out to need multi-task scope) are created via `flowctl epic create` and set to `todo` status before the docs entry is written.
- **R10** — Inline `// D-PASS-*` / `// D-AUDIT-*` / `// D-PF.*` code comments cross-referenced: every ID appearing in any `.kt` source file under `pilot/` `controller/` `protocol/` `sim/` `core/` `migration/` appears as a `docs/deferments.md` entry (active or archive). Verified via grep at task time. No silent ID drift.
- **R11** — fn-18.2 provides the exact MIGRATED header text per Decision #12 AND records it in the evidence block as an external follow-up. This is a "text provided + follow-up logged" criterion, NOT "header present in pilot-firewall.md." The user-edit to pilot-firewall.md is a separately-tracked external follow-up; the legacy register is not declared migrated until the user actually applies the edit. Acceptance for R11 is satisfied at task-close as long as the text is provided and the follow-up is logged.
- **R12** — fn-14 / fn-15 / fn-17 epic specs' `## Deferments register` sections each carry a redirect line per Decision #12 with date + per-fn-18.3 attribution. fn-16 epic spec receives the same redirect IF its `## Deferments register` contains any entries that get migrated by this task (conditional — verify at task time). Commit SHA may be `<pending>`. Existing entries preserved as historical artefact (do NOT delete the entry blocks).
- **R13** — Three meta-deferments (`D-PASS-deferments-map-tooling-automation`, `D-PASS-deferments-renumbering-discipline`, `D-PASS-deferments-cross-ref-from-impl-review`) filed in `docs/deferments.md` per Decision #14, all as bucket 4 (`narrative`).
- **R15 (final whole-repo exhaustiveness gate, added per plan-review round 7)** — at fn-18.3 close, run a **whole-repo grep** across `*.md` (specs, tasks, design docs, repo-root markdown), `*.kt` (all modules), and `.plan` for every `D-*` ID pattern (`D-PASS-`, `D-AUDIT.`, `D-AUDIT-`, `D-PF.`, `D-WORLD-`, `D-WORLD.`). Compare the de-duplicated ID set against `docs/deferments.md`'s set of `### D-...` headings. Every concrete ID found in the whole-repo grep MUST appear exactly once in `docs/deferments.md` (active body or `## Archive`). Documented placeholder IDs (e.g. `D-PASS-cap413-edition-24-<section>`) are excluded by virtue of being placeholders, not stable IDs. The de-dup-and-diff result is recorded in fn-18.3's done summary; any drift fails R15. This is the final exhaustiveness gate that catches any ID neither fn-18.2 nor fn-18.3's per-source inventory captured — meta-defence against inventory rot.

  **Quick command** (regex hardened per plan-review round 8 — tail-char restricted to `[A-Za-z0-9_]` so placeholder IDs like `D-PASS-cap413-edition-24-<section>` cannot match the prefix `D-PASS-cap413-edition-24-`; duplicate-detection split out separately because `sort -u` would hide duplicates; one-way containment instead of two-way diff per plan-review round 9 — docs-only IDs are LEGITIMATE because archive entries may not appear anywhere else after orphan-test deletion):
  ```bash
  # Repo-wide ID discovery — markdown + kotlin + .plan, all D-* prefixes
  # Tail char is alnum/underscore only — no trailing dot or dash, which excludes
  # placeholder forms like D-PASS-cap413-edition-24- (which would otherwise match).
  grep -rhEo "D-(PASS|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|PF)\.[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]" \
    --include="*.kt" --include="*.md" --include=".plan" . \
    | sort -u > /tmp/fn-18-3-repo-ids.txt

  # docs/deferments.md ID set — raw (NOT -u; we use this for duplicate detection)
  grep -hEo '^### D-[A-Za-z0-9_.-]+' docs/deferments.md | sed 's/^### //' \
    | sort > /tmp/fn-18-3-docs-ids-raw.txt
  sort -u /tmp/fn-18-3-docs-ids-raw.txt > /tmp/fn-18-3-docs-ids.txt

  # Duplicate-heading check — fails R15 if any line appears twice in docs
  uniq -d /tmp/fn-18-3-docs-ids-raw.txt > /tmp/fn-18-3-docs-dups.txt
  test ! -s /tmp/fn-18-3-docs-dups.txt || { echo "DUPLICATE HEADINGS in docs/deferments.md:"; cat /tmp/fn-18-3-docs-dups.txt; exit 1; }

  # One-way containment: every concrete repo-wide ID MUST appear in docs.
  # Docs-only IDs (archive entries that have no remaining code/markdown anchor
  # after orphan-test deletion) are legitimate — they appear in docs but not in
  # repo grep, and that's the expected state.
  comm -23 /tmp/fn-18-3-repo-ids.txt /tmp/fn-18-3-docs-ids.txt > /tmp/fn-18-3-id-missing-from-docs.txt
  ```

  R15 fails if ANY of: (a) duplicate `### D-` headings in `docs/deferments.md` (`uniq -d` non-empty); (b) any concrete repo-wide ID is missing from `docs/deferments.md` (`comm -23` non-empty). Docs-only IDs (archive entries with no remaining repo anchor) are NOT drift. Both checks land in fn-18.3's done summary.

  **Hardened gate semantics** (per plan-review round 10): `comm -23` non-empty is allowed at task close ONLY for proven false positives — specifically (i) regex artefacts where a placeholder slipped through the tail-char `[A-Za-z0-9_]` exclusion (in which case fix the placeholder text in-place, not the gate), or (ii) IDs that appear only inside a `<...>`-fenced CONVENTION schema example (in which case fix the CONVENTION text to use a more obviously-placeholder form). The "explained line-by-line in done summary with rationale" allowance is NOT a general escape hatch — every legitimate concrete repo-wide ID MUST be added to `docs/deferments.md` before task close. If the missing-ID list contains a real ID that has no docs entry, the task FAILS R15 — add the entry. Per `feedback_no_corners.md`: no silent prose-pardons of missing entries.

- **R16 (bucket-1 value-flow acceptance, added per plan-review round 7)** — every NEW `@Ignore`d test written for a bucket-1 ID across fn-18.2 and fn-18.3 contains at least one **non-import** real-current-API value-flow reference inside the test body (per epic Decision #1, hardened per plan-review round 6 — "import-only references do NOT count"). The reference must be: an assertion using a real type, OR a type construction of a real domain class, OR a function call returning a real value. Imports-only fails R16. Verified at task close by inspecting each new bucket-1 test body; result recorded in the respective task's done summary. Bucket-2 tests are exempt (commented-out future API by design).

- **R14** — Test suite outcome recorded after each task. Two explicit outcomes (per plan-review round 6 — internal-consistency fix on R14 semantics):
  - **R14-Passed** — `./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt` exits 0. Eight goldens GREEN. Detekt baseline unchanged. This is the desired path.
  - **R14-NoNewRegression** — if baseline at task start was already red, the task records the exact failing test name + base SHA at task start vs at task end and proves "no new failures introduced." R14 is NOT marked Passed but task completion IS allowed. R14-NoNewRegression is the honest fallback per `feedback_no_corners.md`: we don't pretend the suite is green when it isn't, but we don't block fn-18's docs-only work on an unrelated pre-existing red.

  At least one outcome MUST be checked per task. Task evidence records which.

## Strategy drift flagged for review

_(none — plan aligns with the cross-cutting tooling / agent-infrastructure axis without introducing new runtime behaviour.)_

## Quick commands

```bash
# Inventory pilot-firewall.md OPEN entries
grep -nE '^\*\*D-(PASS|AUDIT|PF|WORLD)' ~/.claude/plans/pilot-firewall.md

# Grep inline code-comment deferments
grep -rn -E "D-(PASS|AUDIT|PF|WORLD)-" --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/

# Validate (after spec lands)
.flow/bin/flowctl validate --epic fn-18-deferment-register-reorganization-four --json

# Verify no test regression
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt
```

## Approach

### Three-task split

1. **Task .1 — Convention + scaffolding.** Lays the surface. Writes `docs/deferments.md` (empty body, schema, four-bucket header, `## Archive` stub), `docs/deferments-CONVENTION.md` (decision tree + worked examples), updates `AGENTS.md` (single discovery pointer), rewrites `reference_audit_registers.md` memory entry, files the three meta-deferments. NO migrations yet — establishing the gates and pointers.

2. **Task .2 — Migrate pilot-firewall.md OPEN items + orphan-test cleanup.** ~32 items triaged across the four buckets:
    - Bucket 1/2 items: new `@Ignore`d tests in per-module `DeferredContractsSpec.kt` (created if module didn't have one). Cross-reference back-populated in `docs/deferments.md`.
    - Bucket 3 items: if any inventory item warrants multi-task scope and doesn't already have an epic stub, create one via `flowctl epic create` set to `todo`. (Most pilot-firewall items are bucket 1/2/4; bucket 3 escape-valve only if scope is clear.)
    - Bucket 4 items: docs-only entry.
    - Orphan tests (D-PF.2 / D-AUDIT.5 / .6 / .10): delete-or-convert per Decision #13.
    - User-performed pilot-firewall.md "MIGRATED" header — fn-18.2's evidence section captures the user's confirmation.

3. **Task .3 — Migrate fn-14/15/17 epic-spec siblings + inline-only IDs.** 22 spec-sourced IDs + ~6 inline-only IDs:
    - fn-15 / fn-16 / fn-17 each get a `docs/deferments.md` bucket-3 entry pointing at the existing epic stub.
    - Remaining fn-14/15/17 siblings triaged into buckets 1/2/4 (no fn-14 sibling needs a new epic per Decision #10).
    - Inline-only IDs (`D-PASS-fn6-snap-derived`, etc.) triaged: most are bucket 4 narrative since they were filed inline because no obvious test or epic existed. Where a contract shape is clear, write the `@Ignore`d test.
    - fn-14/15/17 epic specs' `## Deferments register` sections updated to point at `docs/deferments.md` (R12).
    - Bucket distribution recorded in done summary (R8).

### Reuse points

| Surface | Reuse | New |
|---------|-------|-----|
| `@Ignore` test pattern | `pilot/.../DeferredContractsSpec.kt` (existing 6 tests, 4 of which orphaned) | Migration creates ~5-15 new tests across `:controller`, `:protocol`, `:sim` modules |
| Flow-next epic stub | fn-15, fn-16, fn-17 (existing in `todo`) | None new unless a pilot-firewall item warrants escalation |
| Memory entry shape | `reference_audit_registers.md` (existing) | Rewritten in place |
| `AGENTS.md` doctrine pattern | "Project Plan" section, "Self-assessment" list | Two short additions, no rewrite |
| docs/ directory | `docs/design/`, `docs/test-standards.md` (existing) | Two new files: `docs/deferments.md`, `docs/deferments-CONVENTION.md` |
| Inline `// D-PASS-*` comments | ~30 sites across `:pilot`, `:controller`, `:protocol`, `:sim`, `:core`, `:migration` | UNCHANGED — these stay as breadcrumbs; cross-references updated in docs map only |

### No-corners-cut anchors

- Every inventoried ID gets a bucket and a record. No "we'll get the rest in fn-19".
- Orphan `@Ignore` tests for closed deferments are not silently kept — each gets a decision (delete or convert).
- The four-bucket model is exhaustive over the migration surface; no fifth "miscellaneous" bucket.
- Status taxonomy is fixed at 4; no extension without spec amendment.
- `pilot-firewall.md` editing is acceptance-level even though repo-external — the user's confirmation is the evidence.

## Test notes

No new sim tests. fn-18 is a documentation / scaffolding / migration epic. The behaviour-preservation contract is enforced by:

- Each task records one of two R14 outcomes: **R14-Passed** (`./gradlew <module>:allTests detekt` exits 0; eight existing goldens GREEN; detekt baseline unchanged) OR **R14-NoNewRegression** (baseline was already red; task evidence records base SHA + exact failing tests at start and proves no new failures introduced). Per `feedback_no_corners.md`, we don't pretend the suite is green when it isn't, but we don't block fn-18's docs-only work on an unrelated pre-existing red.
- **Bucket-1 tests** (per R16 — see Acceptance) include at least one non-import real-current-API value-flow reference in the test body. If a referenced API is renamed or signature-changed, the test fails to compile loudly — that's the deferment record breaking, exactly the point of bucket 1.
- **Bucket-2 tests** intentionally use commented-out future-API references (the API doesn't exist yet). They are narrative-in-code only — compile-checked as `@Ignore`d shells, but not API-shape-checked. They become compile-checked when the implementer flips `@Ignore` off + uncomments + the new API exists.

No new sim tests because there's no new sim behaviour. fn-18 produces docs + tests-as-records, not feature code.

## Review considerations

### FP / type-safety axis
- The migration introduces no new types and no new state transitions. `@Ignore`d tests use existing types from `:pilot` / `:controller` / `:protocol` — the test's value lies in being a **compile-checked record of the deferment's eventual shape**, exactly what makes bucket 1/2 a totality-friendly pattern.
- The four-bucket model itself is a typed taxonomy (closed enum: `blocked | planned | narrative | closed`). The convention doc spells this out; the docs map enforces it via grep-checkable status values.
- No new sealed `when`s, no new fold paths.
- No new error types. The migration is mechanical: read inventory → assign bucket → write record. Per `feedback_no_corners.md`, the bar is exhaustive coverage, not safe-on-failure machinery.

### Test architecture axis
- The pattern-strengthening is the core test contribution: `@Ignore`d tests in per-module `DeferredContractsSpec.kt` become the project's primary mechanism for pinning eventual-acceptance shapes.
- Bucket-1 vs bucket-2 distinction is test-architecture relevant: bucket 1 uses today's API; bucket 2 has commented-out calls for tomorrow's API. The implementer flipping `@Ignore` off should be able to tell which kind of work is required.
- Orphan-test handling (D-PF.2 etc.) directly enforces the no-corners-cut rule: closed deferments don't get to keep dead test scaffolding.
- No `else` clauses in the bucket-decision: the four buckets are exhaustive; the convention doc must spell out what happens if a deferment doesn't fit (answer: it does — bucket 4 is the catchall, and it's not silent).

### Impact axis
- **Cross-references**: every inline code-comment `// D-PASS-*` must have a corresponding `docs/deferments.md` entry. R10 makes this acceptance-level. If a future ID drift introduces a code comment without a docs entry, the planned tooling automation (`D-PASS-deferments-map-tooling-automation`) catches it — but that's v2. v1 relies on the migration's grep-completeness.
- **`reference_audit_registers.md` rewrite**: every agent that reads this memory entry going forward gets the new pointer. Old contexts that already loaded the prior entry continue to work because pilot-firewall.md still exists as historical secondary.
- **`AGENTS.md` discovery pointer**: minimal change to the existing structure — one bullet, one line. Replaces nothing; supplements.
- **fn-14 / fn-15 / fn-17 spec updates** (R12): the `## Deferments register` sections in those specs are now redirect-only. Future epic specs adopt the convention that their `## Deferments register` is a working note that gets migrated into `docs/deferments.md` at done-time.
- **`pilot-firewall.md` MIGRATED header**: user-performed, evidence-confirmed. Repo-external; can't be CI-gated. The honest path: capture confirmation in the task's evidence-block.
- **Tooling automation deferred** (`D-PASS-deferments-map-tooling-automation`): v1 ships the map; v2 ships the lint. Until v2, drift detection is manual / grep-based. Acceptable risk given the rate of new deferments (~1 per pass).

### Operational axis
- **No runtime impact**: zero new code in production paths. Zero behaviour change. The eight golden tests stay GREEN by construction (no code paths touched).
- **Determinism / observability**: not relevant — this is doc + test surface work.
- **Migration safety**: the orphan-test handling (Decision #13) is the only step that touches existing test code. Each orphan-test decision (delete or convert) is reviewed individually at task time; the modifications are local and reversible via git history.
- **Solo+AI workflow alignment**: the four-bucket model honors how solo+AI development actually surfaces deferments. Buckets 1/2 catch what an implementer noticed but couldn't fix. Bucket 3 catches what's clearly a future feature. Bucket 4 catches what's doctrinally aware-of but unsolvable today.
- **Replaceability**: the convention can be amended later. The `docs/deferments-CONVENTION.md` doc is the source of truth; everything else (the map, the per-module test files, the `AGENTS.md` pointer) is downstream. Future amendment is one-place edit + propagate.

## Early proof point

**Task fn-18.1** validates the convention by writing the empty `docs/deferments.md` and `docs/deferments-CONVENTION.md`. After .1 lands, a reader following the convention doc and looking at the empty map should be able to predict: "the next entries I see will be the D-PF.* / D-AUDIT.* items currently in `~/.claude/plans/pilot-firewall.md`, with `Pinned at:` field values pointing at either `DeferredContractsSpec.kt`, flow-next epic IDs, or `narrative only`." If a reviewer reads the convention doc and can't predict the shape of what fn-18.2 will produce, the convention is unclear and .1 fails review.

## References

### Doctrinal / process
- `feedback_no_corners.md` — absolute rule, no silent workarounds. fn-18 is a no-corners audit that surfaces every existing deferment.
- `feedback_no_half_baked.md` — every commit leaves the codebase in a state where all tests pass and no known-incorrect behavior is silently accepted. fn-18 turns "known but invisible to CI" into "known and in-repo."
- `feedback_review_discipline.md` — every pass gets full 3-agent plan review + post-impl review. fn-18 follows the same discipline.
- `feedback_plans_review_aware.md` — every plan addresses FP / test / impact / operational axes. fn-18's `## Review considerations` section satisfies this.
- `feedback_pass_scope.md` — bigger chunks: fold typed value classes, doctrine retunes, data-on-type fields into the closing pass. fn-18 folds three artefact-types (the map, the convention doc, the memory update) and the per-task migration of ~60 IDs into a single epic — appropriate scope.
- `feedback_firewall_principle.md` — the pilot firewall is first-class; D-PF.* items track its evolution. fn-18 makes those items CI-visible.
- `feedback_world_only_test_triggers.md` — tests author world state; fn-18's new `@Ignore`d tests for bucket 1/2 will follow this rule.
- `reference_audit_registers.md` — the memory entry being rewritten. Old form points at pilot-firewall.md; new form points at docs/deferments.md.

### Codebase prior art
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt` — the existing pattern this epic generalises across all modules.
- `~/.claude/plans/pilot-firewall.md § Deferments register` — the canonical store being migrated FROM (repo-external).
- `.flow/specs/fn-14-g3a-react-pilot-reactive-go-around-on.md § Deferments register` — the in-spec deferments pattern being redirected.
- `.flow/specs/fn-15-g3a-react-tailwind-pilot-reactive-go.md`, `.flow/specs/fn-16-wind-state-migrate-to-aerodromeweather.md`, `.flow/specs/fn-17-cap-413-edition-24-numbering.md` — already-existing bucket-3 epic stubs.
- `docs/test-standards.md`, `docs/design/` — sibling docs directory; new files live next to them.

### Memory
- `reference_audit_registers.md` — rewritten as acceptance.
- `MEMORY.md` index entry — prose updated if needed.
- All `feedback_*.md` entries are inputs, not outputs.

### External
- Plan-review baseline: fn-14 epic spec § Deferments register format (in-spec dash-suffixed IDs with reason).

## Deferments register

Filed by this epic (all bucket-4 narrative, to be migrated into `docs/deferments.md § Active narrative bucket` during fn-18.1 per R13):

- **`D-PASS-deferments-map-tooling-automation`** — detekt rule or script enforcing `docs/deferments.md` ↔ inline-code-comment ID consistency + verifying every `Pinned at:` test or epic exists. v1 ships human-readable map only; tooling lift to v2.
- **`D-PASS-deferments-renumbering-discipline`** — current ID scheme mixes `D-AUDIT.N`, `D-PASS-N.x`, dash-suffixed (`D-AUDIT-lowg-ctr-radius`). v1 preserves all IDs as-is. Future cleanup: settle on dash-suffixed form (which survives renumbering); script-rewrite all references.
- **`D-PASS-deferments-cross-ref-from-impl-review`** — when a review agent surfaces a finding the principal defers, the convention for "this becomes a deferment" isn't automated. Today: manual sibling-file step. Future: a `/flow-next:defer` skill that prompts for bucket assignment + writes the record.

## Closures

- **Scattered deferment tracking closed at the project-meta level.** Single in-repo map (`docs/deferments.md`) becomes the discovery entry point.
- **Per-module `DeferredContractsSpec.kt` pattern lifted from `:pilot` to all modules with at least one bucket 1/2 deferment.** The pattern was always intended to be project-wide; fn-14 era inertia kept it pilot-local.
- **Orphan `@Ignore` tests removed for closed deferments.** D-PF.2 / D-AUDIT.5 / .6 / .10 placeholder tests had been silently kept past their parent's closure; fn-18.2 forces a delete-or-convert decision.
- **`reference_audit_registers.md` memory entry updated.** Future agents read the new pointer.

## Requirement coverage

| Req | Description | Task(s) |
|-----|-------------|---------|
| R1  | `docs/deferments.md` exists with schema + active body + Archive | fn-18.1 (scaffold), fn-18.2, fn-18.3 (populate) |
| R2  | `docs/deferments-CONVENTION.md` exists with decision tree + bucket distinction + transitions + status taxonomy + worked examples | fn-18.1 |
| R3  | `AGENTS.md` updated with discovery pointer + self-assessment note | fn-18.1 |
| R4  | `reference_audit_registers.md` memory entry rewritten | fn-18.1 |
| R5  | Per-module `DeferredContractsSpec.kt` files exist where bucket 1/2 items land | fn-18.2, fn-18.3 |
| R6  | No orphan `@Ignore` tests remain — every existing one maps to active deferment OR is deleted/converted | fn-18.2 |
| R7  | Comprehensive migration: every inventoried ID has a `docs/deferments.md` entry | fn-18.2, fn-18.3 |
| R8  | Bucket distribution recorded in fn-18.3 done summary | fn-18.3 |
| R9  | Bucket-3 epic stubs created (where needed) before docs entries written; existing fn-15/16/17 cross-referenced | fn-18.2 (if any new), fn-18.3 (existing) |
| R10 | Inline code-comment IDs cross-referenced — every grep hit has a docs entry | fn-18.2 (pilot-firewall.md-source items), fn-18.3 (inline-only items) |
| R11 | fn-18.2 provides exact MIGRATED header text and logs external-follow-up status (`pending` / `confirmed-by-user`) in evidence; actual user-edit to pilot-firewall.md is outside flowctl acceptance | fn-18.2 |
| R12 | fn-14 / fn-15 / fn-17 epic specs' `## Deferments register` sections updated to redirect at `docs/deferments.md` | fn-18.3 |
| R13 | Three meta-deferments filed in `docs/deferments.md` as bucket 4 | fn-18.1 |
| R14 | Recorded as **R14-Passed** (gradle + detekt exit 0; eight goldens GREEN; baseline unchanged) OR **R14-NoNewRegression** (baseline was already red; task evidence proves no new failures introduced). Each task records one. | fn-18.1, fn-18.2, fn-18.3 (each task verifies) |
| R15 | Whole-repo grep diff: every concrete `D-*` ID found in the repo appears exactly once in `docs/deferments.md` (final exhaustiveness gate) | fn-18.3 |
| R16 | Every new bucket-1 `@Ignore`d test contains a non-import current-API value-flow reference | fn-18.2, fn-18.3 |

## Done summary

_(filled per task during implementation)_

## Evidence

_(filled per task during implementation)_
