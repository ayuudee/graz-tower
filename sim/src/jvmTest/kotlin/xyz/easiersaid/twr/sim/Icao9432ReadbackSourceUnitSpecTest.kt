package xyz.easiersaid.twr.sim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedForTakeoffReadback
import xyz.easiersaid.twr.protocol.FlyHeading
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.HeadingReadback
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.LevelReadback
import xyz.easiersaid.twr.protocol.MaintainLevel
import xyz.easiersaid.twr.protocol.MaintainSpeed
import xyz.easiersaid.twr.protocol.PressureSetting
import xyz.easiersaid.twr.protocol.PressureSettingReadback
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SetPressure
import xyz.easiersaid.twr.protocol.SetSquawk
import xyz.easiersaid.twr.protocol.Speed
import xyz.easiersaid.twr.protocol.SpeedReadback
import xyz.easiersaid.twr.protocol.Squawk
import xyz.easiersaid.twr.protocol.SquawkReadback
import xyz.easiersaid.twr.protocol.requiredReadbackAtoms

class Icao9432ReadbackSourceUnitSpecTest {
    @Test
    fun `ICAO 9432 readback source units survive generated protocol examples`() {
        sourceUnitSpec("icao9432-readback-generated-protocol-spec") {
            title("Readback obligations are stable across generated protocol parameters")
            sourceUnits(
                listOf(
                    SourceUnitRef("icao9432-extracted::readback_2_8_3_en::15940532b37f8528"),
                    SourceUnitRef("icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60"),
                ),
            )
            domain("instruction-family", setOf("runway", "heading", "level", "speed", "pressure", "squawk"))

            witness("runway takeoff clearance readback") {
                hit("runway")
                val aircraft = AircraftId("OE-ABC")
                val runway = RunwayId("16C")
                assertEquals(
                    setOf(ClearedForTakeoffReadback(runway)),
                    requiredReadbackAtoms(ClearedForTakeoff(aircraft, runway)),
                )
                requireHits("runway")
            }

            fuzz(
                name = "generated operational-parameter readback",
                samples = 24,
                seed = 9432L,
                parameters = { random, _ ->
                    val families = listOf("heading", "level", "speed", "pressure", "squawk")
                    mapOf(
                        "family" to families[random.nextInt(families.size)],
                        "heading" to random.nextInt(1, 361).toString(),
                        "level" to random.nextInt(50, 181).toString(),
                        "speed" to random.nextInt(80, 241).toString(),
                        "pressure" to random.nextInt(980, 1031).toString(),
                        "squawk" to randomSquawk(random.nextInt()),
                    )
                },
            ) {
                assertGeneratedReadbackCase()
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    private fun SpecProbeContext.assertGeneratedReadbackCase() {
        val aircraft = AircraftId("OE-ABC")
        val family = parameter("family")
        when (family) {
            "heading" -> {
                hit("heading")
                val heading = Heading.unsafe(intParameter("heading"))
                assertEquals(setOf(HeadingReadback(heading)), requiredReadbackAtoms(FlyHeading(aircraft, heading)))
            }
            "level" -> {
                hit("level")
                val level = Level.FlightLevel.unsafe(intParameter("level"))
                assertEquals(setOf(LevelReadback(level)), requiredReadbackAtoms(MaintainLevel(aircraft, level)))
            }
            "speed" -> {
                hit("speed")
                val speed = Speed.InKnots(xyz.easiersaid.twr.protocol.Knots.unsafe(intParameter("speed")))
                assertEquals(setOf(SpeedReadback(speed)), requiredReadbackAtoms(MaintainSpeed(aircraft, speed)))
            }
            "pressure" -> {
                hit("pressure")
                val pressure = PressureSetting.QnhHpa.unsafe(intParameter("pressure"))
                assertEquals(setOf(PressureSettingReadback(pressure)), requiredReadbackAtoms(SetPressure(aircraft, pressure)))
            }
            "squawk" -> {
                hit("squawk")
                val squawk = Squawk.unsafe(intParameter("squawk"))
                assertEquals(setOf(SquawkReadback(squawk)), requiredReadbackAtoms(SetSquawk(aircraft, squawk)))
            }
            else -> fail("Unhandled generated readback family '$family'")
        }
        requireHits(family)
    }

    private fun randomSquawk(seed: Int): String {
        val positive = seed and Int.MAX_VALUE
        val digits = listOf(
            positive % 8,
            (positive / 8) % 8,
            (positive / 64) % 8,
            (positive / 512) % 8,
        )
        return digits.joinToString(separator = "")
    }
}
