# Per-aerodrome AIP-driven CTR-radius retune

## Overview

Replace the magic `Meters.fromNauticalMiles(12)` at `TowerDeparture.kt`'s
two `OutsideAerodromeRadius` call sites with a per-aerodrome
`Aerodrome.ctrApproximationRadius: Meters` field, authored from
**AIP AD 2.17 polygon data where available** (LOWG) and a
**conservative placeholder otherwise** (LJMB — Slovenia eAIP could
not be bot-fetched). The runtime field is non-nullable with a
**primary-constructor default** of `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`
(= `Meters.fromNauticalMiles(CTR_FLOOR_NAUTICAL_MILES)`), and the
loader threads JSON-authored values through, falling back to that
same constant when JSON-source is null. This shape preserves back-
compat for the 8 existing fixture-site named-arg constructors
(enumerated in the task spec's "Files NOT edited" section) and the
7 `Aerodrome.copy(…)` sites without forcing a migration. The 8th
fixture (`OutsideAerodromeRadiusSpec.kt:69`) IS edited under R6 to
preserve its 12 NM test geometry — that's an intentional spec edit,
not a fixture-migration. Closes the `D-AUDIT-lowg-ctr-radius`
deferment register entry filed in `fn-6`'s spec; LJMB-specific
polygon transcription remains open as `D-AUDIT-ljmb-polygon`.

**Title framing (per pass-5 plan-review finding #3):** the epic ID
"per-aerodrome AIP-driven CTR-radius" is preserved (renaming the
epic ID would cascade across `.flow/` artifacts), but the scope is
"AIP-driven where AIP data is reachable; conservative permissive-
side-of-strict placeholder otherwise". LJMB authoring is not
AIP-driven in fn-7 and the spec does not claim it is.

**Important reality check (docs-scout finding):** real CTRs are polygons,
not circles. LOWG CTR per AIP Austria AD 2 LOWG §2.17 ranges
**6.7 NM** (W edge) to **16.25 NM** (S edge) from ARP — the 12 NM
hardcode is inaccurate-high on three edges and inaccurate-low on two.
The user's "~7 NM" framing is one (short) edge of the polygon, not a
mean. Polygon containment is the canonical fix; this epic picks a
defensible single scalar per aerodrome and surfaces the approximation
in KDoc + filed deferments.

**Concrete LOWG AIP source citation (per pass-7 plan-review finding
#4):** the polygon vertices and resulting min/max-edge distances
above are derived from:
- **Document**: AIP Austria, AD 2 LOWG §2.17 (Air Traffic Services
  Airspace — control zone lateral limits).
- **Provider**: Austro Control (Austrian ANSP); upstream of the
  OpenFlightMaps Austria mirror used by this project.
- **Local artifact**: `data/ofm/austria/ofmx_extracted/ofmx_lo/isolated/ofmx_lo.ofmx`
  (OFMX format snapshot bundled in repo).
- **Effective date** (per the OFMX snapshot's `effective=` attribute):
  `2026-04-01T22:49:10Z` → AIRAC cycle **2604** (April 2026 cycle).
- **Wiki capture**: `wiki/data-sources/lowg.md` records the citation
  and the polygon-edge derivation at commit time, so the AIP source
  trail is reproducible from the rendered candidate JSON back to
  the OFMX bundle and AIRAC cycle.

## Quick commands

```bash
# Build + run the load-bearing tests
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest --tests "xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest" \
                          --tests "xyz.easiersaid.twr.sim.LowgGoldenTest" \
                          --console=plain

nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :controller:jvmTest \
    --tests "xyz.easiersaid.twr.controller.bdi.OutsideAerodromeRadiusSpec" \
    --console=plain

# Loader sub-floor rejection coverage (R8)
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :migration:jvmTest --console=plain

# Detekt baseline preserved (also part of R7 verification set)
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew detekt --console=plain

# R7 verification set (identical at epic + task acceptance level)
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest :protocol:jvmTest :migration:jvmTest detekt --console=plain
```

## Boundaries / non-goals

- **Out: polygon containment.** Real CTRs are polygons; this epic stays
  on the circular-radius approximation. Polygon containment is FM/Lean
  campaign territory (`fn-4` lineage). Filed as `D-AUDIT-polygon-ctr` in
  the deferments register.
- **Out: AIRAC cycle tracking.** Practice-scout flagged `airacCycle:
  AiracCycle` as a typed staleness-check field. Real CTR boundaries
  change rarely (multi-year cadence per AIRAC research); not blocking.
  Filed as `D-AUDIT-airac-cycle-tracking`.
- **Out: vertical limits / airspace class on `Aerodrome`.** Docs-scout
  flagged the full `CtrLateralLimits` shape as the doctrinally-correct
  authoring (polygon + lower/upper FT + class + AIRAC). Out of scope —
  this epic adds only the scalar radius. Future polygon work supersedes.
- **Out: parsing `data/ofm/.../ofmShapeExtension.xml`** for polygon
  extraction. Per `project_world_building.md`, worlds are hand-authored;
  the OFM data is a reference, not a pipeline input.
- **Out: `Double` overload of `Meters.fromNauticalMiles`.** The schema
  field is `Int?`. Sub-NM precision is irrelevant against the
  6.7–16.25 NM polygon spread; we always round **up** (see Decision
  context, "Authoring-value choice for LOWG").

## Decision context

**Three architectural choices on the table:**

1. **Primary-constructor default on `Aerodrome` resolves to ICAO floor**
   *(this epic — selected; fold-in of plan-review finding #1)*. Field
   is non-nullable in the runtime type with a default value of
   `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`. Single chokepoint; guard reads
   non-null `Meters` directly. **Companion-factory `fromCandidate(...)`
   variant rejected** because it doesn't help the 8 existing fixture-
   site primary-constructor calls or the 7 `Aerodrome.copy(...)` sites
   — those would all need migration. Default-on-primary-constructor +
   loader `?: CTR_FLOOR_5NM` is the minimal, back-compat shape.
2. Nullable field at runtime, guard does `?: ICAO_FLOOR` (rejected:
   doctrine becomes invisible in TowerDeparture diffs; breaks across
   future call sites).
3. Constructor argument on `OutsideAerodromeRadius` (today's shape) with
   a per-aerodrome default — kept for override (rejected: stale-config
   surface; the rule shouldn't carry a value when the aerodrome already
   has it).

Selected option mirrors `fn-6`'s "decision: `coords` is non-nullable"
pattern. ICAO floor lives in a single named constant
(`Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`) cited at the primary-constructor
default and at the loader fallback — same constant in both places, no
duplication of the actual numeric value.

**Authoring-value choice for LOWG:** the polygon ranges 6.7–16.25 NM.
Three doctrine flavours:
- **Conservative (min ~7 NM)**: rule fires earliest; aircraft transits
  Class G longest. Risk: rule fires while aircraft is still inside
  polygon CTR (long edges).
- **Permissive (max edge, rounded up)**: rule fires only after aircraft
  is past every polygon edge. Risk: rule holds aircraft in CTR-control
  after it has actually exited the polygon on short edges.
- **Median (~11 NM)**: balanced average error.

**Recommended: max-edge rounded UP plus proxy-offset margin — `18 NM`
for LOWG.** "Rule says still inside CTR until aircraft is past every
polygon edge" matches real-ATC's strict release-only-after-boundary
doctrine. **Critical rounding + center-mismatch decision (fold-in of
plan-review finding #3 + pass-4 finding #1):**
- The polygon's longest edge is 16.25 NM **from the published ARP**.
- The runtime guard at `Guard.kt:441-459` does NOT use a true ARP — it
  uses the **lexicographically-first runway threshold as an
  ARP-proxy** (see existing comment at `Guard.kt:443-455`). LOWG's
  16C threshold is offset from the published ARP by an amount that
  the existing comment characterizes as "hundreds of metres". A
  conservative upper bound on the proxy-offset is ~1 NM (the runway
  itself spans ~3 km).
- Therefore: the relevant distance from the **threshold proxy** to
  the far polygon edge is 16.25 NM (polygon-from-ARP) + up to ~1 NM
  (proxy-offset, worst-case) = ~17.25 NM.
- Authoring `17` is too tight (loses margin on a worst-case proxy
  offset). Authoring `16` rounds down of the polygon max even
  before the proxy-offset adjustment.
- Author **`18`** — covers 16.25 NM polygon max + ~1 NM proxy-offset
  margin, conservatively rounded up to the next integer. The
  schema stays `Int?` (sub-NM precision is irrelevant against the
  6.7–16.25 NM polygon spread plus the unmeasured proxy offset);
  the `Doctrine.IcaoAnnex11` constant uses the existing
  `Meters.fromNauticalMiles(Int)` companion; we deliberately do
  **not** add a Double overload.

**Center-mismatch caveat (NOT closed by this epic):** the proper fix
for the proxy / ARP discrepancy is a true `Aerodrome.referencePoint`
runtime field plumbed through the guard. Today the field exists on
`Aerodrome` (`WorldModel.kt:348`) as `LatLon?` for projection use,
but is null for synthetic worlds and isn't read by the guard. The
guard's threshold-proxy is documented as the temporary stand-in.
fn-7 stays on the existing proxy and absorbs the offset in the
authored radius; a future epic that uses a true reference point
narrows the radius back toward the polygon-true value. Filed as
`D-AUDIT-arp-proxy-runtime` in the deferments register.

**LJMB authoring (fold-in of pass-5 plan-review finding #2 — the
ICAO 5 NM floor is permissive-wrong for LJMB and ships a known
under-fires risk):** docs-scout could not bot-fetch the Slovenia
eAIP (403/404). Without LJMB polygon data, fn-7 authors LJMB at
**18 NM (same conservative value as LOWG)** rather than the 5 NM
floor.

Reasoning: a too-large authored radius is conservative-wrong (rule
holds aircraft in CTR control after it has actually exited polygon —
under-fires the release; controller keeps responsibility too long).
A too-small authored radius is permissive-wrong (rule releases inside
real CTR — over-fires; controller hands off while aircraft is still
in controlled airspace, which is the regulatorily-bad direction).
For real airports without AIP data, fn-7 picks the conservative
direction by default and explicitly **does not** ship the 5 NM floor
for an aerodrome whose actual polygon is almost certainly larger.
Real polygon transcription is still deferred
(`D-AUDIT-ljmb-polygon`). The 18 NM placeholder is justified solely
by reuse of the LOWG max-edge-rounded-up + proxy-margin value as a
conservative bound — NOT by any uncited regional comparison
(per pass-8 plan-review finding #3 — comparative claims like "Class
D regional CTRs typically span 10–15 NM" require AIP citations,
which fn-7 does not transcribe; the safer rhetoric is "the 18 NM
LOWG value is reused as a conservative bound until LJMB AIP polygon
data lands").

The 5 NM ICAO floor remains the **default for synthetic aerodromes**
(test fixtures with `Aerodrome("TEST", ...)` that don't author a
radius); it is NOT the value that ships for any modeled real
aerodrome in this epic.

**Real-airport authoring guardrail (fold-in of pass-7 plan-review
finding #3 — closes the "silent unsafe default for future real
airports" gap):** add a focused test
`RenderedAirportRadiusAuthoringTest` (or augment an existing
candidate-validation spec under `migration/src/jvmTest/...`) that
asserts every airport in `cad/airports/rendered/<icao>/world-candidate.json`
has a non-null `ctrApproximationRadiusNauticalMiles`. The test
iterates the rendered directory at test-time and fails if any
real-airport candidate is unauthored. Fixture-only synthetic
aerodromes (constructed in test code with `Aerodrome("TEST", ...)`)
are unaffected — they're not in `cad/airports/rendered/`. This
moves the failure mode from "silent fall-back to 5 NM" to "loud
test failure" the moment a future implementer adds a new airport
without authoring its radius.

**Pass scope (per `feedback_pass_scope.md`):** fold the `Doctrine`
constant + KDoc disambiguation + sub-floor loader validation + the
`failureMessage` rewrite into the same pass. Don't fold AIRAC cycle
tracking or LJMB polygon transcription — too much surface.

## Approach

1. **`Aerodrome` data class** at `core/.../world/WorldModel.kt:319-349`
   gains `val ctrApproximationRadius: Meters =
   Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` at the **end** of the parameter
   list. Non-nullable runtime type; default keeps 7 of the 8
   existing fixture-site named-arg constructors and all 7
   `Aerodrome.copy(...)` sites back-compat without modification
   (the 8th fixture, `OutsideAerodromeRadiusSpec.kt:69`, is
   intentionally edited under §8 to preserve its 12 NM test geometry). KDoc on the new field
   disambiguates from polygon-CTR doctrine (per practice-scout #4):
   *"Circular sim approximation. Real CTR is polygonal per AIP AD 2.17;
   this is a single-radius stand-in for the runtime guard. Polygon
   containment is FM/Lean territory (`fn-4` lineage); see
   `D-AUDIT-polygon-ctr` deferment."*

2. **`Doctrine.IcaoAnnex11`** introduces TWO related constants
   (fold-in of pass-4 finding #2 — eliminates numeric drift between
   the metres value and the loader-side schema-units check):
   - `const val CTR_FLOOR_NAUTICAL_MILES: Int = 5` — the source
     numeric value, used at the loader's `require(n >= ...)` and in
     the loader error message (`"...must be >= $CTR_FLOOR_NAUTICAL_MILES NM..."`).
   - `val CTR_FLOOR_5NM: Meters = Meters.fromNauticalMiles(CTR_FLOOR_NAUTICAL_MILES)`
     — the runtime metres value, used at the primary-constructor
     default and at the loader's `?: ...` fallback. Derived from the
     int constant so the numeric `5` lives in exactly one place.

   This is the single named-constant chokepoint for the ICAO Annex 11
   CTR-extent floor.
   KDoc carries an edition-dated citation plus a paraphrase (no
   verbatim quotation — the project has no canonical Annex 11 text in
   `research/txt/`; the existing convention at
   `controller/.../AtisLetterMismatchAdvisorySpec.kt:26` and
   `ControllerTypes.kt:120, 163` is short-cite-no-verbatim, which we
   match):
   - **Edition / source citation** (per project commandment cited by
     plan-review finding #5): *"ICAO Annex 11 — Air Traffic Services,
     15th edition, July 2018, §2.11 (control-zone lateral limits)."*
     The Annex revises subsection numbering across editions; cite the
     edition explicitly. Implementer does NOT transcribe verbatim
     wording — the existing sibling-spec convention is paraphrase
     only.
   - **Paraphrase** (load-bearing content, in implementer's own
     words): *"the lateral limits of a control zone shall extend at
     least 5 NM (9.3 km) from the centre of the aerodrome in the
     directions from which approaches may be made"*. The Annex
     specifies the floor as a **directional minimum**, not a polygon
     shape — a 5 NM **circle** meets the minimum along every axis.
     Real CTR polygons authored from AIP often extend beyond the
     5 NM floor on the approach axis (so a 5 NM circle is **too
     small** there relative to the actual published polygon) and may
     fall short of 5 NM abeam (the floor doesn't require the floor
     **abeam**, only on approach axes — though most authored
     polygons stay at-or-above 5 NM all-around). The take-away: a
     5 NM circle is a regulatory floor, not a polygon-shape
     approximation. Polygon containment supersedes (per pass-8
     plan-review finding #4 — earlier "anisotropic-wrong by
     construction" wording overstated the issue).
   The constant lives in a new sibling file
   `core/.../world/Doctrine.kt` (or appended to `WorldModel.kt` if
   convention favors single-file core types — implementer chooses
   based on what the directory already does).

3. **`CandidateAerodrome` schema** at
   `migration/.../WorldCandidateSchema.kt:88-115` gains
   `val ctrApproximationRadiusNauticalMiles: Int? = null`
   (unit-suffixed-int convention, default-null = back-compat).

4. **`WorldCandidateLoader`** at `WorldCandidateLoader.kt:284-337`
   threads the new field with **sub-floor validation** (fold-in of
   plan-review finding #6 + plan-review-pass-2 finding #1):
   - Read `world.aerodrome.ctrApproximationRadiusNauticalMiles`.
   - **Validation pattern: `require(...)` throwing.** Loader's
     existing convention is `require(condition) { message }` and
     `error("...")` for unsupported variants (verified at
     `WorldCandidateLoader.kt:140, 363, 371, 446, 450, 477` and
     similar). It does **not** use `Either.Left` /
     typed-validation-failure. We match the existing pattern; do not
     introduce a new validation API for this single field.
   - If non-null AND `< Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES`:
     `require(n >= Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES) {
     "ctrApproximationRadiusNauticalMiles must be >=
     ${Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES} NM (ICAO
     Annex 11 §2.11 floor): got $n" }`. Reference the int constant —
     no inline `5` literal in the loader. Add the `require`
     immediately before the `Aerodrome(...)` construction.
   - In the `Aerodrome(...)` construction call:
     `ctrApproximationRadius =
     world.aerodrome.ctrApproximationRadiusNauticalMiles
     ?.let(Meters::fromNauticalMiles)
     ?: Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`.
   - The fallback is **explicit** at the loader site (not implicit via
     the constructor default) so a future reader of the loader sees
     the doctrine resolution — mirrors fn-6's "decision visible at
     each chokepoint" principle.

5. **`OutsideAerodromeRadius`** at `Guard.kt:431-459` becomes a
   `data object` (no constructor argument). The guard already does
   `ctx.world.aerodromes[ctx.view.aerodromeId]` to find the ARP proxy
   (line 441) — read `ctrApproximationRadius` from the same lookup.
   - **`failureMessage` rewrite** (fold-in of plan-review finding #2):
     today's value `"Aircraft within ${thresholdMetres.value}m radial
     of aerodrome (still in CTR scope)"` interpolates a constructor
     field that no longer exists. New static value:
     `"Aircraft within aerodrome CTR approximation radius (still in
     CTR scope)"`. Per-aerodrome radius variance is no longer a
     diagnostic concern at this level (the value lives on the
     aerodrome and is logged elsewhere if needed).
   - KDoc updated for anisotropic-approximation acknowledgement (per
     practice-scout #4).

6. **Both `TowerDeparture.kt` call sites** (DEP-RADAR-SERVICE-TERMINATED
   ~322, DEP-CROSS-AERODROME-RELEASE ~370) construct `OutsideAerodromeRadius`
   with no argument.

7. **JSON authoring**: add `ctrApproximationRadiusNauticalMiles` to
   `cad/airports/rendered/lowg/world-candidate.json` and
   `cad/airports/rendered/ljmb/world-candidate.json`.
   **Hand-edit-the-rendered-output convention (pass-4 finding #4):**
   per `project_world_building.md` ("Hand-author worlds in CAD, not
   automated pipeline; parsers are reference tools") and the existing
   `cad/airports/lowg-authoring.md` callout that `world-candidate.json`
   is the canonical worked-current-core artifact, hand-editing the
   rendered JSON is the project convention. There is no automated CI
   regenerator that would clobber this edit. Future renderer-pipeline
   automation (if/when adopted) would need a manifest-side authoring
   field; that's out of scope here.
   - **LOWG: 18** (max polygon edge 16.25 NM rounded **up** — see
     Decision context for rounding rationale).
   - **LJMB: 18** (conservative placeholder = same as LOWG; chosen
     over the 5 NM ICAO floor because LJMB's real polygon is almost
     certainly larger than 5 NM and a 5 NM authoring would release
     inside real CTR — see Decision context for full reasoning).
     Real AIP polygon transcription deferred — flag in
     `wiki/data-sources/ljmb.md` and filed as `D-AUDIT-ljmb-polygon`.

8. **`OutsideAerodromeRadiusSpec`** at
   `controller/src/commonTest/.../bdi/` updated:
   - Existing 3 rows refactor for the new dispatch shape (drop
     constructor argument from `OutsideAerodromeRadius(...)` calls).
     Test fixture aerodromes pass `ctrApproximationRadius =
     Meters.fromNauticalMiles(12)` explicitly to preserve existing
     22 224 m geometry.
   - Add row 4: aerodrome constructed with **no**
     `ctrApproximationRadius` arg (default kicks in → ICAO floor 5 NM).
     Coords just outside the 5 NM ring; `evaluate()` returns true. Pin:
     primary-constructor default works.
   - **Add row 5 (loader sub-floor rejection)**: this lives in
     `migration/src/jvmTest/.../WorldCandidateLoaderSpec.kt` (or the
     existing loader test file — investigate naming convention) since
     it's loader-level, not guard-level. Author a candidate JSON with
     `ctrApproximationRadiusNauticalMiles: 4`; assert load throws
     `IllegalArgumentException` (`require {...}`) with a message
     mentioning the 5 NM floor.

9. **`Aerodrome.copy(…)` audit** (fold-in of plan-review finding #9).
   Sites identified by repo-scout:
   - `core/.../RouteAdjacentTestWorlds.kt:67`
   - `core/.../WorldConstructionTest.kt:712, 728, 905, 948`
   - `core/.../CommunicationsJurisdictionTest.kt:376, 393`

   With the primary-constructor default, none should require updating
   (each `.copy(...)` preserves the unchanged `ctrApproximationRadius`,
   which on construction defaulted to the ICAO floor unless the
   underlying aerodrome was loader-constructed with an authored value).
   Verify with the test run; no edits expected.

10. **G2 R4 empirical re-baseline**: with LOWG going from 12 NM ⇒
    18 NM (max-edge-rounded-up), the rule fires *later* in the flight
    (aircraft must be ~9 km further out before crossing the larger
    ring). Gap shrinks. Verify `>= 30_000L` still holds; capture
    observed gap in evidence. Back-of-envelope: 5 NM extra at
    ~62 m/s ≈ +149 s firing-delay; observed 374.6 s at 12 NM ⇒
    expected ≈ 225 s at 18 NM, well above the 30 s pin.

### Pattern reuse

- `Meters.fromNauticalMiles(Int)` companion at `core/.../WorldModel.kt:39`
  (landed in fn-6).
- `Aerodrome` data class at `core/.../WorldModel.kt:319-349` — plain data
  class; default-value new field is non-breaking for 8 existing fixture
  sites and 7 `.copy(…)` sites (per repo-scout).
- `CandidateAerodrome` unit-suffixed-int schema convention at
  `migration/.../WorldCandidateSchema.kt:88-115`.
- Loader-level scalar-bounds-check via throwing `require(...) { msg }`
  — the convention used at `WorldCandidateLoader.kt:140, 363, 371,
  446, 450, 477`. Do not invent a new typed-validation pattern.

## Risks / dependencies

- **Dep:** `fn-6-kinematic-position-on` (closed). The kinematic-coords
  fix is the precondition for the radius retune — without it, a smaller
  AIP-faithful radius would fire at the wrong physical moment.
- **Risk: G2 gap pin regression.** With LOWG retuned 12 NM → 18 NM, the
  release fires later (aircraft must travel further). Gap shrinks. The
  `>= 30 s` pin should still hold (observed was 374.6 s post-fn-6) but
  must be re-verified empirically before commit.
- **Risk: pre-existing flake** `:migration:jvmTest >
  LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`
  — out of fn-7 scope, ignore.
- **Risk: doctrine-value choice for LOWG.** The 7-vs-18 NM debate is
  unresolvable without polygon containment. Pick max-edge-rounded-up
  (recommended) and document the choice; future polygon work supersedes.
- **Risk: rule-equality semantics shift.** `OutsideAerodromeRadius`
  becoming `data object` switches from `data class` content equality to
  singleton identity. No consumers should care (rules are looked up by
  class, not value), but verify by running the full suite.

## Acceptance

- **R1:** `Aerodrome.ctrApproximationRadius: Meters` field exists with
  primary-constructor default `= Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`.
  7 of the 8 existing fixture-site named-arg constructors (enumerated
  in the task spec's "Files NOT edited" section) and all 7
  `Aerodrome.copy(…)` sites compile and pass without modification.
  The 8th fixture (`OutsideAerodromeRadiusSpec.kt:69`) is
  intentionally edited under R6 — see Task Approach §8.
- **R2:** `Doctrine.IcaoAnnex11` exposes both
  `const val CTR_FLOOR_NAUTICAL_MILES: Int = 5` (source numeric) and
  `val CTR_FLOOR_5NM: Meters =
  Meters.fromNauticalMiles(CTR_FLOOR_NAUTICAL_MILES)` (derived metres)
  — the numeric `5` lives in exactly one place. KDoc carries an
  edition-dated citation (ICAO Annex 11, 15th ed., July 2018, §2.11)
  **plus paraphrase only** — no verbatim quotation (the project has
  no canonical Annex 11 text in `research/txt/`, and existing Annex
  11 references in the codebase use short-cite-no-verbatim).
  Directional-anisotropy note included in KDoc.
- **R3:** `OutsideAerodromeRadius` becomes a `data object` (no
  constructor argument). `evaluate()` reads
  `ctx.world.aerodromes[ctx.view.aerodromeId].ctrApproximationRadius`
  directly. `failureMessage` is the static string `"Aircraft within
  aerodrome CTR approximation radius (still in CTR scope)"`. KDoc
  updated to call out the anisotropic-circle approximation.
- **R4:** Both `TowerDeparture.kt` call sites construct `OutsideAerodromeRadius`
  with no argument. The previously-passed `Meters.fromNauticalMiles(12)`
  is removed.
- **R5a (LOWG):** `cad/airports/rendered/lowg/world-candidate.json`
  authors `ctrApproximationRadiusNauticalMiles: 18` per AIP AD 2.17.
  Rationale: 16.25 NM polygon-from-ARP max + ~1 NM ARP-proxy-offset
  margin (the guard centres on the threshold proxy, not the ARP)
  → 18 NM rounded up. Hand-edit of the rendered JSON; matches
  project's hand-authored-rendered-output convention.
- **R5b (LJMB):** `cad/airports/rendered/ljmb/world-candidate.json`
  authors `ctrApproximationRadiusNauticalMiles: 18` (conservative
  placeholder = same as LOWG; LJMB polygon transcription deferred,
  the 5 NM ICAO floor would be permissive-wrong for an actual
  aerodrome — see Decision context). Tracked as
  `D-AUDIT-ljmb-polygon`.
- **R6:** `OutsideAerodromeRadiusSpec` exercises the new dispatch shape
  (3 existing rows refactored + 1 new row for ICAO-floor fallback when
  `Aerodrome` is constructed with no radius authored).
- **R7:** No regression. The verification set is identical at epic
  and task levels and is run as **three separate commands** (per
  pass-7 plan-review finding #1 — single gradle invocation
  short-circuits on the first failing test class, so detekt and the
  rest of `:migration:jvmTest` would not run; we want each to run
  to completion):
  1. **Targeted suites that must be green (Gradle exits 0):**
     `./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest
     :core:jvmTest :protocol:jvmTest detekt`. All pass.
  2. **`:migration:jvmTest` with `--continue`** (Gradle runs every
     test even after a failure):
     `./gradlew :migration:jvmTest --continue`. Result: every test
     passes EXCEPT the known pre-fn-7 flake (test-class +
     test-method-name listed below). Capture the full failure block
     verbatim into `## Evidence`.
  3. **G2 R4 empirical pin:** `:sim:jvmTest --tests
     "xyz.easiersaid.twr.sim.G2CrossAerodromeVfrTest"` shows R4 gap
     `>= 30_000L`. Observed gap captured in `## Evidence`.

  **Pre-existing flake — concrete identifier (per pass-7 plan-review
  finding #2 — "unchanged from baseline" was subjective):** the
  failing test is exactly:

      :migration:jvmTest >
      xyz.easiersaid.twr.migration.world.LjmbWorldCandidateValidationTest >
      writesLjmbCurrentCoreValidationReport()

  R7 is satisfied when (a) every test outside that single
  test-method passes; (b) the failing test is exactly that test-
  method (no new failures, no different test-method); and (c) the
  failure output is captured into `## Evidence` labeled
  "pre-fn-7 flake; out of fn-7 scope". No subjective "unchanged from
  baseline" judgement is required — only that the test-method
  identifier matches.
- **R8 (loader sub-floor rejection):** `WorldCandidateLoader` rejects
  authored values
  `< Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES` via
  `require(n >= Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES) {...}`
  (matching the loader's existing throwing-validation pattern; not
  `Either.Left`; no inline `5` literal in the loader). The error
  message interpolates the constant value. New focused test pins the
  throw with `assertFailsWith<IllegalArgumentException>` (or the
  surrounding loader-spec convention).
- **R9 (real-airport authoring guardrail):** focused test in
  `migration/src/jvmTest/...` enforces TWO things:
  - **Currently-modeled airports — exact-value assertions
    (per pass-8 plan-review finding #2 — non-null was too weak):**
    LOWG must be `18`. LJMB must be `18`. Hardcoded in the test;
    a future change to either authored value forces a deliberate
    test-update + plan-review.
  - **Future airports — non-null iteration:** every directory under
    `cad/airports/rendered/<icao>/world-candidate.json` not in the
    LOWG/LJMB allowlist must (a) have a non-null
    `ctrApproximationRadiusNauticalMiles` and (b) be added to the
    test's allowlist with its expected exact value. The test's
    failure message instructs the implementer to either add the
    value or update the allowlist deliberately. This catches both
    "missing field" (silent 5 NM fallback) and "nonsensical value"
    (180, accidental 5, etc.) failure modes.

## Review considerations

Per `feedback_plans_review_aware.md`. Each axis is addressed inline so
silence is impossible.

### FP / type-safety

- Runtime type stays non-nullable (`Meters`, not `Meters?`). Default-
  param value is the doctrine constant — the absence of an authored
  value resolves to a real value at construction, never to null.
- Sub-floor JSON values are rejected at the loader via throwing
  `require(n >= Doctrine.IcaoAnnex11.CTR_FLOOR_NAUTICAL_MILES) {...}`
  — matching the loader's existing throwing-validation convention
  (lines 140, 363, 371, 446, 450, 477). No inline `5` literal in the
  loader; the named int constant is the single chokepoint. The
  loader is not pure-Either-typed today; introducing one new typed-
  validation API for a single field would diverge from convention
  and is rejected per pass-2 plan-review finding #1.
- Single named constant `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` is the
  ICAO citation chokepoint; cited once at the primary-constructor
  default and once at the loader fallback. The numeric value
  (`Meters.fromNauticalMiles(5)`) is not duplicated.
- `OutsideAerodromeRadius` becoming `data object` shifts equality from
  content to singleton-identity. No consumers compare rules by value
  (verified by class-lookup convention); test suite confirms.

### Test architecture

- `OutsideAerodromeRadiusSpec`: 3 existing rows refactored to drop the
  constructor argument + 1 new row pinning the primary-constructor
  default (ICAO floor at 5 NM). Tests the **construction-side** doctrine
  resolution.
- New focused loader test pins `R8` — sub-floor JSON authoring rejected
  with a message citing the 5 NM floor. Tests the **loader-side**
  validation. Two distinct chokepoints, two distinct tests.
- G2 `G2CrossAerodromeVfrTest` R4 gap pin — empirical re-baseline (not
  a new test; existing pin re-verified at the new 18 NM ring). Captures
  observed gap in `## Evidence` per project convention.
- `LowgGoldenTest` stays green — **general regression coverage only**
  (per pass-4 plan-review finding). Circuit-training traffic stays
  with tower and does not exercise `DEP-RADAR-SERVICE-TERMINATED`
  release. The dedicated rule-side coverage lives in
  `OutsideAerodromeRadiusSpec`; the dedicated cross-aerodrome-flow
  coverage lives in `G2CrossAerodromeVfrTest`.
- No scaffold tests added. Per `feedback_testing_philosophy.md`,
  prefer real-job tests; the construction-side row + loader-side row
  + empirical re-baseline are all real-job.

### Impact

- 7 `Aerodrome(...)` named-arg fixture sites: back-compat via
  default. The 8th (`OutsideAerodromeRadiusSpec.kt:69`) is
  intentionally edited under R6.
- 7 `Aerodrome.copy(...)` sites: back-compat via default.
- 2 `TowerDeparture.kt` call sites: explicit one-line edit each.
- 1 `Guard.kt` rule: `data class` → `data object` + `failureMessage`
  rewrite.
- 1 `WorldCandidateSchema.kt` field added (default-null = JSON
  back-compat for any pre-existing world-candidate fixtures).
- 1 `WorldCandidateLoader.kt` validation path added (new failure mode
  for sub-5-NM JSON authoring; surfaced in test).
- 2 `world-candidate.json` files authored.
- 2 `wiki/data-sources/*.md` files updated.
- 1 new constant + KDoc.

No surface beyond the named-aerodrome-CTR-radius concern. AIRAC cycle
tracking and polygon containment stay deferred.

### Operational correctness

- **18 NM** rounds up from the 16.25 NM polygon-from-ARP max edge
  PLUS adds a ~1 NM proxy-offset margin (the guard centres the ring
  on the lexicographically-first runway threshold, not the ARP —
  see `D-AUDIT-arp-proxy-runtime`). Combined target ≈ 17.25 NM
  rounded up to 18. Under worst-case proxy offset the rule still
  does not release an aircraft that is geographically inside the
  polygon CTR. Aligns with reality-anchored "rule still in CTR until
  past every polygon edge" doctrine. The
  `feedback_reality_anchored.md` commitment is preserved
  unflinchingly: this is the doctrinally-correct **circular**
  approximation; polygon containment supersedes it (filed as
  `D-AUDIT-polygon-ctr`).
- **LJMB at 18 NM is conservative-wrong, not permissive-wrong** —
  per pass-5 plan-review fix. Real LJMB CTR size is unknown to
  fn-7 (Slovenia eAIP not bot-fetchable; AIP citation deferred).
  Reusing the LOWG value as a conservative bound is the simplest
  defensible placeholder. The 18 NM ring will hold an aircraft in
  CTR control after
  it has actually exited the real polygon (rule under-fires the
  release; controller keeps responsibility too long). This is the
  regulatorily-safe direction: the aircraft is geographically OUT
  of CTR but the controller still has it — operationally a non-
  event in the simulator (the controller continues to track until
  the rule fires; no missed handoff, no released-into-uncontrolled-
  while-still-in-CTR). The previous draft used 5 NM (ICAO floor)
  which would have been **permissive-wrong** — releasing inside
  real CTR — and that was a corner cut on the reality-anchored
  doctrine. Fixed by raising to 18 NM until polygon transcription
  lands (`D-AUDIT-ljmb-polygon`).
- Sub-floor authoring rejection prevents an authoring-time accident
  (e.g. forgetting the `_NauticalMiles` suffix and writing `1` for
  what was meant as `1 NM`-of-extra-buffer-on-top-of-floor) from
  silently producing a 1 NM ring that releases inside CTR.
- KDoc on the new field calls out the circular-approximation caveat
  + polygon-CTR future direction; future readers see the doctrine
  trail without grepping the deferments register.

## Early proof point

Task `fn-7.1` is the only task. If the primary-constructor default
or the loader sub-floor rejection fails, the architectural choice
(option 1) needs re-evaluation against options 2/3.

## Requirement coverage

| Req  | Description | Task(s) | Gap justification |
|------|-------------|---------|-------------------|
| R1   | `Aerodrome.ctrApproximationRadius: Meters` + primary-constructor default | fn-7.1 | — |
| R2   | `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` constant + edition-dated citation | fn-7.1 | — |
| R3   | `OutsideAerodromeRadius` becomes `data object`, reads from world, static `failureMessage` | fn-7.1 | — |
| R4   | TowerDeparture.kt call sites use no-arg constructor | fn-7.1 | — |
| R5a  | LOWG JSON authoring (18 NM, AIP AD 2.17 max-edge-rounded-up) | fn-7.1 | — |
| R5b  | LJMB JSON authoring (18 NM conservative placeholder; polygon deferred — 5 NM ICAO floor would be permissive-wrong for a real aerodrome) | fn-7.1 | — |
| R6   | `OutsideAerodromeRadiusSpec` updated + ICAO-floor row | fn-7.1 | — |
| R7   | No regression; G2 gap pin ≥ 30 s holds empirically | fn-7.1 | — |
| R8   | Loader sub-floor rejection + focused test | fn-7.1 | — |
| R9   | Real-airport authoring guardrail (every rendered candidate authors the field non-null) | fn-7.1 | — |

## Deferments register

**Filing venue (fold-in of pass-5 finding #4 + pass-6 finding #4 +
pass-7 finding #6):** three registers exist in this project, with
different scopes — fn-7 updates exactly one of them:

1. **`.flow/epics/fn-7-per-aerodrome-aip-driven-ctr-radius.json`** —
   flowctl's epic-tracking record. The fn-7 epic itself lives here
   (created via `flowctl epic create`). No manual update needed for
   fn-7 closure; flowctl handles it.
2. **`/home/andrew/dev/projects/twr2/.plan`** — high-level project
   backlog using `A* / M* / G*` namespacing. **fn-7 was never
   tracked in `.plan`** (epic management has moved to flowctl) so
   no `.plan` update is appropriate here. Adding a "DONE" entry
   retroactively would be misleading. Codex pass-6 originally
   flagged `.plan` as canonical; on closer reading, `.plan`'s
   namespace is for items it actually owns, and fn-7 isn't one.
3. **`~/.claude/plans/pilot-firewall.md` § Deferments register** —
   the user's audit register, per persistent memory
   `reference_audit_registers.md`: *"D-AUDIT/D-PF/D-PASS items live
   in `~/.claude/plans/pilot-firewall.md` § Deferments register, not
   in the project repo"*. Uses `<AUDIT-N-placeholder>` numbered + `<AUDIT-kebab-placeholder>`
   styles (mixed). **fn-7 appends one-line cross-reference pointers
   here** for each new deferment (`D-AUDIT-arp-proxy-runtime`,
   `D-AUDIT-polygon-ctr`, `D-AUDIT-airac-cycle-tracking`,
   `D-AUDIT-ljmb-polygon`).

The **detail bodies** for each new fn-7-filed deferment live in
THIS section of the epic spec (so reviewers reading the epic see
the full context). The **register pointer** in `pilot-firewall.md`
makes the deferments discoverable from the canonical starting
point per user memory.

Forward-looking entries (not acceptance criteria):

- **`D-AUDIT-arp-proxy-runtime`** — the runtime
  `OutsideAerodromeRadius` guard centres its ring on the
  lexicographically-first runway threshold as an ARP-proxy (existing
  comment at `Guard.kt:443-455`). Real ARP coordinates exist on
  `Aerodrome.referencePoint: LatLon?` (`WorldModel.kt:348`) but are
  null for synthetic worlds and not consumed by the guard. The
  authored radius in fn-7 absorbs the proxy-offset margin (~1 NM at
  LOWG) by rounding the polygon-from-ARP max edge up to the next
  integer-plus-margin (16.25 NM → 18 NM). Future epic plumbs a true
  reference point through the guard and narrows the radius back
  toward the polygon-true value. Out of fn-7 scope.
- **`D-AUDIT-polygon-ctr`** — replace the circular-radius approximation
  with polygon containment. fn-4 (richer airspace geometry, FM/Lean) is
  the long-term replacement; runtime polygon containment would consume
  `AirspaceVolume.boundary`. Future epic.
- **`D-AUDIT-airac-cycle-tracking`** — typed `Aerodrome.airacCycle:
  AiracCycle` field for staleness checking. Practice-scout flagged this
  per OpenScope/EuroScope precedent. CTR boundaries change rarely
  (multi-year cadence) so not urgent. Future authoring pass.
- **`D-AUDIT-ljmb-polygon`** — LJMB CTR polygon transcription. Docs-
  scout couldn't bot-fetch Slovenia eAIP (403/404). LJMB **ships at
  18 NM** (conservative placeholder = same as LOWG; under-fires the
  release rule, regulatorily-safe). The 5 NM ICAO Annex 11 §2.11
  floor was deliberately rejected for LJMB because it would over-fire
  (release inside real CTR — permissive-wrong; see Operational
  correctness in this spec). Future authoring pass transcribes the
  actual AD 2.17 polygon and tightens the placeholder to a polygon-
  faithful value.

Closures:

- **`D-AUDIT-lowg-ctr-radius`** (filed in fn-6 spec) — **closed by
  this epic** (fn-7) via R5a (LOWG authored at 18 NM = AIP AD 2.17
  max-edge-rounded-up). Historical filing remains in
  `.flow/specs/fn-6-kinematic-position-on.md`; closure note here.
