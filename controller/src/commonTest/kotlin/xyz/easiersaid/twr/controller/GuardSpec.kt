package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.CircuitIntentIs
import xyz.easiersaid.twr.controller.bdi.IsCircuitTraffic
import xyz.easiersaid.twr.controller.bdi.OperatorContext
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.CircuitIntent
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test

/**
 * Spec test for the new circuit-intent-aware guards (Phase B6).
 *
 * The guards' contract is trivial — read a belief slice — but they sit on
 * the *output* end of the radio→event→fold→belief→guard chain. If G0 wedges
 * with the rest of the chain green, the guard polarity is the next thing to
 * suspect. These tests pin polarity and the absent-entry default semantics.
 */
class GuardSpec {

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
        kind = CommitmentKind.TOWER_ARRIVAL,
        stage = TowerArrivalStage.AwaitDownwind,
        formedAt = SimTime.ZERO,
    )

    private fun ctx(beliefs: BeliefState): OperatorContext = OperatorContext(
        view = ControllerView(
            time = SimTime.ZERO,
            controllerId = ControllerId("TEST_TWR"),
            role = RoleName.TOWER,
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
    fun `CircuitIntentIs(FULL_STOP) is true when belief is FULL_STOP`() {
        val beliefs = BeliefState.EMPTY.copy(circuitIntent = mapOf(aircraft to CircuitIntent.FULL_STOP))
        val guard = CircuitIntentIs(CircuitIntent.FULL_STOP)
        check(guard.evaluate(ac, commitment, ctx(beliefs))) {
            "CircuitIntentIs(FULL_STOP) should evaluate true when belief is FULL_STOP"
        }
    }

    @Test
    fun `CircuitIntentIs(FULL_STOP) is false when belief is TOUCH_AND_GO`() {
        val beliefs = BeliefState.EMPTY.copy(circuitIntent = mapOf(aircraft to CircuitIntent.TOUCH_AND_GO))
        val guard = CircuitIntentIs(CircuitIntent.FULL_STOP)
        check(!guard.evaluate(ac, commitment, ctx(beliefs))) {
            "CircuitIntentIs(FULL_STOP) should evaluate false when belief is TOUCH_AND_GO"
        }
    }

    @Test
    fun `CircuitIntentIs(FULL_STOP) is false when belief is absent`() {
        // Operational default per ICAO/SERA: undeclared circuit traffic = T&G.
        // So the FULL_STOP-gated rule should NOT fire when the pilot hasn't
        // declared. The complementary `Not(CircuitIntentIs(FULL_STOP))` rule
        // (touch-and-go branch) fires by default.
        val guard = CircuitIntentIs(CircuitIntent.FULL_STOP)
        check(!guard.evaluate(ac, commitment, ctx(BeliefState.EMPTY))) {
            "CircuitIntentIs(FULL_STOP) should evaluate false when belief is absent"
        }
    }

    @Test
    fun `IsCircuitTraffic is true iff circuitIntent has any entry for the aircraft`() {
        val empty = ctx(BeliefState.EMPTY)
        val withFullStop = ctx(BeliefState.EMPTY.copy(circuitIntent = mapOf(aircraft to CircuitIntent.FULL_STOP)))
        val withTng = ctx(BeliefState.EMPTY.copy(circuitIntent = mapOf(aircraft to CircuitIntent.TOUCH_AND_GO)))

        check(!IsCircuitTraffic.evaluate(ac, commitment, empty)) {
            "IsCircuitTraffic should be false with no entry"
        }
        check(IsCircuitTraffic.evaluate(ac, commitment, withFullStop)) {
            "IsCircuitTraffic should be true with FULL_STOP entry"
        }
        check(IsCircuitTraffic.evaluate(ac, commitment, withTng)) {
            "IsCircuitTraffic should be true with TOUCH_AND_GO entry"
        }
    }
}
