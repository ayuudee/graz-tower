---
title: Instructor-channel causation for sim-level emergency events (engine failure firs
date: "2026-05-16"
track: knowledge
category: decisions
module: pilot/PilotMission.kt + sim/SimEvent.kt
tags: [pilot, instructor, emergency, firewall]
applies_when: Instructor-channel causation for sim-level emergency events (engine failure firs
---

Engine failure (and future D-AUDIT.9 emergencies — fuel exhaustion, icing/divert) enter the sim via the instructor channel — same shape as instructor-authored `CircuitOutcome.GoAround` — not via a world hook authoring `SimEvent.EngineFailure` directly.

## Considered Options
- **Direct world-hook causation** — test fixture emits `SimEvent.EngineFailure` directly. Simpler but smuggles world-side state past the pilot firewall: the failure observation is a cockpit input, not a world fact, so authoring it world-side is a firewall ergonomics regression.
- **Stochastic per-tick failure model** — engine fails per-tick under seeded probability. Realistic but conflicts with golden determinism gates (`step(s,e) == step(s,e)`); incompatible with the proof-aligned reversal-aware testing approach.
- **Instructor-channel causation (chosen)** — the instructor channel (`PilotMission`-style scaffolding) carries an `EngineFailureBriefing(time)` so the pilot's decision branch reads the failure as a legitimate cockpit input. Sim emits `SimEvent.EngineFailure` as a *consequence* at the briefed time, not as the cause.

## Consequences
- All future sim-level emergencies (D-AUDIT.9.IV fuel exhaustion, .V icing, divert) follow the same instructor-channel shape. Single architectural pattern.
- Pilot firewall stays clean: engine-failure semantics enter through `PilotMission` (already a cockpit input), not through a side channel.
- Test fixtures author scenarios by briefing the instructor channel — symmetric with `CircuitOutcome.GoAround` and other planned-training events.
- The instructor channel is testing scaffolding; production deployment would replace instructor-driven events with real failure models. v1 scope is golden-test reach only.
