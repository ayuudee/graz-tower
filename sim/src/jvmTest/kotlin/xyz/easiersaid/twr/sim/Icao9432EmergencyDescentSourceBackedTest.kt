package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.protocol.AircraftId
import xyz.easiersaid.twr.protocol.EmergencyType

class Icao9432EmergencyDescentSourceBackedTest {
    @Test
    fun `emergency descent announcement activates safeguarding and general warning`() {
        sourceUnitSpec("icao9432-emergency-descent-safeguarding-projection") {
            title("Emergency descent announcement activates safeguarding for other aircraft")
            sourceUnits(
                listOf(
                    chunk08Ref("icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::082f9668292ed82c"),
                    chunk08Ref("icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::c71568b00fb1535e"),
                ),
            )
            domain("trigger", setOf("emergency-descent-announcement", "ordinary-descent", "go-around"))
            domain("affected-traffic", setOf("known-other-aircraft", "none"))
            domain("controller-action", setOf("general-warning", "specific-instructions-policy-blocked"))

            witness("emergency descent announcement safeguards affected traffic") {
                val projection = EmergencyDescentSafeguardingProjection
                    .from(
                        event = EmergencyDescentEvent.EmergencyDescentAnnouncement(
                            emergencyAircraftId = EMERGENCY_AIRCRAFT_ID,
                            affectedTraffic = listOf(AFFECTED_AIRCRAFT_ID),
                            positionKnown = true,
                        ),
                    )
                    .acceptedState()

                check(projection.state == EmergencyDescentState.Active)
                check(projection.emergencyAircraftId == EMERGENCY_AIRCRAFT_ID)
                check(projection.affectedTraffic == setOf(AFFECTED_AIRCRAFT_ID))
                check(
                    projection.safeguardActions == setOf(
                        SafeguardAction.GeneralEmergencyDescentWarning(AFFECTED_AIRCRAFT_ID),
                    ),
                )
                hit("emergency-descent-safeguarding-active")
                hit("emergency-descent-general-warning-issued")
                requireHits("emergency-descent-safeguarding-active")
                requireHits("emergency-descent-general-warning-issued")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `emergency descent safeguarding reset clears derived state`() {
        val active = EmergencyDescentSafeguardingProjection
            .from(
                event = EmergencyDescentEvent.EmergencyDescentAnnouncement(
                    emergencyAircraftId = EMERGENCY_AIRCRAFT_ID,
                    affectedTraffic = listOf(AFFECTED_AIRCRAFT_ID),
                    positionKnown = false,
                ),
            )
            .acceptedState()

        check(active.positionQuestion == PositionQuestion(EMERGENCY_AIRCRAFT_ID))

        val resolved = active.resolve(ResolutionEvent.EmergencyDescentNoLongerActive)
            .acceptedState()

        check(resolved.state == EmergencyDescentState.Resolved)
        check(resolved.emergencyAircraftId == null)
        check(resolved.affectedTraffic.isEmpty())
        check(resolved.safeguardActions.isEmpty())
        check(resolved.positionQuestion == null)
    }

    @Test
    fun `ordinary descent go-around and urgency alone do not activate emergency descent safeguarding`() {
        check(EmergencyDescentSafeguardingProjection.from(EmergencyDescentEvent.OrdinaryDescent).isRejected) {
            "Ordinary descent activated emergency-descent safeguarding"
        }
        check(EmergencyDescentSafeguardingProjection.from(EmergencyDescentEvent.GoAround).isRejected) {
            "Go-around activated emergency-descent safeguarding"
        }
        check(
            EmergencyDescentSafeguardingProjection
                .from(EmergencyDescentEvent.UrgencyWithoutEmergencyDescent(EmergencyType.PAN_PAN))
                .isRejected,
        ) {
            "Urgency/PAN PAN without emergency-descent announcement activated safeguarding"
        }
        check(!RadioEmissionOrder(firstStarted = EmergencyDescentEvent.OrdinaryDescent).provesEmergencyDescent) {
            "Ordinary radio ordering was accepted as emergency-descent evidence"
        }
    }

    @Test
    fun `position uncertainty supports further emergency descent position questioning`() {
        sourceUnitSpec("icao9432-emergency-descent-position-question") {
            title("Emergency descent position uncertainty supports further position questions")
            sourceUnit(
                chunk08Ref("icao9432-extracted::urgency_emergency_descent_9_3_to_9_4_en::ca0c243491ff5d13"),
            )
            domain("position-evidence", setOf("known", "uncertain"))
            domain("controller-action", setOf("no-question", "position-question"))

            witness("uncertain emergency descent position supports position question") {
                val activeWithUncertainPosition = EmergencyDescentSafeguardingProjection
                    .from(
                        event = EmergencyDescentEvent.EmergencyDescentAnnouncement(
                            emergencyAircraftId = EMERGENCY_AIRCRAFT_ID,
                            affectedTraffic = listOf(AFFECTED_AIRCRAFT_ID),
                            positionKnown = false,
                        ),
                    )
                    .acceptedState()

                check(activeWithUncertainPosition.positionQuestion == PositionQuestion(EMERGENCY_AIRCRAFT_ID))
                hit("emergency-descent-position-question")
                requireHits("emergency-descent-position-question")
            }
        }.assertSatisfied().assertNoModelGaps()

        val activeWithKnownPosition = EmergencyDescentSafeguardingProjection
            .from(
                event = EmergencyDescentEvent.EmergencyDescentAnnouncement(
                    emergencyAircraftId = EMERGENCY_AIRCRAFT_ID,
                    affectedTraffic = listOf(AFFECTED_AIRCRAFT_ID),
                    positionKnown = true,
                ),
            )
            .acceptedState()

        check(activeWithKnownPosition.positionQuestion == null) {
            "Known-position emergency descent produced position-question evidence"
        }
    }

    private fun chunk08Ref(canonicalId: String): SourceUnitRef =
        ICAO9432.DistressUrgencyCommsFailure.Chunk08Items
            .single { source -> source.canonicalId == canonicalId }
            .toSourceUnitRef()

    private sealed interface EmergencyDescentEvent {
        data class EmergencyDescentAnnouncement(
            val emergencyAircraftId: AircraftId,
            val affectedTraffic: List<AircraftId>,
            val positionKnown: Boolean,
        ) : EmergencyDescentEvent

        data object OrdinaryDescent : EmergencyDescentEvent
        data object GoAround : EmergencyDescentEvent
        data class UrgencyWithoutEmergencyDescent(val emergencyType: EmergencyType) : EmergencyDescentEvent
    }

    private data class EmergencyDescentSafeguardingProjection(
        val state: EmergencyDescentState,
        val emergencyAircraftId: AircraftId?,
        val affectedTraffic: Set<AircraftId>,
        val safeguardActions: Set<SafeguardAction>,
        val positionQuestion: PositionQuestion?,
    ) {
        fun resolve(event: ResolutionEvent): StateTransition<EmergencyDescentSafeguardingProjection> =
            when (event) {
                ResolutionEvent.EmergencyDescentNoLongerActive -> StateTransition.Accepted(resolved())
                ResolutionEvent.OrdinaryAltitudeStabilized -> StateTransition.Rejected(
                    "Ordinary altitude stabilization does not resolve emergency-descent safeguarding",
                )
            }

        private fun resolved(): EmergencyDescentSafeguardingProjection =
            EmergencyDescentSafeguardingProjection(
                state = EmergencyDescentState.Resolved,
                emergencyAircraftId = null,
                affectedTraffic = emptySet(),
                safeguardActions = emptySet(),
                positionQuestion = null,
            )

        companion object {
            fun from(event: EmergencyDescentEvent): StateTransition<EmergencyDescentSafeguardingProjection> =
                when (event) {
                    is EmergencyDescentEvent.EmergencyDescentAnnouncement -> StateTransition.Accepted(
                        EmergencyDescentSafeguardingProjection(
                            state = EmergencyDescentState.Active,
                            emergencyAircraftId = event.emergencyAircraftId,
                            affectedTraffic = event.affectedTraffic.toSet(),
                            safeguardActions = event.affectedTraffic
                                .map(SafeguardAction::GeneralEmergencyDescentWarning)
                                .toSet(),
                            positionQuestion = if (event.positionKnown) {
                                null
                            } else {
                                PositionQuestion(event.emergencyAircraftId)
                            },
                        ),
                    )

                    EmergencyDescentEvent.GoAround,
                    EmergencyDescentEvent.OrdinaryDescent,
                    is EmergencyDescentEvent.UrgencyWithoutEmergencyDescent,
                    -> StateTransition.Rejected("Not an emergency descent announcement")
                }
        }
    }

    private enum class EmergencyDescentState {
        Active,
        Resolved,
    }

    private enum class ResolutionEvent {
        EmergencyDescentNoLongerActive,
        OrdinaryAltitudeStabilized,
    }

    private sealed interface SafeguardAction {
        data class GeneralEmergencyDescentWarning(val affectedAircraftId: AircraftId) : SafeguardAction
    }

    private data class PositionQuestion(val emergencyAircraftId: AircraftId)

    private data class RadioEmissionOrder(
        val firstStarted: EmergencyDescentEvent,
    ) {
        val provesEmergencyDescent: Boolean = false
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

    private fun StateTransition<EmergencyDescentSafeguardingProjection>.acceptedState():
        EmergencyDescentSafeguardingProjection =
        when (this) {
            is StateTransition.Accepted -> state
            is StateTransition.Rejected -> throw AssertionError("Expected accepted transition: $reason")
        }

    private companion object {
        val EMERGENCY_AIRCRAFT_ID = AircraftId("G-DESC")
        val AFFECTED_AIRCRAFT_ID = AircraftId("G-OTHR")
    }
}
