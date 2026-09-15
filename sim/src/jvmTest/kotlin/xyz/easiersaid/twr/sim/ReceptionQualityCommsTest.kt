package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SayAgain
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Standby
import xyz.easiersaid.twr.sim.testing.runUntil
import xyz.easiersaid.twr.sim.testing.toTransmissionRecords

class ReceptionQualityCommsTest {
    @Test
    fun `stepped-on controller transmission produces pilot SayAgain and resolved reception-doubt evidence`() {
        val aircraftA = AircraftId("OE-ABC")
        val aircraftB = AircraftId("OE-DEF")
        val towerId = ControllerId("LOWG_TWR")
        val towerFrequency = Frequency.unsafe("118.200")
        val towerTransmission = InFlightTransmission(
            id = TransmissionId(10),
            speaker = SpeakerRef.Controller(towerId),
            receiver = ReceiverRef.Pilot(aircraftA),
            frequency = towerFrequency,
            utterance = Utterance.FromController(
                ControllerOutput.Respond(
                    target = aircraftA,
                    response = Standby(target = aircraftA),
                    trace = DecisionTrace(
                        ruleId = "TEST-COMMS-1",
                        description = "test stepped-on controller response",
                        regulations = emptyList(),
                    ),
                ),
            ),
            startedAt = SimTime.ZERO,
            endsAt = SimTime.ofMillis(2500),
        )
        val blockingTransmission = InFlightTransmission(
            id = TransmissionId(11),
            speaker = SpeakerRef.Pilot(aircraftB),
            receiver = ReceiverRef.Controller(towerId),
            frequency = towerFrequency,
            utterance = Utterance.FromPilot(Report(events = listOf(ReportEvent.Ready))),
            startedAt = SimTime.ZERO,
            endsAt = SimTime.ofMillis(2000),
        )

        val (_, events) = runUntil(
            initialState = simState(
                aircraftA = aircraftA,
                aircraftB = aircraftB,
                towerId = towerId,
                towerFrequency = towerFrequency,
            ),
            initialEvents = listOf(
                SimEvent.TransmissionStart(time = SimTime.ZERO, transmission = towerTransmission),
                SimEvent.TransmissionStart(time = SimTime.ZERO, transmission = blockingTransmission),
            ),
            untilTime = SimTime.ofSeconds(10),
        )
        val records = events.toTransmissionRecords()

        val doubtfulRecord = records.single { it.transmissionId == towerTransmission.id }
        assertEquals(
            ReceptionQuality.Doubtful(ReceptionDoubtCause.SteppedOn),
            doubtfulRecord.receptionQuality,
        )
        val sayAgainRecord = records.single { record ->
            val speaker = record.speaker as? SpeakerRef.Pilot ?: return@single false
            val utterance = record.utterance as? Utterance.FromPilot ?: return@single false
            speaker.aircraftId == aircraftA && utterance.transmission is SayAgain
        }
        assertTrue(
            sayAgainRecord.time > towerTransmission.endsAt,
            "pilot SayAgain must be transmitted after the stepped-on controller message has ended",
        )

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "comms-1-real-overlap",
            records = records,
        )
        val doubt = facts.facts
            .mapNotNull { it.payload as? EvidenceFactPayload.ReceptionDoubt }
            .single { it.aircraftId == aircraftA }
        assertEquals(towerTransmission.id, doubt.transmissionRef)
        assertEquals(ReceptionDoubtSource.SteppedOn, doubt.doubtSource)
        assertEquals(SayAgainRef(sayAgainRecord.transmissionId), doubt.resolvedBy)

        val report = simEvidence("comms-1-real-overlap") {
            observe { facts }
            source("stepped-on controller transmission resolved by pilot repetition request") {
                cites(ICAO9432.Communications.ReceptionDoubtRepetitionRequested)
                expect { receptionDoubt(aircraftA).requiresRepetitionResponse() }
            }
        }
        report.assertNoFailures()
        assertIs<EvidenceAuditOutcome.Pass>(report.results.single().outcome)
    }

    private fun simState(
        aircraftA: AircraftId,
        aircraftB: AircraftId,
        towerId: ControllerId,
        towerFrequency: Frequency,
    ): SimState =
        SimState(
            now = SimTime.ZERO,
            seq = 0L,
            rng = SimRandom(0L),
            rngByAircraft = mapOf(aircraftA to SimRandom(1L), aircraftB to SimRandom(2L)),
            aircraft = linkedMapOf(
                aircraftA to aircraft(aircraftA, "OE-ABC"),
                aircraftB to aircraft(aircraftB, "OE-DEF"),
            ),
            controllers = mapOf(
                towerId to ControllerSpec(
                    id = towerId,
                    role = RoleName.TOWER,
                    aerodromeId = AerodromeId("LOWG"),
                    frequency = towerFrequency,
                    responsibilities = emptyMap(),
                ),
            ),
            beliefs = emptyMap(),
            world = AviationWorld(),
            worldIndex = WorldIndex(),
            nextTransmissionId = 100L,
        )

    private fun aircraft(aircraftId: AircraftId, callsign: String): AircraftState =
        AircraftState(
            id = aircraftId,
            callsign = Callsign(callsign),
            position = Position(0.0, 0.0),
            positionPoint = PointId("P"),
        )
}
