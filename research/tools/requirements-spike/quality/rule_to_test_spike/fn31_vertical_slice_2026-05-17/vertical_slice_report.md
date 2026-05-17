# FN31 Vertical Slice Report

## Scope

This task attempted the first cited source-unit-to-test vertical slice using the
coverage matrix recommendation:

`circuit_go_around_after_landing_clearance`

The slice was chosen because it ties accepted source units to existing simulator
and controller behavior rather than requiring a new production rule model.

## Selected Rule Claims

| Source unit | Rule claim | Outcome |
| --- | --- | --- |
| `cap413-extracted::ch4_4_50_to_4_60::36206ebf73ce35e5` | `CONTINUE` is not landing clearance; the pilot must wait for landing clearance or initiate missed approach. | Existing executable coverage identified in `G3aRunwayObstructionContinueApproachTest` and `ObstructionContinueApproachSpec`. |
| `cap413-extracted::ch4_4_50_to_4_60::3e867581f0f695b2` | If the runway is obstructed at final but expected available in good time, delay landing clearance. | Existing executable coverage identified in the continue-approach obstruction path. |
| `cap413-extracted::ch4_4_61_to_4_69::ed3edf669048bfab` | When runway occupation drives go-around circumstances, inform the pilot of aircraft or vehicles on the runway. | Existing executable coverage identified in obstruction go-around companion-info tests. |
| `cap413-extracted::ch4_4_61_to_4_69::452877f6b21af73b` | Pilot-initiated missed approach uses the phrase `going around`. | Existing executable coverage identified in G3 pilot-trained/reactive go-around traces. |
| `cap413-extracted::ch4_4_61_to_4_69::2adeb25694ea02ff` | VFR aircraft continues into the normal traffic circuit unless instructed otherwise. | Existing executable coverage identified in post-GA recovery and pattern-rejoin tests. |
| `icao9432-extracted::go_around_4_8_en::c3581d40a48406bb` | VFR aircraft going around continues in the normal traffic circuit unless instructed otherwise. | Existing executable coverage identified; overlaps the CAP 413 recovery rule. |

## Executable Evidence Map

- `G3aRunwayObstructionContinueApproachTest`: maps the pre-clearance runway-obstruction case to `ContinueApproach`, companion runway-obstruction information, later `ClearedToLand`, and explicit regulation-set checks for the pre-clearance path.
- `ObstructionContinueApproachSpec`: provides focused controller-level checks for the same pre-clearance continue-approach rule family.
- `ObstructionGoAroundSpec`: maps post-clearance obstruction to `GoAround`, companion obstruction information, commitment regression to `AwaitDownwind`, and supersession of stale landing-class clearances.
- `G3aPilotTrainedGoAroundTest`: maps pilot-authored go-around to `Report(GoingAround)`, no touchdown before the report, post-clearance commitment regression, and recovery to the next circuit.
- `GoAroundSequencingSpec`: maps `Report(GoingAround)` to runway-scoped go-around-in-progress state and expected recovery/traffic sequencing behavior.

## Verification Attempt

Command:

```bash
nix --extra-experimental-features 'nix-command flakes' develop -c ./gradlew :sim:jvmTest --tests 'xyz.easiersaid.twr.sim.G3aPilotTrainedGoAroundTest' --tests 'xyz.easiersaid.twr.sim.G3aRunwayObstructionContinueApproachTest' --tests 'xyz.easiersaid.twr.sim.G3aRunwayObstructionTest' :controller:jvmTest --tests 'xyz.easiersaid.twr.controller.ObstructionGoAroundSpec' --tests 'xyz.easiersaid.twr.controller.GoAroundSequencingSpec'
```

Result: blocked/red.

- `:controller:jvmTest` ran 27 selected tests and failed 3 `GoAroundSequencingSpec` cases:
  - `ARR-EXTEND-FOR-GA fires from same-cycle GoAroundDetected fold (round-trip)`
  - `ARR-TURN-BASE fires once GA belief clears via pattern-rejoin (concrete cancel-output)`
  - `ARR-TURN-BASE fires once GA belief clears via 60s timeout`
- `:sim:compileTestKotlinJvm` failed before selected sim tests could run because `EngineOffKinematicClampSpec.kt` has a backtick test name containing `;`, which Kotlin rejects.

The failures are now tracked in `.plan` as `FN31-TEST-1`.

## Findings

This vertical slice is still the right first slice, but the current tree cannot yet provide a clean green executable result for it. That is a useful spike finding rather than a reason to switch families: the slice immediately found a concrete verification blocker in the go-around sequencing surface.

The source-unit-to-test mapping worked well for:

- identifying already-modeled simulator concepts;
- separating direct executable coverage from manual translation;
- preserving source-unit identity through the analysis;
- locating a small test set that should prove the slice.

The weak point is not source-unit quality. The weak point is current test health on the targeted go-around sequencing path.

## Recommendation

Proceed with this same family after resolving `FN31-TEST-1`. The next useful step is not broader source-unit selection or model generation; it is to make the targeted go-around verification command green, then re-run this vertical slice and record pass/fail at the source-unit level.

Do not scale to generated tests yet. A generated-test campaign would currently amplify an already-red verification surface.

## Review Considerations

- FP / type safety: no production rule execution model was introduced. This remains a research mapping from source-unit records to existing typed simulator behavior.
- Test architecture: the slice correctly prefers existing integration/controller behavior tests over structural tests. The failed command is retained as evidence.
- Impact: no simulator code was coupled to the source-unit registry.
- Operational correctness: each claim remains tied to accepted source-unit canonical ids; the executable tests still use existing `RegulationDatabase` references rather than source-unit ids.
- Reversibility: all vertical-slice artifacts are isolated under this FN31 quality directory.
