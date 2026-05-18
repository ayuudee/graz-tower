# FN37 source-unit spec spike review

## Verdict

`SourceUnitSpec` feels like the right next vocabulary. It is lighter than
`law`, keeps the source unit as the audit atom, and gives each source unit a
small suite: domain dimensions, witness examples, partition checks, fuzz probes,
non-vacuity counters, and a report.

The strongest signal is that five disparate cases fit the same shape:

- readback obligations as generated pure protocol checks;
- taxi clearance / runway-use boundary as a full LOWG sim trace;
- touch-and-go then full-stop as intent-sensitive circuit behavior;
- essential aerodrome information as an explicit trace-vocabulary model gap;
- critical-phase radio silence as an explicit safety-necessity model gap with
  fuzzed phase/altitude/transmission-class parameters.

## What worked

- `spec` is the right noun. It does not overstate legal force and can cover
  obligations, permissions, phraseology, definitions, and model gaps.
- Domain dimensions make the claimed scope visible. Even the trivial LOWG-only
  cases now say what they cover.
- Non-vacuity counters are useful. They make "this scenario reached the
  behavior under test" explicit instead of implicit in a long trace assertion.
- Fuzzing should be typed-domain fuzzing. The critical-phase case uses the shape
  Andrew described: sample over the relevant range/partition space, not over
  arbitrary simulator events.
- Explicit `modelGap(...)` is a good forcing function. It lets a source-unit
  spec be present and reviewable without pretending the simulator can express
  the claim yet.

## What did not work yet

- The first harness is still too stringly typed. `parameters: Map<String,
  String>` is useful for reports, but real spec authors should get typed
  generated values in the assertion body.
- There is no real black-box `TowerConformanceTarget` yet. Taxi and
  touch-and-go still build `SimState` directly inside the spec body, so a
  separate conformance-test team would still need too much repo knowledge.
- The report only exists in memory and failure messages. The next version should
  write a JSON/Markdown artifact so progress can be reviewed without reading
  test code.
- `modelGap` currently passes when paired with `assertHasModelGap()`. That is
  acceptable for a spike, but a permanent runner should distinguish expected
  gaps from unexpected gaps and feed them into `.plan` or Flow explicitly.
- The existing taxi/touch test names still say `SourceBackedScenario`; this is a
  historical name leak from FN35/FN36, not the target vocabulary.

## Red-team notes

- A source-unit spec can still become ceremonial if authors cite a source id but
  write broad assertions. The mitigation is to require named oracle predicates
  and report which predicate supports each source unit.
- Fuzzing can become expensive quickly if each sample runs the full sim. Keep
  three tiers: witness in fast CI, partition sweep in focused CI, larger fuzz
  locally/nightly.
- The current domain DSL does not prevent contradictory dimensions. That should
  be fixed with typed domain objects before any clean rebuild.
- The model-gap specs are valuable only if they stay visible. They should not be
  counted as coverage, and they should not be allowed to drift forever without a
  backlog link.

## Recommendation

Continue the spike one more step, but keep treating the current API as
throwaway. The next useful work is not more source units; it is a cleaner
black-box target and typed generated-domain values.

The shape worth carrying forward is:

`SourceUnitSpec = source id + typed domain + witness + partition sweep + fuzz probe + trace oracle + adequacy report`.

If that can be rebuilt so taxi/touch-and-go specs no longer construct
`SimState` directly, the approach is strong enough to become the foundation for
future conformance work.
