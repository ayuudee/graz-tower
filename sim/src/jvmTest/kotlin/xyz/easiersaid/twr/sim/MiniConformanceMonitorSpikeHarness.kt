package xyz.easiersaid.twr.sim

import kotlin.test.fail
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.SimTime

data class MiniConformanceTrace(
    val scenarioId: String,
    val capabilities: Set<MiniCapability>,
    val facts: List<MiniTraceFact>,
)

enum class MiniCapability {
    ProtocolSynthetic,
    ReadbackExchange,
    TowerCircuit,
    TaxiClearance,
    LandingIntent,
    AerodromeInformation,
    CriticalPhase,
    GoldenOutcome,
}

sealed interface MiniTraceFact {
    val provenance: MiniFactProvenance

    data class InstructionObserved(
        val time: SimTime,
        val aircraftId: AircraftId,
        val instruction: AtcInstruction,
        override val provenance: MiniFactProvenance,
    ) : MiniTraceFact

    data class PilotReportObserved(
        val time: SimTime,
        val aircraftId: AircraftId,
        val events: List<ReportEvent>,
        override val provenance: MiniFactProvenance,
    ) : MiniTraceFact

    data class PilotTransmissionObserved(
        val time: SimTime,
        val aircraftId: AircraftId,
        val transmission: PilotTransmission,
        override val provenance: MiniFactProvenance,
    ) : MiniTraceFact

    data class AircraftFinalObserved(
        val aircraftId: AircraftId,
        val phase: PilotPhase,
        val missionComplete: Boolean,
        override val provenance: MiniFactProvenance,
    ) : MiniTraceFact
}

data class MiniFactProvenance(
    val scenarioId: String,
    val origin: MiniFactOrigin,
    val path: String,
)

enum class MiniFactOrigin {
    SimObservationPort,
    SyntheticProtocolPort,
}

data class MiniSourceContract(
    val id: String,
    val basis: EvidenceBasis,
    val requiredCapabilities: Set<MiniCapability>,
    val expectedGapPlanId: String? = null,
    val monitor: MiniMonitorContext.() -> MiniMonitorOutcome,
) {
    init {
        require(id.isNotBlank()) { "contract id must not be blank" }
    }
}

class MiniMonitorContext internal constructor(
    val trace: MiniConformanceTrace,
    val contract: MiniSourceContract,
) {
    inline fun <reified I : AtcInstruction> instructionsTo(aircraftId: AircraftId): List<MiniTraceFact.InstructionObserved> =
        trace.facts.filterIsInstance<MiniTraceFact.InstructionObserved>()
            .filter { fact -> fact.aircraftId == aircraftId && fact.instruction is I }

    inline fun <reified E : ReportEvent> reportsFrom(aircraftId: AircraftId): List<MiniTraceFact.PilotReportObserved> =
        trace.facts.filterIsInstance<MiniTraceFact.PilotReportObserved>()
            .filter { fact -> fact.aircraftId == aircraftId && fact.events.any { event -> event is E } }

    fun finalAircraft(aircraftId: AircraftId): MiniTraceFact.AircraftFinalObserved? =
        trace.facts.filterIsInstance<MiniTraceFact.AircraftFinalObserved>()
            .firstOrNull { fact -> fact.aircraftId == aircraftId }

    fun pass(activationCount: Int, vararg facts: MiniTraceFact): MiniMonitorOutcome.Pass =
        MiniMonitorOutcome.Pass(activationCount = activationCount, factPaths = facts.map { it.provenance.path })

    fun fail(reason: String, vararg facts: MiniTraceFact): MiniMonitorOutcome.Fail =
        MiniMonitorOutcome.Fail(reason = reason, factPaths = facts.map { it.provenance.path })

    fun expectedGap(reason: String): MiniMonitorOutcome.ExpectedGap =
        MiniMonitorOutcome.ExpectedGap(
            planId = checkNotNull(contract.expectedGapPlanId) {
                "contract ${contract.id} returned ExpectedGap without expectedGapPlanId"
            },
            reason = reason,
        )

    fun unexpectedGap(reason: String): MiniMonitorOutcome.UnexpectedGap =
        MiniMonitorOutcome.UnexpectedGap(reason = reason)

    fun vacuous(reason: String): MiniMonitorOutcome.Vacuous =
        MiniMonitorOutcome.Vacuous(reason = reason)
}

sealed interface MiniMonitorOutcome {
    data class Pass(val activationCount: Int, val factPaths: List<String>) : MiniMonitorOutcome
    data class Fail(val reason: String, val factPaths: List<String>) : MiniMonitorOutcome
    data class Vacuous(val reason: String) : MiniMonitorOutcome
    data class ExpectedGap(val planId: String, val reason: String) : MiniMonitorOutcome
    data class UnexpectedGap(val reason: String) : MiniMonitorOutcome
}

data class MiniMonitorResult(
    val contractId: String,
    val basis: EvidenceBasis,
    val outcome: MiniMonitorOutcome,
)

data class MiniMonitorReport(
    val scenarioId: String,
    val results: List<MiniMonitorResult>,
) {
    fun assertNoUnexpectedFailures() {
        val bad = results.filter { result ->
            when (result.outcome) {
                is MiniMonitorOutcome.Fail,
                is MiniMonitorOutcome.UnexpectedGap,
                -> true
                is MiniMonitorOutcome.ExpectedGap,
                is MiniMonitorOutcome.Pass,
                is MiniMonitorOutcome.Vacuous,
                -> false
            }
        }
        if (bad.isNotEmpty()) {
            fail(format())
        }
    }

    fun format(): String = buildString {
        appendLine("Mini monitor report for $scenarioId")
        results.forEach { result ->
            appendLine("- ${result.contractId}: ${result.outcome.label()}")
        }
    }
}

fun SimObservation.toMiniConformanceTrace(
    capabilities: Set<MiniCapability>,
    origin: MiniFactOrigin,
): MiniConformanceTrace =
    MiniConformanceTrace(
        scenarioId = scenarioId,
        capabilities = capabilities,
        facts = instructions.mapIndexed { index, instruction ->
            MiniTraceFact.InstructionObserved(
                time = instruction.time,
                aircraftId = instruction.aircraftId,
                instruction = instruction.instruction,
                provenance = MiniFactProvenance(
                    scenarioId = scenarioId,
                    origin = origin,
                    path = "instructions[$index]",
                ),
            )
        } + pilotReports.mapIndexed { index, report ->
            MiniTraceFact.PilotReportObserved(
                time = report.time,
                aircraftId = report.aircraftId,
                events = report.events,
                provenance = MiniFactProvenance(
                    scenarioId = scenarioId,
                    origin = origin,
                    path = "pilotReports[$index]",
                ),
            )
        } + pilotTransmissions.mapIndexed { index, transmission ->
            MiniTraceFact.PilotTransmissionObserved(
                time = transmission.time,
                aircraftId = transmission.aircraftId,
                transmission = transmission.transmission,
                provenance = MiniFactProvenance(
                    scenarioId = scenarioId,
                    origin = origin,
                    path = "pilotTransmissions[$index]",
                ),
            )
        } + aircraft.entries.map { (aircraftId, aircraft) ->
            MiniTraceFact.AircraftFinalObserved(
                aircraftId = aircraftId,
                phase = aircraft.phase,
                missionComplete = aircraft.missionComplete,
                provenance = MiniFactProvenance(
                    scenarioId = scenarioId,
                    origin = origin,
                    path = "aircraft[${aircraftId.value}]",
                ),
            )
        },
    )

fun runMiniMonitors(
    trace: MiniConformanceTrace,
    contracts: List<MiniSourceContract>,
): MiniMonitorReport =
    MiniMonitorReport(
        scenarioId = trace.scenarioId,
        results = contracts.mapNotNull { contract ->
            if (!trace.capabilities.containsAll(contract.requiredCapabilities)) {
                null
            } else {
                MiniMonitorResult(
                    contractId = contract.id,
                    basis = contract.basis,
                    outcome = contract.monitor(MiniMonitorContext(trace = trace, contract = contract)),
                )
            }
        },
    )

private fun MiniMonitorOutcome.label(): String = when (this) {
    is MiniMonitorOutcome.ExpectedGap -> "expected_gap($planId): $reason"
    is MiniMonitorOutcome.Fail -> "fail: $reason facts=${factPaths.joinToString()}"
    is MiniMonitorOutcome.Pass -> "pass activations=$activationCount facts=${factPaths.joinToString()}"
    is MiniMonitorOutcome.UnexpectedGap -> "unexpected_gap: $reason"
    is MiniMonitorOutcome.Vacuous -> "vacuous: $reason"
}
