package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.IcaoTypeDesignator
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.UnknownDesignator
import xyz.easiersaid.twr.protocol.WakeCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Pass 10 (D-AUDIT.4) — `AircraftType` doctrine pin and invariant
 * enforcement. Pass 13 extends with circuit pattern, waypoint radius,
 * run-up duration, and the firewall-narrow `runwayRequirementsFor`
 * companion lookup.
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
        // Pass 13: POH §4 / FAA AIM 4-3-3 — pattern altitude / downwind offset.
        assertEquals(305.0, t.circuitPattern.altitudeAglM, "POH §4 1000 ft pattern altitude")
        assertEquals(925.0, t.circuitPattern.downwindOffsetM, "FAA AIM 4-3-3 downwind ~0.5 nm")
        // Pass 13: engineering tuning — 4× half-tick at Vy 40 m/s.
        assertEquals(80.0, t.kinematics.waypointRadiusM, "engineering tuning, not doctrine")
        // Pass 13: POH §4 normal-procedures run-up sequence.
        assertEquals(60_000L, t.runUpDurationMs, "POH §4 typical 60 s run-up")
        // Pass 17: engineering tuning, not POH/FCOM doctrine — sim
        // default cruise altitude for IFR fallback when no published
        // altitude resolves. Same framing as `waypointRadiusM`.
        assertEquals(
            1000.0,
            t.cruiseAltitudeM,
            "engineering tuning — typical VFR cruise (~3300 ft); sim default for IFR fallback",
        )
        // fn-14.1 (R1): POH §2 — Maximum demonstrated crosswind velocity is 15 knots (not a limitation).
        assertEquals(
            Knots.unsafe(15),
            t.maxCrosswindKnots,
            "POH §2 — C172 maximum demonstrated crosswind 15 kt",
        )
        // Positivity invariant: every POH crosswind value is >= 1 kt by `Knots`'
        // positive-smart-type construction. The invariant test (parametric over
        // every leaf) lives below in `every leaf has positive maxCrosswindKnots`.
        assertTrue(
            t.maxCrosswindKnots.value > 0,
            "maxCrosswindKnots must be positive (Knots positive-only)",
        )
        // fn-15.1 (R1): C172 POH §2 does NOT publish a hard tailwind limitation;
        // the 10 kt value is the FAA AFH Ch 9 (FAA-H-8083-3C) industry-standard
        // advisory for light singles. See AircraftType.kt C172 KDoc.
        assertEquals(
            Knots.unsafe(10),
            t.maxTailwindKnots,
            "FAA AFH Ch 9 — C172 industry-standard advisory tailwind 10 kt",
        )
        assertTrue(
            t.maxTailwindKnots.value > 0,
            "maxTailwindKnots must be positive (Knots positive-only)",
        )
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
        // Pass 13: FCOM Supplementary Procedures (jet visual circuit).
        assertEquals(457.0, t.circuitPattern.altitudeAglM, "FCOM 1500 ft jet pattern altitude")
        assertEquals(1850.0, t.circuitPattern.downwindOffsetM, "FCOM ~1.0 nm jet downwind offset")
        // Pass 13: engineering tuning — 4× half-tick at climb 130 m/s.
        assertEquals(250.0, t.kinematics.waypointRadiusM, "engineering tuning, not doctrine")
        // Pass 13: FCOM NP cold-start before-takeoff sequence.
        assertEquals(600_000L, t.runUpDurationMs, "FCOM NP 10 min cold-start sequence")
        // Pass 17: engineering tuning — typical below-FL100 climb plateau.
        assertEquals(
            3000.0,
            t.cruiseAltitudeM,
            "engineering tuning — ~10000 ft below-FL100 plateau; sub-FL180 sim default",
        )
        // fn-14.1 (R1): Boeing 737-800 FCOM Limitations — 33 kt steady-crosswind (dry/grooved runway).
        assertEquals(
            Knots.unsafe(33),
            t.maxCrosswindKnots,
            "FCOM Limitations — B738 33 kt steady crosswind",
        )
        // fn-15.1 (R1): Boeing 737-800 FCOM Limitations §1 — 15 kt steady tailwind
        // (dry runway). Hard operational limitation, distinct doctrinal severity
        // from the C172 leaf's AFH advisory. See AircraftType.kt B738 KDoc.
        assertEquals(
            Knots.unsafe(15),
            t.maxTailwindKnots,
            "FCOM Limitations §1 — B738 15 kt steady tailwind (hard operational limitation)",
        )
    }

    @Test
    fun `every AircraftType leaf has a positive maxCrosswindKnots — fn-14_1 R1 invariant`() {
        // Parametric invariant over every leaf: POH crosswind values are always
        // >= 1 kt; the `Knots` positive-smart type enforces this at construction
        // time, but a regression that constructed via `Knots.unsafe(0)` or `-N`
        // would error at class-load. This test pins the contract behaviorally:
        // every leaf is referenced and reading `maxCrosswindKnots.value` is
        // positive. A new leaf landing without honoring the invariant fails
        // here. (Sealed `AircraftType` is closed; new leaves are added in this
        // file, so a per-leaf row is the right form rather than reflection.)
        assertTrue(AircraftType.C172.maxCrosswindKnots.value > 0, "C172 maxCrosswindKnots > 0")
        assertTrue(AircraftType.B738.maxCrosswindKnots.value > 0, "B738 maxCrosswindKnots > 0")
    }

    @Test
    fun `every AircraftType leaf has a positive maxTailwindKnots — fn-15_1 R1 invariant`() {
        // Parametric invariant over every leaf: tailwind values are always
        // >= 1 kt (Knots positive-smart type). 0 kt "no tailwind allowed"
        // would force GA on dead headwind with zero margin — operationally
        // nonsensical. A new leaf landing without honoring the invariant
        // fails here. Doctrinal severity varies per type (C172 advisory /
        // B738 hard limit) — see AircraftType.maxTailwindKnots KDoc — but
        // the positivity invariant is uniform.
        assertTrue(AircraftType.C172.maxTailwindKnots.value > 0, "C172 maxTailwindKnots > 0")
        assertTrue(AircraftType.B738.maxTailwindKnots.value > 0, "B738 maxTailwindKnots > 0")
    }

    @Test
    fun `Kinematics init rejects non-positive speeds rates and waypoint radius`() {
        assertFails("taxiSpeedMps must be > 0") {
            AircraftType.Kinematics(0.0, 28.0, 40.0, 33.0, 3.7, 80.0)
        }
        assertFails("rotationSpeedMps must be > 0") {
            AircraftType.Kinematics(10.0, 0.0, 40.0, 33.0, 3.7, 80.0)
        }
        assertFails("climbSpeedMps must be > 0") {
            AircraftType.Kinematics(10.0, 28.0, 0.0, 33.0, 3.7, 80.0)
        }
        assertFails("approachSpeedMps must be > 0") {
            AircraftType.Kinematics(10.0, 28.0, 40.0, 0.0, 3.7, 80.0)
        }
        assertFails("climbRateMps must be > 0") {
            AircraftType.Kinematics(10.0, 28.0, 40.0, 33.0, 0.0, 80.0)
        }
        assertFails("waypointRadiusM must be > 0") {
            AircraftType.Kinematics(10.0, 28.0, 40.0, 33.0, 3.7, 0.0)
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
                waypointRadiusM = 80.0,
            )
        }
    }

    // Pass 17 (D-PASS-13.2): cross-field invariant on `AircraftType.init`
    // (cruiseAltitudeM > circuitPattern.altitudeAglM, < 5500m) cannot
    // be tested via synthetic leaves because `AircraftType` is sealed
    // (subtyping restricted to the same module). The C172 and B738
    // doctrine pins above implicitly verify the invariant: each
    // construction would fail at class-load time if the require()s
    // didn't hold for the configured values. A direct invariant test
    // would require moving the spec to `:protocol/commonTest` — filed
    // as **D-PASS-17.3-FOLLOWUP** if a third type lands.

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
    fun `CircuitPattern init rejects non-positive altitude or downwind offset`() {
        assertFails("altitudeAglM must be > 0") {
            AircraftType.CircuitPattern(altitudeAglM = 0.0, downwindOffsetM = 925.0)
        }
        assertFails("downwindOffsetM must be > 0") {
            AircraftType.CircuitPattern(altitudeAglM = 305.0, downwindOffsetM = 0.0)
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

    @Test
    fun `runwayRequirementsFor returns runway slice for known and Left for unknown`() {
        // Pass 13: firewall-narrow lookup. Returns ONLY the runway slice
        // — controller cannot reach kinematics or circuit data via this path.
        val c172Result = AircraftType.runwayRequirementsFor(IcaoTypeDesignator.unsafe("C172"))
        assertEquals(
            AircraftType.C172.runwayLengthM,
            c172Result.getOrNull(),
            "C172 designator → C172 runway requirements (TCDS 3A12)",
        )
        val b738Result = AircraftType.runwayRequirementsFor(IcaoTypeDesignator.unsafe("B738"))
        assertEquals(
            AircraftType.B738.runwayLengthM,
            b738Result.getOrNull(),
            "B738 designator → B738 runway requirements (737 AFM)",
        )
        // Unknown designator → Left(UnknownDesignator(...)). The Either is the
        // shape: a future controller-side caller pattern-matches and emits a
        // diagnostic naming the offender.
        val unknown = IcaoTypeDesignator.unsafe("XXXX")
        val unknownResult = AircraftType.runwayRequirementsFor(unknown)
        assertTrue(unknownResult.isLeft(), "unknown designator returns Left")
        assertEquals(
            UnknownDesignator(unknown),
            unknownResult.swap().getOrNull(),
            "Left carries the offending designator for diagnostics",
        )
    }
}
