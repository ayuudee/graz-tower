package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * Reconcile commitments based on current situation.
 *
 * New responsibilities get fresh commitments. Existing compatible commitments persist
 * (they advance via operators, not reformation). Completed and orphaned
 * initial-stage commitments are pruned.
 */
@Suppress("LongParameterList", "NestedBlockDepth") // every parameter is a context input the
// reconciliation genuinely needs; bundling is cosmetic. NestedBlockDepth: nested fold is the
// natural shape for per-aircraft × per-stage decision; flattening forces auxiliary state.
fun reconcileCommitments(
    existing: Map<AircraftId, Commitment>,
    role: RoleName,
    aircraft: Map<AircraftId, AircraftObservation>,
    responsibilities: Set<AircraftId>,
    activeRunway: RunwayId?,
    time: SimTime,
    worldIndex: WorldIndex,
    flightStripIntents: Map<AircraftId, AircraftIntent> = emptyMap(),
    recentRadio: Map<AircraftId, xyz.easiersaid.twr.controller.observe.RecentRadio> = emptyMap(),
    circuitIntent: Map<AircraftId, CircuitIntent> = emptyMap(),
    contactedThisCycle: Set<AircraftId> = emptySet(),
): Map<AircraftId, Commitment> {
    // Step 1: prune completed commitments. Fresh commitments can then form in
    // the same cycle a previous one completed (e.g. touch-and-go finishes an
    // arrival and the next circuit needs one).
    val alive = existing.filterValues { !it.isComplete }

    // Step 2: fold over responsibilities. Pass 5 (D-AUDIT.14 closure):
    // intent is derived on demand from primary sources (strip + recentRadio)
    // via `deriveCurrentIntent`, not consulted from a cached slice.
    fun reconcileOne(
        acc: Map<AircraftId, Commitment>,
        acId: AircraftId,
    ): Map<AircraftId, Commitment> {
        val ac = aircraft[acId] ?: return acc
        val intent: AircraftIntent = xyz.easiersaid.twr.controller.observe.deriveIntent(
            flightStripIntents, recentRadio, acId,
        )
        val isCircuitTraffic = acId in circuitIntent
        val current = acc[acId]
        val neededKind = determineServiceKind(role, ac, intent, isCircuitTraffic) ?: return acc
        val preserve = current != null &&
            (isCompatible(current, neededKind) || !isAtInitialStage(current))
        if (preserve) return acc
        val commitment = createCommitment(neededKind, acId, ac, intent, isCircuitTraffic, activeRunway, time, worldIndex)
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

/**
 * Determine what kind of service this aircraft needs based on role, entity
 * position, the controller's belief about the aircraft's broad service intent
 * ([AircraftIntent]), and whether the aircraft has declared itself as circuit
 * traffic ([isCircuitTraffic]).
 *
 * The previous implementation read [AircraftObservation.pilotGoal] directly
 * — a firewall leak. The new implementation reads only:
 *  - sensor-derived position ([ac.entities], [ac.onGround]);
 *  - controller-side beliefs populated from radio + flight strip.
 */
internal fun determineServiceKind(
    role: RoleName,
    ac: AircraftObservation,
    intent: AircraftIntent?,
    isCircuitTraffic: Boolean,
): CommitmentKind? = when (role) {
    RoleName.TOWER -> serviceKindForTower(ac, intent, isCircuitTraffic)
    RoleName.GROUND -> serviceKindForGround(ac, intent)
    RoleName.APPROACH -> if (intent == AircraftIntent.Arriving) CommitmentKind.APPROACH_ARRIVAL
        else CommitmentKind.APPROACH_TRANSIT
    RoleName.AREA_CONTROL -> CommitmentKind.AREA_TRANSIT
    RoleName.CLEARANCE_DELIVERY -> error("${RoleName.CLEARANCE_DELIVERY} commitments not yet modelled")
    RoleName.DEPARTURE -> error("${RoleName.DEPARTURE} commitments not yet modelled")
    RoleName.AFIS -> error("${RoleName.AFIS} commitments not yet modelled")
}

private fun serviceKindForTower(
    ac: AircraftObservation,
    intent: AircraftIntent?,
    isCircuitTraffic: Boolean,
): CommitmentKind? {
    val isDeparting = intent == AircraftIntent.Departing
    val isArriving = intent == AircraftIntent.Arriving
    val onRunway = ac.entities.any { it is EntityRef.RunwayRef }
    val inCircuit = ac.entities.any { it is EntityRef.CircuitProcedureRef }
    val onApproach = ac.entities.any { it is EntityRef.ApproachRef }
    return when {
        // Circuit traffic on the runway after touchdown — still TOWER_ARRIVAL
        // until either the aircraft lifts off again (T&G → ARR-TNG-AIRBORNE
        // completes the arrival) or vacates (full-stop → ARR-VACATE fires).
        ac.onGround && onRunway && isCircuitTraffic -> CommitmentKind.TOWER_ARRIVAL
        // Circuit traffic airborne anywhere — TOWER_ARRIVAL handles sequencing
        // and landing. The pilot has reported a Downwind with intent so we
        // know they are flying the pattern.
        !ac.onGround && isCircuitTraffic -> CommitmentKind.TOWER_ARRIVAL
        // Normal departure: on ground wanting to depart, no circuit declared yet.
        ac.onGround && isDeparting -> CommitmentKind.TOWER_DEPARTURE
        // Airborne one-shot departure: not in circuit/approach, intent Departing.
        !ac.onGround && isDeparting && !inCircuit && !onApproach -> CommitmentKind.TOWER_DEPARTURE
        // Landed aircraft not departing.
        onRunway && ac.onGround && !isDeparting -> CommitmentKind.TOWER_ARRIVAL
        // In circuit or on approach by entity (one-shot inbound that hasn't yet
        // reported intent).
        inCircuit || onApproach -> CommitmentKind.TOWER_ARRIVAL
        // Airborne arrival.
        !ac.onGround && isArriving -> CommitmentKind.TOWER_ARRIVAL
        else -> null
    }
}

private fun serviceKindForGround(ac: AircraftObservation, intent: AircraftIntent?): CommitmentKind? {
    // Arrival intent + already at stand means the journey is over: no further
    // taxi service is needed. Returning null here prevents the completed
    // GroundArrival commitment from being re-created every cycle after it
    // prunes, which would otherwise flood the frequency with redundant TaxiTo
    // instructions.
    val parked = ac.onGround && ac.entities.any { it is EntityRef.StandRef }
    val arrivalOnly = intent == AircraftIntent.Arriving
    return if (parked && arrivalOnly) null else CommitmentKind.GROUND_TAXI
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

@Suppress("LongParameterList") // commitment factory: every parameter is a context input;
// bundling them is cosmetic.
private fun createCommitment(
    kind: CommitmentKind,
    acId: AircraftId,
    ac: AircraftObservation,
    intent: AircraftIntent?,
    isCircuitTraffic: Boolean,
    activeRunway: RunwayId?,
    time: SimTime,
    worldIndex: WorldIndex,
): Commitment? {
    val atHoldingPoint = activeRunway != null &&
        worldIndex.holdingPointsByRunway[activeRunway]?.contains(ac.position) == true

    return when (kind) {
        CommitmentKind.TOWER_DEPARTURE -> {
            // Always start at AwaitReady. The reconciliation function will
            // advance the stage based on the observed position in the same
            // cycle. This ensures that an aircraft on the runway without a
            // line-up clearance gets TransitionKind.ANOMALOUS (incursion),
            // not a silent skip to AwaitLineUpObserved.
            Commitment(acId, kind, TowerDepartureStage.AwaitReady, activeRunway, time)
        }
        CommitmentKind.TOWER_ARRIVAL -> {
            // Always start at AwaitDownwind. The reconciliation function will
            // advance the stage based on the observed position in the same cycle.
            Commitment(acId, kind, TowerArrivalStage.AwaitDownwind, activeRunway, time)
        }
        CommitmentKind.GROUND_TAXI -> {
            val isDeparting = intent == AircraftIntent.Departing
            val onRunway = ac.entities.any { it is EntityRef.RunwayRef }
            val atStand = ac.entities.any { it is EntityRef.StandRef }
            val stage = when {
                // Post-landing circuit traffic: the controller observes the
                // aircraft on the ground, off the runway, not yet at a stand,
                // and `isCircuitTraffic` (the pilot declared a circuit intent
                // on downwind, so this is the taxi-back leg of a circuit
                // training mission). No need to read pilot mission state —
                // the controller's observation suffices to classify this as
                // arrival flow.
                //
                // Without this arm, the strip-seeded `aircraftIntent =
                // Departing` would route to `GroundDepartureStage`, and the
                // post-vacate aircraft would wedge waiting for a TaxiRequested
                // event that never comes (the pilot's mission has moved past
                // REQUEST_TAXI to AWAIT_VACATE_INSTRUCTION).
                //
                // This is a small surgical fix; the deeper D-AUDIT.14 audit
                // (deriving CommitmentKind purely from primary observable
                // sources) would replace this whole function.
                isCircuitTraffic && ac.onGround && !onRunway && !atStand ->
                    GroundArrivalStage.TaxiToStand
                isDeparting && atHoldingPoint -> GroundDepartureStage.AwaitAtHolding
                isDeparting -> GroundDepartureStage.AwaitTaxiRequest
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
        // Transit: approach/area monitors the aircraft but doesn't issue instructions.
        // No commitment → no procedure → no outputs. The aircraft just passes through.
        CommitmentKind.APPROACH_TRANSIT -> null
        CommitmentKind.AREA_TRANSIT -> null
        else -> error("Unknown commitment kind: $kind")
    }
}

/**
 * Tower-departure stage hierarchy.
 *
 * [ordinal] defines the forward-only ordering for observation-driven
 * reconciliation. Reconciliation may advance the stage (observation wins)
 * but never regresses it, enforced by `maxOf(current.ordinal, observed.ordinal)`.
 *
 * Named regression paths (e.g. rejected takeoff → back to AwaitLineUpObserved)
 * are explicit transitions in the reconciliation function, not violations
 * of the ordering.
 */
sealed interface TowerDepartureStage : Stage {
    /** Monotonic ordering for forward-only reconciliation. */
    val ordinal: Int
    data object AwaitReady : TowerDepartureStage {
        override val name = "AwaitReady"; override val ordinal = 0
    }
    data object AwaitLineUpObserved : TowerDepartureStage {
        override val name = "AwaitLineUpObserved"; override val ordinal = 1
    }
    /** ClearedForTakeoff issued, awaiting readback confirmation. Explicit state
     *  that was previously invisible in the coordination ledger. */
    data object TakeoffClearanceIssued : TowerDepartureStage {
        override val name = "TakeoffClearanceIssued"; override val ordinal = 2
    }
    data object AwaitTakeoffObserved : TowerDepartureStage {
        override val name = "AwaitTakeoffObserved"; override val ordinal = 3
    }
    data object Complete : TowerDepartureStage {
        override val name = "Complete"
        override val isComplete = true; override val ordinal = 4
    }
}

/**
 * Tower-arrival stage hierarchy.
 *
 * [ordinal] defines the forward-only ordering for reconciliation.
 * Go-around is a defined regression path (AwaitApproach/AwaitLandedObserved → AwaitDownwind)
 * that is a named exception to the forward-only invariant, not a violation of it.
 */
sealed interface TowerArrivalStage : Stage {
    val ordinal: Int
    data object AwaitDownwind : TowerArrivalStage {
        override val name = "AwaitDownwind"; override val ordinal = 0
    }
    data object AwaitApproach : TowerArrivalStage {
        override val name = "AwaitApproach"; override val ordinal = 1
    }
    /** Landing clearance issued (ClearedToLand or ClearedTouchAndGo), awaiting readback. */
    data object LandingClearanceIssued : TowerArrivalStage {
        override val name = "LandingClearanceIssued"; override val ordinal = 2
    }
    data object AwaitLandedObserved : TowerArrivalStage {
        override val name = "AwaitLandedObserved"; override val ordinal = 3
    }
    /**
     * Aircraft has been instructed to vacate; awaiting the post-runway handoff
     * to ground. Terminal-ish: the commitment leaves this stage only by being
     * orphan-pruned after responsibility transfers to ground (successful
     * handoff) or by re-firing the handoff rule if the pending ContactFrequency
     * readback times out (stepped-on transmission).
     */
    data object AwaitVacating : TowerArrivalStage {
        override val name = "AwaitVacating"; override val ordinal = 4
    }
    data object Complete : TowerArrivalStage {
        override val name = "Complete"
        override val isComplete = true; override val ordinal = 5
    }
}

/** Ground-departure stage hierarchy. */
sealed interface GroundDepartureStage : Stage {
    val ordinal: Int
    data object AwaitTaxiRequest : GroundDepartureStage {
        override val name = "AwaitTaxiRequest"; override val ordinal = 0
    }
    data object AwaitAtHolding : GroundDepartureStage {
        override val name = "AwaitAtHolding"; override val ordinal = 1
    }
    data object Complete : GroundDepartureStage {
        override val name = "Complete"
        override val isComplete = true; override val ordinal = 2
    }
}

/** Ground-arrival stage hierarchy. */
sealed interface GroundArrivalStage : Stage {
    val ordinal: Int
    data object TaxiToStand : GroundArrivalStage {
        override val name = "TaxiToStand"; override val ordinal = 0
    }
    data object AwaitParked : GroundArrivalStage {
        override val name = "AwaitParked"; override val ordinal = 1
    }
    data object Complete : GroundArrivalStage {
        override val name = "Complete"
        override val isComplete = true; override val ordinal = 2
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
