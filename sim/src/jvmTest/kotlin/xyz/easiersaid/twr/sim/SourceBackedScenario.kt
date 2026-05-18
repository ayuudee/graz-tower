package xyz.easiersaid.twr.sim

import kotlin.test.fail

data class SourceBackedScenario(
    val id: String,
    val sourceUnits: Set<SourceUnitRef>,
    val run: () -> Unit,
) {
    init {
        require(id.isNotBlank()) { "scenario id must not be blank" }
        require(sourceUnits.isNotEmpty()) { "scenario must cite at least one source unit" }
    }

    fun assertSatisfied() {
        try {
            run()
        } catch (failure: AssertionError) {
            failWithSourceContext(failure)
        } catch (failure: RuntimeException) {
            failWithSourceContext(failure)
        }
    }

    private fun failWithSourceContext(failure: Throwable): Nothing =
        fail(
            buildString {
                appendLine("Source-backed scenario '$id' failed.")
                appendLine("Source units:")
                sourceUnits.forEach { sourceUnit -> appendLine("  - ${sourceUnit.canonicalId}") }
                appendLine(failure.message.orEmpty())
            },
        )
}

@JvmInline
value class SourceUnitRef(val canonicalId: String)
