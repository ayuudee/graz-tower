package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.Airborne
import xyz.easiersaid.twr.controller.bdi.AircraftIntentIs
import xyz.easiersaid.twr.controller.bdi.instructionOfType
import xyz.easiersaid.twr.controller.bdi.AllOf
import xyz.easiersaid.twr.controller.bdi.AnomalousTransition
import xyz.easiersaid.twr.controller.bdi.AnyOf
import xyz.easiersaid.twr.controller.bdi.AtcRule
import xyz.easiersaid.twr.controller.bdi.DestinationDifferentAerodrome
import xyz.easiersaid.twr.controller.bdi.CancelTakeoffAction
import xyz.easiersaid.twr.controller.bdi.ClearTakeoffAction
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.ConditionalLineUpAction
import xyz.easiersaid.twr.controller.bdi.ContactEstablished
import xyz.easiersaid.twr.controller.bdi.ExpectedPilotAct
import xyz.easiersaid.twr.controller.bdi.HandoffAction
import xyz.easiersaid.twr.controller.bdi.IsTransferTargetStaffed
import xyz.easiersaid.twr.controller.bdi.HoldPositionAction
import xyz.easiersaid.twr.controller.bdi.IsCircuitTraffic
import xyz.easiersaid.twr.controller.bdi.LineUpAction
import xyz.easiersaid.twr.controller.bdi.NoActiveInstruction
import xyz.easiersaid.twr.controller.bdi.NoPendingReadback
import xyz.easiersaid.twr.controller.bdi.NoRunwayClearanceIssued
import xyz.easiersaid.twr.controller.bdi.Not
import xyz.easiersaid.twr.controller.bdi.OutsideAerodromeRadius
import xyz.easiersaid.twr.controller.bdi.TerminateRadarServiceAction
import xyz.easiersaid.twr.controller.bdi.OnCircuitLeg
import xyz.easiersaid.twr.controller.bdi.OnGround
import xyz.easiersaid.twr.controller.bdi.OnRunway
import xyz.easiersaid.twr.controller.bdi.OtherTrafficOnShortFinal
import xyz.easiersaid.twr.controller.bdi.PilotReady
import xyz.easiersaid.twr.controller.bdi.ProcedureSpec
import xyz.easiersaid.twr.controller.bdi.RunwayAccessGranted
import xyz.easiersaid.twr.controller.bdi.RunwayLengthOperation
import xyz.easiersaid.twr.controller.bdi.RunwayLengthSufficient
import xyz.easiersaid.twr.controller.bdi.RunwayPhysicallyClear
import xyz.easiersaid.twr.controller.bdi.StageExpectation
import xyz.easiersaid.twr.controller.bdi.TowerDepartureStage
import xyz.easiersaid.twr.controller.bdi.WeatherPermitsVfr
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_10_1
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_6
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_9
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_9_3
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_CONDITIONAL
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_FREQUENCY_CHANGE
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_LINEUP
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_READY
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_TAKEOFF
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9870_RUNWAY_INCURSION
import xyz.easiersaid.twr.protocol.RegulationDatabase.SERA_5001
import xyz.easiersaid.twr.protocol.RegulationDatabase.SERA_5005
import xyz.easiersaid.twr.protocol.Urgency
import xyz.easiersaid.twr.controller.observe.AdvancementPolicy

// AI pilots emit Report(Ready) the same way human pilots do, so PilotReady
// alone is sufficient. Removing AiProactive closes a firewall leak — the
// controller no longer reads `humanPiloted`.
private val DepartureTrigger = PilotReady

/** Shared guard: conditions for issuing or re-issuing a takeoff clearance. */
private val TakeoffConditions = AllOf(listOf(
    OnRunway, OnGround,
    WeatherPermitsVfr,
    RunwayAccessGranted,
    RunwayPhysicallyClear,
))

@Suppress("LongMethod") // procedure spec is a flat list of rules — splitting is a behavioural
// decision (separate CLEARANCE_DELIVERY/DEPARTURE flows), not a stylistic one.
fun towerDepartureProcedure(): ProcedureSpec = ProcedureSpec(
    kind = CommitmentKind.TOWER_DEPARTURE,
    stageExpectations = mapOf(
        TowerDepartureStage.AwaitReady to StageExpectation(
            ExpectedPilotAct.ReadyForDeparture,
            "Report ready for departure at the holding point so the controller can sequence you for the runway",
            regulations = listOf(ICAO9432_READY),
        ),
    ),
    stageRules = mapOf(
        TowerDepartureStage.AwaitReady to listOf(
            AtcRule(
                id = "DEP-RUNWAY-INCURSION",
                description = "Hold position — aircraft on runway without clearance",
                regulations = listOf(ICAO4444_7_6, ICAO9870_RUNWAY_INCURSION),
                guard = AllOf(listOf(OnRunway, NoRunwayClearanceIssued)),
                action = HoldPositionAction,
                urgency = Urgency.SAFETY,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            AtcRule(
                id = "DEP-HOLD-IMC",
                description = "Hold departing VFR traffic when weather below VMC minima",
                // SERA.5001 defines VMC minima but does not forbid dispatch on its own.
                // SERA.5005 is the operative rule: a VFR flight shall not be operated
                // when conditions are below those minima. That's the sole authority here.
                regulations = listOf(SERA_5005),
                guard = AllOf(listOf(DepartureTrigger, ContactEstablished, Not(WeatherPermitsVfr))),
                action = HoldPositionAction,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            AtcRule(
                id = "DEP-LUAW",
                description = "Line up and wait when pilot ready and runway available",
                regulations = listOf(ICAO4444_7_9, ICAO4444_7_9_3, ICAO9432_LINEUP),
                guard = AllOf(listOf(
                    DepartureTrigger,
                    ContactEstablished,
                    WeatherPermitsVfr,
                    RunwayAccessGranted,
                    RunwayPhysicallyClear,
                    // Pass 13 (D-AUDIT.4.A-FOLLOWUP closure): runway must be
                    // long enough for the type's takeoff TODA. Fails closed
                    // for unknown designator or absent declared distances.
                    RunwayLengthSufficient(RunwayLengthOperation.TAKEOFF),
                    // Never line up in front of an arrival on short final — even with
                    // runway access the approach buffer would be eroded below ICAO minima.
                    Not(OtherTrafficOnShortFinal),
                )),
                action = LineUpAction,
                nextStage = TowerDepartureStage.AwaitLineUpObserved,
                stampReadyAt = true,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Conditional line-up: runway occupied but access granted
            AtcRule(
                id = "DEP-LUAW-COND",
                description = "Conditional line-up behind landing/departing traffic",
                regulations = listOf(ICAO4444_7_9_3, ICAO9432_CONDITIONAL),
                guard = AllOf(listOf(
                    DepartureTrigger,
                    ContactEstablished,
                    WeatherPermitsVfr,
                    RunwayAccessGranted,
                    // Pass 13 (D-AUDIT.4.A-FOLLOWUP): conditional line-up
                    // still requires the runway is long enough for the
                    // aircraft's takeoff TODA.
                    RunwayLengthSufficient(RunwayLengthOperation.TAKEOFF),
                    // Also blocked: conditional line-up in front of landing traffic
                    // would still put the departure on the runway ahead of the arrival.
                    Not(OtherTrafficOnShortFinal),
                )),
                action = ConditionalLineUpAction,
                nextStage = TowerDepartureStage.AwaitLineUpObserved,
                stampReadyAt = true,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        TowerDepartureStage.AwaitLineUpObserved to listOf(
            // Safety: if reconciliation detected an anomalous transition to this stage
            // (pilot on runway without line-up clearance), hold position before evaluating
            // normal progression. Prevents clearing for takeoff an aircraft that just incurred.
            AtcRule(
                id = "DEP-HOLD-INCURSION",
                description = "Hold position — anomalous transition detected (possible runway incursion)",
                regulations = listOf(ICAO4444_7_6, ICAO9870_RUNWAY_INCURSION),
                guard = AllOf(listOf(OnRunway, OnGround, AnomalousTransition)),
                action = HoldPositionAction,
                urgency = Urgency.SAFETY,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            AtcRule(
                id = "DEP-HOLD-LINEUP-IMC",
                description = "Hold on runway when weather deteriorates below VMC",
                regulations = listOf(SERA_5001, SERA_5005),
                guard = AllOf(listOf(OnRunway, OnGround, Not(WeatherPermitsVfr))),
                action = HoldPositionAction,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            AtcRule(
                id = "DEP-TAKEOFF",
                description = "Cleared for takeoff when lined up, runway clear, and weather permits",
                regulations = listOf(ICAO4444_7_9, ICAO9432_TAKEOFF),
                guard = TakeoffConditions,
                action = ClearTakeoffAction,
                // Advance immediately to TakeoffClearanceIssued (the instruction is now
                // in play). Readback confirmation advances to AwaitTakeoffObserved.
                // Observation-based reconciliation may also advance past this stage
                // if the aircraft is observed airborne before the readback arrives.
                nextStage = TowerDepartureStage.TakeoffClearanceIssued,
                readbackAdvancesToStage = TowerDepartureStage.AwaitTakeoffObserved,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            AtcRule(
                id = "DEP-HOLD-LINEUP",
                description = "Hold position on runway when runway no longer available",
                regulations = listOf(ICAO4444_7_9),
                guard = AllOf(listOf(
                    OnRunway, OnGround,
                    NoActiveInstruction(instructionOfType<xyz.easiersaid.twr.protocol.HoldPosition>()),
                )),
                action = HoldPositionAction,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        // ── TakeoffClearanceIssued: awaiting readback or observing takeoff roll ──
        TowerDepartureStage.TakeoffClearanceIssued to listOf(
            // Hold position when runway becomes unsafe before the pilot has confirmed
            // the takeoff clearance. "Hold position" not "cancel takeoff" because the
            // pilot may not have heard ClearedForTakeoff — you can't cancel what wasn't
            // acknowledged (ICAO Doc 9432). DEP-CANCEL-TAKEOFF at AwaitTakeoffObserved
            // (readback confirmed) correctly uses CancelTakeoffAction.
            AtcRule(
                id = "DEP-HOLD-TAKEOFF-UNCONFIRMED",
                description = "Hold position — runway no longer clear before takeoff readback confirmed",
                regulations = listOf(ICAO4444_7_9),
                guard = AllOf(listOf(OnRunway, OnGround, Not(RunwayPhysicallyClear))),
                action = HoldPositionAction,
                urgency = Urgency.SAFETY,
                nextStage = TowerDepartureStage.AwaitLineUpObserved,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Re-issue: if the ClearedForTakeoff transmission was stepped on,
            // the coordination escalates through Querying → Reissued (Pass 9
            // D-AUDIT.2). Once it's no longer in Issued state, the rule re-fires.
            AtcRule(
                id = "DEP-TAKEOFF-REISSUE",
                description = "Re-issue takeoff clearance after readback timeout",
                regulations = listOf(ICAO4444_7_9, ICAO9432_TAKEOFF),
                guard = AllOf(listOf(
                    TakeoffConditions,
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.ClearedForTakeoff>()),
                )),
                action = ClearTakeoffAction,
                // Stay at TakeoffClearanceIssued; a new coordination will be recorded.
                nextStage = TowerDepartureStage.TakeoffClearanceIssued,
                readbackAdvancesToStage = TowerDepartureStage.AwaitTakeoffObserved,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        TowerDepartureStage.AwaitTakeoffObserved to listOf(
            // Cancel takeoff if runway becomes occupied after clearance.
            // Defense-in-depth: the Lean runway kernel should prevent this scenario
            // (runway commitment coherence). If this fires, something went wrong upstream.
            AtcRule(
                id = "DEP-CANCEL-TAKEOFF",
                description = "Cancel takeoff — runway no longer clear after clearance issued",
                regulations = listOf(ICAO4444_7_9),
                guard = AllOf(listOf(OnRunway, OnGround, Not(RunwayPhysicallyClear))),
                action = CancelTakeoffAction,
                urgency = Urgency.SAFETY,
                advancementPolicy = AdvancementPolicy.Immediate,
                // Stay at AWAIT_TAKEOFF_OBSERVED — re-evaluate next cycle
            ),
            // Circuit training: departure is complete when the aircraft reaches
            // downwind. Tower retains the aircraft and forms a fresh TOWER_ARRIVAL
            // for the circuit. No handoff to approach — circuit traffic stays with tower.
            AtcRule(
                id = "DEP-CIRCUIT-COMPLETE",
                description = "Departure complete — circuit traffic reaching downwind, tower retains",
                // Fires for any aircraft the controller has identified as
                // circuit traffic — i.e. the pilot has reported a Downwind
                // call carrying a CircuitIntent. The intent value (T&G or
                // FULL_STOP) is irrelevant here; the relevance is "the
                // aircraft is in the circuit pattern, retain at tower."
                regulations = listOf(ICAO4444_7_9),
                guard = AllOf(listOf(
                    Airborne,
                    OnCircuitLeg(LegName.DOWNWIND),
                    IsCircuitTraffic,
                )),
                nextStage = TowerDepartureStage.Complete,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            AtcRule(
                id = "DEP-HANDOFF",
                description = "Hand departing local traffic to approach/area control after climb-out",
                // Transfer of *communications* (§10.1), not transfer of control
                // (§6.3). Phraseology is the frequency-change instruction per Doc 9432.
                regulations = listOf(ICAO4444_10_1, ICAO9432_FREQUENCY_CHANGE),
                // Wait until the aircraft is on the upwind/crosswind climb-out rather
                // than handing off the moment the wheels leave the ground — tower needs
                // to watch the initial climb for aborts / engine failures (CAP 413 §4.46).
                // Circuit traffic stays with tower (DEP-CIRCUIT-COMPLETE handles it
                // once they reach downwind and have declared circuit intent). A
                // straight-through departure has no circuit intent declared — it
                // climbs out and gets handed to APPROACH.
                //
                // G2 Phase H: `Not(DestinationDifferentAerodrome)` gates this rule
                // OFF for cross-aerodrome flights. Pre-fix, a transit aircraft
                // briefly riding UPWIND/CROSSWIND geometry near the runway end would
                // hit `Immediate`-advancement here and get peer-handed to LOWG_APPROACH
                // — exactly the doctrine violation the cross-aerodrome design exists
                // to prevent (release + procedure-following + autonomous initial
                // contact, not peer handoff).
                guard = AllOf(listOf(
                    Airborne,
                    AnyOf(listOf(OnCircuitLeg(LegName.UPWIND), OnCircuitLeg(LegName.CROSSWIND))),
                    Not(IsCircuitTraffic),
                    AircraftIntentIs(xyz.easiersaid.twr.protocol.AircraftIntent.Departing),
                    Not(DestinationDifferentAerodrome),
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.ContactFrequency>()),
                    IsTransferTargetStaffed(xyz.easiersaid.twr.protocol.RoleName.APPROACH),
                )),
                action = HandoffAction(xyz.easiersaid.twr.protocol.RoleName.APPROACH),
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Pass 7 (D-PF.7 closure): boundary release sibling for the
            // unstaffed-APPROACH case. Same compatibility set as DEP-HANDOFF
            // except `Not(IsTransferTargetStaffed)` and the aircraft has
            // crossed the CTR boundary (12 NM radial conservative). Per
            // ICAO Doc 4444 §10.1.4: "radar service terminated, squawk
            // 7000, frequency change approved." E17 architectural test
            // pairs this with DEP-HANDOFF.
            AtcRule(
                id = "DEP-RADAR-SERVICE-TERMINATED",
                description = "Terminate radar service when APPROACH unstaffed and local traffic past CTR boundary",
                regulations = listOf(ICAO4444_10_1, ICAO9432_FREQUENCY_CHANGE),
                // G2 Phase H: `Not(DestinationDifferentAerodrome)` gates this OFF
                // for cross-aerodrome flights. Cross-aerodrome flights take the
                // dedicated `DEP-CROSS-AERODROME-RELEASE` path regardless of
                // whether APPROACH is staffed; this local-traffic boundary
                // release is for circuit-leg-completing one-shot departures only.
                guard = AllOf(listOf(
                    Airborne,
                    AnyOf(listOf(OnCircuitLeg(LegName.UPWIND), OnCircuitLeg(LegName.CROSSWIND))),
                    Not(IsCircuitTraffic),
                    AircraftIntentIs(xyz.easiersaid.twr.protocol.AircraftIntent.Departing),
                    Not(IsTransferTargetStaffed(xyz.easiersaid.twr.protocol.RoleName.APPROACH)),
                    Not(DestinationDifferentAerodrome),
                    OutsideAerodromeRadius(xyz.easiersaid.twr.core.world.Meters.fromNauticalMiles(12)),  // D-AUDIT.7
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.RadarServiceTerminated>()),
                )),
                action = TerminateRadarServiceAction(
                    forRole = xyz.easiersaid.twr.protocol.RoleName.APPROACH,
                    squawk = arrow.core.Some(xyz.easiersaid.twr.protocol.Squawk.unsafe(7000)),
                ),
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // G2 Phase H — cross-aerodrome boundary release.
            //
            // Doctrine: cross-aerodrome handoff is **release + procedure-
            // following + autonomous initial contact** at the destination's
            // published REP, not peer handoff. LOWG_TOWER releases the aircraft
            // when it crosses the CTR boundary; the pilot then navigates VFR
            // to the destination (per the route planner) and autonomously calls
            // the destination tower. `applyTwoWayCommsEstablished`'s knownStrips
            // arm flips the destination to `Owned` on the pilot's first call.
            //
            // Distinct from `DEP-RADAR-SERVICE-TERMINATED`:
            //  - This rule fires regardless of `IsTransferTargetStaffed(APPROACH)`
            //    (cross-aerodrome flights skip APPROACH even when staffed —
            //    APPROACH would have nothing useful to do for a flight leaving
            //    the local controlled airspace).
            //  - This rule does NOT gate on circuit-leg position (a transit
            //    route off the runway end may not register on UPWIND/CROSSWIND
            //    points at all; the radius gate is the load-bearing geometric
            //    check).
            //
            // Geometry note: 12 NM = 22 224 m (D-AUDIT.7 conservative). Verified
            // reachable for the G2 LOWG → LJMB fixture: OSMOT (LJMB's first
            // VFR contact REP) is ~25 NM from LOWG ARP; the 12 NM ring is
            // crossed well before the aircraft reaches the destination's REP.
            // A future "per-aerodrome CTR boundary from world data" pass
            // tightens this to LOWG's actual ~7 NM CTR.
            //
            // Squawk 7000 (VFR conspicuity) per ICAO Doc 4444 §10.1.4 boundary
            // release. `forRole = APPROACH` mirrors the unstaffed-APPROACH
            // sibling for `TerminateRadarServiceAction`'s phraseology shape.
            AtcRule(
                id = "DEP-CROSS-AERODROME-RELEASE",
                description = "Release cross-aerodrome flight at CTR boundary — pilot autonomously contacts destination at REP",
                regulations = listOf(ICAO4444_10_1, ICAO9432_FREQUENCY_CHANGE),
                guard = AllOf(listOf(
                    Airborne,
                    Not(IsCircuitTraffic),
                    AircraftIntentIs(xyz.easiersaid.twr.protocol.AircraftIntent.Departing),
                    DestinationDifferentAerodrome,
                    OutsideAerodromeRadius(xyz.easiersaid.twr.core.world.Meters.fromNauticalMiles(12)),
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.RadarServiceTerminated>()),
                )),
                action = TerminateRadarServiceAction(
                    forRole = xyz.easiersaid.twr.protocol.RoleName.APPROACH,
                    squawk = arrow.core.Some(xyz.easiersaid.twr.protocol.Squawk.unsafe(7000)),
                ),
                // Advance to Complete on issuance so the rule doesn't re-fire
                // on subsequent cycles. Without this, the radius gate stays
                // satisfied indefinitely (cross-aerodrome flight cruises away
                // from LOWG); after the 10s NoPendingReadback timeout
                // (Issued → Querying), the rule would otherwise re-fire and
                // hit `applyRadarServiceTerminated`'s requireOwner check
                // (controller is now HandingOff(Released), not Owned).
                // The existing `DEP-RADAR-SERVICE-TERMINATED` doesn't need
                // this because its `OnCircuitLeg(UPWIND/CROSSWIND)` geometry
                // gate self-deactivates after the aircraft moves off those
                // legs; cross-aerodrome flights don't have that
                // self-deactivation property.
                nextStage = TowerDepartureStage.Complete,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
    ),
)
