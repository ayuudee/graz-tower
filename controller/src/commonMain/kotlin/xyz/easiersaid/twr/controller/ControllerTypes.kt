package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.protocol.*

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
)

/** What the controller knows about one aircraft. */
data class AircraftObservation(
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
    val flightRules: FlightRules?,
    val pilotGoal: PilotGoal?,
    val humanPiloted: Boolean,
    val typeDescription: String? = null,
    /** ICAO wake turbulence category. Null = unknown; separation engine defaults to H (worst-case). */
    val wakeCategory: WakeCategory? = null,
)

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
 * Observed weather at a single aerodrome.
 *
 * The [wind] field is intentionally nullable — it represents "no wind report
 * available." When wind is null, [selectRunwayIntoWind] returns null and
 * downstream controller logic that needs an active runway must defer.
 * Tightening this to a typed `Either<NoWindReport, Wind>` is tracked as
 * G1-DEF-7 in `docs/design/g1-cross-aerodrome-vfr-outbound-2026-04-24.md`,
 * scheduled as a pre-G1.6 must-fix.
 */
data class WeatherObservation(val wind: Wind?, val qnh: PressureSetting?, val visibility: Int?)

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

    /** Initiate a handoff. */
    data class InitiateHandoff(
        val aircraft: AircraftId,
        val to: ControllerId,
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
