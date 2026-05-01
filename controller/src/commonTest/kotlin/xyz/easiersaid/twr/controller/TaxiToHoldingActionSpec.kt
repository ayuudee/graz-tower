package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.ActionResolutionFailure
import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.GroundDepartureStage
import xyz.easiersaid.twr.controller.bdi.OperatorContext
import xyz.easiersaid.twr.controller.bdi.TaxiToHoldingAction
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test

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

    private val ac = AircraftObservation(
        id = aircraft,
        callsign = Callsign("OEABC"),
        position = PointId("P"),
        entities = emptySet(),
        altitude = null,
        speed = null,
        heading = null,
        groundSpeed = null,
        onGround = true,
        wakeCategory = null,
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
            pendingInboundHandoffs = emptyList(),
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
}
