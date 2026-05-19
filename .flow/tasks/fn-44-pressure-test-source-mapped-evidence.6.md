# fn-44-pressure-test-source-mapped-evidence.6 Review source-mapped projection design and close epic

## Description
Run completion review and red-team for the FN44 design and implementation.

The review must specifically attack hidden complexity, fake negative evidence, decorative source citations, broad expected gaps, report ambiguity, and DSL verbosity. Fix findings immediately unless they are outside scope and loudly tracked in `.plan`.

## Acceptance
- [x] Focused FN44 tests pass.
- [x] `nix-shell --run './gradlew detekt'` passes.
- [x] Review artifact records what worked, what did not, and whether the design should remain the permanent direction.
- [x] `.plan` is updated for every remaining gap or broad-suite limitation surfaced by the work.
- [x] Flow completion review status is set based on the review result.
- [x] Epic is closed only if no staff-engineer-catchable issue remains untracked.

## Done summary
Reviewed and red-teamed FN44. Verdict: ship the direction with two positive projection cases, two typed transfer gaps, and explicit caution that positives are subcase evidence rather than full source-unit coverage.
## Evidence
- Commits:
- Tests:
- PRs: