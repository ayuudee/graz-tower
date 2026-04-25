package xyz.easiersaid.twr.sim.g1

import arrow.core.getOrElse
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.WindReport
import xyz.easiersaid.twr.controller.controllerDecide
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.migration.world.WorldCandidateLoader
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.sim.ControllerSpec
import xyz.easiersaid.twr.sim.SimState
import xyz.easiersaid.twr.sim.buildControllerView

/**
 * G1.1 — wind threading end-to-end.
 *
 * Behavioural contract: when the simulator threads weather into the
 * controller view, the tower's [BeliefState.activeRunway] (computed by
 * [selectRunwayIntoWind] inside [controllerDecide]) reflects the
 * configured wind.
 *
 * South wind (180°) → LOWG selects 16C, LJMB selects 14.
 * North wind (360°) → LOWG selects 34C, LJMB selects 32.
 *
 * The test runs `controllerDecide` once for each TWR controller — that
 * is the publicly observable surface where weather actually does work.
 * Asserting [BeliefState.activeRunway] proves the full chain from
 * `SimState.weatherByAerodrome` through `buildControllerView.weather`
 * through `selectRunwayIntoWind`. No structural-only assertions.
 */
class WindActiveRunwayTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `south wind drives LOWG to 16C and LJMB to 14`() {
        val ctx = loadMergedWorld()
        val southWind = Wind.unsafe(directionDegrees = 180, speedKnots = 8)
        val state = mergedState(
            ctx,
            weather = ctx.world.aerodromes.keys.associateWith {
                WeatherObservation(wind = WindReport.Available(southWind), qnh = null, visibility = null)
            },
        )

        val lowgBeliefs = controllerDecide(
            buildControllerView(state, ctx.lowgTwr.id),
            BeliefState.EMPTY,
            state.world,
        ).updatedBeliefs
        val ljmbBeliefs = controllerDecide(
            buildControllerView(state, ctx.ljmbTwr.id),
            BeliefState.EMPTY,
            state.world,
        ).updatedBeliefs

        assertEquals(RunwayId("16C"), lowgBeliefs.activeRunway, "LOWG TWR active runway with south wind should be 16C.")
        assertEquals(RunwayId("14"), ljmbBeliefs.activeRunway, "LJMB TWR active runway with south wind should be 14.")
    }

    @Test
    fun `north wind drives LOWG to 34C and LJMB to 32`() {
        val ctx = loadMergedWorld()
        val northWind = Wind.unsafe(directionDegrees = 360, speedKnots = 8)
        val state = mergedState(
            ctx,
            weather = ctx.world.aerodromes.keys.associateWith {
                WeatherObservation(wind = WindReport.Available(northWind), qnh = null, visibility = null)
            },
        )

        val lowgBeliefs = controllerDecide(
            buildControllerView(state, ctx.lowgTwr.id),
            BeliefState.EMPTY,
            state.world,
        ).updatedBeliefs
        val ljmbBeliefs = controllerDecide(
            buildControllerView(state, ctx.ljmbTwr.id),
            BeliefState.EMPTY,
            state.world,
        ).updatedBeliefs

        assertEquals(RunwayId("34C"), lowgBeliefs.activeRunway, "LOWG TWR active runway with north wind should be 34C.")
        assertEquals(RunwayId("32"), ljmbBeliefs.activeRunway, "LJMB TWR active runway with north wind should be 32.")
    }

    @Test
    fun `SimState initial rejects runway-bearing aerodrome with no weather entry`() {
        val ctx = loadMergedWorld()
        // Provide weather only for LOWG; LJMB is omitted.
        val partialWeather = mapOf(
            AerodromeId("LOWG") to WeatherObservation(wind = WindReport.NotReported, qnh = null, visibility = null),
        )
        val result = SimState.initial(
            seed = 0L,
            world = ctx.world,
            worldIndex = ctx.worldIndex,
            controllers = listOf(ctx.lowgTwr, ctx.ljmbTwr),
            weatherByAerodrome = partialWeather,
        )
        assertNotNull(
            result.swap().getOrNull(),
            "SimState.initial must reject a world with a runway-bearing aerodrome that has no weather entry.",
        )
    }

    private data class Ctx(
        val world: xyz.easiersaid.twr.core.world.AviationWorld,
        val worldIndex: WorldIndex,
        val lowgTwr: ControllerSpec,
        val ljmbTwr: ControllerSpec,
    )

    private fun loadMergedWorld(): Ctx {
        val projectRoot = resolveProjectRoot()
        val lowgPath = projectRoot.resolve("cad/airports/rendered/lowg/world-candidate.json")
        val ljmbPath = projectRoot.resolve("cad/airports/rendered/ljmb/world-candidate.json")
        val lowgWorld = WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(java.nio.file.Files.readString(lowgPath)))
        val ljmbWorld = WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(java.nio.file.Files.readString(ljmbPath)))
        val world = WorldCandidateLoader.mergeAviationWorlds(listOf(lowgWorld, ljmbWorld))
        val worldIndex = WorldIndex(positions = world.geometry.points)

        val lowgTwr = ControllerSpec(
            id = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = emptySet(),
        )
        val ljmbTwr = ControllerSpec(
            id = ControllerId("LJMB_TWR"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LJMB"),
            frequency = Frequency.unsafe("119.205"),
            responsibilities = emptySet(),
        )
        return Ctx(world, worldIndex, lowgTwr, ljmbTwr)
    }

    private fun mergedState(
        ctx: Ctx,
        weather: Map<AerodromeId, WeatherObservation>,
    ): SimState = SimState.initial(
        seed = 0L,
        world = ctx.world,
        worldIndex = ctx.worldIndex,
        controllers = listOf(ctx.lowgTwr, ctx.ljmbTwr),
        weatherByAerodrome = weather,
    ).getOrElse { error("WindActiveRunway test setup invalid: $it") }

    private fun resolveProjectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val direct = cwd.resolve("settings.gradle.kts")
        return if (java.nio.file.Files.exists(direct)) cwd else cwd.parent ?: cwd
    }
}
