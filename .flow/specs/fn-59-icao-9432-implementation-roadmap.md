# fn-59-icao-9432-implementation-roadmap ICAO 9432 implementation roadmap

## Overview
Plan the next phase after the ICAO Doc 9432 source-unit programme has mapped
all accepted units and authored the first source-mapped coverage wall. The
deliverable is an implementation roadmap, reviewed and red-teamed, that turns
the remaining blockers into executable Flow epics while preserving the firewall
between regulatory tests and simulator implementation work.

## Scope
- Reconcile the 166 accepted ICAO 9432 source units with the remaining blocker
  families: `PHRASE-1`, `POLICY-1`, `FN43-GAP-1`, `FN43-GAP-2`,
  `PUSHBACK-1`, `VEHICLE-1`, and `EMERGENCY-1`.
- Rank the blocker families by mission value, dependency order, and risk of
  false confidence.
- Define child implementation epics at a size where tests can be authored,
  fail for honest reasons, and then be fixed out-of-band.
- Record plan review and red-team findings before child epics are treated as
  executable.
- Do not implement simulator behavior in this epic.

## Approach
1. Synthesize the existing programme map, chunk coverage reports, and expected
   gaps into a blocker-family roadmap.
2. Review the roadmap against Flow, the project commandments, the test
   standards, and the actual mission: a source-mapped regulatory test wall, not
   a verbatim implementation project.
3. Red-team the roadmap for hidden complexity, weak evidence, bad sequencing,
   phraseology brittleness, policy overreach, and accidental coupling to current
   simulator internals.
4. Create executable follow-on epics only after material review and red-team
   findings are either incorporated into the roadmap or encoded as explicit
   acceptance criteria in those child epics.
5. Validate Flow metadata and commit the roadmap artifacts.

## Quick commands
<!-- Required: at least one smoke command for the repo -->
- `./.flow/bin/flowctl validate --epic fn-59-icao-9432-implementation-roadmap`
- `git diff --check`

## Acceptance
- [ ] Roadmap artifact exists under `research/tools/requirements-spike/quality/icao9432_programme/` and accounts for every current blocker family.
- [ ] Roadmap includes a `Review considerations` section covering FP/type
  safety, test architecture, impact, and operational correctness.
- [ ] Plan review and red-team artifacts exist and their material findings are
  resolved or explicitly carried into follow-on epics.
- [ ] Follow-on Flow epics exist for the reviewed implementation sequence, and
  each child epic encodes the resolved review/red-team constraints that apply
  to it.
- [ ] Flow validation and whitespace validation pass.

## References
- `research/tools/requirements-spike/quality/icao9432_programme/README.md`
- `research/tools/requirements-spike/quality/icao9432_programme/proposed_chunk_epics.md`
- `research/tools/requirements-spike/quality/icao9432_programme/*/coverage_report.md`
- `docs/test-standards.md`
