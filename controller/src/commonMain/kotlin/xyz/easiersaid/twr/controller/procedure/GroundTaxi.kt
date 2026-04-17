package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.*
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_10_1
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_7_6
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_FREQUENCY_CHANGE
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_TAXI
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.TaxiTo

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
                guard = AnyOf(listOf(TaxiRequested, AiProactive)),
                action = TaxiToHoldingAction,
                nextStage = GroundDepartureStage.AwaitAtHolding,
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
                )),
                action = HandoffAction(RoleName.TOWER),
                // Stay at AwaitAtHolding. Pruning happens when responsibility actually
                // transfers (orphan-prune in reconcileCommitments).
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
                guard = NoActiveInstruction(instructionOfType<TaxiTo>()),
                action = TaxiToStandAction,
                nextStage = GroundArrivalStage.AwaitParked,
            ),
        ),
        GroundArrivalStage.AwaitParked to listOf(
            AtcRule(
                id = "GND-PARKED",
                description = "Ground arrival complete when aircraft at stand",
                regulations = listOf(ICAO9432_TAXI),
                guard = AtStand,
                nextStage = GroundArrivalStage.Complete,
            ),
        ),
    ),
)

