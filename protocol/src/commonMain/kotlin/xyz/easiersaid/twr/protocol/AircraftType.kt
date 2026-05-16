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
     * fn-14.1 (G3a-react R1): POH-derived maximum demonstrated
     * crosswind component. Read by the pilot's reactive-GA recognition
     * `derivePilotEvent` crosswind branch — when the
     * crosswind component computed from the world's wind report
     * exceeds this value while the aircraft is on final, the pilot
     * self-initiates a go-around.
     *
     * **Doctrine note**: per FAA AC 23-8B / 14 CFR §23.233 (pre-Amd 64),
     * the POH "maximum demonstrated crosswind" is **performance
     * information** (`0.2 V_SO` certification floor), NOT a formal
     * limitation. FAA AFH (FAA-H-8083-3C) Chapter 9 lists "attempting
     * a landing in crosswinds that exceed the airplane's maximum
     * demonstrated crosswind component" as Common Error #1. v1 models
     * a competent VFR pilot as going around when the demonstrated
     * value is exceeded; the personal-minimums judgement layer is
     * filed as `D-PASS-g3a-react-personal-minimums`. Cited per-leaf
     * below.
     *
     * Reuses [Knots] (positive-only smart type from
     * [xyz.easiersaid.twr.protocol.Instruction]); every POH
     * crosswind value is ≥ 1 kt by construction.
     *
     * End-to-end sim coverage:
     * `xyz.easiersaid.twr.sim.G3aPilotReactiveCrosswindTest` (fn-14.2 —
     * world hook authors a wind shift past `C172.maxCrosswindKnots`;
     * pilot reactive-GA path is exercised composition-end). Pilot-side
     * unit coverage: `xyz.easiersaid.twr.pilot.observe
     * .CrosswindLimitExceededSpec` + `xyz.easiersaid.twr.pilot
     * .PilotCrosswindHysteresisTest` (fn-14.1).
     */
    val maxCrosswindKnots: Knots,
    /**
     * fn-15.1 (G3a-react-tailwind R1): maximum tailwind component the
     * type's operating handbook (POH / FCOM) or industry guidance
     * recognises as the operational maximum. Read by the pilot's
     * reactive-GA recognition `derivePilotEvent` tailwind branch — when
     * the tailwind component computed from the world's wind report
     * against the active runway exceeds this value while the aircraft is
     * on final, the pilot self-initiates a go-around.
     *
     * **Doctrinal severity varies per type** — load-bearing for KDoc /
     * commit message / RegulationDatabase scope:
     *  - for some types (e.g. [C172] light single) the current POH does
     *    NOT publish an explicit tailwind limitation; the value used here
     *    is the **FAA AFH (FAA-H-8083-3C) industry-standard advisory**
     *    operating maximum for light singles (AFH Ch 9 frames tailwind
     *    landings as high-risk operations);
     *  - for others (e.g. [B738] narrow-body twinjet) the FCOM publishes
     *    a **hard operational limitation** (Limitations §1).
     *
     * Per-leaf KDocs cite the source. The pilot's reactive-GA recognition
     * fires on exceedance regardless of doctrinal severity — modelling
     * a competent pilot's go-around decision (same rationale as fn-14.1
     * for crosswind: AC 23-8B / AFH Ch 9 Common Error #1 — a competent
     * pilot goes around when the demonstrated / advisory value is
     * exceeded). Personal-minimums judgement layer below the typed value
     * is filed as `D-PASS-g3a-react-tailwind-personal-minimums`. No
     * generic "POH = hard limit" framing — manufacturer values are not
     * regulations, per codex round-1 closure on `RegulationDatabase`
     * scope.
     *
     * Reuses [Knots] (positive-only smart type from
     * [xyz.easiersaid.twr.protocol.Instruction]); every POH / advisory
     * tailwind value is ≥ 1 kt by construction. Cross-reference
     * [maxCrosswindKnots] — sibling typed datum, complementary axis
     * (lateral control authority vs. touchdown energy / runway remaining
     * / go-around margin).
     *
     * End-to-end sim coverage:
     * `xyz.easiersaid.twr.sim.G3aPilotReactiveTailwindTest` (fn-15.2 —
     * world hook authors a wind shift past `C172.maxTailwindKnots`;
     * pilot reactive-GA path is exercised composition-end). Pilot-side
     * unit coverage: `xyz.easiersaid.twr.pilot.observe
     * .PilotEventTailwindTest` + `xyz.easiersaid.twr.pilot
     * .PilotTailwindHysteresisTest` (fn-15.1).
     */
    val maxTailwindKnots: Knots,
    /**
     * fn-28.2 (G3a-react-density-altitude R2): per-type maximum density
     * altitude beyond which the pilot declines departure on the apron.
     *
     * **Nullable by design** (round-5 Major 2 / applicability semantic):
     *  - Light-GA training types (e.g. [C172]) carry a concrete threshold —
     *    `Feet.unsafe(5000)` per FAA AC 61-107B §3-1, the FAA's "high
     *    density altitude operating considerations" advisory floor for
     *    light, non-turbo-charged piston aircraft.
     *  - Jet-class / turbine types (e.g. [B738]) carry **null** — DA
     *    decline is a light-GA concept. Jet engines are flat-rated for
     *    thrust over their certification envelope, and jet-class aircraft
     *    have substantially more performance margin at common DA values;
     *    a "decline departure on DA alone" decision is not part of jet
     *    operational doctrine at v1 scope. Future heavy-turbine
     *    performance modelling (takeoff-distance correction tables, V-speed
     *    adjustments) lives in a sibling field — out of scope here.
     *
     * **Recognition site** (fn-28.2): `derivePilotEvent`'s
     * `deriveDensityAltitudeEvent` branch gates on
     * `aircraft.type.maxDensityAltitudeFt?.let { da > it } ?: false`
     * — a null threshold means the trigger never fires (fall-through),
     * NOT that it fires unconditionally. Unit-tested for B738 explicitly.
     *
     * **Reuses** [Feet] (positive-int smart constructor in `:protocol` per
     * R24 / fn-28.1 residency lift) — every DA threshold is ≥ 1 ft by
     * construction. The smart-type rules out negative or zero thresholds
     * (operationally nonsensical).
     *
     * **Doctrine**: FAA AC 61-107B §3-1 ("Aircraft Operations at Altitudes
     * Above 25,000 Feet Mean Sea Level or Mach Numbers Greater Than .75")
     * §3-1 — high-DA operating considerations for light, non-turbo-charged
     * piston aircraft. The 5000 ft DA threshold is the AC's named
     * "high density altitude" floor. See also
     * [xyz.easiersaid.twr.protocol.RegulationDatabase.FAA_AC_61_107B_3_1].
     */
    val maxDensityAltitudeFt: Feet?,
    /**
     * Engineering-tuning cruise-altitude default for IFR route-planner
     * fallback. Pass 17 (D-PASS-13.2 closure): when an IFR procedure
     * has no published altitude (e.g., a SID with no last-waypoint
     * altitude constraint), the helper falls back to this value
     * rather than the pre-Pass-17 wrong-units fallback to
     * `circuitPattern.altitudeAglM` (terminal-area altitude).
     *
     * **Engineering tuning, not pilot doctrine** (per Pass 17 review
     * fold-in — earlier draft cited AIM 4-3-3 incorrectly). Sim-default
     * cruise target; values approximate typical en-route altitudes
     * for the type's operational envelope. Same framing as
     * [Kinematics.waypointRadiusM].
     */
    val cruiseAltitudeM: Double,
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
        // Pass 17 (D-PASS-13.2): cruise altitude is en-route, terminal-
        // area pattern altitude is by-construction below it. The strict
        // `>` rejects degenerate equality (pattern == cruise would
        // mean no climb between terminal and en-route phases). Fixed-
        // wing C172/B738 catalogue today; rotorcraft / very-short-haul
        // types adding to the catalogue would re-shape this invariant.
        require(cruiseAltitudeM > circuitPattern.altitudeAglM) {
            "cruiseAltitudeM ($cruiseAltitudeM) must be > circuitPattern.altitudeAglM " +
                "(${circuitPattern.altitudeAglM}); en-route is above terminal-area"
        }
        // Sub-FL180 sim default. Class A floor at FL180 in most
        // jurisdictions; FL180+ cruise needs typed `FlightLevel` (out
        // of scope for Pass 17 — manifest type loading would touch it).
        require(cruiseAltitudeM < 5500.0) {
            "cruiseAltitudeM ($cruiseAltitudeM) must be < 5500m (FL180 floor); " +
                "FL180+ cruise needs typed FlightLevel — out of scope"
        }
        // fn-28.2 (R2-DA): nullable DA threshold — null is the "DA decline
        // is out-of-scope for this type" semantic (jet-class), NOT a default
        // for missing data. When non-null, the value must be strictly
        // positive — a 0 ft threshold would fire DA decline at every
        // sea-level airport, which is operationally nonsensical. [Feet]'s
        // smart constructor already enforces `>= 0`; this require tightens
        // to `> 0` for the threshold semantic. Skips on null per the
        // applicability semantic (jet-class types fall through).
        maxDensityAltitudeFt?.let { da ->
            require(da.value > 0) {
                "maxDensityAltitudeFt must be > 0 ft when non-null, got ${da.value}"
            }
        }
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
     *  - POH §2 (Limitations / Operating Limitations): "Maximum
     *    demonstrated crosswind velocity is 15 knots (not a limitation)" —
     *    consumed by fn-14.1's reactive-GA recognition.
     *  - **Tailwind (fn-15.1)**: the Cessna 172S NAV III / 172R POH §2
     *    Operating Limitations does **NOT** publish an explicit hard
     *    tailwind component limitation (POH §2 addresses crosswind only —
     *    "15 knots demonstrated", not a limitation). The 10 kt value used
     *    here is the **FAA AFH Ch 9 (FAA-H-8083-3C) industry-standard
     *    advisory** operating maximum for light singles; the AFH frames
     *    tailwind landings as high-risk operations and 10 kt is the common
     *    operating advisory. Modelling: a competent VFR pilot goes around
     *    when the advisory is exceeded — same rationale as fn-14's
     *    crosswind modelling (AC 23-8B's demonstrated value is similarly
     *    performance information, but a competent pilot treats it as the
     *    trigger). Personal-minimums judgement layer filed as
     *    `D-PASS-g3a-react-tailwind-personal-minimums`.
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
        cruiseAltitudeM = 1000.0, // engineering tuning — typical VFR cruise; sub-FL180.
        // POH Section 2: "Maximum demonstrated crosswind velocity is 15 knots (not a limitation)."
        maxCrosswindKnots = Knots.unsafe(15),
        // fn-15.1: FAA AFH Ch 9 industry-standard advisory for light singles
        // (POH §2 does NOT publish a hard tailwind limitation — see C172 KDoc above).
        maxTailwindKnots = Knots.unsafe(10),
        // fn-28.2 (R2-DA): FAA AC 61-107B §3-1 high-DA operating threshold
        // for light, non-turbo-charged piston aircraft (the 5000 ft DA floor).
        // Light-GA training type — concrete threshold applies.
        maxDensityAltitudeFt = Feet.unsafe(5000),
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
     *  - Boeing 737-800 FCOM (Limitations §1, "Crosswind Guidelines"):
     *    33 kt steady-crosswind limit on dry / grooved runway — consumed
     *    by fn-14.1's reactive-GA recognition.
     *  - **Tailwind (fn-15.1)**: Boeing 737-800 FCOM Limitations §1
     *    publishes **15 kt steady tailwind on dry runway** as a **hard
     *    operational limitation** (Limitations section, no exception).
     *    Distinct doctrinal severity from the C172 leaf's AFH advisory:
     *    for jet-class types the value IS a hard limitation. Verify
     *    edition at task time when updating.
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
        cruiseAltitudeM = 3000.0, // engineering tuning — typical below-FL100 climb plateau.
        // Boeing 737-800 FCOM Limitations: 33 kt steady crosswind (dry/grooved runway).
        maxCrosswindKnots = Knots.unsafe(33),
        // fn-15.1: Boeing 737-800 FCOM Limitations §1 — 15 kt steady tailwind
        // (dry runway). Hard operational limitation; see B738 KDoc above.
        maxTailwindKnots = Knots.unsafe(15),
        // fn-28.2 (R2-DA): jet-class types do not carry a DA-decline threshold —
        // DA decline is a light-GA concept (flat-rated thrust + substantial
        // performance margin make a "decline on DA alone" decision out-of-scope
        // for v1 jet operations). Recognition gate `da > limit` evaluates `false`
        // via the elvis-default on null, so the DA branch never fires for B738.
        // Future heavy-turbine performance modelling (takeoff distance, V-speed
        // corrections) lives in a sibling field — see KDoc above.
        maxDensityAltitudeFt = null,
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
