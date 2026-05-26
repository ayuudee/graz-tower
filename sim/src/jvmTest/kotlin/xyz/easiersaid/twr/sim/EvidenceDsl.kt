package xyz.easiersaid.twr.sim

import kotlin.test.fail
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtomicReadback
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.requiredReadbackAtoms
import xyz.easiersaid.twr.pilot.PilotPhase

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

    /**
     * Advisory outcome leaf for ICAO 9432 "should" / "avoid" / "on no occasion"
     * obligations that observe behaviour without forcing a Pass/Fail dichotomy.
     *
     * Source: §2.8.3.2 (controllers *should* pace clearances; *should* avoid
     * issuing clearances during complicated taxi manoeuvres; on no occasion
     * during line-up or take-off). Advisory is observable-and-reported, never
     * build-blocking.
     *
     * [EvidenceReport.assertNoFailures] continues to treat only [Fail] as
     * failing; [Advisory] is reported, counted, and rendered distinctly but
     * does not propagate into JUnit failures. The audit honestly records the
     * advisory observation while keeping the build green.
     *
     * Carries a list of typed [AdvisoryViolation] records (one per observed
     * regulation hit) plus a human-readable [reason] describing the selector's
     * branch decision.
     */
    data class Advisory(
        val violations: List<AdvisoryViolation>,
        val reason: String,
    ) : EvidenceAuditOutcome
}

/**
 * One observation of an advisory-grade regulation hit. Used by
 * [EvidenceAuditOutcome.Advisory] to carry per-clearance diagnostic records
 * for §2.8.3.2-style "should" obligations.
 *
 * [clearanceRef] is the transmission instance carrying the clearance; the
 * audit log links the advisory back to the originating instruction so future
 * reviewers can reconstruct the per-clearance evidence chain.
 *
 * [observedWindow] names the pacing window the clearance was issued during —
 * the regulation-relevant phase classification, not a 1:1 mirror of
 * [xyz.easiersaid.twr.pilot.PilotPhase].
 */
data class AdvisoryViolation(
    val clearanceRef: TransmissionId,
    val observedWindow: PacingWindow,
)

data class EvidenceAuditCase(
    val id: String,
    val claimKind: EvidenceClaimKind,
    val sources: Set<EvidenceSourceRef>,
    val samples: List<EvidenceSample<*>>,
    val requiresActivation: Boolean,
    val evaluate: (EvidenceFactSet) -> EvidenceAuditEvaluation,
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
    val activationFactIds: Set<FactId>,
)

data class EvidenceAuditEvaluation(
    val outcome: EvidenceAuditOutcome,
    val activationFactIds: Set<FactId>,
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

    fun <T : Any> generatedProtocol(
        id: String,
        domain: EvidenceGeneratedDomain<T>,
        build: ProtocolEvidenceBuilder.(EvidenceGeneratedSample<T>) -> Unit,
    ) {
        require(id.isNotBlank()) { "generated protocol group id must not be blank" }
        domain.samples.forEach { sample -> build(sample) }
    }

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
    private val samples: MutableList<EvidenceSample<*>> = mutableListOf()
    private var requirementDeclared: Boolean = false

    fun cites(vararg refs: EvidenceSourceRef) {
        citationScope.cites(*refs)
    }

    fun cites(refs: Iterable<EvidenceSourceRef>) {
        citationScope.cites(refs)
    }

    fun requires(vararg atoms: AtomicReadback) {
        require(atoms.isNotEmpty()) { "structural readback case must require at least one atom" }
        requirementDeclared = true
        requiredAtoms += atoms
    }

    fun requiresNoAtoms() {
        requirementDeclared = true
    }

    fun <T : Any> sample(sample: EvidenceGeneratedSample<T>) {
        samples += EvidenceSample(
            name = sample.metadata.domainName,
            value = sample.value,
            tier = SampleTier.Generated,
            generated = sample.metadata,
        )
    }

    internal fun toCase(): EvidenceAuditCase {
        val sources = citationScope.sources.toSet()
        require(sources.isNotEmpty()) { "structural readback case '$id' must cite typed source refs" }
        val expected = requiredAtoms.toSet()
        require(requirementDeclared) { "structural readback case '$id' declared no structural atom expectation" }
        return EvidenceAuditCase(
            id = id,
            claimKind = EvidenceClaimKind.StructuralProtocolRequirement,
            sources = sources,
            samples = samples.toList(),
            requiresActivation = false,
        ) {
            val actual = requiredReadbackAtoms(instruction)
            val outcome = if (actual == expected) {
                EvidenceAuditOutcome.Pass(
                    evidence = listOf("${instruction::class.simpleName} structural atoms match $actual"),
                )
            } else {
                EvidenceAuditOutcome.Fail(
                    reason = "${instruction::class.simpleName} structural atoms differ",
                    evidence = listOf("expected=$expected", "actual=$actual"),
                )
            }
            EvidenceAuditEvaluation(outcome = outcome, activationFactIds = emptySet())
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
            requiresActivation = requireSources,
        ) { facts ->
            val context = EvidenceExpectContext(facts = facts, notes = notes.toList())
            val outcome = assertion(context)
            val activationFactIds = context.activationFactIds()
            val activationCheckedOutcome = if (
                requireSources &&
                activationFactIds.isEmpty() &&
                outcome !is EvidenceAuditOutcome.ExpectedGap &&
                outcome !is EvidenceAuditOutcome.Vacuous &&
                outcome !is EvidenceAuditOutcome.Advisory
            ) {
                EvidenceAuditOutcome.Fail(
                    reason = "Source evidence case '$id' did not activate any evidence facts",
                    evidence = emptyList(),
                )
            } else {
                outcome
            }
            EvidenceAuditEvaluation(outcome = activationCheckedOutcome, activationFactIds = activationFactIds)
        }
    }
}

class EvidenceExpectContext internal constructor(
    @PublishedApi internal val facts: EvidenceFactSet,
    private val notes: List<String>,
) {
    @PublishedApi
    internal val activated: MutableSet<FactId> = mutableSetOf()

    fun pass(vararg evidence: String): EvidenceAuditOutcome.Pass =
        EvidenceAuditOutcome.Pass(evidence = evidence.toList() + notes)

    fun fail(reason: String, vararg evidence: String): EvidenceAuditOutcome.Fail =
        EvidenceAuditOutcome.Fail(reason = reason, evidence = evidence.toList() + notes)

    fun vacuous(reason: String): EvidenceAuditOutcome.Vacuous =
        EvidenceAuditOutcome.Vacuous(reason)

    fun expectedGap(gap: EvidenceGapId, reason: String): EvidenceAuditOutcome.ExpectedGap =
        EvidenceAuditOutcome.ExpectedGap(gap = gap, reason = reason)

    /**
     * Outcome builder for ICAO 9432 "should" / "avoid" obligations.
     *
     * Returns [EvidenceAuditOutcome.Advisory] — counted and rendered by the
     * audit report but NOT propagated to JUnit failures by
     * [EvidenceAuditReport.assertNoFailures]. Used for §2.8.3.2-style advisory
     * observations (clearance pacing during sensitive phases).
     *
     * Pass [violations] for the per-clearance diagnostic records, and
     * [reason] for the human-readable branch summary.
     */
    fun advisory(violations: List<AdvisoryViolation>, reason: String): EvidenceAuditOutcome.Advisory =
        EvidenceAuditOutcome.Advisory(violations = violations, reason = reason)

    fun activationFactIds(): Set<FactId> =
        activated.toSet()

    inline fun <reified I : AtcInstruction> instructions(aircraftId: AircraftId): EvidenceSelector =
        EvidenceSelector(
            label = I::class.simpleName ?: "Instruction",
            facts = facts.orderedFacts().filter { fact ->
                val instruction = fact.payload as? EvidenceFactPayload.Instruction ?: return@filter false
                instruction.aircraftId == aircraftId && instruction.instruction is I
            },
            activate = { factId -> activated += factId },
        )

    inline fun <reified E : ReportEvent> reports(aircraftId: AircraftId): EvidenceSelector =
        EvidenceSelector(
            label = E::class.simpleName ?: "Report",
            facts = facts.orderedFacts().filter { fact ->
                val report = fact.payload as? EvidenceFactPayload.PilotReport ?: return@filter false
                report.aircraftId == aircraftId && report.events.any { event -> event is E }
            },
            activate = { factId -> activated += factId },
        )

    fun aircraft(aircraftId: AircraftId): AuditAircraftSubject =
        AuditAircraftSubject(
            aircraftId = aircraftId,
            summaryFact = facts.orderedFacts().firstOrNull { fact ->
                val summary = fact.payload as? EvidenceFactPayload.AircraftSummary ?: return@firstOrNull false
                summary.aircraftId == aircraftId
            },
            activate = { factId -> activated += factId },
        )

    fun aerodromeInformation(aircraftId: AircraftId): AuditAerodromeInformationSubject =
        AuditAerodromeInformationSubject(
            aircraftId = aircraftId,
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun criticalPhase(aircraftId: AircraftId): AuditCriticalPhaseSubject =
        AuditCriticalPhaseSubject(
            aircraftId = aircraftId,
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun frequencyTransfer(aircraftId: AircraftId): AuditFrequencyTransferSubject =
        AuditFrequencyTransferSubject(
            aircraftId = aircraftId,
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun receptionDoubt(aircraftId: AircraftId): AuditReceptionDoubtSubject =
        AuditReceptionDoubtSubject(
            aircraftId = aircraftId,
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun clearancePacing(aircraftId: AircraftId): AuditClearancePacingSubject =
        AuditClearancePacingSubject(
            aircraftId = aircraftId,
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )
}

class AuditAircraftSubject internal constructor(
    private val aircraftId: AircraftId,
    private val summaryFact: EvidenceFact?,
    private val activate: (FactId) -> Unit,
) {
    fun isParkedAndComplete(): EvidenceAuditOutcome {
        val fact = summaryFact ?: return EvidenceAuditOutcome.Fail(
            reason = "Missing final aircraft summary for ${aircraftId.value}",
            evidence = emptyList(),
        )
        activate(fact.id)
        val summary = fact.payload as? EvidenceFactPayload.AircraftSummary ?: return EvidenceAuditOutcome.Fail(
            reason = "Aircraft summary fact had unexpected payload for ${aircraftId.value}",
            evidence = listOf(fact.id.value),
        )
        return if (summary.phase == PilotPhase.Parked && summary.missionComplete) {
            EvidenceAuditOutcome.Pass(listOf("phase=${summary.phase}", "missionComplete=${summary.missionComplete}"))
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Aircraft ${aircraftId.value} did not finish parked and complete",
                evidence = listOf("phase=${summary.phase}", "missionComplete=${summary.missionComplete}"),
            )
        }
    }
}

class EvidenceSelector @PublishedApi internal constructor(
    private val label: String,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun first(): AuditEvidencePoint =
        nth(0)

    fun nth(index: Int): AuditEvidencePoint {
        require(index >= 0) { "selector index must be non-negative" }
        val fact = facts.getOrNull(index)
        return if (fact == null) {
            AuditEvidencePoint.Missing("$label#$index")
        } else {
            activate(fact.id)
            AuditEvidencePoint.Present(label = "$label#$index", sequence = fact.provenance.sequence, factId = fact.id)
        }
    }

    fun exactly(count: Int): EvidenceAuditOutcome {
        require(count >= 0) { "expected count must be non-negative" }
        return if (facts.size == count) {
            facts.forEach { fact -> activate(fact.id) }
            EvidenceAuditOutcome.Pass(listOf("$label count=$count"))
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Expected exactly $count $label fact(s); got ${facts.size}",
                evidence = facts.map { fact -> "${fact.id.value}@${fact.provenance.sequence.value}" },
            )
        }
    }

    fun none(): EvidenceAuditOutcome =
        exactly(0)
}

class AuditAerodromeInformationSubject internal constructor(
    private val aircraftId: AircraftId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun beforeTaxi(): AuditAerodromeInformationContext =
        context(AerodromeInformationTimingContext.BeforeTaxi)

    fun beforeFinalApproach(): AuditAerodromeInformationContext =
        context(AerodromeInformationTimingContext.BeforeFinalApproach)

    private fun context(timingContext: AerodromeInformationTimingContext): AuditAerodromeInformationContext =
        AuditAerodromeInformationContext(
            aircraftId = aircraftId,
            timingContext = timingContext,
            facts = facts.filter { fact ->
                val information = fact.payload as? EvidenceFactPayload.AerodromeInformation
                    ?: return@filter false
                information.aircraftId == aircraftId && information.timingContext == timingContext
            },
            activate = activate,
        )
}

class AuditAerodromeInformationContext internal constructor(
    private val aircraftId: AircraftId,
    private val timingContext: AerodromeInformationTimingContext,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun wasPassedOrKnownReceived(): EvidenceAuditOutcome {
        val supportingFacts = facts.filter { fact ->
            val information = fact.payload as? EvidenceFactPayload.AerodromeInformation ?: return@filter false
            when (information.status) {
                AerodromeInformationStatus.KnownReceivedElsewhere,
                AerodromeInformationStatus.PassedByController,
                -> true
            }
        }
        return if (supportingFacts.isEmpty()) {
            EvidenceAuditOutcome.Fail(
                reason = "Missing aerodrome-information evidence for ${aircraftId.value} $timingContext",
                evidence = emptyList(),
            )
        } else {
            supportingFacts.forEach { fact -> activate(fact.id) }
            EvidenceAuditOutcome.Pass(
                supportingFacts.map { fact ->
                    val information = fact.payload as EvidenceFactPayload.AerodromeInformation
                    "${information.status}:${information.detail.value}@${fact.provenance.sequence.value}"
                },
            )
        }
    }
}

/**
 * Audit selector over [EvidenceFactPayload.FrequencyTransfer] facts filtered
 * by aircraft.
 *
 * Source: ICAO 9432 §2.8.2.1. Two branches:
 * - [controllerAdvised] requires at least one fact with mode
 *   [FrequencyTransferMode.ControllerAdvised] (FN44-GAP-1 closure path).
 * - [pilotNotified] requires at least one fact with mode
 *   [FrequencyTransferMode.PilotNotifiedAbsentAdvice] (FN44-GAP-2 closure
 *   path).
 *
 * Both branches return [EvidenceAuditOutcome.Fail] when no matching fact is
 * present, because §2.8.2.1 is mandatory on both arms ("shall") and the
 * regulation cannot be satisfied without an observation.
 */
class AuditFrequencyTransferSubject internal constructor(
    private val aircraftId: AircraftId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun controllerAdvised(): EvidenceAuditOutcome =
        outcomeForMode(
            mode = FrequencyTransferMode.ControllerAdvised,
            failReason = "Missing controller-advised frequency-transfer fact for ${aircraftId.value}",
        )

    fun pilotNotified(): EvidenceAuditOutcome =
        outcomeForMode(
            mode = FrequencyTransferMode.PilotNotifiedAbsentAdvice,
            failReason = "Missing pilot-notified frequency-change fact for ${aircraftId.value}",
        )

    private fun outcomeForMode(
        mode: FrequencyTransferMode,
        failReason: String,
    ): EvidenceAuditOutcome {
        val matching = facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.FrequencyTransfer ?: return@filter false
            payload.aircraftId == aircraftId && payload.mode == mode
        }
        if (matching.isEmpty()) {
            return EvidenceAuditOutcome.Fail(reason = failReason, evidence = emptyList())
        }
        matching.forEach { fact -> activate(fact.id) }
        return EvidenceAuditOutcome.Pass(
            evidence = matching.map { fact ->
                val payload = fact.payload as EvidenceFactPayload.FrequencyTransfer
                val target = when (val t = payload.target) {
                    is FrequencyTransferTarget.UnitOnly -> t.unitName
                    is FrequencyTransferTarget.UnitAndFrequency -> "${t.unitName}@${t.frequency}"
                }
                "${payload.mode}:$target@${fact.provenance.sequence.value}"
            },
        )
    }
}

/**
 * Audit selector over [EvidenceFactPayload.ReceptionDoubt] facts filtered
 * by aircraft.
 *
 * Source: ICAO 9432 §2.8.1.4 — *"If there is doubt that a message has been
 * correctly received, a repetition of the messages shall be requested
 * either in full or in part."*
 *
 * §2.8.1.4 is mandatory ("shall"). The single branch
 * [requiresRepetitionResponse] returns:
 *
 * - [EvidenceAuditOutcome.Fail] when **no** reception-doubt fact exists
 *   for the aircraft. This is the honest covered-red landing while the
 *   sim has no reception-quality signal infrastructure: regulation
 *   cannot be satisfied without an observation.
 * - [EvidenceAuditOutcome.Pass] when every doubt fact for the aircraft
 *   has a matching `protocol.SayAgain` resolution via
 *   [EvidenceFactPayload.ReceptionDoubt.resolvedBy]. The activation
 *   carries the matched doubt fact ids.
 * - [EvidenceAuditOutcome.Fail] when at least one doubt fact for the
 *   aircraft has no `resolvedBy` link (regulation explicitly requires
 *   "a repetition... shall be requested"). The failure evidence lists
 *   the unresolved doubt fact ids.
 *
 * The trigger (doubt) and the response (`SayAgain`) are distinct types
 * linked by a typed optional reference. The selector inspects the doubt
 * fact's `resolvedBy: SayAgainRef?` — it does not require `SayAgain` to
 * back-reference the doubt.
 */
class AuditReceptionDoubtSubject internal constructor(
    private val aircraftId: AircraftId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun requiresRepetitionResponse(): EvidenceAuditOutcome {
        val doubtFacts = facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.ReceptionDoubt ?: return@filter false
            payload.aircraftId == aircraftId
        }
        if (doubtFacts.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing reception-doubt evidence for ${aircraftId.value}",
                evidence = emptyList(),
            )
        }
        // Activate every doubt fact we examined so the source-case
        // activation check (AuditEvidenceCaseBuilder.toCase) preserves
        // this selector's specific Pass/Fail outcome instead of replacing
        // it with the generic "did not activate any evidence facts" Fail.
        // Activation reflects "the selector consulted these facts", not
        // "the regulation passed" — both Pass and Fail paths activate.
        doubtFacts.forEach { fact -> activate(fact.id) }
        val unresolved = doubtFacts.filter { fact ->
            val payload = fact.payload as EvidenceFactPayload.ReceptionDoubt
            payload.resolvedBy == null
        }
        return if (unresolved.isEmpty()) {
            EvidenceAuditOutcome.Pass(
                evidence = doubtFacts.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.ReceptionDoubt
                    val resolution = checkNotNull(payload.resolvedBy)
                    "${payload.doubtSource.label}@${fact.provenance.sequence.value}->${resolution}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Reception-doubt observations without repetition response for ${aircraftId.value}",
                evidence = unresolved.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.ReceptionDoubt
                    "${payload.doubtSource.label}@${fact.provenance.sequence.value}"
                },
            )
        }
    }
}

/**
 * Audit selector over [EvidenceFactPayload.ClearancePacing] facts filtered
 * by aircraft.
 *
 * Source: ICAO 9432 §2.8.3.2 — *"Controllers should pass a clearance slowly
 * and clearly … should avoid passing a clearance to a pilot engaged in
 * complicated taxiing manoeuvres … on no occasion should a clearance be
 * passed when the pilot is engaged in line up or take-off manoeuvres."*
 *
 * §2.8.3.2 is **advisory** ("should" / "avoid" / "on no occasion"). The
 * single branch [whenIssuedDuring] returns:
 *
 * - [EvidenceAuditOutcome.Fail] when **no** clearance-pacing fact exists
 *   for the aircraft — regulation cannot be evaluated without an
 *   observation. This is the regulation-cannot-apply leg, surfaced as
 *   Fail per the existing "missing evidence → Fail" convention.
 * - [EvidenceAuditOutcome.Advisory] when at least one pacing fact for the
 *   aircraft observes a clearance issued during the sensitive [windows]
 *   passed in. Carries one [AdvisoryViolation] per offending clearance.
 *   Does NOT propagate to JUnit failure via [EvidenceAuditReport.assertNoFailures].
 * - [EvidenceAuditOutcome.Pass] when pacing facts exist but none fall into
 *   the sensitive windows — the controller paced clearances acceptably.
 *
 * Activation discipline (per memory
 * `bug/test-failures/audit-selectors-must-activate-examined-2026-05-26`):
 * once the aircraft-filtered facts are determined, [activate] is called for
 * every consulted fact BEFORE branching into Advisory / Pass / Fail.
 * Otherwise [AuditEvidenceCaseBuilder.toCase] would override the
 * regulation-specific outcome with the generic
 * "did not activate any evidence facts" Fail. The "no facts at all → Fail"
 * path stays un-activated because that path *correctly* surfaces as the
 * generic activation-check Fail.
 *
 * **Distinctness from POLICY-1**: this selector observes *conditions*
 * (clearances issued during phase X), NOT *prescriptive timing rules*
 * (controller MUST wait until phase Y). If design pressure pushes toward
 * encoding prescriptive timing, halt — POLICY-1 territory.
 */
class AuditClearancePacingSubject internal constructor(
    private val aircraftId: AircraftId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun whenIssuedDuring(windows: List<PacingWindow>): EvidenceAuditOutcome {
        val pacingFacts = facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.ClearancePacing ?: return@filter false
            payload.aircraftId == aircraftId
        }
        if (pacingFacts.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing clearance-pacing evidence for ${aircraftId.value}",
                evidence = emptyList(),
            )
        }
        // Activate every pacing fact we examined so the source-case
        // activation check (AuditEvidenceCaseBuilder.toCase) preserves
        // this selector's specific Advisory/Pass outcome instead of
        // replacing it with the generic "did not activate any evidence
        // facts" Fail. Activation reflects "the selector consulted these
        // facts", not "the regulation passed" — Advisory AND Pass paths
        // activate.
        pacingFacts.forEach { fact -> activate(fact.id) }
        val sensitive = windows.toSet()
        val violations = pacingFacts.mapNotNull { fact ->
            val payload = fact.payload as EvidenceFactPayload.ClearancePacing
            if (payload.issuedDuring in sensitive) {
                AdvisoryViolation(
                    clearanceRef = payload.clearanceRef,
                    observedWindow = payload.issuedDuring,
                )
            } else {
                null
            }
        }
        return if (violations.isEmpty()) {
            EvidenceAuditOutcome.Pass(
                evidence = pacingFacts.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.ClearancePacing
                    "${payload.issuedDuring}@${fact.provenance.sequence.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Advisory(
                violations = violations,
                reason = "Clearance(s) issued during sensitive pacing window(s) for ${aircraftId.value}",
            )
        }
    }
}

class AuditCriticalPhaseSubject internal constructor(
    private val aircraftId: AircraftId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun routineControllerTransmissions(): AuditCriticalPhaseRoutineTransmissions =
        AuditCriticalPhaseRoutineTransmissions(
            aircraftId = aircraftId,
            facts = facts,
            activate = activate,
        )
}

class AuditCriticalPhaseRoutineTransmissions internal constructor(
    private val aircraftId: AircraftId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun none(): EvidenceAuditOutcome {
        val windows = facts.filter { fact ->
            val window = fact.payload as? EvidenceFactPayload.CriticalPhaseWindow ?: return@filter false
            window.aircraftId == aircraftId
        }
        if (windows.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing critical-phase window evidence for ${aircraftId.value}",
                evidence = emptyList(),
            )
        }
        val routineTransmissions = facts.filter { fact ->
            val transmission = fact.payload as? EvidenceFactPayload.CriticalPhaseTransmission
                ?: return@filter false
            transmission.aircraftId == aircraftId && transmission.necessity == TransmissionNecessity.Routine
        }
        windows.forEach { fact -> activate(fact.id) }
        routineTransmissions.forEach { fact -> activate(fact.id) }
        return if (routineTransmissions.isEmpty()) {
            EvidenceAuditOutcome.Pass(
                windows.map { fact ->
                    val window = fact.payload as EvidenceFactPayload.CriticalPhaseWindow
                    "${window.phase}:${window.start.value}-${window.end.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Observed routine controller transmission(s) during critical phase",
                evidence = routineTransmissions.map { fact -> fact.id.value },
            )
        }
    }
}

sealed interface AuditEvidencePoint {
    val label: String

    data class Present(
        override val label: String,
        val sequence: EvidenceSequence,
        val factId: FactId,
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
            val evaluation = case.evaluate(facts)
            EvidenceAuditResult(
                id = case.id,
                claimKind = case.claimKind,
                sources = case.sources,
                samples = case.samples,
                outcome = evaluation.outcome,
                activationFactIds = evaluation.activationFactIds,
            )
        },
    )

private fun EvidenceAuditOutcome.label(): String =
    when (this) {
        is EvidenceAuditOutcome.Advisory -> "advisory(${violations.size}): $reason"
        is EvidenceAuditOutcome.ExpectedGap -> "expected_gap(${gap.metadata.id}): $reason"
        is EvidenceAuditOutcome.Fail -> "fail: $reason"
        is EvidenceAuditOutcome.Pass -> "pass: ${evidence.joinToString()}"
        is EvidenceAuditOutcome.Vacuous -> "vacuous: $reason"
    }
