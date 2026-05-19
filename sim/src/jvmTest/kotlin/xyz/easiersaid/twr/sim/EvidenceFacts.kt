package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.pilot.CircuitOutcome
import xyz.easiersaid.twr.pilot.PilotPhase
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.testing.TransmissionRecord

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
    Sample,
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

    fun fromLowgCircuitTrace(trace: LowgCircuitTrace): EvidenceFactSet {
        return fromTransmissionRecords(
            scenarioId = trace.scenarioId,
            records = trace.records,
            finalAircraft = trace.finalAircraft,
            diagnostic = trace.diagnostic,
        )
    }

    fun fromTransmissionRecords(
        scenarioId: String,
        records: List<TransmissionRecord>,
        finalAircraft: Map<AircraftId, ObservedAircraft> = emptyMap(),
        diagnostic: String = "Projected evidence facts",
    ): EvidenceFactSet {
        val transmissionFacts = records.flatMapIndexed { index, record ->
            recordFacts(
                scenarioId = scenarioId,
                recordIndex = index,
                record = record,
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
    ): List<EvidenceFact> =
        when (val utterance = record.utterance) {
            is Utterance.FromController -> when (val speaker = record.speaker) {
                is SpeakerRef.Controller -> controllerFacts(
                    scenarioId = scenarioId,
                    recordIndex = recordIndex,
                    record = record,
                    controller = speaker,
                    output = utterance.output,
                )

                is SpeakerRef.Pilot -> emptyList()
            }

            is Utterance.FromPilot -> when (val speaker = record.speaker) {
                is SpeakerRef.Controller -> emptyList()
                is SpeakerRef.Pilot -> pilotFacts(
                    scenarioId = scenarioId,
                    recordIndex = recordIndex,
                    record = record,
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
    ): List<EvidenceFact> =
        when (output) {
            is ControllerOutput.Instruct -> listOf(
                fact(
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
                ),
            )

            is ControllerOutput.Respond -> emptyList()
        }

    private fun pilotFacts(
        scenarioId: String,
        recordIndex: Int,
        record: TransmissionRecord,
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
        val report = transmission as? Report
        return if (report == null) {
            listOf(transmissionFact)
        } else {
            listOf(
                transmissionFact,
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
}
