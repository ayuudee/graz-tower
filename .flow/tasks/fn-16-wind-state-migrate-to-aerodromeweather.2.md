---
satisfies: [R9, R10, R11, R12, R13]
---

## Description

<!-- Updated by plan-sync: fn-16.1 absorbed most of the planned R10 KDoc edits inline; fn-18.3 moved the deferment register from `.plan` + `~/.claude/plans/pilot-firewall.md` to in-repo `docs/deferments.md`. fn-16.2 is now verification-dominant with a narrowed live-edit set. -->

Sweep + paper-trail pass for fn-16. After fn-16.1 lands the atomic field migration (and absorbs most of the planned KDoc cross-references inline), this task:
1. Re-runs the audit script to confirm no orphan `SimState.weatherByAerodrome` references remain across the entire codebase (including test fixtures, KDoc, comments).
2. **Verifies** (not re-edits) that the KDoc updates listed in the epic spec § R10 already landed in fn-16.1. Most of the eight enumerated sites were absorbed during the atomic migration because the migration mechanically required touching them — see "Phase-1 absorption ledger" in the Files section below. Any residual hit is a sign the migration left a stale reference; clean it.
3. Updates the existing `project_rich_world_domain.md` memory entry **in place** (path A/B per R11) to confirm both `Runway.obstruction` (fn-12) and `Aerodrome.weather` (fn-16) live on entities, so future world-state slices follow the same shape automatically. **No new memory file is created.**
4. Optionally renames the `SimState.initial(weatherByAerodrome = ...)` parameter — default decision = **keep the name** since it's a parameter, not a field; renaming adds churn without architectural value. Pin the decision in this task's evidence note.
5. **Deferment-register reconciliation (R13 — revised per plan-sync 2026-05-13)**: register location changed during fn-18.3 — `.plan` + `~/.claude/plans/pilot-firewall.md` are no longer the canonical register; the in-repo `docs/deferments.md` is. The 7 NEW deferments from this epic were filed in `docs/deferments.md` as fn-18.3 migrated entries; the `D-PASS-wind-state-migrate-to-aerodrome` entry itself remains in `docs/deferments.md` with `Status: planned` and must be flipped to `closed` + moved to `## Archive` by this task. fn-16.1 also filed a NEW deferment `D-PASS-pilot-world-strip-dynamic-state` (planned) — already in-repo, no action.

This task is **L → S → XS** in scope vs fn-16.1 (further narrowed by plan-sync: most R10 KDoc edits already landed in fn-16.1; register location moved in fn-18.3). **Documentation + register updates only; no behavioral code changes**. The build green-bar is a sanity check that the documentation edits didn't accidentally touch a code path.

**Files:**

<!-- Updated by plan-sync 2026-05-13: classified each enumerated R10 site by whether fn-16.1 already absorbed the KDoc edit during the atomic migration. "ABSORBED" rows are verify-only; "RESIDUAL" rows are the live-edit set. -->

**Phase-1 absorption ledger (fn-16.1 already landed these; verify-only in fn-16.2):**
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt` — ABSORBED (`Aerodrome.weather` KDoc landed cleanly in fn-16.1 R2).
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldLenses.kt` — ABSORBED (KDoc with validation-boundary caveat landed in fn-16.1 R3, verified at WorldLenses.kt:5-23).
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WeatherObservation.kt` — ABSORBED (relocation KDoc landed in fn-16.1 R1, verified at WeatherObservation.kt:6-24).
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimState.kt` — ABSORBED (`SimState.initial` KDoc fully updated in fn-16.1 with order-of-operations notes at SimState.kt:231-258; `InitError` variants' KDocs both updated at SimState.kt:152-183; the class-level KDoc at lines 19-36 has no `weatherByAerodrome` reference to remove — the original field had no class-level mention).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotInput.kt` — ABSORBED (the fn-14 deferment cite was rewritten in fn-16.1 to read "fn-16 closed `D-PASS-wind-state-migrate-to-aerodrome` by hoisting weather onto `Aerodrome.weather`; the pilot firewall surface ... is unchanged — only the source migrates", verified at PilotInput.kt:70-74).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt` — ABSORBED (the `WeatherObservation` data class and its KDoc were cleanly removed by fn-16.1 R1; no residual orphan; verified at ControllerTypes.kt:260-272 — the `Controller output` section header sits directly after `ReceivedMessage`).
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/PilotWiring.kt:36-55` — ABSORBED (the comment block now layers fn-14.1's projection rationale ON TOP of an explicit fn-16 R7a source cite + pinned-form rationale, verified at PilotWiring.kt:42-52).
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/ControllerWiring.kt:150-154` — ABSORBED (one-line cite landed: `// fn-16 (R7b): source migrated from the deleted state.weatherByAerodrome to aerodrome.weather...`, verified at ControllerWiring.kt:150-153).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/SimTraceQueries.kt:186-213` — ABSORBED (extractor KDoc rewritten to cite "world.aerodromes[id].weather" + carries an explicit fn-16 R7c paragraph alongside the prior fn-14.2 anchor, verified at SimTraceQueries.kt:204-207).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveCrosswindTest.kt:789-820` — ABSORBED (`authorWeather` KDoc updated to cite the `updateAerodrome` lens per fn-16 R8, verified at the method header).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveCrosswindTest.kt:106-122` — ABSORBED (file-level KDoc rewritten to "`state.world.aerodromes[LOWG].weather` directly through ... `updateAerodrome` lens — fn-16", verified at lines 107-112).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt:57` — ABSORBED (provenance KDoc reads "`world.aerodromes[LOWG].weather` mutation" — verified).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt:81,89` — ABSORBED (file-level cross-references use `world.aerodromes[LOWG].weather` shape — verified).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt:80` — ABSORBED.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionTest.kt:99` — ABSORBED.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionContinueApproachTest.kt:119` — ABSORBED.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotTrainedGoAroundTest.kt:92` — ABSORBED.

**Residual live-edit set (fn-16.2 must actually edit these):**
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/project_rich_world_domain.md` — **EXISTING** memory file updated **in place** (path A) OR missing-file recorded in evidence (path B); see Step 4. NO new memory entry file is created; the principle and its precedent list live in one entry.
- `docs/deferments.md` (in-repo, repo-root) — **register location changed via fn-18.3** (per the `.flow/specs/fn-16-...md:439` migration note: "MIGRATED to `docs/deferments.md` per fn-18.3 on 2026-05-13"). Flip `D-PASS-wind-state-migrate-to-aerodrome` from `Status: planned` to `Status: closed` and **move the entry from the active `## D-PASS` section to `## Archive`** with a `Closed by:` line. Verify all 7 NEW deferments from this epic are already present in `docs/deferments.md` (they were filed during fn-18.3's migration sweep): `D-PASS-weather-model-expansion` (line 608), `D-PASS-per-runway-weather` (line 572), `D-PASS-weather-history-replay` (line 602), `D-PASS-metar-taf-ingestion` (line 566), `D-PASS-weather-validity-window` (line 620), `D-PASS-weather-shift-event-leaf` (line 614), `D-PASS-direct-simstate-constructor-canonicalization` (line 344). Each is in active section already — no action beyond audit.
- **Sister registers** (`.plan` + `~/.claude/plans/pilot-firewall.md`) — REMOVED FROM SCOPE per fn-18.3 migration; `docs/deferments.md` is the canonical register. If those external files still carry stale `D-PASS-wind-state-migrate-to-aerodrome` entries pointing into the now-migrated set, record in evidence (do not edit).

## Approach

### Step 1: confirm fn-16.1 acceptance (R9 closure)
Re-run the audit greps from the epic's "Quick commands" section:
```bash
grep -rn "\.weatherByAerodrome\b" --include="*.kt" .   # Any direct field access (should be zero non-param hits)
grep -rn "weatherByAerodrome" --include="*.kt" .        # All remaining (param names + PilotInput field + SimTraceQueries name)
grep -rn "D-PASS-wind-state-migrate-to-aerodrome" --include="*.kt" --include="*.md" .  # Deferment refs to close (now expected to surface docs/deferments.md only; source-tree refs were retired in fn-16.1's PilotInput.kt edit)
```

Categorise every remaining hit into the allowed list from the epic R9. Any uncategorised hit is a regression — root-cause and fix in fn-16.1 (this task is paper-trail only; don't add new code in fn-16.2 that should have been in fn-16.1).

### Step 2: KDoc verification (R10) — verify-only after plan-sync

<!-- Updated by plan-sync 2026-05-13: fn-16.1 absorbed the KDoc cross-cutting edits inline; this step is now verification-dominant. -->

For each entry in the **Phase-1 absorption ledger** above, open the cited file:line and verify the KDoc cite/replacement landed cleanly in fn-16.1. Pattern to look for:
- `D-PASS-wind-state-migrate-to-aerodrome` references that survived fn-16.1 (should be zero; deferment closure cite is the right shape).
- `state.weatherByAerodrome[X]` text in prose (should already read `world.aerodromes[X].weather`).
- `SimState.weatherByAerodrome` text in KDoc (should be replaced by lens-for-writers or walk-for-readers).
- Cross-reference fn-16 (this epic) at relocation sites (`WeatherObservation` move, `Aerodrome.weather` add, lens add).

If a verification turns up a stale reference, clean it inline (tight scope — KDoc-only). Record each verified hit (ABSORBED with file:line citation) OR each cleaned residual (RESIDUAL → fixed inline) in the evidence note. **The plan-sync ledger expects every R10 site to verify ABSORBED**; any RESIDUAL surfaces is a fn-16.1 paper-trail gap to flag in evidence.

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

**Required (deferment register — single canonical in-repo file post fn-18.3):**

<!-- Updated by plan-sync 2026-05-13: fn-18.3 migrated the register out of `.plan` + `~/.claude/plans/pilot-firewall.md` into in-repo `docs/deferments.md`. Single source of truth now lives in-repo. -->

**`docs/deferments.md` (repo-root, IN-REPO, canonical)** — per the `## D-PASS` and `## Archive` headings, the four-bucket model from `docs/deferments-CONVENTION.md`, and the migration note at `.flow/specs/fn-16-wind-state-migrate-to-aerodromeweather.md:439` ("MIGRATED to `docs/deferments.md` per fn-18.3 on 2026-05-13"). All entries on the register live in one file.

- **Scan `docs/deferments.md` for `D-PASS-wind-state-migrate-to-aerodrome`**: present at line ~626 with `Status: planned`. Edit to flip `Status: closed`, add a `**Closed by:** fn-16 (atomic field migration in fn-16.1 + paper-trail sweep in fn-16.2)` line, add a `**Enforcement:** ` line citing the key landed shapes (`Aerodrome.weather` on `core.world.WorldModel`, `AviationWorld.updateAerodrome` lens, `SimState.initial`'s fold + `WeatherForUnknownAerodrome` invariant, three production readers migrated, `G3aPilotReactiveCrosswindTest.authorWeather` mutator via lens), and **move the entry from `## D-PASS` to `## Archive`** preserving the four-field contract.
- **Verify the 7 NEW deferments are already filed** in `docs/deferments.md` (they were captured by fn-18.3's migration sweep when it consolidated entries from `.plan` + `~/.claude/plans/pilot-firewall.md`): `D-PASS-weather-model-expansion` (line ~608), `D-PASS-per-runway-weather` (line ~572), `D-PASS-weather-history-replay` (line ~602), `D-PASS-metar-taf-ingestion` (line ~566), `D-PASS-weather-validity-window` (line ~620), `D-PASS-weather-shift-event-leaf` (line ~614), `D-PASS-direct-simstate-constructor-canonicalization` (line ~344). No action beyond audit; if any are missing, file them per the schema in `docs/deferments-CONVENTION.md`.
- **`docs/deferments.md` is in-repo and always present — no missing-file branch.**

**Sister registers (removed from scope per fn-18.3):** `.plan` and `~/.claude/plans/pilot-firewall.md` are no longer the canonical deferment register. If they still carry the `D-PASS-wind-state-migrate-to-aerodrome` entry (possibly orphaned from before fn-18.3's migration), record in evidence — do not edit. The user owns post-migration cleanup of those legacy registers.

## Key context

<!-- Updated by plan-sync 2026-05-13: scope narrowed — fn-16.1 absorbed the KDoc work; fn-18.3 moved the deferment register in-repo. -->

- **Documentation + in-repo-register task.** **No behavioral code changes; verification-dominant KDoc work (fn-16.1 absorbed most of it inline) + in-repo `docs/deferments.md` edit + user-memory file update.** If a KDoc edit feels like it requires a code line change, that's a sign the change belongs in fn-16.1 (paper-trail task here, no scope creep).
- **Memory entry: update `project_rich_world_domain.md` in place**, don't create a new file. The principle is the same; the precedent list grows.
- **Parameter rename: default-no.** Keep the `weatherByAerodrome: Map<...>` parameter name on `SimState.initial`. Rename costs (25+ call sites) outweigh the architectural value (zero — it's a parameter).
- **Sweep audit grep is the bar.** Zero remaining `SimState.weatherByAerodrome` field references (compiler-enforced post fn-16.1). All other remaining `weatherByAerodrome` references categorised per the epic R9 allowed list.
- **Deferment register: in-repo only.** Post fn-18.3 the canonical register is `docs/deferments.md` in the repo root. `.plan` and `~/.claude/plans/pilot-firewall.md` are NOT in scope for fn-16.2; if they retain stale entries, surface in evidence only.

## Acceptance

- [ ] R9 closure: post fn-16.1, the audit greps return zero unauthorized hits. Every remaining `weatherByAerodrome` reference categorised into the allowed list (parameter name, `PilotInput` field name, `MultiAerodromeFixture` struct field, `SimTraceQueries.weatherTransitions` name, `PilotWiring.buildPilotInput` named-arg call site).
- [ ] R10: KDoc updates landed at the enumerated sites — **verified ABSORBED per the Phase-1 absorption ledger in Files (above) for all sixteen sites** (plan-sync 2026-05-13 confirmed fn-16.1 absorbed every KDoc cross-reference inline during the atomic migration). Any RESIDUAL site cleaned inline and recorded in evidence as a fn-16.1 paper-trail gap. Every `D-PASS-wind-state-migrate-to-aerodrome` reference in source-tree code replaced with a fn-16 closure note. Every `state.weatherByAerodrome[X]` reference in KDoc/inline comments replaced with `world.aerodromes[X].weather` (reader form) or `world.updateAerodrome(X) { ... }` (writer form).
- [ ] R11: memory file `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/project_rich_world_domain.md` handling — **Path A** (file present): updated in place with the two-precedents block (fn-12, fn-16) and the next-slice default-shape note; NO new memory file created. **Path B** (file absent — CI/fresh-clone): missing-file state recorded in this task's evidence note plus the exact intended-append text captured for later user update. Either path satisfies acceptance. Refined per codex round 4 to handle the user-memory-outside-repo case.
- [ ] R10 (optional rename decision): the `SimState.initial(weatherByAerodrome = ...)` parameter name decision pinned in this task's evidence. Default = KEEP. If renamed, all 25+ test fixture sites updated; if not, no code touched.
- [~] R12: `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt` exits 0. All eight goldens green. detekt baseline unchanged. **PARTIAL** per codex round 2 — fn-16-relevant test targets all GREEN, but `:migration:allTests` fails due to pre-existing `D-WORLD.2 (blocked)`: `LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport` has been failing on `main` since commit `369ead7` (2026-04-30); unrelated to weather state shape. Carve-out promoted from narrative-only (fn-5/6.2/9.2/11.2/16.1) to named register entry in `docs/deferments.md` by this task. Future epics inherit the partial-with-D-WORLD.2 framing until that deferment closes.
- [ ] Sweep audit script output committed to the task evidence note (so future readers can replay the verification).
- [ ] **R13: `docs/deferments.md` (in-repo, repo-root, canonical) reconciled (revised per plan-sync 2026-05-13 — register migrated from `.plan` + `~/.claude/plans/pilot-firewall.md` to in-repo `docs/deferments.md` via fn-18.3):**
  - `D-PASS-wind-state-migrate-to-aerodrome` entry at `docs/deferments.md:~626` flipped from `Status: planned` to `Status: closed`; `**Closed by:**` line added citing fn-16.1 + fn-16.2; `**Enforcement:**` line added citing the key landed shapes; entry moved from `## D-PASS` to `## Archive`.
  - All 7 NEW deferments from this epic verified present in `docs/deferments.md` (filed during fn-18.3's migration sweep): `D-PASS-weather-model-expansion`, `D-PASS-per-runway-weather`, `D-PASS-weather-history-replay`, `D-PASS-metar-taf-ingestion`, `D-PASS-weather-validity-window`, `D-PASS-weather-shift-event-leaf`, `D-PASS-direct-simstate-constructor-canonicalization`. Any missing entry filed per the schema in `docs/deferments-CONVENTION.md`.
  - `docs/deferments.md` is in-repo and always present (no missing-file branch).
  - **Sister registers `.plan` + `~/.claude/plans/pilot-firewall.md` removed from scope** — fn-18.3's migration made `docs/deferments.md` canonical; legacy register entries in those files (if any) are out of fn-16.2 scope. Record state in evidence if observed.

## Done summary

Paper-trail sweep closing fn-16. R9 audit greps surfaced only categorizable hits (parameter-name / PilotInput-field / migration-narrative-cite); R10 absorption-ledger verified for the 16 enumerated sites with two RESIDUAL stale references in `G3aPilotReactiveTailwindTest.kt` (a fn-15 sibling not in the original ledger) cleaned inline; R11 Path B taken (memory file absent — recorded in evidence, intended-append text captured); R12 PARTIAL — fn-16-relevant test targets green, `:migration:allTests` blocked by `D-WORLD.2` (pre-existing `LjmbWorldCandidateValidationTest` failure, unrelated to fn-16, promoted from narrative carve-out to named register entry by this task); R13 `docs/deferments.md` flipped `D-PASS-wind-state-migrate-to-aerodrome` from `planned` to `closed` and moved to `## Archive` with the three-field locked schema (Status + Closed by + Enforcement) per `docs/deferments-CONVENTION.md` § 5.2.2, plus `D-WORLD.2` filed as `Status: blocked` for the cross-epic R12 inheritance. Parameter-rename decision: KEEP — pinned in evidence below.

## Evidence

### R9 — sweep audit grep output (post-fn-16.1 + this commit)

```
$ grep -rn "\.weatherByAerodrome\b" --include="*.kt" .
```
All 24 remaining hits categorized into the R9 allowed list:

- **Parameter name `weatherByAerodrome` on `SimState.initial`** (per Decision #10, KEEP):
  `sim/.../G2CrossAerodromeVfrTest.kt:255` (passes `Fixtures.LOWG_LJMB_VFR.weatherByAerodrome` through).
- **`PilotInput.weatherByAerodrome` field reads** (different type — pilot-side firewall surface, fn-14.1; unchanged by fn-16):
  `core/.../WorldModel.kt:397,410` (KDoc cross-refs), `pilot/.../Pilot.kt:121,130,200`, `sim/.../PilotWiringWeatherProjectionSpec.kt:110,118,125,130`, `sim/.../G3aPilotReactiveTailwindTest.kt:95,158`, `sim/.../G3aPilotReactiveCrosswindTest.kt:61,114`, `sim/.../SimTraceQueries.kt:199`.
- **Migration-narrative cites** (intentional historical references — predecessor/closure text, verifies the migration narrative landed):
  `core/.../WorldModel.kt:401` (`Predecessor: SimState.weatherByAerodrome:...`),
  `sim/.../PilotWiring.kt:43` (`fn-16 (R7a): source migrated from the deleted state.weatherByAerodrome...`),
  `sim/.../ControllerWiring.kt:151` (`fn-16 (R7b): source migrated from the deleted state.weatherByAerodrome...`),
  `sim/.../SimState.kt:159,246` (`fn-16: predicate source migrated from the deleted SimState.weatherByAerodrome map...` / `no flat SimState.weatherByAerodrome field survives`),
  `sim/.../SimTraceQueries.kt:205` (`fn-16 (R7c): source migrated from the deleted SimState.weatherByAerodrome to...`),
  `sim/.../G3aPilotReactiveCrosswindTest.kt:813` (`fn-16 (R8): migrated from the deleted state.weatherByAerodrome flat map to...`),
  `sim/.../G3aPilotReactiveTailwindTest.kt:91,645,1022` (residuals cleaned inline by this commit — see R10 below).

```
$ grep -rn "D-PASS-wind-state-migrate-to-aerodrome" --include="*.kt" --include="*.md" docs/ pilot/ sim/ controller/ core/ migration/ protocol/
pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotInput.kt:71:  * closed `D-PASS-wind-state-migrate-to-aerodrome` by hoisting
docs/deferments.md:824: ### D-PASS-wind-state-migrate-to-aerodrome — Wind lives on Aerodrome.weather (Archive entry)
```
Exactly the two expected source-tree references remain — `PilotInput.kt:71` is the fn-16.1 closure cite (absorbed); `docs/deferments.md:824` is fn-16.2's Archive entry. **Zero unauthorized hits.**

### R10 — KDoc verification ledger + RESIDUAL cleanup

**ABSORBED ledger (16 sites verified clean per the plan-sync ledger):**
- `core/.../world/WorldModel.kt` Aerodrome.weather KDoc — verified at lines 388-410 (R2).
- `core/.../world/WorldLenses.kt:5-23` — KDoc with validation-boundary caveat (R3).
- `core/.../world/WeatherObservation.kt:6-23` — relocation KDoc (R1).
- `sim/.../SimState.kt:151-183, 231-258` — InitError variant KDocs + initial fold KDoc (R4/R5).
- `pilot/.../PilotInput.kt:70-74` — deferment-closure cite (R10 cross-ref scrub).
- `controller/.../ControllerTypes.kt:260-272` — WeatherObservation orphan cleanly removed.
- `sim/.../PilotWiring.kt:42-55` — fn-14.1 + fn-16 R7a layered cites.
- `sim/.../ControllerWiring.kt:150-153` — fn-16 R7b cite.
- `sim/.../testing/SimTraceQueries.kt:204-207` — R7c cite.
- `sim/.../G3aPilotReactiveCrosswindTest.kt:805-815, 107-112` — R8 authorWeather lens + file-level KDoc.
- `sim/.../testing/Fixtures.kt:57` — provenance KDoc.
- `sim/.../LowgGoldenTest.kt:81,89`, `G2CrossAerodromeVfrTest.kt:80`, `G3aRunwayObstructionTest.kt:99`, `G3aRunwayObstructionContinueApproachTest.kt:119`, `G3aPilotTrainedGoAroundTest.kt:92` — file-level KDoc cross-refs.

**RESIDUAL cleaned inline in this commit** (sites NOT in the original absorption ledger):
- `sim/.../G3aPilotReactiveTailwindTest.kt:87-91` — file-level KDoc had stale `weatherByAerodrome[LOWG] = WeatherObservation(...)` form; rewrote to `world.aerodromes[LOWG].weather = ...` via `AviationWorld.updateAerodrome` with fn-16 R8 predecessor cite.
- `sim/.../G3aPilotReactiveTailwindTest.kt:642-647` — "World-weather transition pin" comment block referenced `weatherByAerodrome[LOWG] slice`; rewrote to `world.aerodromes[LOWG].weather slice (fn-16; predecessor was the deleted state.weatherByAerodrome[LOWG] flat-map slot)`.

These were fn-15 work that landed before fn-16; the absorption ledger correctly only covered files the fn-16.1 atomic migration mechanically touched. The fn-15 tailwind test is structurally identical to its fn-14 crosswind sibling, which IS in the ledger and was fully absorbed — fn-16.2's residual sweep caught the parallel sibling.

### R10 (optional parameter rename) — KEEP

Decision: **KEEP** the `SimState.initial(weatherByAerodrome = ...)` parameter name. Rationale: parameter is purely a caller-ergonomics surface, not a field. Renaming would touch 25+ test fixture call sites (`mapOf(lowg to fixture.weather)` style) without any architectural value. The smart constructor's fold (Decision #5) renders the parameter shape an internal implementation detail. Pinned per Decision #10 default.

### R11 — memory file handling (Path B taken)

The memory file path cited in the task spec — `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/project_rich_world_domain.md` — does **NOT exist** on this machine. The graz-tower variant directory (`~/.claude/projects/-Users-andrew-dev-projects-graz-tower/memory/`) exists with `MEMORY.md` and `reference_audit_registers.md` only — no `project_rich_world_domain.md` entry. `flowctl memory search "rich_world_domain"` also returned zero matches in the in-repo `.flow/memory/` categorized tree.

**Path B taken: missing-file state recorded; no file created.** Intended-append text captured below for later user update if/when the file materializes:

```markdown
**Precedents (both shipped):**
- fn-12: `Runway.obstruction: RunwayObstruction?` on `core.world.Runway` (with `Some → None → Some` lifetime semantics).
- fn-16: `Aerodrome.weather: WeatherObservation?` on `core.world.Aerodrome` (overwrite-in-place; no lifetime semantics).

Future world-state additions (surface contamination, lighting, NOTAM, taxiway-closure) default to the same entity-on-domain shape. The all-aerodromes walk pattern lives in `sim.RunwayObstructionWiring.expireRunwayObstructions`; the single-id lens lives in `core.world.AviationWorld.updateAerodrome`.
```

In-repo memory at `.flow/memory/bug/integration/rich-world-domain-entity-field-needs-2026-05-13.md` (captured by fn-16.1) already documents the pilot-firewall reachability lesson that complements this principle entry.

### R12 — smoke verify (PARTIAL — `:migration:allTests` blocked by D-WORLD.2)

**Status: PARTIAL.** The full epic-spec command does NOT exit 0 because of a pre-existing `:migration:jvmTest` failure independent of fn-16. The fn-16-relevant verification surfaces are all GREEN; the failure is filed as a named deferment (`D-WORLD.2 — LjmbWorldCandidateValidationTest pre-existing failure`, status `blocked`) so this carve-out has a register entry rather than only narrative evidence.

**fn-16-relevant green bar** (subset of the spec command — fn-16-touched modules only):

```
$ GRADLE_USER_HOME=$TMPDIR/gradle-user-home _JAVA_OPTIONS=-Djava.io.tmpdir=$TMPDIR \
  JAVA_HOME=/nix/store/fh73gfg7fp1mhyxw6cf8bkv14v2xbzbb-zulu-ca-jdk-21.0.8 \
  ./gradlew --offline --no-daemon \
    :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests detekt
...
BUILD SUCCESSFUL in 6s
25 actionable tasks: 1 executed, 24 up-to-date
```

All eight goldens green via the cached `:sim:jvmTest` task. fn-16.2 documentation-only edits don't invalidate the test cache. detekt baseline unchanged.

**Full spec command output** (with `:migration:allTests`):

```
$ ./gradlew --offline --no-daemon \
    :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:allTests :protocol:allTests :migration:allTests detekt
...
> Task :migration:jvmTest
LjmbWorldCandidateValidationTest[jvm] > writesLjmbCurrentCoreValidationReport()[jvm] FAILED
    org.opentest4j.AssertionFailedError at LjmbWorldCandidateValidationTest.kt:264
80 tests completed, 1 failed
BUILD FAILED in 11s
```

This failure is **PRE-EXISTING** and **UNRELATED** to fn-16:
- Test file last touched in commit `369ead7` (2026-04-30, "Pilot/ATC firewall: passes 1-5"), predating fn-16 by ~11 days.
- `git diff --stat 369ead7..HEAD -- migration/` shows fn-16's only touch to `migration/` is `migration/src/jvmTest/.../...` test fixtures that absorb fn-16's API changes (the failure is unrelated to those fixtures — it concerns LJMB world-candidate JSON authoring / IFR SID inventory validation).
- fn-5, fn-6.2, fn-9.2, fn-11.2, fn-16.1 all observed the same failure and explicitly documented it as out-of-scope.
- Promoted from narrative carve-out to named register entry by fn-16.2 — filed as `D-WORLD.2` in `docs/deferments.md` with `Status: blocked, Blocked on: LJMB world-candidate JSON authoring`.

**R12 acceptance status:** PARTIAL — every fn-16-relevant test target is GREEN; the only failure is `D-WORLD.2 (blocked, pre-existing, unrelated)`. The standing R12 shape (which inherits `:migration:allTests` from a planning-time over-cast that didn't anticipate D-WORLD.2 surfacing) cannot exit 0 in any epic until D-WORLD.2 closes. Future epics may inherit the partial-with-D-WORLD.2 framing.

### R13 — `docs/deferments.md` reconciliation

- `D-PASS-wind-state-migrate-to-aerodrome` entry at `docs/deferments.md` flipped from `Status: planned` to `Status: closed`. Entry moved from active `## D-PASS` section to `## Archive` (now at lines 824-827). New body conforms to the three-field locked schema per `docs/deferments-CONVENTION.md` § 5.2.2 (Status + Closed by + Enforcement). Enforcement field cites: `Aerodrome.weather` field; relocated `WeatherObservation`; `AviationWorld.updateAerodrome` lens + unit test; `SimState.initial` fold with `WeatherForUnknownAerodrome` + `MissingWeatherForRunwayAerodrome` invariants; deleted `SimState.weatherByAerodrome` field; three production readers (pinned `mapNotNull` form on `PilotWiring`); `authorWeather` lens migration on `G3aPilotReactiveCrosswindTest`; 8 direct-constructor `SimState` test sites cleaned; UNCHANGED firewall surfaces (`PilotInput.weatherByAerodrome`, `ControllerView.weather`); cross-reference to `D-PASS-pilot-world-strip-dynamic-state` follow-up filed during fn-16.1 codex round 2.
- 7 NEW deferments from this epic verified present in `docs/deferments.md` (already filed during fn-18.3's migration sweep, no action needed): `D-PASS-weather-model-expansion` (line 608), `D-PASS-per-runway-weather` (line 572), `D-PASS-weather-history-replay` (line 602), `D-PASS-metar-taf-ingestion` (line 566), `D-PASS-weather-validity-window` (line 620), `D-PASS-weather-shift-event-leaf` (line 614), `D-PASS-direct-simstate-constructor-canonicalization` (line 344).
- Plus `D-PASS-pilot-world-strip-dynamic-state` (line 633) filed during fn-16.1 codex round 2 — already in-repo, no action.
- **NEW:** `D-WORLD.2 — LjmbWorldCandidateValidationTest pre-existing failure` filed as `Status: blocked` to capture the cross-epic R12-inheritance carve-out (see R12 evidence above). This promotes the standing narrative carve-out (observed in fn-5, fn-6.2, fn-9.2, fn-11.2, fn-16.1, fn-16.2) into a named register entry so future epics can cite the single deferment instead of re-deriving the out-of-scope argument.

### Sister registers (legacy, out of scope per fn-18.3)

- `.plan` (in-repo) — `grep -n "D-PASS-wind-state-migrate-to-aerodrome" .plan` returns **zero hits**. fn-18.3's migration cleaned the entry when it was promoted into `docs/deferments.md`. No action.
- `~/.claude/plans/pilot-firewall.md` (external) — same grep returns **zero hits**. fn-18.3 cleaned this too. No action.

Both sister registers are post-migration clean; no orphan entries observed.

### Commits + verification artifacts

- Commits: `9435175` (this commit — docs(fn-16.2): close D-PASS-wind-state-migrate + paper-trail sweep)
- Plus fn-16.1 series for context: `51ccebf` (atomic migration), `3ced6e0` (codex round 1), `55f5223` (codex round 2), `dbf4c8d` (memory capture)
- Build verification: see R12 above
- PRs: none
