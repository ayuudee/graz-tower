# fn-31-cited-rule-to-test-exploration-spike.4 Select cited rule slice and build coverage matrix

## Description
Select a small, representative slice of accepted source units from the curated registry and classify them by operational family, simulator relevance, and likely testability. Build a coverage matrix that ties each selected source unit to citation/provenance, normalized rule claim, current simulator concept/code path where known, existing tests where known, and whether it is executable, manually reviewable, non-testable, or design-blocked.

Prefer one operational family that can support a later vertical slice, plus a few contrast examples that expose non-testable or documentation-only categories. Do not force every source unit into a test shape.

## Acceptance
- [ ] Selected source units are justified by family, confidence, and expected simulator relevance.
- [ ] Coverage matrix records source-unit id, citation/provenance, rule claim, family, simulator concept, code path/test path where known, testability class, confidence, and notes.
- [ ] The matrix explicitly separates executable, manually translatable, design-blocked, non-testable, and uncertain units.
- [ ] Any script or query used to build the matrix fails loudly on missing citation/provenance or malformed registry records.
- [ ] The task recommends the best family for the vertical slice task.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
