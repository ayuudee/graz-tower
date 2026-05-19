# fn-44-pressure-test-source-mapped-evidence.4 Implement critical-phase transmission evidence case

## Description
Implement the ICAO Doc 9432 §4.1.2 critical-phase transmission restraint case against explicit phase-window and transmission-necessity evidence.

This task must not equate an empty list of transmissions with compliance unless the critical-phase window itself is observed. Missing windows are failures or typed gaps. Safety-necessary transmissions must be classified explicitly if they are accepted inside the window.

## Acceptance
- [x] Public test cites `icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4` through a typed catalog ref.
- [x] Case proves no routine controller transmissions during observed critical windows, or remains a narrow typed expected gap.
- [x] Missing window evidence cannot pass silently.
- [x] Report output distinguishes negative/window evidence from missing evidence.
- [x] Focused FN44 tests pass.

## Done summary
Projected critical-phase windows from LOWG SimTrace for TakeoffRoll and LandingRoll, then added a source-mapped case proving no routine controller transmissions during observed windows. The DSL fails if no window evidence exists.
## Evidence
- Commits:
- Tests:
- PRs: