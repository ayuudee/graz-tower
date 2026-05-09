package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.PilotRoute
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AftnAddress
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.ResponsibilityState
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pass 14 (D-AUDIT.6.A-FOLLOWUP / .6.B-FOLLOWUP / .13) — `knownStrips`
 * lifecycle bridge.
 *
 * Doctrine: ICAO Doc 4444 §10.1 (responsibility transfer); §11 (FPL filing).
 *
 * Four rows pin the handler-level invariants by driving the production
 * code path end-to-end (post-impl test review M1: prior versions of
 * rows 1+2 hand-shaped the post-state — those were rewritten to call
 * `applyContactFrequency` and `step()` directly):
 *  1. Intra-aerodrome handoff where target had a destination strip in
 *     knownStrips: `applyContactFrequency` clears the strip as the
 *     target enters Watching.
 *  2. Intra-aerodrome handoff where target had no prior strip: cleanup
 *     is a no-op; both sides reach the expected state.
 *  3. Refile-same-plan is idempotent.
 *  4. Refile-different-plan errors loudly with ICAO Doc 4444 §11.4 cite.
 */
class KnownStripsHandoffTransitionSpec {

    private val ac = AircraftId("OE-LJB")
    private val LOWG = AerodromeId("LOWG")
    private val LJMB = AerodromeId("LJMB")
    private val lowgGndId = ControllerId("LOWG_GROUND")
    private val lowgTwrId = ControllerId("LOWG_TOWER")
    private val ljmbTwrId = ControllerId("LJMB_TOWER")
    private val now0 = SimTime.ofMillis(0)
    private val now1 = SimTime.ofMillis(60_000)

    private fun stateWith(
        ctrls: Map<ControllerId, ControllerSpec>,
        aircraft: List<AircraftState> = emptyList(),
        now: SimTime = now0,
    ): SimState = SimState(
        now = now,
        seq = 0L,
        rng = SimRandom(0L),
        rngByAircraft = aircraft.associate { it.id to SimRandom(it.id.value.hashCode().toLong()) },
        aircraft = LinkedHashMap<AircraftId, AircraftState>().apply { aircraft.forEach { put(it.id, it) } },
        controllers = ctrls,
        beliefs = emptyMap(),
        world = AviationWorld(),
        worldIndex = WorldIndex(),
        weatherByAerodrome = emptyMap(),
    )

    private fun spec(
        id: ControllerId,
        role: RoleName,
        aerodrome: AerodromeId,
        responsibilities: Map<AircraftId, ResponsibilityState> = emptyMap(),
        knownStrips: Map<AircraftId, FiledPlan> = emptyMap(),
    ): ControllerSpec = ControllerSpec(
        id = id,
        role = role,
        aerodromeId = aerodrome,
        frequency = Frequency.unsafe("118.200"),
        responsibilities = responsibilities,
        knownStrips = knownStrips,
    )

    private fun aircraftAt(point: PointId): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OELJB"),
        position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
        positionPoint = point,
        phase = PilotPhase.Climbing,
        route = PilotRoute.None,
    )

    /**
     * Plan filed at LOWG inbound to LJMB — but the destination strip is
     * directed at LOWG (i.e. an inbound-to-LOWG cross-aerodrome plan).
     * Used in row 1 to set up a knownStrips entry on LOWG_TWR that
     * `applyContactFrequency` (intra-LOWG handoff) can then clear.
     */
    private val inboundToLowgPlan = FiledPlan.Vfr(
        departureAerodrome = LJMB,
        destinationAerodrome = LOWG,
        intent = AircraftIntent.Departing,
    )

    @Test
    fun `applyContactFrequency clears knownStrips entry on target as Watching arrives per Doc 4444 sec10dot1`() {
        // Setup: LOWG_GROUND owns the aircraft (current owner). LOWG_TWR
        // has the destination strip in knownStrips (the aircraft was
        // filed inbound to LOWG; AFTN dispatched the destination strip
        // to LOWG_TWR before any radio activity). Now LOWG_GROUND issues
        // ContactFrequency(TOWER) — applyContactFrequency must transition
        // LOWG_TWR to Watching AND remove the prior knownStrips entry.
        val state = stateWith(
            linkedMapOf(
                lowgGndId to spec(
                    lowgGndId, RoleName.GROUND, LOWG,
                    responsibilities = mapOf(ac to ResponsibilityState.Owned(now0)),
                ),
                lowgTwrId to spec(
                    lowgTwrId, RoleName.TOWER, LOWG,
                    knownStrips = mapOf(ac to inboundToLowgPlan),
                ),
            ),
            aircraft = listOf(aircraftAt(PointId("LOWG_RUNWAY_END"))),
            now = now1,
        )

        // Drive the production code path: applyContactFrequency is the
        // function that fires on a ContactFrequency instruction's effect.
        val instruction = ContactFrequency(target = ac, role = RoleName.TOWER)
        val nextState = applyContactFrequency(state, state.aircraft.getValue(ac), instruction)

        val gndAfter = nextState.controllers.getValue(lowgGndId)
        val twrAfter = nextState.controllers.getValue(lowgTwrId)

        // LOWG_GROUND transitioned Owned → HandingOff(Peer(LOWG_TWR)).
        val gndState = gndAfter.responsibilities[ac]
        assertTrue(
            gndState is ResponsibilityState.HandingOff,
            "Doc 4444 §10.1: current owner transitions to HandingOff on ContactFrequency. Got: $gndState",
        )

        // LOWG_TWR gained Watching(from = LOWG_GROUND).
        val twrResp = twrAfter.responsibilities[ac]
        assertTrue(
            twrResp is ResponsibilityState.Watching,
            "Doc 4444 §10.1: peer becomes Watching on handoff. Got: $twrResp",
        )

        // Pass 14 disjointness invariant: the prior knownStrips entry
        // was cleared so the post-state respects strip ≠ responsibility.
        assertNull(
            twrAfter.knownStrips[ac],
            "knownStrips entry must clear on Watching transition (disjointness invariant)",
        )
    }

    @Test
    fun `intra-aerodrome handoff with no prior knownStrips leaves target in clean Watching state`() {
        // Symmetric to row 1 but the target had NO prior knownStrips
        // entry (typical intra-aerodrome handoff: AFTN didn't pre-deliver
        // anything to this controller). The cleanup `knownStrips - ac`
        // is a structural no-op; the post-state must still be valid
        // (Watching present, knownStrips empty).
        val state = stateWith(
            linkedMapOf(
                lowgGndId to spec(
                    lowgGndId, RoleName.GROUND, LOWG,
                    responsibilities = mapOf(ac to ResponsibilityState.Owned(now0)),
                ),
                lowgTwrId to spec(lowgTwrId, RoleName.TOWER, LOWG), // empty knownStrips
            ),
            aircraft = listOf(aircraftAt(PointId("LOWG_RUNWAY_END"))),
            now = now1,
        )
        val instruction = ContactFrequency(target = ac, role = RoleName.TOWER)
        val nextState = applyContactFrequency(state, state.aircraft.getValue(ac), instruction)
        val twrAfter = nextState.controllers.getValue(lowgTwrId)
        assertTrue(
            twrAfter.responsibilities[ac] is ResponsibilityState.Watching,
            "target reaches Watching even when no prior strip existed",
        )
        assertTrue(twrAfter.knownStrips.isEmpty(), "knownStrips remains empty (no entry to remove)")
    }

    @Test
    fun `refile-same-plan is idempotent on the arrival side`() {
        val crossAerodromePlan = FiledPlan.Vfr(
            departureAerodrome = LOWG,
            destinationAerodrome = LJMB,
            intent = AircraftIntent.Departing,
        )
        val state = stateWith(
            linkedMapOf(
                ljmbTwrId to spec(
                    ljmbTwrId, RoleName.TOWER, LJMB,
                    knownStrips = mapOf(ac to crossAerodromePlan),
                ),
            ),
        )
        val event = SimEvent.FlightPlanFiled(
            time = now0,
            aircraft = ac,
            plan = crossAerodromePlan,
            recipient = AftnAddress(LJMB, RoleName.TOWER),
        )
        val (next, emitted) = step(state, event)
        assertEquals(emptyList(), emitted, "refile emits no follow-up events")
        assertEquals(
            mapOf(ac to crossAerodromePlan),
            next.controllers.getValue(ljmbTwrId).knownStrips,
            "byte-identical refile leaves knownStrips unchanged (idempotent)",
        )
    }

    @Test
    fun `refile-different-plan errors loudly with ICAO Doc 4444 sec11dot4 cite`() {
        // Per ICAO Doc 4444 §11.4 (FPL amendment via CHG message), the
        // real-world flow uses a separate amendment message, not a
        // re-filed FPL. TWR2 deferment D-AUDIT.6.C-FOLLOWUP tracks the
        // amendment-update flow; today refile must be byte-identical.
        val originalPlan = FiledPlan.Vfr(
            departureAerodrome = LOWG,
            destinationAerodrome = LJMB,
            intent = AircraftIntent.Departing,
        )
        val state = stateWith(
            linkedMapOf(
                ljmbTwrId to spec(
                    ljmbTwrId, RoleName.TOWER, LJMB,
                    knownStrips = mapOf(ac to originalPlan),
                ),
            ),
        )
        val amendedPlan = originalPlan.copy(intent = AircraftIntent.Transit)
        val event = SimEvent.FlightPlanFiled(
            time = now0,
            aircraft = ac,
            plan = amendedPlan,
            recipient = AftnAddress(LJMB, RoleName.TOWER),
        )
        try {
            step(state, event)
            fail("expected loud error: refile-with-different-plan is D-AUDIT.6.C-FOLLOWUP")
        } catch (e: IllegalStateException) {
            assertTrue(
                e.message!!.contains("Doc 4444 §11.4"),
                "diagnostic must cite ICAO Doc 4444 §11.4 (FPL amendment): ${e.message}",
            )
            assertTrue(
                e.message!!.contains("D-AUDIT.6.C-FOLLOWUP"),
                "diagnostic must cite the deferment governing strip-amendment: ${e.message}",
            )
        }
    }
}
