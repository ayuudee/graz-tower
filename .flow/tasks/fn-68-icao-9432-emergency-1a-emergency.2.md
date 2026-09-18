# fn-68-icao-9432-emergency-1a-emergency.2 Implement emergency classification and payload evidence

## Description
Implement the approved fn-68 evidence surface. Reuse existing protocol
emergency transmission types where honest, but add closed evidence/projection
support as needed so source-mapped tests can distinguish distress, urgency, and
emergency message payload fields without relying on ordinary VFR traces or
rendered text.

## Acceptance
- [ ] Typed evidence distinguishes distress from urgency.
- [ ] Typed evidence exposes emergency message payload fields needed by the
  selected source units.
- [ ] Ordinary non-emergency pilot transmissions cannot satisfy emergency
  source-unit claims.
- [ ] Source-backed tests move only the declared source units/split branches.
- [ ] Existing chunk 08 exact-union/model-gap specs are updated so moved rows
  are not still counted as gaps.
- [ ] Focused tests pass.

## Done summary
Implemented fn-68 source-backed emergency classification and all-fields-present distress payload representation using a local projection over existing PilotTransmissionFact. Updated chunk 08 gap grouping, coverage docs, and blocker manifest; resolved review findings on aircraft type and 23c9 wording.
## Evidence
- Commits:
- Tests: ./gradlew-nix :sim:jvmTest --tests '*.Icao9432EmergencyClassificationPayloadSourceBackedTest' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest', ./gradlew-nix :sim:jvmTest --tests '*.Icao9432*Source*' --tests '*.Icao9432ModelGapSourceUnitSpecTest' --tests '*.EvidenceSourceCatalogTest', ./gradlew-nix detekt
- PRs: