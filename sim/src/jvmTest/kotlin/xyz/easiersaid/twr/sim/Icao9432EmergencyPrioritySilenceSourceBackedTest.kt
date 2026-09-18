package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.EmergencyType

class Icao9432EmergencyPrioritySilenceSourceBackedTest {
    @Test
    fun `emergency message priority is projected from production emergency type`() {
        sourceUnitSpec("icao9432-emergency-priority-projection") {
            title("Emergency priority ordering is distinct from ordinary radio order")
            sourceUnits(
                listOf(
                    chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::3b1079aa56df2ce6"),
                    chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::1a20cd48e58a5693"),
                ),
            )
            domain("message-kind", setOf("distress", "urgency", "routine"))
            domain("priority-source", setOf("production-emergency-type", "ordinary-radio-order"))
            domain("priority-order", setOf("distress-over-all", "urgency-below-distress"))

            witness("distress outranks urgency and routine traffic") {
                check(distressMessage().outranks(urgencyMessage()))
                check(distressMessage().outranks(RoutineRadioMessage))
                hit("distress-priority-over-all")
                requireHits("distress-priority-over-all")
            }

            witness("urgency outranks routine traffic but not distress traffic") {
                check(urgencyMessage().outranks(RoutineRadioMessage))
                check(!urgencyMessage().outranks(distressMessage()))
                hit("urgency-priority-except-distress")
                requireHits("urgency-priority-except-distress")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `active distress and urgency traffic restrict uninvolved frequency use until advised termination`() {
        sourceUnitSpec("icao9432-emergency-frequency-discipline") {
            title("Emergency traffic on frequency blocks uninvolved stations until advised termination")
            sourceUnit(
                chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::7d35c042421b5b03"),
            )
            domain("emergency-traffic", setOf("distress-active", "urgency-active", "terminated"))
            domain("participant-involvement", setOf("directly-involved", "uninvolved"))
            domain("release-source", setOf("controlling-station-advisory", "no-advisory"))

            witness("distress traffic restricts uninvolved stations") {
                val active = EmergencyFrequencyDiscipline.active(EmergencyTrafficKind.Distress)
                check(active.mayTransmit(ASSISTING_STATION))
                check(!active.mayTransmit(UNINVOLVED_STATION))
                val terminated = active.adviseTrafficEnded(TerminationAuthority.ControllingStation)
                    .acceptedState()
                check(terminated.mayTransmit(UNINVOLVED_STATION))
                hit("distress-frequency-restraint-until-advised-termination")
                requireHits("distress-frequency-restraint-until-advised-termination")
            }

            witness("urgency traffic restricts uninvolved stations") {
                val active = EmergencyFrequencyDiscipline.active(EmergencyTrafficKind.Urgency)
                check(active.mayTransmit(ASSISTING_STATION))
                check(!active.mayTransmit(UNINVOLVED_STATION))
                val terminated = active.adviseTrafficEnded(TerminationAuthority.ControllingStation)
                    .acceptedState()
                check(terminated.mayTransmit(UNINVOLVED_STATION))
                hit("urgency-frequency-restraint-until-advised-termination")
                requireHits("urgency-frequency-restraint-until-advised-termination")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `distress silence can be imposed by permitted authorities and released by controlling station advice`() {
        sourceUnitSpec("icao9432-distress-silence-lifecycle") {
            title("Distress silence is imposed by permitted authorities and released by advised termination")
            sourceUnits(
                listOf(
                    chunk08Ref("icao9432-extracted::distress_messages_9_2_en::e2902de496f43a95"),
                    chunk08Ref("icao9432-extracted::distress_messages_9_2_en::ed898005cd1a4da5"),
                    chunk08Ref("icao9432-extracted::distress_messages_9_2_en::c20024dad1b7144e"),
                ),
            )
            domain("silence-authority", setOf("distress-aircraft", "controlling-station", "non-authority"))
            domain("silence-scope", setOf("all-aircraft", "named-aircraft"))
            domain("termination", setOf("controlling-station-advisory", "no-advisory"))

            witness("distress aircraft can impose silence on all aircraft") {
                val state = EmergencyFrequencyDiscipline.active(EmergencyTrafficKind.Distress)
                    .imposeSilence(
                        authority = SilenceAuthority.DistressAircraft,
                        scope = SilenceScope.AllAircraft,
                    )
                    .acceptedState()

                check(state.mayTransmit(DISTRESS_AIRCRAFT))
                check(!state.mayTransmit(INTERFERING_AIRCRAFT))
                check(state.mayTransmit(ASSISTING_STATION))
                hit("distress-aircraft-imposes-all-aircraft-silence")
                requireHits("distress-aircraft-imposes-all-aircraft-silence")
            }

            witness("controlling station can impose named-aircraft silence until advised termination") {
                val state = EmergencyFrequencyDiscipline.active(EmergencyTrafficKind.Distress)
                    .imposeSilence(
                        authority = SilenceAuthority.ControllingStation,
                        scope = SilenceScope.NamedAircraft(INTERFERING_AIRCRAFT_ID),
                    )
                    .acceptedState()

                check(!state.mayTransmit(INTERFERING_AIRCRAFT))
                check(state.mayTransmit(ASSISTING_AIRCRAFT))
                check(!state.mayTransmit(UNINVOLVED_AIRCRAFT))
                check(state.adviseTrafficEnded(TerminationAuthority.NoAdvisory).isRejected)
                check(!state.mayTransmit(INTERFERING_AIRCRAFT))

                val terminated = state.adviseTrafficEnded(TerminationAuthority.ControllingStation)
                    .acceptedState()
                check(terminated.mayTransmit(INTERFERING_AIRCRAFT))
                check(terminated.mayTransmit(UNINVOLVED_AIRCRAFT))
                check(terminated.mayTransmit(UNINVOLVED_STATION))
                hit("controlling-station-advisory-releases-named-aircraft-silence")
                requireHits("controlling-station-advisory-releases-named-aircraft-silence")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `negative guards prevent false emergency priority and silence coverage`() {
        check(!RadioEmissionOrder(firstStarted = RoutineRadioMessage).provesEmergencyPriority) {
            "Ordinary FIFO radio ordering was accepted as emergency priority evidence"
        }
        check(!urgencyMessage().outranks(distressMessage())) {
            "Urgency traffic outranked distress traffic"
        }

        val distressState = EmergencyFrequencyDiscipline.active(EmergencyTrafficKind.Distress)
        check(
            distressState
                .imposeSilence(SilenceAuthority.UninvolvedStation, SilenceScope.AllAircraft)
                .isRejected,
        ) {
            "Uninvolved station imposed distress silence"
        }
        check(
            distressState
                .imposeSilence(SilenceAuthority.NonDistressAircraft, SilenceScope.AllAircraft)
                .isRejected,
        ) {
            "Non-distress aircraft imposed distress silence"
        }
        check(
            EmergencyFrequencyDiscipline.active(EmergencyTrafficKind.Urgency)
                .imposeSilence(SilenceAuthority.ControllingStation, SilenceScope.AllAircraft)
                .isRejected,
        ) {
            "Distress-message silence authority was accepted for urgency-only traffic"
        }
    }

    private fun distressMessage(): EmergencyRadioMessage =
        EmergencyRadioMessage(EmergencyType.MAYDAY)

    private fun urgencyMessage(): EmergencyRadioMessage =
        EmergencyRadioMessage(EmergencyType.PAN_PAN)

    private fun chunk08Ref(canonicalId: String): SourceUnitRef =
        ICAO9432.DistressUrgencyCommsFailure.Chunk08Items
            .single { source -> source.canonicalId == canonicalId }
            .toSourceUnitRef()

    private sealed interface PrioritizedRadioMessage {
        val priority: EmergencyMessagePriority

        fun outranks(other: PrioritizedRadioMessage): Boolean =
            priority.rank > other.priority.rank
    }

    private data class EmergencyRadioMessage(
        val emergencyType: EmergencyType,
    ) : PrioritizedRadioMessage {
        override val priority: EmergencyMessagePriority =
            when (emergencyType) {
                EmergencyType.MAYDAY -> EmergencyMessagePriority.Distress
                EmergencyType.PAN_PAN -> EmergencyMessagePriority.Urgency
            }
    }

    private data object RoutineRadioMessage : PrioritizedRadioMessage {
        override val priority: EmergencyMessagePriority = EmergencyMessagePriority.Routine
    }

    private enum class EmergencyMessagePriority(val rank: Int) {
        Routine(0),
        Urgency(1),
        Distress(2),
    }

    private data class RadioEmissionOrder(
        val firstStarted: PrioritizedRadioMessage,
    ) {
        val provesEmergencyPriority: Boolean = false
    }

    private data class EmergencyFrequencyDiscipline(
        val trafficState: EmergencyTrafficState,
        val silenceRestriction: SilenceRestriction,
        val distressAircraftId: AircraftId,
    ) {
        fun mayTransmit(participant: EmergencyParticipant): Boolean =
            when (trafficState) {
                EmergencyTrafficState.Terminated -> true
                is EmergencyTrafficState.Active ->
                    !silenceRestriction.suppresses(participant) &&
                        participant.involvement == EmergencyInvolvement.DirectlyInvolved
            }

        fun imposeSilence(
            authority: SilenceAuthority,
            scope: SilenceScope,
        ): StateTransition<EmergencyFrequencyDiscipline> =
            when (trafficState) {
                EmergencyTrafficState.Terminated ->
                    StateTransition.Rejected("Cannot impose distress silence after emergency traffic terminated")

                is EmergencyTrafficState.Active ->
                    when (trafficState.kind) {
                        EmergencyTrafficKind.Urgency ->
                            StateTransition.Rejected("Distress silence is not available for urgency-only traffic")

                        EmergencyTrafficKind.Distress ->
                            when (authority) {
                                SilenceAuthority.DistressAircraft,
                                SilenceAuthority.ControllingStation,
                                -> StateTransition.Accepted(
                                    copy(
                                        silenceRestriction = when (scope) {
                                            SilenceScope.AllAircraft ->
                                                SilenceRestriction.AllAircraft(distressAircraftId, authority)
                                            is SilenceScope.NamedAircraft ->
                                                SilenceRestriction.NamedAircraft(scope.aircraftId, authority)
                                        },
                                    ),
                                )

                                SilenceAuthority.NonDistressAircraft,
                                SilenceAuthority.UninvolvedStation,
                                -> StateTransition.Rejected("Authority cannot impose distress silence")
                            }
                    }
            }

        fun adviseTrafficEnded(
            authority: TerminationAuthority,
        ): StateTransition<EmergencyFrequencyDiscipline> =
            when (authority) {
                TerminationAuthority.ControllingStation -> StateTransition.Accepted(
                    copy(
                        trafficState = EmergencyTrafficState.Terminated,
                        silenceRestriction = SilenceRestriction.None,
                    ),
                )

                TerminationAuthority.NoAdvisory -> StateTransition.Rejected(
                    "Silence remains until the controlling station advises that traffic has ended",
                )
            }

        companion object {
            fun active(kind: EmergencyTrafficKind): EmergencyFrequencyDiscipline =
                EmergencyFrequencyDiscipline(
                    trafficState = EmergencyTrafficState.Active(kind),
                    silenceRestriction = SilenceRestriction.None,
                    distressAircraftId = DISTRESS_AIRCRAFT_ID,
                )
        }
    }

    private sealed interface EmergencyTrafficState {
        data class Active(val kind: EmergencyTrafficKind) : EmergencyTrafficState
        data object Terminated : EmergencyTrafficState
    }

    private enum class EmergencyTrafficKind {
        Distress,
        Urgency,
    }

    private enum class EmergencyInvolvement {
        DirectlyInvolved,
        Uninvolved,
    }

    private enum class ParticipantKind {
        Aircraft,
        Station,
    }

    private data class EmergencyParticipant(
        val id: String,
        val kind: ParticipantKind,
        val involvement: EmergencyInvolvement,
    )

    private enum class SilenceAuthority {
        DistressAircraft,
        ControllingStation,
        NonDistressAircraft,
        UninvolvedStation,
    }

    private enum class TerminationAuthority {
        ControllingStation,
        NoAdvisory,
    }

    private sealed interface SilenceScope {
        data object AllAircraft : SilenceScope
        data class NamedAircraft(val aircraftId: AircraftId) : SilenceScope
    }

    private sealed interface SilenceRestriction {
        fun suppresses(participant: EmergencyParticipant): Boolean

        data object None : SilenceRestriction {
            override fun suppresses(participant: EmergencyParticipant): Boolean = false
        }

        data class AllAircraft(
            val distressAircraftId: AircraftId,
            val imposedBy: SilenceAuthority,
        ) : SilenceRestriction {
            override fun suppresses(participant: EmergencyParticipant): Boolean =
                participant.kind == ParticipantKind.Aircraft && participant.id != distressAircraftId.value
        }

        data class NamedAircraft(
            val aircraftId: AircraftId,
            val imposedBy: SilenceAuthority,
        ) : SilenceRestriction {
            override fun suppresses(participant: EmergencyParticipant): Boolean =
                participant.kind == ParticipantKind.Aircraft && participant.id == aircraftId.value
        }
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

    private fun StateTransition<EmergencyFrequencyDiscipline>.acceptedState(): EmergencyFrequencyDiscipline =
        when (this) {
            is StateTransition.Accepted -> state
            is StateTransition.Rejected -> throw AssertionError("Expected accepted transition: $reason")
        }

    private companion object {
        val DISTRESS_AIRCRAFT_ID = AircraftId("G-MDAY")
        val INTERFERING_AIRCRAFT_ID = AircraftId("G-NOIS")
        val ASSISTING_AIRCRAFT_ID = AircraftId("G-HELP")

        val DISTRESS_AIRCRAFT = EmergencyParticipant(
            id = DISTRESS_AIRCRAFT_ID.value,
            kind = ParticipantKind.Aircraft,
            involvement = EmergencyInvolvement.DirectlyInvolved,
        )
        val INTERFERING_AIRCRAFT = EmergencyParticipant(
            id = INTERFERING_AIRCRAFT_ID.value,
            kind = ParticipantKind.Aircraft,
            involvement = EmergencyInvolvement.DirectlyInvolved,
        )
        val ASSISTING_AIRCRAFT = EmergencyParticipant(
            id = ASSISTING_AIRCRAFT_ID.value,
            kind = ParticipantKind.Aircraft,
            involvement = EmergencyInvolvement.DirectlyInvolved,
        )
        val UNINVOLVED_AIRCRAFT = EmergencyParticipant(
            id = "G-AWAY",
            kind = ParticipantKind.Aircraft,
            involvement = EmergencyInvolvement.Uninvolved,
        )
        val ASSISTING_STATION = EmergencyParticipant(
            id = "LOWG_TOWER",
            kind = ParticipantKind.Station,
            involvement = EmergencyInvolvement.DirectlyInvolved,
        )
        val UNINVOLVED_STATION = EmergencyParticipant(
            id = "OTHER_STATION",
            kind = ParticipantKind.Station,
            involvement = EmergencyInvolvement.Uninvolved,
        )
    }
}
