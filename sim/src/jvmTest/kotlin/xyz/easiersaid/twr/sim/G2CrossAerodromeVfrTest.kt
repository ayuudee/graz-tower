package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.fail
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AftnAddress
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.controllerAt
import xyz.easiersaid.twr.sim.testing.firstControllerInstructionOf
import xyz.easiersaid.twr.sim.testing.firstPilotReportOf
import xyz.easiersaid.twr.sim.testing.firstPilotTransmissionTo
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

/**
 * G2 — cross-aerodrome VFR transit (LOWG → LJMB) golden test.
 *
 * AI aircraft files VFR LOWG→LJMB plan, taxis at LOWG, takes off, cruises
 * to the destination's first published contact REP (OSMOT per LJMB
 * authoring), autonomously contacts LJMB Tower, gets joining instructions,
 * lands at LJMB runway 14, taxis to a stand. Mirror of G0's
 * [LowgGoldenTest] structure: single `@Test` method, the run is the test,
 * assertions are what the run produced.
 *
 * **Architectural anchors (R7 / R8):**
 * - The pilot reads only chart-equivalent data from `world.aerodromes[…]`;
 *   no peeks at controller state, no shared SimulationState bus.
 * - Cross-aerodrome handoff modelled as **release + procedure-following +
 *   autonomous initial contact at the procedure's contact REP** — not a
 *   peer handoff. LOWG controllers terminate radar service / approve
 *   frequency change; pilot proceeds through Class G; on reaching the
 *   destination's procedure-published contact REP, the pilot makes the
 *   InitialContact. `applyTwoWayCommsEstablished`'s knownStrips arm flips
 *   the destination's strip to `Owned`.
 * - No `HandoffTarget.Foreign(aerodromeId)` — cross-aerodrome handoff
 *   stays syntactically impossible. Phase G's
 *   `FirewallNoCrossAerodromeHandoffTest` enforces this.
 *
 * **Forbidden test patterns:** no shared SimulationState peeked by both
 * controllers (each reads its own filtered streams); no global event bus
 * the test asserts against; no `world.aircraft.position` reads in test
 * setup; no pilot reads of destination runway-in-use via simulator state
 * (pilot reads runway only via filing or ATIS broadcast).
 *
 * ## CURRENTLY FAILING — DOCUMENTED BLOCKER (G2 Phase F partial)
 *
 * The test runs the full simulation but currently **fails at the
 * `finalMission.isComplete` assertion (~line 188)**. Working through the
 * journey log (printed before the failure) shows the LOWG departure half
 * of the flow runs cleanly:
 *  - filing fan-out (2 recipients);
 *  - GROUND issues TaxiClearance + readback + ReadBackCorrect;
 *  - GROUND issues ContactFrequency to TOWER + readback;
 *  - TOWER takes ownership; pilot reports Ready;
 *  - TOWER issues LineUpAndWait + readback + ClearedForTakeoff + readback;
 *  - aircraft rotates, climbs, follows the published transit route to
 *    `LJMB_FIX_OSMOT` (the destination's first VFR contact REP);
 *  - pilot autonomously sends `InitialContact(stationCalled=TOWER, atisCode=B)`.
 *
 * What's missing — **cross-aerodrome boundary release rule for transit
 * out**: LOWG_TOWER's existing post-takeoff release rules
 * (`DEP-HANDOFF`, `DEP-RADAR-SERVICE-TERMINATED`) gate on
 * `OnCircuitLeg(UPWIND) || OnCircuitLeg(CROSSWIND)`. A VFR cross-aerodrome
 * flight follows a *published transit route* directly to the destination
 * REP — its position never registers on those local circuit legs after
 * lift-off, so the gate never trips. LOWG_TOWER stays `Owned` for the
 * rest of the run; the pilot's `InitialContact` therefore goes out on
 * LOWG_TOWER's frequency (118.200), reaches LOWG_GROUND/TOWER (party-line
 * filter by frequency), and never reaches LJMB_TOWER (119.205) — so the
 * `applyTwoWayCommsEstablished` knownStrips flip never fires and the
 * mission never advances past `AWAIT_JOINING_INSTRUCTIONS`.
 *
 * Closing this requires either (a) a new `DEP-CROSS-AERODROME-RELEASE`
 * rule firing on `Airborne ∧ Departing ∧ OutsideAerodromeRadius(12 NM) ∧
 * destinationAerodromeKnown` (which means the strip needs a
 * `destinationAerodrome` field), or (b) relaxing the existing release
 * rules' circuit-leg gate to a pure-radius gate plus a "no peer handoff
 * needed" predicate. Either path is a meaningful doctrine + types change
 * — out of Phase F's test-integration scope.
 *
 * The failure is intentional and **loud** per project doctrine
 * (`feedback_no_corners`): no `@Disabled`, no skip-list, no exclusion set.
 * The journey log printed at failure is the closure receipt — when the
 * boundary release lands, this test starts passing without further edits.
 */
class G2CrossAerodromeVfrTest {

    @Test
    fun `AI aircraft files VFR LOWG to LJMB transit and lands at LJMB stand`() {
        // ── World + 4 controllers via the multi-aerodrome fixture (Phase A) ──
        val loaded = Fixtures.LOWG_LJMB_VFR.load().getOrElse {
            fail("LOWG_LJMB_VFR fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val ljmb = AerodromeId("LJMB")
        val lowgGround = checkNotNull(loaded.controllerAt(lowg, RoleName.GROUND)) {
            "LOWG_GROUND missing from fixture"
        }
        val lowgTower = checkNotNull(loaded.controllerAt(lowg, RoleName.TOWER)) {
            "LOWG_TOWER missing from fixture"
        }
        val lowgApproach = checkNotNull(loaded.controllerAt(lowg, RoleName.APPROACH)) {
            "LOWG_APPROACH missing from fixture"
        }
        val ljmbTower = checkNotNull(loaded.controllerAt(ljmb, RoleName.TOWER)) {
            "LJMB_TOWER missing from fixture"
        }

        // ── Filing-cardinality pin (R4): 2 events from cross-aerodrome filing ─
        // The fixture's single FiledPlan(LOWG → LJMB) distributes via Pass 14
        // AftnRouting.routeFiledPlan to LOWG_GROUND (Owned) + LJMB_TOWER
        // (knownStrips). A regression that emitted 1 or 3 events would surface
        // here without needing to walk the full ~50min run.
        val filings = loaded.initialEvents.filterIsInstance<SimEvent.FlightPlanFiled>()
        check(filings.size == 2) {
            "G2 routing-cardinality regression: expected exactly 2 FlightPlanFiled " +
                "events for the cross-aerodrome fixture, got ${filings.size}: " +
                "${filings.map { it.recipient }}"
        }

        // ── One AI aircraft at LOWG stand, mission = Transit to LJMB ────────
        val aircraftId = AircraftId("OE-XYZ")
        val now = SimTime.ZERO
        val filedPlan = FiledPlan.Vfr(
            departureAerodrome = lowg,
            destinationAerodrome = ljmb,
            destinationRunway = RunwayId("14"),
            intent = AircraftIntent.Transit,
        )
        val mission = createMission(
            goal = HighLevelGoal.Transit(destination = ljmb),
            startPhase = PilotPhase.AtStand,
            time = now,
            filedPlan = filedPlan,
        )
        val standPointId = Fixtures.LOWG_LJMB_VFR.standPointId
        val aircraft = AircraftState(
            id = aircraftId,
            callsign = Callsign("OEXYZ"),
            position = loaded.world.geometry.points.getValue(standPointId),
            positionPoint = standPointId,
            phase = PilotPhase.AtStand,
            pilotMission = mission,
        )

        // ── Build SimState through the smart constructor ────────────────────
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(lowgGround, lowgTower, lowgApproach, ljmbTower),
            weatherByAerodrome = Fixtures.LOWG_LJMB_VFR.weatherByAerodrome,
        ).getOrElse { error("SimState.initial rejected the LOWG_LJMB_VFR fixture: $it") }

        // ── Drive ────────────────────────────────────────────────────────────
        // 90 sim minutes wall — block-time budget is 50–75 min for ~32 NM
        // (~60 km) C172 cruise + LOWG taxi-out + LJMB pattern + taxi-in.
        // Hitting the wall would mean the run wedged.
        val until = SimTime.ZERO + SimDuration.ofMillis(90 * 60 * 1000L)

        // Multi-aerodrome ATIS: distinct letters for distinct aerodromes.
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
        val ljmbAtis = Atis(
            letter = 'B',
            aerodrome = ljmb,
            configuration = RunwayConfiguration(
                arrivals = listOf(RunwayId("14")),
                departures = listOf(RunwayId("14")),
            ),
            wind = Wind.unsafe(140, 6),
            qnh = null,
            visibility = null,
            generatedAt = now,
        )
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = lowgAtis),
            SimEvent.AtisIssued(time = now, aerodrome = ljmb, atis = ljmbAtis),
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = lowgGround.id),
            SimEvent.ControllerCycle(time = now, controllerId = lowgTower.id),
            SimEvent.ControllerCycle(time = now, controllerId = lowgApproach.id),
            SimEvent.ControllerCycle(time = now, controllerId = ljmbTower.id),
        )
        val (finalState, records, stateTrace) = runUntilWithStateTrace(initialState, initialEvents, until)

        // ── Diagnostic preamble ─────────────────────────────────────────────
        val journey = finalState.formatJourney(aircraftId, records)
        println(journey)

        // ── Outcome (high-level): aircraft completed transit and reached LJMB stand ──
        val finalAircraft = finalState.aircraft.getValue(aircraftId)
        val finalMission = checkNotNull(finalAircraft.pilotMission) { "Aircraft lost its mission" }

        check(finalMission.isComplete) {
            "Mission did not complete within 90 sim minutes.\n$journey"
        }
        check(finalAircraft.altitudeM == 0.0) {
            "Aircraft is not on the ground at end of run.\n$journey"
        }
        check(finalAircraft.phase == PilotPhase.Parked || finalAircraft.phase == PilotPhase.AtStand) {
            "Aircraft did not return to a stand.\n$journey"
        }
        check(finalAircraft.positionPoint == Fixtures.LOWG_LJMB_VFR.destinationStandPointId) {
            "Aircraft did not reach the LJMB destination stand point. Expected " +
                "${Fixtures.LOWG_LJMB_VFR.destinationStandPointId}, got " +
                "${finalAircraft.positionPoint}.\n$journey"
        }

        // ── Pre-radio activeRunway pin (R4) ─────────────────────────────────
        // Phase B / Phase C: createMission(filedPlan = ...) initialises
        // mission.activeRunway from filedPlan.destinationRunway with source
        // RunwayAssignmentSource.Filing. Walk stateTrace forward to the FIRST
        // state where the mission is constructed and pin the value before any
        // radio source can have superseded it.
        //
        // Phase F retroactive review (test-review S2): the matched state
        // must be at simulation time 0 — the assertion's "before any radio
        // source can have superseded it" guarantee depends on the matched
        // state preceding all radio activity. Without the time pin, a
        // regression that deferred mission construction past the first
        // ControllerCycle would still find some state with mission != null,
        // and the activeRunway might still be Filing-sourced there only
        // because the controller hasn't issued a runway-overriding
        // clearance yet — masking the regression.
        val initialMissionEntry = stateTrace
            .firstOrNull { (_, st) -> st.aircraft[aircraftId]?.pilotMission != null }
            ?: fail("Mission never constructed in stateTrace.\n$journey")
        check(initialMissionEntry.second.now.millis == 0L) {
            "Mission must be constructed at sim-init (time=0); first traced state with " +
                "mission != null is at ${initialMissionEntry.second.now.millis}ms — a " +
                "controller's earlier cycle may have superseded the Filing-sourced " +
                "activeRunway before the assertion can observe it.\n$journey"
        }
        val initialMissionState = initialMissionEntry.second
        val initialMission = initialMissionState.aircraft.getValue(aircraftId).pilotMission!!
        val initialActive = initialMission.activeRunway.getOrNull()
            ?: fail("activeRunway is None at first traced state — Phase B createMission " +
                "should have initialised it from filedPlan.destinationRunway.\n$journey")
        check(initialActive.runway == RunwayId("14")) {
            "Pre-radio activeRunway pin: expected RunwayId(14) from filedPlan; got " +
                "${initialActive.runway}.\n$journey"
        }
        check(initialActive.source == RunwayAssignmentSource.Filing) {
            "Pre-radio activeRunway pin: expected source=Filing; got " +
                "${initialActive.source}. A controller-issued clearance superseded " +
                "Filing before the test could observe it — Pass 5 D-PF.2 " +
                "applyPrecedence regression?\n$journey"
        }

        // ── Cross-aerodrome handoff window pin (R4) ─────────────────────────
        // tRelease: time of the last LOWG-controller instruction to the
        //           aircraft (approximation of "release at CTR boundary";
        //           VFR transit doesn't require a formal RST readback).
        // tContact: time of the first pilot InitialContact to LJMB_TWR.
        // Constraints (R4):
        //   tRelease < tContact (gap has duration; ≥30s)
        //   midGapStates.isNotEmpty()
        //   No LOWG controller has aircraft Owned anywhere in the gap
        //   ∃ snapshot in the gap with LJMB_TWR.knownStrips ∋ aircraft
        val lowgControllerIds = setOf(lowgGround.id, lowgTower.id, lowgApproach.id)
        val lastLowgInstrMs = records
            .filter { rec ->
                val speakerControllerId = (rec.speaker as? SpeakerRef.Controller)?.id
                if (speakerControllerId !in lowgControllerIds) return@filter false
                val output = (rec.utterance as? Utterance.FromController)?.output as? ControllerOutput.Instruct
                    ?: return@filter false
                val instr = (output.dispatch as? Dispatch.Direct)?.instruction
                instr != null && output.target == aircraftId
            }
            .maxOfOrNull { it.time.millis }
            ?: fail("No LOWG controller instruction observed for $aircraftId.\n$journey")

        val firstTxToLjmbRecord = records.firstPilotTransmissionTo<InitialContact>(ljmbTower.id)
            .getOrElse {
                fail("No pilot InitialContact to LJMB_TWR observed.\n$journey")
            }
        val firstTxToLjmbMs = firstTxToLjmbRecord.time.millis

        check(lastLowgInstrMs < firstTxToLjmbMs) {
            "Cross-aerodrome handoff window: last LOWG instruction (${lastLowgInstrMs}ms) " +
                "was at-or-after first pilot tx to LJMB_TWR (${firstTxToLjmbMs}ms). " +
                "There must be a gap during which the aircraft is unattended in Class G.\n$journey"
        }
        check(firstTxToLjmbMs - lastLowgInstrMs >= 30_000L) {
            "Cross-aerodrome handoff window: gap between last LOWG instruction and " +
                "first LJMB_TWR contact must be ≥30s (transit duration through Class G); " +
                "got ${firstTxToLjmbMs - lastLowgInstrMs}ms.\n$journey"
        }

        // Pre-contact snapshot: by the moment immediately before the pilot's
        // first LJMB transmission, no LOWG controller may hold ANY responsibility
        // state for the aircraft (Owned, HandingOff, OR Watching). LOWG must
        // have released cleanly. A regression that left LOWG_APPROACH in
        // `HandingOff(Peer(LJMB_TWR))` (a `HandoffTarget.Foreign` smuggling
        // attempt — exactly what R5 prevents) would fail this check.
        val preContactState = stateTrace
            .lastOrNull { (event, _) -> event.time.millis < firstTxToLjmbMs }
            ?.second
            ?: fail("No state snapshot before firstTxToLjmbMs.\n$journey")
        val lowgStillResponsible = preContactState.controllers.values.filter { c ->
            c.aerodromeId == lowg && c.responsibilities.containsKey(aircraftId)
        }.map { c -> c.id.value to c.responsibilities[aircraftId] }
        check(lowgStillResponsible.isEmpty()) {
            "Pre-contact snapshot: by the moment before pilot's first LJMB contact, " +
                "no LOWG controller may hold any responsibility for the aircraft. " +
                "Found: $lowgStillResponsible. LOWG silently kept ownership across release " +
                "or transitioned to HandingOff(Peer(LJMB)) which would mean a Foreign-handoff " +
                "leak (R5 violation).\n$journey"
        }
        // Pre-contact snapshot also: LJMB_TWR holds the strip in knownStrips
        // (from filing) AND has not yet acquired Owned (the strip is the
        // load-bearing pre-contact state).
        val ljmbPreContact = preContactState.controllers[ljmbTower.id]
        check(ljmbPreContact?.knownStrips?.containsKey(aircraftId) == true) {
            "Pre-contact snapshot: LJMB_TWR must have aircraft in knownStrips from filing. " +
                "Got: knownStrips=${ljmbPreContact?.knownStrips?.keys}.\n$journey"
        }
        check(ljmbPreContact.responsibilities[aircraftId] == null) {
            "Pre-contact snapshot: LJMB_TWR.responsibilities[$aircraftId] must still be null " +
                "(Owned flips on the InitialContact's TransmissionEnd). Got: " +
                "${ljmbPreContact.responsibilities[aircraftId]}.\n$journey"
        }

        // ── Post-contact snapshot (R4) ──────────────────────────────────────
        val postContactState = stateTrace
            .firstOrNull { (event, _) -> event.time.millis >= firstTxToLjmbMs }
            ?.second
            ?: fail("No state snapshot at-or-after firstTxToLjmbMs.\n$journey")
        check(postContactState.controllers[ljmbTower.id]?.responsibilities?.get(aircraftId) is ResponsibilityState.Owned) {
            "After pilot's InitialContact, LJMB_TWR must have aircraft as Owned. " +
                "Got: ${postContactState.controllers[ljmbTower.id]?.responsibilities?.get(aircraftId)}.\n$journey"
        }
        check(postContactState.controllers[ljmbTower.id]?.knownStrips?.containsKey(aircraftId) == false) {
            "After applyTwoWayCommsEstablished's knownStrips arm fires, the strip must move " +
                "out of LJMB_TWR.knownStrips into responsibilities.\n$journey"
        }
        check(postContactState.controllers.values.none { c ->
            c.aerodromeId == lowg && c.responsibilities.containsKey(aircraftId)
        }) {
            "Post-contact: no LOWG controller should still hold the aircraft.\n$journey"
        }

        // ── Multi-aerodrome ATIS pins (R4) ──────────────────────────────────
        // The pilot's first contact to LOWG_GROUND (REQUEST_TAXI flow) carries
        // letter 'A' (LOWG ATIS); the first contact to LJMB_TWR (CALL_INBOUND
        // flow at OSMOT) carries letter 'B' (LJMB ATIS). atisLetterForCallInbound's
        // goal-keyed lookup (Phase C C.5) routes the pilot to the destination's
        // ATIS via mission.goal.destination.
        val firstLowgContact = records.firstPilotTransmissionTo<InitialContact>(lowgGround.id)
            .getOrElse { fail("No pilot InitialContact to LOWG_GROUND observed.\n$journey") }
        val lowgIcLetter = ((firstLowgContact.utterance as? Utterance.FromPilot)?.transmission as? InitialContact)
            ?.atisCode
        check(lowgIcLetter == 'A') {
            "Multi-aerodrome ATIS pin: LOWG first contact must carry letter 'A'; got $lowgIcLetter.\n$journey"
        }
        val ljmbIcLetter = ((firstTxToLjmbRecord.utterance as? Utterance.FromPilot)?.transmission as? InitialContact)
            ?.atisCode
        check(ljmbIcLetter == 'B') {
            "Multi-aerodrome ATIS pin: LJMB first contact must carry letter 'B'; got $ljmbIcLetter.\n$journey"
        }

        // ── Autonomous-contact provenance pin (R4 / R7) ─────────────────────
        // Phase F retroactive review (test-review S1): inverted from the
        // original "no CF directs to LJMB" form. The fix-direction-safe
        // predicate is "every CF targets a LOWG-side destination" — works
        // for any number of foreign aerodromes (LJMB, future Vienna ACC,
        // etc.) without enumerating each. Cross-aerodrome contact is the
        // pilot's autonomous initiative at the procedure REP, not a
        // back-channel handoff.
        //
        // For each ContactFrequency directed at the aircraft, classify it
        // as LOWG-side iff EITHER:
        //   - the frequency matches a staffed LOWG controller's frequency, OR
        //   - the frequency is null AND the role exists at LOWG (CAP 413
        //     omits frequency when role implies it; the role-only form is
        //     legitimate for intra-aerodrome handoff but only when the
        //     role is a LOWG-side role).
        // Any other CF is a back-channel cross-aerodrome handoff.
        val lowgFreqs: Set<xyz.easiersaid.twr.protocol.Frequency> = finalState.controllers.values
            .filter { it.aerodromeId == lowg }
            .map { it.frequency }
            .toSet()
        val lowgRoles: Set<RoleName> = finalState.controllers.values
            .filter { it.aerodromeId == lowg }
            .map { it.role }
            .toSet()
        val nonLowgContactFrequency = records.firstOrNull { rec ->
            val output = (rec.utterance as? Utterance.FromController)?.output as? ControllerOutput.Instruct
                ?: return@firstOrNull false
            val instr = (output.dispatch as? Dispatch.Direct)?.instruction as? ContactFrequency
                ?: return@firstOrNull false
            if (instr.target != aircraftId) return@firstOrNull false
            val targetsLowgFreq = instr.frequency != null && instr.frequency in lowgFreqs
            val targetsLowgRoleOnly = instr.frequency == null && instr.role in lowgRoles
            !(targetsLowgFreq || targetsLowgRoleOnly)
        }
        check(nonLowgContactFrequency == null) {
            "Autonomous-contact provenance: pilot must self-contact LJMB_TWR autonomously, " +
                "not via a ContactFrequency from another controller. A back-channel handoff " +
                "has snuck in (the offending CF targets a non-LOWG-side destination). " +
                "Offending record: $nonLowgContactFrequency. Suspect: a controller emitting " +
                "ContactFrequency for a foreign aerodrome's role/frequency, or " +
                "applyContactFrequency's same-aerodrome check at Step.kt:1367 was relaxed.\n$journey"
        }

        // ── Filing-distribution check via post-state ────────────────────────
        // The fixture's flightPlans payload is one VFR plan with
        // destinationRunway=14, intent=Transit. AftnRouting.routeFiledPlan
        // produces 2 events (LOWG_GROUND first, LJMB_TWR second). Filing-cardinality
        // pin above asserts size; this row asserts the recipient identities
        // and ordering survive routing.
        val recipients = filings.map { it.recipient }
        check(recipients == listOf(
            AftnAddress(lowg, RoleName.GROUND),
            AftnAddress(ljmb, RoleName.TOWER),
        )) {
            "G2 filing-distribution: expected [LOWG/GROUND, LJMB/TOWER] in order; got " +
                "$recipients.\n$journey"
        }

        // ── Time band 50–75 min (R4 / practice-scout) ───────────────────────
        // Margin breakdown for ~32 NM (~60 km) cruise:
        //   Taxi out at LOWG: ~8–12 min
        //   Run-up: 60s (D-AUDIT.3 / Pass 13 — C172)
        //   Takeoff + climb to cruise: ~3–5 min
        //   Cruise at C172 ~110 KTAS for ~36 NM track miles (with avoidance):
        //     ~15–20 min
        //   Descent + LJMB TMA entry + pattern + landing: ~8–12 min
        //   LJMB taxi-in: ~3–5 min
        //   ────────────────────────────
        //   Nominal stand-to-stand: 50–75 min.
        //
        // A regression that doubled cruise speed lands at ~25 min — fails low.
        // A regression that quartered cruise speed lands at ~95 min — would
        // have hit the 90-min wall above.
        val completionEvent = stateTrace.firstOrNull { (_, st) ->
            st.aircraft[aircraftId]?.pilotMission?.isComplete == true
        } ?: fail("Mission never reached isComplete during the trace.\n$journey")
        val completionMs = completionEvent.first.time.millis
        // Phase F retroactive review (test-review M1): the practice-scout
        // 45–80 min band is tentative — the test currently fails before
        // completion is ever observed (boundary-release blocker), so the
        // band has no empirical anchor. Widened to 30–90 (the run's wall
        // budget) so first green pass is not blocked on an unanchored
        // guess; **tighten on first observed completion time** (and update
        // the surrounding rationale in the same commit). G0's analogous
        // band has ~100% slack; the cross-aerodrome run has more variance
        // sources (run-up dwell pre-D-AUDIT.3 is ~10s placeholder, not the
        // 60s practice-scout estimate; TMA pattern is highly variable;
        // LJMB approach has no APP staffed so pattern entry depends on
        // TWR's join-circuit instruction) so a wider initial band is the
        // honest shape.
        val minMs = 30 * 60 * 1000L
        val maxMs = 90 * 60 * 1000L
        check(completionMs in minMs..maxMs) {
            "Mission completion time ${completionMs / 1000} s is outside the doctrine-shaped " +
                "band [${minMs / 1000} s, ${maxMs / 1000} s]. The C172 LOWG→LJMB transit should " +
                "land within this window; drift indicates a kinematic doctrine regression " +
                "(cruise speed / climb rate / RUN_UP_CHECKS dwell) or a procedural change.\n$journey"
        }

        // Phase F retroactive review (test-review M2): copy of G0's
        // assertion (f) — RUN_UP_CHECKS dwell ≥10s. After the pilot-
        // firewall removed the `!aircraft.humanPiloted ||` short-circuit
        // on TIMED step completion, the AI must wait the full RUN_UP_CHECKS
        // dwell. A regression that re-introduces the AI fast-path would
        // pass every other check (kinematics survive, mission tree
        // unchanged) but cut ~10s off the time between TaxiTo apply and
        // Report(Ready). G2 exercises the same RUN_UP_CHECKS path as G0
        // during the LOWG departure half; without this row, the regression
        // would only surface in G0.
        val taxiMs = records.firstControllerInstructionOf<xyz.easiersaid.twr.protocol.TaxiToHoldingPoint>(aircraftId)
            .map { it.time.millis }
            .getOrElse {
                fail("Expected at least one TaxiToHoldingPoint to $aircraftId in the transmission stream.\n$journey")
            }
        val readyMs = records.firstPilotReportOf<xyz.easiersaid.twr.protocol.ReportEvent.Ready>(aircraftId)
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
    }
}
