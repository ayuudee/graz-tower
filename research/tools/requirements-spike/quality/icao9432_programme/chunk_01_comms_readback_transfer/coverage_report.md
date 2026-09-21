# Chunk 01 coverage report

Chunk: ICAO 9432 communications, transfer, and readback.

## Summary

| Final state | Units |
|---|---:|
| `covered-green` | 11 |
| `covered-synthetic rendered example phraseology` | 3 |
| `covered-red` | 0 |
| `expected-gap` | 0 |
| `not-applicable` | 1 |
| `phraseology-later` | 1 |
| `policy-blocked` | 2 |
| `split: supported rendered controller templates covered; cancellation wording phraseology-later` | 1 |
| `split: supported rendered readbacks covered; remaining templates phraseology-later` | 1 |

Closed initially at `fn-48-icao-9432-chunk-01-drive-expected-gap`
(2026-05-26), then updated by `fn-49-sim-emits-pilot-notified-frequency`
and `fn-50-sim-models-reception-quality-comms-1`. Frequency-transfer
coverage is green for both the controller-advised and pilot-notified
branches. COMMS-1 is now covered-green via a real radio-overlap scenario
that produces reception-doubt evidence resolved by pilot `SayAgain`.
fn-81 partially closes the readback-termination phraseology row for the
currently supported rendered pilot readback templates (`LineUpReadback` and
`FrequencyReadback`); other readback templates remain PHRASE-1.
fn-82 partially closes the TAKE OFF word-use row for supported rendered
controller templates; take-off-clearance cancellation wording and unsupported
templates remain PHRASE-1. fn-89 covers the §2.8.1 full-callsign and
ALL STATIONS examples as synthetic rendered-example evidence only; it does not
claim live broadcast routing, acknowledgement policy, or controller/pilot
generation.

Focused verification run:

```bash
./gradlew-nix :sim:jvmTest --tests "*Icao9432*" :controller:jvmTest --tests "*Icao9432*"
```

Result: GREEN. All chunk-01 evidence tests pass
(`Icao9432Chunk01ReadbackEvidenceTest`,
`Icao9432Chunk01FrequencyTransferEvidenceTest`,
`Icao9432Chunk01ReceptionDoubtEvidenceTest`,
`Icao9432Chunk01CommunicationsPhraseologyEvidenceTest`,
`Icao9432Chunk01ClearancePacingEvidenceTest` on the sim side;
`Icao9432ReadbackConformanceSpec` on the controller side; plus the
adjacent `Icao9432*SourceUnitSpec` / `Icao9432*SourceBackedScenario`
classes the `*Icao9432*` wildcard also captures). There are no remaining
covered-red or expected-gap units in chunk 01.

Pre-existing sandbox failure, NOT a chunk-01 regression:
`EvidenceReportWriterTest` (2 cases) and `EvidencePermanentTwentyCaseTest`
(1 case) fail on `java.nio.file.FileSystemException` when calling
`Files.createTempDirectory(...)` under the macOS sandbox / agent
environment used to run the chunk-01 closure verification. The tests
predate fn-48 (last touched in commits `9e728ce6` and `514c371e`,
well upstream of any chunk-01 work) and reproduce on master baseline.
They are out of chunk-01 scope and tracked separately as a sandbox /
test-environment concern, not as a chunk closure blocker.

## Coverage Table

| Source unit | Final state | Test / blocker | Claim |
|---|---|---|---|
| `icao9432-extracted::communications_2_8_1_en::0a964f42b6100596` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReceptionDoubtEvidenceTest.kt` | If there is doubt that a message has been correctly received, a repetition of the messages shall be requested either ... |
| `icao9432-extracted::communications_2_8_1_en::5efac97fddfd54ca` | `covered-synthetic rendered example phraseology` | `Icao9432Chunk01CommunicationsPhraseologyEvidenceTest`; SyntheticRenderedPhraseologyExample | Synthetic rendered-example evidence covers the §2.8.1.3 aircraft `ALL STATIONS` broadcast example. |
| `icao9432-extracted::communications_2_8_1_en::8b0487b183cd02cf` | `policy-blocked` | `POLICY-1` | No reply is expected to such general calls unless individual stations are subsequently called upon to acknowledge rec... |
| `icao9432-extracted::communications_2_8_1_en::a685cef087951878` | `covered-synthetic rendered example phraseology` | `Icao9432Chunk01CommunicationsPhraseologyEvidenceTest`; SyntheticRenderedPhraseologyExample | Synthetic rendered-example evidence covers the §2.8.1.1 full-callsign initial-contact examples. |
| `icao9432-extracted::communications_2_8_1_en::b7acdc88125f1510` | `covered-synthetic rendered example phraseology` | `Icao9432Chunk01CommunicationsPhraseologyEvidenceTest`; SyntheticRenderedPhraseologyExample | Synthetic rendered-example evidence covers the §2.8.1.2 ground-station `ALL STATIONS` broadcast example. |
| `icao9432-extracted::readback_2_8_3_en::15940532b37f8528` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReadbackEvidenceTest.kt` | Clearances and instructions to enter, land on, take off from, hold short of, cross and backtrack on any runway shall ... |
| `icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReadbackEvidenceTest.kt` | Runway-in-use, altimeter settings, SSR codes, level instructions, heading and speed instructions, and transition leve... |
| `icao9432-extracted::readback_2_8_3_en::36e6ad16cffe8726` | `policy-blocked` | `POLICY-1` | Whenever possible, controllers should pass a route clearance to an aircraft before start-up. |
| `icao9432-extracted::readback_2_8_3_en::4b6ece953649da07` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReadbackEvidenceTest.kt` | Other clearances or instructions, including conditional clearances, shall be read back or acknowledged in a manner to... |
| `icao9432-extracted::readback_2_8_3_en::58594a8ee6243296` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReadbackEvidenceTest.kt` | ATC route clearances shall always be read back. |
| `icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ClearancePacingEvidenceTest.kt` (via new `EvidenceAuditOutcome.Advisory` leaf) | Controllers should pass a clearance slowly and clearly, avoid passing clearances during complicated taxiing, and on n... |
| `icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649` | `split: supported rendered controller templates covered; cancellation wording phraseology-later` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432PhraseologyEvidenceTest.kt`; `PHRASE-1` for cancellation wording and unsupported templates | The words 'TAKE OFF' are used only when an aircraft is cleared for take-off, or when canceling a take-off clearance; ... |
| `icao9432-extracted::readback_2_8_3_en::fe3b04ca9c3384d9` | `phraseology-later` | `PHRASE-1` | An ATC route clearance is not an instruction to take off or enter an active runway. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::17e1dfdf4ce57253` | `covered-green` | `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/requirements/Icao9432ReadbackConformanceSpec.kt` | The controller shall listen to the read-back to ascertain that the clearance or instruction has been correctly acknow... |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::4c808d67d281ff71` | `split: supported rendered readbacks covered; remaining templates phraseology-later` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432PhraseologyEvidenceTest.kt` (`LineUpReadback`, `FrequencyReadback`); `PHRASE-1` for unsupported templates | An aircraft should terminate the read-back by its call sign. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::ace4ab7ff5d53a66` | `covered-green` | `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/requirements/Icao9432ReadbackConformanceSpec.kt` | The controller shall take immediate action to correct any discrepancies revealed by the read-back. |
| `icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::ce25c18f1b44a6a8` | `not-applicable` | `none` | See: APPENDIX 1 DIFFERENCES FROM ICAO RADIOTELEPHONY PROCEDURES |
| `icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt` (controller-advised path via existing `ContactFrequency` emission) | An aircraft shall be advised by the appropriate aeronautical station to change from one radio frequency to another in... |
| `icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432PhraseologyEvidenceTest.kt` (rendered controller CONTACT [unit] [frequency] plus pilot [frequency] [callsign] readback) | Phraseology for frequency change includes 'CONTACT [Unit] [Frequency]' and readback 'Frequency Callsign'. |
| `icao9432-extracted::transfer_communications_2_8_2_en::b49ae03cbbb2d538` | `covered-green` | `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt` (pilot-notified path via G2 LOWG → LJMB `RequestFrequencyChange` emission) | In the absence of such advice, the aircraft shall notify the aeronautical station before such a change takes place. |

## Repair / Follow-Up Handoff

- `COMMS-1`: CLOSED `covered-green` at fn-50 via typed
  `ReceptionQuality` / `ReceptionDoubtCause` radio observations,
  `EvidenceFactPayload.ReceptionDoubt` + `AuditReceptionDoubtSubject`
  selector, and a chunk-01 source-mapped test asserting
  `assertNoFailures()` over a real stepped-on radio-overlap scenario.
  The old `.plan` repair-epic pointer is deleted.
- `POLICY-1`: UNCHANGED. Still cross-chunk infrastructure; covers
  no-reply general calls and route-clearance timing
  (`readback_2_8_3_en::36e6ad16cffe8726`). Tracked in `.plan`.
- `PHRASE-1`: PARTIALLY CLOSED by fn-78 for the
  `transfer_communications_2_8_2_en::96720e821bf926cc` CONTACT
  frequency-change phraseology row, by fn-81 for the supported rendered
  readback-termination templates (`LineUpReadback`, `FrequencyReadback`), and
  by fn-82 for supported rendered controller-template TAKE OFF word-use, and
  by fn-89 for synthetic rendered §2.8.1 full-callsign / ALL STATIONS examples.
  Still cross-chunk infrastructure; covers TAKE
  OFF cancellation wording, unsupported readback/controller templates,
  unit-only CONTACT variants, MONITOR, WHEN PASSING conditionals, and other
  phraseology units. Tracked in `.plan`.
- `FN33-MODEL-1`: PARTIALLY CLOSED. The §2.8.3.2 advisory-pacing
  source unit (`readback_2_8_3_en::ac9111d240cfd2c2`) landed
  `covered-green` at fn-48 via the new
  `EvidenceAuditOutcome.Advisory` audit-outcome leaf +
  `EvidenceFactPayload.ClearancePacing` projection +
  `Icao9432Chunk01ClearancePacingEvidenceTest`. The two other
  source units the `.plan` paragraph references — route-clearance
  timing (`readback_2_8_3_en::36e6ad16cffe8726`, now classified
  `policy-blocked` against POLICY-1) and TAKE OFF phraseology
  (`readback_2_8_3_en::f06dfa1cefd2d649`, now split by fn-82:
  supported rendered controller templates covered; cancellation wording and
  unsupported templates remain PHRASE-1) — keep the `.plan` paragraph as a
  partial-closure record, not deleted.
- `FN44-GAP-1`: CLOSED `covered-green` at fn-48 via existing
  `ContactFrequency` controller emission in LOWG circuit + new
  controller-advised adapter projection. `.plan` paragraph deleted.
- Pilot-notified frequency-change gap: CLOSED `covered-green` at fn-49
  via the G2 LOWG → LJMB `Request(RequestFrequencyChange(frequency =
  null))` emission + chunk-01 source-mapped test asserting
  `report.assertNoFailures()`.

## Review Considerations

- FP / type safety: permanent coverage reporting uses typed
  coverage states; the chunk-01 closure exercises a new
  `EvidenceAuditOutcome.Advisory` sealed leaf — every `when` over
  `EvidenceAuditOutcome` (audit report formatting,
  `assertNoFailures`, test-DSL helpers) was audited for the new
  branch.
- Test architecture: green tests cover structural readback and
  hearback classification (pre-existing) PLUS frequency-transfer
  (FN44-GAP-1 covered-green, pilot-notified branch covered-green),
  reception-doubt (COMMS-1 covered-green), clearance pacing
  (FN33-MODEL-1 covered-green Advisory), rendered CONTACT frequency-change
  phraseology (fn-78), and supported rendered readback-termination templates
  (fn-81), and supported rendered controller-template TAKE OFF word-use
  (fn-82). Remaining phraseology, policy, and the two remaining FN33-MODEL-1
  source units stay blocked.
- Impact: fn-50 adds final reception-quality observations and a minimal
  non-cognitive pilot `SayAgain` recovery path for stepped-on controller
  transmissions. Full cognitive-mission recovery remains filed as
  `D-AUDIT.15-FOLLOWUP`.
- Operational correctness: each covered/blocked row keeps the
  accepted ICAO 9432 source-unit id visible and cites the new
  `Icao9432Chunk01*EvidenceTest` files where applicable.
