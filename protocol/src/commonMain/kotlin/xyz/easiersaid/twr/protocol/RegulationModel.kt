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

        private val AUTHORITY_NAMES = mapOf(
            "SERA" to "SERA",
            "ICAO_ANNEX_2" to "ICAO Annex 2",
            "ICAO_ANNEX_11" to "ICAO Annex 11",
            "ICAO_4444" to "ICAO Doc 4444",
            "ICAO_9432" to "ICAO Doc 9432",
            "ICAO_9870" to "ICAO Doc 9870",
            "CAP_413" to "CAP 413",
        )
    }
}

/** Urgency classification. Determines priority in arbitration — safety actions always execute. */
enum class Urgency { SAFETY, TIME_SENSITIVE, PROGRESSION, INFORMATIONAL }
