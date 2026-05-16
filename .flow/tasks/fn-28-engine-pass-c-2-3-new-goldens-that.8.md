---
satisfies: [R2-ABORT, R6, R9, R10, R12, R15]
---

## Description

Abort foundation. Round 1+2+3+4 fixes.

- `AircraftState.engineRunning: Boolean = true`
- `SimEvent.EngineFailure(aircraftId, time, seq, source)`; `source = AgentId.System`
- `Step.kt handleEngineFailure` sets engineRunning=false; NO synthetic wake event
- **Instant-speed engine-off clamp** (R12) in `advanceKinematics`: when engineRunning=false, `speedMps = min(targetSpeedMps, currentSpeedMps)` (decel allowed; accel blocked)
- `InstructorInput.EngineFailureAt(aircraftId, time)` typed input (firewall-clean)
- Fixture helper `toInitialEvents(baseSeq: Long)`: translates `List<InstructorInput>` → `List<SimEvent>`; pre-stamps seq monotonically. **Fixture builder advances `SimState.seq` to `baseSeq + emittedCount`** (round-4 Minor 1) — non-overlapping with driver-emitted events. Audit + unit test.
- Firewall test extension for `InstructorInput`
- **`MissionStep.ABORTED`** — terminal non-completing (R15). 4-consumer audit.

NOT in scope: severity enum on EngineFailure; `MissionStep.ABORT_ROLL` (use ABORTED only); `PilotIntent` thrust/brake; `AircraftType.abortDecelMs2`; `AgentId.Instructor`; `Emergency<T>`; RegDB for POH §3.3.

**Size:** L
**Files:**
- `pilot/.../AircraftState.kt` — `engineRunning: Boolean = true`
- `sim/.../SimEvent.kt:22-223` — EngineFailure subtype; withSeq arm
- `sim/.../Step.kt:196-225` — handleEngineFailure dispatch arm (no wake event)
- `sim/.../Step.kt` (advanceKinematics) — engine-off clamp
- `pilot/.../PilotMission.kt` — `MissionStep.ABORTED` + 4-consumer audit
- `pilot/.../InstructorInput.kt` (NEW) — sealed + `EngineFailureAt`
- `sim/src/jvmTest/.../testing/InstructorBriefing.kt` (NEW) — `toInitialEvents(baseSeq)` helper
- `pilot/src/commonTest/.../FirewallInstructorInputTest.kt`
- `sim/src/commonTest/.../EngineOffKinematicClampSpec.kt`
- `sim/src/jvmTest/.../InitialEventsSeqAuditSpec.kt` — verifies fixture's `SimState.seq` ≥ `baseSeq + emittedCount` post-construction

## Approach

- **Instant-speed clamp**: single guard added in `advanceKinematics`; pseudocode `newSpeed = if (engineRunning) targetSpeedMps else min(targetSpeedMps, currentSpeedMps)`.
- **MissionStep.ABORTED**: 4-consumer audit identical pattern to DECLINE_DEPARTURE; KDoc comment cross-references.
- **AgentId.System** for EngineFailure source.
- **No wake event**: KDoc-documented.
- **`toInitialEvents(baseSeq)` + fixture seq advancement**: fixture builder calls helper; receives `(events, nextSeq)` or similar; sets `SimState.seq = nextSeq` post-construction. Unit test asserts no seq overlap.

## Investigation targets

- `pilot/.../AircraftState.kt`
- `sim/.../SimEvent.kt:1-223`
- `sim/.../Step.kt:196-225` + `advanceKinematics`
- `pilot/.../PilotMission.kt`
- `pilot/src/commonTest/.../FirewallPilotInputTest.kt`
- Existing fixture initial-events convention

## Key context

- R12 + round-3 Major 2: instant-speed clamp is the v1 model.
- R15: ABORTED audit at 4 surfaces.
- Round-2 Major 4: no synthetic wake event.
- Round-4 Minor 1: SimState.seq advances past pre-stamped events.

## Acceptance

- [ ] `AircraftState.engineRunning: Boolean = true`
- [ ] `SimEvent.EngineFailure` subtype with source = AgentId.System; withSeq arm
- [ ] `Step.kt` dispatch + handler; NO wake event; KDoc documents
- [ ] `advanceKinematics` engine-off clamp
- [ ] Unit test `EngineOffKinematicClampSpec`: accel blocked; decel allowed
- [ ] `MissionStep.ABORTED` + **4-consumer audit at the same sites as .2's DECLINE_DEPARTURE audit** (round-10 Major 1 — .8 owns ABORTED's audit, NOT .2): `PilotCognitive.stepTransmission` (emits nothing for ABORTED), `skipCompletedSteps` (no skip past ABORTED), `Pilot.planRoute` (at-rest for ABORTED), `isPhysicallyComplete` (audit if needed). Uses `CompletionMode.NON_COMPLETING` from .2's foundation
- [ ] `InstructorInput.EngineFailureAt`
- [ ] `toInitialEvents(baseSeq)` helper + fixture-builder integration advancing `SimState.seq` past emitted events
- [ ] `InitialEventsSeqAuditSpec`: no seq overlap between initial events and driver-emitted events
- [ ] `FirewallInstructorInputTest`: world-state reachability rejected
- [ ] NO Emergency<T>; NO AgentId.Instructor; NO MissionStep.ABORT_ROLL; NO abortDecelMs2; NO RegDB for POH
- [ ] `./gradlew :sim:jvmTest :pilot:jvmTest :protocol:allTests detekt --offline --no-daemon` GREEN

## Done summary
Landed fn-28.8 (G0 abort-takeoff foundation): AircraftState.engineRunning (ground-truth Boolean, default true) + SimEvent.EngineFailure (source body-declared = AgentId.System per round-1 fix) + withSeq arm + Step.kt handleEngineFailure (loud-fail on unknown aircraft per round-1 fix; no synthetic wake event per round-2 Major 4) + advanceKinematics engine-off clamp (R12: speedMps = min(targetSpeedMps, currentSpeedMps) when engineRunning=false — decel allowed, accel blocked, instant-speed v1 model) + InstructorInput sealed interface with EngineFailureAt leaf (firewall-clean instructor channel per memory: knowledge/decisions/instructor-channel-causation-for-sim-2026-05-16) + sim/testing/InstructorBriefing.toInitialEvents(baseSeq) helper + InstructorBriefingResult + withFixtureBaseSeqAdvanced (round-4 Minor 1 seq-advancement contract) + MissionStep.ABORTED (R15 NON_COMPLETING terminal step) + 4-consumer audit arms at PilotCognitive.isReportComplete + isPhysicallyComplete + stepTransmission (all return false/null) and Pilot.planRoute documented absence from airborneSteps. Tests: FirewallAircraftStateTest extended with engineRunning canonical-constructor entry (E5 firewall allowlist), FirewallInstructorInputTest (canonical-constructor + memberProperties reflection + sealedSubclasses leaf-count gate + typed-units defensive pin), MissionStepAbortedAuditSpec (4 rows: enum presence + NON_COMPLETING pairing + DECLINE_DEPARTURE co-presence + ABORT_ROLL negative-space pin), EngineOffKinematicClampSpec (7 rows: engine-on baseline + accel-blocked + decel-allowed + equal + at-rest + EngineFailure-handler-flips + loud-fail-on-unknown + idempotent-already-failed + no-wake-event), InitialEventsSeqAuditSpec (7 rows: monotonic seqs + baseSeq offset + withFixtureBaseSeqAdvanced + non-overlap with driver-emitted events + idempotent advance + empty briefings + AgentId.System mapping). NOT in scope per task: AgentId.Instructor, MissionStep.ABORT_ROLL, Emergency<T> supertype, AircraftType.abortDecelMs2, PilotIntent thrust/brake, RegDB for POH §3.3 (KDoc-only references). Codex impl-review: round 1 NEEDS_WORK (Major 1: SimEvent.EngineFailure.source as ctor-param allowed AgentId override; Major 2: handleEngineFailure silently no-op'd on unknown aircraft) → round 2 SHIP after body-declared val refactor + loud-fail handler. Memory entry bug/build-errors/fixed-source-simevent-body-declared-val-2026-05-16 captures the discipline anchor for fn-28.9+ emergency events.
## Evidence
- Commits: d962500, a09dfce
- Tests: ./gradlew :sim:jvmTest :pilot:jvmTest :protocol:allTests detekt --offline --no-daemon (NOT RUN LOCALLY: no JDK installed in worker environment — verification deferred to next CI/local pass; codex impl-review SHIP'd statically scoped to base e9f0f36)
- PRs: