package xyz.easiersaid.twr.pilot

import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.RunwayId
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `@Ignore`d placeholder tests pinning eventual contracts for deferred work
 * in `:pilot`. See `docs/deferments.md` for the canonical register and
 * `docs/deferments-CONVENTION.md` for the four-bucket model.
 *
 * **Bucket discipline (per deferments-CONVENTION § 5):**
 * - Bucket 1 tests reference real current-API types/values so a rename
 *   breaks compile.
 * - Bucket 2 tests body is commented-out pseudo-code only — the API
 *   doesn't exist yet, and the comment names what API the implementer
 *   adds when uncommenting.
 *
 * When a deferment is picked up, the implementer flips `@Ignore` off,
 * uncomments / extends the body, and the test becomes a real verification
 * of the contract.
 */
class DeferredContractsSpec {

    /**
     * **D-PF.1** — airport-conditional startup clearance.
     *
     * When implemented:
     *  - `Aerodrome` (or its manifest-derived value) gains a
     *    `requiresStartupClearance: Boolean` field.
     *  - `groundDepartureTask(aerodrome)` returns a tree with
     *    `REQUEST_STARTUP` and `AWAIT_STARTUP_APPROVAL` iff the aerodrome
     *    requires it.
     *  - A new `CLEARANCE_DELIVERY` controller role and procedure issues
     *    `StartupApproved` in response to `Request(RequestStartup)`.
     *  - The mission tree branch is determined by aerodrome, never by
     *    cockpit type (that's the same-treatment principle).
     *
     * Bucket 2 — `Aerodrome.requiresStartupClearance` doesn't exist today;
     * `groundDepartureTask()` takes no aerodrome argument.
     */
    @Ignore
    @Test
    fun `PF1 aerodrome requiring startup clearance has REQUEST_STARTUP and AWAIT_STARTUP_APPROVAL`() {
        // Bucket 2: requires a new `Aerodrome.requiresStartupClearance: Boolean`
        // field + `groundDepartureTask(aerodrome)` overload + the
        // `CLEARANCE_DELIVERY` controller role.
        // TODO when D-PF.1 lands:
        //   val tree = groundDepartureTask(aerodrome = lowsAerodrome)  // requires startup
        //   assertContains(tree.steps, MissionStep.REQUEST_STARTUP)
        //   assertContains(tree.steps, MissionStep.AWAIT_STARTUP_APPROVAL)
        //   val noStartupTree = groundDepartureTask(aerodrome = lowgAerodrome)
        //   assertFalse(MissionStep.REQUEST_STARTUP in noStartupTree.steps)
    }

    /**
     * **D-PF.3** — airborne spawn has a runway-assignment path via FiledPlan.
     *
     * When implemented:
     *  - `FlightStrip.filed: FiledPlan?` carries `destinationRunway: RunwayId?`
     *    derived from filed approach / ATIS.
     *  - The pilot reads `mission.filedPlan?.destinationRunway` (set at sim
     *    init from the filing event) as the initial `activeRunway`;
     *    subsequent radio updates override per D-PF.2's precedence.
     *
     * Bucket 1 — `FiledPlan.destinationRunway` + `PilotMission.filedPlan`
     * already exist; the missing piece is the G2 (cross-aerodrome) scenario
     * exercising the path end-to-end.
     */
    @Ignore
    @Test
    fun `PF3 airborne-spawned aircraft with FiledPlan has activeRunway from filed plan`() {
        // Bucket 1: API exists today; this references `FiledPlan.Vfr` +
        // `destinationRunway` so a rename / signature shift breaks compile.
        val filed: FiledPlan = FiledPlan.Vfr(
            departureAerodrome = xyz.easiersaid.twr.protocol.AerodromeId("LJMB"),
            destinationAerodrome = xyz.easiersaid.twr.protocol.AerodromeId("LOWG"),
            intent = xyz.easiersaid.twr.protocol.AircraftIntent.Arriving,
            destinationRunway = RunwayId("16C"),
        )
        // Value-flow reference: the runway flows from the filed plan through
        // `destinationRunway` (the field `createMission` reads at sim init per
        // `PilotMission.kt:835`).
        assertEquals(RunwayId("16C"), filed.destinationRunway)
        assertNotNull(filed.destinationAerodrome)
        // TODO when D-PF.3 lands fully (G2 cross-aerodrome scenario):
        //   val mission = createMission(
        //       goal = HighLevelGoal.Arrival(from = AerodromeId("LJMB")),
        //       startPhase = PilotPhase.Climbing,
        //       time = SimTime.zero,
        //       filedPlan = filed,
        //   )
        //   val active = mission.activeRunway
        //   assertTrue(active is Some, "airborne spawn with filed plan must have activeRunway")
        //   assertEquals(RunwayId("16C"), active.value.runway)
    }

    /**
     * **D-AUDIT.3.II-FOLLOWUP** — per-step TIMED durations beyond
     * RUN_UP_CHECKS.
     *
     * When implemented:
     *  - `MissionStep.runUpDurationMs(type)` (or a step-discriminated map
     *    on `AircraftType`) provides per-step TIMED durations.
     *  - `PilotCognitive.isStepComplete`'s TIMED arm dispatches on the
     *    step, not just the type.
     *
     * Bucket 2 — today only `RUN_UP_CHECKS` uses TIMED; the lookup-by-step
     * surface doesn't exist.
     */
    @Ignore
    @Test
    fun `AUDIT3-II MissionStep runUpDurationMs lookup is step-discriminated`() {
        // Bucket 2: no `MissionStep.runUpDurationMs(type)` lookup today.
        // TODO when D-AUDIT.3.II-FOLLOWUP lands:
        //   val runUp = MissionStep.RUN_UP_CHECKS.runUpDurationMs(c172Type)
        //   val checks2 = MissionStep.BEFORE_TAKEOFF_CHECKS.runUpDurationMs(c172Type)
        //   assertNotEquals(runUp, checks2)  // proves per-step discrimination
    }

    /**
     * **D-AUDIT.4.D.II-FOLLOWUP** — per-phase waypoint radius scaling
     * (taxi vs climb vs final).
     *
     * When implemented:
     *  - `Kinematics.waypointRadiusM` (single scalar today) becomes
     *    phase-discriminated: `taxiWaypointRadiusM`, `climbWaypointRadiusM`,
     *    `finalWaypointRadiusM` (or sealed phase-keyed accessor).
     *  - Read sites in `PilotRoutePlanner`, `Pilot`, `PilotAgent` pick the
     *    phase-appropriate value.
     *
     * Bucket 2 — `Kinematics.waypointRadiusM` is a single scalar today;
     * per-phase fields don't exist.
     */
    @Ignore
    @Test
    fun `AUDIT4-D-II Kinematics carries per-phase waypoint radii`() {
        // Bucket 2: today `Kinematics.waypointRadiusM` is one scalar.
        // TODO when D-AUDIT.4.D.II-FOLLOWUP lands:
        //   val kin = c172Type.kinematics
        //   assertTrue(kin.taxiWaypointRadiusM < kin.finalWaypointRadiusM)
        //   assertTrue(kin.taxiWaypointRadiusM in 5.0..15.0)  // realistic low-speed value
    }

    /**
     * **D-AUDIT.8.IV-FOLLOWUP** — multi-aerodrome ATIS-letter resolution.
     *
     * When implemented:
     *  - `pilotResolveAtisLetter` reads `mission.filedPlan?.arrivalAerodrome`
     *    (or current-leg aerodrome) to pick the right ATIS entry when the
     *    pilot inputs carries multiple aerodromes.
     *  - The size>1 fallback (today returns `null` — see
     *    `PilotCognitive.kt:488`) is replaced by aerodrome-keyed lookup.
     *
     * Bucket 1 — `pilotResolveAtisLetter` already exists with explicit
     * "drop to null at size > 1" branch; the fix is to thread the target
     * aerodrome and dispatch on it.
     */
    @Ignore
    @Test
    fun `AUDIT8-IV ATIS letter resolution dispatches by aerodrome for size greater than one`() {
        // Bucket 1: `PilotInput.atisByAerodrome` is a real public field today;
        // this references its type so a shape change breaks compile.
        val lowgAtis = stubAtis(letter = 'A', aerodrome = xyz.easiersaid.twr.protocol.AerodromeId("LOWG"))
        val ljmbAtis = stubAtis(letter = 'B', aerodrome = xyz.easiersaid.twr.protocol.AerodromeId("LJMB"))
        val map: Map<xyz.easiersaid.twr.protocol.AerodromeId, xyz.easiersaid.twr.protocol.Atis> =
            mapOf(lowgAtis.aerodrome to lowgAtis, ljmbAtis.aerodrome to ljmbAtis)
        // Value-flow: the type IS the documented gap — today multi-aerodrome
        // resolution drops to null in the private `atisLetterForCallInbound`
        // helper at `PilotCognitive.kt:488` for `HighLevelGoal.Arrival` /
        // `CircuitTraining` (no goal-derived destination). The fix dispatches
        // on the target aerodrome.
        assertEquals(2, map.size)
        assertEquals('A', map.getValue(lowgAtis.aerodrome).letter)
        assertEquals('B', map.getValue(ljmbAtis.aerodrome).letter)
        // TODO when D-AUDIT.8.IV-FOLLOWUP lands:
        //   exposes a public `pilotResolveAtisLetter(map, target)` helper
        //   (or the resolution gets threaded through `PilotInput` such that
        //   `Arrival` goals can resolve their target without crashing).
        //     val resolved = pilotResolveAtisLetter(map, target = lowgAtis.aerodrome)
        //     assertEquals('A', resolved)
    }

    /**
     * **D-AUDIT.9.II-FOLLOWUP** — VFR see-and-avoid recognises nearby
     * traffic and yields right of way.
     *
     * When implemented:
     *  - `PilotInput.nearbyTraffic: List<NearbyAircraft>` field carries
     *    the pilot's outside-the-window observation.
     *  - `derivePilotEvent(aircraft, mission, input.nearbyTraffic)`
     *    recognises conflicts and emits `PilotEvent.NearbyTrafficConflict`.
     *  - `applyNearbyTrafficYield` updates the mission tree per CAP 393
     *    Rule 9 right-of-way doctrine.
     *
     * Bucket 2 — `PilotInput.nearbyTraffic` doesn't exist; today the
     * pilot sees the world only via the controller's frequency.
     */
    @Ignore
    @Test
    fun `AUDIT9-II VFR see-and-avoid recognises nearby traffic and yields right of way`() {
        // Bucket 2: requires `PilotInput.nearbyTraffic` field on the cockpit
        // input surface.
        // TODO when D-AUDIT.9.II-FOLLOWUP lands:
        //   val event = derivePilotEvent(aircraft, mission, input.nearbyTraffic)
        //   assertIs<PilotEvent.NearbyTrafficConflict>(event)
    }

    /**
     * **D-AUDIT.9.III-FOLLOWUP** — abort takeoff on engine failure.
     *
     * When implemented:
     *  - `AircraftState.engineState: EngineState` (sealed
     *    `Normal | LowPower(rpm) | Failed(at)`) field.
     *  - `derivePilotEvent` emits `AbortedTakeoff(reason)` when
     *    `engineState != Normal` during takeoff-roll phase.
     *  - V1/Vr decision gates on `engineState`.
     *
     * Bucket 2 — `AircraftState.engineState` doesn't exist.
     */
    @Ignore
    @Test
    fun `AUDIT9-III aborted takeoff on engine failure during takeoff roll`() {
        // Bucket 2: needs `AircraftState.engineState` (sealed type).
        // TODO when D-AUDIT.9.III-FOLLOWUP lands:
        //   val state = aircraftState.copy(engineState = EngineState.Failed(at = SimTime.zero))
        //   val event = derivePilotEvent(state, takeoffRollMission)
        //   assertIs<PilotEvent.AbortedTakeoff>(event)
    }

    /**
     * **D-AUDIT.9.IV-FOLLOWUP** — fuel exhaustion / divert.
     *
     * When implemented:
     *  - `AircraftState.fuelKg: Double` field + per-type fuel-burn rate.
     *  - `derivePilotEvent` emits `LowFuelDivert(alternateAerodrome)`
     *    when reserve threshold breached.
     *  - `alternateAerodrome` populated from filed plan or computed at
     *    decision time.
     *
     * Bucket 2 — `AircraftState.fuelKg` doesn't exist.
     */
    @Ignore
    @Test
    fun `AUDIT9-IV fuel exhaustion triggers divert to alternate`() {
        // Bucket 2: needs `AircraftState.fuelKg` + alternate-aerodrome diversion logic.
        // TODO when D-AUDIT.9.IV-FOLLOWUP lands:
        //   val state = aircraftState.copy(fuelKg = 5.0)  // below reserve
        //   val event = derivePilotEvent(state, enRouteMission)
        //   assertIs<PilotEvent.LowFuelDivert>(event)
    }

    /**
     * **D-AUDIT.9.V-FOLLOWUP** — icing / weather deviation.
     *
     * When implemented:
     *  - `AviationWorld.weatherVolumes: List<WeatherVolume>` field
     *    carries icing, turbulence, convection regions.
     *  - `derivePilotEvent` emits `WeatherDeviation(volume)` when the
     *    mission path crosses a hazardous volume.
     *  - `applyWeatherDeviation` replans around the volume.
     *
     * Bucket 2 — `AviationWorld.weatherVolumes` doesn't exist.
     */
    @Ignore
    @Test
    fun `AUDIT9-V icing or weather deviation replans around hazardous volume`() {
        // Bucket 2: needs `AviationWorld.weatherVolumes`.
        // TODO when D-AUDIT.9.V-FOLLOWUP lands:
        //   val world = aviationWorld.copy(weatherVolumes = listOf(icingVolume))
        //   val event = derivePilotEvent(state, enRouteMission, world)
        //   assertIs<PilotEvent.WeatherDeviation>(event)
    }

    /**
     * Test helper: a stub `Atis` with required defaults.
     * Used by bucket-1 D-AUDIT.8.IV-FOLLOWUP test above.
     */
    private fun stubAtis(
        letter: Char,
        aerodrome: xyz.easiersaid.twr.protocol.AerodromeId,
    ): xyz.easiersaid.twr.protocol.Atis =
        xyz.easiersaid.twr.protocol.Atis(
            letter = letter,
            aerodrome = aerodrome,
            configuration = xyz.easiersaid.twr.protocol.RunwayConfiguration(
                arrivals = listOf(RunwayId("16C")),
                departures = listOf(RunwayId("16C")),
            ),
            wind = xyz.easiersaid.twr.protocol.Wind.unsafe(directionDegrees = 160, speedKnots = 8),
            qnh = null,
            visibility = null,
            generatedAt = xyz.easiersaid.twr.protocol.SimTime.ZERO,
        )
}
