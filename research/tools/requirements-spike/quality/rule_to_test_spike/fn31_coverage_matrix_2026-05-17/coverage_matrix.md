# FN31 Coverage Matrix

## Selection

Primary slice is aerodrome circuit, final approach, landing-clearance, and go-around behavior because it has accepted ICAO/CAP source units and existing simulator coverage in G1/G3/controller tests. Contrast rows cover readback, conditional clearance, and review-only operational guidance so the spike does not overfit to executable examples.

## Recommended Vertical Slice

Family: `circuit_go_around_after_landing_clearance`

This family has multiple high-confidence accepted source units, maps onto existing TowerArrival and G3a tests, and includes executable, partially executable, and review-only boundaries. It is therefore a good first vertical slice for source-unit-to-test exploration.

## Coverage Counts

- `design_blocked`: 2
- `executable_existing`: 10
- `manual_translatable`: 3
- `review_only`: 1

## Matrix

| Source unit | Family | Coverage | Simulator concept | Existing tests | Notes |
| --- | --- | --- | --- | --- | --- |
| `cap413-extracted::ch4_4_50_to_4_60::36206ebf73ce35e5` | final_approach_landing_clearance | executable_existing | ContinueApproach versus ClearedToLand/ClearedTouchAndGo distinction | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionContinueApproachTest.kt`<br>`controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/ObstructionContinueApproachSpec.kt` | Strong vertical-slice candidate: behavior is already represented by distinct instructions and post-clearance/pre-clearance regulation split tests. |
| `cap413-extracted::ch4_4_50_to_4_60::3e867581f0f695b2` | final_approach_landing_clearance | executable_existing | Obstruction-driven continue-approach before landing clearance | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionContinueApproachTest.kt`<br>`controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/ObstructionContinueApproachSpec.kt` | Good test oracle: obstruction present before landing clearance should produce continue-approach companion handling, not landing clearance. |
| `cap413-extracted::ch4_4_61_to_4_69::ed3edf669048bfab` | go_around_obstruction | executable_existing | GoAround instruction with traffic/obstruction reason | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionTest.kt`<br>`controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/ObstructionGoAroundSpec.kt` | Existing tests assert obstruction-driven go-around behavior; vertical slice can check whether emitted reason/trace evidence is sufficiently source-unit-grounded. |
| `cap413-extracted::ch4_4_61_to_4_69::452877f6b21af73b` | pilot_initiated_go_around | executable_existing | ReportEvent.GoingAround / pilot go-around transmission | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotTrainedGoAroundTest.kt`<br>`sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveCrosswindTest.kt`<br>`sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveTailwindTest.kt` | Good source-unit anchor for the named radio witness already used by G3 tests. |
| `cap413-extracted::ch4_4_61_to_4_69::2adeb25694ea02ff` | post_go_around_recovery | executable_existing | Post-go-around recovery to circuit / AwaitDownwind | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotTrainedGoAroundTest.kt`<br>`controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/GoAroundSequencingSpec.kt` | Existing tests check recovery Downwind and commitment regression; vertical slice can make the source-unit evidence explicit. |
| `icao9432-extracted::go_around_4_8_en::c3581d40a48406bb` | post_go_around_recovery | executable_existing | Post-go-around VFR circuit continuation | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotTrainedGoAroundTest.kt`<br>`sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aPilotReactiveTailwindTest.kt` | Duplicates the CAP413 VFR recovery idea from ICAO9432; useful for citation redundancy and source-priority comparison. |
| `icao9432-extracted::go_around_4_8_en::43c33a8e74b02873` | instrument_missed_approach | design_blocked | IFR missed approach procedure | None identified | Accepted and testable in principle, but current golden surface is VFR-heavy; this should probably wait for IFR/missed-approach procedure modeling. |
| `icao4444-extracted::aerodrome_phraseologies_circuit_approach_12_3_4_13_to_12_3_4_15::0029194ff378f5da` | circuit_position_reporting | executable_existing | ReportEvent.Downwind / ReportEvent.Final | `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/EventDerivationSpec.kt`<br>`sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt` | Good bridge from source unit to protocol event and golden trace. |
| `icao9432-extracted::final_approach_landing_4_7_en::00baaf3c55155044` | final_position_reporting | manual_translatable | Final report timing and landing-clearance readiness | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`<br>`sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3aRunwayObstructionContinueApproachTest.kt` | Current tests observe FINAL/landing behavior but do not appear to assert the 4 NM timing threshold directly. |
| `icao9432-extracted::final_approach_landing_4_7_en::4c698a5ad52a30e4` | final_position_reporting | manual_translatable | Long-final report timing | None identified | Likely needs trace-query support for distance-at-report before becoming a clean executable test. |
| `cap413-extracted::ch4_4_50_to_4_60::c7d312fab4599611` | landing_clearance_phraseology | executable_existing | Runway field on ClearedToLand/ClearedTouchAndGo/ClearedLowApproach | `core/src/commonTest/kotlin/xyz/easiersaid/twr/core/clearance/DeliveredMetadataParityTest.kt`<br>`sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt` | May already be structurally enforced by instruction constructors; useful as a boundary case where a new test may add little value. |
| `icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4` | touch_and_go_training | executable_existing | ClearedTouchAndGo and circuit-training outcome | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt`<br>`controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/ObstructionGoAroundSpec.kt` | Executable, but less central than go-around recovery for the next task. |
| `cap413-extracted::ch4_4_61_to_4_69::a28976f537ec29ab` | go_around_radio_discipline | review_only | Radio phrase brevity / transmission-content policy | None identified | Important operational guidance but not a clean deterministic sim test without a phrase-length/content policy. |
| `cap413-extracted::ch4_4_61_to_4_69::e8d9a53dcab101bf` | after_landing_taxi_timing | manual_translatable | After-landing taxi instruction timing | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G0LowgGoldenTest.kt`<br>`sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G1TwoAircraftCircuitsTest.kt` | Goldens cover after-landing taxi flow, but a specific no-early-taxi invariant would need trace timing assertions. |
| `icao4444-extracted::readback_4_5_7_5::eb9186d5355d8914` | runway_instruction_readback | executable_existing | Coordination expectedReadback for runway instructions | `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/CoordinationLifecycleSpec.kt`<br>`controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/CoordinationsCleanupSpec.kt` | Excellent later family, but cross-cutting enough that it should not be the first vertical slice. |
| `eppls-extracted::ch12_key_readbacks_takeoff_cleared_word_limits::a49db487a4dd3164` | conditional_clearance_readback | design_blocked | Conditional clearance readback ordering | None identified | Useful contrast: source unit is precise, but the sim likely needs richer phrase/readback sequence representation before a meaningful test. |

## Review Notes

- FP / type safety: this task adds research artifacts only. No production rule execution shape is introduced.
- Test architecture: coverage classes distinguish existing executable checks from manual translation, design-blocked claims, and review-only guidance.
- Impact: simulator code remains decoupled from the registry; source units are evidence for planning only.
- Operational correctness: every row retains canonical id, source item ids, exact quotes, and provenance line range in `coverage_matrix.json`.
- Reversibility: artifacts are isolated under this FN31 quality directory.
