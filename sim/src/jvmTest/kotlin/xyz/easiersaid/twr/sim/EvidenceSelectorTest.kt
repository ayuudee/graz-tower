package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.testing.TransmissionRecord

class EvidenceSelectorTest {
    @Test
    fun `nth selection makes repeated equal-time reports explicit`() {
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("repeated-equal-time") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "repeated-equal-time",
                    records = listOf(
                        reportRecord(index = 0, event = ReportEvent.Ready),
                        reportRecord(index = 1, event = ReportEvent.Ready),
                    ),
                )
            }

            source("ready report order") {
                cites(ICAO9432.Taxi.HoldingPointLimit)
                expect {
                    (reports<ReportEvent.Ready>(aircraft).nth(0) before
                        reports<ReportEvent.Ready>(aircraft).nth(1)).toOutcome()
                }
            }
        }

        report.assertNoFailures()
        assertEquals(2, report.results.single().activationFactIds.size)
    }

    @Test
    fun `missing nth selection fails instead of returning null`() {
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("missing-nth") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "missing-nth",
                    records = listOf(reportRecord(index = 0, event = ReportEvent.Ready)),
                )
            }

            source("second ready report") {
                cites(ICAO9432.Taxi.HoldingPointLimit)
                expect {
                    (reports<ReportEvent.Ready>(aircraft).first() before
                        reports<ReportEvent.Ready>(aircraft).nth(1)).toOutcome()
                }
            }
        }

        val outcome = report.results.single().outcome
        assertTrue(outcome is EvidenceAuditOutcome.Fail)
        assertTrue((outcome as EvidenceAuditOutcome.Fail).reason.contains("Missing evidence"))
    }

    @Test
    fun `count and absence selectors are explicit`() {
        val aircraft = AircraftId("OE-ABC")
        val report = simEvidence("count-absence") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "count-absence",
                    records = listOf(reportRecord(index = 0, event = ReportEvent.Ready)),
                )
            }

            source("one ready report") {
                cites(ICAO9432.Taxi.HoldingPointLimit)
                expect { reports<ReportEvent.Ready>(aircraft).exactly(1) }
            }

            invariant("no takeoff clearance") {
                expect { instructions<ClearedForTakeoff>(aircraft).none() }
            }
        }

        report.assertNoFailures()
        assertEquals(1, report.results.first { result -> result.id == "one ready report" }.activationFactIds.size)
        assertTrue(report.results.first { result -> result.id == "no takeoff clearance" }.activationFactIds.isEmpty())
    }

    private fun reportRecord(
        index: Long,
        event: ReportEvent,
    ): TransmissionRecord =
        TransmissionRecord(
            transmissionId = TransmissionId(200 + index),
            time = SimTime.ZERO,
            speaker = SpeakerRef.Pilot(AircraftId("OE-ABC")),
            receiver = ReceiverRef.Controller(ControllerId("LOWG_TWR")),
            utterance = Utterance.FromPilot(Report(events = listOf(event))),
        )
}
