---
title: "Fixed-source SimEvent: body-declared val + loud-fail handler (vs ctor-param + si"
date: "2026-05-16"
track: bug
category: build-errors
module: sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/SimEvent.kt + Step.kt
tags: [fn-28, fn-28.8, sim-event, instructor-channel, firewall, emergency-events, no-corners, loud-fail, codex-impl-review, agent-id]
problem_type: build-error
symptoms: "Codex round-1 NEEDS_WORK: SimEvent.EngineFailure.source as ctor-param allows AgentId override; handleEngineFailure silently no-ops on unknown aircraft id"
root_cause: Constructor-param default for fixed-source events leaks discipline; defensive 'no-op on missing target' handlers hide fixture authoring defects
resolution_type: fix
related_to: [bug/build-errors/ga-path-precedence-reorder-when-adding-2026-05-10, bug/build-errors/grep-enforceable-token-prohibitions-2026-05-16, bug/build-errors/recognitionapply-pipelines-need-mission-2026-05-11]
---

## Problem
Two architectural-discipline gaps surfaced in a new sealed-event subtype:
1. `SimEvent.EngineFailure.source` was a constructor parameter with default `AgentId.System` — callers could override with `AgentId.Pilot(...)` / `AgentId.Controller(...)`, defeating the round-2 "no AgentId.Instructor; emergency events are System-sourced" decision.
2. `handleEngineFailure` silently no-op'd on unknown aircraft id (`state.aircraft[id] ?: return state to emptyList()`). A fixture authoring typo (or wrong event ordering vs Spawn) would leave the aircraft engine running while the test passed — degenerate ground truth.

## What Didn't Work
- **Constructor-param default for fixed-source events**: the default makes the value look enforced, but the parameter is still publicly accessible. Discipline drift looks impossible from the call site but is structurally allowed.
- **Defensive "no-op on missing target" handlers**: feels defensive, actually hides authoring defects. Mirrors the pre-Pass-7 `getValue` / silent-null pattern the no-corners rule deletes.

## Solution
- For fixed-source SimEvent subtypes (`source = AgentId.System`), declare `source` in the class body (`override val source: AgentId = AgentId.System`), NOT as a constructor parameter. Mirrors PhysicsTick / Spawn / TransmissionEnd / MissedHandoffDetected / FlightPlanFiled / AtisIssued.
- For sim-side handlers that consume an event referencing a specific aircraft id, fail loudly via `error("handler: no aircraft <id> in state.aircraft ...")` when the id is missing. Mirrors `handleSpawn`'s "duplicate aircraft id" / "positionPoint not in worldIndex" loud-fail invariants.

## Prevention
- **For new SimEvent subtypes**: grep audit at impl time — `grep -rE 'source: AgentId' sim/src/commonMain/.../SimEvent.kt` should show every fixed-source variant declaring `source` in the body, not as a ctor param. The pattern review at codex round-1 is the second-line defence.
- **For new event handlers**: ask "what's the failure mode if the named target is missing?" If the answer is "soft no-op", check whether the upstream fixture-load layer catches the authoring defect (e.g. `StartPointWithoutFlightPlan`). If it doesn't, the sim core itself must be strict.
- **Recipe**: fixed-source body-declared val + loud-fail handler is the round-1-clean shape for emergency / instructor-channel events. The pattern lands here as a memory anchor for fn-28.9+ (fuel exhaustion, icing, divert).
