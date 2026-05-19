# Senior review: FN38 conformance monitor candidate

## Verdict

The monitor candidate is the stronger long-term architecture, but only if the
trace projection is kept narrow, typed, and audit-facing.

Its main advantage is separation of concerns: scenario authors generate
black-box traces; source-backed monitors evaluate those traces. That matches the
objective that a separate team can write regulatory checks against an interface.
It also fits temporal/range rules better than source-unit-owned probes.

Its main risk is overbuilding `ConformanceTrace` into a second simulator model.
That risk is real and must shape the implementation plan.

## Architecture review

### Strengths

- Decouples scenario generation from source-unit checks.
- Lets one scenario exercise many monitors and one monitor run over many
  scenarios.
- Naturally handles “whenever P, Q” rules, range rules, prohibitions, and
  activation/vacuity.
- Creates a plausible black-box collaboration contract:
  `ScenarioInput -> ConformanceTrace -> MonitorReport`.
- Makes missing concepts visible as projection gaps.

### Weaknesses

- Requires a normalized `TraceFact` projection before most monitors are useful.
- Projection can drift into a second domain model if facts become too broad.
- Monitor applicability predicates can hide implementation assumptions if facts
  are inferred from mission internals instead of observed behavior.
- Monitor banks can create noisy vacuity/gap output unless compatibility
  filtering is strong.

### Architectural constraint

`ConformanceTrace` must answer only: “what did this black-box run show?”

It must not expose controller beliefs, BDI state, active rule internals, or a
complete copy of `SimState`.

## Test architecture review

The monitor model fits the project testing standard well:

- monitors are high-level trace oracles;
- scenarios remain believable workflows;
- pure protocol readback can be represented as synthetic trace facts because it
  has an independent oracle;
- fuzzing happens at the scenario/domain level, not as arbitrary event streams.

The strongest test-architecture feature is adequacy:

- observed count;
- applicable count;
- checked count;
- pass/fail count;
- vacuity;
- projection gaps;
- scenario partitions.

That is more rigorous than FN37’s manual hit counters.

## FP / type-safety review

The design should be implemented with:

- sealed `TraceFact`;
- typed monitor input `ConformanceMonitor<E : TraceFact>`;
- typed `ProjectionGap`;
- typed `MonitorOutcome`;
- no nullable missing projection values;
- no catch-all `else` in fact handling.

The danger is generic monitor APIs becoming too dynamic. If monitor authors
write stringly predicates over `Map<String, Any>`, the candidate loses its main
advantage. The projection and monitor APIs must be boringly typed.

## Operational correctness review

This candidate handles source-unit modality better than FN37:

- `shall`: requirement monitor with non-vacuity;
- `should`: advisory monitor with explicit severity;
- `may`: capability monitor with explicit applicability/intent;
- example: support-only or phraseology monitor;
- definition: domain/projection support, not a behavior assertion by itself;
- phraseology: rendered phrase fact required for full coverage;
- model gap: typed projection gap or unsupported source concept.

This is a better fit for ICAO 9432, where many source units are examples,
permissions, definitions, or phraseology rather than simple commands.

## Maintainability review

The maintainability upside is high:

- scenario bank can grow independently;
- monitor bank can grow independently;
- reports can show which monitors activated on which scenarios;
- existing goldens can potentially be re-run through monitor projection without
  rewriting them as source-unit tests.

The maintainability risk is equally clear:

- if every monitor needs a new trace fact, the projection layer becomes a
  permanent bottleneck;
- if monitor reports are too noisy, developers will stop reading them;
- if compatibility filtering is poor, the suite becomes slow and vacuous.

## Senior findings

1. **Major: choose monitor architecture only with a strict projection charter.**
   Trace facts must be audit-facing observations, not simulator state exports.
2. **Major: adequacy/vacuity is mandatory, not optional.** Monitors cannot pass
   because their antecedent never fired.
3. **Major: fact provenance is required.** Reports must say whether a fact is
   observed, fixture-authored, mission-authored, or inferred.
4. **Moderate: compatibility filtering is required before scaling.**
5. **Moderate: pure protocol checks need a clean synthetic trace story.**

## Recommendation

Use the monitor candidate as the primary architecture for the final proposal.
Borrow FN37’s authoring affordances, but run them through monitor/projection
machinery.

The minimum viable implementation should prove:

- `TraceFact` projection for transmissions/instructions/reports;
- readback monitor over synthetic or projected facts;
- taxi monitor over a LOWG trace;
- touch/full-stop monitor with explicit intent/projection handling;
- model-gap monitors for essential-information and safety-necessity facts;
- report statuses: pass, fail, vacuous, expected gap, unexpected gap.
