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
    fun `transit route head is the source-runway departure end, last is TMA entry`() {
        // A1 fix: the transit route's HEAD must be the source-runway
        // departure end (the aircraft is on the source runway after
        // takeoff). The route then runs upwind → crosswind → TMA entry.
        // The test was previously load-bearing on `pts.last() == PETOV`
        // only and missed that the head was the wrong runway altogether.
        val ctx = loadLjmb()
        // Use LJMB RWY 32 as the synthetic "source runway" (this single-
        // airport test world doesn't have a separate source). What matters
        // is that the route's head matches the runway's path-last point
        // and the route's last is the supplied tmaEntry.
        val sourceRunway = RunwayId("32")
        val expectedDepartureEnd = ctx.world.aerodromes.values
            .first { sourceRunway in it.runways.keys }
            .runways.getValue(sourceRunway)
            .path.points.last()

        val result = buildCrossAerodromeTransitRoute(
            runwayId = sourceRunway,
            tmaEntry = PETOV,
            enRouteAltitudeM = 1525.0,
            world = ctx.world,
            worldIndex = ctx.worldIndex,
        )
        assertTrue(result.isRight(), "Transit route construction should succeed; got $result")
        val route = result.getOrNull()!!
        val pts: List<PointId> = listOf(route.waypoints.head) + route.waypoints.tail

        assertEquals(expectedDepartureEnd, pts.first(),
            "Head waypoint must be the source runway's departure end. The aircraft is on " +
                "this runway after takeoff; if the route's head is at the wrong airport, the " +
                "kinematic pilot will track to the wrong place. (A1 round-4 fix.)")
        assertEquals(PointId("LJMB_FIX_PETOV"), pts.last(),
            "Last waypoint of the transit route should be the TMA-entry fix.")
        assertEquals(1525.0, route.targetAltitudeM,
            "Transit route altitude must use the goal's enRouteAltitudeM, not CIRCUIT_ALTITUDE_M.")
    }

    @Test
    fun `missing waypoint at any slot produces typed MissingTransitWaypoint`() {
        // DEF-22 collapse: three previously-separate tests asserted the
        // same shape (`result.isLeft() && err is MissingTransitWaypoint &&
        // err.ident == X`) for the three fix slots in the arrival-join
        // builder. Per `feedback_testing_philosophy.md`, the right shape
        // is one parametrised test until a real caller dispatches per
        // slot — the function's contract is "first missing fix, by slot
        // order: tmaEntry then ctrEntry then corridor."
        val ctx = loadLjmb()
        val cases = listOf(
            "tmaEntry" to BuildArgs(tmaEntry = FixId("DOES_NOT_EXIST"), ctrEntry = MN1, corridor = listOf(MN2)),
            "ctrEntry" to BuildArgs(tmaEntry = PETOV, ctrEntry = FixId("NO_SUCH_REP"), corridor = listOf(MN2)),
            "corridor" to BuildArgs(tmaEntry = PETOV, ctrEntry = MN1, corridor = listOf(FixId("NOT_A_CORRIDOR_REP"))),
        )
        for ((label, args) in cases) {
            val absent = listOfNotNull(
                args.tmaEntry.takeIf { it.value == "DOES_NOT_EXIST" },
                args.ctrEntry.takeIf { it.value == "NO_SUCH_REP" },
                args.corridor.firstOrNull { it.value == "NOT_A_CORRIDOR_REP" },
            ).single()
            val result = buildCrossAerodromeArrivalJoinRoute(
                runwayId = RWY_14,
                tmaEntry = args.tmaEntry,
                ctrEntry = args.ctrEntry,
                corridorWaypoints = args.corridor,
                joinLeg = LegName.BASE,
                world = ctx.world,
                worldIndex = ctx.worldIndex,
            )
            val err = result.leftOrNull()
            assertTrue(err is RoutingError.MissingTransitWaypoint,
                "Slot=$label: expected MissingTransitWaypoint, got ${err?.let { it::class.simpleName }}")
            assertEquals(absent, (err as RoutingError.MissingTransitWaypoint).ident,
                "Slot=$label: error must carry the missing fix ident.")
        }
    }

    private data class BuildArgs(
        val tmaEntry: FixId,
        val ctrEntry: FixId,
        val corridor: List<FixId>,
    )

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
