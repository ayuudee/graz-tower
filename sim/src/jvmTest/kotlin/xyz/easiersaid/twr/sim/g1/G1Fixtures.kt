package xyz.easiersaid.twr.sim.g1

import arrow.core.getOrElse
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.WindReport
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.migration.world.WorldCandidateLoader
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.sim.PilotAirspace
import xyz.easiersaid.twr.sim.SimState

/**
 * Shared fixtures for G1 tests.
 *
 * Before this helper, six different test files duplicated `resolveProjectRoot`,
 * world-loaders, the TMA-Maribor centroid math, the LJMB_TMA_TRIGGER constant,
 * and weather-observation builders. The general-purpose audit (round-4) called
 * this out as the meta-issue blocking G1.6 from compiling cleanly. Round-5
 * fix.
 */
internal object G1Fixtures {

    private val json = Json { ignoreUnknownKeys = true }

    // ── Aerodromes / IDs ────────────────────────────────────────────────

    val LOWG = AerodromeId("LOWG")
    val LJMB = AerodromeId("LJMB")

    val PETOV: FixId = FixId("PETOV")
    val MN1: FixId = FixId("MN1")
    val MN2: FixId = FixId("MN2")

    val PETOV_POINT: PointId = PointId("LJMB_FIX_PETOV")
    val MN1_POINT: PointId = PointId("LJMB_FIX_MN1")
    val MN2_POINT: PointId = PointId("LJMB_FIX_MN2")

    val LJMB_TMA_VOLUME: AirspaceVolumeId = AirspaceVolumeId("LJMB_OPENAIR_TMA_MARIBOR_1")

    // ── Frequencies (sourced from manifests, kept here for tests that
    //    don't go through the manifest decoder) ─────────────────────────

    val LOWG_TWR_FREQ: Frequency = Frequency.unsafe("118.200")
    val LJMB_TWR_FREQ: Frequency = Frequency.unsafe("119.205")
    val LJMB_APP_FREQ: Frequency = Frequency.unsafe("134.305")

    // ── Triggers ────────────────────────────────────────────────────────

    val LJMB_TMA_TRIGGER = PilotAirspace.FrequencyChangeTrigger(
        triggerPoint = PETOV_POINT,
        targetVolume = LJMB_TMA_VOLUME,
        targetRole = RoleName.APPROACH,
        targetFrequency = LJMB_APP_FREQ,
        targetAerodrome = LJMB,
        leadNm = 5.0,
    )

    // ── World loaders ──────────────────────────────────────────────────

    fun loadLjmb(): AviationWorld =
        WorldCandidateLoader.toWorld(
            json.decodeFromString<WorldCandidateDocument>(
                java.nio.file.Files.readString(
                    resolveProjectRoot().resolve("cad/airports/rendered/ljmb/world-candidate.json")
                )
            )
        )

    fun loadLowg(): AviationWorld =
        WorldCandidateLoader.toWorld(
            json.decodeFromString<WorldCandidateDocument>(
                java.nio.file.Files.readString(
                    resolveProjectRoot().resolve("cad/airports/rendered/lowg/world-candidate.json")
                )
            )
        )

    /** Merge LOWG + LJMB into a single coherent-frame world (G1-DEF-11). */
    fun loadMergedLowgLjmb(): AviationWorld =
        WorldCandidateLoader.mergeAviationWorlds(listOf(loadLowg(), loadLjmb()))

    /**
     * Build an index over [world]'s geometry points. Tests that need
     * additional [WorldIndex] fields (adjacency, entitiesByPoint, etc.)
     * should construct a richer index inline.
     */
    fun pointsIndex(world: AviationWorld): WorldIndex =
        WorldIndex(positions = world.geometry.points)

    // ── Weather helpers ────────────────────────────────────────────────

    /**
     * "No weather report" entry for every runway-bearing aerodrome in [world] —
     * required by [SimState.initial]'s validating constructor (G1.1) but doesn't
     * commit to any wind direction. Tests that need an active runway selected
     * use [eastWindFor] or build [WeatherObservation.Available] inline.
     */
    fun unobservedWeather(world: AviationWorld): Map<AerodromeId, WeatherObservation> =
        world.aerodromes.keys.associateWith {
            WeatherObservation(wind = WindReport.NotReported, qnh = null, visibility = null)
        }

    // ── TMA Maribor 1 geometry helpers ─────────────────────────────────

    /**
     * Centroid of TMA Maribor 1's outer boundary. A position guaranteed to
     * lie *inside* the volume (assuming a convex-ish boundary, which the
     * authored OpenAir geometry satisfies).
     */
    fun tmaMariborCentroid(world: AviationWorld, idx: WorldIndex): Position {
        val tma = world.airspace.getValue(LJMB_TMA_VOLUME)
        val ring = tma.boundary!!.rings.first().points.mapNotNull { idx.positions[it] }
        val cx = ring.map { it.xMeters }.average()
        val cy = ring.map { it.yMeters }.average()
        return Position(xMeters = cx, yMeters = cy)
    }

    /**
     * Position [stepNm] nautical miles outward from the TMA centroid through
     * PETOV. PETOV is on the TMA boundary; stepping outward from the centroid
     * through PETOV lands in airspace strictly outside the TMA — useful for
     * simulating "approaching PETOV from the north."
     */
    fun outwardFromTmaThroughPetov(
        world: AviationWorld,
        idx: WorldIndex,
        stepNm: Double = 2.0,
    ): Position {
        val petov = idx.positions.getValue(PETOV_POINT)
        val centroid = tmaMariborCentroid(world, idx)
        val outwardX = petov.xMeters - centroid.xMeters
        val outwardY = petov.yMeters - centroid.yMeters
        val outwardLen = kotlin.math.hypot(outwardX, outwardY)
        val stepM = stepNm * 1852.0
        return Position(
            xMeters = petov.xMeters + outwardX / outwardLen * stepM,
            yMeters = petov.yMeters + outwardY / outwardLen * stepM,
        )
    }

    // ── Project-root resolution ────────────────────────────────────────

    fun resolveProjectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val direct = cwd.resolve("settings.gradle.kts")
        return if (java.nio.file.Files.exists(direct)) cwd else cwd.parent ?: cwd
    }
}
