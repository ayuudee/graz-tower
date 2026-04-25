package xyz.easiersaid.twr.sim.g1

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.WindReport
import xyz.easiersaid.twr.controller.controllerDecide
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.AircraftState
import xyz.easiersaid.twr.sim.ControllerSpec
import xyz.easiersaid.twr.sim.HighLevelGoal
import xyz.easiersaid.twr.sim.PilotMission
import xyz.easiersaid.twr.sim.PilotPhase
import xyz.easiersaid.twr.sim.PilotRoute
import xyz.easiersaid.twr.sim.SimEvent
import xyz.easiersaid.twr.sim.SimState
import xyz.easiersaid.twr.sim.buildControllerView
import xyz.easiersaid.twr.sim.planMission
import xyz.easiersaid.twr.sim.runUntil

/**
 * G1.6 — outbound integration test, LOWG → LJMB VFR cross-aerodrome.
 *
 * Builds the proof-of-concept fixture: merged LOWG+LJMB world, three
 * controllers (LOWG_TWR, LJMB_APP, LJMB_TWR), one aircraft at LOWG
 * stand on a [HighLevelGoal.VfrCrossAerodromeTransit] mission.
 *
 * This first slice proves the **scaffold runs end-to-end at P0** —
 * world loads, three controllers cohabit, smart-constructor accepts
 * the configuration, both towers' active-runway selection works
 * through the threaded south wind, and the aircraft is in
 * `PilotPhase.AtStand` with the cross-aerodrome mission tree present.
 *
 * Subsequent slices (P1–P12) wire ground-departure → climb-out →
 * FIS transit → TMA entry (pilot-initiated contact) → APP/TWR
 * handoff → landing → vacate → taxi-to-stand.
 */
class G1OutboundLowgToLjmbTest {

    // ── IDs ────────────────────────────────────────────────────────────

    private val LOWG = AerodromeId("LOWG")
    private val LJMB = AerodromeId("LJMB")
    private val LOWG_GND_ID = ControllerId("LOWG_GND")
    private val LOWG_TWR_ID = ControllerId("LOWG_TWR")
    private val LJMB_APP_ID = ControllerId("LJMB_APP")
    private val LJMB_TWR_ID = ControllerId("LJMB_TWR")
    private val LJMB_GND_ID = ControllerId("LJMB_GND")
    private val ALPHA = AircraftId("ALPHA")

    // Ground frequencies are placeholders — apt.dat has no ground row for
    // either LOWG or LJMB. AIP Austria has LOWG apron on 121.7; LJMB is
    // combined per atc-general v1 review. Synthetic frequencies here keep
    // the test architecturally clean (separated GROUND vs TOWER per the
    // existing controller pattern).
    private val LOWG_GND_FREQ = xyz.easiersaid.twr.protocol.Frequency.unsafe("121.700")
    private val LJMB_GND_FREQ = xyz.easiersaid.twr.protocol.Frequency.unsafe("121.800")

    private val LOWG_STAND_POINT = PointId("LOWG_STAND_1_POINT")

    // ── Fixture builder ───────────────────────────────────────────────

    private data class Fixture(
        val state: SimState,
        val initialEvents: List<SimEvent>,
    )

    /**
     * Build the initial [SimState] + initial events for the outbound G1 scenario.
     *
     * - Merged LOWG+LJMB world (G1-DEF-11 reprojection in effect).
     * - South wind seeded for both aerodromes — `selectRunwayIntoWind`
     *   picks LOWG 16C and LJMB 14.
     * - Three controllers; LOWG_TWR initially holds the aircraft's
     *   responsibility on Spawn.
     * - One pilot-side trigger: LJMB APP contact at PETOV with 5 NM lead.
     * - Aircraft spawned via [SimEvent.Spawn] at LOWG_STAND_1 with a
     *   [HighLevelGoal.VfrCrossAerodromeTransit] mission, AI-piloted.
     */
    private fun buildFixture(): Fixture {
        val world = G1Fixtures.loadMergedLowgLjmb()
        val worldIndex = G1Fixtures.fullIndex(world)

        val southWind = Wind.unsafe(directionDegrees = 180, speedKnots = 8)
        val weather = world.aerodromes.keys.associateWith {
            WeatherObservation(wind = WindReport.Available(southWind), qnh = null, visibility = null)
        }

        val lowgGnd = ControllerSpec(
            id = LOWG_GND_ID,
            role = RoleName.GROUND,
            aerodromeId = LOWG,
            frequency = LOWG_GND_FREQ,
            responsibilities = setOf(ALPHA),  // ground holds the aircraft pre-takeoff
        )
        val lowgTwr = ControllerSpec(
            id = LOWG_TWR_ID,
            role = RoleName.TOWER,
            aerodromeId = LOWG,
            frequency = G1Fixtures.LOWG_TWR_FREQ,
            responsibilities = emptySet(),
        )
        val ljmbApp = ControllerSpec(
            id = LJMB_APP_ID,
            role = RoleName.APPROACH,
            aerodromeId = LJMB,
            frequency = G1Fixtures.LJMB_APP_FREQ,
            responsibilities = emptySet(),
        )
        val ljmbTwr = ControllerSpec(
            id = LJMB_TWR_ID,
            role = RoleName.TOWER,
            aerodromeId = LJMB,
            frequency = G1Fixtures.LJMB_TWR_FREQ,
            responsibilities = emptySet(),
        )
        val ljmbGnd = ControllerSpec(
            id = LJMB_GND_ID,
            role = RoleName.GROUND,
            aerodromeId = LJMB,
            frequency = LJMB_GND_FREQ,
            responsibilities = emptySet(),
        )

        val state = SimState.initial(
            seed = 0L,
            world = world,
            worldIndex = worldIndex,
            aircraft = emptyList(),  // spawned via Spawn event below
            controllers = listOf(lowgGnd, lowgTwr, ljmbApp, ljmbTwr, ljmbGnd),
            weatherByAerodrome = weather,
            airspaceTriggers = listOf(G1Fixtures.LJMB_TMA_TRIGGER),
        ).getOrElse { error("G1.6 fixture invalid: $it") }

        val alpha = buildAlpha(worldIndex)
        val initialEvents = listOf(
            SimEvent.PhysicsTick(SimTime.ZERO),
            SimEvent.Spawn(SimTime.ZERO, alpha),
            SimEvent.ControllerCycle(SimTime.ZERO, LOWG_GND_ID),
            SimEvent.ControllerCycle(SimTime.ZERO, LOWG_TWR_ID),
            SimEvent.ControllerCycle(SimTime.ZERO, LJMB_APP_ID),
            SimEvent.ControllerCycle(SimTime.ZERO, LJMB_TWR_ID),
            SimEvent.ControllerCycle(SimTime.ZERO, LJMB_GND_ID),
        )
        return Fixture(state, initialEvents)
    }

    private fun buildAlpha(worldIndex: WorldIndex): AircraftState {
        val standPos = worldIndex.positions[LOWG_STAND_POINT]
            ?: error("LOWG_STAND_1_POINT not found in merged world. Stand ID drifted?")
        val goal = HighLevelGoal.VfrCrossAerodromeTransit(
            from = LOWG,
            to = LJMB,
            tmaEntry = G1Fixtures.PETOV,
            ctrEntry = G1Fixtures.MN1,
            ctrCorridorWaypoints = listOf(G1Fixtures.MN2),
            joinLeg = LegName.BASE,
        )
        val mission = PilotMission(
            goal = goal,
            root = planMission(goal, humanPiloted = false),
            stepEnteredAt = SimTime.ZERO,
        )
        return AircraftState(
            id = ALPHA,
            callsign = Callsign("OE-ALPHA"),
            position = standPos,
            positionPoint = LOWG_STAND_POINT,
            phase = PilotPhase.AtStand,
            pilotGoal = PilotGoal.DEPART,
            humanPiloted = false,
            route = PilotRoute.None,
            pilotMission = mission,
        )
    }

    // ── P0 — scaffold proves the fixture path is sound ────────────────

    @Test
    fun `P0 — scaffold loads, three controllers cohabit, both towers select active runways from wind`() {
        val (state, _) = buildFixture()

        // Five controllers seated (separated GROUND/TOWER per aerodrome
        // plus LJMB APPROACH).
        assertEquals(
            setOf(LOWG_GND_ID, LOWG_TWR_ID, LJMB_APP_ID, LJMB_TWR_ID, LJMB_GND_ID),
            state.controllers.keys,
            "Five controllers must coexist in one SimState.",
        )

        // LOWG_GND initially holds the aircraft (departure starts on ground;
        // GND→TWR handoff happens at the holding-short point).
        assertTrue(ALPHA in state.controllers.getValue(LOWG_GND_ID).responsibilities,
            "LOWG_GND must initially hold the aircraft.")

        // Both towers select active runways from the seeded south wind.
        val lowgBeliefs = controllerDecide(
            buildControllerView(state, LOWG_TWR_ID),
            BeliefState.EMPTY,
            state.world,
        ).updatedBeliefs
        val ljmbBeliefs = controllerDecide(
            buildControllerView(state, LJMB_TWR_ID),
            BeliefState.EMPTY,
            state.world,
        ).updatedBeliefs
        assertEquals(RunwayId("16C"), lowgBeliefs.activeRunway,
            "South wind → LOWG TWR active runway = 16C.")
        assertEquals(RunwayId("14"), ljmbBeliefs.activeRunway,
            "South wind → LJMB TWR active runway = 14.")
    }

    @Test
    fun `P1 — aircraft starts taxiing within 5 sim seconds (post G1-DEF-23 wiring)`() {
        // P1 baseline: with the production WorldIndex builder wired
        // (`G1Fixtures.fullIndex(world)`), the GND controller has the
        // adjacency graph it needs to issue a TaxiTo. Aircraft must
        // transition out of [PilotPhase.AtStand] within 5 sim seconds.
        val (state, events) = buildFixture()
        val result = runUntil(state, events, SimTime.ofSeconds(5))
        val alpha = result.aircraft.getValue(ALPHA)
        assertTrue(
            alpha.phase != PilotPhase.AtStand,
            "G1-DEF-23 wired: aircraft should have started moving within 5 sim seconds. " +
                "Currently at phase=${alpha.phase}, position=${alpha.position}, " +
                "step=${alpha.pilotMission?.currentTask?.step}",
        )
    }

    @Test
    fun `P2 — aircraft reaches HoldingShort within 2 sim minutes`() {
        // P2: ground taxi completes. The full taxi from LOWG_STAND_1 to
        // a holding short involves the GND controller issuing TaxiTo and
        // the kinematic pilot tracking the route. Generous budget (2 min)
        // because the LOWG ground graph is large.
        val (state, events) = buildFixture()
        val result = runUntil(state, events, SimTime.ofSeconds(120))
        val alpha = result.aircraft.getValue(ALPHA)
        assertEquals(
            PilotPhase.HoldingShort, alpha.phase,
            "After 2 sim minutes, aircraft should have reached the holding short " +
                "for RWY 16C. Currently phase=${alpha.phase}, " +
                "step=${alpha.pilotMission?.currentTask?.step}, " +
                "position=${alpha.position}",
        )
    }

    // ── P3+ (TODO) ──────────────────────────────────────────────────────
    //
    // Next phase: GND→TWR handoff at the holding short, then TWR issues
    // LineUpAndWait + ClearedForTakeoff, aircraft becomes airborne.
    //
    // Diagnostic test (commented out, retained as the resumption point):
    // after 3 sim minutes the aircraft reaches HoldingShort + reports ready,
    // but `LOWG_GND` still holds responsibility — the GND-HANDOFF rule isn't
    // firing. Likely causes (to investigate when resuming):
    //  - Commitment.runway not set on the GND commitment (no TaxiTo carries
    //    the runway through), so `AtHoldingPoint.evaluate` falls back to
    //    `ctx.beliefs.activeRunway` — verify that's set to 16C.
    //  - Aircraft's `positionPoint` may not match any of the holding points
    //    in `worldIndex.holdingPointsByRunway[16C]`. The GND TaxiTo target
    //    may pick a *different* runway's holding short.
    //  - The cross-aerodrome HTN's `groundDepartureTask(humanPiloted=false)`
    //    might differ in step ordering from the single-airport variant.
    //
    // Use a print-on-failure scenario to inspect commitment + position:
    //
    //   val result = runUntil(state, events, SimTime.ofSeconds(180))
    //   val gndBeliefs = result.beliefs[LOWG_GND_ID]!!
    //   val commitment = gndBeliefs.commitments[ALPHA]
    //   println("commitment.runway = ${commitment?.runway}")
    //   println("activeRunway = ${gndBeliefs.activeRunway}")
    //   println("aircraft.positionPoint = ${result.aircraft[ALPHA]!!.positionPoint}")
    //   println("holdingPointsByRunway[16C] = ${state.worldIndex.holdingPointsByRunway[RunwayId(\"16C\")]}")
}
