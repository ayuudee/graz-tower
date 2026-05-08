---
satisfies: [R1, R2, R3, R4, R5, R6, R7]
---

## Description

Single task closing fn-7. Adds a per-aerodrome `Aerodrome.ctrApproximationRadius:
Meters` field, threads it through the JSON schema/loader, switches
`OutsideAerodromeRadius` to a `data object` that reads from world data,
and authors LOWG + LJMB values per AIP AD 2.17.

**Size:** M
**Files:**
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt`
  (`Aerodrome` data class + new `Doctrine.IcaoAnnex11` constant)
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateSchema.kt`
  (`CandidateAerodrome` + new field)
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateLoader.kt`
  (loader threading + smart-constructor invocation)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt`
  (`OutsideAerodromeRadius` becomes `data object`)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerDeparture.kt`
  (two call sites: drop the `Meters.fromNauticalMiles(12)` argument)
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/bdi/OutsideAerodromeRadiusSpec.kt`
  (refactor 3 existing rows + add ICAO-floor-fallback row)
- `cad/airports/rendered/lowg/world-candidate.json`
  (author `ctrApproximationRadiusNauticalMiles: 16` — see Approach §6)
- `cad/airports/rendered/ljmb/world-candidate.json`
  (author `ctrApproximationRadiusNauticalMiles: 5` — placeholder, see
  Approach §7)
- `wiki/data-sources/lowg.md`, `wiki/data-sources/ljmb.md`
  (note the authored radius + AIP AD 2.17 source citation)

## Approach

1. **Add `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`** at
   `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt`
   (or a new sibling file `core/.../world/Doctrine.kt` if you prefer
   isolation — match existing conventions). The value is
   `Meters.fromNauticalMiles(5)`. KDoc carries the verbatim Annex 11
   §2.11.4.2 wording: *"The lateral limits of control zones shall extend
   to at least 9.3 km (5 NM) from the centre of the aerodrome…in the
   directions from which approaches may be made."* Add a sentence
   acknowledging this is **directional, not omnidirectional** — a
   circular 5 NM is anisotropic-wrong by construction (short on the
   approach axis where polygon may extend further; generous abeam).

2. **Extend `Aerodrome` data class** at `WorldModel.kt:319-349`:
   - Add `val ctrApproximationRadius: Meters` (non-nullable) at the end
     of the parameter list (all existing fields have defaults; this one
     shouldn't — it's resolved at construction).
   - Add a smart-constructor companion factory that takes a nullable
     input radius and resolves to `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`
     when null. Pattern sketch (signature only):
     `fun Aerodrome.Companion.fromCandidate(..., ctrApproximationRadius:
     Meters? = null): Aerodrome`.
   - Add KDoc on the new field disambiguating from polygon-CTR doctrine
     (per practice-scout #4): "Circular sim approximation. Real CTR is
     polygonal per AIP AD 2.17; this is a single-radius stand-in for
     the runtime guard. Polygon containment is FM/Lean territory
     (`fn-4` lineage); see `D-AUDIT-polygon-ctr` deferment."

3. **Extend `CandidateAerodrome` JSON schema** at
   `migration/.../WorldCandidateSchema.kt:88-115`:
   - Add `val ctrApproximationRadiusNauticalMiles: Int? = null` at the
     end of the parameter list. Default-null = back-compat with existing
     LOWG/LJMB JSON before this commit lands.

4. **Extend `WorldCandidateLoader`** at `WorldCandidateLoader.kt:284-337`:
   - In the `Aerodrome(...)` construction call, pass
     `ctrApproximationRadius = world.aerodrome.ctrApproximationRadiusNauticalMiles
     ?.let(Meters::fromNauticalMiles)` — i.e. `Meters?` (nullable) into
     the smart constructor. The smart constructor resolves null →
     `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`.

5. **Switch `OutsideAerodromeRadius`** at `Guard.kt:431-459`:
   - Change `data class OutsideAerodromeRadius(val thresholdMetres: Meters) : RuleGuard`
     to `data object OutsideAerodromeRadius : RuleGuard`.
   - In `evaluate()`, look up the aerodrome via
     `ctx.world.aerodromes[ctx.view.aerodromeId]` (the same lookup
     already used for the ARP proxy at line 441) and read
     `aerodrome.ctrApproximationRadius`. The defensive `?: return false`
     for ARP-not-found is preserved.
   - KDoc updated per practice-scout #4 — "Circular approximation;
     anisotropic-wrong (short on approach axis, generous abeam) since
     real CTR is polygonal per AIP. Polygon containment is the planned
     replacement (D-AUDIT-polygon-ctr)."

6. **Update both `TowerDeparture.kt` call sites** (~lines 322 and 370):
   - `OutsideAerodromeRadius(Meters.fromNauticalMiles(12))` →
     `OutsideAerodromeRadius` (no arg, since it's now a `data object`).

7. **Author JSON values:**
   - `cad/airports/rendered/lowg/world-candidate.json`: add
     `"ctrApproximationRadiusNauticalMiles": 16` to the top-level
     aerodrome block. Rationale: docs-scout found LOWG CTR polygon
     ranges 6.7–16.25 NM from ARP; max-edge (16) is the doctrinally-
     correct circular approximation ("rule says still inside CTR until
     aircraft is past every polygon edge"). Cite AIP AD 2.17 in a
     comment if the JSON format allows; otherwise document in
     `wiki/data-sources/lowg.md`.
   - `cad/airports/rendered/ljmb/world-candidate.json`: add
     `"ctrApproximationRadiusNauticalMiles": 5` (placeholder = ICAO
     floor). Docs-scout couldn't bot-fetch the LJMB eAIP; transcribing
     the polygon is deferred. Note this in `wiki/data-sources/ljmb.md`.

8. **Update `OutsideAerodromeRadiusSpec`** at
   `controller/src/commonTest/kotlin/.../bdi/OutsideAerodromeRadiusSpec.kt`:
   - The 3 existing rows (inside-ring → false, outside-ring → true,
     ARP-not-found → false) should use a test fixture aerodrome that
     authors `ctrApproximationRadius = Meters.fromNauticalMiles(12)`
     (preserves existing geometry — 22 224 m ring).
   - Drop the constructor argument from `OutsideAerodromeRadius(...)`
     calls (now `data object`).
   - Add a 4th row: aerodrome constructed with no
     `ctrApproximationRadius` (smart constructor resolves null to
     ICAO floor 5 NM). Build coords just outside the 5 NM ring; assert
     `evaluate()` returns true. Pin: smart constructor's null-resolution
     works.

9. **G2 R4 empirical re-baseline:**
   - With LOWG retuned 12 NM ⇒ 16 NM (max edge), the rule fires later
     in the flight (aircraft must travel further from ARP). The R4 gap
     pin (`>= 30_000L`) should still hold; observed at fn-6.3 close was
     374.6 s, well above 30 s.
   - Run G2; capture observed gap in `## Evidence`. If the gap drops
     below ~60 s, flag it (we may be entering brittle territory).

10. **Wiki updates** (per docs-gap-scout):
    - `wiki/data-sources/lowg.md`: add a bullet under the CTR section:
      "ctrApproximationRadius: 16 NM (max polygon edge, circular sim
      approximation; AIP AD 2.17 polygon ranges 6.7–16.25 NM from
      ARP)."
    - `wiki/data-sources/ljmb.md`: add a bullet: "ctrApproximationRadius:
      5 NM (ICAO Annex 11 §2.11.4.2 floor; polygon transcription
      deferred — see D-AUDIT register)."

11. **Other Aerodrome construction sites** (per repo-scout — 6 fixture
    sites use named-arg construction; default-null in
    `CandidateAerodrome` makes them all back-compat). Sweep:
    - `controller/src/commonTest/.../RunwayLengthGatingSpec.kt:289, 294, 365`
    - `sim/src/jvmTest/.../ReadbackCorrectionRoundTripTest.kt:78`
    - `core/src/commonTest/.../WorldConstructionTest.kt:475`
    - `pilot/src/commonTest/.../PerTypeCircuitSpec.kt:108`
    - `pilot/src/commonTest/.../TransitRoutePlanningSpec.kt:57`
    - `OutsideAerodromeRadiusSpec.kt:69`
    None should need updating if the smart constructor's null-default
    works. Verify with the test run.

## Investigation targets

**Required** (read before coding):
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:28-41`
  (`Meters` + companion `fromNauticalMiles`, no smart constructor — add
  one for `Aerodrome`).
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:319-349`
  (`Aerodrome` data class shape).
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateSchema.kt:88-115`
  (`CandidateAerodrome` shape + unit-suffixed-int convention).
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateLoader.kt:284-337`
  (loader Aerodrome construction site).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt:431-459`
  (current `OutsideAerodromeRadius` shape — post fn-6).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerDeparture.kt:300-380`
  (both call sites + surrounding rule context).
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/bdi/OutsideAerodromeRadiusSpec.kt`
  (3 existing rows that need refactor).
- `cad/airports/rendered/lowg/world-candidate.json` (top-level
  aerodrome block; add field).
- `cad/airports/rendered/ljmb/world-candidate.json` (same).

**Optional** (reference as needed):
- `data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo_ofmShapeExtension.xml`
  — may already contain LOWG polygon vertices (bypassing manual AIP
  transcription). Reference only — don't depend on it for fn-7.
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/feedback_pass_scope.md`
  — pass-scope discipline.
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/feedback_reality_anchored.md`
  — no softening on AIP truth; deferments are clean and unflinching.

## Key context

- **The 12 NM hardcode is on three counts wrong**, not just "old": (a)
  it's outside ICAO §2.11.4.2 floor on the approach side, (b) it's a
  circle approximating a polygon, (c) it's the same value for both
  LOWG and LJMB regardless of their actual AIPs. The retune addresses
  (a) and (c) within the circular-approximation envelope; (b) is
  deferred.
- Practice-scout flagged: avoid `companion object DefaultRadius` magic
  in `Aerodrome.Companion`. Single named constant
  (`Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`) with one ICAO citation, used
  only at the smart constructor.
- `OutsideAerodromeRadius` becoming `data object` means rule-equality
  changes from `data class` content equality to singleton identity. No
  consumers should care (rules are looked up by class, not value), but
  verify by running the full suite.
- Pre-existing flake `:migration:jvmTest >
  LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`
  is out of fn-7 R7 scope. Don't try to fix it.

## Acceptance

- [ ] `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM = Meters.fromNauticalMiles(5)`
      constant exists with the verbatim §2.11.4.2 KDoc + directional-
      anisotropy note.
- [ ] `Aerodrome.ctrApproximationRadius: Meters` (non-nullable) field
      exists; smart constructor resolves null source to ICAO floor.
- [ ] KDoc on `Aerodrome.ctrApproximationRadius` calls out the
      circular-approximation caveat + polygon-CTR future direction.
- [ ] `CandidateAerodrome.ctrApproximationRadiusNauticalMiles: Int?` is
      in the JSON schema (default-null = back-compat).
- [ ] `WorldCandidateLoader` threads the field through the smart
      constructor.
- [ ] `OutsideAerodromeRadius` is a `data object` (no constructor arg).
      `evaluate()` reads `ctx.world.aerodromes[ctx.view.aerodromeId]
      .ctrApproximationRadius` directly. KDoc updated.
- [ ] Both `TowerDeparture.kt` call sites construct
      `OutsideAerodromeRadius` with no argument.
- [ ] `cad/airports/rendered/lowg/world-candidate.json` authors
      `ctrApproximationRadiusNauticalMiles: 16` (or whatever value the
      implementer chose with documented rationale).
- [ ] `cad/airports/rendered/ljmb/world-candidate.json` authors
      `ctrApproximationRadiusNauticalMiles: 5` (placeholder; deferred
      to polygon transcription).
- [ ] `wiki/data-sources/lowg.md` + `wiki/data-sources/ljmb.md` updated
      with the authored radius + AIP AD 2.17 source citation.
- [ ] `OutsideAerodromeRadiusSpec` exercises the new dispatch shape
      (3 existing rows refactored + 1 new row for ICAO-floor fallback).
- [ ] All 4 spec rows pass.
- [ ] G2 `G2CrossAerodromeVfrTest` stays green; observed R4 gap captured
      in `## Evidence`. Pin `>= 30_000L` still holds.
- [ ] `LowgGoldenTest` stays green.
- [ ] Full test suite stays green; `./gradlew detekt` baseline unchanged.

## Done summary

## Evidence
