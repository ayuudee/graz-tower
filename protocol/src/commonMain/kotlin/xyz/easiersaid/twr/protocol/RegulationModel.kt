package xyz.easiersaid.twr.protocol

/** Unique identifier for an obligation. */
@JvmInline
value class ObligationId(val value: String)

/** How binding: MANDATORY (violation is always wrong), RECOMMENDED (departure requires justification), DISCRETIONARY (technique choice). */
enum class ObligationStrength { MANDATORY, RECOMMENDED, DISCRETIONARY }

/** Where the obligation comes from. Orthogonal to strength — a LOCAL_PROCEDURE can be MANDATORY. */
enum class ObligationSource {
    SINGLE_REGULATION,
    MULTI_REGULATION_SYNTHESIS,
    OPERATIONAL_CONVENTION,
    LOCAL_PROCEDURE,
}

/**
 * What kind of regulatory content: procedural (when to do it), phraseology (how to say it),
 * law (what's required), guidance (supplementary).
 */
enum class RegulationCategory { PROCEDURE, PHRASEOLOGY, LAW, GUIDANCE }

/** A reference to a specific regulatory source. Citation triple: (document, edition, section). */
data class RegulationRef(
    /** Machine-readable document ID: "ICAO_4444", "SERA", "CAP_413" */
    val document: String,
    /** Edition or amendment: "17th ed. (2024)", "EU 923/2012 as amended", etc. */
    val edition: String,
    /** Section within the document: "§7.9", "SERA.5001" */
    val section: String,
    /** Short title: "Take-off clearance", "VMC minima" */
    val title: String,
    /** One-line statement of what the regulation requires. */
    val principle: String,
    val category: RegulationCategory,
) {
    /** Display name derived from document ID. */
    val authority: String get() = AUTHORITY_NAMES[document] ?: document

    /** Format for log/trace output: "ICAO Doc 4444 §7.9 — Take-off clearance" */
    fun formatShort(): String = "$authority $section — $title"

    companion object {
        // Pinned editions for citation stability (resolves tracker #35).
        const val ICAO_4444_EDITION = "17th ed. (2024)"
        const val ICAO_9432_EDITION = "7th ed. (2020)"
        const val SERA_EDITION = "EU 923/2012 as amended"

        /**
         * fn-17 (R9): CAP 413 Edition 24 effective 1 July 2026 — verified
         * against the CAA primary-source PDF SHA
         * `c620cda9b6bdbe8e9ed51b258e4df2f6e3edc839226e53ee2b591cb696a966ac`
         * (captured 2026-05-11 from
         * `https://www.caa.co.uk/publication/download/18165`).
         *
         * Branch A verdict per fn-17.1 verification artifact at
         * `wiki/data-sources/cap413-edition-24-capture.md`: §4.65 (Ed 23
         * ATC-initiated GA) → §4.64 (Ed 24); §4.66 (Ed 23 VFR-continue)
         * → §4.65 (Ed 24); §4.67 (Ed 23 pilot-initiated GA) → §4.66
         * (Ed 24); §4.68 (Ed 23 military) → §4.67 (Ed 24). The full
         * pattern is a uniform `-1` shift across the §4.5x-§4.6x range
         * with §4.49 also renumbered to §4.48.
         *
         * Per-entry application gate (codex round-3/14/18 finding cascade):
         * an entry uses `edition = RegulationRef.CAP_413_EDITION` (Ed 24)
         * only if its R1 Table 2 row in the verification artifact
         * classifies as UNCHANGED, RENUMBERED-updated, or REFINED-updated.
         * For UNREVIEWED rows (currently `CAP413_2_7` and `CAP413_4_46`,
         * pre-existing principle-cite drift unrelated to fn-17's
         * renumbering scope) the entry keeps an inline literal
         * `edition = "Edition 23 Corr (effective 21 January 2021)"`
         * with KDoc citing the corresponding per-entry deferment.
         */
        const val CAP_413_EDITION = "Edition 24 (effective 1 July 2026)"

        private val AUTHORITY_NAMES = mapOf(
            "SERA" to "SERA",
            "ICAO_ANNEX_2" to "ICAO Annex 2",
            "ICAO_ANNEX_6_PII" to "ICAO Annex 6 Part II",
            "ICAO_ANNEX_11" to "ICAO Annex 11",
            "ICAO_4444" to "ICAO Doc 4444",
            "ICAO_9432" to "ICAO Doc 9432",
            "ICAO_9870" to "ICAO Doc 9870",
            "CAP_413" to "CAP 413",
            // fn-14.1 (R14): FAA citations needed for the G3a-react POH-
            // crosswind reactive-GA regulatory anchor set.
            "FAA_AFH" to "FAA Airplane Flying Handbook",
            "FAA_FAR_23" to "14 CFR Part 23",
            "FAA_AIM" to "FAA AIM",
        )
    }
}

/** Urgency classification. Determines priority in arbitration — safety actions always execute. */
enum class Urgency { SAFETY, TIME_SENSITIVE, PROGRESSION, INFORMATIONAL }
