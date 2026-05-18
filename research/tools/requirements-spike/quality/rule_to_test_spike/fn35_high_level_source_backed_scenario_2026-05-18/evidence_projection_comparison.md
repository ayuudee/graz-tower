# FN35 evidence projection comparison

## Question

What is the best disposable evidence shape while we are still searching for the
right strategy?

## Compared Options

| Option | Result |
| --- | --- |
| Test-only source evidence projection | Best next strategy |
| Annotate existing goldens directly | Useful later, too much coupling now |
| Standalone conformance DSL | Useful only if it wraps high-level scenarios |
| Production `DecisionTrace.sourceUnits` | Probably eventual shape, premature now |

## Test-only source evidence projection

This is the best current choice. It lets a high-level scenario cite source units
without committing production runtime types to an experimental trace model.

What worked in the taxi scenario:

- The test ran the real sim path from parked aircraft to taxi clearance, ready
  report, and line-up permission.
- The source units were cited at scenario level, not as isolated unit-test
  comments.
- The ledger could distinguish `covered` from `partially_covered`.
- The validator makes source ids non-decorative: cited ids must exist, and
  covered/partial ledger rows must be backed by citations.

Failure modes:

- The evidence still lives in tests, not runtime traces.
- Reusing setup from goldens can become noisy if not factored later.
- A scenario can still overclaim unless the ledger has partial statuses and
  review discipline.

Throwaway cost: low. Delete the helper/scenario tests and keep the lessons.

## Annotating existing goldens

This is attractive because G0/G1/G2/G3 already exercise believable behavior.
But annotating them now would make the experimental evidence model look more
permanent than it is.

Failure modes:

- Goldens become overloaded with regulatory bookkeeping.
- A broad golden can cite too many source units and hide which exact behavior
  was exercised.
- Review becomes harder: failures may be mission regressions, evidence
  regressions, or both.

Throwaway cost: medium. Once annotations live inside goldens, deleting or
reshaping them is more intrusive.

## Standalone conformance DSL

The FN31/FN33 code-only DSL worked for low-level probes, but by itself it
encourages unit-test-shaped thinking. It is worth keeping only if the DSL names
high-level scenarios and enforces source citations around those scenarios.

Failure modes:

- Easy to drift into protocol helper assertions.
- Easy to make green tests that do not exercise ATC behavior.

Throwaway cost: low to medium.

## Production `DecisionTrace.sourceUnits`

This is probably the right end state if source-unit evidence becomes part of
the product. But it is too early. We do not yet know evidence granularity:
rule-level, instruction-level, scenario-level, or post-hoc proof-level.

Failure modes:

- Production trace shape ossifies around a spike.
- Runtime traces get polluted with test-only coverage concerns.
- Every controller rule becomes a citation-maintenance task before the scenario
  strategy is proven.

Throwaway cost: high.

## Recommendation

Keep experimenting with test-only source evidence projection plus mechanical
validation. Build two or three high-level scenario slices before touching
production trace evidence. The next candidates should be:

1. taxi/runway crossing or hold-short behavior;
2. final/long-final reporting and landing clearance;
3. handoff/contact-frequency behavior.

Only after those slices should we decide whether source-unit evidence belongs
inside production `DecisionTrace`.
