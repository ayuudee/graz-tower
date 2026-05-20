package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.BacktrackReadback
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedForTakeoffReadback
import xyz.easiersaid.twr.protocol.ClearedTo
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedToLandReadback
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.CrossRunwayReadback
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.HeadingReadback
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.HoldShortReadback
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.LevelReadback
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.LineUpReadback
import xyz.easiersaid.twr.protocol.MaintainLevel
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.PressureSettingReadback
import xyz.easiersaid.twr.protocol.RouteReadback
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.RunwayInUseAdvisory
import xyz.easiersaid.twr.protocol.RunwayInUseReadback
import xyz.easiersaid.twr.protocol.RunwayReadback
import xyz.easiersaid.twr.protocol.SetPressure
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.SpeedReadback
import xyz.easiersaid.twr.protocol.Squawk
import xyz.easiersaid.twr.protocol.SquawkReadback
import xyz.easiersaid.twr.protocol.TaxiRouteReadback
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TransitionLevelIssuance
import xyz.easiersaid.twr.protocol.TransitionLevelReadback

class Icao9432Chunk01ReadbackEvidenceTest {
    private val aircraft = AircraftId("OE-ABC")
    private val runway = RunwayId("16C")
    private val holdingPoint = PointId("LOWG-HP-C")
    private val routeFix = FixId("GAMLI")

    @Test
    fun `chunk 01 source-mapped readback structural evidence is exact`() {
        val heading = Heading.unsafe(160)
        val level = Level.FlightLevel.unsafe(90)
        val speed = Speed.InKnots(xyz.easiersaid.twr.protocol.Knots.unsafe(120))
        val pressure = PressureSetting.QnhHpa.unsafe(1016)
        val squawk = Squawk.unsafe(7000)

        val report = protocolEvidence("icao9432-chunk01-readback-structural") {
            structuralReadback("runway line-up readback", LineUpAndWait(aircraft, runway)) {
                cites(ICAO9432.Readback.RunwayOperationsRequiredReadback)
                requires(LineUpReadback(runway))
            }
            structuralReadback("runway landing readback", ClearedToLand(aircraft, runway)) {
                cites(ICAO9432.Readback.RunwayOperationsRequiredReadback)
                requires(ClearedToLandReadback(runway))
            }
            structuralReadback("runway takeoff readback", ClearedForTakeoff(aircraft, runway)) {
                cites(ICAO9432.Readback.RunwayOperationsRequiredReadback)
                requires(ClearedForTakeoffReadback(runway))
            }
            structuralReadback("runway hold-short readback", HoldShortOf(aircraft, runway)) {
                cites(ICAO9432.Readback.RunwayOperationsRequiredReadback)
                requires(HoldShortReadback(runway))
            }
            structuralReadback("runway crossing readback", CrossRunway(aircraft, runway)) {
                cites(ICAO9432.Readback.RunwayOperationsRequiredReadback)
                requires(CrossRunwayReadback(runway))
            }
            structuralReadback("runway backtrack readback", BacktrackRunway(aircraft, runway)) {
                cites(ICAO9432.Readback.RunwayOperationsRequiredReadback)
                requires(BacktrackReadback(runway))
            }
            structuralReadback("runway-in-use readback", RunwayInUseAdvisory(aircraft, runway)) {
                cites(ICAO9432.Readback.OperationalParametersRequiredReadback)
                requires(RunwayInUseReadback(runway))
            }
            structuralReadback("pressure-setting readback", SetPressure(aircraft, pressure)) {
                cites(ICAO9432.Readback.OperationalParametersRequiredReadback)
                requires(PressureSettingReadback(pressure))
            }
            structuralReadback("squawk readback", SetSquawk(aircraft, squawk)) {
                cites(ICAO9432.Readback.OperationalParametersRequiredReadback)
                requires(SquawkReadback(squawk))
            }
            structuralReadback("level readback", MaintainLevel(aircraft, level)) {
                cites(ICAO9432.Readback.OperationalParametersRequiredReadback)
                requires(LevelReadback(level))
            }
            structuralReadback("heading readback", FlyHeading(aircraft, heading)) {
                cites(ICAO9432.Readback.OperationalParametersRequiredReadback)
                requires(HeadingReadback(heading))
            }
            structuralReadback("speed readback", MaintainSpeed(aircraft, speed)) {
                cites(ICAO9432.Readback.OperationalParametersRequiredReadback)
                requires(SpeedReadback(speed))
            }
            structuralReadback("transition-level readback", TransitionLevelIssuance(aircraft, level)) {
                cites(ICAO9432.Readback.OperationalParametersRequiredReadback)
                requires(TransitionLevelReadback(level))
            }
            structuralReadback("route-clearance readback", ClearedTo(aircraft, clearanceLimit = routeFix)) {
                cites(ICAO9432.Readback.AtcRouteClearancesRequiredReadback)
                requires(RouteReadback(RouteSpec.Direct(routeFix)))
            }
            structuralReadback(
                "taxi clearance readback",
                TaxiToHoldingPoint(aircraft, holdingPoint, runway, via = listOf(PointId("A"))),
            ) {
                cites(ICAO9432.Readback.OtherClearancesAcknowledged)
                requires(
                    RunwayReadback(runway),
                    TaxiRouteReadback(holdingPoint, via = listOf(PointId("A"))),
                )
            }
        }

        report.assertNoFailures()
        assertEquals(15, report.results.size)
        assertEquals(
            setOf(
                ICAO9432.Readback.RunwayOperationsRequiredReadback,
                ICAO9432.Readback.OperationalParametersRequiredReadback,
                ICAO9432.Readback.AtcRouteClearancesRequiredReadback,
                ICAO9432.Readback.OtherClearancesAcknowledged,
            ),
            report.results.flatMap { result -> result.sources }.toSet(),
        )
    }
}
