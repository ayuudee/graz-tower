# FN44 completion review

## Verdict

Ship FN44 as a useful pressure test of the FN43 evidence DSL.

The result is not "four source units covered." The honest result is better:
two source units now have positive, activated projection evidence, and two
source units exposed real missing trace concepts that are tracked as typed
gaps. That is the right outcome for this stage.

## What worked

The public tests stayed small. The strongest call sites in
`EvidenceProjectionPressureTest` read as source intent:

- known aerodrome information receipt before taxi;
- no routine controller transmissions in observed critical windows;
- controller-advised transfer remains a projection gap;
- pilot notification absent advice remains a projection gap.

The harness complexity moved to the right place. `EvidenceFactAdapters` now
projects:

- `AerodromeInformation` from pilot initial contact with an ATIS information
  code;
- `CriticalPhaseWindow` from LOWG `SimTrace` phases for `TakeoffRoll` and
  `LandingRoll`;
- typed gap metadata for transfer-of-communications cases.

The critical-phase case is the most important design win. It does not pass
because there are no transmission records. It passes because critical windows
exist and no routine controller transmission facts exist inside the projected
window model. The DSL fails if the window facts are absent.

## What did not work

The transfer-of-communications source units could not honestly become positive
with current traces. Existing G2 doctrine is release plus autonomous first
contact, not a controller-issued frequency transfer or a pilot-notified
frequency change before the change. FN44 records this as `FN44-GAP-1` and
`FN44-GAP-2` rather than weakening the source claim.

The essential-aerodrome-information positive is intentionally narrow. It proves
the "known received elsewhere" path before taxi via an ATIS information code on
initial contact. It does not prove hazard-specific essential-information
delivery, nor the final-approach timing branch. Keep `FN43-GAP-1` open until
those broader projections exist.

The critical-phase window projection is also narrow. It covers `TakeoffRoll`
and `LandingRoll`, where the phase semantics are reliable. It deliberately does
not claim all of "initial climb" or "last part of final approach"; those need
more precise phase/window modelling before source-backed tests should rely on
them.

## Red-team findings

**Could the positives be overclaiming?** Partly, if someone reads the source
unit id as full-source coverage. The tests and review text must be read as
subcase evidence. This is acceptable only because the cases are named narrowly
and the remaining broad gaps stay tracked.

**Could negative evidence be fake?** The critical-phase case avoids the main
failure mode by requiring window facts. A missing-window regression would fail
instead of passing on an empty transmission list.

**Is the DSL getting too broad?** Not yet. Two small helpers were added:
`aerodromeInformation(...).beforeTaxi().wasPassedOrKnownReceived()` and
`criticalPhase(...).routineControllerTransmissions().none()`. Both are
source-shaped and hide provenance/fact ids. No generic scenario builder was
added.

**Is the harness becoming a second simulator?** Not yet, but this is the main
future risk. Critical windows are projected from real `SimTrace` phases, not
re-simulated. Do not add richer inferred windows without trace evidence.

## Self-assessment

**Totality:** new `when` expressions over `RoleName`, `PilotPhase`, and
evidence outcomes are exhaustive. No catch-all `else` was added.

**Error handling honesty:** transfer cases are typed expected gaps with `.plan`
tracking. Missing critical-phase windows fail; they do not silently pass.

**Reversal completeness:** no runtime state transitions were added.

**Interaction coverage:** source cases exercise the public DSL over projected
facts, including real LOWG `SimTrace` critical windows.

**Test coverage:** focused coverage includes catalog validation, fact
projection, DSL behaviour, report/gap output, and the FN44 projection pressure
cases.

**Operational correctness:** docs and test names cite ICAO Doc 9432 §4.10,
§4.1.2, and §2.8.2 at the plan/review level. Phraseology conformance remains
out of scope.

## Recommendation

Continue with this direction. The next useful increment should not add many
more source units. It should either:

1. close `FN44-GAP-1` by modelling a controller-advised frequency transfer
   fact, probably in a focused cross-unit scenario; or
2. deepen `FN43-GAP-1` from ATIS-known receipt to real essential-information
   content/applicability facts.

Do not treat FN44 as evidence that source-unit coverage can be scaled by
counting source ids. It should be scaled by adding one projection primitive at a
time and preserving terse call sites.

## Verification

- `nix-shell --run './gradlew :sim:jvmTest --tests "*.EvidenceProjectionPressureTest" --tests "*.EvidencePermanentTwentyCaseTest" --tests "*.EvidenceReportWriterTest" --tests "*.EvidenceDomainsTest" --tests "*.EvidenceSelectorTest" --tests "*.EvidenceDslTest" --tests "*.EvidenceFactsTest" --tests "*.EvidenceSourceCatalogTest"'`
- `nix-shell --run './gradlew detekt'`
- `git diff --check`

Broad `:sim:jvmTest` remains limited by `.plan` item `SIM-RED-1` and was not
used as FN44 completion evidence.
