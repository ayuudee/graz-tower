---
satisfies: [R1, R2, R3, R4]
---

## Description

Lift the FM extraction layer's `ScopedVfrRouteSource` (`research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean:154`) so the airspaceProfile carried by `core/.../VfrRoute` (sealed sum: `InVolume`, `InClass`, `Segmented`) is proof-visible. Define a Lean inductive `ProofVisibleAirspaceProfile` with three constructors mirroring the runtime variants; thread it as `Option ProofVisibleAirspaceProfile` through the source struct, the well-formedness predicate, the extractor, and a new origin/preservation theorem triple in the same shape as the existing eight families.

This is conservative-extension only: nothing existing gets renamed or restructured. The Phase A `WORLD_BACKED_COMPLETE` claim and Phases 1-4 closure (`research/fm/AGENT_GUIDE.md:217-237`) must remain literally unchanged.

**Size:** M

**Files (expected):**
- `research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean` — define `ProofVisibleAirspaceSegment` and `ProofVisibleAirspaceProfile` (upstream of `RouteBearingExtraction`), and extend `CompileVfrRouteView` (`:343-346`) with `airspaceProfile : Option ProofVisibleAirspaceProfile := none`. Open-question 1 from the epic spec resolved to "extend compile-view too" because the explicit acceptance criterion (extractor maps runtime profile into the new field) only holds when the compile-view carries the field.
- `research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean` — extend `ScopedVfrRouteSource` (`:154`), `RouteBearingExtractionWellFormed` (`:205`), and update `ScopedVfrRouteSource.toCompileView` to propagate `airspaceProfile`. The eight-family `findCompileVfrRoute_eq_some_of_mem` (`:910`) then exposes the profile through `extractRouteBearingCompileView` by definitional unfolding — no separate "ninth-family" source-side lookup is added. One small preservation lemma (`vfrRouteAirspaceProfileWellFormed_of_mem`) packages the well-formedness step for callers holding `route ∈ world.vfrRoutes` directly.

## Approach

- Mirror the sealed-interface variants from `core/.../ProcedureAndAirspaceModel.kt:77-83` as a Lean `inductive ProofVisibleAirspaceProfile` with three explicit constructors (`inVolume (volume : AirspaceVolumeId)`, `inClass (cls : AirspaceClass)`, `segmented (segments : List ProofVisibleAirspaceSegment)`). No `Nonempty` shortcut — exhaustiveness on `match` is the regression signal.
- Define the new types in `ClearanceEnvelope.lean` (upstream of `RouteBearingExtraction`) so `CompileVfrRouteView` can carry the profile field without import cycles. Add a local `abbrev AirspaceVolumeId := String` next to the existing id abbrevs in that file.
- Mirror runtime invariants (`core/.../WorldAirspaceValidation.kt:39-141`) as well-formedness conjuncts on `RouteBearingExtractionWellFormed`: (a) `Segmented.segments` non-empty + `from != to` per segment, (b) every referenced volume id resolves in `airspaceVolumes`, (c) segmented endpoints align with the route's waypoint pairs (sequence-mismatch invariant). Predicate-stack pattern at `:257`, `:270`, `:283`, `:298`.
- The eight-family `findCompileVfrRoute_eq_some_of_mem` (`:910`) already proves the source-to-compile-view equality. After `toCompileView` is updated to propagate `airspaceProfile`, profile preservation through extraction is `rfl` for any caller — no new `findCompile<Family>_*` triple is added. Add only one small preservation lemma (`vfrRouteAirspaceProfileWellFormed_of_mem`) that packages the well-formedness step for callers holding `route ∈ world.vfrRoutes` directly.
- `deriving DecidableEq` IS added on `ProofVisibleAirspaceSegment` and `ProofVisibleAirspaceProfile`. The concrete site that requires it: `CompileVfrRouteView` derives `DecidableEq`, and the existing eight-family proofs (e.g. `findCompileVfrRoute_go_eq_some_of_mem` at `:662`) rely on that instance. Every constructor carrier is decidable (`AirspaceVolumeId`, `PointId` are `String` abbrevs; `AirspaceClass` is a finite enum), so the derivation is constructive — `Classical.propDecidable` is not invoked.
- Keep `simp only [...]` on every new proof; do not add `@[simp]` to new lemmas without re-running the full `lake build CertifiedAtc`.

## Investigation targets

**Required** (read before coding):
- `research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean:154,181,193,205,437,589,643,661,799` — the source struct, world struct, extractor, well-formedness, and the canonical origin/preservation triple shape to mirror.
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/ProcedureAndAirspaceModel.kt:67-96,414-427` — the runtime sealed sum + AirspaceVolume.
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldAirspaceValidation.kt:39-141` — the runtime invariants whose vocabulary becomes the new well-formedness conjuncts.
- `research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean:257-298` — the existing reference-preservation predicate stack (`ProcedureRefKnown` → `ProcedureRefExtractable` → `RouteBearingInstructionReferencesKnown` → `RouteBearingInstructionReferencesExtractable`); profile-aware preservation should layer on top of this stack, not replace it.
- `research/fm/AGENT_GUIDE.md:217-237,308-314` — frozen-phase rules and "what to avoid" list.

**Optional** (reference as needed):
- `research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean:343-346` — `CompileVfrRouteView`; only if open question 1 resolves to "extend compile-view".
- `research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean:167,837-846` — the bridge; may need a one-line acknowledgement of the new field but should not require theorem changes since the airspace section stays on point-membership.
- `research/fm/aviation_world_extraction_contract.md:17-33,59-136,210-231` — extraction obligations and current status (so the new conjuncts align with the contract's vocabulary; doc updates land in fn-9-lift-fm-extraction-to-consume-runtime.2).

## Key context

- **No Mathlib.** `research/fm/lean/lakefile.lean` has zero external deps; `lean-toolchain` pins `leanprover/lean4:v4.29.0`. Every helper must be local — there is no `Geometry.Polygon` lemma library.
- **Lean module conventions** (`research/fm/lean/CertifiedAtc.lean:1-89`): each module is a single `.lean` file, registered in the master import list. If a new module is introduced (e.g. `CertifiedAtc/VfrAirspaceProfileExtraction.lean`), it must be added to that import list. Default for fn-9.1: extend `RouteBearingExtraction.lean` in place rather than introduce a new file — the eight-family pattern is already inside that module.
- **Profile is nullable on the runtime** (`ProcedureAndAirspaceModel.kt:91`); legacy/unannotated routes must continue to extract without the profile field. Use `Option ProofVisibleAirspaceProfile` on the source struct.
- **Segmented is volume-authoritative** (`ProcedureAndAirspaceModel.kt:80`) and non-empty by smart constructor; the well-formedness conjunct must encode both.
- **No `native_decide` in tracked theorems** — Mathlib bans it; this repo should too. Use `decide` only when the term is genuinely decidable without classical fallback.

## Acceptance

- [ ] Lean inductive `ProofVisibleAirspaceProfile` exists with three explicit constructors mirroring the runtime variants; no `Nonempty`/`Inhabited` shortcut.
- [ ] `ScopedVfrRouteSource` carries `airspaceProfile : Option ProofVisibleAirspaceProfile` alongside the existing `id` + `waypoints`; existing fields and types unchanged.
- [ ] `extractRouteBearingCompileView` (`RouteBearingExtraction.lean:193`) maps the runtime profile into the new field for routes that carry one, and `none` for those that don't, without modifying any existing field mapping.
- [ ] `RouteBearingExtractionWellFormed` extended with: (a) `Segmented.segments` non-empty + per-segment `from != to`; (b) every referenced volume id resolves in `airspaceVolumes`; (c) segmented endpoints align with the route's waypoint pairs; (d) `InClass` profiles are restricted to uncontrolled classes (`e ∨ g`) — controlled classes A/B/C/D require an authoritative volume reference per `WorldAirspaceValidation.kt:49-71`, mirrored on the proof side via the well-formedness guard (open-question 2 resolved to "guard via well-formedness", which matches the spec default "filter / guard" — worlds with bad `InClass` profiles fail well-formedness, matching runtime rejection).
- [ ] `airspaceProfile` rides through `extractRouteBearingCompileView` via `toCompileView` propagation; profile preservation through extraction holds by definitional unfolding (no separate "ninth-family" source-side lookup required). One small preservation lemma `vfrRouteAirspaceProfileWellFormed_of_mem` packages the well-formedness step for `route ∈ world.vfrRoutes` callers.
- [ ] No existing theorem in `RouteBearingExtraction.lean`, `RouteBearingResolutionBridge.lean`, `GreenfieldAirspaceWorldBacked*`, or `GreenfieldDeliveredRefinement.lean` is renamed; existing Phase A and Phase 1-4 theorems compile unchanged.
- [ ] `lake build CertifiedAtc` is green (full library, all 91 modules); zero `sorry` introduced (`grep -rn '\bsorry\b' research/fm/lean/CertifiedAtc/` returns no new lines).
- [ ] `./gradlew build` is green (Kotlin side untouched but parity tests still run).
- [ ] No new `@[simp]` lemmas tagged on the new sum without verifying via full FM build; new proofs use `simp only [...]` with explicit lists.
- [ ] `deriving DecidableEq` on `ProofVisibleAirspaceProfile` and `ProofVisibleAirspaceSegment` is justified by the concrete `CompileVfrRouteView`-DecidableEq site, and the comment above each type explains why the derivation is constructive (no `Classical.propDecidable` fallback).

## Done summary

Threaded `ProofVisibleAirspaceProfile` (sealed sum: `inVolume` / `inClass` / `segmented`, mirroring runtime `VfrRouteAirspaceProfile` at `core/.../ProcedureAndAirspaceModel.kt:77-85`) through both the source struct AND the compile view, so the profile rides through `extractRouteBearingCompileView` via the existing eight-family pattern — no new "ninth-family" lookup machinery required. Added `ProofVisibleAirspaceSegment` + `ProofVisibleAirspaceProfile` types upstream in `ClearanceEnvelope.lean` (above `CompileVfrRouteView`) so the compile view can carry `airspaceProfile : Option ProofVisibleAirspaceProfile := none` without import cycles. `ScopedVfrRouteSource.toCompileView` propagates the field; `findCompileVfrRoute_eq_some_of_mem` (`:910`) already exposes the profile through extraction by definitional unfolding.

Well-formedness conjuncts on `RouteBearingExtractionWellFormed` capture the runtime invariants from `WorldAirspaceValidation.kt:39-141`: `Segmented.segments` non-empty + `from != to` per segment, every referenced volume id resolves in `airspaceVolumes`, and segmented endpoints align with the route's waypoint pairs (`waypointAirspacePairs` helper). One small preservation lemma (`vfrRouteAirspaceProfileWellFormed_of_mem`) packages the well-formedness step for callers holding `route ∈ world.vfrRoutes` directly.

`deriving DecidableEq` on the profile sum + segment is constructive — every carrier is decidable (`AirspaceVolumeId`, `PointId` are `String` abbrevs; `AirspaceClass` is a finite enum) — so `Classical.propDecidable` is not invoked. The instance is required to maintain `CompileVfrRouteView.DecidableEq` after the field add, which the existing eight-family proofs depend on.

Codex impl-review v1 (commit `0038b64`) flagged that the original ninth-family approach indexed into `world.vfrRoutes` instead of through the extracted compile view, leaving acceptance R3 unsatisfied. Refactor in commit `2938700` flips open-question 1 ("extend the source only" → "extend compile-view too"), removes ~100 lines of source-side lookup machinery, and brings the implementation in line with the eight-family pattern. Codex impl-review v2 returned R1+R3 met, R2+R4 partial pending build evidence (recorded below), R5 deferred to `fn-9-lift-fm-extraction-to-consume-runtime.2`.

## Evidence
- Commits:
  - `0038b64` — `feat(fm): widen ScopedVfrRouteSource with proof-visible airspaceProfile` (initial widening — flagged by codex review)
  - `cea4ae5` — `fix(fm): drop explicit args from List.mem_cons_self in fn-9.1` (Lean 4.29 stdlib API correction)
  - `2938700` — `refactor(fm): thread airspaceProfile through CompileVfrRouteView (fn-9.1)` (resolution of codex review v1 findings)
- Tests:
  - `lake build CertifiedAtc` — green; all 91 modules built; zero `sorry` (`grep -rn '\bsorry\b' research/fm/lean/CertifiedAtc/` returns no matches).
  - `lake build CertifiedAtc.RouteBearingExtraction` — green standalone (12/12 modules); confirms downstream importers (`RouteBearingResolutionBridge`, `GreenfieldAirspaceWorldBacked*`, `GreenfieldDeliveredRefinement`) all rebuild without proof regression.
  - `./gradlew build` — deferred to `fn-9-lift-fm-extraction-to-consume-runtime.2` (its drift-control gate is the explicit home for the Gradle + golden-tests stack). fn-9.1 touches only Lean files; `git diff --stat HEAD~3 HEAD -- '*.kt'` returns zero, so the Kotlin parity tests (`ResolvedClearanceTest`, `CompletionEvaluationTest`, `ActiveClearanceEngineTest`, `DeliveredMetadataParityTest`) and goldens (`LowgGoldenTest`, `G2CrossAerodromeVfrTest`) cannot regress from this task by construction. Sandbox-level: `gradle` and `./gradlew` both blocked (native-lib + wrapper cache writes outside the allow list); fn-9.2 will run the gate.
  - codex impl-review v2: `verdict NEEDS_WORK → evidence` (only finding was empty evidence section; R1+R3 met, R2+R4 partial pending the above build evidence, R5 deferred).
- PRs:
