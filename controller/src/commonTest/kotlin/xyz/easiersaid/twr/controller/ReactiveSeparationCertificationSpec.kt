package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.assess.Intervention
import xyz.easiersaid.twr.controller.assess.WakeRule
import xyz.easiersaid.twr.controller.assess.emitReactiveOutputs
import xyz.easiersaid.twr.controller.certify.CertificationEvidence
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.SeparationAssessment
import xyz.easiersaid.twr.controller.observe.SeparationConcern
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.BreakOff
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.Urgency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReactiveSeparationCertificationSpec {
    @Test
    fun `reactive go-around with clear runway emits BreakOff with emergency certification evidence`() {
        val leader = AircraftId("LEADER")
        val follower = AircraftId("FOLLOWER")
        val assessment = SeparationAssessment(
            aircraft = leader,
            other = follower,
            currentSeparationNm = 0.5,
            requiredSeparationNm = 3.0,
            closureRateKt = 40.0,
            timeToMinimumSeconds = 0.0,
            concern = SeparationConcern.Severity.VIOLATION,
            wakeRule = WakeRule.UnknownCategory,
        )

        val outputs = emitReactiveOutputs(
            interventions = listOf(assessment to Intervention.GoAround),
            beliefs = BeliefState.EMPTY.copy(
                runwayBeliefs = mapOf(
                    RunwayId("16C") to RunwayObservation(
                        id = RunwayId("16C"),
                        status = RunwayStatus.CLEAR,
                        occupants = emptySet(),
                    ),
                ),
            ),
        )

        val instruct = outputs.filterIsInstance<ControllerOutput.Instruct>().single()
        assertEquals(follower, instruct.target)
        assertEquals(Urgency.SAFETY, instruct.urgency)
        assertIs<BreakOff>(instruct.instruction)
        assertTrue(
            instruct.certificationEvidence.all.any { it is CertificationEvidence.EmergencyPolicy },
            "reactive safety output must carry emergency policy evidence",
        )
    }

    @Test
    fun `reactive go-around with occupied runway emits GoAround with emergency certification evidence`() {
        val leader = AircraftId("LEADER")
        val follower = AircraftId("FOLLOWER")
        val assessment = SeparationAssessment(
            aircraft = leader,
            other = follower,
            currentSeparationNm = 0.5,
            requiredSeparationNm = 3.0,
            closureRateKt = 40.0,
            timeToMinimumSeconds = 0.0,
            concern = SeparationConcern.Severity.VIOLATION,
            wakeRule = WakeRule.UnknownCategory,
        )

        val outputs = emitReactiveOutputs(
            interventions = listOf(assessment to Intervention.GoAround),
            beliefs = BeliefState.EMPTY.copy(
                runwayBeliefs = mapOf(
                    RunwayId("16C") to RunwayObservation(
                        id = RunwayId("16C"),
                        status = RunwayStatus.OCCUPIED_LANDING,
                        occupants = setOf(leader),
                    ),
                ),
            ),
        )

        val instruct = outputs.filterIsInstance<ControllerOutput.Instruct>().single()
        assertEquals(follower, instruct.target)
        assertEquals(Urgency.SAFETY, instruct.urgency)
        assertIs<GoAround>(instruct.instruction)
        assertTrue(
            instruct.certificationEvidence.all.any { it is CertificationEvidence.EmergencyPolicy },
            "reactive safety output must carry emergency policy evidence",
        )
    }
}
