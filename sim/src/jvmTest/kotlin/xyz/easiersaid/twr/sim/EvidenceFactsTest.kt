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
import xyz.easiersaid.twr.protocol.Urgency
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
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
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Standby
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

    // FN44-GAP-2: pilot-notified frequency-change projection (R4, R12).
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
    // The current sim has no reception-quality signal infrastructure, so the
    // adapter projection [EvidenceFactAdapters.receptionDoubtFact] returns
    // null for every record. These primitive-level tests pin that honest
    // current behaviour across the full speaker × utterance × payload matrix
    // + boundary cases. They also pin the typed-payload shape (sealed
    // ReceptionDoubtSource leaves, optional resolvedBy: SayAgainRef?) and
    // the selector's expected outcomes when the sim eventually emits
    // doubt facts via fromProjectedPayloads — those construction-site tests
    // prove the type is wired correctly without claiming covered-green for
    // the source unit (per AGENTS.md commandment 4: tests prove the real
    // job, not the synthetic type).

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
        // and produces no reception-doubt facts under today's sim (which has no reception-
        // quality signal infrastructure). This is the honest covered-red landing: the
        // projection is total, but observes nothing.
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
                speaker = SpeakerRef.Controller(controllerId),
                receiver = ReceiverRef.Pilot(aircraft),
                utterance = Utterance.FromPilot(matchingPilotTransmission),
            ),
            "controller-speaker / pilot-utterance / non-matching-payload" to TransmissionRecord(
                transmissionId = TransmissionId(911),
                time = SimTime.ZERO,
                speaker = SpeakerRef.Controller(controllerId),
                receiver = ReceiverRef.Pilot(aircraft),
                utterance = Utterance.FromPilot(nonMatchingPilotTransmission),
            ),
            "pilot-speaker / controller-utterance / matching-payload" to TransmissionRecord(
                transmissionId = TransmissionId(912),
                time = SimTime.ZERO,
                speaker = SpeakerRef.Pilot(aircraft),
                receiver = ReceiverRef.Controller(controllerId),
                utterance = Utterance.FromController(matchingControllerOutput),
            ),
            "pilot-speaker / controller-utterance / non-matching-payload" to TransmissionRecord(
                transmissionId = TransmissionId(913),
                time = SimTime.ZERO,
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
                "today's sim has no reception-quality signal — expected no ReceptionDoubt facts for $label",
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
    fun `reception-doubt projection is wired through controller Respond records (not only Instruct)`() {
        // Regression for codex impl-review finding: receptionDoubtFact must
        // execute for ControllerOutput.Respond records too — doubt is a
        // property of the transmission instance, not the controller-output
        // subtype. Today's sim has no reception-quality signal, so the
        // projection returns null. The assertion is that the Respond arm
        // produces no facts at all (the unprojected `Respond` arm still
        // returns emptyList for instruction/frequency-transfer facts) —
        // i.e., the code path is exercised without throwing or producing
        // spurious facts. When the production-repair epic adds reception-
        // quality input, ReceptionDoubt facts will start landing here.
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
            speaker = SpeakerRef.Controller(ControllerId("LOWG_TWR")),
            receiver = ReceiverRef.Pilot(aircraft),
            utterance = Utterance.FromController(respondOutput),
        )

        val facts = EvidenceFactAdapters.fromTransmissionRecords(
            scenarioId = "respond-arm-doubt-projection-wired",
            records = listOf(record),
        )

        // No ReceptionDoubt facts (sim has no reception-quality signal) — but the
        // projection was reached. Also: no Instruction or FrequencyTransfer facts
        // either, because Respond does not produce them.
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.ReceptionDoubt })
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.Instruction })
        assertTrue(facts.facts.none { it.payload is EvidenceFactPayload.FrequencyTransfer })
    }

    @Test
    fun `reception-doubt projection on multiple mixed records emits no doubt facts under current sim`() {
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
        // Other projections still fire on the same records — confirms doubt's null return
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
            speaker = SpeakerRef.Pilot(aircraft),
            receiver = ReceiverRef.Controller(controllerId),
            utterance = Utterance.FromPilot(transmission),
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
