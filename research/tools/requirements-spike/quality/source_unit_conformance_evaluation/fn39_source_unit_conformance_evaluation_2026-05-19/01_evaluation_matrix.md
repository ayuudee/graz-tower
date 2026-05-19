# FN39 evaluation matrix and evidence pack

## Scope

This evaluation compares two candidate strategies for source-unit conformance
testing:

- **Candidate A: FN37 `SourceUnitSpec`** — source-unit-owned specs with domain
  dimensions, witness / partition / fuzz probes, non-vacuity counters,
  model-gap reporting, and formatted reports.
- **Candidate B: FN38 conformance monitors** — scenarios produce black-box
  traces; independent source-backed monitors observe those traces with
  applicability predicates, requirements, adequacy rules, and projection gaps.

The representative cases are fixed:

1. readback obligations;
2. taxi clearance / runway-use boundary;
3. touch-and-go / full-stop intent;
4. essential aerodrome information timing;
5. critical-phase radio silence except safety necessity.

## Evidence pack

### Prior artifacts

- FN37 plan: `.flow/specs/fn-37-source-unit-conformance-harness-strategy.md`
- FN37 implementation: `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/SourceUnitSpec.kt`
- FN37 executable examples:
  - `Icao9432ReadbackSourceUnitSpecTest.kt`
  - `Icao9432TaxiSourceBackedScenarioTest.kt`
  - `Icao9432TouchAndGoSourceBackedScenarioTest.kt`
  - `Icao9432ModelGapSourceUnitSpecTest.kt`
- FN37 review:
  `research/tools/requirements-spike/quality/source_unit_spec_spike/fn37_source_unit_spec_spike_2026-05-18/review.md`
- FN38 design:
  `research/tools/requirements-spike/quality/source_unit_monitor_v2/fn38_source_unit_monitor_v2_2026-05-19/design.md`
- Project testing standard: `docs/test-standards.md`

### Evaluation baseline

FN37 is the only implemented candidate. It proves the five-case shape is
possible, including generated protocol parameters and explicit model gaps.
FN38 is a design-only alternative. Its strength is architectural fit and
scaling potential; its risk is projection cost and model drift.

This evaluation does not count lines of code or current green tests as primary
evidence. The question is which strategy best satisfies the project objective:
a durable regulatory foundation that can be authored against a stable interface
without requiring knowledge of simulator internals.

## Comparison matrix

| Dimension | Candidate A: `SourceUnitSpec` | Candidate B: conformance monitors | Evaluation |
| --- | --- | --- | --- |
| Authoring model | Source unit owns a suite of probes. Very direct and readable for one source unit. | Monitors are independent trace observers; scenarios are separate. More abstract. | A is easier for first authoring; B is better once scenario reuse matters. |
| Independence from simulator internals | Current spike still constructs `SimState` directly in taxi/touch specs. Can be fixed with `TowerConformanceTarget`. | Designed around black-box `ConformanceTrace`; authors should never construct sim state. | B has the better boundary by design. A needs a non-trivial rebuild to reach the same independence. |
| Fit for readback | Works well as pure protocol generated checks. | Works as synthetic trace monitor over `ControllerInstructionIssued` + readback facts. | Tie. A is simpler; B scales to observing emitted instructions across any scenario. |
| Fit for taxi | Works, but the spec body owns scenario setup. | Natural: taxi monitors run over any departure trace. | B is better if trace projection is clean. |
| Fit for touch-and-go intent | Works for current positive scenario; weaker for negative partitions unless expanded. | Natural split: intent monitor, full-stop negative monitor, clearance monitor. | B is stronger for positive/negative intent semantics, but needs first-class intent facts. |
| Fit for essential aerodrome information | FN37 can represent a model gap and domain dimensions. | Better conceptual fit: phase boundary monitor checks prior information state. | B is better for temporal information rules, but needs missing projections. |
| Fit for critical-phase radio silence | FN37 can fuzz parameters and report a model gap. | Best fit: “for every transmission matching P, Q must hold.” | B is clearly stronger for temporal/range/prohibition rules. |
| Typed-domain fuzzing | Spike uses string parameter maps. Concept is right but implementation is too weak. | Fuzzing belongs to scenario generation; monitors stay stable. | B gives cleaner separation. A can improve with typed domains but still embeds fuzz inside each source-unit spec. |
| Adequacy / non-vacuity | Explicit counters per probe. Useful but manual. | Activation coverage is central: observed, applicable, checked, vacuous, gap. | B is stronger and less ad hoc. |
| Model-gap handling | `modelGap(...)` exists and is visible, but expected gaps can still pass with `assertHasModelGap()`. | Projection gaps are first-class in the design. | B has the better conceptual model; A has proven small implementation. |
| Traceability to source units | Excellent: source ids sit at the top of each spec. | Good if monitors carry source ids and reports connect monitor activation to source ids. | A is clearer locally; B must enforce report discipline. |
| Risk of ceremonial traceability | Medium: broad assertions can cite a source id without proving the exact claim. | Medium: monitors can attach to broad facts and pass vacuously. | Tie unless B’s adequacy model is mandatory. |
| Risk of second simulator model | Low in current A, unless a black-box target grows large. | High: `ConformanceTrace` can become a duplicate state model. | A is safer. B needs strict projection discipline. |
| Runtime cost | Source-owned specs run only their probes. | Monitor bank over scenario bank can explode if uncontrolled. | A is cheaper by default. B needs compatibility filtering and CI/nightly tiers. |
| Collaboration boundary | Weak in current implementation; plausible with rebuild. | Strong if `ConformanceTrace` is the contract. | B is the better boundary for separate teams. |
| Long-term scaling | Can become one test file per source unit; may grow repetitive. | Scenarios and monitors compose; one trace can satisfy many monitors. | B is better for scale. |

## Preliminary score

| Criterion | Weight | A score | B score | Notes |
| --- | ---: | ---: | ---: | --- |
| Meets project testing standards | 5 | 4 | 5 | B keeps checks at trace/integration level by default. |
| Authorable by independent team | 5 | 2 | 4 | A needs `TowerConformanceTarget`; B is built around black-box trace. |
| Handles typed-domain fuzzing | 5 | 3 | 5 | A proved shape but is stringly typed; B puts fuzz at scenario level. |
| Handles temporal/prohibition rules | 5 | 3 | 5 | B directly models antecedent/consequent monitoring. |
| Avoids false confidence/vacuity | 5 | 3 | 4 | B’s adequacy design is stronger, but only if enforced. |
| Avoids second simulator model | 4 | 4 | 2 | B’s biggest risk. |
| Implementation simplicity | 3 | 4 | 2 | A is already implemented; B needs projection architecture. |
| Source-unit traceability clarity | 4 | 5 | 4 | A is clearest locally. |
| Model-gap honesty | 4 | 3 | 5 | B’s projection-gap idea is stronger. |
| Reuse across scenarios | 4 | 2 | 5 | B separates scenarios and checks. |

Weighted result:

- Candidate A: 130
- Candidate B: 157

This is not a proof. It says the monitor direction better satisfies the
long-term objectives, while `SourceUnitSpec` is the better exploration tool and
contains important pieces to keep.

## Initial evaluation conclusion

The likely final direction is a **monitor-first architecture** with a small
source-unit authoring layer:

- use FN38’s `ConformanceTrace` + monitor bank as the primary architecture;
- borrow FN37’s source-unit identity, domain declarations, witness/partition/fuzz
  tiers, non-vacuity discipline, and model-gap visibility;
- do not keep FN37’s string parameter maps or scenario construction inside
  source-unit specs;
- do not let FN38 grow a broad duplicate state model.

The review/red-team passes should try to falsify this conclusion.
