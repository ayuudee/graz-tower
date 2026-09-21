package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.protocol.AircraftId

class Icao9432Chunk01CommunicationsPhraseologyEvidenceTest {
    @Test
    fun `synthetic rendered communication examples cover ICAO 9432 section 2_8_1 phraseology`() {
        val report = simEvidence("icao9432-communications-phraseology-examples") {
            observe {
                EvidenceFactAdapters.fromProjectedPayloads(
                    scenarioId = "icao9432-communications-phraseology-examples",
                    payloads = listOf(
                        EvidenceFactPayload.RenderedCommunicationPhraseologyExample(
                            template = RenderedCommunicationPhraseologyTemplate.InitialContactStationThenAircraft,
                            tokens = listOf(
                                CommunicationPhraseologyToken.StationCallsign("STEPHENVILLE TOWER"),
                                CommunicationPhraseologyToken.AircraftCallsign(AircraftId("G-ABCD")),
                            ),
                            text = RenderedPhraseText("STEPHENVILLE TOWER G-ABCD"),
                        ),
                        EvidenceFactPayload.RenderedCommunicationPhraseologyExample(
                            template = RenderedCommunicationPhraseologyTemplate.InitialContactAircraftThenStation,
                            tokens = listOf(
                                CommunicationPhraseologyToken.AircraftCallsign(AircraftId("G-ABCD")),
                                CommunicationPhraseologyToken.StationCallsign("STEPHENVILLE TOWER"),
                            ),
                            text = RenderedPhraseText("G-ABCD STEPHENVILLE TOWER"),
                        ),
                        EvidenceFactPayload.RenderedCommunicationPhraseologyExample(
                            template = RenderedCommunicationPhraseologyTemplate.GroundStationAllStationsBroadcast,
                            tokens = listOf(
                                CommunicationPhraseologyToken.AllStations,
                                CommunicationPhraseologyToken.StationCallsign("ALEXANDER CONTROL"),
                                CommunicationPhraseologyToken.BroadcastContent("FUEL DUMPING COMPLETED"),
                            ),
                            text = RenderedPhraseText("ALL STATIONS ALEXANDER CONTROL, FUEL DUMPING COMPLETED"),
                        ),
                        EvidenceFactPayload.RenderedCommunicationPhraseologyExample(
                            template = RenderedCommunicationPhraseologyTemplate.AircraftAllStationsBroadcast,
                            tokens = listOf(
                                CommunicationPhraseologyToken.AllStations,
                                CommunicationPhraseologyToken.AircraftCallsign(AircraftId("G-CDAB")),
                                CommunicationPhraseologyToken.BroadcastContent(
                                    "WESTBOUND MARLO VOR TO STEPHENVILLE LEAVING FL 260 DESCENDING FL 150",
                                ),
                            ),
                            text = RenderedPhraseText(
                                "ALL STATIONS G-CDAB WESTBOUND MARLO VOR TO STEPHENVILLE " +
                                    "LEAVING FL 260 DESCENDING FL 150",
                            ),
                        ),
                    ),
                    origin = EvidenceFactOrigin.SyntheticProjection,
                    diagnostic = "Synthetic rendered communication phraseology examples; not live radio projection",
                )
            }

            sourceRenderedExample("full-callsign initial-contact examples") {
                cites(ICAO9432.Communications.FullCallsignInitialContact)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §2.8.1.1")
                sample("claim-scope", "synthetic rendered example phraseology")
                expect {
                    communicationPhraseologyExamples().exampleBlock()
                }
            }
            sourceRenderedExample("ground-station all-stations broadcast example") {
                cites(ICAO9432.Communications.GroundStationAllStationsBroadcast)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §2.8.1.2")
                sample("claim-scope", "synthetic rendered example phraseology")
                expect {
                    communicationPhraseologyExamples().exampleBlock()
                }
            }
            sourceRenderedExample("aircraft all-stations broadcast example") {
                cites(ICAO9432.Communications.AircraftAllStationsBroadcast)
                sample("source", "ICAO Doc 9432, Manual of Radiotelephony, Fourth Edition, 2007, §2.8.1.3")
                sample("claim-scope", "synthetic rendered example phraseology")
                expect {
                    communicationPhraseologyExamples().exampleBlock()
                }
            }
        }

        report.assertNoFailures()
        assertEquals(
            setOf(EvidenceClaimKind.SyntheticRenderedPhraseologyExample),
            report.results.map { result -> result.claimKind }.toSet(),
        )
    }
}
