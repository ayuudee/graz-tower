# fn-43-build-permanent-evidence-dsl-audit-core.1 Build typed source catalog and gap ids

## Description
Introduce the smallest permanent typed catalog needed by the 20-case suite.
This task owns source and gap identity, not DSL ergonomics.

Build:
- typed source ids / source refs for the ICAO 9432 readback, taxi, and landing
  source units already used by FN41;
- typed gap ids for the current expected-gap cases;
- gap metadata with affected source refs, missing projection/model concept,
  closure trigger, and tracked `.plan` or deferment/backlog link;
- exact validation that catalog ids resolve to accepted source-unit registry
  records or reviewed spike gap records;
- one focused test that prevents ordinary evidence cases from accepting raw
  source-unit strings.

Do not broaden the catalog beyond the current suite.

## Acceptance
- [ ] Typed source catalog entries exist for all FN41 source refs.
- [ ] Typed gap ids exist for the two expected-gap cases.
- [ ] Each typed gap id includes affected source refs, missing concept, closure trigger, and tracked backlog/deferment link.
- [ ] Ordinary `cites(...)` call sites cannot pass raw strings.
- [ ] Validation tests fail loudly for malformed and invented-but-plausible catalog ids.
- [ ] Catalog validation uses exact registry-backed records, not regex/shape checks.
- [ ] No generated or hand-authored catalog entry claims phraseology compliance.

## Done summary
Implemented the typed evidence source catalog for the FN41 source refs, replacing the two spike-only gap ids with governed typed gap ids backed by real ICAO 9432 source refs. Added exact registry validation against accepted candidate JSON records, a raw-string citation API guard, phraseology-overclaim guard, and specific .plan backlog entries for the two projection gaps.
## Evidence
- Commits: this commit
- Tests: nix-shell --run './gradlew :sim:jvmTest --tests "*.EvidenceSourceCatalogTest"', nix-shell --run './gradlew detekt', git diff --check
- PRs:
