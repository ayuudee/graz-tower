package xyz.easiersaid.twr.migration.world

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import xyz.easiersaid.twr.protocol.Frequency

/**
 * Parse-time totality for [Frequency] (Pass 6, FP review P.2).
 *
 * Replaces the loader-side `Frequency.unsafe(...)` call with a parse-time
 * smart-constructor invocation: out-of-range or malformed frequencies fail
 * with a typed [SerializationException] at JSON decode, never reaching the
 * loader. The `:migration` source contains zero `Frequency.unsafe(...)` calls
 * after Pass 6.
 *
 * The on-disk representation is a JSON string ("118.200") matching the
 * canonical hand-authored form used in `structured-airport-package.json`.
 * The string form preserves the controller's verbatim statement (e.g.
 * "118.205" vs "118.20") which a numeric form would round.
 */
internal object FrequencySerializer : KSerializer<Frequency> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FrequencyMhz", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Frequency) {
        encoder.encodeString(value.mhz)
    }

    override fun deserialize(decoder: Decoder): Frequency {
        val raw = decoder.decodeString()
        return Frequency(raw).fold(
            { msg -> throw SerializationException("Invalid VHF frequency '$raw': $msg") },
            { it },
        )
    }
}
