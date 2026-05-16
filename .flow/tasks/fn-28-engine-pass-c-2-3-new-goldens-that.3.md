---
satisfies: [R2-DA, R2a, R2b, R2c, R2d, R2e, R3, R4, R5, R7, R14, R17]
---

## Description

DA sim golden. C172 at LOWG apron-stay. **Fixture sets concrete OAT and QNH numerically** (round-3 Major 4): `oat = Temperature.celsius(47.8)` (ISA+35°C at LOWG elev 1115 ft = 12.79+35 = 47.79; round to 47.8 OR use a computed-from-fixture value) + `qnh = PressureSetting.hPa(1013.25)`. **Test asserts numerical DA via `computeDensityAltitudeFeet`** (round-4 Major 4 / R17) — NO prose approximations. **Test asserts ZERO `RequestTaxi` transmissions** (round-3 Critical 1 / R14). Three-layer pin. Archive flip.

**Size:** M
**Files:**
- `sim/src/jvmTest/.../G3aPilotReactiveDensityAltitudeTest.kt` (new)
- `sim/src/jvmTest/.../Fixtures.kt:28-88` — `LOWG_HIGH_DA` variant: concrete OAT (numerically computed: `15.0 - (1115/1000)*1.98 + 35.0`) + concrete QNH 1013.25; provenance entry
- `docs/deferments.md` — archive flip

## Approach

- KDoc 200+ lines per tailwind template.
- **Numerical DA via `computeDensityAltitudeFeet`** (R17): test reads fixture OAT/QNH + LOWG elevation; calls `computeDensityAltitudeFeet(input)`; asserts `> 5000`. The fixture's OAT is chosen so the function returns ≥ 5500 ft (comfortably above threshold).
- Three-layer pin:
  - L1 causal: fixture weather projection construction (static — no OAT-update transition; round-10 Minor 1) → DA input enters `PilotInput.densityAltitudeInputsByAerodrome` → recognition (via `computeDensityAltitudeFeet`) → mission rewrite via `replaceFromActivePrimitive([PrimitiveTask(DECLINE_DEPARTURE, NON_COMPLETING)])`.
  - L2 sticky-witness: mission's rendered task-list shows the suffix replaced with `[DECLINE_DEPARTURE]`.
  - L3 kinematic non-event: positionPoint constant; targetSpeedMps=0; altitudeM=0; **ZERO `RequestTaxi` transmissions emitted across decision tick** (R14 assertion).
- Archive flip per §8; Closed-by cites bundled-coverage + apron-stay + cognitive-suppression + numerical DA assertion.

## Investigation targets

- Tasks .1 + .2 outputs
- `sim/.../G3aPilotReactiveTailwindTest.kt:47-310`
- `sim/.../Fixtures.kt:28-88`
- `docs/deferments-CONVENTION.md:314-340`

## Key context

- **R17**: numerical DA via `computeDensityAltitudeFeet`. NO prose like "DA ≈ 5500".
- **R14**: ZERO RequestTaxi assertion.
- Concrete numeric OAT in fixture (computed from LOWG elevation + ISA offset).

## Acceptance

- [ ] `G3aPilotReactiveDensityAltitudeTest.kt` lands with exhaustive KDoc + 1 `@Test` method
- [ ] Three-layer pin: causal + mission-tree suffix-replacement + kinematic non-event
- [ ] Numerical DA assertion via `computeDensityAltitudeFeet(input)` — NO prose
- [ ] Test asserts ZERO `RequestTaxi` transmissions emitted across decision tick (R14)
- [ ] Failure message includes AC 61-107B §3-1 + 5000 ft threshold + computed DA value
- [ ] `Fixtures.LOWG_HIGH_DA`: numerical OAT (LOWG ISA+35 computed from elevation) + concrete QNH 1013.25; provenance entry
- [ ] `docs/deferments.md` archive flip for `D-PASS-g3a-react-other-poh-triggers` per §8; Closed-by cites bundled-coverage + apron-stay + cognitive-suppression + numerical DA
- [ ] Targeted run GREEN
- [ ] Full verify GREEN

## Done summary

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
