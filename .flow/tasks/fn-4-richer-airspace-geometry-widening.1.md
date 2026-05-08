# fn-4-richer-airspace-geometry-widening.1 Scope the first proof-visible airspace geometry slice

## Description
Define the first closed slice for richer airspace geometry before any Lean or Kotlin implementation work.

Start from the current closed point-set model in `GreenfieldAirspaceWorldBackedDeliveredCurrentShape.lean`, the runtime `AirspaceBoundary` / `VfrRouteAirspaceProfile` model, and the extraction contract. Produce or update a scope note, preferably `research/fm/airspace_geometry_scope.md`, that states the exact first proof-visible geometry target.

The default target is finite and explicit: boundary rings, boundary vertices, materialized boundary edges, and segmented VFR route-airspace profile facts. Do not claim arbitrary continuous point-in-polygon inference unless this task explicitly defines the oracle, limitations, and tests.

Include an impact assessment before downstream tasks: Lean modules to touch, Kotlin runtime seams to audit, proof gates, reversal plan, and what remains intentionally open.
## Acceptance
- [ ] `research/fm/airspace_geometry_scope.md` exists or is updated with the first closed geometry slice.
- [ ] The scope distinguishes explicit member-point semantics, boundary-ring/edge semantics, segmented route-profile semantics, and any intentionally excluded continuous geometry.
- [ ] The note names the Lean modules and Kotlin runtime files expected to change or be audited.
- [ ] Review considerations cover FP/type safety, test architecture, impact/reversal, and operational correctness.
- [ ] `flowctl validate --all --json` passes.
## Done summary
Created research/fm/airspace_geometry_scope.md to define the first finite proof-visible airspace geometry slice. The note distinguishes current explicit member-point semantics, boundary ring/edge semantics, segmented VFR route-profile semantics, and explicitly excluded continuous geometry. It records expected Lean modules, Kotlin audit files, proof/runtime gates, impact assessment, reversal path, and review considerations.
## Evidence
- Commits:
- Tests: git diff --check -- research/fm/airspace_geometry_scope.md .flow, nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl validate --all --json
- PRs: