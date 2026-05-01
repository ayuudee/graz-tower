package xyz.easiersaid.twr.migration.world

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Parse-time totality for [LoaderAuthority] (Pass 6 post-impl, FP-P.1).
 *
 * The original Pass 6 design left `authorities: List<String>` on the schema
 * and `LoaderAuthority.fromString` throwing `IllegalArgumentException` from
 * the loader. That was asymmetric with [RoleNameSerializer] /
 * [FrequencySerializer], which fail at parse time with typed
 * [SerializationException].
 *
 * Result of this fix: `WorldCandidateLoader` is total over a successfully
 * parsed schema across all three boundary types (`RoleName`, `Frequency`,
 * `LoaderAuthority`).
 */
internal object LoaderAuthoritySerializer : KSerializer<LoaderAuthority> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LoaderAuthority", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: LoaderAuthority) {
        when (value) {
            is LoaderAuthority.Placeholder -> encoder.encodeString(LoaderAuthority.PLACEHOLDER_TOKEN)
        }
    }

    override fun deserialize(decoder: Decoder): LoaderAuthority {
        val raw = decoder.decodeString()
        return when (raw) {
            LoaderAuthority.PLACEHOLDER_TOKEN -> LoaderAuthority.Placeholder
            else -> throw SerializationException(
                "Unknown authority token '$raw'. " +
                    "Pass 6 only recognises '${LoaderAuthority.PLACEHOLDER_TOKEN}'; " +
                    "D-AUDIT.11 owns real authority strings.",
            )
        }
    }
}
