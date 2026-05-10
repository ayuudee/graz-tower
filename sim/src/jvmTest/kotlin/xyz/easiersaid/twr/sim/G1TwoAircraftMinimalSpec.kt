package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.WakeCategory
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstControllerInstructionOf
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.requiredStartPoints
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

/**
 * G1 minimal pin — two aircraft, `circuits=1` each at LOWG.
 *
 * **Scope-narrower** for `G1TwoAircraftCircuitsTest` (which exercises
 * `circuits=2` plus a wake-rule pin and conflict-resolution chain). The
 * minimal pin's job is the multi-aircraft commitment-stage / coordination-
 * ledger / runway-duty closure proof per fn-8.3 spec acceptance bullet 5:
 *   - both aircraft complete their missions;
 *   - each aircraft's vacate / `BacktrackRunway` readback closes its
 *     coordination — the coordination entry is **absent** from
 *     `BeliefState.coordinations` after correct readback (the model
 *     encodes closure as absence, not a `Closed` state);
 *   - `RunwayDutyState.holder` is `null` OR the next queued aircraft
 *     after the last aircraft vacates (NOT the just-vacated one);
 *   - B receives a runway slot — i.e. an instruction targeting B that
 *     consumes the runway is transmitted (e.g. `LineUpAndWait`).
 *
 * Catches the failure at a smaller scenario shape than G1's
 * `circuits=2`, so future single-circuit refactors fail loudly before
 * reaching G1.
 *
 * Mission shape: both aircraft fly **one** full-stop circuit
 * (`outcomes = listOf(CircuitOutcome.FullStop)` — first and only circuit
 * IS full-stop, no T&G). This avoids the `TouchAndGoCircuitTask` /
 * mid-circuit intent flip that fn-8.3 fixed in B5-α; the goal here is
 * pure multi-aircraft sequencing on a single runway.
 *
 * @see G1TwoAircraftCircuitsTest the two-circuit sibling.
 * @see G3aPilotTrainedGoAroundTest the single-aerodrome trained-GA sibling
 *      (instructor-authored `CircuitOutcome.GoAround` on circuit 1 — the
 *      closure pattern G1-minimal pioneered for vacate-coordinations is
 *      reused at the single-aircraft scale by G3a's R7 pin).
 * @see G3aRunwayObstructionTest the single-aerodrome ATC-instructed-GA
 *      sibling (reactive go-around on a world-authored runway obstruction;
 *      same R7 vacate-coordination closure invariant on the recovery
 *      landing).
 */
class G1TwoAircraftMinimalSpec {

    @Test
    fun `two AI aircraft fly one full-stop circuit each at LOWG`() {
        // ── World + controllers via the shared fixture ──────────────────────
        val fixture = Fixtures.LOWG_TWO_AIRCRAFT
        val loaded = fixture.load().getOrElse {
            fail("LOWG_TWO_AIRCRAFT fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) {
            "GROUND missing from LOWG_TWO_AIRCRAFT fixture"
        }
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) {
            "TOWER missing from LOWG_TWO_AIRCRAFT fixture"
        }

        // ── Two AI aircraft at adjacent LOWG stands ─────────────────────────
        val aircraftAId = AircraftId("OE-ABC")
        val aircraftBId = AircraftId("OE-DEF")
        val now = SimTime.ZERO
        val startPoints = fixture.requiredStartPoints()
        val standPointA = startPoints.getValue(aircraftAId)
        val standPointB = startPoints.getValue(aircraftBId)

        // fn-11.1: typed-outcome migration — listOf(FullStop) is the
        // structurally equivalent shape for the old (circuits=1, fullStopOnLast=true).
        // ONE full-stop circuit per aircraft.
        val oneFullStopCircuit = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop))
        val missionA = createMission(
            goal = oneFullStopCircuit,
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans[aircraftAId]
                ?: fail("LOWG_TWO_AIRCRAFT fixture missing flight plan for $aircraftAId"),
        )
        val missionB = createMission(
            goal = oneFullStopCircuit,
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = fixture.flightPlans[aircraftBId]
                ?: fail("LOWG_TWO_AIRCRAFT fixture missing flight plan for $aircraftBId"),
        )

        val aircraftA = AircraftState(
            id = aircraftAId,
            callsign = Callsign("OEABC"),
            position = loaded.world.geometry.points.getValue(standPointA),
            positionPoint = standPointA,
            phase = PilotPhase.AtStand,
            type = AircraftType.C172,
            pilotMission = missionA,
        )
        val aircraftB = AircraftState(
            id = aircraftBId,
            callsign = Callsign("OEDEF"),
            position = loaded.world.geometry.points.getValue(standPointB),
            positionPoint = standPointB,
            phase = PilotPhase.AtStand,
            type = AircraftType.C172,
            pilotMission = missionB,
        )

        // ── Build SimState through the smart constructor ────────────────────
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraftA, aircraftB),
            controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(lowg to fixture.weather),
        ).getOrElse { error("SimState.initial rejected the LOWG_TWO_AIRCRAFT fixture: $it") }

        // ── ATIS + drive ────────────────────────────────────────────────────
        // 60 sim minutes ceiling. Two C172s with single-runway gating: A
        // departs first, flies one full-stop circuit, vacates; B then gets
        // the runway, flies its circuit, vacates. Mirrors G0's wall pattern,
        // doubled for the second aircraft. A wedged run hits the wall.
        val until = SimTime.ZERO + SimDuration.ofMillis(60 * 60 * 1000L)
        val lowgAtis = Atis(
            letter = 'A',
            aerodrome = lowg,
            configuration = RunwayConfiguration(
                arrivals = listOf(RunwayId("16C")),
                departures = listOf(RunwayId("16C")),
            ),
            wind = Wind.unsafe(160, 8),
            qnh = null,
            visibility = null,
            generatedAt = now,
        )
        // Same 2-min mission-start offset as G1: A starts immediately, B's
        // first PilotDecisionTick fires at T+2 min so the lead-trail
        // ordering is structural (not timing-coincidence). The single-
        // runway gate then serializes them naturally — B departs after A
        // vacates.
        val bMissionStartOffset = SimDuration.ofMillis(2 * 60 * 1000L)
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = lowgAtis),
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftAId),
            SimEvent.PilotDecisionTick(time = now + bMissionStartOffset, aircraftId = aircraftBId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )
        val (finalState, records, _) = runUntilWithStateTrace(initialState, initialEvents, until)

        // ── Diagnostic preamble (per-aircraft) ─────────────────────────────
        val journeyA = finalState.formatJourney(aircraftAId, records)
        val journeyB = finalState.formatJourney(aircraftBId, records)
        val journey = "── Aircraft A ($aircraftAId) ──\n$journeyA\n\n" +
            "── Aircraft B ($aircraftBId) ──\n$journeyB"
        println(journey)

        // ── Outcome: both aircraft complete their missions and park ─────────
        val finalA = finalState.aircraft.getValue(aircraftAId)
        val finalB = finalState.aircraft.getValue(aircraftBId)
        val finalMissionA = checkNotNull(finalA.pilotMission) {
            "Aircraft A lost its mission.\n$journey"
        }
        val finalMissionB = checkNotNull(finalB.pilotMission) {
            "Aircraft B lost its mission.\n$journey"
        }

        check(finalMissionA.isComplete) {
            "A mission did not complete within 60 sim minutes.\n$journey"
        }
        check(finalMissionB.isComplete) {
            "B mission did not complete within 60 sim minutes.\n$journey"
        }
        check(finalA.altitudeM == 0.0) {
            "A is not on the ground at end of run.\n$journey"
        }
        check(finalB.altitudeM == 0.0) {
            "B is not on the ground at end of run.\n$journey"
        }
        check(finalA.phase == PilotPhase.Parked || finalA.phase == PilotPhase.AtStand) {
            "A did not return to a stand.\n$journey"
        }
        check(finalB.phase == PilotPhase.Parked || finalB.phase == PilotPhase.AtStand) {
            "B did not return to a stand.\n$journey"
        }

        val standPoints = loaded.world.aerodromes
            .getValue(lowg)
            .stands.values.map { it.point }.toSet()
        check(finalA.positionPoint in standPoints) {
            "A did not end at a LOWG stand point. positionPoint=${finalA.positionPoint}; " +
                "valid stand points: $standPoints.\n$journey"
        }
        check(finalB.positionPoint in standPoints) {
            "B did not end at a LOWG stand point. positionPoint=${finalB.positionPoint}; " +
                "valid stand points: $standPoints.\n$journey"
        }

        // ── Coordination-ledger closure: vacate / BacktrackRunway readbacks
        //    close their coordination (entry absent from
        //    BeliefState.coordinations after readback), per fn-8.3 acceptance
        //    bullet 5 sub-bullet (b). The model encodes closure as absence
        //    (Pass 12 D-AUDIT.2.E + spec § Phase 1 evidence): no matching
        //    entry remains in Issued / Querying / Reissued / LostCommsDeclared
        //    states.
        // ─────────────────────────────────────────────────────────────────────
        val towerBeliefs = checkNotNull(finalState.beliefs[tower.id]) {
            "Tower beliefs missing at end of run — controller pipeline regression.\n$journey"
        }
        // Closure is "absent from coordinations". The post-run ledger should
        // not contain any AfterLandingVacateVia or BacktrackRunway entries
        // for either aircraft, regardless of state.
        for (acId in listOf(aircraftAId, aircraftBId)) {
            val acCoords = towerBeliefs.coordinations[acId].orEmpty()
            val vacateClass = acCoords.filter { coord ->
                coord.instruction is AfterLandingVacateVia ||
                    coord.instruction is BacktrackRunway
            }
            check(vacateClass.isEmpty()) {
                "Vacate/BacktrackRunway coordination did not close for $acId — entries still " +
                    "present in BeliefState.coordinations: $vacateClass. " +
                    "fn-8.3 spec § acceptance #5: each aircraft's vacate readback closes " +
                    "its coordination (the entry is absent from coordinations after correct " +
                    "readback / supersession).\n$journey"
            }
        }

        // ── RunwayDutyState.holder is null OR the next queued aircraft —
        //    NOT the just-vacated one. After both aircraft have vacated, the
        //    duty state must not still hold either of them as the active
        //    holder. fn-8.3 acceptance bullet 5 sub-bullet (c).
        // ─────────────────────────────────────────────────────────────────────
        val runwayDuty = checkNotNull(towerBeliefs.runwayDuty) {
            "Tower runwayDuty missing at end of run — runway-duty machine regression.\n$journey"
        }
        check(runwayDuty.holder == null) {
            "RunwayDutyState.holder must be null after both aircraft vacate. Got " +
                "holder=${runwayDuty.holder} (just-vacated aircraft retained — runway-duty " +
                "release path broken).\n$journey"
        }

        // ── B receives a runway slot: an instruction targeting B that
        //    consumes the runway is transmitted (LineUpAndWait covers the
        //    canonical "you have the runway, line up" doctrine).
        //    fn-8.3 acceptance bullet 5 sub-bullet (d).
        // ─────────────────────────────────────────────────────────────────────
        val lineUpB = records.firstControllerInstructionOf<LineUpAndWait>(aircraftBId)
        check(lineUpB.isSome()) {
            "Expected at least one LineUpAndWait for $aircraftBId — without a runway slot " +
                "instruction, B never received its turn on the runway. fn-8.3 acceptance " +
                "#5(d): B must receive a runway slot.\n$journey"
        }

        // ── Doctrinal serialization: A's ClearedToLand precedes B's. Both
        //    aircraft fly full-stop; the runway-duty machine must
        //    sequence them (A leads via the 2-min mission-start offset).
        // ─────────────────────────────────────────────────────────────────────
        val landAMs = records.firstControllerInstructionOf<ClearedToLand>(aircraftAId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one ClearedToLand for $aircraftAId on the " +
                    "full-stop circuit — controller never cleared A to land.\n$journey")
            }
        val landBMs = records.firstControllerInstructionOf<ClearedToLand>(aircraftBId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one ClearedToLand for $aircraftBId on the " +
                    "full-stop circuit — controller never cleared B to land.\n$journey")
            }
        check(landAMs < landBMs) {
            "Final-circuit landing order: A's ClearedToLand (${landAMs}ms) must precede " +
                "B's (${landBMs}ms). With B's 2-min mission-start offset, A reaches the " +
                "circuit ahead of B; reversal would indicate the lead-trail ordering broke " +
                "under conflict resolution.\n$journey"
        }

        // ── Wake-category sanity (R7 sibling): both are C172 → WakeCategory.L.
        // ─────────────────────────────────────────────────────────────────────
        check(finalA.type.wakeCategory == WakeCategory.L) {
            "A wake category drift: expected L, got ${finalA.type.wakeCategory}.\n$journey"
        }
        check(finalB.type.wakeCategory == WakeCategory.L) {
            "B wake category drift: expected L, got ${finalB.type.wakeCategory}.\n$journey"
        }

        // ── Belt-and-braces: the runway-duty machine actually sequenced
        //    both aircraft (lastOperationCompletedAt is at-or-after both
        //    landings, proving the machine processed each one).
        // ─────────────────────────────────────────────────────────────────────
        val lastOpMs = runwayDuty.lastOperationCompletedAt?.millis
        checkNotNull(lastOpMs) {
            "RunwayDutyState.lastOperationCompletedAt is null — duty machine never recorded " +
                "an operation completion despite both aircraft landing.\n$journey"
        }
        check(lastOpMs >= landBMs) {
            "RunwayDutyState.lastOperationCompletedAt (${lastOpMs}ms) must be ≥ B's " +
                "ClearedToLand (${landBMs}ms) — the machine should have processed both " +
                "operations end-to-end.\n$journey"
        }

        // Reference (suppress unused-import warning on ControllerOutput / Dispatch):
        // the journey diagnostic uses these via formatJourney; keep the imports
        // visible to readers diffing this against G1TwoAircraftCircuitsTest.
        @Suppress("UNUSED_VARIABLE")
        val _classRefs = ControllerOutput::class to Dispatch::class
    }
}
