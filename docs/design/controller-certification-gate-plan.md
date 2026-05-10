# Controller Certification Gate Plan

Date: May 9, 2026

## Goal

Make it structurally impossible for the controller to emit a safety-relevant
instruction without passing through a Kotlin certification gate shaped by the
four FM kernel families:

- runway
- surface
- air-path
- separation

This is not a claim that Kotlin is mechanically refined from Lean. It is the
runtime architecture needed to consume the closed FM work honestly: procedures
may propose actions, but only certified primitives, explicit no-certification
administrative outputs, or explicit emergency-policy outputs may become
controller instructions.

## Research Summary

The FM closeout contract is kernel-first. It names four certified methods:
`runway_certify`, `surface_certify`, `air_certify`, and `separation_check`.
The contract explicitly says the controller owns operational composition and
certifier selection.

The old command catalog already contains the useful abstraction:

1. classify a command into a static certification plan
2. instantiate kernel-local work items from current runtime state
3. run the independent kernels
4. bundle the approvals
5. run compatibility over the active set
6. issue only after the bundle exists

The current Kotlin controller does not yet enforce that shape. It has good
local safety logic, but direct `ControllerOutput.Instruct` construction still
exists in arbitration, companion-output generation, reactive separation output,
coordination reissue, and handoff reissue paths. That means certification would
be convention-based unless the output boundary changes.

## Design Principles

1. Raw `AtcInstruction` is not an emit-ready controller output.
2. `ProposedAction` remains the procedure-level result.
3. `CertifiedInstruction` is the only normal path to `ControllerOutput.Instruct`.
4. `ControllerOutput.Instruct` must not be a `data class`; its constructor should
   be private and factory-only to avoid `copy` reopening the boundary.
5. Certification classification must be total and explicit. No default branch may
   classify a new instruction as safe or irrelevant.
6. Emergency intervention is not a hidden bypass. It is an explicit policy result
   with evidence and trace.
7. Reissue is not a new operational clearance. It must either carry the original
   certificate or pass a separate reissue-certification path that proves it is a
   delivery replay.

## Proposed Types

Add a controller certification package, tentatively:

`controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/certify/`

Core types:

```kotlin
sealed interface KernelRequirement {
    data object Runway : KernelRequirement
    data object Surface : KernelRequirement
    data object AirPath : KernelRequirement
    data object Separation : KernelRequirement
}

data class CertificationPlan(
    val requirements: Set<KernelRequirement>,
    val compatibilityRequired: Boolean,
    val joint: Boolean,
)

sealed interface CertificationEvidence {
    data class KernelBacked(val requirement: KernelRequirement, val summary: String) : CertificationEvidence
    data class RuntimeChecked(val checkId: String, val summary: String) : CertificationEvidence
    data class OperationalAssumption(val assumption: String) : CertificationEvidence
    data class EmergencyPolicy(val doctrine: String) : CertificationEvidence
    data class NotRequired(val reason: NoCertificationRequired) : CertificationEvidence
}

sealed interface CertificationFailure {
    data class UnsupportedInstruction(val instruction: AtcInstruction) : CertificationFailure
    data class MissingAircraft(val aircraft: AircraftId) : CertificationFailure
    data class StaleSnapshot(val observedAt: SimTime, val decisionAt: SimTime) : CertificationFailure
    data class KernelRejected(val requirement: KernelRequirement, val reason: String) : CertificationFailure
    data class CompatibilityRejected(val reason: String) : CertificationFailure
}

class CertifiedInstruction private constructor(
    val aircraft: AircraftId,
    val dispatch: Dispatch,
    val plan: CertificationPlan,
    val evidence: NonEmptyList<CertificationEvidence>,
    val certifiedAt: SimTime,
) {
    val instruction: AtcInstruction get() = dispatch.instruction
}
```

`CertifiedInstruction` must not be a `data class`. Its only construction path is
inside `ActionCertifier`.

`ControllerOutput.Instruct` should change from a public data class to a private
constructor class:

```kotlin
class Instruct private constructor(...) : ControllerOutput {
    companion object {
        fun fromCertified(..., certified: CertifiedInstruction): Instruct = Instruct(...)
        fun fromAdministrative(..., administrative: AdministrativeInstruction): Instruct = Instruct(...)
        fun fromEmergency(..., emergency: EmergencyCertifiedInstruction): Instruct = Instruct(...)
    }
}
```

Administrative and emergency constructors are deliberately separate, named
escape hatches. They should be scarce and tested.

## Kernel Adapter Contract

Kotlin should expose four primitive certification adapters even if the first
implementation mirrors the Lean kernels rather than executing extracted Lean:

```kotlin
interface RuntimeKernelCertifiers {
    fun certifyRunway(work: RunwayCertificationWork): Either<CertificationFailure, CertificationEvidence.KernelBacked>
    fun certifySurface(work: SurfaceCertificationWork): Either<CertificationFailure, CertificationEvidence.KernelBacked>
    fun certifyAirPath(work: AirPathCertificationWork): Either<CertificationFailure, CertificationEvidence.KernelBacked>
    fun certifySeparation(work: SeparationCertificationWork): Either<CertificationFailure, CertificationEvidence.KernelBacked>
}
```

The adapters are primitive methods. They should know nothing about procedure
rules, phraseology, urgency, stage advancement, or readback obligations. Their
job is to answer only the local safety question for their owned model.

`ActionCertifier` owns:

- static plan lookup
- extraction of kernel-local work items from `CertificationContext`
- collection of independent kernel approvals
- compatibility checking across active commitments
- construction of `CertifiedInstruction`

The controller should use operation composers, not call primitive adapters
directly. That keeps high-level controller code at the operational-action level
while still forcing every emitted primitive through the same certification path.

## Static Classification

Create an explicit classifier:

```kotlin
fun certificationPlanFor(instruction: AtcInstruction): Either<CertificationFailure, CertificationPlan>
```

This function must be exhaustive over `AtcInstruction`. For the first milestone,
classify only the controller-emitted safety surface and explicit administrative
surface. Any unhandled safety-relevant instruction returns
`UnsupportedInstruction`.

Initial mapping:

- `TaxiToHoldingPoint`, `TaxiToStand`, `HoldPosition`, `HoldShortOf`:
  surface + compatibility
- `CrossRunway`, `BacktrackRunway`, `LineUpAndWait`:
  runway + surface + compatibility + joint
- `ClearedForTakeoff`, `ClearedToLand`, `ClearedTouchAndGo`, `GoAround`,
  `BreakOff`:
  runway + air-path + separation + compatibility + joint
- `TurnBase`, `ExtendDownwind`, `ContinueApproach`, `JoinCircuit`:
  air-path + separation + compatibility, except a first implementation may
  make `JoinCircuit` air-path-only if the runtime separation witness is not
  yet available
- `NumberInSequence`, `ReportWhen`, `ContactFrequency`, `MonitorFrequency`,
  `SetSquawk` family:
  explicit no-certification or non-FM certification category, with a named
  rationale and existing drift tests retained

Do not use instruction category interfaces like `RunwayInstruction` as the
classifier. They are useful metadata, but several leaves are multi-domain or
emergency-shaped. Certification requirements must be leaf-explicit.

## Time And Certainty

The runtime gate can be model-certain only relative to a snapshot. Therefore the
certification context should include:

```kotlin
data class CertificationContext(
    val view: ControllerView,
    val beliefs: BeliefState,
    val world: AviationWorld,
    val decisionTime: SimTime,
)
```

The first implementation should treat the current `ControllerView` and
`BeliefState` as one decision snapshot. If observation timestamps are not
available for a required fact, the result must include an
`OperationalAssumption`, not pretend timeless certainty.

Certification outcomes should be understood as:

- model-certain: all required checks pass in the snapshot model
- observation-dependent: checks pass, but evidence relies on controller beliefs
  or unstamped observations
- emergency-policy: immediate safety intervention is authorized despite a gap
- rejected: emission blocked

Normal operations block on rejection or unsupported certification. Emergency
operations use a separate explicit policy path.

## First Higher-Level Operations

Implement these operation composers after the primitive certifier exists. They
are small enough to make the pattern clear and broad enough to exercise all four
kernels.

### 1. Surface Movement Authorization

Inputs:

- `ProposedAction` carrying `TaxiToHoldingPoint`, `TaxiToStand`, `HoldPosition`,
  or `HoldShortOf`
- current aircraft observation
- world surface graph and active runway context

Certification:

- surface kernel-shaped route legality / destination reachability
- compatibility against active movement commitments

Controller refactor target:

- `TaxiToHoldingAction`
- `TaxiToStandAction`
- `HoldPositionAction`

Value:

- establishes the simplest primitive path and the explicit
  no-runway/no-air/no-separation case
- makes fail-closed surface extraction visible

### 2. Runway Access Authorization

Inputs:

- `LineUpAndWait`, `CrossRunway`, or `BacktrackRunway`
- runway duty state
- surface position / hold point evidence
- active runway commitments

Certification:

- runway kernel-shaped protected-resource check
- surface kernel-shaped entry/path check
- compatibility over active runway and surface commitments

Controller refactor target:

- `LineUpAction`
- `ConditionalLineUpAction`
- future `CrossRunway` rule
- `VacateAction` when it emits `BacktrackRunway`

Value:

- demonstrates joint runway + surface certification
- prevents runway-entry instructions from relying only on rule guard discipline

### 3. Air-Runway Operation Authorization

Inputs:

- `ClearedForTakeoff`, `ClearedToLand`, `ClearedTouchAndGo`, `GoAround`, or
  `BreakOff`
- runway duty state
- current aircraft airborne/on-ground state
- current separation assessment
- world-backed runway and air-path context

Certification:

- runway kernel-shaped runway operation check
- air-path kernel-shaped transition/approach/departure path check
- separation checker against modeled pairwise concerns
- compatibility over existing active commitments

Controller refactor target:

- `ClearTakeoffAction`
- `ClearLandAction`
- `ClearTouchAndGoAction`
- `GoAroundAction`
- reactive separation `BreakOff` / `GoAround`

Value:

- exercises all four kernel families in the most safety-critical path
- makes safety-urgency behavior explicit instead of bypassing feasibility

## Controller Refactor Plan

### Phase 1: Add The Certification Boundary

- Add `CertifiedInstruction`, `CertificationPlan`, evidence, failures, and
  context types.
- Add `ActionCertifier.certify(action, context)`.
- Keep the first implementation conservative: unsupported safety-relevant
  instructions reject.
- Add administrative wrappers for explicit no-certification outputs.

### Phase 2: Lock The Output Constructor

- Convert `ControllerOutput.Instruct` from public `data class` to private
  constructor class.
- Expose named factories:
  - `fromCertified`
  - `fromAdministrative`
  - `fromEmergency`
  - possibly `reissueCertified`
- Move all current construction sites to factories.
- Store certificate/evidence on `Instruct` so traces can explain why emission
  was allowed.

### Phase 3: Route Arbitration Through Certification

- Change `arbitrate` so it certifies the enriched proposed action before
  committing output.
- Replace the current feasibility call with either:
  - a certifier sub-check, or
  - a named runtime validation evidence item inside certification
- Safety urgency no longer bypasses the gate. It may choose the emergency policy
  constructor only for declared emergency actions.

### Phase 4: Refactor Reactive And Reissue Paths

- Reactive separation output should produce proposed emergency actions, then run
  emergency certification policy.
- Coordination reissue should preserve or reference the original certificate.
  If old coordination records have no certificate, the migration behavior should
  be loud during development and then handled by a deliberate compatibility
  adapter before release.
- Handoff reissue and administrative companion outputs should be explicit
  administrative instructions, not raw `Instruct` construction.

### Phase 5: Add The Three Operation Composers

- Introduce the three operation composers listed above.
- Make procedure actions call the operation composers where appropriate.
- Keep procedure rules responsible for operational timing, regulation
  references, and phraseology. The operations only certify that the primitive
  emissions are safe/coherent under the snapshot.

### Phase 6: Remove Legacy Bypass Surface

- Delete or privatize any helper that can emit an instruction without certified,
  administrative, reissue, or emergency evidence.
- Add firewall tests that fail on new raw construction paths.

## Testing Plan

Prefer integration and architectural tests over isolated unit tests.

Required tests:

1. Certification firewall compile/scan test:
   - production code may construct `ControllerOutput.Instruct` only in the
     controller output factory
   - production code may construct `CertifiedInstruction` only in the certifier
2. Controller integration tests:
   - existing taxi, line-up, takeoff, landing, touch-and-go, and reactive
     separation flows still emit outputs
   - emitted outputs carry certification or explicit administrative/emergency
     evidence
3. Fail-closed tests:
   - unsupported safety-relevant instruction proposed by a test-only procedure is
     rejected and not emitted
   - missing aircraft/world/runway evidence rejects, not defaults
4. Reissue tests:
   - reissued instruction carries the original certificate or explicit reissue
     evidence
5. Drift tests:
   - static classification is exhaustive over the current emitted instruction
     surface
   - no `else -> Certified`, `else -> Feasible`, or category-wide default may
     certify a new instruction

Existing core current-shape tests remain valuable and should not be weakened.
They prove Kotlin runtime semantics do not drift from delivered theorem
surfaces; the new controller tests prove the controller actually consumes those
semantics through the gate.

## Implementation Order

1. Add certifier types and explicit classification for the first controller
   surface.
2. Add tests for certification results without touching controller output.
3. Lock `ControllerOutput.Instruct` construction and migrate current call sites
   to explicit factories.
4. Certify normal arbitration outputs.
5. Certify or explicitly classify companion, reactive, coordination reissue, and
   handoff reissue outputs.
6. Introduce the three operation composers and move matching `RuleAction`s onto
   them.
7. Remove or fail any legacy direct-emission path.

## Phase Done Gates

Each phase below has an independent `DONE` gate. A later phase must not start
until the prior phase's gate is met. If a gate cannot be met, the incomplete
state must be loud: failing tests, compile errors, or an explicit `.plan` item
with the blocker and rationale.

### Phase 1 DONE: Certification Boundary Exists And Fails Closed

Required state:

- `CertifiedInstruction`, `CertificationPlan`, `CertificationEvidence`,
  `CertificationFailure`, `CertificationContext`, and `ActionCertifier` exist.
- The four primitive adapter methods exist in a `RuntimeKernelCertifiers`
  boundary.
- `certificationPlanFor` is leaf-explicit for the initial controller-emitted
  surface.
- Unsupported safety-relevant instructions return typed failure, not success,
  administrative status, or thrown exceptions.
- Administrative/no-certification cases are represented by named reasons.

Evidence:

- Classifier tests cover every instruction leaf included in the first milestone.
- Fail-closed tests prove an unsupported safety-relevant instruction cannot
  produce `CertifiedInstruction`.
- No public `unsafe` constructor exists for `CertifiedInstruction` or emergency /
  administrative wrappers.
- `./gradlew :controller:allTests` passes, or if module task naming differs, the
  nearest controller test task plus `./gradlew :protocol:allTests :core:allTests`
  passes.

### Phase 2 DONE: Raw Instruction Output Is Not Publicly Constructible

Required state:

- `ControllerOutput.Instruct` is no longer a public `data class`.
- Its constructor is private or otherwise inaccessible outside the approved
  factory boundary.
- Public read API remains stable enough for consumers:
  `target`, `dispatch`, `instruction`, `urgency`, `trace`, and advancement fields.
- Approved factories are narrow and named:
  certified, administrative, reissue, and emergency.
- Factory signatures do not accept arbitrary `AtcInstruction` for normal
  emission.

Evidence:

- Production source has no direct `ControllerOutput.Instruct(` construction
  outside the approved factory file.
- Tests that previously destructured or copied `Instruct` are updated to inspect
  public fields instead.
- Firewall test fails if a new production call site constructs `Instruct`
  directly.
- `./gradlew :controller:allTests` passes.

### Phase 3 DONE: Normal Arbitration Is Certification-Gated

Required state:

- `arbitrate` certifies enriched proposed actions before committing normal
  outputs.
- Current feasibility checks are either inside certification or recorded as
  named runtime validation evidence.
- Non-emergency `Urgency.SAFETY` outputs do not bypass certification.
- Rejected certification means no output and no stage advancement for that
  action.
- Decision trace or output evidence can explain why an emitted instruction was
  allowed.

Evidence:

- Integration tests cover at least taxi, line-up, takeoff, landing, and
  touch-and-go normal flows and assert emitted instructions carry certification
  evidence.
- A test-only procedure proposing an unsupported safety-relevant action is
  rejected, not emitted, and does not advance stage.
- Existing golden/controller tests remain green.
- `./gradlew :controller:allTests :core:allTests` passes.

### Phase 4 DONE: Reactive, Reissue, And Administrative Paths Are Explicit

Required state:

- Reactive separation output produces proposed emergency actions and enters the
  emergency certification policy path.
- Coordination reissue carries or references original certification evidence, or
  uses a narrow reissue evidence path proving delivery replay.
- Handoff reissue is explicitly administrative or reissue-classified.
- Companion outputs such as sequence information are explicit administrative
  instructions with named no-certification reasons.
- No reactive/reissue/admin code can construct raw `Instruct`.

Evidence:

- Reactive separation tests assert emergency evidence on `BreakOff` / `GoAround`
  output.
- Readback query / coordination reissue integration tests assert replayed
  instructions carry original or reissue evidence.
- Administrative companion-output tests assert no-certification reason, not
  absence of evidence.
- Firewall direct-construction test remains green.
- `./gradlew :controller:allTests` passes.

### Phase 5 DONE: Three Operation Composers Own The First Useful Surface

Required state:

- `SurfaceMovementAuthorization` composes certification for `TaxiToHoldingPoint`,
  `TaxiToStand`, `HoldPosition`, and `HoldShortOf`.
- `RunwayAccessAuthorization` composes certification for `LineUpAndWait`,
  `CrossRunway`, and `BacktrackRunway` where currently emitted or testable.
- `AirRunwayOperationAuthorization` composes certification for
  `ClearedForTakeoff`, `ClearedToLand`, `ClearedTouchAndGo`, `GoAround`, and
  `BreakOff`.
- Matching `RuleAction`s use operation composers instead of directly relying on
  scattered controller checks.
- Procedure rules still own regulation references, urgency, phraseology, and
  stage advancement; operation composers own only safety/coherence
  certification.

Evidence:

- Integration tests exercise each composer through at least one real controller
  procedure path.
- Unit tests, where used, cover only formal adapter/classifier behavior with a
  clear oracle.
- No duplicate certifier invocation logic remains inside individual
  `RuleAction`s for these surfaces.
- `./gradlew :controller:allTests :core:allTests` passes.

### Phase 6 DONE: Legacy Bypass Surface Removed

Required state:

- All production instruction emission paths go through certified,
  administrative, reissue, or emergency factories.
- Source/compile firewall tests guard both `ControllerOutput.Instruct` and
  `CertifiedInstruction` construction boundaries.
- `.plan` has no open implementation blocker for the first certification-gated
  surface; any future widening is separate backlog.
- The wiki design decision and this design plan match the implemented state.

Evidence:

- Repository-wide search confirms no direct raw `Instruct` construction outside
  the approved factory boundary.
- Repository-wide search confirms no production `else -> Certified`,
  `else -> Feasible`, or category-wide default certifies safety-relevant
  instructions.
- Controller golden/integration tests pass.
- `./gradlew :protocol:allTests :core:allTests :controller:allTests detekt`
  passes, or any unavailable task is called out with the exact replacement
  command used.

### Gate Discipline

At the end of each phase:

1. Run the phase's evidence commands.
2. Record the result in the implementation notes or commit message.
3. Update `.plan` if the phase defers anything or reveals a new gap.
4. Do not begin the next phase with a red gate unless the red state is the
   deliberate loud failure being fixed by that next phase.

## Review Considerations

### FP / Type Safety

- Use sealed result and failure types.
- Avoid `data class` for certified wrappers and `ControllerOutput.Instruct`;
  public `copy` would weaken constructor privacy.
- Keep classification leaf-explicit. A category interface may inform grouping
  but cannot be the certification decision.
- Use `Either<CertificationFailure, CertifiedInstruction>` for possible runtime
  failure. Use `error()` only for states made impossible by private constructors.
- If a new state field is added to `BeliefState`, coordination records, or
  output types, audit all `.copy(` sites before implementation.
- Do not expose `unsafe` constructors for `CertifiedInstruction`,
  `EmergencyCertifiedInstruction`, or administrative instruction wrappers.
  Test fixtures should build them through test-only certifiers, not bypass the
  boundary they are supposed to exercise.

### Commandments

- No silent bypass: every instruction emission has certified, administrative,
  reissue, or emergency evidence.
- No skip lists: unsupported safety-relevant instructions reject loudly.
- No hidden partial implementation: if a certification path is not implemented,
  the result is a typed failure, not an assumed pass.
- No uncited operational claims: procedure rules keep regulation references;
  certification code should cite only when it encodes operational doctrine
  rather than structural checks.
- Emergency behavior is loud and traceable, not a convenience escape hatch.

### Testability And Test Standards

- Use type boundaries to eliminate most unit tests.
- Unit-test only the classifier and small kernel adapters where the oracle is
  formal and independent.
- Prefer controller integration tests for the useful behavior: a procedure
  proposes an action, certification gates it, and output carries evidence.
- Add architectural firewall tests because bypass prevention is the core job.
- Do not duplicate tests that merely assert sealed type properties already
  enforced by the compiler.

### Impact

- This touches the controller output type, coordination records, arbitration,
  reactive separation output, and several controller tests.
- It will likely require small updates to test fixtures that inspect
  `ControllerOutput.Instruct` as a data class.
- It should not change protocol instruction types or core clearance resolution
  semantics in the first pass.
- It makes future controller features more rigid: new emissions must declare
  their certification story up front.

### Staff-Level Risks

- Over-modeling risk: trying to mirror the whole Lean single-issuer theorem in
  Kotlin would stall the work. The plan deliberately implements a runtime gate,
  not a formal global proof.
- Under-modeling risk: a private constructor alone is not enough if
  administrative or emergency factories become broad. Those paths need named
  evidence and firewall tests.
- Time risk: current observations are not fully timestamped. The first gate must
  be honest about snapshot assumptions and avoid claiming timeless certainty.
- Compatibility risk: reissue semantics are easy to get wrong. Store certificate
  evidence with issued coordination records before making reissue depend on it.
- Migration risk: changing `Instruct` away from a data class can cause broad
  test churn. Keep the public read API stable (`instruction`, `dispatch`,
  `target`, `urgency`, `trace`) to contain blast radius.

## Plan Review Verdict

### FP Review

The plan is aligned with the project style if implementation keeps the boundary
non-data and sealed:

- `Either` carries all runtime certification failures.
- `NonEmptyList` prevents empty evidence on certified outputs.
- Private constructors make impossible states unrepresentable at the output
  boundary.
- Leaf-explicit classification avoids category-wide defaults that would certify
  future instructions by accident.

Main FP risk: using `Set<KernelRequirement>` loses ordering and cardinality. That
is acceptable for independent kernels, but compatibility should consume a named
certificate bundle rather than infer meaning from set membership alone.

### Commandments Review

The plan satisfies the no-corners-cut rule only if unsupported instructions are
typed failures that block normal emission. Administrative and emergency paths are
the pressure points. They must remain named, narrow, evidenced paths with
firewall tests. A broad `fromAdministrative(AtcInstruction)` factory would
violate the plan.

### Testability Review

The plan follows `docs/test-standards.md`: type boundaries do most of the work,
architectural firewall tests enforce non-bypass, and integration tests exercise
actual controller behavior. Classifier tests are justified because the classifier
is the static routing oracle.

Main test risk: source-scan firewall tests can be brittle. Prefer compile-time
visibility first, then source scans only for architectural review of factory
usage.

### Impact Review

The blast radius is real but bounded: controller output type, arbitration,
reactive separation, coordination records, and tests that destructure
`Instruct`. Protocol and core clearance semantics should remain unchanged for
the first milestone.

Main migration risk: coordination reissue. If certificates are added to
coordination records, every coordination mutation and cleanup path must be
audited.

### Senior Review

The plan is directionally right because it moves certification from convention
to type-enforced architecture without reopening the whole Lean single-issuer
proof. The first implementation should stay deliberately conservative: make the
three operation composers work, block unknown safety-relevant instructions, and
resist widening the classifier until there is a controller need and tests that
prove the new path is used.
