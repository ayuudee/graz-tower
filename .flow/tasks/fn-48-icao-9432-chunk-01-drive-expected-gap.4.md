---
satisfies: [R2, R5, R6, R7, R12]
---

## Description

Close FN33-MODEL-1 by building a typed clearance-pacing / workload evidence primitive bound to ICAO 9432 §2.8.3.2: "Controllers should pass a clearance slowly and clearly … should avoid passing a clearance to a pilot engaged in complicated taxiing manoeuvres … on no occasion should a clearance be passed when the pilot is engaged in line up or take-off manoeuvres." Author a source-mapped sim test for canonical id `icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2`.

**This is the largest task** because §2.8.3.2 is **advisory ("should" / "avoid" / "on no occasion")**, not mandatory. Per practice-scout RFC 2119 discipline: advisory obligations must run-and-record, never block CI. **Design decision (pinned by plan-review)**: advisory observation surfaces as a NEW `EvidenceAuditOutcome.Advisory(violations: List<AdvisoryViolation>, reason: String)` sealed leaf added to the existing audit-outcome surface. No external NDJSON / side-effect files. `EvidenceReport.assertNoFailures()` continues to treat only `Fail` as failing; `Advisory` is reported and counted but does not block.

**Expected outcome (anticipated)**: **covered-green**. The sim has phase signals on `PilotPhase` sealed interface (`pilot/.../PilotPhase.kt:22, :24, :28`): `Taxiing`, `LinedUp`, `TakeoffRoll`. The adapter observes clearances issued during these phases; emits `ClearancePacing` facts mapping `PilotPhase → PacingWindow`; the test's `expect { … }` returns `advisory(violations, reason)` when violations observed (or `pass(...)` if none); `report.assertNoFailures()` passes because `Advisory` is not `Fail`. Coverage row records `covered-green` (the audit honestly observes the regulation's advisory outcome).

If the sim turns out to lack the necessary phase signals at clearance-issue time, the honest landing is `covered-red` (adapter returns empty `EvidenceFactSet`, `expect { … }` returns `fail(...)`, spawned repair epic adds the signals).

**Distinctness from POLICY-1**: this is **observable conditions** (was a clearance issued during complicated-taxi window? line-up window?), NOT prescriptive pacing rules. If design starts encoding "the controller MUST wait until taxi is simple", halt — POLICY-1 territory; raise `QUESTION:` in AGENT_DIALOGUE.md.

**Critical design fix (round 2)**: `PacingWindow` is an **`enum class`** (not sealed sub-type). Kotlin sealed types do not have `.entries`; enums do. Per memory `predicate-guards-over-sealed-types-must-2026-05-16`: predicate guards over closed enumerations use `entries` exhaustion in tests. Enum is the right shape here.

**Size:** M (largest in epic)
**Files:**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt` — new `ClearancePacing` payload + `EvidenceFactKind.ClearancePacing` + `enum class PacingWindow` + adapter projection.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt` — new `EvidenceAuditOutcome.Advisory` sealed leaf at `:20-24`; new `advisory(...)` helper at `:311-323`; update `assertNoFailures()` at `:60` (semantics already correct — `Advisory` is not `Fail` — but verify after adding the leaf); update report rendering to render `Advisory` distinctly; new `clearancePacing(aircraftId)` selector.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01ClearancePacingEvidenceTest.kt` — NEW source-mapped sim test (one method targeting FN33-MODEL-1).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFactsTest.kt` / `EvidenceDslTest.kt` — primitive-level tests for new leaves and helpers. Extend the existing `EvidenceFactsTest.kt` landed by task .2; mirror its matrix layout (here: speaker × utterance × `PacingWindow.entries`), boundary-case methods, and selector-primitive `simEvidence(...) { … }` tests. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.2 landed property tests in EvidenceFactsTest.kt with a specific matrix + boundary + selector-primitive shape -->
- `.plan` — close FN33-MODEL-1 per rule.
- IF covered-red: spawn repair epic.

## Approach

- **New audit-outcome leaf** at `EvidenceDsl.kt:20-24`: extend `EvidenceAuditOutcome` sealed interface with `data class Advisory(val violations: List<AdvisoryViolation>, val reason: String) : EvidenceAuditOutcome`. `AdvisoryViolation` is a small typed record (`(clearanceRef, observedWindow: PacingWindow)`).
- **`assertNoFailures()` semantics** (`EvidenceDsl.kt:60`): filters `is EvidenceAuditOutcome.Fail` only — `Advisory` not counted. Verify after the leaf addition that the filter still only catches `Fail`.
- **Update `when` consumers** of `EvidenceAuditOutcome` (especially report rendering around `:50-65` and the post-evaluation filter at `:289-292` which checks `outcome \!is ExpectedGap && outcome \!is Vacuous` — that filter logic may need updating to also include `Advisory` as a non-Fail outcome).
- **`advisory(violations, reason)` builder helper** alongside `pass`/`fail`/`vacuous`/`expectedGap` at `EvidenceDsl.kt:311-323`. Returns `EvidenceAuditOutcome.Advisory`.
- **Sealed leaf addition** on `EvidenceFactPayload`: `ClearancePacing(clearanceRef, issuedDuring: PacingWindow)`. `PacingWindow` is an `enum class`:
  ```kotlin
  enum class PacingWindow { ComplicatedTaxi, LineUp, TakeoffRoll, Other }
  ```
  Grep all `when` consumers.
- **`EvidenceFactKind.ClearancePacing`** enum addition.
- **Adapter projection**: pure function emitting `ClearancePacing` facts into `EvidenceFactSet`. Reserve `FACTS_PER_RECORD` offset **`+6`** for `ClearancePacing` facts (recordIndex * 10 + 6). Existing offsets: 0, 1, 2; task .2 reserves 3 and 4 (FrequencyTransfer); task .3 reserves 5 (ReceptionDoubt). Preserves `EvidenceFactSet` unique-sequence invariant. Total under property tests over:
  - `{Controller, Pilot} speaker × {Controller, Pilot} utterance × {has phase=ComplicatedTaxi at issue, LineUp at issue, TakeoffRoll at issue, Other at issue}` payload variants — 16 base combinations. Use `PacingWindow.entries` for the inner enumeration.
  - + boundary: empty input; single clearance with no concurrent phase data; multiple clearances at different phases.
- **Selector**: `EvidenceExpectContext.clearancePacing(aircraftId)`. Returns a new companion class `AuditClearancePacingSubject` (internal constructor, takes `aircraftId`, `facts: List<EvidenceFact>`, `activate: (FactId) -> Unit`) mirroring the established `AuditFrequencyTransferSubject` pattern landed by task .2 at `EvidenceDsl.kt:496-551`. Branch methods on the subject (e.g. `whenIssuedDuring(windows: List<PacingWindow>)`) return `EvidenceAuditOutcome` — `Advisory(...)` when violations observed, `Pass` when none, `Fail` when prerequisite facts absent. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.2 landed AuditFrequencyTransferSubject companion pattern not anticipated in the original plan -->
- **Activation discipline on the selector — MANDATORY** (per memory `bug/test-failures/audit-selectors-must-activate-examined-2026-05-26`, surfaced as a Major impl-review finding on task .3): once the relevant facts are filtered (`facts.filter { … pacing payload + aircraftId match … }`), call `activate(fact.id)` for **every fact the selector consulted** *before* branching into `Advisory` / `Pass` / `Fail`. This applies to ALL non-empty paths: `Advisory` (violations observed), `Pass` (no violations observed), and the regulation-specific `Fail` (prerequisite facts present but malformed). Only the "no facts at all → Fail" path can stay un-activated — that path correctly surfaces as the generic activation-check Fail. Without this, `AuditEvidenceCaseBuilder.toCase` overrides the selector's specific outcome (including the `Advisory` violations list and the per-window evidence) with the generic "did not activate any evidence facts" Fail, silently destroying the diagnostic. Task .3's `AuditReceptionDoubtSubject` is the reference shape (`EvidenceDsl.kt:589-634`). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.3 surfaced activation-on-both-paths discipline as Major impl-review finding -->
- **Adapter wiring sites — pin them explicitly**: `ClearancePacing` projection emits clearance-pacing facts at clearance-issue moments. Clearances arrive via `ControllerOutput.Instruct.instruction` (the `AtcInstruction` carrying the clearance). Wire the `clearancePacingFact(...)` helper into `controllerFacts(...)` at the existing controller-arm call site (`EvidenceFacts.kt:496-551`), pinned to the `ControllerOutput.Instruct` branch — clearances are not `ControllerOutput.Respond` payloads. Mirror the `controllerAdvisedFrequencyTransferFact(...)` call pattern at `EvidenceFacts.kt:538-545` (returns `EvidenceFact?`; `listOfNotNull(…)` at the end of the Instruct branch). Do NOT wire into the pilot arm — `ClearancePacing` is a property of controller-issued clearances, not pilot transmissions. (Contrast with `ReceptionDoubt` in task .3 which IS wired through both arms because doubt is a property of any transmission instance.) <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.3 established ControllerOutput.Instruct / Respond arm wiring as a deliberate per-payload decision -->
- **Test shape** (sim-level, ONE test method):
  ```kotlin
  simEvidence("icao9432-chunk01-clearance-pacing") {
      observe { /* compose facts via fromTransmissionRecords or scenario trace exposing clearance-issue moments + concurrent phase */ }
      source("clearance-pacing-during-complicated-or-critical-phases") {
          cites(ICAO9432.Readback.ClearancePacingAdvisory) <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.1 used ClearancePacingAdvisory not ClearancePacing -->
          expect { clearancePacing(aircraftId).whenIssuedDuring(PacingWindow.entries.filter { /* not Other */ }) /* returns advisory(violations) if observed, pass() if none */ }
      }
  }
  ```
  Cite the new `ICAO9432.Readback.ClearancePacingAdvisory` source ref (added in task .1). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.1 used ClearancePacingAdvisory not ClearancePacing -->
- **Test method assertion shape**:
  - covered-green (Advisory observed): call `report.assertNoFailures()` — passes because `Advisory` is not `Fail`. Additionally `assertTrue(report.results.any { it.outcome is EvidenceAuditOutcome.Advisory })` to confirm the advisory was emitted (not vacuous).
  - covered-red (if sim lacks signals): direct assertion on `report.results` for `Fail`; spawn repair epic.
- **No external files**: no NDJSON, no side effects. Advisory observations live entirely in audit-outcome surface.
- **`.plan` rule**: covered-green → delete paragraph; covered-red → REPLACE with one-line pointer using the format established by task .2's `fn-49-sim-emits-pilot-notified-frequency` spawn and task .3's `fn-50-sim-models-reception-quality-comms-1` spawn: `**FN33-MODEL-1 — …** — tracked by \`fn-NN-<verb>-<noun>\` (…). Impact: M | Effort: M`. Next NN in sequence is **`fn-51`** (after .3 spawned `fn-50`). Suggested naming if covered-red lands: `fn-51-sim-emits-clearance-pacing-signals-fn33-model-1` or `fn-51-sim-models-phase-at-clearance-issue-fn33-model-1` (precedent: task .3 used the `<descriptor>-<unit-id>` suffix pattern, e.g. `fn-50-sim-models-reception-quality-comms-1`). <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.3 spawned fn-50 and established the <descriptor>-<unit-id> suffix; next NN in sequence is fn-51 -->

## Investigation targets

**Required**:
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:20-24` — `EvidenceAuditOutcome` sealed interface (extend).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:60` — `assertNoFailures()` filter.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:50-65` — report rendering.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:289-292` — post-evaluation filter (`\!is ExpectedGap && \!is Vacuous` — needs `Advisory` too).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceDsl.kt:311-323` — outcome helpers (add `advisory`).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:74-162,164-174,233` — sealed leaf + kind enum + EvidenceFactSet.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:252-385` — adapter patterns.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceSourceCatalog.kt` — new `ICAO9432.Readback.ClearancePacing` (from task .1).
- Sim taxi / line-up / takeoff-roll phase signals: `PilotPhase.Taxiing`, `PilotPhase.LinedUp`, `PilotPhase.TakeoffRoll` at `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotPhase.kt:22, :24, :28`. Adapter maps `PilotPhase → PacingWindow` (semantic window category, not 1:1 mirror).
- Memory `test-pin-discipline-2026-05-15` — source-mapped test discipline.

**Optional**:
- `research/txt/icao9432-extracted.txt:3909-3914` — §2.8.3.2 verbatim.
- Task .2's landed test file — for established `simEvidence("name") { observe; source("id") { cites; expect } }` shape.

## Key context

Highest-risk task for POLICY-1 boundary slip. Line to hold: **observable conditions**, NOT **prescriptive rules**.

The `Advisory` outcome leaf is a minimal extension — keep scoped to advisory observability. Future chunks reuse this leaf; do NOT pre-generalize.

Per memory `predicate-guards-over-sealed-types-must-2026-05-16`: `PacingWindow.entries` exhaustion in tests.

Per memory `dynamic-injection-sim-tests-must-gate-2026-05-16`: sim test gates on post-step state.

Per memory `bug/test-failures/audit-selectors-must-activate-examined-2026-05-26` (landed during .3 fix-loop): `AuditEvidenceCaseBuilder.toCase` silently overrides selector outcomes with a generic "did not activate any evidence facts" `Fail` when `activationFactIds` is empty AND outcome is not `ExpectedGap` / `Vacuous` / (assumed: `Advisory` — verify this branch is added to the framework's filter when the new `Advisory` leaf lands). The reception-doubt selector hit this on its Fail path; the clearance-pacing selector's `Advisory` and `Pass` paths are at the same risk. Activate every consulted fact before branching outcomes. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.3 surfaced this pitfall via Codex impl-review -->

## Acceptance

- [ ] `EvidenceAuditOutcome.Advisory(violations: List<AdvisoryViolation>, reason: String)` sealed leaf added at `EvidenceDsl.kt:20-24`. `AdvisoryViolation` is a typed record carrying `(clearanceRef, observedWindow: PacingWindow)`.
- [ ] Every `when`-over-`EvidenceAuditOutcome` consumer exhausts `Advisory` (no `else ->`). Includes: report rendering, `assertNoFailures` filter, post-evaluation outcome filter at `:289-292`.
- [ ] `EvidenceReport.assertNoFailures()` (`:60`) treats `Advisory` as NON-failing — only `Fail` triggers failure.
- [ ] `advisory(violations: List<AdvisoryViolation>, reason: String)` helper added alongside `pass`/`fail`/`vacuous`/`expectedGap` at `EvidenceDsl.kt:311-323`.
- [ ] `EvidenceFactPayload.ClearancePacing(clearanceRef, issuedDuring: PacingWindow)` sealed leaf added.
- [ ] `enum class PacingWindow { ComplicatedTaxi, LineUp, TakeoffRoll, Other }` declared. Tests use `PacingWindow.entries` for exhaustion.
- [ ] `EvidenceFactKind.ClearancePacing` enum value added; every consumer exhausts it.
- [ ] Adapter projection in `EvidenceFactAdapters` returns `EvidenceFactSet`; total under property tests over `{Controller, Pilot} speaker × {Controller, Pilot} utterance × PacingWindow.entries` (16 base combinations) + boundary cases.
- [ ] `EvidenceExpectContext.clearancePacing(aircraftId)` selector added; primitive-level unit tests cover all `PacingWindow.entries`.
- [ ] `AuditClearancePacingSubject` calls `activate(fact.id)` for every fact the selector consulted on ALL non-empty paths (`Advisory`, `Pass`, and any regulation-specific `Fail`) BEFORE branching outcomes. Only the "no facts at all" path may stay un-activated. Verified by at least one selector-level test that constructs an evidence set producing a non-empty regulation-specific outcome and asserts (a) `outcome` is the expected variant, (b) `activationFactIds.isNotEmpty()` on the resulting `EvidenceAuditResult`, (c) for `Advisory`: `outcome.reason` contains the selector's specific reason substring (not the generic `did not activate any evidence facts` string), (d) `outcome.violations` (for `Advisory`) carries the per-window diagnostic records the selector produces. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.3 surfaced this discipline; memory bug/test-failures/audit-selectors-must-activate-examined-2026-05-26 -->
- [ ] `Icao9432Chunk01ClearancePacingEvidenceTest.kt` created with ONE test method (sim-level) using `simEvidence(...) { observe { … }; source("id") { cites(ICAO9432.Readback.ClearancePacingAdvisory); expect { … } } }`. <!-- Updated by plan-sync: fn-48-icao-9432-chunk-01-drive-expected-gap.1 used ClearancePacingAdvisory not ClearancePacing -->
- [ ] `./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01ClearancePacingEvidenceTest"` executes. Test method passes via `report.assertNoFailures()` (covered-green, advisory observed) AND asserts `report.results.any { it.outcome is EvidenceAuditOutcome.Advisory }` to confirm Advisory was emitted; OR (covered-red fallback) direct `Fail` assertion on `report.results`.
- [ ] `./gradlew-nix :controller:jvmTest --tests "*.SourceUnitCitationValidationTest"` and `./gradlew-nix :sim:jvmTest --tests "*.EvidenceSourceCatalog*"` still green.
- [ ] No external NDJSON / side-effect files introduced.
- [ ] If covered-red: named production-repair epic spec exists; `.plan` FN33-MODEL-1 REPLACED with one-line pointer. If covered-green: paragraph deleted.
- [ ] `./gradlew-nix build` and `./gradlew-nix detekt` both green.
- [ ] No production-code changes outside the test tree and `.plan`.
- [ ] If design pressure pushes into POLICY-1 territory: HALT, raise `QUESTION:` in AGENT_DIALOGUE.md, do NOT silently expand scope.

## Done summary
Closed FN33-MODEL-1 (covered-green via Advisory) for ICAO 9432 §2.8.3.2 by adding a typed EvidenceAuditOutcome.Advisory audit-outcome leaf and an EvidenceFactPayload.ClearancePacing projection wired through ControllerOutput.Instruct only; the chunk-01 source-mapped test Icao9432Chunk01ClearancePacingEvidenceTest lands the §2.8.3.2 advisory source unit icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2 covered-green by observing real LOWG circuit-training clearances during sensitive pilot phases without blocking the build. The .plan FN33-MODEL-1 paragraph was rewritten to reflect the partial closure. Codex impl-review SHIP on first pass.
## Evidence
- Commits: c2d0a51a0567e9f4dd3b6f508422dcee935e5c96
- Tests: ./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01ClearancePacingEvidenceTest" — 1 test green, ./gradlew-nix :sim:jvmTest --tests "*.EvidenceFactsTest" — 37 tests green (9 new clearance-pacing), ./gradlew-nix :sim:jvmTest --tests "*.EvidenceSourceCatalog*" --tests "*.EvidenceDslTest" --tests "*.EvidenceSelectorTest" — all green, ./gradlew-nix :controller:jvmTest --tests "*.SourceUnitCitationValidationTest" — green, ./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01*" — all 5 chunk-01 evidence tests green, ./gradlew-nix detekt — clean, Pre-existing sandbox failure: EvidenceReportWriterTest fails on Files.createTempDirectory (macOS TMPDIR sandbox); reproduces on master — not a regression
- PRs: