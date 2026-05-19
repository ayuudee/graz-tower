# fn-43-build-permanent-evidence-dsl-audit-core.8 Review and red-team permanent evidence DSL implementation

## Description
Run completion review and red-team before closing the implementation.

The review must explicitly look for cut corners, hidden complexity, false
confidence, raw-string leakage, weak source validation, broad expected gaps,
brittle selectors, unreproducible fuzzing, and report holes.

Do not close the epic with a known staff-engineer-catchable issue unless it is
fixed or loudly tracked in `.plan`.

## Acceptance
- [x] Focused evidence DSL tests pass.
- [x] Focused verification command is recorded in the done summary.
- [x] `./gradlew detekt` passes.
- [x] Report-artifact assertions are included in verification, not only behavioural assertions.
- [x] Review findings are fixed or recorded in `.plan` with rationale.
- [x] Review confirms public test call sites remain simple.
- [x] Review confirms reports are honest enough to reproduce source/golden evidence.

## Done summary
Reviewed and red-teamed the permanent evidence DSL implementation; fixed the
selector constructor visibility; recorded broad sim red state as `SIM-RED-1`;
and wrote the FN43 implementation review artifact.

## Evidence
- Artifact:
  `research/tools/requirements-spike/quality/evidence_mapped_test_harness/fn43_implementation_review_2026-05-19/review.md`
- Focused tests:
  `nix-shell --run './gradlew :sim:jvmTest --tests "*.EvidencePermanentTwentyCaseTest" --tests "*.EvidenceReportWriterTest" --tests "*.EvidenceDomainsTest" --tests "*.EvidenceSelectorTest" --tests "*.EvidenceDslTest" --tests "*.EvidenceFactsTest" --tests "*.EvidenceSourceCatalogTest"'`
- Static analysis: `nix-shell --run './gradlew detekt'`
- Whitespace: `git diff --check`
- Broad smoke: `nix-shell --run './gradlew :sim:jvmTest'` failed outside FN43
  in reactive/emergency goldens and is tracked as `.plan` item `SIM-RED-1`.
