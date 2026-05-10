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
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.IcaoTypeDesignator
import xyz.easiersaid.twr.protocol.RunwayId
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
    fun intentOf(aircraft: xyz.easiersaid.twr.protocol.AircraftId): xyz.easiersaid.twr.protocol.AircraftIntent =
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

/**
 * fn-8.3 Phase 2 (B2): sticky witness that the aircraft has been observed
 * on a runway entity AND on the ground at least once during the **current**
 * commitment lifetime. Reads [Commitment.touchedDownDuringCommitment] (set
 * by `reconcileObservedStages` in the controller). Pass-through guard —
 * the witness flag is the load-bearing state.
 *
 * Used to gate `ARR-TNG-AIRBORNE` so that airborne-only observations
 * cannot complete a touch-and-go arrival. Pre-fix, `ARR-TNG-AIRBORNE`
 * fired on bare `Airborne` and combined with `readbackAdvancesToStage =
 * AwaitLandedObserved` produced a runaway commitment ping-pong any time
 * `ARR-LAND-TNG` was issued at a non-runway pattern point — even if the
 * aircraft never reached the runway during this circuit. See fn-8.3 spec
 * § Evidence § Phase 1 + Phase 2 for the empirical loop trace.
 */
data object TouchedDownDuringCommitment : RuleGuard {
    override val failureMessage =
        "Aircraft has not been observed on the runway on-ground during this commitment"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        commitment.touchedDownDuringCommitment
}

/**
 * fn-8.3 Phase 2 (B3): sticky witness that the pilot has reported "Ready
 * for departure" at least once during the **current** commitment lifetime.
 * Reads [Commitment.pilotReadyDuringCommitment] (set by
 * `reconcileObservedStages` in the controller from the
 * `ReadyForDepartureReceived` controller event).
 *
 * Replaces the single-cycle [PilotReady] gate on `DEP-LUAW`. Pilots
 * report Ready once; the controller retains that on the strip. With
 * sequential departures behind a circuit-traffic arrival, the runway
 * is granted to the second departure long after the pilot's one-shot
 * Ready event has aged out of `ctx.events`. Pre-fix `DEP-LUAW` would
 * never fire for the second departure → wedge at AwaitReady. See fn-8.3
 * spec § Evidence § Phase 2 (post-B2-fix) for the empirical wedge.
 */
data object PilotReadyDuringCommitment : RuleGuard {
    override val failureMessage =
        "Pilot has not reported ready for departure during this commitment"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        commitment.pilotReadyDuringCommitment
}

/**
 * fn-8.3 Phase 4 (B5-α): the controller has observed the pilot reporting
 * a position-call during the **current** commitment lifetime that
 * matches at least one of [acceptedReports]. Reads
 * [Commitment.observedReportsDuringCommitment] (set by
 * `reconcileObservedStages` from `ControllerEvent.PositionReported`).
 *
 * The gate covers both VFR circuit-pattern position calls (Downwind /
 * Base / Final / LongFinal — CAP 413 §4.45-4.49) and instrument-approach
 * equivalents (Established / EstablishedLocaliser / EstablishedGlidepath
 * — ICAO Doc 4444 §7.10). The caller picks the set appropriate for the
 * gating rule; today the shared `LandingConditions` accepts any of those
 * because both VFR and instrument arrivals reach the same ARR-LAND rule.
 *
 * Used to gate `ARR-LAND` / `ARR-LAND-TNG` and their re-issue siblings.
 * Doctrine: landing clearance follows the pilot's position call. Pre-fix,
 * those rules fired on observed geometry + strip-derived
 * `IsCircuitTrafficByStrip` (C2/C3) without waiting for the pilot's
 * report; a stepped-on Downwind transmission led to the controller
 * clearing the aircraft to land before the pilot's position call had
 * been delivered (G1 B5 mechanism M1, fn-8.3 spec § Phase 3 round 2
 * evidence).
 *
 * **Failure-closed default**: empty witness set means the rule does NOT
 * fire — a future scenario where reports are mis-populated surfaces as
 * "ARR-LAND never fires" (live wedge, surfaces in tests) rather than
 * "ARR-LAND fires too eagerly" (dangerous silent regression).
 */
data class HasReportedPositionCall(
    val acceptedReports: Set<PositionReportKind>,
) : RuleGuard {
    override val failureMessage =
        "Pilot has not reported a qualifying position call " +
            "(${acceptedReports.joinToString(", ") { it.name }}) during this commitment"

    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        if (commitment.observedReportsDuringCommitment.isEmpty()) return false
        return commitment.observedReportsDuringCommitment.any { event -> matches(event) }
    }

    private fun matches(event: xyz.easiersaid.twr.protocol.ReportEvent): Boolean = when (event) {
        is xyz.easiersaid.twr.protocol.ReportEvent.Downwind -> PositionReportKind.DOWNWIND in acceptedReports
        is xyz.easiersaid.twr.protocol.ReportEvent.Base -> PositionReportKind.BASE in acceptedReports
        is xyz.easiersaid.twr.protocol.ReportEvent.Final -> PositionReportKind.FINAL in acceptedReports
        is xyz.easiersaid.twr.protocol.ReportEvent.LongFinal -> PositionReportKind.LONG_FINAL in acceptedReports
        is xyz.easiersaid.twr.protocol.ReportEvent.Established -> PositionReportKind.ESTABLISHED in acceptedReports
        is xyz.easiersaid.twr.protocol.ReportEvent.EstablishedLocaliser ->
            PositionReportKind.ESTABLISHED_LOCALISER in acceptedReports
        is xyz.easiersaid.twr.protocol.ReportEvent.EstablishedGlidepath ->
            PositionReportKind.ESTABLISHED_GLIDEPATH in acceptedReports
        // All other ReportEvent variants are observation reports but not
        // pre-clearance position calls in the CAP 413 / ICAO Doc 4444
        // sense. The exhaustive listing forces a decision when new
        // variants are added.
        is xyz.easiersaid.twr.protocol.ReportEvent.Airborne,
        is xyz.easiersaid.twr.protocol.ReportEvent.EstablishedInHold,
        is xyz.easiersaid.twr.protocol.ReportEvent.RunwayVacated,
        is xyz.easiersaid.twr.protocol.ReportEvent.Ready,
        is xyz.easiersaid.twr.protocol.ReportEvent.GoingAround,
        is xyz.easiersaid.twr.protocol.ReportEvent.VisualWithField,
        is xyz.easiersaid.twr.protocol.ReportEvent.TcasRa,
        is xyz.easiersaid.twr.protocol.ReportEvent.MinimumFuel,
        is xyz.easiersaid.twr.protocol.ReportEvent.PassingLevel,
        is xyz.easiersaid.twr.protocol.ReportEvent.LeavingLevel,
        is xyz.easiersaid.twr.protocol.ReportEvent.DistanceDme,
        is xyz.easiersaid.twr.protocol.ReportEvent.OverFix -> false
    }
}

/**
 * fn-8.3 Phase 4 (B5-α): typed accepted-report kind for
 * [HasReportedPositionCall]. Distinct from [LegName] (which is
 * geometric / world-graph topology) — these are protocol-level position
 * reports the pilot can transmit. The 1:1 mapping to
 * [xyz.easiersaid.twr.protocol.ReportEvent] is intentional; the guard's
 * `matches` arm is exhaustive on `ReportEvent`.
 */
enum class PositionReportKind {
    DOWNWIND, BASE, FINAL, LONG_FINAL,
    ESTABLISHED, ESTABLISHED_LOCALISER, ESTABLISHED_GLIDEPATH,
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
 * No pending (still-Issued) readback for an instruction matching [matcher].
 *
 * The idempotency pair for fire-and-forget rules — handoffs, position-info,
 * QNH updates — whose effect lands several ticks after the controller speaks.
 * The coordination ledger is the already-authoritative "what's in flight"
 * store: every outgoing [xyz.easiersaid.twr.controller.ControllerOutput.Instruct]
 * is appended there by `recordCoordinations` and either popped when the
 * readback arrives or escalated by `escalateOverdueCoordinations`.
 *
 * Pass 9 (D-AUDIT.2): the guard reads the `pendingReadbacks` projection,
 * which filters to entries still in [xyz.easiersaid.twr.controller.observe.CoordinationState.Issued].
 * An escalated coordination (Querying/Reissued/LostCommsDeclared) is *not*
 * blocking the rule — the escalation flow has taken over — so the rule
 * fires again, providing the "how copy?" retransmit cadence (CAP 413 §2.7)
 * via the lifecycle, not the silent GC.
 */
data class NoPendingReadback(val matcher: InstructionMatcher) : RuleGuard {
    override val failureMessage = "An instruction matching this matcher is already pending readback"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.beliefs.coordinations[ac.id].orEmpty()
            .none { it.state is xyz.easiersaid.twr.controller.observe.CoordinationState.Issued && matcher.matches(it.instruction) }
}

/**
 * fn-8.3 Phase 3 round 1 (codex review iteration 4): an instruction
 * matching [matcher] has been issued by the controller for this aircraft
 * at any point — fresh-Issued, in escalation (Querying / Reissued /
 * LostCommsDeclared), or recently terminal-but-not-yet-pruned.
 *
 * Use this guard for **disposition-locking** semantics: once the
 * controller has committed to a particular instruction (e.g.
 * `ClearedToLand` for a full-stop landing), downstream rules need to
 * stay aligned with that disposition even if the pilot's later radio
 * traffic would otherwise reclassify the aircraft.
 *
 * Concrete trigger that motivated this guard: a circuit-traffic
 * aircraft whose first-circuit Downwind was stepped on receives
 * `ClearedToLand` per the C4 default-flip ("clear-to-land when intent
 * unknown"). The pilot reads back, touches down. THEN the delayed
 * Downwind transmission delivers `CircuitIntent=TOUCH_AND_GO`. Without
 * this guard, `ARR-VACATE`'s gate
 * `AnyOf(CircuitIntentIs(FULL_STOP), Not(IsCircuitTraffic))` evaluates
 * false (intent is now T&G; aircraft IS circuit traffic), and the
 * aircraft wedges on the runway even though it was cleared to land.
 *
 * Reads `ctx.beliefs.coordinations[ac.id]` and matches on instruction
 * type. Symmetric to [NoPendingReadback] but state-agnostic.
 */
data class CoordinationIssued(val matcher: InstructionMatcher) : RuleGuard {
    override val failureMessage = "No coordination for the matching instruction has been issued"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.beliefs.coordinations[ac.id].orEmpty()
            .any { matcher.matches(it.instruction) }
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
    val intent: xyz.easiersaid.twr.protocol.AircraftIntent,
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
 * fn-8.3 Phase 3 (B4 closure): the aircraft is filed as a **VFR local
 * flight** — the strip carries no onward destination aerodrome. Real ATC
 * strips for circuit-training and other local flights are marked "VFR LCL"
 * (or equivalent kind-of-flight indicator) so the controller knows from
 * the AFTN-distributed strip — *before any radio contact* — that this
 * flight is not transiting anywhere else.
 *
 * Distinct from [IsCircuitTraffic] (which keys off the radio-derived
 * Downwind circuit-intent declaration). The two together cover:
 *  - **Strip-known local**: the controller has the filed plan in hand and
 *    knows this is a local flight before the pilot's first transmission.
 *  - **Radio-confirmed circuit**: the pilot has reported a Downwind with
 *    explicit T&G or full-stop intent.
 *
 * The pre-existing `IsCircuitTraffic` is the only signal currently fed to
 * `DEP-CIRCUIT-COMPLETE`'s gate, which causes a wedge when the Downwind
 * transmission is stepped on (multi-aircraft frequency contention) — the
 * controller never sees the radio-derived signal and the commitment never
 * advances out of `TOWER_DEPARTURE`. The strip-based fallback keeps
 * commitment-stage advancement robust to lost radio reports without
 * paving over the radio-side defect (cross-aircraft step-on stays as a
 * separately-tracked deferment).
 *
 * Reads [ControllerView.flightStripDestinations]; absence ↔ local flight
 * (the projection filters non-null on write). Doctrine: ICAO Annex 11
 * §4.3 (flight rules), AIP / AIC kind-of-flight markings (VFR LCL).
 */
data object IsCircuitTrafficByStrip : RuleGuard {
    override val failureMessage = "Aircraft strip carries an onward destination — not a local flight"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        // Tighten "no destination" to "has a strip AND no destination".
        // A controller without a strip for the aircraft has no doctrinal
        // grounds to call it local — guard fails closed.
        val hasStrip = ac.id in ctx.view.flightStripIntents
        val hasDestination = ac.id in ctx.view.flightStripDestinations
        return hasStrip && !hasDestination
    }
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

/**
 * G2 Phase H: aircraft's filed onward destination is a *different*
 * aerodrome than this controller's. Reads
 * [ControllerView.flightStripDestinations].
 *
 * Returns false (rule sleeps) when:
 *  - the aircraft has no filed onward destination on its strip
 *    (Arrival / CircuitTraining / no goal); OR
 *  - the filed destination equals this controller's aerodrome
 *    (degenerate case — local Arrival flow doesn't reach this guard).
 *
 * Used positively to gate `DEP-CROSS-AERODROME-RELEASE` (the rule
 * fires only for cross-aerodrome flights). Used negatively (via
 * `Not(...)`) on `DEP-HANDOFF` and `DEP-RADAR-SERVICE-TERMINATED` to
 * prevent local-traffic release rules from firing for cross-aerodrome
 * flights — closes the rule-ordering hazard where a transit aircraft
 * transiently riding the UPWIND/CROSSWIND geometry would otherwise
 * be peer-handed to APPROACH (impact M1 from plan-stage review).
 *
 * The doctrine: cross-aerodrome handoff is **release + procedure-
 * following + autonomous initial contact**, not peer handoff
 * (project memory `transmission_architecture`).
 */
data object DestinationDifferentAerodrome : RuleGuard {
    override val failureMessage =
        "Aircraft's filed onward destination is the same aerodrome as this controller, " +
            "or no destination on strip — rule applies only to cross-aerodrome traffic."

    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val destination = ctx.view.flightStripDestinations[ac.id] ?: return false
        return destination != ctx.view.aerodromeId
    }
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

/**
 * fn-12 (R5): the commitment's runway has been declared obstructed by the
 * world (per-cycle world-diff producer in sim folded into
 * [BeliefState.runwayObstructions]). Doctrinally distinct from
 * [RunwayPhysicallyClear] which reads `runwayBeliefs[runway].status` for
 * physical occupancy by another aircraft — `RunwayObstructed` is for typed
 * world-state declarations (vehicle, debris, wildlife, surface contamination,
 * etc., though v1 carries only an opaque [RunwayObstruction] with `clearsAt`).
 *
 * **Parameterless `data object`** mirroring [RunwayPhysicallyClear]'s shape.
 * Derives the runway from `commitment.runway ?: ctx.beliefs.activeRunway`.
 * Map-membership (`containsKey`) is the existence check.
 */
data object RunwayObstructed : RuleGuard {
    override val failureMessage = "Runway is declared obstructed"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway ?: return false
        return ctx.beliefs.runwayObstructions.containsKey(runway)
    }
}

/**
 * fn-12 (R7-no-refire): the controller has already issued an
 * obstruction-driven go-around for this aircraft on the **current**
 * approach attempt. Reads
 * [Commitment.obstructionGoAroundIssuedThisAttempt] (the
 * approach-attempt-scoped sticky witness, set in `advanceCommittedStages`
 * after arbitration + certification accepted the candidate output, NOT at
 * candidate-emit time).
 *
 * Used by `ARR-GO-AROUND-RUNWAY-OBSTRUCTED` to prevent re-firing on
 * subsequent rule-evaluation cycles while the obstruction persists. Stage
 * progression alone is insufficient: reconciliation may re-advance the
 * aircraft back through eligible stages. The witness is the no-refire
 * mechanism. Re-armed by the next `Report(Downwind)` arrival in
 * `reconcileTowerArrival` or by commitment replacement.
 */
data object ObstructionGoAroundAlreadyIssuedThisAttempt : RuleGuard {
    override val failureMessage = "Obstruction-driven GA has not been issued this approach attempt"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean =
        commitment.obstructionGoAroundIssuedThisAttempt
}

/**
 * fn-13.1 (R6): the controller has already issued an obstruction-driven
 * CONTINUE APPROACH for this aircraft on the **current** approach attempt.
 * Reads [Commitment.continueApproachIssuedThisAttempt] — the sticky witness
 * set in the new `applyCommittedOutputWitnesses` pass (post-arbitration +
 * certification) when `ARR-CONTINUE-APPROACH-OBSTRUCTION` survives.
 *
 * Used by `ARR-CONTINUE-APPROACH-OBSTRUCTION` to prevent re-firing on
 * subsequent rule-evaluation cycles while both the obstruction and the
 * clears-in-time predicate persist. The rule has `nextStage = null` (no
 * stage advancement), so stage progression alone CANNOT gate re-fire —
 * the witness is the only suppression mechanism. Re-armed on
 * `Report(Downwind)` in `reconcileTowerArrival` and on commitment
 * replacement (fresh `Commitment` takes the default `false`).
 *
 * Same lifecycle as [ObstructionGoAroundAlreadyIssuedThisAttempt] — both
 * witnesses re-arm on the same trigger.
 */
data object ContinueApproachAlreadyIssuedThisAttempt : RuleGuard {
    override val failureMessage = "Obstruction-driven CONTINUE APPROACH has not been issued this approach attempt"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean =
        commitment.continueApproachIssuedThisAttempt
}

/**
 * fn-13.1 (R1): safety margin added to obstruction-clears-in-time predicate
 * to absorb pilot-reaction-time variance + sensor-update-cycle latency.
 *
 * 10 seconds is a doctrinally-defensible buffer:
 *  - Pilot perception-action time on an unexpected runway clear ≈ 3-5s
 *  - Sim sensor cadence is 1 Hz; clearsAt may land mid-cycle (≈1s slack)
 *  - Threshold reaches ground-speed-derived ETA before clearsAt; the gap
 *    closes within the margin so the controller doesn't risk a borderline
 *    "clears just-in-time" CONTINUE APPROACH that GA-recovers anyway.
 *
 * Fail-closed direction: margin is ADDED to the gap (clearsAt - now), so
 * any missing input or marginal arithmetic biases the predicate toward
 * `false` → GA wins.
 */
const val OBSTRUCTION_CLEAR_SAFETY_MARGIN_S: Long = 10L

/** Derived ms form. Kept as a separate constant to avoid recomputation in the guard. */
const val OBSTRUCTION_CLEAR_SAFETY_MARGIN_MS: Long = OBSTRUCTION_CLEAR_SAFETY_MARGIN_S * 1000L

/**
 * fn-13.1 (R1): the runway obstruction (per
 * [BeliefState.runwayObstructions]) is expected to clear in time for a
 * safe landing — i.e. `(clearsAt - now) + safetyMargin <= eta-to-threshold`
 * (all in milliseconds). When this predicate holds, the new
 * `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule fires (delay landing clearance
 * via CONTINUE APPROACH per CAP 413 §4.55-4.56 / ICAO Doc 4444 §12.3.4.16);
 * when it does NOT hold, the `Not(ObstructionClearsInTime)` arm in
 * `obstructionGoAroundRuleAwaitApproach` lets the GA rule fire.
 *
 * **Inputs**:
 *  - `runway` from `commitment.runway ?: ctx.beliefs.activeRunway`
 *  - `obstruction` from `ctx.beliefs.runwayObstructions[runway]`
 *  - `groundSpeed` from `ac.groundSpeed` (nullable `Knots?`; `Knots.value`
 *    is constrained to `> 0` at construction, but null surfaces as
 *    fail-closed false)
 *  - aircraft kinematic position from `ac.coords` (continuous surveillance
 *    position) — **NOT** `worldIndex.positions[ac.position]` (the
 *    graph-snapped point can mislead the predicate by tens of metres,
 *    enough to flip the clears-in-time decision unsafely)
 *  - threshold point from `ctx.worldIndex.thresholdByRunway[runway]`,
 *    coordinate from `ctx.worldIndex.positions[thresholdPoint]`
 *
 * **Predicate** (in ms):
 *  ```
 *  groundSpeedMps = groundSpeed.value * 1852.0 / 3600.0   // knots → m/s
 *  etaMs          = (distanceToThresholdM / groundSpeedMps * 1000.0).toLong()
 *  (clearsAt.millis - ctx.time.millis) + OBSTRUCTION_CLEAR_SAFETY_MARGIN_MS <= etaMs
 *  ```
 *
 * **Fail-closed direction**: any missing input → `false` → GA wins. This is
 * the doctrinally conservative direction: a CONTINUE APPROACH issued onto
 * an obstruction that turns out NOT to clear ends in a late GA; a GA fired
 * onto an obstruction that DOES clear is recoverable in the next approach
 * attempt. The asymmetry is intentional.
 */
data object ObstructionClearsInTime : RuleGuard {
    override val failureMessage = "Runway obstruction will not clear in time for a safe landing"
    @Suppress("ReturnCount") // explicit fail-closed early returns; folding obscures the predicate.
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val runway = commitment.runway ?: ctx.beliefs.activeRunway ?: return false
        val obstruction = ctx.beliefs.runwayObstructions[runway] ?: return false
        val groundSpeed = ac.groundSpeed ?: return false
        // Knots.value is Int and constrained > 0 at construction — defensive
        // belt-and-suspenders for any future construction path that relaxes
        // the invariant.
        if (groundSpeed.value <= 0) return false
        val thresholdPoint = ctx.worldIndex.thresholdByRunway[runway] ?: return false
        val thrPos = ctx.worldIndex.positions[thresholdPoint] ?: return false
        // Use `ac.coords` (continuous surveillance position), NOT
        // `worldIndex.positions[ac.position]` (graph-snapped). Snap error
        // of even tens of metres could flip the predicate unsafely.
        val dx = ac.coords.xMeters - thrPos.xMeters
        val dy = ac.coords.yMeters - thrPos.yMeters
        val distanceM = kotlin.math.sqrt(dx * dx + dy * dy)
        if (!distanceM.isFinite() || distanceM < 0.0) return false
        val groundSpeedMps = groundSpeed.value.toDouble() * 1852.0 / 3600.0
        // groundSpeed.value > 0 by construction + the check above, so this
        // division cannot produce NaN/Infinity unless distanceM is non-finite
        // (already screened) — defensive.
        val etaSeconds = distanceM / groundSpeedMps
        if (!etaSeconds.isFinite() || etaSeconds < 0.0) return false
        val etaMs = (etaSeconds * 1000.0).toLong()
        val gapMs = obstruction.clearsAt.millis - ctx.time.millis + OBSTRUCTION_CLEAR_SAFETY_MARGIN_MS
        return gapMs <= etaMs
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
 * Reads the aircraft's kinematic position ([AircraftObservation.coords], set
 * by `AircraftObservationFactory.from` from sim-side
 * `AircraftState.position` via [SensorReading.coords]); the radius gate fires
 * when the aircraft physically crosses the configured ring. The earlier
 * snap-point read (`worldIndex.positions[ac.position]`) was off by half-snap
 * -distance in the worst case, leaving cross-aerodrome release events
 * bunched against the destination's first published REP — fn-5's R4 gap pin
 * had to be relaxed from `>= 30s` to `> 0` to accommodate that bunching.
 * fn-6 restores the doctrinal physical-ring semantics; fn-6.3 tightens the
 * gap pin back.
 *
 * Real CTR boundaries are typed polygons (FM/Lean campaign territory, fn-4
 * lineage); the circular-radius approximation is intentional pending that
 * work. **`D-AUDIT-polygon-ctr`** owns the polygon-membership upgrade.
 * Today the radius is anisotropic-wrong: short on the approach axis,
 * generous abeam. Per-aerodrome authoring from AIP AD 2.17 polygon data
 * (rounded up, with proxy-offset margin) under fn-7 closes the
 * one-radius-fits-all rot at LOWG; LJMB still uses a conservative
 * placeholder pending real-polygon transcription (`D-AUDIT-ljmb-polygon`).
 *
 * fn-7: rule shape is `data object` — the per-aerodrome radius lives on
 * [Aerodrome.ctrApproximationRadius] and is read at evaluate time. Rule
 * equality changes from `data class` content equality to singleton
 * identity; consumers look up rules by class, not value, so this is a
 * runtime-no-op. Failure message is static (no longer interpolates a
 * removed constructor field); per-aerodrome variance is no longer a
 * concern at the rule level.
 *
 * The defensive `return false` paths preserve the "do not release on
 * unresolvable ARP" semantics — the aerodrome lookup or its proxy ARP
 * point may be missing in malformed worlds.
 */
data object OutsideAerodromeRadius : RuleGuard {
    override val failureMessage = "Aircraft within aerodrome CTR approximation radius (still in CTR scope)"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean {
        val aerodrome = ctx.world.aerodromes[ctx.view.aerodromeId] ?: return false
        // Use the lexicographically-first runway's threshold as a stand-in
        // for the aerodrome reference point. Real ARPs come with the airport
        // manifest under a separate `referencePoint` field; today the field
        // exists on Aerodrome but isn't populated for every aerodrome (some
        // are null until `D-AUDIT-polygon-ctr`'s CTR-polygon work). The
        // threshold is a reasonable proxy at small fields, with the
        // proxy-offset budget folded into the per-aerodrome radius
        // authoring (`D-AUDIT-arp-proxy-runtime` tracks the runtime ARP).
        //
        // Pass 7 post-impl Impact-M.2: sort by `RunwayId.value` before
        // taking the first to make the proxy stable against manifest edits
        // (a new runway added at the head of the manifest would otherwise
        // shift the proxy point). Threshold offsets between runways at
        // multi-runway airports (e.g. LOWG 16C/16L/16R/28) can be hundreds
        // of metres — small relative to the per-aerodrome radius but not
        // negligible; a stable proxy is required for deterministic-replay.
        val arpPointId = aerodrome.runways.entries
            .sortedBy { it.key.value }
            .firstOrNull()?.value?.threshold ?: return false
        val arpPos = ctx.worldIndex.positions[arpPointId] ?: return false
        // fn-6.2 (R3): kinematic read. ac.coords is the primary-surveillance
        // projection of AircraftState.position (continuous Cartesian), not
        // the snap-derived `positionPoint`. Compare against the ARP proxy
        // in the same metric space.
        // fn-7: per-aerodrome radius — read from world data.
        val dx = ac.coords.xMeters - arpPos.xMeters
        val dy = ac.coords.yMeters - arpPos.yMeters
        val limit = aerodrome.ctrApproximationRadius.value
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

/**
 * The runway named on [Commitment.runway] has declared distances long
 * enough for the aircraft's type to perform [operation].
 *
 * Pass 13 (D-AUDIT.4.A-FOLLOWUP closure): consumes
 * [xyz.easiersaid.twr.protocol.AircraftType.RunwayLengthRequirements]
 * data Pass 10 carried but did not yet gate on. Reads through the
 * firewall-narrow lookup
 * [xyz.easiersaid.twr.protocol.AircraftType.runwayRequirementsFor] —
 * the controller never sees the full [AircraftType] (no kinematics or
 * circuit data); only the runway-relevant slice.
 *
 * **Fail-closed semantics** (no-corners rule):
 *  - Unknown ICAO type designator → guard rejects. The pilot's strip
 *    must carry a [xyz.easiersaid.twr.protocol.IcaoTypeDesignator] for
 *    which `runwayRequirementsFor` returns a Right; absent the strip
 *    or unknown type, the rule cannot guarantee runway adequacy and
 *    refuses to fire.
 *  - Null `declaredDistances` → guard rejects. The migration schema
 *    (`CandidateDeclaredDistances` is non-nullable in
 *    `WorldCandidateSchema.kt`) ensures all loaded worlds carry
 *    distances; a null path is reachable only from in-memory test
 *    fixtures, where fail-closed is still correct (test must populate).
 *  - Unknown runway → guard rejects. The commitment carries the runway
 *    selected by upstream rules; absence indicates an upstream defect.
 *
 * **Diagnostic surface** (Pass 13 post-impl FP review S.2):
 * `runwayRequirementsFor` returns
 * `Either<UnknownDesignator, RunwayLengthRequirements>`, but this
 * `evaluate` function collapses the `Left` to `null` via `getOrNull()`
 * because [RuleGuard.failureMessage] is statically typed (not a
 * function of the failing observation). The Either is therefore
 * load-bearing only for the *contract*, not the runtime trace today —
 * a future caller (e.g., a `RunwayLengthDiagnosticAction` that emits
 * a controller response naming the unknown designator) would
 * pattern-match on the Either rather than collapsing. **D-PASS-13.3**
 * tracks the diagnostic-enrichment work (rule-trace surface for the
 * specific fail-closed cause: unknown type / null distances / runway
 * absent / runway too short).
 *
 * Runway-condition adjustments (wet, contaminated, displaced threshold)
 * are filed as **D-AUDIT.4.A.II-FOLLOWUP**; Pass 13 uses dry/MTOW.
 */
data class RunwayLengthSufficient(
    val operation: RunwayLengthOperation,
) : RuleGuard {
    override val failureMessage: String =
        "Runway too short or runway-length data unavailable for $operation"

    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext): Boolean =
        classify(ac, commitment, ctx) == null

    /**
     * Pass 17 (D-PASS-13.3 partial closure): typed diagnostic surface.
     * Returns null on pass; one of the [RunwayLengthFailure] leaves on
     * fail. Test-visible for typed assertions; rule-trace integration
     * is the narrowed remaining work (D-PASS-13.3-II-FOLLOWUP).
     *
     * Pass 17 (D-PASS-13.1 closure): aerodrome-scoped runway lookup.
     * Pre-Pass-17 walked all aerodromes via `firstNotNullOfOrNull`,
     * which would silently match the wrong runway when a multi-aerodrome
     * `RunwayId` collision (e.g. `16C` at LOWG and at LJMB) lands.
     * Now scoped to `ctx.view.aerodromeId` — matches `OutsideAerodromeRadius`
     * (Pass 7) precedent.
     */
    internal fun classify(
        ac: AircraftObservation,
        commitment: Commitment,
        ctx: OperatorContext,
    ): RunwayLengthFailure? =
        when (val designator = ac.icaoTypeDesignator) {
            null -> RunwayLengthFailure.NullAircraftTypeDesignator
            else -> classifyKnownDesignator(designator, commitment, ctx)
        }

    private fun classifyKnownDesignator(
        designator: IcaoTypeDesignator,
        commitment: Commitment,
        ctx: OperatorContext,
    ): RunwayLengthFailure? {
        val requirements = AircraftType
            .runwayRequirementsFor(designator)
            .getOrNull()
        val runwayId = commitment.runway
        return when {
            requirements == null -> RunwayLengthFailure.UnknownDesignator(designator)
            runwayId == null -> RunwayLengthFailure.NullCommitmentRunway
            else -> classifyKnownRunway(designator, requirements, runwayId, ctx)
        }
    }

    private fun classifyKnownRunway(
        designator: IcaoTypeDesignator,
        requirements: AircraftType.RunwayLengthRequirements,
        runwayId: RunwayId,
        ctx: OperatorContext,
    ): RunwayLengthFailure? {
        // Aerodrome-scoped lookup (Pass 17 D-PASS-13.1).
        val runway = ctx.world.aerodromes[ctx.view.aerodromeId]?.runways?.get(runwayId)
        val distances = runway?.declaredDistances
        return when {
            runway == null -> RunwayLengthFailure.RunwayNotInWorld(runwayId)
            distances == null -> RunwayLengthFailure.NullDeclaredDistances(runwayId)
            else -> classifyDeclaredDistances(designator, requirements, runwayId, distances)
        }
    }

    private fun classifyDeclaredDistances(
        designator: IcaoTypeDesignator,
        requirements: AircraftType.RunwayLengthRequirements,
        runwayId: RunwayId,
        distances: xyz.easiersaid.twr.core.world.DeclaredDistances,
    ): RunwayLengthFailure? {
        val (availableM, requiredM) = when (operation) {
            RunwayLengthOperation.TAKEOFF -> distances.toda.value to requirements.takeoffMinM
            RunwayLengthOperation.LANDING -> distances.lda.value to requirements.landingMinM
        }
        return if (availableM >= requiredM) null else RunwayLengthFailure.RunwayTooShort(
            operation = operation,
            designator = designator,
            runway = runwayId,
            requiredM = requiredM,
            availableM = availableM,
        )
    }
}

/**
 * Sealed typed-failure surface for [RunwayLengthSufficient.classify].
 * Pass 17 (D-PASS-13.3 partial closure): replaces the static-text
 * failure message with structured information. Trace-render
 * integration (surface in `DecisionTrace.skippedActions`) is the
 * narrowed remaining work (D-PASS-13.3-II-FOLLOWUP).
 *
 * **`RunwayTooShort` carries `operation`** (Pass 17 review fold-in
 * Impact M1 / FP S1) so a reader can disambiguate TODA-fail (TAKEOFF)
 * from LDA-fail (LANDING) at the type level.
 */
sealed interface RunwayLengthFailure {
    /** Pilot's strip carries no [IcaoTypeDesignator] — VFR without filed type. */
    data object NullAircraftTypeDesignator : RunwayLengthFailure

    /** ICAO designator does not match any [AircraftType] in the catalogue. */
    data class UnknownDesignator(
        val designator: xyz.easiersaid.twr.protocol.IcaoTypeDesignator,
    ) : RunwayLengthFailure

    /** Commitment carries no runway — upstream wiring defect. */
    data object NullCommitmentRunway : RunwayLengthFailure

    /** Runway named on commitment is not in the controller's aerodrome. */
    data class RunwayNotInWorld(val runway: xyz.easiersaid.twr.protocol.RunwayId) : RunwayLengthFailure

    /** Runway has no declared distances (in-memory test fixture path). */
    data class NullDeclaredDistances(val runway: xyz.easiersaid.twr.protocol.RunwayId) : RunwayLengthFailure

    /** Runway is published but length insufficient for the type's [operation]. */
    data class RunwayTooShort(
        val operation: RunwayLengthOperation,
        val designator: xyz.easiersaid.twr.protocol.IcaoTypeDesignator,
        val runway: xyz.easiersaid.twr.protocol.RunwayId,
        val requiredM: Int,
        val availableM: Double,
    ) : RunwayLengthFailure
}

/**
 * Which declared-distance applies to a [RunwayLengthSufficient] check.
 * TAKEOFF reads TODA (takeoff distance available); LANDING reads LDA
 * (landing distance available). ASDA / TORA gating belong to abort and
 * one-engine-inoperative cases that are out of scope for Pass 13.
 */
enum class RunwayLengthOperation { TAKEOFF, LANDING }

/** No active runway clearance for this aircraft. */
data object NoRunwayClearanceIssued : RuleGuard {
    override val failureMessage = "A runway clearance has already been issued for this aircraft"
    override fun evaluate(ac: AircraftObservation, commitment: Commitment, ctx: OperatorContext) =
        ctx.beliefs.issuedClearances.values.none { clr ->
            clr.aircraft == ac.id && !clr.status.isTerminal && clr.domain == ClearanceDomain.RUNWAY
        }
}
