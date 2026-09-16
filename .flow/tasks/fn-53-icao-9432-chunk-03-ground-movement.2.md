# fn-53-icao-9432-chunk-03-ground-movement.2 Author taxi source-mapped evidence tests

## Description
Author the permanent taxi-side source-mapped evidence tests for the chunk 03
taxi units that can be expressed honestly with the current sim/evidence
surface.

## Acceptance
- [ ] Add typed `ICAO9432.Taxi` source refs for covered taxi units.
- [ ] Add source-mapped tests for testable taxi units.
- [ ] Tests exercise real observed sim/protocol behaviour rather than type-only
      properties unless structural protocol evidence is the correct oracle.
- [ ] No policy-sensitive taxi unit is converted into an unconditional green.
- [ ] The `b9e7fc3605fe616e` "always contains a clearance limit" unit is
      handled structurally across the typed taxi-instruction space, or lands
      covered-red / gap; a single LOWG taxi instance must not green it.

## Done summary
Added chunk 03 taxi clearance-limit covered-red evidence and catalog source refs; the universal taxi limit requirement now fails loudly against the current typed protocol surface.
## Evidence
- Commits:
- Tests: focused chunk03 evidence/model-gap/catalog sim jvm tests, ./gradlew-nix detekt
- PRs: