# Richer Airspace Geometry Widening

## Goal & Context
Widen the FM airspace branch beyond the current graph-backed point-set + transition model into the first proof-visible geometry model.

This is a deliberate semantic widening branch, not a current-model gap fix. The previous campaign closed the delivered current model and recorded richer airspace geometry as intentionally open. The new branch starts from:

- `research/fm/FLOW_NEXT_FRONTIER.md`
- `research/fm/FLOW_NEXT_GAP_LEDGER.md`
- `research/fm/parity_inventory.md`
- `research/fm/refinement_inventory.md`
- `research/fm/aviation_world_extraction_contract.md`
- `docs/design/path-network-design.md`
- `docs/design/clearance-model-design.md`
- `research/fm/lean/CertifiedAtc/GreenfieldAirspaceWorldBackedDeliveredCurrentShape.lean`
- the Kotlin airspace runtime model in `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/ProcedureAndAirspaceModel.kt`, `WorldAirspaceValidation.kt`, `InstructionResolution.kt`, and `CompletionEvaluation.kt`

The current delivered FM boundary is explicit: airspace volumes are proof-visible as `ScopedAirspaceVolumeSource.points`, route interaction is computed from point membership, and boundary geometry / segmented VFR route profiles are intentionally not extracted into Lean. The first widening target is to make boundary rings/vertices/edges and segmented route-airspace facts proof-visible without pretending to solve general continuous geometry in one step.

## Architecture & Data Models
The current Kotlin model already carries:

- `AirspaceVolume.memberPoints`
- optional `AirspaceVolume.boundary : AirspaceBoundary`
- `BoundaryRing.points`
- optional `VfrRoute.airspaceProfile`
- `VfrRouteAirspaceProfile.InVolume`, `InClass`, and `Segmented`

The current Lean model carries only:

- `ScopedAirspaceVolumeSource.id`
- `ScopedAirspaceVolumeSource.points`
- member-point route interaction helpers: `airspaceRouteInsidePoints`, `airspaceRouteEntryTransitions`, `airspaceRouteExitTransitions`, and `airspaceRouteTouches`

The planned first geometry slice should add proof-visible structure equivalent to:

- finite boundary rings and closed boundary edges;
- a well-formedness condition that boundary vertices are included in explicit member points;
- segmented route-airspace profiles whose segment endpoints align with route waypoint pairs and named airspace volumes;
- transition facts that can be justified either by explicit point membership or by declared segment/profile structure.

The plan must keep the existing delivered branch closed while adding new theorem surfaces. Do not replace the current `WORLD_BACKED_COMPLETE` airspace branch until the new geometry branch has its own root-gated package and docs.

## API Contracts
Flow-Next owns execution state. Use:

```bash
nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl ready --epic fn-4-richer-airspace-geometry-widening --json
nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl start <task-id> --json
nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl done <task-id> --summary ... --evidence ... --json
nix --extra-experimental-features 'nix-command flakes' develop path:. -c flowctl validate --all --json
```

Lean gates:

```bash
nix --extra-experimental-features 'nix-command flakes' develop path:. -c bash -lc 'cd research/fm/lean && lake build CertifiedAtc'
```

Kotlin gates are required for any task that changes runtime model, validation, resolution, or completion behavior:

```bash
./gradlew :core:allTests :protocol:allTests
```

Full build is required before closing the epic if runtime code changed:

```bash
./gradlew build
```

## Edge Cases & Constraints
Do not hand-wave from boundary rings to continuous point-in-polygon membership. If a finite geometry predicate is added, it must be explicitly named as finite/proof-side and its limitations documented. General numeric geometry should remain out of scope unless a task explicitly defines the oracle and test fixtures.

Do not silently weaken the current point-set model. Existing current-model theorem aliases and drift tests must stay valid. If the new geometry branch changes a delivered branch classification, update `parity_inventory.md`, `refinement_inventory.md`, and `GreenfieldDeliveredRefinement.lean` in the same task.

Do not add `sorry`, `admit`, or new axioms. Do not create fallback defaults for malformed geometry. If a route profile, boundary ring, or volume reference is ill-formed and the type system allows it, expose that as a well-formedness precondition or typed runtime validation failure.

## Acceptance Criteria
- [ ] Flow-Next validates with `flowctl validate --all --json`.
- [ ] The branch scope document defines the first closed geometry slice and explicitly excludes general continuous geometry unless separately justified.
- [ ] Runtime drift tests either already cover or are expanded to cover boundary-ring membership and segmented VFR route profile alignment.
- [ ] Lean adds proof-visible airspace geometry primitives and well-formedness without breaking the existing delivered airspace theorem surface.
- [ ] The widened geometry branch has root `lake build CertifiedAtc` evidence.
- [ ] If Kotlin runtime semantics change, `./gradlew :core:allTests :protocol:allTests` evidence is recorded.
- [ ] Final docs/inventories state what is newly closed, what remains intentionally open, and what would reopen the branch.

## Boundaries
Out of scope unless explicitly promoted into a later epic:

- full continuous point-in-polygon geometry over arbitrary coordinates;
- numeric geometry robustness, tolerances, winding rules, or geodesic projection correctness;
- runtime physics changes;
- controller phraseology or regulatory claims beyond existing airspace-clearance semantics;
- broad operational-sector or published-VFR-procedure semantics not needed for the first geometry slice.

## Decision Context
This branch is the natural next FM widening because the runtime already carries richer airspace facts while the current proof-visible model intentionally ignores them. The first useful proof step is not to prove real-world geometry in full; it is to expose finite boundary/profile structure to Lean and prove that airspace resolution/completion can consume those richer facts honestly.

## Review Considerations
FP / type safety: Lean structures and predicates must make the new geometry assumptions explicit. Avoid `error`-style impossibility claims for states the types allow. In Kotlin, malformed boundary/profile data must be validation output or construction failure, not silently accepted.

Test architecture: use root `lake build CertifiedAtc` for proof changes. Use focused Kotlin world/resolution/completion tests when runtime behavior changes. Prefer integration-style world-validation fixtures over tests that only restate constructor invariants.

Impact: this branch couples `RouteBearingExtraction.lean`, `GreenfieldResolved.lean`, and the world-backed airspace modules. It may also couple Kotlin world validation and instruction resolution if the runtime route/airspace interaction oracle changes. Reversal is to leave the current delivered `WORLD_BACKED_COMPLETE` point-set branch intact and keep the new geometry branch as a separate optional theorem package until it is fully closed.

Operational correctness: do not introduce new ATC legal claims. Existing controlled-airspace entry/restriction instructions remain the operational scope; richer geometry changes only the proof-visible world model unless a later task adds source-cited operational semantics.
