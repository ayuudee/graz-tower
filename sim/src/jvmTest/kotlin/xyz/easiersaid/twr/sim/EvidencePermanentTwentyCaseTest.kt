package xyz.easiersaid.twr.sim

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtomicReadback
import xyz.easiersaid.twr.protocol.AtcInstruction
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

class EvidencePermanentTwentyCaseTest {
    @Test
    fun `twenty permanent evidence cases stay terse and audited`() {
        val aircraft = AircraftId("OE-ABC")
        val runway = RunwayId("16C")
        val holdingPoint = PointId("LOWG-HP-C")
        val heading = Heading.unsafe(180)
        val level = Level.FlightLevel.unsafe(90)
        val speed = Speed.InKnots(Knots.unsafe(110))
        val pressure = PressureSetting.QnhHpa.unsafe(1013)
        val squawk = Squawk.unsafe(7000)

        val protocolReport = protocolEvidence("permanent-protocol-readbacks") {
            readbackCase("takeoff clearance", ClearedForTakeoff(aircraft, runway), ClearedForTakeoffReadback(runway))
            readbackCase("landing clearance", ClearedToLand(aircraft, runway), ClearedToLandReadback(runway))
            readbackCase("touch-and-go clearance", ClearedTouchAndGo(aircraft, runway), ClearedTouchAndGoReadback(runway))
            readbackCase("line up and wait", LineUpAndWait(aircraft, runway), LineUpReadback(runway))
            readbackCase("hold short", HoldShortOf(aircraft, runway), HoldShortReadback(runway))
            readbackCase("cross runway", CrossRunway(aircraft, runway), CrossRunwayReadback(runway))
            readbackCase("fly heading", FlyHeading(aircraft, heading), HeadingReadback(heading))
            readbackCase("maintain level", MaintainLevel(aircraft, level), LevelReadback(level))
            readbackCase("maintain speed", MaintainSpeed(aircraft, speed), SpeedReadback(speed))
            readbackCase("set pressure", SetPressure(aircraft, pressure), PressureSettingReadback(pressure))
            readbackCase("set squawk", SetSquawk(aircraft, squawk), SquawkReadback(squawk))
            readbackCase(
                "taxi to holding point",
                TaxiToHoldingPoint(aircraft, holdingPoint, runway),
                RunwayReadback(runway),
                TaxiRouteReadback(holdingPoint),
            )
        }

        val lowgReport = simEvidence("permanent-lowg-circuit") {
            observe {
                EvidenceFactAdapters.lowgCircuitTraining(
                    scenarioId = "permanent-lowg-circuit",
                    outcomes = listOf(CircuitOutcome.TouchAndGo, CircuitOutcome.FullStop),
                    untilMinutes = 45,
                )
            }

            source("taxi clearance before runway use") {
                cites(ICAO9432.Taxi.HoldingPointLimit)
                sample("runway", runway)
                expect {
                    (instructions<TaxiToHoldingPoint>(aircraft).first() before
                        reports<ReportEvent.Ready>(aircraft).first() before
                        instructions<LineUpAndWait>(aircraft).first()).toOutcome()
                }
            }

            source("takeoff clearance after line up") {
                cites(ICAO9432.Readback.RequiredItems)
                expect {
                    (instructions<LineUpAndWait>(aircraft).first() before
                        instructions<ClearedForTakeoff>(aircraft).first()).toOutcome()
                }
            }

            source("touch-and-go before full-stop landing") {
                cites(ICAO9432.FinalApproachLanding.TouchAndGo)
                expect {
                    (instructions<ClearedTouchAndGo>(aircraft).first() before
                        instructions<ClearedToLand>(aircraft).first()).toOutcome()
                }
            }

            source("landing clearance before runway vacated") {
                cites(ICAO9432.FinalApproachLanding.TouchAndGo)
                expect {
                    (instructions<ClearedToLand>(aircraft).first() before
                        reports<ReportEvent.RunwayVacated>(aircraft).first()).toOutcome()
                }
            }

            golden("mission completes parked", "LOWG touch-and-go plus full-stop should finish parked") {
                expect { this.aircraft(aircraft).isParkedAndComplete() }
            }

            invariant("runway-use sequence stays ordered") {
                expect {
                    (instructions<TaxiToHoldingPoint>(aircraft).first() before
                        reports<ReportEvent.Ready>(aircraft).first() before
                        instructions<LineUpAndWait>(aircraft).first() before
                        instructions<ClearedForTakeoff>(aircraft).first()).toOutcome()
                }
            }

            source("essential aerodrome information remains explicit gap") {
                cites(ICAO9432.GapSources.EssentialAerodromeInformationTiming)
                expect {
                    expectedGap(
                        EvidenceGaps.EssentialAerodromeInformationReceiptProjection,
                        "Evidence facts do not yet expose aerodrome-information receipt.",
                    )
                }
            }

            source("critical phase radio silence is covered-red under routine classification") {
                cites(ICAO9432.CriticalPhase.CriticalPhaseRadioSilence)
                expect { criticalPhase(aircraft).routineControllerTransmissions().none() }
            }
        }

        protocolReport.assertNoFailures()
        assertEquals(20, protocolReport.results.size + lowgReport.results.size)
        assertEquals(1, lowgReport.results.count { result -> result.outcome is EvidenceAuditOutcome.ExpectedGap })
        assertEquals(1, lowgReport.results.count { result -> result.outcome is EvidenceAuditOutcome.Fail })
        assertTrue(lowgReport.results.filter { result -> result.sources.isNotEmpty() }
            .all { result ->
                result.activationFactIds.isNotEmpty() ||
                    result.outcome is EvidenceAuditOutcome.ExpectedGap ||
                    result.outcome is EvidenceAuditOutcome.Vacuous
            })

        val outputDir = Files.createTempDirectory("permanent-twenty-case-report")
        val reportFiles = EvidenceReportWriter.write(lowgReport, outputDir)
        val cases = Json.parseToJsonElement(Files.readString(reportFiles.json))
            .jsonObject.getValue("cases").jsonArray.map { element -> element.jsonObject }
        val gapCases = cases.filter { case -> case.getValue("outcome").jsonPrimitive.content == "expected_gap" }
        val failCases = cases.filter { case -> case.getValue("outcome").jsonPrimitive.content == "fail" }
        assertEquals(1, gapCases.size)
        assertEquals(1, failCases.size)
        assertTrue(gapCases.all { case -> case.getValue("typedGap").jsonObject.getValue("tracking").jsonPrimitive.content.startsWith("FN43-GAP-") })
        assertTrue(gapCases.all { case -> case.getValue("typedGap").jsonObject.getValue("affectedSourceRefs").jsonArray.isNotEmpty() })
        assertTrue(failCases.all { case -> case.getValue("sourceRefs").jsonArray.isNotEmpty() })
    }

    private fun ProtocolEvidenceBuilder.readbackCase(
        title: String,
        instruction: AtcInstruction,
        vararg expected: AtomicReadback,
    ) {
        structuralReadback("readback $title", instruction) {
            cites(ICAO9432.Readback.RequiredItems)
            requires(*expected)
        }
    }
}
