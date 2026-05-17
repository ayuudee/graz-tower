# FN33 synthesis: ICAO 9432 section-by-section workflow

## Verdict

Continue the source-by-source workflow, but do not treat it as one giant test
writing queue. It works best as a triage and traceability workflow:

1. create a complete ledger;
2. bind one narrow behavior slice to executable source-backed cases;
3. classify the rest honestly;
4. use the classification to choose the next behavior/modeling spike.

The ledger is the main control surface. It prevents vague claims like "covered
readback" when only some source units are actually bound to evidence.

## What worked

- `SourceUnitRef` in code was useful. The tests cite accepted registry ids
  directly instead of relying on lossy paragraph/regulation proxies.
- Code-only fixtures were a good fit for typed protocol behavior. The readback
  slice used normal Kotlin assertions over `requiredReadbackAtoms`, so failures
  would be concrete and local.
- The workflow naturally separated executable behavior from domain review,
  model gaps, and out-of-scope records.
- The section ledger made scale visible: after one honest slice, only 4/166
  records were `covered`.

## What did not work yet

- Direct source-unit evidence is still test-local. Production `DecisionTrace`
  evidence remains regulation-ref based, so runtime trace assertions cannot yet
  say "this output was justified by source unit X" without a proxy.
- Literal phraseology cannot be tested well because the simulator mostly models
  typed instructions/transmissions, not rendered RT text. ICAO 9432 has several
  records whose real requirement is wording, not state.
- Communication timing/workload claims need a model of controller/pilot workload
  and transmission timing policy. The current typed readback layer cannot decide
  "do not pass this clearance during complicated taxiing".
- Existing goldens are behavior-rich but source-poor. They probably exercise
  many ICAO 9432 records, but the source-unit ledger correctly refuses to count
  them as covered until evidence is bound.

## Scenario builder assessment

A scenario builder is probably useful, but not as the first abstraction. The
readback slice did not need one; direct protocol assertions were clearer. A
builder becomes valuable for future slices that need world state and event
sequences:

- taxi/runway crossing;
- takeoff clearance and cancellation;
- final/long-final reporting;
- touch-and-go/full-stop decisions;
- handoff/contact-frequency behavior;
- emergency/lost-comms behavior.

The builder should be source-unit aware from the start: each scenario fixture
should carry the source units it claims to exercise, and the assertion side
should fail if expected source evidence is absent.

## Recommended next work

1. Add first-class `SourceUnitRef` evidence to runtime traces or a parallel
   test-only evidence projection. Do this before scaling beyond code-local
   protocol assertions.
2. Build one world-backed scenario slice for taxi/runway operations from
   `taxi_4_4_en` or takeoff procedures. This proves whether the workflow works
   beyond pure protocol functions.
3. Build a phraseology-rendering/linting spike for the `TAKE OFF` wording rule.
   This is a distinct facet and should not be forced into state-only tests.
4. Keep using the ledger status vocabulary. It is a good forcing function for
   honest progress.

## Second-source choice

Use `egast-vfr-extracted` as the second source. It has only 17 accepted records,
so it is small enough to process overnight, and it proves a different facet:
pilot-facing VFR safety/operational guidance rather than ATC phraseology/manual
procedure. That contrast should show whether the workflow is specific to
ICAO-style phraseology or can also classify source units that are valuable but
less directly executable in the simulator.

Avoid EPPLS as the second source for this overnight pass: EPPLS Chapter 12 has
already had special extraction repair work and is closer to general policy /
pilot performance law. `egast-vfr-extracted` is smaller and gives a cleaner
contrast.

## Review considerations

### FP / type safety

The executable slice stayed test-only and used total protocol functions. A
type-enforced claim was deliberately not kept as a runtime test after Kotlin
proved the negative type check statically.

### Test architecture

The tests assert real behavior from `requiredReadbackAtoms`. They do not count
existing goldens as source-unit coverage without explicit evidence binding.

### Impact

Production runtime code was not changed. The reusable addition is a test-only
`SourceBackedBehaviorCase`, which can be deleted if the spike direction changes.

### Operational correctness

Every new conformance case cites accepted ICAO 9432 source-unit ids. The report
does not introduce uncited ATC-law claims beyond those source-unit references.
