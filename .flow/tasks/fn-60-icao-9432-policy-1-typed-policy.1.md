# fn-60-icao-9432-policy-1-typed-policy.1 Build policy matrix and movement manifest

## Description
Build the POLICY-1 source-unit policy matrix before implementation. The matrix must be overlap-aware and start from implementation_blocker_manifest.csv, not raw blocker counts. It must list green-targeted, remain-blocked, and untouched source units; policy owner; modality; configured alternatives; evidence needed for the configured branch; wrong-path evidence; and what is not proven universally.
## Acceptance
- [ ] Policy matrix artifact exists under research/tools/requirements-spike/quality/icao9432_programme/.
- [ ] Matrix covers every POLICY-1 / named policy row selected for fn-60 from implementation_blocker_manifest.csv.
- [ ] Exact movement manifest names green-targeted, remain-blocked, and untouched units.
- [ ] Anti-overcoverage guard states that policy enum/default existence cannot move a source unit green.
- [ ] Review considerations cover FP/type safety, test architecture, impact, and operational correctness.
## Done summary
Built fn60_policy_matrix.md from the overlap-preserving blocker manifest. It names two green-targeted source units, marks the remaining policy rows blocked or untouched, and includes anti-overcoverage and review considerations.
## Evidence
- Commits:
- Tests:
- PRs: