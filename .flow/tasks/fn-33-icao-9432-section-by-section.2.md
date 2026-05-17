# fn-33-icao-9432-section-by-section.2 Implement first section-slice conformance cases

## Description
Choose the first representative ICAO 9432 section slice after the ledger exists, then implement honest Kotlin conformance cases for that slice. Prefer a slice that exercises real simulator/controller behavior and evidence traceability without depending on the known go-around red baseline unless the blocker itself is the useful finding.
## Acceptance
- [ ] First section or section pair is chosen with a written rationale.
- [ ] Executable Kotlin conformance cases are added where honest for that section slice.
- [ ] Cases assert behavior and expected source evidence where current models allow it.
- [ ] Any source-unit evidence modeling gap is made explicit in the report or `.plan`.
- [ ] Focused verification is run and its result is recorded.
## Done summary
Blocked:
Paused at the user-requested plan-review checkpoint. No section-slice implementation has been completed yet; reset this task to todo when resuming implementation.
## Evidence
- Commits:
- Tests:
- PRs:
