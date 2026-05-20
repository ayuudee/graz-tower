# Re-check chunk 01 source units and plan coverage states

## Description
Re-check all 20 chunk-01 ICAO 9432 source units against their accepted registry JSON, then produce the source plan that drives test authoring. This is the guardrail step that keeps the chunk tied to the source corpus instead of drifting into implementation-driven tests.

The source plan must identify candidate tests, expected gaps, phraseology-later units, policy-blocked units, and any non-applicable source records.

## Acceptance Criteria
- [ ] All 20 source ids are listed in `source_plan.*`.
- [ ] Every source id resolves to an accepted registry JSON record.
- [ ] Every source id has at least one exact source quote in the registry record.
- [ ] Every source id has a planned coverage state.
- [ ] Non-behavioural or cross-reference-only units are not overclaimed as test candidates.

## Done summary
Re-checked all 20 chunk-01 source units against accepted registry JSON records with exact source quotes. Produced source_plan CSV/JSON/Markdown. Corrected the Appendix cross-reference unit to not-applicable rather than overclaiming it as a behavioural test candidate.
## Evidence
- Commits:
- Tests:
- PRs: