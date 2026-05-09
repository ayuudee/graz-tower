---
title: "State-threading specs need consumption sites, not just seeding"
date: "2026-05-09"
track: bug
category: build-errors
module: sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/Step.kt
tags: [rng, determinism, state-threading, per-aircraft, review-feedback]
problem_type: build-error
symptoms: "Helper map seeded + tested in isolation, but production tick path never consumes it"
root_cause: "Threading wired only at seeding sites (initial, handleSpawn), not at consumption site (handlePilotTick)"
resolution_type: fix
---

## Problem
Spec called for "per-aircraft RNG threading through pilot-tick dispatcher",
but on first implementation only `SimState.rngByAircraft` was added with
seeding/helpers — `handlePilotTick` did not actually call
`state.aircraftRng(id)` or `state.withAircraftRng(id, advanced)`. The
helper map was tested in isolation but the production tick path didn't
consume it, so the determinism contract was symbolic, not load-bearing.

## What Didn't Work
Adding state + helpers + spawn-path seeding without touching the
pilot-tick dispatcher. Codex review (75% confidence, classification:
introduced) flagged that no production sampling site read the new
per-aircraft stream — the unit-only PerAircraftRngSpec proved the
helper map was deterministic, not that the pilot tick used it.

## Solution
Wire the threading through `Step.kt:handlePilotTick`:
- Read `state.aircraftRng(event.aircraftId)` at the start of the tick.
- Advance once via `acRng.nextLong()` (load-bearing observable advance).
- Persist via `state.withAircraftRng(id, advancedRng)` in the return path.

Plus an integration test that drives a real `PilotDecisionTick` through
`step()` and asserts (a) the named aircraft's RNG advanced, (b) the
other aircraft's RNG is byte-stable. This is the load-bearing R2
assertion: production tick handling consumes per-aircraft RNG.

The single advance per tick keeps G0/G2 trace-stable because no
existing sampling site reads the per-aircraft stream — the advance is
invisible to G0/G2 emissions.

## Prevention
For state-threading specs, always:
1. Identify both the *seeding* site (init / spawn) AND the *consumption*
   site (the tick handler / sampling site).
2. Add at least one production code path that reads + writes the new
   state, even if no consumer needs the value yet — otherwise the
   threading is symbolic and the determinism property is unobservable.
3. Add an integration test that drives the production path and asserts
   the threading actually happened (state mutation + adjacent
   byte-stability), not just unit tests of the helpers.

A useful smoke test before review: `grep` the new field/helper names
across `Step.kt` (or the production hot path). If the only references
are inside a constructor / smart-init / spawn-path, the threading is
incomplete.
