package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.certify.CertificationEvidence
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pass 12 (D-PF.9) — controller-side missed-handoff re-issue contract.
 *
 * Three rows:
 *  1. Fresh notice (`since > beliefs.handoffReissuedAt[ac]`) emits one
 *     ContactFrequency Instruct.
 *  2. Same notice on subsequent cycle (after `handoffReissuedAt` is
 *     bumped) does NOT re-emit.
 *  3. New escalation window (`since` advanced beyond stored value)
 *     re-emits.
 */
class MissedHandoffReissueSpec {

    private val ac = AircraftId("OE-ABC")
    private val controllerId = ControllerId("LOWG_GROUND")
    private val targetFreq = Frequency.unsafe("118.200")
    private val now0 = SimTime.ZERO
    private val now1 = SimTime.ofMillis(120_000)
    private val now2 = SimTime.ofMillis(240_000)

    private fun viewWith(notices: Map<AircraftId, MissedHandoffNotice>): ControllerView = ControllerView(
        time = now1,
        controllerId = controllerId,
        role = RoleName.GROUND,
        aerodromeId = AerodromeId("LOWG"),
        responsibilities = setOf(ac),
        aircraft = emptyMap(),
        runways = emptyMap(),
        activeClearances = emptyMap(),
        receivedMessages = emptyList(),
        weather = null,
        worldIndex = WorldIndex(),
        outgoingMissedHandoffs = notices,
    )

    private fun notice(since: SimTime): MissedHandoffNotice = MissedHandoffNotice(
        targetRole = RoleName.TOWER,
        targetFrequency = targetFreq,
        since = since,
    )

    @Test
    fun `fresh notice emits one ContactFrequency Instruct`() {
        val view = viewWith(mapOf(ac to notice(since = now1)))
        val (outputs, newBeliefs) = missedHandoffReissueOutputs(view, BeliefState.EMPTY)

        val instructs = outputs.filterIsInstance<ControllerOutput.Instruct>()
            .map { it.instruction }
            .filterIsInstance<ContactFrequency>()
        assertEquals(1, instructs.size, "expected one ContactFrequency, got ${outputs.size} total")
        val cf = instructs.single()
        assertEquals(ac, cf.target)
        assertEquals(RoleName.TOWER, cf.role)
        assertEquals(targetFreq, cf.frequency)
        val output = outputs.filterIsInstance<ControllerOutput.Instruct>().single()
        assertTrue(
            output.certificationEvidence.all.any {
                it is CertificationEvidence.RuntimeChecked && it.checkId == "missed-handoff-reissue"
            },
            "handoff reissue must carry explicit reissue evidence",
        )

        // Belief slice is bumped to dampen next cycle.
        assertEquals(now1, newBeliefs.handoffReissuedAt[ac])
    }

    @Test
    fun `same notice on subsequent cycle does NOT re-emit`() {
        val beliefs = BeliefState.EMPTY.copy(handoffReissuedAt = mapOf(ac to now1))
        val view = viewWith(mapOf(ac to notice(since = now1)))
        val (outputs, _) = missedHandoffReissueOutputs(view, beliefs)
        assertTrue(
            outputs.isEmpty(),
            "Same `since` already responded to: must not re-emit. Got: $outputs",
        )
    }

    @Test
    fun `new escalation window re-emits`() {
        // First escalation handled (handoffReissuedAt = now1). Sim re-fires
        // at now2 (next 120s window). Notice's `since` advances; we re-emit.
        val beliefs = BeliefState.EMPTY.copy(handoffReissuedAt = mapOf(ac to now1))
        val view = viewWith(mapOf(ac to notice(since = now2)))
        val (outputs, newBeliefs) = missedHandoffReissueOutputs(view, beliefs)

        assertEquals(1, outputs.size, "new escalation window: should re-emit ContactFrequency")
        assertEquals(now2, newBeliefs.handoffReissuedAt[ac], "handoffReissuedAt advances to the new since")
    }
}
