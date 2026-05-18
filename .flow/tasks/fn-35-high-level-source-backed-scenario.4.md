# fn-35-high-level-source-backed-scenario.4 Compare evidence projection alternatives

## Description
TBD

## Acceptance
- [ ] Compares test-only projection, golden annotation, standalone DSL, and production trace extension.
- [ ] Explicitly recommends which evidence shape to keep experimenting with.
- [ ] Documents failure modes and throwaway cost for each alternative.
## Done summary
Compared evidence projection alternatives. Recommendation: continue with test-only source evidence projection plus mechanical validation; do not annotate existing goldens broadly or add production DecisionTrace.sourceUnits until several high-level slices prove the shape.
## Evidence
- Commits:
- Tests:
- PRs: