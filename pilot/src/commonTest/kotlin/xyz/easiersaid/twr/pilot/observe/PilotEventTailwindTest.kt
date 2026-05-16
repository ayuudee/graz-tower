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
 * fn-15.1 (G3a-react-tailwind R10) — `derivePilotEvent` tailwind branch matrix.
 *
 * Sibling of fn-14.1's `PilotEventCrosswindTest`. Doctrine: FAA AFH Ch 9
 * (tailwind landings as high-risk operations — modelling anchor for the
 * C172 advisory regime); Boeing 737-800 FCOM Limitations §1 (hard limit
 * for the B738 leaf); ICAO Annex 6 Part II §2.4 (PIC final authority);
 * FAA AIM §7-1-12.d.3 (Magnetic frame).
 *
 * The branch must fire when:
 *  - aircraft.phase is Final
 *  - mission.currentStep in {FLY_FINAL, REPORT_FINAL, AWAIT_LANDING_CLEARANCE, LAND}
 *  - active compound is circuit-like (shared mission-shape guard via
 *    `isReactiveGoAroundEligible`)
 *  - weather is WindReport.Available
 *  - mission.activeRunway is Some
 *  - runway.headingDegreesMagnetic() is non-null (in 01..36)
 *  - tailwindComponentKnots(...) > aircraft.type.maxTailwindKnots
 *
 * And must NOT fire (return null) on any of these gates failing.
 *
 * **NOT clearance-gated** (independent of DA branch's
 * `mission.hasClearance` guard). The branch's ordering pins:
 *  - DA fires first when DA + tailwind both apply (DA is the lowest-
 *    altitude / hardest-stop trigger);
 *  - tailwind fires before crosswind when both wind axes apply (tailwind
 *    is the physically stronger constraint; doctrinally a hard limit on
 *    jet-class types per FCOM Limitations §1).
 */
class PilotEventTailwindTest {

    private val ac = AircraftId("OE-ABC")
    // C172 POH/AFH tailwind = 10 kt (FAA AFH Ch 9 industry-standard advisory).
    private val type: AircraftType = AircraftType.C172
    // Runway 27 → heading 270°M. Wind 090°M (dead tailwind) → full tailwind = wind speed.
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
        // the shared mission-shape guard `isReactiveGoAroundEligible` looks
        // for via `root.activeCompound().name.isCircuitLike()`.
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
    fun `wind 090M at 15 kt against runway 27 (heading 270) on FLY_FINAL fires TailwindLimitExceeded`() {
        // Wind 090° (from the east) vs runway 27 (heading 270°): relative
        // 180° → dead tailwind = full speed = 15 kt > 10 kt AFH advisory → fires.
        val event = derivePilotEvent(
            aircraftOnFinal(),
            mission(MissionStep.FLY_FINAL),
            weather = availableWind(directionDegrees = 90, speedKnots = 15),
        )
        assertTrue(
            event is PilotEvent.TailwindLimitExceeded,
            "15 kt dead tailwind > 10 kt AFH advisory should fire TailwindLimitExceeded; got $event",
        )
        assertEquals(ac, event.aircraft, "event carries aircraft id")
        assertEquals(10, event.limitKnots, "event carries POH/AFH advisory (C172 = 10 kt)")
        assertEquals(runway27, event.runway, "event carries the active runway")
        assertEquals(15.0, event.componentKnots, 1e-9, "15 kt dead tailwind = 15.0 component")
    }

    @Test
    fun `tailwind fires on each step in the eligible step set`() {
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
                weather = availableWind(directionDegrees = 90, speedKnots = 15),
            )
            assertTrue(
                event is PilotEvent.TailwindLimitExceeded,
                "$step should be in the WIND_REACTIVE_ELIGIBLE_STEPS set; got $event",
            )
        }
    }

    @Test
    fun `tailwind fires regardless of hasClearance (not clearance-gated)`() {
        // FAA AFH Ch 9: pilot has authority to GA on POH/AFH tailwind
        // regardless of clearance state. Mirrors crosswind branch.
        val event = derivePilotEvent(
            aircraftOnFinal(),
            mission(MissionStep.LAND, hasClearance = true),
            weather = availableWind(directionDegrees = 90, speedKnots = 15),
        )
        assertTrue(
            event is PilotEvent.TailwindLimitExceeded,
            "tailwind branch must fire even with hasClearance=true; got $event",
        )
    }

    // ── Null returns on gate failure ──────────────────────────────────

    @Test
    fun `tailwind does NOT fire when not on Final phase`() {
        // Climbing during FLY_FINAL is contrived but pins the predicate.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(phase = PilotPhase.Climbing),
                mission(MissionStep.FLY_FINAL),
                weather = availableWind(directionDegrees = 90, speedKnots = 15),
            ),
            "phase guard: only Final triggers the tailwind branch",
        )
    }

    @Test
    fun `tailwind does NOT fire when weather is null`() {
        // Pilot has no wind read (e.g. sim before METAR cycle or
        // multi-aerodrome ambiguity returning null from windForMission)
        // → fail-closed.
        assertNull(
            derivePilotEvent(aircraftOnFinal(), mission(MissionStep.FLY_FINAL), weather = null),
            "fail-closed: null weather → no event",
        )
    }

    @Test
    fun `tailwind does NOT fire when weather is WindReport NotReported`() {
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
    fun `tailwind does NOT fire when runway designator fails to parse`() {
        // Synthetic HX runway → headingDegreesMagnetic returns null →
        // recognition fails closed.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_FINAL, runway = RunwayId("HX")),
                weather = availableWind(directionDegrees = 90, speedKnots = 15),
            ),
            "fail-closed: unparseable runway designator → no event",
        )
    }

    @Test
    fun `tailwind does NOT fire when active runway is None`() {
        // Before any runway assignment lands (e.g. ATC silent at sim
        // init) the pilot has no runway to compute against.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_FINAL, runway = null),
                weather = availableWind(directionDegrees = 90, speedKnots = 15),
            ),
            "fail-closed: mission.activeRunway is None → no event",
        )
    }

    @Test
    fun `tailwind does NOT fire when wind is within advisory`() {
        // Wind 5 kt dead tailwind, AFH 10 kt → below advisory → no event.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_FINAL),
                weather = availableWind(directionDegrees = 90, speedKnots = 5),
            ),
            "wind 5 kt tailwind ≤ 10 kt AFH advisory → no event",
        )
    }

    @Test
    fun `tailwind boundary — at advisory returns null (strict gt)`() {
        // Wind 10 kt pure tailwind against C172's 10 kt AFH advisory. The
        // recognition uses strict `>` — the boundary case AT the limit
        // does NOT fire. A real PIC at exactly the advisory is at the
        // operating maximum; the GA fires when the value exceeds it.
        // Pinning the boundary so a regression to `>=` surfaces.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_FINAL),
                weather = availableWind(directionDegrees = 90, speedKnots = 10),
            ),
            "boundary: tailwind == AFH advisory returns null (strict >)",
        )
    }

    @Test
    fun `tailwind boundary — just above advisory (11 kt) fires (strict gt)`() {
        // Sibling to the at-the-limit row above: at 11 kt dead tailwind
        // against the 10 kt C172 AFH advisory, the recognition fires.
        // Pins the upper half of the strict `>` boundary so a regression
        // to `<` or `<=` (which would never fire) surfaces. We use 11
        // (not the spec's notional `10.0001 kt`) because `Wind.speedKnots`
        // is an Int — the smallest representable above-advisory value
        // is 11 kt. The recognition layer's component vs. limit
        // comparison is Double vs Double (`component > limit.toDouble()`),
        // so finer-grain math discrimination is exercised at the helper
        // layer in `TailwindHelperTest.boundary — wind 10 kt direct
        // tailwind ... — exact 10.0`.
        val event = derivePilotEvent(
            aircraftOnFinal(),
            mission(MissionStep.FLY_FINAL),
            weather = availableWind(directionDegrees = 90, speedKnots = 11),
        )
        assertTrue(
            event is PilotEvent.TailwindLimitExceeded,
            "boundary: 11 kt dead tailwind > 10 kt advisory fires (strict >); got $event",
        )
        assertEquals(11.0, event.componentKnots, 1e-9, "component carries the precise value")
    }

    @Test
    fun `tailwind FIRES on Transit-arrival shape — fn-28dot6 R18 disjunctive eligibility`() {
        // fn-28.6 (round-12 Major 1 widening — closes the prior
        // `D-PASS-g3b-react-cross-aerodrome-tailwind` deferment): a
        // Transit-arrival mission carries FLY_FINAL directly under the
        // Transit compound (no inner Circuit wrapper). Recognition now
        // gates on a disjunctive eligibility:
        //   isReactiveGoAroundEligible(mission)               // circuit-shape
        //     || isTransitArrivalReactiveGoAroundEligible(    // Transit-arrival
        //         aircraft, mission)
        // The apply path's dispatch fork in `applyTailwindGoAround` uses
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
            weather = availableWind(directionDegrees = 90, speedKnots = 15),
        )
        assertTrue(
            event is PilotEvent.TailwindLimitExceeded,
            "Transit-arrival reactive tailwind now fires post-fn-28.6 widening; got $event",
        )
    }

    @Test
    fun `tailwind does NOT fire on Transit cruise shape — FLY_DEPARTURE active`() {
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
                weather = availableWind(directionDegrees = 90, speedKnots = 15),
            ),
            "Transit cruise (FLY_DEPARTURE) is NOT in the Transit-arrival wind-reactive eligible set",
        )
    }

    @Test
    fun `tailwind does NOT fire on FLY_BASE — step set is final-only`() {
        // FLY_BASE is in the DA-branch's onApproach set but NOT in the
        // WIND_REACTIVE_ELIGIBLE_STEPS set. The PIC commits to the
        // tailwind GA on final, when touchdown-energy / runway-remaining
        // physics become load-bearing.
        assertNull(
            derivePilotEvent(
                aircraftOnFinal(),
                mission(MissionStep.FLY_BASE),
                weather = availableWind(directionDegrees = 90, speedKnots = 15),
            ),
            "FLY_BASE is NOT in the WIND_REACTIVE_ELIGIBLE_STEPS set",
        )
    }

    // ── DA-vs-tailwind ordering: DA fires first when both apply ─────

    @Test
    fun `when both DA and tailwind would fire, DA wins per branch order`() {
        // Construct a state in which BOTH gates pass:
        //  - aircraft at 50 m (below decision altitude, DA gate)
        //  - REPORT_FINAL (in both DA's onApproach and the wind-reactive
        //    eligible set)
        //  - no clearance (DA's hasClearance gate)
        //  - wind 15 kt dead tailwind (tailwind gate)
        //  - phase = Final (tailwind gate)
        // Result: DA fires first per derivePilotEvent's branch order.
        // Pinning the ordering protects against a regression that swaps
        // the branches or removes the early-return from the DA path.
        val event = derivePilotEvent(
            aircraftOnFinal().copy(altitudeM = 50.0),
            mission(MissionStep.REPORT_FINAL, hasClearance = false),
            weather = availableWind(directionDegrees = 90, speedKnots = 15),
        )
        assertTrue(
            event is PilotEvent.DecisionAltitudeWithoutClearance,
            "ordering pin: DA branch wins when both apply; got $event",
        )
    }

    // ── Tailwind-vs-crosswind ordering: tailwind fires first when both apply ─────

    @Test
    fun `when both tailwind and crosswind would fire same tick, tailwind wins per branch order`() {
        // Construct a wind state in which BOTH wind-axis gates pass on
        // the C172 (10 kt tailwind / 15 kt crosswind):
        //  - wind 135°M from (south-east), speed 30 kt
        //  - runway 27 (heading 270°). Relative angle = 135 − 270 = −135°.
        //  - crosswind = |sin(−135°)| × 30 = 0.7071 × 30 ≈ 21.21 kt > 15 → fires
        //  - tailwind = max(0, −cos(−135°) × 30) = max(0, 0.7071 × 30) ≈ 21.21 kt > 10 → fires
        //
        // Result: tailwind fires first per derivePilotEvent's branch order
        // (DA → tailwind → crosswind). Pinning the ordering protects against
        // a regression that swaps the branches.
        val event = derivePilotEvent(
            aircraftOnFinal(),
            mission(MissionStep.FLY_FINAL, hasClearance = true),
            weather = availableWind(directionDegrees = 135, speedKnots = 30),
        )
        assertTrue(
            event is PilotEvent.TailwindLimitExceeded,
            "ordering pin: tailwind branch wins when tailwind + crosswind both apply same tick; got $event",
        )
        // Sanity: confirm crosswind would have fired had tailwind not won.
        // Quartering tailwind at 30 kt → both components ≈ 21.21 kt; both
        // > their respective C172 limits.
        assertTrue(
            event.componentKnots > 15.0,
            "fixture sanity: the quartering wind is well above both wind limits; tailwind = ${event.componentKnots}",
        )
    }
}
