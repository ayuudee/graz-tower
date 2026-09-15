# Sim models reception quality (COMMS-1 closure)

## Goal & Context

ICAO 9432 §2.8.1.4 requires that "if there is doubt that a message has
been correctly received, a repetition of the messages shall be requested
either in full or in part." The protocol layer already models the
response side (`protocol.SayAgain`), and the evidence DSL now carries a
typed reception-doubt vocabulary added in
`fn-48-icao-9432-chunk-01-drive-expected-gap.3`:

- `EvidenceFactPayload.ReceptionDoubt(aircraftId, transmissionRef, doubtSource: ReceptionDoubtSource, resolvedBy: SayAgainRef? = null)`
- `ReceptionDoubtSource` sealed sub-type with leaves
  `PartialReception`, `Unintelligibility`, `SteppedOn`, `Other(detail)`.
- `SayAgainRef(transmissionId: TransmissionId)` typed linkage to the
  `SayAgain` response (trigger and response stay distinct types).
- `EvidenceFactAdapters.receptionDoubtFact(...)` adapter projection at
  sequence offset `recordIndex * FACTS_PER_RECORD + 5`, wired into both
  the controller and pilot speaker arms.
- `EvidenceExpectContext.receptionDoubt(aircraftId)` selector returning
  `AuditReceptionDoubtSubject` with `requiresRepetitionResponse()`.

Before this epic, the sim did not emit any reception-quality signal:
`TransmissionRecord` modeled speaker / receiver / utterance but did not
carry a typed reception-quality input that the adapter could read.
The adapter was total over the speaker × utterance × payload matrix but
returned `null` for every record on then-current traces. As a result the
chunk-01 evidence test
`Icao9432Chunk01ReceptionDoubtEvidenceTest::reception-doubt source unit
is covered red against current LOWG trace` landed **covered-red**: the
audit honestly reported a `Fail` outcome for the cited source ref, and
the test asserted that outcome directly on `report.results` rather than
via `assertNoFailures()`. JUnit passed (build green) while the audit's
red outcome stood.

This repair epic closed that gap by teaching the sim to model reception
quality and emit observable reception-doubt signals into transmission
records from a real overlapping-transmission scenario. The new adapter
projection produces `ReceptionDoubt` facts where doubt actually arises,
and the COMMS-1 evidence test now runs covered-green via
`report.assertNoFailures()`. The `.plan` pointer
(COMMS-1 → fn-50-sim-models-reception-quality-comms-1) is deleted.

## Closure Signal

`./gradlew-nix :sim:jvmTest --tests
"*.Icao9432Chunk01ReceptionDoubtEvidenceTest"` runs to green with the
test method calling `report.assertNoFailures()` (instead of asserting
on `report.results` for a `Fail` outcome).

Equivalently: in at least one sim scenario, the new adapter projection
[`EvidenceFactAdapters.receptionDoubtFact`] emits one or more
`EvidenceFactPayload.ReceptionDoubt(...)` facts driven by a real
reception-quality signal on the underlying transmission record. The
`AuditReceptionDoubtSubject.requiresRepetitionResponse()` selector
returns `Pass` for the target aircraft in the COMMS-1 overlap scenario
(the emitted doubt fact has a matching `resolvedBy: SayAgainRef` because
the minimal non-cognitive pilot response logic emits a `SayAgain` linked
to the same transmission stream). Full cognitive-mission repetition
recovery is filed separately as `D-AUDIT.15-FOLLOWUP`.

## Acceptance Criteria

- [x] **R1** — `TransmissionRecord` (or a parallel typed signal stream)
  carries a reception-quality input that can express the four typed
  `ReceptionDoubtSource` leaves (`PartialReception`,
  `Unintelligibility`, `SteppedOn`, `Other(detail)`). The signal is
  produced by the sim (e.g., overlapping-transmission detection in the
  radio model, partial-reception by phase-of-flight masking, controller
  ambiguity detection) — NOT injected by tests via
  `fromProjectedPayloads`.
- [x] **R2** — `EvidenceFactAdapters.receptionDoubtFact(...)` branches
  on the new typed input and emits
  `EvidenceFactPayload.ReceptionDoubt(...)` facts at sequence offset
  `recordIndex * FACTS_PER_RECORD + 5`. Tests cover Clear-matrix
  silence, all typed doubt causes, and resolved real-overlap evidence.
- [x] **R3** — The chunk-01 evidence test
  `Icao9432Chunk01ReceptionDoubtEvidenceTest` re-runs `covered-green`:
  the test method asserts via `report.assertNoFailures()` (the
  `report.results` `Fail` assertion introduced in
  fn-48-icao-9432-chunk-01-drive-expected-gap.3 is replaced).
- [x] **R4** — The pilot agent in the minimal COMMS-1 overlap scenario
  that observes a doubt signal also emits a `protocol.SayAgain`
  transmission, and the
  adapter populates `EvidenceFactPayload.ReceptionDoubt.resolvedBy`
  with the matching `SayAgainRef(TransmissionId)`. Full cognitive-mission
  repetition recovery is `D-AUDIT.15-FOLLOWUP`.
- [x] **R5** — `.plan` pointer paragraph for COMMS-1 is deleted (since
  the unit is now `covered-green`).
- [x] **R6** — `./gradlew-nix build` and `./gradlew-nix detekt` both
  green.
- [x] **R7** — No regression in existing sim golden tests
  (`./gradlew-nix :sim:jvmTest`).

## Investigation hints

- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/PilotTransmission.kt:313`
  — `SayAgain(element: String? = null)` already exists. Do NOT modify
  `SayAgain` to back-reference doubt — the doubt fact carries
  `resolvedBy: SayAgainRef?` instead.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:166-185`
  — `EvidenceFactPayload.ReceptionDoubt` data class.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:246-300`
  — `ReceptionDoubtSource` sealed sub-type + `SayAgainRef` value class.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:705+`
  — adapter projection `receptionDoubtFact` (currently returns null;
  branch it on the new typed input to emit facts).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:557-637`
  — `AuditReceptionDoubtSubject` selector — pass-path requires
  `resolvedBy: SayAgainRef?` to be non-null for every emitted doubt.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReceptionDoubtEvidenceTest.kt`
  — closure test; assertion shape changes once green.
- Radio model in the sim (likely `sim/src/.../RadioChannel.kt` or
  similar) — natural site for overlap detection.

## Boundaries

In scope:
- Sim-side reception-quality signal input on transmission records (or
  a parallel typed stream).
- Adapter projection branching on the new typed input to emit
  `ReceptionDoubt` facts.
- Controller / pilot decision logic that links observed doubt to a
  `SayAgain` response.
- Updating the chunk-01 evidence test method to call
  `report.assertNoFailures()`.
- Deleting the COMMS-1 pointer paragraph from `.plan`.

Out of scope:
- Modifying `protocol.SayAgain` itself.
- New `ReceptionDoubtSource` leaves beyond the four landed in task .3
  (`PartialReception`, `Unintelligibility`, `SteppedOn`, `Other`).
- Time-decay semantics on doubt (still deferred per task .3 design
  decision).
- Chunks 02 and later.
