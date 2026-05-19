# FN43 implementation review

## Scope reviewed

FN43 turned the FN40/FN41 evidence-mapped spike into a permanent test-facing
audit core:

- typed ICAO 9432 source refs and typed expected-gap ids;
- provenance-bearing `EvidenceFactSet` records from protocol fixtures and LOWG
  sim observations;
- split `protocolEvidence` / `simEvidence` authoring entry points;
- selectors for instructions, pilot reports, ordering, counts, and final
  aircraft summaries;
- deterministic generated domains for protocol samples;
- durable Markdown and JSON report output; and
- a re-ported twenty-case permanent suite.

The reviewed public call site is
`sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/EvidencePermanentTwentyCaseTest.kt`.
It covers twelve structural readback cases, four LOWG source-observed ordering
cases, one golden project behaviour, one invariant, and two typed expected
gaps.

## Self-assessment

**Totality:** new sealed `when` expressions over evidence outcomes are
exhaustive. The DSL does not add catch-all `else` branches that swallow unknown
cases. Public authoring paths fail loudly for missing `observe`, missing
`expect`, uncited source cases, blank ids, negative selector indexes, and
source cases with no activated evidence facts.

**Error handling honesty:** unimplemented model coverage is represented as
typed `EvidenceGapId` values linked to `.plan`, not as silent pass/fail
workarounds. The one existing test-helper `error()` in
`EvidenceMappedSpikeHarness.kt` is limited to fixture construction rejection
from `SimState.initial`; no evidence DSL user path depends on pretending a
type-valid audit case is impossible.

**Reversal completeness:** this work is test-only audit infrastructure and does
not introduce runtime state transitions or reversible sim/controller state.

**Interaction coverage:** the LOWG observation path runs the simulator, converts
controller/pilot transmissions and final aircraft summaries into stable
evidence facts, and asserts against those facts through the DSL. The protocol
path exercises the same report records without sim facts.

**Test architecture:** coverage is concentrated at the public DSL boundary and
the report boundary. The tests prove source catalog validation, provenance
conversion, selector ordering/counting, generated-domain metadata, report
serialization, and the twenty-case permanent suite. The tests avoid checking
properties already guaranteed by the type system except where the behaviour is
about public failure/reporting semantics.

**New-field audit:** `TransmissionRecord.transmissionId` was added so evidence
facts can carry stable provenance. The central `toTransmissionRecord` adapter
and direct test constructors were updated; no production state class field was
added.

**Operational correctness:** ATC behaviour claims are deliberately narrow. The
source-backed cases cite extracted ICAO 9432 source units. Structural readback
cases assert `requiredReadbackAtoms`, not rendered phraseology. The two ICAO
9432 behaviours that are not observable yet are expected gaps, not fake
coverage.

## Red-team findings

The permanent call sites are materially better than the spike call sites. They
no longer expose raw source-unit strings, monitor vocabulary, fact ids, or
report plumbing. The source refs live behind `Icao9432EvidenceSources`, and the
gap cases use `EvidenceGaps` metadata tied back to `.plan`.

The strongest no-corners-cut property is activation. A source case that cites a
source but does not activate any observed fact now fails unless it is explicitly
`ExpectedGap` or `Vacuous`. This prevents decorative citation.

The report writer is good enough for the current scope. It emits suite,
scenario, claim kind, source refs, sample/generated metadata, outcome,
activation fact ids, and adequacy/applicability strings into Markdown and JSON.
The strings are not a formal adequacy model, but they are durable and inspectable
rather than hidden in assertion text.

One small API surface problem was found and fixed during review:
`EvidenceSelector` was constructible by ordinary test code even though selector
construction should go through `instructions<T>()` and `reports<T>()`. Its
constructor is now `@PublishedApi internal`, preserving public inline selectors
without inviting ad hoc selector construction.

## Residual risks

The old disposable spike tests still exist and still contain raw source strings
and fake spike gap ids. That is acceptable as historical spike material, but the
permanent API should be judged by `EvidencePermanentTwentyCaseTest` and the new
DSL files, not by the disposable spike surface. If confusion grows, archive or
delete the spike tests in a separate cleanup.

The broad `:sim:jvmTest` suite is red in existing reactive/emergency goldens:
`G0AbortTakeoffEngineFailureTest`, `G3aPilotReactiveMultiAircraftTest`, and
`G3bCrossAerodromeReactiveTest`. The failures are aircraft phase, report count,
and stage-regression assertions in existing behaviour tests, not source/evidence
DSL assertions. This is now tracked as `.plan` item `SIM-RED-1`; it blocks a
claim that all sim tests are green, but it does not invalidate the focused FN43
evidence implementation.

The DSL has only the primitives demanded by the twenty-case suite. That is
intentional. Resist adding monitor abstractions, formal adequacy types, or
scenario builders until a real source case demands them. The next extension
should probably be one small helper at a time, driven by the first non-readback
or non-LOWG-ordering source unit that needs it.

## Verification

- `nix-shell --run './gradlew :sim:jvmTest --tests "*.EvidencePermanentTwentyCaseTest" --tests "*.EvidenceReportWriterTest" --tests "*.EvidenceDomainsTest" --tests "*.EvidenceSelectorTest" --tests "*.EvidenceDslTest" --tests "*.EvidenceFactsTest" --tests "*.EvidenceSourceCatalogTest"'`
- `nix-shell --run './gradlew detekt'`
- `nix-shell --run './gradlew :sim:jvmTest'` was run as a broad smoke and failed
  in the unrelated reactive/emergency golden set recorded above.

## Completion judgment

Ship FN43 as the permanent evidence DSL audit core. The implementation meets the
project objective: tests are Kotlin-native, terse at the call site,
source-mapped, activation-aware, reportable, and honest about unsupported
source projections. The known remaining gaps are explicit backlog work rather
than hidden inside green tests.
