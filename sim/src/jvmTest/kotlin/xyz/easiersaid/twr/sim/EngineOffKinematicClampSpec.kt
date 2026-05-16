package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.PilotRoute
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.SimTime

/**
 * fn-28.8 (G0 abort-takeoff foundation R12 / round-3 Major 2): engine-off
 * kinematic-clamp contract.
 *
 * **Instant-speed clamp** — when `AircraftState.engineRunning == false`,
 * the physics integrator's working speed is bounded by
 * `min(targetSpeedMps, currentSpeedMps)`:
 *  - **Decel allowed**: when `targetSpeedMps < currentSpeedMps` (pilot
 *    commands brakes / spool-down), the integrator uses the lower target.
 *    The aircraft decelerates as commanded.
 *  - **Accel blocked**: when `targetSpeedMps > currentSpeedMps` (pilot
 *    commands MORE thrust), the integrator uses the lower (current) value.
 *    The aircraft does NOT accelerate — engine-off has no thrust.
 *  - **Equal**: no change either way.
 *
 * The clamp lives in `advanceKinematics` and is exercised by the standard
 * `SimEvent.PhysicsTick` handler. This spec drives the public sim API
 * (`step()`) with a PhysicsTick over engine-on / engine-off aircraft and
 * pins the contract by sampling `speedMps` post-tick.
 *
 * Pre-fn-28.8 behaviour (engine on) is preserved by the same code path —
 * the test's first case exercises that the engine-on aircraft accelerates
 * to its target as before. The engine-off cases exercise the clamp.
 */
class EngineOffKinematicClampSpec {

    private val aircraftId = AircraftId("OE-ABC")
    private val standPoint = PointId("STAND_A")

    private val worldIndex = WorldIndex(
        positions = mapOf(standPoint to Position(xMeters = 0.0, yMeters = 0.0)),
    )

    private fun stateWithAircraft(ac: AircraftState): SimState = SimState.initial(
        seed = 42L,
        worldIndex = worldIndex,
        aircraft = listOf(ac),
        weatherByAerodrome = emptyMap<AerodromeId, xyz.easiersaid.twr.core.world.WeatherObservation>(),
    ).getOrElse { error("SimState.initial rejected fixture: $it") }

    private fun makeAircraft(
        speedMps: Double,
        targetSpeedMps: Double,
        engineRunning: Boolean,
    ): AircraftState = AircraftState(
        id = aircraftId,
        callsign = Callsign("OE-ABC"),
        position = worldIndex.positions.getValue(standPoint),
        positionPoint = standPoint,
        speedMps = speedMps,
        targetSpeedMps = targetSpeedMps,
        // No route — `advanceKinematics`'s `headPoint == null` early-return
        // exercises the speed-assignment code path without movement, which
        // is precisely what this clamp test is interested in (the speed
        // returned by `advanceKinematics`).
        route = PilotRoute.None,
        engineRunning = engineRunning,
    )

    @Test
    fun `engine running — speed advances to target (regression-prevention for pre-fn-28_8 baseline)`() {
        // Pre-fn-28.8 baseline: engine on, target > current, speed advances
        // to target verbatim. The engine-off clamp must not regress this.
        val ac = makeAircraft(speedMps = 0.0, targetSpeedMps = 10.0, engineRunning = true)
        val state = stateWithAircraft(ac)
        val (next, _) = step(state, SimEvent.PhysicsTick(time = SimTime.ZERO))
        val updated = next.aircraft.getValue(aircraftId)
        assertEquals(
            10.0, updated.speedMps,
            "engine-on baseline: advanceKinematics assigns speedMps = targetSpeedMps (no clamp)",
        )
    }

    @Test
    fun `engine off + accel command — accel BLOCKED (target greater than current is clamped)`() {
        // Engine off, current speed 5 m/s, target 30 m/s (pilot commands
        // climb-speed). The clamp blocks the accel: min(30, 5) = 5.
        // The aircraft's working speed remains 5; no thrust → no accel.
        val ac = makeAircraft(speedMps = 5.0, targetSpeedMps = 30.0, engineRunning = false)
        val state = stateWithAircraft(ac)
        val (next, _) = step(state, SimEvent.PhysicsTick(time = SimTime.ZERO))
        val updated = next.aircraft.getValue(aircraftId)
        assertEquals(
            5.0, updated.speedMps,
            "engine-off accel: min(target=30, current=5) = 5; engine has no thrust to accelerate",
        )
    }

    @Test
    fun `engine off + decel command — decel ALLOWED (target less than current is honoured)`() {
        // Engine off, current 30 m/s (mid-roll), target 0 m/s (pilot
        // commands brakes / abort). The clamp allows the decel:
        // min(0, 30) = 0. Working speed is 0; the aircraft is at rest.
        // (v1 model is instant-speed; future fn-28 work may add a per-tick
        // decel rate — explicitly excluded from this task's scope.)
        val ac = makeAircraft(speedMps = 30.0, targetSpeedMps = 0.0, engineRunning = false)
        val state = stateWithAircraft(ac)
        val (next, _) = step(state, SimEvent.PhysicsTick(time = SimTime.ZERO))
        val updated = next.aircraft.getValue(aircraftId)
        assertEquals(
            0.0, updated.speedMps,
            "engine-off decel: min(target=0, current=30) = 0; pilot's brake/abort intent honoured",
        )
    }

    @Test
    fun `engine off + equal command — speed unchanged (boundary)`() {
        // Engine off, current == target == 5. min(5, 5) = 5. No change.
        val ac = makeAircraft(speedMps = 5.0, targetSpeedMps = 5.0, engineRunning = false)
        val state = stateWithAircraft(ac)
        val (next, _) = step(state, SimEvent.PhysicsTick(time = SimTime.ZERO))
        val updated = next.aircraft.getValue(aircraftId)
        assertEquals(
            5.0, updated.speedMps,
            "engine-off equal: min(target=5, current=5) = 5; boundary preserves the steady-state speed",
        )
    }

    @Test
    fun `engine off + at-rest — speed remains zero (defensive zero-speed coverage)`() {
        // Engine off + already at rest. min(0, 0) = 0. The clamp does not
        // accidentally produce a non-zero speed.
        val ac = makeAircraft(speedMps = 0.0, targetSpeedMps = 0.0, engineRunning = false)
        val state = stateWithAircraft(ac)
        val (next, _) = step(state, SimEvent.PhysicsTick(time = SimTime.ZERO))
        val updated = next.aircraft.getValue(aircraftId)
        assertEquals(
            0.0, updated.speedMps,
            "engine-off at-rest: speedMps stays 0 under the clamp",
        )
    }

    @Test
    fun `EngineFailure handler flips engineRunning to false; subsequent tick applies the clamp`() {
        // End-to-end pin: EngineFailure event → engineRunning=false → next
        // PhysicsTick applies the clamp.
        val ac = makeAircraft(speedMps = 25.0, targetSpeedMps = 30.0, engineRunning = true)
        val state = stateWithAircraft(ac)

        // Pre-failure: engine is running by construction.
        assertTrue(state.aircraft.getValue(aircraftId).engineRunning, "pre-failure: engine running")

        // Fire the engine-failure event.
        val (afterFailure, _) = step(state, SimEvent.EngineFailure(
            time = SimTime.ZERO,
            aircraftId = aircraftId,
        ))
        assertEquals(
            false, afterFailure.aircraft.getValue(aircraftId).engineRunning,
            "handleEngineFailure flips engineRunning to false",
        )

        // The next physics tick applies the clamp. Target (30) > current
        // (25) → clamp picks current.
        val (afterTick, _) = step(afterFailure, SimEvent.PhysicsTick(time = SimTime.ZERO))
        assertEquals(
            25.0, afterTick.aircraft.getValue(aircraftId).speedMps,
            "post-failure PhysicsTick: min(target=30, current=25) = 25; accel blocked",
        )
    }

    @Test
    fun `EngineFailure handler emits no events (no synthetic wake event — round-2 Major 4)`() {
        // Round-2 Major 4 contract pin: handleEngineFailure does NOT emit
        // a synthetic PilotDecisionTick. The pilot's regular tick cadence
        // picks the event up on its next scheduled tick.
        val ac = makeAircraft(speedMps = 0.0, targetSpeedMps = 0.0, engineRunning = true)
        val state = stateWithAircraft(ac)
        val (_, emitted) = step(state, SimEvent.EngineFailure(
            time = SimTime.ZERO,
            aircraftId = aircraftId,
        ))
        assertTrue(
            emitted.isEmpty(),
            "handleEngineFailure must not emit a synthetic wake-up event (round-2 Major 4)",
        )
    }
}
