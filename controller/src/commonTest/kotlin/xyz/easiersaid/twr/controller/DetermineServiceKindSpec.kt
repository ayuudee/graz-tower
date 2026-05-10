package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.determineServiceKind
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.ApproachId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.CircuitProcedureId
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.StandId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pass 5 — pre-rewrite quotient baseline for [determineServiceKind].
 *
 * Per Pass 5 plan Test review S3: this spec lands **before** the rewrite of
 * `determineServiceKind` to derive intent on demand from primary sources
 * (instead of receiving it from the deleted `BeliefState.aircraftIntent`).
 * The spec captures today's `(role, observation, intent, isCircuitTraffic)`
 * → `CommitmentKind?` quotient against the **current** (pre-rewrite)
 * implementation. After the rewrite, the spec stays green — proving the
 * new shape preserves the contract.
 *
 * Coverage:
 *  - TOWER role: 8 conditions in `serviceKindForTower` × strip × radio history.
 *  - GROUND role: 3 conditions in `serviceKindForGround` × strip.
 *  - APPROACH role: arriving vs transit.
 *  - AREA_CONTROL role: always AREA_TRANSIT.
 *  - Three remaining roles (CLEARANCE_DELIVERY, DEPARTURE, AFIS) throw
 *    `error()` per Pass 6's deferral; not tested here.
 *
 * Per the no-corners rule: spec rows assert genuine quotient cells, not
 * tautologies. Each row pins a `(inputs) → CommitmentKind?` cell that the
 * post-Pass-5 rewrite must reproduce.
 */
class DetermineServiceKindSpec {

    private val aircraftId = AircraftId("OE-ABC")
    private val rwy = RunwayId("16")
    private val standId = StandId("STAND_1")

    /** Aircraft on the ground at a stand. */
    private fun parkedOnGround(): AircraftObservation =
        AircraftObservation.fromTestPoint(
            point = PointId("STAND_PT"),
            worldIndex = worldIndexWithEntities(setOf(EntityRef.StandRef(standId))),
            id = aircraftId,
            callsign = Callsign("OEABC"),
            onGround = true,
        )

    /** Aircraft on the ground on a runway. */
    private fun onRunwayGround(): AircraftObservation =
        AircraftObservation.fromTestPoint(
            point = PointId("RWY_PT"),
            worldIndex = worldIndexWithEntities(setOf(EntityRef.RunwayRef(rwy))),
            id = aircraftId,
            callsign = Callsign("OEABC"),
            onGround = true,
        )

    /** Aircraft on the ground off the runway, off any stand (taxiway). */
    private fun onTaxiwayGround(): AircraftObservation =
        AircraftObservation.fromTestPoint(
            point = PointId("TWY_PT"),
            worldIndex = worldIndexWithEntities(emptySet()),
            id = aircraftId,
            callsign = Callsign("OEABC"),
            onGround = true,
        )

    /** Aircraft airborne in the circuit. */
    private fun airborneInCircuit(): AircraftObservation =
        AircraftObservation.fromTestPoint(
            point = PointId("CIRCUIT_PT"),
            worldIndex = worldIndexWithEntities(setOf(EntityRef.CircuitProcedureRef(CircuitProcedureId("CIRC")))),
            id = aircraftId,
            callsign = Callsign("OEABC"),
            onGround = false,
        )

    /** Aircraft airborne on approach. */
    private fun airborneOnApproach(): AircraftObservation =
        AircraftObservation.fromTestPoint(
            point = PointId("APP_PT"),
            worldIndex = worldIndexWithEntities(setOf(EntityRef.ApproachRef(ApproachId("APP")))),
            id = aircraftId,
            callsign = Callsign("OEABC"),
            onGround = false,
        )

    /** Aircraft airborne, no approach/circuit entity (en-route). */
    private fun airborneEnRoute(): AircraftObservation =
        AircraftObservation.fromTestPoint(
            point = PointId("ER_PT"),
            worldIndex = worldIndexWithEntities(emptySet()),
            id = aircraftId,
            callsign = Callsign("OEABC"),
            onGround = false,
        )

    private fun worldIndexWithEntities(entities: Set<EntityRef>): xyz.easiersaid.twr.core.world.WorldIndex =
        xyz.easiersaid.twr.core.world.WorldIndex(
            entitiesByPoint = mapOf(
                PointId("STAND_PT") to entities,
                PointId("RWY_PT") to entities,
                PointId("TWY_PT") to entities,
                PointId("CIRCUIT_PT") to entities,
                PointId("APP_PT") to entities,
                PointId("ER_PT") to entities,
            ),
            // fn-6.1: seed positions for every fixture point so each
            // `AircraftObservation.from(...)` call below can pass
            // `coords = worldIndex.positions[<position>]!!` non-divergently.
            // determineServiceKind reads no geometric field; coords are
            // not load-bearing here.
            positions = mapOf(
                PointId("STAND_PT") to Position(xMeters = 0.0, yMeters = 0.0),
                PointId("RWY_PT") to Position(xMeters = 0.0, yMeters = 0.0),
                PointId("TWY_PT") to Position(xMeters = 0.0, yMeters = 0.0),
                PointId("CIRCUIT_PT") to Position(xMeters = 0.0, yMeters = 0.0),
                PointId("APP_PT") to Position(xMeters = 0.0, yMeters = 0.0),
                PointId("ER_PT") to Position(xMeters = 0.0, yMeters = 0.0),
            ),
        )

    // ── TOWER role ──────────────────────────────────────────────────────

    @Test
    fun `TOWER + circuit traffic on runway after touchdown → TOWER_ARRIVAL`() {
        // Branch: ac.onGround && onRunway && isCircuitTraffic
        val k = determineServiceKind(RoleName.TOWER, onRunwayGround(), AircraftIntent.Departing, isCircuitTraffic = true)
        assertEquals(CommitmentKind.TOWER_ARRIVAL, k)
    }

    @Test
    fun `TOWER + circuit traffic airborne → TOWER_ARRIVAL`() {
        val k = determineServiceKind(RoleName.TOWER, airborneInCircuit(), AircraftIntent.Departing, isCircuitTraffic = true)
        assertEquals(CommitmentKind.TOWER_ARRIVAL, k)
    }

    @Test
    fun `TOWER + on ground + departing intent → TOWER_DEPARTURE`() {
        val k = determineServiceKind(RoleName.TOWER, onRunwayGround(), AircraftIntent.Departing, isCircuitTraffic = false)
        assertEquals(CommitmentKind.TOWER_DEPARTURE, k)
    }

    @Test
    fun `TOWER + airborne one-shot departure (en-route, departing) → TOWER_DEPARTURE`() {
        val k = determineServiceKind(RoleName.TOWER, airborneEnRoute(), AircraftIntent.Departing, isCircuitTraffic = false)
        assertEquals(CommitmentKind.TOWER_DEPARTURE, k)
    }

    @Test
    fun `TOWER + landed not departing (Arriving on runway) → TOWER_ARRIVAL`() {
        val k = determineServiceKind(RoleName.TOWER, onRunwayGround(), AircraftIntent.Arriving, isCircuitTraffic = false)
        assertEquals(CommitmentKind.TOWER_ARRIVAL, k)
    }

    @Test
    fun `TOWER + airborne on approach → TOWER_ARRIVAL (entity-driven, no explicit intent)`() {
        val k = determineServiceKind(RoleName.TOWER, airborneOnApproach(), null, isCircuitTraffic = false)
        assertEquals(CommitmentKind.TOWER_ARRIVAL, k)
    }

    @Test
    fun `TOWER + airborne arriving en-route → TOWER_ARRIVAL`() {
        val k = determineServiceKind(RoleName.TOWER, airborneEnRoute(), AircraftIntent.Arriving, isCircuitTraffic = false)
        assertEquals(CommitmentKind.TOWER_ARRIVAL, k)
    }

    @Test
    fun `TOWER + airborne en-route Transit → null (not the tower's traffic)`() {
        val k = determineServiceKind(RoleName.TOWER, airborneEnRoute(), AircraftIntent.Transit, isCircuitTraffic = false)
        assertNull(k)
    }

    @Test
    fun `TOWER + airborne en-route null intent → null (no classification basis)`() {
        val k = determineServiceKind(RoleName.TOWER, airborneEnRoute(), null, isCircuitTraffic = false)
        assertNull(k)
    }

    // ── GROUND role ─────────────────────────────────────────────────────

    @Test
    fun `GROUND + arriving + parked → null (journey complete, no further taxi service)`() {
        val k = determineServiceKind(RoleName.GROUND, parkedOnGround(), AircraftIntent.Arriving, isCircuitTraffic = false)
        assertNull(k)
    }

    @Test
    fun `GROUND + arriving + on taxiway → GROUND_TAXI (taxi-in to stand)`() {
        val k = determineServiceKind(RoleName.GROUND, onTaxiwayGround(), AircraftIntent.Arriving, isCircuitTraffic = false)
        assertEquals(CommitmentKind.GROUND_TAXI, k)
    }

    @Test
    fun `GROUND + departing + parked → GROUND_TAXI (await taxi request)`() {
        val k = determineServiceKind(RoleName.GROUND, parkedOnGround(), AircraftIntent.Departing, isCircuitTraffic = false)
        assertEquals(CommitmentKind.GROUND_TAXI, k)
    }

    @Test
    fun `GROUND + departing + on taxiway → GROUND_TAXI (taxiing to runway)`() {
        val k = determineServiceKind(RoleName.GROUND, onTaxiwayGround(), AircraftIntent.Departing, isCircuitTraffic = false)
        assertEquals(CommitmentKind.GROUND_TAXI, k)
    }

    @Test
    fun `GROUND + null intent → GROUND_TAXI (default, controller will sort)`() {
        val k = determineServiceKind(RoleName.GROUND, onTaxiwayGround(), null, isCircuitTraffic = false)
        assertEquals(CommitmentKind.GROUND_TAXI, k)
    }

    // ── APPROACH role ───────────────────────────────────────────────────

    @Test
    fun `APPROACH + arriving → APPROACH_ARRIVAL`() {
        val k = determineServiceKind(RoleName.APPROACH, airborneEnRoute(), AircraftIntent.Arriving, isCircuitTraffic = false)
        assertEquals(CommitmentKind.APPROACH_ARRIVAL, k)
    }

    @Test
    fun `APPROACH + transit → APPROACH_TRANSIT`() {
        val k = determineServiceKind(RoleName.APPROACH, airborneEnRoute(), AircraftIntent.Transit, isCircuitTraffic = false)
        assertEquals(CommitmentKind.APPROACH_TRANSIT, k)
    }

    @Test
    fun `APPROACH + departing → APPROACH_TRANSIT (handed off from tower for climb)`() {
        val k = determineServiceKind(RoleName.APPROACH, airborneEnRoute(), AircraftIntent.Departing, isCircuitTraffic = false)
        assertEquals(CommitmentKind.APPROACH_TRANSIT, k)
    }

    // ── AREA_CONTROL role ───────────────────────────────────────────────

    @Test
    fun `AREA_CONTROL → AREA_TRANSIT regardless of intent`() {
        val k = determineServiceKind(RoleName.AREA_CONTROL, airborneEnRoute(), AircraftIntent.Transit, isCircuitTraffic = false)
        assertEquals(CommitmentKind.AREA_TRANSIT, k)
    }
}
