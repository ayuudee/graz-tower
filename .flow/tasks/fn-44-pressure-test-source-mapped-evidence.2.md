# fn-44-pressure-test-source-mapped-evidence.2 Add source catalog refs and projection fact vocabulary

## Description
Add typed catalog refs for the selected source units and the smallest evidence fact vocabulary needed by the design note.

Keep the public authoring surface stable. If new helpers are added, they must hide fact/provenance details and read like source-unit intent. Do not introduce a generic scenario builder.

## Acceptance
- [x] Catalog refs validate against the local source registry.
- [x] New fact payloads/classifications are total and sealed/enum-backed where appropriate.
- [x] Source cases still fail when they cite sources but activate no evidence facts.
- [x] Tests cover catalog validation and pure projection/fact semantics.
- [x] Focused evidence tests pass.
- [x] `nix-shell --run './gradlew detekt'` passes.

## Done summary
Added typed transfer source refs and minimal projection fact vocabulary for aerodrome-information, critical-phase, and frequency-transfer evidence. Public DSL helpers intentionally deferred until real cases prove they reduce ceremony.
## Evidence
- Commits:
- Tests:
- PRs: