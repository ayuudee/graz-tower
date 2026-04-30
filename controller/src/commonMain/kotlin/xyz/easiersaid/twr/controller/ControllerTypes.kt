package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ConditionalPredicate
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.ControllerResponse
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
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
    val pendingInboundHandoffs: List<PendingHandoff>,
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
    val flightStripIntents: Map<AircraftId, xyz.easiersaid.twr.controller.observe.AircraftIntent> = emptyMap(),
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
 */
data class AircraftObservation internal constructor(
    val id: AircraftId,
    val callsign: Callsign,
    val position: PointId,
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

data class PendingHandoff(val aircraft: AircraftId, val from: ControllerId)

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
    data class Instruct(
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
    ) : ControllerOutput {
        val instruction: AtcInstruction get() = dispatch.instruction
        val condition: ConditionalPredicate?
            get() = (dispatch as? Dispatch.Conditional)?.condition
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
