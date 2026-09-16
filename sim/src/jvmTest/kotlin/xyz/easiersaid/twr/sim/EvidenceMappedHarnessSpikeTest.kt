package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertTrue
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.HeadingReadback
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.requiredReadbackAtoms

class EvidenceMappedHarnessSpikeTest {
    @Test
    fun `evidence mapped harness supports typed synthetic source-mapped readback samples`() {
        val aircraftId = AircraftId("OE-ABC")
        val headingSamples = SampleSpace(
            name = "heading",
            representatives = listOf(Heading.unsafe(1), Heading.unsafe(180), Heading.unsafe(360)),
        ).representativeSamples()

        val reports = headingSamples.map { sample ->
            val observation = SyntheticObservationPort.protocolInstruction(
                scenarioId = "synthetic-readback-${sample.value.degrees}",
                aircraftId = aircraftId,
                instruction = FlyHeading(aircraftId, sample.value),
            )
            evidenceMappedReport(
                observation = observation,
                cases = listOf(
                    EvidenceMappedCase(
                        id = "icao9432-readback-heading-${sample.value.degrees}",
                        basis = EvidenceBasis.SourceMapped(
                            sources = listOf(
                                SourceUnitRef("icao9432-extracted::readback_2_8_3_en::15940532b37f8528"),
                                SourceUnitRef("icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60"),
                            ),
                        ),
                        samples = listOf(sample),
                    ) {
                        val instruction = observation.instructions.single().instruction
                        val atoms = requiredReadbackAtoms(instruction)
                        if (atoms == setOf(HeadingReadback(sample.value))) {
                            pass("required readback atom is HeadingReadback(${sample.value.degrees})")
                        } else {
                            fail("unexpected required readback atoms: $atoms")
                        }
                    },
                ),
            )
        }

        reports.forEach { report -> report.assertNoUnexpectedFailures() }
        assertTrue(reports.all { report -> report.results.single().outcome is EvidenceOutcome.Pass })
    }

    @Test
    fun `evidence mapped harness keeps source cases golden cases and expected gaps over one sim observation`() {
        val aircraftId = AircraftId("OE-ABC")
        val observation = LowgObservationPort.runCircuitTraining(
            scenarioId = "lowg-touch-and-go-source-mapped",
            outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
            untilMinutes = 45,
        )
        val report = evidenceMappedReport(
            observation = observation,
            cases = listOf(
                taxiClearanceCase(aircraftId),
                touchAndGoBeforeFullStopCase(aircraftId),
                goldenCompletesAndParksCase(aircraftId),
                essentialAerodromeInformationGapCase(),
                criticalPhaseRadioSilenceGapCase(),
            ),
        )

        report.assertNoUnexpectedFailures()
        assertTrue(report.results.any { result -> result.basis is EvidenceBasis.Golden })
        assertTrue(report.results.count { result -> result.outcome is EvidenceOutcome.ExpectedGap } == 2)
    }

    private fun taxiClearanceCase(aircraftId: AircraftId): EvidenceMappedCase =
        EvidenceMappedCase(
            id = "icao9432-taxi-clearance-before-runway-use",
            basis = EvidenceBasis.SourceMapped(
                sources = listOf(
                    SourceUnitRef("icao9432-extracted::taxi_4_4_en::417f64324f7495bf"),
                    SourceUnitRef("icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e"),
                ),
            ),
            samples = listOf(EvidenceSample(name = "runway", value = RunwayId("16C"), tier = SampleTier.Example)),
        ) {
            val taxi = observation.instructionsTo<TaxiToHoldingPoint>(aircraftId).firstOrNull()
                ?: return@EvidenceMappedCase unexpectedGap("taxi instruction was not observable")
            val ready = observation.reportsFrom<ReportEvent.Ready>(aircraftId).firstOrNull()
                ?: return@EvidenceMappedCase fail("pilot did not report ready", observation.diagnostic)
            val lineUp = observation.instructionsTo<LineUpAndWait>(aircraftId).firstOrNull()
                ?: return@EvidenceMappedCase fail("runway-use instruction was not observed", observation.diagnostic)

            if (taxi.time.millis < ready.time.millis && ready.time.millis < lineUp.time.millis) {
                pass("taxi=${taxi.time.millis}, ready=${ready.time.millis}, lineUp=${lineUp.time.millis}")
            } else {
                fail(
                    reason = "expected taxi clearance before Ready before runway-use instruction",
                    "taxi=${taxi.time.millis}, ready=${ready.time.millis}, lineUp=${lineUp.time.millis}",
                )
            }
        }

    private fun touchAndGoBeforeFullStopCase(aircraftId: AircraftId): EvidenceMappedCase =
        EvidenceMappedCase(
            id = "icao9432-touch-and-go-before-full-stop",
            basis = EvidenceBasis.SourceMapped(
                sources = listOf(
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::0ece166e11d7728e"),
                ),
            ),
            samples = listOf(
                EvidenceSample(
                    name = "mission-outcome",
                    value = "touch-and-go-then-full-stop",
                    tier = SampleTier.Example,
                ),
            ),
        ) {
            val request = observation.reportsFrom<ReportEvent.Downwind>(aircraftId)
                .firstOrNull { report ->
                    report.events.filterIsInstance<ReportEvent.Downwind>()
                        .any { event -> event.circuitIntent == CircuitIntent.TOUCH_AND_GO }
                }
                ?: return@EvidenceMappedCase fail("pilot touch-and-go Downwind request was not observed", observation.diagnostic)
            val touchAndGo = observation.instructionsTo<ClearedTouchAndGo>(aircraftId).firstOrNull()
                ?: return@EvidenceMappedCase fail("ClearedTouchAndGo was not observed", observation.diagnostic)
            val land = observation.instructionsTo<ClearedToLand>(aircraftId).firstOrNull()
                ?: return@EvidenceMappedCase fail("ClearedToLand was not observed", observation.diagnostic)
            if (request.time.millis < touchAndGo.time.millis && touchAndGo.time.millis < land.time.millis) {
                pass("request=${request.time.millis}, touchAndGo=${touchAndGo.time.millis}, land=${land.time.millis}")
            } else {
                fail("touch-and-go request / clearance / full-stop landing order violated", observation.diagnostic)
            }
        }

    private fun goldenCompletesAndParksCase(aircraftId: AircraftId): EvidenceMappedCase =
        EvidenceMappedCase(
            id = "golden-lowg-touch-and-go-mission-completes",
            basis = EvidenceBasis.Golden("LOWG circuit training should finish parked after touch-and-go then full-stop"),
            samples = emptyList(),
        ) {
            val aircraft = observation.aircraft[aircraftId]
                ?: return@EvidenceMappedCase unexpectedGap("final aircraft observation missing")
            if (aircraft.missionComplete && aircraft.phase == PilotPhase.Parked) {
                pass("phase=${aircraft.phase}, missionComplete=${aircraft.missionComplete}")
            } else {
                fail("mission did not complete parked", observation.diagnostic)
            }
        }

    private fun essentialAerodromeInformationGapCase(): EvidenceMappedCase =
        EvidenceMappedCase(
            id = "icao9432-essential-aerodrome-information-model-gap",
            basis = EvidenceBasis.SourceMapped(
                sources = listOf(SourceUnitRef("icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc")),
            ),
            samples = emptyList(),
        ) {
            expectedGap(
                planId = "FN39-CONF-1",
                reason = "observation port does not yet expose required aerodrome-information applicability facts",
            )
        }

    private fun criticalPhaseRadioSilenceGapCase(): EvidenceMappedCase =
        EvidenceMappedCase(
            id = "icao9432-critical-phase-radio-silence-model-gap",
            basis = EvidenceBasis.SourceMapped(
                sources = listOf(SourceUnitRef("icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4")),
            ),
            samples = emptyList(),
        ) {
            expectedGap(
                planId = "FN39-CONF-1",
                reason = "observation port does not yet expose workload/critical-phase communication policy facts",
            )
        }
}
