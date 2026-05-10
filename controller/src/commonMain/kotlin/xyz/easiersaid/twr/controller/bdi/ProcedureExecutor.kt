package xyz.easiersaid.twr.controller.bdi

import arrow.core.left
import arrow.core.right
import xyz.easiersaid.twr.controller.AircraftObservation
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.controller.observe.AdvancementPolicy
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Urgency

/** Result of executing a procedure rule. */
data class OperatorResult(
    val action: ProposedAction?,
    val urgency: Urgency,
    val nextStage: Stage?,
    val trace: DecisionTrace,
    val stampReadyAt: SimTime? = null,
    val advancementPolicy: AdvancementPolicy = AdvancementPolicy.Immediate,
    /** Stage to advance to on readback confirmation. See [AtcRule.readbackAdvancesToStage]. */
    val readbackAdvancesToStage: Stage? = null,
)

private fun operatorResultFor(rule: AtcRule, action: ProposedAction?, time: SimTime): OperatorResult =
    OperatorResult(
        action = action,
        urgency = rule.urgency,
        nextStage = rule.nextStage,
        trace = DecisionTrace(
            ruleId = rule.id,
            description = rule.description,
            regulations = rule.regulations,
        ),
        stampReadyAt = if (rule.stampReadyAt) time else null,
        advancementPolicy = rule.advancementPolicy,
        readbackAdvancesToStage = rule.readbackAdvancesToStage,
    )

/** A rule whose guard passed but whose action failed to resolve. Surfaced in traces. */
data class RuleResolutionFailure(val ruleId: String, val reason: String)

/**
 * Outcome of executing a procedure: the [result] when a rule fires, plus any
 * [actionFailures] from rules whose guards passed but whose actions couldn't
 * resolve against the world. Failures are carried whether or not a later rule
 * ultimately fired — they remain visible in the decision trace.
 */
data class ExecutionOutcome(
    val result: OperatorResult?,
    val actionFailures: List<RuleResolutionFailure> = emptyList(),
)

/**
 * Execute a procedure for a commitment's current stage.
 *
 * 1. Check procedure-level interrupts.
 * 2. Evaluate stage rules in declaration order.
 *    A rule fires when its guard passes AND its action resolves.
 *    Guard-passes-but-action-fails outcomes are collected into [ExecutionOutcome.actionFailures]
 *    rather than being silently swallowed.
 */
fun executeProcedure(
    spec: ProcedureSpec,
    commitment: Commitment,
    ac: AircraftObservation,
    ctx: OperatorContext,
): ExecutionOutcome {
    // 1. Interrupts
    @Suppress("LoopWithTooManyJumpStatements") // first-match-wins early-return is the
    // semantic; folding into firstOrNull obscures the OperatorContext threading.
    for (interrupt in spec.interrupts) {
        if (commitment.stage in interrupt.fromStages &&
            interrupt.guard.evaluate(ac, commitment, ctx)
        ) {
            return ExecutionOutcome(
                result = OperatorResult(
                    action = null,
                    urgency = Urgency.SAFETY,
                    nextStage = interrupt.targetStage,
                    trace = DecisionTrace(
                        ruleId = interrupt.id,
                        description = interrupt.description,
                        regulations = interrupt.regulations,
                    ),
                ),
            )
        }
    }

    // 2. Stage rules
    val rules = spec.stageRules[commitment.stage] ?: return ExecutionOutcome(result = null)
    val failures = mutableListOf<RuleResolutionFailure>()
    @Suppress("LoopWithTooManyJumpStatements") // first-match-wins with action-resolution failure
    // accumulation; the imperative shape mirrors the rule-priority semantics.
    for (rule in rules) {
        if (!rule.guard.evaluate(ac, commitment, ctx)) continue

        if (rule.action == null) {
            return ExecutionOutcome(
                result = operatorResultFor(rule, action = null, time = ctx.time),
                actionFailures = failures.toList(),
            )
        }

        rule.action.resolve(ac, commitment, ctx).fold(
            ifLeft = { failures += RuleResolutionFailure(rule.id, it.reason) },
            ifRight = { proposedAction ->
                validateResolvedActionTarget(rule.id, ac.id, proposedAction).fold(
                    { failure ->
                        failures += failure
                        return@fold
                    },
                    { validAction ->
                        return ExecutionOutcome(
                            result = operatorResultFor(rule, action = validAction, time = ctx.time),
                            actionFailures = failures.toList(),
                        )
                    },
                )
            },
        )
    }
    return ExecutionOutcome(result = null, actionFailures = failures.toList())
}

internal fun validateResolvedActionTarget(
    ruleId: String,
    procedureAircraft: xyz.easiersaid.twr.protocol.AircraftId,
    proposedAction: ProposedAction,
): arrow.core.Either<RuleResolutionFailure, ProposedAction> =
    if (proposedAction.aircraft == procedureAircraft) {
        proposedAction.right()
    } else {
        RuleResolutionFailure(
            ruleId,
            "Resolved action target ${proposedAction.aircraft} does not match procedure aircraft $procedureAircraft",
        ).left()
    }

/** Trace why no rule fired — for diagnostics and training feedback. */
fun traceRuleFailures(
    spec: ProcedureSpec,
    commitment: Commitment,
    ac: AircraftObservation,
    ctx: OperatorContext,
): List<RuleTrace> {
    val rules = spec.stageRules[commitment.stage] ?: return emptyList()
    return rules.map { rule ->
        val passed = rule.guard.evaluate(ac, commitment, ctx)
        val failures = if (!passed) traceGuardFailures(rule.guard, ac, commitment, ctx) else emptyList()
        RuleTrace(rule.id, passed, failures)
    }
}

data class RuleTrace(val ruleId: String, val guardPassed: Boolean, val failures: List<String>)

private fun traceGuardFailures(
    guard: RuleGuard,
    ac: AircraftObservation,
    commitment: Commitment,
    ctx: OperatorContext,
): List<String> = when (guard) {
    is AllOf -> guard.guards
        .filter { !it.evaluate(ac, commitment, ctx) }
        .map { it.failureMessage }
    is AnyOf -> if (!guard.evaluate(ac, commitment, ctx))
        listOf("None of: ${guard.guards.joinToString(", ") { it.failureMessage }}")
    else emptyList()
    is Not -> if (guard.inner.evaluate(ac, commitment, ctx))
        listOf("Condition met but should not be: ${guard.inner.failureMessage}")
    else emptyList()
    else -> listOf(guard.failureMessage)
}
