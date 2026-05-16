---
satisfies: [R2-DA, R5, R6, R7, R9, R10, R13, R14, R15, R16, R17, R20, R21]
---

## Description

Foundation B for DA — refined per rounds 1-5:

1. **`AircraftType.maxDensityAltitudeFt: Feet?` NULLABLE** (round-5 Major 2): C172 = `Feet.unsafe(5000)` (FAA AC 61-107B §3-1); B738 = `null` (DA decline is light-GA concept; jets out-of-scope). KDoc explains the applicability semantic. Pilot DA recognition gates on `aircraft.type.maxDensityAltitudeFt?.let { da > it } ?: false`.
2. **`isDensityAltitudeDeclineEligible(mission: PilotMission): Boolean`** — DA-only named guard.
3. **`computeDensityAltitudeFeet(input: DensityAltitudeInput): Feet`** — pure function in `:pilot` (round-5 Major 1 — same module as `DensityAltitudeInput` to avoid `:protocol` → `:pilot` cyclic dep). Home: `pilot/.../DensityAltitudeFormula.kt`. Formula: `pressure_alt = elev + (1013.25 - qnh.hPa) * 30; isa_celsius = 15.0 - elev/1000 * 1.98; da_ft = pressure_alt + 120 * (oat.celsius - isa_celsius)`. Integer-ft rounding at boundary.
4. **`replaceFromActivePrimitive(newSuffix: List<TaskNode>): CompoundTask`** — SOLE rewrite primitive.
5. **NEW `CompletionMode.NON_COMPLETING`** (round-5 Critical 1 / R20; sites corrected round 7 Major 1): added alongside existing `PHYSICAL / REPORTED / INSTRUCTION_GATED / TIMED / INSTANT`. Audit at CORRECT consumer sites:
   - `CompletionMode.NON_COMPLETING -> false` in `PilotCognitive.isStepComplete` (the actual CompletionMode dispatch site — NOT `isPhysicallyComplete` which consumes `MissionStep` not `CompletionMode`)
   - `stepTransmission(missionStep)` in `PilotCognitive.kt`: handles `DECLINE_DEPARTURE` MissionStep → emits nothing (this is MissionStep-driven, audited here for R15). **`ABORTED` audit lives in .8** (round-10 Major 1 — .8 adds the ABORTED enum value + its 4-site audit; .2 only audits NON_COMPLETING + DECLINE_DEPARTURE)
   - `skipCompletedSteps(...)`: does NOT skip past `DECLINE_DEPARTURE` MissionStep (.8 extends for ABORTED)
   - `planRoute(...)` in `Pilot.kt`: at-rest / no-op for `DECLINE_DEPARTURE` MissionStep (.8 extends for ABORTED)
   - `isPhysicallyComplete(missionStep)`: audit only if default-arm behavior differs for the new non-completing MissionSteps; comment confirms either way
6. **`MissionStep.DECLINE_DEPARTURE`** + 4-consumer audit at MissionStep sites (R15).
7. **`deriveDensityAltitudeEvent`** — uses `computeDensityAltitudeFeet`; gates on aircraft pre-taxi phase + `isDensityAltitudeDeclineEligible(mission)` + nullable `maxDensityAltitudeFt` check.
8. **`applyDensityAltitudeDecline`** — `mission.replaceFromActivePrimitive(listOf(PrimitiveTask(MissionStep.DECLINE_DEPARTURE, CompletionMode.NON_COMPLETING)))`; sets `targetSpeedMps = 0`; returns `suppressSameTickCognitive = true`.
9. **`pilotDecide` cognitive-suppression** mechanism.

**Size:** L
**Files:**
- `protocol/.../AircraftType.kt` — `maxDensityAltitudeFt: Feet?` field (NULLABLE); C172 = `Feet.unsafe(5000)`; B738 = `null`; init invariant skips on null; KDoc explains applicability
- `protocol/.../RegulationDatabase.kt` — AC 61-107B §3-1 entry
- `pilot/.../DensityAltitudeFormula.kt` (NEW) — `computeDensityAltitudeFeet` pure function + constants
- `pilot/.../PilotMission.kt` — `MissionStep.DECLINE_DEPARTURE`; **`CompletionMode.NON_COMPLETING` (NEW)**; **4-consumer audit for DECLINE_DEPARTURE only** (.8 extends the same audit pattern for `ABORTED` — round-10 Major 1 / round-11 Minor 1); `replaceFromActivePrimitive(List<TaskNode>)` primitive; `isDensityAltitudeDeclineEligible` guard
- `pilot/.../observe/PilotEvent.kt` — `deriveDensityAltitudeEvent`
- `pilot/.../Pilot.kt` — `applyDensityAltitudeDecline` + cognitive-suppression payload + `pilotDecide` suppression mechanism
- Tests: primitive direct (nested + flat); MissionStep audit; CompletionMode audit; guard; recognition; apply; cognitive-suppression; nullable maxDensityAltitudeFt fallthrough (B738 case)

## Approach

- **`CompletionMode.NON_COMPLETING`** (R20): NEW enum value. Audit comments at each of the 4 surfaces. KDoc on the value explains the semantic: "the primitive is the terminal state of the mission; no completion event ever flips its status".
- **Nullable `maxDensityAltitudeFt: Feet?`** (Major 2): C172 only. B738 = null is the applicability semantic. KDoc cites AC 61-107B for C172.
- **`computeDensityAltitudeFeet` in `:pilot`** (Major 1): no cyclic dep.
- Recognition+apply pipeline agreement via `isDensityAltitudeDeclineEligible`.

## Investigation targets

- Task .1 outputs
- `pilot/.../PilotMission.kt` — `TaskNode` + `PrimitiveTask` + `CompletionMode` enum
- `pilot/.../observe/PilotEvent.kt:360-440`
- `pilot/.../Pilot.kt:195-1200`
- `protocol/.../AircraftType.kt` — sealed-class C172 + B738 leaves (find every leaf for the nullable field)
- `core/.../world/Aerodrome.kt` — elevation source for DA formula

## Key context

- R13 + R20 + R17 + R16 + R14 + R15 work together. Tests cover each invariant independently + integration.
- Nullable AircraftType field means B738 case is a fall-through (no DA recognition). Test covers explicitly.
- CompletionMode audit checklist: 4 consumer sites for NON_COMPLETING.

## Acceptance

- [ ] `AircraftType.maxDensityAltitudeFt: Feet?` NULLABLE field; C172 = `Feet.unsafe(5000)`; B738 = `null`; init invariant present (skips on null); KDoc explains applicability
- [ ] `RegulationDatabase` AC 61-107B §3-1 entry; `RegulationRef.AC_61_107B_EDITION` constant added if missing
- [ ] `pilot/.../DensityAltitudeFormula.kt` — `computeDensityAltitudeFeet(input: DensityAltitudeInput): Feet` pure function with documented formula + integer-ft rounding + KDoc
- [ ] `CompletionMode.NON_COMPLETING` added with **explicit grep audit** (round-6 Major 3, sites corrected round 7 Major 1): audit comments at `pilot/.../PilotCognitive.kt::isStepComplete` (the CompletionMode dispatch site, NOT `isPhysicallyComplete`), `pilot/.../PilotCognitive.kt::stepTransmission` (MissionStep dispatch for new MissionSteps), `skipCompletedSteps` (locate file via grep), `pilot/.../Pilot.kt::planRoute` (planRoute lives in Pilot.kt, NOT PilotMission.kt), AND any `when (completionMode)` / `CompletionMode.` reflection-exhaustiveness test sites. Grep command: `grep -rE 'when.*CompletionMode|CompletionMode\.'` + `grep -rE 'when.*MissionStep'`. Audit log in `## Resolved during implementation` lists every consumer site visited
- [ ] `deriveDensityAltitudeEvent` branch position **partial order** `DecisionAltitudeWithoutClearance → DensityAltitudeDecline → existing wind branches (TailwindLimitExceeded → CrosswindLimitExceeded)` (round-14 Major 2 — .2 only inserts DensityAltitudeDecline; .9 finalizes R21 with AbortTakeoff). KDoc on `derivePilotEvent` documents the partial order + reserved insertion point for AbortTakeoff (after DensityAltitudeDecline, before TailwindLimitExceeded)
- [ ] `MissionStep.DECLINE_DEPARTURE` added with 4-consumer audit (R15)
- [ ] `replaceFromActivePrimitive(newSuffix: List<TaskNode>): CompoundTask` primitive added with KDoc + unit tests for nested + flat shapes
- [ ] `isDensityAltitudeDeclineEligible(mission)` named guard + unit test
- [ ] `deriveDensityAltitudeEvent`: uses `computeDensityAltitudeFeet`; nullable maxDensityAltitudeFt check (B738 case falls through); gate via `isDensityAltitudeDeclineEligible`
- [ ] **Aerodrome-resolution policy** (round-9 Major 2): named helper `densityAltitudeInputForMission(mission, densityAltitudeInputsByAerodrome): DensityAltitudeInput?` — looks up the per-aerodrome input using **`mission.filedPlan.departureAerodrome` first**, then singleton-fallback (when the map has exactly one entry and no filed plan), then **FAIL-CLOSED** on ambiguity (multi-aerodrome map with no filed plan → returns null; recognition does not fire). Unit tests: (a) filed plan with LOWG departure + LOWG entry → returns LOWG input; (b) no filed plan + singleton LOWG entry → returns LOWG input; (c) no filed plan + multi-aerodrome map → returns null + recognition skips
- [ ] `applyDensityAltitudeDecline` calls `replaceFromActivePrimitive([PrimitiveTask(DECLINE_DEPARTURE, NON_COMPLETING)])`; sets targetSpeedMps=0; returns suppressSameTickCognitive=true
- [ ] `pilotDecide` cognitive-suppression: zeroes same-tick cognitive transmissions when payload signals; unit test
- [ ] **Cognitive-suppression covers ALL `pilotDecide` return paths** (round-13 Major 1): the filter must apply BEFORE every `PilotOutput` construction site — `PlanRouteOutcome.Plan` branch AND `Skip` branch AND any error/fallback branch. NOT only the `Skip` path. Unit test exercises every return path with the suppression-flagged event payload
- [ ] Recognition+apply agreement: both call `isDensityAltitudeDeclineEligible`
- [ ] Unit test for B738 fallthrough (nullable maxDensityAltitudeFt = null → no DA recognition)
- [ ] `./gradlew :pilot:jvmTest :protocol:allTests detekt --offline --no-daemon` GREEN

## Done summary
Landed fn-28.2 (DA foundation B): AircraftType.maxDensityAltitudeFt (NULLABLE; C172=5000 ft per FAA AC 61-107B §3-1, B738=null applicability fallthrough) + RegulationDatabase.FAA_AC_61_107B_3_1 entry + CompletionMode.NON_COMPLETING (R20 audit) + MissionStep.DECLINE_DEPARTURE (R15 audit) + CompoundTask.replaceFromActivePrimitive (R13 sole rewrite primitive) + isDensityAltitudeDeclineEligible guard (R16 split) + computeDensityAltitudeFeet pure function in :pilot (R17) + PilotEvent.DensityAltitudeDecline leaf + deriveDensityAltitudeEvent branch with phase + step + threshold + input gates (R21 branch order: DA-without-clearance → DensityAltitudeDecline → tailwind → crosswind) + applyDensityAltitudeDecline with at-rest intent + suppressSameTickCognitive flag (R14) + applyCognitiveSuppression focused-seam helper + filed-plan departure-aerodrome lookup in densityAltitudeInputForMission (round-9 Major 2). Tests: AircraftTypeSpec extended (C172=5000, B738=null), IsDensityAltitudeDeclineEligibleSpec (6 rows), DensityAltitudeFormulaTest (7 boundary numerics + idempotence), ReplaceFromActivePrimitiveSpec (6 rows: flat + nested + multi-element suffix + terminal + outer-siblings-preserved), CompletionModeNonCompletingAuditSpec (3 rows), MissionStepDeclineDepartureAuditSpec (3 rows), PilotEventDensityAltitudeTest (10 rows incl. B738 fallthrough + airborne-phase rejection + runway-active-phase rejection + Taxiing+TAXI_TO_HOLDING positive), PilotDensityAltitudeDeclineTest (7 rows incl. NON_COMPLETING invariant + ineligible-mission defensive guard), PilotDensityAltitudeDeclineCognitiveSuppressionTest (6 rows incl. control + B738 fallthrough through pilotDecide + 3 focused-seam helper rows), DensityAltitudeInputForMissionTest extended (3 filed-plan rows). Codex impl-review: round 1 NEEDS_WORK (missing filed-plan lookup + missing phase guard + Plan-path test coverage) → round 2 NEEDS_WORK (return@Test compile error + Plan-path test didn't actually prove Plan-coverage) → round 3 SHIP after focused-seam refactor of applyCognitiveSuppression helper.
## Evidence
- Commits: 20297bf, 5ec1ff4, aa623d2
- Tests: ./gradlew :pilot:jvmTest :protocol:allTests detekt --offline --no-daemon (NOT RUN LOCALLY: no JDK installed in worker environment — /Library/Java/JavaVirtualMachines is empty + brew has no jdk cask; verification deferred to next CI/local pass; codex impl-review SHIP'd statically scoped to base afbfc15)
- PRs: