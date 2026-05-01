package xyz.easiersaid.twr.sim

import xyz.easiersaid.twr.controller.ControllerOutput
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.AtcInstruction
import xyz.easiersaid.twr.protocol.ClearedForTakeoff
import xyz.easiersaid.twr.protocol.ClearedToLand
import xyz.easiersaid.twr.protocol.ClearedTouchAndGo
import xyz.easiersaid.twr.protocol.ContactFrequency
import xyz.easiersaid.twr.protocol.ControllerId
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.HoldPosition
import xyz.easiersaid.twr.protocol.HoldShortOf
import xyz.easiersaid.twr.protocol.LineUpAndWait
import xyz.easiersaid.twr.protocol.PilotTransmission
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.RoleName
import xyz.easiersaid.twr.protocol.SimDuration
import xyz.easiersaid.twr.protocol.SimTime
import xyz.easiersaid.twr.protocol.TaxiClearance

/**
 * Slice 4d: communications layer.
 *
 * Transmissions are modelled as a start/end pair with a speaker, a listener
 * frequency, and a duration. The simulation treats a frequency as a shared
 * resource: two transmissions whose on-air windows overlap are both marked
 * [InFlightTransmission.steppedOn] and their content is never delivered —
 * that's the natural "step-on" behaviour, no channel-resolution flag needed.
 *
 * The timing numbers are drawn from:
 *   - Lutz (2022), NASA NTRS 20220007116, for per-domain controller reply
 *     latency (ground ≈ 0.44 s, approach 0.67 s, centre 0.94 s).
 *   - Cardosi DOT/FAA tables for utterance duration; we bucket instructions
 *     coarsely rather than simulating phoneme-level counts.
 *   - A nominal 500 ms pilot cognitive-processing delay between hearing an
 *     instruction and being able to act or reply.
 *
 * Numbers are deterministic for 4d (no jitter). When aircraft-type performance
 * and per-pilot variance arrive in a later slice, they will draw from
 * [SimRandom] so determinism is preserved.
 */

/** Identifies a transmission across its start/end lifecycle. */
@JvmInline
value class TransmissionId(val value: Long)

/** Who is speaking on a transmission. */
sealed interface SpeakerRef {
    data class Pilot(val aircraftId: AircraftId) : SpeakerRef
    data class Controller(val id: ControllerId) : SpeakerRef
}

/** Who the speaker is addressing. "Party-line" secondary listeners are a later slice. */
sealed interface ReceiverRef {
    data class Pilot(val aircraftId: AircraftId) : ReceiverRef
    data class Controller(val id: ControllerId) : ReceiverRef
}

/**
 * The content of a transmission.
 *
 * Keeping pilot and controller utterances as a common [Utterance] lets the
 * frequency layer treat them identically — a transmission is a transmission
 * regardless of direction, and the step-on rule applies uniformly.
 */
sealed interface Utterance {
    data class FromPilot(val transmission: PilotTransmission) : Utterance
    data class FromController(val output: ControllerOutput) : Utterance
}

/**
 * A transmission currently on (or about to go on) the air.
 *
 * Created at [SimEvent.TransmissionStart]. On any subsequent start against
 * the same [frequency] whose window overlaps, both records have [steppedOn]
 * flipped to `true` — that's what "overlap ⇒ step-on" means concretely.
 * Removed at [SimEvent.TransmissionEnd]; delivery is a function of the
 * [steppedOn] flag at that point.
 */
data class InFlightTransmission(
    val id: TransmissionId,
    val speaker: SpeakerRef,
    val receiver: ReceiverRef,
    val frequency: Frequency,
    val utterance: Utterance,
    val startedAt: SimTime,
    val endsAt: SimTime,
    val steppedOn: Boolean = false,
)

/** Latencies and durations used by the comms layer. */
object CommsConstants {
    /**
     * Per-domain controller reply latency — the time between a controller
     * observing that a reply is needed (cycle start) and actually beginning
     * the transmission. Lutz 2022, averaged over radar/tower observations.
     */
    val CONTROLLER_REPLY_LATENCY: Map<RoleName, SimDuration> = mapOf(
        RoleName.TOWER to SimDuration.ofMillis(440),
        RoleName.GROUND to SimDuration.ofMillis(440),
        RoleName.APPROACH to SimDuration.ofMillis(670),
        RoleName.AREA_CONTROL to SimDuration.ofMillis(940),
    )

    /** Default when the role isn't in the Lutz dataset — use tower-like latency. */
    val DEFAULT_CONTROLLER_REPLY_LATENCY: SimDuration = SimDuration.ofMillis(500)

    /**
     * Pilot cognitive processing delay — time from finishing receipt of a
     * transmission to having parsed and acted on it. Same figure for all
     * pilots in 4d; variance lands with aircraft-type performance.
     */
    val PILOT_COGNITIVE_DELAY: SimDuration = SimDuration.ofMillis(500)

    /**
     * Pilot readback-preparation delay — extra time after processing before
     * the pilot actually keys the mic for the readback. Small but non-zero
     * so readbacks don't collide with the controller's trailing edge.
     */
    val PILOT_READBACK_PREP: SimDuration = SimDuration.ofMillis(300)

    /**
     * Delay from completing the readback on the old frequency to making the
     * initial call on the new frequency after a [ContactFrequency] handoff.
     * Models the pilot changing the radio dial, letting the new freq settle,
     * and waiting for a gap on-channel before keying up.
     */
    val PILOT_FREQ_SWITCH_DELAY: SimDuration = SimDuration.ofMillis(2000)
}

/**
 * Estimate how long an [utterance] will take on the air.
 *
 * Coarser than Cardosi's phoneme-level tables — instructions fall into three
 * buckets (short / medium / long) picked by instruction class. Accurate
 * enough to get step-on timing right while a proper Cardosi table lands with
 * the slice that introduces aircraft-type performance curves.
 */
fun utteranceDuration(utterance: Utterance): SimDuration = when (utterance) {
    is Utterance.FromController -> controllerUtteranceDuration(utterance.output)
    is Utterance.FromPilot -> pilotUtteranceDuration(utterance.transmission)
}

private fun controllerUtteranceDuration(output: ControllerOutput): SimDuration = when (output) {
    is ControllerOutput.Instruct -> instructionDuration(output.instruction)
    is ControllerOutput.Respond -> SimDuration.ofMillis(1500)
}

/**
 * The `else` here is deliberate. [AtcInstruction] is a sealed hierarchy with
 * ~60 variants; the buckets above cover the instructions that currently drive
 * Phase-4 scenarios plus the few whose timing materially deviates from the
 * "short routine clearance" default. A future slice may promote individual
 * cases out of the default (e.g. when GoAround phraseology / wake caution
 * lands, its duration will differ). Cardosi-tabulated phoneme-level timings
 * arrive with the aircraft-type-performance slice.
 */
@Suppress("MagicNumber")
private fun instructionDuration(instruction: AtcInstruction): SimDuration = when (instruction) {
    is ClearedForTakeoff, is ClearedToLand, is ClearedTouchAndGo ->
        SimDuration.ofMillis(4500) // includes wind/traffic
    is TaxiClearance -> SimDuration.ofMillis(3500)
    is ContactFrequency -> SimDuration.ofMillis(2500)
    is LineUpAndWait -> SimDuration.ofMillis(2500)
    is HoldShortOf -> SimDuration.ofMillis(2000)
    is HoldPosition -> SimDuration.ofMillis(1500)
    else -> SimDuration.ofMillis(3000)
}

/**
 * Same contract as [instructionDuration]: the `else` defaults to a short
 * initial-call / ready-report-length utterance. Readback atom counts will
 * refine [Readback] duration when the phraseology layer grows.
 */
@Suppress("MagicNumber")
private fun pilotUtteranceDuration(transmission: PilotTransmission): SimDuration = when (transmission) {
    is Readback -> SimDuration.ofMillis(2500)
    else -> SimDuration.ofMillis(2000)
}

/**
 * Resolve the controller that currently owns [aircraftId] — used to route
 * pilot-to-controller transmissions. Returns null if no controller claims
 * responsibility (responsibility-transfer gaps are a 4e concern).
 */
fun responsibleController(state: SimState, aircraftId: AircraftId): ControllerSpec? =
    state.controllers.values.firstOrNull { aircraftId in it.responsibilities }
