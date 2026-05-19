# fn-43-build-permanent-evidence-dsl-audit-core.7 Re-port FN41 twenty-case suite

## Description
Re-port the FN41 twenty-case suite through the permanent-facing facade.

This is the main acceptance wall. The new suite should preserve or improve the
readability of `EvidenceMappedTwentyCaseSpikeTest` while producing stronger
audit records.

Do not expand to new source areas in this task.

## Acceptance
- [ ] The 12 FN41 protocol/readback cases are re-ported.
- [ ] The LOWG source/golden/invariant cases are re-ported.
- [ ] The two expected-gap cases use typed gap ids.
- [ ] The two expected-gap cases report affected source refs, missing concept, closure trigger, and tracked backlog/deferment link.
- [ ] The new call sites contain no raw source ids, no fact/provenance/report plumbing, no manual trace scans, and no monitor vocabulary.
- [ ] The new call sites are at least as simple as FN41's spike call sites by review.
- [ ] Focused tests assert both behaviour and report content.

## Done summary
Re-ported the FN41 twenty-case suite through the permanent evidence facade: 12 structural protocol/readback cases, four LOWG source-ordering cases, one golden, one invariant, and two typed expected gaps. Added an aircraft-summary DSL helper for the golden case. The suite uses typed catalog refs only, asserts activation/gap discipline, writes a durable report, and verifies typed gap metadata includes affected refs and FN43-GAP tracking.
## Evidence
- Commits: this commit
- Tests: nix-shell --run './gradlew :sim:jvmTest --tests "*.EvidencePermanentTwentyCaseTest" --tests "*.EvidenceReportWriterTest" --tests "*.EvidenceDomainsTest" --tests "*.EvidenceSelectorTest" --tests "*.EvidenceDslTest" --tests "*.EvidenceFactsTest" --tests "*.EvidenceSourceCatalogTest"', nix-shell --run './gradlew detekt', git diff --check
- PRs:
