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

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
