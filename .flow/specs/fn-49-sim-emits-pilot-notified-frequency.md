# Sim emits pilot-notified frequency change (FN44-GAP-2 closure)

## Goal & Context

ICAO 9432 §2.8.2.1 requires that, absent controller advice, "an aircraft
will, except for reasons of safety, notify the appropriate aeronautical
station before such a [frequency] change takes place." The protocol
layer already models this notification as
`Request(RequestFrequencyChange(frequency: Frequency? = null))`, and
the evidence DSL projects matching pilot transmissions into
`FrequencyTransfer(mode = PilotNotifiedAbsentAdvice, …)` facts via the
adapter added in `fn-48-icao-9432-chunk-01-drive-expected-gap.2`
(committed on main at `cbccf152`; epic completion-review = ship,
2026-05-26). The adapter accepts the `frequency = null` form, projecting
it as `FrequencyTransferTarget.UnitOnly("UNSPECIFIED")` per the
unit-test surface at `EvidenceFactsTest.kt:296-316`.

However the sim does NOT currently emit
`Request(RequestFrequencyChange(...))` from the pilot side under any
scenario. As a result the chunk-01 evidence test
`Icao9432Chunk01FrequencyTransferEvidenceTest::pilot-notified frequency
change source unit is covered red against current LOWG trace` lands
**covered-red**: the audit honestly reports a `Fail` outcome for the
cited source ref, and the test asserts that outcome directly on
`report.results` rather than via `assertNoFailures()`. JUnit passes
(build green) while the audit's red outcome stands.

This repair epic closes that gap by teaching the sim's pilot agent to
emit `Request(RequestFrequencyChange(frequency = null))` immediately
after radar service is terminated at the G2 cross-aerodrome boundary
(LOWG → LJMB Transit), **before** the autonomous LJMB initial
contact. The G2 scenario is the doctrinally-perfect §2.8.2.1 fallback
host: `RadarServiceTerminated` is a service-termination event, NOT a
`ContactFrequency` advice — the controller did NOT specify the
successor frequency, so the §2.8.2.1 "absent advice" clause applies,
and the pilot must self-notify before switching. (The plan does NOT
claim a specific addressed-station for the transmission: the
production `RequestFrequencyChange` has no addressed-station field
and the pilot-cognitive API does not surface routing metadata to the
emission site — routing follows whatever
`PilotTransmission` delivery semantics already exist in the sim, and
fn-49 does not wire new routing.) When the sim emits the
transmission, the chunk-01 test switches its `observe { }` block to a
new G2-trace adapter and flips from `covered-red` to `covered-green`
via `report.assertNoFailures()` + a targeted `Pass`-outcome
cross-check on the `PilotNotifiesAbsentAdvice` source ref; the
`.plan` pointer (FN44-GAP-2 → fn-49) is deleted; the
`PILOT_NOTIFIED_UNIT_PLACEHOLDER` KDoc in `EvidenceFacts.kt:1188-1195`
is updated to drop the `FN44-GAP-2` token; the
`research/tools/requirements-spike/quality/icao9432_programme/classification.csv`
GAP-2 tag on the §2.8.2.1 source unit at lines 19, 21 is removed; and
STRATEGY.md's covered-red mention of FN44-GAP-2 is updated to
covered-green.

## Quick commands

```bash
# Closure signal — chunk-01 pilot-notified flips green
./gradlew-nix :sim:jvmTest \
  --tests "*.Icao9432Chunk01FrequencyTransferEvidenceTest"

# G2 / G3b regression guards
./gradlew-nix :sim:jvmTest \
  --tests "*.G2CrossAerodromeVfrTest" \
  --tests "*.G3bCrossAerodromeReactiveTest"

# Full sweep
./gradlew-nix build detekt

# Narrow close-out grep gate (FN44-GAP-2 only — `covered-red` is too
# broad; it legitimately persists in COMMS-1 / reception-doubt /
# clearance-pacing / phase-signal contexts tracked by other epics)
grep -RIn "FN44-GAP-2" .plan sim/ STRATEGY.md \
  research/tools/requirements-spike/quality/icao9432_programme/ \
  || true

# Targeted check: pilot-notified-branch covered-red prose is gone
grep -n "covered-red\|covered red" \
  sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt \
  || true
```

(Per memory `gradlew-nix-wrapper-2026-05-18`: export
`GRADLE_USER_HOME="$TMPDIR/gradle-home"` in sandboxed shells.)

## Boundaries / non-goals

In scope:
- New pilot-mission state field encoding the §2.8.2.1 notification
  obligation as a production-local `Boolean`:
  `pendingUnadvisedFrequencyChangeNotification: Boolean = false` on
  `PilotMission` (consistent with the existing
  `contactedOnFrequency: Boolean` neighbour). Boolean — not
  `Option<UnitAndFrequency>` — because the only `UnitAndFrequency` in
  the repo is `EvidenceFacts.FrequencyTransferTarget.UnitAndFrequency`,
  a JVM-test type unavailable to `pilot/src/commonMain/`. The
  obligation is binary (set/cleared); no routing data is captured at
  set-time.
- Setting the obligation in
  `PilotCognitive.processInstruction(RadarServiceTerminated)` when the
  pilot's `HighLevelGoal is Transit` AND the RST did NOT carry a
  successor-frequency advice (i.e., not a `ContactFrequency`).
- Pilot-decision-side emission of
  `Request(RequestFrequencyChange(frequency = null))` on a pilot tick
  BEFORE the autonomous LJMB `InitialContact` step. No claim about
  the transmission's addressed station — the protocol type has no
  such field, the pilot-cognitive API does not surface routing
  metadata, and the current `PilotTransmission` delivery semantics
  govern routing.
- Clearing the obligation in
  `PilotCognitive.updateAfterTransmission` once
  `RequestFrequencyChange` is emitted.
- Atomic G2 + G3b golden trace rebaseline for the new pilot
  transmission.
- New G2-trace adapter added as a sibling function to
  `lowgCircuitTraining` inside `object EvidenceFactAdapters` at
  `sim/src/jvmTest/.../EvidenceFacts.kt:390` so the chunk-01 closure
  test observes a trace that hosts the §2.8.2.1 fallback. The G2
  scenario uses `AircraftId("OE-XYZ")` (per
  `G2CrossAerodromeVfrTest.kt:224`); since the chunk-01 test today
  declares `private val aircraft = AircraftId("OE-ABC")`, task .2
  splits the test-local constant into per-scenario IDs:
  `controllerAdvisedAircraft = OE-ABC` (G0 LOWG circuit-training,
  unchanged) and `pilotNotifiedTransitAircraft = OE-XYZ` (new G2
  branch), each fed into the corresponding `frequencyTransfer(<id>)`
  selector.
- Updating the chunk-01 evidence test to call
  `report.assertNoFailures()` with a targeted `Pass`-outcome
  cross-check on
  `ICAO9432.TransferCommunications.PilotNotifiesAbsentAdvice` (sibling
  pattern at `Icao9432Chunk01FrequencyTransferEvidenceTest.kt:56-62`).
- Deleting the FN44-GAP-2 pointer paragraph from `.plan:227-234`.
- Updating `STRATEGY.md:47` prose: FN44-GAP-2 covered-red →
  covered-green (COMMS-1 / fn-50 remains covered-red unchanged).
- Updating the `PILOT_NOTIFIED_UNIT_PLACEHOLDER` KDoc at
  `sim/src/jvmTest/.../EvidenceFacts.kt:1188-1195` to drop the
  `FN44-GAP-2` token (the projection is now reached by a real
  emission; the gap token is historical).
- Removing the `FN44-GAP-2` substring from the tag field of the two
  §2.8.2.1 rows in `research/tools/requirements-spike/quality/icao9432_programme/classification.csv`
  (line 19: `transfer_communications_2_8_2_en::40382df156ad071e`,
  line 21: `transfer_communications_2_8_2_en::b49ae03cbbb2d538`).
  Leave the `FN44-GAP-1` portion intact (FN44-GAP-1 is closed but its
  separate cleanup is out of scope here — the narrow grep gate
  matches `FN44-GAP-2` only, so the remaining `FN44-GAP-1` substring
  does not fail the gate).

Out of scope:
- Controller-advised frequency change (FN44-GAP-1 already covered-green
  via existing `ContactFrequency` controller emission + adapter).
- Restructuring `EvidenceFactPayload.FrequencyTransfer` or
  `FrequencyTransferTarget`.
- Threading a known successor frequency (the LJMB Tower freq) into the
  emission — fn-49 emits with `frequency = null` and accepts the
  `UnitOnly("UNSPECIFIED")` audit projection. Future work may wire the
  successor freq through if a clean data path emerges (defer to a
  separate epic if needed).
- New ICAO 9432 source units beyond §2.8.2.1.
- COMMS-1 / fn-50 reception-quality work (separate epic; merge-risk
  overlap — fn-49 lands first, fn-50 rebases).
- AFIS / FIS / departure-frequency self-release scenarios beyond G2
  (deferrable; one realistic scenario suffices per R1).
- A new CAP 413 cite (the CAP413_2_7 §-number deferment
  `D-PASS-cap413-2_7` at `docs/deferments.md:310-314` remains open;
  fn-49 cites ICAO 9432 §2.8.2.1 only).
- Removing other unrelated `covered-red` prose (COMMS-1,
  reception-doubt, clearance-pacing, phase signal etc.) — those track
  separate epics and must NOT be touched by fn-49.

## Strategy Alignment

Active tracks served by this plan:
- **Runtime simulator** — Introduces the first pilot-side §2.8.2.1
  notification obligation on the G2 cross-aerodrome Transit path,
  completing the pilot-owns-the-plan authoring at the radar-service
  termination boundary.
- **Requirements registry** — Flips the last open `covered-red` ICAO
  9432 chunk-01 transfer-communications source unit (FN44-GAP-2) to
  `covered-green`, leaving COMMS-1 / fn-50 as the only remaining red
  in chunk-01.

## Decision context

- **Why G2 cross-aerodrome host?** G2 (LOWG → LJMB Transit) is the
  only scenario where a frequency-change-without-`ContactFrequency`
  obligation can be realised in the current sim. `RadarServiceTerminated`
  is a service-termination event — it does NOT specify a successor
  frequency, so the §2.8.2.1 "absent advice" clause applies and the
  pilot must self-notify before switching to LJMB. G0 LOWG
  circuit-training has no §2.8.2.1 trigger (all handoffs use
  `ContactFrequency`); AFIS / FIS scenarios aren't yet in the sim.

- **Why an explicit `pendingUnadvisedFrequencyChangeNotification`
  obligation field instead of gating on `pendingInitialContactRole ==
  None`?** `pendingInitialContactRole == None` is the default state in
  many normal pilot conditions (before any handoff, after an advised
  `InitialContact` completes, etc.). Gating on the default state risks
  drive-by emissions in unrelated Transit contexts. An explicit
  positive obligation set ONLY by RST-processing-in-Transit, and
  cleared ONLY by the emission itself, captures the §2.8.2.1
  obligation as a first-class state. This matches the codex
  reviewer's recommendation and aligns with the project commandment
  "dead programs tell no lies" (don't infer obligation from absence).

- **Why `Boolean` and not `Option<UnitAndFrequency>` or a custom
  type?** The obligation is binary: either the pilot owes a
  §2.8.2.1 notification or they do not. `Boolean` matches the
  existing `contactedOnFrequency: Boolean` neighbour on
  `PilotMission` and uses no production-side type not already
  present. (The only `UnitAndFrequency` in the repo is a JVM-test
  helper inside `EvidenceFacts.kt` — unavailable to
  `pilot/src/commonMain/`.) A future epic that needs the
  station-name / successor-frequency at the emission site can
  introduce a richer obligation type without changing the audit
  contract; fn-49 deliberately keeps the field minimal.

- **No claim about the transmission's addressed station.** The
  production `Request(RequestFrequencyChange(...))` has no
  addressed-station field; the pilot-cognitive API
  (`stepTransmission` returns `PilotTransmission`,
  `processInstruction(RadarServiceTerminated)` does not receive a
  controller/radio-context argument) does not surface routing
  metadata to the emission site. The plan therefore makes no claim
  about who receives the transmission — it just emits a
  `PilotTransmission` whose delivery is governed by the sim's
  existing `PilotTransmission` routing semantics. The audit contract
  (`pilotNotifiedFrequencyChangeFact` adapter) does not depend on
  addressed-station data; it consumes only the transmission's
  `RequestFrequencyChange.frequency` field, which fn-49 emits as
  `null` (`UnitOnly("UNSPECIFIED")` audit projection per
  `EvidenceFactsTest.kt:296-316`).

- **Emission site committed to `Pilot.kt` assembly, not
  `stepTransmission`.** Two candidate emission sites surfaced during
  planning: (a) extending `stepTransmission` at
  `PilotCognitive.kt:540-625` with a new `MissionStep` arm (e.g.
  `NOTIFY_UNADVISED_FREQ_CHANGE`), or (b) emitting upstream in the
  `Pilot.kt` assembly (around `effectiveCognitiveTransmissions` at
  `Pilot.kt:280-282`) directly from the obligation flag. Option (a)
  is **rejected** because adding a `MissionStep` is high-friction
  (compile-fanout audit across `isReportComplete`,
  `isPhysicallyComplete`, `stepTransmission`, `skipCompletedSteps`,
  routing, and tests) and is incongruent with a binary `Boolean`
  obligation. Option (b) is **committed**: when the obligation flag
  is `true`, the assembly prepends
  `Request(RequestFrequencyChange(frequency = null))` to the
  transmission list and lets the sim's existing
  `updateAfterTransmission` clear the flag after the tick's
  transmission(s) propagate.

- **Why `frequency = null` in the emission?** Threading the successor
  LJMB Tower frequency through the obligation field requires a data
  path that doesn't exist cleanly in the current pilot-cognitive API
  (`stepTransmission` doesn't receive the destination's tower freq;
  there is no AIP-lookup mechanism wired to the obligation-setter).
  The adapter projection accepts `frequency = null` and emits
  `FrequencyTransferTarget.UnitOnly("UNSPECIFIED")` — proven by
  `EvidenceFactsTest.kt:296-316`. fn-49 uses the `null` form to keep
  scope tight; a future epic can thread the successor frequency if a
  clean data path emerges.

- **Why two tasks?** Task .1 is the production change with its
  golden-trace ripple (G2 + G3b rebaselined atomically with the new
  obligation field + emission). Task .2 is the test-side flip plus
  framing close-out. Splitting along the production / framing boundary
  keeps PR scopes coherent and lets the unit-isolation test in .1
  stand as the fast-path proof point before goldens churn.

## Review considerations

This section pre-acknowledges the cross-cutting concerns
`/flow-next:plan-review` and the FP-review agents will exercise.

- **FP / type safety.** The new
  `pendingUnadvisedFrequencyChangeNotification: Boolean = false` field
  is added to `PilotMission` (a data class). Total-function audit per
  AGENTS.md §74:
  - Every `copy()` site that constructs a `PilotMission` must decide
    whether to carry the obligation forward (default: yes, unless
    explicit reset like `resetForGoAround`).
  - `resetForGoAround` and any other mission-reset helpers must
    explicitly clear the obligation to `false`.
  - No `error()` calls — the obligation defaults to `false`; emission
    is best-effort, not load-bearing on a `require()`.
- **`Request` consumer audit.** Every exhaustive `when` block on
  `Request.type` (e.g., `updateAfterTransmission`, any controller-side
  ingest, the `EvidenceFacts.kt` projection — already in place — and
  any test fixture that branches on transmission type) must be
  re-examined. New-arm decisions documented in commit.
- **Test architecture.** Positive + negative unit-isolation tests
  precede any golden rebaseline. Per memory
  `predicate-guards-over-sealed-types-must-2026-05-16`: if the
  emission gates on a `MissionStep` arm, add a `MissionStep.entries`
  exhaustive negative-space sweep so a future `MissionStep` addition
  cannot accidentally re-fire the emission.
- **G2 / G3b regression surface.** A new pilot transmission shifts
  `recordIndex`-keyed `EvidenceSequence` offsets in
  `EvidenceFacts.kt:737, 777` (the `+4` slot for
  `pilotNotifiedFrequencyChangeFact`); preflight every `+N` offset
  used by record classes touched by G2. Time-band pins (±15% per
  AGENTS.md §144-651) hold or are re-justified in the commit message.
- **Operational correctness.** Notification timing is "before the
  change takes place" — the emission tick precedes the autonomous
  LJMB `InitialContact` step in mission ordering. The plan makes NO
  routing/addressed-station claim (production `RequestFrequencyChange`
  has no such field); the doctrinal intent that the notification
  ought to reach the station-being-left is acknowledged but NOT
  asserted by fn-49 — a future epic can introduce station-addressed
  routing if/when the API grows.
- **Honest closure.** Per memory
  `honest-close-out-dont-assert-green-on-2026-05-17`: do NOT mark R2
  or R3 green without a recorded gradle run; sandbox blocker → land
  as `[~]` PARTIAL.
- **Atomic framing.** Per memory
  `stop-and-report-deferment-contracts-2026-05-09`: closing FN44-GAP-2
  updates `.plan`, `STRATEGY.md`, and (if present) `classification.csv`
  in a single task commit. Stale framing on any one surface causes
  review NEEDS_WORK.

## Acceptance

- [ ] **R1** — Pilot agent emits
  `Request(RequestFrequencyChange(frequency = null))` as a
  `PilotTransmission` in at least one realistic sim scenario (G2
  LOWG → LJMB Transit) when `RadarServiceTerminated` is processed
  while the pilot's high-level goal is `Transit`. Positive: emission
  fires exactly once per Transit, BEFORE the autonomous LJMB
  `InitialContact`, when the new obligation field has been set to
  `true`. Negative: no emission when the obligation field is `false`
  (e.g., on G0 LOWG circuit-training where all handoffs are
  `ContactFrequency`-advised). No assertion about the transmission's
  addressed station — the production protocol has no such field.
- [ ] **R2** — The chunk-01 evidence test for the pilot-notified
  branch re-runs `covered-green`: the test asserts via
  `report.assertNoFailures()` AND retains a targeted Pass-outcome
  cross-check on
  `ICAO9432.TransferCommunications.PilotNotifiesAbsentAdvice` (sibling
  pattern at `Icao9432Chunk01FrequencyTransferEvidenceTest.kt:56-62`).
  The `report.results` `Fail` assertion introduced in fn-48.2 is
  replaced.
- [ ] **R3** — `.plan` pointer paragraph for FN44-GAP-2
  (`.plan:227-234`) is deleted; `STRATEGY.md:47` prose flips from
  "covered-red" to "covered-green" for the FN44-GAP-2 fragment
  (COMMS-1 / fn-50 fragment unchanged); the
  `PILOT_NOTIFIED_UNIT_PLACEHOLDER` KDoc at
  `sim/src/jvmTest/.../EvidenceFacts.kt:1188-1195` drops the
  `FN44-GAP-2` token; the `classification.csv` GAP-2 tag on the
  §2.8.2.1 source unit at lines 19, 21 has the `FN44-GAP-2` substring
  removed (FN44-GAP-1 substring preserved as-is); the **narrow** grep
  gate
  `grep -RIn "FN44-GAP-2" .plan sim/ STRATEGY.md research/tools/requirements-spike/quality/icao9432_programme/`
  returns no live references; the targeted check
  `grep -n "covered-red\|covered red" Icao9432Chunk01FrequencyTransferEvidenceTest.kt`
  returns no live references in the pilot-notified branch of that
  file. **DO NOT** widen the grep gate to all `covered-red` repo-wide;
  COMMS-1 / reception-doubt / clearance-pacing / phase-signal
  covered-red prose tracks other epics and MUST be preserved
  unchanged.
- [ ] **R4** — `./gradlew-nix build` and `./gradlew-nix detekt` both
  green. Honest-closure rule applies (per
  `honest-close-out-dont-assert-green-on-2026-05-17`): do NOT mark R2
  or R3 green unless a recorded `./gradlew-nix :sim:jvmTest` run
  actually succeeded; sandbox blocker → land as `[~]` PARTIAL.
- [ ] **R5** — No regression in existing sim golden tests
  (`./gradlew-nix :sim:jvmTest`). G2 + G3b traces are rebaselined
  atomically with the new pilot emission (single commit per task
  scope); `EvidenceSequence` `+N` offsets in `EvidenceFacts.kt`
  remain collision-free; time-band pins (±15% per AGENTS.md §144-651)
  hold or are re-justified in the task closure narrative.

## Early proof point

Task **fn-49-sim-emits-pilot-notified-frequency.1** validates the core
approach via a new unit-isolation test that drives
`Mission.Transit(LJMB)` through processing `RadarServiceTerminated`
and asserts (against the **`pilotDecide` integrated path**, NOT
`pilotCognitiveDecide`, since the committed emission site is the
`Pilot.kt` assembly):
- After RST processing (via `processInstruction`): the new
  `pendingUnadvisedFrequencyChangeNotification` field is `true` on
  the mission.
- On the next pilot tick, the `PilotOutput.transmissions` list
  returned by `pilotDecide(...)` contains exactly one
  `Request(RequestFrequencyChange(frequency = null))`.
- After an explicit
  `updateAfterTransmission(mission, the-emitted-request)` call, the
  obligation field is back to `false` on the mission.
- Subsequent `pilotDecide` ticks produce the autonomous LJMB
  `InitialContact` per existing G2 path (mission-order proof only).

The "autonomous LJMB `InitialContact` proceeds in subsequent ticks"
assertion above is **mission-order proof only** — i.e.,
`RequestFrequencyChange` is emitted on an earlier tick than the
later `InitialContact`. The plan makes no receiver/station assertion
about the `RequestFrequencyChange` transmission itself.

If this fails, re-evaluate (a) the obligation-field placement
(mission state vs. cognitive scratch state), and (b) whether the
emission belongs in `Pilot.kt`'s assembly site (the committed choice;
see Decision context and task .1) or some other site.

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | Pilot emits `RequestFrequencyChange(frequency = null)` at G2 boundary; positive + negative coverage | fn-49-sim-emits-pilot-notified-frequency.1 | — |
| R2  | Chunk-01 test flips covered-green with targeted Pass cross-check | fn-49-sim-emits-pilot-notified-frequency.2 | — |
| R3  | `.plan` pointer deleted + `STRATEGY.md` prose updated + narrow grep gate clean | fn-49-sim-emits-pilot-notified-frequency.2 | — |
| R4  | Gradle build + detekt green; honest-closure PARTIAL fallback | fn-49-sim-emits-pilot-notified-frequency.1, fn-49-sim-emits-pilot-notified-frequency.2 | Per-task quality gate |
| R5  | No regression in G2/G3b goldens; `+N` offset collision-free | fn-49-sim-emits-pilot-notified-frequency.1 | Golden rebaseline atomic with emission |

## Investigation hints

- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/PilotTransmission.kt:235-237`
  — `RequestFrequencyChange(frequency: Frequency? = null)` already
  exists; zero production references today.
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:280-282`
  — `effectiveCognitiveTransmissions` assembly site (the committed
  emission site; see Decision context).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:540-625`
  — `stepTransmission` per-step dispatcher (read for context only;
  fn-49 does NOT add a `MissionStep` arm here).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:785-794`
  — `RadarServiceTerminated` handler (where the obligation field is
  set; currently also clears `contactedOnFrequency` — sequence the
  obligation-set BEFORE the clear).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:1289`
  — `updateAfterTransmission` (clear the obligation field here on
  `RequestFrequencyChange` emission; new-field audit per AGENTS.md
  §74).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:127-147`
  — existing state surface (`pendingInitialContactRole`,
  `contactedOnFrequency`); the new obligation field lives alongside.
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt`
  — every `copy()` and `resetForGoAround` site touched by AGENTS.md
  §74 new-field audit.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:390-680`
  — `object EvidenceFactAdapters`; new `lowgLjmbTransit(…)` sibling
  to `lowgCircuitTraining` at line 416.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:737, 756-789`
  — `pilotNotifiedFrequencyChangeFact` projection (already complete;
  DO NOT modify) + sequence-offset surface.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFactsTest.kt:262-293, 296-316`
  — adapter unit tests covering both explicit-frequency and
  null-frequency forms (proves the `null` path is wired).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt:20-29, 65-94`
  — closure test KDoc + `pilotNotified` method body to flip.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/Icao9432Chunk01FrequencyTransferEvidenceTest.kt:56-62`
  — sibling green pattern (controller-advised branch) to mirror.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt`,
  `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3bCrossAerodromeReactiveTest.kt`
  — golden trace surface affected by R5.
- `.plan:227-234` — FN44-GAP-2 pointer paragraph to delete.
- `STRATEGY.md:47` — covered-red prose fragment to flip (FN44-GAP-2
  only; COMMS-1 fragment preserved unchanged).

## Dependencies

- **Hard:** fn-48 (epic JSON status: open, completion-review status:
  ship). The `pilotNotifiedFrequencyChangeFact` adapter committed in
  fn-48-icao-9432-chunk-01-drive-expected-gap.2 is the load-bearing
  prerequisite; it is already on main (commit `cbccf152`).
- **Soft:** fn-50 (open). Both epics touch
  `pilot/src/commonMain/.../PilotCognitive.kt` and
  `sim/src/jvmTest/.../EvidenceFacts.kt`. Coordination: fn-49 lands
  first; fn-50 rebases.

## Notes

Drafted as part of `fn-48-icao-9432-chunk-01-drive-expected-gap.2`'s
honest covered-red landing. Per the "no deferment, no surprises"
doctrine the FN44-GAP-2 `.plan` entry was REPLACED (not deleted) with
a one-line pointer to this repair epic, preserving visible debt until
production closes the gap. fn-49 deletes the pointer under R3.

The spec's quote of §2.8.2.1 ("an aircraft will, except for reasons of
safety, notify the appropriate aeronautical station before such a
change takes place") is preserved across the spec, `EvidenceFacts.kt`
KDoc, and `EvidenceDsl.kt` §2.8.2.1 comments. The ingested ICAO 9432
unit `b49ae03cbbb2d538` uses the tighter wording "In the absence of
such advice, the aircraft shall notify the aeronautical station before
such a change takes place" — both are accepted phrasings of the same
clause; no spec amend needed.
