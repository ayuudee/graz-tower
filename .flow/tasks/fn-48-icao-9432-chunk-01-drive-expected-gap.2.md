---
satisfies: [R3, R4, R5, R6, R7, R12]
---

## Description

Pattern-validator task. Closes BOTH FN44-GAP-1 (controller-advised frequency transfer) and FN44-GAP-2 (pilot-notified frequency change, no-advice fallback) in one pass.

**Key reuse**: this task adds **no new sealed types**. `EvidenceFactPayload.FrequencyTransfer(mode = ControllerAdvised | PilotNotifiedAbsentAdvice, …)` already exists (`EvidenceFacts.kt:142-148, :206-209`). `EvidenceGaps.ControllerAdvisedFrequencyTransferProjection` and `…PilotNotifiedFrequencyChangeProjection` already exist with typed `PlanItem("FN44-GAP-1"/"FN44-GAP-2")` (`EvidenceSourceCatalog.kt:215-233`). Source refs already wired under `ICAO9432.TransferCommunications` and already in `EvidenceSourceCatalog.All`.

**Expected outcome (anticipated, not contractual)**: **covered-red** for both. Per repo-scout, the sim does NOT currently emit `protocol.ContactFrequency` instructions (G2 models cross-aerodrome progression as release + autonomous first contact) and likely does not emit `protocol.RequestFrequencyChange` from the pilot side. Adapter projections will return empty `EvidenceFactSet`; `expect { … }` returns `fail(...)` because the regulation's mandatory transfer-by-advice cannot be satisfied. Test method asserts the expected `Fail` outcome via direct `report.results` inspection (NOT `assertNoFailures()`) — JUnit passes, build green. Two production-repair epics spawned (one per blocker). `.plan` entries REPLACED with one-line pointers.

If the sim turns out to emit either transmission type and the test lands `covered-green`, that is also acceptable — the test method calls `assertNoFailures()` and the `.plan` entry is deleted instead.

The work:

1. **Adapter projections** in `EvidenceFactAdapters` (sim → `EvidenceFactSet`). One for `protocol.ContactFrequency` instructions (controller side, GAP-1) and one for `protocol.RequestFrequencyChange` pilot transmissions (pilot side, GAP-2). Return `EvidenceFactSet` (existing API at `EvidenceFacts.kt:233`).
2. **New `frequencyTransfer(aircraftId)` selector** on `EvidenceExpectContext`, mirroring existing selectors at `EvidenceDsl.kt:304-369`.
3. **Paired source-mapped sim test** `Icao9432Chunk01FrequencyTransferEvidenceTest.kt` using the **actual DSL shape**:
   ```kotlin
   simEvidence("icao9432-chunk01-freq-transfer") {
       observe { /* compose facts via EvidenceFactAdapters.fromTransmissionRecords(...) or fromProjectedPayloads(...) */ }
       source("controller-advised-frequency-change") {
           cites(ICAO9432.TransferCommunications.ControllerAdvisedFrequencyChange)
           expect { /* frequencyTransfer(aircraftId).controllerAdvised(...) or fail(reason) */ }
       }
       source("pilot-notifies-absent-advice") {
           cites(ICAO9432.TransferCommunications.PilotNotifiesAbsentAdvice)
           expect { /* frequencyTransfer(aircraftId).pilotNotified(...) or fail(reason) */ }
       }
   }
   ```
   `source(id: String, build: AuditEvidenceCaseBuilder.() -> Unit)` is at `EvidenceDsl.kt:132-136`. `cites` is on the case builder at `:251-256`. `ProtocolEvidenceBuilder` has NO `source` function — do NOT use `protocolEvidence { source { … } }`. Two separate test methods (one per source unit) — one source unit per method per epic acceptance R6.
4. **Close `.plan` FN44-GAP-1 and FN44-GAP-2 entries** per the covered-green / covered-red rule.

**§2.8.2.1 is mandatory ("shall") on both branches**.

**Size:** M
**Files:**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt` (extend `EvidenceFactAdapters`, ~`:252-385`)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt` (add `frequencyTransfer(aircraftId)` selector on `EvidenceExpectContext`, ~`:304-369`)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt` (NEW — one test method per source unit)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFactsTest.kt` or similar — primitive-level adapter property tests
- `.plan` (close FN44-GAP-1, FN44-GAP-2 per rule)
- IF covered-red: 1-2 new `.flow/epics/fn-NN-….json` + `.flow/specs/fn-NN-….md` (via `flowctl epic create`)

## Approach

- **Adapter projection**: mirror existing `controllerFacts` / `pilotFacts` (`EvidenceFacts.kt:417-490`). Pure function — input → `EvidenceFactSet`. Match against `protocol.ContactFrequency` (controller side) / `protocol.RequestFrequencyChange` (pilot side); emit `FrequencyTransfer` payloads. Total under all reachable inputs.
- **`FACTS_PER_RECORD` offset allocation** (`EvidenceFacts.kt:658, FACTS_PER_RECORD = 10`): existing facts use `recordIndex * 10 + {0, 1, 2}` (`:429, :478, :518`). Reserve **`+3`** for controller-side `FrequencyTransfer(ControllerAdvised)` and **`+4`** for pilot-side `FrequencyTransfer(PilotNotifiedAbsentAdvice)` in this task. Tasks .3 and .4 reserve `+5` (ReceptionDoubt) and `+6` (ClearancePacing). This preserves the `EvidenceFactSet` unique-sequence invariant — collisions would break audit deduplication.
- **Property-test variant matrix** (R7 acceptance — explicit):
  - `{Controller speaker, Pilot speaker}` × `{Controller utterance type, Pilot utterance type}` — 4 combinations.
  - × `{matching payload (ContactFrequency or RequestFrequencyChange), non-matching payload}` — 8 base combinations.
  - + boundary cases: empty input list; single unrelated record; multiple records mixing match and non-match.
  - All combinations enumerated via property test (Kotest `forAll`-style or hand-rolled enumeration).
- **Selector pattern**: mirror `EvidenceExpectContext` selectors at `EvidenceDsl.kt:304-369`. Returns typed view of facts filtered by `aircraftId` and kind.
- **Test method assertion shape**:
  - covered-green: `report.assertNoFailures()`.
  - covered-red: `assertTrue("expected Fail for ${ref}", report.results.any { it.sources.contains(ref) && it.outcome is EvidenceAuditOutcome.Fail })`. Build green; audit honestly red.
- **Covered-red spawn** (if applicable): draft repair epic via `flowctl epic create` + `flowctl epic set-plan`. Spec includes failing audit case as closure signal, Goal & Context, Acceptance Criteria. No production code changes here.
- **`.plan` rule**: covered-green → delete blocker paragraph; covered-red → REPLACE paragraph with one-line pointer + Impact/Effort trailer pointing to repair epic id; NOT deleted until repair epic closes.

## Investigation targets

**Required**:
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:142-148, :206-231, :233` — `FrequencyTransfer` payload + `EvidenceFactSet`.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:252-385,417-490` — adapter projection patterns.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:122-176` — `SimEvidenceBuilder` (`observe` + `source` + variants).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:242-302` — `AuditEvidenceCaseBuilder` (cites, expect).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:304-369` — `EvidenceExpectContext` selectors.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:311-323` — outcome helpers (`pass`/`fail`/`vacuous`/`expectedGap`).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt:152-167, :215-233, :245` — existing TransferCommunications + Gap IDs + `All` Set.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReadbackEvidenceTest.kt` — pattern reference (but note: that file uses `protocolEvidence { structuralReadback { … } }`; THIS test uses `simEvidence("name") { observe { … }; source("id") { cites; expect } }`).
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/Instruction.kt:1084-1088` — `ContactFrequency`.
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/PilotTransmission.kt:235` — `RequestFrequencyChange`.

**Optional**:
- `research/txt/icao9432-extracted.txt:3832-3834` — §2.8.2.1 verbatim.
- `.plan:225-242` — existing FN44-GAP-1 / FN44-GAP-2 entries.

## Key context

§2.8.2.1 mandatory on both branches; hard pin. Advisory pattern is FN33-MODEL-1's (task .4).

Per memory `dynamic-injection-sim-tests-must-gate-2026-05-16`: sim tests gate on post-step SimState.

The likely covered-red landing is HONEST — it reflects what the sim currently does. The test correctly asserts the regulation's requirement; the audit honestly reports the gap; the spawned repair epic tracks the production fix. This is the "no deferment, no surprises" workflow.

## Acceptance

- [ ] `EvidenceFactAdapters` controller-advised frequency-transfer projection added; emits `FrequencyTransfer(mode = ControllerAdvised, target = …)` from `ContactFrequency` instructions; returns `EvidenceFactSet`.
- [ ] `EvidenceFactAdapters` pilot-notified frequency-change projection added; emits `FrequencyTransfer(mode = PilotNotifiedAbsentAdvice, target = …)` from `RequestFrequencyChange` transmissions; returns `EvidenceFactSet`.
- [ ] Both projections total under property tests over the explicit 8-combination matrix (`{Controller, Pilot} speaker × {Controller, Pilot} utterance × {match, non-match} payload`) + boundary cases (empty / single unrelated / mixed).
- [ ] `EvidenceExpectContext.frequencyTransfer(aircraftId)` selector added; primitive-level unit tests cover both modes.
- [ ] `Icao9432Chunk01FrequencyTransferEvidenceTest.kt` created with TWO test methods (one per source unit), each using `simEvidence("name") { observe { … }; source("case-id") { cites(ICAO9432.TransferCommunications.…); expect { … } } }`. Cite refs are typed (NOT raw `SourceUnitRef("…")` strings).
- [ ] `./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01FrequencyTransferEvidenceTest"` executes. For each of the two test methods, EITHER:
  - covered-green: method calls `report.assertNoFailures()`; JUnit green; chunk row records `covered-green`; `.plan` paragraph deleted.
  - covered-red: method does NOT call `assertNoFailures()`; asserts directly that `report.results` contains the expected `Fail` outcome for the cited ref; JUnit green; chunk row records `covered-red`; named production-repair epic spec exists; `.plan` paragraph REPLACED with one-line pointer.
- [ ] `./gradlew-nix :controller:jvmTest --tests "*.SourceUnitCitationValidationTest"` and `./gradlew-nix :sim:jvmTest --tests "*.EvidenceSourceCatalog*"` still green.
- [ ] `.plan` FN44-GAP-1 and FN44-GAP-2 closed per the rule.
- [ ] `./gradlew-nix build` and `./gradlew-nix detekt` both green.
- [ ] No production-code changes outside the test tree and `.plan`.

## Done summary
Closed FN44-GAP-1 (covered-green) and FN44-GAP-2 (covered-red) by adding frequency-transfer projections (FACTS_PER_RECORD +3 controller-advised, +4 pilot-notified) to EvidenceFactAdapters, a frequencyTransfer(aircraftId) selector on EvidenceExpectContext, primitive property tests covering the full speaker x utterance x payload matrix plus boundary cases, and a paired source-mapped sim test Icao9432Chunk01FrequencyTransferEvidenceTest that lands each source unit per the covered-green / covered-red contract. Spawned production-repair epic fn-49-sim-emits-pilot-notified-frequency to track the pilot-side gap closure; .plan GAP-1 paragraph deleted, GAP-2 replaced with one-line pointer.
## Evidence
- Commits: 0f10956e0149a987aafe160356ad1b0ee24def01
- Tests: ./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01FrequencyTransferEvidenceTest" — 2 tests, 0 failures (covered-green: controller-advised; covered-red: pilot-notified), ./gradlew-nix :sim:jvmTest --tests "*.EvidenceFactsTest" — 18 tests, 0 failures (matrix + boundaries + selector primitives), ./gradlew-nix :sim:jvmTest --tests "*.EvidenceSourceCatalog*" — green, ./gradlew-nix :controller:jvmTest --tests "*.SourceUnitCitationValidationTest" — green, ./gradlew-nix detekt --rerun-tasks — green (jvmTest tree not in detekt scope; no production changes)
- PRs: