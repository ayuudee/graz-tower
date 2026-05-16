package xyz.easiersaid.twr.pilot.observe

import arrow.core.Some
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.CompletionMode
import xyz.easiersaid.twr.pilot.CompoundTask
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.MissionStep
import xyz.easiersaid.twr.pilot.PilotMission
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.PrimitiveTask
import xyz.easiersaid.twr.pilot.TaskName
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RunwayAssignment
import xyz.easiersaid.twr.protocol.RunwayAssignmentSource
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.WindReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * fn-14.1 (G3a-react R9) — `derivePilotEvent` crosswind branch matrix.
 *
 * Doctrine: FAA AFH Ch 9 Common Error #1; ICAO Annex 6 Part II §2.4
 * (PIC final authority); FAA AIM §7-1-12.d.3 (Magnetic frame).
 *
 * The branch must fire when:
 *  - aircraft.phase is Final
 *  - mission.currentStep in {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}
 *  - weather is WindReport.Available
 *  - mission.activeRunway is Some
 *  - runway.headingDegreesMagnetic() is non-null (in 01..36)
 *  - crosswindComponentKnots(...) > aircraft.type.maxCrosswindKnots
 *
 * And must NOT fire (return null) on any of these gates failing.
 *
 * **NOT clearance-gated** (independent of DA branch's
 * `mission.hasClearance` guard). The branch's existing-DA-and-crosswind
 * ordering row pins DA-first when both apply.
 */
class PilotEventCrosswindTest {

    private val ac = AircraftId("OE-ABC")
    // C172 POH crosswind = 15 kt.
    private val type: AircraftType = AircraftType.C172
    // Runway 27 → heading 270°M. Wind 360°M ⊥ 270° → full crosswind = wind speed.
    private val runway27 = RunwayId("27")

    private fun aircraftOnFinal(phase: PilotPhase = PilotPhase.Final): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = PointId("P"),
        altitudeM = 200.0,
        phase = phase,
        type = type,
    )

    private fun mission(
        step: MissionStep,
        hasClearance: Boolean = false,
        runway: RunwayId? = runway27,
    ): PilotMission = PilotMission(
        goal = HighLevelGoal.CircuitTraining(outcomes = listOf(CircuitOutcome.FullStop)),
        // Realistic shape: root is the goal-level compound (CircuitTraining),
        // wrapping a circuit-like inner compound (Circuit) that carries the
        // primitive `step`. Mirrors `planMission(CircuitTraining)` and is what
        // the mission-shape guard in `derivePilotEvent` looks for via
        // `root.activeCompound().name.isCircuitLike()`.
        root = CompoundTask(
            name = TaskName.CircuitTraining,
            children = listOf(
                CompoundTask(
                    name = TaskName.Circuit,
                    children = listOf(PrimitiveTask(step, CompletionMode.PHYSICAL)),
                ),
            ),
        ),
        stepEnteredAt = SimTime.ZERO,
        hasClearance = hasClearance,
        activeRunway = runway?.let { Some(RunwayAssignment(it, RunwayAssignmentSource.Filing)) } ?: arrow.core.None,
    )

    private fun availableWind(directionDegrees: Int, speedKnots: Int): WindReport =
        WindReport.Available(Wind.unsafe(directionDegrees, speedKnots))

    // ── Fires when all gates pass ─────────────────────────────────────

    @Test
    fun `wind 360M at 20 kt against runway 27 (heading 270) on FLY_FINAL fires CrosswindLimitExceeded`() {
        // Wind 360° (from due north) vs runway 27 (heading 270°): relative
        // 90° → full speed crosswind component = 20 kt > 15 kt POH → fires.
        val event = derivePilotEvent(
            aircraftOnFinal(),
            mission(MissionStep.FLY_FINAL),
            weather = availableWind(directionDegrees = 360, speedKnots = 20),
        )
        assertTrue(
            event is PilotEvent.CrosswindLimitExceeded,
            "20 kt pure crosswind > 15 kt POH should fire CrosswindLimitExceeded; got $event",
        )
        assertEquals(ac, event.aircraft, "event carries aircraft id")
        assertEquals(15, event.limitKnots, "event carries POH limit (C172 = 15 kt)")
        assertEquals(runway27, event.runway, "event carries the active runway")
        assertEquals(20.0, event.componentKnots, 1e-9, "20 kt pure crosswind = 20.0 component")
    }

    @Test
    fun `crosswind fires on each step in the eligible step set`() {
        // FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND.
        val eligibleSteps = listOf(
            MissionStep.FLY_FINAL,
            MissionStep.REPORT_FINAL,
            MissionStep.AWAIT_LANDING_CLEARANCE,
            MissionStep.LAND,
        )
        for (step in eligibleSteps) {
            val event = derivePilotEvent(
                aircraftOnFinal(),
                mission(step),
                weather = availableWind(directionDegrees = 360, speedKnots = 20),
            )
            assertTrue(
                event is PilotEvent.CrosswindLimitExceeded,
                "$step should be in the crosswind-eligible step set; got $event",
            )
        }
    }

    @Test
    fun `crosswind fires regardless of hasClearance (not clearance-gated)`() {
        // FAA AFH Ch 9: pilot has authority to GA on POH crosswind
        // regardless of clearance state. Distinct from DA branch which
        // requires `!mission.hasClearance`.
        val event = derivePilotEvent(
            aircraftOnFinal(),
            mission(MissionStep.LAND, hasClearance = true),
            weather = availableWind(directionDegrees = 360, speedKnots = 20),
        )
        assertTrue(
            event is PilotEvent.CrosswindLimitExceeded,
            "crosswind branch must fire even with hasClearance=true; got $event",
        )
    }

    // ── Null returns on gate failure ──────────────────────────────────

    @Test
    fun `crosswind does NOT fire when not on Final phase`() {
        // Climbing during FLY_FINAL is contrived but pins the predicate.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(phase = PilotPhase.Climbing),
                mission(MissionStep.FLY_FINAL),
                weather = availableWind(directionDegrees = 360, speedKnots = 20),
            ),
            "phase guard: only Final triggers the crosswind branch",
        )
    }

    @Test
    fun `crosswind does NOT fire when weather is null`() {
        // Pilot has no wind read (e.g. sim before METAR cycle or
        // multi-aerodrome ambiguity returning null from windForMission)
        // → fail-closed.
        assertNull(
            derivePilotEvent(aircraftOnFinal(), mission(MissionStep.FLY_FINAL), weather = null),
            "fail-closed: null weather → no event",
        )
    }

    @Test
    fun `crosswind does NOT fire when weather is WindReport NotReported`() {
        // Sensor offline → fail-closed.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_FINAL),
                weather = WindReport.NotReported,
            ),
            "fail-closed: WindReport.NotReported → no event",
        )
    }

    @Test
    fun `crosswind does NOT fire when runway designator fails to parse`() {
        // Synthetic HX runway → headingDegreesMagnetic returns null →
        // recognition fails closed.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_FINAL, runway = RunwayId("HX")),
                weather = availableWind(directionDegrees = 360, speedKnots = 20),
            ),
            "fail-closed: unparseable runway designator → no event",
        )
    }

    @Test
    fun `crosswind does NOT fire when active runway is None`() {
        // Before any runway assignment lands (e.g. ATC silent at sim
        // init) the pilot has no runway to compute against.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_FINAL, runway = null),
                weather = availableWind(directionDegrees = 360, speedKnots = 20),
            ),
            "fail-closed: mission.activeRunway is None → no event",
        )
    }

    @Test
    fun `crosswind does NOT fire when wind is within POH limit`() {
        // Wind 10 kt pure crosswind, POH 15 kt → below limit → no event.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_FINAL),
                weather = availableWind(directionDegrees = 360, speedKnots = 10),
            ),
            "wind 10 kt crosswind ≤ 15 kt POH → no event",
        )
    }

    @Test
    fun `crosswind boundary — at limit returns null (strict gt)`() {
        // Wind 15 kt pure crosswind against C172's 15 kt POH. The
        // recognition uses strict `>` — the boundary case AT the limit
        // does NOT fire. A real PIC at exactly the limit is at maximum
        // demonstrated performance; the GA fires when the value
        // exceeds it. Pinning the boundary so a regression to `>=`
        // surfaces.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_FINAL),
                weather = availableWind(directionDegrees = 360, speedKnots = 15),
            ),
            "boundary: crosswind == POH limit returns null (strict >)",
        )
    }

    @Test
    fun `crosswind FIRES on Transit-arrival shape — fn-28dot6 R18 disjunctive eligibility`() {
        // fn-28.6 (round-12 Major 1 widening — closes the prior
        // `D-PASS-g3b-react-cross-aerodrome-crosswind` deferment): a
        // Transit-arrival mission carries FLY_FINAL directly under the
        // Transit compound (no inner Circuit wrapper). Recognition now
        // gates on a disjunctive eligibility:
        //   isReactiveGoAroundEligible(mission)               // circuit-shape
        //     || isTransitArrivalReactiveGoAroundEligible(    // Transit-arrival
        //         aircraft, mission)
        // The apply path's dispatch fork in `applyCrosswindGoAround` uses
        // `replaceFromActivePrimitive(listOf(goAroundTask(), circuitTask(),
        // groundArrivalTask()))` (R22) for the Transit branch.
        val transitMission = PilotMission(
            goal = HighLevelGoal.Transit(destination = null),
            root = CompoundTask(
                name = TaskName.Transit,
                children = listOf(PrimitiveTask(MissionStep.FLY_FINAL, CompletionMode.PHYSICAL)),
            ),
            stepEnteredAt = SimTime.ZERO,
            activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
        )
        val event = derivePilotEvent(
            aircraftOnFinal(),
            transitMission,
            weather = availableWind(directionDegrees = 360, speedKnots = 20),
        )
        assertTrue(
            event is PilotEvent.CrosswindLimitExceeded,
            "Transit-arrival reactive crosswind now fires post-fn-28.6 widening; got $event",
        )
    }

    @Test
    fun `crosswind does NOT fire on Transit cruise shape — FLY_DEPARTURE active`() {
        // Sibling negative row: a Transit mission in cruise (FLY_DEPARTURE
        // active as direct child of Transit) does NOT satisfy the
        // disjunctive eligibility. The Transit-arrival guard's step-set
        // gate filters out FLY_DEPARTURE — recognition fails closed.
        val transitCruiseMission = PilotMission(
            goal = HighLevelGoal.Transit(destination = null),
            root = CompoundTask(
                name = TaskName.Transit,
                children = listOf(PrimitiveTask(MissionStep.FLY_DEPARTURE, CompletionMode.PHYSICAL)),
            ),
            stepEnteredAt = SimTime.ZERO,
            activeRunway = Some(RunwayAssignment(runway27, RunwayAssignmentSource.Filing)),
        )
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                transitCruiseMission,
                weather = availableWind(directionDegrees = 360, speedKnots = 20),
            ),
            "Transit cruise (FLY_DEPARTURE) is NOT in the Transit-arrival wind-reactive eligible set",
        )
    }

    @Test
    fun `crosswind does NOT fire on FLY_BASE — step set is final-only`() {
        // FLY_BASE is in the DA-branch's onApproach set but NOT in the
        // crosswind branch's eligible set. The PIC commits to crosswind
        // GA on final, when crab vs slip aerodynamic feel is load-
        // bearing.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_BASE),
                weather = availableWind(directionDegrees = 360, speedKnots = 20),
            ),
            "FLY_BASE is NOT in the crosswind-eligible step set",
        )
    }

    // ── DA-vs-crosswind ordering: DA fires first when both apply ─────

    @Test
    fun `when both DA and crosswind would fire, DA wins per branch order`() {
        // Construct a state in which BOTH gates pass:
        //  - aircraft at 50 m (below decision altitude, DA gate)
        //  - REPORT_FINAL (in both DA's onApproach and crosswind's
        //    eligible set)
        //  - no clearance (DA's hasClearance gate)
        //  - wind 20 kt pure crosswind (crosswind gate)
        //  - phase = Final (crosswind gate)
        // Result: DA fires first per derivePilotEvent's branch order.
        // Pinning the ordering protects against a regression that swaps
        // the branches or removes the early-return from the DA path.
        val event = derivePilotEvent(
            aircraftOnFinal().copy(altitudeM = 50.0),
            mission(MissionStep.REPORT_FINAL, hasClearance = false),
            weather = availableWind(directionDegrees = 360, speedKnots = 20),
        )
        assertTrue(
            event is PilotEvent.DecisionAltitudeWithoutClearance,
            "ordering pin: DA branch wins when both apply; got $event",
        )
    }
}
