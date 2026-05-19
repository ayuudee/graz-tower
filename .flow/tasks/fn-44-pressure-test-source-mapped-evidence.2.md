# fn-44-pressure-test-source-mapped-evidence.2 Add source catalog refs and projection fact vocabulary

## Description
Add typed catalog refs for the selected source units and the smallest evidence fact vocabulary needed by the design note.

Keep the public authoring surface stable. If new helpers are added, they must hide fact/provenance details and read like source-unit intent. Do not introduce a generic scenario builder.

## Acceptance
- [ ] Catalog refs validate against the local source registry.
- [ ] New fact payloads/classifications are total and sealed/enum-backed where appropriate.
- [ ] Source cases still fail when they cite sources but activate no evidence facts.
- [ ] Tests cover catalog validation and pure projection/fact semantics.
- [ ] Focused evidence tests pass.
- [ ] `nix-shell --run './gradlew detekt'` passes.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
