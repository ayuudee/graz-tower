package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.protocol.EmergencyType
import xyz.easiersaid.twr.protocol.Frequency

private val CURRENT_EMERGENCY_FREQUENCY = Frequency.unsafe("121.500")
private val BETTER_ASSISTANCE_FREQUENCY = Frequency.unsafe("123.100")
private val ROUTINE_FREQUENCY = Frequency.unsafe("118.200")
private const val ADVICE = "expect vectors to nearest suitable aerodrome"
private const val INFORMATION = "runway available"
private const val INSTRUCTION = "maintain present heading"

private enum class EmergencyTrafficKind {
    Distress,
    Urgency,
}

private fun EmergencyType.toAssistanceTrafficKind(): EmergencyTrafficKind =
    when (this) {
        EmergencyType.MAYDAY -> EmergencyTrafficKind.Distress
        EmergencyType.PAN_PAN -> EmergencyTrafficKind.Urgency
    }

class Icao9432EmergencyAssistanceRelaySourceBackedTest {
    @Test
    fun `non addressed station or aircraft can assist when called ground station does not reply`() {
        sourceUnitSpec("icao9432-emergency-assistance-actor-projection") {
            title("Other stations or aircraft may reply and assist unacknowledged emergency traffic")
            sourceUnits(
                listOf(
                    chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::9c34a1b8d6d623fa"),
                    chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::82ee7048517d8478"),
                ),
            )
            domain("called-station-state", setOf("no-reply", "replied"))
            domain("assisting-actor", setOf("other-station", "other-aircraft", "distress-aircraft", "called-station"))
            domain("assistance-content", setOf("advice", "information", "instruction"))

            witness("other station assists with configured advice information and instruction") {
                val assisted = EmergencyAssistanceProjection
                    .from(
                        EmergencyAssistanceEvent.CalledGroundStationNoReply(
                            emergencyType = EmergencyType.MAYDAY,
                            calledStation = EmergencyActor.CalledGroundStation,
                        ),
                    )
                    .acceptedState()
                    .replyAndAssist(
                        actor = EmergencyActor.OtherStation,
                        policy = AssistanceContentPolicy.AdviceInformationInstruction,
                    )
                    .acceptedState()

                check(assisted.assistingActor == EmergencyActor.OtherStation)
                check(assisted.assistanceAction == AssistanceAction(
                    advice = ADVICE,
                    information = INFORMATION,
                    instruction = INSTRUCTION,
                ))
                hit("other-station-emergency-assistance")
                hit("assistance-content-policy-advice-information-instruction")
                requireHits("other-station-emergency-assistance")
                requireHits("assistance-content-policy-advice-information-instruction")
            }

            witness("other aircraft can assist unacknowledged distress traffic") {
                val assisted = EmergencyAssistanceProjection
                    .from(
                        EmergencyAssistanceEvent.CalledGroundStationNoReply(
                            emergencyType = EmergencyType.MAYDAY,
                            calledStation = EmergencyActor.CalledGroundStation,
                        ),
                    )
                    .acceptedState()
                    .replyAndAssist(
                        actor = EmergencyActor.OtherAircraft,
                        policy = AssistanceContentPolicy.AdviceInformationInstruction,
                    )
                    .acceptedState()

                check(assisted.assistingActor == EmergencyActor.OtherAircraft)
                hit("other-aircraft-emergency-assistance")
                requireHits("other-aircraft-emergency-assistance")
            }

            witness("other station can assist unacknowledged urgency traffic") {
                val assisted = EmergencyAssistanceProjection
                    .from(
                        EmergencyAssistanceEvent.CalledGroundStationNoReply(
                            emergencyType = EmergencyType.PAN_PAN,
                            calledStation = EmergencyActor.CalledGroundStation,
                        ),
                    )
                    .acceptedState()
                    .replyAndAssist(
                        actor = EmergencyActor.OtherStation,
                        policy = AssistanceContentPolicy.AdviceInformationInstruction,
                    )
                    .acceptedState()

                check(assisted.emergencyKind == EmergencyTrafficKind.Urgency)
                check(assisted.assistingActor == EmergencyActor.OtherStation)
                check(assisted.assistanceAction == AssistanceAction(
                    advice = ADVICE,
                    information = INFORMATION,
                    instruction = INSTRUCTION,
                ))
                hit("urgency-other-station-emergency-assistance")
                requireHits("urgency-other-station-emergency-assistance")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `emergency frequency policy keeps current frequency unless better assistance frequency is selected`() {
        sourceUnitSpec("icao9432-emergency-frequency-policy-projection") {
            title("Emergency calls use current frequency unless another frequency better assists")
            sourceUnits(
                listOf(
                    chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::24f94381ed9e8ce1"),
                    chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::2fee92c222323e6a"),
                    chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::cb12c2f9b97c64b7"),
                ),
            )
            domain("emergency-type", setOf("distress", "urgency"))
            domain("frequency-policy", setOf("current-frequency", "better-assistance-frequency"))
            domain("selection-reason", setOf("normal-current", "necessary-or-desirable"))

            witness("distress normally starts and continues on current frequency") {
                val selected = EmergencyAssistanceProjection
                    .from(
                        EmergencyAssistanceEvent.EmergencyCallStarted(
                            emergencyType = EmergencyType.MAYDAY,
                            frequencyInUse = CURRENT_EMERGENCY_FREQUENCY,
                        ),
                    )
                    .acceptedState()
                    .selectFrequency(EmergencyFrequencyPolicy.CurrentFrequencyUnlessBetterAssistance)
                    .acceptedState()

                check(selected.selectedFrequency == EmergencyFrequencySelection(
                    frequency = CURRENT_EMERGENCY_FREQUENCY,
                    reason = FrequencySelectionReason.NormalCurrentFrequency,
                ))
                hit("distress-current-frequency-policy")
                requireHits("distress-current-frequency-policy")
            }

            witness("urgency normally starts on current frequency") {
                val selected = EmergencyAssistanceProjection
                    .from(
                        EmergencyAssistanceEvent.EmergencyCallStarted(
                            emergencyType = EmergencyType.PAN_PAN,
                            frequencyInUse = CURRENT_EMERGENCY_FREQUENCY,
                        ),
                    )
                    .acceptedState()
                    .selectFrequency(EmergencyFrequencyPolicy.CurrentFrequencyUnlessBetterAssistance)
                    .acceptedState()

                check(selected.emergencyKind == EmergencyTrafficKind.Urgency)
                check(selected.selectedFrequency == EmergencyFrequencySelection(
                    frequency = CURRENT_EMERGENCY_FREQUENCY,
                    reason = FrequencySelectionReason.NormalCurrentFrequency,
                ))
                hit("urgency-current-frequency-policy")
                requireHits("urgency-current-frequency-policy")
            }

            witness("another frequency is selected when better assistance is configured") {
                val selected = EmergencyAssistanceProjection
                    .from(
                        EmergencyAssistanceEvent.EmergencyCallStarted(
                            emergencyType = EmergencyType.PAN_PAN,
                            frequencyInUse = CURRENT_EMERGENCY_FREQUENCY,
                        ),
                    )
                    .acceptedState()
                    .selectFrequency(
                        EmergencyFrequencyPolicy.ChangeToBetterAssistanceFrequency(BETTER_ASSISTANCE_FREQUENCY),
                    )
                    .acceptedState()

                check(selected.selectedFrequency == EmergencyFrequencySelection(
                    frequency = BETTER_ASSISTANCE_FREQUENCY,
                    reason = FrequencySelectionReason.NecessaryOrDesirableForAssistance,
                ))
                hit("alternate-emergency-frequency-necessary-or-desirable")
                requireHits("alternate-emergency-frequency-necessary-or-desirable")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `intercepting aircraft can relay unacknowledged distress`() {
        sourceUnitSpec("icao9432-intercepted-distress-relay-projection") {
            title("Intercepting aircraft may acknowledge and broadcast unacknowledged distress")
            sourceUnit(
                chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::8e9f7818b91d08c3"),
            )
            domain("relay-actor", setOf("intercepting-aircraft", "uninvolved-station"))
            domain("message-state", setOf("unacknowledged-distress", "non-emergency"))
            domain("relay-action", setOf("acknowledge-and-broadcast", "reject"))

            witness("intercepting aircraft acknowledges and broadcasts unacknowledged distress") {
                val relayed = EmergencyAssistanceProjection
                    .from(EmergencyAssistanceEvent.UnacknowledgedDistressIntercepted)
                    .acceptedState()
                    .relayInterceptedDistress(EmergencyActor.InterceptingAircraft)
                    .acceptedState()

                check(relayed.relayState == InterceptedDistressRelayState.AcknowledgedAndBroadcastByInterceptingAircraft)
                hit("intercepting-aircraft-acknowledges-broadcasts-distress")
                requireHits("intercepting-aircraft-acknowledges-broadcasts-distress")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `distress and urgency interference suppression is policy bound`() {
        sourceUnitSpec("icao9432-emergency-interference-suppression-projection") {
            title("Other stations avoid superfluous or interfering transmissions during emergency traffic")
            sourceUnits(
                listOf(
                    chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::06f7a72397c325ac"),
                    chunk08Ref("icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::5df94af7a64c3f5d"),
                ),
            )
            domain("traffic-kind", setOf("distress", "urgency"))
            domain("transmission-kind", setOf("superfluous", "direct-assistance"))
            domain("policy", setOf("suppress-superfluous", "no-policy"))

            witness("distress policy suppresses superfluous uninvolved transmissions") {
                val suppressed = EmergencyAssistanceProjection
                    .from(EmergencyAssistanceEvent.ActiveEmergencyTraffic(EmergencyType.MAYDAY))
                    .acceptedState()
                    .evaluateTransmission(
                        actor = EmergencyActor.UninvolvedStation,
                        kind = EmergencyTransmissionKind.Superfluous,
                        policy = EmergencyInterferencePolicy.SuppressSuperfluous,
                    )
                    .acceptedState()

                check(suppressed.suppressedTransmissions == listOf(SuppressedTransmission(
                    actor = EmergencyActor.UninvolvedStation,
                    trafficKind = EmergencyTrafficKind.Distress,
                    reason = SuppressionReason.SuperfluousDuringEmergency,
                )))
                hit("distress-superfluous-transmission-suppressed")
                requireHits("distress-superfluous-transmission-suppressed")
            }

            witness("urgency policy suppresses other-station interference") {
                val suppressed = EmergencyAssistanceProjection
                    .from(EmergencyAssistanceEvent.ActiveEmergencyTraffic(EmergencyType.PAN_PAN))
                    .acceptedState()
                    .evaluateTransmission(
                        actor = EmergencyActor.UninvolvedStation,
                        kind = EmergencyTransmissionKind.Superfluous,
                        policy = EmergencyInterferencePolicy.SuppressSuperfluous,
                    )
                    .acceptedState()

                check(suppressed.suppressedTransmissions == listOf(SuppressedTransmission(
                    actor = EmergencyActor.UninvolvedStation,
                    trafficKind = EmergencyTrafficKind.Urgency,
                    reason = SuppressionReason.SuperfluousDuringEmergency,
                )))
                hit("urgency-interfering-transmission-suppressed")
                requireHits("urgency-interfering-transmission-suppressed")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `recovery clears all emergency assistance relay and frequency derived state`() {
        val activeAssistance = EmergencyAssistanceProjection
            .from(
                EmergencyAssistanceEvent.CalledGroundStationNoReply(
                    emergencyType = EmergencyType.MAYDAY,
                    calledStation = EmergencyActor.CalledGroundStation,
                ),
            )
            .acceptedState()
            .replyAndAssist(
                actor = EmergencyActor.OtherStation,
                policy = AssistanceContentPolicy.AdviceInformationInstruction,
            )
            .acceptedState()
            .selectFrequency(
                EmergencyFrequencyPolicy.ChangeToBetterAssistanceFrequency(BETTER_ASSISTANCE_FREQUENCY),
            )
            .acceptedState()
            .evaluateTransmission(
                actor = EmergencyActor.UninvolvedStation,
                kind = EmergencyTransmissionKind.Superfluous,
                policy = EmergencyInterferencePolicy.SuppressSuperfluous,
            )
            .acceptedState()

        val active = activeAssistance.copy(
            relayState = InterceptedDistressRelayState.AcknowledgedAndBroadcastByInterceptingAircraft,
        )

        val recovered = active.resolve(EmergencyResolution.EmergencyTrafficEnded).acceptedState()

        check(recovered.state == EmergencyAssistanceState.Normal)
        check(recovered.emergencyKind == null)
        check(recovered.calledStationNoReply == null)
        check(recovered.assistingActor == null)
        check(recovered.assistanceAction == null)
        check(recovered.selectedFrequency == null)
        check(recovered.suppressedTransmissions.isEmpty())
        check(recovered.relayState == InterceptedDistressRelayState.None)
    }

    @Test
    fun `negative guards prevent false assistance relay frequency and suppression coverage`() {
        check(EmergencyAssistanceProjection.from(EmergencyAssistanceEvent.RoutineTraffic).isRejected)
        check(EmergencyAssistanceProjection.from(EmergencyAssistanceEvent.RoutineNoReply).isRejected)
        check(EmergencyAssistanceProjection.from(EmergencyAssistanceEvent.RoutineFrequencyTransferFailure).isRejected)
        check(EmergencyAssistanceProjection.from(EmergencyAssistanceEvent.PhraseologyOnlyEmergency).isRejected)
        check(EmergencyAssistanceProjection.from(EmergencyAssistanceEvent.CommunicationsFailureControllerRelay).isRejected)

        val genericDistress = EmergencyAssistanceProjection
            .from(EmergencyAssistanceEvent.GenericEmergencyWithoutAssistanceContext(EmergencyType.MAYDAY))
        val genericUrgency = EmergencyAssistanceProjection
            .from(EmergencyAssistanceEvent.GenericEmergencyWithoutAssistanceContext(EmergencyType.PAN_PAN))
        check(genericDistress.isRejected)
        check(genericUrgency.isRejected)

        val noReply = EmergencyAssistanceProjection
            .from(
                EmergencyAssistanceEvent.CalledGroundStationNoReply(
                    emergencyType = EmergencyType.MAYDAY,
                    calledStation = EmergencyActor.CalledGroundStation,
                ),
            )
            .acceptedState()
        check(noReply.replyAndAssist(EmergencyActor.DistressAircraft, AssistanceContentPolicy.AdviceInformationInstruction).isRejected)
        check(noReply.replyAndAssist(EmergencyActor.CalledGroundStation, AssistanceContentPolicy.AdviceInformationInstruction).isRejected)
        check(noReply.replyAndAssist(EmergencyActor.OtherStation, AssistanceContentPolicy.Absent).isRejected)

        val active = EmergencyAssistanceProjection
            .from(EmergencyAssistanceEvent.ActiveEmergencyTraffic(EmergencyType.MAYDAY))
            .acceptedState()
        val malformedActive = active.copy(emergencyKind = null)
        check(
            malformedActive
                .evaluateTransmission(
                    actor = EmergencyActor.OtherStation,
                    kind = EmergencyTransmissionKind.DirectAssistance,
                    policy = EmergencyInterferencePolicy.SuppressSuperfluous,
                )
                .isRejected,
        )
        check(
            active
                .evaluateTransmission(
                    actor = EmergencyActor.UninvolvedStation,
                    kind = EmergencyTransmissionKind.Superfluous,
                    policy = EmergencyInterferencePolicy.NoPolicy,
                )
                .isRejected,
        )
        check(
            active
                .evaluateTransmission(
                    actor = EmergencyActor.OtherStation,
                    kind = EmergencyTransmissionKind.DirectAssistance,
                    policy = EmergencyInterferencePolicy.SuppressSuperfluous,
                )
                .acceptedState()
                .suppressedTransmissions
                .isEmpty(),
        )

        val intercepted = EmergencyAssistanceProjection
            .from(EmergencyAssistanceEvent.UnacknowledgedDistressIntercepted)
            .acceptedState()
        check(intercepted.relayInterceptedDistress(EmergencyActor.UninvolvedStation).isRejected)
        check(EmergencyAssistanceProjection.from(EmergencyAssistanceEvent.NonEmergencyRelay).isRejected)
    }

    private fun chunk08Ref(canonicalId: String): SourceUnitRef =
        ICAO9432.DistressUrgencyCommsFailure.Chunk08Items
            .single { source -> source.canonicalId == canonicalId }
            .toSourceUnitRef()

    private data class EmergencyAssistanceProjection(
        val state: EmergencyAssistanceState,
        val emergencyKind: EmergencyTrafficKind?,
        val calledStationNoReply: EmergencyActor?,
        val assistingActor: EmergencyActor?,
        val assistanceAction: AssistanceAction?,
        val selectedFrequency: EmergencyFrequencySelection?,
        val suppressedTransmissions: List<SuppressedTransmission>,
        val relayState: InterceptedDistressRelayState,
    ) {
        fun replyAndAssist(
            actor: EmergencyActor,
            policy: AssistanceContentPolicy,
        ): StateTransition<EmergencyAssistanceProjection> =
            when {
                state !is EmergencyAssistanceState.Active ->
                    StateTransition.Rejected("Assistance requires active emergency state")
                calledStationNoReply == null ->
                    StateTransition.Rejected("Assistance requires no reply from called ground station")
                actor == EmergencyActor.DistressAircraft ->
                    StateTransition.Rejected("Distress aircraft cannot satisfy other-station/aircraft assistance")
                actor == calledStationNoReply ->
                    StateTransition.Rejected("Originally called station cannot satisfy other-station/aircraft assistance")
                actor !in setOf(EmergencyActor.OtherStation, EmergencyActor.OtherAircraft) ->
                    StateTransition.Rejected("Actor is not an assisting station or aircraft")
                policy == AssistanceContentPolicy.Absent ->
                    StateTransition.Rejected("Assistance content policy is required")
                else -> StateTransition.Accepted(
                    copy(
                        assistingActor = actor,
                        assistanceAction = AssistanceAction(
                            advice = ADVICE,
                            information = INFORMATION,
                            instruction = INSTRUCTION,
                        ),
                    ),
                )
            }

        fun selectFrequency(
            policy: EmergencyFrequencyPolicy,
        ): StateTransition<EmergencyAssistanceProjection> =
            when {
                state !is EmergencyAssistanceState.Active ->
                    StateTransition.Rejected("Frequency policy requires active emergency state")
                else ->
                    when (policy) {
                        EmergencyFrequencyPolicy.CurrentFrequencyUnlessBetterAssistance ->
                            StateTransition.Accepted(
                                copy(
                                    selectedFrequency = EmergencyFrequencySelection(
                                        frequency = CURRENT_EMERGENCY_FREQUENCY,
                                        reason = FrequencySelectionReason.NormalCurrentFrequency,
                                    ),
                                ),
                            )

                        is EmergencyFrequencyPolicy.ChangeToBetterAssistanceFrequency ->
                            StateTransition.Accepted(
                                copy(
                                    selectedFrequency = EmergencyFrequencySelection(
                                        frequency = policy.frequency,
                                        reason = FrequencySelectionReason.NecessaryOrDesirableForAssistance,
                                    ),
                                ),
                            )

                        EmergencyFrequencyPolicy.Absent ->
                            StateTransition.Rejected("Emergency frequency policy is required")
                    }
            }

        fun relayInterceptedDistress(
            actor: EmergencyActor,
        ): StateTransition<EmergencyAssistanceProjection> =
            when {
                relayState != InterceptedDistressRelayState.UnacknowledgedDistress ->
                    StateTransition.Rejected("Relay requires intercepted unacknowledged distress")
                actor != EmergencyActor.InterceptingAircraft ->
                    StateTransition.Rejected("Only intercepting aircraft satisfies intercepted-distress relay")
                else -> StateTransition.Accepted(
                    copy(relayState = InterceptedDistressRelayState.AcknowledgedAndBroadcastByInterceptingAircraft),
                )
            }

        fun evaluateTransmission(
            actor: EmergencyActor,
            kind: EmergencyTransmissionKind,
            policy: EmergencyInterferencePolicy,
        ): StateTransition<EmergencyAssistanceProjection> =
            when {
                state !is EmergencyAssistanceState.Active ->
                    StateTransition.Rejected("Suppression requires active emergency traffic")
                policy == EmergencyInterferencePolicy.NoPolicy ->
                    StateTransition.Rejected("Emergency interference policy is required")
                emergencyKind == null ->
                    StateTransition.Rejected("Emergency kind is required")
                kind == EmergencyTransmissionKind.DirectAssistance ->
                    StateTransition.Accepted(this)
                actor == EmergencyActor.UninvolvedStation && kind == EmergencyTransmissionKind.Superfluous ->
                    StateTransition.Accepted(
                        copy(
                            suppressedTransmissions = suppressedTransmissions + SuppressedTransmission(
                                actor = actor,
                                trafficKind = emergencyKind,
                                reason = SuppressionReason.SuperfluousDuringEmergency,
                            ),
                        ),
                    )
                else ->
                    StateTransition.Rejected("Transmission is not suppressible emergency interference")
            }

        fun resolve(event: EmergencyResolution): StateTransition<EmergencyAssistanceProjection> =
            when (event) {
                EmergencyResolution.EmergencyTrafficEnded -> StateTransition.Accepted(normal())
                EmergencyResolution.RoutineCallAnswered -> StateTransition.Rejected(
                    "Routine call answer does not resolve emergency assistance projection",
                )
            }

        companion object {
            fun from(event: EmergencyAssistanceEvent): StateTransition<EmergencyAssistanceProjection> =
                when (event) {
                    is EmergencyAssistanceEvent.CalledGroundStationNoReply -> StateTransition.Accepted(
                        active(event.emergencyType).copy(calledStationNoReply = event.calledStation),
                    )

                    is EmergencyAssistanceEvent.EmergencyCallStarted -> StateTransition.Accepted(
                        active(event.emergencyType).copy(
                            selectedFrequency = EmergencyFrequencySelection(
                                frequency = event.frequencyInUse,
                                reason = FrequencySelectionReason.NormalCurrentFrequency,
                            ),
                        ),
                    )

                    EmergencyAssistanceEvent.UnacknowledgedDistressIntercepted -> StateTransition.Accepted(
                        active(EmergencyType.MAYDAY).copy(
                            relayState = InterceptedDistressRelayState.UnacknowledgedDistress,
                        ),
                    )

                    is EmergencyAssistanceEvent.ActiveEmergencyTraffic -> StateTransition.Accepted(
                        active(event.emergencyType),
                    )

                    EmergencyAssistanceEvent.RoutineTraffic,
                    EmergencyAssistanceEvent.RoutineNoReply,
                    EmergencyAssistanceEvent.RoutineFrequencyTransferFailure,
                    EmergencyAssistanceEvent.PhraseologyOnlyEmergency,
                    EmergencyAssistanceEvent.CommunicationsFailureControllerRelay,
                    EmergencyAssistanceEvent.NonEmergencyRelay,
                    is EmergencyAssistanceEvent.GenericEmergencyWithoutAssistanceContext,
                    -> StateTransition.Rejected("Not typed emergency assistance evidence")
                }

            private fun active(emergencyType: EmergencyType): EmergencyAssistanceProjection =
                normal().copy(
                    state = EmergencyAssistanceState.Active,
                    emergencyKind = emergencyType.toAssistanceTrafficKind(),
                )

            private fun normal(): EmergencyAssistanceProjection =
                EmergencyAssistanceProjection(
                    state = EmergencyAssistanceState.Normal,
                    emergencyKind = null,
                    calledStationNoReply = null,
                    assistingActor = null,
                    assistanceAction = null,
                    selectedFrequency = null,
                    suppressedTransmissions = emptyList(),
                    relayState = InterceptedDistressRelayState.None,
                )
        }
    }

    private sealed interface EmergencyAssistanceEvent {
        data class CalledGroundStationNoReply(
            val emergencyType: EmergencyType,
            val calledStation: EmergencyActor,
        ) : EmergencyAssistanceEvent

        data class EmergencyCallStarted(
            val emergencyType: EmergencyType,
            val frequencyInUse: Frequency,
        ) : EmergencyAssistanceEvent

        data object UnacknowledgedDistressIntercepted : EmergencyAssistanceEvent
        data class ActiveEmergencyTraffic(val emergencyType: EmergencyType) : EmergencyAssistanceEvent
        data object RoutineTraffic : EmergencyAssistanceEvent
        data object RoutineNoReply : EmergencyAssistanceEvent
        data object RoutineFrequencyTransferFailure : EmergencyAssistanceEvent
        data object PhraseologyOnlyEmergency : EmergencyAssistanceEvent
        data object CommunicationsFailureControllerRelay : EmergencyAssistanceEvent
        data object NonEmergencyRelay : EmergencyAssistanceEvent
        data class GenericEmergencyWithoutAssistanceContext(
            val emergencyType: EmergencyType,
        ) : EmergencyAssistanceEvent
    }

    private sealed interface EmergencyAssistanceState {
        data object Normal : EmergencyAssistanceState
        data object Active : EmergencyAssistanceState
    }

    private enum class EmergencyActor {
        DistressAircraft,
        CalledGroundStation,
        OtherStation,
        OtherAircraft,
        InterceptingAircraft,
        UninvolvedStation,
    }

    private enum class AssistanceContentPolicy {
        AdviceInformationInstruction,
        Absent,
    }

    private data class AssistanceAction(
        val advice: String?,
        val information: String?,
        val instruction: String?,
    )

    private sealed interface EmergencyFrequencyPolicy {
        data object CurrentFrequencyUnlessBetterAssistance : EmergencyFrequencyPolicy
        data class ChangeToBetterAssistanceFrequency(val frequency: Frequency) : EmergencyFrequencyPolicy
        data object Absent : EmergencyFrequencyPolicy
    }

    private data class EmergencyFrequencySelection(
        val frequency: Frequency,
        val reason: FrequencySelectionReason,
    )

    private enum class FrequencySelectionReason {
        NormalCurrentFrequency,
        NecessaryOrDesirableForAssistance,
    }

    private enum class EmergencyInterferencePolicy {
        SuppressSuperfluous,
        NoPolicy,
    }

    private enum class EmergencyTransmissionKind {
        Superfluous,
        DirectAssistance,
    }

    private data class SuppressedTransmission(
        val actor: EmergencyActor,
        val trafficKind: EmergencyTrafficKind,
        val reason: SuppressionReason,
    )

    private enum class SuppressionReason {
        SuperfluousDuringEmergency,
    }

    private enum class InterceptedDistressRelayState {
        None,
        UnacknowledgedDistress,
        AcknowledgedAndBroadcastByInterceptingAircraft,
    }

    private enum class EmergencyResolution {
        EmergencyTrafficEnded,
        RoutineCallAnswered,
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

    private fun StateTransition<EmergencyAssistanceProjection>.acceptedState(): EmergencyAssistanceProjection =
        when (this) {
            is StateTransition.Accepted -> state
            is StateTransition.Rejected -> throw AssertionError("Expected accepted transition: $reason")
        }
}
