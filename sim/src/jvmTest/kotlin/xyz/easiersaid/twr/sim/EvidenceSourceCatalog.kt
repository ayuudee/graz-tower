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
    object Communications {
        val ReceptionDoubtRepetitionRequested: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::communications_2_8_1_en::0a964f42b6100596",
            title = "Doubtful reception shall trigger a repetition request",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val RequiredProcedures: Set<EvidenceSourceRef> =
            setOf(ReceptionDoubtRepetitionRequested)
    }

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

        val AtcRouteClearancesRequiredReadback: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::readback_2_8_3_en::58594a8ee6243296",
            title = "ATC route clearances shall always be read back",
            claimScope = EvidenceSourceClaimScope.StructuralProtocol,
        )

        val OtherClearancesAcknowledged: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::readback_2_8_3_en::4b6ece953649da07",
            title = "Other clearances or instructions shall be read back or acknowledged",
            claimScope = EvidenceSourceClaimScope.StructuralProtocol,
        )

        val ClearancePacingAdvisory: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::readback_2_8_3_en::ac9111d240cfd2c2",
            title = "Controllers should pace clearances and avoid line-up/take-off windows",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val RequiredItems: Set<EvidenceSourceRef> =
            setOf(
                RunwayOperationsRequiredReadback,
                OperationalParametersRequiredReadback,
                AtcRouteClearancesRequiredReadback,
                OtherClearancesAcknowledged,
            )

        val AdvisoryItems: Set<EvidenceSourceRef> =
            setOf(ClearancePacingAdvisory)
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

        val TaxiLimitBeyondRunwayRequiresCrossOrHold: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::taxi_4_4_en::1367907005a34ad1",
            title = "Taxi limit beyond runway requires cross clearance or hold-short instruction",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val AtisAcknowledgedNoDepartureInformationRequired: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::taxi_4_4_en::53f33b6da4f2be58",
            title = "ATIS acknowledgement removes need to pass departure information in taxi instruction",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val RunwayVacatedBeyondHoldingPosition: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::taxi_4_4_en::eadf2541fcd51825",
            title = "Runway vacated when entire aircraft beyond runway-holding position",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val HoldingPointLimit: Set<EvidenceSourceRef> =
            setOf(DepartingClearanceLimitNormallyHoldingPoint, TaxiInstructionClearanceLimitMandatory)

        val Chunk03AuditItems: Set<EvidenceSourceRef> =
            setOf(
                TaxiInstructionClearanceLimitMandatory,
                TaxiLimitBeyondRunwayRequiresCrossOrHold,
                AtisAcknowledgedNoDepartureInformationRequired,
                RunwayVacatedBeyondHoldingPosition,
            )
    }

    object TakeoffProcedures {
        val TowerTransferAtHoldingPosition: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::19cfd36a9fce4587",
            title = "Aircraft usually transferred to TOWER at or approaching runway holding position",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val Chunk04ScenarioItems: Set<EvidenceSourceRef> =
            setOf(TowerTransferAtHoldingPosition)
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

    object TransferCommunications {
        val ControllerAdvisedFrequencyChange: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::transfer_communications_2_8_2_en::40382df156ad071e",
            title = "Aircraft shall be advised before frequency change",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val PilotNotifiesAbsentAdvice: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::transfer_communications_2_8_2_en::b49ae03cbbb2d538",
            title = "Aircraft shall notify before frequency change absent advice",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val RequiredProcedures: Set<EvidenceSourceRef> =
            setOf(ControllerAdvisedFrequencyChange, PilotNotifiesAbsentAdvice)
    }

    object AerodromeInformation {
        val EssentialAerodromeInformationTiming: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc",
            title = "Essential aerodrome information timing",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )
    }

    object TestProcedures {
        val GroundStationTestSignalDuration: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::test_procedures_2_8_4_en::d67d1f63cbbecd7d",
            title = "Ground-station test signals limited to ten seconds",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )
    }

    object CriticalPhase {
        val CriticalPhaseRadioSilence: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_ch4_intro_start_4_1_to_4_2_en::095624c5163849a4",
            title = "Controller transmissions during critical phases",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )
    }

    object GapSources {
        val EssentialAerodromeInformationTiming: EvidenceSourceRef =
            AerodromeInformation.EssentialAerodromeInformationTiming

        val CriticalPhaseRadioSilence: EvidenceSourceRef =
            CriticalPhase.CriticalPhaseRadioSilence
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
            missingConcept = "Evidence facts do not yet expose a reason-bearing safety-necessity policy for critical-phase controller transmissions.",
            closureTrigger = "Close when critical-phase controller transmission facts can distinguish routine calls from typed safety-necessary calls.",
            tracking = EvidenceBacklogRef.PlanItem("FN43-GAP-2"),
        )
    }

    data object ControllerAdvisedFrequencyTransferProjection : EvidenceGapId {
        override val metadata: EvidenceGapMetadata = EvidenceGapMetadata(
            id = "evidence-gap-controller-advised-frequency-transfer",
            affectedSources = setOf(ICAO9432.TransferCommunications.ControllerAdvisedFrequencyChange),
            missingConcept = "Evidence facts do not expose controller-advised frequency-transfer facts.",
            closureTrigger = "Close when traces emit FrequencyTransfer(mode = ControllerAdvised).",
            tracking = EvidenceBacklogRef.PlanItem("FN44-GAP-1"),
        )
    }

    val All: Set<EvidenceGapId> =
        setOf(
            EssentialAerodromeInformationReceiptProjection,
            CriticalPhaseRadioSilenceProjection,
            ControllerAdvisedFrequencyTransferProjection,
        )
}

object EvidenceSourceCatalog {
    val All: Set<EvidenceSourceRef> =
        ICAO9432.Communications.RequiredProcedures +
            ICAO9432.Readback.RequiredItems +
            ICAO9432.Readback.AdvisoryItems +
            ICAO9432.Taxi.HoldingPointLimit +
            ICAO9432.Taxi.Chunk03AuditItems +
            ICAO9432.TakeoffProcedures.Chunk04ScenarioItems +
            ICAO9432.FinalApproachLanding.TouchAndGo +
            ICAO9432.TransferCommunications.RequiredProcedures +
            setOf(
                ICAO9432.AerodromeInformation.EssentialAerodromeInformationTiming,
                ICAO9432.CriticalPhase.CriticalPhaseRadioSilence,
                ICAO9432.TestProcedures.GroundStationTestSignalDuration,
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
