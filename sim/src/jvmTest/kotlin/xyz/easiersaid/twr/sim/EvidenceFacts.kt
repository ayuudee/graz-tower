package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.controller.DecisionTrace
import xyz.easiersaid.twr.core.world.AviationWorld
import xyz.easiersaid.twr.core.world.Position
import xyz.easiersaid.twr.core.world.WorldIndex
import xyz.easiersaid.twr.pilot.AircraftState
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AerodromeId
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.Callsign
import xyz.easiersaid.twr.protocol.Clearance
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.InitialContact
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.PointId
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.Request
import xyz.easiersaid.twr.protocol.RequestFrequencyChange
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SayAgain
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.Standby
import xyz.easiersaid.twr.sim.testing.SimTrace
import xyz.easiersaid.twr.sim.testing.TransmissionRecord
import xyz.easiersaid.twr.sim.testing.runUntil
import xyz.easiersaid.twr.sim.testing.toTransmissionRecords

@JvmInline
value class FactId(val value: String) {
    init {
        require(value.isNotBlank()) { "fact id must not be blank" }
    }
}

@JvmInline
value class EvidenceSequence(val value: Int) : Comparable<EvidenceSequence> {
    init {
        require(value >= 0) { "evidence sequence must be non-negative" }
    }

    override fun compareTo(other: EvidenceSequence): Int =
        value.compareTo(other.value)
}

sealed interface EvidenceFactOrigin {
    val label: String

    data object SyntheticProtocol : EvidenceFactOrigin {
        override val label: String = "synthetic-protocol"
    }

    data object SimRun : EvidenceFactOrigin {
        override val label: String = "sim-run"
    }
}

data class EvidenceExtractionPath(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "extraction path must not be blank" }
    }
}

data class EvidenceFactProvenance(
    val scenarioId: String,
    val origin: EvidenceFactOrigin,
    val sequence: EvidenceSequence,
    val simTime: SimTime?,
    val sourceTransmissionId: TransmissionId?,
    val extractionPath: EvidenceExtractionPath,
) {
    init {
        require(scenarioId.isNotBlank()) { "scenario id must not be blank" }
    }
}

data class EvidenceFact(
    val id: FactId,
    val provenance: EvidenceFactProvenance,
    val payload: EvidenceFactPayload,
)

sealed interface EvidenceFactPayload {
    val kind: EvidenceFactKind

    data class Instruction(
        val controllerId: ControllerId,
        val aircraftId: AircraftId,
        val instruction: AtcInstruction,
    ) : EvidenceFactPayload {
        override val kind: EvidenceFactKind = EvidenceFactKind.Instruction
    }

    data class PilotReport(
        val aircraftId: AircraftId,
        val events: List<ReportEvent>,
    ) : EvidenceFactPayload {
        init {
            require(events.isNotEmpty()) { "pilot report fact must carry at least one event" }
        }

        override val kind: EvidenceFactKind = EvidenceFactKind.PilotReport
    }

    data class PilotTransmissionFact(
        val aircraftId: AircraftId,
        val transmission: PilotTransmission,
    ) : EvidenceFactPayload {
        override val kind: EvidenceFactKind = EvidenceFactKind.PilotTransmission
    }

    data class AircraftSummary(
        val aircraftId: AircraftId,
        val phase: PilotPhase,
        val missionComplete: Boolean,
    ) : EvidenceFactPayload {
        override val kind: EvidenceFactKind = EvidenceFactKind.AircraftSummary
    }

    data class AerodromeInformation(
        val aircraftId: AircraftId,
        val timingContext: AerodromeInformationTimingContext,
        val status: AerodromeInformationStatus,
        val detail: AerodromeInformationDetail,
    ) : EvidenceFactPayload {
        override val kind: EvidenceFactKind = EvidenceFactKind.AerodromeInformation
    }

    data class CriticalPhaseWindow(
        val aircraftId: AircraftId,
        val phase: CriticalPhaseKind,
        val start: EvidenceSequence,
        val end: EvidenceSequence,
    ) : EvidenceFactPayload {
        init {
            require(start <= end) { "critical phase window start must not be after end" }
        }

        override val kind: EvidenceFactKind = EvidenceFactKind.CriticalPhaseWindow
    }

    data class CriticalPhaseTransmission(
        val aircraftId: AircraftId,
        val phase: CriticalPhaseKind,
        val transmissionId: TransmissionId,
        val necessity: TransmissionNecessity,
    ) : EvidenceFactPayload {
        override val kind: EvidenceFactKind = EvidenceFactKind.CriticalPhaseTransmission
    }

    data class FrequencyTransfer(
        val aircraftId: AircraftId,
        val mode: FrequencyTransferMode,
        val target: FrequencyTransferTarget,
    ) : EvidenceFactPayload {
        override val kind: EvidenceFactKind = EvidenceFactKind.FrequencyTransfer
    }

    /**
     * Reception-doubt observation about a transmission instance.
     *
     * Cites ICAO 9432 §2.8.1.4 — *"If there is doubt that a message has been
     * correctly received, a repetition of the messages shall be requested
     * either in full or in part."*
     *
     * Doubt is scoped to a single transmission instance (`transmissionRef`),
     * not a channel-quality window. Time-decay semantics are deferred.
     *
     * The trigger (doubt) and the response ([SayAgainRef]) are distinct types
     * linked by an optional typed reference: when the receiver requests a
     * repetition for the doubtful transmission, [resolvedBy] points to the
     * `SayAgain` instance that closed the loop. There is no back-reference
     * from `SayAgain` to the doubt fact — `SayAgain` itself stays unchanged
     * (no retroactive mutability).
     */
    data class ReceptionDoubt(
        val aircraftId: AircraftId,
        val transmissionRef: TransmissionId,
        val doubtSource: ReceptionDoubtSource,
        val resolvedBy: SayAgainRef? = null,
    ) : EvidenceFactPayload {
        override val kind: EvidenceFactKind = EvidenceFactKind.ReceptionDoubt
    }

    /**
     * Clearance-pacing observation: a single controller-issued clearance
     * (carried via [clearanceRef]) was observed being issued while the
     * pilot was in a regulation-relevant operational window
     * ([issuedDuring]).
     *
     * Cites ICAO 9432 §2.8.3.2 — *"Controllers should pass a clearance
     * slowly and clearly … should avoid passing a clearance to a pilot
     * engaged in complicated taxiing manoeuvres … on no occasion should
     * a clearance be passed when the pilot is engaged in line up or
     * take-off manoeuvres."*
     *
     * The leaf observes *conditions* (clearance issued during phase X),
     * not *prescriptive rules* (controller MUST wait). Future POLICY-1
     * work may layer prescriptive policy concepts on top; this primitive
     * stays observational so the advisory audit ("did this happen?")
     * remains evaluable without policy commitment.
     *
     * [issuedDuring] is the regulation-relevant [PacingWindow]
     * classification, not a 1:1 mirror of
     * [xyz.easiersaid.twr.pilot.PilotPhase]. The adapter maps the
     * observed `PilotPhase` to `PacingWindow` so future phase additions
     * are absorbed at the projection boundary, not at the payload.
     */
    data class ClearancePacing(
        val aircraftId: AircraftId,
        val clearanceRef: TransmissionId,
        val issuedDuring: PacingWindow,
    ) : EvidenceFactPayload {
        override val kind: EvidenceFactKind = EvidenceFactKind.ClearancePacing
    }

    data class SampleFact(
        val name: String,
        val displayValue: String,
        val tier: SampleTier,
    ) : EvidenceFactPayload {
        init {
            require(name.isNotBlank()) { "sample fact name must not be blank" }
            require(displayValue.isNotBlank()) { "sample fact value must not be blank" }
        }

        override val kind: EvidenceFactKind = EvidenceFactKind.Sample
    }
}

enum class EvidenceFactKind {
    Instruction,
    PilotReport,
    PilotTransmission,
    AircraftSummary,
    AerodromeInformation,
    CriticalPhaseWindow,
    CriticalPhaseTransmission,
    FrequencyTransfer,
    ReceptionDoubt,
    ClearancePacing,
    Sample,
}

/**
 * Regulation-relevant operational window the pilot was in at the moment a
 * controller-issued clearance was observed.
 *
 * Source: ICAO 9432 §2.8.3.2.
 *
 * Closed enumeration. The adapter maps `PilotPhase` to `PacingWindow` so
 * predicate guards in tests can exhaust via `PacingWindow.entries` (per
 * memory `predicate-guards-over-sealed-types-must-2026-05-16`). Future
 * `PilotPhase` additions are absorbed at the projection boundary by
 * extending the mapping function, not by widening the audit payload.
 *
 * - [ComplicatedTaxi] — pilot engaged in taxiing (§2.8.3.2 "should avoid").
 * - [LineUp] — pilot lined up on the runway (§2.8.3.2 "on no occasion").
 * - [TakeoffRoll] — pilot on the takeoff roll (§2.8.3.2 "on no occasion").
 * - [Other] — non-sensitive window (no advisory hit when a clearance is
 *   issued during this window).
 */
enum class PacingWindow {
    ComplicatedTaxi,
    LineUp,
    TakeoffRoll,
    Other,
}

enum class AerodromeInformationTimingContext {
    BeforeTaxi,
    BeforeFinalApproach,
}

enum class AerodromeInformationStatus {
    PassedByController,
    KnownReceivedElsewhere,
}

data class AerodromeInformationDetail(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "aerodrome information detail must not be blank" }
    }
}

enum class CriticalPhaseKind {
    Takeoff,
    InitialClimb,
    LateFinal,
    LandingRoll,
}

enum class TransmissionNecessity {
    Routine,
    SafetyNecessary,
}

enum class FrequencyTransferMode {
    ControllerAdvised,
    PilotNotifiedAbsentAdvice,
}

/**
 * Reason the receiver could not satisfy a message-correctness expectation
 * for a single transmission instance. Closed sealed sub-type — every
 * `when` consumer must exhaust the leaves.
 *
 * Source: ICAO 9432 §2.8.1.4 (doubtful reception triggers repetition).
 */
sealed interface ReceptionDoubtSource {
    val label: String

    /** Reception interrupted mid-transmission; part of the message missing. */
    data object PartialReception : ReceptionDoubtSource {
        override val label: String = "partial-reception"
    }

    /** Transmission heard but the content could not be parsed / understood. */
    data object Unintelligibility : ReceptionDoubtSource {
        override val label: String = "unintelligibility"
    }

    /** Two transmissions overlapped on the frequency; this one was "stepped on". */
    data object SteppedOn : ReceptionDoubtSource {
        override val label: String = "stepped-on"
    }

    /** Catch-all for doubt sources outside the named leaves (still typed). */
    data class Other(val detail: String) : ReceptionDoubtSource {
        init {
            require(detail.isNotBlank()) { "reception doubt 'Other' detail must not be blank" }
        }

        override val label: String = "other:$detail"
    }
}

/**
 * Typed reference to a `protocol.SayAgain` response transmission that
 * resolved a [EvidenceFactPayload.ReceptionDoubt] observation.
 *
 * Thin wrapper around the originating transmission id. Carrying a typed
 * ref (rather than a raw long) keeps `EvidenceFactPayload.ReceptionDoubt`
 * honest at the type boundary: the doubt fact references the *response*
 * by id without modifying `SayAgain` itself.
 */
@JvmInline
value class SayAgainRef(val transmissionId: TransmissionId) {
    override fun toString(): String = "SayAgain@${transmissionId.value}"
}

sealed interface FrequencyTransferTarget {
    val unitName: String

    data class UnitOnly(
        override val unitName: String,
    ) : FrequencyTransferTarget {
        init {
            require(unitName.isNotBlank()) { "frequency-transfer target unit must not be blank" }
        }
    }

    data class UnitAndFrequency(
        override val unitName: String,
        val frequency: String,
    ) : FrequencyTransferTarget {
        init {
            require(unitName.isNotBlank()) { "frequency-transfer target unit must not be blank" }
            require(frequency.isNotBlank()) { "frequency-transfer target frequency must not be blank" }
        }
    }
}

data class EvidenceFactSet(
    val scenarioId: String,
    val facts: List<EvidenceFact>,
    val diagnostic: String,
) {
    init {
        require(scenarioId.isNotBlank()) { "scenario id must not be blank" }
        require(facts.map { fact -> fact.id }.distinct().size == facts.size) {
            "evidence fact ids must be unique"
        }
        require(facts.map { fact -> fact.provenance.sequence }.distinct().size == facts.size) {
            "evidence fact sequences must be unique"
        }
    }

    fun orderedFacts(): List<EvidenceFact> =
        facts.sortedBy { fact -> fact.provenance.sequence }
}

object EvidenceFactAdapters {
    fun syntheticProtocolInstruction(
        scenarioId: String,
        aircraftId: AircraftId,
        instruction: AtcInstruction,
    ): EvidenceFactSet {
        val fact = fact(
            scenarioId = scenarioId,
            origin = EvidenceFactOrigin.SyntheticProtocol,
            sequence = EvidenceSequence(0),
            simTime = SimTime.ZERO,
            sourceTransmissionId = null,
            extractionPath = EvidenceExtractionPath("synthetic.protocol.instruction"),
            payload = EvidenceFactPayload.Instruction(
                controllerId = ControllerId("SYNTHETIC_TOWER"),
                aircraftId = aircraftId,
                instruction = instruction,
            ),
        )
        return EvidenceFactSet(
            scenarioId = scenarioId,
            facts = listOf(fact),
            diagnostic = "Synthetic protocol evidence: ${instruction::class.simpleName}",
        )
    }

    fun lowgCircuitTraining(
        scenarioId: String,
        outcomes: List<CircuitOutcome>,
        untilMinutes: Long,
    ): EvidenceFactSet {
        val trace = LowgObservationPort.runCircuitTrainingTrace(
            scenarioId = scenarioId,
            outcomes = outcomes,
            untilMinutes = untilMinutes,
        )
        return fromLowgCircuitTrace(trace)
    }

    fun lowgLjmbTransit(
        scenarioId: String,
        untilMinutes: Long,
    ): EvidenceFactSet {
        val trace = LowgObservationPort.runLowgLjmbTransitTrace(
            scenarioId = scenarioId,
            untilMinutes = untilMinutes,
        )
        return fromLowgCircuitTrace(trace)
    }

    fun receptionDoubtOverlap(scenarioId: String): EvidenceFactSet {
        val aircraftA = AircraftId("OE-ABC")
        val aircraftB = AircraftId("OE-DEF")
        val towerId = ControllerId("LOWG_TWR")
        val towerFrequency = Frequency.unsafe("118.200")
        val towerTransmission = InFlightTransmission(
            id = TransmissionId(10),
            speaker = SpeakerRef.Controller(towerId),
            receiver = ReceiverRef.Pilot(aircraftA),
            frequency = towerFrequency,
            utterance = Utterance.FromController(
                ControllerOutput.Respond(
                    target = aircraftA,
                    response = Standby(target = aircraftA),
                    trace = DecisionTrace(
                        ruleId = "COMMS-1-OVERLAP",
                        description = "COMMS-1 stepped-on controller response",
                        regulations = emptyList(),
                    ),
                ),
            ),
            startedAt = SimTime.ZERO,
            endsAt = SimTime.ofMillis(COMMS_1_TOWER_END_MS),
        )
        val blockingTransmission = InFlightTransmission(
            id = TransmissionId(11),
            speaker = SpeakerRef.Pilot(aircraftB),
            receiver = ReceiverRef.Controller(towerId),
            frequency = towerFrequency,
            utterance = Utterance.FromPilot(Report(events = listOf(ReportEvent.Ready))),
            startedAt = SimTime.ZERO,
            endsAt = SimTime.ofMillis(COMMS_1_BLOCKING_END_MS),
        )
        val (_, events) = runUntil(
            initialState = receptionDoubtOverlapState(
                aircraftA = aircraftA,
                aircraftB = aircraftB,
                towerId = towerId,
                towerFrequency = towerFrequency,
            ),
            initialEvents = listOf(
                SimEvent.TransmissionStart(time = SimTime.ZERO, transmission = towerTransmission),
                SimEvent.TransmissionStart(time = SimTime.ZERO, transmission = blockingTransmission),
            ),
            untilTime = SimTime.ofSeconds(COMMS_1_UNTIL_SECONDS),
        )
        return fromTransmissionRecords(
            scenarioId = scenarioId,
            records = events.toTransmissionRecords(),
            diagnostic = "COMMS-1 real radio-overlap evidence facts",
        )
    }

    fun fromLowgCircuitTrace(trace: LowgCircuitTrace): EvidenceFactSet {
        // Build a per-transmission phase lookup from the SimTrace so the
        // ClearancePacing projection can observe the pilot phase at the
        // moment each clearance was issued. The trace step closest at or
        // before the record's time is the authoritative state — phases
        // advance forward in sim time only.
        val phaseAtTransmission: Map<TransmissionId, PilotPhase> =
            phaseAtTransmissionLookup(records = trace.records, trace = trace.trace)
        val transmissionFacts = fromTransmissionRecords(
            scenarioId = trace.scenarioId,
            records = trace.records,
            finalAircraft = trace.finalAircraft,
            diagnostic = trace.diagnostic,
            phaseAtTransmission = phaseAtTransmission,
        )
        val nextSequence = EvidenceSequence(
            transmissionFacts.facts.maxOfOrNull { fact -> fact.provenance.sequence.value }?.plus(1) ?: 0,
        )
        return transmissionFacts.copy(
            facts = transmissionFacts.facts + criticalPhaseWindowFacts(
                scenarioId = trace.scenarioId,
                trace = trace.trace,
                startSequence = nextSequence,
            ),
        )
    }

    fun fromTransmissionRecords(
        scenarioId: String,
        records: List<TransmissionRecord>,
        finalAircraft: Map<AircraftId, ObservedAircraft> = emptyMap(),
        diagnostic: String = "Projected evidence facts",
        phaseAtTransmission: Map<TransmissionId, PilotPhase> = emptyMap(),
    ): EvidenceFactSet {
        val transmissionFacts = records.flatMapIndexed { index, record ->
            recordFacts(
                scenarioId = scenarioId,
            recordIndex = index,
            record = record,
            records = records,
            phaseAtTransmission = phaseAtTransmission,
        )
        }
        val aircraftFacts = finalAircraft.entries
            .sortedBy { (aircraftId, _) -> aircraftId.value }
            .mapIndexed { index, (aircraftId, aircraft) ->
                fact(
                    scenarioId = scenarioId,
                    origin = EvidenceFactOrigin.SimRun,
                    sequence = EvidenceSequence(records.size * FACTS_PER_RECORD + index),
                    simTime = null,
                    sourceTransmissionId = null,
                    extractionPath = EvidenceExtractionPath("sim.finalAircraft.${aircraftId.value}"),
                    payload = EvidenceFactPayload.AircraftSummary(
                        aircraftId = aircraftId,
                        phase = aircraft.phase,
                        missionComplete = aircraft.missionComplete,
                    ),
                )
            }
        return EvidenceFactSet(
            scenarioId = scenarioId,
            facts = transmissionFacts + aircraftFacts,
            diagnostic = diagnostic,
        )
    }

    fun fromProjectedPayloads(
        scenarioId: String,
        payloads: List<EvidenceFactPayload>,
        diagnostic: String = "Projected evidence facts",
    ): EvidenceFactSet {
        val facts = payloads.mapIndexed { index, payload ->
            fact(
                scenarioId = scenarioId,
                origin = EvidenceFactOrigin.SimRun,
                sequence = EvidenceSequence(index),
                simTime = null,
                sourceTransmissionId = null,
                extractionPath = EvidenceExtractionPath("projection[$index].${payload.kind}"),
                payload = payload,
            )
        }
        return EvidenceFactSet(
            scenarioId = scenarioId,
            facts = facts,
            diagnostic = diagnostic,
        )
    }

    fun sampleFact(
        scenarioId: String,
        name: String,
        value: Any,
        tier: SampleTier,
        sequence: EvidenceSequence = EvidenceSequence(0),
    ): EvidenceFact =
        fact(
            scenarioId = scenarioId,
            origin = EvidenceFactOrigin.SyntheticProtocol,
            sequence = sequence,
            simTime = null,
            sourceTransmissionId = null,
            extractionPath = EvidenceExtractionPath("sample.$name"),
            payload = EvidenceFactPayload.SampleFact(name = name, displayValue = value.toString(), tier = tier),
        )

    private fun recordFacts(
        scenarioId: String,
        recordIndex: Int,
        record: TransmissionRecord,
        records: List<TransmissionRecord>,
        phaseAtTransmission: Map<TransmissionId, PilotPhase>,
    ): List<EvidenceFact> =
        when (val utterance = record.utterance) {
            is Utterance.FromController -> when (val speaker = record.speaker) {
                is SpeakerRef.Controller -> controllerFacts(
                    scenarioId = scenarioId,
                    recordIndex = recordIndex,
                    record = record,
                    controller = speaker,
                    output = utterance.output,
                    records = records,
                    phaseAtTransmission = phaseAtTransmission,
                )

                is SpeakerRef.Pilot -> emptyList()
            }

            is Utterance.FromPilot -> when (val speaker = record.speaker) {
                is SpeakerRef.Controller -> emptyList()
                is SpeakerRef.Pilot -> pilotFacts(
                    scenarioId = scenarioId,
                    recordIndex = recordIndex,
                    record = record,
                    records = records,
                    pilot = speaker,
                    transmission = utterance.transmission,
                )
            }
        }

    private fun controllerFacts(
        scenarioId: String,
        recordIndex: Int,
        record: TransmissionRecord,
        controller: SpeakerRef.Controller,
        output: ControllerOutput,
        records: List<TransmissionRecord>,
        phaseAtTransmission: Map<TransmissionId, PilotPhase>,
    ): List<EvidenceFact> {
        val targetAircraft = when (output) {
            is ControllerOutput.Instruct -> output.target
            is ControllerOutput.Respond -> output.target
        }
        val receptionDoubtFact = receptionDoubtFact(
            scenarioId = scenarioId,
            recordIndex = recordIndex,
            record = record,
            records = records,
            aircraftId = targetAircraft,
            extractionSlot = "controller",
        )
        val outputFacts = when (output) {
            is ControllerOutput.Instruct -> {
                val instructionFact = fact(
                    scenarioId = scenarioId,
                    origin = EvidenceFactOrigin.SimRun,
                    sequence = EvidenceSequence(recordIndex * FACTS_PER_RECORD),
                    simTime = record.time,
                    sourceTransmissionId = record.transmissionId,
                    extractionPath = EvidenceExtractionPath("sim.records[$recordIndex].controller.instruction"),
                    payload = EvidenceFactPayload.Instruction(
                        controllerId = controller.id,
                        aircraftId = output.target,
                        instruction = output.instruction,
                    ),
                )
                val frequencyTransferFact = controllerAdvisedFrequencyTransferFact(
                    scenarioId = scenarioId,
                    recordIndex = recordIndex,
                    record = record,
                    instruction = output.instruction,
                    targetAircraft = output.target,
                )
                val clearancePacingFact = clearancePacingFact(
                    scenarioId = scenarioId,
                    recordIndex = recordIndex,
                    record = record,
                    instruction = output.instruction,
                    targetAircraft = output.target,
                    phaseAtTransmission = phaseAtTransmission,
                )
                listOf(instructionFact) +
                    listOfNotNull(frequencyTransferFact) +
                    listOfNotNull(clearancePacingFact)
            }

            is ControllerOutput.Respond -> emptyList()
        }
        return outputFacts + listOfNotNull(receptionDoubtFact)
    }

    /**
     * Project a controller-advised frequency transfer fact from a
     * [ContactFrequency] instruction.
     *
     * Cites ICAO 9432 §2.8.2.1 — *"an aircraft will be advised by the
     * appropriate aeronautical station to change from one radio frequency to
     * another"*. The fact carries the target aircraft and the next unit (role
     * name) plus the explicit frequency when issued.
     *
     * Sequence offset `+3` is reserved for this projection so that the unique-
     * sequence invariant on [EvidenceFactSet] holds when the same record also
     * emits the base instruction fact at offset `+0`. Offset `+5` is reserved
     * for ReceptionDoubt facts (task .3); task .4 reserves `+6`
     * (ClearancePacing).
     */
    private fun controllerAdvisedFrequencyTransferFact(
        scenarioId: String,
        recordIndex: Int,
        record: TransmissionRecord,
        instruction: AtcInstruction,
        targetAircraft: AircraftId,
    ): EvidenceFact? {
        val contactFrequency = instruction as? ContactFrequency ?: return null
        val frequencyValue = contactFrequency.frequency?.mhz
        val target = if (frequencyValue == null) {
            FrequencyTransferTarget.UnitOnly(unitName = contactFrequency.role.name)
        } else {
            FrequencyTransferTarget.UnitAndFrequency(
                unitName = contactFrequency.role.name,
                frequency = frequencyValue,
            )
        }
        return fact(
            scenarioId = scenarioId,
            origin = EvidenceFactOrigin.SimRun,
            sequence = EvidenceSequence(recordIndex * FACTS_PER_RECORD + 3),
            simTime = record.time,
            sourceTransmissionId = record.transmissionId,
            extractionPath = EvidenceExtractionPath(
                "sim.records[$recordIndex].controller.contactFrequency",
            ),
            payload = EvidenceFactPayload.FrequencyTransfer(
                aircraftId = targetAircraft,
                mode = FrequencyTransferMode.ControllerAdvised,
                target = target,
            ),
        )
    }

    private fun pilotFacts(
        scenarioId: String,
        recordIndex: Int,
        record: TransmissionRecord,
        records: List<TransmissionRecord>,
        pilot: SpeakerRef.Pilot,
        transmission: PilotTransmission,
    ): List<EvidenceFact> {
        val transmissionFact = fact(
            scenarioId = scenarioId,
            origin = EvidenceFactOrigin.SimRun,
            sequence = EvidenceSequence(recordIndex * FACTS_PER_RECORD),
            simTime = record.time,
            sourceTransmissionId = record.transmissionId,
            extractionPath = EvidenceExtractionPath("sim.records[$recordIndex].pilot.transmission"),
            payload = EvidenceFactPayload.PilotTransmissionFact(
                aircraftId = pilot.aircraftId,
                transmission = transmission,
            ),
        )
        val aerodromeInformationFact = aerodromeInformationFact(
            scenarioId = scenarioId,
            recordIndex = recordIndex,
            record = record,
            pilot = pilot,
            transmission = transmission,
        )
        val report = transmission as? Report
        val reportFacts = if (report == null) {
            emptyList()
        } else {
            listOf(
                fact(
                    scenarioId = scenarioId,
                    origin = EvidenceFactOrigin.SimRun,
                    sequence = EvidenceSequence(recordIndex * FACTS_PER_RECORD + 1),
                    simTime = record.time,
                    sourceTransmissionId = record.transmissionId,
                    extractionPath = EvidenceExtractionPath("sim.records[$recordIndex].pilot.report"),
                    payload = EvidenceFactPayload.PilotReport(
                        aircraftId = pilot.aircraftId,
                        events = report.events,
                    ),
                ),
            )
        }
        val frequencyChangeFact = pilotNotifiedFrequencyChangeFact(
            scenarioId = scenarioId,
            recordIndex = recordIndex,
            record = record,
            pilot = pilot,
            transmission = transmission,
        )
        val receptionDoubtFact = receptionDoubtFact(
            scenarioId = scenarioId,
            recordIndex = recordIndex,
            record = record,
            records = records,
            aircraftId = pilot.aircraftId,
            extractionSlot = "pilot",
        )
        return listOf(transmissionFact) +
            reportFacts +
            listOfNotNull(aerodromeInformationFact) +
            listOfNotNull(frequencyChangeFact) +
            listOfNotNull(receptionDoubtFact)
    }

    /**
     * Project a pilot-notified frequency change fact from a [Request]
     * pilot transmission whose [Request.type] is [RequestFrequencyChange].
     *
     * Cites ICAO 9432 §2.8.2.1 fallback — *"an aircraft will, except for
     * reasons of safety, notify the appropriate aeronautical station before
     * such a change takes place"*. The fact records the pilot's notification
     * intent; the next unit is not known from the request alone, so
     * [FrequencyTransferTarget] carries a sentinel `unitName` ("UNSPECIFIED")
     * with the requested frequency when present.
     *
     * Sequence offset `+4` is reserved for this projection so that the
     * unique-sequence invariant on [EvidenceFactSet] holds alongside the base
     * pilot transmission fact (`+0`) and aerodrome information fact (`+2`).
     */
    private fun pilotNotifiedFrequencyChangeFact(
        scenarioId: String,
        recordIndex: Int,
        record: TransmissionRecord,
        pilot: SpeakerRef.Pilot,
        transmission: PilotTransmission,
    ): EvidenceFact? {
        val request = transmission as? Request ?: return null
        val frequencyChange = request.type as? RequestFrequencyChange ?: return null
        val frequencyValue = frequencyChange.frequency?.mhz
        val target = if (frequencyValue == null) {
            FrequencyTransferTarget.UnitOnly(unitName = PILOT_NOTIFIED_UNIT_PLACEHOLDER)
        } else {
            FrequencyTransferTarget.UnitAndFrequency(
                unitName = PILOT_NOTIFIED_UNIT_PLACEHOLDER,
                frequency = frequencyValue,
            )
        }
        return fact(
            scenarioId = scenarioId,
            origin = EvidenceFactOrigin.SimRun,
            sequence = EvidenceSequence(recordIndex * FACTS_PER_RECORD + 4),
            simTime = record.time,
            sourceTransmissionId = record.transmissionId,
            extractionPath = EvidenceExtractionPath(
                "sim.records[$recordIndex].pilot.requestFrequencyChange",
            ),
            payload = EvidenceFactPayload.FrequencyTransfer(
                aircraftId = pilot.aircraftId,
                mode = FrequencyTransferMode.PilotNotifiedAbsentAdvice,
                target = target,
            ),
        )
    }

    /**
     * Project a clearance-pacing fact for a controller-issued [Clearance]
     * when the sim trace observably reports the pilot phase at the moment
     * the clearance was issued.
     *
     * Cites ICAO 9432 §2.8.3.2 — *"Controllers should pass a clearance
     * slowly and clearly … should avoid passing a clearance to a pilot
     * engaged in complicated taxiing manoeuvres … on no occasion should
     * a clearance be passed when the pilot is engaged in line up or
     * take-off manoeuvres."*
     *
     * Sequence offset `+6` is reserved for this projection so that the
     * unique-sequence invariant on [EvidenceFactSet] holds alongside the
     * base instruction fact (`+0`), report facts (`+1`), aerodrome-info
     * facts (`+2`), controller-advised frequency-transfer facts (`+3`),
     * pilot-notified frequency-change facts (`+4`), and reception-doubt
     * facts (`+5`).
     *
     * **Wiring scope**: clearance-pacing observation is a property of
     * controller-issued clearances, NOT of arbitrary controller
     * transmissions or pilot transmissions. The projection is wired
     * through `ControllerOutput.Instruct` ONLY. Contrast with
     * [receptionDoubtFact] which is wired through both controller and
     * pilot arms because reception doubt is a property of any
     * transmission instance.
     *
     * Returns null when:
     * - The instruction is not a [xyz.easiersaid.twr.protocol.Clearance]
     *   (§2.8.3.2 talks about *clearances*; non-clearance instructions
     *   like vectors / level changes are out of scope here).
     * - No phase observation is available in [phaseAtTransmission] for
     *   this transmission id (covered-red leg: sim genuinely lacked the
     *   phase signal at the clearance-issue moment).
     *
     * The window mapping (`phaseToPacingWindow`) maps observable
     * [xyz.easiersaid.twr.pilot.PilotPhase] values to the
     * regulation-relevant [PacingWindow] classification. Phases outside
     * the §2.8.3.2 sensitive set map to [PacingWindow.Other] so the
     * projection is *total* (every clearance with a phase observation
     * produces a fact) — the advisory branch decision happens in the
     * selector, not the adapter.
     */
    private fun clearancePacingFact(
        scenarioId: String,
        recordIndex: Int,
        record: TransmissionRecord,
        instruction: AtcInstruction,
        targetAircraft: AircraftId,
        phaseAtTransmission: Map<TransmissionId, PilotPhase>,
    ): EvidenceFact? {
        // §2.8.3.2 scopes the obligation to clearances specifically.
        if (instruction !is Clearance) return null
        // No phase signal observed at this transmission's time → no fact.
        // Honest covered-red leg when sim lacks the signal; covered-green
        // leg is when sim has phase info (LOWG circuit-training traces
        // produce it for AtStand/Taxiing/HoldingShort/LinedUp/TakeoffRoll/etc.).
        val phase = phaseAtTransmission[record.transmissionId] ?: return null
        return fact(
            scenarioId = scenarioId,
            origin = EvidenceFactOrigin.SimRun,
            sequence = EvidenceSequence(recordIndex * FACTS_PER_RECORD + 6),
            simTime = record.time,
            sourceTransmissionId = record.transmissionId,
            extractionPath = EvidenceExtractionPath(
                "sim.records[$recordIndex].controller.clearancePacing",
            ),
            payload = EvidenceFactPayload.ClearancePacing(
                aircraftId = targetAircraft,
                clearanceRef = record.transmissionId,
                issuedDuring = phaseToPacingWindow(phase),
            ),
        )
    }

    /**
     * Map an observed [PilotPhase] to the regulation-relevant
     * [PacingWindow] classification per ICAO 9432 §2.8.3.2.
     *
     * The mapping is closed over `PilotPhase` (Kotlin sealed-interface
     * exhaustiveness check will catch any future phase additions). Phases
     * outside the §2.8.3.2 sensitive set map to [PacingWindow.Other] —
     * the adapter is total; the selector's `whenIssuedDuring(...)`
     * decides which windows are violations.
     *
     * Mapping rationale:
     * - [PilotPhase.Taxiing] → [PacingWindow.ComplicatedTaxi]. The
     *   regulation talks about "complicated taxiing manoeuvres"; the sim
     *   does not distinguish *complicated* from simple taxi, so every
     *   taxi-phase observation surfaces as the regulation-sensitive
     *   window. Future refinement can split via a typed taxi-complexity
     *   signal; until then the observation is the upper bound on what
     *   §2.8.3.2 cares about.
     * - [PilotPhase.LinedUp] → [PacingWindow.LineUp].
     * - [PilotPhase.TakeoffRoll] → [PacingWindow.TakeoffRoll].
     * - All others → [PacingWindow.Other].
     */
    private fun phaseToPacingWindow(phase: PilotPhase): PacingWindow =
        when (phase) {
            PilotPhase.Taxiing -> PacingWindow.ComplicatedTaxi
            PilotPhase.LinedUp -> PacingWindow.LineUp
            PilotPhase.TakeoffRoll -> PacingWindow.TakeoffRoll
            PilotPhase.AtStand,
            PilotPhase.Base,
            PilotPhase.ClearOfRunway,
            PilotPhase.Climbing,
            PilotPhase.Crosswind,
            PilotPhase.Downwind,
            PilotPhase.Final,
            PilotPhase.HoldingShort,
            PilotPhase.LandingRoll,
            PilotPhase.Parked,
            PilotPhase.Vacating,
            -> PacingWindow.Other
        }

    /**
     * Build a per-transmission phase lookup table from a [SimTrace].
     *
     * For each controller-issued [TransmissionRecord], find the trace
     * step at or before the record's time and look up the target
     * aircraft's phase in that step's state. Returns a map keyed by
     * [TransmissionId] for fast O(1) lookup during projection.
     *
     * Why "at or before the record's time": the sim's event scheduler is
     * monotone (per [SimTrace] invariant). The state immediately before
     * the controller's transmission step is the state at which the
     * controller decided to issue the clearance; the post-step state may
     * already reflect the side-effects of the transmission itself
     * (e.g., a `LineUpAndWait` issuance is followed by the pilot's phase
     * transitioning to `LinedUp` later). Using "at or before" pins the
     * observation to the actual pacing moment.
     *
     * Records without a phase observation (no trace step at or before
     * the record's time, or the target aircraft missing from that
     * state) are omitted from the map — the projection returns null for
     * them.
     */
    private fun phaseAtTransmissionLookup(
        records: List<TransmissionRecord>,
        trace: SimTrace,
    ): Map<TransmissionId, PilotPhase> {
        val controllerRecords = records.filter { record ->
            record.speaker is SpeakerRef.Controller &&
                record.utterance is Utterance.FromController
        }
        if (controllerRecords.isEmpty()) return emptyMap()
        // Pre-collect (time, state) snapshots in order so we can scan
        // efficiently. trace.initial counts as the pre-event snapshot.
        val timedStates: List<Pair<SimTime, SimState>> =
            listOf(trace.initial.now to trace.initial) +
                trace.steps.map { step -> step.state.now to step.state }
        val result = mutableMapOf<TransmissionId, PilotPhase>()
        controllerRecords.forEach { record ->
            val target = (record.utterance as Utterance.FromController).output.let { output ->
                when (output) {
                    is ControllerOutput.Instruct -> output.target
                    is ControllerOutput.Respond -> output.target
                }
            }
            // Find the latest snapshot whose time <= record.time.
            val snapshot = timedStates.lastOrNull { (time, _) -> time <= record.time }
                ?: return@forEach
            val phase = snapshot.second.aircraft[target]?.phase ?: return@forEach
            result[record.transmissionId] = phase
        }
        return result
    }

    /**
     * Project a reception-doubt fact for a transmission instance when the
     * sim trace observably signals reception trouble (partial reception,
     * unintelligibility, stepped-on by overlapping traffic, or another typed
     * source).
     *
     * Cites ICAO 9432 §2.8.1.4 — *"If there is doubt that a message has been
     * correctly received, a repetition of the messages shall be requested
     * either in full or in part."*
     *
     * Sequence offset `+5` is reserved for this projection so that the
     * unique-sequence invariant on [EvidenceFactSet] holds alongside the
     * base instruction/transmission facts (`+0`), report facts (`+1`),
     * aerodrome-information facts (`+2`), controller-advised
     * frequency-transfer facts (`+3`), and pilot-notified frequency-change
     * facts (`+4`). Task .4 reserves `+6` (ClearancePacing).
     *
     * Clear transmissions emit no fact. Doubtful transmissions emit an
     * unresolved fact; task fn-50.2 wires the operational `SayAgain` response
     * and fills [EvidenceFactPayload.ReceptionDoubt.resolvedBy].
     *
     * Per AGENTS.md commandment 4 (tests prove the real job), we do NOT
     * fabricate doubt facts from `fromProjectedPayloads` to claim a synthetic
     * covered-green. The trigger (doubt) and the response
     * (`protocol.SayAgain` via [SayAgainRef]) are distinct types linked by
     * a typed optional reference; `SayAgain` itself is not modified by this
     * projection.
     */
    private fun receptionDoubtFact(
        scenarioId: String,
        recordIndex: Int,
        record: TransmissionRecord,
        records: List<TransmissionRecord>,
        aircraftId: AircraftId,
        extractionSlot: String,
    ): EvidenceFact? =
        when (val quality = record.receptionQuality) {
            ReceptionQuality.Clear -> null
            is ReceptionQuality.Doubtful -> fact(
                scenarioId = scenarioId,
                origin = EvidenceFactOrigin.SimRun,
                sequence = EvidenceSequence(recordIndex * FACTS_PER_RECORD + 5),
                simTime = record.time,
                sourceTransmissionId = record.transmissionId,
                extractionPath = EvidenceExtractionPath(
                    "sim.records[$recordIndex].$extractionSlot.receptionDoubt",
                ),
                payload = EvidenceFactPayload.ReceptionDoubt(
                    aircraftId = aircraftId,
                    transmissionRef = record.transmissionId,
                    doubtSource = quality.cause.toReceptionDoubtSource(),
                    resolvedBy = sayAgainResolutionFor(
                        records = records,
                        afterIndex = recordIndex,
                        aircraftId = aircraftId,
                    ),
                ),
            )
        }

    private fun ReceptionDoubtCause.toReceptionDoubtSource(): ReceptionDoubtSource =
        when (this) {
            ReceptionDoubtCause.PartialReception -> ReceptionDoubtSource.PartialReception
            ReceptionDoubtCause.Unintelligibility -> ReceptionDoubtSource.Unintelligibility
            ReceptionDoubtCause.SteppedOn -> ReceptionDoubtSource.SteppedOn
            is ReceptionDoubtCause.Other -> ReceptionDoubtSource.Other(detail)
        }

    private fun sayAgainResolutionFor(
        records: List<TransmissionRecord>,
        afterIndex: Int,
        aircraftId: AircraftId,
    ): SayAgainRef? =
        records.drop(afterIndex + 1)
            .firstOrNull { record ->
                val speaker = record.speaker as? SpeakerRef.Pilot ?: return@firstOrNull false
                val utterance = record.utterance as? Utterance.FromPilot ?: return@firstOrNull false
                speaker.aircraftId == aircraftId && utterance.transmission is SayAgain
            }
            ?.let { record -> SayAgainRef(record.transmissionId) }

    private fun receptionDoubtOverlapState(
        aircraftA: AircraftId,
        aircraftB: AircraftId,
        towerId: ControllerId,
        towerFrequency: Frequency,
    ): SimState =
        SimState(
            now = SimTime.ZERO,
            seq = 0L,
            rng = SimRandom(0L),
            rngByAircraft = mapOf(aircraftA to SimRandom(1L), aircraftB to SimRandom(2L)),
            aircraft = linkedMapOf(
                aircraftA to receptionDoubtAircraft(aircraftA, "OE-ABC"),
                aircraftB to receptionDoubtAircraft(aircraftB, "OE-DEF"),
            ),
            controllers = mapOf(
                towerId to ControllerSpec(
                    id = towerId,
                    role = RoleName.TOWER,
                    aerodromeId = AerodromeId("LOWG"),
                    frequency = towerFrequency,
                    responsibilities = emptyMap(),
                ),
            ),
            beliefs = emptyMap(),
            world = AviationWorld(),
            worldIndex = WorldIndex(),
            nextTransmissionId = COMMS_1_FIRST_MINTED_TX_ID,
        )

    private fun receptionDoubtAircraft(aircraftId: AircraftId, callsign: String): AircraftState =
        AircraftState(
            id = aircraftId,
            callsign = Callsign(callsign),
            position = Position(0.0, 0.0),
            positionPoint = PointId("P"),
        )

    private fun aerodromeInformationFact(
        scenarioId: String,
        recordIndex: Int,
        record: TransmissionRecord,
        pilot: SpeakerRef.Pilot,
        transmission: PilotTransmission,
    ): EvidenceFact? {
        val initialContact = transmission as? InitialContact ?: return null
        val atisCode = initialContact.atisCode ?: return null
        val context = when (initialContact.stationCalled) {
            RoleName.CLEARANCE_DELIVERY,
            RoleName.GROUND,
            -> AerodromeInformationTimingContext.BeforeTaxi

            RoleName.AFIS,
            RoleName.APPROACH,
            RoleName.TOWER,
            -> AerodromeInformationTimingContext.BeforeFinalApproach

            RoleName.AREA_CONTROL,
            RoleName.DEPARTURE,
            -> return null
        }
        return fact(
            scenarioId = scenarioId,
            origin = EvidenceFactOrigin.SimRun,
            sequence = EvidenceSequence(recordIndex * FACTS_PER_RECORD + 2),
            simTime = record.time,
            sourceTransmissionId = record.transmissionId,
            extractionPath = EvidenceExtractionPath("sim.records[$recordIndex].pilot.initialContact.atisCode"),
            payload = EvidenceFactPayload.AerodromeInformation(
                aircraftId = pilot.aircraftId,
                timingContext = context,
                status = AerodromeInformationStatus.KnownReceivedElsewhere,
                detail = AerodromeInformationDetail("pilot reported information $atisCode on initial contact"),
            ),
        )
    }

    private fun criticalPhaseWindowFacts(
        scenarioId: String,
        trace: SimTrace,
        startSequence: EvidenceSequence,
    ): List<EvidenceFact> {
        val aircraftIds = (listOf(trace.initial) + trace.steps.map { step -> step.state })
            .flatMap { state -> state.aircraft.keys }
            .distinct()
            .sortedBy { aircraftId -> aircraftId.value }
        val windows = aircraftIds.flatMap { aircraftId -> criticalPhaseWindowsForAircraft(aircraftId, trace) }
        return windows.mapIndexed { index, window ->
            fact(
                scenarioId = scenarioId,
                origin = EvidenceFactOrigin.SimRun,
                sequence = EvidenceSequence(startSequence.value + index),
                simTime = null,
                sourceTransmissionId = null,
                extractionPath = EvidenceExtractionPath(
                    "sim.trace.${window.aircraftId.value}.${window.phase}.${window.start.value}-${window.end.value}",
                ),
                payload = EvidenceFactPayload.CriticalPhaseWindow(
                    aircraftId = window.aircraftId,
                    phase = window.phase,
                    start = window.start,
                    end = window.end,
                ),
            )
        }
    }

    private fun criticalPhaseWindowsForAircraft(
        aircraftId: AircraftId,
        trace: SimTrace,
    ): List<CriticalWindowSpec> {
        val samples = trace.steps.mapIndexedNotNull { index, step ->
            val phase = step.state.aircraft[aircraftId]?.phase ?: return@mapIndexedNotNull null
            CriticalPhaseSample(
                traceIndex = index + 1,
                kind = criticalPhaseKind(phase),
            )
        }
        val build = samples.fold(CriticalWindowBuild(open = null, closed = emptyList())) { acc, sample ->
            when (val open = acc.open) {
                null -> if (sample.kind == null) {
                    acc
                } else {
                    acc.copy(
                        open = OpenCriticalWindow(
                            aircraftId = aircraftId,
                            phase = sample.kind,
                            start = sample.traceIndex,
                            last = sample.traceIndex,
                        ),
                    )
                }

                else -> if (sample.kind == open.phase) {
                    acc.copy(open = open.copy(last = sample.traceIndex))
                } else {
                    CriticalWindowBuild(
                        open = sample.kind?.let { nextKind ->
                            OpenCriticalWindow(
                                aircraftId = aircraftId,
                                phase = nextKind,
                                start = sample.traceIndex,
                                last = sample.traceIndex,
                            )
                        },
                        closed = acc.closed + open.toSpec(),
                    )
                }
            }
        }
        return build.closed + listOfNotNull(build.open?.toSpec())
    }

    private fun criticalPhaseKind(phase: PilotPhase): CriticalPhaseKind? =
        when (phase) {
            PilotPhase.TakeoffRoll -> CriticalPhaseKind.Takeoff
            PilotPhase.LandingRoll -> CriticalPhaseKind.LandingRoll
            PilotPhase.AtStand,
            PilotPhase.Base,
            PilotPhase.ClearOfRunway,
            PilotPhase.Climbing,
            PilotPhase.Crosswind,
            PilotPhase.Downwind,
            PilotPhase.Final,
            PilotPhase.HoldingShort,
            PilotPhase.LinedUp,
            PilotPhase.Parked,
            PilotPhase.Taxiing,
            PilotPhase.Vacating,
            -> null
        }

    private fun fact(
        scenarioId: String,
        origin: EvidenceFactOrigin,
        sequence: EvidenceSequence,
        simTime: SimTime?,
        sourceTransmissionId: TransmissionId?,
        extractionPath: EvidenceExtractionPath,
        payload: EvidenceFactPayload,
    ): EvidenceFact {
        val id = FactId(
            listOf(
                scenarioId,
                origin.label,
                sequence.value.toString().padStart(6, '0'),
                payload.kind.name,
                extractionPath.value,
            ).joinToString(separator = "::"),
        )
        return EvidenceFact(
            id = id,
            provenance = EvidenceFactProvenance(
                scenarioId = scenarioId,
                origin = origin,
                sequence = sequence,
                simTime = simTime,
                sourceTransmissionId = sourceTransmissionId,
                extractionPath = extractionPath,
            ),
            payload = payload,
        )
    }

    private const val FACTS_PER_RECORD: Int = 10
    private const val COMMS_1_TOWER_END_MS: Long = 2500L
    private const val COMMS_1_BLOCKING_END_MS: Long = 2000L
    private const val COMMS_1_UNTIL_SECONDS: Long = 10L
    private const val COMMS_1_FIRST_MINTED_TX_ID: Long = 100L

    /**
     * Sentinel unit name used by the pilot-notified frequency-change
     * projection. [RequestFrequencyChange] carries only a
     * (possibly absent) [xyz.easiersaid.twr.protocol.Frequency] — the next
     * unit is not known from the request alone. Downstream selectors treat
     * this value as "unspecified next unit".
     */
    internal const val PILOT_NOTIFIED_UNIT_PLACEHOLDER: String = "UNSPECIFIED"
}

private data class CriticalPhaseSample(
    val traceIndex: Int,
    val kind: CriticalPhaseKind?,
)

private data class OpenCriticalWindow(
    val aircraftId: AircraftId,
    val phase: CriticalPhaseKind,
    val start: Int,
    val last: Int,
) {
    fun toSpec(): CriticalWindowSpec =
        CriticalWindowSpec(
            aircraftId = aircraftId,
            phase = phase,
            start = EvidenceSequence(start),
            end = EvidenceSequence(last),
        )
}

private data class CriticalWindowSpec(
    val aircraftId: AircraftId,
    val phase: CriticalPhaseKind,
    val start: EvidenceSequence,
    val end: EvidenceSequence,
)

private data class CriticalWindowBuild(
    val open: OpenCriticalWindow?,
    val closed: List<CriticalWindowSpec>,
)
