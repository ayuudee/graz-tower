---
satisfies: [R4, R7, R9]
---

## Description

Phase F of the G2 multi-phase plan. Builds the `G2CrossAerodromeVfrTest` integration test — the cross-aerodrome equivalent of G0's `LowgGoldenTest` — exercising aircraft from LOWG stand through cruise to LJMB stand end-to-end (~50–75 min wall-clock block).

Test depth mirrors G0 (~500 lines, single `@Test` method). All assertions are observable via the public sim/test APIs — no widening of any allowlisted firewall surface (R7).

Adds a new test extension `firstPilotInitialContactTo(controllerId)` to `TransmissionRecord.kt` (alongside existing `firstControllerInstructionOf`, `firstPilotReportOf`) — the multi-aerodrome ATIS letter assertion needs to filter records by destination controller.

**Size:** M
**Files (expected):**
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/G2CrossAerodromeVfrTest.kt` (new, ~500 lines)
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/TransmissionRecord.kt` (extension addition)

## Approach

- Mirror `LowgGoldenTest`'s structure exactly: single `@Test` method, load fixture → seed initial state → seed initial events → `runUntilWithStateTrace` → outcome assertions + state-trace walks + journey-formatted diagnostics. **Do not over-abstract**: the rich-assertion shape with inline `check { ... \n$journey }` is the proven pattern.
- Use `Fixtures.LOWG_LJMB_VFR.load()` (from fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.1).
- Snapshot anchoring: index by **semantic event** (RST readback, first transmission to LJMB, first knownStrips snapshot, etc.) — NOT by absolute tick. Filter `stateTrace.filter { (event, _) -> event.time.millis in tRelease until tContact }` for the mid-gap window.
- Cross-aerodrome handoff window pin (R4): `midGapStates.isNotEmpty()` AND `tContact - tRelease >= 30_000ms` AND no LOWG controller has `responsibilities[ac] is Owned` anywhere in the window AND `∃ snapshot` where `LJMB_TWR.knownStrips.containsKey(ac)`. Document the predicate inline so a refactor can't regress it silently.
- Autonomous-contact provenance pin (R4 / R7): no `ContactFrequency` in `records` directs the aircraft to LJMB. Predicate: `(instr.frequency == LJMB_TWR.frequency) || (instr.role == TOWER && speakerControllerAerodrome(rec) == ljmb)`. If this fires, a back-channel handoff has snuck in.
- Multi-aerodrome ATIS pins (R4): use the new `firstPilotInitialContactTo(LOWG_GND.id).atisCode == 'A'` and `firstPilotInitialContactTo(LJMB_TWR.id).atisCode == 'B'`. Embeds the lazy-read ATIS lookup contract.
- Time band (R4 / practice-scout): `until = SimTime.ZERO + SimDuration.ofMillis(90 * 60 * 1000L)` (90 min wall ceiling); time-band assertion `completionMs in 50 * 60 * 1000L..75 * 60 * 1000L` (50–75 min). Include margin breakdown in code comment per practice-scout (taxi-out ~8-12 min + run-up 60s + climb 3-5 min + cruise 15-20 min for ~32 NM at C172 110 KTAS + descent + TMA 8-12 min + pattern + landing 8-12 min + taxi-in 3-5 min = ~50-75 min).
- Forbidden test patterns (R7 / practice-scout): no shared `SimulationState` peeked by both controllers; no global event bus the test asserts against; no `world.aircraft.position` reads in test setup; no pilot reads of `world.aerodromes[ljmb].activeRunway` from sim state (pilot must read via filing or ATIS broadcast only).

## Investigation targets

**Required** (read before coding):
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/LowgGoldenTest.kt:52-487` — the reference shape. Especially line ranges 53-138 (construction), 144-159 (outcome), 174-216 (state-trace walks), 218-233 (time band), 447-486 (mid-gap window — note: G2's window is shape-different per R5).
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/RunUntil.kt:75-99` — `runUntilWithStateTrace` return type.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/TransmissionRecord.kt:42-73` — existing extensions (`firstControllerInstructionOf`, `firstPilotReportOf`) to mirror.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/testing/JourneyFormatter.kt:11-54` — `formatJourney` for diagnostic preamble.
- `sim/src/jvmTest/kotlin/xyz/easiersaid/twr/sim/CrossAerodromeFilingSpec.kt:138-180` — Pass 14 cross-aerodrome filing distribution shape (LJMB_TOWER receives strip via knownStrips).
- `sim/src/commonMain/kotlin/xyz/easiersaid/twr/sim/Step.kt:1484-1554` — `applyTwoWayCommsEstablished` knownStrips arm (the post-state Phase F asserts on).
- `protocol/src/commonMain/kotlin/xyz/easiersaid/twr/protocol/PilotTransmission.kt:200-207` — `InitialContact` fields (especially `atisCode`).
- `controller/src/commonMain/kotlin/xyz/easiersaid/twr/controller/bdi/Action.kt:72-81` — `Dispatch.Direct` shape for ContactFrequency provenance pin.

**Optional** (reference as needed):
- `pilot/src/commonMain/kotlin/xyz/easiersaid/twr/pilot/PilotMission.kt:110-135` (Phase C `transitContactRep` slice — Phase F asserts mission.transitContactRep flips to Some(rep) at the right time).

## Acceptance

- [ ] `G2CrossAerodromeVfrTest` mirrors `LowgGoldenTest` structurally: single `@Test`, ~500 lines, load + seed + run + assert flow.
- [ ] `firstPilotInitialContactTo(controllerId: ControllerId)` extension added to `TransmissionRecord.kt`, returning the first `InitialContact` whose receiver matches.
- [ ] Outcome assertions: `mission.isComplete`; aircraft phase ∈ {Parked, AtStand}; altitude 0; positionPoint == fixture's `destinationStandPointId` (or ∈ stand-equivalent set).
- [ ] Filing-cardinality pin: `loaded.initialEvents.filterIsInstance<SimEvent.FlightPlanFiled>().size == 2` with comment noting pre-augmentation read.
- [ ] Pre-radio activeRunway pin: walk stateTrace forward; first state with mission constructed has `activeRunway = Some(RunwayAssignment(RunwayId("14"), Filing))`.
- [ ] Cross-aerodrome handoff window pin, all sub-clauses (non-empty, ≥30s gap, no LOWG-Owned anywhere in window, ∃ snapshot with LJMB knownStrips).
- [ ] Post-contact snapshot: `LJMB_TWR.responsibilities[ac] is Owned`; `LJMB_TWR.knownStrips.containsKey(ac) == false`; no LOWG controller has the aircraft.
- [ ] Multi-aerodrome ATIS pins: LOWG initial-contact has letter 'A'; LJMB initial-contact has letter 'B'.
- [ ] Autonomous-contact provenance pin fires on the named predicate (frequency match OR role+aerodrome match).
- [ ] Time band 50–75 min with margin breakdown documented in code.
- [ ] Forbidden test patterns absent (no shared state peeks, no global bus, no `world.aircraft.position` in test setup, no pilot reading destination runway via sim state).
- [ ] G0 remains green; pre-existing master failures (LJMB-fixture related) resolved by fn-5-g2-cross-aerodrome-vfr-transit-lowg-ljmb.1.
- [ ] 3-agent plan review (impact, fp-review, test-review) before implementation; 3-agent post-impl review on the diff. Findings folded.
- [ ] Commit as `G2 Phase F: G2CrossAerodromeVfrTest + firstPilotInitialContactTo` with `Co-Authored-By` tail.


## Done summary

_Populated at task completion via `flowctl done <id> --summary-file ...`_

## Evidence

_Populated at task completion via `flowctl done <id> --evidence-json ...`_
