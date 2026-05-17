# fn-31-cited-rule-to-test-exploration-spike.5 Spike code-only requirements conformance DSL

## Description
Prototype a throwaway Kotlin-only conformance-test shape for source-unit evidence. The intent is to test ergonomics, not to land a final production contract. Keep it test-only and reversible.

The spike should answer whether a source-unit case can be expressed naturally in code as a special kind of test: declare source units, bind them to existing trace evidence, and assert both behavior/evidence expectations without YAML or a scenario DSL.

## Acceptance
- [ ] Add a test-only `SourceUnitRef`/conformance-case DSL or equivalent prototype.
- [ ] Demonstrate at least one passing conformance-style test against existing `DecisionTrace` evidence.
- [ ] Demonstrate the failure shape for missing expected evidence.
- [ ] Record what feels good, what feels wrong, and what would need production support if this became real.
- [ ] Keep the prototype isolated and easy to throw away.

## Done summary
Spiked a throwaway Kotlin-only requirements conformance shape. Added test-only SourceUnitRef, SourceUnitEvidenceExpectation, RequirementsConformanceCase, and assertSatisfiedBy(DecisionTrace). Demonstrated a passing case using current DecisionTrace.regulations as proxy evidence and a failure-shape test that names the missing source-unit id. Recorded findings in the FN31 code conformance spike report.
## Evidence
- Commits:
- Tests: nix --extra-experimental-features 'nix-command flakes' develop -c ./gradlew :controller:jvmTest --tests 'xyz.easiersaid.twr.controller.requirements.RequirementsConformanceDslSpec'
- PRs: