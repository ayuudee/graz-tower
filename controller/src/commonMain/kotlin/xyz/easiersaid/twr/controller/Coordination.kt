package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.assess.ArrivalSequence
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ApproachType
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Speed

/**
 * Controller-to-controller coordination messages (OLDI-style).
 *
 * Distinct from pilot-facing transmissions. These are state deltas between
 * APP and TWR (or any other adjacent ATC unit). Each controller instance
 * receives the other's coordination messages as [ReceivedMessage]-like
 * inputs on the next cycle. Shared state = zero.
 *
 * Transfer of communication (Doc 4444 §10.1) and transfer of control (§6.3)
 * are explicitly separate — they can happen at different times. LVP/reduced-sep
 * scenarios depend on this distinction.
 *
 * See design doc §4 (2026-04-19-approach-sequencing.md).
 */
sealed interface CoordinationMessage {
    val from: ControllerId
    val to: ControllerId
    val aircraft: AircraftId
    val time: SimTime

    /**
     * Transfer of control: accepting unit now owns separation responsibility.
     * Doc 4444 §6.3.
     */
    data class TransferOfControl(
        override val from: ControllerId,
        override val to: ControllerId,
        override val aircraft: AircraftId,
        override val time: SimTime,
        val atPoint: PointId? = null,
    ) : CoordinationMessage

    /**
     * Transfer of communication: pilot instructed to contact new frequency.
     * Doc 4444 §10.1. Distinct from control transfer — communication may
     * transfer before or after control depending on local LoA.
     */
    data class TransferOfCommunication(
        override val from: ControllerId,
        override val to: ControllerId,
        override val aircraft: AircraftId,
        override val time: SimTime,
    ) : CoordinationMessage

    /**
     * Notification that approach clearance has been issued. APP → TWR.
     * TWR uses this to anticipate the arrival's approach type for landing clearance.
     */
    data class ApproachClearanceIssued(
        override val from: ControllerId,
        override val to: ControllerId,
        override val aircraft: AircraftId,
        override val time: SimTime,
        val approachType: ApproachType,
        val runway: RunwayId,
    ) : CoordinationMessage

    /**
     * Arrival sequence update. APP → TWR.
     * TWR uses this for runway-duty planning and traffic-info to departures.
     */
    data class SequenceUpdate(
        override val from: ControllerId,
        override val to: ControllerId,
        override val aircraft: AircraftId,
        override val time: SimTime,
        val sequence: ArrivalSequence,
    ) : CoordinationMessage

    /**
     * Speed restriction still in effect. APP → TWR.
     * TWR must not override APP's speed restriction until explicitly cancelled.
     */
    data class SpeedRestriction(
        override val from: ControllerId,
        override val to: ControllerId,
        override val aircraft: AircraftId,
        override val time: SimTime,
        val speed: Speed,
    ) : CoordinationMessage

    /**
     * Departure release. TWR → APP.
     * TWR requests release for a departure into approach-controlled airspace.
     * APP responds with release conditions (or hold).
     */
    data class DepartureRelease(
        override val from: ControllerId,
        override val to: ControllerId,
        override val aircraft: AircraftId,
        override val time: SimTime,
        val runway: RunwayId,
    ) : CoordinationMessage

    /**
     * Departure release response. APP → TWR.
     * Completes the release handshake with approval, conditions, or hold.
     */
    data class DepartureReleaseResponse(
        override val from: ControllerId,
        override val to: ControllerId,
        override val aircraft: AircraftId,
        override val time: SimTime,
        val approved: Boolean,
        val conditions: String? = null,
        val expiresAt: SimTime? = null,
    ) : CoordinationMessage

    /**
     * Missed approach / go-around notification. TWR → APP.
     * Highest-priority coordination message: APP must immediately re-sequence
     * and protect the missed approach path.
     */
    data class MissedApproachNotification(
        override val from: ControllerId,
        override val to: ControllerId,
        override val aircraft: AircraftId,
        override val time: SimTime,
        val runway: RunwayId,
    ) : CoordinationMessage
}

/**
 * Configurable handoff gate per aerodrome Letter of Agreement.
 *
 * Realistic gate: `localiserEstablished AND insideHandoffFix` — per Doc 9426
 * Part II. At 8nm the aircraft may not yet be established and APP still needs
 * speed control. The fix is a configured LoA parameter, not a hardcoded distance.
 */
data class HandoffGate(
    val condition: HandoffCondition,
    val fix: PointId? = null,
    val maxDistanceNm: Double? = null,
)

enum class HandoffCondition {
    /** Aircraft established on the localiser. */
    LOCALISER_ESTABLISHED,
    /** Aircraft joining the visual circuit pattern. */
    VISUAL_PATTERN_JOIN,
    /** Aircraft within a specified distance from threshold. */
    DISTANCE_FROM_THRESHOLD,
}
