package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.RunwayId

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
}
