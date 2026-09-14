package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertTrue
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId

/**
 * ICAO 9432 chunk-01 paired source-mapped evidence test for §2.8.2.1
 * (transfer of communications). Covers the controller-advised and
 * pilot-notified transfer branches:
 *
 * - **FN44-GAP-1** (`ControllerAdvisedFrequencyChange`) lands
 *   **covered-green**. The LOWG circuit-training trace emits a
 *   `ContactFrequency` controller instruction during the ground-to-tower
 *   handoff (per `controller/bdi/Action.kt`'s missed-handoff projection).
 *   The new adapter projection added in this task observes this and emits
 *   `FrequencyTransfer(mode = ControllerAdvised, …)`; the selector returns
 *   `Pass`. Test method calls `report.assertNoFailures()`.
 *
 * - `PilotNotifiesAbsentAdvice` lands **covered-green** via
 *   `fn-49-sim-emits-pilot-notified-frequency`.
 *   The G2 LOWG → LJMB Transit trace emits
 *   `Request(RequestFrequencyChange(frequency = null))` after LOWG radar
 *   service termination and before the autonomous LJMB initial contact.
 *   The adapter projects the absent-frequency payload as
 *   `FrequencyTransferTarget.UnitOnly("UNSPECIFIED")`; the selector returns
 *   `Pass`. Test method calls `report.assertNoFailures()`.
 *
 * One test method per source unit (R6 acceptance: source units do not
 * share methods so the assertion shape is unambiguous — green via
 * `assertNoFailures`, red via direct `report.results` inspection).
 */
class Icao9432Chunk01FrequencyTransferEvidenceTest {
    private val controllerAdvisedAircraft = AircraftId("OE-ABC")
    private val pilotNotifiedTransitAircraft = AircraftId("OE-XYZ")

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
                expect { frequencyTransfer(controllerAdvisedAircraft).controllerAdvised() }
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
    fun `pilot-notified frequency change source unit lands covered green against G2 trace`() {
        val report = simEvidence("icao9432-chunk01-pilot-notified-frequency-change") {
            observe {
                EvidenceFactAdapters.lowgLjmbTransit(
                    scenarioId = "icao9432-chunk01-pilot-notified-frequency-change",
                    untilMinutes = LOWG_LJMB_UNTIL_MINUTES,
                )
            }

            source("pilot-notifies-absent-advice") {
                cites(ICAO9432.TransferCommunications.PilotNotifiesAbsentAdvice)
                expect { frequencyTransfer(pilotNotifiedTransitAircraft).pilotNotified() }
            }
        }

        // covered-green: report.assertNoFailures() must pass.
        report.assertNoFailures()
        val sourceRef = ICAO9432.TransferCommunications.PilotNotifiesAbsentAdvice
        val cited = report.results.single { result -> result.sources.contains(sourceRef) }
        assertTrue(
            cited.outcome is EvidenceAuditOutcome.Pass,
            "expected Pass outcome for ${sourceRef.canonicalId}; got: ${cited.outcome::class.simpleName}",
        )
    }

    private companion object {
        private const val LOWG_UNTIL_MINUTES: Long = 45L
        private const val LOWG_LJMB_UNTIL_MINUTES: Long = 90L
    }
}
