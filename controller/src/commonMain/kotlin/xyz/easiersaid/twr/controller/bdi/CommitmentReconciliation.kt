package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.*

/**
 * Reconcile commitments based on current situation.
 *
 * New responsibilities get fresh commitments. Existing compatible commitments persist
 * (they advance via operators, not reformation). Completed and orphaned
 * initial-stage commitments are pruned.
 */
fun reconcileCommitments(
    existing: Map<AircraftId, Commitment>,
    role: RoleName,
    aircraft: Map<AircraftId, AircraftObservation>,
    responsibilities: Set<AircraftId>,
    activeRunway: RunwayId?,
    time: SimTime,
    worldIndex: WorldIndex,
    contactedThisCycle: Set<AircraftId> = emptySet(),
): Map<AircraftId, Commitment> {
    // Step 1: prune completed commitments. Fresh commitments can then form in
    // the same cycle a previous one completed (e.g. touch-and-go finishes an
    // arrival and the next circuit needs one).
    val alive = existing.filterValues { !it.isComplete }

    // Step 2: fold over responsibilities, preserving compatible in-progress
    // commitments and creating fresh ones only where needed. [reconcileOne]
    // closes over [role], [activeRunway], [time] and [worldIndex] — they
    // don't vary per aircraft inside the fold.
    fun reconcileOne(
        acc: Map<AircraftId, Commitment>,
        acId: AircraftId,
    ): Map<AircraftId, Commitment> {
        val ac = aircraft[acId] ?: return acc
        val current = acc[acId]
        val neededKind = determineServiceKind(role, ac) ?: return acc
        val preserve = current != null &&
            (isCompatible(current, neededKind) || !isAtInitialStage(current))
        if (preserve) return acc
        val commitment = createCommitment(neededKind, acId, ac, activeRunway, time, worldIndex)
            ?: return acc
        val withContact = if (acId in contactedThisCycle) commitment.copy(contacted = true) else commitment
        return acc + (acId to withContact)
    }

    val updated = responsibilities.fold(alive, ::reconcileOne)

    // Step 3: prune orphans — aircraft no longer in our responsibilities. The
    // sim is the authority on who we own; once responsibility moves, the
    // commitment goes too. Stage-label liveness (AwaitAtHolding vs handed-off)
    // is irrelevant here.
    return updated.filterKeys { it in responsibilities }
}

/** Determine what kind of service this aircraft needs based on role + entity position + goal. */
private fun determineServiceKind(role: RoleName, ac: AircraftObservation): CommitmentKind? {
    val wantsToDepart = ac.pilotGoal == PilotGoal.DEPART || ac.pilotGoal == PilotGoal.TOUCH_AND_GO
    val wantsToArrive = ac.pilotGoal == PilotGoal.ARRIVE || ac.pilotGoal == PilotGoal.TOUCH_AND_GO
    val onRunway = ac.entities.any { it is EntityRef.RunwayRef }
    val inCircuit = ac.entities.any { it is EntityRef.CircuitProcedureRef }
    val onApproach = ac.entities.any { it is EntityRef.ApproachRef }

    return when (role) {
        RoleName.TOWER -> when {
            // Touch-and-go on ground after landing: still arriving (vacate/circuit)
            ac.onGround && onRunway && ac.pilotGoal == PilotGoal.TOUCH_AND_GO -> CommitmentKind.TOWER_ARRIVAL
            // Normal departure: on ground wanting to depart
            ac.onGround && wantsToDepart -> CommitmentKind.TOWER_DEPARTURE
            // Airborne departure: not in circuit/approach
            !ac.onGround && wantsToDepart && !inCircuit && !onApproach -> CommitmentKind.TOWER_DEPARTURE
            // Landed aircraft not departing
            onRunway && ac.onGround && !wantsToDepart -> CommitmentKind.TOWER_ARRIVAL
            // In circuit or on approach
            inCircuit || onApproach -> CommitmentKind.TOWER_ARRIVAL
            // Airborne wanting to arrive
            !ac.onGround && wantsToArrive -> CommitmentKind.TOWER_ARRIVAL
            else -> null
        }
        RoleName.GROUND -> {
            // Arrival goal + already at stand means the journey is over: no
            // further taxi service is needed. Returning null here prevents the
            // completed GroundArrival commitment from being re-created every
            // cycle after it prunes, which would otherwise flood the frequency
            // with redundant TaxiTo instructions.
            val parked = ac.onGround && ac.entities.any { it is EntityRef.StandRef }
            val arrivalOnly = wantsToArrive && !wantsToDepart
            if (parked && arrivalOnly) null else CommitmentKind.GROUND_TAXI
        }
        RoleName.APPROACH -> when {
            wantsToArrive -> CommitmentKind.APPROACH_ARRIVAL
            else -> CommitmentKind.APPROACH_TRANSIT
        }
        RoleName.AREA_CONTROL -> CommitmentKind.AREA_TRANSIT
        RoleName.CLEARANCE_DELIVERY -> error("${RoleName.CLEARANCE_DELIVERY} commitments not yet modelled")
        RoleName.DEPARTURE -> error("${RoleName.DEPARTURE} commitments not yet modelled")
        RoleName.AFIS -> error("${RoleName.AFIS} commitments not yet modelled")
    }
}

private fun isCompatible(commitment: Commitment, neededKind: CommitmentKind?): Boolean =
    neededKind != null && commitment.kind == neededKind

/**
 * Whether [commitment] is still at its earliest stage (no operator has fired).
 *
 * Handled cases below cover every [CommitmentKind] companion value currently
 * defined. The `else -> true` guard catches any future [CommitmentKind]
 * variant not yet wired up — defaulting to "initial stage" is the safe choice
 * (reconciliation will replace with a fresh commitment rather than preserving
 * a half-understood one).
 */
private fun isAtInitialStage(commitment: Commitment): Boolean = when (commitment.kind) {
    CommitmentKind.TOWER_DEPARTURE -> commitment.stage == TowerDepartureStage.AwaitReady
    CommitmentKind.TOWER_ARRIVAL -> commitment.stage == TowerArrivalStage.AwaitDownwind
    CommitmentKind.GROUND_TAXI -> commitment.stage == GroundDepartureStage.AwaitTaxiRequest ||
        commitment.stage == GroundArrivalStage.TaxiToStand
    CommitmentKind.APPROACH_ARRIVAL -> commitment.stage == ApproachArrivalStage.AwaitDownwind
    CommitmentKind.APPROACH_TRANSIT -> true
    CommitmentKind.AREA_TRANSIT -> true
    else -> true
}

private fun createCommitment(
    kind: CommitmentKind,
    acId: AircraftId,
    ac: AircraftObservation,
    activeRunway: RunwayId?,
    time: SimTime,
    worldIndex: WorldIndex,
): Commitment? {
    val onRunway = ac.entities.any { it is EntityRef.RunwayRef }
    val inCircuit = ac.entities.any { it is EntityRef.CircuitProcedureRef }
    val onApproach = ac.entities.any { it is EntityRef.ApproachRef }
    val atHoldingPoint = activeRunway != null &&
        worldIndex.holdingPointsByRunway[activeRunway]?.contains(ac.position) == true

    return when (kind) {
        CommitmentKind.TOWER_DEPARTURE -> {
            val stage = when {
                onRunway && ac.onGround -> TowerDepartureStage.AwaitLineUpObserved
                !ac.onGround -> TowerDepartureStage.AwaitTakeoffObserved
                else -> TowerDepartureStage.AwaitReady
            }
            Commitment(acId, kind, stage, activeRunway, time)
        }
        CommitmentKind.TOWER_ARRIVAL -> {
            val stage = when {
                // Already off the runway — only the handoff remains; start in AwaitVacating.
                ac.onGround && !onRunway -> TowerArrivalStage.AwaitVacating
                ac.onGround && onRunway -> TowerArrivalStage.AwaitLandedObserved
                onApproach -> TowerArrivalStage.AwaitApproach
                else -> TowerArrivalStage.AwaitDownwind
            }
            Commitment(acId, kind, stage, activeRunway, time)
        }
        CommitmentKind.GROUND_TAXI -> {
            val wantsToDepart = ac.pilotGoal == PilotGoal.DEPART || ac.pilotGoal == PilotGoal.TOUCH_AND_GO
            val atStandEntity = ac.entities.any { it is EntityRef.StandRef }
            val stage = when {
                wantsToDepart && atHoldingPoint -> GroundDepartureStage.AwaitAtHolding
                wantsToDepart -> GroundDepartureStage.AwaitTaxiRequest
                else -> GroundArrivalStage.TaxiToStand
            }
            Commitment(acId, kind, stage, activeRunway, time)
        }
        CommitmentKind.APPROACH_ARRIVAL -> {
            // Single-stage procedure for 4e-B: approach watches for the aircraft
            // to reach downwind, then hands off to tower. Sequencing / vectoring
            // live in a later slice; until then Approach is a pure handoff point.
            Commitment(acId, kind, ApproachArrivalStage.AwaitDownwind, activeRunway, time)
        }
        CommitmentKind.APPROACH_TRANSIT -> error("APPROACH_TRANSIT stage machine not yet modelled")
        CommitmentKind.AREA_TRANSIT -> error("AREA_TRANSIT stage machine not yet modelled")
        else -> error("Unknown commitment kind: $kind")
    }
}

/** Tower-departure stage hierarchy. */
sealed interface TowerDepartureStage : Stage {
    data object AwaitReady : TowerDepartureStage { override val name = "AwaitReady" }
    data object AwaitLineUpObserved : TowerDepartureStage {
        override val name = "AwaitLineUpObserved"
    }
    data object AwaitTakeoffObserved : TowerDepartureStage {
        override val name = "AwaitTakeoffObserved"
    }
    data object Complete : TowerDepartureStage {
        override val name = "Complete"
        override val isComplete = true
    }
}

/** Tower-arrival stage hierarchy. */
sealed interface TowerArrivalStage : Stage {
    data object AwaitDownwind : TowerArrivalStage { override val name = "AwaitDownwind" }
    data object AwaitApproach : TowerArrivalStage { override val name = "AwaitApproach" }
    data object AwaitLandedObserved : TowerArrivalStage {
        override val name = "AwaitLandedObserved"
    }
    /**
     * Aircraft has been instructed to vacate; awaiting the post-runway handoff
     * to ground. Terminal-ish: the commitment leaves this stage only by being
     * orphan-pruned after responsibility transfers to ground (successful
     * handoff) or by re-firing the handoff rule if the pending ContactFrequency
     * readback times out (stepped-on transmission).
     */
    data object AwaitVacating : TowerArrivalStage { override val name = "AwaitVacating" }
    data object Complete : TowerArrivalStage {
        override val name = "Complete"
        override val isComplete = true
    }
}

/** Ground-departure stage hierarchy. */
sealed interface GroundDepartureStage : Stage {
    data object AwaitTaxiRequest : GroundDepartureStage {
        override val name = "AwaitTaxiRequest"
    }
    data object AwaitAtHolding : GroundDepartureStage {
        override val name = "AwaitAtHolding"
    }
    data object Complete : GroundDepartureStage {
        override val name = "Complete"
        override val isComplete = true
    }
}

/** Ground-arrival stage hierarchy. */
sealed interface GroundArrivalStage : Stage {
    data object TaxiToStand : GroundArrivalStage { override val name = "TaxiToStand" }
    data object AwaitParked : GroundArrivalStage { override val name = "AwaitParked" }
    data object Complete : GroundArrivalStage {
        override val name = "Complete"
        override val isComplete = true
    }
}

/**
 * Approach-arrival stage hierarchy.
 *
 * 4e-B scope: approach is a handoff point, not a sequencing layer. When the
 * aircraft reaches the downwind leg the controller hands off to tower and the
 * commitment completes. Vectoring, speed control, STAR sequencing etc. will
 * add stages in the Approach-sequencing slice later.
 */
sealed interface ApproachArrivalStage : Stage {
    data object AwaitDownwind : ApproachArrivalStage { override val name = "AwaitDownwind" }
    data object Complete : ApproachArrivalStage {
        override val name = "Complete"
        override val isComplete = true
    }
}
