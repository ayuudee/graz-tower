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

@ConsistentCopyVisibility
data class Frequency private constructor(val mhz: String) {
    companion object {
        operator fun invoke(mhz: String): arrow.core.Either<String, Frequency> {
            if (mhz.isBlank()) return arrow.core.Either.Left("Frequency must not be blank")
            val freq = mhz.toDoubleOrNull()
                ?: return arrow.core.Either.Left("Frequency must be numeric: $mhz")
            if (freq < 117.975 || freq > 136.975)
                return arrow.core.Either.Left("Frequency must be in VHF airband range (117.975–136.975 MHz): $mhz")
            return arrow.core.Either.Right(Frequency(mhz))
        }

        fun unsafe(mhz: String): Frequency = invoke(mhz).fold({ error(it) }, { it })
    }
}

@ConsistentCopyVisibility
data class Heading private constructor(val degrees: Int) {
    companion object {
        operator fun invoke(degrees: Int): arrow.core.Either<String, Heading> =
            if (degrees in 1..360) arrow.core.Either.Right(Heading(degrees))
            else arrow.core.Either.Left("Heading must be in 1..360: $degrees")

        fun unsafe(degrees: Int): Heading = invoke(degrees).fold({ error(it) }, { it })
    }
}

@ConsistentCopyVisibility
data class Squawk private constructor(val code: Int) {
    companion object {
        operator fun invoke(code: Int): arrow.core.Either<String, Squawk> {
            if (code !in 0..7777)
                return arrow.core.Either.Left("Squawk must be in 0..7777: $code")
            if (!code.toString().padStart(4, '0').all { it in '0'..'7' })
                return arrow.core.Either.Left("Each digit of a squawk code must be 0–7 (octal): $code")
            return arrow.core.Either.Right(Squawk(code))
        }

        fun unsafe(code: Int): Squawk = invoke(code).fold({ error(it) }, { it })
    }
}

@ConsistentCopyVisibility
data class Knots private constructor(val value: Int) {
    companion object {
        operator fun invoke(value: Int): arrow.core.Either<String, Knots> =
            if (value > 0) arrow.core.Either.Right(Knots(value))
            else arrow.core.Either.Left("Speed must be positive: $value")

        fun unsafe(value: Int): Knots = invoke(value).fold({ error(it) }, { it })
    }
}

@ConsistentCopyVisibility
data class Mach private constructor(val value: Double) {
    companion object {
        operator fun invoke(value: Double): arrow.core.Either<String, Mach> =
            if (value > 0.0 && value <= 4.0) arrow.core.Either.Right(Mach(value))
            else arrow.core.Either.Left("Mach must be in (0.0, 4.0]: $value")

        fun unsafe(value: Double): Mach = invoke(value).fold({ error(it) }, { it })
    }
}

@ConsistentCopyVisibility
data class DmeDistanceNm private constructor(val value: Double) {
    companion object {
        operator fun invoke(value: Double): arrow.core.Either<String, DmeDistanceNm> =
            if (value >= 0.0) arrow.core.Either.Right(DmeDistanceNm(value))
            else arrow.core.Either.Left("DME distance must be >= 0: $value")

        fun unsafe(value: Double): DmeDistanceNm = invoke(value).fold({ error(it) }, { it })
    }
}

@ConsistentCopyVisibility
data class Minutes private constructor(val value: Int) {
    companion object {
        operator fun invoke(value: Int): arrow.core.Either<String, Minutes> =
            if (value >= 0) arrow.core.Either.Right(Minutes(value))
            else arrow.core.Either.Left("Minutes must be >= 0: $value")

        fun unsafe(value: Int): Minutes = invoke(value).fold({ error(it) }, { it })
    }
}

@ConsistentCopyVisibility
data class Wind private constructor(
    val directionDegrees: Int,
    val speedKnots: Int,
    val gustKnots: Int? = null
) {
    companion object {
        operator fun invoke(
            directionDegrees: Int,
            speedKnots: Int,
            gustKnots: Int? = null
        ): arrow.core.Either<String, Wind> {
            if (directionDegrees !in 0..360)
                return arrow.core.Either.Left("Wind direction must be in 0..360: $directionDegrees")
            if (speedKnots < 0)
                return arrow.core.Either.Left("Wind speed must be >= 0: $speedKnots")
            if (gustKnots != null && gustKnots <= speedKnots)
                return arrow.core.Either.Left("Gust must exceed mean wind speed: gust=$gustKnots, mean=$speedKnots")
            return arrow.core.Either.Right(Wind(directionDegrees, speedKnots, gustKnots))
        }

        fun unsafe(directionDegrees: Int, speedKnots: Int, gustKnots: Int? = null): Wind =
            invoke(directionDegrees, speedKnots, gustKnots).fold({ error(it) }, { it })
    }
}

// -----------------------------------------------------------------------------
// Level / altitude domain
// -----------------------------------------------------------------------------

sealed interface Level {
    @ConsistentCopyVisibility
    data class FlightLevel private constructor(val fl: Int) : Level {
        companion object {
            operator fun invoke(fl: Int): arrow.core.Either<String, FlightLevel> =
                if (fl > 0) arrow.core.Either.Right(FlightLevel(fl))
                else arrow.core.Either.Left("Flight level must be > 0: $fl")

            fun unsafe(fl: Int): FlightLevel = invoke(fl).fold({ error(it) }, { it })
        }
    }

    @ConsistentCopyVisibility
    data class AltitudeFeet private constructor(val feet: Int) : Level {
        companion object {
            operator fun invoke(feet: Int): arrow.core.Either<String, AltitudeFeet> =
                if (feet >= 0) arrow.core.Either.Right(AltitudeFeet(feet))
                else arrow.core.Either.Left("Altitude must be >= 0 ft: $feet")

            fun unsafe(feet: Int): AltitudeFeet = invoke(feet).fold({ error(it) }, { it })
        }
    }

    @ConsistentCopyVisibility
    data class HeightFeet private constructor(val feet: Int) : Level {
        companion object {
            operator fun invoke(feet: Int): arrow.core.Either<String, HeightFeet> =
                if (feet >= 0) arrow.core.Either.Right(HeightFeet(feet))
                else arrow.core.Either.Left("Height must be >= 0 ft: $feet")

            fun unsafe(feet: Int): HeightFeet = invoke(feet).fold({ error(it) }, { it })
        }
    }
}

sealed interface PressureSetting {
    @ConsistentCopyVisibility
    data class QnhHpa private constructor(val value: Int) : PressureSetting {
        companion object {
            operator fun invoke(value: Int): arrow.core.Either<String, QnhHpa> =
                if (value in 900..1100) arrow.core.Either.Right(QnhHpa(value))
                else arrow.core.Either.Left("QNH must be in 900..1100 hPa: $value")

            fun unsafe(value: Int): QnhHpa = invoke(value).fold({ error(it) }, { it })
        }
    }

    @ConsistentCopyVisibility
    data class QfeHpa private constructor(val value: Int) : PressureSetting {
        companion object {
            operator fun invoke(value: Int): arrow.core.Either<String, QfeHpa> =
                if (value in 900..1100) arrow.core.Either.Right(QfeHpa(value))
                else arrow.core.Either.Left("QFE must be in 900..1100 hPa: $value")

            fun unsafe(value: Int): QfeHpa = invoke(value).fold({ error(it) }, { it })
        }
    }

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
    @ConsistentCopyVisibility
    data class SequenceNumber private constructor(val number: Int) : TrafficRef {
        companion object {
            operator fun invoke(number: Int): arrow.core.Either<String, SequenceNumber> =
                if (number > 0) arrow.core.Either.Right(SequenceNumber(number))
                else arrow.core.Either.Left("Sequence number must be > 0: $number")

            fun unsafe(number: Int): SequenceNumber = invoke(number).fold({ error(it) }, { it })
        }
    }
}

sealed interface RouteSpec {
    data class Direct(val fix: FixId) : RouteSpec
    data class Via(val fixes: arrow.core.NonEmptyList<FixId>) : RouteSpec

    data class Airway(val airway: AirwayId, val exitFix: FixId) : RouteSpec
    data class ViaSid(val sid: SidId) : RouteSpec
    data class ViaStar(val star: StarId) : RouteSpec
    data class ViaRoute(val route: VfrRouteId) : RouteSpec
}

sealed interface HoldSpec {
    data class Published(val fix: FixId) : HoldSpec
    @ConsistentCopyVisibility
    data class InboundTrack private constructor(
        val fix: FixId,
        val inboundDegreesMagnetic: Int,
        val turnDirection: TurnDirection,
        val legTime: Minutes? = null,
        val legDistance: DmeDistanceNm? = null
    ) : HoldSpec {
        companion object {
            operator fun invoke(
                fix: FixId,
                inboundDegreesMagnetic: Int,
                turnDirection: TurnDirection,
                legTime: Minutes? = null,
                legDistance: DmeDistanceNm? = null
            ): arrow.core.Either<String, InboundTrack> {
                if (inboundDegreesMagnetic !in 1..360)
                    return arrow.core.Either.Left("Inbound track must be in 1..360: $inboundDegreesMagnetic")
                if (legTime != null && legDistance != null)
                    return arrow.core.Either.Left("Hold leg must specify time or distance, not both")
                return arrow.core.Either.Right(InboundTrack(fix, inboundDegreesMagnetic, turnDirection, legTime, legDistance))
            }

            fun unsafe(
                fix: FixId,
                inboundDegreesMagnetic: Int,
                turnDirection: TurnDirection,
                legTime: Minutes? = null,
                legDistance: DmeDistanceNm? = null
            ): InboundTrack = invoke(fix, inboundDegreesMagnetic, turnDirection, legTime, legDistance).fold({ error(it) }, { it })
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

    data class AtLevel(
        val level: Level
    ) : ConditionalPredicate

    data class AtDistance(
        val distance: DmeDistanceNm,
        val fix: FixId? = null
    ) : ConditionalPredicate

    data class AfterPassing(
        val fix: FixId
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
    val runway: RunwayId,
    val vacateAt: PointId? = null
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
) : Clearance, RunwayInstruction

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

@ConsistentCopyVisibility
data class TurnByDegrees private constructor(
    override val target: AircraftId,
    val direction: TurnDirection,
    val degrees: Int
) : VectorInstruction {
    companion object {
        operator fun invoke(
            target: AircraftId,
            direction: TurnDirection,
            degrees: Int
        ): arrow.core.Either<String, TurnByDegrees> =
            if (degrees in 1..360) arrow.core.Either.Right(TurnByDegrees(target, direction, degrees))
            else arrow.core.Either.Left("Turn degrees must be in 1..360: $degrees")

        fun unsafe(target: AircraftId, direction: TurnDirection, degrees: Int): TurnByDegrees =
            invoke(target, direction, degrees).fold({ error(it) }, { it })
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
    val rateFtPerMin: Int? = null,
    val byFix: FixId? = null
) : LevelInstruction

data class DescendTo(
    override val target: AircraftId,
    val level: Level,
    val rateFtPerMin: Int? = null,
    val byFix: FixId? = null
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

data class ClearedVisualApproach(
    override val target: AircraftId,
    val runway: RunwayId
) : Clearance, ApproachInstruction

data class ContinueApproach(
    override val target: AircraftId,
    /**
     * Optional reason shared with the pilot so they know *why* the landing clearance
     * is being withheld (CAP 413 §4.55 — "continue approach, [traffic rolling / runway
     * occupied / etc.]"). Null when the controller has no specific reason to share.
     */
    val reason: ContinueApproachReason? = null,
) : ApproachInstruction

enum class ContinueApproachReason {
    /** Preceding aircraft is still on the runway (landing roll or vacate in progress). */
    TRAFFIC_LANDING,

    /** Departing aircraft is rolling or yet to lift off ahead of the arrival. */
    TRAFFIC_DEPARTING,

    /** Runway is being crossed / backtracked. */
    TRAFFIC_CROSSING,

    /** Preceding arrival has gone around; the approach path is not yet clear. */
    PRECEDING_GO_AROUND,

    /** Runway access has not yet been granted (queue position pending). */
    RUNWAY_ACCESS_PENDING,
}

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
) : AerodromeInstruction

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

@ConsistentCopyVisibility
data class NumberInSequence private constructor(
    override val target: AircraftId,
    val number: Int,
    val behindTraffic: TrafficRef? = null
) : SequencingInstruction {
    companion object {
        operator fun invoke(
            target: AircraftId,
            number: Int,
            behindTraffic: TrafficRef? = null
        ): arrow.core.Either<String, NumberInSequence> =
            if (number > 0) arrow.core.Either.Right(NumberInSequence(target, number, behindTraffic))
            else arrow.core.Either.Left("Sequence number must be > 0: $number")

        fun unsafe(target: AircraftId, number: Int, behindTraffic: TrafficRef? = null): NumberInSequence =
            invoke(target, number, behindTraffic).fold({ error(it) }, { it })
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

data class SpecialVfrClearance(
    override val target: AircraftId,
    val airspace: AirspaceVolumeId,
    val route: RouteSpec? = null,
    val levelRestriction: Level? = null
) : Clearance

// -----------------------------------------------------------------------------
// Cancellation / amendment
// -----------------------------------------------------------------------------

data class CancelClearance(
    override val target: AircraftId,
    val domain: ClearanceDomain? = null
) : AtcInstruction

// -----------------------------------------------------------------------------
// Miscellaneous instructions
// -----------------------------------------------------------------------------

data class ReportIntentions(
    override val target: AircraftId
) : ReportInstruction

data class DescendWhenReady(
    override val target: AircraftId,
    val level: Level,
    val byFix: FixId? = null
) : LevelInstruction

data class AvoidArea(
    override val target: AircraftId,
    val description: String
) : AtcInstruction

data class AvoidLevel(
    override val target: AircraftId,
    val level: Level
) : LevelInstruction

// -----------------------------------------------------------------------------
// Controller responses / information (not instructions — do not direct action)
// -----------------------------------------------------------------------------

data class ReadBackCorrect(
    override val target: AircraftId
) : ControllerResponse

/**
 * Controller correction of an incorrect or incomplete readback, per ICAO Doc 4444 §12.3.2
 * and CAP 413 §1.5.6. Phraseology: "NEGATIVE, I SAY AGAIN, …" followed by the correct
 * instruction. The [correct] field carries the instruction to be re-transmitted; whether
 * the original was wrong outright or missing an atom is captured by [kind].
 */
data class ReadbackCorrection(
    override val target: AircraftId,
    val correct: AtcInstruction,
    val kind: ReadbackCorrectionKind,
) : ControllerResponse

enum class ReadbackCorrectionKind {
    /** A required atom was read back with the wrong value ("zero niner" read as "two seven"). */
    INCORRECT_ATOM,

    /** A required atom was silently omitted from the readback. */
    MISSING_ATOM,
}

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
