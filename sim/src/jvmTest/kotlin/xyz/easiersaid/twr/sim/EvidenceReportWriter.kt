package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class EvidenceReportFiles(
    val markdown: Path,
    val json: Path,
)

object EvidenceReportWriter {
    @OptIn(ExperimentalSerializationApi::class)
    private val jsonFormatter: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun write(
        report: EvidenceAuditReport,
        outputDir: Path,
    ): EvidenceReportFiles {
        Files.createDirectories(outputDir)
        val baseName = report.suiteName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val markdown = outputDir.resolve("$baseName.md")
        val json = outputDir.resolve("$baseName.json")
        Files.writeString(markdown, markdown(report))
        Files.writeString(json, json(report))
        return EvidenceReportFiles(markdown = markdown, json = json)
    }

    fun markdown(report: EvidenceAuditReport): String = buildString {
        appendLine("# Evidence Report: ${report.suiteName}")
        appendLine()
        appendLine("- Scenario: `${report.facts.scenarioId}`")
        appendLine("- Fact count: `${report.facts.facts.size}`")
        appendLine("- Case count: `${report.results.size}`")
        appendLine()
        report.results.forEach { result ->
            appendLine("## ${result.id}")
            appendLine()
            appendLine("- Claim kind: `${result.claimKind}`")
            appendLine("- Outcome: `${result.outcome.kind()}`")
            appendLine("- Applicability: `${result.applicability()}`")
            appendLine("- Activation: `${result.activationStatus()}`")
            appendLine("- Adequacy: `${result.adequacy()}`")
            appendLine("- Activation fact ids: ${result.activationFactIds.sortedBy { id -> id.value }.joinToString(prefix = "`", postfix = "`") { id -> id.value }}")
            if (result.sources.isNotEmpty()) {
                appendLine("- Sources:")
                result.sources.sortedBy { source -> source.canonicalId }.forEach { source ->
                    appendLine("  - `${source.canonicalId}`")
                }
            }
            if (result.samples.isNotEmpty()) {
                appendLine("- Samples:")
                result.samples.forEach { sample ->
                    appendLine("  - `${sample.name}` = `${sample.value}` (${sample.tier})")
                    sample.generated?.let { generated ->
                        appendLine(
                            "    - generated: domain `${generated.domainName}`, seed `${generated.seed}`, " +
                                "count `${generated.count}`, index `${generated.sampleIndex}`, " +
                                "value `${generated.displayValue}`, partition `${generated.partition}`",
                        )
                    }
                }
            }
            result.outcome.expectedGapMetadata()?.let { gap ->
                appendLine("- Typed gap: `${gap.id}`")
                appendLine("- Missing concept: ${gap.missingConcept}")
                appendLine("- Closure trigger: ${gap.closureTrigger}")
                appendLine("- Tracking: `${gap.tracking.id}`")
            }
            (result.outcome as? EvidenceAuditOutcome.Advisory)?.let { advisory ->
                appendLine("- Advisory reason: ${advisory.reason}")
                if (advisory.violations.isNotEmpty()) {
                    appendLine("- Advisory violations:")
                    advisory.violations.forEach { violation ->
                        appendLine(
                            "  - clearance `${violation.clearanceRef.value}` " +
                                "issued during `${violation.observedWindow}`",
                        )
                    }
                }
            }
            appendLine()
        }
    }

    fun json(report: EvidenceAuditReport): String =
        jsonFormatter.encodeToString(JsonObject.serializer(), jsonObject(report))

    private fun jsonObject(report: EvidenceAuditReport): JsonObject =
        buildJsonObject {
            put("suiteName", report.suiteName)
            put("scenarioId", report.facts.scenarioId)
            put("factCount", report.facts.facts.size)
            put("cases", JsonArray(report.results.map(::caseJson)))
        }

    private fun caseJson(result: EvidenceAuditResult): JsonObject =
        buildJsonObject {
            put("caseId", result.id)
            put("claimKind", result.claimKind.name)
            put("outcome", result.outcome.kind())
            put("applicability", result.applicability())
            put("activation", result.activationStatus())
            put("adequacy", result.adequacy())
            put("sourceRefs", stringArray(result.sources.sortedBy { source -> source.canonicalId }.map { source -> source.canonicalId }))
            put("activationFactIds", stringArray(result.activationFactIds.sortedBy { id -> id.value }.map { id -> id.value }))
            put("samples", JsonArray(result.samples.map(::sampleJson)))
            result.outcome.expectedGapMetadata()?.let { gap ->
                put(
                    "typedGap",
                    buildJsonObject {
                        put("id", gap.id)
                        put("affectedSourceRefs", stringArray(gap.affectedSources.map { source -> source.canonicalId }.sorted()))
                        put("missingConcept", gap.missingConcept)
                        put("closureTrigger", gap.closureTrigger)
                        put("tracking", gap.tracking.id)
                    },
                )
            }
            (result.outcome as? EvidenceAuditOutcome.Advisory)?.let { advisory ->
                put(
                    "advisory",
                    buildJsonObject {
                        put("reason", advisory.reason)
                        put(
                            "violations",
                            JsonArray(
                                advisory.violations.map { violation ->
                                    buildJsonObject {
                                        put("clearanceRef", violation.clearanceRef.value)
                                        put("observedWindow", violation.observedWindow.name)
                                    }
                                },
                            ),
                        )
                    },
                )
            }
        }

    private fun sampleJson(sample: EvidenceSample<*>): JsonObject =
        buildJsonObject {
            put("name", sample.name)
            put("value", sample.value.toString())
            put("tier", sample.tier.name)
            sample.generated?.let { generated ->
                put(
                    "generated",
                    buildJsonObject {
                        put("domainName", generated.domainName)
                        put("seed", generated.seed)
                        put("count", generated.count)
                        put("sampleIndex", generated.sampleIndex)
                        put("displayValue", generated.displayValue)
                        put("partition", generated.partition)
                    },
                )
            }
        }

    private fun stringArray(values: List<String>): JsonArray =
        buildJsonArray {
            values.forEach { value -> add(JsonPrimitive(value)) }
        }
}

private fun EvidenceAuditOutcome.kind(): String =
    when (this) {
        is EvidenceAuditOutcome.Advisory -> "advisory"
        is EvidenceAuditOutcome.ExpectedGap -> "expected_gap"
        is EvidenceAuditOutcome.Fail -> "fail"
        is EvidenceAuditOutcome.Pass -> "pass"
        is EvidenceAuditOutcome.Vacuous -> "vacuous"
    }

private fun EvidenceAuditOutcome.expectedGapMetadata(): EvidenceGapMetadata? =
    when (this) {
        is EvidenceAuditOutcome.ExpectedGap -> gap.metadata
        is EvidenceAuditOutcome.Advisory,
        is EvidenceAuditOutcome.Fail,
        is EvidenceAuditOutcome.Pass,
        is EvidenceAuditOutcome.Vacuous,
        -> null
    }

private fun EvidenceAuditResult.applicability(): String =
    when (claimKind) {
        EvidenceClaimKind.StructuralProtocolRequirement -> "structural-protocol"
        EvidenceClaimKind.SimObservedSourceBehaviour -> "sim-observed-source"
        EvidenceClaimKind.GoldenProjectBehaviour -> "project-golden"
        EvidenceClaimKind.Regression -> "project-regression"
        EvidenceClaimKind.Invariant -> "project-invariant"
        EvidenceClaimKind.ExpectedModelProjectionGap -> "expected-model-projection-gap"
    }

private fun EvidenceAuditResult.activationStatus(): String =
    when {
        activationFactIds.isNotEmpty() -> "activated"
        outcome is EvidenceAuditOutcome.ExpectedGap -> "typed-gap"
        outcome is EvidenceAuditOutcome.Vacuous -> "vacuous"
        sources.isNotEmpty() && claimKind == EvidenceClaimKind.SimObservedSourceBehaviour -> "missing"
        else -> "not-required"
    }

private fun EvidenceAuditResult.adequacy(): String =
    when {
        outcome is EvidenceAuditOutcome.ExpectedGap -> "blocked-by-typed-gap"
        outcome is EvidenceAuditOutcome.Vacuous -> "vacuous"
        outcome is EvidenceAuditOutcome.Fail -> "failed"
        outcome is EvidenceAuditOutcome.Advisory -> "advisory-observed"
        activationFactIds.isNotEmpty() -> "activated-facts-present"
        claimKind == EvidenceClaimKind.StructuralProtocolRequirement -> "structural-only"
        else -> "not-source-backed"
    }
