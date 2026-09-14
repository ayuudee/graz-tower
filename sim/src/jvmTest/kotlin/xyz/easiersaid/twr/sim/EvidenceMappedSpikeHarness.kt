package xyz.easiersaid.twr.sim

import arrow.core.getOrElse
import kotlin.test.fail
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.HighLevelGoal
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.pilot.createMission
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftIntent
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtomicReadback
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.Atis
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.FiledPlan
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.RunwayConfiguration
import xyz.easiersaid.twr.protocol.RunwayId
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Wind
import xyz.easiersaid.twr.protocol.requiredReadbackAtoms
import xyz.easiersaid.twr.sim.testing.Fixtures
import xyz.easiersaid.twr.sim.testing.SimTrace
import xyz.easiersaid.twr.sim.testing.TransmissionRecord
import xyz.easiersaid.twr.sim.testing.controllerAt
import xyz.easiersaid.twr.sim.testing.controllerByRole
import xyz.easiersaid.twr.sim.testing.formatJourney
import xyz.easiersaid.twr.sim.testing.load
import xyz.easiersaid.twr.sim.testing.runUntilWithStateTrace

/**
 * FN40 throwaway harness. The important experiment is the boundary:
 * source/evidence assertions receive [SimObservation], not [SimState].
 */
data class SimObservation(
    val scenarioId: String,
    val instructions: List<ObservedInstruction>,
    val pilotReports: List<ObservedPilotReport>,
    val pilotTransmissions: List<ObservedPilotTransmission>,
    val aircraft: Map<AircraftId, ObservedAircraft>,
    val diagnostic: String,
) {
    inline fun <reified I : AtcInstruction> instructionsTo(aircraftId: AircraftId): List<ObservedInstruction> =
        instructions.filter { instruction ->
            instruction.aircraftId == aircraftId && instruction.instruction is I
        }

    inline fun <reified E : ReportEvent> reportsFrom(aircraftId: AircraftId): List<ObservedPilotReport> =
        pilotReports.filter { report ->
            report.aircraftId == aircraftId && report.events.any { event -> event is E }
        }
}

data class ObservedInstruction(
    val time: SimTime,
    val controllerId: ControllerId,
    val aircraftId: AircraftId,
    val instruction: AtcInstruction,
)

data class ObservedPilotReport(
    val time: SimTime,
    val aircraftId: AircraftId,
    val events: List<ReportEvent>,
)

data class ObservedPilotTransmission(
    val time: SimTime,
    val aircraftId: AircraftId,
    val transmission: PilotTransmission,
)

data class ObservedAircraft(
    val phase: PilotPhase,
    val missionComplete: Boolean,
)

data class LowgCircuitTrace(
    val scenarioId: String,
    val aircraftId: AircraftId,
    val finalAircraft: Map<AircraftId, ObservedAircraft>,
    val records: List<TransmissionRecord>,
    val trace: SimTrace,
    val diagnostic: String,
)

object LowgObservationPort {
    fun runCircuitTraining(
        scenarioId: String,
        outcomes: List<CircuitOutcome>,
        untilMinutes: Long,
    ): SimObservation {
        val trace = runCircuitTrainingTrace(
            scenarioId = scenarioId,
            outcomes = outcomes,
            untilMinutes = untilMinutes,
        )
        return SimObservation(
            scenarioId = trace.scenarioId,
            instructions = trace.records.mapNotNull { record ->
                val controller = record.speaker as? SpeakerRef.Controller ?: return@mapNotNull null
                val output = (record.utterance as? Utterance.FromController)?.output ?: return@mapNotNull null
                val instruct = output as? ControllerOutput.Instruct ?: return@mapNotNull null
                ObservedInstruction(
                    time = record.time,
                    controllerId = controller.id,
                    aircraftId = instruct.target,
                    instruction = instruct.instruction,
                )
            },
            pilotReports = trace.records.mapNotNull { record ->
                val pilot = record.speaker as? SpeakerRef.Pilot ?: return@mapNotNull null
                val transmission = (record.utterance as? Utterance.FromPilot)?.transmission ?: return@mapNotNull null
                val report = transmission as? Report ?: return@mapNotNull null
                ObservedPilotReport(
                    time = record.time,
                    aircraftId = pilot.aircraftId,
                    events = report.events,
                )
            },
            pilotTransmissions = trace.records.mapNotNull { record ->
                val pilot = record.speaker as? SpeakerRef.Pilot ?: return@mapNotNull null
                val transmission = (record.utterance as? Utterance.FromPilot)?.transmission ?: return@mapNotNull null
                ObservedPilotTransmission(
                    time = record.time,
                    aircraftId = pilot.aircraftId,
                    transmission = transmission,
                )
            },
            aircraft = trace.finalAircraft,
            diagnostic = trace.diagnostic,
        )
    }

    fun runCircuitTrainingTrace(
        scenarioId: String,
        outcomes: List<CircuitOutcome>,
        untilMinutes: Long,
    ): LowgCircuitTrace {
        val loaded = Fixtures.LOWG.load().getOrElse {
            fail("LOWG fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val ground = checkNotNull(loaded.controllerByRole(RoleName.GROUND)) { "GROUND missing from fixture" }
        val tower = checkNotNull(loaded.controllerByRole(RoleName.TOWER)) { "TOWER missing from fixture" }
        val aircraftId = AircraftId("OE-ABC")
        val now = SimTime.ZERO
        val mission = createMission(
            goal = HighLevelGoal.CircuitTraining(outcomes = outcomes),
            startPhase = PilotPhase.AtStand,
            time = now,
        )
        val aircraft = AircraftState(
            id = aircraftId,
            callsign = Callsign("OEABC"),
            position = loaded.world.geometry.points.getValue(Fixtures.LOWG.standPointId),
            positionPoint = Fixtures.LOWG.standPointId,
            phase = PilotPhase.AtStand,
            pilotMission = mission,
        )
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(ground, tower),
            weatherByAerodrome = mapOf(lowg to Fixtures.LOWG.weather),
        ).getOrElse { error("SimState.initial rejected the LOWG fixture: $it") }

        val activeRunway = RunwayId("16C")
        val atis = Atis(
            letter = 'A',
            aerodrome = lowg,
            configuration = RunwayConfiguration(arrivals = listOf(activeRunway), departures = listOf(activeRunway)),
            wind = Wind.unsafe(160, 8),
            qnh = null,
            visibility = null,
            generatedAt = now,
        )
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = atis),
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = ground.id),
            SimEvent.ControllerCycle(time = now, controllerId = tower.id),
        )
        val result = runUntilWithStateTrace(
            initialState = initialState,
            initialEvents = initialEvents,
            untilTime = now + SimDuration.ofMillis(untilMinutes * 60 * 1000L),
        )
        val finalState = result.finalState
        val records = result.records
        val finalAircraft = finalState.aircraft.mapValues { (_, aircraftState) ->
            ObservedAircraft(
                phase = aircraftState.phase,
                missionComplete = aircraftState.pilotMission?.isComplete == true,
            )
        }
        return LowgCircuitTrace(
            scenarioId = scenarioId,
            aircraftId = aircraftId,
            finalAircraft = finalAircraft,
            records = records,
            trace = result.trace,
            diagnostic = finalState.formatJourney(aircraftId, records),
        )
    }

    fun runLowgLjmbTransitTrace(
        scenarioId: String,
        untilMinutes: Long,
    ): LowgCircuitTrace {
        val loaded = Fixtures.LOWG_LJMB_VFR.load().getOrElse {
            fail("LOWG_LJMB_VFR fixture failed to load: $it")
        }
        val lowg = AerodromeId("LOWG")
        val ljmb = AerodromeId("LJMB")
        val lowgGround = checkNotNull(loaded.controllerAt(lowg, RoleName.GROUND)) {
            "LOWG_GROUND missing from fixture"
        }
        val lowgTower = checkNotNull(loaded.controllerAt(lowg, RoleName.TOWER)) {
            "LOWG_TOWER missing from fixture"
        }
        val lowgApproach = checkNotNull(loaded.controllerAt(lowg, RoleName.APPROACH)) {
            "LOWG_APPROACH missing from fixture"
        }
        val ljmbTower = checkNotNull(loaded.controllerAt(ljmb, RoleName.TOWER)) {
            "LJMB_TOWER missing from fixture"
        }
        val aircraftId = AircraftId("OE-XYZ")
        val now = SimTime.ZERO
        val filedPlan = FiledPlan.Vfr(
            departureAerodrome = lowg,
            destinationAerodrome = ljmb,
            destinationRunway = RunwayId("14"),
            intent = AircraftIntent.Transit,
        )
        val aircraft = AircraftState(
            id = aircraftId,
            callsign = Callsign("OEXYZ"),
            position = loaded.world.geometry.points.getValue(Fixtures.LOWG_LJMB_VFR.standPointId),
            positionPoint = Fixtures.LOWG_LJMB_VFR.standPointId,
            phase = PilotPhase.AtStand,
            pilotMission = createMission(
                goal = HighLevelGoal.Transit(destination = ljmb),
                startPhase = PilotPhase.AtStand,
                time = now,
                filedPlan = filedPlan,
            ),
        )
        val initialState = SimState.initial(
            seed = 42L,
            world = loaded.world,
            worldIndex = loaded.worldIndex,
            aircraft = listOf(aircraft),
            controllers = listOf(lowgGround, lowgTower, lowgApproach, ljmbTower),
            weatherByAerodrome = Fixtures.LOWG_LJMB_VFR.weatherByAerodrome,
        ).getOrElse { error("SimState.initial rejected the LOWG_LJMB_VFR fixture: $it") }
        val lowgAtis = Atis(
            letter = 'A',
            aerodrome = lowg,
            configuration = RunwayConfiguration(arrivals = listOf(RunwayId("16C")), departures = listOf(RunwayId("16C"))),
            wind = Wind.unsafe(160, 8),
            qnh = null,
            visibility = null,
            generatedAt = now,
        )
        val ljmbAtis = Atis(
            letter = 'B',
            aerodrome = ljmb,
            configuration = RunwayConfiguration(arrivals = listOf(RunwayId("14")), departures = listOf(RunwayId("14"))),
            wind = Wind.unsafe(140, 6),
            qnh = null,
            visibility = null,
            generatedAt = now,
        )
        val initialEvents = loaded.initialEvents + listOf(
            SimEvent.AtisIssued(time = now, aerodrome = lowg, atis = lowgAtis),
            SimEvent.AtisIssued(time = now, aerodrome = ljmb, atis = ljmbAtis),
            SimEvent.PilotDecisionTick(time = now, aircraftId = aircraftId),
            SimEvent.PhysicsTick(time = now),
            SimEvent.ControllerCycle(time = now, controllerId = lowgGround.id),
            SimEvent.ControllerCycle(time = now, controllerId = lowgTower.id),
            SimEvent.ControllerCycle(time = now, controllerId = lowgApproach.id),
            SimEvent.ControllerCycle(time = now, controllerId = ljmbTower.id),
        )
        val result = runUntilWithStateTrace(
            initialState = initialState,
            initialEvents = initialEvents,
            untilTime = now + SimDuration.ofMillis(untilMinutes * 60 * 1000L),
        )
        val finalState = result.finalState
        val finalAircraft = finalState.aircraft.mapValues { (_, aircraftState) ->
            ObservedAircraft(
                phase = aircraftState.phase,
                missionComplete = aircraftState.pilotMission?.isComplete == true,
            )
        }
        return LowgCircuitTrace(
            scenarioId = scenarioId,
            aircraftId = aircraftId,
            finalAircraft = finalAircraft,
            records = result.records,
            trace = result.trace,
            diagnostic = finalState.formatJourney(aircraftId, result.records),
        )
    }
}

object SyntheticObservationPort {
    fun protocolScenario(scenarioId: String): SimObservation =
        SimObservation(
            scenarioId = scenarioId,
            instructions = emptyList(),
            pilotReports = emptyList(),
            pilotTransmissions = emptyList(),
            aircraft = emptyMap(),
            diagnostic = "Synthetic protocol observation",
        )

    fun protocolInstruction(
        scenarioId: String,
        aircraftId: AircraftId,
        instruction: AtcInstruction,
    ): SimObservation =
        SimObservation(
            scenarioId = scenarioId,
            instructions = listOf(
                ObservedInstruction(
                    time = SimTime.ZERO,
                    controllerId = ControllerId("SYNTHETIC_TOWER"),
                    aircraftId = aircraftId,
                    instruction = instruction,
                ),
            ),
            pilotReports = emptyList(),
            pilotTransmissions = emptyList(),
            aircraft = emptyMap(),
            diagnostic = "Synthetic protocol observation: $instruction",
        )
}

sealed interface EvidenceBasis {
    data class SourceMapped(val sources: List<SourceUnitRef>) : EvidenceBasis {
        init {
            require(sources.isNotEmpty()) { "source-mapped case must cite at least one source unit" }
        }
    }

    data class Golden(val reason: String) : EvidenceBasis
    data class Regression(val issue: String) : EvidenceBasis
    data class Invariant(val name: String) : EvidenceBasis
}

sealed interface EvidenceOutcome {
    data class Pass(val evidence: List<String>) : EvidenceOutcome
    data class Fail(val reason: String, val evidence: List<String>) : EvidenceOutcome
    data class Vacuous(val reason: String) : EvidenceOutcome
    data class ExpectedGap(val planId: String, val reason: String) : EvidenceOutcome
    data class UnexpectedGap(val reason: String) : EvidenceOutcome
}

data class EvidenceSample<T : Any>(
    val name: String,
    val value: T,
    val tier: SampleTier,
    val generated: EvidenceGeneratedSampleMetadata? = null,
)

enum class SampleTier {
    Example,
    Representative,
    Generated,
}

data class SampleSpace<T : Any>(
    val name: String,
    val representatives: List<T>,
) {
    init {
        require(name.isNotBlank()) { "sample-space name must not be blank" }
        require(representatives.isNotEmpty()) { "sample-space must contain representatives" }
    }

    fun representativeSamples(): List<EvidenceSample<T>> =
        representatives.map { value ->
            EvidenceSample(name = name, value = value, tier = SampleTier.Representative)
        }
}

data class EvidenceMappedCase(
    val id: String,
    val basis: EvidenceBasis,
    val samples: List<EvidenceSample<*>>,
    val evaluate: EvidenceCaseContext.() -> EvidenceOutcome,
) {
    init {
        require(id.isNotBlank()) { "evidence-mapped case id must not be blank" }
    }

    fun run(observation: SimObservation): EvidenceCaseResult =
        EvidenceCaseResult(
            id = id,
            basis = basis,
            samples = samples,
            outcome = EvidenceCaseContext(observation).evaluate(),
        )
}

class EvidenceCaseContext internal constructor(
    val observation: SimObservation,
) {
    fun pass(vararg evidence: String): EvidenceOutcome.Pass =
        EvidenceOutcome.Pass(evidence = evidence.toList())

    fun fail(reason: String, vararg evidence: String): EvidenceOutcome.Fail =
        EvidenceOutcome.Fail(reason = reason, evidence = evidence.toList())

    fun vacuous(reason: String): EvidenceOutcome.Vacuous =
        EvidenceOutcome.Vacuous(reason = reason)

    fun expectedGap(planId: String, reason: String): EvidenceOutcome.ExpectedGap =
        EvidenceOutcome.ExpectedGap(planId = planId, reason = reason)

    fun unexpectedGap(reason: String): EvidenceOutcome.UnexpectedGap =
        EvidenceOutcome.UnexpectedGap(reason = reason)
}

data class EvidenceCaseResult(
    val id: String,
    val basis: EvidenceBasis,
    val samples: List<EvidenceSample<*>>,
    val outcome: EvidenceOutcome,
)

data class EvidenceMappedReport(
    val scenarioId: String,
    val results: List<EvidenceCaseResult>,
) {
    fun assertNoUnexpectedFailures() {
        val bad = results.filter { result ->
            when (result.outcome) {
                is EvidenceOutcome.Fail,
                is EvidenceOutcome.UnexpectedGap,
                -> true
                is EvidenceOutcome.ExpectedGap,
                is EvidenceOutcome.Pass,
                is EvidenceOutcome.Vacuous,
                -> false
            }
        }
        if (bad.isNotEmpty()) {
            fail(format())
        }
    }

    fun format(): String = buildString {
        appendLine("Evidence-mapped report for $scenarioId")
        results.forEach { result ->
            appendLine("- ${result.id}: ${result.outcome.label()}")
            appendLine("  basis: ${result.basis.label()}")
            if (result.samples.isNotEmpty()) {
                appendLine("  samples: ${result.samples.joinToString { sample -> "${sample.name}=${sample.value}" }}")
            }
        }
    }
}

fun evidenceMappedReport(
    observation: SimObservation,
    cases: List<EvidenceMappedCase>,
): EvidenceMappedReport =
    EvidenceMappedReport(
        scenarioId = observation.scenarioId,
        results = cases.map { case -> case.run(observation) },
    )

private fun EvidenceOutcome.label(): String = when (this) {
    is EvidenceOutcome.ExpectedGap -> "expected_gap($planId): $reason"
    is EvidenceOutcome.Fail -> "fail: $reason"
    is EvidenceOutcome.Pass -> "pass: ${evidence.joinToString()}"
    is EvidenceOutcome.UnexpectedGap -> "unexpected_gap: $reason"
    is EvidenceOutcome.Vacuous -> "vacuous: $reason"
}

private fun EvidenceBasis.label(): String = when (this) {
    is EvidenceBasis.Golden -> "golden: $reason"
    is EvidenceBasis.Invariant -> "invariant: $name"
    is EvidenceBasis.Regression -> "regression: $issue"
    is EvidenceBasis.SourceMapped -> "source: ${sources.joinToString { it.canonicalId }}"
}

class EvidenceScenarioBuilder internal constructor(
    private val id: String,
) {
    private var observation: SimObservation? = null
    private val cases: MutableList<EvidenceMappedCase> = mutableListOf()

    fun observe(run: () -> SimObservation) {
        observation = run()
    }

    fun sourceCase(
        id: String,
        vararg sourceIds: String,
        build: EvidenceCaseBuilder.() -> Unit,
    ) {
        val builder = EvidenceCaseBuilder(id = id, basis = EvidenceBasis.SourceMapped(sourceIds.map(::SourceUnitRef)))
        cases += builder.apply(build).toCase()
    }

    fun goldenCase(
        id: String,
        reason: String,
        build: EvidenceCaseBuilder.() -> Unit,
    ) {
        val builder = EvidenceCaseBuilder(id = id, basis = EvidenceBasis.Golden(reason))
        cases += builder.apply(build).toCase()
    }

    fun regressionCase(
        id: String,
        issue: String,
        build: EvidenceCaseBuilder.() -> Unit,
    ) {
        val builder = EvidenceCaseBuilder(id = id, basis = EvidenceBasis.Regression(issue))
        cases += builder.apply(build).toCase()
    }

    fun invariantCase(
        id: String,
        name: String,
        build: EvidenceCaseBuilder.() -> Unit,
    ) {
        val builder = EvidenceCaseBuilder(id = id, basis = EvidenceBasis.Invariant(name))
        cases += builder.apply(build).toCase()
    }

    fun report(): EvidenceMappedReport {
        val observed = checkNotNull(observation) { "evidence scenario '$id' did not declare observe { ... }" }
        return evidenceMappedReport(observation = observed, cases = cases)
    }
}

class EvidenceCaseBuilder internal constructor(
    private val id: String,
    private val basis: EvidenceBasis,
) {
    private val samples: MutableList<EvidenceSample<*>> = mutableListOf()
    private var evaluate: (EvidenceDslContext.() -> Any)? = null

    fun <T : Any> sample(name: String, value: T, tier: SampleTier = SampleTier.Example) {
        samples += EvidenceSample(name = name, value = value, tier = tier)
    }

    fun expect(assertion: EvidenceDslContext.() -> Any) {
        evaluate = assertion
    }

    internal fun toCase(): EvidenceMappedCase =
        EvidenceMappedCase(id = id, basis = basis, samples = samples.toList()) {
            val assertion = checkNotNull(evaluate) { "evidence case '$id' did not declare expect { ... }" }
            when (val result = EvidenceDslContext(observation).assertion()) {
                is EvidenceOutcome -> result
                is EvidenceOutcomeCarrier -> result.toOutcome()
                else -> fail("evidence case '$id' returned unsupported assertion result: $result")
            }
        }
}

class EvidenceDslContext internal constructor(
    @PublishedApi internal val observation: SimObservation,
) {
    inline fun <reified I : AtcInstruction> instruction(aircraftId: AircraftId): EvidencePoint =
        observation.instructionsTo<I>(aircraftId)
            .firstOrNull()
            ?.let { instruction -> EvidencePoint.Present(label = I::class.simpleName ?: "Instruction", time = instruction.time) }
            ?: EvidencePoint.Missing(I::class.simpleName ?: "Instruction")

    inline fun <reified E : ReportEvent> report(aircraftId: AircraftId): EvidencePoint =
        observation.reportsFrom<E>(aircraftId)
            .firstOrNull()
            ?.let { report -> EvidencePoint.Present(label = E::class.simpleName ?: "Report", time = report.time) }
            ?: EvidencePoint.Missing(E::class.simpleName ?: "Report")

    fun aircraft(aircraftId: AircraftId): EvidenceAircraftSubject =
        EvidenceAircraftSubject(aircraftId = aircraftId, aircraft = observation.aircraft[aircraftId])

    fun expectedGap(planId: String, reason: String): EvidenceOutcome.ExpectedGap =
        EvidenceOutcome.ExpectedGap(planId = planId, reason = reason)

    fun readback(instruction: AtcInstruction): RequiredReadbackSubject =
        RequiredReadbackSubject(instruction)
}

data class RequiredReadbackSubject(
    private val instruction: AtcInstruction,
) {
    fun requires(vararg atoms: AtomicReadback): EvidenceOutcome {
        val expected = atoms.toSet()
        val actual = requiredReadbackAtoms(instruction)
        return if (actual == expected) {
            EvidenceOutcome.Pass(
                listOf("${instruction::class.simpleName} requires ${actual.joinToString()}"),
            )
        } else {
            EvidenceOutcome.Fail(
                reason = "${instruction::class.simpleName} readback atoms differ",
                evidence = listOf("expected=$expected", "actual=$actual"),
            )
        }
    }

    fun requiresNoAtoms(): EvidenceOutcome {
        val actual = requiredReadbackAtoms(instruction)
        return if (actual.isEmpty()) {
            EvidenceOutcome.Pass(listOf("${instruction::class.simpleName} has no required structural readback atom"))
        } else {
            EvidenceOutcome.Fail(
                reason = "${instruction::class.simpleName} unexpectedly requires readback atoms",
                evidence = listOf("actual=$actual"),
            )
        }
    }
}

sealed interface EvidencePoint {
    val label: String

    data class Present(
        override val label: String,
        val time: SimTime,
    ) : EvidencePoint

    data class Missing(
        override val label: String,
    ) : EvidencePoint
}

data class EvidenceOrder(
    val points: List<EvidencePoint>,
) : EvidenceOutcomeCarrier {
    override fun toOutcome(): EvidenceOutcome {
        val missing = points.filterIsInstance<EvidencePoint.Missing>()
        if (missing.isNotEmpty()) {
            return EvidenceOutcome.UnexpectedGap("Missing evidence point(s): ${missing.joinToString { it.label }}")
        }
        val present = points.filterIsInstance<EvidencePoint.Present>()
        val ordered = present.zipWithNext().all { (left, right) -> left.time.millis < right.time.millis }
        return if (ordered) {
            EvidenceOutcome.Pass(
                evidence = present.map { point -> "${point.label}@${point.time.millis}" },
            )
        } else {
            EvidenceOutcome.Fail(
                reason = "Expected ordered evidence: ${present.joinToString { it.label }}",
                evidence = present.map { point -> "${point.label}@${point.time.millis}" },
            )
        }
    }
}

interface EvidenceOutcomeCarrier {
    fun toOutcome(): EvidenceOutcome
}

infix fun EvidencePoint.before(next: EvidencePoint): EvidenceOrder =
    EvidenceOrder(listOf(this, next))

infix fun EvidenceOrder.before(next: EvidencePoint): EvidenceOrder =
    copy(points = points + next)

data class EvidenceAircraftSubject(
    private val aircraftId: AircraftId,
    private val aircraft: ObservedAircraft?,
) {
    fun isParkedAndComplete(): EvidenceOutcome =
        when {
            aircraft == null -> EvidenceOutcome.UnexpectedGap("Missing final aircraft observation for ${aircraftId.value}")
            aircraft.phase == PilotPhase.Parked && aircraft.missionComplete ->
                EvidenceOutcome.Pass(listOf("phase=${aircraft.phase}", "missionComplete=${aircraft.missionComplete}"))
            else -> EvidenceOutcome.Fail(
                reason = "Aircraft ${aircraftId.value} did not finish parked and complete",
                evidence = listOf("phase=${aircraft.phase}", "missionComplete=${aircraft.missionComplete}"),
            )
        }
}

fun evidenceScenario(
    id: String,
    build: EvidenceScenarioBuilder.() -> Unit,
): EvidenceMappedReport =
    EvidenceScenarioBuilder(id).apply(build).report()
