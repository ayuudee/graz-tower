# LOWG Entity-Fit Audit

## Scope

This is a fit pass against the current domain entities in:

- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldModel.kt`
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/ProcedureAndAirspaceModel.kt`
- `core/src/commonMain/kotlin/xyz/easiersaid/twr/core/world/WorldValidation.kt`

The goal is not to change the model yet. The goal is to see how far the current LOWG authoring
package can be projected into `AviationWorld` / `Aerodrome`, and to make the remaining gaps
explicit.

## Summary

LOWG is now far enough along that a version-1 entity projection is realistic.

That statement is now backed by a real validator pass, not just a paper fit. The generated
current-core subset in `cad/airports/rendered/lowg/world-candidate.json` now validates cleanly in
the current `AviationWorld` model. `cad/airports/rendered/lowg/world-validation-report.json`
currently reports `0` issues and `0` structural issues.

That boundary currently depends on two explicit current-core assumptions:

- low-level LOWG airspace membership is projected as a 2D plan-view point assignment against the
  worked CTR/TMA geometry, not as altitude-aware polygon reasoning
- runway-protection holding points are projected only onto the connected `A` taxiway spine using
  the snapped `D` marker plus X-Plane sign evidence for `G1/G2`, `X`, and `Y`

The new parking result adds one important observation:

- the authored `NEW_Parking` graph and authored `NEW_Parking_Points` stand set now project cleanly
  as entities once the compiler treats real line-line crossings inside `NEW_Parking` as graph
  splits and treats direct authored touches onto taxiway `A` as shared graph attachment points

That boundary is now visible in the generated plate pack too. `AD-1`, `AD-6`, and `AD-2` render
their aerodrome layers from the validated current-core subset, while the procedure and briefing
pages now intentionally show the missing entity/projection coverage instead of being filled from
the publication supplement.

That renderer boundary has now been tightened further. The plate renderer no longer reads the raw
projection documents directly; it consumes an explicit entity-only plate view model translated by
`bin/airport_plate_view_model.py`. Procedure and briefing pages therefore now expose the missing
entity/projection coverage directly instead of being padded out from the publication supplement.

That split has improved further on the LOWG side. The structured airport package now carries
published VFR procedures as candidate procedure objects, so the PRC / ARR pages can render real
procedure semantics through the strict plate view model without dropping back to raw supplement
data.

That second assumption matters because the richer LOWG bundle still contains disconnected
holding-point candidates on `B`, `C`, `Y`, and `Z`. Under the current validator, importing those
directly would force stand-reachability checks from runway-side/glider-side holding points that do
not currently sit on the apron-connected ground graph.

So the current blocker is no longer apron connectivity or stand reachability. The current open
questions are narrower:

- how honest the holding-point and stand-reachability rules really are for disconnected runway-side
  holds in the longer-term model

What already fits well:

- runways from `apt.dat`
- taxiway centrelines from the hand-authored ground graph
- stands from the authored `NEW_Parking_Points` set
- apron geometry from the authored `NEW_Parking` graph, with direct authored attachments onto
  taxiway `A`
- `SECTOR WHISKEY` and `SECTOR ECHO` as runtime `AerodromeAip.operationalSectors` in the LOWG
  current-core candidate
- published VFR procedures as runtime `AerodromeAip.publishedVfrProcedures` in the LOWG
  current-core candidate
- VFR reporting points as `Fix`
- explicit VFR reporting-point paths as candidate `VfrRoute` input
- runway-specific `CircuitProcedure` entities projected from the shared authored graph
- basic aerodrome metadata, frequencies, and some AIP notes

What does not yet fit cleanly:

- non-runtime special-use surrounding-airspace projection beyond the worked LOWG CTR/TMA class-layer set
- altitude-aware airspace membership beyond the current 2D low-level projection
- the east non-standard hold / loiter semantics

## Direct fit to current entities

### PhysicalGeometry

This fits directly.

LOWG now has enough authored and parsed geometry to populate a first-pass geometry graph:

- ground graph from `cad/airports/lowg.dxf`
- circuit graph from `cad/airports/lowg_circuits.dxf`
- explicit VFR route joins from the LOWG manifest
- runway endpoints from `apt.dat`
- apron-access branch spines from X-Plane `taxiway_A` taxi-route edges

The main caution is ownership, not geometry existence.

### Runway

This fits directly.

Candidate source:

- six directional runway entities from `apt.dat`
  - `16C`, `34C`
  - `16L`, `34R`
  - `16R`, `34L`

The current LOWG pass intentionally uses the full strip and does not model displaced-threshold
asymmetry. That is acceptable for the current version-1 fit.

### Taxiway

This mostly fits, but one component needs to be split before it becomes an honest `Taxiway` set.

Good candidates now:

- `A`
- `B`
- `C`
- `Y`
- `Z`

Important caveat:

- the current tower-side component marked as `A` still contains the runway-end `D` turn
- the entity model assumes each taxiway owns its own path segments cleanly
- so the current mixed `D -> A` cluster is not a final taxiway entity yet; it needs a split pass

The control-axis component is not an entity and should never be imported as one.

### HoldingPoint

This partially fits.

The hand-authored ground markers now carry useful names, but the entity model needs each holding
point tied to:

- a concrete point on a taxiway path
- a `HoldingPointType`
- optionally the runway it protects

LOWG now has enough named markers to start that mapping, but the runway-protection assignments are
not yet systematically encoded.

This is now the decisive nuance in the fit, rather than the decisive blocker.

The generated current-core LOWG subset now passes validation by projecting runway-protection holds
onto the connected `A` spine. That is good enough for a current-core import, but it is not the
same thing as saying the richer hand-authored/disconnected holding candidates already fit the
validator honestly.

LOWG therefore exposes a concrete follow-up question for the model:

- should every holding point really be required to reach every stand through the apron/taxiway
  graph?

For now, the current-core subset says "no change yet; project the connected version and keep the
disconnected holds explicit as non-imported candidate data."

### Stand

This fits directly.

LOWG now has an authored stand-point set in `NEW_Parking_Points` plus a definitive row-order
naming sequence carried in `cad/airports/lowg.manifest.json`.

That gives a direct version-1 import of:

- `38` authored stands
- names taken from the authored parking rows rather than from `apt.dat`
- location type / aircraft-type hints still borrowed from the nearest visible X-Plane stand where
  available

So the stand geometry and identity now fit directly. The only residual compromise is that some
secondary attributes are still inherited from the nearest X-Plane reference stand.

### Apron

This now fits more strongly than before, but still with one explicit bridge.

For LOWG v1, the most honest apron model is:

- the authored `NEW_Parking` internal apron graph
- the authored `NEW_Parking_Points` stand set snapped onto that graph
- projected join paths from taxiway `A` back into the authored graph

What is real:

- the internal apron/parking geometry comes from the authored DXF

What is synthetic:

- the taxiway-`A` rejoin paths are still projected rather than hand-authored

That is good enough for a first `Apron` import, and it is materially better than the older
X-Plane-branch-plus-stand-leadin approximation because the stands now sit on the authored apron
graph itself.

### Fix

This fits directly.

LOWG already has at least these VFR reporting points available from OFMX:

- `GLEISDORF`
- `LASSNITZHÖHE`
- `AUTOBAHN-OST`
- `AUTOBAHN-WEST`
- `GREEN CITY`
- `GRAZ-NORD`
- `KALSDORF`
- `SENDER DOBL`

These should be imported as `Fix`, not left plate-local.

### VfrRoute

This mostly fits geometrically, but not perfectly as the current type stands.

Current explicit candidate routes:

- `GLEISDORF -> LASSNITZHÖHE -> AUTOBAHN-OST -> circuit_ne_entry`
- `SENDER DOBL -> AUTOBAHN-WEST -> GREEN CITY -> GRAZ-NORD`
- `SENDER DOBL -> circuit_sw_entry`
- `KALSDORF -> circuit_se_entry`

The route geometry already matches the current `VfrRoute` shape well enough for version 1.

The current LOWG projection now lands in a mixed but honest state:

- `vfr_southeast_entry_path` and `vfr_southwest_entry_path` project as
  `InVolume(LO585)`
- `vfr_western_corridor_path` projects as segmented `LO585 -> LO0EF_E` with an
  explicit boundary transition point
- `vfr_northeast_entry_path` remains intentionally unassigned in the current
  candidate, because the pre-CTR leg still cannot be mapped to a runtime
  airspace volume honestly under the current low-level slice

So LOWG can now project real runtime `VfrRoute` entities, but the non-runtime
special-use surrounding-airspace story is still intentionally incomplete rather
than guessed.

### CircuitProcedure

This now fits directly for LOWG v1, with one explicit caveat.

The hand-authored circuit drawing remains a shared graph in CAD, but the migration compiler now
projects that graph into explicit runway/direction-specific `CircuitProcedure` entities for:

- `16C` west / `34C` west
- `16C` east / `34C` east
- `16R` west / `34L` west
- `16L` east / `34R` east

The projected procedures now carry:

- explicit `UPWIND`, `CROSSWIND`, `DOWNWIND`, `BASE`, and `FINAL` legs
- runway attachments that genuinely meet the runway geometry
- explicit join anchors `NE`, `NW/AUTOBAHN-WEST`, `SE`, `SW` where those anchors land on the
  projected legs

The remaining limitation is not that LOWG lacks circuit procedures. It is that the current
projection is still LOWG-specific and driven by an explicit traversal/compiler spec for this
shared graph rather than by a generalized airport-agnostic circuit compiler.

### Aerodrome / basic AIP

This partially fits.

The current model can already absorb:

- aerodrome elevation and magnetic variation
- frequencies
- controller roles
- noise-abatement notes
- special instructions

That is enough to support some of the briefing-page content, but not all of it.

## Current genuine gaps

These are not just missing integration work. They are true mismatches between LOWG chart content
and the current entity model.

### 1. Published VFR procedure protocol

LOWG now has structured procedure information that does not belong in bare `VfrRoute` geometry:

- contact Tower before a particular point or sector
- altitude caps on sector entry
- hold at route endpoint unless already cleared
- omit-report instructions on departure
- comm-failure reverse routing / NORDO behavior
- runway-specific availability notes

At the moment this lives only in the LOWG sidecar and plate generator.

### 2. Airspace boundary geometry

`AirspaceVolume` currently stores point membership, not actual boundary geometry.

That means the current model can validate "this point is in this volume", but it cannot honestly
drive the chart boundary lines, CTR cut-outs, or the `SECTOR WHISKEY` / `SECTOR ECHO` sketches.

### 3. Operational sectors

For LOWG, `SECTOR WHISKEY` and `SECTOR ECHO` are not just labels on existing routes.

They are boundary-entry constructs with:

- geometry
- altitude semantics
- relation to the CTR boundary
- specific route-entry meaning

There is no first-class home for that today.

### 4. Route-airspace projection honesty

LOWG now makes it concrete that a published VFR route can cross an airspace transition while still
being one operational route.

`VfrRoute` now supports nullable `airspaceProfile` plus `InVolume`, `InClass`, and
volume-authoritative `Segmented` cases. The remaining gap is no longer the runtime shape itself;
it is the projection work needed to assign LOWG routes honestly under that wider model.

### 5. Loiter / hold semantics

This is intentionally deferred for LOWG v1, but the gap is still real.

The current `HoldingPattern` / `OrbitPoint` formalization does not match the intended loiter-area
semantics. That matters for the east non-standard hold and should be treated as a model gap, not as
"just another path to draw."

## Validation consequences of a first LOWG import

Without changing the core model, a serious LOWG `AviationWorld` projection would still need to
satisfy the current validation rules.

The practical blockers are:

### Split mixed taxiway ownership

The current `D -> A` ground cluster cannot remain one imported `Taxiway` if we want clean segment
ownership and truthful taxiway semantics.

### Promote parking access into apron paths

This is now much narrower than before. The stands already sit on the authored apron graph.

The remaining version-1 requirement is simply that the authored apron graph must rejoin taxiway `A`
through projected or hand-authored connectors. Without those joins, stand reachability will fail.

### Assign all points to airspace volumes

The validator requires every geometry point to belong to some airspace volume. LOWG already has the
metadata, but the current point-to-volume assignment is not built yet.

### Choose final circuit procedures

The shared circuit graph must be projected into actual closed `CircuitProcedure` objects. The
geometry now supports that, but the projection itself is still missing.

### Encode runway-protecting holding points

The named ground markers need to become actual `HoldingPoint` objects with runway associations where
appropriate.

## Plate-by-plate drivability

If we want plates to be driven by entities first and supplements second, LOWG now looks like this.

### Mostly entity-driven already

- `AD-1`
  - runway, taxiway, holding-point, tower, and parachute-area geometry
- `AD-6`
  - runway/taxiway/glider-ground picture
  - glider-ops text still supplemental
- `AD-2`
  - stands, aprons, taxiway `A`, runway context
  - current branch-to-stand connectors are synthetic but still entity-projectable
- `PRC-1`
  - reporting-point path + runway/airspace context
- `ARR-1`
  - reporting-point path + circuit + airspace context
- `PRC-4`
  - west circuit geometry + parachute-area relationship

### Entity-driven map, supplement-driven procedure meaning

- `PRC-2`
  - route/circuit geometry is mostly there
  - sector semantics are not
- `PRC-3`
  - same issue as `PRC-2`
- `ENR-1`
  - surrounding airspace and route lines can be entity-driven
  - reference boxes and transit notes are supplemental

### Still explicitly outside version 1

- IFR procedures
  - the structured package now carries CIFP-derived SID / STAR / approach inventory, full LOWG fix-resolution diagnostics, explicit SID/STAR candidate route compilations, and tower-scope default approach candidates plus a shared `GBG` missed-approach hold candidate
  - the current-core candidate now projects a first IFR subset:
    - the LOWG SID set
    - the LOWG STAR set
    - `VOR RWY 16C`
    - `VOR RWY 34C`
    - `RNP RWY 16C`
    - `RNP RWY 34C`
    - `ILS RWY 34C`
    - `LOWG_GBG_MISSED_HOLD`
  - the remaining IFR boundary is now narrower:
    - `LOC 34C` remains structured-package only
    - richer published minima variants remain outside the current runtime model
- `PRC-5`
  - east traffic circuit can be shown
  - east non-standard hold should remain deferred until the loiter model is fixed
- `AD-3`, `AD-4`, `AD-5`
  - these are mostly publication/briefing pages, not bare world geometry

## What this implies for the next step

Without changing the core model yet, the next honest move is:

1. Build a LOWG v1 entity projection target from the existing authoring package.
2. Keep a small publication supplement beside it for the parts the current model cannot express.
3. Start shifting the plate generator to read:
   - entities first
   - publication supplement second
   - renderer-local defaults last

That gets the plates much closer to being driven by the same world model the simulator will use,
without forcing premature model edits.
