package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.OperatorContext
import xyz.easiersaid.twr.controller.bdi.RunwayLengthOperation
import xyz.easiersaid.twr.controller.bdi.RunwayLengthSufficient
import xyz.easiersaid.twr.controller.bdi.TowerDepartureStage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.DeclaredDistances
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.Meters
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.IcaoTypeDesignator
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import kotlin.test.Test

/**
 * Pass 13 (D-AUDIT.4.A-FOLLOWUP closure) — runway-length gating guard.
 *
 * `RunwayLengthSufficient` is the consumer of
 * `AircraftType.RunwayLengthRequirements` data Pass 10 carried but did
 * not gate on. Reads through the firewall-narrow companion lookup
 * `AircraftType.runwayRequirementsFor(IcaoTypeDesignator)` — controller
 * never sees the full type.
 *
 * **Fail-closed semantics** (no-corners): unknown designators or absent
 * declared distances make the guard reject. The migration schema's
 * `CandidateDeclaredDistances` is non-nullable, so loaded worlds
 * always carry distances; null reaches runtime only via in-memory test
 * fixtures, where fail-closed remains correct.
 *
 * Each row exercises a distinct fail-closed branch with named diagnostic.
 */
class RunwayLengthGatingSpec {

    private val aircraftId = AircraftId("OE-B738")

    private val commitment = Commitment(
        aircraft = aircraftId,
        kind = CommitmentKind.TOWER_DEPARTURE,
        stage = TowerDepartureStage.AwaitReady,
        formedAt = SimTime.ZERO,
        runway = RWY_ID,
    )

    private fun observation(designator: IcaoTypeDesignator?): AircraftObservation =
        AircraftObservation(
            id = aircraftId,
            callsign = Callsign("OEB738"),
            position = PointId("P"),
            entities = emptySet(),
            altitude = null,
            speed = null,
            heading = null,
            groundSpeed = null,
            onGround = true,
            wakeCategory = null,
            icaoTypeDesignator = designator,
        )

    private fun ctx(world: AviationWorld): OperatorContext = OperatorContext(
        view = ControllerView(
            time = SimTime.ZERO,
            controllerId = ControllerId("TEST_TWR"),
            role = RoleName.TOWER,
            aerodromeId = ADRM_ID,
            responsibilities = setOf(aircraftId),
            aircraft = mapOf(aircraftId to observation(IcaoTypeDesignator.unsafe("B738"))),
            runways = emptyMap(),
            activeClearances = emptyMap(),
            receivedMessages = emptyList(),
            weather = null,
            worldIndex = WorldIndex(),
        ),
        beliefs = BeliefState.EMPTY,
        events = emptyList(),
        world = world,
    )

    @Test
    fun `B738 takeoff on short 800m runway is rejected (TODA below 2280m AFM minimum)`() {
        // 737 AFM TODA at SL/MTOW = 2280 m. An 800 m TODA is short for any
        // jet operation; the rule must refuse.
        val world = worldWithRunway(toda = 800.0, lda = 800.0)
        val guard = RunwayLengthSufficient(RunwayLengthOperation.TAKEOFF)
        check(!guard.evaluate(observation(IcaoTypeDesignator.unsafe("B738")), commitment, ctx(world))) {
            "B738 takeoff on 800m TODA should be rejected (AFM requires 2280m at SL/MTOW)"
        }
    }

    @Test
    fun `B738 takeoff on long 3000m runway is permitted (TODA exceeds 2280m AFM minimum)`() {
        // LOWG 16C TODA = 3000 m — long enough for B738.
        val world = worldWithRunway(toda = 3000.0, lda = 3000.0)
        val guard = RunwayLengthSufficient(RunwayLengthOperation.TAKEOFF)
        check(guard.evaluate(observation(IcaoTypeDesignator.unsafe("B738")), commitment, ctx(world))) {
            "B738 takeoff on 3000m TODA should be permitted (AFM minimum 2280m satisfied)"
        }
    }

    @Test
    fun `unknown designator XXXX fails closed (no AircraftType match — controller cannot gate)`() {
        // Pass 13 fail-closed on unknown designator (no-corners): the rule
        // refuses to clear an aircraft whose runway requirements it cannot
        // determine. The diagnostic via runwayRequirementsFor.swap()
        // names XXXX in `UnknownDesignator(designator)`.
        val world = worldWithRunway(toda = 3000.0, lda = 3000.0)
        val guard = RunwayLengthSufficient(RunwayLengthOperation.TAKEOFF)
        check(!guard.evaluate(observation(IcaoTypeDesignator.unsafe("XXXX")), commitment, ctx(world))) {
            "Unknown designator XXXX should fail-closed: controller cannot determine runway adequacy"
        }
    }

    @Test
    fun `null declared distances fails closed (in-memory test fixture path)`() {
        // The migration schema's non-null `CandidateDeclaredDistances` ensures
        // loaded worlds always carry distances; this branch covers the
        // in-memory-Runway() test-fixture path, where fail-closed is still
        // correct (the test must populate distances; refusing to silently
        // accept absence is the no-corners rule).
        val world = worldWithRunway(declaredDistances = null)
        val guard = RunwayLengthSufficient(RunwayLengthOperation.TAKEOFF)
        check(!guard.evaluate(observation(IcaoTypeDesignator.unsafe("B738")), commitment, ctx(world))) {
            "Null declaredDistances should fail-closed: controller cannot determine runway adequacy"
        }
    }

    @Test
    fun `null icaoTypeDesignator on observation fails closed (no strip data)`() {
        // VFR aircraft without filed plan have null `icaoTypeDesignator`
        // (FlightStrip carries it as nullable). Without a designator, the
        // guard cannot resolve runway requirements and fails closed.
        val world = worldWithRunway(toda = 3000.0, lda = 3000.0)
        val guard = RunwayLengthSufficient(RunwayLengthOperation.TAKEOFF)
        check(!guard.evaluate(observation(designator = null), commitment, ctx(world))) {
            "Null icaoTypeDesignator should fail-closed: no type info → cannot gate"
        }
    }

    @Test
    fun `null commitment runway fails closed (upstream defect)`() {
        // The commitment carries the runway selected by upstream rules; absence
        // is an upstream defect (a runway-domain rule fired without a runway).
        // Fail-closed is correct: the guard cannot evaluate adequacy without
        // a runway to evaluate against.
        val world = worldWithRunway(toda = 3000.0, lda = 3000.0)
        val commitmentNoRunway = commitment.copy(runway = null)
        val guard = RunwayLengthSufficient(RunwayLengthOperation.TAKEOFF)
        check(!guard.evaluate(observation(IcaoTypeDesignator.unsafe("B738")), commitmentNoRunway, ctx(world))) {
            "Null commitment.runway should fail-closed: cannot evaluate adequacy without a runway"
        }
    }

    @Test
    fun `runway named on commitment but absent from world fails closed`() {
        // Defensive: the commitment names a runway the world does not contain
        // — a wiring defect (e.g., commitment carried over from a prior world
        // load). Without runway data, fail closed.
        val world = AviationWorld() // empty world — no aerodromes, no runways
        val guard = RunwayLengthSufficient(RunwayLengthOperation.TAKEOFF)
        check(!guard.evaluate(observation(IcaoTypeDesignator.unsafe("B738")), commitment, ctx(world))) {
            "Runway named but missing from world should fail-closed"
        }
    }

    @Test
    fun `C172 landing on 600m runway is permitted (LDA exceeds 407m TCDS minimum)`() {
        // Pass 13 LANDING branch coverage. C172 TCDS 3A12 SL/MTOW landing
        // distance = 407 m. 600 m LDA is comfortably adequate.
        val world = worldWithRunway(toda = 600.0, lda = 600.0)
        val landingCommitment = commitment.copy(kind = CommitmentKind.TOWER_ARRIVAL)
        val guard = RunwayLengthSufficient(RunwayLengthOperation.LANDING)
        check(guard.evaluate(observation(IcaoTypeDesignator.unsafe("C172")), landingCommitment, ctx(world))) {
            "C172 landing on 600m LDA should be permitted (TCDS minimum 407m satisfied)"
        }
    }

    @Test
    fun `B738 landing on 1000m runway is rejected (LDA below 1700m AFM minimum)`() {
        // Pass 13 LANDING branch coverage. 737 AFM LDA at SL/MLW = 1700 m.
        // 1000 m is short for jet landing; the rule must refuse.
        val world = worldWithRunway(toda = 1000.0, lda = 1000.0)
        val landingCommitment = commitment.copy(kind = CommitmentKind.TOWER_ARRIVAL)
        val guard = RunwayLengthSufficient(RunwayLengthOperation.LANDING)
        check(!guard.evaluate(observation(IcaoTypeDesignator.unsafe("B738")), landingCommitment, ctx(world))) {
            "B738 landing on 1000m LDA should be rejected (AFM requires 1700m at SL/MLW)"
        }
    }

    companion object {
        private val ADRM_ID = AerodromeId("LOWG")
        private val RWY_ID = RunwayId("16C")
        private val THRESHOLD = PointId("T")
        private val DEP_END = PointId("DEP")

        private fun worldWithRunway(
            toda: Double = 3000.0,
            lda: Double = 3000.0,
            declaredDistances: DeclaredDistances? = DeclaredDistances(
                tora = Meters(toda),
                toda = Meters(toda),
                asda = Meters(toda),
                lda = Meters(lda),
            ),
        ): AviationWorld {
            val runway = Runway(
                id = RWY_ID,
                path = Path(listOf(THRESHOLD, DEP_END)),
                threshold = THRESHOLD,
                declaredDistances = declaredDistances,
            )
            val aerodrome = Aerodrome(
                icao = ADRM_ID,
                elevation = Feet(0),
                magneticVariation = Degrees(0.0),
                transitionAltitude = Level.AltitudeFeet.unsafe(5000),
                runways = mapOf(RWY_ID to runway),
            )
            return AviationWorld(aerodromes = mapOf(ADRM_ID to aerodrome))
        }
    }
}
