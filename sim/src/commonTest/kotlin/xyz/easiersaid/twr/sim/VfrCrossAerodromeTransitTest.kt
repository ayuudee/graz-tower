package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.PilotGoal
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.FixId
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * G1.3 — visible-behaviour test for [HighLevelGoal.VfrCrossAerodromeTransit].
 *
 * The behavioural contract: as the mission progresses through its phases
 * (ground departure → climb-out → en-route → arrival join → circuit →
 * ground arrival), [derivePilotGoal] returns the correct controller-
 * visible goal at each phase.
 *
 * Per test-review M6: no shape-only "decomposition tree contains X
 * children" assertions — those test the type system, not behaviour. This
 * test asserts the **observable contract** between pilot and controller
 * through `derivePilotGoal`.
 */
class VfrCrossAerodromeTransitTest {

    private val transitGoal = HighLevelGoal.VfrCrossAerodromeTransit(
        from = AerodromeId("LOWG"),
        to = AerodromeId("LJMB"),
        tmaEntry = FixId("PETOV"),
        ctrEntry = FixId("MN1"),
        joinLeg = LegName.BASE,
    )

    @Test
    fun `derivePilotGoal walks the cross-aerodrome phases`() {
        // Mission starts with all phases pending.
        val initial = PilotMission(goal = transitGoal, root = planMission(transitGoal, humanPiloted = false))

        // Phase 1 — ground departure at LOWG. activeCompound = GroundDeparture → DEPART.
        assertEquals(PilotGoal.DEPART, derivePilotGoal(initial),
            "While GroundDeparture is active, the controller should see DEPART.")

        // Advance past ground departure: complete every primitive in groundDepartureTask
        // (humanPiloted=false omits the request-* steps; only TAXI_TO_HOLDING through
        // AWAIT_TAKEOFF_CLEARANCE remain).
        val afterGround = stepThrough(
            initial,
            MissionStep.TAXI_TO_HOLDING, MissionStep.RUN_UP_CHECKS,
            MissionStep.REPORT_READY, MissionStep.AWAIT_LINE_UP,
            MissionStep.AWAIT_TAKEOFF_CLEARANCE,
        )

        // Phase 2 — climb-out wrapped in TaskName.Depart compound → DEPART.
        assertEquals(PilotGoal.DEPART, derivePilotGoal(afterGround),
            "While the FLY_DEPARTURE phase (Depart wrapper) is active, controller should see DEPART.")

        val afterDepart = stepThrough(afterGround, MissionStep.FLY_DEPARTURE)

        // Phase 3 — en-route wrapped in TaskName.Transit compound → TRANSIT.
        assertEquals(PilotGoal.TRANSIT, derivePilotGoal(afterDepart),
            "While the FLY_EN_ROUTE phase (Transit wrapper) is active, controller should see TRANSIT.")

        val afterEnRoute = stepThrough(afterDepart, MissionStep.FLY_EN_ROUTE)

        // Phase 4 — arrival join → ARRIVE.
        assertEquals(PilotGoal.ARRIVE, derivePilotGoal(afterEnRoute),
            "While ArrivalJoin is active, controller should see ARRIVE.")

        val afterJoin = stepThrough(afterEnRoute, MissionStep.CALL_INBOUND, MissionStep.AWAIT_JOINING_INSTRUCTIONS)

        // Phase 5 — circuit at destination → ARRIVE (full-stop circuit, last in mission).
        assertEquals(PilotGoal.ARRIVE, derivePilotGoal(afterJoin),
            "While the destination Circuit is active, controller should see ARRIVE.")

        // Walk the rest of the circuit through landing.
        val afterLand = stepThrough(
            afterJoin,
            MissionStep.FLY_DOWNWIND, MissionStep.REPORT_DOWNWIND,
            MissionStep.AWAIT_SEQUENCING,
            MissionStep.FLY_BASE, MissionStep.REPORT_BASE,
            MissionStep.FLY_FINAL, MissionStep.REPORT_FINAL,
            MissionStep.AWAIT_LANDING_CLEARANCE, MissionStep.LAND,
        )

        // Phase 6 — ground arrival → ARRIVE.
        assertEquals(PilotGoal.ARRIVE, derivePilotGoal(afterLand),
            "While GroundArrival is active, controller should see ARRIVE.")
    }

    private fun stepThrough(mission: PilotMission, vararg steps: MissionStep): PilotMission {
        var root = mission.root
        for (step in steps) root = root.markComplete(step)
        return mission.copy(root = root)
    }
}
