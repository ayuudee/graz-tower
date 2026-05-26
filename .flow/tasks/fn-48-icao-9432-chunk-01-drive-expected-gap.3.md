---
satisfies: [R1, R5, R6, R7, R12]
---

## Description

Close COMMS-1 by building a typed reception-doubt evidence primitive bound to ICAO 9432 §2.8.1.4 ("If there is doubt that a message has been correctly received, a repetition of the messages shall be requested either in full or in part"). Author a source-mapped sim test for canonical id `icao9432-extracted::communications_2_8_1_en::0a964f42b6100596`.

**Expected outcome (anticipated, not contractual)**: **covered-red**. The sim has no reception-quality signal infrastructure. The adapter projection over current sim traces returns an empty `EvidenceFactSet`. The test's `expect { … }` returns `fail("no doubt observations available; sim lacks reception-quality modelling")`. Test method asserts the expected `Fail` is in `report.results`. Spawned production-repair epic tracks the sim-side addition of reception-doubt signals; `.plan` COMMS-1 is REPLACED (not deleted) with a one-line pointer to that repair epic.

This is the honest landing per AGENTS.md commandment 4 (tests prove the real job): the test exercises the real sim behaviour (which doesn't produce doubt observations), the audit honestly reports `Fail`, the spawned epic tracks the production fix. We do NOT use `fromProjectedPayloads` to fabricate compliant doubt facts and claim covered-green — that would prove the type can represent compliance, not that any behaviour requests repetition when doubt is observed.

**Key design decisions** (pinned by plan-review):

- **Trigger / response separation** (per practice-scout): "reception doubt" (the trigger) and `protocol.SayAgain` (the response) are **distinct types linked by a typed reference**. The doubt fact carries `resolvedBy: SayAgainRef?`. NO back-reference from `SayAgain` to doubt (no retroactive mutability).
- **Scope of doubt**: fact is about a **transmission instance**, not a channel-quality window. Time decay semantics deferred.
- **Projection result API**: returns `EvidenceFactSet` (existing API). Empty set on absence; outcomes from `expect { … }` carry the semantic via `fail` / `pass` / `vacuous` / `expectedGap`.

**§2.8.1.4 is mandatory ("shall")**.

This task DOES add new sealed leaves to `EvidenceFactPayload` (and a new `EvidenceFactKind` enum value); new-field / new-audit principle applies — grep every consumer.

**Size:** M
**Files:**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt` — new `ReceptionDoubt` payload + `EvidenceFactKind.ReceptionDoubt` + adapter projection.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt` — new selector.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ReceptionDoubtEvidenceTest.kt` — NEW test (one method targeting the COMMS-1 source unit).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFactsTest.kt` — primitive-level adapter property tests (extend the existing file landed by task .2; mirror its 8-combination matrix layout, boundary-case methods, and selector-primitive `simEvidence(...) { … }` tests). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.2 landed property tests in EvidenceFactsTest.kt with a specific matrix + boundary + selector-primitive shape -->
- `.plan` — close COMMS-1 per rule (paragraph REPLACED with repair-epic pointer for covered-red).
- IF covered-red: spawn `flow-next` repair epic.

## Approach

- **Sealed leaf addition** on `EvidenceFactPayload` (`EvidenceFacts.kt:74-162`):
  `ReceptionDoubt(transmissionRef, doubtSource: ReceptionDoubtSource, resolvedBy: SayAgainRef? = null)`. `ReceptionDoubtSource` is a sealed sub-type with leaves like `PartialReception`, `Unintelligibility`, `SteppedOn`, `Other`.
- **Grep ALL `when` consumers** of `EvidenceFactPayload` after adding; no `else -> Unit`.
- **`EvidenceFactKind.ReceptionDoubt`** enum value at `:164-174`; exhaust every consumer.
- **Typed `SayAgainRef`**: thin inline value class wrapping a transmission/instruction id. `SayAgain` instruction lives at `protocol/.../PilotTransmission.kt:313-315`. Do NOT modify `SayAgain` itself.
- **Adapter projection**: pure function over current sim transmission records → `EvidenceFactSet`. Reserve `FACTS_PER_RECORD` offset **`+5`** for `ReceptionDoubt` facts (recordIndex * 10 + 5). Existing offsets: 0, 1, 2. Task .2 reserves 3 and 4 for FrequencyTransfer; task .4 reserves 6 for ClearancePacing. Preserves `EvidenceFactSet` unique-sequence invariant. Total under property tests over the matrix:
  - `{Controller, Pilot} speaker × {Controller, Pilot} utterance × {has doubt-source observability, lacks doubt-source observability}` payload variants — 8 base combinations.
  - + boundary: empty input; single unrelated record; multiple records.
  - Real current behaviour: ALL combinations return empty `EvidenceFactSet` because sim has no doubt signal — that IS the test signal.
- **Selector**: `EvidenceExpectContext.receptionDoubt(aircraftId)` at `EvidenceDsl.kt:304-369`. Returns a new companion class `AuditReceptionDoubtSubject` (internal constructor, takes `aircraftId`, `facts: List<EvidenceFact>`, `activate: (FactId) -> Unit`) mirroring the established `AuditFrequencyTransferSubject` pattern landed by task .2 at `EvidenceDsl.kt:496-551`. Branch methods on the subject (e.g. `requiresRepetitionResponse()`) return `EvidenceAuditOutcome` and activate matching facts on success. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.2 landed AuditFrequencyTransferSubject companion pattern not anticipated in the original plan -->
- **Test shape** (sim-level, ONE test method targeting COMMS-1):
  ```kotlin
  simEvidence("icao9432-chunk01-reception-doubt") {
      observe { /* compose facts via fromTransmissionRecords or current sim trace */ }
      source("doubt-triggers-repetition-request") {
          cites(ICAO9432.Communications.ReceptionDoubtRepetitionRequested) <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.1 used ReceptionDoubtRepetitionRequested not ReceptionDoubt -->
          expect { receptionDoubt(aircraftId).requiresRepetitionResponse(…) /* returns fail() if empty */ }
      }
  }
  ```
  Cite the new `ICAO9432.Communications.ReceptionDoubtRepetitionRequested` source ref (added in task .1). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.1 used ReceptionDoubtRepetitionRequested not ReceptionDoubt -->
- **Test method assertion shape (covered-red)**: do NOT call `report.assertNoFailures()`. Instead: `assertTrue(report.results.any { it.sources.contains(ICAO9432.Communications.ReceptionDoubtRepetitionRequested) && it.outcome is EvidenceAuditOutcome.Fail })`. JUnit passes; audit honestly reports `Fail`. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.1 used ReceptionDoubtRepetitionRequested not ReceptionDoubt -->
- **Covered-red spawn**: draft repair epic via `flowctl epic create`. Goal: "Add reception-doubt observation infrastructure to sim". Acceptance: this test transitions from `Fail` to `Pass` for the cited ref. `.plan` COMMS-1 REPLACED with one-line pointer using the format established by task .2's `fn-49-sim-emits-pilot-notified-frequency` spawn: `**COMMS-1 — …** — tracked by \`fn-NN-<verb>-<noun>\` (…). Impact: M | Effort: M`. Suggested naming: `fn-NN-sim-emits-reception-doubt` or `fn-NN-sim-models-reception-quality`. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.2 established fn-49-sim-emits-pilot-notified-frequency as the spawn-naming precedent -->

## Investigation targets

**Required**:
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:68-72` — `EvidenceFact`.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:74-162` — `EvidenceFactPayload` sealed root (extend).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:164-174` — `EvidenceFactKind` enum.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:233` — `EvidenceFactSet`.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:252-385,417-490` — adapter patterns.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt` — new `ICAO9432.Communications.ReceptionDoubt` (added in task .1).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:122-176` — `SimEvidenceBuilder`.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:242-302` — `AuditEvidenceCaseBuilder`.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:304-369` — selectors.
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/PilotTransmission.kt:313-315` — `SayAgain`.
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/observe/Event.kt:192` — `SayAgain` consumption (no readback consequence).

**Optional**:
- `research/txt/icao9432-extracted.txt:3667-3668` — §2.8.1.4 verbatim.
- Task .2's landed pattern (for the adapter + selector + covered-red-test flow).

## Key context

`SayAgain` has NO readback consequence — modelling doubt is a new fact-type concern.

New sealed-leaf additions force `when` updates across multiple consumers; **NO `else -> Unit` shortcuts**.

The covered-red landing is the **honest** outcome here. The primitive ships (typed fact representation lands in the audit core); the test correctly asserts the regulation; the audit reports `Fail` because sim doesn't model the input; the repair epic adds the input modeling. This is "no deferment, no surprises" in action — the debt is named, visible, tracked.

## Acceptance

- [ ] `EvidenceFactPayload.ReceptionDoubt(transmissionRef, doubtSource: ReceptionDoubtSource, resolvedBy: SayAgainRef? = null)` sealed leaf added. `ReceptionDoubtSource` is a sealed sub-type with at least `PartialReception`, `Unintelligibility`, `SteppedOn`, `Other`.
- [ ] `EvidenceFactKind.ReceptionDoubt` enum value added; every `when` consumer of `EvidenceFactKind` exhausts it. Every `when` consumer of `EvidenceFactPayload` exhausts the new leaf.
- [ ] Adapter projection in `EvidenceFactAdapters` returns `EvidenceFactSet`; total under property tests over the speaker × utterance × payload matrix + boundary cases.
- [ ] `EvidenceExpectContext.receptionDoubt(aircraftId)` selector added; covered by primitive-level unit tests.
- [ ] `Icao9432Chunk01ReceptionDoubtEvidenceTest.kt` created with ONE test method using `simEvidence(...) { observe { … }; source("id") { cites(ICAO9432.Communications.ReceptionDoubtRepetitionRequested); expect { … } } }`. Cite ref is typed. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.1 used ReceptionDoubtRepetitionRequested not ReceptionDoubt -->
- [ ] `./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01ReceptionDoubtEvidenceTest"` executes. Test method passes via direct `report.results` assertion (covered-red expected) OR via `report.assertNoFailures()` (covered-green if sim surprisingly produces doubt facts).
- [ ] `./gradlew-nix :controller:jvmTest --tests "*.SourceUnitCitationValidationTest"` and `./gradlew-nix :sim:jvmTest --tests "*.EvidenceSourceCatalog*"` still green.
- [ ] If covered-red (anticipated): named production-repair epic spec exists (Goal & Context + Acceptance Criteria + this test's `Fail` outcome as closure signal). `.plan` COMMS-1 REPLACED with one-line pointer to repair epic (NOT deleted).
- [ ] If covered-green: `.plan` COMMS-1 paragraph deleted.
- [ ] All existing `when`-on-`EvidenceFactPayload`/`EvidenceFactKind` consumers still pass; no `else -> Unit`.
- [ ] `./gradlew-nix build` and `./gradlew-nix detekt` both green.
- [ ] No production-code changes outside the test tree and `.plan`.

## Done summary
Closed COMMS-1 (ICAO 9432 §2.8.1.4) in covered-red: typed `EvidenceFactPayload.ReceptionDoubt` + `ReceptionDoubtSource` sealed leaves + `SayAgainRef` linkage + adapter projection (wired through controller `Instruct`/`Respond` and pilot arms at sequence offset +5) + `AuditReceptionDoubtSubject.requiresRepetitionResponse()` selector + chunk-01 source-mapped test asserting `Fail` on `report.results`. Spawned production-repair epic `fn-50-sim-models-reception-quality-comms-1`; `.plan` COMMS-1 paragraph replaced with one-line pointer. Codex impl-review: NEEDS_WORK (activation-on-Fail-path + Respond-arm wiring) -> SHIP after fixes. Memory captured: `bug/test-failures/audit-selectors-must-activate-examined-2026-05-26`.
## Evidence
- Commits: 6d1bf478c290336e17ff8d835ff35b727eefb58d, b4f205469bcbc2437406ec6b944fa260fc26c0b3, e77ef1d6ae44311ebe1d3268b3369e03f915968f
- Tests: ./gradlew-nix :sim:jvmTest --tests '*.Icao9432Chunk01ReceptionDoubtEvidenceTest' (pass; covered-red asserted on report.results), ./gradlew-nix :sim:jvmTest --tests '*.EvidenceFactsTest' (pass; matrix + boundary + selector + Respond-arm wiring), ./gradlew-nix :sim:jvmTest --tests '*.EvidenceSourceCatalog*' (pass), ./gradlew-nix :controller:jvmTest --tests '*.SourceUnitCitationValidationTest' (pass), ./gradlew-nix detekt (pass), ./gradlew-nix build (pass)
- PRs: