package xyz.easiersaid.twr.protocol

import arrow.core.Either
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SmartConstructorTest {

    // ---- Frequency ----

    @Test
    fun validFrequencyReturnsRight() {
        val result = Frequency("121.500")
        assertIs<Either.Right<Frequency>>(result)
        assertEquals("121.500", result.value.mhz)
    }

    @Test
    fun frequencyBelowVhfRangeReturnsLeft() {
        assertIs<Either.Left<String>>(Frequency("117.900"))
    }

    @Test
    fun frequencyAboveVhfRangeReturnsLeft() {
        assertIs<Either.Left<String>>(Frequency("137.001"))
    }

    @Test
    fun frequencyAtLowerBoundReturnsRight() {
        assertIs<Either.Right<Frequency>>(Frequency("118.000"))
    }

    @Test
    fun frequencyAtUpperBoundReturnsRight() {
        assertIs<Either.Right<Frequency>>(Frequency("136.975"))
    }

    @Test
    fun blankFrequencyReturnsLeft() {
        assertIs<Either.Left<String>>(Frequency(""))
    }

    @Test
    fun nonNumericFrequencyReturnsLeft() {
        assertIs<Either.Left<String>>(Frequency("abc"))
    }

    // ---- Heading ----

    @Test
    fun validHeadingReturnsRight() {
        val result = Heading(270)
        assertIs<Either.Right<Heading>>(result)
        assertEquals(270, result.value.degrees)
    }

    @Test
    fun headingZeroReturnsLeft() {
        assertIs<Either.Left<String>>(Heading(0))
    }

    @Test
    fun heading360ReturnsRight() {
        assertIs<Either.Right<Heading>>(Heading(360))
    }

    @Test
    fun heading361ReturnsLeft() {
        assertIs<Either.Left<String>>(Heading(361))
    }

    @Test
    fun negativeHeadingReturnsLeft() {
        assertIs<Either.Left<String>>(Heading(-1))
    }

    // ---- Squawk ----

    @Test
    fun validSquawkReturnsRight() {
        val result = Squawk(7000)
        assertIs<Either.Right<Squawk>>(result)
        assertEquals(7000, result.value.code)
    }

    @Test
    fun squawkWithOctalDigitsOnlyReturnsRight() {
        assertIs<Either.Right<Squawk>>(Squawk(0))
        assertIs<Either.Right<Squawk>>(Squawk(7777))
        assertIs<Either.Right<Squawk>>(Squawk(1234))
    }

    @Test
    fun squawkWithDigitEightReturnsLeft() {
        assertIs<Either.Left<String>>(Squawk(8000))
    }

    @Test
    fun squawkWithDigitNineReturnsLeft() {
        assertIs<Either.Left<String>>(Squawk(1298))
    }

    @Test
    fun negativeSquawkReturnsLeft() {
        assertIs<Either.Left<String>>(Squawk(-1))
    }

    // ---- Knots ----

    @Test
    fun validKnotsReturnsRight() {
        val result = Knots(250)
        assertIs<Either.Right<Knots>>(result)
        assertEquals(250, result.value.value)
    }

    @Test
    fun zeroKnotsReturnsLeft() {
        assertIs<Either.Left<String>>(Knots(0))
    }

    @Test
    fun negativeKnotsReturnsLeft() {
        assertIs<Either.Left<String>>(Knots(-10))
    }

    // ---- Mach ----

    @Test
    fun validMachReturnsRight() {
        val result = Mach(0.82)
        assertIs<Either.Right<Mach>>(result)
        assertEquals(0.82, result.value.value)
    }

    @Test
    fun machZeroReturnsLeft() {
        assertIs<Either.Left<String>>(Mach(0.0))
    }

    @Test
    fun machOneReturnsRight() {
        assertIs<Either.Right<Mach>>(Mach(1.0))
    }

    @Test
    fun machAboveUpperBoundReturnsLeft() {
        assertIs<Either.Left<String>>(Mach(4.1))
    }

    // ---- DmeDistanceNm ----

    @Test
    fun validDmeDistanceReturnsRight() {
        val result = DmeDistanceNm(5.0)
        assertIs<Either.Right<DmeDistanceNm>>(result)
        assertEquals(5.0, result.value.value)
    }

    @Test
    fun zeroDmeDistanceReturnsRight() {
        assertIs<Either.Right<DmeDistanceNm>>(DmeDistanceNm(0.0))
    }

    @Test
    fun negativeDmeDistanceReturnsLeft() {
        assertIs<Either.Left<String>>(DmeDistanceNm(-0.1))
    }

    // ---- Minutes ----

    @Test
    fun validMinutesReturnsRight() {
        val result = Minutes(5)
        assertIs<Either.Right<Minutes>>(result)
        assertEquals(5, result.value.value)
    }

    @Test
    fun zeroMinutesReturnsRight() {
        assertIs<Either.Right<Minutes>>(Minutes(0))
    }

    @Test
    fun negativeMinutesReturnsLeft() {
        assertIs<Either.Left<String>>(Minutes(-1))
    }

    // ---- Wind ----

    @Test
    fun validWindReturnsRight() {
        val result = Wind(270, 15)
        assertIs<Either.Right<Wind>>(result)
        val wind = result.value
        assertEquals(270, wind.directionDegrees)
        assertEquals(15, wind.speedKnots)
    }

    @Test
    fun windWithValidGustReturnsRight() {
        val result = Wind(180, 10, gustKnots = 25)
        assertIs<Either.Right<Wind>>(result)
        assertEquals(25, result.value.gustKnots)
    }

    @Test
    fun windGustNotExceedingMeanReturnsLeft() {
        assertIs<Either.Left<String>>(Wind(180, 10, gustKnots = 10))
        assertIs<Either.Left<String>>(Wind(180, 10, gustKnots = 5))
    }

    @Test
    fun windDirectionOutOfRangeReturnsLeft() {
        assertIs<Either.Left<String>>(Wind(-1, 10))
        assertIs<Either.Left<String>>(Wind(361, 10))
    }

    @Test
    fun negativeWindSpeedReturnsLeft() {
        assertIs<Either.Left<String>>(Wind(270, -1))
    }

    // ---- Level ----

    @Test
    fun validFlightLevelReturnsRight() {
        val result = Level.FlightLevel(350)
        assertIs<Either.Right<Level.FlightLevel>>(result)
        assertEquals(350, result.value.fl)
    }

    @Test
    fun flightLevelZeroReturnsLeft() {
        assertIs<Either.Left<String>>(Level.FlightLevel(0))
    }

    @Test
    fun negativeFlightLevelReturnsLeft() {
        assertIs<Either.Left<String>>(Level.FlightLevel(-10))
    }

    @Test
    fun validAltitudeFeetReturnsRight() {
        val result = Level.AltitudeFeet(5000)
        assertIs<Either.Right<Level.AltitudeFeet>>(result)
        assertEquals(5000, result.value.feet)
    }

    @Test
    fun zeroAltitudeReturnsRight() {
        assertIs<Either.Right<Level.AltitudeFeet>>(Level.AltitudeFeet(0))
    }

    @Test
    fun negativeAltitudeReturnsLeft() {
        assertIs<Either.Left<String>>(Level.AltitudeFeet(-1))
    }

    @Test
    fun validHeightFeetReturnsRight() {
        val result = Level.HeightFeet(200)
        assertIs<Either.Right<Level.HeightFeet>>(result)
        assertEquals(200, result.value.feet)
    }

    @Test
    fun negativeHeightReturnsLeft() {
        assertIs<Either.Left<String>>(Level.HeightFeet(-1))
    }

    // ---- TrafficRef.SequenceNumber ----

    @Test
    fun validSequenceNumberReturnsRight() {
        val result = TrafficRef.SequenceNumber(3)
        assertIs<Either.Right<TrafficRef.SequenceNumber>>(result)
        assertEquals(3, result.value.number)
    }

    @Test
    fun zeroSequenceNumberReturnsLeft() {
        assertIs<Either.Left<String>>(TrafficRef.SequenceNumber(0))
    }

    // ---- HoldSpec.InboundTrack ----

    @Test
    fun validInboundTrackReturnsRight() {
        val result = HoldSpec.InboundTrack(
            fix = FixId("WILLO"),
            inboundDegreesMagnetic = 270,
            turnDirection = TurnDirection.RIGHT
        )
        assertIs<Either.Right<HoldSpec.InboundTrack>>(result)
        assertEquals(270, result.value.inboundDegreesMagnetic)
    }

    @Test
    fun inboundTrackZeroDegreesReturnsLeft() {
        assertIs<Either.Left<String>>(
            HoldSpec.InboundTrack(
                fix = FixId("WILLO"),
                inboundDegreesMagnetic = 0,
                turnDirection = TurnDirection.RIGHT
            )
        )
    }

    @Test
    fun inboundTrack361DegreesReturnsLeft() {
        assertIs<Either.Left<String>>(
            HoldSpec.InboundTrack(
                fix = FixId("WILLO"),
                inboundDegreesMagnetic = 361,
                turnDirection = TurnDirection.RIGHT
            )
        )
    }

    // ---- TurnByDegrees ----

    @Test
    fun validTurnByDegreesReturnsRight() {
        val result = TurnByDegrees(AircraftId("BAW123"), TurnDirection.LEFT, 90)
        assertIs<Either.Right<TurnByDegrees>>(result)
        assertEquals(90, result.value.degrees)
    }

    @Test
    fun turnByZeroDegreesReturnsLeft() {
        assertIs<Either.Left<String>>(TurnByDegrees(AircraftId("BAW123"), TurnDirection.LEFT, 0))
    }

    @Test
    fun turnBy360DegreesReturnsRight() {
        assertIs<Either.Right<TurnByDegrees>>(TurnByDegrees(AircraftId("BAW123"), TurnDirection.LEFT, 360))
    }

    @Test
    fun turnBy361DegreesReturnsLeft() {
        assertIs<Either.Left<String>>(TurnByDegrees(AircraftId("BAW123"), TurnDirection.LEFT, 361))
    }

    // ---- NumberInSequence ----

    @Test
    fun validNumberInSequenceReturnsRight() {
        val result = NumberInSequence(AircraftId("BAW123"), 2)
        assertIs<Either.Right<NumberInSequence>>(result)
        assertEquals(2, result.value.number)
    }

    @Test
    fun zeroNumberInSequenceReturnsLeft() {
        assertIs<Either.Left<String>>(NumberInSequence(AircraftId("BAW123"), 0))
    }
}
