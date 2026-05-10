package xyz.easiersaid.twr.controller

import arrow.core.NonEmptyList
import xyz.easiersaid.twr.controller.bdi.Commitment
import xyz.easiersaid.twr.controller.bdi.CommitmentKind
import xyz.easiersaid.twr.controller.bdi.Dispatch
import xyz.easiersaid.twr.controller.bdi.TowerArrivalStage
import xyz.easiersaid.twr.controller.certify.CertificationEvidence
import xyz.easiersaid.twr.controller.observe.AdvancementPolicy
import xyz.easiersaid.twr.controller.observe.BeliefState
import xyz.easiersaid.twr.controller.observe.CoordinationState
import xyz.easiersaid.twr.controller.observe.OutstandingCoordination
import xyz.easiersaid.twr.controller.assess.RunwayDutyState
import xyz.easiersaid.twr.controller.assess.RunwayOperation
import xyz.easiersaid.twr.core.world.Aerodrome
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Degrees
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
import xyz.easiersaid.twr.protocol.ContinueApproach
import xyz.easiersaid.twr.protocol.ContinueApproachReason
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.GoAround
import xyz.easiersaid.twr.protocol.Knots
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.RunwayObstructionInformation
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Urgency
import kotlin.test.Test

/**
 * fn-13.1 (R8): controller-level regression tests for the
 * `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule. End-to-end via
 * [controllerDecide] — no test-fixture seeding bypasses the rule pipeline.
 *
 * Coverage (per fn-13.1 task spec Step 9, 13 pins):
 *  1. Rule fires when predicate holds (positive case).
 *  2. Rule does NOT fire when predicate fails (clears too late) — GA fires.
 *  3. Rule does NOT fire when groundSpeed missing — fail-closed false → GA.
 *  4. Rule does NOT fire when threshold unknown — fail-closed false → GA.
 *  4b. Snap-vs-coords divergence: guard uses `ac.coords`, not the
 *      graph-snapped `worldIndex.positions[ac.position]`.
 *  5. Witness suppression: after first CA fires, subsequent ticks do not
 *      re-fire while obstruction + predicate persist.
 *  6. Re-arm on `Report(Downwind)`: witness clears.
 *  7. Escalation to GA + supersession: predicate flips false; GA fires
 *      and the stale ContinueApproach coordination is superseded.
 *  8. Commitment state preservation: stage unchanged; sticky witnesses
 *      other than `continueApproachIssuedThisAttempt` untouched.
 *  9. Reason populated as `RUNWAY_OBSTRUCTED`.
 * 10. No effect at `LandingClearanceIssued` / `AwaitLandedObserved`:
 *      rule not registered there; fn-12's GA rule fires.
 * 11. Normal-success supersession: CA issued → obstruction clears →
 *      `ARR-LAND` fires `ClearedToLand` → stale CA coord cleaned up.
 * 12. Pre-existing `ARR-CONTINUE` rule UNCHANGED for non-obstruction
 *      triggers.
 * 13. Companion-trace regs split: companion's `DecisionTrace.regulations`
 *      cites `§4.55, §4.56, §12.3.4.16, §8.9.6.1.8` AND does NOT cite
 *      `§4.65` or `§7.4.1.4.1`. Regression check on fn-12's GA path
 *      (companion still cites `§4.65` + `§7.4.1.4.1`) is in
 *      [ObstructionGoAroundSpec].
 */
@Suppress("LargeClass") // 13 pins per task spec; splitting into multiple spec files
// fragments the single-rule coverage that this spec exists to centralize.
class ObstructionContinueApproachSpec {

    // ── Pin 1: rule fires when predicate holds ───────────────────────────

    @Test
    fun `rule fires when predicate holds and emits ContinueApproach + companion + sets witness`() {
        // Obstruction clears at T+15s; aircraft 2000m out at 80 kt
        // → eta ≈ (2000 / (80 * 1852 / 3600 ≈ 41.16)) = 48.6s
        // gap = (15000 - 10000 [now]) + 10000 [margin] = 15000ms
        // predicate: 15000 <= 48600 → TRUE → CA fires
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(15_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        )
        val view = baseView(groundSpeed = Knots.unsafe(80))

        val result = controllerDecide(view, previous, worldWithRunway())

        val caInstruct = result.outputs
            .filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is ContinueApproach }
            ?: error("Expected a ContinueApproach instruction; got ${result.outputs}")
        check(caInstruct.trace.ruleId == "ARR-CONTINUE-APPROACH-OBSTRUCTION") {
            "Expected rule trace ARR-CONTINUE-APPROACH-OBSTRUCTION; got ${caInstruct.trace.ruleId}"
        }
        check(caInstruct.urgency == Urgency.TIME_SENSITIVE) {
            "CONTINUE APPROACH urgency must be TIME_SENSITIVE; got ${caInstruct.urgency}"
        }

        // Companion emitted in same cycle
        val companion = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .firstOrNull { it.response is RunwayObstructionInformation }
            ?: error("Expected RunwayObstructionInformation companion; got ${result.outputs}")
        val info = companion.response as RunwayObstructionInformation
        check(info.runway == RWY) { "companion runway mismatch: ${info.runway} vs $RWY" }
        check(info.clearsAt == obs.clearsAt) {
            "companion clearsAt mismatch: ${info.clearsAt} vs ${obs.clearsAt}"
        }

        // Stage stays at AwaitApproach (nextStage = null on the new rule)
        val updated = result.updatedBeliefs.commitments.getValue(AC)
        check(updated.stage == TowerArrivalStage.AwaitApproach) {
            "Commitment must stay at AwaitApproach after CA; got ${updated.stage}"
        }
        // Witness flipped true
        check(updated.continueApproachIssuedThisAttempt) {
            "Witness continueApproachIssuedThisAttempt must be true after committed-output CA"
        }
        // GA witness NOT set (different rule fired)
        check(!updated.obstructionGoAroundIssuedThisAttempt) {
            "GA witness must remain false when CA fires; got ${updated.obstructionGoAroundIssuedThisAttempt}"
        }
    }

    // ── Pin 2: rule does NOT fire when predicate fails (clears too late) → GA fires

    @Test
    fun `rule does NOT fire when obstruction clears too late — GA fires instead via narrowed guard`() {
        // Obstruction clears at T+200s; ETA still ≈ 48.6s
        // gap = (200000 - 10000) + 10000 = 200000 ms ≫ 48600 → predicate FALSE
        // The narrowed GA rule's `Not(ObstructionClearsInTime)` then holds → GA fires.
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(210_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        )
        val view = baseView(groundSpeed = Knots.unsafe(80))

        val result = controllerDecide(view, previous, worldWithRunway())

        val ca = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is ContinueApproach }
        check(ca == null) {
            "CONTINUE APPROACH must NOT fire when obstruction clears too late; got $ca"
        }
        val ga = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is GoAround }
            ?: error("Expected GoAround when predicate fails; got ${result.outputs}")
        check(ga.trace.ruleId == "ARR-GO-AROUND-RUNWAY-OBSTRUCTED") {
            "Expected GA from obstructionGoAroundRule; got ${ga.trace.ruleId}"
        }
    }

    // ── Pin 3: groundSpeed missing → fail-closed false → GA fires ────────

    @Test
    fun `rule does NOT fire when groundSpeed missing — fail-closed false → GA fires`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(15_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        )
        val view = baseView(groundSpeed = null) // missing

        val result = controllerDecide(view, previous, worldWithRunway())

        check(result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .none { it.instruction is ContinueApproach }) {
            "CONTINUE APPROACH must NOT fire when groundSpeed missing; got ${result.outputs}"
        }
        val ga = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is GoAround }
            ?: error("Expected GA when groundSpeed missing; got ${result.outputs}")
        check(ga.trace.ruleId == "ARR-GO-AROUND-RUNWAY-OBSTRUCTED") {
            "Expected obstruction GA; got ${ga.trace.ruleId}"
        }
    }

    // ── Pin 4: threshold unknown → fail-closed false → GA fires ──────────

    @Test
    fun `rule does NOT fire when threshold unknown — fail-closed false → GA fires`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(15_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        )
        // World index WITHOUT a threshold entry → predicate fail-closed false.
        // Geometric guard (`OnCircuitLeg(FINAL)`) still passes via circuitLegsByPoint.
        val indexWithoutThreshold = WorldIndex(
            positions = mapOf(
                PointId("FINAL") to Position(xMeters = -2000.0, yMeters = 0.0),
                PointId("DOWNWIND") to Position(xMeters = 1000.0, yMeters = 0.0),
                PointId("THR") to Position(xMeters = 0.0, yMeters = 0.0),
            ),
            circuitLegsByPoint = mapOf(
                PointId("FINAL") to setOf(LegName.FINAL),
                PointId("DOWNWIND") to setOf(LegName.DOWNWIND),
            ),
            thresholdByRunway = emptyMap(), // ← missing!
        )
        val view = baseView(groundSpeed = Knots.unsafe(80), worldIndex = indexWithoutThreshold)

        val result = controllerDecide(view, previous, worldWithRunway())

        check(result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .none { it.instruction is ContinueApproach }) {
            "CONTINUE APPROACH must NOT fire when threshold unknown; got ${result.outputs}"
        }
        val ga = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is GoAround }
            ?: error("Expected GA when threshold unknown; got ${result.outputs}")
        check(ga.trace.ruleId == "ARR-GO-AROUND-RUNWAY-OBSTRUCTED") {
            "Expected obstruction GA; got ${ga.trace.ruleId}"
        }
    }

    // ── Pin 4b: snap-vs-coords divergence — guard uses `coords` ──────────

    @Test
    fun `predicate uses ac coords not graph-snapped position`() {
        // Snap-vs-coords divergence pin (codex iter 5): snap point FINAL is
        // 2000m from threshold (snap-eta ≈ 48.6s at 80 kt). Coordinate
        // override places the aircraft kinematically only 100m from
        // threshold (coords-eta ≈ 2.43s).
        //
        // clearsAt set to T+25s → gap = (25000 - 10000) + 10000 = 25000ms.
        //  - Using snap (eta = 48600ms): 25000 <= 48600 → TRUE → CA fires.
        //  - Using coords (eta = 2430ms): 25000 <= 2430 → FALSE → GA fires.
        //
        // Correct (coords-using) implementation must produce GA; observing
        // CA would prove the guard used the graph-snapped point (the
        // production regression).
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(25_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        )
        val coordsOverride = Position(xMeters = -100.0, yMeters = 0.0)
        val view = baseView(groundSpeed = Knots.unsafe(80), coordsOverride = coordsOverride)

        val result = controllerDecide(view, previous, worldWithRunway())

        check(result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .none { it.instruction is ContinueApproach }) {
            "Guard must use ac.coords (very close, short eta → predicate false → GA), " +
                "not the snap point (FINAL = 2000m out, long eta → predicate true → CA); " +
                "got ${result.outputs}"
        }
        val ga = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is GoAround }
            ?: error(
                "Expected GA — coords place aircraft 100m from threshold, eta < clearsAt, " +
                    "predicate must evaluate false; got ${result.outputs}",
            )
        check(ga.trace.ruleId == "ARR-GO-AROUND-RUNWAY-OBSTRUCTED") {
            "Expected obstruction GA; got ${ga.trace.ruleId}"
        }
    }

    // ── Pin 5: witness suppression (no re-fire) ──────────────────────────

    @Test
    fun `witness suppression — once CA witness is set the rule does not re-fire on next cycle`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(15_000))
        // Seed post-first-fire state: witness set; obstruction still active.
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        ).let { b ->
            val c = b.commitments.getValue(AC).copy(
                continueApproachIssuedThisAttempt = true,
            )
            b.copy(commitments = b.commitments + (AC to c))
        }
        val view = baseView(groundSpeed = Knots.unsafe(80))

        val result = controllerDecide(view, previous, worldWithRunway())

        val ca = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is ContinueApproach }
        check(ca == null) {
            "Witness-set commitment must NOT re-fire ARR-CONTINUE-APPROACH-OBSTRUCTION; got $ca"
        }
    }

    // ── Pin 6: re-arm on Report(Downwind) ────────────────────────────────

    @Test
    fun `re-arm — Report(Downwind) clears the CA witness so a fresh obstruction can drive a fresh CA`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(15_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitDownwind,
            obstruction = obs,
        ).let { b ->
            val c = b.commitments.getValue(AC).copy(
                continueApproachIssuedThisAttempt = true,
            )
            b.copy(commitments = b.commitments + (AC to c))
        }
        val view = baseView(
            point = PointId("DOWNWIND"),
            groundSpeed = Knots.unsafe(80),
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

        val updated = result.updatedBeliefs.commitments[AC]
            ?: error("Commitment must persist; got ${result.updatedBeliefs.commitments}")
        check(!updated.continueApproachIssuedThisAttempt) {
            "Witness must be re-armed (cleared) on Report(Downwind); got ${updated.continueApproachIssuedThisAttempt}"
        }
    }

    // ── Pin 7: escalation to GA + supersession of stale CA coord ─────────

    @Test
    fun `escalation to GA — predicate flips false → GA fires and supersedes stale ContinueApproach coordination`() {
        // Models the real post-CA escalation cycle (codex round-2
        // strengthening): on a previous cycle, the CA rule fired (witness
        // is set, coordination is pending). Now `clearsAt` slipped /
        // groundSpeed dropped → `ObstructionClearsInTime` is false →
        // narrowed GA rule's guard passes. The CA witness MUST NOT block
        // the GA rule (different witnesses; the CA witness only gates
        // re-firing of the CA rule itself), so the GA fires AND the stale
        // CA coordination is superseded.
        //
        // Regression we guard against: a future refactor making the GA
        // rule check `Not(ContinueApproachAlreadyIssuedThisAttempt)`
        // would silently break escalation — this test catches it because
        // the CA witness is `true` here.
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(210_000))
        val caCoord = OutstandingCoordination(
            aircraft = AC,
            dispatch = Dispatch.Direct(
                ContinueApproach(target = AC, reason = ContinueApproachReason.RUNWAY_OBSTRUCTED),
            ),
            issuedAt = SimTime.ofMillis(5_000),
            state = CoordinationState.Issued,
            expectedReadback = emptySet(),
            certificationEvidence = NonEmptyList(
                CertificationEvidence.RuntimeChecked(checkId = "test", summary = "test"),
                emptyList(),
            ),
        )
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
            coordinations = mapOf(AC to listOf(caCoord)),
        ).let { b ->
            val c = b.commitments.getValue(AC).copy(
                // Post-CA state — the witness was set on a prior cycle by
                // the applyCommittedOutputWitnesses pass.
                continueApproachIssuedThisAttempt = true,
                // GA witness still false — we want the GA rule to be
                // eligible by its own witness gate.
                obstructionGoAroundIssuedThisAttempt = false,
            )
            b.copy(commitments = b.commitments + (AC to c))
        }
        val view = baseView(groundSpeed = Knots.unsafe(80))

        val result = controllerDecide(view, previous, worldWithRunway())

        val ga = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is GoAround }
            ?: error(
                "Expected GoAround when predicate flips false; got ${result.outputs}; " +
                    "skipped=${result.trace.skippedActions}",
            )
        check(ga.trace.ruleId == "ARR-GO-AROUND-RUNWAY-OBSTRUCTED") {
            "Expected obstruction GA; got ${ga.trace.ruleId}"
        }

        // Stale ContinueApproach coordination must be cleaned up by
        // the GA→CA supersession entry.
        val remaining = result.updatedBeliefs.coordinations[AC].orEmpty()
            .filter { it.instruction is ContinueApproach }
        check(remaining.isEmpty()) {
            "Stale ContinueApproach coordination must be superseded by GoAround; got $remaining"
        }

        // GA witness must be set on this cycle (committed-output path).
        val updated = result.updatedBeliefs.commitments.getValue(AC)
        check(updated.obstructionGoAroundIssuedThisAttempt) {
            "GA witness must be set after committed-output escalation; " +
                "got ${updated.obstructionGoAroundIssuedThisAttempt}"
        }
    }

    // ── Pin 8: commitment-state preservation ─────────────────────────────

    @Test
    fun `commitment-state preservation - only continueApproachIssuedThisAttempt flips, other witnesses unchanged`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(15_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        )
        val view = baseView(groundSpeed = Knots.unsafe(80))

        val result = controllerDecide(view, previous, worldWithRunway())

        val updated = result.updatedBeliefs.commitments.getValue(AC)
        check(updated.stage == TowerArrivalStage.AwaitApproach) {
            "Stage MUST NOT change on CA fire; got ${updated.stage}"
        }
        check(updated.kind == CommitmentKind.TOWER_ARRIVAL) {
            "Kind must be unchanged; got ${updated.kind}"
        }
        check(updated.runway == RWY) {
            "Runway must be unchanged; got ${updated.runway}"
        }
        check(updated.continueApproachIssuedThisAttempt) {
            "CA witness must be true after fire"
        }
        check(!updated.obstructionGoAroundIssuedThisAttempt) {
            "GA witness must remain false"
        }
        // Other sticky witnesses unchanged (default false / empty)
        check(!updated.touchedDownDuringCommitment) { "touchedDown witness should remain false" }
        check(!updated.pilotReadyDuringCommitment) { "pilotReady witness should remain false" }
        check(updated.observedReportsDuringCommitment.isEmpty()) {
            "observedReports should remain empty; got ${updated.observedReportsDuringCommitment}"
        }
    }

    // ── Pin 9: reason populated as RUNWAY_OBSTRUCTED ─────────────────────

    @Test
    fun `reason populated — emitted ContinueApproach instruction has reason RUNWAY_OBSTRUCTED`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(15_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        )
        val view = baseView(groundSpeed = Knots.unsafe(80))

        val result = controllerDecide(view, previous, worldWithRunway())

        val ca = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is ContinueApproach }
            ?: error("Expected ContinueApproach; got ${result.outputs}")
        val instr = ca.instruction as ContinueApproach
        check(instr.reason == ContinueApproachReason.RUNWAY_OBSTRUCTED) {
            "CA reason must be RUNWAY_OBSTRUCTED; got ${instr.reason}"
        }
    }

    // ── Pin 10: no effect at LandingClearanceIssued / AwaitLandedObserved

    @Test
    fun `no effect at LandingClearanceIssued - new rule not registered there, fn-12 GA fires`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(15_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.LandingClearanceIssued,
            obstruction = obs,
        )
        val view = baseView(groundSpeed = Knots.unsafe(80))

        val result = controllerDecide(view, previous, worldWithRunway())

        check(result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .none { it.instruction is ContinueApproach }) {
            "CONTINUE APPROACH rule must NOT be registered at LandingClearanceIssued; got ${result.outputs}"
        }
        val ga = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is GoAround }
            ?: error("Expected post-clearance GA; got ${result.outputs}")
        check(ga.trace.ruleId == "ARR-GO-AROUND-RUNWAY-OBSTRUCTED") {
            "Expected obstruction GA at post-clearance stage; got ${ga.trace.ruleId}"
        }
    }

    @Test
    fun `no effect at AwaitLandedObserved - new rule not registered there, fn-12 GA fires`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(15_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitLandedObserved,
            obstruction = obs,
        )
        val view = baseView(groundSpeed = Knots.unsafe(80))

        val result = controllerDecide(view, previous, worldWithRunway())

        check(result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .none { it.instruction is ContinueApproach }) {
            "CONTINUE APPROACH rule must NOT be registered at AwaitLandedObserved; got ${result.outputs}"
        }
        val ga = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .firstOrNull { it.instruction is GoAround }
            ?: error("Expected post-clearance GA; got ${result.outputs}")
        check(ga.trace.ruleId == "ARR-GO-AROUND-RUNWAY-OBSTRUCTED") {
            "Expected obstruction GA at post-clearance stage; got ${ga.trace.ruleId}"
        }
    }

    // ── Pin 11: normal-success supersession (ClearedToLand → ContinueApproach)

    @Test
    fun `normal-success supersession — ClearedToLand supersedes stale ContinueApproach coordination`() {
        // Direct supersession-relation pin: seed a pending CA coordination
        // and have the controller issue ClearedToLand (no obstruction, all
        // landing conditions met). After the cycle, the CA coordination
        // must be cleaned up by the ClearedToLand → ContinueApproach
        // supersession entry.
        //
        // We exercise applySupersessionCleanup directly via a committed
        // ClearedToLand on a clean (no-obstruction) state to keep the test
        // independent of the rule's eligibility conditions (which would
        // require seeding many witnesses).
        val caCoord = OutstandingCoordination(
            aircraft = AC,
            dispatch = Dispatch.Direct(
                ContinueApproach(target = AC, reason = ContinueApproachReason.RUNWAY_OBSTRUCTED),
            ),
            issuedAt = SimTime.ofMillis(5_000),
            state = CoordinationState.Issued,
            expectedReadback = emptySet(),
            certificationEvidence = NonEmptyList(
                CertificationEvidence.RuntimeChecked(checkId = "test", summary = "test"),
                emptyList(),
            ),
        )
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(AC to listOf(caCoord)),
        )

        // Apply supersession with a ClearedToLand on the same aircraft.
        val after = xyz.easiersaid.twr.controller.bdi.applySupersessionCleanup(
            beliefs,
            listOf(AC to ClearedToLand(target = AC, runway = RWY)),
        )

        val remaining = after.coordinations[AC].orEmpty()
            .filter { it.instruction is ContinueApproach }
        check(remaining.isEmpty()) {
            "ClearedToLand must supersede stale ContinueApproach coordination; got $remaining"
        }
    }

    @Test
    fun `normal-success supersession — ClearedTouchAndGo supersedes stale ContinueApproach coordination`() {
        val caCoord = OutstandingCoordination(
            aircraft = AC,
            dispatch = Dispatch.Direct(
                ContinueApproach(target = AC, reason = ContinueApproachReason.RUNWAY_OBSTRUCTED),
            ),
            issuedAt = SimTime.ofMillis(5_000),
            state = CoordinationState.Issued,
            expectedReadback = emptySet(),
            certificationEvidence = NonEmptyList(
                CertificationEvidence.RuntimeChecked(checkId = "test", summary = "test"),
                emptyList(),
            ),
        )
        val beliefs = BeliefState.EMPTY.copy(
            coordinations = mapOf(AC to listOf(caCoord)),
        )

        val after = xyz.easiersaid.twr.controller.bdi.applySupersessionCleanup(
            beliefs,
            listOf(AC to xyz.easiersaid.twr.protocol.ClearedTouchAndGo(target = AC, runway = RWY)),
        )

        val remaining = after.coordinations[AC].orEmpty()
            .filter { it.instruction is ContinueApproach }
        check(remaining.isEmpty()) {
            "ClearedTouchAndGo must supersede stale ContinueApproach coordination; got $remaining"
        }
    }

    // ── Pin 12: pre-existing ARR-CONTINUE rule UNCHANGED ─────────────────

    @Test
    fun `existing ARR-CONTINUE rule still fires for non-obstruction trigger (positive case)`() {
        // Direct guard-level pin (codex round-2 strengthening): exercise the
        // existing `ARR-CONTINUE` rule's guard predicate against a
        // non-obstruction fixture and assert it would fire. We bypass the
        // full `controllerDecide` pipeline (which would also pick up
        // `ARR-GO-AROUND` / `ARR-LAND` competition in this contrived
        // single-aircraft fixture) and evaluate the rule's guard directly,
        // which is the smallest possible regression check that the
        // existing rule remains eligible.
        val rules = xyz.easiersaid.twr.controller.procedure.towerArrivalProcedure()
            .stageRules[TowerArrivalStage.AwaitApproach]
            ?: error("Could not locate AwaitApproach rules")
        val arrContinue = rules.firstOrNull { it.id == "ARR-CONTINUE" }
            ?: error("ARR-CONTINUE rule not present in AwaitApproach stageRules")
        val arrContinueObstruction = rules.firstOrNull {
            it.id == "ARR-CONTINUE-APPROACH-OBSTRUCTION"
        } ?: error("ARR-CONTINUE-APPROACH-OBSTRUCTION rule not present")

        // Fixture: no obstruction, runway not granted to this aircraft,
        // runway physically clear (default). The existing ARR-CONTINUE's
        // `Not(RunwayAccessGranted)` arm should pass.
        val commitment = Commitment(
            aircraft = AC,
            kind = CommitmentKind.TOWER_ARRIVAL,
            stage = TowerArrivalStage.AwaitApproach,
            runway = RWY,
            formedAt = SimTime.ZERO,
            contacted = true,
        )
        val beliefs = BeliefState.EMPTY.copy(
            activeRunway = RWY,
            commitments = mapOf(AC to commitment),
            // NO runwayObstructions, NO runwayDuty → Not(RunwayAccessGranted)
            // for AC.
        )
        val ac = AircraftObservation.fromTestPoint(
            point = PointId("FINAL"),
            worldIndex = TEST_INDEX,
            id = AC,
            callsign = Callsign("OEABC"),
            altitude = Level.AltitudeFeet.unsafe(800),
            groundSpeed = Knots.unsafe(80),
            onGround = false,
        )
        val view = ControllerView(
            time = SimTime.ofMillis(10_000),
            controllerId = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = ADRM,
            responsibilities = setOf(AC),
            aircraft = mapOf(AC to ac),
            runways = mapOf(RWY to RunwayObservation(RWY, RunwayStatus.CLEAR, emptySet())),
            activeClearances = emptyMap(),
            receivedMessages = emptyList(),
            weather = null,
            worldIndex = TEST_INDEX,
            flightStripIntents = mapOf(AC to AircraftIntent.Arriving),
        )
        val ctx = xyz.easiersaid.twr.controller.bdi.OperatorContext(
            view = view,
            beliefs = beliefs,
            events = emptyList(),
            world = worldWithRunway(),
        )

        // Pin: existing rule's guard passes for this aircraft / context.
        check(arrContinue.guard.evaluate(ac, commitment, ctx)) {
            "Existing ARR-CONTINUE rule's guard must pass for non-obstruction trigger " +
                "(Not(RunwayAccessGranted) arm). Regression: fn-13.1 narrowing must not affect " +
                "this rule's eligibility."
        }
        // Pin: new rule's guard FAILS (no obstruction).
        check(!arrContinueObstruction.guard.evaluate(ac, commitment, ctx)) {
            "New ARR-CONTINUE-APPROACH-OBSTRUCTION must NOT fire without obstruction in beliefs."
        }
        // Pin: existing action emits a ContinueApproach whose reason is
        // NOT RUNWAY_OBSTRUCTED (it comes from `inferContinueApproachReason`,
        // which returns RUNWAY_ACCESS_PENDING for this fixture).
        val resolved = arrContinue.action!!.resolve(ac, commitment, ctx).getOrNull()
            ?: error("Existing ARR-CONTINUE action must resolve for non-obstruction trigger")
        val instr = resolved.instruction as ContinueApproach
        check(instr.reason != ContinueApproachReason.RUNWAY_OBSTRUCTED) {
            "Existing ARR-CONTINUE must NOT set RUNWAY_OBSTRUCTED reason " +
                "(that's only for the new obstruction-specific action); got ${instr.reason}"
        }
    }

    @Test
    fun `new rule does NOT fire when no runway obstruction is declared`() {
        // Pin 12 (per task spec): verify the new
        // `ARR-CONTINUE-APPROACH-OBSTRUCTION` rule is gated on the
        // RunwayObstructed guard — if no obstruction is in beliefs, the
        // rule does NOT fire (and the existing ARR-CONTINUE rule's
        // eligibility is unchanged because the new rule doesn't shadow it
        // when there's no obstruction).
        //
        // This pin focuses narrowly on the negative case for the new
        // rule. The full traffic-driven `ARR-CONTINUE` firing path is
        // covered by pre-existing G1/G2 golden tests; here we only need
        // to verify the new rule's guard correctly requires obstruction.
        val previous = BeliefState.EMPTY.copy(
            activeRunway = RWY,
            commitments = mapOf(
                AC to Commitment(
                    aircraft = AC,
                    kind = CommitmentKind.TOWER_ARRIVAL,
                    stage = TowerArrivalStage.AwaitApproach,
                    runway = RWY,
                    formedAt = SimTime.ZERO,
                    contacted = true,
                ),
            ),
            // No `runwayObstructions` — new rule must NOT fire.
        )
        val view = baseView(groundSpeed = Knots.unsafe(80))

        val result = controllerDecide(view, previous, worldWithRunway())

        // Whatever rule(s) fire, NONE should be ARR-CONTINUE-APPROACH-OBSTRUCTION.
        val newRuleFirings = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .filter { it.trace.ruleId == "ARR-CONTINUE-APPROACH-OBSTRUCTION" }
        check(newRuleFirings.isEmpty()) {
            "New rule must NOT fire when no obstruction is in beliefs; got $newRuleFirings"
        }
        // Also: any ContinueApproach instruction emitted (from the
        // existing ARR-CONTINUE rule) must NOT carry the RUNWAY_OBSTRUCTED
        // reason (which is only set by ObstructionContinueApproachAction).
        val cas = result.outputs.filterIsInstance<ControllerOutput.Instruct>()
            .filter { it.instruction is ContinueApproach }
        cas.forEach { ca ->
            val instr = ca.instruction as ContinueApproach
            check(instr.reason != ContinueApproachReason.RUNWAY_OBSTRUCTED) {
                "Non-obstruction ContinueApproach must NOT carry RUNWAY_OBSTRUCTED reason; " +
                    "got rule=${ca.trace.ruleId} reason=${instr.reason}"
            }
        }
    }

    // ── Pin 13: companion-trace regs split ───────────────────────────────

    @Test
    fun `companion-trace regs split - CONTINUE APPROACH cites 4_55 4_56 12_3_4_16 8_9_6_1_8 and excludes 4_65 + 7_4_1_4_1`() {
        val obs = RunwayObstruction(clearsAt = SimTime.ofMillis(15_000))
        val previous = baseBeliefs(
            stage = TowerArrivalStage.AwaitApproach,
            obstruction = obs,
        )
        val view = baseView(groundSpeed = Knots.unsafe(80))

        val result = controllerDecide(view, previous, worldWithRunway())

        val companion = result.outputs
            .filterIsInstance<ControllerOutput.Respond>()
            .firstOrNull { it.response is RunwayObstructionInformation }
            ?: error("Expected RunwayObstructionInformation companion; got ${result.outputs}")
        val regs = companion.trace.regulations.map { it.section }
        check(regs.containsAll(listOf("§4.55", "§4.56", "§12.3.4.16", "§8.9.6.1.8"))) {
            "CONTINUE APPROACH companion must cite §4.55, §4.56, §12.3.4.16, §8.9.6.1.8; got $regs"
        }
        check("§4.65" !in regs) {
            "CONTINUE APPROACH companion must NOT cite §4.65 (missed-approach phraseology); got $regs"
        }
        check("§7.4.1.4.1" !in regs) {
            "CONTINUE APPROACH companion must NOT cite §7.4.1.4.1 (post-clearance GA mandate); got $regs"
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
            ),
        ),
        runwayObstructions = mapOf(RWY to obstruction),
        coordinations = coordinations,
        // Grant runway access so the *non-obstruction* CONTINUE APPROACH
        // path (existing ARR-CONTINUE) does NOT misfire — its guard is
        // Not(RunwayAccessGranted) OR Not(RunwayPhysicallyClear), and we
        // want only the obstruction path to be eligible.
        runwayDuty = RunwayDutyState(
            runway = RWY,
            holder = AC,
            operation = RunwayOperation.ARRIVAL,
            holderReachedRunway = false,
        ),
    )

    private fun baseView(
        point: PointId = PointId("FINAL"),
        groundSpeed: Knots? = null,
        receivedMessages: List<ReceivedMessage> = emptyList(),
        worldIndex: WorldIndex = TEST_INDEX,
        coordsOverride: Position? = null,
        runwayObservation: RunwayObservation = RunwayObservation(
            id = RWY,
            status = RunwayStatus.CLEAR,
            occupants = emptySet(),
        ),
    ): ControllerView {
        val obs = AircraftObservation.fromTestPoint(
            point = point,
            worldIndex = worldIndex,
            id = AC,
            callsign = Callsign("OEABC"),
            altitude = Level.AltitudeFeet.unsafe(800),
            groundSpeed = groundSpeed,
            onGround = false,
            coordsOverride = coordsOverride,
        )
        return ControllerView(
            time = SimTime.ofMillis(10_000),
            controllerId = ControllerId("LOWG_TWR"),
            role = RoleName.TOWER,
            aerodromeId = ADRM,
            responsibilities = setOf(AC),
            aircraft = mapOf(AC to obs),
            runways = mapOf(RWY to runwayObservation),
            activeClearances = emptyMap(),
            receivedMessages = receivedMessages,
            weather = null,
            worldIndex = worldIndex,
            flightStripIntents = mapOf(AC to AircraftIntent.Arriving),
        )
    }

    companion object {
        private val ADRM = AerodromeId("LOWG")
        private val AC = AircraftId("OE-ABC")
        private val RWY = RunwayId("16C")

        // FINAL is 2000m out from THR on the negative-x axis. This gives a
        // distance of 2000m and an ETA of ~48.6s at 80 kt.
        private val TEST_INDEX = WorldIndex(
            positions = mapOf(
                PointId("FINAL") to Position(xMeters = -2000.0, yMeters = 0.0),
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
