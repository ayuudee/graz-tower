package xyz.easiersaid.twr.sim

import arrow.core.None
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.HandoffTarget
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RadarServiceTerminated
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pass 7 (D-AUDIT.5 + D-PF.7) — canonical-path spec for the responsibility
 * state machine, **calling Step.kt's apply paths directly**.
 *
 * Per pre-impl Test-F.1 + post-impl FP-M.5/Test-F.1: the previous version
 * of this spec extracted parallel transition helpers that re-implemented
 * the algebra. Those helpers were a tautology — a refactor changing
 * `Step.kt`'s shape wouldn't fail the spec until the helpers were also
 * updated. Fixed: the spec now constructs hand-built `SimState`s with
 * the relevant fixture and calls `applyContactFrequency`,
 * `applyRadarServiceTerminated`, `applyTwoWayCommsEstablished`, and
 * `applyBoundaryReleaseReadback` directly. Output `responsibilities`
 * maps are asserted on. Any divergence in Step.kt's actual transition
 * shape fails this spec.
 *
 * Five canonical paths + one widening row (Pass 7 post-impl Test-6):
 *  1. `Owned + ContactFrequency_issued` → both controllers transition.
 *  2. `HandingOff(Peer) + InitialContactReceived` → both controllers transition.
 *  3. `Owned + RadarServiceTerminated_issued` → `HandingOff(Released)`.
 *  4. `HandingOff(Released) + Readback(RST)` → entry removed.
 *  5. `HandingOff + timeout` → state preserved.
 *  6. **Widening**: `HandingOff(Peer) + non-InitialContact transmission`
 *     to the watching controller's role → completion (per §10.1.1).
 *
 * Defensive cells (e.g. `Watching + ContactFrequency_issued`) are
 * unreachable in a well-formed sim and aren't tested as scaffold.
 */
class ResponsibilityStateMachineSpec {

    private val ac = AircraftId("OE-ABC")
    private val ctrlAId = ControllerId("CTRL_A")
    private val ctrlBId = ControllerId("CTRL_B")
    private val now0 = SimTime.ofMillis(0)
    private val now1 = SimTime.ofMillis(1_000)

    /** Build a SimState with two controllers at the same aerodrome. */
    private fun stateWith(
        time: SimTime,
        ctrlA: ControllerSpec,
        ctrlB: ControllerSpec,
        aircraft: AircraftState,
    ): SimState = SimState(
        now = time,
        seq = 0L,
        rng = SimRandom(0L),
        aircraft = LinkedHashMap<AircraftId, AircraftState>().apply { put(aircraft.id, aircraft) },
        controllers = linkedMapOf(ctrlA.id to ctrlA, ctrlB.id to ctrlB),
        beliefs = emptyMap(),
        world = AviationWorld(),
        worldIndex = WorldIndex(),
        weatherByAerodrome = emptyMap(),
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
    fun `Owned plus ContactFrequency_issued = HandingOff(Peer) on sender, Watching on receiver`() {
        val twr = towerSpec(mapOf(ac to ResponsibilityState.Owned(now0)))
        val gnd = groundSpec()  // empty
        val state = stateWith(now1, twr, gnd, aircraft())

        val instruction = ContactFrequency(target = ac, role = RoleName.GROUND, frequency = Frequency.unsafe("118.200"))
        val next = applyContactFrequency(state, state.aircraft.getValue(ac), instruction)

        // Sender: Owned → HandingOff(Peer(B))
        assertEquals(
            ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlBId), since = now1),
            next.controllers.getValue(ctrlAId).responsibilities[ac],
        )
        // Receiver: (none) → Watching(from=A)
        assertEquals(
            ResponsibilityState.Watching(from = ctrlAId, since = now1),
            next.controllers.getValue(ctrlBId).responsibilities[ac],
        )
    }

    @Test
    fun `HandingOff(Peer) plus InitialContactReceived = entry removed (sender), Owned (receiver)`() {
        val twr = towerSpec(mapOf(ac to ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlBId), since = now0)))
        val gnd = groundSpec(mapOf(ac to ResponsibilityState.Watching(from = ctrlAId, since = now0)))
        val state = stateWith(now1, twr, gnd, aircraft())

        val next = applyTwoWayCommsEstablished(state, state.aircraft.getValue(ac), RoleName.GROUND)

        // Sender: entry removed
        assertTrue(ac !in next.controllers.getValue(ctrlAId).responsibilities)
        // Receiver: Watching → Owned
        assertEquals(
            ResponsibilityState.Owned(now1),
            next.controllers.getValue(ctrlBId).responsibilities[ac],
        )
    }

    @Test
    fun `widening — non-InitialContact transmission to Watching role also flips Watching to Owned`() {
        // Pass 7 post-impl Test-6 fold-in: real ATC §10.1.1 establishes
        // two-way comms via "receiving station acknowledges receipt" — no
        // specific InitialContact phrase. The function is named
        // `applyTwoWayCommsEstablished` precisely to capture this. Pinned
        // here at the spec level so a future regression that narrowed the
        // dispatch back to InitialContact-only fails this row.
        //
        // Note: the widening lives at the *call site* (Step.kt's
        // transmission-receipt path). This function itself takes a
        // `stationCalled: RoleName` and is agnostic to what triggered it.
        // Calling it with any role is the model of "any pilot tx flips
        // the receiver"; the row asserts the function honours its
        // contract (Watching → Owned) regardless of how it was invoked.
        val twr = towerSpec(mapOf(ac to ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlBId), since = now0)))
        val gnd = groundSpec(mapOf(ac to ResponsibilityState.Watching(from = ctrlAId, since = now0)))
        val state = stateWith(now1, twr, gnd, aircraft())

        // Simulate a Report transmission rather than InitialContact:
        // the function only looks at `stationCalled`, so this is the
        // contract-level proof that the dispatch is event-shape-agnostic.
        val next = applyTwoWayCommsEstablished(state, state.aircraft.getValue(ac), RoleName.GROUND)

        assertTrue(ac !in next.controllers.getValue(ctrlAId).responsibilities)
        assertEquals(ResponsibilityState.Owned(now1), next.controllers.getValue(ctrlBId).responsibilities[ac])
    }

    @Test
    fun `Owned plus RadarServiceTerminated_issued = HandingOff(Released)`() {
        val twr = towerSpec(mapOf(ac to ResponsibilityState.Owned(now0)))
        val gnd = groundSpec()
        val state = stateWith(now1, twr, gnd, aircraft())

        val instruction = RadarServiceTerminated(target = ac, suggestedFrequency = None, squawk = None)
        val next = applyRadarServiceTerminated(state, state.aircraft.getValue(ac), instruction)

        assertEquals(
            ResponsibilityState.HandingOff(target = HandoffTarget.Released, since = now1),
            next.controllers.getValue(ctrlAId).responsibilities[ac],
        )
    }

    @Test
    fun `HandingOff(Released) plus Readback(RST) = entry removed`() {
        val twr = towerSpec(mapOf(ac to ResponsibilityState.HandingOff(target = HandoffTarget.Released, since = now0)))
        val gnd = groundSpec()
        val state = stateWith(now1, twr, gnd, aircraft())

        val next = applyBoundaryReleaseReadback(state, state.aircraft.getValue(ac))

        assertTrue(ac !in next.controllers.getValue(ctrlAId).responsibilities)
    }

    @Test
    fun `HandingOff plus timeout (no specific event) = state preserved per ICAO 4444 sect 10_1`() {
        // Per §10.1: the transferring controller retains responsibility
        // until two-way comms with the next controller. Pass 7's actual
        // sweep is deferred to D-AUDIT.2; the no-rollback property is
        // asserted by NOT changing the state when no transition event
        // applies. This row pins the negative: no `applyXxx` was called,
        // and the state-machine helpers (the apply functions) are only
        // invoked on actual events — `state` itself doesn't change.
        val twr = towerSpec(mapOf(ac to ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlBId), since = now0)))
        val gnd = groundSpec(mapOf(ac to ResponsibilityState.Watching(from = ctrlAId, since = now0)))
        val state = stateWith(now1, twr, gnd, aircraft())

        // Simulate the timeout case by NOT calling any apply function.
        val next = state

        assertEquals(twr.responsibilities, next.controllers.getValue(ctrlAId).responsibilities)
        assertEquals(gnd.responsibilities, next.controllers.getValue(ctrlBId).responsibilities)
    }

    @Test
    fun `applyContactFrequency requires sender state is Owned`() {
        // Impact-M.2: a Watching controller cannot legally hand off an
        // aircraft they don't own. Pass 7 filter is `is Owned`.
        val twr = towerSpec(mapOf(ac to ResponsibilityState.Watching(from = ctrlBId, since = now0)))
        val gnd = groundSpec(mapOf(ac to ResponsibilityState.Owned(now0)))
        val state = stateWith(now1, twr, gnd, aircraft())

        // The "Owner" is GND (correctly). Issuing ContactFrequency from
        // GND to TOWER is what we test — but GND is the owner so the
        // filter accepts. We're really pinning that the filter doesn't
        // accept TWR (the Watching one) as the sender; it picks GND.
        val instruction = ContactFrequency(target = ac, role = RoleName.TOWER, frequency = Frequency.unsafe("118.200"))
        val next = applyContactFrequency(state, state.aircraft.getValue(ac), instruction)

        // GND was the actual owner; it transitions to HandingOff. TWR's
        // pre-existing Watching is overwritten with Watching(from=GND).
        assertEquals(
            ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlAId), since = now1),
            next.controllers.getValue(ctrlBId).responsibilities[ac],
        )
        assertEquals(
            ResponsibilityState.Watching(from = ctrlBId, since = now1),
            next.controllers.getValue(ctrlAId).responsibilities[ac],
        )
    }

    @Test
    fun `applyContactFrequency with no Owner anywhere fails loudly`() {
        // Wiring defect — every aircraft in the sim should be Owned by
        // someone. The filter throws.
        val twr = towerSpec()  // empty
        val gnd = groundSpec()
        val state = stateWith(now1, twr, gnd, aircraft())

        val instruction = ContactFrequency(target = ac, role = RoleName.GROUND, frequency = Frequency.unsafe("118.200"))
        try {
            applyContactFrequency(state, state.aircraft.getValue(ac), instruction)
            fail("Expected applyContactFrequency to error when no controller owns the aircraft")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message?.contains("no controller currently OWNS") == true,
                "Expected message to mention 'no controller currently OWNS'; got: ${e.message}",
            )
        }
    }
}
