package xyz.easiersaid.twr.protocol

import arrow.core.Either
import arrow.core.left
import arrow.core.right

/**
 * ICAO Doc 8643 aircraft type designator (e.g., "C172", "B738", "A320").
 *
 * Pass 10 (D-AUDIT.4): typed wrapper matching the codebase's
 * [Callsign] / [AircraftId] pattern. Smart constructor enforces Doc 8643
 * shape: 2-4 alphanumeric characters, uppercase. Adding a manifest
 * loader (D-AUDIT.4.B-FOLLOWUP) wires the smart constructor into JSON
 * deserialisation.
 *
 * Validation result is [Either] (post-impl FP review F.2): boundary
 * parsers in this codebase return typed errors, not nullable. The
 * manifest loader will need this shape.
 */
@JvmInline
value class IcaoTypeDesignator private constructor(val raw: String) {
    override fun toString(): String = raw

    companion object {
        private val VALID = Regex("[A-Z0-9]{2,4}")

        /** Smart constructor; returns [IcaoDesignatorError] on invalid input. */
        fun of(raw: String): Either<IcaoDesignatorError, IcaoTypeDesignator> =
            if (VALID.matches(raw)) IcaoTypeDesignator(raw).right()
            else IcaoDesignatorError(raw).left()

        /**
         * Compile-time-known designator. Use only at value-defining sites
         * (the [AircraftType] companion-object constants). Tests should
         * use [of] and assert `.isRight()` at the boundary.
         */
        fun unsafe(raw: String): IcaoTypeDesignator {
            require(VALID.matches(raw)) { "invalid ICAO Doc 8643 designator: '$raw'" }
            return IcaoTypeDesignator(raw)
        }
    }
}

/**
 * Failure to parse an [IcaoTypeDesignator]. Carries the offending raw
 * input for diagnostic purposes; the validation rule (2-4 alphanumeric
 * uppercase) is documented on [IcaoTypeDesignator].
 */
data class IcaoDesignatorError(val raw: String)
