package xyz.easiersaid.twr.sim.reviewer

import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.*
import xyz.easiersaid.twr.sim.*

/**
 * Claim-probe for the `shared_frequency_readback_overlap` surface.
 *
 * Claim: "when two transmissions overlap on the same ATC frequency, the step-on
 * mechanism prevents either from being delivered — including pilot readbacks."
 *
 * This is a pure comms-layer probe. No [SimEvent.ControllerCycle] events are injected,
 * so [SimState.controllerInbox] is the authoritative "was the readback delivered?"
 * register (it accumulates when no cycle drains it). The probe mirrors the pattern
 * established in [CommsStepOnTest].
 *
 * **Nominal scenario** — `CLEAN_DELIVERY`: a single pilot→controller readback
 * transmission with no overlapping traffic. The readback is delivered to the
 * controller inbox undisturbed.
 *
 * **Attack scenario** — `STEP_ON_READBACK`: two transmissions whose on-air windows
 * overlap on the shared frequency. Both are marked stepped-on; the controller inbox
 * stays empty. Issue detector `missing_required_ack_before_state_change` fires.
 *
 * Success checks (both proxy to "inbox is not empty"):
 *   - `required_readback_received_on_same_frequency`
 *   - `no_progress_before_ack` (no ControllerCycle ran → no state change before readback)
 *
 * Issue detector:
 *   - `missing_required_ack_before_state_change` — fires when inbox is empty after attack.
 */
object SharedFrequencyClaimProbeExecutor {

    const val SURFACE_ID = "shared_frequency_readback_overlap"

    private val aerodromeId = AerodromeId("TEST")
    private val standId = PointId("STAND-A")
    private val alphaId = AircraftId("ALPHA")
    private val controllerId = ControllerId("TWR")
    private val sharedFrequency = Frequency.unsafe("118.100")

    private val SUCCESS_CHECKS = setOf(
        "required_readback_received_on_same_frequency",
        "no_progress_before_ack",
    )
    private val ATTACK_DETECTORS = setOf("missing_required_ack_before_state_change")

    private val worldIndex = WorldIndex(
        positions = mapOf(standId to Position(xMeters = 0.0, yMeters = 0.0)),
        entitiesByPoint = mapOf(standId to setOf(EntityRef.StandRef(StandId("STAND-A")))),
    )

    private fun baseState(): SimState = SimState.initial(
        seed = 42L,
        worldIndex = worldIndex,
        aircraft = listOf(
            AircraftState(
                id = alphaId,
                callsign = Callsign("ALPHA"),
                position = worldIndex.positions.getValue(standId),
                positionPoint = standId,
                pilotGoal = PilotGoal.DEPART,
                humanPiloted = false,
                route = PilotRoute.None,
                phase = PilotPhase.AtStand,
            ),
        ),
        controllers = listOf(
            ControllerSpec(
                id = controllerId,
                role = RoleName.TOWER,
                aerodromeId = aerodromeId,
                frequency = sharedFrequency,
                responsibilities = setOf(alphaId),
            ),
        ),
    ).copy(nextTransmissionId = 100L)

    /**
     * Inject a pilot→controller readback transmission with a known start time.
     * [txId] must be unique within the simulation run.
     */
    private fun pilotReadbackTx(txId: Long, startAt: SimTime): InFlightTransmission {
        val utterance = Utterance.FromPilot(
            Readback(elements = listOf(SimpleElement(RunwayReadback(RunwayId("18")))))
        )
        return InFlightTransmission(
            id = TransmissionId(txId),
            speaker = SpeakerRef.Pilot(alphaId),
            receiver = ReceiverRef.Controller(controllerId),
            frequency = sharedFrequency,
            utterance = utterance,
            startedAt = startAt,
            endsAt = startAt + utteranceDuration(utterance),
        )
    }

    fun execute(): ClaimProbeExecutionReport {
        val nominal = runNominal()
        val attack = runAttack()
        return ClaimProbeExecutionReport(
            surfaceId = SURFACE_ID,
            nominalReport = nominal,
            attackReports = listOf(attack),
            disposition = computeDisposition(nominal, listOf(attack)),
        )
    }

    /**
     * Nominal: one undisturbed pilot readback. Controller inbox should contain it.
     */
    fun runNominal(): NominalReport {
        val t0 = SimTime.ZERO
        val tx = pilotReadbackTx(txId = 1L, startAt = t0)
        val result = runUntil(
            initial = baseState(),
            initialEvents = listOf(SimEvent.TransmissionStart(time = tx.startedAt, transmission = tx)),
            until = SimTime.ofSeconds(30),
        )
        val readbackArrived = result.controllerInbox[controllerId].orEmpty().isNotEmpty()
        return if (readbackArrived) {
            NominalReport(satisfiedSuccessChecks = SUCCESS_CHECKS, unsatisfiedSuccessChecks = emptySet())
        } else {
            NominalReport(satisfiedSuccessChecks = emptySet(), unsatisfiedSuccessChecks = SUCCESS_CHECKS)
        }
    }

    /**
     * Attack: two overlapping transmissions on the same frequency — both stepped on.
     * Controller inbox must be empty; `missing_required_ack_before_state_change` fires.
     */
    fun runAttack(): AttackReport {
        val t0 = SimTime.ZERO
        val txA = pilotReadbackTx(txId = 1L, startAt = t0)
        // Second transmission starts halfway through the first — guaranteed overlap.
        val overlapAt = SimTime.ofMillis(txA.endsAt.millis / 2)
        val txB = pilotReadbackTx(txId = 2L, startAt = overlapAt)
        val result = runUntil(
            initial = baseState(),
            initialEvents = listOf(
                SimEvent.TransmissionStart(time = txA.startedAt, transmission = txA),
                SimEvent.TransmissionStart(time = txB.startedAt, transmission = txB),
            ),
            until = SimTime.ofSeconds(30),
        )
        val readbackMissing = result.controllerInbox[controllerId].orEmpty().isEmpty()
        val triggered = if (readbackMissing) ATTACK_DETECTORS else emptySet()
        return AttackReport(
            attackId = "STEP_ON_READBACK",
            triggeredIssueDetectors = triggered,
            unsupportedDetectors = emptySet(),
        )
    }
}
