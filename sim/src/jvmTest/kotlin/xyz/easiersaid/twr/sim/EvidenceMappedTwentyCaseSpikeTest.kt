package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedForTakeoffReadback
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedToLandReadback
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ClearedTouchAndGoReadback
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.CrossRunwayReadback
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.HeadingReadback
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.HoldShortReadback
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.LevelReadback
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.LineUpReadback
import xyz.easiersaid.twr.protocol.MaintainLevel
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.PressureSettingReadback
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.RunwayReadback
import xyz.easiersaid.twr.protocol.SetPressure
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.SpeedReadback
import xyz.easiersaid.twr.protocol.Squawk
import xyz.easiersaid.twr.protocol.SquawkReadback
import xyz.easiersaid.twr.protocol.TaxiRouteReadback
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint

class EvidenceMappedTwentyCaseSpikeTest {
    @Test
    fun `twenty evidence mapped cases stay readable at the call site`() {
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")
        val holdingPoint = PointId("LOWG-HP-C")
        val heading = Heading.unsafe(180)
        val level = Level.FlightLevel.unsafe(90)
        val speed = Speed.InKnots(Knots.unsafe(110))
        val pressure = PressureSetting.QnhHpa.unsafe(1013)
        val squawk = Squawk.unsafe(7000)

        val protocolReport = evidenceScenario("twenty-case-protocol-readback") {
            observe { SyntheticObservationPort.protocolScenario("twenty-case-protocol-readback") }

            readbackCase("takeoff clearance", ClearedForTakeoff(aircraft, runway)) {
                requires(ClearedForTakeoffReadback(runway))
            }
            readbackCase("landing clearance", ClearedToLand(aircraft, runway)) {
                requires(ClearedToLandReadback(runway))
            }
            readbackCase("touch-and-go clearance", ClearedTouchAndGo(aircraft, runway)) {
                requires(ClearedTouchAndGoReadback(runway))
            }
            readbackCase("line up and wait", LineUpAndWait(aircraft, runway)) {
                requires(LineUpReadback(runway))
            }
            readbackCase("hold short", HoldShortOf(aircraft, runway)) {
                requires(HoldShortReadback(runway))
            }
            readbackCase("cross runway", CrossRunway(aircraft, runway)) {
                requires(CrossRunwayReadback(runway))
            }
            readbackCase("fly heading", FlyHeading(aircraft, heading)) {
                sample("heading", heading, SampleTier.Representative)
                requires(HeadingReadback(heading))
            }
            readbackCase("maintain level", MaintainLevel(aircraft, level)) {
                sample("level", level, SampleTier.Representative)
                requires(LevelReadback(level))
            }
            readbackCase("maintain speed", MaintainSpeed(aircraft, speed)) {
                sample("speed", speed, SampleTier.Representative)
                requires(SpeedReadback(speed))
            }
            readbackCase("set pressure", SetPressure(aircraft, pressure)) {
                sample("pressure", pressure, SampleTier.Representative)
                requires(PressureSettingReadback(pressure))
            }
            readbackCase("set squawk", SetSquawk(aircraft, squawk)) {
                sample("squawk", squawk, SampleTier.Representative)
                requires(SquawkReadback(squawk))
            }
            readbackCase("taxi to holding point", TaxiToHoldingPoint(aircraft, holdingPoint, runway)) {
                sample("runway", runway)
                requires(RunwayReadback(runway), TaxiRouteReadback(holdingPoint))
            }
        }

        val lowgReport = evidenceScenario("twenty-case-lowg-circuit") {
            observe {
                LowgObservationPort.runCircuitTraining(
                    scenarioId = "twenty-case-lowg-circuit",
                    outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }

            sourceCase(
                id = "taxi clearance before runway use",
                TAXI_SOURCE_A,
                TAXI_SOURCE_B,
            ) {
                sample("runway", runway)
                expect {
                    instruction<TaxiToHoldingPoint>(aircraft) before
                        report<ReportEvent.Ready>(aircraft) before
                        instruction<LineUpAndWait>(aircraft)
                }
            }

            sourceCase(
                id = "takeoff clearance after line up",
                READBACK_SOURCE_A,
                READBACK_SOURCE_B,
            ) {
                expect {
                    instruction<LineUpAndWait>(aircraft) before
                        instruction<ClearedForTakeoff>(aircraft)
                }
            }

            invariantCase(
                id = "touch-and-go before full-stop landing",
                name = "touch-and-go clearance precedes full-stop landing clearance",
            ) {
                expect {
                    instruction<ClearedTouchAndGo>(aircraft) before
                        instruction<ClearedToLand>(aircraft)
                }
            }

            invariantCase(
                id = "landing clearance before runway vacated",
                name = "landing clearance precedes runway vacated",
            ) {
                expect {
                    instruction<ClearedToLand>(aircraft) before
                        report<ReportEvent.RunwayVacated>(aircraft)
                }
            }

            goldenCase(
                id = "mission completes parked",
                reason = "LOWG touch-and-go plus full-stop should finish parked",
            ) {
                expect { aircraft(aircraft).isParkedAndComplete() }
            }

            invariantCase(
                id = "runway-use sequence stays ordered",
                name = "taxi-ready-lineup-takeoff ordering",
            ) {
                expect {
                    instruction<TaxiToHoldingPoint>(aircraft) before
                        report<ReportEvent.Ready>(aircraft) before
                        instruction<LineUpAndWait>(aircraft) before
                        instruction<ClearedForTakeoff>(aircraft)
                }
            }

            sourceCase(
                id = "essential aerodrome information remains explicit gap",
                "icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc",
            ) {
                expect {
                    expectedGap(
                        planId = "FN40-EVID-1",
                        reason = "SimObservation does not yet expose aerodrome-information receipt facts",
                    )
                }
            }

            sourceCase(
                id = "critical phase radio silence remains explicit gap",
                "icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4",
            ) {
                expect {
                    expectedGap(
                        planId = "FN40-EVID-1",
                        reason = "SimObservation does not yet expose critical-phase workload facts",
                    )
                }
            }
        }

        protocolReport.assertNoUnexpectedFailures()
        lowgReport.assertNoUnexpectedFailures()
        assertEquals(20, protocolReport.results.size + lowgReport.results.size)
        assertEquals(2, lowgReport.results.count { result -> result.outcome is EvidenceOutcome.ExpectedGap })
    }

    private fun EvidenceScenarioBuilder.readbackCase(
        title: String,
        instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
        build: ReadbackCaseBuilder.() -> Unit,
    ) {
        sourceCase("readback $title", READBACK_SOURCE_A, READBACK_SOURCE_B) {
            val builder = ReadbackCaseBuilder(this, instruction)
            builder.build()
            expect { readback(instruction).requires(*builder.expectedAtoms()) }
        }
    }

    private class ReadbackCaseBuilder(
        private val case: EvidenceCaseBuilder,
        private val instruction: xyz.easiersaid.twr.protocol.AtcInstruction,
    ) {
        private val atoms: MutableList<xyz.easiersaid.twr.protocol.AtomicReadback> = mutableListOf()

        fun sample(name: String, value: Any, tier: SampleTier = SampleTier.Example) {
            case.sample(name, value, tier)
        }

        fun requires(vararg expected: xyz.easiersaid.twr.protocol.AtomicReadback) {
            atoms += expected
        }

        fun expectedAtoms(): Array<xyz.easiersaid.twr.protocol.AtomicReadback> {
            check(atoms.isNotEmpty()) { "readback case ${instruction::class.simpleName} declared no expected atoms" }
            return atoms.toTypedArray()
        }
    }

    private companion object {
        const val READBACK_SOURCE_A = "icao9432-extracted::readback_2_8_3_en::15940532b37f8528"
        const val READBACK_SOURCE_B = "icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60"
        const val TAXI_SOURCE_A = "icao9432-extracted::taxi_4_4_en::417f64324f7495bf"
        const val TAXI_SOURCE_B = "icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e"
    }
}
