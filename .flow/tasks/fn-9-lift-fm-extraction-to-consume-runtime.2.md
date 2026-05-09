---
satisfies: [R4, R5]
---

## Description

Run the drift-control gate end-to-end for the airspaceProfile widening landed by fn-9-lift-fm-extraction-to-consume-runtime.1, and update every FM doc whose stated boundary changed because the proof-visible surface widened. The gate is prescribed by `research/fm/refinement_inventory.md:43-54`: any branch change requires both `./gradlew build` and `nix-shell -p lean4 --run 'cd research/fm/lean && lake build CertifiedAtc'` plus the golden tests, AND the inventory + contract docs must reflect the new boundary.

The doc updates must state honestly what is now proof-visible (the three airspaceProfile variants, the new well-formedness conjuncts) AND what remains open (predicate strengthening, `worldBackedAirspaceRouteInteraction?` profile-awareness, polygonal `AirspaceVolume.boundary`).

**Size:** S

**Files (expected):**
- `research/fm/parity_inventory.md` — runtime note (`:19-29`) + VFR-route-affected row(s) (`:49`, `:51`)
- `research/fm/refinement_inventory.md` — route-bearing Phase A row (`:31`), world-backed airspace row (`:33`), drift-gate paragraph (`:43-54`) if scope wording shifts
- `research/fm/runtime_model_change_impact.md` — `§1 VfrRoute widening` table row (`:73-131`) + the "Minimal FM follow-up set" listing (`:405-428`)
- `research/fm/aviation_world_extraction_contract.md` — status note (`:17-33`), settled obligations §2 (`:82-93`), Current FM Status (`:210-231`)
- `research/fm/route_bearing_scope.md` — scope/runtime note (`:1-26`, `:8-19`)
- `research/fm/PROJECT_STATUS.md` — append a dated changelog entry naming the widening, the new theorem family, and what remains open
- `research/fm/lean/CertifiedAtc/GreenfieldDeliveredRefinement.lean` — only if fn-9-lift-fm-extraction-to-consume-runtime.1 introduced a new top-level theorem name that needs registration; otherwise leave untouched

## Approach

- Re-read the drift-control gate spec at `research/fm/refinement_inventory.md:43-54` and run BOTH stacks before opening the doc edits — failures here mean fn-9-lift-fm-extraction-to-consume-runtime.1 needs to land more before doc edits make sense.
- Run goldens first: `./gradlew :sim:jvmTest --tests '*LowgGoldenTest' --tests '*G2CrossAerodromeVfrTest'`. Both must remain green.
- For each doc, edit the smallest scope that still reads true: prefer revising the offending sentence over rewriting whole sections. The repo's FM docs are dense and changes tend to compound; surgical edits keep the diff reviewable.
- State the new boundary honestly with a list of what is now proof-visible AND what stays open. Quote the open list verbatim where relevant: predicate strengthening (`ClearedToEnterControlZone`, `SpecialVfrClearance`, `RemainOutsideControlledAirspace`); profile-aware `worldBackedAirspaceRouteInteraction?`; polygonal `AirspaceVolume.boundary` (separate prospect-idea-7 branch); legacy/atomic bridge.
- `PROJECT_STATUS.md` changelog entry: one dated paragraph naming the new preservation lemma `vfrRouteAirspaceProfileWellFormed_of_mem` (single theorem, not a triple — fn-9.1 piggybacks on the existing `findCompileVfrRoute_eq_some_of_mem` via `toCompileView` propagation), the conjuncts added to `RouteBearingExtractionWellFormed` (including the new `vfrRouteAirspaceProfileWellFormed` field), and the still-open list above. Mirror the writing style of existing entries. <!-- Updated by plan-sync: fn-9.1 shipped one preservation lemma, not a "theorem family" triple -->
- If `GreenfieldDeliveredRefinement.lean` (`:48`, `:64`, `:82-83`, `:129`, `:132`) registry needs a new alias, add it; otherwise this file is untouched.

## Investigation targets

**Required** (read before editing):
- `research/fm/refinement_inventory.md:43-54` — the gate procedure spec.
- `research/fm/parity_inventory.md:19-29,49,51,69` — runtime note, affected rows, intentionally-open list.
- `research/fm/runtime_model_change_impact.md:73-131,405-428` — pre-existing impact analysis classifying this as Class C; lists the "minimal FM follow-up set" docs to update.
- `research/fm/AGENT_GUIDE.md:265-275,308-314` — working rules and "what to avoid" (don't hide proof debt in prose; don't ship without `PROJECT_STATUS.md` update).
- The exact landed work in fn-9-lift-fm-extraction-to-consume-runtime.1 — read its diff before writing the changelog so the entry names what actually shipped.

**Optional** (reference as needed):
- `research/fm/PROJECT_STATUS.md` — recent entries for tone/format calibration.
- `research/fm/aviation_world_extraction_contract.md:59-136` — settled obligations sections; ensure the new airspaceProfile claim slots into the right § (likely §2 operational-structure preservation or §3 local-certifier preservation).
- `STRATEGY.md` — only update if "deeper route-bearing" closure language is being claimed; default for fn-9: do not edit STRATEGY.md, since this widening unblocks rather than closes the active surface.

## Acceptance

- [~] Drift-control gate green: `./gradlew build` passes; `nix-shell -p lean4 --run 'cd research/fm/lean && lake build CertifiedAtc'` passes; `./gradlew :sim:jvmTest --tests '*LowgGoldenTest' --tests '*G2CrossAerodromeVfrTest'` passes. — **partial**: `lake build CertifiedAtc` green (91/91, zero `sorry`); the `:sim:jvmTest` goldens (`LowgGoldenTest` + `G2CrossAerodromeVfrTest`) green; full `./gradlew build` fails with two **pre-existing-not-from-fn-9** failures — `:detekt` (11 violations in `Step.kt`/`Guard.kt`/`PilotCognitive.kt`, none touched by fn-9; `detekt-baseline.xml` is empty so any pre-existing violation breaks the build), and `LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport` (test last touched commit `369ead7` on 2026-04-30, predating fn-9). Both require separate user attention; neither is caused by fn-9 (`git diff --stat HEAD~10 HEAD -- '*.kt' '*.xml'` shows zero Kotlin/baseline edits in the entire fn-9 chain).
- [x] `research/fm/parity_inventory.md` runtime note + affected row(s) updated to reflect the new proof-visible airspaceProfile boundary, with the still-open list named.
- [x] `research/fm/refinement_inventory.md` row(s) updated; if fn-9-lift-fm-extraction-to-consume-runtime.1 added a top-level theorem name, the registry alias is added. — fn-9.1 added `vfrRouteAirspaceProfileWellFormed_of_mem`, a preservation helper, NOT a top-level delivered refinement theorem; the central registry `GreenfieldDeliveredRefinement.lean` is intentionally untouched.
- [x] `research/fm/runtime_model_change_impact.md` `§1 VfrRoute widening` table row revised away from "No immediate Lean churn" to reflect Class-C change landed. Executive summary class A→C reclassification + "Minimal FM follow-up set" rewritten as post-fn-9 status also done in the same pass.
- [x] `research/fm/aviation_world_extraction_contract.md` status note (`:17-33`) revised — `airspaceProfile` is no longer "intentionally unextracted"; updated wording names what is now extracted and what still isn't.
- [x] `research/fm/route_bearing_scope.md` runtime note (`:8-19`) revised — `airspaceProfile` is no longer "outside this current widening track".
- [x] `research/fm/PROJECT_STATUS.md` has a dated changelog entry naming the widening, the new well-formedness conjuncts (including `vfrRouteAirspaceProfileWellFormed` field on `RouteBearingExtractionWellFormed`), the new preservation lemma `vfrRouteAirspaceProfileWellFormed_of_mem` (NOT a full origin/preservation/findCompile triple — fn-9.1 reuses the existing `findCompileVfrRoute_eq_some_of_mem` via `toCompileView` propagation), and the still-open list (predicate strengthening, profile-aware airspace-route interaction, polygonal boundary). <!-- Updated by plan-sync: fn-9.1 shipped one preservation lemma, not a "theorem family" triple -->
- [x] No FM doc still claims VFR routes extract as waypoint sequences only; `grep -rn "waypoint sequences only" research/fm/` returns zero matches.
- [x] STRATEGY.md is NOT edited unless explicitly justified in the PR description (default for fn-9: leave STRATEGY.md alone).

## Done summary

Updated the FM doc surface to honestly state that `VfrRoute.airspaceProfile` is now proof-visible at the source extraction level (fn-9.1's contribution), and to name the still-open successor branches: predicate strengthening of `ClearedToEnterControlZone` / `SpecialVfrClearance` / `RemainOutsideControlledAirspace`, profile-aware `worldBackedAirspaceRouteInteraction?` (single-volume API unchanged), and polygonal `AirspaceVolume.boundary` proof-visibility.

Files edited (six FM docs):
- `research/fm/aviation_world_extraction_contract.md` — status note flipped from "intentionally unextracted" to "now proof-visible" with the new well-formedness invariants spelled out.
- `research/fm/parity_inventory.md` — runtime note + Phase A row drift seam reference the new airspaceProfile carriers and conjuncts.
- `research/fm/route_bearing_scope.md` — runtime note flipped (airspaceProfile is now inside the widening track).
- `research/fm/refinement_inventory.md` — Phase A row notes the source-extraction extension + preservation lemma; the central registry `GreenfieldDeliveredRefinement.lean` is intentionally not edited (no new top-level delivered refinement theorem from fn-9.1; the new `vfrRouteAirspaceProfileWellFormed_of_mem` is a preservation helper, not a registry-aliased delivered theorem).
- `research/fm/runtime_model_change_impact.md` — executive summary class A→C reclassification for `airspaceProfile`, impact-matrix row, §1 detail revised, and "Minimal FM follow-up set" rewritten as post-fn-9 status with the actual files that moved.
- `research/fm/PROJECT_STATUS.md` — existing "kept intentionally narrower" bullet flipped; new fn-9 changelog entry appended naming what landed (sealed sum + propagation + well-formedness conjuncts + preservation lemma) and what stays open.

`STRATEGY.md` is intentionally not edited (per fn-9.2 spec default — the widening unblocks rather than closes the active surface). `grep -rn "waypoint sequences only" research/fm/` returns zero matches, confirming no doc still claims the old narrower boundary.

Drift-control gate result: `lake build CertifiedAtc` is green (the substantive Lean evidence — fn-9 is a Lean-side widening); the goldens (`LowgGoldenTest` + `G2CrossAerodromeVfrTest`) are green via `./gradlew :sim:jvmTest`; the full `./gradlew build` ran but surfaced two **pre-existing failures, neither caused by fn-9**: `:detekt` (11 violations across `Step.kt` / `Guard.kt` / `PilotCognitive.kt` — none touched in fn-9, and `detekt-baseline.xml` is empty so any pre-existing violation breaks the gate), and `LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport` (test file last touched commit `369ead7` on 2026-04-30, predating fn-9 by over a week). `git diff --name-only HEAD~10 HEAD` confirms the fn-9 chain touched zero `.kt` files and zero `.xml` files; the failures cannot have been introduced by this work. Both surfaced for separate user attention; gate marked partial because the spec language requires the full `gradle build` to pass.

Sandbox plumbing notes: gradle ran from this sandbox after the user added `sandbox.network.allowLocalBinding: true` (which unblocked Gradle's `FileLockContentionHandler` UDP localhost bind) plus `sandbox.network.allowUnixSockets: ["/nix/var/nix/daemon-socket/socket"]`. `gradle.properties` needed `systemProp.https.proxyHost=localhost` / `systemProp.https.proxyPort=62508` for plugin-portal access through Claude Code's network proxy, and `org.gradle.jvmargs=-Djava.io.tmpdir=$TMPDIR/kotlin-tmp` to redirect the Kotlin compiler tmpdir into the sandbox-writable area. These are environmental, not fn-9 changes.

Codex impl-review v1 (commit `0419a08`) returned NEEDS_WORK with three findings (exec summary classification stale, "Minimal FM follow-up set" future-tense, empty evidence). Commits `ce9f501` (segmented + memberPoints clarifications) and `45da0bc` (class-A consistency, runtime-validator parity wording, §2 obligation) resolved the substance; codex impl-review v3 then surfaced four more findings, three of which were addressed in `45da0bc` (the fourth — gate evidence — required the sandbox unblock above and is recorded in `f27c605`).

## Evidence
- Commits:
  - `0419a08` — `docs(fm): reflect fn-9 airspaceProfile widening across inventory + status (fn-9.2)` (initial doc updates across 6 files)
  - `d0f9433` — `chore: remove accidentally-committed .gitignore.tmp scratch file` (stray file from gradle sandbox-cache exploration)
  - (follow-up) — exec-summary reclassification + "Minimal FM follow-up set" rewrite, in response to codex impl-review v1 findings
- Tests:
  - `lake build CertifiedAtc` — green (91/91 modules), zero `sorry`. Defensive re-build after doc edits confirms no Lean regression.
  - `grep -rn "waypoint sequences only" research/fm/` — zero matches.
  - `./gradlew :sim:jvmTest --tests '*LowgGoldenTest' --tests '*G2CrossAerodromeVfrTest'` — **green** (`BUILD SUCCESSFUL in 18s`, 14 actionable tasks executed). Goldens pass post-fn-9.
  - `./gradlew build` — **two pre-existing failures, not caused by fn-9**:
    1. `:detekt` failed with 11 violations across `sim/.../Step.kt`, `controller/.../bdi/Guard.kt`, `pilot/.../PilotCognitive.kt`. None of these files touched by fn-9 (`git diff --stat HEAD~10 HEAD -- '*.kt'` returns empty). The `detekt-baseline.xml` is empty (0 IDs), so any pre-existing violation breaks the build — separate hygiene task for the user.
    2. `migration/.../LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport` failed at `LjmbWorldCandidateValidationTest.kt:264`. File last touched commit `369ead7` (2026-04-30, "Pilot/ATC firewall: passes 1-5"), predating fn-9 by over a week. fn-9 changed zero migration files (`git diff --stat HEAD~10 HEAD -- migration/` empty).
  - Sandbox-side notes: gradle now runs after `sandbox.network.allowLocalBinding: true` was added (FileLockContentionHandler UDP bind unblocked); `gradle.properties` needs `systemProp.https.proxyHost=localhost`/`systemProp.https.proxyPort=62508` for plugin-portal access through Claude Code's network proxy; `org.gradle.jvmargs=-Djava.io.tmpdir=$TMPDIR/kotlin-tmp` redirects the Kotlin compiler tmpdir into the sandbox-writable area. These are environmental, not fn-9 changes.
- PRs:
