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
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pass 9 (D-AUDIT.2 / Phase 9.B) — `sweepHandoffTimeouts` contract.
 *
 * The deferment's named contract test. Five rows pin the sweep behaviour:
 *  1. After `MISSED_HANDOFF_TIMEOUT` elapses with HandingOff persisting,
 *     emit one `MissedHandoffDetected`.
 *  2. **Load-bearing**: per ICAO §10.1.2, `responsibilities` is byte-equal
 *     across the sweep — no rollback, no `since` re-anchoring.
 *  3. Re-fire dampening: at most one event per timeout window.
 *  4. No emission if `applyTwoWayCommsEstablished` resolves before timeout.
 *  5. `applyTwoWayCommsEstablished` clears `handoffEscalations` for the key.
 */
class MissedHandoffEventSpec {

    private val ac = AircraftId("OE-ABC")
    private val ctrlAId = ControllerId("CTRL_A")
    private val ctrlBId = ControllerId("CTRL_B")
    private val now0 = SimTime.ofMillis(0)
    private val timeout = MISSED_HANDOFF_TIMEOUT
    private val justAfterTimeout = now0 + timeout + SimDuration.ofMillis(1)

    private fun stateWith(
        time: SimTime,
        ctrlA: ControllerSpec,
        ctrlB: ControllerSpec,
        aircraft: AircraftState,
        handoffEscalations: Map<HandoffEscalationKey, SimTime> = emptyMap(),
    ): SimState = SimState(
        now = time,
        seq = 0L,
        rng = SimRandom(0L),
        rngByAircraft = mapOf(aircraft.id to SimRandom(1L)),
        aircraft = LinkedHashMap<AircraftId, AircraftState>().apply { put(aircraft.id, aircraft) },
        controllers = linkedMapOf(ctrlA.id to ctrlA, ctrlB.id to ctrlB),
        beliefs = emptyMap(),
        world = AviationWorld(),
        worldIndex = WorldIndex(),
        weatherByAerodrome = emptyMap(),
        handoffEscalations = handoffEscalations,
    )

    private fun towerSpec(responsibilities: Map<AircraftId, ResponsibilityState> = emptyMap()): ControllerSpec =
        ControllerSpec(ctrlAId, RoleName.TOWER, AerodromeId("LOWG"), Frequency.unsafe("118.200"), responsibilities)

    private fun groundSpec(responsibilities: Map<AircraftId, ResponsibilityState> = emptyMap()): ControllerSpec =
        ControllerSpec(ctrlBId, RoleName.GROUND, AerodromeId("LOWG"), Frequency.unsafe("118.200"), responsibilities)

    private fun aircraft(): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OE-ABC"),
        position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
        positionPoint = PointId("P"),
    )

    @Test
    fun `emits MissedHandoffDetected after timeout with HandingOff persisting`() {
        val twr = towerSpec(mapOf(ac to ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlBId), since = now0)))
        val gnd = groundSpec(mapOf(ac to ResponsibilityState.Watching(from = ctrlAId, since = now0)))
        val state = stateWith(justAfterTimeout, twr, gnd, aircraft())

        val (after, events) = sweepHandoffTimeouts(state)

        assertEquals(1, events.size, "expected one MissedHandoffDetected event")
        val ev = events.single() as SimEvent.MissedHandoffDetected
        assertEquals(ac, ev.aircraft)
        assertEquals(ctrlAId, ev.sender)
        assertEquals(ctrlBId, ev.target)
        assertEquals(now0, ev.handoffSince)
        assertEquals(justAfterTimeout, ev.time)
        // Escalation tracking recorded for re-fire dampening.
        assertEquals(justAfterTimeout, after.handoffEscalations[HandoffEscalationKey(ctrlAId, ac)])
    }

    @Test
    fun `does NOT roll back HandingOff state per ICAO 10_1_2`() {
        val handingOff = ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlBId), since = now0)
        val watching = ResponsibilityState.Watching(from = ctrlAId, since = now0)
        val twr = towerSpec(mapOf(ac to handingOff))
        val gnd = groundSpec(mapOf(ac to watching))
        val state = stateWith(justAfterTimeout, twr, gnd, aircraft())

        val (after, _) = sweepHandoffTimeouts(state)

        // Byte-equality on responsibilities — no rollback, no `since` re-anchor.
        assertEquals(
            handingOff,
            after.controllers.getValue(ctrlAId).responsibilities[ac],
            "sender's HandingOff must be byte-equal across sweep (no rollback per §10.1.2)",
        )
        assertEquals(
            watching,
            after.controllers.getValue(ctrlBId).responsibilities[ac],
            "receiver's Watching must be byte-equal across sweep (no rollback per §10.1.2)",
        )
    }

    @Test
    fun `re-fires after one full timeout window, not on every step`() {
        val twr = towerSpec(mapOf(ac to ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlBId), since = now0)))
        val gnd = groundSpec(mapOf(ac to ResponsibilityState.Watching(from = ctrlAId, since = now0)))
        // First sweep at justAfterTimeout — emits event, sets lastEscalatedAt.
        val s1 = stateWith(justAfterTimeout, twr, gnd, aircraft())
        val (after1, ev1) = sweepHandoffTimeouts(s1)
        assertEquals(1, ev1.size)

        // Second sweep less than `timeout` after the first — should be silent.
        val tooSoon = justAfterTimeout + (timeout - SimDuration.ofMillis(1))
        val s2 = after1.copy(now = tooSoon)
        val (_, ev2) = sweepHandoffTimeouts(s2)
        assertEquals(0, ev2.size, "must not re-fire within the same timeout window")

        // Third sweep one full window after the first — must re-emit.
        val nextWindow = justAfterTimeout + timeout + SimDuration.ofMillis(1)
        val s3 = after1.copy(now = nextWindow)
        val (_, ev3) = sweepHandoffTimeouts(s3)
        assertEquals(1, ev3.size, "must re-fire after one full timeout window")
    }

    @Test
    fun `does NOT emit when applyTwoWayCommsEstablished arrives before timeout`() {
        val twr = towerSpec(mapOf(ac to ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlBId), since = now0)))
        val gnd = groundSpec(mapOf(ac to ResponsibilityState.Watching(from = ctrlAId, since = now0)))
        val tooSoon = now0 + (timeout - SimDuration.ofMillis(1))
        val state = stateWith(tooSoon, twr, gnd, aircraft())

        // Pilot establishes two-way comms before timeout.
        val resolved = applyTwoWayCommsEstablished(state, state.aircraft.getValue(ac), RoleName.GROUND)
        val (_, events) = sweepHandoffTimeouts(resolved)

        assertEquals(0, events.size, "no event should fire — handoff resolved before timeout")
    }

    @Test
    fun `clears handoffEscalations on applyTwoWayCommsEstablished`() {
        val twr = towerSpec(mapOf(ac to ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlBId), since = now0)))
        val gnd = groundSpec(mapOf(ac to ResponsibilityState.Watching(from = ctrlAId, since = now0)))
        // Pre-populate with a stale escalation entry.
        val key = HandoffEscalationKey(sender = ctrlAId, aircraft = ac)
        val state = stateWith(
            justAfterTimeout,
            twr,
            gnd,
            aircraft(),
            handoffEscalations = mapOf(key to justAfterTimeout),
        )

        val resolved = applyTwoWayCommsEstablished(state, state.aircraft.getValue(ac), RoleName.GROUND)

        assertTrue(
            key !in resolved.handoffEscalations,
            "escalation entry must be cleared once handoff resolves — got ${resolved.handoffEscalations}",
        )
    }
}
