package xyz.easiersaid.twr.controller

import xyz.easiersaid.twr.controller.bdi.*
import xyz.easiersaid.twr.controller.procedure.*
import xyz.easiersaid.twr.protocol.Urgency
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * DFA-as-Specification conformance tests.
 *
 * Verifies that the reconciliation functions (specification — "what stage should the
 * commitment be in?") and the ProcedureSpec rules (implementation — "what action to
 * take at each stage?") describe the same state machine.
 *
 * Six properties across four procedure/reconciliation pairs:
 * 1. Stage coverage: every reconciliation output stage has rules
 * 2. Reachability: every spec stage is reachable from the initial stage
 * 3. Transition coherence: every rule/interrupt target is a known stage
 * 4. Rule targets in enumeration: rule targets exist in the stage hierarchy
 * 5. ANOMALOUS → SAFETY: anomalous transitions reach stages with SAFETY rules
 * 6. Regression symmetry: go-around regressions agree between reconciliation and interrupts
 */
class DfaConformanceTest {

    // ── Fixture ─────────────────────────────────────────────────────────

    private class Fixture<S : Stage, P>(
        val name: String,
        val allStages: List<S>,
        val allPositions: List<P>,
        val initialStage: S,
        val reconcile: (S, P) -> ReconciledStage<S>,
        val stageRules: Map<S, List<AtcRule>>,
        val interrupts: List<ProcedureInterrupt> = emptyList(),
    ) {
        init {
            require(allStages.isNotEmpty()) { "$name: allStages empty" }
            require(allPositions.isNotEmpty()) { "$name: allPositions empty" }
            require(initialStage in allStages) { "$name: initialStage not in allStages" }
        }
    }

    // ── Fixture builders ────────────────────────────────────────────────

    private val depSpec = towerDepartureProcedure()
    private val arrSpec = towerArrivalProcedure()
    private val gndSpec = groundTaxiProcedure()

    @Suppress("UNCHECKED_CAST")
    private fun dep() = Fixture(
        name = "TowerDeparture",
        allStages = listOf(
            TowerDepartureStage.AwaitReady, TowerDepartureStage.AwaitLineUpObserved,
            TowerDepartureStage.TakeoffClearanceIssued, TowerDepartureStage.AwaitTakeoffObserved,
            TowerDepartureStage.Complete,
        ),
        allPositions = listOf(
            DeparturePosition.AtHolding, DeparturePosition.OnRunway,
            DeparturePosition.OnRunwayRolling, DeparturePosition.AirborneOverRunway,
            DeparturePosition.OnClimbout, DeparturePosition.Elsewhere,
        ),
        initialStage = TowerDepartureStage.AwaitReady,
        reconcile = ::reconcileDepartureStage,
        stageRules = depSpec.stageRules.filterKeys { it is TowerDepartureStage }
            as Map<TowerDepartureStage, List<AtcRule>>,
    )

    @Suppress("UNCHECKED_CAST")
    private fun arr() = Fixture(
        name = "TowerArrival",
        allStages = listOf(
            TowerArrivalStage.AwaitDownwind, TowerArrivalStage.AwaitApproach,
            TowerArrivalStage.LandingClearanceIssued, TowerArrivalStage.AwaitLandedObserved,
            TowerArrivalStage.AwaitVacating, TowerArrivalStage.Complete,
        ),
        allPositions = listOf(
            ArrivalPosition.OnDownwind, ArrivalPosition.OnBase,
            ArrivalPosition.OnFinal, ArrivalPosition.OnApproach,
            ArrivalPosition.AirborneElsewhere, ArrivalPosition.OnRunway,
            ArrivalPosition.ClearOfRunway, ArrivalPosition.Elsewhere,
        ),
        initialStage = TowerArrivalStage.AwaitDownwind,
        reconcile = ::reconcileArrivalStage,
        stageRules = arrSpec.stageRules.filterKeys { it is TowerArrivalStage }
            as Map<TowerArrivalStage, List<AtcRule>>,
        interrupts = arrSpec.interrupts,
    )

    @Suppress("UNCHECKED_CAST")
    private fun gndDep() = Fixture(
        name = "GroundDeparture",
        allStages = listOf(
            GroundDepartureStage.AwaitTaxiRequest, GroundDepartureStage.AwaitAtHolding,
            GroundDepartureStage.Complete,
        ),
        allPositions = listOf(
            GroundPosition.AtStand, GroundPosition.Taxiing,
            GroundPosition.AtHoldingPoint, GroundPosition.OnRunway,
            GroundPosition.Elsewhere,
        ),
        initialStage = GroundDepartureStage.AwaitTaxiRequest,
        reconcile = ::reconcileGroundDepartureStage,
        stageRules = gndSpec.stageRules.filterKeys { it is GroundDepartureStage }
            as Map<GroundDepartureStage, List<AtcRule>>,
    )

    @Suppress("UNCHECKED_CAST")
    private fun gndArr() = Fixture(
        name = "GroundArrival",
        allStages = listOf(
            GroundArrivalStage.TaxiToStand, GroundArrivalStage.AwaitParked,
            GroundArrivalStage.Complete,
        ),
        allPositions = listOf(
            GroundPosition.AtStand, GroundPosition.Taxiing,
            GroundPosition.AtHoldingPoint, GroundPosition.OnRunway,
            GroundPosition.Elsewhere,
        ),
        initialStage = GroundArrivalStage.TaxiToStand,
        reconcile = ::reconcileGroundArrivalStage,
        stageRules = gndSpec.stageRules.filterKeys { it is GroundArrivalStage }
            as Map<GroundArrivalStage, List<AtcRule>>,
    )

    // ── Helpers ──────────────────────────────────────────────────────────

    private tailrec fun <T> reachable(frontier: Set<T>, visited: Set<T>, edges: (T) -> Set<T>): Set<T> =
        if (frontier.isEmpty()) visited
        else {
            val next = frontier.flatMap(edges).toSet() - visited
            reachable(next, visited + frontier, edges)
        }

    // ── Property 1: Stage coverage ──────────────────────────────────────

    private fun <S : Stage, P> checkStageCoverage(f: Fixture<S, P>) {
        val outputs = f.allStages.flatMap { s -> f.allPositions.map { p -> f.reconcile(s, p).stage } }.toSet()
        for (stage in outputs) {
            if (stage.isComplete) continue
            assertTrue(stage in f.stageRules,
                "${f.name}: reconciliation can produce $stage but no rules handle it")
        }
    }

    @Test fun `every reconciliation output stage has procedure rules`() {
        checkStageCoverage(dep()); checkStageCoverage(arr())
        checkStageCoverage(gndDep()); checkStageCoverage(gndArr())
    }

    // ── Property 2: Reachability ────────────────────────────────────────

    private fun <S : Stage, P> checkReachability(f: Fixture<S, P>) {
        val adj = mutableMapOf<Stage, MutableSet<Stage>>()
        fun edge(from: Stage, to: Stage) { adj.getOrPut(from) { mutableSetOf() }.add(to) }

        for (s in f.allStages) for (p in f.allPositions) {
            val out = f.reconcile(s, p).stage
            if (out != s) edge(s, out)
        }
        for (interrupt in f.interrupts) for (from in interrupt.fromStages) edge(from, interrupt.targetStage)
        for ((stage, rules) in f.stageRules) for (rule in rules) {
            rule.nextStage?.let { edge(stage, it) }
            rule.readbackAdvancesToStage?.let { edge(stage, it) }
        }

        val seed: Set<Stage> = setOf(f.initialStage)
        val reached = reachable(seed, emptySet()) { adj[it]?.toSet() ?: emptySet() }
        for (specStage in f.stageRules.keys) {
            assertTrue(specStage in reached,
                "${f.name}: stage $specStage has rules but is unreachable from ${f.initialStage}")
        }
    }

    @Test fun `every spec stage is reachable from initial stage`() {
        checkReachability(dep()); checkReachability(arr())
        checkReachability(gndDep()); checkReachability(gndArr())
    }

    // ── Property 3: Transition coherence ────────────────────────────────

    private fun <S : Stage, P> checkTransitionCoherence(f: Fixture<S, P>) {
        val known: Set<Stage> = f.stageRules.keys.toSet<Stage>() + f.allStages.filter { it.isComplete }.toSet<Stage>()
        for ((stage, rules) in f.stageRules) for (rule in rules) {
            rule.nextStage?.let { assertTrue(it in known,
                "${f.name}: ${rule.id} at $stage nextStage=$it unknown") }
            rule.readbackAdvancesToStage?.let { assertTrue(it in known,
                "${f.name}: ${rule.id} at $stage readbackAdvancesToStage=$it unknown") }
        }
        for (interrupt in f.interrupts) assertTrue(interrupt.targetStage in known,
            "${f.name}: interrupt ${interrupt.id} targetStage=${interrupt.targetStage} unknown")
    }

    @Test fun `every rule and interrupt target is a known stage`() {
        checkTransitionCoherence(dep()); checkTransitionCoherence(arr())
        checkTransitionCoherence(gndDep()); checkTransitionCoherence(gndArr())
    }

    // ── Property 4: Rule targets in stage enumeration ───────────────────

    private fun <S : Stage, P> checkRuleTargetsInEnum(f: Fixture<S, P>) {
        val known: Set<Stage> = f.allStages.toSet()
        val targets: Set<Stage> =
            f.stageRules.values.flatten().mapNotNull { it.nextStage }.toSet() +
            f.stageRules.values.flatten().mapNotNull { it.readbackAdvancesToStage }.toSet() +
            f.interrupts.map { it.targetStage }.toSet()
        for (target in targets) assertTrue(target in known,
            "${f.name}: rule target $target not in allStages")
    }

    @Test fun `every rule target stage exists in the stage hierarchy`() {
        checkRuleTargetsInEnum(dep()); checkRuleTargetsInEnum(arr())
        checkRuleTargetsInEnum(gndDep()); checkRuleTargetsInEnum(gndArr())
    }

    // ── Property 5: ANOMALOUS → SAFETY coverage ─────────────────────────

    // Allow-list for ANOMALOUS stage-change cells where the target intentionally
    // has no SAFETY rules. Key format: "ProcedureName: InputStage→OutputStage".
    private val anomalousSafetyAllowList = setOf(
        // Ground controller has no authority over runway incursions — tower handles
        // the runway. Marked ANOMALOUS for observability only.
        "GroundDeparture: AwaitTaxiRequest→AwaitAtHolding",
    )

    private fun <S : Stage, P> checkAnomalousSafety(f: Fixture<S, P>) {
        for (s in f.allStages) for (p in f.allPositions) {
            val result = f.reconcile(s, p)
            if (result.transition != TransitionKind.ANOMALOUS) continue
            if (result.stage == s) continue

            val key = "${f.name}: $s→${result.stage}"
            if (key in anomalousSafetyAllowList) continue

            val targetRules = f.stageRules[result.stage] ?: emptyList()
            assertTrue(targetRules.any { it.urgency == Urgency.SAFETY },
                "$key: ANOMALOUS ($s, $p) → ${result.stage} but no SAFETY rule at target. " +
                    "Add one or document in anomalousSafetyAllowList.")
        }
    }

    @Test fun `ANOMALOUS transitions with stage changes reach stages with SAFETY rules`() {
        checkAnomalousSafety(dep()); checkAnomalousSafety(arr())
        checkAnomalousSafety(gndDep()); checkAnomalousSafety(gndArr())
    }

    // ── Property 6: Regression symmetry (arrival) ───────────────────────

    @Test fun `go-around regressions agree between reconciliation and interrupts`() {
        val f = arr()

        // Direction 1: every reconciliation regression has a covering interrupt
        for (s in f.allStages) for (p in f.allPositions) {
            val result = f.reconcile(s, p)
            val output = result.stage as TowerArrivalStage
            val input = s as TowerArrivalStage
            if (output.ordinal >= input.ordinal) continue

            assertTrue(f.interrupts.any { s in it.fromStages && it.targetStage == result.stage },
                "TowerArrival: reconciliation regression ($s, $p) → ${result.stage} has no covering interrupt")
        }

        // Direction 2: every interrupt regression has a reconciliation path
        for (interrupt in f.interrupts) {
            val target = interrupt.targetStage as TowerArrivalStage
            for (from in interrupt.fromStages) {
                val fromArr = from as TowerArrivalStage
                if (target.ordinal >= fromArr.ordinal) continue

                assertTrue(f.allPositions.any { f.reconcile(fromArr, it).stage == target },
                    "TowerArrival: interrupt ${interrupt.id} regresses $from → ${interrupt.targetStage} " +
                        "but reconciliation never produces this from $from")
            }
        }
    }
}
