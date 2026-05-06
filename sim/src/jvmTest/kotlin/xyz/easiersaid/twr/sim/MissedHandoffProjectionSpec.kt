package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.HandoffTarget
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pass 12 (D-PF.9) — `outgoingMissedHandoffs` projection contract.
 *
 * Three rows:
 *  1. Sender controller's view contains the aircraft when sim's
 *     handoffEscalations has an entry for (sender, ac).
 *  2. Non-sender controller (the receiving target) gets empty.
 *  3. The notice carries the right targetRole + targetFrequency from
 *     the sim's controller spec.
 */
class MissedHandoffProjectionSpec {

    private val ac = AircraftId("OE-ABC")
    private val ctrlGndId = ControllerId("LOWG_GROUND")
    private val ctrlTwrId = ControllerId("LOWG_TOWER")
    private val now0 = SimTime.ZERO
    private val now1 = SimTime.ofMillis(120_000)

    private fun groundSpec(responsibilities: Map<AircraftId, ResponsibilityState> = emptyMap()): ControllerSpec =
        ControllerSpec(ctrlGndId, RoleName.GROUND, AerodromeId("LOWG"), Frequency.unsafe("121.700"), responsibilities)

    private fun towerSpec(responsibilities: Map<AircraftId, ResponsibilityState> = emptyMap()): ControllerSpec =
        ControllerSpec(ctrlTwrId, RoleName.TOWER, AerodromeId("LOWG"), Frequency.unsafe("118.200"), responsibilities)

    private fun aircraft(): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
        positionPoint = PointId("P"),
    )

    private fun stateWith(handoffEscalations: Map<HandoffEscalationKey, SimTime>): SimState {
        val gnd = groundSpec(
            mapOf(ac to ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlTwrId), since = now0)),
        )
        val twr = towerSpec(
            mapOf(ac to ResponsibilityState.Watching(from = ctrlGndId, since = now0)),
        )
        return SimState(
            now = now1,
            seq = 0L,
            rng = SimRandom(0L),
            aircraft = LinkedHashMap<AircraftId, AircraftState>().apply { put(ac, aircraft()) },
            controllers = linkedMapOf(ctrlGndId to gnd, ctrlTwrId to twr),
            beliefs = emptyMap(),
            world = AviationWorld(),
            worldIndex = WorldIndex(),
            weatherByAerodrome = emptyMap(),
            handoffEscalations = handoffEscalations,
        )
    }

    @Test
    fun `sender's view contains outgoingMissedHandoffs when handoffEscalations has the (sender, ac) entry`() {
        val key = HandoffEscalationKey(sender = ctrlGndId, aircraft = ac)
        val state = stateWith(mapOf(key to now1))
        val view = buildControllerView(state, ctrlGndId)
        assertEquals(1, view.outgoingMissedHandoffs.size)
        val notice = view.outgoingMissedHandoffs.getValue(ac)
        assertEquals(RoleName.TOWER, notice.targetRole)
        assertEquals(Frequency.unsafe("118.200"), notice.targetFrequency, "targetFrequency from staffed TOWER spec")
        assertEquals(now1, notice.since)
    }

    @Test
    fun `non-sender controller's view has empty outgoingMissedHandoffs`() {
        val key = HandoffEscalationKey(sender = ctrlGndId, aircraft = ac)
        val state = stateWith(mapOf(key to now1))
        // TWR is the *receiving* target, not the sender — should see empty.
        val twrView = buildControllerView(state, ctrlTwrId)
        assertTrue(
            twrView.outgoingMissedHandoffs.isEmpty(),
            "TWR (the receiver, not sender) must have empty outgoingMissedHandoffs",
        )
    }

    @Test
    fun `empty handoffEscalations produces empty projection`() {
        val state = stateWith(emptyMap())
        val view = buildControllerView(state, ctrlGndId)
        assertTrue(view.outgoingMissedHandoffs.isEmpty(), "no escalations → empty projection")
    }
}
