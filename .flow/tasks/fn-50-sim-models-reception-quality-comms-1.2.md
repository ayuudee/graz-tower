# fn-50-sim-models-reception-quality-comms-1.2 Produce resolved reception-doubt from real sim radio overlap

## Description
Teach the sim to produce at least one real, resolved reception-doubt
observation from the radio model.

Use the narrowest believable scenario: overlapping transmissions on the
same frequency where the receiver can identify the relevant station or
aircraft but the message content is doubtful. The receiver must respond
with `SayAgain`, and the evidence projection must link the doubt fact to
that `SayAgainRef`.

Do not broaden this into a general voice-quality or partial-phoneme
model. COMMS-1 only needs the regulatory behavior: doubt exists, and a
repetition is requested either in full or in part.

## Acceptance
- [ ] A real sim scenario produces a typed reception-doubt signal from
  overlapping radio transmissions, without direct mutation of evidence
  facts.
- [ ] The receiving controller or pilot emits `SayAgain` in response to
  the doubtful transmission. If controller-side `SayAgain` is not yet a
  protocol type, model the smallest typed response needed without
  overloading unrelated readback correction paths.
- [ ] The evidence adapter links each produced
  `EvidenceFactPayload.ReceptionDoubt` to the matching
  `SayAgainRef(TransmissionId)`.
- [ ] At least one focused sim test proves the radio overlap →
  reception-doubt → `SayAgain` chain at the `TransmissionRecord` /
  evidence-fact level.
- [ ] Existing golden tests that rely on stepped-on transmissions remain
  green; no previously vanished transmission is silently delivered as a
  valid operational message.
- [ ] Targeted tests are green:
  `./gradlew-nix :sim:jvmTest --tests "*.EvidenceFactsTest" --tests "*.Icao9432Chunk01ReceptionDoubtEvidenceTest" --tests "*.G1B4ClosurePinSpec"`.

## Review considerations

- FP / type safety: represent doubtful reception separately from clear
  delivery. A doubtful message must not be inserted into normal
  controller inboxes or pilot instruction-processing paths as if it were
  clear.
- Test architecture: the test should exercise DES radio events rather
  than synthetic projected payloads. It should pin both the trigger and
  the response linkage.
- Impact: this touches `handleTransmissionStart` / `handleTransmissionEnd`
  semantics. Audit all paths where stepped-on transmissions are used as
  a non-delivery assumption, especially G1/G2/G3 golden tests.
- Operational correctness: cite ICAO 9432 §2.8.1.4 for the repetition
  request. Do not assert rendered phraseology beyond the existing typed
  `SayAgain` concept.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
