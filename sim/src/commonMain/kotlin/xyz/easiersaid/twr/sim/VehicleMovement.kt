package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime

@JvmInline
value class VehicleId(val value: String)

@JvmInline
value class VehiclePermissionId(val value: Long)

@JvmInline
value class AircraftOperator(val value: String) {
    init {
        require(value.isNotBlank()) { "aircraft operator must not be blank" }
    }
}

data class VehicleState(
    val id: VehicleId,
    val callsign: Callsign,
    val position: PointId,
    val destination: PointId,
    val route: List<PointId>,
    val phase: VehicleMovementPhase = VehicleMovementPhase.NotCalled,
    val activePermission: ActiveVehiclePermission? = null,
    val activeRunwayCrossing: ActiveRunwayCrossingPermission? = null,
    val activeRunwayVacate: ActiveRunwayVacate? = null,
    val activeTow: ActiveTow? = null,
    val runwayState: VehicleRunwayState = VehicleRunwayState.OffRunway,
)

sealed interface VehicleMovementPhase {
    data object NotCalled : VehicleMovementPhase
    data object AwaitingPermission : VehicleMovementPhase
    data object Standby : VehicleMovementPhase
    data object HoldingPosition : VehicleMovementPhase
    data object MovingToLimit : VehicleMovementPhase
    data object StoppedAtLimit : VehicleMovementPhase
    data object Complete : VehicleMovementPhase
}

data class ActiveVehiclePermission(
    val id: VehiclePermissionId,
    val clearanceLimit: PointId,
    val route: List<PointId>,
    val issuedAt: SimTime,
)

sealed interface VehicleRunwayState {
    data object OffRunway : VehicleRunwayState
    data class HoldingShort(val runway: RunwayId) : VehicleRunwayState
    data class Crossing(val runway: RunwayId) : VehicleRunwayState
    data class OnRunway(val runway: RunwayId) : VehicleRunwayState
    data class ClearBeyondHoldingPoint(val runway: RunwayId) : VehicleRunwayState
}

data class ActiveRunwayCrossingPermission(
    val runway: RunwayId,
    val crossingTo: PointId,
    val issuedAt: SimTime,
)

data class ActiveRunwayVacate(
    val id: VehiclePermissionId,
    val runway: RunwayId,
    val issuedAt: SimTime,
)

data class TowMetadata(
    val aircraft: AircraftId,
    val aircraftType: AircraftType,
    val operator: AircraftOperator?,
)

data class TowClearBeyondHoldingPoint(
    val runway: RunwayId,
    val permissionId: VehiclePermissionId,
)

data class ActiveTow(
    val metadata: TowMetadata,
    val receivingStation: ControllerId,
    val startedAt: SimTime,
    val clearBeyondHoldingPoint: TowClearBeyondHoldingPoint? = null,
)

sealed interface VehicleDriverTransmission {
    val vehicle: VehicleId

    data class InitialCall(
        override val vehicle: VehicleId,
        val callsign: Callsign,
        val position: PointId,
        val destination: PointId,
        val route: List<PointId>,
    ) : VehicleDriverTransmission {
        init {
            require(route.isNotEmpty()) { "Vehicle initial call route must be present when possible" }
        }
    }

    data class RequestFurtherPermission(
        override val vehicle: VehicleId,
        val from: PointId,
        val destination: PointId,
    ) : VehicleDriverTransmission

    data class RequestTow(
        override val vehicle: VehicleId,
        val callsign: Callsign,
        val receivingStation: ControllerId,
        val tow: TowMetadata,
    ) : VehicleDriverTransmission

    data class AcknowledgeRunwayCrossing(
        override val vehicle: VehicleId,
        val runway: RunwayId,
    ) : VehicleDriverTransmission

    data class RunwayVacated(
        override val vehicle: VehicleId,
        val runway: RunwayId,
    ) : VehicleDriverTransmission

    data class Acknowledge(
        override val vehicle: VehicleId,
        val instruction: VehicleControllerTransmission,
    ) : VehicleDriverTransmission
}

sealed interface VehicleControllerTransmission {
    val vehicle: VehicleId

    data class Standby(
        override val vehicle: VehicleId,
    ) : VehicleControllerTransmission

    data class HoldPosition(
        override val vehicle: VehicleId,
    ) : VehicleControllerTransmission

    data class HoldShortRunway(
        override val vehicle: VehicleId,
        val runway: RunwayId,
    ) : VehicleControllerTransmission

    data class CrossRunway(
        override val vehicle: VehicleId,
        val runway: RunwayId,
        val crossingTo: PointId,
    ) : VehicleControllerTransmission

    data class VacateRunway(
        override val vehicle: VehicleId,
        val runway: RunwayId,
    ) : VehicleControllerTransmission

    data class ProceedTo(
        override val vehicle: VehicleId,
        val clearanceLimit: PointId,
        val route: List<PointId>,
    ) : VehicleControllerTransmission {
        init {
            require(route.isNotEmpty()) { "Vehicle proceed permission route must not be empty" }
        }
    }
}
