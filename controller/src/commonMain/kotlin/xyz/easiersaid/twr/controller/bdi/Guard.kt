package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.RunwayStatus
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.ControllerEvent
import xyz.easiersaid.twr.controller.observe.isSeverityAtLeast
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Meters
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.reflect.KClass

/**
 * Shared context for guard evaluation and action resolution.
 * Single context — no split between guard and action concerns.
 */
data class OperatorContext(
    val view: ControllerView,
    val beliefs: BeliefState,
    val events: List<ControllerEvent>,
    val world: AviationWorld,
) {
    val time: SimTime get() = view.time
    val worldIndex: WorldIndex get() = view.worldIndex
    val weather: WeatherObservation? get() = view.weather

    /**
     * Derive an aircraft's broad service intent from primary sources
     * (strip + recentRadio). Pass 5 (D-AUDIT.14 closure) — replaces direct
     * reads of the deleted `BeliefState.aircraftIntent` slice. Routes
     * through the single `deriveIntent` accessor so absent-key semantics
     * are consistent across all consumers.
     */
    fun intentOf(aircraft: xyz.easiersaid.twr.protocol.AircraftId): xyz.easiersaid.twr.controller.observe.AircraftIntent =
        xyz.easiersaid.twr.controller.observe.deriveIntent(view.flightStripIntents, beliefs.recentRadio, aircraft)
}

/**
 * Composable guard predicate evaluated against aircraft state, commitment, and context.
 * Entity-aware: guards check [EntityRef] membership, not AircraftPhase enums.
 */
sealed interface RuleGuard {
    fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean
    /** Human-readable explanation when this guard fails. For training feedback. */
    val failureMessage: String get() = this::class.simpleName ?: "unknown guard"
}

// ── Combinators ──────────────────────────────────────────────────────

data class AllOf(val guards: List<RuleGuard>) : RuleGuard {
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        guards.all { it.evaluate(ac, commitment, ctx) }
}

data class AnyOf(val guards: List<RuleGuard>) : RuleGuard {
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        guards.any { it.evaluate(ac, commitment, ctx) }
}

data class Not(val inner: RuleGuard) : RuleGuard {
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        !inner.evaluate(ac, commitment, ctx)
}

data object Always : RuleGuard {
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) = true
}

// ── Entity-aware position checks ────────────────────────────────────

/** Aircraft is at a point belonging to a runway entity. */
data object OnRunway : RuleGuard {
    override val failureMessage = "Aircraft is not on the runway"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ac.entities.any { it is EntityRef.RunwayRef }
}

/** Aircraft is on the ground. */
data object OnGround : RuleGuard {
    override val failureMessage = "Aircraft is not on the ground"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ac.onGround
}

/** Aircraft is airborne. */
data object Airborne : RuleGuard {
    override val failureMessage = "Aircraft is not airborne"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        !ac.onGround
}

/** Last observation reconciliation was anomalous (e.g. runway incursion). Single-cycle flag. */
data object AnomalousTransition : RuleGuard {
    override val failureMessage = "Last reconciliation was not anomalous"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        commitment.lastTransition == xyz.easiersaid.twr.controller.procedure.TransitionKind.ANOMALOUS
}

/** Aircraft is at a known holding point for the commitment's runway. */
data object AtHoldingPoint : RuleGuard {
    override val failureMessage = "Aircraft is not at a holding point for this runway"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway ?: return false
        val holdingPoints = ctx.worldIndex.holdingPointsByRunway[runway] ?: return false
        return ac.position in holdingPoints
    }
}

/** Aircraft is on a circuit procedure entity. */
data object InCircuit : RuleGuard {
    override val failureMessage = "Aircraft is not in the circuit pattern"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ac.entities.any { it is EntityRef.CircuitProcedureRef }
}

/** Aircraft is on an approach procedure entity. */
data object OnApproach : RuleGuard {
    override val failureMessage = "Aircraft is not on an approach procedure"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ac.entities.any { it is EntityRef.ApproachRef }
}

// ── Communication ────────────────────────────────────────────────────

/** Two-way communication established with this aircraft. */
data object ContactEstablished : RuleGuard {
    override val failureMessage = "Two-way communication not yet established"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        commitment.contacted
}

/**
 * No pending (unacknowledged) readback for an instruction matching [matcher].
 *
 * The idempotency pair for fire-and-forget rules — handoffs, position-info,
 * QNH updates — whose effect lands several ticks after the controller speaks.
 * The pending-readback register is the already-authoritative "what's in flight"
 * store: every outgoing [ControllerOutput.Instruct] is appended there by
 * [recordPendingReadbacks] and either popped when the readback arrives or
 * GC'd after [MAX_READBACK_AGE].
 *
 * That GC horizon gives the retry behaviour for free: while a readback is in
 * flight the rule is blocked; if no readback arrives within 30 s the entry ages
 * out and the rule fires again — the "how copy?" retransmit (CAP 413 §2.7).
 */
data class NoPendingReadback(val matcher: InstructionMatcher) : RuleGuard {
    override val failureMessage = "An instruction matching this matcher is already pending readback"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.beliefs.pendingReadbacks[ac.id].orEmpty()
            .none { matcher.matches(it.instruction) }
}

// ── Pilot events ─────────────────────────────────────────────────────

/** Pilot has reported ready for departure this cycle. */
data object PilotReady : RuleGuard {
    override val failureMessage = "Pilot has not reported ready for departure"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.events.any { it is ControllerEvent.ReadyForDepartureReceived && it.aircraft == ac.id }
}

/** Pilot reported position this cycle. */
data object PositionReported : RuleGuard {
    override val failureMessage = "Pilot has not reported position this cycle"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.events.any { it is ControllerEvent.PositionReported && it.aircraft == ac.id }
}

/** Pilot has made initial contact this cycle. */
data object ContactReceived : RuleGuard {
    override val failureMessage = "Pilot has not made initial contact"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.events.any { it is ControllerEvent.InitialContactReceived && it.aircraft == ac.id }
}

/** Pilot has requested taxi this cycle. */
data object TaxiRequested : RuleGuard {
    override val failureMessage = "Pilot has not requested taxi"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.events.any { it is ControllerEvent.TaxiRequested && it.aircraft == ac.id }
}

// ── Service intent (firewall-clean) ──────────────────────────────────
//
// These guards read controller-side belief slices populated only from
// radio + flight-strip channels. They replace the leaky [PilotGoalIs]
// guard which read pilot-internal state via [AircraftObservation.pilotGoal].

/**
 * Broad service intent: derives the aircraft's intent on demand from primary
 * sources (strip + recentRadio) via [OperatorContext.intentOf]. Pass 5
 * (D-AUDIT.14 closure) replaces the cached `aircraftIntent` slice with this
 * derivation; behaviour is unchanged.
 */
data class AircraftIntentIs(
    val intent: xyz.easiersaid.twr.controller.observe.AircraftIntent,
) : RuleGuard {
    override val failureMessage = "Aircraft service intent is not $intent"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.intentOf(ac.id) == intent
}

/**
 * Per-circuit landing intent: matches the controller's belief about whether
 * the next landing is touch-and-go or full-stop. Populated only from the
 * pilot's downwind radio call carrying a non-null [CircuitIntent].
 *
 * Default semantics on absence: returns false. Operationally undeclared
 * circuit traffic defaults to TOUCH_AND_GO under ICAO/SERA, so absent
 * entries naturally yield [CircuitIntentIs(FULL_STOP)] = false → the
 * touch-and-go rule (gated by `Not(CircuitIntentIs(FULL_STOP))`) fires.
 */
data class CircuitIntentIs(val intent: CircuitIntent) : RuleGuard {
    override val failureMessage = "Circuit intent is not $intent"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.beliefs.circuitIntent[ac.id] == intent
}

/**
 * The pilot has at any point reported a circuit intent (TOUCH_AND_GO or
 * FULL_STOP). Used to discriminate "circuit traffic" — aircraft committed to
 * the aerodrome traffic circuit — from a one-shot departure that happens to
 * climb out via the same waypoints.
 *
 * The controller's circuit-completion rule (DEP-CIRCUIT-COMPLETE) fires only
 * for circuit traffic so a non-circuit departure is handed off normally.
 */
data object IsCircuitTraffic : RuleGuard {
    override val failureMessage = "Aircraft has not declared circuit intent"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ac.id in ctx.beliefs.circuitIntent
}

/**
 * The role this rule is about to hand off to has a staffed controller at the
 * current aerodrome.
 *
 * Pass 6 (post-impl Impact-M.2): the aerodrome may *publish* a role (e.g.
 * LOWG APPROACH / GRAZ RADAR) that isn't *staffed* in the current run. Pre-
 * Pass-6, `HandoffAction.resolve` simply failed to resolve when the target
 * was missing from `aerodrome.roles`. Pass 6 added publication, which made
 * "published-but-unstaffed" reachable; the inline check there returned
 * `ActionResolutionFailure` and the rule kept retrying — silent wedge mode
 * if no sibling rule advanced the commitment.
 *
 * Solution: gate the rule itself on staffing. If the target role is
 * unstaffed, this guard is `false` and the rule doesn't fire — the
 * commitment stays in place; the controller keeps the aircraft. The proper
 * "no successor: terminate radar service / approve frequency change" rule
 * (per ICAO Doc 4444 §10.1, *"radar service terminated, squawk 7000,
 * frequency change approved"*) is **deferred as D-PF.7** — Pass 7 owns
 * the boundary-release rule that fires when a handoff target is
 * unreachable AND the aircraft has left the controller's airspace.
 *
 * Until D-PF.7 lands, this guard's failure message names what real ATC
 * would do, so the deferment is visible in the trace, not silent.
 */
data class IsTransferTargetStaffed(val toRole: xyz.easiersaid.twr.protocol.RoleName) : RuleGuard {
    override val failureMessage =
        "${toRole.name} is published at this aerodrome but unstaffed in this run; " +
            "real ATC would terminate radar service per ICAO Doc 4444 §10.1 — " +
            "deferred as D-PF.7."

    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        toRole in ctx.view.staffedRoles
}

// ── Runway state ─────────────────────────────────────────────────────

/** This aircraft has been granted runway access. */
data object RunwayAccessGranted : RuleGuard {
    override val failureMessage = "Runway access not yet granted — another aircraft may have priority"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.beliefs.runwayDuty?.holder == ac.id
}

/** The commitment's runway is physically clear. */
data object RunwayPhysicallyClear : RuleGuard {
    override val failureMessage = "Runway is occupied by another aircraft"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway ?: return false
        val rwyObs = ctx.beliefs.runwayBeliefs[runway] ?: return true
        return rwyObs.status == RunwayStatus.CLEAR ||
            (rwyObs.occupants.size == 1 && ac.id in rwyObs.occupants)
    }
}

// ── Weather ──────────────────────────────────────────────────────────

/** Weather permits VFR operations — visibility >= 5000m. */
data object WeatherPermitsVfr : RuleGuard {
    override val failureMessage = "Weather below VMC minima — VFR operations not permitted"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val weather = ctx.weather ?: return true // no weather info = don't block
        val visibility = weather.visibility ?: return true
        return visibility >= 5000
    }
}

// ── Clearance state ──────────────────────────────────────────────────

/**
 * Inspectable predicate over [AtcInstruction] used by [NoActiveInstruction].
 *
 * Replaces the earlier `(AtcInstruction) -> Boolean` lambda so that guards
 * carrying the same matcher remain structurally equal (KClass has structural
 * equality, which Kotlin lambdas do not) and so rule traces can describe
 * what the guard was looking for.
 */
sealed interface InstructionMatcher {
    fun matches(instruction: AtcInstruction): Boolean

    /** Matches iff the instruction is an instance of [type]. */
    data class OfType(val type: KClass<out AtcInstruction>) : InstructionMatcher {
        override fun matches(instruction: AtcInstruction) = type.isInstance(instruction)
    }

    /** Matches iff any of [matchers] matches. Empty list never matches. */
    data class AnyOf(val matchers: List<InstructionMatcher>) : InstructionMatcher {
        override fun matches(instruction: AtcInstruction) =
            matchers.any { it.matches(instruction) }
    }
}

/** Shorthand for `InstructionMatcher.OfType(T::class)`. */
inline fun <reified T : AtcInstruction> instructionOfType(): InstructionMatcher =
    InstructionMatcher.OfType(T::class)

/** No active non-terminal clearance matching [matcher] for this aircraft. */
data class NoActiveInstruction(val matcher: InstructionMatcher) : RuleGuard {
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.beliefs.issuedClearances.values.none { clr ->
            clr.aircraft == ac.id && !clr.status.isTerminal && matcher.matches(clr.instruction)
        }
}

/** Aircraft is on a specific circuit leg (UPWIND, CROSSWIND, DOWNWIND, BASE, FINAL). */
data class OnCircuitLeg(val leg: LegName) : RuleGuard {
    override val failureMessage = "Aircraft is not on the ${leg.name} leg"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.worldIndex.circuitLegsByPoint[ac.position]?.contains(leg) == true
}

/**
 * Aircraft is within [maxMetres] of the active runway threshold.
 *
 * Used to gate landing clearance issuance — clearance is withheld until the aircraft is
 * close enough that the runway will still be physically clear at touchdown, while leaving
 * enough time for a safe go-around if needed. Conservatively returns false when the
 * runway or threshold position cannot be resolved (unknown position = do not clear).
 */
data class WithinDistanceOfThreshold(val maxMetres: Meters) : RuleGuard {
    override val failureMessage = "Aircraft is beyond ${maxMetres.value}m of the runway threshold"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway ?: return false
        val thresholdPoint = ctx.worldIndex.thresholdByRunway[runway] ?: return false
        val acPos = ctx.worldIndex.positions[ac.position] ?: return false
        val thrPos = ctx.worldIndex.positions[thresholdPoint] ?: return false
        val dx = acPos.xMeters - thrPos.xMeters
        val dy = acPos.yMeters - thrPos.yMeters
        val limit = maxMetres.value
        return (dx * dx + dy * dy) <= limit * limit
    }
}

/**
 * Aircraft is more than [thresholdMetres] from the aerodrome reference point —
 * a **radial-distance approximation** of "outside the CTR boundary."
 *
 * Pass 7 (D-PF.7 closure): used by the boundary-release rules to gate
 * `TerminateRadarServiceAction`. The conservative 12 NM (~22.2 km) default
 * fails closed: aircraft past the actual CTR but inside 12 NM stay with
 * the controller until they reach the threshold (under-fires the release
 * rather than over-firing inside controlled airspace, which would be
 * regulatorily wrong).
 *
 * Real CTR boundaries are typed polygons — `OutsideAerodromeRadius` flags
 * the approximation in its name so a future polygon guard
 * `OutsideAirspaceVolume(AirspaceVolume)` reads as a sibling, not a
 * rename. **D-AUDIT.7** owns the polygon-membership upgrade.
 *
 * Reads the aerodrome's reference point or threshold; conservatively
 * returns false when the position cannot be resolved (unknown position =
 * do not release).
 */
data class OutsideAerodromeRadius(val thresholdMetres: Meters) : RuleGuard {
    override val failureMessage = "Aircraft within ${thresholdMetres.value}m radial of aerodrome (still in CTR scope)"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val aerodrome = ctx.world.aerodromes[ctx.view.aerodromeId] ?: return false
        // Use the first runway's threshold as a stand-in for the aerodrome
        // reference point. Real ARPs come with the airport manifest under
        // a separate `referencePoint` field; today the field exists on
        // Aerodrome but isn't populated for every aerodrome (some are null
        // until D-AUDIT.7's CTR-polygon work). The threshold is a reasonable
        // proxy at small fields.
        val arpPointId = aerodrome.runways.values.firstOrNull()?.threshold ?: return false
        val acPos = ctx.worldIndex.positions[ac.position] ?: return false
        val arpPos = ctx.worldIndex.positions[arpPointId] ?: return false
        val dx = acPos.xMeters - arpPos.xMeters
        val dy = acPos.yMeters - arpPos.yMeters
        val limit = thresholdMetres.value
        return (dx * dx + dy * dy) > limit * limit
    }
}

/** Pilot reported going around this cycle. */
data object GoAroundEvent : RuleGuard {
    override val failureMessage = "No go-around reported this cycle"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.events.any { it is ControllerEvent.GoAroundDetected && it.aircraft == ac.id }
}

/** Aircraft is at a stand entity. */
data object AtStand : RuleGuard {
    override val failureMessage = "Aircraft is not at a parking stand"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ac.entities.any { it is EntityRef.StandRef }
}

/**
 * Some OTHER aircraft holds a landing commitment that has reached the final leg
 * of the active runway. Used to block LineUpAndWait when an arrival is on short
 * final (ICAO Doc 4444 §7.10 — landing traffic has priority, and lining up a
 * departure in front of a committed arrival erodes the approach buffer).
 */
data object OtherTrafficOnShortFinal : RuleGuard {
    override val failureMessage = "Another aircraft is on short final for this runway"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        return ctx.beliefs.commitments.any { (otherId, other) ->
            otherId != ac.id &&
                other.kind == CommitmentKind.TOWER_ARRIVAL &&
                (other.stage == TowerArrivalStage.AwaitApproach ||
                    other.stage == TowerArrivalStage.AwaitLandedObserved) &&
                run {
                    val otherAc = ctx.beliefs.trackedAircraft[otherId] ?: return@run false
                    if (otherAc.onGround) return@run false
                    val legs = ctx.worldIndex.circuitLegsByPoint[otherAc.position] ?: emptySet()
                    val onApproach = otherAc.entities.any { it is EntityRef.ApproachRef }
                    LegName.FINAL in legs || onApproach
                }
        }
    }
}

/**
 * Separation concern for this aircraft is above [threshold].
 *
 * Reads from [BeliefState.separationAssessments] (computed by the separation engine
 * in Phase A, early in the pipeline). True when any assessment involving this aircraft
 * has a concern level ≥ threshold. Used to gate sequencing interventions — e.g.,
 * ExtendDownwind fires when concern is INTERVENTION or higher.
 *
 * Replaces the Phase 5 `SpacingNotAdequate` guard with the Phase 6 comfort-gradient model.
 */
data class SeparationConcernAbove(
    val threshold: xyz.easiersaid.twr.controller.observe.SeparationConcern.Severity,
) : RuleGuard {
    override val failureMessage = "Separation concern below ${threshold.name}"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val assessments = ctx.beliefs.separationAssessments
        if (assessments.isEmpty()) return true // no assessments = assume concern (be conservative)
        return assessments.any { assessment ->
            (assessment.aircraft == ac.id || assessment.other == ac.id) &&
                assessment.concern.isSeverityAtLeast(threshold)
        }
    }
}

/** No active runway clearance for this aircraft. */
data object NoRunwayClearanceIssued : RuleGuard {
    override val failureMessage = "A runway clearance has already been issued for this aircraft"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.beliefs.issuedClearances.values.none { clr ->
            clr.aircraft == ac.id && !clr.status.isTerminal && clr.domain == ClearanceDomain.RUNWAY
        }
}
