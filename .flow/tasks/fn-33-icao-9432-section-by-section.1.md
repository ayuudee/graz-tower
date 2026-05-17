# fn-33-icao-9432-section-by-section.1 Build ICAO 9432 section inventory and ledger

## Description
Generate a mechanical inventory and initial progress ledger for all accepted `icao9432-extracted` source units. The ledger is the accounting mechanism for the whole spike: every source unit must appear once before any section can be called covered, blocked, or out of simulator scope.
## Acceptance
- [ ] Inventory is generated from accepted `icao9432-extracted` records, not hand-counted.
- [ ] Ledger contains all 166 accepted source units exactly once, grouped by the 23 sections.
- [ ] Ledger schema records section id, source-unit id, title/summary, status, test/evidence target, and notes.
- [ ] At least one mechanical validation command proves inventory and ledger counts match.
## Done summary
Generated the FN33 ICAO 9432 inventory and initial ledger from accepted registry records. The ledger has 166 unique source-unit rows and the section inventory has 23 section rows. Added README documentation with schema, classification statuses, validation evidence, and first-slice candidates.
## Evidence
- Commits:
- Tests:
- PRs: