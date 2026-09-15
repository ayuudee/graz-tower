# fn-50-sim-models-reception-quality-comms-1.1 Design typed reception-quality signal and evidence projection

## Description
Decide and implement the typed reception-quality surface that bridges the
sim radio layer to the evidence DSL without changing operational behavior
yet.

The preferred shape is additive and total:
- add a test-visible typed reception-quality value on
  `sim/src/jvmTest/.../testing/TransmissionRecord.kt`, or a parallel
  typed signal stream if production `SimEvent` needs to carry the
  source;
- locate the type in the sim radio model so task .2 can derive it from
  real radio state, not from synthetic `fromProjectedPayloads`;
- update `EvidenceFactAdapters.receptionDoubtFact(...)` so it branches
  on the typed signal and emits
  `EvidenceFactPayload.ReceptionDoubt(..., resolvedBy = null)` for
  unresolved observations;
- keep `protocol.SayAgain` unchanged. Resolution linking is task .2.

This task should be allowed to leave COMMS-1 covered-red: it lands the
type and projection surface, not the resolved scenario.

## Acceptance
- [x] A typed reception-quality/reception-doubt input exists on the
  observation surface. It can express at least
  `ReceptionDoubtSource.PartialReception`, `Unintelligibility`,
  `SteppedOn`, and `Other(detail)`.
- [x] The type is part of the sim radio model and is carried by the
  observation surface. Task .2 derives it from radio overlap; this task
  does not claim COMMS-1 green from hand-built records or
  `fromProjectedPayloads`.
- [x] `EvidenceFactAdapters.receptionDoubtFact(...)` takes the typed
  input and emits `EvidenceFactPayload.ReceptionDoubt` at the reserved
  `+5` offset with stable provenance.
- [x] Existing reception-doubt unit tests are updated to prove both the
  no-signal path and the typed-signal projection path.
- [x] `Icao9432Chunk01ReceptionDoubtEvidenceTest` remains honestly
  covered-red until task .2 provides a resolved real scenario.
- [x] Targeted tests are green:
  `./gradlew-nix :sim:jvmTest --tests "*.EvidenceFactsTest" --tests "*.Icao9432Chunk01ReceptionDoubtEvidenceTest"`.

## Review considerations

- FP / type safety: model signal absence explicitly as a nullable field or
  `Option` on the observation surface; no catch-all `else`; no `error()`
  for type-valid unsignalled transmissions.
- Test architecture: unit tests prove projection totality; the chunk-01
  evidence test stays high-level and red until the sim produces a
  resolved doubt.
- Impact: adding fields to `TransmissionRecord` requires auditing every
  constructor call in sim tests. Prefer a default only if absence is a
  real domain value, not a silent compatibility hack.
- Operational correctness: ICAO 9432 §2.8.1.4 is the cited source for the
  evidence fact; this task does not yet claim the controller/pilot asks
  for repetition.

## Done summary
Implemented typed reception-quality projection for COMMS-1 task .1.

- Added sim-level ReceptionQuality and ReceptionDoubtCause leaves for Clear/Doubtful reception states.
- Extended test-side TransmissionRecord with default ReceptionQuality.Clear.
- Updated EvidenceFactAdapters.receptionDoubtFact to emit unresolved ReceptionDoubt facts at the reserved +5 offset when a record is Doubtful, while Clear records emit no fact.
- Added focused EvidenceFactsTest coverage for Clear matrix behavior, all typed doubt causes, and ControllerOutput.Respond wiring.

COMMS-1 remains operationally covered-red until fn-50.2 makes a real radio-overlap trace produce a doubtful record and an actual SayAgain resolution.
## Evidence
- Commits:
- Tests: {'command': './gradlew-nix :sim:jvmTest --tests "*.EvidenceFactsTest" --tests "*.Icao9432Chunk01ReceptionDoubtEvidenceTest"', 'result': 'pass'}, {'command': './gradlew-nix detekt', 'result': 'pass'}
- PRs:
