package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertTrue
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId

/**
 * ICAO 9432 chunk-01 source-mapped evidence test for §2.8.1.4
 * (doubtful reception triggers repetition request). Closes the COMMS-1
 * expected-gap unit `communications_2_8_1_en::0a964f42b6100596`.
 *
 * **Landing: covered-red.** Today's sim has no reception-quality signal
 * infrastructure — `TransmissionRecord` models speaker / receiver /
 * utterance but does not model reception confidence, partial-reception
 * markers, overlapping-transmission detection ("stepped on"), or
 * unintelligibility. The new adapter projection
 * [EvidenceFactAdapters.receptionDoubtFact] is total over the
 * speaker × utterance × payload matrix but observes no doubt facts on
 * any LOWG circuit-training trace; the audit honestly reports `Fail`
 * for the cited source ref via the [AuditReceptionDoubtSubject]
 * selector.
 *
 * The test method does NOT call [EvidenceAuditReport.assertNoFailures] —
 * doing so would propagate the `Fail` outcome into a JUnit failure and
 * turn `./gradlew-nix build` red, incompatible with AGENTS.md commandment
 * 2 (no half-baked commits). Instead it asserts directly on
 * `report.results` that the expected `Fail` is present for the cited
 * source ref. JUnit passes (build green) while the audit's red outcome
 * stands. The spawned production-repair epic
 * `fn-50-sim-models-reception-quality-comms-1` tracks the sim-side addition of
 * reception-quality input; when that lands, the assertion shape in this
 * test should flip to `report.assertNoFailures()` and the `.plan`
 * pointer (COMMS-1 → fn-50-sim-models-reception-quality-comms-1) deleted.
 *
 * Per AGENTS.md commandment 4 (tests prove the real job), this test
 * exercises the real sim trace via `EvidenceFactAdapters.lowgCircuitTraining`
 * — it does NOT use `fromProjectedPayloads` to fabricate compliant doubt
 * facts and claim covered-green. The construction-site tests for the
 * typed-payload wiring (sealed `ReceptionDoubtSource` leaves, optional
 * `resolvedBy: SayAgainRef?`) and the selector's pass/fail paths live in
 * `EvidenceFactsTest` and use `fromProjectedPayloads` to prove the type
 * is wired, not that the sim observes doubt in real traces.
 *
 * One test method per source unit (R6 acceptance: source units do not
 * share methods so the assertion shape is unambiguous — green via
 * `assertNoFailures`, red via direct `report.results` inspection).
 */
class Icao9432Chunk01ReceptionDoubtEvidenceTest {
    private val aircraft = AircraftId("OE-ABC")

    @Test
    fun `reception-doubt source unit is covered red against current LOWG trace`() {
        val report = simEvidence("icao9432-chunk01-reception-doubt") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "icao9432-chunk01-reception-doubt",
                    outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                    untilMinutes = LOWG_UNTIL_MINUTES,
                )
            }

            source("doubt-triggers-repetition-request") {
                cites(ICAO9432.Communications.ReceptionDoubtRepetitionRequested)
                expect { receptionDoubt(aircraft).requiresRepetitionResponse() }
            }
        }

        // covered-red: assert directly on report.results, NOT via assertNoFailures.
        // Calling assertNoFailures() here would propagate the Fail outcome into a
        // JUnit failure and turn `./gradlew-nix build` red — incompatible with
        // AGENTS.md commandment 2 (no half-baked commits). The honest red landing
        // is tracked by spawned repair epic fn-50-sim-models-reception-quality-comms-1.
        val sourceRef = ICAO9432.Communications.ReceptionDoubtRepetitionRequested
        val cited = report.results.filter { result -> result.sources.contains(sourceRef) }
        assertTrue(
            cited.any { result -> result.outcome is EvidenceAuditOutcome.Fail },
            "expected Fail outcome for ${sourceRef.canonicalId} (COMMS-1 covered-red); got: " +
                cited.joinToString { "${it.id}=${it.outcome::class.simpleName}" },
        )
    }

    private companion object {
        private const val LOWG_UNTIL_MINUTES: Long = 45L
    }
}
