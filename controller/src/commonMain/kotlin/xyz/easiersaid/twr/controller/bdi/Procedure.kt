package xyz.easiersaid.twr.controller.bdi

import xyz.easiersaid.twr.controller.observe.AdvancementPolicy
import xyz.easiersaid.twr.protocol.RegulationRef
import xyz.easiersaid.twr.protocol.Urgency

/**
 * Static procedure specification for one commitment kind.
 *
 * Declares the stage graph, rules, and interrupts that define how
 * the controller works a particular type of service. Interrupts are
 * checked before stage rules on every cycle.
 */
data class ProcedureSpec(
    val kind: CommitmentKind,
    val interrupts: List<ProcedureInterrupt> = emptyList(),
    val stageRules: Map<Stage, List<AtcRule>>,
    val stageExpectations: Map<Stage, StageExpectation> = emptyMap(),
)

/**
 * An ATC rule: guard + action + stage transition + regulatory grounding.
 *
 * Each rule is an isolated, inspectable unit of controller knowledge.
 * The regulation references trace back to why this rule exists.
 */
data class AtcRule(
    val id: String,
    val description: String,
    val regulations: List<RegulationRef>,
    val guard: RuleGuard,
    val action: RuleAction? = null,
    val nextStage: Stage? = null,
    val urgency: Urgency = Urgency.PROGRESSION,
    val stampReadyAt: Boolean = false,
    /**
     * How the stage transition is applied:
     * - [AdvancementPolicy.Immediate]: stage advances when the rule fires (stage-only rules,
     *   rules without readback requirements).
     * - [AdvancementPolicy.OnReadbackConfirmed]: stage advances only when the pilot's readback
     *   is confirmed. An [OutstandingCoordination] is created at emission time.
     *
     * Default: if the rule has an action AND a nextStage, default to OnReadbackConfirmed.
     * If no action (stage-only) or no nextStage, default to Immediate.
     */
    val advancementPolicy: AdvancementPolicy = AdvancementPolicy.Immediate,
)

/**
 * Procedure-level interrupt — fires across multiple stages.
 *
 * Go-around is the canonical example: it can fire from AwaitApproach
 * or AwaitLandedObserved, resetting to AwaitDownwind.
 */
data class ProcedureInterrupt(
    val id: String,
    val description: String,
    val regulations: List<RegulationRef>,
    val fromStages: Set<Stage>,
    val guard: RuleGuard,
    val targetStage: Stage,
)

/** What the controller expects from the pilot at a given stage. For training feedback. */
data class StageExpectation(
    val act: ExpectedPilotAct,
    val explanation: String,
    val regulations: List<RegulationRef> = emptyList(),
)

enum class ExpectedPilotAct {
    Readback,
    InitialContact,
    RequestTaxi,
    RequestStartup,
    ReadyForDeparture,
    PositionReport,
    TuneFrequency,
}
