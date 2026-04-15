package xyz.easiersaid.twr.core.clearance

import xyz.easiersaid.twr.core.resolution.AerodromeResolutionContext
import xyz.easiersaid.twr.core.resolution.GroundResolutionContext
import xyz.easiersaid.twr.core.resolution.ResolutionFailure
import xyz.easiersaid.twr.core.resolution.ResolutionFailureCode
import xyz.easiersaid.twr.core.resolution.ResolutionResult
import xyz.easiersaid.twr.core.resolution.ResolvedAirspaceInstruction
import xyz.easiersaid.twr.core.resolution.ResolvedHoldingPoint
import xyz.easiersaid.twr.core.resolution.ResolvedRunwayCrossing
import xyz.easiersaid.twr.core.resolution.ResolvedTaxiRoute
import xyz.easiersaid.twr.core.resolution.resolveClearedApproach
import xyz.easiersaid.twr.core.resolution.resolveClearedToEnterControlZone
import xyz.easiersaid.twr.core.resolution.resolveClearedTo
import xyz.easiersaid.twr.core.resolution.resolveContactFrequency
import xyz.easiersaid.twr.core.resolution.resolveCrossRunway
import xyz.easiersaid.twr.core.resolution.resolveHoldAt
import xyz.easiersaid.twr.core.resolution.resolveHoldShortOf
import xyz.easiersaid.twr.core.resolution.resolveMonitorFrequency
import xyz.easiersaid.twr.core.resolution.resolveRemainOutsideControlledAirspace
import xyz.easiersaid.twr.core.resolution.resolveSpecialVfrClearance
import xyz.easiersaid.twr.core.resolution.resolveTaxiTo
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.HoldingPoint
import xyz.easiersaid.twr.core.world.Taxiway
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ClearedVisualApproach
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.CompletionCategory
import xyz.easiersaid.twr.protocol.ConditionalClearance
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.HoldAt
import xyz.easiersaid.twr.protocol.InstructionTiming
import xyz.easiersaid.twr.protocol.JoinAirway
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.LeaveHoldProceedDirect
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.ProceedDirect
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.RejoinSidAt
import xyz.easiersaid.twr.protocol.SpecialVfrClearance
import xyz.easiersaid.twr.protocol.TaxiTo
import xyz.easiersaid.twr.protocol.WhenAbleProceedDirect
import xyz.easiersaid.twr.protocol.instructionCompletionCategory
import xyz.easiersaid.twr.protocol.instructionDomain
import xyz.easiersaid.twr.protocol.instructionMayBeConditional
import xyz.easiersaid.twr.protocol.instructionTiming

fun AviationWorld.resolveClearance(
    context: ClearanceResolutionContext,
    clearance: StructuredClearance
): ResolutionResult<ResolvedClearance> {
    val normalizedClearance = when (val normalized = clearance.normalizeConditionalEnvelope()) {
        is arrow.core.Either.Left -> return normalized
        is arrow.core.Either.Right -> normalized.value
    }

    val steps = when (val content = normalizedClearance.content) {
        is ClearanceContent.Single -> listOf(content.instruction)
        is ClearanceContent.Compound -> content.steps
    }

    val initialState = ResolutionCompilationState(currentPoint = context.currentPoint)

    return arrow.core.raise.either {
        val compiled = steps.foldIndexed(Pair(emptyList<ResolvedStep>(), initialState)) { index, (resolvedSoFar, state), instruction ->
            val result = resolveStep(context, normalizedClearance, index, instruction, state).bind()
            Pair(resolvedSoFar + result.step, result.state)
        }
        ResolvedClearance(
            source = normalizedClearance,
            steps = compiled.first
        )
    }
}

private fun StructuredClearance.normalizeConditionalEnvelope(): ResolutionResult<StructuredClearance> =
    when (val content = content) {
        is ClearanceContent.Single -> normalizeSingleConditional(content)
        is ClearanceContent.Compound -> normalizeCompoundConditional(content)
    }

private fun StructuredClearance.normalizeSingleConditional(
    content: ClearanceContent.Single
): ResolutionResult<StructuredClearance> {
    val normalizedClearance = when (val instruction = content.instruction) {
        is ConditionalClearance -> {
            if (condition != null && condition != instruction.condition) {
                return unresolved(
                    ResolutionFailureCode.MULTIPLE_CONDITIONS_NOT_SUPPORTED,
                    "Clearance ${id.value} carries multiple conditional predicates"
                )
            }
            if (!instructionMayBeConditional(instruction.instruction)) {
                return unresolved(
                    ResolutionFailureCode.CONDITIONAL_INSTRUCTION_NOT_ALLOWED,
                    "Conditional clearances may only wrap supported surface instructions"
                )
            }
            copy(
                content = ClearanceContent.Single(instruction.instruction),
                condition = condition ?: instruction.condition
            )
        }

        else -> this
    }

    val unwrappedInstruction = when (val normalizedContent = normalizedClearance.content) {
        is ClearanceContent.Single -> normalizedContent.instruction
        is ClearanceContent.Compound -> error("Single conditional normalization must produce single content")
    }
    if (normalizedClearance.condition != null && !instructionMayBeConditional(unwrappedInstruction)) {
        return unresolved(
            ResolutionFailureCode.CONDITIONAL_INSTRUCTION_NOT_ALLOWED,
            "Conditional clearances may only wrap supported surface instructions"
        )
    }

    return arrow.core.Either.Right(normalizedClearance)
}

private fun StructuredClearance.normalizeCompoundConditional(
    content: ClearanceContent.Compound
): ResolutionResult<StructuredClearance> {
    val wrappedStepIndex = content.steps.indexOfFirst { step -> step is ConditionalClearance }
    if (wrappedStepIndex != -1) {
        return unresolved(
            ResolutionFailureCode.CONDITIONAL_STEP_NOT_SUPPORTED,
            "Conditional step ${wrappedStepIndex + 1} in clearance ${id.value} is not supported; split the clearance envelope instead"
        )
    }
    if (condition != null) {
        val invalidStep = content.steps.firstOrNull { step -> !instructionMayBeConditional(step) }
        if (invalidStep != null) {
            return unresolved(
                ResolutionFailureCode.CONDITIONAL_INSTRUCTION_NOT_ALLOWED,
                "Conditional compound clearances may only contain supported surface instructions"
            )
        }
    }
    return arrow.core.Either.Right(this)
}

private fun AviationWorld.resolveStep(
    context: ClearanceResolutionContext,
    clearance: StructuredClearance,
    index: Int,
    instruction: AtcInstruction,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> {
    val timing = instructionTiming(instruction)
    val domain = instructionDomain(instruction) ?: clearance.domain
    val completionCategory = instructionCompletionCategory(instruction)
    val stepContext = StepContext(
        index = index,
        instruction = instruction,
        timing = timing,
        domain = domain,
        completionCategory = completionCategory
    )

    return when (instruction) {
        is TaxiTo -> resolveTaxiStep(context, stepContext, instruction, state)
        is HoldShortOf -> resolveHoldShortStep(context, stepContext, instruction, state)
        is CrossRunway -> resolveCrossingStep(context, stepContext, instruction, state)
        is BacktrackRunway -> resolveBacktrackStep(context, stepContext, instruction, state)
        is ClearedTo -> resolveRouteStep(context, stepContext, instruction, state)
        is HoldAt -> resolveHoldingStep(context, stepContext, instruction, state)
        is ClearedApproach -> resolveApproachStep(context, stepContext, instruction, state)
        is ClearedVisualApproach -> resolveVisualApproachStep(context, stepContext, instruction, state)
        is RemainOutsideControlledAirspace -> resolveAirspaceStep(context, stepContext, instruction, state)
        is ClearedToEnterControlZone -> resolveAirspaceStep(context, stepContext, instruction, state)
        is SpecialVfrClearance -> resolveAirspaceStep(context, stepContext, instruction, state)
        is ContactFrequency -> resolveContactFrequencyStep(context, stepContext, instruction, state)
        is MonitorFrequency -> resolveMonitorFrequencyStep(context, stepContext, instruction, state)
        is ProceedDirect -> resolveDirectFixStep(stepContext, instruction.fix, state)
        is LeaveHoldProceedDirect -> resolveDirectFixStep(stepContext, instruction.fix, state)
        is WhenAbleProceedDirect -> resolveDirectFixStep(stepContext, instruction.fix, state)
        is RejoinSidAt -> resolveDirectFixStep(stepContext, instruction.fix, state)
        is JoinAirway -> resolveJoinAirwayStep(stepContext, instruction, state)
        is JoinCircuit -> resolveJoinCircuitStep(context, stepContext, instruction, state)
        else -> arrow.core.Either.Right(
            ResolvedStepWithState(
                step = ResolvedStep.Plain(
                    index = stepContext.index,
                    instruction = instruction,
                    timing = stepContext.timing,
                    domain = stepContext.domain,
                    completionCategory = stepContext.completionCategory
                ),
                state = state
            )
        )
    }
}

private fun AviationWorld.resolveAirspaceStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: AtcInstruction,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> {
    val resolvedAirspace = when (instruction) {
        is RemainOutsideControlledAirspace -> when (
            val result = resolveRemainOutsideControlledAirspace(
                context = AerodromeResolutionContext(context.aerodromeId),
                instruction = instruction
            )
        ) {
            is arrow.core.Either.Left -> return result
            is arrow.core.Either.Right -> result.value
        }

        is ClearedToEnterControlZone -> when (
            val result = resolveClearedToEnterControlZone(
                context = AerodromeResolutionContext(context.aerodromeId),
                instruction = instruction
            )
        ) {
            is arrow.core.Either.Left -> return result
            is arrow.core.Either.Right -> result.value
        }

        is SpecialVfrClearance -> when (
            val result = resolveSpecialVfrClearance(
                context = AerodromeResolutionContext(context.aerodromeId),
                instruction = instruction
            )
        ) {
            is arrow.core.Either.Left -> return result
            is arrow.core.Either.Right -> result.value
        }

        else -> error("resolveAirspaceStep called for non-airspace instruction $instruction")
    }

    return arrow.core.Either.Right(
        ResolvedStepWithState(
            step = ResolvedStep.Airspace(
                index = stepContext.index,
                instruction = instruction,
                timing = stepContext.timing,
                domain = stepContext.domain,
                completionCategory = stepContext.completionCategory,
                airspace = resolvedAirspace
            ),
            state = state
        )
    )
}

private fun AviationWorld.resolveTaxiStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: TaxiTo,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> {
    val currentPoint = state.currentPoint ?: return unresolved(
        ResolutionFailureCode.MISSING_CURRENT_POINT,
        "Taxi resolution requires a current point at aerodrome ${context.aerodromeId.value}"
    )

    val resolvedTaxi = when (
        val result = resolveTaxiTo(
            context = GroundResolutionContext(context.aerodromeId, currentPoint),
            instruction = instruction
        )
    ) {
        is arrow.core.Either.Left -> return result
        is arrow.core.Either.Right -> result.value
    }

    return arrow.core.Either.Right(
        ResolvedStepWithState(
            step = ResolvedStep.Taxi(
                index = stepContext.index,
                instruction = instruction,
                timing = stepContext.timing,
                domain = stepContext.domain,
                completionCategory = stepContext.completionCategory,
                route = resolvedTaxi
            ),
            state = state.copy(
                currentPoint = resolvedTaxi.destination,
                activeTaxiRoute = ActiveTaxiRoute(resolvedTaxi, cursorIndex = 0)
            )
        )
    )
}

private fun AviationWorld.resolveHoldShortStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: HoldShortOf,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> {
    val resolvedHoldingPoint = state.activeTaxiRoute?.let { activeRoute ->
        resolveHoldingPointOnActiveTaxiRoute(context.aerodromeId, instruction.runway, activeRoute)
    } ?: run {
        val currentPoint = state.currentPoint ?: return unresolved(
            ResolutionFailureCode.MISSING_CURRENT_POINT,
            "Hold-short resolution requires a current point at aerodrome ${context.aerodromeId.value}"
        )
        when (val result = resolveHoldShortOf(
            context = GroundResolutionContext(context.aerodromeId, currentPoint),
            instruction = instruction
        )) {
            is arrow.core.Either.Left -> return result
            is arrow.core.Either.Right -> arrow.core.Either.Right(
                RouteLocatedHoldingPoint(
                    resolved = result.value,
                    routeIndex = state.activeTaxiRoute?.cursorIndex ?: -1
                )
            )
        }
    }

    return when (resolvedHoldingPoint) {
        is arrow.core.Either.Left -> resolvedHoldingPoint
        is arrow.core.Either.Right -> arrow.core.Either.Right(
            ResolvedStepWithState(
                step = ResolvedStep.HoldShort(
                    index = stepContext.index,
                    instruction = instruction,
                    timing = stepContext.timing,
                    domain = stepContext.domain,
                    completionCategory = stepContext.completionCategory,
                    holdingPoint = resolvedHoldingPoint.value.resolved
                ),
                state = state.copy(
                    currentPoint = resolvedHoldingPoint.value.resolved.holdingPoint.point,
                    activeTaxiRoute = state.activeTaxiRoute?.copy(cursorIndex = resolvedHoldingPoint.value.routeIndex)
                )
            )
        )
    }
}

private fun AviationWorld.resolveCrossingStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: CrossRunway,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> {
    val resolvedCrossing = state.activeTaxiRoute?.let { activeRoute ->
        resolveCrossingOnActiveTaxiRoute(context.aerodromeId, instruction.runway, activeRoute)
    } ?: run {
        val currentPoint = state.currentPoint ?: return unresolved(
            ResolutionFailureCode.MISSING_CURRENT_POINT,
            "Runway-crossing resolution requires a current point at aerodrome ${context.aerodromeId.value}"
        )
        when (val result = resolveCrossRunway(
            context = GroundResolutionContext(context.aerodromeId, currentPoint),
            instruction = instruction
        )) {
            is arrow.core.Either.Left -> return result
            is arrow.core.Either.Right -> arrow.core.Either.Right(
                RouteLocatedCrossing(
                    resolved = result.value,
                    routeIndex = state.activeTaxiRoute?.cursorIndex ?: -1
                )
            )
        }
    }

    return when (resolvedCrossing) {
        is arrow.core.Either.Left -> resolvedCrossing
        is arrow.core.Either.Right -> arrow.core.Either.Right(
            ResolvedStepWithState(
                step = ResolvedStep.Crossing(
                    index = stepContext.index,
                    instruction = instruction,
                    timing = stepContext.timing,
                    domain = stepContext.domain,
                    completionCategory = stepContext.completionCategory,
                    crossing = resolvedCrossing.value.resolved
                ),
                state = state.copy(
                    currentPoint = resolvedCrossing.value.resolved.crossingPoint,
                    activeTaxiRoute = state.activeTaxiRoute?.copy(cursorIndex = resolvedCrossing.value.routeIndex)
                )
            )
        )
    }
}

private fun AviationWorld.resolveRouteStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: ClearedTo,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> =
    when (val result = resolveClearedTo(AerodromeResolutionContext(context.aerodromeId), instruction)) {
        is arrow.core.Either.Left -> result
        is arrow.core.Either.Right -> arrow.core.Either.Right(
            ResolvedStepWithState(
                step = ResolvedStep.Route(
                    index = stepContext.index,
                    instruction = instruction,
                    timing = stepContext.timing,
                    domain = stepContext.domain,
                    completionCategory = stepContext.completionCategory,
                    clearance = result.value
                ),
                state = state
            )
        )
    }

private fun AviationWorld.resolveBacktrackStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: BacktrackRunway,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> {
    val aerodrome = aerodromes[context.aerodromeId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val runway = aerodrome.runways[instruction.runway] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_RUNWAY,
        "Unknown runway ${instruction.runway.value} at aerodrome ${aerodrome.icao.value}"
    )

    return arrow.core.Either.Right(
        ResolvedStepWithState(
            step = ResolvedStep.Backtrack(
                index = stepContext.index,
                instruction = instruction,
                timing = stepContext.timing,
                domain = stepContext.domain,
                completionCategory = stepContext.completionCategory,
                runway = runway,
                farEndPoint = instruction.vacateAt ?: runway.path.points.last()
            ),
            state = state
        )
    )
}

private fun AviationWorld.resolveHoldingStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: HoldAt,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> =
    when (val result = resolveHoldAt(AerodromeResolutionContext(context.aerodromeId), instruction)) {
        is arrow.core.Either.Left -> result
        is arrow.core.Either.Right -> arrow.core.Either.Right(
            ResolvedStepWithState(
                step = ResolvedStep.Holding(
                    index = stepContext.index,
                    instruction = instruction,
                    timing = stepContext.timing,
                    domain = stepContext.domain,
                    completionCategory = stepContext.completionCategory,
                    holding = result.value
                ),
                state = state
            )
        )
    }

private fun AviationWorld.resolveApproachStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: ClearedApproach,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> =
    when (val result = resolveClearedApproach(AerodromeResolutionContext(context.aerodromeId), instruction)) {
        is arrow.core.Either.Left -> result
        is arrow.core.Either.Right -> arrow.core.Either.Right(
            ResolvedStepWithState(
                step = ResolvedStep.Approach(
                    index = stepContext.index,
                    instruction = instruction,
                    timing = stepContext.timing,
                    domain = stepContext.domain,
                    completionCategory = stepContext.completionCategory,
                    approach = result.value
                ),
                state = state
            )
        )
    }

private fun AviationWorld.resolveContactFrequencyStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: ContactFrequency,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> =
    when (val result = resolveContactFrequency(AerodromeResolutionContext(context.aerodromeId), instruction)) {
        is arrow.core.Either.Left -> result
        is arrow.core.Either.Right -> arrow.core.Either.Right(
            ResolvedStepWithState(
                step = ResolvedStep.FrequencyChange(
                    index = stepContext.index,
                    instruction = instruction,
                    timing = stepContext.timing,
                    domain = stepContext.domain,
                    completionCategory = stepContext.completionCategory,
                    frequency = result.value
                ),
                state = state
            )
        )
    }

private fun AviationWorld.resolveMonitorFrequencyStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: MonitorFrequency,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> =
    when (val result = resolveMonitorFrequency(AerodromeResolutionContext(context.aerodromeId), instruction)) {
        is arrow.core.Either.Left -> result
        is arrow.core.Either.Right -> arrow.core.Either.Right(
            ResolvedStepWithState(
                step = ResolvedStep.FrequencyChange(
                    index = stepContext.index,
                    instruction = instruction,
                    timing = stepContext.timing,
                    domain = stepContext.domain,
                    completionCategory = stepContext.completionCategory,
                    frequency = result.value
                ),
                state = state
            )
        )
    }

private fun AviationWorld.resolveDirectFixStep(
    stepContext: StepContext,
    fixId: xyz.easiersaid.twr.protocol.FixId,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> {
    val fix = fixes[fixId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_FIX,
        "Unknown fix ${fixId.value}"
    )
    return arrow.core.Either.Right(
        ResolvedStepWithState(
            step = ResolvedStep.DirectFix(
                index = stepContext.index,
                instruction = stepContext.instruction,
                timing = stepContext.timing,
                domain = stepContext.domain,
                completionCategory = stepContext.completionCategory,
                fix = fix
            ),
            state = state
        )
    )
}

private fun AviationWorld.resolveJoinAirwayStep(
    stepContext: StepContext,
    instruction: JoinAirway,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> {
    val airway = airways[instruction.airway] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AIRWAY,
        "Unknown airway ${instruction.airway.value}"
    )
    val joinFix = fixes[instruction.joinFix] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_FIX,
        "Unknown join fix ${instruction.joinFix.value}"
    )
    if (airway.waypoints.none { waypoint -> waypoint.point == joinFix.point }) {
        return unresolved(
            ResolutionFailureCode.AIRWAY_JOIN_FIX_NOT_ON_AIRWAY,
            "Join fix ${instruction.joinFix.value} is not on airway ${airway.id.value}"
        )
    }
    return arrow.core.Either.Right(
        ResolvedStepWithState(
            step = ResolvedStep.AirwayJoin(
                index = stepContext.index,
                instruction = instruction,
                timing = stepContext.timing,
                domain = stepContext.domain,
                completionCategory = stepContext.completionCategory,
                airway = airway,
                joinFix = joinFix
            ),
            state = state
        )
    )
}

private fun AviationWorld.resolveJoinCircuitStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: JoinCircuit,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> {
    val aerodrome = aerodromes[context.aerodromeId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    val candidates = aerodrome.circuits.values.filter { circuit ->
        circuit.direction == instruction.circuitDirection &&
            (instruction.runway == null || circuit.runway == instruction.runway) &&
            circuit.joinProcedures.any { join -> join.type == instruction.joinType }
    }

    val circuit = when (candidates.size) {
        0 -> return unresolved(
            ResolutionFailureCode.UNKNOWN_CIRCUIT_PROCEDURE,
            "No circuit procedure matches ${instruction.circuitDirection} ${instruction.joinType} at aerodrome ${aerodrome.icao.value}"
        )

        1 -> candidates.single()
        else -> return unresolved(
            ResolutionFailureCode.AMBIGUOUS_CIRCUIT_PROCEDURE,
            "Multiple circuit procedures match ${instruction.circuitDirection} ${instruction.joinType} at aerodrome ${aerodrome.icao.value}"
        )
    }

    return arrow.core.Either.Right(
        ResolvedStepWithState(
            step = ResolvedStep.CircuitJoinStep(
                index = stepContext.index,
                instruction = instruction,
                timing = stepContext.timing,
                domain = stepContext.domain,
                completionCategory = stepContext.completionCategory,
                circuit = circuit
            ),
            state = state
        )
    )
}

// H: Visual approach resolution — validates runway exists but has no instrument procedure to resolve.
private fun AviationWorld.resolveVisualApproachStep(
    context: ClearanceResolutionContext,
    stepContext: StepContext,
    instruction: ClearedVisualApproach,
    state: ResolutionCompilationState
): ResolutionResult<ResolvedStepWithState> {
    val aerodrome = aerodromes[context.aerodromeId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${context.aerodromeId.value}"
    )
    aerodrome.runways[instruction.runway] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_RUNWAY,
        "Unknown runway ${instruction.runway.value} at aerodrome ${aerodrome.icao.value}"
    )
    return arrow.core.Either.Right(
        ResolvedStepWithState(
            step = ResolvedStep.Plain(
                index = stepContext.index,
                instruction = instruction,
                timing = stepContext.timing,
                domain = stepContext.domain,
                completionCategory = stepContext.completionCategory
            ),
            state = state
        )
    )
}

private fun AviationWorld.resolveHoldingPointOnActiveTaxiRoute(
    aerodromeId: AerodromeId,
    runwayId: xyz.easiersaid.twr.protocol.RunwayId,
    activeTaxiRoute: ActiveTaxiRoute
): ResolutionResult<RouteLocatedHoldingPoint> {
    val aerodrome = aerodromes[aerodromeId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${aerodromeId.value}"
    )
    val runway = aerodrome.runways[runwayId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_RUNWAY,
        "Unknown runway ${runwayId.value} at aerodrome ${aerodrome.icao.value}"
    )

    val routePoints = activeTaxiRoute.route.points
    val candidates = aerodrome.taxiways.values.flatMap { taxiway ->
        taxiway.holdingPoints
            .filter { holdingPoint -> holdingPoint.runway == runwayId }
            .mapNotNull { holdingPoint ->
                val routeIndex = routePoints
                    .indexOfFirstAfter(activeTaxiRoute.cursorIndex) { point -> point == holdingPoint.point }
                if (routeIndex == null || !taxiwayMatchesRouteAtIndex(taxiway, routePoints, routeIndex)) {
                    null
                } else {
                    RouteHoldingPointCandidate(taxiway, holdingPoint, routeIndex)
                }
            }
    }

    if (candidates.isEmpty()) {
        return unresolved(
            ResolutionFailureCode.GROUND_STEP_NOT_ON_ACTIVE_TAXI_ROUTE,
            "No holding point for runway ${runwayId.value} exists along the active taxi route"
        )
    }

    val bestCandidate = candidates.minByOrNull { candidate -> candidate.routeIndex }!!
    val ambiguousCandidate = candidates.any { candidate ->
        candidate !== bestCandidate && candidate.routeIndex == bestCandidate.routeIndex
    }
    if (ambiguousCandidate) {
        return unresolved(
            ResolutionFailureCode.AMBIGUOUS_CURRENT_TAXIWAY,
            "Multiple holding points for runway ${runwayId.value} occur at the same route position"
        )
    }

    return arrow.core.Either.Right(
        RouteLocatedHoldingPoint(
            resolved = ResolvedHoldingPoint(
                aerodrome = aerodrome,
                runway = runway,
                taxiway = bestCandidate.taxiway,
                holdingPoint = bestCandidate.holdingPoint
            ),
            routeIndex = bestCandidate.routeIndex
        )
    )
}

private fun AviationWorld.resolveCrossingOnActiveTaxiRoute(
    aerodromeId: AerodromeId,
    runwayId: xyz.easiersaid.twr.protocol.RunwayId,
    activeTaxiRoute: ActiveTaxiRoute
): ResolutionResult<RouteLocatedCrossing> {
    val aerodrome = aerodromes[aerodromeId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_AERODROME,
        "Unknown aerodrome ${aerodromeId.value}"
    )
    val runway = aerodrome.runways[runwayId] ?: return unresolved(
        ResolutionFailureCode.UNKNOWN_RUNWAY,
        "Unknown runway ${runwayId.value} at aerodrome ${aerodrome.icao.value}"
    )

    val routePoints = activeTaxiRoute.route.points
    val runwayPoints = runway.path.points.toSet()
    val candidates = routePoints.mapIndexedNotNull { routeIndex, point ->
        if (routeIndex <= activeTaxiRoute.cursorIndex || point !in runwayPoints) {
            return@mapIndexedNotNull null
        }
        val taxiway = aerodrome.taxiways.values.singleOrNull { candidate ->
            taxiwayMatchesRouteAtIndex(candidate, routePoints, routeIndex)
        } ?: return@mapIndexedNotNull null
        RouteCrossingCandidate(
            taxiway = taxiway,
            crossingPoint = point,
            routeIndex = routeIndex
        )
    }

    if (candidates.isEmpty()) {
        return unresolved(
            ResolutionFailureCode.GROUND_STEP_NOT_ON_ACTIVE_TAXI_ROUTE,
            "No crossing for runway ${runwayId.value} exists along the active taxi route"
        )
    }

    val bestCandidate = candidates.minByOrNull { candidate -> candidate.routeIndex }!!
    return arrow.core.Either.Right(
        RouteLocatedCrossing(
            resolved = ResolvedRunwayCrossing(
                aerodrome = aerodrome,
                runway = runway,
                taxiway = bestCandidate.taxiway,
                crossingPoint = bestCandidate.crossingPoint
            ),
            routeIndex = bestCandidate.routeIndex
        )
    )
}

private fun taxiwayMatchesRouteAtIndex(
    taxiway: Taxiway,
    routePoints: List<PointId>,
    routeIndex: Int
): Boolean {
    val point = routePoints[routeIndex]
    if (point !in taxiway.path.points) {
        return false
    }
    val previous = routePoints.getOrNull(routeIndex - 1)
    val next = routePoints.getOrNull(routeIndex + 1)
    return listOfNotNull(previous, next).any { neighbor -> neighbor in taxiway.path.points }
}

private fun <T> List<T>.indexOfFirstAfter(
    startExclusive: Int,
    predicate: (T) -> Boolean
): Int? {
    for (index in indices) {
        if (index > startExclusive && predicate(this[index])) {
            return index
        }
    }
    return null
}

private fun unresolved(
    code: ResolutionFailureCode,
    message: String
): ResolutionResult<Nothing> =
    arrow.core.Either.Left(ResolutionFailure(code, message))

private data class StepContext(
    val index: Int,
    val instruction: AtcInstruction,
    val timing: InstructionTiming?,
    val domain: ClearanceDomain,
    val completionCategory: CompletionCategory?
)

private data class ResolutionCompilationState(
    val currentPoint: PointId?,
    val activeTaxiRoute: ActiveTaxiRoute? = null
)

private data class ActiveTaxiRoute(
    val route: ResolvedTaxiRoute,
    val cursorIndex: Int
)

private data class ResolvedStepWithState(
    val step: ResolvedStep,
    val state: ResolutionCompilationState
)

private data class RouteLocatedHoldingPoint(
    val resolved: ResolvedHoldingPoint,
    val routeIndex: Int
)

private data class RouteLocatedCrossing(
    val resolved: ResolvedRunwayCrossing,
    val routeIndex: Int
)

private data class RouteHoldingPointCandidate(
    val taxiway: Taxiway,
    val holdingPoint: HoldingPoint,
    val routeIndex: Int
)

private data class RouteCrossingCandidate(
    val taxiway: Taxiway,
    val crossingPoint: PointId,
    val routeIndex: Int
)
