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

- [ ] Drift-control gate green: `./gradlew build` passes; `nix-shell -p lean4 --run 'cd research/fm/lean && lake build CertifiedAtc'` passes; `./gradlew :sim:jvmTest --tests '*LowgGoldenTest' --tests '*G2CrossAerodromeVfrTest'` passes.
- [ ] `research/fm/parity_inventory.md` runtime note + affected row(s) updated to reflect the new proof-visible airspaceProfile boundary, with the still-open list named.
- [ ] `research/fm/refinement_inventory.md` row(s) updated; if fn-9-lift-fm-extraction-to-consume-runtime.1 added a top-level theorem name, the registry alias is added.
- [ ] `research/fm/runtime_model_change_impact.md` `§1 VfrRoute widening` table row revised away from "No immediate Lean churn" to reflect Class-C change landed.
- [ ] `research/fm/aviation_world_extraction_contract.md` status note (`:17-33`) revised — `airspaceProfile` is no longer "intentionally unextracted"; updated wording names what is now extracted and what still isn't.
- [ ] `research/fm/route_bearing_scope.md` runtime note (`:8-19`) revised — `airspaceProfile` is no longer "outside this current widening track".
- [ ] `research/fm/PROJECT_STATUS.md` has a dated changelog entry naming the widening, the new well-formedness conjuncts (including `vfrRouteAirspaceProfileWellFormed` field on `RouteBearingExtractionWellFormed`), the new preservation lemma `vfrRouteAirspaceProfileWellFormed_of_mem` (NOT a full origin/preservation/findCompile triple — fn-9.1 reuses the existing `findCompileVfrRoute_eq_some_of_mem` via `toCompileView` propagation), and the still-open list (predicate strengthening, profile-aware airspace-route interaction, polygonal boundary). <!-- Updated by plan-sync: fn-9.1 shipped one preservation lemma, not a "theorem family" triple -->
- [ ] No FM doc still claims VFR routes extract as waypoint sequences only; `grep -n "waypoint sequences only" research/fm/` returns either zero matches or only matches on intentionally-historical context (e.g. dated changelog entries).
- [ ] STRATEGY.md is NOT edited unless explicitly justified in the PR description (default for fn-9: leave STRATEGY.md alone).

## Done summary

## Evidence
- Commits:
- Tests:
- PRs:
