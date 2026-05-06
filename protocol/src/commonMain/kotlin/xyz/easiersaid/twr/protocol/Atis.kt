package xyz.easiersaid.twr.protocol

/**
 * Automatic Terminal Information Service broadcast — the recurring
 * weather + runway summary every controlled aerodrome publishes.
 * Pass 15 (D-AUDIT.8 closure): closes the "first contact carries no
 * prior information" gap.
 *
 * **Doctrine**: ICAO Annex 11 §4.3 (ATIS service); ICAO Doc 4444
 * §4.5.5 (ATIS broadcast content).
 *
 * Pilots receive ATIS on a separate frequency before first contact
 * with TWR/GND; the first transmission to TWR/GND embeds the letter
 * (`InitialContact.atisCode`) so the controller can verify currency.
 * On letter mismatch the controller issues a `CurrentInformationIs`
 * advisory.
 *
 * **No letter-rotation invariant at the type level**: real ATIS
 * rotates A→Z then wraps to A, but supervisors regenerate fresh
 * reports on material change and may skip letters (regulator-driven,
 * weather-driven). The handler stores any A..Z letter unconditionally;
 * the rotation order is an event-stream property, not a per-update
 * invariant.
 *
 * **Wind discipline** (post-impl FP review S3): [wind] is the concrete
 * [Wind] (a published ATIS always carries a measured wind), distinct
 * from the [WindReport] sealed type which carries a NotReported leaf
 * for query/observation contexts.
 */
data class Atis(
    val letter: Char,
    val aerodrome: AerodromeId,
    val configuration: RunwayConfiguration,
    val wind: Wind,
    val qnh: PressureSetting?,
    val visibility: Int?,
    val generatedAt: SimTime,
) {
    init {
        require(letter in 'A'..'Z') { "ATIS letter must be A..Z, got '$letter'" }
    }
}

/**
 * Pure helper computing the next ATIS letter in canonical rotation:
 * A→B→C→...→Z→A. Useful for tests and supervisors that want to
 * advance the letter on a material change. **NOT enforced as a
 * handler invariant** — real-world ATIS rotation includes skips.
 */
fun nextAtisLetter(c: Char): Char {
    require(c in 'A'..'Z') { "ATIS letter must be A..Z, got '$c'" }
    return if (c == 'Z') 'A' else c + 1
}
