# FN39 Red-Team Review

Date: 2026-05-19

Scope: attack both candidate strategies from the perspective of future
maintainers trying to use the source-unit corpus as a regulatory foundation
for high-level sim behaviour.

## Candidate A: SourceUnitSpec-as-runner

### Attack 1: it creates a second scenario harness

The helper currently has enough scenario setup surface to tempt authors into
building tests there instead of in the normal high-level scenario machinery.
That violates the intended test direction: source-unit conformance should
observe believable scenarios, not own a parallel way to start aircraft, select
runways, or drive the simulation.

Result: survives only if the helper loses runner ownership. A future version
may provide authoring metadata, but scenario construction must remain in the
normal scenario bank.

### Attack 2: generated domains are stringly and underspecified

`"requiredField" = "controller"`, `"omittedField" = "runway"` and similar
parameters are tolerable for a spike, but they are not a durable expression of
the domain. They allow misspellings, incompatible combinations, and assertions
that appear to cover a source unit while testing a different semantic axis.

Result: fails as a final design. Domains need typed values and a generator that
can state compatibility constraints.

### Attack 3: source ids can become decorative

The helper makes it easy to name a source unit without proving that the
scenario actually exercised that unit's applicability predicate. A passing
assertion may prove only that some trace shape occurred.

Result: non-vacuity and applicability must be first-class outcomes, not optional
test prose.

### Attack 4: expected gaps can hide permanent gaps

The current spike can mark known model gaps by convention. Without a typed gap
registry and report, that becomes an unreviewable TODO list.

Result: any retained source-unit authoring shell must distinguish expected gaps
from unexpected gaps in machine-readable output, and expected gaps must point to
`.plan` or an equivalent tracked backlog item.

### Attack 5: it optimizes for author comfort over audit output

The code-first style is pleasant for developers, but the output a reviewer needs
is a source-unit coverage report: what was applicable, what was observed, what
passed, what failed, what was vacuous, and why. Per-test assertions do not
automatically produce that audit view.

Result: a final strategy needs durable Markdown/JSON reports as a core
artifact, not an afterthought.

## Candidate B: trace monitor architecture

### Attack 1: the trace projection can become a second implementation

If `ConformanceTrace` exposes derived state such as "pilot complied with
readback rule" or "this landing clearance was operationally correct", monitors
will test the projection rather than the system. The projection would become an
uncited shadow regulator.

Result: survives only with a strict projection charter: trace facts are
observed events and typed domain facts, not conformance conclusions.

### Attack 2: monitor vacuity is easy and dangerous

A monitor over "if applicable then require X" can pass every run where the
applicability predicate never fires. That is especially dangerous for negative
or rare requirements, because absence looks like success.

Result: adequacy is mandatory. Every monitor declares expected activation
counts, candidate-attempt counts for negative rules, and accepted reasons for
vacuity.

### Attack 3: fact provenance can be lost

If a trace fact does not say whether it came from a transmission, instruction,
scenario input, synthetic protocol trace, or fixture metadata, the monitor
cannot be audited. It also becomes unclear whether a fact was observable to a
black-box implementation.

Result: every fact needs provenance. Reports must show the supporting fact ids
and the source-unit ids that consumed them.

### Attack 4: monitors can be over-broad

A monitor may run on every scenario and then report vacuity for most of them.
At scale that produces noise and hides missing scenario coverage.

Result: monitors need compatibility tags against scenario capabilities:
`TowerCircuit`, `ProtocolSynthetic`, `Taxi`, `LandingIntent`, `CriticalPhase`,
and similar typed labels. Incompatible monitor/scenario pairs should be
non-applicable, not vacuous.

### Attack 5: pure protocol rules do not need the full sim

Readback phrase-order and required-field checks can be tested from small
controller/pilot exchange traces. Forcing the full sim to generate every
protocol shape would be slow and brittle.

Result: the architecture needs a synthetic trace adapter for pure protocol
checks. Synthetic traces still use the same monitors and reports.

### Attack 6: fuzzing can explode the runtime

Fuzzing source-unit domains across altitude, runway, clearance, phase, and
phrase variants can turn a useful suite into another overnight run with unclear
progress.

Result: use explicit tiers: example partitions in regular CI, bounded property
samples in nightly/local runs, and separate exploratory fuzzing only when a
domain is mature enough to pay for it. Every tier reports the generated domain
partition it covered.

### Attack 7: phraseology is still incomplete

Some source units talk about what must be communicated, but not the exact
phraseology. If monitors assert final phrase text prematurely, they will encode
local conventions rather than CAP/ICAO phraseology.

Result: distinguish semantic monitors from phraseology monitors. Phraseology
checks require explicit cited source units and should not be inferred from
semantic requirements.

### Attack 8: evidence can be too coarse for regression work

A pass/fail line per source unit is not enough when a high-level scenario fails.
Developers need the responsible source unit, scenario, generated domain value,
and trace facts that caused the failure.

Result: report records must be narrow and reproducible: source id, scenario id,
domain sample, monitor id, outcome, fact ids, and typed gap id when applicable.

## Attack on the emerging recommendation

The emerging recommendation is a hybrid: monitor-first architecture, with a
small source-unit authoring shell that borrows the useful FN37 concepts. The
strongest counterargument is that this may be over-engineering for a research
spike, and that writing one high-level scenario per source unit is simpler.

That counterargument is real for the next five cases, but weak for the corpus.
The stated goal is not merely to write five tests; it is to learn a strategy
that can track progress across the source units and give confidence that future
ATC behaviour remains source-backed. One-off tests do not answer applicability,
vacuity, coverage progress, or expected-gap reporting.

## Red-Team Conclusion

Candidate A should not become the final runner. It is valuable as evidence that
code-authored source-unit cases feel workable, but it couples authoring to
scenario setup and lacks a durable audit report.

Candidate B is the stronger foundation, but only under hard constraints:

- `ConformanceTrace` is an observation projection, not a second model.
- all trace facts carry provenance.
- monitor adequacy and vacuity are mandatory outcomes.
- monitor/scenario compatibility is typed.
- source-unit ids are consumed by machine-readable reports, not only test names.
- model gaps are typed and linked to tracked backlog work.
- fuzzing is tiered and bounded.
- semantic and phraseology monitors stay separate until phraseology sources are
  explicit.

With those constraints, the monitor-first architecture survives the red-team
pass and should be the basis of the final proposal.
