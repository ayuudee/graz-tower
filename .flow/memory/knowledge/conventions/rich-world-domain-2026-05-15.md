---
title: "Rich-world-domain: time-varying state lives on the entity it concerns"
date: "2026-05-15"
track: knowledge
category: conventions
module: core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt
tags: [rich-world-domain, world-model, aerodrome, runway, weather, obstruction, pilot-firewall, kdoc-discipline, fn-12, fn-16]
applies_when: Adding new time-varying state to the world model, OR migrating existing flat-map state (`SimState.<x>By<Entity>` / `World.<x>By<Entity>`) onto an entity field, OR reviewing a pilot-rule change that touches `PilotInput.world.aerodromes` / `.runways` reachability paths.
related_to: [bug/integration/rich-world-domain-entity-field-needs-2026-05-13]
---

## The convention

Time-varying state about a world entity lives on the **entity it concerns**
(`Aerodrome.weather`, `Runway.obstruction`), not on a flat `World`-root
or `SimState`-root map keyed by entity id. Migrations from flat maps to
entity-fields are **atomic hard cutovers** — no shim, no parallel shape,
no deprecated field.

When the dynamic state is also pilot-visible, the entity-field's KDoc
**must explicitly call out the pilot firewall projection** and forbid
direct reads from any consumer that takes the whole entity.

## Why this shape

`bug/integration/rich-world-domain-entity-field-needs-2026-05-13.md`:
hoisting time-varying state onto a shared world entity creates a new
reachability path through every consumer that takes the entire entity.
`PilotInput.world: AviationWorld` is a chart-input field, so once
`Aerodrome.weather` lives on the entity, a pilot rule could read
`input.world.aerodromes[id].weather.qnh` and bypass the typed wind-only
projection `PilotInput.weatherByAerodrome: Map<AerodromeId, WindReport>`.

Codex impl-review (Major, 75% confidence) flagged this on fn-16: the
full `WeatherObservation` triple is reachable through the entity even
though the firewall projection only carries `WindReport`. The discipline
is conventional (the same shape applies to fn-12's `Runway.obstruction`),
but the new reachability fact was not surfaced on the entity field's
KDoc — code review or future contributors could miss it.

Implicit trust ("the firewall surface is the typed projection field" is
enough) **stops working** once the dynamic state lives on the entity.
The reachability has to be explicitly documented at the entity field.

## Precedents

- **fn-12 — `Runway.obstruction`:** migrated from
  `SimState.runwayObstructions: Map<RunwayId, RunwayObstruction>` to
  `Runway.obstruction: RunwayObstruction?`. Atomic cutover; no shim.
  (Note: fn-12's KDoc is silent on the pilot-firewall reachability;
  retrofit recommended, same concern exists.)
- **fn-16 — `Aerodrome.weather`:** migrated from
  `SimState.weatherByAerodrome` to `Aerodrome.weather: WeatherObservation?`.
  Atomic cutover; no shim. KDoc explicitly forbids pilot-direct reads
  per the fn-16.2 codex-review finding cited above.

## Forward-applicable rule

When **adding** new time-varying state to the world model:

1. **Default to entity-field shape from day 1.** Place the field on the
   entity it concerns; do not introduce a flat `World`-root / `SimState`-
   root map first and "migrate later." The migration is the cost; the
   entity-field is the shape.

2. **Author the entity field's KDoc to name the firewall projections.**
   If pilot rules see the entity, the KDoc MUST:
   - Name the typed projection field pilot rules must read through
     (e.g. `PilotInput.weatherByAerodrome`).
   - Explicitly forbid direct reads from `input.world.aerodromes[id].<field>`
     or analogous paths through the entity.
   - Cite the convention's anchor in `.flow/memory/knowledge/conventions/rich-world-domain-2026-05-15.md`.

3. **File a structural-enforcement deferment** at migration time, not
   later. The long-term fix for the reachability concern is a typed
   `:pilot/PilotAviationWorld` projection that strips entity-level
   dynamic fields at the firewall boundary, so direct reads from pilot
   code fail to compile. File this as `D-PASS-pilot-world-strip-dynamic-state`
   (or analog) per the deferment register convention.

When **migrating** existing flat-map state to the entity:

1. **Plan an atomic-cutover epic** modeled on fn-16. No parallel shape,
   no shim, no deprecated field. The cutover is the test.
2. **Retrofit the entity field's KDoc** to name the firewall projections
   per the rule above, BEFORE landing the migration commit.
3. **Audit existing analogous fields** for missing firewall-KDoc — fn-12's
   `Runway.obstruction` is the standing example.

## Anti-patterns

- `SimState.weatherByAerodrome: Map<AerodromeId, WeatherObservation>` —
  the pre-`rich_world_domain` shape; fn-16 closed it. Any new flat map
  of `Map<EntityId, DynamicState>` at the `SimState`-root or `World`-root
  level should trigger a planning-pass review for entity-field migration.
- Silent KDoc on a pilot-visible dynamic entity field — fn-12's
  `Runway.obstruction` is the legacy example. Adding new dynamic state
  without the explicit firewall-discipline KDoc repeats the fn-16.2
  codex-finding shape.

## Cross-references

- Source capture (kept as authoritative event record):
  `.flow/memory/bug/integration/rich-world-domain-entity-field-needs-2026-05-13.md`
- Spec precedents:
  - fn-12 — `Runway.obstruction` migration
  - fn-16 — `Aerodrome.weather` migration
- Related deferment shape (long-term structural fix):
  `D-PASS-pilot-world-strip-dynamic-state` (proposed at fn-16.2)
