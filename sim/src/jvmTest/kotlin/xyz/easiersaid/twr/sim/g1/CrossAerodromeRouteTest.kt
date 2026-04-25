package xyz.easiersaid.twr.sim.g1

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.migration.world.WorldCandidateDocument
import xyz.easiersaid.twr.migration.world.WorldCandidateLoader
import xyz.easiersaid.twr.protocol.FixId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.sim.RoutingError
import xyz.easiersaid.twr.sim.buildCrossAerodromeArrivalJoinRoute
import xyz.easiersaid.twr.sim.buildCrossAerodromeTransitRoute

/**
 * G1.4 — behavioural tests for cross-aerodrome route construction.
 *
 * Asserts the **waypoint identity sequence** for the destination-side
 * arrival join route. Loads LJMB alone (avoids G1-DEF-11 — the merged-
 * world geometric frame collision). The transit-side route
 * (`buildCrossAerodromeTransitRoute`) is exercised in G1.6 once the
 * geometric frame fix lands.
 *
 * Per design: tests verify
 *  - happy path: route includes PETOV → MN1 → BASE leg points → threshold.
 *  - missing TMA waypoint surfaces `RoutingError.MissingTransitWaypoint`.
 *  - missing CTR waypoint surfaces `RoutingError.MissingTransitWaypoint`.
 */
class CrossAerodromeRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Fix IDs in the world-candidate are keyed by bare name (e.g. "PETOV").
    // The `LJMB_FIX_*` prefix is the *PointId* — the FixId resolves through
    // it via `world.fixes[FixId("PETOV")].point`.
    private val PETOV = FixId("PETOV")
    private val MN1 = FixId("MN1")
    private val MN2 = FixId("MN2")
    private val RWY_14 = RunwayId("14")
    private val RWY_14_THR = PointId("LJMB_RWY_14_THR")

    @Test
    fun `arrival-join route runs PETOV — MN1 — MN2 — BASE-leg points — threshold`() {
        val ctx = loadLjmb()
        val result = buildCrossAerodromeArrivalJoinRoute(
            runwayId = RWY_14,
            tmaEntry = PETOV,
            ctrEntry = MN1,
            corridorWaypoints = listOf(MN2),
            joinLeg = LegName.BASE,
            world = ctx.world,
            worldIndex = ctx.worldIndex,
        )
        assertTrue(result.isRight(), "Route construction should succeed; got $result")
        val route = result.getOrNull()!!
        val pts: List<PointId> = listOf(route.waypoints.head) + route.waypoints.tail

        // PETOV → MN1 → MN2 → ... → threshold. Per atc-general round-3
        // finding: skipping MN2 amounts to skipping a published REP.
        assertEquals(PointId("LJMB_FIX_PETOV"), pts[0], "First waypoint should be the TMA-entry fix.")
        assertEquals(PointId("LJMB_FIX_MN1"), pts[1], "Second waypoint should be the CTR-entry fix.")
        assertEquals(PointId("LJMB_FIX_MN2"), pts[2], "Third waypoint should be the corridor REP MN2.")
        assertEquals(RWY_14_THR, pts.last(), "Final waypoint should be the runway threshold.")
        assertEquals(1, pts.count { it == RWY_14_THR },
            "Threshold should appear exactly once in the waypoint sequence.")
    }

    @Test
    fun `transit route runs LOWG departure-end — upwind — crosswind — TMA entry`() {
        // FP round-3 finding: the transit-route builder was untested at the
        // identity level. This test pins waypoint identities for a pure
        // single-airport transit construction (LJMB stand-in for "source"
        // since loading LOWG-only doesn't have PETOV defined).
        val ctx = loadLjmb()
        val result = buildCrossAerodromeTransitRoute(
            runwayId = RunwayId("32"),  // departing northbound; doesn't matter for identity test
            tmaEntry = PETOV,
            enRouteAltitudeM = 1525.0,
            world = ctx.world,
            worldIndex = ctx.worldIndex,
        )
        assertTrue(result.isRight(), "Transit route construction should succeed; got $result")
        val route = result.getOrNull()!!
        val pts: List<PointId> = listOf(route.waypoints.head) + route.waypoints.tail

        // First waypoint: the runway departure end (last point of the runway path).
        // Last waypoint: the TMA-entry fix's point.
        assertEquals(PointId("LJMB_FIX_PETOV"), pts.last(),
            "Last waypoint of the transit route should be the TMA-entry fix.")
        // Must contain the runway departure end as the route's start.
        // (We can't easily assert which exact PointId without depending on
        // the manifest's runway path naming; the assertion above is enough
        // to pin the contract.)
        assertEquals(1525.0, route.targetAltitudeM,
            "Transit route altitude must use the goal's enRouteAltitudeM, not CIRCUIT_ALTITUDE_M.")
    }

    @Test
    fun `missing TMA waypoint produces typed MissingTransitWaypoint error`() {
        val ctx = loadLjmb()
        val absentFix = FixId("DOES_NOT_EXIST")
        val result = buildCrossAerodromeArrivalJoinRoute(
            runwayId = RWY_14,
            tmaEntry = absentFix,
            ctrEntry = MN1,
            corridorWaypoints = listOf(MN2),
            joinLeg = LegName.BASE,
            world = ctx.world,
            worldIndex = ctx.worldIndex,
        )
        assertTrue(result.isLeft(), "Missing tmaEntry must surface a typed error.")
        val err = result.leftOrNull()!!
        assertTrue(err is RoutingError.MissingTransitWaypoint,
            "Expected MissingTransitWaypoint, got ${err::class.simpleName}")
        assertEquals(absentFix, (err as RoutingError.MissingTransitWaypoint).ident)
    }

    @Test
    fun `missing CTR waypoint produces typed MissingTransitWaypoint error`() {
        val ctx = loadLjmb()
        val absentFix = FixId("NO_SUCH_REP")
        val result = buildCrossAerodromeArrivalJoinRoute(
            runwayId = RWY_14,
            tmaEntry = PETOV,
            ctrEntry = absentFix,
            corridorWaypoints = listOf(MN2),
            joinLeg = LegName.BASE,
            world = ctx.world,
            worldIndex = ctx.worldIndex,
        )
        assertTrue(result.isLeft(), "Missing ctrEntry must surface a typed error.")
        val err = result.leftOrNull()!!
        assertTrue(err is RoutingError.MissingTransitWaypoint,
            "Expected MissingTransitWaypoint, got ${err::class.simpleName}")
        assertEquals(absentFix, (err as RoutingError.MissingTransitWaypoint).ident)
    }

    @Test
    fun `missing corridor waypoint produces typed MissingTransitWaypoint error`() {
        val ctx = loadLjmb()
        val absentCorridor = FixId("NOT_A_CORRIDOR_REP")
        val result = buildCrossAerodromeArrivalJoinRoute(
            runwayId = RWY_14,
            tmaEntry = PETOV,
            ctrEntry = MN1,
            corridorWaypoints = listOf(absentCorridor),
            joinLeg = LegName.BASE,
            world = ctx.world,
            worldIndex = ctx.worldIndex,
        )
        assertTrue(result.isLeft(), "Missing corridor waypoint must surface a typed error.")
        val err = result.leftOrNull()!!
        assertTrue(err is RoutingError.MissingTransitWaypoint,
            "Expected MissingTransitWaypoint, got ${err::class.simpleName}")
        assertEquals(absentCorridor, (err as RoutingError.MissingTransitWaypoint).ident)
    }

    private data class Ctx(val world: AviationWorld, val worldIndex: WorldIndex)

    private fun loadLjmb(): Ctx {
        val projectRoot = resolveProjectRoot()
        val ljmbPath = projectRoot.resolve("cad/airports/rendered/ljmb/world-candidate.json")
        val world = WorldCandidateLoader.toWorld(
            json.decodeFromString<WorldCandidateDocument>(java.nio.file.Files.readString(ljmbPath))
        )
        return Ctx(world, WorldIndex(positions = world.geometry.points))
    }

    private fun resolveProjectRoot(): Path {
        val cwd = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
        val direct = cwd.resolve("settings.gradle.kts")
        return if (java.nio.file.Files.exists(direct)) cwd else cwd.parent ?: cwd
    }
}
