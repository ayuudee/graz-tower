package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertTrue
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId

/**
 * ICAO 9432 chunk-01 paired source-mapped evidence test for §2.8.2.1
 * (transfer of communications). Closes FN44-GAP-1 and FN44-GAP-2:
 *
 * - **FN44-GAP-1** (`ControllerAdvisedFrequencyChange`) lands
 *   **covered-green**. The LOWG circuit-training trace emits a
 *   `ContactFrequency` controller instruction during the ground-to-tower
 *   handoff (per `controller/bdi/Action.kt`'s missed-handoff projection).
 *   The new adapter projection added in this task observes this and emits
 *   `FrequencyTransfer(mode = ControllerAdvised, …)`; the selector returns
 *   `Pass`. Test method calls `report.assertNoFailures()`.
 *
 * - **FN44-GAP-2** (`PilotNotifiesAbsentAdvice`) lands **covered-red**.
 *   The sim does NOT currently emit `Request(RequestFrequencyChange(…))`
 *   pilot transmissions; the adapter projection observes no such facts and
 *   the audit honestly reports `Fail`. Test method does NOT call
 *   `assertNoFailures()`; it asserts directly on `report.results` that the
 *   expected `Fail` outcome exists for the cited source ref. JUnit passes
 *   (build green) while the audit's `Fail` surfaces the regulation gap —
 *   spawned production-repair epic `fn-49-sim-emits-pilot-notified-frequency`
 *   will close this when the sim is taught to emit pilot-initiated
 *   frequency-change requests.
 *
 * One test method per source unit (R6 acceptance: source units do not
 * share methods so the assertion shape is unambiguous — green via
 * `assertNoFailures`, red via direct `report.results` inspection).
 */
class Icao9432Chunk01FrequencyTransferEvidenceTest {
    private val aircraft = AircraftId("OE-ABC")

    @Test
    fun `controller-advised frequency transfer source unit lands covered green against LOWG trace`() {
        val report = simEvidence("icao9432-chunk01-controller-advised-frequency-transfer") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "icao9432-chunk01-controller-advised-frequency-transfer",
                    outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                    untilMinutes = LOWG_UNTIL_MINUTES,
                )
            }

            source("controller-advised-frequency-change") {
                cites(ICAO9432.TransferCommunications.ControllerAdvisedFrequencyChange)
                expect { frequencyTransfer(aircraft).controllerAdvised() }
            }
        }

        // covered-green: report.assertNoFailures() must pass.
        report.assertNoFailures()
        val sourceRef = ICAO9432.TransferCommunications.ControllerAdvisedFrequencyChange
        val cited = report.results.single { result -> result.sources.contains(sourceRef) }
        assertTrue(
            cited.outcome is EvidenceAuditOutcome.Pass,
            "expected Pass outcome for ${sourceRef.canonicalId}; got: ${cited.outcome::class.simpleName}",
        )
    }

    @Test
    fun `pilot-notified frequency change source unit is covered red against current LOWG trace`() {
        val report = simEvidence("icao9432-chunk01-pilot-notified-frequency-change") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "icao9432-chunk01-pilot-notified-frequency-change",
                    outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                    untilMinutes = LOWG_UNTIL_MINUTES,
                )
            }

            source("pilot-notifies-absent-advice") {
                cites(ICAO9432.TransferCommunications.PilotNotifiesAbsentAdvice)
                expect { frequencyTransfer(aircraft).pilotNotified() }
            }
        }

        // covered-red: assert directly on report.results, NOT via assertNoFailures.
        // Calling assertNoFailures() here would propagate the Fail outcome into a
        // JUnit failure and turn `./gradlew-nix build` red — incompatible with
        // AGENTS.md commandment 2 (no half-baked commits). The honest red landing
        // is tracked by spawned repair epic fn-49.
        val sourceRef = ICAO9432.TransferCommunications.PilotNotifiesAbsentAdvice
        val cited = report.results.filter { result -> result.sources.contains(sourceRef) }
        assertTrue(
            cited.any { result -> result.outcome is EvidenceAuditOutcome.Fail },
            "expected Fail outcome for ${sourceRef.canonicalId} (FN44-GAP-2 covered-red); got: " +
                cited.joinToString { "${it.id}=${it.outcome::class.simpleName}" },
        )
    }

    private companion object {
        private const val LOWG_UNTIL_MINUTES: Long = 45L
    }
}
