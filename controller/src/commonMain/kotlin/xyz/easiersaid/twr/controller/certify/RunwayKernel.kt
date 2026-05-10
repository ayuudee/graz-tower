package xyz.easiersaid.twr.controller.certify

import arrow.core.NonEmptyList
import xyz.easiersaid.twr.controller.RunwayObservation
import xyz.easiersaid.twr.controller.RunwayStatus
import xyz.easiersaid.twr.controller.assess.RunwayOperation
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.RunwayId

sealed interface RunwayKernelOperation {
    val aircraft: AircraftId
    val runway: RunwayId
    val requiredDutyOperation: RunwayOperation

    data class LineUpAndWait(
        override val aircraft: AircraftId,
        override val runway: RunwayId,
    ) : RunwayKernelOperation {
        override val requiredDutyOperation: RunwayOperation = RunwayOperation.DEPARTURE
    }

    data class ClearedForTakeoff(
        override val aircraft: AircraftId,
        override val runway: RunwayId,
    ) : RunwayKernelOperation {
        override val requiredDutyOperation: RunwayOperation = RunwayOperation.DEPARTURE
    }

    data class ClearedToLand(
        override val aircraft: AircraftId,
        override val runway: RunwayId,
    ) : RunwayKernelOperation {
        override val requiredDutyOperation: RunwayOperation = RunwayOperation.ARRIVAL
    }
}

data class RunwayKernelInput(
    val operation: RunwayKernelOperation,
    val context: CertificationContext,
)

sealed interface RunwayKernelEvidence {
    data class ActiveRunwayMatched(
        val runway: RunwayId,
    ) : RunwayKernelEvidence

    data class ControllerHeldRunwayAuthority(
        val runway: RunwayId,
        val aircraft: AircraftId,
        val operation: RunwayOperation,
    ) : RunwayKernelEvidence

    data class RunwayOccupancyCompatible(
        val runway: RunwayId,
        val status: RunwayStatus,
        val occupants: Set<AircraftId>,
    ) : RunwayKernelEvidence

    data class AircraftPhaseCompatible(
        val aircraft: AircraftId,
        val onGround: Boolean,
    ) : RunwayKernelEvidence
}

sealed interface RunwayKernelFailure {
    data object MissingActiveRunway : RunwayKernelFailure

    data class ActiveRunwayMismatch(
        val instructionRunway: RunwayId,
        val activeRunway: RunwayId,
    ) : RunwayKernelFailure

    data class MissingAircraft(
        val aircraft: AircraftId,
    ) : RunwayKernelFailure

    data class MissingRunwayDuty(
        val runway: RunwayId,
    ) : RunwayKernelFailure

    data class RunwayAuthorityMismatch(
        val runway: RunwayId,
        val expectedAircraft: AircraftId,
        val actualAircraft: AircraftId?,
        val expectedOperation: RunwayOperation,
        val actualOperation: RunwayOperation?,
    ) : RunwayKernelFailure

    data class MissingRunwayObservation(
        val runway: RunwayId,
    ) : RunwayKernelFailure

    data class IncompatibleRunwayOccupancy(
        val runway: RunwayId,
        val status: RunwayStatus,
        val occupants: Set<AircraftId>,
    ) : RunwayKernelFailure

    data class IncompatibleAircraftPhase(
        val aircraft: AircraftId,
        val onGround: Boolean,
        val expectedOnGround: Boolean,
    ) : RunwayKernelFailure
}

sealed interface RunwayKernelDecision {
    class Accepted internal constructor(
        val operation: RunwayKernelOperation,
        val evidence: NonEmptyList<RunwayKernelEvidence>,
    ) : RunwayKernelDecision

    data class Rejected(
        val failure: RunwayKernelFailure,
    ) : RunwayKernelDecision
}

internal fun runwayKernelOperationFor(
    instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
): RunwayKernelOperation? =
    when (instruction) {
        is xyz.easiersaid.twr.protocol.LineUpAndWait ->
            RunwayKernelOperation.LineUpAndWait(instruction.target, instruction.runway)
        is xyz.easiersaid.twr.protocol.ClearedForTakeoff ->
            RunwayKernelOperation.ClearedForTakeoff(instruction.target, instruction.runway)
        is xyz.easiersaid.twr.protocol.ClearedToLand ->
            RunwayKernelOperation.ClearedToLand(instruction.target, instruction.runway)
        else -> null
    }

internal fun RunwayKernelDecision.Accepted.toKernelBackedEvidence(): CertificationEvidence.KernelBacked =
    CertificationEvidence.KernelBacked(
        requirement = KernelRequirement.Runway,
        summary = "runway operation kernel accepted for ${operation::class.simpleName}: " +
            evidence.all.joinToString { it::class.simpleName ?: "RunwayKernelEvidence" },
    )

internal fun RunwayKernelFailure.toCertificationFailure(): CertificationFailure.KernelRejected =
    CertificationFailure.KernelRejected(
        requirement = KernelRequirement.Runway,
        reason = when (this) {
            is RunwayKernelFailure.ActiveRunwayMismatch ->
                "Instruction runway $instructionRunway does not match active runway $activeRunway"
            is RunwayKernelFailure.IncompatibleAircraftPhase ->
                "Aircraft $aircraft onGround=$onGround but ${expectedOnGroundDescription(expectedOnGround)}"
            is RunwayKernelFailure.IncompatibleRunwayOccupancy ->
                "Runway $runway occupancy $status/$occupants is incompatible with requested operation"
            is RunwayKernelFailure.MissingActiveRunway ->
                "No active runway available for runway operation"
            is RunwayKernelFailure.MissingAircraft ->
                "Aircraft $aircraft is not tracked by the controller"
            is RunwayKernelFailure.MissingRunwayDuty ->
                "No runway-duty authority state available for runway $runway"
            is RunwayKernelFailure.MissingRunwayObservation ->
                "No runway observation available for runway $runway"
            is RunwayKernelFailure.RunwayAuthorityMismatch ->
                "Runway $runway authority is $actualAircraft/$actualOperation, " +
                    "expected $expectedAircraft/$expectedOperation"
        },
    )

private fun expectedOnGroundDescription(expectedOnGround: Boolean): String =
    if (expectedOnGround) "ground phase is required" else "airborne phase is required"

object KotlinRunwayKernel {
    fun evaluate(input: RunwayKernelInput): RunwayKernelDecision {
        val activeRunway = activeRunwayEvidence(input).let {
            when (it) {
                is PrimitiveResult.Accepted -> it.evidence
                is PrimitiveResult.Rejected -> return RunwayKernelDecision.Rejected(it.failure)
            }
        }
        val authority = authorityEvidence(input).let {
            when (it) {
                is PrimitiveResult.Accepted -> it.evidence
                is PrimitiveResult.Rejected -> return RunwayKernelDecision.Rejected(it.failure)
            }
        }
        val occupancy = occupancyEvidence(input).let {
            when (it) {
                is PrimitiveResult.Accepted -> it.evidence
                is PrimitiveResult.Rejected -> return RunwayKernelDecision.Rejected(it.failure)
            }
        }
        val phase = aircraftPhaseEvidence(input).let {
            when (it) {
                is PrimitiveResult.Accepted -> it.evidence
                is PrimitiveResult.Rejected -> return RunwayKernelDecision.Rejected(it.failure)
            }
        }
        return RunwayKernelDecision.Accepted(
            operation = input.operation,
            evidence = NonEmptyList(activeRunway, listOf(authority, occupancy, phase)),
        )
    }

    private fun activeRunwayEvidence(input: RunwayKernelInput): PrimitiveResult {
        val active = input.context.beliefs.activeRunway
            ?: return PrimitiveResult.Rejected(RunwayKernelFailure.MissingActiveRunway)
        return if (active == input.operation.runway) {
            PrimitiveResult.Accepted(RunwayKernelEvidence.ActiveRunwayMatched(active))
        } else {
            PrimitiveResult.Rejected(
                RunwayKernelFailure.ActiveRunwayMismatch(
                    instructionRunway = input.operation.runway,
                    activeRunway = active,
                ),
            )
        }
    }

    private fun authorityEvidence(input: RunwayKernelInput): PrimitiveResult {
        val duty = input.context.beliefs.runwayDuty
            ?: return PrimitiveResult.Rejected(RunwayKernelFailure.MissingRunwayDuty(input.operation.runway))
        val accepted = duty.runway == input.operation.runway &&
            duty.holder == input.operation.aircraft &&
            duty.operation == input.operation.requiredDutyOperation
        return if (accepted) {
            PrimitiveResult.Accepted(
                RunwayKernelEvidence.ControllerHeldRunwayAuthority(
                    runway = input.operation.runway,
                    aircraft = input.operation.aircraft,
                    operation = input.operation.requiredDutyOperation,
                ),
            )
        } else {
            PrimitiveResult.Rejected(
                RunwayKernelFailure.RunwayAuthorityMismatch(
                    runway = input.operation.runway,
                    expectedAircraft = input.operation.aircraft,
                    actualAircraft = duty.holder,
                    expectedOperation = input.operation.requiredDutyOperation,
                    actualOperation = duty.operation,
                ),
            )
        }
    }

    private fun occupancyEvidence(input: RunwayKernelInput): PrimitiveResult {
        val observation = input.context.beliefs.runwayBeliefs[input.operation.runway]
            ?: return PrimitiveResult.Rejected(RunwayKernelFailure.MissingRunwayObservation(input.operation.runway))
        return if (observation.isCompatibleWith(input.operation)) {
            PrimitiveResult.Accepted(
                RunwayKernelEvidence.RunwayOccupancyCompatible(
                    runway = observation.id,
                    status = observation.status,
                    occupants = observation.occupants,
                ),
            )
        } else {
            PrimitiveResult.Rejected(
                RunwayKernelFailure.IncompatibleRunwayOccupancy(
                    runway = observation.id,
                    status = observation.status,
                    occupants = observation.occupants,
                ),
            )
        }
    }

    private fun aircraftPhaseEvidence(input: RunwayKernelInput): PrimitiveResult {
        val aircraft = input.context.beliefs.trackedAircraft[input.operation.aircraft]
            ?: return PrimitiveResult.Rejected(RunwayKernelFailure.MissingAircraft(input.operation.aircraft))
        val expectedOnGround = when (input.operation) {
            is RunwayKernelOperation.LineUpAndWait,
            is RunwayKernelOperation.ClearedForTakeoff,
            -> true
            is RunwayKernelOperation.ClearedToLand -> false
        }
        return if (aircraft.onGround == expectedOnGround) {
            PrimitiveResult.Accepted(
                RunwayKernelEvidence.AircraftPhaseCompatible(
                    aircraft = input.operation.aircraft,
                    onGround = aircraft.onGround,
                ),
            )
        } else {
            PrimitiveResult.Rejected(
                RunwayKernelFailure.IncompatibleAircraftPhase(
                    aircraft = input.operation.aircraft,
                    onGround = aircraft.onGround,
                    expectedOnGround = expectedOnGround,
                ),
            )
        }
    }

    private fun RunwayObservation.isCompatibleWith(operation: RunwayKernelOperation): Boolean {
        val clear = status == RunwayStatus.CLEAR && occupants.isEmpty()
        val selfOccupied = occupants == setOf(operation.aircraft) &&
            status == when (operation) {
                is RunwayKernelOperation.LineUpAndWait,
                is RunwayKernelOperation.ClearedForTakeoff,
                -> RunwayStatus.OCCUPIED_DEPARTURE
                is RunwayKernelOperation.ClearedToLand -> RunwayStatus.OCCUPIED_LANDING
            }
        return clear || selfOccupied
    }
}

private sealed interface PrimitiveResult {
    data class Accepted(
        val evidence: RunwayKernelEvidence,
    ) : PrimitiveResult

    data class Rejected(
        val failure: RunwayKernelFailure,
    ) : PrimitiveResult
}
