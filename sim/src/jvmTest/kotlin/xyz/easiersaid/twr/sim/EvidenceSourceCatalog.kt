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
    StructuralEvidenceVocabulary,
    ScenarioBehavior,
    TypedInstructionTraceOnly,
    RenderedPhraseologyTrace,
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

        val TakeOffWordUse: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::readback_2_8_3_en::f06dfa1cefd2d649",
            title = "ICAO Doc 9432 Fourth Edition 2007 §2.8.3.3 TAKE OFF word-use restriction",
            claimScope = EvidenceSourceClaimScope.RenderedPhraseologyTrace,
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

        val PhraseologyItems: Set<EvidenceSourceRef> =
            setOf(TakeOffWordUse)
    }

    object ReadbackContinuation {
        val ReadbackTerminatesWithCallsign: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::readback_continuation_2_8_3_7_to_2_8_3_10_en::4c808d67d281ff71",
            title = "ICAO Doc 9432 Fourth Edition 2007 §2.8.3.7 readback terminates with call sign",
            claimScope = EvidenceSourceClaimScope.RenderedPhraseologyTrace,
        )

        val PhraseologyItems: Set<EvidenceSourceRef> =
            setOf(ReadbackTerminatesWithCallsign)
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

        val TakeoffClearancePhrase: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::13264a6ac6d529c3",
            title = "Take-off clearance uses RUNWAY designator CLEARED FOR TAKE-OFF",
            claimScope = EvidenceSourceClaimScope.RenderedPhraseologyTrace,
        )

        val RunwayNumberInTakeoffClearance: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::8af22eb8d9795cef",
            title = "Take-off clearance states runway number where confusion is possible",
            claimScope = EvidenceSourceClaimScope.RenderedPhraseologyTrace,
        )

        val StopImmediatelyPhrase: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::takeoff_procedures_4_5_8_to_4_5_12_en::6b5a0d8b27525cbd",
            title = "Stop-immediately instruction repeats instruction and callsign",
            claimScope = EvidenceSourceClaimScope.RenderedPhraseologyTrace,
        )

        val LineUpAndWaitPhrase: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::takeoff_procedures_4_5_1_to_4_5_5_en::a93888a25f0bad03",
            title = "Line-up instruction uses LINE UP AND WAIT and pilot acknowledges LINING UP",
            claimScope = EvidenceSourceClaimScope.RenderedPhraseologyTrace,
        )

        val Chunk04ScenarioItems: Set<EvidenceSourceRef> =
            setOf(
                TowerTransferAtHoldingPosition,
                TakeoffClearancePhrase,
                LineUpAndWaitPhrase,
                StopImmediatelyPhrase,
                RunwayNumberInTakeoffClearance,
            )
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
            claimScope = EvidenceSourceClaimScope.RenderedPhraseologyTrace,
        )

        val TouchAndGo: Set<EvidenceSourceRef> =
            setOf(TouchAndGoRequest, ClearedTouchAndGoPhrase)
    }

    object GoAroundProcedures {
        val InstrumentMissedApproachDefault: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::go_around_4_8_en::43c33a8e74b02873",
            title = "Instrument approach go-around follows missed approach procedure unless otherwise instructed",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val GoAroundTransmissionBrevity: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::go_around_4_8_en::6c8993a0519d5d64",
            title = "Transmissions to aircraft going around should be brief and minimal",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val VfrContinuesTrafficCircuit: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::go_around_4_8_en::c3581d40a48406bb",
            title = "VFR aircraft continues in the normal traffic circuit after go-around",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val Chunk06GoAroundItems: Set<EvidenceSourceRef> =
            setOf(
                InstrumentMissedApproachDefault,
                GoAroundTransmissionBrevity,
                VfrContinuesTrafficCircuit,
            )
    }

    object AfterLanding {
        val RemainTowerFrequencyUntilRunwayVacated: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::after_landing_4_9_en::4a512226eec962cb",
            title = "Pilot remains on tower frequency until runway vacated unless otherwise advised",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val TaxiInstructionsAfterLandingRoll: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::after_landing_4_9_en::5d742dc66caa1790",
            title = "Controllers should not issue taxi instructions until landing roll completed unless necessary",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val Chunk06PolicyItems: Set<EvidenceSourceRef> =
            setOf(RemainTowerFrequencyUntilRunwayVacated, TaxiInstructionsAfterLandingRoll)
    }

    object TransferCommunications {
        val ContactFrequencyPhrase: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::transfer_communications_2_8_2_en::96720e821bf926cc",
            title = "Frequency transfer uses CONTACT unit frequency and frequency callsign readback",
            claimScope = EvidenceSourceClaimScope.RenderedPhraseologyTrace,
        )

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

        val PhraseologyItems: Set<EvidenceSourceRef> =
            setOf(ContactFrequencyPhrase)
    }

    object AerodromeInformation {
        val EssentialAerodromeInformationTiming: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::1aa5cb7e758055bc",
            title = "Essential aerodrome information timing",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val WaterOnMovementArea: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::01c0a4bc62b1e926",
            title = "Essential aerodrome information includes water on movement areas",
            claimScope = EvidenceSourceClaimScope.StructuralEvidenceVocabulary,
        )

        val OmitWhenKnownFromOtherSources: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::1306eb5cc586df34",
            title = "Essential aerodrome information may be omitted when already known",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val Definition: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::18288908932d5ee8",
            title = "Essential aerodrome information concerns movement area and associated facilities",
            claimScope = EvidenceSourceClaimScope.StructuralEvidenceVocabulary,
        )

        val RoughOrBrokenSurfaces: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::502221a46fcc2879",
            title = "Essential aerodrome information includes rough or broken surfaces",
            claimScope = EvidenceSourceClaimScope.StructuralEvidenceVocabulary,
        )

        val ConstructionOrMaintenance: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::66196c8442372a96",
            title = "Essential aerodrome information includes construction or maintenance work",
            claimScope = EvidenceSourceClaimScope.StructuralEvidenceVocabulary,
        )

        val SnowBanksOrDrifts: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::736cc42a00337fee",
            title = "Essential aerodrome information includes adjacent snow banks or drifts",
            claimScope = EvidenceSourceClaimScope.StructuralEvidenceVocabulary,
        )

        val OtherTemporaryHazards: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::9b6c9dbc2af2b5b0",
            title = "Essential aerodrome information includes other temporary hazards",
            claimScope = EvidenceSourceClaimScope.StructuralEvidenceVocabulary,
        )

        val LightingSystemFailure: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::a531dea421075380",
            title = "Essential aerodrome information includes lighting system failure or irregular operation",
            claimScope = EvidenceSourceClaimScope.StructuralEvidenceVocabulary,
        )

        val SnowSlushOrIce: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::c874e24413f4cdee",
            title = "Essential aerodrome information includes snow slush or ice on movement areas",
            claimScope = EvidenceSourceClaimScope.StructuralEvidenceVocabulary,
        )

        val OtherPertinentInformation: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::essential_aerodrome_information_4_10_en::c92651071a9c009e",
            title = "Essential aerodrome information includes other pertinent information",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val Chunk06ModelItems: Set<EvidenceSourceRef> =
            setOf(
                WaterOnMovementArea,
                OmitWhenKnownFromOtherSources,
                Definition,
                EssentialAerodromeInformationTiming,
                RoughOrBrokenSurfaces,
                ConstructionOrMaintenance,
                SnowBanksOrDrifts,
                OtherTemporaryHazards,
                LightingSystemFailure,
                SnowSlushOrIce,
                OtherPertinentInformation,
            )
    }

    object VehiclesAndTowing {
        val DriverVigilanceAndCompliance: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::122426242abf3225",
            title = "Vehicle drivers should be vigilant and comply with ATC instructions",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val HoldPositionRequiresCallbackPermission: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::6061311019d039c7",
            title = "Vehicle driver shall not proceed from hold position until callback permission",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val ApronProceedMayIncludeTrafficInstructions: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::606d053954eff037",
            title = "Apron vehicle proceed permission may include traffic instructions",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val FirstCallIdentifiesVehicleRoute: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::7759017903acf140",
            title = "Vehicle first-call identifies call sign position destination and route",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val StopAtLimitThenRequestFurtherPermission: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::84c61b2ef1f12ad5",
            title = "Vehicle driver must stop at clearance limit and request further permission",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val StandbyRequiresPermissionBeforeProceeding: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_intro_movement_5_1_to_5_2_en::ed59ee805ff7fe77",
            title = "Vehicle driver shall not proceed after standby until permission",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val DangerousSituationStopInstruction: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::0b45b4a4dc2acc0c",
            title = "Vehicle may need dangerous-situation information and stop instruction",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val RunwayCrossingRequiresPermissionAndAcknowledgement: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::24f7b6a86407ef6f",
            title = "Vehicle runway crossing requires positive permission and acknowledgement",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val TowDriverMustNotAssumeStationAware: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::29be4b26bb851605",
            title = "Tow driver should not assume receiving station is aware aircraft is under tow",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val RunwayVehicleVacatesForAircraftOperation: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::331c1cfc98ead868",
            title = "Vehicle on runway shall be instructed to leave for expected landing or takeoff",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val TowRequestStatesAircraftTypeAndOperator: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::4b103081585bfb71",
            title = "Tow driver should state aircraft type and operator where applicable",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val RunwayVacatedReportAfterVehicleTowClear: EvidenceSourceRef = source(
            canonicalId = "icao9432-extracted::aerodrome_vehicles_crossing_towing_5_3_to_5_4_en::735b3e9ada06105b",
            title = "Vehicle and tow runway-vacated report waits until clear beyond holding point",
            claimScope = EvidenceSourceClaimScope.ScenarioBehavior,
        )

        val Chunk07Items: Set<EvidenceSourceRef> =
            setOf(
                DriverVigilanceAndCompliance,
                HoldPositionRequiresCallbackPermission,
                ApronProceedMayIncludeTrafficInstructions,
                FirstCallIdentifiesVehicleRoute,
                StopAtLimitThenRequestFurtherPermission,
                StandbyRequiresPermissionBeforeProceeding,
                DangerousSituationStopInstruction,
                RunwayCrossingRequiresPermissionAndAcknowledgement,
                TowDriverMustNotAssumeStationAware,
                RunwayVehicleVacatesForAircraftOperation,
                TowRequestStatesAircraftTypeAndOperator,
                RunwayVacatedReportAfterVehicleTowClear,
            )
    }

    object DistressUrgencyCommsFailure {
        val Chunk08Items: Set<EvidenceSourceRef> =
            setOf(
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807",
                    title = "Distress is serious or imminent danger requiring immediate assistance",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe",
                    title = "Urgency concerns safety but does not require immediate assistance",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6",
                    title = "Distress messages have priority over all other transmissions",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693",
                    title = "Urgency messages have priority except over distress",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03",
                    title = "Stations refrain from frequencies carrying emergency traffic unless involved",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa",
                    title = "Other stations or aircraft assist if the called station does not reply",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1",
                    title = "Distress communications normally continue on the current frequency",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a",
                    title = "Other emergency communication frequencies may be used when necessary",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478",
                    title = "Replying station provides necessary emergency assistance",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98",
                    title = "Pilots seek assistance when flight safety is in doubt",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3",
                    title = "Intercepted unacknowledged distress may be acknowledged and broadcast",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7",
                    title = "Distress or urgency call normally uses the frequency in use",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac",
                    title = "Superfluous transmissions may distract an emergency pilot",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::13d1c2accd0f7a73",
                    title = "Emergency calls should be spoken slowly and distinctly",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::c30159856a1a5e7a",
                    title = "Annex 10 contains detailed distress and urgency communication procedures",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::9907744b4723d14c",
                    title = "Pilots may adapt emergency phraseology to needs and time available",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::bf04647e26f9c018",
                    title = "MAYDAY or PAN PAN should preferably be spoken three times initially",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26",
                    title = "MAYDAY identifies distress and PAN PAN identifies urgency",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814",
                    title = "Distress message should contain station aircraft distress intention position and useful information",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_messages_9_2_en::f0e99a4c08ea0cb3",
                    title = "Distress message elements should be in the shown order when possible",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a",
                    title = "Distress message normally addresses current or responsible station",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982",
                    title = "Non-distressed sender may vary distress message elements if clear",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa",
                    title = "Aircraft in distress may use any means including SSR 7700",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95",
                    title = "Distress aircraft or controlling station may impose radio silence",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5",
                    title = "Aircraft requested to maintain silence do so until distress traffic ends",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e",
                    title = "Ground station terminates distress communication and silence when distress ends",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8",
                    title = "Urgency message should contain required emergency message elements",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca",
                    title = "Urgency call normally uses current frequency and current or responsible station",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d",
                    title = "Other stations avoid interfering with urgency traffic",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c",
                    title = "Controller safeguards other aircraft after emergency descent announcement",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e",
                    title = "Emergency descent broadcast may be followed by specific instructions",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13",
                    title = "Further questions may help ascertain emergency aircraft position",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808",
                    title = "Failed designated-frequency contact requires route-appropriate alternate frequency",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f",
                    title = "Failed alternate contact requires other route aircraft or stations",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07",
                    title = "Failed attempts lead to twice-transmitted message prefaced TRANSMITTING BLIND",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0",
                    title = "Blind transmission may include addressees when necessary",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::975a63151706f68f",
                    title = "Blind aircraft transmits intended message followed by complete repetition",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede",
                    title = "Blind procedure advises time of next intended transmission",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b",
                    title = "ATC or advisory aircraft transmits continuation intentions during communications failure",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4",
                    title = "Receiver failure reports are prefaced TRANSMITTING BLIND DUE RECEIVER FAILURE",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff",
                    title = "Airborne equipment failure selects SSR code 7600 when equipped",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::bb66a050093251c2",
                    title = "Unable station asks route aircraft to call and relay",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::24c806b040f4ef5e",
                    title = "Unable station asks other stations to call and relay",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::75055714e70d4560",
                    title = "Station may blind-transmit non-clearance messages after failed attempts",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3",
                    title = "Blind ATC clearances are prohibited except at originator request",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
                source(
                    canonicalId = "icao9432-extracted::communications_failure_9_5_en::c1c14fab53a608c6",
                    title = "Annex 10 contains general communications-failure rules",
                    claimScope = EvidenceSourceClaimScope.ProjectionGapSource,
                ),
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
            ICAO9432.Readback.PhraseologyItems +
            ICAO9432.ReadbackContinuation.PhraseologyItems +
            ICAO9432.Taxi.HoldingPointLimit +
            ICAO9432.Taxi.Chunk03AuditItems +
            ICAO9432.TakeoffProcedures.Chunk04ScenarioItems +
            ICAO9432.FinalApproachLanding.TouchAndGo +
            ICAO9432.GoAroundProcedures.Chunk06GoAroundItems +
            ICAO9432.AfterLanding.Chunk06PolicyItems +
            ICAO9432.TransferCommunications.RequiredProcedures +
            ICAO9432.TransferCommunications.PhraseologyItems +
            ICAO9432.AerodromeInformation.Chunk06ModelItems +
            ICAO9432.VehiclesAndTowing.Chunk07Items +
            ICAO9432.DistressUrgencyCommsFailure.Chunk08Items +
            setOf(
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
