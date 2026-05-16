package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.ControllerEvent
import xyz.easiersaid.twr.controller.observe.GoAroundInProgress
import xyz.easiersaid.twr.controller.observe.GoAroundRunwayResolutionFailure
import xyz.easiersaid.twr.controller.observe.resolveGoAroundRunway
import xyz.easiersaid.twr.controller.observe.withGoAroundInProgress
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
import xyz.easiersaid.twr.core.world.Feet
import xyz.easiersaid.twr.core.world.LegName
import xyz.easiersaid.twr.core.world.Path
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.Runway
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.ExtendDownwind
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TurnBase
import xyz.easiersaid.twr.protocol.Urgency
import kotlin.test.Test

/**
 * fn-28.4 (R23): controller-side multi-aircraft sequencing for runway-active
 * go-around. End-to-end via [controllerDecide] for the rule firings; unit-level
 * for the [withGoAroundInProgress] fold + [resolveGoAroundRunway] helper.
 *
 * Coverage matrix (per task acceptance):
 *  - **SET on `Report(GoingAround)`** with runway resolved from active arrival
 *    commitment (primary path) + global activeRunway fallback (round-10 Major 3).
 *  - **Fail-closed runway resolution**: when neither commitment nor activeRunway
 *    yield a runway, no belief write.
 *  - **Tie-breaking** (R23 round-7 Minor 1): first-writer-wins until cleared.
 *  - **CLEAR on pattern-rejoin** (round-13 Major 3): `Report(Downwind/Final/Base)`
 *    with `receivedAt > setAtTime` clears; same-cycle stale Final does NOT.
 *  - **CLEAR on 60s timeout** (R23): bounded recovery without observable
 *    pattern-rejoin.
 *  - **`ARR-EXTEND-FOR-GA` fires** (firewall: controller-observable predicates
 *    only — no `PilotPhase` reads): trailing downwind aircraft on the same runway.
 *  - **No-refire** (R23 acceptance): single GA report → ExtendDownwind fires
 *    ONCE per cycle (the natural NoPendingReadback gate prevents same-cycle
 *    refire; the rule fires every cycle until the readback arrives, then the
 *    pending-readback gate suppresses).
 *  - **Concrete cancel-output** (round-10 Major 2): when GA belief clears AND
 *    trailing B was extended, controller emits `TurnBase` in the SAME cycle;
 *    existing `SupersessionRelation(TurnBase, ExtendDownwind, ABANDON)` drops
 *    B's prior ExtendDownwind coordination.
 *  - **Negative case**: no GA report + downwind traffic → no `ARR-EXTEND-FOR-GA`
 *    fires (existing ARR-EXTEND may fire on its own gating, which is unchanged).
 */
class GoAroundSequencingSpec {

    // ─────────────────────────────────────────────────────────────────
    // Fold-level pins: withGoAroundInProgress + resolveGoAroundRunway
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `resolveGoAroundRunway returns commitment runway when arrival commitment exists`() {
        val beliefs = BeliefState.EMPTY.copy(
            commitments = mapOf(AC_A to commitment(AC_A, runway = RWY)),
            activeRunway = RWY_OTHER, // fallback should NOT win when commitment runway is set
        )
        val resolved = resolveGoAroundRunway(AC_A, beliefs)
        check(resolved.getOrNull() == RWY) {
            "Expected runway $RWY from arrival commitment; got $resolved"
        }
    }

    @Test
    fun `resolveGoAroundRunway falls back to activeRunway when no commitment exists`() {
        val beliefs = BeliefState.EMPTY.copy(
            commitments = emptyMap(),
            activeRunway = RWY,
        )
        val resolved = resolveGoAroundRunway(AC_A, beliefs)
        check(resolved.getOrNull() == RWY) {
            "Expected runway $RWY from activeRunway fallback; got $resolved"
        }
    }

    @Test
    fun `resolveGoAroundRunway fails closed when neither commitment nor activeRunway resolve`() {
        val beliefs = BeliefState.EMPTY.copy(
            commitments = emptyMap(),
            activeRunway = null,
        )
        val resolved = resolveGoAroundRunway(AC_A, beliefs)
        val failure = resolved.leftOrNull()
            ?: error("Expected Left; got $resolved")
        check(failure is GoAroundRunwayResolutionFailure.NoArrivalCommitment) {
            "Expected NoArrivalCommitment for missing commitment + missing activeRunway; got $failure"
        }
    }

    @Test
    fun `fold SET writes goAroundInProgress when GA report resolves a runway`() {
        val now = SimTime.ofMillis(10_000)
        val beliefs = BeliefState.EMPTY.copy(
            commitments = mapOf(AC_A to commitment(AC_A, runway = RWY)),
        )
        val updated = beliefs.withGoAroundInProgress(
            events = listOf(ControllerEvent.GoAroundDetected(AC_A)),
            now = now,
        )
        val entry = updated.goAroundInProgressByRunway[RWY]
            ?: error("Expected goAroundInProgressByRunway[$RWY] entry; got ${updated.goAroundInProgressByRunway}")
        check(entry.aircraftId == AC_A) { "Expected aircraftId=$AC_A; got ${entry.aircraftId}" }
        check(entry.setAtTime == now) { "Expected setAtTime=$now; got ${entry.setAtTime}" }
    }

    @Test
    fun `fold SET fails closed when runway is not resolvable (no commitment, no activeRunway)`() {
        val now = SimTime.ofMillis(10_000)
        val beliefs = BeliefState.EMPTY // no commitment, no activeRunway
        val updated = beliefs.withGoAroundInProgress(
            events = listOf(ControllerEvent.GoAroundDetected(AC_A)),
            now = now,
        )
        check(updated.goAroundInProgressByRunway.isEmpty()) {
            "Fail-closed: expected no belief write when runway unresolvable; got ${updated.goAroundInProgressByRunway}"
        }
    }

    @Test
    fun `fold tie-breaking first-writer-wins ignores subsequent GA reports while entry active`() {
        val now = SimTime.ofMillis(10_000)
        val seeded = BeliefState.EMPTY.copy(
            commitments = mapOf(
                AC_A to commitment(AC_A, runway = RWY),
                AC_B to commitment(AC_B, runway = RWY),
            ),
            goAroundInProgressByRunway = mapOf(
                RWY to GoAroundInProgress(AC_A, SimTime.ofMillis(5_000)),
            ),
        )
        // Subsequent GA from AC_B for the SAME runway must be ignored —
        // the AC_A entry survives, AC_B does NOT overwrite.
        val updated = seeded.withGoAroundInProgress(
            events = listOf(ControllerEvent.GoAroundDetected(AC_B)),
            now = now,
        )
        val entry = updated.goAroundInProgressByRunway[RWY]
            ?: error("Expected surviving entry for $RWY; got ${updated.goAroundInProgressByRunway}")
        check(entry.aircraftId == AC_A) {
            "Expected AC_A (first-writer-wins); got ${entry.aircraftId}"
        }
        check(entry.setAtTime.millis == 5_000L) {
            "Expected original setAtTime=5_000ms preserved; got ${entry.setAtTime}"
        }
    }

    @Test
    fun `fold CLEAR same-cycle stale Final does NOT clear belief that was just set`() {
        // Round-13 Major 3: receivedAt > setAtTime is strict — a Final
        // report arriving in the SAME cycle as the GA report (same
        // view.time) must NOT clear what was just set.
        val now = SimTime.ofMillis(10_000)
        val beliefs = BeliefState.EMPTY.copy(
            commitments = mapOf(AC_A to commitment(AC_A, runway = RWY)),
        )
        val updated = beliefs.withGoAroundInProgress(
            events = listOf(
                ControllerEvent.GoAroundDetected(AC_A),
                ControllerEvent.PositionReported(AC_A, ReportEvent.Final),
            ),
            now = now,
        )
        val entry = updated.goAroundInProgressByRunway[RWY]
            ?: error("Expected belief to SURVIVE same-cycle Final; got ${updated.goAroundInProgressByRunway}")
        check(entry.aircraftId == AC_A)
    }

    @Test
    fun `fold CLEAR post-GA Report(Downwind) clears the entry`() {
        // Pattern-rejoin: AC_A reports Downwind in a LATER cycle than the
        // SET cycle. now > setAtTime → entry clears.
        val setAt = SimTime.ofMillis(10_000)
        val rejoinAt = SimTime.ofMillis(30_000)
        val seeded = BeliefState.EMPTY.copy(
            commitments = mapOf(AC_A to commitment(AC_A, runway = RWY)),
            goAroundInProgressByRunway = mapOf(RWY to GoAroundInProgress(AC_A, setAt)),
        )
        val updated = seeded.withGoAroundInProgress(
            events = listOf(
                ControllerEvent.PositionReported(AC_A, ReportEvent.Downwind(circuitIntent = null)),
            ),
            now = rejoinAt,
        )
        check(updated.goAroundInProgressByRunway.isEmpty()) {
            "Expected belief CLEARED on pattern-rejoin Downwind; got ${updated.goAroundInProgressByRunway}"
        }
    }

    @Test
    fun `fold CLEAR pattern-rejoin Base also clears the entry`() {
        val setAt = SimTime.ofMillis(10_000)
        val rejoinAt = SimTime.ofMillis(30_000)
        val seeded = BeliefState.EMPTY.copy(
            commitments = mapOf(AC_A to commitment(AC_A, runway = RWY)),
            goAroundInProgressByRunway = mapOf(RWY to GoAroundInProgress(AC_A, setAt)),
        )
        val updated = seeded.withGoAroundInProgress(
            events = listOf(ControllerEvent.PositionReported(AC_A, ReportEvent.Base)),
            now = rejoinAt,
        )
        check(updated.goAroundInProgressByRunway.isEmpty())
    }

    @Test
    fun `fold CLEAR 60s timeout drops stale entries even without events`() {
        // R23 lifecycle: entries older than GO_AROUND_TIMEOUT_MS clear
        // deterministically regardless of events.
        val setAt = SimTime.ofMillis(1_000)
        val laterByTimeout = SimTime.ofMillis(1_000 + BeliefState.GO_AROUND_TIMEOUT_MS) // exactly 60s
        val seeded = BeliefState.EMPTY.copy(
            commitments = mapOf(AC_A to commitment(AC_A, runway = RWY)),
            goAroundInProgressByRunway = mapOf(RWY to GoAroundInProgress(AC_A, setAt)),
        )
        val updated = seeded.withGoAroundInProgress(
            events = emptyList(),
            now = laterByTimeout,
        )
        check(updated.goAroundInProgressByRunway.isEmpty()) {
            "Expected belief CLEARED at 60s timeout; got ${updated.goAroundInProgressByRunway}"
        }
    }

    @Test
    fun `fold CLEAR pattern-rejoin from a different aircraft does NOT clear the entry`() {
        // The clear-trigger gates on `entry.aircraftId == ev.aircraft`.
        // Trailing aircraft B reporting Downwind must NOT clear A's entry.
        val setAt = SimTime.ofMillis(10_000)
        val later = SimTime.ofMillis(30_000)
        val seeded = BeliefState.EMPTY.copy(
            commitments = mapOf(AC_A to commitment(AC_A, runway = RWY)),
            goAroundInProgressByRunway = mapOf(RWY to GoAroundInProgress(AC_A, setAt)),
        )
        val updated = seeded.withGoAroundInProgress(
            events = listOf(
                ControllerEvent.PositionReported(AC_B, ReportEvent.Downwind(null)),
            ),
            now = later,
        )
        val entry = updated.goAroundInProgressByRunway[RWY]
            ?: error("Expected belief to survive — different-aircraft pattern call must not clear; got ${updated.goAroundInProgressByRunway}")
        check(entry.aircraftId == AC_A)
    }

    // ─────────────────────────────────────────────────────────────────
    // Rule-level pins: ARR-EXTEND-FOR-GA via controllerDecide
    // ─────────────────────────────────────────────────────────────────

    @Test
    fun `ARR-EXTEND-FOR-GA fires when trailing aircraft on downwind and GA active on same runway`() {
        // Seed: AC_B (trailing) at AwaitApproach on the DOWNWIND point.
        // GA belief for RWY (set on a prior cycle, AC_A is the GA-going
        // aircraft — not represented in this cycle's view, immaterial to
        // the rule's evaluation).
        val previous = baseBeliefs(trailingAircraft = AC_B).copy(
            goAroundInProgressByRunway = mapOf(
                RWY to GoAroundInProgress(AC_A, SimTime.ofMillis(5_000)),
            ),
        )
        val view = baseView(trailingAircraft = AC_B, point = PT_DOWNWIND)
        val result = controllerDecide(view, previous, worldWithRunway())

        val extInstruct = result.outputs
            .filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is ExtendDownwind && it.target == AC_B }
            ?: error("Expected ExtendDownwind to AC_B; got ${result.outputs.map { it::class.simpleName + "/" + (it as? ControllerOutput.Instruct)?.trace?.ruleId }}")
        check(extInstruct.trace.ruleId == "ARR-EXTEND-FOR-GA") {
            "Expected ruleId ARR-EXTEND-FOR-GA; got ${extInstruct.trace.ruleId}"
        }
        check(extInstruct.urgency == Urgency.TIME_SENSITIVE) {
            "Expected TIME_SENSITIVE urgency; got ${extInstruct.urgency}"
        }
    }

    @Test
    fun `ARR-EXTEND-FOR-GA does NOT fire for trailing aircraft when no GA active (negative case)`() {
        // Same geometry as the positive case but no GA belief entry.
        // ARR-EXTEND-FOR-GA must NOT fire. (ARR-EXTEND may or may not
        // fire depending on SeparationConcernAbove gating — both are
        // valid; the negative pin asserts only the NEW rule does not
        // mis-fire.)
        val previous = baseBeliefs(trailingAircraft = AC_B) // no GA entry
        val view = baseView(trailingAircraft = AC_B, point = PT_DOWNWIND)
        val result = controllerDecide(view, previous, worldWithRunway())

        val gaExt = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.trace.ruleId == "ARR-EXTEND-FOR-GA" }
        check(gaExt == null) {
            "Expected NO ARR-EXTEND-FOR-GA when no GA belief active; got $gaExt"
        }
    }

    @Test
    fun `ARR-EXTEND-FOR-GA fires from same-cycle GoAroundDetected fold (round-trip)`() {
        // End-to-end: this cycle delivers AC_A's Report(GoingAround) to
        // the inbox; the controller folds it into beliefs.
        // goAroundInProgressByRunway, then evaluates rules — AC_B on
        // downwind for the same runway gets ARR-EXTEND-FOR-GA.
        // Validates the fold pipeline ordering: withGoAroundInProgress
        // runs AFTER reconcileCommitments (so AC_A's commitment.runway
        // is available for resolveGoAroundRunway) but BEFORE procedure
        // execution (so ARR-EXTEND-FOR-GA sees the updated slice).
        val previous = BeliefState.EMPTY.copy(
            activeRunway = RWY,
            commitments = mapOf(
                AC_A to commitment(AC_A, runway = RWY, stage = TowerArrivalStage.AwaitApproach),
                AC_B to commitment(AC_B, runway = RWY, stage = TowerArrivalStage.AwaitApproach),
            ),
        )
        val view = ControllerView(
            time = SimTime.ofMillis(15_000),
            controllerId = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = ADRM,
            responsibilities = setOf(AC_A, AC_B),
            aircraft = mapOf(
                AC_A to AircraftObservation.fromTestPoint(
                    point = PT_FINAL, worldIndex = TEST_INDEX, id = AC_A,
                    callsign = Callsign("OEAAA"), altitude = Level.AltitudeFeet.unsafe(800),
                    onGround = false,
                ),
                AC_B to AircraftObservation.fromTestPoint(
                    point = PT_DOWNWIND, worldIndex = TEST_INDEX, id = AC_B,
                    callsign = Callsign("OEBBB"), altitude = Level.AltitudeFeet.unsafe(1500),
                    onGround = false,
                ),
            ),
            runways = mapOf(
                RWY to RunwayObservation(
                    id = RWY,
                    status = RunwayStatus.CLEAR,
                    occupants = emptySet(),
                ),
            ),
            activeClearances = emptyMap(),
            receivedMessages = listOf(
                ReceivedMessage.Clear(
                    aircraft = AC_A,
                    transmission = Report(events = listOf(ReportEvent.GoingAround)),
                ),
            ),
            weather = null,
            worldIndex = TEST_INDEX,
            flightStripIntents = mapOf(
                AC_A to AircraftIntent.Arriving,
                AC_B to AircraftIntent.Arriving,
            ),
        )

        val result = controllerDecide(view, previous, worldWithRunway())

        // GA belief written in this cycle.
        val entry = result.updatedBeliefs.goAroundInProgressByRunway[RWY]
            ?: error("Expected GA belief written for $RWY; got ${result.updatedBeliefs.goAroundInProgressByRunway}")
        check(entry.aircraftId == AC_A) {
            "Expected GA belief for AC_A; got ${entry.aircraftId}"
        }
        check(entry.setAtTime == view.time) {
            "Expected setAtTime=${view.time}; got ${entry.setAtTime}"
        }
        // ARR-EXTEND-FOR-GA fires on AC_B in the SAME cycle.
        val ext = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.trace.ruleId == "ARR-EXTEND-FOR-GA" && it.target == AC_B }
            ?: error("Expected ARR-EXTEND-FOR-GA on AC_B same cycle as GA fold; got ${result.outputs}")
        check(ext.instruction is ExtendDownwind)
    }

    @Test
    fun `ARR-TURN-BASE blocked while GA active even if separation+runway clear`() {
        // ARR-TURN-BASE's guard now includes Not(GoAroundInProgressOnRunway).
        // Without GA active, ARR-TURN-BASE would normally fire for a
        // downwind aircraft with separation comfortable. With GA active,
        // the guard fails — TurnBase must NOT fire; ARR-EXTEND-FOR-GA
        // wins instead.
        val previous = baseBeliefs(trailingAircraft = AC_B).copy(
            goAroundInProgressByRunway = mapOf(
                RWY to GoAroundInProgress(AC_A, SimTime.ofMillis(5_000)),
            ),
        )
        val view = baseView(trailingAircraft = AC_B, point = PT_DOWNWIND)
        val result = controllerDecide(view, previous, worldWithRunway())

        val turnBase = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is TurnBase && it.target == AC_B }
        check(turnBase == null) {
            "Expected NO TurnBase to AC_B while GA active on same runway; got $turnBase"
        }
    }

    @Test
    fun `ARR-TURN-BASE fires once GA belief clears via pattern-rejoin (concrete cancel-output)`() {
        // Round-10 Major 2: when the GA belief clears, the controller
        // emits TurnBase to the previously-extended trailing aircraft in
        // the SAME cycle (the fold runs before procedure execution; the
        // belief is gone by the time ARR-TURN-BASE evaluates; the
        // Not(GoAroundInProgressOnRunway) guard now passes).
        //
        // Setup: GA active on RWY (AC_A is the GA-going aircraft,
        // setAtTime = 5_000ms). AC_B is the previously-extended trailing
        // aircraft on downwind. This cycle: view.time = 30_000ms,
        // AC_A transmits Report(Downwind) — pattern-rejoin clears the
        // belief.
        val previous = baseBeliefs(trailingAircraft = AC_B).copy(
            goAroundInProgressByRunway = mapOf(
                RWY to GoAroundInProgress(AC_A, SimTime.ofMillis(5_000)),
            ),
        )
        val view = baseView(
            trailingAircraft = AC_B,
            point = PT_DOWNWIND,
            time = SimTime.ofMillis(30_000),
            receivedMessages = listOf(
                ReceivedMessage.Clear(
                    aircraft = AC_A,
                    transmission = Report(events = listOf(ReportEvent.Downwind(null))),
                ),
            ),
        )
        val result = controllerDecide(view, previous, worldWithRunway())

        // GA belief cleared.
        check(RWY !in result.updatedBeliefs.goAroundInProgressByRunway) {
            "Expected GA belief cleared on pattern-rejoin; got ${result.updatedBeliefs.goAroundInProgressByRunway}"
        }
        // TurnBase fires on AC_B in the SAME cycle.
        val turnBase = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is TurnBase && it.target == AC_B }
            ?: error("Expected TurnBase to AC_B in clear-cycle; got ${result.outputs.filterIsInstance<ControllerOutput.Instruct>().map { it.trace.ruleId + "/" + it.target.value }}")
        check(turnBase.trace.ruleId == "ARR-TURN-BASE") {
            "Expected ruleId ARR-TURN-BASE; got ${turnBase.trace.ruleId}"
        }
    }

    @Test
    fun `ARR-TURN-BASE fires once GA belief clears via 60s timeout`() {
        // Same concrete-cancel-output contract as pattern-rejoin path but
        // via the 60s timeout — bounded recovery if the GA-aircraft's
        // pattern-rejoin transmission is lost / radio failure.
        val previous = baseBeliefs(trailingAircraft = AC_B).copy(
            goAroundInProgressByRunway = mapOf(
                // setAt = 1ms; view.time below is >= 60_000ms → timeout fires.
                RWY to GoAroundInProgress(AC_A, SimTime.ofMillis(1)),
            ),
        )
        val view = baseView(
            trailingAircraft = AC_B,
            point = PT_DOWNWIND,
            time = SimTime.ofMillis(60_001),
        )
        val result = controllerDecide(view, previous, worldWithRunway())

        check(RWY !in result.updatedBeliefs.goAroundInProgressByRunway) {
            "Expected GA belief timed out; got ${result.updatedBeliefs.goAroundInProgressByRunway}"
        }
        val turnBase = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is TurnBase && it.target == AC_B }
            ?: error("Expected TurnBase to AC_B after timeout; got ${result.outputs}")
        check(turnBase.trace.ruleId == "ARR-TURN-BASE")
    }

    // ─────────────────────────────────────────────────────────────────
    // Fixtures
    // ─────────────────────────────────────────────────────────────────

    private fun commitment(
        ac: AircraftId,
        runway: RunwayId?,
        stage: TowerArrivalStage = TowerArrivalStage.AwaitApproach,
    ): Commitment = Commitment(
        aircraft = ac,
        kind = CommitmentKind.TOWER_ARRIVAL,
        stage = stage,
        runway = runway,
        formedAt = SimTime.ZERO,
        contacted = true,
    )

    private fun baseBeliefs(
        trailingAircraft: AircraftId,
        stage: TowerArrivalStage = TowerArrivalStage.AwaitApproach,
    ): BeliefState = BeliefState.EMPTY.copy(
        activeRunway = RWY,
        commitments = mapOf(
            trailingAircraft to commitment(trailingAircraft, runway = RWY, stage = stage),
        ),
    )

    private fun baseView(
        trailingAircraft: AircraftId,
        point: PointId = PT_DOWNWIND,
        time: SimTime = SimTime.ofMillis(10_000),
        receivedMessages: List<ReceivedMessage> = emptyList(),
    ): ControllerView {
        val obs = AircraftObservation.fromTestPoint(
            point = point,
            worldIndex = TEST_INDEX,
            id = trailingAircraft,
            callsign = Callsign("OEBBB"),
            altitude = Level.AltitudeFeet.unsafe(1500),
            onGround = false,
        )
        return ControllerView(
            time = time,
            controllerId = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = ADRM,
            responsibilities = setOf(trailingAircraft),
            aircraft = mapOf(trailingAircraft to obs),
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
            flightStripIntents = mapOf(trailingAircraft to AircraftIntent.Arriving),
        )
    }

    companion object {
        private val ADRM = AerodromeId("LOWG")
        private val AC_A = AircraftId("OE-AAA") // GA-going aircraft
        private val AC_B = AircraftId("OE-BBB") // trailing downwind aircraft
        private val RWY = RunwayId("16C")
        private val RWY_OTHER = RunwayId("28")
        private val PT_DOWNWIND = PointId("DOWNWIND")
        private val PT_FINAL = PointId("FINAL")
        private val PT_THR = PointId("THR")

        private val TEST_INDEX = WorldIndex(
            positions = mapOf(
                PT_FINAL to Position(xMeters = 0.0, yMeters = 0.0),
                PT_DOWNWIND to Position(xMeters = 1000.0, yMeters = 0.0),
                PT_THR to Position(xMeters = 0.0, yMeters = 0.0),
            ),
            circuitLegsByPoint = mapOf(
                PT_FINAL to setOf(LegName.FINAL),
                PT_DOWNWIND to setOf(LegName.DOWNWIND),
            ),
            thresholdByRunway = mapOf(RWY to PT_THR),
        )

        private fun worldWithRunway(): AviationWorld {
            val runway = Runway(
                id = RWY,
                path = Path(listOf(PT_THR, PointId("DEP"))),
                threshold = PT_THR,
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
