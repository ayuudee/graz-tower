# fn-75-icao-9432-emergency-1h-pilot-safety.2 Implement pilot safety-doubt source-backed evidence

## Description
Implement focused source-backed evidence for ICAO 9432 §9.1.7's pilot
safety-doubt assistance trigger.

## Acceptance
- [ ] Positive source-backed test proves safety doubt plus explicit assistance
  policy yields a seek-assistance action.
- [ ] Generic emergency labels alone do not satisfy the row.
- [ ] Routine preference, passenger convenience, ATC-originated prompts,
  phraseology-only evidence, and no-policy cases are rejected.
- [ ] `Icao9432ModelGapSourceUnitSpecTest` removes only the target row from
  chunk-08 residual refs.
- [ ] Focused test command passes.

## Done summary
- Task completed
## Evidence
- Commits:
- Tests:
- PRs: