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
    /** Stage to advance to when readback is confirmed. Used with [AdvancementPolicy.Immediate]
     *  when the rule should advance immediately to [nextStage] AND advance further on readback.
     *  Example: DEP-TAKEOFF advances to TakeoffClearanceIssued immediately, then to
     *  AwaitTakeoffObserved on readback confirmation. */
    val readbackAdvancesToStage: Stage? = null,
    val urgency: Urgency = Urgency.PROGRESSION,
    val stampReadyAt: Boolean = false,
    /**
     * How the stage transition is applied. All rules use [AdvancementPolicy.Immediate].
     * Readback-gated advancement is handled by [readbackAdvancesToStage] — the
     * initial stage advances immediately, and the readback confirmation advances
     * to a further stage via the coordination ledger.
     */
    val advancementPolicy: AdvancementPolicy,
) {
    init {
        require(readbackAdvancesToStage == null || nextStage != null) {
            "Rule $id: readbackAdvancesToStage requires nextStage to be set"
        }
    }
}

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
