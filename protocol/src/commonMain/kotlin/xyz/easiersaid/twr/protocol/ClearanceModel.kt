package xyz.easiersaid.twr.protocol

enum class ClearanceStatus {
    ISSUED,
    READBACK_PENDING,
    CONDITION_PENDING,
    ACTIVE,
    COMPLETED,
    SUPERSEDED,
    CANCELLED
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
        val steps: List<AtcInstruction>,
        val completedSteps: Set<Int> = emptySet()
    ) : ClearanceContent {
        init {
            require(steps.isNotEmpty()) { "Compound clearance must contain at least one step" }
        }
    }
}
