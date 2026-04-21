package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.assess.RunwayDutyState
import xyz.easiersaid.twr.controller.assess.RunwayOperation
import xyz.easiersaid.twr.controller.bdi.*
import xyz.easiersaid.twr.controller.observe.*
import xyz.easiersaid.twr.controller.procedure.*
import xyz.easiersaid.twr.protocol.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Exhaustive condition-space permutation tests for departure procedure rules.
 *
 * For each departure stage, enumerates every boolean combination of guard-relevant
 * conditions, calls [executeProcedure], and verifies structural properties:
 *
 * 1. **SAFETY priority**: when a SAFETY rule's guard conditions are met, a SAFETY-urgency
 *    rule fires — regardless of other flags.
 * 2. **No ambiguous overlap**: documents which rule wins for each combination.
 * 3. **No blind spots**: critical conditions always produce controller action.
 * 4. **No silent failures**: action resolution never fails in any cell.
 *
 * Total combinations across all departure stages: ~832.
 */
class DepartureConditionSpaceTest {

    private val worldIndex = testWorldIndex()
    private val world = testWorld()
    private val spec = towerDepartureProcedure()
    private val ac = TestIds.acAlpha
    private val other = TestIds.acBravo
    private val time = SimTime.ofSeconds(0)

    private val bools = listOf(false, true)

    // ── SAFETY rule IDs per stage ───────────────────────────────────────

    private val awaitReadySafetyRules = setOf("DEP-RUNWAY-INCURSION")
    private val awaitLineUpSafetyRules = setOf("DEP-HOLD-INCURSION")
    private val takeoffIssuedSafetyRules = setOf("DEP-HOLD-TAKEOFF-UNCONFIRMED")
    private val awaitTakeoffSafetyRules = setOf("DEP-CANCEL-TAKEOFF")

    // ── Shared helpers ──────────────────────────────────────────────────

    private fun commitment(
        stage: Stage,
        contacted: Boolean = true,
        lastTransition: TransitionKind? = null,
    ) = Commitment(
        aircraft = ac,
        kind = CommitmentKind.TOWER_DEPARTURE,
        stage = stage,
        runway = TestIds.runway09,
        formedAt = time,
        contacted = contacted,
        lastTransition = lastTransition,
    )

    private fun aircraft(
        point: PointId,
        onGround: Boolean = true,
        humanPiloted: Boolean = false,
    ) = aircraftAt(ac, point, worldIndex, onGround = onGround, humanPiloted = humanPiloted)

    private fun beliefs(
        runwayAccess: Boolean = false,
        runwayClear: Boolean = true,
        trafficOnFinal: Boolean = false,
        runwayClearanceActive: Boolean = false,
        holdPositionActive: Boolean = false,
        pendingInstruction: AtcInstruction? = null,
    ): BeliefState {
        val rwyObs = if (runwayClear)
            RunwayObservation(TestIds.runway09, RunwayStatus.CLEAR, emptySet())
        else
            RunwayObservation(TestIds.runway09, RunwayStatus.OCCUPIED_LANDING, setOf(other))

        val duty = if (runwayAccess)
            RunwayDutyState(runway = TestIds.runway09, holder = ac, operation = RunwayOperation.DEPARTURE)
        else null

        val clearances = buildMap {
            if (runwayClearanceActive) {
                put(ClearanceId("clr-rwy"), ClearanceSummary(
                    id = ClearanceId("clr-rwy"),
                    aircraft = ac,
                    domain = ClearanceDomain.RUNWAY,
                    status = ClearanceStatus.ACTIVE,
                    instruction = LineUpAndWait(ac, TestIds.runway09),
                    issuedAt = time,
                ))
            }
            if (holdPositionActive) {
                put(ClearanceId("clr-hold"), ClearanceSummary(
                    id = ClearanceId("clr-hold"),
                    aircraft = ac,
                    domain = ClearanceDomain.RUNWAY,
                    status = ClearanceStatus.ACTIVE,
                    instruction = HoldPosition(ac),
                    issuedAt = time,
                ))
            }
        }

        val commitments = buildMap<AircraftId, Commitment> {
            if (trafficOnFinal) {
                put(other, Commitment(
                    aircraft = other,
                    kind = CommitmentKind.TOWER_ARRIVAL,
                    stage = TowerArrivalStage.AwaitApproach,
                    runway = TestIds.runway09,
                    formedAt = time,
                ))
            }
        }

        val tracked = buildMap<AircraftId, AircraftObservation> {
            if (trafficOnFinal) {
                put(other, aircraftAt(
                    other, TestIds.finalApproach, worldIndex,
                    onGround = false, goal = PilotGoal.ARRIVE,
                ))
            }
        }

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
            issuedClearances = clearances,
            commitments = commitments,
            trackedAircraft = tracked,
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

    /** Evaluate every rule's guard independently (for overlap analysis). */
    private fun guardResults(
        stage: Stage,
        acObs: AircraftObservation,
        commitment: Commitment,
        ctx: OperatorContext,
    ): List<Pair<String, Boolean>> {
        val rules = spec.stageRules[stage]
            ?: error("Stage $stage has no rules in departure spec")
        return rules.map { rule ->
            rule.id to rule.guard.evaluate(acObs, commitment, ctx)
        }
    }

    // ── AwaitReady: 9 booleans = 512 combinations ──────────────────────
    //
    // DepartureTrigger = AnyOf(PilotReady, AiProactive). Split into two
    // dimensions so both disjuncts are independently exercised:
    //   - humanPiloted=false → AiProactive fires
    //   - humanPiloted=true + pilotReadyEvent=true → PilotReady fires
    //   - humanPiloted=true + pilotReadyEvent=false → neither fires

    data class AwaitReadyConditions(
        val onRunway: Boolean,
        val runwayClearanceActive: Boolean,
        val humanPiloted: Boolean,
        val pilotReadyEvent: Boolean,
        val contacted: Boolean,
        val weatherVfr: Boolean,
        val runwayAccess: Boolean,
        val runwayClear: Boolean,
        val trafficOnShortFinal: Boolean,
    )

    private fun execute(c: AwaitReadyConditions): Pair<ExecutionOutcome, List<Pair<String, Boolean>>> {
        val point = if (c.onRunway) TestIds.rwyMid else TestIds.holdShort
        val acObs = aircraft(point, humanPiloted = c.humanPiloted)
        val commit = commitment(TowerDepartureStage.AwaitReady, contacted = c.contacted)
        val events = if (c.pilotReadyEvent)
            listOf(ControllerEvent.ReadyForDepartureReceived(ac))
        else emptyList()
        val beliefs = beliefs(
            runwayAccess = c.runwayAccess,
            runwayClear = c.runwayClear,
            trafficOnFinal = c.trafficOnShortFinal,
            runwayClearanceActive = c.runwayClearanceActive,
        )
        val ctx = ctx(acObs, beliefs, weatherVfr = c.weatherVfr, events = events)
        val outcome = executeProcedure(spec, commit, acObs, ctx)
        val guards = guardResults(TowerDepartureStage.AwaitReady, acObs, commit, ctx)
        return outcome to guards
    }

    @Test
    fun `AwaitReady — SAFETY rules fire when guard passes`() {
        var tested = 0
        for (onRunway in bools) for (rwyClearance in bools)
        for (human in bools) for (readyEvent in bools)
        for (contacted in bools) for (weather in bools)
        for (access in bools) for (clear in bools)
        for (traffic in bools) {
            val c = AwaitReadyConditions(onRunway, rwyClearance, human, readyEvent, contacted, weather, access, clear, traffic)
            val (outcome, guards) = execute(c)

            val safetyGuardsPassing = guards.filter { (id, passed) -> passed && id in awaitReadySafetyRules }
            if (safetyGuardsPassing.isNotEmpty()) {
                assertNotNull(outcome.result,
                    "AwaitReady $c: SAFETY guard(s) pass ${safetyGuardsPassing.map { it.first }} but no rule fired")
                assertEquals(Urgency.SAFETY, outcome.result!!.urgency,
                    "AwaitReady $c: SAFETY guard passes but non-SAFETY rule ${outcome.result!!.trace.ruleId} fired")
            }
            tested++
        }
        assertEquals(512, tested, "Must test all 2^9 combinations")
    }

    @Test
    fun `AwaitReady — no unintended guard overlap among non-SAFETY rules`() {
        var overlaps = 0
        for (onRunway in bools) for (rwyClearance in bools)
        for (human in bools) for (readyEvent in bools)
        for (contacted in bools) for (weather in bools)
        for (access in bools) for (clear in bools)
        for (traffic in bools) {
            val c = AwaitReadyConditions(onRunway, rwyClearance, human, readyEvent, contacted, weather, access, clear, traffic)
            val (_, guards) = execute(c)

            val nonSafetyPassing = guards.filter { (id, passed) -> passed && id !in awaitReadySafetyRules }
            if (nonSafetyPassing.size > 1) {
                // DEP-LUAW and DEP-LUAW-COND can overlap — LUAW is first in order, wins.
                // This is by design: LUAW fires when runway is clear, LUAW-COND when occupied.
                // Both share most guards; when both pass, LUAW correctly takes priority.
                val ids = nonSafetyPassing.map { it.first }.toSet()
                if (ids == setOf("DEP-LUAW", "DEP-LUAW-COND")) {
                    overlaps++
                } else {
                    throw AssertionError(
                        "AwaitReady $c: unexpected non-SAFETY guard overlap: $ids")
                }
            }
        }
        assertTrue(overlaps > 0,
            "Expected DEP-LUAW / DEP-LUAW-COND overlap in some combinations")
    }

    @Test
    fun `AwaitReady — critical conditions always produce action`() {
        // Runway incursion: aircraft on runway without clearance → must act
        for (human in bools) for (readyEvent in bools)
        for (contacted in bools) for (weather in bools)
        for (access in bools) for (clear in bools)
        for (traffic in bools) {
            val c = AwaitReadyConditions(
                onRunway = true, runwayClearanceActive = false,
                humanPiloted = human, pilotReadyEvent = readyEvent,
                contacted = contacted, weatherVfr = weather,
                runwayAccess = access, runwayClear = clear,
                trafficOnShortFinal = traffic,
            )
            val (outcome, _) = execute(c)
            assertNotNull(outcome.result,
                "AwaitReady $c: aircraft on runway without clearance but no rule fired")
            assertEquals(Urgency.SAFETY, outcome.result!!.urgency,
                "AwaitReady $c: incursion must be SAFETY urgency")
        }

        // Happy path via AiProactive: humanPiloted=false
        val aiPath = AwaitReadyConditions(
            onRunway = false, runwayClearanceActive = false,
            humanPiloted = false, pilotReadyEvent = false,
            contacted = true, weatherVfr = true,
            runwayAccess = true, runwayClear = true,
            trafficOnShortFinal = false,
        )
        val (aiOutcome, _) = execute(aiPath)
        assertNotNull(aiOutcome.result, "AI happy path must produce a rule")
        assertEquals("DEP-LUAW", aiOutcome.result!!.trace.ruleId)

        // Happy path via PilotReady: humanPiloted=true + readyEvent=true
        val pilotPath = AwaitReadyConditions(
            onRunway = false, runwayClearanceActive = false,
            humanPiloted = true, pilotReadyEvent = true,
            contacted = true, weatherVfr = true,
            runwayAccess = true, runwayClear = true,
            trafficOnShortFinal = false,
        )
        val (pilotOutcome, _) = execute(pilotPath)
        assertNotNull(pilotOutcome.result, "Pilot-ready happy path must produce a rule")
        assertEquals("DEP-LUAW", pilotOutcome.result!!.trace.ruleId)
    }

    @Test
    fun `AwaitReady — full enumeration with action-failure checks`() {
        var total = 0
        val ruleDistribution = mutableMapOf<String?, Int>()
        for (onRunway in bools) for (rwyClearance in bools)
        for (human in bools) for (readyEvent in bools)
        for (contacted in bools) for (weather in bools)
        for (access in bools) for (clear in bools)
        for (traffic in bools) {
            val c = AwaitReadyConditions(onRunway, rwyClearance, human, readyEvent, contacted, weather, access, clear, traffic)
            val (outcome, _) = execute(c)
            val ruleId = outcome.result?.trace?.ruleId
            ruleDistribution[ruleId] = (ruleDistribution[ruleId] ?: 0) + 1

            assertTrue(outcome.actionFailures.isEmpty(),
                "AwaitReady $c: unexpected action failures: ${outcome.actionFailures.map { it.ruleId + ": " + it.reason }}")
            total++
        }
        assertEquals(512, total)
        val stageRuleIds = spec.stageRules[TowerDepartureStage.AwaitReady]!!.map { it.id }.toSet()
        for (ruleId in stageRuleIds) {
            assertTrue(ruleId in ruleDistribution,
                "Rule $ruleId never fires across all 512 combinations — possible dead rule")
        }
    }

    // ── AwaitLineUpObserved: 7 booleans = 128 combinations ─────────────

    data class AwaitLineUpConditions(
        val onRunway: Boolean,
        val onGround: Boolean,
        val anomalousTransition: Boolean,
        val weatherVfr: Boolean,
        val runwayAccess: Boolean,
        val runwayClear: Boolean,
        val holdPositionActive: Boolean,
    )

    private fun execute(c: AwaitLineUpConditions): Pair<ExecutionOutcome, List<Pair<String, Boolean>>> {
        val point = if (c.onRunway) TestIds.rwyMid else TestIds.holdShort
        val acObs = aircraft(point, onGround = c.onGround)
        val transition = if (c.anomalousTransition) TransitionKind.ANOMALOUS else null
        val commit = commitment(TowerDepartureStage.AwaitLineUpObserved, lastTransition = transition)
        val beliefs = beliefs(
            runwayAccess = c.runwayAccess,
            runwayClear = c.runwayClear,
            holdPositionActive = c.holdPositionActive,
        )
        val ctx = ctx(acObs, beliefs, weatherVfr = c.weatherVfr)
        val outcome = executeProcedure(spec, commit, acObs, ctx)
        val guards = guardResults(TowerDepartureStage.AwaitLineUpObserved, acObs, commit, ctx)
        return outcome to guards
    }

    @Test
    fun `AwaitLineUpObserved — SAFETY rules fire when guard passes`() {
        var tested = 0
        for (onRunway in bools) for (onGround in bools)
        for (anomalous in bools) for (weather in bools)
        for (access in bools) for (clear in bools)
        for (holdActive in bools) {
            val c = AwaitLineUpConditions(onRunway, onGround, anomalous, weather, access, clear, holdActive)
            val (outcome, guards) = execute(c)

            val safetyPassing = guards.filter { (id, passed) -> passed && id in awaitLineUpSafetyRules }
            if (safetyPassing.isNotEmpty()) {
                assertNotNull(outcome.result,
                    "AwaitLineUpObserved $c: SAFETY guard passes but no rule fired")
                assertEquals(Urgency.SAFETY, outcome.result!!.urgency,
                    "AwaitLineUpObserved $c: SAFETY guard passes but non-SAFETY rule ${outcome.result!!.trace.ruleId} fired")
            }
            tested++
        }
        assertEquals(128, tested)
    }

    @Test
    fun `AwaitLineUpObserved — critical conditions always produce action`() {
        // Anomalous on runway → SAFETY hold
        for (weather in bools) for (access in bools)
        for (clear in bools) for (holdActive in bools) {
            val c = AwaitLineUpConditions(
                onRunway = true, onGround = true, anomalousTransition = true,
                weatherVfr = weather, runwayAccess = access, runwayClear = clear,
                holdPositionActive = holdActive,
            )
            val (outcome, _) = execute(c)
            assertNotNull(outcome.result,
                "AwaitLineUpObserved $c: anomalous incursion on runway but no rule fired")
            assertEquals("DEP-HOLD-INCURSION", outcome.result!!.trace.ruleId)
        }

        // S2: IMC while lined up on runway → hold (SERA.5005)
        for (anomalous in bools) for (access in bools)
        for (clear in bools) for (holdActive in bools) {
            val c = AwaitLineUpConditions(
                onRunway = true, onGround = true, anomalousTransition = anomalous,
                weatherVfr = false, runwayAccess = access, runwayClear = clear,
                holdPositionActive = holdActive,
            )
            val (outcome, _) = execute(c)
            assertNotNull(outcome.result,
                "AwaitLineUpObserved $c: IMC on runway but no rule fired")
            // When anomalous, DEP-HOLD-INCURSION fires first (SAFETY); otherwise DEP-HOLD-LINEUP-IMC
            val expectedRule = if (anomalous) "DEP-HOLD-INCURSION" else "DEP-HOLD-LINEUP-IMC"
            assertEquals(expectedRule, outcome.result!!.trace.ruleId,
                "AwaitLineUpObserved $c: IMC on runway should fire $expectedRule")
        }

        // On runway + VMC + access + clear → takeoff clearance
        val takeoff = AwaitLineUpConditions(
            onRunway = true, onGround = true, anomalousTransition = false,
            weatherVfr = true, runwayAccess = true, runwayClear = true,
            holdPositionActive = false,
        )
        val (outcome, _) = execute(takeoff)
        assertNotNull(outcome.result)
        assertEquals("DEP-TAKEOFF", outcome.result!!.trace.ruleId)
    }

    @Test
    fun `AwaitLineUpObserved — full enumeration with action-failure checks`() {
        var total = 0
        val ruleDistribution = mutableMapOf<String?, Int>()
        for (onRunway in bools) for (onGround in bools)
        for (anomalous in bools) for (weather in bools)
        for (access in bools) for (clear in bools)
        for (holdActive in bools) {
            val c = AwaitLineUpConditions(onRunway, onGround, anomalous, weather, access, clear, holdActive)
            val (outcome, guards) = execute(c)
            ruleDistribution[outcome.result?.trace?.ruleId] =
                (ruleDistribution[outcome.result?.trace?.ruleId] ?: 0) + 1

            assertTrue(outcome.actionFailures.isEmpty(),
                "AwaitLineUpObserved $c: unexpected action failures: ${outcome.actionFailures.map { it.ruleId + ": " + it.reason }}")
            total++
        }
        assertEquals(128, total)
        val stageRuleIds = spec.stageRules[TowerDepartureStage.AwaitLineUpObserved]!!.map { it.id }.toSet()
        for (ruleId in stageRuleIds) {
            assertTrue(ruleId in ruleDistribution,
                "Rule $ruleId never fires across all 128 combinations — possible dead rule")
        }
    }

    // ── TakeoffClearanceIssued: 6 booleans = 64 combinations ───────────

    data class TakeoffIssuedConditions(
        val onRunway: Boolean,
        val onGround: Boolean,
        val weatherVfr: Boolean,
        val runwayAccess: Boolean,
        val runwayClear: Boolean,
        val takeoffReadbackPending: Boolean,
    )

    private fun execute(c: TakeoffIssuedConditions): Pair<ExecutionOutcome, List<Pair<String, Boolean>>> {
        val point = if (c.onRunway) TestIds.rwyMid else TestIds.holdShort
        val acObs = aircraft(point, onGround = c.onGround)
        val commit = commitment(TowerDepartureStage.TakeoffClearanceIssued)
        val pending = if (c.takeoffReadbackPending)
            ClearedForTakeoff(ac, TestIds.runway09) else null
        val beliefs = beliefs(
            runwayAccess = c.runwayAccess,
            runwayClear = c.runwayClear,
            pendingInstruction = pending,
        )
        val ctx = ctx(acObs, beliefs, weatherVfr = c.weatherVfr)
        val outcome = executeProcedure(spec, commit, acObs, ctx)
        val guards = guardResults(TowerDepartureStage.TakeoffClearanceIssued, acObs, commit, ctx)
        return outcome to guards
    }

    @Test
    fun `TakeoffClearanceIssued — SAFETY rules fire when guard passes`() {
        var tested = 0
        for (onRunway in bools) for (onGround in bools)
        for (weather in bools) for (access in bools)
        for (clear in bools) for (pending in bools) {
            val c = TakeoffIssuedConditions(onRunway, onGround, weather, access, clear, pending)
            val (outcome, guards) = execute(c)

            val safetyPassing = guards.filter { (id, passed) -> passed && id in takeoffIssuedSafetyRules }
            if (safetyPassing.isNotEmpty()) {
                assertNotNull(outcome.result,
                    "TakeoffClearanceIssued $c: SAFETY guard passes but no rule fired")
                assertEquals(Urgency.SAFETY, outcome.result!!.urgency,
                    "TakeoffClearanceIssued $c: SAFETY guard passes but non-SAFETY rule fired")
            }
            tested++
        }
        assertEquals(64, tested)
    }

    @Test
    fun `TakeoffClearanceIssued — critical conditions always produce action`() {
        // On runway, on ground, runway not clear → cancel/hold (SAFETY)
        for (weather in bools) for (access in bools) for (pending in bools) {
            val c = TakeoffIssuedConditions(
                onRunway = true, onGround = true, weatherVfr = weather,
                runwayAccess = access, runwayClear = false, takeoffReadbackPending = pending,
            )
            val (outcome, _) = execute(c)
            assertNotNull(outcome.result,
                "TakeoffClearanceIssued $c: runway not clear but no SAFETY rule fired")
            assertEquals(Urgency.SAFETY, outcome.result!!.urgency)
            assertEquals("DEP-HOLD-TAKEOFF-UNCONFIRMED", outcome.result!!.trace.ruleId)
        }

        // Re-issue path: all conditions met + no pending readback
        val reissue = TakeoffIssuedConditions(
            onRunway = true, onGround = true, weatherVfr = true,
            runwayAccess = true, runwayClear = true, takeoffReadbackPending = false,
        )
        val (outcome, _) = execute(reissue)
        assertNotNull(outcome.result)
        assertEquals("DEP-TAKEOFF-REISSUE", outcome.result!!.trace.ruleId)
    }

    @Test
    fun `TakeoffClearanceIssued — full enumeration with action-failure checks`() {
        var total = 0
        val ruleDistribution = mutableMapOf<String?, Int>()
        for (onRunway in bools) for (onGround in bools)
        for (weather in bools) for (access in bools)
        for (clear in bools) for (pending in bools) {
            val c = TakeoffIssuedConditions(onRunway, onGround, weather, access, clear, pending)
            val (outcome, guards) = execute(c)
            ruleDistribution[outcome.result?.trace?.ruleId] =
                (ruleDistribution[outcome.result?.trace?.ruleId] ?: 0) + 1

            assertTrue(outcome.actionFailures.isEmpty(),
                "TakeoffClearanceIssued $c: unexpected action failures: ${outcome.actionFailures.map { it.ruleId + ": " + it.reason }}")
            total++
        }
        assertEquals(64, total)
        val stageRuleIds = spec.stageRules[TowerDepartureStage.TakeoffClearanceIssued]!!.map { it.id }.toSet()
        for (ruleId in stageRuleIds) {
            assertTrue(ruleId in ruleDistribution,
                "Rule $ruleId never fires across all 64 combinations — possible dead rule")
        }
    }

    // ── AwaitTakeoffObserved: 5 booleans = 32 combinations ─────────────
    //
    // Position selects between runway (rwyMid) and climbout (upwind).
    // When onRunway=true, onClimboutLeg is irrelevant — an aircraft cannot
    // physically occupy both a runway point and a circuit point simultaneously.
    // The onRunway flag takes priority in the when-branch below.

    // 7 booleans = 128 combinations. Includes circuit-specific dimensions
    // (touchAndGo, onDownwind) so DEP-CIRCUIT-COMPLETE is covered naturally.
    //
    // Position priority: onRunway > onDownwind > onClimboutLeg > holdShort.
    // An aircraft cannot physically be at two positions simultaneously.

    data class AwaitTakeoffConditions(
        val onRunway: Boolean,
        val onGround: Boolean,
        val runwayClear: Boolean,
        val onClimboutLeg: Boolean,
        val onDownwind: Boolean,
        val touchAndGo: Boolean,
        val handoffReadbackPending: Boolean,
    )

    private fun execute(c: AwaitTakeoffConditions): Pair<ExecutionOutcome, List<Pair<String, Boolean>>> {
        val point = when {
            c.onRunway -> TestIds.rwyMid
            c.onDownwind -> TestIds.downwind
            c.onClimboutLeg -> TestIds.upwind
            else -> TestIds.holdShort
        }
        val goal = if (c.touchAndGo) PilotGoal.TOUCH_AND_GO else PilotGoal.DEPART
        val acObs = aircraftAt(ac, point, worldIndex, onGround = c.onGround, goal = goal)
        val commit = commitment(TowerDepartureStage.AwaitTakeoffObserved)
        val pending = if (c.handoffReadbackPending)
            ContactFrequency(ac, RoleName.APPROACH, frequency = Frequency.unsafe("120.500")) else null
        val beliefs = beliefs(
            runwayClear = c.runwayClear,
            pendingInstruction = pending,
        )
        val ctx = ctx(acObs, beliefs)
        val outcome = executeProcedure(spec, commit, acObs, ctx)
        val guards = guardResults(TowerDepartureStage.AwaitTakeoffObserved, acObs, commit, ctx)
        return outcome to guards
    }

    @Test
    fun `AwaitTakeoffObserved — SAFETY rules fire when guard passes`() {
        var tested = 0
        for (onRunway in bools) for (onGround in bools)
        for (clear in bools) for (climbout in bools)
        for (downwind in bools) for (tng in bools)
        for (pending in bools) {
            val c = AwaitTakeoffConditions(onRunway, onGround, clear, climbout, downwind, tng, pending)
            val (outcome, guards) = execute(c)

            val safetyPassing = guards.filter { (id, passed) -> passed && id in awaitTakeoffSafetyRules }
            if (safetyPassing.isNotEmpty()) {
                assertNotNull(outcome.result,
                    "AwaitTakeoffObserved $c: SAFETY guard passes but no rule fired")
                assertEquals(Urgency.SAFETY, outcome.result!!.urgency,
                    "AwaitTakeoffObserved $c: SAFETY guard passes but non-SAFETY rule fired")
            }
            tested++
        }
        assertEquals(128, tested)
    }

    @Test
    fun `AwaitTakeoffObserved — critical conditions always produce action`() {
        // On runway + on ground + not clear → cancel takeoff
        for (climbout in bools) for (downwind in bools)
        for (tng in bools) for (pending in bools) {
            val c = AwaitTakeoffConditions(
                onRunway = true, onGround = true, runwayClear = false,
                onClimboutLeg = climbout, onDownwind = downwind,
                touchAndGo = tng, handoffReadbackPending = pending,
            )
            val (outcome, _) = execute(c)
            assertNotNull(outcome.result,
                "AwaitTakeoffObserved $c: runway occupied but no SAFETY rule fired")
            assertEquals("DEP-CANCEL-TAKEOFF", outcome.result!!.trace.ruleId)
        }

        // Airborne on climbout + no pending + not T&G → handoff
        val handoff = AwaitTakeoffConditions(
            onRunway = false, onGround = false, runwayClear = true,
            onClimboutLeg = true, onDownwind = false,
            touchAndGo = false, handoffReadbackPending = false,
        )
        val (handoffOutcome, _) = execute(handoff)
        assertNotNull(handoffOutcome.result)
        assertEquals("DEP-HANDOFF", handoffOutcome.result!!.trace.ruleId)

        // Airborne on downwind + T&G → circuit complete
        val circuitComplete = AwaitTakeoffConditions(
            onRunway = false, onGround = false, runwayClear = true,
            onClimboutLeg = false, onDownwind = true,
            touchAndGo = true, handoffReadbackPending = false,
        )
        val (circuitOutcome, _) = execute(circuitComplete)
        assertNotNull(circuitOutcome.result)
        assertEquals("DEP-CIRCUIT-COMPLETE", circuitOutcome.result!!.trace.ruleId)
    }

    @Test
    fun `AwaitTakeoffObserved — full enumeration with action-failure checks`() {
        var total = 0
        val ruleDistribution = mutableMapOf<String?, Int>()
        for (onRunway in bools) for (onGround in bools)
        for (clear in bools) for (climbout in bools)
        for (downwind in bools) for (tng in bools)
        for (pending in bools) {
            val c = AwaitTakeoffConditions(onRunway, onGround, clear, climbout, downwind, tng, pending)
            val (outcome, _) = execute(c)
            ruleDistribution[outcome.result?.trace?.ruleId] =
                (ruleDistribution[outcome.result?.trace?.ruleId] ?: 0) + 1

            assertTrue(outcome.actionFailures.isEmpty(),
                "AwaitTakeoffObserved $c: unexpected action failures: ${outcome.actionFailures.map { it.ruleId + ": " + it.reason }}")
            total++
        }
        assertEquals(128, total)
        val stageRuleIds = spec.stageRules[TowerDepartureStage.AwaitTakeoffObserved]!!.map { it.id }.toSet()
        for (ruleId in stageRuleIds) {
            assertTrue(ruleId in ruleDistribution,
                "Rule $ruleId never fires across all 128 combinations — possible dead rule")
        }
    }

    // ── Cross-stage summary ─────────────────────────────────────────────

    @Test
    fun `all departure stages — total condition space enumerated`() {
        val testedStages = setOf(
            TowerDepartureStage.AwaitReady,
            TowerDepartureStage.AwaitLineUpObserved,
            TowerDepartureStage.TakeoffClearanceIssued,
            TowerDepartureStage.AwaitTakeoffObserved,
        )
        val specStages = spec.stageRules.keys.filterIsInstance<TowerDepartureStage>()
            .filter { !it.isComplete }
            .toSet()
        assertEquals(specStages, testedStages,
            "Every non-Complete departure stage with rules must have condition-space tests")
    }
}
