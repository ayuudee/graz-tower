package xyz.easiersaid.twr.core.clearance

import xyz.easiersaid.twr.core.resolution.ResolvedApproachClearance
import xyz.easiersaid.twr.core.resolution.ResolvedAirspaceInstruction
import xyz.easiersaid.twr.core.resolution.ResolvedCircuitJoinInstruction
import xyz.easiersaid.twr.core.resolution.ResolvedHoldingInstruction
import xyz.easiersaid.twr.core.resolution.ResolvedHoldingPoint
import xyz.easiersaid.twr.core.resolution.ResolvedRoleFrequency
import xyz.easiersaid.twr.core.resolution.ResolvedRouteClearance
import xyz.easiersaid.twr.core.resolution.ResolvedRunwayCrossing
import xyz.easiersaid.twr.core.resolution.ResolvedTaxiRoute
import xyz.easiersaid.twr.core.resolution.ResolvedVectorInstruction
import xyz.easiersaid.twr.core.world.Airway
import xyz.easiersaid.twr.core.world.CircuitProcedure
import xyz.easiersaid.twr.core.world.Fix
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.ClearedApproach
import xyz.easiersaid.twr.protocol.ClearedToEnterControlZone
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearanceContent
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.CompletionCategory
import xyz.easiersaid.twr.protocol.ContinuePresentHeading
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.HoldAt
import xyz.easiersaid.twr.protocol.InstructionTiming
import xyz.easiersaid.twr.protocol.JoinAirway
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RemainOutsideControlledAirspace
import xyz.easiersaid.twr.protocol.SpecialVfrClearance
import xyz.easiersaid.twr.protocol.TaxiTo
import xyz.easiersaid.twr.protocol.TurnByDegrees
import xyz.easiersaid.twr.protocol.TurnHeading
import xyz.easiersaid.twr.protocol.instructionSupersedesIn

data class ClearanceResolutionContext(
    val aerodromeId: AerodromeId,
    val currentPoint: PointId? = null,
    val currentHeading: xyz.easiersaid.twr.protocol.Heading? = null
)

sealed interface ResolvedStep {
    val index: Int
    val instruction: AtcInstruction
    val timing: InstructionTiming?
    val domain: ClearanceDomain
    val completionCategory: CompletionCategory?

    data class Taxi(
        override val index: Int,
        override val instruction: TaxiTo,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val route: ResolvedTaxiRoute
    ) : ResolvedStep

    data class HoldShort(
        override val index: Int,
        override val instruction: HoldShortOf,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val holdingPoint: ResolvedHoldingPoint
    ) : ResolvedStep

    data class Crossing(
        override val index: Int,
        override val instruction: CrossRunway,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val crossing: ResolvedRunwayCrossing
    ) : ResolvedStep

    data class Backtrack(
        override val index: Int,
        override val instruction: BacktrackRunway,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val runway: Runway,
        val farEndPoint: PointId
    ) : ResolvedStep

    data class Route(
        override val index: Int,
        override val instruction: ClearedTo,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val clearance: ResolvedRouteClearance
    ) : ResolvedStep

    data class Holding(
        override val index: Int,
        override val instruction: HoldAt,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val holding: ResolvedHoldingInstruction
    ) : ResolvedStep

    data class Approach(
        override val index: Int,
        override val instruction: ClearedApproach,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val approach: ResolvedApproachClearance
    ) : ResolvedStep

    data class Airspace(
        override val index: Int,
        override val instruction: AtcInstruction,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val airspace: ResolvedAirspaceInstruction
    ) : ResolvedStep {
        init {
            require(
                instruction is RemainOutsideControlledAirspace ||
                    instruction is ClearedToEnterControlZone ||
                    instruction is SpecialVfrClearance
            ) { "ResolvedStep.Airspace may only wrap airspace instructions" }
        }
    }

    data class FrequencyChange(
        override val index: Int,
        override val instruction: AtcInstruction,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val frequency: ResolvedRoleFrequency
    ) : ResolvedStep

    data class DirectFix(
        override val index: Int,
        override val instruction: AtcInstruction,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val fix: Fix
    ) : ResolvedStep

    data class AirwayJoin(
        override val index: Int,
        override val instruction: JoinAirway,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val airway: Airway,
        val joinFix: Fix
    ) : ResolvedStep

    data class CircuitJoinStep(
        override val index: Int,
        override val instruction: JoinCircuit,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val join: ResolvedCircuitJoinInstruction
    ) : ResolvedStep

    data class Vector(
        override val index: Int,
        override val instruction: AtcInstruction,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?,
        val vector: ResolvedVectorInstruction
    ) : ResolvedStep {
        init {
            require(
                instruction is FlyHeading ||
                    instruction is TurnHeading ||
                    instruction is ContinuePresentHeading ||
                    instruction is TurnByDegrees
            ) { "ResolvedStep.Vector may only wrap vector instructions" }
        }
    }

    data class Plain(
        override val index: Int,
        override val instruction: AtcInstruction,
        override val timing: InstructionTiming?,
        override val domain: ClearanceDomain,
        override val completionCategory: CompletionCategory?
    ) : ResolvedStep
}

data class ResolvedClearance(
    val source: StructuredClearance,
    val steps: List<ResolvedStep>
) {
    val completedSteps: Set<Int> =
        when (val content = source.content) {
            is ClearanceContent.Single -> emptySet()
            is ClearanceContent.Compound -> content.completedSteps
        }

    val immediateSteps: List<ResolvedStep>
        get() = steps.filter { step -> step.timing == InstructionTiming.IMMEDIATE }

    val sequentialSteps: List<ResolvedStep>
        get() = steps.filter { step -> step.timing == InstructionTiming.SEQUENTIAL }

    val persistentSteps: List<ResolvedStep>
        get() = steps.filter { step -> step.timing == InstructionTiming.PERSISTENT }

    val nextSequentialStep: ResolvedStep?
        get() = sequentialSteps.firstOrNull { step -> step.index !in completedSteps }

    val supersedesDomains: Set<ClearanceDomain>
        get() = steps.flatMap { step ->
            instructionSupersedesIn(step.instruction)
        }.toSet()

    val stepDomains: Set<ClearanceDomain>
        get() = steps.map { step -> step.domain }.toSet()

    fun effectiveSteps(suppressedDomains: Set<ClearanceDomain> = emptySet()): List<ResolvedStep> =
        steps.filter { step -> step.domain !in suppressedDomains }

    fun withSource(source: StructuredClearance): ResolvedClearance =
        copy(source = source)
}
