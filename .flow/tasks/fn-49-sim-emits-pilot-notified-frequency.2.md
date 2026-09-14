---
satisfies: [R2, R3, R4]
---

## Description

Flip the chunk-01 evidence test for the pilot-notified branch from
`covered-red` to `covered-green` and complete all framing close-out.
Add a new `lowgLjmbTransit(...)` G2-trace adapter to the existing
`object EvidenceFactAdapters` in
`sim/src/jvmTest/.../EvidenceFacts.kt:390` (sibling to
`lowgCircuitTraining` at line 416) so the test observes the
`Request(RequestFrequencyChange(frequency = null))` emission landed in
fn-49-sim-emits-pilot-notified-frequency.1. Split the test-local
`aircraft` constant into `controllerAdvisedAircraft =
AircraftId("OE-ABC")` (G0 LOWG, unchanged) and
`pilotNotifiedTransitAircraft = AircraftId("OE-XYZ")` (new G2 branch,
matching `G2CrossAerodromeVfrTest.kt:224`). Swap the
`pilotNotified` test's `observe { }` block to the G2 adapter and its
`frequencyTransfer(...)` selector to use the Transit aircraft.
Replace the `report.results` `Fail` assertion with
`report.assertNoFailures()` + a targeted `Pass`-outcome cross-check.
Update the test KDoc prose. Delete `.plan:227-234`. Update
`STRATEGY.md:47`. Update the `PILOT_NOTIFIED_UNIT_PLACEHOLDER` KDoc
at `EvidenceFacts.kt:1188-1195` to drop the `FN44-GAP-2` token.
Remove `FN44-GAP-2` from the §2.8.2.1 row tags in
`research/tools/requirements-spike/quality/icao9432_programme/classification.csv`
(lines 19 and 21). Prove the **narrow** grep gate (FN44-GAP-2 only —
see Approach step 8) is clean.

**Size:** M.

**Files (expected):**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt`
  — TWO edits:
  1. Add new `lowgLjmbTransit(...)` function to the existing
     `object EvidenceFactAdapters` (defined at line 390, sibling to
     `lowgCircuitTraining` at line 416). Pattern off
     `lowgCircuitTraining`. Do NOT touch the
     `pilotNotifiedFrequencyChangeFact` projection at lines 756-789
     itself — only add the adapter that drives a G2 trace through the
     existing projection pipeline.
  2. Update the `PILOT_NOTIFIED_UNIT_PLACEHOLDER` KDoc at lines
     1188-1195: drop the `FN44-GAP-2` token (the projection is now
     reached by a real emission; the gap token is historical).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt`
  — aircraft-ID split (`controllerAdvisedAircraft = OE-ABC` +
  `pilotNotifiedTransitAircraft = OE-XYZ`), `pilotNotified`
  observe-block swap, assertion flip, KDoc prose update.
- `.plan` (delete lines 227-234).
- `STRATEGY.md` (update line 47: FN44-GAP-2 fragment covered-red →
  covered-green; COMMS-1 / fn-50 fragment unchanged).
- `research/tools/requirements-spike/quality/icao9432_programme/classification.csv`
  — remove the `FN44-GAP-2` substring from the §2.8.2.1 row tags at
  line 19 (`...::40382df156ad071e`) and line 21
  (`...::b49ae03cbbb2d538`). The current tag field reads
  `FN44-GAP-1/FN44-GAP-2`; edit to `FN44-GAP-1`.

## Approach

1. **Add G2-trace adapter.** Add a new `lowgLjmbTransit(...)` function
   to the existing `object EvidenceFactAdapters` in
   `EvidenceFacts.kt:390` (sibling to `lowgCircuitTraining` at line
   416). Pattern off `lowgCircuitTraining`. The new adapter drives the
   G2 LOWG → LJMB Transit scenario via `Fixtures.LOWG_LJMB_VFR` and
   produces a deterministic trace including the
   `Request(RequestFrequencyChange(frequency = null))` emission from
   task .1. If `EvidenceMappedSpikeHarness.kt:140-213`
   (`LowgObservationPort.runCircuitTrainingTrace`) is the right
   structural template for driving the scenario through to a trace,
   mirror it.
   **Stop condition.** Define explicitly to keep the test fast and
   the trace deterministic:
   - **Preferred:** run until the trace contains BOTH (a) the
     `Request(RequestFrequencyChange)` from the pilot AND (b) the
     subsequent autonomous LJMB `InitialContact`, then halt. This
     keeps the run short (boundary-crossing window only) and proves
     the §2.8.2.1 ordering invariant.
   - **Alternative:** if a clean halt cue is not available, run the
     existing G2 wall (to full mission completion) — but justify the
     wall-clock cost in the commit message vs. the preferred
     short-window cue.
   - **DO NOT** stop before the `RequestFrequencyChange` emission;
     the audit would then be vacuously red.
   Document the chosen stop in the adapter's KDoc and the commit
   message.
2. **Split aircraft-ID constants in chunk-01 test.** Replace the
   `private val aircraft = AircraftId("OE-ABC")` constant at
   `Icao9432Chunk01FrequencyTransferEvidenceTest.kt:36` with:
   ```kotlin
   private val controllerAdvisedAircraft = AircraftId("OE-ABC")
   private val pilotNotifiedTransitAircraft = AircraftId("OE-XYZ")
   ```
   Update the controller-advised test method (lines 38-62) to use
   `controllerAdvisedAircraft` where `aircraft` is referenced. The
   pilot-notified test method will use `pilotNotifiedTransitAircraft`
   (the G2 LOWG → LJMB scenario uses `AircraftId("OE-XYZ")` per
   `G2CrossAerodromeVfrTest.kt:224`).
3. **Swap chunk-01 `pilotNotified` observe block.** Change the test
   method's `observe { }` argument from
   `EvidenceFactAdapters.lowgCircuitTraining(...)` to the new
   `EvidenceFactAdapters.lowgLjmbTransit(...)`. Change the
   `frequencyTransfer(aircraft)` selector to
   `frequencyTransfer(pilotNotifiedTransitAircraft)`.
4. **Replace assertion.** Drop the `report.results.filter { ... } /
   EvidenceAuditOutcome.Fail` block at
   `Icao9432Chunk01FrequencyTransferEvidenceTest.kt:82-93`. Use the
   sibling controller-advised pattern at lines 56-62 verbatim as the
   template: `report.assertNoFailures()` followed by
   `assertTrue(report.results.any { result -> result.sources.contains(sourceRef) && result.outcome is EvidenceAuditOutcome.Pass }, ...)`.
   Per memory `audit-selectors-must-activate-examined-2026-05-26`:
   keep the targeted `Pass`-outcome cross-check so a sibling fact
   failure doesn't silently mask the assertion fn-48.2 built.
5. **Update KDoc.** Rewrite the lines 20-29 "covered-red posture"
   prose: replace with a green-landing summary that cites fn-49 as
   the closing epic, points at the G2-trace adapter as the new
   observed trace, and notes that the emission carries
   `frequency = null` projected as
   `FrequencyTransferTarget.UnitOnly("UNSPECIFIED")`. All
   `covered-red` / `covered red` prose in this file's pilot-notified
   branch is removed (the targeted grep check in R3 verifies this).
6. **Update `PILOT_NOTIFIED_UNIT_PLACEHOLDER` KDoc.** Edit
   `sim/src/jvmTest/.../EvidenceFacts.kt:1188-1195`: drop the
   `FN44-GAP-2` parenthetical. Recommended wording: keep the
   ICAO 9432 §2.8.2.1 reference; replace "(`FN44-GAP-2`)" with a
   neutral phrase like "for `RequestFrequencyChange` transmissions
   absent controller advice" — wording is editor's choice as long as
   `FN44-GAP-2` no longer appears in the constant's KDoc.
7. **Edit `classification.csv`.** Open
   `research/tools/requirements-spike/quality/icao9432_programme/classification.csv`.
   On line 19 (row id ending `40382df156ad071e`) and line 21 (row id
   ending `b49ae03cbbb2d538`): find the final CSV field
   `FN44-GAP-1/FN44-GAP-2` and edit to `FN44-GAP-1`. Leave the
   FN44-GAP-1 substring intact (separate-cleanup concern; the narrow
   grep gate matches only `FN44-GAP-2`).
8. **Delete `.plan:227-234`** verbatim — the 8-line FN44-GAP-2 pointer
   block. No cross-out, no archival comment.
9. **Update `STRATEGY.md:47`.** Rewrite ONLY the FN44-GAP-2 fragment
   so it reads `covered-green`. The COMMS-1 / fn-50 fragment on the
   same line stays exactly as-is.
10. **Narrow grep gate** (NOT the broad gate originally proposed —
    `covered-red` is too permissive and would match unrelated COMMS-1 /
    reception-doubt / clearance-pacing / phase-signal prose tracked by
    other epics). Run:
    - `grep -RIn "FN44-GAP-2" .plan sim/ STRATEGY.md research/tools/requirements-spike/quality/icao9432_programme/`
      — returns no live hits (archive entries and `.git` excluded).
    - `grep -n "covered-red\|covered red" sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt`
      — returns no live hits in the pilot-notified branch of that
      specific file.
    The repo-wide `covered-red` grep is NOT a gate — preserving
    COMMS-1 / reception-doubt covered-red prose unchanged is correct.
11. **Honest-closure gate.** Do NOT mark this task done until
    `./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01FrequencyTransferEvidenceTest"`
    is recorded green. Sandbox blocker → land as `[~]` PARTIAL per
    memory `honest-close-out-dont-assert-green-on-2026-05-17`.

## Investigation targets

**Required** (read before coding):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt:20-29, 36, 56-62, 65-94`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:390-680` (`object EvidenceFactAdapters` + `lowgCircuitTraining` pattern source)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:756-789` (`pilotNotifiedFrequencyChangeFact` projection — read but DO NOT modify)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:1188-1195` (`PILOT_NOTIFIED_UNIT_PLACEHOLDER` KDoc — edit to drop FN44-GAP-2)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceMappedSpikeHarness.kt:140-213` (LOWG observation port — structural reference)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt:224` (confirms G2 aircraft id = `OE-XYZ`)
- `research/tools/requirements-spike/quality/icao9432_programme/classification.csv` (lines 19, 21 — final CSV field edit)
- `.plan:227-234`
- `STRATEGY.md:47`
- `.flow/memory/bug/build-errors/honest-close-out-dont-assert-green-on-2026-05-17.md`
- `.flow/memory/bug/build-errors/stop-and-report-deferment-contracts-2026-05-09.md`
- `.flow/memory/bug/test-failures/audit-selectors-must-activate-examined-2026-05-26.md`

## Key context

- This task depends on
  `fn-49-sim-emits-pilot-notified-frequency.1` having landed — the new
  G2 trace must contain the `Request(RequestFrequencyChange)` emission
  or the audit will stay red. Run G2 in isolation first to confirm
  the emission is present before touching the chunk-01 test.
- Use the sibling controller-advised pattern
  (`Icao9432Chunk01FrequencyTransferEvidenceTest.kt:56-62`) as the
  literal template for the assertion flip — same
  `assertNoFailures()` + targeted Pass cross-check shape.
- Per memory `stop-and-report-deferment-contracts-2026-05-09`: closing
  a GAP item atomically updates every framing surface (`.plan`,
  `STRATEGY.md`, optional `classification.csv`). Stale framing on any
  one surface causes review NEEDS_WORK.
- The grep gate is **narrow** by design — COMMS-1 / reception-doubt /
  clearance-pacing / phase-signal covered-red prose persists
  legitimately across `EvidenceFacts.kt`, `EvidenceDsl.kt`,
  `EvidenceFactsTest.kt`, and `Icao9432Chunk01*EvidenceTest.kt`
  siblings. Do NOT widen and do NOT touch those references.

## Acceptance

- [x] New `lowgLjmbTransit(...)` function added to
  `object EvidenceFactAdapters` in `EvidenceFacts.kt:390`,
  producing a deterministic G2 trace that includes the §2.8.2.1
  pilot transmission.
- [x] `Icao9432Chunk01FrequencyTransferEvidenceTest.kt:36`
  `aircraft` constant split into `controllerAdvisedAircraft =
  AircraftId("OE-ABC")` and `pilotNotifiedTransitAircraft =
  AircraftId("OE-XYZ")`; controller-advised branch uses the former,
  pilot-notified branch uses the latter (matches G2 scenario aircraft).
- [x] Chunk-01 `pilotNotified` test method observes
  `lowgLjmbTransit(...)`, selects on
  `frequencyTransfer(pilotNotifiedTransitAircraft)`, and asserts via
  `report.assertNoFailures()` AND retains a targeted Pass-outcome
  cross-check on `PilotNotifiesAbsentAdvice`.
- [x] Chunk-01 test KDoc rewritten — no "covered-red posture" prose
  remains in the pilot-notified branch; cites fn-49 closure.
- [x] `PILOT_NOTIFIED_UNIT_PLACEHOLDER` KDoc at
  `EvidenceFacts.kt:1188-1195` no longer contains `FN44-GAP-2`.
- [x] `classification.csv` lines 19 and 21 §2.8.2.1 rows have
  `FN44-GAP-2` substring removed from the tag field (FN44-GAP-1
  substring preserved).
- [x] `.plan:227-234` deleted (verbatim, 8-line block).
- [x] `STRATEGY.md:47` frequency-transfer fragment records the
  pilot-notified branch as covered-green; COMMS-1 / fn-50 fragment
  unchanged.
- [x] Narrow grep gate clean:
  `grep -RIn "FN44-GAP-2" .plan sim/ STRATEGY.md research/tools/requirements-spike/quality/icao9432_programme/`
  returns no live hits.
- [x] Targeted grep clean:
  `grep -n "covered-red\|covered red" sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt`
  returns no live hits in the pilot-notified branch.
- [x] Unrelated `covered-red` prose preserved unchanged: COMMS-1 /
  reception-doubt / clearance-pacing / phase-signal references in
  `EvidenceFacts.kt`, `EvidenceDsl.kt`, `EvidenceFactsTest.kt`,
  `Icao9432Chunk01ClearancePacingEvidenceTest.kt`,
  `Icao9432Chunk01ReceptionDoubtEvidenceTest.kt` etc. remain
  untouched.
- [x] `./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01FrequencyTransferEvidenceTest"`
  recorded green (or `[~]` PARTIAL per honest-closure).
- [x] `./gradlew-nix build detekt` green.

## Done summary
fn-49.2 closes FN44-GAP-2 by moving ICAO 9432 §2.8.2.1 pilot-notified frequency-change evidence from covered-red to covered-green. Added a LOWG→LJMB Transit trace adapter, asserted the real G2 trace emits Request(RequestFrequencyChange(frequency = null)) after radar-service termination, removed FN44-GAP-2 from .plan/registry red framing, and kept the request as a no-event/no-intent controller observation that does not establish two-way comms before real operational contact.
## Evidence
- Tests:
  - `./gradlew-nix :sim:jvmTest --tests "*.Icao9432Chunk01FrequencyTransferEvidenceTest"` — green.
  - `./gradlew-nix :sim:jvmTest --tests "*.EvidenceSourceCatalogTest" --tests "*.EvidenceProjectionPressureTest" --tests "*.Icao9432Chunk01FrequencyTransferEvidenceTest"` — green.
  - `./gradlew-nix :sim:jvmTest --tests "*.G2CrossAerodromeVfrTest" --tests "*.G3bCrossAerodromeReactiveTest"` — green.
  - `./gradlew-nix build detekt` — green.
- Grep gates:
  - `grep -RIn "FN44-GAP-2" .plan sim/ STRATEGY.md research/tools/requirements-spike/quality/icao9432_programme/` — no live hits after closing stale source-catalog and generated chunk-01 framing.
  - `grep -n "covered-red\|covered red" sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt` — no hits.
- Flow:
  - `flowctl done fn-49-sim-emits-pilot-notified-frequency.2` completed.
  - `flowctl epic close fn-49-sim-emits-pilot-notified-frequency` completed.
  - `flowctl validate --all` still fails on pre-existing unrelated flow drift: old fn-1/fn-3/fn-4/fn-5/fn-6/fn-7/fn-8/fn-9/fn-11/fn-12/fn-13/fn-14 task/status drift, fn-7/fn-8/fn-9 ID collisions, and missing headings in older fn-46/fn-47/fn-8 task specs.
