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
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
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
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.ReduceSpeedTo
import xyz.easiersaid.twr.protocol.ConfirmSquawk
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SpecialVfrClearance
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.StopClimbAt
import xyz.easiersaid.twr.protocol.StopDescentAt
import xyz.easiersaid.twr.protocol.StopSquawk
import xyz.easiersaid.twr.protocol.SquawkIdent
import xyz.easiersaid.twr.protocol.SquawkNormal
import xyz.easiersaid.twr.protocol.SquawkStandby
import xyz.easiersaid.twr.protocol.AvoidLevel
import xyz.easiersaid.twr.core.resolution.ResolvedVectorInstruction
import xyz.easiersaid.twr.core.resolution.ResolvedVectorKind
import xyz.easiersaid.twr.protocol.DescendWhenReady
import xyz.easiersaid.twr.protocol.InterceptLocaliser
import xyz.easiersaid.twr.protocol.TurnByDegrees
import xyz.easiersaid.twr.protocol.VacateRunway
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

private fun evaluateStepCompletion(
    step: ResolvedStep,
    view: CompletionView
): CompletionResult =
    when (step) {
        is ResolvedStep.Taxi -> if (groundPointReached(step.route.destination, view)) {
            CompletionResult.COMPLETE
        } else {
            CompletionResult.NOT_COMPLETE
        }

        is ResolvedStep.HoldShort -> CompletionResult.NOT_APPLICABLE

        is ResolvedStep.Crossing -> evaluateRunwayTransitionCompletion(
            runway = step.crossing.runway.id,
            crossed = runwayCrossedOnGround(
                runway = step.crossing.runway.id,
                crossingPoint = step.crossing.crossingPoint,
                view = view
            ),
            view = view
        )

        is ResolvedStep.Backtrack -> if (groundPointReached(step.farEndPoint, view)) {
            CompletionResult.COMPLETE
        } else {
            CompletionResult.NOT_COMPLETE
        }

        is ResolvedStep.Route -> evaluateRouteCompletion(step, view)

        is ResolvedStep.Holding -> CompletionResult.NOT_APPLICABLE

        is ResolvedStep.Approach -> evaluateApproachCompletion(step, view)

        is ResolvedStep.Airspace -> evaluateAirspaceCompletion(step, view)

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
            val circuitRef = EntityRef.CircuitProcedureRef(step.join.circuit.id)
            if (circuitRef in view.entities && view.altitude == step.join.circuit.altitude) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_COMPLETE
            }
        }

        is ResolvedStep.Vector -> evaluateVectorCompletion(step.vector, view)

        is ResolvedStep.Plain -> evaluateGenericInstructionCompletion(step.instruction, view)
    }

private fun groundPointReached(
    point: PointId,
    view: CompletionView
): Boolean =
    view.position == point ||
        point in view.groundProgress.traversedPoints ||
        point in view.groundProgress.reachedHoldingPoints

private fun runwayCrossedOnGround(
    runway: RunwayId,
    crossingPoint: PointId,
    view: CompletionView
): Boolean =
    runway in view.groundProgress.crossedRunways ||
        groundPointReached(crossingPoint, view)

private fun evaluateAirspaceCompletion(
    step: ResolvedStep.Airspace,
    view: CompletionView
): CompletionResult {
    val observation = observeAirspaceState(step, view)

    return when (step.instruction) {
        is RemainOutsideControlledAirspace ->
            if (observation.inside || observation.entered) {
                CompletionResult.NOT_COMPLETE
            } else {
                CompletionResult.NOT_APPLICABLE
            }

        is ClearedToEnterControlZone,
        is SpecialVfrClearance ->
            if (observation.exited || observation.landed) {
                CompletionResult.COMPLETE
            } else {
                CompletionResult.NOT_APPLICABLE
            }

        else -> CompletionResult.NOT_COMPLETE
    }
}

private data class AirspaceObservationState(
    val inside: Boolean,
    val entered: Boolean,
    val exited: Boolean,
    val landed: Boolean
)

private fun observeAirspaceState(
    step: ResolvedStep.Airspace,
    view: CompletionView
): AirspaceObservationState {
    val airspaceRef = EntityRef.AirspaceVolumeRef(step.airspace.airspace.id)
    val inside =
        airspaceRef in view.entities || view.position in step.airspace.airspace.points
    val transitioned = airspaceRef in view.transitionHistory
    return AirspaceObservationState(
        inside = inside,
        entered = transitioned && inside,
        exited = transitioned && !inside,
        landed = view.onGround
    )
}

private fun evaluateRouteCompletion(
    step: ResolvedStep.Route,
    view: CompletionView
): CompletionResult {
    val limitFixRef = EntityRef.FixRef(step.clearance.clearanceLimit.id)
    val holdingPatternSatisfied = step.clearance.clearanceLimitHoldingPattern?.let { holdingPattern ->
        EntityRef.HoldingPatternRef(holdingPattern.id) in view.entities
    } ?: false
    return completionOf(
        view.position == step.clearance.clearanceLimit.point ||
            limitFixRef in view.entities ||
            step.clearance.clearanceLimit.id in view.reachedFixes ||
            holdingPatternSatisfied
    )
}

private fun evaluateApproachCompletion(
    step: ResolvedStep.Approach,
    view: CompletionView
): CompletionResult {
    val runwayRef = EntityRef.RunwayRef(step.approach.approach.runway)
    val missedApproachHoldRef =
        EntityRef.HoldingPatternRef(step.approach.missedApproachHoldingPattern.id)
    val landedOnApproachRunway =
        view.onGround && (runwayRef in view.transitionHistory || runwayRef in view.entities)
    return completionOf(
        landedOnApproachRunway || missedApproachHoldRef in view.entities
    )
}

private fun evaluateGenericInstructionCompletion(
    instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
    view: CompletionView
): CompletionResult =
    when (instruction.completionCategory()) {
        CompletionCategory.ON_ACTIVATION -> CompletionResult.COMPLETE
        CompletionCategory.PERSISTENT -> evaluatePersistentConstraint(instruction, view)
        CompletionCategory.EXTERNAL_EVENT -> CompletionResult.NOT_COMPLETE
        CompletionCategory.SELF_COMPLETING -> evaluateSelfCompletingInstruction(instruction, view)
        null -> CompletionResult.NOT_COMPLETE
    }

// Persistent constraints are not "completed" in the compound clearance sense — they remain in
// force until superseded. But we still evaluate whether the aircraft satisfies the constraint,
// returning NOT_APPLICABLE for pure holds/orbits and delegating to the same evaluation logic
// for maintain-level/maintain-speed constraints that have observable compliance.
private fun evaluatePersistentConstraint(
    instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
    view: CompletionView
): CompletionResult =
    when (instruction) {
        is MaintainLevel -> evaluateLevelCompletion(instruction, view)
        is MaintainAtOrAbove -> evaluateLevelCompletion(instruction, view)
        is MaintainAtOrBelow -> evaluateLevelCompletion(instruction, view)
        is MaintainSpeed -> evaluateSpeedCompletion(instruction, view)
        is AvoidLevel -> evaluateLevelCompletion(instruction, view)
        else -> CompletionResult.NOT_APPLICABLE
    }

private fun evaluateSelfCompletingInstruction(
    instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
    view: CompletionView
): CompletionResult =
    when (instruction) {
        is ClimbTo,
        is ExpediteClimb,
        is DescendTo,
        is DescendWhenReady,
        is ExpediteDescend,
        is StopClimbAt,
        is StopDescentAt,
        is AfterPassingLevelClimbTo,
        is AfterPassingLevelDescendTo,
        is MaintainAltitudeUntilEstablished -> evaluateLevelCompletion(instruction, view)
        is ReduceSpeedTo,
        is IncreaseSpeedTo -> evaluateSpeedCompletion(instruction, view)
        is TurnByDegrees -> evaluateTurnByDegreesCompletion(instruction, view)
        is ConfirmSquawk,
        is SquawkIdent,
        is SquawkStandby,
        is SquawkNormal,
        is StopSquawk -> evaluateSquawkCompletion(instruction, view)
        is ClearedForTakeoff -> completionOf(!view.onGround)
        is ClearedToLand -> evaluateRunwayTransitionCompletion(instruction.runway, view = view)
        is ClearedLowApproach -> evaluateLowApproachCompletion(instruction, view)
        is ClearedTouchAndGo -> evaluateTouchAndGoCompletion(instruction, view)
        is BacktrackRunway -> CompletionResult.NOT_COMPLETE
        is JoinCircuit -> CompletionResult.NOT_COMPLETE
        is AfterLandingVacateVia -> completionOf(view.position == instruction.exit)
        // D: InterceptLocaliser completes when localiser captured
        is InterceptLocaliser -> completionOf(ApproachComponent.LOCALISER in view.establishedApproachComponents)
        // K: VacateRunway completes when aircraft is no longer on any runway
        is VacateRunway -> completionOf(view.entities.none { it is EntityRef.RunwayRef })
        else -> CompletionResult.NOT_COMPLETE
    }

private fun evaluateLevelCompletion(
    instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
    view: CompletionView
): CompletionResult =
    when (instruction) {
        is ClimbTo -> completionOf(view.altitude.isAtOrAbove(instruction.level))
        is ExpediteClimb -> completionOf(view.altitude.isAtOrAbove(instruction.level))
        is DescendTo -> completionOf(view.altitude.isAtOrBelow(instruction.level))
        is DescendWhenReady -> completionOf(view.altitude.isAtOrBelow(instruction.level))
        is ExpediteDescend -> completionOf(view.altitude.isAtOrBelow(instruction.level))
        is MaintainLevel -> completionOf(view.altitude.matches(instruction.level))
        is StopClimbAt -> completionOf(view.altitude.matches(instruction.level))
        is StopDescentAt -> completionOf(view.altitude.matches(instruction.level))
        is MaintainAtOrAbove -> completionOf(view.altitude.isAtOrAbove(instruction.minimumLevel))
        is MaintainAtOrBelow -> completionOf(view.altitude.isAtOrBelow(instruction.maximumLevel))
        is AfterPassingLevelClimbTo -> completionOf(view.altitude.isAtOrAbove(instruction.climbTo))
        is AfterPassingLevelDescendTo -> completionOf(view.altitude.isAtOrBelow(instruction.descendTo))
        is MaintainAltitudeUntilEstablished ->
            completionOf(instruction.on in view.establishedApproachComponents)
        is AvoidLevel -> completionOf(!view.altitude.matches(instruction.level))
        else -> CompletionResult.NOT_COMPLETE
    }

private fun evaluateSpeedCompletion(
    instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
    view: CompletionView
): CompletionResult =
    when (instruction) {
        is MaintainSpeed -> completionOf(view.speed.matches(instruction.speed))
        is ReduceSpeedTo -> completionOf(view.speed.isAtOrBelow(instruction.speed))
        is IncreaseSpeedTo -> completionOf(view.speed.isAtOrAbove(instruction.speed))
        else -> CompletionResult.NOT_COMPLETE
    }

private fun evaluateSquawkCompletion(
    instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
    view: CompletionView
): CompletionResult =
    when (instruction) {
        is ConfirmSquawk -> completionOf(view.transponderCode == instruction.squawk)
        is SquawkIdent -> completionOf(view.transponderIdentActive)
        is SquawkStandby ->
            completionOf(view.transponderMode == xyz.easiersaid.twr.protocol.TransponderMode.STANDBY)
        is SquawkNormal -> completionOf(view.transponderMode == instruction.mode)
        is StopSquawk -> completionOf(view.transponderMode != instruction.mode)
        else -> CompletionResult.NOT_COMPLETE
    }

private fun evaluateTurnByDegreesCompletion(
    instruction: TurnByDegrees,
    view: CompletionView
): CompletionResult =
    completionOf(
        view.observedTurnDirection == instruction.direction &&
            (view.observedTurnDegrees ?: 0) >= instruction.degrees
    )

private fun evaluateVectorCompletion(
    instruction: ResolvedVectorInstruction,
    view: CompletionView
): CompletionResult =
    when (instruction.kind) {
        ResolvedVectorKind.TURN_BY_DEGREES ->
            completionOf(
                view.observedTurnDirection == instruction.turnDirection &&
                    (view.observedTurnDegrees ?: 0) >= (instruction.turnDegrees ?: Int.MAX_VALUE)
            )

        ResolvedVectorKind.FLY_HEADING,
        ResolvedVectorKind.TURN_HEADING,
        ResolvedVectorKind.CONTINUE_PRESENT_HEADING -> CompletionResult.NOT_APPLICABLE
    }

private fun evaluateLowApproachCompletion(
    instruction: ClearedLowApproach,
    view: CompletionView
): CompletionResult {
    val runwayRef = EntityRef.RunwayRef(instruction.runway)
    return completionOf(
        !view.onGround && runwayRef in view.transitionHistory && runwayRef !in view.entities
    )
}

private fun evaluateTouchAndGoCompletion(
    instruction: ClearedTouchAndGo,
    view: CompletionView
): CompletionResult {
    val runwayRef = EntityRef.RunwayRef(instruction.runway)
    return completionOf(!view.onGround && runwayRef in view.transitionHistory)
}

private fun completionOf(isComplete: Boolean): CompletionResult =
    if (isComplete) CompletionResult.COMPLETE else CompletionResult.NOT_COMPLETE

private fun evaluateRunwayTransitionCompletion(
    runway: xyz.easiersaid.twr.protocol.RunwayId,
    crossed: Boolean = false,
    view: CompletionView
): CompletionResult {
    val runwayRef = EntityRef.RunwayRef(runway)
    return if (
        crossed ||
        (runwayRef in view.transitionHistory && runwayRef !in view.entities)
    ) {
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

// Converts levels to a comparable integer in feet. Flight levels are converted by FL * 100,
// which assumes standard pressure (1013.25 hPa). This is a pragmatic simplification: in
// non-standard pressure conditions FL100 != 10,000 ft QNH. For correct evaluation near the
// transition altitude, the CompletionView would need a current pressure setting. Acceptable
// for simulation purposes but not for real ATC safety systems.
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
