package xyz.easiersaid.twr.sim

import kotlin.test.Test
import xyz.easiersaid.twr.protocol.EmergencyType

private const val SAFETY_DOUBT_REASON = "unexpected vibration and partial power loss"

class Icao9432PilotSafetyDoubtSourceBackedTest {
    @Test
    fun `pilot safety doubt triggers assistance seeking under explicit policy`() {
        sourceUnitSpec("icao9432-pilot-safety-doubt-assistance-trigger") {
            title("Pilot seeks assistance when flight safety is in doubt")
            sourceUnit(
                chunk08Ref("icao9432-extracted::distress_urgency_intro_9_1_en::87b67820c6092a98"),
            )
            domain("pilot-condition", setOf("flight-safety-doubt", "routine-preference", "passenger-convenience"))
            domain("policy", setOf("assistance-warranted", "missing"))
            domain("surface", setOf("structured-request", "phraseology-only"))

            witness("typed safety doubt and policy yield assistance request") {
                val projection = PilotSafetyDoubtProjection
                    .from(PilotSafetyCondition.FlightSafetyInDoubt(SAFETY_DOUBT_REASON))
                    .acceptedState()
                    .seekAssistance(SafetyDoubtPolicy.AssistanceWarranted)
                    .acceptedState()

                check(projection.safetyDoubtReason == SAFETY_DOUBT_REASON)
                check(projection.policyMarker == SafetyDoubtPolicy.AssistanceWarranted)
                check(
                    projection.assistanceRequest == AssistanceRequest(
                        reason = SAFETY_DOUBT_REASON,
                        sourceWitness = SourceWitness.FlightSafetyDoubt,
                    ),
                )
                hit("pilot-seeks-assistance-for-safety-doubt")
                requireHits("pilot-seeks-assistance-for-safety-doubt")
            }
        }.assertSatisfied().assertNoModelGaps()
    }

    @Test
    fun `safety doubt recovery clears derived assistance state`() {
        val active = PilotSafetyDoubtProjection
            .from(PilotSafetyCondition.FlightSafetyInDoubt(SAFETY_DOUBT_REASON))
            .acceptedState()
            .seekAssistance(SafetyDoubtPolicy.AssistanceWarranted)
            .acceptedState()

        val recovered = active.resolve(SafetyDoubtResolution.AssistanceNoLongerNeeded).acceptedState()

        check(recovered.state == SafetyDoubtState.Normal)
        check(recovered.safetyDoubtReason == null)
        check(recovered.policyMarker == null)
        check(recovered.assistanceRequest == null)
        check(recovered.sourceWitness == null)
    }

    @Test
    fun `wrong paths do not satisfy pilot safety doubt source unit`() {
        check(PilotSafetyDoubtProjection.from(PilotSafetyCondition.RoutineOperationalPreference).isRejected)
        check(PilotSafetyDoubtProjection.from(PilotSafetyCondition.PassengerConvenienceConcern).isRejected)
        check(PilotSafetyDoubtProjection.from(PilotSafetyCondition.AtcOriginatedPrompt).isRejected)
        check(PilotSafetyDoubtProjection.from(PilotSafetyCondition.PhraseologyOnlyEmergencyCall).isRejected)
        check(
            PilotSafetyDoubtProjection
                .from(PilotSafetyCondition.GenericEmergencyLabel(EmergencyType.PAN_PAN))
                .isRejected,
        )
        check(
            PilotSafetyDoubtProjection
                .from(PilotSafetyCondition.GenericEmergencyLabel(EmergencyType.MAYDAY))
                .isRejected,
        )

        val normal = PilotSafetyDoubtProjection.normal()
        check(normal.seekAssistance(SafetyDoubtPolicy.AssistanceWarranted).isRejected)
        check(normal.resolve(SafetyDoubtResolution.AssistanceNoLongerNeeded).isRejected)

        val safetyDoubt = PilotSafetyDoubtProjection
            .from(PilotSafetyCondition.FlightSafetyInDoubt(SAFETY_DOUBT_REASON))
            .acceptedState()

        check(safetyDoubt.seekAssistance(SafetyDoubtPolicy.Missing).isRejected)
        check(safetyDoubt.seekAssistance(SafetyDoubtPolicy.RoutinePreferenceOnly).isRejected)
    }

    private fun chunk08Ref(canonicalId: String): SourceUnitRef =
        ICAO9432.DistressUrgencyCommsFailure.Chunk08Items
            .single { source -> source.canonicalId == canonicalId }
            .toSourceUnitRef()

    private data class PilotSafetyDoubtProjection(
        val state: SafetyDoubtState,
        val safetyDoubtReason: String?,
        val policyMarker: SafetyDoubtPolicy?,
        val assistanceRequest: AssistanceRequest?,
        val sourceWitness: SourceWitness?,
    ) {
        fun seekAssistance(policy: SafetyDoubtPolicy): StateTransition<PilotSafetyDoubtProjection> =
            when (state) {
                SafetyDoubtState.Normal ->
                    StateTransition.Rejected("Assistance request requires active pilot safety doubt")
                SafetyDoubtState.Active ->
                    when (policy) {
                        SafetyDoubtPolicy.AssistanceWarranted ->
                            when (val reason = safetyDoubtReason) {
                                null -> StateTransition.Rejected("Assistance request requires safety-doubt reason")
                                else -> StateTransition.Accepted(
                                    copy(
                                        policyMarker = policy,
                                        assistanceRequest = AssistanceRequest(
                                            reason = reason,
                                            sourceWitness = SourceWitness.FlightSafetyDoubt,
                                        ),
                                        sourceWitness = SourceWitness.FlightSafetyDoubt,
                                    ),
                                )
                            }

                        SafetyDoubtPolicy.Missing ->
                            StateTransition.Rejected("Safety-doubt assistance requires explicit policy")
                        SafetyDoubtPolicy.RoutinePreferenceOnly ->
                            StateTransition.Rejected("Routine preference policy does not satisfy safety-doubt assistance")
                    }
            }

        fun resolve(resolution: SafetyDoubtResolution): StateTransition<PilotSafetyDoubtProjection> =
            when (resolution) {
                SafetyDoubtResolution.AssistanceNoLongerNeeded ->
                    when (state) {
                        SafetyDoubtState.Normal ->
                            StateTransition.Rejected("Safety-doubt resolution requires active state")
                        SafetyDoubtState.Active ->
                            StateTransition.Accepted(normal())
                    }

                SafetyDoubtResolution.RoutinePreferenceSatisfied ->
                    StateTransition.Rejected("Routine preference resolution does not close safety-doubt state")
            }

        companion object {
            fun from(condition: PilotSafetyCondition): StateTransition<PilotSafetyDoubtProjection> =
                when (condition) {
                    is PilotSafetyCondition.FlightSafetyInDoubt ->
                        when {
                            condition.reason.isBlank() ->
                                StateTransition.Rejected("Flight-safety doubt requires stated reason")
                            else ->
                                StateTransition.Accepted(
                                    normal().copy(
                                        state = SafetyDoubtState.Active,
                                        safetyDoubtReason = condition.reason,
                                        sourceWitness = SourceWitness.FlightSafetyDoubt,
                                    ),
                                )
                        }

                    PilotSafetyCondition.RoutineOperationalPreference,
                    PilotSafetyCondition.PassengerConvenienceConcern,
                    PilotSafetyCondition.AtcOriginatedPrompt,
                    PilotSafetyCondition.PhraseologyOnlyEmergencyCall,
                    is PilotSafetyCondition.GenericEmergencyLabel,
                    -> StateTransition.Rejected("Not pilot flight-safety-doubt evidence")
                }

            fun normal(): PilotSafetyDoubtProjection =
                PilotSafetyDoubtProjection(
                    state = SafetyDoubtState.Normal,
                    safetyDoubtReason = null,
                    policyMarker = null,
                    assistanceRequest = null,
                    sourceWitness = null,
                )
        }
    }

    private sealed interface PilotSafetyCondition {
        data class FlightSafetyInDoubt(val reason: String) : PilotSafetyCondition
        data object RoutineOperationalPreference : PilotSafetyCondition
        data object PassengerConvenienceConcern : PilotSafetyCondition
        data object AtcOriginatedPrompt : PilotSafetyCondition
        data object PhraseologyOnlyEmergencyCall : PilotSafetyCondition
        data class GenericEmergencyLabel(val emergencyType: EmergencyType) : PilotSafetyCondition
    }

    private enum class SafetyDoubtState {
        Normal,
        Active,
    }

    private enum class SafetyDoubtPolicy {
        AssistanceWarranted,
        Missing,
        RoutinePreferenceOnly,
    }

    private data class AssistanceRequest(
        val reason: String,
        val sourceWitness: SourceWitness,
    )

    private enum class SourceWitness {
        FlightSafetyDoubt,
    }

    private enum class SafetyDoubtResolution {
        AssistanceNoLongerNeeded,
        RoutinePreferenceSatisfied,
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

    private fun StateTransition<PilotSafetyDoubtProjection>.acceptedState(): PilotSafetyDoubtProjection =
        when (this) {
            is StateTransition.Accepted -> state
            is StateTransition.Rejected -> throw AssertionError("Expected accepted transition: $reason")
        }
}
