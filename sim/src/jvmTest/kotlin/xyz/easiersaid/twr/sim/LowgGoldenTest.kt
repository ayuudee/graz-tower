package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.PilotRoute
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiToStand
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.sim.ReceiverRef
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.LoadedFixture
import xyz.easiersaid.twr.sim.testing.firstControllerInstructionOf
import xyz.easiersaid.twr.sim.testing.firstPilotReportOf
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

/**
 * G0 — single-aerodrome LOWG VFR circuit-training golden test.
 *
 * Parked AI aircraft at LOWG, instructed to fly one circuit and return to the
 * stand. The outermost test asks: did it get there, and did nothing crazy
 * happen along the way? No split into 900 unit tests; the run itself is the
 * test, the assertions are what the run produced.
 *
 * Pass 4: typed [TransmissionRecord]s replace opaque-string assertions; the
 * shared `runUntilWithTransmissions` drives the sim; `Fixtures.LOWG` collapses
 * the world-loading ceremony.
 */
class LowgGoldenTest {

    @Test
    fun `AI aircraft flies one VFR circuit at LOWG and returns to stand`() {
        // ── World + controllers via the shared fixture ──────────────────────
        val loaded = Fixtures.LOWG.load().getOrElse {
            fail("LOWG fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val groundId = ControllerId("LOWG_GROUND")
        val towerId = ControllerId("LOWG_TOWER")
        val ground = checkNotNull(loaded.controllers[RoleName.GROUND]) { "GROUND missing from fixture" }
        val tower = checkNotNull(loaded.controllers[RoleName.TOWER]) { "TOWER missing from fixture" }

        // ── One AI aircraft at the stand, mission = one full-stop circuit ──
        val aircraftId = AircraftId("OE-ABC")
        val now = SimTime.ZERO
        val mission = createMission(
            goal = HighLevelGoal.CircuitTraining(circuits = 1, fullStopOnLast = true),
            startPhase = PilotPhase.AtStand,
            time = now,
        )
        val aircraft = AircraftState(
            id = aircraftId,
            callsign = Callsign("OEABC"),
            position = loaded.world.geometry.points.getValue(Fixtures.LOWG.standPointId),
            positionPoint = Fixtures.LOWG.standPointId,
            phase = PilotPhase.AtStand,
            pilotMission = mission,
        )

        // ── Build SimState through the smart constructor ────────────────────
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(lowg to Fixtures.LOWG.weather),
        ).getOrElse { error("SimState.initial rejected the LOWG fixture: $it") }

        // ── Drive ────────────────────────────────────────────────────────────
        // 30 sim minutes. A full-stop circuit at typical VFR cadence should land
        // in well under that. If we hit the wall the run wedged.
        val until = SimTime.ZERO + SimDuration.ofMillis(30 * 60 * 1000L)
        val initialEvents = listOf(
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )
        val (finalState, records, stateTrace) = runUntilWithStateTrace(initialState, initialEvents, until)

        // ── Diagnostic preamble ─────────────────────────────────────────────
        val journey = finalState.formatJourney(aircraftId, records)
        println(journey)

        // ── Outcome (high-level): the aircraft got back to a stand ──────────
        val finalAircraft = finalState.aircraft.getValue(aircraftId)
        val finalMission = checkNotNull(finalAircraft.pilotMission) { "Aircraft lost its mission" }

        check(finalMission.isComplete) {
            "Mission did not complete within 30 sim minutes.\n$journey"
        }
        check(finalAircraft.altitudeM == 0.0) {
            "Aircraft is not on the ground at end of run.\n$journey"
        }
        check(finalAircraft.phase == PilotPhase.Parked || finalAircraft.phase == PilotPhase.AtStand) {
            "Aircraft did not return to the stand.\n$journey"
        }
        check(finalAircraft.route is PilotRoute.None || finalAircraft.route is PilotRoute.Ground) {
            "Aircraft still has an airborne route at end of run.\n$journey"
        }

        // ── Firewall + circuit-intent path verification ─────────────────────
        // These are not scaffold assertions: they prove the *single* G0 run
        // exercised the firewall path the way the plan claims. A silent
        // regression where the aircraft lands by some other route (e.g.,
        // controller defaulting to T&G then accidentally landing full-stop)
        // would still satisfy the outcome assertions above; these don't.

        // (a) Tower issued at least one ClearedToLand to the aircraft.
        check(records.firstControllerInstructionOf<ClearedToLand>(aircraftId).isSome()) {
            "Expected at least one ClearedToLand for $aircraftId — controller should have " +
                "cleared the pilot to land based on the FULL_STOP circuit-intent declared on " +
                "downwind.\n$journey"
        }

        // (b) Tower did NOT issue ClearedTouchAndGo (pilot declared FULL_STOP).
        check(records.firstControllerInstructionOf<ClearedTouchAndGo>(aircraftId).isNone()) {
            "Unexpected ClearedTouchAndGo for $aircraftId — pilot declared FULL_STOP on downwind, " +
                "controller should not have offered T&G.\n$journey"
        }

        // (b') Pass 9 post-impl test-review Add-2 (revised): no
        // coordination should reach LostCommsDeclared on the G0 happy path.
        //
        // Some Pass 9 escalation activity (ConfirmInstruction emissions,
        // COORD-REISSUE outputs) is *design-intentional* for instructions
        // with no required-atom readback (e.g., ExtendDownwind — see
        // TowerArrival.kt KDoc). Asserting zero escalation outputs would
        // collide with that design. The truly-bad terminal is
        // `LostCommsDeclared`: it means the controller exhausted the
        // escalation ladder without ever receiving acknowledgement —
        // which on a happy-path single-aircraft circuit must not happen.
        val anyLostComms = finalState.beliefs.values.any { b ->
            b.coordinations.values.any { coords ->
                coords.any { it.state is xyz.easiersaid.twr.controller.observe.CoordinationState.LostCommsDeclared }
            }
        }
        check(!anyLostComms) {
            "G0 happy path reached LostCommsDeclared on at least one coordination — " +
                "the escalation ladder ran to terminal without successful readback. " +
                "Either the lifecycle thresholds drifted below natural pilot latency or " +
                "readback delivery is broken.\n$journey"
        }

        // (c) A vacate instruction was issued (AfterLandingVacateVia OR BacktrackRunway).
        check(
            records.firstControllerInstructionOf<AfterLandingVacateVia>(aircraftId).isSome() ||
                records.firstControllerInstructionOf<BacktrackRunway>(aircraftId).isSome()
        ) {
            "Expected a vacate instruction (AfterLandingVacateVia or BacktrackRunway) for " +
                "$aircraftId in the instruction stream.\n$journey"
        }

        // (d) Tower's BeliefState.circuitIntent records FULL_STOP for the aircraft.
        // The pilot declared FULL_STOP on downwind; the controller's deriveFromReport
        // translates ReportEvent.Downwind(intent=FULL_STOP) into a CircuitIntentReported
        // event; withCircuitIntentEvents writes it to the belief slice.
        val towerBeliefs = checkNotNull(finalState.beliefs[tower.id]) {
            "Tower belief state missing at end of run.\n$journey"
        }
        check(towerBeliefs.circuitIntent[aircraftId] == CircuitIntent.FULL_STOP) {
            "Tower's circuitIntent belief should be FULL_STOP for $aircraftId. " +
                "Got: ${towerBeliefs.circuitIntent}\n$journey"
        }

        // (e) Intent-provenance: ClearedToLand strictly after the first Downwind report.
        // A regression where the controller decides FULL_STOP off some other channel
        // (an accidental peek at pilot state, a stale belief from a previous mission,
        // a strip widening that smuggled per-circuit decisions) could still produce a
        // ClearedToLand earlier — this assertion fails if so. Same-tick is impossible
        // because (i) the Downwind transmission has nonzero duration (~2s) and (ii)
        // the controller folds events on TransmissionEnd-derived inbox delivery,
        // never on TransmissionStart.
        val downwindMs = records.firstPilotReportOf<ReportEvent.Downwind>(aircraftId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one Downwind report from $aircraftId — that is the only " +
                    "path circuit intent reaches the controller.\n$journey")
            }
        val landMs = records.firstControllerInstructionOf<ClearedToLand>(aircraftId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one ClearedToLand for $aircraftId.\n$journey")
            }
        check(landMs > downwindMs) {
            "ClearedToLand was issued at ${landMs}ms, at-or-before the first Downwind report " +
                "at ${downwindMs}ms. The radio→belief→guard path should be the *source* of " +
                "the FULL_STOP decision; a clearance at-or-before the report indicates either " +
                "a back-channel leak or a fold-order regression.\n$journey"
        }

        // (f) Phase F.8 — same-treatment-by-behaviour: AI waits the full RUN_UP_CHECKS dwell.
        // After the pilot-firewall removed the `!aircraft.humanPiloted ||` short-circuit
        // on TIMED step completion, the AI must wait the full RUN_UP_CHECKS dwell (≥10s
        // placeholder, D-AUDIT.3 raises to ~90s) before transmitting Report(Ready). The
        // previous AI fast-path made RUN_UP_CHECKS instant; a regression that re-introduces
        // it would pass every other G0 check (no field comes back, mission tree unchanged,
        // runway derivation unchanged) but cut ~10s off the time between TaxiTo apply and
        // Report(Ready). **Diagnostic:** if this fires, the TIMED short-circuit has reappeared
        // in PilotCognitive.kt:isStepComplete.
        val taxiMs = records.firstControllerInstructionOf<TaxiToHoldingPoint>(aircraftId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one TaxiToHoldingPoint to $aircraftId in the transmission stream.\n$journey")
            }
        val readyMs = records.firstPilotReportOf<ReportEvent.Ready>(aircraftId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one Report(Ready) from $aircraftId in the transmission stream.\n$journey")
            }
        val runUpDwellMs = readyMs - taxiMs
        check(runUpDwellMs >= 10_000L) {
            "Expected RUN_UP_CHECKS dwell ≥10s between TaxiTo issued (${taxiMs}ms) and " +
                "first Report(Ready) (${readyMs}ms); got ${runUpDwellMs}ms. The AI must wait " +
                "the same TIMED duration as a human pilot. Regression: the " +
                "`!aircraft.humanPiloted ||` short-circuit on CompletionMode.TIMED " +
                "(PilotCognitive.kt:isStepComplete) has reappeared.\n$journey"
        }

        // (g) D-PF.5 closure — strip dynamism narrowing, post-landing intent flip.
        // The behavioural consequence of the radio-driven intent flip from Departing
        // to Arriving is: AFTER the runway-vacate report, GROUND issues a TaxiTo
        // whose destination is a *stand* point (post-landing taxi-in), not a holding
        // point (which would be a departure clearance).
        //
        // Pre-D-PF.5: the flip was driven by the FlightStrip's dynamic re-read of
        // mission.activeCompound() per cycle (mind-reading via pre-briefing channel).
        //
        // Post-D-PF.5: filed-plan-only strip (CircuitTraining → Departing, set once).
        //
        // Post-Pass-5 (D-AUDIT.14 closure): no cached aircraftIntent slice; intent is
        // derived on demand from `recentRadio` and the strip via deriveCurrentIntent.
        // The pilot's Report(RunwayVacated) → AircraftArrivalCommitted fold writes into
        // recentRadio; reconcileCommitments sees the derived Arriving and stages a
        // GroundArrivalStage commitment; the GND-TAXI-STAND rule fires a stand-bound
        // TaxiTo. We assert on that observable consequence — end-of-run belief state
        // is unreliable because the commitment may complete and be cleaned up.
        val vacatedMs = records.firstPilotReportOf<ReportEvent.RunwayVacated>(aircraftId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one Report(RunwayVacated) from $aircraftId.\n$journey")
            }
        // Walk Ground's TaxiTos for this aircraft after the vacate. The FIRST
        // TaxiTo Ground issues post-vacate is the load-bearing one — it pins
        // that the intent flip drove a stand-bound (arrival) clearance, NOT a
        // holding-point (departure) clearance. A weaker assertion ("any
        // stand-bound TaxiTo somewhere after the vacate") would tolerate a
        // fold-order regression that issued a holding-point TaxiTo first
        // (departure-flow stuck) and only later corrected to a stand. Real
        // ATC observers would see that as a controller error; the test
        // names it.
        // Pass 6 tightens this assertion (Test G.4): TaxiTo split into
        // TaxiToHoldingPoint + TaxiToStand. The FIRST taxi Ground issues
        // post-vacate must be a `TaxiToStand` (sealed-type match, not a
        // `STAND` substring on a unified `TaxiTo` — the substring would
        // have matched `STANDPIPE`/`STAND_BAR_07` etc.).
        val firstGroundTaxiPostVacate = records.firstOrNull { rec ->
            val output = (rec.utterance as? Utterance.FromController)?.output as? ControllerOutput.Instruct ?: return@firstOrNull false
            val instr = (output.dispatch as? Dispatch.Direct)?.instruction
            (instr is TaxiToHoldingPoint || instr is TaxiToStand) &&
                instr.target == aircraftId &&
                rec.speaker == SpeakerRef.Controller(ground.id) &&
                rec.time.millis > vacatedMs
        } ?: fail(
            "Expected ≥1 TaxiToHoldingPoint or TaxiToStand from GROUND for $aircraftId after " +
                "Report(RunwayVacated). None found.\n" +
                "If this fires, the radio→event→belief→commitment→action chain has broken " +
                "before any post-landing taxi was issued.\n$journey"
        )
        val firstInstr = ((firstGroundTaxiPostVacate.utterance as Utterance.FromController).output as ControllerOutput.Instruct)
            .dispatch.let { (it as Dispatch.Direct).instruction }
        check(firstInstr is TaxiToStand) {
            "Expected the FIRST Ground taxi after Report(RunwayVacated) to be a TaxiToStand " +
                "(post-landing taxi-in flow). Got: $firstInstr.\n" +
                "If this fires, the GND-TAXI rule fired before GND-TAXI-STAND — the radio→\n" +
                "event→belief→commitment→action chain produced a departure-flow taxi for an\n" +
                "aircraft that had just landed. Suspect: deriveFromReport(RunwayVacated) not\n" +
                "emitting AircraftArrivalCommitted; withRecentRadio fold not consuming it;\n" +
                "deriveCurrentIntent not finding the most-recent Arriving event;\n" +
                "determineServiceKind not routing GROUND + Arriving + onTaxiway → GROUND_TAXI;\n" +
                "the GND-TAXI-STAND rule not winning over GND-TAXI in priority order; or the\n" +
                "party-line frequency broadcast has regressed to single-receiver routing.\n$journey"
        }
        // Pass 6 post-impl (Test-F.2): pin destination membership too.
        // Type-only assertion would pass for `TaxiToStand(destination=HOLD_A4)`
        // — a typo in GND-TAXI-STAND producing a holding-point destination
        // under the right type would ship clean. Real ATC sending an arriving
        // aircraft toward a holding point is the regression class to catch.
        val standPoints = loaded.world.aerodromes
            .getValue(xyz.easiersaid.twr.protocol.AerodromeId("LOWG"))
            .stands.values.map { it.point }.toSet()
        check((firstInstr as TaxiToStand).destination in standPoints) {
            "Expected the FIRST Ground TaxiToStand's destination to be a stand point at LOWG. " +
                "Got destination=${firstInstr.destination}; valid stand points are $standPoints.\n" +
                "If this fires, GND-TAXI-STAND is producing a TaxiToStand with the wrong " +
                "destination (likely a typo in TaxiToStandAction's nearestPoint over the " +
                "stand set, or a stale point reference).\n$journey"
        }

        // (h) Pass 7 (D-AUDIT.5) — typed mid-handoff transition window.
        // The GND→TWR handoff (after taxi-to-holding) is a real handoff in
        // G0. Pre-Pass-7 it was an instantaneous edge-flip; Pass 7
        // introduces the `HandingOff(Peer)` / `Watching(from)` overlap.
        // This assertion pins that the typed-state benefit is observable
        // end-to-end:
        //
        //   T1 = ContactFrequency(target=TOWER) emission time
        //   T2 = First pilot transmission to TOWER time (Report Ready)
        //   T1 < T2 (handoff has duration; not instantaneous)
        //   ∃ snapshot ∈ [T1, T2): GND HandingOff(Peer(TWR)) AND TWR Watching(from=GND)
        //
        // A regression that re-collapsed transfer to a single edge would
        // produce no overlap window with both states present.
        val cfTimes = records.filter { rec ->
            val output = (rec.utterance as? Utterance.FromController)?.output as? ControllerOutput.Instruct ?: return@filter false
            val instr = (output.dispatch as? Dispatch.Direct)?.instruction as? xyz.easiersaid.twr.protocol.ContactFrequency ?: return@filter false
            instr.target == aircraftId && instr.role == RoleName.TOWER
        }.map { it.time.millis }
        val cfToTwrMs = cfTimes.firstOrNull()
            ?: fail("Expected at least one ContactFrequency(role=TOWER) for $aircraftId.\n$journey")
        // First pilot transmission to the TOWER controller after the CF.
        val firstTxToTwrMs = records.firstOrNull { rec ->
            rec.utterance is Utterance.FromPilot &&
                rec.receiver == ReceiverRef.Controller(tower.id) &&
                rec.time.millis > cfToTwrMs
        }?.time?.millis
            ?: fail("Expected at least one pilot transmission to TOWER after ContactFrequency.\n$journey")
        check(cfToTwrMs < firstTxToTwrMs) {
            "Pass 7 assertion (h): expected handoff to have duration. " +
                "ContactFrequency(TOWER) at ${cfToTwrMs}ms; first pilot tx to TOWER at " +
                "${firstTxToTwrMs}ms.\n$journey"
        }
        // Find a state snapshot in the window where both states co-occur.
        val midHandoffStates = stateTrace.filter { (event, _) ->
            event.time.millis in cfToTwrMs until firstTxToTwrMs
        }
        val anyMidHandoffOverlap = midHandoffStates.any { (_, simState) ->
            val gndState = simState.controllers[ground.id]?.responsibilities?.get(aircraftId)
            val twrState = simState.controllers[tower.id]?.responsibilities?.get(aircraftId)
            gndState is xyz.easiersaid.twr.protocol.ResponsibilityState.HandingOff &&
                (gndState.target as? xyz.easiersaid.twr.protocol.HandoffTarget.Peer)?.controllerId == tower.id &&
                twrState is xyz.easiersaid.twr.protocol.ResponsibilityState.Watching &&
                twrState.from == ground.id
        }
        check(anyMidHandoffOverlap) {
            "Pass 7 assertion (h): expected at least one cycle in [$cfToTwrMs, $firstTxToTwrMs) " +
                "where GND holds HandingOff(Peer(TWR)) AND TWR holds Watching(from=GND). " +
                "Inspected ${midHandoffStates.size} state snapshots.\n" +
                "If this fires, the typed responsibility state machine is broken: either " +
                "applyContactFrequency didn't transition both controllers atomically, " +
                "or the two-way-comms-driven completion fired too eagerly (before the " +
                "actual pilot transmission to the new controller), collapsing the overlap.\n$journey"
        }
    }
}
