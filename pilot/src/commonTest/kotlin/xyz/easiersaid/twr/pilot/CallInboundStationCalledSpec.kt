package xyz.easiersaid.twr.pilot

import arrow.core.None
import arrow.core.Some
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * G2 Phase C — `CALL_INBOUND` station-called fallback contract.
 *
 * The pilot's [InitialContact] at CALL_INBOUND derives `stationCalled` from
 * `mission.pendingInitialContactRole`, falling back to [RoleName.TOWER] when
 * the field is [None]. Two contracts:
 *  1. Transit fallback (None) → TOWER. This is the autonomous-contact path:
 *     no controller has issued a [ContactFrequency] cross-aerodrome, so the
 *     pilot's first call to the destination tower uses the TOWER fallback.
 *     Pinned because Phase F's autonomous-contact provenance pin can only
 *     verify "no ContactFrequency directing to LJMB"; this row certifies
 *     the FALLBACK fired (not e.g. an accidental hardcode).
 *  2. Pre-existing `Some(GROUND)` → GROUND. Pass 7 contract: when a prior
 *     `ContactFrequency(role)` has been issued, the pilot's CALL_INBOUND
 *     uses that role. Regression-pin against any future hardcode-TOWER.
 *
 * D-G2.7 records the future scope of reading the destination's published
 * `roles` list (AFIS / FIS / TOWER variations per aerodrome) instead of the
 * `RoleName.TOWER` fallback.
 */
class CallInboundStationCalledSpec {

    private val LJMB = AerodromeId("LJMB")
    private val ac = AircraftId("OE-XYZ")
    private val now0 = SimTime.ofMillis(0)

    private fun aircraft(): AircraftState = AircraftState(
        id = ac,
        callsign = Callsign("OEXYZ"),
        position = xyz.easiersaid.twr.core.world.Position(0.0, 0.0),
        positionPoint = PointId("OSMOT"),
        phase = PilotPhase.Climbing,
    )

    private fun missionAtCallInbound(
        pendingRole: arrow.core.Option<RoleName>,
    ): PilotMission = PilotMission(
        goal = HighLevelGoal.Transit(destination = LJMB),
        root = CompoundTask(
            name = TaskName.ArrivalJoin,
            children = listOf(
                PrimitiveTask(MissionStep.CALL_INBOUND, CompletionMode.REPORTED),
            ),
        ),
        stepEnteredAt = now0,
        pendingInitialContactRole = pendingRole,
    )

    @Test
    fun `Transit fallback (None) calls TOWER`() {
        val mission = missionAtCallInbound(pendingRole = None)
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(),
            mission = mission,
            worldIndex = WorldIndex(),
            now = now0,
        )
        val ic = decision.transmissions.filterIsInstance<InitialContact>().firstOrNull()
        assertEquals(RoleName.TOWER, ic?.stationCalled,
            "When pendingInitialContactRole is None, CALL_INBOUND falls back to TOWER " +
                "(D-G2.7 deferred — destination tower-role lookup will read the procedure's " +
                "contactRequirement)")
    }

    @Test
    fun `pre-existing Some(GROUND) calls GROUND (Pass 7 contract pin)`() {
        val mission = missionAtCallInbound(pendingRole = Some(RoleName.GROUND))
        val decision = pilotCognitiveDecide(
            aircraft = aircraft(),
            mission = mission,
            worldIndex = WorldIndex(),
            now = now0,
        )
        val ic = decision.transmissions.filterIsInstance<InitialContact>().firstOrNull()
        assertEquals(RoleName.GROUND, ic?.stationCalled,
            "When pendingInitialContactRole is Some(GROUND), CALL_INBOUND must use GROUND. " +
                "Regression-pin against hardcode-TOWER refactors.")
    }
}
