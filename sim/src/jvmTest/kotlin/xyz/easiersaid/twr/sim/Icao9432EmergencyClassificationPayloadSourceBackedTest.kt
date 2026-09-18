package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.Emergency
import xyz.easiersaid.twr.protocol.EmergencyDetails
import xyz.easiersaid.twr.protocol.EmergencyType
import xyz.easiersaid.twr.protocol.Heading
import xyz.easiersaid.twr.protocol.Level
import xyz.easiersaid.twr.protocol.Readback
import xyz.easiersaid.twr.protocol.RoleName

class Icao9432EmergencyClassificationPayloadSourceBackedTest {
    @Test
    fun `distress and urgency classifications carry source-defined semantics`() {
        sourceUnitSpec("icao9432-emergency-classification-structured-semantics") {
            title("Distress and urgency classifications carry immediate-assistance semantics")
            sourceUnits(
                listOf(
                    chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::8b3b3b4117c04807"),
                    chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::c959f0325390e7fe"),
                ),
            )
            domain("condition-kind", setOf("distress", "urgency"))
            domain("assistance-need", setOf("immediate", "not-immediate"))
            domain("projection-source", setOf("pilot-emergency-transmission"))

            witness("MAYDAY is projected as distress") {
                val evidence = emergencyEvidenceFrom(distressFact())
                    ?: error("MAYDAY emergency did not project to emergency evidence")
                check(evidence.classification == EmergencyEvidenceClassification.Distress)
                check(evidence.classification.seriousOrImminentDanger)
                check(evidence.classification.immediateAssistanceRequired)
                check(evidence.classification.safetyConcern)
                hit("distress-immediate-assistance")
                requireHits("distress-immediate-assistance")
            }

            witness("PAN PAN is projected as urgency") {
                val evidence = emergencyEvidenceFrom(urgencyFact())
                    ?: error("PAN PAN emergency did not project to emergency evidence")
                check(evidence.classification == EmergencyEvidenceClassification.Urgency)
                check(evidence.classification.safetyConcern)
                check(!evidence.classification.seriousOrImminentDanger)
                check(!evidence.classification.immediateAssistanceRequired)
                hit("urgency-no-immediate-assistance")
                requireHits("urgency-no-immediate-assistance")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `protocol emergency type discriminator maps MAYDAY to distress and PAN PAN to urgency`() {
        sourceUnitSpec("icao9432-emergency-type-discriminator-classification") {
            title("Protocol emergency type discriminator maps MAYDAY to distress and PAN PAN to urgency")
            sourceUnit(
                chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::d742970b22d8de26"),
            )
            domain("protocol-discriminator", setOf("MAYDAY", "PAN_PAN"))
            domain("classification", setOf("distress", "urgency"))
            domain("phraseology-boundary", setOf("typed-discriminator-not-rendered-wording"))

            witness("MAYDAY and PAN PAN discriminator mapping") {
                check(emergencyEvidenceFrom(distressFact())?.classification == EmergencyEvidenceClassification.Distress)
                check(emergencyEvidenceFrom(urgencyFact())?.classification == EmergencyEvidenceClassification.Urgency)
                hit("typed-discriminator-mapping")
                requireHits("typed-discriminator-mapping")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `distress message exposes structured payload fields without claiming rendered order`() {
        sourceUnitSpec("icao9432-distress-message-structured-payload") {
            title("Distress message exposes the ICAO 9432 structured payload fields")
            sourceUnit(
                chunk08Ref("icao9432-extracted::distress_messages_9_2_en::23c9f447cd6c7814"),
            )
            domain("message-kind", setOf("distress", "urgency", "routine"))
            domain("payload-completeness", setOf("all-structured-fields", "missing-field"))
            domain("phraseology-boundary", setOf("structured-fields-not-rendered-order"))

            witness("MAYDAY full payload satisfies structured distress branch") {
                val payload = emergencyEvidenceFrom(distressFact())?.payload
                    ?: error("MAYDAY full payload did not project to emergency evidence")
                check(payload.isCompleteDistressMessageForSourceUnit) {
                    "Full distress payload did not satisfy structured payload proof: $payload"
                }
                check(payload.stationAddressed == RoleName.TOWER)
                check(payload.aircraftId == AIRCRAFT)
                check(payload.nature == "engine on fire")
                check(payload.intentions == "making forced landing")
                check(payload.position == "20 miles south of WALDEN")
                check(payload.level == Level.AltitudeFeet.unsafe(3000))
                check(payload.heading == Heading.unsafe(360))
                check(payload.usefulInformation == EmergencyUsefulInformation(
                    personsOnBoard = 2,
                    fuelRemainingMinutes = 45,
                    remarks = "smoke in cockpit",
                ))
                hit("distress-structured-payload-complete")
                requireHits("distress-structured-payload-complete")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `non distress and partial messages do not satisfy full structured distress payload`() {
        val urgencyPayload = emergencyEvidenceFrom(urgencyFact())?.payload
            ?: error("PAN PAN emergency did not project to emergency evidence")
        check(!urgencyPayload.isCompleteDistressMessageForSourceUnit) {
            "Urgency message satisfied distress-message structured payload proof"
        }

        val partialPayload = emergencyEvidenceFrom(partialDistressFact())?.payload
            ?: error("Partial MAYDAY emergency did not project to emergency evidence")
        check(!partialPayload.isCompleteDistressMessageForSourceUnit) {
            "Partial distress message satisfied full structured payload proof"
        }

        check(emergencyEvidenceFrom(routineFact()) == null) {
            "Routine non-emergency pilot transmission projected to emergency evidence"
        }
    }

    private fun emergencyEvidenceFrom(
        fact: EvidenceFactPayload.PilotTransmissionFact,
    ): EmergencyTransmissionEvidence? {
        val emergency = fact.transmission as? Emergency ?: return null
        return EmergencyTransmissionEvidence(
            aircraftId = fact.aircraftId,
            classification = emergency.type.toEmergencyClassification(),
            payload = emergency.toEmergencyPayload(fact.aircraftId),
        )
    }

    private fun EmergencyType.toEmergencyClassification(): EmergencyEvidenceClassification =
        when (this) {
            EmergencyType.MAYDAY -> EmergencyEvidenceClassification.Distress
            EmergencyType.PAN_PAN -> EmergencyEvidenceClassification.Urgency
        }

    private fun Emergency.toEmergencyPayload(aircraftId: AircraftId): EmergencyStructuredPayload =
        EmergencyStructuredPayload(
            aircraftId = aircraftId,
            classification = type.toEmergencyClassification(),
            stationAddressed = details.stationAddressed,
            aircraftType = details.aircraftType,
            nature = details.nature,
            intentions = details.intentions,
            position = details.position,
            level = details.level,
            heading = details.heading,
            usefulInformation = EmergencyUsefulInformation(
                personsOnBoard = details.personsOnBoard,
                fuelRemainingMinutes = details.fuelRemainingMinutes,
                remarks = details.remarks,
            ),
        )

    private fun distressFact(): EvidenceFactPayload.PilotTransmissionFact =
        pilotFact(
            Emergency(
                type = EmergencyType.MAYDAY,
                details = EmergencyDetails(
                    stationAddressed = RoleName.TOWER,
                    aircraftType = "C172",
                    nature = "engine on fire",
                    intentions = "making forced landing",
                    position = "20 miles south of WALDEN",
                    level = Level.AltitudeFeet.unsafe(3000),
                    heading = Heading.unsafe(360),
                    personsOnBoard = 2,
                    fuelRemainingMinutes = 45,
                    remarks = "smoke in cockpit",
                ),
            ),
        )

    private fun partialDistressFact(): EvidenceFactPayload.PilotTransmissionFact =
        pilotFact(
            Emergency(
                type = EmergencyType.MAYDAY,
                details = EmergencyDetails(
                    stationAddressed = RoleName.TOWER,
                    aircraftType = "C172",
                    nature = "engine on fire",
                    intentions = "making forced landing",
                    position = null,
                    level = Level.AltitudeFeet.unsafe(3000),
                    heading = Heading.unsafe(360),
                    personsOnBoard = 2,
                    fuelRemainingMinutes = 45,
                    remarks = "smoke in cockpit",
                ),
            ),
        )

    private fun urgencyFact(): EvidenceFactPayload.PilotTransmissionFact =
        pilotFact(
            Emergency(
                type = EmergencyType.PAN_PAN,
                details = EmergencyDetails(
                    stationAddressed = RoleName.TOWER,
                    aircraftType = "C172",
                    nature = "passenger illness",
                    intentions = "request priority landing",
                    position = "10 miles north",
                    level = Level.AltitudeFeet.unsafe(2500),
                    heading = Heading.unsafe(180),
                    personsOnBoard = 3,
                    fuelRemainingMinutes = 60,
                    remarks = "medical assistance requested",
                ),
            ),
        )

    private fun routineFact(): EvidenceFactPayload.PilotTransmissionFact =
        pilotFact(Readback(emptyList()))

    private fun pilotFact(transmission: xyz.easiersaid.twr.protocol.PilotTransmission): EvidenceFactPayload.PilotTransmissionFact =
        EvidenceFactPayload.PilotTransmissionFact(
            aircraftId = AIRCRAFT,
            transmission = transmission,
        )

    private fun chunk08Ref(canonicalId: String): SourceUnitRef =
        ICAO9432.DistressUrgencyCommsFailure.Chunk08Items
            .single { source -> source.canonicalId == canonicalId }
            .toSourceUnitRef()

    private sealed interface EmergencyEvidenceClassification {
        val seriousOrImminentDanger: Boolean
        val safetyConcern: Boolean
        val immediateAssistanceRequired: Boolean

        data object Distress : EmergencyEvidenceClassification {
            override val seriousOrImminentDanger: Boolean = true
            override val safetyConcern: Boolean = true
            override val immediateAssistanceRequired: Boolean = true
        }

        data object Urgency : EmergencyEvidenceClassification {
            override val seriousOrImminentDanger: Boolean = false
            override val safetyConcern: Boolean = true
            override val immediateAssistanceRequired: Boolean = false
        }
    }

    private data class EmergencyTransmissionEvidence(
        val aircraftId: AircraftId,
        val classification: EmergencyEvidenceClassification,
        val payload: EmergencyStructuredPayload,
    )

    private data class EmergencyStructuredPayload(
        val aircraftId: AircraftId,
        val classification: EmergencyEvidenceClassification,
        val stationAddressed: RoleName?,
        val aircraftType: String?,
        val nature: String,
        val intentions: String?,
        val position: String?,
        val level: Level?,
        val heading: Heading?,
        val usefulInformation: EmergencyUsefulInformation,
    ) {
        val isCompleteDistressMessageForSourceUnit: Boolean =
            classification == EmergencyEvidenceClassification.Distress &&
                stationAddressed != null &&
                nature.isNotBlank() &&
                intentions != null &&
                position != null &&
                level != null &&
                heading != null &&
                usefulInformation.hasAnyInformation
    }

    private data class EmergencyUsefulInformation(
        val personsOnBoard: Int?,
        val fuelRemainingMinutes: Int?,
        val remarks: String?,
    ) {
        val hasAnyInformation: Boolean =
            personsOnBoard != null || fuelRemainingMinutes != null || !remarks.isNullOrBlank()
    }

    private companion object {
        val AIRCRAFT = AircraftId("G-ABCD")
    }
}
