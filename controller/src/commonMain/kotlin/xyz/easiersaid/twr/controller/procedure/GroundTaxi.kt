package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.instructionOfType
import xyz.easiersaid.twr.controller.bdi.AllOf
import xyz.easiersaid.twr.controller.bdi.AtHoldingPoint
import xyz.easiersaid.twr.controller.bdi.AtStand
import xyz.easiersaid.twr.controller.bdi.AtcRule
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.ExpectedPilotAct
import xyz.easiersaid.twr.controller.bdi.GroundArrivalStage
import xyz.easiersaid.twr.controller.bdi.GroundDepartureStage
import xyz.easiersaid.twr.controller.bdi.HandoffAction
import xyz.easiersaid.twr.controller.bdi.IsTransferTargetStaffed
import xyz.easiersaid.twr.controller.bdi.NoActiveInstruction
import xyz.easiersaid.twr.controller.bdi.NoPendingReadback
import xyz.easiersaid.twr.controller.bdi.Not
import xyz.easiersaid.twr.controller.bdi.TerminateRadarServiceAction
import xyz.easiersaid.twr.controller.bdi.ProcedureSpec
import xyz.easiersaid.twr.controller.bdi.StageExpectation
import xyz.easiersaid.twr.controller.bdi.TaxiRequested
import xyz.easiersaid.twr.controller.bdi.TaxiToHoldingAction
import xyz.easiersaid.twr.controller.bdi.TaxiToStandAction
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_10_1
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_6
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_FREQUENCY_CHANGE
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_TAXI
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.TaxiClearance
import xyz.easiersaid.twr.controller.observe.AdvancementPolicy

/**
 * Ground taxi procedure — handles both departure and arrival flows.
 *
 * Departure: AwaitTaxiRequest → AwaitAtHolding → Complete
 * Arrival: TaxiToStand → AwaitParked → Complete
 *
 * Entry stage determined by CommitmentReconciliation based on pilot goal.
 */
fun groundTaxiProcedure(): ProcedureSpec = ProcedureSpec(
    kind = CommitmentKind.GROUND_TAXI,
    stageExpectations = mapOf(
        GroundDepartureStage.AwaitTaxiRequest to StageExpectation(
            ExpectedPilotAct.RequestTaxi,
            "Request taxi to the holding point — the controller needs to know you're ready to move",
            regulations = listOf(ICAO9432_TAXI),
        ),
    ),
    stageRules = mapOf(
        // ── Departure: taxi to holding ───────────────────────────────
        GroundDepartureStage.AwaitTaxiRequest to listOf(
            AtcRule(
                id = "GND-TAXI",
                description = "Taxi to holding point for departure",
                regulations = listOf(ICAO4444_7_6, ICAO9432_TAXI),
                // AI pilots emit Request(RequestTaxi) like human pilots
                // (see PilotMission.groundDepartureTask). Removing the
                // AiProactive bypass closes a firewall leak — the
                // controller no longer knows whether the cockpit is crewed
                // by a human or an AI.
                guard = TaxiRequested,
                action = TaxiToHoldingAction,
                nextStage = GroundDepartureStage.AwaitAtHolding,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        GroundDepartureStage.AwaitAtHolding to listOf(
            AtcRule(
                id = "GND-HANDOFF",
                description = "Hand departing traffic to tower at holding point",
                // Intra-aerodrome handoff is transfer of communications (§10.1),
                // not transfer of control (§6.3) — the latter governs ACC/APP
                // inter-unit boundaries. Phraseology is the frequency-change
                // instruction in ICAO Doc 9432.
                regulations = listOf(ICAO4444_10_1, ICAO9432_FREQUENCY_CHANGE),
                // Idempotency via the authoritative pending-readback register: the
                // handoff instruction is in flight for several cycles, so the rule
                // would otherwise queue duplicate ContactFrequencies until the
                // responsibility transfer actually lands in the sim world.
                // The 30 s GC horizon in the pending register gives us retransmit
                // semantics for free — CAP 413 §2.7 "how copy?".
                guard = AllOf(listOf(
                    AtHoldingPoint,
                    NoPendingReadback(instructionOfType<ContactFrequency>()),
                    IsTransferTargetStaffed(RoleName.TOWER),
                )),
                action = HandoffAction(RoleName.TOWER),
                advancementPolicy = AdvancementPolicy.Immediate,
                // Stay at AwaitAtHolding. Pruning happens when responsibility actually
                // transfers (orphan-prune in reconcileCommitments).
            ),
            // Pass 7 (D-PF.7 closure): boundary-release sibling for the
            // unstaffed-TOWER case (small uncontrolled field where AFIS
            // works alone, or shift transitions). Aircraft is at the
            // holding point; the radius gate is moot but kept for E17
            // sibling-pairing.
            AtcRule(
                id = "GND-RADAR-SERVICE-TERMINATED",
                description = "Terminate service when TOWER unstaffed and aircraft at holding point",
                regulations = listOf(ICAO4444_7_6, ICAO9432_TAXI),
                guard = AllOf(listOf(
                    AtHoldingPoint,
                    Not(IsTransferTargetStaffed(RoleName.TOWER)),
                    NoPendingReadback(instructionOfType<xyz.easiersaid.twr.protocol.RadarServiceTerminated>()),
                )),
                action = TerminateRadarServiceAction(forRole = RoleName.TOWER),
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        // ── Arrival: taxi to stand ───────────────────────────────────
        GroundArrivalStage.TaxiToStand to listOf(
            AtcRule(
                id = "GND-TAXI-STAND",
                description = "Taxi arriving traffic to parking stand",
                // §7.11 ("post-landing taxi" / runway vacation) was mis-cited — by the
                // time GND takes the aircraft it is already clear of the runway, so the
                // applicable authority is §7.6 (movement on the manoeuvring area).
                regulations = listOf(ICAO4444_7_6, ICAO9432_TAXI),
                guard = NoActiveInstruction(instructionOfType<TaxiClearance>()),
                action = TaxiToStandAction,
                nextStage = GroundArrivalStage.AwaitParked,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
        GroundArrivalStage.AwaitParked to listOf(
            AtcRule(
                id = "GND-PARKED",
                description = "Ground arrival complete when aircraft at stand",
                regulations = listOf(ICAO9432_TAXI),
                guard = AtStand,
                nextStage = GroundArrivalStage.Complete,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
            // Re-issue if the original TaxiTo was stepped on. The
            // pending-readback horizon (~30 s GC) is the retransmit timer:
            // a successful first issue keeps a coordination in the ledger
            // until the readback confirms it; a step-on means the
            // coordination was never recorded, so NoPendingReadback fires
            // and the rule re-issues. Without this, a GND→TWR shared
            // frequency can wedge the post-landing taxi.
            AtcRule(
                id = "GND-TAXI-STAND-REISSUE",
                description = "Re-issue taxi-to-stand after readback timeout",
                regulations = listOf(ICAO4444_7_6, ICAO9432_TAXI),
                guard = AllOf(listOf(
                    Not(AtStand),
                    NoPendingReadback(instructionOfType<TaxiClearance>()),
                )),
                action = TaxiToStandAction,
                advancementPolicy = AdvancementPolicy.Immediate,
                // Stage stays at AwaitParked so this rule can re-fire if
                // the re-issue is also stepped on.
            ),
        ),
    ),
)

