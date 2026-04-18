# Entity-Driven Plates

## Intent

Airport plates should be generated from the same operational world the simulator uses.

The authoritative source for geometry and operational meaning should be the domain entities in
`AviationWorld` / `Aerodrome`, with a smaller publication supplement layer for content that the
current model cannot yet express.

In practice, that means plates should not be translated directly from raw DXF/source data. They
should be translated from a structured airport package first; see
`docs/design/structured-airport-package.md`.

That keeps the plate pack honest:

- if a route is drawn, it should come from a route/circuit/airspace entity
- if a stand is shown, it should come from a stand/apron/taxiway entity
- if a runway/taxiway/circuit relationship is wrong, the plate should expose the same defect the
  simulator would have

## Current honest split

### Entity-driven

These belong in the current world/aerodrome model whenever we can derive them:

- physical geometry points and segments
- runways, taxiways, holding points, stands, aprons
- fixes and VFR route paths
- circuit procedures and joins
- airspace volumes and FIR membership
- frequencies, roles, and basic aerodrome metadata

### Publication supplement

These are still needed as a separate layer because the current domain model does not carry them
cleanly enough:

- published VFR procedure protocol
  - contact-before points
  - omit-report instructions
  - route-end "hold here unless cleared" behavior
  - comm-failure sequences
  - availability notes tied to charted procedures
- briefing pages and narrative operating notes
- plate-only presentation labels and callouts
- chart-specific presentation annotations not yet worth promoting into runtime entities

## Current model gaps

The supplement layer is not just convenience. Some chart content is genuinely missing from the
current core model.

### 1. Remaining published procedure semantics

The runtime model now has first-class `PublishedVfrProcedure` entities, but the
full publication problem is not closed yet.

The runtime side is also now intentionally narrower than the raw LOWG
publication package:

- typed `PlateId` rather than raw plate strings
- typed contact timing rather than ad hoc `beforePoint` / `beforeEntry`
- sealed published point / anchor references rather than nullable products
- one advisory bag plus explicit communication-failure structure, instead of
  top-level note soup and raw status strings

That is deliberate. Raw publication provenance and scaffold status stay in the
structured package, not in `core`.

Remaining gaps:

- some briefing-page and narrative operating content is still not structured
  enough to be runtime-owned
- the deferred hold/loiter semantics are still missing for version-2 style
  procedures
- directional circuit traversal assignment still depends on package/projection
  work rather than a direct runtime import

### 2. Airspace boundary semantics

`AirspaceVolume` now carries explicit optional boundary geometry alongside the
existing point-membership model, which is enough for the current LOWG plate
path to stay honest.

What remains open is not "no boundaries". It is the deeper semantic question of
how much runtime behavior should derive from the boundary itself rather than the
point-membership subprojection.

### 3. Operational sectors

Sector constructs like LOWG `SECTOR WHISKEY` / `SECTOR ECHO` are not just ordinary interior
reporting points and are not well represented as plain `AirspaceVolume` or `VfrRoute` data.

They are procedural boundary-entry constructs with geometry, altitude limits, and entry semantics.

The current runtime model now carries them through `AerodromeAip.operationalSectors`, and the
LOWG current-core candidate projects them there. The remaining question is whether they should
ever become proof-visible or instruction-addressable in FM, not whether runtime may own them.

### 4. Remaining route airspace work

The current runtime model no longer forces one route-wide `airspaceClass`;
`VfrRoute` now carries `VfrRouteAirspaceProfile`.

That does not fully close the LOWG v1 import problem by itself. The current
runtime can now represent `InVolume`, `InClass`, and fully segmented
volume-authoritative route profiles, but LOWG still leaves mixed
boundary-crossing routes unassigned where projecting a profile would require
invented authority. The remaining open question is how far runtime and FM
should reason over those profiles, rather than merely storing them honestly
when available.

### 5. Loiter geometry semantics

The current `OrbitPoint` / `HoldingPattern` shape assumes closed loop paths. That is not the same
as the intended "loiter within a segment/region at an altitude" semantics. This matters for charted
holding constructs, but can be deferred when building a version-1 airport that does not yet need
those holds.

## Near-term working rule

Until the core model grows, use this rule:

1. If a plate element can be expressed by existing world entities, put it there first.
2. If it cannot, keep it in an explicit publication supplement, not as hidden renderer-local logic.
3. Every supplement-only item should be treated as either:
   - a temporary publication-only note, or
   - an explicit domain gap to close later

## Practical consequence

The plate generator should trend toward this dependency order:

1. `AviationWorld` / `Aerodrome` entities for geometry and operational structure
2. publication supplement for protocol, briefing, and chart-only annotations
3. renderer-local defaults only as a last resort

LOWG now shows the intended intermediate step clearly: the strict plate view
model can consume current-core routes/sectors/procedures where they exist, and
fall back to explicit gap reporting or structured-package-only content where the
runtime still does not carry the meaning strongly enough.

That gives us a stable standard for future airports without forcing premature changes to the core
model.

## Anti-Corruption Layer

Do not let the renderer read raw projection documents opportunistically.

Instead:

1. translate the projected entity/world documents into a dedicated plate view model
2. keep that view model entity-only plus explicit gap-report metadata
3. make the renderer consume only that view model

That is the boundary that prevents publication-supplement content or raw source details from
leaking back into the generated plates.

## Current LOWG result

LOWG now has a generated current-core subset and validator report:

- `cad/airports/rendered/lowg/world-candidate.json`
- `cad/airports/rendered/lowg/world-validation-report.json`
- `cad/airports/rendered/lowg/structured-airport-package.json`

That subset intentionally omits the known mismatched pieces, forces one synthetic point-claim
airspace volume, and projects runway-protection holding points only onto the connected `A` spine so
the validator can focus on current-core entity fit.

The result is useful: the projected LOWG subset now validates cleanly in the current model.

That is exactly the kind of boundary this document is aiming for:

- entity-driven where the current model is already honest enough
- supplement-only where the model still loses operational meaning
- explicit reports when a remaining failure is about the model or missing projection work

It also exposes an important caution: a clean validator pass does not automatically mean the model
is semantically complete. LOWG still keeps briefing/local-regulation content in the publication
supplement, and its disconnected side-runway holding candidates are still being kept out of the
current-core subset because of the present stand-reachability rule.

LOWG now also has that anti-corruption step in code:

- `bin/airport_structured_package.py` builds the explicit pre-core structured airport package
- `bin/project_structured_airport_package.py` materializes that package for inspection
- `bin/airport_plate_view_model.py` translates the structured LOWG airport package plus the
  validated current-core subset into a plate-facing view model
- `bin/render_airport_plate.py` now renders from that view model rather than reading the raw
  projection/world documents directly
- the current pack is intentionally entity-only, so sparse pages and explicit gap notices are now
  treated as the honest output
- the renderer no longer carries dead raw-scene / raw-projection fallback helpers that could bypass
  that view-model boundary

The LOWG plate pack now reflects that split directly:

- `AD-1`, `AD-6`, and `AD-2` draw their aerodrome layers from the validated current-core subset
- the summary and aerodrome sheets show the relevant fit assumptions in-page
- the procedure sheets now read from the current-core subset where the new
  route/sector/procedure entities exist, and remain sparse only where LOWG is
  still blocked on circuit projection or deferred hold semantics
