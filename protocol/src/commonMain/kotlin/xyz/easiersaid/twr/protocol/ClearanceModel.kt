package xyz.easiersaid.twr.protocol

import arrow.core.NonEmptyList

enum class ClearanceStatus {
    ISSUED,
    READBACK_PENDING,
    CONDITION_PENDING,
    ACTIVE,
    COMPLETED,
    SUPERSEDED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this in TERMINAL_STATUSES

    val isSupersedable: Boolean
        get() = this in SUPERSEDABLE_STATUSES

    companion object {
        private val TERMINAL_STATUSES = setOf(COMPLETED, SUPERSEDED, CANCELLED)
        private val SUPERSEDABLE_STATUSES = setOf(ISSUED, READBACK_PENDING, CONDITION_PENDING, ACTIVE)
    }
}

enum class ClearanceDomain {
    GROUND,
    RUNWAY,
    ROUTE,
    LEVEL,
    SPEED,
    SQUAWK,
    FREQUENCY
}

enum class InstructionTiming {
    SEQUENTIAL,
    IMMEDIATE,
    PERSISTENT
}

enum class CompletionCategory {
    SELF_COMPLETING,
    ON_ACTIVATION,
    EXTERNAL_EVENT,
    PERSISTENT
}

sealed interface ClearanceContent {

    data class Single(
        val instruction: AtcInstruction
    ) : ClearanceContent

    data class Compound(
        val steps: NonEmptyList<AtcInstruction>,
        val completedSteps: Set<Int> = emptySet()
    ) : ClearanceContent
}
