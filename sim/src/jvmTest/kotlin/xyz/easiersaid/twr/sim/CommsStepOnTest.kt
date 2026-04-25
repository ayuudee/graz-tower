package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import arrow.core.getOrElse
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.StandId
import xyz.easiersaid.twr.protocol.TaxiTo
import xyz.easiersaid.twr.protocol.Urgency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Step-on (4d): two transmissions whose on-air windows overlap on the same
 * frequency must *both* be garbled — neither pilot acts on the instruction.
 *
 * This is the frequency-as-shared-resource contract in one assertion. We
 * inject two [SimEvent.TransmissionStart]s directly against the step
 * function (rather than waiting for a controller cycle to produce them) so
 * the test is specifically about overlap detection, not anything upstream.
 */
class CommsStepOnTest {

    private val aerodromeId = AerodromeId("TEST")
    private val standAId: PointId = PointId("STAND-A")
    private val standBId: PointId = PointId("STAND-B")
    private val destA: PointId = PointId("DEST-A")
    private val destB: PointId = PointId("DEST-B")

    private val alphaId = AircraftId("ALPHA")
    private val bravoId = AircraftId("BRAVO")
    private val controllerId = ControllerId("GND")

    private val sharedFrequency = Frequency.unsafe("121.800")

    private val worldIndex = WorldIndex(
        positions = mapOf(
            standAId to Position(xMeters = 0.0, yMeters = 0.0),
            standBId to Position(xMeters = 10.0, yMeters = 0.0),
            destA to Position(xMeters = 100.0, yMeters = 0.0),
            destB to Position(xMeters = 110.0, yMeters = 0.0),
        ),
        adjacency = mapOf(
            standAId to setOf(destA),
            standBId to setOf(destB),
        ),
        entitiesByPoint = mapOf(
            standAId to setOf(EntityRef.StandRef(StandId("STAND-A"))),
            standBId to setOf(EntityRef.StandRef(StandId("STAND-B"))),
        ),
    )

    private fun aircraftAt(id: AircraftId, point: PointId) = AircraftState(
        id = id,
        callsign = Callsign(id.value),
        position = worldIndex.positions.getValue(point),
        positionPoint = point,
        pilotGoal = PilotGoal.DEPART,
        humanPiloted = false,
        route = PilotRoute.None,
        phase = PilotPhase.AtStand,
    )

    private val controller = ControllerSpec(
        id = controllerId,
        role = RoleName.GROUND,
        aerodromeId = aerodromeId,
        frequency = sharedFrequency,
        responsibilities = setOf(alphaId, bravoId),
    )

    private fun taxiInstruct(target: AircraftId, via: PointId, destination: PointId) =
        ControllerOutput.Instruct(
            target = target,
            dispatch = Dispatch.Direct(
                TaxiTo(target = target, via = listOf(via), destination = destination),
            ),
            urgency = Urgency.INFORMATIONAL,
            trace = DecisionTrace(
                ruleId = "TEST",
                description = "Injected for step-on test",
                regulations = emptyList(),
            ),
        )

    private fun txAt(
        id: Long,
        target: AircraftId,
        via: PointId,
        destination: PointId,
        startAt: SimTime,
    ): InFlightTransmission {
        val utterance = Utterance.FromController(taxiInstruct(target, via, destination))
        return InFlightTransmission(
            id = TransmissionId(id),
            speaker = SpeakerRef.Controller(controllerId),
            receiver = ReceiverRef.Pilot(target),
            frequency = sharedFrequency,
            utterance = utterance,
            startedAt = startAt,
            endsAt = startAt + utteranceDuration(utterance),
        )
    }

    @Test
    fun `two overlapping transmissions on the same frequency are both stepped-on and neither is delivered`() {
        val alpha = aircraftAt(alphaId, standAId)
        val bravo = aircraftAt(bravoId, standBId)
        val initial = SimState.initial(
            seed = 1L,
            worldIndex = worldIndex,
            aircraft = listOf(alpha, bravo),
            controllers = listOf(controller),
            weatherByAerodrome = emptyMap(),
        ).getOrElse { error("CommsStepOn setup invalid: $it") }
            .copy(nextTransmissionId = 100L)

        val t0 = SimTime.ZERO
        val txA = txAt(id = 1L, target = alphaId, via = standAId, destination = destA, startAt = t0)
        // Start the second transmission halfway through the first so the
        // windows clearly intersect.
        val overlapAt = SimTime.ofMillis(txA.endsAt.millis / 2)
        val txB = txAt(id = 2L, target = bravoId, via = standBId, destination = destB, startAt = overlapAt)

        val result = runUntil(
            initial = initial,
            initialEvents = listOf(
                SimEvent.TransmissionStart(time = txA.startedAt, transmission = txA),
                SimEvent.TransmissionStart(time = txB.startedAt, transmission = txB),
            ),
            until = SimTime.ofSeconds(30),
        )

        val alphaFinal = result.aircraft[alphaId]!!
        val bravoFinal = result.aircraft[bravoId]!!
        assertEquals(
            PilotRoute.None, alphaFinal.route,
            "Alpha must not have received a route — its controller transmission was stepped on",
        )
        assertEquals(
            PilotRoute.None, bravoFinal.route,
            "Bravo must not have received a route — its controller transmission was stepped on",
        )
        assertTrue(
            result.inFlightTransmissions.isEmpty(),
            "Both transmissions must have ended and been cleared from the air",
        )
        assertTrue(
            result.controllerInbox.values.all { it.isEmpty() },
            "Step-on suppresses delivery in both directions — no readback reached the controller inbox",
        )
    }
}
