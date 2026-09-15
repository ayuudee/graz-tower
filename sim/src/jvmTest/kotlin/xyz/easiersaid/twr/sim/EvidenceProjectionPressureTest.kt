package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.testing.TransmissionRecord
import xyz.easiersaid.twr.sim.testing.toTransmissionRecords

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

    @Test
    fun `critical phase radio silence surfaces routine transmissions in observed phase windows`() {
        val aircraft = AircraftId("OE-ABC")

        val report = simEvidence("fn44-critical-phase-radio-silence") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "fn44-critical-phase-radio-silence",
                    outcomes = listOf(CircuitOutcome.FullStop),
                    untilMinutes = 30,
                )
            }

            source("no routine controller transmissions in critical windows") {
                cites(ICAO9432.CriticalPhase.CriticalPhaseRadioSilence)
                expect { criticalPhase(aircraft).routineControllerTransmissions().none() }
            }
        }

        val result = report.results.single()
        assertTrue(result.activationFactIds.isNotEmpty())
        assertTrue(
            result.outcome is EvidenceAuditOutcome.Fail,
            "expected covered-red critical-phase result once routine controller transmissions are projected; " +
                "got ${result.outcome}",
        )
    }

    @Test
    fun `ground-station test signal duration is source-mapped from real start-end records`() {
        val station = ControllerId("LOWG_TWR")
        val transmission = InFlightTransmission(
            id = TransmissionId(410),
            speaker = SpeakerRef.Controller(station),
            receiver = ReceiverRef.Controller(station),
            frequency = Frequency.unsafe("118.200"),
            utterance = Utterance.GroundStationTestSignal(TestSignalPurpose.TransmitterAdjustment),
            startedAt = SimTime.ZERO,
            endsAt = SimTime.ofSeconds(10),
        )

        val report = simEvidence("fn44-ground-station-test-signal-duration") {
            observe {
                EvidenceFactAdapters.fromTransmissionRecords(
                    scenarioId = "fn44-ground-station-test-signal-duration",
                    records = listOf(
                        SimEvent.TransmissionStart(time = transmission.startedAt, transmission = transmission),
                        SimEvent.TransmissionEnd(time = transmission.endsAt, transmissionId = transmission.id),
                    ).toTransmissionRecords(),
                )
            }

            source("ground station test signal duration within ten seconds") {
                cites(ICAO9432.TestProcedures.GroundStationTestSignalDuration)
                expect { groundStationTestSignals().allWithin(SimDuration.ofSeconds(10)) }
            }
        }

        report.assertNoFailures()
        val result = report.results.single()
        assertEquals(EvidenceClaimKind.SimObservedSourceBehaviour, result.claimKind)
        assertTrue(result.activationFactIds.isNotEmpty())
        assertTrue(result.outcome is EvidenceAuditOutcome.Pass)
    }

    @Test
    fun `open transfer communications gaps stay typed and source-specific`() {
        val report = simEvidence("fn44-transfer-communications") {
            observe {
                EvidenceFactSet(
                    scenarioId = "fn44-transfer-communications",
                    facts = emptyList(),
                    diagnostic = "No frequency-transfer projection exists in current traces",
                )
            }

            source("controller-advised frequency change remains projection gap") {
                cites(ICAO9432.TransferCommunications.ControllerAdvisedFrequencyChange)
                expect {
                    expectedGap(
                        EvidenceGaps.ControllerAdvisedFrequencyTransferProjection,
                        "Current traces model release/autonomous contact, not controller CONTACT advice.",
                    )
                }
            }
        }

        report.assertNoFailures()
        assertEquals(1, report.results.count { result -> result.outcome is EvidenceAuditOutcome.ExpectedGap })
        assertTrue(report.results.all { result -> result.activationFactIds.isEmpty() })
    }
}
