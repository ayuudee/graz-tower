# fn-60-icao-9432-policy-1-typed-policy ICAO 9432 POLICY-1 typed policy substrate

## Overview
Second executable epic in the roadmap. Create explicit typed policy concepts so
source-mapped tests can bind discretionary procedures without turning one valid
local/controller choice into universal law.

## Scope
- Build the per-source-unit policy matrix before any policy-green-out.
- Cover source id, modality, policy owner, allowed configured alternatives,
  evidence for the configured branch, and what is not proven universally.
- Introduce typed policy concepts only where source-unit evidence requires
  them.
- Keep policy values closed and cited; do not use strings or unreviewed
  defaults as doctrine.

## Approach
Start with the policy matrix and a minimal policy kernel. Then select a narrow
proof set of policy-blocked units and write high-level source-mapped tests that
prove configured branches. Do not move a unit green merely because a policy enum
exists.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./gradlew-nix :sim:jvmTest --tests '*.Icao9432*SourceUnit*'`
- `./.flow/bin/flowctl validate --epic fn-60-icao-9432-policy-1-typed-policy`
- `git diff --check`

## Acceptance
- [ ] Exact source-unit movement manifest exists: green-targeted,
  remain-blocked, untouched, and anti-overcoverage guard.
- [ ] Policy matrix exists for every targeted `POLICY-1`/policy-concept unit.
- [ ] Policy types are closed and cited; no default policy is treated as
  universal law.
- [ ] Tests prove configured policy branches and explicitly state what they do
  not prove universally.
- [ ] Review considerations cover FP/type safety, test architecture, impact,
  and operational correctness.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_roadmap.md`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_blocker_manifest.csv`
- `research/tools/requirements-spike/quality/icao9432_programme/implementation_plan_review.md`
