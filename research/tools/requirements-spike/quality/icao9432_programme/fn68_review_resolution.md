# fn-68 Review Resolution

Reviewer: `01a0b4ab-2e65-7c50-a23a-bd35f85811b8`

## Findings Resolved

1. `23c9...` completeness required `aircraftType`, which ICAO Doc 9432
   §9.2.1.1 does not list.
   - Resolution: removed `aircraftType != null` from the source-unit
     completeness predicate. Aircraft identification is the pilot fact
     `aircraftId`; aircraft type remains supplemental payload metadata.

2. `23c9...` documentation overstated the branch as mandatory field
   completeness.
   - Resolution: manifest and chunk ledgers now describe the covered branch as
     an all-fields-present structured payload representation. They explicitly
     leave field availability, operational omission, partial-message
     compliance, rendered wording, and order out of scope.

## Reviewer No-Issue Areas

- Local projection over `EvidenceFactPayload.PilotTransmissionFact` is
  appropriate and avoids premature global evidence payload surface.
- Negative tests cover PAN PAN not satisfying distress payload, partial MAYDAY
  not satisfying the full structured branch, and routine transmissions not
  projecting as emergency evidence.
- Chunk 08 counts and exact-union accounting remain consistent.
- No hidden priority, silence, SSR, emergency descent, communications-failure,
  or rendered phraseology behaviour was introduced.

## Verification After Resolution

- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432EmergencyClassificationPayloadSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*Source*' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest'`
- `./gradlew-nix detekt`
