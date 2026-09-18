package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.SimTime

@JvmInline
value class VehicleId(val value: String)

@JvmInline
value class VehiclePermissionId(val value: Long)

data class VehicleState(
    val id: VehicleId,
    val callsign: Callsign,
    val position: PointId,
    val destination: PointId,
    val route: List<PointId>,
    val phase: VehicleMovementPhase = VehicleMovementPhase.NotCalled,
    val activePermission: ActiveVehiclePermission? = null,
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
