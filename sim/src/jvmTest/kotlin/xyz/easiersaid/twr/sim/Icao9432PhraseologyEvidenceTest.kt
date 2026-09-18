package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.StopImmediately
import xyz.easiersaid.twr.protocol.Urgency
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
                        records = listOf(stopImmediatelyRecord(aircraft)),
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
