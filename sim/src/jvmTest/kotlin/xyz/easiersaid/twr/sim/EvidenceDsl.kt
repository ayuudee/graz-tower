package xyz.easiersaid.twr.sim

import kotlin.test.fail
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtomicReadback
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.requiredReadbackAtoms

enum class EvidenceClaimKind {
    StructuralProtocolRequirement,
    SimObservedSourceBehaviour,
    GoldenProjectBehaviour,
    Regression,
    Invariant,
    ExpectedModelProjectionGap,
}

sealed interface EvidenceAuditOutcome {
    data class Pass(val evidence: List<String>) : EvidenceAuditOutcome
    data class Fail(val reason: String, val evidence: List<String>) : EvidenceAuditOutcome
    data class Vacuous(val reason: String) : EvidenceAuditOutcome
    data class ExpectedGap(val gap: EvidenceGapId, val reason: String) : EvidenceAuditOutcome
}

data class EvidenceAuditCase(
    val id: String,
    val claimKind: EvidenceClaimKind,
    val sources: Set<EvidenceSourceRef>,
    val samples: List<EvidenceSample<*>>,
    val evaluate: (EvidenceFactSet) -> EvidenceAuditOutcome,
) {
    init {
        require(id.isNotBlank()) { "evidence case id must not be blank" }
    }
}

data class EvidenceAuditResult(
    val id: String,
    val claimKind: EvidenceClaimKind,
    val sources: Set<EvidenceSourceRef>,
    val samples: List<EvidenceSample<*>>,
    val outcome: EvidenceAuditOutcome,
)

data class EvidenceAuditReport(
    val suiteName: String,
    val facts: EvidenceFactSet,
    val results: List<EvidenceAuditResult>,
) {
    fun assertNoFailures() {
        val failures = results.filter { result -> result.outcome is EvidenceAuditOutcome.Fail }
        if (failures.isNotEmpty()) {
            fail(format())
        }
    }

    fun format(): String = buildString {
        appendLine("Evidence audit report: $suiteName")
        results.forEach { result ->
            appendLine("- ${result.id}: ${result.claimKind} ${result.outcome.label()}")
            if (result.sources.isNotEmpty()) {
                appendLine("  sources: ${result.sources.joinToString { source -> source.canonicalId }}")
            }
        }
    }
}

fun protocolEvidence(
    name: String,
    build: ProtocolEvidenceBuilder.() -> Unit,
): EvidenceAuditReport =
    ProtocolEvidenceBuilder(name).apply(build).report()

fun simEvidence(
    name: String,
    build: SimEvidenceBuilder.() -> Unit,
): EvidenceAuditReport =
    SimEvidenceBuilder(name).apply(build).report()

class ProtocolEvidenceBuilder internal constructor(
    private val name: String,
) {
    private val cases: MutableList<EvidenceAuditCase> = mutableListOf()

    fun structuralReadback(
        id: String,
        instruction: AtcInstruction,
        build: StructuralReadbackBuilder.() -> Unit,
    ) {
        val builder = StructuralReadbackBuilder(id = id, instruction = instruction).apply(build)
        cases += builder.toCase()
    }

    fun report(): EvidenceAuditReport {
        val facts = EvidenceFactSet(
            scenarioId = name,
            facts = emptyList(),
            diagnostic = "Protocol-only evidence suite",
        )
        return auditReport(name = name, facts = facts, cases = cases)
    }
}

class SimEvidenceBuilder internal constructor(
    private val name: String,
) {
    private var facts: EvidenceFactSet? = null
    private val cases: MutableList<EvidenceAuditCase> = mutableListOf()

    fun observe(run: () -> EvidenceFactSet) {
        facts = run()
    }

    fun source(
        id: String,
        build: AuditEvidenceCaseBuilder.() -> Unit,
    ) {
        cases += AuditEvidenceCaseBuilder(id = id, claimKind = EvidenceClaimKind.SimObservedSourceBehaviour)
            .apply(build)
            .toCase(requireSources = true)
    }

    fun golden(
        id: String,
        reason: String,
        build: AuditEvidenceCaseBuilder.() -> Unit,
    ) {
        cases += AuditEvidenceCaseBuilder(id = id, claimKind = EvidenceClaimKind.GoldenProjectBehaviour)
            .also { builder -> builder.note(reason) }
            .apply(build)
            .toCase(requireSources = false)
    }

    fun regression(
        id: String,
        issue: String,
        build: AuditEvidenceCaseBuilder.() -> Unit,
    ) {
        cases += AuditEvidenceCaseBuilder(id = id, claimKind = EvidenceClaimKind.Regression)
            .also { builder -> builder.note(issue) }
            .apply(build)
            .toCase(requireSources = false)
    }

    fun invariant(
        id: String,
        build: AuditEvidenceCaseBuilder.() -> Unit,
    ) {
        cases += AuditEvidenceCaseBuilder(id = id, claimKind = EvidenceClaimKind.Invariant)
            .apply(build)
            .toCase(requireSources = false)
    }

    fun report(): EvidenceAuditReport {
        val observed = checkNotNull(facts) { "simEvidence '$name' did not declare observe { ... }" }
        return auditReport(name = name, facts = observed, cases = cases)
    }
}

class StructuralReadbackBuilder internal constructor(
    private val id: String,
    private val instruction: AtcInstruction,
) {
    private val citationScope = EvidenceSourceCitationScope()
    private val requiredAtoms: MutableList<AtomicReadback> = mutableListOf()

    fun cites(vararg refs: EvidenceSourceRef) {
        citationScope.cites(*refs)
    }

    fun cites(refs: Iterable<EvidenceSourceRef>) {
        citationScope.cites(refs)
    }

    fun requires(vararg atoms: AtomicReadback) {
        require(atoms.isNotEmpty()) { "structural readback case must require at least one atom" }
        requiredAtoms += atoms
    }

    internal fun toCase(): EvidenceAuditCase {
        val sources = citationScope.sources.toSet()
        require(sources.isNotEmpty()) { "structural readback case '$id' must cite typed source refs" }
        val expected = requiredAtoms.toSet()
        require(expected.isNotEmpty()) { "structural readback case '$id' declared no required atoms" }
        return EvidenceAuditCase(
            id = id,
            claimKind = EvidenceClaimKind.StructuralProtocolRequirement,
            sources = sources,
            samples = emptyList(),
        ) {
            val actual = requiredReadbackAtoms(instruction)
            if (actual == expected) {
                EvidenceAuditOutcome.Pass(
                    evidence = listOf("${instruction::class.simpleName} structural atoms match $actual"),
                )
            } else {
                EvidenceAuditOutcome.Fail(
                    reason = "${instruction::class.simpleName} structural atoms differ",
                    evidence = listOf("expected=$expected", "actual=$actual"),
                )
            }
        }
    }
}

class AuditEvidenceCaseBuilder internal constructor(
    private val id: String,
    private val claimKind: EvidenceClaimKind,
) {
    private val citationScope = EvidenceSourceCitationScope()
    private val samples: MutableList<EvidenceSample<*>> = mutableListOf()
    private val notes: MutableList<String> = mutableListOf()
    private var evaluate: (EvidenceExpectContext.() -> EvidenceAuditOutcome)? = null

    fun cites(vararg refs: EvidenceSourceRef) {
        citationScope.cites(*refs)
    }

    fun cites(refs: Iterable<EvidenceSourceRef>) {
        citationScope.cites(refs)
    }

    fun <T : Any> sample(name: String, value: T, tier: SampleTier = SampleTier.Example) {
        samples += EvidenceSample(name = name, value = value, tier = tier)
    }

    fun expect(assertion: EvidenceExpectContext.() -> EvidenceAuditOutcome) {
        evaluate = assertion
    }

    internal fun note(value: String) {
        require(value.isNotBlank()) { "evidence case note must not be blank" }
        notes += value
    }

    internal fun toCase(requireSources: Boolean): EvidenceAuditCase {
        val sources = citationScope.sources.toSet()
        require(!requireSources || sources.isNotEmpty()) { "source evidence case '$id' must cite typed source refs" }
        val assertion = checkNotNull(evaluate) { "evidence case '$id' did not declare expect { ... }" }
        return EvidenceAuditCase(
            id = id,
            claimKind = claimKind,
            sources = sources,
            samples = samples.toList(),
        ) { facts ->
            assertion(EvidenceExpectContext(facts = facts, notes = notes.toList()))
        }
    }
}

class EvidenceExpectContext internal constructor(
    @PublishedApi internal val facts: EvidenceFactSet,
    private val notes: List<String>,
) {
    fun pass(vararg evidence: String): EvidenceAuditOutcome.Pass =
        EvidenceAuditOutcome.Pass(evidence = evidence.toList() + notes)

    fun fail(reason: String, vararg evidence: String): EvidenceAuditOutcome.Fail =
        EvidenceAuditOutcome.Fail(reason = reason, evidence = evidence.toList() + notes)

    fun vacuous(reason: String): EvidenceAuditOutcome.Vacuous =
        EvidenceAuditOutcome.Vacuous(reason)

    fun expectedGap(gap: EvidenceGapId, reason: String): EvidenceAuditOutcome.ExpectedGap =
        EvidenceAuditOutcome.ExpectedGap(gap = gap, reason = reason)

    inline fun <reified I : AtcInstruction> instruction(aircraftId: AircraftId): AuditEvidencePoint =
        facts.orderedFacts()
            .firstOrNull { fact ->
                val instruction = fact.payload as? EvidenceFactPayload.Instruction ?: return@firstOrNull false
                instruction.aircraftId == aircraftId && instruction.instruction is I
            }
            ?.let { fact -> AuditEvidencePoint.Present(label = I::class.simpleName ?: "Instruction", sequence = fact.provenance.sequence) }
            ?: AuditEvidencePoint.Missing(I::class.simpleName ?: "Instruction")

    inline fun <reified E : ReportEvent> report(aircraftId: AircraftId): AuditEvidencePoint =
        facts.orderedFacts()
            .firstOrNull { fact ->
                val report = fact.payload as? EvidenceFactPayload.PilotReport ?: return@firstOrNull false
                report.aircraftId == aircraftId && report.events.any { event -> event is E }
            }
            ?.let { fact -> AuditEvidencePoint.Present(label = E::class.simpleName ?: "Report", sequence = fact.provenance.sequence) }
            ?: AuditEvidencePoint.Missing(E::class.simpleName ?: "Report")
}

sealed interface AuditEvidencePoint {
    val label: String

    data class Present(
        override val label: String,
        val sequence: EvidenceSequence,
    ) : AuditEvidencePoint

    data class Missing(
        override val label: String,
    ) : AuditEvidencePoint
}

data class AuditEvidenceOrder(
    val points: List<AuditEvidencePoint>,
) {
    fun toOutcome(): EvidenceAuditOutcome {
        val missing = points.filterIsInstance<AuditEvidencePoint.Missing>()
        if (missing.isNotEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing evidence point(s): ${missing.joinToString { point -> point.label }}",
                evidence = emptyList(),
            )
        }
        val present = points.filterIsInstance<AuditEvidencePoint.Present>()
        val ordered = present.zipWithNext().all { (left, right) -> left.sequence < right.sequence }
        return if (ordered) {
            EvidenceAuditOutcome.Pass(present.map { point -> "${point.label}@${point.sequence.value}" })
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Expected ordered evidence: ${present.joinToString { point -> point.label }}",
                evidence = present.map { point -> "${point.label}@${point.sequence.value}" },
            )
        }
    }
}

infix fun AuditEvidencePoint.before(next: AuditEvidencePoint): AuditEvidenceOrder =
    AuditEvidenceOrder(listOf(this, next))

infix fun AuditEvidenceOrder.before(next: AuditEvidencePoint): AuditEvidenceOrder =
    copy(points = points + next)

private fun auditReport(
    name: String,
    facts: EvidenceFactSet,
    cases: List<EvidenceAuditCase>,
): EvidenceAuditReport =
    EvidenceAuditReport(
        suiteName = name,
        facts = facts,
        results = cases.map { case ->
            EvidenceAuditResult(
                id = case.id,
                claimKind = case.claimKind,
                sources = case.sources,
                samples = case.samples,
                outcome = case.evaluate(facts),
            )
        },
    )

private fun EvidenceAuditOutcome.label(): String =
    when (this) {
        is EvidenceAuditOutcome.ExpectedGap -> "expected_gap(${gap.metadata.id}): $reason"
        is EvidenceAuditOutcome.Fail -> "fail: $reason"
        is EvidenceAuditOutcome.Pass -> "pass: ${evidence.joinToString()}"
        is EvidenceAuditOutcome.Vacuous -> "vacuous: $reason"
    }
