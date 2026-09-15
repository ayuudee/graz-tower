package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import xyz.easiersaid.twr.protocol.AircraftId

/**
 * ICAO 9432 chunk-01 source-mapped evidence test for §2.8.1.4
 * (doubtful reception triggers repetition request). Closes the COMMS-1
 * expected-gap unit `communications_2_8_1_en::0a964f42b6100596`.
 *
 * **Landing: covered-green.** The test observes a real sim radio-overlap
 * scenario via [EvidenceFactAdapters.receptionDoubtOverlap]: a controller
 * transmission is stepped on, the receiver pilot requests repetition with
 * `SayAgain`, and the evidence adapter links the doubtful transmission to
 * that `SayAgainRef`.
 *
 * Per AGENTS.md commandment 4 (tests prove the real job), this test
 * exercises real sim radio events via `EvidenceFactAdapters.receptionDoubtOverlap`
 * — it does NOT use `fromProjectedPayloads` to fabricate compliant doubt
 * facts. The broader cognitive-mission recovery path is deliberately filed
 * as `D-AUDIT.15-FOLLOWUP`; this source unit requires the repetition request
 * obligation, not full recovery of every mission-level instruction.
 *
 * One test method per source unit (R6 acceptance: source units do not
 * share methods so the assertion shape is unambiguous — green via
 * `assertNoFailures`, red via direct `report.results` inspection).
 */
class Icao9432Chunk01ReceptionDoubtEvidenceTest {
    private val aircraft = AircraftId("OE-ABC")

    @Test
    fun `reception-doubt source unit is covered green via real radio overlap`() {
        val report = simEvidence("icao9432-chunk01-reception-doubt") {
            observe {
                EvidenceFactAdapters.receptionDoubtOverlap(
                    scenarioId = "icao9432-chunk01-reception-doubt",
                )
            }

            source("doubt-triggers-repetition-request") {
                cites(ICAO9432.Communications.ReceptionDoubtRepetitionRequested)
                expect { receptionDoubt(aircraft).requiresRepetitionResponse() }
            }
        }

        report.assertNoFailures()
        val sourceRef = ICAO9432.Communications.ReceptionDoubtRepetitionRequested
        val cited = report.results.filter { result -> result.sources.contains(sourceRef) }
        assertTrue(
            cited.isNotEmpty(),
            "expected cited outcome for ${sourceRef.canonicalId}; got no cited results",
        )
        cited.forEach { result ->
            assertIs<EvidenceAuditOutcome.Pass>(
                result.outcome,
                "expected Pass outcome for ${sourceRef.canonicalId} (COMMS-1 covered-green); got: " +
                cited.joinToString { "${it.id}=${it.outcome::class.simpleName}" },
            )
        }
    }
}
