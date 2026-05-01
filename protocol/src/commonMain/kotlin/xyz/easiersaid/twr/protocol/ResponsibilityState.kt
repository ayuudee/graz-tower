package xyz.easiersaid.twr.protocol

/**
 * Per-aircraft transfer state on a controller's strip board.
 *
 * Pass 7 (D-AUDIT.5 closure) replaces the boolean
 * `aircraft ∈ responsibilities` membership with this typed state machine.
 * Real ATC's transfer-of-control has a measurable duration: the receiving
 * controller is *Watching* (sees the aircraft on radar, expects the call)
 * before the pilot calls; the sending controller is *HandingOff* (still
 * legally owns the aircraft) until two-way comms are confirmed with the
 * receiving controller (ICAO Doc 4444 §10.1).
 *
 * **Released as absence**: a fourth state ("the aircraft has left the
 * system") is the *absence* of any state on the per-aircraft map, not a
 * fourth leaf. The map's domain *is* the responsibility relation; absence
 * encodes "no relationship." The release timestamp is recorded in the
 * `SimEvent` stream (`MissedHandoff` for timeout, readback receipts for
 * normal completions) — `responsibilities` is the live state, not the
 * audit log.
 */
sealed interface ResponsibilityState {
    /** This controller has primary responsibility — talk to the aircraft directly. */
    data class Owned(val since: SimTime) : ResponsibilityState

    /**
     * This controller has issued `ContactFrequency` (or `RadarServiceTerminated`)
     * but the pilot has not yet established two-way comms with the next
     * controller. Per §10.1, the *transferring* controller retains
     * responsibility until the pilot calls the next.
     *
     * `target` is sealed (not `Option<ControllerId>`): consumers must
     * dispatch on whether this is a peer handoff or a boundary release —
     * the *reason* for the no-peer case is named.
     */
    data class HandingOff(
        val target: HandoffTarget,
        val since: SimTime,
    ) : ResponsibilityState

    /**
     * This controller has been informed (via internal coordination — the
     * shared aerodrome world view) that an aircraft is being transferred to
     * them. They watch the aircraft on radar and wait for the pilot's call.
     * On `InitialContact` to this role's frequency, transitions to [Owned].
     */
    data class Watching(
        val from: ControllerId,
        val since: SimTime,
    ) : ResponsibilityState
}

/**
 * Where a transferring controller is sending the aircraft.
 *
 * Pass 7 (D-AUDIT.5 + D-PF.7): sealed type, not `Option<ControllerId>`.
 * The [Released] leaf carries semantic meaning ("no successor; the
 * aircraft is leaving the controlled-airspace system per ICAO Doc 4444
 * §10.1.4") that `Option.None` would erase.
 */
sealed interface HandoffTarget {
    /** Normal handoff to a peer controller at the same aerodrome. */
    data class Peer(val controllerId: ControllerId) : HandoffTarget
    /** Boundary release per ICAO Doc 4444 §10.1.4 — no successor controller. */
    data object Released : HandoffTarget
}
