# fn-4-richer-airspace-geometry-widening.5 Close geometry-backed airspace issuance theorems

## Description
Close the first geometry-backed airspace theorem package.

This task should add the smallest source-level theorem surface that proves the widened geometry/profile facts can support controlled-airspace entry/restriction issuance honestly. It may be a new module or a carefully-scoped extension of the world-backed airspace modules, but it must keep the current delivered branch intact until the geometry branch is packaged and documented.

The theorem surface should cover the chosen first slice only: likely single-step airspace instructions and, if already supported by the prior compound layer without new semantics, the same narrow immediate-adjunct compound shape. Split the task if proof size grows beyond that boundary.
## Acceptance
- [ ] A named Lean theorem package exists for the first geometry-backed airspace slice.
- [ ] The package states source-level reachability/issuance and, where applicable, authority-gated issuance for the scoped geometry branch.
- [ ] Lifecycle/completion behavior remains honest about what geometry can and cannot prove.
- [ ] Existing current-model airspace theorems remain root-gated and are not silently replaced.
- [ ] No `sorry`, `admit`, or new axioms are introduced.
- [ ] `cd research/fm/lean && lake build CertifiedAtc` passes.
## Done summary
Added the first named geometry-backed airspace theorem package. `GreenfieldAirspaceGeometryBackedCurrentShape.lean` proves that declared VFR route-profile touch witnesses lower to the existing world-backed route/airspace interaction helper while preserving point-set entry/exit transition computation, then packages single-step and narrow-compound controlled-airspace permission issuance behind reachable and authority-gated source-level theorems. The package is root-imported and documented separately from the delivered point-set branch.
## Evidence
- Commits:
- Tests: nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc', nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl validate --all --json, git diff --check -- research/fm/README.md research/fm/PROJECT_STATUS.md research/fm/airspace_geometry_scope.md research/fm/lean/CertifiedAtc.lean research/fm/lean/CertifiedAtc/GreenfieldAirspaceGeometryBackedCurrentShape.lean research/fm/lean/CertifiedAtc/GreenfieldAirspaceGeometry.lean research/fm/lean/CertifiedAtc/RouteBearingExtraction.lean research/fm/lean/CertifiedAtc/GreenfieldResolved.lean research/fm/lean/CertifiedAtc/GreenfieldResolution.lean research/fm/lean/CertifiedAtc/RouteBearingResolutionBridge.lean research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCurrentShape.lean research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedCompound.lean, grep -R -nE '(^|[^A-Za-z0-9_])(sorry|admit|axiom)([^A-Za-z0-9_]|$)' <touched Lean files> # no matches
- PRs: