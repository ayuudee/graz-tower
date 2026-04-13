package xyz.easiersaid.twr.protocol

// EASA / SERA-oriented ATC instruction model
// The goal here is to model instruction *families* with typed parameters,
// rather than attempting a closed list of every possible phraseology string.

// -----------------------------------------------------------------------------
// Core IDs / scalar domain types
// -----------------------------------------------------------------------------

@JvmInline value class AircraftId(val value: String)
@JvmInline value class ClearanceId(val value: String)
@JvmInline value class TickNumber(val value: Long)
@JvmInline value class RunwayId(val value: String)
@JvmInline value class PointId(val value: String)
@JvmInline value class TaxiwayId(val value: String)
@JvmInline value class StandId(val value: String)
@JvmInline value class ApronId(val value: String)
@JvmInline value class CircuitProcedureId(val value: String)
@JvmInline value class FixId(val value: String)
@JvmInline value class SidId(val value: String)
@JvmInline value class AirwayId(val value: String)
@JvmInline value class StarId(val value: String)
@JvmInline value class VfrRouteId(val value: String)
@JvmInline value class HoldingPatternId(val value: String)
@JvmInline value class ApproachId(val value: String)
@JvmInline value class AerodromeId(val value: String)
@JvmInline value class AirspaceVolumeId(val value: String)
@JvmInline value class FirId(val value: String)
@JvmInline value class ControllerId(val value: String)
@JvmInline value class Callsign(val value: String)

data class Frequency(val mhz: String) {
    init {
        require(mhz.isNotBlank()) { "Frequency must not be blank" }
        val freq = mhz.toDoubleOrNull()
        require(freq != null && freq >= 118.0 && freq <= 137.0) {
            "Frequency must be in VHF airband range (118.000–136.975 MHz)"
        }
    }
}

data class Heading(val degrees: Int) {
    init {
        require(degrees in 1..360) { "Heading must be in 1..360" }
    }
}

data class Squawk(val code: Int) {
    init {
        require(code in 0..7777) { "Squawk must be in 0..7777" }
        require(code.toString().padStart(4, '0').all { it in '0'..'7' }) {
            "Each digit of a squawk code must be 0–7 (octal)"
        }
    }
}

data class Knots(val value: Int) {
    init {
        require(value > 0) { "Speed must be positive" }
    }
}

data class Mach(val value: Double) {
    init {
        require(value > 0.0 && value < 1.0) { "Mach must be in (0.0, 1.0)" }
    }
}

data class DmeDistanceNm(val value: Double) {
    init {
        require(value >= 0.0) { "DME distance must be >= 0" }
    }
}

data class Minutes(val value: Int) {
    init {
        require(value >= 0) { "Minutes must be >= 0" }
    }
}

data class Wind(
    val directionDegrees: Int,
    val speedKnots: Int,
    val gustKnots: Int? = null
) {
    init {
        require(directionDegrees in 0..360) { "Wind direction must be in 0..360" }
        require(speedKnots >= 0) { "Wind speed must be >= 0" }
        if (gustKnots != null) require(gustKnots > speedKnots) { "Gust must exceed mean wind speed" }
    }
}

// -----------------------------------------------------------------------------
// Level / altitude domain
// -----------------------------------------------------------------------------

sealed interface Level {
    data class FlightLevel(val fl: Int) : Level {
        init {
            require(fl > 0) { "Flight level must be > 0" }
        }
    }

    data class AltitudeFeet(val feet: Int) : Level {
        init {
            require(feet >= 0) { "Altitude must be >= 0 ft" }
        }
    }

    data class HeightFeet(val feet: Int) : Level {
        init {
            require(feet >= 0) { "Height must be >= 0 ft" }
        }
    }
}

sealed interface PressureSetting {
    data class QnhHpa(val value: Int) : PressureSetting
    data class QfeHpa(val value: Int) : PressureSetting
    data object Standard : PressureSetting
}

// -----------------------------------------------------------------------------
// Speed domain
// -----------------------------------------------------------------------------

sealed interface Speed {
    data class InKnots(val knots: Knots) : Speed
    data class InMach(val mach: Mach) : Speed
}

// -----------------------------------------------------------------------------
// Supporting enums / parameter types
// -----------------------------------------------------------------------------

enum class TurnDirection { LEFT, RIGHT }
enum class OrbitDirection { LEFT, RIGHT }
enum class CircuitDirection { LEFT_HAND, RIGHT_HAND }

enum class RoleName {
    CLEARANCE_DELIVERY,
    GROUND,
    TOWER,
    APPROACH,
    DEPARTURE,
    AREA_CONTROL,
    AFIS
}

enum class JoinType {
    STRAIGHT_IN,
    BASE,
    DOWNWIND,
    CROSSWIND,
    MID_DOWNWIND,
    OVERHEAD,
    LONG_FINAL
}

enum class ApproachType {
    ILS,
    LOC,
    RNAV,
    RNP,
    VOR,
    NDB,
    SRA,
    VISUAL,
    PAR
}

enum class ApproachComponent {
    LOCALISER,
    GLIDEPATH
}

enum class TransponderMode {
    CHARLIE,
    STANDBY,
    NORMAL
}

enum class FlightPhase {
    AIRBORNE,
    DOWNWIND,
    BASE,
    FINAL,
    LONG_FINAL,
    ESTABLISHED,
    ESTABLISHED_LOCALISER,
    ESTABLISHED_GLIDEPATH,
    PASSING_LEVEL,
    LEAVING_LEVEL,
    RUNWAY_VACATED,
    READY_FOR_DEPARTURE
}

sealed interface TrafficRef {
    data class ByCallsign(val callsign: Callsign) : TrafficRef
    data class ByDescription(val text: String) : TrafficRef
    data class SequenceNumber(val number: Int) : TrafficRef {
        init {
            require(number > 0) { "Sequence number must be > 0" }
        }
    }
}

sealed interface RouteSpec {
    data class Direct(val fix: FixId) : RouteSpec
    data class Via(val fixes: List<FixId>) : RouteSpec {
        init {
            require(fixes.isNotEmpty()) { "Via route must not be empty" }
        }
    }

    data class Airway(val airway: AirwayId, val exitFix: FixId) : RouteSpec
    data class ViaSid(val sid: SidId) : RouteSpec
    data class ViaStar(val star: StarId) : RouteSpec
    data class ViaRoute(val route: VfrRouteId) : RouteSpec
}

sealed interface HoldSpec {
    data class Published(val fix: FixId) : HoldSpec
    data class InboundTrack(
        val fix: FixId,
        val inboundDegreesMagnetic: Int,
        val turnDirection: TurnDirection,
        val legTime: Minutes? = null,
        val legDistance: DmeDistanceNm? = null
    ) : HoldSpec {
        init {
            require(inboundDegreesMagnetic in 1..360)
        }
    }
}

// -----------------------------------------------------------------------------
// Conditional clearance support
// -----------------------------------------------------------------------------

enum class TrafficAction {
    LANDING,
    DEPARTING,
    PASSING,
    CROSSING
}

sealed interface ConditionalPredicate {
    data class AfterTraffic(
        val traffic: TrafficRef,
        val action: TrafficAction
    ) : ConditionalPredicate

    data class BehindTraffic(
        val traffic: TrafficRef
    ) : ConditionalPredicate
}

// -----------------------------------------------------------------------------
// Transmission hierarchy
// -----------------------------------------------------------------------------

// Parent for all controller-to-aircraft transmissions
sealed interface ControllerTransmission {
    val target: AircraftId
}

// An instruction that directs the pilot to take action
sealed interface AtcInstruction : ControllerTransmission

// A controller response or information item that does not direct action
sealed interface ControllerResponse : ControllerTransmission

// -----------------------------------------------------------------------------
// Instruction category interfaces
// -----------------------------------------------------------------------------

sealed interface Clearance : AtcInstruction
sealed interface GroundInstruction : AtcInstruction
sealed interface RunwayInstruction : AtcInstruction
sealed interface RouteInstruction : AtcInstruction
sealed interface VectorInstruction : AtcInstruction
sealed interface LevelInstruction : AtcInstruction
sealed interface SpeedInstruction : AtcInstruction
sealed interface ApproachInstruction : AtcInstruction
sealed interface ReportInstruction : AtcInstruction
sealed interface FrequencyInstruction : AtcInstruction
sealed interface SurveillanceInstruction : AtcInstruction
sealed interface SequencingInstruction : AtcInstruction
sealed interface AerodromeInstruction : AtcInstruction
sealed interface EmergencyInstruction : AtcInstruction

// -----------------------------------------------------------------------------
// Conditional clearance wrapper
// -----------------------------------------------------------------------------

data class ConditionalClearance(
    override val target: AircraftId,
    val condition: ConditionalPredicate,
    val instruction: AtcInstruction
) : Clearance

// -----------------------------------------------------------------------------
// Ground movement
// -----------------------------------------------------------------------------

data class StartupApproved(
    override val target: AircraftId
) : Clearance, GroundInstruction

data class PushbackApproved(
    override val target: AircraftId
) : Clearance, GroundInstruction

data class PushbackFace(
    override val target: AircraftId,
    val heading: Heading
) : GroundInstruction

data class TaxiTo(
    override val target: AircraftId,
    val destination: PointId,
    val via: List<PointId> = emptyList()
) : GroundInstruction

data class TaxiViaRunway(
    override val target: AircraftId,
    val runway: RunwayId,
    val destination: PointId? = null
) : GroundInstruction

data class AirTaxiTo(
    override val target: AircraftId,
    val destination: PointId,
    val via: List<PointId> = emptyList()
) : GroundInstruction

data class HoldPosition(
    override val target: AircraftId
) : GroundInstruction

data class HoldShortOf(
    override val target: AircraftId,
    val runway: RunwayId
) : Clearance, GroundInstruction

data class CrossRunway(
    override val target: AircraftId,
    val runway: RunwayId
) : Clearance, GroundInstruction

data class BacktrackRunway(
    override val target: AircraftId,
    val runway: RunwayId
) : Clearance, GroundInstruction

data class VacateRunway(
    override val target: AircraftId,
    val direction: TurnDirection? = null,
    val via: PointId? = null
) : GroundInstruction

data class TaxiIntoHoldingBay(
    override val target: AircraftId
) : GroundInstruction

data class TaxiWithCaution(
    override val target: AircraftId,
    val reason: String
) : GroundInstruction

data class ExpediteTaxi(
    override val target: AircraftId
) : GroundInstruction

data class ReduceTaxiSpeed(
    override val target: AircraftId
) : GroundInstruction

data class GiveWayToTraffic(
    override val target: AircraftId,
    val traffic: TrafficRef
) : GroundInstruction

// -----------------------------------------------------------------------------
// Runway / departure / landing
// -----------------------------------------------------------------------------

data class LineUpAndWait(
    override val target: AircraftId,
    val runway: RunwayId
) : RunwayInstruction

data class ClearedForTakeoff(
    override val target: AircraftId,
    val runway: RunwayId,
    val surfaceWind: Wind? = null
) : Clearance, RunwayInstruction

data class ClearedToLand(
    override val target: AircraftId,
    val runway: RunwayId,
    val surfaceWind: Wind? = null
) : Clearance, RunwayInstruction

data class ClearedTouchAndGo(
    override val target: AircraftId,
    val runway: RunwayId,
    val surfaceWind: Wind? = null
) : Clearance, RunwayInstruction

data class ClearedLowApproach(
    override val target: AircraftId,
    val runway: RunwayId
) : Clearance, RunwayInstruction

data class GoAround(
    override val target: AircraftId,
    val level: Level? = null,
    val heading: Heading? = null
) : RunwayInstruction, ApproachInstruction

data class HoldPositionCancelTakeoff(
    override val target: AircraftId
) : RunwayInstruction

data class StopImmediately(
    override val target: AircraftId
) : EmergencyInstruction, RunwayInstruction, GroundInstruction

data class TakeoffImmediatelyOrVacateRunway(
    override val target: AircraftId,
    val runway: RunwayId
) : RunwayInstruction

data class TakeoffImmediatelyOrHoldShort(
    override val target: AircraftId,
    val runway: RunwayId
) : RunwayInstruction

data class AfterLandingVacateVia(
    override val target: AircraftId,
    val exit: PointId
) : RunwayInstruction

// -----------------------------------------------------------------------------
// Route clearances / routeing
// -----------------------------------------------------------------------------

data class ClearedTo(
    override val target: AircraftId,
    val clearanceLimit: FixId,
    val route: RouteSpec? = null
) : Clearance, RouteInstruction

data class ProceedDirect(
    override val target: AircraftId,
    val fix: FixId
) : RouteInstruction

data class ResumeOwnNavigation(
    override val target: AircraftId
) : RouteInstruction

data class RouteAsFiled(
    override val target: AircraftId
) : RouteInstruction

data class JoinAirway(
    override val target: AircraftId,
    val airway: AirwayId,
    val joinFix: FixId
) : RouteInstruction

data class RejoinSidAt(
    override val target: AircraftId,
    val fix: FixId
) : RouteInstruction

data class HoldAt(
    override val target: AircraftId,
    val hold: HoldSpec,
    val expectFurtherClearanceAt: String? = null
) : RouteInstruction

data class LeaveHoldProceedDirect(
    override val target: AircraftId,
    val fix: FixId
) : RouteInstruction

// -----------------------------------------------------------------------------
// Heading / vectoring
// -----------------------------------------------------------------------------

data class FlyHeading(
    override val target: AircraftId,
    val heading: Heading
) : VectorInstruction

data class TurnHeading(
    override val target: AircraftId,
    val direction: TurnDirection,
    val heading: Heading
) : VectorInstruction

data class TurnByDegrees(
    override val target: AircraftId,
    val direction: TurnDirection,
    val degrees: Int
) : VectorInstruction {
    init {
        require(degrees in 1..359)
    }
}

data class ContinuePresentHeading(
    override val target: AircraftId
) : VectorInstruction

data class StopTurn(
    override val target: AircraftId,
    val rollOutHeading: Heading? = null
) : VectorInstruction

data class InterceptLocaliser(
    override val target: AircraftId,
    val runway: RunwayId
) : VectorInstruction, ApproachInstruction

data class WhenAbleProceedDirect(
    override val target: AircraftId,
    val fix: FixId
) : VectorInstruction, RouteInstruction

// -----------------------------------------------------------------------------
// Vertical / level instructions
// -----------------------------------------------------------------------------

data class ClimbTo(
    override val target: AircraftId,
    val level: Level,
    val rateFtPerMin: Int? = null
) : LevelInstruction

data class DescendTo(
    override val target: AircraftId,
    val level: Level,
    val rateFtPerMin: Int? = null
) : LevelInstruction

data class ExpediteClimb(
    override val target: AircraftId,
    val level: Level
) : LevelInstruction

data class ExpediteDescend(
    override val target: AircraftId,
    val level: Level
) : LevelInstruction

data class MaintainLevel(
    override val target: AircraftId,
    val level: Level
) : LevelInstruction

data class StopClimbAt(
    override val target: AircraftId,
    val level: Level
) : LevelInstruction

data class StopDescentAt(
    override val target: AircraftId,
    val level: Level
) : LevelInstruction

data class MaintainAtOrAbove(
    override val target: AircraftId,
    val minimumLevel: Level
) : LevelInstruction

data class MaintainAtOrBelow(
    override val target: AircraftId,
    val maximumLevel: Level
) : LevelInstruction

data class AfterPassingLevelClimbTo(
    override val target: AircraftId,
    val afterPassing: Level,
    val climbTo: Level
) : LevelInstruction

data class AfterPassingLevelDescendTo(
    override val target: AircraftId,
    val afterPassing: Level,
    val descendTo: Level
) : LevelInstruction

// -----------------------------------------------------------------------------
// Speed instructions
// -----------------------------------------------------------------------------

data class MaintainSpeed(
    override val target: AircraftId,
    val speed: Speed
) : SpeedInstruction

data class ReduceSpeedTo(
    override val target: AircraftId,
    val speed: Speed
) : SpeedInstruction

data class IncreaseSpeedTo(
    override val target: AircraftId,
    val speed: Speed
) : SpeedInstruction

data class MinimumCleanSpeed(
    override val target: AircraftId
) : SpeedInstruction

data class ResumeNormalSpeed(
    override val target: AircraftId
) : SpeedInstruction

// -----------------------------------------------------------------------------
// Approach / arrival / circuit
// -----------------------------------------------------------------------------

data class ClearedApproach(
    override val target: AircraftId,
    val approachType: ApproachType,
    val runway: RunwayId,
    val circlingRunway: RunwayId? = null
) : Clearance, ApproachInstruction

data class ContinueApproach(
    override val target: AircraftId
) : ApproachInstruction

data class JoinCircuit(
    override val target: AircraftId,
    val circuitDirection: CircuitDirection,
    val joinType: JoinType,
    val runway: RunwayId? = null
) : ApproachInstruction, AerodromeInstruction

data class MakeShortApproach(
    override val target: AircraftId
) : ApproachInstruction, AerodromeInstruction

data class MakeLongApproach(
    override val target: AircraftId
) : ApproachInstruction, AerodromeInstruction

data class ExtendDownwind(
    override val target: AircraftId
) : ApproachInstruction, AerodromeInstruction

data class TurnBase(
    override val target: AircraftId
) : ApproachInstruction, AerodromeInstruction

data class Orbit(
    override val target: AircraftId,
    val direction: OrbitDirection
) : AerodromeInstruction, VectorInstruction

data class MakeAnotherCircuit(
    override val target: AircraftId
) : AerodromeInstruction

data class CommenceApproachAt(
    override val target: AircraftId,
    val timeUtcHhmm: String
) : ApproachInstruction

data class MaintainAltitudeUntilEstablished(
    override val target: AircraftId,
    val level: Level,
    val on: ApproachComponent
) : ApproachInstruction, LevelInstruction

// -----------------------------------------------------------------------------
// Reporting instructions
// -----------------------------------------------------------------------------

sealed interface ReportEvent {
    data object Downwind : ReportEvent
    data object Base : ReportEvent
    data object Final : ReportEvent
    data object LongFinal : ReportEvent
    data object Airborne : ReportEvent
    data object Established : ReportEvent
    data object EstablishedLocaliser : ReportEvent
    data object EstablishedGlidepath : ReportEvent
    data object RunwayVacated : ReportEvent
    data object Ready : ReportEvent
    data object GoingAround : ReportEvent
    data object VisualWithField : ReportEvent
    data object EstablishedInHold : ReportEvent
    data object TcasRa : ReportEvent
    data object MinimumFuel : ReportEvent
    data class PassingLevel(val level: Level) : ReportEvent
    data class LeavingLevel(val level: Level) : ReportEvent
    data class DistanceDme(val distance: DmeDistanceNm) : ReportEvent
    data class OverFix(val fix: FixId) : ReportEvent
}

data class ReportWhen(
    override val target: AircraftId,
    val event: ReportEvent
) : ReportInstruction

data class ReportTrafficInSight(
    override val target: AircraftId,
    val traffic: TrafficRef
) : ReportInstruction

// -----------------------------------------------------------------------------
// Sequencing / traffic
// -----------------------------------------------------------------------------

data class FollowTraffic(
    override val target: AircraftId,
    val traffic: TrafficRef
) : SequencingInstruction, AerodromeInstruction

data class NumberInSequence(
    override val target: AircraftId,
    val number: Int,
    val behindTraffic: TrafficRef? = null
) : SequencingInstruction {
    init {
        require(number > 0)
    }
}

data class MaintainVisualSeparation(
    override val target: AircraftId,
    val traffic: TrafficRef
) : SequencingInstruction

// -----------------------------------------------------------------------------
// Frequency / communications
// -----------------------------------------------------------------------------

data class ContactFrequency(
    override val target: AircraftId,
    val role: RoleName,
    val frequency: Frequency? = null
) : FrequencyInstruction

data class MonitorFrequency(
    override val target: AircraftId,
    val role: RoleName,
    val frequency: Frequency? = null
) : FrequencyInstruction

// -----------------------------------------------------------------------------
// Surveillance / transponder
// -----------------------------------------------------------------------------

data class SetSquawk(
    override val target: AircraftId,
    val squawk: Squawk
) : SurveillanceInstruction

data class ConfirmSquawk(
    override val target: AircraftId,
    val squawk: Squawk
) : SurveillanceInstruction

data class SquawkIdent(
    override val target: AircraftId
) : SurveillanceInstruction

data class SquawkStandby(
    override val target: AircraftId
) : SurveillanceInstruction

data class SquawkNormal(
    override val target: AircraftId,
    val mode: TransponderMode
) : SurveillanceInstruction

data class StopSquawk(
    override val target: AircraftId,
    val mode: TransponderMode
) : SurveillanceInstruction

// -----------------------------------------------------------------------------
// Pressure setting
// -----------------------------------------------------------------------------

data class SetPressure(
    override val target: AircraftId,
    val pressure: PressureSetting
) : AtcInstruction

// -----------------------------------------------------------------------------
// Emergency instructions
// -----------------------------------------------------------------------------

data class DivertTo(
    override val target: AircraftId,
    val aerodrome: AerodromeId
) : EmergencyInstruction

// -----------------------------------------------------------------------------
// Misc operational / airspace
// -----------------------------------------------------------------------------

data class ClearedToEnterControlZone(
    override val target: AircraftId,
    val airspace: AirspaceVolumeId,
    val route: RouteSpec? = null,
    val levelRestriction: Level? = null
) : Clearance

data class RemainOutsideControlledAirspace(
    override val target: AircraftId,
    val airspace: AirspaceVolumeId
) : AtcInstruction

// -----------------------------------------------------------------------------
// Controller responses / information (not instructions — do not direct action)
// -----------------------------------------------------------------------------

data class ReadBackCorrect(
    override val target: AircraftId
) : ControllerResponse

data class Standby(
    override val target: AircraftId
) : ControllerResponse

data class Identified(
    override val target: AircraftId
) : ControllerResponse

data class NotIdentified(
    override val target: AircraftId
) : ControllerResponse

data class RadarContact(
    override val target: AircraftId
) : ControllerResponse

data class RadarServiceTerminated(
    override val target: AircraftId
) : ControllerResponse

data class AcknowledgeEmergency(
    override val target: AircraftId
) : ControllerResponse

data class TrafficInformation(
    override val target: AircraftId,
    val traffic: TrafficRef,
    val clockPosition: Int? = null,
    val distanceNm: Double? = null,
    val level: Level? = null,
    val movement: String? = null
) : ControllerResponse

data class CautionWakeTurbulence(
    override val target: AircraftId,
    val causedBy: TrafficRef? = null
) : ControllerResponse

data class ExpectApproach(
    override val target: AircraftId,
    val approachType: ApproachType,
    val runway: RunwayId
) : ControllerResponse

data class ExpectVectors(
    override val target: AircraftId,
    val reason: String? = null
) : ControllerResponse
