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
- [ ] **35. Pin ICAO Doc 4444 edition number**

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

## Cross-aircraft TrafficRef (resolved)

Items 12, 15, and 17 were resolved using compound ProposedAction with optional companion fields
(SequenceInfo, TrafficInfo) rather than a general enrichment framework. Actions that need
cross-aircraft references populate them directly. The pipeline emits companion outputs alongside
the primary instruction. If we hit 5+ companion types, generalise to a pipeline enrichment step.
