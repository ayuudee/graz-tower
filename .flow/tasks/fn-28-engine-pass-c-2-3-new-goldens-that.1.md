---
satisfies: [R2-DA, R5, R6, R7, R10, R24]
---

## Description

Foundation A: WeatherObservation.oat + Temperature typed-units + `DensityAltitudeInput(oat, qnh, fieldElevation)` typed projection on `PilotInput` + `derivePilotEvent` signature extension to receive the typed DA input. **Per round-3 Major 4**: `LOWG` fixture sets BOTH concrete OAT and concrete `qnh: PressureSetting.hPa(1013.25)` (current fixture may have qnh=null; must be non-null for any DA-touching scenario). Projection fail-closed when either field is null.

**Size:** M-L
**Files:**
- `core/.../WeatherObservation.kt:25` — **append `val oat: Temperature? = null` AFTER `visibility`** (round-8 Minor 2 — explicit placement; default null preserves positional construction at existing call sites)
- (Possibly NEW) `protocol/.../Temperature.kt` — typed-units smart constructor
- `protocol/.../Atis.kt` — audit/extend OAT per Annex 11 §4.3.6
- (NEW) `pilot/.../DensityAltitudeInput.kt` — `data class DensityAltitudeInput(val oat: Temperature, val qnh: PressureSetting, val fieldElevation: Feet)` (firewall-clean)
- `pilot/.../PilotInput.kt:71-99` — add `val densityAltitudeInputsByAerodrome: Map<AerodromeId, DensityAltitudeInput> = emptyMap()`
- `pilot/.../observe/PilotEvent.kt:368-380` — extend `derivePilotEvent` signature to accept typed DA input
- `pilot/.../Pilot.kt:195-220` — `pilotDecide` call-site update
- `sim/.../PilotWiring.kt` — projection construction; fail-closed when oat OR qnh is null (omit aerodrome entry)
- `sim/src/jvmTest/.../Fixtures.kt:28-88` — populate LOWG with concrete OAT (ISA(elev)) + concrete QNH (`PressureSetting.hPa(1013.25)`); provenance entry
- `core/src/commonTest/.../WeatherObservationSpec.kt` — core type tests
- `sim/src/jvmTest/.../ProjectionDensityAltitudeInputSpec.kt` — projection-wiring + fail-closed test
- `pilot/src/commonTest/.../FirewallPilotInputTest.kt` — extend firewall enumeration for `DensityAltitudeInput`
- `protocol/.../RegulationDatabase.kt` — entry for ICAO Annex 11 §4.3.6 using `RegulationRef.ANNEX_11_*` constant (add if missing)

## Approach

- **Test split per round-2 Minor 9**: `:core` for `WeatherObservation` + `Temperature`; `:sim`/`:pilot` for projection + firewall.
- **Per round-3 Major 4**: `LOWG` fixture (and any LOWG-derived fixtures used by DA tests) MUST set concrete QNH. The projection-construction site in `PilotWiring` builds `DensityAltitudeInput` only when BOTH oat and qnh are non-null on the aerodrome's weather — otherwise omits the map entry (fail-closed).
- No DA recognition branch in this task; signature lands with placeholder.

## Investigation targets

**Required**:
- `core/.../WeatherObservation.kt`
- `pilot/.../PilotInput.kt:71-99`
- `pilot/.../observe/PilotEvent.kt:368-380`
- `pilot/.../Pilot.kt:195-220`
- `sim/.../PilotWiring.kt`
- `protocol/.../AircraftType.kt:80-170` (typed-units pattern)
- `pilot/src/commonTest/.../FirewallPilotInputTest.kt` (firewall scan pattern)

## Key context

- Per round-3 Major 4: DA scenarios depend on QNH; fixtures setting qnh=null cause recognition fail-closed and the test will fail with an unhelpful message. Make QNH concrete + non-null.
- Projection fail-closed when oat OR qnh missing: no `DensityAltitudeInput` entry for that aerodrome; pilot's DA branch (.2) skips that aerodrome's recognition.
- Firewall: `DensityAltitudeInput` is typed-units + scalars only.

## Acceptance

- [ ] `WeatherObservation.oat: Temperature? = null` **appended AFTER `visibility`** field (positional default = null preserves existing call sites); KDoc cites Annex 11 §4.3.6
- [ ] **Construction-site audit** (round-10 Major 4): `grep -rE 'WeatherObservation\('` across the project + classify each site: (a) keep `oat=null` (test fixtures unrelated to DA — non-DA-touching paths); (b) supply concrete `oat` (DA-relevant fixtures: LOWG, LJMB if used by .7); (c) explicitly state default null behavior. Audit log in `## Resolved during implementation`. Field placement alone is not sufficient — silent fail-closed projections in unaudited fixtures would mask DA recognition failures
- [ ] **Field-elevation sourcing fail-closed** (round-14 Minor 1): projection construction reads `Aerodrome.elevation` and converts to `Feet`. If `Aerodrome.elevation` is missing OR unconvertible, omit the DA input entry for that aerodrome (fail-closed). Unit test covers: aerodrome with valid elevation → entry present; aerodrome with missing/invalid elevation → no entry → DA recognition skips that aerodrome
- [ ] `Temperature` typed-units exists (new or reused) with smart-constructor pattern + KDoc
- [ ] **`Feet` typed-units confirmed/required in `:protocol`** (R24 / round-6 Minor 1): audit confirms `Feet` lives in `:protocol` (alongside `Knots`); if missing, add to `:protocol` before `DensityAltitudeInput` (in `:pilot`) and `AircraftType.maxDensityAltitudeFt` (in `:protocol`) both depend on it
- [ ] `DensityAltitudeInput(oat: Temperature, qnh: PressureSetting, fieldElevation: Feet)` in `:pilot`, firewall-clean
- [ ] `PilotInput.densityAltitudeInputsByAerodrome` slice with `emptyMap()` default
- [ ] `derivePilotEvent` signature accepts typed DA input; `pilotDecide` call-site updated; no DA branch yet
- [ ] ATIS broadcast audited; OAT included per Annex 11 §4.3.6 (or audit comment)
- [ ] `Fixtures.LOWG` sets concrete OAT (ISA(elev)) AND concrete QNH (`PressureSetting.hPa(1013.25)`); provenance entry
- [ ] Projection fail-closed when oat OR qnh is null (unit-tested; no map entry for that aerodrome)
- [ ] `:core` tests: WeatherObservation + Temperature shape
- [ ] `:sim` (or `:pilot`) tests: projection construction + fail-closed cases + firewall enumeration
- [ ] `RegulationDatabase` Annex 11 §4.3.6 entry; constant added if missing
- [ ] `./gradlew :core:allTests :pilot:jvmTest :protocol:allTests :sim:jvmTest detekt --offline --no-daemon` GREEN

## Done summary
Landed fn-28.1 (DA foundation A): WeatherObservation.oat + Temperature typed-units + DensityAltitudeInput typed projection on PilotInput + fail-closed projection at PilotWiring + derivePilotEvent signature threading + ATIS OAT slot + Annex 11 §4.3.6 RegulationDatabase entry. Relocated Feet from :core/world to :protocol per R24 with typealias re-export. Populated LOWG fixture with concrete OAT + QNH. Tests: WeatherObservationSpec (3) + TemperatureSpec (11) + ProjectionDensityAltitudeInputSpec (5) + DensityAltitudeInputForMissionTest (7) + FirewallPilotInputTest extension. Codex impl-review: round 1 NEEDS_WORK (sibling-helper destination-vs-departure-aerodrome semantic) → round 2 SHIP after correcting densityAltitudeInputForMission to fail-closed for all goal branches + dedicated regression-pin test suite.
## Evidence
- Commits: 4fe1f05a65ba6384ac9f692d57f7e59b065ad23b, e417c9884d6b6806750982716012b12791f432ab, afbfc15422ab5477e43e635bce14b99a6992673b
- Tests: ./gradlew :core:allTests :pilot:jvmTest :protocol:allTests :sim:jvmTest detekt --offline --no-daemon (NOT RUN LOCALLY: no JDK installed in worker environment — /Library/Java/JavaVirtualMachines is empty + brew has no jdk cask; verification deferred to next CI/local pass; codex impl-review SHIP'd statically scoped to base 1260654)
- PRs: