package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.HoldPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProcedureExecutorTargetSpec {
    @Test
    fun `resolved action must target the aircraft whose procedure is executing`() {
        val procedureAircraft = AircraftId("OE-ABC")
        val other = AircraftId("OE-XYZ")

        val result = validateResolvedActionTarget(
            ruleId = "TEST-WRONG-TARGET",
            procedureAircraft = procedureAircraft,
            proposedAction = ProposedAction(HoldPosition(other)),
        )

        val failure = assertIs<RuleResolutionFailure>(result.leftOrNull())
        assertEquals(
            "Resolved action target $other does not match procedure aircraft $procedureAircraft",
            failure.reason,
        )
    }
}
