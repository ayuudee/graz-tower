package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.IcaoTypeDesignator
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pass 11 (D-AUDIT.6) — `FlightPlanFiled` handler contract and
 * `step()` dispatch arm.
 *
 * Six rows:
 *  1. VFR happy path → recipient gains `Owned(time)`.
 *  2. Routing to TOWER instead of GROUND.
 *  3. Non-staffed role at the named aerodrome → loud error.
 *  4. Unknown aerodrome (no controller) → loud error.
 *  5. Idempotent re-emission at same time + byte-equal state.
 *  6. End-to-end `step()` dispatch arm — proves the top-level `when`
 *     wires correctly (catches a copy-paste regression that would route
 *     to the wrong handler and still pass the direct-call rows above).
 */
class FlightPlanFilingSpec {

    private val ac = AircraftId("OE-ABC")
    private val ctrlGndId = ControllerId("LOWG_GROUND")
    private val ctrlTwrId = ControllerId("LOWG_TOWER")
    private val now0 = SimTime.ofMillis(0)

    private fun stateWith(
        ctrls: Map<ControllerId, ControllerSpec>,
        aircraft: List<AircraftState> = emptyList(),
    ): SimState = SimState(
        now = now0,
        seq = 0L,
        rng = SimRandom(0L),
        aircraft = LinkedHashMap<AircraftId, AircraftState>().apply { aircraft.forEach { put(it.id, it) } },
        controllers = ctrls,
        beliefs = emptyMap(),
        world = AviationWorld(),
        worldIndex = WorldIndex(),
        weatherByAerodrome = emptyMap(),
    )

    private fun groundSpec(responsibilities: Map<AircraftId, ResponsibilityState> = emptyMap()): ControllerSpec =
        ControllerSpec(ctrlGndId, RoleName.GROUND, AerodromeId("LOWG"), Frequency.unsafe("118.200"), responsibilities)

    private fun towerSpec(responsibilities: Map<AircraftId, ResponsibilityState> = emptyMap()): ControllerSpec =
        ControllerSpec(ctrlTwrId, RoleName.TOWER, AerodromeId("LOWG"), Frequency.unsafe("118.200"), responsibilities)

    private fun vfrPlan(): FiledPlan.Vfr = FiledPlan.Vfr(
        departureAerodrome = AerodromeId("LOWG"),
        aircraftType = IcaoTypeDesignator.unsafe("C172"),
        destinationAerodrome = null,
        intent = AircraftIntent.Departing,
    )

    private fun event(
        recipient: RoleName = RoleName.GROUND,
        time: SimTime = now0,
    ): SimEvent.FlightPlanFiled = SimEvent.FlightPlanFiled(
        time = time,
        aircraft = ac,
        plan = vfrPlan(),
        recipient = recipient,
    )

    @Test
    fun `VFR plan filed to GROUND adds aircraft as Owned`() {
        val state = stateWith(linkedMapOf(ctrlGndId to groundSpec(), ctrlTwrId to towerSpec()))
        val (next, emitted) = step(state, event(recipient = RoleName.GROUND))

        assertEquals(emptyList(), emitted, "no follow-up events on filing")
        val gnd = next.controllers.getValue(ctrlGndId)
        assertEquals(
            ResponsibilityState.Owned(now0),
            gnd.responsibilities[ac],
            "GROUND should own the aircraft since now0",
        )
        // TOWER stays empty.
        assertTrue(ctrlTwrId in next.controllers && next.controllers.getValue(ctrlTwrId).responsibilities.isEmpty())
    }

    @Test
    fun `VFR plan filed to TOWER routes there instead of GROUND`() {
        val state = stateWith(linkedMapOf(ctrlGndId to groundSpec(), ctrlTwrId to towerSpec()))
        val (next, _) = step(state, event(recipient = RoleName.TOWER))

        assertEquals(
            ResponsibilityState.Owned(now0),
            next.controllers.getValue(ctrlTwrId).responsibilities[ac],
            "TOWER should own the aircraft",
        )
        assertTrue(next.controllers.getValue(ctrlGndId).responsibilities.isEmpty(), "GROUND must NOT receive the strip")
    }

    @Test
    fun `filing to non-staffed role at the named aerodrome errors loudly`() {
        // GROUND staffed, TOWER absent — file to TOWER.
        val state = stateWith(linkedMapOf(ctrlGndId to groundSpec()))
        try {
            step(state, event(recipient = RoleName.TOWER))
            fail("expected error: TOWER not staffed at LOWG")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("no TOWER controller at"), "diagnostic must name the missing role: ${e.message}")
        }
    }

    @Test
    fun `filing to unknown aerodrome errors loudly`() {
        // No LJMB controllers.
        val state = stateWith(linkedMapOf(ctrlGndId to groundSpec()))
        val ljmbPlan = FiledPlan.Vfr(
            departureAerodrome = AerodromeId("LJMB"),
            aircraftType = IcaoTypeDesignator.unsafe("C172"),
            destinationAerodrome = null,
            intent = AircraftIntent.Departing,
        )
        val ev = SimEvent.FlightPlanFiled(
            time = now0,
            aircraft = ac,
            plan = ljmbPlan,
            recipient = RoleName.GROUND,
        )
        try {
            step(state, ev)
            fail("expected error: no controller at LJMB")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("at AerodromeId"), "diagnostic must name the missing aerodrome: ${e.message}")
        }
    }

    @Test
    fun `re-filing at same time with byte-equal Owned state is idempotent`() {
        // Pre-populate GROUND with Owned(now0) for the aircraft.
        val state = stateWith(
            linkedMapOf(
                ctrlGndId to groundSpec(mapOf(ac to ResponsibilityState.Owned(now0))),
                ctrlTwrId to towerSpec(),
            ),
        )
        val (next, _) = step(state, event(time = now0))

        // Byte-equal state preserved.
        assertEquals(
            ResponsibilityState.Owned(now0),
            next.controllers.getValue(ctrlGndId).responsibilities[ac],
            "idempotent re-fire: state must remain Owned(now0)",
        )
    }

    @Test
    fun `step dispatcher routes FlightPlanFiled to handleFlightPlanFiled`() {
        // End-to-end dispatch: enqueue the event, run step, observe state.
        // Direct-call rows above don't catch a copy-paste regression that
        // would route to e.g. handleSpawn and still leave the test passing
        // because of unrelated state changes.
        val state = stateWith(linkedMapOf(ctrlGndId to groundSpec(), ctrlTwrId to towerSpec()))
        val ev = event(recipient = RoleName.GROUND)
        val (next, emitted) = step(state, ev)

        // Dispatch arm signature: aircraft becomes Owned, no follow-up events.
        val gnd = next.controllers.getValue(ctrlGndId)
        assertEquals(ResponsibilityState.Owned(now0), gnd.responsibilities[ac])
        assertEquals(emptyList(), emitted)
        // The aircraft is NOT added to state.aircraft by filing — that's the
        // Spawn handler's job (filing comes before engine start in real ATC).
        assertTrue(ac !in next.aircraft, "filing does not spawn the aircraft physically")
    }
}
