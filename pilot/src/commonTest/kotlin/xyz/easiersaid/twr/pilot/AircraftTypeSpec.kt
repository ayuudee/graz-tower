package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.IcaoTypeDesignator
import xyz.easiersaid.twr.protocol.WakeCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Pass 10 (D-AUDIT.4) — `AircraftType` doctrine pin and invariant
 * enforcement.
 *
 * Each `@Test` carries multiple assertions per the
 * `LostCommsTerminalSpec` precedent. A regression that swaps two field
 * values, drops doctrine-cited values, or weakens an `init` invariant
 * fails here with a named diagnostic.
 */
class AircraftTypeSpec {

    @Test
    fun `C172 doctrine values match POH and ICAO Doc 8643`() {
        val t = AircraftType.C172
        assertEquals(IcaoTypeDesignator.unsafe("C172"), t.icaoDesignator, "ICAO Doc 8643 designator")
        assertEquals(WakeCategory.L, t.wakeCategory, "ICAO Doc 4444 §5.8 wake category")
        // Cessna 172R POH (2008).
        assertEquals(10.0, t.kinematics.taxiSpeedMps, "POH taxi speed")
        assertEquals(28.0, t.kinematics.rotationSpeedMps, "POH rotation 55 KIAS")
        assertEquals(40.0, t.kinematics.climbSpeedMps, "POH Vy 79 KIAS")
        assertEquals(33.0, t.kinematics.approachSpeedMps, "POH approach 65 KIAS")
        assertEquals(3.7, t.kinematics.climbRateMps, "POH climb 730 fpm")
        // FAA TCDS 3A12 SL/MTOW.
        assertEquals(305, t.runwayLengthM.takeoffMinM, "TCDS takeoff 305 m")
        assertEquals(407, t.runwayLengthM.landingMinM, "TCDS landing 407 m")
    }

    @Test
    fun `B738 doctrine values match Boeing FCOM and ICAO Doc 8643`() {
        val t = AircraftType.B738
        assertEquals(IcaoTypeDesignator.unsafe("B738"), t.icaoDesignator, "ICAO Doc 8643 designator")
        assertEquals(WakeCategory.M, t.wakeCategory, "ICAO Doc 4444 §5.8 wake category")
        // Boeing 737-800 FCOM (2014).
        assertEquals(10.0, t.kinematics.taxiSpeedMps, "FCOM taxi (operationally similar to GA)")
        assertEquals(75.0, t.kinematics.rotationSpeedMps, "FCOM V_R 145 KIAS")
        assertEquals(130.0, t.kinematics.climbSpeedMps, "FCOM 250 KIAS below FL100")
        assertEquals(75.0, t.kinematics.approachSpeedMps, "FCOM V_app 145 KIAS")
        assertEquals(15.0, t.kinematics.climbRateMps, "FCOM 3000 fpm")
        // Boeing 737 AFM SL/MTOW–MLW.
        assertEquals(2280, t.runwayLengthM.takeoffMinM, "AFM TODA 2280 m")
        assertEquals(1700, t.runwayLengthM.landingMinM, "AFM LDA 1700 m")
    }

    @Test
    fun `Kinematics init rejects non-positive speeds and rates`() {
        assertFails("taxiSpeedMps must be > 0") {
            AircraftType.Kinematics(0.0, 28.0, 40.0, 33.0, 3.7)
        }
        assertFails("rotationSpeedMps must be > 0") {
            AircraftType.Kinematics(10.0, 0.0, 40.0, 33.0, 3.7)
        }
        assertFails("climbSpeedMps must be > 0") {
            AircraftType.Kinematics(10.0, 28.0, 0.0, 33.0, 3.7)
        }
        assertFails("approachSpeedMps must be > 0") {
            AircraftType.Kinematics(10.0, 28.0, 40.0, 0.0, 3.7)
        }
        assertFails("climbRateMps must be > 0") {
            AircraftType.Kinematics(10.0, 28.0, 40.0, 33.0, 0.0)
        }
    }

    @Test
    fun `Kinematics init rejects rotation speed greater than climb speed`() {
        assertFails("rotationSpeedMps must be ≤ climbSpeedMps") {
            // Reality-anchored: V_R is at or below cruise climb.
            AircraftType.Kinematics(
                taxiSpeedMps = 10.0,
                rotationSpeedMps = 50.0,  // higher than climbSpeedMps
                climbSpeedMps = 40.0,
                approachSpeedMps = 33.0,
                climbRateMps = 3.7,
            )
        }
    }

    @Test
    fun `RunwayLengthRequirements init rejects non-positive lengths`() {
        assertFails("takeoffMinM must be > 0") {
            AircraftType.RunwayLengthRequirements(takeoffMinM = 0, landingMinM = 407)
        }
        assertFails("landingMinM must be > 0") {
            AircraftType.RunwayLengthRequirements(takeoffMinM = 305, landingMinM = 0)
        }
    }

    @Test
    fun `IcaoTypeDesignator of accepts valid Doc 8643 codes and rejects malformed`() {
        // Valid: 2-4 alphanumeric uppercase.
        assertTrue(IcaoTypeDesignator.of("C172").isRight(), "C172 valid")
        assertTrue(IcaoTypeDesignator.of("B738").isRight(), "B738 valid")
        assertTrue(IcaoTypeDesignator.of("A320").isRight(), "A320 valid")
        assertTrue(IcaoTypeDesignator.of("E2").isRight(), "2-char minimum")
        assertTrue(IcaoTypeDesignator.of("C25A").isRight(), "4-char maximum")
        // Invalid — Either.Left carries the offending raw value.
        assertTrue(IcaoTypeDesignator.of("").isLeft(), "empty string")
        assertTrue(IcaoTypeDesignator.of("A").isLeft(), "1 char too short")
        assertTrue(IcaoTypeDesignator.of("ABCDE").isLeft(), "5 chars too long")
        assertTrue(IcaoTypeDesignator.of("c172").isLeft(), "lowercase rejected")
        assertTrue(IcaoTypeDesignator.of("C-172").isLeft(), "hyphen rejected")
        assertTrue(IcaoTypeDesignator.of("C 17").isLeft(), "space rejected")
    }
}
