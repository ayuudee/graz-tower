package xyz.easiersaid.twr.sim

import kotlin.random.Random
import kotlin.test.fail

data class SourceUnitSpec(
    val id: String,
    val title: String,
    val sourceUnits: Set<SourceUnitRef>,
    val domain: SpecDomain,
    val probes: List<SpecProbe>,
) {
    init {
        require(id.isNotBlank()) { "spec id must not be blank" }
        require(title.isNotBlank()) { "spec title must not be blank" }
        require(sourceUnits.isNotEmpty()) { "spec must cite at least one source unit" }
        require(probes.isNotEmpty()) { "spec must define at least one probe" }
    }

    fun evaluate(): SourceUnitSpecReport =
        SourceUnitSpecReport(
            specId = id,
            title = title,
            sourceUnits = sourceUnits,
            domain = domain,
            probeResults = probes.map { probe -> probe.evaluate(id) },
        )

    fun assertSatisfied(): SourceUnitSpecReport {
        val report = evaluate()
        val failures = report.probeResults.filter { result -> result.status is SpecProbeStatus.Failed }
        if (failures.isNotEmpty()) {
            val first = failures.first().status as SpecProbeStatus.Failed
            throw AssertionError(report.format(), first.cause)
        }
        return report
    }
}

data class SpecDomain(
    val dimensions: List<SpecDimension>,
) {
    init {
        require(dimensions.map { it.name }.distinct().size == dimensions.size) {
            "spec domain dimension names must be unique"
        }
    }

    companion object {
        val Empty: SpecDomain = SpecDomain(emptyList())
    }
}

data class SpecDimension(
    val name: String,
    val values: Set<String>,
) {
    init {
        require(name.isNotBlank()) { "domain dimension name must not be blank" }
        require(values.isNotEmpty()) { "domain dimension '$name' must have at least one value" }
    }
}

data class SpecProbe(
    val name: String,
    val kind: SpecProbeKind,
    val parameters: Map<String, String>,
    val assertProbe: SpecProbeContext.() -> Unit,
) {
    init {
        require(name.isNotBlank()) { "probe name must not be blank" }
    }

    fun evaluate(specId: String): SpecProbeResult {
        val context = SpecProbeContext(specId = specId, probeName = name, parameters = parameters)
        return try {
            context.assertProbe()
            SpecProbeResult(
                name = name,
                kind = kind,
                parameters = parameters,
                status = SpecProbeStatus.Passed,
                counters = context.counters(),
            )
        } catch (gap: SpecModelGap) {
            SpecProbeResult(
                name = name,
                kind = kind,
                parameters = parameters,
                status = SpecProbeStatus.ModelGap(gap.reason),
                counters = context.counters(),
            )
        } catch (failure: AssertionError) {
            SpecProbeResult(
                name = name,
                kind = kind,
                parameters = parameters,
                status = SpecProbeStatus.Failed(failure),
                counters = context.counters(),
            )
        } catch (failure: RuntimeException) {
            SpecProbeResult(
                name = name,
                kind = kind,
                parameters = parameters,
                status = SpecProbeStatus.Failed(failure),
                counters = context.counters(),
            )
        }
    }
}

enum class SpecProbeKind {
    Witness,
    Partition,
    Fuzz,
}

class SpecProbeContext internal constructor(
    val specId: String,
    val probeName: String,
    val parameters: Map<String, String>,
) {
    private val hits: MutableMap<String, Int> = mutableMapOf()

    fun parameter(name: String): String =
        parameters[name] ?: fail("Probe '$probeName' missing required parameter '$name'")

    fun intParameter(name: String): Int =
        parameter(name).toIntOrNull() ?: fail("Probe '$probeName' parameter '$name' is not an Int")

    fun hit(counter: String) {
        require(counter.isNotBlank()) { "counter name must not be blank" }
        hits[counter] = (hits[counter] ?: 0) + 1
    }

    fun requireHits(counter: String, minimum: Int = 1) {
        require(minimum > 0) { "minimum hit count must be positive" }
        val actual = hits[counter] ?: 0
        check(actual >= minimum) {
            "Expected non-vacuity counter '$counter' to be hit at least $minimum time(s); got $actual"
        }
    }

    fun modelGap(reason: String): Nothing {
        require(reason.isNotBlank()) { "model gap reason must not be blank" }
        throw SpecModelGap(reason)
    }

    internal fun counters(): Map<String, Int> = hits.toMap()
}

private class SpecModelGap(val reason: String) : RuntimeException(reason)

sealed interface SpecProbeStatus {
    data object Passed : SpecProbeStatus
    data class Failed(val cause: Throwable) : SpecProbeStatus
    data class ModelGap(val reason: String) : SpecProbeStatus
}

data class SpecProbeResult(
    val name: String,
    val kind: SpecProbeKind,
    val parameters: Map<String, String>,
    val status: SpecProbeStatus,
    val counters: Map<String, Int>,
)

data class SourceUnitSpecReport(
    val specId: String,
    val title: String,
    val sourceUnits: Set<SourceUnitRef>,
    val domain: SpecDomain,
    val probeResults: List<SpecProbeResult>,
) {
    fun format(): String = buildString {
        appendLine("Source-unit spec '$specId' failed.")
        appendLine("Title: $title")
        appendLine("Source units:")
        sourceUnits.forEach { sourceUnit -> appendLine("  - ${sourceUnit.canonicalId}") }
        if (domain.dimensions.isNotEmpty()) {
            appendLine("Domain:")
            domain.dimensions.forEach { dimension ->
                appendLine("  - ${dimension.name}: ${dimension.values.sorted().joinToString()}")
            }
        }
        appendLine("Probe results:")
        probeResults.forEach { result ->
            appendLine("  - ${result.kind}:${result.name} ${result.status.format()}")
            if (result.parameters.isNotEmpty()) {
                appendLine("    parameters: ${result.parameters}")
            }
            if (result.counters.isNotEmpty()) {
                appendLine("    counters: ${result.counters}")
            }
        }
    }
}

private fun SpecProbeStatus.format(): String = when (this) {
    SpecProbeStatus.Passed -> "passed"
    is SpecProbeStatus.Failed -> "failed: ${cause.message.orEmpty()}"
    is SpecProbeStatus.ModelGap -> "model_gap: $reason"
}

class SourceUnitSpecBuilder(
    private val id: String,
) {
    private var title: String = id
    private val sourceUnits: MutableSet<SourceUnitRef> = mutableSetOf()
    private val dimensions: MutableList<SpecDimension> = mutableListOf()
    private val probes: MutableList<SpecProbe> = mutableListOf()

    fun title(value: String) {
        title = value
    }

    fun sourceUnit(sourceUnit: SourceUnitRef) {
        sourceUnits += sourceUnit
    }

    fun sourceUnits(values: Iterable<SourceUnitRef>) {
        sourceUnits += values
    }

    fun domain(name: String, values: Set<String>) {
        dimensions += SpecDimension(name = name, values = values)
    }

    fun witness(
        name: String,
        parameters: Map<String, String> = emptyMap(),
        assertProbe: SpecProbeContext.() -> Unit,
    ) {
        probes += SpecProbe(name = name, kind = SpecProbeKind.Witness, parameters = parameters, assertProbe = assertProbe)
    }

    fun partition(
        name: String,
        parameters: Map<String, String>,
        assertProbe: SpecProbeContext.() -> Unit,
    ) {
        probes += SpecProbe(name = name, kind = SpecProbeKind.Partition, parameters = parameters, assertProbe = assertProbe)
    }

    fun fuzz(
        name: String,
        samples: Int,
        seed: Long,
        parameters: (Random, Int) -> Map<String, String>,
        assertProbe: SpecProbeContext.() -> Unit,
    ) {
        require(samples > 0) { "fuzz sample count must be positive" }
        val random = Random(seed)
        probes += (0 until samples).map { index ->
            SpecProbe(
                name = "$name#$index",
                kind = SpecProbeKind.Fuzz,
                parameters = parameters(random, index) + mapOf("seed" to seed.toString(), "sample" to index.toString()),
                assertProbe = assertProbe,
            )
        }
    }

    fun build(): SourceUnitSpec =
        SourceUnitSpec(
            id = id,
            title = title,
            sourceUnits = sourceUnits.toSet(),
            domain = SpecDomain(dimensions.toList()),
            probes = probes.toList(),
        )
}

fun sourceUnitSpec(
    id: String,
    build: SourceUnitSpecBuilder.() -> Unit,
): SourceUnitSpec =
    SourceUnitSpecBuilder(id).apply(build).build()

fun SourceUnitSpecReport.assertNoModelGaps() {
    val gaps = probeResults.filter { result -> result.status is SpecProbeStatus.ModelGap }
    if (gaps.isNotEmpty()) {
        fail(format())
    }
}

fun SourceUnitSpecReport.assertHasModelGap() {
    val gaps = probeResults.filter { result -> result.status is SpecProbeStatus.ModelGap }
    if (gaps.isEmpty()) {
        fail("Expected source-unit spec '$specId' to report at least one model gap.\n${format()}")
    }
}
