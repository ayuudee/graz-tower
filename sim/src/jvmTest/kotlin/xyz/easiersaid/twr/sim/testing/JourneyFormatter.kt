package xyz.easiersaid.twr.sim.testing

import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.sim.SimState

/**
 * Diagnostic: render the final SimState's relevant slices plus the
 * transmission stream to a multi-line string. Used in failure messages
 * to make G0-style integration tests actionable when something wedges.
 */
fun SimState.formatJourney(
    aircraftId: AircraftId,
    records: List<TransmissionRecord> = emptyList(),
): String = buildString {
    val ac = aircraft[aircraftId]
    appendLine("Final state at sim time ${now.millis} ms:")
    if (ac != null) {
        appendLine("  phase            = ${ac.phase}")
        appendLine("  positionPoint    = ${ac.positionPoint}")
        appendLine("  altitude (m)     = ${ac.altitudeM}")
        appendLine("  speed (m/s)      = ${ac.speedMps}")
        // Note: `ac.pilotMission` is `PilotMission?` (Pass 8 owns
        // AircraftState's nullable migration per the deferments register
        // narrowing). When Pass 8 lands, this `?.let` becomes `.fold(...)`.
        ac.pilotMission?.let { mission ->
            appendLine("  mission complete = ${mission.isComplete}")
            appendLine("  active task      = ${mission.currentTask?.step}")
            appendLine("  contactedOnFreq  = ${mission.contactedOnFrequency}")
        }
    } else {
        appendLine("  (aircraft $aircraftId not in state.aircraft)")
    }
    appendLine()
    appendLine("Controllers:")
    for ((id, ctrl) in controllers) {
        appendLine("  $id: role=${ctrl.role}, responsibilities=${ctrl.responsibilities}")
    }
    appendLine()
    appendLine("Beliefs:")
    for ((id, b) in beliefs) {
        appendLine("  $id:")
        appendLine("    activeRunway = ${b.activeRunway}")
        appendLine("    runwayDuty   = ${b.runwayDuty}")
        for ((acId, c) in b.commitments) {
            appendLine("    commitment[$acId] = kind=${c.kind} stage=${c.stage} contacted=${c.contacted} runway=${c.runway}")
        }
        appendLine("    pendingReadbacks: ${b.pendingReadbacks}")
    }
    if (records.isNotEmpty()) {
        appendLine()
        appendLine("Transmissions (${records.size}):")
        appendLine(records.formatAll())
    }
}
