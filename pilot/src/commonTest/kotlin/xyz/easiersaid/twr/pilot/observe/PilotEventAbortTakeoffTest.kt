package xyz.easiersaid.twr.pilot.observe

import arrow.core.Some
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.pilot.AircraftState
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * fn-28.9 (G0 abort-takeoff): `deriveAbortTakeoffEvent` recognition
 * contract — 4-check gate pin.
 *
 * **R16 recognition+apply agreement**: the derivation gates on
 * `isAbortTakeoffEligible` (takeoff-roll mission shape); the apply stage
 * `applyAbortTakeoff` uses the same predicate. The tests below pin the
 * derivation behaviour for the common cases — the agreement contract is
 * enforced by re-using the named guard in both sites.
 *
 * **R21 branch order**: tested at the function-composition level via the
 * `derivePilotEvent` top-level — the AbortTakeoff branch slots BETWEEN
 * DA-decline and tailwind. Co-occurrence with DA-decline is structurally
 * impossible (mission shape disjoint); co-occurrence with tailwind /
 * crosswind requires `phase = Final` (also disjoint).
 */
class PilotEventAbortTakeoffTest {

    private val ac = AircraftId("OE-ABC")
    private val now0 = SimTime.ZERO
    private val rwy = RunwayId("16C")

    private fun aircraft(
        engineRunning: Boolean,
        speedMps: Double,
        phase: PilotPhase,
        type: AircraftType = AircraftType.C172,
    ): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEABC"),
        position = Position(0.0, 0.0),
        positionPoint = PointId("LOWG_RWY_16C_THR"),
        altitudeM = 0.0,
        phase = phase,
        type = type,
        speedMps = speedMps,
        engineRunning = engineRunning,
    )

    private fun missionWithStep(step: MissionStep): PilotMission = PilotMission(
        goal = HighLevelGoal.Departure(),
        root = CompoundTask(
            name = TaskName.Depart,
            children = listOf(PrimitiveTask(step, CompletionMode.PHYSICAL)),
        ),
        stepEnteredAt = now0,
        activeRunway = Some(RunwayAssignment(rwy, RunwayAssignmentSource.Filing)),
    )

    // C172 rotationSpeedMps = 28.0 (per AircraftType.C172.kinematics).
    private val belowRotationSpeed = 15.0
    private val aboveRotationSpeed = 30.0

    @Test
    fun `fires on C172 + engineRunning=false + speed below VR + TakeoffRoll + FLY_DEPARTURE`() {
        val event = derivePilotEvent(
            aircraft = aircraft(
                engineRunning = false,
                speedMps = belowRotationSpeed,
                phase = PilotPhase.TakeoffRoll,
            ),
            mission = missionWithStep(MissionStep.FLY_DEPARTURE),
            weather = null,
        )
        assertTrue(
            event is PilotEvent.AbortTakeoff,
            "all 4 gates satisfied → AbortTakeoff MUST fire; got $event",
        )
        assertEquals(ac, event.aircraft, "event carries the aircraft id")
        assertEquals(
            belowRotationSpeed,
            event.speedAtFailure,
            "event carries the observed speedMps at failure for trace coherence",
        )
    }

    @Test
    fun `fires on AWAIT_TAKEOFF_CLEARANCE — the other eligible step`() {
        // Confirms the mission-shape gate accepts both eligible steps.
        // (`isAbortTakeoffEligible` enumerates AWAIT_TAKEOFF_CLEARANCE
        // and FLY_DEPARTURE; the recognition path threads through the
        // same guard.)
        val event = derivePilotEvent(
            aircraft = aircraft(
                engineRunning = false,
                speedMps = 0.0, // at-holding-point, engine just failed
                phase = PilotPhase.TakeoffRoll,
            ),
            mission = missionWithStep(MissionStep.AWAIT_TAKEOFF_CLEARANCE),
            weather = null,
        )
        assertTrue(
            event is PilotEvent.AbortTakeoff,
            "AWAIT_TAKEOFF_CLEARANCE + engineRunning=false + pre-VR + TakeoffRoll " +
                "MUST fire AbortTakeoff; got $event",
        )
    }

    @Test
    fun `does NOT fire when engineRunning=true — gate 1 fails closed`() {
        val event = derivePilotEvent(
            aircraft = aircraft(
                engineRunning = true,
                speedMps = belowRotationSpeed,
                phase = PilotPhase.TakeoffRoll,
            ),
            mission = missionWithStep(MissionStep.FLY_DEPARTURE),
            weather = null,
        )
        assertNull(
            event,
            "engineRunning=true → no abort recognition; got $event",
        )
    }

    @Test
    fun `does NOT fire when speed at-or-above rotation speed — gate 2 fails closed`() {
        val event = derivePilotEvent(
            aircraft = aircraft(
                engineRunning = false,
                speedMps = aboveRotationSpeed,
                phase = PilotPhase.TakeoffRoll,
            ),
            mission = missionWithStep(MissionStep.FLY_DEPARTURE),
            weather = null,
        )
        assertNull(
            event,
            "speed >= rotationSpeedMps → no abort recognition; engine-out climb is " +
                "a different emergency class out of fn-28 scope. got $event",
        )
    }

    @Test
    fun `does NOT fire at exactly rotation speed — strict inequality boundary`() {
        // C172.kinematics.rotationSpeedMps = 28.0. Exactly 28.0 should
        // NOT fire (strict `<`). Mirrors the wind-axis branches' strict
        // inequality discipline.
        val exactlyVr = AircraftType.C172.kinematics.rotationSpeedMps
        val event = derivePilotEvent(
            aircraft = aircraft(
                engineRunning = false,
                speedMps = exactlyVr,
                phase = PilotPhase.TakeoffRoll,
            ),
            mission = missionWithStep(MissionStep.FLY_DEPARTURE),
            weather = null,
        )
        assertNull(
            event,
            "speed exactly == rotationSpeedMps → no abort (strict `<` boundary); got $event",
        )
    }

    @Test
    fun `does NOT fire when phase is not TakeoffRoll — gate 3 fails closed`() {
        // Phase guard is the v1 on-runway proxy (round-4 Major 5). Engine
        // failure on `AtStand`, `LinedUp`, `Climbing`, `Final` etc. does
        // not trigger abort — those are either pre-roll or airborne
        // (different emergency class).
        listOf(
            PilotPhase.AtStand,
            PilotPhase.Taxiing,
            PilotPhase.HoldingShort,
            PilotPhase.LinedUp,
            PilotPhase.Climbing,
            PilotPhase.Crosswind,
            PilotPhase.Downwind,
            PilotPhase.Base,
            PilotPhase.Final,
            PilotPhase.LandingRoll,
            PilotPhase.Vacating,
            PilotPhase.ClearOfRunway,
            PilotPhase.Parked,
        ).forEach { phase ->
            val event = derivePilotEvent(
                aircraft = aircraft(
                    engineRunning = false,
                    speedMps = belowRotationSpeed,
                    phase = phase,
                ),
                mission = missionWithStep(MissionStep.FLY_DEPARTURE),
                weather = null,
            )
            assertNull(
                event,
                "phase=$phase → no abort recognition (only TakeoffRoll fires); got $event",
            )
        }
    }

    @Test
    fun `does NOT fire when mission shape is not abort-eligible — exhaustive gate 4 enumeration`() {
        // Codex round-2 finding 1 fix: exhaustive enumeration over
        // `MissionStep.entries` instead of a hand-maintained negative
        // list (the prior hand-maintained list was missing
        // `FLY_FINAL_TO_SHORT_FINAL`, `FLY_STAR`, `AWAITING_ATC_INSTRUCTION`,
        // and several others). The single source of truth for the
        // eligible set is `isAbortTakeoffEligible` in PilotMission.kt;
        // this test enumerates every MissionStep value and asserts that
        // exactly the eligible set fires and every other step does not.
        //
        // The eligible-step rows (AWAIT_TAKEOFF_CLEARANCE +
        // FLY_DEPARTURE) are covered by the positive-row tests above
        // (`fires on C172 + ...` + `fires on AWAIT_TAKEOFF_CLEARANCE
        // ...`). This row covers all the negative-space rows in one pass.
        //
        // **Cross-branch dependency** (fn-32.3 / plan-review R2): the
        // top-level `derivePilotEvent` chains DA-without-clearance →
        // DA-decline → AbortTakeoff. The earlier DA-without-clearance
        // branch fires for on-approach steps (FLY_FINAL / FLY_BASE /
        // REPORT_FINAL / REPORT_BASE / AWAIT_LANDING_CLEARANCE) when
        // `altitudeM <= DECISION_ALTITUDE_M = 100.0` and no clearance is
        // set. To isolate gate 4 of the abort branch under test, we lift
        // the negative-row aircraft above the decision altitude
        // (`altitudeM = 200.0` > 100.0) — abort gates don't read
        // altitudeM, so this leaves all 4 abort gates intact while
        // neutralising the earlier branch. Any future contract drift in
        // either branch's altitude/clearance/mission-step gates will
        // surface here as a fresh failure.
        val eligible = setOf(
            MissionStep.AWAIT_TAKEOFF_CLEARANCE,
            MissionStep.FLY_DEPARTURE,
        )
        val aboveDecisionAlt = 200.0
        for (step in MissionStep.entries) {
            if (step in eligible) continue  // positive rows above cover these
            val event = derivePilotEvent(
                aircraft = aircraft(
                    engineRunning = false,
                    speedMps = belowRotationSpeed,
                    phase = PilotPhase.TakeoffRoll,
                ).copy(altitudeM = aboveDecisionAlt),
                mission = missionWithStep(step),
                weather = null,
            )
            assertNull(
                event,
                "MissionStep.$step is not in the abort-eligible set " +
                    "$eligible → abort gate 4 must fail closed; got $event. " +
                    "If a future broadening of `isAbortTakeoffEligible` adds " +
                    "$step, update the `eligible` set here to match.",
            )
        }
    }

    @Test
    fun `B738 also fires — recognition uses per-type rotation speed`() {
        // B738.kinematics.rotationSpeedMps = 75.0 (per AircraftType.B738).
        // A speed of 30 m/s is well below B738's VR but above C172's VR;
        // confirms the recognition reads `aircraft.type.kinematics.rotationSpeedMps`
        // not a hardcoded threshold.
        val event = derivePilotEvent(
            aircraft = aircraft(
                engineRunning = false,
                speedMps = 30.0,
                phase = PilotPhase.TakeoffRoll,
                type = AircraftType.B738,
            ),
            mission = missionWithStep(MissionStep.FLY_DEPARTURE),
            weather = null,
        )
        assertTrue(
            event is PilotEvent.AbortTakeoff,
            "B738 at 30 m/s (< its 75 m/s VR) MUST fire AbortTakeoff; got $event",
        )
        assertEquals(30.0, event.speedAtFailure)
    }
}
