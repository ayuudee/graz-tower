---
satisfies: [R9, R10, R11, R12, R13]
---

## Description

Sweep + paper-trail pass for fn-16. After fn-16.1 lands the atomic field migration, this task:
1. Re-runs the audit script to confirm no orphan `SimState.weatherByAerodrome` references remain across the entire codebase (including test fixtures, KDoc, comments).
2. Lands the KDoc updates listed in the epic spec § R10 — eight sites, mostly cross-references that previously cited `D-PASS-wind-state-migrate-to-aerodrome` as a deferment (now closed).
3. Updates the existing `project_rich_world_domain.md` memory entry **in place** to confirm both `Runway.obstruction` (fn-12) and `Aerodrome.weather` (fn-16) live on entities, so future world-state slices follow the same shape automatically. **No new memory file is created.**
4. Optionally renames the `SimState.initial(weatherByAerodrome = ...)` parameter — default decision = **keep the name** since it's a parameter, not a field; renaming adds churn without architectural value. Pin the decision in this task's evidence note.

This task is **L → S** in scope vs fn-16.1. **Documentation and external-memory/register only; no behavioral code changes** (refined per codex round 7 — production source files DO get KDoc/comment-touch edits, and external files like `~/.claude/plans/pilot-firewall.md` and `~/.claude/projects/.../memory/project_rich_world_domain.md` get content updates too, but no code paths change in the repo). The build green-bar is a sanity check that the documentation edits didn't accidentally touch a code path.

**Files:**
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt` — verify `Aerodrome.weather` KDoc landed cleanly in fn-16.1 (no additional edit needed beyond fn-16.1's R2 work; cross-reference verification only).
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldLenses.kt` — verify KDoc landed (fn-16.1 R3).
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WeatherObservation.kt` — verify KDoc landed (fn-16.1 R1).
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt` — sweep the class-level KDoc block (lines 19-36) for any remaining `weatherByAerodrome` field references; remove. Update the `SimState.initial` KDoc to add a one-line note about the fold (if not already done in fn-16.1).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotInput.kt:71-72` — the fn-14 KDoc note "The eventual `Aerodrome.weather` rich-domain migration is filed as `D-PASS-wind-state-migrate-to-aerodrome`" updates to "Migrated to `Aerodrome.weather` in fn-16 (deferment closed)."
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt` — verify the old `WeatherObservation` KDoc was relocated to `core/world/WeatherObservation.kt` (per fn-16.1 R1); no residual KDoc fragment remains in `ControllerTypes.kt`. If the fn-16.1 work left a stray KDoc orphan referring to a non-existent symbol, clean it. Otherwise no-op.
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/PilotWiring.kt:36-42` — KDoc update from fn-16.1's R7a may already have landed; verify the "fn-14.1: project just the WindReport slice" block now also notes fn-16's source change (or replace the fn-14.1 ref with fn-16 attribution since the new wiring is the fn-16 shape).
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/ControllerWiring.kt` — verify the `state.world.aerodromes[id]?.weather` call site has a comment noting the source (one-line cite to fn-16 if missing).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt:186-211` — KDoc update for `weatherTransitions` extractor: change the "transitions of [SimState.weatherByAerodrome]" preamble to "transitions of `world.aerodromes[id].weather`" + cite fn-16 alongside the existing fn-14.2 cite.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveCrosswindTest.kt:789-802` — KDoc on `authorWeather` updated to cite `AviationWorld.updateAerodrome` lens (this may already have landed in fn-16.1; verify and add if missing).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveCrosswindTest.kt:106-122` — file-level KDoc references "`state.weatherByAerodrome[LOWG]` mutation"; update to "`world.aerodromes[LOWG].weather` mutation via `updateAerodrome` lens".
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt:57` — provenance KDoc references "`state.weatherByAerodrome[LOWG]` mutation"; update to "`world.aerodromes[LOWG].weather` mutation".
- Other tests' file-level docstrings that reference `state.weatherByAerodrome` — grep `state.weatherByAerodrome` and update each hit. Likely candidates: `LowgGoldenTest.kt:81`, `G2CrossAerodromeVfrTest.kt:80`, `G3aRunwayObstructionTest.kt:99`, `G3aRunwayObstructionContinueApproachTest.kt:119`, `G3aPilotTrainedGoAroundTest.kt:92`.
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/project_rich_world_domain.md` — **EXISTING** memory file updated **in place** (per codex round 2 closure: NO new memory entry file is created; the principle and its precedent list live in one entry).

## Approach

### Step 1: confirm fn-16.1 acceptance (R9 closure)
Re-run the audit greps from the epic's "Quick commands" section:
```bash
grep -rn "\.weatherByAerodrome\b" --include="*.kt" .   # Any direct field access (should be zero non-param hits)
grep -rn "weatherByAerodrome" --include="*.kt" .        # All remaining (param names + PilotInput field + SimTraceQueries name)
grep -rn "D-PASS-wind-state-migrate-to-aerodrome" --include="*.kt" --include="*.md" .  # Deferment refs to close
```

Categorise every remaining hit into the allowed list from the epic R9. Any uncategorised hit is a regression — root-cause and fix in fn-16.1 (this task is paper-trail only; don't add new code in fn-16.2 that should have been in fn-16.1).

### Step 2: KDoc updates (R10)

Edit each site enumerated above. Pattern:
- Replace any `D-PASS-wind-state-migrate-to-aerodrome` reference with "Closed in fn-16."
- Replace `state.weatherByAerodrome[X]` text in KDoc / inline comments with `world.aerodromes[X].weather`.
- Replace `SimState.weatherByAerodrome` text in KDoc with either the lens (for writers) or the new walk (for readers).
- Cross-reference fn-16 (this epic) at the relocation sites (`WeatherObservation` move, `Aerodrome.weather` add, lens add).

Tight scope — KDoc-only edits. No code lines change in this task.

### Step 3: parameter-rename decision (R10 optional)

**Default decision: KEEP** the `SimState.initial(weatherByAerodrome = ...)` parameter name. Renaming touches 25+ test fixture call sites without architectural value; the parameter is a thin caller-ergonomics surface, not a field. Pin this decision in the task evidence note for traceability.

If the codex plan-review surfaces a strong argument for renaming, revisit at that time.

### Step 4: memory entry update IN PLACE if present, else record in evidence (R11 — refined per codex round 4)

The memory file lives **outside the repo** at `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/project_rich_world_domain.md`. It is user-authored and may not exist in all environments (CI, contributor machines, fresh clones).

**Two acceptance paths:**

**Path A (memory file present)**: update the existing file in place. **Do NOT create a new memory file.** Read the existing file first to confirm the shape, then append a "Precedents (both shipped)" block:

```
**Precedents (both shipped):**
- fn-12: `Runway.obstruction: RunwayObstruction?` on `core.world.Runway` (with `Some → None → Some` lifetime semantics).
- fn-16: `Aerodrome.weather: WeatherObservation?` on `core.world.Aerodrome` (overwrite-in-place; no lifetime semantics).

Future world-state additions (surface contamination, lighting, NOTAM, taxiway-closure) default to the same entity-on-domain shape. The all-aerodromes walk pattern lives in `sim.RunwayObstructionWiring.expireRunwayObstructions`; the single-id lens lives in `core.world.AviationWorld.updateAerodrome`.
```

If the existing file has a different shape than expected, integrate cleanly — preserve the existing principle text and append the precedent list without restructuring.

**Path B (memory file absent)**: record the missing-file state in the task evidence note and proceed. Do **not** create the file. **Unconditional** (per codex round 7) — regardless of environment (CI / local / contributor); user-memory files are user-authored. Capture the exact text that WOULD have been appended in the evidence note so the user can update later.

Acceptance is satisfied by either path being clearly demonstrated.

### Step 5: smoke verify (R12)
Run `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt`. Expected: all tests green, detekt baseline unchanged. Documentation-only edits shouldn't move the needle, but the build green is the sanity check.

## Investigation targets

**Required:**
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/project_rich_world_domain.md` — existing memory entry (target of the update)
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotInput.kt:57-83` — `weatherByAerodrome` field KDoc (fn-14.1) — close the deferment cite
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt` — verify fn-16.1 left a clean state (`WeatherObservation` definition removed)
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt:19-36` — class-level KDoc field-iteration block
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt:186-211` — extractor KDoc
- Every file with `state.weatherByAerodrome` in a KDoc/comment block — grep enumerable

**Required (deferment registers — TWO sister registers per `.plan:484`'s standing convention; both files carry the same entries):**

1. **`.plan` (repo-root, IN-REPO, canonical project-local backlog)** — per `.plan`'s own header rules ("On every commit, check whether any item here was resolved and mark it DONE. When work is deferred mid-session, add it here before closing.") AND per `.plan:484`'s explicit register-split note: `~/.claude/plans/pilot-firewall.md` is the architectural design home (D-PF/D-AUDIT/D-PASS items), `.plan` is the project-local backlog, both carry identical entries so on-repo readers / CI / fresh-clone reviewers can resolve every deferment from `.plan` alone.
   - **Scan `.plan` for `D-PASS-wind-state-migrate-to-aerodrome`**: if present, mark `DONE (2026-05-11, fn-16)` per `.plan`'s DONE format. If absent, record in evidence (the deferment may not have been mirrored into `.plan` yet).
   - **Append the 7 NEW deferments** (per epic § Deferments register: `D-PASS-weather-model-expansion`, `D-PASS-per-runway-weather`, `D-PASS-weather-history-replay`, `D-PASS-metar-taf-ingestion`, `D-PASS-weather-validity-window`, `D-PASS-weather-shift-event-leaf`, `D-PASS-direct-simstate-constructor-canonicalization`) to the appropriate active-items section of `.plan` with full four-field contracts (what-today / why-wrong / real-fix-contract / trigger) per the pattern at `.plan:482-516` ("fn-8.3 G1 closure deferments"). Same Impact × Effort grading.
   - `.plan` is in-repo and **always present** — no missing-file path.

2. **`~/.claude/plans/pilot-firewall.md`** — sister register (lives outside repo; the architectural design home for D-PF/D-AUDIT/D-PASS items, pre-dates `.plan`). Same closure + same 7 NEW entries. **Fail-loud-else-record-in-evidence**: if file absent (CI / fresh-clone), record missing-file state in evidence and capture intended text for later user update.

Both registers must end up consistent. Per `.plan:484`'s convention: on-repo readers resolve from `.plan` alone; the external register is the architectural design home but is not load-bearing for on-repo discoverability.

## Key context

- **Documentation + external-register task.** **No behavioral code changes; production-source KDoc/comment edits AND external-memory/register file updates only** (per codex round 7 wording clarification — the external user-memory and deferment-register files at `~/.claude/...` are within scope, but they live outside the repo). If a KDoc edit feels like it requires a code line change, that's a sign the change belongs in fn-16.1 (paper-trail task here, no scope creep).
- **Memory entry: update `project_rich_world_domain.md` in place**, don't create a new file. The principle is the same; the precedent list grows.
- **Parameter rename: default-no.** Keep the `weatherByAerodrome: Map<...>` parameter name on `SimState.initial`. Rename costs (25+ call sites) outweigh the architectural value (zero — it's a parameter).
- **Sweep audit grep is the bar.** Zero remaining `SimState.weatherByAerodrome` field references (compiler-enforced post fn-16.1). All other remaining `weatherByAerodrome` references categorised per the epic R9 allowed list.

## Acceptance

- [ ] R9 closure: post fn-16.1, the audit greps return zero unauthorized hits. Every remaining `weatherByAerodrome` reference categorised into the allowed list (parameter name, `PilotInput` field name, `MultiAerodromeFixture` struct field, `SimTraceQueries.weatherTransitions` name, `PilotWiring.buildPilotInput` named-arg call site).
- [ ] R10: KDoc updates landed at the eight sites enumerated in the Files section above. Every `D-PASS-wind-state-migrate-to-aerodrome` reference replaced with a fn-16 closure note. Every `state.weatherByAerodrome[X]` reference in KDoc/inline comments replaced with `world.aerodromes[X].weather` (reader form) or `world.updateAerodrome(X) { ... }` (writer form).
- [ ] R11: memory file `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/project_rich_world_domain.md` handling — **Path A** (file present): updated in place with the two-precedents block (fn-12, fn-16) and the next-slice default-shape note; NO new memory file created. **Path B** (file absent — CI/fresh-clone): missing-file state recorded in this task's evidence note plus the exact intended-append text captured for later user update. Either path satisfies acceptance. Refined per codex round 4 to handle the user-memory-outside-repo case.
- [ ] R10 (optional rename decision): the `SimState.initial(weatherByAerodrome = ...)` parameter name decision pinned in this task's evidence. Default = KEEP. If renamed, all 25+ test fixture sites updated; if not, no code touched.
- [ ] R12: `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt` exits 0. All eight goldens green. detekt baseline unchanged.
- [ ] Sweep audit script output committed to the task evidence note (so future readers can replay the verification).
- [ ] **R13: `.plan` (in-repo, repo-root, canonical project-local backlog) reconciled (NEW per codex round 8):**
  - `D-PASS-wind-state-migrate-to-aerodrome` entry scanned; if present marked `DONE (2026-05-11, fn-16)`; if absent recorded in evidence.
  - All 7 NEW deferments appended with the full four-field contract format from `.plan:482-516` ("fn-8.3 G1 closure deferments" pattern): what-today / why-wrong / real-fix-contract / trigger; Impact × Effort grading.
  - `.plan` is in-repo and always present (no missing-file branch).
- [ ] `D-PASS-wind-state-migrate-to-aerodrome` entry in `~/.claude/plans/pilot-firewall.md` § Deferments register marked closed (the external sister register; the architectural design home for D-PF/D-AUDIT/D-PASS items per `.plan:484`'s register-split convention). If the file is absent (CI / fresh-clone — same case as R11's user-memory file), record the missing-file state in evidence and capture the intended closure text for later user update. **NEW deferments from this epic** (per the epic's "Deferments register" section — the same 7 entries appended to `.plan` per R13 above) — appended to this register's open-items list too, matching `.plan` content. Same fail-loud-if-missing-else-record-in-evidence semantics.

## Done summary

_(filled at completion)_

## Evidence

_(filled at completion)_
