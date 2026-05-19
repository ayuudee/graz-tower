# FN42 Isolated Red-Team Review

Date: 2026-05-19

Context: adversarial review of the FN35-FN41 evidence-mapped testing
initiative.

## Verdict

Reject the current recommendation as stated.

Evidence-mapped tests over `SimObservation` may be a good authoring facade, but
the current evidence does not justify making that the primary conformance
architecture. It proves call-site ergonomics, not source-backed conformance.

The safer path is: build the audit/reporting core FN39 asked for, then put a
terse evidence DSL on top of it.

## Strongest Objections

### 1. FN40/FN41 did not close FN39's strongest objection

FN39 argued that one-off/source-authored tests do not answer applicability,
vacuity, coverage progress, or expected-gap reporting. FN40/FN41 override that
mainly on authoring feel.

Authoring feel matters, but it does not replace the audit core.

### 2. Source ids are still decorative

`sourceCase` accepts raw strings, and `SourceUnitRef` is only a wrapper. A case
can pass while citing the wrong source unit because there is no registry
validation or applicability predicate.

The twenty-case spike also uses spike-only fake source ids for expected gaps.

### 3. `SimObservation` loses required audit data

Current `SimObservation` has scenario id, instructions, reports, transmissions,
aircraft summaries, and a diagnostic string. It does not carry:

- provenance;
- source event ids;
- fact ids;
- capability tags;
- activation counts;
- adequacy;
- sample partition coverage.

That is not enough for the FN39 reporting goal.

## Specific Failure Modes

- A case cites an irrelevant or wrong source unit and still passes.
- Repeated events false-pass because `instruction<T>()` and `report<T>()` select
  the first matching event only.
- Same-time ordered events fail or pass incorrectly because ordering compares
  only `time.millis`, not stable event/sequence identity.
- Protocol evidence can pass without an exchange because `protocolScenario`
  returns an empty observation and `readbackCase` directly calls
  `requiredReadbackAtoms`.
- Expected gaps are too easy to bless. `assertNoUnexpectedFailures()` allows
  all expected gaps, and the current expected gaps point to broad facade
  follow-up items rather than gap-specific model/projection items.
- The evidence report is an in-memory formatter, not a durable Markdown/JSON
  artifact.

## Required Guardrails If Continuing

- The evidence DSL must compile to report records containing source id,
  scenario id, applicability result, activation count, sample partition, fact
  ids, outcome, and typed gap id.
- Source refs must come from a generated or validated catalog.
- Expected gaps must require a specific tracked model/projection gap, not a
  broad initiative item.
- `SimObservation` needs provenance and stable ordering identifiers, or it must
  be replaced by a trace/fact layer.
- Helpers may not inspect raw sim internals.
- Empty reports, empty case lists, vacuous cases, and source-mapped cases
  without activation must fail unless explicitly justified.

## Alternative Path If Rejecting

Continue from `FN39-CONF-1`: implement minimal `ConformanceTrace`, source
contracts, monitor outcomes, adequacy accounting, and Markdown/JSON reports for
the five-case probe.

Then expose the FN41-style terse DSL as the public authoring facade over that
machinery.

That preserves readability without throwing away auditability.

## Fuzzing-Specific Risks

`SampleTier.Generated` is currently only metadata. The older `SourceUnitSpec`
fuzzing is stringly and random-sample based. Neither proves partition coverage
unless the harness records and asserts every required partition.

Fuzzing must produce reproducible report records: seed, generated value,
partition, source unit, scenario, and evidence.

## Final Recommendation

Do not proceed with evidence-mapped `SimObservation` as the primary next
implementation path.

Proceed with FN39's conformance/reporting core, but require the public authoring
surface to meet FN41's terseness bar.

Evidence-mapped tests are the facade; they are not yet the conformance
architecture.
