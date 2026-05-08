# fn-4-richer-airspace-geometry-widening.4 Lift geometry into extraction and resolution

## Description
Lift the task-3 geometry primitives into the proof-side extraction and resolution world.

Likely seams include `RouteBearingExtraction.lean`, `GreenfieldResolved.lean`, `GreenfieldResolution.lean`, and the current `GreenfieldAirspaceWorldBacked*` modules. The goal is to make the chosen geometry facts available to world-backed airspace resolution while preserving the current point-set delivered branch.

Audit every projection, constructor, and well-formedness theorem affected by adding fields to proof-side world structures. If an equivalent Kotlin field is added or changed, audit every data-class copy/mutation site as required by the repo commandments.
## Acceptance
- [ ] Proof-side airspace volumes and/or route sources expose the scoped geometry/profile facts selected in task 1.
- [ ] Extraction well-formedness includes the new geometry/profile obligations without weakening existing no-invented-id and current-model obligations.
- [ ] Resolution helpers can compute or consume geometry-backed route/airspace interaction facts without breaking current point-set helpers.
- [ ] Existing root-imported airspace modules still build.
- [ ] `cd research/fm/lean && lake build CertifiedAtc` passes; Kotlin gates also pass if runtime code changed.
## Done summary
Lifted the richer airspace geometry seam into the proof-side extraction/resolution path. Scoped airspace volumes now carry optional finite boundary sources; scoped VFR routes now carry optional route-airspace profiles with extraction well-formedness obligations. The resolution world and route-bearing bridge expose boundary/profile facts, and the world-backed airspace current-shape/compound helpers now preserve the existing point-set route touch path while also accepting declared VFR route-profile touches. Updated FM status/scope docs to reflect the new theorem surface and the remaining continuous-geometry boundary.
## Evidence
- Commits:
- Tests: nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc', nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl validate --all --json, git diff --check -- research/fm/README.md research/fm/PROJECT_STATUS.md research/fm/airspace_geometry_scope.md research/fm/lean/CertifiedAtc/GreenfieldAirspaceGeometry.lean research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean research/fm/lean/CertifiedAtc/GreenfieldResolved.lean research/fm/lean/CertifiedAtc/GreenfieldResolution.lean research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCurrentShape.lean research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCompound.lean, grep -R -nE '(^|[^A-Za-z0-9_])(sorry|admit|axiom)([^A-Za-z0-9_]|$)' <touched Lean files> # no matches
- PRs: