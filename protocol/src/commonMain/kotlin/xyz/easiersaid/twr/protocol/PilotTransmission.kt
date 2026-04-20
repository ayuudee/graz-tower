package xyz.easiersaid.twr.protocol

// Pilot → ATC transmission model
// Orthogonal to the ATC instruction hierarchy.
// Structurally expressive, not phrase-string driven.

// -----------------------------------------------------------------------------
// Top-level
// -----------------------------------------------------------------------------

sealed interface PilotTransmission

// -----------------------------------------------------------------------------
// Readback
// -----------------------------------------------------------------------------

data class Readback(
    val elements: List<ReadbackElement>
) : PilotTransmission

// --- Readback element structure ---
// Ordering of elements in the list carries sequencing semantics.

sealed interface ReadbackElement

data class SimpleElement(val value: AtomicReadback) : ReadbackElement

data class ConditionalElement(
    val condition: ReadbackCondition,
    val action: AtomicReadback
) : ReadbackElement

// --- Atomic readback values ---

sealed interface AtomicReadback

data class HeadingReadback(val heading: Heading) : AtomicReadback
data class LevelReadback(val level: Level) : AtomicReadback
data class SpeedReadback(val speed: Speed) : AtomicReadback
data class RouteReadback(val route: RouteSpec) : AtomicReadback
data class RunwayReadback(val runway: RunwayId) : AtomicReadback
data class SquawkReadback(val squawk: Squawk) : AtomicReadback
data class FrequencyReadback(
    val frequency: Frequency,
    val role: RoleName? = null
) : AtomicReadback
data class PressureSettingReadback(val pressure: PressureSetting) : AtomicReadback
data class HoldShortReadback(val runway: RunwayId) : AtomicReadback
data class ClearedForTakeoffReadback(val runway: RunwayId) : AtomicReadback
data class ClearedToLandReadback(val runway: RunwayId) : AtomicReadback
data class ClearedApproachReadback(
    val approachType: ApproachType,
    val runway: RunwayId
) : AtomicReadback
data class ClearedTouchAndGoReadback(val runway: RunwayId) : AtomicReadback
data class ClearedLowApproachReadback(val runway: RunwayId) : AtomicReadback
data class LineUpReadback(val runway: RunwayId) : AtomicReadback
data class CrossRunwayReadback(val runway: RunwayId) : AtomicReadback
data class BacktrackReadback(val runway: RunwayId) : AtomicReadback
data class TaxiRouteReadback(
    val destination: PointId,
    val via: List<PointId> = emptyList()
) : AtomicReadback
data class HoldReadback(val hold: HoldSpec) : AtomicReadback
data class GoAroundReadback(
    val runway: RunwayId? = null,
    val level: Level? = null,
    val heading: Heading? = null
) : AtomicReadback
data class VacateReadback(
    val direction: TurnDirection? = null,
    val via: PointId? = null
) : AtomicReadback
data class OrbitReadback(val direction: OrbitDirection) : AtomicReadback
data class ExtendDownwindReadback(val runway: RunwayId? = null) : AtomicReadback
data class VisualApproachReadback(val runway: RunwayId) : AtomicReadback
data class SpecialVfrReadback(val airspace: AirspaceVolumeId) : AtomicReadback
data class FreeTextReadback(val text: String) : AtomicReadback

/**
 * Acknowledgement of [HoldPosition] / [HoldPositionCancelTakeoff].
 *
 * These instructions have no numeric or identifier payload of their own — the
 * pilot echoes "HOLDING, [callsign]" (and "holding, cancel take-off"). Making
 * the acknowledgement a distinct atom rather than free text lets the classifier
 * detect a missing readback, which matters for runway-safety instructions
 * (CAP 413 §4.46, ICAO 4444 §12.3.1). [cancelTakeoff] distinguishes the two
 * so a cancel-takeoff readback cannot satisfy a simple hold-position pending,
 * and vice versa.
 */
data class HoldingAcknowledgementReadback(
    val cancelTakeoff: Boolean = false,
) : AtomicReadback

/**
 * Acknowledgement of [NumberInSequence].
 *
 * Pilot confirms sequence number: "number [n], [callsign]". The readback atom
 * carries the number so the classifier can detect a wrong-number readback
 * ("number 2" read back as "number 3").
 */
data class SequenceAcknowledgementReadback(
    val number: Int,
    val behindTraffic: TrafficRef? = null,
) : AtomicReadback

/**
 * Readback of ATC-initiated [BreakOff] (discontinue approach).
 * Carries missed-approach level/heading when issued — these are readback-required
 * per ICAO Doc 4444 §12.3.1 (same safety-critical status as go-around instructions).
 */
/** Acknowledgement of [Disregard] instruction. */
data object DisregardAcknowledgementReadback : AtomicReadback

data class BreakOffReadback(
    val level: Level? = null,
    val heading: Heading? = null,
) : AtomicReadback

// --- Readback conditions ---

sealed interface ReadbackCondition

data class PassingLevelCondition(val level: Level) : ReadbackCondition
data object WhenAbleCondition : ReadbackCondition
data class AfterFixCondition(val fix: FixId) : ReadbackCondition
data class AfterTrafficCondition(
    val traffic: TrafficRef,
    val action: TrafficAction
) : ReadbackCondition
data class BehindTrafficCondition(
    val traffic: TrafficRef
) : ReadbackCondition
data class AfterDepartureCondition(val description: String? = null) : ReadbackCondition
data class AtLevelCondition(val level: Level) : ReadbackCondition
data class AtDistanceCondition(val distance: DmeDistanceNm) : ReadbackCondition

// -----------------------------------------------------------------------------
// Initial contact
// -----------------------------------------------------------------------------

data class InitialContact(
    val stationCalled: RoleName,
    val aircraftType: String? = null,
    val position: String? = null,
    val level: Level? = null,
    val atisCode: Char? = null,
    val intention: RequestType? = null
) : PilotTransmission

// -----------------------------------------------------------------------------
// Requests
// -----------------------------------------------------------------------------

data class Request(
    val type: RequestType
) : PilotTransmission

sealed interface RequestType

data class RequestClimb(val level: Level) : RequestType
data class RequestDescent(val level: Level) : RequestType

data class RequestDirect(val fix: FixId) : RequestType
data class RequestRoute(val route: RouteSpec) : RequestType

data class RequestApproach(
    val type: ApproachType,
    val runway: RunwayId? = null
) : RequestType

data class RequestJoinCircuit(
    val joinType: JoinType,
    val runway: RunwayId? = null
) : RequestType

data class RequestFrequencyChange(
    val frequency: Frequency? = null
) : RequestType

data class RequestStartup(
    val atisCode: Char? = null,
    val stand: String? = null
) : RequestType

data object RequestPushback : RequestType

data class RequestTaxi(
    val destination: String? = null
) : RequestType

data class RequestHigherSpeed(val speed: Knots? = null) : RequestType
data class RequestLowerSpeed(val speed: Knots? = null) : RequestType

data class RequestWeatherDeviation(
    val direction: TurnDirection? = null,
    val distanceNm: DmeDistanceNm? = null
) : RequestType

data object RequestSpecialVfr : RequestType
data object RequestHolding : RequestType
data class RequestRunwayChange(val runway: RunwayId) : RequestType
data object RequestPriorityLanding : RequestType
data object RequestShortApproach : RequestType
data object RequestVisualApproach : RequestType
data object RequestRightBase : RequestType
data object RequestOrbit : RequestType

data class RequestInformation(val topic: String) : RequestType

data class RequestFreeText(val text: String) : RequestType

// -----------------------------------------------------------------------------
// Reports
// -----------------------------------------------------------------------------

data class Report(
    val events: List<ReportEvent>,
    /** Runway designator for circuit position reports. Null for non-circuit reports. */
    val runway: RunwayId? = null,
) : PilotTransmission

// -----------------------------------------------------------------------------
// Acknowledgements
// -----------------------------------------------------------------------------

data class Acknowledge(
    val type: AcknowledgeType
) : PilotTransmission

sealed interface AcknowledgeType

data object Wilco : AcknowledgeType
data object Roger : AcknowledgeType
data object Affirm : AcknowledgeType
data object Negative : AcknowledgeType
data class Unable(val reason: String? = null) : AcknowledgeType
data object StandbyAck : AcknowledgeType
data class AcknowledgeWithInfo(val text: String) : AcknowledgeType

// -----------------------------------------------------------------------------
// Traffic responses
// -----------------------------------------------------------------------------

data class TrafficInSight(
    val traffic: TrafficRef? = null
) : PilotTransmission

data object NegativeContact : PilotTransmission

// -----------------------------------------------------------------------------
// Communication management
// -----------------------------------------------------------------------------

data class SayAgain(
    val element: String? = null
) : PilotTransmission

data class Confirm(
    val element: String
) : PilotTransmission

// -----------------------------------------------------------------------------
// Emergency / Urgency
// -----------------------------------------------------------------------------

data class Emergency(
    val type: EmergencyType,
    val details: EmergencyDetails,
    val onBehalfOf: AircraftId? = null
) : PilotTransmission

data object CancelEmergency : PilotTransmission

enum class EmergencyType {
    MAYDAY,
    PAN_PAN
}

data class EmergencyDetails(
    val stationAddressed: RoleName? = null,
    val aircraftType: String? = null,
    val nature: String,
    val intentions: String? = null,
    val position: String? = null,
    val level: Level? = null,
    val heading: Heading? = null,
    val personsOnBoard: Int? = null,
    val fuelRemainingMinutes: Int? = null,
    val remarks: String? = null
)

// -----------------------------------------------------------------------------
// Composite message wrapper
// -----------------------------------------------------------------------------

data class PilotMessage(
    val callsign: Callsign,
    val transmissions: List<PilotTransmission>
)
