package xyz.easiersaid.twr.core.clearance

import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AfterPassingLevelClimbTo
import xyz.easiersaid.twr.protocol.AfterPassingLevelDescendTo
import xyz.easiersaid.twr.protocol.ApproachComponent
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedLowApproach
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ClimbTo
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.CompletionCategory
import xyz.easiersaid.twr.protocol.DescendTo
import xyz.easiersaid.twr.protocol.ExpediteClimb
import xyz.easiersaid.twr.protocol.ExpediteDescend
import xyz.easiersaid.twr.protocol.IncreaseSpeedTo
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.MaintainAtOrAbove
import xyz.easiersaid.twr.protocol.MaintainAtOrBelow
import xyz.easiersaid.twr.protocol.MaintainAltitudeUntilEstablished
import xyz.easiersaid.twr.protocol.MaintainLevel
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.ReduceSpeedTo
import xyz.easiersaid.twr.protocol.ConfirmSquawk
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.StopClimbAt
import xyz.easiersaid.twr.protocol.StopDescentAt
import xyz.easiersaid.twr.protocol.StopSquawk
import xyz.easiersaid.twr.protocol.SquawkIdent
import xyz.easiersaid.twr.protocol.SquawkNormal
import xyz.easiersaid.twr.protocol.SquawkStandby
import xyz.easiersaid.twr.protocol.instructionDomain

data class StepCompletion(
    val step: ResolvedStep,
    val result: CompletionResult
)

data class CompletionEvaluation(
    val source: ResolvedClearance,
    val updated: ResolvedClearance,
    val stepResults: List<StepCompletion>,
    val newlyCompletedSteps: Set<Int>,
    val isComplete: Boolean
) {
    val completedSteps: Set<Int>
        get() = updated.completedSteps
}

fun evaluateCompletion(
    clearance: ResolvedClearance,
    view: CompletionView,
    suppressedDomains: Set<ClearanceDomain> = emptySet()
): CompletionEvaluation {
    val stepResults = clearance.steps.map { step ->
        val result = if (step.domain in suppressedDomains) {
            CompletionResult.NOT_APPLICABLE
        } else {
            evaluateStepCompletion(step, view)
        }
        StepCompletion(step, result)
    }
    val newlyCompletedSteps = stepResults
        .filter { stepCompletion -> stepCompletion.result == CompletionResult.COMPLETE }
        .map { stepCompletion -> stepCompletion.step.index }
        .toSet()
        .minus(clearance.completedSteps)

    val updatedSource = when (val content = clearance.source.content) {
        is ClearanceContent.Single -> {
            val result = stepResults.singleOrNull()?.result
            val nextStatus = if (result == CompletionResult.COMPLETE) {
                ClearanceStatus.COMPLETED
            } else {
                clearance.source.status
            }
            clearance.source.copy(status = nextStatus)
        }

        is ClearanceContent.Compound -> {
            val updatedContent = content.copy(
                completedSteps = content.completedSteps + newlyCompletedSteps
            )
            val isComplete = updatedContent.steps.withIndex()
                .filterNot { (index, instruction) ->
                    val domain = instructionDomain(instruction) ?: clearance.source.domain
                    domain in suppressedDomains ||
                        instruction.completionCategory() == CompletionCategory.PERSISTENT
                }
                .all { (index, _) -> index in updatedContent.completedSteps }
            val nextStatus = if (isComplete) {
                ClearanceStatus.COMPLETED
            } else {
                clearance.source.status
            }
            clearance.source.copy(
                content = updatedContent,
                status = nextStatus
            )
        }
    }
    val updatedClearance = clearance.withSource(updatedSource)

    return CompletionEvaluation(
        source = clearance,
        updated = updatedClearance,
        stepResults = stepResults,
        newlyCompletedSteps = newlyCompletedSteps,
        isComplete = updatedClearance.source.status == ClearanceStatus.COMPLETED
    )
}

@Suppress("CyclomaticComplexMethod")
private fun evaluateStepCompletion(
    step: ResolvedStep,
    view: CompletionView
): CompletionResult =
    when (step) {
        is ResolvedStep.Taxi -> if (view.position == step.route.destination) {
            CompletionResult.COMPLETE
        } else {
            CompletionResult.NOT_COMPLETE
        }

        is ResolvedStep.HoldShort -> CompletionResult.NOT_APPLICABLE

        is ResolvedStep.Crossing -> evaluateRunwayTransitionCompletion(
            runway = step.crossing.runway.id,
            view = view
        )

        is ResolvedStep.Backtrack -> if (view.position == step.farEndPoint) {
            CompletionResult.COMPLETE
        } else {
            CompletionResult.NOT_COMPLETE
        }

        is ResolvedStep.Route -> if (view.position == step.clearance.clearanceLimit.point) {
            CompletionResult.COMPLETE
        } else {
            CompletionResult.NOT_COMPLETE
        }

        is ResolvedStep.Holding -> CompletionResult.NOT_APPLICABLE

        is ResolvedStep.Approach -> evaluateGenericInstructionCompletion(step.instruction, view)

        is ResolvedStep.FrequencyChange -> {
            val radioState = view.radioState
            if (
                radioState.currentRole == step.frequency.roleName ||
                radioState.lastContactRole == step.frequency.roleName ||
                radioState.currentFrequency == step.frequency.instructedFrequency
            ) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }
        }

        is ResolvedStep.DirectFix -> if (view.position == step.fix.point) {
            CompletionResult.COMPLETE
        } else {
            CompletionResult.NOT_COMPLETE
        }

        is ResolvedStep.AirwayJoin -> if (view.position == step.joinFix.point) {
            CompletionResult.COMPLETE
        } else {
            CompletionResult.NOT_COMPLETE
        }

        is ResolvedStep.CircuitJoinStep -> {
            val circuitRef = EntityRef.CircuitProcedureRef(step.circuit.id)
            if (circuitRef in view.entities && view.altitude == step.circuit.altitude) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }
        }

        is ResolvedStep.Plain -> evaluateGenericInstructionCompletion(step.instruction, view)
    }

@Suppress("LongMethod")
private fun evaluateGenericInstructionCompletion(
    instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
    view: CompletionView
): CompletionResult {
    val completionCategory = instruction.completionCategory()
    return when (completionCategory) {
        CompletionCategory.ON_ACTIVATION -> CompletionResult.COMPLETE
        CompletionCategory.PERSISTENT -> CompletionResult.NOT_APPLICABLE
        CompletionCategory.EXTERNAL_EVENT -> CompletionResult.NOT_COMPLETE

        CompletionCategory.SELF_COMPLETING -> when (instruction) {
            is ClimbTo -> if (view.altitude.isAtOrAbove(instruction.level)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is ExpediteClimb -> if (view.altitude.isAtOrAbove(instruction.level)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is DescendTo -> if (view.altitude.isAtOrBelow(instruction.level)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is ExpediteDescend -> if (view.altitude.isAtOrBelow(instruction.level)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is MaintainLevel -> if (view.altitude.matches(instruction.level)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is StopClimbAt -> if (view.altitude.matches(instruction.level)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is StopDescentAt -> if (view.altitude.matches(instruction.level)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is MaintainAtOrAbove -> if (view.altitude.isAtOrAbove(instruction.minimumLevel)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is MaintainAtOrBelow -> if (view.altitude.isAtOrBelow(instruction.maximumLevel)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is AfterPassingLevelClimbTo -> if (view.altitude.isAtOrAbove(instruction.climbTo)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is AfterPassingLevelDescendTo -> if (view.altitude.isAtOrBelow(instruction.descendTo)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is MaintainAltitudeUntilEstablished -> if (instruction.on in view.establishedApproachComponents) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is MaintainSpeed -> if (view.speed.matches(instruction.speed)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is ReduceSpeedTo -> if (view.speed.isAtOrBelow(instruction.speed)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is IncreaseSpeedTo -> if (view.speed.isAtOrAbove(instruction.speed)) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is ConfirmSquawk -> if (view.transponderCode == instruction.squawk) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is SquawkIdent -> if (view.transponderIdentActive) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is SquawkStandby -> if (view.transponderMode == xyz.easiersaid.twr.protocol.TransponderMode.STANDBY) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is SquawkNormal -> if (view.transponderMode == instruction.mode) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is StopSquawk -> if (view.transponderMode != instruction.mode) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is ClearedForTakeoff -> if (!view.onGround) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            is ClearedToLand -> evaluateRunwayTransitionCompletion(instruction.runway, view)

            is ClearedLowApproach -> {
                val runwayRef = EntityRef.RunwayRef(instruction.runway)
                if (!view.onGround && runwayRef in view.transitionHistory && runwayRef !in view.entities) {
                    CompletionResult.COMPLETE
                } else {
                    CompletionResult.NOT_COMPLETE
                }
            }

            is ClearedTouchAndGo -> {
                val runwayRef = EntityRef.RunwayRef(instruction.runway)
                if (!view.onGround && runwayRef in view.transitionHistory) {
                    CompletionResult.COMPLETE
                } else {
                    CompletionResult.NOT_COMPLETE
                }
            }

            is BacktrackRunway -> {
                CompletionResult.NOT_COMPLETE
            }

            is JoinCircuit -> CompletionResult.NOT_COMPLETE

            is AfterLandingVacateVia -> if (view.position == instruction.exit) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }

            else -> CompletionResult.NOT_COMPLETE
        }

        null -> CompletionResult.NOT_COMPLETE
    }
}

private fun evaluateRunwayTransitionCompletion(
    runway: xyz.easiersaid.twr.protocol.RunwayId,
    view: CompletionView
): CompletionResult {
    val runwayRef = EntityRef.RunwayRef(runway)
    return if (runwayRef in view.transitionHistory && runwayRef !in view.entities) {
        CompletionResult.COMPLETE
    } else {
        CompletionResult.NOT_COMPLETE
    }
}

private fun Level?.isAtOrAbove(target: Level): Boolean =
    comparableFeetOrNull(this)?.let { current ->
        comparableFeet(target)?.let { targetFeet -> current >= targetFeet }
    } ?: false

private fun Level?.isAtOrBelow(target: Level): Boolean =
    comparableFeetOrNull(this)?.let { current ->
        comparableFeet(target)?.let { targetFeet -> current <= targetFeet }
    } ?: false

private fun Level?.matches(target: Level): Boolean =
    comparableFeetOrNull(this)?.let { current ->
        comparableFeet(target)?.let { targetFeet -> current == targetFeet }
    } ?: false

private fun Speed?.matches(target: Speed): Boolean =
    comparableSpeedAgainst(this, target)?.let { (current, targetValue) -> current == targetValue } ?: false

private fun Speed?.isAtOrBelow(target: Speed): Boolean =
    comparableSpeedAgainst(this, target)?.let { (current, targetValue) -> current <= targetValue } ?: false

private fun Speed?.isAtOrAbove(target: Speed): Boolean =
    comparableSpeedAgainst(this, target)?.let { (current, targetValue) -> current >= targetValue } ?: false

private fun comparableFeet(level: Level): Int? =
    when (level) {
        is Level.FlightLevel -> level.fl * 100
        is Level.AltitudeFeet -> level.feet
        is Level.HeightFeet -> level.feet
    }

private fun comparableFeetOrNull(level: Level?): Int? =
    level?.let(::comparableFeet)

private fun comparableSpeed(speed: Speed): Double =
    when (speed) {
        is Speed.InKnots -> speed.knots.value.toDouble()
        is Speed.InMach -> speed.mach.value
    }

private fun comparableSpeedAgainst(current: Speed?, target: Speed): Pair<Double, Double>? =
    when {
        current == null -> null
        current is Speed.InKnots && target is Speed.InKnots ->
            comparableSpeed(current) to comparableSpeed(target)
        current is Speed.InMach && target is Speed.InMach ->
            comparableSpeed(current) to comparableSpeed(target)
        else -> null
    }

private fun xyz.easiersaid.twr.protocol.AtcInstruction.completionCategory(): CompletionCategory? =
    xyz.easiersaid.twr.protocol.instructionCompletionCategory(this)
