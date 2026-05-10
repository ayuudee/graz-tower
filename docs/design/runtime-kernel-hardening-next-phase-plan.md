# Runtime Kernel Hardening Next Phase

## Implementation Status

Implemented on 2026-05-10 for the first closed Kotlin slice:

- `LineUpAndWait`, `ClearedForTakeoff`, and `ClearedToLand` are recognized as
  runway-kernel operations.
- Runtime certification now requires typed evidence for active-runway match,
  runway-duty authority, compatible runway occupancy, and compatible aircraft
  phase before those operations receive runway kernel evidence.
- Covered operations cannot fall back to the older active-runway-only primitive;
  tests fail if runway-duty authority is absent.
- `RunwayKernelDecision.Accepted` construction is firewall-pinned to the runway
  kernel file.

Remaining outside this slice: extending the same kernel shape to additional
runway-affecting instructions such as `AfterLandingVacateVia`,
`HoldPositionCancelTakeoff`, `ClearedTouchAndGo`, crossings, and backtracks.
Those instructions still pass through the existing certification gate, but not
this typed runway-operation kernel.

## Goal

Make the Kotlin runtime recognize the same small, valuable runway-safety
kernel that the FM work was trying to isolate: controller code should not
decide whether to issue runway-affecting instructions by re-deriving safety
conditions ad hoc. It should ask a narrow kernel, receive typed evidence, and
only emit certified outputs when that evidence is present.

The next complete phase is not "prove everything." It is: **one Kotlin kernel
for runway authority and occupancy, wired into the existing certified controller
gate, covering the core runway operations already used by G0/G2.**

## What Kotlin Ultimately Recognizes

Kotlin should recognize a certified runway operation as a typed result, not as a
comment or a convention:

- `RunwayKernelInput`: active runway, runway-duty state, runway observations,
  aircraft observation, commitment, intended instruction, and current time.
- `RunwayKernelDecision`: sealed success/failure output.
- `RunwayKernelEvidence`: sealed evidence leaves that explain which primitive
  safety checks passed.
- `CertifiedControllerOutput`: may be constructed only from a successful
  `RunwayKernelDecision` for runway-affecting instructions.

The valuable deliverable is that controller code can no longer issue the covered
runway operations without the kernel evidence. The runtime gets a hard boundary:
if the operation is in scope and evidence is missing, no certified output is
constructed.

## Scope

Cover the four current primitive checks as kernel evidence:

- Active runway matches the requested runway.
- Controller has runway authority for the operation.
- Runway occupancy is compatible with the operation.
- Aircraft/runway state is compatible with the operation's phase.

Cover the most useful composed operations first:

- `LineUpAndWait`
- `ClearedForTakeoff`
- `ClearedToLand`

If one additional operation is cheap after those are wired, add
`AfterLandingVacateVia`; otherwise leave it as the first follow-on.

## Development Phases And DONE Gates

### Phase 1 - Define The Kernel Boundary

Create the sealed input/result/evidence model in `controller` or `core` at the
same ownership boundary as the current certification gate.

DONE gate:

- The kernel API compiles without exposing raw constructors for certified
  success.
- Every sealed `when` over kernel result/evidence is exhaustive.
- No `else` branches, catch-all defaults, or nullable success markers.
- Tests cover one success and one typed failure for each primitive evidence leaf.

### Phase 2 - Implement Primitive Evidence

Move the primitive checks behind the kernel API. Keep existing domain logic
where possible; the refactor is about ownership and totality, not inventing new
rules.

DONE gate:

- Existing primitive behavior is preserved by integration tests.
- Every failure mode has a typed leaf and a traceable diagnostic.
- The runtime throws only for type-impossible states; type-valid rejected states
  return typed failures.
- `./gradlew detekt` remains green.

### Phase 3 - Compose Operations

Implement operation-specific functions for `LineUpAndWait`,
`ClearedForTakeoff`, and `ClearedToLand` by requiring the primitive evidence set
that each operation needs.

DONE gate:

- Each operation has a table-style condition-space test showing required
  evidence, success, and each primary rejection.
- Reversal/cleanup behavior is explicitly covered where the operation changes
  runway duty or commitment state.
- No controller path constructs the covered instruction without the operation
  function.

### Phase 4 - Wire The Controller

Refactor existing controller decision logic to call the operation functions.
This is the phase where the pattern becomes visible in ordinary Kotlin code.

DONE gate:

- Covered runway-affecting outputs are constructed only through the kernel.
- The certification gate rejects missing kernel evidence loudly.
- G0 and G2 golden tests pass.
- `:protocol:allTests`, `:core:allTests`, `:controller:allTests`, and the
  targeted `:sim:jvmTest` golden command pass.

### Phase 5 - Drift Guards

Add firewall tests that pin construction and usage:

- no direct construction of certified success outside the kernel;
- no covered instruction emitted by controller paths without kernel evidence;
- no new runway-affecting instruction can silently bypass classification.

DONE gate:

- Adding a new covered instruction without kernel classification fails a test.
- Moving or widening constructors fails a test.
- `.plan`, FM status docs, and wiki notes agree on the exact covered scope.

### Phase 6 - Review And Close

Do a focused review pass before calling the phase complete.

DONE gate:

- Principal self-assessment passes the AGENTS.md criteria.
- Independent review covers FP/type safety, test architecture, impact, and
  operational correctness.
- Any accepted deferral is recorded in `.plan`; no hidden TODOs or suppressions.
- Broad build status is stated honestly, including unrelated blockers.

## Review Considerations

### FP / Type Safety

Use sealed result/evidence types and private/smart constructors for certified
success. Rejections are typed values, not `null` or `error()`. `error()` is only
acceptable for states made impossible by the input model. New state fields must
be audited across every `.copy(` site on that state type.

### Test Architecture

The kernel gets focused condition-space tests because the safety predicates have
an independent oracle. Controller wiring gets integration tests because the real
value is that the decision path cannot bypass the kernel. Golden tests remain
the end-to-end confidence check, not the only proof.

### Impact

This deliberately couples runway-affecting controller actions to the kernel. It
makes ad hoc controller logic harder and certified issuance easier, which is the
point. The failure mode is over-centralization: the kernel must stay small and
evidence-oriented, while richer sequencing policy stays outside and asks the
kernel only at the authority/occupancy boundary.

### Operational Correctness

The kernel should not invent ATC procedure. Each operation keeps the existing
regulatory trace surface. Any new claim about runway authority, occupancy, or
clearance phraseology must cite the controlling source before it lands in code.

## Recognition Of Value

This phase is valuable when a reviewer can point at Kotlin and say:

1. These runway operations have one recognized safety kernel.
2. The controller cannot issue them through the covered paths without evidence.
3. Failures are typed, diagnosable, and test-covered.
4. The FM work has a clean runtime counterpart rather than a parallel artifact.
