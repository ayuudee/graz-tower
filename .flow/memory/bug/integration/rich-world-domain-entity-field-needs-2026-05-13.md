---
title: Rich-world-domain entity field needs explicit pilot-firewall KDoc discipline
date: "2026-05-13"
track: bug
category: integration
module: core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt
tags: [fn-16, rich-world-domain, pilot-firewall, kdoc-discipline, reachability, migration]
problem_type: integration
symptoms: "Codex impl-review (Major, 75% confidence) flagged Aerodrome.weather reachable via PilotInput.world.aerodromes[id].weather — full WeatherObservation triple exposed where the typed projection only carries WindReport"
root_cause: Migrating time-varying state onto a shared entity creates a new reachability path through every consumer that takes the whole entity; the firewall projection field protects the typed surface but not the underlying reachability — needs explicit KDoc discipline + filed structural-enforcement deferment
resolution_type: documentation
---

## Problem
Hoisting time-varying state onto a shared world entity (`Aerodrome.weather`, mirror of fn-12's `Runway.obstruction`) creates a new reachability path through every consumer that takes the entire entity. `PilotInput.world: AviationWorld` is a chart-input field, so post-migration a pilot rule could read `input.world.aerodromes[id].weather.qnh` and bypass the typed wind-only projection at `PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport>`. The migration's epic spec explicitly accepted this as a convention/discipline (same shape as fn-12's `Runway.obstruction` reachability), but the new reachability path is not surfaced anywhere — code review or future contributors could miss it.

## What Didn't Work
Implicitly trusting "the firewall surface is the typed projection field" is enough when the entity didn't carry the dynamic state. Once the dynamic state lives on the entity, the reachability fact has to be **explicitly documented on the entity field's KDoc** — otherwise a future reader sees `Aerodrome.weather` and naturally assumes "pilots can read this" because PilotInput.world is in their input.

## Solution
Two-layer fix:
1. **Entity field KDoc** explicitly calls out the pilot-side firewall discipline: pilot rules MUST read through the typed projection (`PilotInput.weatherByAerodrome`), never directly off `world.aerodromes[id].weather`. Mirrors the discipline already conventional for `Runway.obstruction`. Cited at `core/.../WorldModel.kt:387` (the `Aerodrome.weather` KDoc).
2. **Structural-enforcement deferment** filed for the long-term fix: `D-PASS-pilot-world-strip-dynamic-state` proposes a `:pilot/PilotAviationWorld` typed projection that strips entity-level dynamic fields at the firewall boundary, so reading them from pilot code fails to compile.

## Prevention
For future rich-world-domain migrations:
- Every entity field that adds dynamic state must, in its KDoc, name the firewall-projection paths and explicitly forbid direct reads from any consumer that takes the whole entity.
- The fn-12 `Runway.obstruction` precedent should also be retrofitted with the same explicit-discipline KDoc (currently silent; same reachability concern exists).
- The structural-enforcement story (typed pilot-side projection that strips dynamic fields) should be filed as a deferment at migration time, not deferred to "someone notices later".
