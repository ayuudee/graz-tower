package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.observe.AdvancementPolicy
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.CoordinationState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.certify.CertificationEvidence
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.RunwayObstruction
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.RunwayObstructionInformation
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Urgency
import arrow.core.NonEmptyList
import kotlin.test.Test

/**
 * fn-12 (R7-stage-coverage / R7-no-refire / R7-supersession / R8 final):
 * controller-level regression tests for the `ARR-GO-AROUND-RUNWAY-OBSTRUCTED`
 * rule. End-to-end via [controllerDecide] — no test-fixture seeding bypasses
 * the rule pipeline.
 *
 * Coverage:
 *  - **Stage coverage** (R7): rule fires from each of the three on-final
 *    stages (`AwaitApproach`, `LandingClearanceIssued`, `AwaitLandedObserved`).
 *    Companion `RunwayObstructionInformation` transmission emitted in same
 *    cycle. Commitment regresses to `AwaitDownwind`.
 *  - **No-refire** (R7-no-refire): on a later cycle with the obstruction
 *    still active, the rule does NOT re-fire (witness is set).
 *  - **Re-arm** (R7-no-refire): a `Report(Downwind)` clears the witness,
 *    allowing a fresh obstruction GA on the recovery approach.
 *  - **Supersession** (R7-supersession): post-fire, no active
 *    `ClearedToLand` / `ClearedTouchAndGo` clearance survives in
 *    `issuedClearances`, and pending coordinations of the same type are
 *    cleaned up across all four coordination states.
 *  - **Companion-info** (R8): the obstruction-info companion carries the
 *    correct runway + clearsAt, with regulation refs ICAO §7.4.1.4.1,
 *    §8.9.6.1.8, CAP 413 §4.65.
 */
class ObstructionGoAroundSpec {

    @Test
    fun `rule fires from AwaitApproach with obstruction in beliefs and emits companion info`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(120))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        )
        val view = baseView()

        val result = controllerDecide(view, previous, worldWithRunway())

        val gaInstruct = result.outputs
            .filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is GoAround }
            ?: error("Expected a GoAround instruction; got ${result.outputs}")
        check(gaInstruct.urgency == Urgency.SAFETY) {
            "Obstruction GA must be SAFETY urgency; got ${gaInstruct.urgency}"
        }
        check(gaInstruct.trace.ruleId == "ARR-GO-AROUND-RUNWAY-OBSTRUCTED") {
            "Expected rule trace ARR-GO-AROUND-RUNWAY-OBSTRUCTED; got ${gaInstruct.trace.ruleId}"
        }

        val companion = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .firstOrNull { it.response is RunwayObstructionInformation }
            ?: error("Expected RunwayObstructionInformation companion; got ${result.outputs}")
        val info = companion.response as RunwayObstructionInformation
        check(info.runway == RWY) { "companion runway mismatch: ${info.runway} vs $RWY" }
        check(info.clearsAt == obs.clearsAt) { "companion clearsAt mismatch: ${info.clearsAt} vs ${obs.clearsAt}" }

        val updated = result.updatedBeliefs.commitments.getValue(AC)
        check(updated.stage == TowerArrivalStage.AwaitDownwind) {
            "Commitment must regress to AwaitDownwind; got ${updated.stage}"
        }
        check(updated.obstructionGoAroundIssuedThisAttempt) {
            "Witness obstructionGoAroundIssuedThisAttempt must be set after committed-output GA"
        }
    }

    @Test
    fun `rule fires from LandingClearanceIssued and supersedes ClearedToLand`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(120))
        val landingCoord = OutstandingCoordination(
            aircraft = AC,
            dispatch = Dispatch.Direct(ClearedToLand(target = AC, runway = RWY)),
            issuedAt = SimTime.ofMillis(5_000),
            state = CoordinationState.Issued,
            expectedReadback = emptySet(),
            certificationEvidence = NonEmptyList(
                CertificationEvidence.RuntimeChecked(checkId = "test", summary = "test"),
                emptyList(),
            ),
        )
        val previous = baseBeliefs(
            stage = TowerArrivalStage.LandingClearanceIssued,
            obstruction = obs,
            coordinations = mapOf(AC to listOf(landingCoord)),
        )
        val view = baseView()

        val result = controllerDecide(view, previous, worldWithRunway())

        // GA emitted
        val gaInstruct = result.outputs
            .filterIsInstance<ControllerOutput.Instruct>()
            .singleOrNull { it.instruction is GoAround }
            ?: error("Expected exactly one GoAround instruction; got ${result.outputs}")
        check(gaInstruct.trace.ruleId == "ARR-GO-AROUND-RUNWAY-OBSTRUCTED") {
            "Expected rule trace ARR-GO-AROUND-RUNWAY-OBSTRUCTED; got ${gaInstruct.trace.ruleId}"
        }

        // Pending ClearedToLand coordination superseded
        val remaining = result.updatedBeliefs.coordinations[AC].orEmpty()
            .filter { it.instruction is ClearedToLand }
        check(remaining.isEmpty()) {
            "Pending ClearedToLand coordinations must be superseded; got $remaining"
        }
    }

    @Test
    fun `rule fires from AwaitLandedObserved and supersedes ClearedTouchAndGo`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(120))
        val tngCoord = OutstandingCoordination(
            aircraft = AC,
            dispatch = Dispatch.Direct(ClearedTouchAndGo(target = AC, runway = RWY)),
            issuedAt = SimTime.ofMillis(5_000),
            state = CoordinationState.Issued,
            expectedReadback = emptySet(),
            certificationEvidence = NonEmptyList(
                CertificationEvidence.RuntimeChecked(checkId = "test", summary = "test"),
                emptyList(),
            ),
        )
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitLandedObserved,
            obstruction = obs,
            coordinations = mapOf(AC to listOf(tngCoord)),
        )
        val view = baseView()

        val result = controllerDecide(view, previous, worldWithRunway())

        result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .singleOrNull { it.instruction is GoAround }
            ?: error("Expected one GoAround instruction; got ${result.outputs}")

        val remaining = result.updatedBeliefs.coordinations[AC].orEmpty()
            .filter { it.instruction is ClearedTouchAndGo }
        check(remaining.isEmpty()) {
            "Pending ClearedTouchAndGo coordinations must be superseded; got $remaining"
        }
    }

    @Test
    fun `supersession across all four coordination states drops landing-class coordinations`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(120))
        // One coordination per state, all ClearedToLand for the same aircraft.
        // (In practice only one state is real at any moment, but the supersession
        // contract MUST cover all four — `processReadback` accepts any state's
        // coordination as a valid match for late readbacks.)
        val makeCoord = { state: CoordinationState ->
            OutstandingCoordination(
                aircraft = AC,
                dispatch = Dispatch.Direct(ClearedToLand(target = AC, runway = RWY)),
                issuedAt = SimTime.ofMillis(5_000),
                state = state,
                expectedReadback = emptySet(),
                certificationEvidence = NonEmptyList(
                    CertificationEvidence.RuntimeChecked(checkId = "test", summary = "test"),
                    emptyList(),
                ),
            )
        }
        val coords = listOf(
            makeCoord(CoordinationState.Issued),
            makeCoord(CoordinationState.Querying(
                queriedAt = SimTime.ofMillis(7_000),
                emittedAt = SimTime.ofMillis(7_000),
            )),
            makeCoord(CoordinationState.Reissued(
                reissuedAt = SimTime.ofMillis(9_000),
                attemptCount = 1,
                emittedAt = SimTime.ofMillis(9_000),
            )),
            makeCoord(CoordinationState.LostCommsDeclared(
                declaredAt = SimTime.ofMillis(11_000),
                emittedBlindAt = null,
            )),
        )
        val previous = baseBeliefs(
            stage = TowerArrivalStage.LandingClearanceIssued,
            obstruction = obs,
            coordinations = mapOf(AC to coords),
        )
        val view = baseView()

        val result = controllerDecide(view, previous, worldWithRunway())

        val remaining = result.updatedBeliefs.coordinations[AC].orEmpty()
            .filter { it.instruction is ClearedToLand }
        check(remaining.isEmpty()) {
            "All four coordination states must be superseded by GA; got $remaining"
        }
    }

    @Test
    fun `no-refire — once witness is set the rule does not fire again on the next cycle`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(120))
        // Simulate the post-first-fire state: witness is set, stage is
        // AwaitDownwind. But the rule's geometric guard
        // `OnApproach || OnCircuitLeg(FINAL)` — the aircraft is still on
        // final (e.g. just received the GA but hasn't begun executing it).
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitDownwind,
            obstruction = obs,
        ).let { b ->
            val c = b.commitments.getValue(AC).copy(
                obstructionGoAroundIssuedThisAttempt = true,
            )
            b.copy(commitments = b.commitments + (AC to c))
        }
        val view = baseView()

        val result = controllerDecide(view, previous, worldWithRunway())

        val ga = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is GoAround }
        check(ga == null) {
            "Witness-set commitment must NOT re-fire ARR-GO-AROUND-RUNWAY-OBSTRUCTED; got $ga"
        }
    }

    @Test
    fun `re-arm — Report(Downwind) clears the witness so a fresh obstruction can drive a fresh GA`() {
        // Simulate post-first-fire state plus a fresh `Report(Downwind)`
        // arriving on the message inbox. The reconciliation must clear
        // the witness in the same cycle, and the next rule-evaluation
        // (in the SAME controllerDecide call) must see the cleared witness.
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(120))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitDownwind,
            obstruction = obs,
        ).let { b ->
            val c = b.commitments.getValue(AC).copy(
                obstructionGoAroundIssuedThisAttempt = true,
            )
            b.copy(commitments = b.commitments + (AC to c))
        }
        // Aircraft on Downwind, transmitting the Downwind report.
        val view = baseView(
            point = PointId("DOWNWIND"),
            receivedMessages = listOf(
                ReceivedMessage.Clear(
                    aircraft = AC,
                    transmission = Report(
                        events = listOf(ReportEvent.Downwind(circuitIntent = null)),
                    ),
                ),
            ),
        )

        val result = controllerDecide(view, previous, worldWithRunway())

        // After the cycle, the witness should be cleared.
        val updated = result.updatedBeliefs.commitments[AC]
            ?: error("Commitment must persist; got ${result.updatedBeliefs.commitments}")
        check(!updated.obstructionGoAroundIssuedThisAttempt) {
            "Witness must be re-armed (cleared) on Report(Downwind); got ${updated.obstructionGoAroundIssuedThisAttempt}"
        }
    }

    @Test
    fun `obstruction-info companion DecisionTrace carries doctrine refs`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofSeconds(120))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        )
        val view = baseView()

        val result = controllerDecide(view, previous, worldWithRunway())

        val companion = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .firstOrNull { it.response is RunwayObstructionInformation }
            ?: error("Expected RunwayObstructionInformation companion; got ${result.outputs}")
        val regs = companion.trace.regulations.map { it.section }
        check(regs.containsAll(listOf("§7.4.1.4.1", "§8.9.6.1.8", "§4.65"))) {
            "Companion DecisionTrace must include ICAO §7.4.1.4.1, §8.9.6.1.8, CAP 413 §4.65; got $regs"
        }
        check(companion.trace.ruleId == "OBSTRUCTION-INFO") {
            "Companion ruleId must be OBSTRUCTION-INFO; got ${companion.trace.ruleId}"
        }
    }

    // ── Fixtures ─────────────────────────────────────────────────────────

    private fun baseBeliefs(
        stage: TowerArrivalStage,
        obstruction: RunwayObstruction,
        coordinations: Map<AircraftId, List<OutstandingCoordination>> = emptyMap(),
    ): BeliefState = BeliefState.EMPTY.copy(
        activeRunway = RWY,
        commitments = mapOf(
            AC to Commitment(
                aircraft = AC,
                kind = CommitmentKind.TOWER_ARRIVAL,
                stage = stage,
                runway = RWY,
                formedAt = SimTime.ZERO,
                contacted = true,
                // The HasReportedPositionCall guard in LandingConditions
                // requires a Final/Downwind report on the witness — but
                // the obstruction-GA rule doesn't gate on this, so we
                // can leave it empty. The geometric guard is OnApproach
                // || OnCircuitLeg(FINAL); we provide that via the test
                // worldIndex's `circuitLegsByPoint` map.
            ),
        ),
        runwayObstructions = mapOf(RWY to obstruction),
        coordinations = coordinations,
    )

    private fun baseView(
        point: PointId = PointId("FINAL"),
        receivedMessages: List<ReceivedMessage> = emptyList(),
    ): ControllerView {
        val obs = AircraftObservation.fromTestPoint(
            point = point,
            worldIndex = TEST_INDEX,
            id = AC,
            callsign = Callsign("OEABC"),
            altitude = Level.AltitudeFeet.unsafe(800),
            onGround = false,
        )
        return ControllerView(
            time = SimTime.ofMillis(10_000),
            controllerId = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = ADRM,
            responsibilities = setOf(AC),
            aircraft = mapOf(AC to obs),
            runways = mapOf(
                RWY to RunwayObservation(
                    id = RWY,
                    status = RunwayStatus.CLEAR,
                    occupants = emptySet(),
                ),
            ),
            activeClearances = emptyMap(),
            receivedMessages = receivedMessages,
            weather = null,
            worldIndex = TEST_INDEX,
            flightStripIntents = mapOf(AC to AircraftIntent.Arriving),
        )
    }

    companion object {
        private val ADRM = AerodromeId("LOWG")
        private val AC = AircraftId("OE-ABC")
        private val RWY = RunwayId("16C")

        private val TEST_INDEX = WorldIndex(
            positions = mapOf(
                PointId("FINAL") to Position(xMeters = 0.0, yMeters = 0.0),
                PointId("DOWNWIND") to Position(xMeters = 1000.0, yMeters = 0.0),
                PointId("THR") to Position(xMeters = 0.0, yMeters = 0.0),
            ),
            circuitLegsByPoint = mapOf(
                PointId("FINAL") to setOf(LegName.FINAL),
                PointId("DOWNWIND") to setOf(LegName.DOWNWIND),
            ),
            thresholdByRunway = mapOf(RWY to PointId("THR")),
        )

        private fun worldWithRunway(): AviationWorld {
            val runway = Runway(
                id = RWY,
                path = Path(listOf(PointId("THR"), PointId("DEP"))),
                threshold = PointId("THR"),
            )
            val aerodrome = Aerodrome(
                icao = ADRM,
                elevation = Feet(0),
                magneticVariation = Degrees(0.0),
                transitionAltitude = Level.AltitudeFeet.unsafe(5000),
                runways = mapOf(RWY to runway),
            )
            return AviationWorld(aerodromes = mapOf(ADRM to aerodrome))
        }
    }
}
