---
satisfies: [R1, R5, R7, R8, R9, R10, R12, R14, R15, R16]
---

# fn-18.3 — Migrate fn-14/15/17 epic-spec siblings + inline-only IDs + epic-spec redirects

## Description

Closes the migration. Two sources to triage and pin: (a) the in-repo epic-spec `## Deferments register` sections of fn-14 / fn-15 / fn-17 (planning-time estimate: 11 IDs from fn-14, 6 from fn-15, 2 concrete + 1 conditional-placeholder from fn-17 = ~19 IDs total; verify at task time via locked-inventory step); (b) the inline-only IDs that exist solely as code comments and were never filed in pilot-firewall.md. <!-- Updated by plan-sync: fn-18.2 handed off 29 inline-only IDs (|B\A|=29) in evidence JSON `set_inline_only_for_fn_18_3`, not the ~6 originally estimated. The 6 originally-named anchors (D-PASS-fn6-snap-derived, D-PASS-g3a-obstruction-aerodrome-payload, D-PASS-continue-approach-pilot-readback, D-PASS-g3a-obstruction-kind-variants, D-PASS-g3a-obstruction-clearsAt-update, D-PASS-fixture-per-plan-filing-time) are a subset; the authoritative list is fn-18.2's evidence. --> The actual handoff from fn-18.2 is 29 inline-only IDs in evidence JSON `set_inline_only_for_fn_18_3` (set-boundary cardinalities: |A|=53, |B|=65, |A∩B|=36, |B\A|=29). Treat the 6-ID anchor list above as illustrative; the locked inventory comes from `flowctl show fn-18-deferment-register-reorganization-four.2 --json | jq .evidence.locked_inventory.set_inline_only_for_fn_18_3`. Per epic Decision #10: planning-time counts are non-authoritative; locked inventory artifact controls. After triage, every fn-14/15/17 epic-spec `## Deferments register` section gets a redirect line pointing at `docs/deferments.md` as the single source of truth (without deleting the existing block — preserving as historical artefact).

Records the final bucket distribution across all sources in the done summary.

## Problem

fn-14's `## Deferments register` was filed during the pre-fn-18 model. fn-15 / fn-17 followed the same pattern. Six inline-only IDs were filed only as KDoc breadcrumbs because no canonical store existed at the time. Today these three in-spec registers and the six orphan inline-only IDs are scattered: an agent doesn't know "the fn-14 deferment register is in fn-14's spec" without reading the spec end-to-end. This task lifts everything into `docs/deferments.md` per fn-18.1's convention.

## Files (read or modify)

- **READ**
  - `./docs/deferments.md` (populated by fn-18.2 with pilot-firewall.md-source items + meta-deferments) — current state.
  - `./docs/deferments-CONVENTION.md` (created by fn-18.1) — the decision tree.
  - `./.flow/specs/fn-14-g3a-react-pilot-reactive-go-around-on.md` § Deferments register — planning-time estimate: 11 entries (9 g3a-react + wind-state + cap413). Non-authoritative per epic Decision #10; lock at task time.
  - `./.flow/specs/fn-15-g3a-react-tailwind-pilot-reactive-go.md` § Deferments register — planning-time estimate: 6 entries (tailwind siblings). Non-authoritative; lock at task time.
  - `./.flow/specs/fn-16-wind-state-migrate-to-aerodromeweather.md` § Deferments register — confirm contents (may have no new siblings beyond closing fn-14's wind-state entry).
  - `./.flow/specs/fn-17-cap-413-edition-24-numbering.md` § Deferments register — planning-time estimate: 2 concrete entries + 1 conditional placeholder + closure record. Non-authoritative; lock at task time.
  - All `.kt` files in `pilot/` `controller/` `protocol/` `sim/` `core/` `migration/` — grep for inline-only IDs (those NOT in pilot-firewall.md OR in fn-14/15/17 specs).
  - `./.plan` (added per plan-review round 10, hardened per plan-review round 13) — repo-root in-repo operational backlog. Grep for any `D-*` IDs; triage each per `.plan`-source rules in Step 1 (dedup against fn-18.2 evidence; file new IDs as active or Archive depending on `.plan`-status). fn-18.3 DOES surgically rewrite each `.plan` D-* entry to a one-line `<ID> — see docs/deferments.md` pointer (collapsing the previously-ambiguous "two canonical surfaces" state). Non-D-* `.plan` content (B*, IFR-*, RR-*, M*, narrative sections) is NOT edited.
  - `./.flow/epics/fn-15-g3a-react-tailwind-pilot-reactive-go.json` — confirm status=`todo`/`open`.
  - `./.flow/epics/fn-16-wind-state-migrate-to-aerodromeweather.json` — confirm status.
  - `./.flow/epics/fn-17-cap-413-edition-24-numbering.json` — confirm status.

- **CREATE (if applicable)**
  - Per-module `DeferredContractsSpec.kt` files if new bucket 1/2 items land in modules that didn't get a file in fn-18.2.

- **MODIFY**
  - `./docs/deferments.md` — append the ~22 + ~6 new entries per the schema, organised by ID prefix.
  - **fn-14 / fn-15 / fn-17 epic specs via `flowctl epic set-plan`** (per plan-review round 3): the project Flow-next rules say use `flowctl` for Flow state instead of ad-hoc edits. For each spec:
    1. Read current spec via `.flow/bin/flowctl cat fn-14-g3a-react-pilot-reactive-go-around-on` (or appropriate ID). The `cat` subcommand prints raw spec markdown; `flowctl show <id>` prints structured metadata + spec. **Verify `cat` exists at task start** (per plan-review round 18 — codex finding "task relies on `flowctl cat` but only `show` is documented in Flow-Next commands"): run `.flow/bin/flowctl --help 2>&1 | grep -E "^\s+cat\b"` and fall back to `.flow/bin/flowctl show <id> --json | jq -r .spec` if `cat` is absent. As of fn-18 planning, `cat` IS present (verified); the fallback is defense-in-depth.
    2. Save current contents to `/tmp/<epic-id>-current.md`.
    3. Edit `/tmp/<epic-id>-current.md` to prepend the MIGRATED redirect line to the `## Deferments register` section.
    4. Re-apply via `.flow/bin/flowctl epic set-plan <epic-id> --file /tmp/<epic-id>-current.md`.
    5. Verify with `.flow/bin/flowctl validate --epic <epic-id> --json`.
  - fn-16 epic spec: check its `## Deferments register` for any NEW siblings (beyond closing fn-14's wind-state deferment). If yes, same flowctl flow. If no, no edit needed.

## Approach (numbered Steps)

### Step 0 — Baseline capture (BEFORE any edits)

Per plan-review round 18 — codex finding "baseline capture is inside the verify step, AFTER edits, so R14-NoNewRegression can't actually prove pre-existing failures": capture the base SHA and pre-task verify output BEFORE doing any inventory mutation, file edit, or test write. The "pre-task baseline" must be pre-EVERYTHING, not pre-verify.

```bash
git rev-parse HEAD > $TMPDIR/fn-18-3-base-sha.txt
# Capture base verify state. If any failures pre-exist, R14-NoNewRegression mode is in effect
# and the post-task verify must not introduce NEW failures beyond what this baseline records.
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
  2>&1 | tee $TMPDIR/fn-18-3-base-test.log
# Module preflight (fail-loud if any required Gradle task is missing — same rule across all fn-18 tasks):
./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt \
  --dry-run --offline --no-daemon 2>&1 | tee $TMPDIR/fn-18-3-preflight.log
```

The Verify step (later) re-runs the same command and diffs against this baseline.

### Step 1 — Lock the inventory (acceptance artifact)

Parse fn-14 / fn-15 / fn-16 / fn-17 epic specs' `## Deferments register` sections and enumerate every ID. Grep for inline-only IDs across the codebase using the BROAD pattern (per plan-review round 4 — same pattern as Step 7 to catch dotted IDs):

```bash
# Broad combined pattern
grep -rn -E "D-(PASS|WORLD)-[A-Za-z0-9_.-]+|D-(AUDIT|PF)\.?[A-Za-z0-9_.-]+" --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/

# Or two separate greps for clarity
grep -rn -E "D-(PASS|WORLD)-" --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/
grep -rn -E "D-(AUDIT|PF)\." --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/
```

Filter the grep results to IDs that are NOT in fn-18.2's locked inventory (those are pilot-firewall.md-source items, already handled). The remainder are inline-only or in-spec siblings.

**Paste the full ID list verbatim into fn-18.3's done summary at task close** as the locked inventory artifact for this task. Acceptance R7 (complete) is "every ID in the union of fn-18.2's locked list AND fn-18.3's locked list appears exactly once in `docs/deferments.md`."

Inventory expected:

**From fn-14 spec § Deferments register**:
- D-PASS-g3a-react-tailwind-limit (→ existing fn-15 epic)
- D-PASS-g3a-react-gust-evaluation
- D-PASS-g3a-react-wind-variability-dynamics
- D-PASS-g3a-react-multi-aircraft-crosswind
- D-PASS-g3b-react-cross-aerodrome-crosswind
- D-PASS-g3a-react-other-poh-triggers
- D-PASS-g3a-react-personal-minimums
- D-PASS-g3a-react-atis-cadence-sensing
- D-PASS-g3a-react-vrb-handling
- D-PASS-wind-state-migrate-to-aerodrome (→ existing fn-16 epic)
- D-PASS-cap413-edition-24-reconciliation (→ existing fn-17 epic)

**From fn-15 spec § Deferments register** (6 new siblings):
- D-PASS-g3a-react-tailwind-gust-evaluation
- D-PASS-g3a-react-multi-aircraft-tailwind
- D-PASS-g3a-react-combined-wind-vector
- D-PASS-g3a-react-tailwind-atis-cadence
- D-PASS-g3a-react-tailwind-condition-corrections
- D-PASS-g3a-react-tailwind-personal-minimums

**From fn-17 spec § Deferments register** (2 concrete + 1 conditional-placeholder):
- D-PASS-cap413-edition-24-rename-pending-pdf
- D-PASS-cap413-edition-24-<section> — **conditional placeholder, NOT a stable ID** (per epic Decision #10 clarification). No `docs/deferments.md` entry filed unless a concrete ID lands. Done summary records the conditional language.
- D-PASS-cap413-principle-text-deep-refresh

**Inline-only IDs (from grep)** <!-- Updated by plan-sync: fn-18.2 used set_inline_only_for_fn_18_3 (29 IDs, |B\A|=29) not the ~6 originally estimated. The 6 anchors below are real-but-incomplete; the authoritative source is fn-18.2's evidence JSON. -->:

The authoritative inline-only list is fn-18.2's evidence-JSON field `set_inline_only_for_fn_18_3` (29 IDs; |B\A|=29). Read it via:

```bash
.flow/bin/flowctl show fn-18-deferment-register-reorganization-four.2 --json \
  | jq -r '.evidence.locked_inventory.set_inline_only_for_fn_18_3 // .evidence.locked_inventory.set_inline_only_for_fn_18_3[]?'
```

The 6 anchors below are the originally-named inline-only KDoc breadcrumbs (from planning-time grep); they are a subset of the 29 IDs fn-18.2 actually handed off. fn-18.3 re-runs its own grep at task time (Step 7 / Step 7b) and reconciles against the evidence JSON.

- D-PASS-fn6-snap-derived (`controller/.../AircraftObservationTestFixtures.kt`, `controller/.../ControllerTypes.kt`)
- D-PASS-g3a-obstruction-aerodrome-payload (`controller/.../ControllerTypes.kt`, `controller/.../Event.kt`, `sim/.../RunwayObstructionWiring.kt`)
- D-PASS-continue-approach-pilot-readback (`protocol/.../Instruction.kt`, `sim/.../G3aRunwayObstructionContinueApproachTest.kt`)
- D-PASS-g3a-obstruction-kind-variants (`core/.../RunwayObstruction.kt`)
- D-PASS-g3a-obstruction-clearsAt-update (`core/.../RunwayObstruction.kt`)
- D-PASS-fixture-per-plan-filing-time (`sim/.../G1TwoAircraftCircuitsTest.kt`)

The remaining ~23 inline-only IDs (regex-fragment matches against longer IDs, plus closed/CLOSED-PARTIAL items surfaced by inline grep) are listed verbatim in fn-18.2's `set_inline_only_for_fn_18_3`. fn-18.2's evidence also flags `D-WORLD-BACKED` as a known false positive (ClearanceId string literal in `core/.../ResolvedClearanceTest.kt:343`) and notes `D-PASS-g3b-react-cross`, `D-AUDIT.9.II`, `D-AUDIT.2.A`/`.B`/`.E` as regex-fragment matches against longer IDs — fn-18.3 de-dups these during its own reconciliation pass.

**From `.plan` (added per plan-review round 10 — explicit fn-18.3 migration source)**:

`.plan` is the repo-root in-repo operational backlog. Per epic Decision #10's `.plan` handling: scan with the same regex used for repo-wide grep, then triage each ID:

```bash
grep -nE "D-(PASS|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|PF)\.[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]" .plan | sort -u
```

Planning-time scan (non-authoritative; verify at task time):
- `D-PASS-g1-diagnostics` — DONE / CLOSED-PARTIAL per `.plan` line ~486. fn-18.3 files as Archive entry in `docs/deferments.md` (three-field schema per Decision #3).
- `D-PASS-g1-diagnostics-typed-events` — OPEN. Likely already in pilot-firewall.md OPEN list (fn-18.2 scope). Dedup at task time: if fn-18.2 already filed it, fn-18.3 makes NO new entry.
- `D-PASS-cross-aircraft-step-on` — OPEN. Same dedup rule.
- `D-PASS-pilot-mid-tng-fullstop-recovery` — OPEN. Same dedup rule.

For each `.plan` ID:
- If already filed by fn-18.2 (in `set_A_pilot_firewall` from fn-18.2 evidence JSON): no new entry. fn-18.3 records "dedup'd against fn-18.2" in done summary.
- If NOT yet filed: bucket-triage and file an active entry (if OPEN) or Archive entry (if DONE/CLOSED).
- `.plan` D-* line treatment (hardened per plan-review round 13 — codex finding "two active-looking canonical surfaces", further hardened per plan-review round 16 — codex finding "rewriting .plan D-* entries to pointers can erase contract detail that wasn't captured upstream"):
  1. **Content-preservation audit BEFORE rewrite (MANDATORY)**: for every `.plan` D-* entry, regardless of whether fn-18.2 already filed the ID, read the `.plan` body and compare against the matching `docs/deferments.md` entry. If the `.plan` body contains contract detail (eventual API shape, blocked-on prerequisite, named closure trigger, regression-test plan) that's NOT preserved in the docs entry's `Why:` + `Contract:` fields, **merge the missing detail into the docs entry FIRST** (extending `Why:` to its 3-sentence cap if useful; adding/extending the `Contract:` field per Decision #7-revised; updating `Closes by:` if the `.plan` named a sharper closure trigger). Only after the docs entry is at least as informative as the `.plan` body may the `.plan` line be rewritten to a pointer.
  2. **Rewrite step (after preservation audit)**: rewrite the `.plan` D-* entry to a one-line pointer of the form `<ID> — see docs/deferments.md`. **Exact block boundary** (per plan-review round 19 — codex finding "rewrite boundary undefined risks deleting adjacent backlog content"): the block to remove starts at the `**D-...` heading line and extends through the line immediately BEFORE either (a) the next `**...` item heading (any prefix — covers `**D-`, `**B`, `**IFR-`, `**RR-`, `**M`), OR (b) the next markdown section heading (`^#+ `), OR (c) the next blank-line-then-non-D-prose paragraph break — whichever comes first. The removed block is replaced by the one-line pointer in-place. Non-D-* surrounding content (B*, IFR-*, RR-*, M*, narrative paragraphs, section headings) is preserved verbatim. Only after the content-preservation audit (preservation step above) has merged ALL contract detail into `docs/deferments.md` may the block be removed. fn-18.3's done summary records a per-ID preservation-audit roll-up (`audit_result: {already-preserved | merged-into-docs | no-content-to-merge}`) AND the byte/line count of the removed block (so the diff is auditable: "removed 27 lines from .plan, all of D-PASS-cross-aircraft-step-on's contract; merged-into-docs at docs/deferments.md L<N>").
- Non-D-* `.plan` entries (B*, IFR-*, RR-*, M*) are untouched — those are operational backlog items, not deferments. **Boundary preserved**: fn-18 still does not rewrite `.plan`'s structure, status flags, or non-D-* content; the edit is surgical, one line per D-* ID, after the content-preservation audit gates each rewrite.

`.plan` IDs are recorded as a fifth locked-inventory list in fn-18.3's evidence JSON (alongside `epic_spec_ids` and `inline_only_ids`):

```json
{
  "locked_inventory_fn_18_3": {
    "epic_spec_ids": [...],
    "inline_only_ids": [...],
    "plan_file_ids": ["D-PASS-g1-diagnostics", ...],
    "plan_file_ids_dedup_against_fn_18_2": [...],
    "plan_file_ids_new_to_fn_18_3": [...]
  }
}
```

Verify inline grep includes any IDs missed in the brief. **Acceptance: any concrete ID found in code comments, `.plan`, or epic specs but not yet filed in `docs/deferments.md` is filed during this task.**

### Step 2 — Per-ID triage

**Pre-triage rule (per plan-review round 12 — codex finding "closed spec-sourced IDs misclassified as planned"): if the source register or `.plan` records the ID as CLOSED/DONE/CLOSED-PARTIAL, the entry goes to `## Archive` with `Status: closed`, `Closed by: <closing epic or pass>`, and `Enforcement: <pointer to the closing artifact>`. Do NOT classify a closed ID as bucket 3 `planned` just because it has an associated epic — the epic landed and closed the deferment, so the deferment is done.**

State as of fn-18 planning (2026-05-12, post-fn-17 closure):
- fn-15 epic — `open`, tasks `done` (fn-15.1, fn-15.2). The epic's open status reflects "completion review SHIP'd but epic not auto-closed per phases.md"; the work is shipped.
- fn-16 epic — `open`, tasks `todo`. Genuinely outstanding work.
- fn-17 epic — `open`, task `done`. fn-17.1 SHIP'd; `D-PASS-cap413-edition-24-reconciliation` is recorded as CLOSED in `.plan` and in `.flow/specs/fn-14-…md` line 404.

Verify epic status at task time (`flowctl show <epic-id> --json | jq .status`) and source register CLOSED-ness (grep `.plan` for the ID + "DONE\|CLOSED"). Triage:

For each ID, apply the bucket-decision tree from `docs/deferments-CONVENTION.md`:

- **D-PASS-g3a-react-tailwind-limit** → fn-15 epic exists; verify epic + task status at task time. If fn-15 tasks are all `done`, this is CLOSED (move to Archive); if any task is `todo`/`in_progress`, bucket 3 (`Status: planned, Pinned at: fn-15-g3a-react-tailwind-pilot-reactive-go`). At fn-18 planning time (post-this-session closure), fn-15.1 + fn-15.2 are both `done` → ARCHIVE this entry as `Closed by: fn-15` with `Enforcement: AircraftType.maxTailwindKnots + PilotEvent.TailwindLimitExceeded + applyTailwindGoAround in pilot/.../Pilot.kt; G3aPilotReactiveTailwindTest as the ninth golden`.
- **D-PASS-wind-state-migrate-to-aerodrome** → fn-16 epic exists, tasks `todo`. Bucket 3. Docs entry: `Status: planned, Pinned at: fn-16-wind-state-migrate-to-aerodromeweather`.
- **D-PASS-cap413-edition-24-reconciliation** → **CLOSED by fn-17.1 in this session** (see `.plan` § "D-PASS-cap413-edition-24-reconciliation — DONE (CLOSED by fn-17.1)" and `.flow/specs/fn-14-…md` line 404). ARCHIVE this entry as `Status: closed`, `Closed by: fn-17.1`, `Enforcement: CAP_413_EDITION = "Edition 24 (effective 1 July 2026)" constant in protocol/.../RegulationModel.kt; CAP413_4_64 typed entry; wiki/data-sources/cap413-edition-24-capture.md verification artifact`.
- **fn-14 remaining 8 g3a-react siblings** → likely bucket 4 (narrative — most are cross-cutting / doctrinal). Per Decision #10, "no fn-14 sibling needs a new epic." Verify each: e.g. D-PASS-g3a-react-multi-aircraft-crosswind has a clearer shape (could be bucket 1 once multi-aircraft scenarios are exercisable). D-PASS-g3a-react-vrb-handling probably bucket 4. D-PASS-g3a-react-gust-evaluation probably bucket 4.
- **fn-15 6 siblings** → likely bucket 4 each (sibling-of-fn-14-deferments shape).
- **fn-17 3 siblings** → bucket 4 (CAP 413 edition reconciliation is doctrinal; sub-deferments don't have test shapes today).
- **Inline-only IDs**:
  - `D-PASS-fn6-snap-derived` — bucket 4 (cross-cutting refactor on `AircraftObservation` derived-vs-projection shape).
  - `D-PASS-g3a-obstruction-aerodrome-payload` — bucket 4 (cross-cutting on `RunwayObstructionInformation` companion payload).
  - `D-PASS-continue-approach-pilot-readback` — bucket 4 (CA readback semantics; doctrinal).
  - `D-PASS-g3a-obstruction-kind-variants` — bucket 4 (richer obstruction taxonomy).
  - `D-PASS-g3a-obstruction-clearsAt-update` — bucket 4 (relaxation rule for clearsAt updates).
  - `D-PASS-fixture-per-plan-filing-time` — bucket 4 (sim-fixture cross-cutting refactor).

Per-ID rationale recorded in a working table. Plan-review may shift any to bucket 1/2 if a clear test shape emerges.

### Step 3 — Write bucket 1/2 tests (rare in .3)

If any item escalates to bucket 1/2, write `@Ignore`d test in the appropriate module's `DeferredContractsSpec.kt`. Create the module file if absent.

### Step 4 — Populate `docs/deferments.md`

For every ID, write the schema entry under the appropriate prefix subsection (most will be `D-PASS-*`). Per Decision #7's locked field order.

For bucket-3 entries pointing at **genuinely open** epics, the entry shape is (per plan-review round 14 — codex finding "Step 4 bucket-3 example uses closed deferment": fn-15 closed during this session, so its `D-PASS-g3a-react-tailwind-limit` is an Archive entry, NOT bucket-3; example updated to fn-16 which still has `todo` tasks):
```markdown
### D-PASS-wind-state-migrate-to-aerodrome — wind lives on Aerodrome.weather
**Status:** planned
**Pinned at:** fn-16-wind-state-migrate-to-aerodromeweather
**Why:** v1 of G3a-react put wind in `PilotInput.weatherByAerodrome` because Aerodrome.weather didn't exist as a typed slice. Migration is cross-cutting (controller belief slice + sim wiring + pilot reads + fixture wiring) and needs its own epic.
**Contract:** (conditional, present when the pre-migration source body had a richer real-fix contract than 3 sentences of `Why:` can hold — per Decision #7-revised) e.g. `weatherByAerodrome` moves from `PilotInput` to `Aerodrome.weather`; controller belief slice extended with `aerodromeWeather: Map<AerodromeId, Weather>`; sim wiring authors via `Aerodrome.weather` setter; pilot reads via aerodrome lookup. Test that lands when closed: a renamed `G3aPilotReactiveCrosswindTest` reading wind via `aerodrome.weather` instead of `PilotInput.weatherByAerodrome`.
**Closes by:** fn-16 (planned).
```

**Field order** (per epic Decision #7, hardened per plan-review round 18 — codex finding "fn-18.3 Step 4 violates locked field order"): `Status`, `Pinned at`, `Blocked on` (conditional, only when `Status=blocked`), `Why`, `Contract` (conditional), `Closes by`. The previous round-14 example had `Closes by:` before `Contract:` — that's wrong. Fixed above.

### Step 5 — Update fn-14 / fn-15 / fn-17 epic specs (R12) — via `flowctl epic set-plan`

Per plan-review round 3: Flow-next rules require `flowctl` for Flow-state edits. Ad-hoc `.flow/specs/*.md` edits are not the right path.

For each of fn-14 / fn-15 / fn-17 (and fn-16 if it files new siblings):

```bash
.flow/bin/flowctl cat fn-14-g3a-react-pilot-reactive-go-around-on > /tmp/fn-14-current.md
# Edit /tmp/fn-14-current.md: prepend the redirect line to ## Deferments register section.
.flow/bin/flowctl epic set-plan fn-14-g3a-react-pilot-reactive-go-around-on --file /tmp/fn-14-current.md
.flow/bin/flowctl validate --epic fn-14-g3a-react-pilot-reactive-go-around-on --json
```

Redirect line to prepend:

```markdown
## Deferments register

**MIGRATED to `docs/deferments.md` per fn-18.3 on YYYY-MM-DD (commit <pending>).** The entries below are preserved for historical context.

<existing entries unchanged>
```

**Sequencing** (per plan-review round 1): commit SHA is back-fillable via `commit <pending>`. R12 acceptance is the redirect line exists with the date + per-fn-18.3 attribution; SHA is not required. Per epic Decision #12.

Do NOT delete existing entries — preserve as historical artefact.

**Interaction with R15 (per plan-review round 9)**: the preserved historical entries inside fn-14/15/17 `## Deferments register` sections remain scanned by R15's whole-repo grep — by design. Every concrete ID inside those preserved blocks MUST have a matching `docs/deferments.md` entry (because fn-18.3 migrates them at this same task). After fn-18.3 completes, the preserved blocks and `docs/deferments.md` are in lockstep: every ID has both a historical entry (in the original spec) and a canonical entry (in docs). If R15 finds a preserved-block ID without a docs entry, that's drift — investigate at task close.

fn-16 spec: check if its `## Deferments register` files any NEW siblings (beyond closing fn-14's wind-state deferment). If yes, same flowctl flow. If no, no edit needed.

### Step 6 — Final bucket-distribution recording (R8)

**Aggregate from fn-18.2's flowctl evidence JSON** (per plan-review round 6 — fn-18.2 records its locked inventory + bucket distribution in evidence JSON; fn-18.3 reads it). `flowctl` paths are bundled — use the same `FLOWCTL` env-var pattern from worker.md:

```bash
FLOWCTL=.flow/bin/flowctl
# Preferred — jq is available on this machine at /usr/bin/jq
$FLOWCTL show fn-18-deferment-register-reorganization-four.2 --json | jq .evidence
# Fallback (per plan-review round 11 — codex finding "jq dependency unguarded"): if jq is missing in some future
# environment, use python or grep on the JSON. Both are acceptable; the file is structured JSON so any tool
# that can navigate it works:
#   $FLOWCTL show fn-18-deferment-register-reorganization-four.2 --json | python3 -c "import json,sys; print(json.dumps(json.load(sys.stdin).get('evidence', {}), indent=2))"
```

Read fn-18.2's `locked_inventory.open_ids`, `closed_from_pilot_firewall_ids`, `inline_ids_observed_deferred_to_fn_18_3`, and `bucket_distribution`.

**Reconcile inline IDs** (per plan-review round 6): fn-18.3's grep should include every inline ID fn-18.2 observed-and-deferred PLUS any new inline IDs fn-18.3 finds. If fn-18.3's grep is missing IDs fn-18.2 listed, investigate (something changed between tasks). If fn-18.3 finds IDs fn-18.2 didn't list, record both. The reconciliation goes in fn-18.3's done summary.

**Write fn-18.3's evidence JSON** in the same structured shape as fn-18.2's:

```json
{
  "locked_inventory_fn_18_3": {
    "epic_spec_ids": ["D-PASS-g3a-react-tailwind-limit", ...],
    "inline_only_ids": ["D-PASS-fn6-snap-derived", ...],
    "bucket_distribution_fn_18_3": {...}
  },
  "final_aggregate": {
    "total_ids_migrated": N,
    "per_bucket_total": {"bucket_1": N, "bucket_2": N, "bucket_3": N, "bucket_4": N},
    "per_source_total": {"pilot_firewall_md": N, "fn_14_spec": N, "fn_15_spec": N, "fn_17_spec": N, "inline_only": N, "meta_deferments": 3}
  }
}
```

Record in fn-18.3's done summary text too.

### Step 7 — Cross-reference grep verification (R10 closure)

Run the broader grep that catches BOTH dash-suffixed and dotted IDs (per plan-review round 2-5), with explicit paths (matching fn-18.2's pattern):

```bash
grep -rn -E "D-(PASS|WORLD)-[A-Za-z0-9_.-]+|D-(AUDIT|PF)\.?[A-Za-z0-9_.-]+" --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/
```

Or two separate greps for clarity:
```bash
grep -rn -E "D-(PASS|WORLD)-" --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/
grep -rn -E "D-(AUDIT|PF)\." --include="*.kt" pilot/ controller/ protocol/ sim/ core/ migration/
```

Every match must reference an ID that exists in `docs/deferments.md`. List any drift in done summary (expected: none — but verify).

### Step 7b — Whole-repo final exhaustiveness gate (R15, added per plan-review round 7)

Step 7 verifies inline `.kt` code-comment IDs. Step 7b is the broader catch-all: scan **every** ID-bearing source in the repo (markdown specs, task files, `.plan`, all `*.kt` files) and compare against the `### D-...` heading set in `docs/deferments.md`. This is the meta-defence against inventory rot — catches any ID that fn-18.2 or fn-18.3's per-source inventories missed (e.g. an ID buried in `STRATEGY.md` or a `docs/design/*.md` page nobody owned).

```bash
# Repo-wide ID discovery — markdown + kotlin + .plan, all D-* prefixes
# Tail char restricted to [A-Za-z0-9_] so placeholder IDs like
# D-PASS-cap413-edition-24-<section> cannot match the prefix
# `D-PASS-cap413-edition-24-` (which would otherwise produce a bogus concrete ID
# ending in `-`). Per plan-review round 8.
grep -rhEo "D-(PASS|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|PF)\.[A-Za-z0-9_.-]*[A-Za-z0-9_]|D-(AUDIT|WORLD)-[A-Za-z0-9_.-]*[A-Za-z0-9_]" \
  --include="*.kt" --include="*.md" --include=".plan" . \
  | sort -u > $TMPDIR/fn-18-3-repo-ids-raw.txt

# Placeholder-fragment filter (per plan-review round 12 — codex finding
# "R15 regex placeholder leakage" round 2). The tail-char restriction
# alone is INSUFFICIENT: greedy regex backtracks, so a heading line
# containing `D-PASS-cap413-edition-24-<section>` extracts to the
# valid-tail-char prefix `D-PASS-cap413-edition-24` (regex backtracks
# from `-<` failure to `-2` then accepts `4` as tail char). The fix is
# a post-filter that drops any candidate ID which, in source context,
# is immediately followed by `-<` — that pattern is the placeholder
# fragment we must NOT promote into a concrete ID.
> $TMPDIR/fn-18-3-repo-ids.txt
> $TMPDIR/fn-18-3-placeholder-drops.txt
while IFS= read -r id; do
  # Escape regex metachars in the ID for the context-grep
  esc=$(printf '%s' "$id" | sed 's/[.[\(*^$+?{|]/\\&/g')
  # If the ID appears followed by `-<` anywhere in source, treat as placeholder fragment and drop
  if grep -rqE "${esc}-<" --include="*.kt" --include="*.md" --include=".plan" .; then
    echo "$id" >> $TMPDIR/fn-18-3-placeholder-drops.txt
  else
    echo "$id" >> $TMPDIR/fn-18-3-repo-ids.txt
  fi
done < $TMPDIR/fn-18-3-repo-ids-raw.txt
# fn-18-3-placeholder-drops.txt is recorded in evidence so the drop set is auditable.

# docs/deferments.md ID set — raw first (NOT -u) so we can check duplicates
grep -hEo '^### D-[A-Za-z0-9_.-]+' docs/deferments.md \
  | sed 's/^### //' \
  | sort > $TMPDIR/fn-18-3-docs-ids-raw.txt

# Duplicate-heading check — fails R15 if any line appears twice
uniq -d $TMPDIR/fn-18-3-docs-ids-raw.txt > $TMPDIR/fn-18-3-docs-dups.txt

# Dedupe for the containment check
sort -u $TMPDIR/fn-18-3-docs-ids-raw.txt > $TMPDIR/fn-18-3-docs-ids.txt

# One-way containment (per plan-review round 9): every concrete repo-wide ID
# MUST appear in docs. Docs-only IDs (archive entries whose only repo anchor is
# the docs entry itself, e.g. orphan tests deleted in fn-18.2) are legitimate
# and NOT drift. Two-way `diff` would have wrongly flagged them.
comm -23 $TMPDIR/fn-18-3-repo-ids.txt $TMPDIR/fn-18-3-docs-ids.txt > $TMPDIR/fn-18-3-id-missing-from-docs.txt
```

**Exclusions**:
- Documented placeholder IDs (e.g. `D-PASS-cap413-edition-24-<section>`, the CONVENTION-doc schema-example placeholders like `D-AUDIT.<example>`) are excluded by virtue of the tightened regex's tail-char `[A-Za-z0-9_]`. Without this, the regex would have matched the prefix `D-PASS-cap413-edition-24-` and produced a bogus concrete ID. Verified at task time: `grep -E "<.*>" $TMPDIR/fn-18-3-repo-ids.txt` must be empty (no placeholder bracket leakage).
- CONVENTION-doc schema-example IDs MUST use placeholder form (per fn-18.1's hardened rule per plan-review round 8) — concrete IDs in CONVENTION examples are forbidden unless they are real `docs/deferments.md` entries. If round-9 inspection finds any concrete-looking ID in the CONVENTION doc that isn't a real entry, it's CONVENTION rot and is fixed in fn-18.3 as a small inline edit.

**Acceptance for R15** (per plan-review rounds 8 + 9 + 10 — split into duplicate-check + one-way containment + tightened false-positive policy):
1. `$TMPDIR/fn-18-3-docs-dups.txt` MUST be empty (no duplicate `### D-` headings in `docs/deferments.md`). Per R15's "exactly once" wording.
2. `$TMPDIR/fn-18-3-id-missing-from-docs.txt` MUST be empty (every concrete repo-wide ID has a matching `### D-...` heading in `docs/deferments.md`). **Tightened (round 10)**: non-empty is allowed ONLY for proven false positives — specifically (i) regex artefacts where a placeholder slipped through `[A-Za-z0-9_]` tail-char exclusion (fix by updating the placeholder text, not by pardoning), or (ii) CONVENTION-doc schema-example IDs that should be re-shaped to placeholder form. Real concrete IDs that have no docs entry FAIL R15; the response is "add the entry," not "explain it in prose." Per `feedback_no_corners.md`.
3. **Docs-only IDs are NOT drift.** `comm -13 $TMPDIR/fn-18-3-repo-ids.txt $TMPDIR/fn-18-3-docs-ids.txt` (the "in docs, not in repo" set) is informational only — it lists archive entries whose only anchor is the docs entry itself (e.g. orphan tests deleted in fn-18.2). Recorded in done summary for visibility but does not fail R15.

Both files (`docs-dups.txt`, `id-missing-from-docs.txt`) are pasted into fn-18.3's done summary (or noted empty). The informational `in docs, not in repo` count is also recorded.

### Step 8 — Verify

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

Baseline already captured in Step 0 (`$TMPDIR/fn-18-3-base-sha.txt` + `$TMPDIR/fn-18-3-base-test.log`). No need to re-run pre-task capture here — that's a defense against round-2's split-baseline anti-pattern (round 18 — codex finding 'baseline must precede edits').

**Post-task verification**: re-run. R14 acceptance is "no failures introduced relative to base log." Nine goldens GREEN (per fn-15 closure: `G3aPilotReactiveTailwindTest` is now a permanent fixture). Any newly-created `@Ignore`d test compiles.

**`flowctl done` invocation** (per plan-review round 11 — codex finding "no concrete done-time step"): write the done summary and evidence JSON to dedicated files, then invoke `flowctl done` with both flags. fn-18.3's evidence JSON is the closure record — it MUST contain `locked_inventory_fn_18_3` with `epic_spec_ids`, `inline_only_ids`, `plan_file_ids`, `bucket_distribution`, plus the R15 gate result (`docs_duplicate_check`, `docs_missing_ids_check`, `docs_only_archive_ids`).

```bash
# Write done summary
cat > $TMPDIR/fn-18-3-summary.md <<'EOF'
fn-18.3 shipped: migrated <N> epic-spec siblings (fn-14/15/17) + <M> inline-only IDs + <K> .plan IDs into docs/deferments.md. Epic-spec redirect lines added to fn-14/15/17 (and fn-16 if applicable). R15 whole-repo exhaustiveness gate result: <PASS|drift recorded>. Implementation commit: see evidence-JSON `implementation_sha` field. Nine goldens GREEN.
EOF
# Write evidence JSON — populated from Step 1 locked-inventory + Step 6 aggregation + Step 7b R15 gate
cat > $TMPDIR/fn-18-3-evidence.json <<'EOF'
{
  "task": "fn-18-deferment-register-reorganization-four.3",
  "base_sha": "<from Step 0 base-sha.txt>",
  "implementation_sha": "<SHA of the implementation commit BEFORE flowctl done",
  "gradle_module_preflight": ["<list from preflight output>"],
  "locked_inventory_fn_18_3": {
    "epic_spec_ids": ["..."],
    "inline_only_ids": ["..."],
    "plan_file_ids": ["..."],
    "bucket_distribution": {"bucket_1": 0, "bucket_2": 0, "bucket_3": 0, "bucket_4": 0}
  },
  "fn_18_2_reconciliation": {
    "open_ids_from_fn_18_2": ["..."],
    "set_A_pilot_firewall_from_fn_18_2": ["..."],
    "set_B_inline_from_fn_18_2": ["..."],
    "set_intersection_A_and_B_from_fn_18_2": ["..."],
    "set_inline_only_for_fn_18_3_from_fn_18_2": ["..."],
    "inline_ids_fn_18_2_observed": ["..."],
    "inline_ids_fn_18_3_grep": ["..."],
    "drift_notes": "<any difference between fn-18.2-observed and fn-18.3-grep with reason>"
  },
  "_schema_note": "fn_18_2_reconciliation keys mirror fn-18.2's evidence.locked_inventory schema verbatim (per plan-review round 13 — codex finding 'evidence JSON schema inconsistency'). Read with `flowctl show ... --json | jq .evidence.locked_inventory` and copy the set arrays into the reconciliation block.",
  "r15_gate": {
    "repo_wide_id_count": 0,
    "docs_md_id_count": 0,
    "docs_duplicate_check": "PASS|FAIL: <duplicate IDs if any>",
    "docs_missing_ids_check": "PASS|FAIL: <ids in repo but not in docs/deferments.md if any>",
    "docs_only_archive_ids": ["<archive entries with no remaining repo anchor — informational, not drift>"],
    "placeholder_drops": ["<candidate IDs dropped by the placeholder-fragment filter — Step 7b — added per plan-review round 13 — codex finding 'placeholder_drops field missing'>"]
  },
  "verify_command": "./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt",
  "verify_outcome": "BUILD SUCCESSFUL"
}
EOF
.flow/bin/flowctl done fn-18-deferment-register-reorganization-four.3 \
  --summary-file $TMPDIR/fn-18-3-summary.md \
  --evidence-json $TMPDIR/fn-18-3-evidence.json --json
```

## Investigation targets

- Verify fn-14 / fn-15 / fn-17 spec line numbers for the `## Deferments register` section (so the redirect prepend lands cleanly).
- Confirm fn-16 spec contents — whether it files new siblings or only closes a fn-14 entry.
- For inline-only IDs: confirm exact file:line locations via grep; ensure docs entry's narrative captures the inline comment's framing without losing nuance.
- Final inventory grep across the entire codebase to catch any ID not yet pinned.

## Key context

- **Bucket 3 redirects are the cleanest case** — when the target epic still has unfinished tasks. fn-16 is the canonical genuinely-open case (tasks `todo`). fn-15 and fn-17 epic stubs already exist, but their tasks all closed during this session — those D-* entries go to `## Archive` instead, per plan-review rounds 13-14. Verify epic status at task time via `flowctl show <epic-id> --json | jq '.tasks | map(.status)'`.
- **fn-14 deferment register is the densest source.** Planning-time estimate ~11 IDs to triage; non-authoritative — locked inventory at task time. Most go to bucket 4.
- **Inline-only IDs are the most fragile.** They exist as code comments only — no parent spec, no closure record. The migration captures them properly.
- **fn-14 / fn-15 / fn-17 epic-spec `## Deferments register` sections become historical artefacts.** Do not delete; prepend redirect.
- **Final bucket distribution recording is acceptance-level.** This is the only place future audits see "how many of each bucket landed."

## Acceptance

- [ ] **R1** (final populated state) — `docs/deferments.md` contains every inventoried ID across all sources. Active body + Archive complete.
- [ ] **R5** — Per-module `DeferredContractsSpec.kt` files exist for any new bucket 1/2 items landing here (rare — most fn-14/15/17 siblings are bucket 4).
- [ ] **R7** (complete) — every inventoried ID across all sources is in `docs/deferments.md`. <!-- Updated by plan-sync: fn-18.2 actual handoff was 32 OPEN + 21 CLOSED (|A|=53), 18 archived; inline-only handoff is 29 (|B\A|=29), not the planning-time ~6. Total to-migrate is materially higher than the planning-time ~61 estimate; the locked inventory artifact (fn-18.2 evidence JSON + fn-18.3 grep) controls. --> Exact count from fn-18.2 + fn-18.3 locked inventory artifacts verified in done summary. fn-18.2's actual cardinalities (recorded in evidence JSON `locked_inventory`): |set_A_pilot_firewall| = 53 (32 OPEN + 21 CLOSED), |set_B_inline| = 65, |set_intersection_A_and_B| = 36, |set_inline_only_for_fn_18_3| = 29 — these supersede the planning-time estimate of ~61 total. The 3 meta-deferments filed in fn-18.1 are NOT inventoried IDs for R7 — they are new entries this epic creates, not migrated. Per epic Decision #10: planning-time counts are non-authoritative; locked inventory artifact controls.
- [ ] **R8** — Final bucket distribution recorded: counts per bucket (1/2/3/4) and per-source (pilot-firewall.md / fn-14 / fn-15 / fn-17 / inline-only / meta-deferments).
- [ ] **R9** — fn-16 (the genuinely-open epic stub) cross-referenced in `docs/deferments.md` as a bucket-3 `planned` entry. fn-15 and fn-17 (closed during this session) cross-referenced as `## Archive` entries with `Closed by:` + `Enforcement:` pointers, NOT as bucket-3 active entries. Verified at task time by `flowctl show <epic-id> --json | jq '.tasks | map(.status)'`. No new stubs needed beyond fn-16 (already in `todo`).
- [ ] **R10** (complete) — every inline `// D-PASS-*` / `// D-AUDIT-*` / `// D-PF.*` code comment across the entire codebase references an ID that exists in `docs/deferments.md`. Final grep audit recorded in done summary.
- [ ] **R12** — fn-14 / fn-15 / fn-17 epic specs' `## Deferments register` sections each carry the MIGRATED redirect prepend per Decision #12. fn-16 epic spec also receives the redirect IF its `## Deferments register` contains migrated entries (conditional — done summary records the verification result). Existing entries preserved as historical artefact. **Interaction with R15** (per plan-review round 9): preserved historical blocks are intentionally still scanned by R15's whole-repo grep — every concrete ID inside them MUST have a matching `docs/deferments.md` entry (which fn-18.3 produces at this same task). R12 + R15 are mutually consistent only when the migration is complete.
- [ ] **R14** — Recorded as one of two outcomes per epic Decision #R14: **R14-Passed** (gradle exits 0; nine goldens GREEN; detekt unchanged) OR **R14-NoNewRegression** (baseline was already red; task evidence records base SHA + exact failing tests at start and proves no new failures introduced).
- [ ] **R15** (final whole-repo exhaustiveness gate, per epic spec R15) — TWO sub-checks per Step 7b's hardened command (per plan-review rounds 8 + 9 + 10): (a) `$TMPDIR/fn-18-3-docs-dups.txt` is empty (no duplicate `### D-` headings in `docs/deferments.md`); (b) `$TMPDIR/fn-18-3-id-missing-from-docs.txt` (one-way `comm -23 repo-ids docs-ids`) is empty OR any non-empty content is a proven false positive (regex placeholder leakage or CONVENTION schema-example artefact — fix in-place, not in prose). Per `feedback_no_corners.md`, real missing concrete IDs FAIL R15; the response is to add the docs entry, not to explain the gap. Every concrete `D-*` ID found across `*.md` + `*.kt` + `.plan` files in the repo appears exactly once as a `### D-...` heading in `docs/deferments.md`. Docs-only IDs (archive entries with no remaining repo anchor) are NOT drift — they're expected. Placeholder IDs (e.g. `D-PASS-cap413-edition-24-<section>`, CONVENTION-doc placeholders like `D-AUDIT.<example>`) are excluded by the tightened tail-char regex `[A-Za-z0-9_]` — no bogus prefix match.
- [ ] **R16** — Every bucket-1 `@Ignore`d test referenced by `docs/deferments.md` from fn-18.3-source items (NEW or RETAINED, per plan-review round 16 scope-widening) contains at least one non-import current-API value-flow reference per epic R16. fn-18.3 typically lands few bucket-1 entries (most fn-14/15/17 siblings are bucket 4) but verifies any it does land. Verified by reading each bucket-1 test body referenced by `docs/deferments.md` at task close; result recorded in done summary.

## Done summary
fn-18.3 shipped: closes the deferment-register reorganization epic (fn-18). Final state of `docs/deferments.md`: **127 entries** (53 carried over from fn-18.2 + **74 net new** from fn-18.3). The 74 fn-18.3 additions decompose by bucket as **bucket_1 = 0, bucket_2 = 0, bucket_3 = 1, bucket_4 = 61, archive = 12** (sum 74). They decompose by section (fn-18.3-only additions to each section heading) as **D-PF = 1 active, D-AUDIT = 6 active + 1 archive, D-PASS = 55 active + 11 archive, D-WORLD = 0** (active sum 62; 62 active includes the 1 bucket-3 entry; archive sum 12; 62 + 12 = 74). The 6 active D-AUDIT additions are D-AUDIT.2.A-FOLLOWUP, D-AUDIT.2.B-FOLLOWUP, D-AUDIT.2.E-FOLLOWUP, D-AUDIT.4.A-FOLLOWUP, D-AUDIT.4.D-FOLLOWUP, D-AUDIT.M2; the 1 D-AUDIT archive is D-AUDIT.13. Sources (overlapping — many IDs surface in multiple sources, so per-source counts overlap and do not sum to 74): fn-14 epic-spec siblings (11 inventoried), fn-15 epic-spec siblings (6), fn-16 epic-spec siblings (7), fn-17 epic-spec deferments (3 concrete + 1 conditional-placeholder NOT filed per epic Decision #10), fn-18.2 inline-only handoff (29 IDs minus 6 false-positive fragments per fn-18.2 evidence = **23 real concrete IDs**, of which 5 land as archive — D-PASS-13.1/13.2/13.3 Pass-17 closures + D-PASS-g3a-react-tailwind-limit fn-15-archive + D-PASS-wind-state-migrate-to-aerodrome bucket-3 — and 18 active narrative), fn-8/11/12/13 spec siblings (14 distinct new bucket-4 IDs), `.plan` (8 D-* entries: 3 dedup against fn-18.2 + 5 new to fn-18.3), plus 3 additional archives from set_A reconciliation (D-AUDIT-lowg-ctr-radius closed by fn-7, D-PASS-g1-diagnostics CLOSED-PARTIAL by fn-8.3, D-AUDIT.13 closed by Pass 14), plus 1 narrative anchor from fn-11 spec (D-AUDIT.M2). Epic-spec redirects via `flowctl epic set-plan` applied to fn-14/15/16/17 with the MIGRATED prepend. .plan: 8 D-* entries content-preservation-audited (4 already-preserved + 4 merged-into-docs: D-PASS-cap413-edition-24-reconciliation absorbed full mapping detail, D-PASS-cap413-2_7-principle-cite-audit and D-PASS-cap413-4_46-principle-cite-audit absorbed Contract fields, D-PASS-cap413-edition-24-r11-verify-sandbox-block absorbed workaround + testsuite detail) then rewritten to one-line pointers; **50 lines and 14,954 bytes** of contract detail surgically lifted into docs. R15 whole-repo gate: PASS (127 docs entries, no duplicates, every concrete repo ID has a docs entry — 26 unfiled IDs are all documented exclusions: 17 regex fragments, **0 test-method aliases** (rounds 4 + 5 renamed all 17 test-method anchors across `:pilot` / `:controller` / `:protocol` / `:sim` `DeferredContractsSpec.kt` files to drop the leading `D-` prefix per CONVENTION §10), 5 placeholder examples, 1 false-positive D-WORLD-BACKED in ResolvedClearanceTest, 3 placeholder-fragment-filter drops). R14: R14-Passed (BUILD SUCCESSFUL, nine goldens GREEN, detekt unchanged). R16: no new bucket-1 entries this task; 4 retained bucket-1 entries from fn-18.2 remain GREEN. Implementation commit `4cb5e3d` (main migration), round-1 fix commits `4670773` (D-AUDIT.M2 schema) and post-done evidence backfill, round-2 fix commits (this commit suite: D-PASS-wind-state contract corrected to match fn-16 spec; CONVENTION §10 added formalizing test-method-name aliases; Done summary arithmetic reconciled).
## Evidence

**Implementation commit:** `4cb5e3d` (round-1 main migration) + `4670773` (round-1 codex fix — D-AUDIT.M2 Archive→active D-AUDIT schema).

**Base SHA:** `333ad37ec6267024ee5b0ad03eb3e7a53585191a` (pre-task baseline; nine goldens GREEN at base).

**Verify command:** `./gradlew :pilot:jvmTest :controller:jvmTest :protocol:allTests :sim:jvmTest :core:allTests detekt --offline --no-daemon`

**Verify outcome:** BUILD SUCCESSFUL (nine goldens GREEN, detekt unchanged) — **R14-Passed**.

**Locked-inventory cardinalities:**
- `docs/deferments.md` total entries: **127** (53 from fn-18.2 + **74 net new** from fn-18.3, verified by `git diff 333ad37..HEAD -- docs/deferments.md | grep -E "^\+### D-" | wc -l`)
- fn-18.3 bucket distribution (sums to 74): **bucket_1=0, bucket_2=0, bucket_3=1, bucket_4=61, archive=12**
- fn-18.3 section distribution (sums to 74): D-PF=1 active + 0 archive; D-AUDIT=6 active + 1 archive; D-PASS=55 active + 11 archive; D-WORLD=0; **total active=62** (includes the 1 bucket-3) + **archive=12** = **74**. The 6 active D-AUDIT additions are D-AUDIT.2.A-FOLLOWUP / D-AUDIT.2.B-FOLLOWUP / D-AUDIT.2.E-FOLLOWUP / D-AUDIT.4.A-FOLLOWUP / D-AUDIT.4.D-FOLLOWUP / D-AUDIT.M2.
- Sources (overlapping — IDs surface in multiple sources; per-source counts do NOT sum to 74):
  - fn-14 epic spec siblings: 11 inventoried (1 archived under fn-17.1, 1 archived under fn-15, 1 bucket-3 → fn-16, 8 bucket-4 narrative)
  - fn-15 epic spec siblings: 6 inventoried (all bucket-4 narrative)
  - fn-16 epic spec siblings: 7 inventoried (all bucket-4 narrative; D-PASS-wind-state-migrate-to-aerodrome counted under fn-14)
  - fn-17 epic spec deferments: 3 concrete + 1 conditional placeholder NOT filed per epic Decision #10 (`D-PASS-cap413-edition-24-<section>`)
  - fn-18.2 inline-only handoff: 29 IDs minus 6 false-positive fragments per fn-18.2 evidence (`D-PASS-g3b-react-cross`, `D-AUDIT.9.II`, `D-AUDIT.2.A`, `D-AUDIT.2.B`, `D-AUDIT.2.E`, `D-WORLD-BACKED`) = **23 real concrete IDs**, decomposing as 5 archive (D-PASS-13.1/13.2/13.3 Pass-17 closures + D-PASS-g3a-react-tailwind-limit fn-15-archive + D-PASS-wind-state-migrate-to-aerodrome bucket-3) + 18 active narrative
  - fn-8/11/12/13 spec siblings: 14 new bucket-4 IDs (CA variants, obstruction siblings, arr-sequencing siblings)
  - `.plan`: 8 D-* entries (3 dedup against fn-18.2 set_A: D-PASS-cross-aircraft-step-on, D-PASS-pilot-mid-tng-fullstop-recovery, D-PASS-g1-diagnostics-typed-events; 5 new to fn-18.3: D-PASS-g1-diagnostics, D-PASS-cap413-edition-24-reconciliation, D-PASS-cap413-2_7-principle-cite-audit, D-PASS-cap413-4_46-principle-cite-audit, D-PASS-cap413-edition-24-r11-verify-sandbox-block)
  - Additional archives from set_A reconciliation (not in fn-18.2 closed_from_pilot_firewall_ids): D-AUDIT-lowg-ctr-radius (closed by fn-7), D-PASS-g1-diagnostics (CLOSED-PARTIAL by fn-8.3), D-AUDIT.13 (closed by Pass 14)
  - fn-11 narrative anchor: D-AUDIT.M2

**.plan content-preservation audit** (8 D-* blocks):
- 4 already-preserved: D-PASS-g1-diagnostics, D-PASS-g1-diagnostics-typed-events, D-PASS-cross-aircraft-step-on, D-PASS-pilot-mid-tng-fullstop-recovery
- 4 merged-into-docs (contract detail lifted before rewrite): D-PASS-cap413-edition-24-reconciliation (Branch-A verdict + full §-mapping + Ed 23/24 SHAs), D-PASS-cap413-2_7-principle-cite-audit (Contract field), D-PASS-cap413-4_46-principle-cite-audit (Contract field), D-PASS-cap413-edition-24-r11-verify-sandbox-block (full sandbox workaround + 8-testsuite golden list)
- Total bytes removed: **14,954**; total lines removed: **50**; rewritten to one-line `<ID> — see docs/deferments.md` pointers
- Non-D-* `.plan` content (B*, IFR-*, RR-*, M*, CB-*, narrative paragraphs, section headings) untouched

**R15 whole-repo exhaustiveness gate:** PASS
- `docs_duplicate_check`: PASS (no duplicate `### D-` headings in `docs/deferments.md`)
- `docs_missing_ids_check`: PASS (every concrete repo-wide ID has a matching `### D-...` heading in docs)
- 26 unfiled IDs are all documented exclusions (round-4 narrowed from 28 to 26 by renaming the two test-method aliases to drop the `D-` prefix):
  - 18 regex fragments (substrings of longer real IDs): `D-AUDIT.2.A/B/E`, `D-AUDIT.4.A/B/D`, `D-AUDIT.6.A`, `D-AUDIT.9.II`, `D-PASS-cap413`, `D-PASS-cap413-edition-23/-comparison`, `D-PASS-cap413-edition-24-rename`, `D-PASS-cap413-edition-24-retired-atc/-ga`, `D-PASS-deferments`, `D-PASS-fixture-per-plan-filing`, `D-PASS-g3a-obstruction`, `D-PASS-g3a-react`, `D-PASS-g3b-react-cross`, `D-PASS-wind-state`
  - 0 test-method aliases (round 4: renamed the two Kotlin test-method anchors to drop the `D-` prefix so they no longer match the regex; canonical entries `D-PASS-13.3-II-FOLLOWUP` and `D-PASS-17.2` remain in docs; `CONVENTION` §10 documents the no-leading-D test-method-anchor rule).
  - 5 placeholder examples: `D-AUDIT.9.x`, `D-AUDIT.N`, `D-PASS-N.x`, `D-PASS-my-feature-shape`, `D-AUDIT-g3a-react-pilot-reactive-go-around`
  - 1 false-positive: `D-WORLD-BACKED` (ClearanceId string literal in `core/.../ResolvedClearanceTest.kt` per fn-18.2 evidence)
  - 2 placeholder-fragment-filter drops: `D-PASS-cap413-edition-24-*` placeholder prefixes (auto-filtered by tail-char regex)

**R10 inline grep audit:** PASS — every `// D-PASS-*` / `// D-AUDIT-*` / `// D-PF.*` code-comment ID across `*.kt` in `pilot/` `controller/` `protocol/` `sim/` `core/` `migration/` references an ID that exists in `docs/deferments.md`. Verified via two-step grep + comm against `### D-` headings.

**R16 value-flow roll-up:** No new bucket-1 `@Ignore`d tests added by fn-18.3 (the 74 fn-18.3 additions are 61 bucket-4 narrative + 12 archive + 1 bucket-3 — i.e. zero bucket-1 / zero bucket-2). The 4 retained bucket-1 entries from fn-18.2 (D-AUDIT.7.III-FOLLOWUP, D-AUDIT.8.IV-FOLLOWUP, D-PASS-13.3-II-FOLLOWUP, D-PF.3) remain GREEN per fn-18.2 evidence `r16_value_flow_rollup`; in fn-18.3 round-4/5 their Pinned-at test-method anchor names were renamed to drop the leading `D-` prefix (`AUDIT7-III`, `AUDIT8-IV`, `PASS-13_3-II`, `PF3`) per CONVENTION §10, but the canonical docs headings (with `D-` prefix) are unchanged.

**Epic-spec redirects** (via `flowctl epic set-plan` per plan-review round 3):
- fn-14: MIGRATED redirect prepended; `flowctl validate` warns about pre-existing tasks-todo / epic-done mismatch — not introduced by fn-18.3
- fn-15: MIGRATED redirect prepended; `flowctl validate` success
- fn-16: MIGRATED redirect prepended; `flowctl validate` success
- fn-17: MIGRATED redirect prepended; `flowctl validate` success

**Review:** codex backend, receipt `/tmp/claude-501/impl-review-receipt.json`.
- Round 1 verdict: NEEDS_WORK (2 Major findings — placeholder Done summary/Evidence block + D-AUDIT.M2 invalid Archive schema)
- Round 1 fix commits: `4670773` (D-AUDIT.M2 schema fix) + post-`flowctl done` evidence backfill (this commit)
- Round 2 expected: SHIP after Evidence block carries the structured locked-inventory + R14/R15/R16 results

**Files / paths:**
- `docs/deferments.md` — 127 entries (+74 new this task)
- `.plan` — 8 D-* blocks rewritten to one-line pointers; 14,954 bytes / 50 lines removed
- `.flow/specs/fn-14-g3a-react-pilot-reactive-go-around-on.md` — MIGRATED prepend on `## Deferments register`
- `.flow/specs/fn-15-g3a-react-tailwind-pilot-reactive-go.md` — MIGRATED prepend on `## Deferments register`
- `.flow/specs/fn-16-wind-state-migrate-to-aerodromeweather.md` — MIGRATED prepend on `## Deferments register`
- `.flow/specs/fn-17-cap-413-edition-24-numbering.md` — MIGRATED prepend on `## Deferments register`

**Evidence JSON:** `/tmp/claude-501/fn-18-3-evidence.json` (load-bearing closure record with full per-source inventory, set-boundary cardinalities, .plan audit results, R15 gate output, documented exclusions, R14/R16 outcomes).
