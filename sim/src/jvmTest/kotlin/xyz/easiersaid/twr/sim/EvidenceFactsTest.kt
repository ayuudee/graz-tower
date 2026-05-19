package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.sim.testing.TransmissionRecord

class EvidenceFactsTest {
    @Test
    fun `synthetic protocol facts have stable provenance distinct from sim facts`() {
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")

        val synthetic = EvidenceFactAdapters.syntheticProtocolInstruction(
            scenarioId = "synthetic-takeoff",
            aircraftId = aircraft,
            instruction = ClearedForTakeoff(aircraft, runway),
        )
        val sim = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "sim-report",
            records = listOf(reportRecord(index = 0, time = SimTime.ZERO, event = ReportEvent.Ready)),
        )

        assertEquals(EvidenceFactOrigin.SyntheticProtocol, synthetic.facts.single().provenance.origin)
        assertEquals(EvidenceFactOrigin.SimRun, sim.facts.first().provenance.origin)
        assertNotEquals(synthetic.facts.single().id, sim.facts.first().id)
    }

    @Test
    fun `equal timestamp facts keep trace sequence ordering`() {
        val records = listOf(
            reportRecord(index = 0, time = SimTime.ZERO, event = ReportEvent.Ready),
            reportRecord(index = 1, time = SimTime.ZERO, event = ReportEvent.Downwind()),
        )

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "equal-time",
            records = records,
        ).orderedFacts()

        val reportFacts = facts.filter { fact -> fact.payload is EvidenceFactPayload.PilotReport }
        assertEquals(listOf(EvidenceSequence(1), EvidenceSequence(11)), reportFacts.map { fact -> fact.provenance.sequence })
        assertEquals(listOf(TransmissionId(100), TransmissionId(101)), reportFacts.map { fact -> fact.provenance.sourceTransmissionId })
        assertTrue(reportFacts.all { fact -> fact.provenance.simTime == SimTime.ZERO })
    }

    @Test
    fun `fact ids and ordering are deterministic across projection reruns`() {
        val records = listOf(
            reportRecord(index = 0, time = SimTime.ZERO, event = ReportEvent.Ready),
            reportRecord(index = 1, time = SimTime.ZERO, event = ReportEvent.RunwayVacated),
        )

        val first = EvidenceFactAdapters.fromTransmissionRecords(scenarioId = "deterministic", records = records)
        val second = EvidenceFactAdapters.fromTransmissionRecords(scenarioId = "deterministic", records = records)

        assertEquals(first.orderedFacts().map { fact -> fact.id }, second.orderedFacts().map { fact -> fact.id })
        assertEquals(
            first.orderedFacts().map { fact -> fact.provenance.sequence },
            second.orderedFacts().map { fact -> fact.provenance.sequence },
        )
    }

    @Test
    fun `LOWG circuit emits instruction report transmission and aircraft summary facts`() {
        val aircraft = AircraftId("OE-ABC")
        val facts = EvidenceFactAdapters.lowgCircuitTraining(
            scenarioId = "lowg-facts",
            outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
            untilMinutes = 45,
        )

        assertNotNull(facts.firstInstruction<TaxiToHoldingPoint>(aircraft))
        assertNotNull(facts.firstInstruction<LineUpAndWait>(aircraft))
        assertNotNull(facts.firstInstruction<ClearedForTakeoff>(aircraft))
        assertNotNull(facts.firstInstruction<ClearedTouchAndGo>(aircraft))
        assertNotNull(facts.firstInstruction<ClearedToLand>(aircraft))
        assertNotNull(facts.firstReport<ReportEvent.Ready>(aircraft))
        assertNotNull(facts.firstReport<ReportEvent.RunwayVacated>(aircraft))
        assertNotNull(facts.facts.firstOrNull { fact ->
            val summary = fact.payload as? EvidenceFactPayload.AircraftSummary ?: return@firstOrNull false
            summary.aircraftId == aircraft && summary.missionComplete
        })
        assertTrue(facts.facts.all { fact -> fact.provenance.scenarioId == "lowg-facts" })
        assertTrue(facts.facts.any { fact -> fact.provenance.sourceTransmissionId != null })
    }

    @Test
    fun `projection facts express aerodrome information critical windows and transfers`() {
        val aircraft = AircraftId("OE-ABC")

        val facts = EvidenceFactAdapters.fromProjectedPayloads(
            scenarioId = "projection-vocabulary",
            payloads = listOf(
                EvidenceFactPayload.AerodromeInformation(
                    aircraftId = aircraft,
                    timingContext = AerodromeInformationTimingContext.BeforeTaxi,
                    status = AerodromeInformationStatus.PassedByController,
                    detail = AerodromeInformationDetail("birds north of runway"),
                ),
                EvidenceFactPayload.CriticalPhaseWindow(
                    aircraftId = aircraft,
                    phase = CriticalPhaseKind.LateFinal,
                    start = EvidenceSequence(10),
                    end = EvidenceSequence(20),
                ),
                EvidenceFactPayload.CriticalPhaseTransmission(
                    aircraftId = aircraft,
                    phase = CriticalPhaseKind.LateFinal,
                    transmissionId = TransmissionId(200),
                    necessity = TransmissionNecessity.SafetyNecessary,
                ),
                EvidenceFactPayload.FrequencyTransfer(
                    aircraftId = aircraft,
                    mode = FrequencyTransferMode.ControllerAdvised,
                    target = FrequencyTransferTarget.UnitAndFrequency(
                        unitName = "ALEXANDER CONTROL",
                        frequency = "129.1",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                EvidenceFactKind.AerodromeInformation,
                EvidenceFactKind.CriticalPhaseWindow,
                EvidenceFactKind.CriticalPhaseTransmission,
                EvidenceFactKind.FrequencyTransfer,
            ),
            facts.orderedFacts().map { fact -> fact.payload.kind },
        )
    }

    @Test
    fun `critical phase window rejects reversed bounds`() {
        assertFailsWith<IllegalArgumentException> {
            EvidenceFactPayload.CriticalPhaseWindow(
                aircraftId = AircraftId("OE-ABC"),
                phase = CriticalPhaseKind.Takeoff,
                start = EvidenceSequence(20),
                end = EvidenceSequence(10),
            )
        }
    }

    @Test
    fun `initial contact with ATIS code projects known aerodrome information receipt`() {
        val aircraft = AircraftId("OE-ABC")
        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "atis-known-receipt",
            records = listOf(
                TransmissionRecord(
                    transmissionId = TransmissionId(300),
                    time = SimTime.ZERO,
                    speaker = SpeakerRef.Pilot(aircraft),
                    receiver = ReceiverRef.Controller(ControllerId("LOWG_GND")),
                    utterance = Utterance.FromPilot(
                        InitialContact(
                            stationCalled = RoleName.GROUND,
                            atisCode = 'A',
                        ),
                    ),
                ),
            ),
        )

        val information = facts.facts.mapNotNull { fact ->
            fact.payload as? EvidenceFactPayload.AerodromeInformation
        }.single()
        assertEquals(AerodromeInformationTimingContext.BeforeTaxi, information.timingContext)
        assertEquals(AerodromeInformationStatus.KnownReceivedElsewhere, information.status)
    }

    private fun reportRecord(
        index: Long,
        time: SimTime,
        event: ReportEvent,
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = TransmissionId(100 + index),
            time = time,
            speaker = SpeakerRef.Pilot(AircraftId("OE-ABC")),
            receiver = ReceiverRef.Controller(ControllerId("LOWG_TWR")),
            utterance = Utterance.FromPilot(Report(events = listOf(event))),
        )
}

private inline fun <reified I : xyz.easiersaid.twr.protocol.AtcInstruction> EvidenceFactSet.firstInstruction(
    aircraftId: AircraftId,
): EvidenceFact? =
    orderedFacts().firstOrNull { fact ->
        val instruction = fact.payload as? EvidenceFactPayload.Instruction ?: return@firstOrNull false
        instruction.aircraftId == aircraftId && instruction.instruction is I
    }

private inline fun <reified E : ReportEvent> EvidenceFactSet.firstReport(
    aircraftId: AircraftId,
): EvidenceFact? =
    orderedFacts().firstOrNull { fact ->
        val report = fact.payload as? EvidenceFactPayload.PilotReport ?: return@firstOrNull false
        report.aircraftId == aircraftId && report.events.any { event -> event is E }
    }
