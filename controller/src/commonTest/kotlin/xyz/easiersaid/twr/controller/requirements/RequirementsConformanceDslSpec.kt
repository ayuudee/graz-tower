package xyz.easiersaid.twr.controller.requirements

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.protocol.RegulationDatabase

class RequirementsConformanceDslSpec {
    private val continueApproachCase = RequirementsConformanceCase(
        id = "continue-approach-runway-obstructed",
        family = "circuit_go_around_after_landing_clearance",
        expectations = listOf(
            SourceUnitEvidenceExpectation(
                sourceUnit = SourceUnitRef("cap413-extracted::ch4_4_50_to_4_60::3e867581f0f695b2"),
                acceptedProxyRegulations = setOf(RegulationDatabase.CAP413_4_55),
            ),
            SourceUnitEvidenceExpectation(
                sourceUnit = SourceUnitRef("cap413-extracted::ch4_4_50_to_4_60::36206ebf73ce35e5"),
                acceptedProxyRegulations = setOf(RegulationDatabase.CAP413_4_56),
            ),
        ),
    )

    @Test
    fun `code-only conformance case accepts current DecisionTrace evidence proxy`() {
        val trace = DecisionTrace(
            ruleId = "ARR-CONTINUE-APPROACH-OBSTRUCTION",
            description = "continue approach while runway obstruction is expected to clear",
            regulations = listOf(
                RegulationDatabase.CAP413_4_55,
                RegulationDatabase.CAP413_4_56,
                RegulationDatabase.ICAO4444_12_3_4_16,
                RegulationDatabase.ICAO4444_8_9_6_1_8,
            ),
        )

        continueApproachCase.assertSatisfiedBy(trace)
    }

    @Test
    fun `code-only conformance case failure names missing source unit`() {
        val trace = DecisionTrace(
            ruleId = "ARR-CONTINUE-APPROACH-OBSTRUCTION",
            description = "continue approach while runway obstruction is expected to clear",
            regulations = listOf(RegulationDatabase.CAP413_4_55),
        )

        val failure = assertFailsWith<AssertionError> {
            continueApproachCase.assertSatisfiedBy(trace)
        }
        assertTrue(
            failure.message.orEmpty().contains("cap413-extracted::ch4_4_50_to_4_60::36206ebf73ce35e5"),
            "Expected failure message to name the missing source unit; got ${failure.message}",
        )
    }
}
