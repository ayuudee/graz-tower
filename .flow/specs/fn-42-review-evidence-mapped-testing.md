# Review Evidence-Mapped Testing Initiative

## Goal & Context
Deeply review the evidence-mapped testing initiative after FN35-FN41, including source-backed scenarios, SourceUnitSpec, monitor-first design, evidence-mapped facade, twenty-case scaling, and fuzzing direction. Produce isolated red-team and deep-review perspectives, then synthesize a final recommendation.

## Architecture & Data Models
Review the current spike code and artifacts: `SourceUnitSpec`, `EvidenceMappedSpikeHarness`, `EvidenceMappedTwentyCaseSpikeTest`, FN39 monitor proposal, FN40/FN41 reviews, `.plan` follow-ups. No new production implementation is expected.

## API Contracts
The review must assess whether there is a credible road to tests that are simple and legible at the call site while harness internals absorb complexity. It must address fuzzing as typed sample/domain expansion rather than public ceremony.

## Edge Cases & Constraints
Preserve isolated review perspectives before synthesis. Do not treat current spike code as final. Be explicit about failure modes, anti-case, and what would falsify the direction.

## Acceptance Criteria
- [ ] Isolated deep review completed.
- [ ] Isolated red-team review completed.
- [ ] Synthesis document weighs both contexts and the local assessment.
- [ ] Backlog updated for any next-step recommendation.
- [ ] Flow epic validated and committed.

## Boundaries
Review only unless small documentation/backlog changes are needed. Do not refactor the harness in this epic.

## Decision Context
The likely direction is evidence-mapped tests over a `SimObservation` anti-corruption port, with terse call-site DSL, typed source catalog, split protocol/sim evidence entry points, and bounded sample/fuzz domains. This review should try to break that conclusion before it becomes the next implementation stream.

## Review considerations

### FP / type safety
Assess whether typed outcomes, typed source refs, typed domains, and total query helpers are sufficient. Identify places string ids or nullable observation access undermine the direction.

### Test architecture
Assess whether this remains high-level and standard-compliant, and whether low-ceremony tests can coexist with strong evidence/reporting.

### Impact
Assess coupling, helper sprawl, corpus coverage, reports, maintenance cost, and migration path from spike code.

### Operational correctness
Assess whether regulatory claims remain source-cited and whether phraseology/semantic claims are separated.
