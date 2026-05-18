# Second High-Level Source-Backed Scenario Spike

## Goal & Context
Follow up FN35 by testing whether the source-backed scenario pattern still feels useful once it covers a second ICAO 9432 section beyond departure taxi. This is deliberately disposable spike work: the goal is evidence about the testing strategy, not a permanent framework.

## Architecture & Data Models
Extract the small source-backed scenario wrapper into a shared JVM test helper under `sim/jvmTest`. Add one believable high-level scenario for ICAO 9432 final approach / landing touch-and-go source units, using the existing LOWG sim pipeline and typed instruction trace rather than low-level controller unit tests.

## API Contracts
A `SourceBackedScenario` must carry a non-blank scenario id, at least one `SourceUnitRef`, and wrap assertion/check failures with the cited source units. The citation validator remains the audit contract: ledger rows marked `covered` or `partially_covered` must be cited by Kotlin tests.

## Edge Cases & Constraints
This spike must not invent phraseology support. If the simulation only proves the typed instruction (`ClearedTouchAndGo`) and not rendered words, the corresponding source unit is only partially covered. Failures should include source context for normal Kotlin assertion/check failures.

## Acceptance Criteria
- [ ] The taxi source-backed scenario still passes with the shared wrapper.
- [ ] A second high-level source-backed scenario covers or partially covers touch-and-go final approach / landing source units.
- [ ] ICAO 9432 coverage ledger/report files are updated consistently.
- [ ] Citation validation passes.
- [ ] Focused sim/controller tests and detekt pass before commit.

## Boundaries
Do not build the final source-backed testing framework in this spike. Do not add phraseology rendering assertions. Do not widen to every ICAO 9432 section.

## Decision Context
FN35 showed that source units can anchor high-level integration scenarios, but one taxi example was too thin to judge the pattern. Touch-and-go gives a second facet: runway/landing phase behavior, partial evidence where phraseology is not yet rendered, and a check against whether the same declarative-in-code wrapper remains readable.

## Review considerations
FP / type safety: the helper is test-only and must not hide reachable failures; it should wrap ordinary assertion/check failures while preserving loud failure semantics. No production sealed hierarchies or state classes are changed.

Test architecture: scenarios remain high-level, believable, and minimal. They assert outcomes through the real sim trace and citation validator rather than checking structural properties the compiler already guarantees.

Impact: this couples test evidence to canonical source-unit ids from the generated ledger. That is intentional for the spike but may become maintenance-heavy if promoted without a registry or generated citation index.

Operational correctness: the touch-and-go claim is tied to ICAO 9432 `final_approach_landing_4_7_en`; because phraseology strings are not asserted, the clearance phrase source unit can only be partially covered.
