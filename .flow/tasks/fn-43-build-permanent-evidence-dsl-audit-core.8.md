# fn-43-build-permanent-evidence-dsl-audit-core.8 Review and red-team permanent evidence DSL implementation

## Description
Run completion review and red-team before closing the implementation.

The review must explicitly look for cut corners, hidden complexity, false
confidence, raw-string leakage, weak source validation, broad expected gaps,
brittle selectors, unreproducible fuzzing, and report holes.

Do not close the epic with a known staff-engineer-catchable issue unless it is
fixed or loudly tracked in `.plan`.

## Acceptance
- [ ] Focused evidence DSL tests pass.
- [ ] Focused verification command is recorded in the done summary.
- [ ] `./gradlew detekt` passes.
- [ ] Report-artifact assertions are included in verification, not only behavioural assertions.
- [ ] Review findings are fixed or recorded in `.plan` with rationale.
- [ ] Review confirms public test call sites remain simple.
- [ ] Review confirms reports are honest enough to reproduce source/golden evidence.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
