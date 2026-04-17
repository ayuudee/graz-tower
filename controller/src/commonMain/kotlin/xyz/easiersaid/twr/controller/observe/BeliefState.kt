package xyz.easiersaid.twr.controller.observe

import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.ClearanceSummary
import xyz.easiersaid.twr.controller.RunwayObservation
import xyz.easiersaid.twr.controller.assess.RunwayDutyState
import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.protocol.*

/**
 * Persistent controller belief state, carried forward between decision cycles.
 * Opaque to the simulation — only the controller reads and writes this.
 */
data class BeliefState(
    val trackedAircraft: Map<AircraftId, AircraftObservation> = emptyMap(),
    val runwayBeliefs: Map<RunwayId, RunwayObservation> = emptyMap(),
    val issuedClearances: Map<ClearanceId, ClearanceSummary> = emptyMap(),
    val commitments: Map<AircraftId, Commitment> = emptyMap(),
    val activeRunway: RunwayId? = null,
    val runwayDuty: RunwayDutyState? = null,
    /**
     * Instructions awaiting readback, keyed by aircraft. Most-recent last.
     * Populated after arbitration from outgoing [xyz.easiersaid.twr.controller.ControllerOutput.Instruct],
     * consumed by the readback validator, GC'd by age.
     */
    val pendingReadbacks: Map<AircraftId, List<PendingReadback>> = emptyMap(),
) {
    companion object {
        val EMPTY = BeliefState()
    }
}
