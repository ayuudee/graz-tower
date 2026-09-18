package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.protocol.EmergencyType
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.Squawk

private val ROUTE_FREQUENCY = Frequency.unsafe("119.700")
private val NON_ROUTE_FREQUENCY = Frequency.unsafe("130.000")
private val SSR_7600 = Squawk.unsafe(7600)
private val SSR_7700 = Squawk.unsafe(7700)
private const val INTENDED_MESSAGE = "OE-ABC continuing to destination"
private const val NEXT_TRANSMISSION_TIME = "1235Z"
private const val CONTINUATION_INTENTION = "continue VFR to LOWG"

class Icao9432CommunicationsFailureSourceBackedTest {
    @Test
    fun `communications failure contact attempts progress through route appropriate alternatives`() {
        sourceUnitSpec("icao9432-communications-failure-routing-projection") {
            title("Communications failure tries route-appropriate alternate frequencies and contacts")
            sourceUnits(
                listOf(
                    chunk08Ref("icao9432-extracted::communications_failure_9_5_en::f05016444e2b8808"),
                    chunk08Ref("icao9432-extracted::communications_failure_9_5_en::fcb3a49672165b4f"),
                ),
            )
            domain("contact-attempt", setOf("designated-frequency", "alternate-frequency", "other-aircraft", "other-station"))
            domain("route-context", setOf("route-appropriate", "not-route-appropriate"))

            witness("failed designated and alternate frequencies select alternate contacts") {
                val active = CommunicationsFailureProjection.from(CommunicationsFailureEvent.DesignatedFrequencyContactFailed)
                    .acceptedState()
                val alternate = active.tryAlternateFrequency(ROUTE_FREQUENCY).acceptedState()
                val aircraftContact = alternate.tryOtherAircraft(ROUTE_FREQUENCY).acceptedState()
                val stationContact = aircraftContact.tryOtherStation(ROUTE_FREQUENCY).acceptedState()

                check(stationContact.contactAttempts == listOf(
                    ContactAttempt.DesignatedFrequency,
                    ContactAttempt.AlternateFrequency(ROUTE_FREQUENCY),
                    ContactAttempt.OtherAircraft(ROUTE_FREQUENCY),
                    ContactAttempt.OtherStation(ROUTE_FREQUENCY),
                ))
                hit("communications-failure-route-appropriate-alternates")
                requireHits("communications-failure-route-appropriate-alternates")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `blind transmission mode separates failed contact and receiver failure paths`() {
        sourceUnitSpec("icao9432-blind-transmission-structured-projection") {
            title("Blind transmission procedure carries repetition, addressee, schedule, and service context")
            sourceUnits(
                listOf(
                    chunk08Ref("icao9432-extracted::communications_failure_9_5_en::bc9bb12804033b07"),
                    chunk08Ref("icao9432-extracted::communications_failure_9_5_en::abbc376a430003b0"),
                    chunk08Ref("icao9432-extracted::communications_failure_9_5_en::975a63151706f68f"),
                    chunk08Ref("icao9432-extracted::communications_failure_9_5_en::045c2e33f59f5ede"),
                    chunk08Ref("icao9432-extracted::communications_failure_9_5_en::7900c606e05e509b"),
                    chunk08Ref("icao9432-extracted::communications_failure_9_5_en::78c73a75fab644f4"),
                ),
            )
            domain("blind-mode", setOf("failed-contact", "receiver-failure"))
            domain("service-context", setOf("atc-advisory", "none"))
            domain("payload", setOf("twice-repeated", "next-time", "continuation-intention"))

            witness("failed contact blind transmission repeats intended message twice") {
                val blind = CommunicationsFailureProjection.from(CommunicationsFailureEvent.DesignatedFrequencyContactFailed)
                    .acceptedState()
                    .tryAlternateFrequency(ROUTE_FREQUENCY)
                    .acceptedState()
                    .tryOtherAircraft(ROUTE_FREQUENCY)
                    .acceptedState()
                    .tryOtherStation(ROUTE_FREQUENCY)
                    .acceptedState()
                    .enterFailedContactBlindTransmission(INTENDED_MESSAGE, listOf("LOWG_TOWER"))
                    .acceptedState()

                check(blind.blindTransmission == BlindTransmissionSchedule(
                    mode = BlindTransmissionMode.FailedContact,
                    intendedMessage = INTENDED_MESSAGE,
                    repetitions = 2,
                    addressees = listOf("LOWG_TOWER"),
                    nextTransmissionTime = null,
                    continuationIntention = null,
                ))
                hit("failed-contact-blind-transmission-twice")
                requireHits("failed-contact-blind-transmission-twice")
            }

            witness("receiver failure blind transmission carries schedule and intentions when service applies") {
                val blind = CommunicationsFailureProjection.from(CommunicationsFailureEvent.ReceiverFailureDetected)
                    .acceptedState()
                    .enterReceiverFailureBlindTransmission(
                        intendedMessage = INTENDED_MESSAGE,
                        addressees = listOf("LOWG_TOWER"),
                        nextTransmissionTime = NEXT_TRANSMISSION_TIME,
                        serviceContext = ServiceContext.AtcOrAdvisory,
                        continuationIntention = CONTINUATION_INTENTION,
                    )
                    .acceptedState()

                check(blind.blindTransmission == BlindTransmissionSchedule(
                    mode = BlindTransmissionMode.ReceiverFailure,
                    intendedMessage = INTENDED_MESSAGE,
                    repetitions = 2,
                    addressees = listOf("LOWG_TOWER"),
                    nextTransmissionTime = NEXT_TRANSMISSION_TIME,
                    continuationIntention = CONTINUATION_INTENTION,
                ))
                hit("receiver-failure-blind-schedule-with-continuation-intention")
                requireHits("receiver-failure-blind-schedule-with-continuation-intention")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `ssr code projection distinguishes radio failure from distress`() {
        sourceUnitSpec("icao9432-communications-failure-ssr-projection") {
            title("SSR emergency codes distinguish communications failure from distress")
            sourceUnits(
                listOf(
                    chunk08Ref("icao9432-extracted::communications_failure_9_5_en::91e7d233bf3b64ff"),
                    chunk08Ref("icao9432-extracted::distress_messages_9_2_en::4b37e039e7eb8afa"),
                ),
            )
            domain("condition", setOf("communications-failure", "distress", "urgency"))
            domain("ssr-equipped", setOf("true", "false"))

            witness("radio failure selects 7600 and distress selects 7700 when equipped") {
                check(ssrCodeFor(SsrCondition.CommunicationsFailure, ssrEquipped = true) == SSR_7600)
                check(ssrCodeFor(SsrCondition.Distress(EmergencyType.MAYDAY), ssrEquipped = true) == SSR_7700)
                hit("communications-failure-7600-distress-7700")
                requireHits("communications-failure-7600-distress-7700")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `blind clearances are prohibited except by originator request`() {
        sourceUnitSpec("icao9432-blind-clearance-prohibition-projection") {
            title("Blind ATC clearances are prohibited unless requested by the originator")
            sourceUnit(
                chunk08Ref("icao9432-extracted::communications_failure_9_5_en::b73dda299970c2f3"),
            )
            domain("message-kind", setOf("clearance", "non-clearance"))
            domain("originator-request", setOf("present", "absent"))

            witness("blind clearance requires originator request") {
                check(!BlindTransmissionPolicy.mayTransmitBlind(MessageKind.Clearance, OriginatorRequest.Absent))
                check(BlindTransmissionPolicy.mayTransmitBlind(MessageKind.Clearance, OriginatorRequest.Present))
                hit("blind-clearance-originator-request-exception")
                requireHits("blind-clearance-originator-request-exception")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `recovery clears all communications failure derived state`() {
        val failedContact = CommunicationsFailureProjection.from(CommunicationsFailureEvent.DesignatedFrequencyContactFailed)
            .acceptedState()
            .tryAlternateFrequency(ROUTE_FREQUENCY)
            .acceptedState()
            .tryOtherAircraft(ROUTE_FREQUENCY)
            .acceptedState()
            .tryOtherStation(ROUTE_FREQUENCY)
            .acceptedState()
            .enterFailedContactBlindTransmission(
                intendedMessage = INTENDED_MESSAGE,
                addressees = listOf("LOWG_TOWER"),
            )
            .acceptedState()
            .withSsrCode(SSR_7600)
            .withBlindClearanceException()

        val recoveredFailedContact = failedContact.resolve(RecoveryEvent.CommunicationsRestored).acceptedState()

        check(recoveredFailedContact.mode == CommunicationsMode.Normal)
        check(recoveredFailedContact.contactAttempts.isEmpty())
        check(recoveredFailedContact.blindTransmission == null)
        check(recoveredFailedContact.ssrCode == null)
        check(!recoveredFailedContact.blindClearanceOriginatorRequest)

        val receiverFailure = CommunicationsFailureProjection.from(CommunicationsFailureEvent.ReceiverFailureDetected)
            .acceptedState()
            .enterReceiverFailureBlindTransmission(
                intendedMessage = INTENDED_MESSAGE,
                addressees = listOf("LOWG_TOWER"),
                nextTransmissionTime = NEXT_TRANSMISSION_TIME,
                serviceContext = ServiceContext.AtcOrAdvisory,
                continuationIntention = CONTINUATION_INTENTION,
            )
            .acceptedState()
            .withSsrCode(SSR_7600)
            .withBlindClearanceException()

        val recoveredReceiverFailure = receiverFailure.resolve(RecoveryEvent.CommunicationsRestored).acceptedState()

        check(recoveredReceiverFailure.mode == CommunicationsMode.Normal)
        check(recoveredReceiverFailure.contactAttempts.isEmpty())
        check(recoveredReceiverFailure.blindTransmission == null)
        check(recoveredReceiverFailure.ssrCode == null)
        check(!recoveredReceiverFailure.blindClearanceOriginatorRequest)
    }

    @Test
    fun `negative guards prevent false communications failure coverage`() {
        check(CommunicationsFailureProjection.from(CommunicationsFailureEvent.OrdinaryNoReply).isRejected)
        check(CommunicationsFailureProjection.from(CommunicationsFailureEvent.RoutineMissedCall).isRejected)
        check(CommunicationsFailureProjection.from(CommunicationsFailureEvent.RoutineFrequencyTransferFailure).isRejected)
        check(!RadioEmissionOrder(firstStarted = "routine call").provesCommunicationsFailure)

        val active = CommunicationsFailureProjection.from(CommunicationsFailureEvent.DesignatedFrequencyContactFailed)
            .acceptedState()
        check(active.tryAlternateFrequency(NON_ROUTE_FREQUENCY).isRejected)

        check(
            CommunicationsFailureProjection.from(
                CommunicationsFailureEvent.GenericEmergencyWithoutCommunicationsFailure(EmergencyType.PAN_PAN),
            ).isRejected,
        )
        check(
            CommunicationsFailureProjection.from(
                CommunicationsFailureEvent.GenericEmergencyWithoutCommunicationsFailure(EmergencyType.MAYDAY),
            ).isRejected,
        )
        check(active.tryOtherAircraft(ROUTE_FREQUENCY).isRejected)
        check(active.tryOtherStation(ROUTE_FREQUENCY).isRejected)
        val afterAlternate = active.tryAlternateFrequency(ROUTE_FREQUENCY).acceptedState()
        check(afterAlternate.enterFailedContactBlindTransmission(INTENDED_MESSAGE, listOf("LOWG_TOWER")).isRejected)
        check(ssrCodeFor(SsrCondition.CommunicationsFailure, ssrEquipped = true) != SSR_7700)
        check(ssrCodeFor(SsrCondition.Distress(EmergencyType.PAN_PAN), ssrEquipped = true) == null)

        val receiverFailure = CommunicationsFailureProjection.from(CommunicationsFailureEvent.ReceiverFailureDetected)
            .acceptedState()
        check(receiverFailure.tryAlternateFrequency(ROUTE_FREQUENCY).isRejected)
        check(
            receiverFailure
                .enterReceiverFailureBlindTransmission(
                    intendedMessage = INTENDED_MESSAGE,
                    addressees = emptyList(),
                    nextTransmissionTime = NEXT_TRANSMISSION_TIME,
                    serviceContext = ServiceContext.None,
                    continuationIntention = CONTINUATION_INTENTION,
                )
                .isRejected,
        )
        check(!BlindTransmissionPolicy.mayTransmitBlind(MessageKind.Clearance, OriginatorRequest.Absent))
        check(!Annex10ConformanceClaim.coveredByLocalProjection)
        check(!RelayRowsCoveredByLocalProjection.covered)
    }

    private fun chunk08Ref(canonicalId: String): SourceUnitRef =
        ICAO9432.DistressUrgencyCommsFailure.Chunk08Items
            .single { source -> source.canonicalId == canonicalId }
            .toSourceUnitRef()

    private data class CommunicationsFailureProjection(
        val mode: CommunicationsMode,
        val contactAttempts: List<ContactAttempt>,
        val blindTransmission: BlindTransmissionSchedule?,
        val ssrCode: Squawk?,
        val blindClearanceOriginatorRequest: Boolean,
    ) {
        fun tryAlternateFrequency(frequency: Frequency): StateTransition<CommunicationsFailureProjection> =
            when {
                mode != CommunicationsMode.FailedContact ->
                    StateTransition.Rejected("Alternate frequency requires failed-contact state")
                contactAttempts != listOf(ContactAttempt.DesignatedFrequency) ->
                    StateTransition.Rejected("Alternate frequency follows designated-frequency failure only")
                frequency != ROUTE_FREQUENCY ->
                    StateTransition.Rejected("Alternate frequency is not route-appropriate")
                else ->
                    StateTransition.Accepted(copy(contactAttempts = contactAttempts + ContactAttempt.AlternateFrequency(frequency)))
            }

        fun tryOtherAircraft(frequency: Frequency): StateTransition<CommunicationsFailureProjection> =
            when {
                mode != CommunicationsMode.FailedContact ->
                    StateTransition.Rejected("Other-aircraft contact requires failed-contact state")
                contactAttempts != listOf(
                    ContactAttempt.DesignatedFrequency,
                    ContactAttempt.AlternateFrequency(ROUTE_FREQUENCY),
                ) -> StateTransition.Rejected("Other-aircraft contact follows alternate-frequency failure only")
                frequency != ROUTE_FREQUENCY ->
                    StateTransition.Rejected("Other-aircraft contact frequency is not route-appropriate")
                else ->
                    StateTransition.Accepted(copy(contactAttempts = contactAttempts + ContactAttempt.OtherAircraft(frequency)))
            }

        fun tryOtherStation(frequency: Frequency): StateTransition<CommunicationsFailureProjection> =
            when {
                mode != CommunicationsMode.FailedContact ->
                    StateTransition.Rejected("Other-station contact requires failed-contact state")
                contactAttempts != listOf(
                    ContactAttempt.DesignatedFrequency,
                    ContactAttempt.AlternateFrequency(ROUTE_FREQUENCY),
                    ContactAttempt.OtherAircraft(ROUTE_FREQUENCY),
                ) -> StateTransition.Rejected("Other-station contact follows other-aircraft contact failure only")
                frequency != ROUTE_FREQUENCY ->
                    StateTransition.Rejected("Other-station contact frequency is not route-appropriate")
                else ->
                    StateTransition.Accepted(copy(contactAttempts = contactAttempts + ContactAttempt.OtherStation(frequency)))
            }

        fun enterFailedContactBlindTransmission(
            intendedMessage: String,
            addressees: List<String>,
        ): StateTransition<CommunicationsFailureProjection> =
            when (mode) {
                CommunicationsMode.FailedContact ->
                    if (contactAttempts == failedContactBlindPrerequisites) {
                        StateTransition.Accepted(
                            copy(
                                blindTransmission = BlindTransmissionSchedule(
                                    mode = BlindTransmissionMode.FailedContact,
                                    intendedMessage = intendedMessage,
                                    repetitions = 2,
                                    addressees = addressees,
                                    nextTransmissionTime = null,
                                    continuationIntention = null,
                                ),
                            ),
                        )
                    } else {
                        StateTransition.Rejected(
                            "Failed-contact blind mode requires prior failed route-appropriate contact attempts",
                        )
                    }

                CommunicationsMode.Normal,
                CommunicationsMode.ReceiverFailure,
                CommunicationsMode.Resolved,
                -> StateTransition.Rejected("Failed-contact blind mode requires failed contact state")
            }

        fun enterReceiverFailureBlindTransmission(
            intendedMessage: String,
            addressees: List<String>,
            nextTransmissionTime: String,
            serviceContext: ServiceContext,
            continuationIntention: String?,
        ): StateTransition<CommunicationsFailureProjection> =
            when (mode) {
                CommunicationsMode.ReceiverFailure ->
                    when {
                        serviceContext == ServiceContext.None && continuationIntention != null ->
                            StateTransition.Rejected("Continuation intentions require ATC/advisory service")
                        else -> StateTransition.Accepted(
                            copy(
                                blindTransmission = BlindTransmissionSchedule(
                                    mode = BlindTransmissionMode.ReceiverFailure,
                                    intendedMessage = intendedMessage,
                                    repetitions = 2,
                                    addressees = addressees,
                                    nextTransmissionTime = nextTransmissionTime,
                                    continuationIntention = continuationIntention,
                                ),
                            ),
                        )
                    }

                CommunicationsMode.FailedContact,
                CommunicationsMode.Normal,
                CommunicationsMode.Resolved,
                -> StateTransition.Rejected("Receiver-failure blind mode requires receiver failure state")
            }

        fun withSsrCode(code: Squawk): CommunicationsFailureProjection =
            copy(ssrCode = code)

        fun withBlindClearanceException(): CommunicationsFailureProjection =
            copy(blindClearanceOriginatorRequest = true)

        fun resolve(event: RecoveryEvent): StateTransition<CommunicationsFailureProjection> =
            when (event) {
                RecoveryEvent.CommunicationsRestored -> StateTransition.Accepted(normal())
                RecoveryEvent.OrdinaryCallAnswered -> StateTransition.Rejected(
                    "Ordinary call answer does not prove communications-failure recovery",
                )
            }

        companion object {
            fun from(event: CommunicationsFailureEvent): StateTransition<CommunicationsFailureProjection> =
                when (event) {
                    CommunicationsFailureEvent.DesignatedFrequencyContactFailed -> StateTransition.Accepted(
                        normal().copy(
                            mode = CommunicationsMode.FailedContact,
                            contactAttempts = listOf(ContactAttempt.DesignatedFrequency),
                        ),
                    )

                    CommunicationsFailureEvent.ReceiverFailureDetected -> StateTransition.Accepted(
                        normal().copy(mode = CommunicationsMode.ReceiverFailure),
                    )

                    CommunicationsFailureEvent.OrdinaryNoReply,
                    CommunicationsFailureEvent.RoutineFrequencyTransferFailure,
                    CommunicationsFailureEvent.RoutineMissedCall,
                    is CommunicationsFailureEvent.GenericEmergencyWithoutCommunicationsFailure,
                    -> StateTransition.Rejected("Not a typed communications failure")
                }

            private fun normal(): CommunicationsFailureProjection =
                CommunicationsFailureProjection(
                    mode = CommunicationsMode.Normal,
                    contactAttempts = emptyList(),
                    blindTransmission = null,
                    ssrCode = null,
                    blindClearanceOriginatorRequest = false,
                )

            private val failedContactBlindPrerequisites: List<ContactAttempt> = listOf(
                ContactAttempt.DesignatedFrequency,
                ContactAttempt.AlternateFrequency(ROUTE_FREQUENCY),
                ContactAttempt.OtherAircraft(ROUTE_FREQUENCY),
                ContactAttempt.OtherStation(ROUTE_FREQUENCY),
            )
        }
    }

    private sealed interface CommunicationsFailureEvent {
        data object DesignatedFrequencyContactFailed : CommunicationsFailureEvent
        data object ReceiverFailureDetected : CommunicationsFailureEvent
        data object OrdinaryNoReply : CommunicationsFailureEvent
        data object RoutineMissedCall : CommunicationsFailureEvent
        data object RoutineFrequencyTransferFailure : CommunicationsFailureEvent
        data class GenericEmergencyWithoutCommunicationsFailure(
            val emergencyType: EmergencyType,
        ) : CommunicationsFailureEvent
    }

    private enum class CommunicationsMode {
        Normal,
        FailedContact,
        ReceiverFailure,
        Resolved,
    }

    private sealed interface ContactAttempt {
        data object DesignatedFrequency : ContactAttempt
        data class AlternateFrequency(val frequency: Frequency) : ContactAttempt
        data class OtherAircraft(val frequency: Frequency) : ContactAttempt
        data class OtherStation(val frequency: Frequency) : ContactAttempt
    }

    private data class BlindTransmissionSchedule(
        val mode: BlindTransmissionMode,
        val intendedMessage: String,
        val repetitions: Int,
        val addressees: List<String>,
        val nextTransmissionTime: String?,
        val continuationIntention: String?,
    )

    private enum class BlindTransmissionMode {
        FailedContact,
        ReceiverFailure,
    }

    private enum class ServiceContext {
        AtcOrAdvisory,
        None,
    }

    private sealed interface SsrCondition {
        data object CommunicationsFailure : SsrCondition
        data class Distress(val emergencyType: EmergencyType) : SsrCondition
    }

    private fun ssrCodeFor(condition: SsrCondition, ssrEquipped: Boolean): Squawk? =
        if (!ssrEquipped) {
            null
        } else {
            when (condition) {
                SsrCondition.CommunicationsFailure -> SSR_7600
                is SsrCondition.Distress ->
                    when (condition.emergencyType) {
                        EmergencyType.MAYDAY -> SSR_7700
                        EmergencyType.PAN_PAN -> null
                    }
            }
        }

    private enum class MessageKind {
        Clearance,
        NonClearance,
    }

    private enum class OriginatorRequest {
        Present,
        Absent,
    }

    private data object BlindTransmissionPolicy {
        fun mayTransmitBlind(kind: MessageKind, request: OriginatorRequest): Boolean =
            when (kind) {
                MessageKind.Clearance -> request == OriginatorRequest.Present
                MessageKind.NonClearance -> true
            }
    }

    private enum class RecoveryEvent {
        CommunicationsRestored,
        OrdinaryCallAnswered,
    }

    private data class RadioEmissionOrder(val firstStarted: String) {
        val provesCommunicationsFailure: Boolean = false
    }

    private data object Annex10ConformanceClaim {
        val coveredByLocalProjection: Boolean = false
    }

    private data object RelayRowsCoveredByLocalProjection {
        val covered: Boolean = false
    }

    private sealed interface StateTransition<out T> {
        val isRejected: Boolean

        data class Accepted<T>(val state: T) : StateTransition<T> {
            override val isRejected: Boolean = false
        }

        data class Rejected(val reason: String) : StateTransition<Nothing> {
            override val isRejected: Boolean = true
        }
    }

    private fun StateTransition<CommunicationsFailureProjection>.acceptedState(): CommunicationsFailureProjection =
        when (this) {
            is StateTransition.Accepted -> state
            is StateTransition.Rejected -> throw AssertionError("Expected accepted transition: $reason")
        }

}
