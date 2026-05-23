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
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFactsTest.kt` / `EvidenceDslTest.kt` — primitive-level tests for new leaves and helpers.
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
- **Selector**: `EvidenceExpectContext.clearancePacing(aircraftId)`.
- **Test shape** (sim-level, ONE test method):
  ```kotlin
  simEvidence("icao9432-chunk01-clearance-pacing") {
      observe { /* compose facts via fromTransmissionRecords or scenario trace exposing clearance-issue moments + concurrent phase */ }
      source("clearance-pacing-during-complicated-or-critical-phases") {
          cites(ICAO9432.Readback.ClearancePacing)
          expect { clearancePacing(aircraftId).whenIssuedDuring(PacingWindow.entries.filter { /* not Other */ }) /* returns advisory(violations) if observed, pass() if none */ }
      }
  }
  ```
  Cite the new `ICAO9432.Readback.ClearancePacing` source ref (added in task .1).
- **Test method assertion shape**:
  - covered-green (Advisory observed): call `report.assertNoFailures()` — passes because `Advisory` is not `Fail`. Additionally `assertTrue(report.results.any { it.outcome is EvidenceAuditOutcome.Advisory })` to confirm the advisory was emitted (not vacuous).
  - covered-red (if sim lacks signals): direct assertion on `report.results` for `Fail`; spawn repair epic.
- **No external files**: no NDJSON, no side effects. Advisory observations live entirely in audit-outcome surface.
- **`.plan` rule**: covered-green → delete paragraph; covered-red → REPLACE with one-line pointer.

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
- [ ] `Icao9432Chunk01ClearancePacingEvidenceTest.kt` created with ONE test method (sim-level) using `simEvidence(...) { observe { … }; source("id") { cites(ICAO9432.Readback.ClearancePacing); expect { … } } }`.
- [ ] `./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01ClearancePacingEvidenceTest"` executes. Test method passes via `report.assertNoFailures()` (covered-green, advisory observed) AND asserts `report.results.any { it.outcome is EvidenceAuditOutcome.Advisory }` to confirm Advisory was emitted; OR (covered-red fallback) direct `Fail` assertion on `report.results`.
- [ ] `./gradlew-nix :controller:jvmTest --tests "*.SourceUnitCitationValidationTest"` and `./gradlew-nix :sim:jvmTest --tests "*.EvidenceSourceCatalog*"` still green.
- [ ] No external NDJSON / side-effect files introduced.
- [ ] If covered-red: named production-repair epic spec exists; `.plan` FN33-MODEL-1 REPLACED with one-line pointer. If covered-green: paragraph deleted.
- [ ] `./gradlew-nix build` and `./gradlew-nix detekt` both green.
- [ ] No production-code changes outside the test tree and `.plan`.
- [ ] If design pressure pushes into POLICY-1 territory: HALT, raise `QUESTION:` in AGENT_DIALOGUE.md, do NOT silently expand scope.

## Done summary
_(filled at task close)_
## Evidence
- Commits:
- Tests:
- PRs:
