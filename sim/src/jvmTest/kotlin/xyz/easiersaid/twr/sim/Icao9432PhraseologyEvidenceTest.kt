package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.RunwayId

class Icao9432PhraseologyEvidenceTest {
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
