---
satisfies: [R1, R4, R5]
---

## Description

Add an explicit `pendingUnadvisedFrequencyChangeNotification: Boolean =
false` obligation field to `PilotMission`, set it to `true` in
`PilotCognitive.processInstruction(RadarServiceTerminated)` when the
pilot's high-level goal is `Transit`, emit
`Request(RequestFrequencyChange(frequency = null))` as a
`PilotTransmission` on the subsequent pilot tick (BEFORE the
autonomous LJMB `InitialContact`), and clear the obligation back to
`false` in `PilotCognitive.updateAfterTransmission`. No claim about
the transmission's addressed station — production `RequestFrequencyChange`
has no such field; routing follows existing `PilotTransmission`
delivery semantics. Rebaseline G2 + G3b golden traces atomically with
the new transmission so no `./gradlew-nix :sim:jvmTest` golden
regresses (R5).

**Size:** M (borderline; if the worker finds it overflowing one
iteration, split as: 1) obligation field + RST handler + cognitive
emission + unit-isolation tests, 2) golden rebaseline as a follow-up
commit on the same task).

**Files (expected):**
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt`
  — add `pendingUnadvisedFrequencyChangeNotification: Boolean = false`
  field (consistent with the existing `contactedOnFrequency: Boolean`
  neighbour); audit every `copy()` site and `resetForGoAround` per
  AGENTS.md §74 new-field rule.
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt`
  — TWO edits only:
  1. RST handler at lines 785-794: set
     `mission.pendingUnadvisedFrequencyChangeNotification = true` on
     the mission `copy()` when `mission.goal is HighLevelGoal.Transit`.
  2. `updateAfterTransmission` at line 1289: clear the obligation
     back to `false` when the emitted transmission is
     `Request(RequestFrequencyChange)`.
  fn-49 does NOT add a `MissionStep` arm in `stepTransmission` — the
  emission site is `Pilot.kt`, not the per-step dispatcher.
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt`
  — extend the transmission-assembly site around
  `effectiveCognitiveTransmissions` (lines 280-282) to prepend
  `Request(RequestFrequencyChange(frequency = null))` to the
  outbound transmissions list when
  `mission.pendingUnadvisedFrequencyChangeNotification == true` on
  the tick following the RST processing. (This is the committed
  emission site per epic Decision context.)
- `pilot/src/commonTest/kotlin/xyz/easiersaid/twr/pilot/PilotCognitiveSpec.kt`
  (or nearest existing pilot-cognitive spec) — new positive +
  negative unit-isolation tests.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt`
  (golden rebaseline).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3bCrossAerodromeReactiveTest.kt`
  (golden rebaseline — shares the G2 Transit trajectory).

## Approach

1. **Add obligation field to `PilotMission`.** Add
   `pendingUnadvisedFrequencyChangeNotification: Boolean = false` to
   the `PilotMission` data class. Boolean (not `Option<UnitAndFrequency>`
   or a custom type) because: (a) the obligation is binary; (b) the
   only `UnitAndFrequency` in the repo lives in `EvidenceFacts.kt`
   under `sim/src/jvmTest/` — unavailable to `pilot/src/commonMain/`;
   (c) consistent with the existing `contactedOnFrequency: Boolean`
   neighbour. No routing/station data is captured at set-time;
   routing follows existing `PilotTransmission` delivery semantics.
2. **AGENTS.md §74 new-field audit.** Enumerate every `copy()` site
   that constructs a `PilotMission` (grep `PilotMission(`,
   `mission.copy(`, etc.). For each: decide whether the new field
   carries forward (default: yes) or resets. Update `resetForGoAround`
   and any other mission-reset helpers to clear the obligation
   explicitly to `false`. No `error()` calls — the obligation is
   `false` by default; emission is best-effort, not load-bearing.
3. **Set obligation in RST handler.** In
   `PilotCognitive.processInstruction(RadarServiceTerminated)` at
   `PilotCognitive.kt:785-794`: check
   `mission.goal is HighLevelGoal.Transit`. If true, set the
   obligation field to `true` on the mission `copy()`. The relative
   ordering with `contactedOnFrequency = false` does not matter for
   correctness (both flips happen in the same `copy()`); document the
   choice in the commit message for reviewers.
4. **Sequence-offset preflight.** Enumerate every
   `recordIndex * FACTS_PER_RECORD + N` use in
   `sim/src/jvmTest/.../EvidenceFacts.kt` and confirm `+4` (the
   `pilotNotifiedFrequencyChangeFact` slot, line 777) is collision-free
   for every record class that will exist in the G2 trace
   post-emission.
5. **Add unit-isolation test FIRST** (fast-path proof point per epic
   spec). The unit-under-test for the emission assertion is
   **`pilotDecide`** (at `Pilot.kt:78`), NOT `pilotCognitiveDecide`
   — the committed emission site is the `Pilot.kt` assembly around
   `effectiveCognitiveTransmissions` at lines 280-282, downstream of
   `pilotCognitiveDecide`. The cognitive layer alone will not exhibit
   the new emission.
   The post-emission clear lives in `updateAfterTransmission`, which
   sim-wiring calls AFTER each transmission emits. The test must
   explicitly invoke `updateAfterTransmission` to prove the clear.
   Drive `Mission.Transit(LJMB)` through the sequence:
   - Apply RST processing path (via `processInstruction(...)`) →
     assert
     `mission.pendingUnadvisedFrequencyChangeNotification == true`.
   - Call `pilotDecide(input)` → assert
     `output.transmissions` (the `PilotOutput.transmissions` list)
     contains exactly one
     `Request(RequestFrequencyChange(frequency = null))`.
   - Explicitly call
     `updateAfterTransmission(mission, the-emitted-request)` →
     assert
     `mission.pendingUnadvisedFrequencyChangeNotification == false`.
   - Run additional `pilotDecide` ticks → assert the autonomous LJMB
     `InitialContact` appears in `output.transmissions` on a later
     tick. This is **mission-order proof only** (RequestFrequencyChange
     emits on an earlier tick than InitialContact); no
     receiver/addressed-station assertion is made.
6. **Add negative-space tests.**
   - **Required (small unit slice):** Construct a `PilotMission` with
     a non-Transit goal —
     `HighLevelGoal.CircuitTraining(listOf(CircuitOutcome.FullStop))`
     (use real terminal invariants per `HighLevelGoal.CircuitTraining`'s
     constructor; `HighLevelGoal.Circuit` does NOT exist) — and
     `pendingUnadvisedFrequencyChangeNotification = false`; process
     a `ContactFrequency` instruction; call `pilotDecide`; assert
     the obligation flag remains `false` AND no
     `Request(RequestFrequencyChange)` appears in
     `output.transmissions`.
   - **Required (small unit slice):** Construct a Transit mission
     where the obligation flag is `false`; call `pilotDecide`
     without RST processing; assert no
     `Request(RequestFrequencyChange)` appears in
     `output.transmissions`.
   - **Optional (full circuit):** A full G0 LOWG circuit-training
     integration run asserting zero `Request(RequestFrequencyChange)`
     across all ticks — useful sanity check, but not required by R1
     and may be slow.
   Since fn-49 commits to `Pilot.kt`-assembly emission (no new
   `MissionStep` arm), the `predicate-guards-over-sealed-types-must-2026-05-16`
   `MissionStep.entries` exhaustive sweep is NOT required for this
   task. The gating predicate is on the Boolean obligation flag, not
   on a sealed `MissionStep` discriminator.
7. **Wire production emission in `Pilot.kt`.** The committed emission
   site (per epic Decision context) is the
   `effectiveCognitiveTransmissions` assembly at `Pilot.kt:280-282`.
   When the obligation flag is `true` for the tick:
   - Prepend `Request(RequestFrequencyChange(frequency = null))` to
     `cognitive.transmissions` BEFORE `applyCognitiveSuppression(...)`
     runs (so suppression semantics still apply uniformly). The
     §2.8.2.1 notification is a communications-routine emission, not
     a safety-critical one; if DA/abort suppression filters it on
     this tick (because the pilot is mid-critical-phase), the
     obligation flag remains `true` and the request retries
     naturally on a later non-suppressed tick — exactly the
     desirable behaviour. (If a future epic decides the notification
     is non-suppressible, the prepend can be moved to AFTER
     `applyCognitiveSuppression` at `Pilot.kt:281`. fn-49 does NOT
     introduce that.)
   - The flag remains `true` until `updateAfterTransmission` is
     called by sim wiring on the emitted request — at which point it
     clears.
   The `stepTransmission` route (adding a new `MissionStep` arm) is
   explicitly NOT used: it would require a high-friction compile-
   fanout audit across `isReportComplete`, `isPhysicallyComplete`,
   `stepTransmission`, `skipCompletedSteps`, routing, and the
   per-step test fixtures — incongruent with a binary Boolean
   obligation.
8. **Clear obligation on emission.** In `updateAfterTransmission`
   (`PilotCognitive.kt:1289`), match
   `Request(RequestFrequencyChange)` and clear the obligation via
   `mission.copy(pendingUnadvisedFrequencyChangeNotification = false)`.
9. **`Request` consumer audit.** Grep every `is Request ->` or
   `request.type` exhaustive `when` consumer downstream
   (controller-side ingest, evidence projection, test fixtures).
   Decide each one deliberately: most will fall through unchanged;
   document any that need a new arm.
10. **Rebaseline G2 + G3b goldens atomically.** Run the tests; expect
    the new transmission in the trace at the boundary release. Verify
    time-band pins still hold (±15% per AGENTS.md §144-651); if a pin
    breaks, re-justify in the test KDoc rather than widening the band.
    Land emission + rebaseline in a single PR / task closure.
11. **Quality gates.** `./gradlew-nix build detekt` green. Per memory
    `gradlew-nix-wrapper-2026-05-18`: export
    `GRADLE_USER_HOME="$TMPDIR/gradle-home"` in sandboxed shells.

## Investigation targets

**Required** (read before coding):
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/Pilot.kt:280-282`
  — committed emission site (assembly of `effectiveCognitiveTransmissions`).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:785-794`
  — RST handler (set obligation here).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:1289`
  — `updateAfterTransmission` (clear obligation here).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotCognitive.kt:540-625`
  — `stepTransmission` (read for context only; fn-49 does NOT modify
  this dispatcher).
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:127-147, 584-615, 647-769`
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/PilotTransmission.kt:235-237`
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt` (golden-test shape + assertion surface)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFacts.kt:737, 756-789` (sequence-offset context — read but DO NOT modify)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidenceFactsTest.kt:262-293, 296-316` (proves the `frequency = null` adapter path)

**Optional**:
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G3bCrossAerodromeReactiveTest.kt`
- AGENTS.md §74 (new-field audit), §144-651 (golden-test discipline),
  §196-203 (cross-aerodrome handoff narrative)
- `.flow/memory/bug/test-failures/predicate-guards-over-sealed-types-must-2026-05-16.md`
- `.flow/memory/knowledge/decisions/instructor-channel-causation-for-sim-2026-05-16.md`

## Key context

- The adapter at `EvidenceFacts.kt:756-789` is **already complete** —
  do NOT touch it. Once the pilot emits the transmission (even with
  `frequency = null`), the audit flips automatically; the `null` path
  projects as `FrequencyTransferTarget.UnitOnly("UNSPECIFIED")` per
  `EvidenceFactsTest.kt:296-316`.
- AGENTS.md Commandment 5 ("the pilot owns the plan"): emission must
  originate in the pilot agent's decision logic, not the test harness
  or a world hook.
- The plan makes **no claim** about which station receives the
  transmission. Production `RequestFrequencyChange` carries no
  addressed-station field; the pilot-cognitive API does not surface
  routing metadata to the emission site; routing follows existing
  `PilotTransmission` delivery semantics. A future epic may introduce
  station-addressed routing if/when the API grows.
- ICAO 9432 §2.8.2.1 verbatim (preserved across spec / EvidenceDsl
  KDoc): "an aircraft will, except for reasons of safety, notify the
  appropriate aeronautical station before such a change takes place."

## Acceptance

- [x] New `pendingUnadvisedFrequencyChangeNotification: Boolean =
  false` field added to `PilotMission`; AGENTS.md §74 new-field audit
  completed (every `copy()` and reset site decided).
- [x] RST handler at `PilotCognitive.kt:785-794` sets the obligation
  to `true` when `goal is HighLevelGoal.Transit`; the
  obligation-set-vs-`contactedOnFrequency`-clear ordering is
  documented in the commit message.
- [x] `updateAfterTransmission` clears the obligation back to `false`
  on `RequestFrequencyChange` emission.
- [x] Positive unit-isolation test passes (mechanically accurate to
  the cognitive pipeline): obligation `== true` after RST processing,
  exactly one `Request(RequestFrequencyChange(frequency = null))`
  emits on the next decision call, obligation `== false` AFTER an
  EXPLICIT call to `updateAfterTransmission(mission, request)`,
  autonomous LJMB `InitialContact` proceeds on a later tick
  (mission-order proof only — no receiver/addressed-station
  assertion).
- [x] Negative-space unit tests pass:
  - Non-Transit goal + `ContactFrequency` processing: obligation
    stays `false` and no `Request(RequestFrequencyChange)` is
    emitted.
  - Transit mission with obligation `false` (no RST yet): no
    `Request(RequestFrequencyChange)` is emitted.
  (Full G0 circuit-training integration sweep is optional, not
  required.)
- [x] `G2CrossAerodromeVfrTest` passes with rebased trace expectations
  (single atomic commit per "no half-baked work" doctrine).
- [x] `G3bCrossAerodromeReactiveTest` passes with rebased trace
  expectations.
- [x] `./gradlew-nix build detekt` green (or `[~]` PARTIAL per
  honest-closure if sandbox blocks).
- [x] No other golden test regresses
  (`./gradlew-nix :sim:jvmTest` overall green).
- [x] Sequence-offset preflight recorded in commit message: `+N`
  offsets in `EvidenceFacts.kt` enumerated and confirmed collision-free
  for new G2 trace.
- [x] `Request` consumer audit recorded in commit message: every
  exhaustive `when` on `Request.type` reviewed.

## Done summary
Implemented pilot-side ICAO Doc 9432 §2.8.2.1 unadvised frequency-change notification.

- Added `PilotMission.pendingUnadvisedFrequencyChangeNotification`.
- Set it on Transit `RadarServiceTerminated`.
- Emitted `Request(RequestFrequencyChange(frequency = null))` from `pilotDecide` before ordinary cognitive transmissions.
- Cleared it in `updateAfterTransmission` only after the request is actually transmitted.
- Fixed sim wire-layer routing so the notification goes to the released/current controller and does not consume the `CALL_INBOUND` step-transmission gate; known-strip fallback remains InitialContact-only.
- Added pilot mission-state tests for set, negative non-set, emit ordering, and clear.

Verification:

- `./gradlew-nix :pilot:jvmTest --tests "*.ProcessInstructionMissionStateSpec" :sim:jvmTest --tests "*.G2CrossAerodromeVfrTest" --tests "*.G3bCrossAerodromeReactiveTest" detekt`
- `./gradlew-nix build detekt`
## Evidence
- Commits:
- Tests:
  - `./gradlew-nix :pilot:jvmTest --tests "*.ProcessInstructionMissionStateSpec" :sim:jvmTest --tests "*.G2CrossAerodromeVfrTest" --tests "*.G3bCrossAerodromeReactiveTest" detekt` — BUILD SUCCESSFUL
  - `./gradlew-nix build detekt` — BUILD SUCCESSFUL
- PRs:
