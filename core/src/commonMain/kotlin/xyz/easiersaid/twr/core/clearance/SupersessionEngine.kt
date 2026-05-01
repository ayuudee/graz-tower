package xyz.easiersaid.twr.core.clearance

import xyz.easiersaid.twr.protocol.ClearanceDomain

data class PartiallySupersededClearance(
    val clearance: ResolvedClearance,
    val suppressedDomains: Set<ClearanceDomain>
) {
    val remainingDomains: Set<ClearanceDomain>
        get() = clearance.stepDomains - suppressedDomains

    val remainingSteps: List<ResolvedStep>
        get() = clearance.effectiveSteps(suppressedDomains)
}

data class SupersessionDecision(
    val incoming: ResolvedClearance,
    val fullySuperseded: List<ResolvedClearance>,
    val partiallySuperseded: List<PartiallySupersededClearance>,
    val unaffected: List<ResolvedClearance>
)

private data class SupersessionAccumulator(
    val fullySuperseded: List<ResolvedClearance> = emptyList(),
    val partiallySuperseded: List<PartiallySupersededClearance> = emptyList(),
    val unaffected: List<ResolvedClearance> = emptyList()
)

fun determineSupersession(
    incoming: ResolvedClearance,
    existing: Iterable<ResolvedClearance>
): SupersessionDecision {
    val supersedesDomains = incoming.supersedesDomains

    val result = existing.fold(SupersessionAccumulator()) { acc, clearance ->
        if (!clearance.source.status.isSupersedable || clearance.source.aircraft != incoming.source.aircraft) {
            return@fold acc.copy(unaffected = acc.unaffected + clearance)
        }

        val overlap = clearance.stepDomains intersect supersedesDomains
        when {
            overlap.isEmpty() -> acc.copy(unaffected = acc.unaffected + clearance)
            overlap == clearance.stepDomains -> acc.copy(fullySuperseded = acc.fullySuperseded + clearance)
            else -> acc.copy(
                partiallySuperseded = acc.partiallySuperseded + PartiallySupersededClearance(
                    clearance = clearance,
                    suppressedDomains = overlap
                )
            )
        }
    }

    return SupersessionDecision(
        incoming = incoming,
        fullySuperseded = result.fullySuperseded,
        partiallySuperseded = result.partiallySuperseded,
        unaffected = result.unaffected
    )
}
