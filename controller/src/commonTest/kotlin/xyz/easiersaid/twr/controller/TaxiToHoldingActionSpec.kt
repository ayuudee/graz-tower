package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.ActionResolutionFailure
import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.GroundDepartureStage
import xyz.easiersaid.twr.controller.bdi.OperatorContext
import xyz.easiersaid.twr.controller.bdi.TaxiToHoldingAction
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import kotlin.test.Test
import kotlin.test.fail

/**
 * Spec test (Test review M.5) — `TaxiToHoldingAction`'s failure path.
 *
 * `TaxiToHoldingAction.resolve` reads `commitment.runway ?: ctx.beliefs.activeRunway`.
 * If both are null, the action returns `ActionResolutionFailure("No runway
 * for holding point lookup")`. G0 always has a non-null commitment runway,
 * so the failure path is dead-untested under the integration corpus. Pass
 * 6 (D-PF.6) makes the runway field load-bearing — the failure case
 * matters now.
 *
 * This is the only justified targeted controller-side spec for Pass 6 —
 * a real failure mode the rest of the test suite doesn't cover.
 */
class TaxiToHoldingActionSpec {

    private val aircraft = AircraftId("OE-ABC")

    // fn-6.1: seed WorldIndex with the test point so `fromTestPoint` derives
    // coords non-divergently. This spec exercises TaxiToHoldingAction's
    // failure path (no runway available); coords are not load-bearing here,
    // but going through the helper preserves the no-fixture-drift invariant.
    private val testWorldIndex = WorldIndex(
        positions = mapOf(PointId("P") to Position(xMeters = 0.0, yMeters = 0.0)),
    )

    private val ac = AircraftObservation.fromTestPoint(
        point = PointId("P"),
        worldIndex = testWorldIndex,
        id = aircraft,
        callsign = Callsign("OEABC"),
        onGround = true,
    )

    private val commitment = Commitment(
        aircraft = aircraft,
        kind = CommitmentKind.GROUND_TAXI,
        stage = GroundDepartureStage.AwaitTaxiRequest,
        runway = null,
        formedAt = SimTime.ZERO,
    )

    private fun ctx(beliefs: BeliefState): OperatorContext = OperatorContext(
        view = ControllerView(
            time = SimTime.ZERO,
            controllerId = ControllerId("TEST_GND"),
            role = RoleName.GROUND,
            aerodromeId = AerodromeId("LOWG"),
            responsibilities = setOf(aircraft),
            aircraft = mapOf(aircraft to ac),
            runways = emptyMap(),
            activeClearances = emptyMap(),
            receivedMessages = emptyList(),
            weather = null,
            worldIndex = WorldIndex(),
        ),
        beliefs = beliefs,
        events = emptyList(),
        world = AviationWorld(),
    )

    @Test
    fun `resolve returns ActionResolutionFailure when both commitment runway and beliefs activeRunway are null`() {
        val beliefs = BeliefState.EMPTY  // activeRunway = null
        val result = TaxiToHoldingAction.resolve(ac, commitment, ctx(beliefs))
        check(result.isLeft()) {
            "Expected ActionResolutionFailure when no runway is available; got $result"
        }
        result.fold(
            { failure ->
                check(failure is ActionResolutionFailure && failure.reason.contains("No runway")) {
                    "Expected reason to mention 'No runway'; got ${failure}"
                }
            },
            { error("Expected Left, got Right: $it") },
        )
    }

    /**
     * Pass 6 post-impl (Test-M.2): commitment.runway-vs-beliefs.activeRunway
     * precedence. The action reads `commitment.runway ?: ctx.beliefs.activeRunway`,
     * so a non-null commitment runway should win over a different value on
     * beliefs. G0 always has the same runway on both, so this precedence
     * cell is otherwise unexercised. Pin it: aircraft on a holding point
     * for runway 16C gets a TaxiToHoldingPoint(runway=16C) even though
     * beliefs.activeRunway is 16L.
     */
    @Test
    fun `resolve reads commitment runway when present, ignoring beliefs activeRunway`() {
        val rwy16C = RunwayId("16C")
        val rwy16L = RunwayId("16L")
        val holdingPoint16C = PointId("HOLDING_POINT_16C")
        val worldIndex = WorldIndex(
            holdingPointsByRunway = mapOf(rwy16C to setOf(holdingPoint16C)),
            positions = mapOf(holdingPoint16C to xyz.easiersaid.twr.core.world.Position(0.0, 0.0)),
        )
        val acAtHolding = ac.copy(position = holdingPoint16C)
        val commitmentForRunway16C = commitment.copy(runway = rwy16C)
        val beliefs = BeliefState.EMPTY.copy(activeRunway = rwy16L)
        val ctxWithIndex = OperatorContext(
            view = ControllerView(
                time = SimTime.ZERO,
                controllerId = ControllerId("TEST_GND"),
                role = RoleName.GROUND,
                aerodromeId = AerodromeId("LOWG"),
                responsibilities = setOf(aircraft),
                aircraft = mapOf(aircraft to acAtHolding),
                runways = emptyMap(),
                activeClearances = emptyMap(),
                receivedMessages = emptyList(),
                weather = null,
                worldIndex = worldIndex,
            ),
            beliefs = beliefs,
            events = emptyList(),
            world = AviationWorld(),
        )
        val result = TaxiToHoldingAction.resolve(acAtHolding, commitmentForRunway16C, ctxWithIndex)
        result.fold(
            { fail("Expected Right with commitment-runway TaxiToHoldingPoint; got Left $it") },
            { proposed ->
                val instr = proposed.instruction as? TaxiToHoldingPoint
                    ?: fail("Expected TaxiToHoldingPoint; got ${proposed.instruction}")
                check(instr.runway == rwy16C) {
                    "Expected runway 16C from commitment, not 16L from beliefs.activeRunway; got ${instr.runway}"
                }
            },
        )
    }
}
