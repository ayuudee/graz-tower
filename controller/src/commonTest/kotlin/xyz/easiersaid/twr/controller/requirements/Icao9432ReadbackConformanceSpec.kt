package xyz.easiersaid.twr.controller.requirements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import xyz.easiersaid.twr.controller.observe.ReadbackVerdict
import xyz.easiersaid.twr.controller.observe.classifyReadback
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtomDefect
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
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.RouteReadback
import xyz.easiersaid.twr.protocol.RouteSpec
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.RunwayInUseAdvisory
import xyz.easiersaid.twr.protocol.RunwayInUseReadback
import xyz.easiersaid.twr.protocol.SetPressure
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.SimpleElement
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.SpeedReadback
import xyz.easiersaid.twr.protocol.Squawk
import xyz.easiersaid.twr.protocol.SquawkReadback
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiRouteReadback
import xyz.easiersaid.twr.protocol.TransitionLevelIssuance
import xyz.easiersaid.twr.protocol.TransitionLevelReadback
import xyz.easiersaid.twr.protocol.requiredReadbackAtoms

class Icao9432ReadbackConformanceSpec {
    private val aircraft = AircraftId("OE-ABC")
    private val runway = RunwayId("16C")
    private val holdingPoint = PointId("A4")
    private val routeFix = FixId("GAMLI")

    @Test
    fun `ICAO 9432 2_8_3 runway operations require structural readback atoms`() {
        SourceBackedBehaviorCase(
            id = "icao9432-readback-runway-operations",
            family = "icao9432_readback_2_8_3",
            sourceUnits = setOf(
                SourceUnitRef("icao9432-extracted::readback_2_8_3_en::15940532b37f8528"),
            ),
        ) {
            assertEquals(setOf(LineUpReadback(runway)), requiredReadbackAtoms(LineUpAndWait(aircraft, runway)))
            assertEquals(setOf(ClearedToLandReadback(runway)), requiredReadbackAtoms(ClearedToLand(aircraft, runway)))
            assertEquals(setOf(ClearedForTakeoffReadback(runway)), requiredReadbackAtoms(ClearedForTakeoff(aircraft, runway)))
            assertEquals(setOf(HoldShortReadback(runway)), requiredReadbackAtoms(HoldShortOf(aircraft, runway)))
            assertEquals(setOf(CrossRunwayReadback(runway)), requiredReadbackAtoms(CrossRunway(aircraft, runway)))
            assertEquals(setOf(BacktrackReadback(runway)), requiredReadbackAtoms(BacktrackRunway(aircraft, runway)))
        }.assertSatisfied()
    }

    @Test
    fun `ICAO 9432 2_8_3 operational parameters require structural readback atoms`() {
        val level = Level.FlightLevel.unsafe(90)
        val heading = Heading.unsafe(160)
        val speed = Speed.InKnots(xyz.easiersaid.twr.protocol.Knots.unsafe(120))
        val pressure = PressureSetting.QnhHpa.unsafe(1016)
        val squawk = Squawk.unsafe(7000)

        SourceBackedBehaviorCase(
            id = "icao9432-readback-operational-parameters",
            family = "icao9432_readback_2_8_3",
            sourceUnits = setOf(
                SourceUnitRef("icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60"),
            ),
        ) {
            assertEquals(setOf(RunwayInUseReadback(runway)), requiredReadbackAtoms(RunwayInUseAdvisory(aircraft, runway)))
            assertEquals(setOf(PressureSettingReadback(pressure)), requiredReadbackAtoms(SetPressure(aircraft, pressure)))
            assertEquals(setOf(SquawkReadback(squawk)), requiredReadbackAtoms(SetSquawk(aircraft, squawk)))
            assertEquals(setOf(LevelReadback(level)), requiredReadbackAtoms(MaintainLevel(aircraft, level)))
            assertEquals(setOf(HeadingReadback(heading)), requiredReadbackAtoms(FlyHeading(aircraft, heading)))
            assertEquals(setOf(SpeedReadback(speed)), requiredReadbackAtoms(MaintainSpeed(aircraft, speed)))
            assertEquals(
                setOf(TransitionLevelReadback(level)),
                requiredReadbackAtoms(TransitionLevelIssuance(aircraft, level)),
            )
        }.assertSatisfied()
    }

    @Test
    fun `ICAO 9432 2_8_3 route and taxi clearances require readback atoms`() {
        SourceBackedBehaviorCase(
            id = "icao9432-readback-route-and-taxi",
            family = "icao9432_readback_2_8_3",
            sourceUnits = setOf(
                SourceUnitRef("icao9432-extracted::readback_2_8_3_en::58594a8ee6243296"),
                SourceUnitRef("icao9432-extracted::readback_2_8_3_en::4b6ece953649da07"),
            ),
        ) {
            assertEquals(
                setOf(RouteReadback(RouteSpec.Direct(routeFix))),
                requiredReadbackAtoms(ClearedTo(aircraft, clearanceLimit = routeFix)),
            )
            assertEquals(
                setOf(
                    xyz.easiersaid.twr.protocol.RunwayReadback(runway),
                    TaxiRouteReadback(holdingPoint, via = listOf(PointId("A"))),
                ),
                requiredReadbackAtoms(
                    TaxiToHoldingPoint(
                        target = aircraft,
                        destination = holdingPoint,
                        runway = runway,
                        via = listOf(PointId("A")),
                    ),
                ),
            )
        }.assertSatisfied()
    }

    @Test
    fun `ICAO 9432 2_8_3_7 hearback classifies correct readback as acknowledged`() {
        SourceBackedBehaviorCase(
            id = "icao9432-hearback-correct-readback-acknowledged",
            family = "icao9432_readback_continuation_2_8_3_7_to_2_8_3_10",
            sourceUnits = setOf(
                SourceUnitRef(
                    "icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::17e1dfdf4ce57253",
                ),
            ),
        ) {
            val instruction = ClearedToLand(aircraft, runway)
            val readback = Readback(listOf(SimpleElement(ClearedToLandReadback(runway))))

            assertEquals(ReadbackVerdict.Correct, classifyReadback(instruction, readback))
        }.assertSatisfied()
    }

    @Test
    fun `ICAO 9432 2_8_3_8 hearback exposes discrepancy for correction`() {
        SourceBackedBehaviorCase(
            id = "icao9432-hearback-discrepancy-correction",
            family = "icao9432_readback_continuation_2_8_3_7_to_2_8_3_10",
            sourceUnits = setOf(
                SourceUnitRef(
                    "icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::ace4ab7ff5d53a66",
                ),
            ),
        ) {
            val instruction = ClearedToLand(aircraft, runway)
            val wrongRunway = RunwayId("34C")
            val readback = Readback(listOf(SimpleElement(ClearedToLandReadback(wrongRunway))))
            val verdict = classifyReadback(instruction, readback) as? ReadbackVerdict.Incorrect
                ?: fail("Expected incorrect readback verdict for wrong runway readback")

            assertEquals(listOf(AtomDefect.WrongAtom(ClearedToLandReadback(runway))), verdict.defects.all)
        }.assertSatisfied()
    }
}
