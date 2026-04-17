package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId

/**
 * Identifies the agent that sourced a [SimEvent].
 *
 * Used as the secondary ordering key in the event queue (primary: time;
 * tertiary: sequence number). The sort is on [sortKey], which partitions agents
 * into stable classes — system-originated events win ties over controller
 * events, which win over pilot events — so replay ordering is deterministic
 * across runs.
 */
sealed interface AgentId : Comparable<AgentId> {
    val sortKey: String

    override fun compareTo(other: AgentId): Int = sortKey.compareTo(other.sortKey)

    data object System : AgentId {
        override val sortKey: String = "0-system"
    }

    data class Controller(val id: ControllerId) : AgentId {
        override val sortKey: String get() = "1-ctrl-${id.value}"
    }

    data class Pilot(val id: AircraftId) : AgentId {
        override val sortKey: String get() = "2-pilot-${id.value}"
    }
}
