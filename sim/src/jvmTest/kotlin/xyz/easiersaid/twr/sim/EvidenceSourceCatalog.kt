package xyz.easiersaid.twr.sim

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class EvidenceSourceRecord(
    val canonicalId: String,
    val title: String,
    val claimScope: EvidenceSourceClaimScope,
) {
    init {
        require(canonicalId.isNotBlank()) { "source canonical id must not be blank" }
        require(title.isNotBlank()) { "source title must not be blank" }
    }
}

enum class EvidenceSourceClaimScope {
    StructuralProtocol,
    ScenarioBehavior,
    TypedInstructionTraceOnly,
    ProjectionGapSource,
}

class EvidenceSourceRef internal constructor(
    val record: EvidenceSourceRecord,
) {
    val canonicalId: String = record.canonicalId

    fun toSourceUnitRef(): SourceUnitRef =
        SourceUnitRef(canonicalId)

    override fun equals(other: Any?): Boolean =
        other is EvidenceSourceRef && other.record == record

    override fun hashCode(): Int =
        record.hashCode()

    override fun toString(): String =
        canonicalId
}

sealed interface EvidenceGapId {
    val metadata: EvidenceGapMetadata
}

data class EvidenceGapMetadata(
    val id: String,
    val affectedSources: Set<EvidenceSourceRef>,
    val missingConcept: String,
    val closureTrigger: String,
    val tracking: EvidenceBacklogRef,
) {
    init {
        require(id.isNotBlank()) { "gap id must not be blank" }
        require(affectedSources.isNotEmpty()) { "gap must name affected source refs" }
        require(missingConcept.isNotBlank()) { "gap missing concept must not be blank" }
        require(closureTrigger.isNotBlank()) { "gap closure trigger must not be blank" }
    }
}

sealed interface EvidenceBacklogRef {
    val id: String

    data class PlanItem(override val id: String) : EvidenceBacklogRef {
        init {
            require(id.isNotBlank()) { "plan item id must not be blank" }
        }
    }
}

private fun source(
    canonicalId: String,
    title: String,
    claimScope: EvidenceSourceClaimScope,
): EvidenceSourceRef =
    EvidenceSourceRef(EvidenceSourceRecord(canonicalId = canonicalId, title = title, claimScope = claimScope))

object ICAO9432 {
    object Readback {
        val RunwayOperationsRequiredReadback: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::readback_2_8_3_en::15940532b37f8528",
            title = "Runway operations shall always be read back",
            claimScope = EvidenceSourceClaimScope.StructuralProtocol,
        )

        val OperationalParametersRequiredReadback: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::readback_2_8_3_en::25c245bc4728ed60",
            title = "Operational parameters shall always be read back",
            claimScope = EvidenceSourceClaimScope.StructuralProtocol,
        )

        val RequiredItems: Set<EvidenceSourceRef> =
            setOf(RunwayOperationsRequiredReadback, OperationalParametersRequiredReadback)
    }

    object Taxi {
        val DepartingClearanceLimitNormallyHoldingPoint: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::taxi_4_4_en::417f64324f7495bf",
            title = "Departing aircraft taxi clearance limit normally holding point",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val TaxiInstructionClearanceLimitMandatory: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::taxi_4_4_en::b9e7fc3605fe616e",
            title = "Taxi instruction must contain a clearance limit",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val HoldingPointLimit: Set<EvidenceSourceRef> =
            setOf(DepartingClearanceLimitNormallyHoldingPoint, TaxiInstructionClearanceLimitMandatory)
    }

    object FinalApproachLanding {
        val TouchAndGoRequest: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::final_approach_landing_4_7_en::0ece166e11d7728e",
            title = "Pilot may request touch-and-go in circuit training",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val ClearedTouchAndGoPhrase: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::final_approach_landing_4_7_en::a4c8fffd8a61adb4",
            title = "ATC may clear touch-and-go using CLEARED TOUCH AND GO",
            claimScope = EvidenceSourceClaimScope.TypedInstructionTraceOnly,
        )

        val TouchAndGo: Set<EvidenceSourceRef> =
            setOf(TouchAndGoRequest, ClearedTouchAndGoPhrase)
    }

    object GapSources {
        val EssentialAerodromeInformationTiming: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc",
            title = "Essential aerodrome information timing",
            claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
        )

        val CriticalPhaseRadioSilence: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4",
            title = "Controller transmissions during critical phases",
            claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
        )
    }
}

object EvidenceGaps {
    data object EssentialAerodromeInformationReceiptProjection : EvidenceGapId {
        override val metadata: EvidenceGapMetadata = EvidenceGapMetadata(
            id = "evidence-gap-essential-aerodrome-information-receipt",
            affectedSources = setOf(ICAO9432.GapSources.EssentialAerodromeInformationTiming),
            missingConcept = "Evidence facts do not expose aerodrome-information receipt before taxi/final approach.",
            closureTrigger = "Close when the observation port emits typed aerodrome-information receipt facts.",
            tracking = EvidenceBacklogRef.PlanItem("FN43-GAP-1"),
        )
    }

    data object CriticalPhaseRadioSilenceProjection : EvidenceGapId {
        override val metadata: EvidenceGapMetadata = EvidenceGapMetadata(
            id = "evidence-gap-critical-phase-radio-silence",
            affectedSources = setOf(ICAO9432.GapSources.CriticalPhaseRadioSilence),
            missingConcept = "Evidence facts do not expose critical-phase windows or safety-necessity classifications.",
            closureTrigger = "Close when the observation port emits phase-window and safety-necessity facts.",
            tracking = EvidenceBacklogRef.PlanItem("FN43-GAP-2"),
        )
    }

    val All: Set<EvidenceGapId> =
        setOf(EssentialAerodromeInformationReceiptProjection, CriticalPhaseRadioSilenceProjection)
}

object EvidenceSourceCatalog {
    val All: Set<EvidenceSourceRef> =
        ICAO9432.Readback.RequiredItems +
            ICAO9432.Taxi.HoldingPointLimit +
            ICAO9432.FinalApproachLanding.TouchAndGo +
            setOf(
                ICAO9432.GapSources.EssentialAerodromeInformationTiming,
                ICAO9432.GapSources.CriticalPhaseRadioSilence,
            )

    fun validateAgainstRegistry(
        registryRoot: Path = defaultRegistryRoot(),
    ): EvidenceSourceCatalogValidation {
        val results = All.map { source -> validate(source.record, registryRoot) }
        return EvidenceSourceCatalogValidation(results)
    }

    fun validateRecordAgainstRegistry(
        record: EvidenceSourceRecord,
        registryRoot: Path = defaultRegistryRoot(),
    ): EvidenceSourceValidation =
        validate(record, registryRoot)

    private fun validate(record: EvidenceSourceRecord, registryRoot: Path): EvidenceSourceValidation {
        val parts = record.canonicalId.split("::")
        if (parts.size != 3) {
            return EvidenceSourceValidation.Invalid(
                record = record,
                reason = "canonical id must have exact document::section::hash registry form",
            )
        }
        val documentId = parts[0]
        val sectionId = parts[1]
        val candidatePath = registryRoot.resolve(documentId).resolve(sectionId).resolve("${record.canonicalId}.json")
        if (!Files.exists(candidatePath)) {
            return EvidenceSourceValidation.Invalid(
                record = record,
                reason = "registry record not found at $candidatePath",
            )
        }
        val json = Json.parseToJsonElement(Files.readString(candidatePath)).jsonObject
        val jsonCanonicalId = json.requiredString("canonicalId")
        if (jsonCanonicalId != record.canonicalId) {
            return EvidenceSourceValidation.Invalid(
                record = record,
                reason = "registry canonicalId mismatch: $jsonCanonicalId",
            )
        }
        val lifecycleState = json.requiredObject("lifecycle").requiredString("state")
        if (lifecycleState != "accepted") {
            return EvidenceSourceValidation.Invalid(
                record = record,
                reason = "registry lifecycle is not accepted: $lifecycleState",
            )
        }
        return EvidenceSourceValidation.Valid(record = record, registryPath = candidatePath)
    }

    private fun defaultRegistryRoot(): Path {
        val start = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        val repoRoot = generateSequence(start) { path -> path.parent }
            .first { path -> Files.exists(path.resolve("settings.gradle.kts")) }
        return repoRoot.resolve("research/tools/requirements-spike/registry/ollama_first/candidates")
    }
}

data class EvidenceSourceCatalogValidation(
    val results: List<EvidenceSourceValidation>,
) {
    val failures: List<EvidenceSourceValidation.Invalid> = results.filterIsInstance<EvidenceSourceValidation.Invalid>()

    fun requireValid() {
        require(failures.isEmpty()) {
            failures.joinToString(prefix = "Evidence source catalog validation failed:\n") { failure ->
                "- ${failure.record.canonicalId}: ${failure.reason}"
            }
        }
    }
}

sealed interface EvidenceSourceValidation {
    val record: EvidenceSourceRecord

    data class Valid(
        override val record: EvidenceSourceRecord,
        val registryPath: Path,
    ) : EvidenceSourceValidation

    data class Invalid(
        override val record: EvidenceSourceRecord,
        val reason: String,
    ) : EvidenceSourceValidation
}

class EvidenceSourceCitationScope {
    private val citedSources: MutableList<EvidenceSourceRef> = mutableListOf()

    val sources: List<EvidenceSourceRef>
        get() = citedSources.toList()

    fun cites(vararg refs: EvidenceSourceRef) {
        require(refs.isNotEmpty()) { "evidence case must cite at least one typed source ref" }
        citedSources += refs
    }

    fun cites(refs: Iterable<EvidenceSourceRef>) {
        val values = refs.toList()
        require(values.isNotEmpty()) { "evidence case must cite at least one typed source ref" }
        citedSources += values
    }
}

private fun JsonObject.requiredObject(name: String): JsonObject =
    requireNotNull(this[name]) { "registry record missing '$name'" }.jsonObject

private fun JsonObject.requiredString(name: String): String =
    requireNotNull(this[name]) { "registry record missing '$name'" }.jsonPrimitive.content
