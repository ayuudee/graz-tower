# fn-44-pressure-test-source-mapped-evidence.6 Review source-mapped projection design and close epic

## Description
Run completion review and red-team for the FN44 design and implementation.

The review must specifically attack hidden complexity, fake negative evidence, decorative source citations, broad expected gaps, report ambiguity, and DSL verbosity. Fix findings immediately unless they are outside scope and loudly tracked in `.plan`.

## Acceptance
- [ ] Focused FN44 tests pass.
- [ ] `nix-shell --run './gradlew detekt'` passes.
- [ ] Review artifact records what worked, what did not, and whether the design should remain the permanent direction.
- [ ] `.plan` is updated for every remaining gap or broad-suite limitation surfaced by the work.
- [ ] Flow completion review status is set based on the review result.
- [ ] Epic is closed only if no staff-engineer-catchable issue remains untracked.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
