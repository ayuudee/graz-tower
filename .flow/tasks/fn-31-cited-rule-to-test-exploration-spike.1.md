# fn-31-cited-rule-to-test-exploration-spike.1 Attempt cited vertical rule-to-test slice

## Description
Using the coverage matrix recommendation, attempt one narrow end-to-end path from accepted source units to cited rule claims to simulator behavior evidence. The target is to learn whether source units can become useful executable tests, not to maximize test count.

For each selected rule claim, preserve source-unit identity and citation/provenance. Prefer integration-style simulator tests when current infrastructure supports them. Where execution is not yet possible, produce a manual test plan or design-blocked finding rather than silently dropping the case.

## Acceptance
- [ ] A narrow operational family is selected from the coverage matrix and cited.
- [ ] Source units are converted into a small set of normalized rule claims with source-unit ids attached.
- [ ] At least one rule claim is mapped to an executable test, existing test, or explicit simulator/design gap.
- [ ] Any new test has a real behavioral oracle tied to cited source units, not just structural assertions.
- [ ] Non-executable claims are recorded as manual-review or design-blocked findings with reasons.
- [ ] The report states whether this vertical-slice approach should be scaled, modified, or abandoned.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
