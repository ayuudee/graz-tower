package xyz.easiersaid.twr.protocol

/**
 * Aircraft type — the doctrine-anchored data both pilot and controller
 * reference. ICAO Doc 8643 governs type designators + wake categories;
 * type-certificate / POH / TCDS / FCOM data govern kinematic + runway
 * performance.
 *
 * Pass 10 (D-AUDIT.4): closes the kinematic-uniformity gap. Pre-Pass-10
 * every aircraft used the same `PilotConstants.<X>_SPEED_MPS` global; now
 * each carries its own [Kinematics].
 *
 * **Sealed** (post-impl FP review S.3): the catalogue is closed; new
 * types are added by extending the sealed hierarchy, which surfaces in
 * code review. Manifest-based loading (D-AUDIT.4.B-FOLLOWUP) reconsiders
 * the seal at that pass — runtime construction will need either an
 * `Other` leaf or unsealing back to a data class.
 *
 * Two-layer projection through the firewall:
 *  - Pilot reads [kinematics] directly (sim-internal).
 *  - Controller sees only [icaoDesignator] (strip-projected via
 *    [xyz.easiersaid.twr.sim.FlightStrip]) and [wakeCategory] (sensor-
 *    projected via [xyz.easiersaid.twr.sim.SensorReading]). The full
 *    [AircraftType] never crosses the firewall.
 *
 * The runway-length gating rule that consumes [runwayLengthM] is filed
 * as **D-AUDIT.4.A-FOLLOWUP**; multi-type scenario testing is
 * **D-AUDIT.4.D-FOLLOWUP** — that pass also lands per-type circuit
 * pattern data + threading through `PilotRoutePlanner`. Pass 10
 * deliberately does NOT carry circuit-pattern data on the type because
 * `pilot.CIRCUIT_ALTITUDE_M` remains the live consumer; data-without-
 * matching-consumer is misinformation.
 */
sealed class AircraftType(
    val icaoDesignator: IcaoTypeDesignator,
    val wakeCategory: WakeCategory,
    val kinematics: Kinematics,
    val runwayLengthM: RunwayLengthRequirements,
) {

    /**
     * Per-type kinematic performance. Doctrine sources cited per-leaf
     * in this file's KDoc.
     */
    data class Kinematics(
        /** Taxi target speed (m/s). */
        val taxiSpeedMps: Double,
        /** Rotation speed at takeoff — phase transition trigger (m/s). */
        val rotationSpeedMps: Double,
        /** Climb-out true airspeed (m/s). */
        val climbSpeedMps: Double,
        /** Approach speed for base/final (m/s). */
        val approachSpeedMps: Double,
        /** Climb rate (vertical metres per second). */
        val climbRateMps: Double,
    ) {
        init {
            require(taxiSpeedMps > 0.0) { "taxiSpeedMps must be > 0, got $taxiSpeedMps" }
            require(rotationSpeedMps > 0.0) { "rotationSpeedMps must be > 0, got $rotationSpeedMps" }
            require(climbSpeedMps > 0.0) { "climbSpeedMps must be > 0, got $climbSpeedMps" }
            require(approachSpeedMps > 0.0) { "approachSpeedMps must be > 0, got $approachSpeedMps" }
            require(climbRateMps > 0.0) { "climbRateMps must be > 0, got $climbRateMps" }
            // Reality-anchored: V_R is at or below cruise climb (aerodynamic).
            require(rotationSpeedMps <= climbSpeedMps) {
                "rotationSpeedMps ($rotationSpeedMps) must be ≤ climbSpeedMps ($climbSpeedMps)"
            }
        }
    }

    /**
     * Runway-length requirements at sea-level / ISA / MTOW–MLW. Pass 10
     * carries the field as data on the type definition; the consuming
     * rule (runway-length gating before takeoff/landing clearance) lands
     * in **D-AUDIT.4.A-FOLLOWUP**.
     */
    data class RunwayLengthRequirements(
        /** Minimum takeoff field length (m). */
        val takeoffMinM: Int,
        /** Minimum landing distance (m). */
        val landingMinM: Int,
    ) {
        init {
            require(takeoffMinM > 0) { "takeoffMinM must be > 0, got $takeoffMinM" }
            require(landingMinM > 0) { "landingMinM must be > 0, got $landingMinM" }
        }
    }

    /**
     * Cessna 172 Skyhawk. Single-engine light GA, 4-seater.
     *
     * **Doctrine**:
     *  - ICAO Doc 8643: designator "C172", wake category Light.
     *  - Cessna 172R POH (Cessna Aircraft Co., 2008):
     *    rotation 55 KIAS (28 m/s), Vy climb 79 KIAS (40 m/s),
     *    approach 65 KIAS (33 m/s), climb 730 fpm (3.7 m/s).
     *  - FAA TCDS 3A12: takeoff 305 m, landing 407 m at SL/MTOW.
     */
    data object C172 : AircraftType(
        icaoDesignator = IcaoTypeDesignator.unsafe("C172"),
        wakeCategory = WakeCategory.L,
        kinematics = Kinematics(
            taxiSpeedMps = 10.0,
            rotationSpeedMps = 28.0,
            climbSpeedMps = 40.0,
            approachSpeedMps = 33.0,
            climbRateMps = 3.7,
        ),
        runwayLengthM = RunwayLengthRequirements(takeoffMinM = 305, landingMinM = 407),
    )

    /**
     * Boeing 737-800 (B738). Narrow-body twinjet.
     *
     * **Doctrine**:
     *  - ICAO Doc 8643: designator "B738", wake category Medium.
     *  - Boeing 737-800 FCOM (Boeing, 2014):
     *    V_R ≈ 145 KIAS (75 m/s), V_2+15 ≈ 250 KIAS below FL100
     *    (130 m/s), V_app ≈ 145 KIAS (75 m/s), climb 3000 fpm (15 m/s).
     *  - Boeing 737 AFM: TODA 2280 m / LDA 1700 m at SL / MTOW–MLW.
     */
    data object B738 : AircraftType(
        icaoDesignator = IcaoTypeDesignator.unsafe("B738"),
        wakeCategory = WakeCategory.M,
        kinematics = Kinematics(
            taxiSpeedMps = 10.0,
            rotationSpeedMps = 75.0,
            climbSpeedMps = 130.0,
            approachSpeedMps = 75.0,
            climbRateMps = 15.0,
        ),
        runwayLengthM = RunwayLengthRequirements(takeoffMinM = 2280, landingMinM = 1700),
    )
}
