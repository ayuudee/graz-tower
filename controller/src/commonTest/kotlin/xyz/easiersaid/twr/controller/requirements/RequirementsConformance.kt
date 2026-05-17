package xyz.easiersaid.twr.controller.requirements

import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.protocol.RegulationRef
import kotlin.test.fail

@JvmInline
value class SourceUnitRef(val canonicalId: String) {
    init {
        require(canonicalId.count { it == ':' } >= 4) {
            "SourceUnitRef must use canonical registry id form document::section::hash: $canonicalId"
        }
    }
}

data class SourceUnitEvidenceExpectation(
    val sourceUnit: SourceUnitRef,
    val acceptedProxyRegulations: Set<RegulationRef>,
)

data class RequirementsConformanceCase(
    val id: String,
    val family: String,
    val expectations: List<SourceUnitEvidenceExpectation>,
) {
    init {
        require(id.isNotBlank()) { "case id must not be blank" }
        require(family.isNotBlank()) { "case family must not be blank" }
        require(expectations.isNotEmpty()) { "case must expect at least one source unit" }
    }
}

fun RequirementsConformanceCase.assertSatisfiedBy(trace: DecisionTrace) {
    val missing = expectations.filterNot { expectation ->
        trace.regulations.toSet().containsAll(expectation.acceptedProxyRegulations)
    }
    if (missing.isNotEmpty()) {
        fail(
            buildString {
                appendLine("Requirements conformance case '$id' failed for rule ${trace.ruleId}.")
                appendLine("Family: $family")
                appendLine("Observed regulations: ${trace.regulations}")
                appendLine("Missing source-unit evidence:")
                missing.forEach { expectation ->
                    appendLine("  - ${expectation.sourceUnit.canonicalId}")
                    appendLine("    expected proxy regs: ${expectation.acceptedProxyRegulations}")
                }
            },
        )
    }
}

