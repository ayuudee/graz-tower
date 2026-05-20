package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint

class EvidenceMappedFacadeSpikeTest {
    @Test
    fun `facade keeps evidence mapped test intent terse`() {
        val aircraft = AircraftId("OE-ABC")

        val report = evidenceScenario("lowg-source-mapped-facade") {
            observe {
                LowgObservationPort.runCircuitTraining(
                    scenarioId = "lowg-source-mapped-facade",
                    outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }

            sourceCase(
                id = "taxi clearance before runway use",
                "icao9432-extracted::taxi_4_4_en::417f64324f7495bf",
                "icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e",
            ) {
                sample("runway", RunwayId("16C"))
                expect {
                    instruction<TaxiToHoldingPoint>(aircraft) before
                        report<ReportEvent.Ready>(aircraft) before
                        instruction<LineUpAndWait>(aircraft)
                }
            }

            goldenCase(
                id = "mission completes parked",
                reason = "LOWG touch-and-go plus full-stop should finish parked",
            ) {
                expect {
                    aircraft(aircraft).isParkedAndComplete()
                }
            }

            sourceCase(
                id = "critical phase radio silence currently lacks observation facts",
                "icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4",
            ) {
                expect {
                    expectedGap(
                        planId = "FN39-CONF-1",
                        reason = "critical-phase workload facts are not projected yet",
                    )
                }
            }
        }

        report.assertNoUnexpectedFailures()
        assertEquals(3, report.results.size)
    }
}
