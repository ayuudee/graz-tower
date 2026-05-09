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
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.firstControllerInstructionOf
import xyz.easiersaid.twr.sim.testing.firstPilotReportOf
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.firstWhere
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
 *
 * @see G1TwoAircraftCircuitsTest the multi-aircraft same-aerodrome sibling
 *      (two C172s at LOWG, conflict-resolution chain, wake-rule pin).
 * @see G2CrossAerodromeVfrTest the cross-aerodrome single-aircraft sibling
 *      (LOWG → LJMB transit, autonomous arrival contact).
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
        // G2 Phase A: LoadedFixture.controllers is keyed by ControllerId so
        // multi-aerodrome fixtures can stage controllers that share a RoleName.
        // Single-aerodrome lookup goes through controllerByRole(role).
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) { "GROUND missing from fixture" }
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) { "TOWER missing from fixture" }

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
        // Pass 11 (D-AUDIT.6 / D-AUDIT.10): the loader emits
        // `SimEvent.FlightPlanFiled` events for every entry in the
        // fixture's `flightPlans`. Enqueue them ahead of the standard
        // ticks; the EventQueue's deterministic ordering processes them
        // first at the same time (System source orders before Controller).
        // Pass 14 (D-AUDIT.6.A-FOLLOWUP): G0 routing-cardinality pin.
        // The LOWG fixture files a single VFR circuit-training plan
        // (departureAerodrome == null destinationAerodrome). Per
        // `routeFiledPlan`'s single-aerodrome branch, this fans out to
        // exactly 1 recipient — the departure-side GROUND. A regression
        // that emitted 2 events for a circuit-training plan (e.g. a
        // routing predicate that misread `destination == null` as
        // "still cross-aerodrome") would not break G0's outcome
        // assertions, but would surface here.
        val filings = loaded.initialEvents.filterIsInstance<SimEvent.FlightPlanFiled>()
        check(filings.size == 1) {
            "G0 routing-cardinality regression: expected exactly 1 FlightPlanFiled " +
                "event for the circuit-training fixture, got ${filings.size}: " +
                "${filings.map { it.recipient }}"
        }
        // Pass 15 (D-AUDIT.8 closure): publish LOWG ATIS at sim-init.
        // The pilot reads it lazily at first contact; the controller's
        // `expectedAtisLetter[LOWG]` folds from `view.atis` and gates
        // the mismatch advisory.
        val lowgAtis = xyz.easiersaid.twr.protocol.Atis(
            letter = 'A',
            aerodrome = lowg,
            configuration = xyz.easiersaid.twr.protocol.RunwayConfiguration(
                arrivals = listOf(xyz.easiersaid.twr.protocol.RunwayId("16C")),
                departures = listOf(xyz.easiersaid.twr.protocol.RunwayId("16C")),
            ),
            wind = xyz.easiersaid.twr.protocol.Wind.unsafe(160, 8),
            qnh = null,
            visibility = null,
            generatedAt = now,
        )
        val atisEvent = SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = lowgAtis)
        val initialEvents = loaded.initialEvents + listOf(
            atisEvent,
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )
        val (finalState, records, trace) = runUntilWithStateTrace(initialState, initialEvents, until)

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

        // Pass 10 post-impl review M.1: doctrine-shaped mission-time band.
        // The wall (30 min) is too loose to detect climb-rate doctrine
        // drift — a regression that quartered the climb rate (3.7 → 0.9
        // m/s ≈ 180 fpm) would still ship green. Pin the mission-completion
        // time within a band aligned to one C172 circuit at LOWG: typical
        // end-to-end is ~10-20 sim minutes (taxi out + run-up + takeoff
        // + circuit + landing + taxi back). The band is wide enough to
        // tolerate small per-pass timing shifts but narrow enough to
        // catch a doctrine regression that materially alters climb cadence.
        // Pass 15 (D-AUDIT.7 / .8 fold-in) — derivation-match + ATIS-letter pins.
        // Per post-impl test review M3 + Impact M1: assert active runway is
        // ATIS-derived (not silently fallen back to wind-only), and the
        // pilot's first InitialContact carries the published letter.
        val firstControllerBeliefs = trace.firstWhere { st ->
            st.beliefs[tower.id]?.activeRunway != null
        }.getOrNull()?.state?.beliefs?.get(tower.id)
        check(firstControllerBeliefs?.activeRunway == xyz.easiersaid.twr.protocol.RunwayId("16C")) {
            "Pass 15: tower's BeliefState.activeRunway must derive from ATIS configuration " +
                "primary (16C), got ${firstControllerBeliefs?.activeRunway}.\n$journey"
        }
        // Pass 15 post-impl Impact S4: witness that the active runway
        // actually came from the ATIS path, not from a silent wind-
        // derived fallback. Both yield 16C at LOWG with wind 160/8 — so
        // the equality above doesn't distinguish the two derivation paths.
        // Couple `activeRunway` to `atis.configuration.primary` to surface
        // a regression where the ATIS-derivation branch breaks and the
        // fallback silently takes over.
        val firstStateWithBeliefs = trace.firstWhere { st ->
            st.beliefs[tower.id]?.activeRunway != null
        }.getOrNull()?.state
        val atisAtFirstBelief = firstStateWithBeliefs?.atisByAerodrome?.get(lowg)
        check(
            atisAtFirstBelief != null &&
                firstStateWithBeliefs.beliefs[tower.id]?.activeRunway == atisAtFirstBelief.configuration.primary,
        ) {
            "Pass 15 (D-AUDIT.7 + .8 coupling): tower's activeRunway must match " +
                "atisByAerodrome[LOWG].configuration.primary at the first belief tick. " +
                "If they diverge, the ATIS-derivation branch silently fell through to " +
                "wind-derived fallback. atis=$atisAtFirstBelief, " +
                "activeRunway=${firstStateWithBeliefs?.beliefs?.get(tower.id)?.activeRunway}.\n$journey"
        }
        // Pass 15 (D-AUDIT.8 closure): controller-side ATIS-letter
        // propagation. The LOWG circuit-training mission's first
        // transmission is `Request(RequestTaxi)` (no CALL_INBOUND step
        // in the ground-departure task tree), so an InitialContact-
        // embedded letter assertion is N/A here. The flow that IS
        // exercised: AtisIssued → state.atisByAerodrome → ControllerView.atis
        // → BeliefState.expectedAtisLetter via withExpectedAtisLetter.
        // A regression where the fold is dropped would surface here.
        val towerExpectedLetter = trace.firstWhere { st ->
            st.beliefs[tower.id]?.expectedAtisLetter?.get(lowg) != null
        }.getOrNull()?.state?.beliefs?.get(tower.id)?.expectedAtisLetter?.get(lowg)
        check(towerExpectedLetter == 'A') {
            "Pass 15 (Annex 11 §4.3.6): tower's BeliefState.expectedAtisLetter[LOWG] must " +
                "fold from view.atis (LOWG fixture publishes letter 'A'); " +
                "got $towerExpectedLetter.\n$journey"
        }

        val completionCursor = trace.firstWhere { st ->
            st.aircraft[aircraftId]?.pilotMission?.isComplete == true
        }.getOrNull()
        checkNotNull(completionCursor) {
            "Mission never reached isComplete during the trace.\n$journey"
        }
        val completionMs = completionCursor.time.millis
        val minMs = 10 * 60 * 1000L
        val maxMs = 22 * 60 * 1000L
        check(completionMs in minMs..maxMs) {
            "Mission completion time ${completionMs / 1000} s is outside the doctrine-shaped band " +
                "[${minMs / 1000} s, ${maxMs / 1000} s]. A C172 single circuit at LOWG should land " +
                "within this window; drift indicates a kinematic doctrine regression " +
                "(climb rate / speeds) or a procedural change.\n$journey"
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

        // (b') Pass 9 fold-in added a "no LostCommsDeclared" assertion
        // here. Pass 12 removed it. The original rationale was: "regression
        // that lowers `queryAfter` below natural latency surfaces here."
        // That rationale was correct under pre-Pass-12 semantics but only
        // because of a latent bug in `acceptReadback`: its `remaining`
        // computation destroyed unmatched coordinations on every readback,
        // silently sweeping escalating entries that were left behind by
        // multiple procedure-rule reissues. Pass 12 fixes the bug
        // (remove-by-identity in acceptReadback) AND widens the
        // `processReadback` filter to match all states (D-AUDIT.2.E).
        //
        // With the bug fixed, surplus coordinations from procedure-rule
        // reissue patterns naturally accumulate and reach LostCommsDeclared.
        // That isn't a "lost-comms event" in the doctrinal sense — the
        // pilot has been complying, the controller has been confirming;
        // older orphan coordinations just exhausted their lifecycle while
        // newer ones got readback. The Pass-9 assertion conflates "any
        // LostCommsDeclared" with "operationally bad". The right signal
        // would be "no LostCommsDeclared on instructions the aircraft has
        // not physically complied with" — filed as
        // **D-AUDIT.2.F-FOLLOWUP** (G0 negative-escalation assertion that
        // tracks instruction-vs-completion semantics).

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
        val midHandoffMatch = trace.firstWhere { simState ->
            val tMs = simState.now.millis
            if (tMs !in cfToTwrMs until firstTxToTwrMs) return@firstWhere false
            val gndState = simState.controllers[ground.id]?.responsibilities?.get(aircraftId)
            val twrState = simState.controllers[tower.id]?.responsibilities?.get(aircraftId)
            gndState is xyz.easiersaid.twr.protocol.ResponsibilityState.HandingOff &&
                (gndState.target as? xyz.easiersaid.twr.protocol.HandoffTarget.Peer)?.controllerId == tower.id &&
                twrState is xyz.easiersaid.twr.protocol.ResponsibilityState.Watching &&
                twrState.from == ground.id
        }.getOrNull()
        check(midHandoffMatch != null) {
            "Pass 7 assertion (h): expected at least one cycle in [$cfToTwrMs, $firstTxToTwrMs) " +
                "where GND holds HandingOff(Peer(TWR)) AND TWR holds Watching(from=GND).\n" +
                "If this fires, the typed responsibility state machine is broken: either " +
                "applyContactFrequency didn't transition both controllers atomically, " +
                "or the two-way-comms-driven completion fired too eagerly (before the " +
                "actual pilot transmission to the new controller), collapsing the overlap.\n$journey"
        }
    }
}
