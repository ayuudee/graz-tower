package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.assess.RunwayDutyState
import xyz.easiersaid.twr.controller.assess.RunwayOperation
import xyz.easiersaid.twr.controller.bdi.*
import xyz.easiersaid.twr.controller.observe.*
import xyz.easiersaid.twr.controller.procedure.*
import xyz.easiersaid.twr.core.world.EntityRef
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exhaustive condition-space permutation tests for arrival procedure rules.
 *
 * For each arrival stage, enumerates every boolean combination of guard-relevant
 * conditions, calls [executeProcedure], and verifies structural properties:
 *
 * 1. **SAFETY priority**: when a SAFETY rule's guard conditions are met, a SAFETY-urgency
 *    rule fires — regardless of other flags.
 * 2. **No ambiguous overlap**: at most one non-SAFETY rule fires for any combination.
 * 3. **No blind spots**: critical conditions always produce controller action.
 * 4. **No silent failures**: action resolution never fails in any cell.
 *
 * The arrival machine differs from departure: go-around is a first-class regression,
 * handled here via [executeProcedure] interrupt processing (GA-PRE-CLEAR, GA-POST-CLEAR)
 * and via SAFETY rules (ARR-GO-AROUND, ARR-GO-AROUND-CLEARANCE-ISSUED).
 *
 * Total combinations across all arrival stages: 208.
 */
class ArrivalConditionSpaceTest {

    private val worldIndex = testWorldIndex()
    private val world = testWorld()
    private val spec = towerArrivalProcedure()
    private val ac = TestIds.acAlpha
    private val other = TestIds.acBravo
    private val time = SimTime.ofSeconds(0)

    private val bools = listOf(false, true)

    // ── SAFETY rule IDs per stage ───────────────────────────────────────

    private val awaitApproachSafetyRules = setOf("ARR-GO-AROUND")
    private val landingIssuedSafetyRules = setOf("ARR-GO-AROUND-CLEARANCE-ISSUED")

    // ── Shared helpers ──────────────────────────────────────────────────

    private fun commitment(
        stage: Stage,
        contacted: Boolean = true,
    ) = Commitment(
        aircraft = ac,
        kind = CommitmentKind.TOWER_ARRIVAL,
        stage = stage,
        runway = TestIds.runway09,
        formedAt = time,
        contacted = contacted,
    )

    /**
     * Build an aircraft observation at [point].
     * When [onApproach] is true, appends an [EntityRef.ApproachRef] to the entity set,
     * simulating an aircraft on an IFR approach procedure.
     */
    private fun aircraft(
        point: PointId = TestIds.holdShort,
        onGround: Boolean = true,
        humanPiloted: Boolean = false,
        goal: PilotGoal = PilotGoal.ARRIVE,
        onApproach: Boolean = false,
    ): AircraftObservation {
        val base = aircraftAt(ac, point, worldIndex, onGround = onGround, humanPiloted = humanPiloted, goal = goal)
        return if (onApproach)
            base.copy(entities = base.entities + EntityRef.ApproachRef(ApproachId("ILS09")))
        else
            base
    }

    private fun beliefs(
        runwayAccess: Boolean = false,
        runwayClear: Boolean = true,
        /**
         * When false, injects a COMFORTABLE separation assessment so that
         * [SeparationConcernAbove(INTERVENTION)] evaluates to false.
         * When true (default), the assessment list is empty — the guard's conservative
         * default treats empty assessments as "concern present".
         */
        separationConcern: Boolean = true,
        pendingInstruction: AtcInstruction? = null,
    ): BeliefState {
        val rwyObs = if (runwayClear)
            RunwayObservation(TestIds.runway09, RunwayStatus.CLEAR, emptySet())
        else
            RunwayObservation(TestIds.runway09, RunwayStatus.OCCUPIED_LANDING, setOf(other))

        val duty = if (runwayAccess)
            RunwayDutyState(runway = TestIds.runway09, holder = ac, operation = RunwayOperation.ARRIVAL)
        else null

        val assessments = if (separationConcern) emptyList()
        else listOf(SeparationAssessment(
            aircraft = ac,
            other = other,
            currentSeparationNm = 5.0,
            requiredSeparationNm = 3.0,
            closureRateKt = 0.0,
            timeToMinimumSeconds = null,
            concern = SeparationConcern.Severity.COMFORTABLE,
        ))

        val coordinations = buildMap<AircraftId, List<OutstandingCoordination>> {
            if (pendingInstruction != null) {
                put(ac, listOf(OutstandingCoordination(
                    aircraft = ac,
                    instruction = pendingInstruction,
                    expectedReadback = emptySet(),
                    issuedAt = time,
                    state = CoordinationState.ISSUED,
                )))
            }
        }

        return BeliefState(
            runwayBeliefs = mapOf(TestIds.runway09 to rwyObs),
            runwayDuty = duty,
            separationAssessments = assessments,
            coordinations = coordinations,
            activeRunway = TestIds.runway09,
        )
    }

    private fun ctx(
        acObs: AircraftObservation,
        beliefs: BeliefState,
        weatherVfr: Boolean = true,
        events: List<ControllerEvent> = emptyList(),
    ): OperatorContext {
        val weather = if (weatherVfr) null else WeatherObservation(null, null, 1000)
        val view = towerView(
            aircraft = mapOf(ac to acObs),
            weather = weather,
            worldIndex = worldIndex,
        )
        return OperatorContext(view = view, beliefs = beliefs, events = events, world = world)
    }

    /** Evaluate every stage rule's guard independently (for overlap analysis). */
    private fun guardResults(
        stage: Stage,
        acObs: AircraftObservation,
        commitment: Commitment,
        ctx: OperatorContext,
    ): List<Pair<String, Boolean>> {
        val rules = spec.stageRules[stage]
            ?: error("Stage $stage has no rules in arrival spec")
        return rules.map { rule -> rule.id to rule.guard.evaluate(acObs, commitment, ctx) }
    }

    // ── AwaitDownwind: 4 booleans = 16 combinations ────────────────────
    //
    // Rules: ARR-DOWNWIND-ACK (InCircuit + PositionReported),
    //        ARR-ADVANCE-APPROACH (OnApproach),
    //        ARR-AI-ADVANCE (InCircuit + AiProactive).
    //
    // When inCircuit=true, aircraft is at the downwind point (CircuitProcedureRef entity).
    // When onApproach=true, ApproachRef is added to the entity set.
    // Both can be true simultaneously (aircraft on approach circuit leg).

    data class AwaitDownwindConditions(
        val inCircuit: Boolean,
        val onApproach: Boolean,
        val humanPiloted: Boolean,
        val positionReported: Boolean,
    )

    private fun execute(c: AwaitDownwindConditions): Pair<ExecutionOutcome, List<Pair<String, Boolean>>> {
        // Position priority: approach aircraft are never on a VFR circuit leg (mutually exclusive).
        // When onApproach=true we use a non-circuit point so InCircuit stays false; the inCircuit
        // flag is therefore only meaningful when onApproach=false.
        val point = when {
            c.onApproach -> TestIds.holdShort   // IFR approach: no CircuitProcedureRef
            c.inCircuit -> TestIds.downwind
            else -> TestIds.holdShort
        }
        val acObs = aircraft(point, humanPiloted = c.humanPiloted, onApproach = c.onApproach)
        val commit = commitment(TowerArrivalStage.AwaitDownwind)
        val events = if (c.positionReported)
            listOf(ControllerEvent.PositionReported(ac, ReportEvent.Downwind()))
        else emptyList()
        val ctx = ctx(acObs, beliefs(), events = events)
        val outcome = executeProcedure(spec, commit, acObs, ctx)
        val guards = guardResults(TowerArrivalStage.AwaitDownwind, acObs, commit, ctx)
        return outcome to guards
    }

    @Test
    fun `AwaitDownwind — no unintended guard overlap`() {
        var overlaps = 0
        for (inCircuit in bools) for (onApproach in bools)
        for (human in bools) for (reported in bools) {
            val c = AwaitDownwindConditions(inCircuit, onApproach, human, reported)
            val (_, guards) = execute(c)

            val passing = guards.filter { (_, passed) -> passed }
            if (passing.size > 1) {
                // ARR-DOWNWIND-ACK and ARR-AI-ADVANCE overlap when an AI aircraft reports
                // position: AiProactive=true AND PositionReported=true simultaneously.
                // ARR-DOWNWIND-ACK is listed first and fires; ARR-AI-ADVANCE is the fallback
                // path when no position report is received. This mirrors the DEP-LUAW /
                // DEP-LUAW-COND overlap pattern — documented, first-rule-wins ordering.
                val ids = passing.map { it.first }.toSet()
                if (ids == setOf("ARR-DOWNWIND-ACK", "ARR-AI-ADVANCE")) {
                    overlaps++
                } else {
                    throw AssertionError(
                        "AwaitDownwind $c: unexpected guard overlap: ${passing.map { it.first }}")
                }
            }
        }
        assertTrue(overlaps > 0,
            "Expected ARR-DOWNWIND-ACK / ARR-AI-ADVANCE overlap in AI aircraft + position-report combinations")
    }

    @Test
    fun `AwaitDownwind — critical conditions always produce action`() {
        // AI aircraft in circuit → AI-ADVANCE fires
        val ai = AwaitDownwindConditions(inCircuit = true, onApproach = false, humanPiloted = false, positionReported = false)
        val (aiOutcome, _) = execute(ai)
        assertNotNull(aiOutcome.result, "AI in circuit must produce ARR-AI-ADVANCE")
        assertEquals("ARR-AI-ADVANCE", aiOutcome.result!!.trace.ruleId)

        // Human aircraft reports position → ACK fires
        val ack = AwaitDownwindConditions(inCircuit = true, onApproach = false, humanPiloted = true, positionReported = true)
        val (ackOutcome, _) = execute(ack)
        assertNotNull(ackOutcome.result, "Human position report must produce ARR-DOWNWIND-ACK")
        assertEquals("ARR-DOWNWIND-ACK", ackOutcome.result!!.trace.ruleId)

        // Aircraft on approach (IFR) → ADVANCE fires
        val approach = AwaitDownwindConditions(inCircuit = false, onApproach = true, humanPiloted = true, positionReported = false)
        val (approachOutcome, _) = execute(approach)
        assertNotNull(approachOutcome.result, "Aircraft on approach must produce ARR-ADVANCE-APPROACH")
        assertEquals("ARR-ADVANCE-APPROACH", approachOutcome.result!!.trace.ruleId)
    }

    @Test
    fun `AwaitDownwind — full enumeration with action-failure checks`() {
        var total = 0
        val ruleDistribution = mutableMapOf<String?, Int>()
        for (inCircuit in bools) for (onApproach in bools)
        for (human in bools) for (reported in bools) {
            val c = AwaitDownwindConditions(inCircuit, onApproach, human, reported)
            val (outcome, _) = execute(c)
            ruleDistribution[outcome.result?.trace?.ruleId] =
                (ruleDistribution[outcome.result?.trace?.ruleId] ?: 0) + 1

            assertTrue(outcome.actionFailures.isEmpty(),
                "AwaitDownwind $c: unexpected action failures: ${outcome.actionFailures.map { it.ruleId + ": " + it.reason }}")
            total++
        }
        assertEquals(16, total)
        val stageRuleIds = spec.stageRules[TowerArrivalStage.AwaitDownwind]!!.map { it.id }.toSet()
        for (ruleId in stageRuleIds) {
            assertTrue(ruleId in ruleDistribution,
                "Rule $ruleId never fires across all 16 combinations — possible dead rule")
        }
    }

    // ── AwaitApproach: 7 booleans = 128 combinations ───────────────────
    //
    // Rules: ARR-GO-AROUND (SAFETY), ARR-EXTEND, ARR-TURN-BASE,
    //        ARR-REPORT-FINAL, ARR-LAND, ARR-LAND-TNG, ARR-CONTINUE.
    //
    // Position priority: onFinalLeg > onBaseLeg > onDownwindLeg > elsewhere.
    // weatherVfr is always true; NoPendingReadback constraints are never blocked
    // (no pending instructions in these cells). Both simplifications are safe
    // because:
    //   - IMC covers a blind-spot test separately.
    //   - Pending-readback blocking is a retransmit concern, not a first-fire concern.

    data class AwaitApproachConditions(
        val onFinalLeg: Boolean,
        val onBaseLeg: Boolean,
        val onDownwindLeg: Boolean,
        val runwayAccessGranted: Boolean,
        val runwayClear: Boolean,
        val separationConcern: Boolean,
        val touchAndGo: Boolean,
    )

    private fun execute(c: AwaitApproachConditions): Pair<ExecutionOutcome, List<Pair<String, Boolean>>> {
        val point = when {
            c.onFinalLeg -> TestIds.finalApproach
            c.onBaseLeg -> TestIds.base
            c.onDownwindLeg -> TestIds.downwind
            else -> TestIds.holdShort
        }
        val goal = if (c.touchAndGo) PilotGoal.TOUCH_AND_GO else PilotGoal.ARRIVE
        val acObs = aircraft(point, onGround = false, goal = goal)
        val commit = commitment(TowerArrivalStage.AwaitApproach)
        val beliefs = beliefs(
            runwayAccess = c.runwayAccessGranted,
            runwayClear = c.runwayClear,
            separationConcern = c.separationConcern,
        )
        val ctx = ctx(acObs, beliefs)
        val outcome = executeProcedure(spec, commit, acObs, ctx)
        val guards = guardResults(TowerArrivalStage.AwaitApproach, acObs, commit, ctx)
        return outcome to guards
    }

    @Test
    fun `AwaitApproach — SAFETY rules fire when guard passes`() {
        var tested = 0
        for (onFinal in bools) for (onBase in bools) for (onDownwind in bools)
        for (access in bools) for (clear in bools)
        for (concern in bools) for (tng in bools) {
            val c = AwaitApproachConditions(onFinal, onBase, onDownwind, access, clear, concern, tng)
            val (outcome, guards) = execute(c)

            val safetyPassing = guards.filter { (id, passed) -> passed && id in awaitApproachSafetyRules }
            if (safetyPassing.isNotEmpty()) {
                assertNotNull(outcome.result,
                    "AwaitApproach $c: SAFETY guard passes but no rule fired")
                assertEquals(Urgency.SAFETY, outcome.result!!.urgency,
                    "AwaitApproach $c: SAFETY guard passes but non-SAFETY rule ${outcome.result!!.trace.ruleId} fired")
            }
            tested++
        }
        assertEquals(128, tested)
    }

    @Test
    fun `AwaitApproach — no unintended guard overlap among non-SAFETY rules`() {
        for (onFinal in bools) for (onBase in bools) for (onDownwind in bools)
        for (access in bools) for (clear in bools)
        for (concern in bools) for (tng in bools) {
            val c = AwaitApproachConditions(onFinal, onBase, onDownwind, access, clear, concern, tng)
            val (_, guards) = execute(c)

            val nonSafetyPassing = guards.filter { (id, passed) -> passed && id !in awaitApproachSafetyRules }
            if (nonSafetyPassing.size > 1) {
                throw AssertionError(
                    "AwaitApproach $c: unexpected non-SAFETY guard overlap: ${nonSafetyPassing.map { it.first }}")
            }
        }
    }

    @Test
    fun `AwaitApproach — critical conditions always produce action`() {
        // Runway not clear while on final with access → SAFETY go-around
        for (onBase in bools) for (onDownwind in bools)
        for (concern in bools) for (tng in bools) {
            val c = AwaitApproachConditions(
                onFinalLeg = true, onBaseLeg = onBase, onDownwindLeg = onDownwind,
                runwayAccessGranted = true, runwayClear = false,
                separationConcern = concern, touchAndGo = tng,
            )
            val (outcome, _) = execute(c)
            assertNotNull(outcome.result,
                "AwaitApproach $c: on final + access + not clear but no SAFETY rule fired")
            assertEquals(Urgency.SAFETY, outcome.result!!.urgency,
                "AwaitApproach $c: go-around must be SAFETY urgency")
            assertEquals("ARR-GO-AROUND", outcome.result!!.trace.ruleId)
        }

        // Clear + access + on final + VMC → landing clearance
        val landPath = AwaitApproachConditions(
            onFinalLeg = true, onBaseLeg = false, onDownwindLeg = false,
            runwayAccessGranted = true, runwayClear = true,
            separationConcern = false, touchAndGo = false,
        )
        val (landOutcome, _) = execute(landPath)
        assertNotNull(landOutcome.result, "Happy path (to land) must produce ARR-LAND")
        assertEquals("ARR-LAND", landOutcome.result!!.trace.ruleId)

        // Clear + access + on final + T&G → T&G clearance
        val tngPath = landPath.copy(touchAndGo = true)
        val (tngOutcome, _) = execute(tngPath)
        assertNotNull(tngOutcome.result, "Happy path (touch-and-go) must produce ARR-LAND-TNG")
        assertEquals("ARR-LAND-TNG", tngOutcome.result!!.trace.ruleId)

        // On base → request final report
        val basePath = AwaitApproachConditions(
            onFinalLeg = false, onBaseLeg = true, onDownwindLeg = false,
            runwayAccessGranted = false, runwayClear = true,
            separationConcern = false, touchAndGo = false,
        )
        val (baseOutcome, _) = execute(basePath)
        assertNotNull(baseOutcome.result, "On base must produce ARR-REPORT-FINAL")
        assertEquals("ARR-REPORT-FINAL", baseOutcome.result!!.trace.ruleId)

        // On downwind with separation concern + no access → extend downwind
        val extendPath = AwaitApproachConditions(
            onFinalLeg = false, onBaseLeg = false, onDownwindLeg = true,
            runwayAccessGranted = false, runwayClear = true,
            separationConcern = true, touchAndGo = false,
        )
        val (extendOutcome, _) = execute(extendPath)
        assertNotNull(extendOutcome.result, "On downwind + concern + no access must produce ARR-EXTEND")
        assertEquals("ARR-EXTEND", extendOutcome.result!!.trace.ruleId)

        // On downwind with no concern + clear → turn base
        val turnBasePath = AwaitApproachConditions(
            onFinalLeg = false, onBaseLeg = false, onDownwindLeg = true,
            runwayAccessGranted = false, runwayClear = true,
            separationConcern = false, touchAndGo = false,
        )
        val (turnBaseOutcome, _) = execute(turnBasePath)
        assertNotNull(turnBaseOutcome.result, "On downwind + no concern + clear must produce ARR-TURN-BASE")
        assertEquals("ARR-TURN-BASE", turnBaseOutcome.result!!.trace.ruleId)
    }

    @Test
    fun `AwaitApproach — IMC on final always blocks landing clearance`() {
        // Even with all conditions met, IMC must prevent ARR-LAND and ARR-LAND-TNG.
        for (tng in bools) {
            val goal = if (tng) PilotGoal.TOUCH_AND_GO else PilotGoal.ARRIVE
            val acObs = aircraft(TestIds.finalApproach, onGround = false, goal = goal)
            val commit = commitment(TowerArrivalStage.AwaitApproach)
            val beliefs = beliefs(runwayAccess = true, runwayClear = true)
            val ctx = ctx(acObs, beliefs, weatherVfr = false)
            val outcome = executeProcedure(spec, commit, acObs, ctx)
            val ruleId = outcome.result?.trace?.ruleId
            assertTrue(ruleId != "ARR-LAND" && ruleId != "ARR-LAND-TNG",
                "IMC must block landing clearance: got $ruleId")
        }
    }

    @Test
    fun `AwaitApproach — full enumeration with action-failure checks`() {
        var total = 0
        val ruleDistribution = mutableMapOf<String?, Int>()
        for (onFinal in bools) for (onBase in bools) for (onDownwind in bools)
        for (access in bools) for (clear in bools)
        for (concern in bools) for (tng in bools) {
            val c = AwaitApproachConditions(onFinal, onBase, onDownwind, access, clear, concern, tng)
            val (outcome, _) = execute(c)
            ruleDistribution[outcome.result?.trace?.ruleId] =
                (ruleDistribution[outcome.result?.trace?.ruleId] ?: 0) + 1

            assertTrue(outcome.actionFailures.isEmpty(),
                "AwaitApproach $c: unexpected action failures: ${outcome.actionFailures.map { it.ruleId + ": " + it.reason }}")
            total++
        }
        assertEquals(128, total)
        val stageRuleIds = spec.stageRules[TowerArrivalStage.AwaitApproach]!!.map { it.id }.toSet()
        for (ruleId in stageRuleIds) {
            assertTrue(ruleId in ruleDistribution,
                "Rule $ruleId never fires across all 128 combinations — possible dead rule")
        }
    }

    // ── LandingClearanceIssued: 5 booleans = 32 combinations ───────────
    //
    // Rules: ARR-GO-AROUND-CLEARANCE-ISSUED (SAFETY), ARR-LAND-REISSUE, ARR-LAND-TNG-REISSUE.
    //
    // The clearance was already issued. The controller watches for the readback
    // or observes the aircraft on final. Go-around if runway not safe; re-issue if
    // the coordination timed out.

    data class LandingClearanceIssuedConditions(
        val onFinalLeg: Boolean,
        val runwayAccessGranted: Boolean,
        val runwayClear: Boolean,
        val touchAndGo: Boolean,
        val landingClearancePending: Boolean,  // existing ClearedToLand in coordinations
    )

    private fun execute(c: LandingClearanceIssuedConditions): Pair<ExecutionOutcome, List<Pair<String, Boolean>>> {
        val point = if (c.onFinalLeg) TestIds.finalApproach else TestIds.holdShort
        val goal = if (c.touchAndGo) PilotGoal.TOUCH_AND_GO else PilotGoal.ARRIVE
        val acObs = aircraft(point, onGround = false, goal = goal)
        val commit = commitment(TowerArrivalStage.LandingClearanceIssued)
        val pending = if (c.landingClearancePending)
            ClearedToLand(ac, TestIds.runway09) else null
        val beliefs = beliefs(
            runwayAccess = c.runwayAccessGranted,
            runwayClear = c.runwayClear,
            pendingInstruction = pending,
        )
        val ctx = ctx(acObs, beliefs)
        val outcome = executeProcedure(spec, commit, acObs, ctx)
        val guards = guardResults(TowerArrivalStage.LandingClearanceIssued, acObs, commit, ctx)
        return outcome to guards
    }

    @Test
    fun `LandingClearanceIssued — SAFETY rules fire when guard passes`() {
        var tested = 0
        for (onFinal in bools) for (access in bools)
        for (clear in bools) for (tng in bools)
        for (pending in bools) {
            val c = LandingClearanceIssuedConditions(onFinal, access, clear, tng, pending)
            val (outcome, guards) = execute(c)

            val safetyPassing = guards.filter { (id, passed) -> passed && id in landingIssuedSafetyRules }
            if (safetyPassing.isNotEmpty()) {
                assertNotNull(outcome.result,
                    "LandingClearanceIssued $c: SAFETY guard passes but no rule fired")
                assertEquals(Urgency.SAFETY, outcome.result!!.urgency,
                    "LandingClearanceIssued $c: SAFETY guard passes but non-SAFETY rule fired")
            }
            tested++
        }
        assertEquals(32, tested)
    }

    @Test
    fun `LandingClearanceIssued — critical conditions always produce action`() {
        // On final + runway not clear → SAFETY go-around
        for (access in bools) for (tng in bools) for (pending in bools) {
            val c = LandingClearanceIssuedConditions(
                onFinalLeg = true, runwayAccessGranted = access, runwayClear = false,
                touchAndGo = tng, landingClearancePending = pending,
            )
            val (outcome, _) = execute(c)
            assertNotNull(outcome.result,
                "LandingClearanceIssued $c: on final + not clear must produce SAFETY go-around")
            assertEquals(Urgency.SAFETY, outcome.result!!.urgency)
            assertEquals("ARR-GO-AROUND-CLEARANCE-ISSUED", outcome.result!!.trace.ruleId)
        }

        // Re-issue landing clearance: on final + all conditions met + no pending + to land
        val reissue = LandingClearanceIssuedConditions(
            onFinalLeg = true, runwayAccessGranted = true, runwayClear = true,
            touchAndGo = false, landingClearancePending = false,
        )
        val (reissueOutcome, _) = execute(reissue)
        assertNotNull(reissueOutcome.result, "Re-issue path (to land) must produce ARR-LAND-REISSUE")
        assertEquals("ARR-LAND-REISSUE", reissueOutcome.result!!.trace.ruleId)

        // Re-issue T&G clearance
        val reissueTng = reissue.copy(touchAndGo = true)
        val (reissueTngOutcome, _) = execute(reissueTng)
        assertNotNull(reissueTngOutcome.result, "Re-issue path (T&G) must produce ARR-LAND-TNG-REISSUE")
        assertEquals("ARR-LAND-TNG-REISSUE", reissueTngOutcome.result!!.trace.ruleId)
    }

    @Test
    fun `LandingClearanceIssued — full enumeration with action-failure checks`() {
        var total = 0
        val ruleDistribution = mutableMapOf<String?, Int>()
        for (onFinal in bools) for (access in bools)
        for (clear in bools) for (tng in bools)
        for (pending in bools) {
            val c = LandingClearanceIssuedConditions(onFinal, access, clear, tng, pending)
            val (outcome, _) = execute(c)
            ruleDistribution[outcome.result?.trace?.ruleId] =
                (ruleDistribution[outcome.result?.trace?.ruleId] ?: 0) + 1

            assertTrue(outcome.actionFailures.isEmpty(),
                "LandingClearanceIssued $c: unexpected action failures: ${outcome.actionFailures.map { it.ruleId + ": " + it.reason }}")
            total++
        }
        assertEquals(32, total)
        val stageRuleIds = spec.stageRules[TowerArrivalStage.LandingClearanceIssued]!!.map { it.id }.toSet()
        for (ruleId in stageRuleIds) {
            assertTrue(ruleId in ruleDistribution,
                "Rule $ruleId never fires across all 32 combinations — possible dead rule")
        }
    }

    // ── AwaitLandedObserved: 4 booleans = 16 combinations ──────────────
    //
    // Rules: ARR-TNG-AIRBORNE (T&G rollout → circuit re-form),
    //        ARR-VACATE (vacate instruction).
    //
    // ARR-TNG-AIRBORNE requires Airborne, ARR-VACATE requires OnGround —
    // these are always mutually exclusive.

    data class AwaitLandedObservedConditions(
        val touchAndGo: Boolean,
        val onRunway: Boolean,
        val onGround: Boolean,
        val pendingVacateReadback: Boolean,
    )

    private fun execute(c: AwaitLandedObservedConditions): Pair<ExecutionOutcome, List<Pair<String, Boolean>>> {
        val point = if (c.onRunway) TestIds.rwyMid else TestIds.apron
        val goal = if (c.touchAndGo) PilotGoal.TOUCH_AND_GO else PilotGoal.ARRIVE
        val acObs = aircraft(point, onGround = c.onGround, goal = goal)
        val commit = commitment(TowerArrivalStage.AwaitLandedObserved)
        val pending = if (c.pendingVacateReadback)
            AfterLandingVacateVia(ac, exit = TestIds.apron) else null
        val beliefs = beliefs(pendingInstruction = pending)
        val ctx = ctx(acObs, beliefs)
        val outcome = executeProcedure(spec, commit, acObs, ctx)
        val guards = guardResults(TowerArrivalStage.AwaitLandedObserved, acObs, commit, ctx)
        return outcome to guards
    }

    @Test
    fun `AwaitLandedObserved — no unintended guard overlap`() {
        for (tng in bools) for (onRunway in bools)
        for (onGround in bools) for (pending in bools) {
            val c = AwaitLandedObservedConditions(tng, onRunway, onGround, pending)
            val (_, guards) = execute(c)

            val passing = guards.filter { (_, passed) -> passed }
            assertTrue(passing.size <= 1,
                "AwaitLandedObserved $c: unexpected guard overlap: ${passing.map { it.first }}")
        }
    }

    @Test
    fun `AwaitLandedObserved — critical conditions always produce action`() {
        // T&G airborne → complete arrival
        val tngAirborne = AwaitLandedObservedConditions(
            touchAndGo = true, onRunway = false, onGround = false, pendingVacateReadback = false,
        )
        val (tngOutcome, _) = execute(tngAirborne)
        assertNotNull(tngOutcome.result, "T&G airborne must produce ARR-TNG-AIRBORNE")
        assertEquals("ARR-TNG-AIRBORNE", tngOutcome.result!!.trace.ruleId)

        // On runway + on ground + not T&G + no pending → vacate instruction
        val vacate = AwaitLandedObservedConditions(
            touchAndGo = false, onRunway = true, onGround = true, pendingVacateReadback = false,
        )
        val (vacateOutcome, _) = execute(vacate)
        assertNotNull(vacateOutcome.result, "On runway must produce ARR-VACATE")
        assertEquals("ARR-VACATE", vacateOutcome.result!!.trace.ruleId)
    }

    @Test
    fun `AwaitLandedObserved — full enumeration with action-failure checks`() {
        var total = 0
        val ruleDistribution = mutableMapOf<String?, Int>()
        for (tng in bools) for (onRunway in bools)
        for (onGround in bools) for (pending in bools) {
            val c = AwaitLandedObservedConditions(tng, onRunway, onGround, pending)
            val (outcome, _) = execute(c)
            ruleDistribution[outcome.result?.trace?.ruleId] =
                (ruleDistribution[outcome.result?.trace?.ruleId] ?: 0) + 1

            assertTrue(outcome.actionFailures.isEmpty(),
                "AwaitLandedObserved $c: unexpected action failures: ${outcome.actionFailures.map { it.ruleId + ": " + it.reason }}")
            total++
        }
        assertEquals(16, total)
        val stageRuleIds = spec.stageRules[TowerArrivalStage.AwaitLandedObserved]!!.map { it.id }.toSet()
        for (ruleId in stageRuleIds) {
            assertTrue(ruleId in ruleDistribution,
                "Rule $ruleId never fires across all 16 combinations — possible dead rule")
        }
    }

    // ── AwaitVacating: 4 booleans = 16 combinations ────────────────────
    //
    // Rules: ARR-VACATE-HANDOFF (hand off to ground after runway clear).
    // Single rule: no overlap possible. Coverage + action-failure checks suffice.

    data class AwaitVacatingConditions(
        val onRunway: Boolean,
        val onGround: Boolean,
        val touchAndGo: Boolean,
        val pendingHandoffReadback: Boolean,
    )

    private fun execute(c: AwaitVacatingConditions): Pair<ExecutionOutcome, List<Pair<String, Boolean>>> {
        val point = if (c.onRunway) TestIds.rwyMid else TestIds.apron
        val goal = if (c.touchAndGo) PilotGoal.TOUCH_AND_GO else PilotGoal.ARRIVE
        val acObs = aircraft(point, onGround = c.onGround, goal = goal)
        val commit = commitment(TowerArrivalStage.AwaitVacating)
        val pending = if (c.pendingHandoffReadback)
            ContactFrequency(ac, RoleName.GROUND, frequency = Frequency.unsafe("121.700")) else null
        val beliefs = beliefs(pendingInstruction = pending)
        val ctx = ctx(acObs, beliefs)
        val outcome = executeProcedure(spec, commit, acObs, ctx)
        val guards = guardResults(TowerArrivalStage.AwaitVacating, acObs, commit, ctx)
        return outcome to guards
    }

    @Test
    fun `AwaitVacating — critical conditions always produce action`() {
        // Off runway + on ground + not T&G + no pending → handoff
        val handoff = AwaitVacatingConditions(
            onRunway = false, onGround = true, touchAndGo = false, pendingHandoffReadback = false,
        )
        val (handoffOutcome, _) = execute(handoff)
        assertNotNull(handoffOutcome.result, "Off runway + on ground must produce ARR-VACATE-HANDOFF")
        assertEquals("ARR-VACATE-HANDOFF", handoffOutcome.result!!.trace.ruleId)

        // Pending handoff readback → retransmit blocked
        val blocked = handoff.copy(pendingHandoffReadback = true)
        val (blockedOutcome, _) = execute(blocked)
        assertTrue(blockedOutcome.result == null,
            "Pending handoff readback must block ARR-VACATE-HANDOFF retransmit")
    }

    @Test
    fun `AwaitVacating — full enumeration with action-failure checks`() {
        var total = 0
        val ruleDistribution = mutableMapOf<String?, Int>()
        for (onRunway in bools) for (onGround in bools)
        for (tng in bools) for (pending in bools) {
            val c = AwaitVacatingConditions(onRunway, onGround, tng, pending)
            val (outcome, _) = execute(c)
            ruleDistribution[outcome.result?.trace?.ruleId] =
                (ruleDistribution[outcome.result?.trace?.ruleId] ?: 0) + 1

            assertTrue(outcome.actionFailures.isEmpty(),
                "AwaitVacating $c: unexpected action failures: ${outcome.actionFailures.map { it.ruleId + ": " + it.reason }}")
            total++
        }
        assertEquals(16, total)
        val stageRuleIds = spec.stageRules[TowerArrivalStage.AwaitVacating]!!.map { it.id }.toSet()
        for (ruleId in stageRuleIds) {
            assertTrue(ruleId in ruleDistribution,
                "Rule $ruleId never fires across all 16 combinations — possible dead rule")
        }
    }

    // ── Go-around interrupts ────────────────────────────────────────────

    @Test
    fun `GA-PRE-CLEAR interrupt — go-around event during AwaitApproach reverts to AwaitDownwind`() {
        val acObs = aircraft(TestIds.finalApproach, onGround = false)
        val commit = commitment(TowerArrivalStage.AwaitApproach)
        val events = listOf(ControllerEvent.GoAroundDetected(ac))
        val ctx = ctx(acObs, beliefs(), events = events)

        val outcome = executeProcedure(spec, commit, acObs, ctx)
        assertNotNull(outcome.result, "Go-around event must trigger interrupt")
        assertEquals("GA-PRE-CLEAR", outcome.result!!.trace.ruleId,
            "Interrupt ID must be GA-PRE-CLEAR")
        assertEquals(TowerArrivalStage.AwaitDownwind, outcome.result!!.nextStage,
            "GA-PRE-CLEAR must revert to AwaitDownwind")
    }

    @Test
    fun `GA-POST-CLEAR interrupt — go-around event during LandingClearanceIssued reverts to AwaitDownwind`() {
        for (stage in listOf(TowerArrivalStage.LandingClearanceIssued, TowerArrivalStage.AwaitLandedObserved)) {
            val acObs = aircraft(TestIds.finalApproach, onGround = false)
            val commit = commitment(stage)
            val events = listOf(ControllerEvent.GoAroundDetected(ac))
            val ctx = ctx(acObs, beliefs(), events = events)

            val outcome = executeProcedure(spec, commit, acObs, ctx)
            assertNotNull(outcome.result, "Go-around event from $stage must trigger interrupt")
            assertEquals("GA-POST-CLEAR", outcome.result!!.trace.ruleId,
                "Interrupt ID from $stage must be GA-POST-CLEAR")
            assertEquals(TowerArrivalStage.AwaitDownwind, outcome.result!!.nextStage,
                "GA-POST-CLEAR from $stage must revert to AwaitDownwind")
        }
    }

    // ── Cross-stage summary ─────────────────────────────────────────────

    @Test
    fun `all arrival stages — total condition space enumerated`() {
        val testedStages = setOf(
            TowerArrivalStage.AwaitDownwind,
            TowerArrivalStage.AwaitApproach,
            TowerArrivalStage.LandingClearanceIssued,
            TowerArrivalStage.AwaitLandedObserved,
            TowerArrivalStage.AwaitVacating,
        )
        val specStages = spec.stageRules.keys.filterIsInstance<TowerArrivalStage>()
            .filter { !it.isComplete }
            .toSet()
        assertEquals(specStages, testedStages,
            "Every non-Complete arrival stage with rules must have condition-space tests")
    }
}
