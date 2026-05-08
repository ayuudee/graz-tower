# fn-4-richer-airspace-geometry-widening.2 Audit runtime airspace geometry oracle and drift tests

## Description
Audit the Kotlin runtime oracle for the geometry slice chosen in task 1 and add drift tests only where they prove real behavior.

Relevant runtime seams include `ProcedureAndAirspaceModel.kt`, `WorldAirspaceValidation.kt`, `WorldIndexBuilders.kt`, `WorldValidation.kt`, `InstructionResolution.kt`, `CompletionEvaluation.kt`, and existing tests such as `WorldConstructionTest`, `ResolvedClearanceTest`, `CompletionEvaluationTest`, and `ActiveClearanceEngineTest`.

The audit should confirm whether current runtime behavior already has the necessary oracle for boundary vertices and segmented VFR route profiles. If behavior changes, tests must fail loudly on the old behavior; no skip lists or catch-all fallbacks.
## Acceptance
- [ ] Existing and missing runtime drift gates for airspace boundary rings, boundary vertices in `memberPoints`, segmented VFR route alignment, unknown volume references, and route/airspace interaction are documented.
- [ ] Any added Kotlin tests exercise behavior through world construction, resolution, or completion paths rather than only constructor invariants.
- [ ] If runtime semantics are changed, `./gradlew :core:allTests :protocol:allTests` passes and evidence is recorded.
- [ ] If no runtime change is needed, the task records why existing behavior is sufficient for the Lean widening.
- [ ] No malformed geometry/profile case is silently accepted.
## Done summary
Completed the runtime airspace geometry oracle audit and unblocked its verification gate. Added research/fm/airspace_geometry_runtime_audit.md documenting existing and missing drift gates, added behavior tests for implicit airspace boundary closing-edge validation and member-point-only airspace completion observation, and migrated stale core tests from the removed TaxiTo leaf to TaxiToHoldingPoint/TaxiToStand so core/protocol test compilation matches the split taxi instruction model.
## Evidence
- Commits:
- Tests: grep -R "import xyz.easiersaid.twr.protocol.TaxiTo$\|[^A-Za-z0-9_]TaxiTo(" -n core/src/commonTest/kotlin core/src/commonMain/kotlin protocol/src/commonTest/kotlin protocol/src/jvmTest/kotlin || true, git diff --check -- core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/DeliveredMetadataParityTest.kt core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/ResolvedClearanceTest.kt core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/CompletionEvaluationTest.kt core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/GroundMovementProgressionTest.kt core/src/commonTest/kotlin/xyz/easiersaid/twr/core/resolution/InstructionResolutionTest.kt core/src/commonTest/kotlin/xyz/easiersaid/twr/core/world/WorldConstructionTest.kt research/fm/airspace_geometry_runtime_audit.md, ./gradlew :core:jvmTest --tests 'xyz.easiersaid.twr.core.world.WorldConstructionTest' --tests 'xyz.easiersaid.twr.core.clearance.CompletionEvaluationTest', ./gradlew :core:allTests :protocol:allTests
- PRs: