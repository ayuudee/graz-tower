package xyz.easiersaid.twr.protocol

/**
 * ICAO Doc 8643 aircraft type designator (e.g., "C172", "B738", "A320").
 *
 * Pass 10 (D-AUDIT.4): typed wrapper matching the codebase's
 * [Callsign] / [AircraftId] pattern. Smart constructor enforces Doc 8643
 * shape: 2-4 alphanumeric characters, uppercase. Adding a manifest
 * loader (D-AUDIT.4.B-FOLLOWUP) will wire the smart constructor into
 * JSON deserialisation.
 */
@JvmInline
value class IcaoTypeDesignator private constructor(val raw: String) {
    override fun toString(): String = raw

    companion object {
        private val VALID = Regex("[A-Z0-9]{2,4}")

        /** Smart constructor; returns null on invalid input. */
        fun of(raw: String): IcaoTypeDesignator? =
            if (VALID.matches(raw)) IcaoTypeDesignator(raw) else null

        /**
         * Compile-time-known designator. Use only at value-defining sites
         * (the [AircraftType] companion-object constants). Tests should
         * use [of] and assert non-null at the boundary.
         */
        fun unsafe(raw: String): IcaoTypeDesignator {
            require(VALID.matches(raw)) { "invalid ICAO Doc 8643 designator: '$raw'" }
            return IcaoTypeDesignator(raw)
        }
    }
}
