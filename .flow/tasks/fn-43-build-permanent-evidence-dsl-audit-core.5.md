# fn-43-build-permanent-evidence-dsl-audit-core.5 Implement typed sample domains and generated protocol slice

## Description
Add typed domain/sample support behind the facade.

Build:
- example samples;
- representative samples;
- one deterministic generated protocol domain with seed, count, sample index,
  sample value, and partition metadata.
- non-trivial partition coverage for that domain;
- at least one omitted-field / negative readback check in the generated
  protocol slice.

Use a pure protocol readback family first. Do not fuzz full sim scenarios in
this task.

## Acceptance
- [ ] Generated samples are deterministic from seed/count.
- [ ] Generated sample metadata is available to reports.
- [ ] One protocol readback case runs over generated samples with non-trivial partitions.
- [ ] One omitted-field / negative readback case is reported with generated or representative samples.
- [ ] A generated failure would be reproducible from reported seed and sample index.
- [ ] Report metadata includes domain name, seed, count, sample index, value, partition, source id, case id, outcome, and supporting fact ids.
- [ ] The public call site says domain/sample intent without exposing generator mechanics.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
