---
satisfies: [R2-MULTI, R6, R7, R10, R23]
---

## Description

Controller-side multi-aircraft sequencing foundation. **Reframed per plan-review-round-1 Major 1+2+3**: pilot-side recognition is independent of ATC; the controller cannot prevent a pilot-side GA. The actual model — and what the deferment `D-PASS-g3a-react-multi-aircraft-{crosswind,tailwind}` requires — is: aircraft A on final declares pilot-reactive GA via the existing crosswind/tailwind branch (`derivePilotEvent` final-phase guard fires); aircraft B on downwind is NOT eligible for wind-GA (final-phase guard rejects); controller observes `Report(GoingAround)` from A → emits `ExtendDownwind` to B so B doesn't turn base into the GA-active runway. When A's GA state ends per R23 lifecycle (pattern-rejoin report OR 60s timeout — NOT runway-vacate per round-7 Major 4), controller's next instruction supersedes the ExtendDownwind and B resumes normal turn-base.

This task adds the controller-side observation + emission + cancel logic. NO `ControllerView.gaInProgressAtRunway` slice based on pilot-internal recognition (plan-review Major 1) — drive off `Report(GoingAround)` reception, which IS controller-observable. No changes to pilot-side recognition.

**Size:** M (4-5 files; controller-side decision logic + supersession audit + unit test)
**Files (expected):**
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt:44+` — audit `ControllerView` for `Report(GoingAround)` reception observability; extend slice if needed (e.g., `recentGoAroundReports: Map<RunwayId, AircraftId>` derived from message bus) — kept minimal
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt` — emit `ExtendDownwind` action when (a) recent GA Report from runway aircraft + (b) downwind aircraft observed expecting clearance to final; emit `CancelExtendDownwind` (or equivalent supersession) when GA state ends
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Supersession.kt:11-64` — audit table for any new interaction; document if a new row needed
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/certify/Certification.kt:27,246,559` — audit certification clauses; document
- `controller/src/commonTest/.../GoAroundSequencingSpec.kt` — unit test: Report(GoingAround) + downwind traffic → ExtendDownwind fires; GA-state-end → cancel fires
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/RegulationDatabase.kt` — add citation: ICAO Doc 4444 17th ed. Ch 12 §12.3.4 (sequencing/circuit); add `RegulationRef.DOC_4444_EDITION` constant if missing

## Approach

- **Observability check**: confirm `ControllerView` surfaces `Report(GoingAround)` reception. If today's view passes raw message bus or a `received: List<Transmission>` slice, this works as-is. If not, extend with a derived slice. Document the choice.
- **Trigger condition** (recognize-then-act): two facts coincide:
  1. A `Report(GoingAround)` received from an aircraft on the controlled runway
  2. Another aircraft observed on the downwind leg of the circuit pattern (round-13 Major 2 — controller-observable predicates ONLY, NO pilot-side `PilotPhase` reads). Predicates: `observation.position` within circuit-leg-downwind via `worldIndex.circuitLegsByPoint[observation.position]`; active arrival commitment stage indicates pre-base; received position reports (`Report(Downwind)` from this aircraft, if observed). NO `aircraft.phase == Downwind` access — that's a firewall violation.
- **Action**: emit `ExtendDownwind(aircraftId, runwayId)` instruction to the downwind aircraft. Standard ICAO Doc 4444 §12.3.4 sequencing phraseology.
- **Cancel condition**: when GA-state belief clears per R23 lifecycle (tracked aircraft's `Report(Downwind)` / `Base` / `Final` pattern-rejoin transmission, OR 60s timeout from `setAtTime`), the controller's next sequencing instruction (`TurnBase` / `Orbit` / `ClearedToLand`) supersedes ExtendDownwind via existing `Supersession.kt:11-64` rows. No runway-vacate clause. Prefer reusing existing supersession rows over adding new ones.
- **No pilot-side change**: the multi-aircraft model does NOT add or modify any pilot-side recognition. Existing `derivePilotEvent` is untouched.
- **Total-order discipline** (practice-scout #2): if simultaneous Reports arrive, the engine's existing `EVENT_ORDER` contract is `(time, source, seq)` via `SimEvent.seq` (round-9 Minor 1 — corrected from earlier `(tick, aircraftId, eventKind ordinal)` misstatement). The belief-folding logic uses the engine's canonical ordering; first-writer-wins (R23 tie-breaking) operates on the per-tick first event in seq order.

## Investigation targets

**Required**:
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/ControllerTypes.kt:1-100` — ControllerView shape + message-reception slice
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/Controller.kt:80-120` — `controllerDecide` entry
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/procedure/TowerArrival.kt` — arrival procedure / sequencing emitter (likely site for ExtendDownwind emission)
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Supersession.kt:11-64` — supersession table
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt` — action emission shape
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/certify/Certification.kt:27,246,559` — ExtendDownwind certification clauses

**Optional**:
- ICAO Doc 4444 17th ed. Ch 12 §12.3.4 — Aerodrome Control Phraseologies (sequencing)
- `protocol/.../Transmission.kt` (or sibling) — `Report(GoingAround)` type shape

## Key context

- **Plan-review reframing**: the original spec had "both aircraft simultaneously recognize wind GA + controller delays one". That contradicted pilot-side recognition independence (Major 2) and downwind-phase guard (Major 3). The CORRECT model is "single-aircraft pilot-reactive GA + controller-side sequencing of the second aircraft". Aircraft B on downwind never had a wind-GA decision; it's purely sequenced by ATC.
- **No `gaInProgressAtRunway` based on pilot internals** — drive off observable Report(GoingAround) message reception.
- **Existing Supersession rules** at L11-64: TurnBase, Orbit, ClearedToLand all supersede ExtendDownwind. When GA state ends + controller clears B to turn base, ExtendDownwind is naturally superseded. NO new supersession row needed.
- **No new transmission types** in this task. ExtendDownwind already exists.

## Acceptance

- [ ] **`BeliefState.goAroundInProgressByRunway: Map<RunwayId, GoAroundInProgress>` belief** (round-7 Major 3 — persistent in `BeliefState`, NOT `ControllerView` which is rebuilt per cycle). `GoAroundInProgress(aircraftId, setAtTime)` typed record. `ControllerView` derives the current value via projection if needed.
- [ ] **Runway-resolution policy for GA reports** (round-9 Major 1): `Report(GoingAround)` carries no runway payload (`Report(listOf(ReportEvent.GoingAround))`). Controller resolves runway from the reporting aircraft's **active arrival commitment** (the runway the aircraft was committed to land on) — typed lookup in `BeliefState` or sibling persistent state. If no commitment exists (e.g., GA report from an aircraft with no active arrival commitment), the belief write is FAIL-CLOSED: typed diagnostic logged; no arbitrary RunwayId silently written. Named helper: `resolveGoAroundRunway(aircraftId, beliefState): Either<NoCommitment, RunwayId>`. Unit tests: GA report → resolves runway from existing commitment; GA report with no resolvable runway → no belief write + diagnostic.
- [ ] **Lifecycle** (R23 refined round 7 Major 4 + round-13 Major 3): SET on `Report(GoingAround)`. CLEARED on observable: (a) pattern-rejoin transmission from tracked aircraft (`Report(Downwind)` / `Report(Final)` / `Report(Base)`) **with `receivedAt > setAtTime`** (round-13 Major 3 — same-cycle or pre-GA stale reports must NOT clear the belief); (b) explicit GA-complete report (out-of-scope for v1); (c) 60s timeout from `setAtTime`. **No positionPoint-vacate clause**. Belief-clear events must be observed AFTER the GA report in controller fold order. Unit tests: pre-GA stale `Report(Final)` arriving same-cycle as the GA report does NOT clear; post-GA `Report(Downwind)` clears correctly
- [ ] **Tie-breaking: first-writer-wins until cleared** (round-7 Minor 1). Subsequent GA Reports on a runway with active belief entry are IGNORED until clear.
- [ ] ControllerView audit: `Report(GoingAround)` reception is observable; KDoc documents the source + the belief's lifecycle
- [ ] **No-refire unit tests** (R23): single GA Report → ExtendDownwind fires ONCE (no per-cycle refire); pattern-rejoin report → belief cleared → ExtendDownwind cancelled; 60s timeout → belief cleared → ExtendDownwind cancelled; second GA Report while belief active → IGNORED (first-writer-wins)
- [ ] **Concrete cancel-output contract** (round-10 Major 2): when belief clears + B is `ExtendDownwind`-extended + B is eligible for next sequencing, controller emits `TurnBase` to B in the SAME cycle (not just "cancelled" abstractly). Unit test asserts the EXACT instruction emitted on the post-clear cycle. If B is no longer eligible for TurnBase, fallback emission is documented in `## Resolved during implementation`.
- [ ] **Runway-resolution fallback** (round-10 Major 3): if `resolveGoAroundRunway` finds no arrival commitment for the reporting aircraft, fallback order: (1) latest controller-side `activeRunway` belief for that aircraft; (2) typed diagnostic + no belief write. .5's setup ensures ARR-LAND commitment exists before wind shift (constraint documented in .5's scenario setup); fallback is for edge-case robustness not the primary path.
- [ ] `TowerArrival.kt` (or sibling) emits `ExtendDownwind` when (a) recent GA Report from runway aircraft + (b) trailing aircraft observed on downwind via **controller-observable predicates ONLY** (round-13 Major 2): `worldIndex.circuitLegsByPoint[observation.position]` indicates downwind leg; active arrival commitment stage pre-base; received `Report(Downwind)` if available. NO `PilotPhase` / `aircraft.phase` read (firewall violation)
- [ ] Acceptance: no `PilotPhase` or pilot mission state crosses into controller logic (firewall audit)
- [ ] Cancel logic: when GA state ends, ExtendDownwind is naturally superseded by next instruction (`TurnBase` / `Orbit` / `ClearedToLand` per Supersession.kt) — confirmed in unit test
- [ ] Supersession table audit comment: no new row needed (existing TurnBase row covers cancel-via-supersession)
- [ ] Certification clauses audit comment (`Certification.kt:27,246,559`): existing ExtendDownwind clauses unchanged OR extended with documented rationale
- [ ] Unit test `GoAroundSequencingSpec` covers: GA Report + downwind traffic → ExtendDownwind fires; GA-state-end + TurnBase → ExtendDownwind cancelled via supersession; no GA Report + downwind traffic → no ExtendDownwind (negative case)
- [ ] `RegulationDatabase` citation added for ICAO Doc 4444 17th ed. Ch 12 §12.3.4 using `RegulationRef.DOC_4444_EDITION` constant (added if missing)
- [ ] `./gradlew :controller:jvmTest :sim:jvmTest detekt --offline --no-daemon` GREEN; nine existing sim goldens don't regress

## Done summary

_(filled by `flowctl done` at task close)_

## Evidence

_(filled by `flowctl done` at task close)_
