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
        clearance.source.status.isTerminal() -> clearance.source.status
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
    val clearances = allClearances.filterNot { managed -> managed.status.isTerminal() }
    val terminalClearances = allClearances.filter { managed -> managed.status.isTerminal() }

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
    val completionEvaluations = mutableListOf<ManagedCompletionEvaluation>()
    var working = existing.map { managed ->
        if (managed.status != ClearanceStatus.ACTIVE) {
            managed
        } else {
            val view = completionViews[managed.aircraft]
            if (view == null) {
                managed
            } else {
                val evaluation = evaluateCompletion(
                    clearance = managed.clearance,
                    view = view,
                    suppressedDomains = managed.suppressedDomains
                )
                completionEvaluations += ManagedCompletionEvaluation(managed, evaluation)
                managed.withClearance(evaluation.updated)
            }
        }
    }

    val activations = mutableListOf<ConditionActivation>()
    val fullySuperseded = mutableListOf<ManagedClearance>()
    val partiallySuperseded = mutableListOf<ManagedClearance>()

    val pendingIds = working
        .filter { managed -> managed.status == ClearanceStatus.CONDITION_PENDING && managed.source.condition != null }
        .sortedBy { managed -> managed.source.issuedAt.value }
        .map { managed -> managed.source.id }

    pendingIds.forEach { clearanceId ->
        val current = working.findById(clearanceId) ?: return@forEach
        val condition = current.source.condition ?: return@forEach
        if (!conditionEvaluator(current.aircraft, condition)) {
            return@forEach
        }

        val activated = current.withStatus(ClearanceStatus.ACTIVE)
        val others = working.filterNot { managed -> managed.source.id == activated.source.id }
        val supersession = applyIncomingSupersession(others, activated)
        activations += ConditionActivation(before = current, after = activated)
        fullySuperseded += supersession.fullySuperseded
        partiallySuperseded += supersession.partiallySuperseded
        working = supersession.updatedExisting + activated
    }

    val clearances = working.filterNot { managed -> managed.status.isTerminal() }
    val terminalClearances = working.filter { managed -> managed.status.isTerminal() }

    return ClearanceReconciliation(
        clearances = clearances,
        terminalClearances = terminalClearances,
        completionEvaluations = completionEvaluations,
        activatedClearances = activations,
        fullySuperseded = fullySuperseded,
        partiallySuperseded = partiallySuperseded
    )
}

private data class SupersessionApplication(
    val updatedExisting: List<ManagedClearance>,
    val fullySuperseded: List<ManagedClearance>,
    val partiallySuperseded: List<ManagedClearance>
)

private fun applyIncomingSupersession(
    existing: List<ManagedClearance>,
    incoming: ManagedClearance
): SupersessionApplication {
    val updatedExisting = mutableListOf<ManagedClearance>()
    val fullySuperseded = mutableListOf<ManagedClearance>()
    val partiallySuperseded = mutableListOf<ManagedClearance>()

    existing.forEach { managed ->
        if (!managed.status.isSupersedable() || managed.aircraft != incoming.aircraft) {
            updatedExisting += managed
            return@forEach
        }

        val overlap = managed.effectiveDomains intersect incoming.clearance.supersedesDomains
        when {
            overlap.isEmpty() -> updatedExisting += managed
            overlap == managed.effectiveDomains -> {
                val superseded = managed
                    .clearSuppression()
                    .withStatus(ClearanceStatus.SUPERSEDED)
                updatedExisting += superseded
                fullySuperseded += superseded
            }

            else -> {
                val suppressed = managed.suppress(overlap)
                updatedExisting += suppressed
                partiallySuperseded += suppressed
            }
        }
    }

    return SupersessionApplication(
        updatedExisting = updatedExisting,
        fullySuperseded = fullySuperseded,
        partiallySuperseded = partiallySuperseded
    )
}

private fun List<ManagedClearance>.findById(id: ClearanceId): ManagedClearance? =
    firstOrNull { managed -> managed.source.id == id }

private fun ClearanceStatus.isTerminal(): Boolean =
    this in setOf(
        ClearanceStatus.COMPLETED,
        ClearanceStatus.SUPERSEDED,
        ClearanceStatus.CANCELLED
    )

private fun ClearanceStatus.isSupersedable(): Boolean =
    this in setOf(
        ClearanceStatus.ISSUED,
        ClearanceStatus.READBACK_PENDING,
        ClearanceStatus.CONDITION_PENDING,
        ClearanceStatus.ACTIVE
    )
