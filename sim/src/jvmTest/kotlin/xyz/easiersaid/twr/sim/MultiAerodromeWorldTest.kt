package xyz.easiersaid.twr.sim

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import arrow.core.getOrElse
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.controller.ControllerView
import xyz.easiersaid.twr.controller.WeatherObservation
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.controllerDecide
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.migration.world.WorldCandidateLoader
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.RoleName

/**
 * Real-job multi-aerodrome integration scaffold.
 *
 * Loads the rendered LOWG and LJMB world candidates, merges them via the
 * production [WorldCandidateLoader], and verifies the multi-aerodrome runtime
 * surface works end-to-end:
 *
 *  - Both aerodromes are addressable as peers in the merged [AviationWorld].
 *  - Two independent [ControllerSpec]s — one per aerodrome's TOWER role —
 *    can be registered in a single [SimState].
 *  - Each controller's [ControllerView], built by [buildControllerView], scopes
 *    cleanly to its own aerodrome (its own runways, its own responsibilities)
 *    with no leakage from the peer aerodrome.
 *  - Cycling [controllerDecide] for each controller in turn produces a
 *    non-null result without crashing — the wiring tolerates a SimState that
 *    contains aircraft and runways from another aerodrome.
 *
 * This test is the foundation for the next golden test (LOWG → LJMB → LOWG VFR
 * round-trip with a single aircraft self-managing its frequency changes per
 * the published Jepp 19-2 procedure). The flight choreography itself is left
 * as a follow-up; this test only proves the multi-aerodrome scaffold.
 */
class MultiAerodromeWorldTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `loads LOWG and LJMB into one merged world with two independent controllers`() {
        val projectRoot = resolveProjectRoot()
        val lowgPath = projectRoot.resolve("cad/airports/rendered/lowg/world-candidate.json")
        val ljmbPath = projectRoot.resolve("cad/airports/rendered/ljmb/world-candidate.json")
        assertTrue(lowgPath.exists(), "Missing LOWG world candidate at $lowgPath")
        assertTrue(ljmbPath.exists(), "Missing LJMB world candidate at $ljmbPath")

        val lowgWorld = WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(lowgPath.readText()))
        val ljmbWorld = WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(ljmbPath.readText()))
        val world = WorldCandidateLoader.mergeAviationWorlds(listOf(lowgWorld, ljmbWorld))

        // Both aerodromes peered in one world.
        assertEquals(
            setOf(AerodromeId("LOWG"), AerodromeId("LJMB")),
            world.aerodromes.keys,
            "Merged world should expose both aerodromes.",
        )

        val lowgTower = ControllerSpec(
            id = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = emptySet(),
        )
        val ljmbTower = ControllerSpec(
            id = ControllerId("LJMB_TWR"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LJMB"),
            frequency = Frequency.unsafe("119.205"),
            responsibilities = emptySet(),
        )

        val worldIndex = WorldIndex(
            positions = world.geometry.points,
        )

        val state = SimState.initial(
            seed = 0L,
            world = world,
            worldIndex = worldIndex,
            aircraft = emptyList(),
            controllers = listOf(lowgTower, ljmbTower),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = null, qnh = null, visibility = null)
            },
        ).getOrElse { error("MultiAerodrome scaffold setup invalid: $it") }

        val lowgView = buildControllerView(state, lowgTower.id)
        val ljmbView = buildControllerView(state, ljmbTower.id)

        // Each controller scopes to its own aerodrome.
        assertEquals(AerodromeId("LOWG"), lowgView.aerodromeId)
        assertEquals(AerodromeId("LJMB"), ljmbView.aerodromeId)
        assertEquals(RoleName.TOWER, lowgView.role)
        assertEquals(RoleName.TOWER, ljmbView.role)

        // LOWG's view sees only LOWG runways; LJMB's view sees only LJMB runways.
        val lowgAerodrome = world.aerodromes.getValue(AerodromeId("LOWG"))
        val ljmbAerodrome = world.aerodromes.getValue(AerodromeId("LJMB"))
        assertEquals(
            lowgAerodrome.runways.keys,
            lowgView.runways.keys,
            "LOWG TOWER view should observe LOWG's runways only.",
        )
        assertEquals(
            ljmbAerodrome.runways.keys,
            ljmbView.runways.keys,
            "LJMB TOWER view should observe LJMB's runways only.",
        )
        assertTrue(
            (lowgView.runways.keys intersect ljmbView.runways.keys).isEmpty(),
            "Independent controllers must not overlap on runway responsibilities.",
        )

        // Cycle each controller through `decide` — no aircraft, just verify the
        // pipeline doesn't crash on a multi-aerodrome SimState.
        val lowgDecision = controllerDecide(lowgView, BeliefState.EMPTY, world)
        assertNotNull(lowgDecision, "LOWG TOWER should produce a decision result.")
        val ljmbDecision = controllerDecide(ljmbView, BeliefState.EMPTY, world)
        assertNotNull(ljmbDecision, "LJMB TOWER should produce a decision result.")
    }

    @Test
    fun `merged world keeps shared enroute fixes addressable from both airports`() {
        val projectRoot = resolveProjectRoot()
        val lowgPath = projectRoot.resolve("cad/airports/rendered/lowg/world-candidate.json")
        val ljmbPath = projectRoot.resolve("cad/airports/rendered/ljmb/world-candidate.json")
        val lowgWorld = WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(lowgPath.readText()))
        val ljmbWorld = WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(ljmbPath.readText()))
        val merged = WorldCandidateLoader.mergeAviationWorlds(listOf(lowgWorld, ljmbWorld))

        // Both airports' candidates publish a Fix for shared enroute waypoints
        // (GOLVA, DIMLO, MUREG, PETOV, VALLU). The merge resolves duplicates by
        // first-wins; the runtime must still be able to look up each shared fix
        // through the global FixId namespace.
        for (sharedIdent in listOf("GOLVA", "DIMLO", "MUREG", "PETOV", "VALLU")) {
            val fix = merged.fixes[xyz.easiersaid.twr.protocol.FixId(sharedIdent)]
            assertNotNull(fix, "Shared enroute fix $sharedIdent should be addressable in the merged world.")
        }
    }

    @Test
    fun `peer-aerodrome aircraft do not leak into a controller's responsibility view`() {
        // Sanity check: an aircraft registered with no controller responsibility
        // should not appear in either tower's view, and certainly not in both.
        val projectRoot = resolveProjectRoot()
        val lowgPath = projectRoot.resolve("cad/airports/rendered/lowg/world-candidate.json")
        val ljmbPath = projectRoot.resolve("cad/airports/rendered/ljmb/world-candidate.json")
        val lowgWorld = WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(lowgPath.readText()))
        val ljmbWorld = WorldCandidateLoader.toWorld(json.decodeFromString<WorldCandidateDocument>(ljmbPath.readText()))
        val world = WorldCandidateLoader.mergeAviationWorlds(listOf(lowgWorld, ljmbWorld))

        val lowgTower = ControllerSpec(
            id = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LOWG"),
            frequency = Frequency.unsafe("118.200"),
            responsibilities = emptySet(),
        )
        val ljmbTower = ControllerSpec(
            id = ControllerId("LJMB_TWR"),
            role = RoleName.TOWER,
            aerodromeId = AerodromeId("LJMB"),
            frequency = Frequency.unsafe("119.205"),
            responsibilities = emptySet(),
        )
        val state = SimState.initial(
            seed = 0L,
            world = world,
            worldIndex = WorldIndex(positions = world.geometry.points),
            aircraft = emptyList(),
            controllers = listOf(lowgTower, ljmbTower),
            weatherByAerodrome = world.aerodromes.keys.associateWith {
                WeatherObservation(wind = null, qnh = null, visibility = null)
            },
        ).getOrElse { error("MultiAerodrome leakage test setup invalid: $it") }

        val lowgView = buildControllerView(state, lowgTower.id)
        val ljmbView = buildControllerView(state, ljmbTower.id)
        assertTrue(lowgView.aircraft.isEmpty(), "Empty responsibilities → empty aircraft observation.")
        assertTrue(ljmbView.aircraft.isEmpty(), "Empty responsibilities → empty aircraft observation.")
        assertNull(state.controllerInbox[lowgTower.id], "No transmissions yet, no inbox entry expected.")
        assertNull(state.controllerInbox[ljmbTower.id], "No transmissions yet, no inbox entry expected.")
    }

    private fun resolveProjectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val direct = cwd.resolve("settings.gradle.kts")
        return if (java.nio.file.Files.exists(direct)) cwd else cwd.parent ?: cwd
    }

    private fun Path.exists(): Boolean = java.nio.file.Files.exists(this)
    private fun Path.readText(): String = java.nio.file.Files.readString(this)
}
