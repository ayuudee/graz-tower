package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.Clearance
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.NumberInSequence
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.Request
import xyz.easiersaid.twr.protocol.RequestFrequencyChange
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Standby
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.Urgency
import xyz.easiersaid.twr.sim.testing.TransmissionRecord
import xyz.easiersaid.twr.sim.testing.toTransmissionRecords

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
    fun `rendered phraseology projection emits takeoff clearance tokens at reserved offset`() {
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")
        val facts = EvidenceFactAdapters.lowgCircuitTraining(
            scenarioId = "rendered-takeoff",
            outcomes = listOf(CircuitOutcome.FullStop),
            untilMinutes = 45,
        )

        val fact = facts.facts.single { fact ->
            val payload = fact.payload as? EvidenceFactPayload.RenderedPhraseology ?: return@single false
            payload.template == RenderedPhraseologyTemplate.TakeoffClearance && payload.aircraftId == aircraft
        }
        val payload = fact.payload as EvidenceFactPayload.RenderedPhraseology
        assertEquals(aircraft, payload.aircraftId)
        assertEquals(RenderedPhraseologyTemplate.TakeoffClearance, payload.template)
        assertEquals(
            setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
                PhraseologyObligationKind.ForbiddenMeaning,
            ),
            payload.obligationKinds,
        )
        assertEquals(
            listOf(
                PhraseologyToken.AircraftCallsign(aircraft),
                PhraseologyToken.Runway,
                PhraseologyToken.RunwayDesignator(runway),
                PhraseologyToken.Cleared,
                PhraseologyToken.For,
                PhraseologyToken.TakeOff,
            ),
            payload.tokens,
        )
        assertEquals(RenderedPhraseText("OE-ABC RUNWAY 16C CLEARED FOR TAKE-OFF"), payload.text)
        assertEquals(payload.transmissionRef, fact.provenance.sourceTransmissionId)
        assertEquals(9, fact.provenance.sequence.value % 10)
        assertEquals(
            fact.provenance.sequence.value - 9,
            facts.orderedFacts()
                .single { candidate ->
                    candidate.provenance.sourceTransmissionId == payload.transmissionRef &&
                        candidate.payload is EvidenceFactPayload.Instruction
                }
                .provenance.sequence.value,
        )
        assertTrue(fact.provenance.extractionPath.value.endsWith(".controller.renderedPhraseology"))
    }

    @Test
    fun `rendered phraseology projection emits touch-and-go clearance tokens`() {
        val aircraft = AircraftId("OE-ABC")
        val facts = EvidenceFactAdapters.lowgCircuitTraining(
            scenarioId = "rendered-touch-and-go",
            outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
            untilMinutes = 45,
        )

        val payload = facts.facts
            .mapNotNull { fact -> fact.payload as? EvidenceFactPayload.RenderedPhraseology }
            .filter { payload -> payload.template == RenderedPhraseologyTemplate.TouchAndGoClearance }
            .single()
        assertEquals(RenderedPhraseologyTemplate.TouchAndGoClearance, payload.template)
        assertEquals(
            listOf(
                PhraseologyToken.AircraftCallsign(aircraft),
                PhraseologyToken.Cleared,
                PhraseologyToken.Touch,
                PhraseologyToken.And,
                PhraseologyToken.Go,
            ),
            payload.tokens,
        )
        assertEquals(RenderedPhraseText("OE-ABC CLEARED TOUCH AND GO"), payload.text)
    }

    @Test
    fun `rendered phraseology projection emits typed unsupported evidence for clear unsupported instructions`() {
        val aircraft = AircraftId("OE-ABC")
        val unsupported = controllerInstructionRecord(
            index = 0,
            instruction = ContactFrequency(
                target = aircraft,
                role = RoleName.TOWER,
                frequency = Frequency.unsafe("118.500"),
            ),
        )

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "rendered-unsupported-clear",
            records = listOf(unsupported),
        )

        assertTrue(facts.facts.none { fact -> fact.payload is EvidenceFactPayload.RenderedPhraseology })
        val unsupportedPayload = facts.facts
            .mapNotNull { fact -> fact.payload as? EvidenceFactPayload.UnsupportedRenderedPhraseology }
            .single()
        assertEquals(aircraft, unsupportedPayload.aircraftId)
        assertEquals(unsupported.transmissionId, unsupportedPayload.transmissionRef)
        assertTrue(unsupportedPayload.instruction is ContactFrequency)
    }

    @Test
    fun `rendered phraseology projection emits no phraseology evidence for doubtful unsupported instructions`() {
        val aircraft = AircraftId("OE-ABC")
        val doubtfulUnsupported = controllerInstructionRecord(
            index = 0,
            instruction = ContactFrequency(
                target = aircraft,
                role = RoleName.TOWER,
                frequency = Frequency.unsafe("118.500"),
            ),
        ).copy(receptionQuality = ReceptionQuality.Doubtful(ReceptionDoubtCause.SteppedOn))

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "rendered-unsupported-doubtful",
            records = listOf(doubtfulUnsupported),
        )

        assertTrue(facts.facts.none { fact -> fact.payload is EvidenceFactPayload.RenderedPhraseology })
        assertTrue(facts.facts.none { fact -> fact.payload is EvidenceFactPayload.UnsupportedRenderedPhraseology })
    }

    @Test
    fun `unsupported phraseology sequence offset is unique within controller instruction record`() {
        val aircraft = AircraftId("OE-ABC")
        val sequences = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "rendered-sequence-unique",
            records = listOf(
                controllerInstructionRecord(
                    index = 0,
                    instruction = ContactFrequency(
                        target = aircraft,
                        role = RoleName.TOWER,
                        frequency = Frequency.unsafe("118.500"),
                    ),
                ),
            ),
        ).orderedFacts().map { fact -> fact.provenance.sequence.value }

        assertTrue(0 in sequences)
        assertTrue(9 in sequences)
        assertEquals(sequences.toSet().size, sequences.size)
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
    fun `critical phase projection emits routine controller transmission for each ICAO protected phase`() {
        val aircraft = AircraftId("OE-ABC")
        val phaseCases = mapOf(
            PilotPhase.TakeoffRoll to CriticalPhaseKind.Takeoff,
            PilotPhase.Climbing to CriticalPhaseKind.InitialClimb,
            PilotPhase.Final to CriticalPhaseKind.LateFinal,
            PilotPhase.LandingRoll to CriticalPhaseKind.LandingRoll,
        )

        phaseCases.entries.forEachIndexed { index, (pilotPhase, expectedCriticalPhase) ->
            val record = controllerInstructionRecord(
                index = index,
                instruction = ContactFrequency(
                    target = aircraft,
                    role = RoleName.TOWER,
                    frequency = Frequency.unsafe("118.500"),
                ),
            )
            val facts = EvidenceFactAdapters.fromTransmissionRecords(
                scenarioId = "critical-phase-projection::$pilotPhase",
                records = listOf(record),
                phaseAtTransmission = mapOf(record.transmissionId to pilotPhase),
            )

            val fact = facts.facts.single {
                it.payload is EvidenceFactPayload.CriticalPhaseTransmission
            }
            val payload = fact.payload as EvidenceFactPayload.CriticalPhaseTransmission
            assertEquals(aircraft, payload.aircraftId)
            assertEquals(expectedCriticalPhase, payload.phase)
            assertEquals(record.transmissionId, payload.transmissionId)
            assertEquals(TransmissionNecessity.Routine, payload.necessity)
            assertEquals(EvidenceSequence(7), fact.provenance.sequence)
            assertEquals(record.transmissionId, fact.provenance.sourceTransmissionId)
        }
    }

    @Test
    fun `critical phase projection is scoped to the controller transmission target aircraft`() {
        val aircraftA = AircraftId("OE-ABC")
        val aircraftB = AircraftId("OE-DEF")
        val recordToA = controllerInstructionRecord(
            index = 0,
            instruction = ContactFrequency(
                target = aircraftA,
                role = RoleName.TOWER,
                frequency = Frequency.unsafe("118.500"),
            ),
        )
        val recordToB = controllerInstructionRecord(
            index = 1,
            instruction = ContactFrequency(
                target = aircraftB,
                role = RoleName.TOWER,
                frequency = Frequency.unsafe("118.500"),
            ),
        )

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "critical-phase-target-scope",
            records = listOf(recordToA, recordToB),
            phaseAtTransmission = mapOf(
                recordToA.transmissionId to PilotPhase.Taxiing,
                recordToB.transmissionId to PilotPhase.Final,
            ),
        )

        val criticalTransmissions = facts.facts.mapNotNull {
            it.payload as? EvidenceFactPayload.CriticalPhaseTransmission
        }
        assertEquals(1, criticalTransmissions.size)
        assertEquals(aircraftB, criticalTransmissions.single().aircraftId)
        assertEquals(recordToB.transmissionId, criticalTransmissions.single().transmissionId)
    }

    @Test
    fun `criticalPhase selector fails source-mapped audit when routine controller transmission is projected`() {
        val aircraft = AircraftId("OE-ABC")
        val record = controllerInstructionRecord(
            index = 0,
            instruction = ContactFrequency(
                target = aircraft,
                role = RoleName.TOWER,
                frequency = Frequency.unsafe("118.500"),
            ),
        )
        val projectedTransmissionFacts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "critical-phase-routine-fail",
            records = listOf(record),
            phaseAtTransmission = mapOf(record.transmissionId to PilotPhase.Final),
        )
        val factSet = projectedTransmissionFacts.copy(
            facts = projectedTransmissionFacts.facts + criticalPhaseWindowFact(
                scenarioId = "critical-phase-routine-fail",
                aircraft = aircraft,
            ),
        )

        val report = simEvidence("critical-phase-routine-fail") {
            observe { factSet }
            source("routine controller transmission during critical phase") {
                cites(ICAO9432.CriticalPhase.CriticalPhaseRadioSilence)
                expect { criticalPhase(aircraft).routineControllerTransmissions().none() }
            }
        }

        val result = report.results.single()
        assertTrue(
            result.outcome is EvidenceAuditOutcome.Fail,
            "expected source-mapped Fail for projected routine critical-phase transmission; got ${result.outcome}",
        )
        assertTrue(
            result.activationFactIds.isNotEmpty(),
            "critical-phase selector must activate both window and transmission evidence on Fail",
        )
    }

    @Test
    fun `ground-station test-signal payload rejects reversed bounds and mismatched duration`() {
        val station = ControllerId("LOWG_TWR")

        assertFailsWith<IllegalArgumentException> {
            EvidenceFactPayload.GroundStationTestSignal(
                stationId = station,
                transmissionRef = TransmissionId(830),
                purpose = TestSignalPurpose.TransmitterAdjustment,
                startedAt = SimTime.ofSeconds(10),
                endedAt = SimTime.ofSeconds(9),
                duration = SimDuration.ofSeconds(1),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EvidenceFactPayload.GroundStationTestSignal(
                stationId = station,
                transmissionRef = TransmissionId(831),
                purpose = TestSignalPurpose.TransmitterAdjustment,
                startedAt = SimTime.ZERO,
                endedAt = SimTime.ofSeconds(10),
                duration = SimDuration.ofSeconds(9),
            )
        }
    }

    @Test
    fun `ground-station test-signal projection uses typed signal identity and actual start-end duration`() {
        val station = ControllerId("LOWG_TWR")
        val transmission = InFlightTransmission(
            id = TransmissionId(840),
            speaker = SpeakerRef.Controller(station),
            receiver = ReceiverRef.Controller(station),
            frequency = Frequency.unsafe("118.200"),
            utterance = Utterance.GroundStationTestSignal(TestSignalPurpose.TransmitterAdjustment),
            startedAt = SimTime.ofSeconds(5),
            endsAt = SimTime.ofSeconds(15),
        )
        val records = listOf(
            SimEvent.TransmissionStart(time = transmission.startedAt, transmission = transmission),
            SimEvent.TransmissionEnd(time = transmission.endsAt, transmissionId = transmission.id),
        ).toTransmissionRecords()

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "ground-station-test-signal-duration",
            records = records,
        )

        val fact = facts.facts.single { it.payload is EvidenceFactPayload.GroundStationTestSignal }
        val payload = fact.payload as EvidenceFactPayload.GroundStationTestSignal
        assertEquals(station, payload.stationId)
        assertEquals(transmission.id, payload.transmissionRef)
        assertEquals(TestSignalPurpose.TransmitterAdjustment, payload.purpose)
        assertEquals(SimTime.ofSeconds(5), payload.startedAt)
        assertEquals(SimTime.ofSeconds(15), payload.endedAt)
        assertEquals(SimDuration.ofSeconds(10), payload.duration)
        assertEquals(EvidenceSequence(8), fact.provenance.sequence)
        assertEquals(transmission.id, fact.provenance.sourceTransmissionId)
        assertEquals("sim.records[0].groundStationTestSignal", fact.provenance.extractionPath.value)
    }

    @Test
    fun `transmission record extraction allows partial slices and keeps typed transmission end time`() {
        val station = ControllerId("LOWG_TWR")
        val transmission = InFlightTransmission(
            id = TransmissionId(841),
            speaker = SpeakerRef.Controller(station),
            receiver = ReceiverRef.Controller(station),
            frequency = Frequency.unsafe("118.200"),
            utterance = Utterance.GroundStationTestSignal(TestSignalPurpose.ReceiverAdjustment),
            startedAt = SimTime.ZERO,
            endsAt = SimTime.ofSeconds(10),
        )

        val record = listOf(
            SimEvent.TransmissionStart(time = transmission.startedAt, transmission = transmission),
        ).toTransmissionRecords().single()

        assertEquals(transmission.endsAt, record.endedAt)
    }

    @Test
    fun `transmission record extraction fails loudly when an end event disagrees with transmission endsAt`() {
        val station = ControllerId("LOWG_TWR")
        val transmission = InFlightTransmission(
            id = TransmissionId(842),
            speaker = SpeakerRef.Controller(station),
            receiver = ReceiverRef.Controller(station),
            frequency = Frequency.unsafe("118.200"),
            utterance = Utterance.GroundStationTestSignal(TestSignalPurpose.ReceiverAdjustment),
            startedAt = SimTime.ZERO,
            endsAt = SimTime.ofSeconds(10),
        )

        assertFailsWith<IllegalArgumentException> {
            listOf(
                SimEvent.TransmissionStart(time = transmission.startedAt, transmission = transmission),
                SimEvent.TransmissionEnd(time = SimTime.ofSeconds(11), transmissionId = transmission.id),
            )
                .toTransmissionRecords()
        }
    }

    @Test
    fun `groundStationTestSignals selector passes at ten seconds and fails above ten seconds`() {
        val station = ControllerId("LOWG_TWR")
        val withinLimit = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "ground-signal-within-limit",
            records = listOf(
                groundStationTestSignalRecord(
                    station = station,
                    startedAt = SimTime.ZERO,
                    endedAt = SimTime.ofSeconds(10),
                ),
            ),
        )
        val overLimit = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "ground-signal-over-limit",
            records = listOf(
                groundStationTestSignalRecord(
                    station = station,
                    startedAt = SimTime.ZERO,
                    endedAt = SimTime.ofSeconds(11),
                ),
            ),
        )

        val passReport = simEvidence("ground-signal-within-limit") {
            observe { withinLimit }
            source("ground-station test signal duration") {
                cites(ICAO9432.TestProcedures.GroundStationTestSignalDuration)
                expect { groundStationTestSignals().allWithin(SimDuration.ofSeconds(10)) }
            }
        }
        val failReport = simEvidence("ground-signal-over-limit") {
            observe { overLimit }
            source("ground-station test signal duration") {
                cites(ICAO9432.TestProcedures.GroundStationTestSignalDuration)
                expect { groundStationTestSignals().allWithin(SimDuration.ofSeconds(10)) }
            }
        }

        passReport.assertNoFailures()
        assertTrue(
            failReport.results.single().outcome is EvidenceAuditOutcome.Fail,
            "expected over-limit ground-station signal to fail duration selector",
        )
        assertTrue(failReport.results.single().activationFactIds.isNotEmpty())
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
                    endedAt = SimTime.ofSeconds(2),
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

    // FN44-GAP-1: controller-advised frequency-transfer projection (R3, R12).
    @Test
    fun `controller ContactFrequency projects controller-advised frequency-transfer fact`() {
        val aircraft = AircraftId("OE-ABC")
        val records = listOf(
            controllerInstructionRecord(
                index = 0,
                instruction = ContactFrequency(
                    target = aircraft,
                    role = RoleName.TOWER,
                    frequency = Frequency.unsafe("118.500"),
                ),
            ),
        )

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "controller-advised-explicit-frequency",
            records = records,
        )

        val transfer = facts.firstFrequencyTransfer(aircraft)
        assertNotNull(transfer)
        assertEquals(FrequencyTransferMode.ControllerAdvised, transfer.mode)
        assertEquals(
            FrequencyTransferTarget.UnitAndFrequency(unitName = "TOWER", frequency = "118.500"),
            transfer.target,
        )
        assertEquals(
            EvidenceSequence(3),
            facts.orderedFacts()
                .first { it.payload is EvidenceFactPayload.FrequencyTransfer }
                .provenance.sequence,
        )
    }

    @Test
    fun `controller ContactFrequency without explicit frequency falls back to UnitOnly target`() {
        val aircraft = AircraftId("OE-XYZ")
        val records = listOf(
            controllerInstructionRecord(
                index = 0,
                instruction = ContactFrequency(target = aircraft, role = RoleName.APPROACH),
            ),
        )

        val transfer = EvidenceFactAdapters
            .fromTransmissionRecords(scenarioId = "controller-advised-implicit", records = records)
            .firstFrequencyTransfer(aircraft)

        assertNotNull(transfer)
        assertEquals(FrequencyTransferMode.ControllerAdvised, transfer.mode)
        assertEquals(FrequencyTransferTarget.UnitOnly(unitName = "APPROACH"), transfer.target)
    }

    // Pilot-notified frequency-change projection (R4, R12).
    @Test
    fun `pilot RequestFrequencyChange projects pilot-notified frequency-transfer fact`() {
        val aircraft = AircraftId("OE-ABC")
        val records = listOf(
            pilotTransmissionRecord(
                index = 0,
                aircraft = aircraft,
                transmission = Request(RequestFrequencyChange(frequency = Frequency.unsafe("123.500"))),
            ),
        )

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "pilot-notified-explicit-frequency",
            records = records,
        )

        val transfer = facts.firstFrequencyTransfer(aircraft)
        assertNotNull(transfer)
        assertEquals(FrequencyTransferMode.PilotNotifiedAbsentAdvice, transfer.mode)
        assertEquals(
            FrequencyTransferTarget.UnitAndFrequency(
                unitName = EvidenceFactAdapters.PILOT_NOTIFIED_UNIT_PLACEHOLDER,
                frequency = "123.500",
            ),
            transfer.target,
        )
        assertEquals(
            EvidenceSequence(4),
            facts.orderedFacts()
                .first { it.payload is EvidenceFactPayload.FrequencyTransfer }
                .provenance.sequence,
        )
    }

    @Test
    fun `pilot RequestFrequencyChange without explicit frequency falls back to UnitOnly target`() {
        val aircraft = AircraftId("OE-XYZ")
        val records = listOf(
            pilotTransmissionRecord(
                index = 0,
                aircraft = aircraft,
                transmission = Request(RequestFrequencyChange()),
            ),
        )

        val transfer = EvidenceFactAdapters
            .fromTransmissionRecords(scenarioId = "pilot-notified-implicit", records = records)
            .firstFrequencyTransfer(aircraft)

        assertNotNull(transfer)
        assertEquals(FrequencyTransferMode.PilotNotifiedAbsentAdvice, transfer.mode)
        assertEquals(
            FrequencyTransferTarget.UnitOnly(unitName = EvidenceFactAdapters.PILOT_NOTIFIED_UNIT_PLACEHOLDER),
            transfer.target,
        )
    }

    // R12 acceptance: explicit speaker × utterance × payload matrix.
    // {Controller, Pilot} speaker × {Controller, Pilot} utterance × {match, non-match} payload
    // ⇒ eight base combinations. Empty input + single-unrelated + mixed boundary cases
    // are exercised in dedicated tests below.
    //
    // Constructing `ControllerOutput.Instruct` requires a factory: `fromMissedHandoffReissue`
    // covers ContactFrequency (the matching controller payload); `fromAdministrative` covers
    // NumberInSequence (a non-matching controller payload that doesn't require certification).
    @Test
    fun `frequency-transfer projections are total over the explicit speaker x utterance x payload matrix`() {
        val aircraft = AircraftId("OE-ABC")
        val controllerId = ControllerId("LOWG_TWR")
        val matchingContactFrequency = ContactFrequency(
            target = aircraft,
            role = RoleName.TOWER,
            frequency = Frequency.unsafe("118.500"),
        )
        val nonMatchingInstruction = NumberInSequence.unsafe(target = aircraft, number = 1)
        val matchingRequestFrequencyChange: PilotTransmission =
            Request(RequestFrequencyChange(frequency = Frequency.unsafe("123.500")))
        val nonMatchingPilotTransmission: PilotTransmission = Report(events = listOf(ReportEvent.Ready))

        val matchingControllerOutput = ControllerOutput.Instruct.fromMissedHandoffReissue(
            instruction = matchingContactFrequency,
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("MATRIX-TEST", "matrix test ContactFrequency", emptyList()),
        )
        val nonMatchingControllerOutput = ControllerOutput.Instruct.fromAdministrative(
            instruction = nonMatchingInstruction,
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("MATRIX-TEST", "matrix test NumberInSequence", emptyList()),
        )

        val matrix = listOf(
            // Speaker = Controller, Utterance = FromController.
            Combo(
                label = "controller-speaker / controller-utterance / matching-payload",
                record = controllerOutputRecord(
                    index = 0,
                    targetAircraft = aircraft,
                    output = matchingControllerOutput,
                ),
                expectControllerAdvised = true,
                expectPilotNotified = false,
            ),
            Combo(
                label = "controller-speaker / controller-utterance / non-matching-payload",
                record = controllerOutputRecord(
                    index = 0,
                    targetAircraft = aircraft,
                    output = nonMatchingControllerOutput,
                ),
                expectControllerAdvised = false,
                expectPilotNotified = false,
            ),
            // Speaker = Controller, Utterance = FromPilot (cross-speaker; record is dropped).
            Combo(
                label = "controller-speaker / pilot-utterance / matching-payload",
                record = TransmissionRecord(
                    transmissionId = TransmissionId(900),
                    time = SimTime.ZERO,
                    endedAt = SimTime.ofSeconds(2),
                    speaker = SpeakerRef.Controller(controllerId),
                    receiver = ReceiverRef.Pilot(aircraft),
                    utterance = Utterance.FromPilot(matchingRequestFrequencyChange),
                ),
                expectControllerAdvised = false,
                expectPilotNotified = false,
            ),
            Combo(
                label = "controller-speaker / pilot-utterance / non-matching-payload",
                record = TransmissionRecord(
                    transmissionId = TransmissionId(901),
                    time = SimTime.ZERO,
                    endedAt = SimTime.ofSeconds(2),
                    speaker = SpeakerRef.Controller(controllerId),
                    receiver = ReceiverRef.Pilot(aircraft),
                    utterance = Utterance.FromPilot(nonMatchingPilotTransmission),
                ),
                expectControllerAdvised = false,
                expectPilotNotified = false,
            ),
            // Speaker = Pilot, Utterance = FromController (cross-speaker; record is dropped).
            Combo(
                label = "pilot-speaker / controller-utterance / matching-payload",
                record = TransmissionRecord(
                    transmissionId = TransmissionId(902),
                    time = SimTime.ZERO,
                    endedAt = SimTime.ofSeconds(2),
                    speaker = SpeakerRef.Pilot(aircraft),
                    receiver = ReceiverRef.Controller(controllerId),
                    utterance = Utterance.FromController(matchingControllerOutput),
                ),
                expectControllerAdvised = false,
                expectPilotNotified = false,
            ),
            Combo(
                label = "pilot-speaker / controller-utterance / non-matching-payload",
                record = TransmissionRecord(
                    transmissionId = TransmissionId(903),
                    time = SimTime.ZERO,
                    endedAt = SimTime.ofSeconds(2),
                    speaker = SpeakerRef.Pilot(aircraft),
                    receiver = ReceiverRef.Controller(controllerId),
                    utterance = Utterance.FromController(nonMatchingControllerOutput),
                ),
                expectControllerAdvised = false,
                expectPilotNotified = false,
            ),
            // Speaker = Pilot, Utterance = FromPilot.
            Combo(
                label = "pilot-speaker / pilot-utterance / matching-payload",
                record = pilotTransmissionRecord(
                    index = 0,
                    aircraft = aircraft,
                    transmission = matchingRequestFrequencyChange,
                ),
                expectControllerAdvised = false,
                expectPilotNotified = true,
            ),
            Combo(
                label = "pilot-speaker / pilot-utterance / non-matching-payload",
                record = pilotTransmissionRecord(
                    index = 0,
                    aircraft = aircraft,
                    transmission = nonMatchingPilotTransmission,
                ),
                expectControllerAdvised = false,
                expectPilotNotified = false,
            ),
        )

        matrix.forEach { combo ->
            val facts = EvidenceFactAdapters.fromTransmissionRecords(
                scenarioId = "matrix::${combo.label}",
                records = listOf(combo.record),
            )
            val controllerAdvised = facts.firstFrequencyTransfer(aircraft, FrequencyTransferMode.ControllerAdvised)
            val pilotNotified = facts.firstFrequencyTransfer(aircraft, FrequencyTransferMode.PilotNotifiedAbsentAdvice)
            assertEquals(
                combo.expectControllerAdvised,
                controllerAdvised != null,
                "controllerAdvised expectation violated for ${combo.label}",
            )
            assertEquals(
                combo.expectPilotNotified,
                pilotNotified != null,
                "pilotNotified expectation violated for ${combo.label}",
            )
        }
    }

    // Boundary case: empty input.
    @Test
    fun `frequency-transfer projection on empty input emits no facts`() {
        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "boundary-empty",
            records = emptyList(),
        )
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.FrequencyTransfer })
    }

    // Boundary case: single unrelated record.
    @Test
    fun `frequency-transfer projection on single unrelated record emits no transfer facts`() {
        val aircraft = AircraftId("OE-ABC")
        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "boundary-unrelated",
            records = listOf(reportRecord(index = 0, time = SimTime.ZERO, event = ReportEvent.Ready)),
        )
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.FrequencyTransfer })
        assertNull(facts.firstFrequencyTransfer(aircraft))
    }

    // Boundary case: mixed match and non-match across many records.
    @Test
    fun `frequency-transfer projection on mixed records emits one fact per matching record`() {
        val aircraft = AircraftId("OE-ABC")
        val records = listOf(
            controllerInstructionRecord(
                index = 0,
                instruction = ContactFrequency(
                    target = aircraft,
                    role = RoleName.TOWER,
                    frequency = Frequency.unsafe("118.500"),
                ),
            ),
            reportRecord(index = 1, time = SimTime.ZERO, event = ReportEvent.Ready),
            pilotTransmissionRecord(
                index = 2,
                aircraft = aircraft,
                transmission = Request(RequestFrequencyChange(frequency = Frequency.unsafe("123.500"))),
            ),
            controllerOutputRecord(
                index = 3,
                targetAircraft = aircraft,
                output = ControllerOutput.Instruct.fromAdministrative(
                    instruction = NumberInSequence.unsafe(target = aircraft, number = 1),
                    urgency = Urgency.PROGRESSION,
                    trace = DecisionTrace("MIXED-TEST", "non-matching controller instruction", emptyList()),
                ),
            ),
        )

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "boundary-mixed",
            records = records,
        )

        val transferFacts = facts.facts.filter { it.payload is EvidenceFactPayload.FrequencyTransfer }
        assertEquals(2, transferFacts.size)
        val modes = transferFacts.map { (it.payload as EvidenceFactPayload.FrequencyTransfer).mode }.toSet()
        assertEquals(
            setOf(
                FrequencyTransferMode.ControllerAdvised,
                FrequencyTransferMode.PilotNotifiedAbsentAdvice,
            ),
            modes,
        )
        // Sequence-offset invariant: controller-advised at +3, pilot-notified at +4.
        val controllerAdvisedSeq = transferFacts
            .first { (it.payload as EvidenceFactPayload.FrequencyTransfer).mode == FrequencyTransferMode.ControllerAdvised }
            .provenance.sequence.value
        val pilotNotifiedSeq = transferFacts
            .first { (it.payload as EvidenceFactPayload.FrequencyTransfer).mode == FrequencyTransferMode.PilotNotifiedAbsentAdvice }
            .provenance.sequence.value
        assertEquals(3, controllerAdvisedSeq)
        assertEquals(2 * 10 + 4, pilotNotifiedSeq)
    }

    // Selector primitive-level coverage (R5 hook + selector unit test).
    @Test
    fun `frequencyTransfer selector returns Pass for controllerAdvised when matching fact present`() {
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("selector-controller-advised") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "selector-controller-advised",
                    records = listOf(
                        controllerInstructionRecord(
                            index = 0,
                            instruction = ContactFrequency(
                                target = aircraft,
                                role = RoleName.TOWER,
                                frequency = Frequency.unsafe("118.500"),
                            ),
                        ),
                    ),
                )
            }
            source("controller advised present") {
                cites(ICAO9432.TransferCommunications.ControllerAdvisedFrequencyChange)
                expect { frequencyTransfer(aircraft).controllerAdvised() }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `frequencyTransfer selector returns Pass for pilotNotified when matching fact present`() {
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("selector-pilot-notified") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "selector-pilot-notified",
                    records = listOf(
                        pilotTransmissionRecord(
                            index = 0,
                            aircraft = aircraft,
                            transmission = Request(RequestFrequencyChange(frequency = Frequency.unsafe("123.500"))),
                        ),
                    ),
                )
            }
            source("pilot notified present") {
                cites(ICAO9432.TransferCommunications.PilotNotifiesAbsentAdvice)
                expect { frequencyTransfer(aircraft).pilotNotified() }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `frequencyTransfer selector returns Fail when no matching fact present`() {
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("selector-missing") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "selector-missing",
                    records = emptyList(),
                )
            }
            source("controller advised missing") {
                cites(ICAO9432.TransferCommunications.ControllerAdvisedFrequencyChange)
                expect { frequencyTransfer(aircraft).controllerAdvised() }
            }
            source("pilot notified missing") {
                cites(ICAO9432.TransferCommunications.PilotNotifiesAbsentAdvice)
                expect { frequencyTransfer(aircraft).pilotNotified() }
            }
        }

        // Activation-checked outcomes fail when there are no activation fact ids and the
        // outcome is not an expectedGap/vacuous. Either way, both source cases report Fail.
        report.results.forEach { result ->
            assertTrue(
                result.outcome is EvidenceAuditOutcome.Fail,
                "expected Fail for ${result.id} but got ${result.outcome}",
            )
        }
    }

    // COMMS-1: reception-doubt evidence primitive (R1, R12).
    //
    // The test-side transmission record now has an explicit reception-quality
    // signal. These primitive-level tests pin both halves of the adapter:
    // Clear emits no ReceptionDoubt fact across the full speaker × utterance
    // × payload matrix, while Doubtful emits one unresolved fact at the
    // reserved sequence offset. The selector construction-site tests prove
    // the payload contract without claiming covered-green for the source unit;
    // real COMMS-1 closure still requires the sim to produce doubt and
    // SayAgain resolution from operational behavior.

    @Test
    fun `reception-doubt payload accepts all sealed source leaves and optional SayAgain resolution`() {
        val aircraft = AircraftId("OE-ABC")
        val partial = EvidenceFactPayload.ReceptionDoubt(
            aircraftId = aircraft,
            transmissionRef = TransmissionId(401),
            doubtSource = ReceptionDoubtSource.PartialReception,
        )
        val unintelligible = EvidenceFactPayload.ReceptionDoubt(
            aircraftId = aircraft,
            transmissionRef = TransmissionId(402),
            doubtSource = ReceptionDoubtSource.Unintelligibility,
            resolvedBy = SayAgainRef(TransmissionId(502)),
        )
        val steppedOn = EvidenceFactPayload.ReceptionDoubt(
            aircraftId = aircraft,
            transmissionRef = TransmissionId(403),
            doubtSource = ReceptionDoubtSource.SteppedOn,
        )
        val other = EvidenceFactPayload.ReceptionDoubt(
            aircraftId = aircraft,
            transmissionRef = TransmissionId(404),
            doubtSource = ReceptionDoubtSource.Other("garbled-callsign"),
        )

        assertEquals(EvidenceFactKind.ReceptionDoubt, partial.kind)
        assertEquals("partial-reception", partial.doubtSource.label)
        assertNull(partial.resolvedBy)
        assertEquals("unintelligibility", unintelligible.doubtSource.label)
        assertEquals(TransmissionId(502), unintelligible.resolvedBy?.transmissionId)
        assertEquals("stepped-on", steppedOn.doubtSource.label)
        assertEquals("other:garbled-callsign", other.doubtSource.label)
    }

    @Test
    fun `reception-doubt 'Other' source rejects blank detail`() {
        assertFailsWith<IllegalArgumentException> {
            ReceptionDoubtSource.Other(detail = "")
        }
        assertFailsWith<IllegalArgumentException> {
            ReceptionDoubtSource.Other(detail = "   ")
        }
    }

    @Test
    fun `reception-doubt projections are total over the explicit speaker x utterance x payload matrix`() {
        // R12: every (speaker, utterance, payload) combination is exercised by the adapter
        // and produces no reception-doubt facts when the typed reception-quality signal is
        // Clear. This pins the default no-signal boundary without hiding the fact that a
        // Doubtful record now projects a real ReceptionDoubt fact.
        val aircraft = AircraftId("OE-ABC")
        val controllerId = ControllerId("LOWG_TWR")
        val matchingContactFrequency = ContactFrequency(
            target = aircraft,
            role = RoleName.TOWER,
            frequency = Frequency.unsafe("118.500"),
        )
        val nonMatchingInstruction = NumberInSequence.unsafe(target = aircraft, number = 1)
        val matchingPilotTransmission: PilotTransmission =
            Request(RequestFrequencyChange(frequency = Frequency.unsafe("123.500")))
        val nonMatchingPilotTransmission: PilotTransmission = Report(events = listOf(ReportEvent.Ready))

        val matchingControllerOutput = ControllerOutput.Instruct.fromMissedHandoffReissue(
            instruction = matchingContactFrequency,
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("DOUBT-MATRIX-TEST", "doubt matrix test ContactFrequency", emptyList()),
        )
        val nonMatchingControllerOutput = ControllerOutput.Instruct.fromAdministrative(
            instruction = nonMatchingInstruction,
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("DOUBT-MATRIX-TEST", "doubt matrix test NumberInSequence", emptyList()),
        )

        val matrix = listOf(
            "controller-speaker / controller-utterance / matching-payload" to controllerOutputRecord(
                index = 0,
                targetAircraft = aircraft,
                output = matchingControllerOutput,
            ),
            "controller-speaker / controller-utterance / non-matching-payload" to controllerOutputRecord(
                index = 0,
                targetAircraft = aircraft,
                output = nonMatchingControllerOutput,
            ),
            "controller-speaker / pilot-utterance / matching-payload" to TransmissionRecord(
                transmissionId = TransmissionId(910),
                time = SimTime.ZERO,
                endedAt = SimTime.ofSeconds(2),
                speaker = SpeakerRef.Controller(controllerId),
                receiver = ReceiverRef.Pilot(aircraft),
                utterance = Utterance.FromPilot(matchingPilotTransmission),
            ),
            "controller-speaker / pilot-utterance / non-matching-payload" to TransmissionRecord(
                transmissionId = TransmissionId(911),
                time = SimTime.ZERO,
                endedAt = SimTime.ofSeconds(2),
                speaker = SpeakerRef.Controller(controllerId),
                receiver = ReceiverRef.Pilot(aircraft),
                utterance = Utterance.FromPilot(nonMatchingPilotTransmission),
            ),
            "pilot-speaker / controller-utterance / matching-payload" to TransmissionRecord(
                transmissionId = TransmissionId(912),
                time = SimTime.ZERO,
                endedAt = SimTime.ofSeconds(2),
                speaker = SpeakerRef.Pilot(aircraft),
                receiver = ReceiverRef.Controller(controllerId),
                utterance = Utterance.FromController(matchingControllerOutput),
            ),
            "pilot-speaker / controller-utterance / non-matching-payload" to TransmissionRecord(
                transmissionId = TransmissionId(913),
                time = SimTime.ZERO,
                endedAt = SimTime.ofSeconds(2),
                speaker = SpeakerRef.Pilot(aircraft),
                receiver = ReceiverRef.Controller(controllerId),
                utterance = Utterance.FromController(nonMatchingControllerOutput),
            ),
            "pilot-speaker / pilot-utterance / matching-payload" to pilotTransmissionRecord(
                index = 0,
                aircraft = aircraft,
                transmission = matchingPilotTransmission,
            ),
            "pilot-speaker / pilot-utterance / non-matching-payload" to pilotTransmissionRecord(
                index = 0,
                aircraft = aircraft,
                transmission = nonMatchingPilotTransmission,
            ),
        )

        matrix.forEach { (label, record) ->
            val facts = EvidenceFactAdapters.fromTransmissionRecords(
                scenarioId = "doubt-matrix::$label",
                records = listOf(record),
            )
            assertTrue(
                facts.facts.none { it.payload is EvidenceFactPayload.ReceptionDoubt },
                "clear reception quality must produce no ReceptionDoubt facts for $label",
            )
        }
    }

    @Test
    fun `reception-doubt projection emits unresolved fact for each typed doubt cause`() {
        val aircraft = AircraftId("OE-ABC")
        val causes = listOf(
            ReceptionDoubtCause.PartialReception to ReceptionDoubtSource.PartialReception,
            ReceptionDoubtCause.Unintelligibility to ReceptionDoubtSource.Unintelligibility,
            ReceptionDoubtCause.SteppedOn to ReceptionDoubtSource.SteppedOn,
            ReceptionDoubtCause.Other("garbled-callsign") to ReceptionDoubtSource.Other("garbled-callsign"),
        )

        causes.forEachIndexed { index, (cause, expectedSource) ->
            val record = pilotTransmissionRecord(
                index = index,
                aircraft = aircraft,
                transmission = Report(events = listOf(ReportEvent.Ready)),
            ).copy(receptionQuality = ReceptionQuality.Doubtful(cause))

            val factSet = EvidenceFactAdapters.fromTransmissionRecords(
                scenarioId = "doubt-cause::$index",
                records = listOf(record),
            )
            val fact = factSet.facts.single { it.payload is EvidenceFactPayload.ReceptionDoubt }
            val payload = fact.payload as EvidenceFactPayload.ReceptionDoubt

            assertEquals(aircraft, payload.aircraftId)
            assertEquals(record.transmissionId, payload.transmissionRef)
            assertEquals(expectedSource, payload.doubtSource)
            assertNull(payload.resolvedBy)
            assertEquals(EvidenceSequence(5), fact.provenance.sequence)
            assertEquals(record.time, fact.provenance.simTime)
            assertEquals(record.transmissionId, fact.provenance.sourceTransmissionId)
            assertEquals(
                "sim.records[0].pilot.receptionDoubt",
                fact.provenance.extractionPath.value,
            )
        }
    }

    @Test
    fun `reception-doubt projection on empty input emits no facts`() {
        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "doubt-boundary-empty",
            records = emptyList(),
        )
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.ReceptionDoubt })
    }

    @Test
    fun `reception-doubt projection on single unrelated record emits no doubt facts`() {
        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "doubt-boundary-unrelated",
            records = listOf(reportRecord(index = 0, time = SimTime.ZERO, event = ReportEvent.Ready)),
        )
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.ReceptionDoubt })
    }

    @Test
    fun `reception-doubt projection emits for controller Respond records (not only Instruct)`() {
        // Regression for codex impl-review finding: receptionDoubtFact must
        // execute for ControllerOutput.Respond records too — doubt is a
        // property of the transmission instance, not the controller-output subtype.
        val aircraft = AircraftId("OE-ABC")
        val respondOutput = ControllerOutput.Respond(
            target = aircraft,
            response = Standby(target = aircraft),
            trace = DecisionTrace(
                ruleId = "TEST-RespondWiring",
                description = "test that controller Respond arm runs the receptionDoubt projection",
                regulations = emptyList(),
            ),
        )
        // controllerOutputRecord helper only accepts Instruct; build the
        // Respond TransmissionRecord directly.
        val record = TransmissionRecord(
            transmissionId = TransmissionId(820),
            time = SimTime.ZERO,
            endedAt = SimTime.ofSeconds(2),
            speaker = SpeakerRef.Controller(ControllerId("LOWG_TWR")),
            receiver = ReceiverRef.Pilot(aircraft),
            utterance = Utterance.FromController(respondOutput),
            receptionQuality = ReceptionQuality.Doubtful(ReceptionDoubtCause.PartialReception),
        )

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "respond-arm-doubt-projection-wired",
            records = listOf(record),
        )

        val payloads = facts.facts.map { it.payload }
        assertEquals(1, payloads.count { it is EvidenceFactPayload.ReceptionDoubt })
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.Instruction })
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.FrequencyTransfer })
    }

    @Test
    fun `reception-doubt projection on multiple clear mixed records emits no doubt facts`() {
        val aircraft = AircraftId("OE-ABC")
        val records = listOf(
            controllerInstructionRecord(
                index = 0,
                instruction = ContactFrequency(
                    target = aircraft,
                    role = RoleName.TOWER,
                    frequency = Frequency.unsafe("118.500"),
                ),
            ),
            reportRecord(index = 1, time = SimTime.ZERO, event = ReportEvent.Ready),
            pilotTransmissionRecord(
                index = 2,
                aircraft = aircraft,
                transmission = Request(RequestFrequencyChange(frequency = Frequency.unsafe("123.500"))),
            ),
        )

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "doubt-boundary-mixed",
            records = records,
        )

        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.ReceptionDoubt })
        // Other projections still fire on the same records — confirms doubt's clear branch
        // is local to this projection (not a record-level filter that suppresses everything).
        assertTrue(facts.facts.any { it.payload is EvidenceFactPayload.FrequencyTransfer })
    }

    @Test
    fun `reception-doubt selector returns Fail when no doubt facts present (today's sim covered-red)`() {
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("doubt-selector-missing") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "doubt-selector-missing",
                    records = emptyList(),
                )
            }
            source("reception doubt missing") {
                cites(ICAO9432.Communications.ReceptionDoubtRepetitionRequested)
                expect { receptionDoubt(aircraft).requiresRepetitionResponse() }
            }
        }

        report.results.forEach { result ->
            assertTrue(
                result.outcome is EvidenceAuditOutcome.Fail,
                "expected Fail for ${result.id} but got ${result.outcome}",
            )
        }
    }

    @Test
    fun `reception-doubt selector returns Pass when doubt facts all carry SayAgain resolution`() {
        // Construction-site test for the selector's pass-path: when the sim
        // eventually emits doubt facts via the production-repair epic and
        // every doubt is resolved by a SayAgain, the selector passes.
        // Uses fromProjectedPayloads — proves the type wiring, NOT that the
        // sim observes doubt in real traces.
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("doubt-selector-resolved") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "doubt-selector-resolved",
                    payloads = listOf(
                        EvidenceFactPayload.ReceptionDoubt(
                            aircraftId = aircraft,
                            transmissionRef = TransmissionId(601),
                            doubtSource = ReceptionDoubtSource.PartialReception,
                            resolvedBy = SayAgainRef(TransmissionId(701)),
                        ),
                    ),
                )
            }
            source("reception doubt resolved") {
                cites(ICAO9432.Communications.ReceptionDoubtRepetitionRequested)
                expect { receptionDoubt(aircraft).requiresRepetitionResponse() }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `reception-doubt selector returns Fail when any doubt fact lacks SayAgain resolution`() {
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("doubt-selector-unresolved") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "doubt-selector-unresolved",
                    payloads = listOf(
                        EvidenceFactPayload.ReceptionDoubt(
                            aircraftId = aircraft,
                            transmissionRef = TransmissionId(602),
                            doubtSource = ReceptionDoubtSource.SteppedOn,
                            resolvedBy = SayAgainRef(TransmissionId(702)),
                        ),
                        EvidenceFactPayload.ReceptionDoubt(
                            aircraftId = aircraft,
                            transmissionRef = TransmissionId(603),
                            doubtSource = ReceptionDoubtSource.Unintelligibility,
                            resolvedBy = null,
                        ),
                    ),
                )
            }
            source("reception doubt partially unresolved") {
                cites(ICAO9432.Communications.ReceptionDoubtRepetitionRequested)
                expect { receptionDoubt(aircraft).requiresRepetitionResponse() }
            }
        }

        report.results.forEach { result ->
            assertTrue(
                result.outcome is EvidenceAuditOutcome.Fail,
                "expected Fail for ${result.id} but got ${result.outcome}",
            )
        }
        // Activation check: the selector activates every doubt fact it
        // examined (resolved or not). Without this, AuditEvidenceCaseBuilder
        // would override the selector's specific Fail with the generic
        // "did not activate any evidence facts" message, hiding the real
        // reason. Asserting non-empty activationFactIds + specific reason
        // ensures the selector's outcome survives.
        val unresolvedResult = report.results.single()
        assertTrue(
            unresolvedResult.activationFactIds.isNotEmpty(),
            "expected unresolved-doubt Fail to activate the examined doubt facts; got ${unresolvedResult.activationFactIds}",
        )
        val failOutcome = unresolvedResult.outcome as EvidenceAuditOutcome.Fail
        assertTrue(
            failOutcome.reason.contains("without repetition response"),
            "expected selector-specific reason, got '${failOutcome.reason}'",
        )
        assertTrue(
            failOutcome.evidence.any { it.contains("unintelligibility") },
            "expected evidence to identify the unresolved doubt by source label; got ${failOutcome.evidence}",
        )
    }

    // FN33-MODEL-1: clearance-pacing advisory evidence primitive (R2, R12).
    //
    // The adapter projects ClearancePacing facts at controller-issued
    // clearance moments (filtered to ControllerOutput.Instruct, instruction
    // is Clearance) when phase information is available in the
    // phaseAtTransmission lookup. Without that lookup (the default empty
    // map used by fromTransmissionRecords callers), the projection emits
    // no facts — verifying the wiring is total without leaking false
    // observations into call sites that do not supply phase data.
    //
    // §2.8.3.2 is **advisory** ("should" / "avoid" / "on no occasion").
    // Violations surface as EvidenceAuditOutcome.Advisory via the selector,
    // not Fail.
    //
    // Per memory `predicate-guards-over-sealed-types-must-2026-05-16`:
    // PacingWindow is an enum class so tests use PacingWindow.entries for
    // exhaustion.

    @Test
    fun `clearance-pacing payload accepts every PacingWindow entry`() {
        val aircraft = AircraftId("OE-ABC")
        PacingWindow.entries.forEachIndexed { index, window ->
            val payload = EvidenceFactPayload.ClearancePacing(
                aircraftId = aircraft,
                clearanceRef = TransmissionId(1100 + index.toLong()),
                issuedDuring = window,
            )
            assertEquals(EvidenceFactKind.ClearancePacing, payload.kind)
            assertEquals(window, payload.issuedDuring)
        }
    }

    @Test
    fun `clearance-pacing projection emits no facts on empty input`() {
        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "pacing-boundary-empty",
            records = emptyList(),
        )
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.ClearancePacing })
    }

    @Test
    fun `clearance-pacing projection emits no facts when no phase lookup is supplied`() {
        // R12: the default `fromTransmissionRecords` call surface (no
        // phaseAtTransmission lookup) must observe NO ClearancePacing facts
        // regardless of speaker × utterance × payload shape. This pins the
        // wiring as total but observably silent without phase data —
        // `fromLowgCircuitTrace` is the path that supplies the phase lookup
        // and produces ClearancePacing facts in the integration test.
        //
        // The matrix below exercises every (speaker, utterance, payload-kind)
        // combination that the adapter contract distinguishes, using only
        // outputs constructible via the public `Instruct.fromAdministrative` /
        // `Instruct.fromMissedHandoffReissue` factories. A real `Clearance`
        // payload (e.g. `LineUpAndWait`) cannot be lifted into an `Instruct`
        // outside the controller module without going through ActionCertifier;
        // the LOWG-trace integration test (Icao9432Chunk01ClearancePacingEvidenceTest)
        // exercises that path end-to-end.
        val aircraft = AircraftId("OE-ABC")
        val controllerId = ControllerId("LOWG_TWR")
        val nonClearanceInstruction = NumberInSequence.unsafe(target = aircraft, number = 1)
        val nonClearanceFrequencyInstruction = ContactFrequency(
            target = aircraft,
            role = RoleName.TOWER,
            frequency = Frequency.unsafe("118.500"),
        )
        val pilotRequest: PilotTransmission =
            Request(RequestFrequencyChange(frequency = Frequency.unsafe("123.500")))
        val pilotReport: PilotTransmission = Report(events = listOf(ReportEvent.Ready))

        val administrativeControllerOutput = ControllerOutput.Instruct.fromAdministrative(
            instruction = nonClearanceInstruction,
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("PACING-MATRIX", "matrix NumberInSequence", emptyList()),
        )
        val frequencyControllerOutput = ControllerOutput.Instruct.fromMissedHandoffReissue(
            instruction = nonClearanceFrequencyInstruction,
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("PACING-MATRIX", "matrix ContactFrequency", emptyList()),
        )

        val matrix = listOf(
            "controller-speaker / controller-utterance / NumberInSequence" to controllerOutputRecord(
                index = 0,
                targetAircraft = aircraft,
                output = administrativeControllerOutput,
            ),
            "controller-speaker / controller-utterance / ContactFrequency" to controllerOutputRecord(
                index = 1,
                targetAircraft = aircraft,
                output = frequencyControllerOutput,
            ),
            "controller-speaker / pilot-utterance / RequestFrequencyChange" to TransmissionRecord(
                transmissionId = TransmissionId(1210),
                time = SimTime.ZERO,
                endedAt = SimTime.ofSeconds(2),
                speaker = SpeakerRef.Controller(controllerId),
                receiver = ReceiverRef.Pilot(aircraft),
                utterance = Utterance.FromPilot(pilotRequest),
            ),
            "controller-speaker / pilot-utterance / Report" to TransmissionRecord(
                transmissionId = TransmissionId(1211),
                time = SimTime.ZERO,
                endedAt = SimTime.ofSeconds(2),
                speaker = SpeakerRef.Controller(controllerId),
                receiver = ReceiverRef.Pilot(aircraft),
                utterance = Utterance.FromPilot(pilotReport),
            ),
            "pilot-speaker / controller-utterance / NumberInSequence" to TransmissionRecord(
                transmissionId = TransmissionId(1212),
                time = SimTime.ZERO,
                endedAt = SimTime.ofSeconds(2),
                speaker = SpeakerRef.Pilot(aircraft),
                receiver = ReceiverRef.Controller(controllerId),
                utterance = Utterance.FromController(administrativeControllerOutput),
            ),
            "pilot-speaker / controller-utterance / ContactFrequency" to TransmissionRecord(
                transmissionId = TransmissionId(1213),
                time = SimTime.ZERO,
                endedAt = SimTime.ofSeconds(2),
                speaker = SpeakerRef.Pilot(aircraft),
                receiver = ReceiverRef.Controller(controllerId),
                utterance = Utterance.FromController(frequencyControllerOutput),
            ),
            "pilot-speaker / pilot-utterance / RequestFrequencyChange" to pilotTransmissionRecord(
                index = 0,
                aircraft = aircraft,
                transmission = pilotRequest,
            ),
            "pilot-speaker / pilot-utterance / Report" to pilotTransmissionRecord(
                index = 1,
                aircraft = aircraft,
                transmission = pilotReport,
            ),
        )

        matrix.forEach { (label, record) ->
            val facts = EvidenceFactAdapters.fromTransmissionRecords(
                scenarioId = "pacing-matrix::$label",
                records = listOf(record),
            )
            assertTrue(
                facts.facts.none { it.payload is EvidenceFactPayload.ClearancePacing },
                "fromTransmissionRecords without phase lookup must observe no ClearancePacing facts for $label",
            )
        }
    }

    @Test
    fun `clearance-pacing projection emits no fact for non-clearance instruction even with phase lookup`() {
        // Boundary: a controller instruction that is NOT a Clearance (e.g. the
        // administrative NumberInSequence used here, or a frequency
        // ContactFrequency reissue) must not produce a ClearancePacing fact,
        // even when phase info is supplied. §2.8.3.2 scopes the obligation to
        // clearances specifically — the adapter's `if (instruction !is
        // Clearance) return null` guard owns this contract.
        val aircraft = AircraftId("OE-ABC")
        val nonClearance = NumberInSequence.unsafe(target = aircraft, number = 1)
        val output = ControllerOutput.Instruct.fromAdministrative(
            instruction = nonClearance,
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("TEST-NC", "non-clearance pacing", emptyList()),
        )
        val record = controllerOutputRecord(index = 0, targetAircraft = aircraft, output = output)
        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "pacing-boundary-non-clearance",
            records = listOf(record),
            phaseAtTransmission = mapOf(record.transmissionId to PilotPhase.LinedUp),
        )
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.ClearancePacing })

        // Same expectation for ContactFrequency (FrequencyInstruction, not
        // Clearance): the projection's Clearance guard filters it out even
        // though both the controller arm and phase lookup are populated.
        val frequencyOutput = ControllerOutput.Instruct.fromMissedHandoffReissue(
            instruction = ContactFrequency(
                target = aircraft,
                role = RoleName.TOWER,
                frequency = Frequency.unsafe("118.500"),
            ),
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("TEST-NC", "non-clearance freq pacing", emptyList()),
        )
        val frequencyRecord = controllerOutputRecord(
            index = 1,
            targetAircraft = aircraft,
            output = frequencyOutput,
        )
        val frequencyFacts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "pacing-boundary-non-clearance-freq",
            records = listOf(frequencyRecord),
            phaseAtTransmission = mapOf(frequencyRecord.transmissionId to PilotPhase.LinedUp),
        )
        assertTrue(frequencyFacts.facts.none { it.payload is EvidenceFactPayload.ClearancePacing })
    }

    @Test
    fun `clearance-pacing projection emits one ClearancePacing fact per Clearance issuance via LOWG trace`() {
        // R12 + R2: drive the projection end-to-end via the LOWG circuit-
        // training trace, which is the only public path that synthesises real
        // `Instruct(Clearance)` outputs (taxi clearances, line-up clearances,
        // takeoff clearances) along with the per-step pilot phase state
        // needed to populate `phaseAtTransmission`.
        //
        // The trace is expected to emit at least one controller-issued
        // Clearance (e.g. taxi to holding point during ground phase,
        // LineUpAndWait at HoldingShort, ClearedForTakeoff at LinedUp) with
        // observable pilot phase. The adapter projects exactly one
        // ClearancePacing fact per such issuance.
        //
        // Asserting "at least one fact" instead of an exact count keeps the
        // test robust against future trace-content additions; the precise
        // window distribution is the concern of the integration test in
        // Icao9432Chunk01ClearancePacingEvidenceTest.
        val facts = EvidenceFactAdapters.lowgCircuitTraining(
            scenarioId = "pacing-lowg-projection",
            outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
            untilMinutes = 45L,
        )
        val pacingFacts = facts.facts.mapNotNull { fact ->
            fact.payload as? EvidenceFactPayload.ClearancePacing
        }
        assertTrue(
            pacingFacts.isNotEmpty(),
            "expected LOWG circuit-training trace to produce at least one ClearancePacing fact; " +
                "if the trace genuinely emits no controller-issued Clearances during phases with " +
                "observable pilot state, this test pins the covered-red leg and the integration " +
                "test in Icao9432Chunk01ClearancePacingEvidenceTest must also land covered-red",
        )
        // Every projected window must be one of the closed PacingWindow.entries
        // (the projection is total over `PacingWindow.entries`).
        val observedWindows = pacingFacts.map { it.issuedDuring }.toSet()
        assertTrue(
            observedWindows.all { window -> window in PacingWindow.entries },
            "every projected window must be a PacingWindow.entries value; got $observedWindows",
        )
    }

    @Test
    fun `clearance-pacing projection covers every PacingWindow when phase forced via LOWG-derived Clearance records`() {
        // R12 acceptance: the adapter is total over `PacingWindow.entries`.
        // The matrix is exhaustive over the constructible inputs:
        //   - {Controller, Pilot} speaker × {Controller, Pilot} utterance —
        //     the existing no-phase matrix test above pins zero
        //     ClearancePacing facts for every non-Clearance permutation.
        //   - {Clearance utterance} × `PacingWindow.entries` (4 windows) —
        //     THIS test pins one ClearancePacing(issuedDuring=X) per X by
        //     reusing real Clearance Instruct records from the LOWG
        //     circuit-training trace and forcing `phaseAtTransmission` to
        //     map every clearance to a `PilotPhase` that resolves to the
        //     target `PacingWindow`.
        //
        // The {Pilot speaker, Clearance utterance} combinations are vacuous
        // — `Clearance` is sealed and only emitted by `ControllerOutput.
        // Instruct`. The wiring-scope test below pins zero pacing facts
        // when a pilot-speaker record carries a controller-output payload.
        // Combined, the matrix is honestly exhausted at every constructible
        // (speaker, utterance, window) triple.
        //
        // `Instruct(Clearance)` cannot be constructed outside the controller
        // module (no public factory exposes Clearance instructions), so we
        // extract real Clearance records from the LOWG trace rather than
        // synthesise them.
        val trace = LowgObservationPort.runCircuitTrainingTrace(
            scenarioId = "pacing-window-matrix",
            outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
            untilMinutes = 45L,
        )
        val clearanceRecords = trace.records.filter { record ->
            val utterance = record.utterance
            val output = (utterance as? Utterance.FromController)?.output
            val instruct = output as? ControllerOutput.Instruct
            instruct != null && instruct.instruction is Clearance
        }
        assertTrue(
            clearanceRecords.isNotEmpty(),
            "expected LOWG trace to emit at least one Clearance Instruct; if " +
                "this fails, the LOWG circuit-training scenario lost its " +
                "clearance issuances and the chunk-01 closure must be re-verified",
        )

        // Inverse of the adapter's `pacingWindowFor(phase)` mapping at
        // EvidenceFacts.kt: Taxiing → ComplicatedTaxi; LinedUp → LineUp;
        // TakeoffRoll → TakeoffRoll; everything else → Other. `AtStand` is
        // the representative non-sensitive phase for the `Other` row.
        val windowToPhase = mapOf(
            PacingWindow.ComplicatedTaxi to PilotPhase.Taxiing,
            PacingWindow.LineUp to PilotPhase.LinedUp,
            PacingWindow.TakeoffRoll to PilotPhase.TakeoffRoll,
            PacingWindow.Other to PilotPhase.AtStand,
        )
        // Belt-and-braces: must cover EVERY PacingWindow.entries value
        // (per `predicate-guards-over-sealed-types-must-2026-05-16` memory).
        assertEquals(
            PacingWindow.entries.toSet(),
            windowToPhase.keys,
            "windowToPhase must cover every PacingWindow.entries value",
        )

        PacingWindow.entries.forEach { window ->
            val phase = windowToPhase.getValue(window)
            val phaseMap = clearanceRecords.associate { it.transmissionId to phase }
            val facts = EvidenceFactAdapters.fromTransmissionRecords(
                scenarioId = "pacing-window-matrix::$window",
                records = clearanceRecords,
                phaseAtTransmission = phaseMap,
            )
            val pacingFacts = facts.facts.mapNotNull {
                it.payload as? EvidenceFactPayload.ClearancePacing
            }
            assertTrue(
                pacingFacts.isNotEmpty(),
                "expected at least one ClearancePacing fact when phase forced " +
                    "to $phase (→ $window); got none",
            )
            assertTrue(
                pacingFacts.all { it.issuedDuring == window },
                "every projected ClearancePacing fact must carry " +
                    "issuedDuring=$window when phase=$phase forced for every " +
                    "Clearance record; got ${pacingFacts.map { it.issuedDuring }.toSet()}",
            )
        }
    }

    @Test
    fun `clearance-pacing projection is wired through controller Instruct only (not Respond, not pilot arm)`() {
        // Per task spec wiring decision: ClearancePacing is a property of
        // CONTROLLER-ISSUED clearances (`ControllerOutput.Instruct` carrying
        // `instruction is Clearance`). Contrast with ReceptionDoubt which is
        // a property of any transmission (wired through both arms). This
        // test pins the wiring scope so a future refactor doesn't accidentally
        // widen the projection.
        //
        // Two concrete regression guards:
        //   1. `ControllerOutput.Respond` (Standby is a ControllerResponse,
        //      not a Clearance) — no pacing fact even with phase observation.
        //   2. A pilot-speaker record whose utterance side carries a
        //      controller output — must not project even with phase observation.
        //
        // We cannot easily fabricate `Instruct(LineUpAndWait, …)` outside the
        // controller module (no public factory), so the pilot-arm record uses
        // `Instruct.fromMissedHandoffReissue(ContactFrequency)`. That output
        // would not produce a pacing fact anyway (ContactFrequency is not a
        // Clearance), so the test logic depends on the wiring guard, not the
        // payload guard. The LOWG-trace integration test exercises the
        // "real Clearance issuance produces pacing facts" path.
        val aircraft = AircraftId("OE-ABC")
        val respondOutput = ControllerOutput.Respond(
            target = aircraft,
            response = Standby(target = aircraft),
            trace = DecisionTrace(
                ruleId = "TEST-RespondPacing",
                description = "test pacing wiring excludes Respond arm",
                regulations = emptyList(),
            ),
        )
        val respondRecord = TransmissionRecord(
            transmissionId = TransmissionId(1820),
            time = SimTime.ZERO,
            endedAt = SimTime.ofSeconds(2),
            speaker = SpeakerRef.Controller(ControllerId("LOWG_TWR")),
            receiver = ReceiverRef.Pilot(aircraft),
            utterance = Utterance.FromController(respondOutput),
        )
        val pilotArmRecord = TransmissionRecord(
            transmissionId = TransmissionId(1821),
            time = SimTime.ZERO,
            endedAt = SimTime.ofSeconds(2),
            speaker = SpeakerRef.Pilot(aircraft),
            receiver = ReceiverRef.Controller(ControllerId("LOWG_TWR")),
            utterance = Utterance.FromController(
                ControllerOutput.Instruct.fromMissedHandoffReissue(
                    instruction = ContactFrequency(
                        target = aircraft,
                        role = RoleName.TOWER,
                        frequency = Frequency.unsafe("118.500"),
                    ),
                    urgency = Urgency.PROGRESSION,
                    trace = DecisionTrace("TEST-PilotArm", "should not project", emptyList()),
                ),
            ),
        )
        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "pacing-wiring-scope",
            records = listOf(respondRecord, pilotArmRecord),
            phaseAtTransmission = mapOf(
                respondRecord.transmissionId to PilotPhase.LinedUp,
                pilotArmRecord.transmissionId to PilotPhase.LinedUp,
            ),
        )
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.ClearancePacing })
    }

    @Test
    fun `clearancePacing selector returns Pass when pacing facts all in non-sensitive windows`() {
        // Construction-site test: when every clearance is issued in
        // PacingWindow.Other (non-sensitive), the selector returns Pass.
        // Activation discipline (per memory
        // bug/test-failures/audit-selectors-must-activate-examined-2026-05-26):
        // Pass path must activate examined facts so the framework's
        // activation check does not override the outcome.
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("pacing-selector-pass") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "pacing-selector-pass",
                    payloads = listOf(
                        EvidenceFactPayload.ClearancePacing(
                            aircraftId = aircraft,
                            clearanceRef = TransmissionId(2001),
                            issuedDuring = PacingWindow.Other,
                        ),
                    ),
                )
            }
            source("pacing all in non-sensitive window") {
                cites(ICAO9432.Readback.ClearancePacingAdvisory)
                expect {
                    clearancePacing(aircraft).whenIssuedDuring(
                        PacingWindow.entries.filter { it != PacingWindow.Other },
                    )
                }
            }
        }

        report.assertNoFailures()
        val result = report.results.single()
        assertTrue(
            result.outcome is EvidenceAuditOutcome.Pass,
            "expected Pass when no clearance hits sensitive windows; got ${result.outcome}",
        )
        assertTrue(
            result.activationFactIds.isNotEmpty(),
            "Pass path must activate examined facts so framework activation check preserves the outcome",
        )
    }

    @Test
    fun `clearancePacing selector returns Advisory when pacing facts hit sensitive windows`() {
        // Construction-site test: a clearance issued during LinedUp / TakeoffRoll
        // produces Advisory (NOT Fail). assertNoFailures still passes because
        // Advisory is not Fail. Activation discipline applies: Advisory path
        // must activate examined facts.
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("pacing-selector-advisory") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "pacing-selector-advisory",
                    payloads = listOf(
                        EvidenceFactPayload.ClearancePacing(
                            aircraftId = aircraft,
                            clearanceRef = TransmissionId(3001),
                            issuedDuring = PacingWindow.LineUp,
                        ),
                        EvidenceFactPayload.ClearancePacing(
                            aircraftId = aircraft,
                            clearanceRef = TransmissionId(3002),
                            issuedDuring = PacingWindow.TakeoffRoll,
                        ),
                        EvidenceFactPayload.ClearancePacing(
                            aircraftId = aircraft,
                            clearanceRef = TransmissionId(3003),
                            issuedDuring = PacingWindow.Other,
                        ),
                    ),
                )
            }
            source("pacing hits sensitive windows") {
                cites(ICAO9432.Readback.ClearancePacingAdvisory)
                expect {
                    clearancePacing(aircraft).whenIssuedDuring(
                        PacingWindow.entries.filter { it != PacingWindow.Other },
                    )
                }
            }
        }

        // Advisory is NOT a JUnit failure. report.assertNoFailures() passes.
        report.assertNoFailures()
        val result = report.results.single()
        val advisory = result.outcome
        assertTrue(
            advisory is EvidenceAuditOutcome.Advisory,
            "expected Advisory when clearances hit sensitive windows; got $advisory",
        )
        advisory as EvidenceAuditOutcome.Advisory
        // R12 / activation discipline: examined facts must activate on the
        // Advisory path so AuditEvidenceCaseBuilder.toCase preserves the
        // specific Advisory outcome (with its violations list) instead of
        // overriding with the generic "did not activate any evidence facts"
        // Fail.
        assertTrue(
            result.activationFactIds.isNotEmpty(),
            "Advisory path must activate examined facts; got ${result.activationFactIds}",
        )
        // Advisory carries the selector's specific reason, not the generic
        // activation-check string.
        assertTrue(
            advisory.reason.contains("sensitive pacing window"),
            "expected selector-specific Advisory reason; got '${advisory.reason}'",
        )
        // Exactly two violations for the LinedUp + TakeoffRoll clearances; the
        // PacingWindow.Other clearance is non-sensitive and must NOT appear.
        assertEquals(2, advisory.violations.size)
        val violationWindows = advisory.violations.map { it.observedWindow }.toSet()
        assertEquals(setOf(PacingWindow.LineUp, PacingWindow.TakeoffRoll), violationWindows)
        val violationRefs = advisory.violations.map { it.clearanceRef }.toSet()
        assertEquals(setOf(TransmissionId(3001), TransmissionId(3002)), violationRefs)
    }

    @Test
    fun `clearancePacing selector returns Fail when no pacing facts present`() {
        // Honest covered-red leg: when sim genuinely lacks the phase signal
        // (or no clearances were issued at all), the selector returns Fail
        // because §2.8.3.2 cannot be evaluated without an observation. The
        // "no facts at all" path stays un-activated — that path correctly
        // surfaces as the generic activation-check Fail per memory
        // bug/test-failures/audit-selectors-must-activate-examined-2026-05-26.
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("pacing-selector-missing") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "pacing-selector-missing",
                    records = emptyList(),
                )
            }
            source("pacing missing") {
                cites(ICAO9432.Readback.ClearancePacingAdvisory)
                expect {
                    clearancePacing(aircraft).whenIssuedDuring(
                        PacingWindow.entries.filter { it != PacingWindow.Other },
                    )
                }
            }
        }

        report.results.forEach { result ->
            assertTrue(
                result.outcome is EvidenceAuditOutcome.Fail,
                "expected Fail when no pacing facts present; got ${result.outcome}",
            )
        }
    }

    private fun reportRecord(
        index: Long,
        time: SimTime,
        event: ReportEvent,
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = TransmissionId(100 + index),
            time = time,
            endedAt = time + SimDuration.ofSeconds(2),
            speaker = SpeakerRef.Pilot(AircraftId("OE-ABC")),
            receiver = ReceiverRef.Controller(ControllerId("LOWG_TWR")),
            utterance = Utterance.FromPilot(Report(events = listOf(event))),
        )

    private fun controllerInstructionRecord(
        index: Int,
        instruction: ContactFrequency,
        controllerId: ControllerId = ControllerId("LOWG_TWR"),
    ): TransmissionRecord {
        val output = ControllerOutput.Instruct.fromMissedHandoffReissue(
            instruction = instruction,
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("TEST-CTRL", "test ContactFrequency", emptyList()),
        )
        return controllerOutputRecord(index = index, targetAircraft = instruction.target, output = output, controllerId = controllerId)
    }

    private fun controllerOutputRecord(
        index: Int,
        targetAircraft: AircraftId,
        output: ControllerOutput.Instruct,
        controllerId: ControllerId = ControllerId("LOWG_TWR"),
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = TransmissionId(500L + index),
            time = SimTime.ZERO,
            endedAt = SimTime.ofSeconds(2),
            speaker = SpeakerRef.Controller(controllerId),
            receiver = ReceiverRef.Pilot(targetAircraft),
            utterance = Utterance.FromController(output),
        )

    private fun pilotTransmissionRecord(
        index: Int,
        aircraft: AircraftId,
        transmission: PilotTransmission,
        controllerId: ControllerId = ControllerId("LOWG_TWR"),
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = TransmissionId(700L + index),
            time = SimTime.ZERO,
            endedAt = SimTime.ofSeconds(2),
            speaker = SpeakerRef.Pilot(aircraft),
            receiver = ReceiverRef.Controller(controllerId),
            utterance = Utterance.FromPilot(transmission),
        )

    private fun groundStationTestSignalRecord(
        station: ControllerId,
        startedAt: SimTime,
        endedAt: SimTime,
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = TransmissionId(850),
            time = startedAt,
            endedAt = endedAt,
            speaker = SpeakerRef.Controller(station),
            receiver = ReceiverRef.Controller(station),
            utterance = Utterance.GroundStationTestSignal(TestSignalPurpose.TransmitterAdjustment),
        )

    private fun criticalPhaseWindowFact(
        scenarioId: String,
        aircraft: AircraftId,
    ): EvidenceFact =
        EvidenceFact(
            id = FactId("$scenarioId::sim-run::000100::CriticalPhaseWindow::test.criticalPhaseWindow"),
            provenance = EvidenceFactProvenance(
                scenarioId = scenarioId,
                origin = EvidenceFactOrigin.SimRun,
                sequence = EvidenceSequence(100),
                simTime = null,
                sourceTransmissionId = null,
                extractionPath = EvidenceExtractionPath("test.criticalPhaseWindow"),
            ),
            payload = EvidenceFactPayload.CriticalPhaseWindow(
                aircraftId = aircraft,
                phase = CriticalPhaseKind.LateFinal,
                start = EvidenceSequence(1),
                end = EvidenceSequence(2),
            ),
        )

    private data class Combo(
        val label: String,
        val record: TransmissionRecord,
        val expectControllerAdvised: Boolean,
        val expectPilotNotified: Boolean,
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

private fun EvidenceFactSet.firstFrequencyTransfer(
    aircraftId: AircraftId,
    mode: FrequencyTransferMode? = null,
): EvidenceFactPayload.FrequencyTransfer? =
    orderedFacts().asSequence()
        .mapNotNull { fact -> fact.payload as? EvidenceFactPayload.FrequencyTransfer }
        .firstOrNull { payload ->
            payload.aircraftId == aircraftId && (mode == null || payload.mode == mode)
        }
