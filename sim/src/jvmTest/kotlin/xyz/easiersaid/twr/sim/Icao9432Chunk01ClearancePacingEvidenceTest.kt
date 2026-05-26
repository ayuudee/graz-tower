package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertTrue
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId

/**
 * ICAO 9432 chunk-01 source-mapped evidence test for §2.8.3.2
 * (controllers should pace clearances; should avoid passing a clearance
 * during complicated taxi manoeuvres; on no occasion during line-up or
 * take-off). Closes the FN33-MODEL-1 model-gap source unit
 * `icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2`.
 *
 * **Landing: covered-green via [EvidenceAuditOutcome.Advisory].** §2.8.3.2
 * is RFC-2119 advisory ("should" / "avoid" / "on no occasion"), not
 * mandatory. The audit honestly observes that controllers issue clearances
 * during sensitive pilot windows (line-up, takeoff roll) in the LOWG
 * circuit-training trace and surfaces those observations as `Advisory`
 * outcomes carrying per-clearance [AdvisoryViolation] records. `Advisory`
 * is reported, counted, and rendered distinctly by the audit, but
 * [EvidenceAuditReport.assertNoFailures] treats only `Fail` as a JUnit
 * failure — so the build stays green while the regulation's advisory
 * outcome is documented.
 *
 * The test asserts both:
 * 1. `report.assertNoFailures()` passes (no `Fail` outcomes; `Advisory` is
 *    not propagated to JUnit failures).
 * 2. At least one result carries an `Advisory` outcome — confirming the
 *    audit produced the §2.8.3.2 observation and did not vacuously skip
 *    the source unit.
 *
 * The `Advisory` outcome is the design pin for FN33-MODEL-1: §2.8.3.2 was
 * previously classified `blocked_by_model_gap` because the audit surface
 * had no slot for "run-and-record, never block CI" obligations. The new
 * sealed leaf added by `fn-48-icao-9432-chunk-01-drive-expected-gap.4`
 * gives the regulation a typed home without requiring it to fail or
 * vacuously pass.
 *
 * **Distinctness from POLICY-1**: this test observes *conditions* (was a
 * clearance issued during line-up?) — NOT *prescriptive timing rules*
 * (the controller MUST wait for taxi to simplify). If a future refactor
 * pushes the selector or the projection toward encoding prescriptive
 * policy, that is POLICY-1 territory and must halt for explicit design
 * review.
 *
 * One test method per source unit (R6 acceptance: source units do not
 * share methods so the assertion shape is unambiguous — green via
 * `assertNoFailures` plus an Advisory-emitted check; red via direct
 * `report.results` inspection).
 */
class Icao9432Chunk01ClearancePacingEvidenceTest {
    private val aircraft = AircraftId("OE-ABC")

    @Test
    fun `clearance-pacing source unit lands covered green as Advisory against LOWG trace`() {
        val report = simEvidence("icao9432-chunk01-clearance-pacing") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "icao9432-chunk01-clearance-pacing",
                    outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                    untilMinutes = LOWG_UNTIL_MINUTES,
                )
            }

            source("clearance-pacing-during-sensitive-phases") {
                cites(ICAO9432.Readback.ClearancePacingAdvisory)
                expect {
                    clearancePacing(aircraft).whenIssuedDuring(
                        PacingWindow.entries.filter { window -> window != PacingWindow.Other },
                    )
                }
            }
        }

        // covered-green: `assertNoFailures()` passes because Advisory is not
        // a Fail. This is the design pin — §2.8.3.2 is advisory and must not
        // block the build.
        report.assertNoFailures()

        // Non-vacuity guard: confirm the audit actually emitted an Advisory
        // outcome for the cited source ref. If this assertion fails the test
        // landed as covered-red (no clearance-pacing facts observed in trace)
        // and a `fn-51-…` repair epic should be tracked. The integration
        // test in EvidenceFactsTest exercises the projection-layer path so
        // landing covered-red here while that test passes would indicate a
        // selector / cite-binding regression, not a sim signal gap.
        val sourceRef = ICAO9432.Readback.ClearancePacingAdvisory
        val cited = report.results.filter { result -> result.sources.contains(sourceRef) }
        assertTrue(
            cited.any { result -> result.outcome is EvidenceAuditOutcome.Advisory },
            "expected Advisory outcome for ${sourceRef.canonicalId} (FN33-MODEL-1 covered-green); got: " +
                cited.joinToString { "${it.id}=${it.outcome::class.simpleName}" },
        )
    }

    private companion object {
        private const val LOWG_UNTIL_MINUTES: Long = 45L
    }
}
