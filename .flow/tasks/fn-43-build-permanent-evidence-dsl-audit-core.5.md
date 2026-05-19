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
Implemented typed generated sample support and the first pure-protocol generated readback slice. Added deterministic heading domains with seed/count/index/value/partition metadata, generatedProtocol authoring support, structural readback samples carrying generated metadata, and a negative omitted-readback-atom case that reports as a structural failure with reproduction metadata.
## Evidence
- Commits: this commit
- Tests: nix-shell --run './gradlew :sim:jvmTest --tests "*.EvidenceDomainsTest" --tests "*.EvidenceSelectorTest" --tests "*.EvidenceDslTest" --tests "*.EvidenceFactsTest" --tests "*.EvidenceSourceCatalogTest"', nix-shell --run './gradlew detekt', git diff --check
- PRs:
