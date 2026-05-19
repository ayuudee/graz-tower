# Spike Evidence-Mapped Sim Test Harness

## Goal & Context
Explore two throwaway designs for source-mapped and evidence-mapped testing after FN39. The first design is the middle-ground evidence/source-mapped scenario harness with a narrow anti-corruption observation port into the sim. The second design is the earlier monitor-first idea, implemented only as a small spike. The objective is not to land production architecture; it is to learn which shape gives clearer, more independent, auditable tests for source units and golden/regression claims.

## Architecture & Data Models
Design A: evidence-mapped tests. Normal Kotlin high-level tests run scenarios or synthetic exchanges, adapt outputs through a narrow `SimObservation` / observation-port surface, declare test basis (`SourceMapped`, `Golden`, `Regression`, `Invariant`), typed samples, expectations, and evidence. The harness emits a local report.

Design B: mini monitor architecture. Scenario or synthetic trace facts are projected into a small `ConformanceTrace`; source-unit contracts bind source ids, capabilities, adequacy, and monitor functions; monitor runner emits typed outcomes.

Both designs are isolated under test/research spike code and may be deleted.

## API Contracts
Design A should expose enough API to express at least: source-mapped readback, source-mapped taxi, source-mapped touch-and-go, expected-gap cases, and one non-source golden assertion.

Design B should expose enough API to express the same source cases with monitors and typed outcomes.

## Edge Cases & Constraints
Do not build a full regulatory wall. Do not expose raw `SimState` to source/evidence assertions except inside the adapter. Do not silently skip expected gaps. Keep fuzzing/sample primitives typed and bounded. Reports must distinguish pass, fail, vacuous, expected gap, and unexpected gap.

## Acceptance Criteria
- [ ] Evidence-mapped harness spike compiles and covers the representative cases.
- [ ] Mini monitor harness spike compiles and covers the representative cases.
- [ ] At least one golden/non-source case is represented in the evidence-mapped form.
- [ ] Focused tests pass for both spikes.
- [ ] A detailed review compares ergonomics, coupling, audit value, fuzzing fit, and failure modes.
- [ ] `.plan` is updated if follow-up work is recommended.

## Boundaries
This spike should not replace existing golden tests or source-unit tests. It should not introduce production APIs unless the existing test source set makes that simplest. It should not attempt full phraseology correctness.

## Decision Context
FN39 recommended source-backed conformance monitors. The user raised a middle ground: Kotlin-native source/evidence-mapped tests behind an anti-corruption port, with modest fuzzing primitives and applicability beyond regulation-backed cases. This epic compares both paths by trying them in code before choosing.

## Review considerations

### FP / type safety
Use sealed outcome and basis types. Avoid string-coded outcomes. Keep generated samples typed where feasible. No catch-all `else` for sealed result handling.

### Test architecture
These are high-level/report-level spike tests, not low-level predicate tests. The evidence-mapped design should preserve believable scenarios and a narrow observation boundary.

### Impact
The main risk is accidental framework gravity. Keep the implementation small and throwaway. The result should inform the next permanent design, not become it by inertia.

### Operational correctness
Source-mapped claims must cite source-unit ids already in the corpus or prior spike. Semantic claims must not imply phraseology compliance.
