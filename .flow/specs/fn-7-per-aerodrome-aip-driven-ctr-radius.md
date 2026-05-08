# Per-aerodrome AIP-driven CTR-radius retune

## Overview

Replace the magic `Meters.fromNauticalMiles(12)` at `TowerDeparture.kt`'s
two `OutsideAerodromeRadius` call sites with a per-aerodrome
`Aerodrome.ctrApproximationRadius: Meters` field, authored from each
aerodrome's AIP AD 2.17 CTR polygon. The data-layer smart constructor
falls back to the ICAO Annex 11 §2.11.4.2 floor (5 NM = 9 260 m) when
un-authored. Closes the `D-AUDIT-lowg-ctr-radius` deferment register
entry filed in `fn-6`'s spec.

**Important reality check (docs-scout finding):** real CTRs are polygons,
not circles. LOWG CTR per AIP AD 2.17 ranges **6.7 NM** (W edge) to
**16.25 NM** (S edge) from ARP — the 12 NM hardcode is inaccurate-high
on three edges and inaccurate-low on two. The user's "~7 NM" framing is
one (short) edge of the polygon, not a mean. Polygon containment is the
canonical fix; this epic picks a defensible single scalar per aerodrome
and surfaces the approximation in KDoc + filed deferments.

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

# Detekt baseline preserved
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew detekt --console=plain

# Full check
nix --extra-experimental-features 'nix-command flakes' develop path:. --command \
  ./gradlew :sim:jvmTest :pilot:jvmTest :controller:jvmTest :core:jvmTest :protocol:jvmTest --console=plain
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

## Decision context

**Three architectural choices on the table:**

1. **Smart constructor on `Aerodrome` resolves null to ICAO floor at
   construction** *(this epic — selected; practice-scout high-confidence
   recommendation)*. Field is non-nullable in the runtime type; null in
   the JSON schema means "not authored, use floor." Single chokepoint;
   guard reads non-null `Meters` directly.
2. Nullable field at runtime, guard does `?: ICAO_FLOOR` (rejected:
   doctrine becomes invisible in TowerDeparture diffs; breaks across
   future call sites).
3. Constructor argument on `OutsideAerodromeRadius` (today's shape) with
   a per-aerodrome default — kept for override (rejected: stale-config
   surface; the rule shouldn't carry a value when the aerodrome already
   has it).

Selected option mirrors `fn-6`'s "decision: `coords` is non-nullable"
pattern. ICAO floor lives in a single named constant
(`Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` or similar) cited at the smart
constructor — single citation, single chokepoint.

**Authoring-value choice for LOWG:** the polygon ranges 6.7–16.25 NM.
Three doctrine flavours:
- **Conservative (min ~7 NM)**: rule fires earliest; aircraft transits
  Class G longest. Risk: rule fires while aircraft is still inside
  polygon CTR (long edges).
- **Permissive (max ~16 NM)**: rule fires only after aircraft is past
  every polygon edge. Risk: rule holds aircraft in CTR-control after
  it has actually exited the polygon on short edges.
- **Median (~11 NM)**: balanced average error.

**Recommended: max-edge (~16 NM for LOWG)** — "rule says still inside
CTR until aircraft is past every polygon edge" matches real-ATC's
strict release-only-after-boundary doctrine. The implementer authors
the actual chosen value at `world-candidate.json` time; the spec
documents both options. LJMB: ICAO 5 NM floor as starting placeholder
(real polygon transcription deferred to a future authoring pass since
docs-scout couldn't fetch the LJMB AIP through the bot path).

**Pass scope (per `feedback_pass_scope.md`):** fold the `Doctrine`
constant + KDoc disambiguation into the same pass. Don't fold AIRAC
cycle tracking — too much surface.

## Approach

1. **`Aerodrome` data class** at `core/.../world/WorldModel.kt:319-349`
   gains `val ctrApproximationRadius: Meters`. The runtime type is
   non-nullable; smart constructor handles the null-source case.
2. **`Doctrine.IcaoAnnex11.CTR_FLOOR_5NM = Meters.fromNauticalMiles(5)`**
   — single named constant for the ICAO §2.11.4.2 floor. KDoc cites the
   verbatim Annex 11 wording: *"shall extend to at least 9.3 km (5 NM)
   from the centre of the aerodrome…in the directions from which
   approaches may be made"* — and notes this is **directional**, not
   omnidirectional, so a 5 NM circular floor is anisotropic-wrong by
   construction.
3. **`CandidateAerodrome` schema** at `migration/.../WorldCandidateSchema.kt:88-115`
   gains `val ctrApproximationRadiusNauticalMiles: Int? = null`
   (unit-suffixed-int convention, default-null = back-compat).
4. **`WorldCandidateLoader`** at `WorldCandidateLoader.kt:284-337`
   threads the new field: `ctrApproximationRadius =
   world.aerodrome.ctrApproximationRadiusNauticalMiles?.let(Meters::fromNauticalMiles)
   ?: Doctrine.IcaoAnnex11.CTR_FLOOR_5NM`.
5. **`OutsideAerodromeRadius`** at `Guard.kt:431-459` becomes a `data
   object` (no constructor argument). The guard already does
   `ctx.world.aerodromes[ctx.view.aerodromeId]` to find the ARP proxy
   (line 441) — read `ctrApproximationRadius` from the same lookup.
   KDoc updated for anisotropic-approximation acknowledgement (per
   practice-scout #4).
6. **Both `TowerDeparture.kt` call sites** (DEP-RADAR-SERVICE-TERMINATED
   ~322, DEP-CROSS-AERODROME-RELEASE ~370) construct `OutsideAerodromeRadius`
   with no argument.
7. **JSON authoring**: add `ctrApproximationRadiusNauticalMiles` to
   `cad/airports/rendered/lowg/world-candidate.json` and
   `cad/airports/rendered/ljmb/world-candidate.json`. Initial values:
   - LOWG: 16 (max polygon edge — see Decision context).
   - LJMB: 5 (ICAO floor placeholder; real AIP polygon transcription
     deferred — flag in wiki/data-sources/ljmb.md).
8. **`OutsideAerodromeRadiusSpec`** at `controller/src/commonTest/.../bdi/`
   updated: existing 3 rows refactor for the new dispatch shape;
   add a 4th row asserting ICAO-floor fallback when JSON is absent
   (`Aerodrome` constructed with no radius authored).
9. **G2 R4 empirical re-baseline**: with LOWG going from 12 NM ⇒ 16 NM
   (or whatever value lands), the rule fires *later* in the flight
   (aircraft must be further out before crossing the larger ring). Gap
   may shrink. Verify `>= 30_000L` still holds; capture observed gap in
   evidence.

### Pattern reuse

- `Meters.fromNauticalMiles(Int)` companion at `core/.../WorldModel.kt:39`
  (landed in fn-6).
- `Aerodrome` data class at `core/.../WorldModel.kt:319-349` — plain data
  class; default-null new field is non-breaking for 6 existing fixture
  sites (per repo-scout).
- `CandidateAerodrome` unit-suffixed-int schema convention at
  `migration/.../WorldCandidateSchema.kt:88-115`.

## Risks / dependencies

- **Dep:** `fn-6-kinematic-position-on` (closed). The kinematic-coords
  fix is the precondition for the radius retune — without it, a smaller
  AIP-faithful radius would fire at the wrong physical moment.
- **Risk: G2 gap pin regression.** With LOWG retuned 12 NM → 16 NM, the
  release fires later (aircraft must travel further). Gap shrinks. The
  `>= 30 s` pin should still hold (observed was 374.6 s post-fn-6) but
  must be re-verified empirically before commit.
- **Risk: pre-existing flake** `:migration:jvmTest >
  LjmbWorldCandidateValidationTest.writesLjmbCurrentCoreValidationReport`
  — out of fn-7 scope, ignore.
- **Risk: doctrine-value choice for LOWG.** The 7-vs-16 NM debate is
  unresolvable without polygon containment. Pick max-edge (recommended)
  and document the choice; future polygon work supersedes.

## Acceptance

- **R1:** `Aerodrome.ctrApproximationRadius: Meters` (non-nullable) field
  exists, populated by a smart constructor that falls back to
  `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` when JSON-source is null.
- **R2:** `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM = Meters.fromNauticalMiles(5)`
  constant exists with KDoc citing Annex 11 §2.11.4.2 verbatim wording.
- **R3:** `OutsideAerodromeRadius` becomes a `data object` (no
  constructor argument). `evaluate()` reads
  `ctx.world.aerodromes[ctx.view.aerodromeId].ctrApproximationRadius`
  directly. KDoc updated to call out the anisotropic-circle approximation.
- **R4:** Both `TowerDeparture.kt` call sites construct `OutsideAerodromeRadius`
  with no argument. The previously-passed `Meters.fromNauticalMiles(12)`
  is removed.
- **R5:** `cad/airports/rendered/lowg/world-candidate.json` and
  `cad/airports/rendered/ljmb/world-candidate.json` author
  `ctrApproximationRadiusNauticalMiles` per AIP AD 2.17 (LOWG max edge,
  LJMB starting at ICAO floor pending polygon transcription).
- **R6:** `OutsideAerodromeRadiusSpec` exercises the new dispatch shape
  (3 existing rows refactored + 1 new row for ICAO-floor fallback when
  `Aerodrome` is constructed with no radius authored).
- **R7:** No regression: G2 R4 gap pin (≥ 30 s) still holds empirically;
  observed gap captured in evidence. `LowgGoldenTest`, all sim/jvm tests,
  pilot/controller/core/protocol suites stay green; detekt baseline
  unchanged.

## Early proof point

Task `fn-7.1` is the only task. If `OutsideAerodromeRadiusSpec`'s
ICAO-floor-fallback row fails (e.g. smart constructor doesn't resolve
null → 5 NM), the architectural choice (option 1, smart constructor)
needs re-evaluation against options 2/3.

## Requirement coverage

| Req | Description | Task(s) | Gap justification |
|-----|-------------|---------|-------------------|
| R1  | `Aerodrome.ctrApproximationRadius: Meters` + smart constructor | fn-7.1 | — |
| R2  | `Doctrine.IcaoAnnex11.CTR_FLOOR_5NM` constant | fn-7.1 | — |
| R3  | `OutsideAerodromeRadius` becomes `data object`, reads from world | fn-7.1 | — |
| R4  | TowerDeparture.kt call sites use no-arg constructor | fn-7.1 | — |
| R5  | JSON authoring for LOWG + LJMB | fn-7.1 | — |
| R6  | `OutsideAerodromeRadiusSpec` updated + ICAO-floor row | fn-7.1 | — |
| R7  | No regression; G2 gap pin ≥ 30 s holds empirically | fn-7.1 | — |

## Deferments register

Forward-looking entries (not acceptance criteria):

- **`D-AUDIT-polygon-ctr`** — replace the circular-radius approximation
  with polygon containment. fn-4 (richer airspace geometry, FM/Lean) is
  the long-term replacement; runtime polygon containment would consume
  `AirspaceVolume.boundary`. Future epic.
- **`D-AUDIT-airac-cycle-tracking`** — typed `Aerodrome.airacCycle:
  AiracCycle` field for staleness checking. Practice-scout flagged this
  per OpenScope/EuroScope precedent. CTR boundaries change rarely
  (multi-year cadence) so not urgent. Future authoring pass.
- **LJMB CTR polygon transcription** — docs-scout couldn't bot-fetch
  Slovenia eAIP (403/404). LJMB ships at the ICAO 5 NM floor; future
  authoring pass transcribes the actual AD 2.17 polygon to author a
  meaningful `ctrApproximationRadiusNauticalMiles`.
