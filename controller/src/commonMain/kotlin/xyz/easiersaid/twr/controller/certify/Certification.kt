package xyz.easiersaid.twr.controller.certify

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.right
import arrow.core.toNonEmptyListOrNull
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.bdi.ProposedAction
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.SeparationConcern
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.BreakOff
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ConfirmSquawk
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldPositionCancelTakeoff
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.instructionMayBeConditional
import xyz.easiersaid.twr.protocol.JoinCircuit
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.MonitorFrequency
import xyz.easiersaid.twr.protocol.NumberInSequence
import xyz.easiersaid.twr.protocol.RadarServiceTerminated
import xyz.easiersaid.twr.protocol.ReportWhen
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.SquawkIdent
import xyz.easiersaid.twr.protocol.SquawkNormal
import xyz.easiersaid.twr.protocol.SquawkStandby
import xyz.easiersaid.twr.protocol.StopSquawk
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiToStand
import xyz.easiersaid.twr.protocol.TurnBase

sealed interface KernelRequirement {
    data object Runway : KernelRequirement
    data object Surface : KernelRequirement
    data object AirPath : KernelRequirement
    data object Separation : KernelRequirement
}

data class CertificationPlan(
    val requirements: Set<KernelRequirement>,
    val compatibilityRequired: Boolean,
    val joint: Boolean,
) {
    val requiresKernel: Boolean get() = requirements.isNotEmpty()
}

sealed interface NoCertificationRequired {
    data object AdministrativeSequencing : NoCertificationRequired
    data object ReportingInstruction : NoCertificationRequired
    data object FrequencyCoordination : NoCertificationRequired
    data object SurveillanceCoordination : NoCertificationRequired
}

sealed interface CertificationEvidence {
    data class KernelBacked(
        val requirement: KernelRequirement,
        val summary: String,
    ) : CertificationEvidence

    data class RuntimeChecked(
        val checkId: String,
        val summary: String,
    ) : CertificationEvidence

    data class OperationalAssumption(
        val assumption: String,
    ) : CertificationEvidence

    data class EmergencyPolicy(
        val doctrine: String,
    ) : CertificationEvidence

    data class NotRequired(
        val reason: NoCertificationRequired,
    ) : CertificationEvidence
}

sealed interface CertificationFailure {
    data class UnsupportedInstruction(
        val instruction: AtcInstruction,
    ) : CertificationFailure

    data class MissingAircraft(
        val aircraft: AircraftId,
    ) : CertificationFailure

    data class StaleSnapshot(
        val observedAt: SimTime,
        val decisionAt: SimTime,
    ) : CertificationFailure

    data class KernelRejected(
        val requirement: KernelRequirement,
        val reason: String,
    ) : CertificationFailure

    data class CompatibilityRejected(
        val reason: String,
    ) : CertificationFailure
}

data class CertificationContext(
    val view: ControllerView,
    val beliefs: BeliefState,
    val world: AviationWorld,
    val decisionTime: SimTime,
)

internal sealed interface CertifiedInstructionToken

private data object CertifiedInstructionTokenInstance : CertifiedInstructionToken

class CertifiedInstruction internal constructor(
    private val token: CertifiedInstructionToken,
    val aircraft: AircraftId,
    val dispatch: Dispatch,
    val plan: CertificationPlan,
    val evidence: NonEmptyList<CertificationEvidence>,
    val certifiedAt: SimTime,
) {
    val instruction: AtcInstruction get() = dispatch.instruction

    init {
        check(token === CertifiedInstructionTokenInstance)
    }
}

data class RunwayCertificationWork(
    val action: ProposedAction,
    val context: CertificationContext,
    val plan: CertificationPlan,
)

data class SurfaceCertificationWork(
    val action: ProposedAction,
    val context: CertificationContext,
    val plan: CertificationPlan,
)

data class AirPathCertificationWork(
    val action: ProposedAction,
    val context: CertificationContext,
    val plan: CertificationPlan,
)

data class SeparationCertificationWork(
    val action: ProposedAction,
    val context: CertificationContext,
    val plan: CertificationPlan,
)

interface RuntimeKernelCertifiers {
    fun certifyRunway(
        work: RunwayCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked>

    fun certifySurface(
        work: SurfaceCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked>

    fun certifyAirPath(
        work: AirPathCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked>

    fun certifySeparation(
        work: SeparationCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked>
}

object KotlinRuntimeKernelCertifiers : RuntimeKernelCertifiers {
    override fun certifyRunway(
        work: RunwayCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked> {
        val runway = runwayOf(work.action, work.context).fold(
            { return it.left() },
            { it },
        )
        val active = work.context.beliefs.activeRunway
        if (active == null) {
            return CertificationFailure.KernelRejected(
                KernelRequirement.Runway,
                "No active runway available for ${work.action.instruction::class.simpleName}",
            ).left()
        }
        if (runway != null && runway != active) {
            return CertificationFailure.KernelRejected(
                KernelRequirement.Runway,
                "Instruction runway $runway does not match active runway $active",
            ).left()
        }
        return approved(KernelRequirement.Runway, work.action.instruction, "active-runway runtime check accepted")
    }

    override fun certifySurface(
        work: SurfaceCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked> {
        val aircraft = work.context.beliefs.trackedAircraft[work.action.aircraft]
            ?: return CertificationFailure.MissingAircraft(work.action.aircraft).left()
        if (!aircraft.onGround) {
            return CertificationFailure.KernelRejected(
                KernelRequirement.Surface,
                "Surface instruction for airborne aircraft ${work.action.aircraft}",
            ).left()
        }
        return approved(KernelRequirement.Surface, work.action.instruction, "surface runtime check accepted: aircraft on ground")
    }

    override fun certifyAirPath(
        work: AirPathCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked> {
        val aircraft = work.context.beliefs.trackedAircraft[work.action.aircraft]
            ?: return CertificationFailure.MissingAircraft(work.action.aircraft).left()
        val instruction = work.action.instruction
        val coherent = when (instruction) {
            is ClearedForTakeoff -> aircraft.onGround
            is ClearedToLand,
            is ClearedTouchAndGo,
            is GoAround,
            is BreakOff,
            is TurnBase,
            is ExtendDownwind,
            is ContinueApproach,
            is JoinCircuit,
            -> !aircraft.onGround
            else -> true
        }
        if (!coherent) {
            return CertificationFailure.KernelRejected(
                KernelRequirement.AirPath,
                "Air-path instruction ${instruction::class.simpleName} is incoherent for onGround=${aircraft.onGround}",
            ).left()
        }
        return approved(KernelRequirement.AirPath, instruction, "air-path runtime check accepted: on-ground coherence")
    }

    override fun certifySeparation(
        work: SeparationCertificationWork,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked> {
        val target = work.action.aircraft
        val unresolved = work.context.beliefs.separationAssessments.any { assessment ->
            assessment.other == target &&
                assessment.concern == SeparationConcern.Severity.VIOLATION &&
                work.action.instruction !is GoAround &&
                work.action.instruction !is BreakOff
        }
        if (unresolved) {
            return CertificationFailure.KernelRejected(
                KernelRequirement.Separation,
                "Target $target has unresolved violation-level separation concern",
            ).left()
        }
        return approved(KernelRequirement.Separation, work.action.instruction, "separation runtime check accepted: no unresolved violation")
    }

    private fun approved(
        requirement: KernelRequirement,
        instruction: AtcInstruction,
        summary: String,
    ): Either<CertificationFailure, CertificationEvidence.KernelBacked> =
        CertificationEvidence.KernelBacked(
            requirement = requirement,
            summary = "$summary for ${instruction::class.simpleName}",
        ).right()

    private fun runwayOf(
        action: ProposedAction,
        context: CertificationContext,
    ): Either<CertificationFailure, RunwayId?> = when (val instruction = action.instruction) {
        is TaxiToHoldingPoint -> instruction.runway
        is HoldShortOf -> instruction.runway
        is CrossRunway -> instruction.runway
        is BacktrackRunway -> instruction.runway
        is LineUpAndWait -> instruction.runway
        is ClearedForTakeoff -> instruction.runway
        is ClearedToLand -> instruction.runway
        is ClearedTouchAndGo -> instruction.runway
        is HoldPositionCancelTakeoff -> observedRunway(action, context).fold(
            { return it.left() },
            { it },
        )
        is AfterLandingVacateVia -> vacateRunway(instruction, action, context).fold(
            { return it.left() },
            { it },
        )
        else -> null
    }.right()

    private fun observedRunway(
        action: ProposedAction,
        context: CertificationContext,
    ): Either<CertificationFailure, RunwayId> {
        val aircraft = context.beliefs.trackedAircraft[action.aircraft]
            ?: return CertificationFailure.MissingAircraft(action.aircraft).left()
        val runways = aircraft.entities.mapNotNull { (it as? EntityRef.RunwayRef)?.id }.toSet()
        return when (runways.size) {
            1 -> runways.single().right()
            0 -> CertificationFailure.KernelRejected(
                KernelRequirement.Runway,
                "Aircraft ${action.aircraft} is not observed on a runway",
            ).left()
            else -> CertificationFailure.KernelRejected(
                KernelRequirement.Runway,
                "Aircraft ${action.aircraft} is observed on multiple runways $runways",
            ).left()
        }
    }

    private fun vacateRunway(
        instruction: AfterLandingVacateVia,
        action: ProposedAction,
        context: CertificationContext,
    ): Either<CertificationFailure, RunwayId> {
        val observed = observedRunway(action, context).fold(
            { return it.left() },
            { it },
        )
        val aerodrome = context.world.aerodromes[context.view.aerodromeId]
            ?: return CertificationFailure.KernelRejected(
                KernelRequirement.Runway,
                "Aerodrome ${context.view.aerodromeId} not found for vacate exit ${instruction.exit}",
            ).left()
        val exitRunways = aerodrome.runways.values
            .filter { runway -> runway.exits.any { it.point == instruction.exit } }
            .map { it.id }
            .toSet()
        if (observed !in exitRunways) {
            return CertificationFailure.KernelRejected(
                KernelRequirement.Runway,
                "Vacate exit ${instruction.exit} is not an exit from observed runway $observed",
            ).left()
        }
        return observed.right()
    }
}

class ActionCertifier(
    private val kernels: RuntimeKernelCertifiers,
) {
    fun certify(
        action: ProposedAction,
        context: CertificationContext,
    ): Either<CertificationFailure, CertifiedInstruction> {
        val plan = certificationPlanFor(action.instruction).fold(
            { return it.left() },
            { it },
        )
        val compatibilityEvidence = checkCompatibility(action, context).fold(
            { return it.left() },
            { it },
        )
        val evidence = if (plan.requiresKernel) {
            collectKernelEvidence(action, context, plan).fold(
                { return it.left() },
                { it },
            )
        } else {
            noCertificationEvidence(action.instruction).fold(
                { return it.left() },
                { it },
            )
        }
        val finalEvidence = if (plan.compatibilityRequired) NonEmptyList(compatibilityEvidence, evidence.all)
            else evidence
        return CertifiedInstruction(
            token = CertifiedInstructionTokenInstance,
            aircraft = action.aircraft,
            dispatch = action.dispatch,
            plan = plan,
            evidence = finalEvidence,
            certifiedAt = context.decisionTime,
        ).right()
    }

    private fun checkCompatibility(
        action: ProposedAction,
        context: CertificationContext,
    ): Either<CertificationFailure, CertificationEvidence.RuntimeChecked> {
        if (action.instruction.target != action.aircraft) {
            return CertificationFailure.CompatibilityRejected(
                "Proposed action aircraft ${action.aircraft} does not match instruction target ${action.instruction.target}",
            ).left()
        }
        when (action.dispatch) {
            is Dispatch.Direct -> Unit
            is Dispatch.Conditional -> {
                if (!instructionMayBeConditional(action.instruction)) {
                    return CertificationFailure.CompatibilityRejected(
                        "${action.instruction::class.simpleName} may not be issued conditionally",
                    ).left()
                }
                if (context.view.lvpMode) {
                    return CertificationFailure.CompatibilityRejected(
                        "Conditional dispatch is not permitted in low visibility procedures",
                    ).left()
                }
            }
        }
        return CertificationEvidence.RuntimeChecked(
            checkId = "dispatch-compatibility",
            summary = "Action target, dispatch shape, and conditional legality checked",
        ).right()
    }

    private fun collectKernelEvidence(
        action: ProposedAction,
        context: CertificationContext,
        plan: CertificationPlan,
    ): Either<CertificationFailure, NonEmptyList<CertificationEvidence>> {
        if (action.aircraft !in context.beliefs.trackedAircraft) {
            return CertificationFailure.MissingAircraft(action.aircraft).left()
        }
        val operationEvidence = when (action.instruction) {
            is TaxiToHoldingPoint,
            is TaxiToStand,
            is HoldPosition,
            is HoldShortOf,
            -> SurfaceMovementAuthorization.certify(action, context, plan, kernels)

            is CrossRunway,
            is BacktrackRunway,
            is LineUpAndWait,
            is HoldPositionCancelTakeoff,
            is AfterLandingVacateVia,
            -> RunwayAccessAuthorization.certify(action, context, plan, kernels)

            is ClearedForTakeoff,
            is ClearedToLand,
            is ClearedTouchAndGo,
            is GoAround,
            is BreakOff,
            -> AirRunwayOperationAuthorization.certify(action, context, plan, kernels)

            else -> runPrimitivePlan(action, context, plan, kernels)
        }
        return operationEvidence
    }
}

object SurfaceMovementAuthorization {
    fun certify(
        action: ProposedAction,
        context: CertificationContext,
        plan: CertificationPlan,
        kernels: RuntimeKernelCertifiers,
    ): Either<CertificationFailure, NonEmptyList<CertificationEvidence>> =
        runPrimitivePlan(action, context, plan, kernels)
}

object RunwayAccessAuthorization {
    fun certify(
        action: ProposedAction,
        context: CertificationContext,
        plan: CertificationPlan,
        kernels: RuntimeKernelCertifiers,
    ): Either<CertificationFailure, NonEmptyList<CertificationEvidence>> =
        runPrimitivePlan(action, context, plan, kernels)
}

object AirRunwayOperationAuthorization {
    fun certify(
        action: ProposedAction,
        context: CertificationContext,
        plan: CertificationPlan,
        kernels: RuntimeKernelCertifiers,
    ): Either<CertificationFailure, NonEmptyList<CertificationEvidence>> =
        runPrimitivePlan(action, context, plan, kernels)
}

private fun runPrimitivePlan(
    action: ProposedAction,
    context: CertificationContext,
    plan: CertificationPlan,
    kernels: RuntimeKernelCertifiers,
): Either<CertificationFailure, NonEmptyList<CertificationEvidence>> {
    val collected = plan.requirements.map { requirement ->
        when (requirement) {
            KernelRequirement.Runway -> kernels.certifyRunway(
                RunwayCertificationWork(action, context, plan),
            )
            KernelRequirement.Surface -> kernels.certifySurface(
                SurfaceCertificationWork(action, context, plan),
            )
            KernelRequirement.AirPath -> kernels.certifyAirPath(
                AirPathCertificationWork(action, context, plan),
            )
            KernelRequirement.Separation -> kernels.certifySeparation(
                SeparationCertificationWork(action, context, plan),
            )
        }.fold(
            { return it.left() },
            { it },
        )
    }
    return collected.toNonEmptyListOrNull()?.right()
        ?: CertificationFailure.CompatibilityRejected("Certification plan had no kernel requirements").left()
}

fun certificationPlanFor(
    instruction: AtcInstruction,
): Either<CertificationFailure, CertificationPlan> = when (instruction) {
    is TaxiToHoldingPoint,
    is TaxiToStand,
    is HoldPosition,
    is HoldShortOf,
    -> CertificationPlan(
        requirements = setOf(KernelRequirement.Surface),
        compatibilityRequired = true,
        joint = false,
    ).right()

    is CrossRunway,
    is BacktrackRunway,
    is LineUpAndWait,
    is HoldPositionCancelTakeoff,
    is AfterLandingVacateVia,
    -> CertificationPlan(
        requirements = setOf(KernelRequirement.Runway, KernelRequirement.Surface),
        compatibilityRequired = true,
        joint = true,
    ).right()

    is ClearedForTakeoff,
    is ClearedToLand,
    is ClearedTouchAndGo,
    is GoAround,
    is BreakOff,
    -> CertificationPlan(
        requirements = setOf(KernelRequirement.Runway, KernelRequirement.AirPath, KernelRequirement.Separation),
        compatibilityRequired = true,
        joint = true,
    ).right()

    is TurnBase,
    is ExtendDownwind,
    is ContinueApproach,
    is JoinCircuit,
    -> CertificationPlan(
        requirements = setOf(KernelRequirement.AirPath, KernelRequirement.Separation),
        compatibilityRequired = true,
        joint = false,
    ).right()

    is NumberInSequence,
    is ReportWhen,
    is ContactFrequency,
    is MonitorFrequency,
    is RadarServiceTerminated,
    is SetSquawk,
    is ConfirmSquawk,
    is SquawkIdent,
    is SquawkStandby,
    is SquawkNormal,
    is StopSquawk,
    -> CertificationPlan(
        requirements = emptySet(),
        compatibilityRequired = false,
        joint = false,
    ).right()

    else -> CertificationFailure.UnsupportedInstruction(instruction).left()
}

private fun noCertificationEvidence(
    instruction: AtcInstruction,
): Either<CertificationFailure, NonEmptyList<CertificationEvidence>> {
    val reason = when (instruction) {
        is NumberInSequence -> NoCertificationRequired.AdministrativeSequencing
        is ReportWhen -> NoCertificationRequired.ReportingInstruction
        is ContactFrequency,
        is MonitorFrequency,
        is RadarServiceTerminated,
        -> NoCertificationRequired.FrequencyCoordination
        is SetSquawk,
        is ConfirmSquawk,
        is SquawkIdent,
        is SquawkStandby,
        is SquawkNormal,
        is StopSquawk,
        -> NoCertificationRequired.SurveillanceCoordination
        else -> return CertificationFailure.UnsupportedInstruction(instruction).left()
    }
    return NonEmptyList(CertificationEvidence.NotRequired(reason), emptyList()).right()
}
