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
  - `./docs/deferments.md` (created by fn-18.1) — schema + section structure to populate.
  - `./docs/deferments-CONVENTION.md` (created by fn-18.1) — the decision tree this task applies.
  - `~/.claude/plans/pilot-firewall.md` § Deferments register (lines ~552-907) — full inventory; lock IDs at task start via `grep -nE '^\*\*D-' ~/.claude/plans/pilot-firewall.md`.
  - `./pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt` — existing tests; D-PF.1 / D-PF.3 stay, D-PF.2 / D-AUDIT.5 / .6 / .10 are orphans.
  - All `.kt` files in `pilot/` `controller/` `protocol/` `sim/` `core/` `migration/` — grep for any inline `// D-PASS-*` / `// D-AUDIT-*` / `// D-PF.*` comment referencing IDs from this task's scope. Cross-reference confirmation per R10.
  - Per-pass plan files in `~/.claude/plans/pass-*.md` — read closure summaries for D-PF.2 / D-AUDIT.5 / .6 / .10 to inform orphan-test delete-vs-convert decision.

- **CREATE (where applicable per per-bucket triage)**
  - `./controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/DeferredContractsSpec.kt` — if any pilot-firewall.md item maps to `:controller` bucket 1/2 (likely candidates: items related to `BeliefState` slices, `ControllerView` projections, runway-related guards).
  - `./protocol/src/commonTest/kotlin/xyz/easiersaid/twr/protocol/DeferredContractsSpec.kt` — if any item maps to protocol-level data-shape contracts.
  - `./sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/DeferredContractsSpec.kt` — if any sim-side bucket 1/2 lands.
  - `./core/src/commonTest/kotlin/xyz/easiersaid/twr/core/DeferredContractsSpec.kt` — if any core-side bucket 1/2 lands.
  - **Only create per-module files if a bucket 1/2 deferment lands in that module.** Empty `DeferredContractsSpec.kt` files are NOT created (per epic R5 — only modules with actual contract-shape deferments get the file).

- **MODIFY**
  - `./docs/deferments.md` — populate every section per the inventory. ~32 new entries.
  - `./pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/DeferredContractsSpec.kt` — delete or convert four orphan tests (D-PF.2, D-AUDIT.5, D-AUDIT.6, D-AUDIT.10) per Decision #13. **Delete** the existing D-PF.5 / D-PF.6 / D-AUDIT.3 closed-deferment block comments (those are orphan rot per Decision #13 — their content lives authoritatively in `docs/deferments.md § Archive`). Keep D-PF.1 and D-PF.3 active `@Ignore`d tests if they're being kept as bucket-1/2 entries (verify at task time — most likely D-PF.1 stays as bucket-2, D-PF.3 stays as bucket-1 or bucket-2 depending on FiledPlan API existence).
- **EXTERNAL FOLLOW-UP ONLY (NOT MODIFIED)** (moved out of MODIFY per plan-review round 18 — codex finding "Files MODIFY listing contradicts the rule that fn-18.2 does not edit pilot-firewall.md")
  - `~/.claude/plans/pilot-firewall.md` — fn-18.2 PROVIDES the exact MIGRATED header text and records it in the evidence block as a post-task follow-up for the user (per Decision #12). fn-18.2 does NOT edit this file directly and does NOT block on whether the user has applied the edit by task-close. fn-18.2 NEVER declares migration confirmed; evidence-block status is `pending` by default, `confirmed-by-user` only if the user independently reports they have applied the edit. Both states satisfy R11 acceptance (per plan-review round 7 normalization).

## Approach (numbered Steps)

### Step 0 — Baseline capture (BEFORE any edits)

Per plan-review round 18 — codex finding "baseline capture is inside the verify step, AFTER edits, so R14-NoNewRegression can't actually prove pre-existing failures": capture the base SHA and pre-task verify output BEFORE doing any inventory mutation, file edit, or test write. The "pre-task baseline" must be pre-EVERYTHING, not pre-verify.

```bash
git rev-parse HEAD > $TMPDIR/fn-18-2-base-sha.txt
# Capture base verify state. If any failures pre-exist, R14-NoNewRegression mode is in effect
# and the post-task verify must not introduce NEW failures beyond what this baseline records.
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
  2>&1 | tee $TMPDIR/fn-18-2-base-test.log
# Module preflight (fail-loud if any required Gradle task is missing — same rule across all fn-18 tasks):
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
  --dry-run --offline --no-daemon 2>&1 | tee $TMPDIR/fn-18-2-preflight.log
```

The Verify step (later) re-runs the same command and diffs against this baseline.

### Step 1 — Lock the inventory (acceptance artifact)

Run the inventory greps. **Two-pass discipline (hardened per plan-review rounds 16 + 17 — codex finding "anchored grep silently omits non-`**D-` references; awk section extractor is brittle on heading-depth and self-terminating-on-same-line")**: the first grep is a headings/anchored sanity check; the second grep is the authoritative concrete-ID extraction over the whole Deferments register section using a depth-agnostic section extractor that explicitly distinguishes the section heading from its first match.

```bash
# Pass 1 — anchored heading sanity check (catches the bolded **D-...** entries that pilot-firewall.md uses as headings)
grep -nE '^\*\*D-(PASS|AUDIT|PF|WORLD)' ~/.claude/plans/pilot-firewall.md > $TMPDIR/fn-18-2-pilot-firewall-anchored-ids.txt

# Pass 2 — robust section extractor (handles ANY heading depth via `^#+ ` regex; uses
# a "start-after-heading" flag instead of awk's range pattern which can self-terminate
# when the start and stop patterns match the same line OR when the depth changes
# unexpectedly):
awk '
  function depth(line,    d) { d = 0; while (substr(line, d+1, 1) == "#") d++; return d }
  /^#+ Deferments register/ { in_section=1; section_depth=depth($0); next }
  in_section && /^#+ / {
    # Stop only on a heading at the SAME or HIGHER level than the section opener.
    # Lower-level (nested) headings stay inside the section. Per plan-review round 18 —
    # codex finding "awk extractor stops on any heading, silently truncating on nested headings".
    if (depth($0) <= section_depth) { in_section=0; next }
  }
  in_section { print }
' ~/.claude/plans/pilot-firewall.md \
  | grep -oE "D-(PASS|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|PF)\.[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]" \
  | sort -u > $TMPDIR/fn-18-2-pilot-firewall-broad-ids.txt

# Reconcile — IDs in broad but not in anchored are either (a) real entries the anchored
# grep missed (different heading shape, indented entry, etc.) or (b) cross-reference /
# parenthetical mentions. The implementer classifies each at task time:
comm -23 $TMPDIR/fn-18-2-pilot-firewall-broad-ids.txt \
  <(grep -oE 'D-[A-Za-z0-9_.-]+' $TMPDIR/fn-18-2-pilot-firewall-anchored-ids.txt | sort -u) \
  > $TMPDIR/fn-18-2-pilot-firewall-anchor-gap.txt

# Authoritative set_A_pilot_firewall is the RECONCILED set (per plan-review round 17 —
# codex finding "set_A still uses anchored grep instead of reconciled broad inventory"):
# anchored IDs UNION (anchor-gap IDs classified as real entries). Cross-reference-only
# mentions go to a separate `cross_ref_only_in_pilot_firewall` field in evidence — they
# are NOT real entries and don't get docs/deferments.md entries.
#
# Implementer step: open $TMPDIR/fn-18-2-pilot-firewall-anchor-gap.txt, classify each
# line as real-entry OR cross-ref, write classified IDs to:
#   $TMPDIR/fn-18-2-pilot-firewall-anchor-gap-real-entries.txt
#   $TMPDIR/fn-18-2-pilot-firewall-anchor-gap-cross-refs.txt
# Then assemble the reconciled set:
cat $TMPDIR/fn-18-2-pilot-firewall-anchored-ids.txt \
    $TMPDIR/fn-18-2-pilot-firewall-anchor-gap-real-entries.txt 2>/dev/null \
  | grep -oE 'D-[A-Za-z0-9_.-]+' | sort -u > $TMPDIR/fn-18-2-set-A-pilot-firewall.txt
# The contents of fn-18-2-set-A-pilot-firewall.txt become the `set_A_pilot_firewall`
# evidence-JSON field. Cross-ref-only IDs go to `cross_ref_only_in_pilot_firewall`.
```

For each entry, classify by its status line in pilot-firewall.md. **Status taxonomy (hardened per plan-review round 14 — codex finding "status classifier misses DONE")**: closed statuses are `DONE` | `CLOSED` | `CLOSED-PARTIAL`. Open statuses are anything else NOT in that set (typically blank, `OPEN`, `PENDING`, `BLOCKED`). **Fail-loud rule**: any status string not in {`DONE`, `CLOSED`, `CLOSED-PARTIAL`, `OPEN`, `PENDING`, `BLOCKED`, blank} fails the task with `unknown_status_strings_in_pilot_firewall: [<list>]` recorded in evidence — fn-18.2 does NOT default an unknown status to open or closed. Expanding the taxonomy is a deliberate decision worth surfacing, not a silent assumption.

- **OPEN list**: every entry whose status is NOT in the closed set above. These get **active** `docs/deferments.md` entries.
- **CLOSED-from-pilot-firewall list**: every entry whose status IS in the closed set above AND that appears in the `DeferredContractsSpec.kt` orphan set (D-PF.2, D-PF.5, D-PF.6, D-AUDIT.3, D-AUDIT.5, D-AUDIT.6, D-AUDIT.10) OR otherwise needs an archive entry (per Decision #3 + Decision #13). These get **archive** entries.

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
# Set A: pilot-firewall.md IDs — the AUTHORITATIVE reconciled set from the inventory
# step above (anchored grep UNION anchor-gap real entries; per plan-review round 17 +
# 18 — codex finding "Set A still uses anchored grep instead of reconciled artifact").
# DO NOT re-grep the anchored form here; that contradicts the reconciled inventory.
cat $TMPDIR/fn-18-2-set-A-pilot-firewall.txt

# Set B: inline code-comment IDs (any module)
grep -rhEo "D-(PASS|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|PF)\.[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]" \
  --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/ \
  | sort -u > $TMPDIR/fn-18-2-set-B-inline.txt

# Compute the three derived sets from $TMPDIR/fn-18-2-set-A-pilot-firewall.txt and $TMPDIR/fn-18-2-set-B-inline.txt
# Set intersection (A ∩ B): inline comments referring to pilot-firewall.md-source IDs. fn-18.2 R10 scope.
comm -12 $TMPDIR/fn-18-2-set-A-pilot-firewall.txt $TMPDIR/fn-18-2-set-B-inline.txt > $TMPDIR/fn-18-2-set-intersection-A-and-B.txt
# Set B \ A: inline-only IDs (not in pilot-firewall.md). fn-18.3 scope; handed off via evidence JSON.
comm -23 $TMPDIR/fn-18-2-set-B-inline.txt $TMPDIR/fn-18-2-set-A-pilot-firewall.txt > $TMPDIR/fn-18-2-set-inline-only-for-fn-18-3.txt
```

**Authoritative source for the evidence-JSON `set_A_pilot_firewall` field**: `$TMPDIR/fn-18-2-set-A-pilot-firewall.txt` (the reconciled artifact from the inventory step), NOT the anchored sanity grep. The cross-ref-only IDs (anchor-gap entries classified as cross-references during the inventory step) live separately under the `cross_ref_only_in_pilot_firewall` field — they are NOT in Set A.

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

4. For bucket 3: check if a flow-next epic stub already exists (e.g. fn-16 for fn-14-source items — but those are NOT pilot-firewall.md-source items, so likely no new epics in .2). If a new stub is genuinely needed, create via `.flow/bin/flowctl epic create` + `epic set-plan` and leave at `todo` status only if multi-task scope is clear. **New-stub minimum spec template** (per plan-review round 14 — codex finding "bucket-3 stubs need Review considerations to satisfy project commandments"): the minimal spec MUST contain `## Overview` (1 paragraph), `## Boundaries / non-goals`, `## Acceptance` (at least one R-ID using the `- **Rn:** ...` form), `## Early proof point`, `## Requirement coverage` (table), AND `## Review considerations` (free-text section noting any cross-cutting concerns, risk areas, or known unknowns that subsequent `/flow-next:plan-review` should focus on). Without `## Review considerations` the stub fails plan-review at the next pass — better to land it complete the first time. fn-18.2 references the bucket-3 stub in `docs/deferments.md` only after the stub is created AND `flowctl validate --epic <new-id>` passes.

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
- `**Contract:**` (conditional — present when the pilot-firewall.md entry's "Real-fix contract" field carries detail richer than 3 sentences of `Why:` can hold; added per epic Decision #7 hardened per plan-review round 14 — codex finding "fn-18.2 Step 5 doesn't apply the Contract: schema where it matters most"). Pull the "Real-fix contract" field verbatim from pilot-firewall.md. **Audit rule for fn-18.2**: for every OPEN ID whose source entry has a "Real-fix contract" field >3 sentences (typically `D-PF.*` and `D-PASS-*` items pinning future API shapes), the `Contract:` field MUST be present and MUST preserve the contract text. fn-18.2's done summary records the count of entries that include `Contract:` and the count without — non-`Contract:` entries are auditable for "did we drop detail?" by re-reading the pilot-firewall.md source.
- `**Closes by:**` — pull from pilot-firewall.md's "Trigger" if it names a closing scenario.

### Step 6 — Populate `docs/deferments.md § Archive` for closed/deleted items

Each of D-PF.2 / D-AUDIT.5 / D-AUDIT.6 / D-AUDIT.10 (orphan tests) AND D-PF.5 / D-PF.6 / D-AUDIT.3 (existing closed-deferment block comments being deleted) gets an `### D-...` archive entry per epic Decision #3 schema (three fields: Status + Closed-by + Enforcement). The `Enforcement:` field captures the enforcement-surface references from the deleted comment blocks (per plan-review round 6 — archive must be at least as informative as the deleted state).

**Verify-before-write rule (per plan-review round 7)**: enforcement-spec names cited in the archive entries MUST be verified against the actual codebase BEFORE the archive entry is written. The example below names `RunUpDurationSpec`, `ResponsibilityInvariantSpec`, etc., illustratively — those names may be stale. For each closed deferment:
1. Read the existing closed-deferment block comment in `DeferredContractsSpec.kt` (it names the canonical enforcement surface).
2. `grep -rn` the cited type names + test class names to confirm they exist with that exact name.
3. If a cited name has changed (rename via subsequent passes), use the current name. Stale archive history is itself rot.
4. Record the verification step in the done summary as a per-entry pass/fail roll-up.

⚠ **SYNTHETIC EXAMPLE SHAPES — DO NOT COPY VERBATIM** (hardened per plan-review round 11 — codex finding "archive examples easy to cargo-cult"). The seven blocks below illustrate the schema; **every field of every real archive entry MUST be reconstructed from primary sources** (the in-file closed-deferment block comment + a fresh grep against current type/test names + the pass-N plan referenced as `Closed by:`). Forbidden: pasting any line below directly into `docs/deferments.md` without first running the verify-before-write rule against the current codebase. The names below were correct at planning time on a different branch and may be stale now.

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

Header template (revised per plan-review round 11 — codex finding "MIGRATED header `<pending>` SHA": the previous template included a `<pending>` commit SHA the agent cannot update post-commit; the user would either have to fill it themselves or leave a stale placeholder forever; switch to a `git log` pointer so the link to commit history is durable without requiring any value to be filled in):
```
**MIGRATED to docs/deferments.md per fn-18 on 2026-MM-DD.**
The entries below are preserved for historical context (pass-by-pass narrative).
For the active deferment register, see `docs/deferments.md` in the repo.
Commit history: `git log --grep "fn-18-deferment-register-reorganization-four" --oneline` in the repo.
```

Evidence-block text format — the only thing fn-18.2 records is `pending` vs `confirmed-by-user`. fn-18.2 itself never writes "migration confirmed":
- If user has independently applied the edit by task-close: `R11 — exact header text provided; external follow-up status: confirmed-by-user on YYYY-MM-DD (user-reported).`
- If not yet applied (the default case): `R11 — exact header text provided; external follow-up status: pending; user-edit to ~/.claude/plans/pilot-firewall.md is queued as non-blocking post-task follow-up.`

Both states satisfy R11 acceptance because R11 gates on "text provided + status logged," NOT on the external edit having landed. Per epic Decision #12 the legacy register is not declared migrated until the user actually edits the file — that's outside fn-18.2's reach.

### Step 9 — Verify

**Pre-task module preflight** (per plan-review round 11 — codex finding ":sim module preflight missing"): confirm referenced Gradle tasks exist before running verify.

```bash
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
  --dry-run --offline --no-daemon 2>&1 | tee $TMPDIR/fn-18-preflight.log
# --dry-run prints the task graph that WOULD execute without actually running tests.
# Gradle exits non-zero if any task is unknown (e.g. ':sim:jvmTest' if :sim module is absent).
# This is the load-bearing preflight check (per plan-review round 13 — codex finding
# './gradlew tasks without --all does NOT list :sim:jvmTest, so the prior preflight would
# false-positive on a valid repo'). --dry-run is the canonical Gradle preflight idiom.
# **Fail loud (per plan-review round 12 — codex finding 'silent under-verification path'): if ANY required Gradle task is missing, halt the task with an explicit `gradle_module_preflight_failure: <missing tasks>` line in evidence and refuse to substitute a trimmed verify command. This is a repository/module mismatch worth surfacing — silently trimming would let fn-18 ship without exercising the goldens that R14 gates on. The only acceptable diversion is the R14-NoNewRegression branch (baseline already red), and that branch must run the SAME task set as the baseline did — not a trimmed set.
```

Baseline already captured in Step 0 (`$TMPDIR/fn-18-2-base-sha.txt` + `$TMPDIR/fn-18-2-base-test.log`). No need to re-run pre-task capture here — that's a defense against round-2's split-baseline anti-pattern (round 18 — codex finding 'baseline must precede edits').

**Post-task verification**: re-run the same command. R14 acceptance is "no failures present in post-task log that were not present in base log." If base log was already red, the diff (not the absolute pass/fail) is what matters.

Nine goldens GREEN at post-task (per fn-15 closure: the ninth golden `G3aPilotReactiveTailwindTest` is now a permanent fixture). Detekt baseline unchanged. Any newly-created `@Ignore`d test compiles (proving its API references exist today) OR is bucket 2 with commented-out future-API references (compilable shell only). No `@Ignore` test goes from pass to fail.

**`flowctl done` invocation** (per plan-review round 11 — codex finding "no concrete done-time step"): write the done summary and evidence JSON to dedicated files, then invoke `flowctl done` with both flags. The evidence JSON is the structured handoff for fn-18.3 — it MUST contain the `locked_inventory` block with `open_ids`, `closed_from_pilot_firewall_ids`, `inline_ids_observed_deferred_to_fn_18_3`, `bucket_distribution`, and `external_followup_status` keys (per Step 1 + Step 8).

```bash
# Write done summary
cat > $TMPDIR/fn-18-2-summary.md <<'EOF'
fn-18.2 shipped: migrated <N> pilot-firewall.md OPEN deferments into docs/deferments.md per the four-bucket model. Bucket distribution: <fill>. Orphan tests resolved: <list>. MIGRATED header provided as post-task follow-up; external-follow-up status: <pending|confirmed-by-user>. Implementation commit: see evidence-JSON `implementation_sha` field. Nine goldens GREEN.
EOF
# Write evidence JSON — populated from Step 1 locked-inventory + Step 8 MIGRATED-header status
cat > $TMPDIR/fn-18-2-evidence.json <<'EOF'
{
  "task": "fn-18-deferment-register-reorganization-four.2",
  "base_sha": "<from Step 0 base-sha.txt>",
  "implementation_sha": "<SHA of the implementation commit BEFORE flowctl done",
  "gradle_module_preflight": ["<list from preflight output>"],
  "locked_inventory": {
    "open_ids": ["..."],
    "closed_from_pilot_firewall_ids": ["..."],
    "set_A_pilot_firewall": ["..."],
    "set_B_inline": ["..."],
    "set_intersection_A_and_B": ["..."],
    "set_inline_only_for_fn_18_3": ["..."],
    "inline_ids_observed_deferred_to_fn_18_3": ["..."],
    "bucket_distribution": {"bucket_1": 0, "bucket_2": 0, "bucket_3": 0, "bucket_4": 0}
  },
  "_schema_note": "locked_inventory keys MUST match Step 1's specification (set_A/set_B/set_intersection/set_inline_only_for_fn_18_3). fn-18.3 Step 6 reads these keys verbatim. Added per plan-review round 13 — codex finding 'evidence JSON schema inconsistency'.",
  "orphan_tests_resolved": {
    "deleted": ["..."],
    "converted": ["..."]
  },
  "external_followup_status": "pending",
  "migrated_header_text_lines": [
    "**MIGRATED to docs/deferments.md per fn-18 on YYYY-MM-DD.**",
    "The entries below are preserved for historical context (pass-by-pass narrative).",
    "For the active deferment register, see `docs/deferments.md` in the repo.",
    "Commit history: `git log --grep \"fn-18-deferment-register-reorganization-four\" --oneline` in the repo."
  ],
  "_migrated_header_text_note": "Lines as written into the array MUST match Step 8's template character-for-character; only YYYY-MM-DD is filled. R11's 'exact text provided' criterion gates on this array equalling Step 8 verbatim. Per plan-review round 15 — codex finding 'migrated_header_text was paraphrased; collapsing to a single string drifted from canonical'.",
  "verify_command": "./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt",
  "verify_outcome": "BUILD SUCCESSFUL"
}
EOF
.flow/bin/flowctl done fn-18-deferment-register-reorganization-four.2 \
  --summary-file $TMPDIR/fn-18-2-summary.md \
  --evidence-json $TMPDIR/fn-18-2-evidence.json --json
```

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
- [ ] **R16** — Every bucket-1 `@Ignore`d test referenced by `docs/deferments.md`, **whether NEW or RETAINED** (per plan-review round 16 scope-widening), contains at least one **non-import** real-current-API value-flow reference inside its test body — an assertion using a real type, OR a type construction of a real domain class, OR a function call returning a real value. Import-only references fail R16 (per epic Decision #1 hardened per plan-review round 6). For retained placeholders (D-PF.1, D-PF.3, any other pre-fn-18 carry-over): if import-only, fn-18.2 either upgrades the test body with a value-flow reference OR re-classifies the deferment to bucket 2 in `docs/deferments.md`. Silent retention of an import-only "bucket 1" entry is forbidden. Bucket-2 tests are exempt. Verified by reading each bucket-1 test body referenced by `docs/deferments.md` at task close; result recorded in done summary as a per-test pass/fail roll-up.
- [ ] **R14** — Recorded as one of two outcomes per epic Decision #R14: **R14-Passed** (gradle exits 0; nine goldens GREEN; detekt unchanged; new `@Ignore`d tests compile) OR **R14-NoNewRegression** (baseline was already red; task evidence records base SHA + exact failing tests at start and proves no new failures introduced by fn-18.2's changes).

## Notes for fn-18.3

fn-18.3 picks up the remaining migration sources (fn-14/15/17 epic-spec siblings + inline-only IDs that aren't pilot-firewall.md-sourced). The schema, conventions, and per-module DeferredContractsSpec.kt locations established by fn-18.1/.2 apply unchanged. fn-18.3 also updates fn-14/15/17 epic specs to redirect their `## Deferments register` sections at `docs/deferments.md` (R12).

## Done summary
fn-18.2 shipped: migrated 32 OPEN pilot-firewall.md deferments into docs/deferments.md per the four-bucket model. Bucket distribution: 1=4 / 2=16 / 3=0 / 4=12. Orphan tests resolved: 4 deleted (D-PF.2, D-AUDIT.5/.6/.10); 3 closed-comment blocks deleted (D-PF.5/.6, D-AUDIT.3); D-PF.1 re-classified to bucket 2 per R16 widening; D-PF.3 retained as bucket 1 with new value-flow reference. Per-module DeferredContractsSpec.kt files created for controller/protocol/sim (pilot updated). 18 archive entries (7 orphan-set + 11 R10-cross-referenced). MIGRATED header provided as post-task follow-up; external-follow-up status: pending. Implementation commits: e32a275 + 0b1d21d + 55bcfed. Nine sim goldens GREEN (R14-Passed: ./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt BUILD SUCCESSFUL).
## Prior block (resolved)
A prior worker recorded a BLOCKED: EXTERNAL_BLOCKED finding because `~/.claude/plans/pilot-firewall.md` was not present on this host. The user subsequently staged the file (122 KB, 1055 lines, 33 anchored D-* entries) at the canonical path and the task was re-claimed. The fresh worker proceeded from Step 0 and the migration shipped — see Done summary above.

## Evidence

**Commits** (fn-18.2 implementation chain):
- `e32a275` — `docs(deferments): migrate pilot-firewall.md OPEN items + orphan-test cleanup` (migration body: docs/deferments.md populated, per-module DeferredContractsSpec.kt created for controller/protocol/sim, pilot updated, archive entries seeded).
- `0b1d21d` — `fn-18.2 round-1 codex: clear stale BLOCKED narrative from task md` (round-1 codex fix).
- `55bcfed` — `fn-18.2 round-2 codex: fill done summary + evidence in task spec md` (round-2 codex fix; richer summary was overwritten by `flowctl done` on the canonical summary-file content — full content remains in `flowctl show fn-18.2 --json | jq .done.summary` and the evidence JSON cited below).
- `dd97a56` — `fn-18.2 round-3 codex: flip task .json status to done; flowctl done md state` (round-3 codex fix).

**Tests** — `./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt --offline --no-daemon` → `BUILD SUCCESSFUL`. Outcome: **R14-Passed**. Nine sim goldens GREEN; detekt baseline unchanged; new `@Ignore`d tests compile (bucket 1 with real value-flow references; bucket 2 with commented-out pseudo-code only). Full log archived at `/tmp/claude-501/fn-18-2-final-verify.log` (session-local).

**PRs** — none yet; fn-18 epic will surface a PR at epic close.

### Locked inventory (R7) — Set A, pilot-firewall.md reconciled

OPEN IDs (32) — every entry has an active `### D-...` block in `docs/deferments.md`:

```
D-AUDIT-airac-cycle-tracking, D-AUDIT-arp-proxy-runtime, D-AUDIT-ljmb-polygon,
D-AUDIT-polygon-ctr, D-AUDIT.11, D-AUDIT.2.C-FOLLOWUP, D-AUDIT.2.F-FOLLOWUP,
D-AUDIT.3.II-FOLLOWUP, D-AUDIT.4.A.II-FOLLOWUP, D-AUDIT.4.B-FOLLOWUP,
D-AUDIT.4.D.II-FOLLOWUP, D-AUDIT.6.C-FOLLOWUP, D-AUDIT.7.II-FOLLOWUP,
D-AUDIT.7.III-FOLLOWUP, D-AUDIT.8.II-FOLLOWUP, D-AUDIT.8.III-FOLLOWUP,
D-AUDIT.8.IV-FOLLOWUP, D-AUDIT.9.II-FOLLOWUP, D-AUDIT.9.III-FOLLOWUP,
D-AUDIT.9.IV-FOLLOWUP, D-AUDIT.9.V-FOLLOWUP, D-PASS-13.3-II-FOLLOWUP,
D-PASS-17.1, D-PASS-17.2, D-PASS-17.3-FOLLOWUP, D-PASS-cross-aircraft-step-on,
D-PASS-g1-diagnostics-typed-events, D-PASS-pilot-mid-tng-fullstop-recovery,
D-PF.1, D-PF.3, D-PF.8, D-WORLD.1
```

CLOSED IDs in Set A (21 total) — broken into three classes:

```
(a) Orphan-test set (7, per Decision #3 + #13; archived in docs/deferments.md):
    D-PF.2, D-PF.5, D-PF.6, D-AUDIT.3, D-AUDIT.5, D-AUDIT.6, D-AUDIT.10

(b) R10-cross-referenced closed entries (11, surfaced by inline-grep
    verification; archived in docs/deferments.md):
    D-PF.4, D-PF.7, D-AUDIT.1, D-AUDIT.2, D-AUDIT.4, D-AUDIT.6.A-FOLLOWUP,
    D-AUDIT.7, D-AUDIT.8, D-AUDIT.9, D-AUDIT.12, D-AUDIT.14

(c) Set-A-closed-but-unanchored (3, NOT archived per Decision #3 scope —
    none are orphan-test sources AND none have inline anchors in Set B,
    so neither archive criterion fires):
    D-AUDIT-lowg-ctr-radius, D-AUDIT.13, D-PASS-g1-diagnostics

  Verification: grep -rn '<id>' --include="*.kt" pilot/ controller/ protocol/
  sim/ core/ migration/ yields zero hits for each of the three; none appear
  in any DeferredContractsSpec.kt closed-comment block; pilot-firewall.md's
  status field carries 'CLOSED by fn-7' / 'CLOSED (Pass 14)' /
  'CLOSED-PARTIAL by fn-8.3' respectively.

Reconciliation: |Set A| = 32 (OPEN) + 21 (CLOSED) = 53. Of the 21 CLOSED,
18 are archived in docs/deferments.md (orphan-set 7 + R10 cross-ref 11),
3 are intentionally not archived per Decision #3.

Archive total in docs/deferments.md: 7 (orphan-set) + 11 (R10 cross-ref) = 18.
```

Cross-ref-only IDs (NOT real entries, excluded from Set A):

```
D-AUDIT.2.A-FOLLOWUP, D-AUDIT.2.B-FOLLOWUP, D-AUDIT.2.E-FOLLOWUP,
D-AUDIT.4.A-FOLLOWUP, D-AUDIT.4.B, D-AUDIT.4.D-FOLLOWUP, D-AUDIT.6.A,
D-AUDIT.9.x, D-AUDIT.N, D-PASS-13.1, D-PASS-13.2, D-PASS-13.3,
D-PASS-N.x, D-PF.9
```

### Set-boundary cardinalities (R10 — durable handoff to fn-18.3)

- `|set_A_pilot_firewall|` = 53 (reconciled anchored ∪ anchor-gap-real).
- `|set_B_inline|` = 65 (grep of `D-*` IDs across `*.kt` in `pilot/ controller/ protocol/ sim/ core/ migration/`).
- `|set_intersection_A_and_B|` = 36 (pilot-firewall-source IDs with inline anchors — fn-18.2's R10 scope; every ID has a `docs/deferments.md` entry).
- `|set_inline_only_for_fn_18_3|` = 29 (inline-only IDs not in Set A; fn-18.3 picks these up).

`set_inline_only_for_fn_18_3` content (handoff for fn-18.3):

```
D-AUDIT.2.A, D-AUDIT.2.A-FOLLOWUP, D-AUDIT.2.B, D-AUDIT.2.E,
D-AUDIT.4.A-FOLLOWUP, D-AUDIT.4.D-FOLLOWUP, D-AUDIT.9.II, D-PASS-13.1,
D-PASS-13.2, D-PASS-13.3, D-PASS-cap413-2_7-principle-cite-audit,
D-PASS-cap413-4_46-principle-cite-audit, D-PASS-continue-approach-pilot-readback,
D-PASS-fixture-per-plan-filing-time, D-PASS-fn6-snap-derived,
D-PASS-g3a-obstruction-aerodrome-payload, D-PASS-g3a-obstruction-clearsAt-update,
D-PASS-g3a-obstruction-kind-variants, D-PASS-g3a-react-atis-cadence-sensing,
D-PASS-g3a-react-personal-minimums, D-PASS-g3a-react-tailwind-limit,
D-PASS-g3a-react-tailwind-personal-minimums, D-PASS-g3a-react-vrb-handling,
D-PASS-g3b-react-cross, D-PASS-g3b-react-cross-aerodrome-crosswind,
D-PASS-g3b-react-cross-aerodrome-tailwind, D-PASS-wind-state-migrate-to-aerodrome,
D-PF.9, D-WORLD-BACKED
```

Note: `D-PASS-g3b-react-cross` and `D-AUDIT.9.II` and `D-AUDIT.2.A`/`.B`/`.E` are regex-fragment matches against longer IDs; `D-WORLD-BACKED` is a false-positive from a ClearanceId string literal in `core/.../ResolvedClearanceTest.kt:343`. fn-18.3 will de-dup these during its own reconciliation pass.

### MIGRATED header (R11) — exact text, external follow-up status: pending

The user-edit to `~/.claude/plans/pilot-firewall.md § Deferments register` is queued as non-blocking post-task follow-up. fn-18.2 does NOT edit the file directly and does NOT auto-flip the status to `confirmed-by-user` — that flip requires the user to independently report they have applied the edit.

Exact 4-line header text (verbatim per Step 8 / Decision #12 canonical template; only `YYYY-MM-DD` is filled to today's date):

```
**MIGRATED to docs/deferments.md per fn-18 on 2026-05-13.**
The entries below are preserved for historical context (pass-by-pass narrative).
For the active deferment register, see `docs/deferments.md` in the repo.
Commit history: `git log --grep "fn-18-deferment-register-reorganization-four" --oneline` in the repo.
```

### R16 per-test value-flow roll-up (R16)

All four bucket-1 tests carry non-import current-API value-flow references:

| Bucket-1 test | Value-flow reference | R16 |
|---|---|---|
| `pilot/.../DeferredContractsSpec.kt::D-PF3 airborne-spawned ...` | `FiledPlan.Vfr(...)` construct + `assertEquals(RunwayId("16C"), filed.destinationRunway)` + `assertNotNull(filed.destinationAerodrome)` | PASS |
| `controller/.../DeferredContractsSpec.kt::D-AUDIT7-III BeliefState ...` | `val state: BeliefState = BeliefState(); assertNull(state.activeRunway); val seeded = state.copy(activeRunway = RunwayId("16C")); assertEquals(...)` | PASS |
| `pilot/.../DeferredContractsSpec.kt::D-AUDIT8-IV ATIS letter resolution ...` | `Map<AerodromeId, Atis>` construction with `assertEquals(2, map.size)` + `assertEquals('A', map.getValue(lowgAtis.aerodrome).letter)` | PASS |
| `controller/.../DeferredContractsSpec.kt::D-PASS-13_3-II RunwayLengthFailure ...` | `RunwayLengthFailure.RunwayTooShort(...)` construction + `assertTrue(failure is ...)` + `assertEquals(RunwayLengthOperation.LANDING, failure.operation)` | PASS |

### Orphan-test cleanup (R6)

All four orphan tests deleted (closures had landed full structural enforcement; no shape left unverified that would warrant conversion):

- `D-PF.2` — deleted (Pass 5 closure: `RunwayAssignmentSource` sealed type + `applyPrecedence` 6×6 invariant tests in `ProcessInstructionRunwayDerivationSpec`).
- `D-AUDIT.5` — deleted (Pass 7 closure: `ResponsibilityState` sealed type + `ResponsibilityInvariantSpec` + `ResponsibilityStateMachineSpec`).
- `D-AUDIT.6` — deleted (Pass 11 closure: `FiledPlan` sealed + `SimEvent.FlightPlanFiled` + `FixtureLoadSpec` + `FlightPlanFilingSpec`).
- `D-AUDIT.10` — deleted (Pass 11 closure alongside D-AUDIT.6: `FirewallFixtureNoDirectResponsibilitiesTest` E20 architectural firewall).

Closed-deferment block comments deleted from `pilot/.../DeferredContractsSpec.kt` (Decision #13):

- `D-PF.5` (filed-plan-only intent), `D-PF.6` (TaxiTo split), `D-AUDIT.3` (per-type runUpDurationMs).

### Bucket distribution (R8) for the 32 OPEN

- **Bucket 1 (4)** — test contract, current-API value-flow reference: `D-AUDIT.7.III-FOLLOWUP`, `D-AUDIT.8.IV-FOLLOWUP`, `D-PASS-13.3-II-FOLLOWUP`, `D-PF.3`.
- **Bucket 2 (16)** — API gap, commented-out future API: `D-AUDIT.2.C-FOLLOWUP`, `D-AUDIT.2.F-FOLLOWUP`, `D-AUDIT.3.II-FOLLOWUP`, `D-AUDIT.4.A.II-FOLLOWUP`, `D-AUDIT.4.D.II-FOLLOWUP`, `D-AUDIT.6.C-FOLLOWUP`, `D-AUDIT.7.II-FOLLOWUP`, `D-AUDIT.8.II-FOLLOWUP`, `D-AUDIT.8.III-FOLLOWUP`, `D-AUDIT.9.II-FOLLOWUP`, `D-AUDIT.9.III-FOLLOWUP`, `D-AUDIT.9.IV-FOLLOWUP`, `D-AUDIT.9.V-FOLLOWUP`, `D-PASS-17.2`, `D-PF.1`, `D-WORLD.1`.
- **Bucket 3 (0)** — multi-task epic stub: none of the pilot-firewall items required a new epic.
- **Bucket 4 (12)** — narrative / cross-cutting: `D-AUDIT-airac-cycle-tracking`, `D-AUDIT-arp-proxy-runtime`, `D-AUDIT-ljmb-polygon`, `D-AUDIT-polygon-ctr`, `D-AUDIT.11`, `D-AUDIT.4.B-FOLLOWUP`, `D-PASS-17.1`, `D-PASS-17.3-FOLLOWUP`, `D-PASS-cross-aircraft-step-on`, `D-PASS-g1-diagnostics-typed-events`, `D-PASS-pilot-mid-tng-fullstop-recovery`, `D-PF.8`.

Total 4 + 16 + 0 + 12 = 32. ✓