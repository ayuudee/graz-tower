package xyz.easiersaid.twr.core.clearance

import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceId
import xyz.easiersaid.twr.protocol.ClearanceStatus
import xyz.easiersaid.twr.protocol.ConditionalPredicate

typealias ConditionEvaluator = (AircraftId, ConditionalPredicate) -> Boolean

data class ManagedClearance(
    val clearance: ResolvedClearance,
    val suppressedDomains: Set<ClearanceDomain> = emptySet()
) {
    val source: StructuredClearance
        get() = clearance.source

    val aircraft: AircraftId
        get() = source.aircraft

    val status: ClearanceStatus
        get() = source.status

    val effectiveDomains: Set<ClearanceDomain>
        get() = clearance.stepDomains - suppressedDomains

    val effectiveSteps: List<ResolvedStep>
        get() = clearance.effectiveSteps(suppressedDomains)

    fun withClearance(clearance: ResolvedClearance): ManagedClearance =
        copy(clearance = clearance)

    fun withSource(source: StructuredClearance): ManagedClearance =
        copy(clearance = clearance.withSource(source))

    fun withStatus(status: ClearanceStatus): ManagedClearance =
        withSource(source.copy(status = status))

    fun suppress(domains: Set<ClearanceDomain>): ManagedClearance =
        copy(suppressedDomains = suppressedDomains + domains)

    fun clearSuppression(): ManagedClearance =
        copy(suppressedDomains = emptySet())
}

data class ManagedCompletionEvaluation(
    val clearance: ManagedClearance,
    val evaluation: CompletionEvaluation
)

data class ConditionActivation(
    val before: ManagedClearance,
    val after: ManagedClearance
)

data class ClearanceAdmission(
    val incoming: ManagedClearance,
    val clearances: List<ManagedClearance>,
    val terminalClearances: List<ManagedClearance>,
    val fullySuperseded: List<ManagedClearance>,
    val partiallySuperseded: List<ManagedClearance>
)

data class ClearanceReconciliation(
    val clearances: List<ManagedClearance>,
    val terminalClearances: List<ManagedClearance>,
    val completionEvaluations: List<ManagedCompletionEvaluation>,
    val activatedClearances: List<ConditionActivation>,
    val fullySuperseded: List<ManagedClearance>,
    val partiallySuperseded: List<ManagedClearance>
)

fun stageIncomingClearance(clearance: ResolvedClearance): ManagedClearance {
    val nextStatus = when {
        clearance.source.status.isTerminal -> clearance.source.status
        clearance.source.condition != null -> ClearanceStatus.CONDITION_PENDING
        clearance.source.status in setOf(
            ClearanceStatus.ISSUED,
            ClearanceStatus.READBACK_PENDING,
            ClearanceStatus.CONDITION_PENDING
        ) -> ClearanceStatus.ACTIVE

        else -> clearance.source.status
    }
    return ManagedClearance(
        clearance = clearance.withSource(clearance.source.copy(status = nextStatus))
    )
}

fun admitClearance(
    existing: List<ManagedClearance>,
    incoming: ResolvedClearance
): ClearanceAdmission {
    val stagedIncoming = stageIncomingClearance(incoming)
    val supersession = if (stagedIncoming.status == ClearanceStatus.ACTIVE) {
        applyIncomingSupersession(existing, stagedIncoming)
    } else {
        SupersessionApplication(
            updatedExisting = existing,
            fullySuperseded = emptyList(),
            partiallySuperseded = emptyList()
        )
    }

    val allClearances = supersession.updatedExisting + stagedIncoming
    val clearances = allClearances.filterNot { managed -> managed.status.isTerminal }
    val terminalClearances = allClearances.filter { managed -> managed.status.isTerminal }

    return ClearanceAdmission(
        incoming = stagedIncoming,
        clearances = clearances,
        terminalClearances = terminalClearances,
        fullySuperseded = supersession.fullySuperseded,
        partiallySuperseded = supersession.partiallySuperseded
    )
}

fun reconcileClearances(
    existing: List<ManagedClearance>,
    completionViews: Map<AircraftId, CompletionView>,
    conditionEvaluator: ConditionEvaluator = { _, _ -> false }
): ClearanceReconciliation {
    // Phase 1: Evaluate completions for active clearances
    val completionResults = existing.map { managed ->
        if (managed.status != ClearanceStatus.ACTIVE) {
            Pair(managed, null as CompletionEvaluation?)
        } else {
            val view = completionViews[managed.aircraft]
            if (view == null) {
                Pair(managed, null)
            } else {
                val evaluation = evaluateCompletion(
                    clearance = managed.clearance,
                    view = view,
                    suppressedDomains = managed.suppressedDomains
                )
                Pair(managed.withClearance(evaluation.updated), evaluation)
            }
        }
    }

    val completionEvaluations = completionResults.mapNotNull { (managed, evaluation) ->
        evaluation?.let { ManagedCompletionEvaluation(managed, it) }
    }
    val afterCompletion = completionResults.map { (managed, _) -> managed }

    // Phase 2: Activate pending conditions via fold
    val pendingIds = afterCompletion
        .filter { managed -> managed.status == ClearanceStatus.CONDITION_PENDING && managed.source.condition != null }
        .sortedBy { managed -> managed.source.issuedAt.value }
        .map { managed -> managed.source.id }

    data class ActivationAcc(
        val working: List<ManagedClearance>,
        val activations: List<ConditionActivation> = emptyList(),
        val fullySuperseded: List<ManagedClearance> = emptyList(),
        val partiallySuperseded: List<ManagedClearance> = emptyList()
    )

    val activationResult = pendingIds.fold(ActivationAcc(working = afterCompletion)) { acc, clearanceId ->
        val current = acc.working.findById(clearanceId) ?: return@fold acc
        val condition = current.source.condition ?: return@fold acc
        if (!conditionEvaluator(current.aircraft, condition)) return@fold acc

        val activated = current.withStatus(ClearanceStatus.ACTIVE)
        val others = acc.working.filterNot { managed -> managed.source.id == activated.source.id }
        val supersession = applyIncomingSupersession(others, activated)

        acc.copy(
            working = supersession.updatedExisting + activated,
            activations = acc.activations + ConditionActivation(before = current, after = activated),
            fullySuperseded = acc.fullySuperseded + supersession.fullySuperseded,
            partiallySuperseded = acc.partiallySuperseded + supersession.partiallySuperseded
        )
    }

    val clearances = activationResult.working.filterNot { managed -> managed.status.isTerminal }
    val terminalClearances = activationResult.working.filter { managed -> managed.status.isTerminal }

    return ClearanceReconciliation(
        clearances = clearances,
        terminalClearances = terminalClearances,
        completionEvaluations = completionEvaluations,
        activatedClearances = activationResult.activations,
        fullySuperseded = activationResult.fullySuperseded,
        partiallySuperseded = activationResult.partiallySuperseded
    )
}

private data class SupersessionApplication(
    val updatedExisting: List<ManagedClearance> = emptyList(),
    val fullySuperseded: List<ManagedClearance> = emptyList(),
    val partiallySuperseded: List<ManagedClearance> = emptyList()
)

private fun applyIncomingSupersession(
    existing: List<ManagedClearance>,
    incoming: ManagedClearance
): SupersessionApplication {
    val result = existing.fold(SupersessionApplication()) { acc, managed ->
        if (!managed.status.isSupersedable || managed.aircraft != incoming.aircraft) {
            return@fold acc.copy(updatedExisting = acc.updatedExisting + managed)
        }

        val overlap = managed.effectiveDomains intersect incoming.clearance.supersedesDomains
        when {
            overlap.isEmpty() -> acc.copy(updatedExisting = acc.updatedExisting + managed)
            overlap == managed.effectiveDomains -> {
                val superseded = managed.clearSuppression().withStatus(ClearanceStatus.SUPERSEDED)
                acc.copy(
                    updatedExisting = acc.updatedExisting + superseded,
                    fullySuperseded = acc.fullySuperseded + superseded
                )
            }

            else -> {
                val suppressed = managed.suppress(overlap)
                acc.copy(
                    updatedExisting = acc.updatedExisting + suppressed,
                    partiallySuperseded = acc.partiallySuperseded + suppressed
                )
            }
        }
    }
    return result
}

private fun List<ManagedClearance>.findById(id: ClearanceId): ManagedClearance? =
    firstOrNull { managed -> managed.source.id == id }

