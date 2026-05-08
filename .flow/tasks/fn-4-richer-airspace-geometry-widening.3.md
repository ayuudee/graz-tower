# fn-4-richer-airspace-geometry-widening.3 Introduce Lean finite airspace geometry primitives

## Description
Introduce proof-visible finite airspace geometry primitives in Lean without changing the existing delivered airspace branch semantics.

Expected work is a small module such as `CertifiedAtc/GreenfieldAirspaceGeometry.lean`, imported by root `CertifiedAtc` when ready. It should define boundary rings, boundary vertices/edges, and well-formedness predicates that can be consumed by later world-backed airspace modules.

Keep this layer finite and structural. It should not claim numeric polygon containment unless task 1 explicitly scoped that oracle.
## Acceptance
- [ ] Lean has explicit finite geometry structures/helpers for airspace boundary rings, materialized closed edges, and boundary vertex membership.
- [ ] Well-formedness states the relationship between boundary vertices and explicit airspace member points.
- [ ] Existing delivered airspace theorem aliases still build unchanged.
- [ ] No `sorry`, `admit`, or new axioms are introduced.
- [ ] `cd research/fm/lean && lake build CertifiedAtc` passes in the Nix dev shell.
## Done summary
Added CertifiedAtc.GreenfieldAirspaceGeometry as a standalone finite structural airspace-geometry layer and imported it from the root CertifiedAtc build. The module defines boundary ring/source structures, closed-point and closed-edge materialization helpers, boundary vertex/edge flattening helpers, and well-formedness predicates tying boundary vertices back to explicit airspace member points. Existing delivered airspace theorem files were left unchanged.
## Evidence
- Commits:
- Tests: grep -n "sorry\|admit\|axiom" research/fm/lean/CertifiedAtc/GreenfieldAirspaceGeometry.lean || true, git diff --check -- research/fm/lean/CertifiedAtc/GreenfieldAirspaceGeometry.lean research/fm/lean/CertifiedAtc.lean core/src/commonTest/kotlin/xyz/easiersaid/twr/core/world/WorldConstructionTest.kt core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/CompletionEvaluationTest.kt research/fm/airspace_geometry_runtime_audit.md research/fm/airspace_geometry_scope.md .flow, nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc', nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl validate --all --json
- PRs: