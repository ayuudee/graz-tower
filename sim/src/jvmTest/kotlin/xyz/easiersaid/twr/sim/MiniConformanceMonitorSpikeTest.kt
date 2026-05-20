package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.HeadingReadback
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.requiredReadbackAtoms

class MiniConformanceMonitorSpikeTest {
    @Test
    fun `mini monitor spike can run source-backed monitors over synthetic and sim traces`() {
        val aircraftId = AircraftId("OE-ABC")
        val readbackTrace = SyntheticObservationPort.protocolInstruction(
            scenarioId = "mini-monitor-synthetic-readback",
            aircraftId = aircraftId,
            instruction = FlyHeading(aircraftId, Heading.unsafe(180)),
        ).toMiniConformanceTrace(
            capabilities = setOf(MiniCapability.ProtocolSynthetic, MiniCapability.ReadbackExchange),
            origin = MiniFactOrigin.SyntheticProtocolPort,
        )
        val lowgTrace = LowgObservationPort.runCircuitTraining(
            scenarioId = "mini-monitor-lowg-touch-and-go",
            outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
            untilMinutes = 45,
        ).toMiniConformanceTrace(
            capabilities = setOf(
                MiniCapability.TowerCircuit,
                MiniCapability.TaxiClearance,
                MiniCapability.LandingIntent,
                MiniCapability.AerodromeInformation,
                MiniCapability.CriticalPhase,
                MiniCapability.GoldenOutcome,
            ),
            origin = MiniFactOrigin.SimObservationPort,
        )

        val readbackReport = runMiniMonitors(readbackTrace, listOf(readbackMonitor(aircraftId, Heading.unsafe(180))))
        val lowgReport = runMiniMonitors(
            trace = lowgTrace,
            contracts = listOf(
                taxiMonitor(aircraftId),
                touchAndGoMonitor(aircraftId),
                goldenCompletionMonitor(aircraftId),
                essentialAerodromeInformationMonitor(),
                criticalPhaseRadioSilenceMonitor(),
            ),
        )

        readbackReport.assertNoUnexpectedFailures()
        lowgReport.assertNoUnexpectedFailures()
        assertEquals(1, readbackReport.results.count { result -> result.outcome is MiniMonitorOutcome.Pass })
        assertEquals(3, lowgReport.results.count { result -> result.outcome is MiniMonitorOutcome.Pass })
        assertEquals(2, lowgReport.results.count { result -> result.outcome is MiniMonitorOutcome.ExpectedGap })
    }

    private fun readbackMonitor(aircraftId: AircraftId, heading: Heading): MiniSourceContract =
        MiniSourceContract(
            id = "monitor-icao9432-readback-heading",
            basis = EvidenceBasis.SourceMapped(
                sources = listOf(
                    SourceUnitRef("icao9432-extracted::readback_2_8_3_en::15940532b37f8528"),
                    SourceUnitRef("icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60"),
                ),
            ),
            requiredCapabilities = setOf(MiniCapability.ProtocolSynthetic, MiniCapability.ReadbackExchange),
        ) {
            val instruction = instructionsTo<FlyHeading>(aircraftId).singleOrNull()
                ?: return@MiniSourceContract unexpectedGap("expected exactly one FlyHeading instruction fact")
            val atoms = requiredReadbackAtoms(instruction.instruction)
            if (atoms == setOf(HeadingReadback(heading))) {
                pass(activationCount = 1, instruction)
            } else {
                fail("unexpected readback atoms: $atoms", instruction)
            }
        }

    private fun taxiMonitor(aircraftId: AircraftId): MiniSourceContract =
        MiniSourceContract(
            id = "monitor-icao9432-taxi-before-runway-use",
            basis = EvidenceBasis.SourceMapped(
                sources = listOf(
                    SourceUnitRef("icao9432-extracted::taxi_4_4_en::417f64324f7495bf"),
                    SourceUnitRef("icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e"),
                ),
            ),
            requiredCapabilities = setOf(MiniCapability.TowerCircuit, MiniCapability.TaxiClearance),
        ) {
            val taxi = instructionsTo<TaxiToHoldingPoint>(aircraftId).firstOrNull()
                ?: return@MiniSourceContract unexpectedGap("taxi instruction was not projected")
            val ready = reportsFrom<ReportEvent.Ready>(aircraftId).firstOrNull()
                ?: return@MiniSourceContract fail("Ready report missing", taxi)
            val lineUp = instructionsTo<LineUpAndWait>(aircraftId).firstOrNull()
                ?: return@MiniSourceContract fail("LineUpAndWait missing after taxi", taxi, ready)
            if (taxi.time.millis < ready.time.millis && ready.time.millis < lineUp.time.millis) {
                pass(activationCount = 1, taxi, ready, lineUp)
            } else {
                fail("taxi/ready/line-up order violated", taxi, ready, lineUp)
            }
        }

    private fun touchAndGoMonitor(aircraftId: AircraftId): MiniSourceContract =
        MiniSourceContract(
            id = "monitor-icao9432-touch-and-go-before-full-stop",
            basis = EvidenceBasis.SourceMapped(
                sources = listOf(
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::0ece166e11d7728e"),
                    SourceUnitRef("icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4"),
                ),
            ),
            requiredCapabilities = setOf(MiniCapability.TowerCircuit, MiniCapability.LandingIntent),
        ) {
            val touchAndGo = instructionsTo<ClearedTouchAndGo>(aircraftId).firstOrNull()
                ?: return@MiniSourceContract fail("ClearedTouchAndGo missing")
            val land = instructionsTo<ClearedToLand>(aircraftId).firstOrNull()
                ?: return@MiniSourceContract fail("ClearedToLand missing", touchAndGo)
            if (touchAndGo.time.millis < land.time.millis) {
                pass(activationCount = 1, touchAndGo, land)
            } else {
                fail("touch-and-go clearance did not precede landing clearance", touchAndGo, land)
            }
        }

    private fun goldenCompletionMonitor(aircraftId: AircraftId): MiniSourceContract =
        MiniSourceContract(
            id = "monitor-golden-lowg-completes-parked",
            basis = EvidenceBasis.Golden("LOWG touch-and-go plus full-stop completes parked"),
            requiredCapabilities = setOf(MiniCapability.GoldenOutcome),
        ) {
            val aircraft = finalAircraft(aircraftId)
                ?: return@MiniSourceContract unexpectedGap("final aircraft fact missing")
            if (aircraft.phase == PilotPhase.Parked && aircraft.missionComplete) {
                pass(activationCount = 1, aircraft)
            } else {
                fail("aircraft did not complete parked", aircraft)
            }
        }

    private fun essentialAerodromeInformationMonitor(): MiniSourceContract =
        MiniSourceContract(
            id = "monitor-icao9432-essential-aerodrome-information-gap",
            basis = EvidenceBasis.SourceMapped(
                sources = listOf(SourceUnitRef("icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc")),
            ),
            requiredCapabilities = setOf(MiniCapability.AerodromeInformation),
            expectedGapPlanId = "FN39-CONF-1",
        ) {
            expectedGap("trace does not yet project aerodrome-information applicability and receipt facts")
        }

    private fun criticalPhaseRadioSilenceMonitor(): MiniSourceContract =
        MiniSourceContract(
            id = "monitor-icao9432-critical-phase-radio-silence-gap",
            basis = EvidenceBasis.SourceMapped(
                sources = listOf(SourceUnitRef("icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4")),
            ),
            requiredCapabilities = setOf(MiniCapability.CriticalPhase),
            expectedGapPlanId = "FN39-CONF-1",
        ) {
            expectedGap("trace does not yet project critical-phase workload or safety-necessity facts")
        }
}
