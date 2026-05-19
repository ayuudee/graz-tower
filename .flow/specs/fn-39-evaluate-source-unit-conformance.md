# Evaluate Source-Unit Conformance Strategy

## Goal & Context
Turn the current source-unit testing exploration into a formal research and evaluation project. The objective is to decide the best way forward for building a durable regulatory/testing foundation from accepted source units, while preserving the core constraints learned so far:

- tests should be high-level and believable, per `docs/test-standards.md`;
- source units remain the audit atom;
- fuzzing means typed-domain fuzzing over the variables a source unit talks about, not arbitrary simulator event fuzzing;
- test authors should be able to work against a stable interface without knowing controller BDI/rule internals;
- model gaps must be explicit, not silently skipped;
- the final output should be a convincing proposal and a concrete work plan, not just another throwaway spike.

The work evaluates at least the two existing candidates:

1. **FN37 `SourceUnitSpec`**: source-unit-owned specs with domain dimensions, witness/partition/fuzz probes, non-vacuity counters, model-gap reporting, and review artifacts.
2. **FN38 monitor v2**: scenarios produce black-box traces; independent source-backed monitors observe those traces with applicability/requirement predicates, adequacy, vacuity, and projection-gap reporting.

The final deliverable is a proposal that makes the case for one strategy, says what to borrow from the losing strategy, and turns the prior spike work into the chosen form.

## Architecture & Data Models
This is an evaluation epic, not a production implementation epic. The central artifacts are:

- a comparison matrix for the candidate designs;
- implementation sketches or thin vertical slices where needed to make comparison concrete;
- senior-level review records for each candidate;
- red-team records for each candidate;
- an iteration log showing changes made after review/red-team feedback;
- a final recommendation and migration/work plan.

Candidate dimensions to evaluate:

- authoring experience for a source-unit/test team;
- independence from simulator internals;
- fit for the five representative cases: readback, taxi, touch-and-go/full-stop, essential aerodrome information, critical-phase radio silence;
- typed-domain fuzzing expressiveness;
- adequacy/non-vacuity reporting;
- model-gap handling;
- traceability to source units and ledger status;
- cost of black-box trace projection;
- risk of becoming ceremonial traceability;
- risk of building a second simulator model;
- runtime cost and CI/nightly split.

## API Contracts
Any candidate that survives must expose a stable test-author interface. Test authors should not import controller internals, inspect BDI/rule state, or construct `SimState` directly. The accepted proposal must define one of:

- a `TowerConformanceTarget`/`ScenarioInput`/`ScenarioTrace` boundary, or
- an equivalent black-box trace/projection boundary with explicit allowed imports.

Reports must distinguish:

- pass;
- fail;
- vacuous / inadequate activation;
- expected model gap;
- unexpected projection gap.

## Edge Cases & Constraints
The evaluation must not reward green-only designs. At least one candidate check must exercise an explicit gap case. Negative/prohibition rules must not pass by absence alone; they require candidate activation/attempt counters. `may` and `should` source units must not be treated as unconditional `shall` rules.

This work should not attempt full ICAO 9432 coverage. It evaluates strategy using the established five-case representative set.

## Acceptance Criteria
- [ ] Candidate A (`SourceUnitSpec`) is reviewed against the evaluation matrix.
- [ ] Candidate B (monitor/contract v2) is reviewed against the same matrix.
- [ ] Each candidate receives a senior-level design review focused on architecture, testing, FP/type safety, operational correctness, and maintainability.
- [ ] Each candidate receives a red-team pass focused on false confidence, vacuity, hidden implementation coupling, fuzzing nonsense, and projection/model gaps.
- [ ] Findings trigger at least one iteration loop: revise, re-review, and record what changed.
- [ ] The final proposal chooses one primary strategy, explicitly lists borrowed ideas from the other, and explains rejected alternatives.
- [ ] The final proposal includes a concrete implementation work plan with staged tasks, verification gates, and backlog/.plan implications.
- [ ] The final proposal makes the case in terms of the project objectives, not local elegance.

## Boundaries
Do not rewrite the production simulator as part of this evaluation. Do not expand source-unit coverage beyond the five representative cases unless needed to answer a specific evaluation question. Do not merge a permanent framework from this epic without a separate implementation epic.

## Decision Context
FN37 showed source-unit-owned specs are readable and good for early exploration, but still risk scenario construction living too close to implementation details. FN38 showed trace monitors may scale better and suit temporal/range rules, but risk projection complexity and second-model drift.

This epic exists to stop local momentum from choosing by accident. It should compare both strategies at staff-engineer depth, force the weak assumptions into the open, loop on review feedback, then produce one coherent proposal.

## Review considerations
FP / type safety: The chosen approach must make invalid domains hard to express and avoid stringly typed fuzz inputs in the final design. Missing projections/gaps must be typed outcomes, not `null`, empty lists, or silent skips.

Test architecture: The chosen approach must preserve high-level/integration testing as the default, with pure protocol tests only where there is an independent oracle. It must specify witness, partition, and fuzz tiers plus CI/nightly expectations.

Impact: The chosen approach will shape future regulatory coverage work. The proposal must address coupling, report artifacts, ledger integration, review workflow, and how to undo or replace the approach if it proves wrong.

Operational correctness: The proposal must handle `shall`, `should`, `may`, examples, phraseology, definitions, and model gaps honestly. It must cite and preserve source-unit identity without overstating coverage.
