package xyz.easiersaid.twr.controller

import arrow.core.NonEmptyList
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.certify.CertificationEvidence
import xyz.easiersaid.twr.controller.certify.CertifiedInstruction
import xyz.easiersaid.twr.controller.certify.NoCertificationRequired
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.BreakOff
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ConditionalPredicate
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.ControllerResponse
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.NumberInSequence
import xyz.easiersaid.twr.protocol.ObligationId
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.RegulationRef
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.Urgency
import xyz.easiersaid.twr.protocol.WakeCategory
import xyz.easiersaid.twr.protocol.Wind

/** Immutable snapshot provided to the controller each decision cycle. */
data class ControllerView(
    val time: SimTime,
    val controllerId: ControllerId,
    val role: RoleName,
    val aerodromeId: AerodromeId,
    val responsibilities: Set<AircraftId>,
    val aircraft: Map<AircraftId, AircraftObservation>,
    val runways: Map<RunwayId, RunwayObservation>,
    val activeClearances: Map<ClearanceId, ClearanceSummary>,
    val receivedMessages: List<ReceivedMessage>,
    val weather: WeatherObservation?,
    val worldIndex: WorldIndex,
    /**
     * Low Visibility Procedures active. Derived from visibility < 550m or RVR < 550m.
     * When true: no conditional clearances, one-on-runway, time-based wake, no visual sep.
     */
    val lvpMode: Boolean = false,
    /**
     * Flight-strip pre-briefing for aircraft scheduled to arrive on this
     * controller's frequency. Modelled as a typed [AircraftIntent]
     * (Departing/Arriving/Transit) — the controller's pre-flight summary,
     * not the pilot's mind. Belief-update fold seeds
     * [xyz.easiersaid.twr.controller.observe.BeliefState.aircraftIntent]
     * from this map; once an aircraft makes radio contact the radio
     * intentions override the strip.
     *
     * Empty when the controller has no pre-briefing (e.g. an unscheduled
     * aircraft entering the frequency cold).
     */
    val flightStripIntents: Map<AircraftId, xyz.easiersaid.twr.protocol.AircraftIntent> = emptyMap(),
    /**
     * G2 Phase H: per-aircraft *filed onward destination*. Sourced from
     * the strip's `destinationAerodrome` field, filtered to non-null
     * entries (aircraft with no onward leg are absent from the map).
     *
     * The doctrine: cross-aerodrome handoff is **release + procedure-
     * following + autonomous initial contact**, not peer handoff.
     * `DestinationDifferentAerodrome` reads this map to gate
     * `DEP-CROSS-AERODROME-RELEASE` (and to `Not(...)`-gate the local-
     * traffic siblings `DEP-HANDOFF` / `DEP-RADAR-SERVICE-TERMINATED` so
     * they don't fire for cross-aerodrome flights).
     *
     * Filter-on-write follows the established `flightStripIntents` /
     * `circuitIntent` precedent — guards check key presence + value, not
     * key presence + nullable value.
     */
    val flightStripDestinations: Map<AircraftId, AerodromeId> = emptyMap(),
    /**
     * Roles staffed *at this aerodrome right now* — i.e. for which a
     * controller is online and accepting handoffs.
     *
     * Pass 6: distinguishes "the aerodrome publishes role X" (authoritative
     * fact, on `aerodrome.roles`) from "role X has a controller working
     * today" (operational reality, depends on staffing). A handoff that
     * targets an unstaffed role is invalid; [HandoffAction] gates on this
     * set before emitting `ContactFrequency`.
     *
     * Empty when the controller has no peers (e.g. AFIS-only single-role
     * aerodrome).
     */
    val staffedRoles: Set<RoleName> = emptySet(),
    /**
     * Pass 12 (D-PF.9): aircraft for whom this controller has issued a
     * peer handoff that has been missed (no two-way comms with the target
     * within `MISSED_HANDOFF_TIMEOUT`). Single-producer projection from
     * sim's `handoffEscalations` filtered by `sender == this controller`.
     *
     * Empty when no missed handoffs are pending. Each value carries the
     * `since` timestamp of the most recent escalation event so the
     * controller's reactive rule can dampen re-emission per cycle.
     *
     * **Cycle latency**: a fresh sim escalation in cycle N is visible to
     * the controller in cycle N+1 (the sweep writes after the cycle's
     * decide pass). Acceptable for the 120 s timeout.
     */
    val outgoingMissedHandoffs: Map<AircraftId, MissedHandoffNotice> = emptyMap(),
    /**
     * Pass 15 (D-AUDIT.8 closure): per-aerodrome ATIS broadcast view.
     * Projected from `state.atisByAerodrome`. The controller's view
     * carries every aerodrome's ATIS — relevant for advisories
     * involving inbound traffic from peer aerodromes (e.g. "current
     * information at LJMB is Charlie"). The single-aerodrome
     * controller most often reads `atis[aerodromeId]` for own-airport
     * weather and runway-in-use.
     *
     * **Doctrine**: ICAO Annex 11 §4.3 (ATIS service).
     */
    val atis: Map<AerodromeId, xyz.easiersaid.twr.protocol.Atis> = emptyMap(),
    /**
     * fn-12 (R3c): world-state-derived events for this controller's
     * aerodrome, populated by the sim's per-cycle world-diff producer at
     * `sim/.../ControllerWiring.kt`. Currently carries
     * [xyz.easiersaid.twr.controller.observe.ControllerEvent.RunwayObstructionDetected]
     * /
     * [xyz.easiersaid.twr.controller.observe.ControllerEvent.RunwayObstructionCleared]
     * — the first world-state-derived sensing channel in the codebase
     * (foundational for future surface-incursion / FOD / wildlife /
     * leader-not-vacated scenarios).
     *
     * **Per-controller scoping invariant**: every event in this list
     * references only `RunwayId`s within `aerodromeId`'s runway set; no
     * AerodromeId payload qualification is needed on the event leaves.
     * Cross-aerodrome routing is filed as
     * `D-PASS-g3a-obstruction-aerodrome-payload`.
     *
     * Concatenated with `deriveEventsFromMessages(receivedMessages)` at
     * `Controller.kt`'s event-assembly site. Default-empty preserves all
     * existing call sites and keeps existing G0-G3a goldens GREEN
     * (vacuously empty list folds to identity). Added as the final
     * constructor parameter to avoid positional-arg call-site churn.
     */
    val worldEvents: List<xyz.easiersaid.twr.controller.observe.ControllerEvent> = emptyList(),
)

/**
 * Pass 12 (D-PF.9): strip-shaped notice that an outgoing peer handoff
 * has been missed. Single-source projection from sim's
 * `handoffEscalations`; the `FirewallOutgoingMissedHandoffsProjectionTest`
 * pins both single-producer and field-shape contracts.
 *
 * Carries the minimum the controller's reactive rule needs to re-issue
 * `ContactFrequency`: the target role + frequency the original handoff
 * named, plus the `since` timestamp the dampening uses.
 */
data class MissedHandoffNotice(
    val targetRole: RoleName,
    val targetFrequency: xyz.easiersaid.twr.protocol.Frequency,
    val since: SimTime,
)

/**
 * What the controller knows about one aircraft from sensor + radio observation.
 *
 * **Firewall invariant:** every field here originates from radar / surface
 * radar / visual / radio. There are no fields representing the pilot's
 * internal state (mission tree, intended next action, "is this an AI or a
 * human in the cockpit?"). Anything the controller knows about service
 * intent (Departing/Arriving/Transit) or per-circuit decisions
 * (TouchAndGo/FullStop) lives on
 * [xyz.easiersaid.twr.controller.observe.BeliefState], populated by typed
 * [xyz.easiersaid.twr.controller.observe.ControllerEvent]s derived from
 * [ReceivedMessage]s, and seeded from
 * [xyz.easiersaid.twr.controller.ControllerView.flightStripIntents] for
 * the broad pre-briefing channel.
 *
 * Adding a new field here requires it to map to a real-world sensor or
 * visual cue. The architectural test
 * `controller/src/commonTest/.../FirewallObservationTest.kt` enforces
 * this — adding a non-sensor field fails to compile against the test's
 * canonical-constructor allowlist.
 *
 * **Position vs. coords (fn-6.1).** [coords] carries the kinematic position
 * from primary surveillance (per ICAO Annex 11 §6 / Doc 4444 §8 —
 * surveillance returns are positional); [position] carries the same return
 * projected onto the published-fix graph for chart-anchored consumers
 * (route-progress, entity membership). Two fields because airspace-boundary
 * semantics need geometry and graph-progress semantics need fix identity.
 * A future tightening (`D-PASS-fn6-snap-derived`) derives one from the
 * other; today both are free arguments and the
 * `AircraftObservation.fromTestPoint(...)` test helper structurally prevents
 * fixture-level divergence.
 */
data class AircraftObservation internal constructor(
    val id: AircraftId,
    val callsign: Callsign,
    val position: PointId,
    /**
     * Kinematic position from primary surveillance. See the type-level KDoc
     * "Position vs. coords" paragraph for the full disambiguation against
     * [position]. Threaded from [xyz.easiersaid.twr.sim.SensorReading.coords]
     * via the [from] factory; not derived from [position].
     */
    val coords: Position,
    val entities: Set<EntityRef>,
    val altitude: Level?,
    val speed: Speed?,
    /** Aircraft magnetic heading, if observable. Required for sequence ETA derivation. */
    val heading: Heading? = null,
    /** Ground speed in knots, if observable. Required for sequence spacing calculation. */
    val groundSpeed: Knots? = null,
    val onGround: Boolean,
    /** ICAO wake turbulence category. Null = unknown; separation engine defaults to H (worst-case). */
    val wakeCategory: WakeCategory? = null,
    /**
     * ICAO Doc 8643 type designator (Pass 10 D-AUDIT.4). Populated from
     * the controller's strip via
     * [xyz.easiersaid.twr.sim.FlightStrip.icaoTypeDesignator]. Null when
     * the strip carries no type (VFR without filed plan).
     */
    val icaoTypeDesignator: xyz.easiersaid.twr.protocol.IcaoTypeDesignator? = null,
) {
    companion object
}

enum class FlightRules { VFR, IFR }

enum class PilotGoal { DEPART, ARRIVE, TOUCH_AND_GO, TRANSIT }

data class RunwayObservation(
    val id: RunwayId,
    val status: RunwayStatus,
    val occupants: Set<AircraftId>,
)

enum class RunwayStatus {
    CLEAR, OCCUPIED_DEPARTURE, OCCUPIED_LANDING, OCCUPIED_CROSSING, OCCUPIED_BACKTRACK,
}

data class ClearanceSummary(
    val id: ClearanceId,
    val aircraft: AircraftId,
    val domain: ClearanceDomain,
    val status: ClearanceStatus,
    val instruction: AtcInstruction,
    val issuedAt: SimTime,
)

/**
 * Sealed wind-report state. Replaces the earlier `Wind?` field on
 * [WeatherObservation] so consumers must explicitly handle the
 * "no wind report yet" case rather than treating null as a silent
 * fallback. Resolves G1-DEF-7 (pre-G1.6 must-fix).
 */
sealed interface WindReport {
    /** A current wind report is available. */
    data class Available(val wind: Wind) : WindReport

    /**
     * No wind report has been received yet — typically before the first
     * METAR cycle, or in the controller's belief state when the weather
     * observation hasn't been refreshed. Downstream selection logic
     * (e.g. [selectRunwayIntoWind]) returns null/no-decision rather
     * than picking a default.
     */
    data object NotReported : WindReport
}

/**
 * Observed weather at a single aerodrome. The [wind] field is a sealed
 * [WindReport] (not nullable) so every consumer must handle the
 * "no report" case explicitly.
 */
data class WeatherObservation(val wind: WindReport, val qnh: PressureSetting?, val visibility: Int?)

/** Channel-resolved pilot message — what was actually heard. Wraps PilotTransmission. */
sealed interface ReceivedMessage {
    val aircraft: AircraftId
    val transmission: PilotTransmission

    data class Clear(
        override val aircraft: AircraftId,
        override val transmission: PilotTransmission,
    ) : ReceivedMessage
}

// ─── Controller output ───────────────────────────────────────────────

sealed interface ControllerOutput {
    /** Issue an instruction. The caller creates the clearance and manages the lifecycle. */
    class Instruct private constructor(
        val target: AircraftId,
        val dispatch: Dispatch,
        val obligation: ObligationId? = null,
        val urgency: Urgency,
        val trace: DecisionTrace,
        /** Stage to advance to immediately when the rule fires. Null = no stage change. */
        val advanceToStage: xyz.easiersaid.twr.controller.bdi.Stage? = null,
        /** Whether this instruction's stage advancement is gated on readback confirmation. */
        val advancementPolicy: xyz.easiersaid.twr.controller.observe.AdvancementPolicy =
            xyz.easiersaid.twr.controller.observe.AdvancementPolicy.Immediate,
        /** Stage to advance to when readback is confirmed. Recorded on the coordination. */
        val readbackAdvancesToStage: xyz.easiersaid.twr.controller.bdi.Stage? = null,
        val certificationEvidence: NonEmptyList<CertificationEvidence>,
    ) : ControllerOutput {
        val instruction: AtcInstruction get() = dispatch.instruction
        val condition: ConditionalPredicate?
            get() = (dispatch as? Dispatch.Conditional)?.condition

        companion object {
            fun fromCertified(
                certified: CertifiedInstruction,
                obligation: ObligationId? = null,
                urgency: Urgency,
                trace: DecisionTrace,
                advanceToStage: xyz.easiersaid.twr.controller.bdi.Stage? = null,
                advancementPolicy: xyz.easiersaid.twr.controller.observe.AdvancementPolicy =
                    xyz.easiersaid.twr.controller.observe.AdvancementPolicy.Immediate,
                readbackAdvancesToStage: xyz.easiersaid.twr.controller.bdi.Stage? = null,
            ): Instruct = Instruct(
                target = certified.aircraft,
                dispatch = certified.dispatch,
                obligation = obligation,
                urgency = urgency,
                trace = trace,
                advanceToStage = advanceToStage,
                advancementPolicy = advancementPolicy,
                readbackAdvancesToStage = readbackAdvancesToStage,
                certificationEvidence = certified.evidence,
            )

            fun fromAdministrative(
                instruction: NumberInSequence,
                urgency: Urgency,
                trace: DecisionTrace,
            ): Instruct = Instruct(
                target = instruction.target,
                dispatch = Dispatch.Direct(instruction),
                urgency = urgency,
                trace = trace,
                certificationEvidence = NonEmptyList(
                    CertificationEvidence.NotRequired(NoCertificationRequired.AdministrativeSequencing),
                    emptyList(),
                ),
            )

            fun fromMissedHandoffReissue(
                instruction: ContactFrequency,
                obligation: ObligationId? = null,
                urgency: Urgency,
                trace: DecisionTrace,
            ): Instruct = Instruct(
                target = instruction.target,
                dispatch = Dispatch.Direct(instruction),
                obligation = obligation,
                urgency = urgency,
                trace = trace,
                certificationEvidence = NonEmptyList(
                    CertificationEvidence.RuntimeChecked(
                        checkId = "missed-handoff-reissue",
                        summary = "ContactFrequency reissued from missed handoff projection",
                    ),
                    emptyList(),
                ),
            )

            fun fromCoordinationReissue(
                coordination: OutstandingCoordination,
                urgency: Urgency,
                trace: DecisionTrace,
            ): Instruct = Instruct(
                target = coordination.aircraft,
                dispatch = coordination.dispatch,
                urgency = urgency,
                trace = trace,
                certificationEvidence = coordination.certificationEvidence,
            )

            fun fromReactiveSeparationEmergency(
                instruction: GoAround,
                urgency: Urgency,
                trace: DecisionTrace,
                doctrine: String,
            ): Instruct = fromReactiveSeparationEmergency(
                target = instruction.target,
                dispatch = Dispatch.Direct(instruction),
                urgency = urgency,
                trace = trace,
                doctrine = doctrine,
            )

            fun fromReactiveSeparationEmergency(
                instruction: BreakOff,
                urgency: Urgency,
                trace: DecisionTrace,
                doctrine: String,
            ): Instruct = fromReactiveSeparationEmergency(
                target = instruction.target,
                dispatch = Dispatch.Direct(instruction),
                urgency = urgency,
                trace = trace,
                doctrine = doctrine,
            )

            private fun fromReactiveSeparationEmergency(
                target: AircraftId,
                dispatch: Dispatch.Direct,
                urgency: Urgency,
                trace: DecisionTrace,
                doctrine: String,
            ): Instruct = Instruct(
                target = target,
                dispatch = dispatch,
                urgency = urgency,
                trace = trace,
                certificationEvidence = NonEmptyList(CertificationEvidence.EmergencyPolicy(doctrine), emptyList()),
            )
        }
    }

    /** Send a response (readback correct, station callback, etc.). Not a clearance. */
    data class Respond(
        val target: AircraftId,
        val response: ControllerResponse,
        val trace: DecisionTrace,
    ) : ControllerOutput
}

data class DecisionTrace(
    val ruleId: String,
    val description: String,
    val regulations: List<RegulationRef>,
    val obligationsFulfilled: List<ObligationId> = emptyList(),
)

data class ControllerDecisionResult(
    val outputs: List<ControllerOutput>,
    val updatedBeliefs: BeliefState,
    val trace: OverallDecisionTrace,
)

data class OverallDecisionTrace(
    val controllerId: ControllerId,
    val time: SimTime,
    val obligationsActive: List<ObligationId> = emptyList(),
    val actionsConsidered: Int = 0,
    val actionsCommitted: Int = 0,
    val skippedActions: List<SkippedAction> = emptyList(),
)

data class SkippedAction(
    val aircraft: AircraftId,
    val reason: String,
    val ruleTraces: List<String> = emptyList(),
)
