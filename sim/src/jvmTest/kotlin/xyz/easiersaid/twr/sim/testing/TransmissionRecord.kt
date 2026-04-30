package xyz.easiersaid.twr.sim.testing

import arrow.core.Option
import arrow.core.toOption
import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.Report
import xyz.easiersaid.twr.protocol.ReportEvent
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.sim.ReceiverRef
import xyz.easiersaid.twr.sim.SimEvent
import xyz.easiersaid.twr.sim.SpeakerRef
import xyz.easiersaid.twr.sim.Utterance

/**
 * Typed test-side record of a transmission that crossed the wire. Pass 4
 * (test-infrastructure) replaces G0's `MutableList<String>` of `tx.toString()`
 * renderings with this typed form.
 *
 * `frequency` is intentionally not stored — it's redundant with `speaker`
 * (controller speakers carry their frequency on `ControllerSpec`; pilot
 * speakers transmit on the receiver-controller's frequency at transmission
 * time). Storing it would admit a "speaker and frequency disagree" bug class.
 */
data class TransmissionRecord(
    val time: SimTime,
    val speaker: SpeakerRef,
    val receiver: ReceiverRef,
    val utterance: Utterance,
)

/** Extract a typed [TransmissionRecord] from a [SimEvent.TransmissionStart]. */
fun SimEvent.TransmissionStart.toTransmissionRecord(): TransmissionRecord =
    TransmissionRecord(
        time = transmission.startedAt,
        speaker = transmission.speaker,
        receiver = transmission.receiver,
        utterance = transmission.utterance,
    )

/** First controller-issued instruction of type [I] addressed to [aircraft]. */
inline fun <reified I : AtcInstruction> List<TransmissionRecord>.firstControllerInstructionOf(
    aircraft: AircraftId,
): Option<TransmissionRecord> = firstOrNull { record ->
    val out = (record.utterance as? Utterance.FromController)?.output ?: return@firstOrNull false
    val instruct = out as? ControllerOutput.Instruct ?: return@firstOrNull false
    instruct.target == aircraft && instruct.instruction is I
}.toOption()

/** First pilot report from [aircraft] containing an event of type [E]. */
inline fun <reified E : ReportEvent> List<TransmissionRecord>.firstPilotReportOf(
    aircraft: AircraftId,
): Option<TransmissionRecord> = firstOrNull { record ->
    val tx = (record.utterance as? Utterance.FromPilot)?.transmission ?: return@firstOrNull false
    val report = tx as? Report ?: return@firstOrNull false
    record.speaker is SpeakerRef.Pilot
        && (record.speaker as SpeakerRef.Pilot).aircraftId == aircraft
        && report.events.any { it is E }
}.toOption()

/** [SimTime] of the first record matching [predicate]. */
fun List<TransmissionRecord>.timeOfFirst(
    predicate: (TransmissionRecord) -> Boolean,
): Option<SimTime> = firstOrNull(predicate)?.time.toOption()

/** Pretty-print for diagnostic output. */
fun TransmissionRecord.format(): String =
    "[${time.millis}ms] $speaker → $receiver: $utterance"

/** Multi-line concatenation of records for failure-message output. */
fun List<TransmissionRecord>.formatAll(): String =
    joinToString(separator = "\n  ", prefix = "  ") { it.format() }
