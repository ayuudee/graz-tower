# fn-33-icao-9432-section-by-section.4 Synthesize workflow findings and next recommendations

## Description
Close the spike by synthesizing what the single-source workflow taught us. The report should decide whether to continue source-by-source, introduce a scenario builder, change trace-evidence modeling, or split future work differently.
## Acceptance
- [ ] Synthesis states whether the section-by-section workflow should continue.
- [ ] Synthesis recommends the next source/workflow shape and why.
- [ ] Findings cover scenario builder value, source-unit evidence modeling, code-only fixture ergonomics, and expected scale.
- [ ] Flow task evidence links to inventory, ledger, tests, verification, and report artifacts.
## Done summary
Synthesized the FN33 ICAO 9432 workflow. Recommendation: continue source-by-source, but use the ledger as triage/traceability rather than a blind test queue; add direct source-unit trace evidence before scaling; next useful simulator slice is taxi/runway or takeoff, and a separate phraseology-rendering/linting spike is needed for wording rules. Chose egast-vfr-extracted as the contrasting second source.
## Evidence
- Commits:
- Tests:
- PRs: