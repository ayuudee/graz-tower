# ICAO 9432 readback 2.8.3 slice

## Selection rationale

`readback_2_8_3_en` was selected as the first executable slice because it is
small, high-signal, and maps to existing simulator/protocol behavior without
depending on the known FN31 go-around red baseline. It tests the central
workflow question: can source units name the evidence behind real executable
behavior?

## Executable cases

Added:

`controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/requirements/Icao9432ReadbackConformanceSpec.kt`

The cases cite ICAO 9432 source-unit ids directly through
`SourceBackedBehaviorCase` and assert real behavior through
`requiredReadbackAtoms`.

Covered source units:

- `icao9432-extracted::readback_2_8_3_en::15940532b37f8528`
  - Runway operations require structural readback atoms: line-up/enter, land,
    take off, hold short, cross, and backtrack.
- `icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60`
  - Runway in use, pressure setting, SSR/squawk, level, heading, speed, and
    transition level require structural readback atoms.
- `icao9432-extracted::readback_2_8_3_en::58594a8ee6243296`
  - Route clearances require a route readback atom.
- `icao9432-extracted::readback_2_8_3_en::4b6ece953649da07`
  - Taxi and other clearances require clear readback/acknowledgement; the
    executable slice pins taxi clearance readback atoms.

## Non-executable records in this section

- `icao9432-extracted::readback_2_8_3_en::fe3b04ca9c3384d9`
  - Classified as `duplicate_support`: the route-clearance-not-runway-authority
    point is enforced by protocol types (`ClearedTo` is route, not runway).
    A runtime assertion was rejected because Kotlin proves the negative type
    check statically.
- `icao9432-extracted::readback_2_8_3_en::36e6ad16cffe8726`
  - Classified as `blocked_by_model_gap`: route-clearance timing before
    start-up is not represented in this conformance slice.
- `icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2`
  - Classified as `blocked_by_model_gap`: slow/clear delivery and complicated
    taxi workload are communication/workload policy, not currently simulator
    state.
- `icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649`
  - Classified as `blocked_by_model_gap`: needs phraseology rendering/linting
    to distinguish `TAKE OFF` from `DEPARTURE` / `AIRBORNE` outside
    takeoff-clearance contexts.

## Verification

```sh
nix --extra-experimental-features 'nix-command flakes' develop -c ./gradlew \
  :controller:jvmTest \
  --tests 'xyz.easiersaid.twr.controller.requirements.Icao9432ReadbackConformanceSpec'
```

Result: `BUILD SUCCESSFUL`.

## Finding

The code-only fixture shape works for source units that already map to typed
protocol behavior. It is honest about its limits: timing/workload constraints
and literal phraseology claims need either richer simulator state or a separate
phraseology-rendering/linting layer. Direct `SourceUnitRef` citation is more
useful than forcing everything through `RegulationRef`.
