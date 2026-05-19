package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedForTakeoffReadback
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint

class EvidenceDslTest {
    @Test
    fun `protocolEvidence expresses structural readback without phraseology overclaim`() {
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")

        val report = protocolEvidence("protocol-readback") {
            structuralReadback("takeoff clearance structural readback", ClearedForTakeoff(aircraft, runway)) {
                cites(ICAO9432.Readback.RequiredItems)
                requires(ClearedForTakeoffReadback(runway))
            }
        }

        report.assertNoFailures()
        assertEquals(EvidenceClaimKind.StructuralProtocolRequirement, report.results.single().claimKind)
        assertTrue(report.format().contains("structural"))
        assertTrue(!report.format().contains("phraseology compliance"))
        assertEquals(ICAO9432.Readback.RequiredItems, report.results.single().sources)
    }

    @Test
    fun `simEvidence expresses typed-source LOWG ordering without monitor vocabulary`() {
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")

        val report = simEvidence("lowg-ordering") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "lowg-ordering",
                    outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }

            source("taxi clearance before runway use") {
                cites(ICAO9432.Taxi.HoldingPointLimit)
                sample("runway", runway)
                expect {
                    (instruction<TaxiToHoldingPoint>(aircraft) before
                        report<ReportEvent.Ready>(aircraft) before
                        instruction<LineUpAndWait>(aircraft)).toOutcome()
                }
            }
        }

        report.assertNoFailures()
        assertEquals(EvidenceClaimKind.SimObservedSourceBehaviour, report.results.single().claimKind)
        assertEquals(ICAO9432.Taxi.HoldingPointLimit, report.results.single().sources)
        assertTrue(!report.format().contains("monitor", ignoreCase = true))
    }

    @Test
    fun `simEvidence without observe fails loudly`() {
        val failure = assertFailsWith<IllegalStateException> {
            simEvidence("missing-observe") {
                invariant("placeholder") {
                    expect { pass("not reached") }
                }
            }
        }

        assertTrue(failure.message.orEmpty().contains("observe"))
    }
}
