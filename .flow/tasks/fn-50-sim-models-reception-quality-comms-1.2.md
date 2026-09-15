# fn-50-sim-models-reception-quality-comms-1.2 Produce resolved reception-doubt from real sim radio overlap

## Description
Teach the sim to produce at least one real, resolved reception-doubt
observation from the radio model.

Use the narrowest believable scenario: overlapping transmissions on the
same frequency where the receiver can identify the relevant station but
the message content is doubtful. The minimal non-cognitive pilot fixture
must respond with `SayAgain`, and the evidence projection must link the
doubt fact to that `SayAgainRef`.

Do not broaden this into a general voice-quality or partial-phoneme
model. COMMS-1 only needs the regulatory behavior: doubt exists, and a
repetition is requested either in full or in part.

Full cognitive-mission recovery is not enabled in this task; it is filed
as `D-AUDIT.15-FOLLOWUP` because naive automatic repetition perturbs the
LOWG golden-style traces and needs its own design.

## Acceptance
- [x] A real sim scenario produces a typed reception-doubt signal from
  overlapping radio transmissions, without direct mutation of evidence
  facts.
- [x] The receiving pilot emits `SayAgain` in response to the doubtful
  controller transmission in the minimal non-cognitive fixture. The
  controller-side `SayAgain` question remains untouched because this
  scenario uses the existing pilot-side protocol type correctly.
- [x] The evidence adapter links each produced
  `EvidenceFactPayload.ReceptionDoubt` to the matching
  `SayAgainRef(TransmissionId)`.
- [x] At least one focused sim test proves the radio overlap →
  reception-doubt → `SayAgain` chain at the `TransmissionRecord` /
  evidence-fact level:
  `ReceptionQualityCommsTest`.
- [x] Existing golden-style tests that rely on stepped-on transmissions remain
  green; no previously vanished transmission is silently delivered as a
  valid operational message.
- [x] Targeted tests are green:
  `./gradlew-nix :sim:jvmTest --tests "*.ReceptionQualityCommsTest" --tests "*.EvidenceFactsTest" --tests "*.Icao9432Chunk01ReceptionDoubtEvidenceTest"`.

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
Implemented the real radio-overlap COMMS-1 behavior for task .2.

- Added final TransmissionReceptionObserved events from TransmissionEnd handling so test traces can distinguish clear delivery from stepped-on doubtful reception.
- Added minimal non-cognitive pilot SayAgain recovery for stepped-on controller-to-pilot transmissions, with one-repeat/pending-doubt guards to avoid recursive radio congestion.
- Updated transmission-record projection to merge final reception quality observations.
- Added ReceptionQualityCommsTest, which drives two overlapping transmissions through the sim, observes a doubtful controller transmission, observes a later pilot SayAgain, and verifies the evidence fact resolves via SayAgainRef.
- Filed D-AUDIT.15-FOLLOWUP for full cognitive-mission repetition recovery, which needs separate design before being enabled in golden-style missions.
## Evidence
- Commits:
- Tests: {'command': './gradlew-nix :sim:jvmTest --tests "*.ReceptionQualityCommsTest" --tests "*.EvidenceFactsTest" --tests "*.Icao9432Chunk01ReceptionDoubtEvidenceTest"', 'result': 'pass'}, {'command': './gradlew-nix :sim:jvmTest --tests "*.G1B4ClosurePinSpec"', 'result': 'pass'}
- PRs: