package xyz.easiersaid.twr.migration.world

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import xyz.easiersaid.twr.protocol.RoleName

/**
 * Parse-time totality for [RoleName] (Pass 6, FP review P.1).
 *
 * Replaces the loader-side `RoleName.valueOf(name)` partial-function call
 * with a parse-time check: invalid role tokens fail with a typed
 * [SerializationException] at JSON decode, never reaching the loader.
 */
internal object RoleNameSerializer : KSerializer<RoleName> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RoleName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: RoleName) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): RoleName {
        val raw = decoder.decodeString()
        return RoleName.entries.firstOrNull { it.name == raw }
            ?: throw SerializationException(
                "Unknown role name '$raw'; expected one of ${RoleName.entries.map { it.name }}",
            )
    }
}
