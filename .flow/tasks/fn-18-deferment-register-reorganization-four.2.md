---
satisfies: [R1, R5, R6, R7, R8, R9, R10, R11, R14, R16]
---

# fn-18.2 — Migrate pilot-firewall.md OPEN items + orphan-test cleanup + MIGRATED header (post-task follow-up)

## Description

Comprehensive migration of every OPEN deferment in `~/.claude/plans/pilot-firewall.md § Deferments register` into `docs/deferments.md` per the four-bucket model. Approximately 32 IDs (verify at task time via locked-inventory step). Includes: (a) per-bucket triage; (b) writing new `@Ignore`d tests in per-module `DeferredContractsSpec.kt` files for bucket 1/2 items; (c) creating new flow-next epic stubs for any bucket 3 items not already an epic; (d) `docs/deferments.md` entry per ID; (e) orphan-test cleanup in `pilot/src/commonTest/.../DeferredContractsSpec.kt` (D-PF.2, D-AUDIT.5, D-AUDIT.6, D-AUDIT.10) PLUS deletion of closed-deferment block comments (D-PF.5, D-PF.6, D-AUDIT.3) with closure capture in `docs/deferments.md § Archive`; (f) MIGRATED header text provided to user as non-blocking post-task follow-up (per epic Decision #12; the header itself is repo-external so fn-18.2 does NOT block on its presence).

The success criterion is: every OPEN ID from the locked inventory appears in `docs/deferments.md`; no orphan `@Ignore` test remains for a closed deferment; the exact MIGRATED header text is recorded in fn-18.2's evidence block AND its external-follow-up status (`pending` or `confirmed`) is logged. fn-18.2 NEVER declares "migration is complete" — it only provides the text and logs the follow-up state. The user-edit to `~/.claude/plans/pilot-firewall.md` is a separately-tracked external action; the legacy register is not declared migrated until the user applies the edit. Both states (pending / confirmed) satisfy R11 acceptance because R11 gates on "text provided + follow-up logged," NOT on the external edit having landed (per epic Decision #12 + plan-review round 7 normalization).

## Problem

Today the pilot-firewall.md deferment register is repo-external and CI-invisible. Agents that don't have read access to `~/.claude/plans/` (or that didn't read the memory entry pointing there) miss the entire register. The `DeferredContractsSpec.kt` placeholder pattern is under-used — only `:pilot` has one, and four of its six placeholders point at deferments **already closed** (orphan rot). This task lifts the register into `docs/deferments.md`, extends the placeholder pattern to other modules where bucket-1/2 deferments anchor, and resolves the four orphan tests.

## Files (read or modify)

- **READ**
  - `/home/andrew/dev/projects/twr2/docs/deferments.md` (created by fn-18.1) — schema + section structure to populate.
  - `/home/andrew/dev/projects/twr2/docs/deferments-CONVENTION.md` (created by fn-18.1) — the decision tree this task applies.
  - `~/.claude/plans/pilot-firewall.md` § Deferments register (lines ~552-907) — full inventory; lock IDs at task start via `grep -nE '^\*\*D-' ~/.claude/plans/pilot-firewall.md`.
  - `/home/andrew/dev/projects/twr2/pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt` — existing tests; D-PF.1 / D-PF.3 stay, D-PF.2 / D-AUDIT.5 / .6 / .10 are orphans.
  - All `.kt` files in `pilot/` `controller/` `protocol/` `sim/` `core/` `migration/` — grep for any inline `// D-PASS-*` / `// D-AUDIT-*` / `// D-PF.*` comment referencing IDs from this task's scope. Cross-reference confirmation per R10.
  - Per-pass plan files in `~/.claude/plans/pass-*.md` — read closure summaries for D-PF.2 / D-AUDIT.5 / .6 / .10 to inform orphan-test delete-vs-convert decision.

- **CREATE (where applicable per per-bucket triage)**
  - `/home/andrew/dev/projects/twr2/controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt` — if any pilot-firewall.md item maps to `:controller` bucket 1/2 (likely candidates: items related to `BeliefState` slices, `ControllerView` projections, runway-related guards).
  - `/home/andrew/dev/projects/twr2/protocol/src/commonTest/kotlin/xyz/easiersaid/twr/protocol/DeferredContractsSpec.kt` — if any item maps to protocol-level data-shape contracts.
  - `/home/andrew/dev/projects/twr2/sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/DeferredContractsSpec.kt` — if any sim-side bucket 1/2 lands.
  - `/home/andrew/dev/projects/twr2/core/src/commonTest/kotlin/xyz/easiersaid/twr/core/DeferredContractsSpec.kt` — if any core-side bucket 1/2 lands.
  - **Only create per-module files if a bucket 1/2 deferment lands in that module.** Empty `DeferredContractsSpec.kt` files are NOT created (per epic R5 — only modules with actual contract-shape deferments get the file).

- **MODIFY**
  - `/home/andrew/dev/projects/twr2/docs/deferments.md` — populate every section per the inventory. ~32 new entries.
  - `/home/andrew/dev/projects/twr2/pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt` — delete or convert four orphan tests (D-PF.2, D-AUDIT.5, D-AUDIT.6, D-AUDIT.10) per Decision #13. **Delete** the existing D-PF.5 / D-PF.6 / D-AUDIT.3 closed-deferment block comments (those are orphan rot per Decision #13 — their content lives authoritatively in `docs/deferments.md § Archive`). Keep D-PF.1 and D-PF.3 active `@Ignore`d tests if they're being kept as bucket-1/2 entries (verify at task time — most likely D-PF.1 stays as bucket-2, D-PF.3 stays as bucket-1 or bucket-2 depending on FiledPlan API existence).
  - `~/.claude/plans/pilot-firewall.md` — fn-18.2 PROVIDES the exact MIGRATED header text and records it in the evidence block as a post-task follow-up for the user (per Decision #12). fn-18.2 does NOT edit this file directly and does NOT block on whether the user has applied the edit by task-close. fn-18.2 NEVER declares migration confirmed; evidence-block status is `pending` by default, `confirmed-by-user` only if the user independently reports they have applied the edit. Both states satisfy R11 acceptance (per plan-review round 7 normalization).

## Approach (numbered Steps)

### Step 1 — Lock the inventory (acceptance artifact)

Run the inventory grep:
```bash
grep -nE '^\*\*D-(PASS|AUDIT|PF|WORLD)' ~/.claude/plans/pilot-firewall.md
```

For each entry, classify by its status line in pilot-firewall.md:
- **OPEN list**: every entry whose status is NOT `CLOSED`/`CLOSED-PARTIAL`. These get **active** `docs/deferments.md` entries.
- **CLOSED-from-pilot-firewall list**: every entry whose status IS `CLOSED`/`CLOSED-PARTIAL` AND that appears in the `DeferredContractsSpec.kt` orphan set (D-PF.2, D-PF.5, D-PF.6, D-AUDIT.3, D-AUDIT.5, D-AUDIT.6, D-AUDIT.10) OR otherwise needs an archive entry (per Decision #3 + Decision #13). These get **archive** entries.

**Both lists are locked inventory artifacts for this task** (per plan-review round 6). At task close, write them into the flowctl evidence JSON for fn-18.2 in a stable, recoverable format that fn-18.3 can consume:

```json
{
  "locked_inventory": {
    "open_ids": ["D-PF.1", "D-PF.3", "D-PF.8", "D-AUDIT.2.C-FOLLOWUP", ...],
    "closed_from_pilot_firewall_ids": ["D-PF.2", "D-PF.5", "D-PF.6", "D-AUDIT.3", "D-AUDIT.5", "D-AUDIT.6", "D-AUDIT.10"],
    "inline_ids_observed_deferred_to_fn_18_3": ["D-PASS-fn6-snap-derived", ...],
    "bucket_distribution": {"bucket_1": N, "bucket_2": N, "bucket_3": N, "bucket_4": N}
  }
}
```

This makes both lists structurally accessible for fn-18.3's reconciliation pass. ALSO paste both lists into the done summary's human-readable text. Acceptance R7's exhaustiveness check is "every ID in the union of OPEN list + CLOSED-from-pilot-firewall list appears exactly once in `docs/deferments.md` (active body or `## Archive` as appropriate)."

This makes the count concrete and reviewable. Verify approximate OPEN count matches epic Decision #10 (~32). Differences vs the epic spec's enumeration: documented as a brief diff in the done summary.

**Also record inline IDs observed but deferred to fn-18.3** (per plan-review round 6): the grep across `.kt` source files may surface inline-only IDs that fn-18.2 doesn't own (fn-18.3 owns those). Record them as a third locked list in the evidence JSON so fn-18.3 can reconcile them against its own grep — preventing duplicates or misses.

**Explicit three-set scope boundary** (per plan-review round 9 — the fn-18.2 / fn-18.3 boundary must be set-theoretically precise to avoid double-migration or gaps; hardened per plan-review round 10 — `/tmp` is NOT durable cross-task state, so the authoritative handoff goes through flowctl evidence JSON, not file paths):

```bash
# Set A: pilot-firewall.md IDs (fn-18.2's authoritative source)
grep -nE '^\*\*D-(PASS|AUDIT|PF|WORLD)' ~/.claude/plans/pilot-firewall.md \
  | grep -Eo "D-(PASS|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|PF)\.[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]" \
  | sort -u

# Set B: inline code-comment IDs (any module)
grep -rhEo "D-(PASS|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|PF)\.[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]" \
  --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/ \
  | sort -u

# Compute the three derived sets via shell pipelines or by hand from A and B.
# Set intersection (A ∩ B): inline comments referring to pilot-firewall.md-source IDs. fn-18.2 R10 scope.
# Set B \ A: inline-only IDs (not in pilot-firewall.md). fn-18.3 scope; handed off.
```

fn-18.2's scope: every ID in **Set A** plus every ID in **Set intersection** (inline comments referencing pilot-firewall.md-source IDs need a docs entry as part of R10). fn-18.3's scope is **Set B \ A** (inline-only) plus the epic-spec siblings plus `.plan` (per plan-review round 10).

**Acceptance for the set-boundary discipline** (per plan-review round 10 — durable cross-task handoff via flowctl evidence JSON, NOT `/tmp` files):

The authoritative handoff is fn-18.2's flowctl evidence JSON. It MUST contain the actual ID lists embedded in JSON arrays (not paths to `/tmp/` files; `/tmp` does not survive between flow sessions):

```json
{
  "locked_inventory": {
    "open_ids": ["D-PF.1", "D-PF.3", "..."],
    "closed_from_pilot_firewall_ids": ["D-PF.2", "D-PF.5", "..."],
    "set_A_pilot_firewall": ["D-PF.1", "D-PF.3", "..."],
    "set_B_inline": ["D-PASS-fn6-snap-derived", "D-PF.1", "..."],
    "set_intersection_A_and_B": ["D-PF.1", "..."],
    "set_inline_only_for_fn_18_3": ["D-PASS-fn6-snap-derived", "D-PASS-g3a-obstruction-aerodrome-payload", "..."],
    "bucket_distribution": {"bucket_1": N, "bucket_2": N, "bucket_3": N, "bucket_4": N}
  }
}
```

- The four set files MAY be staged in `/tmp/` during fn-18.2 execution as scratch, but the durable record is the JSON arrays inside flowctl evidence.
- fn-18.3 reads fn-18.2's evidence JSON via `flowctl show fn-18-deferment-register-reorganization-four.2 --json | jq .evidence` and uses `set_inline_only_for_fn_18_3` as the authoritative inline-only inventory. fn-18.3 then re-runs its own grep and reconciles — any drift between the JSON list and fn-18.3's re-grep is investigated, recorded, and explained in fn-18.3's done summary, not silently reconciled.
- This makes the handoff session-durable: even if fn-18.3 runs days later in a fresh shell with cold `/tmp`, the cross-task contract holds.

This makes R10's "every inline comment referring to a pilot-firewall.md-source ID has a docs entry" mechanically verifiable: it's exactly the **Set intersection** with `docs/deferments.md` containment checked.

### Step 2 — Per-ID triage

For each OPEN ID, walk the bucket-decision tree from `docs/deferments-CONVENTION.md`:

1. **Does this deferment have a clear test shape I could write today?**
   - Yes, API exists → bucket 1
   - Yes, but API is missing (e.g. "needs `PilotInput.nearbyTraffic`") → bucket 2
   - No, multi-task scope → bucket 3
   - No, doctrinal / cross-cutting → bucket 4

2. Record the triage decision (bucket + rationale) in a working table.

3. For bucket 1/2: identify the target module (`:pilot`/`:controller`/`:protocol`/`:sim`/`:core`). Identify the eventual API shape and the test name.

4. For bucket 3: check if a flow-next epic stub already exists (e.g. fn-15 / fn-16 / fn-17 for fn-14-source items — but those are NOT pilot-firewall.md-source items, so likely no new epics in .2). Create stub via `flowctl epic create` + `set-plan` (minimal spec) + leave at `todo` status only if multi-task scope is genuinely clear.

5. For bucket 4: write the `docs/deferments.md` entry directly.

### Step 3 — Write bucket 1/2 `@Ignore`d tests

For every bucket 1 ID: write the `@Test @Ignore` placeholder in the appropriate module's `DeferredContractsSpec.kt` (create the file if absent). Test name format: `D-XXX <one-line shape description>` (mirror existing pilot pattern). KDoc block: 4-field shape (matches `DeferredContractsSpec.kt`'s existing convention) — "When implemented: ...", "This test asserts: ...".

**Bucket-1 value-flow requirement (R16, per plan-review round 7)**: every bucket-1 test body MUST contain at least one **non-import** real-current-API value-flow reference. This means one of: (a) an `assertEquals` / `assertTrue` / `assertIs<>` / etc. against a real domain value, (b) a type construction like `val x: RealType = RealConstructor(...)`, or (c) a function call returning a real value used in the body. Import-only references DO NOT count — IDE auto-cleanup can remove them; detekt unused-import rules can flag them. Example:

```kotlin
@Ignore
@Test
fun `D-AUDIT7-III BeliefState no longer stores activeRunway slice`() {
    // Bucket-1: API exists today; this body uses real types so a rename breaks compile.
    val state: BeliefState = BeliefState.empty()
    assertEquals(null, state.observations.activeRunway)  // value-flow reference (R16 ok)
}
```

If a bucket-1 candidate cannot satisfy R16 — i.e. there's no API surface to reference today — it is bucket 2 by definition. The MUST-vs-SHOULD distinction is what makes bucket 1 "code-as-record"; without compile-check it collapses into bucket 2.

For every bucket 2 ID: same structure, but inside the test body include a **commented-out** reference to the missing API. Example for D-AUDIT.9.II-FOLLOWUP:

```kotlin
@Ignore
@Test
fun `D-AUDIT9-II VFR see-and-avoid recognises nearby traffic and yields right of way`() {
    // TODO when D-AUDIT.9.II-FOLLOWUP lands; needs PilotInput.nearbyTraffic.
    //   val event = derivePilotEvent(aircraft, mission, input.nearbyTraffic)
    //   assertIs<PilotEvent.NearbyTrafficConflict>(event)
}
```

The commented-out call documents the eventual API shape. When the deferment is closed, the implementer uncomments and adapts.

### Step 4 — Orphan-test cleanup

For each of D-PF.2, D-AUDIT.5, D-AUDIT.6, D-AUDIT.10 in `pilot/src/commonTest/.../DeferredContractsSpec.kt`:

1. Read the closing pass's plan file at `~/.claude/plans/pass-<N>-*.md` to understand what landed.
2. Decision per Decision #13: **delete outright** OR **convert** to a non-`@Ignore`d regression test.
3. **Delete** if the closing pass left full structural enforcement (e.g. firewall test + spec coverage). **NO per-file closure comment is left behind** (per plan-review round 2 — Decision #13 clarified). The authoritative closure record lives in `docs/deferments.md § Archive`; in-file comments are themselves orphan rot.
4. **Convert** if a runtime shape was left unverified. Drop `@Ignore`, write the real test body, add to the active suite. The newly-active test is named per the regression's purpose; it is no longer a "D-XXX placeholder."
5. Existing **closed-deferment block-comments** in `DeferredContractsSpec.kt` (e.g. the D-PF.5 / D-PF.6 / D-AUDIT.3 narrative blocks at lines ~92-129 of the current file) are also deleted as part of this step. Their content is captured authoritatively in `docs/deferments.md § Archive` one-liners.
6. Each decision is documented in the done summary; per `feedback_no_corners.md` no silent keep-as-`@Ignore`.

**Fallback** (per plan-review round 1): if a pass plan file is missing or its naming is unstable, resolve the orphan-test decision from in-repo tests + git log + closure record in pilot-firewall.md (which carries per-pass summaries). Record the missing-historical-source path in the done summary; do NOT block on archaeological completeness. The closure decision itself remains acceptance-bound (delete or convert with rationale).

### Step 5 — Populate `docs/deferments.md`

For every triaged ID, write the schema entry under the appropriate subsection (`D-PF.*`, `D-AUDIT.*`, `D-PASS-*`, `D-WORLD.*`). Field values per the locked schema (Decision #7).

For each:
- `**Status:**` — `blocked` (most pilot-firewall.md items are blocked on a prerequisite), `narrative` (cross-cutting items like `D-AUDIT.11`, `D-PASS-cross-aircraft-step-on`, `D-PASS-pilot-mid-tng-fullstop-recovery`).
- `**Pinned at:**` — for bucket 1/2 items: full file::test path. For bucket 4 items: `narrative only`.
- `**Blocked on:**` — present only when Status=blocked. Pull from pilot-firewall.md's "Trigger" field, condense.
- `**Why:**` — 1-3 sentences. Distill from pilot-firewall.md's "What today" + "Why wrong" without softening.
- `**Closes by:**` — pull from pilot-firewall.md's "Trigger" if it names a closing scenario.

### Step 6 — Populate `docs/deferments.md § Archive` for closed/deleted items

Each of D-PF.2 / D-AUDIT.5 / D-AUDIT.6 / D-AUDIT.10 (orphan tests) AND D-PF.5 / D-PF.6 / D-AUDIT.3 (existing closed-deferment block comments being deleted) gets an `### D-...` archive entry per epic Decision #3 schema (three fields: Status + Closed-by + Enforcement). The `Enforcement:` field captures the enforcement-surface references from the deleted comment blocks (per plan-review round 6 — archive must be at least as informative as the deleted state).

**Verify-before-write rule (per plan-review round 7)**: enforcement-spec names cited in the archive entries MUST be verified against the actual codebase BEFORE the archive entry is written. The example below names `RunUpDurationSpec`, `ResponsibilityInvariantSpec`, etc., illustratively — those names may be stale. For each closed deferment:
1. Read the existing closed-deferment block comment in `DeferredContractsSpec.kt` (it names the canonical enforcement surface).
2. `grep -rn` the cited type names + test class names to confirm they exist with that exact name.
3. If a cited name has changed (rename via subsequent passes), use the current name. Stale archive history is itself rot.
4. Record the verification step in the done summary as a per-entry pass/fail roll-up.

Shape example (NON-AUTHORITATIVE — names below must be verified per the verify-before-write rule):

```markdown
### D-PF.2 — RunwayAssignmentSource sealed discriminator
**Status:** closed
**Closed by:** Pass 5 — see ~/.claude/plans/pass-5-entities-and-aircraft-intent.md. Orphan test deleted in fn-18.2 per Decision #13.
**Enforcement:** `RunwayAssignmentSource` sealed type in `protocol/.../RunwayAssignment.kt`; `applyPrecedence` 6×6 cross-product invariant tests in `ProcessInstructionRunwayDerivationSpec.kt`.

### D-PF.5 — Strip/dynamism replacement
**Status:** closed
**Closed by:** /home/andrew/.claude/plans/fragility-and-strip-dynamism.md. Block comment removed from DeferredContractsSpec.kt in fn-18.2.
**Enforcement:** `inferIntentFromGoal(goal: HighLevelGoal?)` signature (no mission arg) + `FirewallStripStaticTest` source-text scan + G0 integration assertion + `BeliefFoldSpec` rows for AircraftArrivalCommitted.

### D-PF.6 — TaxiTo split
**Status:** closed
**Closed by:** Pass 6. Block comment removed in fn-18.2.
**Enforcement:** `TaxiToHoldingPoint` / `TaxiToStand` sealed split in `protocol/Instruction.kt`; `TaxiToSplitFirewallTest` (E14) reflective leaf-cardinality assertion; `ProcessInstructionRunwayDerivationSpec` multi-runway twin-row; G0 assertion (g) sealed-type match on `TaxiToStand`.

### D-AUDIT.3 — Per-type runUpDurationMs
**Status:** closed
**Closed by:** Pass 13. Block comment removed in fn-18.2.
**Enforcement:** `AircraftType.runUpDurationMs` per leaf in `protocol/AircraftType.kt`; `AircraftTypeSpec` test (pinned to C172 = 60 s, B738 = 600 s per the existing in-file comment block at lines 120-128 of `DeferredContractsSpec.kt`) — verified at task time by reading the closed-deferment comment block before deleting. **Note (per plan-review round 7)**: the spec class name above is non-authoritative; fn-18.2 MUST verify the exact enforcement-spec name(s) by reading the in-file closed-deferment block + grepping for the type's actual test usage before writing the archive entry. Stale archive history is itself rot.

### D-AUDIT.5 — Responsibility transfer overlap
**Status:** closed
**Closed by:** Pass 7. Orphan test deleted in fn-18.2 per Decision #13.
**Enforcement:** `ResponsibilityState { Owned | HandingOff | Watching }` sealed type in `:protocol`; `applyContactFrequency` + `applyInitialContact` + `applyBoundaryReleaseReadback` machinery; `ResponsibilityInvariantSpec` cross-controller invariant + `ResponsibilityStateMachineSpec` 5-row transition pin.

### D-AUDIT.6 — Flight-plan filing event
**Status:** closed
**Closed by:** Pass 11. Orphan test deleted in fn-18.2.
**Enforcement:** `FiledPlan` sealed in `:protocol`; `SimEvent.FlightPlanFiled` event; `Step.handleFlightPlanFiled`; `FixtureLoadSpec` and `FlightPlanFilingSpec` row coverage + AFTN routing closure in Pass 14.

### D-AUDIT.10 — Fixture stops mutating responsibilities
**Status:** closed
**Closed by:** Pass 11. Orphan test deleted in fn-18.2.
**Enforcement:** `Fixture.flightPlans` replaces `Fixture.groundResponsibilities`; `FirewallFixtureNoDirectResponsibilitiesTest` (E20) negative-lookahead allowlist; aircraft enter via `SimEvent.FlightPlanFiled`.
```

7 archive entries total. All use the locked archive schema (Status + Closed-by + Enforcement, per Decision #3 hardened per plan-review round 6).

### Step 7 — Cross-reference inline code comments (R10)

Run a broader grep that catches BOTH dash-suffixed and dotted IDs (per plan-review round 2):

```bash
grep -rn -E "D-(PASS|WORLD)-[A-Za-z0-9_.-]+|D-(AUDIT|PF)\.?[A-Za-z0-9_.-]+" --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/
```

This catches `D-PASS-fn6-snap-derived`, `D-PF.1`, `D-AUDIT.5`, `D-AUDIT.9.II-FOLLOWUP`, and the dotted-with-FOLLOWUP variants. If running two separate greps is cleaner, do that:

```bash
grep -rn -E "D-(PASS|WORLD)-" --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/
grep -rn -E "D-(AUDIT|PF)\." --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/
```

For every match referencing a pilot-firewall.md-source ID, verify there's a corresponding `docs/deferments.md` entry. If not, file the entry. Inline-only IDs (those that only exist as code comments) are handled by fn-18.3 — fn-18.2's scope is pilot-firewall.md-source items.

### Step 8 — Provide MIGRATED header text + record external-follow-up status (R11)

Per epic Decision #12 (clarified per plan-review round 2 + normalized per plan-review round 7): fn-18.2 provides the exact header text and records the external-follow-up status in the evidence block. fn-18.2 does NOT block on the user's edit AND does NOT declare "migration confirmed" at task close — declaring migration complete is the user's call once they apply the external edit.

Header template:
```
**MIGRATED to docs/deferments.md per fn-18 on 2026-MM-DD (commit <pending>).**
The entries below are preserved for historical context (pass-by-pass narrative).
For the active deferment register, see `docs/deferments.md` in the repo.
```

Evidence-block text format — the only thing fn-18.2 records is `pending` vs `confirmed-by-user`. fn-18.2 itself never writes "migration confirmed":
- If user has independently applied the edit by task-close: `R11 — exact header text provided; external follow-up status: confirmed-by-user on YYYY-MM-DD (user-reported).`
- If not yet applied (the default case): `R11 — exact header text provided; external follow-up status: pending; user-edit to ~/.claude/plans/pilot-firewall.md is queued as non-blocking post-task follow-up.`

Both states satisfy R11 acceptance because R11 gates on "text provided + status logged," NOT on the external edit having landed. Per epic Decision #12 the legacy register is not declared migrated until the user actually edits the file — that's outside fn-18.2's reach.

### Step 9 — Verify

**Pre-task baseline capture** (per plan-review round 2): at task start, run the verification command and capture base SHA + any pre-existing failures in a working note. This makes the R14 pre-existing-failure honesty clause executable.

```bash
git rev-parse HEAD > /tmp/fn-18-2-base-sha.txt
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt 2>&1 | tee /tmp/fn-18-2-base-test.log
```

**Post-task verification**: re-run the same command. R14 acceptance is "no failures present in post-task log that were not present in base log." If base log was already red, the diff (not the absolute pass/fail) is what matters.

Eight goldens GREEN at post-task. Detekt baseline unchanged. Any newly-created `@Ignore`d test compiles (proving its API references exist today) OR is bucket 2 with commented-out future-API references (compilable shell only). No `@Ignore` test goes from pass to fail.

## Investigation targets

- Verify exact OPEN-item count in `~/.claude/plans/pilot-firewall.md` (the brief said ~22; epic spec said ~32). Real count comes from grep at task time.
- Confirm `:controller` / `:protocol` / `:sim` / `:core` test directory structure for new `DeferredContractsSpec.kt` files.
- For each orphan test (D-PF.2 / D-AUDIT.5 / .6 / .10): read the closing pass-N plan, decide delete-or-convert.
- For each bucket 2 candidate: identify the missing-API line in the eventual contract. The commented-out reference must point at the API shape that lands when the deferment closes.

## Key context

- **Triage is per-bucket — every ID goes in exactly one bucket.** No "this is partly bucket 1, partly bucket 4" — pick one.
- **Orphan-test cleanup is acceptance-level.** No silent keep-as-`@Ignore`.
- **Bucket 3 is rare in .2.** Most pilot-firewall.md items are bucket 1/2/4 because the multi-task ones already moved to fn-15/16/17 (fn-14 era).
- **`docs/deferments.md` entries match the schema EXACTLY.** Field order locked per Decision #7.
- **MIGRATED header is provided as post-task follow-up.** fn-18.2 cannot edit `~/.claude/plans/` directly; evidence records external-follow-up status (`pending` or `confirmed-by-user`). fn-18.2 NEVER declares migration confirmed; the legacy register is migrated only when the user applies the edit. Both states satisfy R11; fn-18.2 does not block on user action.
- **`Why:` field is capped at 3 sentences.** Compress pilot-firewall.md's "What today" + "Why wrong" without softening; if 3 sentences isn't enough, the deferment's framing is wrong.
- **Per-module `DeferredContractsSpec.kt` files are created only if bucket 1/2 items land in that module.** No empty placeholder files.

## Acceptance

- [ ] **R1** (populated portion for pilot-firewall.md source) — every OPEN ID from the inventory has an entry in `docs/deferments.md` per the schema. Active body + Archive populated per orphan-test cleanup.
- [ ] **R5** — Per-module `DeferredContractsSpec.kt` files exist in every module that received at least one bucket 1/2 deferment from this task's scope. Modules with zero bucket 1/2 items do NOT get a file.
- [ ] **R6** — No orphan `@Ignore` test remains in `pilot/src/commonTest/.../DeferredContractsSpec.kt`. D-PF.2 / D-AUDIT.5 / .6 / .10 either deleted outright (closure record in `docs/deferments.md § Archive`, NO per-file closure comment) or converted (no `@Ignore`, full test body, renamed to reflect regression purpose). Existing D-PF.5 / D-PF.6 / D-AUDIT.3 closed-deferment block comments in the file deleted per Decision #13. All seven closed IDs (D-PF.2, .5, .6, D-AUDIT.3, .5, .6, .10) have `### D-...` archive entries in `docs/deferments.md` per the locked archive schema.
- [ ] **R7** (partial — pilot-firewall.md portion) — every OPEN ID in `~/.claude/plans/pilot-firewall.md § Deferments register` maps to exactly one `docs/deferments.md` entry. Count verified at task time and recorded in the done summary.
- [ ] **R8** (partial — bucket distribution for pilot-firewall.md scope) — per-bucket counts recorded in the done summary.
- [ ] **R9** — Bucket 3 epic stubs created (if any) before docs entries written. Existing fn-15/16/17 not in scope (fn-18.3 owns those).
- [ ] **R10** (partial — pilot-firewall.md-source IDs) — every inline `// D-PASS-*` / `// D-AUDIT-*` / `// D-PF.*` code comment referring to a pilot-firewall.md-source ID has a corresponding `docs/deferments.md` entry. Verified via grep, listed in done summary.
- [ ] **R11** — Exact MIGRATED header text provided AND external-follow-up status logged in evidence block (one of: `pending` / `confirmed-by-user`). Acceptance gates on "text provided + status logged"; fn-18.2 never declares migration complete and never writes `confirmed` unless the user has reported their edit. The actual user-edit to pilot-firewall.md is a separately-tracked external action (not flowctl-acceptance-bound). The legacy register is NOT declared migrated until the user applies the edit.
- [ ] **R16** — Every NEW bucket-1 `@Ignore`d test written in this task (across `:pilot`/`:controller`/`:protocol`/`:sim`/`:core` modules) contains at least one **non-import** real-current-API value-flow reference inside its test body — an assertion using a real type, OR a type construction of a real domain class, OR a function call returning a real value. Import-only references fail R16 (per epic Decision #1 hardened per plan-review round 6). Bucket-2 tests are exempt. Verified by reading each new test body at task close; result recorded in done summary as a per-test pass/fail roll-up.
- [ ] **R14** — Recorded as one of two outcomes per epic Decision #R14: **R14-Passed** (gradle exits 0; eight goldens GREEN; detekt unchanged; new `@Ignore`d tests compile) OR **R14-NoNewRegression** (baseline was already red; task evidence records base SHA + exact failing tests at start and proves no new failures introduced by fn-18.2's changes).

## Notes for fn-18.3

fn-18.3 picks up the remaining migration sources (fn-14/15/17 epic-spec siblings + inline-only IDs that aren't pilot-firewall.md-sourced). The schema, conventions, and per-module DeferredContractsSpec.kt locations established by fn-18.1/.2 apply unchanged. fn-18.3 also updates fn-14/15/17 epic specs to redirect their `## Deferments register` sections at `docs/deferments.md` (R12).

## Done summary

_(filled at done-time, including: total IDs migrated, per-bucket counts, orphan-test resolutions, MIGRATED header status — confirmed or pending follow-up)_

## Evidence

_(filled at done-time; include MIGRATED header status — user confirmation OR pending-follow-up note)_
