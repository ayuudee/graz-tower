package xyz.easiersaid.twr.sim

import arrow.core.NonEmptyList
import kotlin.test.Test
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.certify.CertificationEvidence
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AfterLandingVacateVia
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.FrequencyReadback
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.SimpleElement
import xyz.easiersaid.twr.protocol.StopImmediately
import xyz.easiersaid.twr.protocol.Urgency
import xyz.easiersaid.twr.protocol.VacateReadback
import xyz.easiersaid.twr.sim.testing.TransmissionRecord

class Icao9432PhraseologyEvidenceTest {
    @Test
    fun `LOWG frequency transfer renders ICAO 9432 contact instruction and readback`() {
        val aircraft = AircraftId("OE-ABC")
        val towerFrequency = Frequency.unsafe("118.200")

        val report = simEvidence("icao9432-rendered-contact-frequency") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "icao9432-rendered-contact-frequency",
                    outcomes = listOf(CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }
            source("contact frequency rendered phraseology") {
                cites(ICAO9432.TransferCommunications.ContactFrequencyPhrase)
                sample("aerodrome", "LOWG")
                sample("target-unit", "TOWER")
                sample("frequency", towerFrequency.mhz)
                expect {
                    renderedPhraseology(aircraft).contactFrequency("TOWER", towerFrequency)
                }
            }
            source("contact frequency readback rendered phraseology") {
                cites(ICAO9432.TransferCommunications.ContactFrequencyPhrase)
                sample("aerodrome", "LOWG")
                sample("target-unit", "TOWER")
                sample("frequency", towerFrequency.mhz)
                expect {
                    renderedPilotReadbackPhraseology(aircraft).frequencyReadback(towerFrequency)
                }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `LOWG rendered readbacks terminate with aircraft callsign`() {
        val aircraft = AircraftId("OE-ABC")

        val report = simEvidence("icao9432-rendered-readback-terminates-with-callsign") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "icao9432-rendered-readback-terminates-with-callsign",
                    outcomes = listOf(CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }
            source("line-up readback terminates with callsign") {
                cites(ICAO9432.ReadbackContinuation.ReadbackTerminatesWithCallsign)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.7")
                sample("coverage-scope", "supported rendered readback templates only")
                sample("template", "LineUpReadback")
                expect {
                    renderedPilotReadbackPhraseology(aircraft).lineUpReadbackTerminatesWithCallsign()
                }
            }
            source("frequency readback terminates with callsign") {
                cites(ICAO9432.ReadbackContinuation.ReadbackTerminatesWithCallsign)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.7")
                sample("coverage-scope", "supported rendered readback templates only")
                sample("template", "FrequencyReadback")
                expect {
                    renderedPilotReadbackPhraseology(aircraft).frequencyReadbackTerminatesWithCallsign()
                }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `supported rendered controller phraseology restricts TAKE OFF wording to takeoff clearance`() {
        val aircraft = AircraftId("OE-ABC")

        val report = simEvidence("icao9432-rendered-takeoff-word-use") {
            observe {
                combinedEvidenceFactSet(
                    scenarioId = "icao9432-rendered-takeoff-word-use",
                    EvidenceFactAdapters.lowgCircuitTraining(
                        scenarioId = "icao9432-rendered-takeoff-word-use-lowg",
                        outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                        untilMinutes = 45,
                    ),
                    EvidenceFactAdapters.fromTransmissionRecords(
                        scenarioId = "icao9432-rendered-takeoff-word-use-stop",
                        records = listOf(
                            stopImmediatelyRecord(aircraft),
                            firstRightWhenVacatedRecord(aircraft),
                        ),
                    ),
                )
            }
            source("TAKE OFF word appears only in take-off clearance on supported rendered templates") {
                cites(ICAO9432.Readback.TakeOffWordUse)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §2.8.3.3")
                sample("coverage-scope", "supported rendered controller templates only")
                sample("cancellation-wording", "not modelled")
                expect {
                    renderedPhraseology(aircraft).takeOffWordOnlyInTakeoffClearanceAcrossSupportedTemplates()
                }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `synthetic stop-immediately instruction renders repeated ICAO 9432 phraseology`() {
        val aircraft = AircraftId("FASTAIR 345")
        val output = ControllerOutput.Instruct.fromEmergencyPolicy(
            instruction = StopImmediately(aircraft),
            urgency = Urgency.SAFETY,
            trace = DecisionTrace("TEST-STOP", "test stop-immediately phraseology", emptyList()),
            doctrine = "ICAO Doc 9432 §4.5.11",
        )
        val record = TransmissionRecord(
            transmissionId = TransmissionId(80),
            time = SimTime.ZERO,
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(2),
            speaker = SpeakerRef.Controller(ControllerId("LOWG_TWR")),
            receiver = ReceiverRef.Pilot(aircraft),
            utterance = Utterance.FromController(output),
        )

        val report = simEvidence("icao9432-rendered-stop-immediately") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "icao9432-rendered-stop-immediately",
                    records = listOf(record),
                )
            }
            source("stop-immediately rendered phraseology") {
                cites(ICAO9432.TakeoffProcedures.StopImmediatelyPhrase)
                sample("source-text", "FASTAIR 345 STOP IMMEDIATELY FASTAIR 345 STOP IMMEDIATELY")
                sample("coverage-scope", "rendered phraseology only")
                expect {
                    renderedPhraseology(aircraft).stopImmediately()
                }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `LOWG line-up exchange renders ICAO 9432 line-up instruction and acknowledgement`() {
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")

        val report = simEvidence("icao9432-rendered-line-up-and-wait") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "icao9432-rendered-line-up-and-wait",
                    outcomes = listOf(CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }
            source("line-up-and-wait exchange rendered phraseology") {
                cites(ICAO9432.TakeoffProcedures.LineUpAndWaitPhrase)
                sample("aerodrome", "LOWG")
                sample("active-runway", runway.value)
                expect {
                    renderedPhraseology(aircraft).lineUpAndWait(runway)
                }
            }
            source("line-up acknowledgement rendered phraseology") {
                cites(ICAO9432.TakeoffProcedures.LineUpAndWaitPhrase)
                sample("aerodrome", "LOWG")
                sample("active-runway", "16C")
                expect {
                    renderedPilotReadbackPhraseology(aircraft).lineUpReadback()
                }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `LOWG takeoff clearance renders runway number for declared confusion-risk branch`() {
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")

        val report = simEvidence("icao9432-rendered-takeoff-clearance-runway-number") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "icao9432-rendered-takeoff-clearance-runway-number",
                    outcomes = listOf(CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }
            source("takeoff clearance rendered phraseology") {
                cites(ICAO9432.TakeoffProcedures.TakeoffClearancePhrase)
                sample("aerodrome", "LOWG")
                sample("active-runway", runway.value)
                expect {
                    renderedPhraseology(aircraft).takeoffClearance(runway)
                }
            }
            source("takeoff clearance includes runway number in declared confusion-risk branch") {
                cites(ICAO9432.TakeoffProcedures.RunwayNumberInTakeoffClearance)
                sample("aerodrome", "LOWG")
                sample("several-runways-in-use", true)
                sample("confusion-risk", true)
                sample("active-runway", runway.value)
                expect {
                    renderedPhraseology(aircraft).takeoffClearance(runway)
                }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `LOWG touch-and-go clearance renders ICAO 9432 touch-and-go phraseology`() {
        val aircraft = AircraftId("OE-ABC")

        val report = simEvidence("icao9432-rendered-touch-and-go-clearance") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "icao9432-rendered-touch-and-go-clearance",
                    outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }
            source("touch-and-go clearance rendered phraseology") {
                cites(ICAO9432.FinalApproachLanding.ClearedTouchAndGoPhrase)
                sample("aerodrome", "LOWG")
                sample("active-runway", "16C")
                expect {
                    renderedPhraseology(aircraft).touchAndGoClearance()
                }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `synthetic pilot reports render ICAO 9432 final and long-final wording only`() {
        val aircraft = AircraftId("FASTAIR 345")

        val report = simEvidence("icao9432-rendered-final-report-wording") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "icao9432-rendered-final-report-wording",
                    records = listOf(
                        pilotReportRecord(
                            transmissionId = TransmissionId(90),
                            aircraft = aircraft,
                            event = ReportEvent.Final,
                        ),
                        pilotReportRecord(
                            transmissionId = TransmissionId(91),
                            aircraft = aircraft,
                            event = ReportEvent.LongFinal,
                        ),
                    ),
                )
            }
            source("FINAL report rendered wording branch only") {
                cites(ICAO9432.FinalApproachLanding.FinalReportWording)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §4.7")
                sample("coverage-scope", "rendered wording branch only")
                sample("distance-timing", "blocked")
                expect {
                    renderedPilotReportPhraseology(aircraft).finalReport()
                }
            }
            source("LONG FINAL final-turn rendered wording branch only") {
                cites(ICAO9432.FinalApproachLanding.LongFinalTurnReportWording)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §4.7")
                sample("coverage-scope", "rendered wording branch only")
                sample("final-turn-distance", "blocked")
                expect {
                    renderedPilotReportPhraseology(aircraft).longFinalReport()
                }
            }
            source("straight-in LONG FINAL rendered wording branch only") {
                cites(ICAO9432.FinalApproachLanding.StraightInLongFinalReportWording)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §4.7")
                sample("coverage-scope", "rendered wording branch only")
                sample("straight-in-timing-policy", "blocked")
                expect {
                    renderedPilotReportPhraseology(aircraft).longFinalReport()
                }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `LOWG after-landing exchange renders ICAO 9432 runway-vacated taxi-to-stand wording`() {
        val aircraft = AircraftId("OE-ABC")

        val report = simEvidence("icao9432-rendered-after-landing-taxi-to-stand") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "icao9432-rendered-after-landing-taxi-to-stand",
                    outcomes = listOf(CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }
            source("after-landing runway-vacated and taxi-to-stand rendered phraseology") {
                cites(ICAO9432.AfterLanding.RunwayVacatedTaxiToStandWording)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §4.9")
                sample("coverage-scope", "rendered wording over observed LOWG after-landing taxi-to-stand exchange")
                sample("example-values", "LOWG local stand/route values rather than Doc 9432 FASTAIR example literals")
                expect {
                    afterLandingPhraseology(aircraft).runwayVacatedTaxiToStandExchange()
                }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `synthetic after-landing first-right contact-ground branch renders ICAO 9432 wording`() {
        val aircraft = AircraftId("FASTAIR 345")
        val groundFrequency = Frequency.unsafe("118.350")

        val report = simEvidence("icao9432-rendered-after-landing-first-right-contact-ground") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "icao9432-rendered-after-landing-first-right-contact-ground",
                    records = listOf(
                        firstRightWhenVacatedRecord(aircraft),
                        contactGroundRecord(
                            aircraft = aircraft,
                            frequency = groundFrequency,
                        ),
                        firstRightFrequencyReadbackRecord(
                            aircraft = aircraft,
                            frequency = groundFrequency,
                        ),
                    ),
                )
            }
            source("after-landing first-right contact-ground rendered exchange") {
                cites(ICAO9432.AfterLanding.FirstRightContactGroundWording)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §4.9")
                sample("controller-branch", "FASTAIR 345 TAKE FIRST RIGHT WHEN VACATED")
                sample("frequency-branch", "FASTAIR 345 CONTACT GROUND 118.350")
                sample("pilot-readback", "FIRST RIGHT 118.350 FASTAIR 345")
                sample("why-synthetic", "LOWG production trace uses local handoff frequencies, not the Doc 9432 example frequency")
                sample("production-renderer-path", "AfterLandingVacateVia, ContactFrequency, and composite VacateReadback plus FrequencyReadback rendered through EvidenceFactAdapters.fromTransmissionRecords")
                expect {
                    afterLandingPhraseology(aircraft).firstRightWhenVacatedContactGroundExchange(groundFrequency)
                }
            }
        }

        report.assertNoFailures()
    }

    @Test
    fun `after-landing first-right rendering keeps unsupported variants explicit`() {
        val aircraft = AircraftId("FASTAIR 345")
        val groundFrequency = Frequency.unsafe("118.350")

        val report = simEvidence("icao9432-rendered-after-landing-first-right-unsupported") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "icao9432-rendered-after-landing-first-right-unsupported",
                    records = listOf(
                        unsupportedWhenAbleFirstRightRecord(aircraft),
                        wrongExitFirstRightRecord(aircraft),
                        reversedFirstRightFrequencyReadbackRecord(
                            aircraft = aircraft,
                            frequency = groundFrequency,
                        ),
                    ),
                )
            }
            invariant("unsupported variants cannot satisfy first-right contact-ground exchange") {
                expect {
                    afterLandingPhraseology(aircraft).firstRightWhenVacatedContactGroundExchange(groundFrequency)
                }
            }
        }

        val outcome = report.results.single().outcome
        kotlin.test.assertTrue(outcome is EvidenceAuditOutcome.Fail)
        kotlin.test.assertTrue(report.results.single().activationFactIds.isEmpty())
    }

    private fun stopImmediatelyRecord(
        aircraft: AircraftId,
    ): TransmissionRecord {
        val output = ControllerOutput.Instruct.fromEmergencyPolicy(
            instruction = StopImmediately(aircraft),
            urgency = Urgency.SAFETY,
            trace = DecisionTrace("TEST-STOP", "test stop-immediately phraseology", emptyList()),
            doctrine = "ICAO Doc 9432 §4.5.11",
        )
        return TransmissionRecord(
            transmissionId = TransmissionId(80),
            time = SimTime.ZERO,
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(2),
            speaker = SpeakerRef.Controller(ControllerId("LOWG_TWR")),
            receiver = ReceiverRef.Pilot(aircraft),
            utterance = Utterance.FromController(output),
        )
    }

    private fun pilotReportRecord(
        transmissionId: TransmissionId,
        aircraft: AircraftId,
        event: ReportEvent,
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = transmissionId,
            time = SimTime.ZERO,
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(2),
            speaker = SpeakerRef.Pilot(aircraft),
            receiver = ReceiverRef.Controller(ControllerId("LOWG_TWR")),
            utterance = Utterance.FromPilot(Report(events = listOf(event))),
        )

    private fun contactGroundRecord(
        aircraft: AircraftId,
        frequency: Frequency,
    ): TransmissionRecord {
        val output = ControllerOutput.Instruct.fromMissedHandoffReissue(
            instruction = ContactFrequency(target = aircraft, role = RoleName.GROUND, frequency = frequency),
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("TEST-CONTACT-GROUND", "test contact-ground phraseology", emptyList()),
        )
        return TransmissionRecord(
            transmissionId = TransmissionId(92),
            time = SimTime.ZERO,
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(2),
            speaker = SpeakerRef.Controller(ControllerId("GEORGETOWN_TWR")),
            receiver = ReceiverRef.Pilot(aircraft),
            utterance = Utterance.FromController(output),
        )
    }

    private fun firstRightWhenVacatedRecord(
        aircraft: AircraftId,
    ): TransmissionRecord {
        val instruction = AfterLandingVacateVia(
            target = aircraft,
            exit = PointId("FIRST_RIGHT"),
            whenAble = false,
        )
        val output = ControllerOutput.Instruct.fromCoordinationReissue(
            coordination = OutstandingCoordination(
                aircraft = aircraft,
                dispatch = Dispatch.Direct(instruction),
                certificationEvidence = NonEmptyList(
                    CertificationEvidence.RuntimeChecked(
                        checkId = "synthetic-icao9432-first-right-phraseology",
                        summary = "Synthetic ICAO 9432 §4.9 first-right phraseology branch",
                    ),
                    emptyList(),
                ),
                expectedReadback = setOf(
                    VacateReadback(via = PointId("FIRST_RIGHT")),
                ),
                issuedAt = SimTime.ZERO,
            ),
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("TEST-FIRST-RIGHT", "test first-right phraseology", emptyList()),
        )
        return TransmissionRecord(
            transmissionId = TransmissionId(91),
            time = SimTime.ZERO,
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(2),
            speaker = SpeakerRef.Controller(ControllerId("GEORGETOWN_TWR")),
            receiver = ReceiverRef.Pilot(aircraft),
            utterance = Utterance.FromController(output),
        )
    }

    private fun unsupportedWhenAbleFirstRightRecord(
        aircraft: AircraftId,
    ): TransmissionRecord {
        val instruction = AfterLandingVacateVia(
            target = aircraft,
            exit = PointId("FIRST_RIGHT"),
            whenAble = true,
        )
        val output = ControllerOutput.Instruct.fromCoordinationReissue(
            coordination = OutstandingCoordination(
                aircraft = aircraft,
                dispatch = Dispatch.Direct(instruction),
                certificationEvidence = NonEmptyList(
                    CertificationEvidence.RuntimeChecked(
                        checkId = "synthetic-icao9432-when-able-first-right",
                        summary = "Synthetic unsupported when-able first-right phraseology branch",
                    ),
                    emptyList(),
                ),
                expectedReadback = setOf(VacateReadback(via = PointId("FIRST_RIGHT"))),
                issuedAt = SimTime.ZERO,
            ),
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("TEST-FIRST-RIGHT-WHEN-ABLE", "test unsupported first-right phraseology", emptyList()),
        )
        return TransmissionRecord(
            transmissionId = TransmissionId(94),
            time = SimTime.ZERO,
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(2),
            speaker = SpeakerRef.Controller(ControllerId("GEORGETOWN_TWR")),
            receiver = ReceiverRef.Pilot(aircraft),
            utterance = Utterance.FromController(output),
        )
    }

    private fun wrongExitFirstRightRecord(
        aircraft: AircraftId,
    ): TransmissionRecord {
        val instruction = AfterLandingVacateVia(
            target = aircraft,
            exit = PointId("BRAVO"),
            whenAble = false,
        )
        val output = ControllerOutput.Instruct.fromCoordinationReissue(
            coordination = OutstandingCoordination(
                aircraft = aircraft,
                dispatch = Dispatch.Direct(instruction),
                certificationEvidence = NonEmptyList(
                    CertificationEvidence.RuntimeChecked(
                        checkId = "synthetic-icao9432-wrong-exit-first-right",
                        summary = "Synthetic unsupported wrong-exit first-right phraseology branch",
                    ),
                    emptyList(),
                ),
                expectedReadback = setOf(VacateReadback(via = PointId("BRAVO"))),
                issuedAt = SimTime.ZERO,
            ),
            urgency = Urgency.PROGRESSION,
            trace = DecisionTrace("TEST-FIRST-RIGHT-WRONG-EXIT", "test unsupported first-right exit", emptyList()),
        )
        return TransmissionRecord(
            transmissionId = TransmissionId(95),
            time = SimTime.ZERO,
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(2),
            speaker = SpeakerRef.Controller(ControllerId("GEORGETOWN_TWR")),
            receiver = ReceiverRef.Pilot(aircraft),
            utterance = Utterance.FromController(output),
        )
    }

    private fun frequencyReadbackRecord(
        aircraft: AircraftId,
        frequency: Frequency,
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = TransmissionId(93),
            time = SimTime.ZERO + SimDuration.ofSeconds(3),
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(5),
            speaker = SpeakerRef.Pilot(aircraft),
            receiver = ReceiverRef.Controller(ControllerId("GEORGETOWN_TWR")),
            utterance = Utterance.FromPilot(Readback(listOf(SimpleElement(FrequencyReadback(frequency))))),
        )

    private fun firstRightFrequencyReadbackRecord(
        aircraft: AircraftId,
        frequency: Frequency,
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = TransmissionId(93),
            time = SimTime.ZERO + SimDuration.ofSeconds(3),
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(5),
            speaker = SpeakerRef.Pilot(aircraft),
            receiver = ReceiverRef.Controller(ControllerId("GEORGETOWN_TWR")),
            utterance = Utterance.FromPilot(
                Readback(
                    listOf(
                        SimpleElement(VacateReadback(via = PointId("FIRST_RIGHT"))),
                        SimpleElement(FrequencyReadback(frequency)),
                    ),
                ),
            ),
        )

    private fun reversedFirstRightFrequencyReadbackRecord(
        aircraft: AircraftId,
        frequency: Frequency,
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = TransmissionId(96),
            time = SimTime.ZERO + SimDuration.ofSeconds(3),
            endedAt = SimTime.ZERO + SimDuration.ofSeconds(5),
            speaker = SpeakerRef.Pilot(aircraft),
            receiver = ReceiverRef.Controller(ControllerId("GEORGETOWN_TWR")),
            utterance = Utterance.FromPilot(
                Readback(
                    listOf(
                        SimpleElement(FrequencyReadback(frequency)),
                        SimpleElement(VacateReadback(via = PointId("FIRST_RIGHT"))),
                    ),
                ),
            ),
        )

    private fun combinedEvidenceFactSet(
        scenarioId: String,
        vararg sets: EvidenceFactSet,
    ): EvidenceFactSet {
        val facts = sets
            .flatMap { set -> set.orderedFacts() }
            .mapIndexed { index, fact ->
                val extractionPath = EvidenceExtractionPath("combined[$index].${fact.provenance.extractionPath.value}")
                fact.copy(
                    id = FactId(
                        listOf(
                            scenarioId,
                            fact.provenance.origin.label,
                            index.toString().padStart(6, '0'),
                            fact.payload.kind.name,
                            extractionPath.value,
                        ).joinToString(separator = "::"),
                    ),
                    provenance = fact.provenance.copy(
                        scenarioId = scenarioId,
                        sequence = EvidenceSequence(index),
                        extractionPath = extractionPath,
                    ),
                )
            }
        return EvidenceFactSet(
            scenarioId = scenarioId,
            facts = facts,
            diagnostic = sets.joinToString(prefix = "Combined evidence: ") { set -> set.diagnostic },
        )
    }
}
