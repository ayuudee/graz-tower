package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.testing.TransmissionRecord

class EvidenceProjectionPressureTest {
    @Test
    fun `essential aerodrome information before taxi activates known-receipt evidence`() {
        val aircraft = AircraftId("OE-ABC")

        val report = simEvidence("fn44-essential-aerodrome-information") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "fn44-essential-aerodrome-information",
                    records = listOf(
                        TransmissionRecord(
                            transmissionId = TransmissionId(400),
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
            }

            source("known information receipt before taxi") {
                cites(ICAO9432.AerodromeInformation.EssentialAerodromeInformationTiming)
                sample("timing-context", AerodromeInformationTimingContext.BeforeTaxi)
                expect { aerodromeInformation(aircraft).beforeTaxi().wasPassedOrKnownReceived() }
            }
        }

        report.assertNoFailures()
        val result = report.results.single()
        assertEquals(EvidenceClaimKind.SimObservedSourceBehaviour, result.claimKind)
        assertTrue(result.activationFactIds.isNotEmpty())
        assertTrue(result.outcome is EvidenceAuditOutcome.Pass)
    }
}
