# Controller Issue Tracker

Generated 2026-04-15 from three adversarial self-reviews + three ATC agent reviews (law, general, phraseology).

## Tier 1: Safety / Correctness

- [x] **1. Controller-initiated go-around** — ARR-GO-AROUND rule in AWAIT_APPROACH. SAFETY urgency.
- [x] **2. Cancel takeoff** — DEP-CANCEL-TAKEOFF rule in AWAIT_TAKEOFF_OBSERVED. Defense-in-depth.
- [x] **3. Surface wind on takeoff/landing/touch-and-go** — Pipeline enrichment after arbitration (TWR1 pattern).
- [x] **4. TurnBase to cancel ExtendDownwind** — ARR-TURN-BASE on BASE leg with runway access.

## Tier 2: Regulatory Citation Corrections

- [x] **5. ARR-EXTEND**: added ICAO 4444 §7.10
- [x] **6. GND-TAXI + GND-TAXI-STAND**: added ICAO 4444 §7.6
- [x] **7. GND-TAXI-STAND**: added ICAO 4444 §7.11
- [x] **8. ARR-DOWNWIND-ACK**: added ICAO 9432 circuit reports
- [x] **9. DEP-LUAW**: added ICAO 4444 §7.9.3

## Tier 3: Training Product Gaps

- [x] **10. Empty `via` on TaxiTo** — BFS adjacency walk populates intermediate via points
- [x] **11. ContactFrequency null frequency** — Resolved from AerodromeRole.frequency; Left if role missing
- [x] **12. No sequence information for arrivals** — SequenceInfo on ProposedAction, derived from runway duty queue
- [x] **13. No vacate instruction after landing** — ARR-VACATE rule with AfterLandingVacateVia/BacktrackRunway
- [x] **14. Guard failure traces not human-readable** — failureMessage property on all guards
- [x] **15. No traffic information alongside clearances** — TrafficInfo on ProposedAction, emitted as companion Respond

## Tier 4: Operational Realism

- [x] **16. Arbitrator too restrictive** — Now one per urgency level (SAFETY unlimited)
- [x] **17. No conditional line-up** — ConditionalLineUpAction with AfterTraffic predicate, DEP-LUAW-COND rule
- [x] **18. No distance/time in runway priority** — Arrival enqueue restricted to BASE/FINAL legs
- [x] **19. Repeated HoldPosition every cycle** — NoActiveInstruction dedup guard
- [x] **20. `selectRunwayIntoWind` fails on suffixed IDs** — Strip non-digit suffix

## Tier 5: Code Quality

- [x] **21. `advanceCommittedStages` var → fold**
- [x] **22. `OperatorContext` redundant fields** — removed; derived from view via properties
- [x] **23. `TaxiToStandAction` picks first stand** — now picks nearest by position
- [x] **24. `TaxiToHoldingAction` non-deterministic** — now picks nearest by position
- [x] **25. `NoActiveInstruction` lambda equality** — documented as accepted trade-off
- [x] **26. No negative test cases for ground** — added: arrival-at-stand, human-without-request

## Tier 6: Future Scope

- [ ] **27. IFR ground flow** (startup, clearance delivery, pushback)
- [x] **28. Readback verification / incorrect readback** — three-state verdict landed in Phase 4; structured correction still deferred
- [ ] **29. Runway crossing authorisation during taxi**
- [ ] **30. Special VFR provisions**
- [ ] **31. Departure instructions after takeoff** (turn direction, VFR route)
- [ ] **32. Base turn / report base instruction**
- [ ] **33. Belief-delta event derivation** (state-change detection without pilot report)
- [ ] **34. Reactive safety layer** (proactive conflict detection — overlaps with #1)
- [x] **35. Pin ICAO Doc 4444 edition number** — 17th ed. (2024) pinned in RegulationRef.ICAO_4444_EDITION; all 35 RegulationDatabase entries carry edition triples

## Tier 6 additions from Phase 4 sim-engine review (2026-04-17)

See `atc-agent-review-tracker.md` for the full Phase-4 deferred list. The
items that surface as *controller* backlog (rather than sim / protocol
backlog) are:

- [ ] **36. DEP-CANCEL-TAKEOFF missing NoPendingReadback guard** (P4-D14)
- [ ] **37. ARR-TURN-BASE fires from base leg** — should only fire from downwind (P4-D4)
- [ ] **38. ARR-LAND distance gate** — no minimum final distance for landing clearance (P4-D3)
- [ ] **39. APP→TWR handoff point** — downwind is phraseologically wrong for most fields; needs ILS-intercept / 8nm final logic when approach sequencing lands (P4-D1)
- [ ] **40. Approach sequencing layer** — "number N", vectors, speed control, essential traffic (P4-D2, P4-D5, P4-D6)
- [ ] **41. Wake-turbulence separation + arrival/departure runway buffer** — safety-layer concerns, overlap with #34 (P4-D7, P4-D8)
- [ ] **42. Cancel-takeoff / immediate-action family readback atoms** — only `HoldPosition[CancelTakeoff]` landed in Phase 4 (P4-D15)

## Carry-over open tasks from pre-Phase-4 backlog

- [x] **43. Queue semantics regression test for mixed 3+ entries** — `RunwayDutyQueueTest` covers FIFO preservation, preemption-requeue, ARRIVAL-beats-DEPARTURE sort, commitment-pruning
- [x] **44. Transmission-reception design doc v1 appendix** — Appendix A (v1 landing report) in `wiki/design-decisions/2026-04-16-transmission-reception-architecture.md`

## Phase 5 progress (2026-04-19)

### Resolved in 5a–5b

- [x] **35. Pin ICAO Doc 4444 edition number** (see above)
- [x] **P5-R1. Instruction ADT expansion** — BreakOff, Disregard added; vectoring/level/speed/approach types already existed in protocol
- [x] **P5-R2. Citation triples** — `edition` field on RegulationRef; all 35 DB entries updated
- [x] **P5-R3. ReadbackVerdict 2→4** — Missing + Refused variants; processReadback routing specified
- [x] **P5-R4. ArrivalSequence state** — ArrivalSlot, ArrivalGate sealed hierarchy, FollowTarget 5-state lifecycle
- [x] **P5-R5. Atomic migration** — arrivals project from ArrivalSequence; TOWER_ARRIVAL self-enqueue deleted
- [x] **P5-R6. Supersession semantics** — SupersessionRelation with ABANDON/ABSORB; TurnBase→ExtendDownwind landed
- [x] **P5-R7. Coordination message ADT** — 8 CoordinationMessage variants + HandoffGate

### Deferred from Phase 5 reviews (tracked)

| # | Item | Status |
|---|------|--------|
| P5-D1 | `deriveApproachMode` — now derives from `issuedClearances` ClearedApproach/ClearedVisualApproach; flight-rules heuristic is fallback | **RESOLVED** |
| P5-D2 | `deriveGate` — now produces `LocaliserEstablished` from `EstablishedLocaliser` report via `establishedLocaliser` belief set | **RESOLVED** |
| P5-D3 | Distance — now BFS path-following along circuit adjacency graph; Euclidean fallback when off-graph | **RESOLVED** |
| P5-D4 | FollowTarget — `UNABLE` state added to `AcquisitionState` enum | **RESOLVED** |
| P5-D5 | BreakOff readback — dedicated `BreakOffReadback` atom replaces `GoAroundReadback(null,null)` | **RESOLVED** |
| P5-D6 | Stable number reassignment — live: numbers shift on removal/insertion via `relativeOrderPreserved` check | **RESOLVED** |
| P5-D7 | Supersession — 8 relations: Orbit→ExtendDownwind, TurnBase→Orbit, 4 speed pairs (ABSORB), ClearedToLand→TurnBase | **RESOLVED** |
| P5-D8 | Disregard — universal superseder: ABANDON all pending readbacks for aircraft | **RESOLVED** |
| P5-D9 | Inject relations — `applySupersessionCleanup` accepts `relationsIndex` parameter with default | **RESOLVED** |
| P5-D10 | `RunwayStatusChange` coordination message (TWR→APP) for closures/re-openings | Phase 6+ |
| P5-D11 | `var nextNumber` — replaced with fold-based `SlotAcc` accumulator | **RESOLVED** |

## Phase 6 progress (2026-04-20)

### Resolved in Phase 6

- [x] **P6-R1. Wake categories** — WakeCategory enum (J/H/M/L) on AircraftObservation; ICAO baseline table (8 entries); unknown defaults to H
- [x] **P6-R2. Separation engine** — two-phase: Phase A early assessment → beliefs, Phase B reactive safety net; absolute-margin comfort formula with closure adjustment
- [x] **P6-R3. Intervention hierarchy** — SpeedControl → PathExtension → OrbitHold → GoAround; skip predicates (inside FAF, high closure)
- [x] **P6-R4. Belief-delta detection** — ObservationDelta (speed trend, vertical rate) from observation history buffer
- [x] **P6-R5. LVP infrastructure** — lvpMode flag on ControllerView
- [x] **P6-R6. Visual separation decision** — canApplyVisualSeparation with geometry, LVP, FL100, FollowTarget gates
- [x] **P6-R7. SeparationConcernAbove guard** — replaces SpacingNotAdequate; reads from separation assessments in beliefs

### Deferred from Phase 6 reviews — resolved in root-cause fix phases R0-R3

| # | Item | Status |
|---|------|--------|
| P6-D1 | Phase B emission wired: reactive GoAround/BreakOff by runway state + TrafficInfo companion | **RESOLVED** (R0a) |
| P6-D2 | BreakOff when runway clear, GoAround when occupied — implemented in emitReactiveOutputs | **RESOLVED** (R0a) |
| P6-D3 | Traffic info accompanies every reactive intervention (Doc 4444 §5.10.1.1) | **RESOLVED** (R0a) |
| P6-D4 | `takeoffRollStartedAt` not populated — conservative stopgap using `lastOperationCompletedAt` | Acceptable permanent (R3b) |
| P6-D5 | NM-scale test world built (`testWorldNmScale`) with realistic distances | **RESOLVED** (R2a) |
| P6-D6 | All 8 ICAO wake table entries verified per-row | **RESOLVED** (R2c) |
| P6-D7 | Intervention selection: 5 paths tested (GoAround, null, SpeedControl, PathExtension, FAF skip) | **RESOLVED** (R2c) |
| P5-D10 | `RunwayStatusChange` coordination message (TWR→APP) for closures/re-openings | Phase 7+ |

### Root-cause fixes (R0-R3)

All 20 findings from the deep adversarial review resolved:

| Phase | Fixes | Findings |
|-------|-------|----------|
| R0 | Reactive emission wired, TurnBase deadlock guard, SAFETY bypasses feasibility | S1, S3, D7 |
| R1 | SeparationConcern sealed interface, computeConcern internal+positional, readback atoms wired, BreakOff compound | D5, D4, D6, S4, S5 |
| R2 | NM-scale test world, all separation tests rewritten, all-pairs assessment, groundSpeed from sim | T1-4, D1, D3 partial |
| R3 | Hysteresis (RecentDecision), wake timer in grantPhase, time-based ordering, readback enrichments, departure gap analysis | D2, S2, D3, P1-5, departure starvation |

## Cross-aircraft TrafficRef (resolved)

Items 12, 15, and 17 were resolved using compound ProposedAction with optional companion fields
(SequenceInfo, TrafficInfo) rather than a general enrichment framework. Actions that need
cross-aircraft references populate them directly. The pipeline emits companion outputs alongside
the primary instruction. If we hit 5+ companion types, generalise to a pipeline enrichment step.
