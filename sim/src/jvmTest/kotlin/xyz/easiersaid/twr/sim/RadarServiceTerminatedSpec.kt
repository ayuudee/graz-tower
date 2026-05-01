package xyz.easiersaid.twr.sim

import arrow.core.None
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.HandoffTarget
import xyz.easiersaid.twr.protocol.RadarServiceTerminated
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test

/**
 * Pass 7 (D-PF.7) — focused integration test for the boundary-release path.
 *
 * The Pass 6 + Pass 7 published-vs-staffed split ([D-AUDIT.12], [D-PF.7]):
 * an aerodrome may publish a role that isn't staffed in the current run.
 * LOWG publishes APPROACH (real GRAZ RADAR) but the standard test fixture
 * stages only TOWER + GROUND. When a non-circuit departure climbs past
 * the CTR boundary, the controller's DEP-APPROACH-HANDOFF rule's
 * `IsTransferTargetStaffed` guard fails. The sibling
 * DEP-RADAR-SERVICE-TERMINATED rule fires instead, emitting
 * `RadarServiceTerminated` per ICAO Doc 4444 §10.1.4.
 *
 * **Scope (Test-4b post-impl fold-in)**: this test pins the sim-side
 * apply path ([applyRadarServiceTerminated], [applyBoundaryReleaseReadback])
 * end-to-end. The rule firing itself is covered architecturally by
 * `BoundaryReleaseFirewallTest`. This test exercises:
 *
 *  1. The sim-side `applyRadarServiceTerminated` transitions current
 *     controller `Owned → HandingOff(Released)`.
 *  2. The sim-side `applyBoundaryReleaseReadback` drops the aircraft
 *     entry from the controller's responsibilities.
 *  3. The cross-controller invariant (no two-Owned, no orphan Watching)
 *     holds across the full apply sequence.
 *
 * The full G0-scale "fly a Departure mission to 12 NM and watch the rule
 * fire" integration test is deferred — its preconditions are met but its
 * runtime cost (multi-minute sim) without commensurate diagnostic value
 * over this focused spec is poor trade. When G2 or a multi-aerodrome
 * test exercises the path naturally, the integration coverage emerges
 * for free.
 */
class RadarServiceTerminatedSpec {

    private val ac = AircraftId("OE-ABC")
    private val twrId = xyz.easiersaid.twr.protocol.ControllerId("LOWG_TOWER")
    private val now0 = SimTime.ofMillis(0)
    private val now1 = SimTime.ofMillis(1_000)
    private val now2 = SimTime.ofMillis(2_000)

    private fun towerSpec(responsibilities: Map<AircraftId, ResponsibilityState> = emptyMap()): ControllerSpec =
        ControllerSpec(
            id = twrId,
            role = RoleName.TOWER,
            aerodromeId = xyz.easiersaid.twr.protocol.AerodromeId("LOWG"),
            frequency = xyz.easiersaid.twr.protocol.Frequency.unsafe("118.200"),
            responsibilities = responsibilities,
        )

    private fun aircraft(): xyz.easiersaid.twr.pilot.AircraftState = xyz.easiersaid.twr.pilot.AircraftState(
        id = ac,
        callsign = xyz.easiersaid.twr.protocol.Callsign("OE-ABC"),
        position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
        positionPoint = xyz.easiersaid.twr.protocol.PointId("P"),
    )

    private fun stateWith(time: SimTime, twr: ControllerSpec): SimState = SimState(
        now = time,
        seq = 0L,
        rng = SimRandom(0L),
        aircraft = LinkedHashMap<AircraftId, xyz.easiersaid.twr.pilot.AircraftState>().apply {
            put(ac, aircraft())
        },
        controllers = linkedMapOf(twr.id to twr),
        beliefs = emptyMap(),
        world = xyz.easiersaid.twr.core.world.AviationWorld(),
        worldIndex = xyz.easiersaid.twr.core.world.WorldIndex(),
        weatherByAerodrome = emptyMap(),
    )

    @Test
    fun `applyRadarServiceTerminated transitions Owned to HandingOff(Released)`() {
        val twr = towerSpec(mapOf(ac to ResponsibilityState.Owned(now0)))
        val state = stateWith(now1, twr)

        val instruction = RadarServiceTerminated(
            target = ac, suggestedFrequency = None,
            squawk = arrow.core.Some(xyz.easiersaid.twr.protocol.Squawk.unsafe(7000)),
        )
        val next = applyRadarServiceTerminated(state, state.aircraft.getValue(ac), instruction)

        val st = next.controllers.getValue(twrId).responsibilities[ac]
        check(st is ResponsibilityState.HandingOff) {
            "Expected HandingOff after RadarServiceTerminated; got $st"
        }
        check(st.target is HandoffTarget.Released) {
            "Expected HandingOff.target = Released; got ${st.target}"
        }
        check(st.since == now1) {
            "Expected since=$now1; got ${st.since}"
        }
    }

    @Test
    fun `applyBoundaryReleaseReadback drops the aircraft entry`() {
        val twr = towerSpec(mapOf(ac to ResponsibilityState.HandingOff(target = HandoffTarget.Released, since = now1)))
        val state = stateWith(now2, twr)

        val next = applyBoundaryReleaseReadback(state, state.aircraft.getValue(ac))

        check(ac !in next.controllers.getValue(twrId).responsibilities) {
            "Expected aircraft entry to be dropped; got ${next.controllers.getValue(twrId).responsibilities}"
        }
    }

    @Test
    fun `full release sequence preserves the cross-controller invariant`() {
        // Owned → HandingOff(Released) → entry removed.
        val twr = towerSpec(mapOf(ac to ResponsibilityState.Owned(now0)))
        val state0 = stateWith(now0, twr)

        val instruction = RadarServiceTerminated(target = ac)
        val state1 = applyRadarServiceTerminated(state0, state0.aircraft.getValue(ac), instruction)
        // Invariant holds at intermediate state.
        assertResponsibilityInvariant(state1)

        val state2 = applyBoundaryReleaseReadback(state1, state1.aircraft.getValue(ac))
        // Invariant holds at terminal state.
        assertResponsibilityInvariant(state2)
        check(ac !in state2.controllers.getValue(twrId).responsibilities) {
            "Expected aircraft to be released by terminal state"
        }
    }

    @Test
    fun `applyRadarServiceTerminated preserves the squawk on the instruction (informational, not propagated)`() {
        // Pass 7 post-impl Impact-O.3 deferral note: the instruction's
        // squawk field is consumed at the readback level (SquawkReadback
        // atom). AircraftState has no `squawk` field today; sim-side
        // propagation is out of scope until a future pass adds the field.
        // This test pins the deferment by NOT asserting that aircraft.squawk
        // changes — it documents the current contract.
        val twr = towerSpec(mapOf(ac to ResponsibilityState.Owned(now0)))
        val state = stateWith(now1, twr)

        val instruction = RadarServiceTerminated(
            target = ac, squawk = arrow.core.Some(xyz.easiersaid.twr.protocol.Squawk.unsafe(7000)),
        )
        val next = applyRadarServiceTerminated(state, state.aircraft.getValue(ac), instruction)

        // Aircraft state unchanged (no squawk field to propagate to).
        check(next.aircraft.getValue(ac) == state.aircraft.getValue(ac)) {
            "Pass 7 does not propagate squawk to aircraft state — when AircraftState.squawk lands, this test should be updated to assert propagation."
        }
    }
}

