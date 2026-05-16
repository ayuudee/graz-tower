package xyz.easiersaid.twr.pilot

import arrow.core.Either
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AerodromeAip
import xyz.easiersaid.twr.core.world.AirspaceBoundary
import xyz.easiersaid.twr.core.world.AirspaceClass
import xyz.easiersaid.twr.core.world.AirspaceVolume
import xyz.easiersaid.twr.core.world.AirspaceVolumeType
import xyz.easiersaid.twr.core.world.AltitudeBand
import xyz.easiersaid.twr.core.world.AltitudeBoundary
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.BoundaryRing
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.FlightInformationRegion
import xyz.easiersaid.twr.core.world.PlateId
import xyz.easiersaid.twr.core.world.PublishedMapLabel
import xyz.easiersaid.twr.core.world.PublishedPointReference
import xyz.easiersaid.twr.core.world.PublishedVfrProcedure
import xyz.easiersaid.twr.core.world.PublishedVfrProcedureKind
import xyz.easiersaid.twr.pilot.world.PilotAviationWorld
import xyz.easiersaid.twr.pilot.world.toPilotView
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AirspaceVolumeId
import xyz.easiersaid.twr.protocol.FirId
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.PublishedVfrProcedureId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * G2 Phase C — `resolveTransitContactRep` table.
 *
 * Pins the procedure-resolution helper's contract:
 *  1. ARRIVAL hit (happy path).
 *  2. ARRIVAL > TRANSIT priority.
 *  3. TRANSIT-only fallback + lexicographic id-sort (D-G2.6 deferment pin).
 *  4. `mapLabels` fallback when `publishedSequence` is empty (LJMB-real-fixture shape).
 *  5. `AerodromeNotInWorld` failure leaf.
 *  6. `NoArrivalProcedure` failure leaf.
 *  7. `ProcedureRepsUnresolvable` failure leaf (all-Literal references).
 *  8. No-airspace-read property — helper depends only on `aerodromes[…].aip.publishedVfrProcedures`.
 *
 * Planner-branch idempotence (write-once + intent-stability) is exercised
 * indirectly: `resolveTransitContactRep` is pure, the `Option.fold`
 * write-once shape is structurally correct (re-invoking with `Some(rep)`
 * never re-resolves). A direct planner-branch test would couple to
 * `Pilot.kt`'s private `planRoute`; that contract is asserted by the
 * G2 integration test (Phase F) and by `IsPhysicallyCompleteFlyDepartureSpec`.
 */
class TransitRoutePlanningSpec {

    private val DESTINATION = AerodromeId("LJMB")

    private fun aerodrome(procedures: Map<PublishedVfrProcedureId, PublishedVfrProcedure>): Aerodrome =
        Aerodrome(
            icao = DESTINATION,
            elevation = Feet(800),
            magneticVariation = Degrees(3.0),
            transitionAltitude = Level.AltitudeFeet.unsafe(10_000),
            aip = AerodromeAip(publishedVfrProcedures = procedures),
        )

    private fun world(procedures: Map<PublishedVfrProcedureId, PublishedVfrProcedure>): PilotAviationWorld =
        AviationWorld(aerodromes = mapOf(DESTINATION to aerodrome(procedures))).toPilotView()

    private fun procedure(
        id: String,
        kind: PublishedVfrProcedureKind,
        publishedSequence: List<PublishedPointReference> = emptyList(),
        mapLabels: List<PublishedMapLabel> = emptyList(),
    ): Pair<PublishedVfrProcedureId, PublishedVfrProcedure> {
        val pid = PublishedVfrProcedureId(id)
        return pid to PublishedVfrProcedure(
            id = pid,
            plateId = PlateId("$id-plate"),
            kind = kind,
            publishedSequence = publishedSequence,
            mapLabels = mapLabels,
        )
    }

    private fun fix(reference: String, point: String): PublishedPointReference.Fix =
        PublishedPointReference.Fix(reference = reference, point = PointId(point))

    private fun literal(reference: String): PublishedPointReference.Literal =
        PublishedPointReference.Literal(reference = reference)

    @Test
    fun `ARRIVAL hit returns first published REP`() {
        val w = world(mapOf(
            procedure("a_arr", PublishedVfrProcedureKind.ARRIVAL,
                publishedSequence = listOf(
                    fix("OSMOT", "LJMB_FIX_OSMOT"),
                    fix("LAPNA", "LJMB_FIX_LAPNA"),
                )),
        ))
        val result = resolveTransitContactRep(w, DESTINATION)
        assertEquals(Either.Right(PointId("LJMB_FIX_OSMOT")), result)
    }

    @Test
    fun `ARRIVAL with multiple procedures picks first by id-sort (D-G2-6 deferment pin within ARRIVAL kind)`() {
        // Two ARRIVALs, no TRANSIT — picks lexicographically smaller id within
        // the ARRIVAL kind. Phase C row covers TRANSIT-only id-sort; this row
        // pins the same discipline within ARRIVAL.
        val w = world(mapOf(
            procedure("z_arr", PublishedVfrProcedureKind.ARRIVAL,
                publishedSequence = listOf(fix("LAPNA", "LJMB_FIX_LAPNA"))),
            procedure("a_arr", PublishedVfrProcedureKind.ARRIVAL,
                publishedSequence = listOf(fix("OSMOT", "LJMB_FIX_OSMOT"))),
        ))
        val result = resolveTransitContactRep(w, DESTINATION)
        assertEquals(Either.Right(PointId("LJMB_FIX_OSMOT")), result,
            "Within ARRIVAL kind, lexicographic id-sort wins (a_arr beats z_arr).")
    }

    @Test
    fun `ARRIVAL beats TRANSIT priority`() {
        // Both kinds present; ARRIVAL wins regardless of id.value sort order
        // (z_arr > a_trans alphabetically — proves kind-priority dominates).
        val w = world(mapOf(
            procedure("z_arr", PublishedVfrProcedureKind.ARRIVAL,
                publishedSequence = listOf(fix("LAPNA", "LJMB_FIX_LAPNA"))),
            procedure("a_trans", PublishedVfrProcedureKind.TRANSIT,
                publishedSequence = listOf(fix("OSMOT", "LJMB_FIX_OSMOT"))),
        ))
        val result = resolveTransitContactRep(w, DESTINATION)
        assertEquals(Either.Right(PointId("LJMB_FIX_LAPNA")), result, "ARRIVAL must beat TRANSIT")
    }

    @Test
    fun `TRANSIT-only fallback picks first by id-sort (D-G2-6 deferment pin)`() {
        // Two TRANSITs, no ARRIVAL — picks lexicographically smaller id.
        val w = world(mapOf(
            procedure("z_trans", PublishedVfrProcedureKind.TRANSIT,
                publishedSequence = listOf(fix("LAPNA", "LJMB_FIX_LAPNA"))),
            procedure("a_trans", PublishedVfrProcedureKind.TRANSIT,
                publishedSequence = listOf(fix("OSMOT", "LJMB_FIX_OSMOT"))),
        ))
        val result = resolveTransitContactRep(w, DESTINATION)
        assertEquals(Either.Right(PointId("LJMB_FIX_OSMOT")), result,
            "When only TRANSIT procedures, lexicographic id-sort wins. " +
                "D-G2.6 will replace this with approach-direction selection.")
    }

    @Test
    fun `mapLabels fallback when publishedSequence is empty (LJMB-shape)`() {
        // Real LJMB shape: TRANSIT procedures with empty publishedSequence
        // and populated mapLabels (Fix-kind references). Helper falls
        // through to mapLabels.
        val w = world(mapOf(
            procedure("ljmb_ctr_entry_general", PublishedVfrProcedureKind.TRANSIT,
                publishedSequence = emptyList(),
                mapLabels = listOf(
                    PublishedMapLabel("OSMOT", fix("OSMOT", "LJMB_FIX_OSMOT")),
                    PublishedMapLabel("LAPNA", fix("LAPNA", "LJMB_FIX_LAPNA")),
                )),
        ))
        val result = resolveTransitContactRep(w, DESTINATION)
        assertEquals(Either.Right(PointId("LJMB_FIX_OSMOT")), result,
            "When publishedSequence is empty, mapLabels supplies the REP")
    }

    @Test
    fun `aerodrome absent from world returns AerodromeNotInWorld`() {
        val w = AviationWorld().toPilotView() // no aerodromes
        val result = resolveTransitContactRep(w, DESTINATION)
        assertEquals(Either.Left(RoutingError.AerodromeNotInWorld(DESTINATION)), result)
    }

    @Test
    fun `aerodrome with no procedures returns NoArrivalProcedure`() {
        val w = world(emptyMap())
        val result = resolveTransitContactRep(w, DESTINATION)
        assertEquals(Either.Left(RoutingError.NoArrivalProcedure(DESTINATION)), result)
    }

    @Test
    fun `procedure with all-Literal sequence and mapLabels returns ProcedureRepsUnresolvable`() {
        val pid = PublishedVfrProcedureId("literal_only")
        val w = world(mapOf(
            pid to PublishedVfrProcedure(
                id = pid,
                plateId = PlateId("literal-plate"),
                kind = PublishedVfrProcedureKind.ARRIVAL,
                publishedSequence = listOf(literal("FOO"), literal("BAR")),
                mapLabels = listOf(
                    PublishedMapLabel("FOO", literal("FOO")),
                    PublishedMapLabel("BAR", literal("BAR")),
                ),
            ),
        ))
        val result = resolveTransitContactRep(w, DESTINATION)
        assertEquals(Either.Left(RoutingError.ProcedureRepsUnresolvable(pid)), result,
            "Literal-only references have no PointId; surfaces as a procedure-authoring defect")
    }

    @Test
    fun `helper does not depend on airspace or FIR data (asymmetric world property)`() {
        // Architectural property: pilot reads chart data (publishedVfrProcedures),
        // not airspace polygons. The two worlds below differ only in airspace +
        // FIR contents — same aerodrome and procedures. The helper's output is
        // identical, proving it does NOT read airspace/FIRs. A regression that
        // adds e.g. `world.airspace.requireNotEmpty()` to the helper would fail
        // the empty-airspace world; a regression that branches on FIR contents
        // would diverge between the two worlds.
        val procedures = mapOf(
            procedure("a_arr", PublishedVfrProcedureKind.ARRIVAL,
                publishedSequence = listOf(fix("OSMOT", "LJMB_FIX_OSMOT"))),
        )
        val volumeId = AirspaceVolumeId("LJMB_CTR")
        val firId = FirId("LJLA")
        val withAirspaceAndFirs = AviationWorld(
            aerodromes = mapOf(DESTINATION to aerodrome(procedures)),
            airspace = mapOf(volumeId to AirspaceVolume(
                id = volumeId,
                name = "LJMB CTR",
                type = AirspaceVolumeType.CTR,
                airspaceClass = AirspaceClass.D,
                altitudeBand = AltitudeBand(
                    lower = AltitudeBoundary.Surface,
                    upper = AltitudeBoundary.AtLevel(Level.AltitudeFeet.unsafe(4500)),
                ),
                memberPoints = setOf(PointId("BND1"), PointId("BND2"), PointId("BND3")),
                fir = firId,
                boundary = AirspaceBoundary(rings = listOf(BoundaryRing(
                    points = listOf(PointId("BND1"), PointId("BND2"), PointId("BND3")),
                ))),
            )),
            firs = mapOf(firId to FlightInformationRegion(
                id = firId,
                name = "Ljubljana FIR",
                volumes = setOf(volumeId),
            )),
        ).toPilotView()
        val emptyAirspace = AviationWorld(
            aerodromes = mapOf(DESTINATION to aerodrome(procedures)),
        ).toPilotView()
        assertEquals(
            resolveTransitContactRep(withAirspaceAndFirs, DESTINATION),
            resolveTransitContactRep(emptyAirspace, DESTINATION),
            "Helper output must be identical regardless of airspace/FIR contents",
        )
        assertTrue(
            resolveTransitContactRep(emptyAirspace, DESTINATION).isRight(),
            "Helper succeeds with empty airspace map (proves no airspace-read)",
        )
    }

    @Test
    fun `planRoute Transit + FLY_DEPARTURE writes transitContactRep on first tick and not on subsequent ticks`() {
        // Pins the write-once / idempotence contract in `Pilot.kt`'s planRoute
        // Transit arm: tick 1 returns a Plan with mission carrying
        // transitContactRep = Some(rep); a subsequent invocation on that
        // returned mission produces a Plan with the SAME mission (no spurious
        // re-resolution), and the intent's route waypoint equals the first
        // call's. A regression that drops the `cachedRep != null` short-circuit
        // would re-resolve every tick — this test catches that.
        val w = world(mapOf(
            procedure("a_arr", PublishedVfrProcedureKind.ARRIVAL,
                publishedSequence = listOf(fix("OSMOT", "LJMB_FIX_OSMOT"))),
        ))
        val expectedRep = PointId("LJMB_FIX_OSMOT")
        val initialMission = PilotMission(
            goal = HighLevelGoal.Transit(destination = DESTINATION),
            root = CompoundTask(
                name = TaskName.Transit,
                children = listOf(
                    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
                ),
            ),
            stepEnteredAt = xyz.easiersaid.twr.protocol.SimTime.ofMillis(0),
        )
        val aircraft = AircraftState(
            id = xyz.easiersaid.twr.protocol.AircraftId("OE-XYZ"),
            callsign = xyz.easiersaid.twr.protocol.Callsign("OEXYZ"),
            position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
            positionPoint = PointId("STAND"),
            phase = PilotPhase.AtStand,
        )

        // Tick 1: mission has transitContactRep = None; planner resolves and writes.
        val tick1 = planRoute(
            mission = initialMission,
            aircraft = aircraft,
            kinematicRoute = PilotRoute.None,
            world = w,
            worldIndex = xyz.easiersaid.twr.core.world.WorldIndex(),
        )
        assertTrue(tick1 is PlanRouteOutcome.Plan, "Tick 1 must produce a Plan, got: $tick1")
        assertEquals(arrow.core.Some(expectedRep), tick1.mission.transitContactRep,
            "Tick 1 must write transitContactRep into the returned mission")
        val tick1Route = tick1.intent.route as PilotRoute.Airborne
        assertEquals(expectedRep, tick1Route.waypoints.head,
            "Tick 1 intent's route terminus must be the resolved REP")

        // Tick 2: mission now carries transitContactRep = Some(rep). Planner
        // must NOT re-resolve; the returned mission must be the same instance
        // (or structurally equal — Kotlin data class equality).
        val tick2 = planRoute(
            mission = tick1.mission,
            aircraft = aircraft,
            kinematicRoute = PilotRoute.None,
            world = w,
            worldIndex = xyz.easiersaid.twr.core.world.WorldIndex(),
        )
        assertTrue(tick2 is PlanRouteOutcome.Plan, "Tick 2 must produce a Plan, got: $tick2")
        assertEquals(tick1.mission, tick2.mission,
            "Tick 2 must return the cached mission unchanged (no re-resolution)")
        val tick2Route = tick2.intent.route as PilotRoute.Airborne
        assertEquals(tick1Route.waypoints, tick2Route.waypoints,
            "Tick 2 intent's route waypoints must equal tick 1's (intent stability)")
    }

    @Test
    fun `planRoute discriminator — Transit + FLY_DEPARTURE inside recovery Circuit does NOT plan cruise`() {
        // fn-28.6 R14 round-14 Major 1: after a Transit-arrival reactive
        // GA suffix replacement, the active leaf advances into the recovery
        // `circuitTask()` subtree — FLY_DEPARTURE becomes active AGAIN as the
        // first primitive of the recovery circuit, but the mission goal is
        // still `HighLevelGoal.Transit`. The pre-fix code would route this
        // FLY_DEPARTURE through `planTransitCruise` — WRONG. The discriminator
        // gates: treat Transit+FLY_DEPARTURE as cruise ONLY when the active
        // primitive is the original flat Transit departure primitive (no
        // nested Circuit / CircuitAfterGoAround / GoAround wrapper).
        //
        // **Setup**: a Transit mission whose root contains GoAround (complete)
        // + Circuit (active, FLY_DEPARTURE is its first primitive) + ... — the
        // post-suffix-replace post-GOING_AROUND-complete state. `planRoute`
        // must NOT call `planTransitCruise` here. Without a procedures-resolved
        // world the cruise path would fail with `NoArrivalProcedure` →
        // `PlanRouteOutcome.Failed`. With an empty/minimal world AND the
        // discriminator firing correctly, the post-GA recovery path falls
        // through to the regular logic (which requires an `activeRunway`;
        // without one, it skips early). The behavioural pin: NOT a Failed/Plan
        // outcome that came from the cruise arm.
        val emptyWorld = AviationWorld(aerodromes = emptyMap()).toPilotView()
        // GoAround compound (complete: GOING_AROUND is REPORTED — mark
        // completed = true). Circuit compound (active: FLY_DEPARTURE is the
        // first primitive of `circuitTask()`).
        val postGoAroundMission = PilotMission(
            goal = HighLevelGoal.Transit(destination = DESTINATION),
            root = CompoundTask(
                name = TaskName.Transit,
                children = listOf(
                    CompoundTask(
                        name = TaskName.GoAround,
                        children = listOf(
                            PrimitiveTask(MissionStep.GOING_AROUND, CompletionMode.REPORTED, completed = true),
                        ),
                    ),
                    circuitTask(),
                    groundArrivalTask(),
                ),
            ),
            stepEnteredAt = xyz.easiersaid.twr.protocol.SimTime.ofMillis(0),
        )
        // Sanity: active step is FLY_DEPARTURE (circuit's first primitive).
        assertEquals(
            MissionStep.FLY_DEPARTURE,
            postGoAroundMission.currentTask?.step,
            "test scaffold: post-GoAround active step is circuit's FLY_DEPARTURE",
        )
        // Sanity: activeCompound() returns the Circuit compound name.
        assertEquals(
            TaskName.Circuit,
            postGoAroundMission.root.activeCompound()?.name,
            "test scaffold: activeCompound() returns the Circuit compound (recovery shape)",
        )

        // Without the discriminator, `planRoute` would call `planTransitCruise`
        // → fail to resolve LJMB's arrival procedure → `PlanRouteOutcome.Failed`.
        // With the discriminator, planRoute falls through past the
        // `step == FLY_DEPARTURE && goal is Transit` cruise check (because
        // activeCompound is Circuit) and through to the activeRunway gate
        // (which fails closed → Skip, since the test mission has no
        // activeRunway). The behavioural pin: NOT a `Failed`.
        val aircraft = AircraftState(
            id = xyz.easiersaid.twr.protocol.AircraftId("OE-XYZ"),
            callsign = xyz.easiersaid.twr.protocol.Callsign("OEXYZ"),
            position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
            positionPoint = PointId("STAND"),
            phase = PilotPhase.Final,
        )
        val outcome = planRoute(
            mission = postGoAroundMission,
            aircraft = aircraft,
            kinematicRoute = PilotRoute.None,
            world = emptyWorld,
            worldIndex = xyz.easiersaid.twr.core.world.WorldIndex(),
        )
        assertTrue(
            outcome is PlanRouteOutcome.Skip,
            "post-GA recovery FLY_DEPARTURE skips cruise; without activeRunway falls through to Skip; got $outcome",
        )
    }

    @Test
    fun `planRoute discriminator — Transit + FLY_DEPARTURE direct child still plans cruise`() {
        // Negative pin: the existing cruise contract must still hold for the
        // pre-GA Transit mission shape (FLY_DEPARTURE as direct primitive
        // child of Transit, no inner compound). A regression to "always skip
        // cruise" would fail here.
        val procedures = mapOf(
            procedure("a_arr", PublishedVfrProcedureKind.ARRIVAL,
                publishedSequence = listOf(fix("OSMOT", "LJMB_FIX_OSMOT"))),
        )
        val w = world(procedures)
        val cruiseMission = PilotMission(
            goal = HighLevelGoal.Transit(destination = DESTINATION),
            root = CompoundTask(
                name = TaskName.Transit,
                children = listOf(
                    PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL),
                ),
            ),
            stepEnteredAt = xyz.easiersaid.twr.protocol.SimTime.ofMillis(0),
        )
        val aircraft = AircraftState(
            id = xyz.easiersaid.twr.protocol.AircraftId("OE-XYZ"),
            callsign = xyz.easiersaid.twr.protocol.Callsign("OEXYZ"),
            position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
            positionPoint = PointId("STAND"),
            phase = PilotPhase.AtStand,
        )
        val outcome = planRoute(
            mission = cruiseMission,
            aircraft = aircraft,
            kinematicRoute = PilotRoute.None,
            world = w,
            worldIndex = xyz.easiersaid.twr.core.world.WorldIndex(),
        )
        assertTrue(
            outcome is PlanRouteOutcome.Plan,
            "pre-GA Transit cruise: discriminator does NOT skip cruise; got $outcome",
        )
    }
}
