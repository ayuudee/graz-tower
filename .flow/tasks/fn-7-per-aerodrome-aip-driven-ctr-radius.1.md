---
satisfies: [R1, R2, R3, R4, R5a, R5b, R6, R7, R8, R9]
---

## Description

Single task closing fn-7. Adds a per-aerodrome
`Aerodrome.ctrApproximationRadius: Meters` field with a **primary-
constructor default** of `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`, threads
it through the JSON schema/loader (with sub-floor rejection),
switches `OutsideAerodromeRadius` to a `data object` that reads from
world data (with a rewritten static `failureMessage`), and authors
LOWG (18 NM = AIP AD 2.17 max-edge-rounded-up + ARP-proxy-offset
margin) and LJMB (18 NM conservative placeholder — Slovenia eAIP not
bot-fetchable; the 5 NM ICAO floor would be permissive-wrong for an
actual aerodrome and is intentionally NOT shipped for LJMB).

**Size:** M
**Files:**
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt`
  (`Aerodrome` data class — new field with default)
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/Doctrine.kt`
  (NEW file OR appended to `WorldModel.kt` per directory convention —
  `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` constant + edition-dated KDoc)
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateSchema.kt`
  (`CandidateAerodrome` + new `ctrApproximationRadiusNauticalMiles: Int? = null` field)
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateLoader.kt`
  (loader threading + sub-floor `require(n >= 5)` throwing-validation
  + explicit `?: Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` fallback)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt`
  (`OutsideAerodromeRadius` becomes `data object` + static
  `failureMessage` rewrite)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerDeparture.kt`
  (two call sites: drop the `Meters.fromNauticalMiles(12)` argument)
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/bdi/OutsideAerodromeRadiusSpec.kt`
  (refactor 3 existing rows + add ICAO-floor-default row)
- `migration/src/jvmTest/kotlin/.../WorldCandidateLoader*Spec.kt`
  (NEW or augment existing — add sub-floor rejection row for R8;
  investigate which file in `migration/src/jvmTest/...` is the
  canonical loader-validation host)
- `cad/airports/rendered/lowg/world-candidate.json`
  (author `ctrApproximationRadiusNauticalMiles: 18` — see Approach §7)
- `cad/airports/rendered/ljmb/world-candidate.json`
  (author `ctrApproximationRadiusNauticalMiles: 18` — conservative
  placeholder, same as LOWG; see Approach §7 for why NOT 5 NM)
- `wiki/data-sources/lowg.md`, `wiki/data-sources/ljmb.md`
  (note the authored radius; AIP AD 2.17 citation only for LOWG)
- `~/.claude/plans/pilot-firewall.md` § Deferments register
  (append D-AUDIT-* pointers per `reference_audit_registers.md`).
  `.plan` is **NOT** updated — fn-7 is flowctl-managed, not
  `.plan`-tracked.

**Files NOT edited (audit-confirmed back-compat via primary-
constructor default — verify by green test run, no edits expected):**
- `core/src/commonTest/kotlin/.../core/world/RouteAdjacentTestWorlds.kt:67`
  (`aerodrome.copy(...)` site)
- `core/src/commonTest/kotlin/.../core/world/WorldConstructionTest.kt:712, 728, 905, 948`
  (`aerodrome.copy(...)` sites)
- `core/src/commonTest/kotlin/.../core/clearance/CommunicationsJurisdictionTest.kt:376, 393`
  (`aerodrome.copy(...)` sites)
- `controller/src/commonTest/.../RunwayLengthGatingSpec.kt:289, 294, 365`,
  `sim/src/jvmTest/.../ReadbackCorrectionRoundTripTest.kt:78`,
  `core/src/commonTest/.../WorldConstructionTest.kt:475`,
  `pilot/src/commonTest/.../PerTypeCircuitSpec.kt:108`,
  `pilot/src/commonTest/.../TransitRoutePlanningSpec.kt:57`
  (`Aerodrome(...)` named-arg fixture sites — 7 of the 8 enumerated
  fixtures stay back-compat via the primary-constructor default).

**Note (per pass-5 plan-review finding #5 — clarification):**
`OutsideAerodromeRadiusSpec.kt:69` IS edited under R6 — it is the
8th `Aerodrome(...)` named-arg fixture site, but unlike the 7 listed
above it intentionally moves to authoring `ctrApproximationRadius =
Meters.fromNauticalMiles(12)` explicitly. This preserves the test's
existing 22 224 m geometry through the primary-constructor migration.
This is NOT a fixture migration to chase the new field — it's a
deliberate spec edit to keep the row's pre-fn-7 invariant intact.
Listed in the **edited files** section above, not here.

## Approach

1. **Add `Doctrine.IcaoAnnex11`** with TWO related constants (per
   pass-4 plan-review finding #2 — eliminate numeric-drift between
   the metres value and the schema-unit validation):
   - `const val CTR_FLOOR_NAUTICAL_MILES: Int = 5` — source numeric
     value, used at the loader's `require(n >= ...)` and in the
     loader error message.
   - `val CTR_FLOOR_5NM: Meters = Meters.fromNauticalMiles(CTR_FLOOR_NAUTICAL_MILES)`
     — runtime metres value, used at the primary-constructor default
     and at the loader's `?: ...` fallback. Derived from the int
     constant — `5` lives in exactly one place.

   Location: `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/`.
   Match existing conventions for the directory: if `WorldModel.kt`
   already hosts companion-style doctrine constants, append;
   otherwise create sibling `Doctrine.kt`.
   - **KDoc citation (per plan-review finding #5; pass-2 finding #2):**
     *"ICAO Annex 11 — Air Traffic Services, 15th edition, July 2018,
     §2.11 (control zone lateral limits)."* No verbatim quotation —
     `research/txt/` does NOT contain Annex 11 (`icao9432*` is Doc
     9432, the Manual of Radiotelephony, a different document); and
     the project's existing Annex 11 references at
     `controller/.../AtisLetterMismatchAdvisorySpec.kt:26` and
     `ControllerTypes.kt:120, 163` use short-cite-no-verbatim.
     Match that convention.
   - **Paraphrase only** (in implementer's own words, KDoc body):
     *"Lateral limits of the control zone shall extend at least 5 NM
     (9.3 km) from the aerodrome reference point in the directions
     from which approaches may be made."* Followed by the directional-
     vs-polygonal note (per pass-8 plan-review finding #4):
     *"This is a directional **minimum**, not a polygon shape — a
     5 NM circle meets the minimum along every axis. Real CTR
     polygons authored from AIP often extend beyond the 5 NM floor
     on the approach axis (so a 5 NM circle is too small there
     relative to the actual published polygon). The 5 NM circular
     value here is a regulatory floor, not a polygon approximation.
     Polygon containment supersedes; see `D-AUDIT-polygon-ctr`."*

2. **Extend `Aerodrome` data class** at `WorldModel.kt:319-349`:
   - Add `val ctrApproximationRadius: Meters =
     Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` at the **end** of the
     parameter list. Default keeps fixture-site primary-constructor
     calls and `.copy(...)` sites back-compat without modification.
   - Add KDoc on the new field: *"Circular sim approximation. Real CTR
     is polygonal per AIP AD 2.17; this is a single-radius stand-in
     for the runtime guard. Polygon containment is FM/Lean territory
     (`fn-4` lineage); see `D-AUDIT-polygon-ctr` deferment."*
   - **Do NOT add a companion-factory `fromCandidate(...)`.** That
     shape was rejected by plan-review finding #1 — it doesn't help
     existing fixture-site primary-constructor calls or `.copy(...)`
     sites, which would all need migration.

3. **Extend `CandidateAerodrome` JSON schema** at
   `migration/.../WorldCandidateSchema.kt:88-115`:
   - Add `val ctrApproximationRadiusNauticalMiles: Int? = null` at the
     end of the parameter list. Default-null = back-compat with
     existing LOWG/LJMB JSON before this commit lands.
   - Schema field stays `Int?` (no `Double` overload). Sub-NM
     precision is irrelevant against the 6.7–16.25 NM polygon spread;
     authoring-side rounds **up** (LOWG: 18, see §7).

4. **Extend `WorldCandidateLoader`** at `WorldCandidateLoader.kt:284-337`:
   - Read `world.aerodrome.ctrApproximationRadiusNauticalMiles`.
   - **Sub-floor validation (R8) — `require` throwing pattern.** The
     loader uses `require(condition) { message }` and `error("...")`
     for unsupported variants throughout (verified at lines 140, 363,
     371, 446, 450, 477). It does **not** use `Either.Left` /
     typed-validation-failure. Match the existing pattern; do not
     introduce a new validation API for this single field. Insert a
     guard immediately before the `Aerodrome(...)` construction:
     ```kotlin
     val authoredRadiusNm = world.aerodrome.ctrApproximationRadiusNauticalMiles
     if (authoredRadiusNm != null) {
         require(authoredRadiusNm >= Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES) {
             "ctrApproximationRadiusNauticalMiles must be >= " +
             "${Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES} NM " +
             "(ICAO Annex 11 §2.11 floor): got $authoredRadiusNm"
         }
     }
     ```
   - In the `Aerodrome(...)` construction call, pass:
     ```kotlin
     ctrApproximationRadius =
         authoredRadiusNm
             ?.let(Meters::fromNauticalMiles)
             ?: Doctrine.IcaoAnnex11.CTR_FLOOR_5NM
     ```
     Explicit fallback (not relying on the constructor default) so the
     loader chokepoint cites the doctrine.

5. **Switch `OutsideAerodromeRadius`** at `Guard.kt:431-459`:
   - Change `data class OutsideAerodromeRadius(val thresholdMetres: Meters) : RuleGuard`
     to `data object OutsideAerodromeRadius : RuleGuard`.
   - **Rewrite `failureMessage` (per plan-review finding #2):** the
     current value `"Aircraft within ${thresholdMetres.value}m radial
     of aerodrome (still in CTR scope)"` interpolates the removed
     constructor field. Replace with the static string:
     `"Aircraft within aerodrome CTR approximation radius (still in
     CTR scope)"`. Per-aerodrome variance is no longer a concern at
     the rule level; the value lives on the aerodrome.
   - In `evaluate()`, look up the aerodrome via
     `ctx.world.aerodromes[ctx.view.aerodromeId]` (the same lookup
     already used for the ARP proxy at line 441) and read
     `aerodrome.ctrApproximationRadius`. The defensive `?: return false`
     for ARP-not-found is preserved.
   - KDoc updated per practice-scout #4: *"Circular approximation;
     anisotropic-wrong (short on approach axis, generous abeam) since
     real CTR is polygonal per AIP. Polygon containment is the planned
     replacement (D-AUDIT-polygon-ctr)."*

6. **Update both `TowerDeparture.kt` call sites** (~lines 322 and 370):
   - `OutsideAerodromeRadius(Meters.fromNauticalMiles(12))` →
     `OutsideAerodromeRadius` (no arg, since it's now a `data object`).

7. **Author JSON values.** Hand-edit the rendered JSON files —
   per `project_world_building.md` (memory) and
   `cad/airports/lowg-authoring.md` line 41,163, `world-candidate.json`
   under `cad/airports/rendered/<airport>/` is the canonical worked-
   current-core hand-authored artifact for this project. There is no
   automated CI regenerator that would clobber the edit. Pass-4 plan-
   review finding #4 acknowledged.
   - `cad/airports/rendered/lowg/world-candidate.json`: add
     `"ctrApproximationRadiusNauticalMiles": 18` to the top-level
     aerodrome block.
     **Rationale (per plan-review finding #3):** docs-scout found LOWG
     CTR polygon ranges 6.7–16.25 NM from ARP. Max-edge rounded
     **up** (16.25 → 18, including ~1 NM proxy-offset margin) is the doctrinally-correct circular
     approximation: "rule says still inside CTR until aircraft is
     past every polygon edge". Authoring `16` would round *down* and
     release inside the 16.25 NM polygon edge — that violates the
     doctrine. Document the rounding-up choice in
     `wiki/data-sources/lowg.md`.
   - `cad/airports/rendered/ljmb/world-candidate.json`: add
     `"ctrApproximationRadiusNauticalMiles": 18` — conservative
     placeholder, same as LOWG. **NOT the 5 NM ICAO floor** (per
     pass-5 plan-review finding #2): LJMB's real polygon is almost
     certainly larger than 5 NM and a 5 NM ring would release inside
     real CTR (permissive-wrong / regulatorily-bad). 18 NM holds
     traffic too long instead, which is conservative-wrong /
     regulatorily-safe — under-fires the release rather than over-
     fires. Real LJMB CTR size is unknown to fn-7 (no LJMB AIP
     citation transcribed); the 18 NM value is reused from LOWG as
     a conservative bound, not derived from regional-CTR
     comparisons. Docs-
     scout couldn't bot-fetch the LJMB eAIP (Slovenia 403/404);
     real polygon transcription deferred as `D-AUDIT-ljmb-polygon`.
     Document in `wiki/data-sources/ljmb.md` with the AIP-deferral
     callout.

8. **Update `OutsideAerodromeRadiusSpec`** at
   `controller/src/commonTest/kotlin/.../bdi/OutsideAerodromeRadiusSpec.kt`:
   - The 3 existing rows (inside-ring → false, outside-ring → true,
     ARP-not-found → false) should use a test fixture aerodrome that
     authors `ctrApproximationRadius = Meters.fromNauticalMiles(12)`
     **explicitly** (preserves existing geometry — 22 224 m ring).
   - Drop the constructor argument from `OutsideAerodromeRadius(...)`
     calls (now `data object`).
   - **Add row 4 (ICAO-floor default):** aerodrome constructed with no
     `ctrApproximationRadius` arg (primary-constructor default
     resolves to 5 NM). Build coords just outside the 5 NM ring;
     assert `evaluate()` returns true. Pin: primary-constructor
     default works.

9a. **Add loader sub-floor rejection test (R8)** at
   `migration/src/jvmTest/kotlin/.../WorldCandidateLoader*Spec.kt`
   (host file: investigate which existing loader-validation spec is
   the right home — likely a sibling of
   `LjmbWorldCandidateValidationTest`):
   - Author a candidate JSON (or in-test fixture) with
     `ctrApproximationRadiusNauticalMiles: 4`.
   - Assert load throws `IllegalArgumentException` (or whatever
     exception type Kotlin's `require {...}` raises in the
     surrounding test conventions — `assertFailsWith` /
     `shouldThrow` per the spec file's existing assertion style).
     Assert the message contains `"5 NM"` and `"floor"`.
   - Pin: sub-floor JSON authoring is rejected, not silently coerced.

9b. **Add real-airport authoring guardrail (R9)** — fold-in of pass-7
   finding #3 + pass-8 finding #2. Add a focused test
   `RenderedAirportRadiusAuthoringTest` (or augment an existing
   candidate-validation spec) at `migration/src/jvmTest/.../`:
   - **Exact-value allowlist (load-bearing):**
     ```kotlin
     val expected = mapOf(
         "LOWG" to 18,
         "LJMB" to 18,
     )
     ```
     For every airport in `expected`, assert the JSON authors
     exactly that integer. A future change to either value forces a
     deliberate test-update + plan-review (catches accidental
     regressions to 5, typos to 180, etc.).
   - **Future-airport guard (iteration):** scan
     `cad/airports/rendered/<icao>/world-candidate.json`. Any
     `<icao>` NOT in the `expected` map must (a) have a non-null
     `ctrApproximationRadiusNauticalMiles` and (b) cause the test to
     fail with a directive: *"New rendered airport `$icao` is not in
     the R9 allowlist. Add an entry to
     `RenderedAirportRadiusAuthoringTest.expected` with the
     deliberate per-airport value (review fn-7 epic spec §
     Decision context for the LOWG / LJMB authoring precedent)."*
   - Pin: catches both "missing field" (silent 5 NM fallback) and
     "wrong value" (nonsensical or stale) failure modes; forces a
     deliberate review for every new rendered airport.

10. **G2 R4 empirical re-baseline:**
    - With LOWG retuned 12 NM ⇒ 18 NM (max-edge-rounded-up), the rule
      fires later in the flight (aircraft must travel further from
      ARP). The R4 gap pin (`>= 30_000L`) should still hold; observed
      at fn-6.3 close was 374.6 s, well above 30 s. Back-of-envelope:
      5 NM extra at ~62 m/s ≈ +149 s firing-delay; expected gap
      ≈ 225 s at 18 NM.
    - Run G2; capture observed gap in `## Evidence`. If the gap drops
      below ~60 s, flag it (we may be entering brittle territory).

11. **Wiki updates** (per docs-gap-scout):
    - `wiki/data-sources/lowg.md`: add a bullet under the CTR section
      with concrete AIP citation (per pass-7 plan-review finding #4):
      *"ctrApproximationRadius: 18 NM (max polygon edge 16.25 NM
      rounded UP + ~1 NM ARP-proxy-offset margin, circular sim
      approximation). Source: AIP Austria, AD 2 LOWG §2.17 (control
      zone lateral limits), effective 2026-04-01 (AIRAC 2604), via
      Austro Control / OpenFlightMaps Austria mirror at
      `data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo.ofmx`.
      Polygon ranges 6.7–16.25 NM from ARP. Rounding up + proxy
      margin keeps the rule from releasing inside the polygon under
      worst-case threshold-proxy offset."*
    - `wiki/data-sources/ljmb.md`: add a bullet:
      *"ctrApproximationRadius: 18 NM (conservative placeholder =
      same as LOWG; LJMB Slovenia eAIP not bot-fetchable, real polygon
      transcription deferred. 18 NM under-fires the release rule
      (controller holds traffic too long) which is regulatorily-safe;
      the 5 NM ICAO Annex 11 §2.11 floor would over-fire (release
      inside real CTR) and is intentionally NOT used for LJMB. See
      `D-AUDIT-ljmb-polygon` in fn-7 epic spec for the polygon-
      transcription deferment."*

12. **Append D-AUDIT-* pointers to user audit register** (fold-in of
    pass-5 finding #4 + pass-6 finding #4 + pass-7 finding #6).
    Single venue: `~/.claude/plans/pilot-firewall.md` § Deferments
    register, per the user's persistent memory
    `reference_audit_registers.md` ("D-AUDIT items live in
    pilot-firewall.md, not in the project repo"). Append one-line
    pointers:
    - `D-AUDIT-arp-proxy-runtime` — guard centres on threshold proxy,
      not ARP. Body in fn-7 spec § Deferments register.
    - `D-AUDIT-polygon-ctr` — circular-radius approximation
      superseded by polygon containment (fn-4 lineage). Body in fn-7
      spec.
    - `D-AUDIT-airac-cycle-tracking` — typed `AiracCycle` field.
      Body in fn-7 spec.
    - `D-AUDIT-ljmb-polygon` — LJMB CTR polygon transcription
      (Slovenia eAIP not bot-fetchable). Body in fn-7 spec.

    Closure cross-reference (in `pilot-firewall.md`):
    - `D-AUDIT-lowg-ctr-radius` — note "closed by fn-7" if the entry
      is also tracked there (search for it; if not present, the
      closure note in fn-6's spec is sufficient).

    **NOT updated** (per pass-7 plan-review finding #6): the project
    repo's `.plan` was never tracking fn-7 (epic management moved
    to flowctl). Adding a "DONE" entry retroactively would be
    misleading. fn-7's epic-state lives in `.flow/epics/`; its
    deferments live in `pilot-firewall.md` per user memory.

13. **`Aerodrome.copy(...)` audit** (per plan-review finding #9). Sites:
    - `core/src/commonTest/kotlin/.../core/world/RouteAdjacentTestWorlds.kt:67`
    - `core/src/commonTest/kotlin/.../core/world/WorldConstructionTest.kt:712, 728, 905, 948`
    - `core/src/commonTest/kotlin/.../core/clearance/CommunicationsJurisdictionTest.kt:376, 393`

    With the primary-constructor default, all `.copy(...)` sites preserve
    the unchanged `ctrApproximationRadius` field; no edits expected.
    Verify by green test run.

## Investigation targets

**Required** (read before coding):
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:28-41`
  (`Meters` + companion `fromNauticalMiles(Int)`).
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt:319-349`
  (`Aerodrome` data class shape).
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateSchema.kt:88-115`
  (`CandidateAerodrome` shape + unit-suffixed-int convention).
- `migration/src/commonMain/kotlin/xyz/easiersaid/twr/migration/world/WorldCandidateLoader.kt:284-337`
  (loader Aerodrome construction site + surrounding scalar-bounds-
  check via throwing `require(...) { msg }` — see lines 140, 363,
  371, 446, 450, 477 for the convention).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Guard.kt:431-459`
  (current `OutsideAerodromeRadius` shape — post fn-6).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerDeparture.kt:300-380`
  (both call sites + surrounding rule context).
- `controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/bdi/OutsideAerodromeRadiusSpec.kt`
  (3 existing rows that need refactor).
- Existing loader-validation spec under `migration/src/jvmTest/...`
  (host for the sub-floor rejection test — find the right neighbor).
- `cad/airports/rendered/lowg/world-candidate.json` (top-level
  aerodrome block; add field).
- `cad/airports/rendered/ljmb/world-candidate.json` (same).

**Optional** (reference as needed):
- `data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo_ofmShapeExtension.xml`
  — may already contain LOWG polygon vertices (bypassing manual AIP
  transcription). Reference only — don't depend on it for fn-7.
- `controller/.../AtisLetterMismatchAdvisorySpec.kt:26` and
  `controller/.../ControllerTypes.kt:120, 163` — existing
  short-cite-no-verbatim Annex 11 references, for matching the
  citation style on the new doctrine constant. (`research/txt/`
  contains ICAO Doc 9432 — the Manual of Radiotelephony — NOT
  Annex 11; do not point to it as an Annex 11 source.)
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/feedback_pass_scope.md`
  — pass-scope discipline.
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/feedback_reality_anchored.md`
  — no softening on AIP truth; deferments are clean and unflinching.
- `~/.claude/projects/-home-andrew-dev-projects-twr2/memory/feedback_plans_review_aware.md`
  — every plan addresses FP/test/impact/ops review axes inline.

## Key context

- **The 12 NM hardcode is on three counts wrong**, not just "old": (a)
  it's outside ICAO §2.11 floor on the approach side, (b) it's a
  circle approximating a polygon, (c) it's the same value for both
  LOWG and LJMB regardless of their actual AIPs. The retune addresses
  (a) and (c) within the circular-approximation envelope; (b) is
  deferred (`D-AUDIT-polygon-ctr`).
- **18 NM, not 16, for LOWG.** The 16.25 NM polygon max-edge rounds
  **up** PLUS adds a proxy-offset margin (the guard centres the ring
  on the lexicographically-first runway threshold, not the ARP, so
  ~1 NM proxy-offset budget is added to the polygon max). Authoring 16 would round *down* and release inside
  the polygon — directly violates "still in CTR until past every
  edge" doctrine. Schema-`Int?` rounding-direction matters.
- **Practice-scout flagged:** avoid `companion object DefaultRadius`
  magic in `Aerodrome.Companion`. Single named constant
  (`Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`) with one ICAO citation, used
  at the primary-constructor default and at the loader fallback
  (same constant, two sites).
- `OutsideAerodromeRadius` becoming `data object` means rule-equality
  changes from `data class` content equality to singleton identity.
  No consumers should care (rules are looked up by class, not value),
  but verify by running the full suite. The `failureMessage` rewrite
  is also load-bearing: today's interpolation `${thresholdMetres.value}`
  references a constructor field that no longer exists; static
  message replaces it.
- Pre-existing flake `:migration:jvmTest >
  LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`
  is out of fn-7 R7 scope. Don't try to fix it.

## Acceptance

- [ ] `Doctrine.IcaoAnnex11` exposes both
      `const val CTR_FLOOR_NAUTICAL_MILES: Int = 5` (source numeric)
      and `val CTR_FLOOR_5NM: Meters =
      Meters.fromNauticalMiles(CTR_FLOOR_NAUTICAL_MILES)` (derived
      metres). The numeric `5` lives in exactly one place. KDoc
      includes edition-dated citation (ICAO Annex 11, 15th ed., July
      2018, §2.11) **plus paraphrase only** — no verbatim quotation —
      and the directional-anisotropy note.
- [ ] `Aerodrome.ctrApproximationRadius: Meters` field exists with
      primary-constructor default `= Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`.
      No companion-factory `fromCandidate(...)` introduced.
- [ ] KDoc on `Aerodrome.ctrApproximationRadius` calls out the
      circular-approximation caveat + polygon-CTR future direction.
- [ ] `CandidateAerodrome.ctrApproximationRadiusNauticalMiles: Int?` is
      in the JSON schema (default-null = back-compat).
- [ ] `WorldCandidateLoader` threads the field with explicit
      `?: Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` fallback at the loader
      site **and** rejects authored values
      `< Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES` via
      `require(n >= Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES)`
      (matching the loader's existing throwing-validation pattern;
      not `Either.Left`; no inline `5` literal in the loader).
      Message cites the constant value (interpolated, not hardcoded).
- [ ] `OutsideAerodromeRadius` is a `data object` (no constructor arg).
      `failureMessage` is the static string `"Aircraft within
      aerodrome CTR approximation radius (still in CTR scope)"`.
      `evaluate()` reads `ctx.world.aerodromes[ctx.view.aerodromeId]
      .ctrApproximationRadius` directly. KDoc updated.
- [ ] Both `TowerDeparture.kt` call sites construct
      `OutsideAerodromeRadius` with no argument.
- [ ] `cad/airports/rendered/lowg/world-candidate.json` authors
      `ctrApproximationRadiusNauticalMiles: 18` (max polygon edge
      16.25 NM rounded UP).
- [ ] `cad/airports/rendered/ljmb/world-candidate.json` authors
      `ctrApproximationRadiusNauticalMiles: 18` (conservative
      placeholder = same as LOWG; the 5 NM ICAO floor would be
      permissive-wrong for LJMB and is NOT shipped). Polygon
      transcription deferred as `D-AUDIT-ljmb-polygon`.
- [ ] `wiki/data-sources/lowg.md` updated: authored 18 NM + AIP AD 2.17
      source citation + max-edge-rounded-up rationale + ARP-proxy-
      offset margin note.
- [ ] `wiki/data-sources/ljmb.md` updated: authored 18 NM as
      conservative placeholder + Slovenia eAIP not bot-fetchable
      callout + `D-AUDIT-ljmb-polygon` reference. NO claim of
      AIP AD 2.17 source citation for LJMB (since none was
      transcribed); explicit "ICAO 5 NM floor would be permissive-
      wrong, not used" note.
- [ ] `OutsideAerodromeRadiusSpec` exercises the new dispatch shape
      (3 existing rows refactored to drop constructor arg + 1 new row
      pinning the primary-constructor default at 5 NM).
- [ ] Loader sub-floor rejection test (R8) authored at the right
      `migration/src/jvmTest/...` host; passes.
- [ ] Real-airport authoring guardrail (R9) — focused test:
      LOWG = 18 (exact assertion). LJMB = 18 (exact assertion).
      Any other rendered airport not in the allowlist forces a
      deliberate test-update + plan-review (per pass-8 plan-review
      finding #2; non-null alone was too weak — would let stale
      `5` or nonsensical `180` slip through).
- [ ] `Aerodrome.copy(...)` audit verified by test run — none of the
      7 sites needed editing.
- [ ] All spec rows pass.
- [ ] G2 `G2CrossAerodromeVfrTest` stays green; observed R4 gap captured
      in `## Evidence`. Pin `>= 30_000L` still holds.
- [ ] `LowgGoldenTest` stays green.
- [ ] **Verification set (identical to epic R7) — three separate
      commands** (single combined gradle invocation short-circuits on
      the first failing test class; epic R7 explicitly requires the
      split):
      1. **Targeted non-migration suites + detekt (must exit 0):**
         `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest
         :core:jvmTest :protocol:jvmTest detekt`. All pass.
      2. **Migration with `--continue`:**
         `./gradlew :migration:jvmTest --continue`. Every test passes
         EXCEPT exactly:
         `xyz.easiersaid.twr.migration.world.LjmbWorldCandidateValidationTest >
         writesLjmbCurrentCoreValidationReport()`.
         No other failing tests. Capture the failure block verbatim
         into `## Evidence`, labeled "pre-fn-7 flake; out of fn-7
         scope". The acceptance criterion is **exact test-method
         identifier match** — no subjective "unchanged from baseline"
         judgement.
      3. **G2 R4 empirical pin:**
         `./gradlew :sim:jvmTest --tests "xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest"`
         passes. R4 gap `>= 30_000L`; observed gap captured in
         `## Evidence`.

## Done summary

Replaces the magic `Meters.fromNauticalMiles(12)` at TowerDeparture.kt's
two `OutsideAerodromeRadius` call sites with a per-aerodrome
`Aerodrome.ctrApproximationRadius: Meters` field, authored from AIP
data where available (LOWG: 18 NM = AIP AD 2.17 polygon max-edge
16.25 NM rounded UP + ~1 NM ARP-proxy-offset margin) and a conservative
placeholder otherwise (LJMB: 18 NM, Slovenia eAIP not bot-fetchable).
New `Doctrine.IcaoAnnex11.{CTR_FLOOR_NAUTICAL_MILES, CTR_FLOOR_5NM}`
constants centralise the ICAO floor (numeric `5` lives in exactly one
place); the `OutsideAerodromeRadius` rule is now a `data object` reading
from world data; the JSON schema gains
`ctrApproximationRadiusNauticalMiles: Int? = null` (back-compat by
default-null); the loader rejects sub-floor authoring via `require(...)`
matching the existing throwing-validation pattern. Closes
`D-AUDIT-lowg-ctr-radius`; opens `D-AUDIT-arp-proxy-runtime`,
`D-AUDIT-polygon-ctr`, `D-AUDIT-airac-cycle-tracking`, and
`D-AUDIT-ljmb-polygon` in `~/.claude/plans/pilot-firewall.md` §
Deferments register.

## Evidence

**Implementation commit:** `4e362a1` —
`fn-7-per-aerodrome-aip-driven-ctr-radius.1: per-aerodrome ctrApproximationRadius`.

**R7 verification set (three separate gradle invocations, per the spec):**

1. **Targeted non-migration suites — `:sim:jvmTest :pilot:jvmTest
   :controller:jvmTest :core:jvmTest :protocol:jvmTest --rerun-tasks`:**
   `BUILD SUCCESSFUL`. All five test tasks green on a forced rerun.

   `detekt` is a known-pre-fn-7 baseline failure (10 weighted issues,
   all in files fn-7 did not touch in their reported lines:
   `CoordinationEscalation.kt:59`, `WorldCandidateLoader.kt:102`,
   `Step.kt:846/997/283/378`, `Guard.kt:611` — the `classify` function
   not the new `OutsideAerodromeRadius` data object,
   `PilotCognitive.kt:540/542/924`). Verified by running detekt on the
   pre-fn-7 baseline `f52313c`: identical 10 issues, identical files,
   identical line numbers (the `Guard.kt` violation just shifts from
   line 599 to 611 because fn-7 added KDoc lines above the unchanged
   `classify`). The R7 acceptance criterion "detekt (must exit 0)" is
   not satisfiable from the pre-fn-7 baseline; recording as a baseline-
   debt observation, not a fn-7 regression.

2. **Migration with `--continue` — `:migration:jvmTest --continue
   --rerun-tasks`:** `80 tests completed, 1 failed`. The single
   failure is exactly the spec-named pre-fn-7 flake:

   ```
   LjmbWorldCandidateValidationTest[jvm] > writesLjmbCurrentCoreValidationReport()[jvm] FAILED
       org.opentest4j.AssertionFailedError at LjmbWorldCandidateValidationTest.kt:264

   org.opentest4j.AssertionFailedError: LJMB runtime SID subset should
   project the 9 X-Plane CIFP SIDs whose leg models are
   waypoint-representable; the remaining -1J/-1N/-2G/-3H SIDs carry
   intermediate fixless VI legs and remain in the IFR inventory only.
   ==> expected:
       <[LJMB_SID_DIML1S_14, LJMB_SID_GOLV1S_14, LJMB_SID_GOLV2G_14,
         LJMB_SID_MURE1S_14, LJMB_SID_PETO1S_14, LJMB_SID_PETO2B_14,
         LJMB_SID_PETO5D_32, LJMB_SID_VALU1S_14, LJMB_SID_VALU4L_32]>
   but was:
       <[LJMB_SID_GOLV2G_14, LJMB_SID_PETO2B_14, LJMB_SID_PETO5D_32,
         LJMB_SID_VALU1S_14, LJMB_SID_VALU4L_32]>
       at .../LjmbWorldCandidateValidationTest.assertExpectedLjmbIfrSids(LjmbWorldCandidateValidationTest.kt:264)
       at .../LjmbWorldCandidateValidationTest.assertExpectedLjmbCurrentCoreSubset(LjmbWorldCandidateValidationTest.kt:227)
       at .../LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport(LjmbWorldCandidateValidationTest.kt:123)
   ```

   Pre-fn-7 flake; out of fn-7 scope (LJMB SID-subset assertion,
   nothing to do with CTR-radius work). No other failing tests in
   `:migration:jvmTest`.

3. **G2 R4 empirical pin — `:sim:jvmTest --tests
   "xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest" --rerun-tasks`:**
   `BUILD SUCCESSFUL`. **Observed gap = 96 560 ms ≈ 96.6 s** between
   the last LOWG instruction and the first LJMB transmission (captured
   by temporarily raising the floor to trip the failure message; reverted
   immediately after — verified clean working tree on
   `G2CrossAerodromeVfrTest.kt`). Well above the `>= 30_000L` (30 s)
   floor; pin holds.

   Note: this is **lower than the fn-6.3 close observation of 374.6 s**.
   The drop reflects the CTR-radius retune working as intended: at
   12 NM the rule fired earlier (kinematic crossing well before the
   pilot's natural LJMB-contact point, producing a 374.6 s release-to-
   contact gap); at 18 NM the rule fires later (the aircraft must
   travel further from ARP before release), shrinking the gap toward
   the natural LJMB-contact moment. Spec §10 predicted ~225 s back-of-
   envelope (5 NM × 62 m/s ≈ +149 s firing-delay applied to 374.6 s
   yielding ~225 s); observed 96.6 s is shorter still, plausibly
   because the natural pilot-contact moment already trailed the
   release moment by less than the full geometric delta. Gap remains
   ~3.2× above the 30 s floor — comfortable margin, no brittle-
   territory flag.

**R8/R9 focused tests:**
- `:migration:jvmTest --tests
  "xyz.easiersaid.twr.migration.world.CtrApproximationRadiusLoaderTest"`:
  `BUILD SUCCESSFUL`. Both R8 (sub-floor rejection on authored 4 NM
  with message containing `"5 NM"` + `"§2.11"`) and R9 (real-airport
  authoring guardrail: LOWG=18, LJMB=18 exact-value allowlist + new-
  airport iteration directive) green.
- `:controller:jvmTest --tests
  "xyz.easiersaid.twr.controller.bdi.OutsideAerodromeRadiusSpec"`:
  `BUILD SUCCESSFUL`. All 4 rows pass (3 existing rows preserved at
  22 224 m via explicit `Meters.fromNauticalMiles(12)` fixture
  authoring + 1 new row pinning the primary-constructor default at
  5 NM = `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`).
- `:sim:jvmTest --tests "xyz.easiersaid.twr.sim.LowgGoldenTest"`:
  `BUILD SUCCESSFUL`. Golden stays green at LOWG-retuned-18 NM.

**Aerodrome.copy(...) audit (per spec §13):** all 7 enumerated
`.copy(...)` sites (RouteAdjacentTestWorlds.kt:67;
WorldConstructionTest.kt:712, 728, 905, 948;
CommunicationsJurisdictionTest.kt:376, 393) plus the 7 `Aerodrome(...)`
named-arg fixture sites stayed back-compat through the primary-
constructor default with no edits — verified by green run of every
suite that consumes them.
