package xyz.easiersaid.twr.sim.reviewer

import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.controller.ReceivedMessage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.CoordinationState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.*
import xyz.easiersaid.twr.sim.*

/**
 * Claim-probe for the `contact_frequency_handoff` surface.
 *
 * Claim: "when a controller issues a ContactFrequency instruction and the pilot
 * does not read back before the coordination is checked, the outstanding
 * coordination is still ISSUED — the handoff is not silently confirmed."
 *
 * This probe exercises the OR-1 fix: the coordination ledger must survive a
 * controller cycle regardless of whether the aircraft is still in
 * [ControllerSpec.responsibilities] at the time the readback is processed.
 *
 * **Setup**: SimState is pre-seeded with an ISSUED [ContactFrequency] coordination
 * in `beliefs[groundId]` for alpha. Both ground and tower controllers are registered.
 * Alpha starts in the ground controller's responsibilities.
 *
 * **Nominal scenario** — `READBACK_CONFIRMED`: a matching [FrequencyReadback] is
 * placed in `controllerInbox[groundId]` before the ControllerCycle fires.
 * After the cycle, the coordination is confirmed (removed from the ledger).
 * Success check: `coordinations[alphaId]` contains no ContactFrequency entry.
 *
 * **Attack scenario** — `NO_READBACK_BEFORE_CHECK`: no readback in the inbox.
 * After the cycle, the coordination is still ISSUED.
 * Issue detector: `handoff_reissued_while_pending` fires.
 */
object ContactFrequencyClaimProbeExecutor {

    const val SURFACE_ID = "contact_frequency_handoff"

    private val aerodromeId = AerodromeId("TEST")
    private val standId = PointId("STAND-A")
    private val alphaId = AircraftId("ALPHA")
    private val groundId = ControllerId("GND")
    private val towerId = ControllerId("TWR")
    private val groundFrequency = Frequency.unsafe("121.800")
    private val towerFrequency = Frequency.unsafe("118.100")

    private val SUCCESS_CHECKS = setOf("coordination_confirmed_on_correct_readback")
    private val ATTACK_DETECTORS = setOf("handoff_reissued_while_pending")

    private val worldIndex = WorldIndex(
        positions = mapOf(standId to Position(xMeters = 0.0, yMeters = 0.0)),
        entitiesByPoint = mapOf(standId to setOf(EntityRef.StandRef(StandId("STAND-A")))),
    )

    private val cfInstruction = ContactFrequency(target = alphaId, role = RoleName.TOWER, frequency = towerFrequency)

    /** An ISSUED coordination for the CF instruction above. */
    private fun cfCoordination(): OutstandingCoordination = OutstandingCoordination(
        aircraft = alphaId,
        instruction = cfInstruction,
        expectedReadback = setOf(FrequencyReadback(towerFrequency, RoleName.TOWER)),
        issuedAt = SimTime.ZERO,
        state = CoordinationState.ISSUED,
        advanceToStage = null,
    )

    /** A correct readback for the CF instruction. */
    private val cfReadback: Readback = Readback(
        elements = listOf(SimpleElement(FrequencyReadback(towerFrequency, RoleName.TOWER)))
    )

    private fun baseState(priorBeliefs: BeliefState, inboxMessages: List<ReceivedMessage>): SimState =
        SimState.initial(
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
                    id = groundId,
                    role = RoleName.GROUND,
                    aerodromeId = aerodromeId,
                    frequency = groundFrequency,
                    responsibilities = setOf(alphaId),
                ),
                ControllerSpec(
                    id = towerId,
                    role = RoleName.TOWER,
                    aerodromeId = aerodromeId,
                    frequency = towerFrequency,
                    responsibilities = emptySet(),
                ),
            ),
            weatherByAerodrome = emptyMap(),
        ).getOrElse { error("Probe baseState failed validation: $it") }.copy(
            beliefs = mapOf(groundId to priorBeliefs),
            controllerInbox = if (inboxMessages.isEmpty()) emptyMap()
            else mapOf(groundId to inboxMessages),
        )

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
     * Nominal: CF coordination is ISSUED, readback arrives in inbox, ControllerCycle fires.
     * After the cycle the coordination must be gone (confirmed = removed from ledger).
     */
    fun runNominal(): NominalReport {
        val priorBeliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(alphaId to listOf(cfCoordination()))
        )
        val readbackMsg = ReceivedMessage.Clear(aircraft = alphaId, transmission = cfReadback)
        val initial = baseState(priorBeliefs, listOf(readbackMsg))

        val result = runUntil(
            initial = initial,
            initialEvents = listOf(SimEvent.ControllerCycle(time = SimTime.ZERO, controllerId = groundId)),
            until = SimTime.ofSeconds(5),
        )
        val cfCoordsRemain = result.beliefs[groundId]?.coordinations?.get(alphaId)
            ?.any { it.instruction is ContactFrequency } == true

        return if (!cfCoordsRemain) {
            NominalReport(satisfiedSuccessChecks = SUCCESS_CHECKS, unsatisfiedSuccessChecks = emptySet())
        } else {
            NominalReport(satisfiedSuccessChecks = emptySet(), unsatisfiedSuccessChecks = SUCCESS_CHECKS)
        }
    }

    /**
     * Attack: CF coordination is ISSUED, no readback in inbox, ControllerCycle fires.
     * After the cycle the coordination must still be ISSUED; `handoff_reissued_while_pending` fires.
     */
    fun runAttack(): AttackReport {
        val priorBeliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(alphaId to listOf(cfCoordination()))
        )
        val initial = baseState(priorBeliefs, emptyList())

        val result = runUntil(
            initial = initial,
            initialEvents = listOf(SimEvent.ControllerCycle(time = SimTime.ZERO, controllerId = groundId)),
            until = SimTime.ofSeconds(5),
        )
        val cfCoordsStillIssued = result.beliefs[groundId]?.coordinations?.get(alphaId)
            ?.any { it.instruction is ContactFrequency && it.state == CoordinationState.ISSUED } == true

        val triggered = if (cfCoordsStillIssued) ATTACK_DETECTORS else emptySet()
        return AttackReport(
            attackId = "NO_READBACK_BEFORE_CHECK",
            triggeredIssueDetectors = triggered,
            unsupportedDetectors = emptySet(),
        )
    }
}
