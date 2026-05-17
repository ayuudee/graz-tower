# fn-33-icao-9432-section-by-section.3 Classify remaining ICAO 9432 sections

## Description
Classify the remaining ICAO 9432 sections and records against the ledger. This task is about breadth and accounting, not pretending everything is executable: covered behavior, new case needs, model gaps, duplicate support, non-sim scope, and domain-review needs must be separated explicitly.
## Acceptance
- [ ] Every remaining ICAO 9432 section gets a section-level simulator relevance status.
- [ ] Every source unit receives one of the agreed classifications.
- [ ] Existing tests/goldens are referenced where they already cover the behavior.
- [ ] Blockers and non-scope decisions are explained, not hidden.
## Done summary
Classified all remaining ICAO 9432 accepted source units. No records remain pending_classification. The final ledger counts are covered=4, duplicate_support=1, new_case_needed=43, blocked_by_existing_red=3, blocked_by_model_gap=41, needs_domain_review=59, not_sim_scope=15. Added a classification report describing section outcomes and existing-test overlap without overstating coverage.
## Evidence
- Commits:
- Tests:
- PRs: