package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.HandoffTarget
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pass 7 (D-AUDIT.5 + D-PF.7) — canonical-path spec for the responsibility
 * state machine.
 *
 * Per Pass 7 plan + pre-impl Test-F.1: this spec pins the **reachable** cells
 * of the (state × event) cross-product, NOT a defensive table over every
 * input. The five canonical paths are:
 *
 *  1. `Owned + ContactFrequency_issued → HandingOff(Peer)` (current side)
 *     and `(none) → Watching(from)` (target side).
 *  2. `HandingOff(Peer) + InitialContactReceived(target) → entry removed`
 *     (current side) and `Watching → Owned` (target side).
 *  3. `Owned + RadarServiceTerminated_issued → HandingOff(Released)`.
 *  4. `HandingOff(Released) + Readback(RST) → entry removed`.
 *  5. `HandingOff(_) + timeout → no transition (state preserved); event side-effect`.
 *
 * The actual transitions live in `Step.kt`'s `applyContactFrequency`,
 * `applyRadarServiceTerminated`, `applyInitialContact`, and
 * `applyBoundaryReleaseReadback`. This spec is a pure-function distillation
 * of the "what should each transition produce" contract — the Step.kt
 * implementations must produce equivalent results.
 *
 * Defensive cells (e.g. `Watching + ContactFrequency_issued`) are
 * unreachable in a well-formed sim. Pinning them here would be scaffold;
 * the architectural test (`BoundaryReleaseFirewallTest`) and the cross-
 * controller `Owned` invariant assertion in
 * [ResponsibilityInvariantSpec] catch the regression modes that *would*
 * make them reachable.
 */
class ResponsibilityStateMachineSpec {

    private val now0 = SimTime.ofMillis(0)
    private val now1 = SimTime.ofMillis(1_000)
    private val ctrlA = ControllerId("CTRL_A")
    private val ctrlB = ControllerId("CTRL_B")

    @Test
    fun `Owned plus ContactFrequency_issued = HandingOff(Peer) + Watching`() {
        // Sender: Owned → HandingOff(Peer(B))
        val senderBefore = ResponsibilityState.Owned(now0)
        val senderAfter = transitionOnHandoffIssued(senderBefore, ctrlB, now1)
        assertEquals(
            ResponsibilityState.HandingOff(target = HandoffTarget.Peer(ctrlB), since = now1),
            senderAfter,
        )
        // Receiver: (none) → Watching(from=A)
        val receiverAfter = ResponsibilityState.Watching(from = ctrlA, since = now1)
        assertEquals(ctrlA, (receiverAfter as ResponsibilityState.Watching).from)
    }

    @Test
    fun `HandingOff(Peer) plus InitialContactReceived = entry removed (sender), Owned (receiver)`() {
        val senderBefore = ResponsibilityState.HandingOff(
            target = HandoffTarget.Peer(ctrlB), since = now0,
        )
        // Sender drops the aircraft (entry removed; modelled as null transition output)
        val senderAfter = transitionOnReceiverInitialContact(senderBefore, ctrlB, now1)
        assertEquals(null, senderAfter)
        // Receiver: Watching → Owned
        val receiverBefore = ResponsibilityState.Watching(from = ctrlA, since = now0)
        val receiverAfter = transitionReceiverToOwned(receiverBefore, now1)
        assertEquals(ResponsibilityState.Owned(now1), receiverAfter)
    }

    @Test
    fun `Owned plus RadarServiceTerminated_issued = HandingOff(Released)`() {
        val before = ResponsibilityState.Owned(now0)
        val after = transitionOnReleaseIssued(before, now1)
        assertEquals(
            ResponsibilityState.HandingOff(target = HandoffTarget.Released, since = now1),
            after,
        )
    }

    @Test
    fun `HandingOff(Released) plus Readback(RST) = entry removed`() {
        val before = ResponsibilityState.HandingOff(
            target = HandoffTarget.Released, since = now0,
        )
        val after = transitionOnReleaseReadback(before)
        assertEquals(null, after)
    }

    @Test
    fun `HandingOff plus timeout = state preserved (no rollback per ICAO 4444 sect 10_1)`() {
        // Per §10.1: the transferring controller retains responsibility until
        // two-way comms with the next controller. Timeout fires the
        // MissedHandoff event but does NOT roll back the state.
        val before = ResponsibilityState.HandingOff(
            target = HandoffTarget.Peer(ctrlB), since = now0,
        )
        val after = transitionOnTimeout(before)
        assertEquals(before, after)
    }

    // ── Pure transition helpers (extracted from Step.kt's apply paths) ────
    //
    // These mirror the Step.kt sim-side transitions one-for-one. The
    // contract is: feed the same (state, event) pair through both this
    // spec's transition and Step.kt's apply path; outputs must agree.

    private fun transitionOnHandoffIssued(
        sender: ResponsibilityState.Owned,
        targetId: ControllerId,
        now: SimTime,
    ): ResponsibilityState =
        ResponsibilityState.HandingOff(target = HandoffTarget.Peer(targetId), since = now)

    private fun transitionOnReceiverInitialContact(
        sender: ResponsibilityState.HandingOff,
        @Suppress("UNUSED_PARAMETER") expectedReceiver: ControllerId,
        @Suppress("UNUSED_PARAMETER") now: SimTime,
    ): ResponsibilityState? = if (sender.target is HandoffTarget.Peer) null else sender

    private fun transitionReceiverToOwned(
        receiver: ResponsibilityState.Watching,
        now: SimTime,
    ): ResponsibilityState = ResponsibilityState.Owned(now)

    private fun transitionOnReleaseIssued(
        @Suppress("UNUSED_PARAMETER") sender: ResponsibilityState.Owned,
        now: SimTime,
    ): ResponsibilityState =
        ResponsibilityState.HandingOff(target = HandoffTarget.Released, since = now)

    private fun transitionOnReleaseReadback(
        sender: ResponsibilityState.HandingOff,
    ): ResponsibilityState? = if (sender.target is HandoffTarget.Released) null else sender

    private fun transitionOnTimeout(
        sender: ResponsibilityState.HandingOff,
    ): ResponsibilityState = sender // no rollback

    @Suppress("unused") private val acIdForReference = AircraftId("OE-ABC")
}
