---
satisfies: [R1, R2, R3, R4]
---

## Description

Lift the FM extraction layer's `ScopedVfrRouteSource` (`research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean:154`) so the airspaceProfile carried by `core/.../VfrRoute` (sealed sum: `InVolume`, `InClass`, `Segmented`) is proof-visible. Define a Lean inductive `ProofVisibleAirspaceProfile` with three constructors mirroring the runtime variants; thread it as `Option ProofVisibleAirspaceProfile` through the source struct, the well-formedness predicate, the extractor, and a new origin/preservation theorem triple in the same shape as the existing eight families.

This is conservative-extension only: nothing existing gets renamed or restructured. The Phase A `WORLD_BACKED_COMPLETE` claim and Phases 1-4 closure (`research/fm/AGENT_GUIDE.md:217-237`) must remain literally unchanged.

**Size:** M

**Files (expected):**
- `research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean` — extend `ScopedVfrRouteSource` (`:154`), `RouteBearingExtractionWellFormed` (`:205`), the extractor `extractRouteBearingCompileView` (`:193`); add new origin/preservation theorems mirroring the eight-family pattern (`:437`, `:589`, `:661`).
- `research/fm/lean/CertifiedAtc/ClearanceEnvelope.lean` — `CompileVfrRouteView` (`:343-346`) only if open question 1 in the epic spec resolves to "extend compile-view too" (default: do not touch).

## Approach

- Mirror the sealed-interface variants from `core/.../ProcedureAndAirspaceModel.kt:77-83` as a Lean `inductive ProofVisibleAirspaceProfile` with three explicit constructors (`inVolume (volume : Greenfield.AirspaceVolumeId)`, `inClass (cls : Greenfield.AirspaceClass)`, `segmented (segments : List ProofVisibleAirspaceSegment)`). No `Nonempty` shortcut — exhaustiveness on `match` is the regression signal.
- Mirror runtime invariants (`core/.../WorldAirspaceValidation.kt:39-141`) as well-formedness conjuncts on `RouteBearingExtractionWellFormed`: (a) `Segmented.segments` non-empty + `from != to` per segment, (b) every referenced volume id resolves in `airspaceVolumes`, (c) segmented endpoints align with the route's waypoint pairs (sequence-mismatch invariant). Predicate-stack pattern at `:257`, `:270`, `:283`, `:298`.
- Add origin/preservation theorems in the existing triple shape: `findCompile<Family>_go_eq_some_of_mem` / `extractRouteBearingCompileView_<family>_origin` / `findCompile<Family>_eq_some_of_mem`. Pick a family name like `vfrAirspaceProfile`; the goal is parity with how the existing eight families layer onto extraction.
- Reuse `Greenfield.AirspaceVolumeId` (already in scope via `ScopedAirspaceVolumeSource.id` at `:170`); do not introduce a parallel id type.
- No `deriving DecidableEq` on the new sum — leave it off until a concrete site forces it. Avoids `Classical.propDecidable` poisoning downstream `decide` callers.
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
- [ ] `RouteBearingExtractionWellFormed` extended with: (a) `Segmented.segments` non-empty + per-segment `from != to`; (b) every referenced volume id resolves in `airspaceVolumes`; (c) segmented endpoints align with the route's waypoint pairs.
- [ ] Origin/preservation triple added in the existing eight-family shape; one entry registered alongside the existing eight in the well-formedness/origin/preservation pattern.
- [ ] No existing theorem in `RouteBearingExtraction.lean`, `RouteBearingResolutionBridge.lean`, `GreenfieldAirspaceWorldBacked*`, or `GreenfieldDeliveredRefinement.lean` is renamed; existing Phase A and Phase 1-4 theorems compile unchanged.
- [ ] `nix-shell -p lean4 --run 'cd research/fm/lean && lake build CertifiedAtc'` is green; zero `sorry` introduced (`grep -n sorry research/fm/lean/CertifiedAtc/*.lean` returns no new lines).
- [ ] `./gradlew build` is green (Kotlin side untouched but parity tests still run).
- [ ] No new `@[simp]` lemmas tagged on the new sum without verifying via full FM build; new proofs use `simp only [...]` with explicit lists.
- [ ] No `deriving DecidableEq` on `ProofVisibleAirspaceProfile` unless a downstream proof concretely requires it (and then locally only).

## Done summary

## Evidence
- Commits:
- Tests:
- PRs:
