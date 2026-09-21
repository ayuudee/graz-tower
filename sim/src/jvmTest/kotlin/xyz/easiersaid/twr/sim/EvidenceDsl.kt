package xyz.easiersaid.twr.sim

import kotlin.test.fail
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AircraftType
import xyz.easiersaid.twr.protocol.AtomicReadback
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.AirTaxiTo
import xyz.easiersaid.twr.protocol.BacktrackRunway
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.CrossRunway
import xyz.easiersaid.twr.protocol.ExpediteTaxi
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.GiveWayToTraffic
import xyz.easiersaid.twr.protocol.GroundInstruction
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.PushbackApproved
import xyz.easiersaid.twr.protocol.PushbackFace
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.ReduceTaxiSpeed
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.StartupApproved
import xyz.easiersaid.twr.protocol.StopImmediately
import xyz.easiersaid.twr.protocol.TaxiClearance
import xyz.easiersaid.twr.protocol.TaxiIntoHoldingBay
import xyz.easiersaid.twr.protocol.TaxiToHoldingPoint
import xyz.easiersaid.twr.protocol.TaxiToStand
import xyz.easiersaid.twr.protocol.TaxiViaRunway
import xyz.easiersaid.twr.protocol.TaxiWithCaution
import xyz.easiersaid.twr.protocol.VacateRunway
import xyz.easiersaid.twr.protocol.requiredReadbackAtoms

enum class EvidenceClaimKind {
    StructuralProtocolRequirement,
    StructuralEvidenceVocabulary,
    SyntheticRenderedPhraseologyExample,
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

    fun sourceVocabulary(
        id: String,
        build: AuditEvidenceCaseBuilder.() -> Unit,
    ) {
        cases += AuditEvidenceCaseBuilder(id = id, claimKind = EvidenceClaimKind.StructuralEvidenceVocabulary)
            .apply(build)
            .toCase(requireSources = true)
    }

    fun sourceRenderedExample(
        id: String,
        build: AuditEvidenceCaseBuilder.() -> Unit,
    ) {
        cases += AuditEvidenceCaseBuilder(id = id, claimKind = EvidenceClaimKind.SyntheticRenderedPhraseologyExample)
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

    fun essentialAerodromeInformation(): AuditEssentialAerodromeInformationSubject =
        AuditEssentialAerodromeInformationSubject(
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun essentialAerodromeInformationPhraseology(): AuditEssentialAerodromeInformationPhraseologySubject =
        AuditEssentialAerodromeInformationPhraseologySubject(
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun communicationPhraseologyExamples(): AuditCommunicationPhraseologyExampleSubject =
        AuditCommunicationPhraseologyExampleSubject(
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

    fun groundStationTestSignals(): AuditGroundStationTestSignalSubject =
        AuditGroundStationTestSignalSubject(
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun renderedPhraseology(aircraftId: AircraftId): AuditRenderedPhraseologySubject =
        AuditRenderedPhraseologySubject(
            aircraftId = aircraftId,
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun renderedPilotReadbackPhraseology(aircraftId: AircraftId): AuditRenderedPilotReadbackPhraseologySubject =
        AuditRenderedPilotReadbackPhraseologySubject(
            aircraftId = aircraftId,
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun renderedPilotReportPhraseology(aircraftId: AircraftId): AuditRenderedPilotReportPhraseologySubject =
        AuditRenderedPilotReportPhraseologySubject(
            aircraftId = aircraftId,
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun afterLandingPhraseology(aircraftId: AircraftId): AuditAfterLandingPhraseologySubject =
        AuditAfterLandingPhraseologySubject(
            aircraftId = aircraftId,
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun renderedVehicleDriverPhraseology(vehicleId: VehicleId): AuditRenderedVehicleDriverPhraseologySubject =
        AuditRenderedVehicleDriverPhraseologySubject(
            vehicleId = vehicleId,
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun operationalPolicy(): AuditOperationalPolicySubject =
        AuditOperationalPolicySubject(
            facts = facts.orderedFacts(),
            activate = { factId -> activated += factId },
        )

    fun taxiInstructions(): AuditTaxiInstructionSubject =
        AuditTaxiInstructionSubject(
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

class AuditEssentialAerodromeInformationSubject internal constructor(
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun includes(
        category: EssentialAerodromeInformationCategory,
        facets: Set<EssentialAerodromeInformationFacet> = emptySet(),
    ): EvidenceAuditOutcome {
        val informationFacts = facts.filter { fact ->
            fact.payload is EvidenceFactPayload.EssentialAerodromeInformation
        }
        if (informationFacts.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing essential-aerodrome-information category evidence",
                evidence = emptyList(),
            )
        }
        informationFacts.forEach { fact -> activate(fact.id) }
        val matching = informationFacts.filter { fact ->
            val information = fact.payload as EvidenceFactPayload.EssentialAerodromeInformation
            information.category == category &&
                information.facets.containsAll(facets) &&
                information.safetyRelevance == EssentialAerodromeInformationSafetyRelevance.NecessaryForSafeOperation
        }
        return if (matching.isNotEmpty()) {
            EvidenceAuditOutcome.Pass(
                matching.map { fact ->
                    val information = fact.payload as EvidenceFactPayload.EssentialAerodromeInformation
                    "${information.category}:${information.domain}:${information.facets.sortedBy { facet -> facet.name }}:" +
                        information.detail.value
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Missing essential-aerodrome-information category $category with facet(s) " +
                    facets.sortedBy { facet -> facet.name },
                evidence = informationFacts.map { fact ->
                    val information = fact.payload as EvidenceFactPayload.EssentialAerodromeInformation
                    "${information.category}:${information.domain}:${information.facets.sortedBy { facet -> facet.name }}:" +
                        information.detail.value
                },
            )
        }
    }

    fun domainsInclude(domains: Set<EssentialAerodromeInformationDomain>): EvidenceAuditOutcome {
        require(domains.isNotEmpty()) { "expected essential-information domains must not be empty" }
        val informationFacts = facts.filter { fact ->
            fact.payload is EvidenceFactPayload.EssentialAerodromeInformation
        }
        if (informationFacts.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing essential-aerodrome-information domain evidence",
                evidence = emptyList(),
            )
        }
        informationFacts.forEach { fact -> activate(fact.id) }
        val safetyRelevantFacts = informationFacts.filter { fact ->
            val information = fact.payload as EvidenceFactPayload.EssentialAerodromeInformation
            information.safetyRelevance == EssentialAerodromeInformationSafetyRelevance.NecessaryForSafeOperation
        }
        val observedDomains = safetyRelevantFacts
            .map { fact -> (fact.payload as EvidenceFactPayload.EssentialAerodromeInformation).domain }
            .toSet()
        val missing = domains - observedDomains
        return if (missing.isEmpty()) {
            EvidenceAuditOutcome.Pass(observedDomains.map { domain -> domain.name }.sorted())
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Missing essential-aerodrome-information domain(s): ${missing.joinToString()}",
                evidence = observedDomains.map { domain -> domain.name }.sorted(),
            )
        }
    }
}

class AuditEssentialAerodromeInformationPhraseologySubject internal constructor(
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun exampleBlock(): EvidenceAuditOutcome {
        val phraseologyFacts = facts.filter { fact ->
            fact.payload is EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology
        }
        if (phraseologyFacts.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing rendered essential-aerodrome-information example phraseology",
                evidence = emptyList(),
            )
        }
        phraseologyFacts.forEach { fact -> activate(fact.id) }
        return if (phraseologyFacts.map { fact -> fact.payload }.toList() == expectedPayloads) {
            EvidenceAuditOutcome.Pass(
                phraseologyFacts.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology
                    "${payload.template}:${payload.text.value}@${fact.provenance.sequence.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Rendered essential-aerodrome-information examples did not match the exact source block",
                evidence = phraseologyFacts.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology
                    "${payload.template}:${payload.tokens}:${payload.text.value}@${fact.provenance.sequence.value}"
                },
            )
        }
    }

    private companion object {
        val expectedPayloads: List<EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology> =
            listOf(
                essentialCautionConstructionWorkExample(),
                essentialCentreLineLightingUnserviceableExample(),
                essentialRunwayConditionExample(),
            )
    }
}

fun essentialCautionConstructionWorkExample(): EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology =
    EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology(
        template = RenderedEssentialAerodromeInformationPhraseologyTemplate.CautionConstructionWorkAdjacentToGate,
        tokens = listOf(
            EssentialAerodromeInformationPhraseologyToken.AircraftCallsign(AircraftId("FASTAIR 345")),
            EssentialAerodromeInformationPhraseologyToken.Caution,
            EssentialAerodromeInformationPhraseologyToken.ConstructionWork,
            EssentialAerodromeInformationPhraseologyToken.AdjacentTo,
            EssentialAerodromeInformationPhraseologyToken.Gate("37"),
        ),
        text = RenderedPhraseText("FASTAIR 345 CAUTION CONSTRUCTION WORK ADJACENT TO GATE 37"),
    )

fun essentialCentreLineLightingUnserviceableExample():
    EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology =
    EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology(
        template = RenderedEssentialAerodromeInformationPhraseologyTemplate.CentreLineTaxiwayLightingUnserviceable,
        tokens = listOf(
            EssentialAerodromeInformationPhraseologyToken.CentreLine,
            EssentialAerodromeInformationPhraseologyToken.TaxiwayLighting,
            EssentialAerodromeInformationPhraseologyToken.Unserviceable,
        ),
        text = RenderedPhraseText("CENTRE LINE TAXIWAY LIGHTING UNSERVICEABLE"),
    )

fun essentialRunwayConditionExample(): EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology =
    EvidenceFactPayload.RenderedEssentialAerodromeInformationPhraseology(
        template = RenderedEssentialAerodromeInformationPhraseologyTemplate.RunwayConditionReport,
        tokens = listOf(
            EssentialAerodromeInformationPhraseologyToken.RunwayConditions,
            EssentialAerodromeInformationPhraseologyToken.RunwayDesignator(RunwayId("09")),
            EssentialAerodromeInformationPhraseologyToken.AvailableWidth,
            EssentialAerodromeInformationPhraseologyToken.WidthMetres(32),
            EssentialAerodromeInformationPhraseologyToken.CoveredWithThinPatchesOfIce,
            EssentialAerodromeInformationPhraseologyToken.BrakingActionPoor,
        ),
        text = RenderedPhraseText(
            "RUNWAY CONDITIONS 09: AVAILABLE WIDTH 32 METRES, " +
                "COVERED WITH THIN PATCHES OF ICE, BRAKING ACTION POOR",
        ),
    )

class AuditCommunicationPhraseologyExampleSubject internal constructor(
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun exampleBlock(): EvidenceAuditOutcome {
        val phraseologyFacts = facts.filter { fact ->
            fact.payload is EvidenceFactPayload.RenderedCommunicationPhraseologyExample
        }
        if (phraseologyFacts.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing rendered communication phraseology examples",
                evidence = emptyList(),
            )
        }
        phraseologyFacts.forEach { fact -> activate(fact.id) }
        return if (phraseologyFacts.map { fact -> fact.payload } == expectedPayloads) {
            EvidenceAuditOutcome.Pass(
                phraseologyFacts.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedCommunicationPhraseologyExample
                    "${payload.template}:${payload.text.value}@${fact.provenance.sequence.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Rendered communication phraseology examples did not match the exact source block",
                evidence = phraseologyFacts.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedCommunicationPhraseologyExample
                    "${payload.template}:${payload.tokens}:${payload.text.value}@${fact.provenance.sequence.value}"
                },
            )
        }
    }

    private companion object {
        val expectedPayloads: List<EvidenceFactPayload.RenderedCommunicationPhraseologyExample> =
            listOf(
                communicationInitialContactStationThenAircraftExample(),
                communicationInitialContactAircraftThenStationExample(),
                communicationGroundStationAllStationsExample(),
                communicationAircraftAllStationsExample(),
            )
    }
}

fun communicationInitialContactStationThenAircraftExample(): EvidenceFactPayload.RenderedCommunicationPhraseologyExample =
    EvidenceFactPayload.RenderedCommunicationPhraseologyExample(
        template = RenderedCommunicationPhraseologyTemplate.InitialContactStationThenAircraft,
        tokens = listOf(
            CommunicationPhraseologyToken.StationCallsign("STEPHENVILLE TOWER"),
            CommunicationPhraseologyToken.AircraftCallsign(AircraftId("G-ABCD")),
        ),
        text = RenderedPhraseText("STEPHENVILLE TOWER G-ABCD"),
    )

fun communicationInitialContactAircraftThenStationExample(): EvidenceFactPayload.RenderedCommunicationPhraseologyExample =
    EvidenceFactPayload.RenderedCommunicationPhraseologyExample(
        template = RenderedCommunicationPhraseologyTemplate.InitialContactAircraftThenStation,
        tokens = listOf(
            CommunicationPhraseologyToken.AircraftCallsign(AircraftId("G-ABCD")),
            CommunicationPhraseologyToken.StationCallsign("STEPHENVILLE TOWER"),
        ),
        text = RenderedPhraseText("G-ABCD STEPHENVILLE TOWER"),
    )

fun communicationGroundStationAllStationsExample(): EvidenceFactPayload.RenderedCommunicationPhraseologyExample =
    EvidenceFactPayload.RenderedCommunicationPhraseologyExample(
        template = RenderedCommunicationPhraseologyTemplate.GroundStationAllStationsBroadcast,
        tokens = listOf(
            CommunicationPhraseologyToken.AllStations,
            CommunicationPhraseologyToken.StationCallsign("ALEXANDER CONTROL"),
            CommunicationPhraseologyToken.BroadcastContent("FUEL DUMPING COMPLETED"),
        ),
        text = RenderedPhraseText("ALL STATIONS ALEXANDER CONTROL, FUEL DUMPING COMPLETED"),
    )

fun communicationAircraftAllStationsExample(): EvidenceFactPayload.RenderedCommunicationPhraseologyExample =
    EvidenceFactPayload.RenderedCommunicationPhraseologyExample(
        template = RenderedCommunicationPhraseologyTemplate.AircraftAllStationsBroadcast,
        tokens = listOf(
            CommunicationPhraseologyToken.AllStations,
            CommunicationPhraseologyToken.AircraftCallsign(AircraftId("G-CDAB")),
            CommunicationPhraseologyToken.BroadcastContent(
                "WESTBOUND MARLO VOR TO STEPHENVILLE LEAVING FL 260 DESCENDING FL 150",
            ),
        ),
        text = RenderedPhraseText(
            "ALL STATIONS G-CDAB WESTBOUND MARLO VOR TO STEPHENVILLE " +
                "LEAVING FL 260 DESCENDING FL 150",
        ),
    )

/**
 * Audit selector over [EvidenceFactPayload.FrequencyTransfer] facts filtered
 * by aircraft.
 *
 * Source: ICAO 9432 §2.8.2.1. Two branches:
 * - [controllerAdvised] requires at least one fact with mode
 *   [FrequencyTransferMode.ControllerAdvised] (FN44-GAP-1 closure path).
 * - [pilotNotified] requires at least one fact with mode
 *   [FrequencyTransferMode.PilotNotifiedAbsentAdvice] (pilot-notified closure
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

class AuditGroundStationTestSignalSubject internal constructor(
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun allWithin(maxDuration: SimDuration): EvidenceAuditOutcome {
        val signals = facts.filter { fact ->
            fact.payload is EvidenceFactPayload.GroundStationTestSignal
        }
        if (signals.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing ground-station test-signal evidence",
                evidence = emptyList(),
            )
        }
        signals.forEach { fact -> activate(fact.id) }
        val overLimit = signals.filter { fact ->
            val signal = fact.payload as EvidenceFactPayload.GroundStationTestSignal
            signal.duration > maxDuration
        }
        return if (overLimit.isEmpty()) {
            EvidenceAuditOutcome.Pass(
                signals.map { fact ->
                    val signal = fact.payload as EvidenceFactPayload.GroundStationTestSignal
                    "${signal.stationId.value}:${signal.duration.millis}ms"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Ground-station test signal exceeded ${maxDuration.millis}ms",
                evidence = overLimit.map { fact -> fact.id.value },
            )
        }
    }
}

class AuditRenderedPhraseologySubject internal constructor(
    private val aircraftId: AircraftId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun takeoffClearance(runway: RunwayId): EvidenceAuditOutcome =
        phraseologyOutcome(
            template = RenderedPhraseologyTemplate.TakeoffClearance,
            expectedObligationKinds = renderedClearanceObligations,
            expectedTokens = listOf(
                PhraseologyToken.AircraftCallsign(aircraftId),
                PhraseologyToken.Runway,
                PhraseologyToken.RunwayDesignator(runway),
                PhraseologyToken.Cleared,
                PhraseologyToken.For,
                PhraseologyToken.TakeOff,
            ),
            failReason = "Missing rendered take-off clearance phraseology for ${aircraftId.value} runway ${runway.value}",
        )

    fun touchAndGoClearance(): EvidenceAuditOutcome =
        phraseologyOutcome(
            template = RenderedPhraseologyTemplate.TouchAndGoClearance,
            expectedObligationKinds = renderedClearanceObligations,
            expectedTokens = listOf(
                PhraseologyToken.AircraftCallsign(aircraftId),
                PhraseologyToken.Cleared,
                PhraseologyToken.Touch,
                PhraseologyToken.And,
                PhraseologyToken.Go,
            ),
            failReason = "Missing rendered touch-and-go clearance phraseology for ${aircraftId.value}",
        )

    fun lineUpAndWait(runway: RunwayId): EvidenceAuditOutcome =
        phraseologyOutcome(
            template = RenderedPhraseologyTemplate.LineUpAndWaitInstruction,
            expectedObligationKinds = renderedClearanceObligations,
            expectedTokens = listOf(
                PhraseologyToken.AircraftCallsign(aircraftId),
                PhraseologyToken.Runway,
                PhraseologyToken.RunwayDesignator(runway),
                PhraseologyToken.Line,
                PhraseologyToken.Up,
                PhraseologyToken.And,
                PhraseologyToken.Wait,
            ),
            failReason = "Missing rendered line-up-and-wait phraseology for ${aircraftId.value} runway ${runway.value}",
        )

    fun contactFrequency(unitName: String, frequency: Frequency): EvidenceAuditOutcome =
        phraseologyOutcome(
            template = RenderedPhraseologyTemplate.ContactFrequencyInstruction,
            expectedObligationKinds = setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
                PhraseologyObligationKind.Readback,
            ),
            expectedTokens = listOf(
                PhraseologyToken.AircraftCallsign(aircraftId),
                PhraseologyToken.Contact,
                PhraseologyToken.UnitName(unitName),
                PhraseologyToken.FrequencyValue(frequency),
            ),
            failReason = "Missing rendered contact-frequency phraseology for ${aircraftId.value} $unitName ${frequency.mhz}",
        )

    fun stopImmediately(): EvidenceAuditOutcome =
        phraseologyOutcome(
            template = RenderedPhraseologyTemplate.StopImmediatelyInstruction,
            expectedObligationKinds = setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
            ),
            expectedTokens = listOf(
                PhraseologyToken.AircraftCallsign(aircraftId),
                PhraseologyToken.Stop,
                PhraseologyToken.Immediately,
                PhraseologyToken.AircraftCallsign(aircraftId),
                PhraseologyToken.Stop,
                PhraseologyToken.Immediately,
            ),
            failReason = "Missing rendered stop-immediately phraseology for ${aircraftId.value}",
        )

    fun taxiToStand(destination: PointId, via: List<PointId>): EvidenceAuditOutcome =
        phraseologyOutcome(
            template = RenderedPhraseologyTemplate.TaxiToStandInstruction,
            expectedObligationKinds = setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
                PhraseologyObligationKind.Readback,
            ),
            expectedTokens = listOf(
                PhraseologyToken.AircraftCallsign(aircraftId),
                PhraseologyToken.Taxi,
                PhraseologyToken.To,
            ) + routeTokens(destination = destination, via = via),
            failReason = "Missing rendered taxi-to-stand phraseology for ${aircraftId.value} to ${destination.value}",
        )

    fun takeOffWordOnlyInTakeoffClearanceAcrossSupportedTemplates(): EvidenceAuditOutcome {
        val candidates = facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.RenderedPhraseology ?: return@filter false
            payload.aircraftId == aircraftId && payload.template in supportedControllerPhraseologyTemplates
        }
        val observedTemplates = candidates
            .map { fact -> (fact.payload as EvidenceFactPayload.RenderedPhraseology).template }
            .toSet()
        val missingTemplates = supportedControllerPhraseologyTemplates - observedTemplates
        if (missingTemplates.isNotEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing supported rendered controller phraseology templates: $missingTemplates",
                evidence = candidates.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPhraseology
                    "${payload.template}@${fact.provenance.sequence.value}"
                },
            )
        }
        candidates.forEach { fact -> activate(fact.id) }
        val violations = candidates.filter { fact ->
            val payload = fact.payload as EvidenceFactPayload.RenderedPhraseology
            payload.template != RenderedPhraseologyTemplate.TakeoffClearance &&
                PhraseologyToken.TakeOff in payload.tokens
        }
        return if (violations.isEmpty()) {
            EvidenceAuditOutcome.Pass(
                candidates.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPhraseology
                    "${payload.template}:takeoff-token-ok@${fact.provenance.sequence.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "TAKE OFF token appeared outside take-off clearance phraseology",
                evidence = violations.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPhraseology
                    "${payload.template}:${payload.tokens}@${fact.provenance.sequence.value}"
                },
            )
        }
    }

    private fun phraseologyOutcome(
        template: RenderedPhraseologyTemplate,
        expectedObligationKinds: Set<PhraseologyObligationKind>,
        expectedTokens: List<PhraseologyToken>,
        failReason: String,
    ): EvidenceAuditOutcome {
        val candidates = facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.RenderedPhraseology ?: return@filter false
            payload.aircraftId == aircraftId && payload.template == template
        }
        if (candidates.isEmpty()) {
            return EvidenceAuditOutcome.Fail(reason = failReason, evidence = emptyList())
        }
        candidates.forEach { fact -> activate(fact.id) }
        val matching = candidates.filter { fact ->
            val payload = fact.payload as EvidenceFactPayload.RenderedPhraseology
            payload.tokens == expectedTokens && payload.obligationKinds.containsAll(expectedObligationKinds)
        }
        return if (matching.isNotEmpty()) {
            EvidenceAuditOutcome.Pass(
                matching.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPhraseology
                    "${payload.template}:${payload.text.value}@${fact.provenance.sequence.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = failReason,
                evidence = candidates.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPhraseology
                    "${payload.template}:${payload.obligationKinds}:${payload.tokens}@${fact.provenance.sequence.value}"
                },
            )
        }
    }

    private companion object {
        val supportedControllerPhraseologyTemplates: Set<RenderedPhraseologyTemplate> =
            setOf(
                RenderedPhraseologyTemplate.ContactFrequencyInstruction,
                RenderedPhraseologyTemplate.AfterLandingVacateViaInstruction,
                RenderedPhraseologyTemplate.AirTaxiToInstruction,
                RenderedPhraseologyTemplate.LineUpAndWaitInstruction,
                RenderedPhraseologyTemplate.TakeoffClearance,
                RenderedPhraseologyTemplate.TouchAndGoClearance,
                RenderedPhraseologyTemplate.StopImmediatelyInstruction,
                RenderedPhraseologyTemplate.TaxiToStandInstruction,
            )

        val renderedClearanceObligations: Set<PhraseologyObligationKind> =
            setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
                PhraseologyObligationKind.ForbiddenMeaning,
            )
    }
}

class AuditRenderedPilotReadbackPhraseologySubject internal constructor(
    private val aircraftId: AircraftId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun lineUpReadback(): EvidenceAuditOutcome {
        val expectedObligationKinds = readbackObligations
        val expectedTokens = listOf(
            PhraseologyToken.Lining,
            PhraseologyToken.Up,
            PhraseologyToken.AircraftCallsign(aircraftId),
        )
        return readbackPhraseologyOutcome(
            template = RenderedPhraseologyTemplate.LineUpReadback,
            expectedObligationKinds = expectedObligationKinds,
            expectedTokens = expectedTokens,
            failReason = "Missing rendered line-up readback phraseology for ${aircraftId.value}",
        )
    }

    fun frequencyReadback(frequency: Frequency): EvidenceAuditOutcome {
        val expectedTokens = listOf(
            PhraseologyToken.FrequencyValue(frequency),
            PhraseologyToken.AircraftCallsign(aircraftId),
        )
        return readbackPhraseologyOutcome(
            template = RenderedPhraseologyTemplate.FrequencyReadback,
            expectedObligationKinds = readbackObligations,
            expectedTokens = expectedTokens,
            failReason = "Missing rendered frequency readback phraseology for ${aircraftId.value} ${frequency.mhz}",
        )
    }

    fun taxiRouteReadback(destination: PointId, via: List<PointId>): EvidenceAuditOutcome =
        readbackPhraseologyOutcome(
            template = RenderedPhraseologyTemplate.TaxiRouteReadback,
            expectedObligationKinds = readbackObligations,
            expectedTokens = routeTokens(destination = destination, via = via) +
                PhraseologyToken.AircraftCallsign(aircraftId),
            failReason = "Missing rendered taxi-route readback phraseology for ${aircraftId.value} to ${destination.value}",
        )

    fun airTaxiRouteReadback(): EvidenceAuditOutcome =
        readbackPhraseologyOutcome(
            template = RenderedPhraseologyTemplate.AirTaxiRouteReadback,
            expectedObligationKinds = readbackObligations,
            expectedTokens = airTaxiRouteReadbackTokens(aircraftId),
            failReason = "Missing rendered air-taxi route readback phraseology for ${aircraftId.value}",
        )

    fun lineUpReadbackTerminatesWithCallsign(): EvidenceAuditOutcome =
        readbackTerminatesWithCallsign(
            template = RenderedPhraseologyTemplate.LineUpReadback,
            failReason = "Missing rendered line-up readback phraseology for ${aircraftId.value}",
        )

    fun frequencyReadbackTerminatesWithCallsign(): EvidenceAuditOutcome =
        readbackTerminatesWithCallsign(
            template = RenderedPhraseologyTemplate.FrequencyReadback,
            failReason = "Missing rendered frequency readback phraseology for ${aircraftId.value}",
        )

    private fun readbackPhraseologyOutcome(
        template: RenderedPhraseologyTemplate,
        expectedObligationKinds: Set<PhraseologyObligationKind>,
        expectedTokens: List<PhraseologyToken>,
        failReason: String,
    ): EvidenceAuditOutcome {
        val candidates = facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.RenderedPilotReadbackPhraseology ?: return@filter false
            payload.aircraftId == aircraftId && payload.template == template
        }
        if (candidates.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = failReason,
                evidence = emptyList(),
            )
        }
        candidates.forEach { fact -> activate(fact.id) }
        val matching = candidates.filter { fact ->
            val payload = fact.payload as EvidenceFactPayload.RenderedPilotReadbackPhraseology
            payload.tokens == expectedTokens && payload.obligationKinds.containsAll(expectedObligationKinds)
        }
        return if (matching.isNotEmpty()) {
            EvidenceAuditOutcome.Pass(
                matching.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPilotReadbackPhraseology
                    "${payload.template}:${payload.text.value}@${fact.provenance.sequence.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Rendered pilot readback phraseology did not match expected tokens",
                evidence = candidates.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPilotReadbackPhraseology
                    "${payload.template}:${payload.obligationKinds}:${payload.tokens}@${fact.provenance.sequence.value}"
                },
            )
        }
    }

    private fun readbackTerminatesWithCallsign(
        template: RenderedPhraseologyTemplate,
        failReason: String,
    ): EvidenceAuditOutcome {
        val candidates = facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.RenderedPilotReadbackPhraseology ?: return@filter false
            payload.aircraftId == aircraftId && payload.template == template
        }
        if (candidates.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = failReason,
                evidence = emptyList(),
            )
        }
        candidates.forEach { fact -> activate(fact.id) }
        val expectedFinalToken = PhraseologyToken.AircraftCallsign(aircraftId)
        val nonTerminating = candidates.filterNot { fact ->
            val payload = fact.payload as EvidenceFactPayload.RenderedPilotReadbackPhraseology
            payload.tokens.lastOrNull() == expectedFinalToken
        }
        return if (nonTerminating.isEmpty()) {
            EvidenceAuditOutcome.Pass(
                candidates.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPilotReadbackPhraseology
                    "${payload.template}:terminates-with-callsign:${payload.text.value}@${fact.provenance.sequence.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Rendered pilot readback phraseology did not terminate with ${aircraftId.value}",
                evidence = nonTerminating.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPilotReadbackPhraseology
                    "${payload.template}:${payload.tokens}@${fact.provenance.sequence.value}"
                },
            )
        }
    }

    private companion object {
        val readbackObligations: Set<PhraseologyObligationKind> =
            setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.Readback,
                PhraseologyObligationKind.SemanticSlot,
            )
    }
}

class AuditRenderedPilotReportPhraseologySubject internal constructor(
    private val aircraftId: AircraftId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun finalReport(): EvidenceAuditOutcome =
        reportPhraseologyOutcome(
            template = RenderedPhraseologyTemplate.FinalReport,
            expectedTokens = listOf(PhraseologyToken.Final),
            failReason = "Missing rendered FINAL report phraseology for ${aircraftId.value}",
        )

    fun longFinalReport(): EvidenceAuditOutcome =
        reportPhraseologyOutcome(
            template = RenderedPhraseologyTemplate.LongFinalReport,
            expectedTokens = listOf(PhraseologyToken.Long, PhraseologyToken.Final),
            failReason = "Missing rendered LONG FINAL report phraseology for ${aircraftId.value}",
        )

    fun runwayVacatedReport(): EvidenceAuditOutcome =
        reportPhraseologyOutcome(
            template = RenderedPhraseologyTemplate.RunwayVacatedReport,
            expectedTokens = listOf(PhraseologyToken.Runway, PhraseologyToken.Vacated),
            failReason = "Missing rendered RUNWAY VACATED report phraseology for ${aircraftId.value}",
        )

    private fun reportPhraseologyOutcome(
        template: RenderedPhraseologyTemplate,
        expectedTokens: List<PhraseologyToken>,
        failReason: String,
    ): EvidenceAuditOutcome {
        val candidates = facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.RenderedPilotReportPhraseology ?: return@filter false
            payload.aircraftId == aircraftId && payload.template == template
        }
        if (candidates.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = failReason,
                evidence = emptyList(),
            )
        }
        candidates.forEach { fact -> activate(fact.id) }
        val expectedObligationKinds = setOf(
            PhraseologyObligationKind.OrderedPhrase,
            PhraseologyObligationKind.SemanticSlot,
        )
        val matching = candidates.filter { fact ->
            val payload = fact.payload as EvidenceFactPayload.RenderedPilotReportPhraseology
            payload.tokens == expectedTokens && payload.obligationKinds.containsAll(expectedObligationKinds)
        }
        return if (matching.isNotEmpty()) {
            EvidenceAuditOutcome.Pass(
                matching.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPilotReportPhraseology
                    "${payload.template}:${payload.text.value}@${fact.provenance.sequence.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Rendered pilot report phraseology did not match expected tokens",
                evidence = candidates.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedPilotReportPhraseology
                    "${payload.template}:${payload.obligationKinds}:${payload.tokens}@${fact.provenance.sequence.value}"
                },
            )
        }
    }
}

class AuditAfterLandingPhraseologySubject internal constructor(
    private val aircraftId: AircraftId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun firstRightWhenVacatedContactGroundExchange(frequency: Frequency): EvidenceAuditOutcome {
        val firstRightFacts = renderedController(RenderedPhraseologyTemplate.AfterLandingVacateViaInstruction)
        val contactFacts = renderedController(RenderedPhraseologyTemplate.ContactFrequencyInstruction)
        val readbackFacts = renderedReadbacks(RenderedPhraseologyTemplate.FirstRightFrequencyReadback)
        val consultedFacts = firstRightFacts + contactFacts + readbackFacts
        if (firstRightFacts.isEmpty() || contactFacts.isEmpty() || readbackFacts.isEmpty()) {
            consultedFacts.forEach { fact -> activate(fact.id) }
            return EvidenceAuditOutcome.Fail(
                reason = "Missing rendered after-landing first-right/contact-ground phraseology for ${aircraftId.value}",
                evidence = firstRightExchangeDiagnostics(firstRightFacts, contactFacts, readbackFacts),
            )
        }

        val malformed = consultedFacts.filterNot { fact -> fact.hasValidFirstRightExchangeShape(frequency) }
        val projection = renderedControllerAndReadbackProjection()
        val matching = projection.windowed(FirstRightExchangeFactCount).firstOrNull { window ->
            window[0].isExpectedFirstRightInstruction() &&
                window[1].isExpectedGroundContact(frequency) &&
                window[2].isExpectedFirstRightFrequencyReadback(frequency)
        }

        return if (matching == null) {
            consultedFacts.forEach { fact -> activate(fact.id) }
            val reason = if (malformed.isNotEmpty()) {
                "Malformed rendered after-landing first-right/contact-ground phraseology for ${aircraftId.value}"
            } else {
                "Rendered after-landing first-right/contact-ground phraseology did not appear as adjacent first-right, contact-ground, readback facts"
            }
            EvidenceAuditOutcome.Fail(
                reason = reason,
                evidence = firstRightExchangeDiagnostics(firstRightFacts, contactFacts, readbackFacts),
            )
        } else {
            matching.forEach { fact -> activate(fact.id) }
            EvidenceAuditOutcome.Pass(
                matching.map { fact ->
                    val payload = fact.payload
                    when (payload) {
                        is EvidenceFactPayload.RenderedPhraseology ->
                            "${payload.template}:${payload.text.value}@${fact.provenance.sequence.value}"
                        is EvidenceFactPayload.RenderedPilotReadbackPhraseology ->
                            "${payload.template}:${payload.text.value}@${fact.provenance.sequence.value}"
                        else -> "${payload.kind}@${fact.provenance.sequence.value}"
                    }
                },
            )
        }
    }

    fun runwayVacatedTaxiToStandExchange(): EvidenceAuditOutcome {
        val runwayVacatedFacts = renderedPilotReports(RenderedPhraseologyTemplate.RunwayVacatedReport)
            .filter { fact ->
                val payload = fact.payload as EvidenceFactPayload.RenderedPilotReportPhraseology
                payload.tokens == listOf(PhraseologyToken.Runway, PhraseologyToken.Vacated)
            }
        val taxiFacts = renderedController(RenderedPhraseologyTemplate.TaxiToStandInstruction)
        val readbackFacts = renderedReadbacks(RenderedPhraseologyTemplate.TaxiRouteReadback)
        val consultedFacts = runwayVacatedFacts + taxiFacts + readbackFacts
        if (runwayVacatedFacts.isEmpty() || taxiFacts.isEmpty() || readbackFacts.isEmpty()) {
            consultedFacts.forEach { fact -> activate(fact.id) }
            return EvidenceAuditOutcome.Fail(
                reason = "Missing rendered after-landing taxi-to-stand phraseology for ${aircraftId.value}",
                evidence = diagnosticEvidence(runwayVacatedFacts, taxiFacts, readbackFacts),
            )
        }
        val matching = runwayVacatedFacts.firstNotNullOfOrNull { runwayVacatedFact ->
            taxiFacts.firstNotNullOfOrNull { taxiFact ->
                if (runwayVacatedFact.provenance.sequence >= taxiFact.provenance.sequence) {
                    return@firstNotNullOfOrNull null
                }
                val taxiPayload = taxiFact.payload as EvidenceFactPayload.RenderedPhraseology
                val taxiRoute = taxiPayload.taxiRoute() ?: return@firstNotNullOfOrNull null
                val readbackFact = readbackFacts.firstOrNull { readbackFact ->
                    val readbackPayload = readbackFact.payload as EvidenceFactPayload.RenderedPilotReadbackPhraseology
                    taxiFact.provenance.sequence < readbackFact.provenance.sequence &&
                        readbackPayload.readbackRoute() == taxiRoute
                } ?: return@firstNotNullOfOrNull null
                AfterLandingPhraseologyMatch(
                    runwayVacated = runwayVacatedFact,
                    taxiToStand = taxiFact,
                    routeReadback = readbackFact,
                    routeTokens = taxiRoute,
                )
            }
        }
        return if (matching == null) {
            consultedFacts.forEach { fact -> activate(fact.id) }
            EvidenceAuditOutcome.Fail(
                reason = "Rendered after-landing phraseology did not appear in runway-vacated, taxi-to-stand, readback order with matching destination and ordered via route",
                evidence = diagnosticEvidence(runwayVacatedFacts, taxiFacts, readbackFacts),
            )
        } else {
            activate(matching.runwayVacated.id)
            activate(matching.taxiToStand.id)
            activate(matching.routeReadback.id)
            EvidenceAuditOutcome.Pass(
                listOf(
                    "RunwayVacatedReport@${matching.runwayVacated.provenance.sequence.value}",
                    "TaxiToStandInstruction:${matching.routeTokens.toTokens()}@${matching.taxiToStand.provenance.sequence.value}",
                    "TaxiRouteReadback:${matching.routeTokens.toTokens()}@${matching.routeReadback.provenance.sequence.value}",
                ),
            )
        }
    }

    fun helicopterAirTaxiToStandExchange(): EvidenceAuditOutcome {
        val instructionFacts = renderedController(RenderedPhraseologyTemplate.AirTaxiToInstruction)
        val readbackFacts = renderedReadbacks(RenderedPhraseologyTemplate.AirTaxiRouteReadback)
        val consultedFacts = instructionFacts + readbackFacts
        if (instructionFacts.isEmpty() || readbackFacts.isEmpty()) {
            consultedFacts.forEach { fact -> activate(fact.id) }
            return EvidenceAuditOutcome.Fail(
                reason = "Missing rendered after-landing helicopter air-taxi phraseology for ${aircraftId.value}",
                evidence = airTaxiExchangeDiagnostics(instructionFacts, readbackFacts),
            )
        }

        val malformed = consultedFacts.filterNot { fact -> fact.hasValidAirTaxiExchangeShape() }
        val projection = renderedControllerAndReadbackProjection()
        val matching = projection.windowed(AirTaxiExchangeFactCount).firstOrNull { window ->
            window[0].isExpectedAirTaxiInstruction() &&
                window[1].isExpectedAirTaxiReadback()
        }

        return if (matching == null) {
            consultedFacts.forEach { fact -> activate(fact.id) }
            val reason = if (malformed.isNotEmpty()) {
                "Malformed rendered after-landing helicopter air-taxi phraseology for ${aircraftId.value}"
            } else {
                "Rendered after-landing helicopter air-taxi phraseology did not appear as adjacent instruction/readback facts"
            }
            EvidenceAuditOutcome.Fail(
                reason = reason,
                evidence = airTaxiExchangeDiagnostics(instructionFacts, readbackFacts),
            )
        } else {
            matching.forEach { fact -> activate(fact.id) }
            EvidenceAuditOutcome.Pass(
                matching.map { fact ->
                    val payload = fact.payload
                    when (payload) {
                        is EvidenceFactPayload.RenderedPhraseology ->
                            "${payload.template}:${payload.text.value}@${fact.provenance.sequence.value}"
                        is EvidenceFactPayload.RenderedPilotReadbackPhraseology ->
                            "${payload.template}:${payload.text.value}@${fact.provenance.sequence.value}"
                        else -> "${payload.kind}@${fact.provenance.sequence.value}"
                    }
                },
            )
        }
    }

    private fun renderedController(template: RenderedPhraseologyTemplate): List<EvidenceFact> =
        facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.RenderedPhraseology ?: return@filter false
            payload.aircraftId == aircraftId && payload.template == template
        }

    private fun renderedReadbacks(template: RenderedPhraseologyTemplate): List<EvidenceFact> =
        facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.RenderedPilotReadbackPhraseology ?: return@filter false
            payload.aircraftId == aircraftId && payload.template == template
        }

    private fun renderedPilotReports(template: RenderedPhraseologyTemplate): List<EvidenceFact> =
        facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.RenderedPilotReportPhraseology ?: return@filter false
            payload.aircraftId == aircraftId && payload.template == template
        }

    private fun renderedControllerAndReadbackProjection(): List<EvidenceFact> =
        facts.filter { fact ->
            when (val payload = fact.payload) {
                is EvidenceFactPayload.RenderedPhraseology -> payload.aircraftId == aircraftId
                is EvidenceFactPayload.RenderedPilotReadbackPhraseology -> payload.aircraftId == aircraftId
                else -> false
            }
        }.sortedBy { fact -> fact.provenance.sequence }

    private fun EvidenceFact.hasValidFirstRightExchangeShape(frequency: Frequency): Boolean =
        isExpectedFirstRightInstruction() ||
            isExpectedGroundContact(frequency) ||
            isExpectedFirstRightFrequencyReadback(frequency)

    private fun EvidenceFact.isExpectedFirstRightInstruction(): Boolean {
        val payload = payload as? EvidenceFactPayload.RenderedPhraseology ?: return false
        return payload.template == RenderedPhraseologyTemplate.AfterLandingVacateViaInstruction &&
            payload.obligationKinds.containsAll(readbackInstructionObligations) &&
            payload.tokens == firstRightInstructionTokens() &&
            payload.text == RenderedPhraseText("${aircraftId.value} TAKE FIRST RIGHT WHEN VACATED")
    }

    private fun EvidenceFact.isExpectedGroundContact(frequency: Frequency): Boolean {
        val payload = payload as? EvidenceFactPayload.RenderedPhraseology ?: return false
        return payload.template == RenderedPhraseologyTemplate.ContactFrequencyInstruction &&
            payload.obligationKinds.containsAll(readbackInstructionObligations) &&
            payload.tokens == listOf(
                PhraseologyToken.AircraftCallsign(aircraftId),
                PhraseologyToken.Contact,
                PhraseologyToken.UnitName("GROUND"),
                PhraseologyToken.FrequencyValue(frequency),
            ) &&
            payload.text == RenderedPhraseText("${aircraftId.value} CONTACT GROUND ${frequency.mhz}")
    }

    private fun EvidenceFact.isExpectedFirstRightFrequencyReadback(frequency: Frequency): Boolean {
        val payload = payload as? EvidenceFactPayload.RenderedPilotReadbackPhraseology ?: return false
        return payload.template == RenderedPhraseologyTemplate.FirstRightFrequencyReadback &&
            payload.obligationKinds.containsAll(readbackObligations) &&
            payload.tokens == firstRightReadbackTokens(frequency) &&
            payload.text == RenderedPhraseText("FIRST RIGHT ${frequency.mhz} ${aircraftId.value}")
    }

    private fun EvidenceFact.hasValidAirTaxiExchangeShape(): Boolean =
        isExpectedAirTaxiInstruction() || isExpectedAirTaxiReadback()

    private fun EvidenceFact.isExpectedAirTaxiInstruction(): Boolean {
        val payload = payload as? EvidenceFactPayload.RenderedPhraseology ?: return false
        return payload.template == RenderedPhraseologyTemplate.AirTaxiToInstruction &&
            payload.obligationKinds.containsAll(readbackInstructionObligations) &&
            payload.tokens == airTaxiInstructionTokens(aircraftId) &&
            payload.text == RenderedPhraseText("${aircraftId.value} AIR-TAXI TO HELICOPTER STAND")
    }

    private fun EvidenceFact.isExpectedAirTaxiReadback(): Boolean {
        val payload = payload as? EvidenceFactPayload.RenderedPilotReadbackPhraseology ?: return false
        return payload.template == RenderedPhraseologyTemplate.AirTaxiRouteReadback &&
            payload.obligationKinds.containsAll(readbackObligations) &&
            payload.tokens == airTaxiRouteReadbackTokens(aircraftId) &&
            payload.text == RenderedPhraseText("AIR-TAXI TO HELICOPTER STAND ${aircraftId.value}")
    }

    private fun firstRightInstructionTokens(): List<PhraseologyToken> =
        listOf(
            PhraseologyToken.AircraftCallsign(aircraftId),
            PhraseologyToken.Take,
            PhraseologyToken.First,
            PhraseologyToken.Right,
            PhraseologyToken.When,
            PhraseologyToken.Vacated,
        )

    private fun firstRightReadbackTokens(frequency: Frequency): List<PhraseologyToken> =
        listOf(
            PhraseologyToken.First,
            PhraseologyToken.Right,
            PhraseologyToken.FrequencyValue(frequency),
            PhraseologyToken.AircraftCallsign(aircraftId),
        )

    private fun airTaxiExchangeDiagnostics(
        instructionFacts: List<EvidenceFact>,
        readbackFacts: List<EvidenceFact>,
    ): List<String> =
        listOf(
            "airTaxi=${instructionFacts.map { fact -> (fact.payload as EvidenceFactPayload.RenderedPhraseology).tokens }}",
            "airTaxiReadback=${readbackFacts.map { fact -> (fact.payload as EvidenceFactPayload.RenderedPilotReadbackPhraseology).tokens }}",
        )

    private fun firstRightExchangeDiagnostics(
        firstRightFacts: List<EvidenceFact>,
        contactFacts: List<EvidenceFact>,
        readbackFacts: List<EvidenceFact>,
    ): List<String> =
        listOf(
            "firstRight=${firstRightFacts.map { fact -> (fact.payload as EvidenceFactPayload.RenderedPhraseology).tokens }}",
            "contactGround=${contactFacts.map { fact -> (fact.payload as EvidenceFactPayload.RenderedPhraseology).tokens }}",
            "firstRightReadback=${readbackFacts.map { fact -> (fact.payload as EvidenceFactPayload.RenderedPilotReadbackPhraseology).tokens }}",
        )

    private fun diagnosticEvidence(
        runwayVacatedFacts: List<EvidenceFact>,
        taxiFacts: List<EvidenceFact>,
        readbackFacts: List<EvidenceFact>,
    ): List<String> =
        listOf(
            "runwayVacated=${runwayVacatedFacts.size}",
            "taxiToStand=${taxiFacts.map { fact -> (fact.payload as EvidenceFactPayload.RenderedPhraseology).tokens }}",
            "routeReadback=${readbackFacts.map { fact -> (fact.payload as EvidenceFactPayload.RenderedPilotReadbackPhraseology).tokens }}",
        )

    private data class AfterLandingPhraseologyMatch(
        val runwayVacated: EvidenceFact,
        val taxiToStand: EvidenceFact,
        val routeReadback: EvidenceFact,
        val routeTokens: RenderedTaxiRoute,
    )

    private companion object {
        const val FirstRightExchangeFactCount = 3
        const val AirTaxiExchangeFactCount = 2

        val readbackInstructionObligations: Set<PhraseologyObligationKind> =
            setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.SemanticSlot,
                PhraseologyObligationKind.Readback,
            )

        val readbackObligations: Set<PhraseologyObligationKind> =
            setOf(
                PhraseologyObligationKind.OrderedPhrase,
                PhraseologyObligationKind.Readback,
                PhraseologyObligationKind.SemanticSlot,
            )
    }
}

class AuditRenderedVehicleDriverPhraseologySubject internal constructor(
    private val vehicleId: VehicleId,
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun initialCall(
        callsign: Callsign,
        position: PointId,
        destination: PointId,
        route: List<PointId>,
    ): EvidenceAuditOutcome {
        require(route.isNotEmpty()) { "vehicle initial-call expected route must not be empty" }
        return phraseologyOutcome(
            template = RenderedPhraseologyTemplate.VehicleInitialCall,
            expectedTokens = listOf(
                PhraseologyToken.VehicleCallsign(callsign),
                PhraseologyToken.PointName(position),
                PhraseologyToken.To,
                PhraseologyToken.PointName(destination),
                PhraseologyToken.Via,
            ) + route.map(PhraseologyToken::PointName),
            expectedText = RenderedPhraseText(
                "${callsign.value} ${position.value} TO ${destination.value} VIA ${pointValues(route)}",
            ),
            failReason = "Missing rendered vehicle initial-call phraseology for ${vehicleId.value}",
        )
    }

    fun towRequest(
        callsign: Callsign,
        receivingStation: ControllerId,
        aircraft: AircraftId,
        aircraftType: AircraftType,
        operator: AircraftOperator,
    ): EvidenceAuditOutcome =
        phraseologyOutcome(
            template = RenderedPhraseologyTemplate.VehicleTowRequest,
            expectedTokens = listOf(
                PhraseologyToken.StationName(receivingStation),
                PhraseologyToken.VehicleCallsign(callsign),
                PhraseologyToken.Request,
                PhraseologyToken.Tow,
                PhraseologyToken.AircraftCallsign(aircraft),
                PhraseologyToken.AircraftTypeName(aircraftType),
                PhraseologyToken.OperatorName(operator),
            ),
            expectedText = RenderedPhraseText(
                listOf(
                    receivingStation.value,
                    callsign.value,
                    "REQUEST TOW",
                    aircraft.value,
                    aircraftType.icaoDesignator.raw,
                    operator.value,
                ).joinToString(separator = " "),
            ),
            failReason = "Missing rendered vehicle tow-request phraseology for ${vehicleId.value}",
        )

    fun unsupportedRequestFurtherPermission(): EvidenceAuditOutcome =
        unsupportedTransmission(VehicleDriverTransmission.RequestFurtherPermission::class.simpleName)

    private fun unsupportedTransmission(transmissionName: String?): EvidenceAuditOutcome {
        val candidates = facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.UnsupportedRenderedVehicleDriverPhraseology
                ?: return@filter false
            payload.vehicleId == vehicleId && payload.transmission::class.simpleName == transmissionName
        }
        return if (candidates.isEmpty()) {
            EvidenceAuditOutcome.Fail(
                reason = "Missing unsupported rendered vehicle phraseology evidence for $transmissionName",
                evidence = emptyList(),
            )
        } else {
            candidates.forEach { fact -> activate(fact.id) }
            EvidenceAuditOutcome.Pass(
                candidates.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.UnsupportedRenderedVehicleDriverPhraseology
                    "${payload.transmission::class.simpleName}@${fact.provenance.sequence.value}"
                },
            )
        }
    }

    private fun phraseologyOutcome(
        template: RenderedPhraseologyTemplate,
        expectedTokens: List<PhraseologyToken>,
        expectedText: RenderedPhraseText,
        failReason: String,
    ): EvidenceAuditOutcome {
        val candidates = facts.filter { fact ->
            val payload = fact.payload as? EvidenceFactPayload.RenderedVehicleDriverPhraseology
                ?: return@filter false
            payload.vehicleId == vehicleId && payload.template == template
        }
        if (candidates.isEmpty()) {
            return EvidenceAuditOutcome.Fail(reason = failReason, evidence = emptyList())
        }
        candidates.forEach { fact -> activate(fact.id) }
        val expectedObligationKinds = setOf(
            PhraseologyObligationKind.OrderedPhrase,
            PhraseologyObligationKind.SemanticSlot,
        )
        val matching = candidates.filter { fact ->
            val payload = fact.payload as EvidenceFactPayload.RenderedVehicleDriverPhraseology
            payload.tokens == expectedTokens &&
                payload.text == expectedText &&
                payload.obligationKinds.containsAll(expectedObligationKinds)
        }
        return if (matching.isNotEmpty()) {
            EvidenceAuditOutcome.Pass(
                matching.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedVehicleDriverPhraseology
                    "${payload.template}:${payload.text.value}@${fact.provenance.sequence.value}"
                },
            )
        } else {
            val malformed = candidates.filter { fact ->
                val payload = fact.payload as EvidenceFactPayload.RenderedVehicleDriverPhraseology
                payload.tokens.hasValidShapeFor(template).not() ||
                    payload.obligationKinds.containsAll(expectedObligationKinds).not()
            }
            val reason = if (malformed.isNotEmpty()) {
                "Malformed rendered vehicle phraseology for ${vehicleId.value}"
            } else {
                "Mismatched rendered vehicle phraseology for ${vehicleId.value}"
            }
            EvidenceAuditOutcome.Fail(
                reason = reason,
                evidence = candidates.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.RenderedVehicleDriverPhraseology
                    "${payload.template}:${payload.obligationKinds}:${payload.tokens}@${fact.provenance.sequence.value}"
                },
            )
        }
    }

    private fun List<PhraseologyToken>.hasValidShapeFor(template: RenderedPhraseologyTemplate): Boolean =
        when (template) {
            RenderedPhraseologyTemplate.VehicleInitialCall -> hasVehicleInitialCallShape()
            RenderedPhraseologyTemplate.VehicleTowRequest -> hasVehicleTowRequestShape()
            RenderedPhraseologyTemplate.AfterLandingVacateViaInstruction,
            RenderedPhraseologyTemplate.FirstRightFrequencyReadback,
            RenderedPhraseologyTemplate.AirTaxiToInstruction,
            RenderedPhraseologyTemplate.AirTaxiRouteReadback,
            RenderedPhraseologyTemplate.ContactFrequencyInstruction,
            RenderedPhraseologyTemplate.FrequencyReadback,
            RenderedPhraseologyTemplate.TaxiToStandInstruction,
            RenderedPhraseologyTemplate.TaxiRouteReadback,
            RenderedPhraseologyTemplate.LineUpAndWaitInstruction,
            RenderedPhraseologyTemplate.LineUpReadback,
            RenderedPhraseologyTemplate.TakeoffClearance,
            RenderedPhraseologyTemplate.TouchAndGoClearance,
            RenderedPhraseologyTemplate.StopImmediatelyInstruction,
            RenderedPhraseologyTemplate.RunwayVacatedReport,
            RenderedPhraseologyTemplate.FinalReport,
            RenderedPhraseologyTemplate.LongFinalReport,
            -> false
        }

    private fun List<PhraseologyToken>.hasVehicleInitialCallShape(): Boolean =
        size > VehicleInitialCallFixedTokenCount &&
            this[0] is PhraseologyToken.VehicleCallsign &&
            this[1] is PhraseologyToken.PointName &&
            this[2] == PhraseologyToken.To &&
            this[3] is PhraseologyToken.PointName &&
            this[4] == PhraseologyToken.Via &&
            drop(VehicleInitialCallFixedTokenCount).all { token -> token is PhraseologyToken.PointName }

    private fun List<PhraseologyToken>.hasVehicleTowRequestShape(): Boolean =
        size in VehicleTowRequestTokenCountWithoutOperator..VehicleTowRequestTokenCountWithOperator &&
            this[0] is PhraseologyToken.StationName &&
            this[1] is PhraseologyToken.VehicleCallsign &&
            this[2] == PhraseologyToken.Request &&
            this[3] == PhraseologyToken.Tow &&
            this[4] is PhraseologyToken.AircraftCallsign &&
            this[5] is PhraseologyToken.AircraftTypeName &&
            getOrNull(VehicleTowRequestOperatorTokenIndex)
                ?.let { token -> token is PhraseologyToken.OperatorName } != false

    private fun pointValues(route: List<PointId>): String =
        route.joinToString(separator = " ") { point -> point.value }

    private companion object {
        const val VehicleInitialCallFixedTokenCount = 5
        const val VehicleTowRequestTokenCountWithoutOperator = 6
        const val VehicleTowRequestTokenCountWithOperator = 7
        const val VehicleTowRequestOperatorTokenIndex = 6
    }
}

class AuditOperationalPolicySubject internal constructor(
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun <S : OperationalPolicyScope> configured(
        branch: OperationalPolicyBranch<S>,
        scope: S,
    ): EvidenceAuditOutcome {
        val policyFacts = facts.filter { fact ->
            fact.payload is EvidenceFactPayload.ConfiguredPolicy
        }
        if (policyFacts.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing configured operational-policy evidence for ${branch.id.value}",
                evidence = emptyList(),
            )
        }
        policyFacts.forEach { fact -> activate(fact.id) }
        val matching = policyFacts.filter { fact ->
            val payload = fact.payload as EvidenceFactPayload.ConfiguredPolicy
            payload.policy.branch == branch && payload.policy.scope == scope
        }
        return if (matching.isNotEmpty()) {
            EvidenceAuditOutcome.Pass(
                matching.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.ConfiguredPolicy
                    "${payload.policy.branch.id.value}@${fact.provenance.sequence.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Configured operational policy did not match ${branch.id.value}",
                evidence = policyFacts.map { fact ->
                    val payload = fact.payload as EvidenceFactPayload.ConfiguredPolicy
                    "${payload.policy.scope}:${payload.policy.branch.id.value}@${fact.provenance.sequence.value}"
                },
            )
        }
    }
}

class AuditTaxiInstructionSubject internal constructor(
    private val facts: List<EvidenceFact>,
    private val activate: (FactId) -> Unit,
) {
    fun allHaveClearanceLimit(): EvidenceAuditOutcome {
        val taxiFacts = facts.filter { fact ->
            val instruction = fact.payload as? EvidenceFactPayload.Instruction ?: return@filter false
            val groundInstruction = instruction.instruction as? GroundInstruction ?: return@filter false
            groundInstruction.taxiLimitAuditShape() != null
        }
        if (taxiFacts.isEmpty()) {
            return EvidenceAuditOutcome.Fail(
                reason = "Missing taxi-instruction evidence",
                evidence = emptyList(),
            )
        }
        taxiFacts.forEach { fact -> activate(fact.id) }
        val missingLimit = taxiFacts.filter { fact ->
            val instruction = (fact.payload as EvidenceFactPayload.Instruction).instruction as GroundInstruction
            instruction.taxiLimitAuditShape()?.hasClearanceLimit == false
        }
        return if (missingLimit.isEmpty()) {
            EvidenceAuditOutcome.Pass(
                evidence = taxiFacts.map { fact ->
                    val instruction = (fact.payload as EvidenceFactPayload.Instruction).instruction
                    "${instruction::class.simpleName}@${fact.provenance.sequence.value}"
                },
            )
        } else {
            EvidenceAuditOutcome.Fail(
                reason = "Typed taxi instruction(s) without clearance-limit field",
                evidence = missingLimit.map { fact ->
                    val instruction = (fact.payload as EvidenceFactPayload.Instruction).instruction
                    "${instruction::class.simpleName}@${fact.provenance.sequence.value}"
                },
            )
        }
    }
}

private data class TaxiLimitAuditShape(
    val hasClearanceLimit: Boolean
)

private data class RenderedTaxiRoute(
    val destination: PointId,
    val via: List<PointId>,
) {
    fun toTokens(): List<PhraseologyToken> = routeTokens(destination = destination, via = via)
}

private fun routeTokens(
    destination: PointId,
    via: List<PointId>,
): List<PhraseologyToken> =
    if (via.isEmpty()) {
        listOf(PhraseologyToken.PointName(destination))
    } else {
        listOf(PhraseologyToken.PointName(destination), PhraseologyToken.Via) +
            via.map(PhraseologyToken::PointName)
    }

internal fun airTaxiInstructionTokens(aircraftId: AircraftId): List<PhraseologyToken> =
    listOf(
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.AirTaxi,
        PhraseologyToken.To,
        PhraseologyToken.PointName(HelicopterStandPoint),
    )

internal fun airTaxiRouteReadbackTokens(aircraftId: AircraftId): List<PhraseologyToken> =
    listOf(
        PhraseologyToken.AirTaxi,
        PhraseologyToken.To,
        PhraseologyToken.PointName(HelicopterStandPoint),
        PhraseologyToken.AircraftCallsign(aircraftId),
    )

private fun EvidenceFactPayload.RenderedPhraseology.taxiRoute(): RenderedTaxiRoute? {
    if (template != RenderedPhraseologyTemplate.TaxiToStandInstruction) return null
    val prefix = listOf(
        PhraseologyToken.AircraftCallsign(aircraftId),
        PhraseologyToken.Taxi,
        PhraseologyToken.To,
    )
    return tokens.removePrefixOrNull(prefix)?.toRenderedTaxiRoute()
}

private fun EvidenceFactPayload.RenderedPilotReadbackPhraseology.readbackRoute(): RenderedTaxiRoute? {
    if (template != RenderedPhraseologyTemplate.TaxiRouteReadback) return null
    val suffix = PhraseologyToken.AircraftCallsign(aircraftId)
    return if (tokens.lastOrNull() == suffix) {
        tokens.dropLast(1).toRenderedTaxiRoute()
    } else {
        null
    }
}

private fun List<PhraseologyToken>.removePrefixOrNull(prefix: List<PhraseologyToken>): List<PhraseologyToken>? =
    if (size >= prefix.size && take(prefix.size) == prefix) drop(prefix.size) else null

private fun List<PhraseologyToken>.toRenderedTaxiRoute(): RenderedTaxiRoute? {
    val destination = firstOrNull() as? PhraseologyToken.PointName ?: return null
    val rest = drop(1)
    if (rest.isEmpty()) {
        return RenderedTaxiRoute(destination = destination.point, via = emptyList())
    }
    if (rest.firstOrNull() != PhraseologyToken.Via) return null
    val viaTokens = rest.drop(1)
    if (viaTokens.isEmpty()) return null
    val via = viaTokens.map { token ->
        (token as? PhraseologyToken.PointName)?.point ?: return null
    }
    return RenderedTaxiRoute(destination = destination.point, via = via)
}

private fun GroundInstruction.taxiLimitAuditShape(): TaxiLimitAuditShape? =
    when (this) {
        is StartupApproved -> null
        is PushbackApproved -> null
        is PushbackFace -> null
        is TaxiToHoldingPoint -> TaxiLimitAuditShape(hasClearanceLimit = true)
        is TaxiToStand -> TaxiLimitAuditShape(hasClearanceLimit = true)
        is TaxiViaRunway -> TaxiLimitAuditShape(hasClearanceLimit = destination != null)
        is AirTaxiTo -> TaxiLimitAuditShape(hasClearanceLimit = true)
        is HoldPosition -> null
        is HoldShortOf -> null
        is CrossRunway -> null
        is BacktrackRunway -> null
        is VacateRunway -> null
        is StopImmediately -> null
        is TaxiIntoHoldingBay,
        is TaxiWithCaution,
        is ExpediteTaxi,
        is ReduceTaxiSpeed,
        -> TaxiLimitAuditShape(hasClearanceLimit = false)
        is GiveWayToTraffic -> null
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
