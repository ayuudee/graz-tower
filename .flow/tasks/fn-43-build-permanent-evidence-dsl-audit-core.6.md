# fn-43-build-permanent-evidence-dsl-audit-core.6 Emit durable Markdown and JSON evidence reports

## Description
Emit durable human and machine-readable evidence reports.

Build Markdown and JSON report output for the suite. The report is part of the
test value, not a debug string.

Records must include suite/scenario id, case id, basis, source refs, samples,
generated sample metadata, outcome, typed gap ids, fact ids supporting
pass/fail, claim kind, and applicability/activation/adequacy for every
source-backed case.

## Acceptance
- [ ] Markdown report is emitted deterministically.
- [ ] JSON report is emitted deterministically.
- [ ] Tests assert report records include source refs, samples, claim kinds, applicability, activation, adequacy, outcomes, fact ids, and typed gaps.
- [ ] Reports fail or mark typed vacuity/gap when a source-backed case has no activation.
- [ ] Reports distinguish structural readback checks from sim behaviour checks.
- [ ] Reports include generated-domain reproducibility metadata.
- [ ] No in-memory-only report path is treated as sufficient.

## Done summary
Implemented durable Markdown and JSON evidence report output. Reports are written to disk and include suite/scenario ids, case ids, claim kind, source refs, samples, generated reproduction metadata, outcome, activation fact ids, derived applicability/activation/adequacy, and typed expected-gap metadata. Tests parse emitted JSON and compare deterministic file content to the formatter output.
## Evidence
- Commits: this commit
- Tests: nix-shell --run './gradlew :sim:jvmTest --tests "*.EvidenceReportWriterTest" --tests "*.EvidenceDomainsTest" --tests "*.EvidenceSelectorTest" --tests "*.EvidenceDslTest" --tests "*.EvidenceFactsTest" --tests "*.EvidenceSourceCatalogTest"', nix-shell --run './gradlew detekt', git diff --check
- PRs:
