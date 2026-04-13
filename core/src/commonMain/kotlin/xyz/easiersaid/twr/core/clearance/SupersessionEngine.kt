package xyz.easiersaid.twr.core.clearance

import xyz.easiersaid.twr.protocol.ClearanceDomain
import xyz.easiersaid.twr.protocol.ClearanceStatus

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

fun determineSupersession(
    incoming: ResolvedClearance,
    existing: Iterable<ResolvedClearance>
): SupersessionDecision {
    val supersedesDomains = incoming.supersedesDomains
    val fullySuperseded = mutableListOf<ResolvedClearance>()
    val partiallySuperseded = mutableListOf<PartiallySupersededClearance>()
    val unaffected = mutableListOf<ResolvedClearance>()

    existing.forEach { clearance ->
        if (!clearance.isSupersedable() || clearance.source.aircraft != incoming.source.aircraft) {
            unaffected += clearance
            return@forEach
        }

        val overlap = clearance.stepDomains intersect supersedesDomains
        when {
            overlap.isEmpty() -> unaffected += clearance
            overlap == clearance.stepDomains -> fullySuperseded += clearance
            else -> partiallySuperseded += PartiallySupersededClearance(
                clearance = clearance,
                suppressedDomains = overlap
            )
        }
    }

    return SupersessionDecision(
        incoming = incoming,
        fullySuperseded = fullySuperseded,
        partiallySuperseded = partiallySuperseded,
        unaffected = unaffected
    )
}

private fun ResolvedClearance.isSupersedable(): Boolean =
    source.status in setOf(
        ClearanceStatus.ISSUED,
        ClearanceStatus.READBACK_PENDING,
        ClearanceStatus.CONDITION_PENDING,
        ClearanceStatus.ACTIVE
    )
