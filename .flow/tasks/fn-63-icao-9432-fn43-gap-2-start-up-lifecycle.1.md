# fn-63-icao-9432-fn43-gap-2-start-up-lifecycle.1 Build start-up lifecycle movement manifest and impact plan

## Description
Audit the FN43-GAP-2 source units, D-PF.1 deferment, existing startup protocol/mission/controller surfaces, and decide the minimal honest movement for fn-63. This task is planning plus impact assessment only.
## Acceptance
Exact movement manifest exists. Impact assessment covers D-PF.1 coupling, state/reversal implications, tests, and operational correctness. False-green risks are explicit.
## Done summary
Created and reviewed the start-up lifecycle movement manifest. The audit found one candidate source unit, 95034efc191fa9cd, but it requires D-PF.1 because real coverage needs airport-conditional startup clearance plus an orderable engine-start lifecycle event.
## Evidence
- Commits:
- Tests: .flow/bin/flowctl validate --epic fn-63-icao-9432-fn43-gap-2-start-up-lifecycle, git diff --check
- PRs: