# FN31 Code-Only Conformance Spike

## What Was Tried

Added a throwaway test-only Kotlin conformance shape under
`controller/src/commonTest/kotlin/xyz/easiersaid/twr/controller/requirements/`.

The prototype has:

- `SourceUnitRef`, a typed wrapper around accepted registry canonical ids.
- `SourceUnitEvidenceExpectation`, mapping a source unit to today's available
  proxy evidence: `DecisionTrace.regulations`.
- `RequirementsConformanceCase`, a code-declared case with id, family, and
  expected source-unit evidence.
- `assertSatisfiedBy(trace)`, which fails with the missing source-unit ids when
  the observed trace lacks the proxy evidence.

## Why It Is Still Throwaway

Production traces do not yet carry source-unit ids. The prototype therefore uses
`RegulationRef` as a proxy adapter. That feels useful for exploring the test
shape, but it is not the final evidence contract.

If this became real, source-unit refs should be carried directly by the decision
or certification evidence surface instead of inferred from regulation refs in
tests.

## What Felt Good

- Keeping cases in Kotlin feels natural.
- Failure messages can name missing source-unit ids directly.
- No YAML parser, schema, fixture language, or scenario DSL is needed.
- Existing `DecisionTrace` tests can be upgraded incrementally.
- IDE navigation and normal test failure behavior remain intact.

## What Felt Wrong

- The current proxy layer is lossy: a regulation ref can map to more than one
  source unit, and a source unit may be narrower than a regulation section.
- The prototype only checks evidence, not behavior. The intended final shape
  should compose behavior assertions and source-unit evidence assertions in one
  case.
- Putting the helper under `controller` was convenient for compilation, but the
  eventual shared conformance helpers may belong in a test-support module or the
  sim test tree.

## Next Recommendation

Keep the code-only approach, but do not formalize the DSL yet. The next spike
should add direct `SourceUnitRef` evidence to one narrow trace surface, then
convert one existing G3/controller test to assert both behavior and source-unit
evidence through the same case object.

