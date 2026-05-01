package xyz.easiersaid.twr.controller.procedure

import xyz.easiersaid.twr.controller.bdi.AllOf
import xyz.easiersaid.twr.controller.bdi.ApproachArrivalStage
import xyz.easiersaid.twr.controller.bdi.AtcRule
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.HandoffAction
import xyz.easiersaid.twr.controller.bdi.IsTransferTargetStaffed
import xyz.easiersaid.twr.controller.bdi.NoPendingReadback
import xyz.easiersaid.twr.controller.bdi.OnCircuitLeg
import xyz.easiersaid.twr.controller.bdi.ProcedureSpec
import xyz.easiersaid.twr.controller.bdi.instructionOfType
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO4444_10_1
import xyz.easiersaid.twr.protocol.RegulationDatabase.ICAO9432_FREQUENCY_CHANGE
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.controller.observe.AdvancementPolicy

/**
 * Approach-arrival procedure — a single-stage handoff procedure for 4e-B.
 *
 * Approach owns the aircraft on the inbound transit and hands off to tower
 * when the aircraft reaches the downwind leg of the circuit. Sequencing,
 * vectoring, speed control, and STAR management land in a later slice; for
 * now Approach is a pure handoff point so the arrival vertical can close.
 *
 * Idempotency mirrors [towerDepartureProcedure]'s `DEP-HANDOFF`: the
 * [NoPendingReadback] guard blocks re-firing while the [ContactFrequency]
 * instruction is still outbound, and the pending register's 30 s GC horizon
 * doubles as the retransmit timer (CAP 413 §2.7).
 *
 * **Caveat:** downwind is the wrong handoff point for most real aerodromes —
 * approach normally releases to tower at ILS intercept or 8 nm final. Phase 4
 * explicitly scopes approach to "pure handoff point"; proper sequencing /
 * vectoring / release-point logic lands in the Approach-sequencing slice.
 */
fun approachArrivalProcedure(): ProcedureSpec = ProcedureSpec(
    kind = CommitmentKind.APPROACH_ARRIVAL,
    stageRules = mapOf(
        ApproachArrivalStage.AwaitDownwind to listOf(
            AtcRule(
                id = "APP-HANDOFF-DOWNWIND",
                description = "Hand arriving traffic to tower when established on the downwind leg",
                // Transfer of *communications* (§10.1) between APP and TWR.
                // Phraseology is the frequency-change instruction per Doc 9432.
                regulations = listOf(ICAO4444_10_1, ICAO9432_FREQUENCY_CHANGE),
                guard = AllOf(listOf(
                    OnCircuitLeg(LegName.DOWNWIND),
                    NoPendingReadback(instructionOfType<ContactFrequency>()),
                    IsTransferTargetStaffed(RoleName.TOWER),
                )),
                action = HandoffAction(RoleName.TOWER),
                nextStage = ApproachArrivalStage.Complete,
                advancementPolicy = AdvancementPolicy.Immediate,
            ),
        ),
    ),
)
