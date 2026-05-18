# FN35 synthesis: high-level source-backed scenario strategy

## Verdict

The high-level scenario direction is better than the low-level FN33 readback
probe. Keep going wide with this shape.

The current best strategy is:

1. keep the source-unit ledger;
2. validate citations mechanically;
3. write believable minimal scenarios through the simulator/controller path;
4. mark coverage honestly as `covered` or `partially_covered`;
5. keep evidence test-only until the shape survives several slices.

## What to throw away later

- The low-level `Icao9432ReadbackConformanceSpec` style should not become the
  main approach. It was useful for learning but too narrow.
- The duplicate `SourceBackedScenario` / `SourceBackedBehaviorCase` helpers
  should be collapsed or deleted once a clean strategy exists.
- The JSONL ledger patching should become generated/validated rather than
  hand-edited.

## What to keep for the next experiment

- `partially_covered` is essential. Some source units are broad, and a
  scenario often exercises only one facet.
- Source citation validation is essential. Without it, source ids are just
  decorative strings.
- The high-level taxi scenario is the right kind of test: real fixture, real
  sim path, minimal assertion surface, regulatory citations attached.

## Red-team result

The approach can still become compliance theater if broad scenarios cite too
many source units. The antidote is strict ledger discipline:

- broad source unit + narrow scenario = `partially_covered`;
- source unit not cited by a scenario/test = not covered;
- existing golden overlap is not coverage unless evidence is bound;
- review-only/training sources stay support material until reconciled against
  primary sources.

## Next spike

Build one more high-level source-backed scenario, preferably either:

1. runway crossing / hold-short behavior from `taxi_4_4_en`, because it extends
   the current taxi slice into an explicit runway-safety boundary; or
2. final/long-final reporting from `final_approach_landing_4_7_en`, because it
   tests whether the strategy works for airborne reporting and landing
   clearance timing.

Do not start phraseology rendering yet. Do not start adversarial model work yet.
Do not migrate production `DecisionTrace` yet.

## Review considerations

### FP / type safety

The validator is strict enough for the spike: source ids must exist and covered
ledger rows must have citations. A production version should replace regex
scanning with a generated typed source-unit index.

### Test architecture

The successful new scenario uses a full sim run and asserts only the relevant
regulatory wall: taxi clearance has a holding-point limit before runway-use
permission. This matches the testing standards better than protocol helper
assertions.

### Impact

The new strategy remains disposable. Production code is untouched; changes are
test/research artifacts and Flow state.

### Operational correctness

The scenario cites ICAO 9432 source units. It does not rely on EPPLS training
material for primary operational doctrine.
