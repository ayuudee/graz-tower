# FN42 Isolated Deep Review

Date: 2026-05-19

Context: isolated senior review of FN39-FN41 evidence-mapped testing work.

## Verdict

The evidence-mapped path is credible, but only if the next iteration treats the
public DSL as the product and pushes monitors, provenance, coverage accounting,
and selector complexity behind it.

FN40/FN41 correctly pivot away from monitor-first authoring. The 20-case spike
has legible call sites, especially protocol readback cases and LOWG ordering
cases. It is not yet safe as a permanent harness because citation validation,
event selection semantics, expected-gap discipline, and corpus reporting remain
too weak.

## What Works

- The public shape is promising. Protocol cases in
  `EvidenceMappedTwentyCaseSpikeTest.kt` read compactly, and the LOWG temporal
  cases read as evidence rather than framework plumbing.
- `SimObservation` is the right anti-corruption boundary for authored tests: it
  exposes observed instructions, reports, transmissions, and final aircraft
  summaries without handing tests raw `SimState`.
- The outcome model is explicit and sealed: pass, fail, vacuous, expected gap,
  unexpected gap.
- The `.plan` entries show the initiative learned from itself: FN39
  monitor-first remains recorded, while FN40/FN41 narrow the authoring path to a
  facade, source catalog, and split protocol/sim entry points.

## Main Risks

- Current event selectors can false-pass. `instruction<T>()` and `report<T>()`
  take the first matching observation. That is too weak for repeated
  clearances, recovery circuits, absence checks, counts, scoped windows, or
  "between A and B" assertions.
- Source references are still stringly. `sourceCase` accepts raw strings, and
  `SourceUnitRef` is only a value wrapper. Permanent tests need a generated or
  mechanically validated source catalog.
- Expected gaps are visible but not governed. `assertNoUnexpectedFailures()`
  allows every `ExpectedGap` through, and the gap only carries a plan-id string.
- The mini monitor spike contains a pattern that must not escape the spike:
  incompatible contracts are silently dropped by filtering. If monitors are used
  internally, incompatibility must be reported, not omitted.
- Some cases prove structural predicates, not full operational behaviour.
  `requiredReadbackAtoms(...)` is valuable, but it is not the same as proving a
  pilot actually read back an instruction in a sim exchange.

## Design Recommendations

- Split public entry points into `protocolEvidence` and `simEvidence`.
- Promote the readback helper, but name it precisely: either
  `structuralReadbackRequirement` or make it drive a real instruction/readback
  exchange when claiming an operational readback.
- Replace raw source ids with a generated or mechanically validated source
  catalog. The FN33 ICAO 9432 ledger already has the relevant mappings for
  readback, taxi, and touch-and-go sections.
- Build a small query algebra before adding more cases: `first`, `nth`,
  `exactly`, `none`, `between`, `after`, scoped by aircraft/runway/attempt/
  circuit.
- Treat expected gaps as tracked records with typed gap ids, affected source
  units, missing observation/projection, and closure trigger.

## Fuzzing Recommendation

Use modest deterministic fuzzing behind the facade, not as public test ceremony.
Start with pure protocol domains: headings, levels, speeds, squawks, pressure
settings, runway identifiers.

For sim scenarios, prefer boundary/representative samples until failure
diagnostics include seed, sample, source unit, and observed evidence.

## Review Considerations

### FP / Type Safety

Sealed outcomes are good. Raw string source ids, nullable observation lookup,
mutable builders, and silent monitor filtering are weak spots.

### Test Architecture

The path aligns with the testing standard when it stays high-level:
scenario/synthetic input plus observation-port assertions. Unit tests are still
appropriate only for pure protocol/generator oracles.

### Impact

This creates a durable testing API. That is worth doing if helper growth is
governed; otherwise it becomes a second folklore DSL.

### Operational Correctness

Keep semantic, structural-readback, rendered phraseology, and golden/project
behaviour as separate claim types. Do not let a source id imply phraseology
compliance unless the test observes rendered RT text.

## What Would Falsify This Path

- The next 30-50 cases require frequent one-off helpers, raw `SimState`, or
  manual event scanning.
- Source ids cannot be mechanically validated.
- Expected gaps accumulate without narrow closure triggers.
- Repeated-event cases produce ambiguous selectors.
- The facade cannot express absence, counts, scoped windows, and generated
  samples without exposing monitor vocabulary at the call site.
