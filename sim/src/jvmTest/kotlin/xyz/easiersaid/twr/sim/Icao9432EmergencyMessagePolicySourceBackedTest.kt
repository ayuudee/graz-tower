package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.protocol.EmergencyType
import xyz.easiersaid.twr.protocol.Frequency
import xyz.easiersaid.twr.protocol.RoleName

private val MESSAGE_POLICY_CURRENT_FREQUENCY = Frequency.unsafe("121.500")

class Icao9432EmergencyMessagePolicySourceBackedTest {
    @Test
    fun `distress message addressing policy selects current or responsible station`() {
        sourceUnitSpec("icao9432-distress-message-addressing-policy") {
            title("Distress messages address the current or responsible-area station under explicit policy")
            sourceUnit(
                chunk08Ref("icao9432-extracted::distress_messages_9_2_en::27a450fa3bfcbc0a"),
            )
            domain("emergency-kind", setOf("distress", "urgency", "routine"))
            domain("addressing-policy", setOf("current-station", "responsible-area-station", "absent"))
            domain("phraseology-boundary", setOf("structured-policy-not-rendered-address"))

            witness("distress message addresses station currently in communication") {
                val addressed = EmergencyMessagePolicyProjection
                    .from(
                        EmergencyMessagePolicyEvent.DistressMessageStarted(
                            sender = EmergencyMessageSender.DistressedAircraft,
                            currentStation = RoleName.TOWER,
                            responsibleAreaStation = RoleName.APPROACH,
                        ),
                    )
                    .acceptedState()
                    .selectAddressee(AddressingPolicy.CurrentStation)
                    .acceptedState()

                check(addressed.addressee == EmergencyAddressee.CurrentStation(RoleName.TOWER))
                check(addressed.policyMarker == PolicyMarker.DistressAddressing)
                hit("distress-address-current-station")
                requireHits("distress-address-current-station")
            }

            witness("distress message may address responsible area station") {
                val addressed = EmergencyMessagePolicyProjection
                    .from(
                        EmergencyMessagePolicyEvent.DistressMessageStarted(
                            sender = EmergencyMessageSender.DistressedAircraft,
                            currentStation = RoleName.TOWER,
                            responsibleAreaStation = RoleName.APPROACH,
                        ),
                    )
                    .acceptedState()
                    .selectAddressee(AddressingPolicy.ResponsibleAreaStation)
                    .acceptedState()

                check(addressed.addressee == EmergencyAddressee.ResponsibleAreaStation(RoleName.APPROACH))
                check(addressed.responsibleAreaStation == RoleName.APPROACH)
                hit("distress-address-responsible-area-station")
                requireHits("distress-address-responsible-area-station")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `relayed distress variation requires clearly stated circumstances`() {
        sourceUnitSpec("icao9432-relayed-distress-variation-policy") {
            title("Non-distressed transmitting station varies distress message elements only with stated circumstance")
            sourceUnit(
                chunk08Ref("icao9432-extracted::distress_messages_9_2_en::94e94be0c800c982"),
            )
            domain("sender-role", setOf("distressed-aircraft", "relaying-station"))
            domain("circumstance", setOf("explicitly-stated", "clear-but-unstated", "absent"))
            domain("payload-variation", setOf("standard-elements", "varied-elements"))

            witness("relaying station may vary elements when circumstance is explicitly stated") {
                val varied = EmergencyMessagePolicyProjection
                    .from(
                        EmergencyMessagePolicyEvent.RelayedDistressMessageStarted(
                            sender = EmergencyMessageSender.RelayingStation,
                            statedCircumstance = StatedCircumstance("distressed aircraft unable to transmit full message"),
                        ),
                    )
                    .acceptedState()
                    .varyDistressPayload(RelayedDistressVariationPolicy.AllowWhenCircumstanceStated)
                    .acceptedState()

                check(varied.sender == EmergencyMessageSender.RelayingStation)
                check(varied.variationReason == StatedCircumstance("distressed aircraft unable to transmit full message"))
                check(varied.selectedPayloadElements == setOf(
                    EmergencyPayloadElement.StationAddressed,
                    EmergencyPayloadElement.AircraftIdentification,
                    EmergencyPayloadElement.NatureOfEmergency,
                    EmergencyPayloadElement.Position,
                ))
                hit("relayed-distress-varied-elements-stated-circumstance")
                requireHits("relayed-distress-varied-elements-stated-circumstance")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `urgency message payload policy selects circumstance required elements`() {
        sourceUnitSpec("icao9432-urgency-message-payload-policy") {
            title("Urgency messages carry the elements required by circumstances")
            sourceUnit(
                chunk08Ref("icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::1de475a788206cc8"),
            )
            domain("emergency-kind", setOf("urgency", "distress"))
            domain("circumstance", setOf("medical-priority", "position-known", "missing-required"))
            domain("payload-elements", setOf("required-present", "required-missing", "non-required-omitted"))

            witness("urgency payload includes all circumstance required elements") {
                val selected = EmergencyMessagePolicyProjection
                    .from(
                        EmergencyMessagePolicyEvent.UrgencyMessageStarted(
                            currentStation = RoleName.TOWER,
                            responsibleAreaStation = RoleName.APPROACH,
                            frequencyInUse = MESSAGE_POLICY_CURRENT_FREQUENCY,
                        ),
                    )
                    .acceptedState()
                    .selectUrgencyPayload(
                        UrgencyPayloadPolicy.CircumstanceRequiredElements(
                            required = setOf(
                                EmergencyPayloadElement.StationAddressed,
                                EmergencyPayloadElement.AircraftIdentification,
                                EmergencyPayloadElement.NatureOfEmergency,
                                EmergencyPayloadElement.Intentions,
                            ),
                            optionalOmitted = setOf(EmergencyPayloadElement.Heading),
                        ),
                    )
                    .acceptedState()

                check(selected.selectedPayloadElements.containsAll(
                    setOf(
                        EmergencyPayloadElement.StationAddressed,
                        EmergencyPayloadElement.AircraftIdentification,
                        EmergencyPayloadElement.NatureOfEmergency,
                        EmergencyPayloadElement.Intentions,
                    ),
                ))
                check(!selected.selectedPayloadElements.contains(EmergencyPayloadElement.Heading))
                check(selected.policyMarker == PolicyMarker.UrgencyPayload)
                hit("urgency-required-elements-present")
                hit("urgency-non-required-element-omitted")
                requireHits("urgency-required-elements-present")
                requireHits("urgency-non-required-element-omitted")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `urgency addressing and frequency policy uses current frequency and current or responsible station`() {
        sourceUnitSpec("icao9432-urgency-addressing-frequency-policy") {
            title("Urgency calls use frequency in use and address current or responsible station")
            sourceUnit(
                chunk08Ref("icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::b95d7bb1cb409bca"),
            )
            domain("frequency-policy", setOf("frequency-in-use", "changed-frequency"))
            domain("addressee-policy", setOf("current-station", "responsible-area-station", "absent"))
            domain("emergency-kind", setOf("urgency", "distress"))

            witness("urgency call uses frequency in use and current station") {
                val addressed = EmergencyMessagePolicyProjection
                    .from(
                        EmergencyMessagePolicyEvent.UrgencyMessageStarted(
                            currentStation = RoleName.TOWER,
                            responsibleAreaStation = RoleName.APPROACH,
                            frequencyInUse = MESSAGE_POLICY_CURRENT_FREQUENCY,
                        ),
                    )
                    .acceptedState()
                    .selectUrgencyAddressing(UrgencyAddressingPolicy.FrequencyInUseAndCurrentStation)
                    .acceptedState()

                check(addressed.frequencySelection == EmergencyFrequencySelection.FrequencyInUse(MESSAGE_POLICY_CURRENT_FREQUENCY))
                check(addressed.addressee == EmergencyAddressee.CurrentStation(RoleName.TOWER))
                hit("urgency-frequency-in-use-current-station")
                requireHits("urgency-frequency-in-use-current-station")
            }

            witness("urgency call may address responsible area station on frequency in use") {
                val addressed = EmergencyMessagePolicyProjection
                    .from(
                        EmergencyMessagePolicyEvent.UrgencyMessageStarted(
                            currentStation = RoleName.TOWER,
                            responsibleAreaStation = RoleName.APPROACH,
                            frequencyInUse = MESSAGE_POLICY_CURRENT_FREQUENCY,
                        ),
                    )
                    .acceptedState()
                    .selectUrgencyAddressing(UrgencyAddressingPolicy.FrequencyInUseAndResponsibleAreaStation)
                    .acceptedState()

                check(addressed.frequencySelection == EmergencyFrequencySelection.FrequencyInUse(MESSAGE_POLICY_CURRENT_FREQUENCY))
                check(addressed.addressee == EmergencyAddressee.ResponsibleAreaStation(RoleName.APPROACH))
                hit("urgency-frequency-in-use-responsible-area-station")
                requireHits("urgency-frequency-in-use-responsible-area-station")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `recovery clears all emergency message policy derived state`() {
        val active = EmergencyMessagePolicyProjection
            .from(
                EmergencyMessagePolicyEvent.UrgencyMessageStarted(
                    currentStation = RoleName.TOWER,
                    responsibleAreaStation = RoleName.APPROACH,
                    frequencyInUse = MESSAGE_POLICY_CURRENT_FREQUENCY,
                ),
            )
            .acceptedState()
            .selectUrgencyAddressing(UrgencyAddressingPolicy.FrequencyInUseAndResponsibleAreaStation)
            .acceptedState()
            .selectUrgencyPayload(
                UrgencyPayloadPolicy.CircumstanceRequiredElements(
                    required = setOf(
                        EmergencyPayloadElement.StationAddressed,
                        EmergencyPayloadElement.NatureOfEmergency,
                    ),
                    optionalOmitted = setOf(EmergencyPayloadElement.Heading),
                ),
            )
            .acceptedState()

        val recovered = active
            .copy(
                variationReason = StatedCircumstance("distressed aircraft unable to transmit full message"),
                policyMarker = PolicyMarker.RelayedDistressVariation,
            )
            .resolve(EmergencyMessageResolution.EmergencyMessageComplete)
            .acceptedState()

        check(recovered.state == EmergencyMessagePolicyState.Normal)
        check(recovered.emergencyKind == null)
        check(recovered.sender == null)
        check(recovered.currentStation == null)
        check(recovered.addressee == null)
        check(recovered.responsibleAreaStation == null)
        check(recovered.selectedPayloadElements.isEmpty())
        check(recovered.variationReason == null)
        check(recovered.frequencySelection == null)
        check(recovered.policyMarker == null)
    }

    @Test
    fun `negative guards prevent false emergency message policy coverage`() {
        check(EmergencyMessagePolicyProjection.from(EmergencyMessagePolicyEvent.RoutineTraffic).isRejected)
        check(EmergencyMessagePolicyProjection.from(EmergencyMessagePolicyEvent.PhraseologyOnlyEmergency).isRejected)
        check(EmergencyMessagePolicyProjection.from(EmergencyMessagePolicyEvent.GenericEmergencyLabel(EmergencyType.MAYDAY)).isRejected)
        check(EmergencyMessagePolicyProjection.from(EmergencyMessagePolicyEvent.GenericEmergencyLabel(EmergencyType.PAN_PAN)).isRejected)
        check(EmergencyMessagePolicyProjection.from(EmergencyMessagePolicyEvent.CommunicationsFailureControllerWorkflow).isRejected)
        check(
            EmergencyMessagePolicyProjection
                .normal()
                .resolve(EmergencyMessageResolution.EmergencyMessageComplete)
                .isRejected,
        )

        val distress = EmergencyMessagePolicyProjection
            .from(
                EmergencyMessagePolicyEvent.DistressMessageStarted(
                    sender = EmergencyMessageSender.DistressedAircraft,
                    currentStation = RoleName.TOWER,
                    responsibleAreaStation = RoleName.APPROACH,
                ),
            )
            .acceptedState()
        check(distress.selectUrgencyAddressing(UrgencyAddressingPolicy.FrequencyInUseAndCurrentStation).isRejected)
        check(distress.selectUrgencyPayload(UrgencyPayloadPolicy.CircumstanceRequiredElements(required = emptySet())).isRejected)
        check(distress.selectAddressee(AddressingPolicy.Absent).isRejected)

        val urgency = EmergencyMessagePolicyProjection
            .from(
                EmergencyMessagePolicyEvent.UrgencyMessageStarted(
                    currentStation = RoleName.TOWER,
                    responsibleAreaStation = RoleName.APPROACH,
                    frequencyInUse = MESSAGE_POLICY_CURRENT_FREQUENCY,
                ),
            )
            .acceptedState()
        check(urgency.selectAddressee(AddressingPolicy.CurrentStation).isRejected)
        check(
            urgency
                .selectUrgencyPayload(
                    UrgencyPayloadPolicy.CircumstanceRequiredElements(
                        required = setOf(EmergencyPayloadElement.NatureOfEmergency),
                        provided = emptySet(),
                    ),
                )
                .isRejected,
        )

        val relayedWithoutStatedCircumstance = EmergencyMessagePolicyProjection
            .from(
                EmergencyMessagePolicyEvent.RelayedDistressMessageStarted(
                    sender = EmergencyMessageSender.RelayingStation,
                    statedCircumstance = null,
                ),
            )
            .acceptedState()
        check(
            relayedWithoutStatedCircumstance
                .varyDistressPayload(RelayedDistressVariationPolicy.AllowWhenCircumstanceStated)
                .isRejected,
        )

        val relayedByDistressedAircraft = EmergencyMessagePolicyProjection
            .from(
                EmergencyMessagePolicyEvent.RelayedDistressMessageStarted(
                    sender = EmergencyMessageSender.DistressedAircraft,
                    statedCircumstance = StatedCircumstance("distressed aircraft unable to transmit full message"),
                ),
            )
            .acceptedState()
        check(
            relayedByDistressedAircraft
                .varyDistressPayload(RelayedDistressVariationPolicy.AllowWhenCircumstanceStated)
                .isRejected,
        )
    }

    private fun chunk08Ref(canonicalId: String): SourceUnitRef =
        ICAO9432.DistressUrgencyCommsFailure.Chunk08Items
            .single { source -> source.canonicalId == canonicalId }
            .toSourceUnitRef()

    private data class EmergencyMessagePolicyProjection(
        val state: EmergencyMessagePolicyState,
        val emergencyKind: EmergencyMessageKind?,
        val sender: EmergencyMessageSender?,
        val currentStation: RoleName?,
        val responsibleAreaStation: RoleName?,
        val addressee: EmergencyAddressee?,
        val selectedPayloadElements: Set<EmergencyPayloadElement>,
        val variationReason: StatedCircumstance?,
        val frequencySelection: EmergencyFrequencySelection?,
        val policyMarker: PolicyMarker?,
    ) {
        fun selectAddressee(policy: AddressingPolicy): StateTransition<EmergencyMessagePolicyProjection> =
            when {
                state !is EmergencyMessagePolicyState.Active ->
                    StateTransition.Rejected("Addressing policy requires active emergency message")
                emergencyKind != EmergencyMessageKind.Distress ->
                    StateTransition.Rejected("Distress addressing policy requires distress message")
                sender != EmergencyMessageSender.DistressedAircraft ->
                    StateTransition.Rejected("Distress addressing default requires distressed aircraft sender")
                policy == AddressingPolicy.Absent ->
                    StateTransition.Rejected("Addressing policy is required")
                currentStation == null ->
                    StateTransition.Rejected("Current station is required")
                policy == AddressingPolicy.CurrentStation ->
                    StateTransition.Accepted(
                        copy(
                            addressee = EmergencyAddressee.CurrentStation(currentStation),
                            policyMarker = PolicyMarker.DistressAddressing,
                        ),
                    )
                responsibleAreaStation == null ->
                    StateTransition.Rejected("Responsible area station is required")
                policy == AddressingPolicy.ResponsibleAreaStation ->
                    StateTransition.Accepted(
                        copy(
                            addressee = EmergencyAddressee.ResponsibleAreaStation(responsibleAreaStation),
                            policyMarker = PolicyMarker.DistressAddressing,
                        ),
                    )
                else ->
                    StateTransition.Rejected("Unsupported distress addressing policy")
            }

        fun varyDistressPayload(
            policy: RelayedDistressVariationPolicy,
        ): StateTransition<EmergencyMessagePolicyProjection> =
            when {
                state !is EmergencyMessagePolicyState.Active ->
                    StateTransition.Rejected("Payload variation requires active emergency message")
                emergencyKind != EmergencyMessageKind.Distress ->
                    StateTransition.Rejected("Relayed variation requires distress message")
                sender != EmergencyMessageSender.RelayingStation ->
                    StateTransition.Rejected("Relayed variation requires non-distressed relaying station")
                policy == RelayedDistressVariationPolicy.Absent ->
                    StateTransition.Rejected("Relayed variation policy is required")
                variationReason == null ->
                    StateTransition.Rejected("Relayed variation requires explicitly stated circumstance")
                policy == RelayedDistressVariationPolicy.AllowWhenCircumstanceStated ->
                    StateTransition.Accepted(
                        copy(
                            selectedPayloadElements = setOf(
                                EmergencyPayloadElement.StationAddressed,
                                EmergencyPayloadElement.AircraftIdentification,
                                EmergencyPayloadElement.NatureOfEmergency,
                                EmergencyPayloadElement.Position,
                            ),
                            policyMarker = PolicyMarker.RelayedDistressVariation,
                        ),
                    )
                else ->
                    StateTransition.Rejected("Unsupported relayed distress variation policy")
            }

        fun selectUrgencyPayload(
            policy: UrgencyPayloadPolicy,
        ): StateTransition<EmergencyMessagePolicyProjection> =
            when {
                state !is EmergencyMessagePolicyState.Active ->
                    StateTransition.Rejected("Urgency payload policy requires active emergency message")
                emergencyKind != EmergencyMessageKind.Urgency ->
                    StateTransition.Rejected("Urgency payload policy requires urgency message")
                policy == UrgencyPayloadPolicy.Absent ->
                    StateTransition.Rejected("Urgency payload policy is required")
                policy is UrgencyPayloadPolicy.CircumstanceRequiredElements && policy.required.isEmpty() ->
                    StateTransition.Rejected("At least one circumstance-required urgency element is required")
                policy is UrgencyPayloadPolicy.CircumstanceRequiredElements &&
                    !policy.provided.containsAll(policy.required) ->
                    StateTransition.Rejected("Urgency payload is missing a circumstance-required element")
                policy is UrgencyPayloadPolicy.CircumstanceRequiredElements ->
                    StateTransition.Accepted(
                        copy(
                            selectedPayloadElements = policy.provided - policy.optionalOmitted,
                            policyMarker = PolicyMarker.UrgencyPayload,
                        ),
                    )
                else ->
                    StateTransition.Rejected("Unsupported urgency payload policy")
            }

        fun selectUrgencyAddressing(
            policy: UrgencyAddressingPolicy,
        ): StateTransition<EmergencyMessagePolicyProjection> =
            when {
                state !is EmergencyMessagePolicyState.Active ->
                    StateTransition.Rejected("Urgency addressing policy requires active emergency message")
                emergencyKind != EmergencyMessageKind.Urgency ->
                    StateTransition.Rejected("Urgency addressing policy requires urgency message")
                frequencySelection !is EmergencyFrequencySelection.FrequencyInUse ->
                    StateTransition.Rejected("Urgency addressing requires frequency in use")
                policy == UrgencyAddressingPolicy.Absent ->
                    StateTransition.Rejected("Urgency addressing policy is required")
                policy == UrgencyAddressingPolicy.FrequencyInUseAndCurrentStation && currentStation != null ->
                    StateTransition.Accepted(
                        copy(
                            addressee = EmergencyAddressee.CurrentStation(currentStation),
                            policyMarker = PolicyMarker.UrgencyAddressing,
                        ),
                    )
                policy == UrgencyAddressingPolicy.FrequencyInUseAndResponsibleAreaStation &&
                    responsibleAreaStation != null ->
                    StateTransition.Accepted(
                        copy(
                            addressee = EmergencyAddressee.ResponsibleAreaStation(responsibleAreaStation),
                            policyMarker = PolicyMarker.UrgencyAddressing,
                        ),
                    )
                else ->
                    StateTransition.Rejected("Urgency addressing target is not available")
            }

        fun resolve(event: EmergencyMessageResolution): StateTransition<EmergencyMessagePolicyProjection> =
            when (event) {
                EmergencyMessageResolution.EmergencyMessageComplete ->
                    when (state) {
                        EmergencyMessagePolicyState.Active -> StateTransition.Accepted(normal())
                        EmergencyMessagePolicyState.Normal -> StateTransition.Rejected(
                            "Emergency message completion requires active emergency message policy",
                        )
                    }

                EmergencyMessageResolution.RoutineMessageComplete -> StateTransition.Rejected(
                    "Routine message completion does not resolve emergency message policy",
                )
            }

        companion object {
            fun from(event: EmergencyMessagePolicyEvent): StateTransition<EmergencyMessagePolicyProjection> =
                when (event) {
                    is EmergencyMessagePolicyEvent.DistressMessageStarted -> StateTransition.Accepted(
                        normal().copy(
                            state = EmergencyMessagePolicyState.Active,
                            emergencyKind = EmergencyMessageKind.Distress,
                            sender = event.sender,
                            currentStation = event.currentStation,
                            responsibleAreaStation = event.responsibleAreaStation,
                        ),
                    )

                    is EmergencyMessagePolicyEvent.RelayedDistressMessageStarted -> StateTransition.Accepted(
                        normal().copy(
                            state = EmergencyMessagePolicyState.Active,
                            emergencyKind = EmergencyMessageKind.Distress,
                            sender = event.sender,
                            variationReason = event.statedCircumstance,
                        ),
                    )

                    is EmergencyMessagePolicyEvent.UrgencyMessageStarted -> StateTransition.Accepted(
                        normal().copy(
                            state = EmergencyMessagePolicyState.Active,
                            emergencyKind = EmergencyMessageKind.Urgency,
                            sender = EmergencyMessageSender.UrgencyAircraft,
                            currentStation = event.currentStation,
                            responsibleAreaStation = event.responsibleAreaStation,
                            frequencySelection = EmergencyFrequencySelection.FrequencyInUse(event.frequencyInUse),
                        ),
                    )

                    EmergencyMessagePolicyEvent.RoutineTraffic,
                    EmergencyMessagePolicyEvent.PhraseologyOnlyEmergency,
                    EmergencyMessagePolicyEvent.CommunicationsFailureControllerWorkflow,
                    is EmergencyMessagePolicyEvent.GenericEmergencyLabel,
                    -> StateTransition.Rejected("Not typed emergency message policy evidence")
                }

            fun normal(): EmergencyMessagePolicyProjection =
                EmergencyMessagePolicyProjection(
                    state = EmergencyMessagePolicyState.Normal,
                    emergencyKind = null,
                    sender = null,
                    currentStation = null,
                    responsibleAreaStation = null,
                    addressee = null,
                    selectedPayloadElements = emptySet(),
                    variationReason = null,
                    frequencySelection = null,
                    policyMarker = null,
                )
        }
    }

    private sealed interface EmergencyMessagePolicyEvent {
        data class DistressMessageStarted(
            val sender: EmergencyMessageSender,
            val currentStation: RoleName,
            val responsibleAreaStation: RoleName,
        ) : EmergencyMessagePolicyEvent

        data class RelayedDistressMessageStarted(
            val sender: EmergencyMessageSender,
            val statedCircumstance: StatedCircumstance?,
        ) : EmergencyMessagePolicyEvent

        data class UrgencyMessageStarted(
            val currentStation: RoleName,
            val responsibleAreaStation: RoleName,
            val frequencyInUse: Frequency,
        ) : EmergencyMessagePolicyEvent

        data object RoutineTraffic : EmergencyMessagePolicyEvent
        data object PhraseologyOnlyEmergency : EmergencyMessagePolicyEvent
        data object CommunicationsFailureControllerWorkflow : EmergencyMessagePolicyEvent
        data class GenericEmergencyLabel(val emergencyType: EmergencyType) : EmergencyMessagePolicyEvent
    }

    private sealed interface EmergencyMessagePolicyState {
        data object Normal : EmergencyMessagePolicyState
        data object Active : EmergencyMessagePolicyState
    }

    private enum class EmergencyMessageKind {
        Distress,
        Urgency,
    }

    private enum class EmergencyMessageSender {
        DistressedAircraft,
        RelayingStation,
        UrgencyAircraft,
    }

    private enum class AddressingPolicy {
        CurrentStation,
        ResponsibleAreaStation,
        Absent,
    }

    private enum class RelayedDistressVariationPolicy {
        AllowWhenCircumstanceStated,
        Absent,
    }

    private sealed interface UrgencyPayloadPolicy {
        data class CircumstanceRequiredElements(
            val required: Set<EmergencyPayloadElement>,
            val optionalOmitted: Set<EmergencyPayloadElement> = emptySet(),
            val provided: Set<EmergencyPayloadElement> = required + optionalOmitted,
        ) : UrgencyPayloadPolicy

        data object Absent : UrgencyPayloadPolicy
    }

    private enum class UrgencyAddressingPolicy {
        FrequencyInUseAndCurrentStation,
        FrequencyInUseAndResponsibleAreaStation,
        Absent,
    }

    private sealed interface EmergencyAddressee {
        data class CurrentStation(val role: RoleName) : EmergencyAddressee
        data class ResponsibleAreaStation(val role: RoleName) : EmergencyAddressee
    }

    private enum class EmergencyPayloadElement {
        StationAddressed,
        AircraftIdentification,
        NatureOfEmergency,
        Intentions,
        Position,
        Level,
        Heading,
        UsefulInformation,
    }

    private data class StatedCircumstance(val value: String)

    private sealed interface EmergencyFrequencySelection {
        data class FrequencyInUse(val frequency: Frequency) : EmergencyFrequencySelection
    }

    private enum class PolicyMarker {
        DistressAddressing,
        RelayedDistressVariation,
        UrgencyPayload,
        UrgencyAddressing,
    }

    private enum class EmergencyMessageResolution {
        EmergencyMessageComplete,
        RoutineMessageComplete,
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

    private fun StateTransition<EmergencyMessagePolicyProjection>.acceptedState(): EmergencyMessagePolicyProjection =
        when (this) {
            is StateTransition.Accepted -> state
            is StateTransition.Rejected -> throw AssertionError("Expected accepted transition: $reason")
        }
}
