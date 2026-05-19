# fn-43-build-permanent-evidence-dsl-audit-core.4 Implement selector algebra over evidence facts

## Description
Implement the first permanent evidence selector algebra over provenance-bearing
facts.

Build only the selectors required by the 20-case suite plus one small
absence/count primitive if it can be kept clean.

Required direction:
- `first<T>(aircraft)` and report equivalent;
- stable-order `before`;
- explicit repeated-event semantics through `nth`, `exactly`, or another typed
  primitive if used;
- no hidden first-match semantics in helpers that appear to express stronger
  claims.
- source-case applicability/activation accounting over selected evidence facts.

## Acceptance
- [ ] `before` uses stable fact order, not timestamp-only comparison.
- [ ] Missing evidence returns typed `UnexpectedGap` / failure, not `null`.
- [ ] Repeated-event selection is explicit in the API.
- [ ] At least one test demonstrates equal-time or repeated-event ordering cannot false-pass silently.
- [ ] Every source-backed case can report activation facts or an explicit typed vacuous/gap reason.
- [ ] No selector inspects raw sim internals.

## Done summary
TBD

## Evidence
- Commits:
- Tests:
- PRs:
