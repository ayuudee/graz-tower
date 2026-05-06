package xyz.easiersaid.twr.protocol

import arrow.core.Either
import arrow.core.left
import arrow.core.right

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
 * Pass 13 (D-AUDIT.3, .4.A-FOLLOWUP, .4.D-FOLLOWUP) — closes the remaining
 * placeholder consumers. [CircuitPattern] is re-introduced (was Pass 10
 * then dropped because the parallel `pilot.CIRCUIT_ALTITUDE_M` global
 * would have made the data misleading; that global is now deleted).
 * [Kinematics.waypointRadiusM] tunes capture radius per-type (jet at
 * 130 m/s × 1 s tick = 130 m physics step; capture radius needs to scale).
 * [runUpDurationMs] is procedural (mag check, FCOM checklists), not
 * kinematic — top-level on the type. [runwayRequirementsFor] is the
 * companion-object lookup the runway-length gating rule reads — it
 * returns ONLY the runway slice (firewall-narrow), not the whole type.
 *
 * **Sealed** (post-impl FP review S.3): the catalogue is closed; new
 * types are added by extending the sealed hierarchy, which surfaces in
 * code review. Manifest-based loading (D-AUDIT.4.B-FOLLOWUP) reconsiders
 * the seal at that pass — runtime construction will need either an
 * `Other` leaf or unsealing back to a data class.
 *
 * Two-layer projection through the firewall:
 *  - Pilot reads [kinematics] / [circuitPattern] / [runUpDurationMs]
 *    directly (sim-internal).
 *  - Controller sees only [icaoDesignator] (strip-projected via
 *    [xyz.easiersaid.twr.sim.FlightStrip]) and [wakeCategory] (sensor-
 *    projected via [xyz.easiersaid.twr.sim.SensorReading]). The
 *    runway-length gating rule reads [runwayRequirementsFor] which
 *    returns only [RunwayLengthRequirements] — the type itself never
 *    crosses the firewall.
 */
sealed class AircraftType(
    val icaoDesignator: IcaoTypeDesignator,
    val wakeCategory: WakeCategory,
    val kinematics: Kinematics,
    val runwayLengthM: RunwayLengthRequirements,
    /**
     * VFR circuit pattern — pattern altitude (AGL) and downwind offset
     * (lateral distance from runway centreline). Read by the pilot's
     * route planner to set `targetAltitudeM` on circuit-pattern routes.
     */
    val circuitPattern: CircuitPattern,
    /**
     * Run-up / before-takeoff procedural duration. Read by
     * `PilotCognitive` for [xyz.easiersaid.twr.protocol] mission steps
     * with `CompletionMode.TIMED`. Procedural, not kinematic — lives at
     * the top of the type, not inside [Kinematics].
     */
    val runUpDurationMs: Long,
) {

    init {
        require(runUpDurationMs > 0) { "runUpDurationMs must be > 0, got $runUpDurationMs" }
    }

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
        /**
         * Waypoint capture radius (m) — pilot pops a waypoint when
         * distance to it falls below this threshold. **Engineering
         * tuning, not doctrine.** A jet at climb speed traverses
         * ≈100–130 m per 1 s tick; a 5 m radius (the pre-Pass-13 global)
         * would mean the aircraft passes the waypoint between ticks
         * without registering capture. Per-phase scaling (taxi vs
         * climb vs final) is the deeper fix and is filed as
         * **D-AUDIT.4.D.II-FOLLOWUP**; Pass 13 carries a single
         * per-type scalar.
         */
        val waypointRadiusM: Double,
    ) {
        init {
            require(taxiSpeedMps > 0.0) { "taxiSpeedMps must be > 0, got $taxiSpeedMps" }
            require(rotationSpeedMps > 0.0) { "rotationSpeedMps must be > 0, got $rotationSpeedMps" }
            require(climbSpeedMps > 0.0) { "climbSpeedMps must be > 0, got $climbSpeedMps" }
            require(approachSpeedMps > 0.0) { "approachSpeedMps must be > 0, got $approachSpeedMps" }
            require(climbRateMps > 0.0) { "climbRateMps must be > 0, got $climbRateMps" }
            require(waypointRadiusM > 0.0) { "waypointRadiusM must be > 0, got $waypointRadiusM" }
            // Reality-anchored: V_R is at or below cruise climb (aerodynamic).
            require(rotationSpeedMps <= climbSpeedMps) {
                "rotationSpeedMps ($rotationSpeedMps) must be ≤ climbSpeedMps ($climbSpeedMps)"
            }
        }
    }

    /**
     * Runway-length requirements at sea-level / ISA / MTOW–MLW. Pass 13
     * (D-AUDIT.4.A-FOLLOWUP) wires the consuming rule
     * `RunwayLengthSufficient`. Runway-condition adjustments (wet,
     * contaminated, displaced threshold) are filed as
     * **D-AUDIT.4.A.II-FOLLOWUP**.
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
     * VFR circuit pattern dimensions. [altitudeAglM] is the pattern
     * altitude above aerodrome elevation; [downwindOffsetM] is the
     * lateral distance from the runway centreline at the downwind leg.
     * Doctrine sources cited per-leaf in this file's KDoc.
     */
    data class CircuitPattern(
        /** Pattern altitude AGL (m). */
        val altitudeAglM: Double,
        /** Downwind leg lateral offset from runway centreline (m). */
        val downwindOffsetM: Double,
    ) {
        init {
            require(altitudeAglM > 0.0) { "altitudeAglM must be > 0, got $altitudeAglM" }
            require(downwindOffsetM > 0.0) { "downwindOffsetM must be > 0, got $downwindOffsetM" }
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
     *  - POH §4 (Normal Procedures): pattern altitude 1000 ft AGL ≈ 305 m;
     *    downwind offset ≈ 0.5 nm ≈ 925 m (FAA AIM 4-3-3 / AC 90-66B).
     *  - POH §4 (Run-Up): 60 s typical (mag check, carb-heat verify,
     *    instrument scan).
     *  - Capture radius: engineering tuning at 80 m (≈ 4× half-tick at
     *    Vy = 40 m/s; rounded for safety margin). See [Kinematics.waypointRadiusM].
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
            waypointRadiusM = 80.0,
        ),
        runwayLengthM = RunwayLengthRequirements(takeoffMinM = 305, landingMinM = 407),
        circuitPattern = CircuitPattern(altitudeAglM = 305.0, downwindOffsetM = 925.0),
        runUpDurationMs = 60_000L,
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
     *  - Visual circuit (FCOM Supplementary Procedures, jet-class):
     *    pattern altitude 1500 ft AGL ≈ 457 m; downwind offset ≈ 1.0 nm
     *    ≈ 1850 m.
     *  - FCOM NP (Normal Procedures): cold-start before-takeoff sequence
     *    ≈ 10 minutes (engine warmup, FMC entry, before-takeoff checklist).
     *  - Capture radius: engineering tuning at 250 m (≈ 4× half-tick at
     *    cruise climb 130 m/s; matches the C172 ratio).
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
            waypointRadiusM = 250.0,
        ),
        runwayLengthM = RunwayLengthRequirements(takeoffMinM = 2280, landingMinM = 1700),
        circuitPattern = CircuitPattern(altitudeAglM = 457.0, downwindOffsetM = 1850.0),
        runUpDurationMs = 600_000L,
    )

    companion object {
        /**
         * Firewall-narrow lookup for the runway-length gating rule. The
         * controller has only the [IcaoTypeDesignator] (from the strip);
         * resolving it to the full [AircraftType] would expose
         * [Kinematics] and [CircuitPattern] (pilot-internal). This
         * function returns ONLY the runway-relevant slice — the
         * controller cannot reach the rest of the type via this path.
         *
         * Returns [Either.Left] with [UnknownDesignator] for any code
         * not present in the sealed hierarchy. The runway-length guard
         * fails closed on the [Left] branch; callers are responsible
         * for surfacing the diagnostic via the rule trace.
         */
        fun runwayRequirementsFor(
            designator: IcaoTypeDesignator,
        ): Either<UnknownDesignator, RunwayLengthRequirements> = when (designator) {
            C172.icaoDesignator -> C172.runwayLengthM.right()
            B738.icaoDesignator -> B738.runwayLengthM.right()
            else -> UnknownDesignator(designator).left()
        }
    }
}

/**
 * Failure side of [AircraftType.Companion.runwayRequirementsFor]: the
 * given designator does not match any known [AircraftType]. Carries the
 * offending designator so the rule's diagnostic message can name it.
 */
data class UnknownDesignator(val designator: IcaoTypeDesignator)
