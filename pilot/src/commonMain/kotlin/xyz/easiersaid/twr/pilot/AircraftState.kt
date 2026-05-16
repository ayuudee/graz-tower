package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId

/**
 * Ground-truth aircraft state as the simulation knows it.
 *
 * Distinct from [xyz.easiersaid.twr.controller.AircraftObservation] (the
 * controller's view) because the sim holds reality and the controller may
 * only have a partial or delayed picture of it.
 *
 * Two position representations are carried side-by-side:
 *   - [position] is the Cartesian ground truth; physics integrates it.
 *   - [positionPoint] is the graph-level "nearest named point" that the
 *     controller sees in an [xyz.easiersaid.twr.controller.AircraftObservation].
 *     Kinematics snaps it forward when the aircraft lands on a waypoint.
 *     Keeping a discrete graph position alongside continuous coordinates is
 *     how the controller's point-indexed guards (AtHoldingPoint, AtStand,
 *     OnRunway) keep working without continuous geometry queries.
 *
 * Kinematics otherwise are the minimum needed for ground operations
 * (slice 4b/4c): scalar current [speedMps] and pilot-commanded
 * [targetSpeedMps]. Headings are not explicit — while on a
 * [PilotRoute.Ground] the aircraft always heads toward the first remaining
 * waypoint. Airborne kinematics (heading, altitude, vertical rate as
 * controllable intent) land with the slice that introduces circuit and
 * approach following.
 */
data class AircraftState(
    val id: AircraftId,
    val callsign: Callsign,
    val position: Position,
    val positionPoint: PointId,
    val speedMps: Double = 0.0,
    val targetSpeedMps: Double = 0.0,
    /** Geometric altitude AGL (the sim's only vertical axis; 4e-A uses metres, scalar). */
    val altitudeM: Double = 0.0,
    /** Target altitude the pilot is climbing/descending toward; 0.0 ⇒ ground. */
    val targetAltitudeM: Double = 0.0,
    val phase: PilotPhase = PilotPhase.AtStand,
    val route: PilotRoute = PilotRoute.None,
    /**
     * Aircraft type — kinematic performance + ICAO designator + wake
     * category + runway-length requirements + circuit pattern (Pass 10
     * D-AUDIT.4). Default `C172` matches G0's GA fixture; multi-type
     * scenarios populate explicitly.
     *
     * The pilot reads `type.kinematics.<field>` directly. The controller
     * sees only the strip-projected `icaoDesignator` and sensor-projected
     * `wakeCategory` — never the full type — per the firewall.
     */
    val type: AircraftType = AircraftType.C172,
    /**
     * Cognitive pilot mission plan. When non-null, the cognitive pilot
     * generates transmissions and advances through mission steps. When null,
     * legacy DefaultPilot runs.
     *
     * Phase D of the pilot-firewall plan: the previous `humanPiloted: Boolean`
     * field is gone. The pilot's behaviour does not branch on whether the
     * cockpit is crewed by a human or AI — same mission tree, same timing
     * rules, same inputs. Real-world differences in crewing are observed
     * (radio-derived), not assumed (boolean-discriminated). Architectural
     * test `FirewallAircraftStateTest` enforces structurally: re-introducing
     * any AI-vs-human discriminator field fails to compile.
     */
    val pilotMission: PilotMission? = null,
    /**
     * fn-28.8 (G0 abort-takeoff foundation R12): ground-truth engine state.
     *
     * `true` for a normally-operating aircraft (the default for every spawn,
     * matching real-world fixture authoring: aircraft enter the sim with the
     * engine running). Flipped to `false` by the sim's
     * `handleEngineFailure(SimEvent.EngineFailure)` handler when the
     * instructor channel fires an engine-failure event (typed input
     * `InstructorInput.EngineFailureAt` in test fixtures).
     *
     * **Read by physics, not by cognition** (R12 round-3 Major 2 contract):
     * the `advanceKinematics` engine-off clamp reads this field directly —
     * when `engineRunning == false`, the new speed is bounded by
     * `min(targetSpeedMps, currentSpeedMps)` (decel allowed; accel blocked).
     * The pilot's cognitive layer does NOT read `aircraft.engineRunning`;
     * the engine-failure event is delivered to the pilot via a cockpit-side
     * observation in fn-28.9 (the abort recognition branch reads
     * `PilotEvent.EngineFailure` from `derivePilotEvent`, NOT this
     * ground-truth field). This is the same firewall shape as wind: ground
     * truth lives on the entity (`Aerodrome.weather`), cognition reads only
     * the typed observation projection (`WindReport`).
     *
     * **No synthetic wake event** (round-2 Major 4 decision): the sim does
     * NOT emit a `PilotDecisionTick` synthesised by `handleEngineFailure`.
     * The pilot's regular per-aircraft `PilotDecisionTick` cadence picks up
     * the engine-failure event from the queue (via the instructor channel
     * in fn-28.9) on the next scheduled tick. A synthetic wake-up would
     * couple the sim's event production to the pilot's decision cadence —
     * the same coupling the firewall plan deletes elsewhere.
     *
     * **Doctrine**: ground-truth physics fact (engine spinning or not);
     * authored by the sim, not by the pilot. Cf. POH §3.3 (engine-failure
     * procedure) — referenced in KDoc only; not modelled via RegDB
     * (excluded per task scope).
     */
    val engineRunning: Boolean = true,
)
