---
satisfies: [R3, R7, R9, R10]
---

## Description

Phase A of the G2 multi-phase plan. Two coupled changes:

1. **LJMB authoring fix** — extend `cad/airports/rendered/ljmb/world-candidate.json` to publish the TOWER role (frequency 119.205 MHz, per `migration/src/commonTest/resources/airports/LJMB.dat:1054`). Today the LJMB aerodrome block has NO `roles` field at all (verified in scout findings); `Fixture.validate()` raises `RoleNotPublished(role=TOWER, aerodrome=LJMB)`. This is the root cause of the pre-existing `FixtureLoadSpec` and `FixtureSanityTest` failures on master.

2. **`Fixtures.LOWG_LJMB_VFR` multi-aerodrome loader** — new fixture object alongside the existing `Fixtures.LOWG` and `Fixtures.LJMB`. Returns a `LoadedFixture` whose merged `AviationWorld` contains both LOWG and LJMB; staffs four controllers: `LOWG_GROUND` (118.200), `LOWG_TOWER` (118.200), `LOWG_APPROACH` (119.300), `LJMB_TOWER` (119.205). Single VFR FiledPlan(LOWG → LJMB) distributes via `AftnRouting.routeFiledPlan` to LOWG_GROUND (Owned) + LJMB_TOWER (knownStrips) — this falls out of existing routing automatically, no manual recipient list.

The existing single-aerodrome `Fixture` data class (`Fixture.kt:37-60`) cannot represent this directly: it has one `aerodromeId`, one `frequency`, one `controllerRoles` set. Choose one shape:
  - **(A)** Extend `Fixture` with optional per-aerodrome staffing (`additionalAerodromes: Map<AerodromeId, AerodromeStaffing>`).
  - **(B)** Introduce a sibling `MultiAerodromeFixture` data class with parallel `load(): Either<LoadError, LoadedFixture>`.
  - Pick one based on which preserves the existing `Fixtures.LOWG` / `Fixtures.LJMB` API. (B) is likely cleaner and avoids breaking single-aerodrome fixtures.

**Size:** M
**Files (expected):**
- `cad/airports/rendered/ljmb/world-candidate.json` (or its source CAD/manifest if regenerated via authoring pipeline)
- `sim/src/jvmTest/kotlin/.../testing/Fixtures.kt`
- `sim/src/jvmTest/kotlin/.../testing/Fixture.kt` (extension or sibling type)
- Possibly `sim/src/jvmTest/kotlin/.../testing/FixtureLoadSpec.kt` (one new row for LOWG_LJMB_VFR)

## Approach

- Reuse `WorldCandidateLoader.mergeAviationWorlds(...)` (`migration/src/commonMain/kotlin/.../WorldCandidateLoader.kt:362`). Pattern at `sim/src/jvmTest/.../CrossAerodromeFilingSpec.kt:186-195` (private `mergedWorld()` helper) is copy-pasteable as a starting point.
- Reuse `AftnRouting.routeFiledPlan` (`sim/src/commonMain/kotlin/.../AftnRouting.kt:43-89`) — already invoked from `Fixture.load()` at line 184. The multi-aerodrome variant must reuse, not bypass, this path.
- Reuse `Fixture.validate()` semantics (`Fixture.kt:219-243`); the new shape must produce a `LoadedFixture` that passes `validate()` against its declared roles.
- Verify both LOWG and LJMB world-candidates carry a `referencePoint` — `mergeAviationWorlds` requires this and hard-fails otherwise (`WorldCandidateLoader.kt:450-458`).
- LJMB stand point: today `Fixtures.LJMB.standPointId = PointId("LJMB_TWY_A_17_02")` (a taxiway point, not a stand entity per `cad/airports/rendered/ljmb/`). For Phase F's `positionPoint ∈ LJMB stand points` outcome assertion, either (a) add stand entities to LJMB authoring, or (b) document that the multi-aerodrome fixture exposes a `destinationStandPointId` and the integration test asserts equality against that point. Pick (b) for minimal authoring scope; document the choice.

## Investigation targets

**Required** (read before coding):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixtures.kt:27-78` — existing `Fixtures.LOWG` and `Fixtures.LJMB` — the API to preserve.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/Fixture.kt` — `Fixture` + `LoadedFixture` data classes; `Fixture.load()`; `validate()`.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/CrossAerodromeFilingSpec.kt:186-195` — multi-world merge helper template.
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateLoader.kt:362-460` — `mergeAviationWorlds` signature + preconditions.
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/AftnRouting.kt:43-102` — routing helper + failure leaves.
- `cad/airports/rendered/lowg/world-candidate.json` — reference shape for the `roles` block (LOWG publishes TOWER/GROUND on 118.200, APPROACH on 119.300 at lines 1631-1647).
- `cad/airports/rendered/ljmb/world-candidate.json` — the file to extend with the `roles` block; current state has none (look around line 496).

**Optional** (reference as needed):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/FixtureLoadSpec.kt` — for adding the new fixture-load row.

## Acceptance

- [ ] LJMB world-candidate.json publishes a `roles` map containing TOWER (frequency 119.205 MHz). The wiki `wiki/data-sources/ljmb.md` AIP citation supports this; `migration/src/commonTest/resources/airports/LJMB.dat:1054` confirms.
- [ ] `Fixtures.LOWG_LJMB_VFR` exists alongside `LOWG` and `LJMB`; calling `.load()` returns `Right(LoadedFixture)` (no validation violations).
- [ ] The merged `AviationWorld` contains both `AerodromeId("LOWG")` and `AerodromeId("LJMB")` keys.
- [ ] Loaded `controllers` map keys equal `setOf("LOWG_GROUND", "LOWG_TOWER", "LOWG_APPROACH", "LJMB_TOWER")` — exact-set match.
- [ ] A single `FiledPlan.Vfr(departure=LOWG, destination=LJMB, destinationRunway=RWY_14, intent=Transit)` produces exactly 2 `SimEvent.FlightPlanFiled` events in `initialEvents` — one targeting `LOWG_GROUND` (with `responsibilityKind = Owned`) and one targeting `LJMB_TOWER` (with `responsibilityKind = knownStrips`).
- [ ] `FixtureLoadSpec` and `FixtureSanityTest` pass (no `RoleNotPublished` violation).
- [ ] G0 (`LowgGoldenTest`) remains green.
- [ ] 3-agent plan review (impact, fp-review, test-review) on this task's plan, clean contexts, before implementation. 3-agent post-impl review on the diff. Findings folded per `feedback_agent_review_process`.
- [ ] Commit as `G2 Phase A: LJMB TOWER authoring + LOWG_LJMB_VFR fixture loader` with `Co-Authored-By` tail.


## Done summary
Phase A landed as `bd3370a`.

LJMB world-candidate.json gained TOWER role at 119.205 (resolves 3 pre-existing master failures: `FixtureLoadSpec`, `FixtureSanityTest`, `LoaderRolesPopulatedTest`).

New `MultiAerodromeFixture` sibling type with `NonEmptyList<AerodromeStaffing>`, per-role frequency map, `ControllerId`-keyed `LoadedFixture.controllers`, `controllerByRole`/`controllerAt` accessors, typed `RoutingFailed`/`MergeFailed` `LoadError` leaves.

`Fixtures.LOWG_LJMB_VFR` staffs LOWG_GROUND/TOWER/APPROACH + LJMB_TOWER.

3 new FixtureLoadSpec rows + FixtureSanityTest negative-trigger tightening. G0 unbroken.

Pre-existing `LjmbWorldCandidateValidationTest` (IFR SID inventory) failure documented as out of G2 (VFR) scope.
## Evidence
- Commits: bd3370a
- Tests: ./gradlew :protocol:allTests :pilot:allTests :controller:allTests :sim:jvmTest, ./gradlew :migration:jvmTest --tests LoaderRolesPopulatedTest --tests LoaderFrequencyConsistencyTest
- PRs: