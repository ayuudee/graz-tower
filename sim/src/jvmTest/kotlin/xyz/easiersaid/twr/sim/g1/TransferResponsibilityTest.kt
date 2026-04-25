package xyz.easiersaid.twr.sim.g1

import arrow.core.None
import arrow.core.Some
import arrow.core.getOrElse
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.WindReport
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.migration.world.WorldCandidateLoader
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.sim.AircraftState
import xyz.easiersaid.twr.sim.ControllerSpec
import xyz.easiersaid.twr.sim.HighLevelGoal
import xyz.easiersaid.twr.sim.PilotAirspace
import xyz.easiersaid.twr.sim.PilotMission
import xyz.easiersaid.twr.sim.PilotPhase
import xyz.easiersaid.twr.sim.PilotRoute
import xyz.easiersaid.twr.sim.SimState
import xyz.easiersaid.twr.sim.TransferError
import xyz.easiersaid.twr.sim.pilotInitiatedContactTrigger
import xyz.easiersaid.twr.sim.planMission
import xyz.easiersaid.twr.sim.releaseResponsibility
import xyz.easiersaid.twr.sim.transferResponsibility

/**
 * G1.5 — behavioural tests for the [transferResponsibility] primitive
 * and the [pilotInitiatedContactTrigger] predicate.
 *
 * These cover the primitives that the cross-aerodrome handoff path will
 * use. End-to-end wiring (cognitive emit → step apply) is part of G1.6,
 * which is gated by G1-DEF-7 (typed wind) and G1-DEF-11 (geometric frame).
 */
class TransferResponsibilityTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val LJMB = AerodromeId("LJMB")
    private val LJMB_APP_ID = ControllerId("LJMB_APP")
    private val LJMB_APP_FREQ = Frequency.unsafe("134.305")
    private val LJMB_TWR_ID = ControllerId("LJMB_TWR")
    private val LJMB_TWR_FREQ = Frequency.unsafe("119.205")
    private val LOWG = AerodromeId("LOWG")
    private val LOWG_TWR_ID = ControllerId("LOWG_TWR")
    private val LOWG_TWR_FREQ = Frequency.unsafe("118.200")

    private val ALPHA = AircraftId("ALPHA")
    private val PETOV = FixId("PETOV")
    private val MN1 = FixId("MN1")

    @Test
    fun `transferResponsibility plants target when controller exists`() {
        val state = ljmbStateWithUnownedAircraft()
        val after = transferResponsibility(
            state = state,
            ac = ALPHA,
            toAerodrome = LJMB,
            toRole = RoleName.APPROACH,
        ).getOrElse { error("Transfer should have succeeded: $it") }
        assertTrue(
            ALPHA in after.controllers.getValue(LJMB_APP_ID).responsibilities,
            "After transfer, LJMB_APP should hold the aircraft.",
        )
        assertTrue(
            ALPHA !in after.controllers.getValue(LJMB_TWR_ID).responsibilities,
            "LJMB_TWR should not gain the aircraft.",
        )
    }

    @Test
    fun `transferResponsibility returns TargetUnresolved when no controller matches`() {
        // Strict semantics (round-3 impact-review fix): a typo'd or
        // unmodelled role does not silently strip responsibility — it
        // returns a typed error. The caller decides what to do.
        val state = lowgStateWithOwnedAircraft()
        val result = transferResponsibility(
            state = state,
            ac = ALPHA,
            toAerodrome = LOWG,
            toRole = RoleName.APPROACH,
        )
        assertTrue(result.isLeft(),
            "Strict transfer must surface a typed error when target is absent.")
        val err = result.swap().getOrNull()!!
        assertTrue(err is TransferError.TargetUnresolved,
            "Expected TargetUnresolved, got ${err::class.simpleName}")
    }

    @Test
    fun `releaseResponsibility drops the aircraft cleanly`() {
        // Explicit "release into the void" — the LOWG → FIS case where no
        // FIS controller is modelled. The caller calls release explicitly.
        val state = lowgStateWithOwnedAircraft()
        val after = releaseResponsibility(state = state, ac = ALPHA)
        val owners = after.controllers.values.filter { ALPHA in it.responsibilities }
        assertEquals(emptyList(), owners,
            "After releaseResponsibility, no controller should hold the aircraft.")
    }

    @Test
    fun `transferResponsibility moves responsibility across aerodromes`() {
        val state = lowgAndLjmbState()
        val after = transferResponsibility(
            state = state,
            ac = ALPHA,
            toAerodrome = LJMB,
            toRole = RoleName.APPROACH,
        ).getOrElse { error("Cross-aerodrome transfer should have succeeded: $it") }
        assertTrue(
            ALPHA !in after.controllers.getValue(LOWG_TWR_ID).responsibilities,
            "LOWG_TWR should release the aircraft.",
        )
        assertTrue(
            ALPHA in after.controllers.getValue(LJMB_APP_ID).responsibilities,
            "LJMB_APP at a different aerodrome should pick up the aircraft.",
        )
    }

    // ── pilotInitiatedContactTrigger predicate ──────────────────────────

    @Test
    fun `pilotInitiatedContactTrigger fires for cross-aerodrome mission near PETOV when uncontrolled`() {
        val state = ljmbStateWithUnownedAircraft()
        val petov = state.worldIndex.positions.getValue(PointId("LJMB_FIX_PETOV"))
        val tmaCentroid = tmaCentroid(state)
        val outwardX = petov.xMeters - tmaCentroid.xMeters
        val outwardY = petov.yMeters - tmaCentroid.yMeters
        val outwardLen = kotlin.math.hypot(outwardX, outwardY)
        val nearPetov = Position(
            xMeters = petov.xMeters + outwardX / outwardLen * 2.0 * 1852.0,
            yMeters = petov.yMeters + outwardY / outwardLen * 2.0 * 1852.0,
        )
        val aircraft = airborneAircraft(nearPetov)
        val mission = crossAerodromeMission()
        val triggers = listOf(LJMB_TMA_TRIGGER)

        val fired = pilotInitiatedContactTrigger(aircraft, mission, state, triggers)
        assertTrue(fired is Some,
            "Trigger must fire when airborne, uncontrolled, on a cross-aerodrome mission, near PETOV.")
        assertEquals(LJMB_APP_FREQ, fired.value.targetFrequency)
        assertEquals(RoleName.APPROACH, fired.value.targetRole)
        assertEquals(LJMB, fired.value.targetAerodrome)
    }

    @Test
    fun `pilotInitiatedContactTrigger does not fire while a controller already holds the aircraft`() {
        // Currently-controlled guard — geometric "outside TMA" is satisfied
        // but the aircraft is owned (e.g., not yet released by LOWG_TWR), so
        // the trigger must not fire.
        val state = ljmbStateWithAircraftOwnedBy(LJMB_TWR_ID)
        val petov = state.worldIndex.positions.getValue(PointId("LJMB_FIX_PETOV"))
        val tmaCentroid = tmaCentroid(state)
        val outwardX = petov.xMeters - tmaCentroid.xMeters
        val outwardY = petov.yMeters - tmaCentroid.yMeters
        val outwardLen = kotlin.math.hypot(outwardX, outwardY)
        val nearPetov = Position(
            xMeters = petov.xMeters + outwardX / outwardLen * 2.0 * 1852.0,
            yMeters = petov.yMeters + outwardY / outwardLen * 2.0 * 1852.0,
        )
        val aircraft = airborneAircraft(nearPetov)
        val mission = crossAerodromeMission()
        val fired = pilotInitiatedContactTrigger(aircraft, mission, state, listOf(LJMB_TMA_TRIGGER))
        assertEquals(None, fired,
            "Trigger must not fire while any controller still owns the aircraft.")
    }

    @Test
    fun `pilotInitiatedContactTrigger does not fire on the ground`() {
        val state = ljmbStateWithUnownedAircraft()
        val petov = state.worldIndex.positions.getValue(PointId("LJMB_FIX_PETOV"))
        val onGroundAircraft = AircraftState(
            id = ALPHA,
            callsign = Callsign("ALPHA"),
            position = petov,
            positionPoint = PointId("LJMB_FIX_PETOV"),
            phase = PilotPhase.AtStand,  // ground phase
            pilotGoal = PilotGoal.DEPART,
            humanPiloted = false,
            route = PilotRoute.None,
        )
        val mission = crossAerodromeMission()
        val fired = pilotInitiatedContactTrigger(onGroundAircraft, mission, state, listOf(LJMB_TMA_TRIGGER))
        assertEquals(None, fired, "Trigger must not fire on the ground.")
    }

    @Test
    fun `pilotInitiatedContactTrigger does not fire for non-cross-aerodrome missions`() {
        val state = ljmbStateWithUnownedAircraft()
        val petov = state.worldIndex.positions.getValue(PointId("LJMB_FIX_PETOV"))
        val tmaCentroid = tmaCentroid(state)
        val outwardX = petov.xMeters - tmaCentroid.xMeters
        val outwardY = petov.yMeters - tmaCentroid.yMeters
        val outwardLen = kotlin.math.hypot(outwardX, outwardY)
        val nearPetov = Position(
            xMeters = petov.xMeters + outwardX / outwardLen * 2.0 * 1852.0,
            yMeters = petov.yMeters + outwardY / outwardLen * 2.0 * 1852.0,
        )
        val aircraft = airborneAircraft(nearPetov)
        val arrivalGoal = HighLevelGoal.Arrival(from = LOWG)
        val arrivalMission = PilotMission(goal = arrivalGoal, root = planMission(arrivalGoal, humanPiloted = false))
        val fired = pilotInitiatedContactTrigger(aircraft, arrivalMission, state, listOf(LJMB_TMA_TRIGGER))
        assertEquals(None, fired,
            "Trigger must not fire for a single-aerodrome Arrival mission (the controller-initiated handoff path is correct for those).")
    }

    // ── Fixtures ────────────────────────────────────────────────────────

    private fun ljmbStateWithUnownedAircraft(): SimState {
        val world = loadLjmbWorld()
        val worldIndex = WorldIndex(positions = world.geometry.points)
        val twr = ControllerSpec(LJMB_TWR_ID, RoleName.TOWER, LJMB, LJMB_TWR_FREQ, emptySet())
        val app = ControllerSpec(LJMB_APP_ID, RoleName.APPROACH, LJMB, LJMB_APP_FREQ, emptySet())
        return SimState.initial(
            seed = 0L,
            world = world,
            worldIndex = worldIndex,
            controllers = listOf(app, twr),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = WindReport.NotReported, qnh = null, visibility = null)
            },
        ).getOrElse { error("Fixture invalid: $it") }
    }

    private fun ljmbStateWithAircraftOwnedBy(owner: ControllerId): SimState {
        val base = ljmbStateWithUnownedAircraft()
        val withOwner = base.controllers.mapValues { (id, spec) ->
            if (id == owner) spec.copy(responsibilities = spec.responsibilities + ALPHA) else spec
        }
        return base.copy(controllers = withOwner)
    }

    private fun lowgStateWithOwnedAircraft(): SimState {
        val world = loadLowgWorld()
        val worldIndex = WorldIndex(positions = world.geometry.points)
        val twr = ControllerSpec(LOWG_TWR_ID, RoleName.TOWER, LOWG, LOWG_TWR_FREQ, setOf(ALPHA))
        return SimState.initial(
            seed = 0L,
            world = world,
            worldIndex = worldIndex,
            controllers = listOf(twr),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = WindReport.NotReported, qnh = null, visibility = null)
            },
        ).getOrElse { error("Fixture invalid: $it") }
    }

    private fun lowgAndLjmbState(): SimState {
        // Cross-aerodrome state. LOWG_TWR holds the aircraft initially.
        val lowgWorld = loadLowgWorld()
        val ljmbWorld = loadLjmbWorld()
        val world = WorldCandidateLoader.mergeAviationWorlds(listOf(lowgWorld, ljmbWorld))
        val worldIndex = WorldIndex(positions = world.geometry.points)
        val lowgTwr = ControllerSpec(LOWG_TWR_ID, RoleName.TOWER, LOWG, LOWG_TWR_FREQ, setOf(ALPHA))
        val ljmbTwr = ControllerSpec(LJMB_TWR_ID, RoleName.TOWER, LJMB, LJMB_TWR_FREQ, emptySet())
        val ljmbApp = ControllerSpec(LJMB_APP_ID, RoleName.APPROACH, LJMB, LJMB_APP_FREQ, emptySet())
        return SimState.initial(
            seed = 0L,
            world = world,
            worldIndex = worldIndex,
            controllers = listOf(lowgTwr, ljmbApp, ljmbTwr),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = WindReport.NotReported, qnh = null, visibility = null)
            },
        ).getOrElse { error("Fixture invalid: $it") }
    }

    private fun loadLjmbWorld(): AviationWorld {
        val ljmbPath = resolveProjectRoot().resolve("cad/airports/rendered/ljmb/world-candidate.json")
        return WorldCandidateLoader.toWorld(
            json.decodeFromString<WorldCandidateDocument>(java.nio.file.Files.readString(ljmbPath))
        )
    }

    private fun loadLowgWorld(): AviationWorld {
        val lowgPath = resolveProjectRoot().resolve("cad/airports/rendered/lowg/world-candidate.json")
        return WorldCandidateLoader.toWorld(
            json.decodeFromString<WorldCandidateDocument>(java.nio.file.Files.readString(lowgPath))
        )
    }

    private fun tmaCentroid(state: SimState): Position {
        val tma = state.world.airspace.getValue(AirspaceVolumeId("LJMB_OPENAIR_TMA_MARIBOR_1"))
        val ring = tma.boundary!!.rings.first().points.mapNotNull { state.worldIndex.positions[it] }
        val cx = ring.map { it.xMeters }.average()
        val cy = ring.map { it.yMeters }.average()
        return Position(xMeters = cx, yMeters = cy)
    }

    private fun airborneAircraft(at: Position): AircraftState = AircraftState(
        id = ALPHA,
        callsign = Callsign("ALPHA"),
        position = at,
        positionPoint = PointId("LJMB_FIX_PETOV"),
        phase = PilotPhase.Climbing,  // airborne
        pilotGoal = PilotGoal.TRANSIT,
        humanPiloted = false,
        route = PilotRoute.None,
    )

    private fun crossAerodromeMission(): PilotMission {
        val goal = HighLevelGoal.VfrCrossAerodromeTransit(
            from = LOWG,
            to = LJMB,
            tmaEntry = PETOV,
            ctrEntry = MN1,
            joinLeg = LegName.BASE,
        )
        return PilotMission(goal = goal, root = planMission(goal, humanPiloted = false))
    }

    private fun resolveProjectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val direct = cwd.resolve("settings.gradle.kts")
        return if (java.nio.file.Files.exists(direct)) cwd else cwd.parent ?: cwd
    }

    companion object {
        private val LJMB_TMA_TRIGGER = PilotAirspace.FrequencyChangeTrigger(
            triggerPoint = PointId("LJMB_FIX_PETOV"),
            targetVolume = AirspaceVolumeId("LJMB_OPENAIR_TMA_MARIBOR_1"),
            targetRole = RoleName.APPROACH,
            targetFrequency = Frequency.unsafe("134.305"),
            targetAerodrome = AerodromeId("LJMB"),
            leadNm = 5.0,
        )
    }
}
