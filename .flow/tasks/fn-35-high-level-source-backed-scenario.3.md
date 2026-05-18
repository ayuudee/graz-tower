# fn-35-high-level-source-backed-scenario.3 Spike high-level taxi runway source-backed scenario

## Description
TBD

## Acceptance
- [ ] Scenario is high-level, believable, and minimal.
- [ ] Scenario exercises a system path rather than only `requiredReadbackAtoms` or another protocol helper.
- [ ] Scenario cites the relevant `taxi_4_4_en` source units.
- [ ] If blocked, the blocker is documented loudly with the exact missing harness/model capability.
## Done summary
Added a high-level source-backed LOWG taxi scenario for ICAO 9432 taxi_4_4_en. The scenario runs the real sim path from a parked aircraft through taxi clearance, ready report, and line-up permission; it asserts the first departure taxi clearance has a holding-point clearance limit for the active runway and that runway-use permission follows the ready report. Updated the FN33 ledger for one covered and one partially-covered taxi source unit.
## Evidence
- Commits:
- Tests:
- PRs: